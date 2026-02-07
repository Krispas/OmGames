package krispasi.omGames.bedwars.config;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import krispasi.omGames.bedwars.generator.GeneratorInfo;
import krispasi.omGames.bedwars.generator.GeneratorType;
import krispasi.omGames.bedwars.model.Arena;
import krispasi.omGames.bedwars.model.BedLocation;
import krispasi.omGames.bedwars.model.BlockPoint;
import krispasi.omGames.bedwars.model.TeamColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public class BedwarsConfigLoader {
    private final File file;
    private final Logger logger;

    public BedwarsConfigLoader(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public Map<String, Arena> load() {
        Map<String, Arena> arenas = new HashMap<>();
        if (!file.exists()) {
            logger.warning("BedWars config not found: " + file.getAbsolutePath());
            return arenas;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection arenasSection = config.getConfigurationSection("arenas");
        if (arenasSection == null) {
            logger.warning("No arenas section found in bedwars.yml");
            return arenas;
        }

        for (String arenaId : arenasSection.getKeys(false)) {
            ConfigurationSection arenaSection = arenasSection.getConfigurationSection(arenaId);
            if (arenaSection == null) {
                continue;
            }

            String worldName = arenaSection.getString("world");
            if (worldName == null || worldName.isBlank()) {
                logger.warning("Arena " + arenaId + " missing world name.");
                continue;
            }

            BlockPoint center = parsePoint(arenaSection.getString("center"), "center", arenaId);
            int lobbyHeight = arenaSection.getInt("lobby-height", 0);
            int baseGeneratorRadius = arenaSection.getInt("anti-build.base-generator-radius", 0);
            int advancedGeneratorRadius = arenaSection.getInt("anti-build.advanced-generator-radius", 0);

            Map<TeamColor, BedLocation> beds = new EnumMap<>(TeamColor.class);
            ConfigurationSection bedSection = arenaSection.getConfigurationSection("beds");
            if (bedSection != null) {
                for (String teamKey : bedSection.getKeys(false)) {
                    TeamColor team = TeamColor.fromKey(teamKey);
                    if (team == null) {
                        logger.warning("Unknown bed team in " + arenaId + ": " + teamKey);
                        continue;
                    }
                    String value = bedSection.getString(teamKey);
                    if (value == null) {
                        continue;
                    }
                    String[] parts = value.split(",");
                    if (parts.length != 2) {
                        logger.warning("Invalid bed format for " + arenaId + " team " + teamKey + ": " + value);
                        continue;
                    }
                    BlockPoint head = parsePoint(parts[0].trim(), "bed head", arenaId);
                    BlockPoint foot = parsePoint(parts[1].trim(), "bed foot", arenaId);
                    if (head != null && foot != null) {
                        beds.put(team, new BedLocation(head, foot));
                    }
                }
            }

            List<GeneratorInfo> generators = new ArrayList<>();
            ConfigurationSection generatorSection = arenaSection.getConfigurationSection("generators");
            ConfigurationSection baseGeneratorSection = arenaSection.getConfigurationSection("Base_Generators");
            if (baseGeneratorSection == null) {
                baseGeneratorSection = arenaSection.getConfigurationSection("base_generators");
            }
            if (baseGeneratorSection == null) {
                baseGeneratorSection = arenaSection.getConfigurationSection("base-generators");
            }
            if (generatorSection != null) {
                for (String key : generatorSection.getKeys(false)) {
                    if (key.startsWith("base_") && baseGeneratorSection != null) {
                        continue;
                    }
                    String value = generatorSection.getString(key);
                    BlockPoint location = parsePoint(value, "generator", arenaId);
                    if (location == null) {
                        continue;
                    }
                    GeneratorInfo info = parseGenerator(key, location, arenaId);
                    if (info != null) {
                        generators.add(info);
                    }
                }
            }
            if (baseGeneratorSection != null) {
                for (String key : baseGeneratorSection.getKeys(false)) {
                    String value = baseGeneratorSection.getString(key);
                    BlockPoint location = parsePoint(value, "base generator", arenaId);
                    if (location == null) {
                        continue;
                    }
                    GeneratorInfo info = parseBaseGenerator(key, location, arenaId);
                    if (info != null) {
                        generators.add(info);
                    }
                }
            }

            Map<TeamColor, BlockPoint> spawns = new EnumMap<>(TeamColor.class);
            ConfigurationSection spawnSection = arenaSection.getConfigurationSection("Spawns");
            if (spawnSection == null) {
                spawnSection = arenaSection.getConfigurationSection("spawns");
            }
            if (spawnSection != null) {
                for (String key : spawnSection.getKeys(false)) {
                    if (!key.startsWith("base_")) {
                        continue;
                    }
                    String teamKey = key.substring("base_".length());
                    TeamColor team = TeamColor.fromKey(teamKey);
                    if (team == null) {
                        logger.warning("Unknown spawn team in " + arenaId + ": " + key);
                        continue;
                    }
                    BlockPoint point = parsePoint(spawnSection.getString(key), "spawn", arenaId);
                    if (point != null) {
                        spawns.put(team, point);
                    }
                }
            }

            if (center == null) {
                logger.warning("Arena " + arenaId + " missing center location.");
                continue;
            }

            Arena arena = new Arena(
                    arenaId,
                    worldName,
                    lobbyHeight,
                    center,
                    baseGeneratorRadius,
                    advancedGeneratorRadius,
                    beds,
                    generators,
                    spawns
            );
            arenas.put(arenaId, arena);
        }

        return arenas;
    }

    private BlockPoint parsePoint(String value, String label, String arenaId) {
        if (value == null || value.isBlank()) {
            logger.warning("Missing " + label + " location in " + arenaId);
            return null;
        }
        try {
            return BlockPoint.parse(value);
        } catch (IllegalArgumentException ex) {
            logger.warning("Invalid " + label + " location in " + arenaId + ": " + value);
            return null;
        }
    }

    private GeneratorInfo parseGenerator(String key, BlockPoint location, String arenaId) {
        if (key.startsWith("base_")) {
            String teamKey = key.substring("base_".length());
            TeamColor team = TeamColor.fromKey(teamKey);
            if (team == null) {
                logger.warning("Unknown base generator team in " + arenaId + ": " + key);
                return null;
            }
            return new GeneratorInfo(GeneratorType.BASE, team, location, key);
        }
        if (key.startsWith("diamond_")) {
            return new GeneratorInfo(GeneratorType.DIAMOND, null, location, key);
        }
        if (key.startsWith("emerald_")) {
            return new GeneratorInfo(GeneratorType.EMERALD, null, location, key);
        }
        logger.warning("Unknown generator key in " + arenaId + ": " + key);
        return null;
    }

    private GeneratorInfo parseBaseGenerator(String key, BlockPoint location, String arenaId) {
        String teamKey = null;
        if (key.startsWith("base_gen_")) {
            teamKey = key.substring("base_gen_".length());
        } else if (key.startsWith("base_gem_")) {
            teamKey = key.substring("base_gem_".length());
        } else if (key.startsWith("base_")) {
            teamKey = key.substring("base_".length());
        }
        if (teamKey == null) {
            logger.warning("Unknown base generator key in " + arenaId + ": " + key);
            return null;
        }
        TeamColor team = TeamColor.fromKey(teamKey);
        if (team == null) {
            logger.warning("Unknown base generator team in " + arenaId + ": " + key);
            return null;
        }
        return new GeneratorInfo(GeneratorType.BASE, team, location, key);
    }
}
