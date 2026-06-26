package net.runelite.client.plugins.cupidbot.bradleycombat.actions;

import net.runelite.client.plugins.cupidbot.bradleycombat.BradleyCombatConfig;
import net.runelite.client.plugins.cupidbot.bradleycombat.interfaces.CombatAction;

public class TankAction implements CombatAction {
    private final BradleyCombatConfig config;

    public TankAction(BradleyCombatConfig config) {
        this.config = config;
    }

    @Override
    public void execute() {
        new EquipAction(config.gearIDsTank()).execute();
    }
}