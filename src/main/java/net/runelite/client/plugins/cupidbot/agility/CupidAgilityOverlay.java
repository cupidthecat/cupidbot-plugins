package net.runelite.client.plugins.cupidbot.agility;

import net.runelite.api.Skill;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class CupidAgilityOverlay extends OverlayPanel
{
	final CupidAgilityPlugin plugin;
	final CupidAgilityConfig config;

	@Inject
	CupidAgilityOverlay(CupidAgilityPlugin plugin, CupidAgilityConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setNaughty();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		try
		{
			panelComponent.setPreferredSize(new Dimension(200, 300));
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("Cupid Agility V" + CupidAgilityPlugin.version)
				.color(Color.GREEN)
				.build());

			panelComponent.getChildren().add(LineComponent.builder().build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Agility Exp")
				.right(Integer.toString(CupidBot.getClient().getSkillExperience(Skill.AGILITY)))
				.build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Current Obstacle")
				.right(Integer.toString(config.agilityCourse().getHandler().getCurrentObstacleIndex()))
				.build());

		}
		catch (Exception ex)
		{
			CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
		}
		return super.render(graphics);
	}
}
