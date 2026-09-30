package krispasi.omGames.hallsofcarnage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import krispasi.omGames.hallsofcarnage.HallsExplorationGenerator.Cell;
import krispasi.omGames.hallsofcarnage.HallsExplorationGenerator.Room;
import krispasi.omGames.hallsofcarnage.HallsSession.ExplorationBuild;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Session-owned main-thread cursors; no scheduling or lifecycle ownership outside the session. */
final class HallsSessionFloorBuildJob {
    private static final int CLEAR_COLUMNS_PER_TICK = 3;
    private static final int CORRIDOR_CELLS_PER_TICK = 96;
    private static final int CONTENT_CELLS_PER_TICK = 128;
    private static final long CONTENT_BUDGET_NANOS = 4_000_000L;
    private static final int MIN_TRANSITION_TICKS = 100;
    private static final double[] PROGRESS = {0, .03, .18, .20, .40, .55, .65, .70, .78, .83, .88, .90, .93, .96, .99, 1};
    private static final String[] PHASES = {"Planning", "Clearing", "Elevator", "Rooms", "Corridors", "Traps",
            "Liquid Planning", "Liquids", "Room Vegetation", "Corridor Vegetation", "Sculk", "Research Crate",
            "Distilleries", "Library Vents", "Breakables", "Arrival"};

    private final HallsSession session;
    private final int floor;
    private final int originX;
    private int clearRadius;
    private int clearX;
    private int stage;
    private int index;
    private int ticksElapsed;
    private int rareRoom = -1;
    private int vegetationRoom;
    private int distilleriesPlaced;
    private ExplorationBuild build;
    private List<Cell> cells = List.of();
    private List<Room> distilleryRooms = List.of();
    private final Set<Cell> reserved = new HashSet<>();
    private final Set<Cell> liquids = new HashSet<>();
    private final Set<Cell> vegetation = new HashSet<>();
    private Set<Cell> vegetationReserved = Set.of();
    private HallsSessionTrapRuntime.GeneratedTrapPlacement traps;
    private HallsSessionSculkRuntime.PatchPlacement sculk;
    private double phaseProgress;

    HallsSessionFloorBuildJob(HallsSession session, int floor, int clearRadius, int originX) {
        this.session = session;
        this.floor = floor;
        this.clearRadius = clearRadius;
        this.originX = originX;
    }

    void tick() {
        if (!session.floorBuildRunning()) {
            session.cancelFloorBuildTask();
            return;
        }
        ticksElapsed++;
        switch (stage) {
            case 0 -> {
                build = session.beginFloorBuild(floor);
                clearRadius = Math.max(clearRadius, session.floorBuildClearRadius());
                clearX = originX - clearRadius;
                advance();
            }
            case 1 -> {
                int endX = Math.min(originX + clearRadius, clearX + CLEAR_COLUMNS_PER_TICK - 1);
                session.clearBuildVolumeColumns(clearX, endX, clearRadius);
                clearX = endX + 1;
                phaseProgress = ratio(clearX - (originX - clearRadius), clearRadius * 2 + 1);
                if (clearX > originX + clearRadius) advance();
            }
            case 2 -> {
                session.buildFloorElevator();
                advance();
            }
            case 3 -> {
                if (index < build.plan().rooms().size()) {
                    session.buildFloorRoom(build, build.plan().rooms().get(index++));
                    phaseProgress = ratio(index, build.plan().rooms().size());
                } else {
                    cells = new ArrayList<>(build.plan().corridorShellCells());
                    advance();
                }
            }
            case 4 -> {
                int end = Math.min(cells.size(), index + CORRIDOR_CELLS_PER_TICK);
                while (index < end) session.buildFloorCorridor(build, cells.get(index++));
                phaseProgress = ratio(index, cells.size());
                if (index >= cells.size()) advance();
            }
            case 5 -> {
                if (traps == null) traps = session.beginFloorTraps(build);
                if (traps.tick()) {
                    reserved.addAll(traps.occupiedCells());
                    if (build.levelType().liquid().enabled() && !build.plan().rooms().isEmpty()) {
                        liquids.addAll(build.plan().liquidCells());
                    }
                    advance();
                } else phaseProgress = traps.progress();
            }
            case 6 -> {
                if (build.levelType().liquid().enabled() && index < build.plan().rooms().size()) {
                    liquids.addAll(session.roomLiquidCells(build, build.plan().rooms().get(index++), reserved));
                    phaseProgress = ratio(index, build.plan().rooms().size());
                } else {
                    // Freeze the complete set before rendering: touching puddles must merge, not grow divider walls.
                    session.setFloorLiquids(liquids);
                    cells = new ArrayList<>(liquids);
                    advance();
                }
            }
            case 7 -> {
                long deadline = System.nanoTime() + CONTENT_BUDGET_NANOS;
                int end = Math.min(cells.size(), index + CONTENT_CELLS_PER_TICK);
                while (index < end && System.nanoTime() < deadline) {
                    session.renderLiquidCell(cells.get(index++), build.levelType(), liquids);
                }
                phaseProgress = ratio(index, cells.size());
                if (index >= cells.size()) {
                    vegetationReserved = new HashSet<>(reserved);
                    vegetationReserved.addAll(liquids);
                    cells = List.of();
                    advance();
                }
            }
            case 8 -> roomVegetation();
            case 9 -> {
                renderVegetationBatch(.5);
                phaseProgress = ratio(index, cells.size());
                if (index >= cells.size()) advance();
            }
            case 10 -> {
                // Sculk preserves the original trap-only reservation rule; breakables may still occupy liquid cells.
                if (sculk == null) sculk = session.beginFloorSculk(build, reserved);
                if (sculk.tick()) {
                    reserved.addAll(vegetation);
                    advance();
                } else phaseProgress = sculk.progress();
            }
            case 11 -> {
                reserved.addAll(session.placeResearchCrate(build, reserved));
                distilleryRooms = new ArrayList<>(build.plan().rooms());
                if (!distilleryRooms.isEmpty()) distilleryRooms.removeFirst();
                Collections.shuffle(distilleryRooms, build.random());
                advance();
            }
            case 12 -> {
                if ("exploration".equalsIgnoreCase(build.floorDefinition().kind())
                        && distilleriesPlaced < build.floorDefinition().blueprintDistilleries()
                        && index < distilleryRooms.size()) {
                    Set<Cell> added = session.placeFloorDistilleryRoom(build, reserved, distilleryRooms.get(index++));
                    reserved.addAll(added);
                    if (!added.isEmpty()) distilleriesPlaced++;
                    phaseProgress = ratio(index, distilleryRooms.size());
                } else advance();
            }
            case 13 -> {
                if ("library".equalsIgnoreCase(build.levelType().id()) && index < build.plan().rooms().size()) {
                    if (build.plan().rooms().get(index).ventOnly()) {
                        reserved.addAll(session.placeFloorLibraryVent(build, reserved, index));
                    }
                    index++;
                    phaseProgress = ratio(index, build.plan().rooms().size());
                } else {
                    rareRoom = session.rareBreakableRoomIndex(build.plan(), reserved, build.random());
                    advance();
                }
            }
            case 14 -> {
                if (index < build.plan().rooms().size()) {
                    session.placeGeneratedRoomContents(build.plan().rooms().get(index), build.random(), floor,
                            index, build.floorDefinition(), build.levelType(), reserved, index == rareRoom);
                    index++;
                    phaseProgress = ratio(index, build.plan().rooms().size());
                } else advance();
            }
            default -> {
                if (ticksElapsed >= MIN_TRANSITION_TICKS) {
                    session.finishFloorBuild(build);
                    return;
                }
            }
        }
        double end = stage + 1 < PROGRESS.length ? PROGRESS[stage + 1] : 1.0;
        session.updateFloorBuildProgress(PROGRESS[stage] + (end - PROGRESS[stage]) * phaseProgress, PHASES[stage]);
    }

