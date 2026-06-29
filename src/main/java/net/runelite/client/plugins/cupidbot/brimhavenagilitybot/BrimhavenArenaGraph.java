package net.runelite.client.plugins.cupidbot.brimhavenagilitybot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * Adapted from rl-plugin-brimhaven-agility, BSD 2-Clause, Copyright (c) 2025-2026 Karl Goffin.
 */
@Slf4j
public final class BrimhavenArenaGraph
{
	private static final String COMMENT_CHAR = "#";
	private static volatile boolean loaded;
	private static final Map<BrimhavenArenaLocation, List<BrimhavenArenaNeighbour>> NEIGHBOURS = new HashMap<>();

	private BrimhavenArenaGraph()
	{
	}

	public static List<BrimhavenArenaNeighbour> getNeighbours(BrimhavenArenaLocation location)
	{
		if (!loaded)
		{
			loadNeighbours();
		}
		return NEIGHBOURS.getOrDefault(location, List.of());
	}

	public static BrimhavenArenaObstacle obstacleBetween(BrimhavenArenaLocation start, BrimhavenArenaLocation end)
	{
		return getNeighbours(start).stream()
			.filter(neighbour -> neighbour.getLocation().equals(end))
			.map(BrimhavenArenaNeighbour::getObstacle)
			.findFirst()
			.orElse(null);
	}

	private static synchronized void loadNeighbours()
	{
		if (loaded)
		{
			return;
		}

		try (InputStream input = resourceStream())
		{
			if (input == null)
			{
				log.error("Brimhaven arena layout resource not found");
				loaded = true;
				return;
			}

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)))
			{
				reader.lines().forEach(BrimhavenArenaGraph::parseLayoutLine);
				loaded = true;
			}
		}
		catch (IOException ex)
		{
			log.error("Failed to load Brimhaven arena layout", ex);
			loaded = true;
		}
	}

	private static InputStream resourceStream()
	{
		InputStream input = BrimhavenArenaGraph.class.getResourceAsStream("arena_layout.txt");
		if (input != null)
		{
			return input;
		}
		return BrimhavenArenaGraph.class.getResourceAsStream(
			"/net/runelite/client/plugins/cupidbot/brimhavenagilitybot/arena_layout.txt");
	}

	private static void parseLayoutLine(String rawLine)
	{
		try
		{
			String line = rawLine.split(COMMENT_CHAR, 2)[0].trim();
			if (line.isEmpty())
			{
				return;
			}

			int srcX = Integer.parseInt(line.substring(0, 1));
			int srcY = Integer.parseInt(line.substring(1, 2));
			char obstacle = line.charAt(2);
			int dstX = Integer.parseInt(line.substring(3, 4));
			int dstY = Integer.parseInt(line.substring(4, 5));

			addNeighbour(srcX, srcY, dstX, dstY, obstacle);
			addNeighbour(dstX, dstY, srcX, srcY, obstacle);
		}
		catch (Exception ex)
		{
			log.error("Failed to parse Brimhaven arena layout line '{}'", rawLine, ex);
		}
	}

	private static void addNeighbour(int srcX, int srcY, int dstX, int dstY, char obstacle)
	{
		NEIGHBOURS.merge(
			BrimhavenArenaLocation.of(srcX, srcY),
			List.of(BrimhavenArenaNeighbour.of(dstX, dstY, BrimhavenArenaObstacle.from(obstacle))),
			(existing, added) -> Stream.concat(existing.stream(), added.stream())
				.sorted()
				.collect(Collectors.toUnmodifiableList()));
	}

	public static synchronized void unload()
	{
		NEIGHBOURS.clear();
		loaded = false;
	}
}
