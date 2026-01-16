package dev.fgonz.quickstack;

import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Plugin entry point. Loads config from disk and registers /qsu and /quickstack commands.
 */
public class QuickStackCommandPlugin extends JavaPlugin {

    private Config<QuickStackConfig> configWrapper;
    private QuickStackService service;

    public QuickStackCommandPlugin(JavaPluginInit init) {
        super(init);
    }

    static {
        System.out.println("QuickStack: Class Loaded. Dependency check: Config=" + Config.class.getName());
    }

    @Override
    protected void setup() {
        System.out.println("QuickStack: setup() started.");
        
        Path configPath = Paths.get("UserData/Configs/QuickStackCommand.json");
        
        try {
            if (configPath.getParent() != null) {
                java.nio.file.Files.createDirectories(configPath.getParent());
            }
        } catch (Exception e) {
            System.err.println("QuickStack: Failed to create dir: " + e);
        }

        this.configWrapper = new Config<>(configPath, "QuickStackCommand", QuickStackConfig.CODEC);
        
        boolean isFirstRun = !java.nio.file.Files.exists(configPath);
        
        try {
            System.out.println("QuickStack: Loading config...");
            this.configWrapper.load().join();
            System.out.println("QuickStack: Config loaded.");
            
            if (isFirstRun) {
                System.out.println("QuickStack: First run detected - saving default config to disk.");
                this.configWrapper.save().join();
            }
        } catch (Exception e) {
            System.err.println("QuickStack: Load failed, creating and saving default. Error: " + e.getMessage());
            this.configWrapper.save().join();
        }
        
        QuickStackConfig config = this.configWrapper.get();
        if (config == null) {
            config = new QuickStackConfig();
            System.err.println("QuickStack: Config was null after load, using new instance.");
        }
        
        this.service = new QuickStackService(config);
        
        CommandRegistry registry = getCommandRegistry();
        registry.registerCommand(new QuickStackCommand(this.service));
        registry.registerCommand(new QuickStackUiCommand(this.service, this.configWrapper));
        
        System.out.println("QuickStack: setup() finished.");
    }
    
    public QuickStackService getService() {
        return service;
    }
    
    public Config<QuickStackConfig> getConfigWrapper() {
        return configWrapper;
    }
}
