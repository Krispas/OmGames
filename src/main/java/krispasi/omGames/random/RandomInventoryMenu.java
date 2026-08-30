package krispasi.omGames.random;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

interface RandomInventoryMenu extends InventoryHolder {
    void handleClick(InventoryClickEvent event);
}
