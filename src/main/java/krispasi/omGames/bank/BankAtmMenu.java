package krispasi.omGames.bank;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public final class BankAtmMenu implements BankInventoryMenu {
    private static final int SIZE = 27;
    private static final int CARD_SLOT = 11;
    private static final int DEPOSIT_SLOT = 13;
    private static final int WITHDRAW_SLOT = 15;
    private static final int BALANCE_SLOT = 17;

    private final BankManager manager;
    private final Inventory inventory;
    private String cardId;

    public BankAtmMenu(BankManager manager, String cardId) {
        this.manager = manager;
        this.cardId = cardId;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("ATM", NamedTextColor.GOLD));
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
    public boolean handlesPlayerInventoryClick() {
        return true;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() >= inventory.getSize()) {
            loadCard(player, event.getCurrentItem());
            return;
        }
        int slot = event.getRawSlot();
        if (slot == CARD_SLOT) {
            if (!loadCard(player, event.getCursor())) {
                cardId = null;
                player.sendMessage(Component.text("Credit card removed from ATM.", NamedTextColor.YELLOW));
                refresh();
            }
            return;
        }
        if (slot == DEPOSIT_SLOT) {
            BankManager.Result result = manager.validateAtmCard(cardId);
            if (!result.success()) {
                player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
                return;
            }
            manager.openAtmDepositMenu(player, cardId);
            return;
        }
        if (slot == WITHDRAW_SLOT) {
            BankManager.Result result = manager.validateAtmCard(cardId);
            if (!result.success()) {
                player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
                return;
            }
            manager.openAtmWithdrawMenu(player, cardId);
        }
    }

    private void refresh() {
        inventory.clear();
        BankCard card = cardId == null ? null : manager.getCard(cardId);
        BankAccount account = card == null ? null : manager.getAccount(card.accountId());
        long balance = account == null ? 0L : account.balance();
        inventory.setItem(CARD_SLOT, BankMenuItems.item(
                card == null ? Material.PAPER : Material.LIME_CONCRETE,
                Component.text(card == null ? "Insert Credit Card" : "Card: " + card.ownerName(), card == null ? NamedTextColor.YELLOW : NamedTextColor.GREEN),
                card == null
                        ? List.of(
                                Component.text("Click this slot with a credit card.", NamedTextColor.GRAY),
                                Component.text("You can also click a card in your inventory.", NamedTextColor.DARK_GRAY)
                        )
                        : List.of(
                                Component.text("Owner: " + card.ownerName(), NamedTextColor.GRAY),
                                Component.text("Click with empty cursor to remove.", NamedTextColor.DARK_GRAY)
                        )
        ));
        inventory.setItem(DEPOSIT_SLOT, BankMenuItems.item(
                card == null ? Material.BARRIER : Material.HOPPER,
                Component.text("Deposit Credits", card == null ? NamedTextColor.RED : NamedTextColor.GREEN),
                card == null
                        ? List.of(Component.text("Insert a credit card first.", NamedTextColor.GRAY))
                        : List.of(
                                Component.text("Deposits credits to this card account.", NamedTextColor.GRAY),
                                Component.text("Insert credit items into the ATM deposit slots.", NamedTextColor.DARK_GRAY)
                        )
        ));
        inventory.setItem(BALANCE_SLOT, BankMenuItems.item(
                Material.GOLD_INGOT,
                Component.text("Balance", NamedTextColor.GOLD),
                List.of(
                        Component.text("Account: " + (account == null ? "missing" : account.displayName()), account == null ? NamedTextColor.RED : NamedTextColor.GRAY),
                        Component.text("Credits: " + balance, NamedTextColor.GRAY)
                )
        ));
        inventory.setItem(WITHDRAW_SLOT, BankMenuItems.item(
                card == null ? Material.BARRIER : Material.DISPENSER,
                Component.text("Withdraw Credits", card == null ? NamedTextColor.RED : NamedTextColor.GREEN),
                card == null
                        ? List.of(Component.text("Insert a credit card first.", NamedTextColor.GRAY))
                        : List.of(
                                Component.text("Withdraws credit from this card account.", NamedTextColor.GRAY),
                                Component.text("Choose which credit type to receive.", NamedTextColor.DARK_GRAY)
                        )
        ));
    }

    private boolean loadCard(Player player, org.bukkit.inventory.ItemStack item) {
        String newCardId = manager.readCardId(item);
        if (newCardId == null) {
            return false;
        }
        BankManager.Result result = manager.validateAtmCard(newCardId);
        if (!result.success()) {
            player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
            return true;
        }
        cardId = newCardId;
        player.sendMessage(Component.text(result.message(), NamedTextColor.GREEN));
        refresh();
        return true;
    }
}
