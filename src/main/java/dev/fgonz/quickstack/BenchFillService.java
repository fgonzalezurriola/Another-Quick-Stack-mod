package dev.fgonz.quickstack;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.builtin.crafting.state.ProcessingBenchState;

import dev.fgonz.quickstack.handlers.BenchFillHandler;
import dev.fgonz.quickstack.handlers.FurnaceFillHandler;
import dev.fgonz.quickstack.handlers.TanneryFillHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Unified service for filling ProcessingBench blocks using registered handlers.
 * Extensible: add new BenchFillHandler implementations for new bench types.
 */
public class BenchFillService {

    private final QuickStackConfig config;
    private final List<BenchFillHandler> handlers;

    public BenchFillService(QuickStackConfig config) {
        this.config = config;
        this.handlers = new ArrayList<>();

        // Register default handlers
        registerHandler(new FurnaceFillHandler());
        registerHandler(new TanneryFillHandler());
    }

    public void registerHandler(BenchFillHandler handler) {
        handlers.add(handler);
    }

    public List<BenchFillHandler> getHandlers() {
        return handlers;
    }

    /**
     * Find a handler by alias (user input like "furnace", "f", "tan", etc.)
     * @return handler or null if not found
     */
    public BenchFillHandler findHandlerByAlias(String alias) {
        if (alias == null) return null;
        String lower = alias.toLowerCase();
        for (BenchFillHandler handler : handlers) {
            for (String a : handler.getAliases()) {
                if (a.equalsIgnoreCase(lower)) {
                    return handler;
                }
            }
        }
        return null;
    }

    public static class FillResult {
        private final int benchesProcessed;
        private final Map<String, Integer> movedItems;
        private final Map<String, Integer> benchesByType;

        public FillResult(int benchesProcessed, Map<String, Integer> movedItems, 
                         Map<String, Integer> benchesByType) {
            this.benchesProcessed = benchesProcessed;
            this.movedItems = movedItems;
            this.benchesByType = benchesByType;
        }

