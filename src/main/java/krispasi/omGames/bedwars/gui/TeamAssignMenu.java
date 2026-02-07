package krispasi.omGames.bedwars.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.game.GameSession;
import krispasi.omGames.bedwars.model.TeamColor;
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
import org.bukkit.inventory.meta.SkullMeta;

public class TeamAssignMenu implements InventoryHolder {
    private static final int INVENTORY_SIZE = 54;
    private static final int TEAM_ROW_START = 45;
    private static final int START_SLOT = 53;

    private final BedwarsManager bedwarsManager;
    private final GameSession session;
    private final Inventory inventory;
    private final Map<Integer, UUID> playerSlots = new HashMap<>();

    public TeamAssignMenu(BedwarsManager bedwarsManager, GameSession session) {
        this.bedwarsManager = bedwarsManager;
        this.session = session;
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, Component.text("BedWars - Teams", NamedTextColor.GOLD));
        refresh();
    }

    public void open(Player player) {
        refresh();
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
        if (!event.getWhoClicked().hasPermission("omgames.bw.start")) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == START_SLOT) {
            Player player = (Player) event.getWhoClicked();
            bedwarsManager.startSession(player, session);
            player.closeInventory();
            return;
        }
        UUID playerId = playerSlots.get(slot);
        if (playerId == null) {
            return;
        }
        cycleTeam(playerId);
        refresh();
    }

    private void refresh() {
        inventory.clear();
        playerSlots.clear();

        int slot = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (slot >= TEAM_ROW_START) {
                break;
            }
            inventory.setItem(slot, buildPlayerItem(player));
            playerSlots.put(slot, player.getUniqueId());
            slot++;
        }

        int teamSlot = TEAM_ROW_START;
        for (TeamColor team : session.getArena().getTeams()) {
            if (teamSlot >= START_SLOT) {
                break;
            }
            inventory.setItem(teamSlot, buildTeamItem(team));
            teamSlot++;
        }

        inventory.setItem(START_SLOT, buildStartItem());
    }

    private ItemStack buildPlayerItem(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(Component.text(player.getName(), NamedTextColor.WHITE));

        TeamColor team = session.getTeam(player.getUniqueId());
        Component teamLine = team == null
                ? Component.text("Team: Unassigned", NamedTextColor.GRAY)
                : Component.text("Team: ", NamedTextColor.GRAY).append(team.displayComponent());
        meta.lore(List.of(
                teamLine,
                Component.text("Click to cycle team", NamedTextColor.DARK_GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildTeamItem(TeamColor team) {
        ItemStack item = new ItemStack(team.wool());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(team.displayComponent());
        int count = (int) session.getAssignments().values().stream()
                .filter(team::equals)
                .count();
        meta.lore(List.of(Component.text("Players: " + count, NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildStartItem() {
        ItemStack item = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Start Game", NamedTextColor.GREEN));
        List<Component> lore = List.of(
                Component.text("Starts BedWars with the current teams.", NamedTextColor.GRAY),
                Component.text("Generators will begin immediately.", NamedTextColor.DARK_GRAY)
        );
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void cycleTeam(UUID playerId) {
        List<TeamColor> teams = session.getArena().getTeams();
        if (teams.isEmpty()) {
            session.assignTeam(playerId, null);
            return;
        }
        TeamColor current = session.getTeam(playerId);
        if (current == null) {
            session.assignTeam(playerId, teams.get(0));
            return;
        }
        int index = teams.indexOf(current);
        if (index == -1 || index + 1 >= teams.size()) {
            session.assignTeam(playerId, null);
            return;
        }
        session.assignTeam(playerId, teams.get(index + 1));
    }
}
