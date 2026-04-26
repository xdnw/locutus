package link.locutus.discord.sim.planners;

import java.util.List;
import java.util.Objects;

/**
 * Small tentative assignment delta consumed synchronously by local refinement.
 */
final class PlannerAssignmentChange {
    private int primaryAttackerId;
    private int secondaryAttackerId;
    private int primaryLength;
    private int secondaryLength;
    private final int[] primaryDefenders;
    private final int[] secondaryDefenders;

    private PlannerAssignmentChange(int primaryCapacity, int secondaryCapacity) {
        this.primaryDefenders = new int[Math.max(0, primaryCapacity)];
        this.secondaryDefenders = new int[Math.max(0, secondaryCapacity)];
    }

    static PlannerAssignmentChange single(int attackerId, List<Integer> defenders) {
        Objects.requireNonNull(defenders, "defenders");
        PlannerAssignmentChange change = new PlannerAssignmentChange(defenders.size(), 0);
        change.setSingle(attackerId);
        for (Integer defenderId : defenders) {
            change.addPrimaryDefender(Objects.requireNonNull(defenderId, "defenderId"));
        }
        return change;
    }

    static PlannerAssignmentChange pair(
            int firstAttackerId,
            List<Integer> firstDefenders,
            int secondAttackerId,
            List<Integer> secondDefenders
    ) {
        Objects.requireNonNull(firstDefenders, "firstDefenders");
        Objects.requireNonNull(secondDefenders, "secondDefenders");
        PlannerAssignmentChange change = new PlannerAssignmentChange(firstDefenders.size(), secondDefenders.size());
        change.setPair(firstAttackerId, secondAttackerId);
        for (Integer defenderId : firstDefenders) {
            change.addPrimaryDefender(Objects.requireNonNull(defenderId, "firstDefenderId"));
        }
        for (Integer defenderId : secondDefenders) {
            change.addSecondaryDefender(Objects.requireNonNull(defenderId, "secondDefenderId"));
        }
        return change;
    }

    static PlannerAssignmentChange scratch(int primaryCapacity, int secondaryCapacity) {
        return new PlannerAssignmentChange(primaryCapacity, secondaryCapacity);
    }

    void setSingle(int attackerId) {
        validatePrimaryAttacker(attackerId);
        this.primaryAttackerId = attackerId;
        this.secondaryAttackerId = 0;
        this.primaryLength = 0;
        this.secondaryLength = 0;
    }

    void setPair(int firstAttackerId, int secondAttackerId) {
        validatePrimaryAttacker(firstAttackerId);
        if (secondAttackerId <= 0) {
            throw new IllegalArgumentException("secondaryAttackerId must be > 0");
        }
        if (secondAttackerId == firstAttackerId) {
            throw new IllegalArgumentException("assignment change cannot repeat attacker id");
        }
        this.primaryAttackerId = firstAttackerId;
        this.secondaryAttackerId = secondAttackerId;
        this.primaryLength = 0;
        this.secondaryLength = 0;
    }

    void addPrimaryDefender(int defenderId) {
        if (primaryLength >= primaryDefenders.length) {
            throw new IllegalStateException("primary defender scratch capacity exceeded");
        }
        primaryDefenders[primaryLength++] = defenderId;
    }

    void addSecondaryDefender(int defenderId) {
        if (secondaryAttackerId == 0) {
            throw new IllegalStateException("secondary attacker is not set");
        }
        if (secondaryLength >= secondaryDefenders.length) {
            throw new IllegalStateException("secondary defender scratch capacity exceeded");
        }
        secondaryDefenders[secondaryLength++] = defenderId;
    }

    int size() {
        return secondaryAttackerId == 0 ? 1 : 2;
    }

    int attackerIdAt(int index) {
        return switch (index) {
            case 0 -> primaryAttackerId;
            case 1 -> {
                if (secondaryAttackerId == 0) {
                    throw new IndexOutOfBoundsException(index);
                }
                yield secondaryAttackerId;
            }
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    int defenderCountAt(int index) {
        return switch (index) {
            case 0 -> primaryLength;
            case 1 -> {
                if (secondaryAttackerId == 0) {
                    throw new IndexOutOfBoundsException(index);
                }
                yield secondaryLength;
            }
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    int defenderIdAt(int changeIndex, int defenderIndex) {
        return switch (changeIndex) {
            case 0 -> {
                if (defenderIndex < 0 || defenderIndex >= primaryLength) {
                    throw new IndexOutOfBoundsException(defenderIndex);
                }
                yield primaryDefenders[defenderIndex];
            }
            case 1 -> {
                if (secondaryAttackerId == 0 || defenderIndex < 0 || defenderIndex >= secondaryLength) {
                    throw new IndexOutOfBoundsException(defenderIndex);
                }
                yield secondaryDefenders[defenderIndex];
            }
            default -> throw new IndexOutOfBoundsException(changeIndex);
        };
    }

    private static void validatePrimaryAttacker(int attackerId) {
        if (attackerId <= 0) {
            throw new IllegalArgumentException("primaryAttackerId must be > 0");
        }
    }
}
