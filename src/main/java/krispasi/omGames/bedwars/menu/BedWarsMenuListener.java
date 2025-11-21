package krispasi.omGames.bedwars.menu;

import krispasi.omGames.bedwars.model.BedWarsMap;
import krispasi.omGames.bedwars.game.BedWarsMatchManager;
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
    private final BedWarsMatchManager matchManager;

    public BedWarsMenuListener(Map<String, BedWarsMap> maps, String dimensionName, BedWarsMatchManager matchManager) {
        this.maps = maps;
        this.dimensionName = dimensionName;
        this.matchManager = matchManager;
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
                        player.sendMessage(ChatColor.YELLOW + "Selected map: " + map.getName() + ". Starting BedWars now.");
                        if (matchManager.isRunning()) {
                            player.sendMessage(ChatColor.RED + "A match is already live.");
                            return;
                        }
                        BedWarsMatchManager.Mode mode = player.getWorld().getPlayers().size() > map.getTeams().size() ?
                                BedWarsMatchManager.Mode.DOUBLES : BedWarsMatchManager.Mode.SOLO;
                        if (matchManager.startMatch(map, mode, player.getWorld().getPlayers())) {
                            player.sendMessage(ChatColor.GREEN + "BedWars started on " + map.getName() + ".");
                        } else {
                            player.sendMessage(ChatColor.RED + "Could not start BedWars. Check console.");
                        }
                    });
        } catch (Exception ex) {
            player.sendMessage(ChatColor.RED + "Something went wrong while picking a map.");
        }
    }
}
