package krispasi.omGames.bedwars.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Persistent data helper for tagging {@link org.bukkit.inventory.ItemStack} with custom item ids.
 * <p>Used by shop items and listeners to recognize custom behaviors.</p>
 */
public final class CustomItemData {
    private static final NamespacedKey KEY =
            new NamespacedKey(JavaPlugin.getProvidingPlugin(CustomItemData.class), "custom_item");

    private CustomItemData() {
    }

    public static void apply(ItemMeta meta, String id) {
        if (meta == null || id == null || id.isBlank()) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(KEY, PersistentDataType.STRING, id);
    }

    public static String getId(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
    }
}
