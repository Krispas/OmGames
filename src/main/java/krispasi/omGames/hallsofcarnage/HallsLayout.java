package krispasi.omGames.hallsofcarnage;

import java.util.List;

public record HallsLayout(List<String> rows, int width, int depth) {
    public char at(int x, int z) {
        if (z < 0 || z >= rows.size()) {
            return 'X';
        }
        String row = rows.get(z);
        if (x < 0 || x >= row.length()) {
            return 'X';
        }
        return row.charAt(x);
    }
}
