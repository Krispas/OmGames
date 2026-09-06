package krispasi.omGames.bank;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public final class BankTerminalOwnerMenu implements BankInventoryMenu {
    private static final int SIZE = 27;
    private static final int BACK_SLOT = 18;
    private static final int EDIT_SLOT = 11;
    private static final int BUYER_VIEW_SLOT = 13;
    private static final int SUMMARY_SLOT = 15;
    private static final int DELETE_SLOT = 22;
    private static final int DECONSTRUCT_SLOT = 20;
    private static final int GIVE_ITEM_SLOT = 24;

    private final BankManager manager;
    private final String terminalId;
    private final String placementId;
    private final Inventory inventory;

    public BankTerminalOwnerMenu(BankManager manager, String terminalId) {
        this(manager, terminalId, null);
    }

    public BankTerminalOwnerMenu(BankManager manager, String terminalId, String placementId) {
        this.manager = manager;
        this.terminalId = terminalId;
        this.placementId = placementId;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Terminal Owner", NamedTextColor.GOLD));
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
        BankTerminal terminal = manager.getTerminal(terminalId);
        int slot = event.getRawSlot();
        if (slot == BACK_SLOT && terminal != null) {
            manager.openTerminalsMenu(player, terminal.accountId());
            return;
        }
        if (slot == BUYER_VIEW_SLOT) {
            manager.openTerminalBuyerMenu(player, terminalId);
            return;
        }
        if (slot == EDIT_SLOT) {
            player.sendMessage(Component.text("Terminal editing is prepared; item/pricing setup will be added later.", NamedTextColor.YELLOW));
            return;
        }
        if (slot == DELETE_SLOT && terminal != null) {
            new BankTerminalDeleteConfirmMenu(manager, terminal.terminalId(), terminal.accountId()).open(player);
            return;
        }
        if (slot == DECONSTRUCT_SLOT && terminal != null) {
            BankManager.Result result = manager.getPlacementService().deconstruct(player, terminal.terminalId(), placementId);
            player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (result.success()) {
                refresh();
            } else {
                refresh();
            }
            return;
        }
        if (slot == GIVE_ITEM_SLOT && terminal != null && placementId == null) {
            BankManager.Result result = manager.giveTerminalItem(player, terminal.terminalId());
            player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
    }

    private void refresh() {
        inventory.clear();
        BankTerminal terminal = manager.getTerminal(terminalId);
        if (terminal == null) {
            inventory.setItem(SUMMARY_SLOT, BankMenuItems.item(Material.BARRIER, Component.text("Terminal not found", NamedTextColor.RED), List.of()));
            return;
        }
        inventory.setItem(EDIT_SLOT, BankMenuItems.item(
                Material.ANVIL,
                Component.text("Edit Terminal", NamedTextColor.YELLOW),
                List.of(
                        Component.text("Prepared for future item and price setup.", NamedTextColor.GRAY),
                        Component.text("No item logic is connected yet.", NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(BUYER_VIEW_SLOT, BankMenuItems.item(
                Material.CHEST,
                Component.text("Buyer View", NamedTextColor.AQUA),
                List.of(Component.text("Open the customer cart/check menu.", NamedTextColor.GRAY))
        ));
        inventory.setItem(SUMMARY_SLOT, BankMenuItems.item(
                Material.COMPARATOR,
                Component.text(terminal.name(), NamedTextColor.GOLD),
                List.of(
                        Component.text("Owner: " + terminal.ownerName(), NamedTextColor.GRAY),
                        Component.text("Items: " + manager.listTerminalItems(terminalId).size(), NamedTextColor.GRAY)
                )
        ));
        inventory.setItem(DELETE_SLOT, BankMenuItems.item(
                Material.REDSTONE_BLOCK,
                Component.text("Delete Terminal", NamedTextColor.RED),
                List.of(
                        Component.text("Removes this terminal from Bank storage.", NamedTextColor.GRAY),
                        Component.text("A confirmation menu opens first.", NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(DECONSTRUCT_SLOT, BankMenuItems.item(
                Material.IRON_PICKAXE,
                Component.text("Deconstruct", NamedTextColor.YELLOW),
                List.of(
                        Component.text(placementId == null ? "Removes all placed entities for this terminal." : "Removes this placed terminal entity pair.", NamedTextColor.GRAY),
                        Component.text("Terminal data stays saved and item is returned.", NamedTextColor.DARK_GRAY)
                )
        ));
        if (placementId == null) {
            inventory.setItem(GIVE_ITEM_SLOT, BankMenuItems.item(
                    Material.COMPARATOR,
                    Component.text("Give Item", NamedTextColor.GREEN),
                    List.of(Component.text("Gives you this terminal's cash register item.", NamedTextColor.GRAY))
            ));
        }
        inventory.setItem(BACK_SLOT, BankMenuItems.item(
                Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW),
                List.of(Component.text("Return to terminals.", NamedTextColor.GRAY))
        ));
    }
}
