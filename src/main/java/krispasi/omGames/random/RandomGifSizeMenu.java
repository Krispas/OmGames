package krispasi.omGames.random;

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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class RandomGifSizeMenu implements RandomInventoryMenu {
    private static final int SIZE = 54;
    private static final int BACK_SLOT = 45;
    private static final int START_SLOT = 10;

    private final RandomGifManager manager;
    private final String fileName;
    private final Inventory inventory;
    private final Map<Integer, RandomGifSize> sizeSlots = new HashMap<>();

    RandomGifSizeMenu(RandomGifManager manager, String fileName) {
        this.manager = manager;
        this.fileName = fileName;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("GIF Size", NamedTextColor.LIGHT_PURPLE));
        refresh();
    }

    void open(Player player) {
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
            new RandomGifFileMenu(manager).open(player);
            return;
        }
        RandomGifSize selected = sizeSlots.get(slot);
        if (selected != null) {
            player.closeInventory();
            manager.beginMapIdPrompt(player, fileName, selected);
        }
    }

    private void refresh() {
        inventory.clear();
        sizeSlots.clear();

        int slot = START_SLOT;
        for (RandomGifSize size : RandomGifSize.MENU_SIZES) {
            inventory.setItem(slot, sizeItem(size));
            sizeSlots.put(slot, size);
            slot++;
            if (slot % 9 == 8) {
                slot += 2;
            }
        }

        inventory.setItem(BACK_SLOT, item(
                Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW),
                List.of(Component.text("Return to GIF files.", NamedTextColor.GRAY))
        ));
    }

    private ItemStack sizeItem(RandomGifSize size) {
        Material material = size.mapCount() == 1 ? Material.WHITE_STAINED_GLASS_PANE : Material.LIGHT_BLUE_STAINED_GLASS_PANE;
        return item(
                material,
                Component.text(size.label(), NamedTextColor.AQUA),
                List.of(
                        Component.text("Maps needed: " + size.mapCount(), NamedTextColor.GRAY),
                        Component.text("Uses consecutive map ids.", NamedTextColor.GRAY),
                        Component.text("Click to choose this size.", NamedTextColor.GREEN)
                )
        );
    }

    private ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}
