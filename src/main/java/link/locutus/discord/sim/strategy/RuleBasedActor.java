package link.locutus.discord.sim.strategy;

import link.locutus.discord.sim.Actor;
import link.locutus.discord.sim.DecisionContext;
import link.locutus.discord.sim.SimNation;
import link.locutus.discord.sim.SimWorld;
import link.locutus.discord.sim.actions.SimAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Rule-based actor driven by a menu of strategy primitives.
 * 
 * Each pass, the actor:
 * 1. Filters primitives by isActive()
 * 2. Asks each to nominate candidates
 * 3. Scores the best candidate from each primitive
 * 4. Picks the argmax scorer
 * 5. Returns its action sequence
 * 
 * Primitives are composable and not exclusive —
 * a nation's mix can change across turns.
 */
public class RuleBasedActor implements Actor {
    private final List<StrategyPrimitive> primitives = new ArrayList<>();

    public RuleBasedActor() {
    }

    /**
     * Register a strategy primitive.
     */
    public void addPrimitive(StrategyPrimitive primitive) {
        this.primitives.add(Objects.requireNonNull(primitive, "primitive"));
    }

    @Override
    public List<SimAction> decide(SimWorld world, SimNation self, DecisionContext ctx) {
        double bestScore = Double.NEGATIVE_INFINITY;
        List<List<SimAction>> bestCandidates = null;

        for (StrategyPrimitive primitive : primitives) {
            if (!primitive.isActive(world, self, ctx)) {
                continue;
            }

            List<List<SimAction>> candidates = primitive.nominate(world, self, ctx);
            if (candidates == null || candidates.isEmpty()) {
                continue;
            }

            double score = primitive.expectedDelta(world, self, ctx);
            if (score > bestScore) {
                bestScore = score;
                bestCandidates = candidates;
            }
        }

        if (bestCandidates == null || bestCandidates.isEmpty()) {
            return new ArrayList<>();
        }

        // Return the first candidate from the best primitive.
        // In future, this could be refined to consider all candidates,
        // but for MVP we pick the first.
        return new ArrayList<>(bestCandidates.get(0));
    }
}
