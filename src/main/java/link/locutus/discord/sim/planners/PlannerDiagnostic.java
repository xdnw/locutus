package link.locutus.discord.sim.planners;

/**
 * Structured planner diagnostic emitted on plan outputs.
 */
public record PlannerDiagnostic(
        int codeOrdinal,
        int severityOrdinal,
        int nationRoleOrdinal,
        int nationId
) {
    public PlannerDiagnostic(Code code, Severity severity, NationRole nationRole, int nationId) {
        this(code.ordinal(), severity.ordinal(), nationRole.ordinal(), nationId);
    }

    public PlannerDiagnostic {
        codeAt(codeOrdinal);
        severityAt(severityOrdinal);
        nationRoleAt(nationRoleOrdinal);
        if (nationId <= 0) {
            throw new IllegalArgumentException("nationId must be > 0");
        }
    }

    public static PlannerDiagnostic resetHourFallback(NationRole nationRole, int nationId) {
        return new PlannerDiagnostic(Code.RESET_HOUR_FALLBACK, Severity.WARNING, nationRole, nationId);
    }

    public Code code() {
        return codeAt(codeOrdinal);
    }

    public Severity severity() {
        return severityAt(severityOrdinal);
    }

    public NationRole nationRole() {
        return nationRoleAt(nationRoleOrdinal);
    }

    public String message() {
        return switch (code()) {
            case RESET_HOUR_FALLBACK -> nationRole().displayName() + " " + nationId
                    + " is using a fallback reset hour - DoubleBuyWindow accuracy may be reduced.";
        };
    }

    private static Code codeAt(int ordinal) {
        Code[] values = Code.values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("codeOrdinal is out of range: " + ordinal);
        }
        return values[ordinal];
    }

    private static Severity severityAt(int ordinal) {
        Severity[] values = Severity.values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("severityOrdinal is out of range: " + ordinal);
        }
        return values[ordinal];
    }

    private static NationRole nationRoleAt(int ordinal) {
        NationRole[] values = NationRole.values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("nationRoleOrdinal is out of range: " + ordinal);
        }
        return values[ordinal];
    }

    public enum Code {
        RESET_HOUR_FALLBACK
    }

    public enum Severity {
        WARNING
    }

    public enum NationRole {
        ATTACKER,
        DEFENDER;

        public String displayName() {
            return switch (this) {
                case ATTACKER -> "Attacker";
                case DEFENDER -> "Defender";
            };
        }
    }
}