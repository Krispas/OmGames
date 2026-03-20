package krispasi.omGames.bedwars.karma;

/**
 * Global karma-event scheduling configuration loaded from {@code bedwars.yml}.
 */
public class BedwarsKarmaEventConfig {
    private static final double DEFAULT_MIN_CHECK_SECONDS = 10.0;
    private static final double DEFAULT_MAX_CHECK_SECONDS = 200.0;
    private static final double DEFAULT_BASE_ROLL_CHANCE_PERCENT = 10.0;
    private static final double DEFAULT_PER_KARMA_CHANCE_PERCENT = 10.0;

    private final double minCheckSeconds;
    private final double maxCheckSeconds;
    private final double baseRollChancePercent;
    private final double perKarmaChancePercent;

    public BedwarsKarmaEventConfig(double minCheckSeconds,
                                   double maxCheckSeconds,
                                   double baseRollChancePercent,
                                   double perKarmaChancePercent) {
        double normalizedMin = sanitizeNonNegative(minCheckSeconds, DEFAULT_MIN_CHECK_SECONDS);
        double normalizedMax = sanitizeNonNegative(maxCheckSeconds, DEFAULT_MAX_CHECK_SECONDS);
        if (normalizedMax < normalizedMin) {
            normalizedMax = normalizedMin;
        }
        this.minCheckSeconds = normalizedMin;
        this.maxCheckSeconds = normalizedMax;
        this.baseRollChancePercent = sanitizePercent(baseRollChancePercent, DEFAULT_BASE_ROLL_CHANCE_PERCENT);
        this.perKarmaChancePercent = sanitizePercent(perKarmaChancePercent, DEFAULT_PER_KARMA_CHANCE_PERCENT);
    }

    public static BedwarsKarmaEventConfig defaults() {
        return new BedwarsKarmaEventConfig(
                DEFAULT_MIN_CHECK_SECONDS,
                DEFAULT_MAX_CHECK_SECONDS,
                DEFAULT_BASE_ROLL_CHANCE_PERCENT,
                DEFAULT_PER_KARMA_CHANCE_PERCENT
        );
    }

    public double minCheckSeconds() {
        return minCheckSeconds;
    }

    public double maxCheckSeconds() {
        return maxCheckSeconds;
    }

    public double baseRollChancePercent() {
        return baseRollChancePercent;
    }

    public double perKarmaChancePercent() {
        return perKarmaChancePercent;
    }

    public long minCheckDelayTicks() {
        return Math.max(0L, Math.round(minCheckSeconds * 20.0));
    }

    public long maxCheckDelayTicks() {
        return Math.max(minCheckDelayTicks(), Math.round(maxCheckSeconds * 20.0));
    }

    public double baseRollChance() {
        return baseRollChancePercent / 100.0;
    }

    public double perKarmaChance() {
        return perKarmaChancePercent / 100.0;
    }

    private static double sanitizeNonNegative(double value, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(0.0, value);
    }

    private static double sanitizePercent(double value, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(0.0, Math.min(100.0, value));
    }
}
