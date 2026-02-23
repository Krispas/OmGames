package krispasi.omGames.bedwars.game;

/**
 * Lifecycle states for a {@link krispasi.omGames.bedwars.game.GameSession}.
 * <p>Used to gate logic between idle, starting, running, and ending phases.</p>
 */
public enum GameState {
    IDLE,
    LOBBY,
    STARTING,
    RUNNING,
    ENDING
}
