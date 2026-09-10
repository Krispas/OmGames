package krispasi.omGames.hallsofcarnage;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;

public record HallsBuildingType(String id,
                                String name,
                                String size,
                                String blueprint,
                                boolean implemented,
                                Map<Integer, Level> levels) {
    public HallsBuildingType {
        id = normalize(id);
        size = normalize(size);
        blueprint = normalize(blueprint);
        levels = Map.copyOf(levels);
    }

    public Level level(int level) {
        return levels.getOrDefault(Math.max(1, Math.min(3, level)), Level.empty());
    }

    public boolean fits(String plotSize) {
        int building = sizeRank(size);
        int plot = sizeRank(plotSize);
        return building > 0 && plot >= building;
    }

    public static int sizeRank(String size) {
        return switch (normalize(size)) {
            case "small" -> 1;
            case "medium" -> 2;
            case "large" -> 3;
            default -> 0;
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    public record Level(List<Part> parts,
                        Map<String, Integer> upgradeCost,
                        List<String> giveItems) {
        public Level {
            parts = List.copyOf(parts);
            upgradeCost = Map.copyOf(upgradeCost);
            giveItems = List.copyOf(giveItems);
        }

        public static Level empty() {
            return new Level(List.of(), Map.of(), List.of());
        }
    }

    public record Part(Material material,
                       double offsetX,
                       double offsetY,
                       double offsetZ,
                       double scaleX,
                       double scaleY,
                       double scaleZ) {
    }
}
