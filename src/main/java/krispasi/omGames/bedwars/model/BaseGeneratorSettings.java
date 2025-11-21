package krispasi.omGames.bedwars.model;

public class BaseGeneratorSettings {
    private final long tier1IronInterval;
    private final long tier1GoldInterval;
    private final long tier2IronInterval;
    private final long tier2GoldInterval;
    private final long tier3IronInterval;
    private final long tier3GoldInterval;
    private final long tier2UpgradeSeconds;
    private final long tier3UpgradeSeconds;

    public BaseGeneratorSettings(long tier1IronInterval, long tier1GoldInterval,
                                 long tier2IronInterval, long tier2GoldInterval,
                                 long tier3IronInterval, long tier3GoldInterval,
                                 long tier2UpgradeSeconds, long tier3UpgradeSeconds) {
        this.tier1IronInterval = tier1IronInterval;
        this.tier1GoldInterval = tier1GoldInterval;
        this.tier2IronInterval = tier2IronInterval;
        this.tier2GoldInterval = tier2GoldInterval;
        this.tier3IronInterval = tier3IronInterval;
        this.tier3GoldInterval = tier3GoldInterval;
        this.tier2UpgradeSeconds = tier2UpgradeSeconds;
        this.tier3UpgradeSeconds = tier3UpgradeSeconds;
    }

    public long getTier1IronInterval() {
        return tier1IronInterval;
    }

    public long getTier1GoldInterval() {
        return tier1GoldInterval;
    }

    public long getTier2IronInterval() {
        return tier2IronInterval;
    }

    public long getTier2GoldInterval() {
        return tier2GoldInterval;
    }

    public long getTier3IronInterval() {
        return tier3IronInterval;
    }

    public long getTier3GoldInterval() {
        return tier3GoldInterval;
    }

    public long getTier2UpgradeSeconds() {
        return tier2UpgradeSeconds;
    }

    public long getTier3UpgradeSeconds() {
        return tier3UpgradeSeconds;
    }
}
