package krispasi.omGames.chess;

import java.util.UUID;

public final class ChessPiece {
    private final UUID pieceId;
    private final ChessSide side;
    private final ChessPieceType type;
    private ChessSquare square;
    private UUID displayId;
    private UUID interactionId;
    private boolean moved;
    private boolean captured;
    private boolean selected;

    public ChessPiece(UUID pieceId, ChessSide side, ChessPieceType type, ChessSquare square) {
        this.pieceId = pieceId;
        this.side = side;
        this.type = type;
        this.square = square;
    }

    public UUID pieceId() {
        return pieceId;
    }

    public ChessSide side() {
        return side;
    }

    public ChessPieceType type() {
        return type;
    }

    public ChessSquare square() {
        return square;
    }

    public void setSquare(ChessSquare square) {
        this.square = square;
    }

    public UUID displayId() {
        return displayId;
    }

    public void setDisplayId(UUID displayId) {
        this.displayId = displayId;
    }

    public UUID interactionId() {
        return interactionId;
    }

    public void setInteractionId(UUID interactionId) {
        this.interactionId = interactionId;
    }

    public boolean moved() {
        return moved;
    }

    public void setMoved(boolean moved) {
        this.moved = moved;
    }

    public boolean captured() {
        return captured;
    }

    public void setCaptured(boolean captured) {
        this.captured = captured;
    }

    public boolean selected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public String logName() {
        return side.key() + "_" + type.key();
    }
}
