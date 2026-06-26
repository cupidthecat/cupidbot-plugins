package net.runelite.client.plugins.cupidbot.moonlightmoth;

import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.SplitComponent;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Instant;

public class MoonlightMothOverlay extends OverlayPanel {

    private static final BufferedImage MOTH_ICON = createMothIcon();
    public final MoonlightMothPlugin plugin;
    private final ImageComponent imageComponent;

    @Inject
    public MoonlightMothOverlay(MoonlightMothPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        imageComponent = new ImageComponent(MOTH_ICON);
        this.plugin = plugin;
    }

    private static BufferedImage createMothIcon() {
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(197, 230, 255));
            g.fillOval(2, 6, 10, 11);
            g.fillOval(12, 6, 10, 11);
            g.setColor(new Color(125, 165, 205));
            g.fillRoundRect(10, 5, 4, 15, 4, 4);
            g.setColor(new Color(235, 248, 255, 180));
            g.drawArc(3, 4, 8, 12, 40, 240);
            g.drawArc(13, 4, 8, 12, -100, 240);
        } finally {
            g.dispose();
        }
        return image;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            final LineComponent title = LineComponent.builder()
                    .left("           00 Moonlight Moth")
                    .leftColor(Color.RED)
                    .build();
            final SplitComponent iconTitleSplit = SplitComponent.builder()
                    .first(imageComponent)
                    .second(title)
                    .orientation(ComponentOrientation.HORIZONTAL)
                    .gap(new Point(2, 0))
                    .build();
            panelComponent.getChildren().add(iconTitleSplit);
            // Script running time
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runtime:")
                    .right(plugin.getTimeRunning())
                    .rightColor(Color.GREEN)
                    .build());
            // State information
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("State:")
                    .right(CupidBot.status)
                    .rightColor(Color.GREEN)
                    .build());
            // Rate calculations
            long runtimeMs = Instant.now().toEpochMilli() - plugin.scriptStartTime.toEpochMilli();
            double hoursElapsed = runtimeMs / (1000.0 * 60.0 * 60.0); // Convert ms to hours

            // Avoid division by zero
            int caughtPerHour = hoursElapsed > 0 ?
                    (int) (plugin.script.totalCaught / hoursElapsed) : 0;
            int profitPerHour = hoursElapsed > 0 ?
                    (int) ((plugin.script.totalCaught * plugin.script.pricePerMoth) / hoursElapsed) : 0;

            // Total caught moths
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Total moths caught:")
                    .right(plugin.script.totalCaught + " (" + caughtPerHour + "/hr)")
                    .rightColor(Color.YELLOW)
                    .build());
            // Total profit
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Total profit:")
                    .right(String.format("%,d", plugin.script.totalCaught * plugin.script.pricePerMoth) + " (" + (profitPerHour / 1000) + "k/hr)")
                    .rightColor(Color.YELLOW)
                    .build());

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
