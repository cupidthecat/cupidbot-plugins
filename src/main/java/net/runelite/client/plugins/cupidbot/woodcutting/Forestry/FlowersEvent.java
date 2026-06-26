package net.runelite.client.plugins.cupidbot.woodcutting.Forestry;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.cupidbot.BlockingEvent;
import net.runelite.client.plugins.cupidbot.BlockingEventPriority;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.cupidbot.woodcutting.AutoWoodcuttingPlugin;
import net.runelite.client.plugins.cupidbot.woodcutting.enums.ForestryEvents;

import static net.runelite.client.plugins.cupidbot.util.Global.sleepUntil;
@Slf4j
public class FlowersEvent implements BlockingEvent {

    private final AutoWoodcuttingPlugin plugin;
    public FlowersEvent(AutoWoodcuttingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        try{
            if (plugin == null || !CupidBot.isPluginEnabled(plugin)) return false;
            if (CupidBot.getClient() == null || !CupidBot.isLoggedIn()) return false;
            var flowers = CupidBot.getRs2NpcCache().query()
                    .where(npc -> npc.getName() != null && isFloweringBush(npc.getId()))
                    .toList();
            return !flowers.isEmpty();
        } catch (Exception e) {
            log.error("FlowersEvent: Exception in validate method", e);
            return false;
        }
    }

    @Override
    public boolean execute() {
        plugin.currentForestryEvent = ForestryEvents.FLOWERING_TREE;
        Rs2Walker.setTarget(null); // stop walking, stop moving to bank for example

        // ensure inventory space for strange pollen and fruits/seeds
        if (!plugin.ensureInventorySpace(3)) {
            log.warn("Cannot make enough inventory space for flowering tree rewards, ending event.");
            return true;
        }
        log.info("FlowersEvent: Executing Flowers event");
        while (this.validate()) {
            var flowers = CupidBot.getRs2NpcCache().query()
                    .where(npc -> npc.getName() != null && isFloweringBush(npc.getId()))
                    .toList();

            if (flowers.isEmpty()) {
                break;
            }

            // find a flower that hasn't been pollinated yet
            var availableFlower = flowers.stream()
                .filter(flower -> flower.getAnimation() == -1)
                .findFirst()
                .orElse(null);

            if (availableFlower == null) {
                // all flowers are being worked on, wait a bit
                sleepUntil(() -> false, 1000);
                continue;
            }

            if (availableFlower.click("Tend-to")) {
                Rs2Player.waitForAnimation();
                sleepUntil(() -> !Rs2Player.isInteracting(), 8000);
            }
        }

        plugin.incrementForestryEventCompleted();
        return true;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL; // Set appropriate priority for this event
    }

    private static boolean isFloweringBush(int npcId)
    {
        return npcId == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL01 ||
                npcId == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL02 ||
                npcId == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL03 ||
                npcId == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL04 ||
                npcId == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL05 ||
                npcId == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL06 ||
                npcId == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL07 ||
                npcId == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL08;
    }
}
