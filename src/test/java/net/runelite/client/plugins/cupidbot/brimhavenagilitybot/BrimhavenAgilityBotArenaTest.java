package net.runelite.client.plugins.cupidbot.brimhavenagilitybot;

import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;

public class BrimhavenAgilityBotArenaTest
{
	public static void main(String[] args)
	{
		assertArenaLayoutLoadsFromPluginResourcePackage();
		assertPathFinderFindsWeightedArenaPath();
		assertLowAgilityPathDoesNotUseHighLevelObstacles();
		assertDispenserIsMappedSeparatelyFromDartsObstacle();
		assertDispenserLookupCoversTicketDispenserVariants();
		assertDispenserActionPriorityTagsPillar();
		assertArenaContinueDialogueDoesNotRequireAvailableTicket();
		assertRewardItemsAreRetainedAcrossBanking();
		assertRewardCounterSumsStackQuantities();
		assertBankingRetainsRewardsCoinsAndFood();
		assertKnownPlankChoiceRejectsWrongRows();
		assertObstacleFallbackTriggersWhenObjectClickDoesNotAdvance();
		assertObstacleCrossingRequiresNextPlatform();
		assertObstacleRetryDecisionUsesAttemptLimit();
		assertObstacleRuntimeTicksMatchWikiStats();
		assertObstacleWaitUsesReferenceTickWeights();
		assertObstacleSettleRequiresCrossingMinimumDurationAndIdle();
		assertObjectClickObstaclesUseObjectInteractions();
		assertTrapObstaclesClickPastInsteadOfObject();
		assertEveryTraversableObstacleHasInteractionMode();
	}

	private static void assertArenaLayoutLoadsFromPluginResourcePackage()
	{
		BrimhavenArenaGraph.unload();
		List<BrimhavenArenaNeighbour> neighbours = BrimhavenArenaGraph.getNeighbours(BrimhavenArenaLocation.of(0, 0));

		if (!neighbours.contains(BrimhavenArenaNeighbour.of(1, 0, BrimhavenArenaObstacle.BALANCING_LEDGE)))
		{
			throw new AssertionError("Arena layout should load ledge neighbour 00l10 from plugin resources");
		}
		if (!neighbours.contains(BrimhavenArenaNeighbour.of(0, 1, BrimhavenArenaObstacle.PILLAR)))
		{
			throw new AssertionError("Arena layout should load pillar neighbour 00i01 from plugin resources");
		}
	}

	private static void assertPathFinderFindsWeightedArenaPath()
	{
		BrimhavenArenaPath path = BrimhavenArenaPathFinder.findPath(
			BrimhavenArenaLocation.of(0, 0),
			BrimhavenArenaLocation.of(4, 4),
			99,
			BrimhavenTraversalPreferences.none());

		if (path == null || path.size() < 2)
		{
			throw new AssertionError("Pathfinder should find a path between opposite arena corners");
		}
		if (!BrimhavenArenaLocation.of(0, 0).equals(path.getLocations().get(0)))
		{
			throw new AssertionError("Path should start at the requested platform");
		}
		if (!BrimhavenArenaLocation.of(4, 4).equals(path.getLocations().get(path.size() - 1)))
		{
			throw new AssertionError("Path should end at the requested dispenser platform");
		}
	}

	private static void assertLowAgilityPathDoesNotUseHighLevelObstacles()
	{
		BrimhavenArenaPath path = BrimhavenArenaPathFinder.findPath(
			BrimhavenArenaLocation.of(0, 0),
			BrimhavenArenaLocation.of(4, 4),
			1,
			BrimhavenTraversalPreferences.none());

		if (path == null)
		{
			throw new AssertionError("Pathfinder should still find a level-1 legal route when one exists");
		}

		for (int i = 1; i < path.size(); i++)
		{
			BrimhavenArenaObstacle obstacle = BrimhavenArenaGraph.obstacleBetween(
				path.getLocations().get(i - 1),
				path.getLocations().get(i));
			if (obstacle == null)
			{
				throw new AssertionError("Every adjacent path step should map back to a layout obstacle");
			}
			if (obstacle.getMinLevel() > 1)
			{
				throw new AssertionError("Level-1 path should not use obstacle requiring level " + obstacle.getMinLevel());
			}
		}
	}

