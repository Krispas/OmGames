package krispasi.omGames.chess;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

final class ChessGuiController {
    private static final String LOBBY_WORLD = "bedwars_lobby";
    private final ChessManager manager;
    private final NamespacedKey actionKey;
    private final Map<UUID, PlayerOptions> optionsByPlayer = new HashMap<>();
    private final Map<UUID, PendingChallenge> pendingByTarget = new HashMap<>();
    private final Map<UUID, UUID> pendingTargetByChallenger = new HashMap<>();

    ChessGuiController(JavaPlugin plugin, ChessManager manager) {
        this.manager = manager;
        this.actionKey = new NamespacedKey(plugin, "chess_gui_action");
    }

    void openMenu(Player player) {
        Inventory inventory = inventory(GuiType.MENU, player.getUniqueId(), 45, "Chess menu");
        fill(inventory);
        inventory.setItem(13, item("New match", NamedTextColor.GREEN, "om:play_icon"));
        inventory.setItem(29, item("Settings", NamedTextColor.WHITE, "om:settings_icon"));
        inventory.setItem(30, item("Previous preset", NamedTextColor.WHITE, "om:left_arrow"));
        inventory.setItem(31, presetItem(options(player).menuPreset()));
        inventory.setItem(32, item("Next preset", NamedTextColor.WHITE, "om:right_arrow"));
        inventory.setItem(33, item("Coming soon", NamedTextColor.DARK_GRAY, "minecraft:armor_stand"));
        player.openInventory(inventory);
    }

    boolean handleInventoryClick(Player player, Inventory inventory, int slot) {
        if (!(inventory.getHolder() instanceof ChessGuiHolder holder)) {
            return false;
        }
        if (slot < 0 || slot >= inventory.getSize()) {
            return true;
        }
        PlayerOptions options = options(player);
        switch (holder.type()) {
            case MENU -> handleMenuClick(player, slot, options);
            case SETTINGS -> handleSettingsClick(player, slot, options);
            case SET_TIMER -> handleSetTimerClick(player, slot, options);
            case PLAYER_SELECTOR -> handlePlayerSelectorClick(player, slot);
            case CHALLENGE -> handleChallengeClick(player, slot);
            case SELECT_GAME -> handleSelectGameClick(player, slot);
            case CHANGE_SETTINGS -> handleChangeSettingsClick(player, slot);
            case CONFIRM -> handleConfirmClick(player, slot);
            case NEXT_MATCH -> handleNextMatchClick(player, slot, options);
        }
        return true;
    }

    boolean handleInventoryClose(Player player, Inventory inventory) {
        if (!(inventory.getHolder() instanceof ChessGuiHolder holder)) {
            return false;
        }
        if (holder.type() == GuiType.CHALLENGE) {
            PendingChallenge challenge = pendingByTarget.remove(player.getUniqueId());
            if (challenge != null) {
                pendingTargetByChallenger.remove(challenge.challengerId());
            }
        }
        return true;
    }

