package krispasi.omGames.bedwars.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Persistent data helper for lobby control items (pause/skip/manage).
 */
public final class LobbyControlItemData {
    private static final NamespacedKey KEY =
            new NamespacedKey(JavaPlugin.getProvidingPlugin(LobbyControlItemData.class), "lobby_control");

    private LobbyControlItemData() {
    }

    public static void apply(ItemMeta meta, String action) {
        if (meta == null || action == null || action.isBlank()) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(KEY, PersistentDataType.STRING, action.trim().toLowerCase());
    }

    public static String getAction(ItemStack stack) {
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
