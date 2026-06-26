package net.runelite.client.plugins.cupidbot.giantmole;

import net.runelite.api.Skill;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.cupidbot.util.item.Rs2ExplorersRing;
import net.runelite.client.plugins.cupidbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.cupidbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.cupidbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.cupidbot.util.math.Rs2Random;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.settings.Rs2Settings;
import net.runelite.client.plugins.cupidbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.cupidbot.util.widget.Rs2Widget;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class HighAlchScript extends Script {

    private static final int MIN_TICKS = (int) Math.ceil(30.0 / 0.6);
    private static final int MAX_TICKS = (int) Math.floor(45.0 / 0.6);
    private int lastAlchCheckTick = -1;
    private int nextAlchIntervalTicks = 0;

    public boolean run(GiantMoleConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try
            {
                if (!CupidBot.isLoggedIn() || !super.run() || !config.toggleHighAlchProfitable())
                {
                    return;
                }
                List<Rs2ItemModel> items = Rs2Inventory.getList(Rs2ItemModel::isHaProfitable);

                if (items.isEmpty())
                {
                    if (Rs2Tab.getCurrentTab() != InterfaceTab.INVENTORY)
                    {
                        Rs2Tab.switchTo(InterfaceTab.INVENTORY);
                    }
                    return;
                }

                int currentTick = CupidBot.getClient().getTickCount();

                if (lastAlchCheckTick != -1 && currentTick - lastAlchCheckTick < nextAlchIntervalTicks)
                {
                    return;
                }

                lastAlchCheckTick = currentTick;
                nextAlchIntervalTicks = Rs2Random.nextInt(MIN_TICKS, MAX_TICKS, 1.5, true);

                if (Rs2ExplorersRing.hasRing() && Rs2ExplorersRing.hasCharges())
                {
                    for (Rs2ItemModel item : items)
                    {
                        if (!isRunning())
                        {
                            break;
                        }

                        Rs2ExplorersRing.highAlch(item);
                    }
                    Rs2ExplorersRing.closeInterface();
                }
                else if (Rs2Player.getSkillRequirement(Skill.MAGIC, Rs2Spells.HIGH_LEVEL_ALCHEMY.getRequiredLevel()) && Rs2Magic.hasRequiredRunes(Rs2Spells.HIGH_LEVEL_ALCHEMY))
                {
                    for (Rs2ItemModel item : items)
                    {
                        if (!isRunning())
                        {
                            break;
                        }

                        Rs2Magic.alch(item);
                        if (item.getHaPrice() > Rs2Settings.getMinimumItemValueAlchemyWarning())
                        {
                            sleepUntil(() -> Rs2Widget.hasWidget("Proceed to cast High Alchemy on it"));
                            if (Rs2Widget.hasWidget("Proceed to cast High Alchemy on it"))
                            {
                                Rs2Keyboard.keyPress('1');
                                Rs2Player.waitForAnimation();
                            }
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    public void shutdown() {
        super.shutdown();
    }
}
