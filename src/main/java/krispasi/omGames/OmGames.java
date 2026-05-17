package krispasi.omGames;

import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.command.BedwarsCommand;
import krispasi.omGames.bedwars.listener.BedwarsListener;
import krispasi.omGames.bedwars.setup.BedwarsSetupManager;
import krispasi.omGames.egghunt.EggHuntCommand;
import krispasi.omGames.egghunt.EggHuntListener;
import krispasi.omGames.egghunt.EggHuntManager;
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
    private static final String[] BEDWARS_CONFIG_FILES = {
            "bedwars.yml",
            "shop.yml",
            "rotating-items.yml",
            "custom-items.yml"
    };
    private static final String[] BEDWARS_VERSION_REFRESH_FILES = {
            "shop.yml",
            "rotating-items.yml",
            "custom-items.yml"
    };
    private static final String BEDWARS_VERSION_MARKER_FILE = ".config-sync-version";
    private static OmGames instance;
    private BedwarsManager bedwarsManager;
    private BedwarsSetupManager setupManager;
    private EggHuntManager eggHuntManager;

    @Override
    public void onEnable() {
        instance = this;

        synchronizeBedwarsConfigs();

        bedwarsManager = new BedwarsManager(this);
        bedwarsManager.loadArenas();
        bedwarsManager.loadCustomItems();
        bedwarsManager.loadShopConfig();
        bedwarsManager.loadRotationHistory();
        bedwarsManager.loadQuickBuy();
        bedwarsManager.loadStats();
        bedwarsManager.loadTimeCapsules();
        bedwarsManager.loadKarma();
        bedwarsManager.loadSkins();
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
/*      setupBedwars();
        setupBedwars1();
        setupBedwars2();
        setupBedwars3();
*/
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

    private void synchronizeBedwarsConfigs() {
        for (String configName : BEDWARS_CONFIG_FILES) {
            ensureBedwarsConfig(configName);
        }
        String pluginVersion = getDescription().getVersion();
        String storedVersion = readBedwarsConfigSyncVersion();
        if (pluginVersion.equalsIgnoreCase(storedVersion)) {
            return;
        }
        getLogger().info("Detected plugin version change for BedWars config sync (" + storedVersion + " -> "
                + pluginVersion + "). Refreshing shop/rotating/custom item defaults.");
        for (String configName : BEDWARS_VERSION_REFRESH_FILES) {
            copyBedwarsConfig(configName);
        }
        writeBedwarsConfigSyncVersion(pluginVersion);
    }

    private void copyBedwarsConfig(String name) {
        java.io.File dataFolder = getDataFolder();
        java.io.File bedwarsFolder = new java.io.File(dataFolder, "Bedwars");
        if (!bedwarsFolder.exists()) {
            bedwarsFolder.mkdirs();
        }
        java.io.File target = new java.io.File(bedwarsFolder, name);
        try (java.io.InputStream input = getResource(name)) {
            if (input == null) {
                return;
            }
            java.nio.file.Files.copy(input, target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException ex) {
            getLogger().warning("Failed to refresh Bedwars config " + name + ": " + ex.getMessage());
        }
    }

    private String readBedwarsConfigSyncVersion() {
        java.io.File markerFile = new java.io.File(new java.io.File(getDataFolder(), "Bedwars"), BEDWARS_VERSION_MARKER_FILE);
        if (!markerFile.exists()) {
            return null;
        }
        try {
            return java.nio.file.Files.readString(markerFile.toPath()).trim();
        } catch (java.io.IOException ex) {
            getLogger().warning("Failed to read Bedwars config sync marker: " + ex.getMessage());
            return null;
        }
    }

    private void writeBedwarsConfigSyncVersion(String version) {
        java.io.File bedwarsFolder = new java.io.File(getDataFolder(), "Bedwars");
        if (!bedwarsFolder.exists()) {
            bedwarsFolder.mkdirs();
        }
        java.io.File markerFile = new java.io.File(bedwarsFolder, BEDWARS_VERSION_MARKER_FILE);
        try {
            java.nio.file.Files.writeString(markerFile.toPath(), version == null ? "" : version);
        } catch (java.io.IOException ex) {
            getLogger().warning("Failed to write Bedwars config sync marker: " + ex.getMessage());
        }
    }

    public static OmGames getInstance() {
        return instance;
    }

/*    private void setupBedwars() {
        WorldCreator creator = new WorldCreator("Rooftop");
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
    }

    private void setupBedwars1() {
        WorldCreator creator = new WorldCreator("Mirage");
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
    }
    private void setupBedwars2() {
        WorldCreator creator = new WorldCreator("Vigilante");
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
    }
    private void setupBedwars3() {
        WorldCreator creator = new WorldCreator("Keep");
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
