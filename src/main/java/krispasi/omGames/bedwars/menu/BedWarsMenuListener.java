package krispasi.omGames.bedwars.menu;

import krispasi.omGames.bedwars.model.BedWarsMap;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;
import java.util.Optional;

public class BedWarsMenuListener implements Listener {
    private final Map<String, BedWarsMap> maps;
    private final String dimensionName;

    public BedWarsMenuListener(Map<String, BedWarsMap> maps, String dimensionName) {
        this.maps = maps;
        this.dimensionName = dimensionName;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTitle() == null || !event.getView().getTitle().contains("Pick BedWars Map")) return;

        event.setCancelled(true);

        // keep admin menu limited to the BedWars dimension
        if (!player.getWorld().getName().equalsIgnoreCase(dimensionName)) {
            player.sendMessage(ChatColor.RED + "Hop into the BedWars dimension first.");
            return;
        }

        try {
            Optional.ofNullable(event.getCurrentItem())
                    .flatMap(item -> Optional.ofNullable(item.getItemMeta()))
                    .map(meta -> ChatColor.stripColor(meta.getDisplayName()))
                    .map(String::toLowerCase)
                    .flatMap(name -> Optional.ofNullable(maps.get(name)))
                    .ifPresent(map -> {
                        // leave the menu open so the admin can keep assigning things after picking the map
                        player.sendMessage(ChatColor.YELLOW + "Selected map: " + map.getName() + ". Keep the menu open to assign teams.");
                    });
        } catch (Exception ex) {
            player.sendMessage(ChatColor.RED + "Something went wrong while picking a map.");
        }
    }
}
