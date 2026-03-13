package krispasi.omGames.bedwars.game;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import krispasi.omGames.OmVeinsAPI;
import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.event.BedwarsMatchEventConfig;
import krispasi.omGames.bedwars.event.BedwarsMatchEventType;
import krispasi.omGames.bedwars.generator.GeneratorInfo;
import krispasi.omGames.bedwars.generator.GeneratorManager;
import krispasi.omGames.bedwars.generator.GeneratorType;
import krispasi.omGames.bedwars.gui.ShopMenu;
import krispasi.omGames.bedwars.gui.TeamPickMenu;
import krispasi.omGames.bedwars.gui.UpgradeShopMenu;
import krispasi.omGames.bedwars.model.Arena;
import krispasi.omGames.bedwars.model.BedLocation;
import krispasi.omGames.bedwars.model.BedState;
import krispasi.omGames.bedwars.model.BlockPoint;
import krispasi.omGames.bedwars.model.EventSettings;
import krispasi.omGames.bedwars.model.ShopLocation;
import krispasi.omGames.bedwars.model.ShopType;
import krispasi.omGames.bedwars.model.TeamColor;
import krispasi.omGames.bedwars.item.TeamSelectItemData;
import krispasi.omGames.bedwars.item.CustomItemData;
import krispasi.omGames.bedwars.item.CustomItemDefinition;
import krispasi.omGames.bedwars.item.CustomItemType;
import krispasi.omGames.bedwars.shop.ShopConfig;
import krispasi.omGames.bedwars.shop.ShopCost;
import krispasi.omGames.bedwars.shop.ShopCategoryType;
import krispasi.omGames.bedwars.shop.ShopItemBehavior;
import krispasi.omGames.bedwars.shop.ShopItemDefinition;
import krispasi.omGames.bedwars.shop.ShopItemLimit;
import krispasi.omGames.bedwars.shop.ShopItemLimitScope;
import krispasi.omGames.bedwars.upgrade.TeamUpgradeState;
import krispasi.omGames.bedwars.upgrade.TeamUpgradeType;
import krispasi.omGames.bedwars.upgrade.TrapType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Bed;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Runtime state machine for a single BedWars match.
 * <p>Tracks team assignments, beds, generators, placed blocks, upgrades, traps,
 * and per-player tool tiers.</p>
 * <p>Enforces match rules such as respawn timing, combat restrictions, sudden death,
 * and world border behavior.</p>
 * <p>Creates and tears down shop NPCs, scoreboards, and other match-scoped entities.</p>
 * @see krispasi.omGames.bedwars.game.GameState
 */
public class GameSession {
    private static final String SIDEBAR_OBJECTIVE_ID = "bedwars";
    private static final String HEALTH_OBJECTIVE_ID = "bedwars_hp";
    public enum RotatingSelectionMode {
        AUTO,
        MANUAL
    }

    public static final String ITEM_SHOP_TAG = "bw_item_shop";
    public static final String UPGRADES_SHOP_TAG = "bw_upgrades_shop";
    public static final String BED_BUG_TAG = "bw_bed_bug";
    public static final String DREAM_DEFENDER_TAG = "bw_dream_defender";
    public static final String CRYSTAL_TAG = "bw_crystal";
    public static final String CRYSTAL_EXPLOSION_TAG = "bw_crystal_exploded";
    public static final String HAPPY_GHAST_TAG = "bw_happy_ghast";
    public static final String CREEPING_CREEPER_TAG = "bw_creeping_creeper";
    public static final String ABYSSAL_RIFT_TAG = "bw_abyssal_rift";
    public static final String ABYSSAL_RIFT_DISPLAY_TAG = "bw_abyssal_rift_display";
    public static final String ABYSSAL_RIFT_NAME_TAG = "bw_abyssal_rift_nameplate";
    public static final String ELYTRA_STRIKE_ACTIVE_ITEM_ID = "elytra_strike_active";
    private static final double DEFAULT_PLAYER_SCALE = 1.0;
    private static final double SCALE_DOWN_TIER_ONE = 0.9;
    private static final double SCALE_DOWN_TIER_TWO = 0.8;
    private static final double APRIL_FOOLS_SCALE_MULTIPLIER = 0.5;
    private static final double LONG_ARMS_RANGE_BONUS = 10.0;
    private static final double BLOOD_MOON_HEALTH_MULTIPLIER = 0.5;
    private static final double MOON_BIG_JUMP_MULTIPLIER = 2.5;
    private static final double BLOOD_MOON_LIFESTEAL_RATIO = 1.0;
    private static final Duration TITLE_FADE_IN = Duration.ofMillis(300);
    private static final Duration TITLE_STAY = Duration.ofSeconds(2);
    private static final Duration TITLE_FADE_OUT = Duration.ofSeconds(1);
    private static final Duration ALERT_TITLE_STAY = Duration.ofSeconds(3);
    private static final Title.Times DEFAULT_TITLE_TIMES =
            Title.Times.times(TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT);
    private static final Title.Times ALERT_TITLE_TIMES =
            Title.Times.times(TITLE_FADE_IN, ALERT_TITLE_STAY, TITLE_FADE_OUT);
    private static final int ABYSSAL_RIFT_AURA_INTERVAL_TICKS = 20;
    private static final int ABYSSAL_RIFT_EFFECT_DURATION_TICKS = 40;
    private static final float ABYSSAL_RIFT_HITBOX_WIDTH = 1.0f;
    private static final float ABYSSAL_RIFT_HITBOX_HEIGHT = 2.0f;
    private static final double ABYSSAL_RIFT_DISPLAY_Y_OFFSET = 1.5;
    private static final double ABYSSAL_RIFT_NAME_Y_OFFSET = 2.35;
    private static final double ABYSSAL_RIFT_HEALTH_Y_OFFSET = 2.1;
    private static final int ELYTRA_STRIKE_ALTITUDE = 300;
    private static final int ELYTRA_STRIKE_REGEN_DURATION_TICKS = 20;
    private static final int ELYTRA_STRIKE_REGEN_AMPLIFIER = 9;
    private static final long MIRACLE_OF_THE_STARS_DELAY_TICKS = 5L * 20L;
    private static final long TOWER_CHEST_TEMP_CHEST_TICKS = 8L;
    private static final NamespacedKey ABYSSAL_RIFT_ITEM_MODEL = new NamespacedKey("om", "rift1");
    private static final String[][] TOWER_CHEST_LAYERS = new String[][]{
            {
                    "0000000",
                    "00xxx00",
                    "0x0L0x0",
                    "0x0C0x0",
                    "00x0x00",
                    "0000000"
            },
            {
                    "0000000",
                    "00xxx00",
                    "0x0L0x0",
                    "0x000x0",
                    "00x0x00",
                    "0000000"
            },
            {
                    "0000000",
                    "00xxx00",
                    "0x0L0x0",
                    "0x000x0",
                    "00xxx00",
                    "0000000"
            },
            {
                    "0x0x0x0",
                    "xxxxxxx",
                    "0xxLxx0",
                    "0xxxxx0",
                    "xxxxxxx",
                    "0x0x0x0"
            },
            {
                    "0xxxxx0",
                    "x00000x",
                    "x00000x",
                    "x00000x",
                    "x00000x",
                    "0xxxxx0"
            },
            {
                    "0x0x0x0",
                    "x00000x",
                    "0000000",
                    "0000000",
                    "x00000x",
                    "0x0x0x0"
            }
    };
    private static final Set<String> IN_THIS_ECONOMY_BANNED_ITEMS = Set.of(
            "fireball",
            "bed_bug",
            "dream_defender"
    );
    private static final List<TeamUpgradeType> BENEVOLENT_UPGRADE_POOL = List.of(
            TeamUpgradeType.PROTECTION,
            TeamUpgradeType.SHARPNESS,
            TeamUpgradeType.HASTE,
            TeamUpgradeType.EFFICIENCY,
            TeamUpgradeType.FEATHER_FALLING,
            TeamUpgradeType.THORNS,
            TeamUpgradeType.FIRE_ASPECT,
            TeamUpgradeType.FORGE,
            TeamUpgradeType.HEAL_POOL
    );
    private static final Set<Material> SWORD_MATERIALS = EnumSet.of(
            Material.WOODEN_SWORD,
            Material.STONE_SWORD,
            Material.IRON_SWORD,
            Material.DIAMOND_SWORD,
            Material.MACE,
            Material.NETHERITE_SPEAR
    );
    private static final Set<Material> WOODEN_SWORD_ONLY = EnumSet.of(Material.WOODEN_SWORD);
    private static final Set<Material> BOW_MATERIALS = EnumSet.of(Material.BOW);
    private static final Set<Material> CROSSBOW_MATERIALS = EnumSet.of(Material.CROSSBOW);
    private static final Set<Material> PICKAXE_MATERIALS = EnumSet.of(
            Material.WOODEN_PICKAXE,
            Material.STONE_PICKAXE,
            Material.IRON_PICKAXE,
            Material.GOLDEN_PICKAXE,
            Material.DIAMOND_PICKAXE
    );
    private static final Set<Material> AXE_MATERIALS = EnumSet.of(
            Material.WOODEN_AXE,
            Material.STONE_AXE,
            Material.IRON_AXE,
            Material.GOLDEN_AXE,
            Material.DIAMOND_AXE
    );
    private static final Set<Material> TOOL_EFFICIENCY_MATERIALS = EnumSet.of(
            Material.WOODEN_PICKAXE,
            Material.STONE_PICKAXE,
            Material.IRON_PICKAXE,
            Material.GOLDEN_PICKAXE,
            Material.DIAMOND_PICKAXE,
            Material.WOODEN_AXE,
            Material.STONE_AXE,
            Material.IRON_AXE,
            Material.GOLDEN_AXE,
            Material.DIAMOND_AXE,
            Material.SHEARS
    );
    private static final Set<Material> ATTACK_MATERIALS = EnumSet.of(
            Material.WOODEN_SWORD,
            Material.STONE_SWORD,
            Material.IRON_SWORD,
            Material.DIAMOND_SWORD,
            Material.MACE,
            Material.NETHERITE_SPEAR,
            Material.WOODEN_AXE,
            Material.STONE_AXE,
            Material.IRON_AXE,
            Material.GOLDEN_AXE,
            Material.DIAMOND_AXE,
            Material.TRIDENT
    );
    private static final int START_COUNTDOWN_SECONDS = 5;
    private static final int RESPAWN_DELAY_SECONDS = 5;
    private static final int RESPAWN_PROTECTION_SECONDS = 5;
    private static final int DEFAULT_BASE_RADIUS = 8;
    private static final int HEAL_POOL_INTERVAL_TICKS = 20;
    private static final int HEAL_POOL_DURATION_TICKS = 60;
    private static final int TRAP_CHECK_INTERVAL_TICKS = 20;
    private static final int TRAP_MAX_COUNT = 3;
    private static final int TRAP_BLINDNESS_SECONDS = 8;
    private static final int TRAP_SLOW_SECONDS = 8;
    private static final int TRAP_COUNTER_SECONDS = 15;
    private static final int TRAP_FATIGUE_SECONDS = 10;
    private static final int LOBBY_REGEN_DURATION_TICKS = 60;
    private static final long REGEN_DELAY_MILLIS = 4000L;
    private static final long REGEN_INTERVAL_MILLIS = 3000L;
    private static final double SUDDEN_DEATH_BORDER_TARGET_SIZE = 6.0;
    private static final double WORLD_BORDER_DAMAGE_BUFFER = 0.0;
    private static final double WORLD_BORDER_DAMAGE_AMOUNT = 2.0;
    private static final int WORLD_BORDER_WARNING_DISTANCE = 1;
    private static final int LONG_MATCH_PARTY_EXP_SECONDS = 8 * 60;
    private static final int PARTY_EXP_LONG_MATCH = 50;
    private static final int PARTY_EXP_BED_GONE_MATCH = 100;
    private static final int PARTY_EXP_WIN = 100;
    private static final int PARTY_EXP_SECOND_PLACE = 50;
    private static final int PARTY_EXP_THIRD_PLACE = 25;
    private static final int PARTY_EXP_KILL = 1;
    private static final int PARTY_EXP_BED_BREAK = 10;
    private static final int PARTY_EXP_FINAL_KILL = 5;

    private final BedwarsManager bedwarsManager;
    private final Arena arena;
    private final Map<UUID, TeamColor> assignments = new HashMap<>();
    private final Map<TeamColor, BedState> bedStates = new EnumMap<>(TeamColor.class);
    private final Map<TeamColor, BedLocation> activeBedLocations = new EnumMap<>(TeamColor.class);
    private final Map<BlockPoint, TeamColor> bedBlocks = new HashMap<>();
    private final Set<BlockPoint> placedBlocks = new HashSet<>();
    private final Map<BlockPoint, ItemStack> placedBlockItems = new HashMap<>();
    private final Map<BlockPoint, BlockState> temporaryMapLobbyIslandBlocks = new HashMap<>();
    private final Set<Long> forcedChunks = new HashSet<>();
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Set<UUID> eliminatedPlayers = new HashSet<>();
    private final Set<TeamColor> eliminatedTeams = new HashSet<>();
    private final List<TeamColor> eliminationOrder = new ArrayList<>();
    private final Set<UUID> pendingRespawns = new HashSet<>();
    private final Set<UUID> respawnGracePlayers = new HashSet<>();
    private final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> respawnCountdownTasks = new HashMap<>();
    private final Map<UUID, Long> respawnProtectionEnds = new HashMap<>();
    private final Map<UUID, BukkitTask> respawnProtectionTasks = new HashMap<>();
    private final Map<UUID, Inventory> fakeEnderChests = new HashMap<>();
    private final Map<UUID, Scoreboard> previousScoreboards = new HashMap<>();
    private final Map<UUID, Scoreboard> activeScoreboards = new HashMap<>();
    private final Map<UUID, List<String>> sidebarLines = new HashMap<>();
    private final Set<String> rotatingItemIds = new HashSet<>();
    private final Set<String> rotatingUpgradeIds = new HashSet<>();
    private final List<String> manualRotatingItemIds = new ArrayList<>();
    private final List<String> manualRotatingUpgradeIds = new ArrayList<>();
    private RotatingSelectionMode rotatingMode = RotatingSelectionMode.AUTO;
    private final Map<UUID, AbyssalRiftState> abyssalRifts = new HashMap<>();
    private final Map<UUID, UUID> abyssalRiftEntityLinks = new HashMap<>();
    private final Map<UUID, ElytraStrikeState> activeElytraStrikes = new HashMap<>();
    private final Map<UUID, Integer> killCounts = new HashMap<>();
    private final Map<UUID, Integer> pendingPartyExp = new HashMap<>();
    private final Map<TeamColor, Map<String, Integer>> teamPurchaseCounts = new EnumMap<>(TeamColor.class);
    private final Map<UUID, Map<String, Integer>> playerPurchaseCounts = new HashMap<>();
    private final Map<UUID, Map<String, Long>> customItemCooldownEnds = new HashMap<>();
    private final Map<UUID, UUID> pendingDeathKillCredits = new HashMap<>();
    private final Set<UUID> editorPlayers = new HashSet<>();
    private final Set<UUID> lockedCommandSpectators = new HashSet<>();
    private final Set<TeamColor> teamsInMatch = EnumSet.noneOf(TeamColor.class);
    private final Set<UUID> shopNpcIds = new HashSet<>();
    private final Map<TeamColor, TeamUpgradeState> teamUpgrades = new EnumMap<>(TeamColor.class);
    private final Map<TeamColor, BlockPoint> baseCenters = new EnumMap<>(TeamColor.class);
    private final Map<TeamColor, Set<UUID>> baseOccupants = new EnumMap<>(TeamColor.class);
    private final Map<UUID, Long> trapImmunityEnds = new HashMap<>();
    private final Map<UUID, BukkitTask> trapImmunityTasks = new HashMap<>();
    private final Map<UUID, Integer> armorTiers = new HashMap<>();
    private final Map<UUID, Integer> pickaxeTiers = new HashMap<>();
    private final Map<UUID, Integer> axeTiers = new HashMap<>();
    private final Set<UUID> shearsUnlocked = new HashSet<>();
    private final Set<UUID> shieldUnlocked = new HashSet<>();
    private final List<TeamUpgradeType> benevolentEventUpgrades = new ArrayList<>();
    private boolean suddenDeathActive;
    private boolean tier2Triggered;
    private boolean tier3Triggered;
    private boolean bedDestructionTriggered;
    private boolean gameEndTriggered;
    private final Map<UUID, Long> lastCombatTimes = new HashMap<>();
    private final Map<UUID, UUID> lastDamagers = new HashMap<>();
    private final Map<UUID, Long> lastDamagerTimes = new HashMap<>();
    private final Map<UUID, Long> lastDamageTimes = new HashMap<>();
    private final Map<UUID, Long> lastRegenTimes = new HashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private GeneratorManager generatorManager;
    private GameState state = GameState.IDLE;
    private JavaPlugin plugin;
    private BukkitTask sidebarTask;
    private long matchStartMillis;
    private int startCountdownRemaining;
    private int maxTeamSize = 4;
    private boolean teamPickEnabled;
    private BukkitTask lobbyCountdownTask;
    private boolean lobbyCountdownPaused;
    private int lobbyCountdownRemaining;
    private UUID lobbyInitiatorId;
    private Double previousBorderSize;
    private Location previousBorderCenter;
    private Double previousBorderDamageAmount;
    private Double previousBorderDamageBuffer;
    private Integer previousBorderWarningDistance;
    private boolean statsEnabled = true;
    private int grantedMatchParticipationPartyExp;
    private boolean partyExpUnavailableLogged;
    private boolean matchEventRollEnabled = true;
    private BedwarsMatchEventType forcedMatchEvent;
    private BedwarsMatchEventType activeMatchEvent;
    private Long previousWorldTime;
    private Boolean previousDaylightCycle;

    public GameSession(BedwarsManager bedwarsManager, Arena arena) {
        this.bedwarsManager = bedwarsManager;
        this.arena = arena;
    }

    public Arena getArena() {
        return arena;
    }

    public BedwarsManager getBedwarsManager() {
        return bedwarsManager;
    }

    public GameState getState() {
        return state;
    }

    public boolean isStarting() {
        return state == GameState.STARTING;
    }

    public boolean isLobby() {
        return state == GameState.LOBBY;
    }

    public boolean isLobbyInitiator(UUID playerId) {
        return playerId != null && playerId.equals(lobbyInitiatorId);
    }

    public boolean isRunning() {
        return state == GameState.RUNNING;
    }

    public boolean isActive() {
        return state == GameState.LOBBY || state == GameState.STARTING || state == GameState.RUNNING;
    }

    public boolean isStatsEnabled() {
        return statsEnabled;
    }

    public void setStatsEnabled(boolean statsEnabled) {
        this.statsEnabled = statsEnabled;
    }

    public void applyUpgradesTo(Player player) {
        if (player == null) {
            return;
        }
        TeamColor team = getTeam(player.getUniqueId());
        if (team == null) {
            return;
        }
        applyTeamUpgrades(player, team);
    }

    public void assignTeam(UUID playerId, TeamColor team) {
        if (team == null) {
            assignments.remove(playerId);
        } else {
            assignments.put(playerId, team);
        }
        if (state == GameState.LOBBY) {
            updateTeamsInMatch();
        }
    }

    public TeamColor getTeam(UUID playerId) {
        return assignments.get(playerId);
    }

    public Map<UUID, TeamColor> getAssignments() {
        return Collections.unmodifiableMap(assignments);
    }

    public int getMaxTeamSize() {
        return maxTeamSize;
    }

    public void setMaxTeamSize(int size) {
        maxTeamSize = Math.max(1, Math.min(4, size));
    }

    public boolean isTeamPickEnabled() {
        return teamPickEnabled;
    }

    public void setTeamPickEnabled(boolean enabled) {
        teamPickEnabled = enabled;
    }

    public int getTeamMemberCount(TeamColor team) {
        if (team == null) {
            return 0;
        }
        int count = 0;
        for (TeamColor assigned : assignments.values()) {
            if (team.equals(assigned)) {
                count++;
            }
        }
        return count;
    }

    public boolean isTeamFull(TeamColor team) {
        return team != null && getTeamMemberCount(team) >= maxTeamSize;
    }

    public int getAssignedCount() {
        return assignments.size();
    }

    public boolean isParticipant(UUID playerId) {
        return assignments.containsKey(playerId);
    }

    public int getKillCount(UUID playerId) {
        return killCounts.getOrDefault(playerId, 0);
    }

