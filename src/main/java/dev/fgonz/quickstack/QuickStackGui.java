package dev.fgonz.quickstack;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;

import javax.annotation.Nonnull;

/**
 * UI page for Quick Stack. Shows the action button and settings (radius, backpack toggle).
 * Config changes persist to disk automatically.
 */
public class QuickStackGui extends InteractiveCustomUIPage<QuickStackGui.GuiData> {

    private final QuickStackService service;
    private final Config<QuickStackConfig> configWrapper;

    public static class GuiData {
        private String action;
        
        public String getAction() { return action; }
        public void setAction(String a) { this.action = a; }

        public static final BuilderCodec<GuiData> CODEC = BuilderCodec.builder(GuiData.class, GuiData::new)
            .addField(new KeyedCodec<>("Action", Codec.STRING), GuiData::setAction, GuiData::getAction)
            .build();
    }

    public QuickStackGui(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime, QuickStackService service, Config<QuickStackConfig> configWrapper) {
        super(playerRef, lifetime, GuiData.CODEC);
        this.service = service;
        this.configWrapper = configWrapper;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder, @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/QuickStack_Gui.ui");
        
        QuickStackConfig cfg = configWrapper.get();
        
        uiCommandBuilder.set("#LblRadius.Text", String.valueOf(cfg.getSearchRadius()));
        uiCommandBuilder.set("#BtnToggleBackpack.Text", cfg.isCheckBackpack() ? "ON" : "OFF");
        
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnQuickStack", EventData.of("Action", "stack"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnRadiusMinus", EventData.of("Action", "radius_dec"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnRadiusPlus", EventData.of("Action", "radius_inc"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnToggleBackpack", EventData.of("Action", "toggle_backpack"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull GuiData data) {
        super.handleDataEvent(ref, store, data);
        
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        String action = data.getAction();
        QuickStackConfig cfg = configWrapper.get();
        boolean configChanged = false;
        
        switch (action) {
            case "stack":
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                    "Checking within " + cfg.getSearchRadius() + " blocks..."));
                
                service.performQuickStack(player).thenAccept(result -> {
                    String msg;
                    if (result.hasMovedItems()) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("Quickstack done! Moved:");
                        for (java.util.Map.Entry<String, Integer> entry : result.getMovedItems().entrySet()) {
                            sb.append("\n[x").append(entry.getValue()).append("] ").append(entry.getKey());
                        }
                        msg = sb.toString();
                        player.sendInventory();
                    } else {
                        msg = "Nothing to move. Checked " + result.getContainersChecked() + " containers.";
                    }
                    player.sendMessage(com.hypixel.hytale.server.core.Message.raw(msg));
                });
                return;
                
            case "radius_inc":
                if (cfg.getSearchRadius() < 15) {
                    cfg.setSearchRadius(cfg.getSearchRadius() + 1);
                    configChanged = true;
                }
                break;
                
            case "radius_dec":
                if (cfg.getSearchRadius() > 1) {
                    cfg.setSearchRadius(cfg.getSearchRadius() - 1);
                    configChanged = true;
                }
                break;
                
            case "toggle_backpack":
                cfg.setCheckBackpack(!cfg.isCheckBackpack());
                configChanged = true;
                break;
        }
        
        if (configChanged) {
            configWrapper.save();
            
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef != null) {
                player.getPageManager().openCustomPage(ref, store, 
                    new QuickStackGui(playerRef, CustomPageLifetime.CanDismiss, service, configWrapper));
            }
        }
    }
}
