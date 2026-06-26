package net.runelite.client.plugins.cupidbot.bradleycombat.handlers;

import net.runelite.client.config.Keybind;
import net.runelite.client.plugins.cupidbot.bradleycombat.interfaces.CombatAction;
import net.runelite.client.plugins.cupidbot.bradleycombat.interfaces.Relay;

public class RelayHandler implements Relay {
    @Override
    public void action(Keybind key, CombatAction action) {
        if (key != null)
            action.execute();
    }
}