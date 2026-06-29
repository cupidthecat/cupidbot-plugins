package net.runelite.client.plugins.cupidbot.brimhavenagilitybot;

import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;

public class BrimhavenAgilityBotTravelTest
{
	public static void main(String[] args)
	{
		assertFarAwayPlayerUsesLumbridgeTeleportOnce();
		assertNearbyPortSarimPlayerUsesBoat();
		assertMusaPointPlayerWalksToBrimhaven();
		assertBrimhavenEntrancePlayerPaysThenEnters();
		assertBrimhavenEntrancePlayerWaitsForCooldown();
		assertRequiredCoinsCoverBoatAndArenaEntry();
		assertRemainingCoinRequirementDropsAfterBoatAndEntry();
		assertSupplyCheckDoesNotRestockUsableFoodStacks();
		assertSupplyCheckUsesStackQuantities();
		assertMissingPlayerLocationBlocksUnsafeBankWalk();
		assertBoatNpcIdsCoverLegacyAndMenuSwapVariants();
		assertBoatActionPriorityUsesDirectMusaPointAction();
		assertBoatActionMatcherHandlesNullMenuSlots();
		assertTravelLoopRunsOftenEnoughToAvoidCheckpointPauses();
	}

	private static void assertFarAwayPlayerUsesLumbridgeTeleportOnce()
	{
		WorldPoint grandExchange = new WorldPoint(3164, 3487, 0);
		BrimhavenTravelStep firstStep = BrimhavenTravelRoute.nextStep(
			grandExchange,
			false,
			false,
			true,
			180,
			false);
		if (firstStep != BrimhavenTravelStep.TELEPORT_TO_LUMBRIDGE)
		{
			throw new AssertionError("Far-away player should teleport to Lumbridge before walking to Port Sarim");
		}

		BrimhavenTravelStep secondStep = BrimhavenTravelRoute.nextStep(
			grandExchange,
			false,
			false,
			true,
			180,
			true);
		if (secondStep != BrimhavenTravelStep.WALK_TO_PORT_SARIM)
		{
			throw new AssertionError("Teleport should be attempted once, then fall back to walking");
		}
	}

	private static void assertNearbyPortSarimPlayerUsesBoat()
	{
		WorldPoint portSarimDock = new WorldPoint(3027, 3218, 0);
		BrimhavenTravelStep step = BrimhavenTravelRoute.nextStep(
			portSarimDock,
			false,
			false,
			true,
			180,
			false);
		if (step != BrimhavenTravelStep.TAKE_BOAT_TO_MUSA_POINT)
		{
			throw new AssertionError("Port Sarim dock player should take the Musa Point boat");
		}
	}

	private static void assertMusaPointPlayerWalksToBrimhaven()
	{
		WorldPoint musaPointDock = new WorldPoint(2955, 3146, 0);
		BrimhavenTravelStep step = BrimhavenTravelRoute.nextStep(
			musaPointDock,
			false,
			false,
			true,
			180,
			false);
		if (step != BrimhavenTravelStep.WALK_TO_BRIMHAVEN)
		{
			throw new AssertionError("Musa Point player should walk to Brimhaven entrance");
		}
	}

	private static void assertBrimhavenEntrancePlayerPaysThenEnters()
	{
		WorldPoint entrance = BrimhavenAgilityBotPlugin.ARENA_ENTRANCE_POINT;
		BrimhavenTravelStep unpaid = BrimhavenTravelRoute.nextStep(
			entrance,
			false,
			false,
			true,
			180,
			false);
		if (unpaid != BrimhavenTravelStep.PAY_ENTRY)
		{
			throw new AssertionError("Unpaid player at entrance should pay the agility clerk");
		}

		BrimhavenTravelStep paid = BrimhavenTravelRoute.nextStep(
			entrance,
			false,
			true,
			true,
			180,
			false);
		if (paid != BrimhavenTravelStep.ENTER_ARENA)
		{
			throw new AssertionError("Paid player at entrance should climb into the arena");
		}
	}

	private static void assertBrimhavenEntrancePlayerWaitsForCooldown()
	{
		WorldPoint entrance = BrimhavenAgilityBotPlugin.ARENA_ENTRANCE_POINT;
		BrimhavenTravelStep paidOnCooldown = BrimhavenTravelRoute.nextStep(
			entrance,
			false,
			true,
			true,
			180,
			false,
			true);
		if (paidOnCooldown != BrimhavenTravelStep.WAIT_FOR_ENTRY_COOLDOWN)
		{
			throw new AssertionError("Paid player at Brimhaven entrance should wait while arena re-entry cooldown is active");
		}

		BrimhavenTravelStep unpaidOnCooldown = BrimhavenTravelRoute.nextStep(
			entrance,
			false,
			false,
			true,
			180,
			false,
			true);
		if (unpaidOnCooldown != BrimhavenTravelStep.WAIT_FOR_ENTRY_COOLDOWN)
		{
			throw new AssertionError("Unpaid player at Brimhaven entrance should not spam the clerk while cooldown is active");
		}
	}

	private static void assertRequiredCoinsCoverBoatAndArenaEntry()
	{
		int required = BrimhavenTravelRoute.requiredCoins(200);
		int minimumTravelCost = BrimhavenTravelRoute.BOAT_FARE_COINS + BrimhavenTravelRoute.ARENA_ENTRY_COINS;
		if (required < minimumTravelCost)
		{
			throw new AssertionError("Required coins must include boat fare plus arena entry");
		}
		if (BrimhavenTravelRoute.requiredCoins(1_000) != 1_000)
		{
			throw new AssertionError("Configured higher coin minimum should be preserved");
		}
	}

