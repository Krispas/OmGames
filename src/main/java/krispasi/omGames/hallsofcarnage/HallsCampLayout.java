package krispasi.omGames.hallsofcarnage;

import java.util.List;
import org.bukkit.block.BlockFace;

public record HallsCampLayout(List<String> rows,
                              int width,
                              int depth,
                              List<BuildSpot> buildSpots) {
    public HallsCampLayout {
        rows = List.copyOf(rows);
        buildSpots = List.copyOf(buildSpots);
    }

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

    public boolean openAt(int x, int z) {
        return at(x, z) != 'X';
    }

    public record BuildSpot(int id,
                            int minX,
                            int maxX,
                            int minZ,
                            int maxZ,
                            int anchorX,
                            int anchorZ,
                            BlockFace facing,
                            String size) {
        public int centerX() {
            return (minX + maxX) / 2;
        }

        public int centerZ() {
            return (minZ + maxZ) / 2;
        }
    }
}
