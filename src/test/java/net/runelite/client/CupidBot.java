package net.runelite.client;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import net.runelite.client.plugins.kourendlibrary.KourendLibraryPlugin;
import net.runelite.client.plugins.cupidbot.GiantSeaweedFarmer.GiantSeaweedFarmerPlugin;
import net.runelite.client.plugins.cupidbot.agentserver.AgentServerPlugin;
import net.runelite.client.plugins.cupidbot.arceuuslibrary.ArceuusLibraryPlugin;
import net.runelite.client.plugins.cupidbot.astralrc.AstralRunesPlugin;
import net.runelite.client.plugins.cupidbot.birdhouseruns.FornBirdhouseRunsPlugin;
import net.runelite.client.plugins.cupidbot.autofishing.AutoFishingPlugin;
import net.runelite.client.plugins.cupidbot.crafting.jewelry.JewelryPlugin;
import net.runelite.client.plugins.cupidbot.example.ExamplePlugin;
import net.runelite.client.plugins.cupidbot.karambwans.GabulhasKarambwansPlugin;
import net.runelite.client.plugins.cupidbot.kraken.KrakenPlugin;
import net.runelite.client.plugins.cupidbot.leftclickcast.LeftClickCastPlugin;
import net.runelite.client.plugins.cupidbot.pitfallhunter.PitfallHunterPlugin;
import net.runelite.client.plugins.cupidbot.sailing.MSailingPlugin;
import net.runelite.client.plugins.cupidbot.thieving.ThievingPlugin;
import net.runelite.client.plugins.cupidbot.motherloadmine.MotherloadMinePlugin;
import net.runelite.client.plugins.cupidbot.woodcutting.AutoWoodcuttingPlugin;
import net.runelite.client.plugins.woodcutting.WoodcuttingPlugin;

public class CupidBot
{

	private static final Class<?>[] debugPlugins = {
		AgentServerPlugin.class,
		FornBirdhouseRunsPlugin.class,
		GiantSeaweedFarmerPlugin.class,
		PitfallHunterPlugin.class,
		GabulhasKarambwansPlugin.class,
		MotherloadMinePlugin.class,
		KourendLibraryPlugin.class,
		ArceuusLibraryPlugin.class
	};

    public static void main(String[] args) throws Exception
    {
		List<Class<?>> _debugPlugins = Arrays.stream(debugPlugins).collect(Collectors.toList());
        RuneLiteDebug.pluginsToDebug.addAll(_debugPlugins);
        RuneLiteDebug.main(args);
    }
}
