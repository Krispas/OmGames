package krispasi.omGames.bedwars.model;

public class GeneratorSettings {
    private final long tier1Interval;
    private final long tier2Interval;
    private final long tier3Interval;
    private final long tier2UpgradeSeconds;
    private final long tier3UpgradeSeconds;

    public GeneratorSettings(long tier1Interval, long tier2Interval, long tier3Interval,
                             long tier2UpgradeSeconds, long tier3UpgradeSeconds) {
        this.tier1Interval = tier1Interval;
        this.tier2Interval = tier2Interval;
        this.tier3Interval = tier3Interval;
        this.tier2UpgradeSeconds = tier2UpgradeSeconds;
        this.tier3UpgradeSeconds = tier3UpgradeSeconds;
    }

    public long getTier1Interval() {
        return tier1Interval;
    }

    public long getTier2Interval() {
        return tier2Interval;
    }

    public long getTier3Interval() {
        return tier3Interval;
    }

    public long getTier2UpgradeSeconds() {
        return tier2UpgradeSeconds;
    }

    public long getTier3UpgradeSeconds() {
        return tier3UpgradeSeconds;
    }
}
