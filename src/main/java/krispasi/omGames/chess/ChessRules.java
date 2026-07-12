package krispasi.omGames.chess;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ChessRules {
    private enum MoveMode {
        MOVE,
        ATTACK
    }

    private ChessRules() {
    }

    public static List<ChessSquare> getCandidateMoves(ChessManager manager, ChessPiece piece, boolean legalOnly) {
        if (manager == null || piece == null || piece.captured() || piece.square() == null) {
            return List.of();
        }
        BoardState board = BoardState.from(manager);
        PieceState statePiece = board.getPiece(piece.pieceId());
        if (statePiece == null || statePiece.captured()) {
            return List.of();
        }
        List<ChessSquare> moves = getPseudoMoves(board, statePiece, MoveMode.MOVE);
        if (!legalOnly) {
            return moves;
        }
        List<ChessSquare> legalMoves = new ArrayList<>();
        for (ChessSquare target : moves) {
            BoardState simulated = board.copy();
            simulated.applyMove(statePiece.id(), target);
            if (!simulated.isKingInCheck(statePiece.side())) {
                legalMoves.add(target);
            }
        }
        return legalMoves;
    }

    public static boolean isValidMove(ChessManager manager, ChessPiece piece, ChessSquare target, boolean legalOnly) {
        return target != null && getCandidateMoves(manager, piece, legalOnly).contains(target);
    }

    public static boolean isKingInCheck(ChessManager manager, ChessSide side) {
        if (manager == null || side == null) {
            return false;
        }
        return BoardState.from(manager).isKingInCheck(side);
    }

    public static boolean hasAnyLegalMove(ChessManager manager, ChessSide side) {
        if (manager == null || side == null) {
            return false;
        }
        BoardState board = BoardState.from(manager);
        for (PieceState piece : board.activePieces()) {
            if (piece.side() != side) {
                continue;
            }
            for (ChessSquare target : getPseudoMoves(board, piece, MoveMode.MOVE)) {
                BoardState simulated = board.copy();
                simulated.applyMove(piece.id(), target);
                if (!simulated.isKingInCheck(side)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isDeadPosition(ChessManager manager) {
        Collection<ChessPiece> pieces = manager.getActivePieces();
        List<ChessPiece> whitePieces = pieces.stream().filter(piece -> piece.side() == ChessSide.WHITE).toList();
        List<ChessPiece> blackPieces = pieces.stream().filter(piece -> piece.side() == ChessSide.BLACK).toList();
        if (pieces.size() == 2) {
            return true;
        }
        if (pieces.size() == 3) {
            return onlyKingsPlusSingleMinor(whitePieces) || onlyKingsPlusSingleMinor(blackPieces);
        }
        if (pieces.size() == 4 && onlyKingsAndBishops(whitePieces) && onlyKingsAndBishops(blackPieces)) {
            ChessPiece whiteBishop = whitePieces.stream().filter(piece -> piece.type() == ChessPieceType.BISHOP).findFirst().orElse(null);
            ChessPiece blackBishop = blackPieces.stream().filter(piece -> piece.type() == ChessPieceType.BISHOP).findFirst().orElse(null);
            if (whiteBishop != null && blackBishop != null && whiteBishop.square() != null && blackBishop.square() != null) {
                return whiteBishop.square().isLightSquare() == blackBishop.square().isLightSquare();
            }
        }
        return false;
    }

    public static Set<ChessSquare> getAttackedSquares(ChessManager manager, ChessSide side) {
        Set<ChessSquare> attacked = new LinkedHashSet<>();
        if (manager == null || side == null) {
            return attacked;
        }
        BoardState board = BoardState.from(manager);
        for (PieceState piece : board.activePieces()) {
            if (piece.side() == side) {
                attacked.addAll(getPseudoMoves(board, piece, MoveMode.ATTACK));
            }
        }
        return attacked;
    }

    public static boolean isSquareAttacked(ChessManager manager, ChessSquare target, ChessSide bySide) {
        if (manager == null || target == null || bySide == null) {
            return false;
        }
        return BoardState.from(manager).isSquareAttacked(target, bySide);
    }

    private static List<ChessSquare> getPseudoMoves(BoardState board, PieceState piece, MoveMode mode) {
        return switch (piece.type()) {
            case KING -> getKingMoves(board, piece, mode);
            case QUEEN -> getSlidingMoves(board, piece, mode, new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}});
            case BISHOP -> getSlidingMoves(board, piece, mode, new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}});
            case ROOK -> getSlidingMoves(board, piece, mode, new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}});
            case HORSE -> getKnightMoves(board, piece, mode);
            case PAWN -> getPawnMoves(board, piece, mode);
        };
    }

    private static List<ChessSquare> getKingMoves(BoardState board, PieceState piece, MoveMode mode) {
        List<ChessSquare> moves = new ArrayList<>();
        for (int fileDelta = -1; fileDelta <= 1; fileDelta++) {
            for (int rankDelta = -1; rankDelta <= 1; rankDelta++) {
                if (fileDelta == 0 && rankDelta == 0) {
                    continue;
                }
                ChessSquare target = piece.square().offset(fileDelta, rankDelta);
                if (mode == MoveMode.ATTACK) {
                    addIfPresent(target, moves);
                } else {
                    addIfEnterable(board, piece, target, moves);
                }
            }
        }
        if (mode == MoveMode.MOVE) {
            addCastlingMoves(board, piece, moves);
        }
        return moves;
    }

    private static void addCastlingMoves(BoardState board, PieceState king, List<ChessSquare> moves) {
        if (king.moved() || board.isKingInCheck(king.side())) {
            return;
        }
        int rank = king.side() == ChessSide.WHITE ? 0 : 7;
        if (canCastle(board, king.side(), rank, true)) {
            moves.add(new ChessSquare(6, rank));
        }
        if (canCastle(board, king.side(), rank, false)) {
            moves.add(new ChessSquare(2, rank));
        }
    }

    private static boolean canCastle(BoardState board, ChessSide side, int rank, boolean kingside) {
        int rookFile = kingside ? 7 : 0;
        PieceState rook = board.getPieceAt(new ChessSquare(rookFile, rank));
        if (rook == null || rook.type() != ChessPieceType.ROOK || rook.side() != side || rook.moved()) {
            return false;
        }
        int[] emptyFiles = kingside ? new int[]{5, 6} : new int[]{1, 2, 3};
        for (int file : emptyFiles) {
            if (board.getPieceAt(new ChessSquare(file, rank)) != null) {
                return false;
            }
        }
        int[] safeFiles = kingside ? new int[]{5, 6} : new int[]{3, 2};
        for (int file : safeFiles) {
            if (board.isSquareAttacked(new ChessSquare(file, rank), side.opposite())) {
                return false;
            }
        }
        return true;
    }

    private static List<ChessSquare> getSlidingMoves(BoardState board, PieceState piece, MoveMode mode, int[][] directions) {
        List<ChessSquare> moves = new ArrayList<>();
        for (int[] direction : directions) {
            ChessSquare cursor = piece.square();
            while (true) {
                cursor = cursor.offset(direction[0], direction[1]);
                if (cursor == null) {
                    break;
                }
                PieceState occupant = board.getPieceAt(cursor);
                if (occupant == null) {
                    moves.add(cursor);
                    continue;
                }
                if (mode == MoveMode.ATTACK || (occupant.side() != piece.side() && occupant.type() != ChessPieceType.KING)) {
                    moves.add(cursor);
                }
                break;
            }
        }
        return moves;
    }

    private static List<ChessSquare> getKnightMoves(BoardState board, PieceState piece, MoveMode mode) {
        List<ChessSquare> moves = new ArrayList<>();
        int[][] offsets = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
        for (int[] offset : offsets) {
            ChessSquare target = piece.square().offset(offset[0], offset[1]);
            if (mode == MoveMode.ATTACK) {
                addIfPresent(target, moves);
            } else {
                addIfEnterable(board, piece, target, moves);
            }
        }
        return moves;
    }

    private static List<ChessSquare> getPawnMoves(BoardState board, PieceState piece, MoveMode mode) {
        List<ChessSquare> moves = new ArrayList<>();
        int direction = piece.side().pawnDirection();
        if (mode == MoveMode.ATTACK) {
            for (int fileDelta : new int[]{-1, 1}) {
                addIfPresent(piece.square().offset(fileDelta, direction), moves);
            }
            return moves;
        }

        ChessSquare forward = piece.square().offset(0, direction);
        if (forward != null && board.getPieceAt(forward) == null) {
            moves.add(forward);
            int startRank = piece.side() == ChessSide.WHITE ? 1 : 6;
            ChessSquare doubleForward = piece.square().offset(0, direction * 2);
            if (!piece.moved()
                    && piece.square().rank() == startRank
                    && doubleForward != null
                    && board.getPieceAt(doubleForward) == null) {
                moves.add(doubleForward);
            }
        }

        for (int fileDelta : new int[]{-1, 1}) {
            ChessSquare diagonal = piece.square().offset(fileDelta, direction);
            if (diagonal == null) {
                continue;
            }
            PieceState occupant = board.getPieceAt(diagonal);
            if (occupant != null && occupant.side() != piece.side() && occupant.type() != ChessPieceType.KING) {
                moves.add(diagonal);
                continue;
            }
            if (diagonal.equals(board.enPassantSquare())) {
                PieceState enPassantTarget = board.getPiece(board.enPassantPawnId());
                if (enPassantTarget != null && enPassantTarget.side() != piece.side()) {
                    moves.add(diagonal);
                }
            }
        }
        return moves;
    }

    private static void addIfEnterable(BoardState board, PieceState piece, ChessSquare target, List<ChessSquare> moves) {
        if (target == null) {
            return;
        }
        PieceState occupant = board.getPieceAt(target);
        if (occupant == null || (occupant.side() != piece.side() && occupant.type() != ChessPieceType.KING)) {
            moves.add(target);
        }
    }

    private static void addIfPresent(ChessSquare target, List<ChessSquare> moves) {
        if (target != null) {
            moves.add(target);
        }
    }

    private static boolean onlyKingsPlusSingleMinor(List<ChessPiece> sidePieces) {
        if (sidePieces.size() != 2) {
            return false;
        }
        long minors = sidePieces.stream().filter(piece -> piece.type() == ChessPieceType.BISHOP || piece.type() == ChessPieceType.HORSE).count();
        long kings = sidePieces.stream().filter(piece -> piece.type() == ChessPieceType.KING).count();
        return minors == 1 && kings == 1;
    }

    private static boolean onlyKingsAndBishops(List<ChessPiece> sidePieces) {
        if (sidePieces.size() == 1) {
            return sidePieces.getFirst().type() == ChessPieceType.KING;
        }
        if (sidePieces.size() != 2) {
            return false;
        }
        long kings = sidePieces.stream().filter(piece -> piece.type() == ChessPieceType.KING).count();
        long bishops = sidePieces.stream().filter(piece -> piece.type() == ChessPieceType.BISHOP).count();
        return kings == 1 && bishops == 1;
    }

    private static final class BoardState {
        private final Map<UUID, PieceState> piecesById;
        private final Map<ChessSquare, UUID> piecesBySquare;
        private ChessSquare enPassantSquare;
        private UUID enPassantPawnId;

        private BoardState(Map<UUID, PieceState> piecesById,
                           Map<ChessSquare, UUID> piecesBySquare,
                           ChessSquare enPassantSquare,
                           UUID enPassantPawnId) {
            this.piecesById = piecesById;
            this.piecesBySquare = piecesBySquare;
            this.enPassantSquare = enPassantSquare;
            this.enPassantPawnId = enPassantPawnId;
        }

        static BoardState from(ChessManager manager) {
            Map<UUID, PieceState> pieces = new LinkedHashMap<>();
            Map<ChessSquare, UUID> squares = new LinkedHashMap<>();
            for (ChessPiece piece : manager.getPieces().values()) {
                PieceState state = new PieceState(
                        piece.pieceId(),
                        piece.side(),
                        piece.type(),
                        piece.square(),
                        piece.moved(),
                        piece.captured()
                );
                pieces.put(state.id(), state);
                if (!state.captured() && state.square() != null) {
                    squares.put(state.square(), state.id());
                }
            }
            return new BoardState(pieces, squares, manager.getEnPassantSquare(), manager.getEnPassantPawnId());
        }

        BoardState copy() {
            return new BoardState(
                    new LinkedHashMap<>(piecesById),
                    new LinkedHashMap<>(piecesBySquare),
                    enPassantSquare,
                    enPassantPawnId
            );
        }

        PieceState getPiece(UUID pieceId) {
            return pieceId == null ? null : piecesById.get(pieceId);
        }

        PieceState getPieceAt(ChessSquare square) {
            UUID pieceId = square == null ? null : piecesBySquare.get(square);
            return pieceId == null ? null : piecesById.get(pieceId);
        }

        Collection<PieceState> activePieces() {
            return piecesById.values().stream().filter(piece -> !piece.captured()).toList();
        }

        ChessSquare enPassantSquare() {
            return enPassantSquare;
        }

        UUID enPassantPawnId() {
            return enPassantPawnId;
        }

        boolean isKingInCheck(ChessSide side) {
            PieceState king = null;
            for (PieceState piece : piecesById.values()) {
                if (!piece.captured() && piece.side() == side && piece.type() == ChessPieceType.KING) {
                    king = piece;
                    break;
                }
            }
            return king != null && isSquareAttacked(king.square(), side.opposite());
        }

        boolean isSquareAttacked(ChessSquare target, ChessSide bySide) {
            for (PieceState piece : activePieces()) {
                if (piece.side() != bySide) {
                    continue;
                }
                if (getPseudoMoves(this, piece, MoveMode.ATTACK).contains(target)) {
                    return true;
                }
            }
            return false;
        }

        void applyMove(UUID pieceId, ChessSquare target) {
            PieceState piece = getPiece(pieceId);
            if (piece == null || piece.captured() || piece.square() == null || target == null) {
                return;
            }

            PieceState captured = resolveCapturedPiece(piece, target);
            if (captured != null) {
                piecesBySquare.remove(captured.square());
                piecesById.put(captured.id(), captured.asCaptured());
            }

            ChessSquare from = piece.square();
            piecesBySquare.remove(from);
            ChessPieceType nextType = piece.type();
            if (piece.type() == ChessPieceType.PAWN && (target.rank() == 0 || target.rank() == 7)) {
                nextType = ChessPieceType.QUEEN;
            }
            PieceState moved = piece.withMove(target, nextType);
            piecesById.put(piece.id(), moved);
            piecesBySquare.put(target, piece.id());

            if (piece.type() == ChessPieceType.KING && Math.abs(target.file() - from.file()) == 2) {
                moveCastlingRook(piece.side(), target);
            }

            if (piece.type() == ChessPieceType.PAWN && Math.abs(target.rank() - from.rank()) == 2) {
                enPassantSquare = new ChessSquare(from.file(), (from.rank() + target.rank()) / 2);
                enPassantPawnId = piece.id();
            } else {
                enPassantSquare = null;
                enPassantPawnId = null;
            }
        }

        private PieceState resolveCapturedPiece(PieceState piece, ChessSquare target) {
            PieceState occupant = getPieceAt(target);
            if (occupant != null && occupant.side() != piece.side()) {
                return occupant;
            }
            if (piece.type() == ChessPieceType.PAWN && occupant == null && target.equals(enPassantSquare)) {
                PieceState enPassantTarget = getPiece(enPassantPawnId);
                if (enPassantTarget != null && enPassantTarget.side() != piece.side()) {
                    return enPassantTarget;
                }
            }
            return null;
        }

        private void moveCastlingRook(ChessSide side, ChessSquare kingTarget) {
            int rank = side == ChessSide.WHITE ? 0 : 7;
            boolean kingside = kingTarget.file() == 6;
            ChessSquare rookFrom = new ChessSquare(kingside ? 7 : 0, rank);
            ChessSquare rookTo = new ChessSquare(kingside ? 5 : 3, rank);
            PieceState rook = getPieceAt(rookFrom);
            if (rook == null || rook.type() != ChessPieceType.ROOK || rook.side() != side) {
                return;
            }
            piecesBySquare.remove(rookFrom);
            PieceState movedRook = rook.withMove(rookTo, rook.type());
            piecesById.put(rook.id(), movedRook);
            piecesBySquare.put(rookTo, rook.id());
        }
    }

    private record PieceState(UUID id,
                              ChessSide side,
                              ChessPieceType type,
                              ChessSquare square,
                              boolean moved,
                              boolean captured) {
        PieceState withMove(ChessSquare target, ChessPieceType nextType) {
            return new PieceState(id, side, nextType, target, true, false);
        }

        PieceState asCaptured() {
            return new PieceState(id, side, type, square, moved, true);
        }
    }
}
