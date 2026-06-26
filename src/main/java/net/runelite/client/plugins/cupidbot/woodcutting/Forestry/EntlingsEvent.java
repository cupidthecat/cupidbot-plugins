package net.runelite.client.plugins.cupidbot.woodcutting.Forestry;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.cupidbot.BlockingEvent;
import net.runelite.client.plugins.cupidbot.BlockingEventPriority;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.cupidbot.woodcutting.AutoWoodcuttingPlugin;
import net.runelite.client.plugins.cupidbot.woodcutting.enums.ForestryEvents;

import java.util.Comparator;
@Slf4j
public class EntlingsEvent implements BlockingEvent {

    private final AutoWoodcuttingPlugin plugin;
    public EntlingsEvent(AutoWoodcuttingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        try{
            if (plugin == null || !CupidBot.isPluginEnabled(plugin)) return false;
            if (CupidBot.getClient() == null || !CupidBot.isLoggedIn()) return false;
            var entlings = CupidBot.getRs2NpcCache().query().where(npc -> npc.getId() == NpcID.GATHERING_EVENT_ENTLINGS_NPC_01).toList();
            return !entlings.isEmpty();
        } catch (Exception e) {
            log.error("EntlingsEvent: Exception in validate method", e);
            return false;
        }
    }

    @Override
    public boolean execute() {
        CupidBot.log("EntlingsEvent: Starting Entlings event execution");
        plugin.currentForestryEvent = ForestryEvents.ENTLING;
        Rs2Walker.setTarget(null); // stop walking, stop moving to bank for example

        // ensure inventory space for tree leaves and potential egg nest (20% chance)
        if (!plugin.ensureInventorySpace(2)) {
            CupidBot.log("EntlingsEvent: Cannot make enough inventory space, ending event.");
            return true;
        }

        while (this.validate()) {
            var entlings = CupidBot.getRs2NpcCache().query()
                    .where(npc -> npc.getId() == NpcID.GATHERING_EVENT_ENTLINGS_NPC_01)
                    .toList();
            entlings.sort(Comparator.comparingInt(e ->
                    e.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())
            ));

            for (Rs2NpcModel entling : entlings) {
                String request = entling.getOverheadText();
                String action;

                if (request == null || request.isEmpty()) {
                    continue; // Skip if no overhead text is present
                }
                switch (request) {
                    case "Breezy at the back!":
                    case "Short back and sides!":
                        action = "Prune-back";
                        break;
                    case "A leafy mullet!":
                    case "Short on top!":
                        action = "Prune-top";
                        break;
                    default:
                        continue;
                }

                CupidBot.log("EntlingsEvent: Interacting with entling: with action: " + action);
                entling.click(action);
                Rs2Player.waitForAnimation(1000); // Wait for the pruning animation to finish
            }
        }
        CupidBot.log("EntlingsEvent: Ending event execution");
        plugin.incrementForestryEventCompleted();
        return true;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
