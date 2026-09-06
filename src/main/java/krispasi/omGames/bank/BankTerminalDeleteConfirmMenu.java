package krispasi.omGames.bank;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public final class BankTerminalDeleteConfirmMenu implements BankInventoryMenu {
    private static final int SIZE = 27;
    private static final int CONFIRM_SLOT = 11;
    private static final int SUMMARY_SLOT = 13;
    private static final int CANCEL_SLOT = 15;

    private final BankManager manager;
    private final String terminalId;
    private final String accountId;
    private final Inventory inventory;

    public BankTerminalDeleteConfirmMenu(BankManager manager, String terminalId, String accountId) {
        this.manager = manager;
        this.terminalId = terminalId;
        this.accountId = accountId;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Delete Terminal", NamedTextColor.RED));
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
        int slot = event.getRawSlot();
        if (slot == CANCEL_SLOT) {
            manager.openTerminalOwnerMenu(player, terminalId);
            return;
        }
        if (slot == CONFIRM_SLOT) {
            BankManager.Result result = manager.deleteTerminal(terminalId);
            player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (result.success()) {
                manager.openTerminalsMenu(player, accountId);
            } else {
                manager.openTerminalOwnerMenu(player, terminalId);
            }
        }
    }

    private void refresh() {
        inventory.clear();
        BankTerminal terminal = manager.getTerminal(terminalId);
        inventory.setItem(CONFIRM_SLOT, BankMenuItems.item(
                Material.LIME_CONCRETE,
                Component.text("Confirm Delete", NamedTextColor.GREEN),
                List.of(Component.text("This removes terminal data, items, and carts.", NamedTextColor.GRAY))
        ));
        inventory.setItem(SUMMARY_SLOT, BankMenuItems.item(
                Material.COMPARATOR,
                Component.text(terminal == null ? "Missing Terminal" : terminal.name(), terminal == null ? NamedTextColor.RED : NamedTextColor.GOLD),
                List.of(Component.text("Terminal id: " + shortId(terminalId), NamedTextColor.DARK_GRAY))
        ));
        inventory.setItem(CANCEL_SLOT, BankMenuItems.item(
                Material.RED_CONCRETE,
                Component.text("Cancel", NamedTextColor.RED),
                List.of(Component.text("Return without deleting.", NamedTextColor.GRAY))
        ));
    }

    private String shortId(String id) {
        return id == null || id.length() <= 10 ? String.valueOf(id) : id.substring(0, 10);
    }
}
