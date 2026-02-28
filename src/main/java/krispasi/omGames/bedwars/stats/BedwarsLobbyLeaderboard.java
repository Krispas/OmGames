package krispasi.omGames.bedwars.stats;

import java.text.NumberFormat;
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
 * Lobby-only BedWars leaderboard rendered with text displays.
 * <p>Shows three top-10 columns (wins, final kills, beds broken) and only appears
 * while players are near the configured anchor location.</p>
 */
public class BedwarsLobbyLeaderboard {
    private static final String[] WORLD_NAMES = {"bw", "bedwars"};
    private static final String DISPLAY_TAG = "bw_lobby_leaderboard";
    private static final int TOP_LIMIT = 10;
    private static final long CHECK_INTERVAL_TICKS = 20L;
    private static final long REFRESH_INTERVAL_MILLIS = 15_000L;
    private static final double NEAR_RADIUS_BLOCKS = 24.0;
    private static final double ANCHOR_X = 4.0;
    private static final double ANCHOR_Y = 73.0;
    private static final double ANCHOR_Z = 3.0;
    private static final double DEPTH_OFFSET_X = -1.25;
    private static final double COLUMN_MIN_Z = -2.0;
    private static final double COLUMN_MID_Z = 1.0;
    private static final double COLUMN_MAX_Z = 4.0;
    private static final double HEIGHT_OFFSET = 3.2;
    private static final double LINE_SPACING = 0.28;
    private static final float BOARD_YAW = 90.0f;

    private final JavaPlugin plugin;
    private final BedwarsStatsService statsService;
    private final List<UUID> activeDisplays = new ArrayList<>();
    private final Map<UUID, String> nameCache = new HashMap<>();
    private BukkitTask task;
    private long lastRefreshMillis;

    public BedwarsLobbyLeaderboard(JavaPlugin plugin, BedwarsStatsService statsService) {
        this.plugin = plugin;
        this.statsService = statsService;
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
        World world = resolveWorld();
        if (world != null) {
            removeAllDisplays(world);
        } else {
            activeDisplays.clear();
        }
    }

    private void tick() {
        World world = resolveWorld();
        if (world == null) {
            activeDisplays.clear();
            return;
        }
        Location anchor = new Location(world, ANCHOR_X, ANCHOR_Y, ANCHOR_Z);
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
        for (String worldName : WORLD_NAMES) {
            World world = Bukkit.getWorld(worldName);
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

    private void render(World world, Location anchor) {
        removeAllDisplays(world);

        List<Component> wins = buildColumn("Lifetime Wins", statsService.getTopWins(TOP_LIMIT));
        List<Component> finalKills = buildColumn("Lifetime Final Kills", statsService.getTopFinalKills(TOP_LIMIT));
        List<Component> beds = buildColumn("Lifetime Beds Broken", statsService.getTopBedsBroken(TOP_LIMIT));

        double x = ANCHOR_X + DEPTH_OFFSET_X;
        spawnColumn(world, new Location(world, x, anchor.getY(), COLUMN_MIN_Z), wins);
        spawnColumn(world, new Location(world, x, anchor.getY(), COLUMN_MID_Z), finalKills);
        spawnColumn(world, new Location(world, x, anchor.getY(), COLUMN_MAX_Z), beds);
    }

    private List<Component> buildColumn(String title, List<BedwarsStatsService.TopStatEntry> topEntries) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text(title, NamedTextColor.AQUA, TextDecoration.BOLD, TextDecoration.UNDERLINED));
        lines.add(Component.empty());

        for (int i = 0; i < TOP_LIMIT; i++) {
            if (i < topEntries.size()) {
                BedwarsStatsService.TopStatEntry entry = topEntries.get(i);
                String name = resolvePlayerName(entry.playerId());
                lines.add(Component.text((i + 1) + ". ", NamedTextColor.YELLOW)
                        .append(Component.text(name, NamedTextColor.GOLD))
                        .append(Component.text(" ", NamedTextColor.GRAY))
                        .append(Component.text(formatNumber(entry.value()), NamedTextColor.YELLOW)));
            } else {
                lines.add(Component.text((i + 1) + ". ---", NamedTextColor.DARK_GRAY));
            }
        }

        return lines;
    }

    private void spawnColumn(World world, Location origin, List<Component> lines) {
        for (int i = 0; i < lines.size(); i++) {
            Location lineLocation = origin.clone().subtract(0.0, i * LINE_SPACING, 0.0);
            lineLocation.setYaw(BOARD_YAW);
            lineLocation.setPitch(0.0f);
            Component text = lines.get(i);
            TextDisplay display = world.spawn(lineLocation, TextDisplay.class, entity -> configureDisplay(entity, text));
            activeDisplays.add(display.getUniqueId());
        }
    }

    private void configureDisplay(TextDisplay display, Component text) {
        display.text(text);
        display.setBillboard(Display.Billboard.FIXED);
        display.setShadowed(false);
        display.setSeeThrough(false);
        display.setLineWidth(300);
        display.setDefaultBackground(false);
        display.setGravity(false);
        display.setPersistent(false);
        display.addScoreboardTag(DISPLAY_TAG);
    }

    private void removeAllDisplays(World world) {
        for (UUID id : activeDisplays) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        activeDisplays.clear();
        removeTaggedDisplays(world);
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

    private String formatNumber(int value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(Math.max(0, value));
    }
}
