package krispasi.omGames.hallsofcarnage;

import java.util.List;
import java.util.Map;

public record HallsScenario(
        String id,
        String name,
        int ordering,
        String difficulty,
        String type,
        List<String> endlessLevelTypes,
        List<BossChoice> endlessBossPool,
        long endlessBossSeed,
        String endlessBossLayout,
        EndlessProgression endlessProgression,
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
        type = normalize(type);
        endlessLevelTypes = endlessLevelTypes == null ? List.of() : List.copyOf(endlessLevelTypes);
        endlessBossPool = endlessBossPool == null ? List.of() : List.copyOf(endlessBossPool);
        endlessBossLayout = endlessBossLayout == null ? "" : endlessBossLayout.trim();
        endlessProgression = endlessProgression == null ? EndlessProgression.defaults() : endlessProgression;
        allowedItems = Map.copyOf(allowedItems);
        blueprintPools = Map.copyOf(blueprintPools);
        levelTypeBlueprintPools = deepCopyBlueprintPools(levelTypeBlueprintPools);
        craftingStations = deepCopyCraftingStations(craftingStations);
        researchNodes = researchNodes == null ? Map.of() : Map.copyOf(researchNodes);
    }

    public boolean endless() { return "endless".equals(type); }

    public record BossChoice(String levelType, String boss) {
        public BossChoice { levelType = normalize(levelType); boss = normalize(boss); }
    }

    public record EndlessProgression(double startingDifficulty, double difficultyPerModule,
                                     double startingRooms, double roomsPerExploration, int maxRooms,
                                     double startingBreakables, double breakablesPerExploration,
                                     double startingTraps, double trapsPerExploration,
                                     double startingHoles, double holesPerExploration,
                                     double startingSculkPatches, double sculkPatchesPerExploration,
                                     double startingCoinQuota, double quotaPerExploration,
                                     int blueprintDistilleriesOnFinalExploration, int bossEveryModules) {
        public EndlessProgression { bossEveryModules = Math.max(1, bossEveryModules); }
        public static EndlessProgression defaults() {
            return new EndlessProgression(10.0, 2.0, 6.0, 0.62, 22, 24.0, 2.0,
                    7.0, 0.40, 5.0, 0.62, 1.0, 0.38, 16.0, 1.23, 5, 4);
        }
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
        if (endless() && floor > 1) return endlessFloor(floor, 1L);
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

    public FloorDefinition endlessFloor(int floor, long runSeed) {
        if (floor <= 1) {
            return new FloorDefinition(1, 1, "start", "howling_corridors", "5", 1, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, "special/start_floor.txt", "");
        }
        EndlessProgression p = endlessProgression;
        int modulesPerBoss = Math.max(1, p.bossEveryModules());
        int bossStart = modulesPerBoss * 4;
        int blockSize = bossStart + 2;
        int block = (floor - 2) / blockSize;
        int inBlock = (floor - 2) % blockSize;
        if (inBlock >= bossStart) {
            if (inBlock == bossStart && !endlessBossPool.isEmpty()) {
                java.util.Random random = new java.util.Random(endlessBossSeed + (long) block * 0x9E3779B97F4A7C15L);
                BossChoice choice = endlessBossPool.get(random.nextInt(endlessBossPool.size()));
                return new FloorDefinition(floor, floor, "combat", choice.levelType(), "40", 1, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, endlessBossLayout, choice.boss());
            }
            return new FloorDefinition(floor, floor, "camp", "howling_corridors", "0", 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, "", "");
        }
        int module = block * modulesPerBoss + inBlock / 4;
        int moduleFloor = inBlock % 4;
        if (moduleFloor == 3) return new FloorDefinition(floor, floor, "camp", "howling_corridors", "0",
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "", "");
        List<String> pool = endlessLevelTypes.isEmpty() ? List.of("howling_corridors") : endlessLevelTypes;
        java.util.List<String> shuffled = new java.util.ArrayList<>(pool);
        java.util.Collections.shuffle(shuffled, new java.util.Random(runSeed ^ (long) module * 0x9E3779B97F4A7C15L));
        String levelType = shuffled.get(moduleFloor % shuffled.size());
        int explorationIndex = module * 3 + moduleFloor;
        int difficulty = roundedProgression(p.startingDifficulty(), p.difficultyPerModule(), module);
        int rooms = Math.min(p.maxRooms(), roundedProgression(p.startingRooms(), p.roomsPerExploration(), explorationIndex));
        int breakables = roundedProgression(p.startingBreakables(), p.breakablesPerExploration(), explorationIndex);
        int traps = roundedProgression(p.startingTraps(), p.trapsPerExploration(), explorationIndex);
        int holes = roundedProgression(p.startingHoles(), p.holesPerExploration(), explorationIndex);
        int sculk = roundedProgression(p.startingSculkPatches(), p.sculkPatchesPerExploration(), explorationIndex);
        int quota = roundedProgression(p.startingCoinQuota(), p.quotaPerExploration(), explorationIndex);
        return new FloorDefinition(floor, floor, "exploration", levelType, Integer.toString(difficulty), rooms,
                0, breakables, traps, 1, 7, holes, sculk, quota,
                moduleFloor == 2 ? p.blueprintDistilleriesOnFinalExploration() : 0, "", "");
    }

    private static int roundedProgression(double startingValue, double perFloor, int floorIndex) {
        return Math.max(0, (int) Math.round(startingValue + perFloor * floorIndex));
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
            int blueprintDistilleries,
            String layout,
            String boss
    ) {
        public FloorDefinition {
            trappedRooms = Math.max(0, trappedRooms);
            minTrapsPerRoom = Math.max(0, minTrapsPerRoom);
            maxTrapsPerRoom = Math.max(minTrapsPerRoom, maxTrapsPerRoom);
            coinQuota = Math.max(0, coinQuota);
            blueprintDistilleries = Math.max(0, blueprintDistilleries);
            layout = layout == null ? "" : layout.trim();
            boss = normalize(boss);
        }

        public boolean includes(int floor) {
            return floor >= firstFloor && floor <= lastFloor;
        }

        public static FloorDefinition fallback(int floor) {
            return new FloorDefinition(floor, floor, "exploration", "howling_corridors", "0", 8, 0, 16, 3, 1, 2, 1, 2, 10, 0, "", "");
        }

        public FloorDefinition atFloor(int floor) {
            return new FloorDefinition(floor, floor, kind, levelType, difficulty, rooms, items, breakables,
                    trappedRooms, minTrapsPerRoom, maxTrapsPerRoom, holes, sculkPatches, coinQuota,
                    blueprintDistilleries, layout, boss);
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
