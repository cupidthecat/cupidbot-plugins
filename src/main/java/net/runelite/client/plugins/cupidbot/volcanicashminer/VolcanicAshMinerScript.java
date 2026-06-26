package net.runelite.client.plugins.cupidbot.volcanicashminer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.cupidbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.cupidbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.cupidbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.cupidbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.cupidbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.cupidbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.math.Rs2Random;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.security.Login;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.mining.MiningAnimation;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.cupidbot.util.antiban.enums.ActivityIntensity.VERY_LOW;

@Slf4j
public class VolcanicAshMinerScript extends Script {
    public static VolcanicAshMinerState BOT_STATUS = VolcanicAshMinerState.MINING;
    private final WorldPoint VOLCANIC_ASH_LOCATION = new WorldPoint(3790, 3770, 0);
    public Instant startTime;

    /**
     * Get the total runtime of the script
     *
     * @return the total runtime of the script
     */
    public Duration getRunTime() {
        if (startTime == null) return Duration.ofSeconds(0);
        return Duration.between(startTime, Instant.now());
    }
    public boolean run(VolcanicAshMinerConfig config) {
        startTime = Instant.now();
        BOT_STATUS = VolcanicAshMinerState.MINING;
        CupidBot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.simulateFatigue = false;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.dynamicActivity = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.04;
        Rs2Antiban.setActivityIntensity(VERY_LOW);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!CupidBot.isLoggedIn()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;
                handleMining(config);
            } catch (Exception ex) {
                if (Script.isInterruption(ex)) {
                    Thread.currentThread().interrupt();
                    return;
                }
                log.error("Error during script execution", ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleMining(VolcanicAshMinerConfig config) {
        if (Rs2Inventory.isFull()) {
            if (config.bankAsh()) {
                BOT_STATUS = VolcanicAshMinerState.WALKING;
                Rs2Bank.walkToBankAndUseBank(BankLocation.VOLCANO_BANK);
                if (!Rs2Bank.isOpen()) return;
                BOT_STATUS = VolcanicAshMinerState.BANKING;
                Rs2Bank.depositAll("soda ash");
                if (!sleepUntil(() -> !Rs2Inventory.contains("soda ash"))) {
                    log.warn("Failed to deposit soda ash");
                    return;
                }
                Rs2Bank.closeBank();
            } else {
                // Just for visual feedback in the overlay
                BOT_STATUS = VolcanicAshMinerState.DROPPING;
                Rs2Inventory.dropAll("soda ash");
            }
        }

        if (Rs2Player.distanceTo(VOLCANIC_ASH_LOCATION) > 15) {
            BOT_STATUS = VolcanicAshMinerState.WALKING;
            Rs2Walker.walkTo(VOLCANIC_ASH_LOCATION);
        }

        if (hopIfTooManyPlayersNearby(config)) return; // Exit current cycle after hop

        if (Rs2Equipment.isWearing("Dragon pickaxe") || Rs2Equipment.isWearing("Crystal pickaxe")) {
            if (Rs2Combat.setSpecState(true, 1000)) return;
        }

        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            return;
        }

        GameObject rock = Rs2GameObject.findReachableObject("Ash pile", true, 12, VOLCANIC_ASH_LOCATION);
        if (rock != null) {
            BOT_STATUS = VolcanicAshMinerState.MINING;
            if (Rs2GameObject.interact(rock)) {
                Rs2Player.waitForXpDrop(Skill.MINING, true);
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            }
        } else {
            BOT_STATUS = VolcanicAshMinerState.WAITING;
            CupidBot.log("No Ash pile found. Waiting...");
        }
    }

    private boolean hopIfTooManyPlayersNearby(VolcanicAshMinerConfig config) {
        int maxPlayers = config.maxPlayersInArea();
        if (maxPlayers > 0) {
            WorldPoint localLocation = Rs2Player.getWorldLocation();

            long nearbyPlayers = CupidBot.getClient().getTopLevelWorldView().players().stream()
                    .filter(p -> p != null && p != CupidBot.getClient().getLocalPlayer())
                    .filter(p -> p.getWorldLocation().distanceTo(localLocation) <= 15)
                    //filter if players are using mining animation
                    .map(Actor::getAnimation)
                    .filter(MiningAnimation.MINING_ANIMATIONS::contains)
                    .count();

            if (nearbyPlayers >= maxPlayers) {
                CupidBot.log("Too many players nearby. Hopping...");
                Rs2Random.waitEx(3200, 800); // Delay to avoid UI locking
                int world = Login.getRandomWorld(Rs2Player.isMember());
                boolean hopped = CupidBot.hopToWorld(world);
                if (!hopped) return false;
                sleepUntil(() -> CupidBot.getClient().getGameState() == GameState.HOPPING);
                sleepUntil(() -> CupidBot.getClient().getGameState() == GameState.LOGGED_IN);
            }
        }
        return false;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }

    public enum VolcanicAshMinerState {
        WALKING,
        BANKING,
        DROPPING,
        MINING,
        WAITING
    }
}
