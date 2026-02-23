package krispasi.omGames.bedwars.item;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Loads {@code custom-items.yml} into a
 * {@link krispasi.omGames.bedwars.item.CustomItemConfig}.</p>
 * <p>Validates {@link krispasi.omGames.bedwars.item.CustomItemType} and applies defaults
 * for omitted fields.</p>
 */
public class CustomItemConfigLoader {
    private final File file;
    private final Logger logger;

    public CustomItemConfigLoader(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public CustomItemConfig load() {
        if (!file.exists()) {
            logger.warning("Custom item config not found: " + file.getAbsolutePath());
            return CustomItemConfig.empty();
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("custom-items");
        if (root == null) {
            logger.warning("Missing custom-items section in custom-items.yml.");
            return CustomItemConfig.empty();
        }

        Map<String, CustomItemDefinition> items = new HashMap<>();
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            CustomItemDefinition definition = parseDefinition(key, section);
            if (definition != null) {
                items.put(definition.getId(), definition);
            }
        }
        return new CustomItemConfig(items);
    }

    private CustomItemDefinition parseDefinition(String id, ConfigurationSection section) {
        CustomItemType type = parseType(section.getString("type"));
        if (type == null) {
            logger.warning("Unknown custom item type for " + id + ".");
            return null;
        }
        String materialName = section.getString("material");
        Material material = materialName != null ? Material.matchMaterial(materialName) : null;
        if (material == null) {
            logger.warning("Unknown material for custom item " + id + ": " + materialName);
            return null;
        }
        double velocity = section.getDouble("velocity", type.getDefaultVelocity());
        float yield = (float) section.getDouble("yield", type.getDefaultYield());
        boolean incendiary = section.getBoolean("incendiary", type.isDefaultIncendiary());
        double damage = section.getDouble("damage", type.getDefaultDamage());
        double knockback = section.getDouble("knockback", type.getDefaultKnockback());
        int lifetimeSeconds = section.getInt("lifetime-seconds", type.getDefaultLifetimeSeconds());
        double health = section.getDouble("health", type.getDefaultHealth());
        double speed = section.getDouble("speed", type.getDefaultSpeed());
        double range = section.getDouble("range", type.getDefaultRange());
        int uses = Math.max(0, section.getInt("uses", type.getDefaultUses()));
        int maxBlocks = section.getInt("max-blocks", type.getDefaultMaxBlocks());
        int bridgeWidth = section.getInt("bridge-width", type.getDefaultBridgeWidth());
        if (maxBlocks < 0) {
            maxBlocks = type.getDefaultMaxBlocks();
        }
        if (bridgeWidth < 1) {
            bridgeWidth = type.getDefaultBridgeWidth();
        }
        if (bridgeWidth % 2 == 0) {
            bridgeWidth += 1;
        }
        String normalizedId = id.toLowerCase(Locale.ROOT);
        return new CustomItemDefinition(normalizedId, type, material, velocity, yield, incendiary,
                maxBlocks, bridgeWidth, damage, knockback, lifetimeSeconds, health, speed, range, uses);
    }

    private CustomItemType parseType(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return CustomItemType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
