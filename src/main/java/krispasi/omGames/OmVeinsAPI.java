package krispasi.omGames;

import krispasi.omGames.shared.SKIN_TYPE;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Static bridge API used to connect OmGames with the OmVeins plugin.
 *
 * <p>This class acts as a lightweight runtime integration layer. It allows
 * OmVeins to register functional callbacks (via {@link BiConsumer}) that
 * OmGames can invoke without directly depending on OmVeins' internal classes.</p>
 *
 * <p>The API must be fully initialized before use. Initialization is considered
 * complete once all required consumers are provided. If an API method is called
 * before initialization, an {@link IllegalStateException} will be thrown.</p>
 *
 * <p>Do not use this API during server startup!</p>
 *
 * <p>Thread-safety: This class is not explicitly thread-safe and is expected
 * to be used from the main server thread.</p>
 */
public class OmVeinsAPI {

    /**
     * Indicates whether the API has received all required consumers
     * and is ready for use.
     */
    private static boolean initialized = false;

    /**
     * Consumer responsible for handling party experience addition.
     *
     * <p>Arguments:
     * <ul>
     *     <li>{@link Player} - the player receiving party experience</li>
     *     <li>{@link Integer} - amount of experience to add</li>
     * </ul>
     * </p>
     */
    private static BiConsumer<Player, Integer> addPartyExpConsumer;

    /**
     * Function responsible for returning a player's available Om skins,
     * grouped and sorted by {@link SKIN_TYPE}.
     *
     * <p>Each entry is a {@link Pair} of {@code (left, right)} parts of the skin ID.
     * The left part is always present and is the full skin ID. The right part is
     * optional and is only populated when the skin is an Equipable; in that case
     * it contains the right-side token of the equipable ID so that
     * {@code left} is {@code "om:" + right}.</p>
     */
    private static Function<Player, Map<SKIN_TYPE, ArrayList<Pair<String, String>>>> getPlayerSkinsFunction;

    /**
     * Checks whether all required consumers are set.
     * If so, marks the API as initialized and logs the state.
     */
    private static void checkIfDone() {
        if (addPartyExpConsumer == null) return;
        if (getPlayerSkinsFunction == null) return;

        initialized = true;
        OmGames.getInstance().getLogger().info("OmVeins API: Fully initialized!");
    }

    /**
     * Returns whether the API is fully initialized and safe to use.
     *
     * @return {@code true} if all required consumers are set,
     *         {@code false} otherwise.
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Registers the consumer responsible for adding party experience.
     *
     * <p>This method should be called by the OmVeins plugin during its
     * initialization phase.</p>
     *
     * @param consumer the {@link BiConsumer} that handles adding party
     *                 experience to a player
     */
    public static void setAddPartyExpConsumer(BiConsumer<Player, Integer> consumer) {
        addPartyExpConsumer = consumer;
        OmGames.getInstance().getLogger().info("OmVeins API: AddPartyExp consumer set!");
        checkIfDone();
    }

    /**
     * Adds party experience to the specified player via the registered consumer.
     *
     * <p>Do not use often as it requires database query. Tip: instead of multiple executions per tick,
     * try one with summed up exp amount.</p>
     *
     * <p>This method will throw an exception if the API has not been
     * initialized yet.</p>
     *
     * <p>If the underlying consumer throws an exception, it will be caught
     * and logged at {@link Level#SEVERE} without propagating further.</p>
     *
     * @param player the player who should receive party experience
     * @param amount the amount of experience to add
     *
     * @throws IllegalStateException if the API has not been initialized
     */
    public static void addPartyExp(Player player, Integer amount) {
        if (!initialized) {
            notInitializedWarning();
        }

        try {
            addPartyExpConsumer.accept(player, amount);
        } catch (Exception e) {
            OmGames.getInstance().getLogger()
                    .log(Level.SEVERE,
                            "OmVeins API: error while executing AddPartyExp!", e);
        }
    }

    /**
     * Registers the function responsible for returning a player's available Om skins.
     *
     * <p>The function must return a map grouped by {@link SKIN_TYPE}. Each value is a list of
     * {@link Pair} entries {@code (left, right)} representing the skin ID.</p>
     *
     * <p>{@code left} is always present and is the full skin ID. {@code right} is optional and
     * is only populated when the skin is an Equipable; in that case {@code right} is the
     * right-side token of the equipable ID so that {@code left} is {@code "om:" + right}.</p>
     *
     * <p>Returned data is expected to be sorted by {@link SKIN_TYPE}.</p>
     *
     * @param consumer function returning player's skins grouped by type
     */
    public static void setGetPlayerSkinsFunction(Function<Player, Map<SKIN_TYPE, ArrayList<Pair<String, String>>>> consumer) {
        getPlayerSkinsFunction = consumer;
        OmGames.getInstance().getLogger().info("OmVeins API: GetPlayerSkinsConsumer consumer set!");
        checkIfDone();
    }

    /**
     * Returns the player's available Om skins grouped by {@link SKIN_TYPE}.
     *
     * <p>Each entry is a {@link Pair} of {@code (left, right)} representing the skin ID.
     * {@code left} is always present and is the full skin ID. {@code right} is optional and
     * is only populated when the skin is an Equipable; in that case {@code right} is the
     * right-side token of the equipable ID so that {@code left} is {@code "om:" + right}.</p>
     *
     * <p>Result is expected to be sorted by {@link SKIN_TYPE}.</p>
     *
     * @param player player whose skins should be returned
     * @return map of skin type to list of skin ID pairs
     *
     * @throws IllegalStateException if the API has not been initialized
     */
    public static Map<SKIN_TYPE, ArrayList<Pair<String, String>>> getPlayerSkins(Player player){
        if (!initialized) {
            notInitializedWarning();
        }
        return getPlayerSkinsFunction.apply(player);
    }

    private static void notInitializedWarning(){
        throw new IllegalStateException(
                "Attempting to use OmVeins API while it is not initialized!"
        );
    }
}
