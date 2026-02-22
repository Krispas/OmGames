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
            List.of("Blindness + Slowness.")),
    COUNTER_OFFENSIVE("Counter-Offensive Trap", Material.FEATHER,
            List.of("Speed + Jump for your team.")),
    ALARM("Alarm Trap", Material.REDSTONE_TORCH,
            List.of("Alerts your team.", "Reveals enemies.")),
    MINER_FATIGUE("Miner Fatigue Trap", Material.IRON_PICKAXE,
            List.of("Mining Fatigue on enemies."));

    private final String displayName;
    private final Material icon;
    private final List<String> description;

    TrapType(String displayName, Material icon, List<String> description) {
        this.displayName = displayName;
        this.icon = icon;
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
}
