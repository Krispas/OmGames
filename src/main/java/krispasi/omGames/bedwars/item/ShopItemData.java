package krispasi.omGames.bedwars.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Persistent metadata helper for purchased shop item ids.
 */
public final class ShopItemData {
    private static final NamespacedKey KEY =
            new NamespacedKey(JavaPlugin.getProvidingPlugin(ShopItemData.class), "shop_item");

    private ShopItemData() {
    }

    public static void apply(ItemMeta meta, String id) {
        if (meta == null) {
            return;
        }
        if (id == null || id.isBlank()) {
            meta.getPersistentDataContainer().remove(KEY);
            return;
        }
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, id);
    }

    public static String getId(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        return getId(item.getItemMeta());
    }

    public static String getId(ItemMeta meta) {
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
    }
}
