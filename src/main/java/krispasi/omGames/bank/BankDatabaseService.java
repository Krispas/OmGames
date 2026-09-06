package krispasi.omGames.bank;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import krispasi.omGames.storage.OmGamesDatabaseFiles;
import org.bukkit.plugin.java.JavaPlugin;

public final class BankDatabaseService {
    private static final String ACCOUNTS_SQL = """
            CREATE TABLE IF NOT EXISTS bank_accounts (
              player_uuid TEXT PRIMARY KEY,
              player_name TEXT NOT NULL,
              balance INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL
            )
            """;
    private static final String ACCOUNT_PROFILES_SQL = """
            CREATE TABLE IF NOT EXISTS bank_account_profiles (
              account_id TEXT PRIMARY KEY,
              account_type TEXT NOT NULL,
              display_name TEXT NOT NULL,
              player_uuid TEXT,
              balance INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL
            )
            """;
    private static final String ACCOUNT_EDITORS_SQL = """
            CREATE TABLE IF NOT EXISTS bank_account_editors (
              account_id TEXT NOT NULL,
              player_uuid TEXT NOT NULL,
              player_name TEXT NOT NULL,
              PRIMARY KEY(account_id, player_uuid),
              FOREIGN KEY(account_id) REFERENCES bank_account_profiles(account_id)
            )
            """;
    private static final String CARDS_SQL = """
            CREATE TABLE IF NOT EXISTS bank_cards (
              card_id TEXT PRIMARY KEY,
              owner_account_id TEXT,
              owner_uuid TEXT NOT NULL,
              owner_name TEXT NOT NULL,
              frozen INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL,
              FOREIGN KEY(owner_uuid) REFERENCES bank_accounts(player_uuid)
            )
            """;
    private static final String TERMINALS_SQL = """
            CREATE TABLE IF NOT EXISTS bank_terminals (
              terminal_id TEXT PRIMARY KEY,
              owner_account_id TEXT,
              owner_uuid TEXT NOT NULL,
              owner_name TEXT NOT NULL,
              name TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              FOREIGN KEY(owner_uuid) REFERENCES bank_accounts(player_uuid)
            )
            """;
    private static final String TERMINAL_ITEMS_SQL = """
            CREATE TABLE IF NOT EXISTS bank_terminal_items (
              item_id TEXT PRIMARY KEY,
              terminal_id TEXT NOT NULL,
              display_name TEXT NOT NULL,
              price INTEGER NOT NULL,
              sort_order INTEGER NOT NULL DEFAULT 0,
              FOREIGN KEY(terminal_id) REFERENCES bank_terminals(terminal_id)
            )
            """;
    private static final String CART_LINES_SQL = """
            CREATE TABLE IF NOT EXISTS bank_cart_lines (
              player_uuid TEXT NOT NULL,
              terminal_id TEXT NOT NULL,
              item_id TEXT NOT NULL,
              amount INTEGER NOT NULL,
              PRIMARY KEY(player_uuid, terminal_id, item_id),
              FOREIGN KEY(terminal_id) REFERENCES bank_terminals(terminal_id),
              FOREIGN KEY(item_id) REFERENCES bank_terminal_items(item_id)
            )
            """;
    private static final String STOCKS_SQL = """
            CREATE TABLE IF NOT EXISTS bank_stocks (
              player_uuid TEXT NOT NULL,
              stock_id TEXT NOT NULL,
              amount INTEGER NOT NULL,
              PRIMARY KEY(player_uuid, stock_id)
            )
            """;

    private final File databaseFile;
    private final Logger logger;
    private Connection connection;

    public BankDatabaseService(JavaPlugin plugin) {
        this.databaseFile = OmGamesDatabaseFiles.getMainDatabaseFile(plugin.getDataFolder());
        this.logger = plugin.getLogger();
    }

    public void load() {
        try {
            openConnection();
            try (Statement statement = connection.createStatement()) {
                statement.execute(ACCOUNTS_SQL);
                statement.execute(ACCOUNT_PROFILES_SQL);
                statement.execute(ACCOUNT_EDITORS_SQL);
                statement.execute(CARDS_SQL);
                statement.execute(TERMINALS_SQL);
                statement.execute(TERMINAL_ITEMS_SQL);
                statement.execute(CART_LINES_SQL);
                statement.execute(STOCKS_SQL);
            }
            ensureColumn("bank_cards", "owner_account_id", "TEXT");
            ensureColumn("bank_terminals", "owner_account_id", "TEXT");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to load Bank database tables.", ex);
        }
    }

