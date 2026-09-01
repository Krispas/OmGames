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

public final class HallsItemTypeLoader {
    private HallsItemTypeLoader() {
    }

    public static Map<String, HallsItemType> loadItemTypes(JavaPlugin plugin, File folder) {
        if (folder == null || !folder.isDirectory()) {
            return Map.of();
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".txt")
                || name.endsWith(".yml")
                || name.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            return Map.of();
        }
        Map<String, HallsItemType> items = new LinkedHashMap<>();
        for (File file : java.util.Arrays.stream(files).sorted(Comparator.comparing(File::getName)).toList()) {
            try {
                HallsItemType item = loadItem(file);
                items.put(item.id(), item);
            } catch (IllegalArgumentException ex) {
                if (plugin != null) {
                    plugin.getLogger().warning("Failed to load Halls item " + file + ": " + ex.getMessage());
                }
            }
        }
        return Map.copyOf(items);
    }

    private static HallsItemType loadItem(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String id = normalizeId(yaml.getString("id", stripExtension(file.getName())));
        String name = yaml.getString("name", id);
        String category = normalizeId(yaml.getString("category", "utility"));
        String rarity = normalizeId(yaml.getString("rarity", "normal"));
        Material material = material(yaml.getString("material"), Material.PAPER);
        String itemModel = yaml.getString("item-model", "");
        int maxStackSize = Math.max(1, yaml.getInt("max-stack-size", 1));
        List<String> lore = yaml.getStringList("lore");
        Map<String, Integer> recipe = recipe(yaml.getConfigurationSection("recipe"));
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        return new HallsItemType(id, name, category, rarity, material, itemModel, maxStackSize, lore, recipe);
    }

    private static Map<String, Integer> recipe(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Integer> recipe = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            recipe.put(normalizeId(key), Math.max(0, section.getInt(key, 0)));
        }
        return recipe;
    }

    private static Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
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
