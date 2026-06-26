package net.runelite.client.plugins.cupidbot.aiomagic.scripts;

import net.runelite.api.Skill;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.aiomagic.AIOMagicPlugin;
import net.runelite.client.plugins.cupidbot.aiomagic.enums.MagicState;
import net.runelite.client.plugins.cupidbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.cupidbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.cupidbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.cupidbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

public class TeleAlchScript extends Script {

    private MagicState state = MagicState.CASTING;
    private final AIOMagicPlugin plugin;

    @Inject
    public TeleAlchScript(AIOMagicPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean run() {
        CupidBot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyGeneralBasicSetup();
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2Antiban.setActivity(Activity.TELEPORT_TRAINING);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!CupidBot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();


                switch (state) {
                    case CASTING:
                        if (plugin.getAlchItemNames().isEmpty() || plugin.getAlchItemNames().stream().noneMatch(Rs2Inventory::hasItem)) {
                            CupidBot.log("Missing alch items...");
                            return;
                        }

                        if (!Rs2Magic.hasRequiredRunes(plugin.getAlchSpell())) {
                            CupidBot.showMessage("Out of runes for alchemy");
                            shutdown();
                            return;
                        }

                        Rs2ItemModel alchItem = plugin.getAlchItemNames().stream()
                                .filter(Rs2Inventory::hasItem)
                                .map(Rs2Inventory::get)
                                .findFirst()
                                .orElse(null);

                        if (alchItem == null) {
                            CupidBot.log("Missing alch items...");
                            return;
                        }

                        if (Rs2AntibanSettings.naturalMouse) {
                            int inventorySlot = Rs2Player.getSkillRequirement(Skill.MAGIC, 55) ? 12 : 4;
                            if (alchItem.getSlot() != inventorySlot) {
                                Rs2Inventory.moveItemToSlot(alchItem, inventorySlot);
                                return;
                            }
                        }

                        Rs2Magic.alch(alchItem, 100, 150);

                        if (!Rs2Magic.hasRequiredRunes(plugin.getTeleportSpell().getRs2Spell())) {
                            CupidBot.showMessage("Out of runes for " + plugin.getTeleportSpell().name());
                            shutdown();
                            return;
                        }

                        Rs2Magic.cast(plugin.getTeleportSpell().getRs2Spell().getMagicAction());

                        sleep(900, 950);

                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
