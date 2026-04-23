package link.locutus.discord.sim;

/**
 * Tracks global turn progression and computes per-nation day-phase offsets.
 */
public final class SimClock {
    private final int simStartHourUtc;
    private int turn;

    public SimClock() {
        this(0);
    }

    public SimClock(int simStartHourUtc) {
        if (simStartHourUtc < 0 || simStartHourUtc > 23) {
            throw new IllegalArgumentException("simStartHourUtc must be in [0,23]");
        }
        this.simStartHourUtc = simStartHourUtc;
    }

    public int currentTurn() {
        return turn;
    }

    public int currentHourUtc() {
        return currentHourUtcForTurn(simStartHourUtc, turn);
    }

    public int dayPhaseForResetHour(byte resetHourUtc) {
        return dayPhaseForTurn(simStartHourUtc, turn, resetHourUtc);
    }

    public static int dayPhaseForTurn(int simStartHourUtc, int turn, byte resetHourUtc) {
        if (simStartHourUtc < 0 || simStartHourUtc > 23) {
            throw new IllegalArgumentException("simStartHourUtc must be in [0,23]");
        }
        if (turn < 0) {
            throw new IllegalArgumentException("turn must be >= 0");
        }
        if (resetHourUtc < 0 || resetHourUtc > 23) {
            throw new IllegalArgumentException("resetHourUtc must be in [0,23]");
        }
        int offsetHours = Math.floorMod(currentHourUtcForTurn(simStartHourUtc, turn) - resetHourUtc, 24);
        return offsetHours / 2;
    }

    private static int currentHourUtcForTurn(int simStartHourUtc, int turn) {
        return Math.floorMod(simStartHourUtc + turn * 2, 24);
    }

    public void advanceTurn() {
        turn++;
    }

    /** Returns a deep copy of this clock at its current state. */
    public SimClock deepCopy() {
        SimClock copy = new SimClock(simStartHourUtc);
        copy.turn = this.turn;
        return copy;
    }
}
