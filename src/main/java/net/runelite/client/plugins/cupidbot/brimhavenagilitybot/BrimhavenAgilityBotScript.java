package net.runelite.client.plugins.cupidbot.brimhavenagilitybot;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPCComposition;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.cupidbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.cupidbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.cupidbot.util.misc.Rs2Food;
import net.runelite.client.plugins.cupidbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.cupidbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

@Slf4j
public class BrimhavenAgilityBotScript extends Script
{
	private static final int LOOP_DELAY_MILLIS = 300;
	private static final int OBJECT_SEARCH_DISTANCE = 20;
	private static final int PLATFORM_OBJECT_MARGIN = 8;
	private static final int DISPENSER_SEARCH_RADIUS = 8;
	private static final int BOAT_NPC_SEARCH_RADIUS = 12;
	static final int GAME_TICK_MILLIS = 600;
	private static final int OBSTACLE_START_WAIT_MILLIS = 2_000;
	static final int OBSTACLE_STABLE_IDLE_MILLIS = 900;
	private static final int OBSTACLE_TIMEOUT_HEADROOM_MILLIS = 6_000;
	private static final int OBSTACLE_RETRY_DELAY_MILLIS = 700;
	private static final int OBSTACLE_RETRY_LIMIT = 2;
	private static final int ENTRY_WAIT_MILLIS = 6_000;
	private static final int ENTRY_COOLDOWN_WAIT_MILLIS = 60_000;
	private static final int TELEPORT_WAIT_MILLIS = 12_000;
	private static final int BOAT_WAIT_MILLIS = 8_000;
	private static final List<String> BOAT_TRAVEL_ACTIONS = List.of("Musa Point", "Pay-fare", "Talk-to", "Travel");

	private final BrimhavenAgilityBotPlugin plugin;
	private boolean lumbridgeTeleportAttempted;
	private boolean suppliesPrepared;
	private long entryCooldownUntilMillis;

	@Inject
	public BrimhavenAgilityBotScript(BrimhavenAgilityBotPlugin plugin)
	{
		this.plugin = plugin;
	}

