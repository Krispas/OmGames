package krispasi.omGames.bedwars.shop;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Persists quick-buy layout per player.
 * <p>Stores slot mappings in SQLite and tracks edit state during
 * {@link krispasi.omGames.bedwars.gui.ShopMenu} customization.</p>
 */
public class QuickBuyService {
    private static final String TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS quick_buy (
              player_uuid TEXT NOT NULL,
              slot INTEGER NOT NULL,
              item_id TEXT NOT NULL,
              PRIMARY KEY (player_uuid, slot)
            )
            """;
    private static final String SELECT_SQL = "SELECT player_uuid, slot, item_id FROM quick_buy";
    private static final String UPSERT_SQL = """
            INSERT INTO quick_buy (player_uuid, slot, item_id)
            VALUES (?, ?, ?)
            ON CONFLICT(player_uuid, slot)
            DO UPDATE SET item_id = excluded.item_id
            """;
    private static final String DELETE_SQL = "DELETE FROM quick_buy WHERE player_uuid = ? AND slot = ?";

    private final File databaseFile;
    private final Logger logger;
    private Connection connection;
    private final Map<UUID, Map<Integer, String>> quickBuy = new HashMap<>();
    private final Map<UUID, Boolean> editing = new HashMap<>();
    private final Map<UUID, Integer> pendingSlots = new HashMap<>();

    public QuickBuyService(JavaPlugin plugin) {
        this.databaseFile = new File(plugin.getDataFolder(), "quickbuy.db");
        this.logger = plugin.getLogger();
    }

    public void load() {
        try {
            openConnection();
            createTable();
            loadAll();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to load quick buy data.", ex);
        }
    }

    public void shutdown() {
        closeConnection();
        quickBuy.clear();
        editing.clear();
        pendingSlots.clear();
    }

    public Map<Integer, String> getQuickBuy(UUID playerId) {
        Map<Integer, String> map = quickBuy.get(playerId);
        if (map == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(map);
    }

    public void setQuickBuySlot(UUID playerId, int slot, String itemId) {
        if (playerId == null || itemId == null || itemId.isBlank()) {
            return;
        }
        quickBuy.computeIfAbsent(playerId, key -> new HashMap<>()).put(slot, itemId);
        if (connection == null) {
            logger.warning("Quick buy database is not available.");
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, slot);
            statement.setString(3, itemId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to save quick buy slot for " + playerId, ex);
        }
    }

    public void removeQuickBuySlot(UUID playerId, int slot) {
        if (playerId == null) {
            return;
        }
        Map<Integer, String> map = quickBuy.get(playerId);
        if (map != null) {
            map.remove(slot);
        }
        if (connection == null) {
            logger.warning("Quick buy database is not available.");
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, slot);
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to remove quick buy slot for " + playerId, ex);
        }
    }

    public boolean toggleEditing(UUID playerId) {
        boolean next = !editing.getOrDefault(playerId, false);
        editing.put(playerId, next);
        if (!next) {
            pendingSlots.remove(playerId);
        }
        return next;
    }

    public boolean isEditing(UUID playerId) {
        return editing.getOrDefault(playerId, false);
    }

    public Integer getPendingSlot(UUID playerId) {
        return pendingSlots.get(playerId);
    }

    public void setPendingSlot(UUID playerId, int slot) {
        pendingSlots.put(playerId, slot);
    }

    public void clearPendingSlot(UUID playerId) {
        pendingSlots.remove(playerId);
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

    private void loadAll() throws SQLException {
        quickBuy.clear();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(SELECT_SQL)) {
            while (resultSet.next()) {
                String rawId = resultSet.getString("player_uuid");
                String itemId = resultSet.getString("item_id");
                int slot = resultSet.getInt("slot");
                UUID playerId;
                try {
                    playerId = UUID.fromString(rawId);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                if (itemId == null || itemId.isBlank()) {
                    continue;
                }
                quickBuy.computeIfAbsent(playerId, key -> new HashMap<>()).put(slot, itemId);
            }
        }
    }

    private void closeConnection() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to close quick buy database.", ex);
        }
        connection = null;
    }
}
