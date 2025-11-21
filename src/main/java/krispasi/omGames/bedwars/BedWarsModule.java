package krispasi.omGames.bedwars;

import krispasi.omGames.bedwars.command.BedWarsCommand;
import krispasi.omGames.bedwars.config.BedWarsConfigService;
import krispasi.omGames.bedwars.menu.BedWarsMenuFactory;
import krispasi.omGames.bedwars.menu.BedWarsMenuListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class BedWarsModule {
    private final JavaPlugin plugin;
    private BedWarsConfigService configService;

    public BedWarsModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        configService = new BedWarsConfigService(plugin);
        configService.load();

        // command + listeners live here so we can wire everything simply
        BedWarsMenuFactory menuFactory = new BedWarsMenuFactory();
        try {
            BedWarsCommand commandHandler = new BedWarsCommand(menuFactory, configService);
            if (plugin.getCommand("bw") != null) {
                plugin.getCommand("bw").setExecutor(commandHandler);
                plugin.getCommand("bw").setTabCompleter(commandHandler);
            } else {
                plugin.getLogger().warning("/bw command missing from paper-plugin.yml");
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not wire BedWars command: " + ex.getMessage());
        }

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new BedWarsMenuListener(configService.getMaps(), configService.getDimensionName()), plugin);
    }

    public void disable() {
        // placeholder if we need cleanup later
    }

    public BedWarsConfigService getConfigService() {
        return configService;
    }
}
