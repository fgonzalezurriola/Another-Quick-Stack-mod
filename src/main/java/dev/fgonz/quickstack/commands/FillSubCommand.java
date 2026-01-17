package dev.fgonz.quickstack.commands;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;

import dev.fgonz.quickstack.BenchFillService;
import dev.fgonz.quickstack.handlers.BenchFillHandler;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Subcommand: /qs fill [type]
 * Fills nearby ProcessingBench blocks with relevant items.
 * 
 * Usage:
 *   /qs fill          - Fill ALL bench types
 *   /qs fill furnace  - Fill only furnaces (aliases: f, smelt, s)
 *   /qs fill tannery  - Fill only tanneries (aliases: t, tan)
 */
public class FillSubCommand extends AbstractAsyncCommand {

    private final BenchFillService service;
    private final OptionalArg<String> benchTypeArg;

    public FillSubCommand(BenchFillService service) {
        super("fill", "Fill nearby processing benches with items");
        this.addAliases("f");
        this.setPermissionGroup(GameMode.Adventure);
        this.service = service;

        this.benchTypeArg = withOptionalArg(
            "type", 
            "Bench type to fill (furnace, tannery, or leave empty for all)", 
            ArgTypes.STRING
        );
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        if (!context.isPlayer()) {
            return CompletableFuture.completedFuture(null);
        }

        Player player = (Player) context.sender();

        try {
            // Parse optional filter
            BenchFillHandler filterHandler = null;
            String typeFilter = context.get(benchTypeArg);

            if (typeFilter != null && !typeFilter.isEmpty()) {
                filterHandler = service.findHandlerByAlias(typeFilter);
                if (filterHandler == null) {
                    String validTypes = service.getHandlers().stream()
                        .map(h -> h.getAliases()[0])
                        .collect(Collectors.joining(", "));
                    player.sendMessage(Message.raw(
                        "[QuickStack] Unknown type '" + typeFilter + "'. Valid: " + validTypes
                    ));
                    return CompletableFuture.completedFuture(null);
                }
            }

            String scanMsg = filterHandler != null 
                ? "[QuickStack] Scanning for " + filterHandler.getDisplayName() + "..."
                : "[QuickStack] Scanning for all processing benches...";
            player.sendMessage(Message.raw(scanMsg));

            final BenchFillHandler finalFilter = filterHandler;

            return service.performFill(player, finalFilter).thenAccept(result -> {
                String msg;
                if (result.hasMovedItems()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[QuickStack] Done!");

                    // Show benches processed by type
                    if (!result.getBenchesByType().isEmpty()) {
                        sb.append(" Benches:");
                        for (Map.Entry<String, Integer> e : result.getBenchesByType().entrySet()) {
                            sb.append(" ").append(e.getValue()).append("x ").append(e.getKey());
                        }
                    }

                    sb.append("\nMoved:");
                    for (Map.Entry<String, Integer> entry : result.getMovedItems().entrySet()) {
                        sb.append("\n  [x").append(entry.getValue()).append("] ").append(entry.getKey());
                    }
                    msg = sb.toString();
                    player.sendInventory();
                } else {
                    msg = "[QuickStack] Nothing moved. Found " + result.getBenchesProcessed() + " bench(es).";
                }
                player.sendMessage(Message.raw(msg));

            }).exceptionally(e -> {
                handleException(player, e);
                return null;
            });

        } catch (Throwable t) {
            handleException(player, t);
            return CompletableFuture.completedFuture(null);
        }
    }

    private void handleException(Player player, Throwable e) {
        System.err.println("[QuickStack Fill] Error: " + e.getMessage());
        e.printStackTrace();
        player.sendMessage(Message.raw("[QuickStack] Error: " + e.getMessage()));
    }
}
