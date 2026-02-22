package krispasi.omGames.bedwars.shop;

import org.bukkit.Material;

/**
 * Cost tuple for a shop item.
 * <p>Encapsulates currency material and amount with a validity check.</p>
 */
public record ShopCost(Material material, int amount) {
    public boolean isValid() {
        return material != null && amount > 0;
    }
}
