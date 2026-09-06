package krispasi.omGames.bank;

import java.util.List;
import java.util.UUID;
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
    private static final int CREATE_TERMINAL_SLOT = 13;
    private static final int TERMINALS_SLOT = 14;
    private static final int SUMMARY_SLOT = 16;

    private final BankManager manager;
    private final UUID accountId;
    private final Inventory inventory;

    public BankAccountMenu(BankManager manager, UUID accountId) {
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
            send(player, manager.createTerminal(accountId));
            refresh();
            return;
        }
        if (slot == TERMINALS_SLOT) {
            manager.openTerminalsMenu(player, accountId);
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
        inventory.setItem(SUMMARY_SLOT, BankMenuItems.item(
                Material.GOLD_INGOT,
                Component.text(account.playerName(), NamedTextColor.GOLD),
                List.of(Component.text("Balance: " + account.balance(), NamedTextColor.GRAY))
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
