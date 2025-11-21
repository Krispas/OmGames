package krispasi.omGames.bedwars.menu;

import krispasi.omGames.bedwars.model.BedWarsMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BedWarsMenuFactory {
    public Inventory buildMapMenu(Map<String, BedWarsMap> maps) {
        // quick 27-slot chest menu; easy to extend later
        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.BLUE + "Pick BedWars Map");
        int slot = 0;
        for (BedWarsMap map : maps.values()) {
            ItemStack item = new ItemStack(Material.MAP);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GREEN + map.getName());
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "World: " + map.getWorldName());
                lore.add(ChatColor.GRAY + "Teams: " + map.getTeams().size());
                lore.add(ChatColor.GRAY + "Diamonds: " + map.getDiamondGens().size());
                lore.add(ChatColor.GRAY + "Emeralds: " + map.getEmeraldGens().size());
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inventory.setItem(slot, item);
            slot++;
        }
        return inventory;
    }
}
