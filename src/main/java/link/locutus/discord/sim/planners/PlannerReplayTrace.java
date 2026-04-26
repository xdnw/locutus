package link.locutus.discord.sim.planners;

public record PlannerReplayTrace(
        Frame initialFrame,
        Delta[] deltas
) {
    public record Frame(
            int currentTurn,
            NationState[] nations,
            WarState[] wars
    ) {
    }

    public record Delta(
            int turn,
            NationState[] nations,
            WarState[] wars,
            DeclaredWar[] declaredWars,
            ConcludedWar[] concludedWars
    ) {
    }

    public record NationState(
            int nationId,
            int[] unitsByMilitaryUnitOrdinal,
            double[] cityInfra,
            double score,
            int beigeTurns,
            double[] resources
    ) {
    }

    public record WarState(
            int declarerNationId,
            int targetNationId,
            int warTypeOrdinal,
            int startTurn,
            int statusOrdinal,
            int attackerMaps,
            int defenderMaps,
            int attackerResistance,
            int defenderResistance,
            int groundControlOwnerOrdinal,
            int airSuperiorityOwnerOrdinal,
            int blockadeOwnerOrdinal,
            boolean attackerFortified,
            boolean defenderFortified
    ) {
        public long pairKey() {
            return PlannerLocalConflict.pairKey(declarerNationId, targetNationId);
        }
    }

    public record DeclaredWar(
            int declarerNationId,
            int targetNationId,
            int warTypeOrdinal,
            int startTurn
    ) {
        public long pairKey() {
            return PlannerLocalConflict.pairKey(declarerNationId, targetNationId);
        }
    }

    public record ConcludedWar(
            int declarerNationId,
            int targetNationId,
            int endStatusOrdinal
    ) {
        public long pairKey() {
            return PlannerLocalConflict.pairKey(declarerNationId, targetNationId);
        }
    }
}