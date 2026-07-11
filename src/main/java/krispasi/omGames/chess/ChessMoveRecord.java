package krispasi.omGames.chess;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ChessMoveRecord(
        UUID actorId,
        String actorName,
        ChessSide side,
        ChessPieceType pieceType,
        ChessSquare from,
        ChessSquare to,
        String capturedPieceName,
        boolean legal,
        boolean check,
        boolean castling,
        boolean enPassant,
        String promotionPieceName,
        String timestamp,
        String eventLabel
) {
    public String moveLabel() {
        return pieceName() + " " + from.notation() + " - " + to.notation();
    }

    public String pieceName() {
        return side.key() + "_" + pieceType.key();
    }

    public static ChessMoveRecord event(UUID actorId, String actorName, ChessSide side, String timestamp, String label) {
        return new ChessMoveRecord(actorId, actorName, side, null, null, null, null, true, false, false, false, null, timestamp, label);
    }
}
