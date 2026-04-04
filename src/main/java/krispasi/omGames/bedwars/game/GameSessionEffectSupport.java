package krispasi.omGames.bedwars.game;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import krispasi.omGames.OmVeinsAPI;
import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.event.*;
import krispasi.omGames.bedwars.generator.*;
import krispasi.omGames.bedwars.gui.*;
import krispasi.omGames.bedwars.item.*;
import krispasi.omGames.bedwars.model.*;
import krispasi.omGames.bedwars.shop.*;
import krispasi.omGames.bedwars.upgrade.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Bed;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.*;
import org.bukkit.scoreboard.*;
import org.bukkit.util.Vector;

/**
 * Runtime state machine for a single BedWars match.
 * <p>Tracks team assignments, beds, generators, placed blocks, upgrades, traps,
 * and per-player tool tiers.</p>
 * <p>Enforces match rules such as respawn timing, combat restrictions, sudden death,
 * and world border behavior.</p>
 * <p>Creates and tears down shop NPCs, scoreboards, and other match-scoped entities.</p>
 * @see krispasi.omGames.bedwars.game.GameState
 */

abstract class GameSessionEffectSupport {
    protected static final String SIDEBAR_OBJECTIVE_ID = "bedwars";
    protected static final String HEALTH_OBJECTIVE_ID = "bedwars_hp";
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
    public static final String MOON_BIG_ASTEROID_TAG = "bw_moon_big_asteroid";
    public static final String ELYTRA_STRIKE_ACTIVE_ITEM_ID = "elytra_strike_active";
    protected static final double DEFAULT_PLAYER_SCALE = 1.0;
    protected static final double SCALE_DOWN_TIER_ONE = 0.9;
    protected static final double SCALE_DOWN_TIER_TWO = 0.8;
    protected static final double DEFAULT_PLAYER_MOVEMENT_SPEED = 0.1;
    protected static final double DEFAULT_PLAYER_STEP_HEIGHT = 0.6;
    protected static final double DEFAULT_PLAYER_GRAVITY = 0.08;
    protected static final double APRIL_FOOLS_SCALE_MULTIPLIER = 0.5;
    protected static final double LONG_ARMS_RANGE_BONUS = 10.0;
    protected static final double BLOOD_MOON_HEALTH_MULTIPLIER = 0.5;
    protected static final double MOON_BIG_GRAVITY = 0.01;
    protected static final double BLOOD_MOON_LIFESTEAL_RATIO = 1.0;
    protected static final Duration TITLE_FADE_IN = Duration.ofMillis(300);
    protected static final Duration TITLE_STAY = Duration.ofSeconds(3);
    protected static final Duration TITLE_FADE_OUT = Duration.ofSeconds(1);
    protected static final Title.Times DEFAULT_TITLE_TIMES =
            Title.Times.times(TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT);
    protected static final Title.Times ALERT_TITLE_TIMES =
            DEFAULT_TITLE_TIMES;
    protected static final Set<String> IN_THIS_ECONOMY_BANNED_ITEMS = Set.of();
    protected static final Set<String> IN_THIS_ECONOMY_PRICE_MULTIPLIED_ITEMS = Set.of(
            "fireball",
            "bed_bug",
            "dream_defender"
    );
    protected static final int IN_THIS_ECONOMY_PRICE_MULTIPLIER = 4;
    protected static final List<TeamUpgradeType> BENEVOLENT_UPGRADE_POOL = List.of(
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
    protected static final Set<Material> SWORD_MATERIALS = EnumSet.of(
            Material.WOODEN_SWORD,
            Material.STONE_SWORD,
            Material.IRON_SWORD,
            Material.DIAMOND_SWORD,
            Material.MACE,
            Material.NETHERITE_SPEAR
    );
    protected static final Set<Material> WOODEN_SWORD_ONLY = EnumSet.of(Material.WOODEN_SWORD);
    protected static final Set<Material> BOW_MATERIALS = EnumSet.of(Material.BOW);
    protected static final Set<Material> CROSSBOW_MATERIALS = EnumSet.of(Material.CROSSBOW);
    protected static final Set<Material> PICKAXE_MATERIALS = EnumSet.of(
            Material.WOODEN_PICKAXE,
            Material.STONE_PICKAXE,
            Material.IRON_PICKAXE,
            Material.GOLDEN_PICKAXE,
            Material.DIAMOND_PICKAXE
    );
    protected static final Set<Material> AXE_MATERIALS = EnumSet.of(
            Material.WOODEN_AXE,
            Material.STONE_AXE,
            Material.IRON_AXE,
            Material.GOLDEN_AXE,
            Material.DIAMOND_AXE
    );
    protected static final Set<Material> TOOL_EFFICIENCY_MATERIALS = EnumSet.of(
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
    protected static final Set<Material> ATTACK_MATERIALS = EnumSet.of(
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
    protected static final int START_COUNTDOWN_SECONDS = 5;
    protected static final int RESPAWN_DELAY_SECONDS = 5;
    protected static final int RESPAWN_PROTECTION_SECONDS = 5;
    protected static final int DEFAULT_BASE_RADIUS = 8;
    protected static final int HEAL_POOL_INTERVAL_TICKS = 20;
    protected static final int HEAL_POOL_DURATION_TICKS = 60;
    protected static final int TRAP_CHECK_INTERVAL_TICKS = 20;
    protected static final int TRAP_MAX_COUNT = 3;
    protected static final int TRAP_BLINDNESS_SECONDS = 8;
    protected static final int TRAP_SLOW_SECONDS = 8;
    protected static final int TRAP_COUNTER_SECONDS = 15;
    protected static final int TRAP_FATIGUE_SECONDS = 10;
    protected static final int LOBBY_REGEN_DURATION_TICKS = 60;
    protected static final long REGEN_DELAY_MILLIS = 4000L;
    protected static final long REGEN_INTERVAL_MILLIS = 3000L;
    protected static final double SUDDEN_DEATH_BORDER_TARGET_SIZE = 6.0;
    protected static final double WORLD_BORDER_DAMAGE_BUFFER = 0.0;
    protected static final double WORLD_BORDER_DAMAGE_AMOUNT = 2.0;
    protected static final int WORLD_BORDER_WARNING_DISTANCE = 1;
    protected static final int LONG_MATCH_PARTY_EXP_SECONDS = 8 * 60;
    protected static final int PARTY_EXP_LONG_MATCH = 50;
    protected static final int PARTY_EXP_BED_GONE_MATCH = 100;
    protected static final int PARTY_EXP_WIN = 100;
    protected static final int PARTY_EXP_SECOND_PLACE = 50;
    protected static final int PARTY_EXP_THIRD_PLACE = 25;
    protected static final int PARTY_EXP_KILL = 1;
    protected static final int PARTY_EXP_BED_BREAK = 10;
    protected static final int PARTY_EXP_FINAL_KILL = 5;

    protected final BedwarsManager bedwarsManager;
    protected final Arena arena;
    protected final Map<UUID, TeamColor> assignments = new HashMap<>();
    protected final Map<TeamColor, BedState> bedStates = new EnumMap<>(TeamColor.class);
    protected final Map<TeamColor, BedLocation> activeBedLocations = new EnumMap<>(TeamColor.class);
    protected final Map<BlockPoint, TeamColor> bedBlocks = new HashMap<>();
    protected final Set<BlockPoint> placedBlocks = new HashSet<>();
    protected final Map<BlockPoint, ItemStack> placedBlockItems = new HashMap<>();
    protected final Map<BlockPoint, BlockState> temporaryMapLobbyIslandBlocks = new HashMap<>();
    protected final Set<Long> forcedChunks = new HashSet<>();
    protected final Set<UUID> frozenPlayers = new HashSet<>();
    protected final Set<UUID> eliminatedPlayers = new HashSet<>();
    protected final Set<TeamColor> eliminatedTeams = new HashSet<>();
    protected final List<TeamColor> eliminationOrder = new ArrayList<>();
    protected final Set<UUID> pendingRespawns = new HashSet<>();
    protected final Set<UUID> respawnGracePlayers = new HashSet<>();
    protected final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();
    protected final Map<UUID, BukkitTask> respawnCountdownTasks = new HashMap<>();
    protected final Map<UUID, Long> respawnProtectionEnds = new HashMap<>();
    protected final Map<UUID, BukkitTask> respawnProtectionTasks = new HashMap<>();
    protected final Map<UUID, Inventory> fakeEnderChests = new HashMap<>();
    protected final Map<UUID, Scoreboard> previousScoreboards = new HashMap<>();
    protected final Map<UUID, Scoreboard> activeScoreboards = new HashMap<>();
    protected final Map<UUID, List<String>> sidebarLines = new HashMap<>();
    protected final Set<String> rotatingItemIds = new HashSet<>();
    protected final Set<String> rotatingUpgradeIds = new HashSet<>();
    protected final List<String> manualRotatingItemIds = new ArrayList<>();
    protected final List<String> manualRotatingUpgradeIds = new ArrayList<>();
    protected RotatingSelectionMode rotatingMode = RotatingSelectionMode.AUTO;
    protected final Map<UUID, Integer> killCounts = new HashMap<>();
    protected final Map<UUID, Integer> pendingPartyExp = new HashMap<>();
    protected final Map<TeamColor, Map<String, Integer>> teamPurchaseCounts = new EnumMap<>(TeamColor.class);
    protected final Map<UUID, Map<String, Integer>> playerPurchaseCounts = new HashMap<>();
    protected final Map<UUID, UUID> pendingDeathKillCredits = new HashMap<>();
    protected final Set<UUID> editorPlayers = new HashSet<>();
    protected final Set<UUID> lockedCommandSpectators = new HashSet<>();
    protected final Set<UUID> disconnectedParticipants = new HashSet<>();
    protected final Set<TeamColor> teamsInMatch = EnumSet.noneOf(TeamColor.class);
    protected final Set<UUID> shopNpcIds = new HashSet<>();
    protected final Map<TeamColor, TeamUpgradeState> teamUpgrades = new EnumMap<>(TeamColor.class);
    protected final Map<TeamColor, BlockPoint> baseCenters = new EnumMap<>(TeamColor.class);
    protected final Map<TeamColor, Set<UUID>> baseOccupants = new EnumMap<>(TeamColor.class);
    protected final Map<UUID, Long> trapImmunityEnds = new HashMap<>();
    protected final Map<UUID, BukkitTask> trapImmunityTasks = new HashMap<>();
    protected final Map<UUID, Integer> armorTiers = new HashMap<>();
    protected final Map<UUID, Integer> pickaxeTiers = new HashMap<>();
    protected final Map<UUID, Integer> axeTiers = new HashMap<>();
    protected final Set<UUID> shearsUnlocked = new HashSet<>();
    protected final Set<UUID> shieldUnlocked = new HashSet<>();
    protected final List<TeamUpgradeType> benevolentEventUpgrades = new ArrayList<>();
    protected boolean suddenDeathActive;
    protected boolean tier2Triggered;
    protected boolean tier3Triggered;
    protected boolean bedDestructionTriggered;
    protected boolean gameEndTriggered;
    protected final Map<UUID, Long> lastCombatTimes = new HashMap<>();
    protected final Map<UUID, UUID> lastDamagers = new HashMap<>();
    protected final Map<UUID, Long> lastDamagerTimes = new HashMap<>();
    protected final Map<UUID, Long> lastDamageTimes = new HashMap<>();
    protected final Map<UUID, Long> lastRegenTimes = new HashMap<>();
    protected final List<BukkitTask> tasks = new ArrayList<>();
    protected GeneratorManager generatorManager;
    protected GameState state = GameState.IDLE;
    protected JavaPlugin plugin;
    protected BukkitTask sidebarTask;
    protected long matchStartMillis;
    protected int startCountdownRemaining;
    protected int maxTeamSize = 4;
    protected boolean teamPickEnabled;
    protected BukkitTask lobbyCountdownTask;
    protected boolean lobbyCountdownPaused;
    protected int lobbyCountdownRemaining;
    protected UUID lobbyInitiatorId;
    protected Double previousBorderSize;
    protected Location previousBorderCenter;
    protected Double previousBorderDamageAmount;
    protected Double previousBorderDamageBuffer;
    protected Integer previousBorderWarningDistance;
    protected boolean statsEnabled = true;
    protected boolean testMode;
    protected int grantedMatchParticipationPartyExp;
    protected boolean partyExpUnavailableLogged;
    protected boolean matchEventRollEnabled = true;
    protected boolean forcedRandomMatchEvent;
    protected BedwarsMatchEventType forcedMatchEvent;
    protected BedwarsMatchEventType activeMatchEvent;
    protected Long previousWorldTime;
    protected Boolean previousDaylightCycle;
    protected GameSessionCustomItemRuntime customItemRuntime;
    protected GameSessionProximityMineRuntime proximityMineRuntime;
    protected GameSessionFalloutRuntime falloutRuntime;
    protected GameSessionSpinjitzuRuntime spinjitzuRuntime;
    protected GameSessionMoonBigAsteroidRuntime moonBigAsteroidRuntime;
    protected GameSessionTimeCapsuleRuntime timeCapsuleRuntime;
    protected GameSessionKarmaRuntime karmaRuntime;


    protected GameSessionEffectSupport(BedwarsManager bedwarsManager, Arena arena) {
        this.bedwarsManager = bedwarsManager;
        this.arena = arena;
    }

    protected abstract TeamColor getTeam(UUID playerId);

    protected abstract boolean isParticipant(UUID playerId);

    protected abstract boolean isRunning();

    protected abstract boolean isActive();

    protected abstract boolean isLobbyInitiator(UUID playerId);

    protected abstract boolean isInArenaWorld(World world);

    protected abstract boolean isLockedCommandSpectator(UUID playerId);

    protected abstract boolean isEditor(UUID playerId);

    protected abstract int getKillCount(UUID playerId);

    protected abstract int getTeamMemberCount(TeamColor team);

    protected abstract BedState getBedState(TeamColor team);

    protected abstract boolean hasTrapImmunity(UUID playerId);

    protected abstract void clearCombat(UUID playerId);

    protected abstract void clearTrapImmunity(UUID playerId);

    protected abstract void syncToolTiers(Player player);

    protected abstract void rewardFinalKill(UUID playerId);

    protected abstract boolean removeParticipant(Player player);

    protected abstract boolean removeParticipant(UUID playerId);

    protected abstract void awardPendingDeathFinalStats(UUID playerId);

    protected abstract void eliminatePlayer(Player player, TeamColor team);

    protected abstract void checkTeamEliminated(TeamColor team);

    protected abstract void scheduleRespawn(Player player, TeamColor team, int delaySeconds,
                                            boolean allowRespawnAfterBedBreak);

    protected abstract void clearAllElytraStrikes(boolean restoreChestplate);

    protected abstract void clearElytraStrike(Player player, boolean restoreChestplate, boolean grantLandingRegen);

    protected abstract void clearAbyssalRifts();

    protected record EventInfo(String label, int secondsRemaining) {
    }

    protected void startRespawnCountdown(Player player) {
        startRespawnCountdown(player, RESPAWN_DELAY_SECONDS);
    }

    protected void startRespawnCountdown(Player player, int delaySeconds) {
        startRespawnCountdown(player, delaySeconds, null, null);
    }

    protected void startRespawnCountdown(Player player,
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

    protected void sendBeaconRespawnTimerToTeam(UUID revivedPlayerId,
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

    protected void cancelRespawnCountdown(UUID playerId) {
        BukkitTask task = respawnCountdownTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    protected void safeRun(String context, Runnable action) {
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

    protected boolean isDisconnectedParticipant(UUID playerId) {
        return playerId != null && disconnectedParticipants.contains(playerId);
    }

    protected boolean isPartyExpEnabled() {
        return statsEnabled;
    }

    protected void queuePartyExp(UUID playerId, int amount) {
        if (!isPartyExpEnabled() || playerId == null || amount <= 0 || !isParticipant(playerId)) {
            return;
        }
        pendingPartyExp.merge(playerId, amount, Integer::sum);
    }

    protected void queuePartyExpForTeam(TeamColor team, int amount) {
        if (team == null || amount <= 0) {
            return;
        }
        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            if (team == entry.getValue()) {
                queuePartyExp(entry.getKey(), amount);
            }
        }
    }

    protected void queuePartyExpForAllParticipants(int amount) {
        if (amount <= 0) {
            return;
        }
        for (UUID playerId : assignments.keySet()) {
            queuePartyExp(playerId, amount);
        }
    }

    protected void updateMatchParticipationPartyExp(int targetAmount) {
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

    protected void finalizeMatchParticipationPartyExp() {
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

    protected void queueFinalPlacementPartyExp(TeamColor winner) {
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

    protected void flushPendingPartyExpForOnlineParticipants() {
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

    protected void flushPendingPartyExp(Player player) {
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

    protected void updateTeamsInMatch() {
        teamsInMatch.clear();
        for (TeamColor team : assignments.values()) {
            if (team != null) {
                teamsInMatch.add(team);
            }
        }
    }

    protected void initializeTeamUpgrades() {
        teamUpgrades.clear();
        for (TeamColor team : teamsInMatch) {
            teamUpgrades.put(team, new TeamUpgradeState());
        }
    }

    protected void initializeBaseCenters() {
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

    protected TeamUpgradeState getUpgradeState(TeamColor team) {
        if (team == null) {
            return new TeamUpgradeState();
        }
        return teamUpgrades.computeIfAbsent(team, key -> new TeamUpgradeState());
    }

    protected boolean hasTeamUpgrade(TeamColor team, TeamUpgradeType type) {
        return team != null && getUpgradeState(team).getTier(type) > 0;
    }

    protected void syncForgeTiers() {
        if (generatorManager == null) {
            return;
        }
        Map<TeamColor, Integer> tiers = new EnumMap<>(TeamColor.class);
        for (TeamColor team : teamsInMatch) {
            tiers.put(team, getUpgradeState(team).getTier(TeamUpgradeType.FORGE));
        }
        generatorManager.setBaseForgeTiers(tiers);
    }

    protected int getBaseEffectRadius() {
        int radius = arena.getBaseRadius();
        if (radius > 0) {
            return radius;
        }
        int fallback = arena.getBaseGeneratorRadius();
        return fallback > 0 ? fallback : DEFAULT_BASE_RADIUS;
    }

    protected void startUpgradeTasks() {
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

    protected void startRegenTask() {
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> safeRun("customRegen", this::applyRegenTick),
                20L,
                20L);
        tasks.add(task);
    }

    protected void applyRegenTick() {
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

    protected void applyHealPoolTick() {
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

    protected void checkTrapTriggers() {
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

    protected void triggerTrap(TeamColor team, TrapType trap, Player intruder, Set<UUID> currentIntruders) {
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

    protected void triggerIllusionTrap(TeamColor defendingTeam, Set<UUID> intruders) {
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

    protected void applyCounterOffensive(TeamColor team) {
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

    protected void announceTrap(TeamColor team, String name) {
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

    protected void announceTeamUpgrade(TeamColor team, Player buyer, TeamUpgradeType type, int tier) {
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

    protected void showTrapTitle(TeamColor team, String name) {
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

    protected void applyTeamUpgradeEffects(TeamColor team) {
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

    protected void applyTeamUpgrades(Player player, TeamColor team) {
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
        applyScale(player, resolveEffectiveScale(upgrades.getTier(TeamUpgradeType.SCALE_DOWN)));
        applyMatchEventEffects(player);
    }

    protected void applyProtection(Player player, int level) {
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

    protected void applySharpness(Player player) {
        applyEnchantment(player, SWORD_MATERIALS, Enchantment.SHARPNESS, 1);
    }

    protected void applyHaste(Player player, int tier) {
        int amplifier = Math.max(0, tier - 1);
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,
                Integer.MAX_VALUE,
                amplifier,
                true,
                false,
                true));
    }

    protected void applyEfficiency(Player player) {
        applyEnchantment(player, TOOL_EFFICIENCY_MATERIALS, Enchantment.EFFICIENCY, 1);
    }

    protected void applyFeatherFalling(Player player, int level) {
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

    protected void applyThorns(Player player, int level) {
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

    protected void applyFireAspect(Player player) {
        applyEnchantment(player, ATTACK_MATERIALS, Enchantment.FIRE_ASPECT, 1);
    }

    protected void applyMatchEventEffects(Player player) {
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
            case MOON_BIG -> setAttributeToValue(player, MOON_BIG_GRAVITY, "GRAVITY", "GENERIC_GRAVITY");
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
            case FALLOUT -> {
                if (falloutRuntime != null) {
                    falloutRuntime.applyTo(player);
                }
            }
            default -> {
            }
        }
    }

    protected void applyScale(Player player, double scale) {
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

    protected double resolveScaleDownValue(int tier) {
        return switch (tier) {
            case 1 -> SCALE_DOWN_TIER_ONE;
            case 2 -> SCALE_DOWN_TIER_TWO;
            default -> DEFAULT_PLAYER_SCALE;
        };
    }

    protected double resolveEffectiveScale(int scaleDownTier) {
        if (activeMatchEvent == BedwarsMatchEventType.APRIL_FOOLS) {
            return APRIL_FOOLS_SCALE_MULTIPLIER;
        }
        return resolveScaleDownValue(scaleDownTier);
    }

    protected double resolveMatchEventScaleMultiplier() {
        return activeMatchEvent == BedwarsMatchEventType.APRIL_FOOLS
                ? APRIL_FOOLS_SCALE_MULTIPLIER
                : DEFAULT_PLAYER_SCALE;
    }

    protected boolean setAttributeBaseValue(LivingEntity entity, String attributeName, double value) {
        if (entity == null) {
            return false;
        }
        Attribute attribute = resolveAttribute(attributeName);
        if (attribute == null) {
            return false;
        }
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return false;
        }
        instance.setBaseValue(value);
        return true;
    }

    protected boolean setAttributeToDefaultPlus(LivingEntity entity, double bonus, String... attributeNames) {
        org.bukkit.attribute.AttributeInstance instance = resolveAttributeInstance(entity, attributeNames);
        if (instance == null) {
            return false;
        }
        instance.setBaseValue(instance.getDefaultValue() + bonus);
        return true;
    }

    protected boolean setAttributeToValue(LivingEntity entity, double value, String... attributeNames) {
        org.bukkit.attribute.AttributeInstance instance = resolveAttributeInstance(entity, attributeNames);
        if (instance == null) {
            return false;
        }
        instance.setBaseValue(value);
        return true;
    }

    protected boolean setAttributeToDefaultMultiplier(LivingEntity entity, double multiplier, String... attributeNames) {
        org.bukkit.attribute.AttributeInstance instance = resolveAttributeInstance(entity, attributeNames);
        if (instance == null) {
            return false;
        }
        instance.setBaseValue(instance.getDefaultValue() * multiplier);
        return true;
    }

    protected void resetAttributeToDefault(LivingEntity entity, String... attributeNames) {
        org.bukkit.attribute.AttributeInstance instance = resolveAttributeInstance(entity, attributeNames);
        if (instance != null) {
            instance.setBaseValue(instance.getDefaultValue());
        }
    }

    protected AttributeInstance resolveAttributeInstance(LivingEntity entity, String... attributeNames) {
        if (entity == null || attributeNames == null) {
            return null;
        }
        for (String attributeName : attributeNames) {
            Attribute attribute = resolveAttribute(attributeName);
            if (attribute == null) {
                continue;
            }
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null) {
                return instance;
            }
        }
        return null;
    }

    protected Attribute resolveAttribute(String attributeName) {
        if (attributeName == null || attributeName.isBlank()) {
            return null;
        }
        String normalized = attributeName.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains(":")) {
            NamespacedKey key = NamespacedKey.fromString(normalized);
            return key == null ? null : Registry.ATTRIBUTE.get(key);
        }

        String keyName = normalized;
        if (keyName.startsWith("generic_")) {
            keyName = keyName.substring("generic_".length());
        } else if (keyName.startsWith("player_")) {
            keyName = keyName.substring("player_".length());
        }
        return Registry.ATTRIBUTE.get(NamespacedKey.minecraft(keyName));
    }

    protected PotionEffectType resolvePotionEffectType(String... names) {
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

    protected void applyEnchantment(Player player, Set<Material> materials, Enchantment enchantment, int level) {
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

    protected void removeHealPoolEffects(TeamColor team) {
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

    protected void clearUpgradeEffects(Player player) {
        if (player == null) {
            return;
        }
        if (falloutRuntime != null) {
            falloutRuntime.clearPlayer(player);
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
        setAttributeToValue(player, DEFAULT_PLAYER_MOVEMENT_SPEED, "MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED");
        setAttributeToValue(player, DEFAULT_PLAYER_STEP_HEIGHT, "STEP_HEIGHT", "GENERIC_STEP_HEIGHT");
        resetAttributeToDefault(player, "BLOCK_INTERACTION_RANGE", "PLAYER_BLOCK_INTERACTION_RANGE");
        resetAttributeToDefault(player, "JUMP_STRENGTH", "GENERIC_JUMP_STRENGTH");
        setAttributeToValue(player, DEFAULT_PLAYER_GRAVITY, "GRAVITY", "GENERIC_GRAVITY");
        resetAttributeToDefault(player, "MAX_HEALTH", "GENERIC_MAX_HEALTH");
        applyScale(player, DEFAULT_PLAYER_SCALE);
        clampPlayerHealthToMax(player);
    }

    protected void clampPlayerHealthToMax(Player player) {
        if (player == null || player.isDead()) {
            return;
        }
        double currentHealth = player.getHealth();
        if (currentHealth <= 0.0) {
            return;
        }
        player.setHealth(Math.min(currentHealth, player.getMaxHealth()));
    }

    protected void applyEditorInvisibility(Player player) {
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

    protected void applyLobbyBuffsToLobbyPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isInArenaWorld(player.getWorld()) || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            applyLobbyBuffs(player);
        }
    }

    protected void applyLobbyBuffs(Player player) {
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

    protected void hideEditorFromParticipants(Player editor) {
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

    protected void hideEditorsFrom(Player viewer) {
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

    protected void showEditorToParticipants(Player editor) {
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

    protected void clearEditors() {
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

    protected void spawnShops() {
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

    protected void despawnShops() {
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

    protected void spawnShopNpc(World world, ShopLocation location, ShopType type) {
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

    protected void clearExistingShopNpc(World world, Location spawn) {
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

    protected boolean isWithinRadius(BlockPoint a, BlockPoint b, int radius) {
        int dx = a.x() - b.x();
        int dz = a.z() - b.z();
        return dx * dx + dz * dz <= radius * radius;
    }

    protected String toRoman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(tier);
        };
    }

}
