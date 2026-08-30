package krispasi.omGames.bank.fortuna;

import java.util.Locale;

public enum FortunaOutcome {
    HOME("home", "Home win"),
    DRAW("draw", "Draw"),
    AWAY("away", "Away win");

    private final String key;
    private final String displayName;

    FortunaOutcome(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public static FortunaOutcome fromKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (FortunaOutcome outcome : values()) {
            if (outcome.key.equals(normalized) || outcome.name().equalsIgnoreCase(normalized)) {
                return outcome;
            }
        }
        return null;
    }
}
