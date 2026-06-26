package net.runelite.client.plugins.cupidbot.valetotems.handlers;

import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.valetotems.ValeTotemConfig;
import net.runelite.client.plugins.cupidbot.valetotems.enums.BankLocation;
import net.runelite.client.plugins.cupidbot.valetotems.enums.GameObjectId;
import net.runelite.client.plugins.cupidbot.valetotems.enums.GameState;
import net.runelite.client.plugins.cupidbot.valetotems.utils.CoordinateUtils;
import net.runelite.client.plugins.cupidbot.valetotems.utils.GameObjectUtils;
import net.runelite.client.plugins.cupidbot.valetotems.utils.InventoryUtils;
import net.runelite.client.plugins.cupidbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.cupidbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.cupidbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.cupidbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;
import net.runelite.client.plugins.cupidbot.util.walker.Rs2Walker;

import java.util.List;

import static net.runelite.client.plugins.cupidbot.util.Global.sleep;
import static net.runelite.client.plugins.cupidbot.util.Global.sleepGaussian;
import static net.runelite.client.plugins.cupidbot.util.Global.sleepUntil;

import java.util.Random;
import net.runelite.client.plugins.cupidbot.valetotems.enums.TotemLocation;
import net.runelite.client.plugins.cupidbot.valetotems.models.GameSession;
import org.slf4j.event.Level;

/**
 * Handles all banking operations for the Vale Totems minigame
 */
public class BankingHandler {

    private static final Random random = new Random();
    private static final double SHORT_AFK_PROBABILITY = 0.40; // 40% chance
    private static final double MEDIUM_AFK_PROBABILITY = 0.30; // 30% chance
    private static final double LONG_AFK_PROBABILITY = 0.10; // 10% chance

    private static final int BANK_INTERACTION_DISTANCE = 5;
    private static final long BANK_TIMEOUT_MS = 10000; // 10 seconds

    // Configuration instance
    private static ValeTotemConfig config;

    /**
     * Set the configuration for this handler
     * @param config the ValeTotemConfig instance
     */
    public static void setConfig(ValeTotemConfig config) {
        BankingHandler.config = config;
    }

    /**
     * Navigate to the bank and position correctly for banking
     * @return true if successfully positioned at bank
     */
    public static boolean navigateToBank() {
        try {
            boolean alreadyNear = CoordinateUtils.isNearBank(BANK_INTERACTION_DISTANCE);
            CupidBot.log("[Banking] navigateToBank: alreadyNear=" + alreadyNear + " pos=" + CoordinateUtils.getPlayerLocation());

            if (alreadyNear) {
                if (!CoordinateUtils.isAtBankingPosition()) {
                    CupidBot.log("[Banking] Walking to optimal banking tile");
                    return Rs2Walker.walkTo(BankLocation.PLAYER_STANDING_TILE.getLocation());
                }
                return true;
            }

            CupidBot.log("[Banking] Walking to bank booth at " + BankLocation.BANK_BOOTH.getLocation());
            boolean walked = Rs2Walker.walkTo(BankLocation.BANK_BOOTH.getLocation());
            CupidBot.log("[Banking] walkTo bank result=" + walked);
            if (walked) {
                Rs2Player.waitForWalking();
                return Rs2Walker.walkTo(BankLocation.PLAYER_STANDING_TILE.getLocation());
            }

            return false;
        } catch (Exception e) {
            CupidBot.log("[Banking] Error navigating to bank: " + e.getMessage());
            return false;
        }
    }

    /**
     * Open the bank interface
     * @return true if bank interface is open
     */
    public static boolean openBank() {
        try {
            if (Rs2Bank.isOpen()) {
                CupidBot.log("[Banking] Bank already open");
                return true;
            }

            if (!CoordinateUtils.isNearBank(BANK_INTERACTION_DISTANCE)) {
                CupidBot.log("[Banking] openBank: not near bank, navigating first");
                if (!navigateToBank()) {
                    return false;
                }
            }

            CupidBot.log("[Banking] Interacting with bank booth (id=" + GameObjectId.BANK_BOOTH.getId() + " at " + BankLocation.BANK_BOOTH.getLocation() + ")");
            boolean interacted = GameObjectUtils.findAndInteractAtLocation(
                    GameObjectId.BANK_BOOTH.getId(),
                    BankLocation.BANK_BOOTH.getLocation(),
                    "Bank"
            );
            CupidBot.log("[Banking] Bank booth interact result=" + interacted);

            if (interacted) {
                long startTime = System.currentTimeMillis();
                while (!Rs2Bank.isOpen() && System.currentTimeMillis() - startTime < BANK_TIMEOUT_MS) {
                    sleep(100);
                }
                boolean opened = Rs2Bank.isOpen();
                CupidBot.log("[Banking] Bank opened=" + opened);
                return opened;
            }

            return false;
        } catch (Exception e) {
            CupidBot.log("[Banking] Error opening bank: " + e.getMessage());
            return false;
        }
    }

