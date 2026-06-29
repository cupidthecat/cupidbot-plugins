package net.runelite.client.plugins.cupidbot.brimhavenagilitybot;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.cupidbot.util.misc.Rs2Food;

@ConfigGroup("brimhavenagilitybot")
@ConfigInformation(
	"Starts outside or near Brimhaven, restocks coins and food, pays arena entry, then routes to active Brimhaven ticket dispensers.")
public interface BrimhavenAgilityBotConfig extends Config
{
	@ConfigSection(
		name = "Supplies",
		description = "Coins and food used before entering the arena.",
		position = 0,
		closedByDefault = false
	)
	String suppliesSection = "supplies";

	@ConfigSection(
		name = "Travel",
		description = "Startup routing to Port Sarim, Musa Point, and Brimhaven.",
		position = 10,
		closedByDefault = false
	)
	String travelSection = "travel";

	@ConfigSection(
		name = "Arena",
		description = "Arena routing and safety settings.",
		position = 20,
		closedByDefault = false
	)
	String arenaSection = "arena";

	@ConfigSection(
		name = "Avoid Obstacles",
		description = "Weighted pathfinder avoidance preferences.",
		position = 40,
		closedByDefault = true
	)
	String avoidSection = "avoid";

	@ConfigItem(
		keyName = "food",
		name = "Food",
		description = "Food to withdraw from the bank.",
		position = 1,
		section = suppliesSection
	)
	default Rs2Food food()
	{
		return Rs2Food.MONKFISH;
	}

	@ConfigItem(
		keyName = "foodAmount",
		name = "Food amount",
		description = "Food quantity to carry after banking.",
		position = 2,
		section = suppliesSection
	)
	@Range(min = 0, max = 27)
	default int foodAmount()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "eatAtPercent",
		name = "Eat at %",
		description = "Eat when hitpoints are at or below this percentage. 0 disables eating.",
		position = 3,
		section = suppliesSection
	)
	@Range(min = 0, max = 100)
	default int eatAtPercent()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "minCoins",
		name = "Minimum coins",
		description = "Bank before entering if inventory coins are below this amount.",
		position = 4,
		section = suppliesSection
	)
	@Range(min = 200, max = 1_000_000)
	default int minCoins()
	{
		return 200;
	}

	@ConfigItem(
		keyName = "coinsToWithdraw",
		name = "Coins to withdraw",
		description = "Coin stack to withdraw during banking.",
		position = 5,
		section = suppliesSection
	)
	@Range(min = 200, max = 1_000_000)
	default int coinsToWithdraw()
	{
		return 1_000;
	}

	@ConfigItem(
		keyName = "useLumbridgeHomeTeleport",
		name = "Lumbridge fallback",
		description = "Use Lumbridge Home Teleport once when far from the Port Sarim boat route.",
		position = 11,
		section = travelSection
	)
	default boolean useLumbridgeHomeTeleport()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lumbridgeTeleportDistance",
		name = "Teleport distance",
		description = "Teleport to Lumbridge when farther than this many tiles from Port Sarim.",
		position = 12,
		section = travelSection
	)
	@Range(min = 1, max = 500)
	default int lumbridgeTeleportDistance()
	{
		return 180;
	}

	@ConfigItem(
		keyName = "stopWhenNoFood",
		name = "Stop when unsafe",
		description = "Stop if health is low in the arena and no food can be eaten.",
		position = 21,
		section = arenaSection
	)
	default boolean stopWhenNoFood()
	{
		return true;
	}

	@ConfigItem(
		keyName = "drawPath",
		name = "Show status",
		description = "Show the Brimhaven bot status overlay.",
		position = 22,
		section = arenaSection
	)
	default boolean drawPath()
	{
		return true;
	}

	@ConfigItem(
		keyName = "adjustPathForPlanks",
		name = "Solve planks",
		description = "Adjust pathing and clicks around the correct plank row when it is known.",
		position = 23,
		section = arenaSection
	)
	default boolean adjustPathForPlanks()
	{
		return true;
	}

	@ConfigItem(keyName = "bladeAvoid", name = "Avoid blades", description = "Prefer other routes when possible.", position = 41, section = avoidSection)
	default boolean bladeAvoid() { return false; }

	@ConfigItem(keyName = "ropeSwingAvoid", name = "Avoid rope swing", description = "Prefer other routes when possible.", position = 42, section = avoidSection)
	default boolean ropeSwingAvoid() { return false; }

	@ConfigItem(keyName = "lowWallAvoid", name = "Avoid low wall", description = "Prefer other routes when possible.", position = 43, section = avoidSection)
	default boolean lowWallAvoid() { return false; }

	@ConfigItem(keyName = "plankAvoid", name = "Avoid planks", description = "Prefer other routes when possible.", position = 44, section = avoidSection)
	default boolean plankAvoid() { return false; }

	@ConfigItem(keyName = "balancingRopeAvoid", name = "Avoid balancing rope", description = "Prefer other routes when possible.", position = 45, section = avoidSection)
	default boolean balancingRopeAvoid() { return false; }

	@ConfigItem(keyName = "logBalanceAvoid", name = "Avoid log balance", description = "Prefer other routes when possible.", position = 46, section = avoidSection)
	default boolean logBalanceAvoid() { return false; }

	@ConfigItem(keyName = "balancingLedgeAvoid", name = "Avoid ledges", description = "Prefer other routes when possible.", position = 47, section = avoidSection)
	default boolean balancingLedgeAvoid() { return false; }

	@ConfigItem(keyName = "monkeyBarsAvoid", name = "Avoid monkey bars", description = "Prefer other routes when possible.", position = 48, section = avoidSection)
	default boolean monkeyBarsAvoid() { return false; }

	@ConfigItem(keyName = "pillarAvoid", name = "Avoid pillars", description = "Prefer other routes when possible.", position = 49, section = avoidSection)
	default boolean pillarAvoid() { return false; }

	@ConfigItem(keyName = "pressurePadAvoid", name = "Avoid pressure pads", description = "Prefer other routes when possible.", position = 50, section = avoidSection)
	default boolean pressurePadAvoid() { return false; }

	@ConfigItem(keyName = "floorSpikesAvoid", name = "Avoid floor spikes", description = "Prefer other routes when possible.", position = 51, section = avoidSection)
	default boolean floorSpikesAvoid() { return false; }

	@ConfigItem(keyName = "handHoldsAvoid", name = "Avoid hand holds", description = "Prefer other routes when possible.", position = 52, section = avoidSection)
	default boolean handHoldsAvoid() { return false; }

	@ConfigItem(keyName = "spinningBladesAvoid", name = "Avoid spinning blades", description = "Prefer other routes when possible.", position = 53, section = avoidSection)
	default boolean spinningBladesAvoid() { return false; }

	@ConfigItem(keyName = "dartsAvoid", name = "Avoid darts", description = "Prefer other routes when possible.", position = 54, section = avoidSection)
	default boolean dartsAvoid() { return false; }
}
