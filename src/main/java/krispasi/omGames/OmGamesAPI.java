package krispasi.omGames;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import krispasi.omGames.bank.BankManager;
import org.bukkit.entity.Player;

/**
 * Runtime bridge API exposed by OmGames for OmVeins integrations.
 *
 * <p>Do not call this API during server startup. It is available only after
 * OmGames has connected its runtime managers.</p>
 */
public final class OmGamesAPI {
    private static BankManager bankManager;
    private static Consumer<UUID> boughtKrgStockConsumer;

    private OmGamesAPI() {
    }

    public static boolean isInitialized() {
        return bankManager != null;
    }

    public static void setBankManager(BankManager manager) {
        bankManager = manager;
    }

    public static void clearBankManager(BankManager manager) {
        if (bankManager == manager) {
            bankManager = null;
        }
    }

    public static void openAtm(Player player) {
        requireInitialized().openAtm(player);
    }

    public static long getPlayerBalance(UUID playerId) {
        return requireInitialized().getBalance(playerId);
    }

    public static Map<String, Long> getPlayerStocks(UUID playerId) {
        return requireInitialized().getStocks(playerId);
    }

    public static void setBoughtKrgStockConsumer(Consumer<UUID> consumer) {
        boughtKrgStockConsumer = consumer;
    }

    public static void boughtKrgStock(UUID playerId) {
        if (boughtKrgStockConsumer != null) {
            boughtKrgStockConsumer.accept(playerId);
        }
    }

    public static void registerOmVeinsBankItems() {
        requireInitialized().registerOmVeinsItems();
    }

    private static BankManager requireInitialized() {
        if (bankManager == null) {
            throw new IllegalStateException("Attempting to use OmGames API while it is not initialized.");
        }
        return bankManager;
    }
}
