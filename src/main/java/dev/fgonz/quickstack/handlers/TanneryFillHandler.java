package dev.fgonz.quickstack.handlers;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

/**
 * Handler for Tannery bench - processes hides into leather.
 * Does NOT use fuel - only requires valid input items.
 */
public class TanneryFillHandler implements BenchFillHandler {

    @Override
    public String getBenchId() {
        return "Tannery";
    }

    @Override
    public String getDisplayName() {
        return "Tannery";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"tannery", "t", "tan", "hide", "leather"};
    }

    @Override
    public boolean isValidInput(String itemId) {
        if (itemId == null) return false;
        String cleanId = getCleanItemName(itemId);

        // Raw hides - the input for tanning
        // Matches: Ingredient_Hide_Light, Ingredient_Hide_Heavy, etc.
        return cleanId.startsWith("Ingredient_Hide_");
    }

    @Override
    public boolean isValidFuel(String itemId) {
        // Tannery does NOT use fuel
        return false;
    }

    @Override
    public boolean shouldActivate(ItemContainer container) {
        // Tannery only needs input items, no fuel required
        try {
            short capacity = container.getCapacity();
            for (short slot = 0; slot < capacity; slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (stack == null) continue;

                String itemId = stack.getItemId();
                if (itemId == null) continue;

                if (isValidInput(itemId)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        return false;
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
