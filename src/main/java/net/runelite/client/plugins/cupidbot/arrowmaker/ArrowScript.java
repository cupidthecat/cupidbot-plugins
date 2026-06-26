package net.runelite.client.plugins.cupidbot.arrowmaker;

import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.arrowmaker.ArrowConfig;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.cupidbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;

public class ArrowScript extends Script {

    public boolean run(ArrowConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
				if (!super.run()) return;
				if (!CupidBot.isLoggedIn()) return;
                if (config.ARROWBool()) {
                    checkAndUseItem(config.ARROW().getItem1(), config.ARROW().getItem2());
                }
                if (config.BOLTBool()) {
                    checkAndUseItem(config.BOLT().getItem1(), config.BOLT().getItem2());
                }
                if (config.DARTBool()) {
                    checkAndUseItem(config.DART().getItem1(), config.DART().getItem2());
                }
                if (config.TIPPINGBool()) {
                    checkAndUseItem(config.TIP().getItem1(), config.TIP().getItem2());
                }
                if (config.DRAGONTIPPINGBool()) {
                    checkAndUseItem(config.DragonTIP().getItem1(), config.DragonTIP().getItem2());
                }
            } catch (Exception ex) {
                CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void checkAndUseItem(String item1, String item2) {
        if (Rs2Inventory.count(item1) > 0 && Rs2Inventory.count(item2) > 0) {
            Rs2Inventory.combine(item1, item2);
            handleSleep();
        }
    }
    private void handleSleep(){
        sleepUntilOnClientThread(() -> Rs2Widget.getWidget(17694733) != null);
        Rs2Keyboard.keyPress('1');
        sleep(12000,14000);
    }
}
