package net.runelite.client.plugins.cupidbot.brimhavenagilitybot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * Adapted from rl-plugin-brimhaven-agility, BSD 2-Clause, Copyright (c) 2025-2026 Karl Goffin.
 */
@Slf4j
public final class BrimhavenArenaPathFinder
{
	private static final int MAX_VISITED_NODES = 25;
	private static final int MAX_TOTAL_NEIGHBOURS = 100;
	public static final int NEVER_USE_WEIGHT = 999999;
	public static final int AVOID_WEIGHT = 999;

	private BrimhavenArenaPathFinder()
	{
	}

	public static BrimhavenArenaPath findPath(
		WorldPoint playerLocation,
		WorldPoint dispenserLocation,
		int playerAgilityLevel,
		BrimhavenTraversalPreferences preferences)
	{
		return findPath(
			BrimhavenArenaLocation.fromWorldPoint(playerLocation),
			BrimhavenArenaLocation.fromWorldPoint(dispenserLocation),
			playerAgilityLevel,
			preferences);
	}

	public static BrimhavenArenaPath findPath(
		BrimhavenArenaLocation start,
		BrimhavenArenaLocation end,
		int playerAgilityLevel,
		BrimhavenTraversalPreferences preferences)
	{
		if (start == null || end == null)
		{
			return null;
		}

		Map<BrimhavenArenaLocation, Integer> gScore = new MapWithDefault<>(NEVER_USE_WEIGHT);
		gScore.put(start, 0);
		Map<BrimhavenArenaLocation, Integer> fScore = new MapWithDefault<>(NEVER_USE_WEIGHT);
		fScore.put(start, heuristic(start, end));

		PriorityQueue<BrimhavenArenaLocation> openSet = new PriorityQueue<>(Comparator.comparing(fScore::get));
		openSet.add(start);

		Map<BrimhavenArenaLocation, BrimhavenArenaLocation> cameFrom = new HashMap<>();
		int visitedNodes = 0;
		int examinedNeighbours = 0;

		while (!openSet.isEmpty())
		{
			BrimhavenArenaLocation current = openSet.poll();
			visitedNodes++;
			if (current.equals(end))
			{
				return reconstructPath(cameFrom, current);
			}
			if (visitedNodes > MAX_VISITED_NODES)
			{
				log.warn("Exceeded Brimhaven pathfinder node guard");
				return null;
			}

			for (BrimhavenArenaNeighbour neighbour : BrimhavenArenaGraph.getNeighbours(current))
			{
				examinedNeighbours++;
				if (examinedNeighbours > MAX_TOTAL_NEIGHBOURS)
				{
					log.warn("Exceeded Brimhaven pathfinder neighbour guard");
					return null;
				}
				if (neighbour.getLocation().equals(current))
				{
					continue;
				}

				int tentativeGScore = gScore.get(current) + weightedDistance(neighbour, playerAgilityLevel, preferences);
				if (tentativeGScore < gScore.get(neighbour.getLocation()))
				{
					cameFrom.put(neighbour.getLocation(), current);
					gScore.put(neighbour.getLocation(), tentativeGScore);
					fScore.put(neighbour.getLocation(), tentativeGScore + heuristic(neighbour.getLocation(), end));
					if (!openSet.contains(neighbour.getLocation()))
					{
						openSet.add(neighbour.getLocation());
					}
				}
			}
		}

		return null;
	}

	private static int weightedDistance(
		BrimhavenArenaNeighbour neighbour,
		int agilityLevel,
		BrimhavenTraversalPreferences preferences)
	{
		if (neighbour.getObstacle().getMinLevel() > agilityLevel)
		{
			return NEVER_USE_WEIGHT;
		}
		if (preferences.shouldAvoid(neighbour.getObstacle()))
		{
			return AVOID_WEIGHT;
		}
		return neighbour.getObstacle().getWeight();
	}

	private static BrimhavenArenaPath reconstructPath(
		Map<BrimhavenArenaLocation, BrimhavenArenaLocation> cameFrom,
		BrimhavenArenaLocation end)
	{
		List<BrimhavenArenaLocation> path = new ArrayList<>();
		path.add(end);
		BrimhavenArenaLocation current = end;
		while (cameFrom.containsKey(current) && path.size() < MAX_VISITED_NODES)
		{
			current = cameFrom.get(current);
			path.add(current);
		}
		Collections.reverse(path);
		return new BrimhavenArenaPath(path);
	}

	private static int heuristic(BrimhavenArenaLocation start, BrimhavenArenaLocation end)
	{
		return Math.abs(end.getX() - start.getX()) + Math.abs(end.getY() - start.getY());
	}
}
