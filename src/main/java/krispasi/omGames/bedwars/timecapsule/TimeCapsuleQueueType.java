package krispasi.omGames.bedwars.timecapsule;

import java.util.Locale;

public enum TimeCapsuleQueueType {
    NORMAL,
    TEST;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
