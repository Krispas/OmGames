package krispasi.omGames.hallsofcarnage;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public record HallsFloorModifiers(List<HallsModifierType> selected) {
    public HallsFloorModifiers {
        selected = selected == null ? List.of() : List.copyOf(selected);
    }

    public static HallsFloorModifiers none() {
        return new HallsFloorModifiers(List.of());
    }

    public boolean empty() {
        return selected.isEmpty();
    }

    public int compassLevel() {
        return countEffect("compass");
    }

    public int witherAfterSeconds() {
        int earliest = Integer.MAX_VALUE;
        for (HallsModifierType modifier : selected) {
            int seconds = intEffect(modifier, "wither_after_seconds", 0);
            if (seconds > 0) {
                earliest = Math.min(earliest, seconds);
            }
        }
        return earliest == Integer.MAX_VALUE ? 0 : earliest;
    }

    public double enemySpawnMultiplier() {
        return multipliedEffect("enemy_spawn_multiplier", 1.0);
    }

    public double trapMultiplier() {
        return multipliedEffect("trap_multiplier", 1.0) * Math.pow(1.33, trapBoostKinds().size());
    }

    public double lootMultiplier() {
        return multipliedEffect("loot_multiplier", 1.0);
    }

    public double sculkMultiplier() {
        return multipliedEffect("sculk_multiplier", 1.0);
    }

    public double coinMultiplier() {
        return multipliedEffect("coin_multiplier", 1.0);
    }

    public double corridorDistanceMultiplier(String corridorGeneration) {
        boolean maze = corridorGeneration != null && corridorGeneration.toLowerCase(Locale.ROOT).contains("maze");
        String key = maze ? "maze_corridor_distance_multiplier" : "corridor_distance_multiplier";
        double value = multipliedEffect(key, 1.0);
        if (maze && value == 1.0) {
            return multipliedEffect("corridor_distance_multiplier", 1.0);
        }
        return value;
    }

    public boolean useSpecialEnemy() {
        return countEffect("special_enemy") > 0;
    }

    public List<String> trapBoostKinds() {
        return selected.stream()
                .map(modifier -> stringEffect(modifier, "trap", ""))
                .filter(value -> !value.isBlank())
                .map(HallsFloorModifiers::normalizeId)
                .distinct()
                .toList();
    }

    public HallsScenario.FloorDefinition adjustFloor(HallsScenario.FloorDefinition floor, Random random) {
        if (floor == null) {
            return floor;
        }
        int extraRooms = 0;
        for (HallsModifierType modifier : selected) {
            int min = intEffect(modifier, "extra_rooms_min", 0);
            int max = Math.max(min, intEffect(modifier, "extra_rooms_max", min));
            if (max > 0) {
                extraRooms += min == max ? min : min + random.nextInt(max - min + 1);
            }
        }
        int rooms = Math.max(1, floor.rooms() + extraRooms);
        int breakables = Math.max(0, (int) Math.round(floor.breakables() * lootMultiplier()));
        int trappedRooms = Math.max(0, (int) Math.round(floor.trappedRooms() * trapMultiplier()));
        int sculkPatches = Math.max(0, (int) Math.round(floor.sculkPatches() * sculkMultiplier()));
        return new HallsScenario.FloorDefinition(
                floor.firstFloor(),
                floor.lastFloor(),
                floor.kind(),
                floor.levelType(),
                floor.difficulty(),
                rooms,
                floor.items(),
                breakables,
                trappedRooms,
                floor.minTrapsPerRoom(),
                floor.maxTrapsPerRoom(),
                floor.holes(),
                sculkPatches,
                floor.coinQuota()
        );
    }

    public Component hudComponent() {
        if (selected.isEmpty()) {
            return Component.empty();
        }
        Component component = Component.text("Mods ", NamedTextColor.DARK_GRAY);
        for (int i = 0; i < selected.size(); i++) {
            HallsModifierType modifier = selected.get(i);
            if (i > 0) {
                component = component.append(Component.text(" ", NamedTextColor.DARK_GRAY));
            }
            component = component.append(Component.text(modifier.icon(), modifier.good() ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
        return component;
    }

    public String displaySummary() {
        if (selected.isEmpty()) {
            return "None";
        }
        return selected.stream()
                .map(modifier -> modifier.icon() + " " + modifier.displayName())
                .reduce((first, second) -> first + " | " + second)
                .orElse("None");
    }

    private int countEffect(String key) {
        int count = 0;
        for (HallsModifierType modifier : selected) {
            Object value = modifier.effects().get(key);
            if (value instanceof Boolean bool && bool) {
                count++;
            } else if (value != null && !(value instanceof Boolean)) {
                count++;
            }
        }
        return count;
    }

    private double multipliedEffect(String key, double fallback) {
        double result = fallback;
        for (HallsModifierType modifier : selected) {
            Object value = modifier.effects().get(key);
            if (value instanceof Number number) {
                result *= number.doubleValue();
            } else if (value instanceof String text) {
                result *= parseDouble(text, 1.0);
            }
        }
        return result;
    }

    private int intEffect(HallsModifierType modifier, String key, int fallback) {
        Object value = modifier.effects().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String stringEffect(HallsModifierType modifier, String key, String fallback) {
        Object value = modifier.effects().get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
