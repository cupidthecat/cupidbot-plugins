package net.runelite.client.plugins.cupidbot.bradleycombat.handlers;

import com.google.inject.Inject;
import net.runelite.api.Player;
import net.runelite.api.events.AnimationChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.bradleycombat.BradleyCombatConfig;
import net.runelite.client.plugins.cupidbot.bradleycombat.actions.MageAction;
import net.runelite.client.plugins.cupidbot.bradleycombat.actions.MeleeAction;
import net.runelite.client.plugins.cupidbot.bradleycombat.actions.RangeAction;
import net.runelite.client.plugins.cupidbot.bradleycombat.actions.SpecAction;

public class PostActionHandler {
    private final BradleyCombatConfig config;

    @Inject
    public PostActionHandler(BradleyCombatConfig config) {
        this.config = config;
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        Player local = CupidBot.getClient().getLocalPlayer();
        if (local == null)
            return;
        int currentAnim = local.getAnimation();
        if (matches(currentAnim, config.postActionMeleePrimary())) {
            CupidBot.getClientThread().runOnSeperateThread(() -> {
                new MeleeAction(config, 1).execute();
                return null;
            });
            return;
        } else if (matches(currentAnim, config.postActionMeleeSecondary())) {
            CupidBot.getClientThread().runOnSeperateThread(() -> {
                new MeleeAction(config, 2).execute();
                return null;
            });
            return;
        } else if (matches(currentAnim, config.postActionMeleeTertiary())) {
            CupidBot.getClientThread().runOnSeperateThread(() -> {
                new MeleeAction(config, 3).execute();
                return null;
            });
            return;
        } else if (matches(currentAnim, config.postActionRangePrimary())) {
            CupidBot.getClientThread().runOnSeperateThread(() -> {
                new RangeAction(config, 1).execute();
                return null;
            });
            return;
        } else if (matches(currentAnim, config.postActionRangeSecondary())) {
            CupidBot.getClientThread().runOnSeperateThread(() -> {
                new RangeAction(config, 2).execute();
                return null;
            });
            return;
        } else if (matches(currentAnim, config.postActionRangeTertiary())) {
            CupidBot.getClientThread().runOnSeperateThread(() -> {
                new RangeAction(config, 3).execute();
                return null;
            });
            return;
        } else if (matches(currentAnim, config.postActionMagePrimary())) {
            CupidBot.getClientThread().runOnSeperateThread(() -> {
                new MageAction(config, 1).execute();
                return null;
            });
            return;
        } else if (matches(currentAnim, config.postActionMageSecondary())) {
            CupidBot.getClientThread().runOnSeperateThread(() -> {
                new MageAction(config, 2).execute();
                return null;
            });
            return;
        } else if (matches(currentAnim, config.postActionMageTertiary())) {
            CupidBot.getClientThread().runOnSeperateThread(() -> {
                new MageAction(config, 3).execute();
                return null;
            });
            return;
        } else if (matches(currentAnim, config.postActionSpecPrimary())) {
            CupidBot.getClientThread().runOnSeperateThread(() -> {
                new SpecAction(config, 1).execute();
                return null;
            });
            return;
        } else if (matches(currentAnim, config.postActionSpecSecondary())) {
            CupidBot.getClientThread().runOnSeperateThread(() -> {
                new SpecAction(config, 2).execute();
                return null;
            });
            return;
        } else if (matches(currentAnim, config.postActionSpecTertiary())) {
            CupidBot.getClientThread().runOnSeperateThread(() -> {
                new SpecAction(config, 3).execute();
                return null;
            });
        }
    }

    private boolean matches(int currentAnim, String configValue) {
        if (configValue == null || configValue.trim().isEmpty())
            return false;
        String[] parts = configValue.split("\\s*,\\s*");
        for (String part : parts) {
            try {
                int animId = Integer.parseInt(part.trim());
                if (animId == currentAnim)
                    return true;
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }
}