package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.SuccessType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Shared owner for war-control acquisition and stripping semantics.
 *
 * <p>The kernel owns attack-family control semantics, while mutable live/planner runtimes own
 * state application. This helper keeps those rules in one place so live sim, planner-local exact
 * execution, and command-side heuristics do not drift.</p>
 */
public final class WarControlRules {
    private WarControlRules() {
    }

    public interface MutableWarControlState {
        boolean isActive();

        Integer groundControlNationId();

        Integer airSuperiorityNationId();

        Integer blockadeNationId();

        void setGroundControlNationId(Integer nationId);

        void setAirSuperiorityNationId(Integer nationId);

        void setBlockadeNationId(Integer nationId);
    }
    private enum ControlKind {
        GROUND {
            @Override
            Integer owner(MutableWarControlState war) {
                return war.groundControlNationId();
            }

            @Override
            void setOwner(MutableWarControlState war, Integer nationId) {
                war.setGroundControlNationId(nationId);
            }

            @Override
            boolean canHold(CombatKernel.NationState nation) {
                return nation.getUnits(MilitaryUnit.SOLDIER) > 0 || nation.getUnits(MilitaryUnit.TANK) > 0;
            }

            @Override
            boolean attackCanStrip(AttackType type) {
                return type == AttackType.GROUND || type == AttackType.NAVAL_GROUND;
            }

            @Override
            int delta(ControlFlagDelta delta) {
                return delta.groundControl();
            }

            @Override
            boolean clearDefender(ControlFlagDelta delta) {
                return delta.clearGroundControl();
            }
        },
        AIR {
            @Override
            Integer owner(MutableWarControlState war) {
                return war.airSuperiorityNationId();
            }

            @Override
            void setOwner(MutableWarControlState war, Integer nationId) {
                war.setAirSuperiorityNationId(nationId);
            }

            @Override
            boolean canHold(CombatKernel.NationState nation) {
                return nation.getUnits(MilitaryUnit.AIRCRAFT) > 0;
            }

            @Override
            boolean attackCanStrip(AttackType type) {
                return type.isAir() || type == AttackType.NAVAL_AIR;
            }

            @Override
            int delta(ControlFlagDelta delta) {
                return delta.airSuperiority();
            }

            @Override
            boolean clearDefender(ControlFlagDelta delta) {
                return delta.clearAirSuperiority();
            }
        },
        BLOCKADE {
            @Override
            Integer owner(MutableWarControlState war) {
                return war.blockadeNationId();
            }

            @Override
            void setOwner(MutableWarControlState war, Integer nationId) {
                war.setBlockadeNationId(nationId);
            }

            @Override
            boolean canHold(CombatKernel.NationState nation) {
                return nation.getUnits(MilitaryUnit.SHIP) > 0;
            }

            @Override
            boolean attackCanStrip(AttackType type) {
                return type.isNaval();
            }

            @Override
            int delta(ControlFlagDelta delta) {
                return delta.blockade();
            }

            @Override
            boolean clearDefender(ControlFlagDelta delta) {
                return delta.clearBlockade();
            }
        };

        abstract Integer owner(MutableWarControlState war);

        abstract void setOwner(MutableWarControlState war, Integer nationId);

        abstract boolean canHold(CombatKernel.NationState nation);

        abstract boolean attackCanStrip(AttackType type);

        abstract int delta(ControlFlagDelta delta);

        abstract boolean clearDefender(ControlFlagDelta delta);
    }

    public static ControlFlagDelta controlDelta(
            CombatKernel.AttackContext context,
            AttackType type,
            SuccessType success
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(success, "success");

        return controlDelta(
                type,
                success,
                context.defenderHasGroundControl(),
                context.defenderHasAirControl(),
                context.blockadeOwner() == CombatKernel.AttackContext.BLOCKADE_DEFENDER
        );
    }

    public static ControlFlagDelta controlDelta(
            AttackType type,
            SuccessType success,
            boolean defenderHasGroundControl,
            boolean defenderHasAirControl,
            boolean defenderHasBlockade
    ) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(success, "success");

        if (success == SuccessType.UTTER_FAILURE) {
            return ControlFlagDelta.NONE;
        }

        boolean clearGround = ControlKind.GROUND.attackCanStrip(type) && defenderHasGroundControl;
        boolean clearAir = ControlKind.AIR.attackCanStrip(type) && defenderHasAirControl;
        boolean clearBlockade = ControlKind.BLOCKADE.attackCanStrip(type) && defenderHasBlockade;

        if (success != SuccessType.IMMENSE_TRIUMPH) {
            return ControlFlagDelta.of(0, 0, 0, clearGround, clearAir, clearBlockade);
        }

