package net.runelite.client.plugins.cupidbot.valetotems;

import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.Script;
import net.runelite.client.plugins.cupidbot.valetotems.enums.GameObjectId;
import net.runelite.client.plugins.cupidbot.valetotems.enums.GameState;
import net.runelite.client.plugins.cupidbot.valetotems.enums.TotemLocation;
import net.runelite.client.plugins.cupidbot.valetotems.enums.TotemLocation.RouteType;
import net.runelite.client.plugins.cupidbot.valetotems.handlers.*;
import net.runelite.client.plugins.cupidbot.valetotems.models.GameSession;
import net.runelite.client.plugins.cupidbot.valetotems.models.TotemProgress;
import net.runelite.client.plugins.cupidbot.valetotems.utils.InventoryUtils;
import net.runelite.client.plugins.cupidbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.RandomUtils;

public class ValeTotemScript extends Script {

    private ValeTotemConfig config;
    private GameSession gameSession;
    private boolean isRunning = false;
    private long lastStateLogTime = 0;

    public boolean run(ValeTotemConfig config) {
        this.config = config;
        CupidBot.enableAutoRunOn = false;

        // Initialize configuration for all handlers and utilities
        InventoryUtils.setConfig(config);
        FletchingHandler.setConfig(config);
        BankingHandler.setConfig(config);

        // Initialize game session
        gameSession = new GameSession();
        isRunning = true;

        String logType = config.logType().getDisplayName();
        String bowType = config.bowType().getDisplayName();
        System.out.println("Vale Totems bot started!");
        System.out.println("Configuration: Using " + logType + " to make " + bowType + "s");

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!CupidBot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!isRunning) return;

                // Main bot logic
                executeMainLoop();

                long endTime = System.currentTimeMillis();

