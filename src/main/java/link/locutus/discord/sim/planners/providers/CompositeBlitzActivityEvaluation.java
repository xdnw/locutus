package link.locutus.discord.sim.planners.providers;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.TimeUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record CompositeBlitzActivityEvaluation(List<NationRow> rows) {
    public CompositeBlitzActivityEvaluation {
        rows = List.copyOf(rows);
    }

    public NationRow rowFor(int nationId) {
        return rows.stream()
                .filter(row -> row.nationId() == nationId)
                .findFirst()
                .orElse(null);
    }

    public record NationRow(
            int nationId,
            CompositeBlitzActivityModel.NationSignals signals,
            int declaredWarCount,
            int warsWithObservedFirstAction,
            Double observedParticipationRate,
            Double observedFirstActionScore
    ) {
    }

    static CompositeBlitzActivityEvaluation evaluateHistory(
            Set<Integer> nationIds,
            Int2IntOpenHashMap allianceByNationId,
            Map<Integer, Set<Long>> loginTurnsByNation,
            Collection<DBWar> wars,
            Collection<? extends AbstractCursor> attacks,
            long lookbackStartTurn,
            long lookbackEndTurn,
            int currentWeekTurn,
            long tieBreakSeed,
            CompositeBlitzActivityModel.Options options
    ) {
        Objects.requireNonNull(nationIds, "nationIds");
        Objects.requireNonNull(allianceByNationId, "allianceByNationId");
        Objects.requireNonNull(loginTurnsByNation, "loginTurnsByNation");
        Objects.requireNonNull(wars, "wars");
        Objects.requireNonNull(options, "options");

        CompositeBlitzActivityModel model = CompositeBlitzActivityModel.fromHistoryWithAttacks(
                nationIds,
                allianceByNationId,
                loginTurnsByNation,
                wars,
                attacks == null ? List.of() : attacks,
                lookbackStartTurn,
                lookbackEndTurn,
                currentWeekTurn,
                tieBreakSeed,
                options
        );

        Map<Integer, DBWar> warsById = new LinkedHashMap<>();
        Map<Integer, OutcomeAccumulator> accumulators = new LinkedHashMap<>();
        for (DBWar war : wars) {
            warsById.put(war.warId, war);
            if (nationIds.contains(war.getAttacker_id())) {
                accumulators.computeIfAbsent(war.getAttacker_id(), ignored -> new OutcomeAccumulator()).declaredWarCount++;
            }
        }

        Map<Integer, Long> earliestActionTurnByWarId = new LinkedHashMap<>();
        if (attacks != null) {
            for (AbstractCursor attack : attacks) {
                if (!isObservedFirstAction(attack)) {
                    continue;
                }
                DBWar war = warsById.get(attack.getWar_id());
                if (war == null || !nationIds.contains(war.getAttacker_id()) || attack.getAttacker_id() != war.getAttacker_id()) {
                    continue;
                }
                earliestActionTurnByWarId.merge(attack.getWar_id(), TimeUtil.getTurn(attack.getDate()), Math::min);
            }
        }

        List<NationRow> rows = new ArrayList<>(nationIds.size());
        for (int nationId : nationIds) {
            OutcomeAccumulator accumulator = accumulators.computeIfAbsent(nationId, ignored -> new OutcomeAccumulator());
            for (DBWar war : wars) {
                if (war.getAttacker_id() != nationId) {
                    continue;
                }
                Long firstActionTurn = earliestActionTurnByWarId.get(war.warId);
                if (firstActionTurn == null) {
                    continue;
                }
                accumulator.warsWithObservedFirstAction++;
                long startTurn = TimeUtil.getTurn(war.getDate());
                long delayTurns = Math.max(0L, firstActionTurn - startTurn);
                double score = Math.max(0.0d, 1.0d - (Math.min(delayTurns, CompositeBlitzActivityModel.RESPONSE_LOOKAHEAD_TURNS)
                        / (double) CompositeBlitzActivityModel.RESPONSE_LOOKAHEAD_TURNS));
                accumulator.firstActionScoreSum += score;
            }
            Double participationRate = accumulator.declaredWarCount == 0
                    ? null
                    : accumulator.warsWithObservedFirstAction / (double) accumulator.declaredWarCount;
            Double firstActionScore = accumulator.declaredWarCount == 0
                    ? null
                    : accumulator.firstActionScoreSum / accumulator.declaredWarCount;
            rows.add(new NationRow(
                    nationId,
                    model.signalsFor(nationId),
                    accumulator.declaredWarCount,
                    accumulator.warsWithObservedFirstAction,
                    participationRate,
                    firstActionScore
            ));
        }
        rows.sort(Comparator.comparingInt(NationRow::nationId));
        return new CompositeBlitzActivityEvaluation(rows);
    }

    private static boolean isObservedFirstAction(AbstractCursor attack) {
        AttackType attackType = attack.getAttack_type();
        return attackType != AttackType.PEACE && attackType != AttackType.VICTORY;
    }

    private static final class OutcomeAccumulator {
        private int declaredWarCount;
        private int warsWithObservedFirstAction;
        private double firstActionScoreSum;
    }
}