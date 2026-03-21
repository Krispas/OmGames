package krispasi.omGames.bedwars.command;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.game.GameSession;
import krispasi.omGames.bedwars.gui.TimeCapsuleViewMenu;
import krispasi.omGames.bedwars.karma.BedwarsKarmaService;
import krispasi.omGames.bedwars.model.Arena;
import krispasi.omGames.bedwars.model.TeamColor;
import krispasi.omGames.bedwars.setup.BedwarsSetupManager;
import krispasi.omGames.bedwars.shop.ShopCategory;
import krispasi.omGames.bedwars.shop.ShopCategoryType;
import krispasi.omGames.bedwars.shop.ShopConfig;
import krispasi.omGames.bedwars.shop.ShopItemBehavior;
import krispasi.omGames.bedwars.shop.ShopItemDefinition;
import krispasi.omGames.bedwars.stats.BedwarsPlayerStats;
import krispasi.omGames.bedwars.stats.BedwarsStatsService;
import krispasi.omGames.bedwars.timecapsule.TimeCapsuleQueueType;
import krispasi.omGames.bedwars.timecapsule.TimeCapsuleSerialization;
import krispasi.omGames.bedwars.timecapsule.TimeCapsuleService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Command executor and tab completer for {@code /bw}.
 * <p>Checks permissions and routes start, stop, reload, and setup actions to
 * {@link krispasi.omGames.bedwars.BedwarsManager} and
 * {@link krispasi.omGames.bedwars.setup.BedwarsSetupManager}.</p>
 * @see krispasi.omGames.bedwars.setup.BedwarsSetupManager
 */
public class BedwarsCommand implements CommandExecutor, TabCompleter {
    private static final String NORMAL_ROTATING_GIVE_ACCOUNT = "krispasi_2";
    private static final int TIME_CAPSULE_SIZE = 27;
    private static final DateTimeFormatter FULL_TIME_ID_FORMATTER =
            DateTimeFormatter.ofPattern("MM_dd_HH_mm_ss");
    private static final DateTimeFormatter SHORT_TIME_ID_FORMATTER =
            DateTimeFormatter.ofPattern("MM_dd_mm_ss");

    private final BedwarsManager bedwarsManager;
    private final BedwarsSetupManager setupManager;

