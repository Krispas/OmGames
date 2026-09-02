package krispasi.omGames.hallsofcarnage;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;

public record HallsItemType(String id,
                            String name,
                            String category,
                            String rarity,
                            Material material,
                            String itemModel,
                            String armorModel,
                            int maxStackSize,
                            List<String> lore,
                            Map<String, Double> stats,
                            Map<String, Integer> recipe) {
    public HallsItemType {
        lore = List.copyOf(lore);
        stats = Map.copyOf(stats);
        recipe = Map.copyOf(recipe);
    }
}
