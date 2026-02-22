package krispasi.omGames.bedwars.model;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Compact integer block coordinate with parsing and conversion helpers.
 * <p>Stores positions independent of {@link org.bukkit.World} for config persistence.</p>
 * <p>Parsing expects three space-separated integers: {@code x y z}.</p>
 */
public record BlockPoint(int x, int y, int z) {
    public Location toLocation(World world) {
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    public static BlockPoint parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Missing location value");
        }
        String[] parts = value.trim().split("\\s+");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid location: " + value);
        }
        return new BlockPoint(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
        );
    }
}
