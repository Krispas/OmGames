package krispasi.omGames.bank;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public final class BankAccountMenu implements BankInventoryMenu {
    private static final int SIZE = 27;
    private static final int BACK_SLOT = 18;
    private static final int CREATE_CARD_SLOT = 10;
    private static final int CARDS_SLOT = 11;
    private static final int RENAME_SLOT = 12;
    private static final int CREATE_TERMINAL_SLOT = 13;
    private static final int TERMINALS_SLOT = 14;
    private static final int EDITORS_SLOT = 15;
    private static final int SUMMARY_SLOT = 16;
    private static final int DELETE_SLOT = 26;

    private final BankManager manager;
    private final String accountId;
    private final Inventory inventory;

    public BankAccountMenu(BankManager manager, String accountId) {
        this.manager = manager;
        this.accountId = accountId;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Bank Account", NamedTextColor.GOLD));
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
        if (slot == BACK_SLOT) {
            manager.openAdminMenu(player);
            return;
        }
        if (slot == CREATE_CARD_SLOT) {
            send(player, manager.createCard(player, accountId));
            refresh();
            return;
        }
        if (slot == CARDS_SLOT) {
            manager.openCardsMenu(player, accountId);
            return;
        }
        if (slot == CREATE_TERMINAL_SLOT) {
            send(player, manager.createTerminal(player, accountId));
            refresh();
            return;
        }
        if (slot == TERMINALS_SLOT) {
            manager.openTerminalsMenu(player, accountId);
            return;
        }
        if (slot == EDITORS_SLOT) {
            BankAccount account = manager.getAccount(accountId);
            if (account != null && !account.playerAccount()) {
                manager.openAccountEditorsMenu(player, accountId);
            }
            return;
        }
        if (slot == RENAME_SLOT) {
            BankAccount account = manager.getAccount(accountId);
            if (account != null && !account.playerAccount()) {
                manager.beginRenameNonPlayerAccountPrompt(player, accountId);
            }
            return;
        }
        if (slot == DELETE_SLOT) {
            BankAccount account = manager.getAccount(accountId);
            if (account != null && !account.playerAccount()) {
                new BankAccountDeleteConfirmMenu(manager, accountId).open(player);
            }
        }
    }

    private void refresh() {
        inventory.clear();
        BankAccount account = manager.getAccount(accountId);
        if (account == null) {
            inventory.setItem(SUMMARY_SLOT, BankMenuItems.item(
                    Material.BARRIER,
                    Component.text("Account not found", NamedTextColor.RED),
                    List.of()
            ));
            return;
        }
        inventory.setItem(CREATE_CARD_SLOT, BankMenuItems.item(
                Material.PAPER,
                Component.text("+ Credit Card", NamedTextColor.GREEN),
                List.of(
                        Component.text("Creates a new usable card.", NamedTextColor.GRAY),
                        Component.text("The card item is given to you.", NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(CARDS_SLOT, BankMenuItems.item(
                Material.BOOK,
                Component.text("Manage Cards", NamedTextColor.AQUA),
                List.of(Component.text("Cards: " + manager.listCards(accountId).size(), NamedTextColor.GRAY))
        ));
        inventory.setItem(CREATE_TERMINAL_SLOT, BankMenuItems.item(
                Material.COPPER_BLOCK,
                Component.text("+ Terminal", NamedTextColor.GREEN),
                List.of(Component.text("Creates terminal ownership data.", NamedTextColor.GRAY))
        ));
        inventory.setItem(TERMINALS_SLOT, BankMenuItems.item(
                Material.COMPARATOR,
                Component.text("Manage Terminals", NamedTextColor.AQUA),
                List.of(Component.text("Terminals: " + manager.listTerminals(accountId).size(), NamedTextColor.GRAY))
        ));
        if (!account.playerAccount()) {
            inventory.setItem(RENAME_SLOT, BankMenuItems.item(
                    Material.NAME_TAG,
                    Component.text("Rename Account", NamedTextColor.AQUA),
                    List.of(
                            Component.text(account.displayName(), NamedTextColor.GRAY),
                            Component.text("Click to enter a new name.", NamedTextColor.DARK_GRAY)
                    )
            ));
            inventory.setItem(EDITORS_SLOT, BankMenuItems.item(
                    Material.NAME_TAG,
                    Component.text("Manage Editors", NamedTextColor.AQUA),
                    List.of(Component.text("Editors: " + manager.listEditors(accountId).size(), NamedTextColor.GRAY))
            ));
            inventory.setItem(DELETE_SLOT, BankMenuItems.item(
                    Material.REDSTONE_BLOCK,
                    Component.text("Delete Account", NamedTextColor.RED),
                    List.of(
                            Component.text("Requires confirmation.", NamedTextColor.GRAY),
                            Component.text("Removes cards, terminals, items, carts, and editors.", NamedTextColor.DARK_GRAY)
                    )
            ));
        }
        inventory.setItem(SUMMARY_SLOT, BankMenuItems.item(
                Material.GOLD_INGOT,
                Component.text(account.displayName(), NamedTextColor.GOLD),
                List.of(
                        Component.text("Type: " + (account.playerAccount() ? "Player" : "Non-player"), NamedTextColor.GRAY),
                        Component.text("Balance: " + account.balance(), NamedTextColor.GRAY)
                )
        ));
        inventory.setItem(BACK_SLOT, BankMenuItems.item(
                Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW),
                List.of(Component.text("Return to Bank Admin.", NamedTextColor.GRAY))
        ));
    }

    private void send(Player player, BankManager.Result result) {
        player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
    }
}