    public BedwarsCommand(BedwarsManager bedwarsManager, BedwarsSetupManager setupManager) {
        this.bedwarsManager = bedwarsManager;
        this.setupManager = setupManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("stats")) {
            return handleStatsCommand(sender, player, args);
        }
        if (args.length > 0 && isQuickBuyCommand(args[0])) {
            GameSession session = bedwarsManager.getActiveSession();
            if (session != null && session.isActive() && session.isParticipant(player.getUniqueId())) {
                player.sendMessage(Component.text("You cannot edit Quick Buy while in a BedWars match.", NamedTextColor.RED));
                return true;
            }
            bedwarsManager.openQuickBuyEditor(player);
            return true;
        }
        boolean temporaryCreator = bedwarsManager.isTemporaryCreator(player.getUniqueId());
        if (args.length > 0 && isCreatorCommand(args[0])) {
            return handleCreatorCommand(sender, player, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("karma")) {
            return handleKarmaCommand(sender, player, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("give")) {
            return handleGiveCommand(sender, player, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("time_capsule")) {
            return handleTimeCapsuleCommand(sender, player, args, TimeCapsuleQueueType.NORMAL,
                    "/bw time_capsule view");
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("test_time_capsule")) {
            return handleTimeCapsuleCommand(sender, player, args, TimeCapsuleQueueType.TEST,
                    "/bw test_time_capsule view");
        }
        if (!player.isOp() && !temporaryCreator) {
            sender.sendMessage(Component.text("Only OP can use this command (except /bw stats and /bw quick-buy).", NamedTextColor.RED));
            return true;
        }
        if (!sender.hasPermission("omgames.bw.start")
                && !sender.hasPermission("omgames.bw.setup")
                && !sender.hasPermission("omgames.bw.reload")
                && !temporaryCreator) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("start")) {
            if (!sender.hasPermission("omgames.bw.start")) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            bedwarsManager.openMapSelect(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("stop")) {
            if (!sender.hasPermission("omgames.bw.start")) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            bedwarsManager.stopSession(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("test")) {
            if (!sender.hasPermission("omgames.bw.start")) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2 || !args[1].equalsIgnoreCase("start")) {
                sender.sendMessage(Component.text("Usage: /bw test start | /bw test_time_capsule view <user> [time_id]",
                        NamedTextColor.YELLOW));
                return true;
            }
            bedwarsManager.openMapSelect(player, false);
            return true;
        }
        if (args[0].equalsIgnoreCase("tp")) {
            if (!canUseMapSetupCommands(sender, player)) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(Component.text("Usage: /bw tp <arena>|lobby", NamedTextColor.YELLOW));
                return true;
            }
            if (args.length == 2 && args[1].equalsIgnoreCase("lobby")) {
                Location target = resolveBedwarsLobbyLocation();
                if (target == null) {
                    sender.sendMessage(Component.text("No BedWars world is loaded.", NamedTextColor.RED));
                    return true;
                }
                player.teleport(target);
                sender.sendMessage(Component.text("Teleported to BedWars lobby.", NamedTextColor.GREEN));
                return true;
            }
            String arenaName = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
            if (arenaName.isBlank()) {
                sender.sendMessage(Component.text("Usage: /bw tp <arena>|lobby", NamedTextColor.YELLOW));
                return true;
            }
            Arena arena = findArena(arenaName);
            if (arena == null) {
                sender.sendMessage(Component.text("Arena not found: " + arenaName, NamedTextColor.RED));
                return true;
            }
            World world = arena.getWorld();
            if (world == null) {
                sender.sendMessage(Component.text("World not loaded: " + arena.getWorldName(), NamedTextColor.RED));
                return true;
            }
            Location target = resolveArenaLobbyLocation(arena);
            if (target == null && arena.getCenter() != null) {
                target = arena.getCenter().toLocation(world);
            }
            if (target == null) {
                sender.sendMessage(Component.text("Arena teleport location not set: " + arena.getId(), NamedTextColor.RED));
                return true;
            }
            player.teleport(target);
            sender.sendMessage(Component.text("Teleported to " + arena.getId() + " in " + world.getName() + ".", NamedTextColor.GREEN));
            return true;
        }
        if (args[0].equalsIgnoreCase("lobby")) {
            if (!sender.hasPermission("omgames.bw.start")) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            return handleLobbyParkourCommand(sender, player, args);
        }
        if (args[0].equalsIgnoreCase("out")) {
            sender.sendMessage(Component.text("Use /bw game out [player].", NamedTextColor.YELLOW));
            return true;
        }
        if (args[0].equalsIgnoreCase("game")) {
            if (!sender.hasPermission("omgames.bw.start")) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            if (!player.isOp()) {
                sender.sendMessage(Component.text("Only OP can use /bw game commands.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(Component.text("Usage: /bw game out [player] | /bw game join <team|spectate> [player] | /bw game spectate [player] | /bw game revive <team> | /bw game skipphase", NamedTextColor.YELLOW));
                return true;
            }
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("out")) {
                return handleGameOut(sender, player, Arrays.copyOfRange(args, 1, args.length));
            }
            if (action.equals("spectate")) {
                return handleGameSpectate(sender, player, Arrays.copyOfRange(args, 1, args.length));
            }
            if (action.equals("join")) {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /bw game join <team|spectate> [player]", NamedTextColor.YELLOW));
                    return true;
                }
                if (args[2].equalsIgnoreCase("spectate")) {
                    return handleGameSpectate(sender, player, Arrays.copyOfRange(args, 2, args.length));
                }
                TeamColor team = TeamColor.fromKey(args[2]);
                if (team == null) {
                    sender.sendMessage(Component.text("Unknown team: " + args[2], NamedTextColor.RED));
                    return true;
                }
                Player target = args.length >= 4 ? Bukkit.getPlayer(args[3]) : player;
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                GameSession session = bedwarsManager.getActiveSession();
                if (session == null || !session.isActive()) {
                    sender.sendMessage(Component.text("No active BedWars session.", NamedTextColor.RED));
                    return true;
                }
                if (!session.forceJoin(target, team)) {
                    sender.sendMessage(Component.text("Could not add player to team " + team.displayName() + ".", NamedTextColor.RED));
                    return true;
                }
                sender.sendMessage(Component.text("Added " + target.getName() + " to " + team.displayName() + ".", NamedTextColor.GREEN));
                if (target != player) {
                    target.sendMessage(Component.text("You were added to " + team.displayName() + ".", NamedTextColor.GREEN));
                }
                return true;
            }
            if (action.equals("revive")) {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /bw game revive <team>", NamedTextColor.YELLOW));
                    return true;
                }
                TeamColor team = TeamColor.fromKey(args[2]);
                if (team == null) {
                    sender.sendMessage(Component.text("Unknown team: " + args[2], NamedTextColor.RED));
                    return true;
                }
                GameSession session = bedwarsManager.getActiveSession();
                if (session == null || !session.isActive()) {
                    sender.sendMessage(Component.text("No active BedWars session.", NamedTextColor.RED));
                    return true;
                }
                if (!session.reviveBed(team)) {
                    sender.sendMessage(Component.text("Could not revive " + team.displayName() + " bed.", NamedTextColor.RED));
                    return true;
                }
                sender.sendMessage(Component.text(team.displayName() + " bed revived.", NamedTextColor.GREEN));
                return true;
            }
            if (action.equals("skipphase")) {
                GameSession session = bedwarsManager.getActiveSession();
                if (session == null || !session.isActive()) {
                    sender.sendMessage(Component.text("No active BedWars session.", NamedTextColor.RED));
                    return true;
                }
                String phase = session.skipNextPhase();
                if (phase == null) {
                    sender.sendMessage(Component.text("No phase to skip.", NamedTextColor.YELLOW));
                    return true;
                }
                sender.sendMessage(Component.text("Skipped to " + phase + ".", NamedTextColor.GREEN));
                return true;
            }
            sender.sendMessage(Component.text("Usage: /bw game out [player] | /bw game join <team|spectate> [player] | /bw game spectate [player] | /bw game revive <team> | /bw game skipphase", NamedTextColor.YELLOW));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("omgames.bw.reload")) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            bedwarsManager.loadArenas();
            bedwarsManager.loadCustomItems();
            bedwarsManager.loadShopConfig();
            bedwarsManager.loadQuickBuy();
            sender.sendMessage(Component.text("BedWars configs reloaded.", NamedTextColor.GREEN));
            if (bedwarsManager.getActiveSession() != null && bedwarsManager.getActiveSession().isActive()) {
                sender.sendMessage(Component.text("Running match keeps its current arena state.", NamedTextColor.YELLOW));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("setup")) {
            if (!canUseMapSetupCommands(sender, player)) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            if (bedwarsManager.getActiveSession() != null && bedwarsManager.getActiveSession().isActive()) {
                sender.sendMessage(Component.text("Stop the current BedWars match before setup.", NamedTextColor.RED));
                return true;
            }
            if (args.length >= 3 && args[1].equalsIgnoreCase("new")) {
                setupManager.createArena(player, args[2]);
                return true;
            }
            if (args.length == 2) {
                if (args[1].equalsIgnoreCase("new")) {
                    sender.sendMessage(Component.text("Usage: /bw setup new <arena>", NamedTextColor.YELLOW));
                    return true;
                }
                setupManager.showSetupList(player, args[1]);
                return true;
            }
            if (args.length >= 3) {
                String arenaId = args[1];
                String target = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                setupManager.applySetup(player, arenaId, target);
                return true;
            }
        }

        sender.sendMessage(Component.text("Usage: /bw start | /bw test start | /bw test_time_capsule view <user> [time_id] | /bw stop | /bw tp <arena>|lobby | /bw lobby parkour <start|checkpoint [x]|end> | /bw game out [player] | /bw game join <team|spectate> [player] | /bw game spectate [player] | /bw game revive <team> | /bw karma <user> | /bw karma add <permanent|temporary> <user> | /bw karma cause | /bw give <rotating_item> | /bw time_capsule view <user> [time_id] | /bw quick_buy | /bw stats [user] | /bw stats modify <user> <stat|all> <+|-|set|+1|-1> [amount] | /bw reload | /bw setup new <arena> | /bw setup <arena> [key] | /bw creator add <user> | /bw creator remove <user>", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("start", "test", "stop", "tp", "lobby", "game", "karma", "give", "time_capsule",
                            "test_time_capsule", "quick_buy", "stats", "reload", "setup", "creator")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            String input = normalizeRotatingGiveId(args[1]);
            return getRotatingGiveCandidateIds(sender).stream()
                    .filter(option -> input == null || option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("time_capsule")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("view")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 3
                && args[0].equalsIgnoreCase("time_capsule")
                && args[1].equalsIgnoreCase("view")) {
            String input = args[2].toLowerCase(Locale.ROOT);
            return getKnownPlayerNames()
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 4
                && args[0].equalsIgnoreCase("time_capsule")
                && args[1].equalsIgnoreCase("view")) {
            OfflinePlayer target = resolveKnownPlayer(args[2]);
            if (target == null || target.getUniqueId() == null) {
                return List.of();
            }
            String input = args[3].toLowerCase(Locale.ROOT);
            return getCurrentCapsules(target.getUniqueId(), TimeCapsuleQueueType.NORMAL).stream()
                    .map(capsule -> formatFullTimeId(capsule.createdAt()))
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("test_time_capsule")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("view")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 3
                && args[0].equalsIgnoreCase("test_time_capsule")
                && args[1].equalsIgnoreCase("view")) {
            String input = args[2].toLowerCase(Locale.ROOT);
            return getKnownPlayerNames()
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 4
                && args[0].equalsIgnoreCase("test_time_capsule")
                && args[1].equalsIgnoreCase("view")) {
            OfflinePlayer target = resolveKnownPlayer(args[2]);
            if (target == null || target.getUniqueId() == null) {
                return List.of();
            }
            String input = args[3].toLowerCase(Locale.ROOT);
            return getCurrentCapsules(target.getUniqueId(), TimeCapsuleQueueType.TEST).stream()
                    .map(capsule -> formatFullTimeId(capsule.createdAt()))
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 2 && isCreatorCommand(args[0])) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("add", "remove")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 3 && isCreatorCommand(args[0])) {
            String input = args[2].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("stats")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return Stream.concat(
                            Stream.of("modify"),
                            Bukkit.getOnlinePlayers().stream().map(Player::getName))
                    .distinct()
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("stats") && args[1].equalsIgnoreCase("modify")) {
            String input = args[2].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("stats") && args[1].equalsIgnoreCase("modify")) {
            String input = args[3].toLowerCase(Locale.ROOT);
            return bedwarsManager.getStatsService().getModifiableStatKeys().stream()
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("stats") && args[1].equalsIgnoreCase("modify")) {
            String input = args[4].toLowerCase(Locale.ROOT);
            return Stream.of("+", "-", "set", "+1", "-1", "add", "remove")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 6 && args[0].equalsIgnoreCase("stats") && args[1].equalsIgnoreCase("modify")) {
            String input = args[5].toLowerCase(Locale.ROOT);
            return Stream.of("1", "5", "10", "50", "100")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("lobby")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("parkour")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 3
                && args[0].equalsIgnoreCase("lobby")
                && args[1].equalsIgnoreCase("parkour")) {
            String input = args[2].toLowerCase(Locale.ROOT);
            return Stream.of("start", "checkpoint", "end")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 4
                && args[0].equalsIgnoreCase("lobby")
                && args[1].equalsIgnoreCase("parkour")
                && args[2].equalsIgnoreCase("checkpoint")) {
            String input = args[3].toLowerCase(Locale.ROOT);
            return Stream.of("1", "2", "3", "4", "5", "6", "7", "8")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("start", "time")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 3
                && args[0].equalsIgnoreCase("test")
                && args[1].equalsIgnoreCase("time")) {
            String input = args[2].toLowerCase(Locale.ROOT);
            return Stream.of("capsule")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 4
                && args[0].equalsIgnoreCase("test")
                && args[1].equalsIgnoreCase("time")
                && args[2].equalsIgnoreCase("capsule")) {
            String input = args[3].toLowerCase(Locale.ROOT);
            return Stream.of("view")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 5
                && args[0].equalsIgnoreCase("test")
                && args[1].equalsIgnoreCase("time")
                && args[2].equalsIgnoreCase("capsule")
                && args[3].equalsIgnoreCase("view")) {
            String input = args[4].toLowerCase(Locale.ROOT);
            return getKnownPlayerNames()
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 6
                && args[0].equalsIgnoreCase("test")
                && args[1].equalsIgnoreCase("time")
                && args[2].equalsIgnoreCase("capsule")
                && args[3].equalsIgnoreCase("view")) {
            OfflinePlayer target = resolveKnownPlayer(args[4]);
            if (target == null || target.getUniqueId() == null) {
                return List.of();
            }
            String input = args[5].toLowerCase(Locale.ROOT);
            return getCurrentCapsules(target.getUniqueId(), TimeCapsuleQueueType.TEST).stream()
                    .map(capsule -> formatFullTimeId(capsule.createdAt()))
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("game")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("out", "join", "spectate", "revive", "skipphase")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("karma")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return Stream.concat(
                            Stream.of("add", "cause"),
                            getKnownPlayerNames())
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("karma") && args[1].equalsIgnoreCase("add")) {
            String input = args[2].toLowerCase(Locale.ROOT);
            return Stream.of("permanent", "temporary")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("karma") && args[1].equalsIgnoreCase("add")) {
            String input = args[3].toLowerCase(Locale.ROOT);
            return getKnownPlayerNames()
                    .distinct()
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("game") && args[1].equalsIgnoreCase("join")) {
            String input = args[2].toLowerCase(Locale.ROOT);
            return Stream.concat(
                            Stream.of("spectate"),
                            Stream.of(TeamColor.values()).map(TeamColor::key))
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("game") && args[1].equalsIgnoreCase("join")) {
            String input = args[3].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("game") && args[1].equalsIgnoreCase("spectate")) {
            String input = args[2].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("game") && args[1].equalsIgnoreCase("revive")) {
            String input = args[2].toLowerCase(Locale.ROOT);
            return Stream.of(TeamColor.values())
                    .map(TeamColor::key)
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("game") && args[1].equalsIgnoreCase("out")) {
            String input = args[2].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tp")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            List<String> options = new java.util.ArrayList<>();
            options.add("lobby");
            bedwarsManager.getArenas().forEach(arena -> options.add(arena.getId()));
            return options.stream()
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setup")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            List<String> options = new java.util.ArrayList<>();
            options.add("new");
            for (var arena : bedwarsManager.getArenas()) {
                options.add(arena.getId());
            }
            return options.stream()
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setup") && !args[1].equalsIgnoreCase("new")) {
            String input = args[2].toLowerCase(Locale.ROOT);
            return setupManager.getSetupKeys(args[1]).stream()
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        return List.of();
    }

    private boolean handleCreatorCommand(CommandSender sender, Player player, String[] args) {
        if (!player.isOp()) {
            sender.sendMessage(Component.text("Only OP can use /bw creator.", NamedTextColor.RED));
            return true;
        }
        if (!sender.hasPermission("omgames.bw.setup") && !sender.hasPermission("omgames.bw.start")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /bw creator add <user> | /bw creator remove <user>", NamedTextColor.YELLOW));
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        OfflinePlayer target = resolveKnownPlayer(args[2]);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(Component.text("Player not found: " + args[2], NamedTextColor.RED));
            return true;
        }
        String targetName = resolvePlayerName(target, args[2]);
        if (action.equals("add")) {
            if (!bedwarsManager.addTemporaryCreator(target.getUniqueId())) {
                sender.sendMessage(Component.text(targetName + " already has temporary creator access.", NamedTextColor.YELLOW));
                return true;
            }
            sender.sendMessage(Component.text("Granted temporary BedWars creator access to " + targetName + " until restart.", NamedTextColor.GREEN));
            if (target.isOnline() && target.getPlayer() != null) {
                target.getPlayer().sendMessage(Component.text("You can now use /bw setup and /bw tp until restart.", NamedTextColor.GREEN));
            }
            return true;
        }
        if (action.equals("remove")) {
            if (!bedwarsManager.removeTemporaryCreator(target.getUniqueId())) {
                sender.sendMessage(Component.text(targetName + " does not have temporary creator access.", NamedTextColor.YELLOW));
                return true;
            }
            sender.sendMessage(Component.text("Removed temporary BedWars creator access from " + targetName + ".", NamedTextColor.GREEN));
            if (target.isOnline() && target.getPlayer() != null) {
                target.getPlayer().sendMessage(Component.text("Your temporary BedWars creator access was removed.", NamedTextColor.YELLOW));
            }
            return true;
        }
        sender.sendMessage(Component.text("Usage: /bw creator add <user> | /bw creator remove <user>", NamedTextColor.YELLOW));
        return true;
    }

    private boolean canUseMapSetupCommands(CommandSender sender, Player player) {
        return bedwarsManager.isTemporaryCreator(player.getUniqueId())
                || sender.hasPermission("omgames.bw.setup")
                || sender.hasPermission("omgames.bw.start");
    }

    private boolean isCreatorCommand(String command) {
        return command != null
                && (command.equalsIgnoreCase("creater") || command.equalsIgnoreCase("creator"));
    }

    private OfflinePlayer resolveKnownPlayer(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Arrays.stream(Bukkit.getOfflinePlayers())
                .filter(player -> player.getName() != null && player.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private String resolvePlayerName(OfflinePlayer player, String fallback) {
        if (player != null && player.getName() != null && !player.getName().isBlank()) {
            return player.getName();
        }
        return fallback;
    }

    private Stream<String> getKnownPlayerNames() {
        return Stream.concat(
                        Bukkit.getOnlinePlayers().stream().map(Player::getName),
                        Arrays.stream(Bukkit.getOfflinePlayers())
                                .map(OfflinePlayer::getName)
                                .filter(name -> name != null && !name.isBlank()))
                .distinct();
    }

    private Arena findArena(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        for (Arena arena : bedwarsManager.getArenas()) {
            if (arena.getId().equalsIgnoreCase(trimmed)) {
                return arena;
            }
        }
        return bedwarsManager.getArena(trimmed);
    }

    private boolean handleStatsCommand(CommandSender sender, Player player, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("modify")) {
            return handleStatsModifyCommand(sender, player, args);
        }
        if (args.length > 2) {
            sender.sendMessage(Component.text("Usage: /bw stats [user] | /bw stats modify <user> <stat|all> <+|-|set|+1|-1> [amount]",
                    NamedTextColor.YELLOW));
            return true;
        }
        OfflinePlayer target = player;
        String targetName = player.getName();
        if (args.length == 2) {
            target = resolveKnownPlayer(args[1]);
            if (target == null || target.getUniqueId() == null) {
                sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                return true;
            }
            targetName = resolvePlayerName(target, args[1]);
        }
        BedwarsPlayerStats stats = bedwarsManager.getStatsService().getStats(target.getUniqueId());
        sendStatsView(player, targetName, stats);
        return true;
    }

    private boolean handleGiveCommand(CommandSender sender, Player player, String[] args) {
        GameSession session = bedwarsManager.getActiveSession();
        if (session == null || !session.isActive()) {
            sender.sendMessage(Component.text("No active BedWars session.", NamedTextColor.RED));
            return true;
        }
        if (session.isLobby()) {
            sender.sendMessage(Component.text("You can only use /bw give after the match countdown has started.",
                    NamedTextColor.RED));
            return true;
        }
        if (!canUseRotatingGive(player, session)) {
            if (session.isTestMode()) {
                sender.sendMessage(Component.text("Only OP can use /bw give in test BedWars matches.",
                        NamedTextColor.RED));
            } else {
                sender.sendMessage(Component.text("Only " + NORMAL_ROTATING_GIVE_ACCOUNT
                        + " can use /bw give in normal BedWars matches.", NamedTextColor.RED));
            }
            return true;
        }
        if (!session.isParticipant(player.getUniqueId()) || !session.isInArenaWorld(player.getWorld())) {
            sender.sendMessage(Component.text("You must be in the active BedWars session to use /bw give.",
                    NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /bw give <rotating_item>", NamedTextColor.YELLOW));
            return true;
        }
        String requestedId = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        ShopItemDefinition definition = resolveRotatingGiveDefinition(requestedId);
        if (definition == null) {
            sender.sendMessage(Component.text("Unknown rotating item: " + requestedId + ".", NamedTextColor.RED));
            return true;
        }
        if (session.isSuddenDeathActive() && definition.isDisabledAfterSuddenDeath()) {
            sender.sendMessage(Component.text("That rotating item is disabled after sudden death.", NamedTextColor.RED));
            return true;
        }
        if (!session.giveAdminRotatingItem(player, definition)) {
            sender.sendMessage(Component.text("Could not give that rotating item right now.", NamedTextColor.RED));
            return true;
        }
        sender.sendMessage(Component.text("Given " + describeRotatingGiveItem(definition) + ".", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleTimeCapsuleCommand(CommandSender sender,
                                             Player player,
                                             String[] args,
                                             TimeCapsuleQueueType queueType,
                                             String commandPath) {
        int viewIndex = 1;
        int userIndex = 2;
        int timeIdIndex = 3;
        if (!player.isOp()) {
            sender.sendMessage(Component.text("Only OP can use " + commandPath + ".", NamedTextColor.RED));
            return true;
        }
        if (args.length < userIndex + 1 || !args[viewIndex].equalsIgnoreCase("view")) {
            sender.sendMessage(Component.text("Usage: " + commandPath + " <user> [time_id]", NamedTextColor.YELLOW));
            return true;
        }
        if (args.length < userIndex + 1) {
            sender.sendMessage(Component.text("Usage: " + commandPath + " <user> [time_id]", NamedTextColor.YELLOW));
            return true;
        }
        OfflinePlayer target = resolveKnownPlayer(args[userIndex]);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(Component.text("Player not found: " + args[userIndex], NamedTextColor.RED));
            return true;
        }
        String targetName = resolvePlayerName(target, args[userIndex]);
        List<TimeCapsuleService.VisibleTimeCapsule> capsules = getCurrentCapsules(target.getUniqueId(), queueType);
        if (capsules.isEmpty()) {
            sender.sendMessage(Component.text("No current " + queueLabel(queueType) + " Time Capsules found for "
                    + targetName + ".", NamedTextColor.RED));
            return true;
        }
        if (args.length == userIndex + 1) {
            sendAvailableCapsules(sender, targetName, capsules, queueType);
            return true;
        }
        String requestedTimeId = args[timeIdIndex];
        List<TimeCapsuleService.VisibleTimeCapsule> matches = capsules.stream()
                .filter(capsule -> matchesTimeId(capsule, requestedTimeId))
                .toList();
        if (matches.isEmpty()) {
            sender.sendMessage(Component.text("No current " + queueLabel(queueType) + " Time Capsule found for " + targetName
                    + " with time id " + requestedTimeId + ".", NamedTextColor.RED));
            sendAvailableCapsules(sender, targetName, capsules, queueType);
            return true;
        }
        if (matches.size() > 1) {
            sender.sendMessage(Component.text("Multiple current " + queueLabel(queueType)
                    + " Time Capsules match " + requestedTimeId + ".", NamedTextColor.RED));
            sendAvailableCapsules(sender, targetName, matches, queueType);
            return true;
        }
        TimeCapsuleService.VisibleTimeCapsule capsule = matches.getFirst();
        ItemStack[] contents;
        try {
            contents = TimeCapsuleSerialization.deserialize(capsule.contentsBase64(), TIME_CAPSULE_SIZE);
        } catch (IOException | IllegalArgumentException ex) {
            bedwarsManager.getPlugin().getLogger().warning("Failed to view stored Time Capsule for "
                    + target.getUniqueId() + ": " + ex.getMessage());
            sender.sendMessage(Component.text("That Time Capsule could not be opened.", NamedTextColor.RED));
            return true;
        }
        new TimeCapsuleViewMenu(contents).open(player);
        sender.sendMessage(Component.text("Viewing " + targetName + "'s " + queueLabel(queueType)
                + " Time Capsule " + formatFullTimeId(capsule.createdAt()) + ".", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleStatsModifyCommand(CommandSender sender, Player player, String[] args) {
        if (!player.isOp()) {
            sender.sendMessage(Component.text("Only OP can use /bw stats modify.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 5) {
            sender.sendMessage(Component.text("Usage: /bw stats modify <user> <stat|all> <+|-|set|+1|-1> [amount]",
                    NamedTextColor.YELLOW));
            return true;
        }
        String targetName = args[2];
        OfflinePlayer target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            target = Bukkit.getOfflinePlayer(targetName);
        }
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return true;
        }
        ParsedStatOperation parsed = parseStatOperation(args[4], args.length >= 6 ? args[5] : null);
        if (parsed == null) {
            sender.sendMessage(Component.text("Usage: /bw stats modify <user> <stat|all> <+|-|set|+1|-1> [amount]",
                    NamedTextColor.YELLOW));
            return true;
        }
        List<BedwarsStatsService.StatChange> changes = bedwarsManager.getStatsService()
                .modifyStats(target.getUniqueId(), args[3], parsed.operation(), parsed.amount());
        if (changes.isEmpty()) {
            String supported = String.join(", ", bedwarsManager.getStatsService().getModifiableStatKeys());
            sender.sendMessage(Component.text("Unknown stat. Available: " + supported, NamedTextColor.RED));
            return true;
        }
        String summary = changes.stream()
                .map(change -> change.statKey() + ": " + change.before() + " -> " + change.after())
                .collect(Collectors.joining(", "));
        String targetDisplayName = target.getName() != null ? target.getName() : targetName;
        sender.sendMessage(Component.text("Updated stats for " + targetDisplayName + ": " + summary, NamedTextColor.GREEN));
        if (target.isOnline() && target.getPlayer() != null && target.getPlayer() != player) {
            target.getPlayer().sendMessage(Component.text("Your BedWars stats were updated by " + player.getName() + ".",
                    NamedTextColor.YELLOW));
        }
        return true;
    }

    private boolean handleKarmaCommand(CommandSender sender, Player player, String[] args) {
        if (!player.isOp()) {
            sender.sendMessage(Component.text("Only OP can use /bw karma.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /bw karma <user> | /bw karma add <permanent|temporary> <user> | /bw karma cause",
                    NamedTextColor.YELLOW));
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (!action.equals("add") && !action.equals("cause")) {
            OfflinePlayer target = resolveKnownPlayer(args[1]);
            if (target == null || target.getUniqueId() == null) {
                sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                return true;
            }
            String targetName = resolvePlayerName(target, args[1]);
            int permanent = bedwarsManager.getKarmaService().getPermanentKarma(target.getUniqueId());
            GameSession session = bedwarsManager.getActiveSession();
            if (session != null && session.isActive() && session.isParticipant(target.getUniqueId())) {
                int temporary = session.getTemporaryKarma(target.getUniqueId());
                int total = session.getTotalKarma(target.getUniqueId());
                sender.sendMessage(Component.text("Karma for " + targetName + ": permanent "
                        + permanent + ", temporary " + temporary + ", total " + total + ".",
                        NamedTextColor.GREEN));
                return true;
            }
            sender.sendMessage(Component.text("Permanent karma for " + targetName + ": " + permanent + ".",
                    NamedTextColor.GREEN));
            return true;
        }
        if (action.equals("cause")) {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isRunning()) {
                sender.sendMessage(Component.text("Karma cause only works during a running BedWars match.",
                        NamedTextColor.RED));
                return true;
            }
            int triggered = session.triggerKarmaCause();
            sender.sendMessage(Component.text("Triggered karma events for " + triggered + " player(s).",
                    triggered > 0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            return true;
        }
        if (!action.equals("add")) {
            sender.sendMessage(Component.text("Usage: /bw karma <user> | /bw karma add <permanent|temporary> <user> | /bw karma cause",
                    NamedTextColor.YELLOW));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(Component.text("Usage: /bw karma add <permanent|temporary> <user>",
                    NamedTextColor.YELLOW));
            return true;
        }
        String mode = args[2].toLowerCase(Locale.ROOT);
        if (mode.equals("permanent")) {
            OfflinePlayer target = resolveKnownPlayer(args[3]);
            if (target == null || target.getUniqueId() == null) {
                sender.sendMessage(Component.text("Player not found: " + args[3], NamedTextColor.RED));
                return true;
            }
            BedwarsKarmaService.KarmaChange change = bedwarsManager.getKarmaService()
                    .addPermanentKarma(target.getUniqueId(), 1);
            String targetName = resolvePlayerName(target, args[3]);
            sender.sendMessage(Component.text("Permanent karma for " + targetName + ": "
                    + change.before() + " -> " + change.after(), NamedTextColor.GREEN));
            if (target.isOnline() && target.getPlayer() != null && target.getPlayer() != player) {
                target.getPlayer().sendMessage(Component.text("Your permanent karma was increased to "
                        + change.after() + ".", NamedTextColor.YELLOW));
            }
            return true;
        }
        if (mode.equals("temporary")) {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isActive()) {
                sender.sendMessage(Component.text("No active BedWars session for temporary karma.",
                        NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayer(args[3]);
            if (target == null) {
                sender.sendMessage(Component.text("Player must be online for temporary karma.",
                        NamedTextColor.RED));
                return true;
            }
            if (!session.isParticipant(target.getUniqueId())) {
                sender.sendMessage(Component.text(target.getName() + " is not in the active BedWars session.",
                        NamedTextColor.RED));
                return true;
            }
            int after = session.addTemporaryKarma(target.getUniqueId(), 1);
            sender.sendMessage(Component.text("Temporary karma for " + target.getName() + " is now " + after + ".",
                    NamedTextColor.GREEN));
            if (target != player) {
                target.sendMessage(Component.text("Your temporary karma increased to " + after + ".",
                        NamedTextColor.YELLOW));
            }
            return true;
        }
        sender.sendMessage(Component.text("Usage: /bw karma <user> | /bw karma add <permanent|temporary> <user> | /bw karma cause",
                NamedTextColor.YELLOW));
        return true;
    }

    private boolean canUseRotatingGive(Player player, GameSession session) {
        if (player == null || session == null || !session.isActive()) {
            return false;
        }
        if (session.isTestMode()) {
            return player.isOp();
        }
        String name = player.getName();
        return name != null && name.equalsIgnoreCase(NORMAL_ROTATING_GIVE_ACCOUNT);
    }

    private List<String> getRotatingGiveCandidateIds(CommandSender sender) {
        GameSession session = bedwarsManager.getActiveSession();
        if (sender instanceof Player player && session != null && session.isActive() && !canUseRotatingGive(player, session)) {
            return List.of();
        }
        if (session != null && session.isActive()) {
            return session.getRotatingItemCandidateIds();
        }
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return List.of();
        }
        ShopCategory category = config.getCategory(ShopCategoryType.ROTATING);
        if (category == null || category.getEntries().isEmpty()) {
            return List.of();
        }
        return category.getEntries().values().stream()
                .map(this::normalizeRotatingGiveId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .filter(id -> {
                    ShopItemDefinition definition = config.getItem(id);
                    return definition != null && definition.getBehavior() != ShopItemBehavior.UPGRADE;
                })
                .toList();
    }

    private ShopItemDefinition resolveRotatingGiveDefinition(String rawItemId) {
        String normalized = normalizeRotatingGiveId(rawItemId);
        if (normalized == null) {
            return null;
        }
        if (!getRotatingGiveCandidateIds(null).contains(normalized)) {
            return null;
        }
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return null;
        }
        ShopItemDefinition definition = config.getItem(normalized);
        if (definition == null || definition.getBehavior() == ShopItemBehavior.UPGRADE) {
            return null;
        }
        return definition;
    }

    private String normalizeRotatingGiveId(String rawItemId) {
        if (rawItemId == null) {
            return null;
        }
        String trimmed = rawItemId.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isBlank()) {
            return null;
        }
        return trimmed.replaceAll("[\\s-]+", "_");
    }

    private String describeRotatingGiveItem(ShopItemDefinition definition) {
        if (definition == null) {
            return "rotating item";
        }
        String displayName = definition.getDisplayName();
        return displayName != null && !displayName.isBlank() ? displayName : definition.getId();
    }

    private void sendAvailableCapsules(CommandSender sender,
                                       String targetName,
                                       List<TimeCapsuleService.VisibleTimeCapsule> capsules,
                                       TimeCapsuleQueueType queueType) {
        sender.sendMessage(Component.text("Current " + queueLabel(queueType) + " Time Capsules for " + targetName
                + " (id format: MM_dd_HH_mm_ss):", NamedTextColor.YELLOW));
        for (TimeCapsuleService.VisibleTimeCapsule capsule : capsules) {
            sender.sendMessage(Component.text("- " + formatFullTimeId(capsule.createdAt()), NamedTextColor.GRAY));
        }
    }

    private List<TimeCapsuleService.VisibleTimeCapsule> getCurrentCapsules(java.util.UUID creatorId,
                                                                           TimeCapsuleQueueType queueType) {
        return bedwarsManager.getTimeCapsuleService().getCurrentCapsulesByCreator(creatorId).stream()
                .filter(capsule -> capsule.queueType() == queueType)
                .toList();
    }

    private String queueLabel(TimeCapsuleQueueType queueType) {
        return queueType == TimeCapsuleQueueType.TEST ? "test" : "normal";
    }

    private boolean matchesTimeId(TimeCapsuleService.VisibleTimeCapsule capsule, String requestedTimeId) {
        if (capsule == null) {
            return false;
        }
        if (requestedTimeId == null || requestedTimeId.isBlank()) {
            return false;
        }
        String normalized = requestedTimeId.trim();
        if (formatQualifiedTimeId(capsule).equalsIgnoreCase(normalized)
                || formatQualifiedShortTimeId(capsule).equalsIgnoreCase(normalized)) {
            return true;
        }
        return formatFullTimeId(capsule.createdAt()).equalsIgnoreCase(normalized)
                || formatShortTimeId(capsule.createdAt()).equalsIgnoreCase(normalized);
    }

    private String formatFullTimeId(long createdAt) {
        return FULL_TIME_ID_FORMATTER.format(Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()));
    }

    private String formatShortTimeId(long createdAt) {
        return SHORT_TIME_ID_FORMATTER.format(Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()));
    }

    private String formatQualifiedTimeId(TimeCapsuleService.VisibleTimeCapsule capsule) {
        return capsule.queueType().key() + ":" + formatFullTimeId(capsule.createdAt());
    }

    private String formatQualifiedShortTimeId(TimeCapsuleService.VisibleTimeCapsule capsule) {
        return capsule.queueType().key() + ":" + formatShortTimeId(capsule.createdAt());
    }

    private ParsedStatOperation parseStatOperation(String operationToken, String amountToken) {
        if (operationToken == null || operationToken.isBlank()) {
            return null;
        }
        String token = operationToken.trim().toLowerCase(Locale.ROOT);
        if (token.equals("set") || token.equals("=")) {
            Long amount = parseLong(amountToken);
            if (amount == null) {
                return null;
            }
            return new ParsedStatOperation(BedwarsStatsService.StatOperation.SET, amount);
        }
        if (token.equals("+") || token.equals("add") || token.equals("plus")) {
            Long amount = amountToken == null ? 1L : parseLong(amountToken);
            if (amount == null) {
                return null;
            }
            return new ParsedStatOperation(BedwarsStatsService.StatOperation.ADD, safeAbs(amount));
        }
        if (token.equals("-") || token.equals("remove") || token.equals("sub") || token.equals("subtract")) {
            Long amount = amountToken == null ? 1L : parseLong(amountToken);
            if (amount == null) {
                return null;
            }
            return new ParsedStatOperation(BedwarsStatsService.StatOperation.SUBTRACT, safeAbs(amount));
        }
        if ((token.startsWith("+") || token.startsWith("-")) && token.length() > 1) {
            BedwarsStatsService.StatOperation op = token.startsWith("-")
                    ? BedwarsStatsService.StatOperation.SUBTRACT
                    : BedwarsStatsService.StatOperation.ADD;
            Long amount = parseLong(token.substring(1));
            if (amount == null) {
                return null;
            }
            if (amountToken != null) {
                Long override = parseLong(amountToken);
                if (override == null) {
                    return null;
                }
                amount = override;
            }
            return new ParsedStatOperation(op, safeAbs(amount));
        }
        return null;
    }

    private Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void sendStatsView(Player viewer, String targetName, BedwarsPlayerStats stats) {
        String bestTime = stats.getParkourBestTimeMillis() > 0L ? formatElapsed(stats.getParkourBestTimeMillis()) : "N/A";
        viewer.sendMessage(Component.text("BedWars Stats: " + targetName, NamedTextColor.GOLD));
        viewer.sendMessage(Component.text("Wins: " + stats.getWins(), NamedTextColor.YELLOW));
        viewer.sendMessage(Component.text("Kills: " + stats.getKills(), NamedTextColor.YELLOW));
        viewer.sendMessage(Component.text("Deaths: " + stats.getDeaths(), NamedTextColor.YELLOW));
        viewer.sendMessage(Component.text("KDR: " + formatRatio(stats.getKillDeathRatio()), NamedTextColor.YELLOW));
        viewer.sendMessage(Component.text("Final Kills: " + stats.getFinalKills(), NamedTextColor.YELLOW));
        viewer.sendMessage(Component.text("Final Deaths: " + stats.getFinalDeaths(), NamedTextColor.YELLOW));
        viewer.sendMessage(Component.text("FKDR: " + formatRatio(stats.getFinalKillDeathRatio()), NamedTextColor.YELLOW));
        viewer.sendMessage(Component.text("Games Played: " + stats.getGamesPlayed(), NamedTextColor.YELLOW));
        viewer.sendMessage(Component.text("Beds Broken: " + stats.getBedsBroken(), NamedTextColor.YELLOW));
        viewer.sendMessage(Component.text("Parkour Best Time: " + bestTime, NamedTextColor.YELLOW));
        viewer.sendMessage(Component.text("Parkour Best Checkpoint Uses: " + stats.getParkourBestCheckpointUses(), NamedTextColor.YELLOW));
    }

    private long safeAbs(long value) {
        if (value == Long.MIN_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.abs(value);
    }

    private String formatElapsed(long elapsedMillis) {
        long millis = Math.max(0L, elapsedMillis);
        long minutes = millis / 60_000L;
        long seconds = (millis % 60_000L) / 1_000L;
        long ms = millis % 1_000L;
        if (minutes > 0L) {
            return String.format(Locale.ROOT, "%d:%02d.%03d", minutes, seconds, ms);
        }
        return String.format(Locale.ROOT, "%d.%03ds", seconds, ms);
    }

    private String formatRatio(double ratio) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0.0, ratio));
    }

    private record ParsedStatOperation(BedwarsStatsService.StatOperation operation, long amount) {
    }

    private boolean handleGameOut(CommandSender sender, Player caller, String[] args) {
        Player target = args.length >= 2 ? Bukkit.getPlayer(args[1]) : caller;
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }
        GameSession session = bedwarsManager.getActiveSession();
        if (session == null || !session.isActive()) {
            sender.sendMessage(Component.text("No active BedWars session.", NamedTextColor.RED));
            return true;
        }
        session.removeLockedCommandSpectator(target.getUniqueId());
        session.removeParticipant(target);
        session.addEditor(target);
        target.setGameMode(GameMode.CREATIVE);
        target.setAllowFlight(true);
        target.setFlying(true);
        Location editorLobby = resolveArenaLobbyLocation(session.getArena());
        if (editorLobby != null) {
            target.teleport(editorLobby);
        }
        sender.sendMessage(Component.text("Removed " + target.getName() + " from the match.", NamedTextColor.YELLOW));
        if (target != caller) {
            target.sendMessage(Component.text("You were removed from the match.", NamedTextColor.YELLOW));
        }
        return true;
    }

    private boolean handleGameSpectate(CommandSender sender, Player caller, String[] args) {
        Player target = args.length >= 2 ? Bukkit.getPlayer(args[1]) : caller;
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }
        GameSession session = bedwarsManager.getActiveSession();
        if (session == null || !session.isActive()) {
            sender.sendMessage(Component.text("No active BedWars session.", NamedTextColor.RED));
            return true;
        }
        if (!session.isInArenaWorld(caller.getWorld())) {
            sender.sendMessage(Component.text("You must be in the active BedWars world to use /bw game spectate.", NamedTextColor.RED));
            return true;
        }
        session.removeEditor(target);
        session.removeParticipant(target);
        session.addLockedCommandSpectator(target.getUniqueId());
        target.getInventory().clear();
        target.setGameMode(GameMode.SPECTATOR);
        Location spectate = resolveArenaLobbyLocation(session.getArena());
        if (spectate != null) {
            target.teleport(spectate);
        }
        session.refreshSidebar(target);
        sender.sendMessage(Component.text("Set " + target.getName() + " to spectator.", NamedTextColor.YELLOW));
        if (target != caller) {
            target.sendMessage(Component.text("You were set to spectator.", NamedTextColor.YELLOW));
        }
        return true;
    }

    private boolean isQuickBuyCommand(String arg) {
        if (arg == null) {
            return false;
        }
        return arg.equalsIgnoreCase("quick_buy")
                || arg.equalsIgnoreCase("quick-buy")
                || arg.equalsIgnoreCase("quck-buy")
                || arg.equalsIgnoreCase("quck_buy");
    }

    private Location resolveBedwarsLobbyLocation() {
        GameSession session = bedwarsManager.getActiveSession();
        if (session != null && session.getArena() != null) {
            World activeWorld = session.getArena().getWorld();
            if (activeWorld != null) {
                return new Location(activeWorld, 0.0, 73.0, 0.0);
            }
        }
        World bedwarsWorld = Bukkit.getWorld("bedwars");
        if (bedwarsWorld != null) {
            return new Location(bedwarsWorld, 0.0, 73.0, 0.0);
        }
        for (Arena arena : bedwarsManager.getArenas()) {
            World world = arena.getWorld();
            if (world != null) {
                return new Location(world, 0.0, 73.0, 0.0);
            }
        }
        return null;
    }

    private Location resolveArenaLobbyLocation(Arena arena) {
        if (arena == null) {
            return null;
        }
        Location mapLobby = arena.getMapLobbyLocation();
        if (mapLobby != null) {
            return mapLobby;
        }
        return arena.getLobbyLocation();
    }

    private boolean handleLobbyParkourCommand(CommandSender sender, Player player, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("parkour")) {
            sender.sendMessage(Component.text("Usage: /bw lobby parkour <start|checkpoint [x]|end>", NamedTextColor.YELLOW));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /bw lobby parkour <start|checkpoint [x]|end>", NamedTextColor.YELLOW));
            return true;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        String message;
        if (action.equals("start")) {
            message = bedwarsManager.getLobbyParkour().setStartPlate(player);
        } else if (action.equals("end")) {
            message = bedwarsManager.getLobbyParkour().setEndPlate(player);
        } else if (action.equals("checkpoint")) {
            Integer checkpoint = null;
            if (args.length >= 4) {
                try {
                    checkpoint = Integer.parseInt(args[3]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(Component.text("Checkpoint index must be a number.", NamedTextColor.RED));
                    return true;
                }
                if (checkpoint <= 0) {
                    sender.sendMessage(Component.text("Checkpoint index must be greater than 0.", NamedTextColor.RED));
                    return true;
                }
            }
            message = bedwarsManager.getLobbyParkour().setCheckpointPlate(player, checkpoint);
        } else {
            sender.sendMessage(Component.text("Usage: /bw lobby parkour <start|checkpoint [x]|end>", NamedTextColor.YELLOW));
            return true;
        }
        NamedTextColor color = message.toLowerCase(Locale.ROOT).startsWith("parkour")
                ? NamedTextColor.GREEN
                : NamedTextColor.RED;
        sender.sendMessage(Component.text(message, color));
        return true;
    }
}
