package krispasi.omGames.egghunt;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record EggHuntPoint(String worldName, double x, double y, double z) {
    public static EggHuntPoint fromLocation(Location location) {
        return new EggHuntPoint(
                location.getWorld().getName(),
                location.getBlockX() + 0.5,
                location.getBlockY(),
                location.getBlockZ() + 0.5
        );
    }

    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z);
    }
}
