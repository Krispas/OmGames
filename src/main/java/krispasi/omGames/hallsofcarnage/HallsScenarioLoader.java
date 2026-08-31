package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
        int floorCount = countFloors(config);
        if (id.isBlank() || name == null || name.isBlank()) {
            plugin.getLogger().warning("Skipping invalid Halls scenario file " + file.getName() + ".");
            return null;
        }
        return new HallsScenario(id, name, difficulty, List.copyOf(description), minPlayers, maxPlayers, floorCount);
    }

    private static int countFloors(YamlConfiguration config) {
        int highest = 0;
        for (Object row : config.getList("floors", List.of())) {
            if (!(row instanceof java.util.Map<?, ?> map)) {
                continue;
            }
            Object number = map.get("number");
            highest = Math.max(highest, highestFloor(number));
        }
        return highest;
    }

    private static int highestFloor(Object value) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (!(value instanceof String text)) {
            return 0;
        }
        String trimmed = text.trim();
        int dash = trimmed.indexOf('-');
        if (dash >= 0 && dash + 1 < trimmed.length()) {
            trimmed = trimmed.substring(dash + 1);
        }
        try {
            return Math.max(0, Integer.parseInt(trimmed.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
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
}
