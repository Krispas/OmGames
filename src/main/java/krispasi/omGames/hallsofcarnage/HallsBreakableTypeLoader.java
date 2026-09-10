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

public final class HallsBreakableTypeLoader {
    private HallsBreakableTypeLoader() {
    }

    public static Map<String, HallsBreakableType> loadBreakableTypes(JavaPlugin plugin, File folder) {
        if (folder == null || !folder.isDirectory()) {
            return fallbackTypes();
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".txt")
                || name.endsWith(".yml")
                || name.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            return fallbackTypes();
        }
        Map<String, List<HallsBreakableType.LootEntry>> lootPools = loadLootPools(plugin, new File(folder.getParentFile(), "breakable_loot_pools"));
        Map<String, HallsBreakableType> types = new LinkedHashMap<>();
        for (File file : java.util.Arrays.stream(files).sorted(Comparator.comparing(File::getName)).toList()) {
            try {
                HallsBreakableType type = loadType(file, lootPools);
                types.put(type.id(), type);
            } catch (IllegalArgumentException ex) {
                if (plugin != null) {
                    plugin.getLogger().warning("Failed to load Halls breakable " + file + ": " + ex.getMessage());
                }
            }
        }
        return types.isEmpty() ? fallbackTypes() : Map.copyOf(types);
    }

    private static HallsBreakableType loadType(File file, Map<String, List<HallsBreakableType.LootEntry>> lootPools) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String id = normalizeId(yaml.getString("id", stripExtension(file.getName())));
        String rarity = normalizeId(yaml.getString("rarity", "common"));
        String breakMessage = yaml.getString("break-message", "You broke " + id + ".");
        float hitboxHeight = (float) yaml.getDouble("hitbox-height", 1.0);
        Material particleMaterial = material(yaml.getString("particle-material"), Material.BARREL);
        List<String> scrapDrops = parseScrapDrops(yaml);
        List<HallsBreakableType.Part> parts = parseParts(yaml);
        List<HallsBreakableType.LootEntry> loot = parseLoot(yaml);
        if (loot.isEmpty()) {
            loot = lootPools.getOrDefault(rarity, lootPools.getOrDefault("common", List.of()));
        }
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("parts must not be empty");
        }
        if (loot.isEmpty()) {
            throw new IllegalArgumentException("loot must not be empty");
        }
        return new HallsBreakableType(id, rarity, breakMessage, hitboxHeight, particleMaterial, scrapDrops, parts, loot);
    }

    private static List<HallsBreakableType.Part> parseParts(YamlConfiguration yaml) {
        List<HallsBreakableType.Part> parts = new ArrayList<>();
        for (Map<?, ?> map : yaml.getMapList("parts")) {
            Material material = material(String.valueOf(map.get("material")), Material.BARREL);
            List<Integer> offset = intList(map.get("offset"));
            int x = offset.size() > 0 ? offset.get(0) : intValue(map.get("offset-x"), 0);
            int y = offset.size() > 1 ? offset.get(1) : intValue(map.get("offset-y"), 0);
            int z = offset.size() > 2 ? offset.get(2) : intValue(map.get("offset-z"), 0);
            parts.add(new HallsBreakableType.Part(x, y, z, material));
        }
        return parts;
    }

    private static List<String> parseScrapDrops(YamlConfiguration yaml) {
        List<String> values = yaml.getStringList("scrap-drops").stream()
                .map(HallsBreakableTypeLoader::normalizeId)
                .filter(value -> !value.isBlank())
                .toList();
        if (values.isEmpty()) {
            values = List.of("wood_scrap", "iron_scrap");
        }
        return values.stream().limit(2).toList();
    }

    private static List<HallsBreakableType.LootEntry> parseLoot(YamlConfiguration yaml) {
        List<HallsBreakableType.LootEntry> loot = new ArrayList<>();
        for (Map<?, ?> map : yaml.getMapList("loot")) {
            String item = normalizeId(String.valueOf(map.get("item")));
            int weight = intValue(map.get("weight"), 1);
            Object rawAmount = map.containsKey("amount") ? map.get("amount") : 1;
            int[] amount = amountRange(rawAmount);
            loot.add(new HallsBreakableType.LootEntry(item, weight, amount[0], amount[1]));
        }
        ConfigurationSection section = yaml.getConfigurationSection("loot-table");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                loot.add(new HallsBreakableType.LootEntry(normalizeId(key), section.getInt(key, 1), 1, 1));
            }
        }
        return loot;
    }

    private static Map<String, List<HallsBreakableType.LootEntry>> loadLootPools(JavaPlugin plugin, File folder) {
        if (folder == null || !folder.isDirectory()) {
            return fallbackLootPools();
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".txt")
                || name.endsWith(".yml")
                || name.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            return fallbackLootPools();
        }
        Map<String, List<HallsBreakableType.LootEntry>> pools = new LinkedHashMap<>();
        for (File file : java.util.Arrays.stream(files).sorted(Comparator.comparing(File::getName)).toList()) {
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                String rarity = normalizeId(yaml.getString("rarity", stripExtension(file.getName())));
                List<HallsBreakableType.LootEntry> loot = parseLoot(yaml);
                if (!rarity.isBlank() && !loot.isEmpty()) {
                    pools.put(rarity, List.copyOf(loot));
                }
            } catch (RuntimeException ex) {
                if (plugin != null) {
                    plugin.getLogger().warning("Failed to load Halls breakable loot pool " + file + ": " + ex.getMessage());
                }
            }
        }
        if (!pools.containsKey("common")) {
            pools.putAll(fallbackLootPools());
        }
        return Map.copyOf(pools);
    }

    private static int[] amountRange(Object value) {
        if (value instanceof Number number) {
            int amount = Math.max(1, number.intValue());
            return new int[]{amount, amount};
        }
        String raw = String.valueOf(value).trim();
        String[] parts = raw.split("-", 2);
        int min = parseInt(parts[0], 1);
        int max = parts.length > 1 ? parseInt(parts[1], min) : min;
        return new int[]{Math.max(1, min), Math.max(Math.max(1, min), max)};
    }

    private static List<Integer> intList(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        List<Integer> ints = new ArrayList<>();
        for (Object entry : raw) {
            ints.add(intValue(entry, 0));
        }
        return ints;
    }

    private static Material material(String name, Material fallback) {
        if (name == null || name.equals("null")) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return parseInt(String.valueOf(value), fallback);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
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

    private static Map<String, HallsBreakableType> fallbackTypes() {
        Map<String, HallsBreakableType> types = new LinkedHashMap<>();
        addFallback(types, "barrel", "You broke open a dusty barrel.", 1.0f, Material.BARREL,
                List.of(new HallsBreakableType.Part(0, 0, 0, Material.BARREL)),
                List.of("wood_scrap", "iron_scrap"), fallbackLootPools().get("common"));
        addFallback(types, "chair", "You kicked apart a wooden chair.", 1.0f, Material.OAK_STAIRS,
                List.of(new HallsBreakableType.Part(0, 0, 0, Material.OAK_STAIRS),
                        new HallsBreakableType.Part(0, 1, 0, Material.OAK_TRAPDOOR)),
                List.of("wood_scrap", "iron_scrap"), fallbackLootPools().get("common"));
        return Map.copyOf(types);
    }

    private static void addFallback(Map<String, HallsBreakableType> types,
                                    String id,
                                    String breakMessage,
                                    float hitboxHeight,
                                    Material particleMaterial,
                                    List<HallsBreakableType.Part> parts,
                                    List<String> scrapDrops,
                                    List<HallsBreakableType.LootEntry> loot) {
        types.put(id, new HallsBreakableType(id, "common", breakMessage, hitboxHeight, particleMaterial, scrapDrops, parts, loot));
    }

    private static HallsBreakableType.LootEntry loot(String item, int weight) {
        return new HallsBreakableType.LootEntry(item, weight, 1, 1);
    }

    private static Map<String, List<HallsBreakableType.LootEntry>> fallbackLootPools() {
        Map<String, List<HallsBreakableType.LootEntry>> pools = new LinkedHashMap<>();
        pools.put("common", List.of(
                loot("scrap", 200),
                loot("coin", 20),
                loot("food", 18),
                loot("utility", 8),
                loot("weapon", 7),
                loot("armor", 7),
                loot("blueprint", 6),
                loot("rare_food", 2),
                loot("rare_utility", 1),
                loot("rare_weapon", 1),
                loot("rare_armor", 1),
                loot("rare_blueprint", 1)
        ));
        pools.put("rare", List.of(
                loot("scrap", 160),
                loot("coin", 35),
                loot("food", 14),
                loot("utility", 14),
                loot("weapon", 12),
                loot("armor", 12),
                loot("blueprint", 10),
                loot("rare_food", 6),
                loot("rare_utility", 4),
                loot("rare_weapon", 4),
                loot("rare_armor", 4),
                loot("rare_blueprint", 3)
        ));
        return pools;
    }
}
