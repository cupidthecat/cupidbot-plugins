package net.runelite.client.plugins.cupidbot.moonsofperil.handlers;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.cupidbot.moonsofperil.enums.State;
import net.runelite.client.plugins.cupidbot.moonsofperil.MoonsOfPerilConfig;
import net.runelite.client.plugins.cupidbot.moonsofperil.MoonsOfPerilScript;
import net.runelite.client.plugins.cupidbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;

import static net.runelite.client.plugins.cupidbot.util.Global.sleep;
import static net.runelite.client.plugins.cupidbot.util.Global.sleepUntil;

import java.util.List;

public class DeathHandler implements BaseHandler {
    private final MoonsOfPerilScript script = null;
    private final boolean shutdownOnDeath;
    private final boolean debugLogging;

    public DeathHandler(MoonsOfPerilConfig cfg) {
        this.shutdownOnDeath = cfg.shutdownOnDeath();
        this.debugLogging = cfg.debugLogging();
    }

    @Override
    public boolean validate() {
        return Rs2Player.isInArea(new WorldPoint(3221, 3219, 0), 30); // true if near lumbridge spawn point
    }

    @Override
    public State execute() {
        if (debugLogging) {CupidBot.log("Player detected near Lumbridge spawn. Starting death handler sequence");}
        if (shutdownOnDeath) {
            CupidBot.showMessage("Script shut down due to player death");
            script.shutdown();
        }

        if (retrieveTravelItems()) {
            sleep(1_200);
            if (retrieveDeathItems()) {
                sleep(1_200);
                equipDeathItems();
                }
            }
        return null;
    }

    /**
     * Travels to nearest bank and retrieves runes required for Civitas illa Fortis teleport.
     */
    private boolean retrieveTravelItems() {
        BreakHandlerScript.setLockState(true);
        Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint(), 1);
        sleep(600);
        if (Rs2Bank.openBank()) {
            sleep(600);
            Rs2Bank.withdrawOne(ItemID.FIRERUNE);
            sleep(600);
            Rs2Bank.withdrawOne(ItemID.EARTHRUNE);
            sleep(600);
            Rs2Bank.withdrawX(ItemID.LAWRUNE, 2);
            sleep(600);
            Rs2Bank.closeBank();
            BreakHandlerScript.setLockState(false);
            return true;
        }
        BreakHandlerScript.setLockState(false);
        return false;
    }

    private boolean retrieveDeathItems() {
        WorldPoint graveLocation = new WorldPoint(1440, 9626, 1);
        if (debugLogging) {CupidBot.log("Attempting to walk back to grave site");}
        Rs2Walker.walkTo(graveLocation, 2);
        sleepUntil(() -> (Rs2Player.getWorldLocation().distanceTo(graveLocation) <= 3), 60_000);
        if (CupidBot.getRs2NpcCache().query().withId(NpcID.GRAVESTONE_DEFAULT).interact("Loot")) {
            if (debugLogging) {CupidBot.log("Successfully looted gravestone");}
            return true;
        }
        return false;
    }

    private void equipDeathItems() {
        if (debugLogging) {CupidBot.log("Attempting to equip items retrieved");}
        List<Rs2ItemModel> items = Rs2Inventory.all();
        for (Rs2ItemModel i:items) {
            Rs2Inventory.equip(i.getId());
            sleep(300);
        }
    }
}
