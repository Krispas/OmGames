package krispasi.omGames.hallsofcarnage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

final class HallsSidebar {
    private static final String OBJECTIVE_ID = "hoc";

    private final Map<UUID, Scoreboard> previousScoreboards = new HashMap<>();
    private final Map<UUID, Scoreboard> activeScoreboards = new HashMap<>();
    private final Map<UUID, List<String>> previousLines = new HashMap<>();

    void update(Player player, HallsSession.SidebarState state) {
        if (player == null || state == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        Scoreboard scoreboard = activeScoreboards.get(playerId);
        if (scoreboard == null) {
            previousScoreboards.putIfAbsent(playerId, player.getScoreboard());
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            activeScoreboards.put(playerId, scoreboard);
        }
        Objective objective = ensureObjective(scoreboard);
        List<String> oldLines = previousLines.remove(playerId);
        if (oldLines != null) {
            for (String line : oldLines) {
                scoreboard.resetScores(line);
            }
        }
        List<String> lines = buildLines(state);
        int score = lines.size();
        for (String line : lines) {
            objective.getScore(line).setScore(score--);
        }
        previousLines.put(playerId, lines);
        player.setScoreboard(scoreboard);
    }

    void restore(UUID playerId) {
        if (playerId == null) {
            return;
        }
        Scoreboard active = activeScoreboards.remove(playerId);
        List<String> lines = previousLines.remove(playerId);
        if (active != null && lines != null) {
            for (String line : lines) {
                active.resetScores(line);
            }
        }
        Scoreboard previous = previousScoreboards.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.setScoreboard(previous != null ? previous : Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    void clear() {
        for (UUID playerId : List.copyOf(activeScoreboards.keySet())) {
            restore(playerId);
        }
    }

    private Objective ensureObjective(Scoreboard scoreboard) {
        Objective objective = scoreboard.getObjective(OBJECTIVE_ID);
        if (objective == null) {
            objective = scoreboard.registerNewObjective(OBJECTIVE_ID, "dummy",
                    Component.text("Halls", NamedTextColor.DARK_RED));
        }
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        return objective;
    }

    private List<String> buildLines(HallsSession.SidebarState state) {
        List<String> lines = new ArrayList<>();
        lines.add("Floor: " + state.floor());
        lines.add("Time: " + state.elapsed());
        lines.add("Wood: " + state.woodScrap() + "  Iron: " + state.ironScrap());
        lines.add("Diamond: " + state.diamondScrap() + "  Redstone: " + state.redstoneScrap());
        lines.add(state.campFloor() ? "Keys: " + state.keys() + "  Bank: " + state.campBank()
                : "Coins: " + state.coins() + "/" + state.coinQuota());
        lines.add("Lives: " + state.lives());
        lines.add("Sculk: " + state.sculkPercent() + "%");
        lines.add("Research Crate: " + (state.researchCrateDeposited() ? "Yes" : "No"));
        lines.add("Distiller: " + (state.blueprintDistillerCollected() ? "Yes" : "No"));
        if (!state.modifiers().isBlank()) {
            lines.add("Mods: " + truncate(state.modifiers(), 30));
        }
        for (int i = 0; i < lines.size(); i++) {
            lines.set(i, uniqueLine(lines.get(i), i));
        }
        return lines;
    }

    private String uniqueLine(String line, int index) {
        String trimmed = line.length() > 38 ? line.substring(0, 38) : line;
        return trimmed + " ".repeat(index);
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + ".";
    }
}
