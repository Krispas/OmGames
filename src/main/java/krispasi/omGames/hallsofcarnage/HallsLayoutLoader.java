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
        int width = rows.stream().mapToInt(String::length).max().orElse(0);
        return new HallsLayout(rows, width, rows.size());
    }
}
