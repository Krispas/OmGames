package krispasi.omGames.egghunt;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

public class EggHuntManager {
    private static final int DEFAULT_TIMER_SECONDS = 180;
    private static final double START_RADIUS = 50.0;
    private static final String SIDEBAR_OBJECTIVE_ID = "egghunt";

    public record Result(boolean success, String message) {
    }

    private enum SidebarState {
        READY,
        COUNTDOWN,
        RUNNING,
        FINISHED
    }

    private final JavaPlugin plugin;
    private final List<EggHuntPoint> savedPoints = new ArrayList<>();
    private final Map<UUID, Scoreboard> previousScoreboards = new HashMap<>();
    private final List<String> sidebarEntries = new ArrayList<>();
    private final LinkedHashMap<UUID, String> sidebarNames = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, Integer> sidebarScores = new LinkedHashMap<>();
    private int timerSeconds = DEFAULT_TIMER_SECONDS;
    private EggHuntSession activeSession;
    private Scoreboard sidebarScoreboard;
    private SidebarState sidebarState = SidebarState.READY;
    private int sidebarCountdownSeconds = 10;
    private int sidebarDisplaySeconds = DEFAULT_TIMER_SECONDS;
    private int sidebarEggsRemaining = 0;

    public EggHuntManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public void load() {
        savedPoints.clear();
        File configFile = getConfigFile();
        if (!configFile.exists()) {
            timerSeconds = DEFAULT_TIMER_SECONDS;
            sidebarDisplaySeconds = timerSeconds;
            sidebarEggsRemaining = 0;
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        timerSeconds = Math.max(1, config.getInt("timer-seconds", DEFAULT_TIMER_SECONDS));
        sidebarDisplaySeconds = timerSeconds;

        for (Map<?, ?> rawPoint : config.getMapList("points")) {
            String worldName = asString(rawPoint.get("world"));
            Double x = asDouble(rawPoint.get("x"));
            Double y = asDouble(rawPoint.get("y"));
            Double z = asDouble(rawPoint.get("z"));
            if (worldName == null || x == null || y == null || z == null) {
                continue;
            }
            savedPoints.add(new EggHuntPoint(worldName, x, y, z));
        }
        sidebarEggsRemaining = savedPoints.size();
        plugin.getLogger().info("Loaded " + savedPoints.size() + " Egg Hunt points.");
    }

    public Result addPoint(Player player) {
        if (player == null || player.getWorld() == null) {
            return new Result(false, "Only a player in a loaded world can add Egg Hunt points.");
        }
        EggHuntPoint point = EggHuntPoint.fromLocation(player.getLocation());
        if (savedPoints.contains(point)) {
            return new Result(false, "That Egg Hunt point is already saved.");
        }
        savedPoints.add(point);
        save();
        if (sidebarScoreboard != null && activeSession == null) {
            sidebarEggsRemaining = savedPoints.size();
            refreshSidebar();
        }
        return new Result(true, String.format(
                Locale.ROOT,
                "Saved Egg Hunt point at %s %.1f %.1f %.1f. Total points: %d.",
                point.worldName(),
                point.x(),
                point.y(),
                point.z(),
                savedPoints.size()
        ));
    }

    public Result prepareSidebar() {
        if (sidebarScoreboard != null) {
            clearSidebarInternal();
        }
        if (!initializeSidebar()) {
            return new Result(false, "Could not create the Egg Hunt scoreboard.");
        }
        resetPreparedSidebarState();
        refreshSidebar();
        return new Result(true, "Egg Hunt scoreboard prepared and shown on the sidebar.");
    }

    public Result setTimerSeconds(int seconds) {
        if (seconds <= 0) {
            return new Result(false, "Timer must be greater than 0 seconds.");
        }
        timerSeconds = seconds;
        save();
        if (activeSession == null && sidebarScoreboard != null) {
            sidebarDisplaySeconds = timerSeconds;
            refreshSidebar();
        }
        if (activeSession != null) {
            return new Result(true, "Egg Hunt timer set to " + seconds + " seconds for the next game.");
        }
        return new Result(true, "Egg Hunt timer set to " + seconds + " seconds.");
    }

    public Result start(Player initiator) {
        if (initiator == null || initiator.getWorld() == null) {
            return new Result(false, "Only a player in a loaded world can start Egg Hunt.");
        }

        List<EggHuntPoint> worldPoints = getPointsForWorld(initiator.getWorld());
        if (worldPoints.isEmpty()) {
            return new Result(false, "No Egg Hunt points are saved in this world.");
        }

        List<Player> participants = initiator.getWorld().getPlayers().stream()
                .filter(player -> player.getLocation().distanceSquared(initiator.getLocation()) <= START_RADIUS * START_RADIUS)
                .toList();
        if (participants.isEmpty()) {
            return new Result(false, "No players were found within 50 blocks.");
        }

        stopActiveSession();
        if (!initializeSidebar()) {
            return new Result(false, "Could not create the Egg Hunt scoreboard.");
        }

        sidebarNames.clear();
        sidebarScores.clear();
        sidebarEggsRemaining = worldPoints.size();
        sidebarDisplaySeconds = timerSeconds;
        activeSession = new EggHuntSession(
                this,
                initiator.getLocation().clone(),
                participants,
                worldPoints,
                timerSeconds
        );
        activeSession.start();
        refreshSidebar();

        return new Result(true, "Egg Hunt countdown started for " + participants.size()
                + " players with " + worldPoints.size() + " eggs.");
    }

    public Result clearSidebar() {
        if (sidebarScoreboard == null && activeSession == null) {
            return new Result(false, "Egg Hunt scoreboard is not active.");
        }
        clearSidebarInternal();
        return new Result(true, "Egg Hunt scoreboard cleared.");
    }

    public void syncSidebar(EggHuntSession session) {
        if (session == null || session != activeSession) {
            return;
        }
        sidebarNames.clear();
        sidebarNames.putAll(session.getParticipantNames());
        sidebarScores.clear();
        sidebarScores.putAll(session.getScores());
        sidebarEggsRemaining = session.getRemainingEggCount();

        switch (session.getState()) {
            case COUNTDOWN -> {
                sidebarState = SidebarState.COUNTDOWN;
                sidebarCountdownSeconds = session.getCountdownRemaining();
                sidebarDisplaySeconds = session.getConfiguredTimerSeconds();
            }
            case RUNNING -> {
                sidebarState = SidebarState.RUNNING;
                sidebarDisplaySeconds = Math.max(0, session.getSecondsRemaining());
            }
            case FINISHED -> sidebarState = SidebarState.FINISHED;
            case ABORTED -> sidebarState = SidebarState.READY;
        }
        refreshSidebar();
    }

    public void completeSession(EggHuntSession session) {
        if (session == null || session != activeSession) {
            return;
        }
        activeSession = null;
        sidebarState = SidebarState.FINISHED;
        sidebarNames.clear();
        sidebarNames.putAll(session.getParticipantNames());
        sidebarScores.clear();
        sidebarScores.putAll(session.getScores());
        sidebarEggsRemaining = 0;
        sidebarDisplaySeconds = Math.max(0, session.getSecondsRemaining());
        refreshSidebar();
    }

    public boolean isMovementLocked(Player player) {
        return player != null
                && activeSession != null
                && activeSession.isMovementLocked(player.getUniqueId());
    }

    public Location getLockedLocation(Player player) {
        if (!isMovementLocked(player)) {
            return null;
        }
        return activeSession.getLockedLocation();
    }

    public void handlePlayerJoin(Player player) {
        if (player == null) {
            return;
        }
        if (sidebarScoreboard != null) {
            applySidebarToPlayer(player);
        }
        if (activeSession != null) {
            activeSession.handlePlayerJoin(player);
        }
    }

    public void handlePlayerQuit(Player player) {
        if (player == null) {
            return;
        }
        previousScoreboards.remove(player.getUniqueId());
    }

    public void shutdown() {
        clearSidebarInternal();
    }

    private void stopActiveSession() {
        if (activeSession == null) {
            return;
        }
        EggHuntSession session = activeSession;
        activeSession = null;
        session.abort();
    }

    private void clearSidebarInternal() {
        stopActiveSession();
        restorePlayers();
        clearSidebarEntries();
        sidebarScoreboard = null;
        sidebarNames.clear();
        sidebarScores.clear();
        resetPreparedSidebarState();
    }

    private boolean initializeSidebar() {
        if (sidebarScoreboard != null) {
            return true;
        }
        if (Bukkit.getScoreboardManager() == null) {
            return false;
        }
        sidebarScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = sidebarScoreboard.registerNewObjective(
                SIDEBAR_OBJECTIVE_ID,
                "dummy",
                Component.text("EGG HUNT", NamedTextColor.GOLD)
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        return true;
    }

    private void resetPreparedSidebarState() {
        sidebarState = SidebarState.READY;
        sidebarCountdownSeconds = 10;
        sidebarDisplaySeconds = timerSeconds;
        sidebarEggsRemaining = savedPoints.size();
        sidebarNames.clear();
        sidebarScores.clear();
    }

    private void refreshSidebar() {
        if (sidebarScoreboard == null) {
            return;
        }
        Objective objective = sidebarScoreboard.getObjective(SIDEBAR_OBJECTIVE_ID);
        if (objective == null) {
            objective = sidebarScoreboard.registerNewObjective(
                    SIDEBAR_OBJECTIVE_ID,
                    "dummy",
                    Component.text("EGG HUNT", NamedTextColor.GOLD)
            );
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        clearSidebarEntries();
        List<String> visibleLines = buildSidebarLines();
        int scoreValue = visibleLines.size();
        for (int i = 0; i < visibleLines.size(); i++) {
            String entry = uniquifyLine(visibleLines.get(i), i);
            objective.getScore(entry).setScore(scoreValue--);
            sidebarEntries.add(entry);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            applySidebarToPlayer(player);
        }
    }

    private void clearSidebarEntries() {
        if (sidebarScoreboard == null) {
            sidebarEntries.clear();
            return;
        }
        for (String entry : new ArrayList<>(sidebarEntries)) {
            sidebarScoreboard.resetScores(entry);
        }
        sidebarEntries.clear();
    }

    private void applySidebarToPlayer(Player player) {
        if (player == null || sidebarScoreboard == null) {
            return;
        }
        if (player.getScoreboard() != sidebarScoreboard) {
            previousScoreboards.putIfAbsent(player.getUniqueId(), player.getScoreboard());
            player.setScoreboard(sidebarScoreboard);
        }
    }

    private void restorePlayers() {
        for (Map.Entry<UUID, Scoreboard> entry : new ArrayList<>(previousScoreboards.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            if (sidebarScoreboard != null && player.getScoreboard() == sidebarScoreboard) {
                player.setScoreboard(entry.getValue() != null
                        ? entry.getValue()
                        : Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        previousScoreboards.clear();
    }

    private List<String> buildSidebarLines() {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.YELLOW + "State: " + stateLabel());
        if (sidebarState == SidebarState.COUNTDOWN) {
            lines.add(ChatColor.GOLD + "Starts in: " + sidebarCountdownSeconds + "s");
        } else {
            lines.add(ChatColor.GOLD + "Timer: " + formatTime(sidebarDisplaySeconds));
        }
        lines.add(ChatColor.GREEN + "Eggs left: " + sidebarEggsRemaining);
        lines.add("");

        List<Map.Entry<UUID, Integer>> standings = new ArrayList<>(sidebarScores.entrySet());
        standings.sort(Comparator
                .comparingInt((Map.Entry<UUID, Integer> entry) -> entry.getValue()).reversed()
                .thenComparing(entry -> sidebarNames.getOrDefault(entry.getKey(), entry.getKey().toString()), String.CASE_INSENSITIVE_ORDER));

        if (standings.isEmpty()) {
            lines.add(ChatColor.GRAY + "No scores yet");
            return lines;
        }

        int maxPlayerLines = 10;
        int limit = Math.min(maxPlayerLines, standings.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<UUID, Integer> entry = standings.get(i);
            String name = sidebarNames.getOrDefault(entry.getKey(), "Unknown");
            lines.add(ChatColor.WHITE + name + ChatColor.GRAY + ": " + ChatColor.AQUA + entry.getValue());
        }
        if (standings.size() > maxPlayerLines) {
            lines.add(ChatColor.DARK_GRAY + "+" + (standings.size() - maxPlayerLines) + " more");
        }
        return lines;
    }

    private String uniquifyLine(String line, int index) {
        ChatColor[] colors = ChatColor.values();
        return line + colors[index % colors.length];
    }

    private String stateLabel() {
        return switch (sidebarState) {
            case READY -> "Ready";
            case COUNTDOWN -> "Countdown";
            case RUNNING -> "Running";
            case FINISHED -> "Finished";
        };
    }

    private String formatTime(int totalSeconds) {
        int safeSeconds = Math.max(0, totalSeconds);
        int minutes = safeSeconds / 60;
        int seconds = safeSeconds % 60;
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private List<EggHuntPoint> getPointsForWorld(World world) {
        if (world == null) {
            return List.of();
        }
        return savedPoints.stream()
                .filter(point -> point.worldName().equalsIgnoreCase(world.getName()))
                .toList();
    }

    private File getDataFolder() {
        return new File(plugin.getDataFolder(), "EggHunt");
    }

    private File getConfigFile() {
        return new File(getDataFolder(), "egghunt.yml");
    }

    private void save() {
        File folder = getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Failed to create Egg Hunt data folder.");
            return;
        }

        YamlConfiguration config = new YamlConfiguration();
        config.set("timer-seconds", timerSeconds);
        List<Map<String, Object>> serializedPoints = new ArrayList<>();
        for (EggHuntPoint point : savedPoints) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("world", point.worldName());
            row.put("x", point.x());
            row.put("y", point.y());
            row.put("z", point.z());
            serializedPoints.add(row);
        }
        config.set("points", serializedPoints);
        try {
            config.save(getConfigFile());
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save Egg Hunt points: " + ex.getMessage());
        }
    }

    private String asString(Object value) {
        return value instanceof String string && !string.isBlank() ? string.trim() : null;
    }

    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string) {
            try {
                return Double.parseDouble(string.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
