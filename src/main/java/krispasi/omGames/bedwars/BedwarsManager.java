package krispasi.omGames.bedwars;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import krispasi.omGames.bedwars.skin.BedwarsSkinSelection;
import krispasi.omGames.bedwars.config.BedwarsConfigLoader;
import krispasi.omGames.bedwars.event.BedwarsMatchEventConfig;
import krispasi.omGames.bedwars.event.BedwarsMatchEventType;
import krispasi.omGames.bedwars.event.BedwarsMoonBigConfig;
import krispasi.omGames.bedwars.game.GameSession;
import krispasi.omGames.bedwars.karma.BedwarsKarmaEventConfig;
import krispasi.omGames.bedwars.item.CustomItemConfig;
import krispasi.omGames.bedwars.item.CustomItemConfigLoader;
import krispasi.omGames.bedwars.karma.BedwarsKarmaService;
import krispasi.omGames.bedwars.lobby.BedwarsLobbyParkour;
import krispasi.omGames.bedwars.lobby.BedwarsParkourLeaderboard;
import krispasi.omGames.bedwars.shop.QuickBuyService;
import krispasi.omGames.bedwars.stats.BedwarsLobbyLeaderboard;
import krispasi.omGames.bedwars.stats.BedwarsStatsService;
import krispasi.omGames.bedwars.timecapsule.TimeCapsuleService;
import krispasi.omGames.bedwars.shop.ShopConfig;
import krispasi.omGames.bedwars.shop.ShopConfigLoader;
import krispasi.omGames.shared.SKIN_TYPE;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.FireworkEffect;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;
import krispasi.omGames.bedwars.gui.MapSelectMenu;
import krispasi.omGames.bedwars.gui.ShopMenu;
import krispasi.omGames.bedwars.gui.TeamAssignMenu;
import krispasi.omGames.bedwars.model.Arena;
import krispasi.omGames.bedwars.model.BlockPoint;
import krispasi.omGames.bedwars.model.TeamColor;
import krispasi.omGames.bedwars.shop.ShopCategoryType;

/**
 * Service layer and coordinator for BedWars runtime.
 * <p>Loads arenas, shop configuration, and custom items, and manages quick-buy
 * data via {@link krispasi.omGames.bedwars.shop.QuickBuyService}.</p>
 * <p>Owns the active {@link krispasi.omGames.bedwars.game.GameSession} and handles
 * start, stop, and end-of-game cleanup.</p>
 * @see krispasi.omGames.bedwars.game.GameSession
 */
public class BedwarsManager {
    private static final String DEFAULT_LOBBY_WORLD_NAME = "bedwars_lobby";
    private static final String SHARED_SKINS_FOLDER = "Skins";
    private static final String BEDWARS_SKINS_FILE = "bedwars.yml";
    private static final String LEGACY_BEDWARS_SKINS_FILE = "skins.yml";
    private static final BlockPoint DEFAULT_LOBBY_SPAWN = new BlockPoint(0, 73, 0);
    private static final double DEFAULT_LEADERBOARD_X = 4.0;
    private static final double DEFAULT_LEADERBOARD_Y = 73.0;
    private static final double DEFAULT_LEADERBOARD_Z = -1.0;
    private static final long LOBBY_CHIME_MIN_DELAY_TICKS = 30L * 20L;
    private static final long LOBBY_CHIME_MAX_DELAY_TICKS = 60L * 20L;
    private static final double LOBBY_CHIME_X = 0.0;
    private static final double LOBBY_CHIME_Y = 90.0;
    private static final double LOBBY_CHIME_Z = 0.0;
    private static final float LOBBY_CHIME_VOLUME = 5.0f;
    private static final float LOBBY_CHIME_PITCH = 1.8f;
    private final JavaPlugin plugin;
    private final QuickBuyService quickBuyService;
    private final BedwarsStatsService statsService;
    private final TimeCapsuleService timeCapsuleService;
    private final BedwarsKarmaService karmaService;
    private final BedwarsLobbyLeaderboard lobbyLeaderboard;
    private final BedwarsLobbyParkour lobbyParkour;
    private final BedwarsParkourLeaderboard parkourLeaderboard;
    private final Map<UUID, EnumMap<SKIN_TYPE, BedwarsSkinSelection>> skinSelections = new HashMap<>();
    private final Set<UUID> temporaryCreators = new HashSet<>();
    private Map<String, Arena> arenas = Map.of();
    private GameSession activeSession;
    private String sharedLobbyWorldName = DEFAULT_LOBBY_WORLD_NAME;
    private BlockPoint sharedLobbySpawn = DEFAULT_LOBBY_SPAWN;
    private String lobbyAmbientWorldName;
    private BukkitTask lobbyAmbientChimeTask;
    private ShopConfig shopConfig = ShopConfig.empty();
    private CustomItemConfig customItemConfig = CustomItemConfig.empty();
    private BedwarsMatchEventConfig matchEventConfig = BedwarsMatchEventConfig.defaults();
    private BedwarsKarmaEventConfig karmaEventConfig = BedwarsKarmaEventConfig.defaults();
    private BedwarsMoonBigConfig moonBigConfig = BedwarsMoonBigConfig.defaults();

