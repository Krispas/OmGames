package krispasi.omGames.bank;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public final class BankTerminalsMenu implements BankInventoryMenu {
    private static final int SIZE = 54;
    private static final int BACK_SLOT = 45;
    private static final int CREATE_TERMINAL_SLOT = 10;
    private static final int TERMINAL_START_SLOT = 18;

    private final BankManager manager;
    private final UUID accountId;
    private final Inventory inventory;
    private final Map<Integer, String> terminalSlots = new HashMap<>();

    public BankTerminalsMenu(BankManager manager, UUID accountId) {
        this.manager = manager;
        this.accountId = accountId;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Bank Terminals", NamedTextColor.GOLD));
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
            manager.openAccountMenu(player, accountId);
            return;
        }
        if (slot == CREATE_TERMINAL_SLOT) {
            send(player, manager.createTerminal(accountId));
            refresh();
            return;
        }
        String terminalId = terminalSlots.get(slot);
        if (terminalId != null) {
            manager.openTerminalOwnerMenu(player, terminalId);
        }
    }

    private void refresh() {
        inventory.clear();
        terminalSlots.clear();
        inventory.setItem(CREATE_TERMINAL_SLOT, BankMenuItems.item(
                Material.COPPER_BLOCK,
                Component.text("+ Terminal", NamedTextColor.GREEN),
                List.of(Component.text("Creates terminal ownership data.", NamedTextColor.GRAY))
        ));
        int slot = TERMINAL_START_SLOT;
        for (BankTerminal terminal : manager.listTerminals(accountId)) {
            if (slot >= SIZE) {
                break;
            }
            inventory.setItem(slot, BankMenuItems.item(
                    Material.COMPARATOR,
                    Component.text(terminal.name(), NamedTextColor.AQUA),
                    List.of(
                            Component.text("Owner: " + terminal.ownerName(), NamedTextColor.GRAY),
                            Component.text("Items: " + manager.listTerminalItems(terminal.terminalId()).size(), NamedTextColor.GRAY),
                            Component.text("Click to open owner menu.", NamedTextColor.DARK_GRAY)
                    )
            ));
            terminalSlots.put(slot, terminal.terminalId());
            slot++;
        }
        inventory.setItem(BACK_SLOT, BankMenuItems.item(
                Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW),
                List.of(Component.text("Return to account.", NamedTextColor.GRAY))
        ));
    }

    private void send(Player player, BankManager.Result result) {
        player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
    }
}
