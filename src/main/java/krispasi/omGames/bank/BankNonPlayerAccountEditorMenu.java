package krispasi.omGames.bank;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public final class BankNonPlayerAccountEditorMenu implements BankInventoryMenu {
    private static final int SIZE = 27;
    private static final int CREATE_SLOT = 11;
    private static final int BACK_SLOT = 18;

    private final BankManager manager;
    private final Inventory inventory;

    public BankNonPlayerAccountEditorMenu(BankManager manager) {
        this.manager = manager;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Non-Player Account", NamedTextColor.GOLD));
        refresh();
    }

    public void open(Player player) {
        refresh();
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() == BACK_SLOT) {
            manager.openAdminMenu(player);
            return;
        }
        if (event.getRawSlot() == CREATE_SLOT) {
            manager.beginCreateNonPlayerAccountPrompt(player);
        }
    }

    private void refresh() {
        inventory.clear();
        inventory.setItem(CREATE_SLOT, BankMenuItems.item(
                Material.CHEST,
                Component.text("Name Account", NamedTextColor.GREEN),
                List.of(
                        Component.text("Enter the account name in chat.", NamedTextColor.GRAY),
                        Component.text("Editors are selected after creation.", NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(BACK_SLOT, BankMenuItems.item(
                Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW),
                List.of(Component.text("Return to Bank Admin.", NamedTextColor.GRAY))
        ));
    }
}