    public BedwarsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.quickBuyService = new QuickBuyService(plugin);
        this.statsService = new BedwarsStatsService(plugin);
        this.timeCapsuleService = new TimeCapsuleService(plugin);
        this.karmaService = new BedwarsKarmaService(plugin);
        this.lobbyLeaderboard = new BedwarsLobbyLeaderboard(plugin, statsService);
        this.lobbyParkour = new BedwarsLobbyParkour(this);
        this.parkourLeaderboard = new BedwarsParkourLeaderboard(plugin, lobbyParkour);
    }

    public File getBedwarsDataFolder() {
        File folder = new File(plugin.getDataFolder(), "Bedwars");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    public File getBedwarsConfigFile(String name) {
        return new File(getBedwarsDataFolder(), name);
    }

    public void loadArenas() {
        File configFile = getBedwarsConfigFile("bedwars.yml");
        BedwarsConfigLoader loader = new BedwarsConfigLoader(configFile, plugin.getLogger());
        arenas = loader.load();
        matchEventConfig = loadMatchEventConfig(configFile);
        karmaEventConfig = loadKarmaEventConfig(configFile);
        moonBigConfig = loadMoonBigConfig(configFile);
        configureSharedLobby(configFile);
        configureLobbyLeaderboard(configFile);
        configureParkourLeaderboard(configFile);
        configureLobbyAmbientWorld(configFile);
        lobbyParkour.load(configFile);
        plugin.getLogger().info("Loaded " + arenas.size() + " BedWars arenas.");
    }

    public void loadShopConfig() {
        File configFile = getBedwarsConfigFile("shop.yml");
        ShopConfig baseConfig = new ShopConfigLoader(configFile, plugin.getLogger()).load();
        File rotatingFile = getBedwarsConfigFile("rotating-items.yml");
        if (rotatingFile.exists()) {
            ShopConfig rotatingConfig = new ShopConfigLoader(rotatingFile, plugin.getLogger()).load();
            shopConfig = ShopConfig.merge(baseConfig, rotatingConfig);
        } else {
            shopConfig = baseConfig;
        }
        plugin.getLogger().info("Loaded BedWars shop config.");
    }

    public void loadCustomItems() {
        File configFile = getBedwarsConfigFile("custom-items.yml");
        CustomItemConfigLoader loader = new CustomItemConfigLoader(configFile, plugin.getLogger());
        customItemConfig = loader.load();
        plugin.getLogger().info("Loaded BedWars custom items.");
    }

    public void loadQuickBuy() {
        quickBuyService.load();
    }

    public void loadStats() {
        statsService.load();
    }

    public void loadTimeCapsules() {
        timeCapsuleService.load();
    }

    public void loadKarma() {
        karmaService.load();
    }

    public void loadSkins() {
        skinSelections.clear();
        File file = resolveBedwarsSkinsLoadFile();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("skins");
        if (root == null) {
            return;
        }
        for (String playerKey : root.getKeys(false)) {
            UUID playerId = parseUuid(playerKey);
            if (playerId == null) {
                continue;
            }
            ConfigurationSection playerSection = root.getConfigurationSection(playerKey);
            if (playerSection == null) {
                continue;
            }
            EnumMap<SKIN_TYPE, BedwarsSkinSelection> selections = new EnumMap<>(SKIN_TYPE.class);
            for (String typeKey : playerSection.getKeys(false)) {
                SKIN_TYPE type = parseSkinType(typeKey);
                if (type == null) {
                    continue;
                }
                ConfigurationSection typeSection = playerSection.getConfigurationSection(typeKey);
                if (typeSection == null) {
                    continue;
                }
                String model = trimToNull(typeSection.getString("model"));
                String equipment = trimToNull(typeSection.getString("equipment"));
                if (model == null) {
                    continue;
                }
                selections.put(type, new BedwarsSkinSelection(model, equipment));
            }
            if (!selections.isEmpty()) {
                skinSelections.put(playerId, selections);
            }
        }
    }

    public void startLobbyLeaderboard() {
        lobbyLeaderboard.start();
        parkourLeaderboard.start();
        startLobbyAmbientChimeLoop();
    }

    public Collection<Arena> getArenas() {
        return arenas.values();
    }

    public Arena getArena(String id) {
        return arenas.get(id);
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public String getLobbyWorldName() {
        return resolveConfiguredLobbyWorldName();
    }

    public Location getLobbyLocation() {
        String worldName = resolveConfiguredLobbyWorldName();
        World world = worldName != null ? Bukkit.getWorld(worldName) : null;
        if (world == null) {
            return null;
        }
        BlockPoint spawn = sharedLobbySpawn != null ? sharedLobbySpawn : DEFAULT_LOBBY_SPAWN;
        return spawn.toLocation(world);
    }

    public GameSession getActiveSession() {
        return activeSession;
    }

    public ShopConfig getShopConfig() {
        return shopConfig;
    }

    public CustomItemConfig getCustomItemConfig() {
        return customItemConfig;
    }

    public BedwarsMatchEventConfig getMatchEventConfig() {
        return matchEventConfig;
    }

    public BedwarsKarmaEventConfig getKarmaEventConfig() {
        return karmaEventConfig;
    }

    public BedwarsMoonBigConfig getMoonBigConfig() {
        return moonBigConfig;
    }

    public QuickBuyService getQuickBuyService() {
        return quickBuyService;
    }

    public BedwarsStatsService getStatsService() {
        return statsService;
    }

    public TimeCapsuleService getTimeCapsuleService() {
        return timeCapsuleService;
    }

    public BedwarsKarmaService getKarmaService() {
        return karmaService;
    }

    public BedwarsLobbyParkour getLobbyParkour() {
        return lobbyParkour;
    }

    public BedwarsSkinSelection getSkinSelection(UUID playerId, SKIN_TYPE type) {
        if (playerId == null || type == null) {
            return null;
        }
        EnumMap<SKIN_TYPE, BedwarsSkinSelection> selections = skinSelections.get(playerId);
        return selections != null ? selections.get(type) : null;
    }

    public void setSkinSelection(UUID playerId, SKIN_TYPE type, BedwarsSkinSelection selection) {
        if (playerId == null || type == null) {
            return;
        }
        if (selection == null) {
            clearSkinSelection(playerId, type);
            return;
        }
        skinSelections
                .computeIfAbsent(playerId, ignored -> new EnumMap<>(SKIN_TYPE.class))
                .put(type, selection);
        saveSkins();
    }

    public void clearSkinSelection(UUID playerId, SKIN_TYPE type) {
        if (playerId == null || type == null) {
            return;
        }
        EnumMap<SKIN_TYPE, BedwarsSkinSelection> selections = skinSelections.get(playerId);
        if (selections == null) {
            return;
        }
        selections.remove(type);
        if (selections.isEmpty()) {
            skinSelections.remove(playerId);
        }
        saveSkins();
    }

    public boolean addTemporaryCreator(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return temporaryCreators.add(playerId);
    }

    public boolean removeTemporaryCreator(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return temporaryCreators.remove(playerId);
    }

    public boolean isTemporaryCreator(UUID playerId) {
        return playerId != null && temporaryCreators.contains(playerId);
    }

    public boolean isBedwarsWorld(String worldName) {
        String lobbyWorld = resolveConfiguredLobbyWorldName();
        if (lobbyWorld != null && lobbyWorld.equalsIgnoreCase(worldName)) {
            return true;
        }
        for (Arena arena : arenas.values()) {
            if (arena.getWorldName().equalsIgnoreCase(worldName)) {
                return true;
            }
        }
        return false;
    }

    public void openMapSelect(Player player) {
        openMapSelect(player, true);
    }

    public void openMapSelect(Player player, boolean statsEnabled) {
        if ( arenas.isEmpty()) {
            player.sendMessage(Component.text("No arenas configured.", NamedTextColor.RED));
            return;
        }
        new MapSelectMenu(this, statsEnabled).open(player);
    }

    public void openTeamAssignMenu(Player player, Arena arena) {
        openTeamAssignMenu(player, arena, true);
    }

    public void openTeamAssignMenu(Player player, Arena arena, boolean statsEnabled) {
        GameSession session = new GameSession(this, arena);
        session.setStatsEnabled(statsEnabled);
        session.setTestMode(!statsEnabled);
        session.setMaxTeamSize(1);
        new TeamAssignMenu(this, session).open(player);
    }

    public void openQuickBuyEditor(Player player) {
        if (player == null) {
            return;
        }
        if (shopConfig == null) {
            player.sendMessage(Component.text("Shop config is not loaded.", NamedTextColor.RED));
            return;
        }
        Arena arena = arenas.values().stream().findFirst().orElse(null);
        if (arena == null) {
            player.sendMessage(Component.text("No arenas configured.", NamedTextColor.RED));
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!quickBuyService.isEditing(playerId)) {
            quickBuyService.toggleEditing(playerId);
        }
        quickBuyService.clearPendingSlot(playerId);
        GameSession menuSession = new GameSession(this, arena);
        new ShopMenu(menuSession, shopConfig, ShopCategoryType.QUICK_BUY, player).open(player);
    }

    public void startSession(Player initiator, GameSession session) {
        if (session.getAssignedCount() == 0 && !session.isTeamPickEnabled()) {
            initiator.sendMessage(Component.text("Assign at least one player to a team or enable Team Pick.", NamedTextColor.RED));
            return;
        }

        World world = session.getArena().getWorld();
        if (world == null) {
            initiator.sendMessage(Component.text("World not loaded: " + session.getArena().getWorldName(), NamedTextColor.RED));
            return;
        }

        if (activeSession != null) {
            activeSession.stop();
        }

        activeSession = session;
        session.start(plugin, initiator);
        initiator.sendMessage(Component.text("BedWars started on " + session.getArena().getId() + ".", NamedTextColor.GREEN));
    }

    public void startLobby(Player initiator, GameSession session, int lobbySeconds) {
        if (session.getAssignedCount() == 0 && !session.isTeamPickEnabled()) {
            initiator.sendMessage(Component.text("Assign at least one player to a team or enable Team Pick.", NamedTextColor.RED));
            return;
        }

        World world = session.getArena().getWorld();
        if (world == null) {
            initiator.sendMessage(Component.text("World not loaded: " + session.getArena().getWorldName(), NamedTextColor.RED));
            return;
        }

        if (activeSession != null) {
            activeSession.stop();
        }

        activeSession = session;
        session.startLobby(plugin, initiator, lobbySeconds);
        initiator.sendMessage(Component.text("BedWars lobby started on " + session.getArena().getId() + ".", NamedTextColor.GREEN));
    }

    public void stopSession(Player initiator) {
        if (activeSession == null) {
            initiator.sendMessage(Component.text("No BedWars session is running.", NamedTextColor.RED));
            return;
        }
        activeSession.stop();
        activeSession = null;
        initiator.sendMessage(Component.text("BedWars session stopped.", NamedTextColor.YELLOW));
    }

    public void endSession(GameSession session, TeamColor winner) {
        if (session != activeSession) {
            return;
        }
        session.finalizePartyExpRewards(winner);
        Component message;
        if (winner != null) {
            message = Component.text("Team ", NamedTextColor.GOLD)
                    .append(winner.displayComponent())
                    .append(Component.text(" wins!", NamedTextColor.GOLD));
        } else {
            message = Component.text("BedWars ended.", NamedTextColor.YELLOW);
        }
        for (UUID playerId : session.getAssignments().keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
                if (winner != null && winner.equals(session.getAssignments().get(playerId))) {
                    launchVictoryFirework(player, winner);
                }
            }
        }
        if (winner != null && session.isStatsEnabled()) {
            for (Map.Entry<UUID, TeamColor> entry : session.getAssignments().entrySet()) {
                if (winner.equals(entry.getValue())) {
                    statsService.addWin(entry.getKey());
                }
            }
        }
        session.stop();
        activeSession = null;
    }

    public void shutdown() {
        stopLobbyAmbientChimeLoop();
        if (activeSession != null) {
            activeSession.stop();
            activeSession = null;
        }
        temporaryCreators.clear();
        lobbyLeaderboard.stop();
        parkourLeaderboard.stop();
        lobbyParkour.shutdown();
        quickBuyService.shutdown();
        statsService.shutdown();
        timeCapsuleService.shutdown();
        karmaService.shutdown();
        saveSkins();
        skinSelections.clear();
        clearDroppedItems();
    }

    private void clearDroppedItems() {
        for (Arena arena : arenas.values()) {
            World world = arena.getWorld();
            if (world == null) {
                continue;
            }
            for (Item item : world.getEntitiesByClass(Item.class)) {
                item.remove();
            }
        }
    }

    private void launchVictoryFirework(Player player, TeamColor team) {
        player.getWorld().spawn(player.getLocation(), Firework.class, firework -> {
            FireworkMeta meta = firework.getFireworkMeta();
            FireworkEffect effect = FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(team.dyeColor().getColor())
                    .flicker(true)
                    .trail(true)
                    .build();
            meta.addEffect(effect);
            meta.setPower(1);
            firework.setFireworkMeta(meta);
        });
    }

    private BedwarsMatchEventConfig loadMatchEventConfig(File configFile) {
        if (!configFile.exists()) {
            return BedwarsMatchEventConfig.defaults();
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection section = config.getConfigurationSection("match-events");
        if (section == null) {
            return BedwarsMatchEventConfig.defaults();
        }
        boolean enabled = section.getBoolean("enabled", true);
        double chancePercent = section.getDouble("chance-percent", 40.0);
        EnumMap<BedwarsMatchEventType, Integer> weights = new EnumMap<>(BedwarsMatchEventType.class);
        ConfigurationSection events = section.getConfigurationSection("events");
        if (events != null) {
            for (String key : events.getKeys(false)) {
                BedwarsMatchEventType type = BedwarsMatchEventType.fromKey(key);
                if (type == null) {
                    continue;
                }
                weights.put(type, Math.max(0, events.getInt(key + ".weight", type.defaultWeight())));
            }
        }
        return new BedwarsMatchEventConfig(enabled, chancePercent, weights);
    }

    private BedwarsKarmaEventConfig loadKarmaEventConfig(File configFile) {
        BedwarsKarmaEventConfig defaults = BedwarsKarmaEventConfig.defaults();
        if (!configFile.exists()) {
            return defaults;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection section = config.getConfigurationSection("karma-events");
        if (section == null) {
            return defaults;
        }
        double minCheckSeconds = section.getDouble("check-min-seconds", defaults.minCheckSeconds());
        double maxCheckSeconds = section.getDouble("check-max-seconds", defaults.maxCheckSeconds());
        double baseRollChancePercent = section.getDouble("base-roll-chance-percent", defaults.baseRollChancePercent());
        double perKarmaChancePercent = section.getDouble("per-karma-chance-percent", defaults.perKarmaChancePercent());
        return new BedwarsKarmaEventConfig(
                minCheckSeconds,
                maxCheckSeconds,
                baseRollChancePercent,
                perKarmaChancePercent
        );
    }

    private BedwarsMoonBigConfig loadMoonBigConfig(File configFile) {
        BedwarsMoonBigConfig defaults = BedwarsMoonBigConfig.defaults();
        if (!configFile.exists()) {
            return defaults;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection section = config.getConfigurationSection("match-events");
        if (section == null) {
            return defaults;
        }
        ConfigurationSection moonBig = section.getConfigurationSection("moon-big");
        if (moonBig == null) {
            return defaults;
        }
        ConfigurationSection asteroids = moonBig.getConfigurationSection("asteroids");
        if (asteroids == null) {
            return defaults;
        }
        double fallSpeed = asteroids.getDouble("fall-speed-blocks-per-second", defaults.fallSpeedBlocksPerSecond());
        int startMin = asteroids.getInt("start-interval-min-seconds", defaults.startIntervalMinSeconds());
        int startMax = asteroids.getInt("start-interval-max-seconds", defaults.startIntervalMaxSeconds());
        int endMin = asteroids.getInt("end-interval-min-seconds", defaults.endIntervalMinSeconds());
        int endMax = asteroids.getInt("end-interval-max-seconds", defaults.endIntervalMaxSeconds());
        int radiusMin = asteroids.getInt("radius-min", defaults.radiusMin());
        int radiusMax = asteroids.getInt("radius-max", defaults.radiusMax());
        double missingChance = asteroids.getDouble("missing-block-chance", defaults.missingBlockChance());
        double crateChance = asteroids.getDouble("crate-chance", defaults.crateChance());
        int spawnHeight = asteroids.getInt("spawn-height-above-ground", defaults.spawnHeightAboveGround());
        double explosionMultiplier = asteroids.getDouble("explosion-power-multiplier", defaults.explosionPowerMultiplier());
        return new BedwarsMoonBigConfig(
                fallSpeed,
                startMin,
                startMax,
                endMin,
                endMax,
                radiusMin,
                radiusMax,
                missingChance,
                crateChance,
                spawnHeight,
                explosionMultiplier
        );
    }

    private void configureSharedLobby(File configFile) {
        sharedLobbyWorldName = DEFAULT_LOBBY_WORLD_NAME;
        sharedLobbySpawn = DEFAULT_LOBBY_SPAWN;
        if (!configFile.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection section = config.getConfigurationSection("lobby");
        if (section == null) {
            return;
        }
        String configuredWorld = trimToNull(section.getString("world"));
        if (configuredWorld != null) {
            sharedLobbyWorldName = configuredWorld;
        }
        BlockPoint configuredSpawn = parseOptionalPoint(section.getString("spawn"));
        if (configuredSpawn == null) {
            configuredSpawn = parseOptionalPoint(section.getString("location"));
        }
        if (configuredSpawn != null) {
            sharedLobbySpawn = configuredSpawn;
        }
    }

    private void configureLobbyLeaderboard(File configFile) {
        String worldName = null;
        double x = DEFAULT_LEADERBOARD_X;
        double y = DEFAULT_LEADERBOARD_Y;
        double z = DEFAULT_LEADERBOARD_Z;

        if (configFile.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            ConfigurationSection section = config.getConfigurationSection("leaderboard");
            if (section != null) {
                String sectionWorld = section.getString("world");
                if (sectionWorld != null && !sectionWorld.isBlank()) {
                    worldName = sectionWorld.trim();
                }
                if (section.isSet("x") && section.isSet("y") && section.isSet("z")) {
                    x = section.getDouble("x", x);
                    y = section.getDouble("y", y);
                    z = section.getDouble("z", z);
                } else {
                    String rawSection = section.getString("location");
                    if (rawSection != null && !rawSection.isBlank()) {
                        String parsedWorld = parseLeaderboardEntry(rawSection, worldName);
                        if (parsedWorld != null) {
                            worldName = parsedWorld;
                        }
                        double[] parsedCoords = parseLeaderboardCoordinates(rawSection);
                        if (parsedCoords != null) {
                            x = parsedCoords[0];
                            y = parsedCoords[1];
                            z = parsedCoords[2];
                        }
                    }
                }
            } else {
                String raw = config.getString("leaderboard");
                if (raw != null && !raw.isBlank()) {
                    String parsedWorld = parseLeaderboardEntry(raw, null);
                    if (parsedWorld != null) {
                        worldName = parsedWorld;
                    }
                    double[] parsedCoords = parseLeaderboardCoordinates(raw);
                    if (parsedCoords != null) {
                        x = parsedCoords[0];
                        y = parsedCoords[1];
                        z = parsedCoords[2];
                    } else {
                        plugin.getLogger().warning("Invalid leaderboard location in bedwars.yml: " + raw);
                    }
                }
            }
        }

        if (worldName == null || worldName.isBlank()) {
            worldName = resolveConfiguredLobbyWorldName();
        }
        if (worldName == null || worldName.isBlank()) {
            worldName = resolveLeaderboardWorldFallback();
        }
        lobbyLeaderboard.configureAnchor(worldName, x, y, z);
    }

    private void configureParkourLeaderboard(File configFile) {
        String worldName = null;
        double x = DEFAULT_LEADERBOARD_X + 2.0;
        double y = DEFAULT_LEADERBOARD_Y;
        double z = DEFAULT_LEADERBOARD_Z;
        if (configFile.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            ConfigurationSection section = config.getConfigurationSection("parkour-leaderboard");
            if (section != null) {
                String sectionWorld = section.getString("world");
                if (sectionWorld != null && !sectionWorld.isBlank()) {
                    worldName = sectionWorld.trim();
                }
                if (section.isSet("x") && section.isSet("y") && section.isSet("z")) {
                    x = section.getDouble("x", x);
                    y = section.getDouble("y", y);
                    z = section.getDouble("z", z);
                } else {
                    String rawSection = section.getString("location");
                    if (rawSection != null && !rawSection.isBlank()) {
                        String parsedWorld = parseLeaderboardEntry(rawSection, worldName);
                        if (parsedWorld != null) {
                            worldName = parsedWorld;
                        }
                        double[] parsedCoords = parseLeaderboardCoordinates(rawSection);
                        if (parsedCoords != null) {
                            x = parsedCoords[0];
                            y = parsedCoords[1];
                            z = parsedCoords[2];
                        }
                    }
                }
            } else {
                String raw = config.getString("parkour-leaderboard");
                if (raw != null && !raw.isBlank()) {
                    String parsedWorld = parseLeaderboardEntry(raw, null);
                    if (parsedWorld != null) {
                        worldName = parsedWorld;
                    }
                    double[] parsedCoords = parseLeaderboardCoordinates(raw);
                    if (parsedCoords != null) {
                        x = parsedCoords[0];
                        y = parsedCoords[1];
                        z = parsedCoords[2];
                    } else {
                        plugin.getLogger().warning("Invalid parkour-leaderboard location in bedwars.yml: " + raw);
                    }
                }
            }
        }
        if (worldName == null || worldName.isBlank()) {
            worldName = resolveLobbyParkourWorld(configFile);
        }
        if (worldName == null || worldName.isBlank()) {
            worldName = resolveConfiguredLobbyWorldName();
        }
        if (worldName == null || worldName.isBlank()) {
            worldName = resolveLeaderboardWorldFallback();
        }
        parkourLeaderboard.configureAnchor(worldName, x, y, z);
    }

    private void configureLobbyAmbientWorld(File configFile) {
        String worldName = resolveConfiguredLobbyWorldName();
        if (worldName == null || worldName.isBlank()) {
            worldName = resolveLobbyParkourWorld(configFile);
        }
        if (worldName == null || worldName.isBlank()) {
            worldName = resolveLeaderboardWorldFallback();
        }
        lobbyAmbientWorldName = worldName;
    }

    private String resolveLobbyParkourWorld(File configFile) {
        if (configFile == null || !configFile.exists()) {
            return null;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection section = config.getConfigurationSection("lobby-parkour");
        if (section == null) {
            return null;
        }
        String worldName = section.getString("world");
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        return worldName.trim();
    }

    private String parseLeaderboardEntry(String raw, String defaultWorld) {
        if (raw == null || raw.isBlank()) {
            return defaultWorld;
        }
        String[] parts = raw.trim().split("\\s+");
        if (parts.length == 4) {
            return parts[0];
        }
        return defaultWorld;
    }

    private double[] parseLeaderboardCoordinates(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split("\\s+");
        int start = parts.length == 4 ? 1 : 0;
        if (parts.length - start != 3) {
            return null;
        }
        try {
            return new double[]{
                    Double.parseDouble(parts[start]),
                    Double.parseDouble(parts[start + 1]),
                    Double.parseDouble(parts[start + 2])
            };
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolveLeaderboardWorldFallback() {
        String lobbyWorld = resolveConfiguredLobbyWorldName();
        if (lobbyWorld != null) {
            return lobbyWorld;
        }
        for (Arena arena : arenas.values()) {
            World world = arena.getWorld();
            if (world != null) {
                return world.getName();
            }
            if (arena.getWorldName() != null && !arena.getWorldName().isBlank()) {
                return arena.getWorldName();
            }
        }
        World secondary = Bukkit.getWorld("bw");
        if (secondary != null) {
            return secondary.getName();
        }
        return null;
    }

    private String resolveConfiguredLobbyWorldName() {
        String configured = trimToNull(sharedLobbyWorldName);
        if (configured != null) {
            return configured;
        }
        return trimToNull(DEFAULT_LOBBY_WORLD_NAME);
    }

    private void startLobbyAmbientChimeLoop() {
        stopLobbyAmbientChimeLoop();
        scheduleNextLobbyAmbientChime();
    }

    private void stopLobbyAmbientChimeLoop() {
        if (lobbyAmbientChimeTask != null) {
            lobbyAmbientChimeTask.cancel();
            lobbyAmbientChimeTask = null;
        }
    }

    private void scheduleNextLobbyAmbientChime() {
        if (!plugin.isEnabled()) {
            lobbyAmbientChimeTask = null;
            return;
        }
        long delay = ThreadLocalRandom.current().nextLong(LOBBY_CHIME_MIN_DELAY_TICKS, LOBBY_CHIME_MAX_DELAY_TICKS + 1L);
        lobbyAmbientChimeTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                playLobbyAmbientChimeIfNeeded();
            } finally {
                if (plugin.isEnabled()) {
                    scheduleNextLobbyAmbientChime();
                } else {
                    lobbyAmbientChimeTask = null;
                }
            }
        }, delay);
    }

    private void playLobbyAmbientChimeIfNeeded() {
        GameSession session = activeSession;
        if (session != null && session.isActive()) {
            return;
        }
        World world = resolveLobbyAmbientWorld();
        if (world == null || world.getPlayers().isEmpty()) {
            return;
        }
        Location origin = new Location(world, LOBBY_CHIME_X, LOBBY_CHIME_Y, LOBBY_CHIME_Z);
        for (Player player : world.getPlayers()) {
            player.playSound(origin, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER,
                    LOBBY_CHIME_VOLUME, LOBBY_CHIME_PITCH);
        }
    }

    private World resolveLobbyAmbientWorld() {
        if (lobbyAmbientWorldName != null && !lobbyAmbientWorldName.isBlank()) {
            World world = Bukkit.getWorld(lobbyAmbientWorldName);
            if (world != null) {
                return world;
            }
        }
        String fallback = resolveLeaderboardWorldFallback();
        if (fallback == null || fallback.isBlank()) {
            return null;
        }
        return Bukkit.getWorld(fallback);
    }

    private BlockPoint parseOptionalPoint(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return BlockPoint.parse(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void saveSkins() {
        File file = getBedwarsSkinsFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection root = config.createSection("skins");
        for (Map.Entry<UUID, EnumMap<SKIN_TYPE, BedwarsSkinSelection>> entry : skinSelections.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            ConfigurationSection playerSection = root.createSection(entry.getKey().toString());
            for (Map.Entry<SKIN_TYPE, BedwarsSkinSelection> skinEntry : entry.getValue().entrySet()) {
                SKIN_TYPE type = skinEntry.getKey();
                BedwarsSkinSelection selection = skinEntry.getValue();
                if (type == null || selection == null || selection.modelId() == null || selection.modelId().isBlank()) {
                    continue;
                }
                ConfigurationSection typeSection = playerSection.createSection(type.name().toLowerCase(Locale.ROOT));
                typeSection.set("model", selection.modelId());
                if (selection.equipmentModelId() != null && !selection.equipmentModelId().isBlank()) {
                    typeSection.set("equipment", selection.equipmentModelId());
                }
            }
        }
        try {
            config.save(file);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to save BedWars skins.yml: " + ex.getMessage());
        }
    }

    private File getBedwarsSkinsFile() {
        return new File(new File(plugin.getDataFolder(), SHARED_SKINS_FOLDER), BEDWARS_SKINS_FILE);
    }

    private File resolveBedwarsSkinsLoadFile() {
        File primary = getBedwarsSkinsFile();
        if (primary.exists()) {
            return primary;
        }
        File legacy = getBedwarsConfigFile(LEGACY_BEDWARS_SKINS_FILE);
        return legacy.exists() ? legacy : primary;
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private SKIN_TYPE parseSkinType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return SKIN_TYPE.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

}