    boolean handleHotbarInteract(Player player, ItemStack item) {
        String action = action(item);
        if (action == null) {
            return false;
        }
        switch (action) {
            case "settings" -> openChangeSettings(player);
            case "pause" -> send(player, manager.togglePause(player, null));
            case "undo", "backward" -> send(player, manager.undo(player, player.isOp()));
            case "redo" -> send(player, manager.redo(player, player.isOp()));
            case "forward" -> send(player, manager.forward(player));
            case "review_backward" -> send(player, manager.backward(player));
            case "confirm" -> openConfirm(player);
            case "rematch" -> openNextMatch(player);
            case "exit" -> {
                player.getInventory().clear();
                manager.resetPlayer(player);
                if (player.getRespawnLocation() != null) {
                    player.teleport(player.getRespawnLocation());
                } else if (player.getWorld() != null) {
                    player.teleport(player.getWorld().getSpawnLocation());
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    void giveInGameHotbar(Player player, boolean allowUndo) {
        player.getInventory().clear();
        player.getInventory().setItem(0, hotbar("Settings", "om:settings_icon", "settings"));
        player.getInventory().setItem(1, hotbar("Pause game", "om:pause_icon", "pause"));
        player.getInventory().setItem(4, hotbar(allowUndo ? "Undo" : "Backward", "om:left_arrow", allowUndo ? "undo" : "backward"));
        player.getInventory().setItem(5, hotbar(allowUndo ? "Redo" : "Forward", "om:right_arrow", allowUndo ? "redo" : "forward"));
        player.getInventory().setItem(8, hotbar("Terminate game", "minecraft:white_banner", "confirm"));
    }

    void giveSpectatorHotbar(Player player) {
        player.getInventory().clear();
        player.getInventory().setItem(8, hotbar("Exit", "om:home_icon", "exit"));
    }

    void giveEndGameHotbar(Player player) {
        player.getInventory().clear();
        player.getInventory().setItem(0, hotbar("Settings", "om:settings_icon", "settings"));
        player.getInventory().setItem(1, hotbar("Rematch", "om:reroll_icon", "rematch"));
        player.getInventory().setItem(4, hotbar("Backward", "om:left_arrow", "review_backward"));
        player.getInventory().setItem(5, hotbar("Forward", "om:right_arrow", "forward"));
        player.getInventory().setItem(8, hotbar("Exit", "om:home_icon", "exit"));
    }

    private void handleMenuClick(Player player, int slot, PlayerOptions options) {
        if (slot == 13) {
            openPlayerSelector(player);
        } else if (slot == 29) {
            openSettings(player);
        } else if (slot == 30 || slot == 32) {
            options.setMenuPreset(nextMenuPreset(options.menuPreset(), slot == 32 ? 1 : -1));
            applyMenuPreset(options);
            openMenu(player);
        }
    }

    private void handleSettingsClick(Player player, int slot, PlayerOptions options) {
        if (slot == 21) {
            options.setStartingSide(ChessSide.WHITE);
            openSettings(player);
        } else if (slot == 22) {
            options.setStartingSide(null);
            openSettings(player);
        } else if (slot == 23) {
            options.setStartingSide(ChessSide.BLACK);
            openSettings(player);
        } else if (slot == 27 || slot == 29) {
            options.setTimerPreset(nextTimerPreset(options.timerPreset(), slot == 29 ? 1 : -1));
            applyTimerPreset(options);
            openSettings(player);
        } else if (slot == 28) {
            openSetTimer(player);
        } else if (slot == 32) {
            options.setShowMovementHints(!options.showMovementHints());
            openSettings(player);
        } else if (slot == 33) {
            options.setFigureStyle(options.figureStyle() == ChessSettings.FigureStyle.DEFAULT
                    ? ChessSettings.FigureStyle.FLAT : ChessSettings.FigureStyle.DEFAULT);
            openSettings(player);
        } else if (slot == 34) {
            options.setAllowUndo(!options.allowUndo());
            openSettings(player);
        } else if (slot == 48) {
            optionsByPlayer.put(player.getUniqueId(), new PlayerOptions());
            openSettings(player);
        } else if (slot == 50) {
            openMenu(player);
        }
    }

    private void handleSetTimerClick(Player player, int slot, PlayerOptions options) {
        switch (slot) {
            case 1 -> options.addMoveBonusMillis(-60_000L);
            case 2 -> options.addMoveBonusMillis(-10_000L);
            case 3 -> options.addMoveBonusMillis(-1_000L);
            case 5 -> options.addMoveBonusMillis(1_000L);
            case 6 -> options.addMoveBonusMillis(10_000L);
            case 7 -> options.addMoveBonusMillis(60_000L);
            case 10 -> options.addCheckBonusMillis(-60_000L);
            case 11 -> options.addCheckBonusMillis(-10_000L);
            case 12 -> options.addCheckBonusMillis(-1_000L);
            case 14 -> options.addCheckBonusMillis(1_000L);
            case 15 -> options.addCheckBonusMillis(10_000L);
            case 16 -> options.addCheckBonusMillis(60_000L);
            case 22 -> { openSettings(player); return; }
            default -> { return; }
        }
        openSetTimer(player);
    }
    private void handlePlayerSelectorClick(Player player, int slot) {
        if (slot == 26) {
            openMenu(player);
            return;
        }
        if (slot == 8) {
            openSelectGame(player);
            return;
        }
        ItemStack clicked = player.getOpenInventory().getTopInventory().getItem(slot);
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD || !(clicked.getItemMeta() instanceof SkullMeta meta)
                || meta.getOwningPlayer() == null) {
            return;
        }
        Player target = Bukkit.getPlayer(meta.getOwningPlayer().getUniqueId());
        if (target == null || target.equals(player)) {
            return;
        }
        UUID oldTarget = pendingTargetByChallenger.remove(player.getUniqueId());
        if (oldTarget != null) {
            pendingByTarget.remove(oldTarget);
        }
        PlayerOptions snapshot = options(player).copy();
        pendingByTarget.put(target.getUniqueId(), new PendingChallenge(player.getUniqueId(), snapshot));
        pendingTargetByChallenger.put(player.getUniqueId(), target.getUniqueId());
        openChallenge(target, player);
        player.sendMessage(Component.text("Chess offer sent to " + target.getName() + ".", NamedTextColor.GREEN));
    }

    private void handleChallengeClick(Player player, int slot) {
        PendingChallenge challenge = pendingByTarget.remove(player.getUniqueId());
        if (challenge == null) {
            player.closeInventory();
            return;
        }
        pendingTargetByChallenger.remove(challenge.challengerId());
        Player challenger = Bukkit.getPlayer(challenge.challengerId());
        if (slot == 1 && challenger != null) {
            send(player, manager.startGuiMatch(challenger, player, challenge.options()));
            player.closeInventory();
            challenger.closeInventory();
        } else if (slot == 3 && challenger != null) {
            challenger.sendMessage(Component.text(player.getName() + " declined the chess match.", NamedTextColor.YELLOW));
            player.closeInventory();
        }
    }

    private void handleSelectGameClick(Player player, int slot) {
        if (slot == 26) {
            openPlayerSelector(player);
            return;
        }
        ItemStack clicked = player.getOpenInventory().getTopInventory().getItem(slot);
        String target = action(clicked);
        if (target != null && target.startsWith("spectate:")) {
            send(player, manager.spectate(player, target.substring("spectate:".length())));
            player.closeInventory();
        }
    }

    private void handleChangeSettingsClick(Player player, int slot) {
        PlayerOptions options = options(player);
        if (slot == 0) {
            options.setShowMovementHints(!options.showMovementHints());
            manager.setSetting(player, null, "visualize_movement_check", options.showMovementHints());
            openChangeSettings(player);
        } else if (slot == 2) {
            player.closeInventory();
        } else if (slot == 4) {
            options.setFigureStyle(options.figureStyle() == ChessSettings.FigureStyle.DEFAULT
                    ? ChessSettings.FigureStyle.FLAT : ChessSettings.FigureStyle.DEFAULT);
            manager.setFigureStyle(player, null, options.figureStyle() == ChessSettings.FigureStyle.FLAT ? "flat" : "default");
            openChangeSettings(player);
        }
    }

    private void handleConfirmClick(Player player, int slot) {
        if (slot == 0) {
            send(player, manager.voteDraw(player));
            player.closeInventory();
        } else if (slot == 2) {
            player.closeInventory();
        } else if (slot == 4) {
            send(player, manager.resign(player));
            player.closeInventory();
        }
    }

    private void handleNextMatchClick(Player player, int slot, PlayerOptions options) {
        if (slot == 22) {
            player.closeInventory();
        } else if (slot == 1 || slot == 3) {
            options.setTimerPreset(nextTimerPreset(options.timerPreset(), slot == 3 ? 1 : -1));
            applyTimerPreset(options);
            openNextMatch(player);
        } else if (slot == 2) {
            openSetTimer(player);
        } else if (slot == 12) {
            options.setShowMovementHints(!options.showMovementHints());
            openNextMatch(player);
        } else if (slot == 13) {
            options.setFigureStyle(options.figureStyle() == ChessSettings.FigureStyle.DEFAULT
                    ? ChessSettings.FigureStyle.FLAT : ChessSettings.FigureStyle.DEFAULT);
            openNextMatch(player);
        } else if (slot == 14) {
            options.setAllowUndo(!options.allowUndo());
            openNextMatch(player);
        }
    }

    private void openSettings(Player player) {
        PlayerOptions options = options(player);
        Inventory inventory = inventory(GuiType.SETTINGS, player.getUniqueId(), 54, "Settings");
        fill(inventory);
        inventory.setItem(21, colorItem("Starting color", "om:white_pawn_icon", options.startingSide() == ChessSide.WHITE, "White"));
        inventory.setItem(22, colorItem("Starting color", "om:selected_pawn_icon", options.startingSide() == null, "Random"));
        inventory.setItem(23, colorItem("Starting color", "om:black_pawn_icon", options.startingSide() == ChessSide.BLACK, "Black"));
        inventory.setItem(27, item("Previous preset", NamedTextColor.WHITE, "om:left_arrow"));
        inventory.setItem(28, timerPresetItem(options.timerPreset()));
        inventory.setItem(29, item("Next preset", NamedTextColor.WHITE, "om:right_arrow"));
        inventory.setItem(32, toggleItem("Show movement hints", options.showMovementHints(), "om:hint1", "om:hint0"));
        inventory.setItem(33, styleItem(options.figureStyle()));
        inventory.setItem(34, toggleItem("Allow undo", options.allowUndo(), "om:undo_icon", "om:no_undo_icon"));
        inventory.setItem(48, item("Reset settings", NamedTextColor.DARK_RED, "om:filled_reroll"));
        inventory.setItem(50, item("Go back", NamedTextColor.WHITE, "om:filled_home"));
        player.openInventory(inventory);
    }

    private void openSetTimer(Player player) {
        PlayerOptions options = options(player);
        Inventory inventory = inventory(GuiType.SET_TIMER, player.getUniqueId(), 27, "Set timer");
        fill(inventory);
        String[] labels = {"-1m", "-10s", "-1s", "+1s", "+10s", "+1m"};
        int[] firstRow = {1, 2, 3, 5, 6, 7};
        int[] secondRow = {10, 11, 12, 14, 15, 16};
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            inventory.setItem(firstRow[i], item(label, label.startsWith("-") ? NamedTextColor.DARK_RED : NamedTextColor.DARK_GREEN,
                    label.startsWith("-") ? "om:left_arrow" : "om:right_arrow"));
            inventory.setItem(secondRow[i], item(label, label.startsWith("-") ? NamedTextColor.DARK_RED : NamedTextColor.DARK_GREEN,
                    label.startsWith("-") ? "om:left_arrow" : "om:right_arrow"));
        }
        inventory.setItem(4, display("Time added after move set to:", formatDuration(options.moveBonusMillis()), "om:selected_pawn_icon"));
        inventory.setItem(13, display("Time added after check set to:", formatDuration(options.checkBonusMillis()), "om:selected_king_icon"));
        inventory.setItem(22, item("Go back", NamedTextColor.WHITE, "om:filled_home"));
        player.openInventory(inventory);
    }

    private void openPlayerSelector(Player player) {
        Inventory inventory = inventory(GuiType.PLAYER_SELECTOR, player.getUniqueId(), 27, "Player selector");
        fill(inventory);
        World lobby = Bukkit.getWorld(NamespacedKey.minecraft(LOBBY_WORLD));
        if (lobby == null) {
            lobby = Bukkit.getWorld(LOBBY_WORLD);
        }
        int[] slots = {1, 2, 3, 4};
        int slotIndex = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player) || lobby == null || !online.getWorld().equals(lobby)) {
                continue;
            }
            if (slotIndex >= slots.length) {
                break;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(online);
            meta.displayName(Component.text(online.getName(), NamedTextColor.WHITE));
            head.setItemMeta(meta);
            inventory.setItem(slots[slotIndex++], head);
        }
        inventory.setItem(8, item("Spectate game", NamedTextColor.WHITE, "minecraft:ender_eye"));
        inventory.setItem(26, item("Go back", NamedTextColor.WHITE, "om:filled_home"));
        player.openInventory(inventory);
    }

