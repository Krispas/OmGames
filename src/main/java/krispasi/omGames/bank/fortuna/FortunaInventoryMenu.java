package krispasi.omGames.bank.fortuna;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

interface FortunaInventoryMenu extends InventoryHolder {
    void handleClick(InventoryClickEvent event);
}
