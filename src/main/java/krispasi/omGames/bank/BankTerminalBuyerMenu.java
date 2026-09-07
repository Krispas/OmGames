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

public final class BankTerminalBuyerMenu implements BankInventoryMenu {
    private static final int SIZE = 54;
    private static final int CART_SLOT = 11;
    private static final int PAY_SLOT = 13;
    private static final int SUMMARY_SLOT = 15;
    private static final int ITEM_START_SLOT = 18;

    private final BankManager manager;
    private final String terminalId;
    private final Inventory inventory;
    private final Map<Integer, String> itemSlots = new HashMap<>();

    public BankTerminalBuyerMenu(BankManager manager, String terminalId) {
        this.manager = manager;
        this.terminalId = terminalId;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Terminal", NamedTextColor.GOLD));
        refresh(null);
    }

    public void open(Player player) {
        refresh(player);
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
        if (event.getRawSlot() == CART_SLOT) {
            sendCart(player);
            return;
        }
        if (event.getRawSlot() == PAY_SLOT) {
            BankManager.Result result = manager.payCartWithCardInput(player, terminalId, event.getCursor());
            player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            refresh(player);
            return;
        }
        String itemId = itemSlots.get(event.getRawSlot());
        if (itemId != null) {
            BankManager.Result result = manager.addTerminalItemToCart(player, itemId, event.isShiftClick() ? 10 : 1);
            player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            refresh(player);
        }
    }

    private void refresh(Player player) {
        inventory.clear();
        itemSlots.clear();
        BankTerminal terminal = manager.getTerminal(terminalId);
        List<BankCartLine> cart = player == null ? List.of() : manager.listCart(player, terminalId);
        List<BankTerminalItem> items = manager.listTerminalItems(terminalId);
        inventory.setItem(CART_SLOT, BankMenuItems.item(
                Material.CHEST,
                Component.text("Cart", NamedTextColor.AQUA),
                List.of(
                        Component.text("Lines: " + cart.size(), NamedTextColor.GRAY),
                        Component.text("Total: " + manager.cartTotal(cart), NamedTextColor.GRAY),
                        Component.text("Click to print cart to chat.", NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(PAY_SLOT, BankMenuItems.item(
                Material.GOLD_INGOT,
                Component.text("Pay", NamedTextColor.GREEN),
                List.of(
                        Component.text("Hold a credit card, or click with one on cursor.", NamedTextColor.GRAY),
                        Component.text("Total: " + manager.cartTotal(cart), NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(SUMMARY_SLOT, BankMenuItems.item(
                Material.COMPARATOR,
                Component.text(terminal == null ? "Unknown Terminal" : terminal.name(), NamedTextColor.GOLD),
                List.of(Component.text("Items: " + items.size(), NamedTextColor.GRAY))
        ));

        int slot = ITEM_START_SLOT;
        for (BankTerminalItem item : items) {
            if (slot >= SIZE) {
                break;
            }
            inventory.setItem(slot, BankMenuItems.item(
                    item.iconMaterial(),
                    Component.text(item.displayName(), NamedTextColor.AQUA),
                    List.of(
                            Component.text("Price: " + item.price(), NamedTextColor.GRAY),
                            Component.text("Click: add 1 to cart.", NamedTextColor.DARK_GRAY),
                            Component.text("Shift-click: add 10.", NamedTextColor.DARK_GRAY)
                    )
            ));
            itemSlots.put(slot, item.itemId());
            slot++;
        }
    }

    private void sendCart(Player player) {
        List<BankCartLine> cart = manager.listCart(player, terminalId);
        if (cart.isEmpty()) {
            player.sendMessage(Component.text("Your cart is empty.", NamedTextColor.YELLOW));
            return;
        }
        player.sendMessage(Component.text("Cart:", NamedTextColor.GOLD));
        for (BankCartLine line : cart) {
            player.sendMessage(Component.text("- " + line.amount() + "x " + line.displayName()
                    + " = " + line.lineTotal(), NamedTextColor.GRAY));
        }
        player.sendMessage(Component.text("Total: " + manager.cartTotal(cart), NamedTextColor.GREEN));
    }
}