    public void shutdown() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to close Bank database.", ex);
        }
        connection = null;
    }

    public BankAccount createPlayerAccount(UUID playerId, String playerName, long createdAt) {
        if (connection == null || playerId == null || playerName == null || playerName.isBlank()) {
            return null;
        }
        String accountId = playerAccountId(playerId);
        String profileSql = """
                INSERT INTO bank_account_profiles (account_id, account_type, display_name, player_uuid, balance, created_at)
                VALUES (?, 'player', ?, ?, 0, ?)
                ON CONFLICT(account_id) DO UPDATE SET display_name = excluded.display_name, player_uuid = excluded.player_uuid
                """;
        String sql = """
                INSERT INTO bank_accounts (player_uuid, player_name, balance, created_at)
                VALUES (?, ?, 0, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET player_name = excluded.player_name
                """;
        try (PreparedStatement profile = connection.prepareStatement(profileSql);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            profile.setString(1, accountId);
            profile.setString(2, playerName);
            profile.setString(3, playerId.toString());
            profile.setLong(4, createdAt);
            profile.executeUpdate();
            statement.setString(1, playerId.toString());
            statement.setString(2, playerName);
            statement.setLong(3, createdAt);
            statement.executeUpdate();
            return getAccount(accountId);
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to create Bank account for " + playerName + ".", ex);
            return null;
        }
    }

    public BankAccount createNonPlayerAccount(String accountId, String displayName, long createdAt) {
        if (connection == null || accountId == null || accountId.isBlank() || displayName == null || displayName.isBlank()) {
            return null;
        }
        String sql = """
                INSERT INTO bank_account_profiles (account_id, account_type, display_name, player_uuid, balance, created_at)
                VALUES (?, 'non_player', ?, NULL, 0, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accountId);
            statement.setString(2, displayName);
            statement.setLong(3, createdAt);
            statement.executeUpdate();
            return getAccount(accountId);
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to create non-player Bank account " + displayName + ".", ex);
            return null;
        }
    }

    public BankAccount getAccount(String accountId) {
        if (connection == null || accountId == null || accountId.isBlank()) {
            return null;
        }
        String sql = "SELECT * FROM bank_account_profiles WHERE account_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return parseAccount(resultSet);
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to load Bank account " + accountId + ".", ex);
        }
        return null;
    }

    public BankAccount getPlayerAccount(UUID playerId) {
        return getAccount(playerAccountId(playerId));
    }

    public List<BankAccount> listAccounts() {
        List<BankAccount> accounts = new ArrayList<>();
        if (connection == null) {
            return accounts;
        }
        String sql = "SELECT * FROM bank_account_profiles ORDER BY lower(display_name), created_at";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                accounts.add(parseAccount(resultSet));
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to list Bank accounts.", ex);
        }
        return accounts;
    }

    public boolean deposit(String accountId, long amount) {
        if (connection == null || accountId == null || accountId.isBlank() || amount <= 0L) {
            return false;
        }
        String sql = "UPDATE bank_account_profiles SET balance = balance + ? WHERE account_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, amount);
            statement.setString(2, accountId);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to deposit Bank credits for " + accountId + ".", ex);
            return false;
        }
    }

    public Map<String, Long> getStocks(UUID playerId) {
        Map<String, Long> stocks = new LinkedHashMap<>();
        if (connection == null || playerId == null) {
            return stocks;
        }
        String sql = "SELECT stock_id, amount FROM bank_stocks WHERE player_uuid = ? ORDER BY lower(stock_id)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    stocks.put(resultSet.getString("stock_id"), resultSet.getLong("amount"));
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to load Bank stocks for " + playerId + ".", ex);
        }
        return stocks;
    }

    public List<BankAccountEditor> listEditors(String accountId) {
        List<BankAccountEditor> editors = new ArrayList<>();
        if (connection == null || accountId == null || accountId.isBlank()) {
            return editors;
        }
        String sql = "SELECT * FROM bank_account_editors WHERE account_id = ? ORDER BY lower(player_name)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    editors.add(new BankAccountEditor(
                            resultSet.getString("account_id"),
                            UUID.fromString(resultSet.getString("player_uuid")),
                            resultSet.getString("player_name")
                    ));
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to list Bank account editors for " + accountId + ".", ex);
        }
        return editors;
    }

    public boolean isEditor(String accountId, UUID playerId) {
        if (connection == null || accountId == null || accountId.isBlank() || playerId == null) {
            return false;
        }
        String sql = "SELECT 1 FROM bank_account_editors WHERE account_id = ? AND player_uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accountId);
            statement.setString(2, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to check Bank account editor for " + accountId + ".", ex);
            return false;
        }
    }

    public boolean addEditor(String accountId, UUID playerId, String playerName) {
        if (connection == null || accountId == null || accountId.isBlank() || playerId == null || playerName == null || playerName.isBlank()) {
            return false;
        }
        String sql = """
                INSERT INTO bank_account_editors (account_id, player_uuid, player_name)
                VALUES (?, ?, ?)
                ON CONFLICT(account_id, player_uuid) DO UPDATE SET player_name = excluded.player_name
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accountId);
            statement.setString(2, playerId.toString());
            statement.setString(3, playerName);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to add Bank account editor for " + accountId + ".", ex);
            return false;
        }
    }

    public boolean removeEditor(String accountId, UUID playerId) {
        if (connection == null || accountId == null || accountId.isBlank() || playerId == null) {
            return false;
        }
        String sql = "DELETE FROM bank_account_editors WHERE account_id = ? AND player_uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accountId);
            statement.setString(2, playerId.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to remove Bank account editor for " + accountId + ".", ex);
            return false;
        }
    }

    public BankCard createCard(String accountId, UUID ownerId, String ownerName, String cardId, long createdAt) {
        if (connection == null || accountId == null || accountId.isBlank() || cardId == null || ownerName == null) {
            return null;
        }
        String sql = "INSERT INTO bank_cards (card_id, owner_account_id, owner_uuid, owner_name, frozen, created_at) VALUES (?, ?, ?, ?, 0, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, cardId);
            statement.setString(2, accountId);
            statement.setString(3, ownerId == null ? "" : ownerId.toString());
            statement.setString(4, ownerName);
            statement.setLong(5, createdAt);
            statement.executeUpdate();
            return getCard(cardId);
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to create Bank card for " + ownerName + ".", ex);
            return null;
        }
    }

    public BankCard getCard(String cardId) {
        if (connection == null || cardId == null || cardId.isBlank()) {
            return null;
        }
        String sql = "SELECT * FROM bank_cards WHERE card_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, cardId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return parseCard(resultSet);
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to load Bank card " + cardId + ".", ex);
        }
        return null;
    }

    public List<BankCard> listCards(String accountId) {
        List<BankCard> cards = new ArrayList<>();
        if (connection == null || accountId == null || accountId.isBlank()) {
            return cards;
        }
        String sql = "SELECT * FROM bank_cards WHERE owner_account_id = ? ORDER BY created_at DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    cards.add(parseCard(resultSet));
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to list Bank cards for " + accountId + ".", ex);
        }
        return cards;
    }

    public boolean setCardFrozen(String cardId, boolean frozen) {
        if (connection == null || cardId == null || cardId.isBlank()) {
            return false;
        }
        try (PreparedStatement statement = connection.prepareStatement("UPDATE bank_cards SET frozen = ? WHERE card_id = ?")) {
            statement.setInt(1, frozen ? 1 : 0);
            statement.setString(2, cardId);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to update Bank card " + cardId + ".", ex);
            return false;
        }
    }

    public BankTerminal createTerminal(String accountId, UUID ownerId, String ownerName, String terminalId, String name, long createdAt) {
        if (connection == null || accountId == null || accountId.isBlank() || terminalId == null || ownerName == null || name == null) {
            return null;
        }
        String sql = "INSERT INTO bank_terminals (terminal_id, owner_account_id, owner_uuid, owner_name, name, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, terminalId);
            statement.setString(2, accountId);
            statement.setString(3, ownerId == null ? "" : ownerId.toString());
            statement.setString(4, ownerName);
            statement.setString(5, name);
            statement.setLong(6, createdAt);
            statement.executeUpdate();
            return getTerminal(terminalId);
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to create Bank terminal for " + ownerName + ".", ex);
            return null;
        }
    }

    public BankTerminal getTerminal(String terminalId) {
        if (connection == null || terminalId == null || terminalId.isBlank()) {
            return null;
        }
        String sql = "SELECT * FROM bank_terminals WHERE terminal_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, terminalId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return parseTerminal(resultSet);
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to load Bank terminal " + terminalId + ".", ex);
        }
        return null;
    }

    public List<BankTerminal> listTerminals(String accountId) {
        List<BankTerminal> terminals = new ArrayList<>();
        if (connection == null || accountId == null || accountId.isBlank()) {
            return terminals;
        }
        String sql = "SELECT * FROM bank_terminals WHERE owner_account_id = ? ORDER BY created_at DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    terminals.add(parseTerminal(resultSet));
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to list Bank terminals for " + accountId + ".", ex);
        }
        return terminals;
    }

    public List<BankTerminalItem> listTerminalItems(String terminalId) {
        List<BankTerminalItem> items = new ArrayList<>();
        if (connection == null || terminalId == null || terminalId.isBlank()) {
            return items;
        }
        String sql = "SELECT * FROM bank_terminal_items WHERE terminal_id = ? ORDER BY sort_order, lower(display_name)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, terminalId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    items.add(parseTerminalItem(resultSet));
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to list Bank terminal items for " + terminalId + ".", ex);
        }
        return items;
    }

    public boolean deleteTerminal(String terminalId) {
        if (connection == null || terminalId == null || terminalId.isBlank()) {
            return false;
        }
        boolean previousAutoCommit = true;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement deleteCart = connection.prepareStatement("DELETE FROM bank_cart_lines WHERE terminal_id = ?");
                 PreparedStatement deleteItems = connection.prepareStatement("DELETE FROM bank_terminal_items WHERE terminal_id = ?");
                 PreparedStatement deleteTerminal = connection.prepareStatement("DELETE FROM bank_terminals WHERE terminal_id = ?")) {
                deleteCart.setString(1, terminalId);
                deleteCart.executeUpdate();
                deleteItems.setString(1, terminalId);
                deleteItems.executeUpdate();
                deleteTerminal.setString(1, terminalId);
                int deleted = deleteTerminal.executeUpdate();
                connection.commit();
                return deleted > 0;
            }
        } catch (SQLException ex) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                logger.log(Level.WARNING, "Failed to roll back Bank terminal deletion.", rollbackEx);
            }
            logger.log(Level.WARNING, "Failed to delete Bank terminal " + terminalId + ".", ex);
            return false;
        } finally {
            try {
                connection.setAutoCommit(previousAutoCommit);
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Failed to restore Bank database autocommit.", ex);
            }
        }
    }

    public List<BankCartLine> listCart(UUID playerId, String terminalId) {
        List<BankCartLine> lines = new ArrayList<>();
        if (connection == null || playerId == null || terminalId == null || terminalId.isBlank()) {
            return lines;
        }
        String sql = """
                SELECT c.player_uuid, c.terminal_id, c.item_id, i.display_name, i.price, c.amount
                FROM bank_cart_lines c
                JOIN bank_terminal_items i ON i.item_id = c.item_id
                WHERE c.player_uuid = ? AND c.terminal_id = ?
                ORDER BY i.sort_order, lower(i.display_name)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, terminalId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    lines.add(new BankCartLine(
                            UUID.fromString(resultSet.getString("player_uuid")),
                            resultSet.getString("terminal_id"),
                            resultSet.getString("item_id"),
                            resultSet.getString("display_name"),
                            resultSet.getLong("price"),
                            resultSet.getInt("amount")
                    ));
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to list Bank cart for " + playerId + ".", ex);
        }
        return lines;
    }

    private BankAccount parseAccount(ResultSet resultSet) throws SQLException {
        String playerUuid = resultSet.getString("player_uuid");
        return new BankAccount(
                resultSet.getString("account_id"),
                playerUuid == null || playerUuid.isBlank() ? null : UUID.fromString(playerUuid),
                resultSet.getString("display_name"),
                "player".equalsIgnoreCase(resultSet.getString("account_type")),
                resultSet.getLong("balance"),
                resultSet.getLong("created_at")
        );
    }

    private BankCard parseCard(ResultSet resultSet) throws SQLException {
        return new BankCard(
                resultSet.getString("card_id"),
                fallbackAccountId(resultSet.getString("owner_account_id"), resultSet.getString("owner_uuid")),
                parseUuid(resultSet.getString("owner_uuid")),
                resultSet.getString("owner_name"),
                resultSet.getInt("frozen") != 0,
                resultSet.getLong("created_at")
        );
    }

    private BankTerminal parseTerminal(ResultSet resultSet) throws SQLException {
        return new BankTerminal(
                resultSet.getString("terminal_id"),
                fallbackAccountId(resultSet.getString("owner_account_id"), resultSet.getString("owner_uuid")),
                parseUuid(resultSet.getString("owner_uuid")),
                resultSet.getString("owner_name"),
                resultSet.getString("name"),
                resultSet.getLong("created_at")
        );
    }

    private BankTerminalItem parseTerminalItem(ResultSet resultSet) throws SQLException {
        return new BankTerminalItem(
                resultSet.getString("item_id"),
                resultSet.getString("terminal_id"),
                resultSet.getString("display_name"),
                resultSet.getLong("price"),
                resultSet.getInt("sort_order")
        );
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

    private void ensureColumn(String tableName, String columnName, String definition) throws SQLException {
        if (hasColumn(tableName, columnName)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }

    private boolean hasColumn(String tableName, String columnName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + tableName + ")");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String playerAccountId(UUID playerId) {
        return playerId == null ? null : "player:" + playerId;
    }

    private String fallbackAccountId(String accountId, String ownerUuid) {
        if (accountId != null && !accountId.isBlank()) {
            return accountId;
        }
        UUID uuid = parseUuid(ownerUuid);
        return playerAccountId(uuid);
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
