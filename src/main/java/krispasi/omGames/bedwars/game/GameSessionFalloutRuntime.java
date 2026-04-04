package krispasi.omGames.bedwars.game;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import krispasi.omGames.bedwars.event.BedwarsMatchEventType;
import krispasi.omGames.bedwars.model.TeamColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

final class GameSessionFalloutRuntime {
    private static final long FALLOUT_INTERVAL_TICKS = 20L;

    private final GameSession session;
    private final Map<UUID, TeamColor> assignments;
    private final java.util.List<BukkitTask> sessionTasks;
    private final EnumMap<FalloutAttribute, Double> currentValues = new EnumMap<>(FalloutAttribute.class);
    private BukkitTask task;

    GameSessionFalloutRuntime(GameSession session,
                              Map<UUID, TeamColor> assignments,
                              java.util.List<BukkitTask> sessionTasks) {
        this.session = session;
        this.assignments = assignments;
        this.sessionTasks = sessionTasks;
        resetCurrentValues();
    }

    void start() {
        reset();
        if (session.plugin == null) {
            return;
        }
        task = new BukkitRunnable() {
            @Override
            public void run() {
                session.safeRun("falloutTick", () -> {
                    if (!session.isRunning() || session.getActiveMatchEvent() != BedwarsMatchEventType.FALLOUT) {
                        reset();
                        cancel();
                        return;
                    }
                    mutateRandomAttribute();
                    applyToParticipants();
                });
            }
        }.runTaskTimer(session.plugin, FALLOUT_INTERVAL_TICKS, FALLOUT_INTERVAL_TICKS);
        sessionTasks.add(task);
    }

    void reset() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        resetCurrentValues();
    }

    void applyTo(Player player) {
        if (player == null) {
            return;
        }
        for (Map.Entry<FalloutAttribute, Double> entry : currentValues.entrySet()) {
            entry.getKey().apply(session, player, entry.getValue());
        }
    }

    void clearPlayer(Player player) {
        if (player == null) {
            return;
        }
        for (FalloutAttribute attribute : FalloutAttribute.values()) {
            attribute.reset(session, player);
        }
    }

    private void resetCurrentValues() {
        currentValues.clear();
        for (FalloutAttribute attribute : FalloutAttribute.values()) {
            currentValues.put(attribute, resolveDefaultValue(attribute));
        }
    }

    private void mutateRandomAttribute() {
        FalloutAttribute[] values = FalloutAttribute.values();
        if (values.length == 0) {
            return;
        }
        FalloutAttribute attribute = values[ThreadLocalRandom.current().nextInt(values.length)];
        double current = currentValues.getOrDefault(attribute, resolveDefaultValue(attribute));
        double next = attribute.randomStep(current);
        currentValues.put(attribute, next);
    }

    private void applyToParticipants() {
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null
                    || !player.isOnline()
                    || !session.isInArenaWorld(player.getWorld())
                    || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            applyTo(player);
        }
    }

    private double resolveDefaultValue(FalloutAttribute attribute) {
        if (attribute == null) {
            return 0.0;
        }
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline() || !session.isInArenaWorld(player.getWorld())) {
                continue;
            }
            Double playerDefault = attribute.resolveDefaultValue(session, player);
            if (playerDefault != null) {
                return playerDefault;
            }
        }
        return attribute.fallbackDefault();
    }

    private enum FalloutAttribute {
        ATTACK_KNOCKBACK("attack_knockback", 0.1, 0.0, 0.0, 5.0),
        ATTACK_SPEED("attack_speed", 0.01, 4.0, 0.0, 1024.0),
        BLOCK_BREAK_SPEED("block_break_speed", 0.1, 1.0, 0.0, 1024.0),
        BLOCK_INTERACTION_RANGE("block_interaction_range", 0.1, 4.5, 0.0, 64.0),
        BURNING_TIME("burning_time", 0.1, 1.0, 0.0, 1024.0),
        EXPLOSION_KNOCKBACK_RESISTANCE("explosion_knockback_resistance", 0.1, 0.0, 0.0, 1.0),
        FALL_DAMAGE_MULTIPLIER("fall_damage_multiplier", 0.2, 1.0, 0.0, 100.0),
        GRAVITY("gravity", 0.005, 0.08, 0.01, 0.1),
        KNOCKBACK_RESISTANCE("knockback_resistance", 0.1, 0.0, 0.0, 1.0),
        MOVEMENT_SPEED("movement_speed", 0.03, 0.1, 0.0, 1024.0),
        SAFE_FALL_DISTANCE("safe_fall_distance", 0.03, 3.0, 1.5, 1024.0),
        SCALE("scale", 0.01, 1.0, 0.0625, 16.0),
        SNEAKING_SPEED("sneaking_speed", 0.1, 0.3, 0.0, 1.0),
        STEP_HEIGHT("step_height", 0.1, 0.6, 0.0, 10.0);

        private final String key;
        private final double delta;
        private final double fallbackDefault;
        private final double minValue;
        private final double maxValue;

        FalloutAttribute(String key,
                         double delta,
                         double fallbackDefault,
                         double minValue,
                         double maxValue) {
            this.key = key;
            this.delta = delta;
            this.fallbackDefault = fallbackDefault;
            this.minValue = minValue;
            this.maxValue = maxValue;
        }

        private double fallbackDefault() {
            return fallbackDefault;
        }

        private void apply(GameSession session, Player player, double value) {
            if (session == null || player == null) {
                return;
            }
            AttributeInstance instance = session.resolveAttributeInstance(player, key);
            if (instance == null) {
                return;
            }
            instance.setBaseValue(clamp(value));
        }

        private void reset(GameSession session, Player player) {
            Double defaultValue = resolveDefaultValue(session, player);
            apply(session, player, defaultValue != null ? defaultValue : fallbackDefault);
        }

        private Double resolveDefaultValue(GameSession session, Player player) {
            if (session == null || player == null) {
                return null;
            }
            AttributeInstance instance = session.resolveAttributeInstance(player, key);
            if (instance == null) {
                return null;
            }
            return instance.getDefaultValue();
        }

        private double randomStep(double currentValue) {
            double upward = clamp(currentValue + delta);
            double downward = clamp(currentValue - delta);
            boolean increaseFirst = ThreadLocalRandom.current().nextBoolean();
            if (increaseFirst) {
                if (isDifferent(upward, currentValue)) {
                    return upward;
                }
                return downward;
            }
            if (isDifferent(downward, currentValue)) {
                return downward;
            }
            return upward;
        }

        private double clamp(double value) {
            return Math.max(minValue, Math.min(maxValue, value));
        }

        private boolean isDifferent(double left, double right) {
            return Math.abs(left - right) > 0.0000001;
        }
    }
}
