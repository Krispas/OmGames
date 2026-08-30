package krispasi.omGames.bank.fortuna;

import java.time.LocalDateTime;

final class FortunaPromptSession {
    enum Mode {
        NEW_MATCH,
        ODDS_UPDATE
    }

    enum Step {
        HOME_NAME,
        AWAY_NAME,
        SCHEDULED_AT,
        HOME_ODDS,
        DRAW_ODDS,
        AWAY_ODDS,
        ODDS_LINE
    }

    private final Mode mode;
    private final int matchId;
    private Step step;
    private String homeName;
    private String awayName;
    private LocalDateTime scheduledAt;
    private double homeOdds;
    private double drawOdds;

    private FortunaPromptSession(Mode mode, int matchId, Step step) {
        this.mode = mode;
        this.matchId = matchId;
        this.step = step;
    }

    static FortunaPromptSession newMatch() {
        return new FortunaPromptSession(Mode.NEW_MATCH, -1, Step.HOME_NAME);
    }

    static FortunaPromptSession oddsUpdate(int matchId) {
        return new FortunaPromptSession(Mode.ODDS_UPDATE, matchId, Step.ODDS_LINE);
    }

    Mode mode() {
        return mode;
    }

    int matchId() {
        return matchId;
    }

    Step step() {
        return step;
    }

    void setStep(Step step) {
        this.step = step;
    }

    String homeName() {
        return homeName;
    }

    void setHomeName(String homeName) {
        this.homeName = homeName;
    }

    String awayName() {
        return awayName;
    }

    void setAwayName(String awayName) {
        this.awayName = awayName;
    }

    LocalDateTime scheduledAt() {
        return scheduledAt;
    }

    void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    double homeOdds() {
        return homeOdds;
    }

    void setHomeOdds(double homeOdds) {
        this.homeOdds = homeOdds;
    }

    double drawOdds() {
        return drawOdds;
    }

    void setDrawOdds(double drawOdds) {
        this.drawOdds = drawOdds;
    }
}
