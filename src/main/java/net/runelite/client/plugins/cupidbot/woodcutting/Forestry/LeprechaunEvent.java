package net.runelite.client.plugins.cupidbot.woodcutting.Forestry;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.cupidbot.BlockingEvent;
import net.runelite.client.plugins.cupidbot.BlockingEventPriority;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.util.Global;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.cupidbot.woodcutting.AutoWoodcuttingPlugin;
import net.runelite.client.plugins.cupidbot.woodcutting.enums.ForestryEvents;
import org.slf4j.event.Level;

import static net.runelite.client.plugins.cupidbot.util.Global.sleepGaussian;
@Slf4j
public class LeprechaunEvent implements BlockingEvent {

    private final AutoWoodcuttingPlugin plugin;

    public LeprechaunEvent(AutoWoodcuttingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        try{
            if (plugin == null || !CupidBot.isPluginEnabled(plugin)) return false;
            if (CupidBot.getClient() == null || !CupidBot.isLoggedIn()) return false;
            var leprechaun = CupidBot.getRs2NpcCache().query()
                    .withId(NpcID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN)
                    .nearest();
            return leprechaun != null;
        } catch (Exception e) {
            log.error("LeprechaunEvent: Exception in validate method", e);
            return false;
        }
    }

    @Override
    public boolean execute() {
        CupidBot.log("LeprechaunEvent: Executing Leprechaun event");
        plugin.currentForestryEvent = ForestryEvents.RAINBOW;
        Rs2Walker.setTarget(null); // stop walking, stop moving to bank for example
        while (this.validate()) {
            log.info("LeprechaunEvent: Leprechaun event still valid, continuing execution get opbject");
            var endOfRainbow = CupidBot.getRs2TileObjectCache().query().withId(ObjectID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN_RAINBOW).nearest();
            if (endOfRainbow == null) {
                log.warn("LeprechaunEvent: End of the rainbow not found, retrying...");
                sleepGaussian(900, 300);
                continue; // If the end of the rainbow is not found, we cannot proceed with the event
            }
            // Move to the end of the rainbow
            var location = endOfRainbow.getWorldLocation();
            if (!Rs2Player.getWorldLocation().equals(location)) {
                CupidBot.log("LeprechaunEvent: Walking to the end of the rainbow at " + location, Level.INFO);
                Rs2Walker.walkFastCanvas(location);
                Global.sleepUntil(() -> Rs2Player.getWorldLocation().equals(location), 5000);
            }
        }
        plugin.incrementForestryEventCompleted();
        return true;

        //TODO: Implement interaction with the leprechaun for banking
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
