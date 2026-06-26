package net.runelite.client.plugins.cupidbot.thievingstalls.constants;

import lombok.AllArgsConstructor;
import net.runelite.client.plugins.cupidbot.thievingstalls.StallThievingConfig;
import net.runelite.client.plugins.cupidbot.thievingstalls.model.BankInventoryStrategy;
import net.runelite.client.plugins.cupidbot.thievingstalls.model.DropInventoryStrategy;
import net.runelite.client.plugins.cupidbot.thievingstalls.model.IInventoryStrategy;

import javax.inject.Inject;

@AllArgsConstructor(onConstructor_ = @Inject)
public class InventoryStrategyFetcher {

    public IInventoryStrategy getInventoryStrategy(final StallThievingConfig config)
    {
        if (config.shouldBankWhenPossible())
        {
            return bankInventoryStrategy;
        }

        return dropInventoryStrategy;
    }

    private BankInventoryStrategy bankInventoryStrategy;
    private DropInventoryStrategy dropInventoryStrategy;
}
