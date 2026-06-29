package net.runelite.client.plugins.cupidbot.brimhavenagilitybot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;

/**
 * Adapted from rl-plugin-brimhaven-agility, BSD 2-Clause, Copyright (c) 2025-2026 Karl Goffin.
 */
@Singleton
public class BrimhavenWorldPathConverter
{
	private static final Set<Integer> PLANKS_1_X = Set.of(0, 1);
	private static final int PLANKS_1_Y = 1;
	private static final Set<Integer> PLANKS_2_X = Set.of(3, 4);
	private static final int PLANKS_2_Y = 4;

	private final BrimhavenPlankManager plankManager;
	private final BrimhavenAgilityBotConfig config;
	private int previousHash = -1;
	private List<WorldPoint> previousWorldPoints = null;

	@Inject
	public BrimhavenWorldPathConverter(BrimhavenPlankManager plankManager, BrimhavenAgilityBotConfig config)
	{
		this.plankManager = plankManager;
		this.config = config;
	}

	public synchronized List<WorldPoint> toWorldPoints(BrimhavenArenaPath path)
	{
		if (path == null)
		{
			return List.of();
		}

		int hash = Objects.hash(
			path,
			plankManager.getPlanks1Choice(),
			plankManager.getPlanks2Choice(),
			config.adjustPathForPlanks());
		if (hash == previousHash && previousWorldPoints != null)
		{
			return previousWorldPoints;
		}

		previousHash = hash;
		if (!config.adjustPathForPlanks() || path.getLocations().size() < 2)
		{
			previousWorldPoints = path.getLocations().stream()
				.map(BrimhavenArenaLocation::toCenteredWorldPoint)
				.collect(Collectors.toList());
			return previousWorldPoints;
		}

		previousWorldPoints = new ArrayList<>();
		previousWorldPoints.add(path.getLocations().get(0).toCenteredWorldPoint());
		for (int i = 1; i < path.getLocations().size(); i++)
		{
			BrimhavenArenaLocation prev = path.getLocations().get(i - 1);
			BrimhavenArenaLocation curr = path.getLocations().get(i);
			if (isPlankSet(prev, curr, PLANKS_1_X, PLANKS_1_Y, plankManager.getPlanks1Choice()))
			{
				addPlankPoints(previousWorldPoints, prev, curr, plankManager.getPlanks1Choice());
			}
			else if (isPlankSet(prev, curr, PLANKS_2_X, PLANKS_2_Y, plankManager.getPlanks2Choice()))
			{
				addPlankPoints(previousWorldPoints, prev, curr, plankManager.getPlanks2Choice());
			}
			previousWorldPoints.add(curr.toCenteredWorldPoint());
		}
		return previousWorldPoints;
	}

	private static boolean isPlankSet(
		BrimhavenArenaLocation prev,
		BrimhavenArenaLocation curr,
		Set<Integer> xs,
		int y,
		BrimhavenPlankChoice choice)
	{
		return choice != BrimhavenPlankChoice.UNKNOWN
			&& prev.getY() == y
			&& curr.getY() == y
			&& xs.contains(prev.getX())
			&& xs.contains(curr.getX());
	}

	private static void addPlankPoints(
		List<WorldPoint> out,
		BrimhavenArenaLocation prev,
		BrimhavenArenaLocation curr,
		BrimhavenPlankChoice choice)
	{
		boolean westToEast = prev.getX() < curr.getX();
		WorldPoint point1 = prev.toCenteredWorldPoint().dx((westToEast ? 1 : -1) * 2);
		WorldPoint point2 = curr.toCenteredWorldPoint().dx((westToEast ? -1 : 1) * 2);
		out.add(point1);
		out.add(point1.dy(choice.ordinal() - 1));
		out.add(point2.dy(choice.ordinal() - 1));
		out.add(point2);
	}
}
