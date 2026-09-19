package krispasi.omGames.hallsofcarnage;

import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

final class HallsInventorySupport {
    private HallsInventorySupport() {
    }

    static boolean hasItemIngredients(JavaPlugin plugin, PlayerInventory inventory, Map<String, Integer> cost) {
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            if (countHotbarItem(plugin, inventory, entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    static int countHotbarItem(JavaPlugin plugin, PlayerInventory inventory, String itemId) {
        int count = 0;
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (hasHallsItemId(plugin, item, itemId)) {
                count += Math.max(1, item.getAmount());
            }
        }
        return count;
    }

    static void consumeItemIngredients(JavaPlugin plugin, PlayerInventory inventory, Map<String, Integer> cost) {
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            int remaining = entry.getValue();
            for (int slot = 0; slot <= 8 && remaining > 0; slot++) {
                ItemStack item = inventory.getItem(slot);
                if (!hasHallsItemId(plugin, item, entry.getKey())) {
                    continue;
                }
                int take = Math.min(remaining, Math.max(1, item.getAmount()));
                remaining -= take;
                int newAmount = item.getAmount() - take;
                if (newAmount <= 0) {
                    inventory.setItem(slot, null);
                } else {
                    ItemStack remainingItem = item.clone();
                    remainingItem.setAmount(newAmount);
                    inventory.setItem(slot, remainingItem);
                }
            }
        }
    }

    static int firstAvailableHotbarSlot(PlayerInventory inventory) {
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                return slot;
            }
        }
        return -1;
    }

    static int availableHotbarSlots(PlayerInventory inventory) {
        int slots = 0;
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                slots++;
            }
        }
        return slots;
    }

    private static boolean hasHallsItemId(JavaPlugin plugin, ItemStack item, String itemId) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        String actual = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "hoc_item_id"), PersistentDataType.STRING);
        return itemId.equals(actual);
    }
}
