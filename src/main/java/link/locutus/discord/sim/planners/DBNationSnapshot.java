package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.sim.combat.NationCombatProfile;
import link.locutus.discord.sim.combat.SpecialistCityProfile;
import link.locutus.discord.sim.combat.UnitEconomy;
import link.locutus.discord.sim.WarSlotRules;
import link.locutus.discord.sim.input.NationInit;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable snapshot of a nation at the time of planner entry.
 * All planners accept {@code Collection<DBNationSnapshot>} rather than live {@code DBNation} objects
 * so planner logic is decoupled from the DB and tests can use synthetic data.
 *
 * <p>Build via {@link #of(DBNation)} or {@link #synthetic} for tests.</p>
 */
public final class DBNationSnapshot {
    private static final AttackType[] ATTACK_TYPES = AttackType.values;
    private static final MilitaryUnit[] SIM_PURCHASABLE_UNITS = {
            MilitaryUnit.SOLDIER,
            MilitaryUnit.TANK,
            MilitaryUnit.AIRCRAFT,
            MilitaryUnit.SHIP,
            MilitaryUnit.MISSILE,
            MilitaryUnit.NUKE,
            MilitaryUnit.SPIES
    };

    private final int nationId;
    private final int allianceId;
    private final int teamId;
    private final double score;
    private final int cities;
    private final int currentOffensiveWars;
    private final int currentDefensiveWars;
    private final int maxOff;
    private final WarPolicy warPolicy;
    private final EnumMap<MilitaryUnit, Integer> units;
    private final double[] resources;
    private final double nonInfraScoreBase;
    private final double[] cityInfra;
    private final SpecialistCityProfile[] citySpecialistProfiles;
    private final double totalInfra;
    private final byte resetHourUtc;
    private final boolean resetHourUtcFallback;
    private final Set<Integer> activeOpponentNationIds;
    private final int policyCooldownTurnsRemaining;
    private final int beigeTurns;
    private final EnumMap<MilitaryUnit, Integer> unitBuysToday;
    private final EnumMap<MilitaryUnit, Integer> pendingBuysNextTurn;
    private final int researchBits;
    private final long projectBits;
    private final boolean blitzkriegActive;
    private final double[] infraAttackModifiers;
    private final double[] infraDefendModifiers;
    private final double groundLooterModifier;
    private final double nonGroundLooterModifier;
    private final double lootModifier;

    private DBNationSnapshot(Builder b) {
        this.nationId = b.nationId;
        this.allianceId = b.allianceId;
        this.teamId = b.teamId;
        this.score = b.score;
        this.cities = b.cities;
        this.currentOffensiveWars = b.currentOffensiveWars;
        this.currentDefensiveWars = b.currentDefensiveWars;
        this.maxOff = b.maxOff;
        this.warPolicy = Objects.requireNonNull(b.warPolicy, "warPolicy");
        this.units = new EnumMap<>(b.units);
        this.resources = b.resources.clone();
        this.nonInfraScoreBase = b.nonInfraScoreBase;
        this.cityInfra = b.cityInfra.clone();
        this.citySpecialistProfiles = b.citySpecialistProfiles.clone();
        this.totalInfra = totalInfra(this.cityInfra);
        this.resetHourUtc = b.resetHourUtc;
        this.resetHourUtcFallback = b.resetHourUtcFallback;
        this.activeOpponentNationIds = Set.copyOf(b.activeOpponentNationIds);
        this.policyCooldownTurnsRemaining = b.policyCooldownTurnsRemaining;
        this.beigeTurns = b.beigeTurns;
        this.unitBuysToday = new EnumMap<>(b.unitBuysToday);
        this.pendingBuysNextTurn = new EnumMap<>(b.pendingBuysNextTurn);
        this.researchBits = b.researchBits;
        this.projectBits = b.projectBits;
        this.blitzkriegActive = b.blitzkriegActive;
        this.infraAttackModifiers = b.infraAttackModifiers.clone();
        this.infraDefendModifiers = b.infraDefendModifiers.clone();
        this.groundLooterModifier = b.groundLooterModifier;
        this.nonGroundLooterModifier = b.nonGroundLooterModifier;
        this.lootModifier = b.lootModifier;
    }

    /** Snapshot from a live DBNation. Does not seed current-day unit buys unless explicitly requested in bulk. */
    public static DBNationSnapshot of(DBNation nation) {
        Objects.requireNonNull(nation, "nation");
        return fromNation(nation, Map.of());
    }

    /** Snapshot list from live DBNations with caller-owned optional current-day unit-buy seeding. */
    public static java.util.List<DBNationSnapshot> of(
            java.util.Collection<DBNation> nations,
            Map<Integer, Map<MilitaryUnit, Integer>> unitBuysTodayByNationId
    ) {
        Objects.requireNonNull(nations, "nations");
        Map<Integer, Map<MilitaryUnit, Integer>> effectiveBuys =
                unitBuysTodayByNationId == null ? Map.of() : unitBuysTodayByNationId;
        java.util.List<DBNationSnapshot> snapshots = new java.util.ArrayList<>(nations.size());
        for (DBNation nation : nations) {
            if (nation == null) {
                continue;
            }
            snapshots.add(fromNation(nation, effectiveBuys.getOrDefault(nation.getNation_id(), Map.of())));
        }
        return snapshots;
    }

    private static DBNationSnapshot fromNation(DBNation nation, Map<MilitaryUnit, Integer> unitBuysToday) {
        Objects.requireNonNull(nation, "nation");
        Builder b = new Builder(nation.getNation_id());
        b.allianceId(nation.getAlliance_id());
        b.teamId(nation.getAlliance_id());
        b.score(nation.getScore());
        b.cities(nation.getCities());
        b.currentOffensiveWars(nation.getOff());
        b.currentDefensiveWars(nation.getDef());
        b.warPolicy(nation.getWarPolicy());
        b.beigeTurns(nation.getBeigeTurns());
        long projectBits = nation.data()._projects();
        int researchBits = nation.data()._researchBits();
        boolean blitzkriegActive = nation.isBlitzkrieg();
        b.researchBits(researchBits);
        b.projectBits(projectBits);
        b.blitzkriegActive(blitzkriegActive);
        var cityEntries = nation._getCitiesV3().entrySet().stream()
            .sorted(Comparator.comparingInt(Map.Entry::getKey))
            .toList();
        double[] cityInfra = new double[cityEntries.size()];
        SpecialistCityProfile[] cityProfiles = new SpecialistCityProfile[cityEntries.size()];
        for (int i = 0; i < cityEntries.size(); i++) {
            var city = cityEntries.get(i).getValue();
            cityInfra[i] = city.getInfra();
            cityProfiles[i] = SpecialistCityProfile.fromCity(city, nation::hasProject);
        }
        if (cityInfra.length == 0 && nation.getCities() > 0) {
            cityInfra = new double[nation.getCities()];
            java.util.Arrays.fill(cityInfra, nation.getAvg_infra());
            cityProfiles = new SpecialistCityProfile[cityInfra.length];
            java.util.Arrays.fill(cityProfiles, SpecialistCityProfile.DEFAULT);
        }
        b.cityInfra(cityInfra);
        b.citySpecialistProfiles(cityProfiles);
        double[] infraAttack = new double[ATTACK_TYPES.length];
        double[] infraDefend = new double[ATTACK_TYPES.length];
        for (AttackType type : ATTACK_TYPES) {
            infraAttack[type.ordinal()] = nation.infraAttackModifier(type);
            infraDefend[type.ordinal()] = nation.infraDefendModifier(type);
        }
        b.combatProfile(new NationCombatProfile(
            researchBits,
            blitzkriegActive,
            infraAttack,
            infraDefend,
            nation.looterModifier(true),
            nation.looterModifier(false),
            nation.lootModifier()
        ));
        for (MilitaryUnit unit : MilitaryUnit.values) {
            b.unit(unit, nation.getUnits(unit));
        }
        for (MilitaryUnit unit : SIM_PURCHASABLE_UNITS) {
            int boughtToday = unitBuysToday.getOrDefault(unit, 0);
            if (boughtToday > 0) {
                b.unitBoughtToday(unit, boughtToday);
            }
        }
        for (DBWar war : nation.getActiveWars()) {
            int opponentId = war.getAttacker_id() == nation.getNation_id()
                    ? war.getDefender_id()
                    : war.getAttacker_id();
            if (opponentId > 0) {
                b.activeOpponentNationId(opponentId);
            }
        }
        int dcTurn = nation.getDc_turn();
        if (dcTurn >= 0 && dcTurn < 12) {
            b.resetHourUtc((byte) (dcTurn * 2));
            b.resetHourUtcFallback(false);
        } else {
            b.resetHourUtc((byte) 0);
            b.resetHourUtcFallback(true);
        }
        return b.build();
    }

    /** Constructs a synthetic snapshot for tests / offline planning. */
    public static Builder synthetic(int nationId) {
        return new Builder(nationId);
    }

    public int nationId() { return nationId; }
    public int allianceId() { return allianceId; }
    public int teamId() { return teamId; }
    public double score() { return score; }
    public int cities() { return cities; }
    public int currentOffensiveWars() { return currentOffensiveWars; }
    public int currentDefensiveWars() { return currentDefensiveWars; }
    public int maxOff() { return maxOff; }
    public WarPolicy warPolicy() { return warPolicy; }
    public int unit(MilitaryUnit u) { return units.getOrDefault(u, 0); }
    public double[] resources() { return resources.clone(); }
    /** Copies resources into caller-owned storage without allocating a clone. */
    public void copyResourcesInto(double[] target, int targetOffset) {
        System.arraycopy(resources, 0, target, targetOffset, resources.length);
    }
    public double resource(ResourceType type) { return resources[type.ordinal()]; }
    public double nonInfraScoreBase() { return nonInfraScoreBase; }
    public double[] cityInfra() { return cityInfra.clone(); }
    /** Returns the city-infra length without allocating a cloned array. */
    public int cityInfraCount() { return cityInfra.length; }
    /** Copies city infra into caller-owned storage without allocating a clone. */
    public void copyCityInfraInto(double[] target, int targetOffset) {
        System.arraycopy(cityInfra, 0, target, targetOffset, cityInfra.length);
    }
    public SpecialistCityProfile[] citySpecialistProfiles() { return citySpecialistProfiles.clone(); }
    public byte resetHourUtc() { return resetHourUtc; }
    public boolean resetHourUtcFallback() { return resetHourUtcFallback; }
    public Set<Integer> activeOpponentNationIds() { return activeOpponentNationIds; }
    /** Returns whether this snapshot is currently involved in any active war. */
    public boolean hasActiveWars() {
        return currentOffensiveWars > 0 || currentDefensiveWars > 0 || !activeOpponentNationIds.isEmpty();
    }
    public int policyCooldownTurnsRemaining() { return policyCooldownTurnsRemaining; }
    public int beigeTurns() { return beigeTurns; }
    public int unitsBoughtToday(MilitaryUnit unit) { return unitBuysToday.getOrDefault(unit, 0); }
    public int pendingBuysNextTurn(MilitaryUnit unit) { return pendingBuysNextTurn.getOrDefault(unit, 0); }
    public int dailyBuyCap(MilitaryUnit unit) {
        return UnitEconomy.maxBuyPerDayFor(
                cities,
                unit,
                this::hasProject,
                research -> research.getLevel(researchBits),
                beigeTurns,
                hasActiveWars()
        );
    }
    public int researchBits() { return researchBits; }
    public long projectBits() { return projectBits; }
    public boolean blitzkriegActive() { return blitzkriegActive; }
    public double infraAttackModifier(AttackType type) { return infraAttackModifiers[type.ordinal()]; }
    public double infraDefendModifier(AttackType type) { return infraDefendModifiers[type.ordinal()]; }
    public double looterModifier(boolean ground) { return ground ? groundLooterModifier : nonGroundLooterModifier; }
    public double lootModifier() { return lootModifier; }
    public NationCombatProfile combatProfile() {
        return new NationCombatProfile(
                researchBits,
                blitzkriegActive,
                infraAttackModifiers,
                infraDefendModifiers,
                groundLooterModifier,
                nonGroundLooterModifier,
                lootModifier
        );
    }

    double cityInfraAt(int cityIndex) { return cityInfra[cityIndex]; }
    SpecialistCityProfile citySpecialistProfileAt(int cityIndex) { return citySpecialistProfiles[cityIndex]; }
    double[] cityInfraRaw() { return cityInfra; }
    SpecialistCityProfile[] citySpecialistProfilesRaw() { return citySpecialistProfiles; }
    double totalInfraRaw() { return totalInfra; }
    boolean hasProject(Project project) { return (projectBits & (1L << project.ordinal())) != 0L; }

    /**
     * Free offensive slots ignoring any forceFreeOff override.
     * Use {@link OverrideSet#effectiveFreeOff(DBNationSnapshot)} to apply overrides.
     */
    public int rawFreeOff() {
        return Math.max(0, maxOff - currentOffensiveWars);
    }

    /** Free defensive slots ignoring any forceFreeDef override. */
    public int rawFreeDef() {
        return WarSlotRules.freeDefensiveSlots(currentDefensiveWars);
    }

    /**
     * Builds the {@link NationInit} DTO consumed by {@link link.locutus.discord.sim.SimNation}.
     * Applies no overrides; use {@link OverrideSet} before calling this.
     */
    public NationInit toNationInit() {
        return new NationInit(
                nationId,
                teamId,
                warPolicy,
                resources,
                nonInfraScoreBase,
                cityInfra,
                maxOff,
                resetHourUtc,
                projectBits,
                citySpecialistProfiles,
                combatProfile()
        );
    }

    public Builder toBuilder() {
        Builder builder = new Builder(nationId)
                .allianceId(allianceId)
                .teamId(teamId)
                .score(score)
                .cities(cities)
                .currentOffensiveWars(currentOffensiveWars)
                .currentDefensiveWars(currentDefensiveWars)
                .maxOff(maxOff)
                .warPolicy(warPolicy)
                .resources(resources)
                .nonInfraScoreBase(nonInfraScoreBase)
                .cityInfra(cityInfra)
                .citySpecialistProfiles(citySpecialistProfiles)
                .resetHourUtc(resetHourUtc)
                .resetHourUtcFallback(resetHourUtcFallback)
                .activeOpponentNationIds(activeOpponentNationIds)
                .policyCooldownTurnsRemaining(policyCooldownTurnsRemaining)
                .beigeTurns(beigeTurns)
                .researchBits(researchBits)
                .projectBits(projectBits)
                .combatProfile(combatProfile());
        for (Map.Entry<MilitaryUnit, Integer> entry : unitBuysToday.entrySet()) {
            builder.unitBoughtToday(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<MilitaryUnit, Integer> entry : units.entrySet()) {
            builder.unit(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<MilitaryUnit, Integer> entry : pendingBuysNextTurn.entrySet()) {
            builder.pendingBuyNextTurn(entry.getKey(), entry.getValue());
        }
        return builder;
    }

    private static double totalInfra(double[] cityInfra) {
        double total = 0d;
        for (double infra : cityInfra) {
            total += infra;
        }
        return total;
    }

    public static final class Builder {
        private final int nationId;
        private int allianceId;
        private int teamId;
        private double score;
        private int cities;
        private int currentOffensiveWars;
        private int currentDefensiveWars;
        private int maxOff = -1;
        private WarPolicy warPolicy = WarPolicy.ATTRITION;
        private final EnumMap<MilitaryUnit, Integer> units = new EnumMap<>(MilitaryUnit.class);
        private double[] resources = new double[ResourceType.values.length];
        private double nonInfraScoreBase;
        private double[] cityInfra = new double[0];
        private SpecialistCityProfile[] citySpecialistProfiles = new SpecialistCityProfile[0];
        private byte resetHourUtc;
        private boolean resetHourUtcFallback;
        private final Set<Integer> activeOpponentNationIds = new LinkedHashSet<>();
        private int policyCooldownTurnsRemaining;
        private int beigeTurns;
        private final EnumMap<MilitaryUnit, Integer> unitBuysToday = new EnumMap<>(MilitaryUnit.class);
        private final EnumMap<MilitaryUnit, Integer> pendingBuysNextTurn = new EnumMap<>(MilitaryUnit.class);
        private int researchBits;
        private long projectBits;
        private boolean blitzkriegActive;
        private double[] infraAttackModifiers;
        private double[] infraDefendModifiers;
        private double groundLooterModifier = 1.0;
        private double nonGroundLooterModifier = 1.0;
        private double lootModifier = 1.0;
        private boolean combatProfileExplicit;

        public Builder(int nationId) {
            if (nationId <= 0) throw new IllegalArgumentException("nationId must be > 0");
            this.nationId = nationId;
            this.allianceId = nationId;
            this.teamId = nationId;
            this.infraAttackModifiers = new double[ATTACK_TYPES.length];
            this.infraDefendModifiers = new double[ATTACK_TYPES.length];
            Arrays.fill(this.infraAttackModifiers, 1.0);
            Arrays.fill(this.infraDefendModifiers, 1.0);
        }

        public Builder allianceId(int v) { this.allianceId = v; return this; }
        public Builder teamId(int v) { this.teamId = v; return this; }
        public Builder score(double v) { this.score = v; return this; }
        public Builder cities(int v) { this.cities = v; return this; }
        public Builder currentOffensiveWars(int v) { this.currentOffensiveWars = v; return this; }
        public Builder currentDefensiveWars(int v) { this.currentDefensiveWars = v; return this; }
        public Builder maxOff(int v) { this.maxOff = v; return this; }
        public Builder warPolicy(WarPolicy v) { this.warPolicy = v; return this; }
        public Builder unit(MilitaryUnit u, int count) { units.put(u, count); return this; }
        public Builder resources(double[] v) { this.resources = v.clone(); return this; }
        public Builder resource(ResourceType t, double v) { this.resources[t.ordinal()] = v; return this; }
        public Builder nonInfraScoreBase(double v) { this.nonInfraScoreBase = v; return this; }
        public Builder cityInfra(double[] v) { this.cityInfra = v.clone(); return this; }
        public Builder citySpecialistProfiles(SpecialistCityProfile[] values) {
            this.citySpecialistProfiles = values == null ? new SpecialistCityProfile[0] : values.clone();
            return this;
        }
        public Builder resetHourUtc(byte v) { this.resetHourUtc = v; return this; }
        public Builder resetHourUtcFallback(boolean v) { this.resetHourUtcFallback = v; return this; }
        public Builder policyCooldownTurnsRemaining(int turns) {
            if (turns < 0) {
                throw new IllegalArgumentException("policyCooldownTurnsRemaining must be >= 0");
            }
            this.policyCooldownTurnsRemaining = turns;
            return this;
        }
        public Builder beigeTurns(int turns) {
            if (turns < 0) {
                throw new IllegalArgumentException("beigeTurns must be >= 0");
            }
            this.beigeTurns = turns;
            return this;
        }
        public Builder unitBoughtToday(MilitaryUnit unit, int amount) {
            Objects.requireNonNull(unit, "unit");
            if (amount < 0) {
                throw new IllegalArgumentException("unit buy amount must be >= 0");
            }
            if (amount == 0) {
                unitBuysToday.remove(unit);
            } else {
                unitBuysToday.put(unit, amount);
            }
            return this;
        }
        public Builder pendingBuyNextTurn(MilitaryUnit unit, int amount) {
            Objects.requireNonNull(unit, "unit");
            if (amount < 0) {
                throw new IllegalArgumentException("pending buy amount must be >= 0");
            }
            if (amount == 0) {
                pendingBuysNextTurn.remove(unit);
            } else {
                pendingBuysNextTurn.put(unit, amount);
            }
            return this;
        }
        public Builder activeOpponentNationIds(Set<Integer> v) {
            this.activeOpponentNationIds.clear();
            this.activeOpponentNationIds.addAll(v);
            return this;
        }
        public Builder activeOpponentNationId(int nationId) {
            if (nationId > 0) {
                this.activeOpponentNationIds.add(nationId);
            }
            return this;
        }
        public Builder researchBits(int v) { this.researchBits = v; return this; }
        public Builder projectBits(long v) { this.projectBits = v; return this; }
        public Builder blitzkriegActive(boolean v) {
            this.blitzkriegActive = v;
            this.combatProfileExplicit = true;
            return this;
        }
        public Builder combatProfile(NationCombatProfile profile) {
            NationCombatProfile validated = Objects.requireNonNull(profile, "profile");
            this.researchBits = validated.researchBits();
            this.blitzkriegActive = validated.blitzkriegActive();
            this.infraAttackModifiers = validated.infraAttackModifiers();
            this.infraDefendModifiers = validated.infraDefendModifiers();
            this.groundLooterModifier = validated.groundLooterModifier();
            this.nonGroundLooterModifier = validated.nonGroundLooterModifier();
            this.lootModifier = validated.lootModifier();
            this.combatProfileExplicit = true;
            return this;
        }
        public Builder looterModifiers(double ground, double nonGround) {
            this.groundLooterModifier = ground;
            this.nonGroundLooterModifier = nonGround;
            this.combatProfileExplicit = true;
            return this;
        }
        public Builder lootModifier(double value) {
            this.lootModifier = value;
            this.combatProfileExplicit = true;
            return this;
        }
        public Builder infraModifiers(double[] attackByType, double[] defendByType) {
            if (attackByType.length != ATTACK_TYPES.length || defendByType.length != ATTACK_TYPES.length) {
                throw new IllegalArgumentException("infra modifier arrays must match AttackType.values length");
            }
            this.infraAttackModifiers = attackByType.clone();
            this.infraDefendModifiers = defendByType.clone();
            this.combatProfileExplicit = true;
            return this;
        }

        public DBNationSnapshot build() {
            if (citySpecialistProfiles.length == 0) {
                citySpecialistProfiles = SpecialistCityProfile.defaults(cityInfra.length);
            } else if (citySpecialistProfiles.length != cityInfra.length) {
                throw new IllegalArgumentException("citySpecialistProfiles must match cityInfra length");
            }
            if (maxOff < 0) {
                maxOff = WarSlotRules.offensiveSlotCap(projectBits);
            }
            if (!combatProfileExplicit) {
                applyDerivedCombatProfile();
            }
            return new DBNationSnapshot(this);
        }

        private void applyDerivedCombatProfile() {
            NationCombatProfile profile = NationCombatProfile.derived(
                    warPolicy,
                    projectBits,
                    researchBits,
                    warPolicy == WarPolicy.BLITZKRIEG
            );
            this.blitzkriegActive = profile.blitzkriegActive();
            this.groundLooterModifier = profile.groundLooterModifier();
            this.nonGroundLooterModifier = profile.nonGroundLooterModifier();
            this.lootModifier = profile.lootModifier();
            this.infraAttackModifiers = profile.infraAttackModifiers();
            this.infraDefendModifiers = profile.infraDefendModifiers();
        }
    }

}
