package net.runelite.client.plugins.cupidbot.moonsofperil;

import javax.inject.Inject;
import lombok.Getter;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;

import net.runelite.client.plugins.cupidbot.moonsofperil.enums.State;
import net.runelite.client.plugins.cupidbot.moonsofperil.handlers.BaseHandler;
import net.runelite.client.plugins.cupidbot.util.Rs2InventorySetup;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class MoonsOfPerilScript extends Script {

	private Rs2InventorySetup bloodEquipment;
	private Rs2InventorySetup blueEquipment;
	private Rs2InventorySetup eclipseEquipment;
	private Rs2InventorySetup eclipseClones;

    @Getter
    private net.runelite.client.plugins.cupidbot.moonsofperil.enums.State state = net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.IDLE;
    public static boolean test = false;
    public static volatile net.runelite.client.plugins.cupidbot.moonsofperil.enums.State CURRENT_STATE = net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.IDLE;
	private final MoonsOfPerilConfig config;

    private final Map<net.runelite.client.plugins.cupidbot.moonsofperil.enums.State, net.runelite.client.plugins.cupidbot.moonsofperil.handlers.BaseHandler> handlers = new EnumMap<>(net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.class);

	@Inject
	public MoonsOfPerilScript(MoonsOfPerilConfig config) {
		this.config = config;
	}

    public boolean run() {

        this.bloodEquipment = new Rs2InventorySetup(config.bloodEquipmentNormal(), mainScheduledFuture);
        this.blueEquipment = new Rs2InventorySetup(config.blueEquipmentNormal(), mainScheduledFuture);
        this.eclipseEquipment = new Rs2InventorySetup(config.eclipseEquipmentNormal(), mainScheduledFuture);
        this.eclipseClones = new Rs2InventorySetup(config.eclipseEquipmentClones(), mainScheduledFuture);

        initHandlers();

        CupidBot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!CupidBot.isLoggedIn()) return;
                if (!super.run()) return;
                long start = System.currentTimeMillis();

                /* ---------------- MAIN LOOP ---------------- */
                state = determineState();
                CURRENT_STATE = state;
                net.runelite.client.plugins.cupidbot.moonsofperil.handlers.BaseHandler h = handlers.get(state);
                if (h != null && h.validate()) {
                    h.execute();
                }
                /* ------------------------------------------- */

                CupidBot.log("Loop " + (System.currentTimeMillis() - start) + " ms");
            } catch (Exception ex) {
                CupidBot.log("MoonsOfPerilScript error: " + ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);            // 600 ms ≈ one game tick

        return true;
    }

    /* ------------------------------------------------------------------ */
    /* One-off wiring of state → handler instances                        */
    /* ------------------------------------------------------------------ */
    private void initHandlers() {
        handlers.put(net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.IDLE,        new net.runelite.client.plugins.cupidbot.moonsofperil.handlers.IdleHandler(config));
        handlers.put(net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.RESUPPLY,    new net.runelite.client.plugins.cupidbot.moonsofperil.handlers.ResupplyHandler(config));
        handlers.put(net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.ECLIPSE_MOON,new net.runelite.client.plugins.cupidbot.moonsofperil.handlers.EclipseMoonHandler(config, eclipseEquipment, eclipseClones));
        handlers.put(net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.BLUE_MOON,   new net.runelite.client.plugins.cupidbot.moonsofperil.handlers.BlueMoonHandler(config, blueEquipment));
        handlers.put(net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.BLOOD_MOON,  new net.runelite.client.plugins.cupidbot.moonsofperil.handlers.BloodMoonHandler(config, bloodEquipment));
        handlers.put(net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.REWARDS,     new net.runelite.client.plugins.cupidbot.moonsofperil.handlers.RewardHandler(config));
        handlers.put(net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.DEATH,       new net.runelite.client.plugins.cupidbot.moonsofperil.handlers.DeathHandler(config));
    }

    /* ------------------------------------------------------------------ */
    /* state logic                */
    /* ------------------------------------------------------------------ */
    private net.runelite.client.plugins.cupidbot.moonsofperil.enums.State determineState() {
        /* 1 ─ In case of death */
        if (isPlayerDead())                 return net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.DEATH;

        /* 2 ─ if all bosses are dead --> end-of-run chest loot */
        if (readyToLootChest())             return net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.REWARDS;

        /* 3 ─ Do resupply as needed before boss phases */
        if (needsResupply())                  return net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.RESUPPLY;

        /* 4 ─ boss phases in order */
        if (eclipseMoonSequence())       return net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.ECLIPSE_MOON;
        if (blueMoonSequence())         return net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.BLUE_MOON;
        if (bloodMoonSequence())         return net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.BLOOD_MOON;

        /* 5 ─ nothing to do */
        return net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.IDLE;
    }

    /* ---------- Supplies ------------------------------------------------- */
    private boolean needsResupply()
    {
        // Skip while we're already resupplying
        if (state == net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.RESUPPLY) {
            return false;
        }
        net.runelite.client.plugins.cupidbot.moonsofperil.handlers.BaseHandler resupply = handlers.get(net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.RESUPPLY);
        return resupply != null && resupply.validate();
    }

    /* ---------- Eclipse Moon -------------------------------------------- */
    private boolean eclipseMoonSequence()
    {
        net.runelite.client.plugins.cupidbot.moonsofperil.handlers.BaseHandler eclipse = handlers.get(net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.ECLIPSE_MOON);
        return eclipse != null && eclipse.validate();
    }

    /* ---------- Blue Moon ------------------------------------------------ */
    private boolean blueMoonSequence()
    {
        net.runelite.client.plugins.cupidbot.moonsofperil.handlers.BaseHandler blue = handlers.get(net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.BLUE_MOON);
        return blue != null && blue.validate();
    }

    /* ---------- Blood Moon ---------------------------------------------- */
    private boolean bloodMoonSequence()
    {
        net.runelite.client.plugins.cupidbot.moonsofperil.handlers.BaseHandler blood = handlers.get(net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.BLOOD_MOON);
        return blood != null && blood.validate();
    }

    /* ---------- Rewards Chest ---------------------------------------------- */
    private boolean readyToLootChest() {
        net.runelite.client.plugins.cupidbot.moonsofperil.handlers.BaseHandler reward = handlers.get(net.runelite.client.plugins.cupidbot.moonsofperil.enums.State.REWARDS);
        return reward != null && reward.validate();
    }

    /* ---------- Death Handler ---------------------------------------------- */
    private boolean isPlayerDead() {
        BaseHandler reward = handlers.get(State.DEATH);
        return reward != null && reward.validate();
    }

    /* ------------------------------------------------------------------ */
    /* Clean shutdown – cancels the scheduled task and frees resources    */
    /* ------------------------------------------------------------------ */
    @Override
    public void shutdown() {
        super.shutdown();
    }
}
