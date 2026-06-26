package net.runelite.client.plugins.cupidbot.virewatch;

import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;

public class PVirewatchScript extends Script {

    public boolean run(PVirewatchKillerConfig config, PVirewatchKillerPlugin plugin) {
        CupidBot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!CupidBot.isLoggedIn()) return;
                if (!super.run()) return;
                Rs2Combat.enableAutoRetialiate();

                WorldPoint playerLocation = CupidBot.getClientThread().invoke(() -> {
                    Player player = CupidBot.getClient().getLocalPlayer();
                    return player == null ? null : player.getWorldLocation();
                });
                if (playerLocation == null || plugin.startingLocation == null || plugin.fightArea == null) {
                    return;
                }

                if(plugin.fightArea.contains(playerLocation)) {
                    CupidBot.status = "Figthing";
                }

                if(!plugin.startingLocation.equals(playerLocation)) {
                    if(plugin.ticksOutOfArea > config.tickToReturn() || plugin.countedTicks > config.tickToReturnCombat()) {
                        Rs2Walker.walkTo(plugin.startingLocation, 0);
                    }
                }

                Rs2Player.eatAt(config.hitpoints());

                if(CupidBot.getClient().getBoostedSkillLevel(Skill.PRAYER) <= config.prayAt()) {
                    plugin.rechargingPrayer = true;
                    var statue = CupidBot.getRs2TileObjectCache().query().withId(39234).nearest();
                    if(statue != null) {
                        Rs2Walker.walkTo(statue.getWorldLocation(), 1);
                        sleepUntil(statue::isReachable);
                        if(statue.isReachable()) {
                            CupidBot.status = "RECHARGING PRAYER";
                            statue.click();
                            sleep(100);
                            plugin.rechargingPrayer = false;
                            if(Rs2Player.isInteracting()) {
                                sleepUntil(() -> CupidBot.getClient().getBoostedSkillLevel(Skill.PRAYER) > config.prayAt());
                                CupidBot.status = "WALKING TO STARTING POINT";
                                Rs2Walker.walkTo(plugin.startingLocation, 0);

                            }
                        }
                    }

                }

            } catch (Exception ex) {
                if (Script.isInterruption(ex)) {
                    Thread.currentThread().interrupt();
                    return;
                }
                CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
