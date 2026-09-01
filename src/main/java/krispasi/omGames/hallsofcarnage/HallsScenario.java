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
        Map<String, List<String>> allowedItems,
        Map<String, List<String>> blueprintPools,
        List<FloorDefinition> floors,
        List<String> debugLines
) {
    public HallsScenario {
        allowedItems = Map.copyOf(allowedItems);
        blueprintPools = Map.copyOf(blueprintPools);
    }

    public List<String> allowedItems(String category) {
        return allowedItems.getOrDefault(category, List.of());
    }

    public List<String> blueprintPool(String rarity) {
        return blueprintPools.getOrDefault(rarity, List.of());
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
            int traps
    ) {
        public boolean includes(int floor) {
            return floor >= firstFloor && floor <= lastFloor;
        }

        public static FloorDefinition fallback(int floor) {
            return new FloorDefinition(floor, floor, "exploration", "howling_corridors", "0", 8, 0, 16, 5);
        }

        public FloorDefinition atFloor(int floor) {
            return new FloorDefinition(floor, floor, kind, levelType, difficulty, rooms, items, breakables, traps);
        }
    }
}
