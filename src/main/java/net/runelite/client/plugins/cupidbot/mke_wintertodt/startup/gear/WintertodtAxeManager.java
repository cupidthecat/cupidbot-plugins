package net.runelite.client.plugins.cupidbot.mke_wintertodt.startup.gear;

import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.cupidbot.CupidBot;
import net.runelite.client.plugins.cupidbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.cupidbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.cupidbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.cupidbot.util.player.Rs2Player;

import java.util.Arrays;
import java.util.List;

import static net.runelite.client.plugins.cupidbot.util.Global.sleepGaussian;
import static net.runelite.client.plugins.cupidbot.util.Global.sleepUntilTrue;

/**
 * Intelligent axe management system that automatically determines optimal axe usage.
 * Considers player levels, available gear, and inventory optimization.
 * Also handles bruma torch conversion to offhand version when optimal.
 *
 * @author MakeCD
 * @version 2.0.0
 */
public class WintertodtAxeManager {

    // Axe priority list (best to worst) - updated with correct requirements
    private static final List<AxeInfo> AXE_PRIORITY = Arrays.asList(
        new AxeInfo(ItemID.INFERNAL_AXE, "Infernal axe", 61, 60), // WC 61, ATK 60
        new AxeInfo(ItemID.CRYSTAL_AXE, "Crystal axe", 71, 70),   // WC 71, ATK 70
        new AxeInfo(ItemID.DRAGON_AXE, "Dragon axe", 61, 60),    // WC 61, ATK 60
        new AxeInfo(ItemID.RUNE_AXE, "Rune axe", 41, 40),        // WC 41, ATK 40
        new AxeInfo(ItemID.ADAMANT_AXE, "Adamant axe", 31, 30),  // WC 31, ATK 30
        new AxeInfo(ItemID.MITHRIL_AXE, "Mithril axe", 21, 20),  // WC 21, ATK 20
        new AxeInfo(ItemID.BLACK_AXE, "Black axe", 11, 10),      // WC 11, ATK 10
        new AxeInfo(ItemID.STEEL_AXE, "Steel axe", 6, 5),        // WC 6, ATK 5
        new AxeInfo(ItemID.IRON_AXE, "Iron axe", 1, 1),          // WC 1, ATK 1
        new AxeInfo(ItemID.BRONZE_AXE, "Bronze axe", 1, 1)       // WC 1, no ATK req
    );

    // Bruma torch variants
    private static final List<Integer> BRUMA_TORCH_ITEMS = Arrays.asList(
        ItemID.BRUMA_TORCH,
        ItemID.BRUMA_TORCH_OFFHAND
    );

    private static class AxeInfo {
        final int itemId;
        final String name;
        final int wcLevel;
        final int attackLevel;

        AxeInfo(int itemId, String name, int wcLevel, int attackLevel) {
            this.itemId = itemId;
            this.name = name;
            this.wcLevel = wcLevel;
            this.attackLevel = attackLevel;
        }

        boolean canUse(int playerWc, int playerAttack) {
            return playerWc >= wcLevel && playerAttack >= attackLevel;
        }

        boolean canWield(int playerAttack) {
            return playerAttack >= attackLevel;
        }
    }

    /**
     * Determines the optimal axe configuration automatically using database-driven approach.
     * @return AxeDecision containing the best setup
     */
    public static AxeDecision determineOptimalAxeSetup() {
        try {
            CupidBot.log("Analyzing optimal axe configuration using intelligent system...");

            int wcLevel = Rs2Player.getRealSkillLevel(Skill.WOODCUTTING);
            int attackLevel = Rs2Player.getRealSkillLevel(Skill.ATTACK);

            CupidBot.log("Player levels - Woodcutting: " + wcLevel + ", Attack: " + attackLevel);

            // Find the best available axe
            AxeInfo bestAxe = findBestAvailableAxe(wcLevel, attackLevel);
            if (bestAxe == null) {
                CupidBot.log("No suitable axe found!");
                return new AxeDecision(false, ItemID.BRONZE_AXE, "No axe available", false, false);
            }

            // Check bruma torch situation
            BrumaTorchAnalysis torchAnalysis = analyzeBrumaTorchSituation();
            CupidBot.log("Bruma torch analysis: " + torchAnalysis.toString());

            // Determine if axe should be equipped or kept in inventory
            boolean shouldEquipAxe = determineOptimalAxePlacement(bestAxe, attackLevel, torchAnalysis);

            String reasoning = buildDetailedReasoning(bestAxe, attackLevel, torchAnalysis, shouldEquipAxe);

            CupidBot.log("Axe decision: " + reasoning);

            return new AxeDecision(
                shouldEquipAxe,
                bestAxe.itemId,
                bestAxe.name,
                bestAxe.canWield(attackLevel),
                torchAnalysis.needsConversion
            );

        } catch (Exception e) {
            CupidBot.log("Error determining axe setup: " + e.getMessage());
            e.printStackTrace();
            return new AxeDecision(false, ItemID.BRONZE_AXE, "Error fallback", false, false);
        }
    }

