package krispasi.omGames.bedwars.shop;

/**
 * Purchase limit definition for a shop item.
 */
public record ShopItemLimit(ShopItemLimitScope scope, int amount) {
    public boolean isValid() {
        return scope != null && amount > 0;
    }
}
