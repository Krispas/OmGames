package krispasi.omGames.bank;

public final class BankPromptSession {
    public enum Mode {
        CREATE_NON_PLAYER_ACCOUNT
    }

    private final Mode mode;

    private BankPromptSession(Mode mode) {
        this.mode = mode;
    }

    public static BankPromptSession createNonPlayerAccount() {
        return new BankPromptSession(Mode.CREATE_NON_PLAYER_ACCOUNT);
    }

    public Mode mode() {
        return mode;
    }
}
