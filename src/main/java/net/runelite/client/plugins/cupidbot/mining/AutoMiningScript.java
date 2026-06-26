package net.runelite.client.plugins.cupidbot.mining;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.mining.data.LocationOption;
import net.runelite.client.plugins.cupidbot.mining.data.MiningRockLocations;
import net.runelite.client.plugins.cupidbot.mining.data.Rocks;
import net.runelite.client.plugins.cupidbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.cupidbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.cupidbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.cupidbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.cupidbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.cupidbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.cupidbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.cupidbot.util.math.Rs2Random;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.security.Login;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

enum State {
    MINING,
    RESETTING,
}

@Slf4j
public class AutoMiningScript extends Script {

    private static final int GEM_MINE_UNDERGROUND = 11410;
    private static final int POST_ROCK_CLICK_WAIT_MILLIS = 650;
    private static final long MINING_ACTION_IDLE_GRACE_MILLIS = 2_500;
    private static final long MAX_MINING_ACTION_WAIT_MILLIS = 60_000;
    private static final long DIAGNOSTIC_LOG_INTERVAL_MILLIS = 2_000;
    private static final int REACHABILITY_CANDIDATE_LIMIT = 3;
    private static final long SLOW_ROCK_LOOKUP_LOG_THRESHOLD_MILLIS = 500;
    State state = State.MINING;
    private static final List<Rocks> PROGRESSIVE_ROCKS = buildProgressiveRocks();
    private Rocks activeRock;
    private LocationOption activeLocation;
    private long lastDiagnosticLogAtMillis = 0;
    private String lastDiagnosticReason = "";
    private boolean miningActionInProgress = false;
    private long miningActionStartedAtMillis = -1;
    private long lastMiningActionActivityAtMillis = -1;
    private int miningActionStartedExperience = -1;
    private int miningActionStartedInventoryCount = -1;
    private int activeMiningRockId = -1;
    private WorldPoint activeMiningRockLocation;

    public boolean run(AutoMiningConfig config) {
        initialPlayerLocation = null;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
        configureFastMiningCadence();
        log.info("[AutoMining] Starting AutoMiningPlugin v{} ore={} progressive={} radius={} useBank={} maxPlayers={} leagueMode={} itemsToBank='{}' itemsToKeep='{}'",
                AutoMiningPlugin.version,
                config.ORE(),
                config.progressiveMode(),
                config.distanceToStray(),
                config.useBank(),
                config.maxPlayersInArea(),
                config.leagueMode(),
                config.itemsToBank(),
                config.itemsToKeep());
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) {
                    logDiagnostic("blocked by script guard", config, null, false, false, false);
                    return;
                }
                if (!CupidBot.isLoggedIn()) {
                    logDiagnostic("waiting for login", config, null, false, false, false);
                    return;
                }
                WorldPoint playerLocation = getCurrentWorldLocationIfReady();
                if (!isWorldLocationReady(playerLocation)) {
                    CupidBot.status = "Waiting for player location...";
                    logDiagnostic("waiting for player location", config, null);
                    return;
                }
                if (config.leagueMode() && Rs2Player.checkIdleLogout(Rs2Random.between(500, 1500))) {
                    int[] arrowKeys = { KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN };
                    Rs2Keyboard.keyPress(arrowKeys[Rs2Random.between(0, arrowKeys.length - 1)]);
                }
                if (shouldPauseForAntibanCooldown() && Rs2AntibanSettings.actionCooldownActive) {
                    logDiagnostic("waiting for antiban cooldown", config, playerLocation);
                    return;
                }
                if (initialPlayerLocation == null) {
                    initialPlayerLocation = playerLocation;
                    log.info("[AutoMining] Initial mining anchor set to {}", formatWorldPoint(initialPlayerLocation));
                }

                // Skip cycle if we don't have a valid location
                if (initialPlayerLocation == null) {
                    logDiagnostic("waiting for mining anchor", config, playerLocation);
                    return;
                }

                updateActiveRock(config, playerLocation);

                if (config.progressiveMode() && ensureProgressiveLocation(config, playerLocation)) {
                    logDiagnostic("walking to progressive mining location", config, playerLocation);
                    return;
                }

