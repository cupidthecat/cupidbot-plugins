package net.runelite.client.plugins.cupidbot.arrowmaker;

import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.arrowmaker.ArrowPlugin;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;



public class ArrowOverlay extends OverlayPanel {

    @Inject
    ArrowOverlay(ArrowPlugin plugin)
    {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
    }
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(275, 800));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Nate's Arrow Maker")
                    .color(Color.green)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(CupidBot.status)
                    .build());


        } catch(Exception ex) {
            CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }
}
