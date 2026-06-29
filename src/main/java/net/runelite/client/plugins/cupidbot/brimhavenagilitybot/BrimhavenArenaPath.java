package net.runelite.client.plugins.cupidbot.brimhavenagilitybot;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;

public final class BrimhavenArenaPath
{
	private final List<BrimhavenArenaLocation> locations;

	public BrimhavenArenaPath(List<BrimhavenArenaLocation> locations)
	{
		this.locations = List.copyOf(locations);
	}

	public List<BrimhavenArenaLocation> getLocations()
	{
		return locations;
	}

	public int size()
	{
		return locations.size();
	}

	public boolean hasPathChanged(WorldPoint playerLocation, WorldPoint dispenserLocation)
	{
		if (locations.isEmpty())
		{
			return true;
		}
		return !locations.get(0).equals(BrimhavenArenaLocation.fromWorldPoint(playerLocation))
			|| !locations.get(locations.size() - 1).equals(BrimhavenArenaLocation.fromWorldPoint(dispenserLocation));
	}

	public BrimhavenArenaPath subPath(WorldPoint newStartLocation, WorldPoint newEndLocation)
	{
		BrimhavenArenaLocation start = BrimhavenArenaLocation.fromWorldPoint(newStartLocation);
		BrimhavenArenaLocation end = BrimhavenArenaLocation.fromWorldPoint(newEndLocation);
		int startIndex = locations.indexOf(start);
		int endIndex = locations.indexOf(end);
		if (startIndex < 0 || endIndex < 0 || endIndex < startIndex)
		{
			return null;
		}
		if (startIndex == 0 && endIndex == locations.size() - 1)
		{
			return this;
		}
		return new BrimhavenArenaPath(locations.subList(startIndex, endIndex + 1));
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof BrimhavenArenaPath))
		{
			return false;
		}
		BrimhavenArenaPath that = (BrimhavenArenaPath) o;
		return Objects.equals(locations, that.locations);
	}

	@Override
	public int hashCode()
	{
		return locations.hashCode();
	}

	@Override
	public String toString()
	{
		return locations.stream().map(BrimhavenArenaLocation::toString).collect(Collectors.joining("->"));
	}
}
