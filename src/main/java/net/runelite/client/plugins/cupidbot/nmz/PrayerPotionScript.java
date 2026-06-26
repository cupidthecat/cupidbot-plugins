package net.runelite.client.plugins.cupidbot.nmz;

import net.runelite.api.Skill;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.cupidbot.util.math.Rs2Random;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class PrayerPotionScript extends Script {
    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!CupidBot.isLoggedIn()) return;
                if (!super.run()) return;
                if ((CupidBot.getClient().getBoostedSkillLevel(Skill.PRAYER) * 100) / CupidBot.getClient().getRealSkillLevel(Skill.PRAYER) > Rs2Random.between(25, 30))
                    return;
                List<Rs2ItemModel> potions = CupidBot.getClientThread().runOnClientThreadOptional(Rs2Inventory::getPotions).orElse(null);
                if (potions == null || potions.isEmpty()) {
                    return;
                }
                for (Rs2ItemModel potion : potions) {
                    if (potion.getName().toLowerCase().contains("prayer") || potion.getName().toLowerCase().contains("super restore") || potion.getName().toLowerCase().contains("moonlight potion")) {
                        Rs2Inventory.interact(potion, "drink");
                        sleep(1200, 2000);
                        Rs2Inventory.dropAll("Vial");
                        break;
                    }
                }
            } catch (Exception ex) {
                CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    public boolean run(NmzConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!config.togglePrayerPotions()) return;
                if ((CupidBot.getClient().getBoostedSkillLevel(Skill.PRAYER) * 100) / CupidBot.getClient().getRealSkillLevel(Skill.PRAYER) > Rs2Random.between(25, 30))
                    return;
                List<Rs2ItemModel> potions = CupidBot.getClientThread().runOnClientThreadOptional(Rs2Inventory::getPotions).orElse(null);
                if (potions == null || potions.isEmpty()) {
                    return;
                }
                for (Rs2ItemModel potion : potions) {
                    if (potion.getName().toLowerCase().contains("prayer")) {
                        Rs2Inventory.interact(potion, "drink");
                        sleep(1200, 2000);
                        Rs2Inventory.dropAll("Vial");
                        break;
                    }
                }
            } catch (Exception ex) {
                CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }
}
