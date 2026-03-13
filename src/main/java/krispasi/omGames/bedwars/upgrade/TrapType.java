package krispasi.omGames.bedwars.upgrade;

import java.util.List;
import org.bukkit.Material;

/**
 * Defines trap options and their descriptions.
 * <p>Queued in {@link krispasi.omGames.bedwars.upgrade.TeamUpgradeState} and triggered
 * when enemies enter base range.</p>
 */
public enum TrapType {
    ITS_A_TRAP("It's a Trap!", Material.TRIPWIRE_HOOK,
            List.of("Blindness + Slowness."),
            null,
            1,
            1,
            false),
    COUNTER_OFFENSIVE("Counter-Offensive Trap", Material.FEATHER,
            List.of("Speed + Jump for your team."),
            null,
            1,
            1,
            false),
    ALARM("Alarm Trap", Material.REDSTONE_TORCH,
            List.of("Alerts your team.", "Reveals enemies."),
            null,
            1,
            1,
            false),
    MINER_FATIGUE("Miner Fatigue Trap", Material.IRON_PICKAXE,
            List.of("Mining Fatigue on enemies."),
            null,
            1,
            1,
            false),
    ILLUSION("Illusion Trap", Material.END_PORTAL_FRAME,
            List.of("Warps nearby enemies", "back to their base."),
            "illusion_trap",
            3,
            6,
            true);

    private final String displayName;
    private final Material icon;
    private final List<String> description;
    private final String rotatingUpgradeItemId;
    private final int soloDoubleCost;
    private final int trioQuadCost;
    private final boolean oneTimePurchase;

    TrapType(String displayName,
             Material icon,
             List<String> description,
             String rotatingUpgradeItemId,
             int soloDoubleCost,
             int trioQuadCost,
             boolean oneTimePurchase) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
        this.rotatingUpgradeItemId = rotatingUpgradeItemId;
        this.soloDoubleCost = Math.max(1, soloDoubleCost);
        this.trioQuadCost = Math.max(1, trioQuadCost);
        this.oneTimePurchase = oneTimePurchase;
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

    public String rotatingUpgradeItemId() {
        return rotatingUpgradeItemId;
    }

    public int baseCost(int maxTeamSize) {
        return maxTeamSize <= 2 ? soloDoubleCost : trioQuadCost;
    }

    public boolean oneTimePurchase() {
        return oneTimePurchase;
    }
}
