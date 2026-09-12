package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class HallsVegetationTypeLoader {
    private HallsVegetationTypeLoader() {
    }

    public static Map<String, HallsVegetationType> loadVegetationTypes(JavaPlugin plugin, File folder) {
        if (folder == null || !folder.isDirectory()) {
            return fallbackTypes();
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".txt")
                || name.endsWith(".yml")
                || name.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            return fallbackTypes();
        }
        Map<String, HallsVegetationType> types = new LinkedHashMap<>();
        for (File file : java.util.Arrays.stream(files).sorted(Comparator.comparing(File::getName)).toList()) {
            try {
                HallsVegetationType type = loadType(file);
                if (type != null) {
                    types.put(type.id(), type);
                }
            } catch (RuntimeException ex) {
                if (plugin != null) {
                    plugin.getLogger().warning("Failed to load Halls vegetation " + file + ": " + ex.getMessage());
                }
            }
        }
        return types.isEmpty() ? fallbackTypes() : Map.copyOf(types);
    }

    private static HallsVegetationType loadType(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String id = normalizeId(yaml.getString("id", stripExtension(file.getName())));
        if (id.isBlank()) {
            return null;
        }
        Material material = material(yaml.getString("material"), fallbackMaterial("SHORT_GRASS", Material.FERN));
        if (material == null || !material.isBlock()) {
            material = fallbackMaterial("SHORT_GRASS", Material.FERN);
        }
        return new HallsVegetationType(
                id,
                material,
                yaml.getString("block-data", ""),
                yaml.getDouble("offset-y", 0.0),
                (float) yaml.getDouble("scale", 1.0),
                yaml.getBoolean("random-yaw", true)
        );
    }

    private static Map<String, HallsVegetationType> fallbackTypes() {
        Map<String, HallsVegetationType> types = new LinkedHashMap<>();
        addFallback(types, "grass", "SHORT_GRASS", "FERN");
        addFallback(types, "deadbush", "DEAD_BUSH", "FERN");
        addFallback(types, "dry_grass", "DRY_GRASS", "DEAD_BUSH");
        addFallback(types, "bush", "BUSH", "AZALEA");
        return Map.copyOf(types);
    }

    private static void addFallback(Map<String, HallsVegetationType> types, String id, String materialName, String fallbackName) {
        Material material = fallbackMaterial(materialName, Material.matchMaterial(fallbackName));
        if (material != null) {
            types.put(id, new HallsVegetationType(id, material, "", 0.0, 1.0f, true));
        }
    }

    private static Material material(String name, Material fallback) {
        if (name == null || name.isBlank() || name.equals("null")) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    private static Material fallbackMaterial(String primaryName, Material fallback) {
        Material material = Material.matchMaterial(primaryName);
        return material == null ? fallback : material;
    }

    private static String normalizeId(String id) {
        if (id == null) {
            return "";
        }
        return id.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
