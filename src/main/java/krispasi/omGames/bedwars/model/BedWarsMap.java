package krispasi.omGames.bedwars.model;

import org.bukkit.Location;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class BedWarsMap {
    private final String name;
    private final String worldName;
    private final int lobbyHeight;
    private final Location center;
    private final Map<String, TeamConfig> teams;
    private final Set<Location> diamondGens;
    private final Set<Location> emeraldGens;

    public BedWarsMap(String name, String worldName, int lobbyHeight, Location center,
                      Map<String, TeamConfig> teams, Set<Location> diamondGens, Set<Location> emeraldGens) {
        this.name = name;
        this.worldName = worldName;
        this.lobbyHeight = lobbyHeight;
        this.center = center;
        this.teams = teams;
        this.diamondGens = diamondGens;
        this.emeraldGens = emeraldGens;
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getLobbyHeight() {
        return lobbyHeight;
    }

    public Location getCenter() {
        return center;
    }

    public Map<String, TeamConfig> getTeams() {
        return Collections.unmodifiableMap(teams);
    }

    public Set<Location> getDiamondGens() {
        return Collections.unmodifiableSet(diamondGens);
    }

    public Set<Location> getEmeraldGens() {
        return Collections.unmodifiableSet(emeraldGens);
    }
}
