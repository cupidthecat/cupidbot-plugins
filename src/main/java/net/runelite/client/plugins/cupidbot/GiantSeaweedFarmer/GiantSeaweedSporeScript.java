package net.runelite.client.plugins.cupidbot.GiantSeaweedFarmer;

import net.runelite.api.ItemID;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;

public class GiantSeaweedSporeScript extends Script {
    private GiantSeaweedFarmerConfig config;

    public boolean run(GiantSeaweedFarmerConfig config) {
        this.config = config;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !CupidBot.isLoggedIn()) return;
                if (!config.lootSeaweedSpores()) return;

                // Check for seaweed spores - but respect critical farming operations
                if (CupidBot.getRs2TileItemCache().query().withId(ItemID.SEAWEED_SPORE).within(15).count() > 0 &&
                        !GiantSeaweedFarmerScript.inCriticalSection) {
                    // Pause all other scripts while we loot
                    CupidBot.pauseAllScripts.set(true);
                    try {
                        lootAllSpores();
                    } finally {
                        // Always unpause when done
                        CupidBot.pauseAllScripts.set(false);
                    }
                }
            } catch (Exception ex) {
                CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
                // Ensure we unpause if there's an error
                CupidBot.pauseAllScripts.set(false);
            }
        }, 0, 300, TimeUnit.MILLISECONDS); // Fast checking interval for quick spore detection
        return true;
    }

    private void lootAllSpores() {
        while (CupidBot.getRs2TileItemCache().query().withId(ItemID.SEAWEED_SPORE).within(15).count() > 0 && this.isRunning()) {
            CupidBot.log("Seaweed spore detected - looting");
            boolean looted = CupidBot.getRs2TileItemCache().query().withId(ItemID.SEAWEED_SPORE).within(15).interact("Take");
            if (looted) {
                // Wait for movement to start and complete
                sleepUntil(Rs2Player::isMoving, 2000);
                if (Rs2Player.isMoving()) {
                    sleepUntil(() -> !Rs2Player.isMoving(), 5000);
                }

                // Wait for inventory change
                Rs2Inventory.waitForInventoryChanges(2000);
                sleep(300, 500);
            } else {
                // If loot failed, try again after a small delay
                sleep(200, 300);
            }
        }
        CupidBot.log("Finished looting seaweed spores");
    }

    @Override
    public void shutdown() {
        // Ensure we unpause when shutting down
        CupidBot.pauseAllScripts.set(false);
        super.shutdown();
    }
}