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

public final class BankTerminalEditorMenu implements BankInventoryMenu {
    private static final int SIZE = 54;
    private static final int NEW_ITEM_SLOT = 10;
    private static final int END_EDIT_SLOT = 16;
    private static final int ITEM_START_SLOT = 18;

    private final BankManager manager;
    private final String terminalId;
    private final Inventory inventory;
    private final Map<Integer, String> itemSlots = new HashMap<>();

    public BankTerminalEditorMenu(BankManager manager, String terminalId) {
        this.manager = manager;
        this.terminalId = terminalId;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Terminal Editor", NamedTextColor.GOLD));
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
        if (slot == NEW_ITEM_SLOT) {
            BankManager.Result result = manager.createTerminalItem(player, terminalId);
            player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            List<BankTerminalItem> items = manager.listTerminalItems(terminalId);
            if (result.success() && !items.isEmpty()) {
                manager.openTerminalItemEditMenu(player, items.get(items.size() - 1).itemId());
                return;
            }
            refresh();
            return;
        }
        if (slot == END_EDIT_SLOT) {
            BankManager.Result result = manager.stopTerminalEditing(player, terminalId);
            player.sendMessage(Component.text(result.message(), NamedTextColor.YELLOW));
            player.closeInventory();
            return;
        }
        String itemId = itemSlots.get(slot);
        if (itemId != null) {
            manager.openTerminalItemEditMenu(player, itemId);
        }
    }

    private void refresh() {
        inventory.clear();
        itemSlots.clear();
        BankTerminal terminal = manager.getTerminal(terminalId);
        inventory.setItem(NEW_ITEM_SLOT, BankMenuItems.item(
                Material.EMERALD,
                Component.text("+ Item", NamedTextColor.GREEN),
                List.of(Component.text("Create a new sellable terminal item.", NamedTextColor.GRAY))
        ));
        inventory.setItem(END_EDIT_SLOT, BankMenuItems.item(
                Material.BARRIER,
                Component.text("End Edit", NamedTextColor.RED),
                List.of(Component.text("Removes your terminal editor item.", NamedTextColor.GRAY))
        ));
        inventory.setItem(13, BankMenuItems.item(
                Material.COMPARATOR,
                Component.text(terminal == null ? "Unknown Terminal" : terminal.name(), NamedTextColor.GOLD),
                List.of(Component.text("Items: " + manager.listTerminalItems(terminalId).size(), NamedTextColor.GRAY))
        ));

        int slot = ITEM_START_SLOT;
        for (BankTerminalItem item : manager.listTerminalItems(terminalId)) {
            if (slot >= SIZE) {
                break;
            }
            inventory.setItem(slot, BankMenuItems.item(
                    item.iconMaterial(),
                    Component.text(item.displayName(), NamedTextColor.AQUA),
                    List.of(
                            Component.text("Price: " + item.price(), NamedTextColor.GRAY),
                            Component.text("Click block: " + item.clickLocationText(), NamedTextColor.GRAY),
                            Component.text("Click to edit.", NamedTextColor.DARK_GRAY)
                    )
            ));
            itemSlots.put(slot, item.itemId());
            slot++;
        }
    }
}