        return switch (type) {
            case GROUND -> ControlFlagDelta.of(1, 0, 0, clearGround, clearAir, clearBlockade);
            case AIRSTRIKE_INFRA, AIRSTRIKE_SOLDIER, AIRSTRIKE_TANK, AIRSTRIKE_MONEY,
                    AIRSTRIKE_SHIP, AIRSTRIKE_AIRCRAFT ->
                    ControlFlagDelta.of(0, 1, 0, clearGround, clearAir, clearBlockade);
            case NAVAL, NAVAL_INFRA, NAVAL_AIR, NAVAL_GROUND ->
                    ControlFlagDelta.of(0, 0, 1, clearGround, clearAir, clearBlockade);
            default -> ControlFlagDelta.of(0, 0, 0, clearGround, clearAir, clearBlockade);
        };
    }

    public static <W extends MutableWarControlState> void reconcileAfterAttack(
            W currentWar,
            CombatKernel.NationState actor,
            CombatKernel.NationState defender,
            ControlFlagDelta delta,
            IntFunction<? extends Iterable<W>> activeWarsForNation,
            Consumer<? super W> onBlockadeChanged
    ) {
        Objects.requireNonNull(currentWar, "currentWar");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(defender, "defender");
        Objects.requireNonNull(activeWarsForNation, "activeWarsForNation");
        Consumer<? super W> blockadeCallback = onBlockadeChanged == null ? war -> { } : onBlockadeChanged;

        ControlFlagDelta resolvedDelta = delta == null ? ControlFlagDelta.NONE : delta;
        applySameWarDelta(currentWar, actor.nationId(), defender.nationId(), resolvedDelta, blockadeCallback);
        reconcileNation(actor, snapshotWars(activeWarsForNation.apply(actor.nationId())), blockadeCallback);
        reconcileNation(defender, snapshotWars(activeWarsForNation.apply(defender.nationId())), blockadeCallback);
    }

    public static <W extends MutableWarControlState> boolean applySameWarDelta(
            W war,
            int actorNationId,
            int defenderNationId,
            ControlFlagDelta delta
    ) {
        Objects.requireNonNull(war, "war");
        boolean[] blockadeChanged = new boolean[1];
        applySameWarDelta(
                war,
                actorNationId,
                defenderNationId,
                delta == null ? ControlFlagDelta.NONE : delta,
                ignored -> blockadeChanged[0] = true
        );
        return blockadeChanged[0];
    }

    private static <W extends MutableWarControlState> void applySameWarDelta(
            W war,
            int actorNationId,
            int defenderNationId,
            ControlFlagDelta delta,
            Consumer<? super W> onBlockadeChanged
    ) {
        applySameWarDelta(war, ControlKind.GROUND, actorNationId, defenderNationId, delta, onBlockadeChanged);
        applySameWarDelta(war, ControlKind.AIR, actorNationId, defenderNationId, delta, onBlockadeChanged);
        applySameWarDelta(war, ControlKind.BLOCKADE, actorNationId, defenderNationId, delta, onBlockadeChanged);
    }

    private static <W extends MutableWarControlState> void applySameWarDelta(
            W war,
            ControlKind kind,
            int actorNationId,
            int defenderNationId,
            ControlFlagDelta delta,
            Consumer<? super W> onBlockadeChanged
    ) {
        Integer newOwner = kind.owner(war);
        int controlDelta = kind.delta(delta);
        if (controlDelta > 0) {
            newOwner = actorNationId;
        } else if (controlDelta < 0) {
            newOwner = defenderNationId;
        } else if (kind.clearDefender(delta) && Objects.equals(newOwner, defenderNationId)) {
            newOwner = null;
        }
        setOwner(war, kind, newOwner, onBlockadeChanged);
    }

    private static <W extends MutableWarControlState> void reconcileNation(
            CombatKernel.NationState nation,
            List<W> wars,
            Consumer<? super W> onBlockadeChanged
    ) {
        if (wars.isEmpty()) {
            return;
        }

        int nationId = nation.nationId();
        boolean underGround = holdsIncomingEnemyControl(wars, nationId, ControlKind.GROUND);
        boolean underAir = holdsIncomingEnemyControl(wars, nationId, ControlKind.AIR);
        boolean underBlockade = holdsIncomingEnemyControl(wars, nationId, ControlKind.BLOCKADE);

        if (underGround || !ControlKind.GROUND.canHold(nation)) {
            clearOutgoingControl(wars, nationId, ControlKind.GROUND, onBlockadeChanged);
        }
        if (underAir || !ControlKind.AIR.canHold(nation)) {
            clearOutgoingControl(wars, nationId, ControlKind.AIR, onBlockadeChanged);
        }
        if (underBlockade || !ControlKind.BLOCKADE.canHold(nation)) {
            clearOutgoingControl(wars, nationId, ControlKind.BLOCKADE, onBlockadeChanged);
        }
    }

    private static <W extends MutableWarControlState> boolean holdsIncomingEnemyControl(
            List<W> wars,
            int nationId,
            ControlKind kind
    ) {
        for (W war : wars) {
            if (!war.isActive()) {
                continue;
            }
            Integer owner = kind.owner(war);
            if (owner != null && owner != nationId) {
                return true;
            }
        }
        return false;
    }

    private static <W extends MutableWarControlState> void clearOutgoingControl(
            List<W> wars,
            int nationId,
            ControlKind kind,
            Consumer<? super W> onBlockadeChanged
    ) {
        for (W war : wars) {
            if (!war.isActive()) {
                continue;
            }
            if (Objects.equals(kind.owner(war), nationId)) {
                setOwner(war, kind, null, onBlockadeChanged);
            }
        }
    }

    private static <W extends MutableWarControlState> void setOwner(
            W war,
            ControlKind kind,
            Integer nationId,
            Consumer<? super W> onBlockadeChanged
    ) {
        Integer oldOwner = kind.owner(war);
        if (Objects.equals(oldOwner, nationId)) {
            return;
        }
        kind.setOwner(war, nationId);
        if (kind == ControlKind.BLOCKADE) {
            onBlockadeChanged.accept(war);
        }
    }

    private static <W extends MutableWarControlState> List<W> snapshotWars(Iterable<W> wars) {
        if (wars == null) {
            return List.of();
        }
        ArrayList<W> copy = new ArrayList<>();
        for (W war : wars) {
            copy.add(war);
        }
        return copy;
    }
}