package net.runelite.client.plugins.cupidbot.aiofighter.combat;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.cupidbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class FoodScript extends Script {

    Map<Integer, String> equipment = new HashMap<>();

    public boolean run(AIOFighterConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!CupidBot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!config.toggleFood()) return;
                double treshHold = (double) (CupidBot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) * 100) / CupidBot.getClient().getRealSkillLevel(Skill.HITPOINTS);
                if (Rs2Equipment.all("guthan's").count() == 4) {
                    if (treshHold > 80) //only unequip guthans if we have more than 80% hp
                        unEquipGuthans();
                    return;
                } else {
                    if (treshHold > 51) //return as long as we have more than 51% health and not guthan equipped
                        return;
                }

                if (!equipFullGuthans()) {
                    Rs2Player.eatAt(50);
                }

            } catch (Exception ex) {
                CupidBot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void unEquipGuthans() {
        Rs2Equipment.all("guthan's").forEach(g -> {
            if (equipment.containsKey(g.getSlot())) {
                Rs2Inventory.wield(equipment.get(g.getSlot()));
            }
            if (g.getSlot() == EquipmentInventorySlot.WEAPON.getSlotIdx() && equipment.containsKey(EquipmentInventorySlot.SHIELD.getSlotIdx())) {
                Rs2Inventory.wield(equipment.get(EquipmentInventorySlot.SHIELD.getSlotIdx()));
            }
        });
    }

    private boolean equipFullGuthans() {
        Rs2Equipment.all().filter(i -> i != null && !i.getName().contains("guthan's")).forEach(i -> equipment.put(i.getSlot(), i.getName()));

        if (!Rs2Equipment.isWearing("guthan's warspear")) {
            Rs2ItemModel spearWidget = CupidBot.getClientThread().runOnClientThreadOptional(() ->
                    Rs2Inventory.get("guthan's warspear")).orElse(null);
            if (spearWidget == null) return false;
            Rs2Inventory.wield(spearWidget.getName());
        }
        if (!Rs2Equipment.isWearing("guthan's platebody")) {
            Rs2ItemModel bodyWidget = CupidBot.getClientThread().runOnClientThreadOptional(() -> Rs2Inventory.get("guthan's platebody")).orElse(null);
            if (bodyWidget == null) return false;
            Rs2Inventory.wield(bodyWidget.getName());
        }
        if (!Rs2Equipment.isWearing("guthan's chainskirt")) {
            Rs2ItemModel legsWidget = CupidBot.getClientThread().runOnClientThreadOptional(() -> Rs2Inventory.get("guthan's chainskirt")).orElse(null);
            if (legsWidget == null) return false;
            Rs2Inventory.wield(legsWidget.getName());
        }
        if (!Rs2Equipment.isWearing("guthan's helm")) {
            Rs2ItemModel helmWidget = CupidBot.getClientThread().runOnClientThreadOptional(() -> Rs2Inventory.get("guthan's helm")).orElse(null);
            if (helmWidget == null) return false;
            Rs2Inventory.wield(helmWidget.getName());
        }
        return true;
    }
}
