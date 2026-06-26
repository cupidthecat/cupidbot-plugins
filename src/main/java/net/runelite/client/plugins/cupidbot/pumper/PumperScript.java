package net.runelite.client.plugins.cupidbot.pumper;

import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.pumper.PumperConfig;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;


public class PumperScript extends Script {

    public static boolean test = false;
    public boolean run(PumperConfig config) {
        CupidBot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!CupidBot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                if (!Rs2Player.isAnimating()) {
                    CupidBot.getRs2TileObjectCache().query().interact(9090, "operate");
                    sleep(50,200);
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}