    public void addKill(UUID playerId) {
        if (playerId == null || !isParticipant(playerId)) {
            return;
        }
        killCounts.merge(playerId, 1, Integer::sum);
        queuePartyExp(playerId, PARTY_EXP_KILL);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            updateSidebarForPlayer(player);
        }
    }

    public void rewardFinalKill(UUID playerId) {
        queuePartyExp(playerId, PARTY_EXP_FINAL_KILL);
    }

    public void finalizePartyExpRewards(TeamColor winner) {
        if (!isPartyExpEnabled()) {
            return;
        }
        finalizeMatchParticipationPartyExp();
        queueFinalPlacementPartyExp(winner);
        if (winner != null) {
            queuePartyExpForTeam(winner, PARTY_EXP_WIN);
        }
        flushPendingPartyExpForOnlineParticipants();
    }

    public boolean isEditor(UUID playerId) {
        return playerId != null && editorPlayers.contains(playerId);
    }

    public boolean isLockedCommandSpectator(UUID playerId) {
        return playerId != null && lockedCommandSpectators.contains(playerId);
    }

    public void addLockedCommandSpectator(UUID playerId) {
        if (playerId == null) {
            return;
        }
        lockedCommandSpectators.add(playerId);
    }

    public void removeLockedCommandSpectator(UUID playerId) {
        if (playerId == null) {
            return;
        }
        lockedCommandSpectators.remove(playerId);
    }

    public Location getLockedCommandSpectatorLocation() {
        return resolveMapLobbyLocation();
    }

    public boolean isTeamInMatch(TeamColor team) {
        return team != null && teamsInMatch.contains(team);
    }

    public boolean removeParticipant(Player player) {
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (!isParticipant(playerId)) {
            return false;
        }
        assignments.remove(playerId);
        eliminatedPlayers.remove(playerId);
        pendingRespawns.remove(playerId);
        respawnGracePlayers.remove(playerId);
        frozenPlayers.remove(playerId);
        armorTiers.remove(playerId);
        pickaxeTiers.remove(playerId);
        axeTiers.remove(playerId);
        shearsUnlocked.remove(playerId);
        shieldUnlocked.remove(playerId);
        killCounts.remove(playerId);
        playerPurchaseCounts.remove(playerId);
        pendingDeathKillCredits.remove(playerId);
        lockedCommandSpectators.remove(playerId);
        lastCombatTimes.remove(playerId);
        lastDamagers.remove(playerId);
        lastDamagerTimes.remove(playerId);
        lastDamageTimes.remove(playerId);
        lastRegenTimes.remove(playerId);
        cancelRespawnCountdown(playerId);
        removeRespawnProtection(playerId);
        BukkitTask respawnTask = respawnTasks.remove(playerId);
        if (respawnTask != null) {
            respawnTask.cancel();
        }
        restoreSidebar(playerId);
        clearUpgradeEffects(player);
        fakeEnderChests.remove(playerId);
        if (state == GameState.LOBBY) {
            updateTeamsInMatch();
        }
        return true;
    }

    public boolean forceJoin(Player player, TeamColor team) {
        if (player == null || team == null) {
            return false;
        }
        if (!isActive()) {
            return false;
        }
        if (state != GameState.LOBBY && !teamsInMatch.contains(team)) {
            return false;
        }
        Location spawn = null;
        if (state != GameState.LOBBY) {
            spawn = arena.getSpawn(team);
            if (spawn == null) {
                return false;
            }
        }
        UUID playerId = player.getUniqueId();
        removeEditor(player);
        removeLockedCommandSpectator(playerId);
        assignments.put(playerId, team);
        eliminatedPlayers.remove(playerId);
        pendingRespawns.remove(playerId);
        respawnGracePlayers.remove(playerId);
        frozenPlayers.remove(playerId);
        cancelRespawnCountdown(playerId);
        removeRespawnProtection(playerId);
        BukkitTask respawnTask = respawnTasks.remove(playerId);
        if (respawnTask != null) {
            respawnTask.cancel();
        }
        applyLobbyBuffs(player);
        double maxHealth = player.getMaxHealth();
        player.setHealth(Math.max(1.0, maxHealth));

        if (state == GameState.LOBBY) {
            updateTeamsInMatch();
            Location lobby = resolveMapLobbyLocation();
            if (lobby != null) {
                player.teleport(lobby);
            }
            player.getInventory().clear();
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            hideEditorsFrom(player);
            updateSidebarForPlayer(player);
            return true;
        }

        player.teleport(spawn);
        player.getInventory().clear();
        giveStarterKit(player, team);
        applyPermanentItemsWithShield(player, team);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        if (state == GameState.STARTING) {
            frozenPlayers.add(playerId);
        } else if (state == GameState.RUNNING) {
            grantRespawnProtection(player);
            if (statsEnabled) {
                bedwarsManager.getStatsService().addGamePlayed(playerId);
            }
        }
        hideEditorsFrom(player);
        updateSidebarForPlayer(player);
        return true;
    }

    public boolean reviveBed(TeamColor team) {
        if (team == null) {
            return false;
        }
        BedLocation location = arena.getBeds().get(team);
        World world = arena.getWorld();
        if (location == null || world == null) {
            return false;
        }
        boolean addedToMatch = teamsInMatch.add(team);
        if (!addedToMatch && getBedState(team) == BedState.ALIVE) {
            return false;
        }
        BedLocation previous = clearTrackedBed(team);
        if (previous != null && !previous.equals(location)) {
            removeBedBlocks(previous);
        }
        setBedAlive(team, location);
        restoreBed(world, team, location);
        eliminatedTeams.remove(team);
        eliminationOrder.remove(team);
        initializeBaseCenters();
        syncForgeTiers();
        updateSidebars();
        return true;
    }

    public boolean placeTeamBed(Player player, BedLocation location) {
        if (player == null || location == null || !isRunning() || !isParticipant(player.getUniqueId())) {
            return false;
        }
        TeamColor team = getTeam(player.getUniqueId());
        if (team == null) {
            return false;
        }
        if (suddenDeathActive) {
            player.sendMessage(Component.text("This bed is disabled after sudden death.", NamedTextColor.RED));
            return false;
        }
        if (getBedState(team) == BedState.ALIVE) {
            player.sendMessage(Component.text("Your team already has a bed.", NamedTextColor.RED));
            return false;
        }
        if (!isInsideMap(location.head()) || !isInsideMap(location.foot())) {
            player.sendMessage(Component.text("You cannot place your bed outside the map.", NamedTextColor.RED));
            return false;
        }
        if (isPlacementBlocked(location.head()) || isPlacementBlocked(location.foot())) {
            player.sendMessage(Component.text("You cannot place your bed here.", NamedTextColor.RED));
            return false;
        }
        teamsInMatch.add(team);
        BedLocation previous = clearTrackedBed(team);
        if (previous != null && !previous.equals(location)) {
            removeBedBlocks(previous);
        }
        setBedAlive(team, location);
        eliminatedTeams.remove(team);
        eliminationOrder.remove(team);
        reviveEliminatedTeammates(team);
        initializeBaseCenters();
        syncForgeTiers();
        updateSidebars();
        broadcast(Component.text("The ", NamedTextColor.GREEN)
                .append(team.displayComponent())
                .append(Component.text(" team rebuilt their bed. Respawns are back online.", NamedTextColor.GREEN)));
        playSoundToParticipants(Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 1.2f);
        return true;
    }

    public String skipNextPhase() {
        if (state != GameState.RUNNING) {
            return null;
        }
        if (!tier2Triggered) {
            triggerTierUpgrade(2);
            return "Generators II";
        }
        if (!tier3Triggered) {
            triggerTierUpgrade(3);
            return "Generators III";
        }
        if (!bedDestructionTriggered) {
            triggerBedDestructionEvent();
            return "Beds Destroyed";
        }
        if (!suddenDeathActive) {
            triggerSuddenDeath();
            return "Sudden Death";
        }
        if (!gameEndTriggered) {
            triggerGameEnd();
            return "Game End";
        }
        return null;
    }

    public void addEditor(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        editorPlayers.add(playerId);
        if (state == GameState.RUNNING) {
            applyEditorInvisibility(player);
            hideEditorFromParticipants(player);
        }
    }

    public void removeEditor(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!editorPlayers.remove(playerId)) {
            return;
        }
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
        showEditorToParticipants(player);
    }

    public boolean isInCombat(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        Long last = lastCombatTimes.get(playerId);
        if (last == null) {
            return false;
        }
        return System.currentTimeMillis() - last <= 15000L;
    }

    public UUID getRecentDamager(UUID victimId) {
        if (victimId == null) {
            return null;
        }
        Long last = lastDamagerTimes.get(victimId);
        if (last == null || System.currentTimeMillis() - last > 15000L) {
            return null;
        }
        return lastDamagers.get(victimId);
    }

    public void recordPendingDeathCredit(UUID victimId, UUID killerId) {
        if (victimId == null) {
            return;
        }
        if (!pendingRespawns.contains(victimId)) {
            pendingDeathKillCredits.remove(victimId);
            return;
        }
        pendingDeathKillCredits.put(victimId, victimId.equals(killerId) ? null : killerId);
    }

    public void recordCombat(UUID attackerId, UUID victimId) {
        long now = System.currentTimeMillis();
        if (attackerId != null) {
            lastCombatTimes.put(attackerId, now);
        }
        if (victimId != null) {
            lastCombatTimes.put(victimId, now);
            if (attackerId != null) {
                lastDamagers.put(victimId, attackerId);
                lastDamagerTimes.put(victimId, now);
            }
        }
    }

    public void recordDamage(UUID playerId) {
        if (playerId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        lastDamageTimes.put(playerId, now);
        lastRegenTimes.remove(playerId);
    }

    public void clearCombat(UUID playerId) {
        if (playerId == null) {
            return;
        }
        lastCombatTimes.remove(playerId);
        lastDamagers.remove(playerId);
        lastDamagerTimes.remove(playerId);
    }

    public int getPickaxeTier(UUID playerId) {
        return pickaxeTiers.getOrDefault(playerId, 0);
    }

    public int getArmorTier(UUID playerId) {
        return armorTiers.getOrDefault(playerId, 0);
    }

    public int getAxeTier(UUID playerId) {
        return axeTiers.getOrDefault(playerId, 0);
    }

    public boolean hasShieldUnlocked(UUID playerId) {
        return playerId != null && shieldUnlocked.contains(playerId);
    }

    public void syncToolTiers(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!isActive() || !isParticipant(playerId)) {
            return;
        }
        if (!isInArenaWorld(player.getWorld())) {
            return;
        }
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return;
        }
        int pickaxeTier = resolveToolTier(player, config, ShopItemBehavior.PICKAXE);
        int axeTier = resolveToolTier(player, config, ShopItemBehavior.AXE);
        updateToolTier(pickaxeTiers, playerId, pickaxeTier);
        updateToolTier(axeTiers, playerId, axeTier);
        if (hasInventoryItem(player, Material.SHEARS)) {
            shearsUnlocked.add(playerId);
        } else {
            shearsUnlocked.remove(playerId);
        }
    }

    public boolean isFrozen(UUID playerId) {
        return frozenPlayers.contains(playerId);
    }

    public boolean isInArenaWorld(World world) {
        return world != null && world.getName().equalsIgnoreCase(arena.getWorldName());
    }

    public BedState getBedState(TeamColor team) {
        return bedStates.getOrDefault(team, BedState.DESTROYED);
    }

    public BedLocation getActiveBedLocation(TeamColor team) {
        return activeBedLocations.get(team);
    }

    public TeamColor getBedOwner(BlockPoint point) {
        return bedBlocks.get(point);
    }

    public boolean isPlacedBlock(BlockPoint point) {
        return placedBlocks.contains(point);
    }

    public boolean isPlacementBlocked(BlockPoint point) {
        int baseRadius = arena.getBaseGeneratorRadius();
        int advancedRadius = arena.getAdvancedGeneratorRadius();
        if (baseRadius <= 0 && advancedRadius <= 0) {
            return false;
        }
        for (GeneratorInfo generator : arena.getGenerators()) {
            int radius = generator.type() == GeneratorType.BASE ? baseRadius : advancedRadius;
            if (radius <= 0) {
                continue;
            }
            if (isWithinRadius(point, generator.location(), radius)) {
                return true;
            }
        }
        return false;
    }

    public boolean isInsideMap(BlockPoint point) {
        BlockPoint corner1 = arena.getCorner1();
        BlockPoint corner2 = arena.getCorner2();
        if (corner1 == null || corner2 == null) {
            return true;
        }
        int minX = Math.min(corner1.x(), corner2.x());
        int maxX = Math.max(corner1.x(), corner2.x());
        int minY = Math.min(corner1.y(), corner2.y());
        int maxY = Math.max(corner1.y(), corner2.y());
        int minZ = Math.min(corner1.z(), corner2.z());
        int maxZ = Math.max(corner1.z(), corner2.z());
        return point.x() >= minX && point.x() <= maxX
                && point.y() >= minY && point.y() <= maxY
                && point.z() >= minZ && point.z() <= maxZ;
    }

    public void recordPlacedBlock(BlockPoint point) {
        recordPlacedBlock(point, null);
    }

    public void recordPlacedBlock(BlockPoint point, ItemStack item) {
        if (point == null) {
            return;
        }
        placedBlocks.add(point);
        if (item == null || item.getType() == Material.AIR) {
            placedBlockItems.remove(point);
            return;
        }
        ItemStack stored = item.clone();
        stored.setAmount(1);
        placedBlockItems.put(point, stored);
    }

    public void removePlacedBlock(BlockPoint point) {
        placedBlocks.remove(point);
        placedBlockItems.remove(point);
    }

    public ItemStack removePlacedBlockItem(BlockPoint point) {
        placedBlocks.remove(point);
        return placedBlockItems.remove(point);
    }

    public boolean isPendingRespawn(UUID playerId) {
        return pendingRespawns.contains(playerId);
    }

    public boolean isEliminated(UUID playerId) {
        return eliminatedPlayers.contains(playerId);
    }

    public void openFakeEnderChest(Player player) {
        Inventory inventory = getFakeEnderChest(player);
        player.openInventory(inventory);
    }

    public Inventory getFakeEnderChest(Player player) {
        return fakeEnderChests.computeIfAbsent(player.getUniqueId(),
                id -> Bukkit.createInventory(player, 27, Component.text("Ender Chest")));
    }

    public void openShop(Player player, ShopType type) {
        if (!isActive() || !isParticipant(player.getUniqueId()) || !isInArenaWorld(player.getWorld())) {
            return;
        }
        if (type == ShopType.UPGRADES) {
            new UpgradeShopMenu(this, player).open(player);
            return;
        }
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            player.sendMessage(Component.text("Shop config is not loaded.", NamedTextColor.RED));
            return;
        }
        new ShopMenu(this, config, ShopCategoryType.QUICK_BUY, player).open(player);
    }

    public void openTeamPickMenu(Player player) {
        if (player == null) {
            return;
        }
        if (!isLobby() || !teamPickEnabled || !isInArenaWorld(player.getWorld())) {
            return;
        }
        new TeamPickMenu(this, player).open(player);
    }

    public boolean handleShopPurchase(Player player, ShopItemDefinition item) {
        if (!isActive() || !isParticipant(player.getUniqueId()) || !isInArenaWorld(player.getWorld())) {
            return false;
        }
        if (item != null && isShopItemBlockedByMatchEvent(item)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            player.sendMessage(Component.text("This item is disabled by the current match event.", NamedTextColor.RED));
            return false;
        }
        if (!isRotatingItemAvailable(item)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            player.sendMessage(Component.text("This rotating item is not available in this match.", NamedTextColor.RED));
            return false;
        }
        if (item.getBehavior() == ShopItemBehavior.UPGRADE) {
            return handleUpgradePurchaseInternal(player, item.getUpgradeType());
        }
        ShopCost cost = item.getCost();
        if (cost == null || !cost.isValid() || !hasResources(player, cost)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        if (!canPurchaseLimitedItem(player, item)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            player.sendMessage(Component.text("Purchase limit reached.", NamedTextColor.RED));
            return false;
        }
        if (!applyPurchase(player, item)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        removeResources(player, cost);
        recordLimitedPurchase(player, item);
        if (item.getBehavior() == ShopItemBehavior.SHIELD) {
            equipShieldOffhand(player, getTeam(player.getUniqueId()));
            player.updateInventory();
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        return true;
    }

    private boolean isShopItemBlockedByMatchEvent(ShopItemDefinition item) {
        if (item == null || activeMatchEvent != BedwarsMatchEventType.IN_THIS_ECONOMY) {
            return false;
        }
        return IN_THIS_ECONOMY_BANNED_ITEMS.contains(item.getId());
    }

    public boolean handleUpgradePurchase(Player player, TeamUpgradeType type) {
        return handleUpgradePurchaseInternal(player, type);
    }

    private boolean handleUpgradePurchaseInternal(Player player, TeamUpgradeType type) {
        if (player == null || type == null) {
            return false;
        }
        if (!isActive() || !isParticipant(player.getUniqueId()) || !isInArenaWorld(player.getWorld())) {
            return false;
        }
        if (!isRotatingUpgradeAvailable(type)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            player.sendMessage(Component.text("This rotating upgrade is not available in this match.", NamedTextColor.RED));
            return false;
        }
        TeamColor team = getTeam(player.getUniqueId());
        if (team == null) {
            return false;
        }
        TeamUpgradeState state = getUpgradeState(team);
        int currentTier = state.getTier(type);
        int cost = type.nextCost(currentTier);
        if (cost < 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        ShopCost shopCost = new ShopCost(Material.DIAMOND, cost);
        if (!hasResources(player, shopCost)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        int nextTier = currentTier + 1;
        state.setTier(type, nextTier);
        removeResources(player, shopCost);
        if (type == TeamUpgradeType.FORGE && generatorManager != null) {
            generatorManager.setBaseForgeTier(team, nextTier);
        }
        applyTeamUpgradeEffects(team);
        announceTeamUpgrade(team, player, type, nextTier);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        return true;
    }

    public boolean handleTrapPurchase(Player player, TrapType trap) {
        if (!isActive() || !isParticipant(player.getUniqueId()) || !isInArenaWorld(player.getWorld())) {
            return false;
        }
        if (trap == null) {
            return false;
        }
        TeamColor team = getTeam(player.getUniqueId());
        if (team == null) {
            return false;
        }
        TeamUpgradeState state = getUpgradeState(team);
        if (!isRotatingTrapAvailable(trap)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        int cost = getTrapCost(team, trap);
        if (cost < 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        ShopCost shopCost = new ShopCost(Material.DIAMOND, cost);
        if (!hasResources(player, shopCost)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        state.addTrap(trap);
        removeResources(player, shopCost);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        return true;
    }

    private void applyCustomEntityStats(LivingEntity entity, CustomItemDefinition custom) {
        if (entity == null || custom == null) {
            return;
        }
        double health = custom.getHealth();
        if (health > 0.0) {
            entity.setMaxHealth(health);
            entity.setHealth(Math.min(health, entity.getMaxHealth()));
        }
        double speed = custom.getSpeed();
        if (speed > 0.0) {
            applyAttribute(entity, "GENERIC_MOVEMENT_SPEED", speed);
            applyAttribute(entity, "GENERIC_FLYING_SPEED", speed);
        }
        double range = custom.getRange();
        if (range > 0.0) {
            applyAttribute(entity, "GENERIC_FOLLOW_RANGE", range);
        }
    }

    private void applyAttribute(LivingEntity entity, String attributeName, double value) {
        setAttributeBaseValue(entity, attributeName, value);
    }

    private boolean canPurchaseLimitedItem(Player player, ShopItemDefinition item) {
        ShopItemLimit limit = item.getLimit();
        if (limit == null || !limit.isValid()) {
            return true;
        }
        ShopItemLimitScope scope = limit.scope();
        String itemId = item.getId();
        if (scope == ShopItemLimitScope.PLAYER) {
            Map<String, Integer> counts = playerPurchaseCounts.get(player.getUniqueId());
            int current = counts != null ? counts.getOrDefault(itemId, 0) : 0;
            return current < limit.amount();
        }
        if (scope == ShopItemLimitScope.TEAM) {
            TeamColor team = getTeam(player.getUniqueId());
            if (team == null) {
                return false;
            }
            Map<String, Integer> counts = teamPurchaseCounts.get(team);
            int current = counts != null ? counts.getOrDefault(itemId, 0) : 0;
            return current < limit.amount();
        }
        return true;
    }

    private void recordLimitedPurchase(Player player, ShopItemDefinition item) {
        ShopItemLimit limit = item.getLimit();
        if (limit == null || !limit.isValid()) {
            return;
        }
        ShopItemLimitScope scope = limit.scope();
        String itemId = item.getId();
        if (scope == ShopItemLimitScope.PLAYER) {
            playerPurchaseCounts
                    .computeIfAbsent(player.getUniqueId(), key -> new HashMap<>())
                    .merge(itemId, 1, Integer::sum);
            return;
        }
        if (scope == ShopItemLimitScope.TEAM) {
            TeamColor team = getTeam(player.getUniqueId());
            if (team == null) {
                return;
            }
            teamPurchaseCounts
                    .computeIfAbsent(team, key -> new HashMap<>())
                    .merge(itemId, 1, Integer::sum);
        }
    }

    public int getUpgradeTier(TeamColor team, TeamUpgradeType type) {
        if (team == null) {
            return 0;
        }
        return getUpgradeState(team).getTier(type);
    }

    public int getTrapCost(TeamColor team, TrapType trap) {
        if (team == null || trap == null) {
            return -1;
        }
        TeamUpgradeState state = getUpgradeState(team);
        int count = state.getTrapCount();
        if (count >= TRAP_MAX_COUNT) {
            return -1;
        }
        if (trap.oneTimePurchase() && state.hasPurchasedTrap(trap)) {
            return -1;
        }
        int baseCost = trap.baseCost(getMaxTeamSize());
        return baseCost * (1 << count);
    }

    public List<TrapType> getActiveTraps(TeamColor team) {
        if (team == null) {
            return List.of();
        }
        return getUpgradeState(team).getTraps();
    }

    public boolean hasPurchasedTrap(TeamColor team, TrapType trap) {
        if (team == null || trap == null) {
            return false;
        }
        return getUpgradeState(team).hasPurchasedTrap(trap);
    }

    public void grantTrapImmunity(UUID playerId, int seconds) {
        if (playerId == null) {
            return;
        }
        clearTrapImmunity(playerId);
        if (seconds <= 0) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + seconds * 1000L;
        trapImmunityEnds.put(playerId, expiresAt);
        if (plugin == null) {
            return;
        }
        long delayTicks = Math.max(1L, seconds * 20L);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                safeRun("trapImmunityExpire", () -> {
                    Long end = trapImmunityEnds.get(playerId);
                    if (end == null || end > System.currentTimeMillis()) {
                        return;
                    }
                    trapImmunityEnds.remove(playerId);
                    trapImmunityTasks.remove(playerId);
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        player.sendMessage(Component.text("Magic Milk expired. Traps can trigger again.", NamedTextColor.RED));
                    }
                }), delayTicks);
        trapImmunityTasks.put(playerId, task);
    }

    public void clearTrapImmunity(UUID playerId) {
        if (playerId == null) {
            return;
        }
        trapImmunityEnds.remove(playerId);
        BukkitTask task = trapImmunityTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    public boolean hasTrapImmunity(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        Long expiresAt = trapImmunityEnds.get(playerId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt <= System.currentTimeMillis()) {
            trapImmunityEnds.remove(playerId);
            return false;
        }
        return true;
    }

    private boolean applyPurchase(Player player, ShopItemDefinition item) {
        TeamColor team = getTeam(player.getUniqueId());
        ShopItemBehavior behavior = item.getBehavior();
        CustomItemDefinition custom = resolveCustomPurchaseDefinition(item);
        return switch (behavior) {
            case BLOCK, UTILITY, POTION -> {
                giveItem(player, createPurchaseItem(item, team));
                if (behavior == ShopItemBehavior.UTILITY
                        && team != null
                        && hasTeamUpgrade(team, TeamUpgradeType.FIRE_ASPECT)
                        && ATTACK_MATERIALS.contains(item.getMaterial())) {
                    applyFireAspect(player);
                }
                yield true;
            }
            case SWORD -> {
                if (item.getMaterial() != Material.WOODEN_SWORD) {
                    removeItems(player, WOODEN_SWORD_ONLY);
                }
                giveItem(player, createPurchaseItem(item, team));
                if (team != null && hasTeamUpgrade(team, TeamUpgradeType.SHARPNESS)) {
                    applySharpness(player);
                }
                if (team != null && hasTeamUpgrade(team, TeamUpgradeType.FIRE_ASPECT)) {
                    applyFireAspect(player);
                }
                yield true;
            }
            case BOW -> {
                removeItems(player, BOW_MATERIALS);
                giveItem(player, createPurchaseItem(item, team));
                yield true;
            }
            case CROSSBOW -> {
                if (countItem(player, Material.CROSSBOW) >= 3) {
                    yield false;
                }
                giveItem(player, createPurchaseItem(item, team));
                yield true;
            }
            case ARMOR -> applyTierUpgrade(player, item, armorTiers, ShopItemBehavior.ARMOR);
            case PICKAXE -> applyTierUpgrade(player, item, pickaxeTiers, ShopItemBehavior.PICKAXE);
            case AXE -> applyTierUpgrade(player, item, axeTiers, ShopItemBehavior.AXE);
            case SHEARS -> applyShears(player, item, team);
            case SHIELD -> applyShield(player);
            case UPGRADE -> handleUpgradePurchaseInternal(player, item.getUpgradeType());
        };
    }

    private CustomItemDefinition resolveCustomPurchaseDefinition(ShopItemDefinition item) {
        if (item == null) {
            return null;
        }
        String customId = item.getCustomItemId();
        if (customId == null || customId.isBlank()) {
            return null;
        }
        return bedwarsManager.getCustomItemConfig().getItem(customId);
    }

    private ItemStack createPurchaseItem(ShopItemDefinition item, TeamColor team) {
        ItemStack stack = createEventAdjustedPurchaseItem(item, team);
        applyCustomItemMetadata(stack, item);
        return stack;
    }

    private ItemStack createEventAdjustedPurchaseItem(ShopItemDefinition item, TeamColor team) {
        if (item == null) {
            return null;
        }
        if (activeMatchEvent == BedwarsMatchEventType.APRIL_FOOLS
                && "bedrock".equalsIgnoreCase(item.getId())) {
            ItemStack stack = new ItemStack(Material.BARRIER, Math.max(1, item.getAmount()));
            ItemMeta meta = stack.getItemMeta();
            meta.displayName(Component.text("Barrier", NamedTextColor.RED));
            meta.lore(List.of(
                    Component.text("April Fools.", NamedTextColor.RED),
                    Component.text("The bedrock was fake.", NamedTextColor.GRAY)
            ));
            stack.setItemMeta(meta);
            return stack;
        }
        return item.createPurchaseItem(team);
    }

    private void applyCustomItemMetadata(ItemStack item, ShopItemDefinition definition) {
        if (item == null || definition == null) {
            return;
        }
        String customId = definition.getCustomItemId();
        if (customId == null || customId.isBlank()) {
            return;
        }
        CustomItemDefinition custom = bedwarsManager.getCustomItemConfig().getItem(customId);
        if (custom == null) {
            return;
        }
        if (custom.getUses() > 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return;
            }
            CustomItemData.setUses(meta, custom.getUses());
            updateUsesLore(meta, custom.getUses());
            item.setItemMeta(meta);
        }
    }

    private void updateUsesLore(ItemMeta meta, int uses) {
        if (meta == null) {
            return;
        }
        List<Component> lore = meta.lore();
        if (lore == null) {
            lore = new ArrayList<>();
        } else {
            lore = new ArrayList<>(lore);
        }
        Component line = Component.text("Uses: " + uses, NamedTextColor.GRAY);
        boolean replaced = false;
        for (int i = 0; i < lore.size(); i++) {
            Component existing = lore.get(i);
            if (existing == null) {
                continue;
            }
            String plain = PlainTextComponentSerializer.plainText().serialize(existing);
            if (plain != null && plain.toLowerCase(Locale.ROOT).startsWith("uses:")) {
                lore.set(i, line);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            lore.add(line);
        }
        meta.lore(lore);
    }

    public boolean activateElytraStrike(Player player, CustomItemDefinition custom) {
        if (player == null || custom == null || !isRunning() || !isParticipant(player.getUniqueId())) {
            return false;
        }
        if (activeElytraStrikes.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("You are already in an airstrike.", NamedTextColor.RED));
            return false;
        }
        TeamColor team = getTeam(player.getUniqueId());
        if (team == null) {
            return false;
        }
        Location spawn = arena.getSpawn(team);
        if (spawn == null || spawn.getWorld() == null) {
            return false;
        }
        ItemStack previousChestplate = player.getInventory().getChestplate();
        activeElytraStrikes.put(player.getUniqueId(),
                new ElytraStrikeState(previousChestplate != null ? previousChestplate.clone() : null,
                        player.getAllowFlight(),
                        player.isFlying()));

        player.getInventory().setChestplate(createActiveElytraChestplate());
        player.setAllowFlight(true);
        player.setFlying(false);

        Location target = spawn.clone();
        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());
        double maxY = spawn.getWorld().getMaxHeight() - 5.0;
        target.setY(Math.max(spawn.getY(), Math.min(maxY, spawn.getY() + ELYTRA_STRIKE_ALTITUDE)));
        if (!player.teleport(target)) {
            clearElytraStrike(player, true, false);
            return false;
        }

        Vector launchVelocity = target.getDirection().normalize().multiply(1.35);
        if (launchVelocity.lengthSquared() <= 0.0001) {
            launchVelocity = new Vector(0.0, -0.35, 0.0);
        } else if (launchVelocity.getY() > -0.25) {
            launchVelocity.setY(-0.25);
        }
        Vector finalLaunchVelocity = launchVelocity;
        UUID playerId = player.getUniqueId();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(playerId);
            if (online == null || !online.isOnline() || !activeElytraStrikes.containsKey(playerId)) {
                return;
            }
            online.setGliding(true);
            online.setVelocity(finalLaunchVelocity);
        });
        broadcast(Component.text("airstrike incoming!", NamedTextColor.RED));
        return true;
    }

    public boolean activateUnstableTeleportationDevice(Player player, CustomItemDefinition custom) {
        if (player == null
                || custom == null
                || !isRunning()
                || !isParticipant(player.getUniqueId())
                || !isInArenaWorld(player.getWorld())) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        long remainingMillis = getCustomItemCooldownRemainingMillis(playerId, custom.getId());
        if (remainingMillis > 0L) {
            long secondsRemaining = Math.max(1L, (remainingMillis + 999L) / 1000L);
            player.sendMessage(Component.text(
                    "Unstable Teleportation Device is on cooldown for " + secondsRemaining + "s.",
                    NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        UnstableTeleportResult result = rollUnstableTeleportationDestination(player);
        if (result == null || result.destination() == null) {
            player.sendMessage(Component.text("The device fizzled. No safe destination was found.", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        Location origin = player.getLocation().clone();
        Location destination = result.destination().clone();
        destination.setYaw(origin.getYaw());
        destination.setPitch(origin.getPitch());
        if (!player.teleport(destination)) {
            player.sendMessage(Component.text("The device fizzled. Teleport failed.", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        startCustomItemCooldown(playerId, custom.getId(), custom.getCooldownSeconds());
        playUnstableTeleportEffects(origin);
        playUnstableTeleportEffects(destination);
        player.sendMessage(result.message());
        return true;
    }

    public boolean activateMiracleOfTheStars(Player player, CustomItemDefinition custom) {
        if (player == null
                || custom == null
                || plugin == null
                || !isRunning()
                || !isParticipant(player.getUniqueId())
                || !isInArenaWorld(player.getWorld())) {
            return false;
        }
        if (suddenDeathActive) {
            player.sendMessage(Component.text("Miracle of the Stars is disabled after sudden death.", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        TeamColor team = getTeam(player.getUniqueId());
        if (team == null) {
            return false;
        }
        Location baseSpawn = arena.getSpawn(team);
        if (baseSpawn == null) {
            player.sendMessage(Component.text("The stars could not find your base.", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        Component primedMessage = Component.text("Miracle of the Stars activates in ", NamedTextColor.AQUA)
                .append(Component.text("5 seconds", NamedTextColor.YELLOW))
                .append(Component.text(". Your team will be recalled to base.", NamedTextColor.AQUA));
        for (UUID playerId : assignments.keySet()) {
            if (!isMiracleOfTheStarsTarget(playerId, team)) {
                continue;
            }
            Player teammate = Bukkit.getPlayer(playerId);
            if (teammate == null) {
                continue;
            }
            teammate.sendMessage(primedMessage);
            teammate.playSound(teammate.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.3f);
        }

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                safeRun("miracleOfTheStars", () -> resolveMiracleOfTheStars(team)),
                MIRACLE_OF_THE_STARS_DELAY_TICKS);
        tasks.add(task);
        return true;
    }

    public void handleElytraStrikeMovement(Player player) {
        if (player == null) {
            return;
        }
        if (!activeElytraStrikes.containsKey(player.getUniqueId())) {
            return;
        }
        if (!isRunning()
                || !isParticipant(player.getUniqueId())
                || !isInArenaWorld(player.getWorld())
                || player.getGameMode() == GameMode.SPECTATOR) {
            clearElytraStrike(player, false, false);
            return;
        }
        if (player.isOnGround()) {
            clearElytraStrike(player, true, true);
        }
    }

    public boolean isActiveElytraStrikeItem(ItemStack item) {
        return ELYTRA_STRIKE_ACTIVE_ITEM_ID.equalsIgnoreCase(CustomItemData.getId(item));
    }

    private UnstableTeleportResult rollUnstableTeleportationDestination(Player player) {
        TeamColor team = getTeam(player.getUniqueId());
        if (team == null) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(4);
        return switch (roll) {
            case 0 -> {
                Location base = sanitizeUnstableTeleportDestination(arena.getSpawn(team));
                yield base == null
                        ? null
                        : new UnstableTeleportResult(
                        base,
                        Component.text("The device snapped you back to your base.", NamedTextColor.AQUA));
            }
            case 1 -> {
                BlockPoint center = arena.getCenter();
                Location centerLocation = center == null ? null : findSafeArenaTeleportLocation(center.x(), center.z());
                yield centerLocation == null
                        ? null
                        : new UnstableTeleportResult(
                        centerLocation,
                        Component.text("The device dropped you at the exact center of the map.", NamedTextColor.LIGHT_PURPLE));
            }
            case 2 -> {
                Location randomLocation = findRandomSafeArenaLocation();
                yield randomLocation == null
                        ? null
                        : new UnstableTeleportResult(
                        randomLocation,
                        Component.text("The device scattered you to a random location.", NamedTextColor.YELLOW));
            }
            default -> {
                UnstableTeleportResult baseResult = findRandomBaseTeleportResult(team);
                yield baseResult;
            }
        };
    }

    private UnstableTeleportResult findRandomBaseTeleportResult(TeamColor playerTeam) {
        List<TeamColor> candidates = new ArrayList<>(teamsInMatch.isEmpty() ? arena.getTeams() : teamsInMatch);
        if (candidates.size() > 1) {
            candidates.remove(playerTeam);
        }
        Collections.shuffle(candidates);
        for (TeamColor targetTeam : candidates) {
            Location spawn = sanitizeUnstableTeleportDestination(arena.getSpawn(targetTeam));
            if (spawn == null) {
                continue;
            }
            Component message = Component.text("The device hurled you to the ",
                            NamedTextColor.YELLOW)
                    .append(targetTeam.displayComponent())
                    .append(Component.text(" base.", NamedTextColor.YELLOW));
            return new UnstableTeleportResult(spawn, message);
        }
        Location fallback = sanitizeUnstableTeleportDestination(arena.getSpawn(playerTeam));
        return fallback == null
                ? null
                : new UnstableTeleportResult(
                fallback,
                Component.text("The device snapped you back to your base.", NamedTextColor.AQUA));
    }

    private Location findRandomSafeArenaLocation() {
        BlockPoint corner1 = arena.getCorner1();
        BlockPoint corner2 = arena.getCorner2();
        if (corner1 == null || corner2 == null) {
            return null;
        }
        int minX = Math.min(corner1.x(), corner2.x());
        int maxX = Math.max(corner1.x(), corner2.x());
        int minZ = Math.min(corner1.z(), corner2.z());
        int maxZ = Math.max(corner1.z(), corner2.z());
        for (int attempt = 0; attempt < 64; attempt++) {
            int x = ThreadLocalRandom.current().nextInt(minX, maxX + 1);
            int z = ThreadLocalRandom.current().nextInt(minZ, maxZ + 1);
            Location safe = findSafeArenaTeleportLocation(x, z);
            if (safe != null) {
                return safe;
            }
        }
        return null;
    }

    private Location findSafeArenaTeleportLocation(int x, int z) {
        World world = arena.getWorld();
        if (world == null) {
            return null;
        }
        BlockPoint corner1 = arena.getCorner1();
        BlockPoint corner2 = arena.getCorner2();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        if (corner1 != null && corner2 != null) {
            minY = Math.max(minY, Math.min(corner1.y(), corner2.y()));
            maxY = Math.min(maxY, Math.max(corner1.y(), corner2.y()));
        }
        if (maxY < minY) {
            return null;
        }

        Block highest = world.getHighestBlockAt(x, z);
        if (highest != null && highest.getY() >= minY && highest.getY() <= maxY) {
            Location highestLocation = buildSafeTeleportLocation(highest);
            if (highestLocation != null) {
                return highestLocation;
            }
        }

        int startY = highest != null ? Math.min(highest.getY(), maxY) : maxY;
        for (int y = startY; y >= minY; y--) {
            Location candidate = buildSafeTeleportLocation(world.getBlockAt(x, y, z));
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private Location buildSafeTeleportLocation(Block floor) {
        if (floor == null || !isSafeTeleportFloor(floor)) {
            return null;
        }
        Block feet = floor.getRelative(BlockFace.UP);
        Block head = feet.getRelative(BlockFace.UP);
        if (!isSafeTeleportSpace(feet) || !isSafeTeleportSpace(head)) {
            return null;
        }
        Location location = feet.getLocation().add(0.5, 0.0, 0.5);
        return isInsideMapTeleportLocation(location) ? location : null;
    }

    private Location sanitizeUnstableTeleportDestination(Location location) {
        if (!isInsideMapTeleportLocation(location)) {
            return null;
        }
        return location;
    }

    private boolean isInsideMapTeleportLocation(Location location) {
        if (location == null || location.getWorld() == null || !isInArenaWorld(location.getWorld())) {
            return false;
        }
        BlockPoint feet = new BlockPoint(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        BlockPoint head = new BlockPoint(location.getBlockX(), location.getBlockY() + 1, location.getBlockZ());
        return isInsideMap(feet) && isInsideMap(head);
    }

    private boolean isSafeTeleportFloor(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        if (!type.isSolid()) {
            return false;
        }
        return switch (type) {
            case LAVA,
                    WATER,
                    FIRE,
                    SOUL_FIRE,
                    CACTUS,
                    MAGMA_BLOCK,
                    CAMPFIRE,
                    SOUL_CAMPFIRE,
                    POWDER_SNOW,
                    END_PORTAL -> false;
            default -> true;
        };
    }

    private boolean isSafeTeleportSpace(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        if (type == Material.WATER || type == Material.LAVA) {
            return false;
        }
        return block.isPassable();
    }

    private void playUnstableTeleportEffects(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        location.getWorld().spawnParticle(Particle.PORTAL,
                location.clone().add(0.0, 1.0, 0.0),
                40,
                0.45,
                0.8,
                0.45,
                0.0);
        location.getWorld().playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    private void resolveMiracleOfTheStars(TeamColor team) {
        if (team == null || state != GameState.RUNNING) {
            return;
        }
        if (suddenDeathActive) {
            Component cancelled = Component.text("Miracle of the Stars faded when sudden death began.", NamedTextColor.RED);
            for (UUID playerId : assignments.keySet()) {
                if (assignments.get(playerId) != team) {
                    continue;
                }
                Player teammate = Bukkit.getPlayer(playerId);
                if (teammate == null) {
                    continue;
                }
                teammate.sendMessage(cancelled);
                teammate.playSound(teammate.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return;
        }
        Location baseSpawn = arena.getSpawn(team);
        if (baseSpawn == null) {
            return;
        }

        Component recallMessage = Component.text("Miracle of the Stars carried your team back to base.", NamedTextColor.GOLD);
        for (UUID playerId : assignments.keySet()) {
            if (!isMiracleOfTheStarsTarget(playerId, team)) {
                continue;
            }
            Player teammate = Bukkit.getPlayer(playerId);
            if (teammate == null) {
                continue;
            }
            Location source = teammate.getLocation().clone();
            Location destination = baseSpawn.clone();
            if (!teammate.teleport(destination)) {
                continue;
            }
            teammate.setFallDistance(0.0f);
            playMiracleOfTheStarsEffects(source);
            playMiracleOfTheStarsEffects(destination);
            teammate.sendMessage(recallMessage);
        }
    }

    private boolean isMiracleOfTheStarsTarget(UUID playerId, TeamColor team) {
        if (playerId == null || team == null || assignments.get(playerId) != team) {
            return false;
        }
        if (eliminatedPlayers.contains(playerId) || pendingRespawns.contains(playerId)) {
            return false;
        }
        Player player = Bukkit.getPlayer(playerId);
        return player != null
                && player.isOnline()
                && isInArenaWorld(player.getWorld())
                && player.getGameMode() != GameMode.SPECTATOR;
    }

    private void playMiracleOfTheStarsEffects(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        World world = location.getWorld();
        Location center = location.clone().add(0.0, 1.0, 0.0);
        world.spawnParticle(Particle.END_ROD, center, 30, 0.45, 0.75, 0.45, 0.0);
        world.spawnParticle(Particle.PORTAL, center, 24, 0.35, 0.65, 0.35, 0.0);
        world.playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.25f);
    }

    private long getCustomItemCooldownRemainingMillis(UUID playerId, String itemId) {
        String normalized = normalizeItemId(itemId);
        if (playerId == null || normalized == null) {
            return 0L;
        }
        Map<String, Long> cooldowns = customItemCooldownEnds.get(playerId);
        if (cooldowns == null) {
            return 0L;
        }
        Long expiresAt = cooldowns.get(normalized);
        if (expiresAt == null) {
            return 0L;
        }
        long remaining = expiresAt - System.currentTimeMillis();
        if (remaining > 0L) {
            return remaining;
        }
        cooldowns.remove(normalized);
        if (cooldowns.isEmpty()) {
            customItemCooldownEnds.remove(playerId);
        }
        return 0L;
    }

    private void startCustomItemCooldown(UUID playerId, String itemId, int cooldownSeconds) {
        String normalized = normalizeItemId(itemId);
        if (playerId == null || normalized == null || cooldownSeconds <= 0) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + cooldownSeconds * 1000L;
        customItemCooldownEnds
                .computeIfAbsent(playerId, key -> new HashMap<>())
                .put(normalized, expiresAt);
    }

    private void clearAllElytraStrikes(boolean restoreChestplate) {
        for (UUID playerId : new ArrayList<>(activeElytraStrikes.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                activeElytraStrikes.remove(playerId);
                continue;
            }
            clearElytraStrike(player, restoreChestplate, false);
        }
    }

    private void clearElytraStrike(Player player, boolean restoreChestplate, boolean grantLandingRegen) {
        if (player == null) {
            return;
        }
        ElytraStrikeState state = activeElytraStrikes.remove(player.getUniqueId());
        removeItemsByCustomId(player, ELYTRA_STRIKE_ACTIVE_ITEM_ID);
        if (isActiveElytraStrikeItem(player.getInventory().getChestplate())) {
            player.getInventory().setChestplate(null);
        }
        if (restoreChestplate && state != null) {
            player.getInventory().setChestplate(state.previousChestplate() != null
                    ? state.previousChestplate().clone()
                    : null);
        }
        if (state != null && player.getGameMode() != GameMode.SPECTATOR) {
            player.setAllowFlight(state.previousAllowFlight());
            player.setFlying(state.previousFlying());
        }
        player.setGliding(false);
        if (grantLandingRegen
                && isRunning()
                && isParticipant(player.getUniqueId())
                && isInArenaWorld(player.getWorld())
                && player.getGameMode() != GameMode.SPECTATOR) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                    ELYTRA_STRIKE_REGEN_DURATION_TICKS,
                    ELYTRA_STRIKE_REGEN_AMPLIFIER,
                    true,
                    false,
                    true));
        }
        player.updateInventory();
    }

    private ItemStack createActiveElytraChestplate() {
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta meta = elytra.getItemMeta();
        meta.displayName(Component.text("Airstrike Elytra", NamedTextColor.AQUA));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        CustomItemData.apply(meta, ELYTRA_STRIKE_ACTIVE_ITEM_ID);
        elytra.setItemMeta(meta);
        return elytra;
    }

    private void removeItemsByCustomId(Player player, String customId) {
        if (player == null || customId == null || customId.isBlank()) {
            return;
        }
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (customId.equalsIgnoreCase(CustomItemData.getId(item))) {
                contents[i] = null;
            }
        }
        player.getInventory().setStorageContents(contents);
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (customId.equalsIgnoreCase(CustomItemData.getId(offhand))) {
            player.getInventory().setItemInOffHand(null);
        }
    }

    public boolean deployAbyssalRift(Player player, CustomItemDefinition custom, Block clickedBlock) {
        if (player == null
                || custom == null
                || clickedBlock == null
                || !isRunning()
                || !isParticipant(player.getUniqueId())) {
            return false;
        }
        TeamColor team = getTeam(player.getUniqueId());
        if (team == null) {
            return false;
        }
        Block firstAir = clickedBlock.getRelative(org.bukkit.block.BlockFace.UP);
        Block secondAir = firstAir.getRelative(org.bukkit.block.BlockFace.UP);
        if (!firstAir.getType().isAir() || !secondAir.getType().isAir()) {
            player.sendMessage(Component.text("You cannot place an abyssal rift here.", NamedTextColor.RED));
            return false;
        }
        BlockPoint firstPoint = new BlockPoint(firstAir.getX(), firstAir.getY(), firstAir.getZ());
        BlockPoint secondPoint = new BlockPoint(secondAir.getX(), secondAir.getY(), secondAir.getZ());
        if (!isInsideMap(firstPoint) || !isInsideMap(secondPoint)) {
            player.sendMessage(Component.text("You cannot place an abyssal rift outside the map.", NamedTextColor.RED));
            return false;
        }
        if (isPlacementBlocked(firstPoint) || isPlacementBlocked(secondPoint)) {
            player.sendMessage(Component.text("You cannot place an abyssal rift here.", NamedTextColor.RED));
            return false;
        }
        Location base = clickedBlock.getLocation().add(0.5, 1.0, 0.5);
        World world = base.getWorld();
        if (world == null) {
            return false;
        }
        Interaction interaction = world.spawn(base, Interaction.class, entity -> {
            entity.setPersistent(false);
            entity.setInvulnerable(false);
            entity.setGravity(false);
            entity.setInteractionWidth(ABYSSAL_RIFT_HITBOX_WIDTH);
            entity.setInteractionHeight(ABYSSAL_RIFT_HITBOX_HEIGHT);
            entity.setResponsive(true);
            entity.addScoreboardTag(ABYSSAL_RIFT_TAG);
        });
        ItemDisplay display = world.spawn(base, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setInvulnerable(false);
            entity.setGravity(false);
            entity.addScoreboardTag(ABYSSAL_RIFT_DISPLAY_TAG);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setShadowRadius(0.0f);
            entity.setShadowStrength(0.0f);
            entity.setDisplayWidth(1.0f);
            entity.setDisplayHeight(2.0f);
            entity.setTransformation(new Transformation(
                    new Vector3f(0.0f, (float) ABYSSAL_RIFT_DISPLAY_Y_OFFSET, 0.0f),
                    new Quaternionf(),
                    new Vector3f(1.0f, 1.0f, 1.0f),
                    new Quaternionf()));
            entity.setItemStack(createAbyssalRiftDisplayItem(custom));
        });
        double health = custom.getHealth() > 0.0 ? custom.getHealth() : 30.0;
        double radius = custom.getRange() > 0.0 ? custom.getRange() : 10.0;
        ArmorStand titleStand = spawnAbyssalRiftNameStand(base.clone().add(0.0, ABYSSAL_RIFT_NAME_Y_OFFSET, 0.0));
        ArmorStand healthStand = spawnAbyssalRiftNameStand(base.clone().add(0.0, ABYSSAL_RIFT_HEALTH_Y_OFFSET, 0.0));
        AbyssalRiftState state = new AbyssalRiftState(interaction.getUniqueId(),
                display.getUniqueId(),
                titleStand != null ? titleStand.getUniqueId() : null,
                healthStand != null ? healthStand.getUniqueId() : null,
                team,
                health,
                health,
                radius);
        abyssalRifts.put(interaction.getUniqueId(), state);
        abyssalRiftEntityLinks.put(interaction.getUniqueId(), interaction.getUniqueId());
        abyssalRiftEntityLinks.put(display.getUniqueId(), interaction.getUniqueId());
        updateAbyssalRiftNameplate(state);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                tickAbyssalRift(interaction.getUniqueId());
            }
        }.runTaskTimer(plugin, 0L, ABYSSAL_RIFT_AURA_INTERVAL_TICKS);
        state.setAuraTask(task);
        player.playSound(base, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.8f, 1.4f);
        return true;
    }

    public boolean deployTowerChest(Player player, Block clickedBlock, BlockFace clickedFace) {
        if (player == null
                || clickedBlock == null
                || clickedFace != BlockFace.UP
                || !isRunning()
                || !isParticipant(player.getUniqueId())) {
            return false;
        }
        TeamColor team = getTeam(player.getUniqueId());
        if (team == null) {
            return false;
        }
        Block origin = clickedBlock.getRelative(BlockFace.UP);
        World world = origin.getWorld();
        if (world == null) {
            return false;
        }
        BlockFace forward = resolveTowerChestForward(player).getOppositeFace();
        BlockFace right = rotateClockwise(forward);
        boolean outsideMap = false;
        for (int layer = 0; layer < TOWER_CHEST_LAYERS.length; layer++) {
            String[] rows = TOWER_CHEST_LAYERS[layer];
            for (int row = 0; row < rows.length; row++) {
                String pattern = rows[row];
                for (int column = 0; column < pattern.length(); column++) {
                    char cell = pattern.charAt(column);
                    if (cell == '0') {
                        continue;
                    }
                    Block target = resolveTowerChestBlock(origin, forward, right, layer, row, column);
                    BlockPoint point = new BlockPoint(target.getX(), target.getY(), target.getZ());
                    if (!isInsideMap(point)) {
                        outsideMap = true;
                        continue;
                    }
                    if (isPlacementBlocked(point) || !target.getType().isAir()) {
                        player.sendMessage(Component.text("You cannot place a tower chest here.", NamedTextColor.RED));
                        return false;
                    }
                }
            }
        }
        if (outsideMap) {
            player.sendMessage(Component.text("You cannot place a tower chest outside the map.", NamedTextColor.RED));
            return false;
        }
        placeTemporaryTowerChest(origin, forward);
        ItemStack woolDrop = new ItemStack(team.wool());
        ItemStack ladderDrop = new ItemStack(Material.LADDER);
        for (int layer = 0; layer < TOWER_CHEST_LAYERS.length; layer++) {
            String[] rows = TOWER_CHEST_LAYERS[layer];
            for (int row = 0; row < rows.length; row++) {
                String pattern = rows[row];
                for (int column = 0; column < pattern.length(); column++) {
                    if (pattern.charAt(column) != 'x') {
                        continue;
                    }
                    Block target = resolveTowerChestBlock(origin, forward, right, layer, row, column);
                    target.setType(team.wool(), false);
                    recordPlacedBlock(new BlockPoint(target.getX(), target.getY(), target.getZ()), woolDrop);
                }
            }
        }
        for (int layer = 0; layer < TOWER_CHEST_LAYERS.length; layer++) {
            String[] rows = TOWER_CHEST_LAYERS[layer];
            for (int row = 0; row < rows.length; row++) {
                String pattern = rows[row];
                for (int column = 0; column < pattern.length(); column++) {
                    if (pattern.charAt(column) != 'L') {
                        continue;
                    }
                    Block target = resolveTowerChestBlock(origin, forward, right, layer, row, column);
                    target.setType(Material.LADDER, false);
                    if (target.getBlockData() instanceof Directional directional) {
                        directional.setFacing(forward);
                        target.setBlockData(directional, false);
                    }
                    recordPlacedBlock(new BlockPoint(target.getX(), target.getY(), target.getZ()), ladderDrop);
                }
            }
        }
        world.playSound(origin.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_WOOD_PLACE, 1.0f, 0.9f);
        return true;
    }

    private void placeTemporaryTowerChest(Block origin, BlockFace forward) {
        if (origin == null) {
            return;
        }
        origin.setType(Material.CHEST, false);
        if (origin.getBlockData() instanceof Directional directional) {
            directional.setFacing(forward);
            origin.setBlockData(directional, false);
        }
        if (plugin == null) {
            origin.setType(Material.AIR, false);
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (origin.getType() == Material.CHEST) {
                origin.setType(Material.AIR, false);
                origin.getWorld().playSound(origin.getLocation().add(0.5, 0.5, 0.5),
                        Sound.BLOCK_WOOD_BREAK,
                        0.8f,
                        1.1f);
            }
        }, TOWER_CHEST_TEMP_CHEST_TICKS);
    }

    private Block resolveTowerChestBlock(Block origin,
                                         BlockFace forward,
                                         BlockFace right,
                                         int layer,
                                         int row,
                                         int column) {
        int localRight = column - 3;
        int localForward = row - 3;
        int x = origin.getX() + right.getModX() * localRight + forward.getModX() * localForward;
        int y = origin.getY() + layer;
        int z = origin.getZ() + right.getModZ() * localRight + forward.getModZ() * localForward;
        return origin.getWorld().getBlockAt(x, y, z);
    }

    private BlockFace resolveTowerChestForward(Player player) {
        if (player == null) {
            return BlockFace.SOUTH;
        }
        Vector direction = player.getLocation().getDirection();
        if (Math.abs(direction.getX()) >= Math.abs(direction.getZ())) {
            return direction.getX() >= 0.0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return direction.getZ() >= 0.0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private BlockFace rotateClockwise(BlockFace face) {
        if (face == null) {
            return BlockFace.WEST;
        }
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> BlockFace.WEST;
        };
    }

    public boolean isAbyssalRiftEntity(Entity entity) {
        return resolveAbyssalRiftId(entity) != null;
    }

    public boolean damageAbyssalRift(Entity entity, Player attacker, double damage) {
        UUID interactionId = resolveAbyssalRiftId(entity);
        if (interactionId == null) {
            return false;
        }
        AbyssalRiftState state = abyssalRifts.get(interactionId);
        if (state == null) {
            return false;
        }
        if (attacker != null) {
            if (!isParticipant(attacker.getUniqueId()) || !isInArenaWorld(attacker.getWorld())) {
                return true;
            }
            TeamColor attackerTeam = getTeam(attacker.getUniqueId());
            if (attackerTeam != null && attackerTeam == state.team()) {
                return true;
            }
        }
        double appliedDamage = Math.max(0.0, damage);
        if (appliedDamage <= 0.0) {
            return true;
        }
        state.damage(appliedDamage);
        Interaction interaction = getAbyssalRiftInteraction(interactionId);
        if (interaction != null && interaction.getWorld() != null) {
            interaction.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,
                    interaction.getLocation().add(0.0, 1.0, 0.0),
                    8,
                    0.25,
                    0.25,
                    0.25,
                    0.0);
            interaction.getWorld().playSound(interaction.getLocation(), Sound.ENTITY_ARMOR_STAND_HIT, 0.7f, 0.8f);
        }
        updateAbyssalRiftNameplate(state);
        if (state.health() <= 0.0) {
            destroyAbyssalRift(interactionId, true);
        }
        return true;
    }

    private UUID resolveAbyssalRiftId(Entity entity) {
        if (entity == null) {
            return null;
        }
        return abyssalRiftEntityLinks.get(entity.getUniqueId());
    }

    private void tickAbyssalRift(UUID interactionId) {
        AbyssalRiftState state = abyssalRifts.get(interactionId);
        if (state == null) {
            return;
        }
        Interaction interaction = getAbyssalRiftInteraction(interactionId);
        ItemDisplay display = getAbyssalRiftDisplay(state.displayId());
        if (!isRunning()
                || interaction == null
                || display == null
                || !interaction.isValid()
                || !display.isValid()) {
            destroyAbyssalRift(interactionId, false);
            return;
        }
        updateAbyssalRiftNameplate(state);
        Location center = interaction.getLocation();
        double radiusSquared = state.radius() * state.radius();
        for (UUID playerId : assignments.keySet()) {
            Player candidate = Bukkit.getPlayer(playerId);
            if (candidate == null
                    || !candidate.isOnline()
                    || !isInArenaWorld(candidate.getWorld())
                    || candidate.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (candidate.getLocation().distanceSquared(center) > radiusSquared) {
                continue;
            }
            TeamColor candidateTeam = getTeam(playerId);
            if (candidateTeam == null) {
                continue;
            }
            if (candidateTeam == state.team()) {
                applyAuraEffect(candidate, PotionEffectType.STRENGTH, 0);
                applyAuraEffect(candidate, PotionEffectType.SPEED, 0);
            } else {
                applyAuraEffect(candidate, PotionEffectType.WEAKNESS, 0);
                applyAuraEffect(candidate, PotionEffectType.SLOWNESS, 0);
            }
        }
    }

    private void applyAuraEffect(Player player, PotionEffectType type, int amplifier) {
        if (player == null || type == null) {
            return;
        }
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && current.getAmplifier() > amplifier) {
            return;
        }
        if (current != null
                && current.getAmplifier() == amplifier
                && current.getDuration() > ABYSSAL_RIFT_EFFECT_DURATION_TICKS / 2) {
            return;
        }
        player.addPotionEffect(new PotionEffect(type,
                ABYSSAL_RIFT_EFFECT_DURATION_TICKS,
                amplifier,
                true,
                false,
                true));
    }

    private ItemStack createAbyssalRiftDisplayItem(CustomItemDefinition custom) {
        ItemStack stack = new ItemStack(custom.getMaterial());
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(ABYSSAL_RIFT_ITEM_MODEL);
        meta.displayName(Component.text("Abyssal Rift", NamedTextColor.DARK_AQUA));
        meta.setHideTooltip(true);
        stack.setItemMeta(meta);
        return stack;
    }

    private ArmorStand spawnAbyssalRiftNameStand(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setGravity(false);
            stand.setCustomNameVisible(true);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.addScoreboardTag(ABYSSAL_RIFT_NAME_TAG);
        });
    }

    private void updateAbyssalRiftNameplate(AbyssalRiftState state) {
        if (state == null) {
            return;
        }
        Interaction interaction = getAbyssalRiftInteraction(state.interactionId());
        if (interaction == null) {
            return;
        }
        ArmorStand titleStand = getAbyssalRiftNameStand(state.titleStandId());
        ArmorStand healthStand = getAbyssalRiftNameStand(state.healthStandId());
        Location base = interaction.getLocation();
        if (titleStand != null) {
            titleStand.teleport(base.clone().add(0.0, ABYSSAL_RIFT_NAME_Y_OFFSET, 0.0));
            titleStand.customName(Component.text("Abyssal Rift", NamedTextColor.DARK_AQUA));
        }
        if (healthStand != null) {
            healthStand.teleport(base.clone().add(0.0, ABYSSAL_RIFT_HEALTH_Y_OFFSET, 0.0));
            healthStand.customName(Component.text(formatAbyssalRiftHealth(state), healthColor(state)));
        }
    }

    private String formatAbyssalRiftHealth(AbyssalRiftState state) {
        if (state == null) {
            return "0 HP";
        }
        int current = (int) Math.ceil(Math.max(0.0, state.health()));
        int max = (int) Math.ceil(Math.max(0.0, state.maxHealth()));
        return current + "/" + max + " HP";
    }

    private NamedTextColor healthColor(AbyssalRiftState state) {
        if (state == null || state.maxHealth() <= 0.0) {
            return NamedTextColor.RED;
        }
        double ratio = Math.max(0.0, state.health()) / state.maxHealth();
        if (ratio > 0.66) {
            return NamedTextColor.GREEN;
        }
        if (ratio > 0.33) {
            return NamedTextColor.YELLOW;
        }
        return NamedTextColor.RED;
    }

    private Interaction getAbyssalRiftInteraction(UUID entityId) {
        Entity entity = entityId != null ? Bukkit.getEntity(entityId) : null;
        return entity instanceof Interaction interaction ? interaction : null;
    }

    private ItemDisplay getAbyssalRiftDisplay(UUID entityId) {
        Entity entity = entityId != null ? Bukkit.getEntity(entityId) : null;
        return entity instanceof ItemDisplay display ? display : null;
    }

    private ArmorStand getAbyssalRiftNameStand(UUID entityId) {
        Entity entity = entityId != null ? Bukkit.getEntity(entityId) : null;
        return entity instanceof ArmorStand stand ? stand : null;
    }

    private void clearAbyssalRifts() {
        for (UUID interactionId : new ArrayList<>(abyssalRifts.keySet())) {
            destroyAbyssalRift(interactionId, false);
        }
    }

    private void destroyAbyssalRift(UUID interactionId, boolean playEffects) {
        AbyssalRiftState state = abyssalRifts.remove(interactionId);
        if (state == null) {
            return;
        }
        abyssalRiftEntityLinks.remove(interactionId);
        abyssalRiftEntityLinks.remove(state.displayId());
        if (state.auraTask() != null) {
            state.auraTask().cancel();
        }
        ArmorStand titleStand = getAbyssalRiftNameStand(state.titleStandId());
        if (titleStand != null) {
            titleStand.remove();
        }
        ArmorStand healthStand = getAbyssalRiftNameStand(state.healthStandId());
        if (healthStand != null) {
            healthStand.remove();
        }
        ItemDisplay display = getAbyssalRiftDisplay(state.displayId());
        if (display != null) {
            display.remove();
        }
        Interaction interaction = getAbyssalRiftInteraction(interactionId);
        if (interaction != null) {
            Location location = interaction.getLocation().add(0.0, 1.0, 0.0);
            World world = interaction.getWorld();
            interaction.remove();
            if (playEffects && world != null) {
                world.spawnParticle(Particle.SMOKE, location, 16, 0.2, 0.3, 0.2, 0.01);
                world.playSound(location, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 0.8f);
            }
        }
    }

    public void startLobby(JavaPlugin plugin, Player initiator, int lobbySeconds) {
        this.plugin = plugin;
        stopInternal();
        resetState();
        state = GameState.LOBBY;
        lobbyInitiatorId = initiator != null ? initiator.getUniqueId() : null;
        prepareWorld();
        updateTeamsInMatch();
        initializeTeamUpgrades();
        initializeBaseCenters();
        initializeBeds();
        applyBedLayout();
        createTemporaryMapLobbyIsland();

        lobbyCountdownRemaining = Math.max(0, lobbySeconds);
        lobbyCountdownPaused = false;
        startCountdownRemaining = lobbyCountdownRemaining;

        Location lobby = resolveMapLobbyLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isInArenaWorld(player.getWorld())) {
                continue;
            }
            if (lobby != null) {
                player.teleport(lobby);
                player.setRespawnLocation(lobby, true);
            }
            player.getInventory().clear();
            applyLobbyBuffs(player);
            double maxHealth = player.getMaxHealth();
            player.setHealth(Math.max(1.0, maxHealth));
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            if (isLobbyInitiator(player.getUniqueId())) {
                giveLobbyControlItems(player);
            }
            if (teamPickEnabled) {
                giveTeamSelectItem(player);
            }
        }

        startLobbyCountdown();
        startSidebarUpdates();
    }

    public void start(JavaPlugin plugin, Player initiator) {
        start(plugin, initiator, START_COUNTDOWN_SECONDS);
    }

    public void start(JavaPlugin plugin, Player initiator, int countdownSeconds) {
        this.plugin = plugin;
        stopInternal();
        resetState();
        state = GameState.STARTING;
        prepareWorld();
        updateTeamsInMatch();
        initializeTeamUpgrades();
        rollMatchEvent();
        applyPreMatchEventSetup();
        initializeBaseCenters();
        initializeBeds();
        applyBedLayout();
        rollRotatingItems();
        applyMatchEventRotatingOverrides();
        spawnShops();
        startCountdownRemaining = Math.max(0, countdownSeconds);

        Location respawnLobby = resolveMapLobbyLocation();
        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            TeamColor team = entry.getValue();
            if (player == null || team == null) {
                continue;
            }
            Location spawn = arena.getSpawn(team);
            if (spawn == null) {
                initiator.sendMessage(Component.text("Missing spawn for team " + team.displayName() + ".", NamedTextColor.RED));
                continue;
            }
            player.teleport(spawn);
            player.getInventory().clear();
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            double maxHealth = player.getMaxHealth();
            player.setHealth(Math.max(1.0, maxHealth));
            giveStarterKit(player, team);
            applyPermanentItemsWithShield(player, team);
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            frozenPlayers.add(player.getUniqueId());
            player.sendMessage(Component.text("You are on the ").append(team.displayComponent()).append(Component.text(" team.")));
            if (respawnLobby != null) {
                player.setRespawnLocation(respawnLobby, true);
            }
        }

        startCountdown(countdownSeconds);
        startSidebarUpdates();
    }

    public int getLobbyCountdownRemaining() {
        return lobbyCountdownRemaining;
    }

    public boolean isLobbyCountdownPaused() {
        return lobbyCountdownPaused;
    }

    public void toggleLobbyCountdownPause() {
        if (lobbyCountdownTask == null) {
            return;
        }
        lobbyCountdownPaused = !lobbyCountdownPaused;
    }

    public void skipLobbyCountdown() {
        if (lobbyCountdownTask == null) {
            return;
        }
        lobbyCountdownRemaining = 0;
        lobbyCountdownPaused = false;
        beginMatchFromLobby();
    }

    public void stop() {
        stopInternal();
        cleanupWorld();
        resetState();
        state = GameState.IDLE;
    }

    public boolean handlePlayerDeath(Player player) {
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        clearPendingDeathCredit(playerId);
        if (!isRunning() || !isParticipant(playerId)) {
            return false;
        }
        clearElytraStrike(player, false, false);
        clearCombat(playerId);
        clearTrapImmunity(playerId);
        removeRespawnProtection(playerId);
        downgradeTools(playerId);
        removeItems(player, PICKAXE_MATERIALS);
        removeItems(player, AXE_MATERIALS);
        TeamColor team = getTeam(playerId);
        if (team == null) {
            return false;
        }
        if (getBedState(team) == BedState.ALIVE || respawnGracePlayers.contains(playerId)) {
            pendingRespawns.add(playerId);
            return false;
        }
        eliminatePlayer(player, team);
        return true;
    }

    public void handleRespawn(Player player) {
        UUID playerId = player.getUniqueId();
        if (!isActive() || !isParticipant(playerId)) {
            return;
        }
        TeamColor team = getTeam(playerId);
        if (team == null) {
            return;
        }
        Location lobby = resolveMapLobbyLocation();
        if (getBedState(team) == BedState.DESTROYED && eliminatedPlayers.contains(playerId)) {
            setSpectator(player);
            if (lobby != null) {
                player.teleport(lobby);
            }
            return;
        }
        if (!pendingRespawns.contains(playerId)) {
            return;
        }
        setSpectator(player);
        if (lobby != null) {
            player.teleport(lobby);
        }
        boolean allowRespawnAfterBedBreak = respawnGracePlayers.contains(playerId);
        if (getBedState(team) == BedState.DESTROYED && !allowRespawnAfterBedBreak) {
            pendingRespawns.remove(playerId);
            awardPendingDeathFinalStats(playerId);
            eliminatePlayer(player, team);
            return;
        }
        scheduleRespawn(player, team, RESPAWN_DELAY_SECONDS, allowRespawnAfterBedBreak);
    }

    public void handleBedDestroyed(TeamColor team, Player breaker) {
        if (team == null || getBedState(team) == BedState.DESTROYED) {
            return;
        }
        if (statsEnabled && breaker != null && isParticipant(breaker.getUniqueId())) {
            bedwarsManager.getStatsService().addBedBroken(breaker.getUniqueId());
        }
        if (breaker != null) {
            queuePartyExp(breaker.getUniqueId(), PARTY_EXP_BED_BREAK);
        }
        grantPendingRespawnGrace(team);
        destroyBed(team);
        broadcast(Component.text("The ", NamedTextColor.RED)
                .append(team.displayComponent())
                .append(Component.text(" bed was destroyed!", NamedTextColor.RED)));
        playSoundToParticipants(Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);

        Title title = Title.title(
                Component.text("Your Bed was destroyed!", NamedTextColor.RED),
                Component.text("You will no longer respawn.", NamedTextColor.GRAY),
                ALERT_TITLE_TIMES
        );
        for (UUID playerId : assignments.keySet()) {
            if (team.equals(assignments.get(playerId))) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.showTitle(title);
                }
            }
        }
        checkTeamEliminated(team);
    }

    public boolean triggerRespawnBeacon(Player activator, int delaySeconds) {
        if (activator == null || !isRunning()) {
            return false;
        }
        UUID activatorId = activator.getUniqueId();
        if (!isParticipant(activatorId)) {
            return false;
        }
        TeamColor team = getTeam(activatorId);
        if (team == null) {
            return false;
        }
        if (getBedState(team) != BedState.DESTROYED) {
            activator.sendMessage(Component.text("Your bed is still alive.", NamedTextColor.RED));
            return false;
        }
        if (getTeamMemberCount(team) != 2) {
            activator.sendMessage(Component.text("Respawn Beacon can only be used in 2-player teams.", NamedTextColor.RED));
            return false;
        }
        UUID targetId = null;
        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            if (entry.getValue() != team) {
                continue;
            }
            UUID playerId = entry.getKey();
            if (playerId.equals(activatorId)) {
                continue;
            }
            if (eliminatedPlayers.contains(playerId)) {
                targetId = playerId;
                break;
            }
        }
        if (targetId == null) {
            activator.sendMessage(Component.text("No eliminated teammate to respawn.", NamedTextColor.RED));
            return false;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            activator.sendMessage(Component.text("Your teammate must be online to respawn.", NamedTextColor.RED));
            return false;
        }
        showTitleAll(
                Component.empty()
                        .append(team.displayComponent())
                        .append(Component.text(" Used the Respawn Beacon.", NamedTextColor.WHITE)),
                Component.empty()
        );
        eliminatedPlayers.remove(targetId);
        respawnGracePlayers.add(targetId);
        setSpectator(target);
        scheduleRespawn(target, team, delaySeconds, true, true);
        return true;
    }

    public boolean triggerSoloRespawnBeacon(Player player, int delaySeconds) {
        if (player == null || !isRunning()) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (!isParticipant(playerId)) {
            return false;
        }
        TeamColor team = getTeam(playerId);
        if (team == null || getBedState(team) != BedState.DESTROYED) {
            return false;
        }
        if (getTeamMemberCount(team) != 1) {
            return false;
        }
        pendingRespawns.add(playerId);
        respawnGracePlayers.add(playerId);
        showTitleAll(
                Component.empty()
                        .append(team.displayComponent())
                        .append(Component.text(" Respawn Beacon activated!", NamedTextColor.WHITE)),
                Component.text(player.getName() + " will respawn in " + delaySeconds + "s.", NamedTextColor.GRAY));
        return true;
    }

    private void playSoundToParticipants(Sound sound, float volume, float pitch) {
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    public void handlePlayerEliminated(Player player) {
        TeamColor team = getTeam(player.getUniqueId());
        if (team == null) {
            return;
        }
        eliminatePlayer(player, team);
    }

    private void startCountdown(int seconds) {
        if (seconds <= 0) {
            beginRunning();
            return;
        }
        BukkitTask task = new BukkitRunnable() {
            private int remaining = seconds;

            @Override
            public void run() {
                safeRun("startCountdown", () -> {
                    if (state != GameState.STARTING) {
                        cancel();
                        return;
                    }
                    if (remaining <= 0) {
                        showTitleAll(Component.text("GO!", NamedTextColor.GREEN), Component.empty());
                        beginRunning();
                        cancel();
                        return;
                    }
                    startCountdownRemaining = remaining;
                    showTitleAll(Component.text("Starting in " + remaining, NamedTextColor.YELLOW), Component.empty());
                    remaining--;
                });
            }
        }.runTaskTimer(plugin, 0L, 20L);
        tasks.add(task);
    }

    private void startLobbyCountdown() {
        if (lobbyCountdownTask != null) {
            lobbyCountdownTask.cancel();
        }
        lobbyCountdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                safeRun("lobbyCountdown", () -> {
                    if (state != GameState.LOBBY) {
                        cancel();
                        return;
                    }
                    if (lobbyCountdownPaused) {
                        return;
                    }
                    if (lobbyCountdownRemaining <= 0) {
                        showTitleAll(Component.text("GO!", NamedTextColor.GREEN), Component.empty());
                        beginMatchFromLobby();
                        cancel();
                        return;
                    }
                    startCountdownRemaining = lobbyCountdownRemaining;
                    showTitleAll(Component.text("Starting in " + lobbyCountdownRemaining, NamedTextColor.YELLOW), Component.empty());
                    lobbyCountdownRemaining--;
                });
            }
        }.runTaskTimer(plugin, 0L, 20L);
        tasks.add(lobbyCountdownTask);
    }

    private void beginMatchFromLobby() {
        if (lobbyCountdownTask != null) {
            lobbyCountdownTask.cancel();
            lobbyCountdownTask = null;
        }
        Player initiator = lobbyInitiatorId != null ? Bukkit.getPlayer(lobbyInitiatorId) : null;
        if (initiator == null) {
            initiator = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        }
        if (initiator == null) {
            stop();
            return;
        }
        start(plugin, initiator, 0);
    }

    private void beginRunning() {
        state = GameState.RUNNING;
        frozenPlayers.clear();
        matchStartMillis = System.currentTimeMillis();
        startCountdownRemaining = 0;
        if (statsEnabled) {
            for (UUID playerId : assignments.keySet()) {
                bedwarsManager.getStatsService().addGamePlayed(playerId);
            }
        }
        generatorManager = new GeneratorManager(plugin, arena, this);
        syncForgeTiers();
        generatorManager.start();
        startUpgradeTasks();
        startRegenTask();
        scheduleGameEvents();
        applyMatchEventToParticipants();
        startMatchEventTasks();
        announceMatchEvent();
        announceCurrentRotatingItems();
    }

    private void scheduleGameEvents() {
        krispasi.omGames.bedwars.model.EventSettings events = arena.getEventSettings();
        scheduleLongMatchPartyExp(LONG_MATCH_PARTY_EXP_SECONDS);
        scheduleTierUpgrade(2, events.getTier2Delay());
        scheduleTierUpgrade(3, events.getTier3Delay());
        scheduleBedDestruction(events.getBedDestructionDelay());
        scheduleSuddenDeath(events.getSuddenDeathDelay());
        scheduleGameEnd(events.getGameEndDelay());
    }

    private void rollMatchEvent() {
        activeMatchEvent = null;
        benevolentEventUpgrades.clear();
        if (!matchEventRollEnabled) {
            return;
        }
        if (forcedMatchEvent != null) {
            activeMatchEvent = forcedMatchEvent;
            return;
        }
        BedwarsMatchEventConfig config = bedwarsManager.getMatchEventConfig();
        if (config == null || !config.isEnabled() || !config.hasEligibleEvents()) {
            return;
        }
        double roll = ThreadLocalRandom.current().nextDouble(100.0);
        if (roll >= config.chancePercent()) {
            return;
        }
        activeMatchEvent = pickWeightedMatchEvent(config);
    }

    private BedwarsMatchEventType pickWeightedMatchEvent(BedwarsMatchEventConfig config) {
        if (config == null) {
            return null;
        }
        int totalWeight = 0;
        for (BedwarsMatchEventType type : BedwarsMatchEventType.values()) {
            totalWeight += Math.max(0, config.weight(type));
        }
        if (totalWeight <= 0) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int current = 0;
        for (BedwarsMatchEventType type : BedwarsMatchEventType.values()) {
            current += Math.max(0, config.weight(type));
            if (roll < current) {
                return type;
            }
        }
        return null;
    }

    private void applyPreMatchEventSetup() {
        if (activeMatchEvent != BedwarsMatchEventType.BENEVOLENT_UPGRADES) {
            return;
        }
        List<TeamUpgradeType> pool = new ArrayList<>(BENEVOLENT_UPGRADE_POOL);
        Collections.shuffle(pool);
        int count = Math.min(3, pool.size());
        for (int i = 0; i < count; i++) {
            TeamUpgradeType type = pool.get(i);
            benevolentEventUpgrades.add(type);
            for (TeamColor team : teamsInMatch) {
                getUpgradeState(team).setTier(type, type.maxTier());
            }
        }
    }

    private void applyMatchEventRotatingOverrides() {
        if (activeMatchEvent != BedwarsMatchEventType.APRIL_FOOLS) {
            return;
        }
        String bedrockId = normalizeItemId("bedrock");
        if (bedrockId == null || !isRotatingItemCandidate(bedrockId)) {
            return;
        }
        int target = 2;
        java.util.LinkedHashSet<String> adjusted = new java.util.LinkedHashSet<>();
        adjusted.add(bedrockId);
        for (String id : rotatingItemIds) {
            if (adjusted.size() >= target) {
                break;
            }
            if (!bedrockId.equals(id)) {
                adjusted.add(id);
            }
        }
        if (adjusted.size() < target) {
            for (String candidate : getRotatingItemCandidateIds()) {
                if (adjusted.size() >= target) {
                    break;
                }
                if (!bedrockId.equals(candidate)) {
                    adjusted.add(candidate);
                }
            }
        }
        rotatingItemIds.clear();
        rotatingItemIds.addAll(adjusted);
    }

    private void applyMatchEventToParticipants() {
        if (activeMatchEvent == null) {
            return;
        }
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            applyTeamUpgrades(player, assignments.get(playerId));
        }
    }

    private void startMatchEventTasks() {
        if (activeMatchEvent == null || plugin == null) {
            return;
        }
        if (activeMatchEvent == BedwarsMatchEventType.BLOOD_MOON) {
            prepareBloodMoonWorld();
            BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                World world = arena.getWorld();
                if (world == null) {
                    return;
                }
                world.setTime(18000L);
                Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(200, 20, 20), 1.2f);
                for (UUID playerId : assignments.keySet()) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player == null || !isInArenaWorld(player.getWorld()) || player.getGameMode() == GameMode.SPECTATOR) {
                        continue;
                    }
                    Location location = player.getLocation().add(0, 1.0, 0);
                    world.spawnParticle(Particle.DUST, location, 10, 0.45, 0.7, 0.45, 0.01, dust);
                }
            }, 0L, 20L);
            tasks.add(task);
            return;
        }
        if (activeMatchEvent == BedwarsMatchEventType.APRIL_FOOLS) {
            BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                World world = arena.getWorld();
                if (world == null) {
                    return;
                }
                for (UUID playerId : assignments.keySet()) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player == null || !isInArenaWorld(player.getWorld()) || player.getGameMode() == GameMode.SPECTATOR) {
                        continue;
                    }
                    world.spawnParticle(Particle.NOTE,
                            player.getLocation().add(0, 1.0, 0),
                            6,
                            0.35,
                            0.5,
                            0.35,
                            1.0);
                }
            }, 20L, 20L);
            tasks.add(task);
        }
    }

    private void prepareBloodMoonWorld() {
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        if (previousWorldTime == null) {
            previousWorldTime = world.getTime();
        }
        if (previousDaylightCycle == null) {
            previousDaylightCycle = world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE);
        }
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setTime(18000L);
    }

    private void restoreMatchEventWorldState() {
        World world = arena.getWorld();
        if (world == null) {
            previousWorldTime = null;
            previousDaylightCycle = null;
            return;
        }
        if (previousDaylightCycle != null) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, previousDaylightCycle);
        }
        if (previousWorldTime != null) {
            world.setTime(previousWorldTime);
        }
        previousWorldTime = null;
        previousDaylightCycle = null;
    }

    private void announceMatchEvent() {
        if (activeMatchEvent == null || plugin == null) {
            return;
        }
        broadcast(Component.text("Match Event: ", NamedTextColor.AQUA)
                .append(Component.text(activeMatchEvent.displayName(), NamedTextColor.YELLOW)));
        if (activeMatchEvent == BedwarsMatchEventType.BENEVOLENT_UPGRADES && !benevolentEventUpgrades.isEmpty()) {
            List<String> upgrades = new ArrayList<>();
            for (TeamUpgradeType type : benevolentEventUpgrades) {
                upgrades.add(type.tierName(type.maxTier()));
            }
            broadcast(Component.text("Rolled Upgrades: ", NamedTextColor.GRAY)
                    .append(Component.text(String.join(", ", upgrades), NamedTextColor.GREEN)));
        }
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                        showTitleAll(Component.text(activeMatchEvent.displayName(), NamedTextColor.GOLD),
                                Component.text(activeMatchEvent.subtitle(), NamedTextColor.GRAY)),
                10L);
        tasks.add(task);
    }

    private void triggerTierUpgrade(int tier) {
        if (state != GameState.RUNNING || generatorManager == null) {
            return;
        }
        int clamped = Math.max(1, Math.min(3, tier));
        if (clamped <= 1) {
            return;
        }
        if (clamped == 2 && tier2Triggered) {
            return;
        }
        if (clamped == 3 && tier3Triggered) {
            return;
        }
        if (clamped >= 2) {
            tier2Triggered = true;
        }
        if (clamped >= 3) {
            tier3Triggered = true;
        }
        generatorManager.setDiamondTier(clamped);
        generatorManager.setEmeraldTier(clamped);
        generatorManager.refresh();
        broadcast(Component.text("Generators " + toRoman(clamped) + "!", NamedTextColor.GOLD));
    }

    private void scheduleTierUpgrade(int tier, int delaySeconds) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            safeRun("tierUpgrade", () -> {
                triggerTierUpgrade(tier);
            });
        }, delaySeconds * 20L);
        tasks.add(task);
    }

    private void scheduleAnnouncement(Component message, int delaySeconds) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            safeRun("announcement", () -> {
                if (state != GameState.RUNNING) {
                    return;
                }
                broadcast(message);
            });
        }, delaySeconds * 20L);
        tasks.add(task);
    }

    private void scheduleBedDestruction(int delaySeconds) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            safeRun("bedDestruction", () -> {
                triggerBedDestructionEvent();
            });
        }, delaySeconds * 20L);
        tasks.add(task);
    }

    private void scheduleLongMatchPartyExp(int delaySeconds) {
        if (delaySeconds <= 0) {
            updateMatchParticipationPartyExp(PARTY_EXP_LONG_MATCH);
            return;
        }
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            safeRun("longMatchPartyExp", () -> {
                if (state != GameState.RUNNING) {
                    return;
                }
                updateMatchParticipationPartyExp(PARTY_EXP_LONG_MATCH);
            });
        }, delaySeconds * 20L);
        tasks.add(task);
    }

    private void scheduleSuddenDeath(int delaySeconds) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            safeRun("suddenDeath", () -> {
                triggerSuddenDeath();
            });
        }, delaySeconds * 20L);
        tasks.add(task);
    }

    private void scheduleGameEnd(int delaySeconds) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            safeRun("gameEnd", () -> {
                triggerGameEnd();
            });
        }, delaySeconds * 20L);
        tasks.add(task);
    }

    private void triggerBedDestructionEvent() {
        if (state != GameState.RUNNING || bedDestructionTriggered) {
            return;
        }
        bedDestructionTriggered = true;
        updateMatchParticipationPartyExp(PARTY_EXP_BED_GONE_MATCH);
        triggerBedDestruction();
    }

    private void triggerSuddenDeath() {
        if (state != GameState.RUNNING || suddenDeathActive) {
            return;
        }
        suddenDeathActive = true;
        boolean destroyedBeds = false;
        for (TeamColor team : teamsInMatch) {
            if (getBedState(team) == BedState.ALIVE) {
                destroyBed(team);
                destroyedBeds = true;
            }
        }
        if (destroyedBeds) {
            updateSidebars();
        }
        Component subtitle = destroyedBeds
                ? Component.text("Remaining beds were destroyed.", NamedTextColor.GRAY)
                : Component.text("Final battle begins.", NamedTextColor.GRAY);
        showTitleAll(Component.text("Sudden Death!", NamedTextColor.RED), subtitle);
        if (destroyedBeds) {
            broadcast(Component.text("Sudden Death has begun! Any remaining beds were destroyed.", NamedTextColor.RED));
        } else {
            broadcast(Component.text("Sudden Death has begun!", NamedTextColor.RED));
        }
        startSuddenDeathBorderShrink();
        if (destroyedBeds) {
            for (TeamColor team : teamsInMatch) {
                checkTeamEliminated(team);
            }
        }
    }

    private void triggerGameEnd() {
        if (state != GameState.RUNNING || gameEndTriggered) {
            return;
        }
        gameEndTriggered = true;
        List<TeamColor> aliveTeams = getAliveTeams();
        if (aliveTeams.size() == 1) {
            bedwarsManager.endSession(this, aliveTeams.get(0));
            return;
        }
        bedwarsManager.endSession(this, null);
    }

    private void startSuddenDeathBorderShrink() {
        World world = arena.getWorld();
        BlockPoint corner1 = arena.getCorner1();
        BlockPoint corner2 = arena.getCorner2();
        BlockPoint center = arena.getCenter();
        if (world == null || corner1 == null || corner2 == null || center == null) {
            return;
        }
        EventSettings events = arena.getEventSettings();
        int remainingSeconds = Math.max(1, events.getGameEndDelay() - events.getSuddenDeathDelay());
        WorldBorder border = world.getWorldBorder();
        double targetSize = SUDDEN_DEATH_BORDER_TARGET_SIZE;
        border.setCenter(center.x() + 0.5, center.z() + 0.5);
        border.setSize(Math.max(targetSize, 1.0), remainingSeconds);
        border.setDamageBuffer(WORLD_BORDER_DAMAGE_BUFFER);
        border.setDamageAmount(WORLD_BORDER_DAMAGE_AMOUNT);
        border.setWarningDistance(WORLD_BORDER_WARNING_DISTANCE);
    }

    private void scheduleRespawn(Player player, TeamColor team) {
        scheduleRespawn(player, team, RESPAWN_DELAY_SECONDS, false);
    }

    private void scheduleRespawn(Player player, TeamColor team, int delaySeconds, boolean allowRespawnAfterBedBreak) {
        scheduleRespawn(player, team, delaySeconds, allowRespawnAfterBedBreak, false);
    }

    private void scheduleRespawn(Player player,
                                 TeamColor team,
                                 int delaySeconds,
                                 boolean allowRespawnAfterBedBreak,
                                 boolean beaconRevive) {
        Location spawn = arena.getSpawn(team);
        if (spawn == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        BukkitTask existing = respawnTasks.remove(player.getUniqueId());
        if (existing != null) {
            existing.cancel();
        }
        BukkitTask existingCountdown = respawnCountdownTasks.remove(player.getUniqueId());
        if (existingCountdown != null) {
            existingCountdown.cancel();
        }
        if (beaconRevive) {
            startRespawnCountdown(player, delaySeconds, team, player.getName());
        } else {
            startRespawnCountdown(player, delaySeconds);
        }
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> safeRun("respawn", () -> {
            boolean allowRespawn = allowRespawnAfterBedBreak || respawnGracePlayers.contains(playerId);
            boolean finalElimination = getBedState(team) == BedState.DESTROYED && !allowRespawn;
            if (state != GameState.RUNNING || (getBedState(team) == BedState.DESTROYED && !allowRespawn)) {
                if (finalElimination) {
                    awardPendingDeathFinalStats(playerId);
                } else {
                    clearPendingDeathCredit(playerId);
                }
                eliminatedPlayers.add(playerId);
                pendingRespawns.remove(playerId);
                setSpectator(player);
                removeRespawnProtection(playerId);
                cancelRespawnCountdown(playerId);
                respawnGracePlayers.remove(playerId);
                respawnTasks.remove(player.getUniqueId());
                checkTeamEliminated(team);
                return;
            }
            pendingRespawns.remove(playerId);
            player.teleport(spawn);
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.getInventory().clear();
            giveStarterKit(player, team);
            applyPermanentItemsWithShield(player, team);
            grantRespawnProtection(player);
            cancelRespawnCountdown(playerId);
            respawnGracePlayers.remove(playerId);
            respawnTasks.remove(player.getUniqueId());
            clearPendingDeathCredit(playerId);
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> applyPermanentItemsWithShield(player, team),
                    1L);
            if (beaconRevive) {
                broadcast(Component.text(player.getName(), NamedTextColor.AQUA)
                        .append(Component.text(" has been revived.", NamedTextColor.GREEN)));
            }
        }), Math.max(0, delaySeconds) * 20L);
        respawnTasks.put(player.getUniqueId(), task);
        showTitle(player, Component.text("Respawning in " + delaySeconds, NamedTextColor.YELLOW), Component.empty());
    }

    private void grantPendingRespawnGrace(TeamColor team) {
        if (team == null) {
            return;
        }
        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            if (entry.getValue() != team) {
                continue;
            }
            UUID playerId = entry.getKey();
            if (pendingRespawns.contains(playerId)) {
                respawnGracePlayers.add(playerId);
            }
        }
    }

    private void clearPendingDeathCredit(UUID playerId) {
        if (playerId == null) {
            return;
        }
        pendingDeathKillCredits.remove(playerId);
    }

    private void awardPendingDeathFinalStats(UUID playerId) {
        if (playerId == null || !pendingDeathKillCredits.containsKey(playerId)) {
            return;
        }
        UUID killerId = pendingDeathKillCredits.remove(playerId);
        if (statsEnabled) {
            bedwarsManager.getStatsService().addFinalDeath(playerId);
        }
        if (killerId == null) {
            return;
        }
        rewardFinalKill(killerId);
        if (statsEnabled) {
            bedwarsManager.getStatsService().addFinalKill(killerId);
        }
    }

    private void eliminatePlayer(Player player, TeamColor team) {
        UUID playerId = player.getUniqueId();
        eliminatedPlayers.add(playerId);
        pendingRespawns.remove(playerId);
        respawnGracePlayers.remove(playerId);
        clearPendingDeathCredit(playerId);
        setSpectator(player);
        checkTeamEliminated(team);
    }

    private void checkTeamEliminated(TeamColor team) {
        if (eliminatedTeams.contains(team)) {
            return;
        }
        if (!isTeamAlive(team)) {
            eliminatedTeams.add(team);
            eliminationOrder.remove(team);
            eliminationOrder.add(team);
            broadcast(Component.text("Team ", NamedTextColor.RED)
                    .append(team.displayComponent())
                    .append(Component.text(" has been eliminated!", NamedTextColor.RED)));
            checkForWin();
        }
    }

    private void checkForWin() {
        List<TeamColor> aliveTeams = getAliveTeams();
        if (aliveTeams.size() == 1) {
            bedwarsManager.endSession(this, aliveTeams.get(0));
        }
    }

    private List<TeamColor> getAliveTeams() {
        List<TeamColor> aliveTeams = new ArrayList<>();
        for (TeamColor team : teamsInMatch) {
            if (isTeamAlive(team)) {
                aliveTeams.add(team);
            }
        }
        return aliveTeams;
    }

    private boolean isTeamAlive(TeamColor team) {
        if (!hasPlayers(team)) {
            return false;
        }
        if (getBedState(team) == BedState.ALIVE) {
            return true;
        }
        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            if (entry.getValue() == team && !eliminatedPlayers.contains(entry.getKey())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPlayers(TeamColor team) {
        for (TeamColor value : assignments.values()) {
            if (value == team) {
                return true;
            }
        }
        return false;
    }

    private void initializeBeds() {
        bedStates.clear();
        activeBedLocations.clear();
        bedBlocks.clear();
        for (Map.Entry<TeamColor, BedLocation> entry : arena.getBeds().entrySet()) {
            if (!teamsInMatch.contains(entry.getKey())) {
                continue;
            }
            setBedAlive(entry.getKey(), entry.getValue());
        }
    }

    private void destroyBed(TeamColor team) {
        bedStates.put(team, BedState.DESTROYED);
        removeHealPoolEffects(team);
        BedLocation location = clearTrackedBed(team);
        if (location == null) {
            return;
        }
        removeBedBlocks(location);
    }

    private void setBedAlive(TeamColor team, BedLocation location) {
        if (team == null || location == null) {
            return;
        }
        bedStates.put(team, BedState.ALIVE);
        activeBedLocations.put(team, location);
        bedBlocks.put(location.head(), team);
        bedBlocks.put(location.foot(), team);
    }

    private BedLocation clearTrackedBed(TeamColor team) {
        if (team == null) {
            return null;
        }
        BedLocation location = activeBedLocations.remove(team);
        if (location == null) {
            return null;
        }
        bedBlocks.remove(location.head());
        bedBlocks.remove(location.foot());
        return location;
    }

    private void reviveEliminatedTeammates(TeamColor team) {
        if (team == null) {
            return;
        }
        Location spawn = arena.getSpawn(team);
        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            if (entry.getValue() != team) {
                continue;
            }
            UUID playerId = entry.getKey();
            if (!eliminatedPlayers.remove(playerId)) {
                continue;
            }
            pendingRespawns.remove(playerId);
            respawnGracePlayers.remove(playerId);
            cancelRespawnCountdown(playerId);
            BukkitTask respawnTask = respawnTasks.remove(playerId);
            if (respawnTask != null) {
                respawnTask.cancel();
            }
            Player teammate = Bukkit.getPlayer(playerId);
            if (teammate != null && teammate.isOnline()) {
                restoreRevivedTeammate(teammate, team, spawn);
            }
        }
    }

    private void restoreRevivedTeammate(Player player, TeamColor team, Location spawn) {
        if (player == null || team == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        clearCombat(playerId);
        clearTrapImmunity(playerId);
        removeRespawnProtection(playerId);
        if (spawn != null) {
            player.teleport(spawn);
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.getInventory().clear();
        giveStarterKit(player, team);
        applyPermanentItemsWithShield(player, team);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setHealth(Math.max(1.0, player.getMaxHealth()));
        grantRespawnProtection(player);
        hideEditorsFrom(player);
        updateSidebarForPlayer(player);
    }

    private void removeBedBlocks(BedLocation location) {
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        world.getBlockAt(location.head().x(), location.head().y(), location.head().z()).setType(Material.AIR, false);
        world.getBlockAt(location.foot().x(), location.foot().y(), location.foot().z()).setType(Material.AIR, false);
    }

    private void triggerBedDestruction() {
        boolean changed = false;
        for (TeamColor team : teamsInMatch) {
            if (getBedState(team) == BedState.ALIVE) {
                destroyBed(team);
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        showTitleAll(Component.text("Beds Destroyed!", NamedTextColor.RED),
                Component.text("No more respawns.", NamedTextColor.GRAY));
        broadcast(Component.text("All beds have been destroyed!", NamedTextColor.RED));
        for (TeamColor team : teamsInMatch) {
            checkTeamEliminated(team);
        }
    }

    private void cleanupWorld() {
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        clearDroppedItems(world);
        clearContainerInventories(world);
        despawnShops();
        removeActivePlacedBeds();
        for (BlockPoint point : placedBlocks) {
            world.getBlockAt(point.x(), point.y(), point.z()).setType(Material.AIR, false);
        }
        restoreBeds();
        Location lobby = arena.getLobbyLocation();
        Set<UUID> relocatedPlayers = new HashSet<>();
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            relocatedPlayers.add(playerId);
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);
            clearUpgradeEffects(player);
            if (lobby != null) {
                player.teleport(lobby);
                player.setRespawnLocation(lobby, true);
            }
        }
        if (lobby == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (player == null || relocatedPlayers.contains(player.getUniqueId())) {
                continue;
            }
            if (player.getGameMode() != GameMode.SPECTATOR) {
                continue;
            }
            clearUpgradeEffects(player);
            restoreSidebar(player.getUniqueId());
            player.teleport(lobby);
            player.setRespawnLocation(lobby, true);
        }
    }

    private void restoreBeds() {
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        for (Map.Entry<TeamColor, BedLocation> entry : arena.getBeds().entrySet()) {
            restoreBed(world, entry.getKey(), entry.getValue());
        }
    }

    private void createTemporaryMapLobbyIsland() {
        removeTemporaryMapLobbyIsland();
        Location lobby = resolveMapLobbyLocation();
        if (lobby == null || lobby.getWorld() == null) {
            return;
        }
        World world = lobby.getWorld();
        int floorY = lobby.getBlockY() - 1;
        if (floorY < world.getMinHeight() || floorY >= world.getMaxHeight()) {
            return;
        }
        int centerX = lobby.getBlockX();
        int centerZ = lobby.getBlockZ();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block block = world.getBlockAt(centerX + dx, floorY, centerZ + dz);
                BlockPoint point = new BlockPoint(block.getX(), block.getY(), block.getZ());
                temporaryMapLobbyIslandBlocks.put(point, block.getState());
                block.setType(Material.BARRIER, false);
            }
        }
    }

    private void removeTemporaryMapLobbyIsland() {
        if (temporaryMapLobbyIslandBlocks.isEmpty()) {
            return;
        }
        for (BlockState snapshot : temporaryMapLobbyIslandBlocks.values()) {
            if (snapshot != null) {
                snapshot.update(true, false);
            }
        }
        temporaryMapLobbyIslandBlocks.clear();
    }

    private void removeActivePlacedBeds() {
        for (Map.Entry<TeamColor, BedLocation> entry : activeBedLocations.entrySet()) {
            BedLocation activeLocation = entry.getValue();
            if (activeLocation == null) {
                continue;
            }
            BedLocation originalLocation = arena.getBeds().get(entry.getKey());
            if (!activeLocation.equals(originalLocation)) {
                removeBedBlocks(activeLocation);
            }
        }
    }

    private void applyBedLayout() {
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        for (Map.Entry<TeamColor, BedLocation> entry : arena.getBeds().entrySet()) {
            if (teamsInMatch.contains(entry.getKey())) {
                restoreBed(world, entry.getKey(), entry.getValue());
            } else {
                removeBedBlocks(entry.getValue());
            }
        }
    }

    private void prepareWorld() {
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        forceLoadMap(world);
        clearDroppedItems(world);
        applyWorldBorder(world);
    }

    private void forceLoadMap(World world) {
        BlockPoint corner1 = arena.getCorner1();
        BlockPoint corner2 = arena.getCorner2();
        if (corner1 == null || corner2 == null) {
            return;
        }
        int minX = Math.min(corner1.x(), corner2.x());
        int maxX = Math.max(corner1.x(), corner2.x());
        int minZ = Math.min(corner1.z(), corner2.z());
        int maxZ = Math.max(corner1.z(), corner2.z());
        int minChunkX = Math.floorDiv(minX, 16);
        int maxChunkX = Math.floorDiv(maxX, 16);
        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                world.setChunkForceLoaded(chunkX, chunkZ, true);
                world.getChunkAt(chunkX, chunkZ);
                forcedChunks.add(chunkKey(chunkX, chunkZ));
            }
        }
    }

    private void releaseForcedChunks() {
        if (forcedChunks.isEmpty()) {
            return;
        }
        World world = arena.getWorld();
        if (world == null) {
            forcedChunks.clear();
            return;
        }
        for (long key : forcedChunks) {
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            world.setChunkForceLoaded(chunkX, chunkZ, false);
        }
        forcedChunks.clear();
    }

    private long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }

    private void clearDroppedItems(World world) {
        for (Item item : world.getEntitiesByClass(Item.class)) {
            item.remove();
        }
    }

    private void restoreBed(World world, TeamColor team, BedLocation bed) {
        BlockPoint head = bed.head();
        BlockPoint foot = bed.foot();
        int dx = head.x() - foot.x();
        int dz = head.z() - foot.z();
        org.bukkit.block.BlockFace facing = switch (dx + "," + dz) {
            case "1,0" -> org.bukkit.block.BlockFace.EAST;
            case "-1,0" -> org.bukkit.block.BlockFace.WEST;
            case "0,1" -> org.bukkit.block.BlockFace.SOUTH;
            case "0,-1" -> org.bukkit.block.BlockFace.NORTH;
            default -> org.bukkit.block.BlockFace.NORTH;
        };

        Block headBlock = world.getBlockAt(head.x(), head.y(), head.z());
        headBlock.setType(team.bed(), false);
        Bed headData = (Bed) team.bed().createBlockData();
        headData.setPart(Bed.Part.HEAD);
        headData.setFacing(facing);
        headBlock.setBlockData(headData, false);

        Block footBlock = world.getBlockAt(foot.x(), foot.y(), foot.z());
        footBlock.setType(team.bed(), false);
        Bed footData = (Bed) team.bed().createBlockData();
        footData.setPart(Bed.Part.FOOT);
        footData.setFacing(facing);
        footBlock.setBlockData(footData, false);
    }

    private void stopInternal() {
        finalizeMatchParticipationPartyExp();
        flushPendingPartyExpForOnlineParticipants();
        state = GameState.ENDING;
        removeTemporaryMapLobbyIsland();
        clearAllElytraStrikes(false);
        clearAbyssalRifts();
        clearEditors();
        releaseForcedChunks();
        restoreWorldBorder();
        restoreMatchEventWorldState();
        despawnShops();
        despawnSummons();
        closeFakeEnderChests();
        clearSidebars();
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        for (BukkitTask task : respawnTasks.values()) {
            task.cancel();
        }
        respawnTasks.clear();
        for (BukkitTask task : respawnCountdownTasks.values()) {
            task.cancel();
        }
        respawnCountdownTasks.clear();
        for (BukkitTask task : respawnProtectionTasks.values()) {
            task.cancel();
        }
        respawnProtectionTasks.clear();
        respawnProtectionEnds.clear();
        for (BukkitTask task : trapImmunityTasks.values()) {
            task.cancel();
        }
        trapImmunityTasks.clear();
        trapImmunityEnds.clear();
        pendingRespawns.clear();
        respawnGracePlayers.clear();
        if (generatorManager != null) {
            generatorManager.stop();
            generatorManager = null;
        }
        frozenPlayers.clear();
        sidebarTask = null;
        if (lobbyCountdownTask != null) {
            lobbyCountdownTask.cancel();
        }
        lobbyCountdownTask = null;
        lobbyCountdownPaused = false;
        lobbyCountdownRemaining = 0;
        lobbyInitiatorId = null;
        matchStartMillis = 0L;
        startCountdownRemaining = 0;
    }

    private void applyWorldBorder(World world) {
        BlockPoint corner1 = arena.getCorner1();
        BlockPoint corner2 = arena.getCorner2();
        if (world == null || corner1 == null || corner2 == null) {
            return;
        }
        WorldBorder border = world.getWorldBorder();
        if (previousBorderSize == null) {
            previousBorderSize = border.getSize();
            previousBorderCenter = border.getCenter();
            previousBorderDamageAmount = border.getDamageAmount();
            previousBorderDamageBuffer = border.getDamageBuffer();
            previousBorderWarningDistance = border.getWarningDistance();
        }
        int minX = Math.min(corner1.x(), corner2.x());
        int maxX = Math.max(corner1.x(), corner2.x());
        int minZ = Math.min(corner1.z(), corner2.z());
        int maxZ = Math.max(corner1.z(), corner2.z());
        double centerX = (minX + maxX) / 2.0 + 0.5;
        double centerZ = (minZ + maxZ) / 2.0 + 0.5;
        double sizeX = (maxX - minX) + 1.0;
        double sizeZ = (maxZ - minZ) + 1.0;
        double size = Math.max(sizeX, sizeZ) + 2.0;
        border.setCenter(centerX, centerZ);
        border.setSize(size);
        border.setDamageBuffer(WORLD_BORDER_DAMAGE_BUFFER);
        border.setDamageAmount(WORLD_BORDER_DAMAGE_AMOUNT);
        border.setWarningDistance(WORLD_BORDER_WARNING_DISTANCE);
    }

    private void restoreWorldBorder() {
        World world = arena.getWorld();
        if (world == null || previousBorderSize == null || previousBorderCenter == null) {
            return;
        }
        WorldBorder border = world.getWorldBorder();
        border.setCenter(previousBorderCenter);
        border.setSize(previousBorderSize);
        if (previousBorderDamageAmount != null) {
            border.setDamageAmount(previousBorderDamageAmount);
        }
        if (previousBorderDamageBuffer != null) {
            border.setDamageBuffer(previousBorderDamageBuffer);
        }
        if (previousBorderWarningDistance != null) {
            border.setWarningDistance(previousBorderWarningDistance);
        }
        previousBorderSize = null;
        previousBorderCenter = null;
        previousBorderDamageAmount = null;
        previousBorderDamageBuffer = null;
        previousBorderWarningDistance = null;
    }

    private void despawnSummons() {
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        for (org.bukkit.entity.Entity entity : world.getEntities()) {
            if (entity.getScoreboardTags().contains(BED_BUG_TAG)
                    || entity.getScoreboardTags().contains(DREAM_DEFENDER_TAG)
                    || entity.getScoreboardTags().contains(CRYSTAL_TAG)
                    || entity.getScoreboardTags().contains(HAPPY_GHAST_TAG)
                    || entity.getScoreboardTags().contains(CREEPING_CREEPER_TAG)
                    || entity.getScoreboardTags().contains(ABYSSAL_RIFT_TAG)
                    || entity.getScoreboardTags().contains(ABYSSAL_RIFT_DISPLAY_TAG)
                    || entity.getScoreboardTags().contains(ABYSSAL_RIFT_NAME_TAG)) {
                entity.remove();
            }
        }
    }

    private void clearContainerInventories(World world) {
        BlockPoint corner1 = arena.getCorner1();
        BlockPoint corner2 = arena.getCorner2();
        if (corner1 == null || corner2 == null) {
            for (Chunk chunk : world.getLoadedChunks()) {
                clearChunkContainers(chunk);
            }
            return;
        }
        int minX = Math.min(corner1.x(), corner2.x());
        int maxX = Math.max(corner1.x(), corner2.x());
        int minZ = Math.min(corner1.z(), corner2.z());
        int maxZ = Math.max(corner1.z(), corner2.z());
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                clearChunkContainers(chunk);
            }
        }
    }

    private void clearChunkContainers(Chunk chunk) {
        if (chunk == null) {
            return;
        }
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Container container) {
                container.getInventory().clear();
            }
        }
    }

    private void resetState() {
        clearEditors();
        eliminatedPlayers.clear();
        eliminatedTeams.clear();
        eliminationOrder.clear();
        placedBlocks.clear();
        placedBlockItems.clear();
        temporaryMapLobbyIslandBlocks.clear();
        forcedChunks.clear();
        bedStates.clear();
        activeBedLocations.clear();
        bedBlocks.clear();
        fakeEnderChests.clear();
        previousScoreboards.clear();
        activeScoreboards.clear();
        sidebarLines.clear();
        rotatingItemIds.clear();
        rotatingUpgradeIds.clear();
        abyssalRifts.clear();
        abyssalRiftEntityLinks.clear();
        activeElytraStrikes.clear();
        killCounts.clear();
        pendingPartyExp.clear();
        teamPurchaseCounts.clear();
        playerPurchaseCounts.clear();
        customItemCooldownEnds.clear();
        respawnProtectionEnds.clear();
        respawnProtectionTasks.clear();
        teamsInMatch.clear();
        shopNpcIds.clear();
        teamUpgrades.clear();
        baseCenters.clear();
        baseOccupants.clear();
        armorTiers.clear();
        pickaxeTiers.clear();
        axeTiers.clear();
        shearsUnlocked.clear();
        shieldUnlocked.clear();
        lastCombatTimes.clear();
        lastDamagers.clear();
        lastDamagerTimes.clear();
        lastDamageTimes.clear();
        lastRegenTimes.clear();
        pendingDeathKillCredits.clear();
        lockedCommandSpectators.clear();
        benevolentEventUpgrades.clear();
        trapImmunityEnds.clear();
        trapImmunityTasks.clear();
        suddenDeathActive = false;
        tier2Triggered = false;
        tier3Triggered = false;
        bedDestructionTriggered = false;
        gameEndTriggered = false;
        grantedMatchParticipationPartyExp = 0;
        partyExpUnavailableLogged = false;
        activeMatchEvent = null;
        previousWorldTime = null;
        previousDaylightCycle = null;
    }

    public Set<String> getRotatingItemIds() {
        return Collections.unmodifiableSet(rotatingItemIds);
    }

    public Set<String> getRotatingUpgradeIds() {
        return Collections.unmodifiableSet(rotatingUpgradeIds);
    }

    public RotatingSelectionMode getRotatingMode() {
        return rotatingMode;
    }

    public boolean isMatchEventRollEnabled() {
        return matchEventRollEnabled;
    }

    public void setMatchEventRollEnabled(boolean enabled) {
        matchEventRollEnabled = enabled;
    }

    public boolean toggleMatchEventRollEnabled() {
        matchEventRollEnabled = !matchEventRollEnabled;
        return matchEventRollEnabled;
    }

    public BedwarsMatchEventType getForcedMatchEvent() {
        return forcedMatchEvent;
    }

    public void setForcedMatchEvent(BedwarsMatchEventType forcedMatchEvent) {
        this.forcedMatchEvent = forcedMatchEvent;
        if (forcedMatchEvent != null) {
            matchEventRollEnabled = true;
        }
    }

    public BedwarsMatchEventType getActiveMatchEvent() {
        return activeMatchEvent;
    }

    public double getCrystalContactDamage(double configuredDamage) {
        return configuredDamage > 0.0 ? configuredDamage : 1.0;
    }

    public int adjustGeneratedResourceAmount(Material material, int baseAmount) {
        int amount = Math.max(0, baseAmount);
        if (amount <= 0 || activeMatchEvent != BedwarsMatchEventType.IN_THIS_ECONOMY || material == null) {
            return amount;
        }
        return switch (material) {
            case DIAMOND, EMERALD -> 0;
            case GOLD_INGOT -> {
                int halved = amount / 2;
                if ((amount & 1) == 1 && ThreadLocalRandom.current().nextBoolean()) {
                    halved++;
                }
                yield Math.max(0, halved);
            }
            case IRON_INGOT -> Math.max(0, amount * 2);
            default -> amount;
        };
    }

    public void handleMatchEventDamage(Player attacker, Player victim, double finalDamage) {
        if (attacker == null || victim == null || finalDamage <= 0.0) {
            return;
        }
        if (activeMatchEvent != BedwarsMatchEventType.BLOOD_MOON) {
            return;
        }
        TeamColor attackerTeam = getTeam(attacker.getUniqueId());
        TeamColor victimTeam = getTeam(victim.getUniqueId());
        if (attackerTeam == null || victimTeam == null || attackerTeam == victimTeam) {
            return;
        }
        double healAmount = finalDamage * BLOOD_MOON_LIFESTEAL_RATIO;
        if (healAmount <= 0.0) {
            return;
        }
        if (plugin == null) {
            healBloodMoonAttacker(attacker, healAmount);
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> healBloodMoonAttacker(attacker, healAmount));
    }

    private void healBloodMoonAttacker(Player attacker, double healAmount) {
        if (attacker == null || healAmount <= 0.0 || !attacker.isOnline()) {
            return;
        }
        if (!isParticipant(attacker.getUniqueId()) || !isInArenaWorld(attacker.getWorld())) {
            return;
        }
        double currentHealth = attacker.getHealth();
        double newHealth = Math.min(attacker.getMaxHealth(), currentHealth + healAmount);
        if (newHealth <= currentHealth) {
            return;
        }
        attacker.setHealth(newHealth);
        attacker.getWorld().spawnParticle(Particle.HEART,
                attacker.getLocation().add(0.0, 1.0, 0.0),
                2,
                0.25,
                0.35,
                0.25,
                0.0);
    }

    public void setRotatingMode(RotatingSelectionMode mode) {
        if (mode == null) {
            return;
        }
        rotatingMode = mode;
        if (rotatingMode == RotatingSelectionMode.MANUAL) {
            syncManualRotatingSelection();
        } else {
            rotatingItemIds.clear();
            rotatingUpgradeIds.clear();
        }
    }

    public RotatingSelectionMode cycleRotatingMode() {
        RotatingSelectionMode[] values = RotatingSelectionMode.values();
        int next = (rotatingMode.ordinal() + 1) % values.length;
        setRotatingMode(values[next]);
        return rotatingMode;
    }

    public List<String> getManualRotatingItemIds() {
        if (bedwarsManager.getShopConfig() != null) {
            sanitizeManualRotatingSelection(getRotatingItemCandidateIds(), getRotatingUpgradeCandidateIds());
        }
        return Collections.unmodifiableList(manualRotatingItemIds);
    }

    public List<String> getManualRotatingUpgradeIds() {
        if (bedwarsManager.getShopConfig() != null) {
            sanitizeManualRotatingSelection(getRotatingItemCandidateIds(), getRotatingUpgradeCandidateIds());
        }
        return Collections.unmodifiableList(manualRotatingUpgradeIds);
    }

    public boolean toggleManualRotatingItem(String id) {
        String normalized = normalizeItemId(id);
        if (normalized == null) {
            return false;
        }
        List<String> itemCandidates = getRotatingItemCandidateIds();
        List<String> upgradeCandidates = getRotatingUpgradeCandidateIds();
        sanitizeManualRotatingSelection(itemCandidates, upgradeCandidates);
        if (!itemCandidates.contains(normalized)) {
            return false;
        }
        if (manualRotatingItemIds.remove(normalized)) {
            syncManualRotatingSelection();
            return true;
        }
        manualRotatingItemIds.add(normalized);
        syncManualRotatingSelection();
        return true;
    }

    public boolean toggleManualRotatingUpgrade(String id) {
        String normalized = normalizeItemId(id);
        if (normalized == null) {
            return false;
        }
        List<String> itemCandidates = getRotatingItemCandidateIds();
        List<String> upgradeCandidates = getRotatingUpgradeCandidateIds();
        sanitizeManualRotatingSelection(itemCandidates, upgradeCandidates);
        if (!upgradeCandidates.contains(normalized)) {
            return false;
        }
        if (manualRotatingUpgradeIds.remove(normalized)) {
            syncManualRotatingSelection();
            return true;
        }
        manualRotatingUpgradeIds.add(normalized);
        syncManualRotatingSelection();
        return true;
    }

    public List<String> getRotatingItemCandidateIds() {
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return List.of();
        }
        List<String> candidates = new ArrayList<>();
        addRotatingItemCandidates(candidates, config.getCategory(ShopCategoryType.ROTATING), config);
        return candidates;
    }

    public List<String> getRotatingUpgradeCandidateIds() {
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return List.of();
        }
        List<String> candidates = new ArrayList<>();
        addRotatingUpgradeCandidates(candidates, config.getCategory(ShopCategoryType.ROTATING_UPGRADES), config);
        if (candidates.isEmpty()) {
            addRotatingUpgradeCandidates(candidates, config.getCategory(ShopCategoryType.ROTATING), config);
        }
        return candidates;
    }

    private void sanitizeManualRotatingSelection(List<String> itemCandidates, List<String> upgradeCandidates) {
        Set<String> validItems = new HashSet<>(itemCandidates);
        LinkedHashSet<String> sanitizedItems = new LinkedHashSet<>();
        for (String id : manualRotatingItemIds) {
            String normalized = normalizeItemId(id);
            if (normalized == null || !validItems.contains(normalized)) {
                continue;
            }
            sanitizedItems.add(normalized);
        }
        manualRotatingItemIds.clear();
        manualRotatingItemIds.addAll(sanitizedItems);

        Set<String> validUpgrades = new HashSet<>(upgradeCandidates);
        LinkedHashSet<String> sanitizedUpgrades = new LinkedHashSet<>();
        for (String id : manualRotatingUpgradeIds) {
            String normalized = normalizeItemId(id);
            if (normalized == null || !validUpgrades.contains(normalized)) {
                continue;
            }
            sanitizedUpgrades.add(normalized);
        }
        manualRotatingUpgradeIds.clear();
        manualRotatingUpgradeIds.addAll(sanitizedUpgrades);
    }

    private void addRotatingItemCandidates(List<String> candidates,
                                           krispasi.omGames.bedwars.shop.ShopCategory category,
                                           ShopConfig config) {
        if (category == null || category.getEntries().isEmpty()) {
            return;
        }
        for (String id : category.getEntries().values()) {
            String normalized = normalizeItemId(id);
            ShopItemDefinition definition = config != null && normalized != null ? config.getItem(normalized) : null;
            if (normalized != null
                    && definition != null
                    && definition.getBehavior() != ShopItemBehavior.UPGRADE
                    && !candidates.contains(normalized)) {
                candidates.add(normalized);
            }
        }
    }

    private void addRotatingUpgradeCandidates(List<String> candidates,
                                              krispasi.omGames.bedwars.shop.ShopCategory category,
                                              ShopConfig config) {
        if (category == null || category.getEntries().isEmpty()) {
            return;
        }
        for (String id : category.getEntries().values()) {
            String normalized = normalizeItemId(id);
            ShopItemDefinition definition = config != null && normalized != null ? config.getItem(normalized) : null;
            if (normalized != null
                    && definition != null
                    && definition.getBehavior() == ShopItemBehavior.UPGRADE
                    && !candidates.contains(normalized)) {
                candidates.add(normalized);
            }
        }
    }

    public boolean isRotatingItemAvailable(ShopItemDefinition item) {
        if (item == null) {
            return false;
        }
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return true;
        }
        krispasi.omGames.bedwars.shop.ShopCategory category = config.getCategory(ShopCategoryType.ROTATING);
        if (category == null || category.getEntries().isEmpty()) {
            return true;
        }
        boolean rotating = category.getEntries().containsValue(item.getId());
        if (!rotating) {
            return true;
        }
        if (suddenDeathActive && item.isDisabledAfterSuddenDeath()) {
            return false;
        }
        if (rotatingMode == RotatingSelectionMode.MANUAL) {
            return rotatingItemIds.contains(item.getId());
        }
        if (rotatingItemIds.isEmpty()) {
            return true;
        }
        return rotatingItemIds.contains(item.getId());
    }

    public boolean isRotatingUpgradeAvailable(TeamUpgradeType type) {
        if (type == null) {
            return true;
        }
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return true;
        }
        krispasi.omGames.bedwars.shop.ShopCategory category = config.getCategory(ShopCategoryType.ROTATING_UPGRADES);
        if ((category == null || category.getEntries().isEmpty())
                && config.getCategory(ShopCategoryType.ROTATING) != null) {
            category = config.getCategory(ShopCategoryType.ROTATING);
        }
        if (category == null || category.getEntries().isEmpty()) {
            return true;
        }
        boolean hasRotating = false;
        for (String id : category.getEntries().values()) {
            ShopItemDefinition definition = config.getItem(id);
            if (definition == null || definition.getBehavior() != ShopItemBehavior.UPGRADE) {
                continue;
            }
            if (definition.getUpgradeType() != type) {
                continue;
            }
            hasRotating = true;
            boolean selected = rotatingMode == RotatingSelectionMode.MANUAL
                    ? rotatingUpgradeIds.contains(definition.getId())
                    : rotatingUpgradeIds.isEmpty() || rotatingUpgradeIds.contains(definition.getId());
            if (!selected) {
                continue;
            }
            if (suddenDeathActive && definition.isDisabledAfterSuddenDeath()) {
                return false;
            }
            return true;
        }
        return !hasRotating;
    }

    public ShopItemDefinition getRotatingUpgradeDefinition(TeamUpgradeType type) {
        if (type == null) {
            return null;
        }
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return null;
        }
        ShopItemDefinition definition = findRotatingUpgradeDefinition(config,
                config.getCategory(ShopCategoryType.ROTATING_UPGRADES),
                type);
        if (definition != null) {
            return definition;
        }
        return findRotatingUpgradeDefinition(config, config.getCategory(ShopCategoryType.ROTATING), type);
    }

    public boolean isRotatingTrapAvailable(TrapType trap) {
        if (trap == null) {
            return false;
        }
        String itemId = normalizeItemId(trap.rotatingUpgradeItemId());
        if (itemId == null) {
            return true;
        }
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null || !isListedInRotatingUpgradeCategories(config, itemId)) {
            return false;
        }
        ShopItemDefinition definition = config.getItem(itemId);
        if (definition == null) {
            return false;
        }
        if (suddenDeathActive && definition.isDisabledAfterSuddenDeath()) {
            return false;
        }
        return isRotatingUpgradeEntrySelected(itemId);
    }

    public ShopItemDefinition getRotatingTrapDefinition(TrapType trap) {
        if (trap == null) {
            return null;
        }
        String itemId = normalizeItemId(trap.rotatingUpgradeItemId());
        if (itemId == null) {
            return null;
        }
        ShopConfig config = bedwarsManager.getShopConfig();
        return config != null ? config.getItem(itemId) : null;
    }

    private ShopItemDefinition findRotatingUpgradeDefinition(ShopConfig config,
                                                             krispasi.omGames.bedwars.shop.ShopCategory category,
                                                             TeamUpgradeType type) {
        if (config == null || category == null || type == null) {
            return null;
        }
        for (String id : category.getEntries().values()) {
            ShopItemDefinition definition = config.getItem(id);
            if (definition == null || definition.getBehavior() != ShopItemBehavior.UPGRADE) {
                continue;
            }
            if (definition.getUpgradeType() == type) {
                return definition;
            }
        }
        return null;
    }

    private boolean isListedInRotatingUpgradeCategories(ShopConfig config, String itemId) {
        if (config == null || itemId == null) {
            return false;
        }
        krispasi.omGames.bedwars.shop.ShopCategory upgradeCategory = config.getCategory(ShopCategoryType.ROTATING_UPGRADES);
        if (upgradeCategory != null && upgradeCategory.getEntries().containsValue(itemId)) {
            return true;
        }
        krispasi.omGames.bedwars.shop.ShopCategory rotatingCategory = config.getCategory(ShopCategoryType.ROTATING);
        return rotatingCategory != null && rotatingCategory.getEntries().containsValue(itemId);
    }

    private boolean isRotatingUpgradeEntrySelected(String itemId) {
        if (itemId == null) {
            return false;
        }
        if (rotatingMode == RotatingSelectionMode.MANUAL) {
            return rotatingUpgradeIds.contains(itemId);
        }
        return rotatingUpgradeIds.isEmpty() || rotatingUpgradeIds.contains(itemId);
    }

    private void rollRotatingItems() {
        rotatingItemIds.clear();
        rotatingUpgradeIds.clear();
        List<String> itemCandidates = getRotatingItemCandidateIds();
        List<String> upgradeCandidates = getRotatingUpgradeCandidateIds();
        sanitizeManualRotatingSelection(itemCandidates, upgradeCandidates);
        if (rotatingMode == RotatingSelectionMode.MANUAL) {
            for (String id : manualRotatingItemIds) {
                String normalized = normalizeItemId(id);
                if (normalized != null && itemCandidates.contains(normalized)) {
                    rotatingItemIds.add(normalized);
                }
            }
            for (String id : manualRotatingUpgradeIds) {
                String normalized = normalizeItemId(id);
                if (normalized != null && upgradeCandidates.contains(normalized)) {
                    rotatingUpgradeIds.add(normalized);
                }
            }
            return;
        }
        rotatingItemIds.addAll(pickRandom(itemCandidates, 2));
        rotatingUpgradeIds.addAll(pickRandom(upgradeCandidates, 1));
    }

    private void announceCurrentRotatingItems() {
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return;
        }
        if (getRotatingItemCandidateIds().isEmpty() && getRotatingUpgradeCandidateIds().isEmpty()) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (String id : rotatingItemIds) {
            names.add(resolveRotatingItemName(id, config.getItem(id)));
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        String itemText = names.isEmpty() ? "none" : String.join(", ", names);
        List<String> upgradeNames = new ArrayList<>();
        for (String id : rotatingUpgradeIds) {
            upgradeNames.add(resolveRotatingItemName(id, config.getItem(id)));
        }
        upgradeNames.sort(String.CASE_INSENSITIVE_ORDER);
        String upgradeText = upgradeNames.isEmpty() ? "none" : String.join(", ", upgradeNames);
        broadcast(Component.text("Rotation Items: ", NamedTextColor.AQUA)
                .append(Component.text(itemText, NamedTextColor.YELLOW))
                .append(Component.text(" | Upgrades: ", NamedTextColor.AQUA))
                .append(Component.text(upgradeText, NamedTextColor.YELLOW)));
    }

    private String resolveRotatingItemName(String id, ShopItemDefinition definition) {
        if (definition != null) {
            String displayName = definition.getDisplayName();
            if (displayName != null && !displayName.isBlank()) {
                return displayName;
            }
            TeamUpgradeType upgradeType = definition.getUpgradeType();
            if (upgradeType != null) {
                return upgradeType.displayName();
            }
        }
        return humanizeRotatingId(id);
    }

    private String humanizeRotatingId(String id) {
        if (id == null || id.isBlank()) {
            return "Unknown";
        }
        String normalized = id.replace('-', ' ').replace('_', ' ').trim();
        if (normalized.isBlank()) {
            return id;
        }
        String[] parts = normalized.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            String lower = part.toLowerCase(Locale.ROOT);
            builder.append(Character.toUpperCase(lower.charAt(0)));
            if (lower.length() > 1) {
                builder.append(lower.substring(1));
            }
        }
        return builder.length() > 0 ? builder.toString() : id;
    }

    private List<String> pickRandom(List<String> candidates, int count) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        int target = Math.max(1, Math.min(count, candidates.size()));
        if (candidates.size() <= target) {
            return new ArrayList<>(candidates);
        }
        List<String> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled);
        return new ArrayList<>(shuffled.subList(0, target));
    }

    private boolean isRotatingItemCandidate(String id) {
        String normalized = normalizeItemId(id);
        if (normalized == null) {
            return false;
        }
        return getRotatingItemCandidateIds().contains(normalized);
    }

    private void syncManualRotatingSelection() {
        if (rotatingMode != RotatingSelectionMode.MANUAL) {
            return;
        }
        if (bedwarsManager.getShopConfig() != null) {
            sanitizeManualRotatingSelection(getRotatingItemCandidateIds(), getRotatingUpgradeCandidateIds());
        }
        rotatingItemIds.clear();
        rotatingUpgradeIds.clear();
        for (String id : manualRotatingItemIds) {
            String normalized = normalizeItemId(id);
            if (normalized != null) {
                rotatingItemIds.add(normalized);
            }
        }
        for (String id : manualRotatingUpgradeIds) {
            String normalized = normalizeItemId(id);
            if (normalized != null) {
                rotatingUpgradeIds.add(normalized);
            }
        }
    }

    private String normalizeItemId(String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    public int getRemainingLimit(UUID playerId, ShopItemDefinition item) {
        if (playerId == null || item == null) {
            return -1;
        }
        ShopItemLimit limit = item.getLimit();
        if (limit == null || !limit.isValid()) {
            return -1;
        }
        int current = 0;
        if (limit.scope() == ShopItemLimitScope.PLAYER) {
            Map<String, Integer> counts = playerPurchaseCounts.get(playerId);
            current = counts != null ? counts.getOrDefault(item.getId(), 0) : 0;
        } else if (limit.scope() == ShopItemLimitScope.TEAM) {
            TeamColor team = getTeam(playerId);
            if (team == null) {
                return 0;
            }
            Map<String, Integer> counts = teamPurchaseCounts.get(team);
            current = counts != null ? counts.getOrDefault(item.getId(), 0) : 0;
        }
        int remaining = limit.amount() - current;
        return Math.max(0, remaining);
    }

    private void closeFakeEnderChests() {
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            Inventory open = player.getOpenInventory().getTopInventory();
            if (fakeEnderChests.containsValue(open)) {
                player.closeInventory();
            }
        }
        for (Inventory inventory : fakeEnderChests.values()) {
            inventory.clear();
        }
    }

    private void showTitleAll(Component title, Component subtitle) {
        Title message = Title.title(title, subtitle, DEFAULT_TITLE_TIMES);
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.showTitle(message);
            }
        }
    }

    private void showTitle(Player player, Component title, Component subtitle) {
        Title message = Title.title(title, subtitle, DEFAULT_TITLE_TIMES);
        player.showTitle(message);
    }

    private void broadcast(Component message) {
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private void setSpectator(Player player) {
        player.getInventory().clear();
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
        clearUpgradeEffects(player);
        updateSidebarForPlayer(player);
    }

    public boolean hasRespawnProtection(UUID playerId) {
        Long end = respawnProtectionEnds.get(playerId);
        if (end == null) {
            return false;
        }
        if (System.currentTimeMillis() >= end) {
            removeRespawnProtection(playerId);
            return false;
        }
        return true;
    }

    public void removeRespawnProtection(UUID playerId) {
        respawnProtectionEnds.remove(playerId);
        BukkitTask task = respawnProtectionTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private void grantRespawnProtection(Player player) {
        UUID playerId = player.getUniqueId();
        removeRespawnProtection(playerId);
        respawnProtectionEnds.put(playerId,
                System.currentTimeMillis() + RESPAWN_PROTECTION_SECONDS * 1000L);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> removeRespawnProtection(playerId),
                RESPAWN_PROTECTION_SECONDS * 20L);
        respawnProtectionTasks.put(playerId, task);
    }

    private void giveStarterKit(Player player, TeamColor team) {
        ItemStack sword = new ItemStack(Material.WOODEN_SWORD);
        player.getInventory().addItem(sword);
        equipBaseArmor(player, team);
    }

    private void giveTeamSelectItem(Player player) {
        ItemStack item = new ItemStack(Material.RED_BED);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Pick a Team", NamedTextColor.YELLOW));
        meta.lore(List.of(
                Component.text("Right click to choose your team.", NamedTextColor.GRAY)
        ));
        TeamSelectItemData.apply(meta);
        item.setItemMeta(meta);
        placeLobbyItem(player, item, 4);
    }

    private void giveLobbyControlItems(Player player) {
        if (player == null) {
            return;
        }
        ItemStack manage = new ItemStack(Material.WHITE_BED);
        ItemMeta manageMeta = manage.getItemMeta();
        manageMeta.displayName(Component.text("Manage Teams", NamedTextColor.YELLOW));
        manageMeta.lore(List.of(
                Component.text("Right click to edit teams.", NamedTextColor.GRAY)
        ));
        krispasi.omGames.bedwars.item.LobbyControlItemData.apply(manageMeta, "manage");
        manage.setItemMeta(manageMeta);

        ItemStack pause = new ItemStack(Material.RED_CONCRETE);
        ItemMeta pauseMeta = pause.getItemMeta();
        pauseMeta.displayName(Component.text("Pause Timer", NamedTextColor.RED));
        pauseMeta.lore(List.of(
                Component.text("Right click to pause/resume.", NamedTextColor.GRAY)
        ));
        krispasi.omGames.bedwars.item.LobbyControlItemData.apply(pauseMeta, "pause");
        pause.setItemMeta(pauseMeta);

        ItemStack skip = new ItemStack(Material.GREEN_CONCRETE);
        ItemMeta skipMeta = skip.getItemMeta();
        skipMeta.displayName(Component.text("Skip Timer", NamedTextColor.GREEN));
        skipMeta.lore(List.of(
                Component.text("Right click to start.", NamedTextColor.GRAY)
        ));
        krispasi.omGames.bedwars.item.LobbyControlItemData.apply(skipMeta, "skip");
        skip.setItemMeta(skipMeta);

        placeLobbyItem(player, manage, 0);
        placeLobbyItem(player, pause, 1);
        placeLobbyItem(player, skip, 2);
    }

    private void placeLobbyItem(Player player, ItemStack item, int slot) {
        if (player == null || item == null) {
            return;
        }
        if (slot >= 0 && slot < 9 && player.getInventory().getItem(slot) == null) {
            player.getInventory().setItem(slot, item);
            return;
        }
        player.getInventory().addItem(item);
    }

    private ItemStack colorLeatherArmor(Material material, org.bukkit.Color color) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(color);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }

    private void applyPermanentItems(Player player, TeamColor team) {
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null || team == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        int armorTier = armorTiers.getOrDefault(playerId, 0);
        equipBaseArmor(player, team);
        if (armorTier > 0) {
            equipArmor(player, team, config, armorTier);
        }
        int pickaxeTier = pickaxeTiers.getOrDefault(playerId, 0);
        if (pickaxeTier > 0) {
            equipTieredItem(player, team, config, ShopItemBehavior.PICKAXE, pickaxeTier, PICKAXE_MATERIALS);
        }
        int axeTier = axeTiers.getOrDefault(playerId, 0);
        if (axeTier > 0) {
            equipTieredItem(player, team, config, ShopItemBehavior.AXE, axeTier, AXE_MATERIALS);
        }
        if (shearsUnlocked.contains(playerId)) {
            giveShears(player, team);
        }
        if (shieldUnlocked.contains(playerId)) {
            giveShield(player, team);
        }
        applyTeamUpgrades(player, team);
        player.updateInventory();
    }

    private void applyPermanentItemsWithShield(Player player, TeamColor team) {
        applyPermanentItems(player, team);
        equipShieldOffhand(player, team);
    }

    private void equipBaseArmor(Player player, TeamColor team) {
        if (team == null) {
            return;
        }
        org.bukkit.Color color = team.dyeColor().getColor();
        player.getInventory().setHelmet(colorLeatherArmor(Material.LEATHER_HELMET, color));
        player.getInventory().setChestplate(colorLeatherArmor(Material.LEATHER_CHESTPLATE, color));
        player.getInventory().setLeggings(colorLeatherArmor(Material.LEATHER_LEGGINGS, color));
        player.getInventory().setBoots(colorLeatherArmor(Material.LEATHER_BOOTS, color));
    }

    private boolean applyTierUpgrade(Player player,
                                     ShopItemDefinition item,
                                     Map<UUID, Integer> tierMap,
                                     ShopItemBehavior behavior) {
        int tier = item.getTier();
        if (tier <= 0) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        int current = tierMap.getOrDefault(playerId, 0);
        if (behavior == ShopItemBehavior.PICKAXE || behavior == ShopItemBehavior.AXE) {
            if (tier != current + 1) {
                return false;
            }
        } else if (tier <= current) {
            return false;
        }
        tierMap.put(playerId, tier);
        applyPermanentItems(player, getTeam(playerId));
        return true;
    }

    private boolean applyShears(Player player, ShopItemDefinition item, TeamColor team) {
        UUID playerId = player.getUniqueId();
        if (shearsUnlocked.contains(playerId)) {
            return false;
        }
        shearsUnlocked.add(playerId);
        giveItem(player, item.createPurchaseItem(team));
        applyTeamUpgrades(player, team);
        return true;
    }

    private boolean applyShield(Player player) {
        UUID playerId = player.getUniqueId();
        if (shieldUnlocked.contains(playerId)) {
            return false;
        }
        shieldUnlocked.add(playerId);
        return true;
    }

    private void equipTieredItem(Player player,
                                 TeamColor team,
                                 ShopConfig config,
                                 ShopItemBehavior behavior,
                                 int tier,
                                 Set<Material> removeSet) {
        ShopItemDefinition definition = config.getTieredItem(behavior, tier).orElse(null);
        if (definition == null) {
            return;
        }
        removeItems(player, removeSet);
        giveItem(player, definition.createPurchaseItem(team));
    }

    private int resolveToolTier(Player player, ShopConfig config, ShopItemBehavior behavior) {
        if (player == null || config == null) {
            return 0;
        }
        Map<Integer, ShopItemDefinition> tiered = config.getTieredItems(behavior);
        if (tiered == null || tiered.isEmpty()) {
            return 0;
        }
        Map<Material, Integer> materialTiers = new EnumMap<>(Material.class);
        for (Map.Entry<Integer, ShopItemDefinition> entry : tiered.entrySet()) {
            Integer tier = entry.getKey();
            ShopItemDefinition definition = entry.getValue();
            if (tier == null || tier <= 0 || definition == null) {
                continue;
            }
            Material material = definition.getMaterial();
            if (material == null) {
                continue;
            }
            materialTiers.merge(material, tier, Math::max);
        }
        if (materialTiers.isEmpty()) {
            return 0;
        }
        int foundTier = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null) {
                continue;
            }
            Integer tier = materialTiers.get(item.getType());
            if (tier != null) {
                foundTier = Math.max(foundTier, tier);
            }
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null) {
            Integer tier = materialTiers.get(main.getType());
            if (tier != null) {
                foundTier = Math.max(foundTier, tier);
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null) {
            Integer tier = materialTiers.get(offhand.getType());
            if (tier != null) {
                foundTier = Math.max(foundTier, tier);
            }
        }
        return foundTier;
    }

    private void updateToolTier(Map<UUID, Integer> map, UUID playerId, int tier) {
        if (playerId == null) {
            return;
        }
        if (tier <= 0) {
            map.remove(playerId);
        } else {
            map.put(playerId, tier);
        }
    }

    private boolean hasInventoryItem(Player player, Material material) {
        if (player == null || material == null) {
            return false;
        }
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) {
                return true;
            }
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && main.getType() == material) {
            return true;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        return offhand != null && offhand.getType() == material;
    }

    private void equipArmor(Player player, TeamColor team, ShopConfig config, int tier) {
        ShopItemDefinition definition = config.getTieredItem(ShopItemBehavior.ARMOR, tier).orElse(null);
        if (definition == null) {
            return;
        }
        String base = armorBase(definition.getMaterial());
        Material leggings = Material.matchMaterial(base + "_LEGGINGS");
        Material boots = Material.matchMaterial(base + "_BOOTS");
        if (leggings == null || boots == null) {
            return;
        }
        org.bukkit.Color color = team.dyeColor().getColor();
        player.getInventory().setHelmet(colorLeatherArmor(Material.LEATHER_HELMET, color));
        player.getInventory().setChestplate(colorLeatherArmor(Material.LEATHER_CHESTPLATE, color));
        if ("LEATHER".equals(base)) {
            player.getInventory().setLeggings(colorLeatherArmor(leggings, color));
            player.getInventory().setBoots(colorLeatherArmor(boots, color));
        } else {
            player.getInventory().setLeggings(makeUnbreakable(new ItemStack(leggings)));
            player.getInventory().setBoots(makeUnbreakable(new ItemStack(boots)));
        }
    }

    private ItemStack makeUnbreakable(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }

    private String armorBase(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET")) {
            return name.substring(0, name.length() - "_HELMET".length());
        }
        if (name.endsWith("_CHESTPLATE")) {
            return name.substring(0, name.length() - "_CHESTPLATE".length());
        }
        if (name.endsWith("_LEGGINGS")) {
            return name.substring(0, name.length() - "_LEGGINGS".length());
        }
        if (name.endsWith("_BOOTS")) {
            return name.substring(0, name.length() - "_BOOTS".length());
        }
        return name;
    }

    private void giveShears(Player player, TeamColor team) {
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return;
        }
        if (player.getInventory().contains(Material.SHEARS)) {
            return;
        }
        ShopItemDefinition definition = config.getFirstByBehavior(ShopItemBehavior.SHEARS);
        if (definition != null) {
            giveItem(player, definition.createPurchaseItem(team));
        } else {
            giveItem(player, new ItemStack(Material.SHEARS));
        }
    }

    private void giveShield(Player player, TeamColor team) {
        if (countItem(player, Material.SHIELD) > 0) {
            return;
        }
        ItemStack shield = createShieldItem(team);
        if (shield != null) {
            giveItem(player, shield);
        }
    }

    private ItemStack createShieldItem(TeamColor team) {
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return new ItemStack(Material.SHIELD);
        }
        ShopItemDefinition definition = config.getFirstByBehavior(ShopItemBehavior.SHIELD);
        if (definition != null) {
            return definition.createPurchaseItem(team);
        }
        return new ItemStack(Material.SHIELD);
    }

    private void equipShieldOffhand(Player player, TeamColor team) {
        if (player == null) {
            return;
        }
        if (!shieldUnlocked.contains(player.getUniqueId())) {
            return;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() == Material.SHIELD) {
            return;
        }
        ItemStack shield = removeShieldFromStorage(player);
        if (shield == null) {
            shield = createShieldItem(team);
        }
        if (shield == null) {
            shield = new ItemStack(Material.SHIELD);
        }
        if (offhand != null && !offhand.getType().isAir()) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(offhand);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        player.getInventory().setItemInOffHand(shield);
    }

    private ItemStack removeShieldFromStorage(Player player) {
        if (player == null) {
            return null;
        }
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != Material.SHIELD) {
                continue;
            }
            contents[i] = null;
            player.getInventory().setStorageContents(contents);
            return item;
        }
        return null;
    }

    private void downgradeTools(UUID playerId) {
        downgradeTier(pickaxeTiers, playerId);
        downgradeTier(axeTiers, playerId);
    }

    private void downgradeTier(Map<UUID, Integer> map, UUID playerId) {
        int tier = map.getOrDefault(playerId, 0);
        if (tier > 1) {
            map.put(playerId, tier - 1);
        }
    }

    private boolean hasResources(Player player, ShopCost cost) {
        if (cost == null || !cost.isValid()) {
            return true;
        }
        int remaining = cost.amount();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() != cost.material()) {
                continue;
            }
            remaining -= item.getAmount();
            if (remaining <= 0) {
                return true;
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() == cost.material()) {
            remaining -= offhand.getAmount();
        }
        return remaining <= 0;
    }

    private int countItem(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() == material) {
            count += offhand.getAmount();
        }
        return count;
    }

    private void removeResources(Player player, ShopCost cost) {
        if (cost == null || !cost.isValid()) {
            return;
        }
        int remaining = cost.amount();
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != cost.material()) {
                continue;
            }
            int amount = item.getAmount();
            if (amount <= remaining) {
                remaining -= amount;
                contents[i] = null;
            } else {
                item.setAmount(amount - remaining);
                remaining = 0;
            }
            if (remaining <= 0) {
                break;
            }
        }
        player.getInventory().setStorageContents(contents);
        if (remaining > 0) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (offhand != null && offhand.getType() == cost.material()) {
                int amount = offhand.getAmount();
                if (amount <= remaining) {
                    player.getInventory().setItemInOffHand(null);
                } else {
                    offhand.setAmount(amount - remaining);
                }
            }
        }
    }

    private void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void removeSwords(Player player) {
        removeItems(player, SWORD_MATERIALS);
    }

    private void removeItems(Player player, Set<Material> materials) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && materials.contains(item.getType())) {
                contents[i] = null;
            }
        }
        player.getInventory().setStorageContents(contents);
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && materials.contains(offhand.getType())) {
            player.getInventory().setItemInOffHand(null);
        }
    }

    public void handleWorldChange(Player player) {
        if (!isInArenaWorld(player.getWorld())) {
            clearElytraStrike(player, false, false);
            clearUpgradeEffects(player);
        } else if (isParticipant(player.getUniqueId())) {
            applyTeamUpgrades(player, getTeam(player.getUniqueId()));
        }
        updateSidebarForPlayer(player);
    }

    public void handlePlayerQuit(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        TeamColor team = getTeam(playerId);
        cancelRespawnCountdown(playerId);
        removeRespawnProtection(playerId);
        clearTrapImmunity(playerId);
        BukkitTask task = respawnTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        clearElytraStrike(player, false, false);
        clearUpgradeEffects(player);
        flushPendingPartyExp(player);
        if (state == GameState.RUNNING
                && team != null
                && isParticipant(playerId)
                && getBedState(team) == BedState.DESTROYED) {
            removeParticipant(player);
            checkTeamEliminated(team);
            return;
        }
        restoreSidebar(playerId);
    }

    public void handlePlayerJoin(Player player) {
        if (!isActive()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (isLockedCommandSpectator(playerId)) {
            Location spectate = resolveMapLobbyLocation();
            setSpectator(player);
            if (spectate != null) {
                player.teleport(spectate);
            }
            hideEditorsFrom(player);
            updateSidebarForPlayer(player);
            return;
        }
        if (!isParticipant(playerId)) {
            return;
        }
        if (state == GameState.LOBBY) {
            Location lobby = resolveMapLobbyLocation();
            if (lobby != null) {
                player.teleport(lobby);
            }
            player.getInventory().clear();
            if (isLobbyInitiator(playerId)) {
                giveLobbyControlItems(player);
            }
            if (teamPickEnabled) {
                giveTeamSelectItem(player);
            }
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            applyLobbyBuffs(player);
            syncToolTiers(player);
            hideEditorsFrom(player);
            updateSidebarForPlayer(player);
            return;
        }

        TeamColor team = getTeam(playerId);
        if (team == null) {
            return;
        }
        if (state == GameState.STARTING) {
            Location spawn = arena.getSpawn(team);
            if (spawn != null) {
                player.teleport(spawn);
            }
            player.getInventory().clear();
            giveStarterKit(player, team);
            applyPermanentItemsWithShield(player, team);
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            frozenPlayers.add(playerId);
            syncToolTiers(player);
            hideEditorsFrom(player);
            updateSidebarForPlayer(player);
            return;
        }

        if (state != GameState.RUNNING) {
            return;
        }

        Location lobby = resolveMapLobbyLocation();
        if (eliminatedPlayers.contains(playerId)) {
            setSpectator(player);
            if (lobby != null) {
                player.teleport(lobby);
            }
            syncToolTiers(player);
            hideEditorsFrom(player);
            updateSidebarForPlayer(player);
            return;
        }

        if (pendingRespawns.contains(playerId)) {
            setSpectator(player);
            if (lobby != null) {
                player.teleport(lobby);
            }
            boolean allowRespawnAfterBedBreak = respawnGracePlayers.contains(playerId);
            if (getBedState(team) == BedState.DESTROYED && !allowRespawnAfterBedBreak) {
                pendingRespawns.remove(playerId);
                awardPendingDeathFinalStats(playerId);
                eliminatePlayer(player, team);
                syncToolTiers(player);
                hideEditorsFrom(player);
                updateSidebarForPlayer(player);
                return;
            }
            scheduleRespawn(player, team, RESPAWN_DELAY_SECONDS, allowRespawnAfterBedBreak);
            syncToolTiers(player);
            hideEditorsFrom(player);
            updateSidebarForPlayer(player);
            return;
        }

        Location spawn = arena.getSpawn(team);
        if (spawn != null) {
            player.teleport(spawn);
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        grantRespawnProtection(player);
        applyPermanentItemsWithShield(player, team);
        syncToolTiers(player);
        hideEditorsFrom(player);
        updateSidebarForPlayer(player);
    }

    private void startSidebarUpdates() {
        if (sidebarTask != null) {
            sidebarTask.cancel();
        }
        sidebarTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> safeRun("sidebarUpdate", this::updateSidebars),
                0L,
                20L);
        tasks.add(sidebarTask);
    }

    private void updateSidebars() {
        Set<UUID> updated = new HashSet<>();
        if (state == GameState.LOBBY) {
            applyLobbyBuffsToLobbyPlayers();
        }
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                updated.add(playerId);
                updateSidebarForPlayer(player);
            }
        }
        World world = arena.getWorld();
        if (world != null) {
            for (Player player : world.getPlayers()) {
                UUID playerId = player.getUniqueId();
                if (!updated.add(playerId)) {
                    continue;
                }
                updateSidebarForPlayer(player);
            }
        }
        for (UUID playerId : new HashSet<>(activeScoreboards.keySet())) {
            if (!updated.add(playerId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                updateSidebarForPlayer(player);
            } else {
                restoreSidebar(playerId);
            }
        }
    }

    private void updateSidebarForPlayer(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!shouldShowSidebar(player)) {
            restoreSidebar(playerId);
            return;
        }
        Scoreboard scoreboard = activeScoreboards.get(playerId);
        if (scoreboard == null) {
            previousScoreboards.putIfAbsent(playerId, player.getScoreboard());
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            activeScoreboards.put(playerId, scoreboard);
        }
        ensureSidebarObjective(scoreboard);
        ensureHealthObjective(scoreboard);
        player.setScoreboard(scoreboard);
        updateTeamColors(scoreboard);
        updateSidebarLines(player, scoreboard);
        updateBelowNameHealth(scoreboard);
    }

    public void refreshSidebar(Player player) {
        updateSidebarForPlayer(player);
    }

    private boolean shouldShowSidebar(Player player) {
        if (player == null || !isActive() || !isInArenaWorld(player.getWorld())) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        return isParticipant(playerId) || isSessionSpectator(player);
    }

    private boolean isSessionSpectator(Player player) {
        return player != null
                && player.getGameMode() == GameMode.SPECTATOR
                && !isEditor(player.getUniqueId());
    }

    private void restoreSidebar(UUID playerId) {
        Scoreboard previous = previousScoreboards.remove(playerId);
        Scoreboard active = activeScoreboards.remove(playerId);
        List<String> lines = sidebarLines.remove(playerId);
        if (active != null && lines != null) {
            for (String line : lines) {
                active.resetScores(line);
            }
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.setScoreboard(previous != null
                    ? previous
                    : Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private void clearSidebars() {
        for (UUID playerId : new HashSet<>(activeScoreboards.keySet())) {
            restoreSidebar(playerId);
        }
    }

    private void updateSidebarLines(Player player, Scoreboard scoreboard) {
        Objective objective = ensureSidebarObjective(scoreboard);
        List<String> previous = sidebarLines.get(player.getUniqueId());
        if (previous != null) {
            for (String line : previous) {
                scoreboard.resetScores(line);
            }
        }
        List<String> lines = buildSidebarLines(player);
        int score = lines.size();
        for (String line : lines) {
            objective.getScore(line).setScore(score--);
        }
        sidebarLines.put(player.getUniqueId(), lines);
    }

    private Objective ensureSidebarObjective(Scoreboard scoreboard) {
        Objective objective = scoreboard.getObjective(SIDEBAR_OBJECTIVE_ID);
        if (objective == null) {
            objective = scoreboard.registerNewObjective(
                    SIDEBAR_OBJECTIVE_ID,
                    "dummy",
                    Component.text("BED WARS", NamedTextColor.GOLD)
            );
        }
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        return objective;
    }

    private Objective ensureHealthObjective(Scoreboard scoreboard) {
        Objective objective = scoreboard.getObjective(HEALTH_OBJECTIVE_ID);
        if (objective == null) {
            objective = scoreboard.registerNewObjective(
                    HEALTH_OBJECTIVE_ID,
                    "dummy",
                    Component.text("HP", NamedTextColor.RED)
            );
        }
        objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        return objective;
    }

    private void updateBelowNameHealth(Scoreboard scoreboard) {
        Objective objective = ensureHealthObjective(scoreboard);
        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            Player target = Bukkit.getPlayer(entry.getKey());
            String entryName = target != null ? target.getName() : null;
            if (entryName == null || entryName.isBlank()) {
                org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.getKey());
                entryName = offline != null ? offline.getName() : null;
            }
            if (entryName == null || entryName.isBlank()) {
                continue;
            }
            if (target == null || !target.isOnline() || !isInArenaWorld(target.getWorld())) {
                scoreboard.resetScores(entryName);
                continue;
            }
            int health = (int) Math.ceil(Math.max(0.0, target.getHealth()));
            objective.getScore(entryName).setScore(health);
        }
    }

    private void updateTeamColors(Scoreboard scoreboard) {
        for (Team team : new ArrayList<>(scoreboard.getTeams())) {
            if (team.getName().startsWith("bw_") && !teamsInMatch.contains(teamFromName(team.getName()))) {
                team.unregister();
            }
        }
        for (TeamColor teamColor : teamsInMatch) {
            String teamName = "bw_" + teamColor.key();
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
            }
            team.color(teamColor.textColor());
            team.setPrefix("");
            team.setSuffix("");
            for (String entry : new HashSet<>(team.getEntries())) {
                team.removeEntry(entry);
            }
        }
        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            TeamColor teamColor = entry.getValue();
            if (teamColor == null || !teamsInMatch.contains(teamColor)) {
                continue;
            }
            Player target = Bukkit.getPlayer(entry.getKey());
            if (target == null) {
                continue;
            }
            Team team = scoreboard.getTeam("bw_" + teamColor.key());
            if (team != null) {
                team.addEntry(target.getName());
            }
        }
    }

    private TeamColor teamFromName(String teamName) {
        if (teamName == null || !teamName.startsWith("bw_")) {
            return null;
        }
        return TeamColor.fromKey(teamName.substring(3));
    }

    private List<String> buildSidebarLines(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.DARK_GRAY + " ");
        lines.add(buildEventLine());
        lines.add(ChatColor.DARK_GRAY + "  ");

        TeamColor playerTeam = getTeam(player.getUniqueId());
        for (TeamColor team : teamsInMatch) {
            lines.add(buildTeamLine(team, playerTeam));
        }

        lines.add(ChatColor.DARK_GRAY + "   ");
        lines.add(ChatColor.GOLD + "Kills: " + ChatColor.WHITE + getKillCount(player.getUniqueId()));
        lines.add(ChatColor.AQUA + "Made by Krispasi");
        return lines;
    }

    private Location resolveMapLobbyLocation() {
        Location mapLobby = arena.getMapLobbyLocation();
        if (mapLobby != null) {
            return mapLobby;
        }
        return arena.getLobbyLocation();
    }

    private String buildEventLine() {
        if (state == GameState.LOBBY) {
            if (lobbyCountdownPaused) {
                return ChatColor.YELLOW + "Countdown Paused";
            }
            return ChatColor.YELLOW + "Starting in " + lobbyCountdownRemaining + "s";
        }
        if (state == GameState.STARTING) {
            return ChatColor.YELLOW + "Starting in " + startCountdownRemaining + "s";
        }
        if (state != GameState.RUNNING) {
            return ChatColor.GRAY + "Waiting...";
        }
        EventInfo info = getNextEventInfo();
        return ChatColor.AQUA + info.label() + ChatColor.GRAY + " in " + formatTime(info.secondsRemaining());
    }

    private String buildTeamLine(TeamColor team, TeamColor playerTeam) {
        String status = getTeamStatus(team);
        String you = team == playerTeam ? ChatColor.GRAY + " YOU" : "";
        return team.chatColor()
                + team.shortName()
                + " "
                + team.displayName()
                + ChatColor.WHITE
                + ": "
                + status
                + you;
    }

    private String getTeamStatus(TeamColor team) {
        if (getBedState(team) == BedState.ALIVE) {
            return ChatColor.GREEN + "Alive";
        }
        int alive = countAlivePlayers(team);
        if (alive > 0) {
            return ChatColor.YELLOW + String.valueOf(alive);
        }
        return ChatColor.RED + "X";
    }

    private int countAlivePlayers(TeamColor team) {
        int count = 0;
        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            if (entry.getValue() == team && !eliminatedPlayers.contains(entry.getKey())) {
                count++;
            }
        }
        return count;
    }

    private EventInfo getNextEventInfo() {
        long elapsedSeconds = matchStartMillis > 0
                ? (System.currentTimeMillis() - matchStartMillis) / 1000L
                : 0L;
        krispasi.omGames.bedwars.model.EventSettings events = arena.getEventSettings();
        if (!tier2Triggered) {
            return new EventInfo("Generators II", remainingSeconds(events.getTier2Delay(), elapsedSeconds));
        }
        if (!tier3Triggered) {
            return new EventInfo("Generators III", remainingSeconds(events.getTier3Delay(), elapsedSeconds));
        }
        if (!bedDestructionTriggered) {
            return new EventInfo("Beds Destroyed", remainingSeconds(events.getBedDestructionDelay(), elapsedSeconds));
        }
        if (!suddenDeathActive) {
            return new EventInfo("Sudden Death", remainingSeconds(events.getSuddenDeathDelay(), elapsedSeconds));
        }
        if (!gameEndTriggered) {
            return new EventInfo("Game End", remainingSeconds(events.getGameEndDelay(), elapsedSeconds));
        }
        return new EventInfo("Game End", 0);
    }

    private int remainingSeconds(int targetSeconds, long elapsedSeconds) {
        return (int) Math.max(0L, (long) targetSeconds - elapsedSeconds);
    }

    private String formatTime(int totalSeconds) {
        int minutes = Math.max(0, totalSeconds) / 60;
        int seconds = Math.max(0, totalSeconds) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private record UnstableTeleportResult(Location destination, Component message) {
    }

    private record EventInfo(String label, int secondsRemaining) {
    }

    private void startRespawnCountdown(Player player) {
        startRespawnCountdown(player, RESPAWN_DELAY_SECONDS);
    }

    private void startRespawnCountdown(Player player, int delaySeconds) {
        startRespawnCountdown(player, delaySeconds, null, null);
    }

    private void startRespawnCountdown(Player player,
                                       int delaySeconds,
                                       TeamColor beaconTeam,
                                       String revivedName) {
        BukkitTask task = new BukkitRunnable() {
            private int remaining = Math.max(0, delaySeconds);

            @Override
            public void run() {
                safeRun("respawnCountdown", () -> {
                    if (state != GameState.RUNNING || !player.isOnline()) {
                        cancelRespawnCountdown(player.getUniqueId());
                        return;
                    }
                    if (remaining <= 0) {
                        cancelRespawnCountdown(player.getUniqueId());
                        return;
                    }
                    player.sendActionBar(Component.text("Respawning in " + remaining + "s", NamedTextColor.YELLOW));
                    if (beaconTeam != null && revivedName != null && !revivedName.isBlank()) {
                        sendBeaconRespawnTimerToTeam(player.getUniqueId(), beaconTeam, revivedName, remaining);
                    }
                    remaining--;
                });
            }
        }.runTaskTimer(plugin, 0L, 20L);
        respawnCountdownTasks.put(player.getUniqueId(), task);
    }

    private void sendBeaconRespawnTimerToTeam(UUID revivedPlayerId,
                                              TeamColor team,
                                              String revivedName,
                                              int remainingSeconds) {
        if (team == null || revivedName == null || revivedName.isBlank()) {
            return;
        }
        Component timer = Component.text(revivedName + " respawns in " + remainingSeconds + "s", NamedTextColor.YELLOW);
        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            if (entry.getValue() != team || entry.getKey().equals(revivedPlayerId)) {
                continue;
            }
            Player teammate = Bukkit.getPlayer(entry.getKey());
            if (teammate != null && teammate.isOnline()) {
                teammate.sendActionBar(timer);
            }
        }
    }

    private void cancelRespawnCountdown(UUID playerId) {
        BukkitTask task = respawnCountdownTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private void safeRun(String context, Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            if (plugin != null) {
                plugin.getLogger().log(Level.SEVERE, "BedWars error in " + context, ex);
            } else {
                Bukkit.getLogger().log(Level.SEVERE, "BedWars error in " + context, ex);
            }
        }
    }

    private boolean isPartyExpEnabled() {
        return statsEnabled;
    }

    private void queuePartyExp(UUID playerId, int amount) {
        if (!isPartyExpEnabled() || playerId == null || amount <= 0 || !isParticipant(playerId)) {
            return;
        }
        pendingPartyExp.merge(playerId, amount, Integer::sum);
    }

    private void queuePartyExpForTeam(TeamColor team, int amount) {
        if (team == null || amount <= 0) {
            return;
        }
        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            if (team == entry.getValue()) {
                queuePartyExp(entry.getKey(), amount);
            }
        }
    }

    private void queuePartyExpForAllParticipants(int amount) {
        if (amount <= 0) {
            return;
        }
        for (UUID playerId : assignments.keySet()) {
            queuePartyExp(playerId, amount);
        }
    }

    private void updateMatchParticipationPartyExp(int targetAmount) {
        if (!isPartyExpEnabled()) {
            return;
        }
        int normalizedTarget = Math.max(0, targetAmount);
        if (normalizedTarget <= grantedMatchParticipationPartyExp) {
            return;
        }
        int delta = normalizedTarget - grantedMatchParticipationPartyExp;
        grantedMatchParticipationPartyExp = normalizedTarget;
        queuePartyExpForAllParticipants(delta);
    }

    private void finalizeMatchParticipationPartyExp() {
        if (!isPartyExpEnabled()) {
            return;
        }
        if (bedDestructionTriggered) {
            updateMatchParticipationPartyExp(PARTY_EXP_BED_GONE_MATCH);
            return;
        }
        if (matchStartMillis <= 0L) {
            return;
        }
        long elapsedMillis = System.currentTimeMillis() - matchStartMillis;
        if (elapsedMillis >= LONG_MATCH_PARTY_EXP_SECONDS * 1000L) {
            updateMatchParticipationPartyExp(PARTY_EXP_LONG_MATCH);
        }
    }

    private void queueFinalPlacementPartyExp(TeamColor winner) {
        if (!isPartyExpEnabled()) {
            return;
        }
        int totalTeams = teamsInMatch.size();
        if (totalTeams < 3) {
            return;
        }
        Map<TeamColor, Integer> placements = new EnumMap<>(TeamColor.class);
        for (int i = 0; i < eliminationOrder.size(); i++) {
            TeamColor team = eliminationOrder.get(i);
            if (teamsInMatch.contains(team)) {
                placements.put(team, totalTeams - i);
            }
        }
        if (winner != null && teamsInMatch.contains(winner)) {
            placements.put(winner, 1);
        }
        for (Map.Entry<TeamColor, Integer> entry : placements.entrySet()) {
            int amount = 0;
            if (entry.getValue() == 2) {
                amount = PARTY_EXP_SECOND_PLACE;
            } else if (entry.getValue() == 3 && totalTeams >= 4) {
                amount = PARTY_EXP_THIRD_PLACE;
            }
            queuePartyExpForTeam(entry.getKey(), amount);
        }
    }

    private void flushPendingPartyExpForOnlineParticipants() {
        if (!isPartyExpEnabled()) {
            return;
        }
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                flushPendingPartyExp(player);
            }
        }
    }

    private void flushPendingPartyExp(Player player) {
        if (!isPartyExpEnabled() || player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        int amount = pendingPartyExp.getOrDefault(playerId, 0);
        if (amount <= 0) {
            return;
        }
        if (!OmVeinsAPI.isInitialized()) {
            if (!partyExpUnavailableLogged) {
                partyExpUnavailableLogged = true;
                plugin.getLogger().warning("OmVeins API is not initialized. BedWars party EXP rewards are queued until it becomes available.");
            }
            return;
        }
        OmVeinsAPI.addPartyExp(player, amount);
        pendingPartyExp.remove(playerId);
    }

    private void updateTeamsInMatch() {
        teamsInMatch.clear();
        for (TeamColor team : assignments.values()) {
            if (team != null) {
                teamsInMatch.add(team);
            }
        }
    }

    private void initializeTeamUpgrades() {
        teamUpgrades.clear();
        for (TeamColor team : teamsInMatch) {
            teamUpgrades.put(team, new TeamUpgradeState());
        }
    }

    private void initializeBaseCenters() {
        baseCenters.clear();
        baseOccupants.clear();
        Map<TeamColor, BlockPoint> spawns = arena.getSpawns();
        for (TeamColor team : teamsInMatch) {
            BlockPoint spawn = spawns.get(team);
            if (spawn != null) {
                baseCenters.put(team, spawn);
                baseOccupants.put(team, new HashSet<>());
            }
        }
        for (GeneratorInfo generator : arena.getGenerators()) {
            if (generator.type() != GeneratorType.BASE || generator.team() == null) {
                continue;
            }
            if (!teamsInMatch.contains(generator.team())) {
                continue;
            }
            if (baseCenters.containsKey(generator.team())) {
                continue;
            }
            baseCenters.put(generator.team(), generator.location());
            baseOccupants.put(generator.team(), new HashSet<>());
        }
    }

    private TeamUpgradeState getUpgradeState(TeamColor team) {
        if (team == null) {
            return new TeamUpgradeState();
        }
        return teamUpgrades.computeIfAbsent(team, key -> new TeamUpgradeState());
    }

    private boolean hasTeamUpgrade(TeamColor team, TeamUpgradeType type) {
        return team != null && getUpgradeState(team).getTier(type) > 0;
    }

    private void syncForgeTiers() {
        if (generatorManager == null) {
            return;
        }
        Map<TeamColor, Integer> tiers = new EnumMap<>(TeamColor.class);
        for (TeamColor team : teamsInMatch) {
            tiers.put(team, getUpgradeState(team).getTier(TeamUpgradeType.FORGE));
        }
        generatorManager.setBaseForgeTiers(tiers);
    }

    private int getBaseEffectRadius() {
        int radius = arena.getBaseRadius();
        if (radius > 0) {
            return radius;
        }
        int fallback = arena.getBaseGeneratorRadius();
        return fallback > 0 ? fallback : DEFAULT_BASE_RADIUS;
    }

    private void startUpgradeTasks() {
        BukkitTask healTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> safeRun("healPool", this::applyHealPoolTick),
                0L,
                HEAL_POOL_INTERVAL_TICKS);
        tasks.add(healTask);
        BukkitTask trapTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> safeRun("trapCheck", this::checkTrapTriggers),
                0L,
                TRAP_CHECK_INTERVAL_TICKS);
        tasks.add(trapTask);
    }

    private void startRegenTask() {
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> safeRun("customRegen", this::applyRegenTick),
                20L,
                20L);
        tasks.add(task);
    }

    private void applyRegenTick() {
        if (state != GameState.RUNNING) {
            return;
        }
        long now = System.currentTimeMillis();
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (!isInArenaWorld(player.getWorld()) || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            double maxHealth = player.getMaxHealth();
            if (player.getHealth() >= maxHealth) {
                continue;
            }
            long lastDamage = lastDamageTimes.getOrDefault(playerId, 0L);
            if (now - lastDamage < REGEN_DELAY_MILLIS) {
                continue;
            }
            long lastRegen = lastRegenTimes.getOrDefault(playerId, 0L);
            if (now - lastRegen < REGEN_INTERVAL_MILLIS) {
                continue;
            }
            double newHealth = Math.min(maxHealth, player.getHealth() + 2.0);
            player.setHealth(newHealth);
            lastRegenTimes.put(playerId, now);
        }
    }

    private void applyHealPoolTick() {
        if (state != GameState.RUNNING) {
            return;
        }
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        int radius = getBaseEffectRadius();
        int radiusSquared = radius * radius;
        for (TeamColor team : teamsInMatch) {
            TeamUpgradeState upgrades = teamUpgrades.get(team);
            if (upgrades == null || upgrades.getTier(TeamUpgradeType.HEAL_POOL) <= 0) {
                continue;
            }
            BlockPoint base = baseCenters.get(team);
            if (base == null) {
                continue;
            }
            for (UUID playerId : assignments.keySet()) {
                if (assignments.get(playerId) != team) {
                    continue;
                }
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    continue;
                }
                if (!isInArenaWorld(player.getWorld()) || player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                Location location = player.getLocation();
                int dx = location.getBlockX() - base.x();
                int dz = location.getBlockZ() - base.z();
                if (dx * dx + dz * dz <= radiusSquared) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                            HEAL_POOL_DURATION_TICKS,
                            0,
                            true,
                            false,
                            true));
                }
            }
        }
    }

    private void checkTrapTriggers() {
        if (state != GameState.RUNNING) {
            return;
        }
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        int radius = getBaseEffectRadius();
        int radiusSquared = radius * radius;
        for (TeamColor team : teamsInMatch) {
            TeamUpgradeState upgrades = teamUpgrades.get(team);
            if (upgrades == null) {
                continue;
            }
            BlockPoint base = baseCenters.get(team);
            if (base == null) {
                continue;
            }
            Set<UUID> previous = baseOccupants.computeIfAbsent(team, key -> new HashSet<>());
            Set<UUID> current = new HashSet<>();
            for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
                if (entry.getValue() == null || entry.getValue() == team) {
                    continue;
                }
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null || !player.isOnline()) {
                    continue;
                }
                if (!isInArenaWorld(player.getWorld()) || player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                if (hasTrapImmunity(entry.getKey())) {
                    continue;
                }
                Location location = player.getLocation();
                int dx = location.getBlockX() - base.x();
                int dz = location.getBlockZ() - base.z();
                if (dx * dx + dz * dz <= radiusSquared) {
                    current.add(entry.getKey());
                }
            }
            TrapType triggeredTrap = null;
            Player intruder = null;
            if (!current.isEmpty() && upgrades.getTrapCount() > 0) {
                for (UUID playerId : current) {
                    if (!previous.contains(playerId)) {
                        intruder = Bukkit.getPlayer(playerId);
                        triggeredTrap = upgrades.pollTrap();
                        break;
                    }
                }
            }
            if (triggeredTrap != null && intruder != null) {
                triggerTrap(team, triggeredTrap, intruder, current);
            }
            previous.clear();
            previous.addAll(current);
        }
    }

    private void triggerTrap(TeamColor team, TrapType trap, Player intruder, Set<UUID> currentIntruders) {
        intruder.removePotionEffect(PotionEffectType.INVISIBILITY);
        switch (trap) {
            case ITS_A_TRAP -> {
                intruder.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                        TRAP_BLINDNESS_SECONDS * 20,
                        0,
                        true,
                        false,
                        true));
                intruder.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                        TRAP_SLOW_SECONDS * 20,
                        1,
                        true,
                        false,
                        true));
            }
            case COUNTER_OFFENSIVE -> applyCounterOffensive(team);
            case ALARM -> showTrapTitle(team, "Alarm Trap!");
            case ILLUSION -> triggerIllusionTrap(team, currentIntruders);
            case MINER_FATIGUE -> intruder.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,
                    TRAP_FATIGUE_SECONDS * 20,
                    0,
                    true,
                    false,
                    true));
            default -> {
            }
        }
        announceTrap(team, trap.displayName());
    }

    private void triggerIllusionTrap(TeamColor defendingTeam, Set<UUID> intruders) {
        if (intruders == null || intruders.isEmpty()) {
            return;
        }
        showTrapTitle(defendingTeam, "Illusion Trap!");
        for (UUID playerId : new HashSet<>(intruders)) {
            TeamColor enemyTeam = assignments.get(playerId);
            if (enemyTeam == null || enemyTeam == defendingTeam) {
                continue;
            }
            Player enemy = Bukkit.getPlayer(playerId);
            if (enemy == null || !enemy.isOnline()) {
                continue;
            }
            if (!isInArenaWorld(enemy.getWorld()) || enemy.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Location destination = arena.getSpawn(enemyTeam);
            if (destination == null) {
                continue;
            }
            Location source = enemy.getLocation().clone();
            enemy.removePotionEffect(PotionEffectType.INVISIBILITY);
            World sourceWorld = source.getWorld();
            if (sourceWorld != null) {
                sourceWorld.spawnParticle(Particle.PORTAL, source.clone().add(0.0, 1.0, 0.0),
                        30, 0.45, 0.7, 0.45, 0.0);
                sourceWorld.playSound(source, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            }
            enemy.teleport(destination);
            World destinationWorld = destination.getWorld();
            if (destinationWorld != null) {
                destinationWorld.spawnParticle(Particle.PORTAL, destination.clone().add(0.0, 1.0, 0.0),
                        30, 0.45, 0.7, 0.45, 0.0);
                destinationWorld.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.1f);
            }
            enemy.sendMessage(Component.text("An Illusion Trap warped you back to base.", NamedTextColor.RED));
        }
    }

    private void applyCounterOffensive(TeamColor team) {
        for (UUID playerId : assignments.keySet()) {
            if (assignments.get(playerId) != team) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (!isInArenaWorld(player.getWorld()) || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                    TRAP_COUNTER_SECONDS * 20,
                    0,
                    true,
                    false,
                    true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,
                    TRAP_COUNTER_SECONDS * 20,
                    0,
                    true,
                    false,
                    true));
        }
    }

    private void announceTrap(TeamColor team, String name) {
        Component message = Component.text("Trap triggered: " + name, NamedTextColor.RED);
        for (UUID playerId : assignments.keySet()) {
            if (assignments.get(playerId) != team) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            player.sendMessage(message);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
        }
    }

    private void announceTeamUpgrade(TeamColor team, Player buyer, TeamUpgradeType type, int tier) {
        if (team == null || buyer == null || type == null) {
            return;
        }
        String upgradeName = type.tierName(tier);
        Component message = Component.text(buyer.getName(), NamedTextColor.YELLOW)
                .append(Component.text(" purchased ", NamedTextColor.GRAY))
                .append(Component.text(upgradeName, NamedTextColor.GREEN));
        for (UUID playerId : assignments.keySet()) {
            if (assignments.get(playerId) != team) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private void showTrapTitle(TeamColor team, String name) {
        Title title = Title.title(
                Component.text(name, NamedTextColor.RED),
                Component.text("Enemy detected", NamedTextColor.GRAY),
                ALERT_TITLE_TIMES
        );
        for (UUID playerId : assignments.keySet()) {
            if (assignments.get(playerId) != team) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.showTitle(title);
            }
        }
    }

    private void applyTeamUpgradeEffects(TeamColor team) {
        if (team == null) {
            return;
        }
        for (UUID playerId : assignments.keySet()) {
            if (assignments.get(playerId) != team) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                applyTeamUpgrades(player, team);
            }
        }
    }

    private void applyTeamUpgrades(Player player, TeamColor team) {
        if (team == null || player == null) {
            return;
        }
        if (!isInArenaWorld(player.getWorld())) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        TeamUpgradeState upgrades = getUpgradeState(team);
        int protectionTier = upgrades.getTier(TeamUpgradeType.PROTECTION);
        if (protectionTier > 0) {
            applyProtection(player, protectionTier);
        }
        if (upgrades.getTier(TeamUpgradeType.SHARPNESS) > 0) {
            applySharpness(player);
        }
        int hasteTier = upgrades.getTier(TeamUpgradeType.HASTE);
        if (hasteTier > 0) {
            applyHaste(player, hasteTier);
        }
        if (upgrades.getTier(TeamUpgradeType.EFFICIENCY) > 0) {
            applyEfficiency(player);
        }
        int featherTier = upgrades.getTier(TeamUpgradeType.FEATHER_FALLING);
        if (featherTier > 0) {
            applyFeatherFalling(player, featherTier);
        }
        int thornsTier = upgrades.getTier(TeamUpgradeType.THORNS);
        if (thornsTier > 0) {
            applyThorns(player, thornsTier);
        }
        if (upgrades.getTier(TeamUpgradeType.FIRE_ASPECT) > 0) {
            applyFireAspect(player);
        }
        applyMatchEventEffects(player);
        applyScale(player, resolveEffectiveScale(upgrades.getTier(TeamUpgradeType.SCALE_DOWN)));
    }

    private void applyProtection(Player player, int level) {
        if (level <= 0) {
            return;
        }
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean changed = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
            if (piece == null) {
                continue;
            }
            ItemMeta meta = piece.getItemMeta();
            if (meta.getEnchantLevel(Enchantment.PROTECTION) >= level) {
                continue;
            }
            meta.addEnchant(Enchantment.PROTECTION, level, true);
            piece.setItemMeta(meta);
            armor[i] = piece;
            changed = true;
        }
        if (changed) {
            player.getInventory().setArmorContents(armor);
        }
    }

    private void applySharpness(Player player) {
        applyEnchantment(player, SWORD_MATERIALS, Enchantment.SHARPNESS, 1);
    }

    private void applyHaste(Player player, int tier) {
        int amplifier = Math.max(0, tier - 1);
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,
                Integer.MAX_VALUE,
                amplifier,
                true,
                false,
                true));
    }

    private void applyEfficiency(Player player) {
        applyEnchantment(player, TOOL_EFFICIENCY_MATERIALS, Enchantment.EFFICIENCY, 1);
    }

    private void applyFeatherFalling(Player player, int level) {
        if (level <= 0) {
            return;
        }
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean changed = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
            if (piece == null) {
                continue;
            }
            if (!piece.getType().name().endsWith("_BOOTS")) {
                continue;
            }
            ItemMeta meta = piece.getItemMeta();
            if (meta.getEnchantLevel(Enchantment.FEATHER_FALLING) >= level) {
                continue;
            }
            meta.addEnchant(Enchantment.FEATHER_FALLING, level, true);
            piece.setItemMeta(meta);
            armor[i] = piece;
            changed = true;
        }
        if (changed) {
            player.getInventory().setArmorContents(armor);
        }
    }

    private void applyThorns(Player player, int level) {
        if (level <= 0) {
            return;
        }
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean changed = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
            if (piece == null) {
                continue;
            }
            ItemMeta meta = piece.getItemMeta();
            if (meta.getEnchantLevel(Enchantment.THORNS) >= level) {
                continue;
            }
            meta.addEnchant(Enchantment.THORNS, level, true);
            piece.setItemMeta(meta);
            armor[i] = piece;
            changed = true;
        }
        if (changed) {
            player.getInventory().setArmorContents(armor);
        }
    }

    private void applyFireAspect(Player player) {
        applyEnchantment(player, ATTACK_MATERIALS, Enchantment.FIRE_ASPECT, 1);
    }

    private void applyMatchEventEffects(Player player) {
        if (player == null || activeMatchEvent == null) {
            return;
        }
        switch (activeMatchEvent) {
            case SPEEDRUN -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                    Integer.MAX_VALUE,
                    1,
                    true,
                    false,
                    true));
            case LONG_ARMS -> setAttributeToDefaultPlus(player, LONG_ARMS_RANGE_BONUS,
                    "BLOCK_INTERACTION_RANGE",
                    "PLAYER_BLOCK_INTERACTION_RANGE");
            case MOON_BIG -> {
                boolean jumpApplied = setAttributeToDefaultMultiplier(player, MOON_BIG_JUMP_MULTIPLIER,
                        "JUMP_STRENGTH",
                        "GENERIC_JUMP_STRENGTH");
                if (!jumpApplied) {
                    setAttributeToDefaultMultiplier(player, 0.6, "GRAVITY", "GENERIC_GRAVITY");
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,
                        Integer.MAX_VALUE,
                        1,
                        true,
                        false,
                        true));
            }
            case BLOOD_MOON -> {
                setAttributeToDefaultMultiplier(player, BLOOD_MOON_HEALTH_MULTIPLIER,
                        "MAX_HEALTH",
                        "GENERIC_MAX_HEALTH");
                PotionEffectType resistance = resolvePotionEffectType("RESISTANCE", "DAMAGE_RESISTANCE");
                if (resistance != null) {
                    player.addPotionEffect(new PotionEffect(resistance,
                            Integer.MAX_VALUE,
                            1,
                            true,
                            false,
                            true));
                }
                clampPlayerHealthToMax(player);
            }
            default -> {
            }
        }
    }

    private void applyScale(Player player, double scale) {
        if (player == null) {
            return;
        }
        org.bukkit.attribute.AttributeInstance instance = resolveAttributeInstance(player, "SCALE", "GENERIC_SCALE");
        if (instance == null) {
            return;
        }
        for (org.bukkit.attribute.AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
            instance.removeModifier(modifier);
        }
        instance.setBaseValue(scale);
    }

    private double resolveScaleDownValue(int tier) {
        return switch (tier) {
            case 1 -> SCALE_DOWN_TIER_ONE;
            case 2 -> SCALE_DOWN_TIER_TWO;
            default -> DEFAULT_PLAYER_SCALE;
        };
    }

    private double resolveEffectiveScale(int scaleDownTier) {
        if (activeMatchEvent == BedwarsMatchEventType.APRIL_FOOLS) {
            return APRIL_FOOLS_SCALE_MULTIPLIER;
        }
        return resolveScaleDownValue(scaleDownTier);
    }

    private double resolveMatchEventScaleMultiplier() {
        return activeMatchEvent == BedwarsMatchEventType.APRIL_FOOLS
                ? APRIL_FOOLS_SCALE_MULTIPLIER
                : DEFAULT_PLAYER_SCALE;
    }

    private boolean setAttributeBaseValue(LivingEntity entity, String attributeName, double value) {
        if (entity == null || attributeName == null || attributeName.isBlank()) {
            return false;
        }
        try {
            org.bukkit.attribute.Attribute attribute = org.bukkit.attribute.Attribute.valueOf(attributeName);
            org.bukkit.attribute.AttributeInstance instance = entity.getAttribute(attribute);
            if (instance == null) {
                return false;
            }
            instance.setBaseValue(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean setAttributeToDefaultPlus(LivingEntity entity, double bonus, String... attributeNames) {
        org.bukkit.attribute.AttributeInstance instance = resolveAttributeInstance(entity, attributeNames);
        if (instance == null) {
            return false;
        }
        instance.setBaseValue(instance.getDefaultValue() + bonus);
        return true;
    }

    private boolean setAttributeToDefaultMultiplier(LivingEntity entity, double multiplier, String... attributeNames) {
        org.bukkit.attribute.AttributeInstance instance = resolveAttributeInstance(entity, attributeNames);
        if (instance == null) {
            return false;
        }
        instance.setBaseValue(instance.getDefaultValue() * multiplier);
        return true;
    }

    private void resetAttributeToDefault(LivingEntity entity, String... attributeNames) {
        org.bukkit.attribute.AttributeInstance instance = resolveAttributeInstance(entity, attributeNames);
        if (instance != null) {
            instance.setBaseValue(instance.getDefaultValue());
        }
    }

    private org.bukkit.attribute.AttributeInstance resolveAttributeInstance(LivingEntity entity, String... attributeNames) {
        if (entity == null || attributeNames == null) {
            return null;
        }
        for (String attributeName : attributeNames) {
            if (attributeName == null || attributeName.isBlank()) {
                continue;
            }
            try {
                org.bukkit.attribute.Attribute attribute = org.bukkit.attribute.Attribute.valueOf(attributeName);
                org.bukkit.attribute.AttributeInstance instance = entity.getAttribute(attribute);
                if (instance != null) {
                    return instance;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private PotionEffectType resolvePotionEffectType(String... names) {
        if (names == null) {
            return null;
        }
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            PotionEffectType type = PotionEffectType.getByName(name);
            if (type != null) {
                return type;
            }
        }
        return null;
    }

    private void applyEnchantment(Player player, Set<Material> materials, Enchantment enchantment, int level) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || !materials.contains(item.getType())) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta.getEnchantLevel(enchantment) >= level) {
                continue;
            }
            meta.addEnchant(enchantment, level, true);
            item.setItemMeta(meta);
            contents[i] = item;
            changed = true;
        }
        if (changed) {
            player.getInventory().setStorageContents(contents);
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && materials.contains(offhand.getType())) {
            ItemMeta meta = offhand.getItemMeta();
            if (meta.getEnchantLevel(enchantment) < level) {
                meta.addEnchant(enchantment, level, true);
                offhand.setItemMeta(meta);
                player.getInventory().setItemInOffHand(offhand);
            }
        }
    }

    private void removeHealPoolEffects(TeamColor team) {
        for (UUID playerId : assignments.keySet()) {
            if (assignments.get(playerId) != team) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.removePotionEffect(PotionEffectType.REGENERATION);
            }
        }
    }

    private void clearUpgradeEffects(Player player) {
        if (player == null) {
            return;
        }
        player.removePotionEffect(PotionEffectType.HASTE);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        player.removePotionEffect(PotionEffectType.SLOW_FALLING);
        PotionEffectType resistance = resolvePotionEffectType("RESISTANCE", "DAMAGE_RESISTANCE");
        if (resistance != null) {
            player.removePotionEffect(resistance);
        }
        resetAttributeToDefault(player, "BLOCK_INTERACTION_RANGE", "PLAYER_BLOCK_INTERACTION_RANGE");
        resetAttributeToDefault(player, "JUMP_STRENGTH", "GENERIC_JUMP_STRENGTH");
        resetAttributeToDefault(player, "GRAVITY", "GENERIC_GRAVITY");
        resetAttributeToDefault(player, "MAX_HEALTH", "GENERIC_MAX_HEALTH");
        applyScale(player, DEFAULT_PLAYER_SCALE);
        clampPlayerHealthToMax(player);
    }

    private void clampPlayerHealthToMax(Player player) {
        if (player == null || player.isDead()) {
            return;
        }
        double currentHealth = player.getHealth();
        if (currentHealth <= 0.0) {
            return;
        }
        player.setHealth(Math.min(currentHealth, player.getMaxHealth()));
    }

    private void applyEditorInvisibility(Player player) {
        if (player == null) {
            return;
        }
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY,
                Integer.MAX_VALUE,
                0,
                false,
                false,
                false
        ));
    }

    private void applyLobbyBuffsToLobbyPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isInArenaWorld(player.getWorld()) || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            applyLobbyBuffs(player);
        }
    }

    private void applyLobbyBuffs(Player player) {
        if (player == null) {
            return;
        }
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExhaustion(0.0f);
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.REGENERATION,
                LOBBY_REGEN_DURATION_TICKS,
                0,
                false,
                false,
                false
        ), true);
    }

    private void hideEditorFromParticipants(Player editor) {
        if (plugin == null || editor == null) {
            return;
        }
        UUID editorId = editor.getUniqueId();
        for (UUID playerId : assignments.keySet()) {
            if (playerId.equals(editorId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.hidePlayer(plugin, editor);
            }
        }
    }

    private void hideEditorsFrom(Player viewer) {
        if (plugin == null || viewer == null) {
            return;
        }
        for (UUID editorId : editorPlayers) {
            Player editor = Bukkit.getPlayer(editorId);
            if (editor != null && !editor.getUniqueId().equals(viewer.getUniqueId())) {
                viewer.hidePlayer(plugin, editor);
            }
        }
    }

    private void showEditorToParticipants(Player editor) {
        if (plugin == null || editor == null) {
            return;
        }
        UUID editorId = editor.getUniqueId();
        for (UUID playerId : assignments.keySet()) {
            if (playerId.equals(editorId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.showPlayer(plugin, editor);
            }
        }
    }

    private void clearEditors() {
        if (editorPlayers.isEmpty()) {
            return;
        }
        if (plugin != null) {
            for (UUID editorId : new HashSet<>(editorPlayers)) {
                Player editor = Bukkit.getPlayer(editorId);
                if (editor != null) {
                    if (editor.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                        editor.removePotionEffect(PotionEffectType.INVISIBILITY);
                    }
                    for (Player viewer : Bukkit.getOnlinePlayers()) {
                        viewer.showPlayer(plugin, editor);
                    }
                }
            }
        }
        editorPlayers.clear();
    }

    private void spawnShops() {
        despawnShops();
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        for (ShopLocation main : arena.getMainShops().values()) {
            if (main != null) {
                spawnShopNpc(world, main, ShopType.ITEM);
            }
        }
        for (ShopLocation upgrades : arena.getUpgradeShops().values()) {
            if (upgrades != null) {
                spawnShopNpc(world, upgrades, ShopType.UPGRADES);
            }
        }
    }

    private void despawnShops() {
        for (UUID id : shopNpcIds) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        shopNpcIds.clear();
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        for (org.bukkit.entity.Entity entity : world.getEntitiesByClass(Villager.class)) {
            if (entity.getScoreboardTags().contains(ITEM_SHOP_TAG)
                    || entity.getScoreboardTags().contains(UPGRADES_SHOP_TAG)) {
                entity.remove();
            }
        }
    }

    private void spawnShopNpc(World world, ShopLocation location, ShopType type) {
        Location spawn = location.toLocation(world);
        clearExistingShopNpc(world, spawn);
        Villager villager = world.spawn(spawn, Villager.class, entity -> {
            entity.setAI(false);
            entity.setInvulnerable(true);
            entity.setCollidable(false);
            entity.setSilent(true);
            entity.setRemoveWhenFarAway(false);
            entity.setPersistent(true);
            entity.customName(Component.text(type.displayName(), NamedTextColor.YELLOW));
            entity.setCustomNameVisible(true);
            entity.addScoreboardTag(type == ShopType.ITEM ? ITEM_SHOP_TAG : UPGRADES_SHOP_TAG);
        });
        shopNpcIds.add(villager.getUniqueId());
    }

    private void clearExistingShopNpc(World world, Location spawn) {
        if (world == null || spawn == null) {
            return;
        }
        int blockX = spawn.getBlockX();
        int blockY = spawn.getBlockY();
        int blockZ = spawn.getBlockZ();
        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(spawn, 0.6, 0.6, 0.6)) {
            if (!(entity instanceof Villager villager)) {
                continue;
            }
            Location location = villager.getLocation();
            if (location.getBlockX() == blockX
                    && location.getBlockY() == blockY
                    && location.getBlockZ() == blockZ) {
                villager.remove();
            }
        }
    }

    private boolean isWithinRadius(BlockPoint a, BlockPoint b, int radius) {
        int dx = a.x() - b.x();
        int dz = a.z() - b.z();
        return dx * dx + dz * dz <= radius * radius;
    }

    private String toRoman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(tier);
        };
    }

    private static final class AbyssalRiftState {
        private final UUID interactionId;
        private final UUID displayId;
        private final UUID titleStandId;
        private final UUID healthStandId;
        private final TeamColor team;
        private double health;
        private final double maxHealth;
        private final double radius;
        private BukkitTask auraTask;

        private AbyssalRiftState(UUID interactionId,
                                 UUID displayId,
                                 UUID titleStandId,
                                 UUID healthStandId,
                                 TeamColor team,
                                 double health,
                                 double maxHealth,
                                 double radius) {
            this.interactionId = interactionId;
            this.displayId = displayId;
            this.titleStandId = titleStandId;
            this.healthStandId = healthStandId;
            this.team = team;
            this.health = health;
            this.maxHealth = maxHealth;
            this.radius = radius;
        }

        private UUID interactionId() {
            return interactionId;
        }

        private UUID displayId() {
            return displayId;
        }

        private UUID titleStandId() {
            return titleStandId;
        }

        private UUID healthStandId() {
            return healthStandId;
        }

        private TeamColor team() {
            return team;
        }

        private double health() {
            return health;
        }

        private double maxHealth() {
            return maxHealth;
        }

        private double radius() {
            return radius;
        }

        private BukkitTask auraTask() {
            return auraTask;
        }

        private void setAuraTask(BukkitTask auraTask) {
            this.auraTask = auraTask;
        }

        private void damage(double amount) {
            health -= amount;
        }
    }

    private record ElytraStrikeState(ItemStack previousChestplate,
                                     boolean previousAllowFlight,
                                     boolean previousFlying) {
    }
}
