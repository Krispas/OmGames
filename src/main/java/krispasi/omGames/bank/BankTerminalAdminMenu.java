package krispasi.omGames.bank;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public final class BankTerminalAdminMenu implements BankInventoryMenu {
    private static final int SIZE = 27;
    private static final int BACK_SLOT = 18;
    private static final int OWNER_VIEW_SLOT = 11;
    private static final int SUMMARY_SLOT = 13;
    private static final int GIVE_ITEM_SLOT = 15;
    private static final int DELETE_SLOT = 22;

    private final BankManager manager;
    private final String terminalId;
    private final Inventory inventory;

    public BankTerminalAdminMenu(BankManager manager, String terminalId) {
        this.manager = manager;
        this.terminalId = terminalId;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Terminal Admin", NamedTextColor.GOLD));
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
        BankTerminal terminal = manager.getTerminal(terminalId);
        int slot = event.getRawSlot();
        if (terminal == null) {
            return;
        }
        if (slot == BACK_SLOT) {
            manager.openTerminalsMenu(player, terminal.accountId());
            return;
        }
        if (slot == OWNER_VIEW_SLOT) {
            manager.openTerminalOwnerMenu(player, terminalId);
            return;
        }
        if (slot == GIVE_ITEM_SLOT) {
            BankManager.Result result = manager.giveTerminalItem(player, terminal.terminalId());
            player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            return;
        }
        if (slot == DELETE_SLOT) {
            new BankTerminalDeleteConfirmMenu(manager, terminal.terminalId(), terminal.accountId()).open(player);
        }
    }

    private void refresh() {
        inventory.clear();
        BankTerminal terminal = manager.getTerminal(terminalId);
        if (terminal == null) {
            inventory.setItem(SUMMARY_SLOT, BankMenuItems.item(Material.BARRIER, Component.text("Terminal not found", NamedTextColor.RED), List.of()));
            return;
        }
        inventory.setItem(OWNER_VIEW_SLOT, BankMenuItems.item(
                Material.ANVIL,
                Component.text("Owner Menu", NamedTextColor.YELLOW),
                List.of(Component.text("Open the normal owner controls.", NamedTextColor.GRAY))
        ));
        inventory.setItem(SUMMARY_SLOT, BankMenuItems.item(
                Material.COMPARATOR,
                Component.text(terminal.name(), NamedTextColor.GOLD),
                List.of(
                        Component.text("Owner: " + terminal.ownerName(), NamedTextColor.GRAY),
                        Component.text("Items: " + manager.listTerminalItems(terminalId).size(), NamedTextColor.GRAY)
                )
        ));
        inventory.setItem(GIVE_ITEM_SLOT, BankMenuItems.item(
                Material.COMPARATOR,
                Component.text("Give Item", NamedTextColor.GREEN),
                List.of(Component.text("Gives you this terminal's cash register item.", NamedTextColor.GRAY))
        ));
        inventory.setItem(DELETE_SLOT, BankMenuItems.item(
                Material.REDSTONE_BLOCK,
                Component.text("Delete Terminal", NamedTextColor.RED),
                List.of(
                        Component.text("Admin-only data deletion.", NamedTextColor.GRAY),
                        Component.text("A confirmation menu opens first.", NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(BACK_SLOT, BankMenuItems.item(
                Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW),
                List.of(Component.text("Return to terminals.", NamedTextColor.GRAY))
        ));
    }
}
