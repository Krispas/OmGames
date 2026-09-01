package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class HallsLevelTypeLoader {
    private HallsLevelTypeLoader() {
    }

    public static Map<String, HallsLevelType> loadLevelTypes(JavaPlugin plugin, File folder) {
        Map<String, HallsLevelType> levelTypes = new HashMap<>();
        if (folder == null || !folder.exists()) {
            HallsLevelType fallback = HallsLevelType.fallback("howling_corridors");
            levelTypes.put(fallback.id(), fallback);
            return Map.copyOf(levelTypes);
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".txt"));
        if (files != null) {
            for (File file : java.util.Arrays.stream(files).sorted(Comparator.comparing(File::getName)).toList()) {
                HallsLevelType levelType = loadLevelType(plugin, file);
                if (levelType != null) {
                    levelTypes.put(levelType.id(), levelType);
                }
            }
        }
        levelTypes.putIfAbsent("howling_corridors", HallsLevelType.fallback("howling_corridors"));
        return Map.copyOf(levelTypes);
    }

    private static HallsLevelType loadLevelType(JavaPlugin plugin, File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String fallbackId = file.getName().replaceFirst("\\.[^.]+$", "");
        String id = normalizeId(config.getString("id", fallbackId));
        if (id.isBlank()) {
            plugin.getLogger().warning("Skipping invalid Halls level type file " + file.getName() + ".");
            return null;
        }
        HallsLevelType fallback = HallsLevelType.fallback(id);
        return new HallsLevelType(
                id,
                config.getString("name", fallback.name()),
                normalizeId(config.getString("corridor-generation", fallback.corridorGeneration())),
                material(config.getString("materials.floor"), fallback.floor(), plugin, file),
                material(config.getString("materials.ceiling"), fallback.ceiling(), plugin, file),
                material(config.getString("materials.corridor-floor"), fallback.corridorFloor(), plugin, file),
                material(config.getString("materials.corridor-ceiling"), fallback.corridorCeiling(), plugin, file),
                material(config.getString("materials.light"), fallback.light(), plugin, file),
                palettes(config.getConfigurationSection("wall-palettes"), fallback.walls(), plugin, file),
                palettes(config.getConfigurationSection("pillar-palettes"), fallback.pillars(), plugin, file)
        );
    }

    private static List<HallsLevelType.BlockPalette> palettes(ConfigurationSection section,
                                                               List<HallsLevelType.BlockPalette> fallback,
                                                               JavaPlugin plugin,
                                                               File file) {
        if (section == null) {
            return fallback;
        }
        List<HallsLevelType.BlockPalette> palettes = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection row = section.getConfigurationSection(key);
            if (row == null) {
                continue;
            }
            Material block = material(row.getString("block"), null, plugin, file);
            if (block == null) {
                continue;
            }
            palettes.add(new HallsLevelType.BlockPalette(
                    block,
                    material(row.getString("special-block"), null, plugin, file),
                    clamp(row.getDouble("special-chance", 0.0), 0.0, 1.0)
            ));
        }
        return palettes.isEmpty() ? fallback : List.copyOf(palettes);
    }

    private static Material material(String value, Material fallback, JavaPlugin plugin, File file) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(value.trim());
        if (material == null || !material.isBlock()) {
            plugin.getLogger().warning("Invalid Halls level type material '" + value + "' in " + file.getName() + ".");
            return fallback;
        }
        return material;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
