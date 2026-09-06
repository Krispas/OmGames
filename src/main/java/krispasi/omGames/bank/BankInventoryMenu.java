package krispasi.omGames.bank;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

interface BankInventoryMenu extends InventoryHolder {
    void handleClick(InventoryClickEvent event);
}
