package krispasi.omGames.hallsofcarnage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

final class HallsItemFactory {
    private HallsItemFactory() {
    }

    static ItemStack create(JavaPlugin plugin, HallsItemType type, int amount) {
        ItemStack item = new ItemStack(type.material());
        item.setAmount(Math.max(1, Math.min(amount, type.maxStackSize())));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(type.name(), itemColor(type)));
            meta.setMaxStackSize(type.maxStackSize());
            List<Component> lore = new ArrayList<>();
            for (String line : type.lore()) {
                lore.add(Component.text(line, NamedTextColor.GRAY));
            }
            if (!type.stats().isEmpty()) {
                if (!lore.isEmpty()) {
                    lore.add(Component.empty());
                }
                type.stats().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> Component.text(statName(entry.getKey()) + ": " + statValue(entry.getValue()), NamedTextColor.DARK_AQUA))
                        .forEach(lore::add);
            }
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            if (type.itemModel() != null && !type.itemModel().isBlank()) {
                NamespacedKey modelKey = NamespacedKey.fromString(type.itemModel());
                if (modelKey != null) {
                    meta.setItemModel(modelKey);
                }
            }
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "hoc_item_id"), PersistentDataType.STRING, type.id());
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "hoc_item_category"), PersistentDataType.STRING, type.category());
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "hoc_item_rarity"), PersistentDataType.STRING, type.rarity());
            for (Map.Entry<String, Double> entry : type.stats().entrySet()) {
                meta.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, "hoc_stat_" + entry.getKey()),
                        PersistentDataType.DOUBLE,
                        entry.getValue()
                );
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static NamedTextColor itemColor(HallsItemType type) {
        if (type.category().equals("blueprint")) {
            return type.rarity().equals("rare") ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.AQUA;
        }
        return type.rarity().equals("rare") ? NamedTextColor.GOLD : NamedTextColor.WHITE;
    }

    private static String statName(String id) {
        String[] words = id.replace('_', ' ').split(" ");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return builder.toString();
    }

    private static String statValue(double value) {
        if (Math.rint(value) == value) {
            return Integer.toString((int) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