                // Update status every few iterations
                if ((endTime / 1000) % 10 == 0) { // Every 10 seconds
                    updateStatus();
                }

            } catch (Exception ex) {
                System.err.println("Error in main loop: " + ex.getMessage());
                gameSession.addError(ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Main bot execution logic - state machine approach
     */
    private void executeMainLoop() {
        GameState currentState = gameSession.getCurrentState();
        long now = System.currentTimeMillis();
        if (now - lastStateLogTime > 5000) {
            lastStateLogTime = now;
            CupidBot.log("[ValeTotem] State: " + currentState.name() + " | Player: " + net.runelite.client.plugins.cupidbot.valetotems.utils.CoordinateUtils.getPlayerLocation());
        }

        switch (currentState) {
            case IDLE:
                handleIdleState();
                break;
            case BANKING:
                handleBankingState();
                break;
            case NAVIGATING_TO_TOTEM:
                handleNavigationState();
                break;
            case BUILDING_TOTEM:
                handleBuildingState();
                break;
            case IDENTIFYING_ANIMALS:
                handleIdentifyingState();
                break;
            case CARVING_TOTEM:
                handleCarvingState();
                break;
            case FLETCHING:
                handleFletchingState();
                break;
            case DECORATING_TOTEM:
                handleDecoratingState();
                break;
            case COLLECTING_REWARDS:
                handleRewardCollectionState();
                break;
            case RETURNING_TO_BANK:
                handleReturningToBankState();
                break;
            case ERROR:
                handleErrorState();
                break;
            case STOPPING:
                handleStoppingState();
                break;
            case COMPLETED:
                handleCompletedState();
                break;
        }
    }

    /**
     * Handle idle state - always go to banking
     */
    private void handleIdleState() {
        System.out.println("Idle state - going to bank for restocking");
        gameSession.setState(GameState.BANKING);
    }

    /**
     * Handle banking operations
     */
    private void handleBankingState() {
        CupidBot.log("[ValeTotem] handleBankingState: starting unified banking cycle");
        boolean bankingSuccess = BankingHandler.performUnifiedBankingCycle(gameSession);
        CupidBot.log("[ValeTotem] handleBankingState: result=" + bankingSuccess);

        if (bankingSuccess) {
            gameSession.startNewRound();
            gameSession.setState(GameState.NAVIGATING_TO_TOTEM);

            // Set current totem to first totem of the detected route
            TotemLocation.RouteType currentRouteType = gameSession.getCurrentRouteType();
            gameSession.setCurrentTotem(TotemLocation.getFirst(currentRouteType));

            // Calculate reward collection frequency
            RewardHandler.COLLECTION_FREQUENCY = Math.max(1, config.collectOfferingsFrequency() - 2 + RandomUtils.nextInt(1, 4));
            CupidBot.log("Reward collection frequency set to: " + RewardHandler.COLLECTION_FREQUENCY);

            // Log the route being used
            CupidBot.log("Using " + currentRouteType.getDescription() + " with " + currentRouteType.getMaxTotems() + " totems");
        } else {
            gameSession.addError("Banking failed");
        }
    }

    /**
     * Handle navigation to totem locations
     */
    private void handleNavigationState() {
        TotemLocation currentTotem = gameSession.getCurrentTotem();
        if (currentTotem == null) {
            gameSession.addError("No current totem set");
            return;
        }

        // Check if navigation is complete
        if (NavigationHandler.navigateToTotem(currentTotem, gameSession)) {
            gameSession.setState(GameState.BUILDING_TOTEM);
            gameSession.setNavigationInProgress(false);
            FletchingHandler.stopFletchingWhileWalking(); // Stop fletching when we arrive
        }
    }

    /**
     * Handle totem building
     */
    private void handleBuildingState() {
        TotemLocation currentTotem = gameSession.getCurrentTotem();
        TotemProgress progress = gameSession.getCurrentTotemProgress();

        // Sync progress with the actual in-game totem state first.
        GameObjectId currentState = TotemHandler.checkTotemState(currentTotem);
        System.out.println("Initial totem state at " + currentTotem.getDescription() + ": " + (currentState != null ? currentState.name() : "UNKNOWN"));

        if (currentState == GameObjectId.EMPTY_TOTEM) {
            if (!progress.isBaseBuilt()) {
                System.out.println("Detected existing totem base. Updating progress.");
                progress.setBaseBuilt(true);
            }
        }

        if (TotemHandler.buildTotemBase(currentTotem, progress)) {
            gameSession.setState(GameState.IDENTIFYING_ANIMALS);
        } else {
            gameSession.addError("Failed to build totem base");
        }
    }

    /**
     * Handle spirit animal identification
     */
    private void handleIdentifyingState() {
        TotemLocation currentTotem = gameSession.getCurrentTotem();
        TotemProgress progress = gameSession.getCurrentTotemProgress();

        if (TotemHandler.identifySpiritAnimals(currentTotem, progress)) {
            gameSession.setState(GameState.CARVING_TOTEM);
        } else {
            gameSession.addError("Failed to identify spirit animals");
        }
    }

    /**
     * Handle animal carving
     */
    private void handleCarvingState() {
        TotemLocation currentTotem = gameSession.getCurrentTotem();
        TotemProgress progress = gameSession.getCurrentTotemProgress();

        if (TotemHandler.carveAnimalsIntoTotem(currentTotem, progress)) {
            gameSession.setState(GameState.FLETCHING);
        } else {
            gameSession.addError("Failed to carve animals");
        }
    }

    /**
     * Handle fletching operations
     */
    private void handleFletchingState() {
        TotemProgress progress = gameSession.getCurrentTotemProgress();

        // Check if we need to fletch more bows
        if (!progress.areEnoughBowsFletched()) {
            if (FletchingHandler.fletchBowsForOneTotem(gameSession)) {
                CupidBot.log("Fletched bows");
            } else {
                gameSession.addError("Failed to fletch bows");
                return;
            }
        }

        gameSession.setState(GameState.DECORATING_TOTEM);
    }

    /**
     * Handle totem decoration
     */
    private void handleDecoratingState() {
        TotemLocation currentTotem = gameSession.getCurrentTotem();
        TotemProgress progress = gameSession.getCurrentTotemProgress();

        if (TotemHandler.decorateTotem(currentTotem, progress)) {
            gameSession.setState(GameState.COLLECTING_REWARDS);
        } else {
            gameSession.addError("Failed to decorate totem");
        }
    }

    /**
     * Handle reward collection
     */
    private void handleRewardCollectionState() {
        System.out.println("Collecting rewards");
        TotemLocation currentTotem = gameSession.getCurrentTotem();
        TotemProgress progress = gameSession.getCurrentTotemProgress();

        // Try to collect offerings
        if (RewardHandler.collectOfferings(currentTotem, progress, gameSession)) {
            gameSession.incrementOfferingsCollected();
        }

        // Move to next totem or return to bank
        if (currentTotem.isLast()) {
            gameSession.setNavigationInProgress(false); // Reset navigation tracking
            gameSession.setState(GameState.RETURNING_TO_BANK);
        } else {
            gameSession.setNavigationInProgress(false); // Reset navigation tracking
            gameSession.moveToNextTotem();
            gameSession.setState(GameState.NAVIGATING_TO_TOTEM);
        }
    }

    /**
     * Handle returning to bank after completing all totems
     */
    private void handleReturningToBankState() {
        // Initialize bank return navigation (only on first attempt)
        if (!gameSession.isNavigationInProgress()) {
            gameSession.setNavigationInProgress(true);
        }

        // Check if navigation to bank is complete
        if (NavigationHandler.navigateToBank()) {
            gameSession.setNavigationInProgress(false);
            gameSession.completeRound();

            // Check if we should continue or stop
            if (InventoryUtils.shouldReturnToBank()) {
                gameSession.setState(GameState.BANKING);
            } else {
                gameSession.setState(GameState.COMPLETED);
            }
        }
    }

    /**
     * Handle error states
     */
    private void handleErrorState() {
        CupidBot.log("[ValeTotem] ERROR state. Errors so far: " + gameSession.getErrorMessages().size() + ". Attempting recovery...");

        FletchingHandler.emergencyStopFletching();
        NavigationHandler.emergencyReturnToBank();

        //Reset all totem progress
        gameSession.resetTotemProgressForAllLocations();

        // Reset to banking state to try recovery
        gameSession.setState(GameState.BANKING);

        // If too many errors, stop the bot
        if (gameSession.getErrorMessages().size() > 10) {
            System.err.println("Too many errors. Stopping bot.");
            shutdown();
        }
    }

    /**
     * Handle stopping state - gracefully shutdown on critical errors
     */
    private void handleStoppingState() {
        System.out.println("Critical error detected - initiating graceful shutdown...");

        try {
            // Close bank if open
            if (Rs2Bank.isOpen()) {
                System.out.println("Closing bank before shutdown...");
                Rs2Bank.closeBank();
                sleep(1000); // Wait for bank to close
            }

            // Stop any fletching operations
            FletchingHandler.emergencyStopFletching();

        } catch (Exception e) {
            System.err.println("Error during graceful shutdown: " + e.getMessage());
        } finally {
            // Ensure bot stops regardless of errors
            isRunning = false;
            if (gameSession != null) {
                gameSession.setActive(false);
            }
            shutdown();
        }
    }

    /**
     * Handle completed state
     */
    private void handleCompletedState() {
        RouteType currentRoute = gameSession.getCurrentRouteType();
        System.out.println("Round completed using " + currentRoute.getDescription() + "! Starting new round...");
        gameSession.resetTotemProgressForAllLocations();
        gameSession.setState(GameState.IDLE);
    }

    /**
     * Update status display
     */
    private void updateStatus() {
        RouteType currentRoute = gameSession.getCurrentRouteType();
        System.out.println("=== Vale Totems Bot Status ===");
        System.out.println("Route: " + currentRoute.getDescription() + " (" + currentRoute.getMaxTotems() + " totems)");
        System.out.println("Log Basket: " + (InventoryUtils.hasLogBasket() ? "Present" : "Not Present"));
        System.out.println(gameSession.toString());
        System.out.println(InventoryUtils.getInventorySummary());
        System.out.println(FletchingHandler.getFletchingStatus());
        System.out.println(NavigationHandler.getNavigationStatus());
        System.out.println(RewardHandler.getRewardStatistics(gameSession));
        System.out.println("==============================");
    }

    @Override
    public void shutdown() {
        System.out.println("Vale Totems bot shutting down...");

        // Clean up
        if (gameSession != null) {
            gameSession.setActive(false);
        }

        FletchingHandler.emergencyStopFletching();
        isRunning = false;

        super.shutdown();
        CupidBot.log("Vale Totems bot stopped.");
    }

    /**
     * Get the current game session for external access
     * @return current game session
     */
    public GameSession getGameSession() {
        return gameSession;
    }

    /**
     * Check if the bot is currently running
     * @return true if running
     */
    public boolean isRunning() {
        return isRunning && gameSession != null && gameSession.isActive();
    }
}