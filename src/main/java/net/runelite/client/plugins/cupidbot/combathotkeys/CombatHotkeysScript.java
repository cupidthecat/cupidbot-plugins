package net.runelite.client.plugins.cupidbot.combathotkeys;

import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.util.Global;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;


public class CombatHotkeysScript extends Script {
    public boolean dance = false;

    public boolean run(CombatHotkeysConfig config) {
        CupidBot.enableAutoRunOn = true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!CupidBot.isLoggedIn()) return;
                if (!super.run()) return;

                if (dance) {
                    if(Rs2Player.getWorldLocation().equals(config.tile1())){
                        Rs2Walker.walkFastCanvas(config.tile2());
                        Global.sleepUntil(() -> Rs2Player.getWorldLocation().equals(config.tile2()), 1000);
                    }
                    else if(Rs2Player.getWorldLocation().equals(config.tile2())){
                        Rs2Walker.walkFastCanvas(config.tile1());
                        Global.sleepUntil(() -> Rs2Player.getWorldLocation().equals(config.tile1()), 1000);
                    }
                    else{
                        Rs2Walker.walkFastCanvas(config.tile1());
                        Global.sleepUntil(() -> Rs2Player.getWorldLocation().equals(config.tile1()), 1000);
                    }
                }
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