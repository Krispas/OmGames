package krispasi.omGames.bank.fortuna;

import java.time.LocalDateTime;

public record FortunaOddsPoint(
        LocalDateTime changedAt,
        double homeOdds,
        double drawOdds,
        double awayOdds
) {
}
