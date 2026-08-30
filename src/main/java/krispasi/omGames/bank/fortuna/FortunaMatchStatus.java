package krispasi.omGames.bank.fortuna;

import java.util.Locale;

public enum FortunaMatchStatus {
    UPCOMING("upcoming", "Upcoming"),
    ACTIVE("active", "Active"),
    FINISHED("finished", "Finished");

    private final String key;
    private final String displayName;

    FortunaMatchStatus(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public static FortunaMatchStatus fromKey(String key) {
        if (key == null || key.isBlank()) {
            return UPCOMING;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (FortunaMatchStatus status : values()) {
            if (status.key.equals(normalized) || status.name().equalsIgnoreCase(normalized)) {
                return status;
            }
        }
        return UPCOMING;
    }
}
