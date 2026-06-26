package net.runelite.client.plugins.cupidbot.bradleycombat.actions;

import net.runelite.api.Skill;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.bradleycombat.interfaces.CombatAction;
import net.runelite.client.plugins.cupidbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

public class VengeanceAction implements CombatAction {
    private final boolean useVengeance;

    public VengeanceAction(boolean useVengeance) {
        this.useVengeance = useVengeance;
    }

    @Override
    public void execute() {
        if (!useVengeance) return;
        if (CupidBot.getClient().getBoostedSkillLevel(Skill.MAGIC) <= 93) return;
        Rs2Magic.cast(MagicAction.VENGEANCE);
    }
}