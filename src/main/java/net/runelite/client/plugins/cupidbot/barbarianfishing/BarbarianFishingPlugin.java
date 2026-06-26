package net.runelite.client.plugins.cupidbot.barbarianfishing;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.See1Duck + " Barbarian Fisher",
        description = "Barbarian Fishing plugin",
        tags = {"Fishing", "barbarian", "skilling"},
        authors = {"See1Duck"},
        version = BarbarianFishingPlugin.version,
        minClientVersion = "2.0.7",
        cardUrl = "BarbarianFishingPlugin/assets/card.jpg",
        iconUrl = "BarbarianFishingPlugin/assets/icon.jpg",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class BarbarianFishingPlugin extends Plugin {
    public static final String version = "1.1.0";
    @Inject
    BarbarianFishingScript fishingScript;
    @Inject
    private BarbarianFishingConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private BarbarianFishingOverlay fishingOverlay;

    @Provides
    BarbarianFishingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BarbarianFishingConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
		CupidBot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(fishingOverlay);
        }
        fishingScript.run(config);
    }

    protected void shutDown() {
        fishingScript.shutdown();
        if (overlayManager != null) {
            overlayManager.remove(fishingOverlay);
        }
    }
}
