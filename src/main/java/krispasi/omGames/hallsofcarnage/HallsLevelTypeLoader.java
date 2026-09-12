package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
                palettes(config.getConfigurationSection("pillar-palettes"), fallback.pillars(), plugin, file),
                normalizedStringList(config.getStringList("monsters.common"), fallback.commonMonsters()),
                normalizedStringList(config.getStringList("monsters.special"), fallback.specialMonsters()),
                clamp(config.getDouble("vegetation.chance", fallback.vegetationChance()), 0.0, 1.0),
                vegetation(config.getConfigurationSection("vegetation.types"), fallback.vegetation())
        );
    }

    private static List<HallsLevelType.VegetationEntry> vegetation(ConfigurationSection section,
                                                                   List<HallsLevelType.VegetationEntry> fallback) {
        if (section == null) {
            return fallback;
        }
        List<HallsLevelType.VegetationEntry> entries = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            int weight = section.getInt(key, 0);
            if (weight > 0) {
                entries.add(new HallsLevelType.VegetationEntry(normalizeId(key), weight));
            }
        }
        return entries.isEmpty() ? fallback : List.copyOf(entries);
    }

    private static List<String> normalizedStringList(List<String> values, List<String> fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        List<String> normalized = values.stream()
                .map(HallsLevelTypeLoader::normalizeId)
                .filter(value -> !value.isBlank())
                .toList();
        return normalized.isEmpty() ? fallback : List.copyOf(normalized);
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
            Map<Material, Double> specialBlocks = specialBlocks(row, plugin, file);
            Material legacySpecial = material(row.getString("special-block"), null, plugin, file);
            if (legacySpecial != null) {
                specialBlocks.putIfAbsent(legacySpecial, clamp(row.getDouble("special-chance", 0.0), 0.0, 1.0));
            }
            palettes.add(new HallsLevelType.BlockPalette(block, specialBlocks));
        }
        return palettes.isEmpty() ? fallback : List.copyOf(palettes);
    }

    private static Map<Material, Double> specialBlocks(ConfigurationSection row, JavaPlugin plugin, File file) {
        ConfigurationSection section = row.getConfigurationSection("special-blocks");
        if (section == null) {
            return new LinkedHashMap<>();
        }
        Map<Material, Double> specialBlocks = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            String materialName = entry == null ? key : entry.getString("block", key);
            double chance = entry == null ? section.getDouble(key, 0.0) : entry.getDouble("chance", 0.0);
            Material material = material(materialName, null, plugin, file);
            if (material != null) {
                specialBlocks.put(material, clamp(chance, 0.0, 1.0));
            }
        }
        return specialBlocks;
    }

    private static Material material(String value, Material fallback, JavaPlugin plugin, File file) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(value.trim().toUpperCase(Locale.ROOT));
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
