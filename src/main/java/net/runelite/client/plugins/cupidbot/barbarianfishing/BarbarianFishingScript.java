package net.runelite.client.plugins.cupidbot.barbarianfishing;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.FishingSpot;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.cupidbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.cupidbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.cupidbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.cupidbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.cupidbot.barbarianfishing.Rs2DropUtils;
import net.runelite.client.plugins.cupidbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.math.Rs2Random;
import net.runelite.client.plugins.cupidbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.cupidbot.util.npc.Rs2Npc.validateInteractable;

public class BarbarianFishingScript extends Script {
    private long specReadyTime = 0;
    private boolean specActivated = false;
    private int animationTimeout = 600;
    private BarbarianFishingConfig config;

    public boolean run(BarbarianFishingConfig config) {
        this.config = config;
        specReadyTime = 0;
        specActivated = false;
        animationTimeout = 600;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyFishingSetup();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !CupidBot.isLoggedIn() || !Rs2Inventory.hasItem("feather") || !Rs2Inventory.hasItem("Barbarian rod")) {
                    return;
                }

                if (Rs2Equipment.isWearing(ItemID.DRAGON_HARPOON)) {
                    if (Rs2Combat.getSpecEnergy() == 1000) {
                        if (specReadyTime == 0) {
                            double delay = Math.max(0, Rs2Random.gaussRand(45000, 30000));
                            specReadyTime = System.currentTimeMillis() + (long) delay;
                        } else if (!specActivated && System.currentTimeMillis() >= specReadyTime) {
                            if (Rs2Combat.setSpecState(true)) {
                                specActivated = true;
                            }
                        }
                    } else {
                        specReadyTime = 0;
                        specActivated = false;
                    }
                }

                if (Rs2Inventory.isFull()) {
                    dropInventoryItems(config);
                    return;
                }

                if (Rs2AntibanSettings.actionCooldownActive) return;

                if (Rs2Player.isAnimating(animationTimeout) || Rs2Player.isMoving())
                    return;

                animationTimeout = (int) Rs2Random.truncatedGauss(600, 1200, 2.0);

                Rs2NpcModel fishingspot = findFishingSpot();
                if (fishingspot == null) {
                    return;
                }

                if (!Rs2Camera.isTileOnScreen(fishingspot.getLocalLocation())) {
                    validateInteractable(fishingspot.getNpc());
                }

                if (fishingspot.click("Use-rod")) {
                    Rs2Antiban.actionCooldown();
                    Rs2Antiban.takeMicroBreakByChance();
                }
            } catch (Exception ex) {
                CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
            }

        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private Rs2NpcModel findFishingSpot() {
        return CupidBot.getRs2NpcCache().query()
                .withIds(FishingSpot.BARB_FISH.getIds())
                .nearest();
    }

    private void dropInventoryItems(BarbarianFishingConfig config) {
        InteractOrder dropOrder = config.dropOrder() == InteractOrder.RANDOM ? InteractOrder.random() : config.dropOrder();
        Rs2DropUtils.dropAllHumanized(x -> {
            String name = x.getName().toLowerCase();
            return name.contains("leaping") || name.contains("roe") || name.contains("caviar");
        }, dropOrder);
    }

    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