	private static void assertDispenserIsMappedSeparatelyFromDartsObstacle()
	{
		if (BrimhavenObstacleInteraction.getDispenserObjectId() != ObjectID.AGILITY_TICKETPILLAR)
		{
			throw new AssertionError("Ticket dispenser should use AGILITY_TICKETPILLAR");
		}
		if (BrimhavenObstacleInteraction.idsFor(BrimhavenArenaObstacle.DARTS)
			.contains(BrimhavenObstacleInteraction.getDispenserObjectId()))
		{
			throw new AssertionError("Darts obstacle must not include the ticket dispenser object ID");
		}
		if (!BrimhavenObstacleInteraction.idsFor(BrimhavenArenaObstacle.DARTS)
			.contains(ObjectID.AGILITYARENA_POISONDARTS))
		{
			throw new AssertionError("Darts obstacle should include the poison darts object ID");
		}
		if (!BrimhavenObstacleInteraction.idsFor(BrimhavenArenaObstacle.PLANK)
			.contains(ObjectID.AGILITYARENA_PLANK_BROKE1))
		{
			throw new AssertionError("Plank mapping should include broken plank IDs so bad planks can be detected");
		}
	}

	private static void assertDispenserLookupCoversTicketDispenserVariants()
	{
		if (!BrimhavenObstacleInteraction.dispenserObjectIds().contains(ObjectID.AGILITY_TICKETPILLAR))
		{
			throw new AssertionError("Dispenser lookup should include the modern ticket pillar object ID");
		}
		if (!BrimhavenObstacleInteraction.dispenserObjectIds().contains(ObjectID.AGILITYARENA_POISONDARTS))
		{
			throw new AssertionError("Dispenser lookup should include the legacy gameval ticket dispenser variant");
		}
	}

	private static void assertDispenserActionPriorityTagsPillar()
	{
		if (!"Tag".equals(BrimhavenObstacleInteraction.dispenserActions().get(0)))
		{
			throw new AssertionError("Ticket dispenser should prefer the Tag action");
		}
		if (!BrimhavenObstacleInteraction.dispenserActions().contains("Touch"))
		{
			throw new AssertionError("Ticket dispenser should retain Touch as a fallback action");
		}
	}

	private static void assertArenaContinueDialogueDoesNotRequireAvailableTicket()
	{
		if (!BrimhavenAgilityBotScript.shouldAdvanceArenaContinueDialogue(false, true, true))
		{
			throw new AssertionError("Post-ticket continue dialogue must be handled after ticket availability clears");
		}
		if (!BrimhavenAgilityBotScript.shouldAdvanceArenaContinueDialogue(true, true, true))
		{
			throw new AssertionError("Arena continue dialogue should be handled while routing to a ticket");
		}
		if (BrimhavenAgilityBotScript.shouldAdvanceArenaContinueDialogue(false, true, false))
		{
			throw new AssertionError("Arena dialogue without a continue prompt should not be clicked as continue");
		}
		if (BrimhavenAgilityBotScript.shouldAdvanceArenaContinueDialogue(false, false, true))
		{
			throw new AssertionError("Continue prompts should only be handled while actually in dialogue");
		}
	}

	private static void assertRewardItemsAreRetainedAcrossBanking()
	{
		if (!BrimhavenAgilityRewards.isRewardItem(ItemID.AGILITYARENA_TICKET)
			|| !BrimhavenAgilityRewards.isRewardItem(ItemID.AGILITYARENA_TICKET_NEW)
			|| !BrimhavenAgilityRewards.isRewardItem(ItemID.AGILITYARENA_VOUCHER))
		{
			throw new AssertionError("Bank setup should retain legacy tickets, modern tickets, and vouchers");
		}
		if (BrimhavenAgilityRewards.isRewardItem(ItemID.COINS))
		{
			throw new AssertionError("Coins are a supply item, not an arena reward to preserve");
		}
	}

