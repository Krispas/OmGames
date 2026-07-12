package krispasi.omGames.chess;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class ChessManager {
    private static final String BOARD_WORLD_NAME = "bedwars_lobby";
    private static final int BOARD_SIZE = 8;
    private static final int TILE_SIZE = 2;
    private static final float SQUARE_INTERACTION_WIDTH = 2.0f;
    private static final float SQUARE_INTERACTION_HEIGHT = 0.3f;
    private static final float PIECE_INTERACTION_WIDTH = 2.0f;
    private static final DateTimeFormatter BOARD_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yy.dd.MM-HH.mm.ss", Locale.ROOT);
    private static final DateTimeFormatter MATCH_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy.dd.MM-HH.mm.ss", Locale.ROOT);
    private static final DateTimeFormatter MOVE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd-HH.mm.ss", Locale.ROOT);
    private static final Transformation PIECE_TRANSFORMATION = new Transformation(
            new Vector3f(),
            new Quaternionf(),
            new Vector3f(2.0f, 2.0f, 2.0f),
            new Quaternionf()
    );

    private final JavaPlugin plugin;
    private final ChessDatabaseService databaseService;
    private final NamespacedKey entityTypeKey;
    private final NamespacedKey pieceIdKey;
    private final NamespacedKey squareKey;
    private final NamespacedKey timestampKey;
    private final Map<UUID, ChessPiece> pieces = new LinkedHashMap<>();
    private final Map<ChessSquare, UUID> squareInteractions = new LinkedHashMap<>();
    private final Map<ChessSquare, UUID> squareAnnotationDisplays = new LinkedHashMap<>();
    private final Set<UUID> boardEntityIds = new LinkedHashSet<>();
    private final LinkedHashMap<UUID, String> whitePlayers = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, String> blackPlayers = new LinkedHashMap<>();
    private final Map<UUID, Boolean> previousGlowing = new LinkedHashMap<>();
    private final Set<UUID> drawVotes = new LinkedHashSet<>();
    private final Deque<ChessBoardSnapshot> undoStack = new ArrayDeque<>();
    private final Deque<ChessBoardSnapshot> redoStack = new ArrayDeque<>();
    private ChessBoardPalette palette = ChessBoardPalette.DEFAULT;
    private ChessSettings settings = new ChessSettings();
    private BoardContext boardContext;
    private ChessSide turn = ChessSide.WHITE;
    private UUID selectedPieceId;
    private ChessSquare enPassantSquare;
    private UUID enPassantPawnId;
    private boolean whiteKingMoved;
    private boolean blackKingMoved;
    private boolean whiteKingsideRookMoved;
    private boolean whiteQueensideRookMoved;
    private boolean blackKingsideRookMoved;
    private boolean blackQueensideRookMoved;
    private boolean matchActive;
    private boolean pendingTestMode;
    private boolean testMode;
    private long matchId = -1L;
    private int moveCount;
    private int undoCount;
    private int redoCount;

    public ChessManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.databaseService = new ChessDatabaseService(plugin);
        this.entityTypeKey = new NamespacedKey(plugin, "chess_entity_type");
        this.pieceIdKey = new NamespacedKey(plugin, "chess_piece_id");
        this.squareKey = new NamespacedKey(plugin, "chess_square");
        this.timestampKey = new NamespacedKey(plugin, "chess_timestamp");
    }

    public void load() {
        databaseService.load();
    }

    public void shutdown() {
        if (matchActive) {
            abortActiveMatch("Plugin disabled");
        }
        clearTurnGlow();
        databaseService.shutdown();
    }

    public Result buildBoard(CommandSender sender, int x, int y, int z) {
        World world = resolveBoardWorld();
        if (world == null) {
            return Result.fail("World minecraft:" + BOARD_WORLD_NAME + " is not loaded.");
        }
        if (matchActive) {
            abortActiveMatch("Board rebuilt");
        }
        clearSelection();
        if (boardContext != null) {
            clearBoardBlocks(toBoardRef(boardContext));
            databaseService.deleteBoard(boardContext.timestamp());
        }
        clearBoardEntities();
        boardContext = new BoardContext(currentBoardTimestamp(), world.getName(), x, y, z);
        databaseService.saveBoard(boardContext);
        placeCheckerboard(List.of());
        spawnSquareInteractions();
        resetPiecesToStartingPosition(true);
        updateAnnotations();
        return Result.ok("Chess board built at minecraft:" + BOARD_WORLD_NAME + " " + x + " " + y + " " + z
                + " with 64 board interactions, 32 piece interactions, and 32 item displays.");
    }

    public Result resetBoard() {
        if (boardContext == null) {
            return Result.fail("Build a chess board first with /chess board build <x> <y> <z>.");
        }
        if (matchActive) {
            abortActiveMatch("Board reset");
        }
        clearSelection();
        databaseService.deleteBoard(boardContext.timestamp());
        clearBoardEntities();
        boardContext = boardContext.withTimestamp(currentBoardTimestamp());
        databaseService.saveBoard(boardContext);
        placeCheckerboard(List.of());
        spawnSquareInteractions();
        resetPiecesToStartingPosition(true);
        updateAnnotations();
        clearMatchRuntime();
        return Result.ok("Chess board reset to the starting position.");
    }

    public Result setPalette(Material lightBlock, Material darkBlock, Material highlightBlock) {
        if (lightBlock == null || darkBlock == null || highlightBlock == null) {
            return Result.fail("All three chess board blocks must be valid blocks.");
        }
        palette = new ChessBoardPalette(lightBlock, darkBlock, highlightBlock);
        if (boardContext != null) {
            refreshHighlights();
        }
        return Result.ok("Chess board palette set to " + lightBlock.getKey() + ", " + darkBlock.getKey()
                + ", " + highlightBlock.getKey() + ".");
    }

    public Result resetPalette() {
        palette = ChessBoardPalette.DEFAULT;
        if (boardContext != null) {
            refreshHighlights();
        }
        return Result.ok("Chess board palette reset to defaults.");
    }

    public Result setTeam(ChessSide side, List<Player> players) {
        if (matchActive) {
            return Result.fail("Chess teams cannot be changed while a match is active.");
        }
        if (side == null || players == null || players.isEmpty()) {
            return Result.fail("Specify at least one online player.");
        }
        LinkedHashMap<UUID, String> target = side == ChessSide.WHITE ? whitePlayers : blackPlayers;
        LinkedHashMap<UUID, String> other = side == ChessSide.WHITE ? blackPlayers : whitePlayers;
        target.clear();
        for (Player player : players) {
            if (player == null) {
                continue;
            }
            target.put(player.getUniqueId(), player.getName());
            other.remove(player.getUniqueId());
        }
        return Result.ok("Chess " + side.key() + " team set to " + String.join(", ", target.values()) + ".");
    }

    public Result startMatch() {
        if (boardContext == null) {
            return Result.fail("Build a chess board first with /chess board build <x> <y> <z>.");
        }
        if (matchActive) {
            return Result.fail("A chess match is already active.");
        }
        if (whitePlayers.isEmpty() || blackPlayers.isEmpty()) {
            return Result.fail("Set both teams first with /chess match white ... and /chess match black ....");
        }
        List<UUID> missingPlayers = getMissingTeamPlayers();
        if (!missingPlayers.isEmpty()) {
            return Result.fail("All chess players must be online before the match starts.");
        }

        clearBoardEntities();
        placeCheckerboard(List.of());
        spawnSquareInteractions();
        resetPiecesToStartingPosition(true);
        updateAnnotations();
        clearMatchRuntime();
        matchActive = true;
        testMode = pendingTestMode;
        pendingTestMode = false;
        turn = ChessSide.WHITE;
        teleportTeamsToBoard();
        applyTurnGlow();
        String startedAt = currentMatchTimestamp();
        matchId = databaseService.startMatch(
                boardContext,
                playerRefs(whitePlayers),
                playerRefs(blackPlayers),
                settings.copy(),
                testMode,
                startedAt
        );
        String suffix = testMode ? " Test mode is active: logging is disabled and any player can move any piece." : "";
        return Result.ok("Chess match started. White moves first." + suffix);
    }

    public Result enableTestMode() {
        if (matchActive) {
            if (moveCount > 0) {
                return Result.fail("Chess test mode can only be enabled before the first move.");
            }
            testMode = true;
            if (matchId > 0L) {
                databaseService.deleteMatch(matchId);
                matchId = -1L;
            }
            return Result.ok("Chess test mode enabled for the active match. Logging is disabled.");
        }
        pendingTestMode = true;
        return Result.ok("Chess test mode enabled for the next match.");
    }

    public Result setSetting(String settingKey, boolean value) {
        if (settingKey == null) {
            return Result.fail("Unknown chess setting.");
        }
        String normalized = settingKey.toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "do_movement_check" -> settings.setDoMovementCheck(value);
            case "visualize_movement_check" -> {
                settings.setVisualizeMovementCheck(value);
                refreshHighlights();
            }
            case "do_endgame_checks" -> settings.setDoEndgameChecks(value);
            case "show_annotation" -> {
                settings.setShowAnnotation(value);
                updateAnnotations();
            }
            case "allow_undo" -> {
                if (matchActive && moveCount > 0) {
                    return Result.fail("allow_undo can only be changed before the first move.");
                }
                settings.setAllowUndo(value);
            }
            default -> {
                return Result.fail("Unknown chess setting: " + settingKey + ".");
            }
        }
        return Result.ok("Chess setting " + normalized + " set to " + value + ".");
    }

    public Result removeBoard(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Result.fail("Usage: /chess board remove <timestamp|*>");
        }
        if (matchActive) {
            abortActiveMatch("Board removed");
        }
        clearSelection();
        if (timestamp.equals("*")) {
            List<ChessDatabaseService.BoardRef> boards = databaseService.getBoards();
            for (ChessDatabaseService.BoardRef board : boards) {
                loadBoardChunks(board);
                clearBoardBlocks(board);
            }
            int removedEntities = removeBoardEntities(null);
            databaseService.deleteAllBoards();
            clearLocalBoardState();
            return Result.ok("Removed " + boards.size() + " saved chess board"
                    + (boards.size() == 1 ? "" : "s") + " and " + removedEntities + " chess entities.");
        }

        ChessDatabaseService.BoardRef board = databaseService.getBoard(timestamp);
        if (board != null) {
            loadBoardChunks(board);
            clearBoardBlocks(board);
        }
        int removedEntities = removeBoardEntities(timestamp);
        databaseService.deleteBoard(timestamp);
        if (boardContext != null && boardContext.timestamp().equals(timestamp)) {
            clearLocalBoardState();
        }
        if (board == null && removedEntities == 0) {
            return Result.fail("No chess board or loaded chess entities were found for timestamp " + timestamp + ".");
        }
        return Result.ok("Removed chess board " + timestamp + " and " + removedEntities + " chess entities.");
    }

    public List<String> getBoardTimestamps() {
        return databaseService.getBoards().stream()
                .map(ChessDatabaseService.BoardRef::timestamp)
                .toList();
    }

    public Result printRecentLog(CommandSender sender) {
        ChessDatabaseService.RecentMatchLog log = databaseService.getMostRecentMatchLog();
        if (log == null) {
            return Result.fail("No chess match log is stored.");
        }
        sender.sendMessage(Component.text(log.header(), NamedTextColor.GOLD));
        for (String setting : log.settings()) {
            sender.sendMessage(Component.text(setting, NamedTextColor.YELLOW));
        }
        for (String event : log.events()) {
            sender.sendMessage(Component.text(event, NamedTextColor.WHITE));
        }
        if (log.result() != null && !log.result().isBlank()) {
            sender.sendMessage(Component.text(log.result(), NamedTextColor.GREEN));
        }
        return Result.ok("Printed most recent chess match log.");
    }

    public Result resign(Player player) {
        if (!matchActive) {
            return Result.fail("No chess match is active.");
        }
        ChessSide side = getPlayerSide(player);
        if (side == null) {
            return Result.fail("You are not on a chess team.");
        }
        ChessSide winner = side.opposite();
        logEvent(player, side, "Resign");
        finishMatch(winner, winner.key() + " team won", side == ChessSide.WHITE, side == ChessSide.BLACK);
        return Result.ok(winner.key().substring(0, 1).toUpperCase(Locale.ROOT) + winner.key().substring(1)
                + " team won by resignation.");
    }

    public Result voteDraw(Player player) {
        if (!matchActive) {
            return Result.fail("No chess match is active.");
        }
        ChessSide side = getPlayerSide(player);
        if (side == null) {
            return Result.fail("You are not on a chess team.");
        }
        drawVotes.add(player.getUniqueId());
        logEvent(player, side, "Draw vote");
        if (drawVotes.containsAll(whitePlayers.keySet()) && drawVotes.containsAll(blackPlayers.keySet())) {
            finishMatch(null, "Draw", false, false);
            return Result.ok("All chess team players voted for a draw. The match ended in a draw.");
        }
        int required = whitePlayers.size() + blackPlayers.size();
        return Result.ok("Draw vote recorded (" + drawVotes.size() + "/" + required + ").");
    }

    public Result undo(Player player, boolean operator) {
        if (!matchActive) {
            return Result.fail("No chess match is active.");
        }
        if (!settings.allowUndo()) {
            return Result.fail("Chess undo is disabled for this match.");
        }
        if (!operator && getPlayerSide(player) == null) {
            return Result.fail("Only chess team players and operators can undo moves.");
        }
        if (undoStack.isEmpty()) {
            return Result.fail("There is no chess move to undo.");
        }
        ChessBoardSnapshot current = ChessBoardSnapshot.capture(this);
        redoStack.push(current);
        restoreSnapshot(undoStack.pop(), true);
        clearSelection();
        drawVotes.clear();
        undoCount++;
        logEvent(player, getPlayerSide(player), "Undo");
        applyTurnGlow();
        return Result.ok("Chess move undone.");
    }

    public Result redo(Player player, boolean operator) {
        if (!matchActive) {
            return Result.fail("No chess match is active.");
        }
        if (!settings.allowUndo()) {
            return Result.fail("Chess redo is disabled for this match.");
        }
        if (!operator && getPlayerSide(player) == null) {
            return Result.fail("Only chess team players and operators can redo moves.");
        }
        if (redoStack.isEmpty()) {
            return Result.fail("There is no chess move to redo.");
        }
        ChessBoardSnapshot current = ChessBoardSnapshot.capture(this);
        undoStack.push(current);
        restoreSnapshot(redoStack.pop(), true);
        clearSelection();
        drawVotes.clear();
        redoCount++;
        logEvent(player, getPlayerSide(player), "Redo");
        applyTurnGlow();
        return Result.ok("Chess move redone.");
    }

    public Result checkmate(Player player) {
        if (!matchActive) {
            return Result.fail("No chess match is active.");
        }
        if (settings.doEndgameChecks()) {
            return Result.fail("Automatic endgame checks are enabled.");
        }
        if (getPlayerSide(player) == null) {
            return Result.fail("Only chess team players can use /chess checkmate.");
        }
        if (ChessRules.isKingInCheck(this, turn) && !ChessRules.hasAnyLegalMove(this, turn)) {
            ChessSide winner = turn.opposite();
            logEvent(player, getPlayerSide(player), "Checkmate");
            finishMatch(winner, winner.key() + " team won by checkmate", false, false);
            return Result.ok("Checkmate confirmed. " + winner.key() + " team won.");
        }
        return Result.fail("The side to move is not checkmated.");
    }

    public boolean handleEntityInteraction(Player player, Entity entity) {
        if (player == null || entity == null) {
            return false;
        }
        PersistentDataContainer container = entity.getPersistentDataContainer();
        String type = container.get(entityTypeKey, PersistentDataType.STRING);
        if (type == null) {
            return false;
        }
        if (!matchActive) {
            player.sendMessage(Component.text("No chess match is active.", NamedTextColor.RED));
            return true;
        }
        if (type.equals("piece")) {
            String pieceIdText = container.get(pieceIdKey, PersistentDataType.STRING);
            if (pieceIdText == null) {
                return true;
            }
            try {
                ChessPiece piece = pieces.get(UUID.fromString(pieceIdText));
                handlePieceClick(player, piece);
            } catch (IllegalArgumentException ignored) {
                return true;
            }
            return true;
        }
        if (type.equals("square")) {
            ChessSquare square = ChessSquare.fromNotation(container.get(squareKey, PersistentDataType.STRING));
            handleSquareClick(player, square);
            return true;
        }
        return true;
    }

    public boolean handleAirRightClick(Player player) {
        if (player == null || selectedPieceId == null) {
            return false;
        }
        ChessPiece selected = getPieceById(selectedPieceId);
        if (selected == null || !canControl(player, selected.side())) {
            return false;
        }
        deselectPiece();
        player.sendMessage(Component.text("Chess selection cancelled.", NamedTextColor.YELLOW));
        return true;
    }

    public void handleJoin(Player player) {
        if (player == null || !matchActive) {
            return;
        }
        if (whitePlayers.containsKey(player.getUniqueId()) || blackPlayers.containsKey(player.getUniqueId())) {
            previousGlowing.putIfAbsent(player.getUniqueId(), player.isGlowing());
            applyTurnGlow();
        }
    }

    public void handleQuit(Player player) {
        if (player == null) {
            return;
        }
        if (!matchActive) {
            previousGlowing.remove(player.getUniqueId());
        }
    }

    public Map<UUID, ChessPiece> getPieces() {
        return pieces;
    }

    public Collection<ChessPiece> getActivePieces() {
        return pieces.values().stream().filter(piece -> !piece.captured()).toList();
    }

    public ChessPiece getPieceById(UUID pieceId) {
        return pieceId == null ? null : pieces.get(pieceId);
    }

    public ChessPiece getPieceAt(ChessSquare square) {
        if (square == null) {
            return null;
        }
        for (ChessPiece piece : pieces.values()) {
            if (!piece.captured() && square.equals(piece.square())) {
                return piece;
            }
        }
        return null;
    }

    public ChessPiece findKing(ChessSide side) {
        for (ChessPiece piece : pieces.values()) {
            if (piece.side() == side && piece.type() == ChessPieceType.KING && !piece.captured()) {
                return piece;
            }
        }
        return null;
    }

    public ChessSide getTurn() {
        return turn;
    }

    public UUID getSelectedPieceId() {
        return selectedPieceId;
    }

    public ChessSquare getEnPassantSquare() {
        return enPassantSquare;
    }

    public UUID getEnPassantPawnId() {
        return enPassantPawnId;
    }

    public boolean isWhiteKingMoved() {
        return whiteKingMoved;
    }

    public boolean isBlackKingMoved() {
        return blackKingMoved;
    }

    public boolean isWhiteKingsideRookMoved() {
        return whiteKingsideRookMoved;
    }

    public boolean isWhiteQueensideRookMoved() {
        return whiteQueensideRookMoved;
    }

    public boolean isBlackKingsideRookMoved() {
        return blackKingsideRookMoved;
    }

    public boolean isBlackQueensideRookMoved() {
        return blackQueensideRookMoved;
    }

    public void restoreSnapshot(ChessBoardSnapshot snapshot, boolean updateEntities) {
        if (snapshot == null) {
            return;
        }
        if (updateEntities) {
            removePieceEntities();
        }
        pieces.clear();
        for (Map.Entry<UUID, ChessBoardSnapshot.PieceState> entry : snapshot.pieces().entrySet()) {
            ChessBoardSnapshot.PieceState state = entry.getValue();
            ChessPiece piece = new ChessPiece(entry.getKey(), state.side(), state.type(), state.square());
            piece.setDisplayId(updateEntities ? null : state.displayId());
            piece.setInteractionId(updateEntities ? null : state.interactionId());
            piece.setMoved(state.moved());
            piece.setCaptured(state.captured());
            piece.setSelected(state.selected());
            pieces.put(entry.getKey(), piece);
        }
        turn = snapshot.turn();
        selectedPieceId = snapshot.selectedPieceId();
        enPassantSquare = snapshot.enPassantSquare();
        enPassantPawnId = snapshot.enPassantPawnId();
        whiteKingMoved = snapshot.whiteKingMoved();
        blackKingMoved = snapshot.blackKingMoved();
        whiteKingsideRookMoved = snapshot.whiteKingsideRookMoved();
        whiteQueensideRookMoved = snapshot.whiteQueensideRookMoved();
        blackKingsideRookMoved = snapshot.blackKingsideRookMoved();
        blackQueensideRookMoved = snapshot.blackQueensideRookMoved();
        if (updateEntities) {
            for (ChessPiece piece : pieces.values()) {
                if (!piece.captured()) {
                    spawnPieceEntities(piece);
                }
            }
            refreshHighlights();
            updateAnnotations();
        }
    }

    private void handlePieceClick(Player player, ChessPiece clickedPiece) {
        if (clickedPiece == null || clickedPiece.captured()) {
            return;
        }
        ChessPiece selectedPiece = getPieceById(selectedPieceId);
        if (selectedPiece != null && selectedPiece.pieceId().equals(clickedPiece.pieceId())) {
            if (!canControl(player, selectedPiece.side())) {
                player.sendMessage(Component.text("It is not your chess turn.", NamedTextColor.RED));
                return;
            }
            deselectPiece();
            player.sendMessage(Component.text("Chess selection cancelled.", NamedTextColor.YELLOW));
            return;
        }
        if (selectedPiece != null && selectedPiece.side() != clickedPiece.side()) {
            attemptMove(player, selectedPiece, clickedPiece.square());
            return;
        }
        if (!canControl(player, clickedPiece.side())) {
            player.sendMessage(Component.text("It is not your chess turn.", NamedTextColor.RED));
            return;
        }
        selectPiece(clickedPiece);
        player.sendMessage(Component.text("Selected " + clickedPiece.logName() + " at "
                + clickedPiece.square().notation() + ".", NamedTextColor.YELLOW));
    }

    private void handleSquareClick(Player player, ChessSquare square) {
        if (square == null) {
            return;
        }
        ChessPiece selectedPiece = getPieceById(selectedPieceId);
        if (selectedPiece == null) {
            ChessPiece occupant = getPieceAt(square);
            if (occupant != null) {
                handlePieceClick(player, occupant);
            }
            return;
        }
        if (!canControl(player, selectedPiece.side())) {
            player.sendMessage(Component.text("It is not your chess turn.", NamedTextColor.RED));
            return;
        }
        attemptMove(player, selectedPiece, square);
    }

    private void attemptMove(Player actor, ChessPiece piece, ChessSquare target) {
        if (piece == null || target == null || piece.captured()) {
            return;
        }
        if (!canControl(actor, piece.side())) {
            actor.sendMessage(Component.text("It is not your chess turn.", NamedTextColor.RED));
            return;
        }
        ChessPiece occupant = getPieceAt(target);
        if (occupant != null && occupant.side() == piece.side()) {
            actor.sendMessage(Component.text("You cannot move onto your own chess piece.", NamedTextColor.RED));
            return;
        }
        if (occupant != null && occupant.type() == ChessPieceType.KING) {
            actor.sendMessage(Component.text("The king is never captured. Put it in check instead.", NamedTextColor.RED));
            return;
        }
        boolean legal = ChessRules.isValidMove(this, piece, target, true);
        boolean allowed = settings.doMovementCheck() ? legal : occupant == null || occupant.side() != piece.side();
        if (!allowed) {
            actor.sendMessage(Component.text("That chess move is not legal.", NamedTextColor.RED));
            return;
        }

        ChessBoardSnapshot snapshot = ChessBoardSnapshot.capture(this);
        ChessSquare from = piece.square();
        ChessPieceType movedType = piece.type();
        MoveExecution execution = executeMove(piece, target, true);
        undoStack.push(snapshot);
        redoStack.clear();
        clearSelection();
        turn = piece.side().opposite();
        moveCount++;
        drawVotes.clear();
        boolean check = ChessRules.isKingInCheck(this, turn);
        ChessMoveRecord record = new ChessMoveRecord(
                actor.getUniqueId(),
                actor.getName(),
                piece.side(),
                movedType,
                from,
                target,
                execution.capturedPieceName(),
                legal,
                check,
                execution.castling(),
                execution.enPassant(),
                execution.promotionPieceName(),
                currentMoveTimestamp(),
                "Move"
        );
        if (!testMode) {
            if (moveCount == 1) {
                databaseService.markSettingsLogged(matchId, settings.copy());
            }
            databaseService.logMove(matchId, record);
        }
        applyTurnGlow();
        actor.sendMessage(Component.text(record.moveLabel() + (check ? " check" : ""), NamedTextColor.GREEN));
        if (settings.doEndgameChecks()) {
            evaluateEndgame();
        }
    }

    private MoveExecution executeMove(ChessPiece piece, ChessSquare target, boolean updateEntities) {
        ChessSquare from = piece.square();
        ChessPiece occupant = getPieceAt(target);
        ChessPiece capturedPiece = null;
        boolean castling = piece.type() == ChessPieceType.KING && Math.abs(target.file() - from.file()) == 2;
        boolean enPassant = false;
        String promotionPieceName = null;

        if (piece.type() == ChessPieceType.PAWN && occupant == null && target.equals(enPassantSquare)) {
            ChessPiece enPassantPawn = getPieceById(enPassantPawnId);
            if (enPassantPawn != null && enPassantPawn.side() != piece.side()) {
                capturedPiece = enPassantPawn;
                enPassant = true;
            }
        } else if (occupant != null && occupant.side() != piece.side()) {
            capturedPiece = occupant;
        }

        if (capturedPiece != null) {
            capturedPiece.setCaptured(true);
            if (updateEntities) {
                removePieceEntityPair(capturedPiece);
            }
        }

        updateCastlingFlags(piece, from);
        piece.setSquare(target);
        piece.setMoved(true);
        if (castling) {
            moveCastlingRook(piece.side(), target, updateEntities);
        }

        if (piece.type() == ChessPieceType.PAWN && (target.rank() == 0 || target.rank() == 7)) {
            piece.setType(ChessPieceType.QUEEN);
            promotionPieceName = piece.side().key() + "_queen";
        }

        if (piece.type() == ChessPieceType.PAWN && Math.abs(target.rank() - from.rank()) == 2) {
            enPassantSquare = new ChessSquare(from.file(), (from.rank() + target.rank()) / 2);
            enPassantPawnId = piece.pieceId();
        } else {
            enPassantSquare = null;
            enPassantPawnId = null;
        }

        if (updateEntities) {
            movePieceEntities(piece);
            updatePieceDisplayItem(piece);
        }
        return new MoveExecution(capturedPiece == null ? null : capturedPiece.logName(), castling, enPassant, promotionPieceName);
    }

    private void moveCastlingRook(ChessSide side, ChessSquare kingTarget, boolean updateEntities) {
        int rank = side == ChessSide.WHITE ? 0 : 7;
        boolean kingside = kingTarget.file() == 6;
        ChessSquare rookFrom = new ChessSquare(kingside ? 7 : 0, rank);
        ChessSquare rookTo = new ChessSquare(kingside ? 5 : 3, rank);
        ChessPiece rook = getPieceAt(rookFrom);
        if (rook == null || rook.type() != ChessPieceType.ROOK || rook.side() != side) {
            return;
        }
        updateCastlingFlags(rook, rookFrom);
        rook.setSquare(rookTo);
        rook.setMoved(true);
        if (updateEntities) {
            movePieceEntities(rook);
        }
    }

    private void updateCastlingFlags(ChessPiece piece, ChessSquare from) {
        if (piece.type() == ChessPieceType.KING) {
            if (piece.side() == ChessSide.WHITE) {
                whiteKingMoved = true;
            } else {
                blackKingMoved = true;
            }
            return;
        }
        if (piece.type() != ChessPieceType.ROOK || from == null) {
            return;
        }
        if (piece.side() == ChessSide.WHITE && from.rank() == 0) {
            if (from.file() == 0) {
                whiteQueensideRookMoved = true;
            } else if (from.file() == 7) {
                whiteKingsideRookMoved = true;
            }
        } else if (piece.side() == ChessSide.BLACK && from.rank() == 7) {
            if (from.file() == 0) {
                blackQueensideRookMoved = true;
            } else if (from.file() == 7) {
                blackKingsideRookMoved = true;
            }
        }
    }

    private void selectPiece(ChessPiece piece) {
        if (piece == null) {
            return;
        }
        if (selectedPieceId != null) {
            ChessPiece previous = getPieceById(selectedPieceId);
            if (previous != null) {
                previous.setSelected(false);
                updatePieceDisplayItem(previous);
            }
        }
        selectedPieceId = piece.pieceId();
        piece.setSelected(true);
        updatePieceDisplayItem(piece);
        refreshHighlights();
    }

    private void deselectPiece() {
        if (selectedPieceId == null) {
            return;
        }
        ChessPiece selected = getPieceById(selectedPieceId);
        if (selected != null) {
            selected.setSelected(false);
            updatePieceDisplayItem(selected);
        }
        selectedPieceId = null;
        refreshHighlights();
    }

    private void clearSelection() {
        if (selectedPieceId == null) {
            refreshHighlights();
            return;
        }
        ChessPiece selected = getPieceById(selectedPieceId);
        if (selected != null) {
            selected.setSelected(false);
            updatePieceDisplayItem(selected);
        }
        selectedPieceId = null;
        refreshHighlights();
    }

    private boolean canControl(Player player, ChessSide side) {
        if (player == null || side == null || !matchActive) {
            return false;
        }
        if (testMode) {
            return true;
        }
        ChessSide playerSide = getPlayerSide(player);
        return playerSide == side && turn == side;
    }

    private ChessSide getPlayerSide(Player player) {
        if (player == null) {
            return null;
        }
        UUID id = player.getUniqueId();
        if (whitePlayers.containsKey(id)) {
            return ChessSide.WHITE;
        }
        if (blackPlayers.containsKey(id)) {
            return ChessSide.BLACK;
        }
        return null;
    }

    private void evaluateEndgame() {
        if (!matchActive) {
            return;
        }
        boolean inCheck = ChessRules.isKingInCheck(this, turn);
        boolean hasMove = ChessRules.hasAnyLegalMove(this, turn);
        if (inCheck && !hasMove) {
            ChessSide winner = turn.opposite();
            finishMatch(winner, winner.key() + " team won by checkmate", false, false);
            return;
        }
        if (!inCheck && !hasMove) {
            finishMatch(null, "Draw by stalemate", false, false);
            return;
        }
        if (ChessRules.isDeadPosition(this)) {
            finishMatch(null, "Draw by dead position", false, false);
        }
    }

    private void finishMatch(ChessSide winner, String result, boolean whiteResigned, boolean blackResigned) {
        if (!matchActive) {
            return;
        }
        clearSelection();
        matchActive = false;
        databaseService.finishMatch(
                matchId,
                playerRefs(whitePlayers),
                playerRefs(blackPlayers),
                winner,
                result,
                currentMatchTimestamp(),
                testMode,
                whiteResigned,
                blackResigned,
                undoCount,
                redoCount
        );
        clearTurnGlow();
        for (UUID playerId : allTeamPlayerIds()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(Component.text(result, winner == null ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
            }
        }
        clearMatchRuntime();
    }

    private void abortActiveMatch(String reason) {
        if (!matchActive) {
            return;
        }
        clearSelection();
        matchActive = false;
        if (!testMode) {
            databaseService.abortMatch(matchId, reason, currentMatchTimestamp());
        }
        clearTurnGlow();
        clearMatchRuntime();
    }

    private void logEvent(Player player, ChessSide side, String label) {
        if (testMode || matchId <= 0L) {
            return;
        }
        databaseService.logMove(matchId, ChessMoveRecord.event(
                player == null ? null : player.getUniqueId(),
                player == null ? null : player.getName(),
                side,
                currentMoveTimestamp(),
                label
        ));
    }

    private void clearMatchRuntime() {
        matchActive = false;
        testMode = false;
        matchId = -1L;
        moveCount = 0;
        undoCount = 0;
        redoCount = 0;
        undoStack.clear();
        redoStack.clear();
        drawVotes.clear();
        turn = ChessSide.WHITE;
        enPassantSquare = null;
        enPassantPawnId = null;
    }

    private void resetPiecesToStartingPosition(boolean spawnEntities) {
        removePieceEntities();
        pieces.clear();
        resetMoveState();
        createBackRank(ChessSide.WHITE, 0);
        createPawnRank(ChessSide.WHITE, 1);
        createPawnRank(ChessSide.BLACK, 6);
        createBackRank(ChessSide.BLACK, 7);
        if (spawnEntities) {
            for (ChessPiece piece : pieces.values()) {
                spawnPieceEntities(piece);
            }
        }
    }

    private void resetMoveState() {
        turn = ChessSide.WHITE;
        selectedPieceId = null;
        enPassantSquare = null;
        enPassantPawnId = null;
        whiteKingMoved = false;
        blackKingMoved = false;
        whiteKingsideRookMoved = false;
        whiteQueensideRookMoved = false;
        blackKingsideRookMoved = false;
        blackQueensideRookMoved = false;
    }

    private void createBackRank(ChessSide side, int rank) {
        ChessPieceType[] order = {
                ChessPieceType.ROOK,
                ChessPieceType.HORSE,
                ChessPieceType.BISHOP,
                ChessPieceType.QUEEN,
                ChessPieceType.KING,
                ChessPieceType.BISHOP,
                ChessPieceType.HORSE,
                ChessPieceType.ROOK
        };
        for (int file = 0; file < order.length; file++) {
            addPiece(side, order[file], new ChessSquare(file, rank));
        }
    }

    private void createPawnRank(ChessSide side, int rank) {
        for (int file = 0; file < BOARD_SIZE; file++) {
            addPiece(side, ChessPieceType.PAWN, new ChessSquare(file, rank));
        }
    }

    private void addPiece(ChessSide side, ChessPieceType type, ChessSquare square) {
        ChessPiece piece = new ChessPiece(UUID.randomUUID(), side, type, square);
        pieces.put(piece.pieceId(), piece);
    }

    private void placeCheckerboard(Collection<ChessSquare> highlightedSquares) {
        if (boardContext == null) {
            return;
        }
        World world = Bukkit.getWorld(boardContext.worldName());
        if (world == null) {
            return;
        }
        Set<ChessSquare> highlights = highlightedSquares == null ? Set.of() : new LinkedHashSet<>(highlightedSquares);
        for (int file = 0; file < BOARD_SIZE; file++) {
            for (int rank = 0; rank < BOARD_SIZE; rank++) {
                ChessSquare square = new ChessSquare(file, rank);
                Material material = highlights.contains(square)
                        ? palette.highlightBlock()
                        : (square.isLightSquare() ? palette.lightBlock() : palette.darkBlock());
                setSquareBlocks(world, square, material);
            }
        }
    }

    private void setSquareBlocks(World world, ChessSquare square, Material material) {
        int startX = boardContext.originX() + square.file() * TILE_SIZE;
        int startZ = boardContext.originZ() - square.rank() * TILE_SIZE;
        for (int dx = 0; dx < TILE_SIZE; dx++) {
            for (int dz = 0; dz < TILE_SIZE; dz++) {
                Block block = world.getBlockAt(startX + dx, boardContext.originY(), startZ - dz);
                block.setType(material, false);
            }
        }
    }

    private void clearBoardBlocks(ChessDatabaseService.BoardRef board) {
        if (board == null) {
            return;
        }
        World world = Bukkit.getWorld(board.worldName());
        if (world == null) {
            return;
        }
        for (int dx = 0; dx < BOARD_SIZE * TILE_SIZE; dx++) {
            for (int dz = 0; dz < BOARD_SIZE * TILE_SIZE; dz++) {
                world.getBlockAt(board.originX() + dx, board.originY(), board.originZ() - dz).setType(Material.AIR, false);
            }
        }
    }

    private ChessDatabaseService.BoardRef toBoardRef(BoardContext context) {
        return new ChessDatabaseService.BoardRef(
                context.timestamp(),
                context.worldName(),
                context.originX(),
                context.originY(),
                context.originZ()
        );
    }

    private void loadBoardChunks(ChessDatabaseService.BoardRef board) {
        if (board == null) {
            return;
        }
        World world = Bukkit.getWorld(board.worldName());
        if (world == null) {
            return;
        }
        int minX = board.originX();
        int maxX = board.originX() + BOARD_SIZE * TILE_SIZE - 1;
        int minZ = board.originZ() - BOARD_SIZE * TILE_SIZE + 1;
        int maxZ = board.originZ();
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                world.loadChunk(chunkX, chunkZ);
            }
        }
    }

    private void refreshHighlights() {
        if (boardContext == null) {
            return;
        }
        ChessPiece selected = getPieceById(selectedPieceId);
        if (settings.visualizeMovementCheck() && selected != null && !selected.captured()) {
            placeCheckerboard(ChessRules.getCandidateMoves(this, selected, true));
        } else {
            placeCheckerboard(List.of());
        }
    }

    private void spawnSquareInteractions() {
        if (boardContext == null) {
            return;
        }
        World world = Bukkit.getWorld(boardContext.worldName());
        if (world == null) {
            return;
        }
        squareInteractions.clear();
        for (int file = 0; file < BOARD_SIZE; file++) {
            for (int rank = 0; rank < BOARD_SIZE; rank++) {
                ChessSquare square = new ChessSquare(file, rank);
                Location location = squareCenter(square);
                Interaction interaction = world.spawn(location, Interaction.class, entity -> {
                    entity.setPersistent(true);
                    entity.setInvulnerable(true);
                    entity.setGravity(false);
                    entity.setInteractionWidth(SQUARE_INTERACTION_WIDTH);
                    entity.setInteractionHeight(SQUARE_INTERACTION_HEIGHT);
                    entity.setResponsive(true);
                    tagEntity(entity, "square", square.notation(), null);
                });
                squareInteractions.put(square, interaction.getUniqueId());
                boardEntityIds.add(interaction.getUniqueId());
            }
        }
    }

    private void spawnPieceEntities(ChessPiece piece) {
        if (boardContext == null || piece == null || piece.captured()) {
            return;
        }
        World world = Bukkit.getWorld(boardContext.worldName());
        if (world == null) {
            return;
        }
        Location displayLocation = pieceDisplayCenter(piece.square());
        Location interactionLocation = squareCenter(piece.square());
        float yaw = piece.side() == ChessSide.WHITE ? 180.0f : 0.0f;
        displayLocation.setYaw(yaw);
        interactionLocation.setYaw(yaw);
        ItemDisplay display = world.spawn(displayLocation, ItemDisplay.class, entity -> {
            entity.setPersistent(true);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setShadowRadius(0.0f);
            entity.setShadowStrength(0.0f);
            entity.setDisplayWidth(2.0f);
            entity.setDisplayHeight(piece.type().interactionHeight());
            entity.setTransformation(PIECE_TRANSFORMATION);
            entity.setItemStack(createPieceItem(piece));
            entity.setRotation(yaw, 0.0f);
            tagEntity(entity, "piece_display", piece.square().notation(), piece);
        });
        Interaction interaction = world.spawn(interactionLocation, Interaction.class, entity -> {
            entity.setPersistent(true);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setInteractionWidth(PIECE_INTERACTION_WIDTH);
            entity.setInteractionHeight(piece.type().interactionHeight());
            entity.setResponsive(true);
            entity.setRotation(yaw, 0.0f);
            tagEntity(entity, "piece", piece.square().notation(), piece);
        });
        piece.setDisplayId(display.getUniqueId());
        piece.setInteractionId(interaction.getUniqueId());
        boardEntityIds.add(display.getUniqueId());
        boardEntityIds.add(interaction.getUniqueId());
    }

    private void tagEntity(Entity entity, String entityType, String square, ChessPiece piece) {
        PersistentDataContainer container = entity.getPersistentDataContainer();
        container.set(entityTypeKey, PersistentDataType.STRING, entityType);
        container.set(timestampKey, PersistentDataType.STRING, boardContext.timestamp());
        if (square != null) {
            container.set(squareKey, PersistentDataType.STRING, square);
        }
        entity.addScoreboardTag("chess");
        entity.addScoreboardTag("chess_" + boardContext.timestamp());
        entity.addScoreboardTag("chess_" + entityType);
        if (piece != null) {
            String pieceName = piece.logName();
            container.set(pieceIdKey, PersistentDataType.STRING, piece.pieceId().toString());
            entity.addScoreboardTag(pieceName);
        } else if (square != null) {
            entity.addScoreboardTag(square);
        }
        updateEntityAnnotation(entity, entityType, square, piece);
    }

    private void updateAnnotations() {
        removeSquareAnnotationDisplays();
        if (settings.showAnnotation() && boardContext != null) {
            spawnSquareAnnotationDisplays();
        }
        for (Map.Entry<ChessSquare, UUID> entry : squareInteractions.entrySet()) {
            if (Bukkit.getEntity(entry.getValue()) instanceof Interaction interaction) {
                updateEntityAnnotation(interaction, "square", entry.getKey().notation(), null);
            }
        }
        for (ChessPiece piece : pieces.values()) {
            if (piece.captured()) {
                continue;
            }
            if (Bukkit.getEntity(piece.displayId()) instanceof ItemDisplay display) {
                updateEntityAnnotation(display, "piece_display", piece.square().notation(), piece);
            }
            if (Bukkit.getEntity(piece.interactionId()) instanceof Interaction interaction) {
                updateEntityAnnotation(interaction, "piece", piece.square().notation(), piece);
            }
        }
    }

    private void updateEntityAnnotation(Entity entity, String entityType, String square, ChessPiece piece) {
        if (entity == null) {
            return;
        }
        if (entity instanceof Interaction) {
            String hiddenLabel = piece != null
                    ? piece.logName() + " " + boardContext.timestamp()
                    : (square == null ? "chess " + boardContext.timestamp() : "chess_square_" + square + " " + boardContext.timestamp());
            entity.customName(Component.text(hiddenLabel, NamedTextColor.GRAY));
            entity.setCustomNameVisible(false);
            return;
        }
        boolean visible = false;
        String label;
        NamedTextColor color = NamedTextColor.GRAY;
        if (settings.showAnnotation() && entityType.equals("piece_display") && piece != null) {
            label = annotationName(piece);
            visible = true;
            color = piece.side() == ChessSide.WHITE ? NamedTextColor.WHITE : NamedTextColor.GRAY;
        } else if (piece != null) {
            label = piece.logName() + " " + boardContext.timestamp();
        } else if (square != null) {
            label = "chess_square_" + square + " " + boardContext.timestamp();
        } else {
            label = "chess " + boardContext.timestamp();
        }
        entity.customName(Component.text(label, color));
        entity.setCustomNameVisible(visible);
    }

    private void spawnSquareAnnotationDisplays() {
        World world = Bukkit.getWorld(boardContext.worldName());
        if (world == null) {
            return;
        }
        for (int file = 0; file < BOARD_SIZE; file++) {
            for (int rank = 0; rank < BOARD_SIZE; rank++) {
                ChessSquare square = new ChessSquare(file, rank);
                Location location = squareCenter(square).add(0.0, 0.15, 0.0);
                TextDisplay display = world.spawn(location, TextDisplay.class, entity -> {
                    entity.setPersistent(true);
                    entity.setInvulnerable(true);
                    entity.setGravity(false);
                    entity.setBillboard(Display.Billboard.CENTER);
                    entity.text(Component.text(square.notation(), NamedTextColor.AQUA));
                    entity.setAlignment(TextDisplay.TextAlignment.CENTER);
                    entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                    entity.setShadowed(true);
                    entity.setSeeThrough(true);
                    entity.setTransformation(new Transformation(
                            new Vector3f(),
                            new Quaternionf(),
                            new Vector3f(0.6f, 0.6f, 0.6f),
                            new Quaternionf()));
                    tagEntity(entity, "square_annotation", square.notation(), null);
                    entity.setCustomNameVisible(false);
                });
                squareAnnotationDisplays.put(square, display.getUniqueId());
                boardEntityIds.add(display.getUniqueId());
            }
        }
    }

    private void removeSquareAnnotationDisplays() {
        for (UUID entityId : new ArrayList<>(squareAnnotationDisplays.values())) {
            removeEntity(entityId);
        }
        squareAnnotationDisplays.clear();
    }

    private String annotationName(ChessPiece piece) {
        return capitalize(piece.side().key()) + " " + piece.type().key();
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private void movePieceEntities(ChessPiece piece) {
        if (piece == null || piece.square() == null) {
            return;
        }
        Location displayLocation = pieceDisplayCenter(piece.square());
        Location interactionLocation = squareCenter(piece.square());
        float yaw = piece.side() == ChessSide.WHITE ? 180.0f : 0.0f;
        displayLocation.setYaw(yaw);
        interactionLocation.setYaw(yaw);
        if (Bukkit.getEntity(piece.displayId()) instanceof ItemDisplay display) {
            display.teleport(displayLocation);
            display.setRotation(yaw, 0.0f);
            display.setDisplayHeight(piece.type().interactionHeight());
            display.getPersistentDataContainer().set(squareKey, PersistentDataType.STRING, piece.square().notation());
            updateEntityAnnotation(display, "piece_display", piece.square().notation(), piece);
        }
        if (Bukkit.getEntity(piece.interactionId()) instanceof Interaction interaction) {
            interaction.teleport(interactionLocation);
            interaction.setRotation(yaw, 0.0f);
            interaction.setInteractionHeight(piece.type().interactionHeight());
            interaction.getPersistentDataContainer().set(squareKey, PersistentDataType.STRING, piece.square().notation());
            updateEntityAnnotation(interaction, "piece", piece.square().notation(), piece);
        }
    }

    private void updatePieceDisplayItem(ChessPiece piece) {
        if (piece == null) {
            return;
        }
        if (Bukkit.getEntity(piece.displayId()) instanceof ItemDisplay display) {
            display.setItemStack(createPieceItem(piece));
            updateEntityAnnotation(display, "piece_display", piece.square().notation(), piece);
        }
    }

    private ItemStack createPieceItem(ChessPiece piece) {
        ItemStack stack = new ItemStack(Material.IRON_NUGGET);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(pieceModel(piece));
        meta.displayName(Component.text(piece.logName(), piece.side() == ChessSide.WHITE ? NamedTextColor.WHITE : NamedTextColor.GRAY));
        meta.setHideTooltip(true);
        stack.setItemMeta(meta);
        return stack;
    }

    private NamespacedKey pieceModel(ChessPiece piece) {
        String model = piece.selected()
                ? "selected_" + piece.type().key()
                : (piece.side() == ChessSide.BLACK ? "black_" : "") + piece.type().key();
        return new NamespacedKey("om", model);
    }

    private void removePieceEntities() {
        for (ChessPiece piece : pieces.values()) {
            removePieceEntityPair(piece);
        }
    }

    private void removePieceEntityPair(ChessPiece piece) {
        if (piece == null) {
            return;
        }
        removeEntity(piece.displayId());
        removeEntity(piece.interactionId());
        piece.setDisplayId(null);
        piece.setInteractionId(null);
    }

    private void clearBoardEntities() {
        for (UUID entityId : new ArrayList<>(boardEntityIds)) {
            removeEntity(entityId);
        }
        boardEntityIds.clear();
        squareInteractions.clear();
        squareAnnotationDisplays.clear();
        for (ChessPiece piece : pieces.values()) {
            piece.setDisplayId(null);
            piece.setInteractionId(null);
        }
    }

    private void clearLocalBoardState() {
        clearBoardEntities();
        pieces.clear();
        squareInteractions.clear();
        squareAnnotationDisplays.clear();
        boardEntityIds.clear();
        boardContext = null;
        clearMatchRuntime();
        resetMoveState();
    }

    private int removeBoardEntities(String timestamp) {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (!isChessBoardEntity(entity, timestamp)) {
                    continue;
                }
                UUID entityId = entity.getUniqueId();
                entity.remove();
                boardEntityIds.remove(entityId);
                removed++;
            }
        }
        return removed;
    }

    private boolean isChessBoardEntity(Entity entity, String timestamp) {
        if (entity == null) {
            return false;
        }
        PersistentDataContainer container = entity.getPersistentDataContainer();
        boolean chessEntity = container.has(entityTypeKey, PersistentDataType.STRING)
                || entity.getScoreboardTags().contains("chess");
        if (!chessEntity) {
            return false;
        }
        if (timestamp == null || timestamp.isBlank()) {
            return true;
        }
        String entityTimestamp = container.get(timestampKey, PersistentDataType.STRING);
        return timestamp.equals(entityTimestamp) || entity.getScoreboardTags().contains("chess_" + timestamp);
    }

    private void removeEntity(UUID entityId) {
        if (entityId == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(entityId);
        if (entity != null) {
            entity.remove();
        }
        boardEntityIds.remove(entityId);
    }

    private Location squareCenter(ChessSquare square) {
        World world = Bukkit.getWorld(boardContext.worldName());
        return new Location(
                world,
                boardContext.originX() + square.file() * TILE_SIZE + 1.0,
                boardContext.originY() + 1.0,
                boardContext.originZ() - square.rank() * TILE_SIZE,
                0.0f,
                0.0f
        );
    }

    private Location pieceDisplayCenter(ChessSquare square) {
        return squareCenter(square).add(0.0, 1.0, 0.0);
    }

    private void teleportTeamsToBoard() {
        Location whiteSpawn = sideSpawn(ChessSide.WHITE);
        Location blackSpawn = sideSpawn(ChessSide.BLACK);
        for (UUID playerId : whitePlayers.keySet()) {
            teleportToBoard(Bukkit.getPlayer(playerId), whiteSpawn);
        }
        for (UUID playerId : blackPlayers.keySet()) {
            teleportToBoard(Bukkit.getPlayer(playerId), blackSpawn);
        }
    }

    private Location sideSpawn(ChessSide side) {
        World world = Bukkit.getWorld(boardContext.worldName());
        double centerX = boardContext.originX() + 8.0;
        double z = side == ChessSide.WHITE ? boardContext.originZ() + 3.0 : boardContext.originZ() - 17.0;
        float yaw = side == ChessSide.WHITE ? 180.0f : 0.0f;
        return new Location(world, centerX, boardContext.originY() + 1.0, z, yaw, 0.0f);
    }

    private void teleportToBoard(Player player, Location target) {
        if (player == null || target == null) {
            return;
        }
        player.teleport(target);
        player.setVelocity(new Vector());
    }

    private void applyTurnGlow() {
        if (!matchActive) {
            return;
        }
        for (UUID playerId : allTeamPlayerIds()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            previousGlowing.putIfAbsent(playerId, player.isGlowing());
            ChessSide side = whitePlayers.containsKey(playerId) ? ChessSide.WHITE : ChessSide.BLACK;
            player.setGlowing(side == turn);
        }
    }

    private void clearTurnGlow() {
        for (Map.Entry<UUID, Boolean> entry : new ArrayList<>(previousGlowing.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.setGlowing(Boolean.TRUE.equals(entry.getValue()));
            }
        }
        previousGlowing.clear();
    }

    private Set<UUID> allTeamPlayerIds() {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        ids.addAll(whitePlayers.keySet());
        ids.addAll(blackPlayers.keySet());
        return ids;
    }

    private List<UUID> getMissingTeamPlayers() {
        List<UUID> missing = new ArrayList<>();
        for (UUID playerId : allTeamPlayerIds()) {
            if (Bukkit.getPlayer(playerId) == null) {
                missing.add(playerId);
            }
        }
        return missing;
    }

    private List<ChessDatabaseService.PlayerRef> playerRefs(Map<UUID, String> players) {
        return players.entrySet().stream()
                .map(entry -> new ChessDatabaseService.PlayerRef(entry.getKey(), entry.getValue()))
                .toList();
    }

    private World resolveBoardWorld() {
        World world = Bukkit.getWorld(NamespacedKey.minecraft(BOARD_WORLD_NAME));
        if (world != null) {
            return world;
        }
        return Bukkit.getWorld(BOARD_WORLD_NAME);
    }

    private String currentBoardTimestamp() {
        return BOARD_TIMESTAMP_FORMAT.format(LocalDateTime.now());
    }

    private String currentMatchTimestamp() {
        return MATCH_TIMESTAMP_FORMAT.format(LocalDateTime.now());
    }

    private String currentMoveTimestamp() {
        return MOVE_TIMESTAMP_FORMAT.format(LocalDateTime.now());
    }

    public record Result(boolean success, String message) {
        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }

    public record BoardContext(String timestamp, String worldName, int originX, int originY, int originZ) {
        public BoardContext withTimestamp(String timestamp) {
            return new BoardContext(timestamp, worldName, originX, originY, originZ);
        }
    }

    private record MoveExecution(String capturedPieceName, boolean castling, boolean enPassant, String promotionPieceName) {
    }
}