    /**
     * Enhanced bruma torch analysis with more detailed logic.
     */
    private static BrumaTorchAnalysis analyzeBrumaTorchSituation() {
        int firLevel = Rs2Player.getRealSkillLevel(Skill.FIREMAKING);

        // Check firemaking level requirement
        if (firLevel < 50) {
            return new BrumaTorchAnalysis(false, false, false,
                "Insufficient Firemaking level (have " + firLevel + ", need 50)");
        }

        // Check if we already have offhand bruma torch (optimal)
        if (hasAccess(ItemID.BRUMA_TORCH_OFFHAND)) {
            return new BrumaTorchAnalysis(true, false, true,
                "Already have offhand bruma torch (optimal setup)");
        }

        // Check if we have regular bruma torch that should be converted
        if (hasAccess(ItemID.BRUMA_TORCH)) {
            return new BrumaTorchAnalysis(true, true, false,
                "Have regular bruma torch - should convert to offhand for optimal inventory management");
        }

        return new BrumaTorchAnalysis(false, false, false,
            "No bruma torch available (will use tinderbox)");
    }

    /**
     * Enhanced logic for determining optimal axe placement.
     */
    private static boolean determineOptimalAxePlacement(AxeInfo bestAxe, int attackLevel, BrumaTorchAnalysis torchAnalysis) {
        // Can't equip if insufficient attack level
        if (!bestAxe.canWield(attackLevel)) {
            CupidBot.log("Cannot wield " + bestAxe.name + " (need " + bestAxe.attackLevel + " Attack, have " + attackLevel + ")");
            return false;
        }

        // Special case: Bronze axe has no attack requirement, always equip if possible
        if (bestAxe.itemId == ItemID.BRONZE_AXE) {
            CupidBot.log("Bronze axe - equipping (no attack requirement)");
            return true;
        }

        // If we have/will have bruma torch offhand, definitely equip axe for optimal setup
        if (torchAnalysis.hasBrumaTorch) {
            if (torchAnalysis.hasOffhand) {
                CupidBot.log("Optimal setup: " + bestAxe.name + " + bruma torch offhand");
            } else {
                CupidBot.log("Will be optimal after conversion: " + bestAxe.name + " + bruma torch offhand");
            }
            return true;
        }

        // No bruma torch - analyze inventory efficiency
        // Equipped axe + tinderbox in inventory is generally better than axe in inventory + tinderbox
        // Both scenarios use same total slots, but equipped axe allows for more flexible inventory management
        CupidBot.log("No bruma torch available - equipping " + bestAxe.name + " for better inventory efficiency");
        return true;
    }

    /**
     * Finds the best axe considering both requirements and availability.
     */
    private static AxeInfo findBestAvailableAxe(int wcLevel, int attackLevel) {
        CupidBot.log("Searching for best available axe...");

        for (AxeInfo axe : AXE_PRIORITY) {
            // Must meet woodcutting requirement at minimum
            if (wcLevel < axe.wcLevel) {
                continue;
            }

            // Check availability
            if (hasAccess(axe.itemId)) {
                String canWield = axe.canWield(attackLevel) ? "can wield" : "inventory only";
                CupidBot.log("Found: " + axe.name + " (" + canWield + ")");
                return axe;
            }
        }

        CupidBot.log("No suitable axe found in inventory/bank/equipment");
        return null;
    }

