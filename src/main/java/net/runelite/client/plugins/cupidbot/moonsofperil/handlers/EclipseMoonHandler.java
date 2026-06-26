package net.runelite.client.plugins.cupidbot.moonsofperil.handlers;

import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.cupidbot.moonsofperil.enums.GameObjects;
import net.runelite.client.plugins.cupidbot.moonsofperil.enums.Locations;
import net.runelite.client.plugins.cupidbot.moonsofperil.enums.State;
import net.runelite.client.plugins.cupidbot.moonsofperil.enums.Widgets;
import net.runelite.client.plugins.cupidbot.moonsofperil.MoonsOfPerilConfig;
import net.runelite.client.plugins.cupidbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.cupidbot.util.coords.Rs2LocalPoint;
import net.runelite.client.plugins.cupidbot.util.math.Rs2Random;
import net.runelite.client.plugins.cupidbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.cupidbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;

import static net.runelite.client.plugins.cupidbot.util.Global.sleep;
import static net.runelite.client.plugins.cupidbot.util.Global.sleepUntil;

import java.util.List;
import java.util.stream.Collectors;

public class EclipseMoonHandler implements BaseHandler {

    private static final String bossName = "Eclipse Moon";
    private static final int bossHealthBarWidgetID = Widgets.BOSS_HEALTH_BAR.getID();
    private static final int bossStatusWidgetID = Widgets.ECLIPSE_MOON_ID.getID();
    private static final int bossStatueObjectID = GameObjects.ECLIPSE_MOON_STATUE_ID.getID();
    private static final WorldPoint bossLobbyLocation = Locations.ECLIPSE_LOBBY.getWorldPoint();
    private static final WorldPoint bossArenaCenter = Locations.ECLIPSE_ARENA_CENTER.getWorldPoint();
    private static final WorldPoint exitTile = Locations.ECLIPSE_EXIT_TILE.getWorldPoint();
    private static final WorldPoint shieldSpawnTile = Locations.ECLIPSE_SHIELD_SPAWN_TILE.getWorldPoint();
    private static final WorldPoint cloneAttackTile = bossArenaCenter;
    private static final WorldPoint[] ATTACK_TILES = Locations.eclipseAttackTiles();
    private final int sigilNpcID = GameObjects.SIGIL_NPC_ID.getID();
    private final Rs2InventorySetup equipmentNormal;
    private final Rs2InventorySetup equipmentClones;
    private final boolean enableBoss;
    private final boolean clonerandomdelay;
    private final net.runelite.client.plugins.cupidbot.moonsofperil.handlers.BossHandler boss;
    private final boolean debugLogging;

    public EclipseMoonHandler(MoonsOfPerilConfig cfg, Rs2InventorySetup equipmentNormal, Rs2InventorySetup equipmentClones)
    {
        this.equipmentNormal = equipmentNormal;
        this.equipmentClones = equipmentClones;
        this.enableBoss = cfg.enableEclipse();
        this.boss = new net.runelite.client.plugins.cupidbot.moonsofperil.handlers.BossHandler(cfg);
        this.debugLogging = cfg.debugLogging();
        this.clonerandomdelay = cfg.enableEclipseRandomDelay();
    }

    @Override
    public boolean validate() {
        if (!enableBoss) {
            return false;
        }
        return (boss.bossIsAlive(bossName, bossStatusWidgetID));
    }

    @Override
    public State execute() {
        if (!Rs2Widget.isWidgetVisible(bossHealthBarWidgetID)) {
            BreakHandlerScript.setLockState(true);
            boss.walkToBoss(equipmentNormal, bossName, bossLobbyLocation);
            boss.fightPreparation(equipmentNormal);
            boss.enterBossArena(bossName, bossStatueObjectID, bossLobbyLocation);
            sleepUntil(() -> Rs2Widget.isWidgetVisible(bossHealthBarWidgetID), 5_000);
        }
        int bossNpcID = NpcID.PMOON_BOSS_ECLIPSE_MOON_VIS;
        while (Rs2Widget.isWidgetVisible(bossHealthBarWidgetID) || CupidBot.getRs2NpcCache().query().withId(bossNpcID).nearest() != null) {
            if (isSpecialAttack1Sequence()) {
                specialAttack1Sequence();
            }
            else if (isSpecialAttack2Sequence()) {
                specialAttack2Sequence();
            }
            else if (net.runelite.client.plugins.cupidbot.moonsofperil.handlers.BossHandler.isNormalAttackSequence(sigilNpcID)) {
                boss.normalAttackSequence(sigilNpcID, bossNpcID, ATTACK_TILES, equipmentNormal);
            }
            sleep(300);
        }
        if (debugLogging) {CupidBot.log("The " + bossName + "boss health bar widget is no longer visible, the fight must have ended.");}
        Rs2Prayer.disableAllPrayers();
        sleep(2400);
        net.runelite.client.plugins.cupidbot.moonsofperil.handlers.BossHandler.rechargeRunEnergy();
        BreakHandlerScript.setLockState(false);
        return State.IDLE;
    }