                if (activeRock == null || !activeRock.hasRequiredLevel()) {
                    CupidBot.log("You do not have the required mining level to mine this ore.");
                    logDiagnostic("missing mining level", config, playerLocation);
                    return;
                }

                if (Rs2Equipment.isWearing("Dragon pickaxe"))
                    Rs2Combat.setSpecState(true, 1000);

                boolean moving = Rs2Player.isMoving();
                boolean animating = Rs2Player.isAnimating();
                boolean inventoryFull = Rs2Inventory.isFull();
                boolean completedMiningActionThisCycle = false;
                if (miningActionInProgress) {
                    if (waitForMiningActionIfNeeded(config, playerLocation, moving, animating, inventoryFull)) {
                        return;
                    }
                    completedMiningActionThisCycle = true;
                }
                if (moving || (animating && !completedMiningActionThisCycle)) {
                    logDiagnostic("waiting for movement or animation", config, playerLocation, moving, animating, Rs2Inventory.isFull());
                    return;
                }

                //code to change worlds if there are too many players in the distance to stray tiles
                int maxPlayers = config.maxPlayersInArea();
                if (maxPlayers > 0) {
                    WorldPoint localLocation = playerLocation;
                    long nearbyPlayers = CupidBot.getClientThread().runOnClientThreadOptional(() ->
                                    CupidBot.getClient().getTopLevelWorldView().players().stream()
                                            .filter(p -> p != null && p != CupidBot.getClient().getLocalPlayer())
                                            .filter(p -> {
                                                if (config.distanceToStray() == 0) {
                                                    // Only count players standing on the same exact tile
                                                    return p.getWorldLocation().equals(localLocation);
                                                }
                                                // Count players within distanceToStray
                                                return p.getWorldLocation().distanceTo(localLocation) <= config.distanceToStray();
                                            })
                                            // filter if players are using mining animation
                                            .filter(p -> p.getAnimation() != -1)
                                            .count())
                            .orElse(0L);

                    if (nearbyPlayers >= maxPlayers) {
                        CupidBot.status = "Too many players nearby. Hopping...";
                        log.info("[AutoMining] Too many mining players nearby: nearbyPlayers={} maxPlayers={} player={}",
                                nearbyPlayers, maxPlayers, formatWorldPoint(playerLocation));
                        Rs2Random.waitEx(3200, 800); // Delay to avoid UI locking

                        int world = Login.getRandomWorld(Rs2Player.isMember());
                        boolean hopped = CupidBot.hopToWorld(world);
                        if (hopped) {
                            CupidBot.status = "Hopped to world: " + world;
                            log.info("[AutoMining] Hopped to world {}", world);
                            return; // Exit current cycle after hop
                        }
                        log.warn("[AutoMining] World hop request failed for world {}", world);
                    }
                }


                switch (state) {
                    case MINING:
                        if (Rs2Inventory.isFull()) {
                            state = State.RESETTING;
                            log.info("[AutoMining] Inventory full; switching to RESETTING at {}", formatWorldPoint(playerLocation));
                            return;
                        }

                        if (activeRock == null) {
                            logDiagnostic("waiting for active rock", config, playerLocation);
                            return;
                        }

                        // Check if we're too far from mining location - walk back first
                        if (initialPlayerLocation != null) {
                            int distanceFromStart = playerLocation.distanceTo(initialPlayerLocation);
                            if (distanceFromStart > config.distanceToStray()) {
                                CupidBot.status = "Walking back to mining location...";
                                logDiagnostic("walking back to mining anchor", config, playerLocation, false, false, false);
                                Rs2Walker.walkTo(initialPlayerLocation, config.distanceToStray());
                                return;
                            }
                        }

                        GameObject rock = findReachableRock(activeRock, playerLocation, initialPlayerLocation, config.distanceToStray());

                        if (rock == null) {
                            CupidBot.status = "Looking for " + activeRock.getName() + "...";
                            logDiagnostic("no reachable rock found", config, playerLocation);
                            return;
                        }

                        log.info("[AutoMining] Clicking rock id={} name='{}' loc={} player={} anchor={} radius={}",
                                rock.getId(),
                                Rs2GameObject.getCompositionName(rock).orElse(activeRock.getName()),
                                formatWorldPoint(rock.getWorldLocation()),
                                formatWorldPoint(playerLocation),
                                formatWorldPoint(initialPlayerLocation),
                                config.distanceToStray());
                        int experienceBeforeClick = getMiningExperience();
                        int inventoryCountBeforeClick = Rs2Inventory.count();
                        boolean clicked = Rs2GameObject.interact(rock);
                        if (clicked) {
                            startMiningAction(rock, experienceBeforeClick, inventoryCountBeforeClick);
                            boolean waitSatisfied = sleepUntil(() -> Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Inventory.isFull(),
                                    getPostRockClickWaitMillis());
                            log.info("[AutoMining] Rock click accepted waitSatisfied={} waitMs={} moving={} animating={} inventoryFull={}",
                                    waitSatisfied,
                                    getPostRockClickWaitMillis(),
                                    Rs2Player.isMoving(),
                                    Rs2Player.isAnimating(),
                                    Rs2Inventory.isFull());
                        } else {
                            log.warn("[AutoMining] Rock click failed id={} loc={} player={}",
                                    rock.getId(),
                                    formatWorldPoint(rock.getWorldLocation()),
                                    formatWorldPoint(playerLocation));
                        }
                        break;
                    case RESETTING:
                        List<String> itemNames = Arrays.stream(config.itemsToBank().split(","))
                                .map(String::trim)
                                .map(String::toLowerCase)
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toList());

