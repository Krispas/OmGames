package krispasi.omGames.chess;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import krispasi.omGames.storage.OmGamesDatabaseFiles;
import org.bukkit.entity.Player;
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

    private final File databaseFile;
    private final Logger logger;
    private Connection connection;

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
            }
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
                           Collection<Player> whitePlayers,
                           Collection<Player> blackPlayers,
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
                  settings_logged
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
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
                    settings_logged = 1
                WHERE match_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, settings.allowUndo() ? 1 : 0);
            statement.setInt(2, settings.doMovementCheck() ? 1 : 0);
            statement.setInt(3, settings.visualizeMovementCheck() ? 1 : 0);
            statement.setInt(4, settings.doEndgameChecks() ? 1 : 0);
            statement.setLong(5, matchId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to mark Chess settings logged.", ex);
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
                            Collection<Player> whitePlayers,
                            Collection<Player> blackPlayers,
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

    private void updateStats(Collection<Player> players,
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
        for (Player player : players) {
            if (player == null) {
                continue;
            }
            upsertStats(player.getUniqueId(), whiteSide, won, lost, draw, resigned, undoCount, redoCount);
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

    private String joinPlayers(Collection<Player> players) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Player player : players) {
            if (player == null) {
                continue;
            }
            if (!first) {
                builder.append(" , ");
            }
            builder.append(player.getName());
            first = false;
        }
        return builder.toString();
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
