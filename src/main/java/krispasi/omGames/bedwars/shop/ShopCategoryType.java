package krispasi.omGames.bedwars.shop;

import java.util.List;
import java.util.Locale;

public enum ShopCategoryType {
    QUICK_BUY("quick_buy", "Quick Buy"),
    BLOCKS("blocks", "Blocks"),
    MELEE("melee", "Melee"),
    RANGED("ranged", "Ranged"),
    ARMOR("armor", "Armor"),
    TOOLS("tools", "Tools"),
    UTILITY("utility", "Utility"),
    MISCELLANEOUS("miscellaneous", "Miscellaneous");

    private static final List<ShopCategoryType> ORDERED = List.of(
            QUICK_BUY, BLOCKS, MELEE, RANGED, ARMOR, TOOLS, UTILITY, MISCELLANEOUS
    );

    private final String key;
    private final String defaultTitle;

    ShopCategoryType(String key, String defaultTitle) {
        this.key = key;
        this.defaultTitle = defaultTitle;
    }

    public String key() {
        return key;
    }

    public String defaultTitle() {
        return defaultTitle;
    }

    public static ShopCategoryType fromKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (ShopCategoryType type : values()) {
            if (type.key.equals(normalized)) {
                return type;
            }
        }
        return null;
    }

    public static List<ShopCategoryType> ordered() {
        return ORDERED;
    }
}
