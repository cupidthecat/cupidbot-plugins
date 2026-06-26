package net.runelite.client.plugins.cupidbot.sandminer;

import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.SplitComponent;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;

public class GabulhasSandMinerOverlay extends OverlayPanel {
    private static final Color TITLE_COLOR = Color.decode("#ffd700");  // Gold for sand color
    private static final Color BACKGROUND_COLOR = new Color(0, 0, 0, 150);
    private static final Color NORMAL_COLOR = Color.WHITE;
    private static final Color WARNING_COLOR = Color.YELLOW;
    private static final Color SUCCESS_COLOR = Color.GREEN;
    private static final BufferedImage MINING_ICON = createMiningIcon();

    private final GabulhasSandMinerPlugin plugin;

    @Inject
    GabulhasSandMinerOverlay(GabulhasSandMinerPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
        this.plugin = plugin;
    }

    private static BufferedImage createMiningIcon() {
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(197, 156, 85));
            g.fillPolygon(new int[]{4, 12, 21, 17, 7}, new int[]{18, 5, 13, 20, 21}, 5);
            g.setColor(new Color(235, 202, 126));
            g.fillPolygon(new int[]{7, 12, 18, 14}, new int[]{16, 7, 13, 18}, 4);
            g.setColor(new Color(102, 77, 45));
            g.drawPolygon(new int[]{4, 12, 21, 17, 7}, new int[]{18, 5, 13, 20, 21}, 5);
        } finally {
            g.dispose();
        }
        return image;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(190, 300));
            panelComponent.setBackgroundColor(BACKGROUND_COLOR);

            final ImageComponent imageComponent = new ImageComponent(MINING_ICON);
            final LineComponent title = LineComponent.builder()
                    .left(" Gabulhas Sand Miner")
                    .leftColor(TITLE_COLOR)
                    .build();
            final SplitComponent iconTitleSplit = SplitComponent.builder()
                    .first(imageComponent)
                    .second(title)
                    .orientation(ComponentOrientation.HORIZONTAL)
                    .gap(new Point(2, 0))
                    .build();
            panelComponent.getChildren().add(iconTitleSplit);

            // Runtime
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runtime:")
                    .right(plugin.getTimeRunning())
                    .rightColor(NORMAL_COLOR)
                    .build());

            // Status
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(GabulhasSandMinerInfo.botStatus.toString().replace("_", " "))
                    .rightColor(getStateColor(GabulhasSandMinerInfo.botStatus))
                    .build());

            // Rocks mined
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Rocks Mined:")
                    .right(formatNumber(plugin.rocksMined))
                    .rightColor(NORMAL_COLOR)
                    .build());

            // Rocks per hour calculation
            long rocksPerHour = calculateRocksPerHour();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Rocks/Hour:")
                    .right(formatNumber(rocksPerHour))
                    .rightColor(rocksPerHour > 0 ? SUCCESS_COLOR : NORMAL_COLOR)
                    .build());

            // Version footer
            panelComponent.getChildren().add(LineComponent.builder()
                    .right(GabulhasSandMinerPlugin.version)
                    .rightColor(new Color(160, 160, 160))
                    .build());

        } catch (Exception ex) {
            CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }

    private String formatNumber(long number) {
        return String.format("%,d", number);
    }

    private long calculateRocksPerHour() {
        if (plugin.scriptStartTime == null) return 0;

        double hoursElapsed = (System.currentTimeMillis() - plugin.scriptStartTime.toEpochMilli()) / 3600000.0;
        if (hoursElapsed <= 0) return 0;

        return (long) (plugin.rocksMined / hoursElapsed);
    }

    private Color getStateColor(GabulhasSandMinerInfo.states state) {
        if (state == null) return NORMAL_COLOR;
        switch (state) {
            case Mining:
                return SUCCESS_COLOR;
            case Depositing:
                return WARNING_COLOR;
            default:
                return NORMAL_COLOR;
        }
    }
}
