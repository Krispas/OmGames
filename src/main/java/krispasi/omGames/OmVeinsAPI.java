package krispasi.omGames;

import krispasi.omGames.shared.Skin;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.function.BiConsumer;
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
     * keyed by model id in the form {@code om:<id_in_string>}.
     *
     * <p>Each value is a {@link Skin} instance. Wearable skins (armor/elytra)
     * are returned as {@code SkinEquipment} instances.</p>
     */
    private static Function<Player, Map<String, Skin>> getPlayerSkinsFunction;

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
     * <p>The function must return a map keyed by model id in the form
     * {@code om:<id_in_string>}.</p>
     *
     * <p>Each value is a {@link Skin} instance. Wearable skins (armor/elytra)
     * are returned as {@code SkinEquipment} instances.</p>
     *
     * @param consumer function returning player's skins keyed by model id
     */
    public static void setGetPlayerSkinsFunction(Function<Player, Map<String, Skin>> consumer) {
        getPlayerSkinsFunction = consumer;
        OmGames.getInstance().getLogger().info("OmVeins API: GetPlayerSkinsConsumer consumer set!");
        checkIfDone();
    }

    /**
     * Returns the player's available Om skins keyed by model id.
     *
     * <p>Keys are formatted as {@code om:<id_in_string>}. Each value is a {@link Skin}
     * instance; wearable skins (armor/elytra) are returned as
     * {@code SkinEquipment} instances.</p>
     *
     * @param player player whose skins should be returned
     * @return map of model id to skin metadata
     *
     * @throws IllegalStateException if the API has not been initialized
     */
    public static Map<String, Skin> getPlayerSkins(Player player){
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
