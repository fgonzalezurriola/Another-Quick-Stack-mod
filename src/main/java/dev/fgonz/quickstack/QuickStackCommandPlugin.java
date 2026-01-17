package dev.fgonz.quickstack;

import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

import dev.fgonz.quickstack.commands.QuickStackParentCommand;
import dev.fgonz.quickstack.commands.QuickStackUiCommand;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Plugin entry point. Registers the QuickStack command system.
 * 
 * Commands:
 *   /quickstack          - Open settings UI
 *   /qs                  - Quick stack to nearby chests
 *   /qs fill [type]      - Fill processing benches (furnace, tannery)
 *   /qs config           - Open settings UI
 */
public class QuickStackCommandPlugin extends JavaPlugin {

    private Config<QuickStackConfig> configWrapper;
    private QuickStackService stackService;
    private BenchFillService benchFillService;

    public QuickStackCommandPlugin(JavaPluginInit init) {
        super(init);
    }

    static {
        System.out.println("[QuickStack] Class loaded");
    }

    @Override
    protected void setup() {
        System.out.println("[QuickStack] setup() started");

        Path configPath = Paths.get("UserData/Configs/QuickStackCommand.json");

        try {
            if (configPath.getParent() != null) {
                java.nio.file.Files.createDirectories(configPath.getParent());
            }
        } catch (Exception e) {
            System.err.println("[QuickStack] Failed to create config dir: " + e);
        }

        this.configWrapper = new Config<>(configPath, "QuickStackCommand", QuickStackConfig.CODEC);

        boolean isFirstRun = !java.nio.file.Files.exists(configPath);

        try {
            this.configWrapper.load().join();

            if (isFirstRun) {
                this.configWrapper.save().join();
            }
        } catch (Exception e) {
            System.err.println("[QuickStack] Config load failed: " + e.getMessage());
            this.configWrapper.save().join();
        }

        QuickStackConfig config = this.configWrapper.get();
        if (config == null) {
            config = new QuickStackConfig();
            System.err.println("[QuickStack] Config was null, using defaults");
        }

        // Initialize services
        this.stackService = new QuickStackService(config);
        this.benchFillService = new BenchFillService(config);

        // Register commands
        CommandRegistry registry = getCommandRegistry();
        registry.registerCommand(new QuickStackUiCommand(stackService, configWrapper));
        registry.registerCommand(new QuickStackParentCommand(stackService, benchFillService, configWrapper));

        System.out.println("[QuickStack] Ready! Commands:");
        System.out.println("  /quickstack   - Open settings UI");
        System.out.println("  /qs           - Quick stack to chests");
        System.out.println("  /qs config    - Open settings UI");
        System.out.println("  /qs fill      - Fill all processing benches");
        System.out.println("  /qs fill f    - Fill furnaces only");
        System.out.println("  /qs fill t    - Fill tanneries only");
    }

    public QuickStackService getStackService() {
        return stackService;
    }

    public BenchFillService getBenchFillService() {
        return benchFillService;
    }

    public Config<QuickStackConfig> getConfigWrapper() {
        return configWrapper;
    }
}