    private void openChallenge(Player target, Player challenger) {
        Inventory inventory = inventory(GuiType.CHALLENGE, target.getUniqueId(), 5, "Player wants a match");
        fill(inventory);
        inventory.setItem(1, item("Start", NamedTextColor.GREEN, "minecraft:lime_wool"));
        inventory.setItem(3, item("Cancel", NamedTextColor.RED, "minecraft:red_wool"));
        target.openInventory(inventory);
        target.sendMessage(Component.text(challenger.getName() + " wants a chess match.", NamedTextColor.YELLOW));
    }

    private void openSelectGame(Player player) {
        Inventory inventory = inventory(GuiType.SELECT_GAME, player.getUniqueId(), 27, "Select game");
        fill(inventory);
        int slot = 1;
        for (String match : manager.getActiveMatchTimestamps()) {
            ItemStack item = item("Game: " + match, NamedTextColor.WHITE, "om:white_pawn_icon");
            setAction(item, "spectate:" + match);
            inventory.setItem(slot++, item);
            if (slot >= 26) {
                break;
            }
        }
        inventory.setItem(26, item("Go back", NamedTextColor.WHITE, "om:filled_home"));
        player.openInventory(inventory);
    }

    private void openChangeSettings(Player player) {
        PlayerOptions options = options(player);
        Inventory inventory = inventory(GuiType.CHANGE_SETTINGS, player.getUniqueId(), 5, "Change settings");
        fill(inventory);
        inventory.setItem(0, toggleItem("Show movement hints", options.showMovementHints(), "om:hint1", "om:hint0"));
        inventory.setItem(2, item("Go back", NamedTextColor.WHITE, "om:filled_home"));
        inventory.setItem(4, styleItem(options.figureStyle()));
        player.openInventory(inventory);
    }

