package net.runelite.client.plugins.cupidbot.agility;

import net.runelite.client.plugins.cupidbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.cupidbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.cupidbot.util.antiban.enums.Activity;

public class AgilityAntibanSettingsTest
{
	public static void main(String[] args)
	{
		agilityStartupPreservesUserAntibanSettings();
	}

	private static void agilityStartupPreservesUserAntibanSettings()
	{
		Rs2Antiban.resetAntibanSettings(true);
		Rs2AntibanSettings.naturalMouse = false;
		Rs2AntibanSettings.simulateMistakes = true;
		Rs2AntibanSettings.moveMouseOffScreen = true;
		Rs2AntibanSettings.moveMouseRandomly = true;
		Rs2AntibanSettings.dynamicIntensity = true;
		Rs2AntibanSettings.dynamicActivity = true;

		AgilityScript.configureAntibanSettings();

		assertFalse(Rs2AntibanSettings.naturalMouse, "Natural Mouse should keep the user selection");
		assertTrue(Rs2AntibanSettings.simulateMistakes, "Simulate Mistakes should keep the user selection");
		assertTrue(Rs2AntibanSettings.moveMouseOffScreen, "Move Mouse Off Screen should keep the user selection");
		assertTrue(Rs2AntibanSettings.moveMouseRandomly, "Move Mouse Randomly should keep the user selection");
		assertTrue(Rs2AntibanSettings.dynamicIntensity, "Dynamic Activity Intensity should keep the user selection");
		assertTrue(Rs2AntibanSettings.dynamicActivity, "Dynamic Activity should keep the user selection");
		assertSame(Activity.GENERAL_AGILITY, Rs2Antiban.getActivity());
	}

	private static void assertTrue(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}

	private static void assertFalse(boolean condition, String message)
	{
		if (condition)
		{
			throw new AssertionError(message);
		}
	}

	private static void assertSame(Object expected, Object actual)
	{
		if (expected != actual)
		{
			throw new AssertionError("Expected " + expected + " but was " + actual);
		}
	}
}
