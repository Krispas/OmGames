package krispasi.omGames.bank.fortuna;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class FortunaMatch {
    public static final DateTimeFormatter STORAGE_TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    public static final DateTimeFormatter DISPLAY_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM HH:mm", Locale.ROOT);

    private final int id;
    private final LocalDateTime createdAt;
    private final List<FortunaOddsPoint> oddsHistory = new ArrayList<>();
    private String homeName;
    private String awayName;
    private LocalDateTime scheduledAt;
    private double homeOdds;
    private double drawOdds;
    private double awayOdds;
    private FortunaMatchStatus status = FortunaMatchStatus.UPCOMING;
    private FortunaOutcome result;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public FortunaMatch(int id,
                     String homeName,
                     String awayName,
                     LocalDateTime scheduledAt,
                     double homeOdds,
                     double drawOdds,
                     double awayOdds,
                     LocalDateTime createdAt) {
        this.id = id;
        this.homeName = cleanName(homeName);
        this.awayName = cleanName(awayName);
        this.scheduledAt = scheduledAt == null ? LocalDateTime.now() : scheduledAt;
        this.homeOdds = homeOdds;
        this.drawOdds = drawOdds;
        this.awayOdds = awayOdds;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }

    public int getId() {
        return id;
    }

    public String getHomeName() {
        return homeName;
    }

    public String getAwayName() {
        return awayName;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public double getHomeOdds() {
        return homeOdds;
    }

    public double getDrawOdds() {
        return drawOdds;
    }

    public double getAwayOdds() {
        return awayOdds;
    }

    public FortunaMatchStatus getStatus() {
        return status;
    }

    public FortunaOutcome getResult() {
        return result;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public List<FortunaOddsPoint> getOddsHistory() {
        return List.copyOf(oddsHistory);
    }

    public void setStatus(FortunaMatchStatus status) {
        this.status = status == null ? FortunaMatchStatus.UPCOMING : status;
    }

    public void setResult(FortunaOutcome result) {
        this.result = result;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public void setOdds(double homeOdds, double drawOdds, double awayOdds, LocalDateTime changedAt) {
        this.homeOdds = homeOdds;
        this.drawOdds = drawOdds;
        this.awayOdds = awayOdds;
        addOddsPoint(new FortunaOddsPoint(
                changedAt == null ? LocalDateTime.now() : changedAt,
                homeOdds,
                drawOdds,
                awayOdds
        ));
    }

    public void addOddsPoint(FortunaOddsPoint point) {
        if (point == null) {
            return;
        }
        oddsHistory.add(point);
        oddsHistory.sort(Comparator.comparing(FortunaOddsPoint::changedAt));
    }

    public String label() {
        return homeName + " vs " + awayName;
    }

    public String scheduledLabel() {
        return DISPLAY_TIME_FORMAT.format(scheduledAt);
    }

    public String resultLabel() {
        if (result == null) {
            return "-";
        }
        return switch (result) {
            case HOME -> homeName;
            case DRAW -> "Draw";
            case AWAY -> awayName;
        };
    }

    private String cleanName(String name) {
        if (name == null || name.isBlank()) {
            return "TBD";
        }
        return name.trim();
    }
}
