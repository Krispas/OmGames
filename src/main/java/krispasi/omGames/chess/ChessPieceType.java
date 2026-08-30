package krispasi.omGames.chess;

public enum ChessPieceType {
    KING("king", 1.3f, 3.5f),
    QUEEN("queen", 1.3f, 3.5f),
    BISHOP("bishop", 1.2f, 3.0f),
    HORSE("horse", 1.2f, 2.4f),
    ROOK("rook", 1.2f, 2.4f),
    PAWN("pawn", 1.0f, 1.8f);

    private final String key;
    private final float interactionWidth;
    private final float interactionHeight;

    ChessPieceType(String key, float interactionWidth, float interactionHeight) {
        this.key = key;
        this.interactionWidth = interactionWidth;
        this.interactionHeight = interactionHeight;
    }

    public String key() {
        return key;
    }

    public float interactionWidth() {
        return interactionWidth;
    }

    public float interactionHeight() {
        return interactionHeight;
    }
}
