package krispasi.omGames.random;

import java.util.ArrayList;
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

public final class RandomGifMainMenu implements RandomInventoryMenu {
    private static final int SIZE = 54;
    private static final int CREATE_SLOT = 10;
    private static final int RELOAD_SLOT = 12;
    private static final int FOLDER_SLOT = 14;
    private static final int SUMMARY_SLOT = 16;
    private static final int BINDINGS_START_SLOT = 18;

    private final RandomGifManager manager;
    private final Inventory inventory;
    private final Map<Integer, Integer> bindingSlots = new HashMap<>();

    RandomGifMainMenu(RandomGifManager manager) {
        this.manager = manager;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("OmGames GIF", NamedTextColor.LIGHT_PURPLE));
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
        if (slot == CREATE_SLOT) {
            new RandomGifFileMenu(manager).open(player);
            return;
        }
        if (slot == RELOAD_SLOT) {
            send(player, manager.reload());
            refresh();
            return;
        }

        Integer mapId = bindingSlots.get(slot);
        if (mapId == null) {
            return;
        }
        if (event.isShiftClick()) {
            send(player, manager.removeBinding(mapId));
            refresh();
        } else {
            send(player, manager.giveMap(player, mapId));
        }
    }

    private void refresh() {
        inventory.clear();
        bindingSlots.clear();

        inventory.setItem(CREATE_SLOT, item(
                Material.EMERALD_BLOCK,
                Component.text("+ Create New GIF", NamedTextColor.GREEN),
                List.of(
                        Component.text("Choose a .gif file from the server folder.", NamedTextColor.GRAY),
                        Component.text("Map id is entered in chat after selection.", NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(RELOAD_SLOT, item(
                Material.COMPASS,
                Component.text("Reload GIFs", NamedTextColor.YELLOW),
                List.of(Component.text("Reloads saved links and GIF files.", NamedTextColor.GRAY))
        ));
        inventory.setItem(FOLDER_SLOT, item(
                Material.CHEST,
                Component.text("GIF Folder", NamedTextColor.AQUA),
                List.of(
                        Component.text(manager.getGifFolderDisplayPath(), NamedTextColor.GRAY),
                        Component.text("Drop .gif files there on the server.", NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(SUMMARY_SLOT, item(
                Material.FILLED_MAP,
                Component.text("Linked GIF Maps", NamedTextColor.LIGHT_PURPLE),
                List.of(
                        Component.text("Links: " + manager.getBindingsForMenu().size(), NamedTextColor.GRAY),
                        Component.text("Available files: " + manager.getAvailableGifCount(), NamedTextColor.GRAY),
                        Component.text("Animation range: 20 blocks from item frame.", NamedTextColor.DARK_GRAY)
                )
        ));

        int slot = BINDINGS_START_SLOT;
        for (RandomGifBinding binding : manager.getBindingsForMenu()) {
            if (slot >= SIZE) {
                break;
            }
            inventory.setItem(slot, bindingItem(binding));
            bindingSlots.put(slot, binding.baseMapId());
            slot++;
        }
    }

    private ItemStack bindingItem(RandomGifBinding binding) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("File: " + binding.fileName(), NamedTextColor.GRAY));
        lore.add(Component.text("Size: " + binding.sizeLabel(), NamedTextColor.GRAY));
        lore.add(Component.text("Map ids: " + binding.mapIds(), NamedTextColor.GRAY));
        lore.add(Component.text("Frames: " + binding.frameCount(), NamedTextColor.DARK_GRAY));
        lore.add(Component.text("Click to receive these maps.", NamedTextColor.YELLOW));
        lore.add(Component.text("Shift-click to delete link.", NamedTextColor.RED));
        return item(
                Material.FILLED_MAP,
                Component.text("Map " + binding.baseMapId() + " " + binding.sizeLabel()
                        + " -> " + binding.fileName(), NamedTextColor.WHITE),
                lore
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

    private void send(Player player, RandomGifManager.Result result) {
        player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
    }
}
