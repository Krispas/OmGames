package krispasi.omGames.bank;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class BankAtmDepositMenu implements BankInventoryMenu {
    private static final int SIZE = 54;
    private static final int BACK_SLOT = 45;
    private static final int CONFIRM_SLOT = 49;
    private static final int INFO_SLOT = 53;
    private static final List<Integer> DEPOSIT_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    );

    private final BankManager manager;
    private final String cardId;
    private final Inventory inventory;

    public BankAtmDepositMenu(BankManager manager, String cardId) {
        this.manager = manager;
        this.cardId = cardId;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("ATM Deposit", NamedTextColor.GOLD));
        refreshControls();
    }

    public void open(Player player) {
        refreshControls();
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
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        int slot = event.getRawSlot();
        if (slot >= inventory.getSize()) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                shiftMoveIntoDeposit(event);
                refreshControls();
                return;
            }
            event.setCancelled(false);
            return;
        }
        if (DEPOSIT_SLOTS.contains(slot)) {
            event.setCancelled(false);
            return;
        }
        event.setCancelled(true);
        if (slot == BACK_SLOT) {
            returnDepositedItems(player);
            manager.openAtm(player, cardId);
            return;
        }
        if (slot != CONFIRM_SLOT) {
            return;
        }
        BankManager.Result result = manager.depositCreditsFromInventory(player, cardId, inventory, DEPOSIT_SLOTS);
        player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
        if (result.success()) {
            returnDepositedItems(player);
            manager.openAtm(player, cardId);
        } else {
            refreshControls();
        }
    }

    @Override
    public void handleDrag(InventoryDragEvent event) {
        for (int slot : event.getRawSlots()) {
            if (slot < inventory.getSize() && !DEPOSIT_SLOTS.contains(slot)) {
                event.setCancelled(true);
                return;
            }
        }
        event.setCancelled(false);
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            returnDepositedItems(player);
        }
    }

    private void refreshControls() {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!DEPOSIT_SLOTS.contains(slot)) {
                inventory.setItem(slot, null);
            }
        }
        inventory.setItem(CONFIRM_SLOT, BankMenuItems.item(
                Material.EMERALD_BLOCK,
                Component.text("Deposit Inserted Credits", NamedTextColor.GREEN),
                List.of(
                        Component.text("Credits placed in the slots above will be deposited.", NamedTextColor.GRAY),
                        Component.text("Unrecognized items are returned.", NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(BACK_SLOT, BankMenuItems.item(
                Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW),
                List.of(Component.text("Return to ATM.", NamedTextColor.GRAY))
        ));
        inventory.setItem(INFO_SLOT, BankMenuItems.item(
                Material.HOPPER,
                Component.text("Deposit Slots", NamedTextColor.GOLD),
                List.of(
                        Component.text("Put OmVeins credit items into the empty slots.", NamedTextColor.GRAY),
                        Component.text("Then click the emerald block.", NamedTextColor.DARK_GRAY)
                )
        ));
    }

    private void shiftMoveIntoDeposit(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        ItemStack remaining = clicked.clone();
        for (int slot : DEPOSIT_SLOTS) {
            ItemStack target = inventory.getItem(slot);
            if (target == null || target.getType().isAir()) {
                inventory.setItem(slot, remaining);
                event.setCurrentItem(null);
                return;
            }
            if (!target.isSimilar(remaining)) {
                continue;
            }
            int maxStackSize = Math.min(target.getMaxStackSize(), inventory.getMaxStackSize());
            int space = maxStackSize - target.getAmount();
            if (space <= 0) {
                continue;
            }
            int moved = Math.min(space, remaining.getAmount());
            target.setAmount(target.getAmount() + moved);
            remaining.setAmount(remaining.getAmount() - moved);
            if (remaining.getAmount() <= 0) {
                event.setCurrentItem(null);
                return;
            }
        }
        clicked.setAmount(remaining.getAmount());
        event.setCurrentItem(clicked);
    }

    private void returnDepositedItems(Player player) {
        for (int slot : DEPOSIT_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            inventory.setItem(slot, null);
            manager.giveOrDrop(player, item);
        }
    }
}
