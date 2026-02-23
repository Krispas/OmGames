package krispasi.omGames.bedwars.shop;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory shop configuration.
 * <p>Holds items, categories, and tiered lookups for tool and armor upgrades.</p>
 * @see krispasi.omGames.bedwars.shop.ShopConfigLoader
 */
public class ShopConfig {
    private final Map<String, ShopItemDefinition> items;
    private final Map<ShopCategoryType, ShopCategory> categories;
    private final Map<ShopItemBehavior, Map<Integer, ShopItemDefinition>> tieredItems;

    public ShopConfig(Map<String, ShopItemDefinition> items,
                      Map<ShopCategoryType, ShopCategory> categories,
                      Map<ShopItemBehavior, Map<Integer, ShopItemDefinition>> tieredItems) {
        this.items = Collections.unmodifiableMap(new HashMap<>(items));
        EnumMap<ShopCategoryType, ShopCategory> categoryCopy = new EnumMap<>(ShopCategoryType.class);
        categoryCopy.putAll(categories);
        this.categories = Collections.unmodifiableMap(categoryCopy);
        EnumMap<ShopItemBehavior, Map<Integer, ShopItemDefinition>> tierCopy = new EnumMap<>(ShopItemBehavior.class);
        tierCopy.putAll(tieredItems);
        this.tieredItems = Collections.unmodifiableMap(tierCopy);
    }

    public static ShopConfig empty() {
        return new ShopConfig(Map.of(), Map.of(), Map.of());
    }

    public ShopItemDefinition getItem(String id) {
        if (id == null) {
            return null;
        }
        return items.get(id);
    }

    public ShopCategory getCategory(ShopCategoryType type) {
        return categories.get(type);
    }

    public Map<ShopCategoryType, ShopCategory> getCategories() {
        return categories;
    }

    public Map<String, ShopItemDefinition> getItems() {
        return items;
    }

    public ShopItemDefinition getFirstByBehavior(ShopItemBehavior behavior) {
        for (ShopItemDefinition item : items.values()) {
            if (item.getBehavior() == behavior) {
                return item;
            }
        }
        return null;
    }

    public Optional<ShopItemDefinition> getTieredItem(ShopItemBehavior behavior, int tier) {
        Map<Integer, ShopItemDefinition> map = tieredItems.get(behavior);
        if (map == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(map.get(tier));
    }

    public Map<Integer, ShopItemDefinition> getTieredItems(ShopItemBehavior behavior) {
        Map<Integer, ShopItemDefinition> map = tieredItems.get(behavior);
        if (map == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(map);
    }

    public static ShopConfig merge(ShopConfig base, ShopConfig extra) {
        if (base == null) {
            return extra != null ? extra : ShopConfig.empty();
        }
        if (extra == null) {
            return base;
        }
        Map<String, ShopItemDefinition> mergedItems = new HashMap<>(base.items);
        for (Map.Entry<String, ShopItemDefinition> entry : extra.items.entrySet()) {
            mergedItems.putIfAbsent(entry.getKey(), entry.getValue());
        }
        Map<ShopCategoryType, ShopCategory> mergedCategories = new EnumMap<>(ShopCategoryType.class);
        mergedCategories.putAll(base.categories);
        for (Map.Entry<ShopCategoryType, ShopCategory> entry : extra.categories.entrySet()) {
            ShopCategoryType type = entry.getKey();
            ShopCategory extraCategory = entry.getValue();
            ShopCategory existing = mergedCategories.get(type);
            if (existing == null) {
                mergedCategories.put(type, extraCategory);
                continue;
            }
            Map<Integer, String> mergedEntries = new HashMap<>(existing.getEntries());
            for (Map.Entry<Integer, String> slot : extraCategory.getEntries().entrySet()) {
                mergedEntries.putIfAbsent(slot.getKey(), slot.getValue());
            }
            mergedCategories.put(type, new ShopCategory(type,
                    existing.getTitle(),
                    existing.getIcon(),
                    existing.getSize(),
                    mergedEntries));
        }
        Map<ShopItemBehavior, Map<Integer, ShopItemDefinition>> tiered = buildTieredItems(mergedItems);
        return new ShopConfig(mergedItems, mergedCategories, tiered);
    }

    public static Map<ShopItemBehavior, Map<Integer, ShopItemDefinition>> buildTieredItems(
            Map<String, ShopItemDefinition> items) {
        Map<ShopItemBehavior, Map<Integer, ShopItemDefinition>> tiered = new EnumMap<>(ShopItemBehavior.class);
        for (ShopItemDefinition item : items.values()) {
            if (item.getTier() <= 0) {
                continue;
            }
            ShopItemBehavior behavior = item.getBehavior();
            if (behavior != ShopItemBehavior.ARMOR
                    && behavior != ShopItemBehavior.PICKAXE
                    && behavior != ShopItemBehavior.AXE
                    && behavior != ShopItemBehavior.BOW
                    && behavior != ShopItemBehavior.CROSSBOW) {
                continue;
            }
            tiered.computeIfAbsent(behavior, key -> new HashMap<>())
                    .put(item.getTier(), item);
        }
        return tiered;
    }
}
