package net.runelite.client.plugins.cupidbot.fletching;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginConstants.MOCROSOFT + "Fletcher",
        description = "CupidBot fletching plugin",
        authors = { "Mocrosoft" },
        version = FletchingPlugin.version,
        minClientVersion = "1.9.9.1",
        tags = {"fletching", "cupidbot", "skills"},
        iconUrl = "FletchingPlugin/assets/icon.png",
        cardUrl = "FletchingPlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class FletchingPlugin extends Plugin {

    public static final String version = "1.7.0";

    @Inject
    private FletchingConfig config;

    @Provides
    FletchingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FletchingConfig.class);
    }
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private FletchingOverlay fletchingOverlay;

    FletchingScript fletchingScript;


    @Override
    protected void startUp() throws AWTException {
		CupidBot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(fletchingOverlay);
        }
        fletchingScript = new FletchingScript();
        fletchingScript.run(config);
    }

    protected void shutDown() {
        fletchingScript.shutdown();
        overlayManager.remove(fletchingOverlay);
    }
}
