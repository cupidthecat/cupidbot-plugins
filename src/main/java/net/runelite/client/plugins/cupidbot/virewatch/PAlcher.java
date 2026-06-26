package net.runelite.client.plugins.cupidbot.virewatch;

import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.cupidbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.cupidbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.cupidbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

public class PAlcher extends Script {

    PVirewatchKillerPlugin plugin;

    public boolean run(PVirewatchKillerConfig config, PVirewatchKillerPlugin plugin) {
        CupidBot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            this.plugin = plugin;
            try {
                if (!CupidBot.isLoggedIn()) return;
                if (!super.run()) return;
                if(!config.alchItems()) return;

                if(Rs2Inventory.contains("Nature rune", "Fire rune")) {
                    if(Rs2Inventory.contains("Rune dagger")) {
                        alchItem("Rune dagger");
                    } else if (Rs2Inventory.contains("Adamant platelegs")) {
                        alchItem("Adamant platelegs");
                    } else if (Rs2Inventory.contains("Adamant platebody")) {
                        alchItem("Adamant platebody");
                    } else if (Rs2Inventory.contains("Rune full helm")) {
                        alchItem("Rune full helm");
                    } else if (Rs2Inventory.contains("Rune kiteshield")) {
                        //alchItemWithConfirm("Rune kiteshield");

                    }

                }


            } catch (Exception ex) {
                CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 2000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void alchItem(String item) {
        plugin.alchedItems++;
        plugin.alchingDrop = true;
        Rs2Magic.alch(item);
        sleepUntil(() -> !Rs2Inventory.contains(item));
        plugin.alchingDrop = false;
        Rs2Tab.switchTo(InterfaceTab.INVENTORY);

    }

    // Does not work yet
    private void alchItemWithConfirm(String item) {
        plugin.alchedItems++;
        plugin.alchingDrop = true;
        Rs2Magic.alch(item);
        Rs2Widget.sleepUntilHasWidget("Really cast High");
        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        sleep(300);
        Rs2Keyboard.keyPress(KeyEvent.VK_1);
        plugin.alchingDrop = false;
        Rs2Tab.switchTo(InterfaceTab.INVENTORY);
    }
    @Override
    public void shutdown() {
        super.shutdown();
    }
}
