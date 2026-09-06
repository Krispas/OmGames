package krispasi.omGames.bank;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class BankManager {
    private final JavaPlugin plugin;
    private final BankDatabaseService database;
    private final Map<UUID, BankPromptSession> prompts = new ConcurrentHashMap<>();
    private final NamespacedKey cardIdKey;
    private final NamespacedKey terminalIdKey;

    public BankManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.database = new BankDatabaseService(plugin);
        this.cardIdKey = new NamespacedKey(plugin, "bank_card_id");
        this.terminalIdKey = new NamespacedKey(plugin, "bank_terminal_id");
    }

    public void load() {
        database.load();
    }

    public void shutdown() {
        prompts.clear();
        database.shutdown();
    }

    public void openAdminMenu(Player player) {
        if (player == null) {
            return;
        }
        new BankAdminMenu(this).open(player);
    }

    public void openAccountMenu(Player player, UUID accountId) {
        if (player == null || accountId == null) {
            return;
        }
        new BankAccountMenu(this, accountId).open(player);
    }

    public void openCardsMenu(Player player, UUID accountId) {
        if (player == null || accountId == null) {
            return;
        }
        new BankCardsMenu(this, accountId).open(player);
    }

    public void openTerminalsMenu(Player player, UUID accountId) {
        if (player == null || accountId == null) {
            return;
        }
        new BankTerminalsMenu(this, accountId).open(player);
    }

    public void openTerminalOwnerMenu(Player player, String terminalId) {
        if (player == null || terminalId == null) {
            return;
        }
        new BankTerminalOwnerMenu(this, terminalId).open(player);
    }

    public void openTerminalBuyerMenu(Player player, String terminalId) {
        if (player == null || terminalId == null) {
            return;
        }
        new BankTerminalBuyerMenu(this, terminalId).open(player);
    }

    public Result openTerminalForPlayer(Player player, String terminalId) {
        BankTerminal terminal = getTerminal(terminalId);
        if (terminal == null) {
            return Result.fail("Terminal not found.");
        }
        if (ownsTerminal(player, terminal)) {
            openTerminalOwnerMenu(player, terminalId);
        } else {
            openTerminalBuyerMenu(player, terminalId);
        }
        return Result.ok("Opened terminal " + terminal.name() + ".");
    }

    public void beginCreateAccountPrompt(Player player) {
        if (player == null) {
            return;
        }
        prompts.put(player.getUniqueId(), BankPromptSession.createAccount());
        player.closeInventory();
        sendPrompt(player, "Napis nick hrace, kteremu chces zalozit ucet. Napis cancel pro zruseni.");
    }

    public boolean hasPrompt(Player player) {
        return player != null && prompts.containsKey(player.getUniqueId());
    }

    public void cancelPrompt(Player player) {
        if (player != null) {
            prompts.remove(player.getUniqueId());
        }
    }

    public void handlePromptInput(Player player, String message) {
        if (player == null) {
            return;
        }
        BankPromptSession session = prompts.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        String input = message == null ? "" : message.trim();
        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("zrusit")) {
            prompts.remove(player.getUniqueId());
            player.sendMessage(Component.text("Bank input cancelled.", NamedTextColor.YELLOW));
            openAdminMenu(player);
            return;
        }
        if (session.mode() == BankPromptSession.Mode.CREATE_ACCOUNT) {
            createAccountFromPrompt(player, input);
        }
    }

    public Result createAccount(OfflinePlayer target) {
        if (target == null || target.getUniqueId() == null) {
            return Result.fail("Player not found.");
        }
        String name = target.getName();
        if (name == null || name.isBlank()) {
            name = target.getUniqueId().toString();
        }
        BankAccount account = database.createAccount(target.getUniqueId(), name, System.currentTimeMillis());
        if (account == null) {
            return Result.fail("Failed to create bank account.");
        }
        return Result.ok("Bank account ready for " + account.playerName() + ".");
    }

    public Result createCard(Player receiver, UUID ownerId) {
        BankAccount account = database.getAccount(ownerId);
        if (account == null) {
            return Result.fail("Bank account not found.");
        }
        String cardId = newId("card");
        BankCard card = database.createCard(account.playerId(), account.playerName(), cardId, System.currentTimeMillis());
        if (card == null) {
            return Result.fail("Failed to create credit card.");
        }
        if (receiver != null) {
            giveOrDrop(receiver, createCardItem(card));
            receiver.playSound(receiver.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.1f);
        }
        return Result.ok("Created credit card for " + account.playerName() + ".");
    }

    public Result setCardFrozen(String cardId, boolean frozen) {
        BankCard card = database.getCard(cardId);
        if (card == null) {
            return Result.fail("Credit card not found.");
        }
        if (database.setCardFrozen(cardId, frozen)) {
            return Result.ok((frozen ? "Frozen" : "Unfrozen") + " credit card for " + card.ownerName() + ".");
        }
        return Result.fail("Failed to update credit card.");
    }

    public Result createTerminal(UUID ownerId) {
        BankAccount account = database.getAccount(ownerId);
        if (account == null) {
            return Result.fail("Bank account not found.");
        }
        String terminalId = newId("terminal");
        int number = database.listTerminals(ownerId).size() + 1;
        BankTerminal terminal = database.createTerminal(
                account.playerId(),
                account.playerName(),
                terminalId,
                account.playerName() + " Terminal " + number,
                System.currentTimeMillis()
        );
        if (terminal == null) {
            return Result.fail("Failed to create terminal.");
        }
        return Result.ok("Created terminal " + terminal.name() + ".");
    }

    public BankAccount getAccount(UUID accountId) {
        return database.getAccount(accountId);
    }

    public List<BankAccount> listAccounts() {
        return database.listAccounts();
    }

    public List<BankCard> listCards(UUID ownerId) {
        return database.listCards(ownerId);
    }

    public BankCard getCard(String cardId) {
        return database.getCard(cardId);
    }

    public List<BankTerminal> listTerminals(UUID ownerId) {
        return database.listTerminals(ownerId);
    }

    public BankTerminal getTerminal(String terminalId) {
        return database.getTerminal(terminalId);
    }

    public List<BankTerminalItem> listTerminalItems(String terminalId) {
        return database.listTerminalItems(terminalId);
    }

    public List<BankCartLine> listCart(Player player, String terminalId) {
        if (player == null) {
            return List.of();
        }
        return database.listCart(player.getUniqueId(), terminalId);
    }

    public long cartTotal(List<BankCartLine> lines) {
        long total = 0L;
        for (BankCartLine line : lines) {
            if (line != null) {
                total += line.lineTotal();
            }
        }
        return total;
    }

    public boolean ownsTerminal(Player player, BankTerminal terminal) {
        return player != null && terminal != null && player.getUniqueId().equals(terminal.ownerId());
    }

    public String readCardId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(cardIdKey, PersistentDataType.STRING);
    }

    public String readTerminalId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(terminalIdKey, PersistentDataType.STRING);
    }

    public Result previewCardCheckout(Player player, String terminalId, String cardId) {
        BankTerminal terminal = getTerminal(terminalId);
        BankCard card = getCard(cardId);
        if (terminal == null) {
            return Result.fail("Terminal not found.");
        }
        if (card == null) {
            return Result.fail("Credit card not found.");
        }
        if (card.frozen()) {
            return Result.fail("This credit card is frozen.");
        }
        List<BankCartLine> lines = listCart(player, terminalId);
        if (lines.isEmpty()) {
            return Result.fail("Your cart is empty.");
        }
        List<String> summary = new ArrayList<>();
        for (BankCartLine line : lines) {
            summary.add(line.amount() + "x " + line.displayName() + " = " + line.lineTotal());
        }
        return Result.ok("Cart on " + terminal.name() + ": " + String.join(", ", summary)
                + ". Total: " + cartTotal(lines) + ".");
    }

    public Result payCart(Player player, String terminalId, String cardId) {
        Result preview = previewCardCheckout(player, terminalId, cardId);
        if (!preview.success()) {
            return preview;
        }
        return Result.fail("Payment is prepared but disabled until the credit economy is connected.");
    }

    private void createAccountFromPrompt(Player player, String input) {
        if (input.isBlank()) {
            sendPrompt(player, "Nick nesmi byt prazdny. Zadej nick znovu.");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(input);
        Result result = createAccount(target);
        prompts.remove(player.getUniqueId());
        player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
        openAdminMenu(player);
    }

    private ItemStack createCardItem(BankCard card) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Credit Card - " + card.ownerName(), NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text("Owner: " + card.ownerName(), NamedTextColor.GRAY),
                Component.text("Card: " + shortId(card.cardId()), NamedTextColor.DARK_GRAY),
                Component.text("Usable by anyone unless frozen.", NamedTextColor.YELLOW)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(cardIdKey, PersistentDataType.STRING, card.cardId());
        item.setItemMeta(meta);
        return item;
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            if (leftover != null) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String shortId(String id) {
        if (id == null || id.length() <= 10) {
            return id == null ? "" : id;
        }
        return id.substring(0, 10);
    }

    private void sendPrompt(Player player, String message) {
        player.sendMessage(Component.text("[Bank] " + message, NamedTextColor.YELLOW));
    }

    public record Result(boolean success, String message) {
        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }
}
