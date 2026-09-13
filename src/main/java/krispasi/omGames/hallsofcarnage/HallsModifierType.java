package krispasi.omGames.hallsofcarnage;

import java.util.Map;

public record HallsModifierType(
        String id,
        String displayName,
        String icon,
        Kind kind,
        int weight,
        Map<String, Object> effects
) {
    public HallsModifierType {
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        icon = icon == null || icon.isBlank() ? "?" : icon;
        weight = Math.max(0, weight);
        effects = effects == null ? Map.of() : Map.copyOf(effects);
    }

    public boolean good() {
        return kind == Kind.GOOD;
    }

    public enum Kind {
        GOOD,
        BAD
    }
}
