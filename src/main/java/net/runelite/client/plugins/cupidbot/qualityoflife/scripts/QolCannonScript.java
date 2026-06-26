package net.runelite.client.plugins.cupidbot.qualityoflife.scripts;

import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.qualityoflife.QoLConfig;
import net.runelite.client.plugins.cupidbot.util.gameobject.Rs2Cannon;

import java.util.concurrent.TimeUnit;

public class QolCannonScript extends Script {
    public boolean run(QoLConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!CupidBot.isLoggedIn()) return;
                if (!super.run() || !config.refillCannon()) return;
                if (Rs2Cannon.repair())
                    return;
                Rs2Cannon.refill();
            } catch(Exception ex) {
                CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 2000, TimeUnit.MILLISECONDS);
        return true;
    }
}
