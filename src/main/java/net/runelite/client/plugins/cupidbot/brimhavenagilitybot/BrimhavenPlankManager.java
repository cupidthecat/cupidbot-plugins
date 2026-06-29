package net.runelite.client.plugins.cupidbot.brimhavenagilitybot;

import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GroundObject;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;

/**
 * Adapted from rl-plugin-brimhaven-agility, BSD 2-Clause, Copyright (c) 2025-2026 Karl Goffin.
 */
@Slf4j
@Singleton
public class BrimhavenPlankManager
{
	private static final Set<Integer> BAD = Set.of(ObjectID.AGILITYARENA_PLANK_BROKE1);
	private static final Set<Integer> GOOD = Set.of(
		ObjectID.AGILITYARENA_PLANK,
		ObjectID.AGILITYARENA_PLANK2,
		ObjectID.AGILITYARENA_PLANK3,
		ObjectID.AGILITYARENA_PLANK_MIDDLE,
		ObjectID.AGILITYARENA_PLANK2_MIDDLE,
		ObjectID.AGILITYARENA_PLANK3_MIDDLE);

	private static final WorldArea PLANKS1_AREA = new WorldArea(2764, 9556, 6, 3, BrimhavenArenaLocation.PLANE);
	private static final WorldArea PLANKS2_AREA = new WorldArea(2797, 9589, 6, 3, BrimhavenArenaLocation.PLANE);

	private final GroundObject[][] planks1 = new GroundObject[3][6];
	private final GroundObject[][] planks2 = new GroundObject[3][6];
	private volatile boolean planks1Changed;
	private volatile boolean planks2Changed;

	@Getter
	private BrimhavenPlankChoice planks1Choice = BrimhavenPlankChoice.UNKNOWN;
	@Getter
	private BrimhavenPlankChoice planks2Choice = BrimhavenPlankChoice.UNKNOWN;

	@Inject
	public BrimhavenPlankManager()
	{
	}

	public void clear()
	{
		for (int y = 0; y < planks1.length; y++)
		{
			for (int x = 0; x < planks1[y].length; x++)
			{
				planks1[y][x] = null;
				planks2[y][x] = null;
			}
		}
		planks1Choice = BrimhavenPlankChoice.UNKNOWN;
		planks2Choice = BrimhavenPlankChoice.UNKNOWN;
		planks1Changed = false;
		planks2Changed = false;
	}

	public void recomputeCorrectPlanks()
	{
		if (planks1Changed)
		{
			planks1Choice = findCorrectPlank(planks1);
			planks1Changed = false;
		}
		if (planks2Changed)
		{
			planks2Choice = findCorrectPlank(planks2);
			planks2Changed = false;
		}
	}

	public boolean add(GroundObject groundObject)
	{
		return updateVal(groundObject, groundObject);
	}

	public boolean remove(GroundObject groundObject)
	{
		return updateVal(groundObject, null);
	}

	public boolean isOnBadPlank(WorldPoint point)
	{
		return isOnBadPlank(point, planks1Choice, planks2Choice);
	}

	static boolean isOnBadPlank(
		WorldPoint point,
		BrimhavenPlankChoice planks1Choice,
		BrimhavenPlankChoice planks2Choice)
	{
		if (point == null)
		{
			return false;
		}
		if (point.isInArea(PLANKS1_AREA))
		{
			return isBadPlankRow(point.getY() - PLANKS1_AREA.getY(), planks1Choice);
		}
		if (point.isInArea(PLANKS2_AREA))
		{
			return isBadPlankRow(point.getY() - PLANKS2_AREA.getY(), planks2Choice);
		}
		return false;
	}

	private static boolean isBadPlankRow(int row, BrimhavenPlankChoice choice)
	{
		return choice != BrimhavenPlankChoice.UNKNOWN && row != choice.ordinal();
	}

	private BrimhavenPlankChoice findCorrectPlank(GroundObject[][] planks)
	{
		for (int y = 0; y < planks.length; y++)
		{
			boolean hasBad = false;
			for (int x = 0; x < planks[y].length; x++)
			{
				if (planks[y][x] == null)
				{
					return BrimhavenPlankChoice.UNKNOWN;
				}
				if (BAD.contains(planks[y][x].getId()))
				{
					hasBad = true;
					break;
				}
			}
			if (!hasBad)
			{
				return BrimhavenPlankChoice.values()[y];
			}
		}
		return BrimhavenPlankChoice.UNKNOWN;
	}

	private boolean updateVal(GroundObject compareTo, GroundObject setTo)
	{
		if (compareTo == null || compareTo.getPlane() != BrimhavenArenaLocation.PLANE)
		{
			return false;
		}
		if (!GOOD.contains(compareTo.getId()) && !BAD.contains(compareTo.getId()))
		{
			return false;
		}
		return updateArray(compareTo, setTo, PLANKS1_AREA, planks1, true)
			|| updateArray(compareTo, setTo, PLANKS2_AREA, planks2, false);
	}

	private boolean updateArray(
		GroundObject compareTo,
		GroundObject setTo,
		WorldArea area,
		GroundObject[][] planks,
		boolean firstSet)
	{
		if (!compareTo.getWorldLocation().isInArea(area))
		{
			return false;
		}

		int y = compareTo.getWorldLocation().getY() - area.getY();
		int x = compareTo.getWorldLocation().getX() - area.getX();
		if (y < 0 || y >= planks.length || x < 0 || x >= planks[y].length)
		{
			log.warn("Brimhaven plank coordinate out of bounds x={} y={}", x, y);
			return false;
		}
		planks[y][x] = setTo;
		if (firstSet)
		{
			planks1Changed = true;
		}
		else
		{
			planks2Changed = true;
		}
		return true;
	}
}
