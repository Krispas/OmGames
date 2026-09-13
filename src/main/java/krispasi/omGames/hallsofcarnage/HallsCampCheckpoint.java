package krispasi.omGames.hallsofcarnage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public record HallsCampCheckpoint(int floor,
                                  int woodScrap,
                                  int ironScrap,
                                  int diamondScrap,
                                  int redstoneScrap,
                                  int coins,
                                  int campBankCoins,
                                  int campKeys,
                                  int campKeysEarned,
                                  int remainingLives,
                                  int lastCampFloor,
                                  ItemStack[] elevatorChest,
                                  Map<UUID, HallsSaveData.PlayerState> players,
                                  Map<Integer, List<HallsCampRuntime.PlotState>> camps,
                                  Map<Integer, Set<Integer>> campUnlockedDoors) {
    public static HallsCampCheckpoint fromSaveData(HallsSaveData.LastCampCheckpoint checkpoint) {
        if (checkpoint == null) {
            return null;
        }
        return new HallsCampCheckpoint(
                checkpoint.floor(),
                checkpoint.woodScrap(),
                checkpoint.ironScrap(),
                checkpoint.diamondScrap(),
                checkpoint.redstoneScrap(),
                checkpoint.coins(),
                checkpoint.campBankCoins(),
                checkpoint.campKeys(),
                checkpoint.campKeysEarned(),
                checkpoint.remainingLives(),
                checkpoint.lastCampFloor(),
                cloneArray(checkpoint.elevatorChest(), 27),
                Map.copyOf(checkpoint.players()),
                copyCampStates(checkpoint.camps()),
                copyCampDoors(checkpoint.campUnlockedDoors()));
    }

    public void save(YamlConfiguration yaml) {
        String path = "last-camp-checkpoint";
        yaml.set(path + ".floor", floor);
        yaml.set(path + ".storage.wood", woodScrap);
        yaml.set(path + ".storage.iron", ironScrap);
        yaml.set(path + ".storage.diamond", diamondScrap);
        yaml.set(path + ".storage.redstone", redstoneScrap);
        yaml.set(path + ".storage.coins", coins);
        yaml.set(path + ".camp-bank.coins", campBankCoins);
        yaml.set(path + ".camp-bank.keys", campKeys);
        yaml.set(path + ".camp-bank.keys-earned", campKeysEarned);
        yaml.set(path + ".team-lives.remaining", remainingLives);
        yaml.set(path + ".team-lives.last-camp-floor", lastCampFloor);
        yaml.set(path + ".elevator-chest", java.util.Arrays.asList(elevatorChest));
        savePlayers(yaml, path + ".players", players);
        saveCamps(yaml, path + ".camps", camps, campUnlockedDoors);
    }

    public static Map<Integer, List<HallsCampRuntime.PlotState>> copyCampStates(
            Map<Integer, List<HallsCampRuntime.PlotState>> source) {
        Map<Integer, List<HallsCampRuntime.PlotState>> copy = new HashMap<>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<Integer, List<HallsCampRuntime.PlotState>> entry : source.entrySet()) {
            List<HallsCampRuntime.PlotState> states = new ArrayList<>();
            for (HallsCampRuntime.PlotState state : entry.getValue()) {
                states.add(new HallsCampRuntime.PlotState(
                        state.plotId(),
                        state.buildingId(),
                        state.level(),
                        state.harvestRemaining(),
                        state.harvestUsed(),
                        cloneArray(state.storageContents(), 54)));
            }
            copy.put(entry.getKey(), List.copyOf(states));
        }
        return copy;
    }

    public static Map<Integer, Set<Integer>> copyCampDoors(Map<Integer, Set<Integer>> source) {
        Map<Integer, Set<Integer>> copy = new HashMap<>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<Integer, Set<Integer>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return copy;
    }

    private static void savePlayers(YamlConfiguration yaml,
                                    String root,
                                    Map<UUID, HallsSaveData.PlayerState> states) {
        for (Map.Entry<UUID, HallsSaveData.PlayerState> entry : states.entrySet()) {
            String path = root + "." + entry.getKey();
            HallsSaveData.PlayerState state = entry.getValue();
            yaml.set(path + ".name", state.name());
            yaml.set(path + ".ghost", state.ghost());
            yaml.set(path + ".sculk", state.sculk());
            yaml.set(path + ".health-totem-level", state.healthTotemLevel());
            yaml.set(path + ".speed-totem-level", state.speedTotemLevel());
            yaml.set(path + ".hotbar", java.util.Arrays.asList(cloneArray(state.hotbar(), 9)));
            yaml.set(path + ".armor", java.util.Arrays.asList(cloneArray(state.armor(), 4)));
            yaml.set(path + ".offhand", cloneOrNull(state.offhand()));
        }
    }

    private static void saveCamps(YamlConfiguration yaml,
                                  String root,
                                  Map<Integer, List<HallsCampRuntime.PlotState>> campStates,
                                  Map<Integer, Set<Integer>> unlockedDoorStates) {
        Set<Integer> campKeys = new java.util.HashSet<>();
        campKeys.addAll(campStates.keySet());
        campKeys.addAll(unlockedDoorStates.keySet());
        for (Integer campKey : campKeys) {
            List<Map<String, Object>> plots = new ArrayList<>();
            for (HallsCampRuntime.PlotState state : campStates.getOrDefault(campKey, List.of())) {
                Map<String, Object> row = new HashMap<>();
                row.put("plot", state.plotId());
                row.put("building", state.buildingId());
                row.put("level", state.level());
                row.put("harvest-remaining", state.harvestRemaining());
                row.put("harvest-used", state.harvestUsed());
                row.put("storage", java.util.Arrays.asList(cloneArray(state.storageContents(), 54)));
                plots.add(row);
            }
            yaml.set(root + "." + campKey + ".plots", plots);
            Set<Integer> unlockedDoors = unlockedDoorStates.getOrDefault(campKey, Set.of());
            if (!unlockedDoors.isEmpty()) {
                yaml.set(root + "." + campKey + ".unlocked-doors", unlockedDoors.stream().sorted().toList());
            }
        }
    }

    private static ItemStack[] cloneArray(ItemStack[] source, int size) {
        ItemStack[] copy = new ItemStack[size];
        if (source == null) {
            return copy;
        }
        for (int i = 0; i < Math.min(source.length, size); i++) {
            copy[i] = cloneOrNull(source[i]);
        }
        return copy;
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }
}
