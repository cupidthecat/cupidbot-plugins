// filepath: c:\Users\marcu\IdeaProjects\cupidbot\runelite-client\src\main\java\net\runelite\client\plugins\cupidbot\kittentracker\KittenScript.java
package net.runelite.client.plugins.cupidbot.kittentracker;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;

import java.util.concurrent.TimeUnit;

public class KittenScript extends Script {

    private KittenPlugin kittenPlugin;

    public boolean run(KittenConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!CupidBot.isLoggedIn()) return;
                if (!super.run()) {
                }



            }
            catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleKittenNeeds(KittenConfig config) {
        if (config.kittenHungryOverlay()
            && Rs2Inventory.contains(ItemID.TBWT_RAW_KARAMBWANJI)
            && (KittenPlugin.HUNGRY_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) >= kittenPlugin.getTimeBeforeHungry()) {
            feedKitten();
        }
        if (config.kittenAttentionOverlay()
            && Rs2Inventory.contains(ItemID.BALL_OF_WOOL)
            && (KittenPlugin.ATTENTION_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) >= kittenPlugin.getTimeBeforeNeedingAttention()) {
            giveKittenAttention();
        }
    }

    private void feedKitten() {
        CupidBot.getRs2NpcCache().query().withName("Kitten").toListOnClientThread().stream().findFirst().ifPresent(kitten -> Rs2Inventory.useItemOnNpc(ItemID.TBWT_RAW_KARAMBWANJI, kitten.getNpc()));
        sleep(1000, 2000);
    }

    private void giveKittenAttention() {
        CupidBot.getRs2NpcCache().query().withName("Kitten").toListOnClientThread().stream().findFirst().ifPresent(kitten -> Rs2Inventory.useItemOnNpc(ItemID.BALL_OF_WOOL, kitten.getNpc()));
        sleep(1000, 2000);
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
