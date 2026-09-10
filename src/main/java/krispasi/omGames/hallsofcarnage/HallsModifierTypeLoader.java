package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class HallsModifierTypeLoader {
    private HallsModifierTypeLoader() {
    }

    public static Map<String, HallsModifierType> loadModifierTypes(JavaPlugin plugin, File folder) {
        Map<String, HallsModifierType> modifiers = new HashMap<>();
        if (folder == null || !folder.exists()) {
            return Map.copyOf(modifiers);
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".txt"));
        if (files == null) {
            return Map.copyOf(modifiers);
        }
        for (File file : java.util.Arrays.stream(files).sorted(Comparator.comparing(File::getName)).toList()) {
            loadFile(plugin, file, modifiers);
        }
        return Map.copyOf(modifiers);
    }

    private static void loadFile(JavaPlugin plugin, File file, Map<String, HallsModifierType> modifiers) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("modifiers");
        if (root == null) {
            return;
        }
        String levelType = normalizeId(file.getName().replaceFirst("\\.[^.]+$", ""));
        for (String key : root.getKeys(false)) {
            ConfigurationSection row = root.getConfigurationSection(key);
            if (row == null) {
                continue;
            }
            String id = normalizeId(key);
            HallsModifierType.Kind kind = modifierKind(row.getString("type", "bad"));
            Map<String, Object> effects = new LinkedHashMap<>();
            ConfigurationSection effectsSection = row.getConfigurationSection("effects");
            if (effectsSection != null) {
                for (String effectKey : effectsSection.getKeys(false)) {
                    effects.put(normalizeId(effectKey), effectsSection.get(effectKey));
                }
            }
            if (!levelType.equals("shared")) {
                effects.put("level_type", levelType);
            }
            if (id.isBlank()) {
                plugin.getLogger().warning("Skipping invalid Halls modifier in " + file.getName() + ".");
                continue;
            }
            modifiers.put(id, new HallsModifierType(
                    id,
                    row.getString("display-name", id),
                    row.getString("icon", "?"),
                    kind,
                    row.getInt("weight", 1),
                    effects
            ));
        }
    }

    private static HallsModifierType.Kind modifierKind(String value) {
        return "good".equalsIgnoreCase(value) ? HallsModifierType.Kind.GOOD : HallsModifierType.Kind.BAD;
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
