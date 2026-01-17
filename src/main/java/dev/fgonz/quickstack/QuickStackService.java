package dev.fgonz.quickstack;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Core logic for moving items from player inventory to nearby containers.
 * Scans blocks within a configurable radius and transfers matching item stacks.
 */
public class QuickStackService {

    private final QuickStackConfig config;

    public QuickStackService(QuickStackConfig config) {
        this.config = config;
    }

    public QuickStackConfig getConfig() {
        return config;
    }

    /**
     * Represents the result of a Quick Stack operation.
     */
    public static class StackResult {
        private final int containersChecked;
        private final Map<String, Integer> movedItems;

        public StackResult(int containersChecked, Map<String, Integer> movedItems) {
            this.containersChecked = containersChecked;
            this.movedItems = movedItems;
        }

        public int getContainersChecked() { return containersChecked; }
        public Map<String, Integer> getMovedItems() { return movedItems; }
        public boolean hasMovedItems() { return !movedItems.isEmpty(); }
    }

    /**
     * Scans for containers around the player and moves matching items into them.
     * Containers are processed nearest-first to prioritize closer storage.
     */
    public CompletableFuture<StackResult> performQuickStack(Player player) {
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

        CompletableFuture<StackResult> future = new CompletableFuture<>();

        world.execute(() -> {
            try {
                int uniqueContainers = 0;
                Map<String, Integer> movedItemsSummary = new HashMap<>();
                HashSet<ItemContainer> seenContainers = new HashSet<>();
                ArrayList<Vector3i> containerPositions = new ArrayList<>();

                for (int x = startX; x <= endX; x++) {
                    for (int y = startY; y <= endY; y++) {
                        for (int z = startZ; z <= endZ; z++) {
                            try {
                                Object state = world.getState(x, y, z, true);
                                if (state instanceof ItemContainerState) {
                                    containerPositions.add(new Vector3i(x, y, z));
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }

                final double originX = origin.x;
                final double originY = origin.y;
                final double originZ = origin.z;

                try {
                    containerPositions.sort(Comparator.comparingDouble(p -> {
                        double dx = p.x - originX;
                        double dy = p.y - originY;
                        double dz = p.z - originZ;
                        return (dx * dx) + (dy * dy) + (dz * dz);
                    }));
                } catch (Throwable t) {
                   System.err.println("Error sorting containers");
                   t.printStackTrace();
                }

                ItemContainer backpack = playerInventory.getBackpack();
                ItemContainer storage = playerInventory.getStorage();
                ItemContainer hotbar = playerInventory.getHotbar();

                for (Vector3i blockPos : containerPositions) {
                    try {
                        Object state = world.getState(blockPos.x, blockPos.y, blockPos.z, true);
                        if (!(state instanceof ItemContainerState containerState)) continue;

                        ItemContainer chestContainer = containerState.getItemContainer();
                        if (chestContainer == null || !seenContainers.add(chestContainer)) {
                            continue;
                        }
                        uniqueContainers++;

                        if (config.isCheckBackpack() && backpack != null) {
                            var tx = backpack.quickStackTo(chestContainer);
                            if (tx != null && tx.size() > 0 && tx.succeeded()) {
                                processTransaction(tx, movedItemsSummary);
                            }
                        }

                        if (config.isCheckStorage() && storage != null) {
                            var tx = storage.quickStackTo(chestContainer);
                            if (tx != null && tx.size() > 0 && tx.succeeded()) {
                                processTransaction(tx, movedItemsSummary);
                            }
                        }

                        if (config.isCheckHotbar() && hotbar != null) {
                            var tx = hotbar.quickStackTo(chestContainer);
                            if (tx != null && tx.size() > 0 && tx.succeeded()) {
                                processTransaction(tx, movedItemsSummary);
                            }
                        }

                    } catch (Throwable t) {
                        System.err.println("Error processing container at " + blockPos);
                        t.printStackTrace();
                    }
                }

                future.complete(new StackResult(uniqueContainers, movedItemsSummary));

            } catch (Throwable e) {
                System.err.println("Critical error in QuickStack task");
                e.printStackTrace();
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    private void processTransaction(ListTransaction<MoveTransaction<ItemStackTransaction>> txList, Map<String, Integer> summary) {
        if (txList == null) return;
        
        try {
            for (MoveTransaction<ItemStackTransaction> moveTx : txList.getList()) {
                if (moveTx.succeeded()) {
                    ItemStackTransaction itemTx = moveTx.getAddTransaction();
                    if (itemTx != null) {
                        ItemStack query = itemTx.getQuery();
                        ItemStack remainder = itemTx.getRemainder();
                        
                        if (query != null) {
                            int queryQty = query.getQuantity();
                            int remainderQty = (remainder != null) ? remainder.getQuantity() : 0;
                            int movedQty = queryQty - remainderQty;
                            
                            if (movedQty > 0) {
                                String rawName = query.getItemId();
                                String cleanName = getCleanItemName(rawName);
                                summary.put(cleanName, summary.getOrDefault(cleanName, 0) + movedQty);
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("Error processing transaction");
            t.printStackTrace();
        }
    }

    /**
     * Extracts item name from namespaced id (e.g. "hytale:stone" -> "stone").
     */
    private String getCleanItemName(String itemId) {
        if (itemId == null) return "Unknown";
        try {
            if (itemId.contains(":")) {
                String[] parts = itemId.split(":");
                if (parts.length > 1) {
                    return parts[1];
                }
            }
            return itemId;
        } catch (Exception e) {
            return itemId;
        }
    }
}