	public boolean run(BrimhavenAgilityBotConfig config)
	{
		plugin.setState(BrimhavenBotState.STARTING);
		plugin.setCurrentPath(null);
		plugin.setRewardCount(rewardCount());
		lumbridgeTeleportAttempted = false;
		suppliesPrepared = false;
		entryCooldownUntilMillis = 0L;

		mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
			try
			{
				if (!super.run())
				{
					return;
				}
				if (!CupidBot.isLoggedIn())
				{
					return;
				}
				tick(config);
			}
			catch (RuntimeException ex)
			{
				if (Script.isInterruption(ex))
				{
					log.debug("[BrimhavenAgilityBot] loop interrupted during shutdown");
					return;
				}
				log.warn("[BrimhavenAgilityBot] loop failed", ex);
			}
		}, 0, LOOP_DELAY_MILLIS, TimeUnit.MILLISECONDS);
		return true;
	}

	static int loopDelayMillis()
	{
		return LOOP_DELAY_MILLIS;
	}

	private void tick(BrimhavenAgilityBotConfig config)
	{
		plugin.refreshStateFromClient();
		WorldPoint player = Rs2Player.getWorldLocation();
		if (shouldDelayForMissingPlayerLocation(player))
		{
			plugin.setState(BrimhavenBotState.STARTING);
			return;
		}
		plugin.setRewardCount(rewardCount());

		if (handleEating(config))
		{
			return;
		}

		boolean inArena = isInArena(player);
		if (!inArena && needsSupplies(config, player))
		{
			bankForSupplies(config);
			return;
		}

		if (!inArena)
		{
			reachAndEnterArena(config, player);
			return;
		}

		runArena(config);
	}

	private boolean handleEating(BrimhavenAgilityBotConfig config)
	{
		if (config.eatAtPercent() <= 0 || Rs2Player.getHealthPercentage() > config.eatAtPercent())
		{
			return false;
		}

		plugin.setState(BrimhavenBotState.EATING);
		if (Rs2Player.eatAt(config.eatAtPercent()))
		{
			sleep(600, 900);
			return true;
		}

		if (isInArena() && config.stopWhenNoFood())
		{
			log.warn("[BrimhavenAgilityBot] unsafe health and no food available; stopping");
			plugin.setState(BrimhavenBotState.STOPPED);
			shutdown();
			return true;
		}
		return false;
	}

	private boolean needsSupplies(BrimhavenAgilityBotConfig config, WorldPoint player)
	{
		int requiredCoins = BrimhavenTravelRoute.requiredCoinsForRemainingRoute(
			player,
			plugin.isEntryPaid(),
			config.minCoins(),
			suppliesPrepared);
		boolean shouldBank = shouldBankForSupplies(
			Rs2Inventory.itemQuantity(ItemID.COINS),
			requiredCoins,
			Rs2Inventory.itemQuantity(config.food().getId()),
			config.foodAmount());
		if (!shouldBank)
		{
			suppliesPrepared = true;
		}
		return shouldBank;
	}

	static boolean shouldDelayForMissingPlayerLocation(WorldPoint player)
	{
		return player == null;
	}

	static boolean shouldBankForSupplies(int coins, int requiredCoins, int foodCount, int desiredFoodAmount)
	{
		if (coins < Math.max(0, requiredCoins))
		{
			return true;
		}
		return desiredFoodAmount > 0 && foodCount <= 0;
	}

	static boolean shouldBankForInventorySupplySnapshot(
		int coinStacks,
		int coinQuantity,
		int requiredCoins,
		int foodQuantity,
		int desiredFoodAmount)
	{
		return shouldBankForSupplies(coinQuantity, requiredCoins, foodQuantity, desiredFoodAmount);
	}

	private void bankForSupplies(BrimhavenAgilityBotConfig config)
	{
		plugin.setState(BrimhavenBotState.BANKING);
		if (!Rs2Bank.isOpen())
		{
			if (shouldDelayForMissingPlayerLocation(Rs2Player.getWorldLocation()))
			{
				return;
			}
			Rs2Bank.walkToBankAndUseBank();
			return;
		}

		int foodId = config.food().getId();
		Set<Integer> retainedItemIds = BrimhavenAgilityRewards.bankRetainedItemIds(foodId);
		Rs2Bank.depositAllExcept(retainedItemIds, java.util.Map.of());

		int coinsToWithdraw = Math.max(config.coinsToWithdraw(), BrimhavenTravelRoute.requiredCoins(config.minCoins()));
		if (!Rs2Bank.hasBankItem(ItemID.COINS, coinsToWithdraw))
		{
			log.warn("[BrimhavenAgilityBot] missing coins in bank");
			plugin.setState(BrimhavenBotState.STOPPED);
			shutdown();
			return;
		}

		if (!Rs2Bank.withdrawX(true, ItemID.COINS, coinsToWithdraw))
		{
			return;
		}

		if (config.foodAmount() > 0)
		{
			if (!Rs2Bank.hasBankItem(foodId, Math.max(1, config.foodAmount())))
			{
				log.warn("[BrimhavenAgilityBot] missing configured food in bank: {}", config.food());
				plugin.setState(BrimhavenBotState.STOPPED);
				shutdown();
				return;
			}
			Rs2Bank.withdrawX(true, foodId, config.foodAmount());
		}

		Rs2Bank.closeBank();
	}

	private void reachAndEnterArena(BrimhavenAgilityBotConfig config, WorldPoint player)
	{
		if (player == null)
		{
			return;
		}

		if (lumbridgeTeleportAttempted
			&& !BrimhavenTravelRoute.isInLumbridgeHomeArea(player)
				&& Rs2Player.isAnimating())
		{
			plugin.setState(BrimhavenBotState.TELEPORTING_TO_LUMBRIDGE);
			return;
		}

		long now = System.currentTimeMillis();
		entryCooldownUntilMillis = updateEntryCooldownUntilMillis(
			plugin.isCooldownRequired(),
			now,
			entryCooldownUntilMillis);
		boolean entryCooldownActive = shouldWaitForEntryCooldown(
			plugin.isCooldownRequired(),
			now,
			entryCooldownUntilMillis);
		if (!entryCooldownActive)
		{
			entryCooldownUntilMillis = 0L;
		}

		BrimhavenTravelStep step = BrimhavenTravelRoute.nextStep(
			player,
			false,
			plugin.isEntryPaid(),
			config.useLumbridgeHomeTeleport(),
			config.lumbridgeTeleportDistance(),
			lumbridgeTeleportAttempted,
			entryCooldownActive);

		switch (step)
		{
			case TELEPORT_TO_LUMBRIDGE:
				teleportToLumbridge();
				return;
			case WALK_TO_PORT_SARIM:
				plugin.setState(BrimhavenBotState.WALKING_TO_PORT_SARIM);
				Rs2Walker.walkTo(BrimhavenTravelRoute.PORT_SARIM_DOCK_POINT, 4);
				return;
			case TAKE_BOAT_TO_MUSA_POINT:
				takeBoatToMusaPoint();
				return;
			case WALK_TO_BRIMHAVEN:
				plugin.setState(BrimhavenBotState.WALKING_TO_BRIMHAVEN);
				if (!handleBoatDialogue())
				{
					Rs2Walker.walkTo(BrimhavenTravelRoute.BRIMHAVEN_ENTRANCE_POINT, 4);
				}
				return;
			case WAIT_FOR_ENTRY_COOLDOWN:
				plugin.setState(BrimhavenBotState.WAITING_FOR_ENTRY_COOLDOWN);
				plugin.setCurrentPath(null);
				sleep(500, 900);
				return;
			case PAY_ENTRY:
				plugin.setState(BrimhavenBotState.PAYING_ENTRY);
				if (Rs2Npc.interact(NpcID.AGILITYARENA_CLERK, "Pay"))
				{
					sleepUntil(plugin::isEntryPaid, ENTRY_WAIT_MILLIS);
				}
				return;
			case ENTER_ARENA:
				plugin.setState(BrimhavenBotState.ENTERING_ARENA);
				if (Rs2GameObject.interact(ObjectID.AGILITYARENA_LADDERDOWN, "Climb-down"))
				{
					sleepUntil(this::isInArena, ENTRY_WAIT_MILLIS);
				}
				return;
			case NONE:
			default:
				return;
		}
	}

	static long updateEntryCooldownUntilMillis(boolean cooldownRequired, long nowMillis, long currentCooldownUntilMillis)
	{
		if (!cooldownRequired)
		{
			return currentCooldownUntilMillis;
		}
		if (currentCooldownUntilMillis > nowMillis)
		{
			return currentCooldownUntilMillis;
		}
		return nowMillis + ENTRY_COOLDOWN_WAIT_MILLIS;
	}

	static boolean shouldWaitForEntryCooldown(boolean cooldownRequired, long nowMillis, long cooldownUntilMillis)
	{
		return cooldownRequired || nowMillis < cooldownUntilMillis;
	}

	private void teleportToLumbridge()
	{
		plugin.setState(BrimhavenBotState.TELEPORTING_TO_LUMBRIDGE);
		lumbridgeTeleportAttempted = true;
		if (Rs2Player.isAnimating())
		{
			return;
		}

		if (Rs2Magic.cast(MagicAction.LUMBRIDGE_HOME_TELEPORT))
		{
			sleepUntil(() -> {
				WorldPoint now = Rs2Player.getWorldLocation();
				return BrimhavenTravelRoute.isInLumbridgeHomeArea(now) || !Rs2Player.isAnimating();
			}, TELEPORT_WAIT_MILLIS);
		}
	}

	private void takeBoatToMusaPoint()
	{
		plugin.setState(BrimhavenBotState.TAKING_BOAT_TO_MUSA_POINT);
		if (handleBoatDialogue())
		{
			sleepUntil(() -> BrimhavenTravelRoute.isInMusaPointDockArea(Rs2Player.getWorldLocation()), BOAT_WAIT_MILLIS);
			return;
		}

		WorldPoint player = Rs2Player.getWorldLocation();
		if (player == null)
		{
			return;
		}
		if (!BrimhavenTravelRoute.isInPortSarimDockArea(player))
		{
			Rs2Walker.walkTo(BrimhavenTravelRoute.PORT_SARIM_DOCK_POINT, 4);
			return;
		}

		for (String action : boatTravelActions())
		{
			Rs2NpcModel boatNpc = nearestBoatNpcForAction(action);
			if (boatNpc != null && Rs2Npc.interact(boatNpc, action))
			{
				sleepUntil(() -> BrimhavenTravelRoute.isInMusaPointDockArea(Rs2Player.getWorldLocation())
					|| Rs2Dialogue.isInDialogue(), BOAT_WAIT_MILLIS);
				return;
			}
		}
	}

	static List<String> boatTravelActions()
	{
		return BOAT_TRAVEL_ACTIONS;
	}

	static boolean hasMenuAction(String[] actions, String action)
	{
		if (actions == null || action == null || action.isBlank())
		{
			return false;
		}
		for (String candidate : actions)
		{
			if (candidate != null && candidate.equalsIgnoreCase(action))
			{
				return true;
			}
		}
		return false;
	}

	private Rs2NpcModel nearestBoatNpcForAction(String action)
	{
		WorldPoint player = Rs2Player.getWorldLocation();
		if (player == null)
		{
			return null;
		}
		return Rs2Npc.getNpcs(npc -> BrimhavenTravelRoute.boatNpcIds().contains(npc.getId())
				&& npc.getWorldLocation() != null
				&& npc.getWorldLocation().distanceTo(player) <= BOAT_NPC_SEARCH_RADIUS
				&& npcHasAction(npc, action))
			.min(Comparator.comparingInt(npc -> npc.getWorldLocation().distanceTo(player)))
			.orElse(null);
	}

	private static boolean npcHasAction(Rs2NpcModel npc, String action)
	{
		if (npc == null)
		{
			return false;
		}
		return compositionHasAction(npc.getComposition(), action)
			|| compositionHasAction(npc.getTransformedComposition(), action);
	}

	private static boolean compositionHasAction(NPCComposition composition, String action)
	{
		return composition != null && hasMenuAction(composition.getActions(), action);
	}

	private boolean handleBoatDialogue()
	{
		if (!Rs2Dialogue.isInDialogue())
		{
			return false;
		}

		if (Rs2Dialogue.hasContinue())
		{
			Rs2Dialogue.clickContinue();
			sleep(300, 600);
			return true;
		}

		if (Rs2Dialogue.hasSelectAnOption())
		{
			boolean clicked = Rs2Dialogue.clickOption(
				"Yes please",
				"Yes",
				"Karamja",
				"Musa Point",
				"pay 30",
				"Okay");
			if (clicked)
			{
				sleep(300, 600);
			}
			return clicked;
		}

		return false;
	}

	private void runArena(BrimhavenAgilityBotConfig config)
	{
		if (handleArenaContinueDialogue())
		{
			return;
		}

		if (!plugin.isTicketAvailable())
		{
			plugin.setCurrentPath(null);
			plugin.setState(BrimhavenBotState.WAITING_FOR_DISPENSER);
			return;
		}

		WorldPoint player = Rs2Player.getWorldLocation();
		WorldPoint dispenser = getHintArrowPoint();
		BrimhavenArenaLocation playerLocation = BrimhavenArenaLocation.fromWorldPoint(player);
		BrimhavenArenaLocation dispenserLocation = BrimhavenArenaLocation.fromWorldPoint(dispenser);
		if (playerLocation == null || dispenserLocation == null)
		{
			plugin.setState(BrimhavenBotState.WAITING_FOR_DISPENSER);
			return;
		}
		if (Rs2Player.isAnimating() || Rs2Player.isMoving())
		{
			plugin.setState(BrimhavenBotState.MOVING_TO_DISPENSER);
			return;
		}

		if (playerLocation.equals(dispenserLocation))
		{
			tagDispenser(dispenser);
			return;
		}

		BrimhavenArenaPath path = currentOrNewPath(player, dispenser, config);
		if (path == null || path.size() < 2)
		{
			plugin.setCurrentPath(null);
			plugin.setState(BrimhavenBotState.WAITING_FOR_DISPENSER);
			return;
		}

		BrimhavenArenaLocation nextLocation = path.getLocations().get(1);
		BrimhavenArenaObstacle obstacle = BrimhavenArenaGraph.obstacleBetween(playerLocation, nextLocation);
		if (obstacle == null || obstacle == BrimhavenArenaObstacle.IMPASSABLE)
		{
			plugin.setCurrentPath(null);
			return;
		}

		plugin.setState(BrimhavenBotState.MOVING_TO_DISPENSER);
		if (traverseObstacle(obstacle, playerLocation, nextLocation, config))
		{
			plugin.setCurrentPath(null);
		}
	}

	static boolean shouldAdvanceArenaContinueDialogue(boolean ticketAvailable, boolean inDialogue, boolean hasContinue)
	{
		return inDialogue && hasContinue;
	}

	private boolean handleArenaContinueDialogue()
	{
		boolean inDialogue = Rs2Dialogue.isInDialogue();
		boolean hasContinue = inDialogue && Rs2Dialogue.hasContinue();
		if (!shouldAdvanceArenaContinueDialogue(plugin.isTicketAvailable(), inDialogue, hasContinue))
		{
			return false;
		}

		plugin.setState(BrimhavenBotState.TAGGING_DISPENSER);
		Rs2Dialogue.clickContinue();
		sleep(300, 600);
		sleepUntil(() -> !Rs2Dialogue.isInDialogue() || !Rs2Dialogue.hasContinue(), 1_500);
		plugin.setRewardCount(rewardCount());
		return true;
	}

	private boolean traverseObstacle(
		BrimhavenArenaObstacle obstacle,
		BrimhavenArenaLocation playerLocation,
		BrimhavenArenaLocation nextLocation,
		BrimhavenAgilityBotConfig config)
	{
		for (int attempt = 0; attempt <= OBSTACLE_RETRY_LIMIT; attempt++)
		{
			boolean attempted = attemptObstacle(obstacle, playerLocation, nextLocation, config);
			boolean crossed = attempted && waitForObstacleCrossing(obstacle, nextLocation);
			if (crossed)
			{
				return true;
			}
			if (BrimhavenObstacleInteraction.modeFor(obstacle) == BrimhavenObstacleInteractionMode.OBJECT
				&& shouldClickPastObstacleAfterObjectAttempt(attempted, crossed))
			{
				boolean fallbackClicked = clickPastObstacle(nextLocation);
				crossed = fallbackClicked && waitForObstacleCrossing(obstacle, nextLocation);
				if (crossed)
				{
					return true;
				}
			}
			if (!shouldRetryObstacleAttempt(attempted, crossed, attempt, OBSTACLE_RETRY_LIMIT))
			{
				return false;
			}

			log.info("[BrimhavenAgilityBot] obstacle {} did not reach {}; retry {}/{}",
				obstacle, nextLocation, attempt + 1, OBSTACLE_RETRY_LIMIT);
			sleep(OBSTACLE_RETRY_DELAY_MILLIS, OBSTACLE_RETRY_DELAY_MILLIS + 300);
		}
		return false;
	}

	private boolean attemptObstacle(
		BrimhavenArenaObstacle obstacle,
		BrimhavenArenaLocation playerLocation,
		BrimhavenArenaLocation nextLocation,
		BrimhavenAgilityBotConfig config)
	{
		if (BrimhavenObstacleInteraction.modeFor(obstacle) == BrimhavenObstacleInteractionMode.CLICK_PAST)
		{
			return clickPastObstacle(nextLocation);
		}

		TileObject obstacleObject = findObstacleObject(obstacle, playerLocation, nextLocation, config);
		return interactWithObject(obstacleObject, "");
	}

	static boolean shouldClickPastObstacleAfterObjectAttempt(boolean objectClicked, boolean progressed)
	{
		return objectClicked && !progressed;
	}

	static boolean hasCrossedObstacle(WorldPoint current, BrimhavenArenaLocation nextLocation)
	{
		return current != null
			&& nextLocation != null
			&& nextLocation.equals(BrimhavenArenaLocation.fromWorldPoint(current));
	}

	static boolean shouldRetryObstacleAttempt(boolean attempted, boolean crossed, int attempt, int maxAttempts)
	{
		return attempted && !crossed && attempt < maxAttempts;
	}

	static int obstacleTraversalMinimumMillis(BrimhavenArenaObstacle obstacle)
	{
		if (obstacle == null || obstacle == BrimhavenArenaObstacle.IMPASSABLE)
		{
			return GAME_TICK_MILLIS;
		}
		return obstacle.getTraversalTicks() * GAME_TICK_MILLIS;
	}

	static int obstacleTraversalTimeoutMillis(BrimhavenArenaObstacle obstacle)
	{
		return obstacleTraversalMinimumMillis(obstacle) + OBSTACLE_TIMEOUT_HEADROOM_MILLIS;
	}

	static boolean isObstacleTraversalSettled(
		boolean crossed,
		boolean playerBusy,
		long elapsedMillis,
		long idleMillis,
		int minimumMillis)
	{
		return crossed
			&& !playerBusy
			&& elapsedMillis >= minimumMillis
			&& idleMillis >= OBSTACLE_STABLE_IDLE_MILLIS;
	}

	private boolean waitForObstacleCrossing(BrimhavenArenaObstacle obstacle, BrimhavenArenaLocation nextLocation)
	{
		int minimumMillis = obstacleTraversalMinimumMillis(obstacle);
		int timeoutMillis = obstacleTraversalTimeoutMillis(obstacle);
		long startedAt = System.currentTimeMillis();
		long lastBusyAt = startedAt;

		boolean started = sleepUntil(() -> hasReachedNextPlatform(nextLocation) || isPlayerBusy(), OBSTACLE_START_WAIT_MILLIS);
		if (!started)
		{
			return false;
		}

		while (System.currentTimeMillis() - startedAt < timeoutMillis)
		{
			long now = System.currentTimeMillis();
			boolean playerBusy = isPlayerBusy();
			if (playerBusy)
			{
				lastBusyAt = now;
			}

			boolean crossed = hasReachedNextPlatform(nextLocation);
			long elapsedMillis = now - startedAt;
			long idleMillis = now - lastBusyAt;
			if (isObstacleTraversalSettled(crossed, playerBusy, elapsedMillis, idleMillis, minimumMillis))
			{
				return true;
			}

			if (!crossed && !playerBusy && elapsedMillis >= minimumMillis && idleMillis >= OBSTACLE_STABLE_IDLE_MILLIS)
			{
				return false;
			}
			sleep(100, 175);
		}
		long now = System.currentTimeMillis();
		return isObstacleTraversalSettled(
			hasReachedNextPlatform(nextLocation),
			isPlayerBusy(),
			now - startedAt,
			now - lastBusyAt,
			minimumMillis);
	}

	private boolean hasReachedNextPlatform(BrimhavenArenaLocation nextLocation)
	{
		WorldPoint player = Rs2Player.getWorldLocation();
		if (player == null)
		{
			return false;
		}
		return !isInArena(player) || hasCrossedObstacle(player, nextLocation);
	}

	private boolean isPlayerIdle()
	{
		return !isPlayerBusy();
	}

	private boolean isPlayerBusy()
	{
		return Rs2Player.isAnimating() || Rs2Player.isMoving();
	}

	private boolean clickPastObstacle(BrimhavenArenaLocation nextLocation)
	{
		WorldPoint target = nextLocation.toCenteredWorldPoint();
		if (Rs2Walker.walkFastCanvas(target))
		{
			return true;
		}
		return Rs2Walker.walkTo(target, 2);
	}

	private BrimhavenArenaPath currentOrNewPath(WorldPoint player, WorldPoint dispenser, BrimhavenAgilityBotConfig config)
	{
		BrimhavenArenaPath path = plugin.getCurrentPath();
		if (path == null || path.hasPathChanged(player, dispenser))
		{
			path = BrimhavenArenaPathFinder.findPath(
				player,
				dispenser,
				Math.max(1, Rs2Player.getBoostedSkillLevel(Skill.AGILITY)),
				BrimhavenTraversalPreferences.fromConfig(config));
			plugin.setCurrentPath(path);
		}
		return path;
	}

	private void tagDispenser(WorldPoint dispenser)
	{
		plugin.setState(BrimhavenBotState.TAGGING_DISPENSER);
		int beforeRewards = rewardCount();
		TileObject dispenserObject = findDispenserObject(dispenser);
		if (dispenserObject == null)
		{
			log.warn("[BrimhavenAgilityBot] ticket dispenser object not found near {}", dispenser);
			return;
		}

		if (!interactWithDispenser(dispenserObject))
		{
			log.warn("[BrimhavenAgilityBot] failed to interact with ticket dispenser {} at {}",
				dispenserObject.getId(), dispenserObject.getWorldLocation());
			return;
		}

		sleepUntil(() -> !plugin.isTicketAvailable() || rewardCount() > beforeRewards, ENTRY_WAIT_MILLIS);
		plugin.setRewardCount(rewardCount());
		plugin.setCurrentPath(null);
	}

	private TileObject findDispenserObject(WorldPoint dispenser)
	{
		List<TileObject> candidates = objectsByIds(
			BrimhavenObstacleInteraction.dispenserObjectIds(),
			dispenser,
			DISPENSER_SEARCH_RADIUS);
		List<TileObject> likelyDispenser = candidates.stream()
			.filter(this::isLikelyTicketDispenser)
			.collect(Collectors.toList());
		if (!likelyDispenser.isEmpty())
		{
			return nearest(likelyDispenser);
		}
		return nearest(candidates);
	}

	private boolean isLikelyTicketDispenser(TileObject object)
	{
		if (object == null)
		{
			return false;
		}
		String name = Rs2GameObject.getCompositionName(object).orElse("").toLowerCase(Locale.ROOT);
		return name.contains("ticket dispenser")
			|| name.contains("agility dispenser")
			|| BrimhavenObstacleInteraction.dispenserActions().stream()
				.anyMatch(action -> Rs2GameObject.hasAction(object, action));
	}

	private boolean interactWithDispenser(TileObject dispenserObject)
	{
		for (String action : BrimhavenObstacleInteraction.dispenserActions())
		{
			if (Rs2GameObject.hasAction(dispenserObject, action) && interactWithObject(dispenserObject, action))
			{
				return true;
			}
		}
		return interactWithObject(dispenserObject, "");
	}

	private TileObject findObstacleObject(
		BrimhavenArenaObstacle obstacle,
		BrimhavenArenaLocation current,
		BrimhavenArenaLocation next,
		BrimhavenAgilityBotConfig config)
	{
		List<TileObject> candidates = objectsByIds(BrimhavenObstacleInteraction.idsFor(obstacle), Rs2Player.getWorldLocation(), OBJECT_SEARCH_DISTANCE);
		List<TileObject> between = candidates.stream()
			.filter(object -> isBetweenPlatforms(object.getWorldLocation(), current, next))
			.filter(object -> obstacle != BrimhavenArenaObstacle.PLANK || !isKnownBadPlank(object))
			.collect(Collectors.toList());
		if (!between.isEmpty())
		{
			return nearest(between);
		}
		return nearest(candidates);
	}

	private boolean isKnownBadPlank(TileObject object)
	{
		return object != null
			&& (object.getId() == ObjectID.AGILITYARENA_PLANK_BROKE1
				|| plugin.isOnBadPlank(object.getWorldLocation()));
	}

	private TileObject findNearestObject(Collection<Integer> ids, WorldPoint anchor, int distance)
	{
		return nearest(objectsByIds(ids, anchor, distance));
	}

	private List<TileObject> objectsByIds(Collection<Integer> ids, WorldPoint anchor, int distance)
	{
		WorldPoint searchAnchor = anchor != null ? anchor : Rs2Player.getWorldLocation();
		return Rs2GameObject.getAll((TileObject object) -> ids.contains(object.getId()), searchAnchor, distance);
	}

	private TileObject nearest(List<TileObject> objects)
	{
		WorldPoint player = Rs2Player.getWorldLocation();
		if (player == null || objects == null || objects.isEmpty())
		{
			return null;
		}
		return objects.stream()
			.min(Comparator.comparingInt(object -> object.getWorldLocation().distanceTo(player)))
			.orElse(null);
	}

	private boolean isBetweenPlatforms(WorldPoint point, BrimhavenArenaLocation current, BrimhavenArenaLocation next)
	{
		if (point == null)
		{
			return false;
		}
		WorldPoint a = current.toCenteredWorldPoint();
		WorldPoint b = next.toCenteredWorldPoint();
		int minX = Math.min(a.getX(), b.getX()) - PLATFORM_OBJECT_MARGIN;
		int maxX = Math.max(a.getX(), b.getX()) + PLATFORM_OBJECT_MARGIN;
		int minY = Math.min(a.getY(), b.getY()) - PLATFORM_OBJECT_MARGIN;
		int maxY = Math.max(a.getY(), b.getY()) + PLATFORM_OBJECT_MARGIN;
		return point.getPlane() == BrimhavenArenaLocation.PLANE
			&& point.getX() >= minX
			&& point.getX() <= maxX
			&& point.getY() >= minY
			&& point.getY() <= maxY;
	}

	private boolean interactWithObject(TileObject object, String action)
	{
		if (object == null || object.getWorldLocation() == null)
		{
			return false;
		}

		WorldPoint player = Rs2Player.getWorldLocation();
		if (player != null && player.distanceTo(object.getWorldLocation()) > 15)
		{
			Rs2Walker.walkTo(object.getWorldLocation(), 3);
			return false;
		}

		return Rs2GameObject.interact(object, action, true);
	}

	private boolean isInArena()
	{
		return isInArena(Rs2Player.getWorldLocation());
	}

	private static boolean isInArena(WorldPoint player)
	{
		return player != null
			&& player.getRegionID() == BrimhavenArenaLocation.AGILITY_ARENA_REGION_ID
			&& player.getPlane() == BrimhavenArenaLocation.PLANE;
	}

	private WorldPoint getHintArrowPoint()
	{
		return CupidBot.getClientThread().runOnClientThreadOptional(() -> CupidBot.getClient().getHintArrowPoint()).orElse(null);
	}

	private int rewardCount()
	{
		return BrimhavenAgilityRewards.rewardQuantity(Rs2Inventory::itemQuantity);
	}
}
