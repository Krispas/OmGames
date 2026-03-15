package krispasi.omGames.bedwars.event;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Global weighted match-event configuration loaded from {@code bedwars.yml}.
 */
public class BedwarsMatchEventConfig {
    private static final double DEFAULT_CHANCE_PERCENT = 40.0;

    private final boolean enabled;
    private final double chancePercent;
    private final Map<BedwarsMatchEventType, Integer> weights;

    public BedwarsMatchEventConfig(boolean enabled,
                                   double chancePercent,
                                   Map<BedwarsMatchEventType, Integer> weights) {
        this.enabled = enabled;
        this.chancePercent = Math.max(0.0, Math.min(100.0, chancePercent));
        EnumMap<BedwarsMatchEventType, Integer> copy = new EnumMap<>(BedwarsMatchEventType.class);
        if (weights != null) {
            for (Map.Entry<BedwarsMatchEventType, Integer> entry : weights.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                copy.put(entry.getKey(), Math.max(0, entry.getValue() != null ? entry.getValue() : 0));
            }
        }
        for (BedwarsMatchEventType type : BedwarsMatchEventType.values()) {
            copy.putIfAbsent(type, type.defaultWeight());
        }
        this.weights = Collections.unmodifiableMap(copy);
    }

    public static BedwarsMatchEventConfig defaults() {
        return new BedwarsMatchEventConfig(true, DEFAULT_CHANCE_PERCENT, Map.of());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double chancePercent() {
        return chancePercent;
    }

    public int weight(BedwarsMatchEventType type) {
        if (type == null) {
            return 0;
        }
        return type.effectiveWeight(weights.getOrDefault(type, 0));
    }

    public boolean hasEligibleEvents() {
        for (BedwarsMatchEventType type : BedwarsMatchEventType.values()) {
            if (weight(type) > 0) {
                return true;
            }
        }
        return false;
    }

    public Map<BedwarsMatchEventType, Integer> weights() {
        return weights;
    }
}
