package krispasi.omGames.bedwars.model;

import org.bukkit.Location;

public class TeamConfig {
    private final String name;
    private final Location spawn;
    private final Location bed;
    private final Location generator;

    public TeamConfig(String name, Location spawn, Location bed, Location generator) {
        this.name = name;
        this.spawn = spawn;
        this.bed = bed;
        this.generator = generator;
    }

    public String getName() {
        return name;
    }

    public Location getSpawn() {
        return spawn;
    }

    public Location getBed() {
        return bed;
    }

    public Location getGenerator() {
        return generator;
    }
}
