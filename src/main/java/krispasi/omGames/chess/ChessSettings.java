package krispasi.omGames.chess;

public final class ChessSettings {
    private boolean doMovementCheck = true;
    private boolean visualizeMovementCheck = false;
    private boolean doEndgameChecks = true;
    private boolean allowUndo = false;

    public boolean doMovementCheck() {
        return doMovementCheck;
    }

    public void setDoMovementCheck(boolean doMovementCheck) {
        this.doMovementCheck = doMovementCheck;
    }

    public boolean visualizeMovementCheck() {
        return visualizeMovementCheck;
    }

    public void setVisualizeMovementCheck(boolean visualizeMovementCheck) {
        this.visualizeMovementCheck = visualizeMovementCheck;
    }

    public boolean doEndgameChecks() {
        return doEndgameChecks;
    }

    public void setDoEndgameChecks(boolean doEndgameChecks) {
        this.doEndgameChecks = doEndgameChecks;
    }

    public boolean allowUndo() {
        return allowUndo;
    }

    public void setAllowUndo(boolean allowUndo) {
        this.allowUndo = allowUndo;
    }

    public ChessSettings copy() {
        ChessSettings copy = new ChessSettings();
        copy.doMovementCheck = doMovementCheck;
        copy.visualizeMovementCheck = visualizeMovementCheck;
        copy.doEndgameChecks = doEndgameChecks;
        copy.allowUndo = allowUndo;
        return copy;
    }
}
