package dev.fgonz.quickstack.handlers;

import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

/**
 * Strategy interface for filling different types of ProcessingBench.
 * Implement this interface to add support for new bench types.
 */
public interface BenchFillHandler {

    /**
     * @return The bench ID as defined in game data (e.g., "Furnace", "Tannery")
     */
    String getBenchId();

    /**
     * @return User-friendly name for display in messages
     */
    String getDisplayName();

    /**
     * @return Aliases that users can type to filter this bench type
     *         e.g., ["f", "smelt", "s"] for Furnace
     */
    String[] getAliases();

    /**
     * Check if an item should be transferred as INPUT (the item to be processed)
     * @param itemId Full item ID (e.g., "hytale:Ore_Iron")
     * @return true if this item is valid input for this bench
     */
    boolean isValidInput(String itemId);

    /**
     * Check if an item should be transferred as FUEL
     * @param itemId Full item ID
     * @return true if valid fuel, false if bench doesn't use fuel or item isn't fuel
     */
    boolean isValidFuel(String itemId);

    /**
     * Determine if the bench should be activated after filling.
     * Typically checks if bench has both input and fuel (if required).
     * @param container The bench's item container
     * @return true if bench should be activated
     */
    boolean shouldActivate(ItemContainer container);

    /**
     * Check if an item is relevant to this handler (input OR fuel)
     */
    default boolean isRelevantItem(String itemId) {
        return isValidInput(itemId) || isValidFuel(itemId);
    }
}
