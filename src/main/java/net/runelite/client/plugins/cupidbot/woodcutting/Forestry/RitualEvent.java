package net.runelite.client.plugins.cupidbot.woodcutting.Forestry;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.cupidbot.BlockingEvent;
import net.runelite.client.plugins.cupidbot.BlockingEventPriority;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.cupidbot.woodcutting.AutoWoodcuttingPlugin;
import net.runelite.client.plugins.cupidbot.woodcutting.enums.ForestryEvents;
import org.slf4j.event.Level;

import java.util.List;

import static net.runelite.client.plugins.cupidbot.util.Global.sleepGaussian;
import static net.runelite.client.plugins.cupidbot.util.Global.sleepUntil;

@Slf4j
public class RitualEvent implements BlockingEvent {

    private final AutoWoodcuttingPlugin plugin;

    public RitualEvent(AutoWoodcuttingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        try{
            if (plugin == null || !CupidBot.isPluginEnabled(plugin)) return false;
            if (CupidBot.getClient() == null || !CupidBot.isLoggedIn()) return false;
            var dryad = CupidBot.getRs2NpcCache().query()
                    .withId(NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_DRYAD)
                    .nearest();
            return dryad != null;
        } catch (Exception e) {
            log.error("RitualEvent: Exception in validate method", e);
            return false;
        }
    }

    @Override
    public boolean execute() {
        plugin.currentForestryEvent = ForestryEvents.RITUAL_CIRCLES;
        Rs2Walker.setTarget(null); // stop walking, stop moving to bank for example

        // ensure inventory space for potential petal garland (1/30 chance)
        if (!plugin.ensureInventorySpace(1)) {
            log.warn("Cannot make inventory space for ritual rewards, ending event.");
            return true;
        }

        while (this.validate()) {
            var targetCircle = this.solveCircles(plugin.ritualCircles);
            if (targetCircle == null) {
                CupidBot.log("RitualEvent: No target circle found, cannot proceed with the ritual.", Level.INFO);
                sleepGaussian(600, 150);
                continue; // If no target circle is found, we cannot proceed with the event
            }

            if (Rs2Player.getWorldLocation().equals(targetCircle.getWorldLocation())) {
                //CupidBot.log("RitualEvent: Already at the target circle, performing the ritual.", Level.INFO);
                sleepGaussian(600, 150);
                continue; // TODO find some condition to wait for here
            }

            // Move to the target circle
            CupidBot.log("RitualEvent: Moving to the target circle to perform the ritual.", Level.INFO);
            Rs2Walker.walkFastCanvas(targetCircle.getWorldLocation());
            sleepUntil(() -> Rs2Player.getWorldLocation().equals(targetCircle.getWorldLocation()), 5000);
        }
        CupidBot.log("RitualEvent: Finished executing the ritual event.", Level.INFO);
        plugin.incrementForestryEventCompleted();
        return true;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }

    private Rs2NpcModel solveCircles(List<Rs2NpcModel> ritualCircles) {
        if (ritualCircles.size() != 5) {
            return null;
        }

        int s = 0;
        for (Rs2NpcModel npc : ritualCircles) {
            int off = npc.getId() - NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1;
            int shape = off / 4;
            int color = off % 4;
            int id = (16 << shape) | (1 << color);
            s = s ^ id; // XOR operation
        }

        for (Rs2NpcModel npc : ritualCircles) {
            int off = npc.getId() - NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1;
            int shape = off / 4;
            int color = off % 4;
            int id = (16 << shape) | (1 << color);
            if ((id & s) == id) { // Bitwise AND
                return npc;
            }
        }

        return null;
    }
}