	private static void assertRemainingCoinRequirementDropsAfterBoatAndEntry()
	{
		int beforeBoat = BrimhavenTravelRoute.requiredCoinsForRemainingRoute(
			BrimhavenTravelRoute.PORT_SARIM_DOCK_POINT,
			false,
			200,
			false);
		if (beforeBoat != BrimhavenTravelRoute.BOAT_FARE_COINS + BrimhavenTravelRoute.ARENA_ENTRY_COINS)
		{
			throw new AssertionError("Before taking the boat, banking should require fare plus arena entry");
		}

		int afterBoat = BrimhavenTravelRoute.requiredCoinsForRemainingRoute(
			BrimhavenTravelRoute.MUSA_POINT_DOCK_POINT,
			false,
			200,
			true);
		if (afterBoat != BrimhavenTravelRoute.ARENA_ENTRY_COINS)
		{
			throw new AssertionError("After paying the boat fare, banking should only require arena entry");
		}

		int afterEntry = BrimhavenTravelRoute.requiredCoinsForRemainingRoute(
			BrimhavenAgilityBotPlugin.ARENA_ENTRANCE_POINT,
			true,
			200,
			true);
		if (afterEntry != 0)
		{
			throw new AssertionError("After paying arena entry, banking should not require more coins before climbing down");
		}
	}

	private static void assertSupplyCheckDoesNotRestockUsableFoodStacks()
	{
		if (BrimhavenAgilityBotScript.shouldBankForSupplies(230, 230, 1, 10))
		{
			throw new AssertionError("A usable food stack should not force a pre-arena banking loop");
		}
		if (BrimhavenAgilityBotScript.shouldBankForSupplies(200, 200, 9, 10))
		{
			throw new AssertionError("Spending travel coins or one food should not force restocking when remaining route costs are covered");
		}
		if (!BrimhavenAgilityBotScript.shouldBankForSupplies(200, 230, 10, 10))
		{
			throw new AssertionError("Missing coins for the remaining route should still bank");
		}
		if (!BrimhavenAgilityBotScript.shouldBankForSupplies(230, 230, 0, 10))
		{
			throw new AssertionError("Configured food with none in inventory should still bank");
		}
	}

	private static void assertSupplyCheckUsesStackQuantities()
	{
		if (BrimhavenAgilityBotScript.shouldBankForInventorySupplySnapshot(1, 230, 230, 1, 10))
		{
			throw new AssertionError("A single coin stack with enough quantity should not be treated as one coin");
		}
		if (!BrimhavenAgilityBotScript.shouldBankForInventorySupplySnapshot(1, 229, 230, 1, 10))
		{
			throw new AssertionError("Coin quantity below the remaining route cost should still bank");
		}
	}

	private static void assertMissingPlayerLocationBlocksUnsafeBankWalk()
	{
		if (!BrimhavenAgilityBotScript.shouldDelayForMissingPlayerLocation(null))
		{
			throw new AssertionError("Missing player location should delay bank walking to avoid client-helper null crashes");
		}
		if (BrimhavenAgilityBotScript.shouldDelayForMissingPlayerLocation(BrimhavenTravelRoute.PORT_SARIM_DOCK_POINT))
		{
			throw new AssertionError("Known player location should not delay normal travel or banking");
		}
	}

	private static void assertBoatNpcIdsCoverLegacyAndMenuSwapVariants()
	{
		Set<Integer> boatNpcIds = BrimhavenTravelRoute.boatNpcIds();
		for (int id : new int[] {
			NpcID.SEAMAN_LORRIS,
			NpcID.SEAMAN_THRESNOR,
			NpcID.CUSTOMS_OFFICER,
			NpcID.SEAMAN_MORRIS,
			NpcID.SEAMAN_LORRIS_1OP,
			NpcID.SEAMAN_LORRIS_2OP,
			NpcID.SEAMAN_THRESNOR_1OP,
			NpcID.SEAMAN_THRESNOR_2OP,
			NpcID.CUSTOMS_OFFICER_1OP,
			NpcID.CUSTOMS_OFFICER_2OP
		})
		{
			if (!boatNpcIds.contains(id))
			{
				throw new AssertionError("Boat NPC ID set is missing " + id);
			}
		}
	}

	private static void assertBoatActionPriorityUsesDirectMusaPointAction()
	{
		if (!"Musa Point".equals(BrimhavenAgilityBotScript.boatTravelActions().get(0)))
		{
			throw new AssertionError("Boat travel should prefer the direct Musa Point action over noisy fare/talk probes");
		}
	}

	private static void assertBoatActionMatcherHandlesNullMenuSlots()
	{
		String[] actions = {"Talk-to", null, "Musa Point", "The Pandemonium"};
		if (!BrimhavenAgilityBotScript.hasMenuAction(actions, "Musa Point"))
		{
			throw new AssertionError("Boat action matcher should find Musa Point through null action slots");
		}
		if (BrimhavenAgilityBotScript.hasMenuAction(actions, "Pay-fare"))
		{
			throw new AssertionError("Boat action matcher should not report unsupported actions");
		}
	}

	private static void assertTravelLoopRunsOftenEnoughToAvoidCheckpointPauses()
	{
		if (BrimhavenAgilityBotScript.loopDelayMillis() > 300)
		{
			throw new AssertionError("Brimhaven travel should ask the walker for the next checkpoint without a long scheduler pause");
		}
	}
}