    /**
     * Deposit all items except knife
     * @return true if successfully deposited items
     */
    public static boolean depositAllExceptKnife() {
        try {
            if (!Rs2Bank.isOpen() && !openBank()) {
                return false;
            }

            // Deposit all except knife
            boolean deposited = Rs2Bank.depositAllExcept(InventoryUtils.KNIFE_ID, InventoryUtils.FLETCHING_KNIFE_ID);

            if (deposited) {
                sleep(1300);
            }

            return true;
        } catch (Exception e) {
            CupidBot.log("Error depositing items: " + e.getMessage());
            return false;
        }
    }

        /**
     * Withdraw the required items for the minigame
     * Note: This function assumes materials have already been pre-checked
     * @param gameSession the game session to update if critical errors occur
     * @return true if successfully withdrew required items
     */
    public static boolean withdrawRequiredItems(GameSession gameSession) {
        try {
            // Bank should already be open from prior checks, but ensure it is
            if (!Rs2Bank.isOpen() && !openBank()) {
                CupidBot.log("Cannot withdraw items - failed to open bank");
                return false;
            }

            // Calculate how many logs to withdraw
            int logsToWithdraw = InventoryUtils.getOptimalLogWithdrawAmount();

            if (logsToWithdraw <= 0) {
                CupidBot.log("Already have sufficient logs in inventory - no withdrawal needed");
                return true; // Already have enough materials
            }

            // Get the configured log type ID
            int logId = InventoryUtils.getLogId();
            String logTypeName = config != null ? config.logType().getDisplayName() : "Yew Logs";

            // Rs2Bank.withdrawX only queues the menu click — it returns immediately and the item
            // can lag several ticks behind. Generic waitForInventoryChanges + hasRequiredItems
            // was racing and reporting shortages right after a successful withdraw.
            CupidBot.log("Withdrawing " + logsToWithdraw + " " + logTypeName + " from bank");
            int logsBefore = InventoryUtils.getLogCount();
            int expectedLogs = logsBefore + logsToWithdraw;
            if (!Rs2Bank.withdrawX(logId, logsToWithdraw)) {
                CupidBot.log("Failed to queue log withdrawal");
                return false;
            }
            if (!sleepUntil(() -> InventoryUtils.getLogCount() >= expectedLogs, 3000)) {
                CupidBot.log("Log withdrawal timed out (have " + InventoryUtils.getLogCount() + "/" + expectedLogs + ")");
                return false;
            }
            CupidBot.log("Successfully withdrew " + logsToWithdraw + " " + logTypeName);
            return InventoryUtils.hasRequiredItems();

        } catch (Exception e) {
            CupidBot.log("Error withdrawing required items: " + e.getMessage());
            return false;
        }
    }

    public static boolean fixZoom() {
        try {
            Rs2Camera.setZoom(0);
            return true;
        } catch (Exception e) {
            CupidBot.log("Error fixing zoom: " + e.getMessage());
            return false;
        }
    }

