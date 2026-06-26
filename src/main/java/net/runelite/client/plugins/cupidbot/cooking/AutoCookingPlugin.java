package net.runelite.client.plugins.cupidbot.cooking;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.PluginConstants;
import net.runelite.client.plugins.cupidbot.cooking.scripts.AutoCookingScript;
import net.runelite.client.plugins.cupidbot.cooking.scripts.BurnBakingScript;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

import static net.runelite.client.plugins.PluginDescriptor.Mocrosoft;

@PluginDescriptor(
        name = PluginDescriptor.GMason + "Auto Cooking",
        description = "CupidBot cooking plugin",
        tags = {"cooking", "cupidbot", "skilling"},
        authors = {"George"},
        version = AutoCookingPlugin.version,
        minClientVersion = "2.0.8",
        cardUrl = "AutoCookingPlugin/assets/card.jpg",
        iconUrl = "AutoCookingPlugin/assets/icon.jpg",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class AutoCookingPlugin extends Plugin {
    public final static String version = "1.1.5";
    @Inject
    AutoCookingScript autoCookingScript;
    @Inject
    BurnBakingScript burnBakingScript;
    @Inject
    private AutoCookingConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AutoCookingOverlay overlay;

    @Provides
    AutoCookingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoCookingConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        switch (config.cookingActivity()) {
            case COOKING:
                autoCookingScript.run(config);
                break;
            case BURN_BAKING:
                burnBakingScript.run(config);
                break;
            default:
                CupidBot.log("Invalid Cooking Activity");
        }
    }

    protected void shutDown() {
        autoCookingScript.shutdown();
        burnBakingScript.shutdown();
        overlayManager.remove(overlay);
    }
}
