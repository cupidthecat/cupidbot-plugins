package net.runelite.client.plugins.cupidbot.mke_wintertodt.startup.location;

import net.runelite.api.coords.WorldPoint;

public class WintertodtLocationManagerTest
{
	public static void main(String[] args)
	{
		isInsideGameRoomReturnsFalseWhenLocationUnknown();
		isInsideGameRoomDetectsBossRoom();
		isAtWintertodtReturnsFalseWhenLocationUnknown();
		isAtWintertodtDetectsCampAreaOutsideGameRoom();
	}

	private static void isInsideGameRoomReturnsFalseWhenLocationUnknown()
	{
		assertFalse(WintertodtLocationManager.isInsideGameRoom(null));
	}

	private static void isInsideGameRoomDetectsBossRoom()
	{
		assertTrue(WintertodtLocationManager.isInsideGameRoom(new WorldPoint(1630, 3982, 0)));
		assertFalse(WintertodtLocationManager.isInsideGameRoom(new WorldPoint(1640, 3944, 0)));
	}

	private static void isAtWintertodtReturnsFalseWhenLocationUnknown()
	{
		assertFalse(WintertodtLocationManager.isAtWintertodt(null));
	}

	private static void isAtWintertodtDetectsCampAreaOutsideGameRoom()
	{
		assertTrue(WintertodtLocationManager.isAtWintertodt(new WorldPoint(1640, 3944, 0)));
		assertFalse(WintertodtLocationManager.isAtWintertodt(new WorldPoint(1630, 3982, 0)));
	}

	private static void assertTrue(boolean value)
	{
		if (!value)
		{
			throw new AssertionError("Expected true");
		}
	}

	private static void assertFalse(boolean value)
	{
		if (value)
		{
			throw new AssertionError("Expected false");
		}
	}
}
