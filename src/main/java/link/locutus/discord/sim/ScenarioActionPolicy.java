package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.AttackType;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable legality policy for scenario-level action permissions.
 *
 * <p>This seam is intentionally sim-core generic: planners decide which policy to inject,
 * while sim enforces legality uniformly per acting nation.</p>
 */
public interface ScenarioActionPolicy {
    ScenarioActionPolicy ALLOW_ALL = new ScenarioActionPolicy() {
        @Override
        public NationActionPolicy resolve(SimWorld world, SimNation nation) {
            return NationActionPolicy.allowAll();
        }

        @Override
        public NationActionPolicy resolveSnapshot(int nationId, int teamId) {
            return NationActionPolicy.allowAll();
        }
    };

    NationActionPolicy resolve(SimWorld world, SimNation nation);

    /**
     * Snapshot-native policy resolution seam.
     *
     * <p>Implementations that can resolve legality from immutable nation identity/team context
     * should override this method. The default behavior is fail-closed so planner-local callers
     * never need a live world to remain safe.</p>
     */
    default NationActionPolicy resolveSnapshot(int nationId, int teamId) {
        return NationActionPolicy.noActions();
    }

    static NationActionPolicy resolveSnapshot(ScenarioActionPolicy policy, int nationId, int teamId) {
        Objects.requireNonNull(policy, "policy");
        NationActionPolicy resolved = policy.resolveSnapshot(nationId, teamId);
        return resolved == null ? NationActionPolicy.noActions() : resolved;
    }

    static ScenarioActionPolicy fixed(NationActionPolicy policy) {
        NationActionPolicy resolved = Objects.requireNonNull(policy, "policy");
        return new ScenarioActionPolicy() {
            @Override
            public NationActionPolicy resolve(SimWorld world, SimNation nation) {
                return resolved;
            }

            @Override
            public NationActionPolicy resolveSnapshot(int nationId, int teamId) {
                return resolved;
            }
        };
    }

    static ScenarioActionPolicy perNation(Map<Integer, NationActionPolicy> byNationId, NationActionPolicy defaultPolicy) {
        Objects.requireNonNull(byNationId, "byNationId");
        NationActionPolicy fallback = Objects.requireNonNull(defaultPolicy, "defaultPolicy");
        return new ScenarioActionPolicy() {
            @Override
            public NationActionPolicy resolve(SimWorld world, SimNation nation) {
                return byNationId.getOrDefault(nation.nationId(), fallback);
            }

            @Override
            public NationActionPolicy resolveSnapshot(int nationId, int teamId) {
                return byNationId.getOrDefault(nationId, fallback);
            }
        };
    }

    static ScenarioActionPolicy perTeam(Map<Integer, NationActionPolicy> byTeamId, NationActionPolicy defaultPolicy) {
        Objects.requireNonNull(byTeamId, "byTeamId");
        NationActionPolicy fallback = Objects.requireNonNull(defaultPolicy, "defaultPolicy");
        return new ScenarioActionPolicy() {
            @Override
            public NationActionPolicy resolve(SimWorld world, SimNation nation) {
                return byTeamId.getOrDefault(nation.teamId(), fallback);
            }

            @Override
            public NationActionPolicy resolveSnapshot(int nationId, int teamId) {
                return byTeamId.getOrDefault(teamId, fallback);
            }
        };
    }

    record NationActionPolicy(
            boolean allowDeclares,
            boolean allowBuys,
            boolean allowPeace,
            boolean allowMapReservations,
            EnumSet<AttackType> allowedAttackTypes
    ) {
        public NationActionPolicy {
            Objects.requireNonNull(allowedAttackTypes, "allowedAttackTypes");
            allowedAttackTypes = allowedAttackTypes.clone();
        }

        public static NationActionPolicy allowAll() {
            return new NationActionPolicy(
                    true,
                    true,
                    true,
                    true,
                    EnumSet.allOf(AttackType.class)
            );
        }

        public static NationActionPolicy noActions() {
            return new NationActionPolicy(
                    false,
                    false,
                    false,
                    false,
                    EnumSet.noneOf(AttackType.class)
            );
        }

        public boolean allowsAttack(AttackType type) {
            return allowedAttackTypes.contains(type);
        }

        public EnumSet<AttackType> allowedAttackTypes() {
            return allowedAttackTypes.clone();
        }
    }
}