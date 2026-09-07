package krispasi.omGames.bank;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import krispasi.omGames.OmVeinsAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class BankManager {
    private static final String CREDIT_CARD_ITEM_ID = "credit_card";
    private static final String CASH_REGISTER_ITEM_ID = "cash_register";
    private static final List<String> CREDIT_IDS = List.of("credit1", "credit10", "credit50", "credit100", "credit1000", "credit5000");
    private static final Map<String, Long> CREDIT_VALUES = Map.of(
            "credit1", 1L,
            "credit10", 10L,
            "credit50", 50L,
            "credit100", 100L,
            "credit1000", 1000L,
            "credit5000", 5000L
    );

    private final JavaPlugin plugin;
    private final BankDatabaseService database;
    private final BankTerminalPlacementService placementService;
    private final Map<UUID, BankPromptSession> prompts = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingTerminalItemClickLocations = new ConcurrentHashMap<>();
    private final NamespacedKey cardIdKey;
    private final NamespacedKey terminalIdKey;
    private final NamespacedKey terminalEditorKey;
    private final NamespacedKey omCreditCardKey;

    public BankManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.database = new BankDatabaseService(plugin);
        this.placementService = new BankTerminalPlacementService(this, plugin);
        this.cardIdKey = new NamespacedKey(plugin, "bank_card_id");
        this.terminalIdKey = new NamespacedKey(plugin, "bank_terminal_id");
        this.terminalEditorKey = new NamespacedKey(plugin, "bank_terminal_editor");
        this.omCreditCardKey = new NamespacedKey("om", "credit_card");
    }

    public void load() {
        database.load();
    }

    public void shutdown() {
        prompts.clear();
        pendingTerminalItemClickLocations.clear();
        database.shutdown();
    }

    public void openAdminMenu(Player player) {
        if (player == null) {
            return;
        }
        new BankAdminMenu(this).open(player);
    }

    public void openAccountMenu(Player player, String accountId) {
        if (player == null || accountId == null) {
            return;
        }
        new BankAccountMenu(this, accountId).open(player);
    }

    public void openOnlinePlayerMenu(Player player) {
        if (player == null) {
            return;
        }
        new BankOnlinePlayerMenu(this).open(player);
    }

    public void openNonPlayerAccountEditorMenu(Player player) {
        if (player == null) {
            return;
        }
        new BankNonPlayerAccountEditorMenu(this).open(player);
    }

    public void openCardsMenu(Player player, String accountId) {
        if (player == null || accountId == null) {
            return;
        }
        new BankCardsMenu(this, accountId).open(player);
    }

    public void openTerminalsMenu(Player player, String accountId) {
        if (player == null || accountId == null) {
            return;
        }
        new BankTerminalsMenu(this, accountId).open(player);
    }

    public void openAccountEditorsMenu(Player player, String accountId) {
        if (player == null || accountId == null) {
            return;
        }
        new BankAccountEditorsMenu(this, accountId).open(player);
    }

    public void openTerminalOwnerMenu(Player player, String terminalId) {
        if (player == null || terminalId == null) {
            return;
        }
        new BankTerminalOwnerMenu(this, terminalId).open(player);
    }

    public void openTerminalAdminMenu(Player player, String terminalId) {
        if (player == null || terminalId == null) {
            return;
        }
        new BankTerminalAdminMenu(this, terminalId).open(player);
    }

    public void openTerminalBuyerMenu(Player player, String terminalId) {
        if (player == null || terminalId == null) {
            return;
        }
        new BankTerminalBuyerMenu(this, terminalId).open(player);
    }

    public void openTerminalEditorMenu(Player player, String terminalId) {
        if (player == null || terminalId == null) {
            return;
        }
        new BankTerminalEditorMenu(this, terminalId).open(player);
    }

    public void openTerminalItemEditMenu(Player player, String itemId) {
        if (player == null || itemId == null) {
            return;
        }
        new BankTerminalItemEditMenu(this, itemId).open(player);
    }

    public void openTerminalIconMenu(Player player, String itemId, int page) {
        if (player == null || itemId == null) {
            return;
        }
        new BankTerminalIconMenu(this, itemId, page).open(player);
    }

    public void openAtm(Player player) {
        if (player == null) {
            return;
        }
        new BankAtmMenu(this, null).open(player);
    }

    public void openAtm(Player player, String cardId) {
        if (player == null) {
            return;
        }
        new BankAtmMenu(this, cardId).open(player);
    }

    public void openAtmDepositMenu(Player player, String cardId) {
        if (player == null) {
            return;
        }
        new BankAtmDepositMenu(this, cardId).open(player);
    }

    public void openAtmWithdrawMenu(Player player, String cardId) {
        if (player == null) {
            return;
        }
        new BankAtmWithdrawMenu(this, cardId).open(player);
    }

    public void beginCreateNonPlayerAccountPrompt(Player player) {
        if (player == null) {
            return;
        }
        prompts.put(player.getUniqueId(), BankPromptSession.createNonPlayerAccount());
        player.closeInventory();
        sendPrompt(player, "Napis nazev non-player uctu. Napis cancel pro zruseni.");
    }

    public boolean hasPrompt(Player player) {
        return player != null && prompts.containsKey(player.getUniqueId());
    }

    public void cancelPrompt(Player player) {
        if (player != null) {
            prompts.remove(player.getUniqueId());
            pendingTerminalItemClickLocations.remove(player.getUniqueId());
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
            if (session.mode() == BankPromptSession.Mode.TERMINAL_ITEM_NAME
                    || session.mode() == BankPromptSession.Mode.TERMINAL_ITEM_PRICE) {
                openTerminalItemEditMenu(player, session.itemId());
                return;
            }
            openAdminMenu(player);
            return;
        }
        if (session.mode() == BankPromptSession.Mode.CREATE_NON_PLAYER_ACCOUNT) {
            Result result = createNonPlayerAccount(input);
            prompts.remove(player.getUniqueId());
            player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (result.success()) {
                BankAccount account = getAccountByName(input);
                if (account != null) {
                    openAccountMenu(player, account.accountId());
                    return;
                }
            }
            openAdminMenu(player);
            return;
        }
        if (session.mode() == BankPromptSession.Mode.TERMINAL_ITEM_NAME) {
            Result result = updateTerminalItemName(player, session.itemId(), input);
            prompts.remove(player.getUniqueId());
            player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            openTerminalItemEditMenu(player, session.itemId());
            return;
        }
        if (session.mode() == BankPromptSession.Mode.TERMINAL_ITEM_PRICE) {
            long price;
            try {
                price = Long.parseLong(input);
            } catch (NumberFormatException ex) {
                player.sendMessage(Component.text("Price must be a whole positive number.", NamedTextColor.RED));
                return;
            }
            Result result = updateTerminalItemPrice(player, session.itemId(), price);
            prompts.remove(player.getUniqueId());
            player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            openTerminalItemEditMenu(player, session.itemId());
        }
    }

    public Result openTerminalForPlayer(Player player, String terminalId) {
        return openTerminalForPlayer(player, terminalId, null);
    }

    public Result openTerminalForPlayer(Player player, String terminalId, String placementId) {
        BankTerminal terminal = getTerminal(terminalId);
        if (terminal == null) {
            return Result.fail("Terminal not found.");
        }
        if (ownsTerminal(player, terminal)) {
            new BankTerminalOwnerMenu(this, terminalId, placementId).open(player);
        } else {
            openTerminalBuyerMenu(player, terminalId);
        }
        return Result.ok("Opened terminal " + terminal.name() + ".");
    }

    public BankTerminalPlacementService getPlacementService() {
        return placementService;
    }

    public Result createAccount(Player target) {
        if (target == null || target.getUniqueId() == null) {
            return Result.fail("Player must be online.");
        }
        String name = target.getName();
        if (name == null || name.isBlank()) {
            name = target.getUniqueId().toString();
        }
        BankAccount account = database.createPlayerAccount(target.getUniqueId(), name, System.currentTimeMillis());
        if (account == null) {
            return Result.fail("Failed to create bank account.");
        }
        return Result.ok("Bank account ready for " + account.displayName() + ".");
    }

    public Result createNonPlayerAccount(String displayName) {
        String name = displayName == null ? "" : displayName.trim();
        if (name.isBlank() || name.length() > 32) {
            return Result.fail("Account name must have 1-32 characters.");
        }
        if (getAccountByName(name) != null) {
            return Result.fail("A bank account with that name already exists.");
        }
        BankAccount account = database.createNonPlayerAccount(newId("account"), name, System.currentTimeMillis());
        if (account == null) {
            return Result.fail("Failed to create non-player account.");
        }
        return Result.ok("Created non-player account " + account.displayName() + ".");
    }

    public Result createCard(Player receiver, String accountId) {
        BankAccount account = database.getAccount(accountId);
        if (account == null) {
            return Result.fail("Bank account not found.");
        }
        String cardId = newId("card");
        BankCard card = database.createCard(account.accountId(), account.playerId(), account.displayName(), cardId, System.currentTimeMillis());
        if (card == null) {
            return Result.fail("Failed to create credit card.");
        }
        if (receiver != null) {
            ItemStack item = createCardItem(card);
            if (item == null) {
                return Result.fail("OmVeins credit_card item is not available.");
            }
            giveOrDrop(receiver, item);
            receiver.playSound(receiver.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.1f);
        }
        return Result.ok("Created credit card for " + account.displayName() + ".");
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

    public Result createTerminal(Player receiver, String accountId) {
        BankAccount account = database.getAccount(accountId);
        if (account == null) {
            return Result.fail("Bank account not found.");
        }
        if (receiver != null && !OmVeinsAPI.isInitialized()) {
            return Result.fail("OmVeins API is not initialized.");
        }
        if (receiver != null) {
            Result registration = registerOmVeinsItems();
            if (!registration.success()) {
                return registration;
            }
            if (createRegisteredCashRegisterBase() == null) {
                return Result.fail("OmVeins cash_register item is not available.");
            }
        }
        String terminalId = newId("terminal");
        int number = database.listTerminals(account.accountId()).size() + 1;
        BankTerminal terminal = database.createTerminal(
                account.accountId(),
                account.playerId(),
                account.displayName(),
                terminalId,
                account.displayName() + " Terminal " + number,
                System.currentTimeMillis()
        );
        if (terminal == null) {
            return Result.fail("Failed to create terminal.");
        }
        if (receiver != null) {
            ItemStack item = createCashRegisterItem(terminal);
            if (item == null) {
                return Result.fail("OmVeins cash_register item is not available.");
            }
            giveOrDrop(receiver, item);
            receiver.playSound(receiver.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.1f);
        }
        return Result.ok("Created terminal " + terminal.name() + ".");
    }

    public Result createTerminal(String accountId) {
        return createTerminal(null, accountId);
    }

    public BankAccount getAccount(String accountId) {
        return database.getAccount(accountId);
    }

    public BankAccount getPlayerAccount(UUID playerId) {
        return database.getPlayerAccount(playerId);
    }

    public String playerAccountId(UUID playerId) {
        return playerId == null ? null : "player:" + playerId;
    }

    public long getBalance(UUID accountId) {
        BankAccount account = database.getPlayerAccount(accountId);
        return account == null ? 0L : account.balance();
    }

    public Map<String, Long> getStocks(UUID accountId) {
        return database.getStocks(accountId);
    }

    public List<BankAccount> listAccounts() {
        return database.listAccounts();
    }

    public List<BankCard> listCards(String accountId) {
        return database.listCards(accountId);
    }

    public BankCard getCard(String cardId) {
        return database.getCard(cardId);
    }

    public List<BankTerminal> listTerminals(String accountId) {
        return database.listTerminals(accountId);
    }

    public BankTerminal getTerminal(String terminalId) {
        return database.getTerminal(terminalId);
    }

    public Result deleteTerminal(String terminalId) {
        BankTerminal terminal = database.getTerminal(terminalId);
        if (terminal == null) {
            return Result.fail("Terminal not found.");
        }
        if (database.deleteTerminal(terminalId)) {
            return Result.ok("Deleted terminal " + terminal.name() + ".");
        }
        return Result.fail("Failed to delete terminal.");
    }

    public Result giveTerminalItem(Player player, String terminalId) {
        if (player == null) {
            return Result.fail("Only players can receive terminal items.");
        }
        BankTerminal terminal = database.getTerminal(terminalId);
        if (terminal == null) {
            return Result.fail("Terminal not found.");
        }
        ItemStack item = createCashRegisterItem(terminal);
        if (item == null) {
            return Result.fail("OmVeins cash_register item is not available.");
        }
        giveOrDrop(player, item);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.1f);
        return Result.ok("Gave terminal item for " + terminal.name() + ".");
    }

    public List<BankTerminalItem> listTerminalItems(String terminalId) {
        return database.listTerminalItems(terminalId);
    }

    public BankTerminalItem getTerminalItem(String itemId) {
        return database.getTerminalItem(itemId);
    }

    public Result createTerminalItem(Player player, String terminalId) {
        BankTerminal terminal = database.getTerminal(terminalId);
        if (terminal == null) {
            return Result.fail("Terminal not found.");
        }
        if (!canEditAccount(player, terminal.accountId())) {
            return Result.fail("You cannot edit this terminal.");
        }
        int number = database.listTerminalItems(terminalId).size() + 1;
        BankTerminalItem item = database.createTerminalItem(
                newId("terminal_item"),
                terminalId,
                "Item " + number,
                1L,
                Material.CHEST,
                number
        );
        if (item == null) {
            return Result.fail("Failed to create terminal item.");
        }
        return Result.ok("Created terminal item " + item.displayName() + ".");
    }

    public Result updateTerminalItemName(Player player, String itemId, String displayName) {
        BankTerminalItem item = database.getTerminalItem(itemId);
        if (item == null) {
            return Result.fail("Terminal item not found.");
        }
        BankTerminal terminal = database.getTerminal(item.terminalId());
        if (terminal == null) {
            return Result.fail("Terminal not found.");
        }
        if (!canEditAccount(player, terminal.accountId())) {
            return Result.fail("You cannot edit this terminal.");
        }
        String name = displayName == null ? "" : displayName.trim();
        if (name.isBlank() || name.length() > 48) {
            return Result.fail("Item name must have 1-48 characters.");
        }
        return database.updateTerminalItemName(itemId, name)
                ? Result.ok("Updated terminal item name.")
                : Result.fail("Failed to update terminal item name.");
    }

    public Result updateTerminalItemPrice(Player player, String itemId, long price) {
        BankTerminalItem item = database.getTerminalItem(itemId);
        if (item == null) {
            return Result.fail("Terminal item not found.");
        }
        BankTerminal terminal = database.getTerminal(item.terminalId());
        if (terminal == null) {
            return Result.fail("Terminal not found.");
        }
        if (!canEditAccount(player, terminal.accountId())) {
            return Result.fail("You cannot edit this terminal.");
        }
        if (price <= 0L) {
            return Result.fail("Price must be positive.");
        }
        return database.updateTerminalItemPrice(itemId, price)
                ? Result.ok("Updated terminal item price.")
                : Result.fail("Failed to update terminal item price.");
    }

    public Result updateTerminalItemIcon(Player player, String itemId, Material iconMaterial) {
        BankTerminalItem item = database.getTerminalItem(itemId);
        if (item == null) {
            return Result.fail("Terminal item not found.");
        }
        BankTerminal terminal = database.getTerminal(item.terminalId());
        if (terminal == null) {
            return Result.fail("Terminal not found.");
        }
        if (!canEditAccount(player, terminal.accountId())) {
            return Result.fail("You cannot edit this terminal.");
        }
        if (iconMaterial == null || iconMaterial.isAir() || !iconMaterial.isItem()) {
            return Result.fail("Choose a valid item icon.");
        }
        return database.updateTerminalItemIcon(itemId, iconMaterial)
                ? Result.ok("Updated terminal item icon.")
                : Result.fail("Failed to update terminal item icon.");
    }

    public Result deleteTerminalItem(Player player, String itemId) {
        BankTerminalItem item = database.getTerminalItem(itemId);
        if (item == null) {
            return Result.fail("Terminal item not found.");
        }
        BankTerminal terminal = database.getTerminal(item.terminalId());
        if (terminal == null) {
            return Result.fail("Terminal not found.");
        }
        if (!canEditAccount(player, terminal.accountId())) {
            return Result.fail("You cannot edit this terminal.");
        }
        return database.deleteTerminalItem(itemId)
                ? Result.ok("Deleted terminal item " + item.displayName() + ".")
                : Result.fail("Failed to delete terminal item.");
    }

    public void beginTerminalItemNamePrompt(Player player, String itemId) {
        BankTerminalItem item = database.getTerminalItem(itemId);
        if (!canEditTerminalItem(player, item)) {
            player.sendMessage(Component.text("You cannot edit this terminal item.", NamedTextColor.RED));
            return;
        }
        prompts.put(player.getUniqueId(), BankPromptSession.terminalItemName(item.terminalId(), item.itemId()));
        player.closeInventory();
        sendPrompt(player, "Napis novy nazev terminal itemu. Napis cancel pro zruseni.");
    }

    public void beginTerminalItemPricePrompt(Player player, String itemId) {
        BankTerminalItem item = database.getTerminalItem(itemId);
        if (!canEditTerminalItem(player, item)) {
            player.sendMessage(Component.text("You cannot edit this terminal item.", NamedTextColor.RED));
            return;
        }
        prompts.put(player.getUniqueId(), BankPromptSession.terminalItemPrice(item.terminalId(), item.itemId()));
        player.closeInventory();
        sendPrompt(player, "Napis cenu v kreditech. Napis cancel pro zruseni.");
    }

    public Result beginTerminalItemClickLocation(Player player, String itemId) {
        BankTerminalItem item = database.getTerminalItem(itemId);
        if (!canEditTerminalItem(player, item)) {
            return Result.fail("You cannot edit this terminal item.");
        }
        pendingTerminalItemClickLocations.put(player.getUniqueId(), item.itemId());
        player.closeInventory();
        return Result.ok("Right-click a block with the terminal editor to bind this item.");
    }

    public Result startTerminalEditing(Player player, String terminalId) {
        BankTerminal terminal = database.getTerminal(terminalId);
        if (terminal == null) {
            return Result.fail("Terminal not found.");
        }
        if (!canEditAccount(player, terminal.accountId())) {
            return Result.fail("You cannot edit this terminal.");
        }
        removeTerminalEditorItems(player, terminalId);
        ItemStack editor = createTerminalEditorItem(terminal);
        if (player.getInventory().getItemInMainHand().getType().isAir()) {
            player.getInventory().setItemInMainHand(editor);
        } else {
            giveOrDrop(player, editor);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.25f);
        return Result.ok("Terminal editor ready. Left-click with it to open the item list.");
    }

    public Result stopTerminalEditing(Player player, String terminalId) {
        if (player == null) {
            return Result.fail("Only players can edit terminals.");
        }
        int removed = removeTerminalEditorItems(player, terminalId);
        pendingTerminalItemClickLocations.remove(player.getUniqueId());
        return Result.ok("Terminal editing ended. Removed " + removed + " editor item" + (removed == 1 ? "" : "s") + ".");
    }

    public Result addTerminalItemToCart(Player player, String itemId, int amount) {
        if (player == null) {
            return Result.fail("Only players can use terminal carts.");
        }
        BankTerminalItem item = database.getTerminalItem(itemId);
        if (item == null) {
            return Result.fail("Terminal item not found.");
        }
        if (!database.addCartItem(player.getUniqueId(), item.itemId(), Math.max(1, amount))) {
            return Result.fail("Failed to add item to cart.");
        }
        return Result.ok("Added " + item.displayName() + " to cart for " + item.price() + " credits.");
    }

    public boolean handleTerminalEditorInteract(Player player, org.bukkit.event.block.Action action, Block clickedBlock, EquipmentSlot hand, ItemStack item) {
        if (player == null || hand != EquipmentSlot.HAND || !isTerminalEditorItem(item)) {
            return false;
        }
        String terminalId = readTerminalEditorId(item);
        BankTerminal terminal = database.getTerminal(terminalId);
        if (terminal == null || !canEditAccount(player, terminal.accountId())) {
            player.sendMessage(Component.text("You cannot use this terminal editor.", NamedTextColor.RED));
            return true;
        }
        if (action == org.bukkit.event.block.Action.LEFT_CLICK_AIR || action == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            openTerminalEditorMenu(player, terminalId);
            return true;
        }
        if (action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK && clickedBlock != null) {
            String itemId = pendingTerminalItemClickLocations.remove(player.getUniqueId());
            if (itemId == null) {
                player.sendMessage(Component.text("Choose Set Click Block in an item menu first.", NamedTextColor.YELLOW));
                return true;
            }
            BankTerminalItem terminalItem = database.getTerminalItem(itemId);
            if (terminalItem == null || !terminalItem.terminalId().equals(terminalId)) {
                player.sendMessage(Component.text("Terminal item not found for this editor.", NamedTextColor.RED));
                return true;
            }
            boolean updated = database.updateTerminalItemClickLocation(
                    terminalItem.itemId(),
                    clickedBlock.getWorld().getName(),
                    clickedBlock.getX(),
                    clickedBlock.getY(),
                    clickedBlock.getZ()
            );
            player.sendMessage(Component.text(updated
                    ? "Click block set for " + terminalItem.displayName() + "."
                    : "Failed to set click block.", updated ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (updated) {
                openTerminalItemEditMenu(player, terminalItem.itemId());
            }
            return true;
        }
        return true;
    }

    public boolean handleTerminalItemBlockClick(Player player, Block clickedBlock) {
        if (player == null || clickedBlock == null) {
            return false;
        }
        BankTerminalItem item = database.getTerminalItemAt(
                clickedBlock.getWorld().getName(),
                clickedBlock.getX(),
                clickedBlock.getY(),
                clickedBlock.getZ()
        );
        if (item == null) {
            return false;
        }
        String cardId = firstCardId(player, null);
        if (cardId == null) {
            player.sendMessage(Component.text("Hold a credit card to add terminal items to your cart.", NamedTextColor.YELLOW));
            return true;
        }
        Result validation = validateAtmCard(cardId);
        if (!validation.success()) {
            player.sendMessage(Component.text(validation.message(), NamedTextColor.RED));
            return true;
        }
        Result result = addTerminalItemToCart(player, item.itemId(), 1);
        player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
        return true;
    }

    public boolean handleTerminalEditorDrop(Player player, ItemStack item) {
        if (player == null || !isTerminalEditorItem(item)) {
            return false;
        }
        pendingTerminalItemClickLocations.remove(player.getUniqueId());
        player.sendMessage(Component.text("Terminal editor removed.", NamedTextColor.YELLOW));
        return true;
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
        return canEditAccount(player, terminal.accountId());
    }

    public boolean canEditAccount(Player player, String accountId) {
        if (player == null || accountId == null) {
            return false;
        }
        if (player.hasPermission("omgames.bank.admin")) {
            return true;
        }
        BankAccount account = database.getAccount(accountId);
        if (account == null) {
            return false;
        }
        if (account.playerId() != null && account.playerId().equals(player.getUniqueId())) {
            return true;
        }
        return database.isEditor(accountId, player.getUniqueId());
    }

    public List<BankAccountEditor> listEditors(String accountId) {
        return database.listEditors(accountId);
    }

    public boolean isEditor(String accountId, UUID playerId) {
        return database.isEditor(accountId, playerId);
    }

    public Result toggleEditor(String accountId, Player target) {
        BankAccount account = database.getAccount(accountId);
        if (account == null) {
            return Result.fail("Bank account not found.");
        }
        if (account.playerAccount()) {
            return Result.fail("Player accounts do not use editor lists.");
        }
        if (target == null || !target.isOnline()) {
            return Result.fail("Player must be online.");
        }
        if (database.isEditor(accountId, target.getUniqueId())) {
            return database.removeEditor(accountId, target.getUniqueId())
                    ? Result.ok("Removed " + target.getName() + " from account editors.")
                    : Result.fail("Failed to remove account editor.");
        }
        return database.addEditor(accountId, target.getUniqueId(), target.getName())
                ? Result.ok("Added " + target.getName() + " as account editor.")
                : Result.fail("Failed to add account editor.");
    }

    public String readCardId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        if (!isOmVeinsCreditCard(item)) {
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

    public String readTerminalEditorId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        Boolean editor = meta.getPersistentDataContainer().get(terminalEditorKey, PersistentDataType.BOOLEAN);
        if (!Boolean.TRUE.equals(editor)) {
            return null;
        }
        return meta.getPersistentDataContainer().get(terminalIdKey, PersistentDataType.STRING);
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
        BankTerminal terminal = getTerminal(terminalId);
        BankCard card = getCard(cardId);
        List<BankCartLine> lines = listCart(player, terminalId);
        long total = cartTotal(lines);
        if (terminal == null || card == null || total <= 0L) {
            return Result.fail("Checkout is no longer valid.");
        }
        if (!database.transferAndClearCart(card.accountId(), terminal.accountId(), player.getUniqueId(), terminalId, total)) {
            return Result.fail("Payment failed. Check your card balance.");
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        return Result.ok("Paid " + total + " credits to " + terminal.name()
                + ". Balance: " + accountBalance(card.accountId()) + ".");
    }

    public Result payCartWithCardInput(Player player, String terminalId, ItemStack cursorItem) {
        String cardId = firstCardId(player, cursorItem);
        if (cardId == null) {
            return Result.fail("Hold a credit card or click Pay with a card on your cursor.");
        }
        return payCart(player, terminalId, cardId);
    }

    public Result depositHeldCredits(Player player) {
        return Result.fail("Insert a credit card first.");
    }

    public Result depositHeldCredits(Player player, String cardId) {
        return depositCredits(player, cardId, null);
    }

    public Result depositCreditType(Player player, String creditId) {
        return Result.fail("Insert a credit card first.");
    }

    public Result depositCreditType(Player player, String cardId, String creditId) {
        if (creditId == null || !CREDIT_VALUES.containsKey(creditId)) {
            return Result.fail("Unknown credit type.");
        }
        return depositCredits(player, cardId, creditId);
    }

    public Result withdrawCreditType(Player player, String cardId, String creditId, int itemAmount) {
        if (creditId == null || !CREDIT_VALUES.containsKey(creditId)) {
            return Result.fail("Unknown credit type.");
        }
        return withdrawCredits(player, cardId, creditId, itemAmount);
    }

    public List<CreditDepositOption> getDepositOptions(Player player) {
        List<CreditDepositOption> options = new ArrayList<>();
        if (player == null) {
            return options;
        }
        Map<String, ItemStack> templates = loadCreditTemplates();
        if (templates.isEmpty()) {
            return options;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String id : CREDIT_IDS) {
            counts.put(id, 0);
        }
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            String creditId = creditId(item, templates);
            if (creditId != null) {
                counts.put(creditId, counts.getOrDefault(creditId, 0) + item.getAmount());
            }
        }
        for (String id : CREDIT_IDS) {
            int amount = counts.getOrDefault(id, 0);
            if (amount > 0) {
                options.add(new CreditDepositOption(id, CREDIT_VALUES.get(id), amount));
            }
        }
        return options;
    }

    public List<CreditWithdrawalOption> getWithdrawalOptions(String cardId) {
        List<CreditWithdrawalOption> options = new ArrayList<>();
        BankCard card = database.getCard(cardId);
        if (card == null || card.frozen()) {
            return options;
        }
        BankAccount account = database.getAccount(card.accountId());
        if (account == null || account.balance() <= 0L) {
            return options;
        }
        Map<String, ItemStack> templates = loadCreditTemplates();
        if (templates.isEmpty()) {
            return options;
        }
        for (String id : CREDIT_IDS) {
            ItemStack template = templates.get(id);
            Long value = CREDIT_VALUES.get(id);
            if (template == null || template.getType().isAir() || value == null || value > account.balance()) {
                continue;
            }
            int maxStack = Math.max(1, template.getMaxStackSize());
            long affordable = account.balance() / value;
            int maxSingleWithdrawAmount = (int) Math.min(affordable, maxStack);
            if (maxSingleWithdrawAmount > 0) {
                options.add(new CreditWithdrawalOption(id, value, affordable, maxSingleWithdrawAmount));
            }
        }
        return options;
    }

    private Result depositCredits(Player player, String cardId, String selectedCreditId) {
        if (player == null) {
            return Result.fail("Only players can deposit credits.");
        }
        BankCard card = database.getCard(cardId);
        if (card == null) {
            return Result.fail("Insert a valid credit card first.");
        }
        if (card.frozen()) {
            return Result.fail("This credit card is frozen.");
        }
        BankAccount account = database.getAccount(card.accountId());
        if (account == null) {
            return Result.fail("Card account not found.");
        }
        Map<String, ItemStack> templates = loadCreditTemplates();
        if (templates.isEmpty()) {
            return Result.fail("OmVeins credit items are not available.");
        }
        long total = 0L;
        ItemStack[] contents = player.getInventory().getStorageContents();
        Set<Integer> matchedSlots = new LinkedHashSet<>();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            String creditId = creditId(item, templates);
            if (creditId == null || (selectedCreditId != null && !selectedCreditId.equals(creditId))) {
                continue;
            }
            Long value = CREDIT_VALUES.get(creditId);
            total += value * item.getAmount();
            matchedSlots.add(slot);
        }
        if (total <= 0L) {
            return Result.fail(selectedCreditId == null ? "No credit items found in your inventory."
                    : "No " + selectedCreditId + " items found in your inventory.");
        }
        if (!database.deposit(account.accountId(), total)) {
            return Result.fail("Failed to deposit credits.");
        }
        for (Integer slot : matchedSlots) {
            contents[slot] = null;
        }
        player.getInventory().setStorageContents(contents);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        return Result.ok("Deposited " + total + " credits. Balance: " + accountBalance(account.accountId()) + ".");
    }

    private Result withdrawCredits(Player player, String cardId, String creditId, int requestedItemAmount) {
        if (player == null) {
            return Result.fail("Only players can withdraw credits.");
        }
        BankCard card = database.getCard(cardId);
        if (card == null) {
            return Result.fail("Insert a valid credit card first.");
        }
        if (card.frozen()) {
            return Result.fail("This credit card is frozen.");
        }
        BankAccount account = database.getAccount(card.accountId());
        if (account == null) {
            return Result.fail("Card account not found.");
        }
        if (requestedItemAmount <= 0) {
            return Result.fail("Withdraw amount must be positive.");
        }
        ItemStack item = createCreditItem(creditId);
        if (item == null) {
            return Result.fail("OmVeins " + creditId + " item is not available.");
        }
        Long value = CREDIT_VALUES.get(creditId);
        int itemAmount = Math.min(requestedItemAmount, Math.max(1, item.getMaxStackSize()));
        long total = value * itemAmount;
        if (account.balance() < total) {
            return Result.fail("Insufficient balance.");
        }
        item.setAmount(itemAmount);
        if (!database.withdraw(account.accountId(), total)) {
            return Result.fail("Failed to withdraw credits.");
        }
        giveOrDrop(player, item);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.1f);
        return Result.ok("Withdrew " + total + " credits. Balance: " + accountBalance(account.accountId()) + ".");
    }

    public Result validateAtmCard(String cardId) {
        if (cardId == null || cardId.isBlank()) {
            return Result.fail("Insert a credit card first.");
        }
        BankCard card = database.getCard(cardId);
        if (card == null) {
            return Result.fail("Credit card not found.");
        }
        if (card.frozen()) {
            return Result.fail("This credit card is frozen.");
        }
        if (database.getAccount(card.accountId()) == null) {
            return Result.fail("Card account not found.");
        }
        return Result.ok("Credit card accepted.");
    }

    public long accountBalance(String accountId) {
        BankAccount account = database.getAccount(accountId);
        return account == null ? 0L : account.balance();
    }

    private BankAccount getAccountByName(String name) {
        for (BankAccount account : listAccounts()) {
            if (account.displayName().equalsIgnoreCase(name.trim())) {
                return account;
            }
        }
        return null;
    }

    public Result registerOmVeinsItems() {
        if (!OmVeinsAPI.isInitialized()) {
            return Result.fail("OmVeins API is not initialized.");
        }
        try {
            boolean added = OmVeinsAPI.addItem(CASH_REGISTER_ITEM_ID, createCashRegisterTemplate());
            return Result.ok(added ? "Registered cash_register in OmVeins ItemDatabase."
                    : "cash_register is already registered in OmVeins ItemDatabase.");
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to register Bank items in OmVeins: " + ex.getMessage());
            return Result.fail("Failed to register Bank items in OmVeins.");
        }
    }

    private ItemStack createCardItem(BankCard card) {
        if (!OmVeinsAPI.isInitialized()) {
            return null;
        }
        ItemStack item;
        try {
            item = OmVeinsAPI.getItem(CREDIT_CARD_ITEM_ID);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to get OmVeins credit_card item: " + ex.getMessage());
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !hasOmVeinsCreditCardMarker(meta)) {
            plugin.getLogger().warning("OmVeins credit_card item is missing om:credit_card=true persistent data.");
            return null;
        }
        meta.displayName(Component.text("Credit Card - " + card.ownerName(), NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text("Owner: " + card.ownerName(), NamedTextColor.GRAY),
                Component.text("Card: " + shortId(card.cardId()), NamedTextColor.DARK_GRAY),
                Component.text("Usable by anyone unless frozen.", NamedTextColor.YELLOW)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(cardIdKey, PersistentDataType.STRING, card.cardId());
        item.setItemMeta(meta);
        if (!isOmVeinsCreditCard(item)) {
            plugin.getLogger().warning("Bank credit card metadata update removed OmVeins credit_card marker.");
            return null;
        }
        return item;
    }

    private ItemStack createCashRegisterTemplate() {
        ItemStack item = new ItemStack(Material.COMPARATOR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Cash Register", NamedTextColor.GOLD));
        meta.lore(List.of(Component.text("Bank terminal item.", NamedTextColor.GRAY)));
        meta.setItemModel(new NamespacedKey("om", "cash_register"));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    ItemStack createCashRegisterItem(BankTerminal terminal) {
        ItemStack item = createRegisteredCashRegisterBase();
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(terminal.name(), NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("Account: " + terminal.ownerName(), NamedTextColor.GRAY),
                Component.text("Terminal: " + shortId(terminal.terminalId()), NamedTextColor.DARK_GRAY)
        ));
        meta.getPersistentDataContainer().set(terminalIdKey, PersistentDataType.STRING, terminal.terminalId());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTerminalEditorItem(BankTerminal terminal) {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Terminal Editor - " + terminal.name(), NamedTextColor.LIGHT_PURPLE));
        meta.lore(List.of(
                Component.text("Left-click: terminal item list", NamedTextColor.GRAY),
                Component.text("Right-click selected block: bind pending item", NamedTextColor.GRAY),
                Component.text("Drop or End Edit to remove.", NamedTextColor.YELLOW)
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(terminalIdKey, PersistentDataType.STRING, terminal.terminalId());
        meta.getPersistentDataContainer().set(terminalEditorKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRegisteredCashRegisterBase() {
        if (OmVeinsAPI.isInitialized()) {
            try {
                return OmVeinsAPI.getItem(CASH_REGISTER_ITEM_ID);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean canEditTerminalItem(Player player, BankTerminalItem item) {
        if (player == null || item == null) {
            return false;
        }
        BankTerminal terminal = database.getTerminal(item.terminalId());
        return terminal != null && canEditAccount(player, terminal.accountId());
    }

    private boolean isOmVeinsCreditCard(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return hasOmVeinsCreditCardMarker(item.getItemMeta());
    }

    private boolean isTerminalEditorItem(ItemStack item) {
        return readTerminalEditorId(item) != null;
    }

    private boolean hasOmVeinsCreditCardMarker(ItemMeta meta) {
        Boolean value = meta.getPersistentDataContainer().get(omCreditCardKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(value);
    }

    private Map<String, ItemStack> loadCreditTemplates() {
        Map<String, ItemStack> templates = new LinkedHashMap<>();
        if (!OmVeinsAPI.isInitialized()) {
            return templates;
        }
        for (String id : CREDIT_IDS) {
            try {
                templates.put(id, OmVeinsAPI.getItem(id));
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to get OmVeins credit item " + id + ": " + ex.getMessage());
            }
        }
        return templates;
    }

    private ItemStack createCreditItem(String creditId) {
        if (!OmVeinsAPI.isInitialized()) {
            return null;
        }
        try {
            return OmVeinsAPI.getItem(creditId);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to get OmVeins credit item " + creditId + ": " + ex.getMessage());
            return null;
        }
    }

    private String firstCardId(Player player, ItemStack cursorItem) {
        String cardId = readCardId(cursorItem);
        if (cardId != null) {
            return cardId;
        }
        if (player == null) {
            return null;
        }
        cardId = readCardId(player.getInventory().getItemInMainHand());
        if (cardId != null) {
            return cardId;
        }
        return readCardId(player.getInventory().getItemInOffHand());
    }

    private String creditId(ItemStack item, Map<String, ItemStack> templates) {
        for (Map.Entry<String, ItemStack> entry : templates.entrySet()) {
            ItemStack template = entry.getValue();
            if (template != null && template.isSimilar(item)) {
                return entry.getKey();
            }
        }
        return null;
    }

    void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            if (leftover != null) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private int removeTerminalEditorItems(Player player, String terminalId) {
        int removed = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            String editorTerminalId = readTerminalEditorId(item);
            if (editorTerminalId == null) {
                continue;
            }
            if (terminalId == null || terminalId.equals(editorTerminalId)) {
                contents[slot] = null;
                removed++;
            }
        }
        player.getInventory().setContents(contents);
        return removed;
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

    public record CreditDepositOption(String creditId, long value, int amount) {
        public long total() {
            return value * amount;
        }
    }

    public record CreditWithdrawalOption(String creditId, long value, long affordableAmount, int maxSingleWithdrawAmount) {
    }
}
