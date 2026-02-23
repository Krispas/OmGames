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

    public BedwarsPlayerStats() {
        this(0, 0, 0, 0, 0, 0, 0);
    }

    public BedwarsPlayerStats(int wins,
                              int kills,
                              int deaths,
                              int finalKills,
                              int finalDeaths,
                              int gamesPlayed,
                              int bedsBroken) {
        this.wins = Math.max(0, wins);
        this.kills = Math.max(0, kills);
        this.deaths = Math.max(0, deaths);
        this.finalKills = Math.max(0, finalKills);
        this.finalDeaths = Math.max(0, finalDeaths);
        this.gamesPlayed = Math.max(0, gamesPlayed);
        this.bedsBroken = Math.max(0, bedsBroken);
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

    public void addWins(int amount) {
        wins = Math.max(0, wins + amount);
    }

    public void addKills(int amount) {
        kills = Math.max(0, kills + amount);
    }

    public void addDeaths(int amount) {
        deaths = Math.max(0, deaths + amount);
    }

    public void addFinalKills(int amount) {
        finalKills = Math.max(0, finalKills + amount);
    }

    public void addFinalDeaths(int amount) {
        finalDeaths = Math.max(0, finalDeaths + amount);
    }

    public void addGamesPlayed(int amount) {
        gamesPlayed = Math.max(0, gamesPlayed + amount);
    }

    public void addBedsBroken(int amount) {
        bedsBroken = Math.max(0, bedsBroken + amount);
    }

    public BedwarsPlayerStats copy() {
        return new BedwarsPlayerStats(wins, kills, deaths, finalKills, finalDeaths, gamesPlayed, bedsBroken);
    }
}
