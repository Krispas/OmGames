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
            if (mapRoot == null) {
                // allow users to name the section "arenas" like many BedWars setups
                mapRoot = config.getConfigurationSection("arenas");
            }
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
        int lobbyHeight = section.getInt("lobby-height",
                section.getInt("lobbyHeight",
                        section.getInt("lobby_height", 120)));
        Location center = parseLocation(world, section.getString("center", "0 120 0"), false);

        Map<String, TeamConfig> teams = parseTeams(section, world);
        Set<Location> diamondGens = parseAdvancedGenerators(section, world, "diamond");
        Set<Location> emeraldGens = parseAdvancedGenerators(section, world, "emerald");

        return new BedWarsMap(name, world, lobbyHeight, center, teams, diamondGens, emeraldGens);
    }

    private Map<String, TeamConfig> parseTeams(ConfigurationSection section, String world) {
        Map<String, TeamConfig> teams = new HashMap<>();

        // legacy format: teams -> <color> -> spawn/bed/generator
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
            return teams;
        }

        // new format: beds + generators + spawns (can be spelled Spawns)
        ConfigurationSection bedsSection = section.getConfigurationSection("beds");
        ConfigurationSection spawnSection = findSectionIgnoreCase(section, "spawns");
        ConfigurationSection generatorSection = section.getConfigurationSection("generators");

        if (bedsSection == null) {
            return teams; // nothing to parse
        }

        for (String team : bedsSection.getKeys(false)) {
            String normalized = team.toLowerCase();
            Location bed = parseLocation(world, bedsSection.getString(team), false);

            Location spawn = null;
            if (spawnSection != null) {
                String spawnKey = "base_" + normalized;
                String raw = getStringIgnoreCase(spawnSection, spawnKey);
                if (raw == null) raw = getStringIgnoreCase(spawnSection, normalized);
                spawn = parseLocation(world, raw, true);
            }

            Location generator = null;
            if (generatorSection != null) {
                String generatorKey = "base_" + normalized;
                String raw = getStringIgnoreCase(generatorSection, generatorKey);
                if (raw == null) raw = getStringIgnoreCase(generatorSection, normalized);
                generator = parseLocation(world, raw, false);
            }

            if (spawn == null || bed == null || generator == null) {
                plugin.getLogger().log(Level.WARNING, "Missing spawn/bed/generator for team " + normalized + " in map " + section.getName());
                continue;
            }

            teams.put(normalized, new TeamConfig(normalized, spawn, bed, generator));
        }

        return teams;
    }

    private Set<Location> parseAdvancedGenerators(ConfigurationSection section, String world, String prefix) {
        Set<Location> result = new HashSet<>();

        ConfigurationSection generatorSection = section.getConfigurationSection("generators");
        if (generatorSection != null) {
            for (String key : generatorSection.getKeys(false)) {
                if (!key.toLowerCase().startsWith(prefix)) continue;
                Location loc = parseLocation(world, generatorSection.getString(key), false);
                if (loc != null) result.add(loc);
            }
        }

        if (!result.isEmpty()) {
            return result;
        }

        // fall back to legacy lists if present
        String listKey = prefix.equalsIgnoreCase("diamond") ? "diamonds" : "emeralds";
        for (String raw : section.getStringList(listKey)) {
            Location loc = parseLocation(world, raw, false);
            if (loc != null) result.add(loc);
        }

        return result;
    }

    private ConfigurationSection findSectionIgnoreCase(ConfigurationSection parent, String key) {
        if (parent == null) return null;
        for (String child : parent.getKeys(false)) {
            if (child.equalsIgnoreCase(key)) {
                return parent.getConfigurationSection(child);
            }
        }
        return null;
    }

    private String getStringIgnoreCase(ConfigurationSection section, String key) {
        if (section == null) return null;
        for (String child : section.getKeys(false)) {
            if (child.equalsIgnoreCase(key)) {
                return section.getString(child);
            }
        }
        return null;
    }

    private Location parseLocation(String world, String raw, boolean hasDirection) {
        if (raw == null) return null;
        try {
            String normalized = raw.trim().replaceAll(",", " ").replaceAll("\\s+", " ");
            String[] parts = normalized.split(" ");
            if (parts.length < 3) throw new IllegalArgumentException("Need at least x y z");

            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);

            if (hasDirection && parts.length >= 5) {
                float yaw = Float.parseFloat(parts[3]);
                float pitch = Float.parseFloat(parts[4]);
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

    public java.util.logging.Logger getLogger() {
        // expose plugin logger for friendly diagnostics without holding the plugin reference elsewhere
        return plugin.getLogger();
    }

    public Optional<BedWarsMap> getMap(String name) {
        return Optional.ofNullable(maps.get(name.toLowerCase()));
    }

    public Map<String, BedWarsMap> getMaps() {
        return Map.copyOf(maps);
    }
}
