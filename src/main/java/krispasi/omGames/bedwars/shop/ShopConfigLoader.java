package krispasi.omGames.bedwars.shop;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class ShopConfigLoader {
    private final File file;
    private final Logger logger;

    public ShopConfigLoader(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public ShopConfig load() {
        if (!file.exists()) {
            logger.warning("Shop config not found: " + file.getAbsolutePath());
            return ShopConfig.empty();
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection shopSection = config.getConfigurationSection("shop");
        if (shopSection == null) {
            logger.warning("Missing shop section in shop.yml.");
            return ShopConfig.empty();
        }

        Map<String, Map<Integer, String>> positionedEntries = new HashMap<>();
        Map<String, ShopItemDefinition> items = loadItems(shopSection.getConfigurationSection("items"), positionedEntries);
        Map<ShopCategoryType, ShopCategory> categories =
                loadCategories(shopSection.getConfigurationSection("categories"), positionedEntries);
        Map<ShopItemBehavior, Map<Integer, ShopItemDefinition>> tiered = buildTieredItems(items);

        return new ShopConfig(items, categories, tiered);
    }

    private Map<String, ShopItemDefinition> loadItems(ConfigurationSection itemsSection,
                                                      Map<String, Map<Integer, String>> positionedEntries) {
        Map<String, ShopItemDefinition> items = new HashMap<>();
        if (itemsSection == null) {
            logger.warning("Missing shop items section.");
            return items;
        }
        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection section = itemsSection.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            if (isItemSection(section)) {
                addItem(items, key, section, null, positionedEntries);
                continue;
            }
            String categoryKey = key;
            for (String nestedKey : section.getKeys(false)) {
                ConfigurationSection nestedSection = section.getConfigurationSection(nestedKey);
                if (nestedSection == null || !isItemSection(nestedSection)) {
                    continue;
                }
                addItem(items, nestedKey, nestedSection, categoryKey, positionedEntries);
            }
        }
        return items;
    }

    private boolean isItemSection(ConfigurationSection section) {
        return section.isSet("material");
    }

    private void addItem(Map<String, ShopItemDefinition> items,
                         String id,
                         ConfigurationSection section,
                         String categoryKey,
                         Map<String, Map<Integer, String>> positionedEntries) {
        ShopItemDefinition item = parseItem(id, section, categoryKey);
        if (item == null) {
            return;
        }
        if (items.containsKey(item.getId())) {
            logger.warning("Duplicate shop item id: " + item.getId());
            return;
        }
        items.put(item.getId(), item);
        recordPosition(positionedEntries, item.getId(), section, categoryKey);
    }

    private ShopItemDefinition parseItem(String id, ConfigurationSection section, String categoryKey) {
        String materialName = section.getString("material");
        Material material = parseMaterial(materialName);
        if (material == null) {
            logger.warning("Unknown material for shop item " + id + ": " + materialName);
            return null;
        }
        int amount = Math.max(1, section.getInt("amount", 1));
        ShopCost cost = parseCost(section.getConfigurationSection("cost"));
        boolean teamColor = section.getBoolean("team-color", false);
        int tier = section.getInt("tier", 0);
        Map<Enchantment, Integer> enchants = parseEnchants(section.getConfigurationSection("enchants"));
        List<PotionEffect> potionEffects = parsePotionEffects(section.getStringList("potion-effects"));
        ShopItemBehavior behavior = parseBehavior(section.getString("behavior"));
        if (behavior == null) {
            behavior = inferBehavior(categoryKey, material, potionEffects);
        }
        if (behavior == null) {
            behavior = ShopItemBehavior.UTILITY;
        }
        String displayName = section.getString("display-name");
        List<String> lore = section.getStringList("lore");
        String customItemId = section.getString("custom-item");
        Integer fireworkPower = null;
        FireworkEffect fireworkEffect = null;
        if (material == Material.FIREWORK_ROCKET) {
            ConfigurationSection fireworkSection = section.getConfigurationSection("firework");
            if (fireworkSection != null) {
                fireworkPower = fireworkSection.getInt("power");
                fireworkEffect = parseFireworkEffect(fireworkSection.getConfigurationSection("effect"));
            }
        }
        if (customItemId != null) {
            customItemId = customItemId.trim();
            if (customItemId.isBlank()) {
                customItemId = null;
            } else {
                customItemId = customItemId.toLowerCase(Locale.ROOT);
            }
        }

        return new ShopItemDefinition(id, material, amount, cost, behavior, teamColor, tier,
                enchants, potionEffects, displayName, lore, customItemId, fireworkPower, fireworkEffect);
    }

    private ShopCost parseCost(ConfigurationSection section) {
        if (section == null) {
            return new ShopCost(null, 0);
        }
        String materialName = section.getString("material");
        Material material = parseMaterial(materialName);
        int amount = section.getInt("amount", 0);
        return new ShopCost(material, amount);
    }

    private Material parseMaterial(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return null;
        }
        String normalized = materialName.trim().toUpperCase(Locale.ROOT);
        if ("GOLD".equals(normalized)) {
            normalized = "GOLD_INGOT";
        } else if ("IRON".equals(normalized)) {
            normalized = "IRON_INGOT";
        }
        return Material.matchMaterial(normalized);
    }

    private ShopItemBehavior parseBehavior(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return ShopItemBehavior.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            logger.warning("Unknown shop item behavior: " + value);
            return null;
        }
    }

    private ShopItemBehavior inferBehavior(String categoryKey,
                                           Material material,
                                           List<PotionEffect> potionEffects) {
        if (material == null) {
            return null;
        }
        if (potionEffects != null && !potionEffects.isEmpty()) {
            return ShopItemBehavior.POTION;
        }
        if (material == Material.POTION
                || material == Material.SPLASH_POTION
                || material == Material.LINGERING_POTION) {
            return ShopItemBehavior.POTION;
        }
        if (material == Material.SHEARS) {
            return ShopItemBehavior.SHEARS;
        }
        String name = material.name();
        if (name.endsWith("_PICKAXE")) {
            return ShopItemBehavior.PICKAXE;
        }
        if (name.endsWith("_AXE")) {
            return ShopItemBehavior.AXE;
        }
        if (name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS")) {
            return ShopItemBehavior.ARMOR;
        }
        if ("BOW".equals(name)) {
            return ShopItemBehavior.BOW;
        }
        if ("CROSSBOW".equals(name)) {
            return ShopItemBehavior.CROSSBOW;
        }
        if (categoryKey == null) {
            return null;
        }
        String normalized = categoryKey.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "blocks" -> ShopItemBehavior.BLOCK;
            case "melee" -> ShopItemBehavior.SWORD;
            case "ranged" -> ShopItemBehavior.UTILITY;
            case "armor" -> ShopItemBehavior.ARMOR;
            case "tools" -> ShopItemBehavior.UTILITY;
            case "utility", "miscellaneous" -> ShopItemBehavior.UTILITY;
            default -> null;
        };
    }

    private FireworkEffect parseFireworkEffect(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String typeRaw = section.getString("type", "BURST");
        FireworkEffect.Type type;
        try {
            type = FireworkEffect.Type.valueOf(typeRaw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            type = FireworkEffect.Type.BURST;
        }
        boolean flicker = section.getBoolean("flicker", false);
        boolean trail = section.getBoolean("trail", false);
        List<Color> colors = parseColors(section.getStringList("colors"));
        if (colors.isEmpty()) {
            colors = List.of(Color.RED);
        }
        List<Color> fadeColors = parseColors(section.getStringList("fade-colors"));
        FireworkEffect.Builder builder = FireworkEffect.builder().with(type).withColor(colors);
        if (!fadeColors.isEmpty()) {
            builder.withFade(fadeColors);
        }
        if (flicker) {
            builder.flicker(true);
        }
        if (trail) {
            builder.trail(true);
        }
        return builder.build();
    }

    private List<Color> parseColors(List<String> values) {
        List<Color> colors = new ArrayList<>();
        if (values == null) {
            return colors;
        }
        for (String raw : values) {
            Color color = parseColor(raw);
            if (color != null) {
                colors.add(color);
            }
        }
        return colors;
    }

    private Color parseColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("#")) {
            String hex = normalized.substring(1);
            return parseHexColor(hex);
        }
        if (normalized.startsWith("0X")) {
            return parseHexColor(normalized.substring(2));
        }
        try {
            DyeColor dye = DyeColor.valueOf(normalized);
            return dye.getColor();
        } catch (IllegalArgumentException ex) {
            return parseHexColor(normalized);
        }
    }

    private Color parseHexColor(String hex) {
        if (hex == null || hex.isBlank()) {
            return null;
        }
        try {
            int rgb = Integer.parseInt(hex, 16);
            return Color.fromRGB(rgb & 0xFFFFFF);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Map<Enchantment, Integer> parseEnchants(ConfigurationSection section) {
        Map<Enchantment, Integer> enchants = new HashMap<>();
        if (section == null) {
            return enchants;
        }
        for (String key : section.getKeys(false)) {
            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
            if (enchantment == null) {
                enchantment = Enchantment.getByName(key.toUpperCase(Locale.ROOT));
            }
            if (enchantment == null) {
                logger.warning("Unknown enchantment in shop config: " + key);
                continue;
            }
            int level = section.getInt(key, 1);
            enchants.put(enchantment, Math.max(1, level));
        }
        return enchants;
    }

    private List<PotionEffect> parsePotionEffects(List<String> rawEffects) {
        List<PotionEffect> effects = new ArrayList<>();
        for (String raw : rawEffects) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String[] parts = raw.split(":");
            if (parts.length < 2) {
                continue;
            }
            PotionEffectType type = PotionEffectType.getByName(parts[0].toUpperCase(Locale.ROOT));
            if (type == null) {
                logger.warning("Unknown potion effect in shop config: " + raw);
                continue;
            }
            int level = parseInt(parts[1], 1);
            int duration = parts.length >= 3 ? parseInt(parts[2], 30) : 30;
            int amplifier = Math.max(0, level - 1);
            effects.add(new PotionEffect(type, duration * 20, amplifier));
        }
        return effects;
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private Map<ShopCategoryType, ShopCategory> loadCategories(ConfigurationSection categoriesSection,
                                                               Map<String, Map<Integer, String>> positionedEntries) {
        Map<ShopCategoryType, ShopCategory> categories = new EnumMap<>(ShopCategoryType.class);
        if (categoriesSection == null) {
            logger.warning("Missing shop categories section.");
            return categories;
        }
        for (String key : categoriesSection.getKeys(false)) {
            ShopCategoryType type = ShopCategoryType.fromKey(key);
            if (type == null) {
                logger.warning("Unknown shop category: " + key);
                continue;
            }
            ConfigurationSection section = categoriesSection.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            String title = section.getString("title", type.defaultTitle());
            String iconName = section.getString("icon");
            Material icon = iconName != null ? Material.matchMaterial(iconName) : null;
            if (icon == null) {
                logger.warning("Unknown icon material for shop category " + key + ": " + iconName);
                icon = Material.CHEST;
            }
            int size = normalizeSize(section.getInt("size", 54));
            Map<Integer, String> entries = parseEntries(section.getConfigurationSection("entries"));
            applyPositionOverrides(entries, key, size, positionedEntries);
            categories.put(type, new ShopCategory(type, title, icon, size, entries));
        }
        return categories;
    }

    private void recordPosition(Map<String, Map<Integer, String>> positionedEntries,
                                String itemId,
                                ConfigurationSection section,
                                String categoryKey) {
        Integer slot = parsePositionSlot(section, itemId);
        if (slot == null) {
            return;
        }
        if (categoryKey == null || categoryKey.isBlank()) {
            logger.warning("Shop item " + itemId + " has a position but no category.");
            return;
        }
        String normalized = categoryKey.trim().toLowerCase(Locale.ROOT);
        Map<Integer, String> categoryMap = positionedEntries.computeIfAbsent(normalized, key -> new HashMap<>());
        String existing = categoryMap.put(slot, itemId);
        if (existing != null && !existing.equals(itemId)) {
            logger.warning("Duplicate shop position in " + normalized + " slot " + slot
                    + ": " + existing + " replaced by " + itemId);
        }
    }

    private Integer parsePositionSlot(ConfigurationSection section, String itemId) {
        ConfigurationSection pos = section.getConfigurationSection("position");
        if (pos == null) {
            return null;
        }
        int line = pos.getInt("line", 0);
        int column = pos.getInt("column", pos.getInt("collum", 0));
        if (line <= 0 || column <= 0) {
            logger.warning("Invalid shop position for " + itemId + ": line/column must be >= 1.");
            return null;
        }
        if (column > 9) {
            logger.warning("Invalid shop position for " + itemId + ": column must be 1-9.");
            return null;
        }
        return (line * 9) + (column - 1);
    }

    private void applyPositionOverrides(Map<Integer, String> entries,
                                        String categoryKey,
                                        int size,
                                        Map<String, Map<Integer, String>> positionedEntries) {
        if (entries == null || positionedEntries == null || categoryKey == null) {
            return;
        }
        Map<Integer, String> overrides = positionedEntries.get(categoryKey.trim().toLowerCase(Locale.ROOT));
        if (overrides == null || overrides.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, String> entry : overrides.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= size) {
                logger.warning("Shop position out of range in " + categoryKey + " for item "
                        + entry.getValue() + ": slot " + slot + " (size " + size + ")");
                continue;
            }
            String existing = entries.put(slot, entry.getValue());
            if (existing != null && !existing.equals(entry.getValue())) {
                logger.warning("Shop position conflict in " + categoryKey + " slot " + slot
                        + ": " + existing + " replaced by " + entry.getValue());
            }
        }
    }

    private Map<Integer, String> parseEntries(ConfigurationSection entriesSection) {
        Map<Integer, String> entries = new HashMap<>();
        if (entriesSection == null) {
            return entries;
        }
        for (String key : entriesSection.getKeys(false)) {
            int slot = entriesSection.getInt(key, -1);
            if (slot < 0) {
                continue;
            }
            entries.put(slot, key);
        }
        return entries;
    }

    private int normalizeSize(int size) {
        int normalized = Math.max(9, size);
        if (normalized % 9 != 0) {
            normalized = ((normalized / 9) + 1) * 9;
        }
        return Math.min(54, normalized);
    }

    private Map<ShopItemBehavior, Map<Integer, ShopItemDefinition>> buildTieredItems(
            Map<String, ShopItemDefinition> items) {
        Map<ShopItemBehavior, Map<Integer, ShopItemDefinition>> tiered = new EnumMap<>(ShopItemBehavior.class);
        for (ShopItemDefinition item : items.values()) {
            if (item.getTier() <= 0) {
                continue;
            }
            ShopItemBehavior behavior = item.getBehavior();
            if (behavior != ShopItemBehavior.ARMOR
                    && behavior != ShopItemBehavior.PICKAXE
                    && behavior != ShopItemBehavior.AXE
                    && behavior != ShopItemBehavior.BOW
                    && behavior != ShopItemBehavior.CROSSBOW) {
                continue;
            }
            tiered.computeIfAbsent(behavior, key -> new HashMap<>())
                    .put(item.getTier(), item);
        }
        return tiered;
    }
}