                        if (config.useBank()) {
                            if (config.clayBracelet() && config.ORE() == Rocks.CLAY) {
                                logDiagnostic("banking clay and bracelet", config, playerLocation);
                                if (!Rs2Bank.walkToBankAndUseBank()) {
                                    return;
                                }

                                // deposit all non-locked items to make room for bracelet of clay
                                Rs2Bank.depositAll();
                                if (Rs2Bank.hasItem(11074)) {
                                    Rs2Bank.withdrawAndEquip(11074);
                                }
                                else {
                                    log.info("You don't have any more bracelet of clays");
                                }
                                Rs2Bank.bankItemsAndWalkBackToOriginalPosition(itemNames, initialPlayerLocation, 0, config.distanceToStray());
                            }
                            else if (activeRock == Rocks.GEM && playerLocation.getRegionID() == GEM_MINE_UNDERGROUND) {
                                logDiagnostic("depositing gems", config, playerLocation);
                                if (Rs2DepositBox.openDepositBox()) {
                                    if (Rs2Inventory.contains("Open gem bag")) {
                                        Rs2Inventory.interact("Open gem bag", "Empty");
                                        Rs2DepositBox.depositAllExcept("Open gem bag");
                                    } else {
                                        Rs2DepositBox.depositAll();
                                    }
                                    Rs2DepositBox.closeDepositBox();
                                }
                            } else if (Rocks.BASALT == activeRock) {
                                logDiagnostic("noting basalt", config, playerLocation);
                                if (Rs2Walker.walkTo(2872, 3935, 0)) {
                                    Rs2Inventory.useItemOnNpc(ItemID.BASALT, NpcID.MY2ARM_SNOWFLAKE);
                                    Rs2Walker.walkTo(2841, 10339, 0);
                                }
                            } else {
                                if (!Rs2Bank.isOpen()) {
                                    logDiagnostic("walking to bank", config, playerLocation);
                                    if (!Rs2Bank.walkToBankAndUseBank()) {
                                        return;
                                    }
                                    return;
                                }

                                if (itemNames.isEmpty()) {
                                    Rs2Bank.depositAll();
                                } else {
                                    Rs2Bank.depositAll(i ->
                                            i.getName() != null &&
                                                    itemNames.stream().anyMatch(item -> i.getName().toLowerCase().contains(item)));
                                }

                                if (!Rs2Bank.closeBank())
                                    return;

                                Rs2Walker.walkTo(initialPlayerLocation, config.distanceToStray());
                            }

                        } else {
                            log.info("[AutoMining] Dropping inventory items; keeping '{}'", config.itemsToKeep());
                            Rs2Inventory.dropAllExcept(false, config.interactOrder(), Arrays.stream(config.itemsToKeep().split(",")).map(String::trim).toArray(String[]::new));
                        }

