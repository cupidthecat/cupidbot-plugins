package net.runelite.client.plugins.cupidbot.bradleycombat.actions;


import net.runelite.client.plugins.cupidbot.bradleycombat.BradleyCombatPlugin;
import net.runelite.client.plugins.cupidbot.bradleycombat.interfaces.CombatAction;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;

import static net.runelite.client.plugins.cupidbot.util.player.Rs2Player.getPlayer;

public class WalkUnderAction implements CombatAction {
    @Override
    public void execute() {
        if (BradleyCombatPlugin.validTarget() && BradleyCombatPlugin.getTarget().getLocalLocation().isInScene())
            Rs2Player.walkUnder(getPlayer(BradleyCombatPlugin.getTarget().getName()));
    }
}