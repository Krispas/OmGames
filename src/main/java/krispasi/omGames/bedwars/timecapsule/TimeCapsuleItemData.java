package krispasi.omGames.bedwars.timecapsule;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class TimeCapsuleItemData {
    private static final NamespacedKey CONTENTS_KEY =
            new NamespacedKey(JavaPlugin.getProvidingPlugin(TimeCapsuleItemData.class), "time_capsule_contents");
    private static final NamespacedKey SOURCE_PLAYER_NAME_KEY =
            new NamespacedKey(JavaPlugin.getProvidingPlugin(TimeCapsuleItemData.class), "time_capsule_source_player_name");

    private TimeCapsuleItemData() {
    }

    public static void applyContents(ItemMeta meta, String encodedContents) {
        if (meta == null) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (encodedContents == null || encodedContents.isBlank()) {
            container.remove(CONTENTS_KEY);
            return;
        }
        container.set(CONTENTS_KEY, PersistentDataType.STRING, encodedContents);
    }

    public static void applySourcePlayerName(ItemMeta meta, String sourcePlayerName) {
        if (meta == null) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (sourcePlayerName == null || sourcePlayerName.isBlank()) {
            container.remove(SOURCE_PLAYER_NAME_KEY);
            return;
        }
        container.set(SOURCE_PLAYER_NAME_KEY, PersistentDataType.STRING, sourcePlayerName);
    }

    public static String getContents(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(CONTENTS_KEY, PersistentDataType.STRING);
    }

    public static String getSourcePlayerName(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(SOURCE_PLAYER_NAME_KEY, PersistentDataType.STRING);
    }
}