    /**
     * Detect which route type to use based on inventory and bank contents
     * This method ensures the bank is open before checking for log basket
     * @return RouteType.EXTENDED if log basket is present, RouteType.STANDARD otherwise
     */
    public static TotemLocation.RouteType detectRouteTypeWithBankOpen() {
        try {
            // Check inventory first
            if (InventoryUtils.hasLogBasket()) {
                CupidBot.log("Log basket detected in inventory - enabling Extended Route (8 totems)");
                return TotemLocation.RouteType.EXTENDED;
            }

            // Ensure bank is open before checking
            if (!Rs2Bank.isOpen() && !openBank()) {
                CupidBot.log("Could not open bank for route detection - defaulting to Standard Route");
                return TotemLocation.RouteType.STANDARD;
            }

            // Check bank for log basket
            if (Rs2Bank.hasItem(InventoryUtils.LOG_BASKET_ID)) {
                CupidBot.log("Log basket detected in bank - enabling Extended Route (8 totems)");
                return TotemLocation.RouteType.EXTENDED;
            }

            CupidBot.log("No log basket detected in inventory or bank - using Standard Route (5 totems)");
            return TotemLocation.RouteType.STANDARD;

        } catch (Exception e) {
            CupidBot.log("Error detecting route type: " + e.getMessage());
            CupidBot.log("Defaulting to Standard Route due to error");
            return TotemLocation.RouteType.STANDARD;
        }
    }

    /**
     * Perform a unified banking cycle that detects route type and calls appropriate banking method
     * @param gameSession the game session to update with detected route type
     * @return true if banking cycle completed successfully
     */
    public static boolean performUnifiedBankingCycle(GameSession gameSession) {
        try {
            var playerPos = CoordinateUtils.getPlayerLocation();
            int distToBank = CoordinateUtils.getDistanceToBank();
            boolean nearBank = CoordinateUtils.isNearBank(BANK_INTERACTION_DISTANCE);
            CupidBot.log("[Banking] Start cycle. Player=" + playerPos + " distToBank=" + distToBank + " nearBank=" + nearBank);

            if (!nearBank) {
                CupidBot.log("[Banking] Not near bank, navigating...");
                boolean navResult = navigateToBank();
                CupidBot.log("[Banking] Navigate result=" + navResult + " newPos=" + CoordinateUtils.getPlayerLocation());
                if (!navResult) {
                    return false;
                }
            }

            CupidBot.log("[Banking] Opening bank...");
            if (!openBank()) {
                CupidBot.log("[Banking] Failed to open bank");
                return false;
            }
            CupidBot.log("[Banking] Bank opened successfully");

            // Now detect route type with bank open
            TotemLocation.RouteType detectedRouteType = detectRouteTypeWithBankOpen();

            // Update game session with detected route type
            if (gameSession.getCurrentRouteType() != detectedRouteType) {
                gameSession.setRouteType(detectedRouteType);
            }

            // Handle random AFK
            handleRandomAFK();

            boolean bankingSuccess = false;

            // Call appropriate banking method based on detected route type
            if (detectedRouteType == TotemLocation.RouteType.EXTENDED) {
                CupidBot.log("Performing extended route banking cycle");
                bankingSuccess = performExtendedRouteBankingOperations(gameSession);
            } else {
                CupidBot.log("Performing standard route banking cycle");
                bankingSuccess = performStandardBankingOperations(gameSession);
            }

            if (bankingSuccess) {
                // Close bank
                Rs2Bank.closeBank();
                fixZoom();
                CupidBot.log("Banking cycle completed successfully using " + detectedRouteType.getDescription());
            }

            return bankingSuccess;

        } catch (Exception e) {
            CupidBot.log("Error during unified banking cycle: " + e.getMessage());
            return false;
        }
    }

    /**
     * Perform standard route banking operations (assumes bank is already open)
     * @return true if standard banking operations completed successfully
     */
    private static boolean performStandardBankingOperations(GameSession gameSession) {
        try {
            // Step 1: Ensure we have a knife in inventory
            if (!ensureKnifeInInventory(gameSession)) {
                CupidBot.log("Failed to ensure knife availability for standard route");
                return false;
            }

            // Step 2: Deposit all except knife (and stamina potion if we have one)
            if (!depositAllExceptKnife()) {
                CupidBot.log("Failed to deposit items for standard route");
                return false;
            }

            // Step 3: Check if we have enough materials
            if (!hasSufficientMaterials(1)) {
                CupidBot.log("Not enough materials for standard route - stopping bot");
                gameSession.setState(GameState.STOPPING);
                return false;
            }

            // Step 4: Withdraw required items
            if (!withdrawRequiredItems(gameSession)) {
                CupidBot.log("Failed to withdraw required items for standard route");
                return false;
            }

            // Step 5: Handle stamina potion withdrawal if enabled
            if (!handleStaminaPotionWithdrawal()) {
                CupidBot.log("Warning: Failed to handle stamina potions - continuing without");
            }

            return true;
        } catch (Exception e) {
            CupidBot.log("Error during standard banking operations: " + e.getMessage());
            return false;
        }
    }

