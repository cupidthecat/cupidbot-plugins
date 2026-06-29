package net.runelite.client.plugins.cupidbot.brimhavenagilitybot;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class BrimhavenAgilityBotOverlay extends OverlayPanel
{
	private final BrimhavenAgilityBotPlugin plugin;
	private final BrimhavenAgilityBotConfig config;

	@Inject
	BrimhavenAgilityBotOverlay(BrimhavenAgilityBotPlugin plugin, BrimhavenAgilityBotConfig config)
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
		if (!config.drawPath())
		{
			return null;
		}

		panelComponent.setPreferredSize(new Dimension(220, 130));
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Brimhaven Bot v" + BrimhavenAgilityBotPlugin.VERSION)
			.color(Color.GREEN)
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("State")
			.right(plugin.getState().name())
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Ticket")
			.right(plugin.isTicketAvailable() ? "Available" : "Waiting")
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Rewards")
			.right(Integer.toString(plugin.getRewardCount()))
			.build());
		BrimhavenArenaPath path = plugin.getCurrentPath();
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Path")
			.right(path == null ? "-" : Integer.toString(path.size()))
			.build());
		return super.render(graphics);
	}
}
