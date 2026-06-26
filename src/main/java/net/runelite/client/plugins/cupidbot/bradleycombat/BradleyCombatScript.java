package net.runelite.client.plugins.cupidbot.bradleycombat;

import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;

import java.util.concurrent.TimeUnit;

public class BradleyCombatScript extends Script {

    public boolean run(BradleyCombatConfig config) {

        CupidBot.enableAutoRunOn = true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try {
                if (!CupidBot.isLoggedIn()) return;
                if (!super.run()) return;
                // TODO: Logic for potions and food coming soon.
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}