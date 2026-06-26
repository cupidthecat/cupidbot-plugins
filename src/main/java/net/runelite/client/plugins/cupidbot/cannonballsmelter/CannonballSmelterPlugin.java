package net.runelite.client.plugins.cupidbot.cannonballsmelter;

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
        name = PluginConstants.GIRDY + "Cannonball Smelter",
        description = "Makes cannonballs",
        authors = { "Girdy", "Engin" },
        version = CannonballSmelterPlugin.version,
        minClientVersion = "1.9.9.1",
        tags = { "smithing", "girdy", "skilling" },
        iconUrl = "CannonballSmelterPlugin/assets/icon.png",
        cardUrl = "CannonballSmelterPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class CannonballSmelterPlugin extends Plugin {
        public static final String version = "1.1.1";

        @Inject
        private CannonballSmelterConfig config;

        @Provides
        CannonballSmelterConfig provideConfig(ConfigManager configManager) {
            return configManager.getConfig(CannonballSmelterConfig.class);
        }

        @Inject
        private OverlayManager overlayManager;
        @Inject
        private CannonballSmelterOverlay cannonballSmelterOverlay;

        @Inject
        CannonballSmelterScript cannonballSmelterScript;

        @Override
        protected void startUp() throws AWTException {
			CupidBot.pauseAllScripts.compareAndSet(true, false);
            if (overlayManager != null) {
                overlayManager.add(cannonballSmelterOverlay);
            }
            cannonballSmelterScript.run(config);
        }

        protected void shutDown() {
            cannonballSmelterScript.shutdown();
            overlayManager.remove(cannonballSmelterOverlay);
        }
}

