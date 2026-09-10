package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.block.BlockFace;

public final class HallsCampLayoutLoader {
    private HallsCampLayoutLoader() {
    }

    public static HallsCampLayout load(File file) throws IOException {
        List<String> rows = Files.readAllLines(file.toPath()).stream()
                .map(String::stripTrailing)
                .filter(line -> !line.isBlank())
                .map(line -> line.toUpperCase(Locale.ROOT))
                .toList();
        int width = rows.stream().mapToInt(String::length).max().orElse(0);
        List<String> padded = rows.stream()
                .map(row -> row + "X".repeat(Math.max(0, width - row.length())))
                .toList();
        return new HallsCampLayout(padded, width, padded.size(), buildSpots(padded, width));
    }

    private static List<HallsCampLayout.BuildSpot> buildSpots(List<String> rows, int width) {
        Set<Cell> visited = new HashSet<>();
        List<HallsCampLayout.BuildSpot> spots = new ArrayList<>();
        int id = 1;
        for (int z = 0; z < rows.size(); z++) {
            for (int x = 0; x < width; x++) {
                char cell = rows.get(z).charAt(x);
                if (!isPlotCell(cell) || visited.contains(new Cell(x, z))) {
                    continue;
                }
                List<Cell> component = flood(rows, width, new Cell(x, z), visited);
                HallsCampLayout.BuildSpot spot = spot(id, component, rows);
                if (spot != null) {
                    spots.add(spot);
                    id++;
                }
            }
        }
        return List.copyOf(spots);
    }

    private static List<Cell> flood(List<String> rows, int width, Cell start, Set<Cell> visited) {
        List<Cell> cells = new ArrayList<>();
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            Cell cell = queue.removeFirst();
            cells.add(cell);
            for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
                Cell next = new Cell(cell.x() + face.getModX(), cell.z() + face.getModZ());
                if (next.z() < 0 || next.z() >= rows.size() || next.x() < 0 || next.x() >= width
                        || visited.contains(next) || !isPlotCell(rows.get(next.z()).charAt(next.x()))) {
                    continue;
                }
                visited.add(next);
                queue.addLast(next);
            }
        }
        return cells;
    }

    private static HallsCampLayout.BuildSpot spot(int id, List<Cell> cells, List<String> rows) {
        if (cells.isEmpty()) {
            return null;
        }
        int minX = cells.stream().mapToInt(Cell::x).min().orElse(0);
        int maxX = cells.stream().mapToInt(Cell::x).max().orElse(minX);
        int minZ = cells.stream().mapToInt(Cell::z).min().orElse(0);
        int maxZ = cells.stream().mapToInt(Cell::z).max().orElse(minZ);
        Cell anchor = cells.stream()
                .filter(cell -> isFacingMarker(rows.get(cell.z()).charAt(cell.x())))
                .findFirst()
                .orElse(new Cell((minX + maxX) / 2, (minZ + maxZ) / 2));
        BlockFace facing = face(rows.get(anchor.z()).charAt(anchor.x()));
        int span = Math.max(maxX - minX + 1, maxZ - minZ + 1);
        String size = span >= 5 ? "large" : span >= 3 ? "medium" : "small";
        return new HallsCampLayout.BuildSpot(id, minX, maxX, minZ, maxZ, anchor.x(), anchor.z(), facing, size);
    }

    private static boolean isPlotCell(char cell) {
        return cell == 'C' || isFacingMarker(cell);
    }

    private static boolean isFacingMarker(char cell) {
        return cell == 'N' || cell == 'S' || cell == 'E' || cell == 'W';
    }

    private static BlockFace face(char marker) {
        return switch (marker) {
            case 'S' -> BlockFace.SOUTH;
            case 'E' -> BlockFace.EAST;
            case 'W' -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
    }

    private record Cell(int x, int z) {
    }
}
