package dev.fgonz.quickstack.commands;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;

import dev.fgonz.quickstack.QuickStackService;
import dev.fgonz.quickstack.BenchFillService;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Parent command: /quickstack (/qs)
 * 
 * Usage:
 *   /qs              - Quick stack items to nearby chests
 *   /qs fill [type]  - Fill processing benches (subcommand)
 * 
 * Subcommands are registered via addSubCommand().
 */
public class QuickStackParentCommand extends AbstractAsyncCommand {

    private final QuickStackService stackService;

    public QuickStackParentCommand(QuickStackService stackService, BenchFillService fillService) {
        super("quickstack", "Quick stack items to nearby containers");
        this.addAliases("qs");
        this.setPermissionGroup(GameMode.Adventure);
        this.stackService = stackService;

        // Register subcommands
        this.addSubCommand(new FillSubCommand(fillService));
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        // Default behavior when no subcommand: quick stack to chests
        if (!context.isPlayer()) {
            return CompletableFuture.completedFuture(null);
        }

        Player player = (Player) context.sender();

        try {
            player.sendMessage(Message.raw(
                "[QuickStack] Stacking to nearby containers (radius: " + 
                stackService.getConfig().getSearchRadius() + ")..."
            ));

            return stackService.performQuickStack(player).thenAccept(result -> {
                String msg;
                if (result.hasMovedItems()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[QuickStack] Done! Moved:");
                    for (Map.Entry<String, Integer> entry : result.getMovedItems().entrySet()) {
                        sb.append("\n  [x").append(entry.getValue()).append("] ").append(entry.getKey());
                    }
                    msg = sb.toString();
                    player.sendInventory();
                } else {
                    msg = "[QuickStack] Nothing to move. Checked " + result.getContainersChecked() + " containers.";
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
        System.err.println("[QuickStack] Error: " + e.getMessage());
        e.printStackTrace();
        String causeMsg = e.getMessage();
        if (e.getCause() != null) {
            causeMsg += " Cause: " + e.getCause().getMessage();
        }
        player.sendMessage(Message.raw("[QuickStack] Error: " + causeMsg));
    }
}
