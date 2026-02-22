package krispasi.omGames.bedwars.generator;

import krispasi.omGames.bedwars.model.BlockPoint;
import krispasi.omGames.bedwars.model.TeamColor;

/**
 * Immutable descriptor of a generator placement.
 * <p>Stores {@link krispasi.omGames.bedwars.generator.GeneratorType}, owning
 * {@link krispasi.omGames.bedwars.model.TeamColor}, location, and config key.</p>
 * @see krispasi.omGames.bedwars.generator.GeneratorManager
 */
public record GeneratorInfo(GeneratorType type, TeamColor team, BlockPoint location, String key) {
}
