package krispasi.omGames.bedwars.gui;

import java.util.Arrays;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class TimeCapsuleViewMenu implements InventoryHolder {
    private static final int INVENTORY_SIZE = 27;
    private static final Component TITLE = Component.text("Time Capsule View", NamedTextColor.GOLD);

    private final Inventory inventory;

    public TimeCapsuleViewMenu(ItemStack[] contents) {
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, TITLE);
        if (contents != null) {
            inventory.setContents(Arrays.copyOf(contents, INVENTORY_SIZE));
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
    }
}
