package net.runelite.client.plugins.cupidbot.sandminer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.cupidbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.cupidbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.cupidbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.cupidbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.cupidbot.util.math.Rs2Random;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.cupidbot.sandminer.GabulhasSandMinerInfo.botStatus;
import static net.runelite.client.plugins.cupidbot.sandminer.GabulhasSandMinerInfo.states;

@Slf4j
public class GabulhasSandMinerScript extends Script {
    private final WorldPoint miningPoint = new WorldPoint(3165, 2905, 0);
    private final WorldPoint grinder = new WorldPoint(3150, 2908, 0);

    public boolean run(GabulhasSandMinerConfig config) {
        CupidBot.enableAutoRunOn = false;
        if (config.turboMode()) {
            Rs2Camera.setZoom(Rs2Random.randomGaussian(100, 20));
            Rs2Camera.setYaw((Rs2Random.dicePercentage(50) ? Rs2Random.randomGaussian(750, 50) : Rs2Random.randomGaussian(1700, 50)));
            Rs2Camera.setPitch(Rs2Random.betweenInclusive(418, 512));
        }
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!CupidBot.isLoggedIn()) return;
                if (!super.run()) return;
                switch (botStatus) {
                    case Mining:
                        if (config.useHumidify()) humidifyIfNeeded();
                        miningLoop(config);
                        handleSafety();
                        botStatus = states.Depositing;
                        break;
                    case Depositing:
                        deposit(config);
                        botStatus = states.Mining;
                        break;
                }
            } catch (Exception ex) {
                CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleSafety() {
        var health = Rs2Player.getHealthPercentage();
        if (health < 25) {
            CupidBot.log("Health is low: " + health + "%, banking for safety.");
            Rs2Bank.walkToBank();
            Rs2Player.logout();
            CupidBot.stopPlugin(GabulhasSandMinerPlugin.class);
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }


    private void miningLoop(GabulhasSandMinerConfig config) {
        boolean firstRock = true;
        if (!config.turboMode()) {
            Rs2Walker.walkTo(miningPoint, 0);
            sleep(100, 4000);
        }
        while (!Rs2Inventory.isFull() && super.isRunning()) {
            // Drop empty waterskins if not using humidify (only when idle)
            if (!config.useHumidify() && !Rs2Player.isAnimating()) {
                dropEmptyWaterskins();
            }

            if (config.hopworldclose()) {
                while (Rs2Player.hopIfPlayerDetected(1, 3000, 100) && super.isRunning()) {
                    sleepUntil(() -> CupidBot.getClient().getGameState() == GameState.HOPPING);
                    sleepUntil(() -> CupidBot.getClient().getGameState() == GameState.LOGGED_IN);
                    sleep(1200, 2000);
                }
            } else if (config.hopworldlong()) {
                while (Rs2Player.hopIfPlayerDetected(1, 3000, 6) && super.isRunning()) {
                    sleepUntil(() -> CupidBot.getClient().getGameState() == GameState.HOPPING);
                    sleepUntil(() -> CupidBot.getClient().getGameState() == GameState.LOGGED_IN);
                    sleep(1200, 2000);
                }
            }



            if (!config.turboMode()) sleep(Rs2Random.nextInt(300, 5000, 0.1, true));
            if (!Rs2Player.isInteracting() || !Rs2Player.isAnimating()) {
                if (config.turboMode()) {
                    if (firstRock) {
                        WorldPoint innerMiningPoint = (Rs2Random.dicePercentage(50)) ?
                                new WorldPoint(3164, 2905, 0) : new WorldPoint(3166, 2905, 0);
                        var innerSandstoneRock = CupidBot.getRs2TileObjectCache().query()
                                .withName("Sandstone rocks")
                                .nearestOnClientThread(innerMiningPoint, 0);
                        if (innerSandstoneRock != null) innerSandstoneRock.click("Mine");
                        Rs2Player.waitForXpDrop(Skill.MINING, 15000);
                        Rs2Antiban.actionCooldown();
                        firstRock = false;
                        continue;
                    }
                }
                var sandstoneRock = CupidBot.getRs2TileObjectCache().query()
                        .withName("Sandstone rocks")
                        .nearestOnClientThread(miningPoint, 5);
                if (sandstoneRock != null) {
                    sandstoneRock.click("Mine");
                    if (config.turboMode()) {
                        Rs2Player.waitForXpDrop(Skill.MINING, 15000);
                    } else {
                        Rs2Player.waitForAnimation();
                    }
                    Rs2Antiban.actionCooldown();
                }
            }
        }
    }

    private void humidifyIfNeeded() {
        if (Rs2Equipment.isWearing("Circlet of water")) {
            return;
        }
        if (Rs2Magic.isSpellbook(Rs2Spellbook.LUNAR) && !Rs2Inventory.hasItem(ItemID.WATER_SKIN1, ItemID.WATER_SKIN2, ItemID.WATER_SKIN3, ItemID.WATER_SKIN4)) {
            System.out.println("Humidified");
            Rs2Magic.cast(MagicAction.HUMIDIFY);
            sleepUntilOnClientThread(() -> Rs2Inventory.hasItem("Waterskin(4)"));
            Rs2Antiban.actionCooldown();
            Rs2Antiban.takeMicroBreakByChance();
            sleep(1000, 2000);
        }
    }

    private void dropEmptyWaterskins() {
        while (Rs2Inventory.hasItem(ItemID.WATER_SKIN0)) {
            Rs2Inventory.drop(ItemID.WATER_SKIN0);
            sleep(550,650);
        }
    }

    private void deposit(GabulhasSandMinerConfig config) {
        if (!config.turboMode()) Rs2Walker.walkTo(grinder);
        var grinderObj = CupidBot.getRs2TileObjectCache().query()
                .withId(26199)
                .nearest(grinder, 5);
        if (grinderObj != null) grinderObj.click("Deposit");
        while (Rs2Inventory.contains("Sandstone (1kg)", "Sandstone (2kg)", "Sandstone (5kg)", "Sandstone (10kg)") && super.isRunning()) {
            if (!config.turboMode()) {
                sleep(100, 3000);
            } else {
                sleepGaussian(300, 200);
            }
        }
    }
}

