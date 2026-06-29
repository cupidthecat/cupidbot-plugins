package net.runelite.client.plugins.cupidbot.brimhavenagilitybot;

import java.util.Objects;

public final class BrimhavenArenaNeighbour implements Comparable<BrimhavenArenaNeighbour>
{
	private final BrimhavenArenaLocation location;
	private final BrimhavenArenaObstacle obstacle;

	public BrimhavenArenaNeighbour(BrimhavenArenaLocation location, BrimhavenArenaObstacle obstacle)
	{
		this.location = location;
		this.obstacle = obstacle;
	}

	public static BrimhavenArenaNeighbour of(int x, int y, BrimhavenArenaObstacle obstacle)
	{
		return new BrimhavenArenaNeighbour(BrimhavenArenaLocation.of(x, y), obstacle);
	}

	public BrimhavenArenaLocation getLocation()
	{
		return location;
	}

	public BrimhavenArenaObstacle getObstacle()
	{
		return obstacle;
	}

	@Override
	public int compareTo(BrimhavenArenaNeighbour other)
	{
		return location.compareTo(other.location);
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof BrimhavenArenaNeighbour))
		{
			return false;
		}
		BrimhavenArenaNeighbour that = (BrimhavenArenaNeighbour) o;
		return Objects.equals(location, that.location) && obstacle == that.obstacle;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(location, obstacle);
	}

	@Override
	public String toString()
	{
		return location + " via " + obstacle;
	}
}