                        state = State.MINING;
                        log.info("[AutoMining] Reset complete; switching to MINING at anchor={}", formatWorldPoint(initialPlayerLocation));
                        break;
                }
            } catch (Exception ex) {
                if (Script.isInterruption(ex)) {
                    Thread.currentThread().interrupt();
                    log.info("[AutoMining] Mining loop interrupted; exiting current cycle");
                    return;
                }
                log.error("[AutoMining] Error in mining loop", ex);
                CupidBot.log(ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    static boolean shouldUsePostOreActionCooldown(Rocks rock) {
        // Auto Mining uses a short post-click settle wait and then relies on animation/movement state.
        // Applying the general mining antiban cooldown after every ore adds a 17-30 tick idle gap.
        return false;
    }

    static boolean shouldPauseForAntibanCooldown() {
        // Auto Mining needs ore-to-ore responsiveness; shared antiban cooldowns can last 17-30 ticks.
        return false;
    }

    static int getPostRockClickWaitMillis() {
        return POST_ROCK_CLICK_WAIT_MILLIS;
    }

    static long getMiningActionIdleGraceMillis() {
        return MINING_ACTION_IDLE_GRACE_MILLIS;
    }

    static long getMaxMiningActionWaitMillis() {
        return MAX_MINING_ACTION_WAIT_MILLIS;
    }

    static long getDiagnosticLogIntervalMillis() {
        return DIAGNOSTIC_LOG_INTERVAL_MILLIS;
    }

    static int getReachabilityCandidateLimit() {
        return REACHABILITY_CANDIDATE_LIMIT;
    }

    static boolean shouldCheckRockReachabilityCandidate(boolean rockNameMatches, int namedCandidateIndex) {
        return rockNameMatches && namedCandidateIndex >= 0 && namedCandidateIndex < REACHABILITY_CANDIDATE_LIMIT;
    }

    static boolean shouldKeepMiningActionInProgress(
            boolean moving,
            boolean animating,
            boolean experienceChanged,
            boolean inventoryChanged,
            boolean inventoryFull,
            boolean targetRockAvailable,
            long nowMillis,
            long actionStartedAtMillis,
            long lastActivityAtMillis) {
        if (experienceChanged || inventoryChanged || inventoryFull || !targetRockAvailable || actionStartedAtMillis <= 0) {
            return false;
        }

        if (nowMillis - actionStartedAtMillis > MAX_MINING_ACTION_WAIT_MILLIS) {
            return false;
        }

        if (moving || animating) {
            return true;
        }

        long activityAt = lastActivityAtMillis > 0 ? lastActivityAtMillis : actionStartedAtMillis;
        return nowMillis - activityAt <= MINING_ACTION_IDLE_GRACE_MILLIS;
    }

    static boolean shouldLogDiagnostic(long nowMillis, long lastLogMillis, String reason, String lastReason) {
        return !Objects.equals(reason, lastReason)
                || nowMillis - lastLogMillis >= DIAGNOSTIC_LOG_INTERVAL_MILLIS;
    }

    static String formatDiagnosticMessage(
            String reason,
            State state,
            Rocks rock,
            WorldPoint playerLocation,
            WorldPoint anchorLocation,
            int radius,
            boolean moving,
            boolean animating,
            boolean inventoryFull,
            boolean cooldownActive) {
        String rockName = rock != null ? rock.getName() : "none";
        return "reason=" + reason
                + " state=" + state
                + " rock=" + rockName
                + " player=" + formatWorldPoint(playerLocation)
                + " anchor=" + formatWorldPoint(anchorLocation)
                + " radius=" + radius
                + " moving=" + moving
                + " animating=" + animating
                + " inventoryFull=" + inventoryFull
                + " cooldownActive=" + cooldownActive;
    }

    static boolean isWorldLocationReady(WorldPoint playerLocation) {
        return playerLocation != null;
    }

    static String formatWorldPoint(WorldPoint point) {
        if (point == null) {
            return "null";
        }
        return point.getX() + "," + point.getY() + "," + point.getPlane();
    }

    private static WorldPoint getCurrentWorldLocationIfReady() {
        return CupidBot.getClientThread().runOnClientThreadOptional(() -> {
            if (CupidBot.getClient().getGameState() != GameState.LOGGED_IN) {
                return null;
            }

            Player localPlayer = CupidBot.getClient().getLocalPlayer();
            if (localPlayer == null || localPlayer.getWorldLocation() == null) {
                return null;
            }

            if (CupidBot.getClient().getTopLevelWorldView().getScene().isInstance()) {
                LocalPoint localPoint = LocalPoint.fromWorld(
                        CupidBot.getClient().getTopLevelWorldView(),
                        localPlayer.getWorldLocation());
                if (localPoint != null) {
                    return WorldPoint.fromLocalInstance(CupidBot.getClient(), localPoint);
                }
            }

            return localPlayer.getWorldLocation();
        }).orElse(null);
    }

    private static void configureFastMiningCadence() {
        Rs2AntibanSettings.actionCooldownChance = 0.0;
        Rs2AntibanSettings.actionCooldownActive = false;
        Rs2AntibanSettings.microBreakActive = false;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2Antiban.setTIMEOUT(0);
    }

    @Override
    public void shutdown() {
        log.info("[AutoMining] Shutting down AutoMiningPlugin v{}", AutoMiningPlugin.version);
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }

    private static List<Rocks> buildProgressiveRocks() {
        List<Rocks> rocks = new ArrayList<>(Arrays.asList(
                Rocks.TIN,
                Rocks.IRON,
                Rocks.COAL,
                Rocks.GOLD,
                Rocks.MITHRIL,
                Rocks.ADAMANTITE,
                Rocks.RUNITE
        ));
        return rocks;
    }

    private void updateActiveRock(AutoMiningConfig config, WorldPoint playerLocation) {
        Rocks previousRock = activeRock;
        LocationOption previousLocation = activeLocation;

        if (!config.progressiveMode()) {
            activeRock = config.ORE();
            activeLocation = MiningRockLocations.getBestAccessibleLocation(activeRock, playerLocation);
            logActiveRockChange(previousRock, previousLocation);
            updateStatus();
            return;
        }

        Rocks unlockedRock = PROGRESSIVE_ROCKS.stream()
                .filter(Rocks::hasRequiredLevel)
                .max(Comparator.comparingInt(Rocks::getMiningLevel))
                .orElse(PROGRESSIVE_ROCKS.get(0));

        activeRock = unlockedRock;
        activeLocation = MiningRockLocations.getBestAccessibleLocation(activeRock, playerLocation);

        logActiveRockChange(previousRock, previousLocation);
        updateStatus();
    }

    private boolean ensureProgressiveLocation(AutoMiningConfig config, WorldPoint playerLocation) {
        if (activeLocation == null || activeLocation.getWorldPoint() == null) {
            return false;
        }

        WorldPoint targetPoint = activeLocation.getWorldPoint();

        // Only update initialPlayerLocation if it's null
        // Don't update just because player is far away (e.g., at bank) - that breaks return-to-location
        if (initialPlayerLocation == null) {
            initialPlayerLocation = targetPoint;
        }

        if (playerLocation == null) {
            return true;
        }

        int acceptableDistance = Math.min(Math.max(1, config.distanceToStray()), 5);
        int distanceToTarget = playerLocation.distanceTo(targetPoint);
        if (distanceToTarget > acceptableDistance) {
            if (Rs2Player.isMoving()) {
                logDiagnostic("waiting while walking to progressive mining location", config, playerLocation);
                return true;
            }

            log.info("[AutoMining] Walking to progressive location name='{}' point={} player={} acceptableDistance={}",
                    activeLocation.getName(),
                    formatWorldPoint(targetPoint),
                    formatWorldPoint(playerLocation),
                    acceptableDistance);
            Rs2Walker.walkTo(targetPoint, 3);
            return true;
        }

        return false;
    }

    private void startMiningAction(GameObject rock, int startExperience, int startInventoryCount) {
        miningActionInProgress = true;
        miningActionStartedAtMillis = System.currentTimeMillis();
        lastMiningActionActivityAtMillis = miningActionStartedAtMillis;
        miningActionStartedExperience = startExperience;
        miningActionStartedInventoryCount = startInventoryCount;
        activeMiningRockId = rock != null ? rock.getId() : -1;
        activeMiningRockLocation = rock != null ? rock.getWorldLocation() : null;

        log.info("[AutoMining] Mining action started rockId={} rockLoc={} startXp={} startInventoryCount={}",
                activeMiningRockId,
                formatWorldPoint(activeMiningRockLocation),
                miningActionStartedExperience,
                miningActionStartedInventoryCount);
    }

    private boolean waitForMiningActionIfNeeded(
            AutoMiningConfig config,
            WorldPoint playerLocation,
            boolean moving,
            boolean animating,
            boolean inventoryFull) {
        long now = System.currentTimeMillis();
        if (moving || animating) {
            lastMiningActionActivityAtMillis = now;
        }

        int currentExperience = getMiningExperience();
        int currentInventoryCount = Rs2Inventory.count();
        boolean experienceChanged = miningActionStartedExperience >= 0
                && currentExperience >= 0
                && currentExperience != miningActionStartedExperience;
        boolean inventoryChanged = miningActionStartedInventoryCount >= 0
                && currentInventoryCount != miningActionStartedInventoryCount;
        boolean targetRockAvailable = isActiveMiningTargetAvailable();

        if (shouldKeepMiningActionInProgress(
                moving,
                animating,
                experienceChanged,
                inventoryChanged,
                inventoryFull,
                targetRockAvailable,
                now,
                miningActionStartedAtMillis,
                lastMiningActionActivityAtMillis)) {
            logDiagnostic("waiting for mining completion", config, playerLocation, moving, animating, inventoryFull);
            return true;
        }

        String reason = getMiningActionCompletionReason(
                experienceChanged,
                inventoryChanged,
                inventoryFull,
                targetRockAvailable,
                now,
                miningActionStartedAtMillis,
                lastMiningActionActivityAtMillis);
        log.info("[AutoMining] Mining action released reason={} elapsedMs={} xpStart={} xpNow={} invStart={} invNow={} targetAvailable={} rockId={} rockLoc={}",
                reason,
                miningActionStartedAtMillis > 0 ? now - miningActionStartedAtMillis : -1,
                miningActionStartedExperience,
                currentExperience,
                miningActionStartedInventoryCount,
                currentInventoryCount,
                targetRockAvailable,
                activeMiningRockId,
                formatWorldPoint(activeMiningRockLocation));
        clearMiningAction();
        return false;
    }

    private static String getMiningActionCompletionReason(
            boolean experienceChanged,
            boolean inventoryChanged,
            boolean inventoryFull,
            boolean targetRockAvailable,
            long nowMillis,
            long actionStartedAtMillis,
            long lastActivityAtMillis) {
        if (experienceChanged) {
            return "experience changed";
        }
        if (inventoryChanged) {
            return "inventory changed";
        }
        if (inventoryFull) {
            return "inventory full";
        }
        if (!targetRockAvailable) {
            return "target rock unavailable";
        }
        if (actionStartedAtMillis <= 0) {
            return "no active action";
        }
        if (nowMillis - actionStartedAtMillis > MAX_MINING_ACTION_WAIT_MILLIS) {
            return "max wait exceeded";
        }
        long activityAt = lastActivityAtMillis > 0 ? lastActivityAtMillis : actionStartedAtMillis;
        if (nowMillis - activityAt > MINING_ACTION_IDLE_GRACE_MILLIS) {
            return "idle grace expired";
        }
        return "released";
    }

    private void clearMiningAction() {
        miningActionInProgress = false;
        miningActionStartedAtMillis = -1;
        lastMiningActionActivityAtMillis = -1;
        miningActionStartedExperience = -1;
        miningActionStartedInventoryCount = -1;
        activeMiningRockId = -1;
        activeMiningRockLocation = null;
    }

    private boolean isActiveMiningTargetAvailable() {
        if (activeMiningRockLocation == null || activeMiningRockId < 0) {
            return true;
        }

        return !Rs2GameObject.getGameObjects(
                candidate -> candidate.getId() == activeMiningRockId
                        && activeMiningRockLocation.equals(candidate.getWorldLocation()),
                activeMiningRockLocation,
                1).isEmpty();
    }

    private static int getMiningExperience() {
        try {
            if (CupidBot.getClient() == null) {
                return -1;
            }
            return CupidBot.getClient().getSkillExperience(Skill.MINING);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private GameObject findReachableRock(Rocks rock, WorldPoint playerLocation, WorldPoint anchorLocation, int distance) {
        if (rock == null || playerLocation == null || anchorLocation == null) {
            return null;
        }

        long lookupStarted = System.currentTimeMillis();
        Predicate<TileObject> namePredicate = Rs2GameObject.nameMatches(rock.getName(), true);
        List<GameObject> candidates = Rs2GameObject.getGameObjects(
                        candidate -> namePredicate.test(candidate),
                        anchorLocation,
                        distance)
                .stream()
                .sorted(Comparator.comparingInt(candidate -> playerLocation.distanceTo(candidate.getWorldLocation())))
                .collect(Collectors.toList());

        int checked = 0;
        for (GameObject candidate : candidates) {
            if (!shouldCheckRockReachabilityCandidate(true, checked)) {
                break;
            }
            checked++;
            if (Rs2GameObject.isReachable(candidate)) {
                logSlowRockLookup(rock, playerLocation, anchorLocation, candidates.size(), checked, lookupStarted);
                return candidate;
            }
        }

        logSlowRockLookup(rock, playerLocation, anchorLocation, candidates.size(), checked, lookupStarted);
        return null;
    }

    private void logSlowRockLookup(
            Rocks rock,
            WorldPoint playerLocation,
            WorldPoint anchorLocation,
            int candidateCount,
            int reachabilityChecks,
            long lookupStarted) {
        long elapsed = System.currentTimeMillis() - lookupStarted;
        if (elapsed < SLOW_ROCK_LOOKUP_LOG_THRESHOLD_MILLIS) {
            return;
        }

        log.info("[AutoMining] Slow rock lookup elapsedMs={} rock={} candidates={} reachabilityChecks={} player={} anchor={} limit={}",
                elapsed,
                rock != null ? rock.getName() : "none",
                candidateCount,
                reachabilityChecks,
                formatWorldPoint(playerLocation),
                formatWorldPoint(anchorLocation),
                REACHABILITY_CANDIDATE_LIMIT);
    }

    private void updateStatus() {
        String oreName = activeRock != null ? activeRock.getName() : "Unknown";
        String locationName = (activeLocation != null && activeLocation.getName() != null)
                ? activeLocation.getName()
                : "current area";
        CupidBot.status = "Mining " + oreName + " @ " + locationName;
    }

    private void logDiagnostic(String reason, AutoMiningConfig config, WorldPoint playerLocation) {
        boolean loggedIn = CupidBot.isLoggedIn();
        logDiagnostic(
                reason,
                config,
                playerLocation,
                loggedIn && Rs2Player.isMoving(),
                loggedIn && Rs2Player.isAnimating(),
                loggedIn && Rs2Inventory.isFull());
    }

    private void logDiagnostic(
            String reason,
            AutoMiningConfig config,
            WorldPoint playerLocation,
            boolean moving,
            boolean animating,
            boolean inventoryFull) {
        long now = System.currentTimeMillis();
        if (!shouldLogDiagnostic(now, lastDiagnosticLogAtMillis, reason, lastDiagnosticReason)) {
            return;
        }

        lastDiagnosticLogAtMillis = now;
        lastDiagnosticReason = reason;

        int radius = config != null ? config.distanceToStray() : -1;
        log.info("[AutoMining] {}",
                formatDiagnosticMessage(
                        reason,
                        state,
                        activeRock,
                        playerLocation,
                        initialPlayerLocation,
                        radius,
                        moving,
                        animating,
                        inventoryFull,
                        Rs2AntibanSettings.actionCooldownActive));
    }

    private void logActiveRockChange(Rocks previousRock, LocationOption previousLocation) {
        String previousLocationName = previousLocation != null ? previousLocation.getName() : null;
        String activeLocationName = activeLocation != null ? activeLocation.getName() : null;
        WorldPoint activeLocationPoint = activeLocation != null ? activeLocation.getWorldPoint() : null;

        if (previousRock != activeRock || !Objects.equals(previousLocationName, activeLocationName)) {
            log.info("[AutoMining] Target updated rock={} location='{}' locationPoint={}",
                    activeRock != null ? activeRock.getName() : "none",
                    activeLocationName != null ? activeLocationName : "current area",
                    formatWorldPoint(activeLocationPoint));
        }
    }
}
