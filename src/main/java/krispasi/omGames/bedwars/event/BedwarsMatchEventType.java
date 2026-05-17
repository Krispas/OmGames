package krispasi.omGames.bedwars.event;

import java.time.LocalDate;
import java.time.Month;
import java.util.Locale;

/**
 * Weighted match event types that can modify a BedWars game from the start.
 */
public enum BedwarsMatchEventType {
    SPEEDRUN("speedrun", "Speedrun!", "Everyone can be a speedrunner!!!", 3),
    SPEEDRUN_ANY("speedrun-any", "Speedrun Any%", "GET TO THE FINISH LINE!!!", 3),
    THE_RAPTURE("the-rapture", "The Rapture", "Anger of god is coming!", 3),
    BENEVOLENT_UPGRADES("benevolent-upgrades", "Benevolent Upgrades", "Better than a pizza party!", 3),
    LONG_ARMS("long-arms", "Long Arms!", "Put those long noodles to use!", 3),
    MOON_BIG("moon-big", "MOON BIG!!!", "Grian? Is the moon big???!!!", 3),
    BLOOD_MOON("blood-moon", "Blood Moon!", "Embrace your inner demon.", 3),
    SUMO("sumo", "SUMO!", "No damage. Only knockback.", 3),
    FALLOUT("fallout", "Fallout!", "Radiation is healthy!", 3),
    CHAOS("chaos", "Chaos!", "soahC ecarbmE", 3),
    IN_THIS_ECONOMY("in-this-economy", "In this economy?!", "Even ingame now?!", 3),
    APRIL_FOOLS("april-fools", "April Fools!", "Something is very wrong.", 2);

    private static final int APRIL_FOOLS_APRIL_WEIGHT = 15;

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
