package krispasi.omGames.bedwars.stats;

/**
 * Persistent BedWars stats for a single player.
 */
public class BedwarsPlayerStats {
    private int wins;
    private int kills;
    private int deaths;
    private int finalKills;
    private int finalDeaths;
    private int gamesPlayed;
    private int bedsBroken;
    private long parkourBestTimeMillis;
    private int parkourBestCheckpointUses;

    public BedwarsPlayerStats() {
        this(0, 0, 0, 0, 0, 0, 0, -1L, 0);
    }

    public BedwarsPlayerStats(int wins,
                              int kills,
                              int deaths,
                              int finalKills,
                              int finalDeaths,
                              int gamesPlayed,
                              int bedsBroken,
                              long parkourBestTimeMillis,
                              int parkourBestCheckpointUses) {
        this.wins = Math.max(0, wins);
        this.kills = Math.max(0, kills);
        this.deaths = Math.max(0, deaths);
        this.finalKills = Math.max(0, finalKills);
        this.finalDeaths = Math.max(0, finalDeaths);
        this.gamesPlayed = Math.max(0, gamesPlayed);
        this.bedsBroken = Math.max(0, bedsBroken);
        this.parkourBestTimeMillis = parkourBestTimeMillis < 0L ? -1L : parkourBestTimeMillis;
        this.parkourBestCheckpointUses = Math.max(0, parkourBestCheckpointUses);
    }

    public int getWins() {
        return wins;
    }

    public int getKills() {
        return kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public int getFinalKills() {
        return finalKills;
    }

    public int getFinalDeaths() {
        return finalDeaths;
    }

    public int getGamesPlayed() {
        return gamesPlayed;
    }

    public int getBedsBroken() {
        return bedsBroken;
    }

    public long getParkourBestTimeMillis() {
        return parkourBestTimeMillis;
    }

    public int getParkourBestCheckpointUses() {
        return parkourBestCheckpointUses;
    }

    public double getKillDeathRatio() {
        return ratio(kills, deaths);
    }

    public double getFinalKillDeathRatio() {
        return ratio(finalKills, finalDeaths);
    }

    public void addWins(int amount) {
        wins = Math.max(0, wins + amount);
    }

    public void setWins(int value) {
        wins = Math.max(0, value);
    }

    public void addKills(int amount) {
        kills = Math.max(0, kills + amount);
    }

    public void setKills(int value) {
        kills = Math.max(0, value);
    }

    public void addDeaths(int amount) {
        deaths = Math.max(0, deaths + amount);
    }

    public void setDeaths(int value) {
        deaths = Math.max(0, value);
    }

    public void addFinalKills(int amount) {
        finalKills = Math.max(0, finalKills + amount);
    }

    public void setFinalKills(int value) {
        finalKills = Math.max(0, value);
    }

    public void addFinalDeaths(int amount) {
        finalDeaths = Math.max(0, finalDeaths + amount);
    }

    public void setFinalDeaths(int value) {
        finalDeaths = Math.max(0, value);
    }

    public void addGamesPlayed(int amount) {
        gamesPlayed = Math.max(0, gamesPlayed + amount);
    }

    public void setGamesPlayed(int value) {
        gamesPlayed = Math.max(0, value);
    }

    public void addBedsBroken(int amount) {
        bedsBroken = Math.max(0, bedsBroken + amount);
    }

    public void setBedsBroken(int value) {
        bedsBroken = Math.max(0, value);
    }

    public void setParkourBestTimeMillis(long value) {
        parkourBestTimeMillis = value < 0L ? -1L : value;
        if (parkourBestTimeMillis < 0L) {
            parkourBestCheckpointUses = 0;
        }
    }

    public void addParkourBestTimeMillis(long amount) {
        long base = parkourBestTimeMillis < 0L ? 0L : parkourBestTimeMillis;
        long updated = base + amount;
        setParkourBestTimeMillis(updated);
    }

    public void setParkourBestCheckpointUses(int value) {
        parkourBestCheckpointUses = Math.max(0, value);
    }

    public void addParkourBestCheckpointUses(int amount) {
        parkourBestCheckpointUses = Math.max(0, parkourBestCheckpointUses + amount);
    }

    public void applyParkourFinish(long timeMillis, int checkpointUses) {
        long candidateTime = Math.max(0L, timeMillis);
        int candidateCheckpoints = Math.max(0, checkpointUses);
        if (parkourBestTimeMillis < 0L
                || candidateTime < parkourBestTimeMillis
                || (candidateTime == parkourBestTimeMillis && candidateCheckpoints < parkourBestCheckpointUses)) {
            parkourBestTimeMillis = candidateTime;
            parkourBestCheckpointUses = candidateCheckpoints;
        }
    }

    public BedwarsPlayerStats copy() {
        return new BedwarsPlayerStats(
                wins,
                kills,
                deaths,
                finalKills,
                finalDeaths,
                gamesPlayed,
                bedsBroken,
                parkourBestTimeMillis,
                parkourBestCheckpointUses
        );
    }

    private double ratio(int numerator, int denominator) {
        int safeNumerator = Math.max(0, numerator);
        int safeDenominator = Math.max(0, denominator);
        if (safeDenominator == 0) {
            return safeNumerator;
        }
        return (double) safeNumerator / safeDenominator;
    }
}
