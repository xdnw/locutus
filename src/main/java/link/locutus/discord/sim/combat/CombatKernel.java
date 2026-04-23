package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.project.Projects;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Shared combat math entry point for planners and sim compatibility wrappers.
 *
 * <p>The public API is entirely in terms of kernel-owned contracts ({@link NationState},
 * {@link AttackContext}). Boundary adapters for command/test surfaces that carry
 * {@code CombatantView} or {@code WarStateView} live on {@link AttackResolver}, which
 * owns the transition from boundary view types into kernel inputs.</p>
 *
 * <p>Resolution contract: {@link ResolutionMode#DETERMINISTIC_EV} exposes expected-value
 * ranking outputs only. Callers must not treat EV arrays as authoritative mutable state.
 * Only discrete resolution outputs are valid state-transition inputs for mutable war/nation state.</p>
 */
public final class CombatKernel {
    private static final SuccessType[] SUCCESS_TYPES = SuccessType.values;

    private CombatKernel() {
    }

    /** Kernel-owned engagement toggles used by planner and boundary adapters. */
    record EngagementOptions(
            boolean equipAttackerSoldiers,
            boolean equipDefenderSoldiers
    ) {
        public static EngagementOptions defaults() {
            return new EngagementOptions(true, true);
        }
    }

    /**
     * Kernel-owned nation surface for combat math. Boundary adapters may wrap richer models,
     * but planner hot paths should depend on this contract instead of combat-state wrappers.
     */
    public interface NationState extends AttackType.CasualtyNationView {
        int nationId();

        int cities();

        int researchBits();
    }

    public interface PrimitiveNationBuffer {
        int[] unitsFlat();

        int unitBaseOffset(int nationIndex);

        double[] cityInfraFlat();

        int cityInfraBaseOffset(int nationIndex);

        int cityCount(int nationIndex);
    }

    /**
     * Nation contract backed by primitive flat buffers and per-nation offsets.
     *
     * <p>Implementers provide immutable nation metadata and modifier hooks, while units and
     * per-city infra are read directly from caller-owned flat arrays. This keeps hot reads on
     * indexed primitive slices without requiring adapter objects per access.</p>
     */
    public interface BufferBackedNationState extends PrimitiveCityAccess {
        PrimitiveNationBuffer nationBuffer();

        int nationIndex();

        @Override
        default int cities() {
            return nationBuffer().cityCount(nationIndex());
        }

        @Override
        default int getUnits(MilitaryUnit unit) {
            return nationBuffer().unitsFlat()[nationBuffer().unitBaseOffset(nationIndex()) + unit.ordinal()];
        }

        @Override
        default double cityInfra(int cityIndex) {
            PrimitiveNationBuffer buffer = nationBuffer();
            int cityCount = buffer.cityCount(nationIndex());
            if (cityIndex < 0 || cityIndex >= cityCount) {
                throw new IndexOutOfBoundsException(cityIndex);
            }
            return buffer.cityInfraFlat()[buffer.cityInfraBaseOffset(nationIndex()) + cityIndex];
        }
    }

    public interface PrimitiveCityAccess extends NationState {
        default double cityInfra(int cityIndex) {
            throw new UnsupportedOperationException("Primitive city infra access is not available");
        }

        default Map.Entry<Integer, Integer> cityMissileDamage(int cityIndex) {
            throw new UnsupportedOperationException("Primitive city missile damage access is not available");
        }

        default Map.Entry<Integer, Integer> cityNukeDamage(int cityIndex) {
            throw new UnsupportedOperationException("Primitive city nuke damage access is not available");
        }

        @Override
        default Collection<? extends AttackType.CasualtyCityView> getCityViews() {
            int cityCount = cities();
            return new AbstractList<>() {
                @Override
                public AttackType.CasualtyCityView get(int index) {
                    if (index < 0 || index >= cityCount) {
                        throw new IndexOutOfBoundsException(index);
                    }
                    return new AttackType.CasualtyCityView() {
                        @Override
                        public double getInfra() {
                            return cityInfra(index);
                        }

                        @Override
                        public Map.Entry<Integer, Integer> getMissileDamage(
                                java.util.function.Predicate<link.locutus.discord.apiv1.enums.city.project.Project> hasProject
                        ) {
                            return cityMissileDamage(index);
                        }

                        @Override
                        public Map.Entry<Integer, Integer> getNukeDamage(
                                java.util.function.Predicate<link.locutus.discord.apiv1.enums.city.project.Project> hasProject
                        ) {
                            return cityNukeDamage(index);
                        }
                    };
                }

                @Override
                public int size() {
                    return cityCount;
                }
            };
        }

        @Override
        default double maxCityInfra() {
            double max = 0d;
            for (int i = 0; i < cities(); i++) {
                max = Math.max(max, cityInfra(i));
            }
            return max;
        }
    }

    public interface AttackContext {
        /** Returned by {@link #blockadeOwner()} when neither side holds a blockade. */
        int BLOCKADE_NONE = 0;
        /** Returned by {@link #blockadeOwner()} when the attacker holds a blockade. */
        int BLOCKADE_ATTACKER = 1;
        /** Returned by {@link #blockadeOwner()} when the defender holds a blockade. */
        int BLOCKADE_DEFENDER = 2;

        NationState attacker();

        NationState defender();

        WarType warType();

        default boolean attackerIsOriginalAttacker() {
            return true;
        }

        boolean attackerHasAirControl();

        boolean defenderHasAirControl();

        boolean attackerHasGroundControl();

        boolean defenderHasGroundControl();

        boolean attackerFortified();

        boolean defenderFortified();

        int attackerMaps();

        int defenderMaps();

        int attackerResistance();

        int defenderResistance();

        int blockadeOwner();
    }

    public interface PrimitiveWarBuffer {
        WarType warType(int warIndex);

        boolean attackerHasAirControl(int warIndex);

        boolean defenderHasAirControl(int warIndex);

        boolean attackerHasGroundControl(int warIndex);

        boolean defenderHasGroundControl(int warIndex);

        boolean attackerFortified(int warIndex);

        boolean defenderFortified(int warIndex);

        int attackerMaps(int warIndex);

        int defenderMaps(int warIndex);

        int attackerResistance(int warIndex);

        int defenderResistance(int warIndex);

        int blockadeOwner(int warIndex);
    }

    /**
     * War context contract backed by primitive indexed storage.
     *
     * <p>Implementers provide attacker/defender nation views and a stable war index. All mutable
     * war fields are then read via the shared primitive war buffer.</p>
     */
    public interface BufferBackedAttackContext extends AttackContext {
        PrimitiveWarBuffer warBuffer();

        int warIndex();

        @Override
        default boolean attackerIsOriginalAttacker() {
            return true;
        }

        @Override
        default WarType warType() {
            return warBuffer().warType(warIndex());
        }

        @Override
        default boolean attackerHasAirControl() {
            return warBuffer().attackerHasAirControl(warIndex());
        }

        @Override
        default boolean defenderHasAirControl() {
            return warBuffer().defenderHasAirControl(warIndex());
        }

        @Override
        default boolean attackerHasGroundControl() {
            return warBuffer().attackerHasGroundControl(warIndex());
        }

        @Override
        default boolean defenderHasGroundControl() {
            return warBuffer().defenderHasGroundControl(warIndex());
        }

        @Override
        default boolean attackerFortified() {
            return warBuffer().attackerFortified(warIndex());
        }

        @Override
        default boolean defenderFortified() {
            return warBuffer().defenderFortified(warIndex());
        }

        @Override
        default int attackerMaps() {
            return warBuffer().attackerMaps(warIndex());
        }

        @Override
        default int defenderMaps() {
            return warBuffer().defenderMaps(warIndex());
        }

        @Override
        default int attackerResistance() {
            return warBuffer().attackerResistance(warIndex());
        }

        @Override
        default int defenderResistance() {
            return warBuffer().defenderResistance(warIndex());
        }

        @Override
        default int blockadeOwner() {
            return warBuffer().blockadeOwner(warIndex());
        }
    }

    /** Deterministic or most-likely resolution. Callers with view types use {@link AttackResolver}. */
    public static AttackOutcome resolve(
            AttackContext context,
            AttackType type,
            ResolutionMode mode
    ) {
        AttackScratch scratch = new AttackScratch();
        MutableAttackResult result = new MutableAttackResult();
        resolveInto(
            context,
            type,
            mode,
            EngagementOptions.defaults(),
            null,
            streamKey(context.attacker().nationId(), context.defender().nationId(), type),
            OddsModel.DEFAULT,
            scratch,
            result
        );
        return result.toAttackOutcome();
    }

    /** Stochastic resolution. Callers with view types use {@link AttackResolver}. */
    public static AttackOutcome resolve(
            AttackContext context,
            AttackType type,
            ResolutionMode mode,
            RandomSource rng,
            long streamKey
    ) {
        AttackScratch scratch = new AttackScratch();
        MutableAttackResult result = new MutableAttackResult();
        resolveInto(
            context,
            type,
            mode,
            EngagementOptions.defaults(),
            rng,
            streamKey,
            OddsModel.DEFAULT,
            scratch,
            result
        );
        return result.toAttackOutcome();
    }

    /** Allocation-free resolve into caller-owned scratch and result buffers. */
    public static void resolveInto(
            AttackContext context,
            AttackType type,
            ResolutionMode mode,
            AttackScratch scratch,
            MutableAttackResult out
    ) {
        resolveInto(
            context,
            type,
            mode,
            EngagementOptions.defaults(),
            null,
            streamKey(context.attacker().nationId(), context.defender().nationId(), type),
            OddsModel.DEFAULT,
            scratch,
            out
        );
    }

    /** Stochastic allocation-free resolve into caller-owned scratch and result buffers. */
    public static void resolveInto(
            AttackContext context,
            AttackType type,
            ResolutionMode mode,
            RandomSource rng,
            long streamKey,
            AttackScratch scratch,
            MutableAttackResult out
    ) {
        resolveInto(
                context,
                type,
                mode,
                EngagementOptions.defaults(),
                rng,
                streamKey,
                OddsModel.DEFAULT,
                scratch,
                out
        );
    }

    static void resolveInto(
            AttackContext context,
            AttackType type,
            ResolutionMode mode,
            EngagementOptions options,
            RandomSource rng,
            long streamKey,
            OddsModel oddsModel,
            AttackScratch scratch,
            MutableAttackResult out
    ) {
        Objects.requireNonNull(context, "context");
        resolveInto(
                context.attacker(),
                context.defender(),
                context,
                type,
                mode,
                options,
                rng,
                streamKey,
                oddsModel,
                scratch,
                out
        );
    }

    static double[] oddsVector(
            AttackContext context,
            AttackType type,
            EngagementOptions options,
            OddsModel oddsModel
    ) {
        Objects.requireNonNull(context, "context");
        return oddsVector(context.attacker(), context.defender(), context, type, options, oddsModel);
    }

    /**
     * Kernel-derived low-probe specialist admission signal.
     *
     * <p>The signal is the expected infra damage from a legal missile or nuke attack,
     * normalized against the defender's highest-infra city and clamped to [0, 1]. This
     * keeps low-probe specialist admission tied to shared combat legality and the current
     * city-damage owner instead of stocked-unit heuristics in planner code.</p>
     */
    public static double specialistAdmissionSignal(
            AttackContext context,
            AttackType type,
            AttackScratch scratch,
            MutableAttackResult out
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(scratch, "scratch");
        Objects.requireNonNull(out, "out");

        if (!isLegalSpecialistAttack(context.attacker(), type)) {
            return 0d;
        }
        double maxCityInfra = context.defender().maxCityInfra();
        if (!(maxCityInfra > 0d)) {
            return 0d;
        }

        resolveInto(context, type, ResolutionMode.DETERMINISTIC_EV, scratch, out);
        double normalized = out.infraDestroyed() / maxCityInfra;
        if (normalized <= 0d) {
            return 0d;
        }
        return Math.min(1d, normalized);
    }

    /**
     * Kernel-derived admission signal for non-specialist opening attacks.
     *
     * <p>The signal is {@code P(success >= MODERATE_SUCCESS)} computed from the same
     * odds path used by shared combat resolution, written into caller-owned scratch to
     * avoid per-probe allocation.</p>
     */
    public static double admissionSignal(
            AttackContext context,
            AttackType type,
            AttackScratch scratch
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(scratch, "scratch");

        if (ProjectileDefenseMath.isProjectileAttack(type)) {
            return 0d;
        }

        fillOddsVector(
                context.attacker(),
                context.defender(),
                context,
                type,
                EngagementOptions.defaults(),
                OddsModel.DEFAULT,
                scratch.odds
        );
        double signal = 0d;
        for (SuccessType success : SuccessType.values) {
            if (success.ordinal() >= SuccessType.MODERATE_SUCCESS.ordinal()) {
                signal += scratch.odds[success.ordinal()];
            }
        }
        return signal;
    }

    public static boolean isLegalSpecialistAttack(NationState attacker, AttackType type) {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(type, "type");
        return switch (type) {
            case MISSILE -> attacker.getUnits(MilitaryUnit.MISSILE) > 0
                    && attacker.hasProject(Projects.MISSILE_LAUNCH_PAD);
            case NUKE -> attacker.getUnits(MilitaryUnit.NUKE) > 0
                    && attacker.hasProject(Projects.NUCLEAR_RESEARCH_FACILITY);
            default -> false;
        };
    }

    private static void resolveInto(
            NationState attacker,
            NationState defender,
            AttackContext context,
            AttackType type,
            ResolutionMode mode,
            EngagementOptions options,
            RandomSource rng,
            long streamKey,
            OddsModel oddsModel,
            AttackScratch scratch,
            MutableAttackResult out
    ) {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(defender, "defender");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(scratch, "scratch");
        Objects.requireNonNull(out, "out");

        if (options == null) {
            options = EngagementOptions.defaults();
        }
        if (oddsModel == null) {
            oddsModel = OddsModel.DEFAULT;
        }

        EngagementOptions finalOptions = options;

        fillOddsVector(attacker, defender, context, type, finalOptions, oddsModel, scratch.odds);
        type.writeConsumption(attacker::getUnits, finalOptions.equipAttackerSoldiers(), scratch.consumption);

        CasualtyProvider casualties = success -> type.getCasualties(
                attacker,
                defender,
                success,
                context.warType(),
            context.attackerIsOriginalAttacker(),
                context.defenderHasAirControl(),
                context.attackerHasAirControl(),
                context.defenderFortified(),
                finalOptions.equipAttackerSoldiers(),
                finalOptions.equipDefenderSoldiers(),
                context.attackerHasGroundControl()
        );

        resolveInternalInto(
            casualties,
            success -> computeControlDelta(context, type, success),
            type,
            scratch.odds,
            mode,
            rng,
            streamKey,
            scratch.consumption,
            scratch,
            out
        );
    }

    private static double[] oddsVector(
            NationState attacker,
            NationState defender,
            AttackContext context,
            AttackType type,
            EngagementOptions options,
            OddsModel oddsModel
    ) {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(defender, "defender");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(context, "context");

        if (options == null) {
            options = EngagementOptions.defaults();
        }
        if (ProjectileDefenseMath.isProjectileAttack(type)) {
            return projectileOddsVector(type, defender);
        }
        if (oddsModel == null) {
            oddsModel = OddsModel.DEFAULT;
        }

        StrengthPair strengths = computeStrengths(attacker, defender, context, type, options);
        return oddsModel.odds(strengths.attacker(), strengths.defender(), type);
    }

    private interface ControlDeltaProvider {
        ControlFlagDelta controlDelta(SuccessType success);
    }

    private static void resolveInternalInto(
            CasualtyProvider casualtyProvider,
            ControlDeltaProvider controlDeltaProvider,
            AttackType type,
            double[] odds,
            ResolutionMode mode,
            RandomSource rng,
            long streamKey,
            double[] consumption,
            AttackScratch scratch,
            MutableAttackResult out
    ) {
        if (mode == ResolutionMode.STOCHASTIC && rng == null) {
            throw new IllegalArgumentException("STOCHASTIC mode requires a non-null RandomSource");
        }
        requireOddsVector(odds);

        SuccessType successHint = argmax(odds);

        switch (mode) {
            case DETERMINISTIC_EV -> {
                Arrays.fill(scratch.attackerLossesWork, 0d);
                Arrays.fill(scratch.defenderLossesWork, 0d);
                double infraSum = 0d;
                double lootSum = 0d;
                double attackerResistanceDelta = 0d;
                double defenderResistanceDelta = 0d;

                for (int s = 0; s < SUCCESS_TYPES.length; s++) {
                    double weight = odds[s];
                    if (weight <= 0d) continue;

                    SuccessType success = SUCCESS_TYPES[s];
                    var casualties = casualtyProvider.casualties(success);

                    DoubleBox infraBox = new DoubleBox();
                    DoubleBox lootBox = new DoubleBox();

                    accumulateMidpoint(casualties.getKey(), scratch.attackerLossesWork, weight, null, null);
                    accumulateMidpoint(casualties.getValue(), scratch.defenderLossesWork, weight, infraBox, lootBox);

                    infraSum += infraBox.value;
                    lootSum += lootBox.value;
                    defenderResistanceDelta += -weight * type.getResistance(success);
                }

                out.setExpected(
                        successHint,
                        scratch.attackerLossesWork,
                        scratch.defenderLossesWork,
                        infraSum,
                        lootSum,
                        clampResistanceDelta(attackerResistanceDelta),
                        clampResistanceDelta(defenderResistanceDelta),
                        type.getMapUsed(),
                        consumption,
                        controlDeltaProvider.controlDelta(successHint)
                );
            }
            case MOST_LIKELY -> {
                SuccessType success = successHint;
                var casualties = casualtyProvider.casualties(success);

                Arrays.fill(scratch.attackerLossesWork, 0d);
                Arrays.fill(scratch.defenderLossesWork, 0d);
                DoubleBox infraBox = new DoubleBox();
                DoubleBox lootBox = new DoubleBox();

                accumulateMidpoint(casualties.getKey(), scratch.attackerLossesWork, 1d, null, null);
                accumulateMidpoint(casualties.getValue(), scratch.defenderLossesWork, 1d, infraBox, lootBox);
                roundToInt(scratch.attackerLossesWork, scratch.attackerLossesInt);
                roundToInt(scratch.defenderLossesWork, scratch.defenderLossesInt);

                out.setDiscrete(
                        success,
                        ResolutionMode.MOST_LIKELY,
                        scratch.attackerLossesInt,
                        scratch.defenderLossesInt,
                        infraBox.value,
                        lootBox.value,
                        0d,
                        clampResistanceDelta(-type.getResistance(success)),
                        type.getMapUsed(),
                        consumption,
                        controlDeltaProvider.controlDelta(success)
                );
            }
            case STOCHASTIC -> {
                SuccessType success = sampleSuccess(odds, rng, streamKey);
                var casualties = casualtyProvider.casualties(success);

                Arrays.fill(scratch.attackerLossesWork, 0d);
                Arrays.fill(scratch.defenderLossesWork, 0d);
                DoubleBox infraBox = new DoubleBox();
                DoubleBox lootBox = new DoubleBox();

                accumulateSampled(casualties.getKey(), scratch.attackerLossesWork, rng, streamKey ^ 0x41AL, null, null);
                accumulateSampled(casualties.getValue(), scratch.defenderLossesWork, rng, streamKey ^ 0xDEFL, infraBox, lootBox);
                roundToInt(scratch.attackerLossesWork, scratch.attackerLossesInt);
                roundToInt(scratch.defenderLossesWork, scratch.defenderLossesInt);

                out.setDiscrete(
                        success,
                        ResolutionMode.STOCHASTIC,
                        scratch.attackerLossesInt,
                        scratch.defenderLossesInt,
                        infraBox.value,
                        lootBox.value,
                        0d,
                        clampResistanceDelta(-type.getResistance(success)),
                        type.getMapUsed(),
                        consumption,
                        controlDeltaProvider.controlDelta(success)
                );
            }
        }
    }

    static ControlFlagDelta computeControlDelta(AttackContext context, AttackType type, SuccessType success) {
        return WarControlRules.controlDelta(context, type, success);
    }

    private static void requireOddsVector(double[] odds) {
        if (odds == null || odds.length != SUCCESS_TYPES.length) {
            throw new IllegalArgumentException("odds must be sized to SuccessType.values.length");
        }
    }

    private static void accumulateMidpoint(
            Map<MilitaryUnit, Map.Entry<Integer, Integer>> casualties,
            double[] unitLosses,
            double weight,
            DoubleBox infraOut,
            DoubleBox lootOut
    ) {
        if (casualties == null) return;

        for (Map.Entry<MilitaryUnit, Map.Entry<Integer, Integer>> casualtyEntry : casualties.entrySet()) {
            MilitaryUnit unit = casualtyEntry.getKey();
            Map.Entry<Integer, Integer> range = casualtyEntry.getValue();

            double midpoint = (range.getKey() + range.getValue()) * 0.5d;
            switch (unit) {
                case INFRASTRUCTURE -> {
                    if (infraOut != null) infraOut.value += weight * midpoint;
                }
                case MONEY -> {
                    if (lootOut != null) lootOut.value += weight * midpoint;
                }
                default -> unitLosses[unit.ordinal()] += weight * midpoint;
            }
        }
    }

    private static void accumulateSampled(
            Map<MilitaryUnit, Map.Entry<Integer, Integer>> casualties,
            double[] unitLosses,
            RandomSource rng,
            long baseKey,
            DoubleBox infraOut,
            DoubleBox lootOut
    ) {
        if (casualties == null) return;

        int offset = 0;
        for (Map.Entry<MilitaryUnit, Map.Entry<Integer, Integer>> casualtyEntry : casualties.entrySet()) {
            MilitaryUnit unit = casualtyEntry.getKey();
            Map.Entry<Integer, Integer> range = casualtyEntry.getValue();

            int min = range.getKey();
            int max = range.getValue();
            double sample = min == max ? min : min + (max - min) * rng.nextDouble(baseKey + offset++);
            switch (unit) {
                case INFRASTRUCTURE -> {
                    if (infraOut != null) infraOut.value += sample;
                }
                case MONEY -> {
                    if (lootOut != null) lootOut.value += sample;
                }
                default -> unitLosses[unit.ordinal()] = sample;
            }
        }
    }

    private static void roundToInt(double[] values, int[] out) {
        for (int i = 0; i < values.length; i++) {
            out[i] = (int) Math.round(values[i]);
        }
    }

    private static void fillOddsVector(
            NationState attacker,
            NationState defender,
            AttackContext context,
            AttackType type,
            EngagementOptions options,
            OddsModel oddsModel,
            double[] out
    ) {
        if (ProjectileDefenseMath.isProjectileAttack(type)) {
            writeProjectileOdds(type, defender, out);
            return;
        }
        if (oddsModel == OddsModel.DEFAULT) {
            StrengthPair strengths = computeStrengths(attacker, defender, context, type, options);
            OddsCalculator.writeOdds(strengths.attacker(), strengths.defender(), out);
            return;
        }
        double[] computed = oddsVector(attacker, defender, context, type, options, oddsModel);
        System.arraycopy(computed, 0, out, 0, out.length);
    }

    private static double[] projectileOddsVector(AttackType type, NationState defender) {
        double[] odds = new double[SUCCESS_TYPES.length];
        writeProjectileOdds(type, defender, odds);
        return odds;
    }

    private static void writeProjectileOdds(AttackType type, NationState defender, double[] out) {
        Arrays.fill(out, 0d);
        double interceptChance = ProjectileDefenseMath.interceptionChance(type, defender);
        out[SuccessType.UTTER_FAILURE.ordinal()] = interceptChance;
        out[SuccessType.IMMENSE_TRIUMPH.ordinal()] = 1d - interceptChance;
    }

    private static StrengthPair computeStrengths(
            NationState attacker,
            NationState defender,
            AttackContext context,
            AttackType type,
            EngagementOptions options
    ) {
        return switch (type) {
            case GROUND -> new StrengthPair(
                    UnitEconomy.groundStrength(attacker, options.equipAttackerSoldiers(), context.defenderHasAirControl()),
                    UnitEconomy.groundStrength(defender, options.equipDefenderSoldiers(), context.attackerHasAirControl())
            );
            case AIRSTRIKE_INFRA, AIRSTRIKE_SOLDIER, AIRSTRIKE_TANK, AIRSTRIKE_MONEY,
                    AIRSTRIKE_SHIP, AIRSTRIKE_AIRCRAFT -> new StrengthPair(
                    UnitEconomy.airStrength(attacker),
                    UnitEconomy.airStrength(defender)
            );
            case NAVAL, NAVAL_INFRA, NAVAL_AIR, NAVAL_GROUND -> new StrengthPair(
                    UnitEconomy.navalStrength(attacker),
                    UnitEconomy.navalStrength(defender)
            );
            default -> new StrengthPair(1d, 1d);
        };
    }

    private static SuccessType argmax(double[] odds) {
        int best = 0;
        for (int i = 1; i < odds.length; i++) {
            if (odds[i] > odds[best]) {
                best = i;
            }
        }
        return SUCCESS_TYPES[best];
    }

    private static SuccessType sampleSuccess(double[] odds, RandomSource rng, long streamKey) {
        double roll = rng.nextDouble(streamKey);
        double cumulative = 0d;
        for (int i = 0; i < odds.length; i++) {
            cumulative += odds[i];
            if (roll < cumulative) {
                return SUCCESS_TYPES[i];
            }
        }
        return SUCCESS_TYPES[odds.length - 1];
    }

    private static double clampResistanceDelta(double value) {
        if (value < -100d) return -100d;
        if (value > 100d) return 100d;
        return value;
    }

    private static long streamKey(int attackerId, int defenderId, AttackType type) {
        long h = attackerId;
        h = h * 31L + defenderId;
        h = h * 31L + type.ordinal();
        return h;
    }

    @FunctionalInterface
    private interface CasualtyProvider {
        Map.Entry<Map<MilitaryUnit, Map.Entry<Integer, Integer>>,
                Map<MilitaryUnit, Map.Entry<Integer, Integer>>> casualties(SuccessType success);
    }

    private record StrengthPair(double attacker, double defender) {
    }

    private static final class DoubleBox {
        double value;
    }
}
