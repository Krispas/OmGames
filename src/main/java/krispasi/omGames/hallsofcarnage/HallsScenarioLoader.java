package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class HallsScenarioLoader {
    private HallsScenarioLoader() {
    }

    public static List<HallsScenario> loadScenarios(JavaPlugin plugin, File folder) {
        if (folder == null || !folder.exists()) {
            return List.of();
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".txt"));
        if (files == null || files.length == 0) {
            return List.of();
        }
        List<HallsScenario> scenarios = new ArrayList<>();
        for (File file : files) {
            HallsScenario scenario = loadScenario(plugin, file);
            if (scenario != null) {
                scenarios.add(scenario);
            }
        }
        scenarios.sort(Comparator.comparing(HallsScenario::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(scenarios);
    }

    private static HallsScenario loadScenario(JavaPlugin plugin, File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String fallbackId = file.getName().replaceFirst("\\.[^.]+$", "");
        String id = normalizeId(config.getString("id", fallbackId));
        String name = config.getString("name", fallbackId);
        String difficulty = config.getString("difficulty", "Unknown");
        List<String> description = config.getStringList("description");
        if (description.isEmpty()) {
            String singleLine = config.getString("description");
            if (singleLine != null && !singleLine.isBlank()) {
                description = List.of(singleLine);
            }
        }
        ConfigurationSection players = config.getConfigurationSection("players");
        int minPlayers = players == null ? 1 : Math.max(1, players.getInt("min", 1));
        int maxPlayers = players == null ? 6 : clamp(players.getInt("max", 6), minPlayers, 6);
        Map<String, List<String>> allowedItems = loadStringListMap(config.getConfigurationSection("allowed-items"));
        Map<String, List<String>> blueprintPools = loadStringListMap(config.getConfigurationSection("blueprint-pools"));
        List<HallsScenario.FloorDefinition> floors = loadFloors(config);
        int floorCount = floors.stream().mapToInt(HallsScenario.FloorDefinition::lastFloor).max().orElse(0);
        if (id.isBlank() || name == null || name.isBlank()) {
            plugin.getLogger().warning("Skipping invalid Halls scenario file " + file.getName() + ".");
            return null;
        }
        return new HallsScenario(id, name, difficulty, List.copyOf(description), minPlayers, maxPlayers,
                floorCount, allowedItems, blueprintPools, List.copyOf(floors), debugLines(file, config, floors));
    }

    private static Map<String, List<String>> loadStringListMap(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            values.put(normalizeId(key), section.getStringList(key).stream()
                    .map(HallsScenarioLoader::normalizeId)
                    .filter(value -> !value.isBlank())
                    .toList());
        }
        return Map.copyOf(values);
    }

    private static List<HallsScenario.FloorDefinition> loadFloors(YamlConfiguration config) {
        List<HallsScenario.FloorDefinition> floors = new ArrayList<>();
        for (Object row : config.getList("floors", List.of())) {
            if (!(row instanceof java.util.Map<?, ?> map)) {
                continue;
            }
            FloorRange range = floorRange(map.get("number"));
            if (range.lastFloor() <= 0) {
                continue;
            }
            floors.add(new HallsScenario.FloorDefinition(
                    range.firstFloor(),
                    range.lastFloor(),
                    stringValue(map.get("kind"), "exploration"),
                    normalizeId(stringValue(map.get("level-type"), "howling_corridors")),
                    stringValue(map.get("difficulty"), "0"),
                    positiveInt(map.get("rooms"), 8),
                    positiveInt(map.get("items"), 0),
                    positiveInt(map.get("breakables"), 16),
                    positiveInt(map.get("traps"), 5)
            ));
        }
        floors.sort(Comparator.comparingInt(HallsScenario.FloorDefinition::firstFloor));
        return floors;
    }

    private static List<String> debugLines(File file,
                                           YamlConfiguration config,
                                           List<HallsScenario.FloorDefinition> floors) {
        List<String> lines = new ArrayList<>();
        lines.add("source-file: " + file.getName());
        lines.add("parsed-floor-definitions:");
        if (floors.isEmpty()) {
            lines.add("  <none>");
        } else {
            for (HallsScenario.FloorDefinition floor : floors) {
                lines.add("  " + floor.firstFloor() + "-" + floor.lastFloor()
                        + " kind=" + floor.kind()
                        + " level-type=" + floor.levelType()
                        + " difficulty=" + floor.difficulty()
                        + " rooms=" + floor.rooms()
                        + " items=" + floor.items()
                        + " breakables=" + floor.breakables()
                        + " traps=" + floor.traps());
            }
        }
        lines.add("loaded-yaml:");
        config.saveToString().lines()
                .map(line -> "  " + line)
                .forEach(lines::add);
        return List.copyOf(lines);
    }

    private static FloorRange floorRange(Object value) {
        if (value instanceof Number number) {
            int floor = Math.max(0, number.intValue());
            return new FloorRange(floor, floor);
        }
        if (!(value instanceof String text)) {
            return new FloorRange(0, 0);
        }
        String trimmed = text.trim();
        int dash = trimmed.indexOf('-');
        if (dash >= 0 && dash + 1 < trimmed.length()) {
            int first = parsePositiveInt(trimmed.substring(0, dash), 0);
            int last = parsePositiveInt(trimmed.substring(dash + 1), first);
            return new FloorRange(Math.min(first, last), Math.max(first, last));
        }
        int floor = parsePositiveInt(trimmed, 0);
        return new FloorRange(floor, floor);
    }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static int positiveInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        return parsePositiveInt(String.valueOf(value), fallback);
    }

    private static int parsePositiveInt(String text, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(text.trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String normalizeId(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record FloorRange(int firstFloor, int lastFloor) {
    }
}
