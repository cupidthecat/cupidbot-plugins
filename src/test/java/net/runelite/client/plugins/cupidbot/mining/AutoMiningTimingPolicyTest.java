package net.runelite.client.plugins.cupidbot.mining;

import net.runelite.client.plugins.cupidbot.mining.data.Rocks;
import net.runelite.api.coords.WorldPoint;

public class AutoMiningTimingPolicyTest
{
	public static void main(String[] args)
	{
		assertNoPostOreCooldown(Rocks.TIN);
		assertNoPostOreCooldown(Rocks.COPPER);
		assertNoPostOreCooldown(Rocks.CLAY);
		assertNoPostOreCooldown(Rocks.IRON);
		assertNoPostOreCooldown(Rocks.GEM);
		assertNoPostOreCooldown(Rocks.RUNITE);
		assertShortPostRockClickWait();
		assertNoGlobalAntibanCooldownPause();
		assertWorldLocationReadiness();
		assertDiagnosticThrottle();
		assertDiagnosticMessageContents();
		assertMiningActionWaitsForCompletion();
		assertReachabilityCandidateLimit();
	}

	private static void assertNoPostOreCooldown(Rocks rock)
	{
		if (AutoMiningScript.shouldUsePostOreActionCooldown(rock))
		{
			throw new AssertionError("Auto Mining should not apply antiban action cooldown after mining " + rock);
		}
	}

	private static void assertWorldLocationReadiness()
	{
		if (AutoMiningScript.isWorldLocationReady(null))
		{
			throw new AssertionError("Auto Mining should wait when the local player world location is unavailable");
		}
		if (!AutoMiningScript.isWorldLocationReady(new WorldPoint(3222, 3222, 0)))
		{
			throw new AssertionError("Auto Mining should continue when the local player world location is available");
		}
	}

	private static void assertShortPostRockClickWait()
	{
		if (AutoMiningScript.getPostRockClickWaitMillis() > 1_000)
		{
			throw new AssertionError("Auto Mining should not block the mining loop for seconds after clicking a rock");
		}
	}

	private static void assertNoGlobalAntibanCooldownPause()
	{
		if (AutoMiningScript.shouldPauseForAntibanCooldown())
		{
			throw new AssertionError("Auto Mining should not pause on global antiban action cooldowns");
		}
	}

	private static void assertDiagnosticThrottle()
	{
		long interval = AutoMiningScript.getDiagnosticLogIntervalMillis();
		if (interval > 2_000)
		{
			throw new AssertionError("Auto Mining diagnostics should report repeated waits at least every 2 seconds");
		}
		if (AutoMiningScript.shouldLogDiagnostic(interval - 1, 0, "waiting for movement", "waiting for movement"))
		{
			throw new AssertionError("Auto Mining diagnostics should throttle repeated identical wait reasons");
		}
		if (!AutoMiningScript.shouldLogDiagnostic(1, 0, "no reachable rock", "waiting for movement"))
		{
			throw new AssertionError("Auto Mining diagnostics should log immediately when the wait reason changes");
		}
	}

	private static void assertDiagnosticMessageContents()
	{
		String message = AutoMiningScript.formatDiagnosticMessage(
				"no reachable rock",
				State.MINING,
				Rocks.TIN,
				new WorldPoint(3222, 3222, 0),
				new WorldPoint(3221, 3221, 0),
				20,
				false,
				true,
				false,
				false);

		assertContains(message, "reason=no reachable rock");
		assertContains(message, "state=MINING");
		assertContains(message, "rock=tin rocks");
		assertContains(message, "player=3222,3222,0");
		assertContains(message, "anchor=3221,3221,0");
		assertContains(message, "radius=20");
		assertContains(message, "moving=false");
		assertContains(message, "animating=true");
	}

	private static void assertMiningActionWaitsForCompletion()
	{
		long idleGrace = AutoMiningScript.getMiningActionIdleGraceMillis();
		long maxWait = AutoMiningScript.getMaxMiningActionWaitMillis();
		if (idleGrace > 3_000)
		{
			throw new AssertionError("Auto Mining should retry quickly when a click never starts mining");
		}
		if (maxWait < 30_000)
		{
			throw new AssertionError("Auto Mining should allow slow mining attempts to finish before retrying");
		}
		if (!AutoMiningScript.shouldKeepMiningActionInProgress(false, true, false, false, false, true, 10_000, 1, 10_000))
		{
			throw new AssertionError("Auto Mining should not reclick while the mining animation is still active");
		}
		if (AutoMiningScript.shouldKeepMiningActionInProgress(false, true, true, false, false, true, 10_000, 1, 10_000))
		{
			throw new AssertionError("Auto Mining should release the current action after mining XP changes");
		}
		if (AutoMiningScript.shouldKeepMiningActionInProgress(false, true, false, true, false, true, 10_000, 1, 10_000))
		{
			throw new AssertionError("Auto Mining should release the current action after inventory changes");
		}
		if (AutoMiningScript.shouldKeepMiningActionInProgress(false, true, false, false, false, false, 10_000, 1, 10_000))
		{
			throw new AssertionError("Auto Mining should release the current action after the clicked rock disappears");
		}
		if (!AutoMiningScript.shouldKeepMiningActionInProgress(false, false, false, false, false, true, idleGrace, 1, 1))
		{
			throw new AssertionError("Auto Mining should allow a short idle grace after a click starts");
		}
		if (AutoMiningScript.shouldKeepMiningActionInProgress(false, false, false, false, false, true, idleGrace + 2, 1, 1))
		{
			throw new AssertionError("Auto Mining should retry when a click never starts mining");
		}
		if (AutoMiningScript.shouldKeepMiningActionInProgress(false, true, false, false, false, true, maxWait + 2, 1, maxWait + 2))
		{
			throw new AssertionError("Auto Mining should eventually recover from a stuck mining animation");
		}
	}

	private static void assertReachabilityCandidateLimit()
	{
		int limit = AutoMiningScript.getReachabilityCandidateLimit();
		if (limit < 1 || limit > 4)
		{
			throw new AssertionError("Auto Mining should bound expensive reachability checks to the nearest rock candidates");
		}
		if (!AutoMiningScript.shouldCheckRockReachabilityCandidate(true, 0))
		{
			throw new AssertionError("Auto Mining should check the nearest named rock candidate");
		}
		if (AutoMiningScript.shouldCheckRockReachabilityCandidate(false, 0))
		{
			throw new AssertionError("Auto Mining should not run reachability checks before the rock name matches");
		}
		if (AutoMiningScript.shouldCheckRockReachabilityCandidate(true, limit))
		{
			throw new AssertionError("Auto Mining should stop expensive reachability checks after the candidate limit");
		}
	}

	private static void assertContains(String haystack, String needle)
	{
		if (!haystack.contains(needle))
		{
			throw new AssertionError("Expected diagnostic message to contain '" + needle + "' but was: " + haystack);
		}
	}
}
