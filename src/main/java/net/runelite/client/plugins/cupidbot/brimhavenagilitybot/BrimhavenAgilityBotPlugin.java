package net.runelite.client.plugins.cupidbot.brimhavenagilitybot;

import com.google.inject.Provides;
import java.awt.AWTException;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.PluginConstants;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = PluginConstants.DEFAULT_PREFIX + "Brimhaven Agility Bot",
	description = "Restocks, enters, and routes Brimhaven Agility Arena tickets and vouchers.",
	tags = {"brimhaven", "agility", "ticket", "voucher", "minigame", "cupidbot"},
	authors = {"Mocrosoft"},
	version = "1.0.13",
	minClientVersion = "2.6.12",
	enabledByDefault = PluginConstants.DEFAULT_ENABLED,
	isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class BrimhavenAgilityBotPlugin extends Plugin
{
	public static final String VERSION = "1.0.13";
	static final int ENTRY_PAID_VARBIT = VarbitID.AGILITYARENA_CANENTER;
	static final int COOLDOWN_REQUIRED_VARBIT = VarbitID.AGILITY_ARENA_TELEPORTED_OUT;
	static final int TICKET_AVAILABLE_VARBIT = VarbitID.AGILITYARENA_TICKETAVAILABLE;
	static final WorldArea ARENA_ENTRY_AREA = new WorldArea(2805, 3185, 7, 11, 0);
	static final WorldPoint ARENA_ENTRANCE_POINT = new WorldPoint(2809, 3192, 0);

	@Inject
	private Client client;
	@Inject
	private BrimhavenAgilityBotConfig config;
	@Inject
	private BrimhavenAgilityBotScript script;
	@Inject
	private BrimhavenAgilityBotOverlay overlay;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private BrimhavenPlankManager plankManager;

	@Getter
	private volatile boolean entryPaid;
	@Getter
	private volatile boolean cooldownRequired;
	@Getter
	private volatile boolean ticketAvailable;
	@Getter
	private volatile int boostedAgilityLevel = 1;
	@Getter
	private volatile BrimhavenArenaPath currentPath;
	@Getter
	private volatile BrimhavenBotState state = BrimhavenBotState.STARTING;
	@Getter
	private volatile int rewardCount;

	@Provides
	BrimhavenAgilityBotConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BrimhavenAgilityBotConfig.class);
	}

	@Override
	protected void startUp() throws AWTException
	{
		refreshStateFromClient();
		if (overlayManager != null)
		{
			overlayManager.add(overlay);
		}
		script.run(config);
	}

	@Override
	protected void shutDown()
	{
		script.shutdown();
		plankManager.clear();
		currentPath = null;
		state = BrimhavenBotState.STOPPED;
		if (overlayManager != null)
		{
			overlayManager.remove(overlay);
		}
	}

	void setCurrentPath(BrimhavenArenaPath currentPath)
	{
		this.currentPath = currentPath;
	}

	void setState(BrimhavenBotState state)
	{
		this.state = state;
		CupidBot.status = "Brimhaven: " + state;
	}

	void setRewardCount(int rewardCount)
	{
		this.rewardCount = rewardCount;
	}

	boolean isOnBadPlank(WorldPoint point)
	{
		return plankManager.isOnBadPlank(point);
	}

	void refreshStateFromClient()
	{
		if (client == null || CupidBot.getClientThread() == null)
		{
			return;
		}
		CupidBot.getClientThread().runOnClientThreadOptional(() -> {
			entryPaid = client.getVarbitValue(ENTRY_PAID_VARBIT) == 1;
			cooldownRequired = client.getVarbitValue(COOLDOWN_REQUIRED_VARBIT) == 1;
			ticketAvailable = client.getVarbitValue(TICKET_AVAILABLE_VARBIT) == 1;
			boostedAgilityLevel = Math.max(1, client.getBoostedSkillLevel(Skill.AGILITY));
			return null;
		});
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() == ENTRY_PAID_VARBIT)
		{
			entryPaid = event.getValue() == 1;
		}
		else if (event.getVarbitId() == COOLDOWN_REQUIRED_VARBIT)
		{
			cooldownRequired = event.getValue() == 1;
		}
		else if (event.getVarbitId() == TICKET_AVAILABLE_VARBIT)
		{
			ticketAvailable = event.getValue() == 1;
			if (!ticketAvailable)
			{
				currentPath = null;
			}
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() == Skill.AGILITY)
		{
			boostedAgilityLevel = Math.max(1, event.getBoostedLevel());
			currentPath = null;
		}
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		if (plankManager.add(event.getGroundObject()))
		{
			currentPath = null;
		}
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		if (plankManager.remove(event.getGroundObject()))
		{
			currentPath = null;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		plankManager.recomputeCorrectPlanks();
	}
}