    /**
     * Perform extended route banking operations (assumes bank is already open)
     * @return true if extended banking operations completed successfully
     */
    private static boolean performExtendedRouteBankingOperations(GameSession gameSession) {
        try {
            CupidBot.log("Starting extended route banking operations");

            // Step 1: Ensure we have knife and log basket in inventory
            if (!ensureKnifeAndLogBasketInInventory(gameSession)) {
                CupidBot.log("Failed to ensure knife and log basket are in inventory");
                return false;
            }

            // Step 2: Empty log basket if this is initial banking or log type changed
            if (InventoryUtils.shouldEmptyLogBasket(gameSession)) {
                CupidBot.log("Emptying log basket for fresh start");
                if (!InventoryUtils.emptyLogBasket(gameSession)) {
                    CupidBot.log("Failed to empty log basket");
                    return false;
                }
            }

            // Step 3: Check if we have enough materials for extended route
            if (!hasSufficientMaterialsForExtendedRoute(gameSession, 1)) {
                CupidBot.log("Not enough materials for extended route - stopping bot");
                gameSession.setState(GameState.STOPPING);
                return false;
            }

            // Step 4: Deposit all items except knife and log basket
            if (!depositAllExceptKnifeAndLogBasket()) {
                CupidBot.log("Failed to deposit items while preserving knife and log basket");
                return false;
            }

            // Step 5: Perform log basket filling operation
            if (!performLogBasketFillingOperation(gameSession)) {
                CupidBot.log("Failed to perform log basket filling operation");
                return false;
            }

            // Step 6: Handle stamina potion withdrawal if enabled
            if (!handleStaminaPotionWithdrawal()) {
                CupidBot.log("Warning: Failed to handle stamina potions - continuing without");
            }

            CupidBot.log("Extended route banking operations completed successfully");
            return true;

        } catch (Exception e) {
            CupidBot.log("Error during extended route banking operations: " + e.getMessage());
            return false;
        }
    }

    /**
     * Handles random AFK scenarios while banking
     */
    private static void handleRandomAFK() {
        double roll = random.nextDouble(); // Generates a value between 0.0 and 1.0

        if (roll < LONG_AFK_PROBABILITY) {
            // Long AFK: 30 to 90 seconds
            CupidBot.log("Performing a long AFK...");
            CupidBot.status = "Long AFK";
            Rs2Antiban.moveMouseOffScreen(90);
            sleep(30000, 90000);
        } else if (roll < MEDIUM_AFK_PROBABILITY) {
            // Medium AFK: 10 to 30 seconds
            CupidBot.log("Performing a medium AFK...");
            CupidBot.status = "Medium AFK";
            Rs2Antiban.moveMouseOffScreen(90);
            sleep(10000, 30000);
        } else if (roll < SHORT_AFK_PROBABILITY) {
            // Short AFK: 2 to 8 seconds
            CupidBot.log("Performing a short AFK...");
            CupidBot.status = "Short AFK";
            Rs2Antiban.moveMouseOffScreen(60);
            sleep(2000, 8000);
        }
    }

