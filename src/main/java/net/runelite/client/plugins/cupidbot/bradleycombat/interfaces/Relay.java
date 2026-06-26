package net.runelite.client.plugins.cupidbot.bradleycombat.interfaces;

import net.runelite.client.config.Keybind;

public abstract interface Relay {

    default void action(Keybind key, CombatAction action) {
    }

}