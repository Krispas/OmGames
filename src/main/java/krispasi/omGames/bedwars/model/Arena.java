package krispasi.omGames.bedwars.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import krispasi.omGames.bedwars.generator.GeneratorInfo;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class Arena {
    private final String id;
    private final String worldName;
    private final int lobbyHeight;
    private final BlockPoint center;
    private final int baseGeneratorRadius;
    private final int advancedGeneratorRadius;
    private final Map<TeamColor, BedLocation> beds;
    private final List<GeneratorInfo> generators;
    private final Map<TeamColor, BlockPoint> spawns;

    public Arena(String id,
                 String worldName,
                 int lobbyHeight,
                 BlockPoint center,
                 int baseGeneratorRadius,
                 int advancedGeneratorRadius,
                 Map<TeamColor, BedLocation> beds,
                 List<GeneratorInfo> generators,
                 Map<TeamColor, BlockPoint> spawns) {
        this.id = Objects.requireNonNull(id, "id");
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.lobbyHeight = lobbyHeight;
        this.center = center;
        this.baseGeneratorRadius = baseGeneratorRadius;
        this.advancedGeneratorRadius = advancedGeneratorRadius;
        this.beds = Collections.unmodifiableMap(new EnumMap<>(beds));
        this.generators = List.copyOf(generators);
        this.spawns = Collections.unmodifiableMap(new EnumMap<>(spawns));
    }

    public String getId() {
        return id;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getLobbyHeight() {
        return lobbyHeight;
    }

    public BlockPoint getCenter() {
        return center;
    }

    public int getBaseGeneratorRadius() {
        return baseGeneratorRadius;
    }

    public int getAdvancedGeneratorRadius() {
        return advancedGeneratorRadius;
    }

    public Map<TeamColor, BedLocation> getBeds() {
        return beds;
    }

    public List<GeneratorInfo> getGenerators() {
        return generators;
    }

    public Map<TeamColor, BlockPoint> getSpawns() {
        return spawns;
    }

    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    public Location getSpawn(TeamColor team) {
        BlockPoint point = spawns.get(team);
        World world = getWorld();
        if (point == null || world == null) {
            return null;
        }
        return point.toLocation(world);
    }

    public Location getLobbyLocation() {
        World world = getWorld();
        if (world == null || center == null) {
            return null;
        }
        return new Location(world, center.x() + 0.5, lobbyHeight, center.z() + 0.5);
    }

    public List<TeamColor> getTeams() {
        return TeamColor.ordered().stream()
                .filter(color -> beds.containsKey(color) || spawns.containsKey(color))
                .collect(Collectors.toList());
    }
}