    /**
     * Check if we have enough materials in bank for continued operation
     * @param roundsPlanned number of rounds we want to complete
     * @return true if bank has sufficient materials
     */
    public static boolean hasSufficientMaterials(int roundsPlanned) {
        try {
            if (!Rs2Bank.isOpen() && !openBank()) {
                CupidBot.log("Unable to check materials - cannot open bank");
                return false;
            }

            boolean hasSufficientMaterials = true;
            StringBuilder shortageDetails = new StringBuilder();

            // Check for knife
            boolean hasKnifeAvailable = InventoryUtils.hasKnife() ||
                Rs2Bank.hasItem(InventoryUtils.KNIFE_ID) ||
                Rs2Bank.hasItem(InventoryUtils.FLETCHING_KNIFE_ID);

            if (!hasKnifeAvailable) {
                hasSufficientMaterials = false;
                shortageDetails.append("• No knife available (inventory or bank)\n");
            }

            // Check for logs (25 logs per round for standard route)
            int logsNeeded = roundsPlanned * 25;
            int currentLogs = InventoryUtils.getLogCount();
            int logId = InventoryUtils.getLogId();
            int bankLogs = Rs2Bank.count(logId);
            int totalLogsAvailable = currentLogs + bankLogs;

            if (totalLogsAvailable < logsNeeded) {
                hasSufficientMaterials = false;
                String logTypeName = config != null ? config.logType().getDisplayName() : "Yew Logs";
                shortageDetails.append(String.format("• Logs: Have %d, Need %d (%s)\n",
                    totalLogsAvailable, logsNeeded, logTypeName));
            }

            // Log material status
            if (hasSufficientMaterials) {
                CupidBot.log("Material check passed - sufficient materials for " + roundsPlanned + " rounds");
            } else {
                CupidBot.log("=== MATERIAL SHORTAGE DETECTED ===");
                CupidBot.log("Standard Route Requirements:");
                CupidBot.log(shortageDetails.toString().trim());
                CupidBot.log("==================================");
                CupidBot.showMessage("Material Shortage: " + shortageDetails.toString().trim());
            }

            return hasSufficientMaterials;

        } catch (Exception e) {
            CupidBot.log("Error checking bank materials: " + e.getMessage());
            return false;
        }
    }

    /**
     * Handle critical material shortage by setting game state to STOPPING
     * @param gameSession the game session to update
     * @param missingItem description of what material is missing
     */
    private static void handleCriticalMaterialShortage(GameSession gameSession, String missingItem) {
        // Log critical error with clear formatting
        CupidBot.log("=== CRITICAL MATERIAL SHORTAGE DETECTED ===");
        CupidBot.log("Issue: " + missingItem);
        CupidBot.log("Bot Status: Stopping due to insufficient materials");
        CupidBot.log("Action Required: Please restock materials in bank before restarting");
        CupidBot.log("=============================================");

        CupidBot.showMessage("Critical Material Shortage: " + missingItem);

        // Update game session state
        gameSession.setState(GameState.STOPPING);
    }


    /**
     * Check if we have sufficient materials in bank for extended route
     * @param gameSession the game session that tracks log basket contents
     * @param roundsPlanned number of extended route rounds planned
     * @return true if bank has sufficient materials for extended route
     */
    public static boolean hasSufficientMaterialsForExtendedRoute(net.runelite.client.plugins.cupidbot.valetotems.models.GameSession gameSession, int roundsPlanned) {
        try {
            if (!Rs2Bank.isOpen() && !openBank()) {
                CupidBot.log("Unable to check materials for extended route - cannot open bank");
                return false;
            }

            boolean hasSufficientMaterials = true;
            StringBuilder shortageDetails = new StringBuilder();

            // Check for knife
            boolean hasKnifeAvailable = InventoryUtils.hasKnife() ||
                Rs2Bank.hasItem(InventoryUtils.KNIFE_ID) ||
                Rs2Bank.hasItem(InventoryUtils.FLETCHING_KNIFE_ID);

            if (!hasKnifeAvailable) {
                hasSufficientMaterials = false;
                shortageDetails.append("• No knife available (inventory or bank)\n");
            }

            // Check for log basket
            boolean hasLogBasket = InventoryUtils.hasLogBasket();
            if (!hasLogBasket) {
                hasSufficientMaterials = false;
                shortageDetails.append("• No log basket available (inventory or bank)\n");
            }

            // Check for logs (40 logs per extended route round - 8 totems * 5 each)
            int logsNeeded = roundsPlanned * 40;
            int currentLogs = InventoryUtils.getLogCount();
            int logId = InventoryUtils.getLogId();
            int bankLogs = Rs2Bank.count(logId);
            int logBasketLogs = InventoryUtils.getLogBasketLogCount(gameSession);
            int totalLogsAvailable = currentLogs + bankLogs + logBasketLogs;

            if (totalLogsAvailable < logsNeeded) {
                hasSufficientMaterials = false;
                String logTypeName = config != null ? config.logType().getDisplayName() : "Yew Logs";
                shortageDetails.append(String.format("• Logs: Have %d (inventory: %d, bank: %d, basket: %d), Need %d (%s)\n",
                    totalLogsAvailable, currentLogs, bankLogs, logBasketLogs, logsNeeded, logTypeName));
            }

            // Log material status
            if (hasSufficientMaterials) {
                CupidBot.log("Extended route material check passed - sufficient materials");
            } else {
                CupidBot.log("=== MATERIAL SHORTAGE DETECTED (EXTENDED ROUTE) ===");
                CupidBot.log("Extended Route Requirements (8 totems per round):");
                CupidBot.log(shortageDetails.toString().trim());
                CupidBot.log("==================================================");
                CupidBot.showMessage("Material Shortage: " + shortageDetails.toString().trim());
            }

            return hasSufficientMaterials;

        } catch (Exception e) {
            CupidBot.log("Error checking bank materials for extended route: " + e.getMessage());
            return false;
        }
    }