    /**
     * Enhanced access checking with detailed logging.
     */
    private static boolean hasAccess(int itemId) {
        if (Rs2Equipment.isWearing(itemId)) {
            return true;
        }

        if (Rs2Inventory.hasItem(itemId)) {
            return true;
        }

        if (Rs2Bank.isOpen() && Rs2Bank.hasBankItem(itemId, 1)) {
            return true;
        }

        return false;
    }

    /**
     * Builds detailed reasoning for the axe decision.
     */
    private static String buildDetailedReasoning(AxeInfo bestAxe, int attackLevel, BrumaTorchAnalysis torchAnalysis, boolean shouldEquipAxe) {
        StringBuilder sb = new StringBuilder();
        sb.append(bestAxe.name);

        if (!bestAxe.canWield(attackLevel)) {
            sb.append(" (inventory only - need ").append(bestAxe.attackLevel).append(" Attack, have ").append(attackLevel).append(")");
        } else if (shouldEquipAxe) {
            sb.append(" (equipped");

            if (torchAnalysis.hasOffhand) {
                sb.append(" + bruma torch offhand) ← OPTIMAL");
            } else if (torchAnalysis.needsConversion) {
                sb.append(" + bruma torch offhand after conversion) ← WILL BE OPTIMAL");
            } else if (torchAnalysis.hasBrumaTorch) {
                sb.append(" + bruma torch) ← GOOD");
            } else {
                sb.append(" + tinderbox) ← STANDARD");
            }
        } else {
            sb.append(" (inventory + tinderbox) ← SUBOPTIMAL BUT NECESSARY");
        }

        return sb.toString();
    }

    /**
     * Enhanced bruma torch conversion with better error handling and logging.
     */
    public static boolean performBrumaTorchConversion() {
        try {
            BrumaTorchAnalysis analysis = analyzeBrumaTorchSituation();

            if (!analysis.needsConversion) {
                if (analysis.hasOffhand) {
                    CupidBot.log("Bruma torch already in optimal offhand form");
                } else {
                    CupidBot.log("ℹ No bruma torch conversion needed");
                }
                return true;
            }

            CupidBot.log("Starting bruma torch conversion to offhand version...");

            boolean bankWasOpen = Rs2Bank.isOpen();

            // Ensure we have the regular bruma torch in inventory
            if (!ensureBrumaTorchInInventory()) {
                CupidBot.log("Failed to get bruma torch into inventory for conversion");
                return false;
            }

            // Close bank for the conversion process
            if (Rs2Bank.isOpen()) {
                CupidBot.log("Closing bank for bruma torch conversion...");
                Rs2Bank.closeBank();
                sleepUntilTrue(() -> !Rs2Bank.isOpen(), 100, 3000);
                sleepGaussian(600, 200); // Extra delay for stability
            }

            // Perform the actual conversion
            if (!Rs2Inventory.hasItem(ItemID.BRUMA_TORCH)) {
                CupidBot.log("Bruma torch disappeared from inventory");
                return reopenBankAndReturn(bankWasOpen, false);
            }

            CupidBot.log("Converting bruma torch to offhand version...");

            if (Rs2Inventory.interact(ItemID.BRUMA_TORCH, "Swap")) {
                boolean converted = sleepUntilTrue(() ->
                    Rs2Inventory.hasItem(ItemID.BRUMA_TORCH_OFFHAND), 100, 6000);

                if (converted) {
                    CupidBot.log("Successfully converted bruma torch to offhand version!");
                    return reopenBankAndReturn(bankWasOpen, true);
                } else {
                    CupidBot.log("Bruma torch conversion timed out");
                    return reopenBankAndReturn(bankWasOpen, false);
                }
            } else {
                CupidBot.log("Failed to interact with bruma torch for conversion");
                return reopenBankAndReturn(bankWasOpen, false);
            }

        } catch (Exception e) {
            CupidBot.log("Error during bruma torch conversion: " + e.getMessage());
            e.printStackTrace();

            // Try to restore bank state
            try {
                reopenBankAndReturn(true, false);
            } catch (Exception reopenException) {
                CupidBot.log("Failed to reopen bank after conversion error");
            }

            return false;
        }
    }

