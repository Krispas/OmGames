package krispasi.omGames.bedwars.menu;

import krispasi.omGames.bedwars.model.BedWarsMap;
import krispasi.omGames.bedwars.model.TeamConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    public Inventory buildAssignmentMenu(BedWarsMap map,
                                         Map<UUID, String> current,
                                         UUID selected,
                                         List<org.bukkit.entity.Player> pool,
                                         String modeLabel) {
        // 54 slots: row 0 player pool, rows 2-5 teams, bottom corners for mode/start
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.GOLD + "Assign BedWars Teams");

        int slot = 0;
        for (org.bukkit.entity.Player p : pool) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(p);
                meta.setDisplayName(ChatColor.YELLOW + p.getName());
                List<String> lore = new ArrayList<>();
                String team = current.get(p.getUniqueId());
                lore.add(ChatColor.GRAY + "Team: " + (team == null ? "(unassigned)" : team));
                if (selected != null && selected.equals(p.getUniqueId())) {
                    lore.add(ChatColor.GREEN + "Selected to assign");
                }
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

        slot = 18;
        for (TeamConfig team : map.getTeams().values()) {
            Material wool = Material.WHITE_WOOL;
            try {
                wool = Material.valueOf(team.getName().toUpperCase() + "_WOOL");
            } catch (Exception ignored) {
            }
            ItemStack banner = new ItemStack(wool);
            ItemMeta meta = banner.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.AQUA + team.getName());
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Click to assign selected player");
                lore.add(ChatColor.GRAY + "Shift-click to clear this team");
                lore.add(ChatColor.YELLOW + "Members:");
                current.entrySet().stream()
                        .filter(e -> e.getValue().equalsIgnoreCase(team.getName()))
                        .map(e -> Bukkit.getOfflinePlayer(e.getKey()).getName())
                        .forEach(name -> lore.add(ChatColor.WHITE + "- " + name));
                meta.setLore(lore);
                banner.setItemMeta(meta);
            }
            inv.setItem(slot++, banner);
        }

        ItemStack mode = new ItemStack(Material.BOOK);
        ItemMeta modeMeta = mode.getItemMeta();
        if (modeMeta != null) {
            modeMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Mode: " + modeLabel);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Click to toggle solo/doubles");
            modeMeta.setLore(lore);
            mode.setItemMeta(modeMeta);
        }
        inv.setItem(45, mode);

        ItemStack start = new ItemStack(Material.LIME_WOOL);
        ItemMeta startMeta = start.getItemMeta();
        if (startMeta != null) {
            startMeta.setDisplayName(ChatColor.GREEN + "Start Match");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Uses current assignments");
            lore.add(ChatColor.GRAY + "Unassigned players fill leftover slots");
            startMeta.setLore(lore);
            start.setItemMeta(startMeta);
        }
        inv.setItem(53, start);

        return inv;
    }
}
