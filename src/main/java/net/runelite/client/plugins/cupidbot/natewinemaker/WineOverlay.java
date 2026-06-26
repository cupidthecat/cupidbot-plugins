package net.runelite.client.plugins.cupidbot.natewinemaker;

import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;


public class WineOverlay extends OverlayPanel {

    @Inject
    WineOverlay(WinePlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(275, 800));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Nate's Wine Maker")
                    .color(Color.magenta)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(CupidBot.status)
                    .right("version: " + WinePlugin.version)
                    .build());


        } catch (Exception ex) {
            CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }
}
