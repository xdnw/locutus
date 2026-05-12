package link.locutus.discord.sim.planners;

import java.util.List;

public record LaterDeclarationScope(
        List<Integer> declarerNationIds,
        List<Integer> targetNationIds,
        SidePolicy declarerPolicy,
        SidePolicy targetPolicy
) {
    public LaterDeclarationScope {
        declarerNationIds = declarerNationIds == null || declarerNationIds.isEmpty() ? List.of() : List.copyOf(declarerNationIds);
        targetNationIds = targetNationIds == null || targetNationIds.isEmpty() ? List.of() : List.copyOf(targetNationIds);
        if (declarerPolicy == null) {
            throw new IllegalArgumentException("declarerPolicy must not be null");
        }
        if (targetPolicy == null) {
            throw new IllegalArgumentException("targetPolicy must not be null");
        }
    }

    public boolean isEmpty() {
        return declarerNationIds.isEmpty() || targetNationIds.isEmpty();
    }
}
