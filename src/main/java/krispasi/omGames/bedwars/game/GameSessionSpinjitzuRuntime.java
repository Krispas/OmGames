package krispasi.omGames.bedwars.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import krispasi.omGames.bedwars.item.CustomItemDefinition;
import krispasi.omGames.bedwars.model.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

final class GameSessionSpinjitzuRuntime {
    private static final double SPINJITZU_STEP_HEIGHT = 2.0;
    private static final double SPINJITZU_HORIZONTAL_SPEED_MULTIPLIER = 20.0;
    private static final double SPINJITZU_MIN_HORIZONTAL_SPEED = 0.3;
    private static final double SPINJITZU_MAX_HORIZONTAL_SPEED = 1.2;
    private static final long SPINJITZU_DAMAGE_COOLDOWN_MILLIS = 1000L;
    private static final int SPINJITZU_SOUND_INTERVAL_TICKS = 8;

    private final GameSession session;
    private final Map<UUID, TeamColor> assignments;
    private final List<BukkitTask> sessionTasks;
    private final Map<UUID, SpinjitzuState> activeStates = new HashMap<>();

    GameSessionSpinjitzuRuntime(GameSession session,
                                Map<UUID, TeamColor> assignments,
                                List<BukkitTask> sessionTasks) {
        this.session = session;
        this.assignments = assignments;
        this.sessionTasks = sessionTasks;
    }

    boolean activate(Player player, CustomItemDefinition custom) {
        if (player == null
                || custom == null
                || !session.isRunning()
                || !session.isParticipant(player.getUniqueId())
                || !session.isInArenaWorld(player.getWorld())) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (activeStates.containsKey(playerId)) {
            player.sendMessage(Component.text("Spinjitzu is already active.", NamedTextColor.RED));
            return false;
        }
        JavaPlugin plugin = session.getBedwarsManager().getPlugin();
        if (plugin == null) {
            return false;
        }

        AttributeInstance speedAttribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        AttributeInstance stepAttribute = player.getAttribute(Attribute.STEP_HEIGHT);
        Double previousSpeed = speedAttribute != null ? speedAttribute.getBaseValue() : null;
        Double previousStepHeight = stepAttribute != null ? stepAttribute.getBaseValue() : null;
        double speedBonus = Math.max(0.0, custom.getSpeed());
        if (speedAttribute != null && speedBonus > 0.0) {
            speedAttribute.setBaseValue(previousSpeed + speedBonus);
        }
        if (stepAttribute != null) {
            stepAttribute.setBaseValue(Math.max(previousStepHeight, SPINJITZU_STEP_HEIGHT));
        }

        player.setFallDistance(0.0f);
        player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.9f, 0.7f);

        SpinjitzuState state = new SpinjitzuState(
                previousSpeed,
                previousStepHeight,
                resolveHorizontalSpeed(custom));
        activeStates.put(playerId, state);

