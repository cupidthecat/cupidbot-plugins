package net.runelite.client.plugins.cupidbot.natepieshells;

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
        name = PluginDescriptor.Nate + "Pie Shell Maker",
        description = "Nate's Pie Shell Maker",
        tags = {"MoneyMaking", "nate", "pies"},
        authors = {"Nate"},
        version = PiePlugin.version,
        minClientVersion = "2.0.7",
        iconUrl = "PiePlugin/assets/icon.png",
        cardUrl = "PiePlugin/assets/card.png",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class PiePlugin extends Plugin {
    final static String version = "1.2.1";

    @Inject
    private PieConfig config;

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private PieOverlay pieOverlay;

    @Inject
    PieScript pieScript;

    @Provides
    PieConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PieConfig.class);
    }


    @Override
    protected void startUp() throws AWTException {
        PieScript.totalPieShellsMade = 0;
        CupidBot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(pieOverlay);
        }
        pieScript.run(config);
    }

    protected void shutDown() {
        pieScript.shutdown();
        overlayManager.remove(pieOverlay);
    }
}
