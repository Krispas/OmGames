package krispasi.omGames.hallsofcarnage;

import java.util.List;
import java.util.Map;

public record HallsScenario(
        String id,
        String name,
        String difficulty,
        List<String> description,
        int minPlayers,
        int maxPlayers,
        int floorCount,
        CampSettings camp,
        Map<String, List<String>> allowedItems,
        Map<String, List<String>> blueprintPools,
        Map<String, Map<String, List<String>>> levelTypeBlueprintPools,
        Map<String, Map<Integer, List<String>>> craftingStations,
        Map<String, HallsResearchNode> researchNodes,
        List<FloorDefinition> floors,
        List<String> debugLines
) {
    public HallsScenario {
        camp = camp == null ? CampSettings.defaults() : camp;
        allowedItems = Map.copyOf(allowedItems);
        blueprintPools = Map.copyOf(blueprintPools);
        levelTypeBlueprintPools = deepCopyBlueprintPools(levelTypeBlueprintPools);
        craftingStations = deepCopyCraftingStations(craftingStations);
        researchNodes = researchNodes == null ? Map.of() : Map.copyOf(researchNodes);
    }

    public record CampSettings(String layout, int teamLives, List<Integer> keyCosts) {
        public CampSettings {
            layout = layout == null ? "" : layout.trim();
            teamLives = Math.max(0, teamLives);
            keyCosts = keyCosts == null ? List.of() : keyCosts.stream()
                    .filter(cost -> cost != null && cost > 0)
                    .toList();
        }

        public static CampSettings defaults() {
            return new CampSettings("camps/camp_1.txt", 3, List.of(30, 40, 50));
        }

        public int nextKeyCost(int earnedKeys) {
            if (keyCosts.isEmpty()) {
                return 0;
            }
            return keyCosts.get(Math.min(Math.max(0, earnedKeys), keyCosts.size() - 1));
        }
    }

    public List<String> allowedItems(String category) {
        return allowedItems.getOrDefault(category, List.of());
    }

    public List<String> blueprintPool(String rarity) {
        return blueprintPools.getOrDefault(rarity, List.of());
    }

    public List<String> blueprintPool(String rarity, String levelType) {
        String normalizedLevelType = normalize(levelType);
        Map<String, List<String>> levelPools = levelTypeBlueprintPools.get(normalizedLevelType);
        if (levelPools == null) {
            return blueprintPool(rarity);
        }
        List<String> ids = levelPools.getOrDefault(normalize(rarity), List.of());
        return ids.isEmpty() ? blueprintPool(rarity) : ids;
    }

    public List<String> craftingRecipes(String stationId, int level) {
        Map<Integer, List<String>> levels = craftingStations.getOrDefault(normalize(stationId), Map.of());
        List<String> recipes = new java.util.ArrayList<>();
        int cappedLevel = Math.max(1, Math.min(3, level));
        for (int current = 1; current <= cappedLevel; current++) {
            recipes.addAll(levels.getOrDefault(current, List.of()));
        }
        return List.copyOf(recipes);
    }

    public List<String> rootResearchNodes() {
        return researchNodes.values().stream()
                .filter(HallsResearchNode::root)
                .map(HallsResearchNode::id)
                .toList();
    }

    public HallsResearchNode researchNode(String nodeId) {
        return researchNodes.get(normalize(nodeId));
    }

    public HallsResearchNode researchNodeForItem(String itemId) {
        String normalized = normalize(itemId);
        for (HallsResearchNode node : researchNodes.values()) {
            if (node.unlocks().contains(normalized)) {
                return node;
            }
        }
        return null;
    }

    public boolean usesResearch() {
        return !researchNodes.isEmpty();
    }

    public FloorDefinition floor(int floor) {
        for (FloorDefinition definition : floors) {
            if (definition.includes(floor)) {
                return definition;
            }
        }
        FloorDefinition nearestPriorExploration = null;
        for (FloorDefinition definition : floors) {
            if (definition.firstFloor() <= floor && definition.kind().equalsIgnoreCase("exploration")) {
                nearestPriorExploration = definition;
            }
        }
        if (nearestPriorExploration != null) {
            return nearestPriorExploration.atFloor(floor);
        }
        return FloorDefinition.fallback(floor);
    }

    public record FloorDefinition(
            int firstFloor,
            int lastFloor,
            String kind,
            String levelType,
            String difficulty,
            int rooms,
            int items,
            int breakables,
            int trappedRooms,
            int minTrapsPerRoom,
            int maxTrapsPerRoom,
            int holes,
            int sculkPatches,
            int coinQuota,
            String layout,
            String boss
    ) {
        public FloorDefinition {
            trappedRooms = Math.max(0, trappedRooms);
            minTrapsPerRoom = Math.max(0, minTrapsPerRoom);
            maxTrapsPerRoom = Math.max(minTrapsPerRoom, maxTrapsPerRoom);
            coinQuota = Math.max(0, coinQuota);
            layout = layout == null ? "" : layout.trim();
            boss = normalize(boss);
        }

        public boolean includes(int floor) {
            return floor >= firstFloor && floor <= lastFloor;
        }

        public static FloorDefinition fallback(int floor) {
            return new FloorDefinition(floor, floor, "exploration", "howling_corridors", "0", 8, 0, 16, 3, 1, 2, 1, 2, 10, "", "");
        }

        public FloorDefinition atFloor(int floor) {
            return new FloorDefinition(floor, floor, kind, levelType, difficulty, rooms, items, breakables,
                    trappedRooms, minTrapsPerRoom, maxTrapsPerRoom, holes, sculkPatches, coinQuota, layout, boss);
        }
    }

    private static Map<String, Map<Integer, List<String>>> deepCopyCraftingStations(
            Map<String, Map<Integer, List<String>>> source) {
        Map<String, Map<Integer, List<String>>> copy = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Map<Integer, List<String>>> station : source.entrySet()) {
            Map<Integer, List<String>> levels = new java.util.LinkedHashMap<>();
            for (Map.Entry<Integer, List<String>> level : station.getValue().entrySet()) {
                levels.put(level.getKey(), List.copyOf(level.getValue()));
            }
            copy.put(normalize(station.getKey()), Map.copyOf(levels));
        }
        return Map.copyOf(copy);
    }

    private static Map<String, Map<String, List<String>>> deepCopyBlueprintPools(
            Map<String, Map<String, List<String>>> source) {
        Map<String, Map<String, List<String>>> copy = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Map<String, List<String>>> levelType : source.entrySet()) {
            Map<String, List<String>> pools = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, List<String>> pool : levelType.getValue().entrySet()) {
                pools.put(normalize(pool.getKey()), List.copyOf(pool.getValue()));
            }
            copy.put(normalize(levelType.getKey()), Map.copyOf(pools));
        }
        return Map.copyOf(copy);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
