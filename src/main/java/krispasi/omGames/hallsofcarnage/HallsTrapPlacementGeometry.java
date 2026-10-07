package krispasi.omGames.hallsofcarnage;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import krispasi.omGames.hallsofcarnage.HallsExplorationGenerator.Cell;

final class HallsTrapPlacementGeometry {
    private HallsTrapPlacementGeometry() { }

    static boolean isNearOccupied(Cell cell, Set<Cell> occupied) {
        for (int dx = -4; dx <= 4; dx++) {
            int reach = 4 - Math.abs(dx);
            for (int dz = -reach; dz <= reach; dz++) {
                if (occupied.contains(new Cell(cell.x() + dx, cell.z() + dz))) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean roomEntrancesReachable(Set<Cell> roomCells, List<Cell> entrances,
                                         Set<Cell> pits, Set<Cell> bridges) {
        Set<Cell> target = new HashSet<>(roomCells);
        for (Cell pit : pits) {
            if (!bridges.contains(pit)) {
                target.remove(pit);
            }
        }
        if (target.isEmpty() || !target.containsAll(entrances)) {
            return false;
        }
        Cell start = entrances.isEmpty() ? target.iterator().next() : entrances.get(0);
        Set<Cell> visited = new HashSet<>();
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            Cell cell = queue.remove();
            for (int direction = 0; direction < 4; direction++) {
                Cell next = neighbor(cell, direction);
                if (target.contains(next) && visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return visited.size() == target.size();
    }

    private static Cell neighbor(Cell cell, int direction) {
        return switch (direction) {
            case 0 -> new Cell(cell.x(), cell.z() - 1);
            case 1 -> new Cell(cell.x(), cell.z() + 1);
            case 2 -> new Cell(cell.x() + 1, cell.z());
            default -> new Cell(cell.x() - 1, cell.z());
        };
    }

    // Main-thread, placement-job-local scratch. Never update this graph for accepted traps.
    static final class FloorConnectivity {
        private final Map<Cell, Integer> indices = new HashMap<>();
        private final int[] adjacency;
        private final int[] starts;
        private final int[] queue;
        private final int[] visited;
        private final int[] blocked;
        private int stamp;

        FloorConnectivity(Set<Cell> walkable, List<Cell> preferredStarts) {
            int index = 0;
            for (Cell cell : walkable) {
                indices.put(cell, index++);
            }
            queue = new int[index];
            visited = new int[index];
            blocked = new int[index];
            adjacency = new int[index * 4];
            for (Map.Entry<Cell, Integer> entry : indices.entrySet()) {
                for (int direction = 0; direction < 4; direction++) {
                    adjacency[entry.getValue() * 4 + direction] =
                            indices.getOrDefault(neighbor(entry.getKey(), direction), -1);
                }
            }
            starts = new int[preferredStarts.size()];
            for (int i = 0; i < starts.length; i++) {
                starts[i] = indices.getOrDefault(preferredStarts.get(i), -1);
            }
        }

        boolean reachableWithout(Set<Cell> removed) {
            if (stamp == Integer.MAX_VALUE) {
                Arrays.fill(visited, 0);
                Arrays.fill(blocked, 0);
                stamp = 0;
            }
            stamp++;
            int targetSize = queue.length;
            for (Cell cell : removed) {
                Integer index = indices.get(cell);
                if (index != null && blocked[index] != stamp) {
                    blocked[index] = stamp;
                    targetSize--;
                }
            }
            int start = -1;
            for (int candidate : starts) {
                if (candidate >= 0 && blocked[candidate] != stamp) {
                    start = candidate;
                    break;
                }
            }
            if (start < 0) {
                return false;
            }
            int head = 0;
            int tail = 1;
            queue[0] = start;
            visited[start] = stamp;
            while (head < tail) {
                int offset = queue[head++] * 4;
                for (int direction = 0; direction < 4; direction++) {
                    int next = adjacency[offset + direction];
                    if (next >= 0 && blocked[next] != stamp && visited[next] != stamp) {
                        visited[next] = stamp;
                        queue[tail++] = next;
                    }
                }
            }
            return tail == targetSize;
        }
    }
}
