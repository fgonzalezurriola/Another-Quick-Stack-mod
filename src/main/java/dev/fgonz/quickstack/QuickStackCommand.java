package dev.fgonz.quickstack;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Chat command /qsu - executes quick stack immediately without opening the UI.
 */
public class QuickStackCommand extends AbstractAsyncCommand {

    private final QuickStackService service;

    public QuickStackCommand(QuickStackService service) {
        super("qsu", "Quick Stack items to nearby containers");
        this.setPermissionGroup(GameMode.Adventure);
        this.service = service;
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        if (!context.isPlayer()) {
            return CompletableFuture.completedFuture(null);
        }

        Player player = (Player) context.sender();
        try {
            player.sendMessage(Message.raw("Checking within " + service.getConfig().getSearchRadius() + " blocks..."));

            return service.performQuickStack(player).thenAccept(result -> {
                String msg;
                if (result.hasMovedItems()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Quickstack done! Moved:");
                    for (Map.Entry<String, Integer> entry : result.getMovedItems().entrySet()) {
                        sb.append("\n[x").append(entry.getValue()).append("] ").append(entry.getKey());
                    }
                    msg = sb.toString();
                    player.sendInventory();
                } else {
                    msg = "Nothing to move. Checked " + result.getContainersChecked() + " containers.";
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
        System.err.println("Error executing quickstack command");
        e.printStackTrace();
        String causeMsg = e.getMessage();
        if (e instanceof ExceptionInInitializerError) {
             Throwable cause = e.getCause();
             if (cause != null) {
                 causeMsg = "InitError Cause: " + cause.getMessage() + " (" + cause.getClass().getSimpleName() + ")";
                 cause.printStackTrace();
             }
        } else if (e.getCause() != null) {
            causeMsg += " Cause: " + e.getCause().getMessage();
        }
        
        player.sendMessage(Message.raw("- Error: " + causeMsg));
    }
}