    private void openConfirm(Player player) {
        Inventory inventory = inventory(GuiType.CONFIRM, player.getUniqueId(), 5, "Confirm");
        fill(inventory);
        inventory.setItem(0, item("Offer draw", NamedTextColor.WHITE, "minecraft:white_banner"));
        inventory.setItem(2, item("Go back", NamedTextColor.WHITE, "om:filled_home"));
        inventory.setItem(4, item("Resign", NamedTextColor.WHITE, "minecraft:lime_banner"));
        player.openInventory(inventory);
    }

    private void openNextMatch(Player player) {
        Inventory inventory = inventory(GuiType.NEXT_MATCH, player.getUniqueId(), 27, "Set up next match");
        fill(inventory);
        PlayerOptions options = options(player);
        inventory.setItem(1, item("Previous preset", NamedTextColor.WHITE, "om:left_arrow"));
        inventory.setItem(2, timerPresetItem(options.timerPreset()));
        inventory.setItem(3, item("Next preset", NamedTextColor.WHITE, "om:right_arrow"));
        inventory.setItem(12, toggleItem("Show movement hints", options.showMovementHints(), "om:hint1", "om:hint0"));
        inventory.setItem(13, styleItem(options.figureStyle()));
        inventory.setItem(14, toggleItem("Allow undo", options.allowUndo(), "om:undo_icon", "om:no_undo_icon"));
        inventory.setItem(22, item("Go back", NamedTextColor.WHITE, "om:filled_home"));
        player.openInventory(inventory);
    }

