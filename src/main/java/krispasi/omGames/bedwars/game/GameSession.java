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
import org.bukkit.entity.Entity;
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

public class GameSession extends GameSessionMatchFlowSupport {

    public GameSession(BedwarsManager bedwarsManager, Arena arena) {
        super(bedwarsManager, arena);
        this.customItemRuntime = new GameSessionCustomItemRuntime(
                this,
                arena,
                assignments,
                eliminatedPlayers,
                pendingRespawns,
                teamsInMatch,
                tasks);
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

    public boolean isSuddenDeathActive() {
        return suddenDeathActive;
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

    public double getEffectivePlayerScale(Player player) {
        if (player == null) {
            return DEFAULT_PLAYER_SCALE;
        }
        TeamColor team = getTeam(player.getUniqueId());
        if (team == null) {
            return resolveMatchEventScaleMultiplier();
        }
        TeamUpgradeState upgrades = getUpgradeState(team);
        return resolveEffectiveScale(upgrades.getTier(TeamUpgradeType.SCALE_DOWN));
    }

    public void restorePlayerScale(Player player) {
        if (player == null) {
            return;
        }
        applyScale(player, getEffectivePlayerScale(player));
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

    public TeamColor resolveBaseOwner(Block block) {
        if (block == null) {
            return null;
        }
        return resolveBaseOwner(new BlockPoint(block.getX(), block.getY(), block.getZ()));
    }

    public TeamColor resolveBaseOwner(BlockPoint point) {
        if (point == null) {
            return null;
        }
        int baseRadius = arena.getBaseRadius();
        if (baseRadius <= 0) {
            return null;
        }
        TeamColor owner = null;
        int bestDistanceSquared = Integer.MAX_VALUE;
        for (Map.Entry<TeamColor, BlockPoint> entry : baseCenters.entrySet()) {
            TeamColor team = entry.getKey();
            BlockPoint center = entry.getValue();
            if (team == null || center == null || !teamsInMatch.contains(team)) {
                continue;
            }
            int dx = point.x() - center.x();
            int dz = point.z() - center.z();
            int distanceSquared = dx * dx + dz * dz;
            if (distanceSquared > baseRadius * baseRadius || distanceSquared >= bestDistanceSquared) {
                continue;
            }
            owner = team;
            bestDistanceSquared = distanceSquared;
        }
        return owner;
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
        openFakeEnderChest(player, player != null ? player.getUniqueId() : null);
    }

    public Inventory getFakeEnderChest(Player player) {
        return player == null ? null : getFakeEnderChest(player.getUniqueId());
    }

    public void openFakeEnderChest(Player viewer, UUID ownerId) {
        if (viewer == null) {
            return;
        }
        Inventory inventory = getFakeEnderChest(ownerId != null ? ownerId : viewer.getUniqueId());
        if (inventory != null) {
            viewer.openInventory(inventory);
        }
    }

    public Inventory getFakeEnderChest(UUID ownerId) {
        if (ownerId == null) {
            return null;
        }
        return fakeEnderChests.computeIfAbsent(ownerId,
                id -> Bukkit.createInventory(null, 27, Component.text("Ender Chest")));
    }

    public boolean canAccessBaseChest(Player player, Block block) {
        if (player == null || block == null) {
            return false;
        }
        TeamColor ownerTeam = resolveBaseOwner(block);
        if (ownerTeam == null) {
            return true;
        }
        TeamColor playerTeam = getTeam(player.getUniqueId());
        if (playerTeam == ownerTeam || getBedState(ownerTeam) == BedState.DESTROYED) {
            return true;
        }
        return customItemRuntime.hasChestLockpickAccess(player.getUniqueId(), ownerTeam);
    }

    public boolean beginChestLockpick(Player player, Block chestBlock) {
        return customItemRuntime.beginChestLockpick(player, resolveBaseOwner(chestBlock), chestBlock, plugin);
    }

    public boolean beginEnderChestLockpick(Player player, Block chestBlock, UUID targetPlayerId) {
        return customItemRuntime.beginEnderChestLockpick(player,
                resolveBaseOwner(chestBlock),
                chestBlock,
                targetPlayerId,
                plugin);
    }

    public UUID resolveAccessibleEnderChestOwner(Player player, Block chestBlock, boolean allowLockpickAccess) {
        if (player == null) {
            return null;
        }
        TeamColor ownerTeam = resolveBaseOwner(chestBlock);
        UUID overrideOwner = allowLockpickAccess && ownerTeam != null
                ? customItemRuntime.resolveEnderChestLockpickTarget(player.getUniqueId(), ownerTeam)
                : null;
        return overrideOwner != null ? overrideOwner : player.getUniqueId();
    }

    public UUID resolveAccessibleEnderChestOwner(Player player, Block chestBlock) {
        return resolveAccessibleEnderChestOwner(player, chestBlock, true);
    }

    public Inventory getAccessibleEnderChest(Player player, Block chestBlock, boolean allowLockpickAccess) {
        UUID ownerId = resolveAccessibleEnderChestOwner(player, chestBlock, allowLockpickAccess);
        return ownerId == null ? null : getFakeEnderChest(ownerId);
    }

    public Inventory getAccessibleEnderChest(Player player, Block chestBlock) {
        return getAccessibleEnderChest(player, chestBlock, true);
    }

    public void openAccessibleEnderChest(Player player, Block chestBlock, boolean allowLockpickAccess) {
        UUID ownerId = resolveAccessibleEnderChestOwner(player, chestBlock, allowLockpickAccess);
        openFakeEnderChest(player, ownerId);
    }

    public void openAccessibleEnderChest(Player player, Block chestBlock) {
        openAccessibleEnderChest(player, chestBlock, true);
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
        ShopCost cost = getEffectiveShopCost(item);
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
        return customItemRuntime.activateElytraStrike(player, custom, plugin);
    }

    public boolean activateUnstableTeleportationDevice(Player player, CustomItemDefinition custom) {
        return customItemRuntime.activateUnstableTeleportationDevice(player, custom);
    }

    public boolean activateMiracleOfTheStars(Player player, CustomItemDefinition custom) {
        return customItemRuntime.activateMiracleOfTheStars(player, custom, plugin);
    }

    public void handleElytraStrikeMovement(Player player) {
        customItemRuntime.handleElytraStrikeMovement(player);
    }

    public boolean isActiveElytraStrikeItem(ItemStack item) {
        return customItemRuntime.isActiveElytraStrikeItem(item);
    }

    protected void clearAllElytraStrikes(boolean restoreChestplate) {
        customItemRuntime.clearAllElytraStrikes(restoreChestplate);
    }

    protected void clearElytraStrike(Player player, boolean restoreChestplate, boolean grantLandingRegen) {
        customItemRuntime.clearElytraStrike(player, restoreChestplate, grantLandingRegen);
    }

    public boolean deployAbyssalRift(Player player, CustomItemDefinition custom, Block clickedBlock) {
        return customItemRuntime.deployAbyssalRift(player, custom, clickedBlock, plugin);
    }

    public boolean deployTowerChest(Player player, Block clickedBlock, BlockFace clickedFace) {
        return customItemRuntime.deployTowerChest(player, clickedBlock, clickedFace, plugin);
    }

    public boolean isAbyssalRiftEntity(Entity entity) {
        return customItemRuntime.isAbyssalRiftEntity(entity);
    }

    public boolean damageAbyssalRift(Entity entity, Player attacker, double damage) {
        return customItemRuntime.damageAbyssalRift(entity, attacker, damage);
    }

    protected void clearAbyssalRifts() {
        customItemRuntime.clearAbyssalRifts();
    }

}
