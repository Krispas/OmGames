package krispasi.omGames.bedwars.shop;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;

/**
 * Represents a shop category layout.
 * <p>Defines title, icon, size, and slot-to-item mapping loaded from config.</p>
 * @see krispasi.omGames.bedwars.shop.ShopCategoryType
 */
public class ShopCategory {
    private final ShopCategoryType type;
    private final String title;
    private final Material icon;
    private final int size;
    private final Map<Integer, String> entries;

    public ShopCategory(ShopCategoryType type, String title, Material icon, int size,
                        Map<Integer, String> entries) {
        this.type = type;
        this.title = title;
        this.icon = icon;
        this.size = size;
        this.entries = Collections.unmodifiableMap(new HashMap<>(entries));
    }

    public ShopCategoryType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public Material getIcon() {
        return icon;
    }

    public int getSize() {
        return size;
    }

    public Map<Integer, String> getEntries() {
        return entries;
    }
}
