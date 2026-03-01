package krispasi.omGames.bedwars.stats;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Persistent BedWars player statistics stored in SQLite.
 */
public class BedwarsStatsService {
    private static final String TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS bedwars_stats (
              player_uuid TEXT PRIMARY KEY,
              wins INTEGER NOT NULL,
              kills INTEGER NOT NULL,
              deaths INTEGER NOT NULL,
              final_kills INTEGER NOT NULL,
              final_deaths INTEGER NOT NULL,
              games_played INTEGER NOT NULL,
              beds_broken INTEGER NOT NULL,
              parkour_best_time_ms INTEGER NOT NULL DEFAULT -1,
              parkour_best_checkpoint_uses INTEGER NOT NULL DEFAULT 0
            )
            """;
    private static final String SELECT_SQL = """
            SELECT wins,
                   kills,
                   deaths,
                   final_kills,
                   final_deaths,
                   games_played,
                   beds_broken,
                   parkour_best_time_ms,
                   parkour_best_checkpoint_uses
            FROM bedwars_stats
            WHERE player_uuid = ?
            """;
    private static final String UPSERT_SQL = """
            INSERT INTO bedwars_stats (
              player_uuid,
              wins,
              kills,
              deaths,
              final_kills,
              final_deaths,
              games_played,
              beds_broken,
              parkour_best_time_ms,
              parkour_best_checkpoint_uses
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(player_uuid)
            DO UPDATE SET
              wins = excluded.wins,
              kills = excluded.kills,
              deaths = excluded.deaths,
              final_kills = excluded.final_kills,
              final_deaths = excluded.final_deaths,
              games_played = excluded.games_played,
              beds_broken = excluded.beds_broken,
              parkour_best_time_ms = excluded.parkour_best_time_ms,
              parkour_best_checkpoint_uses = excluded.parkour_best_checkpoint_uses
            """;
    private static final List<String> MODIFIABLE_STAT_KEYS = List.of(
            "wins",
            "kills",
            "deaths",
            "final_kills",
            "final_deaths",
            "games_played",
            "beds_broken",
            "parkour_best_time_ms",
            "parkour_best_checkpoint_uses",
            "all"
    );

    private final File databaseFile;
    private final Logger logger;
    private Connection connection;
    private final Map<UUID, BedwarsPlayerStats> cache = new HashMap<>();

    public BedwarsStatsService(JavaPlugin plugin) {
        this.databaseFile = resolveDatabaseFile(plugin, "bedwars-stats.db");
        this.logger = plugin.getLogger();
    }

    public void load() {
        try {
            openConnection();
            createTable();
            ensureColumns();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to load BedWars stats.", ex);
        }
    }

    public void shutdown() {
        closeConnection();
        cache.clear();
    }

    public BedwarsPlayerStats getStats(UUID playerId) {
        if (playerId == null) {
            return new BedwarsPlayerStats();
        }
        return getOrLoadStats(playerId).copy();
    }

    public List<String> getModifiableStatKeys() {
        return MODIFIABLE_STAT_KEYS;
    }

    public void addKill(UUID playerId) {
        updateStats(playerId, stats -> stats.addKills(1));
    }

    public void addDeath(UUID playerId) {
        updateStats(playerId, stats -> stats.addDeaths(1));
    }

    public void addFinalKill(UUID playerId) {
        updateStats(playerId, stats -> stats.addFinalKills(1));
    }

    public void addFinalDeath(UUID playerId) {
        updateStats(playerId, stats -> stats.addFinalDeaths(1));
    }

    public void addWin(UUID playerId) {
        updateStats(playerId, stats -> stats.addWins(1));
    }

    public void addGamePlayed(UUID playerId) {
        updateStats(playerId, stats -> stats.addGamesPlayed(1));
    }

    public void addBedBroken(UUID playerId) {
        updateStats(playerId, stats -> stats.addBedsBroken(1));
    }

    public void recordParkourFinish(UUID playerId, long elapsedMillis, int checkpointUses) {
        updateStats(playerId, stats -> stats.applyParkourFinish(elapsedMillis, checkpointUses));
    }

    public List<TopStatEntry> getTopWins(int limit) {
        return getTopByColumn("wins", limit);
    }

    public List<TopStatEntry> getTopFinalKills(int limit) {
        return getTopByColumn("final_kills", limit);
    }

    public List<TopStatEntry> getTopBedsBroken(int limit) {
        return getTopByColumn("beds_broken", limit);
    }

    public List<TopParkourEntry> getTopParkourTimes(int limit) {
        if (connection == null || limit <= 0) {
            return List.of();
        }
        String sql = """
                SELECT player_uuid, parkour_best_time_ms, parkour_best_checkpoint_uses
                FROM bedwars_stats
                WHERE parkour_best_time_ms > 0
                ORDER BY parkour_best_time_ms ASC, parkour_best_checkpoint_uses ASC, player_uuid ASC
                LIMIT ?
                """;
        List<TopParkourEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    UUID playerId = parsePlayerId(rs.getString("player_uuid"));
                    if (playerId == null) {
                        continue;
                    }
                    long timeMillis = rs.getLong("parkour_best_time_ms");
                    int checkpointUses = rs.getInt("parkour_best_checkpoint_uses");
                    entries.add(new TopParkourEntry(playerId, Math.max(0L, timeMillis), Math.max(0, checkpointUses)));
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to load parkour top times.", ex);
        }
        return entries;
    }

    public List<StatChange> modifyStats(UUID playerId, String statKey, StatOperation operation, long amount) {
        if (playerId == null || statKey == null || operation == null) {
            return List.of();
        }
        Set<MutableStat> targets = resolveTargetStats(statKey);
        if (targets.isEmpty()) {
            return List.of();
        }
        long normalizedAmount = operation == StatOperation.SET ? amount : safeAbs(amount);
        List<StatChange> changes = new ArrayList<>();
        updateStats(playerId, stats -> {
            for (MutableStat target : targets) {
                long before = readStat(stats, target);
                long after = applyStatChange(stats, target, operation, normalizedAmount);
                changes.add(new StatChange(target.key(), before, after));
            }
        });
        return List.copyOf(changes);
    }

    private void updateStats(UUID playerId, Consumer<BedwarsPlayerStats> updater) {
        if (playerId == null) {
            return;
        }
        BedwarsPlayerStats stats = getOrLoadStats(playerId);
        updater.accept(stats);
        saveStats(playerId, stats);
    }

    private BedwarsPlayerStats getOrLoadStats(UUID playerId) {
        BedwarsPlayerStats cached = cache.get(playerId);
        if (cached != null) {
            return cached;
        }
        BedwarsPlayerStats stats = loadStats(playerId);
        cache.put(playerId, stats);
        return stats;
    }

    private BedwarsPlayerStats loadStats(UUID playerId) {
        if (connection == null) {
            logger.warning("Stats database is not available.");
            return new BedwarsPlayerStats();
        }
        try (PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    int wins = rs.getInt("wins");
                    int kills = rs.getInt("kills");
                    int deaths = rs.getInt("deaths");
                    int finalKills = rs.getInt("final_kills");
                    int finalDeaths = rs.getInt("final_deaths");
                    int gamesPlayed = rs.getInt("games_played");
                    int bedsBroken = rs.getInt("beds_broken");
                    long parkourBestTimeMs = rs.getLong("parkour_best_time_ms");
                    if (rs.wasNull()) {
                        parkourBestTimeMs = -1L;
                    }
                    int parkourBestCheckpointUses = rs.getInt("parkour_best_checkpoint_uses");
                    return new BedwarsPlayerStats(
                            wins,
                            kills,
                            deaths,
                            finalKills,
                            finalDeaths,
                            gamesPlayed,
                            bedsBroken,
                            parkourBestTimeMs,
                            parkourBestCheckpointUses
                    );
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to load stats for " + playerId, ex);
        }
        return new BedwarsPlayerStats();
    }

    private List<TopStatEntry> getTopByColumn(String column, int limit) {
        if (connection == null || limit <= 0) {
            return List.of();
        }
        String normalizedColumn = normalizeLeaderboardColumn(column);
        if (normalizedColumn == null) {
            return List.of();
        }
        String sql = "SELECT player_uuid, " + normalizedColumn + " AS value "
                + "FROM bedwars_stats "
                + "ORDER BY " + normalizedColumn + " DESC, player_uuid ASC "
                + "LIMIT ?";
        List<TopStatEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    UUID playerId = parsePlayerId(rs.getString("player_uuid"));
                    if (playerId == null) {
                        continue;
                    }
                    int value = rs.getInt("value");
                    entries.add(new TopStatEntry(playerId, value));
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to load top stats for column " + normalizedColumn, ex);
        }
        return entries;
    }

    private String normalizeLeaderboardColumn(String column) {
        if (column == null) {
            return null;
        }
        return switch (column.toLowerCase(Locale.ROOT)) {
            case "wins" -> "wins";
            case "final_kills", "finalkills", "fk" -> "final_kills";
            case "beds_broken", "bedsbroken", "beds" -> "beds_broken";
            default -> null;
        };
    }

    private UUID parsePlayerId(String rawUuid) {
        if (rawUuid == null || rawUuid.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(rawUuid);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Set<MutableStat> resolveTargetStats(String statKey) {
        String normalized = normalizeStatKey(statKey);
        if (normalized == null || normalized.isBlank()) {
            return Set.of();
        }
        if (normalized.equals("all")) {
            return EnumSet.allOf(MutableStat.class);
        }
        for (MutableStat stat : MutableStat.values()) {
            if (stat.matches(normalized)) {
                Set<MutableStat> single = new LinkedHashSet<>();
                single.add(stat);
                return single;
            }
        }
        return Set.of();
    }

    private String normalizeStatKey(String statKey) {
        if (statKey == null) {
            return null;
        }
        return statKey.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    private long readStat(BedwarsPlayerStats stats, MutableStat stat) {
        return switch (stat) {
            case WINS -> stats.getWins();
            case KILLS -> stats.getKills();
            case DEATHS -> stats.getDeaths();
            case FINAL_KILLS -> stats.getFinalKills();
            case FINAL_DEATHS -> stats.getFinalDeaths();
            case GAMES_PLAYED -> stats.getGamesPlayed();
            case BEDS_BROKEN -> stats.getBedsBroken();
            case PARKOUR_BEST_TIME_MS -> stats.getParkourBestTimeMillis();
            case PARKOUR_BEST_CHECKPOINT_USES -> stats.getParkourBestCheckpointUses();
        };
    }

    private long applyStatChange(BedwarsPlayerStats stats, MutableStat stat, StatOperation operation, long amount) {
        return switch (stat) {
            case WINS -> applyIntStat(stats::getWins, stats::setWins, stats::addWins, operation, amount);
            case KILLS -> applyIntStat(stats::getKills, stats::setKills, stats::addKills, operation, amount);
            case DEATHS -> applyIntStat(stats::getDeaths, stats::setDeaths, stats::addDeaths, operation, amount);
            case FINAL_KILLS -> applyIntStat(stats::getFinalKills, stats::setFinalKills, stats::addFinalKills, operation, amount);
            case FINAL_DEATHS -> applyIntStat(stats::getFinalDeaths, stats::setFinalDeaths, stats::addFinalDeaths, operation, amount);
            case GAMES_PLAYED -> applyIntStat(stats::getGamesPlayed, stats::setGamesPlayed, stats::addGamesPlayed, operation, amount);
            case BEDS_BROKEN -> applyIntStat(stats::getBedsBroken, stats::setBedsBroken, stats::addBedsBroken, operation, amount);
            case PARKOUR_BEST_TIME_MS -> applyParkourTimeStat(stats, operation, amount);
            case PARKOUR_BEST_CHECKPOINT_USES ->
                    applyIntStat(stats::getParkourBestCheckpointUses, stats::setParkourBestCheckpointUses, stats::addParkourBestCheckpointUses, operation, amount);
        };
    }

    private long applyParkourTimeStat(BedwarsPlayerStats stats, StatOperation operation, long amount) {
        if (operation == StatOperation.SET) {
            stats.setParkourBestTimeMillis(amount);
            return stats.getParkourBestTimeMillis();
        }
        long delta = operation == StatOperation.ADD ? amount : -amount;
        stats.addParkourBestTimeMillis(delta);
        return stats.getParkourBestTimeMillis();
    }

    private long applyIntStat(IntGetter getter,
                              IntSetter setter,
                              IntAdder adder,
                              StatOperation operation,
                              long amount) {
        if (operation == StatOperation.SET) {
            setter.set(toPositiveInt(amount));
            return getter.get();
        }
        int delta = toSignedInt(amount, operation == StatOperation.SUBTRACT);
        adder.add(delta);
        return getter.get();
    }

    private int toPositiveInt(long value) {
        if (value <= 0L) {
            return 0;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }

    private int toSignedInt(long magnitude, boolean negative) {
        long bounded = Math.min(safeAbs(magnitude), Integer.MAX_VALUE);
        int value = (int) bounded;
        return negative ? -value : value;
    }

    private long safeAbs(long value) {
        if (value == Long.MIN_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.abs(value);
    }

    private void saveStats(UUID playerId, BedwarsPlayerStats stats) {
        if (connection == null) {
            logger.warning("Stats database is not available.");
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, stats.getWins());
            statement.setInt(3, stats.getKills());
            statement.setInt(4, stats.getDeaths());
            statement.setInt(5, stats.getFinalKills());
            statement.setInt(6, stats.getFinalDeaths());
            statement.setInt(7, stats.getGamesPlayed());
            statement.setInt(8, stats.getBedsBroken());
            statement.setLong(9, stats.getParkourBestTimeMillis());
            statement.setInt(10, stats.getParkourBestCheckpointUses());
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to save stats for " + playerId, ex);
        }
    }

    private void openConnection() throws SQLException {
        if (connection != null) {
            return;
        }
        File parent = databaseFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            logger.log(Level.SEVERE, "SQLite driver not found.", ex);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
    }

    private static File resolveDatabaseFile(JavaPlugin plugin, String name) {
        File base = new File(plugin.getDataFolder(), "Bedwars");
        return new File(base, name);
    }

    private void createTable() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(TABLE_SQL);
        }
    }

    private void ensureColumns() throws SQLException {
        ensureColumn("deaths", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("final_kills", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("final_deaths", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn("parkour_best_time_ms", "INTEGER NOT NULL DEFAULT -1");
        ensureColumn("parkour_best_checkpoint_uses", "INTEGER NOT NULL DEFAULT 0");
    }

    private void ensureColumn(String name, String definition) throws SQLException {
        if (connection == null || name == null || name.isBlank() || definition == null || definition.isBlank()) {
            return;
        }
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(bedwars_stats)")) {
            while (rs.next()) {
                String column = rs.getString("name");
                if (name.equalsIgnoreCase(column)) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE bedwars_stats ADD COLUMN " + name + " " + definition);
        }
    }

    private void closeConnection() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to close stats database.", ex);
        }
        connection = null;
    }

    private enum MutableStat {
        WINS("wins", "win"),
        KILLS("kills", "kill"),
        DEATHS("deaths", "death"),
        FINAL_KILLS("final_kills", "finalkills", "fk"),
        FINAL_DEATHS("final_deaths", "finaldeaths", "fd"),
        GAMES_PLAYED("games_played", "gamesplayed", "games", "gp"),
        BEDS_BROKEN("beds_broken", "bedsbroken", "beds", "bed"),
        PARKOUR_BEST_TIME_MS("parkour_best_time_ms", "parkour_time", "parkour_best", "parkour"),
        PARKOUR_BEST_CHECKPOINT_USES("parkour_best_checkpoint_uses", "parkour_checkpoint_uses", "parkour_cps", "parkour_cp");

        private final String key;
        private final Set<String> aliases;

        MutableStat(String key, String... aliases) {
            this.key = key;
            Set<String> values = new LinkedHashSet<>();
            values.add(key);
            for (String alias : aliases) {
                if (alias == null || alias.isBlank()) {
                    continue;
                }
                values.add(alias.toLowerCase(Locale.ROOT));
            }
            this.aliases = Set.copyOf(values);
        }

        public String key() {
            return key;
        }

        public boolean matches(String candidate) {
            if (candidate == null) {
                return false;
            }
            return aliases.contains(candidate.toLowerCase(Locale.ROOT));
        }
    }

    @FunctionalInterface
    private interface IntGetter {
        int get();
    }

    @FunctionalInterface
    private interface IntSetter {
        void set(int value);
    }

    @FunctionalInterface
    private interface IntAdder {
        void add(int delta);
    }

    public enum StatOperation {
        ADD,
        SUBTRACT,
        SET
    }

    public record TopStatEntry(UUID playerId, int value) {
    }

    public record TopParkourEntry(UUID playerId, long timeMillis, int checkpointUses) {
    }

    public record StatChange(String statKey, long before, long after) {
    }
}
