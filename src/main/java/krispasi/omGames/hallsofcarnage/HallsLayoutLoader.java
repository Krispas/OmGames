package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public final class HallsLayoutLoader {
    private HallsLayoutLoader() {
    }

    public static HallsLayout load(File file) throws IOException {
        List<String> rows = Files.readAllLines(file.toPath()).stream()
                .map(String::stripTrailing)
                .filter(line -> !line.isBlank())
                .toList();
        rows = stripLegacyOuterWall(rows).stream()
                .map(HallsLayoutLoader::normalizeCells)
                .toList();
        int width = rows.stream().mapToInt(String::length).max().orElse(0);
        return new HallsLayout(rows, width, rows.size());
    }

    private static List<String> stripLegacyOuterWall(List<String> rows) {
        if (rows.size() < 3 || rows.stream().anyMatch(String::isBlank)) {
            return rows;
        }
        int width = rows.stream().mapToInt(String::length).min().orElse(0);
        if (width < 3 || !isSolidWall(rows.getFirst(), width) || !isSolidWall(rows.getLast(), width)) {
            return rows;
        }
        for (String row : rows) {
            if (row.length() < width || row.charAt(0) != 'X' || row.charAt(width - 1) != 'X') {
                return rows;
            }
        }
        return rows.subList(1, rows.size() - 1).stream()
                .map(row -> row.substring(1, width - 1))
                .toList();
    }

    private static boolean isSolidWall(String row, int width) {
        if (row.length() < width) {
            return false;
        }
        for (int i = 0; i < width; i++) {
            if (row.charAt(i) != 'X') {
                return false;
            }
        }
        return true;
    }

    private static String normalizeCells(String row) {
        StringBuilder builder = new StringBuilder(row.length());
        for (int i = 0; i < row.length(); i++) {
            builder.append(row.charAt(i) == 'X' ? 'X' : 'O');
        }
        return builder.toString();
    }
}
