package net.runelite.client.plugins.cupidbot.aerialfishing;

import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.cupidbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.cupidbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.cupidbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.cupidbot.util.math.Rs2Random;
import net.runelite.client.plugins.cupidbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.cupidbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.cupidbot.util.npc.Rs2Npc.validateInteractable;

public class AerialFishingScript extends Script {
    public static int timeout = 0;
    public static final WorldPoint FISHING_SPOT = new WorldPoint(1376, 3629, 0);

    public boolean run(AerialFishingConfig config) {
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyFishingSetup();
        Rs2AntibanSettings.actionCooldownChance = 0.14;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.takeMicroBreaks = true;
        Rs2AntibanSettings.microBreakChance = 0.01;
        Rs2AntibanSettings.microBreakDurationLow = 1;
        Rs2AntibanSettings.microBreakDurationHigh = 5;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run() || !CupidBot.isLoggedIn() || !Rs2Inventory.hasItem("fish chunks", "king worm", "fish offcuts") || (!Rs2Equipment.isWearing(ItemID.AERIAL_FISHING_GLOVES_NO_BIRD) && !Rs2Equipment.isWearing(ItemID.AERIAL_FISHING_GLOVES_BIRD))) {
                return;
            }

            if (Rs2AntibanSettings.actionCooldownActive) return;


            if (Rs2Inventory.isFull() || (!Rs2Inventory.hasItem(ItemID.AERIAL_FISHING_PEARL) && Rs2Inventory.emptySlotCount() == 1)) {
                cutFish();
                return;
            }

            if (!Rs2Player.getWorldLocation().equals(FISHING_SPOT)) {
                Rs2Walker.walkTo(FISHING_SPOT, 0);
            }


            Rs2NpcModel fishingspot = findFishingSpot();
            if (fishingspot == null) {
                return;
            }
            if (Rs2Player.isInteracting()) {
                return;
            }

            if (!Rs2Camera.isTileOnScreen(fishingspot.getLocalLocation())) {
                validateInteractable(fishingspot.getNpc());
            }

            if (fishingspot.click()) {
                if (sleepUntil(Rs2Player::isInteracting, 1200)) {
                    sleepUntil(() -> Rs2Equipment.isWearing(ItemID.AERIAL_FISHING_GLOVES_BIRD), () -> {
                        if ((Rs2Inventory.emptySlotCount() <= 1 && Rs2Equipment.isWearing(ItemID.AERIAL_FISHING_GLOVES_NO_BIRD)) || (Rs2Inventory.emptySlotCount() == 0 && Rs2Equipment.isWearing(ItemID.AERIAL_FISHING_GLOVES_BIRD))) {
                            CupidBot.log("Empty slot count:" + Rs2Inventory.emptySlotCount());
                            Rs2ItemModel knife = Rs2Inventory.get(ItemID.KNIFE);
                            Rs2Inventory.hover(knife);

                        } else {
                            Rs2NpcModel preHoverSpot = findPreHoverSpot(fishingspot);
                            if (preHoverSpot != null) {
                                if (Rs2Npc.hoverOverActor(preHoverSpot.getNpc())) {

                                    if (Rs2Random.dicePercentage(20)) {
                                        CupidBot.getMouse().click();
                                    }
                                }
                            }
                        }
                    }, 5000, 100);
                    Rs2Antiban.actionCooldown();
                    Rs2Antiban.takeMicroBreakByChance();
                }
            }

        }, 0, 300, TimeUnit.MILLISECONDS);
        return true;
    }


    private Rs2NpcModel findFishingSpot() {
        return CupidBot.getRs2NpcCache().query().withId(NpcID.FISHING_SPOT_AERIAL).nearest();
    }

    private Rs2NpcModel findPreHoverSpot(Rs2NpcModel excludedSpot) {
        List<Rs2NpcModel> spots = CupidBot.getRs2NpcCache().query().withId(NpcID.FISHING_SPOT_AERIAL).toList();
        return spots.stream().filter(x -> x != excludedSpot).findFirst().orElse(null);
    }

    private void cutFish() {
        Rs2ItemModel randomFish = Rs2Inventory.getRandom(ItemID.AERIAL_FISHING_BLUEGILL, ItemID.AERIAL_FISHING_COMMON_TENCH, ItemID.AERIAL_FISHING_MOTTLED_EEL, ItemID.AERIAL_FISHING_GREATER_SIREN);
        Rs2ItemModel knife = Rs2Inventory.get(ItemID.KNIFE);
        Rs2Inventory.combine(knife, randomFish);
        sleepUntil(() -> !Rs2Inventory.hasItem(ItemID.AERIAL_FISHING_BLUEGILL, ItemID.AERIAL_FISHING_COMMON_TENCH, ItemID.AERIAL_FISHING_MOTTLED_EEL, ItemID.AERIAL_FISHING_GREATER_SIREN), 1000 * 60);
        Rs2Random.waitEx(2000, 2000);
    }


    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
