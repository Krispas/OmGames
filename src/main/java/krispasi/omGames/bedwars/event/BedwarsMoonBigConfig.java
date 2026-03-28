package krispasi.omGames.bedwars.event;

/**
 * Moon Big match event configuration for asteroid behavior.
 */
public class BedwarsMoonBigConfig {
    private static final double DEFAULT_FALL_SPEED_BLOCKS_PER_SECOND = 3.0;
    private static final int DEFAULT_START_INTERVAL_MIN_SECONDS = 60;
    private static final int DEFAULT_START_INTERVAL_MAX_SECONDS = 90;
    private static final int DEFAULT_END_INTERVAL_MIN_SECONDS = 1;
    private static final int DEFAULT_END_INTERVAL_MAX_SECONDS = 3;
    private static final int DEFAULT_RADIUS_MIN = 2;
    private static final int DEFAULT_RADIUS_MAX = 5;
    private static final double DEFAULT_MISSING_BLOCK_CHANCE = 0.30;
    private static final double DEFAULT_CRATE_CHANCE = 0.05;
    private static final int DEFAULT_SPAWN_HEIGHT_ABOVE_GROUND = 60;
    private static final double DEFAULT_EXPLOSION_POWER_MULTIPLIER = 1.0;

    private final double fallSpeedBlocksPerSecond;
    private final int startIntervalMinSeconds;
    private final int startIntervalMaxSeconds;
    private final int endIntervalMinSeconds;
    private final int endIntervalMaxSeconds;
    private final int radiusMin;
    private final int radiusMax;
    private final double missingBlockChance;
    private final double crateChance;
    private final int spawnHeightAboveGround;
    private final double explosionPowerMultiplier;

    public BedwarsMoonBigConfig(double fallSpeedBlocksPerSecond,
                                int startIntervalMinSeconds,
                                int startIntervalMaxSeconds,
                                int endIntervalMinSeconds,
                                int endIntervalMaxSeconds,
                                int radiusMin,
                                int radiusMax,
                                double missingBlockChance,
                                double crateChance,
                                int spawnHeightAboveGround,
                                double explosionPowerMultiplier) {
        this.fallSpeedBlocksPerSecond = Math.max(0.1, fallSpeedBlocksPerSecond);
        this.startIntervalMinSeconds = Math.max(0, startIntervalMinSeconds);
        this.startIntervalMaxSeconds = Math.max(this.startIntervalMinSeconds, startIntervalMaxSeconds);
        this.endIntervalMinSeconds = Math.max(0, endIntervalMinSeconds);
        this.endIntervalMaxSeconds = Math.max(this.endIntervalMinSeconds, endIntervalMaxSeconds);
        this.radiusMin = Math.max(1, radiusMin);
        this.radiusMax = Math.max(this.radiusMin, radiusMax);
        this.missingBlockChance = clampChance(missingBlockChance);
        this.crateChance = clampChance(crateChance);
        this.spawnHeightAboveGround = Math.max(5, spawnHeightAboveGround);
        this.explosionPowerMultiplier = Math.max(0.0, explosionPowerMultiplier);
    }

    public static BedwarsMoonBigConfig defaults() {
        return new BedwarsMoonBigConfig(
                DEFAULT_FALL_SPEED_BLOCKS_PER_SECOND,
                DEFAULT_START_INTERVAL_MIN_SECONDS,
                DEFAULT_START_INTERVAL_MAX_SECONDS,
                DEFAULT_END_INTERVAL_MIN_SECONDS,
                DEFAULT_END_INTERVAL_MAX_SECONDS,
                DEFAULT_RADIUS_MIN,
                DEFAULT_RADIUS_MAX,
                DEFAULT_MISSING_BLOCK_CHANCE,
                DEFAULT_CRATE_CHANCE,
                DEFAULT_SPAWN_HEIGHT_ABOVE_GROUND,
                DEFAULT_EXPLOSION_POWER_MULTIPLIER
        );
    }

    public double fallSpeedBlocksPerSecond() {
        return fallSpeedBlocksPerSecond;
    }

    public int startIntervalMinSeconds() {
        return startIntervalMinSeconds;
    }

    public int startIntervalMaxSeconds() {
        return startIntervalMaxSeconds;
    }

    public int endIntervalMinSeconds() {
        return endIntervalMinSeconds;
    }

    public int endIntervalMaxSeconds() {
        return endIntervalMaxSeconds;
    }

    public int radiusMin() {
        return radiusMin;
    }

    public int radiusMax() {
        return radiusMax;
    }

    public double missingBlockChance() {
        return missingBlockChance;
    }

    public double crateChance() {
        return crateChance;
    }

    public int spawnHeightAboveGround() {
        return spawnHeightAboveGround;
    }

    public double explosionPowerMultiplier() {
        return explosionPowerMultiplier;
    }

    private static double clampChance(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