    private void roomVegetation() {
        if (vegetationRoom >= build.plan().rooms().size()) {
            cells = session.vegetationCorridorCells(build);
            advance();
            return;
        }
        if (cells.isEmpty()) cells = session.floorRoomVegetationCells(build.plan().rooms().get(vegetationRoom));
        renderVegetationBatch(1.0);
        phaseProgress = (vegetationRoom + ratio(index, cells.size())) / Math.max(1, build.plan().rooms().size());
        if (index >= cells.size()) {
            vegetationRoom++;
            index = 0;
            cells = List.of();
        }
    }

    private void renderVegetationBatch(double chance) {
        long deadline = System.nanoTime() + CONTENT_BUDGET_NANOS;
        int end = Math.min(cells.size(), index + CONTENT_CELLS_PER_TICK);
        while (index < end && System.nanoTime() < deadline) {
            int batchEnd = Math.min(end, index + 16);
            session.placeVegetationInCells(build, new ArrayList<>(cells.subList(index, batchEnd)),
                    vegetationReserved, vegetation, chance);
            index = batchEnd;
        }
    }

    private void advance() {
        stage++;
        index = 0;
        phaseProgress = 0.0;
    }

    private static double ratio(int done, int total) {
        return total <= 0 ? 1.0 : Math.min(1.0, (double) done / total);
    }

    static final class LoadingProgress {
        private final World world;
        private final Set<UUID> participants;
        private double progress = -1.0;
        private String phase = "Planning";

        LoadingProgress(World world, Set<UUID> participants) {
            this.world = world;
            this.participants = participants;
        }

        void update(double value, String phase) {
            progress = Math.max(progress, Math.max(0.0, Math.min(1.0, value)));
            this.phase = phase;
            send();
        }

        void send() {
            if (progress < 0.0) return;
            int percent = (int) Math.floor(progress * 100.0);
            int filled = percent / 5;
            Component bar = Component.text("Loading " + phase + " [", NamedTextColor.GRAY)
                    .append(Component.text("|".repeat(filled), NamedTextColor.GREEN))
                    .append(Component.text("|".repeat(20 - filled), NamedTextColor.DARK_GRAY))
                    .append(Component.text("] " + percent + "%", NamedTextColor.GRAY));
            send(bar);
        }

        void clear() {
            if (progress < 0.0) return;
            progress = -1.0;
            phase = "Planning";
            send(Component.empty());
        }

        private void send(Component bar) {
            for (UUID playerId : participants) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.getWorld().equals(world)) player.sendActionBar(bar);
            }
        }
    }
}
