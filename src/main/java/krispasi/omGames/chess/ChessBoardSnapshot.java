package krispasi.omGames.chess;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record ChessBoardSnapshot(
        Map<UUID, PieceState> pieces,
        ChessSide turn,
        UUID selectedPieceId,
        ChessSquare enPassantSquare,
        UUID enPassantPawnId,
        boolean whiteKingMoved,
        boolean blackKingMoved,
        boolean whiteKingsideRookMoved,
        boolean whiteQueensideRookMoved,
        boolean blackKingsideRookMoved,
        boolean blackQueensideRookMoved
) {
    public static ChessBoardSnapshot capture(ChessMatchRuntime manager) {
        Map<UUID, PieceState> pieceStates = new LinkedHashMap<>();
        for (ChessPiece piece : manager.getPieces().values()) {
            pieceStates.put(piece.pieceId(), new PieceState(
                    piece.side(),
                    piece.type(),
                    piece.square(),
                    piece.displayId(),
                    piece.interactionId(),
                    piece.moved(),
                    piece.captured(),
                    piece.selected(),
                    piece.promotionConsumed(),
                    piece.captureOrder()
            ));
        }
        return new ChessBoardSnapshot(
                pieceStates,
                manager.getTurn(),
                manager.getSelectedPieceId(),
                manager.getEnPassantSquare(),
                manager.getEnPassantPawnId(),
                manager.isWhiteKingMoved(),
                manager.isBlackKingMoved(),
                manager.isWhiteKingsideRookMoved(),
                manager.isWhiteQueensideRookMoved(),
                manager.isBlackKingsideRookMoved(),
                manager.isBlackQueensideRookMoved()
        );
    }

    public record PieceState(
            ChessSide side,
            ChessPieceType type,
            ChessSquare square,
            UUID displayId,
            UUID interactionId,
            boolean moved,
            boolean captured,
            boolean selected,
            boolean promotionConsumed,
            int captureOrder
    ) {
    }
}
