package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.combat.state.BasicWarStateView;
import link.locutus.discord.sim.combat.state.CombatantView;
import link.locutus.discord.sim.combat.state.WarStateView;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class AttackResolver {
    private AttackResolver() {
    }

    public record Flags(
            boolean defAirControl,
            boolean attAirControl,
            boolean attGroundControl,
            boolean defFortified,
            boolean equipAttackerSoldiers,
            boolean equipDefenderSoldiers
    ) {
        public static Flags defaults() {
            return new Flags(false, false, false, false, true, true);
        }

        public WarStateView toWarState(WarType warType) {
            return new BasicWarStateView(
                    warType,
                    true,
                    attAirControl,
                    defAirControl,
                    attGroundControl,
                    false,
                    false,
                    defFortified,
                    0,
                    0,
                    100,
                    100,
                    WarStateView.BLOCKADE_NONE
            );
        }

        public EngagementOptions toEngagementOptions() {
            return new EngagementOptions(equipAttackerSoldiers, equipDefenderSoldiers);
        }
    }

    public record EngagementOptions(
            boolean equipAttackerSoldiers,
            boolean equipDefenderSoldiers
    ) {
        public static EngagementOptions defaults() {
            return new EngagementOptions(true, true);
        }
    }

    public record AttackRanges(
            SuccessType success,
            Map<MilitaryUnit, Map.Entry<Integer, Integer>> attackerLossRanges,
            Map<MilitaryUnit, Map.Entry<Integer, Integer>> defenderLossRanges,
            double[] consumption,
            int mapCost,
            ControlFlagDelta controlDelta
    ) {
        public AttackRanges {
            Objects.requireNonNull(success, "success");
            attackerLossRanges = immutableRanges(attackerLossRanges);
            defenderLossRanges = immutableRanges(defenderLossRanges);
            consumption = consumption == null ? new double[ResourceType.values.length] : consumption.clone();
            controlDelta = controlDelta == null ? ControlFlagDelta.NONE : controlDelta;
            if (mapCost < 0) {
                throw new IllegalArgumentException("mapCost must be >= 0");
            }
        }

        private static Map<MilitaryUnit, Map.Entry<Integer, Integer>> immutableRanges(
                Map<MilitaryUnit, Map.Entry<Integer, Integer>> source
        ) {
            if (source == null || source.isEmpty()) {
                return Collections.emptyMap();
            }
            EnumMap<MilitaryUnit, Map.Entry<Integer, Integer>> copy = new EnumMap<>(MilitaryUnit.class);
            for (Map.Entry<MilitaryUnit, Map.Entry<Integer, Integer>> entry : source.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                int min = Math.max(0, entry.getValue().getKey());
                int max = Math.max(min, entry.getValue().getValue());
                copy.put(entry.getKey(), Map.entry(min, max));
            }
            return Collections.unmodifiableMap(copy);
        }
    }

    public static AttackOutcome resolve(
            CombatantView attacker,
            CombatantView defender,
            WarStateView war,
            AttackType type,
            ResolutionMode mode
    ) {
        return resolve(new ViewAttackContext(attacker, defender, war), type, mode);
    }

    static AttackOutcome resolve(
            CombatKernel.AttackContext context,
            AttackType type,
            ResolutionMode mode
    ) {
        return CombatKernel.resolve(context, type, mode);
    }

    public static AttackOutcome resolve(
            CombatantView attacker,
            CombatantView defender,
            WarStateView war,
            AttackType type,
            ResolutionMode mode,
            RandomSource rng,
            long streamKey
    ) {
        return resolve(new ViewAttackContext(attacker, defender, war), type, mode, rng, streamKey);
    }

    static AttackOutcome resolve(
            CombatKernel.AttackContext context,
            AttackType type,
            ResolutionMode mode,
            RandomSource rng,
            long streamKey
    ) {
        return CombatKernel.resolve(context, type, mode, rng, streamKey);
    }

    static void resolveInto(
            CombatKernel.AttackContext context,
            AttackType type,
            ResolutionMode mode,
            AttackScratch scratch,
            MutableAttackResult out
    ) {
        CombatKernel.resolveInto(context, type, mode, scratch, out);
    }

    static void resolveInto(
            CombatKernel.AttackContext context,
            AttackType type,
            ResolutionMode mode,
            RandomSource rng,
            long streamKey,
            AttackScratch scratch,
            MutableAttackResult out
    ) {
        CombatKernel.resolveInto(context, type, mode, rng, streamKey, scratch, out);
    }

    static AttackOutcome resolve(
            CombatKernel.AttackContext context,
            AttackType type,
            ResolutionMode mode,
            EngagementOptions options,
            RandomSource rng,
            long streamKey,
            OddsModel oddsModel
    ) {
        AttackScratch scratch = new AttackScratch();
        MutableAttackResult result = new MutableAttackResult();
        resolveInto(context, type, mode, options, rng, streamKey, oddsModel, scratch, result);
        return result.toAttackOutcome();
    }

    static void resolveInto(
            CombatKernel.AttackContext context,
            AttackType type,
            ResolutionMode mode,
            EngagementOptions options,
            RandomSource rng,
            long streamKey,
            OddsModel oddsModel,
            AttackScratch scratch,
            MutableAttackResult out
    ) {
        CombatKernel.resolveInto(
                context,
                type,
                mode,
                toKernelOptions(options),
                rng,
                streamKey,
                oddsModel,
                scratch,
                out
        );
    }

    public static AttackOutcome resolve(
            CombatantView attacker,
            CombatantView defender,
            WarStateView war,
            AttackType type,
            ResolutionMode mode,
            EngagementOptions options,
            RandomSource rng,
            long streamKey,
            OddsModel oddsModel
    ) {
        return resolve(new ViewAttackContext(attacker, defender, war), type, mode, options, rng, streamKey, oddsModel);
    }

    public static AttackRanges rangesForSuccess(
            CombatantView attacker,
            CombatantView defender,
            WarStateView war,
            AttackType type,
            SuccessType success
    ) {
        return rangesForSuccess(attacker, defender, war, type, success, EngagementOptions.defaults(), OddsModel.DEFAULT);
    }

    static AttackRanges rangesForSuccess(
            CombatKernel.AttackContext context,
            AttackType type,
            SuccessType success,
            EngagementOptions options,
            OddsModel oddsModel
    ) {
        Objects.requireNonNull(context, "context");
        return rangesForSuccess(context.attacker(), context.defender(), context, type, success, options, oddsModel);
    }

    public static AttackRanges rangesForSuccess(
            CombatantView attacker,
            CombatantView defender,
            WarStateView war,
            AttackType type,
            SuccessType success,
            EngagementOptions options,
            OddsModel oddsModel
    ) {
        return rangesForSuccess(attacker, defender, new ViewAttackContext(attacker, defender, war), type, success, options, oddsModel);
    }

    private static AttackRanges rangesForSuccess(
            CombatKernel.NationState attacker,
            CombatKernel.NationState defender,
            CombatKernel.AttackContext context,
            AttackType type,
            SuccessType success,
            EngagementOptions options,
            OddsModel oddsModel
    ) {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(defender, "defender");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(success, "success");

        if (options == null) {
            options = EngagementOptions.defaults();
        }
        if (oddsModel == null) {
            oddsModel = OddsModel.DEFAULT;
        }

        double[] consumption = ResourceType.resourcesToArray(
            type.getConsumption(attacker::getUnits, options.equipAttackerSoldiers())
        );

        Map.Entry<Map<MilitaryUnit, Map.Entry<Integer, Integer>>,
                Map<MilitaryUnit, Map.Entry<Integer, Integer>>> casualties = type.getCasualties(
                attacker,
                defender,
                success,
                context.warType(),
            context.attackerIsOriginalAttacker(),
                context.defenderHasAirControl(),
                context.attackerHasAirControl(),
                context.defenderFortified(),
                options.equipAttackerSoldiers(),
                options.equipDefenderSoldiers(),
                context.attackerHasGroundControl()
        );

        return new AttackRanges(
                success,
                casualties.getKey(),
                casualties.getValue(),
                consumption,
                type.getMapUsed(),
            CombatKernel.computeControlDelta(context, type, success)
        );
    }

    public static double[] oddsVector(
            CombatantView attacker,
            CombatantView defender,
            AttackType type,
            WarStateView war
    ) {
        return oddsVector(attacker, defender, type, war, EngagementOptions.defaults(), OddsModel.DEFAULT);
    }

    public static double[] oddsVector(
            CombatantView attacker,
            CombatantView defender,
            AttackType type,
            WarStateView war,
            EngagementOptions options,
            OddsModel oddsModel
    ) {
        return oddsVector(new ViewAttackContext(attacker, defender, war), type, options, oddsModel);
    }

    static double[] oddsVector(
            CombatKernel.AttackContext context,
            AttackType type,
            EngagementOptions options,
            OddsModel oddsModel
    ) {
        return CombatKernel.oddsVector(context, type, toKernelOptions(options), oddsModel);
    }

    private static CombatKernel.EngagementOptions toKernelOptions(EngagementOptions options) {
        EngagementOptions resolved = options == null ? EngagementOptions.defaults() : options;
        return new CombatKernel.EngagementOptions(
                resolved.equipAttackerSoldiers(),
                resolved.equipDefenderSoldiers()
        );
    }
}
