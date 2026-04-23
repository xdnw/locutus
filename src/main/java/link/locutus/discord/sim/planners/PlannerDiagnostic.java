package link.locutus.discord.sim.planners;

import java.util.Objects;

/**
 * Structured planner diagnostic emitted on plan outputs.
 */
public record PlannerDiagnostic(
        Code code,
        Severity severity,
        NationRole nationRole,
        int nationId
) {
    public PlannerDiagnostic {
        code = Objects.requireNonNull(code, "code");
        severity = Objects.requireNonNull(severity, "severity");
        nationRole = Objects.requireNonNull(nationRole, "nationRole");
        if (nationId <= 0) {
            throw new IllegalArgumentException("nationId must be > 0");
        }
    }

    public static PlannerDiagnostic resetHourFallback(NationRole nationRole, int nationId) {
        return new PlannerDiagnostic(Code.RESET_HOUR_FALLBACK, Severity.WARNING, nationRole, nationId);
    }

    public String message() {
        return switch (code) {
            case RESET_HOUR_FALLBACK -> nationRole.displayName() + " " + nationId
                    + " is using a fallback reset hour - DoubleBuyWindow accuracy may be reduced.";
        };
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