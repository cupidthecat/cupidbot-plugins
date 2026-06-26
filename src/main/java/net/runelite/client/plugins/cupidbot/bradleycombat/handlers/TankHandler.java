package net.runelite.client.plugins.cupidbot.bradleycombat.handlers;

import com.google.inject.Inject;
import net.runelite.api.Player;
import net.runelite.api.events.AnimationChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.bradleycombat.BradleyCombatConfig;
import net.runelite.client.plugins.cupidbot.bradleycombat.actions.TankAction;

public class TankHandler {
    private final BradleyCombatConfig config;

    @Inject
    public TankHandler(BradleyCombatConfig config) {
        this.config = config;
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        Player local = CupidBot.getClient().getLocalPlayer();
        if (local == null) return;
        int currentAnim = local.getAnimation();
        String animConfig = config.tankAnimations();
        if (animConfig == null || animConfig.trim().isEmpty()) return;
        String[] parts = animConfig.split("\\s*,\\s*");
        for (String part : parts) {
            try {
                int animId = Integer.parseInt(part);
                if (currentAnim == animId) {
                    new TankAction(config).execute();
                    break;
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
