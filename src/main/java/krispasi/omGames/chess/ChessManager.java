package krispasi.omGames.chess;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChessManager {
    private final JavaPlugin plugin;
    private final ChessDatabaseService databaseService;
    private final org.bukkit.NamespacedKey timestampKey;
    private final ChessMatchRuntime setupRuntime;
    private final Map<String, ChessMatchRuntime> activeMatches = new LinkedHashMap<>();

    public ChessManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.databaseService = new ChessDatabaseService(plugin);
        this.timestampKey = new org.bukkit.NamespacedKey(plugin, "chess_timestamp");
        this.setupRuntime = new ChessMatchRuntime(plugin);
    }

    public void load() {
        databaseService.load();
        setupRuntime.openStorage();
        for (ChessDatabaseService.ActiveMatchState state : databaseService.loadActiveMatchStates()) {
            ChessMatchRuntime runtime = new ChessMatchRuntime(plugin);
            runtime.openStorage();
            runtime.restoreActiveMatchState(state);
            if (runtime.isMatchActive()) {
                activeMatches.put(runtime.activeMatchStartedAt(), runtime);
            } else {
                runtime.shutdown();
            }
        }
    }

    public void shutdown() {
        for (ChessMatchRuntime runtime : new ArrayList<>(activeMatches.values())) {
            runtime.shutdown();
        }
        setupRuntime.shutdown();
        databaseService.shutdown();
    }

    public Result buildBoard(org.bukkit.command.CommandSender sender, int x, int y, int z) {
        return setupRuntime.buildBoard(sender, x, y, z);
    }

    public Result resetBoard() {
        return setupRuntime.resetBoard();
    }

    public Result setPalette(Material lightBlock, Material darkBlock, Material highlightBlock) {
        setupRuntime.setPalette(lightBlock, darkBlock, highlightBlock);
        for (ChessMatchRuntime runtime : activeMatches.values()) {
            runtime.setPalette(lightBlock, darkBlock, highlightBlock);
        }
        return Result.ok("Chess board palette set to " + lightBlock.getKey() + ", " + darkBlock.getKey()
                + ", " + highlightBlock.getKey() + ".");
    }

    public Result resetPalette() {
        setupRuntime.resetPalette();
        for (ChessMatchRuntime runtime : activeMatches.values()) {
            runtime.resetPalette();
        }
        return Result.ok("Chess board palette reset to defaults.");
    }

    public Result removeBoard(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Result.fail("Usage: /chess board remove <timestamp|*>");
        }
        if (timestamp.equals("*")) {
            for (ChessMatchRuntime runtime : new ArrayList<>(activeMatches.values())) {
                runtime.cancelMatch("*");
            }
            activeMatches.clear();
        } else {
            ChessMatchRuntime runtime = activeMatchByBoard(timestamp);
            if (runtime != null) {
                runtime.cancelMatch(timestamp);
                activeMatches.remove(runtime.activeMatchStartedAt());
            }
        }
        return setupRuntime.removeBoard(timestamp);
    }

    public List<String> getBoardTimestamps() {
        return databaseService.getBoards().stream()
                .map(ChessDatabaseService.BoardRef::timestamp)
                .toList();
    }

    public List<String> getActiveMatchTimestamps() {
        purgeInactiveMatches();
        return activeMatches.keySet().stream().toList();
    }

    public Result setTeam(ChessSide side, List<Player> players) {
        return setupRuntime.setTeam(side, players);
    }

    public Result startMatch() {
        return startMatch(null);
    }

    public Result startMatch(String boardTimestamp) {
        ChessMatchRuntime runtime = new ChessMatchRuntime(plugin);
        runtime.openStorage();
        copyPendingSetupTo(runtime);
        Result result = runtime.startMatch(boardTimestamp);
        if (!result.success()) {
            runtime.shutdown();
            return result;
        }
        activeMatches.put(runtime.activeMatchStartedAt(), runtime);
        return Result.ok(result.message() + " Timestamp: " + runtime.activeMatchStartedAt() + ".");
    }

    public Result enableTestMode() {
        ChessMatchRuntime runtime = mostRecentActiveMatch();
        if (runtime != null) {
            return runtime.enableTestMode();
        }
        return setupRuntime.enableTestMode();
    }

    public Result setSetting(String settingKey, boolean value) {
        if (activeMatches.isEmpty()) {
            return setupRuntime.setSetting(settingKey, value);
        }
        ChessMatchRuntime runtime = mostRecentActiveMatch();
        return runtime == null ? setupRuntime.setSetting(settingKey, value) : runtime.setSetting(settingKey, value);
    }

    public Result setFigureStyle(String style) {
        if (activeMatches.isEmpty()) {
            return setupRuntime.setFigureStyle(style);
        }
        ChessMatchRuntime runtime = mostRecentActiveMatch();
        return runtime == null ? setupRuntime.setFigureStyle(style) : runtime.setFigureStyle(style);
    }

    public Result setTimer(ChessTimerConfig timerConfig) {
        ChessMatchRuntime runtime = mostRecentActiveMatch();
        if (runtime != null) {
            return runtime.setTimer(timerConfig);
        }
        return setupRuntime.setTimer(timerConfig);
    }

    public Result printLog(org.bukkit.command.CommandSender sender, String timestamp) {
        if (timestamp != null && timestamp.equals("*")) {
            int printed = 0;
            for (String storedTimestamp : databaseService.getMatchLogTimestamps()) {
                ChessDatabaseService.RecentMatchLog storedLog = databaseService.getMatchLog(storedTimestamp);
                if (storedLog != null) {
                    printLog(sender, storedLog);
                    printed++;
                }
            }
            return printed == 0 ? Result.fail("No chess match logs are stored.")
                    : Result.ok("Printed " + printed + " chess match log" + (printed == 1 ? "" : "s") + ".");
        }
        ChessDatabaseService.RecentMatchLog log = timestamp == null || timestamp.isBlank()
                ? databaseService.getMostRecentMatchLog()
                : databaseService.getMatchLog(timestamp);
        if (log == null) {
            return Result.fail("No chess match log matched.");
        }
        printLog(sender, log);
        return Result.ok("Printed chess match log.");
    }

    private void printLog(org.bukkit.command.CommandSender sender, ChessDatabaseService.RecentMatchLog log) {
        sender.sendMessage(Component.text(log.header(), NamedTextColor.GOLD));
        for (String setting : log.settings()) {
            sender.sendMessage(Component.text(setting, NamedTextColor.YELLOW));
        }
        for (String event : log.events()) {
            sender.sendMessage(Component.text(event, NamedTextColor.WHITE));
        }
        if (log.result() != null && !log.result().isBlank()) {
            sender.sendMessage(Component.text(log.result(), NamedTextColor.GREEN));
        }
    }

    public Result deleteLog(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Result.fail("Usage: /chess log delete <timestamp|*>");
        }
        int deleted = databaseService.deleteMatchLogs(timestamp);
        return deleted == 0 ? Result.fail("No chess match log matched " + timestamp + ".")
                : Result.ok("Deleted " + deleted + " chess match log" + (deleted == 1 ? "" : "s") + ".");
    }

    public Result searchLogs(org.bukkit.command.CommandSender sender, List<String> playerNames) {
        if (playerNames == null || playerNames.isEmpty()) {
            return Result.fail("Usage: /chess log search <player> [player...]");
        }
        List<String> timestamps = databaseService.searchMatchLogsByPlayers(playerNames);
        for (String timestamp : timestamps) {
            sender.sendMessage(Component.text(timestamp, NamedTextColor.WHITE));
        }
        return Result.ok("Found " + timestamps.size() + " chess match log" + (timestamps.size() == 1 ? "" : "s") + ".");
    }

    public Result cancelMatch(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Result.fail("Usage: /chess match cancel <timestamp|*>");
        }
        int active = 0;
        if (timestamp.equals("*")) {
            for (ChessMatchRuntime runtime : new ArrayList<>(activeMatches.values())) {
                runtime.cancelMatch("*");
                active++;
            }
            activeMatches.clear();
        } else {
            ChessMatchRuntime runtime = activeMatches.get(timestamp);
            if (runtime == null) {
                runtime = activeMatchByBoard(timestamp);
            }
            if (runtime != null) {
                runtime.cancelMatch(timestamp);
                activeMatches.remove(runtime.activeMatchStartedAt());
                active = 1;
            }
        }
        int stored = databaseService.cancelUnfinishedMatches(timestamp, java.time.format.DateTimeFormatter
                .ofPattern("yyyy.dd.MM-HH.mm.ss", Locale.ROOT)
                .format(java.time.LocalDateTime.now()));
        if (active == 0 && stored == 0) {
            return Result.fail("No active or unfinished chess match matched " + timestamp + ".");
        }
        return Result.ok("Cancelled " + active + " active match" + (active == 1 ? "" : "es")
                + " and " + stored + " stored log entr" + (stored == 1 ? "y" : "ies") + ".");
    }

    public Result resign(Player player) {
        ChessMatchRuntime runtime = activeMatchForPlayer(player);
        return runtime == null ? Result.fail("No chess match is active for you.") : runtime.resign(player);
    }

    public Result voteDraw(Player player) {
        ChessMatchRuntime runtime = activeMatchForPlayer(player);
        return runtime == null ? Result.fail("No chess match is active for you.") : runtime.voteDraw(player);
    }

    public Result undo(Player player, boolean operator) {
        ChessMatchRuntime runtime = activeMatchForPlayer(player);
        return runtime == null ? Result.fail("No chess match is active for you.") : runtime.undo(player, operator);
    }

    public Result redo(Player player, boolean operator) {
        ChessMatchRuntime runtime = activeMatchForPlayer(player);
        return runtime == null ? Result.fail("No chess match is active for you.") : runtime.redo(player, operator);
    }

    public Result rewind(Player player) {
        ChessMatchRuntime runtime = activeMatchForPlayer(player);
        return runtime == null ? Result.fail("No chess match is active for you.") : runtime.rewind(player);
    }

    public Result forward(Player player) {
        ChessMatchRuntime runtime = activeMatchForPlayer(player);
        return runtime == null ? Result.fail("No chess match is active for you.") : runtime.forward(player);
    }

    public Result checkmate(Player player) {
        ChessMatchRuntime runtime = activeMatchForPlayer(player);
        return runtime == null ? Result.fail("No chess match is active for you.") : runtime.checkmate(player);
    }

    public boolean handleEntityInteraction(Player player, Entity entity) {
        ChessMatchRuntime runtime = activeMatchForEntity(entity);
        return runtime != null && runtime.handleEntityInteraction(player, entity);
    }

    public boolean handleAirRightClick(Player player) {
        ChessMatchRuntime runtime = activeMatchForPlayer(player);
        return runtime != null && runtime.handleAirRightClick(player);
    }

    public boolean hasPendingPromotion(Player player) {
        ChessMatchRuntime runtime = activeMatchForPlayer(player);
        return runtime != null && runtime.hasPendingPromotion(player);
    }

    public boolean handlePromotionChat(Player player, String message) {
        ChessMatchRuntime runtime = activeMatchForPlayer(player);
        return runtime != null && runtime.handlePromotionChat(player, message);
    }

    public void handleJoin(Player player) {
        for (ChessMatchRuntime runtime : activeMatches.values()) {
            runtime.handleJoin(player);
        }
    }

    public void handleQuit(Player player) {
        for (ChessMatchRuntime runtime : activeMatches.values()) {
            runtime.handleQuit(player);
        }
    }

    public void handleWorldChange(Player player) {
        for (ChessMatchRuntime runtime : activeMatches.values()) {
            runtime.handleWorldChange(player);
        }
    }

    public boolean handlePromotionInventoryClick(Player player, org.bukkit.inventory.Inventory inventory, int slot) {
        ChessMatchRuntime runtime = activeMatchForPlayer(player);
        return runtime != null && runtime.handlePromotionInventoryClick(player, inventory, slot);
    }

    public boolean handlePromotionInventoryClose(Player player, org.bukkit.inventory.Inventory inventory) {
        ChessMatchRuntime runtime = activeMatchForPlayer(player);
        return runtime != null && runtime.handlePromotionInventoryClose(player, inventory);
    }

    private ChessMatchRuntime activeMatchForEntity(Entity entity) {
        purgeInactiveMatches();
        if (entity == null) {
            return null;
        }
        String timestamp = entity.getPersistentDataContainer().get(timestampKey, PersistentDataType.STRING);
        for (ChessMatchRuntime runtime : activeMatches.values()) {
            if (runtime.hasEntityTimestamp(timestamp)) {
                return runtime;
            }
        }
        return null;
    }

    private ChessMatchRuntime activeMatchForPlayer(Player player) {
        purgeInactiveMatches();
        if (player == null) {
            return null;
        }
        UUID playerId = player.getUniqueId();
        for (ChessMatchRuntime runtime : activeMatches.values()) {
            if (runtime.hasPlayer(playerId)) {
                return runtime;
            }
        }
        return mostRecentActiveMatch();
    }

    private ChessMatchRuntime activeMatchByBoard(String boardTimestamp) {
        purgeInactiveMatches();
        for (ChessMatchRuntime runtime : activeMatches.values()) {
            if (runtime.hasEntityTimestamp(boardTimestamp)) {
                return runtime;
            }
        }
        return null;
    }

    private ChessMatchRuntime mostRecentActiveMatch() {
        purgeInactiveMatches();
        ChessMatchRuntime last = null;
        for (ChessMatchRuntime runtime : activeMatches.values()) {
            last = runtime;
        }
        return last;
    }

    private void purgeInactiveMatches() {
        activeMatches.entrySet().removeIf(entry -> !entry.getValue().isMatchActive());
    }

    private void copyPendingSetupTo(ChessMatchRuntime runtime) {
        // Pending setup still lives in the runtime implementation. This is intentionally narrow;
        // commands continue to use /chess match white|black and /chess match settings before start.
        runtime.importSetupFrom(setupRuntime);
    }

    public record Result(boolean success, String message) {
        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }

    public record ChessTimerConfig(boolean enabled, long initialMillis, long checkBonusMillis) {
        public static ChessTimerConfig off() {
            return new ChessTimerConfig(false, 0L, 0L);
        }
    }
}
