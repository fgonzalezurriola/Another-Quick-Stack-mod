package dev.fgonz.quickstack.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;

import dev.fgonz.quickstack.QuickStackConfig;
import dev.fgonz.quickstack.QuickStackGui;
import dev.fgonz.quickstack.QuickStackService;

import java.util.concurrent.CompletableFuture;

/**
 * Command: /quickstack
 * Opens the Quick Stack UI with settings and action button.
 */
public class QuickStackUiCommand extends AbstractAsyncCommand {

    private final QuickStackService service;
    private final Config<QuickStackConfig> configWrapper;

    public QuickStackUiCommand(QuickStackService service, Config<QuickStackConfig> configWrapper) {
        super("quickstack", "Open Quick Stack UI");
        this.setPermissionGroup(GameMode.Adventure);
        this.service = service;
        this.configWrapper = configWrapper;
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        if (!context.isPlayer()) {
            return CompletableFuture.completedFuture(null);
        }

        Player player = (Player) context.sender();
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return CompletableFuture.completedFuture(null);
        }

        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();

        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRefComponent = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRefComponent != null) {
                player.getPageManager().openCustomPage(
                    ref,
                    store,
                    new QuickStackGui(playerRefComponent, CustomPageLifetime.CanDismiss, service, configWrapper)
                );
            }
        }, world);
    }
}
