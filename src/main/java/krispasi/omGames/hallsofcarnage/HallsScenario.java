package krispasi.omGames.hallsofcarnage;

import java.util.List;

public record HallsScenario(
        String id,
        String name,
        String difficulty,
        List<String> description,
        int minPlayers,
        int maxPlayers,
        int floorCount,
        List<FloorDefinition> floors
) {
    public FloorDefinition floor(int floor) {
        for (FloorDefinition definition : floors) {
            if (definition.includes(floor)) {
                return definition;
            }
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
            int breakables
    ) {
        public boolean includes(int floor) {
            return floor >= firstFloor && floor <= lastFloor;
        }

        public static FloorDefinition fallback(int floor) {
            return new FloorDefinition(floor, floor, "exploration", "howling_corridors", "0", 8, 0, 16);
        }
    }
}
