package krispasi.omGames.bedwars.upgrade;

import java.util.List;
import org.bukkit.Material;

/**
 * Defines available team upgrades with tiers and costs.
 * <p>Exposes human-friendly tier names and cost lookup for shop display.</p>
 */
public enum TeamUpgradeType {
    PROTECTION("Protection", Material.IRON_CHESTPLATE, new int[]{2, 4, 8, 16},
            List.of("Adds Protection to team armor.")),
    SHARPNESS("Sharpness", Material.IRON_SWORD, new int[]{4},
            List.of("Sharpness I on team swords.")),
    HASTE("Haste", Material.IRON_PICKAXE, new int[]{2, 4},
            List.of("Permanent Haste for your team.")),
    FEATHER_FALLING("Feather Falling", Material.FEATHER, new int[]{2, 4},
            List.of("Feather Falling on team boots.")),
    THORNS("Thorns", Material.CACTUS, new int[]{2, 4, 8},
            List.of("Thorns on team armor.")),
    FIRE_ASPECT("Fire Aspect", Material.BLAZE_POWDER, new int[]{4},
            List.of("Fire Aspect I on team weapons.")),
    GARRY("Garry the Warden", Material.WARDEN_SPAWN_EGG, new int[]{4, 6, 8},
            List.of("Shared upgrade.",
                    "Spawns Garry at the center.")),
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
            case GARRY -> garryName(tier);
            default -> displayName + " " + toRoman(tier);
        };
    }

    private String garryName(int tier) {
        return switch (tier) {
            case 1 -> "Garry";
            case 2 -> "Garry's Wife";
            case 3 -> "Garry Jr.";
            default -> displayName;
        };
    }

    private String forgeName(int tier) {
        return switch (tier) {
            case 1 -> "Iron Forge";
            case 2 -> "Golden Forge";
            case 3 -> "Emerald Forge";
            case 4 -> "Molten Forge";
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