    private Inventory inventory(GuiType type, UUID owner, int size, String title) {
        return Bukkit.createInventory(new ChessGuiHolder(type, owner), size, Component.text(title));
    }

    private PlayerOptions options(Player player) {
        return optionsByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerOptions());
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item("", NamedTextColor.WHITE, "om:filled");
        ItemMeta meta = filler.getItemMeta();
        meta.setHideTooltip(true);
        filler.setItemMeta(meta);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler.clone());
        }
    }

    private ItemStack hotbar(String name, String model, String action) {
        ItemStack item = item(name, name.equals("Exit") ? NamedTextColor.RED : NamedTextColor.WHITE, model);
        setAction(item, action);
        return item;
    }

    private ItemStack item(String name, NamedTextColor color, String model) {
        ItemStack stack = new ItemStack(Material.IRON_NUGGET);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color));
        NamespacedKey modelKey = NamespacedKey.fromString(model);
        if (modelKey != null) {
            meta.setItemModel(modelKey);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack display(String name, String value, String model) {
        ItemStack stack = item(name, NamedTextColor.WHITE, model);
        ItemMeta meta = stack.getItemMeta();
        meta.lore(List.of(Component.text(" " + value, NamedTextColor.GRAY)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack toggleItem(String name, boolean value, String trueModel, String falseModel) {
        ItemStack stack = item(name, NamedTextColor.WHITE, value ? trueModel : falseModel);
        ItemMeta meta = stack.getItemMeta();
        meta.lore(List.of(Component.text(" - " + value, value ? NamedTextColor.DARK_GREEN : NamedTextColor.DARK_RED)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack styleItem(ChessSettings.FigureStyle style) {
        boolean flat = style == ChessSettings.FigureStyle.FLAT;
        ItemStack stack = item("Style of figures", NamedTextColor.WHITE, flat ? "om:selected_king_icon" : "om:selected_king");
        ItemMeta meta = stack.getItemMeta();
        meta.lore(List.of(Component.text(" - " + (flat ? "flat" : "3D"), NamedTextColor.GRAY)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack colorItem(String name, String baseModel, boolean selected, String label) {
        String model = baseModel;
        if (selected) {
            model = switch (label) {
                case "White" -> "om:framed_white_pawn_icon";
                case "Black" -> "om:framed_black_pawn_icon";
                default -> "om:framed_selected_pawn_icon";
            };
        }
        ItemStack stack = item(name, NamedTextColor.WHITE, model);
        ItemMeta meta = stack.getItemMeta();
        meta.lore(List.of(Component.text(" - " + label, NamedTextColor.GRAY)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack presetItem(MenuPreset preset) {
        ItemStack stack = switch (preset) {
            case FRIENDLY -> item("Friendly preset", NamedTextColor.GREEN, "minecraft:lime_wool");
            case TRAINING -> item("Training preset", NamedTextColor.GOLD, "minecraft:orange_wool");
            case DUEL -> item("Duel preset", NamedTextColor.RED, "minecraft:red_wool");
            case BLITZ -> item("Blitz preset", NamedTextColor.DARK_AQUA, "minecraft:cyan_wool");
            case BEGINNER -> item("Beginner preset", NamedTextColor.LIGHT_PURPLE, "minecraft:pink_wool");
        };
        ItemMeta meta = stack.getItemMeta();
        String description = switch (preset) {
            case FRIENDLY -> "Untimed game with undo enabled.";
            case TRAINING -> "30 minute game with undo enabled.";
            case DUEL -> "18 minute game with a 10 second move bonus.";
            case BLITZ -> "5 minute game with a 5 second move bonus.";
            case BEGINNER -> "Untimed game with movement hints enabled.";
        };
        meta.lore(List.of(Component.text(description, NamedTextColor.GRAY)));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack timerPresetItem(TimerPreset preset) {
        ItemStack stack = item("Match timer preset", NamedTextColor.WHITE, "minecraft:clock");
        ItemMeta meta = stack.getItemMeta();
        meta.lore(List.of(
                Component.text(" " + preset.name().toLowerCase(Locale.ROOT), NamedTextColor.GRAY),
                Component.text(" - click to edit", NamedTextColor.DARK_GRAY)
        ));
        stack.setItemMeta(meta);
        return stack;
    }

    private void setAction(ItemStack item, String action) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
    }

    private String action(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private MenuPreset nextMenuPreset(MenuPreset current, int delta) {
        MenuPreset[] values = MenuPreset.values();
        return values[Math.floorMod(current.ordinal() + delta, values.length)];
    }

    private TimerPreset nextTimerPreset(TimerPreset current, int delta) {
        TimerPreset[] values = TimerPreset.values();
        return values[Math.floorMod(current.ordinal() + delta, values.length)];
    }

    private void applyMenuPreset(PlayerOptions options) {
        options.setStartingSide(null);
        switch (options.menuPreset()) {
            case FRIENDLY -> {
                options.setTimerPreset(TimerPreset.UNLIMITED);
                options.setTimer(false, 0L, 0L, 0L);
                options.setAllowUndo(true);
                options.setShowMovementHints(false);
            }
            case TRAINING -> {
                options.setTimerPreset(TimerPreset.FRIENDLY);
                options.setTimer(true, 30L * 60_000L, 0L, 0L);
                options.setAllowUndo(true);
                options.setShowMovementHints(false);
            }
            case DUEL -> {
                options.setTimerPreset(TimerPreset.DUEL);
                options.setTimer(true, 18L * 60_000L, 10_000L, 0L);
                options.setAllowUndo(false);
                options.setShowMovementHints(false);
            }
            case BLITZ -> {
                options.setTimerPreset(TimerPreset.BLITZ);
                options.setTimer(true, 5L * 60_000L, 5_000L, 0L);
                options.setAllowUndo(false);
                options.setShowMovementHints(false);
            }
            case BEGINNER -> {
                options.setTimerPreset(TimerPreset.UNLIMITED);
                options.setTimer(false, 0L, 0L, 0L);
                options.setAllowUndo(true);
                options.setShowMovementHints(true);
            }
        }
    }

    private void applyTimerPreset(PlayerOptions options) {
        switch (options.timerPreset()) {
            case UNLIMITED -> options.setTimer(false, 0L, 0L, 0L);
            case FRIENDLY -> options.setTimer(true, 30L * 60_000L, 0L, 30_000L);
            case DUEL -> options.setTimer(true, 18L * 60_000L, 10_000L, 0L);
            case BLITZ -> options.setTimer(true, 5L * 60_000L, 5_000L, 0L);
        }
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        return seconds / 3600L + "h " + seconds % 3600L / 60L + "m " + seconds % 60L + "s";
    }

    private void send(Player player, ChessManager.Result result) {
        player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    enum GuiType {
        MENU,
        SETTINGS,
        SET_TIMER,
        PLAYER_SELECTOR,
        CHALLENGE,
        SELECT_GAME,
        CHANGE_SETTINGS,
        CONFIRM,
        NEXT_MATCH
    }

    enum MenuPreset {
        FRIENDLY,
        TRAINING,
        DUEL,
        BLITZ,
        BEGINNER
    }

    enum TimerPreset {
        UNLIMITED,
        FRIENDLY,
        DUEL,
        BLITZ
    }

    record PendingChallenge(UUID challengerId, PlayerOptions options) {
    }

    record ChessGuiHolder(GuiType type, UUID ownerId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    static final class PlayerOptions {
        private ChessSide startingSide;
        private MenuPreset menuPreset = MenuPreset.FRIENDLY;
        private TimerPreset timerPreset = TimerPreset.UNLIMITED;
        private boolean timerEnabled;
        private long initialMillis;
        private long moveBonusMillis;
        private long checkBonusMillis;
        private boolean showMovementHints;
        private boolean allowUndo = true;
        private ChessSettings.FigureStyle figureStyle = ChessSettings.FigureStyle.DEFAULT;

        ChessSide startingSide() {
            return startingSide;
        }

        void setStartingSide(ChessSide startingSide) {
            this.startingSide = startingSide;
        }

        MenuPreset menuPreset() {
            return menuPreset;
        }

        void setMenuPreset(MenuPreset menuPreset) {
            this.menuPreset = menuPreset == null ? MenuPreset.FRIENDLY : menuPreset;
        }

        TimerPreset timerPreset() {
            return timerPreset;
        }

        void setTimerPreset(TimerPreset timerPreset) {
            this.timerPreset = timerPreset == null ? TimerPreset.UNLIMITED : timerPreset;
        }

        long initialMillis() {
            return initialMillis;
        }

        long moveBonusMillis() {
            return moveBonusMillis;
        }

        long checkBonusMillis() {
            return checkBonusMillis;
        }

        boolean showMovementHints() {
            return showMovementHints;
        }

        void setShowMovementHints(boolean showMovementHints) {
            this.showMovementHints = showMovementHints;
        }

        boolean allowUndo() {
            return allowUndo;
        }

        void setAllowUndo(boolean allowUndo) {
            this.allowUndo = allowUndo;
        }

        ChessSettings.FigureStyle figureStyle() {
            return figureStyle;
        }

        void setFigureStyle(ChessSettings.FigureStyle figureStyle) {
            this.figureStyle = figureStyle == null ? ChessSettings.FigureStyle.DEFAULT : figureStyle;
        }

        void setTimer(boolean enabled, long initialMillis, long moveBonusMillis, long checkBonusMillis) {
            this.timerEnabled = enabled;
            this.initialMillis = Math.max(0L, initialMillis);
            this.moveBonusMillis = Math.max(0L, moveBonusMillis);
            this.checkBonusMillis = Math.max(0L, checkBonusMillis);
        }

        void addInitialMillis(long delta) {
            initialMillis = Math.max(0L, initialMillis + delta);
            timerEnabled = initialMillis > 0L;
        }

        void addMoveBonusMillis(long delta) {
            moveBonusMillis = Math.max(0L, moveBonusMillis + delta);
        }

        void addCheckBonusMillis(long delta) {
            checkBonusMillis = Math.max(0L, checkBonusMillis + delta);
        }

        ChessManager.ChessTimerConfig timerConfig() {
            return timerEnabled && initialMillis > 0L
                    ? new ChessManager.ChessTimerConfig(true, initialMillis, checkBonusMillis, moveBonusMillis)
                    : ChessManager.ChessTimerConfig.off();
        }

        PlayerOptions copy() {
            PlayerOptions copy = new PlayerOptions();
            copy.startingSide = startingSide;
            copy.menuPreset = menuPreset;
            copy.timerPreset = timerPreset;
            copy.timerEnabled = timerEnabled;
            copy.initialMillis = initialMillis;
            copy.moveBonusMillis = moveBonusMillis;
            copy.checkBonusMillis = checkBonusMillis;
            copy.showMovementHints = showMovementHints;
            copy.allowUndo = allowUndo;
            copy.figureStyle = figureStyle;
            return copy;
        }
    }
}
