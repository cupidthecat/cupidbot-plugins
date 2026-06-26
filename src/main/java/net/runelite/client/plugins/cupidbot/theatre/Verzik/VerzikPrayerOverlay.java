package net.runelite.client.plugins.cupidbot.theatre.Verzik;

import net.runelite.api.Client;
import net.runelite.client.plugins.cupidbot.theatre.TheatreConfig;
import net.runelite.client.plugins.cupidbot.theatre.prayer.TheatrePrayerOverlay;
import net.runelite.client.plugins.cupidbot.theatre.prayer.TheatreUpcomingAttack;

import javax.inject.Inject;
import java.util.Queue;

public class VerzikPrayerOverlay extends TheatrePrayerOverlay
{

	private final Verzik plugin;

	@Inject
	protected VerzikPrayerOverlay(Client client, TheatreConfig config, Verzik plugin)
	{
		super(client, config);
		this.plugin = plugin;
	}

	@Override
	protected Queue<TheatreUpcomingAttack> getAttackQueue()
	{
		return plugin.getUpcomingAttackQueue();
	}

	@Override
	protected long getLastTick()
	{
		return plugin.getLastTick();
	}

	@Override
	protected boolean isEnabled()
	{
		return getConfig().verzikPrayerHelper();
	}
}
