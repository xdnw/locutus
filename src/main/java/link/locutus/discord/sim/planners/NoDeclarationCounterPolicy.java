package link.locutus.discord.sim.planners;

import java.util.List;

final class NoDeclarationCounterPolicy implements CounterDeclarationPolicy {
    static final NoDeclarationCounterPolicy INSTANCE = new NoDeclarationCounterPolicy();

    private NoDeclarationCounterPolicy() {
    }

    @Override
    public List<CounterSelection> chooseDeclarations(CounterDeclarationContext context) {
        return List.of();
    }
}