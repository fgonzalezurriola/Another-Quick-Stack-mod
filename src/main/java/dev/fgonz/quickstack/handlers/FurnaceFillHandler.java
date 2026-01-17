package dev.fgonz.quickstack.handlers;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

/**
 * Handler for Furnace bench - processes ores using fuel.
 */
public class FurnaceFillHandler implements BenchFillHandler {

    @Override
    public String getBenchId() {
        return "Furnace";
    }

    @Override
    public String getDisplayName() {
        return "Furnace";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"furnace", "f", "smelt", "s"};
    }

    @Override
    public boolean isValidInput(String itemId) {
        if (itemId == null) return false;
        return itemId.contains(":Ore_") || 
               itemId.startsWith("Ore_") || 
               itemId.contains("_Ore_");
    }

    @Override
    public boolean isValidFuel(String itemId) {
        if (itemId == null) return false;
        String cleanId = getCleanItemName(itemId);

        // Wood trunks (logs)
        if (cleanId.startsWith("Wood_") && cleanId.endsWith("_Trunk")) {
            return true;
        }

        // Wooden planks
        if (cleanId.startsWith("Wood_") && cleanId.endsWith("_Planks")) {
            return true;
        }

        // Wooden beams
        if (cleanId.startsWith("Wood_") && cleanId.endsWith("_Beam")) {
            return true;
        }

        // Sticks
        if (cleanId.equals("Wood_Sticks") || cleanId.equals("Ingredient_Stick")) {
            return true;
        }

        // Charcoal and Coal
        if (cleanId.equals("Ingredient_Charcoal") || cleanId.equals("Ingredient_Coal")) {
            return true;
        }

        return false;
    }

    @Override
    public boolean shouldActivate(ItemContainer container) {
        boolean hasInput = false;
        boolean hasFuel = false;

        try {
            short capacity = container.getCapacity();
            for (short slot = 0; slot < capacity; slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (stack == null) continue;

                String itemId = stack.getItemId();
                if (itemId == null) continue;

                if (isValidInput(itemId)) {
                    hasInput = true;
                }
                if (isValidFuel(itemId)) {
                    hasFuel = true;
                }

                if (hasInput && hasFuel) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        return hasInput && hasFuel;
    }

    private String getCleanItemName(String itemId) {
        if (itemId == null) return "";
        if (itemId.contains(":")) {
            String[] parts = itemId.split(":");
            if (parts.length > 1) {
                return parts[1];
            }
        }
        return itemId;
    }
}
