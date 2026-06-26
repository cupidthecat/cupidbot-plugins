package net.runelite.client.plugins.cupidbot.aiofighter.combat;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.cupidbot.aiofighter.AIOFighterPlugin;
import net.runelite.client.plugins.cupidbot.aiofighter.enums.State;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;

public class SafeSpot extends Script {

    public WorldPoint currentSafeSpot = null;
    private boolean messageShown = false;

public boolean run(AIOFighterConfig config) {
    mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
        try {
            if (AIOFighterPlugin.getState().equals(State.BANKING) || AIOFighterPlugin.getState().equals(State.WALKING)) return;
            if (!CupidBot.isLoggedIn() || !super.run() || !config.toggleSafeSpot() || Rs2Player.isMoving()) return;

            currentSafeSpot = config.safeSpot();
            if (isDefaultSafeSpot(currentSafeSpot)) {
                if(!messageShown){
                    CupidBot.showMessage("Please set a safespot location");
                    messageShown = true;
                }
                return;
            }

			messageShown = false;

			if (!isPlayerAtSafeSpot(currentSafeSpot)) {
				Rs2Walker.walkFastCanvas(currentSafeSpot);
				CupidBot.pauseAllScripts.compareAndSet(false, true);
				sleepUntil(() -> isPlayerAtSafeSpot(currentSafeSpot));
				CupidBot.pauseAllScripts.compareAndSet(true, false);
			}


        } catch (Exception ex) {
            CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
    }, 0, 600, TimeUnit.MILLISECONDS);
    return true;
}

private boolean isDefaultSafeSpot(WorldPoint safeSpot) {
    return safeSpot.getX() == 0 && safeSpot.getY() == 0;
}

private boolean isPlayerAtSafeSpot(WorldPoint safeSpot) {
    return safeSpot.equals(Rs2Player.getWorldLocation());
}

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
