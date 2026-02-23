package krispasi.omGames.bedwars.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Persistent data helper for marking the team selection item.
 * <p>Used to detect the right-click action that opens the team pick GUI.</p>
 */
public final class TeamSelectItemData {
    private static final NamespacedKey KEY =
            new NamespacedKey(JavaPlugin.getProvidingPlugin(TeamSelectItemData.class), "team_select");

    private TeamSelectItemData() {
    }

    public static void apply(ItemMeta meta) {
        if (meta == null) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(KEY, PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean isTeamSelectItem(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte value = meta.getPersistentDataContainer().get(KEY, PersistentDataType.BYTE);
        return value != null && value == 1;
    }
}
