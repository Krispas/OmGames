package krispasi.omGames.bedwars.config;

import krispasi.omGames.bedwars.model.BedWarsMap;
import krispasi.omGames.bedwars.model.TeamConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

public class BedWarsConfigService {
    private final Plugin plugin;
    private FileConfiguration config;
    private String dimensionName;
    private int respawnDelay;
    private int baseGenRadius;
    private int advancedGenRadius;
    private final Map<String, BedWarsMap> maps = new HashMap<>();

    public BedWarsConfigService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        try {
            plugin.saveResource("bedwars.yml", false);
        } catch (IllegalArgumentException ignored) {
            // file already exists, keep user edits
        }

        try {
            File file = new File(plugin.getDataFolder(), "bedwars.yml");
            config = YamlConfiguration.loadConfiguration(file);

            dimensionName = config.getString("bedwars-dimension", "bedwars");
            respawnDelay = config.getInt("respawn-delay-seconds", 5);
            baseGenRadius = config.getInt("anti-build.base-generator-radius", 14);
            advancedGenRadius = config.getInt("anti-build.advanced-generator-radius", 4);

            ConfigurationSection mapRoot = config.getConfigurationSection("maps");
            if (mapRoot != null) {
                for (String mapName : mapRoot.getKeys(false)) {
                    ConfigurationSection section = mapRoot.getConfigurationSection(mapName);
                    if (section == null) continue;

                    try {
                        BedWarsMap map = parseMap(mapName, section);
                        maps.put(mapName.toLowerCase(), map);
                    } catch (Exception ex) {
                        plugin.getLogger().log(Level.WARNING, "Could not parse map " + mapName, ex);
                    }
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load BedWars config", ex);
        }
    }

    private BedWarsMap parseMap(String name, ConfigurationSection section) {
        String world = section.getString("world", dimensionName);
        int lobbyHeight = section.getInt("lobby-height", 100);
        Location center = parseLocation(world, section.getString("center", "0, 100, 0"), false);

        Map<String, TeamConfig> teams = new HashMap<>();
        ConfigurationSection teamSection = section.getConfigurationSection("teams");
        if (teamSection != null) {
            for (String team : teamSection.getKeys(false)) {
                ConfigurationSection teamConfig = teamSection.getConfigurationSection(team);
                if (teamConfig == null) continue;
                Location spawn = parseLocation(world, teamConfig.getString("spawn"), true);
                Location bed = parseLocation(world, teamConfig.getString("bed"), false);
                Location generator = parseLocation(world, teamConfig.getString("generator"), false);
                if (spawn != null && bed != null && generator != null) {
                    teams.put(team.toLowerCase(), new TeamConfig(team, spawn, bed, generator));
                }
            }
        }

        Set<Location> diamondGens = new HashSet<>();
        for (String raw : section.getStringList("diamonds")) {
            Location loc = parseLocation(world, raw, false);
            if (loc != null) diamondGens.add(loc);
        }

        Set<Location> emeraldGens = new HashSet<>();
        for (String raw : section.getStringList("emeralds")) {
            Location loc = parseLocation(world, raw, false);
            if (loc != null) emeraldGens.add(loc);
        }

        return new BedWarsMap(name, world, lobbyHeight, center, teams, diamondGens, emeraldGens);
    }

    private Location parseLocation(String world, String raw, boolean hasDirection) {
        if (raw == null) return null;
        String[] parts = raw.split(",");
        try {
            double x = Double.parseDouble(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());
            double z = Double.parseDouble(parts[2].trim());
            if (hasDirection && parts.length >= 5) {
                float yaw = Float.parseFloat(parts[3].trim());
                float pitch = Float.parseFloat(parts[4].trim());
                return new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
            }
            return new Location(Bukkit.getWorld(world), x, y, z);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Bad location string: " + raw, ex);
            return null;
        }
    }

    public String getDimensionName() {
        return dimensionName;
    }

    public int getRespawnDelay() {
        return respawnDelay;
    }

    public int getBaseGenRadius() {
        return baseGenRadius;
    }

    public int getAdvancedGenRadius() {
        return advancedGenRadius;
    }

    public Optional<BedWarsMap> getMap(String name) {
        return Optional.ofNullable(maps.get(name.toLowerCase()));
    }

    public Map<String, BedWarsMap> getMaps() {
        return Map.copyOf(maps);
    }
}
