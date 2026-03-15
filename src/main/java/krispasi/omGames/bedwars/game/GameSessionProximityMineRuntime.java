package krispasi.omGames.bedwars.game;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import krispasi.omGames.bedwars.model.BlockPoint;
import krispasi.omGames.bedwars.model.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

final class GameSessionProximityMineRuntime {
    private static final long PROXIMITY_MINE_ARM_DELAY_TICKS = 5L * 20L;
    private static final long PROXIMITY_MINE_DISPLAY_UPDATE_TICKS = 5L;
    private static final int PROXIMITY_MINE_BAR_SEGMENTS = 10;
    private static final double PROXIMITY_MINE_DISPLAY_Y_OFFSET = 1.2;
    private static final String PROXIMITY_MINE_DISPLAY_TAG = "bw_proximity_mine_display";

    private final GameSession session;
    private final Map<UUID, TeamColor> assignments;
    private final List<BukkitTask> sessionTasks;
    private final Map<BlockPoint, ProximityMineState> placedProximityMines = new HashMap<>();

    GameSessionProximityMineRuntime(GameSession session,
                                    Map<UUID, TeamColor> assignments,
                                    List<BukkitTask> sessionTasks) {
        this.session = session;
        this.assignments = assignments;
        this.sessionTasks = sessionTasks;
    }

    void reset() {
        for (ProximityMineState state : placedProximityMines.values()) {
            clearMineVisuals(state);
        }
        placedProximityMines.clear();
    }

    boolean place(Player player, Block block, ItemStack item) {
        if (player == null
                || block == null
                || item == null
                || !session.isRunning()
                || !session.isParticipant(player.getUniqueId())
                || !session.isInArenaWorld(block.getWorld())) {
            return false;
        }
        TeamColor team = assignments.get(player.getUniqueId());
        if (team == null || block.getType() != Material.STONE_PRESSURE_PLATE) {
            return false;
        }
        BlockPoint point = new BlockPoint(block.getX(), block.getY(), block.getZ());
        remove(point);
        session.recordPlacedBlock(point, item);

        ProximityMineState state = new ProximityMineState(player.getUniqueId(), team);
        ArmorStand display = spawnDisplay(block.getLocation().add(0.5, PROXIMITY_MINE_DISPLAY_Y_OFFSET, 0.5));
        if (display != null) {
            state.setDisplayId(display.getUniqueId());
            display.customName(buildPrimingDisplay(0L));
        }
        placedProximityMines.put(point, state);
        startPrimingTask(point, block.getWorld(), state);
        return true;
    }

