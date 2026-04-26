package link.locutus.discord.web.commands.binding.value_types;

import java.util.List;

/**
 * Response for the conflict start detection endpoint.
 *
 * <p>The frontend works in turns rather than timestamps here so it can render
 * and compare nearby candidates without any extra conversion roundtrip.
 */
public class WebConflictStartDetection {
    public String conflictId;
    /** Current conflict start turn before any apply step. */
    public long currentStartTurn;
    /** Search window floor in turns. */
    public long searchedFromTurn;
    /** Applied turn, or null when the response is preview-only. */
    public Long appliedStartTurn;
    public boolean applied;
    /** Opaque token that binds the apply step to the previewed candidate set. */
    public String token;
    public List<Candidate> candidates;

    public WebConflictStartDetection() {
    }

    public WebConflictStartDetection(String conflictId, long currentStartTurn, long searchedFromTurn,
            Long appliedStartTurn, boolean applied, String token, List<Candidate> candidates) {
        this.conflictId = conflictId;
        this.currentStartTurn = currentStartTurn;
        this.searchedFromTurn = searchedFromTurn;
        this.appliedStartTurn = appliedStartTurn;
        this.applied = applied;
        this.token = token;
        this.candidates = candidates;
    }

    public static class Candidate {
        public long turn;
        public int coal1Nations;
        public int coal2Nations;
        public int coal1Declarations;
        public int coal2Declarations;
        public int totalDeclarations;
        public List<AllianceSummary> coal1Alliances;
        public List<AllianceSummary> coal2Alliances;

        public Candidate() {
        }

        public Candidate(long turn, int coal1Nations, int coal2Nations,
                int coal1Declarations, int coal2Declarations, int totalDeclarations,
                List<AllianceSummary> coal1Alliances, List<AllianceSummary> coal2Alliances) {
            this.turn = turn;
            this.coal1Nations = coal1Nations;
            this.coal2Nations = coal2Nations;
            this.coal1Declarations = coal1Declarations;
            this.coal2Declarations = coal2Declarations;
            this.totalDeclarations = totalDeclarations;
            this.coal1Alliances = coal1Alliances;
            this.coal2Alliances = coal2Alliances;
        }
    }

    public static class AllianceSummary {
        public int allianceId;
        public String allianceName;
        public int nations;
        public int declarations;

        public AllianceSummary() {
        }

        public AllianceSummary(int allianceId, String allianceName, int nations, int declarations) {
            this.allianceId = allianceId;
            this.allianceName = allianceName;
            this.nations = nations;
            this.declarations = declarations;
        }
    }
}