    /**
     * Helper method to reopen bank and return result.
     */
    private static boolean reopenBankAndReturn(boolean shouldReopenBank, boolean result) {
        if (shouldReopenBank && !Rs2Bank.isOpen()) {
            try {
                CupidBot.log("Reopening bank after conversion...");
                if (!Rs2Inventory.isOpen()) {
                    Rs2Inventory.open();
                }
                Rs2Bank.openBank();
                sleepUntilTrue(() -> Rs2Bank.isOpen(), 100, 3000);
            } catch (Exception e) {
                CupidBot.log("Failed to reopen bank: " + e.getMessage());
            }
        }
        return result;
    }

    /**
     * Enhanced method to ensure bruma torch is in inventory.
     */
    private static boolean ensureBrumaTorchInInventory() {
        try {
            // Already in inventory
            if (Rs2Inventory.hasItem(ItemID.BRUMA_TORCH)) {
                CupidBot.log("Bruma torch already in inventory");
                return true;
            }

            // Check if equipped and unequip
            if (Rs2Equipment.isWearing(ItemID.BRUMA_TORCH)) {
                CupidBot.log("Unequipping bruma torch for conversion...");
                Rs2Equipment.unEquip(net.runelite.api.EquipmentInventorySlot.WEAPON);
                sleepUntilTrue(() -> Rs2Inventory.hasItem(ItemID.BRUMA_TORCH), 100, 3000);

                if (Rs2Inventory.hasItem(ItemID.BRUMA_TORCH)) {
                    CupidBot.log("Successfully unequipped bruma torch");
                    return true;
                } else {
                    CupidBot.log("Failed to unequip bruma torch");
                    return false;
                }
            }

            // Check bank and withdraw
            if (Rs2Bank.isOpen() && Rs2Bank.hasBankItem(ItemID.BRUMA_TORCH, 1)) {
                CupidBot.log("Withdrawing bruma torch from bank...");
                Rs2Bank.withdrawX(ItemID.BRUMA_TORCH, 1);
                sleepUntilTrue(() -> Rs2Inventory.hasItem(ItemID.BRUMA_TORCH), 100, 3000);

                if (Rs2Inventory.hasItem(ItemID.BRUMA_TORCH)) {
                    CupidBot.log("Successfully withdrew bruma torch");
                    return true;
                } else {
                    CupidBot.log("Failed to withdraw bruma torch");
                    return false;
                }
            }

            CupidBot.log("Cannot find bruma torch to convert");
            return false;

        } catch (Exception e) {
            CupidBot.log("Error ensuring bruma torch in inventory: " + e.getMessage());
            return false;
        }
    }

    /**
     * Enhanced bruma torch analysis result.
     */
    private static class BrumaTorchAnalysis {
        final boolean hasBrumaTorch;
        final boolean needsConversion;
        final boolean hasOffhand;
        final String reason;

        BrumaTorchAnalysis(boolean hasBrumaTorch, boolean needsConversion, boolean hasOffhand, String reason) {
            this.hasBrumaTorch = hasBrumaTorch;
            this.needsConversion = needsConversion;
            this.hasOffhand = hasOffhand;
            this.reason = reason;
        }

        @Override
        public String toString() {
            return reason;
        }
    }

    /**
     * Enhanced axe decision result with better methods.
     */
    public static class AxeDecision {
        private final boolean shouldEquip;
        private final int axeId;
        private final String axeName;
        private final boolean canWield;
        private final boolean needsTorchConversion;

        public AxeDecision(boolean shouldEquip, int axeId, String axeName, boolean canWield, boolean needsTorchConversion) {
            this.shouldEquip = shouldEquip;
            this.axeId = axeId;
            this.axeName = axeName;
            this.canWield = canWield;
            this.needsTorchConversion = needsTorchConversion;
        }

        public boolean shouldEquipAxe() { return shouldEquip; }
        public boolean shouldKeepInInventory() { return !shouldEquip; }
        public int getAxeId() { return axeId; }
        public String getAxeName() { return axeName; }
        public boolean canWieldAxe() { return canWield; }
        public boolean needsBrumaTorchConversion() { return needsTorchConversion; }

        public boolean isOptimalSetup() {
            return shouldEquip && !needsTorchConversion;
        }

        @Override
        public String toString() {
            return String.format("AxeDecision{axe=%s, equip=%s, canWield=%s, convertTorch=%s}",
                               axeName, shouldEquip, canWield, needsTorchConversion);
        }
    }
}