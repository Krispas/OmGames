package krispasi.omGames.hallsofcarnage;

import java.util.List;
import org.bukkit.Material;

public record HallsBreakableType(String id,
                                 String rarity,
                                 String breakMessage,
                                 float hitboxHeight,
                                 Material particleMaterial,
                                 List<String> scrapDrops,
                                 List<Part> parts,
                                 List<LootEntry> loot) {
    public HallsBreakableType {
        scrapDrops = List.copyOf(scrapDrops);
        parts = List.copyOf(parts);
        loot = List.copyOf(loot);
    }

    public record Part(int offsetX, int offsetY, int offsetZ, Material material) {
    }

    public record LootEntry(String item, int weight, int minAmount, int maxAmount) {
        public LootEntry {
            weight = Math.max(1, weight);
            minAmount = Math.max(1, minAmount);
            maxAmount = Math.max(minAmount, maxAmount);
        }
    }
}
