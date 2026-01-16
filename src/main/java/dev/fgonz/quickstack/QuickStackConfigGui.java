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
 * Settings Screen Logic.
 */
public class QuickStackConfigGui extends InteractiveCustomUIPage<QuickStackConfigGui.GuiData> {

    private final Config<QuickStackConfig> configWrapper;
    
    public static class GuiData {
        private String action;
        public String getAction() { return action; }
        public void setAction(String a) { this.action = a; }

        public static final BuilderCodec<GuiData> CODEC = BuilderCodec.builder(GuiData.class, GuiData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), GuiData::setAction, GuiData::getAction).add()
            .build();
    }

    public QuickStackConfigGui(@Nonnull PlayerRef playerRef, Config<QuickStackConfig> configWrapper) {
        super(playerRef, CustomPageLifetime.CanDismiss, GuiData.CODEC);
        this.configWrapper = configWrapper;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder, @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        uiCommandBuilder.append("Pages/QuickStack_Config_Gui.ui");
        
        QuickStackConfig cfg = configWrapper.get();
        
        // Update visual state using .set()
        uiCommandBuilder.set("#LblRadius.Text", String.valueOf(cfg.getSearchRadius()));
        uiCommandBuilder.set("#BtnToggleBackpack.Text", cfg.isCheckBackpack() ? "ON" : "OFF");
        
        // Bind Actions
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnRadiusMinus", EventData.of("Action", "radius_dec"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnRadiusPlus", EventData.of("Action", "radius_inc"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnToggleBackpack", EventData.of("Action", "toggle_backpack"), false);
        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BtnSave", EventData.of("Action", "save"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull GuiData data) {
        super.handleDataEvent(ref, store, data);
        
        String action = data.getAction();
        QuickStackConfig cfg = configWrapper.get();
        boolean needsRebuild = false;
        
        if ("radius_inc".equals(action)) {
            int val = cfg.getSearchRadius();
            if (val < 15) cfg.setSearchRadius(val + 1);
            needsRebuild = true;
        } else if ("radius_dec".equals(action)) {
            int val = cfg.getSearchRadius();
            if (val > 1) cfg.setSearchRadius(val - 1);
            needsRebuild = true;
        } else if ("toggle_backpack".equals(action)) {
            cfg.setCheckBackpack(!cfg.isCheckBackpack());
            needsRebuild = true;
        } else if ("save".equals(action)) {
            // Save to disk (blocking to ensure persistence)
            try {
                configWrapper.save().join();
                
                // Send confirmation message (user closes with ESC)
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    player.sendMessage(com.hypixel.hytale.server.core.Message.raw("- Configuration saved to disk! Press ESC to close."));
                }
            } catch (Exception e) {
                Player player = store.getComponent(ref, Player.getComponentType());
                if (player != null) {
                    player.sendMessage(com.hypixel.hytale.server.core.Message.raw("- ERROR: Failed to save configuration!"));
                }
                System.err.println("QuickStack: Save failed: " + e.getMessage());
            }
            return;
        }
        
        if (needsRebuild) {
            // Refresh UI
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef != null) {
                // Re-open with updated values
                store.getComponent(ref, Player.getComponentType()).getPageManager()
                     .openCustomPage(ref, store, new QuickStackConfigGui(playerRef, configWrapper));
            }
        }
    }
}
