package krispasi.omGames.bank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public final class BankTerminalIconMenu implements BankInventoryMenu {
    private static final int SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final List<Material> ICONS = Arrays.stream(Material.values())
            .filter(material -> !material.isAir() && material.isItem())
            .toList();

    private final BankManager manager;
    private final String itemId;
    private final int page;
    private final Inventory inventory;

    public BankTerminalIconMenu(BankManager manager, String itemId, int page) {
        this.manager = manager;
        this.itemId = itemId;
        this.page = Math.max(0, page);
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Terminal Icon", NamedTextColor.GOLD));
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
            manager.openTerminalItemEditMenu(player, itemId);
            return;
        }
        if (slot == PREVIOUS_SLOT && page > 0) {
            manager.openTerminalIconMenu(player, itemId, page - 1);
            return;
        }
        if (slot == NEXT_SLOT && (page + 1) * PAGE_SIZE < ICONS.size()) {
            manager.openTerminalIconMenu(player, itemId, page + 1);
            return;
        }
        int index = page * PAGE_SIZE + slot;
        if (slot < 0 || slot >= PAGE_SIZE || index >= ICONS.size()) {
            return;
        }
        Material material = ICONS.get(index);
        BankManager.Result result = manager.updateTerminalItemIcon(player, itemId, material);
        player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
        if (result.success()) {
            manager.openTerminalItemEditMenu(player, itemId);
        }
    }

    private void refresh() {
        inventory.clear();
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, ICONS.size());
        List<Material> pageIcons = new ArrayList<>(ICONS.subList(start, end));
        for (int slot = 0; slot < pageIcons.size(); slot++) {
            Material material = pageIcons.get(slot);
            inventory.setItem(slot, BankMenuItems.item(
                    material,
                    Component.text(material.name().toLowerCase(java.util.Locale.ROOT), NamedTextColor.WHITE),
                    List.of(Component.text("Click to use this icon.", NamedTextColor.GRAY))
            ));
        }
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, BankMenuItems.item(
                    Material.ARROW,
                    Component.text("Previous", NamedTextColor.YELLOW),
                    List.of(Component.text("Page " + page, NamedTextColor.GRAY))
            ));
        }
        inventory.setItem(BACK_SLOT, BankMenuItems.item(
                Material.BARRIER,
                Component.text("Back", NamedTextColor.YELLOW),
                List.of(Component.text("Return to item edit.", NamedTextColor.GRAY))
        ));
        if (end < ICONS.size()) {
            inventory.setItem(NEXT_SLOT, BankMenuItems.item(
                    Material.ARROW,
                    Component.text("Next", NamedTextColor.YELLOW),
                    List.of(Component.text("Page " + (page + 2), NamedTextColor.GRAY))
            ));
        }
    }
}
