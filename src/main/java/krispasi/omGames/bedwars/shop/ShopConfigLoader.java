package krispasi.omGames.bedwars.shop;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
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

        Map<String, ShopItemDefinition> items = loadItems(shopSection.getConfigurationSection("items"));
        Map<ShopCategoryType, ShopCategory> categories = loadCategories(shopSection.getConfigurationSection("categories"));
        Map<ShopItemBehavior, Map<Integer, ShopItemDefinition>> tiered = buildTieredItems(items);

        return new ShopConfig(items, categories, tiered);
    }

    private Map<String, ShopItemDefinition> loadItems(ConfigurationSection itemsSection) {
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
            ShopItemDefinition item = parseItem(key, section);
            if (item != null) {
                items.put(key, item);
            }
        }
        return items;
    }

    private ShopItemDefinition parseItem(String id, ConfigurationSection section) {
        String materialName = section.getString("material");
        Material material = materialName != null ? Material.matchMaterial(materialName) : null;
        if (material == null) {
            logger.warning("Unknown material for shop item " + id + ": " + materialName);
            return null;
        }
        int amount = Math.max(1, section.getInt("amount", 1));
        ShopCost cost = parseCost(section.getConfigurationSection("cost"));
        ShopItemBehavior behavior = parseBehavior(section.getString("behavior"));
        boolean teamColor = section.getBoolean("team-color", false);
        int tier = section.getInt("tier", 0);
        Map<Enchantment, Integer> enchants = parseEnchants(section.getConfigurationSection("enchants"));
        List<PotionEffect> potionEffects = parsePotionEffects(section.getStringList("potion-effects"));
        String displayName = section.getString("display-name");
        List<String> lore = section.getStringList("lore");

        return new ShopItemDefinition(id, material, amount, cost, behavior, teamColor, tier,
                enchants, potionEffects, displayName, lore);
    }

    private ShopCost parseCost(ConfigurationSection section) {
        if (section == null) {
            return new ShopCost(null, 0);
        }
        String materialName = section.getString("material");
        Material material = materialName != null ? Material.matchMaterial(materialName) : null;
        int amount = section.getInt("amount", 0);
        return new ShopCost(material, amount);
    }

    private ShopItemBehavior parseBehavior(String value) {
        if (value == null) {
            return ShopItemBehavior.UTILITY;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return ShopItemBehavior.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            logger.warning("Unknown shop item behavior: " + value);
            return ShopItemBehavior.UTILITY;
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

    private Map<ShopCategoryType, ShopCategory> loadCategories(ConfigurationSection categoriesSection) {
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
            categories.put(type, new ShopCategory(type, title, icon, size, entries));
        }
        return categories;
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
                    && behavior != ShopItemBehavior.BOW) {
                continue;
            }
            tiered.computeIfAbsent(behavior, key -> new HashMap<>())
                    .put(item.getTier(), item);
        }
        return tiered;
    }
}
