package net.runelite.client.plugins.cupidbot.mke_wintertodt;

import net.runelite.client.plugins.cupidbot.mke_wintertodt.enums.State;
import net.runelite.client.plugins.cupidbot.util.antiban.enums.Activity;

public class MKE_WintertodtAntibanActivityTest
{
	public static void main(String[] args)
	{
		activeWintertodtStatesMapToSkillActivities();
	}

	private static void activeWintertodtStatesMapToSkillActivities()
	{
		assertSame(Activity.GENERAL_WOODCUTTING, MKE_WintertodtScript.antibanActivityForState(State.CHOP_ROOTS));
		assertSame(Activity.GENERAL_FLETCHING, MKE_WintertodtScript.antibanActivityForState(State.FLETCH_LOGS));
		assertSame(Activity.GENERAL_FIREMAKING, MKE_WintertodtScript.antibanActivityForState(State.BURN_LOGS));
		assertSame(Activity.GENERAL_FIREMAKING, MKE_WintertodtScript.antibanActivityForState(State.WAITING));
	}

	private static void assertSame(Object expected, Object actual)
	{
		if (expected != actual)
		{
			throw new AssertionError("Expected " + expected + " but was " + actual);
		}
	}
}
