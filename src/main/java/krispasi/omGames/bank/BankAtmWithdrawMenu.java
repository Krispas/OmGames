package krispasi.omGames.bank;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public final class BankAtmWithdrawMenu implements BankInventoryMenu {
    private static final int SIZE = 27;
    private static final int BACK_SLOT = 18;
    private static final int OPTION_START_SLOT = 10;

    private final BankManager manager;
    private final String cardId;
    private final Inventory inventory;
    private final Map<Integer, BankManager.CreditWithdrawalOption> creditSlots = new HashMap<>();

    public BankAtmWithdrawMenu(BankManager manager, String cardId) {
        this.manager = manager;
        this.cardId = cardId;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("ATM Withdraw", NamedTextColor.GOLD));
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
            manager.openAtm(player, cardId);
            return;
        }
        BankManager.CreditWithdrawalOption option = creditSlots.get(slot);
        if (option == null) {
            return;
        }
        int amount = event.isShiftClick() ? option.maxSingleWithdrawAmount() : 1;
        BankManager.Result result = manager.withdrawCreditType(player, cardId, option.creditId(), amount);
        player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
        if (result.success()) {
            refresh();
        } else {
            manager.openAtm(player, cardId);
        }
    }

    private void refresh() {
        inventory.clear();
        creditSlots.clear();

        BankCard card = cardId == null ? null : manager.getCard(cardId);
        BankAccount account = card == null ? null : manager.getAccount(card.accountId());
        long balance = account == null ? 0L : account.balance();
        List<BankManager.CreditWithdrawalOption> options = manager.getWithdrawalOptions(cardId);

        int slot = OPTION_START_SLOT;
        for (BankManager.CreditWithdrawalOption option : options) {
            if (slot >= 16) {
                break;
            }
            inventory.setItem(slot, BankMenuItems.item(
                    Material.GOLD_INGOT,
                    Component.text(option.creditId(), NamedTextColor.GOLD),
                    List.of(
                            Component.text("Value each: " + option.value(), NamedTextColor.GRAY),
                            Component.text("Available now: " + option.affordableAmount(), NamedTextColor.GRAY),
                            Component.text("Shift withdraw: " + option.maxSingleWithdrawAmount(), NamedTextColor.GREEN),
                            Component.text("Click to withdraw this credit type.", NamedTextColor.DARK_GRAY)
                    )
            ));
            creditSlots.put(slot, option);
            slot++;
        }

        if (options.isEmpty()) {
            inventory.setItem(13, BankMenuItems.item(
                    Material.BARRIER,
                    Component.text(balance <= 0L ? "No Balance" : "No Credits Available", NamedTextColor.RED),
                    List.of(Component.text(balance <= 0L
                            ? "This account has no credits to withdraw."
                            : "OmVeins credit items are not available.", NamedTextColor.GRAY))
            ));
        }
        inventory.setItem(16, BankMenuItems.item(
                Material.GOLD_BLOCK,
                Component.text("Balance", NamedTextColor.GOLD),
                List.of(
                        Component.text("Account: " + (account == null ? "missing" : account.displayName()), account == null ? NamedTextColor.RED : NamedTextColor.GRAY),
                        Component.text("Credits: " + balance, NamedTextColor.GRAY)
                )
        ));
        inventory.setItem(BACK_SLOT, BankMenuItems.item(
                Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW),
                List.of(Component.text("Return to ATM.", NamedTextColor.GRAY))
        ));
    }
}
