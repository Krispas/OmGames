package krispasi.omGames.bedwars.config;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import krispasi.omGames.bedwars.generator.GeneratorInfo;
import krispasi.omGames.bedwars.generator.GeneratorSettings;
import krispasi.omGames.bedwars.generator.GeneratorType;
import krispasi.omGames.bedwars.model.Arena;
import krispasi.omGames.bedwars.model.BedLocation;
import krispasi.omGames.bedwars.model.BlockPoint;
import krispasi.omGames.bedwars.model.EventSettings;
import krispasi.omGames.bedwars.model.ShopLocation;
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
            BlockPoint corner1 = parseOptionalPoint(arenaSection.getString("corner_1"));
            BlockPoint corner2 = parseOptionalPoint(arenaSection.getString("corner_2"));
            int baseRadius = arenaSection.getInt("base-radius", 0);
            int lobbyHeight = arenaSection.getInt("lobby-height", 0);
            int baseGeneratorRadius = arenaSection.getInt("anti-build.base-generator-radius", 0);
            int advancedGeneratorRadius = arenaSection.getInt("anti-build.advanced-generator-radius", 0);
            GeneratorSettings generatorSettings = parseGeneratorSettings(arenaSection, arenaId);
            EventSettings eventSettings = parseEventSettings(arenaSection);

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
                    if (value == null || value.isBlank()) {
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
                    if (value == null || value.isBlank()) {
                        continue;
                    }
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
                    if (value == null || value.isBlank()) {
                        continue;
                    }
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
                    String raw = spawnSection.getString(key);
                    if (raw == null || raw.isBlank()) {
                        continue;
                    }
                    BlockPoint point = parsePoint(raw, "spawn", arenaId);
                    if (point != null) {
                        spawns.put(team, point);
                    }
                }
            }

            Map<TeamColor, ShopLocation> mainShops = new EnumMap<>(TeamColor.class);
            Map<TeamColor, ShopLocation> upgradeShops = new EnumMap<>(TeamColor.class);
            ConfigurationSection shopsSection = arenaSection.getConfigurationSection("Shops");
            if (shopsSection == null) {
                shopsSection = arenaSection.getConfigurationSection("shops");
            }
            if (shopsSection != null) {
                for (String teamKey : shopsSection.getKeys(false)) {
                    TeamColor team = TeamColor.fromKey(teamKey);
                    if (team == null) {
                        logger.warning("Unknown shop team in " + arenaId + ": " + teamKey);
                        continue;
                    }
                    ConfigurationSection teamSection = shopsSection.getConfigurationSection(teamKey);
                    if (teamSection == null) {
                        continue;
                    }
                    String mainRaw = teamSection.getString("main");
                    ShopLocation main = mainRaw == null || mainRaw.isBlank()
                            ? null
                            : parseShopLocation(mainRaw, "shop main", arenaId);
                    if (main != null) {
                        mainShops.put(team, main);
                    }
                    String upgradesRaw = teamSection.getString("upgrades");
                    ShopLocation upgrades = upgradesRaw == null || upgradesRaw.isBlank()
                            ? null
                            : parseShopLocation(upgradesRaw, "shop upgrades", arenaId);
                    if (upgrades != null) {
                        upgradeShops.put(team, upgrades);
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
                    baseRadius,
                    corner1,
                    corner2,
                    baseGeneratorRadius,
                    advancedGeneratorRadius,
                    generatorSettings,
                    eventSettings,
                    beds,
                    generators,
                    spawns,
                    mainShops,
                    upgradeShops
            );
            arenas.put(arenaId, arena);
        }

        return arenas;
    }

    private GeneratorSettings parseGeneratorSettings(ConfigurationSection arenaSection, String arenaId) {
        ConfigurationSection section = arenaSection.getConfigurationSection("generator-settings");
        GeneratorSettings defaults = GeneratorSettings.defaults();
        if (section == null) {
            return defaults;
        }

        long[] baseIronIntervals = parseSecondsList(section,
                "base-forge.iron.intervals-seconds",
                "base-forge.iron.intervals",
                defaults.getBaseIronIntervals(),
                5,
                arenaId);
        int[] baseIronAmounts = parseIntList(section,
                "base-forge.iron.amounts",
                defaults.getBaseIronAmounts(),
                5,
                arenaId);
        int[] baseIronCaps = parseIntList(section,
                "base-forge.iron.caps",
                defaults.getBaseIronCaps(),
                5,
                arenaId);

        long[] baseGoldIntervals = parseSecondsList(section,
                "base-forge.gold.intervals-seconds",
                "base-forge.gold.intervals",
                defaults.getBaseGoldIntervals(),
                5,
                arenaId);
        int[] baseGoldAmounts = parseIntList(section,
                "base-forge.gold.amounts",
                defaults.getBaseGoldAmounts(),
                5,
                arenaId);
        int[] baseGoldCaps = parseIntList(section,
                "base-forge.gold.caps",
                defaults.getBaseGoldCaps(),
                5,
                arenaId);

        long[] baseEmeraldIntervals = parseSecondsList(section,
                "base-forge.emerald.intervals-seconds",
                "base-forge.emerald.intervals",
                defaults.getBaseEmeraldIntervals(),
                5,
                arenaId);
        int[] baseEmeraldAmounts = parseIntList(section,
                "base-forge.emerald.amounts",
                defaults.getBaseEmeraldAmounts(),
                5,
                arenaId);
        int[] baseEmeraldCaps = parseIntList(section,
                "base-forge.emerald.caps",
                defaults.getBaseEmeraldCaps(),
                5,
                arenaId);

        long[] diamondIntervals = parseSecondsList(section,
                "diamond.intervals-seconds",
                "diamond.intervals",
                defaults.getDiamondIntervals(),
                3,
                arenaId);
        int diamondCap = section.getInt("diamond.cap", defaults.getDiamondCap());

        long[] emeraldIntervals = parseSecondsList(section,
                "emerald.intervals-seconds",
                "emerald.intervals",
                defaults.getEmeraldIntervals(),
                3,
                arenaId);
        int emeraldCap = section.getInt("emerald.cap", defaults.getEmeraldCap());

        double despawnSeconds = section.getDouble("resource-despawn-seconds",
                defaults.getResourceDespawnMillis() / 1000.0);
        long resourceDespawnMillis = Math.max(0L, Math.round(despawnSeconds * 1000.0));

        return new GeneratorSettings(
                baseIronIntervals,
                baseGoldIntervals,
                baseIronAmounts,
                baseGoldAmounts,
                baseIronCaps,
                baseGoldCaps,
                baseEmeraldIntervals,
                baseEmeraldAmounts,
                baseEmeraldCaps,
                diamondIntervals,
                emeraldIntervals,
                diamondCap,
                emeraldCap,
                resourceDespawnMillis
        );
    }

    private long[] parseSecondsList(ConfigurationSection section,
                                    String path,
                                    String legacyPath,
                                    long[] fallback,
                                    int expectedSize,
                                    String arenaId) {
        List<Double> values = readNumberList(section, path);
        if (values == null && legacyPath != null) {
            values = readNumberList(section, legacyPath);
        }
        if (values == null) {
            return fallback;
        }
        if (values.size() != expectedSize) {
            logger.warning("Invalid list size for " + path + " in " + arenaId + ": expected "
                    + expectedSize + ", got " + values.size());
            return fallback;
        }
        long[] result = new long[expectedSize];
        for (int i = 0; i < expectedSize; i++) {
            double seconds = values.get(i);
            result[i] = Math.max(0L, Math.round(seconds * 20.0));
        }
        return result;
    }

    private int[] parseIntList(ConfigurationSection section,
                               String path,
                               int[] fallback,
                               int expectedSize,
                               String arenaId) {
        List<Double> values = readNumberList(section, path);
        if (values == null) {
            return fallback;
        }
        if (values.size() != expectedSize) {
            logger.warning("Invalid list size for " + path + " in " + arenaId + ": expected "
                    + expectedSize + ", got " + values.size());
            return fallback;
        }
        int[] result = new int[expectedSize];
        for (int i = 0; i < expectedSize; i++) {
            result[i] = Math.max(0, (int) Math.round(values.get(i)));
        }
        return result;
    }

    private List<Double> readNumberList(ConfigurationSection section, String path) {
        if (section == null || path == null) {
            return null;
        }
        List<?> raw = section.getList(path);
        if (raw == null) {
            return null;
        }
        List<Double> values = new ArrayList<>();
        for (Object value : raw) {
            if (value instanceof Number number) {
                values.add(number.doubleValue());
                continue;
            }
            if (value instanceof String text) {
                try {
                    values.add(Double.parseDouble(text));
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
        }
        return values;
    }

    private EventSettings parseEventSettings(ConfigurationSection arenaSection) {
        ConfigurationSection section = arenaSection.getConfigurationSection("event-times");
        EventSettings defaults = EventSettings.defaults();
        if (section == null) {
            return defaults;
        }
        int tier2 = section.getInt("tier-2", defaults.getTier2Delay());
        int tier3 = section.getInt("tier-3", defaults.getTier3Delay());
        int bedBreak = section.getInt("bed-destruction", defaults.getBedDestructionDelay());
        int suddenDeath = section.getInt("sudden-death", defaults.getSuddenDeathDelay());
        int gameEnd = section.getInt("game-end", defaults.getGameEndDelay());
        return new EventSettings(tier2, tier3, bedBreak, suddenDeath, gameEnd);
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

    private BlockPoint parseOptionalPoint(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return BlockPoint.parse(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
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

    private ShopLocation parseShopLocation(String value, String label, String arenaId) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.trim().split("\\s+");
        if (parts.length < 3) {
            logger.warning("Invalid " + label + " location in " + arenaId + ": " + value);
            return null;
        }
        try {
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            float yaw = 0.0f;
            if (parts.length >= 4) {
                yaw = parseYaw(parts[3], label, arenaId);
            }
            return new ShopLocation(new BlockPoint(x, y, z), yaw);
        } catch (NumberFormatException ex) {
            logger.warning("Invalid " + label + " location in " + arenaId + ": " + value);
            return null;
        }
    }

    private float parseYaw(String value, String label, String arenaId) {
        if (value == null || value.isBlank()) {
            return 0.0f;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SOUTH", "S" -> 0.0f;
            case "WEST", "W" -> 90.0f;
            case "NORTH", "N" -> 180.0f;
            case "EAST", "E" -> 270.0f;
            default -> {
                try {
                    yield Float.parseFloat(value);
                } catch (NumberFormatException ex) {
                    logger.warning("Invalid " + label + " yaw in " + arenaId + ": " + value);
                    yield 0.0f;
                }
            }
        };
    }
}
