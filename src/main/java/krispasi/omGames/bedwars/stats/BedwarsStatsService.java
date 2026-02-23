package krispasi.omGames.bedwars.stats;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
              beds_broken INTEGER NOT NULL
            )
            """;
    private static final String SELECT_SQL = """
            SELECT wins, kills, deaths, final_kills, final_deaths, games_played, beds_broken
            FROM bedwars_stats
            WHERE player_uuid = ?
            """;
    private static final String UPSERT_SQL = """
            INSERT INTO bedwars_stats (player_uuid, wins, kills, deaths, final_kills, final_deaths, games_played, beds_broken)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(player_uuid)
            DO UPDATE SET
              wins = excluded.wins,
              kills = excluded.kills,
              deaths = excluded.deaths,
              final_kills = excluded.final_kills,
              final_deaths = excluded.final_deaths,
              games_played = excluded.games_played,
              beds_broken = excluded.beds_broken
            """;

    private final File databaseFile;
    private final Logger logger;
    private Connection connection;
    private final Map<UUID, BedwarsPlayerStats> cache = new HashMap<>();

    public BedwarsStatsService(JavaPlugin plugin) {
        this.databaseFile = new File(plugin.getDataFolder(), "bedwars-stats.db");
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

    private void updateStats(UUID playerId, java.util.function.Consumer<BedwarsPlayerStats> updater) {
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
                    return new BedwarsPlayerStats(
                            wins,
                            kills,
                            deaths,
                            finalKills,
                            finalDeaths,
                            gamesPlayed,
                            bedsBroken
                    );
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to load stats for " + playerId, ex);
        }
        return new BedwarsPlayerStats();
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

    private void createTable() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(TABLE_SQL);
        }
    }

    private void ensureColumns() throws SQLException {
        ensureColumn("deaths");
        ensureColumn("final_kills");
        ensureColumn("final_deaths");
    }

    private void ensureColumn(String name) throws SQLException {
        if (connection == null || name == null || name.isBlank()) {
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
            statement.execute("ALTER TABLE bedwars_stats ADD COLUMN " + name + " INTEGER NOT NULL DEFAULT 0");
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
}
