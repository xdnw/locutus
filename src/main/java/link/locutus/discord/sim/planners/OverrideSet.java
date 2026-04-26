package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.SimUnits;
import link.locutus.discord.sim.WarSlotRules;
import link.locutus.discord.sim.combat.NationCombatProfile;
import link.locutus.discord.sim.input.NationInit;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-nation override bundle applied once at {@link link.locutus.discord.sim.SimNation} construction.
 * After all overrides are folded in, planners recompute derived caps (maxOff, unitCap, etc.)
 * from the overridden state before calling {@link DBNationSnapshot#toNationInit()}.
 *
 * <p>Direct score override is not supported — override the inputs that drive score
 * (citiesOverride, nonInfraScoreBaseOverride) so the sim's incremental score recomputation
 * remains consistent.</p>
 *
 * <p>This class is immutable once built.</p>
 */
public final class OverrideSet {

    /** Active flag: TRUE=always active, FALSE=never active, AUTO=use ActivityProvider. */
    public enum ActiveOverride { TRUE, FALSE, AUTO }

    private final Map<Integer, ActiveOverride> activeOverride;
    private final Map<Integer, WarPolicy> policyOverride;
    private final Map<Integer, Integer> forceFreeOffSlots;
    private final Map<Integer, Integer> forceFreeDefSlots;
    private final Map<Integer, Integer> citiesOverride;
    private final Map<Integer, double[]> resourceOverride;
    private final Map<Integer, Double> nonInfraScoreBaseOverride;
    private final Map<Integer, Byte> resetHourOverride;
    private final Map<Integer, Map<MilitaryUnit, Integer>> unitOverride;
    private final Map<Integer, Integer> maxOffOverride;

    private OverrideSet(Builder b) {
        this.activeOverride = Map.copyOf(b.activeOverride);
        this.policyOverride = Map.copyOf(b.policyOverride);
        this.forceFreeOffSlots = Map.copyOf(b.forceFreeOffSlots);
        this.forceFreeDefSlots = Map.copyOf(b.forceFreeDefSlots);
        this.citiesOverride = Map.copyOf(b.citiesOverride);
        this.resourceOverride = copyResourceMap(b.resourceOverride);
        this.nonInfraScoreBaseOverride = Map.copyOf(b.nonInfraScoreBaseOverride);
        this.resetHourOverride = Map.copyOf(b.resetHourOverride);
        this.unitOverride = copyUnitMap(b.unitOverride);
        this.maxOffOverride = Map.copyOf(b.maxOffOverride);
    }

    private static Map<Integer, double[]> copyResourceMap(Map<Integer, double[]> src) {
        var result = new java.util.LinkedHashMap<Integer, double[]>(src.size());
        src.forEach((k, v) -> result.put(k, v.clone()));
        return java.util.Collections.unmodifiableMap(result);
    }

    private static Map<Integer, Map<MilitaryUnit, Integer>> copyUnitMap(Map<Integer, Map<MilitaryUnit, Integer>> src) {
        var result = new java.util.LinkedHashMap<Integer, Map<MilitaryUnit, Integer>>(src.size());
        src.forEach((k, v) -> result.put(k, Map.copyOf(v)));
        return java.util.Collections.unmodifiableMap(result);
    }

    /** An empty override set — no overrides for any nation. */
    public static final OverrideSet EMPTY = new Builder().build();

    // ---- Accessors -------------------------------------------------------

    public ActiveOverride activeOverride(int nationId) {
        return activeOverride.getOrDefault(nationId, ActiveOverride.AUTO);
    }

    public WarPolicy policyOverride(int nationId, WarPolicy defaultPolicy) {
        return policyOverride.getOrDefault(nationId, defaultPolicy);
    }

    /**
     * Effective free offensive slots after applying {@code forceFreeOffSlots}.
     * Semantics: {@code offSlotsUsed = max(0, maxOff − forceFreeOff)}.
     */
    public int effectiveFreeOff(DBNationSnapshot snap) {
        int forced = forceFreeOffSlots.getOrDefault(snap.nationId(), -1);
        if (forced >= 0) {
            return Math.min(forced, effectiveMaxOff(snap));
        }
        return snap.rawFreeOff();
    }

    public int effectiveMaxOff(DBNationSnapshot snap) {
        return maxOffOverride.getOrDefault(snap.nationId(), snap.maxOff());
    }

    public int effectiveFreeDef(DBNationSnapshot snap) {
        int forced = forceFreeDefSlots.getOrDefault(snap.nationId(), -1);
        if (forced >= 0) {
            return WarSlotRules.clampFreeDefensiveSlots(forced);
        }
        return snap.rawFreeDef();
    }

    public int effectiveCities(DBNationSnapshot snap) {
        return citiesOverride.getOrDefault(snap.nationId(), snap.cities());
    }

    public double effectiveNonInfraScoreBase(DBNationSnapshot snap) {
        return nonInfraScoreBaseOverride.getOrDefault(snap.nationId(), snap.nonInfraScoreBase());
    }

    /**
     * Returns the overridden unit count for the given nation/unit if provided,
     * otherwise the snapshot count.
     */
    public int overrideUnitCount(DBNationSnapshot snap, MilitaryUnit unit) {
        Map<MilitaryUnit, Integer> byUnit = unitOverride.get(snap.nationId());
        if (byUnit == null) {
            return snap.unit(unit);
        }
        Integer overridden = byUnit.get(unit);
        return overridden == null ? snap.unit(unit) : overridden;
    }

    /**
     * Builds an overridden {@link NationInit} from the snapshot, folding all per-nation overrides.
     * The resulting init can be passed to {@link link.locutus.discord.sim.SimWorld#addNation(NationInit)}.
     */
    public NationInit applyTo(DBNationSnapshot snap) {
        int nationId = snap.nationId();
        WarPolicy policy = policyOverride(nationId, snap.warPolicy());
        double[] resources = resourceOverride.containsKey(nationId)
                ? resourceOverride.get(nationId).clone()
                : snap.resources();
        int maxOff = effectiveMaxOff(snap);
        double nonInfraScoreBase = effectiveNonInfraScoreBase(snap);
        double[] cityInfra = snap.cityInfra();
        byte resetHour = resetHourOverride.getOrDefault(nationId, snap.resetHourUtc());

        // Apply unit overrides (if any): these will be set on SimNation after construction
        // by the planner; NationInit doesn't carry unit state (units start at 0 in the init).
        // The planner calls SimNation.setUnitCount for each override after addNation.

        int teamId = snap.teamId();
    NationCombatProfile combatProfile = policy == snap.warPolicy()
        ? snap.combatProfile()
        : NationCombatProfile.derived(
            policy,
            snap.projectBits(),
            snap.researchBits(),
            policy == WarPolicy.BLITZKRIEG
        );
        return new NationInit(
            nationId,
            teamId,
            policy,
            resources,
            nonInfraScoreBase,
            cityInfra,
            maxOff,
            resetHour,
            snap.projectBits(),
            snap.citySpecialistProfiles(),
            combatProfile
        );
    }

    /**
     * Returns a new snapshot with persistent input overrides baked into authoritative planner state.
     * Transient slot/activity overrides remain external and should still be queried through this set.
     */
    public DBNationSnapshot applyToSnapshot(DBNationSnapshot snap) {
        int nationId = snap.nationId();
        double[] cityInfra = snap.cityInfra();
        double nonInfraScoreBase = effectiveNonInfraScoreBase(snap);
        DBNationSnapshot.Builder builder = snap.toBuilder()
            .cities(effectiveCities(snap))
            .maxOff(effectiveMaxOff(snap))
            .warPolicy(policyOverride(nationId, snap.warPolicy()))
            .resources(resourceOverride.containsKey(nationId)
                ? resourceOverride.get(nationId).clone()
                : snap.resources())
            .nonInfraScoreBase(nonInfraScoreBase)
            .resetHourUtc(resetHourOverride.getOrDefault(nationId, snap.resetHourUtc()));
        double score = nonInfraScoreBase;
        for (double infra : cityInfra) {
            score += infra / 40.0;
        }
        for (MilitaryUnit unit : MilitaryUnit.values) {
            int count = overrideUnitCount(snap, unit);
            builder.unit(unit, count);
            if (count > 0 && SimUnits.isPurchasable(unit)) {
                score += unit.getScore(count);
            }
        }
        builder.score(score);
        return builder.build();
    }

    // ---- Builder ----------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final Map<Integer, ActiveOverride> activeOverride = new java.util.LinkedHashMap<>();
        private final Map<Integer, WarPolicy> policyOverride = new java.util.LinkedHashMap<>();
        private final Map<Integer, Integer> forceFreeOffSlots = new java.util.LinkedHashMap<>();
        private final Map<Integer, Integer> forceFreeDefSlots = new java.util.LinkedHashMap<>();
        private final Map<Integer, Integer> citiesOverride = new java.util.LinkedHashMap<>();
        private final Map<Integer, double[]> resourceOverride = new java.util.LinkedHashMap<>();
        private final Map<Integer, Double> nonInfraScoreBaseOverride = new java.util.LinkedHashMap<>();
        private final Map<Integer, Byte> resetHourOverride = new java.util.LinkedHashMap<>();
        private final Map<Integer, Map<MilitaryUnit, Integer>> unitOverride = new java.util.LinkedHashMap<>();
        private final Map<Integer, Integer> maxOffOverride = new java.util.LinkedHashMap<>();

        public Builder active(int nationId, ActiveOverride v) { activeOverride.put(nationId, v); return this; }
        public Builder policy(int nationId, WarPolicy v) { policyOverride.put(nationId, Objects.requireNonNull(v)); return this; }
        /** Force this many free offensive slots. Semantics: offSlotsUsed = max(0, maxOff − forced). */
        public Builder forceFreeOff(int nationId, int freeSlots) { forceFreeOffSlots.put(nationId, freeSlots); return this; }
        /** Convenience overload: assume all 5 base off slots are free. */
        public Builder forceFreeOff(int nationId) { return forceFreeOff(nationId, 5); }
        public Builder forceFreeDefSlots(int nationId, int freeSlots) { forceFreeDefSlots.put(nationId, freeSlots); return this; }
        public Builder cities(int nationId, int v) { citiesOverride.put(nationId, v); return this; }
        public Builder resources(int nationId, double[] v) { resourceOverride.put(nationId, v.clone()); return this; }
        public Builder nonInfraScoreBase(int nationId, double v) { nonInfraScoreBaseOverride.put(nationId, v); return this; }
        public Builder resetHour(int nationId, byte v) { resetHourOverride.put(nationId, v); return this; }
        public Builder units(int nationId, Map<MilitaryUnit, Integer> v) { unitOverride.put(nationId, new EnumMap<>(v)); return this; }
        public Builder maxOff(int nationId, int v) { maxOffOverride.put(nationId, v); return this; }

        public OverrideSet build() { return new OverrideSet(this); }
    }
}
