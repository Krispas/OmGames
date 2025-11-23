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
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Collectors;

public class BedWarsMenuListener implements Listener {
    private final Map<String, BedWarsMap> maps;
    private final String dimensionName;
    private final BedWarsMatchManager matchManager;
    private final BedWarsMenuFactory menuFactory;
    private final Map<UUID, AssignmentSession> sessions = new HashMap<>();

    public BedWarsMenuListener(Map<String, BedWarsMap> maps, String dimensionName, BedWarsMatchManager matchManager) {
        this.maps = maps;
        this.dimensionName = dimensionName;
        this.matchManager = matchManager;
        this.menuFactory = new BedWarsMenuFactory();
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (title == null) return;

        if (title.contains("Pick BedWars Map")) {
            handleMapClick(event, player);
        } else if (title.contains("Assign BedWars Teams")) {
            handleAssignClick(event, player);
        }
    }

    private void handleMapClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

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
                        if (matchManager.isRunning()) {
                            player.sendMessage(ChatColor.RED + "A match is already live.");
                            return;
                        }
                        player.sendMessage(ChatColor.YELLOW + "Selected map: " + map.getName() + ". Assign teams.");
                        AssignmentSession session = new AssignmentSession(map,
                                player.getWorld().getPlayers().stream()
                                        .filter(p -> p.getWorld().getName().equalsIgnoreCase(dimensionName))
                                        .collect(Collectors.toList()));
                        sessions.put(player.getUniqueId(), session);
                        player.openInventory(menuFactory.buildAssignmentMenu(map, session.assignments, session.selectedPlayer,
                                session.pool, session.mode.name()));
                    });
        } catch (Exception ex) {
            player.sendMessage(ChatColor.RED + "Something went wrong while picking a map.");
        }
    }

    private void handleAssignClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        AssignmentSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        try {
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= event.getInventory().getSize()) return;

            if (slot == 53) {
                // start match
                if (matchManager.startMatch(session.map, session.mode, session.pool, session.assignments)) {
                    player.sendMessage(ChatColor.GREEN + "BedWars started on " + session.map.getName() + ".");
                } else {
                    player.sendMessage(ChatColor.RED + "Could not start BedWars. Check console.");
                }
                sessions.remove(player.getUniqueId());
                player.closeInventory();
                return;
            }

            if (slot == 45) {
                session.toggleMode();
                player.sendMessage(ChatColor.AQUA + "Mode now " + session.mode);
                player.openInventory(menuFactory.buildAssignmentMenu(session.map, session.assignments, session.selectedPlayer,
                        session.pool, session.mode.name()));
                return;
            }

            // player heads row
            if (slot >= 0 && slot < 9 && event.getCurrentItem() != null && event.getCurrentItem().getType() == org.bukkit.Material.PLAYER_HEAD) {
                UUID picked = Optional.ofNullable(event.getCurrentItem().getItemMeta())
                        .filter(meta -> meta instanceof org.bukkit.inventory.meta.SkullMeta)
                        .map(meta -> ((org.bukkit.inventory.meta.SkullMeta) meta).getOwningPlayer())
                        .map(org.bukkit.OfflinePlayer::getUniqueId)
                        .orElse(null);
                if (picked != null) {
                    session.selectedPlayer = picked;
                    player.sendMessage(ChatColor.YELLOW + "Selected " + org.bukkit.Bukkit.getOfflinePlayer(picked).getName());
                    player.openInventory(menuFactory.buildAssignmentMenu(session.map, session.assignments, session.selectedPlayer,
                            session.pool, session.mode.name()));
                }
                return;
            }

            // team wool slots start at 18 and go up by team count
            if (slot >= 18 && slot < 18 + session.map.getTeams().size()) {
                BedWarsMap map = session.map;
                String teamName = map.getTeams().values().stream().skip(slot - 18).findFirst().map(t -> t.getName().toLowerCase()).orElse(null);
                if (teamName == null) return;
                if (event.isShiftClick()) {
                    // clear assignments
                    session.assignments.entrySet().removeIf(e -> e.getValue().equalsIgnoreCase(teamName));
                    player.sendMessage(ChatColor.GRAY + "Cleared team " + teamName);
                } else {
                    if (session.selectedPlayer == null) {
                        player.sendMessage(ChatColor.RED + "Pick a player head first.");
                        return;
                    }
                    session.assignments.put(session.selectedPlayer, teamName);
                    player.sendMessage(ChatColor.GREEN + "Assigned " + org.bukkit.Bukkit.getOfflinePlayer(session.selectedPlayer).getName()
                            + " to " + teamName);
                }
                player.openInventory(menuFactory.buildAssignmentMenu(session.map, session.assignments, session.selectedPlayer,
                        session.pool, session.mode.name()));
            }
        } catch (Exception ex) {
            player.sendMessage(ChatColor.RED + "Error handling team assignment.");
        }
    }

    private static class AssignmentSession {
        private final BedWarsMap map;
        private final Map<UUID, String> assignments = new HashMap<>();
        private UUID selectedPlayer;
        private BedWarsMatchManager.Mode mode;
        private final java.util.List<Player> pool;

        AssignmentSession(BedWarsMap map, java.util.List<Player> pool) {
            this.map = map;
            this.pool = pool;
            this.mode = pool.size() > map.getTeams().size() ? BedWarsMatchManager.Mode.DOUBLES : BedWarsMatchManager.Mode.SOLO;
        }

        void toggleMode() {
            this.mode = this.mode == BedWarsMatchManager.Mode.SOLO ? BedWarsMatchManager.Mode.DOUBLES : BedWarsMatchManager.Mode.SOLO;
        }
    }
}
