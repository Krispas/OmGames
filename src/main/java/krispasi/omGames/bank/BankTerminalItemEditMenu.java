package krispasi.omGames.bank;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public final class BankTerminalItemEditMenu implements BankInventoryMenu {
    private static final int SIZE = 27;
    private static final int NAME_SLOT = 10;
    private static final int PRICE_SLOT = 11;
    private static final int ICON_SLOT = 12;
    private static final int CLICK_BLOCK_SLOT = 14;
    private static final int DELETE_SLOT = 15;
    private static final int BACK_SLOT = 18;

    private final BankManager manager;
    private final String itemId;
    private final Inventory inventory;

    public BankTerminalItemEditMenu(BankManager manager, String itemId) {
        this.manager = manager;
        this.itemId = itemId;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Terminal Item", NamedTextColor.GOLD));
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
        BankTerminalItem item = manager.getTerminalItem(itemId);
        if (item == null) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot == BACK_SLOT) {
            manager.openTerminalEditorMenu(player, item.terminalId());
            return;
        }
        if (slot == NAME_SLOT) {
            manager.beginTerminalItemNamePrompt(player, itemId);
            return;
        }
        if (slot == PRICE_SLOT) {
            manager.beginTerminalItemPricePrompt(player, itemId);
            return;
        }
        if (slot == ICON_SLOT) {
            manager.openTerminalIconMenu(player, itemId, 0);
            return;
        }
        if (slot == CLICK_BLOCK_SLOT) {
            BankManager.Result result = manager.beginTerminalItemClickLocation(player, itemId);
            player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            return;
        }
        if (slot == DELETE_SLOT) {
            BankManager.Result result = manager.deleteTerminalItem(player, itemId);
            player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (result.success()) {
                manager.openTerminalEditorMenu(player, item.terminalId());
            }
        }
    }

    private void refresh() {
        inventory.clear();
        BankTerminalItem item = manager.getTerminalItem(itemId);
        if (item == null) {
            inventory.setItem(13, BankMenuItems.item(Material.BARRIER, Component.text("Item not found", NamedTextColor.RED), List.of()));
            return;
        }
        inventory.setItem(NAME_SLOT, BankMenuItems.item(
                Material.NAME_TAG,
                Component.text("Name", NamedTextColor.AQUA),
                List.of(
                        Component.text(item.displayName(), NamedTextColor.GRAY),
                        Component.text("Click to rename.", NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(PRICE_SLOT, BankMenuItems.item(
                Material.GOLD_INGOT,
                Component.text("Price", NamedTextColor.GOLD),
                List.of(
                        Component.text(item.price() + " credits", NamedTextColor.GRAY),
                        Component.text("Click to change price.", NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(ICON_SLOT, BankMenuItems.item(
                item.iconMaterial(),
                Component.text("Icon", NamedTextColor.YELLOW),
                List.of(Component.text("Click to choose any Minecraft item icon.", NamedTextColor.GRAY))
        ));
        inventory.setItem(CLICK_BLOCK_SLOT, BankMenuItems.item(
                Material.TARGET,
                Component.text("Set Click Block", NamedTextColor.GREEN),
                List.of(
                        Component.text(item.clickLocationText(), NamedTextColor.GRAY),
                        Component.text("Then right-click a block with the editor.", NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(DELETE_SLOT, BankMenuItems.item(
                Material.REDSTONE_BLOCK,
                Component.text("Delete", NamedTextColor.RED),
                List.of(Component.text("Removes this item from carts and terminal setup.", NamedTextColor.GRAY))
        ));
        inventory.setItem(BACK_SLOT, BankMenuItems.item(
                Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW),
                List.of(Component.text("Return to terminal item list.", NamedTextColor.GRAY))
        ));
    }
}
