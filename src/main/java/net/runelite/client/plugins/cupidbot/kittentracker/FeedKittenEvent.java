package net.runelite.client.plugins.cupidbot.kittentracker;


import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.cupidbot.BlockingEvent;
import net.runelite.client.plugins.cupidbot.BlockingEventPriority;
import net.runelite.client.plugins.cupidbot.util.Global;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.CupidBot;

import javax.inject.Inject;


public class FeedKittenEvent implements BlockingEvent {
    private final KittenPlugin kittenPlugin;
    @Inject
    public FeedKittenEvent(KittenPlugin kittenPlugin) {
        this.kittenPlugin = kittenPlugin;
    }

    @Override
    public boolean validate() {
        return Rs2Inventory.contains(ItemID.TBWT_RAW_KARAMBWANJI)
                && (KittenPlugin.HUNGRY_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) >= kittenPlugin.getTimeBeforeHungry() && (kittenPlugin.playerHasFollower() && kittenPlugin.isKitten());

    }

    @Override
    public boolean execute() {
        CupidBot.getRs2NpcCache().query().withName("Kitten").toListOnClientThread().stream().findFirst().ifPresent(kitten -> Rs2Inventory.useItemOnNpc(ItemID.TBWT_RAW_KARAMBWANJI, kitten.getNpc()));
        Global.sleepUntil(() -> (KittenPlugin.HUNGRY_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) < kittenPlugin.getTimeBeforeHungry());
        return true;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
