package krispasi.omGames.chess;

public enum ChessPieceType {
    KING("king", 4.0f),
    QUEEN("queen", 4.0f),
    BISHOP("bishop", 3.0f),
    HORSE("horse", 3.0f),
    ROOK("rook", 3.0f),
    PAWN("pawn", 2.0f);

    private final String key;
    private final float interactionHeight;

    ChessPieceType(String key, float interactionHeight) {
        this.key = key;
        this.interactionHeight = interactionHeight;
    }

    public String key() {
        return key;
    }

    public float interactionHeight() {
        return interactionHeight;
    }
}
