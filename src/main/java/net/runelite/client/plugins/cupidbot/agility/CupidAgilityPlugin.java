package net.runelite.client.plugins.cupidbot.agility;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.cupidbot.PluginConstants;
import net.runelite.client.plugins.cupidbot.agility.courses.AgilityCourseHandler;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

@PluginDescriptor(

	name = PluginConstants.MOCROSOFT + "Agility",
	description = "CupidBot agility plugin",
    authors = { "Mocrosoft" },
    version = CupidAgilityPlugin.version,
        minClientVersion = "2.1.0",
	tags = {"agility", "cupidbot"},
    iconUrl = "CupidAgilityPlugin/assets/icon.png",
    cardUrl = "CupidAgilityPlugin/assets/card.png",
    enabledByDefault = PluginConstants.DEFAULT_ENABLED,
    isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class CupidAgilityPlugin extends Plugin
{
	public static final String version = "1.2.8";
	@Inject
	private CupidAgilityConfig config;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private CupidAgilityOverlay agilityOverlay;
	@Inject
	private AgilityScript agilityScript;


	@Provides
	CupidAgilityConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CupidAgilityConfig.class);
	}
	public CupidAgilityConfig getConfig()
	{
		return config;
	}

	@Override
	protected void startUp() throws AWTException
	{
		if (overlayManager != null)
		{
			overlayManager.add(agilityOverlay);
		}
        agilityScript.run();
    }

	protected void shutDown()
	{
		agilityScript.shutdown();
		overlayManager.remove(agilityOverlay);
	}

	public AgilityCourseHandler getCourseHandler()
	{
		return config.agilityCourse().getHandler();
	}

	public List<Rs2ItemModel> getInventoryFood()
	{
		return Rs2Inventory.getInventoryFood().stream().filter(i -> !(i.getName().toLowerCase().contains("summer pie"))).collect(Collectors.toList());
	}

	public List<Rs2ItemModel> getSummerPies()
	{
		return Rs2Inventory.getInventoryFood().stream().filter(i -> i.getName().toLowerCase().contains("summer pie")).collect(Collectors.toList());
	}

	public boolean hasRequiredLevel()
	{
		if (getSummerPies().isEmpty() || !getCourseHandler().canBeBoosted())
		{
			return Rs2Player.getRealSkillLevel(Skill.AGILITY) >= getCourseHandler().getRequiredLevel();
		}

		return Rs2Player.getBoostedSkillLevel(Skill.AGILITY) >= getCourseHandler().getRequiredLevel();
	}

	public AgilityScript getAgilityScript() {
		return agilityScript;
	}

}
