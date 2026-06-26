package net.runelite.client.plugins.cupidbot.agility;

import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.agility.courses.BrimhavenSpikeCourse;
import net.runelite.client.plugins.cupidbot.agility.courses.GnomeStrongholdCourse;
import net.runelite.client.plugins.cupidbot.agility.courses.PrifddinasCourse;
import net.runelite.client.plugins.cupidbot.agility.courses.WerewolfCourse;
import net.runelite.client.plugins.cupidbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.cupidbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.cupidbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.cupidbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.cupidbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.cupidbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AgilityScript extends Script
{

	final CupidAgilityPlugin plugin;
	final CupidAgilityConfig config;

	WorldPoint startPoint = null;
	int lastAgilityXp = 0;
	long lastTimeoutWarning = 0;  // For throttled timeout warnings

	@Inject
	public AgilityScript(CupidAgilityPlugin plugin, CupidAgilityConfig config)
	{
		this.plugin = plugin;
		this.config = config;
	}

	@Override
	public void shutdown()
	{
		// Reset BrimhavenSpike course flags if applicable
		if (plugin.getCourseHandler() instanceof BrimhavenSpikeCourse)
		{
			BrimhavenSpikeCourse course = (BrimhavenSpikeCourse) plugin.getCourseHandler();
			course.reset();
		}

		super.shutdown();
	}

	public boolean run()
	{
		CupidBot.enableAutoRunOn = true;
		Rs2Antiban.resetAntibanSettings();
		Rs2Antiban.antibanSetupTemplates.applyAgilitySetup();
		startPoint = plugin.getCourseHandler().getStartPoint();
		lastAgilityXp = CupidBot.getClient().getSkillExperience(Skill.AGILITY);
		mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
			try
			{
				if (!CupidBot.isLoggedIn())
				{
					return;
				}
				if (!super.run())
				{
					return;
				}

				// Debug log to see if main loop is running
				CupidBot.log("AgilityScript main loop running - Course: " + config.agilityCourse().getTooltip());
				if (!plugin.hasRequiredLevel())
				{
					CupidBot.log("Early return: Required level not met");
					CupidBot.showMessage("You do not have the required level for this course.");
					shutdown();
					return;
				}

				// Check coin requirement for BrimhavenSpike course (only before payment)
				if (plugin.getCourseHandler() instanceof BrimhavenSpikeCourse)
				{
					BrimhavenSpikeCourse course = (BrimhavenSpikeCourse) plugin.getCourseHandler();
					if (!course.hasPaid() && !course.hasRequiredCoins())
					{
						CupidBot.log("Early return: Not enough coins for BrimhavenSpike course");
						CupidBot.showMessage("You need 200 coins to enter the Brimhaven Spike course!");
						shutdown();
						return;
					}
				}
				if (Rs2AntibanSettings.actionCooldownActive)
				{
					CupidBot.log("Early return: Action cooldown active");
					return;
				}
				if (startPoint == null)
				{
					CupidBot.log("Early return: Start point is null");
					CupidBot.showMessage("Agility course: " + config.agilityCourse().getTooltip() + " is not supported.");
					shutdown();
					return;
				}

				final WorldPoint playerWorldLocation = CupidBot.getClientThread().invoke(() -> CupidBot.getClient().getLocalPlayer().getWorldLocation());
				final int currentAgilityXp = CupidBot.getClient().getSkillExperience(Skill.AGILITY);

				if (handleFood())
				{
					CupidBot.log("Early return: Handling food");
					return;
				}
				if (handleSummerPies())
				{
					CupidBot.log("Early return: Handling summer pies");
					return;
				}

				if (lootMarksOfGrace())
				{
					CupidBot.log("Early return: Looting marks of grace");
					return;
				}

				if (handleCourseSpecificActions(playerWorldLocation))
				{
					return;
				}

				// Debug log to see if script is running
				if (plugin.getCourseHandler() instanceof BrimhavenSpikeCourse) {
					CupidBot.log("BrimhavenSpike course detected, but handleCourseSpecificActions returned false");
				}

				final int agilityExp = CupidBot.getClient().getSkillExperience(Skill.AGILITY);

				TileObject gameObject = plugin.getCourseHandler().getCurrentObstacle();

				if (gameObject == null)
				{
					CupidBot.log("No agility obstacle found. Report this as a bug if this keeps happening.");
					return;
				}

				if (!Rs2Camera.isTileOnScreen(gameObject))
				{
					Rs2Walker.walkMiniMap(gameObject.getWorldLocation());
				}

				// Check if we should click (handles animation/XP logic)
				if (!plugin.getCourseHandler().shouldClickObstacle(currentAgilityXp, lastAgilityXp))
				{
					return; // Not ready to click yet
				}

				// Update XP if we got it while animating
				if (currentAgilityXp > lastAgilityXp)
				{
					lastAgilityXp = currentAgilityXp;
				}

				// Handle alchemy if enabled
				if (shouldPerformAlch())
				{
					Optional<String> alchItem = getAlchItem();
					if (alchItem.isPresent())
					{
						// Check if we should skip inefficient alchs
						if (config.skipInefficient())
						{
							// Only alch if obstacle is far enough for efficient alching
							if (gameObject.getWorldLocation().distanceTo(playerWorldLocation) >= 5)
							{
								if (config.efficientAlching())
								{
									if (performEfficientAlch(gameObject, alchItem.get(), agilityExp))
									{
										return;
									}
								}
								else
								{
									// Still do normal alch if far enough but efficient alching is disabled
									performNormalAlch(alchItem.get());
								}
							}
							// Skip alching if obstacle is too close
						}
						else
						{
							// Normal behavior when skipInefficient is disabled
							if (config.efficientAlching())
							{
								if (performEfficientAlch(gameObject, alchItem.get(), agilityExp))
								{
									return;
								}
							}
							// Fall back to normal alching
							performNormalAlch(alchItem.get());
						}
					}
				}

				// Normal obstacle interaction
				if (Rs2GameObject.interact(gameObject)) {
					// Wait for completion - this now returns quickly on XP drop
					boolean completed = plugin.getCourseHandler().waitForCompletion(agilityExp,
						CupidBot.getClientThread().invoke(() -> CupidBot.getClient().getLocalPlayer().getWorldLocation()).getPlane());

					if (!completed) {
						// Timeout occurred - log warning (throttled to once per 30 seconds)
						long now = System.currentTimeMillis();
						if (now - lastTimeoutWarning > 30000) {
							CupidBot.log("Obstacle completion timed out - retrying on next iteration");
							lastTimeoutWarning = now;
						}
						return;  // Bail early to avoid acting on stale state
					}

					// XP tracking is already updated before clicking (line 137)
					// Don't update here to avoid losing early action state

					// If we're still animating after XP, don't add delays - proceed immediately
					if (!Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
						// Only add delays if we're not animating
						Rs2Antiban.actionCooldown();
						Rs2Antiban.takeMicroBreakByChance();
					}
				}
			}
			catch (Exception ex)
			{
				CupidBot.log("An error occurred: " + ex.getMessage(), ex);
			}
		}, 0, 100, TimeUnit.MILLISECONDS);
		return true;
	}

	private Optional<String> getAlchItem()
	{
		String itemsInput = config.itemsToAlch().trim();
		if (itemsInput.isEmpty())
		{
			// CupidBot.log("No items specified for alching or none available.");
			return Optional.empty();
		}

		List<String> itemsToAlch = Arrays.stream(itemsInput.split(","))
			.map(String::trim)
			.map(String::toLowerCase)
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toList());

		if (itemsToAlch.isEmpty())
		{
			// CupidBot.log("No valid items specified for alching.");
			return Optional.empty();
		}

		for (String itemName : itemsToAlch)
		{
			if (Rs2Inventory.hasItem(itemName))
			{
				return Optional.of(itemName);
			}
		}

		return Optional.empty();
	}

	private boolean lootMarksOfGrace()
	{
		final int lootDistance = plugin.getCourseHandler().getLootDistance();
		if (Rs2Inventory.isFull() && !Rs2Inventory.contains(ItemID.GRACE))
		{
			return false;
		}

		Rs2TileItemModel markOfGrace = CupidBot.getRs2TileItemCache().query()
			.fromWorldView()
			.withId(ItemID.GRACE)
			.where(Rs2TileItemModel::isLootAble)
			.where(item -> Rs2GameObject.canReach(item.getWorldLocation(), lootDistance, lootDistance, lootDistance, lootDistance))
			.nearest(lootDistance);

		if (markOfGrace == null)
		{
			return false;
		}

		if (!markOfGrace.pickup())
		{
			return false;
		}

		Rs2Player.waitForWalking();
		return true;
	}

	private boolean handleFood()
	{
		if (Rs2Player.getHealthPercentage() > config.hitpoints())
		{
			return false;
		}

		List<Rs2ItemModel> foodItems = plugin.getInventoryFood();
		if (foodItems.isEmpty())
		{
			return false;
		}
		Rs2ItemModel foodItem = foodItems.get(0);

		Rs2Inventory.interact(foodItem, foodItem.getName().toLowerCase().contains("jug of wine") ? "drink" : "eat");
		Rs2Inventory.waitForInventoryChanges(1800);

		if (Rs2Inventory.contains(ItemID.JUG_EMPTY))
		{
			Rs2Inventory.dropAll(ItemID.JUG_EMPTY);
		}
		return true;
	}

	private boolean handleSummerPies()
	{
		if (plugin.getCourseHandler().getCurrentObstacleIndex() > 0)
		{
			return false;
		}
		if (Rs2Player.getBoostedSkillLevel(Skill.AGILITY) > plugin.getCourseHandler().getRequiredLevel())
		{
			return false;
		}

		List<Rs2ItemModel> summerPies = plugin.getSummerPies();
		if (summerPies.isEmpty())
		{
			return false;
		}
		Rs2ItemModel summerPie = summerPies.get(0);

		Rs2Inventory.interact(summerPie, "eat");
		Rs2Inventory.waitForInventoryChanges(1800);
		if (Rs2Inventory.contains(ItemID.PIEDISH))
		{
			Rs2Inventory.dropAll(ItemID.PIEDISH);
		}
		return true;
	}

	private boolean shouldPerformAlch()
	{
		if (!config.alchemy())
		{
			return false;
		}

		// Check if we should skip alching based on configured chance
		if (Math.random() * 100 < config.alchSkipChance())
		{
			return false;
		}

		return true;
	}

	private boolean performEfficientAlch(TileObject gameObject, String alchItem, int agilityExp)
	{
		WorldPoint playerLocation = CupidBot.getClientThread().invoke(() -> CupidBot.getClient().getLocalPlayer().getWorldLocation());

		if (gameObject.getWorldLocation().distanceTo(playerLocation) >= 5)
		{
			// Efficient alching: click, alch, click
			if (Rs2GameObject.interact(gameObject))
			{
				sleep(100, 200);
				Rs2Magic.alch(alchItem, 50, 75);
				Rs2GameObject.interact(gameObject);
				boolean completed = plugin.getCourseHandler().waitForCompletion(agilityExp,
					CupidBot.getClientThread().invoke(() -> CupidBot.getClient().getLocalPlayer().getWorldLocation()).getPlane());

				if (!completed) {
					// Timeout during efficient alching - log warning
					long now = System.currentTimeMillis();
					if (now - lastTimeoutWarning > 30000) {
						CupidBot.log("Obstacle completion timed out during efficient alching");
						lastTimeoutWarning = now;
					}
					return false;  // Return false to indicate alch sequence failed
				}

				Rs2Antiban.actionCooldown();
				Rs2Antiban.takeMicroBreakByChance();
				lastAgilityXp = CupidBot.getClient().getSkillExperience(Skill.AGILITY);
				return true;
			}
		}
		return false;
	}

	private void performNormalAlch(String alchItem)
	{
		// Simple alch - waitForCompletion handles all timing
		Rs2Magic.alch(alchItem, 50, 75);
	}

	private boolean handleCourseSpecificActions(WorldPoint playerWorldLocation)
	{
		CupidBot.log("handleCourseSpecificActions called for: " + plugin.getCourseHandler().getClass().getSimpleName());

		if (plugin.getCourseHandler() instanceof PrifddinasCourse)
		{
			PrifddinasCourse course = (PrifddinasCourse) plugin.getCourseHandler();
			return course.handlePortal() || course.handleWalkToStart(playerWorldLocation);
		}
		else if (plugin.getCourseHandler() instanceof WerewolfCourse)
		{
			WerewolfCourse course = (WerewolfCourse) plugin.getCourseHandler();
			return course.handleFirstSteppingStone(playerWorldLocation)
				|| course.handleStickPickup(playerWorldLocation)
				|| course.handleSlide()
				|| course.handleStickReturn(playerWorldLocation);
		}
		else if (plugin.getCourseHandler() instanceof BrimhavenSpikeCourse)
		{
			CupidBot.log("BrimhavenSpikeCourse detected, calling handleWalkToStart");
			BrimhavenSpikeCourse course = (BrimhavenSpikeCourse) plugin.getCourseHandler();
			boolean result = course.handleWalkToStart(playerWorldLocation);
			CupidBot.log("BrimhavenSpikeCourse handleWalkToStart returned: " + result);
			return result;
		}
		else if (!(plugin.getCourseHandler() instanceof GnomeStrongholdCourse))
		{
			return plugin.getCourseHandler().handleWalkToStart(playerWorldLocation);
		}
		return false;
	}
}
