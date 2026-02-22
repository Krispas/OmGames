package krispasi.omGames.bedwars.model;

/**
 * Bed head and foot block locations for a team.
 * <p>Used to detect bed destruction and respawn eligibility.</p>
 */
public record BedLocation(BlockPoint head, BlockPoint foot) {
}
