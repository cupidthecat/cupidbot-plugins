package net.runelite.client.plugins.cupidbot.brimhavenagilitybot;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;

final class BrimhavenTravelRoute
{
	static final int BOAT_FARE_COINS = 30;
	static final int ARENA_ENTRY_COINS = 200;
	static final WorldPoint PORT_SARIM_DOCK_POINT = new WorldPoint(3027, 3218, 0);
	static final WorldPoint MUSA_POINT_DOCK_POINT = new WorldPoint(2955, 3146, 0);
	static final WorldPoint BRIMHAVEN_ENTRANCE_POINT = BrimhavenAgilityBotPlugin.ARENA_ENTRANCE_POINT;
	static final WorldArea PORT_SARIM_DOCK_AREA = new WorldArea(3023, 3213, 12, 12, 0);
	static final WorldArea MUSA_POINT_DOCK_AREA = new WorldArea(2948, 3138, 17, 17, 0);
	static final WorldArea LUMBRIDGE_HOME_AREA = new WorldArea(3200, 3198, 45, 45, 0);

	private static final WorldArea KARAMJA_ROUTE_AREA = new WorldArea(2780, 3120, 220, 126, 0);
	private static final Set<Integer> BOAT_NPC_IDS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
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
	)));

	private BrimhavenTravelRoute()
	{
	}

	static BrimhavenTravelStep nextStep(
		WorldPoint player,
		boolean inArena,
		boolean entryPaid,
		boolean useLumbridgeHomeTeleport,
		int lumbridgeTeleportDistance,
		boolean lumbridgeTeleportAttempted)
	{
		return nextStep(
			player,
			inArena,
			entryPaid,
			useLumbridgeHomeTeleport,
			lumbridgeTeleportDistance,
			lumbridgeTeleportAttempted,
			false);
	}

	static BrimhavenTravelStep nextStep(
		WorldPoint player,
		boolean inArena,
		boolean entryPaid,
		boolean useLumbridgeHomeTeleport,
		int lumbridgeTeleportDistance,
		boolean lumbridgeTeleportAttempted,
		boolean entryCooldownActive)
	{
		if (player == null || inArena)
		{
			return BrimhavenTravelStep.NONE;
		}

		if (isAtArenaEntrance(player))
		{
			if (entryCooldownActive)
			{
				return BrimhavenTravelStep.WAIT_FOR_ENTRY_COOLDOWN;
			}
			return entryPaid ? BrimhavenTravelStep.ENTER_ARENA : BrimhavenTravelStep.PAY_ENTRY;
		}

		if (isOnKaramjaRoute(player))
		{
			return BrimhavenTravelStep.WALK_TO_BRIMHAVEN;
		}

		if (isInPortSarimDockArea(player))
		{
			return BrimhavenTravelStep.TAKE_BOAT_TO_MUSA_POINT;
		}

		if (shouldUseLumbridgeTeleport(player, useLumbridgeHomeTeleport, lumbridgeTeleportDistance, lumbridgeTeleportAttempted))
		{
			return BrimhavenTravelStep.TELEPORT_TO_LUMBRIDGE;
		}

		return BrimhavenTravelStep.WALK_TO_PORT_SARIM;
	}

	static int requiredCoins(int configuredMinimum)
	{
		return Math.max(configuredMinimum, BOAT_FARE_COINS + ARENA_ENTRY_COINS);
	}

	static int requiredCoinsForRemainingRoute(
		WorldPoint player,
		boolean entryPaid,
		int configuredMinimum,
		boolean suppliesPrepared)
	{
		int required = remainingRouteCoins(player, entryPaid);
		if (suppliesPrepared)
		{
			return required;
		}
		return Math.max(configuredMinimum, required);
	}

	private static int remainingRouteCoins(WorldPoint player, boolean entryPaid)
	{
		int required = entryPaid ? 0 : ARENA_ENTRY_COINS;
		if (requiresBoatFare(player))
		{
			required += BOAT_FARE_COINS;
		}
		return required;
	}

	static Set<Integer> boatNpcIds()
	{
		return BOAT_NPC_IDS;
	}

	static boolean isInPortSarimDockArea(WorldPoint point)
	{
		return isInArea(point, PORT_SARIM_DOCK_AREA);
	}

	static boolean isInMusaPointDockArea(WorldPoint point)
	{
		return isInArea(point, MUSA_POINT_DOCK_AREA);
	}

	static boolean isInLumbridgeHomeArea(WorldPoint point)
	{
		return isInArea(point, LUMBRIDGE_HOME_AREA);
	}

	static boolean isAtArenaEntrance(WorldPoint point)
	{
		return isInArea(point, BrimhavenAgilityBotPlugin.ARENA_ENTRY_AREA);
	}

	private static boolean isOnKaramjaRoute(WorldPoint point)
	{
		return isInArea(point, KARAMJA_ROUTE_AREA);
	}

	private static boolean shouldUseLumbridgeTeleport(
		WorldPoint player,
		boolean enabled,
		int teleportDistance,
		boolean alreadyAttempted)
	{
		return enabled
			&& !alreadyAttempted
			&& !isInLumbridgeHomeArea(player)
			&& player.distanceTo(PORT_SARIM_DOCK_POINT) > Math.max(1, teleportDistance);
	}

	private static boolean isInArea(WorldPoint point, WorldArea area)
	{
		return point != null && point.isInArea(area);
	}

	private static boolean requiresBoatFare(WorldPoint point)
	{
		return point == null
			|| (!isOnKaramjaRoute(point) && !isAtArenaEntrance(point));
	}
}
