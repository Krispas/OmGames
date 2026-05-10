package krispasi.omGames.bedwars.rotation;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class BedwarsRotationHistoryService {
    private final JavaPlugin plugin;
    private final Map<String, Integer> itemPickCounts = new HashMap<>();
    private final Map<String, Integer> upgradePickCounts = new HashMap<>();
    private File historyFile;

    public BedwarsRotationHistoryService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void load(File bedwarsDataFolder) {
        itemPickCounts.clear();
        upgradePickCounts.clear();
        historyFile = new File(bedwarsDataFolder, "rotating-history.yml");
        if (!historyFile.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(historyFile);
        loadSection(config.getConfigurationSection("items"), itemPickCounts);
        loadSection(config.getConfigurationSection("upgrades"), upgradePickCounts);
    }

    public synchronized void shutdown() {
        save();
    }

    public synchronized void ensureCandidates(Collection<String> itemCandidates, Collection<String> upgradeCandidates) {
        ensurePoolCandidates(itemPickCounts, itemCandidates);
        ensurePoolCandidates(upgradePickCounts, upgradeCandidates);
        save();
    }

    public synchronized int getItemPickCount(String itemId) {
        return itemPickCounts.getOrDefault(itemId, 0);
    }

    public synchronized int getUpgradePickCount(String upgradeId) {
        return upgradePickCounts.getOrDefault(upgradeId, 0);
    }

    public synchronized void recordItemPicks(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        incrementMany(itemPickCounts, ids);
        save();
    }

    public synchronized void recordUpgradePicks(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        incrementMany(upgradePickCounts, ids);
        save();
    }

    private void incrementMany(Map<String, Integer> target, Collection<String> ids) {
        for (String id : new LinkedHashSet<>(ids)) {
            if (id == null || id.isBlank()) {
                continue;
            }
            int current = target.getOrDefault(id, 0);
            target.put(id, current + 1);
        }
    }

    private void ensurePoolCandidates(Map<String, Integer> target, Collection<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        int min = target.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        Set<String> unique = new LinkedHashSet<>(candidates);
        for (String id : unique) {
            if (id == null || id.isBlank()) {
                continue;
            }
            target.putIfAbsent(id, min);
        }
    }

    private void loadSection(ConfigurationSection section, Map<String, Integer> target) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            int value = Math.max(0, section.getInt(key, 0));
            target.put(key, value);
        }
    }

    private void save() {
        if (historyFile == null) {
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        writeSection(config, "items", itemPickCounts);
        writeSection(config, "upgrades", upgradePickCounts);
        try {
            config.save(historyFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save rotating history.", ex);
        }
    }

    private void writeSection(YamlConfiguration config, String path, Map<String, Integer> values) {
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            config.set(path + "." + entry.getKey(), Math.max(0, entry.getValue()));
        }
    }
}
