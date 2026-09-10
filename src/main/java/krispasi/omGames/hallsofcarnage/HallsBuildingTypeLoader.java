package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class HallsBuildingTypeLoader {
    private HallsBuildingTypeLoader() {
    }

    public static Map<String, HallsBuildingType> loadBuildingTypes(JavaPlugin plugin, File folder) {
        if (folder == null || !folder.isDirectory()) {
            return Map.of();
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".txt") || name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            return Map.of();
        }
        Map<String, HallsBuildingType> types = new LinkedHashMap<>();
        for (File file : java.util.Arrays.stream(files).sorted(Comparator.comparing(File::getName)).toList()) {
            try {
                HallsBuildingType type = loadType(file);
                types.put(type.id(), type);
            } catch (IllegalArgumentException ex) {
                if (plugin != null) {
                    plugin.getLogger().warning("Failed to load Halls building " + file + ": " + ex.getMessage());
                }
            }
        }
        return Map.copyOf(types);
    }

    private static HallsBuildingType loadType(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String id = normalizeId(yaml.getString("id", stripExtension(file.getName())));
        String name = yaml.getString("name", id);
        String size = normalizeId(yaml.getString("size", "small"));
        String blueprint = normalizeId(yaml.getString("blueprint", id + "_blueprint"));
        boolean implemented = yaml.getBoolean("implemented", false);
        Map<Integer, HallsBuildingType.Level> levels = new LinkedHashMap<>();
        ConfigurationSection levelSection = yaml.getConfigurationSection("levels");
        if (levelSection != null) {
            for (String key : levelSection.getKeys(false)) {
                int level = parseLevel(key);
                if (level < 1 || level > 3) {
                    continue;
                }
                ConfigurationSection section = levelSection.getConfigurationSection(key);
                if (section != null) {
                    levels.put(level, parseLevel(section));
                }
            }
        }
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        return new HallsBuildingType(id, name, size, blueprint, implemented, levels);
    }

    private static HallsBuildingType.Level parseLevel(ConfigurationSection section) {
        return new HallsBuildingType.Level(
                parseParts(section.getMapList("parts")),
                parseCost(section.getConfigurationSection("upgrade-cost")),
                section.getStringList("interaction.give-items").stream()
                        .map(HallsBuildingTypeLoader::normalizeId)
                        .filter(value -> !value.isBlank())
                        .toList()
        );
    }

    private static List<HallsBuildingType.Part> parseParts(List<Map<?, ?>> rows) {
        List<HallsBuildingType.Part> parts = new ArrayList<>();
        for (Map<?, ?> row : rows) {
            Object materialValue = row.get("material");
            Material material = material(materialValue == null ? "BARREL" : String.valueOf(materialValue), Material.BARREL);
            double[] offset = vector(row.get("offset"), 0.0, 0.0, 0.0);
            double[] scale = vector(row.get("scale"), 1.0, 1.0, 1.0);
            double[] rotation = vector(row.get("rotation"), 0.0, 0.0, 0.0);
            if (rotation[0] == 0.0 && rotation[1] == 0.0 && rotation[2] == 0.0) {
                rotation = vector(row.get("euler"), 0.0, 0.0, 0.0);
            }
            String blockData = stringValue(row.get("block-data"));
            parts.add(new HallsBuildingType.Part(material, blockData, offset[0], offset[1], offset[2],
                    scale[0], scale[1], scale[2], rotation[0], rotation[1], rotation[2]));
        }
        return List.copyOf(parts);
    }

    private static Map<String, Integer> parseCost(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Integer> cost = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            int amount = Math.max(0, section.getInt(key, 0));
            if (amount > 0) {
                cost.put(normalizeId(key), amount);
            }
        }
        return Map.copyOf(cost);
    }

    private static double[] vector(Object value, double x, double y, double z) {
        if (value instanceof List<?> list && list.size() >= 3) {
            return new double[]{number(list.get(0), x), number(list.get(1), y), number(list.get(2), z)};
        }
        return new double[]{x, y, z};
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    private static int parseLevel(String key) {
        String normalized = normalizeId(key).replace("level_", "");
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
