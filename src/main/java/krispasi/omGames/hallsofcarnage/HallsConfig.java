package krispasi.omGames.hallsofcarnage;

import java.io.File;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

public final class HallsConfig {
    private static final String DEFAULT_WORLD = "om:halls_of_carnage";

    private final String lobbyWorldName;
    private final LocationValues lobbySpawn;
    private final LocationValues menuVillager;
    private final boolean menuVillagerEnabled;
    private final BlockPoint firstSessionOrigin;
    private final int sessionSpacing;
    private final int maxPlayers;
    private final int disconnectGraceSeconds;

    private HallsConfig(String lobbyWorldName,
                        LocationValues lobbySpawn,
                        LocationValues menuVillager,
                        boolean menuVillagerEnabled,
                        BlockPoint firstSessionOrigin,
                        int sessionSpacing,
                        int maxPlayers,
                        int disconnectGraceSeconds) {
        this.lobbyWorldName = lobbyWorldName;
        this.lobbySpawn = lobbySpawn;
        this.menuVillager = menuVillager;
        this.menuVillagerEnabled = menuVillagerEnabled;
        this.firstSessionOrigin = firstSessionOrigin;
        this.sessionSpacing = sessionSpacing;
        this.maxPlayers = maxPlayers;
        this.disconnectGraceSeconds = disconnectGraceSeconds;
    }

    public static HallsConfig load(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String world = config.getString("lobby.world", DEFAULT_WORLD);
        LocationValues spawn = readLocation(config, "lobby.spawn", new LocationValues(0.5, 70.0, 0.5, 0.0f, 0.0f));
        LocationValues villager = readLocation(config, "lobby.menu-villager", spawn);
        boolean villagerEnabled = config.getBoolean("lobby.menu-villager.enabled", false);
        BlockPoint firstOrigin = new BlockPoint(
                config.getInt("sessions.first-origin.x", 2000),
                config.getInt("sessions.first-origin.y", 70),
                config.getInt("sessions.first-origin.z", 0)
        );
        int spacing = Math.max(250, config.getInt("sessions.spacing", 1000));
        int maxPlayers = clamp(config.getInt("sessions.max-players", 6), 1, 6);
        int graceSeconds = Math.max(1, config.getInt("sessions.disconnect-grace-seconds", 300));
        return new HallsConfig(world, spawn, villager, villagerEnabled, firstOrigin, spacing, maxPlayers, graceSeconds);
    }

    public String lobbyWorldName() {
        return lobbyWorldName;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public int disconnectGraceSeconds() {
        return disconnectGraceSeconds;
    }

    public BlockPoint sessionOrigin(int slot) {
        int safeSlot = Math.max(0, slot);
        return new BlockPoint(firstSessionOrigin.x() + safeSlot * sessionSpacing, firstSessionOrigin.y(), firstSessionOrigin.z());
    }

    public boolean menuVillagerEnabled() {
        return menuVillagerEnabled;
    }

    public Location lobbySpawn() {
        return lobbySpawn.toLocation(resolveLobbyWorld());
    }

    public Location menuVillagerLocation() {
        return menuVillager.toLocation(resolveLobbyWorld());
    }

    public World resolveLobbyWorld() {
        World world = Bukkit.getWorld(lobbyWorldName);
        if (world != null) {
            return world;
        }
        NamespacedKey key = NamespacedKey.fromString(lobbyWorldName);
        return key == null ? null : Bukkit.getWorld(key);
    }

    private static LocationValues readLocation(YamlConfiguration config, String path, LocationValues fallback) {
        double x = config.getDouble(path + ".x", fallback.x());
        double y = config.getDouble(path + ".y", fallback.y());
        double z = config.getDouble(path + ".z", fallback.z());
        float yaw = (float) config.getDouble(path + ".yaw", fallback.yaw());
        float pitch = (float) config.getDouble(path + ".pitch", fallback.pitch());
        return new LocationValues(x, y, z, yaw, pitch);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record LocationValues(double x, double y, double z, float yaw, float pitch) {
        Location toLocation(World world) {
            return world == null ? null : new Location(world, x, y, z, yaw, pitch);
        }
    }

    public record BlockPoint(int x, int y, int z) {
    }
}
