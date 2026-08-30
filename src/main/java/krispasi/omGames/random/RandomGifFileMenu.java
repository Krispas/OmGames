package krispasi.omGames.random;

import java.io.File;
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

public final class RandomGifFileMenu implements RandomInventoryMenu {
    private static final int SIZE = 54;
    private static final int FILE_LIMIT = 45;
    private static final int BACK_SLOT = 45;
    private static final int RELOAD_SLOT = 49;

    private final RandomGifManager manager;
    private final Inventory inventory;
    private final Map<Integer, String> fileSlots = new HashMap<>();

    RandomGifFileMenu(RandomGifManager manager) {
        this.manager = manager;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Choose GIF", NamedTextColor.LIGHT_PURPLE));
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
            new RandomGifMainMenu(manager).open(player);
            return;
        }
        if (slot == RELOAD_SLOT) {
            manager.reload();
            refresh();
            return;
        }
        String fileName = fileSlots.get(slot);
        if (fileName != null) {
            new RandomGifSizeMenu(manager, fileName).open(player);
        }
    }

    private void refresh() {
        inventory.clear();
        fileSlots.clear();

        List<File> files = manager.getGifFilesForMenu();
        if (files.isEmpty()) {
            inventory.setItem(22, item(
                    Material.BARRIER,
                    Component.text("No GIF files found", NamedTextColor.RED),
                    List.of(
                            Component.text(manager.getGifFolderDisplayPath(), NamedTextColor.GRAY),
                            Component.text("Add .gif files and click Reload.", NamedTextColor.DARK_GRAY)
                    )
            ));
        } else {
            int slot = 0;
            for (File file : files) {
                if (slot >= FILE_LIMIT) {
                    break;
                }
                inventory.setItem(slot, item(
                        Material.PAPER,
                        Component.text(file.getName(), NamedTextColor.WHITE),
                        List.of(
                                Component.text("Click to choose this GIF.", NamedTextColor.GREEN),
                                Component.text("Then choose the map board size.", NamedTextColor.GRAY)
                        )
                ));
                fileSlots.put(slot, file.getName());
                slot++;
            }
        }

        inventory.setItem(BACK_SLOT, item(
                Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW),
                List.of(Component.text("Return to GIF links.", NamedTextColor.GRAY))
        ));
        inventory.setItem(RELOAD_SLOT, item(
                Material.COMPASS,
                Component.text("Reload Files", NamedTextColor.YELLOW),
                List.of(Component.text("Reloads links and scans the GIF folder.", NamedTextColor.GRAY))
        ));
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
