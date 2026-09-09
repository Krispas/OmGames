package krispasi.omGames.bank;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public final class BankAccountDeleteConfirmMenu implements BankInventoryMenu {
    private static final int SIZE = 27;
    private static final int CONFIRM_SLOT = 11;
    private static final int SUMMARY_SLOT = 13;
    private static final int CANCEL_SLOT = 15;

    private final BankManager manager;
    private final String accountId;
    private final Inventory inventory;

    public BankAccountDeleteConfirmMenu(BankManager manager, String accountId) {
        this.manager = manager;
        this.accountId = accountId;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Delete Account", NamedTextColor.RED));
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
            manager.openAccountMenu(player, accountId);
            return;
        }
        if (slot == CONFIRM_SLOT) {
            BankManager.Result result = manager.deleteNonPlayerAccount(accountId);
            player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (result.success()) {
                manager.openAdminMenu(player);
            } else {
                manager.openAccountMenu(player, accountId);
            }
        }
    }

    private void refresh() {
        inventory.clear();
        BankAccount account = manager.getAccount(accountId);
        boolean canDelete = account != null && !account.playerAccount();
        inventory.setItem(CONFIRM_SLOT, BankMenuItems.item(
                canDelete ? Material.LIME_CONCRETE : Material.BARRIER,
                Component.text("Confirm Delete", canDelete ? NamedTextColor.GREEN : NamedTextColor.RED),
                List.of(Component.text(canDelete
                        ? "This permanently removes the account, balance, and linked Bank data."
                        : "Only non-player accounts can be deleted here.", NamedTextColor.GRAY))
        ));
        inventory.setItem(SUMMARY_SLOT, BankMenuItems.item(
                Material.CHEST,
                Component.text(account == null ? "Missing Account" : account.displayName(), account == null ? NamedTextColor.RED : NamedTextColor.GOLD),
                account == null
                        ? List.of(Component.text("Account id: " + shortId(accountId), NamedTextColor.DARK_GRAY))
                        : List.of(
                                Component.text("Balance: " + account.balance(), NamedTextColor.GRAY),
                                Component.text("Cards: " + manager.listCards(accountId).size(), NamedTextColor.GRAY),
                                Component.text("Terminals: " + manager.listTerminals(accountId).size(), NamedTextColor.GRAY),
                                Component.text("Editors: " + manager.listEditors(accountId).size(), NamedTextColor.GRAY),
                                Component.text("Account id: " + shortId(accountId), NamedTextColor.DARK_GRAY)
                        )
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
