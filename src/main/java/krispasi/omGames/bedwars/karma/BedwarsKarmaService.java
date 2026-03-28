package krispasi.omGames.bedwars.karma;

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
import krispasi.omGames.storage.OmGamesDatabaseFiles;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Persistent BedWars karma storage backed by SQLite.
 */
public class BedwarsKarmaService {
    private static final String TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS bedwars_karma (
              player_uuid TEXT PRIMARY KEY,
              karma INTEGER NOT NULL
            )
            """;
    private static final String SELECT_SQL = """
            SELECT karma
            FROM bedwars_karma
            WHERE player_uuid = ?
            """;
    private static final String UPSERT_SQL = """
            INSERT INTO bedwars_karma (
              player_uuid,
              karma
            )
            VALUES (?, ?)
            ON CONFLICT(player_uuid)
            DO UPDATE SET
              karma = excluded.karma
            """;

    private final File databaseFile;
    private final Logger logger;
    private final Map<UUID, Integer> cache = new HashMap<>();
    private Connection connection;

    public BedwarsKarmaService(JavaPlugin plugin) {
        this.databaseFile = OmGamesDatabaseFiles.getMainDatabaseFile(plugin.getDataFolder());
        this.logger = plugin.getLogger();
    }

    public void load() {
        try {
            openConnection();
            createTable();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to load BedWars karma.", ex);
        }
    }

    public void shutdown() {
        closeConnection();
        cache.clear();
    }

    public int getPermanentKarma(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        Integer cached = cache.get(playerId);
        if (cached != null) {
            return cached;
        }
        int loaded = loadKarma(playerId);
        cache.put(playerId, loaded);
        return loaded;
    }

    public KarmaChange addPermanentKarma(UUID playerId, int amount) {
        if (amount <= 0) {
            int current = getPermanentKarma(playerId);
            return new KarmaChange(current, current);
        }
        return changePermanentKarma(playerId, amount);
    }

    public KarmaChange spendPermanentKarma(UUID playerId, int amount) {
        if (amount <= 0) {
            int current = getPermanentKarma(playerId);
            return new KarmaChange(current, current);
        }
        return changePermanentKarma(playerId, -amount);
    }

    private KarmaChange changePermanentKarma(UUID playerId, int delta) {
        if (playerId == null) {
            return new KarmaChange(0, 0);
        }
        int before = getPermanentKarma(playerId);
        int after = clampToNonNegativeInt((long) before + delta);
        cache.put(playerId, after);
        saveKarma(playerId, after);
        return new KarmaChange(before, after);
    }

    private int loadKarma(UUID playerId) {
        if (connection == null) {
            logger.warning("Karma database is not available.");
            return 0;
        }
        try (PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Math.max(0, resultSet.getInt("karma"));
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to load karma for " + playerId, ex);
        }
        return 0;
    }

    private void saveKarma(UUID playerId, int karma) {
        if (connection == null) {
            logger.warning("Karma database is not available.");
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, Math.max(0, karma));
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to save karma for " + playerId, ex);
        }
    }

    private int clampToNonNegativeInt(long value) {
        if (value <= 0L) {
            return 0;
        }
        if (value >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }

    private void openConnection() throws SQLException {
        if (connection != null) {
            return;
        }
        File parent = databaseFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
        }
    }

    private void createTable() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(TABLE_SQL);
        }
    }

    private void closeConnection() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to close karma database.", ex);
        }
        connection = null;
    }

    public record KarmaChange(int before, int after) {
    }
}
