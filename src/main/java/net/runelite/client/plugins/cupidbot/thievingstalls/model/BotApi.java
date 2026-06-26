package net.runelite.client.plugins.cupidbot.thievingstalls.model;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;

public class BotApi {
    private static final String STEAL_ACTION = "Steal-from";

    public boolean walkTo(final WorldPoint worldPoint)
    {
        return Rs2Walker.walkTo(worldPoint, 0);
    }

    public Rs2TileObjectModel getGameObject(final int id, final WorldPoint worldPoint)
    {
        return CupidBot.getRs2TileObjectCache().query().withId(id).nearest(worldPoint, 5);
    }

    public void steal(final Rs2TileObjectModel gameObject)
    {
        gameObject.click(STEAL_ACTION);
    }

    public void dropAll(int... ids)
    {
        Rs2Inventory.dropAll(ids);
    }

    public void sleepUntilNextTick()
    {
        Rs2Player.waitForXpDrop(Skill.THIEVING);
    }

    public boolean isInventoryFull()
    {
        return Rs2Inventory.isFull();
    }
}
