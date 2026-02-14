package krispasi.omGames.bedwars.generator;

import java.util.Arrays;

public class GeneratorSettings {
    private static final long[] DEFAULT_BASE_IRON_INTERVALS = {20L, 16L, 14L, 12L, 8L};
    private static final long[] DEFAULT_BASE_GOLD_INTERVALS = {160L, 120L, 100L, 90L, 70L};
    private static final int[] DEFAULT_BASE_IRON_AMOUNTS = {1, 1, 1, 1, 2};
    private static final int[] DEFAULT_BASE_GOLD_AMOUNTS = {1, 1, 1, 1, 2};
    private static final int[] DEFAULT_BASE_IRON_CAPS = {32, 64, 96, 112, 128};
    private static final int[] DEFAULT_BASE_GOLD_CAPS = {4, 8, 12, 20, 30};
    private static final long[] DEFAULT_BASE_EMERALD_INTERVALS = {0L, 0L, 0L, 80L * 20L, 40L * 20L};
    private static final int[] DEFAULT_BASE_EMERALD_AMOUNTS = {0, 0, 0, 0, 1};
    private static final int[] DEFAULT_BASE_EMERALD_CAPS = {0, 0, 0, 1, 2};
    private static final long[] DEFAULT_DIAMOND_INTERVALS = {30L * 20L, 23L * 20L, 12L * 20L};
    private static final long[] DEFAULT_EMERALD_INTERVALS = {50L * 20L, 30L * 20L, 12L * 20L};
    private static final int DEFAULT_DIAMOND_CAP = 4;
    private static final int DEFAULT_EMERALD_CAP = 2;
    private static final long DEFAULT_RESOURCE_DESPAWN_MILLIS = 5L * 60L * 1000L;

    private final long[] baseIronIntervals;
    private final long[] baseGoldIntervals;
    private final int[] baseIronAmounts;
    private final int[] baseGoldAmounts;
    private final int[] baseIronCaps;
    private final int[] baseGoldCaps;
    private final long[] baseEmeraldIntervals;
    private final int[] baseEmeraldAmounts;
    private final int[] baseEmeraldCaps;
    private final long[] diamondIntervals;
    private final long[] emeraldIntervals;
    private final int diamondCap;
    private final int emeraldCap;
    private final long resourceDespawnMillis;

    public GeneratorSettings(long[] baseIronIntervals,
                             long[] baseGoldIntervals,
                             int[] baseIronAmounts,
                             int[] baseGoldAmounts,
                             int[] baseIronCaps,
                             int[] baseGoldCaps,
                             long[] baseEmeraldIntervals,
                             int[] baseEmeraldAmounts,
                             int[] baseEmeraldCaps,
                             long[] diamondIntervals,
                             long[] emeraldIntervals,
                             int diamondCap,
                             int emeraldCap,
                             long resourceDespawnMillis) {
        this.baseIronIntervals = Arrays.copyOf(baseIronIntervals, baseIronIntervals.length);
        this.baseGoldIntervals = Arrays.copyOf(baseGoldIntervals, baseGoldIntervals.length);
        this.baseIronAmounts = Arrays.copyOf(baseIronAmounts, baseIronAmounts.length);
        this.baseGoldAmounts = Arrays.copyOf(baseGoldAmounts, baseGoldAmounts.length);
        this.baseIronCaps = Arrays.copyOf(baseIronCaps, baseIronCaps.length);
        this.baseGoldCaps = Arrays.copyOf(baseGoldCaps, baseGoldCaps.length);
        this.baseEmeraldIntervals = Arrays.copyOf(baseEmeraldIntervals, baseEmeraldIntervals.length);
        this.baseEmeraldAmounts = Arrays.copyOf(baseEmeraldAmounts, baseEmeraldAmounts.length);
        this.baseEmeraldCaps = Arrays.copyOf(baseEmeraldCaps, baseEmeraldCaps.length);
        this.diamondIntervals = Arrays.copyOf(diamondIntervals, diamondIntervals.length);
        this.emeraldIntervals = Arrays.copyOf(emeraldIntervals, emeraldIntervals.length);
        this.diamondCap = diamondCap;
        this.emeraldCap = emeraldCap;
        this.resourceDespawnMillis = resourceDespawnMillis;
    }

