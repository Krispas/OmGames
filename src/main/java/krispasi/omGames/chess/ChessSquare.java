package krispasi.omGames.chess;

public record ChessSquare(int file, int rank) {
    public ChessSquare {
        if (file < 0 || file > 7 || rank < 0 || rank > 7) {
            throw new IllegalArgumentException("Square out of bounds: " + file + "," + rank);
        }
    }

    public String notation() {
        char fileChar = (char) ('A' + file);
        return fileChar + Integer.toString(rank + 1);
    }

    public ChessSquare offset(int fileDelta, int rankDelta) {
        int nextFile = file + fileDelta;
        int nextRank = rank + rankDelta;
        if (nextFile < 0 || nextFile > 7 || nextRank < 0 || nextRank > 7) {
            return null;
        }
        return new ChessSquare(nextFile, nextRank);
    }

    public boolean isLightSquare() {
        return ((file + rank) & 1) == 1;
    }

    public static ChessSquare fromNotation(String notation) {
        if (notation == null || notation.length() != 2) {
            return null;
        }
        char fileChar = Character.toUpperCase(notation.charAt(0));
        char rankChar = notation.charAt(1);
        if (fileChar < 'A' || fileChar > 'H' || rankChar < '1' || rankChar > '8') {
            return null;
        }
        return new ChessSquare(fileChar - 'A', rankChar - '1');
    }
}
