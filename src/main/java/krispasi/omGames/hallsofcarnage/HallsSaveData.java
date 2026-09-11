package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public record HallsSaveData(File file,
                            String scenarioId,
                            String difficultyId,
                            double difficultyMultiplier,
                            UUID hostId,
                            int currentFloor,
                            List<UUID> participants,
                            int woodScrap,
                            int ironScrap,
                            int diamondScrap,
                            int redstoneScrap,
                            int coins,
                            ItemStack[] elevatorChest,
                            Map<UUID, PlayerState> players,
                            Map<Integer, List<HallsCampRuntime.PlotState>> camps,
                            long savedAt) {
    public static HallsSaveData load(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String scenarioId = normalizeId(yaml.getString("scenario", ""));
        UUID hostId = parseUuid(yaml.getString("host", ""));
        List<UUID> participants = yaml.getStringList("participants").stream()
                .map(HallsSaveData::parseUuid)
                .filter(uuid -> uuid != null)
                .toList();
        if (scenarioId.isBlank() || hostId == null || participants.isEmpty()) {
            return null;
        }
        String difficultyId = normalizeId(yaml.getString("difficulty.id", "normal"));
        double difficultyMultiplier = Math.max(1.0, yaml.getDouble("difficulty.multiplier", 1.0));
        Map<UUID, PlayerState> players = new HashMap<>();
        for (UUID playerId : participants) {
            String path = "players." + playerId;
            players.put(playerId, new PlayerState(
                    yaml.getString(path + ".name", playerId.toString().substring(0, 8)),
                    yaml.getBoolean(path + ".ghost", false),
                    yaml.getDouble(path + ".sculk", 0.0),
                    itemArray(yaml.getList(path + ".hotbar"), 9),
                    itemArray(yaml.getList(path + ".armor"), 4),
                    item(yaml.get(path + ".offhand"))));
        }
        return new HallsSaveData(
                file,
                scenarioId,
                difficultyId.isBlank() ? "normal" : difficultyId,
                difficultyMultiplier,
                hostId,
                Math.max(1, yaml.getInt("current-floor", 1)),
                List.copyOf(participants),
                yaml.getInt("storage.wood", 0),
                yaml.getInt("storage.iron", 0),
                yaml.getInt("storage.diamond", 0),
                yaml.getInt("storage.redstone", 0),
                yaml.getInt("storage.coins", 0),
                itemArray(yaml.getList("elevator-chest"), 27),
                Map.copyOf(players),
                camps(yaml),
                yaml.getLong("saved-at", file.lastModified()));
    }

    public String displayName() {
        return scenarioId + " floor " + currentFloor;
    }

    private static Map<Integer, List<HallsCampRuntime.PlotState>> camps(YamlConfiguration yaml) {
        Map<Integer, List<HallsCampRuntime.PlotState>> camps = new LinkedHashMap<>();
        if (!yaml.isConfigurationSection("camps")) {
            return camps;
        }
        for (String key : yaml.getConfigurationSection("camps").getKeys(false)) {
            int floor;
            try {
                floor = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                continue;
            }
            List<HallsCampRuntime.PlotState> plots = new ArrayList<>();
            for (Map<?, ?> row : yaml.getMapList("camps." + key + ".plots")) {
                Object buildingValue = row.get("building");
                String building = normalizeId(buildingValue == null ? "" : String.valueOf(buildingValue));
                if (building.isBlank()) {
                    continue;
                }
                plots.add(new HallsCampRuntime.PlotState(
                        intValue(row.get("plot"), 0),
                        building,
                        intValue(row.get("level"), 1),
                        intValue(row.get("harvest-remaining"), 0),
                        intValue(row.get("harvest-used"), 0),
                        itemArray(listValue(row.get("storage")), 54)));
            }
            camps.put(floor, List.copyOf(plots));
        }
        return Map.copyOf(camps);
    }

    private static List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static ItemStack[] itemArray(List<?> rows, int size) {
        ItemStack[] items = new ItemStack[size];
        if (rows == null) {
            return items;
        }
        for (int i = 0; i < Math.min(size, rows.size()); i++) {
            items[i] = item(rows.get(i));
        }
        return items;
    }

    private static ItemStack item(Object value) {
        return value instanceof ItemStack item && !item.getType().isAir() ? item.clone() : null;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    public record PlayerState(String name,
                              boolean ghost,
                              double sculk,
                              ItemStack[] hotbar,
                              ItemStack[] armor,
                              ItemStack offhand) {
    }
}
