package net.runelite.client.plugins.cupidbot.crafting.scripts;

import net.runelite.api.Skill;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.crafting.CraftingConfig;
import net.runelite.client.plugins.cupidbot.crafting.enums.BoltTips;
import net.runelite.client.plugins.cupidbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GemsScript extends Script implements ICraftingScript {

    public static double version = 1.0;

    @Override
    public String getName() {
        return "Gems";
    }

    @Override
    public String getVersion() {
        return String.valueOf(version);
    }

    @Override
    public String getState() {
        return CupidBot.status;
    }

    @Override
    public Map<String, String> getCustomProperties() {
        return Collections.emptyMap();
    }


    public boolean run(CraftingConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!CupidBot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!Rs2Player.getSkillRequirement(Skill.CRAFTING, config.gemType().getLevelRequired())) {
                    CupidBot.showMessage("Crafting level too low to craft " + config.gemType().getName());
                    shutdown();
                    return;
                }
                final boolean amethyst = (config.gemType().getName().contains("Amethyst"));
                final String uncutGemName = "uncut " + config.gemType().getName();

                if ((!Rs2Inventory.hasItem(uncutGemName) && !amethyst) || !Rs2Inventory.hasItem("chisel") || (amethyst && !Rs2Inventory.hasItem(21347))) {
                    CupidBot.status = "BANKING";
                    Rs2Bank.openBank();
                    if (Rs2Bank.isOpen()) {
                        Rs2Bank.depositAll("crushed gem");
                        Rs2Bank.depositAll(config.gemType().getName());
                        sleepUntil(() -> !Rs2Inventory.hasItem(config.gemType().getName()) && !Rs2Inventory.hasItem("crushed gem"));
                        if (Rs2Bank.hasItem(uncutGemName)) {
                            Rs2Bank.withdrawItem(true, "chisel");
                            Rs2Bank.withdrawAll(true, uncutGemName);
                        } else if (amethyst && Rs2Bank.hasItem(21347)) {
                            Rs2Bank.withdrawItem(true, "chisel");
                            Rs2Bank.withdrawAll(21347);
                        }
                        else
                        {
                            CupidBot.showMessage("You've ran out of materials!");
                            shutdown();
                            }
                        Rs2Bank.closeBank();
                        sleepUntil(() -> !Rs2Bank.isOpen());
                    }
                } else {
                    CupidBot.status = "CUTTING GEMS";
                    Rs2Inventory.use("chisel");
                    if (amethyst) {
                        Rs2Inventory.use(21347);
                        Rs2Widget.sleepUntilHasWidgetText("How many do you wish to make?", 270, 5, false, 5000);
                        Rs2Widget.clickWidget(config.gemType().getName(),true);
                        Rs2Widget.sleepUntilHasNotWidgetText("How many do you wish to make?", 270, 5, false, 5000);
                        sleep(3000);
                        sleepUntil(() -> !CupidBot.isGainingExp || !Rs2Inventory.hasItem(21347), 30000);
                    } else {
                        Rs2Inventory.use(uncutGemName);
                        sleep(600);
                        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                        sleep(4000);
                        sleepUntil(() -> !CupidBot.isGainingExp || !Rs2Inventory.hasItem(uncutGemName), 30000);

                        if (config.fletchIntoBoltTips()) {
                            CupidBot.status = "FLETCHING BOLT TIPS";
                            BoltTips boltTip = BoltTips.valueOf(config.gemType().name());
                            if (Rs2Player.getSkillRequirement(Skill.FLETCHING, boltTip.getFletchingLevelRequired()) &&
                                    Rs2Inventory.hasItem(config.gemType().getName()) &&
                                    Rs2Inventory.hasItem("chisel")) {
                                Rs2Inventory.use("chisel");
                                Rs2Inventory.use(config.gemType().getName());
                                sleep(600);
                                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                                sleep(4000);
                                sleepUntil(() -> !CupidBot.isGainingExp || !Rs2Inventory.hasItem(config.gemType().getName()), 30000);
                            }
                        }
                    }
                }
                CupidBot.status = "IDLE";
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        CupidBot.status = "IDLE";
    }
}