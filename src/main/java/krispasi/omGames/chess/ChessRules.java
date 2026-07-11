package krispasi.omGames.chess;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ChessRules {
    private ChessRules() {
    }

    public static List<ChessSquare> getCandidateMoves(ChessManager manager, ChessPiece piece, boolean legalOnly) {
        if (manager == null || piece == null || piece.captured() || piece.square() == null) {
            return List.of();
        }
        List<ChessSquare> moves = getPseudoMoves(manager, piece, true);
        if (!legalOnly) {
            return moves;
        }
        List<ChessSquare> legalMoves = new ArrayList<>();
        for (ChessSquare target : moves) {
            if (wouldLeaveKingSafe(manager, piece, target)) {
                legalMoves.add(target);
            }
        }
        return legalMoves;
    }

    public static boolean isValidMove(ChessManager manager, ChessPiece piece, ChessSquare target, boolean legalOnly) {
        return getCandidateMoves(manager, piece, legalOnly).contains(target);
    }

    public static boolean isKingInCheck(ChessManager manager, ChessSide side) {
        ChessPiece king = manager.findKing(side);
        if (king == null || king.square() == null || king.captured()) {
            return false;
        }
        return isSquareAttacked(manager, king.square(), side.opposite());
    }

    public static boolean hasAnyLegalMove(ChessManager manager, ChessSide side) {
        for (ChessPiece piece : manager.getPieces().values()) {
            if (piece.side() != side || piece.captured()) {
                continue;
            }
            if (!getCandidateMoves(manager, piece, true).isEmpty()) {
                return true;
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
        for (ChessPiece piece : manager.getPieces().values()) {
            if (piece.side() != side || piece.captured()) {
                continue;
            }
            attacked.addAll(getPseudoMoves(manager, piece, false));
        }
        return attacked;
    }

    public static boolean isSquareAttacked(ChessManager manager, ChessSquare target, ChessSide bySide) {
        for (ChessPiece piece : manager.getPieces().values()) {
            if (piece.side() != bySide || piece.captured()) {
                continue;
            }
            if (getPseudoMoves(manager, piece, false).contains(target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean wouldLeaveKingSafe(ChessManager manager, ChessPiece piece, ChessSquare target) {
        ChessBoardSnapshot snapshot = ChessBoardSnapshot.capture(manager);
        manager.applySimulationMove(piece, target);
        boolean safe = !isKingInCheck(manager, piece.side());
        manager.restoreSnapshot(snapshot, false);
        return safe;
    }

    private static List<ChessSquare> getPseudoMoves(ChessManager manager, ChessPiece piece, boolean includeCastling) {
        return switch (piece.type()) {
            case KING -> getKingMoves(manager, piece, includeCastling);
            case QUEEN -> getSlidingMoves(manager, piece, new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}});
            case BISHOP -> getSlidingMoves(manager, piece, new int[][]{{1,1},{1,-1},{-1,1},{-1,-1}});
            case ROOK -> getSlidingMoves(manager, piece, new int[][]{{1,0},{-1,0},{0,1},{0,-1}});
            case HORSE -> getKnightMoves(manager, piece);
            case PAWN -> getPawnMoves(manager, piece, includeCastling);
        };
    }

    private static List<ChessSquare> getKingMoves(ChessManager manager, ChessPiece piece, boolean includeCastling) {
        List<ChessSquare> moves = new ArrayList<>();
        for (int fileDelta = -1; fileDelta <= 1; fileDelta++) {
            for (int rankDelta = -1; rankDelta <= 1; rankDelta++) {
                if (fileDelta == 0 && rankDelta == 0) {
                    continue;
                }
                addIfEnterable(manager, piece, piece.square().offset(fileDelta, rankDelta), moves);
            }
        }
        if (includeCastling) {
            addCastlingMoves(manager, piece, moves);
        }
        return moves;
    }

    private static void addCastlingMoves(ChessManager manager, ChessPiece king, List<ChessSquare> moves) {
        if (king.moved() || isKingInCheck(manager, king.side())) {
            return;
        }
        int rank = king.side() == ChessSide.WHITE ? 0 : 7;
        if (canCastleKingside(manager, king.side(), rank)) {
            moves.add(new ChessSquare(6, rank));
        }
        if (canCastleQueenside(manager, king.side(), rank)) {
            moves.add(new ChessSquare(2, rank));
        }
    }

    private static boolean canCastleKingside(ChessManager manager, ChessSide side, int rank) {
        ChessPiece rook = manager.getPieceAt(new ChessSquare(7, rank));
        if (rook == null || rook.type() != ChessPieceType.ROOK || rook.side() != side || rook.moved() || rook.captured()) {
            return false;
        }
        if (manager.getPieceAt(new ChessSquare(5, rank)) != null || manager.getPieceAt(new ChessSquare(6, rank)) != null) {
            return false;
        }
        return !isSquareAttacked(manager, new ChessSquare(5, rank), side.opposite())
                && !isSquareAttacked(manager, new ChessSquare(6, rank), side.opposite());
    }

    private static boolean canCastleQueenside(ChessManager manager, ChessSide side, int rank) {
        ChessPiece rook = manager.getPieceAt(new ChessSquare(0, rank));
        if (rook == null || rook.type() != ChessPieceType.ROOK || rook.side() != side || rook.moved() || rook.captured()) {
            return false;
        }
        if (manager.getPieceAt(new ChessSquare(1, rank)) != null
                || manager.getPieceAt(new ChessSquare(2, rank)) != null
                || manager.getPieceAt(new ChessSquare(3, rank)) != null) {
            return false;
        }
        return !isSquareAttacked(manager, new ChessSquare(3, rank), side.opposite())
                && !isSquareAttacked(manager, new ChessSquare(2, rank), side.opposite());
    }

    private static List<ChessSquare> getSlidingMoves(ChessManager manager, ChessPiece piece, int[][] directions) {
        List<ChessSquare> moves = new ArrayList<>();
        for (int[] direction : directions) {
            ChessSquare cursor = piece.square();
            while (true) {
                cursor = cursor.offset(direction[0], direction[1]);
                if (cursor == null) {
                    break;
                }
                ChessPiece occupant = manager.getPieceAt(cursor);
                if (occupant == null) {
                    moves.add(cursor);
                    continue;
                }
                if (occupant.side() != piece.side() && occupant.type() != ChessPieceType.KING) {
                    moves.add(cursor);
                }
                break;
            }
        }
        return moves;
    }

    private static List<ChessSquare> getKnightMoves(ChessManager manager, ChessPiece piece) {
        List<ChessSquare> moves = new ArrayList<>();
        int[][] offsets = {{1,2},{2,1},{2,-1},{1,-2},{-1,-2},{-2,-1},{-2,1},{-1,2}};
        for (int[] offset : offsets) {
            addIfEnterable(manager, piece, piece.square().offset(offset[0], offset[1]), moves);
        }
        return moves;
    }

    private static List<ChessSquare> getPawnMoves(ChessManager manager, ChessPiece piece, boolean includeStandardMoves) {
        List<ChessSquare> moves = new ArrayList<>();
        int direction = piece.side().pawnDirection();
        ChessSquare forward = piece.square().offset(0, direction);
        if (includeStandardMoves && forward != null && manager.getPieceAt(forward) == null) {
            moves.add(forward);
            int startRank = piece.side() == ChessSide.WHITE ? 1 : 6;
            ChessSquare doubleForward = piece.square().offset(0, direction * 2);
            if (!piece.moved() && piece.square().rank() == startRank && doubleForward != null && manager.getPieceAt(doubleForward) == null) {
                moves.add(doubleForward);
            }
        }
        for (int fileDelta : new int[]{-1, 1}) {
            ChessSquare diagonal = piece.square().offset(fileDelta, direction);
            if (diagonal == null) {
                continue;
            }
            ChessPiece occupant = manager.getPieceAt(diagonal);
            if (occupant != null && occupant.side() != piece.side() && occupant.type() != ChessPieceType.KING) {
                moves.add(diagonal);
                continue;
            }
            if (manager.getEnPassantSquare() != null && manager.getEnPassantSquare().equals(diagonal)) {
                ChessPiece enPassantTarget = manager.getPieceById(manager.getEnPassantPawnId());
                if (enPassantTarget != null && enPassantTarget.side() != piece.side()) {
                    moves.add(diagonal);
                }
            }
        }
        if (!includeStandardMoves) {
            return moves.stream().filter(square -> Math.abs(square.file() - piece.square().file()) == 1).toList();
        }
        return moves;
    }

    private static void addIfEnterable(ChessManager manager, ChessPiece piece, ChessSquare target, List<ChessSquare> moves) {
        if (target == null) {
            return;
        }
        ChessPiece occupant = manager.getPieceAt(target);
        if (occupant == null || (occupant.side() != piece.side() && occupant.type() != ChessPieceType.KING)) {
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
}
