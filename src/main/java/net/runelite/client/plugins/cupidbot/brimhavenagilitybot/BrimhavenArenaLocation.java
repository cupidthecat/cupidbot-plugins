package net.runelite.client.plugins.cupidbot.brimhavenagilitybot;

import java.util.Objects;
import net.runelite.api.coords.WorldPoint;

/**
 * Adapted from rl-plugin-brimhaven-agility, BSD 2-Clause, Copyright (c) 2025-2026 Karl Goffin.
 */
public final class BrimhavenArenaLocation implements Comparable<BrimhavenArenaLocation>
{
	private static final int PLATFORM_CENTER_OFFSET = 5;
	private static final int PLATFORM_WIDTH = 11;
	private static final int PLATFORM_HEIGHT = 11;
	private static final int PLATFORM_START_X = 9;
	private static final int PLATFORM_START_Y = 10;

	public static final int AGILITY_ARENA_REGION_ID = 11157;
	public static final int PLANE = 3;

	private final int x;
	private final int y;

	private BrimhavenArenaLocation(int x, int y)
	{
		this.x = x;
		this.y = y;
	}

	public static BrimhavenArenaLocation of(int x, int y)
	{
		return new BrimhavenArenaLocation(x, y);
	}

	public int getX()
	{
		return x;
	}

	public int getY()
	{
		return y;
	}

	public WorldPoint toCenteredWorldPoint()
	{
		return WorldPoint.fromRegion(
			AGILITY_ARENA_REGION_ID,
			PLATFORM_START_X + (x * PLATFORM_WIDTH),
			PLATFORM_START_Y + (y * PLATFORM_HEIGHT),
			PLANE);
	}

	public static BrimhavenArenaLocation fromWorldPoint(WorldPoint worldPoint)
	{
		if (worldPoint == null || worldPoint.getRegionID() != AGILITY_ARENA_REGION_ID)
		{
			return null;
		}

		int x = (worldPoint.getRegionX() + PLATFORM_CENTER_OFFSET - PLATFORM_START_X) / PLATFORM_WIDTH;
		int y = (worldPoint.getRegionY() + PLATFORM_CENTER_OFFSET - PLATFORM_START_Y) / PLATFORM_HEIGHT;
		if (x < 0 || y < 0 || x > 4 || y > 4)
		{
			return null;
		}
		return of(x, y);
	}

	@Override
	public int compareTo(BrimhavenArenaLocation other)
	{
		int cmp = Integer.compare(x, other.x);
		return cmp != 0 ? cmp : Integer.compare(y, other.y);
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof BrimhavenArenaLocation))
		{
			return false;
		}
		BrimhavenArenaLocation that = (BrimhavenArenaLocation) o;
		return x == that.x && y == that.y;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(x, y);
	}

	@Override
	public String toString()
	{
		return "(" + x + ", " + y + ")";
	}
}
