package krispasi.omGames.hallsofcarnage;

import java.util.List;

public record HallsScenario(
        String id,
        String name,
        String difficulty,
        List<String> description,
        int minPlayers,
        int maxPlayers,
        int floorCount
) {
}
