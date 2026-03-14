package krispasi.omGames.bedwars.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import krispasi.omGames.storage.OmGamesDatabaseFiles;

public final class BedwarsLegacyDatabaseMigrator {
    private static final String MIGRATION_KEY = "bedwars_legacy_dbs_to_omgames_v1";
    private static final String MIGRATIONS_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS omgames_migrations (
              migration_key TEXT PRIMARY KEY,
              applied_at TEXT NOT NULL
            )
            """;
    private static final String QUICK_BUY_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS quick_buy (
              player_uuid TEXT NOT NULL,
              slot INTEGER NOT NULL,
              item_id TEXT NOT NULL,
              PRIMARY KEY (player_uuid, slot)
            )
            """;
    private static final String STATS_TABLE_SQL = """
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
    private static final String QUICK_BUY_UPSERT_SQL = """
            INSERT INTO quick_buy (player_uuid, slot, item_id)
            VALUES (?, ?, ?)
            ON CONFLICT(player_uuid, slot)
            DO UPDATE SET item_id = excluded.item_id
            """;
    private static final String STATS_UPSERT_SQL = """
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

    private BedwarsLegacyDatabaseMigrator() {
    }

    public static void migrate(File pluginDataFolder, Logger logger) {
        if (pluginDataFolder == null || logger == null) {
            return;
        }

        File legacyFolder = new File(pluginDataFolder, "Bedwars");
        File legacyQuickBuy = new File(legacyFolder, "quickbuy.db");
        File legacyStats = new File(legacyFolder, "bedwars-stats.db");
        if (!legacyQuickBuy.isFile() && !legacyStats.isFile()) {
            return;
        }

        if (!loadDriver(logger)) {
            return;
        }

        File unifiedDatabase = OmGamesDatabaseFiles.getMainDatabaseFile(pluginDataFolder);
        try (Connection destination = openConnection(unifiedDatabase)) {
            initializeDestination(destination);
            if (isMigrationApplied(destination)) {
                return;
            }
            if (destinationHasBedwarsData(destination)) {
                markMigrationApplied(destination);
                logger.info("Unified OmGames.db already contains BedWars data. Skipping legacy BedWars DB migration.");
                return;
            }

            boolean previousAutoCommit = destination.getAutoCommit();
            destination.setAutoCommit(false);
            try {
                int quickBuyRows = legacyQuickBuy.isFile() ? migrateQuickBuy(destination, legacyQuickBuy, logger) : 0;
                int statsRows = legacyStats.isFile() ? migrateStats(destination, legacyStats, logger) : 0;
                markMigrationApplied(destination);
                destination.commit();
                logger.info("Migrated legacy BedWars DBs into OmGames.db. quick_buy rows: "
                        + quickBuyRows + ", bedwars_stats rows: " + statsRows + ".");
            } catch (SQLException ex) {
                destination.rollback();
                throw ex;
            } finally {
                destination.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to migrate legacy BedWars databases into OmGames.db.", ex);
        }
    }

    private static boolean loadDriver(Logger logger) {
        try {
            Class.forName("org.sqlite.JDBC");
            return true;
        } catch (ClassNotFoundException ex) {
            logger.log(Level.SEVERE, "SQLite driver not found.", ex);
            return false;
        }
    }

    private static Connection openConnection(File databaseFile) throws SQLException {
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    private static void initializeDestination(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(MIGRATIONS_TABLE_SQL);
            statement.execute(QUICK_BUY_TABLE_SQL);
            statement.execute(STATS_TABLE_SQL);
        }
        ensureStatsColumn(connection, "deaths", "INTEGER NOT NULL DEFAULT 0");
        ensureStatsColumn(connection, "final_kills", "INTEGER NOT NULL DEFAULT 0");
        ensureStatsColumn(connection, "final_deaths", "INTEGER NOT NULL DEFAULT 0");
        ensureStatsColumn(connection, "parkour_best_time_ms", "INTEGER NOT NULL DEFAULT -1");
        ensureStatsColumn(connection, "parkour_best_checkpoint_uses", "INTEGER NOT NULL DEFAULT 0");
    }

    private static void ensureStatsColumn(Connection connection, String column, String definition) throws SQLException {
        Set<String> existing = getTableColumns(connection, "bedwars_stats");
        if (existing.contains(column.toLowerCase(Locale.ROOT))) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE bedwars_stats ADD COLUMN " + column + " " + definition);
        }
    }

