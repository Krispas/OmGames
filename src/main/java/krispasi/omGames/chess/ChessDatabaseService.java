package krispasi.omGames.chess;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import krispasi.omGames.storage.OmGamesDatabaseFiles;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChessDatabaseService {
    private static final String MATCHES_SQL = """
            CREATE TABLE IF NOT EXISTS chess_matches (
              match_id INTEGER PRIMARY KEY AUTOINCREMENT,
              started_at TEXT NOT NULL,
              board_timestamp TEXT NOT NULL,
              world_name TEXT NOT NULL,
              origin_x INTEGER NOT NULL,
              origin_y INTEGER NOT NULL,
              origin_z INTEGER NOT NULL,
              white_players TEXT NOT NULL,
              black_players TEXT NOT NULL,
              test_mode INTEGER NOT NULL,
              allow_undo INTEGER NOT NULL,
              do_movement_check INTEGER NOT NULL,
              visualize_movement_check INTEGER NOT NULL,
              do_endgame_checks INTEGER NOT NULL,
              show_annotation INTEGER NOT NULL DEFAULT 0,
              settings_logged INTEGER NOT NULL DEFAULT 0,
              finished_at TEXT,
              result TEXT,
              winner TEXT
            )
            """;
    private static final String EVENTS_SQL = """
            CREATE TABLE IF NOT EXISTS chess_match_events (
              event_id INTEGER PRIMARY KEY AUTOINCREMENT,
              match_id INTEGER NOT NULL,
              event_time TEXT NOT NULL,
              player_uuid TEXT,
              player_name TEXT,
              side TEXT,
              piece_name TEXT,
              from_square TEXT,
              to_square TEXT,
              captured_piece_name TEXT,
              legal INTEGER,
              is_check INTEGER,
              event_type TEXT NOT NULL,
              detail TEXT,
              FOREIGN KEY(match_id) REFERENCES chess_matches(match_id)
            )
            """;
    private static final String STATS_SQL = """
            CREATE TABLE IF NOT EXISTS chess_player_stats (
              player_uuid TEXT PRIMARY KEY,
              games_played INTEGER NOT NULL DEFAULT 0,
              wins INTEGER NOT NULL DEFAULT 0,
              losses INTEGER NOT NULL DEFAULT 0,
              draws INTEGER NOT NULL DEFAULT 0,
              white_games INTEGER NOT NULL DEFAULT 0,
              black_games INTEGER NOT NULL DEFAULT 0,
              resigns INTEGER NOT NULL DEFAULT 0,
              undos INTEGER NOT NULL DEFAULT 0,
              redos INTEGER NOT NULL DEFAULT 0
            )
            """;
    private static final String BOARDS_SQL = """
            CREATE TABLE IF NOT EXISTS chess_boards (
              board_timestamp TEXT PRIMARY KEY,
              world_name TEXT NOT NULL,
              origin_x INTEGER NOT NULL,
              origin_y INTEGER NOT NULL,
              origin_z INTEGER NOT NULL
            )
            """;

    private final File databaseFile;
    private final Logger logger;
    private Connection connection;

    public record PlayerRef(UUID uuid, String name) {
    }

    public record BoardRef(String timestamp, String worldName, int originX, int originY, int originZ) {
    }

    public record RecentMatchLog(String header, List<String> settings, List<String> events, String result) {
    }

    public ChessDatabaseService(JavaPlugin plugin) {
        this.databaseFile = OmGamesDatabaseFiles.getMainDatabaseFile(plugin.getDataFolder());
        this.logger = plugin.getLogger();
    }

    public void load() {
        try {
            openConnection();
            try (Statement statement = connection.createStatement()) {
                statement.execute(MATCHES_SQL);
                statement.execute(EVENTS_SQL);
                statement.execute(STATS_SQL);
                statement.execute(BOARDS_SQL);
            }
            ensureMatchColumn("show_annotation", "INTEGER NOT NULL DEFAULT 0");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to load Chess database tables.", ex);
        }
    }

    public void shutdown() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to close Chess database.", ex);
        }
        connection = null;
    }

    public long startMatch(ChessManager.BoardContext board,
                           Collection<PlayerRef> whitePlayers,
                           Collection<PlayerRef> blackPlayers,
                           ChessSettings settings,
                           boolean testMode,
                           String startedAt) {
        if (connection == null || board == null || testMode) {
            return -1L;
        }
        String sql = """
                INSERT INTO chess_matches (
                  started_at,
                  board_timestamp,
                  world_name,
                  origin_x,
                  origin_y,
                  origin_z,
                  white_players,
                  black_players,
                  test_mode,
                  allow_undo,
                  do_movement_check,
                  visualize_movement_check,
                  do_endgame_checks,
                  show_annotation,
                  settings_logged
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, startedAt);
            statement.setString(2, board.timestamp());
            statement.setString(3, board.worldName());
            statement.setInt(4, board.originX());
            statement.setInt(5, board.originY());
            statement.setInt(6, board.originZ());
            statement.setString(7, joinPlayers(whitePlayers));
            statement.setString(8, joinPlayers(blackPlayers));
            statement.setInt(9, 0);
            statement.setInt(10, settings.allowUndo() ? 1 : 0);
            statement.setInt(11, settings.doMovementCheck() ? 1 : 0);
            statement.setInt(12, settings.visualizeMovementCheck() ? 1 : 0);
            statement.setInt(13, settings.doEndgameChecks() ? 1 : 0);
            statement.setInt(14, settings.showAnnotation() ? 1 : 0);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to start Chess match log.", ex);
        }
        return -1L;
    }

    public void markSettingsLogged(long matchId, ChessSettings settings) {
        if (connection == null || matchId <= 0L) {
            return;
        }
        String sql = """
                UPDATE chess_matches
                SET allow_undo = ?,
                    do_movement_check = ?,
                    visualize_movement_check = ?,
                    do_endgame_checks = ?,
                    show_annotation = ?,
                    settings_logged = 1
                WHERE match_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, settings.allowUndo() ? 1 : 0);
            statement.setInt(2, settings.doMovementCheck() ? 1 : 0);
            statement.setInt(3, settings.visualizeMovementCheck() ? 1 : 0);
            statement.setInt(4, settings.doEndgameChecks() ? 1 : 0);
            statement.setInt(5, settings.showAnnotation() ? 1 : 0);
            statement.setLong(6, matchId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to mark Chess settings logged.", ex);
        }
    }

    public void saveBoard(ChessManager.BoardContext board) {
        if (connection == null || board == null) {
            return;
        }
        String sql = """
                INSERT OR REPLACE INTO chess_boards (
                  board_timestamp,
                  world_name,
                  origin_x,
                  origin_y,
                  origin_z
                ) VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, board.timestamp());
            statement.setString(2, board.worldName());
            statement.setInt(3, board.originX());
            statement.setInt(4, board.originY());
            statement.setInt(5, board.originZ());
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to save Chess board metadata.", ex);
        }
    }

    public List<BoardRef> getBoards() {
        List<BoardRef> boards = new ArrayList<>();
        if (connection == null) {
            return boards;
        }
        String sql = "SELECT board_timestamp, world_name, origin_x, origin_y, origin_z FROM chess_boards ORDER BY board_timestamp";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                boards.add(new BoardRef(
                        resultSet.getString("board_timestamp"),
                        resultSet.getString("world_name"),
                        resultSet.getInt("origin_x"),
                        resultSet.getInt("origin_y"),
                        resultSet.getInt("origin_z")
                ));
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to list Chess boards.", ex);
        }
        return boards;
    }

    public BoardRef getBoard(String timestamp) {
        if (connection == null || timestamp == null || timestamp.isBlank()) {
            return null;
        }
        String sql = "SELECT board_timestamp, world_name, origin_x, origin_y, origin_z FROM chess_boards WHERE board_timestamp = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, timestamp);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new BoardRef(
                            resultSet.getString("board_timestamp"),
                            resultSet.getString("world_name"),
                            resultSet.getInt("origin_x"),
                            resultSet.getInt("origin_y"),
                            resultSet.getInt("origin_z")
                    );
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to load Chess board " + timestamp + ".", ex);
        }
        return null;
    }

    public void deleteBoard(String timestamp) {
        if (connection == null || timestamp == null || timestamp.isBlank()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM chess_boards WHERE board_timestamp = ?")) {
            statement.setString(1, timestamp);
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to delete Chess board metadata.", ex);
        }
    }

    public void deleteAllBoards() {
        if (connection == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM chess_boards")) {
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to delete all Chess board metadata.", ex);
        }
    }

    public RecentMatchLog getMostRecentMatchLog() {
        if (connection == null) {
            return null;
        }
        String matchSql = """
                SELECT match_id,
                       started_at,
                       white_players,
                       black_players,
                       allow_undo,
                       do_movement_check,
                       visualize_movement_check,
                       do_endgame_checks,
                       show_annotation,
                       result,
                       winner
                FROM chess_matches
                ORDER BY match_id DESC
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(matchSql);
             ResultSet match = statement.executeQuery()) {
            if (!match.next()) {
                return null;
            }
            long matchId = match.getLong("match_id");
            String header = match.getString("started_at")
                    + " white= " + match.getString("white_players")
                    + " black= " + match.getString("black_players");
            List<String> settings = List.of(
                    "allow_undo = " + asBooleanText(match.getInt("allow_undo")),
                    "do_movement_check = " + asBooleanText(match.getInt("do_movement_check")),
                    "do_endgame_checks = " + asBooleanText(match.getInt("do_endgame_checks")),
                    "visualize_movement_check = " + asBooleanText(match.getInt("visualize_movement_check")),
                    "show_annotation = " + asBooleanText(match.getInt("show_annotation"))
            );
            List<String> events = getMatchEventLines(matchId);
            String result = match.getString("result");
            String winner = match.getString("winner");
            if (result != null && winner != null && !winner.isBlank()) {
                result += " (" + winner + ")";
            }
            return new RecentMatchLog(header, settings, events, result);
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to read recent Chess match log.", ex);
            return null;
        }
    }

    public void logMove(long matchId, ChessMoveRecord record) {
        if (connection == null || matchId <= 0L || record == null) {
            return;
        }
        String sql = """
                INSERT INTO chess_match_events (
                  match_id,
                  event_time,
                  player_uuid,
                  player_name,
                  side,
                  piece_name,
                  from_square,
                  to_square,
                  captured_piece_name,
                  legal,
                  is_check,
                  event_type,
                  detail
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, matchId);
            statement.setString(2, record.timestamp());
            statement.setString(3, record.actorId() == null ? null : record.actorId().toString());
            statement.setString(4, record.actorName());
            statement.setString(5, record.side() == null ? null : record.side().key());
            statement.setString(6, record.pieceType() == null || record.side() == null ? null : record.pieceName());
            statement.setString(7, record.from() == null ? null : record.from().notation());
            statement.setString(8, record.to() == null ? null : record.to().notation());
            statement.setString(9, record.capturedPieceName());
            statement.setInt(10, record.legal() ? 1 : 0);
            statement.setInt(11, record.check() ? 1 : 0);
            statement.setString(12, record.eventLabel());
            statement.setString(13, buildDetail(record));
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to log Chess match event.", ex);
        }
    }

    public void deleteMatch(long matchId) {
        if (connection == null || matchId <= 0L) {
            return;
        }
        try (PreparedStatement deleteEvents = connection.prepareStatement("DELETE FROM chess_match_events WHERE match_id = ?");
             PreparedStatement deleteMatch = connection.prepareStatement("DELETE FROM chess_matches WHERE match_id = ?")) {
            deleteEvents.setLong(1, matchId);
            deleteEvents.executeUpdate();
            deleteMatch.setLong(1, matchId);
            deleteMatch.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to delete Chess test match log.", ex);
        }
    }

    public void finishMatch(long matchId,
                            Collection<PlayerRef> whitePlayers,
                            Collection<PlayerRef> blackPlayers,
                            ChessSide winner,
                            String result,
                            String finishedAt,
                            boolean testMode,
                            boolean whiteResigned,
                            boolean blackResigned,
                            int undoCount,
                            int redoCount) {
        if (connection == null || matchId <= 0L || testMode) {
            return;
        }
        String sql = "UPDATE chess_matches SET finished_at = ?, result = ?, winner = ? WHERE match_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, finishedAt);
            statement.setString(2, result);
            statement.setString(3, winner == null ? null : winner.key());
            statement.setLong(4, matchId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to finalize Chess match.", ex);
        }
        updateStats(whitePlayers, true, winner == ChessSide.WHITE, winner == ChessSide.BLACK, winner == null, whiteResigned, undoCount, redoCount);
        updateStats(blackPlayers, false, winner == ChessSide.BLACK, winner == ChessSide.WHITE, winner == null, blackResigned, undoCount, redoCount);
    }

    public void abortMatch(long matchId, String result, String finishedAt) {
        if (connection == null || matchId <= 0L) {
            return;
        }
        String sql = "UPDATE chess_matches SET finished_at = ?, result = ? WHERE match_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, finishedAt);
            statement.setString(2, result);
            statement.setLong(3, matchId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to abort Chess match.", ex);
        }
    }

    private void updateStats(Collection<PlayerRef> players,
                             boolean whiteSide,
                             boolean won,
                             boolean lost,
                             boolean draw,
                             boolean resigned,
                             int undoCount,
                             int redoCount) {
        if (players == null) {
            return;
        }
        for (PlayerRef player : players) {
            if (player == null) {
                continue;
            }
            upsertStats(player.uuid(), whiteSide, won, lost, draw, resigned, undoCount, redoCount);
        }
    }

    private void upsertStats(UUID playerId,
                             boolean whiteSide,
                             boolean won,
                             boolean lost,
                             boolean draw,
                             boolean resigned,
                             int undoCount,
                             int redoCount) {
        String sql = """
                INSERT INTO chess_player_stats (
                  player_uuid,
                  games_played,
                  wins,
                  losses,
                  draws,
                  white_games,
                  black_games,
                  resigns,
                  undos,
                  redos
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                  games_played = chess_player_stats.games_played + excluded.games_played,
                  wins = chess_player_stats.wins + excluded.wins,
                  losses = chess_player_stats.losses + excluded.losses,
                  draws = chess_player_stats.draws + excluded.draws,
                  white_games = chess_player_stats.white_games + excluded.white_games,
                  black_games = chess_player_stats.black_games + excluded.black_games,
                  resigns = chess_player_stats.resigns + excluded.resigns,
                  undos = chess_player_stats.undos + excluded.undos,
                  redos = chess_player_stats.redos + excluded.redos
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, 1);
            statement.setInt(3, won ? 1 : 0);
            statement.setInt(4, lost ? 1 : 0);
            statement.setInt(5, draw ? 1 : 0);
            statement.setInt(6, whiteSide ? 1 : 0);
            statement.setInt(7, whiteSide ? 0 : 1);
            statement.setInt(8, resigned ? 1 : 0);
            statement.setInt(9, undoCount);
            statement.setInt(10, redoCount);
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to update Chess player stats for " + playerId, ex);
        }
    }

    private String buildDetail(ChessMoveRecord record) {
        if (record.pieceType() == null || record.from() == null || record.to() == null) {
            return record.eventLabel();
        }
        StringBuilder detail = new StringBuilder(record.moveLabel());
        if (record.capturedPieceName() != null) {
            detail.append(" X ").append(record.capturedPieceName());
        }
        if (record.check()) {
            detail.append(" check");
        }
        detail.append(record.legal() ? " legal" : " ilegal");
        return detail.toString();
    }

    private String joinPlayers(Collection<PlayerRef> players) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (PlayerRef player : players) {
            if (player == null) {
                continue;
            }
            if (!first) {
                builder.append(" , ");
            }
            builder.append(player.name());
            first = false;
        }
        return builder.toString();
    }

    private List<String> getMatchEventLines(long matchId) throws SQLException {
        List<String> events = new ArrayList<>();
        String sql = """
                SELECT event_time, player_name, detail
                FROM chess_match_events
                WHERE match_id = ?
                ORDER BY event_id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, matchId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String time = resultSet.getString("event_time");
                    String player = resultSet.getString("player_name");
                    String detail = resultSet.getString("detail");
                    StringBuilder line = new StringBuilder(time == null ? "" : time);
                    if (player != null && !player.isBlank()) {
                        line.append(" ").append(player);
                    }
                    if (detail != null && !detail.isBlank()) {
                        line.append(" ").append(detail);
                    }
                    events.add(line.toString().trim());
                }
            }
        }
        return events;
    }

    private String asBooleanText(int value) {
        return value == 0 ? "false" : "true";
    }

    private void ensureMatchColumn(String columnName, String definition) throws SQLException {
        if (hasColumn("chess_matches", columnName)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE chess_matches ADD COLUMN " + columnName + " " + definition);
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
}
