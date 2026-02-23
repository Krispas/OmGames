package krispasi.omGames.bedwars.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/**
 * Inventory UI that lets players pick their team during the lobby phase.
 * <p>Displays available teams, enforces team size limits, and updates the session assignment.</p>
 */
public class TeamPickMenu implements InventoryHolder {
    private static final int INVENTORY_SIZE = 27;

    private final GameSession session;
    private final UUID viewerId;
    private final Inventory inventory;
    private final Map<Integer, TeamColor> teamSlots = new HashMap<>();

    public TeamPickMenu(GameSession session, Player viewer) {
        this.session = session;
        this.viewerId = viewer.getUniqueId();
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE,
                Component.text("Pick a Team", NamedTextColor.GOLD));
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
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.getUniqueId().equals(viewerId)) {
            return;
        }
        TeamColor team = teamSlots.get(event.getRawSlot());
        if (team == null) {
            return;
        }
        if (session.isTeamFull(team)) {
            player.sendMessage(Component.text("That team is full.", NamedTextColor.RED));
            return;
        }
        session.assignTeam(player.getUniqueId(), team);
        player.sendMessage(Component.text("Joined the ", NamedTextColor.GRAY)
                .append(team.displayComponent())
                .append(Component.text(" team.", NamedTextColor.GRAY)));
        player.closeInventory();
    }

    private void build() {
        inventory.clear();
        teamSlots.clear();
        List<TeamColor> teams = session.getArena().getTeams();
        int slot = 0;
        for (TeamColor team : teams) {
            if (slot >= inventory.getSize()) {
                break;
            }
            ItemStack item = new ItemStack(team.wool());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(team.displayComponent());
            int count = session.getTeamMemberCount(team);
            int max = session.getMaxTeamSize();
            meta.lore(List.of(
                    Component.text("Players: " + count + "/" + max, NamedTextColor.GRAY),
                    Component.text("Click to join", NamedTextColor.DARK_GRAY)
            ));
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            teamSlots.put(slot, team);
            slot++;
        }
    }
}
