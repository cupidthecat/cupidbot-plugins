package net.runelite.client.plugins.cupidbot.sailing.features.trials;

import net.runelite.api.Client;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

public class BoatLocation {
    public static WorldPoint fromLocal(Client client, LocalPoint local) {
        if (client == null || local == null) {
            return null;
        }

        if (client.getLocalPlayer() == null) {
            return null;
        }

        WorldView wv = client.getLocalPlayer().getWorldView();
        if (wv == null) {
            return WorldPoint.fromLocalInstance(client, local);
        }

        int wvid = wv.getId();
        boolean isOnBoat = wvid != -1;
        if (isOnBoat) {
            if (client.getTopLevelWorldView() == null) {
                return null;
            }

            WorldEntity we = client.getTopLevelWorldView().worldEntities().byIndex(wvid);
            if (we == null || we.getLocalLocation() == null) {
                return null;
            }
            return WorldPoint.fromLocalInstance(client, we.getLocalLocation());
        }
        return WorldPoint.fromLocalInstance(client, local);
    }
}
