package net.runelite.client.plugins.cupidbot.aiomagic.scripts;

import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.aiomagic.AIOMagicPlugin;
import net.runelite.client.plugins.cupidbot.aiomagic.enums.MagicState;
import net.runelite.client.plugins.cupidbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.cupidbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.cupidbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.cupidbot.util.magic.Rs2Magic;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

public class TeleportScript extends Script {

    private MagicState state = MagicState.CASTING;
    private final AIOMagicPlugin plugin;

    @Inject
    public TeleportScript(AIOMagicPlugin plugin) {
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

                if (state == null) {
                    CupidBot.showMessage("Unable to evaluate state");
                    shutdown();
                    return;
                }

                switch (state) {
                    case CASTING:
                        if (!Rs2Magic.hasRequiredRunes(plugin.getTeleportSpell().getRs2Spell())) {
                            CupidBot.showMessage("Out of runes for " + plugin.getTeleportSpell().name());
                            shutdown();
                            return;
                        }

                        if (!Rs2Magic.cast(plugin.getTeleportSpell().getRs2Spell().getMagicAction())) {
                            CupidBot.log("Unable to cast " + plugin.getTeleportSpell().getRs2Spell().name());
                        }
                        sleep(2000, 2100);
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
