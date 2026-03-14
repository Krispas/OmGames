package krispasi.omGames.bedwars.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import krispasi.omGames.bedwars.item.CustomItemData;
import krispasi.omGames.bedwars.item.CustomItemDefinition;
import krispasi.omGames.bedwars.model.Arena;
import krispasi.omGames.bedwars.model.BedState;
import krispasi.omGames.bedwars.model.BlockPoint;
import krispasi.omGames.bedwars.model.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class GameSessionCustomItemRuntime {
    private static final String ABYSSAL_RIFT_NAME = "Abyssal Rift: Domination";
    private static final String AIRSTRIKE_ELYTRA_NAME = "Airstrike Elytra";
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
    private static final int STEEL_SHELL_RESISTANCE_AMPLIFIER = 4;
    private static final long LOCKPICK_CHEST_DELAY_TICKS = 10L * 20L;
    private static final long LOCKPICK_ENDER_CHEST_DELAY_TICKS = 20L * 20L;
    private static final long LOCKPICK_ACCESS_DURATION_MILLIS = 60_000L;
    private static final double LOCKPICK_DISPLAY_Y_OFFSET = 1.2;
    private static final String LOCKPICK_DISPLAY_TAG = "bw_lockpick_display";
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

    private final GameSession session;
    private final Arena arena;
    private final Map<UUID, TeamColor> assignments;
    private final Set<UUID> eliminatedPlayers;
    private final Set<UUID> pendingRespawns;
    private final Set<TeamColor> teamsInMatch;
    private final List<BukkitTask> sessionTasks;
    private final Map<UUID, AbyssalRiftState> abyssalRifts = new HashMap<>();
    private final Map<UUID, UUID> abyssalRiftEntityLinks = new HashMap<>();
    private final Map<UUID, ElytraStrikeState> activeElytraStrikes = new HashMap<>();
    private final Map<UUID, SteelShellState> activeSteelShells = new HashMap<>();
    private final Map<UUID, Map<String, Long>> customItemCooldownEnds = new HashMap<>();
    private final Map<LockpickAccessKey, Long> chestLockpickAccessEnds = new HashMap<>();
    private final Map<LockpickAccessKey, EnderChestLockpickAccess> enderChestLockpickAccesses = new HashMap<>();
    private final Map<LockpickAccessKey, LockpickCountdownState> lockpickCountdowns = new HashMap<>();

    GameSessionCustomItemRuntime(GameSession session,
                                 Arena arena,
                                 Map<UUID, TeamColor> assignments,
                                 Set<UUID> eliminatedPlayers,
                                 Set<UUID> pendingRespawns,
                                 Set<TeamColor> teamsInMatch,
                                 List<BukkitTask> sessionTasks) {
        this.session = session;
        this.arena = arena;
        this.assignments = assignments;
        this.eliminatedPlayers = eliminatedPlayers;
        this.pendingRespawns = pendingRespawns;
        this.teamsInMatch = teamsInMatch;
        this.sessionTasks = sessionTasks;
    }

    void reset() {
        clearAllElytraStrikes(false);
        clearAbyssalRifts();
        clearAllSteelShells(false);
        clearLockpickState();
        customItemCooldownEnds.clear();
    }

    boolean hasChestLockpickAccess(UUID playerId, TeamColor baseTeam) {
        if (playerId == null || baseTeam == null) {
            return false;
        }
        LockpickAccessKey key = new LockpickAccessKey(playerId, baseTeam);
        Long expiresAt = chestLockpickAccessEnds.get(key);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt <= System.currentTimeMillis()) {
            chestLockpickAccessEnds.remove(key);
            return false;
        }
        return true;
    }

    UUID resolveEnderChestLockpickTarget(UUID playerId, TeamColor baseTeam) {
        if (playerId == null || baseTeam == null) {
            return null;
        }
        LockpickAccessKey key = new LockpickAccessKey(playerId, baseTeam);
        EnderChestLockpickAccess access = enderChestLockpickAccesses.get(key);
        if (access == null) {
            return null;
        }
        if (access.expiresAt() <= System.currentTimeMillis()) {
            enderChestLockpickAccesses.remove(key);
            return null;
        }
        if (assignments.get(access.targetPlayerId()) != baseTeam) {
            enderChestLockpickAccesses.remove(key);
            return null;
        }
        return access.targetPlayerId();
    }

    boolean beginChestLockpick(Player player, TeamColor ownerTeam, Block chestBlock, JavaPlugin plugin) {
        if (player == null || ownerTeam == null || chestBlock == null || plugin == null
                || !session.isRunning() || !session.isParticipant(player.getUniqueId())) {
            return false;
        }
        TeamColor playerTeam = assignments.get(player.getUniqueId());
        if (playerTeam == ownerTeam) {
            player.sendMessage(Component.text("Your team already owns this chest.", NamedTextColor.RED));
            return false;
        }
        if (session.getBedState(ownerTeam) == BedState.DESTROYED) {
            player.sendMessage(Component.text("That chest is already open to everyone.", NamedTextColor.RED));
            return false;
        }
        LockpickAccessKey key = new LockpickAccessKey(player.getUniqueId(), ownerTeam);
        if (lockpickCountdowns.containsKey(key)) {
            player.sendMessage(Component.text("You are already picking this team's chest.", NamedTextColor.RED));
            return false;
        }
        return startLockpickCountdown(player,
                chestBlock,
                key,
                "Chest unlock",
                (int) (LOCKPICK_CHEST_DELAY_TICKS / 20L),
                plugin,
                () -> {
                    chestLockpickAccessEnds.put(key, System.currentTimeMillis() + LOCKPICK_ACCESS_DURATION_MILLIS);
                    player.sendMessage(Component.text("You can open the ", NamedTextColor.GREEN)
                            .append(ownerTeam.displayComponent())
                            .append(Component.text(" team's chests for 60s.", NamedTextColor.GREEN)));
                });
    }

    boolean beginEnderChestLockpick(Player player,
                                    TeamColor ownerTeam,
                                    Block chestBlock,
                                    UUID targetPlayerId,
                                    JavaPlugin plugin) {
        if (player == null || ownerTeam == null || chestBlock == null || targetPlayerId == null || plugin == null
                || !session.isRunning() || !session.isParticipant(player.getUniqueId())) {
            return false;
        }
        TeamColor playerTeam = assignments.get(player.getUniqueId());
        if (playerTeam == ownerTeam) {
            player.sendMessage(Component.text("Your own team's ender chest already opens your storage.", NamedTextColor.RED));
            return false;
        }
        if (assignments.get(targetPlayerId) != ownerTeam) {
            player.sendMessage(Component.text("That player is no longer on this team.", NamedTextColor.RED));
            return false;
        }
        LockpickAccessKey key = new LockpickAccessKey(player.getUniqueId(), ownerTeam);
        if (lockpickCountdowns.containsKey(key)) {
            player.sendMessage(Component.text("You are already picking this team's ender chest.", NamedTextColor.RED));
            return false;
        }
        String targetName = resolveTargetPlayerName(targetPlayerId);
        return startLockpickCountdown(player,
                chestBlock,
                key,
                "Ender chest access",
                (int) (LOCKPICK_ENDER_CHEST_DELAY_TICKS / 20L),
                plugin,
                () -> {
                    enderChestLockpickAccesses.put(key,
                            new EnderChestLockpickAccess(targetPlayerId,
                                    System.currentTimeMillis() + LOCKPICK_ACCESS_DURATION_MILLIS));
                    player.sendMessage(Component.text("You can open ", NamedTextColor.GREEN)
                            .append(Component.text(targetName, NamedTextColor.YELLOW))
                            .append(Component.text("'s ender chest for 60s.", NamedTextColor.GREEN)));
                });
    }

    private boolean startLockpickCountdown(Player player,
                                           Block chestBlock,
                                           LockpickAccessKey key,
                                           String label,
                                           int countdownSeconds,
                                           JavaPlugin plugin,
                                           Runnable onComplete) {
        if (player == null || chestBlock == null || key == null || plugin == null || countdownSeconds <= 0) {
            return false;
        }
        ArmorStand display = spawnLockpickDisplay(chestBlock.getLocation().add(0.5, LOCKPICK_DISPLAY_Y_OFFSET, 0.5));
        if (display == null) {
            return false;
        }
        BukkitTask task = new BukkitRunnable() {
            private int remaining = countdownSeconds;

            @Override
            public void run() {
                if (!session.isRunning() || !player.isOnline() || !isValidLockpickTargetBlock(chestBlock)) {
                    clearLockpickCountdown(key);
                    cancel();
                    return;
                }
                ArmorStand currentDisplay = getLockpickDisplay(display.getUniqueId());
                if (currentDisplay == null) {
                    clearLockpickCountdown(key);
                    cancel();
                    return;
                }
                currentDisplay.teleport(chestBlock.getLocation().add(0.5, LOCKPICK_DISPLAY_Y_OFFSET, 0.5));
                currentDisplay.customName(Component.text(label + ": " + remaining + "s", NamedTextColor.GOLD));
                if (remaining <= 1) {
                    currentDisplay.remove();
                    lockpickCountdowns.remove(key);
                    onComplete.run();
                    cancel();
                    return;
                }
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        sessionTasks.add(task);
        lockpickCountdowns.put(key, new LockpickCountdownState(display.getUniqueId(), task));
        return true;
    }

    private void clearLockpickState() {
        for (LockpickCountdownState countdown : lockpickCountdowns.values()) {
            if (countdown.task() != null) {
                countdown.task().cancel();
            }
            removeLockpickDisplay(countdown.displayId());
        }
        lockpickCountdowns.clear();
        chestLockpickAccessEnds.clear();
        enderChestLockpickAccesses.clear();
    }

    private void clearLockpickCountdown(LockpickAccessKey key) {
        if (key == null) {
            return;
        }
        LockpickCountdownState countdown = lockpickCountdowns.remove(key);
        if (countdown == null) {
            return;
        }
        if (countdown.task() != null) {
            countdown.task().cancel();
        }
        removeLockpickDisplay(countdown.displayId());
    }

    private ArmorStand spawnLockpickDisplay(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setCustomNameVisible(true);
            stand.addScoreboardTag(LOCKPICK_DISPLAY_TAG);
        });
    }

    private ArmorStand getLockpickDisplay(UUID displayId) {
        if (displayId == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(displayId);
        return entity instanceof ArmorStand stand ? stand : null;
    }

    private void removeLockpickDisplay(UUID displayId) {
        ArmorStand display = getLockpickDisplay(displayId);
        if (display != null) {
            display.remove();
        }
    }

    private boolean isValidLockpickTargetBlock(Block block) {
        if (block == null || block.getWorld() == null) {
            return false;
        }
        Material type = block.getType();
        return type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.ENDER_CHEST;
    }

    private String resolveTargetPlayerName(UUID targetPlayerId) {
        if (targetPlayerId == null) {
            return "Unknown";
        }
        Player online = Bukkit.getPlayer(targetPlayerId);
        if (online != null) {
            return online.getName();
        }
        String offlineName = Bukkit.getOfflinePlayer(targetPlayerId).getName();
        if (offlineName != null && !offlineName.isBlank()) {
            return offlineName;
        }
        return targetPlayerId.toString().substring(0, 8);
    }

    boolean activateElytraStrike(Player player, CustomItemDefinition custom, JavaPlugin plugin) {
        if (player == null || custom == null || plugin == null
                || !session.isRunning() || !session.isParticipant(player.getUniqueId())) {
            return false;
        }
        if (activeElytraStrikes.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("You are already in an airstrike.", NamedTextColor.RED));
            return false;
        }
        TeamColor team = session.getTeam(player.getUniqueId());
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
        UUID playerId = player.getUniqueId();
        Vector finalLaunchVelocity = launchVelocity;
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

    boolean activateUnstableTeleportationDevice(Player player, CustomItemDefinition custom) {
        if (player == null
                || custom == null
                || !session.isRunning()
                || !session.isParticipant(player.getUniqueId())
                || !session.isInArenaWorld(player.getWorld())) {
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

    boolean activateMiracleOfTheStars(Player player, CustomItemDefinition custom, JavaPlugin plugin) {
        if (player == null
                || custom == null
                || plugin == null
                || !session.isRunning()
                || !session.isParticipant(player.getUniqueId())
                || !session.isInArenaWorld(player.getWorld())) {
            return false;
        }
        if (session.isSuddenDeathActive()) {
            player.sendMessage(Component.text("Miracle of the Stars is disabled after sudden death.", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        TeamColor team = session.getTeam(player.getUniqueId());
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

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> runSafe(plugin, "miracleOfTheStars", () -> resolveMiracleOfTheStars(team)),
                MIRACLE_OF_THE_STARS_DELAY_TICKS);
        sessionTasks.add(task);
        return true;
    }

    boolean activateSteelShell(Player player, CustomItemDefinition custom, JavaPlugin plugin) {
        if (player == null
                || custom == null
                || plugin == null
                || !session.isRunning()
                || !session.isParticipant(player.getUniqueId())
                || !session.isInArenaWorld(player.getWorld())) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (activeSteelShells.containsKey(playerId)) {
            player.sendMessage(Component.text("Steel Shell is already active.", NamedTextColor.RED));
            return false;
        }
        List<Block> shellBlocks = resolveSteelShellBlocks(player);
        if (!canPlaceSteelShell(player, shellBlocks)) {
            return false;
        }

        Location centered = player.getLocation().clone();
        centered.setX(player.getLocation().getBlockX() + 0.5);
        centered.setZ(player.getLocation().getBlockZ() + 0.5);
        player.teleport(centered);
        player.setFallDistance(0.0f);

        List<BlockPoint> shellPoints = new ArrayList<>(shellBlocks.size());
        for (Block block : shellBlocks) {
            block.setType(Material.BEDROCK, false);
            BlockPoint point = toPoint(block);
            shellPoints.add(point);
            session.recordPlacedBlock(point);
        }

        int durationSeconds = custom.getLifetimeSeconds() > 0 ? custom.getLifetimeSeconds() : 10;
        PotionEffectType resistance = resolveResistanceEffectType();
        PotionEffect previousResistance = resistance != null ? player.getPotionEffect(resistance) : null;
        if (resistance != null) {
            player.addPotionEffect(new PotionEffect(resistance,
                    durationSeconds * 20 + 10,
                    STEEL_SHELL_RESISTANCE_AMPLIFIER,
                    true,
                    false,
                    true));
        }

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> clearSteelShell(playerId, true),
                durationSeconds * 20L);
        sessionTasks.add(task);
        activeSteelShells.put(playerId, new SteelShellState(shellPoints, task, previousResistance));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NETHERITE_BLOCK_PLACE, 1.0f, 0.9f);
        return true;
    }

    void handleElytraStrikeMovement(Player player) {
        if (player == null || !activeElytraStrikes.containsKey(player.getUniqueId())) {
            return;
        }
        if (!session.isRunning()
                || !session.isParticipant(player.getUniqueId())
                || !session.isInArenaWorld(player.getWorld())
                || player.getGameMode() == GameMode.SPECTATOR) {
            clearElytraStrike(player, false, false);
            return;
        }
        if (player.isOnGround()) {
            clearElytraStrike(player, true, true);
        }
    }

    boolean isActiveElytraStrikeItem(ItemStack item) {
        return GameSession.ELYTRA_STRIKE_ACTIVE_ITEM_ID.equalsIgnoreCase(CustomItemData.getId(item));
    }

    void clearAllElytraStrikes(boolean restoreChestplate) {
        for (UUID playerId : new ArrayList<>(activeElytraStrikes.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                activeElytraStrikes.remove(playerId);
                continue;
            }
            clearElytraStrike(player, restoreChestplate, false);
        }
    }

    void clearElytraStrike(Player player, boolean restoreChestplate, boolean grantLandingRegen) {
        if (player == null) {
            return;
        }
        ElytraStrikeState state = activeElytraStrikes.remove(player.getUniqueId());
        removeItemsByCustomId(player, GameSession.ELYTRA_STRIKE_ACTIVE_ITEM_ID);
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
                && session.isRunning()
                && session.isParticipant(player.getUniqueId())
                && session.isInArenaWorld(player.getWorld())
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
        meta.displayName(Component.text(AIRSTRIKE_ELYTRA_NAME, NamedTextColor.AQUA));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        CustomItemData.apply(meta, GameSession.ELYTRA_STRIKE_ACTIVE_ITEM_ID);
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

    boolean deployAbyssalRift(Player player, CustomItemDefinition custom, Block clickedBlock, JavaPlugin plugin) {
        if (player == null
                || custom == null
                || clickedBlock == null
                || plugin == null
                || !session.isRunning()
                || !session.isParticipant(player.getUniqueId())) {
            return false;
        }
        TeamColor team = session.getTeam(player.getUniqueId());
        if (team == null) {
            return false;
        }
        Block firstAir = clickedBlock.getRelative(BlockFace.UP);
        Block secondAir = firstAir.getRelative(BlockFace.UP);
        if (!firstAir.getType().isAir() || !secondAir.getType().isAir()) {
            player.sendMessage(Component.text("You cannot place an abyssal rift here.", NamedTextColor.RED));
            return false;
        }
        BlockPoint firstPoint = new BlockPoint(firstAir.getX(), firstAir.getY(), firstAir.getZ());
        BlockPoint secondPoint = new BlockPoint(secondAir.getX(), secondAir.getY(), secondAir.getZ());
        if (!session.isInsideMap(firstPoint) || !session.isInsideMap(secondPoint)) {
            player.sendMessage(Component.text("You cannot place an abyssal rift outside the map.", NamedTextColor.RED));
            return false;
        }
        if (session.isPlacementBlocked(firstPoint) || session.isPlacementBlocked(secondPoint)) {
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
            entity.addScoreboardTag(GameSession.ABYSSAL_RIFT_TAG);
        });
        ItemDisplay display = world.spawn(base, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setInvulnerable(false);
            entity.setGravity(false);
            entity.addScoreboardTag(GameSession.ABYSSAL_RIFT_DISPLAY_TAG);
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

    boolean deployTowerChest(Player player, Block clickedBlock, BlockFace clickedFace, JavaPlugin plugin) {
        if (player == null
                || clickedBlock == null
                || clickedFace != BlockFace.UP
                || !session.isRunning()
                || !session.isParticipant(player.getUniqueId())) {
            return false;
        }
        TeamColor team = session.getTeam(player.getUniqueId());
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
        List<TowerChestPlacement> placements = resolveTowerChestPlacements(origin, forward, right);
        if (!canPlaceTowerChest(player, placements)) {
            return false;
        }

        placeTemporaryTowerChest(origin, forward, plugin);
        ItemStack woolDrop = new ItemStack(team.wool());
        ItemStack ladderDrop = new ItemStack(Material.LADDER);
        for (TowerChestPlacement placement : placements) {
            if (placement.cell() == 'x') {
                placement.block().setType(team.wool(), false);
                session.recordPlacedBlock(toPoint(placement.block()), woolDrop);
                continue;
            }
            if (placement.cell() == 'L') {
                placement.block().setType(Material.LADDER, false);
                if (placement.block().getBlockData() instanceof Directional directional) {
                    directional.setFacing(forward);
                    placement.block().setBlockData(directional, false);
                }
                session.recordPlacedBlock(toPoint(placement.block()), ladderDrop);
            }
        }
        world.playSound(origin.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_WOOD_PLACE, 1.0f, 0.9f);
        return true;
    }

    boolean isAbyssalRiftEntity(Entity entity) {
        return resolveAbyssalRiftId(entity) != null;
    }

    boolean damageAbyssalRift(Entity entity, Player attacker, double damage) {
        UUID interactionId = resolveAbyssalRiftId(entity);
        if (interactionId == null) {
            return false;
        }
        AbyssalRiftState state = abyssalRifts.get(interactionId);
        if (state == null) {
            return false;
        }
        if (attacker != null) {
            if (!session.isParticipant(attacker.getUniqueId()) || !session.isInArenaWorld(attacker.getWorld())) {
                return true;
            }
            TeamColor attackerTeam = session.getTeam(attacker.getUniqueId());
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

    void clearAbyssalRifts() {
        for (UUID interactionId : new ArrayList<>(abyssalRifts.keySet())) {
            destroyAbyssalRift(interactionId, false);
        }
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
        customItemCooldownEnds.computeIfAbsent(playerId, ignored -> new HashMap<>()).put(normalized, expiresAt);
    }

    private UnstableTeleportResult rollUnstableTeleportationDestination(Player player) {
        TeamColor team = session.getTeam(player.getUniqueId());
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
                                Component.text("The device dropped you at the exact center of the map.",
                                        NamedTextColor.LIGHT_PURPLE));
            }
            case 2 -> {
                Location randomLocation = findRandomSafeArenaLocation();
                yield randomLocation == null
                        ? null
                        : new UnstableTeleportResult(
                                randomLocation,
                                Component.text("The device scattered you to a random location.",
                                        NamedTextColor.YELLOW));
            }
            default -> {
                yield findRandomBaseTeleportResult(team);
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
            Component message = Component.text("The device hurled you to the ", NamedTextColor.YELLOW)
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
        return isInsideMapTeleportLocation(location) ? location : null;
    }

    private boolean isInsideMapTeleportLocation(Location location) {
        if (location == null || location.getWorld() == null || !session.isInArenaWorld(location.getWorld())) {
            return false;
        }
        BlockPoint feet = new BlockPoint(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        BlockPoint head = new BlockPoint(location.getBlockX(), location.getBlockY() + 1, location.getBlockZ());
        return session.isInsideMap(feet) && session.isInsideMap(head);
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
        if (team == null || session.getState() != GameState.RUNNING) {
            return;
        }
        if (session.isSuddenDeathActive()) {
            Component cancelled = Component.text("Miracle of the Stars faded when sudden death began.",
                    NamedTextColor.RED);
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

        Component recallMessage = Component.text("Miracle of the Stars carried your team back to base.",
                NamedTextColor.GOLD);
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
                && session.isInArenaWorld(player.getWorld())
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

    private List<Block> resolveSteelShellBlocks(Player player) {
        List<Block> blocks = new ArrayList<>();
        if (player == null || player.getWorld() == null) {
            return blocks;
        }
        World world = player.getWorld();
        int centerX = player.getLocation().getBlockX();
        int centerY = player.getLocation().getBlockY();
        int centerZ = player.getLocation().getBlockZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                blocks.add(world.getBlockAt(centerX + dx, centerY + 2, centerZ + dz));
            }
        }
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) != 1 && Math.abs(dz) != 1) {
                        continue;
                    }
                    blocks.add(world.getBlockAt(centerX + dx, centerY + dy, centerZ + dz));
                }
            }
        }
        return blocks;
    }

    private boolean canPlaceSteelShell(Player player, List<Block> shellBlocks) {
        boolean outsideMap = false;
        for (Block block : shellBlocks) {
            if (block == null) {
                continue;
            }
            if (!session.isInsideMap(toPoint(block))) {
                outsideMap = true;
                continue;
            }
            if (!block.getType().isAir()) {
                player.sendMessage(Component.text("You cannot use Steel Shell here.", NamedTextColor.RED));
                return false;
            }
        }
        if (outsideMap) {
            player.sendMessage(Component.text("You cannot use Steel Shell outside the map.", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    private void clearAllSteelShells(boolean restorePreviousResistance) {
        for (UUID playerId : new ArrayList<>(activeSteelShells.keySet())) {
            clearSteelShell(playerId, restorePreviousResistance);
        }
    }

    private void clearSteelShell(UUID playerId, boolean restorePreviousResistance) {
        if (playerId == null) {
            return;
        }
        SteelShellState state = activeSteelShells.remove(playerId);
        if (state == null) {
            return;
        }
        if (state.task() != null) {
            state.task().cancel();
        }
        World world = resolveArenaWorld();
        for (BlockPoint point : state.blocks()) {
            if (point == null) {
                continue;
            }
            if (world != null) {
                Block block = world.getBlockAt(point.x(), point.y(), point.z());
                if (block.getType() == Material.BEDROCK) {
                    block.setType(Material.AIR, false);
                }
            }
            session.removePlacedBlock(point);
        }
        Player player = Bukkit.getPlayer(playerId);
        PotionEffectType resistance = resolveResistanceEffectType();
        if (player != null && resistance != null) {
            player.removePotionEffect(resistance);
            if (restorePreviousResistance && state.previousResistance() != null) {
                player.addPotionEffect(state.previousResistance());
            }
        }
    }

    private PotionEffectType resolveResistanceEffectType() {
        PotionEffectType resistance = PotionEffectType.getByName("RESISTANCE");
        if (resistance != null) {
            return resistance;
        }
        return PotionEffectType.getByName("DAMAGE_RESISTANCE");
    }

    private World resolveArenaWorld() {
        String worldName = arena.getWorldName();
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        return Bukkit.getWorld(worldName);
    }

    private List<TowerChestPlacement> resolveTowerChestPlacements(Block origin, BlockFace forward, BlockFace right) {
        List<TowerChestPlacement> placements = new ArrayList<>();
        for (int layer = 0; layer < TOWER_CHEST_LAYERS.length; layer++) {
            String[] rows = TOWER_CHEST_LAYERS[layer];
            for (int row = 0; row < rows.length; row++) {
                String pattern = rows[row];
                for (int column = 0; column < pattern.length(); column++) {
                    char cell = pattern.charAt(column);
                    if (cell == '0') {
                        continue;
                    }
                    placements.add(new TowerChestPlacement(
                            resolveTowerChestBlock(origin, forward, right, layer, row, column),
                            cell));
                }
            }
        }
        return placements;
    }

    private boolean canPlaceTowerChest(Player player, List<TowerChestPlacement> placements) {
        boolean outsideMap = false;
        for (TowerChestPlacement placement : placements) {
            Block block = placement.block();
            if (!session.isInsideMap(toPoint(block))) {
                outsideMap = true;
                continue;
            }
            if (!block.getType().isAir()) {
                player.sendMessage(Component.text("You cannot place a tower chest here.", NamedTextColor.RED));
                return false;
            }
        }
        if (outsideMap) {
            player.sendMessage(Component.text("You cannot place a tower chest outside the map.", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    private void placeTemporaryTowerChest(Block origin, BlockFace forward, JavaPlugin plugin) {
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

    private UUID resolveAbyssalRiftId(Entity entity) {
        return entity == null ? null : abyssalRiftEntityLinks.get(entity.getUniqueId());
    }

    private void tickAbyssalRift(UUID interactionId) {
        AbyssalRiftState state = abyssalRifts.get(interactionId);
        if (state == null) {
            return;
        }
        Interaction interaction = getAbyssalRiftInteraction(interactionId);
        ItemDisplay display = getAbyssalRiftDisplay(state.displayId());
        if (!session.isRunning()
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
                    || !session.isInArenaWorld(candidate.getWorld())
                    || candidate.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (candidate.getLocation().distanceSquared(center) > radiusSquared) {
                continue;
            }
            TeamColor candidateTeam = session.getTeam(playerId);
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
        meta.displayName(Component.text(ABYSSAL_RIFT_NAME, NamedTextColor.DARK_AQUA));
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
            stand.addScoreboardTag(GameSession.ABYSSAL_RIFT_NAME_TAG);
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
            titleStand.customName(Component.text(ABYSSAL_RIFT_NAME, NamedTextColor.DARK_AQUA));
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

    private void broadcast(Component message) {
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private void runSafe(JavaPlugin plugin, String context, Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to execute GameSessionCustomItemRuntime." + context, ex);
        }
    }

    private String normalizeItemId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private BlockPoint toPoint(Block block) {
        return new BlockPoint(block.getX(), block.getY(), block.getZ());
    }

    private record UnstableTeleportResult(Location destination, Component message) {
    }

    private record TowerChestPlacement(Block block, char cell) {
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

    private record SteelShellState(List<BlockPoint> blocks,
                                   BukkitTask task,
                                   PotionEffect previousResistance) {
    }

    private record LockpickAccessKey(UUID playerId, TeamColor baseTeam) {
    }

    private record LockpickCountdownState(UUID displayId, BukkitTask task) {
    }

    private record EnderChestLockpickAccess(UUID targetPlayerId, long expiresAt) {
    }
}
