package krispasi.omGames.bedwars.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import krispasi.omGames.bedwars.generator.GeneratorInfo;
import krispasi.omGames.bedwars.generator.GeneratorSettings;
import krispasi.omGames.bedwars.model.EventSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Immutable arena configuration container.
 * <p>Holds world metadata, bounds, spawns, beds, generators, and shop locations loaded by
 * {@link krispasi.omGames.bedwars.config.BedwarsConfigLoader}.</p>
 * @see krispasi.omGames.bedwars.model.TeamColor
 */
public class Arena {
    private final String id;
    private final String worldName;
    private final BlockPoint center;
    private final int centerRadius;
    private final BlockPoint gameLobby;
    private final BlockPoint mapLobby;
    private final int baseRadius;
    private final BlockPoint corner1;
    private final BlockPoint corner2;
    private final int baseGeneratorRadius;
    private final int advancedGeneratorRadius;
    private final GeneratorSettings generatorSettings;
    private final EventSettings eventSettings;
    private final Map<TeamColor, BedLocation> beds;
    private final List<GeneratorInfo> generators;
    private final Map<TeamColor, BlockPoint> spawns;
    private final Map<TeamColor, ShopLocation> mainShops;
    private final Map<TeamColor, ShopLocation> upgradeShops;

    public Arena(String id,
                 String worldName,
                 BlockPoint center,
                 int centerRadius,
                 BlockPoint gameLobby,
                 BlockPoint mapLobby,
                 int baseRadius,
                 BlockPoint corner1,
                 BlockPoint corner2,
                 int baseGeneratorRadius,
                 int advancedGeneratorRadius,
                 GeneratorSettings generatorSettings,
                 EventSettings eventSettings,
                 Map<TeamColor, BedLocation> beds,
                 List<GeneratorInfo> generators,
                 Map<TeamColor, BlockPoint> spawns,
                 Map<TeamColor, ShopLocation> mainShops,
                 Map<TeamColor, ShopLocation> upgradeShops) {
        this.id = Objects.requireNonNull(id, "id");
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.center = center;
        this.centerRadius = centerRadius;
        this.gameLobby = gameLobby;
        this.mapLobby = mapLobby;
        this.baseRadius = baseRadius;
        this.corner1 = corner1;
        this.corner2 = corner2;
        this.baseGeneratorRadius = baseGeneratorRadius;
        this.advancedGeneratorRadius = advancedGeneratorRadius;
        this.generatorSettings = generatorSettings;
        this.eventSettings = eventSettings;
        this.beds = Collections.unmodifiableMap(new EnumMap<>(beds));
        this.generators = List.copyOf(generators);
        this.spawns = Collections.unmodifiableMap(new EnumMap<>(spawns));
        this.mainShops = Collections.unmodifiableMap(new EnumMap<>(mainShops));
        this.upgradeShops = Collections.unmodifiableMap(new EnumMap<>(upgradeShops));
    }

    public String getId() {
        return id;
    }

    public String getWorldName() {
        return worldName;
    }

    public BlockPoint getCenter() {
        return center;
    }

    public int getCenterRadius() {
        return centerRadius;
    }

    public BlockPoint getGameLobby() {
        return gameLobby;
    }

    public BlockPoint getMapLobby() {
        return mapLobby;
    }

    public int getBaseRadius() {
        return baseRadius;
    }

    public BlockPoint getCorner1() {
        return corner1;
    }

    public BlockPoint getCorner2() {
        return corner2;
    }

    public int getBaseGeneratorRadius() {
        return baseGeneratorRadius;
    }

    public int getAdvancedGeneratorRadius() {
        return advancedGeneratorRadius;
    }

    public GeneratorSettings getGeneratorSettings() {
        return generatorSettings;
    }

    public EventSettings getEventSettings() {
        return eventSettings;
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

    public Map<TeamColor, ShopLocation> getMainShops() {
        return mainShops;
    }

    public Map<TeamColor, ShopLocation> getUpgradeShops() {
        return upgradeShops;
    }

    public ShopLocation getShop(TeamColor team, ShopType type) {
        if (type == ShopType.UPGRADES) {
            return upgradeShops.get(team);
        }
        return mainShops.get(team);
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
        return toLobbyLocation(gameLobby);
    }

    public Location getMapLobbyLocation() {
        return toLobbyLocation(mapLobby);
    }

    private Location toLobbyLocation(BlockPoint point) {
        World world = getWorld();
        if (world == null || point == null) {
            return null;
        }
        return new Location(world, point.x() + 0.5, point.y(), point.z() + 0.5);
    }

    public List<TeamColor> getTeams() {
        return TeamColor.ordered().stream()
                .filter(color -> beds.containsKey(color) || spawns.containsKey(color))
                .collect(Collectors.toList());
    }
}
