package krispasi.omGames.bedwars.generator;

import krispasi.omGames.bedwars.model.BlockPoint;
import krispasi.omGames.bedwars.model.TeamColor;

public record GeneratorInfo(GeneratorType type, TeamColor team, BlockPoint location, String key) {
}
