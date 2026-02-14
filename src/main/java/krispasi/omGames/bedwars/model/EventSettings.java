package krispasi.omGames.bedwars.model;

public class EventSettings {
    private static final int DEFAULT_TIER_2_DELAY = 360;
    private static final int DEFAULT_TIER_3_DELAY = 720;
    private static final int DEFAULT_BED_DESTRUCTION_DELAY = 1440;
    private static final int DEFAULT_SUDDEN_DEATH_DELAY = 1800;
    private static final int DEFAULT_GAME_END_DELAY = 3000;

    private final int tier2Delay;
    private final int tier3Delay;
    private final int bedDestructionDelay;
    private final int suddenDeathDelay;
    private final int gameEndDelay;

    public EventSettings(int tier2Delay,
                         int tier3Delay,
                         int bedDestructionDelay,
                         int suddenDeathDelay,
                         int gameEndDelay) {
        this.tier2Delay = Math.max(0, tier2Delay);
        this.tier3Delay = Math.max(0, tier3Delay);
        this.bedDestructionDelay = Math.max(0, bedDestructionDelay);
        this.suddenDeathDelay = Math.max(0, suddenDeathDelay);
        this.gameEndDelay = Math.max(0, gameEndDelay);
    }

    public static EventSettings defaults() {
        return new EventSettings(
                DEFAULT_TIER_2_DELAY,
                DEFAULT_TIER_3_DELAY,
                DEFAULT_BED_DESTRUCTION_DELAY,
                DEFAULT_SUDDEN_DEATH_DELAY,
                DEFAULT_GAME_END_DELAY
        );
    }

    public int getTier2Delay() {
        return tier2Delay;
    }

    public int getTier3Delay() {
        return tier3Delay;
    }

    public int getBedDestructionDelay() {
        return bedDestructionDelay;
    }

    public int getSuddenDeathDelay() {
        return suddenDeathDelay;
    }

    public int getGameEndDelay() {
        return gameEndDelay;
    }
}
