package krispasi.omGames.chess;

public enum ChessSide {
    WHITE("white", 1),
    BLACK("black", -1);

    private final String key;
    private final int pawnDirection;

    ChessSide(String key, int pawnDirection) {
        this.key = key;
        this.pawnDirection = pawnDirection;
    }

    public String key() {
        return key;
    }

    public int pawnDirection() {
        return pawnDirection;
    }

    public ChessSide opposite() {
        return this == WHITE ? BLACK : WHITE;
    }

    public static ChessSide fromKey(String key) {
        if (key == null) {
            return null;
        }
        for (ChessSide side : values()) {
            if (side.key.equalsIgnoreCase(key.trim())) {
                return side;
            }
        }
        return null;
    }
}
