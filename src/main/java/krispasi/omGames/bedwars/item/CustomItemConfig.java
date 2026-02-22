package krispasi.omGames.bedwars.item;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;

/**
 * Read-only registry of {@link krispasi.omGames.bedwars.item.CustomItemDefinition}.
 * <p>Supports lookups by id and by unique {@link org.bukkit.Material}.</p>
 * @see krispasi.omGames.bedwars.item.CustomItemConfigLoader
 */
public class CustomItemConfig {
    private final Map<String, CustomItemDefinition> items;

    public CustomItemConfig(Map<String, CustomItemDefinition> items) {
        this.items = Collections.unmodifiableMap(items);
    }

    public static CustomItemConfig empty() {
        return new CustomItemConfig(Map.of());
    }

    public CustomItemDefinition getItem(String id) {
        if (id == null) {
            return null;
        }
        return items.get(id.toLowerCase(Locale.ROOT));
    }

    public CustomItemDefinition findByMaterial(Material material) {
        if (material == null) {
            return null;
        }
        CustomItemDefinition match = null;
        for (CustomItemDefinition definition : items.values()) {
            if (definition.getMaterial() != material) {
                continue;
            }
            if (match != null) {
                return null;
            }
            match = definition;
        }
        return match;
    }
}