        long durationTicks = Math.max(1L, custom.getLifetimeSeconds() > 0 ? custom.getLifetimeSeconds() * 20L : 200L);
        BukkitTask task = new BukkitRunnable() {
            private long elapsedTicks;

            @Override
            public void run() {
                SpinjitzuState current = activeStates.get(playerId);
                if (current != state) {
                    cancel();
                    return;
                }
                Player activePlayer = Bukkit.getPlayer(playerId);
                if (!isValidActivePlayer(activePlayer)) {
                    clear(playerId);
                    cancel();
                    return;
                }
                applyHorizontalVelocity(activePlayer, current);
                activePlayer.setFallDistance(0.0f);
                activePlayer.setFireTicks(0);
                spawnTornadoParticles(activePlayer, elapsedTicks);
                if (elapsedTicks % SPINJITZU_SOUND_INTERVAL_TICKS == 0L) {
                    activePlayer.getWorld().playSound(activePlayer.getLocation(),
                            Sound.ITEM_FIRECHARGE_USE,
                            0.35f,
                            1.25f);
                }
                damageNearbyEnemies(activePlayer, current, custom);
                elapsedTicks++;
                if (elapsedTicks >= durationTicks) {
                    clear(playerId);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        state.setTask(task);
        sessionTasks.add(task);
        return true;
    }

    boolean isDamageImmune(Player player, EntityDamageEvent.DamageCause cause) {
        if (player == null || cause == null) {
            return false;
        }
        return activeStates.containsKey(player.getUniqueId());
    }

    void clear(UUID playerId) {
        if (playerId == null) {
            return;
        }
        SpinjitzuState state = activeStates.remove(playerId);
        if (state == null) {
            return;
        }
        if (state.task() != null) {
            state.task().cancel();
            state.setTask(null);
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        if (state.previousSpeed() != null) {
            AttributeInstance speedAttribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speedAttribute != null) {
                speedAttribute.setBaseValue(state.previousSpeed());
            }
        }
        if (state.previousStepHeight() != null) {
            AttributeInstance stepAttribute = player.getAttribute(Attribute.STEP_HEIGHT);
            if (stepAttribute != null) {
                stepAttribute.setBaseValue(state.previousStepHeight());
            }
        }
        player.setFallDistance(0.0f);
    }

    void reset() {
        for (UUID playerId : new ArrayList<>(activeStates.keySet())) {
            clear(playerId);
        }
    }

    private boolean isValidActivePlayer(Player player) {
        return player != null
                && player.isOnline()
                && session.isRunning()
                && session.isParticipant(player.getUniqueId())
                && session.isInArenaWorld(player.getWorld())
                && player.getGameMode() != GameMode.SPECTATOR
                && !session.isPendingRespawn(player.getUniqueId())
                && !session.isEliminated(player.getUniqueId());
    }

    private double resolveHorizontalSpeed(CustomItemDefinition custom) {
        if (custom == null) {
            return SPINJITZU_MIN_HORIZONTAL_SPEED;
        }
        double configured = Math.max(0.0, custom.getSpeed()) * SPINJITZU_HORIZONTAL_SPEED_MULTIPLIER;
        return Math.max(SPINJITZU_MIN_HORIZONTAL_SPEED, Math.min(SPINJITZU_MAX_HORIZONTAL_SPEED, configured));
    }

    private void applyHorizontalVelocity(Player player, SpinjitzuState state) {
        if (player == null || state == null) {
            return;
        }
        Vector look = player.getLocation().getDirection();
        look.setY(0.0);
        if (look.lengthSquared() < 0.000001) {
            return;
        }
        Vector horizontal = look.normalize().multiply(state.horizontalSpeed());
        Vector current = player.getVelocity();
        player.setVelocity(new Vector(horizontal.getX(), current.getY(), horizontal.getZ()));
    }

    private void damageNearbyEnemies(Player player, SpinjitzuState state, CustomItemDefinition custom) {
        if (player == null || state == null || custom == null) {
            return;
        }
        TeamColor team = assignments.get(player.getUniqueId());
        if (team == null) {
            return;
        }
        double radius = Math.max(0.0, custom.getRange());
        double damage = Math.max(0.0, custom.getDamage());
        if (radius <= 0.0 || damage <= 0.0) {
            return;
        }
        Location center = player.getLocation().add(0.0, 1.0, 0.0);
        long now = System.currentTimeMillis();
        for (Player target : player.getWorld().getNearbyPlayers(center, radius, 1.5, radius)) {
            if (target == null
                    || target.getUniqueId().equals(player.getUniqueId())
                    || !session.isParticipant(target.getUniqueId())
                    || target.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            TeamColor targetTeam = assignments.get(target.getUniqueId());
            if (targetTeam == null || targetTeam == team || !state.canDamage(target.getUniqueId(), now)) {
                continue;
            }
            target.damage(damage, player);
            state.markDamaged(target.getUniqueId(), now + SPINJITZU_DAMAGE_COOLDOWN_MILLIS);
            target.getWorld().spawnParticle(Particle.FLAME,
                    target.getLocation().add(0.0, 1.0, 0.0),
                    8,
                    0.2,
                    0.4,
                    0.2,
                    0.01);
        }
    }

    private void spawnTornadoParticles(Player player, long elapsedTicks) {
        if (player == null) {
            return;
        }
        World world = player.getWorld();
        Location base = player.getLocation().clone();
        double baseAngle = elapsedTicks * 0.45;
        for (double y = 0.15; y <= 2.75; y += 0.35) {
            double radius = 0.55 + y * 0.08;
            for (int i = 0; i < 3; i++) {
                double angle = baseAngle + y * 1.3 + i * (Math.PI * 2.0 / 3.0);
                Location point = base.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
                world.spawnParticle(Particle.FLAME, point, 1, 0.03, 0.03, 0.03, 0.0);
                if (((int) elapsedTicks + i) % 2 == 0) {
                    world.spawnParticle(Particle.SMOKE, point, 1, 0.02, 0.04, 0.02, 0.0);
                }
            }
        }
    }

    private static final class SpinjitzuState {
        private final Double previousSpeed;
        private final Double previousStepHeight;
        private final double horizontalSpeed;
        private final Map<UUID, Long> nextDamageTimes = new HashMap<>();
        private BukkitTask task;

        private SpinjitzuState(Double previousSpeed, Double previousStepHeight, double horizontalSpeed) {
            this.previousSpeed = previousSpeed;
            this.previousStepHeight = previousStepHeight;
            this.horizontalSpeed = horizontalSpeed;
        }

        private Double previousSpeed() {
            return previousSpeed;
        }

        private Double previousStepHeight() {
            return previousStepHeight;
        }

        private double horizontalSpeed() {
            return horizontalSpeed;
        }

        private BukkitTask task() {
            return task;
        }

        private void setTask(BukkitTask task) {
            this.task = task;
        }

        private boolean canDamage(UUID playerId, long now) {
            Long nextDamageTime = nextDamageTimes.get(playerId);
            return nextDamageTime == null || now >= nextDamageTime;
        }

        private void markDamaged(UUID playerId, long nextDamageTime) {
            nextDamageTimes.put(playerId, nextDamageTime);
        }
    }
}
