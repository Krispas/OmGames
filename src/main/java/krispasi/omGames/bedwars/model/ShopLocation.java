package krispasi.omGames.bedwars.model;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Shop NPC position and facing yaw.
 * <p>Converted to a Bukkit {@link org.bukkit.Location} for spawning shop entities.</p>
 */
public record ShopLocation(BlockPoint point, float yaw) {
    public Location toLocation(World world) {
        return new Location(world, point.x() + 0.5, point.y(), point.z() + 0.5, yaw, 0.0f);
    }
}
