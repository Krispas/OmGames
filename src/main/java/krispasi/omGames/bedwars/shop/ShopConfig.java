package krispasi.omGames.bedwars.shop;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
}
