package net.runelite.client.plugins.cupidbot.autofishing;

import java.util.Arrays;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.FishingSpot;
import net.runelite.client.plugins.cupidbot.autofishing.enums.Fish;
import net.runelite.client.plugins.cupidbot.autofishing.enums.FishingMethod;
import net.runelite.client.plugins.cupidbot.autofishing.enums.FishingSpotLocation;

public class AutoFishingKarambwanjiTest
{
	public static void main(String[] args)
	{
		Fish karambwanji = Fish.KARAMBWANJI;
		FishingMethod method = karambwanji.getMethod();

		if (method != FishingMethod.KARAMBWANJI_NET)
		{
			throw new AssertionError("Karambwanji should use its level-5 net fishing method");
		}
		if (!method.getActions().equals(List.of("Net")))
		{
			throw new AssertionError("Karambwanji should use the Net fishing-spot action");
		}
		if (!method.getRequiredItems().equals(List.of("Small fishing net")))
		{
			throw new AssertionError("Karambwanji should require a small fishing net");
		}
		if (method.getLevelRequired() != 5)
		{
			throw new AssertionError("Karambwanji should require level 5 Fishing");
		}
		if (!Arrays.equals(karambwanji.getFishingSpot(), FishingSpot.KARAMBWANJI.getIds()))
		{
			throw new AssertionError("Karambwanji should target the canonical Karambwanji fishing spot IDs");
		}
		if (!karambwanji.getItemNames().equals(List.of("Raw karambwanji")))
		{
			throw new AssertionError("Karambwanji should recognize the stackable raw catch");
		}
		if (!karambwanji.getAvailableLocations().contains(FishingSpotLocation.FAIRY_RING_CKR))
		{
			throw new AssertionError("Karambwanji should expose the CKR holy lake fishing location");
		}

		WorldPoint lake = new WorldPoint(2806, 3014, 0);
		if (!lake.equals(karambwanji.getClosestLocation(new WorldPoint(2800, 3010, 0))))
		{
			throw new AssertionError("Karambwanji should travel to the CKR holy lake waypoint");
		}
	}
}
