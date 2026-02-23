package krispasi.omGames.bedwars.shop;

import java.util.Locale;

/**
 * Scope of a purchase limit for a shop item.
 */
public enum ShopItemLimitScope {
    PLAYER,
    TEAM;

    public static ShopItemLimitScope fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return ShopItemLimitScope.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