    /**
     * Returns True if the eclipseMoonShield NPC is found.
     */
    public boolean isSpecialAttack1Sequence() {
        Rs2NpcModel eclipseMoonShield = CupidBot.getRs2NpcCache().query().withId(NpcID.PMOON_BOSS_ECLIPSE_MOON_SHIELD).nearest();
        return eclipseMoonShield != null && CupidBot.getRs2NpcCache().query().withId(sigilNpcID).nearest() == null;
    }

    /**  Eclipse – Moon Shield Special-Attack Handler */
    public void specialAttack1Sequence()
    {
        Rs2Prayer.disableAllPrayers();
        var shieldNpc = CupidBot.getRs2NpcCache().query().withId(NpcID.PMOON_BOSS_ECLIPSE_MOON_SHIELD).nearest();
        if (shieldNpc == null) return;
        WorldPoint spawn = shieldNpc.getWorldLocation();
        if (debugLogging) {CupidBot.log("Exact Moonshield location = " + spawn);}
        /*if we enter arena mid attack phase, bail out*/
        if (!spawn.equals(shieldSpawnTile)) {
            CupidBot.log("Player has spawned into the arena in the middle of the sequence. Need to escape.");
            boss.bossBailOut(exitTile);
            return;
        }

        /*      1 ─ wait until shield starts sliding */

        if (debugLogging) {CupidBot.log("Sleeping until knockback animation finishes...");}
        sleepUntil(() ->
                        Rs2Player.getAnimation() != AnimationID.HUMAN_TROLL_FLYBACK_MERGE &&
                                Rs2Player.getWorldLocation().equals(new WorldPoint(1491, 9627, 0)),
                5_000);

        if (debugLogging) {CupidBot.log("Now sleeping 3.5 ticks to perfectly time our walk");}
        sleep(2_100);
        if (debugLogging) {CupidBot.log("Commencing our walk around the lap");}

        /*         ───── 2. Four anchor tiles around the boss (SW → NW → NE → SE) ───── */
        WorldPoint[] lap = {
                new WorldPoint(1483, 9627, 0),  // SW
                new WorldPoint(1483, 9637, 0),  // NW
                new WorldPoint(1493, 9637, 0),  // NE
                new WorldPoint(1493, 9627, 0)   // SE
        };

        for (WorldPoint p : lap) {
            if (debugLogging) {CupidBot.log("Walking to WorldPoint: " + p);}
            Rs2Walker.walkFastCanvas(p, false);
            boss.eatIfNeeded();
            boss.drinkIfNeeded();
            if (!isSpecialAttack1Sequence()) {
                return;
            }
            sleepUntil(() -> Rs2Player.getWorldLocation().equals(p));
        }
        if (debugLogging) {CupidBot.log("Shield lap has been completed");}

        /*         3 ─ run to post-phase attack tile */
        WorldPoint fin = Locations.ECLIPSE_ATTACK_6.getWorldPoint();
        if (debugLogging) {CupidBot.log("Running to the normal attack sequence tile");}
        Rs2Walker.walkFastCanvas(fin, true);
        if (debugLogging) {CupidBot.log("Sleeping until the Sigil tile spawns");}
        sleepUntil(() -> CupidBot.getRs2NpcCache().query().withId(sigilNpcID).nearest() != null, 4_000);
        if (debugLogging) {CupidBot.log("Searing Rays phase finished");}

    }

