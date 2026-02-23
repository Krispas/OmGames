package krispasi.omGames.bedwars.gui;

import java.util.HashMap;
import java.util.Map;
import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.model.Arena;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Inventory UI for selecting an arena to start.
 * <p>Lists arenas from {@link krispasi.omGames.bedwars.BedwarsManager} and opens
 * {@link krispasi.omGames.bedwars.gui.TeamAssignMenu}.</p>
 * @see krispasi.omGames.bedwars.gui.TeamAssignMenu
 */
public class MapSelectMenu implements InventoryHolder {
    private static final int RANDOM_SLOT = 26;
    private final BedwarsManager bedwarsManager;
    private final boolean statsEnabled;
    private final Inventory inventory;
    private final Map<Integer, String> arenaSlots = new HashMap<>();

    public MapSelectMenu(BedwarsManager bedwarsManager, boolean statsEnabled) {
        this.bedwarsManager = bedwarsManager;
        this.statsEnabled = statsEnabled;
        this.inventory = Bukkit.createInventory(this, 27, Component.text("BedWars - Select Arena", NamedTextColor.GOLD));
        build();
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void handleClick(InventoryClickEvent event) {
        if (event.getRawSlot() >= inventory.getSize()) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() == RANDOM_SLOT) {
            openRandomArena((Player) event.getWhoClicked());
            return;
        }
        String arenaId = arenaSlots.get(event.getRawSlot());
        if (arenaId == null) {
            return;
        }
        Arena arena = bedwarsManager.getArena(arenaId);
        if (arena == null) {
            return;
        }
        bedwarsManager.openTeamAssignMenu((Player) event.getWhoClicked(), arena, statsEnabled);
    }

    private void build() {
        int slot = 0;
        for (Arena arena : bedwarsManager.getArenas()) {
            if (slot == RANDOM_SLOT) {
                slot++;
            }
            if (slot >= inventory.getSize()) {
                break;
            }
            ItemStack item = new ItemStack(Material.MAP);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(arena.getId(), NamedTextColor.AQUA));
            meta.lore(java.util.List.of(
                    Component.text("World: " + arena.getWorldName(), NamedTextColor.GRAY),
                    Component.text("Teams: " + arena.getTeams().size(), NamedTextColor.GRAY)
            ));
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            arenaSlots.put(slot, arena.getId());
            slot++;
        }
        inventory.setItem(RANDOM_SLOT, buildRandomItem());
    }

    private ItemStack buildRandomItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Random Map", NamedTextColor.YELLOW));
        meta.lore(java.util.List.of(
                Component.text("Click to pick a random arena.", NamedTextColor.GRAY)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private void openRandomArena(Player player) {
        java.util.List<Arena> arenas = bedwarsManager.getArenas().stream().toList();
        if (arenas.isEmpty()) {
            player.sendMessage(Component.text("No arenas configured.", NamedTextColor.RED));
            return;
        }
        Arena arena = arenas.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(arenas.size()));
        bedwarsManager.openTeamAssignMenu(player, arena, statsEnabled);
    }
}
