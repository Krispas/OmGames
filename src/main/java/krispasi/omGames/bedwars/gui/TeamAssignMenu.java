package krispasi.omGames.bedwars.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
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

/**
 * Inventory UI for assigning players to teams and starting the match.
 * <p>Lets admins cycle {@link krispasi.omGames.bedwars.model.TeamColor} for each player,
 * configure team size and selection mode, and launch the session.</p>
 */
public class TeamAssignMenu implements InventoryHolder {
    private static final int INVENTORY_SIZE = 54;
    private static final int SETTINGS_ROW_START = 36;
    private static final int TEAM_ROW_START = 45;
    private static final int START_SLOT = 53;
    private static final int RANDOM_SLOT = SETTINGS_ROW_START;
    private static final int TEAM_PICK_SLOT = SETTINGS_ROW_START + 1;
    private static final int ROTATING_SLOT = SETTINGS_ROW_START + 2;
    private static final int PAUSE_SLOT = SETTINGS_ROW_START + 7;
    private static final int SKIP_SLOT = SETTINGS_ROW_START + 8;

    private final BedwarsManager bedwarsManager;
    private final GameSession session;
    private final Inventory inventory;
    private final Map<Integer, UUID> playerSlots = new java.util.HashMap<>();

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
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.hasPermission("omgames.bw.start")) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == START_SLOT) {
            handleStartClick(player);
            return;
        }
        if (slot == RANDOM_SLOT) {
            if (event.isLeftClick()) {
                assignRandomTeams();
            } else if (event.isRightClick()) {
                cycleTeamSize();
            }
            refresh();
            return;
        }
        if (slot == TEAM_PICK_SLOT) {
            if (event.isLeftClick()) {
                toggleTeamPick();
            } else if (event.isRightClick()) {
                cycleTeamSize();
            }
            refresh();
            return;
        }
        if (slot == ROTATING_SLOT) {
            if (event.isLeftClick()) {
                session.cycleRotatingMode();
                refresh();
                return;
            }
            if (event.isRightClick()) {
                session.setRotatingMode(GameSession.RotatingSelectionMode.MANUAL);
                new RotatingItemMenu(session, player).open(player);
                return;
            }
            return;
        }
        if (slot == PAUSE_SLOT && session.isLobby()) {
            session.toggleLobbyCountdownPause();
            refresh();
            return;
        }
        if (slot == SKIP_SLOT && session.isLobby()) {
            session.skipLobbyCountdown();
            refresh();
            return;
        }
        UUID playerId = playerSlots.get(slot);
        if (playerId == null) {
            return;
        }
        if (event.isLeftClick()) {
            cycleTeam(playerId, 1);
        } else if (event.isRightClick()) {
            cycleTeam(playerId, -1);
        } else {
            return;
        }
        refresh();
    }

    private void handleStartClick(Player player) {
        if (session.isLobby()) {
            return;
        }
        bedwarsManager.startLobby(player, session, 20);
        refresh();
    }

    private void refresh() {
        inventory.clear();
        playerSlots.clear();

        int slot = 0;
        for (Player player : getLobbyPlayers()) {
            if (slot >= TEAM_ROW_START) {
                break;
            }
            inventory.setItem(slot, buildPlayerItem(player));
            playerSlots.put(slot, player.getUniqueId());
            slot++;
        }

        inventory.setItem(RANDOM_SLOT, buildRandomItem());
        inventory.setItem(TEAM_PICK_SLOT, buildTeamPickItem());
        inventory.setItem(ROTATING_SLOT, buildRotatingItem());
        if (session.isLobby()) {
            inventory.setItem(PAUSE_SLOT, buildPauseItem());
            inventory.setItem(SKIP_SLOT, buildSkipItem());
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

    private List<Player> getLobbyPlayers() {
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (session.isInArenaWorld(player.getWorld())) {
                players.add(player);
            }
        }
        return players;
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
                Component.text("Left click: next team", NamedTextColor.DARK_GRAY),
                Component.text("Right click: previous team", NamedTextColor.DARK_GRAY)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildTeamItem(TeamColor team) {
        ItemStack item = new ItemStack(team.wool());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(team.displayComponent());
        int count = session.getTeamMemberCount(team);
        int max = session.getMaxTeamSize();
        meta.lore(List.of(Component.text("Players: " + count + "/" + max, NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildRandomItem() {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Random Teams", NamedTextColor.YELLOW));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Left click: assign random teams", NamedTextColor.GRAY));
        lore.add(Component.text("Right click: team size " + teamSizeName(), NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildTeamPickItem() {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        boolean enabled = session.isTeamPickEnabled();
        meta.displayName(Component.text("Team Pick", enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(enabled ? "Enabled" : "Disabled", NamedTextColor.GRAY));
        lore.add(Component.text("Left click: toggle", NamedTextColor.GRAY));
        lore.add(Component.text("Right click: team size " + teamSizeName(), NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildPauseItem() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        boolean paused = session.isLobbyCountdownPaused();
        meta.displayName(Component.text(paused ? "Resume Timer" : "Pause Timer", NamedTextColor.YELLOW));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Time left: " + session.getLobbyCountdownRemaining() + "s", NamedTextColor.GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildSkipItem() {
        ItemStack item = new ItemStack(Material.FEATHER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Skip Timer", NamedTextColor.YELLOW));
        meta.lore(List.of(Component.text("Start immediately", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildStartItem() {
        boolean lobbyActive = session.isLobby();
        ItemStack item = new ItemStack(lobbyActive ? Material.YELLOW_CONCRETE : Material.LIME_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        if (lobbyActive) {
            meta.displayName(Component.text("Lobby Countdown", NamedTextColor.YELLOW));
            meta.lore(List.of(
                    Component.text("Time left: " + session.getLobbyCountdownRemaining() + "s", NamedTextColor.GRAY),
                    Component.text("Use pause/skip in settings.", NamedTextColor.DARK_GRAY)
            ));
        } else {
            meta.displayName(Component.text("Start Game", NamedTextColor.GREEN));
            meta.lore(List.of(
                    Component.text("Teleports players to the map lobby.", NamedTextColor.GRAY),
                    Component.text("Starts a 20s countdown.", NamedTextColor.DARK_GRAY)
            ));
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildRotatingItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Rotating Items", NamedTextColor.YELLOW));
        List<Component> lore = new ArrayList<>();
        GameSession.RotatingSelectionMode mode = session.getRotatingMode();
        lore.add(Component.text("Mode: " + formatRotatingMode(mode), NamedTextColor.GRAY));
        if (mode == GameSession.RotatingSelectionMode.MANUAL) {
            List<String> selected = session.getManualRotatingItemIds();
            lore.add(Component.text("Selected: " + selected.size() + "/2", selected.isEmpty()
                    ? NamedTextColor.RED
                    : NamedTextColor.GRAY));
            for (String id : selected) {
                lore.add(Component.text(formatRotatingItemName(id), NamedTextColor.DARK_GRAY));
            }
        } else if (mode == GameSession.RotatingSelectionMode.ONE_RANDOM) {
            lore.add(Component.text("Items: 1 random", NamedTextColor.DARK_GRAY));
        } else {
            lore.add(Component.text("Items: 2 random", NamedTextColor.DARK_GRAY));
        }
        lore.add(Component.text("Left click: change mode", NamedTextColor.GRAY));
        lore.add(Component.text("Right click: choose items", NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String formatRotatingMode(GameSession.RotatingSelectionMode mode) {
        return switch (mode) {
            case ONE_RANDOM -> "One Random";
            case MANUAL -> "Manual";
            default -> "Two Random";
        };
    }

    private String formatRotatingItemName(String id) {
        if (id == null) {
            return "Unknown";
        }
        krispasi.omGames.bedwars.shop.ShopConfig config = bedwarsManager.getShopConfig();
        if (config != null) {
            krispasi.omGames.bedwars.shop.ShopItemDefinition definition = config.getItem(id);
            if (definition != null) {
                String display = definition.getDisplayName();
                if (display != null && !display.isBlank()) {
                    return display;
                }
                Material material = definition.getMaterial();
                if (material != null) {
                    return formatMaterialName(material);
                }
            }
        }
        return id;
    }

    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        String[] parts = name.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private void cycleTeam(UUID playerId, int direction) {
        List<TeamColor> teams = session.getArena().getTeams();
        if (teams.isEmpty()) {
            session.assignTeam(playerId, null);
            return;
        }
        TeamColor current = session.getTeam(playerId);
        int startIndex = current == null ? (direction >= 0 ? -1 : 0) : teams.indexOf(current);
        if (startIndex == -1 && current != null) {
            session.assignTeam(playerId, null);
            return;
        }
        int size = teams.size();
        for (int i = 0; i < size; i++) {
            int index = startIndex + (direction >= 0 ? i + 1 : -(i + 1));
            index = ((index % size) + size) % size;
            TeamColor next = teams.get(index);
            if (!session.isTeamFull(next)) {
                session.assignTeam(playerId, next);
                return;
            }
        }
    }

    private void assignRandomTeams() {
        List<Player> players = getLobbyPlayers();
        List<TeamColor> teams = session.getArena().getTeams();
        if (players.isEmpty() || teams.isEmpty()) {
            return;
        }
        for (Player player : players) {
            session.assignTeam(player.getUniqueId(), null);
        }
        Collections.shuffle(players);
        Map<TeamColor, Integer> counts = new EnumMap<>(TeamColor.class);
        for (TeamColor team : teams) {
            counts.put(team, 0);
        }
        int teamIndex = 0;
        int max = session.getMaxTeamSize();
        for (Player player : players) {
            boolean assigned = false;
            for (int tries = 0; tries < teams.size(); tries++) {
                TeamColor team = teams.get(teamIndex);
                teamIndex = (teamIndex + 1) % teams.size();
                int current = counts.getOrDefault(team, 0);
                if (current < max) {
                    session.assignTeam(player.getUniqueId(), team);
                    counts.put(team, current + 1);
                    assigned = true;
                    break;
                }
            }
            if (!assigned) {
                break;
            }
        }
    }

    private void toggleTeamPick() {
        session.setTeamPickEnabled(!session.isTeamPickEnabled());
    }

    private void cycleTeamSize() {
        int size = session.getMaxTeamSize();
        int next = size >= 4 ? 1 : size + 1;
        session.setMaxTeamSize(next);
    }

    private String teamSizeName() {
        int size = session.getMaxTeamSize();
        return switch (size) {
            case 1 -> "Solo";
            case 2 -> "Duo";
            case 3 -> "Trio";
            default -> "Quad";
        };
    }
}