    void handleMovement(Player player) {
        if (player == null
                || !session.isRunning()
                || !session.isParticipant(player.getUniqueId())
                || !session.isInArenaWorld(player.getWorld())
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        TeamColor playerTeam = assignments.get(player.getUniqueId());
        if (playerTeam == null) {
            return;
        }
        BlockPoint triggerPoint = resolveTriggeredMine(player, playerTeam);
        if (triggerPoint == null) {
            return;
        }
        detonate(triggerPoint);
    }

    void remove(BlockPoint point) {
        if (point == null) {
            return;
        }
        ProximityMineState state = placedProximityMines.remove(point);
        clearMineVisuals(state);
    }

    private void startPrimingTask(BlockPoint point, World world, ProximityMineState state) {
        JavaPlugin plugin = session.getBedwarsManager().getPlugin();
        if (plugin == null) {
            state.setArmed(true);
            clearMineDisplay(state.displayId());
            return;
        }
        BukkitTask task = new BukkitRunnable() {
            private long elapsedTicks;

            @Override
            public void run() {
                ProximityMineState current = placedProximityMines.get(point);
                if (current != state) {
                    clearMineDisplay(state.displayId());
                    cancel();
                    return;
                }
                if (!session.isRunning() || world == null) {
                    current.setPrimingTask(null);
                    clearMineDisplay(current.displayId());
                    cancel();
                    return;
                }
                Block currentBlock = world.getBlockAt(point.x(), point.y(), point.z());
                if (currentBlock.getType() != Material.STONE_PRESSURE_PLATE) {
                    current.setPrimingTask(null);
                    clearMineDisplay(current.displayId());
                    cancel();
                    return;
                }
                ArmorStand display = getMineDisplay(current.displayId());
                if (display != null) {
                    display.customName(buildPrimingDisplay(elapsedTicks));
                }
                if (elapsedTicks >= PROXIMITY_MINE_ARM_DELAY_TICKS) {
                    current.setArmed(true);
                    current.setPrimingTask(null);
                    clearMineDisplay(current.displayId());
                    cancel();
                    return;
                }
                elapsedTicks = Math.min(PROXIMITY_MINE_ARM_DELAY_TICKS,
                        elapsedTicks + PROXIMITY_MINE_DISPLAY_UPDATE_TICKS);
            }
        }.runTaskTimer(plugin, 0L, PROXIMITY_MINE_DISPLAY_UPDATE_TICKS);
        state.setPrimingTask(task);
        sessionTasks.add(task);
    }

    private BlockPoint resolveTriggeredMine(Player player, TeamColor playerTeam) {
        Location feet = player.getLocation();
        int centerX = feet.getBlockX();
        int supportY = feet.clone().subtract(0.0, 0.2, 0.0).getBlockY();
        int centerZ = feet.getBlockZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPoint point = new BlockPoint(centerX + dx, supportY, centerZ + dz);
                ProximityMineState state = placedProximityMines.get(point);
                if (state == null || state.team() == playerTeam || !state.isArmed()) {
                    continue;
                }
                return point;
            }
        }
        return null;
    }

    private void detonate(BlockPoint point) {
        if (point == null) {
            return;
        }
        ProximityMineState state = placedProximityMines.remove(point);
        clearMineVisuals(state);

        World world = session.getArena().getWorld();
        if (world == null) {
            session.removePlacedBlock(point);
            return;
        }
        Block block = world.getBlockAt(point.x(), point.y(), point.z());
        if (block.getType() == Material.STONE_PRESSURE_PLATE) {
            block.setType(Material.AIR, false);
        }
        session.removePlacedBlock(point);
        Location spawn = block.getLocation().add(0.5, 0.0, 0.5);
        world.spawn(spawn, TNTPrimed.class, tnt -> {
            tnt.setFuseTicks(0);
            tnt.setIsIncendiary(false);
            if (state != null && state.team() != null) {
                tnt.addScoreboardTag("bw_proximity_mine_team_" + state.team().key());
            }
            if (state == null || state.ownerId() == null) {
                return;
            }
            Player owner = Bukkit.getPlayer(state.ownerId());
            if (owner != null && owner.isOnline() && session.isParticipant(owner.getUniqueId())
                    && session.isInArenaWorld(owner.getWorld())) {
                tnt.setSource(owner);
            }
        });
        world.playSound(spawn, Sound.ENTITY_TNT_PRIMED, 1.0f, 1.2f);
    }

    private void clearMineVisuals(ProximityMineState state) {
        if (state == null) {
            return;
        }
        BukkitTask primingTask = state.primingTask();
        if (primingTask != null) {
            primingTask.cancel();
            state.setPrimingTask(null);
        }
        clearMineDisplay(state.displayId());
    }

    private ArmorStand spawnDisplay(Location location) {
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
            stand.addScoreboardTag(PROXIMITY_MINE_DISPLAY_TAG);
        });
    }

    private ArmorStand getMineDisplay(UUID displayId) {
        if (displayId == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(displayId);
        return entity instanceof ArmorStand stand ? stand : null;
    }

    private void clearMineDisplay(UUID displayId) {
        ArmorStand display = getMineDisplay(displayId);
        if (display != null) {
            display.remove();
        }
    }

    private Component buildPrimingDisplay(long elapsedTicks) {
        long clampedTicks = Math.max(0L, Math.min(PROXIMITY_MINE_ARM_DELAY_TICKS, elapsedTicks));
        int filled = (int) Math.floor(clampedTicks * PROXIMITY_MINE_BAR_SEGMENTS
                / (double) PROXIMITY_MINE_ARM_DELAY_TICKS);
        filled = Math.max(0, Math.min(PROXIMITY_MINE_BAR_SEGMENTS, filled));
        int empty = PROXIMITY_MINE_BAR_SEGMENTS - filled;
        Component display = Component.text("Priming ", NamedTextColor.YELLOW)
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text("#".repeat(filled), NamedTextColor.GREEN))
                .append(Component.text("-".repeat(empty), NamedTextColor.DARK_GRAY))
                .append(Component.text("]", NamedTextColor.DARK_GRAY));
        long remainingTicks = PROXIMITY_MINE_ARM_DELAY_TICKS - clampedTicks;
        if (remainingTicks > 0L) {
            long remainingSeconds = Math.max(1L, (long) Math.ceil(remainingTicks / 20.0));
            display = display.append(Component.text(" " + remainingSeconds + "s", NamedTextColor.GRAY));
        }
        return display;
    }

    private static final class ProximityMineState {
        private final UUID ownerId;
        private final TeamColor team;
        private UUID displayId;
        private BukkitTask primingTask;
        private boolean armed;

        private ProximityMineState(UUID ownerId, TeamColor team) {
            this.ownerId = ownerId;
            this.team = team;
        }

        private UUID ownerId() {
            return ownerId;
        }

        private TeamColor team() {
            return team;
        }

        private UUID displayId() {
            return displayId;
        }

        private void setDisplayId(UUID displayId) {
            this.displayId = displayId;
        }

        private BukkitTask primingTask() {
            return primingTask;
        }

        private void setPrimingTask(BukkitTask primingTask) {
            this.primingTask = primingTask;
        }

        private boolean isArmed() {
            return armed;
        }

        private void setArmed(boolean armed) {
            this.armed = armed;
        }
    }
}
