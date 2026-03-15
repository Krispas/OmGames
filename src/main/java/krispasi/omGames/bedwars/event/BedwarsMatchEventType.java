package krispasi.omGames.bedwars.event;

import java.time.LocalDate;
import java.time.Month;
import java.util.Locale;

/**
 * Weighted match event types that can modify a BedWars game from the start.
 */
public enum BedwarsMatchEventType {
    SPEEDRUN("speedrun", "Speedrun!", "Everyone is faster.", 3),
    BENEVOLENT_UPGRADES("benevolent-upgrades", "Benevolent Upgrades", "Teams begin with maxed upgrades.", 3),
    LONG_ARMS("long-arms", "Long Arms!", "Your block reach is extended.", 3),
    MOON_BIG("moon-big", "MOON BIG!!!", "Jump like the moon owns you.", 3),
    BLOOD_MOON("blood-moon", "Blood Moon!", "The night bites back.", 2),
    CHAOS("chaos", "Chaos!", "Everything is active and maxed out.", 2),
    IN_THIS_ECONOMY("in-this-economy", "In this economy?!", "The resources dried up.", 3),
    APRIL_FOOLS("april-fools", "April Fools!", "Something is very wrong.", 1);

    private static final int APRIL_FOOLS_APRIL_WEIGHT = 7;

    private final String key;
    private final String displayName;
    private final String subtitle;
    private final int defaultWeight;

    BedwarsMatchEventType(String key, String displayName, String subtitle, int defaultWeight) {
        this.key = key;
        this.displayName = displayName;
        this.subtitle = subtitle;
        this.defaultWeight = defaultWeight;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public String subtitle() {
        return subtitle;
    }

    public int defaultWeight() {
        return defaultWeight;
    }

    public int effectiveWeight(int configuredWeight) {
        int normalized = Math.max(0, configuredWeight);
        if (this == APRIL_FOOLS
                && normalized == defaultWeight
                && LocalDate.now().getMonth() == Month.APRIL) {
            return APRIL_FOOLS_APRIL_WEIGHT;
        }
        return normalized;
    }

    public static BedwarsMatchEventType fromKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replace(' ', '-');
        for (BedwarsMatchEventType type : values()) {
            if (type.key.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
