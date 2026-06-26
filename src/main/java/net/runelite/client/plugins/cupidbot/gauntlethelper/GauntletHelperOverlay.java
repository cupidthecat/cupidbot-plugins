package net.runelite.client.plugins.cupidbot.gauntlethelper;

import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.gauntlethelper.GauntletHelperConfig;
import net.runelite.client.plugins.cupidbot.gauntlethelper.GauntletHelperPlugin;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class GauntletHelperOverlay extends OverlayPanel {

    private final GauntletHelperPlugin plugin;
    private final GauntletHelperConfig config;
    private final GauntletHelperScript script;

    @Inject
    GauntletHelperOverlay(GauntletHelperPlugin plugin, GauntletHelperConfig config, GauntletHelperScript script) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics) {

        if (!config.enOverlay()) {
            panelComponent.getChildren().clear();
            return null; // returning null tells RuneLite not to render anything
        }

            panelComponent.setPreferredSize(new Dimension(250, 500));
            panelComponent.getChildren().clear(); // Always clear before rendering

            try {
                // === Header ===
                panelComponent.getChildren().add(
                        TitleComponent.builder()
                                .text("Gauntlet Prayer Helper")
                                .color(new Color(0x00FF88))
                                .build()
                );

                panelComponent.getChildren().add(LineComponent.builder().build());

                // === Next Prayer ===
                String nextPrayerText = (plugin != null && script.getNextPrayer() != null)
                        ? script.getNextPrayer().toString()
                        : "None";

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Next Prayer:")
                        .right(nextPrayerText)
                        .leftColor(Color.WHITE)
                        .rightColor(Color.CYAN)
                        .build());

                String stateText = (plugin != null && GauntletHelperScript.gh_state != null)
                        ? GauntletHelperScript.gh_state.toString()
                        : "None";

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("State:")
                        .right(stateText)
                        .leftColor(Color.WHITE)
                        .rightColor(Color.CYAN)
                        .build());


                // === General CupidBot Status ===
                if (CupidBot.status != null && !CupidBot.status.isEmpty()) {
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Status:")
                            .right(CupidBot.status)
                            .leftColor(Color.WHITE)
                            .rightColor(Color.GREEN)
                            .build());
                }

            } catch (Exception ex) {
                CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
            }

        return super.render(graphics);
    }
}
