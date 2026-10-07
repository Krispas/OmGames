package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.nio.file.Path;
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
        try (var paths = java.nio.file.Files.walk(folder.toPath())) {
            for (Path path : paths.filter(java.nio.file.Files::isRegularFile)
                    .filter(HallsModifierTypeLoader::isModifierFile)
                    .sorted(Comparator.comparing(Path::toString)).toList()) {
                Path relative = folder.toPath().relativize(path);
                if (relative.getNameCount() != 2) {
                    plugin.getLogger().warning("Skipping Halls modifier outside a scenario folder: " + path.getFileName());
                    continue;
                }
                loadFile(plugin, path.toFile(), relative.getName(0).toString(), modifiers);
            }
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("Failed to list Halls modifiers in " + folder + ": " + ex.getMessage());
        }
        return Map.copyOf(modifiers);
    }

    private static boolean isModifierFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".txt");
    }

    private static void loadFile(JavaPlugin plugin, File file, String scenarioId,
                                 Map<String, HallsModifierType> modifiers) {
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
            if (id.equals("more_sculk")) {
                continue;
            }
            HallsModifierType.Kind kind = modifierKind(row.getString("type", "bad"));
            Map<String, Object> effects = new LinkedHashMap<>();
            ConfigurationSection effectsSection = row.getConfigurationSection("effects");
            if (effectsSection != null) {
                for (String effectKey : effectsSection.getKeys(false)) {
                    effects.put(normalizeId(effectKey), effectsSection.get(effectKey));
                }
            }
            effects.put("scenario", normalizeId(scenarioId));
            if (!levelType.equals("shared")) {
                effects.put("level_type", levelType);
            }
            if (id.isBlank()) {
                plugin.getLogger().warning("Skipping invalid Halls modifier in " + file.getName() + ".");
                continue;
            }
            modifiers.put(normalizeId(scenarioId) + "/" + id, new HallsModifierType(
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
