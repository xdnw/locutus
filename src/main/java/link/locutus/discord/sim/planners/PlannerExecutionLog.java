package link.locutus.discord.sim.planners;

import java.util.List;

record PlannerExecutionLog(List<Turn> turns) {
    PlannerExecutionLog {
        turns = turns == null || turns.isEmpty() ? List.of() : List.copyOf(turns);
    }

    record Turn(
            int turn,
            List<DeclaredWar> preOpeningDeclarations,
            List<DeclaredWar> autonomousDeclarations,
            List<ConcludedWar> concludedWars
    ) {
        Turn {
            preOpeningDeclarations = immutable(preOpeningDeclarations);
            autonomousDeclarations = immutable(autonomousDeclarations);
            concludedWars = immutable(concludedWars);
        }

        private static <T> List<T> immutable(List<T> values) {
            return values == null || values.isEmpty() ? List.of() : List.copyOf(values);
        }
    }

    record DeclaredWar(
            int warId,
            int declarerNationId,
            int targetNationId,
            int warTypeOrdinal
    ) {
    }

    record ConcludedWar(
            int warId,
            int endStatusOrdinal
    ) {
    }
}