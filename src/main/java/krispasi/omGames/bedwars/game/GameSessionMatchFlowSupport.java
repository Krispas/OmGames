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
import krispasi.omGames.bedwars.timecapsule.TimeCapsuleItemData;
import krispasi.omGames.bedwars.upgrade.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
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

abstract class GameSessionMatchFlowSupport extends GameSessionRuntimeSupport {

    protected GameSessionMatchFlowSupport(BedwarsManager bedwarsManager, Arena arena) {
        super(bedwarsManager, arena);
    }

    private static final List<Material> MOON_BIG_DEBRIS_MATERIALS = List.of(
            Material.BASALT,
            Material.DEEPSLATE,
            Material.COBBLED_DEEPSLATE
    );
    private static final List<AsteroidLootEntry> MOON_BIG_ASTEROID_NORMAL_LOOT_POOL = List.of(
            new AsteroidLootEntry(Material.IRON_INGOT, 8, 24),
            new AsteroidLootEntry(Material.GOLD_INGOT, 4, 16),
            new AsteroidLootEntry(Material.DIAMOND, 1, 3),
            new AsteroidLootEntry(Material.EMERALD, 1, 2),
            new AsteroidLootEntry(Material.ARROW, 8, 20),
            new AsteroidLootEntry(Material.TNT, 1, 3),
            new AsteroidLootEntry(Material.GOLDEN_APPLE, 1, 2),
            new AsteroidLootEntry(Material.ENDER_PEARL, 1, 2)
    );

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
        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
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
        List<UUID> startingParticipants = new ArrayList<>();
        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            if (entry.getValue() != null) {
                startingParticipants.add(entry.getKey());
            }
        }
        Map<UUID, ItemStack> startingTimeCapsules = timeCapsuleRuntime != null
                ? timeCapsuleRuntime.createMatchRewardItems(startingParticipants)
                : Map.of();

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
            ItemStack startingTimeCapsule = startingTimeCapsules.get(player.getUniqueId());
            if (startingTimeCapsule != null) {
                giveItem(player, startingTimeCapsule);
                String sourcePlayerName = TimeCapsuleItemData.getSourcePlayerName(startingTimeCapsule);
                if (sourcePlayerName != null && !sourcePlayerName.isBlank()) {
                    player.sendMessage(Component.text("You received ", NamedTextColor.YELLOW)
                            .append(Component.text(sourcePlayerName, NamedTextColor.AQUA))
                            .append(Component.text("'s Time Capsule from a previous match.", NamedTextColor.YELLOW)));
                } else {
                    player.sendMessage(Component.text("You received a Time Capsule from a previous match.", NamedTextColor.YELLOW));
                }
            }
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
        if (spinjitzuRuntime != null) {
            spinjitzuRuntime.clear(playerId);
        }
        clearUpgradeEffects(player);
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
            clearUpgradeEffects(player);
            setSpectator(player);
            if (lobby != null) {
                player.teleport(lobby);
            }
            return;
        }
        if (!pendingRespawns.contains(playerId)) {
            return;
        }
        clearUpgradeEffects(player);
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
        removeDisconnectedParticipantsWithoutRespawnGrace(team);
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
        if (getTeamMemberCount(team) <= 1) {
            activator.sendMessage(Component.text("Respawn Beacon cannot manually revive a solo team.", NamedTextColor.RED));
            return false;
        }
        List<Player> targets = new ArrayList<>();
        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            if (entry.getValue() != team) {
                continue;
            }
            UUID playerId = entry.getKey();
            if (playerId.equals(activatorId)) {
                continue;
            }
            if (!eliminatedPlayers.contains(playerId)) {
                continue;
            }
            Player target = Bukkit.getPlayer(playerId);
            if (target == null || !target.isOnline()) {
                continue;
            }
            targets.add(target);
        }
        if (targets.isEmpty()) {
            activator.sendMessage(Component.text("No eliminated teammates to respawn.", NamedTextColor.RED));
            return false;
        }
        showTitleAll(
                Component.empty()
                        .append(team.displayComponent())
                        .append(Component.text(" Used the Respawn Beacon.", NamedTextColor.WHITE)),
                Component.empty()
        );
        for (Player target : targets) {
            UUID targetId = target.getUniqueId();
            eliminatedPlayers.remove(targetId);
            respawnGracePlayers.add(targetId);
            setSpectator(target);
            scheduleRespawn(target, team, delaySeconds, true, true);
        }
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

    public int getDefaultRespawnDelaySeconds() {
        return RESPAWN_DELAY_SECONDS;
    }

    protected void playSoundToParticipants(Sound sound, float volume, float pitch) {
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

    protected void startCountdown(int seconds) {
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

    protected void startLobbyCountdown() {
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

    protected void beginMatchFromLobby() {
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

    protected void beginRunning() {
        state = GameState.RUNNING;
        frozenPlayers.clear();
        matchStartMillis = System.currentTimeMillis();
        startCountdownRemaining = 0;
        if (statsEnabled) {
            for (UUID playerId : assignments.keySet()) {
                bedwarsManager.getStatsService().addGamePlayed(playerId);
            }
        }
        generatorManager = new GeneratorManager(plugin, arena, (GameSession) this);
        syncForgeTiers();
        applyPreStartGeneratorEventOverrides();
        generatorManager.start();
        startUpgradeTasks();
        startRegenTask();
        scheduleGameEvents();
        applyMatchEventToParticipants();
        startMatchEventTasks();
        announceMatchEvent();
        announceCurrentRotatingItems();
        if (karmaRuntime != null) {
            karmaRuntime.start(plugin);
        }
    }

    protected void scheduleGameEvents() {
        krispasi.omGames.bedwars.model.EventSettings events = arena.getEventSettings();
        scheduleLongMatchPartyExp(LONG_MATCH_PARTY_EXP_SECONDS);
        scheduleTierUpgrade(2, events.getTier2Delay());
        scheduleTierUpgrade(3, events.getTier3Delay());
        scheduleBedDestruction(events.getBedDestructionDelay());
        scheduleSuddenDeath(events.getSuddenDeathDelay());
        scheduleGameEnd(events.getGameEndDelay());
    }

    protected void rollMatchEvent() {
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
        if (forcedRandomMatchEvent) {
            activeMatchEvent = pickWeightedMatchEvent(config);
            return;
        }
        double roll = ThreadLocalRandom.current().nextDouble(100.0);
        if (roll >= config.chancePercent()) {
            return;
        }
        activeMatchEvent = pickWeightedMatchEvent(config);
    }

    protected BedwarsMatchEventType pickWeightedMatchEvent(BedwarsMatchEventConfig config) {
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

    protected void applyPreMatchEventSetup() {
        if (activeMatchEvent == BedwarsMatchEventType.BENEVOLENT_UPGRADES) {
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
            return;
        }
        if (activeMatchEvent != BedwarsMatchEventType.CHAOS) {
            return;
        }
        for (TeamColor team : teamsInMatch) {
            getUpgradeState(team).setTier(TeamUpgradeType.FORGE, TeamUpgradeType.FORGE.maxTier());
        }
        tier2Triggered = true;
        tier3Triggered = true;
    }

    protected void applyMatchEventRotatingOverrides() {
        if (activeMatchEvent == BedwarsMatchEventType.CHAOS) {
            rotatingItemIds.clear();
            rotatingUpgradeIds.clear();
            rotatingItemIds.addAll(getRotatingItemCandidateIds());
            rotatingUpgradeIds.addAll(getRotatingUpgradeCandidateIds());
            return;
        }
        if (activeMatchEvent != BedwarsMatchEventType.APRIL_FOOLS) {
            return;
        }
        String bedrockId = normalizeItemId("bedrock");
        String ridingFireballId = normalizeItemId("riding_fireball");
        java.util.List<String> forcedAprilFoolsItems = new java.util.ArrayList<>();
        if (bedrockId != null && isRotatingItemCandidate(bedrockId)) {
            forcedAprilFoolsItems.add(bedrockId);
        }
        if (ridingFireballId != null
                && isRotatingItemCandidate(ridingFireballId)
                && !forcedAprilFoolsItems.contains(ridingFireballId)) {
            forcedAprilFoolsItems.add(ridingFireballId);
        }
        if (forcedAprilFoolsItems.isEmpty()) {
            return;
        }
        int target = 2;
        java.util.LinkedHashSet<String> adjusted = new java.util.LinkedHashSet<>();
        for (String forcedId : forcedAprilFoolsItems) {
            if (adjusted.size() >= target) {
                break;
            }
            adjusted.add(forcedId);
        }
        for (String id : rotatingItemIds) {
            if (adjusted.size() >= target) {
                break;
            }
            if (!adjusted.contains(id)) {
                adjusted.add(id);
            }
        }
        if (adjusted.size() < target) {
            for (String candidate : getRotatingItemCandidateIds()) {
                if (adjusted.size() >= target) {
                    break;
                }
                if (!adjusted.contains(candidate)) {
                    adjusted.add(candidate);
                }
            }
        }
        rotatingItemIds.clear();
        rotatingItemIds.addAll(adjusted);
    }

    protected void applyPreStartGeneratorEventOverrides() {
        if (generatorManager == null || activeMatchEvent != BedwarsMatchEventType.CHAOS) {
            return;
        }
        generatorManager.setDiamondTier(3);
        generatorManager.setEmeraldTier(3);
    }

    protected void applyMatchEventToParticipants() {
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

    protected void startMatchEventTasks() {
        if (activeMatchEvent == null || plugin == null) {
            return;
        }
        if (activeMatchEvent == BedwarsMatchEventType.MOON_BIG) {
            prepareBloodMoonWorld();
            startMoonBigAsteroidLoop();
            BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                World world = arena.getWorld();
                if (world == null) {
                    return;
                }
                world.setTime(18000L);
            }, 0L, 40L);
            tasks.add(task);
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
        if (activeMatchEvent == BedwarsMatchEventType.FALLOUT) {
            if (falloutRuntime != null) {
                falloutRuntime.start();
            }
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

    protected void prepareBloodMoonWorld() {
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

    protected void startMoonBigAsteroidLoop() {
        if (moonBigAsteroidRuntime != null) {
            moonBigAsteroidRuntime.startLoop();
        }
    }

    protected double resolveMoonBigAsteroidIntervalSeconds(BedwarsMoonBigConfig config) {
        if (config == null) {
            return 0.0;
        }
        double elapsedSeconds = 0.0;
        if (matchStartMillis > 0L) {
            elapsedSeconds = Math.max(0.0, (System.currentTimeMillis() - matchStartMillis) / 1000.0);
        }
        int totalDuration = 0;
        if (arena != null && arena.getEventSettings() != null) {
            totalDuration = Math.max(0, arena.getEventSettings().getGameEndDelay());
        }
        double ratio = totalDuration > 0 ? Math.min(1.0, elapsedSeconds / totalDuration) : 1.0;
        double minInterval = lerp(config.startIntervalMinSeconds(), config.endIntervalMinSeconds(), ratio);
        double maxInterval = lerp(config.startIntervalMaxSeconds(), config.endIntervalMaxSeconds(), ratio);
        if (maxInterval < minInterval) {
            double swap = minInterval;
            minInterval = maxInterval;
            maxInterval = swap;
        }
        if (maxInterval <= 0.0) {
            return 0.0;
        }
        if (Math.abs(maxInterval - minInterval) < 0.0001) {
            return Math.max(0.0, maxInterval) * 0.1;
        }
        return ThreadLocalRandom.current().nextDouble(minInterval, maxInterval) * 0.1;
    }

    protected void spawnMoonBigAsteroidParticles(World world, Location location) {
        if (world == null || location == null) {
            return;
        }
        world.spawnParticle(Particle.FLAME, location, 6, 0.2, 0.2, 0.2, 0.01);
        world.spawnParticle(Particle.SMOKE, location, 3, 0.2, 0.2, 0.2, 0.01);
    }

    protected void handleMoonBigAsteroidImpact(Location location, int radius, BedwarsMoonBigConfig config) {
        if (location == null || config == null) {
            return;
        }
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        float power = (float) (radius * config.explosionPowerMultiplier());
        world.createExplosion(location, power, false, false);
        world.spawnParticle(Particle.EXPLOSION, location, 1);
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 8.0f, 0.9f);
        damageMoonBigAsteroidImpact(world, location, radius);
        placeMoonBigAsteroidDebris(location, radius, config);
    }

    protected void damageMoonBigAsteroidImpact(World world, Location location, int radius) {
        if (world == null || location == null) {
            return;
        }
        double range = Math.max(2.0, radius + 1.0);
        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(location, range, range, range)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            living.damage(2.0);
        }
    }

    protected void placeMoonBigAsteroidDebris(Location impact,
                                              int radius,
                                              BedwarsMoonBigConfig config) {
        if (impact == null || config == null) {
            return;
        }
        World world = impact.getWorld();
        if (world == null) {
            return;
        }
        int centerX = impact.getBlockX();
        int centerY = impact.getBlockY();
        int centerZ = impact.getBlockZ();
        double missingChance = config.missingBlockChance();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) {
                        continue;
                    }
                    if (ThreadLocalRandom.current().nextDouble() < missingChance) {
                        continue;
                    }
                    int x = centerX + dx;
                    int y = centerY + dy;
                    int z = centerZ + dz;
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.getType().isAir()) {
                        continue;
                    }
                    Material material = MOON_BIG_DEBRIS_MATERIALS.get(
                            ThreadLocalRandom.current().nextInt(MOON_BIG_DEBRIS_MATERIALS.size()));
                    block.setType(material, false);
                    placedBlocks.add(new BlockPoint(x, y, z));
                }
            }
        }
        if (ThreadLocalRandom.current().nextDouble() < config.crateChance()) {
            placeMoonBigAsteroidCrate(world, centerX, centerY, centerZ);
        }
    }

    protected void placeMoonBigAsteroidCrate(World world, int centerX, int centerY, int centerZ) {
        if (world == null) {
            return;
        }
        Block crateBlock = world.getBlockAt(centerX, centerY, centerZ);
        if (!crateBlock.getType().isAir()) {
            return;
        }
        crateBlock.setType(Material.BARREL, false);
        placedBlocks.add(new BlockPoint(crateBlock.getX(), crateBlock.getY(), crateBlock.getZ()));
        BlockState state = crateBlock.getState();
        if (state instanceof Barrel barrel) {
            barrel.setCustomName("Asteroid Crate");
            fillMoonBigAsteroidCrate(barrel);
            barrel.update();
        }
    }

    protected void fillMoonBigAsteroidCrate(Barrel barrel) {
        if (barrel == null) {
            return;
        }
        Inventory inventory = barrel.getInventory();
        inventory.clear();
        AsteroidLootEntry normal = pickRandomNormalAsteroidLoot();
        if (normal != null) {
            int amount = normal.rollAmount();
            if (amount > 0) {
                inventory.addItem(new ItemStack(normal.material(), amount));
            }
        }
        ShopItemDefinition rotating = pickRandomRotatingAsteroidLoot();
        if (rotating != null) {
            inventory.addItem(rotating.createPurchaseItem(null));
        }
    }

    protected double lerp(double start, double end, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return start + (end - start) * clamped;
    }

    private AsteroidLootEntry pickRandomNormalAsteroidLoot() {
        if (MOON_BIG_ASTEROID_NORMAL_LOOT_POOL.isEmpty()) {
            return null;
        }
        return MOON_BIG_ASTEROID_NORMAL_LOOT_POOL.get(
                ThreadLocalRandom.current().nextInt(MOON_BIG_ASTEROID_NORMAL_LOOT_POOL.size()));
    }

    private ShopItemDefinition pickRandomRotatingAsteroidLoot() {
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return null;
        }
        krispasi.omGames.bedwars.shop.ShopCategory category = config.getCategory(ShopCategoryType.ROTATING);
        if (category == null || category.getEntries().isEmpty()) {
            return null;
        }
        List<ShopItemDefinition> pool = new ArrayList<>();
        for (String id : category.getEntries().values()) {
            String normalized = normalizeItemId(id);
            if (normalized == null) {
                continue;
            }
            ShopItemDefinition definition = config.getItem(normalized);
            if (definition != null) {
                pool.add(definition);
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private record AsteroidLootEntry(Material material, int minAmount, int maxAmount) {
        int rollAmount() {
            if (maxAmount <= minAmount) {
                return Math.max(0, minAmount);
            }
            return ThreadLocalRandom.current().nextInt(minAmount, maxAmount + 1);
        }
    }

    protected void restoreMatchEventWorldState() {
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

    protected void announceMatchEvent() {
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

    protected void triggerTierUpgrade(int tier) {
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

    protected void scheduleTierUpgrade(int tier, int delaySeconds) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            safeRun("tierUpgrade", () -> {
                triggerTierUpgrade(tier);
            });
        }, delaySeconds * 20L);
        tasks.add(task);
    }

    protected void scheduleAnnouncement(Component message, int delaySeconds) {
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

    protected void scheduleBedDestruction(int delaySeconds) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            safeRun("bedDestruction", () -> {
                triggerBedDestructionEvent();
            });
        }, delaySeconds * 20L);
        tasks.add(task);
    }

    protected void scheduleLongMatchPartyExp(int delaySeconds) {
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

    protected void scheduleSuddenDeath(int delaySeconds) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            safeRun("suddenDeath", () -> {
                triggerSuddenDeath();
            });
        }, delaySeconds * 20L);
        tasks.add(task);
    }

    protected void scheduleGameEnd(int delaySeconds) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            safeRun("gameEnd", () -> {
                triggerGameEnd();
            });
        }, delaySeconds * 20L);
        tasks.add(task);
    }

    protected void triggerBedDestructionEvent() {
        if (state != GameState.RUNNING || bedDestructionTriggered) {
            return;
        }
        bedDestructionTriggered = true;
        updateMatchParticipationPartyExp(PARTY_EXP_BED_GONE_MATCH);
        triggerBedDestruction();
    }

    protected void triggerSuddenDeath() {
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

    protected void triggerGameEnd() {
        if (state != GameState.RUNNING || gameEndTriggered) {
            return;
        }
        gameEndTriggered = true;
        List<TeamColor> aliveTeams = getAliveTeams();
        if (aliveTeams.size() == 1) {
            bedwarsManager.endSession((GameSession) this, aliveTeams.get(0));
            return;
        }
        bedwarsManager.endSession((GameSession) this, null);
    }

    protected void startSuddenDeathBorderShrink() {
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

    protected void scheduleRespawn(Player player, TeamColor team) {
        scheduleRespawn(player, team, RESPAWN_DELAY_SECONDS, false);
    }

    protected void scheduleRespawn(Player player, TeamColor team, int delaySeconds, boolean allowRespawnAfterBedBreak) {
        scheduleRespawn(player, team, delaySeconds, allowRespawnAfterBedBreak, false);
    }

    protected void scheduleRespawn(Player player,
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
                clearUpgradeEffects(player);
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
            clearUpgradeEffects(player);
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

    protected void grantPendingRespawnGrace(TeamColor team) {
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

    protected void clearPendingDeathCredit(UUID playerId) {
        if (playerId == null) {
            return;
        }
        pendingDeathKillCredits.remove(playerId);
    }

    protected void awardPendingDeathFinalStats(UUID playerId) {
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

    protected void eliminatePlayer(Player player, TeamColor team) {
        UUID playerId = player.getUniqueId();
        eliminatedPlayers.add(playerId);
        pendingRespawns.remove(playerId);
        respawnGracePlayers.remove(playerId);
        clearPendingDeathCredit(playerId);
        setSpectator(player);
        checkTeamEliminated(team);
    }

    protected void checkTeamEliminated(TeamColor team) {
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

    protected void checkForWin() {
        List<TeamColor> aliveTeams = getAliveTeams();
        if (aliveTeams.size() == 1) {
            bedwarsManager.endSession((GameSession) this, aliveTeams.get(0));
        } else if (aliveTeams.isEmpty()) {
            bedwarsManager.endSession((GameSession) this, null);
        }
    }

    protected List<TeamColor> getAliveTeams() {
        List<TeamColor> aliveTeams = new ArrayList<>();
        for (TeamColor team : teamsInMatch) {
            if (isTeamAlive(team)) {
                aliveTeams.add(team);
            }
        }
        return aliveTeams;
    }

    protected boolean isTeamAlive(TeamColor team) {
        if (!hasPlayers(team)) {
            return false;
        }
        if (getBedState(team) == BedState.ALIVE) {
            return true;
        }
        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            UUID playerId = entry.getKey();
            if (entry.getValue() == team
                    && !eliminatedPlayers.contains(playerId)
                    && (!disconnectedParticipants.contains(playerId)
                    || respawnGracePlayers.contains(playerId))) {
                return true;
            }
        }
        return false;
    }

    protected void removeDisconnectedParticipantsWithoutRespawnGrace(TeamColor team) {
        if (team == null) {
            return;
        }
        for (UUID playerId : new ArrayList<>(disconnectedParticipants)) {
            if (assignments.get(playerId) != team || respawnGracePlayers.contains(playerId)) {
                continue;
            }
            removeParticipant(playerId);
        }
    }

    protected boolean hasPlayers(TeamColor team) {
        for (TeamColor value : assignments.values()) {
            if (value == team) {
                return true;
            }
        }
        return false;
    }

    protected void initializeBeds() {
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

    protected void destroyBed(TeamColor team) {
        bedStates.put(team, BedState.DESTROYED);
        removeHealPoolEffects(team);
        BedLocation location = clearTrackedBed(team);
        if (location == null) {
            return;
        }
        removeBedBlocks(location);
    }

    protected void setBedAlive(TeamColor team, BedLocation location) {
        if (team == null || location == null) {
            return;
        }
        bedStates.put(team, BedState.ALIVE);
        activeBedLocations.put(team, location);
        bedBlocks.put(location.head(), team);
        bedBlocks.put(location.foot(), team);
    }

    protected BedLocation clearTrackedBed(TeamColor team) {
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

    protected void reviveEliminatedTeammates(TeamColor team) {
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

    protected void restoreRevivedTeammate(Player player, TeamColor team, Location spawn) {
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

    protected void removeBedBlocks(BedLocation location) {
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        world.getBlockAt(location.head().x(), location.head().y(), location.head().z()).setType(Material.AIR, false);
        world.getBlockAt(location.foot().x(), location.foot().y(), location.foot().z()).setType(Material.AIR, false);
    }

    protected void triggerBedDestruction() {
        boolean changed = false;
        for (TeamColor team : teamsInMatch) {
            if (getBedState(team) == BedState.ALIVE) {
                destroyBed(team);
                removeDisconnectedParticipantsWithoutRespawnGrace(team);
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

    protected void cleanupWorld() {
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
        Location lobby = bedwarsManager.getLobbyLocation();
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

    protected void restoreBeds() {
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        for (Map.Entry<TeamColor, BedLocation> entry : arena.getBeds().entrySet()) {
            restoreBed(world, entry.getKey(), entry.getValue());
        }
    }

    protected void createTemporaryMapLobbyIsland() {
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
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                Block block = world.getBlockAt(centerX + dx, floorY, centerZ + dz);
                BlockPoint point = new BlockPoint(block.getX(), block.getY(), block.getZ());
                temporaryMapLobbyIslandBlocks.put(point, block.getState());
                block.setType(Material.BARRIER, false);
            }
        }
    }

    protected void removeTemporaryMapLobbyIsland() {
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

    protected void removeActivePlacedBeds() {
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

    protected void applyBedLayout() {
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

    protected void prepareWorld() {
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        forceLoadMap(world);
        clearDroppedItems(world);
        applyWorldBorder(world);
    }

    protected void forceLoadMap(World world) {
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

    protected void releaseForcedChunks() {
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

    protected long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }

    protected void clearDroppedItems(World world) {
        for (Item item : world.getEntitiesByClass(Item.class)) {
            item.remove();
        }
    }

    protected void restoreBed(World world, TeamColor team, BedLocation bed) {
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

    protected void stopInternal() {
        finalizeMatchParticipationPartyExp();
        flushPendingPartyExpForOnlineParticipants();
        state = GameState.ENDING;
        removeTemporaryMapLobbyIsland();
        clearAllElytraStrikes(false);
        if (spinjitzuRuntime != null) {
            spinjitzuRuntime.reset();
        }
        clearAbyssalRifts();
        clearEditors();
        releaseForcedChunks();
        restoreWorldBorder();
        restoreMatchEventWorldState();
        despawnShops();
        despawnSummons();
        closeFakeEnderChests();
        if (timeCapsuleRuntime != null) {
            timeCapsuleRuntime.closeOpenInventories();
        }
        if (karmaRuntime != null) {
            karmaRuntime.stop();
        }
        clearSidebars();
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        if (moonBigAsteroidRuntime != null) {
            moonBigAsteroidRuntime.reset();
        }
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

    protected void applyWorldBorder(World world) {
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

    protected void restoreWorldBorder() {
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

    protected void despawnSummons() {
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
                    || entity.getScoreboardTags().contains(ABYSSAL_RIFT_NAME_TAG)
                    || entity.getScoreboardTags().contains(MOON_BIG_ASTEROID_TAG)) {
                entity.remove();
            }
        }
    }

    protected void clearContainerInventories(World world) {
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

    protected void clearChunkContainers(Chunk chunk) {
        if (chunk == null) {
            return;
        }
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Container container) {
                container.getInventory().clear();
            }
        }
    }

    protected void resetState() {
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
        if (timeCapsuleRuntime != null) {
            timeCapsuleRuntime.reset();
        }
        if (proximityMineRuntime != null) {
            proximityMineRuntime.reset();
        }
        if (falloutRuntime != null) {
            falloutRuntime.reset();
        }
        if (spinjitzuRuntime != null) {
            spinjitzuRuntime.reset();
        }
        if (moonBigAsteroidRuntime != null) {
            moonBigAsteroidRuntime.reset();
        }
        if (karmaRuntime != null) {
            karmaRuntime.reset();
        }
        previousScoreboards.clear();
        activeScoreboards.clear();
        sidebarLines.clear();
        rotatingItemIds.clear();
        rotatingUpgradeIds.clear();
        customItemRuntime.reset();
        disconnectedParticipants.clear();
        killCounts.clear();
        pendingPartyExp.clear();
        teamPurchaseCounts.clear();
        playerPurchaseCounts.clear();
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

}