    private static boolean isMigrationApplied(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM omgames_migrations WHERE migration_key = ?")) {
            statement.setString(1, MIGRATION_KEY);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void markMigrationApplied(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO omgames_migrations (migration_key, applied_at)
                VALUES (?, ?)
                ON CONFLICT(migration_key)
                DO UPDATE SET applied_at = excluded.applied_at
                """)) {
            statement.setString(1, MIGRATION_KEY);
            statement.setString(2, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    private static boolean destinationHasBedwarsData(Connection connection) throws SQLException {
        return tableHasRows(connection, "quick_buy") || tableHasRows(connection, "bedwars_stats");
    }

    private static boolean tableHasRows(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT 1 FROM " + tableName + " LIMIT 1")) {
            return rs.next();
        }
    }

    private static int migrateQuickBuy(Connection destination, File legacyFile, Logger logger) throws SQLException {
        try (Connection source = openConnection(legacyFile)) {
            if (!hasTable(source, "quick_buy")) {
                logger.info("Legacy quickbuy.db exists but has no quick_buy table. Skipping quick buy migration.");
                return 0;
            }
            int migrated = 0;
            try (Statement select = source.createStatement();
                 ResultSet rs = select.executeQuery("SELECT player_uuid, slot, item_id FROM quick_buy");
                 PreparedStatement insert = destination.prepareStatement(QUICK_BUY_UPSERT_SQL)) {
                while (rs.next()) {
                    String rawPlayerId = rs.getString("player_uuid");
                    String itemId = rs.getString("item_id");
                    if (!isValidUuid(rawPlayerId) || itemId == null || itemId.isBlank()) {
                        continue;
                    }
                    insert.setString(1, rawPlayerId);
                    insert.setInt(2, rs.getInt("slot"));
                    insert.setString(3, itemId);
                    insert.addBatch();
                    migrated++;
                }
                insert.executeBatch();
            }
            return migrated;
        }
    }

    private static int migrateStats(Connection destination, File legacyFile, Logger logger) throws SQLException {
        try (Connection source = openConnection(legacyFile)) {
            if (!hasTable(source, "bedwars_stats")) {
                logger.info("Legacy bedwars-stats.db exists but has no bedwars_stats table. Skipping stats migration.");
                return 0;
            }
            Set<String> columns = getTableColumns(source, "bedwars_stats");
            if (!columns.contains("player_uuid")) {
                logger.warning("Legacy bedwars_stats table has no player_uuid column. Skipping stats migration.");
                return 0;
            }

            int migrated = 0;
            try (Statement select = source.createStatement();
                 ResultSet rs = select.executeQuery("SELECT * FROM bedwars_stats");
                 PreparedStatement insert = destination.prepareStatement(STATS_UPSERT_SQL)) {
                while (rs.next()) {
                    String rawPlayerId = rs.getString("player_uuid");
                    if (!isValidUuid(rawPlayerId)) {
                        continue;
                    }
                    insert.setString(1, rawPlayerId);
                    insert.setInt(2, getInt(rs, columns, "wins", 0));
                    insert.setInt(3, getInt(rs, columns, "kills", 0));
                    insert.setInt(4, getInt(rs, columns, "deaths", 0));
                    insert.setInt(5, getInt(rs, columns, "final_kills", 0));
                    insert.setInt(6, getInt(rs, columns, "final_deaths", 0));
                    insert.setInt(7, getInt(rs, columns, "games_played", 0));
                    insert.setInt(8, getInt(rs, columns, "beds_broken", 0));
                    insert.setLong(9, getLong(rs, columns, "parkour_best_time_ms", -1L));
                    insert.setInt(10, getInt(rs, columns, "parkour_best_checkpoint_uses", 0));
                    insert.addBatch();
                    migrated++;
                }
                insert.executeBatch();
            }
            return migrated;
        }
    }

    private static boolean hasTable(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM sqlite_master
                WHERE type = 'table' AND name = ?
                """)) {
            statement.setString(1, tableName);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static Set<String> getTableColumns(Connection connection, String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (name != null && !name.isBlank()) {
                    columns.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
        return columns;
    }

    private static int getInt(ResultSet rs, Set<String> columns, String column, int defaultValue) throws SQLException {
        if (!columns.contains(column.toLowerCase(Locale.ROOT))) {
            return defaultValue;
        }
        int value = rs.getInt(column);
        return rs.wasNull() ? defaultValue : value;
    }

    private static long getLong(ResultSet rs, Set<String> columns, String column, long defaultValue) throws SQLException {
        if (!columns.contains(column.toLowerCase(Locale.ROOT))) {
            return defaultValue;
        }
        long value = rs.getLong(column);
        return rs.wasNull() ? defaultValue : value;
    }

    private static boolean isValidUuid(String rawPlayerId) {
        if (rawPlayerId == null || rawPlayerId.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(rawPlayerId);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
