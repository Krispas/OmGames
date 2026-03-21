package krispasi.omGames.bedwars.timecapsule;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import krispasi.omGames.storage.OmGamesDatabaseFiles;
import org.bukkit.plugin.java.JavaPlugin;

public class TimeCapsuleService {
    private static final String TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS time_capsules (
              capsule_id TEXT PRIMARY KEY,
              queue_type TEXT NOT NULL,
              created_by_player_uuid TEXT,
              created_at INTEGER NOT NULL,
              contents_base64 TEXT NOT NULL
            )
            """;
    private static final String SELECT_SQL = """
            SELECT capsule_id, created_by_player_uuid, contents_base64
            FROM time_capsules
            WHERE queue_type = ?
            """;
    private static final String INSERT_SQL = """
            INSERT INTO time_capsules (
              capsule_id,
              queue_type,
              created_by_player_uuid,
              created_at,
              contents_base64
            )
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String SELECT_BY_CREATOR_SQL = """
            SELECT queue_type, created_at, contents_base64
            FROM time_capsules
            WHERE created_by_player_uuid = ?
            ORDER BY created_at DESC
            """;
    private static final String DELETE_SQL = "DELETE FROM time_capsules WHERE capsule_id = ?";

    private final File databaseFile;
    private final Logger logger;
    private Connection connection;

    public TimeCapsuleService(JavaPlugin plugin) {
        this.databaseFile = OmGamesDatabaseFiles.getMainDatabaseFile(plugin.getDataFolder());
        this.logger = plugin.getLogger();
    }

    public void load() {
        try {
            openConnection();
            createTable();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to load time capsules.", ex);
        }
    }

    public void shutdown() {
        closeConnection();
    }

    public boolean saveCapsule(TimeCapsuleQueueType queueType, UUID creatorId, String contentsBase64) {
        if (queueType == null || contentsBase64 == null || contentsBase64.isBlank()) {
            return false;
        }
        if (connection == null) {
            logger.warning("Time capsule database is not available.");
            return false;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, queueType.key());
            statement.setString(3, creatorId != null ? creatorId.toString() : null);
            statement.setLong(4, System.currentTimeMillis());
            statement.setString(5, contentsBase64);
            statement.executeUpdate();
            return true;
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to save time capsule.", ex);
            return false;
        }
    }

    public List<ClaimedTimeCapsule> claimRandomCapsules(TimeCapsuleQueueType queueType, int amount) {
        if (queueType == null || amount <= 0) {
            return List.of();
        }
        if (connection == null) {
            logger.warning("Time capsule database is not available.");
            return List.of();
        }
        boolean previousAutoCommit = true;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            List<StoredTimeCapsule> storedCapsules = loadCapsules(queueType);
            if (storedCapsules.isEmpty()) {
                connection.commit();
                return List.of();
            }

            Collections.shuffle(storedCapsules);
            List<ClaimedTimeCapsule> claimedCapsules = new ArrayList<>(amount);
            Set<String> consumedIds = new LinkedHashSet<>();
            if (storedCapsules.size() >= amount) {
                for (int i = 0; i < amount; i++) {
                    StoredTimeCapsule capsule = storedCapsules.get(i);
                    claimedCapsules.add(new ClaimedTimeCapsule(capsule.contentsBase64(), capsule.creatorId()));
                    consumedIds.add(capsule.capsuleId());
                }
            } else {
                for (StoredTimeCapsule capsule : storedCapsules) {
                    claimedCapsules.add(new ClaimedTimeCapsule(capsule.contentsBase64(), capsule.creatorId()));
                    consumedIds.add(capsule.capsuleId());
                }
                while (claimedCapsules.size() < amount) {
                    StoredTimeCapsule capsule = storedCapsules.get(ThreadLocalRandom.current().nextInt(storedCapsules.size()));
                    claimedCapsules.add(new ClaimedTimeCapsule(capsule.contentsBase64(), capsule.creatorId()));
                }
            }
            deleteCapsules(consumedIds);
            connection.commit();
            return List.copyOf(claimedCapsules);
        } catch (SQLException ex) {
            rollbackQuietly();
            logger.log(Level.WARNING, "Failed to claim time capsules for queue " + queueType.key(), ex);
            return List.of();
        } finally {
            restoreAutoCommit(previousAutoCommit);
        }
    }

    public List<VisibleTimeCapsule> getCurrentCapsulesByCreator(UUID creatorId) {
        if (creatorId == null) {
            return List.of();
        }
        if (connection == null) {
            logger.warning("Time capsule database is not available.");
            return List.of();
        }
        List<VisibleTimeCapsule> capsules = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_BY_CREATOR_SQL)) {
            statement.setString(1, creatorId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    TimeCapsuleQueueType queueType = parseQueueType(resultSet.getString("queue_type"));
                    long createdAt = resultSet.getLong("created_at");
                    String contentsBase64 = resultSet.getString("contents_base64");
                    if (queueType == null || contentsBase64 == null || contentsBase64.isBlank()) {
                        continue;
                    }
                    capsules.add(new VisibleTimeCapsule(queueType, createdAt, contentsBase64));
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to load current time capsules for " + creatorId, ex);
            return List.of();
        }
        return List.copyOf(capsules);
    }

    private List<StoredTimeCapsule> loadCapsules(TimeCapsuleQueueType queueType) throws SQLException {
        List<StoredTimeCapsule> storedCapsules = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
            statement.setString(1, queueType.key());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String capsuleId = resultSet.getString("capsule_id");
                    UUID creatorId = parseUuid(resultSet.getString("created_by_player_uuid"));
                    String contentsBase64 = resultSet.getString("contents_base64");
                    if (capsuleId == null || capsuleId.isBlank() || contentsBase64 == null || contentsBase64.isBlank()) {
                        continue;
                    }
                    storedCapsules.add(new StoredTimeCapsule(capsuleId, creatorId, contentsBase64));
                }
            }
        }
        return storedCapsules;
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private TimeCapsuleQueueType parseQueueType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (TimeCapsuleQueueType type : TimeCapsuleQueueType.values()) {
            if (type.key().equalsIgnoreCase(raw)) {
                return type;
            }
        }
        return null;
    }

    private void deleteCapsules(Set<String> capsuleIds) throws SQLException {
        if (capsuleIds == null || capsuleIds.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
            for (String capsuleId : capsuleIds) {
                if (capsuleId == null || capsuleId.isBlank()) {
                    continue;
                }
                statement.setString(1, capsuleId);
                statement.addBatch();
            }
            statement.executeBatch();
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
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
        }
    }

    private void createTable() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(TABLE_SQL);
        }
    }

    private void rollbackQuietly() {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to roll back time capsule transaction.", ex);
        }
    }

    private void restoreAutoCommit(boolean autoCommit) {
        if (connection == null) {
            return;
        }
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to restore time capsule auto-commit mode.", ex);
        }
    }

    private void closeConnection() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to close time capsule database.", ex);
        }
        connection = null;
    }

    public record ClaimedTimeCapsule(String contentsBase64, UUID creatorId) {
    }

    public record VisibleTimeCapsule(TimeCapsuleQueueType queueType, long createdAt, String contentsBase64) {
    }

    private record StoredTimeCapsule(String capsuleId, UUID creatorId, String contentsBase64) {
    }
}
