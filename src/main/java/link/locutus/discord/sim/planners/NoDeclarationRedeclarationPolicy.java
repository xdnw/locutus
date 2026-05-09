package link.locutus.discord.sim.planners;

import java.util.List;

final class NoDeclarationRedeclarationPolicy implements RedeclarationPolicy {
    static final NoDeclarationRedeclarationPolicy INSTANCE = new NoDeclarationRedeclarationPolicy();

    private NoDeclarationRedeclarationPolicy() {
    }

    @Override
    public List<RedeclarationChoice> chooseRedeclarations(RedeclarationContext context) {
        return List.of();
    }
}