	private static void assertRewardCounterSumsStackQuantities()
	{
		Map<Integer, Integer> inventoryQuantities = Map.of(
			ItemID.AGILITYARENA_TICKET, 7,
			ItemID.AGILITYARENA_TICKET_NEW, 3,
			ItemID.AGILITYARENA_VOUCHER, 12,
			ItemID.COINS, 99_999);

		int rewards = BrimhavenAgilityRewards.rewardQuantity(id -> inventoryQuantities.getOrDefault(id, 0));
		if (rewards != 22)
		{
			throw new AssertionError("Reward counter should sum stacked ticket and voucher quantities, not matching slots");
		}
	}

	private static void assertBankingRetainsRewardsCoinsAndFood()
	{
		if (!BrimhavenAgilityRewards.bankRetainedItemIds(ItemID.SHARK).contains(ItemID.AGILITYARENA_TICKET)
			|| !BrimhavenAgilityRewards.bankRetainedItemIds(ItemID.SHARK).contains(ItemID.AGILITYARENA_TICKET_NEW)
			|| !BrimhavenAgilityRewards.bankRetainedItemIds(ItemID.SHARK).contains(ItemID.AGILITYARENA_VOUCHER))
		{
			throw new AssertionError("Banking must retain Brimhaven tickets and vouchers");
		}
		if (!BrimhavenAgilityRewards.bankRetainedItemIds(ItemID.SHARK).contains(ItemID.COINS))
		{
			throw new AssertionError("Banking must retain withdrawn coins between bank loop ticks");
		}
		if (!BrimhavenAgilityRewards.bankRetainedItemIds(ItemID.SHARK).contains(ItemID.SHARK))
		{
			throw new AssertionError("Banking must retain configured food between bank loop ticks");
		}
	}

	private static void assertKnownPlankChoiceRejectsWrongRows()
	{
		WorldPoint bottomPlank1 = new WorldPoint(2764, 9556, BrimhavenArenaLocation.PLANE);
		WorldPoint middlePlank1 = new WorldPoint(2764, 9557, BrimhavenArenaLocation.PLANE);
		WorldPoint topPlank1 = new WorldPoint(2764, 9558, BrimhavenArenaLocation.PLANE);

		if (!BrimhavenPlankManager.isOnBadPlank(bottomPlank1, BrimhavenPlankChoice.TOP, BrimhavenPlankChoice.UNKNOWN)
			|| !BrimhavenPlankManager.isOnBadPlank(middlePlank1, BrimhavenPlankChoice.TOP, BrimhavenPlankChoice.UNKNOWN))
		{
			throw new AssertionError("When top plank is known correct, bottom and middle rows should be rejected");
		}
		if (BrimhavenPlankManager.isOnBadPlank(topPlank1, BrimhavenPlankChoice.TOP, BrimhavenPlankChoice.UNKNOWN))
		{
			throw new AssertionError("Known correct top plank row should not be rejected");
		}
		if (BrimhavenPlankManager.isOnBadPlank(bottomPlank1, BrimhavenPlankChoice.UNKNOWN, BrimhavenPlankChoice.UNKNOWN))
		{
			throw new AssertionError("Unknown plank choice should not reject any plank row");
		}
	}

	private static void assertObstacleFallbackTriggersWhenObjectClickDoesNotAdvance()
	{
		if (!BrimhavenAgilityBotScript.shouldClickPastObstacleAfterObjectAttempt(true, false))
		{
			throw new AssertionError("Object click without platform progress should trigger a click-past fallback");
		}
		if (BrimhavenAgilityBotScript.shouldClickPastObstacleAfterObjectAttempt(true, true))
		{
			throw new AssertionError("Successful platform progress should not trigger a click-past fallback");
		}
		if (BrimhavenAgilityBotScript.shouldClickPastObstacleAfterObjectAttempt(false, false))
		{
			throw new AssertionError("No object click should not trigger a click-past fallback");
		}
	}

