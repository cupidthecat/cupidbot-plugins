package net.runelite.client.plugins.cupidbot.thievingstalls;

import lombok.AllArgsConstructor;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.thievingstalls.constants.InventoryStrategyFetcher;
import net.runelite.client.plugins.cupidbot.thievingstalls.constants.ThievingSpotMapper;
import net.runelite.client.plugins.cupidbot.thievingstalls.model.BotApi;
import net.runelite.client.plugins.cupidbot.thievingstalls.model.IStallThievingSpot;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;


@AllArgsConstructor(onConstructor_ = @Inject)
public class StallThievingScript extends Script {
    private BotApi botApi;
    private ThievingSpotMapper thievingSpotMapper;
    private InventoryStrategyFetcher inventoryStrategyMapper;

    public boolean run(StallThievingConfig config) {
        CupidBot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!CupidBot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                execute(config);

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;

            } catch (Exception ex) {
                CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    private void execute(final StallThievingConfig config)
    {
        final IStallThievingSpot thievingSpot = thievingSpotMapper.getThievingSpot(config.THIEVING_SPOT());
        if (botApi.isInventoryFull()) {
            inventoryStrategyMapper.getInventoryStrategy(config).execute(thievingSpot);
            return;
        }

        thievingSpot.thieve();
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
