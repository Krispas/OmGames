package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import krispasi.omGames.storage.OmGamesDatabaseFiles;
import org.bukkit.plugin.java.JavaPlugin;

public final class HallsShameService {
    private static final String SHAME_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS hoc_shame (
              player_uuid TEXT PRIMARY KEY,
              shame INTEGER NOT NULL
            )
            """;
    private static final String HISTORY_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS hoc_completed_scenarios (
              scenario_id TEXT NOT NULL,
              player_uuid TEXT NOT NULL,
              completed_at INTEGER NOT NULL,
              final_shame INTEGER NOT NULL,
              PRIMARY KEY (scenario_id, player_uuid, completed_at)
            )
            """;

    private final File databaseFile;
    private final Logger logger;
    private Connection connection;

    public HallsShameService(JavaPlugin plugin) {
        this.databaseFile = OmGamesDatabaseFiles.getMainDatabaseFile(plugin.getDataFolder());
        this.logger = plugin.getLogger();
    }

    public void load() {
        try {
            openConnection();
            try (Statement statement = connection.createStatement()) {
                statement.execute(SHAME_TABLE_SQL);
                statement.execute(HISTORY_TABLE_SQL);
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to load Halls of Carnage shame database.", ex);
        }
    }

    public void shutdown() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to close Halls of Carnage shame database.", ex);
        }
        connection = null;
    }

    public int getShame(UUID playerId) {
        if (connection == null || playerId == null) {
            return 0;
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT shame FROM hoc_shame WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Math.max(0, rs.getInt("shame")) : 0;
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to load Halls shame for " + playerId + ".", ex);
            return 0;
        }
    }

    public int setShame(UUID playerId, int shame) {
        int value = Math.max(0, shame);
        if (connection == null || playerId == null) {
            return value;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO hoc_shame (player_uuid, shame)
                VALUES (?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET shame = excluded.shame
                """)) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, value);
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to save Halls shame for " + playerId + ".", ex);
        }
        return value;
    }

    public int addShame(UUID playerId, int delta) {
        return setShame(playerId, Math.max(0, getShame(playerId) + delta));
    }

    public List<ShameEntry> getLeaderboard(int limit) {
        if (connection == null || limit <= 0) {
            return List.of();
        }
        List<ShameEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT player_uuid, shame
                FROM hoc_shame
                ORDER BY shame ASC, player_uuid ASC
                LIMIT ?
                """)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    entries.add(new ShameEntry(UUID.fromString(rs.getString("player_uuid")), Math.max(0, rs.getInt("shame"))));
                }
            }
        } catch (IllegalArgumentException | SQLException ex) {
            logger.log(Level.WARNING, "Failed to load Halls shame leaderboard.", ex);
        }
        return entries;
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

    public record ShameEntry(UUID playerId, int shame) {
    }
}