	private static void assertObstacleCrossingRequiresNextPlatform()
	{
		WorldPoint oldPlatformMovement = BrimhavenArenaLocation.of(0, 0).toCenteredWorldPoint().dx(5);
		WorldPoint nextPlatform = BrimhavenArenaLocation.of(1, 0).toCenteredWorldPoint();

		if (BrimhavenAgilityBotScript.hasCrossedObstacle(oldPlatformMovement, BrimhavenArenaLocation.of(1, 0)))
		{
			throw new AssertionError("Moving inside the previous platform must not count as crossing an obstacle");
		}
		if (!BrimhavenAgilityBotScript.hasCrossedObstacle(nextPlatform, BrimhavenArenaLocation.of(1, 0)))
		{
			throw new AssertionError("Being on the next platform should count as crossing an obstacle");
		}
		if (BrimhavenAgilityBotScript.hasCrossedObstacle(null, BrimhavenArenaLocation.of(1, 0)))
		{
			throw new AssertionError("A missing player location should not count as crossing an obstacle");
		}
	}

	private static void assertObstacleRetryDecisionUsesAttemptLimit()
	{
		if (!BrimhavenAgilityBotScript.shouldRetryObstacleAttempt(true, false, 0, 2))
		{
			throw new AssertionError("A clicked obstacle that did not cross should retry while attempts remain");
		}
		if (BrimhavenAgilityBotScript.shouldRetryObstacleAttempt(true, true, 0, 2))
		{
			throw new AssertionError("A successful crossing should not retry");
		}
		if (BrimhavenAgilityBotScript.shouldRetryObstacleAttempt(true, false, 2, 2))
		{
			throw new AssertionError("Retry should stop at the configured attempt limit");
		}
		if (BrimhavenAgilityBotScript.shouldRetryObstacleAttempt(false, false, 0, 2))
		{
			throw new AssertionError("A failed click should not spin inside the obstacle retry loop");
		}
	}

	private static void assertObstacleRuntimeTicksMatchWikiStats()
	{
		assertRuntimeTicks(BrimhavenArenaObstacle.BLADE, 6);
		assertRuntimeTicks(BrimhavenArenaObstacle.ROPE_SWING, 4);
		assertRuntimeTicks(BrimhavenArenaObstacle.LOW_WALL, 5);
		assertRuntimeTicks(BrimhavenArenaObstacle.PLANK, 9);
		assertRuntimeTicks(BrimhavenArenaObstacle.BALANCING_ROPE, 9);
		assertRuntimeTicks(BrimhavenArenaObstacle.LOG_BALANCE, 9);
		assertRuntimeTicks(BrimhavenArenaObstacle.BALANCING_LEDGE, 9);
		assertRuntimeTicks(BrimhavenArenaObstacle.MONKEY_BARS, 13);
		assertRuntimeTicks(BrimhavenArenaObstacle.PILLAR, 9);
		assertRuntimeTicks(BrimhavenArenaObstacle.PRESSURE_PAD, 4);
		assertRuntimeTicks(BrimhavenArenaObstacle.FLOOR_SPIKES, 4);
		assertRuntimeTicks(BrimhavenArenaObstacle.HAND_HOLDS, 10);
		assertRuntimeTicks(BrimhavenArenaObstacle.SPINNING_BLADES, 5);
		assertRuntimeTicks(BrimhavenArenaObstacle.DARTS, 10);
	}

	private static void assertRuntimeTicks(BrimhavenArenaObstacle obstacle, int expectedTicks)
	{
		if (obstacle.getTraversalTicks() != expectedTicks)
		{
			throw new AssertionError(obstacle + " should use " + expectedTicks + " runtime traversal ticks");
		}
	}

	private static void assertObstacleWaitUsesReferenceTickWeights()
	{
		int ropeSwingMinimum = BrimhavenAgilityBotScript.obstacleTraversalMinimumMillis(BrimhavenArenaObstacle.ROPE_SWING);
		int monkeyBarsMinimum = BrimhavenAgilityBotScript.obstacleTraversalMinimumMillis(BrimhavenArenaObstacle.MONKEY_BARS);

		if (ropeSwingMinimum != BrimhavenArenaObstacle.ROPE_SWING.getTraversalTicks() * BrimhavenAgilityBotScript.GAME_TICK_MILLIS)
		{
			throw new AssertionError("Obstacle traversal minimum should be based on wiki runtime ticks");
		}
		if (monkeyBarsMinimum <= ropeSwingMinimum)
		{
			throw new AssertionError("Long obstacles like monkey bars should wait longer than short obstacles");
		}
		if (BrimhavenAgilityBotScript.obstacleTraversalTimeoutMillis(BrimhavenArenaObstacle.MONKEY_BARS) <= monkeyBarsMinimum)
		{
			throw new AssertionError("Obstacle timeout should leave retry headroom beyond the minimum traversal duration");
		}
	}

