package net.runelite.client.plugins.cupidbot.brimhavenagilitybot;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ObjectID;

public final class BrimhavenObstacleInteraction
{
	private static final int DISPENSER_OBJECT_ID = ObjectID.AGILITY_TICKETPILLAR;
	private static final List<Integer> DISPENSER_OBJECT_IDS = List.of(
		ObjectID.AGILITY_TICKETPILLAR,
		ObjectID.AGILITYARENA_POISONDARTS);
	private static final List<String> DISPENSER_ACTIONS = List.of("Tag", "Touch");
	private static final Map<BrimhavenArenaObstacle, List<Integer>> IDS_BY_OBSTACLE = new EnumMap<>(BrimhavenArenaObstacle.class);
	private static final Map<BrimhavenArenaObstacle, BrimhavenObstacleInteractionMode> MODE_BY_OBSTACLE = new EnumMap<>(BrimhavenArenaObstacle.class);

	static
	{
		IDS_BY_OBSTACLE.put(BrimhavenArenaObstacle.BALANCING_ROPE, List.of(
			ObjectID.AGILITYARENA_ROPEBALANCE,
			ObjectID.AGILITYARENA_ROPEBALANCE_MIDDLE));
		IDS_BY_OBSTACLE.put(BrimhavenArenaObstacle.LOG_BALANCE, List.of(
			ObjectID.AGILITYARENA_LOGBALANCE1,
			ObjectID.AGILITYARENA_LOGBALANCE1_MIDDLE,
			ObjectID.AGILITYARENA_LOGBALANCE2,
			ObjectID.AGILITYARENA_LOGBALANCE2_MIDDLE,
			ObjectID.AGILITYARENA_LOGBALANCE3,
			ObjectID.AGILITYARENA_LOGBALANCE3_MIDDLE));
		IDS_BY_OBSTACLE.put(BrimhavenArenaObstacle.BALANCING_LEDGE, List.of(
			ObjectID.AGILITYARENA_LEDGEBALANCE,
			ObjectID.AGILITYARENA_LEDGEBALANCE_MIDDLE,
			ObjectID.AGILITYARENA_LEDGEBALANCE2,
			ObjectID.AGILITYARENA_LEDGEBALANCE2_MIDDLE));
		IDS_BY_OBSTACLE.put(BrimhavenArenaObstacle.MONKEY_BARS, List.of(
			ObjectID.AGILITYARENA_MONKEYBARS_MIDDLE,
			ObjectID.AGILITYARENA_MONKEYBARS_END));
		IDS_BY_OBSTACLE.put(BrimhavenArenaObstacle.LOW_WALL, List.of(ObjectID.AGILITYARENA_LOWWALL));
		IDS_BY_OBSTACLE.put(BrimhavenArenaObstacle.ROPE_SWING, List.of(ObjectID.AGILITYARENA_ROPESWING));
		IDS_BY_OBSTACLE.put(BrimhavenArenaObstacle.BLADE, List.of(
			ObjectID.AGILITYARENA_TRAP_TIMEDBLADE2,
			ObjectID.AGILITYARENA_TIMEDBLADE2_FLOOR,
			ObjectID.AGILITYARENA_TIMEDBLADE2_FLOORL));
		IDS_BY_OBSTACLE.put(BrimhavenArenaObstacle.PLANK, List.of(
			ObjectID.AGILITYARENA_PLANK,
			ObjectID.AGILITYARENA_PLANK2,
			ObjectID.AGILITYARENA_PLANK3,
			ObjectID.AGILITYARENA_PLANK_MIDDLE,
			ObjectID.AGILITYARENA_PLANK2_MIDDLE,
			ObjectID.AGILITYARENA_PLANK3_MIDDLE,
			ObjectID.AGILITYARENA_PLANK_BROKE1));
		IDS_BY_OBSTACLE.put(BrimhavenArenaObstacle.PILLAR, List.of(
			ObjectID.AGILITYARENA_PILLAR_TOP,
			ObjectID.AGILITYARENA_PILLAR_TOP_MIDDLE));
		IDS_BY_OBSTACLE.put(BrimhavenArenaObstacle.SPINNING_BLADES, List.of(
			ObjectID.AGILITYARENA_SAWBLADES,
			ObjectID.AGILITYARENA_PILLAR_SAWBLADES));
		IDS_BY_OBSTACLE.put(BrimhavenArenaObstacle.DARTS, List.of(ObjectID.AGILITYARENA_POISONDARTS));
		IDS_BY_OBSTACLE.put(BrimhavenArenaObstacle.FLOOR_SPIKES, List.of(ObjectID.AGILITYARENA_FLOORSPIKES));
		IDS_BY_OBSTACLE.put(BrimhavenArenaObstacle.HAND_HOLDS, List.of(
			ObjectID.AGILITYARENA_HANDHOLDS,
			ObjectID.AGILITYARENA_HANDHOLDS_MIDDLE));
		IDS_BY_OBSTACLE.put(BrimhavenArenaObstacle.PRESSURE_PAD, List.of(ObjectID.AGILITYARENA_PRESSUREPAD));
		IDS_BY_OBSTACLE.put(BrimhavenArenaObstacle.IMPASSABLE, List.of());

		MODE_BY_OBSTACLE.put(BrimhavenArenaObstacle.BALANCING_ROPE, BrimhavenObstacleInteractionMode.OBJECT);
		MODE_BY_OBSTACLE.put(BrimhavenArenaObstacle.LOG_BALANCE, BrimhavenObstacleInteractionMode.OBJECT);
		MODE_BY_OBSTACLE.put(BrimhavenArenaObstacle.BALANCING_LEDGE, BrimhavenObstacleInteractionMode.OBJECT);
		MODE_BY_OBSTACLE.put(BrimhavenArenaObstacle.MONKEY_BARS, BrimhavenObstacleInteractionMode.OBJECT);
		MODE_BY_OBSTACLE.put(BrimhavenArenaObstacle.LOW_WALL, BrimhavenObstacleInteractionMode.OBJECT);
		MODE_BY_OBSTACLE.put(BrimhavenArenaObstacle.ROPE_SWING, BrimhavenObstacleInteractionMode.OBJECT);
		MODE_BY_OBSTACLE.put(BrimhavenArenaObstacle.PLANK, BrimhavenObstacleInteractionMode.OBJECT);
		MODE_BY_OBSTACLE.put(BrimhavenArenaObstacle.PILLAR, BrimhavenObstacleInteractionMode.OBJECT);
		MODE_BY_OBSTACLE.put(BrimhavenArenaObstacle.HAND_HOLDS, BrimhavenObstacleInteractionMode.OBJECT);
		MODE_BY_OBSTACLE.put(BrimhavenArenaObstacle.BLADE, BrimhavenObstacleInteractionMode.CLICK_PAST);
		MODE_BY_OBSTACLE.put(BrimhavenArenaObstacle.SPINNING_BLADES, BrimhavenObstacleInteractionMode.CLICK_PAST);
		MODE_BY_OBSTACLE.put(BrimhavenArenaObstacle.DARTS, BrimhavenObstacleInteractionMode.CLICK_PAST);
		MODE_BY_OBSTACLE.put(BrimhavenArenaObstacle.FLOOR_SPIKES, BrimhavenObstacleInteractionMode.CLICK_PAST);
		MODE_BY_OBSTACLE.put(BrimhavenArenaObstacle.PRESSURE_PAD, BrimhavenObstacleInteractionMode.CLICK_PAST);
	}

	private BrimhavenObstacleInteraction()
	{
	}

	public static List<Integer> idsFor(BrimhavenArenaObstacle obstacle)
	{
		return IDS_BY_OBSTACLE.getOrDefault(obstacle, List.of());
	}

	public static BrimhavenObstacleInteractionMode modeFor(BrimhavenArenaObstacle obstacle)
	{
		return MODE_BY_OBSTACLE.get(obstacle);
	}

	public static int getDispenserObjectId()
	{
		return DISPENSER_OBJECT_ID;
	}

	public static List<Integer> dispenserObjectIds()
	{
		return DISPENSER_OBJECT_IDS;
	}

	public static List<String> dispenserActions()
	{
		return DISPENSER_ACTIONS;
	}
}
