package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

final class HallsMonsterTypeLoader {
    private HallsMonsterTypeLoader() {
    }

    static Map<String, HallsMonsterType> loadMonsterTypes(JavaPlugin plugin, File folder) {
        Map<String, HallsMonsterType> monsters = new LinkedHashMap<>();
        addVanillaFallbacks(monsters);
        if (folder == null || !folder.isDirectory()) {
            return Map.copyOf(monsters);
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".txt") || name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) {
            return Map.copyOf(monsters);
        }
        for (File file : java.util.Arrays.stream(files).sorted(Comparator.comparing(File::getName)).toList()) {
            try {
                HallsMonsterType type = loadMonsterType(file);
                monsters.put(type.id(), type);
            } catch (IllegalArgumentException ex) {
                if (plugin != null) {
                    plugin.getLogger().warning("Failed to load Halls monster " + file + ": " + ex.getMessage());
                }
            }
        }
        return Map.copyOf(monsters);
    }

    private static HallsMonsterType loadMonsterType(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String id = normalizeId(yaml.getString("id", stripExtension(file.getName())));
        EntityType entityType = entityType(yaml.getString("entity-type", id));
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (entityType == null) {
            throw new IllegalArgumentException("invalid entity-type");
        }
        return new HallsMonsterType(
                id,
                yaml.getString("name", id),
                entityType,
                Math.max(1.0, yaml.getDouble("health", defaultHealth(entityType))),
                yaml.getBoolean("baby", false),
                Math.max(0, yaml.getInt("slime-size", 0)),
                material(yaml.getString("equipment.main-hand"), Material.AIR),
                armor(yaml.getConfigurationSection("equipment.armor"))
        );
    }

    private static Map<String, Material> armor(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Material> armor = new LinkedHashMap<>();
        for (String slot : section.getKeys(false)) {
            Material material = material(section.getString(slot), Material.AIR);
            if (!material.isAir()) {
                armor.put(normalizeId(slot), material);
            }
        }
        return Map.copyOf(armor);
    }

    private static void addVanillaFallbacks(Map<String, HallsMonsterType> monsters) {
        for (String id : ListIds.DEFAULT_MONSTERS) {
            EntityType entityType = entityType(id);
            if (entityType != null) {
                monsters.put(id, new HallsMonsterType(id, title(id), entityType, defaultHealth(entityType),
                        false, 0, Material.AIR, Map.of()));
            }
        }
    }

    private static EntityType entityType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return EntityType.valueOf(normalizeId(value).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Material material(String value, Material fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(value.toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    private static double defaultHealth(EntityType type) {
        return switch (type) {
            case CREEPER -> 20.0;
            case SLIME -> 16.0;
            case CAVE_SPIDER -> 12.0;
            case BREEZE -> 30.0;
            default -> 20.0;
        };
    }

    private static String title(String id) {
        StringBuilder builder = new StringBuilder();
        for (String word : id.split("_")) {
            if (word.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return builder.toString();
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static final class ListIds {
        private static final java.util.List<String> DEFAULT_MONSTERS = java.util.List.of(
                "zombie", "creeper", "creaking", "skeleton", "cave_spider", "stray", "bogged", "husk", "breeze"
        );
    }
}