    /**
     * Withdraw log basket from bank if available and not in inventory
     * @return true if log basket is now available (either was in inventory or successfully withdrawn)
     */
    public static boolean ensureLogBasketAvailable() {
        try {
            // Check if already in inventory
            if (InventoryUtils.hasLogBasket()) {
                return true;
            }

            // Check if bank is open
            if (!Rs2Bank.isOpen() && !openBank()) {
                return false;
            }

            // Check if log basket is in bank and withdraw it
            if (Rs2Bank.hasItem(InventoryUtils.LOG_BASKET_ID)) {
                CupidBot.log("Withdrawing log basket from bank for extended route");
                if (!Rs2Bank.withdrawOne(InventoryUtils.LOG_BASKET_ID)) {
                    CupidBot.log("Failed to queue log basket withdrawal");
                    return false;
                }
                return sleepUntil(InventoryUtils::hasLogBasket, 3000);
            }

            CupidBot.log("No log basket found in bank");
            return false;

        } catch (Exception e) {
            CupidBot.log("Error ensuring log basket availability: " + e.getMessage());
            return false;
        }
    }

    /**
     * Deposit all items except knife and log basket (for extended route)
     * @return true if successfully deposited items
     */
    public static boolean depositAllExceptKnifeAndLogBasket() {
        try {
            if (!Rs2Bank.isOpen() && !openBank()) {
                return false;
            }

            // Deposit all except knife and log basket
            boolean deposited = Rs2Bank.depositAllExcept(
                InventoryUtils.KNIFE_ID,
                InventoryUtils.FLETCHING_KNIFE_ID,
                InventoryUtils.LOG_BASKET_ID
            );

            if (deposited) {
                Rs2Inventory.waitForInventoryChanges(3000);
            }

            return true;
        } catch (Exception e) {
            CupidBot.log("Error depositing items (preserving knife and log basket): " + e.getMessage());
            return false;
        }
    }

    /**
     * Ensure knife is available in inventory (prioritizes fletching knife)
     * @param gameSession the game session to update if critical errors occur
     * @return true if knife is available in inventory
     */
    private static boolean ensureKnifeInInventory(GameSession gameSession) {
        try {
            if (!Rs2Bank.isOpen() && !openBank()) {
                CupidBot.log("Cannot ensure knife availability - failed to open bank");
                return false;
            }

            // Check if we already have a knife in inventory
            if (InventoryUtils.hasKnife()) {
                return true;
            }

            // Try to withdraw fletching knife first (prioritized).
            // Rs2Bank.withdrawOne returns true as soon as the click is queued; we must wait
            // on the concrete predicate (hasKnife), not a generic inventory-change wait,
            // or a slow server tick causes a stale "no knife" read right after we withdrew —
            // the code then used to fall through and try the regular knife, which isn't in
            // the bank either, and report a critical shortage on a knife that was in-flight.
            if (Rs2Bank.hasItem(InventoryUtils.FLETCHING_KNIFE_ID)) {
                CupidBot.log("Withdrawing Fletching knife from bank (prioritized)");
                if (Rs2Bank.withdrawOne(InventoryUtils.FLETCHING_KNIFE_ID)) {
                    if (sleepUntil(InventoryUtils::hasKnife, 3000)) {
                        CupidBot.log("Successfully withdrew Fletching knife");
                        return true;
                    }
                    CupidBot.log("Fletching knife withdrawal timed out");
                    return false;
                }
                CupidBot.log("Failed to queue Fletching knife withdrawal");
            }

            // Try to withdraw regular knife as fallback
            if (Rs2Bank.hasItem(InventoryUtils.KNIFE_ID)) {
                CupidBot.log("Withdrawing regular knife from bank");
                if (Rs2Bank.withdrawOne(InventoryUtils.KNIFE_ID)) {
                    if (sleepUntil(InventoryUtils::hasKnife, 3000)) {
                        CupidBot.log("Successfully withdrew regular knife");
                        return true;
                    }
                    CupidBot.log("Regular knife withdrawal timed out");
                    return false;
                }
                CupidBot.log("Failed to queue regular knife withdrawal");
            }

            // No knife found anywhere - critical error
            handleCriticalMaterialShortage(gameSession, "No knife available (checked inventory and bank for both Fletching knife and regular knife)");
            return false;

        } catch (Exception e) {
            CupidBot.log("Error ensuring knife in inventory: " + e.getMessage());
            return false;
        }
    }

