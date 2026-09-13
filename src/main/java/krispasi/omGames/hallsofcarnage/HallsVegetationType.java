package krispasi.omGames.hallsofcarnage;

import org.bukkit.Material;

public record HallsVegetationType(String id,
                                  Material material,
                                  String blockData,
                                  double offsetY,
                                  float scale,
                                  boolean randomYaw) {
    public HallsVegetationType {
        scale = Math.max(0.1f, scale);
    }
}
