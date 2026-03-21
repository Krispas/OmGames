package krispasi.omGames.bedwars.lobby;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Lobby parkour leaderboard rendered with text displays.
 */
public class BedwarsParkourLeaderboard {
    private static final String[] FALLBACK_WORLD_NAMES = {"bedwars_lobby", "bw", "bedwars"};
    private static final String DISPLAY_TAG = "bw_parkour_leaderboard";
    private static final int TOP_LIMIT = 10;
    private static final long CHECK_INTERVAL_TICKS = 20L;
    private static final long REFRESH_INTERVAL_MILLIS = 5_000L;
    private static final double NEAR_RADIUS_BLOCKS = 32.0;
    private static final double DEFAULT_ANCHOR_X = 6.0;
    private static final double DEFAULT_ANCHOR_Y = 73.0;
    private static final double DEFAULT_ANCHOR_Z = -1.0;
    private static final double HEIGHT_OFFSET = 3.2;
    private static final double LINE_SPACING = 0.28;
    private static final float BOARD_YAW = 90.0f;

    private final JavaPlugin plugin;
    private final BedwarsLobbyParkour parkour;
    private final List<UUID> activeDisplays = new ArrayList<>();
    private final Map<UUID, String> nameCache = new HashMap<>();

    private String anchorWorldName;
    private double anchorX = DEFAULT_ANCHOR_X;
    private double anchorY = DEFAULT_ANCHOR_Y;
    private double anchorZ = DEFAULT_ANCHOR_Z;
    private BukkitTask task;
    private long lastRefreshMillis;

    public BedwarsParkourLeaderboard(JavaPlugin plugin, BedwarsLobbyParkour parkour) {
        this.plugin = plugin;
        this.parkour = parkour;
    }

    public void start() {
        if (task != null) {
            return;
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 40L, CHECK_INTERVAL_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        removeTrackedDisplays();
        for (World world : Bukkit.getWorlds()) {
            removeTaggedDisplays(world);
        }
    }

    public void configureAnchor(String worldName, double x, double y, double z) {
        this.anchorWorldName = worldName == null || worldName.isBlank() ? null : worldName.trim();
        this.anchorX = x;
        this.anchorY = y;
        this.anchorZ = z;
        this.lastRefreshMillis = 0L;
        removeTrackedDisplays();
    }

    private void tick() {
        World world = resolveWorld();
        if (world == null) {
            activeDisplays.clear();
            return;
        }
        Location anchor = new Location(world, anchorX, anchorY, anchorZ);
        if (!hasNearbyPlayers(world, anchor)) {
            if (!activeDisplays.isEmpty()) {
                removeAllDisplays(world);
            } else {
                removeTaggedDisplays(world);
            }
            return;
        }
        long now = System.currentTimeMillis();
        if (!activeDisplays.isEmpty() && now - lastRefreshMillis < REFRESH_INTERVAL_MILLIS) {
            return;
        }
        render(world, anchor.clone().add(0.0, HEIGHT_OFFSET, 0.0));
        lastRefreshMillis = now;
    }

    private World resolveWorld() {
        if (anchorWorldName != null && !anchorWorldName.isBlank()) {
            World world = Bukkit.getWorld(anchorWorldName);
            if (world != null) {
                return world;
            }
        }
        for (String fallback : FALLBACK_WORLD_NAMES) {
            World world = Bukkit.getWorld(fallback);
            if (world != null) {
                return world;
            }
        }
        return null;
    }

    private boolean hasNearbyPlayers(World world, Location anchor) {
        double radiusSquared = NEAR_RADIUS_BLOCKS * NEAR_RADIUS_BLOCKS;
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (player.getLocation().distanceSquared(anchor) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private void render(World world, Location origin) {
        removeAllDisplays(world);
        List<Component> lines = buildLines();
        for (int i = 0; i < lines.size(); i++) {
            Location line = origin.clone().subtract(0.0, i * LINE_SPACING, 0.0);
            line.setYaw(BOARD_YAW);
            line.setPitch(0.0f);
            Component text = lines.get(i);
            TextDisplay display = world.spawn(line, TextDisplay.class, entity -> configureDisplay(entity, text));
            activeDisplays.add(display.getUniqueId());
        }
    }

    private List<Component> buildLines() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("Lobby Parkour", NamedTextColor.AQUA, TextDecoration.BOLD, TextDecoration.UNDERLINED));
        lines.add(Component.text("Top Times", NamedTextColor.YELLOW, TextDecoration.BOLD));
        lines.add(Component.empty());

        List<BedwarsLobbyParkour.ParkourTopEntry> top = parkour.getTopEntries(TOP_LIMIT);
        for (int i = 0; i < TOP_LIMIT; i++) {
            if (i >= top.size()) {
                lines.add(Component.text((i + 1) + ". ---", NamedTextColor.DARK_GRAY));
                continue;
            }
            BedwarsLobbyParkour.ParkourTopEntry entry = top.get(i);
            String name = resolvePlayerName(entry.playerId());
            String time = formatElapsed(entry.timeMillis());
            lines.add(Component.text((i + 1) + ". ", NamedTextColor.YELLOW)
                    .append(Component.text(name, NamedTextColor.GOLD))
                    .append(Component.text(" ", NamedTextColor.GRAY))
                    .append(Component.text(time, NamedTextColor.GREEN))
                    .append(Component.text(" (" + entry.checkpointUses() + " cp)", NamedTextColor.GRAY)));
        }
        return lines;
    }

    private void configureDisplay(TextDisplay display, Component text) {
        display.text(text);
        display.setBillboard(Display.Billboard.CENTER);
        display.setShadowed(false);
        display.setSeeThrough(false);
        display.setLineWidth(300);
        display.setDefaultBackground(false);
        display.setGravity(false);
        display.setPersistent(false);
        display.addScoreboardTag(DISPLAY_TAG);
    }

    private void removeAllDisplays(World world) {
        removeTrackedDisplays();
        removeTaggedDisplays(world);
    }

    private void removeTrackedDisplays() {
        for (UUID id : activeDisplays) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        activeDisplays.clear();
    }

    private void removeTaggedDisplays(World world) {
        for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
            if (display.getScoreboardTags().contains(DISPLAY_TAG)) {
                display.remove();
            }
        }
    }

    private String resolvePlayerName(UUID playerId) {
        String cached = nameCache.get(playerId);
        if (cached != null) {
            return cached;
        }
        String name = Bukkit.getOfflinePlayer(playerId).getName();
        if (name == null || name.isBlank()) {
            name = playerId.toString().substring(0, 8);
        }
        nameCache.put(playerId, name);
        return name;
    }

    private String formatElapsed(long elapsedMillis) {
        Duration duration = Duration.ofMillis(Math.max(0L, elapsedMillis));
        long minutes = duration.toMinutes();
        long seconds = duration.minusMinutes(minutes).toSeconds();
        long millis = duration.minusMinutes(minutes).minusSeconds(seconds).toMillis();
        if (minutes > 0) {
            return String.format(Locale.ROOT, "%d:%02d.%03d", minutes, seconds, millis);
        }
        return String.format(Locale.ROOT, "%d.%03ds", seconds, millis);
    }
}
