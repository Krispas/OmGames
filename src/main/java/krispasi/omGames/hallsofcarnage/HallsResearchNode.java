package krispasi.omGames.hallsofcarnage;

import java.util.List;
import org.bukkit.Material;

public record HallsResearchNode(String id,
                                String name,
                                Material icon,
                                int cost,
                                int row,
                                int column,
                                List<String> prerequisites,
                                List<String> unlocks) {
    public HallsResearchNode {
        id = normalize(id);
        name = name == null || name.isBlank() ? id.replace('_', ' ') : name;
        icon = icon == null ? Material.BOOK : icon;
        cost = Math.max(0, cost);
        row = Math.max(0, Math.min(5, row));
        column = Math.max(0, Math.min(9, column));
        prerequisites = prerequisites == null ? List.of() : prerequisites.stream()
                .map(HallsResearchNode::normalize)
                .filter(value -> !value.isBlank())
                .toList();
        unlocks = unlocks == null ? List.of() : unlocks.stream()
                .map(HallsResearchNode::normalize)
                .filter(value -> !value.isBlank())
                .toList();
    }

    public boolean root() {
        return prerequisites.isEmpty();
    }

    public boolean hasGridPosition() {
        return row > 0 && column > 0;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }
}
