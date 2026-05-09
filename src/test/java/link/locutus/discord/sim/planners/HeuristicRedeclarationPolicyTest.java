package link.locutus.discord.sim.planners;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeuristicRedeclarationPolicyTest {
    @Test
    void defersImmediateRedeclareWhenSoonUnlockingTargetIsMateriallyBetter() {
        RedeclarationPolicy.RedeclarationContext context = new RedeclarationPolicy.RedeclarationContext(
                2,
                new int[]{1},
                new int[]{1, 1},
                0d,
                12,
                edgeIndex -> switch (edgeIndex) {
                    case 0 -> new RedeclarationPolicy.RedeclareCandidate(0, 0, true, false, 10d, 10d, 1d, 10d, 0);
                    case 1 -> new RedeclarationPolicy.RedeclareCandidate(0, 1, false, false, 10d, 10d, 1d, 30d, 1);
                    default -> throw new IllegalArgumentException();
                }
        );

        List<RedeclarationPolicy.RedeclarationChoice> choices = HeuristicRedeclarationPolicy.INSTANCE.chooseRedeclarations(context);

        assertTrue(choices.isEmpty(), "policy should keep the slot open when a much stronger target unlocks next turn");
    }

    @Test
    void stillDeclaresWhenImmediateRedeclareDominatesDeferredWindow() {
        RedeclarationPolicy.RedeclarationContext context = new RedeclarationPolicy.RedeclarationContext(
                2,
                new int[]{1},
                new int[]{1, 1},
                0d,
                12,
                edgeIndex -> switch (edgeIndex) {
                    case 0 -> new RedeclarationPolicy.RedeclareCandidate(0, 0, true, false, 10d, 10d, 1d, 20d, 0);
                    case 1 -> new RedeclarationPolicy.RedeclareCandidate(0, 1, false, false, 10d, 10d, 1d, 24d, 3);
                    default -> throw new IllegalArgumentException();
                }
        );

        List<RedeclarationPolicy.RedeclarationChoice> choices = HeuristicRedeclarationPolicy.INSTANCE.chooseRedeclarations(context);

        assertEquals(1, choices.size());
        assertEquals(0, choices.get(0).edgeIndex());
    }
}