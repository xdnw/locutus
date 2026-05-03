package link.locutus.discord.sim.planners.providers;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.TimeUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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

        Int2ObjectOpenHashMap<DBWar> warsById = new Int2ObjectOpenHashMap<>(Math.max(16, wars.size() * 2));
        Int2ObjectOpenHashMap<OutcomeAccumulator> accumulators = new Int2ObjectOpenHashMap<>(Math.max(16, nationIds.size() * 2));
        for (DBWar war : wars) {
            warsById.put(war.warId, war);
            if (nationIds.contains(war.getAttacker_id())) {
                accumulators.computeIfAbsent(war.getAttacker_id(), ignored -> new OutcomeAccumulator()).declaredWarCount++;
            }
        }

        Int2LongOpenHashMap earliestActionTurnByWarId = new Int2LongOpenHashMap(Math.max(16, wars.size() * 2));
        earliestActionTurnByWarId.defaultReturnValue(Long.MIN_VALUE);
        if (attacks != null) {
            for (AbstractCursor attack : attacks) {
                if (!isObservedFirstAction(attack)) {
                    continue;
                }
                DBWar war = warsById.get(attack.getWar_id());
                if (war == null || !nationIds.contains(war.getAttacker_id()) || attack.getAttacker_id() != war.getAttacker_id()) {
                    continue;
                }
                int warId = attack.getWar_id();
                long attackTurn = TimeUtil.getTurn(attack.getDate());
                long current = earliestActionTurnByWarId.get(warId);
                if (current == Long.MIN_VALUE || attackTurn < current) {
                    earliestActionTurnByWarId.put(warId, attackTurn);
                }
            }
        }

        List<NationRow> rows = new ArrayList<>(nationIds.size());
        for (int nationId : nationIds) {
            OutcomeAccumulator accumulator = accumulators.computeIfAbsent(nationId, ignored -> new OutcomeAccumulator());
            for (DBWar war : wars) {
                if (war.getAttacker_id() != nationId) {
                    continue;
                }
                long firstActionTurn = earliestActionTurnByWarId.get(war.warId);
                if (firstActionTurn == Long.MIN_VALUE) {
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