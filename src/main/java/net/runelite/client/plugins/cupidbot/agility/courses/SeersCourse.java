package net.runelite.client.plugins.cupidbot.agility.courses;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.agility.models.AgilityObstacleModel;
import net.runelite.client.plugins.cupidbot.util.Global;
import net.runelite.client.plugins.cupidbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.cupidbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;

import java.util.List;

public class SeersCourse implements AgilityCourseHandler
{
	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(2729, 3486, 0);
	}

	@Override
	public List<AgilityObstacleModel> getObstacles()
	{
		return List.of(
			new AgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_WALLCLIMB),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_JUMP),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_TIGHTROPE),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_JUMP_1),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_JUMP_2),
			new AgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_LEAPDOWN)
		);
	}

	@Override
	public Integer getRequiredLevel()
	{
		return 60;
	}

	@Override
	public boolean handleWalkToStart(WorldPoint playerWorldLocation)
	{
		if (CupidBot.getClient().getTopLevelWorldView().getPlane() != 0)
		{
			return false;
		}
		if (getCurrentObstacleIndex() > 0)
		{
			return false;
		}

		if (CupidBot.getVarbitValue(VarbitID.KANDARIN_DIARY_HARD_COMPLETE) == 1
			&& Rs2Magic.hasRequiredRunes(Rs2Spells.CAMELOT_TELEPORT)
			&& playerWorldLocation.distanceTo(getStartPoint()) > 12)

		{
			Rs2Magic.cast(Rs2Spells.CAMELOT_TELEPORT, "Seers'", 2);
			return Global.sleepUntil(() -> {
				WorldPoint currentLocation = CupidBot.getClient().getLocalPlayer().getWorldLocation();
				return currentLocation.distanceTo(getStartPoint()) <= 12;
			}, 5000);
		}

		if (playerWorldLocation.distanceTo(getStartPoint()) > 12)
		{
			CupidBot.log("Going back to course's starting point");
			Rs2Walker.walkTo(getStartPoint(), 2);
			return true;
		}
		return false;
	}
}
