package krispasi.omGames;

import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.command.BedwarsCommand;
import krispasi.omGames.bedwars.listener.BedwarsListener;
import krispasi.omGames.bedwars.setup.BedwarsSetupManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class OmGames extends JavaPlugin {
    private BedwarsManager bedwarsManager;
    private BedwarsSetupManager setupManager;

    @Override
    public void onEnable() {
        saveResource("bedwars.yml", false);

        bedwarsManager = new BedwarsManager(this);
        bedwarsManager.loadArenas();
        setupManager = new BedwarsSetupManager(this, bedwarsManager);

        PluginCommand command = getCommand("bw");
        if (command != null) {
            BedwarsCommand executor = new BedwarsCommand(bedwarsManager, setupManager);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(new BedwarsListener(bedwarsManager), this);
    }

    @Override
    public void onDisable() {
        if (bedwarsManager != null) {
            bedwarsManager.shutdown();
        }
    }
}
