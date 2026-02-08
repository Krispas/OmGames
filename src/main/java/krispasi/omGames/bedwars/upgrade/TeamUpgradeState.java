package krispasi.omGames.bedwars.upgrade;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.List;
import java.util.Queue;

public class TeamUpgradeState {
    private final EnumMap<TeamUpgradeType, Integer> tiers = new EnumMap<>(TeamUpgradeType.class);
    private final Queue<TrapType> traps = new ArrayDeque<>();

    public int getTier(TeamUpgradeType type) {
        return tiers.getOrDefault(type, 0);
    }

    public void setTier(TeamUpgradeType type, int tier) {
        tiers.put(type, tier);
    }

    public int getTrapCount() {
        return traps.size();
    }

    public void addTrap(TrapType trap) {
        traps.add(trap);
    }

    public TrapType pollTrap() {
        return traps.poll();
    }

    public List<TrapType> getTraps() {
        return List.copyOf(traps);
    }

    public void clear() {
        tiers.clear();
        traps.clear();
    }
}
