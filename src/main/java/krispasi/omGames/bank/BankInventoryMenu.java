package krispasi.omGames.bank;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

interface BankInventoryMenu extends InventoryHolder {
    void handleClick(InventoryClickEvent event);

    default void handleDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    default void handleClose(InventoryCloseEvent event) {
    }

    default boolean handlesPlayerInventoryClick() {
        return false;
    }
}
