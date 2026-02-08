package krispasi.omGames.bedwars.upgrade;

import java.util.List;
import org.bukkit.Material;

public enum TeamUpgradeType {
    PROTECTION("Protection", Material.IRON_CHESTPLATE, new int[]{2, 4, 8, 16},
            List.of("Adds Protection to team armor.")),
    SHARPNESS("Sharpness", Material.IRON_SWORD, new int[]{4},
            List.of("Sharpness I on team swords.")),
    HASTE("Haste", Material.IRON_PICKAXE, new int[]{2, 4},
            List.of("Permanent Haste for your team.")),
    FORGE("Forge", Material.FURNACE, new int[]{2, 4, 6, 8},
            List.of("Upgrade your base generator.")),
    HEAL_POOL("Heal Pool", Material.BEACON, new int[]{1},
            List.of("Regen I near base while bed is alive."));

    private final String displayName;
    private final Material icon;
    private final int[] costs;
    private final List<String> description;

    TeamUpgradeType(String displayName, Material icon, int[] costs, List<String> description) {
        this.displayName = displayName;
        this.icon = icon;
        this.costs = costs;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public List<String> description() {
        return description;
    }

    public int maxTier() {
        return costs.length;
    }

    public int costForTier(int tier) {
        if (tier <= 0 || tier > costs.length) {
            return -1;
        }
        return costs[tier - 1];
    }

    public int nextCost(int currentTier) {
        return costForTier(currentTier + 1);
    }

    public String tierName(int tier) {
        return switch (this) {
            case FORGE -> forgeName(tier);
            case SHARPNESS -> displayName;
            case HEAL_POOL -> displayName;
            default -> displayName + " " + toRoman(tier);
        };
    }

    private String forgeName(int tier) {
        return switch (tier) {
            case 1 -> "Iron Forge";
            case 2 -> "Golden Forge";
            case 3 -> "Molten Forge";
            case 4 -> "Molten Forge (Max)";
            default -> displayName;
        };
    }

    private String toRoman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(tier);
        };
    }
}
