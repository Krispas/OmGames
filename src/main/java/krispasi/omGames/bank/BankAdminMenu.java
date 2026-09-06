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

public final class BankAdminMenu implements BankInventoryMenu {
    private static final int SIZE = 54;
    private static final int CREATE_PLAYER_ACCOUNT_SLOT = 10;
    private static final int CREATE_NON_PLAYER_ACCOUNT_SLOT = 11;
    private static final int SUMMARY_SLOT = 16;
    private static final int ACCOUNT_START_SLOT = 18;

    private final BankManager manager;
    private final Inventory inventory;
    private final Map<Integer, String> accountSlots = new HashMap<>();

    public BankAdminMenu(BankManager manager) {
        this.manager = manager;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Bank Admin", NamedTextColor.GOLD));
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
        if (slot == CREATE_PLAYER_ACCOUNT_SLOT) {
            manager.openOnlinePlayerMenu(player);
            return;
        }
        if (slot == CREATE_NON_PLAYER_ACCOUNT_SLOT) {
            manager.openNonPlayerAccountEditorMenu(player);
            return;
        }
        String accountId = accountSlots.get(slot);
        if (accountId != null) {
            manager.openAccountMenu(player, accountId);
        }
    }

    private void refresh() {
        inventory.clear();
        accountSlots.clear();
        List<BankAccount> accounts = manager.listAccounts();
        inventory.setItem(CREATE_PLAYER_ACCOUNT_SLOT, BankMenuItems.item(
                Material.EMERALD_BLOCK,
                Component.text("+ Player Account", NamedTextColor.GREEN),
                List.of(
                        Component.text("Choose an online player.", NamedTextColor.GRAY),
                        Component.text("Offline players cannot receive new accounts.", NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(CREATE_NON_PLAYER_ACCOUNT_SLOT, BankMenuItems.item(
                Material.CHEST,
                Component.text("+ Non-Player Account", NamedTextColor.GREEN),
                List.of(
                        Component.text("Create a named account.", NamedTextColor.GRAY),
                        Component.text("Editors are set from the account menu.", NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(SUMMARY_SLOT, BankMenuItems.item(
                Material.GOLD_INGOT,
                Component.text("Bank Accounts", NamedTextColor.GOLD),
                List.of(Component.text("Accounts: " + accounts.size(), NamedTextColor.GRAY))
        ));

        int slot = ACCOUNT_START_SLOT;
        for (BankAccount account : accounts) {
            if (slot >= SIZE) {
                break;
            }
            inventory.setItem(slot, BankMenuItems.item(
                    account.playerAccount() ? Material.PLAYER_HEAD : Material.CHEST,
                    Component.text(account.displayName(), NamedTextColor.AQUA),
                    List.of(
                            Component.text("Type: " + (account.playerAccount() ? "Player" : "Non-player"), NamedTextColor.GRAY),
                            Component.text("Balance: " + account.balance(), NamedTextColor.GRAY),
                            Component.text("Cards: " + manager.listCards(account.accountId()).size(), NamedTextColor.GRAY),
                            Component.text("Terminals: " + manager.listTerminals(account.accountId()).size(), NamedTextColor.GRAY),
                            Component.text("Click to manage.", NamedTextColor.DARK_GRAY)
                    )
            ));
            accountSlots.put(slot, account.accountId());
            slot++;
        }
    }
}
