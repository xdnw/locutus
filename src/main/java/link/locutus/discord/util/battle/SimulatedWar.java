package link.locutus.discord.util.battle;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Consumer;
import java.util.function.Function;

public class SimulatedWar {
    private final SimulatedWarNode origin;
    private final Function<SimulatedWarNode, Double> valueFunction;
    private final Function<SimulatedWarNode, SimulatedWarNode.WarGoal> victoryFunction;

    private final PriorityQueue<SimulatedWarNode> queue;
    private SimulatedWarNode solution;

    public SimulatedWar(SimulatedWarNode origin, Function<SimulatedWarNode, Double> valueFunction, Function<SimulatedWarNode, SimulatedWarNode.WarGoal> victoryFunction) {
        this.origin = origin;
        this.valueFunction = valueFunction;
        this.victoryFunction = victoryFunction;
        this.queue = new PriorityQueue<>(Comparator.comparingDouble(o -> -o.getValue(valueFunction)));
        this.solution = null;
    }

    public SimulatedWarNode solve() {
        if (solution != null) {
            return solution;
        }

        queue.add(origin);

        Consumer<SimulatedWarNode> addQueue = queue::add;

        long start = System.currentTimeMillis();

        while (!queue.isEmpty()) {
            SimulatedWarNode next = queue.poll();

            SimulatedWarNode.WarGoal victory = victoryFunction.apply(next);
            if (victory == SimulatedWarNode.WarGoal.FAILURE) {
                continue;
            }
            if (victory == SimulatedWarNode.WarGoal.SUCCESS) {
                if (solution == null) {
                    solution = next;
                } else if (solution.getValue(valueFunction) < next.getValue(valueFunction)) {
                    solution = next;
                }
                if (System.currentTimeMillis() - start > 10000) {
                    System.out.println("!!! Took too long to brute force. Breaking loop");
                    break;
                }
                continue;
            }

            List<SimulatedWarNode> options = next.getRaidOptions();
            queue.addAll(options);

            if (options.isEmpty() && next.getTurnsLeft() > 0) {
                next = new SimulatedWarNode(next, true, WarNation.Actions.WAIT);
                if (next.getAggressor().wait(next.getDefender())) {
                    next.decrementTurns();
                    addQueue.accept(next);
                }
            }
        }

        return solution;
    }

    public int getSize() {
        return queue.size();
    }

    public int firstResistance() {
        SimulatedWarNode peek = queue.peek();
        return peek != null ? peek.getDefender().getResistance() : 100;
    }
}
