package krispasi.omGames.hallsofcarnage;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class HallsMainMenu {
    private HallsMainMenu() {
    }

    public static void open(Player player,
                            List<HallsScenario> scenarios,
                            List<HallsShameService.ShameEntry> leaderboard) {
        Inventory inventory = Bukkit.createInventory(new Holder(), 27, Component.text("Halls of Carnage", NamedTextColor.DARK_RED));
        int slot = 10;
        for (HallsScenario scenario : scenarios) {
            if (slot > 16) {
                break;
            }
            inventory.setItem(slot++, scenarioItem(scenario));
        }
        inventory.setItem(22, leaderboardItem(leaderboard));
        player.openInventory(inventory);
    }

    public static boolean isMenu(Inventory inventory) {
        return inventory != null && inventory.getHolder(false) instanceof Holder;
    }

    private static ItemStack scenarioItem(HallsScenario scenario) {
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(scenario.name(), NamedTextColor.GOLD));
        List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text("Difficulty: " + scenario.difficulty(), NamedTextColor.GRAY));
        lore.add(Component.text("Players: " + scenario.minPlayers() + "-" + scenario.maxPlayers(), NamedTextColor.GRAY));
        lore.add(Component.text("Floors: " + scenario.floorCount(), NamedTextColor.GRAY));
        for (String line : scenario.description()) {
            lore.add(Component.text(line, NamedTextColor.DARK_GRAY));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack leaderboardItem(List<HallsShameService.ShameEntry> leaderboard) {
        ItemStack item = new ItemStack(Material.SOUL_LANTERN);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Lowest Shame", NamedTextColor.AQUA));
        List<Component> lore = new java.util.ArrayList<>();
        if (leaderboard.isEmpty()) {
            lore.add(Component.text("No shame recorded yet.", NamedTextColor.GRAY));
        } else {
            int rank = 1;
            for (HallsShameService.ShameEntry entry : leaderboard) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(entry.playerId());
                String name = player.getName() == null ? entry.playerId().toString().substring(0, 8) : player.getName();
                lore.add(Component.text(rank++ + ". " + name + ": " + entry.shame(), NamedTextColor.GRAY));
            }
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
