package krispasi.omGames.bedwars.model;

import java.util.Locale;

public enum ShopType {
    ITEM("main", "Item Shop"),
    UPGRADES("upgrades", "Upgrades Shop");

    private final String configKey;
    private final String displayName;

    ShopType(String configKey, String displayName) {
        this.configKey = configKey;
        this.displayName = displayName;
    }

    public String configKey() {
        return configKey;
    }

    public String displayName() {
        return displayName;
    }

    public static ShopType fromKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (ShopType type : values()) {
            if (type.configKey.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
