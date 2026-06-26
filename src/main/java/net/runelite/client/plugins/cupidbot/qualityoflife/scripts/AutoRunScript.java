package net.runelite.client.plugins.cupidbot.qualityoflife.scripts;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.qualityoflife.QoLConfig;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

public class AutoRunScript extends Script {

    @Inject
    public ConfigManager configManager;


    public boolean run(QoLConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!CupidBot.isLoggedIn()) return;
                if (!super.run()) {
                    return;
                }
                if(CupidBot.useStaminaPotsIfNeeded != config.autoStamina()) {
                    configManager.setConfiguration("QoL", "autoStamina", CupidBot.useStaminaPotsIfNeeded);
                }
                if(CupidBot.runEnergyThreshold/100 != config.staminaThreshold()) {
                    configManager.setConfiguration("QoL", "staminaThreshold", CupidBot.runEnergyThreshold/100);
                }
            } catch (Exception ex) {
                CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    public void shutdown() {
        super.shutdown();
    }
}