    /**
     * Ensure knife and log basket are in inventory (for extended route)
     * @param gameSession the game session to update if critical errors occur
     * @return true if both items are available in inventory
     */
    private static boolean ensureKnifeAndLogBasketInInventory(GameSession gameSession) {
        try {
            if (!Rs2Bank.isOpen() && !openBank()) {
                CupidBot.log("Cannot ensure knife and log basket availability - failed to open bank");
                return false;
            }

            // Ensure we have a knife (prioritizing fletching knife). Wait on the concrete
            // predicate so a slow inventory update doesn't get misread as "no knife in bank".
            if (!InventoryUtils.hasKnife()) {
                if (Rs2Bank.hasItem(InventoryUtils.FLETCHING_KNIFE_ID)) {
                    CupidBot.log("Withdrawing Fletching knife from bank (prioritized)");
                    if (!Rs2Bank.withdrawOne(InventoryUtils.FLETCHING_KNIFE_ID)) {
                        CupidBot.log("Failed to queue Fletching knife withdrawal");
                        return false;
                    }
                } else if (Rs2Bank.hasItem(InventoryUtils.KNIFE_ID)) {
                    CupidBot.log("Withdrawing regular knife from bank");
                    if (!Rs2Bank.withdrawOne(InventoryUtils.KNIFE_ID)) {
                        CupidBot.log("Failed to queue knife withdrawal");
                        return false;
                    }
                } else {
                    handleCriticalMaterialShortage(gameSession, "No knife available (checked inventory and bank for both Fletching knife and regular knife)");
                    return false;
                }
                if (!sleepUntil(InventoryUtils::hasKnife, 3000)) {
                    CupidBot.log("Knife withdrawal timed out");
                    return false;
                }
            }

            // Ensure we have a log basket
            if (!InventoryUtils.hasLogBasket()) {
                if (Rs2Bank.hasItem(InventoryUtils.LOG_BASKET_ID)) {
                    if (!Rs2Bank.withdrawOne(InventoryUtils.LOG_BASKET_ID)) {
                        CupidBot.log("Failed to queue log basket withdrawal");
                        return false;
                    }
                } else {
                    handleCriticalMaterialShortage(gameSession, "No log basket available (checked inventory and bank)");
                    return false;
                }
                if (!sleepUntil(InventoryUtils::hasLogBasket, 3000)) {
                    CupidBot.log("Log basket withdrawal timed out");
                    return false;
                }
            }

            return true;

        } catch (Exception e) {
            CupidBot.log("Error ensuring knife and log basket in inventory: " + e.getMessage());
            return false;
        }
    }

