package net.runelite.client.plugins.cupidbot.moonsofperil.handlers;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.moonsofperil.enums.Widgets;
import net.runelite.client.plugins.cupidbot.moonsofperil.MoonsOfPerilConfig;
import net.runelite.client.plugins.cupidbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.cupidbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.cupidbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.cupidbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.cupidbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.cupidbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.cupidbot.util.tile.Rs2Tile;


import java.util.*;

import static net.runelite.client.plugins.cupidbot.util.Global.sleep;
import static net.runelite.client.plugins.cupidbot.util.Global.sleepUntil;

public final class BossHandler {

    private final int healthPercentage;
    private final int prayerPercentage;
    private final boolean debugLogging;

    public BossHandler(MoonsOfPerilConfig cfg) {
        this.healthPercentage = cfg.healthPercentage();
        this.prayerPercentage = cfg.prayerPercentage();
        this.debugLogging = cfg.debugLogging();
    }

    /** Walks to the chosen boss lobby.
     *
     * Loops the walker with a reached-distance tolerance. The previous implementation called
     * {@code walkWithState(bossWorldPoint, 0)} (exact-tile match) followed by a
     * {@code walkFastCanvas(bossWorldPoint)} fallback; when the walker exited early on a single
     * pass (e.g. cross-region transport, door handler interrupt) the canvas-click fallback tried
     * to click a tile dozens of tiles off-screen, {@code LocalPoint.fromWorld} returned null,
     * and the plugin looped forever without the player ever moving.
     */
    public void walkToBoss(Rs2InventorySetup inventorySetup, String bossName, WorldPoint bossWorldPoint) {
        if (inventorySetup != null) {
            equipInventorySetup(inventorySetup);
        }
        if (debugLogging) {CupidBot.log("Walking to " + bossName + " lobby");}

        final int reachedDistance = 3;
        final long deadlineMs = System.currentTimeMillis() + 90_000L;
        while (System.currentTimeMillis() < deadlineMs) {
            WorldPoint loc = Rs2Player.getWorldLocation();
            if (loc != null && loc.distanceTo(bossWorldPoint) <= reachedDistance) {
                break;
            }
            Rs2Walker.walkWithState(bossWorldPoint, reachedDistance);
            sleep(600);
        }

        WorldPoint finalLoc = Rs2Player.getWorldLocation();
        if (finalLoc != null && finalLoc.distanceTo(bossWorldPoint) <= reachedDistance) {
            if (debugLogging) {CupidBot.log("Arrived at " + bossName + " lobby");}
            return;
        }
        if (debugLogging) {CupidBot.log("Something went wrong. Did not arrive at " + bossName + " lobby");}
    }

    /** True while the “boss check mark” widget is hidden on-screen. i.e. the boss is alive*/
    public boolean bossIsAlive(String bossName, int aliveWidgetId) {
        if (debugLogging) {CupidBot.log(bossName + " is alive: " + Rs2Widget.isHidden(aliveWidgetId));}
        return Rs2Widget.isHidden(aliveWidgetId);
    }

    /** Interacts with the Boss statue and waits for the player to be teleported into Boss Arena*/
    public void enterBossArena(String bossName, int bossStatueID, WorldPoint bossWorldPoint) {
        if (!Rs2Player.getWorldLocation().equals(bossWorldPoint)) {
            if (Rs2Walker.walkFastCanvas(bossWorldPoint)) {
                if (debugLogging) {CupidBot.log("Walking to statue tile");}
            }
            sleepUntil(() -> Rs2Player.getWorldLocation().equals(bossWorldPoint));
        }
        if (CupidBot.getRs2TileObjectCache().query().interact(bossStatueID, "Use")) {
            if (debugLogging) {CupidBot.log("Entering " + bossName + " arena");}
            sleepUntil(() -> !Rs2Player.getWorldLocation().equals(bossWorldPoint),5_000);
        }
    }

    /** 1. Equips the player's chosen weapon + offhand
     * 2. Eats food if required
     * 3. Drinks potions if required
     * 4. Turns on Player's best offensive melee prayer*/
    public void fightPreparation(Rs2InventorySetup inventorySetup) {
        equipInventorySetup(inventorySetup);
        sleep(600);
        eatIfNeeded();
        sleep(600);
        drinkIfNeeded();
        sleep(600);
        Rs2Player.toggleRunEnergy(true);
        Rs2Camera.resetPitch();
        Rs2Camera.resetZoom();
    }

    /**
     * Equips the inventory setup.
     */
    public void equipInventorySetup(Rs2InventorySetup inventorySetup) {
        if (!inventorySetup.doesEquipmentMatch()) {
            inventorySetup.wearEquipment();
        }
    }

    /**
     * Eats food if hitpoints below percentage threshold
     */
    public void eatIfNeeded() {
        Rs2Player.eatAt(healthPercentage);
    }

    /**
     * Drinks prayer potion if prayer points below percentage threshold
     */
    public void drinkIfNeeded() {
        int maxPrayer = Rs2Player.getRealSkillLevel(Skill.PRAYER);
        int minimumPrayerPoint = (maxPrayer * prayerPercentage) / 100;
        if (!Rs2Player.hasMoonlightActive()) {
            Rs2Player.drinkPrayerPotion();
        }
        Rs2Player.drinkPrayerPotionAt(minimumPrayerPoint);
    }