        public int getBenchesProcessed() { return benchesProcessed; }
        public Map<String, Integer> getMovedItems() { return movedItems; }
        public Map<String, Integer> getBenchesByType() { return benchesByType; }
        public boolean hasMovedItems() { return !movedItems.isEmpty(); }
        public int getTotalMoved() {
            return movedItems.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /**
     * Fill nearby ProcessingBench blocks.
     * @param player The player
     * @param filterHandler If not null, only process benches matching this handler
     */
    public CompletableFuture<FillResult> performFill(Player player, BenchFillHandler filterHandler) {
        World world = player.getWorld();
        Inventory playerInventory = player.getInventory();
        int radius = config.getSearchRadius();

        Vector3d position;
        try {
            position = player.getTransformComponent().getPosition();
        } catch (Throwable t) {
            position = new Vector3d(0, 64, 0);
        }

        final Vector3d origin = position;
        int startX = (int) Math.floor(origin.x) - radius;
        int endX = (int) Math.floor(origin.x) + radius;
        int startY = (int) Math.floor(origin.y) - radius;
        int endY = (int) Math.floor(origin.y) + radius;
        int startZ = (int) Math.floor(origin.z) - radius;
        int endZ = (int) Math.floor(origin.z) + radius;

        CompletableFuture<FillResult> future = new CompletableFuture<>();

        world.execute(() -> {
            try {
                int benchCount = 0;
                Map<String, Integer> movedItems = new HashMap<>();
                Map<String, Integer> benchesByType = new HashMap<>();
                HashSet<ItemContainer> seenContainers = new HashSet<>();
                ArrayList<Vector3i> benchPositions = new ArrayList<>();

                // Scan for ProcessingBenchState blocks
                for (int x = startX; x <= endX; x++) {
                    for (int y = startY; y <= endY; y++) {
                        for (int z = startZ; z <= endZ; z++) {
                            try {
                                Object state = world.getState(x, y, z, true);
                                if (state instanceof ProcessingBenchState) {
                                    benchPositions.add(new Vector3i(x, y, z));
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }

                // Sort by distance
                final double originX = origin.x;
                final double originY = origin.y;
                final double originZ = origin.z;
                benchPositions.sort(Comparator.comparingDouble(p -> {
                    double dx = p.x - originX;
                    double dy = p.y - originY;
                    double dz = p.z - originZ;
                    return (dx * dx) + (dy * dy) + (dz * dz);
                }));

                ItemContainer backpack = playerInventory.getBackpack();
                ItemContainer storage = playerInventory.getStorage();

                for (Vector3i blockPos : benchPositions) {
                    try {
                        Object state = world.getState(blockPos.x, blockPos.y, blockPos.z, true);

                        if (!(state instanceof ProcessingBenchState bench)) {
                            continue;
                        }

                        ItemContainer container = bench.getItemContainer();
                        if (container == null || !seenContainers.add(container)) {
                            continue;
                        }

                        // Find handler for this bench type
                        BenchFillHandler handler = findHandlerForBench(bench);
                        if (handler == null) {
                            continue;
                        }

                        // Apply filter if specified
                        if (filterHandler != null && handler != filterHandler) {
                            continue;
                        }

                        benchCount++;
                        benchesByType.merge(handler.getDisplayName(), 1, Integer::sum);

                        // Transfer items from enabled inventory sections
                        if (config.isCheckBackpack() && backpack != null) {
                            transferItems(backpack, container, handler, movedItems);
                        }
                        if (config.isCheckStorage() && storage != null) {
                            transferItems(storage, container, handler, movedItems);
                        }

                        // Activate if conditions are met
                        if (!bench.isActive() && handler.shouldActivate(container)) {
                            bench.setActive(true);
                        }

                    } catch (Throwable t) {
                        System.err.println("[BenchFill] Error at " + blockPos + ": " + t.getMessage());
                    }
                }

                future.complete(new FillResult(benchCount, movedItems, benchesByType));

            } catch (Throwable e) {
                System.err.println("[BenchFill] Critical error");
                e.printStackTrace();
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    private BenchFillHandler findHandlerForBench(ProcessingBenchState bench) {
        String benchId = null;
        try {
            benchId = bench.getBench().getId();
        } catch (Throwable ignored) {}

        if (benchId != null) {
            for (BenchFillHandler handler : handlers) {
                if (handler.getBenchId().equalsIgnoreCase(benchId)) {
                    return handler;
                }
            }
        }

        return null;
    }

    private void transferItems(ItemContainer source, ItemContainer target,
                              BenchFillHandler handler, Map<String, Integer> movedSummary) {
        try {
            short capacity = source.getCapacity();

            for (short slot = 0; slot < capacity; slot++) {
                try {
                    ItemStack stack = source.getItemStack(slot);
                    if (stack == null) continue;

                    String itemId = stack.getItemId();
                    if (itemId == null) continue;

                    if (handler.isRelevantItem(itemId)) {
                        int quantityBefore = stack.getQuantity();

                        var tx = source.moveItemStackFromSlot(slot, target);

                        if (tx != null && tx.succeeded()) {
                            String cleanName = getCleanItemName(itemId);
                            int moved = quantityBefore;

                            var addTx = tx.getAddTransaction();
                            if (addTx != null) {
                                ItemStack remainder = addTx.getRemainder();
                                if (remainder != null) {
                                    moved = quantityBefore - remainder.getQuantity();
                                }
                            }

                            if (moved > 0) {
                                movedSummary.merge(cleanName, moved, Integer::sum);
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            System.err.println("[BenchFill] Transfer error: " + t.getMessage());
        }
    }

    private String getCleanItemName(String itemId) {
        if (itemId == null) return "Unknown";
        if (itemId.contains(":")) {
            String[] parts = itemId.split(":");
            if (parts.length > 1) {
                return parts[1];
            }
        }
        return itemId;
    }
}