    /**
     * Returns true while the clone‐burst (Special-2) phase is active.
     * – Player must be standing on the center knock-back tile
     * – Boss must NOT be on that center tile
     * – Sigil NPC must not be present
     *
     */
    public boolean isSpecialAttack2Sequence()
    {
        WorldPoint center = cloneAttackTile;
        WorldPoint playerTile = Rs2Player.getWorldLocation();
        Rs2NpcModel bossNPC = CupidBot.getRs2NpcCache().query().withId(NpcID.PMOON_BOSS_ECLIPSE_MOON_VIS).nearest();

        // 1. Captures the conditions required for the start of the special attack sequence.
        if (playerTile.equals(center) && Rs2Player.getAnimation() == AnimationID.HUMAN_TROLL_FLYBACK_MERGE) {
            if (debugLogging) {CupidBot.log("Player located on center tile and knock back animation – entering Special Attack 2");}
            sleepUntil(() -> Rs2Player.getAnimation() != AnimationID.HUMAN_TROLL_FLYBACK_MERGE);
            if (debugLogging) {CupidBot.log("Knockback animation stopped. Clones are about to spawn");}
            boss.equipInventorySetup(equipmentClones);
            boss.eatIfNeeded();
            boss.drinkIfNeeded();
            net.runelite.client.plugins.cupidbot.moonsofperil.handlers.BossHandler.meleePrayerOn();
            return true;
        }

        // 2. Captures the conditions required if we spawn into the arena midway through the special attack phase.
        if (playerTile.equals(center) && bossNPC != null && CupidBot.getRs2NpcCache().query().withId(sigilNpcID).nearest() == null && !bossNPC.getLocalLocation().equals(Rs2LocalPoint.fromWorldInstance(center))) {
            boss.equipInventorySetup(equipmentClones);
            BossHandler.meleePrayerOn();
            return true;
        }

        return false;
    }

    public void specialAttack2Sequence() {
        final int CLONE_SPAWN_ANIM = 11019;
        final int CLONE_NPC_ID = NpcID.PMOON_BOSS_ECLIPSE_MOON_VIS;
        final long PHASE_TIMEOUT_MS = 35_000;

        if (debugLogging) {CupidBot.log("Starting Eclipse Special Attack 2 sequence");}

        int parried = 0;
        long phaseStart = System.currentTimeMillis();

        while (isSpecialAttack2Sequence() && System.currentTimeMillis() - phaseStart < PHASE_TIMEOUT_MS) {


            // 1. Look at ALL Eclipse-Moon NPCs this tick that have the specific spawn animation. Game mechanics mean this list SHOULD only return 1 NPC
            List<Rs2NpcModel> spawningClones = CupidBot.getRs2NpcCache().query()
                    .where(n -> n.getId() == CLONE_NPC_ID
                            && n.getNpc().getAnimation() == CLONE_SPAWN_ANIM)
                    .toList();
            if (debugLogging) {CupidBot.log("Collected all NPCs that match NPC ID & NPC Animation. Total = " + spawningClones.size());}

            // 2. Find the first clone within the list that matches the filter
            if (!spawningClones.isEmpty()) {
                Rs2NpcModel clone = spawningClones.get(0);
                WorldPoint cloneTrueLocation = clone.getWorldLocation();
                if (debugLogging) {CupidBot.log("Spawn true location: " + cloneTrueLocation);}
                WorldPoint cloneLocalLocation = WorldPoint.fromLocal(CupidBot.getClient(), clone.getLocalLocation());
                if (debugLogging) {CupidBot.log("Spawn local location: " + cloneLocalLocation);}

                // 3. Parry / Attack the clone
                if (debugLogging) {CupidBot.log("Clone #" + (parried + 1) + " spawned at " + cloneTrueLocation + " → Parrying via " + cloneLocalLocation);}
                if (clonerandomdelay) {
                    int delay = Rs2Random.between(30, 150);
                    sleep(delay);
                }
                Rs2Walker.walkCanvas(cloneLocalLocation);
                parried++;
            }
            if (!isSpecialAttack2Sequence()) {
                if (debugLogging) {CupidBot.log("Special attack 2 sequence has ended, breaking out");}
                break;
            }
            sleep(600);
        }
        if (debugLogging) {CupidBot.log("Clone phase ended – total clones parried: " + parried);}
    }
}