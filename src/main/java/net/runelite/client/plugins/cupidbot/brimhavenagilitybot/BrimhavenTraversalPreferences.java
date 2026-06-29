package net.runelite.client.plugins.cupidbot.brimhavenagilitybot;

import java.util.EnumSet;
import java.util.Set;

public final class BrimhavenTraversalPreferences
{
	private final Set<BrimhavenArenaObstacle> avoidedObstacles;

	private BrimhavenTraversalPreferences(Set<BrimhavenArenaObstacle> avoidedObstacles)
	{
		this.avoidedObstacles = EnumSet.copyOf(avoidedObstacles);
	}

	public static BrimhavenTraversalPreferences none()
	{
		return new BrimhavenTraversalPreferences(EnumSet.noneOf(BrimhavenArenaObstacle.class));
	}

	public static BrimhavenTraversalPreferences fromConfig(BrimhavenAgilityBotConfig config)
	{
		EnumSet<BrimhavenArenaObstacle> avoided = EnumSet.noneOf(BrimhavenArenaObstacle.class);
		addIf(avoided, BrimhavenArenaObstacle.BLADE, config.bladeAvoid());
		addIf(avoided, BrimhavenArenaObstacle.ROPE_SWING, config.ropeSwingAvoid());
		addIf(avoided, BrimhavenArenaObstacle.LOW_WALL, config.lowWallAvoid());
		addIf(avoided, BrimhavenArenaObstacle.PLANK, config.plankAvoid());
		addIf(avoided, BrimhavenArenaObstacle.BALANCING_ROPE, config.balancingRopeAvoid());
		addIf(avoided, BrimhavenArenaObstacle.LOG_BALANCE, config.logBalanceAvoid());
		addIf(avoided, BrimhavenArenaObstacle.BALANCING_LEDGE, config.balancingLedgeAvoid());
		addIf(avoided, BrimhavenArenaObstacle.MONKEY_BARS, config.monkeyBarsAvoid());
		addIf(avoided, BrimhavenArenaObstacle.PILLAR, config.pillarAvoid());
		addIf(avoided, BrimhavenArenaObstacle.PRESSURE_PAD, config.pressurePadAvoid());
		addIf(avoided, BrimhavenArenaObstacle.FLOOR_SPIKES, config.floorSpikesAvoid());
		addIf(avoided, BrimhavenArenaObstacle.HAND_HOLDS, config.handHoldsAvoid());
		addIf(avoided, BrimhavenArenaObstacle.SPINNING_BLADES, config.spinningBladesAvoid());
		addIf(avoided, BrimhavenArenaObstacle.DARTS, config.dartsAvoid());
		return new BrimhavenTraversalPreferences(avoided);
	}

	private static void addIf(Set<BrimhavenArenaObstacle> avoided, BrimhavenArenaObstacle obstacle, boolean avoid)
	{
		if (avoid)
		{
			avoided.add(obstacle);
		}
	}

	public boolean shouldAvoid(BrimhavenArenaObstacle obstacle)
	{
		return avoidedObstacles.contains(obstacle);
	}
}
