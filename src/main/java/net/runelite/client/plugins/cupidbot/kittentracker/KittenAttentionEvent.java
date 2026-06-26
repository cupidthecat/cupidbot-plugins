package net.runelite.client.plugins.cupidbot.kittentracker;

import net.runelite.client.plugins.cupidbot.BlockingEvent;
import net.runelite.client.plugins.cupidbot.BlockingEventPriority;
import net.runelite.client.plugins.cupidbot.util.Global;
import net.runelite.client.plugins.cupidbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.cupidbot.CupidBot;

import javax.inject.Inject;

public class KittenAttentionEvent implements BlockingEvent
{
    private final KittenPlugin kittenPlugin;
    @Inject
    public KittenAttentionEvent(KittenPlugin kittenPlugin)
    {
        this.kittenPlugin = kittenPlugin;
    }

    @Override
    public boolean validate()
    {
        return (KittenPlugin.ATTENTION_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) >= kittenPlugin.getTimeBeforeNeedingAttention() && (kittenPlugin.playerHasFollower() && kittenPlugin.isKitten());
    }

    @Override
    public boolean execute()
    {
        CupidBot.getRs2NpcCache().query().withName("Kitten").toListOnClientThread().stream().findFirst().ifPresent(kitten -> kitten.click("Interact"));
        if (Rs2Dialogue.sleepUntilHasDialogueOption("Stroke")) {
            Rs2Dialogue.clickOption("Stroke");
        }
        Global.sleepUntil(() -> (KittenPlugin.ATTENTION_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) < kittenPlugin.getTimeBeforeNeedingAttention(),10000);
        return true;
    }

    @Override
    public BlockingEventPriority priority()
    {
        return BlockingEventPriority.NORMAL;
    }
}
