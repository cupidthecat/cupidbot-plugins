package net.runelite.client.plugins.cupidbot.looter;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.cupidbot.PluginConstants;
import net.runelite.client.plugins.cupidbot.looter.scripts.DefaultScript;
import net.runelite.client.plugins.cupidbot.looter.scripts.FlaxScript;
import net.runelite.client.plugins.cupidbot.looter.scripts.NatureRuneChestScript;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Auto Looter",
        description = "CupidBot looter plugin",
        tags = {"looter", "cupidbot"},
        version = AutoLooterPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class AutoLooterPlugin extends Plugin {
    public static final String version = "1.2.0";
    @Inject
    DefaultScript defaultScript;
    @Inject
    FlaxScript flaxScript;
    @Inject
    NatureRuneChestScript natureRuneChestScript;
    @Inject
    private AutoLooterConfig config;
    @Inject
    private ConfigManager configManager;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AutoLooterOverlay autoLooterOverlay;

    @Provides
    AutoLooterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoLooterConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {

        switch (config.looterActivity()) {
            case DEFAULT:
                defaultScript.run(config);
                if (!config.priorityMode()) {
                    defaultScript.handleWalk(config);
                }
                break;
            case FLAX:
                flaxScript.run(config);
                break;
            case NATURE_RUNE_CHEST:
                natureRuneChestScript.run(config);
                break;
        }

        if(overlayManager != null){
            overlayManager.add(autoLooterOverlay);
        }
    }

    protected void shutDown() throws Exception {
        defaultScript.shutdown();
        flaxScript.shutdown();
        natureRuneChestScript.shutdown();
        overlayManager.remove(autoLooterOverlay);
    }
}
