package krispasi.omGames.bedwars.model;

import org.bukkit.Location;
import org.bukkit.World;

public record ShopLocation(BlockPoint point, float yaw) {
    public Location toLocation(World world) {
        return new Location(world, point.x() + 0.5, point.y(), point.z() + 0.5, yaw, 0.0f);
    }
}
