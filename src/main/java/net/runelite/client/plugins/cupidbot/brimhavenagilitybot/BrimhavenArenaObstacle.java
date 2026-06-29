package net.runelite.client.plugins.cupidbot.brimhavenagilitybot;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adapted from rl-plugin-brimhaven-agility, BSD 2-Clause, Copyright (c) 2025-2026 Karl Goffin.
 */
public enum BrimhavenArenaObstacle
{
	BLADE('b', 1, 5, 6),
	ROPE_SWING('s', 1, 4),
	LOW_WALL('w', 1, 5),
	PLANK('p', 1, 9),
	BALANCING_ROPE('r', 1, 9),
	LOG_BALANCE('o', 1, 9),
	BALANCING_LEDGE('l', 1, 9),
	MONKEY_BARS('m', 1, 13),
	PILLAR('i', 1, 9),
	PRESSURE_PAD('a', 20, 4),
	FLOOR_SPIKES('f', 20, 4),
	HAND_HOLDS('h', 20, 10),
	SPINNING_BLADES('n', 40, 5),
	DARTS('d', 40, 10),
	IMPASSABLE('x', 999999, BrimhavenArenaPathFinder.NEVER_USE_WEIGHT);

	private static final Map<Character, BrimhavenArenaObstacle> BY_SHORT_FORM = Arrays.stream(values())
		.collect(Collectors.toMap(BrimhavenArenaObstacle::getShortForm, Function.identity()));

	private final char shortForm;
	private final int minLevel;
	private final int weight;
	private final int traversalTicks;

	BrimhavenArenaObstacle(char shortForm, int minLevel, int weight)
	{
		this(shortForm, minLevel, weight, weight);
	}

	BrimhavenArenaObstacle(char shortForm, int minLevel, int weight, int traversalTicks)
	{
		this.shortForm = shortForm;
		this.minLevel = minLevel;
		this.weight = weight;
		this.traversalTicks = traversalTicks;
	}

	public char getShortForm()
	{
		return shortForm;
	}

	public int getMinLevel()
	{
		return minLevel;
	}

	public int getWeight()
	{
		return weight;
	}

	public int getTraversalTicks()
	{
		return traversalTicks;
	}

	public static BrimhavenArenaObstacle from(char shortForm)
	{
		return BY_SHORT_FORM.getOrDefault(shortForm, IMPASSABLE);
	}
}
