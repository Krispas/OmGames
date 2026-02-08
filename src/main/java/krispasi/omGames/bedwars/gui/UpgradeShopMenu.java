package krispasi.omGames.bedwars.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class UpgradeShopMenu implements InventoryHolder {
    private final Inventory inventory;

    public UpgradeShopMenu() {
        this.inventory = Bukkit.createInventory(this, 27,
                Component.text("Upgrades Shop", NamedTextColor.GOLD));
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