    /**
     * Turns on Player's best offensive melee prayer and returns true
     */
    public static void meleePrayerOn() {
        Rs2Prayer.toggle(Objects.requireNonNull(Rs2Prayer.getBestMeleePrayer()), true);
    }

    /**
     * Returns true if the sigil NPC (the highlighted attack tile) is present
     */
    public static boolean isNormalAttackSequence(int sigilNpcID) {
        return CupidBot.getRs2NpcCache().query().withId(sigilNpcID).nearest() != null;
    }

    /**
     * “Normal” attack phase: follow ≤ 3 sigil squares, stand on the
     * matching attack tile, and keep attacking the boss.
     *
     * @param sigilNpcID  NPC ID of the 2×2 sigil marker
     * @param bossNpcID   NPC ID of the Eclipse boss
     * @param attackTiles WorldPoints from Locations enum
     */
    public void normalAttackSequence(int sigilNpcID,
                                            int bossNpcID,
                                            WorldPoint[] attackTiles, Rs2InventorySetup inventorySetup)
    {
        if (debugLogging) {CupidBot.log("Script has entered the normal attack sequence loop");}
        WorldPoint lastSigilSW = null;
        WorldPoint currentTarget = null;
        int sigilMoves = 0;
        equipInventorySetup(inventorySetup);
        sleep(150);
        meleePrayerOn();
        Rs2Player.toggleRunEnergy(true);
        sleep(150);

        while (sigilMoves <= 3 && isNormalAttackSequence(sigilNpcID))
        {
            /* 1 ─ detect a new sigil square */
            Rs2NpcModel sigil = CupidBot.getRs2NpcCache().query().withId(sigilNpcID).nearest();
            if (sigil == null) {
                sleep(300);
                if (debugLogging) {CupidBot.log("Sigil not found. Breaking out of sequence");}
                break;
            }
            WorldPoint sigilLocation = sigil.getWorldLocation();
            if (!sigilLocation.equals(lastSigilSW)) {
                ++sigilMoves;
                lastSigilSW = sigilLocation;

                /* 2 ─ choose *once* the nearest predefined attack tile ≤1 away */
                currentTarget = sigilLocation;
                for (WorldPoint atk : attackTiles) {
                    if (atk.distanceTo(sigilLocation) <= 1) {
                        currentTarget = atk;
                        break;
                    }
                }
                if (debugLogging) {CupidBot.log("Sigil #" + sigilMoves + ": target tile = " + currentTarget);}
            }

            /* 3 ─ run onto the target tile if not already there */
            if (Rs2Player.distanceTo(currentTarget) > 1) {
                if (debugLogging) {CupidBot.log("Running to attack tile target location: " + currentTarget);}
                if (Rs2Walker.walkFastCanvas(currentTarget, true)) {
                    final WorldPoint dest = currentTarget;
                    sleepUntil(() -> Rs2Player.getWorldLocation().equals(dest), 3_000);
                }
            }

            /* 4 ─ attack the boss whenever not in combat */
            Rs2NpcModel boss = CupidBot.getRs2NpcCache().query().withId(bossNpcID).nearest();
            if (boss != null && !Rs2Combat.inCombat()) {
                if (debugLogging) {CupidBot.log("Attacking the boss");}
                CupidBot.getRs2NpcCache().query().withId(bossNpcID).interact("Attack");
            }

            sleep(300);
        }
        if (debugLogging) {CupidBot.log("Breaking out of the normal attack sequence");}
    }

    /** True if the WorldPoint param is located on a dangerous tile*/
    public static boolean inDanger(WorldPoint location) {
        return Rs2Tile.getDangerousGraphicsObjectTiles().containsKey(location);
    }

    /** Runs the player out of the arena */
    public void bossBailOut(WorldPoint bailOutLocation) {
        int exitStairsGroundObjectID = 53003;
        long endTime = System.currentTimeMillis() + 10_000;

        while (System.currentTimeMillis() < endTime) {
            if (CupidBot.getRs2TileObjectCache().query().interact(exitStairsGroundObjectID)) {
                sleepUntil(() -> Rs2Widget.isWidgetVisible(Widgets.BOSS_HEALTH_BAR.getID()),5_000);
                if (debugLogging) {CupidBot.log("Successfully bailed out of the boss arena");}
                return;
            }

            if (debugLogging) {CupidBot.log("Couldn't interact with the door. Attempting to move closer");}
            Rs2Walker.walkFastCanvas(bailOutLocation, true);
            sleep(600);
        }
        if (debugLogging) {CupidBot.log("Timeout: Failed to bail out of the boss arena after 10 seconds.");}
    }

    /** If current run energy is less than 80%, recharges run energy at a campfire located on the world canvas */
    public static void rechargeRunEnergy() {
        if (CupidBot.getRs2TileObjectCache().query().withId(ObjectID.PMOON_RANGE).nearest() != null && Rs2Player.getRunEnergy() <=80) {
            CupidBot.getRs2TileObjectCache().query().interact(ObjectID.PMOON_RANGE, "Make-cuppa");
            sleep(600);
        }
    }
}
