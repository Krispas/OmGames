package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class HallsTrapTypeLoader {
    private HallsTrapTypeLoader() {
    }

    public static Map<String, HallsTrapType> loadTrapTypes(JavaPlugin plugin, File folder) {
        if (folder == null || !folder.isDirectory()) {
            return fallbackTypes();
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".txt")
                || name.endsWith(".yml")
                || name.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            return fallbackTypes();
        }
        Map<String, HallsTrapType> types = new LinkedHashMap<>();
        for (File file : java.util.Arrays.stream(files).sorted(Comparator.comparing(File::getName)).toList()) {
            try {
                HallsTrapType type = loadType(file);
                types.put(type.id(), type);
            } catch (IllegalArgumentException ex) {
                if (plugin != null) {
                    plugin.getLogger().warning("Failed to load Halls trap " + file + ": " + ex.getMessage());
                }
            }
        }
        return types.isEmpty() ? fallbackTypes() : Map.copyOf(types);
    }

    private static HallsTrapType loadType(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String id = normalizeId(yaml.getString("id", stripExtension(file.getName())));
        String kind = normalizeId(yaml.getString("kind", id));
        if (id.isBlank() || kind.isBlank()) {
            throw new IllegalArgumentException("id and kind must not be blank");
        }
        return new HallsTrapType(
                id,
                kind,
                yaml.getInt("weight", 1),
                yaml.getStringList("level-types").stream().map(HallsTrapTypeLoader::normalizeId).filter(value -> !value.isBlank()).toList(),
                material(yaml.getString("block-material"), Material.IRON_TRAPDOOR),
                material(yaml.getString("ceiling-material"), Material.POINTED_DRIPSTONE),
                material(yaml.getString("model-material"), Material.IRON_NUGGET),
                yaml.getString("item-model", ""),
                (float) yaml.getDouble("model-scale", 1.0),
                material(yaml.getString("bridge-material"), Material.SPRUCE_PLANKS),
                yaml.getInt("hole.min-size", yaml.getInt("min-size", 1)),
                yaml.getInt("hole.max-size", yaml.getInt("max-size", 1)),
                yaml.getInt("hole.depth", yaml.getInt("depth", 10)),
                yaml.getDouble("damage", defaultDamage(kind)),
                yaml.getDouble("radius", defaultRadius(kind)),
                yaml.getInt("interval-ticks", defaultInterval(kind)),
                yaml.getInt("active-ticks", defaultActiveTicks(kind)),
                (float) yaml.getDouble("explosion-power", 2.4)
        );
    }

    private static double defaultDamage(String kind) {
        return switch (kind) {
            case "bear_trap", "wall_spikes" -> 12.0;
            case "proximity_mine" -> 18.0;
            case "poison_darts" -> 4.0;
            default -> 200.0;
        };
    }

    private static double defaultRadius(String kind) {
        return switch (kind) {
            case "poison_darts" -> 5.5;
            case "swinging_blade" -> 1.15;
            default -> 1.0;
        };
    }

    private static int defaultInterval(String kind) {
        return switch (kind) {
            case "wall_spikes" -> 70;
            case "falling_ice" -> 90;
            case "poison_darts" -> 55;
            default -> 60;
        };
    }

    private static int defaultActiveTicks(String kind) {
        return switch (kind) {
            case "wall_spikes" -> 12;
            default -> 16;
        };
    }

    private static Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    private static String normalizeId(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static Map<String, HallsTrapType> fallbackTypes() {
        Map<String, HallsTrapType> types = new LinkedHashMap<>();
        add(types, "hole", "hole", 3, List.of(), Material.AIR, Material.AIR, Material.IRON_NUGGET, "", 1.0f, Material.SPRUCE_PLANKS, 5, 15, 10, 200.0, 1.0, 60, 16, 2.4f);
        add(types, "bear_trap", "bear_trap", 4, List.of(), Material.IRON_TRAPDOOR, Material.AIR, Material.IRON_NUGGET, "", 1.0f, Material.SPRUCE_PLANKS, 1, 1, 10, 12.0, 1.0, 60, 16, 2.4f);
        add(types, "proximity_mine", "proximity_mine", 3, List.of(), Material.STONE_PRESSURE_PLATE, Material.AIR, Material.IRON_NUGGET, "", 1.0f, Material.SPRUCE_PLANKS, 1, 1, 10, 18.0, 1.0, 60, 16, 2.4f);
        add(types, "swinging_blade", "swinging_blade", 3, List.of(), Material.IRON_BARS, Material.AIR, Material.IRON_NUGGET, "", 1.0f, Material.SPRUCE_PLANKS, 1, 1, 10, 200.0, 1.15, 60, 16, 2.4f);
        add(types, "wall_spikes", "wall_spikes", 3, List.of(), Material.POINTED_DRIPSTONE, Material.AIR, Material.IRON_NUGGET, "", 1.0f, Material.SPRUCE_PLANKS, 1, 1, 10, 12.0, 1.1, 70, 12, 2.4f);
        add(types, "falling_ice", "falling_ice", 2, List.of("frozen_halls"), Material.AIR, Material.POINTED_DRIPSTONE, Material.IRON_NUGGET, "", 1.0f, Material.SPRUCE_PLANKS, 1, 1, 10, 200.0, 1.0, 90, 1, 2.4f);
        add(types, "poison_darts", "poison_darts", 2, List.of("deep_crypt"), Material.DISPENSER, Material.AIR, Material.IRON_NUGGET, "", 1.0f, Material.SPRUCE_PLANKS, 1, 1, 10, 4.0, 5.5, 55, 1, 2.4f);
        return Map.copyOf(types);
    }

    private static void add(Map<String, HallsTrapType> types,
                            String id,
                            String kind,
                            int weight,
                            List<String> levelTypes,
                            Material blockMaterial,
                            Material ceilingMaterial,
                            Material modelMaterial,
                            String itemModel,
                            float modelScale,
                            Material bridgeMaterial,
                            int minSize,
                            int maxSize,
                            int depth,
                            double damage,
                            double radius,
                            int intervalTicks,
                            int activeTicks,
                            float explosionPower) {
        types.put(id, new HallsTrapType(id, kind, weight, levelTypes, blockMaterial, ceilingMaterial,
                modelMaterial, itemModel, modelScale, bridgeMaterial, minSize, maxSize, depth, damage,
                radius, intervalTicks, activeTicks, explosionPower));
    }
}
