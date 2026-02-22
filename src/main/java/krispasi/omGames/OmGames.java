package krispasi.omGames;

import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.command.BedwarsCommand;
import krispasi.omGames.bedwars.listener.BedwarsListener;
import krispasi.omGames.bedwars.setup.BedwarsSetupManager;
import org.bukkit.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main Bukkit plugin class for OmGames.
 * <p>Initializes BedWars configuration files and loads them through
 * {@link krispasi.omGames.bedwars.BedwarsManager}.</p>
 * <p>Registers the {@code /bw} command and the BedWars event listener, and
 * shuts down active sessions on disable.</p>
 * @see krispasi.omGames.bedwars.BedwarsManager
 * @see krispasi.omGames.bedwars.listener.BedwarsListener
 */
public final class OmGames extends JavaPlugin {
    private BedwarsManager bedwarsManager;
    private BedwarsSetupManager setupManager;

    @Override
    public void onEnable() {
        saveResource("bedwars.yml", false);
        saveResource("shop.yml", false);
        saveResource("custom-items.yml", false);

        bedwarsManager = new BedwarsManager(this);
        bedwarsManager.loadArenas();
        bedwarsManager.loadCustomItems();
        bedwarsManager.loadShopConfig();
        bedwarsManager.loadQuickBuy();
        setupManager = new BedwarsSetupManager(this, bedwarsManager);

        PluginCommand command = getCommand("bw");
        if (command != null) {
            BedwarsCommand executor = new BedwarsCommand(bedwarsManager, setupManager);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(new BedwarsListener(bedwarsManager), this);
        //setupBedwars();
    }

    @Override
    public void onDisable() {
        if (bedwarsManager != null) {
            bedwarsManager.shutdown();
        }
    }
/*    private void setupBedwars() {
        WorldCreator creator = new WorldCreator("slumber");
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.FLAT);

        // Void superflat preset
        creator.generatorSettings("""
        {
          "type": "minecraft:flat",
          "settings": {
            "layers": [
              {
                "block": "minecraft:air",
                "height": 1
              }
            ],
            "biome": "minecraft:the_void",
            "structure_overrides": []
          }
        }
        """);

        World resourceWorld = Bukkit.createWorld(creator);
        if (resourceWorld != null) {
            resourceWorld.setGameRule(GameRules.KEEP_INVENTORY, true);
            resourceWorld.setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 1);
            resourceWorld.setDifficulty(Difficulty.HARD);
            resourceWorld.setSpawnLocation(new Location(resourceWorld, 0.5, 64, 0.5));
        } else {
            System.out.println("Failed to create bedwars");
        }
*/
}
