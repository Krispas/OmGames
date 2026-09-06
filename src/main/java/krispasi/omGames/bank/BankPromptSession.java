package krispasi.omGames.bank;

public final class BankPromptSession {
    public enum Mode {
        CREATE_ACCOUNT
    }

    private final Mode mode;

    private BankPromptSession(Mode mode) {
        this.mode = mode;
    }

    public static BankPromptSession createAccount() {
        return new BankPromptSession(Mode.CREATE_ACCOUNT);
    }

    public Mode mode() {
        return mode;
    }
}
