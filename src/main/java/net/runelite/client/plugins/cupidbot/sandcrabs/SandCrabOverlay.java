package net.runelite.client.plugins.cupidbot.sandcrabs;

import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class SandCrabOverlay extends OverlayPanel {
    private final SandCrabPlugin plugin;

    @Inject
    SandCrabOverlay(SandCrabPlugin plugin) {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Micro SandCrabs Plugin V" + SandCrabPlugin.version)
                    .color(Color.decode("#a4ffff"))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(plugin.sandCrabScript.state.name())
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("AFK Timer: " + plugin.sandCrabScript.afkTimer)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("HIJACK Timer: " + plugin.sandCrabScript.hijackTimer)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Times Hopped: " + plugin.sandCrabScript.timesHopped)
                    .build());

        } catch (Exception ex) {
            CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }
}
