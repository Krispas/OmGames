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

public final class BankAtmMenu implements BankInventoryMenu {
    private static final int SIZE = 27;
    private static final int DEPOSIT_SLOT = 11;
    private static final int BALANCE_SLOT = 15;

    private final BankManager manager;
    private final UUID playerId;
    private final Inventory inventory;

    public BankAtmMenu(BankManager manager, UUID playerId) {
        this.manager = manager;
        this.playerId = playerId;
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
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() == DEPOSIT_SLOT) {
            manager.openAtmDepositMenu(player);
        }
    }

    private void refresh() {
        inventory.clear();
        BankAccount account = manager.getPlayerAccount(playerId);
        long balance = account == null ? 0L : account.balance();
        inventory.setItem(DEPOSIT_SLOT, BankMenuItems.item(
                Material.HOPPER,
                Component.text("Deposit Credits", NamedTextColor.GREEN),
                List.of(
                        Component.text("Deposits OmVeins credit items from inventory.", NamedTextColor.GRAY),
                        Component.text("Choose which credit type to deposit.", NamedTextColor.DARK_GRAY)
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
    }
}
