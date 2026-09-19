package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.io.IOException;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

final class HallsMapIdStore {
    private HallsMapIdStore() {
    }

    static MapView floorMap(JavaPlugin plugin, File dataFolder, World world, String key) {
        if (plugin == null || world == null || key == null || key.isBlank()) {
            return world == null ? null : Bukkit.createMap(world);
        }
        File file = new File(dataFolder, "map-ids.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String path = "maps." + key;
        if (yaml.isInt(path)) {
            MapView existing = Bukkit.getMap(yaml.getInt(path));
            if (existing != null) {
                return existing;
            }
        }
        MapView created = Bukkit.createMap(world);
        yaml.set(path, created.getId());
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save Halls map id " + created.getId() + ": " + ex.getMessage());
        }
        return created;
    }
}
