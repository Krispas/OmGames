package krispasi.omGames;

import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.command.BedwarsCommand;
import krispasi.omGames.bedwars.listener.BedwarsListener;
import krispasi.omGames.bedwars.setup.BedwarsSetupManager;
import krispasi.omGames.egghunt.EggHuntCommand;
import krispasi.omGames.egghunt.EggHuntListener;
import krispasi.omGames.egghunt.EggHuntManager;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
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
    private static OmGames instance;
    private BedwarsManager bedwarsManager;
    private BedwarsSetupManager setupManager;
    private EggHuntManager eggHuntManager;

    @Override
    public void onEnable() {
        instance = this;

        ensureBedwarsConfig("bedwars.yml");
        ensureBedwarsConfig("shop.yml");
        ensureBedwarsConfig("rotating-items.yml");
        ensureBedwarsConfig("custom-items.yml");

        bedwarsManager = new BedwarsManager(this);
        bedwarsManager.loadArenas();
        bedwarsManager.loadCustomItems();
        bedwarsManager.loadShopConfig();
        bedwarsManager.loadQuickBuy();
        bedwarsManager.loadStats();
        bedwarsManager.startLobbyLeaderboard();
        setupManager = new BedwarsSetupManager(this, bedwarsManager);
        eggHuntManager = new EggHuntManager(this);
        eggHuntManager.load();

        PluginCommand command = getCommand("bw");
        if (command != null) {
            BedwarsCommand executor = new BedwarsCommand(bedwarsManager, setupManager);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        PluginCommand eggHuntCommand = getCommand("egghunt");
        if (eggHuntCommand != null) {
            EggHuntCommand executor = new EggHuntCommand(eggHuntManager);
            eggHuntCommand.setExecutor(executor);
            eggHuntCommand.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(new BedwarsListener(bedwarsManager), this);
        getServer().getPluginManager().registerEvents(new EggHuntListener(eggHuntManager), this);
        //setupBedwars();
    }

    @Override
    public void onDisable() {
        if (eggHuntManager != null) {
            eggHuntManager.shutdown();
        }
        if (bedwarsManager != null) {
            bedwarsManager.shutdown();
        }
    }

    private void ensureBedwarsConfig(String name) {
        java.io.File dataFolder = getDataFolder();
        java.io.File bedwarsFolder = new java.io.File(dataFolder, "Bedwars");
        if (!bedwarsFolder.exists()) {
            bedwarsFolder.mkdirs();
        }
        java.io.File target = new java.io.File(bedwarsFolder, name);
        if (target.exists()) {
            return;
        }
        try (java.io.InputStream input = getResource(name)) {
            if (input == null) {
                return;
            }
            java.nio.file.Files.copy(input, target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException ex) {
            getLogger().warning("Failed to save Bedwars config " + name + ": " + ex.getMessage());
        }
    }

    public static OmGames getInstance(){ return instance; }
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