    private static boolean handleStaminaPotionWithdrawal() {
        if (config == null || !config.useStaminaPotions() || !Rs2Bank.isOpen()) {
            return true;
        }

        if (Rs2Inventory.hasItem(Rs2Potion.getStaminaPotion()) ||
            Rs2Inventory.hasItem(Rs2Potion.getRestoreEnergyPotionsVariants().toArray(String[]::new))) {
            return true;
        }

        InventoryUtils.depositEmptyVials();

        int currentEnergy = Rs2Player.getRunEnergy();
        int threshold = config.staminaThreshold();
        boolean needsDrink = currentEnergy <= threshold && !Rs2Player.hasStaminaBuffActive();

        String potionToWithdraw = findBestPotionInBank();
        if (potionToWithdraw == null) {
            CupidBot.log("No stamina or energy potions available in bank");
            return true;
        }

        if (needsDrink) {
            CupidBot.log("Run energy low - withdrawing and drinking potion");
            Rs2Bank.withdrawOne(potionToWithdraw);
            Rs2Inventory.waitForInventoryChanges(1800);
            if (Rs2Inventory.hasItem(potionToWithdraw)) {
                Rs2Inventory.interact(potionToWithdraw, "drink");
                Rs2Inventory.waitForInventoryChanges(1800);
                InventoryUtils.depositEmptyVials();
            }
        } else {
            CupidBot.log("Withdrawing potion for navigation");
            Rs2Bank.withdrawOne(potionToWithdraw);
            Rs2Inventory.waitForInventoryChanges(1800);
        }

        return true;
    }

    private static String findBestPotionInBank() {
        if (Rs2Bank.hasItem(Rs2Potion.getStaminaPotion())) {
            return Rs2Potion.getStaminaPotion();
        }

        List<String> energyVariants = Rs2Potion.getRestoreEnergyPotionsVariants();
        Rs2ItemModel energyPotion = Rs2Bank.bankItems().stream()
                .filter(item -> energyVariants.stream()
                        .anyMatch(variant -> item.getName().toLowerCase().contains(variant.toLowerCase())))
                .findFirst()
                .orElse(null);

        return energyPotion != null ? energyPotion.getName() : null;
    }

    /**
     * Perform the log basket filling operation according to user specifications:
     * 1. Take inventory full of logs
     * 2. Close bank
     * 3. Fill log basket
     * 4. Open bank
     * 5. Take rest of needed logs to inventory
     * @param gameSession the game session to update log basket tracking
     * @return true if operation completed successfully
     */
    private static boolean performLogBasketFillingOperation(net.runelite.client.plugins.cupidbot.valetotems.models.GameSession gameSession) {
        try {
            CupidBot.log("Starting log basket filling operation");

            // Step 1: Take inventory full of logs (leaving space for knife and basket)
            int logsToWithdraw = InventoryUtils.getOptimalLogBasketLogAmountForExtendedRoute(gameSession) - InventoryUtils.getLogCount();
            int logId = InventoryUtils.getLogId();
            int logsBeforeFirst = InventoryUtils.getLogCount();

            if (!Rs2Bank.withdrawX(logId, logsToWithdraw)) {
                CupidBot.log("Failed to queue log withdrawal for basket-fill step");
                return false;
            }

            int expectedAfterFirst = logsBeforeFirst + logsToWithdraw;
            if (!sleepUntil(() -> InventoryUtils.getLogCount() >= expectedAfterFirst, 3000)) {
                CupidBot.log("Log withdrawal timed out in basket-fill step");
                return false;
            }

            // Step 2: Close bank
            Rs2Bank.closeBank();

            sleepGaussian(500,300);

            // Step 3: Fill log basket with logs from inventory
            if (!InventoryUtils.fillLogBasket(gameSession)) {
                CupidBot.log("Failed to fill log basket");
                return false;
            }

            // Step 4: Open bank again
            if (!openBank()) {
                CupidBot.log("Failed to reopen bank after filling basket");
                return false;
            }

            // Step 5: Take rest of needed logs to inventory
            int logsStillNeeded = InventoryUtils.getOptimalLogAmountForExtendedRoute(gameSession);
            if (logsStillNeeded > 0) {
                CupidBot.log("Withdrawing additional " + logsStillNeeded + " logs for extended route");
                int logsBeforeSecond = InventoryUtils.getLogCount();
                if (!Rs2Bank.withdrawX(logId, logsStillNeeded)) {
                    CupidBot.log("Failed to queue additional log withdrawal");
                    return false;
                }
                int expectedAfterSecond = logsBeforeSecond + logsStillNeeded;
                if (!sleepUntil(() -> InventoryUtils.getLogCount() >= expectedAfterSecond, 3000)) {
                    CupidBot.log("Additional log withdrawal timed out");
                    return false;
                }
            }

            CupidBot.log("Log basket filling operation completed successfully");
            return true;

        } catch (Exception e) {
            CupidBot.log("Error during log basket filling operation: " + e.getMessage());
            return false;
        }
    }
}