	private static void assertObstacleSettleRequiresCrossingMinimumDurationAndIdle()
	{
		int minimumMillis = BrimhavenAgilityBotScript.obstacleTraversalMinimumMillis(BrimhavenArenaObstacle.MONKEY_BARS);
		int stableIdleMillis = BrimhavenAgilityBotScript.OBSTACLE_STABLE_IDLE_MILLIS;

		if (BrimhavenAgilityBotScript.isObstacleTraversalSettled(true, false, minimumMillis - 1L, stableIdleMillis, minimumMillis))
		{
			throw new AssertionError("Traversal should not settle before the obstacle-specific minimum duration");
		}
		if (BrimhavenAgilityBotScript.isObstacleTraversalSettled(true, true, minimumMillis + 1L, stableIdleMillis, minimumMillis))
		{
			throw new AssertionError("Traversal should not settle while the player is still moving or animating");
		}
		if (BrimhavenAgilityBotScript.isObstacleTraversalSettled(false, false, minimumMillis + 1L, stableIdleMillis, minimumMillis))
		{
			throw new AssertionError("Traversal should not settle without reaching the next platform");
		}
		if (BrimhavenAgilityBotScript.isObstacleTraversalSettled(true, false, minimumMillis + 1L, stableIdleMillis - 1L, minimumMillis))
		{
			throw new AssertionError("Traversal should require a stable idle window after motion ends");
		}
		if (!BrimhavenAgilityBotScript.isObstacleTraversalSettled(true, false, minimumMillis + 1L, stableIdleMillis, minimumMillis))
		{
			throw new AssertionError("Traversal should settle after crossing, minimum duration, and stable idle");
		}
	}

	private static void assertObjectClickObstaclesUseObjectInteractions()
	{
		for (BrimhavenArenaObstacle obstacle : new BrimhavenArenaObstacle[] {
			BrimhavenArenaObstacle.PILLAR,
			BrimhavenArenaObstacle.MONKEY_BARS,
			BrimhavenArenaObstacle.LOW_WALL,
			BrimhavenArenaObstacle.BALANCING_ROPE,
			BrimhavenArenaObstacle.ROPE_SWING,
			BrimhavenArenaObstacle.PLANK,
			BrimhavenArenaObstacle.LOG_BALANCE,
			BrimhavenArenaObstacle.BALANCING_LEDGE,
			BrimhavenArenaObstacle.HAND_HOLDS
		})
		{
			if (BrimhavenObstacleInteraction.modeFor(obstacle) != BrimhavenObstacleInteractionMode.OBJECT)
			{
				throw new AssertionError(obstacle + " should be clicked through its obstacle object");
			}
		}
	}

	private static void assertTrapObstaclesClickPastInsteadOfObject()
	{
		for (BrimhavenArenaObstacle obstacle : new BrimhavenArenaObstacle[] {
			BrimhavenArenaObstacle.BLADE,
			BrimhavenArenaObstacle.SPINNING_BLADES,
			BrimhavenArenaObstacle.DARTS,
			BrimhavenArenaObstacle.FLOOR_SPIKES,
			BrimhavenArenaObstacle.PRESSURE_PAD
		})
		{
			if (BrimhavenObstacleInteraction.modeFor(obstacle) != BrimhavenObstacleInteractionMode.CLICK_PAST)
			{
				throw new AssertionError(obstacle + " should be traversed by clicking past the obstacle");
			}
		}
	}

	private static void assertEveryTraversableObstacleHasInteractionMode()
	{
		for (BrimhavenArenaObstacle obstacle : BrimhavenArenaObstacle.values())
		{
			if (obstacle == BrimhavenArenaObstacle.IMPASSABLE)
			{
				continue;
			}
			if (BrimhavenObstacleInteraction.modeFor(obstacle) == null)
			{
				throw new AssertionError(obstacle + " has no interaction mode");
			}
		}
	}
}
