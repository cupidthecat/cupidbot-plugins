package net.runelite.client.plugins.cupidbot.nmz;

import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class NmzOverlay extends OverlayPanel {
    @Inject
    NmzOverlay(NmzPlugin plugin)
    {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }
    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(200, 300));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("Micro NMZ V" + NmzPlugin.version)
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left(CupidBot.status)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Will self damage at: " + NmzScript.maxHealth)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Will drink absorption at: " + NmzScript.minAbsorption)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Overload (barrel):")
                    .right(String.valueOf(CupidBot.getVarbitValue(VarbitID.NZONE_POTION_3)))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Absorb (barrel):")
                    .right(String.valueOf(CupidBot.getVarbitValue(VarbitID.NZONE_POTION_4)))
                    .build());

} catch(Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