    public static GeneratorSettings defaults() {
        return new GeneratorSettings(
                DEFAULT_BASE_IRON_INTERVALS,
                DEFAULT_BASE_GOLD_INTERVALS,
                DEFAULT_BASE_IRON_AMOUNTS,
                DEFAULT_BASE_GOLD_AMOUNTS,
                DEFAULT_BASE_IRON_CAPS,
                DEFAULT_BASE_GOLD_CAPS,
                DEFAULT_BASE_EMERALD_INTERVALS,
                DEFAULT_BASE_EMERALD_AMOUNTS,
                DEFAULT_BASE_EMERALD_CAPS,
                DEFAULT_DIAMOND_INTERVALS,
                DEFAULT_EMERALD_INTERVALS,
                DEFAULT_DIAMOND_CAP,
                DEFAULT_EMERALD_CAP,
                DEFAULT_RESOURCE_DESPAWN_MILLIS
        );
    }

    public long baseIronInterval(int tier) {
        return baseIronIntervals[clampBaseTier(tier)];
    }

    public long baseGoldInterval(int tier) {
        return baseGoldIntervals[clampBaseTier(tier)];
    }

    public int baseIronAmount(int tier) {
        return baseIronAmounts[clampBaseTier(tier)];
    }

    public int baseGoldAmount(int tier) {
        return baseGoldAmounts[clampBaseTier(tier)];
    }

    public int baseIronCap(int tier) {
        return baseIronCaps[clampBaseTier(tier)];
    }

    public int baseGoldCap(int tier) {
        return baseGoldCaps[clampBaseTier(tier)];
    }

    public long baseEmeraldInterval(int tier) {
        return baseEmeraldIntervals[clampBaseTier(tier)];
    }

    public int baseEmeraldAmount(int tier) {
        return baseEmeraldAmounts[clampBaseTier(tier)];
    }

    public int baseEmeraldCap(int tier) {
        return baseEmeraldCaps[clampBaseTier(tier)];
    }

    public long diamondInterval(int tier) {
        return diamondIntervals[clampAdvancedTier(tier, diamondIntervals.length)];
    }

    public long emeraldInterval(int tier) {
        return emeraldIntervals[clampAdvancedTier(tier, emeraldIntervals.length)];
    }

    public int getDiamondCap() {
        return diamondCap;
    }

    public int getEmeraldCap() {
        return emeraldCap;
    }

    public long getResourceDespawnMillis() {
        return resourceDespawnMillis;
    }

    public long[] getBaseIronIntervals() {
        return Arrays.copyOf(baseIronIntervals, baseIronIntervals.length);
    }

    public long[] getBaseGoldIntervals() {
        return Arrays.copyOf(baseGoldIntervals, baseGoldIntervals.length);
    }

    public int[] getBaseIronAmounts() {
        return Arrays.copyOf(baseIronAmounts, baseIronAmounts.length);
    }

    public int[] getBaseGoldAmounts() {
        return Arrays.copyOf(baseGoldAmounts, baseGoldAmounts.length);
    }

    public int[] getBaseIronCaps() {
        return Arrays.copyOf(baseIronCaps, baseIronCaps.length);
    }

    public int[] getBaseGoldCaps() {
        return Arrays.copyOf(baseGoldCaps, baseGoldCaps.length);
    }

    public long[] getBaseEmeraldIntervals() {
        return Arrays.copyOf(baseEmeraldIntervals, baseEmeraldIntervals.length);
    }

    public int[] getBaseEmeraldAmounts() {
        return Arrays.copyOf(baseEmeraldAmounts, baseEmeraldAmounts.length);
    }

    public int[] getBaseEmeraldCaps() {
        return Arrays.copyOf(baseEmeraldCaps, baseEmeraldCaps.length);
    }

    public long[] getDiamondIntervals() {
        return Arrays.copyOf(diamondIntervals, diamondIntervals.length);
    }

    public long[] getEmeraldIntervals() {
        return Arrays.copyOf(emeraldIntervals, emeraldIntervals.length);
    }

    private int clampBaseTier(int tier) {
        int max = baseIronIntervals.length - 1;
        return Math.max(0, Math.min(max, tier));
    }

    private int clampAdvancedTier(int tier, int size) {
        int max = Math.max(1, size) - 1;
        int clamped = Math.max(1, Math.min(size, tier));
        return clamped - 1;
    }
}
