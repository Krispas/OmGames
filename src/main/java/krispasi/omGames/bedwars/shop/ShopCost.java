package krispasi.omGames.bedwars.shop;

import org.bukkit.Material;

public record ShopCost(Material material, int amount) {
    public boolean isValid() {
        return material != null && amount > 0;
    }
}
