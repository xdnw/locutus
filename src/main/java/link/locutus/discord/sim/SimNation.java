package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.sim.combat.NationCombatProfile;
import link.locutus.discord.sim.combat.SpecialistCityProfile;
import link.locutus.discord.sim.combat.WarOutcomeMath;
import link.locutus.discord.sim.input.NationInit;
import link.locutus.discord.sim.combat.state.CombatCityView;
import link.locutus.discord.sim.combat.state.CombatantView;
import link.locutus.discord.sim.combat.state.CombatantSnapshots;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class SimNation implements CombatantView {
    private static final Map.Entry<Integer, Integer> ZERO_DAMAGE_RANGE = Map.entry(0, 0);

    private SimNationArrayStore storage;
    private int nationIndex;
    private NationScoreListener scoreListener;
    private Collection<? extends CombatCityView> combatCityViews;

    public SimNation(int nationId, WarPolicy policy) {
        this(NationInit.basic(nationId, policy));
    }

    public SimNation(int nationId, WarPolicy policy, double warchest) {
        this(NationInit.moneyOnly(
                nationId,
                policy,
                warchest,
                0d,
                new double[0],
                NationInit.DEFAULT_MAX_OFF_SLOTS,
                NationInit.DEFAULT_RESET_HOUR_UTC
        ));
    }

    public SimNation(int nationId, WarPolicy policy, double warchest, double nonInfraScoreBase, int maxOffSlots) {
        this(NationInit.moneyOnly(
                nationId,
                policy,
                warchest,
                nonInfraScoreBase,
                new double[0],
                maxOffSlots,
                NationInit.DEFAULT_RESET_HOUR_UTC
        ));
    }

    public SimNation(int nationId, WarPolicy policy, double warchest, double nonInfraScoreBase, int maxOffSlots, byte resetHourUtc) {
        this(NationInit.moneyOnly(nationId, policy, warchest, nonInfraScoreBase, new double[0], maxOffSlots, resetHourUtc));
    }

    public SimNation(int nationId, WarPolicy policy, double[] resources, double nonInfraScoreBase, int maxOffSlots) {
        this(new NationInit(
                nationId,
                nationId,
                policy,
                resources,
                nonInfraScoreBase,
                new double[0],
                maxOffSlots,
                NationInit.DEFAULT_RESET_HOUR_UTC
        ));
    }

    public SimNation(int nationId, WarPolicy policy, double[] resources, double nonInfraScoreBase, int maxOffSlots, byte resetHourUtc) {
        this(new NationInit(nationId, nationId, policy, resources, nonInfraScoreBase, new double[0], maxOffSlots, resetHourUtc));
    }

    // Primary constructor with explicit per-city infra.
    // nonInfraScoreBase captures static score from cities, land, research, projects — not infra or units.
    // cityInfra is per-city infra; length must equal the number of cities for this nation.
    // Score from infra = sum(cityInfra) / 40.0 (PnW formula).
    public SimNation(int nationId, WarPolicy policy, double[] resources,
                     double nonInfraScoreBase, double[] cityInfra, int maxOffSlots) {
        this(new NationInit(
                nationId,
                nationId,
                policy,
                resources,
                nonInfraScoreBase,
                cityInfra,
                maxOffSlots,
                NationInit.DEFAULT_RESET_HOUR_UTC
        ));
    }

    public SimNation(int nationId, WarPolicy policy, double[] resources,
                     double nonInfraScoreBase, double[] cityInfra, int maxOffSlots, byte resetHourUtc) {
        this(new NationInit(nationId, nationId, policy, resources, nonInfraScoreBase, cityInfra, maxOffSlots, resetHourUtc));
    }

    public SimNation(NationInit init) {
        NationInit validated = Objects.requireNonNull(init, "init");
        this.storage = SimNationArrayStore.standalone(SimNationSnapshot.initial(validated));
        this.nationIndex = 0;
    }

    SimNation(SimNationArrayStore storage, int nationIndex) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.nationIndex = nationIndex;
    }

    void bind(SimNationArrayStore storage, int nationIndex) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.nationIndex = nationIndex;
        this.combatCityViews = null;
    }

    void setScoreListener(NationScoreListener scoreListener) {
        this.scoreListener = scoreListener;
    }

    SimNationSnapshot snapshot() {
        return storage.snapshot(nationIndex);
    }

    public int nationId() {
        return storage.nationIds[nationIndex];
    }

    public int teamId() {
        return storage.teamIds[nationIndex];
    }

    public WarPolicy policy() {
        return storage.policies[nationIndex];
    }

    public int policyCooldownTurnsRemaining() {
        return storage.policyCooldownTurnsRemaining[nationIndex];
    }

    public int beigeTurns() {
        return storage.beigeTurns[nationIndex];
    }

    @Deprecated
    public double warchest() {
        return resource(ResourceType.MONEY);
    }

    public double resource(ResourceType type) {
        return storage.resourcesFlat[storage.resourceBase(nationIndex) + Objects.requireNonNull(type, "type").ordinal()];
    }

    public double[] resources() {
        return storage.copyResources(nationIndex);
    }

    public double convertedResources() {
        return ResourceType.convertedTotal(resources());
    }

    public int maxOffSlots() {
        return NationCapacityRules.maxOffSlots(storage.maxOffSlotOverrides[nationIndex], projectBits());
    }

    public byte resetHourUtc() {
        return storage.resetHoursUtc[nationIndex];
    }

    public int dayPhaseTurn() {
        return storage.dayPhaseTurns[nationIndex];
    }

    public int offSlotsUsed() {
        return storage.offSlotsUsed[nationIndex];
    }

    public int defSlotsUsed() {
        return storage.defSlotsUsed[nationIndex];
    }

    public int freeOffensiveSlots() {
        return Math.max(0, maxOffSlots() - offSlotsUsed());
    }

    public int freeDefensiveSlots() {
        return WarSlotRules.freeDefensiveSlots(defSlotsUsed());
    }

    public int cities() {
        return storage.cityCounts[nationIndex];
    }

    public double[] cityInfra() {
        return storage.copyCityInfra(nationIndex);
    }

    public double totalInfra() {
        double sum = 0;
        int cityOffset = storage.cityInfraOffsets[nationIndex];
        int cityCount = storage.cityCounts[nationIndex];
        for (int i = 0; i < cityCount; i++) {
            sum += storage.cityInfraFlat[cityOffset + i];
        }
        return sum;
    }

    public double nonInfraScoreBase() {
        return storage.nonInfraScoreBase[nationIndex];
    }

    @Deprecated
    public double scoreBase() {
        return nonInfraScoreBase();
    }

    public double score() {
        return storage.scores[nationIndex];
    }

    public int unitsBoughtToday(MilitaryUnit unit) {
        return storage.unitBuysTodayFlat[storage.unitBase(nationIndex) + requirePurchasableIndex(unit)];
    }

    public int pendingBuys(MilitaryUnit unit) {
        return storage.pendingBuysNextTurnFlat[storage.unitBase(nationIndex) + requirePurchasableIndex(unit)];
    }

    public int units(MilitaryUnit unit) {
        return storage.unitsFlat[storage.unitBase(nationIndex) + requirePurchasableIndex(unit)];
    }

    @Override
    public int getUnits(MilitaryUnit unit) {
        if (unit == MilitaryUnit.INFRASTRUCTURE) {
            return (int) Math.round(totalInfra());
        }
        if (unit == MilitaryUnit.MONEY) {
            return (int) Math.round(resource(ResourceType.MONEY));
        }
        if (!SimUnits.isPurchasable(unit)) {
            return 0;
        }
        return units(unit);
    }

    public int dailyBuyCap(MilitaryUnit unit) {
        return NationCapacityRules.dailyBuyCap(
                storage.dailyBuyCapOverridesFlat[storage.unitBase(nationIndex) + requirePurchasableIndex(unit)],
                cities(),
                unit,
                this::hasProject,
                researchBits(),
                beigeTurns(),
                hasActiveWars()
        );
    }

    public int unitCap(MilitaryUnit unit) {
        int cityOffset = storage.cityInfraOffsets[nationIndex];
        return NationCapacityRules.unitCap(
                storage.unitCapOverridesFlat[storage.unitBase(nationIndex) + requirePurchasableIndex(unit)],
                unit,
                storage.citySpecialistProfilesFlat,
                storage.cityInfraFlat,
                cityOffset,
                storage.cityCounts[nationIndex],
                this::hasProject,
                researchBits()
        );
    }

    @Override
    public int getUnitCapacity(MilitaryUnit unit) {
        if (unit == MilitaryUnit.MONEY || unit == MilitaryUnit.INFRASTRUCTURE) {
            return Integer.MAX_VALUE;
        }
        if (!SimUnits.isPurchasable(unit)) {
            return 0;
        }
        return unitCap(unit);
    }

    @Override
    public int getUnitMaxPerDay(MilitaryUnit unit) {
        if (unit == MilitaryUnit.MONEY || unit == MilitaryUnit.INFRASTRUCTURE) {
            return Integer.MAX_VALUE;
        }
        if (!SimUnits.isPurchasable(unit)) {
            return 0;
        }
        return dailyBuyCap(unit);
    }

    public void initializeDayPhaseTurn(int dayPhaseTurn) {
        if (dayPhaseTurn < 0 || dayPhaseTurn > 11) {
            throw new IllegalArgumentException("dayPhaseTurn must be in [0,11]");
        }
        storage.dayPhaseTurns[nationIndex] = dayPhaseTurn;
    }

    public boolean advanceDayPhaseTurn() {
        int next = (storage.dayPhaseTurns[nationIndex] + 1) % 12;
        storage.dayPhaseTurns[nationIndex] = next;
        return next == 0;
    }

    public void resetUnitBuysToday() {
        java.util.Arrays.fill(
                storage.unitBuysTodayFlat,
                storage.unitBase(nationIndex),
                storage.unitBase(nationIndex) + SimNationArrayStore.PURCHASABLE_COUNT,
                0
        );
    }

    public void setDailyBuyCap(MilitaryUnit unit, int cap) {
        int idx = requirePurchasableIndex(unit);
        if (cap < 0) {
            throw new IllegalArgumentException("cap must be >= 0");
        }
        storage.dailyBuyCapOverridesFlat[storage.unitBase(nationIndex) + idx] = cap;
    }

    public void clearDailyBuyCapOverride(MilitaryUnit unit) {
        int idx = requirePurchasableIndex(unit);
        storage.dailyBuyCapOverridesFlat[storage.unitBase(nationIndex) + idx] = NationCapacityRules.UNSPECIFIED_CAP_OVERRIDE;
    }

    public void setUnitCap(MilitaryUnit unit, int cap) {
        int idx = requirePurchasableIndex(unit);
        if (cap < 0) {
            throw new IllegalArgumentException("cap must be >= 0");
        }
        storage.unitCapOverridesFlat[storage.unitBase(nationIndex) + idx] = cap;
    }

    public void clearUnitCapOverride(MilitaryUnit unit) {
        int idx = requirePurchasableIndex(unit);
        storage.unitCapOverridesFlat[storage.unitBase(nationIndex) + idx] = NationCapacityRules.UNSPECIFIED_CAP_OVERRIDE;
    }

    public void queueUnitBuy(MilitaryUnit unit, int amount) {
        int idx = requirePurchasableIndex(unit);
        int unitBase = storage.unitBase(nationIndex);
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }

        int cap = dailyBuyCap(unit);
        int used = storage.unitBuysTodayFlat[unitBase + idx];
        if (used + amount > cap) {
            throw new IllegalStateException("Daily buy cap exceeded for " + unit + " on nation " + nationId());
        }

        int current = storage.unitsFlat[unitBase + idx];
        int pending = storage.pendingBuysNextTurnFlat[unitBase + idx];
        int unitCap = unitCap(unit);
        if (current + pending + amount > unitCap) {
            throw new IllegalStateException("Unit cap exceeded for " + unit + " on nation " + nationId());
        }

        storage.unitBuysTodayFlat[unitBase + idx] = used + amount;
        storage.pendingBuysNextTurnFlat[unitBase + idx] = pending + amount;
        recalculateScoreAndNotify();
    }

    public void materializePendingBuys() {
        boolean changed = false;
        int unitBase = storage.unitBase(nationIndex);
        for (MilitaryUnit unit : SimUnits.PURCHASABLE_UNITS) {
            int idx = SimUnits.purchasableIndex(unit);
            int pending = storage.pendingBuysNextTurnFlat[unitBase + idx];
            if (pending <= 0) {
                continue;
            }
            int current = storage.unitsFlat[unitBase + idx];
            int cap = unitCap(unit);
            int next = Math.min(cap, current + pending);
            storage.unitsFlat[unitBase + idx] = next;
            storage.pendingBuysNextTurnFlat[unitBase + idx] = 0;
            changed = true;
        }
        if (changed) {
            recalculateScoreAndNotify();
        }
    }

    public boolean canChangePolicy() {
        return storage.policyCooldownTurnsRemaining[nationIndex] == 0;
    }

    public void changePolicy(WarPolicy nextPolicy, int cooldownTurns) {
        if (!canChangePolicy()) {
            throw new IllegalStateException("Policy cooldown active for nation " + nationId() + ": " + policyCooldownTurnsRemaining());
        }
        WarPolicy validatedPolicy = Objects.requireNonNull(nextPolicy, "nextPolicy");
        if (policy() == validatedPolicy) {
            throw new IllegalArgumentException("Policy already set for nation " + nationId() + ": " + validatedPolicy);
        }
        if (cooldownTurns <= 0) {
            throw new IllegalArgumentException("cooldownTurns must be > 0");
        }
        storage.policies[nationIndex] = validatedPolicy;
        storage.combatProfiles[nationIndex] = NationCombatProfile.derived(
                validatedPolicy,
                projectBits(),
                combatProfile().researchBits(),
                validatedPolicy == WarPolicy.BLITZKRIEG
        );
        storage.policyCooldownTurnsRemaining[nationIndex] = cooldownTurns;
    }

    public void decrementPolicyCooldown() {
        if (storage.policyCooldownTurnsRemaining[nationIndex] > 0) {
            storage.policyCooldownTurnsRemaining[nationIndex]--;
        }
    }

    public void applyBeigeTurns(int turns) {
        if (turns <= 0) {
            throw new IllegalArgumentException("turns must be > 0");
        }
        storage.beigeTurns[nationIndex] = Math.max(storage.beigeTurns[nationIndex], turns);
    }

    public void decrementBeigeTurns() {
        if (storage.beigeTurns[nationIndex] > 0) {
            storage.beigeTurns[nationIndex]--;
        }
    }

    public void clearBeigeTurns() {
        storage.beigeTurns[nationIndex] = 0;
    }

    @Deprecated
    public void addWarchest(double amount) {
        addResource(ResourceType.MONEY, amount);
    }

    public void addResource(ResourceType type, double amount) {
        Objects.requireNonNull(type, "type");
        if (amount < 0d) {
            throw new IllegalArgumentException("amount must be >= 0");
        }
        storage.resourcesFlat[storage.resourceBase(nationIndex) + type.ordinal()] += amount;
    }

    public void addResources(double[] delta) {
        double[] validatedDelta = validateResources(delta, "delta");
        int resourceBase = storage.resourceBase(nationIndex);
        for (ResourceType type : ResourceType.values) {
            storage.resourcesFlat[resourceBase + type.ordinal()] += validatedDelta[type.ordinal()];
        }
    }

    @Deprecated
    public double subtractWarchest(double amount) {
        return subtractResource(ResourceType.MONEY, amount);
    }

    public double subtractResource(ResourceType type, double amount) {
        Objects.requireNonNull(type, "type");
        if (amount < 0d) {
            throw new IllegalArgumentException("amount must be >= 0");
        }
        int idx = storage.resourceBase(nationIndex) + type.ordinal();
        double removed = Math.min(amount, storage.resourcesFlat[idx]);
        storage.resourcesFlat[idx] -= removed;
        return removed;
    }

    public boolean canAfford(double[] cost) {
        double[] validatedCost = validateResources(cost, "cost");
        return canAffordValidated(validatedCost);
    }

    private boolean canAffordValidated(double[] validatedCost) {
        int resourceBase = storage.resourceBase(nationIndex);
        for (ResourceType type : ResourceType.values) {
            int idx = type.ordinal();
            if (storage.resourcesFlat[resourceBase + idx] + 1e-9 < validatedCost[idx]) {
                return false;
            }
        }
        return true;
    }

    public void spendResources(double[] cost) {
        double[] validatedCost = validateResources(cost, "cost");
        if (!canAffordValidated(validatedCost)) {
            throw new IllegalStateException(
                    "Insufficient resources for nation " + nationId()
                            + ": required=" + ResourceType.toString(validatedCost)
                            + ", available=" + ResourceType.toString(resources())
            );
        }
        int resourceBase = storage.resourceBase(nationIndex);
        for (ResourceType type : ResourceType.values) {
            storage.resourcesFlat[resourceBase + type.ordinal()] -= validatedCost[type.ordinal()];
        }
    }

    public void setNonInfraScoreBase(double base) {
        if (base < 0d) {
            throw new IllegalArgumentException("nonInfraScoreBase must be >= 0");
        }
        storage.nonInfraScoreBase[nationIndex] = base;
        recalculateScoreAndNotify();
    }

    @Deprecated
    public void setScoreBase(double scoreBase) {
        setNonInfraScoreBase(scoreBase);
    }

    // Applies infra damage to the highest-infra city first, then spills into the next-highest, etc.
    // This matches PnW semantics: each attack targets the city with maximum infra (the formula caps on
    // defender.maxCityInfra()), so the max city takes the hit rather than spreading evenly.
    // Recomputes score. No-op if this nation has no cities or damage <= 0.
    public void applyInfraDamage(double totalInfraDestroyed) {
        int cityOffset = storage.cityInfraOffsets[nationIndex];
        int cityCount = storage.cityCounts[nationIndex];
        if (totalInfraDestroyed <= 0d || cityCount == 0) {
            return;
        }
        double remaining = totalInfraDestroyed;
        while (remaining > 0d) {
            // Find highest-infra city index
            int maxIdx = 0;
            for (int i = 1; i < cityCount; i++) {
                if (storage.cityInfraFlat[cityOffset + i] > storage.cityInfraFlat[cityOffset + maxIdx]) maxIdx = i;
            }
            double currentInfra = storage.cityInfraFlat[cityOffset + maxIdx];
            if (currentInfra <= 0d) break; // all cities at 0
            double removed = Math.min(remaining, currentInfra);
            storage.cityInfraFlat[cityOffset + maxIdx] = currentInfra - removed;
            remaining -= removed;
        }
        recalculateScoreAndNotify();
    }

    public void applyVictoryInfraPercent(double infraDestroyedPercent) {
        int cityOffset = storage.cityInfraOffsets[nationIndex];
        int cityCount = storage.cityCounts[nationIndex];
        int infraPercentMilli = WarOutcomeMath.victoryInfraPercentMilli(infraDestroyedPercent);
        if (infraPercentMilli <= 0 || cityCount == 0) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < cityCount; i++) {
            int cityIndex = cityOffset + i;
            int beforeCents = Math.max(0, (int) Math.round(storage.cityInfraFlat[cityIndex] * 100d));
            int afterCents = WarOutcomeMath.victoryInfraAfterCents(beforeCents, infraPercentMilli);
            double afterInfra = afterCents * 0.01d;
            if (storage.cityInfraFlat[cityIndex] != afterInfra) {
                storage.cityInfraFlat[cityIndex] = afterInfra;
                changed = true;
            }
        }
        if (changed) {
            recalculateScoreAndNotify();
        }
    }

    public double cityInfra(int cityIndex) {
        if (cityIndex < 0 || cityIndex >= cities()) {
            throw new IndexOutOfBoundsException(cityIndex);
        }
        return storage.cityInfraFlat[storage.cityInfraOffsets[nationIndex] + cityIndex];
    }

    public Map.Entry<Integer, Integer> cityMissileDamage(int cityIndex) {
        if (cityIndex < 0 || cityIndex >= cities()) {
            return ZERO_DAMAGE_RANGE;
        }
        double infra = cityInfra(cityIndex);
        return storage.citySpecialistProfilesFlat[storage.cityInfraOffsets[nationIndex] + cityIndex]
                .missileDamage(infra, this::hasProject);
    }

    @Override
    public int cityMissileDamageMin(int cityIndex) {
        if (cityIndex < 0 || cityIndex >= cities()) {
            return 0;
        }
        double infra = cityInfra(cityIndex);
        return storage.citySpecialistProfilesFlat[storage.cityInfraOffsets[nationIndex] + cityIndex]
                .missileDamageMin(infra, this::hasProject);
    }

    @Override
    public int cityMissileDamageMax(int cityIndex) {
        if (cityIndex < 0 || cityIndex >= cities()) {
            return 0;
        }
        double infra = cityInfra(cityIndex);
        return storage.citySpecialistProfilesFlat[storage.cityInfraOffsets[nationIndex] + cityIndex]
                .missileDamageMax(infra, this::hasProject);
    }

    public Map.Entry<Integer, Integer> cityNukeDamage(int cityIndex) {
        if (cityIndex < 0 || cityIndex >= cities()) {
            return ZERO_DAMAGE_RANGE;
        }
        double infra = cityInfra(cityIndex);
        return storage.citySpecialistProfilesFlat[storage.cityInfraOffsets[nationIndex] + cityIndex]
                .nukeDamage(infra, this::hasProject);
    }

    @Override
    public int cityNukeDamageMin(int cityIndex) {
        if (cityIndex < 0 || cityIndex >= cities()) {
            return 0;
        }
        double infra = cityInfra(cityIndex);
        return storage.citySpecialistProfilesFlat[storage.cityInfraOffsets[nationIndex] + cityIndex]
                .nukeDamageMin(infra, this::hasProject);
    }

    @Override
    public int cityNukeDamageMax(int cityIndex) {
        if (cityIndex < 0 || cityIndex >= cities()) {
            return 0;
        }
        double infra = cityInfra(cityIndex);
        return storage.citySpecialistProfilesFlat[storage.cityInfraOffsets[nationIndex] + cityIndex]
                .nukeDamageMax(infra, this::hasProject);
    }

    @Override
    public Collection<? extends CombatCityView> getCityViews() {
        Collection<? extends CombatCityView> cached = combatCityViews;
        if (cached != null) {
            return cached;
        }
        int cityCount = cities();
        if (cityCount == 0) {
            combatCityViews = Collections.emptyList();
            return combatCityViews;
        }
        ArrayList<CombatCityView> views = new ArrayList<>(cityCount);
        for (int cityIndex = 0; cityIndex < cityCount; cityIndex++) {
            final int currentCityIndex = cityIndex;
            views.add(new CombatCityView() {
                @Override
                public double getInfra() {
                    return cityInfra(currentCityIndex);
                }

                @Override
                public Map.Entry<Integer, Integer> getMissileDamage(Predicate<Project> hasProject) {
                    return cityMissileDamage(currentCityIndex);
                }

                @Override
                public Map.Entry<Integer, Integer> getNukeDamage(Predicate<Project> hasProject) {
                    return cityNukeDamage(currentCityIndex);
                }
            });
        }
        combatCityViews = List.copyOf(views);
        return combatCityViews;
    }

    /**
     * Returns a snapshot CombatantView with the stored combat profile, project bits from the bound nation state,
     * and specialist city ranges derived from the compact per-city profile data.
     * Use the SimNation instance itself for live sim paths that can consume CombatantView directly.
     */
    public CombatantView asCombatantView() {
        return asCombatantView(combatProfile(), projectSet());
    }

    // Returns a snapshot CombatantView backed by this nation's current per-city infra and unit counts.
    // Callers can still override the stored combat profile when a test or boundary adapter needs it.
    public CombatantView asCombatantView(NationCombatProfile combatProfile, Collection<Project> projects) {
        return CombatantSnapshots.snapshotOf(this, Objects.requireNonNull(combatProfile, "combatProfile"), projects);
    }

    public CombatantView asCombatantView(
            int researchBits,
            double infraAttackModifier,
            double infraDefendModifier,
            double groundLooterModifier,
            double nonGroundLooterModifier,
            double lootModifier,
            boolean blitzkrieg,
            java.util.Collection<link.locutus.discord.apiv1.enums.city.project.Project> projects
    ) {
        double[] attackModifiers = new double[AttackType.values.length];
        double[] defendModifiers = new double[AttackType.values.length];
        java.util.Arrays.fill(attackModifiers, infraAttackModifier);
        java.util.Arrays.fill(defendModifiers, infraDefendModifier);
        return asCombatantView(
                new NationCombatProfile(
                        researchBits,
                        blitzkrieg,
                        attackModifiers,
                        defendModifiers,
                        groundLooterModifier,
                        nonGroundLooterModifier,
                        lootModifier
                ),
                projects
        );
    }

    public NationCombatProfile combatProfile() {
        return storage.combatProfiles[nationIndex];
    }

    public int researchBits() {
        return combatProfile().researchBits();
    }

    public boolean isBlitzkriegActive() {
        return combatProfile().blitzkriegActive();
    }

    @Override
    public boolean isBlitzkrieg() {
        return isBlitzkriegActive();
    }

    public double infraAttackModifier(AttackType type) {
        return combatProfile().infraAttackModifier(type);
    }

    public double infraDefendModifier(AttackType type) {
        return combatProfile().infraDefendModifier(type);
    }

    public double looterModifier(boolean ground) {
        return combatProfile().looterModifier(ground);
    }

    public double lootModifier() {
        return combatProfile().lootModifier();
    }

    public long projectBits() {
        return storage.projectBits[nationIndex];
    }

    public boolean hasProject(Project project) {
        Objects.requireNonNull(project, "project");
        return (projectBits() & (1L << project.ordinal())) != 0L;
    }

    private Collection<Project> projectSet() {
        long bits = projectBits();
        if (bits == 0L) {
            return Collections.emptySet();
        }
        ArrayList<Project> projects = new ArrayList<>();
        for (Project project : Projects.values()) {
            if ((bits & (1L << project.ordinal())) != 0L) {
                projects.add(project);
            }
        }
        return projects;
    }

    private boolean hasActiveWars() {
        return offSlotsUsed() > 0 || defSlotsUsed() > 0;
    }

    public void setUnitCount(MilitaryUnit unit, int amount) {
        int idx = requirePurchasableIndex(unit);
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0");
        }
        storage.unitsFlat[storage.unitBase(nationIndex) + idx] = amount;
        recalculateScoreAndNotify();
    }

    public void addUnits(MilitaryUnit unit, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
        setUnitCount(unit, units(unit) + amount);
    }

    public int removeUnits(MilitaryUnit unit, int requested) {
        int idx = requirePurchasableIndex(unit);
        int unitBase = storage.unitBase(nationIndex);
        if (requested <= 0) {
            throw new IllegalArgumentException("requested must be > 0");
        }
        int current = storage.unitsFlat[unitBase + idx];
        int removed = Math.min(current, requested);
        storage.unitsFlat[unitBase + idx] = current - removed;
        recalculateScoreAndNotify();
        return removed;
    }

    public void occupyOffensiveSlot() {
        if (storage.offSlotsUsed[nationIndex] >= maxOffSlots()) {
            throw new IllegalStateException("No free offensive slots for nation " + nationId());
        }
        storage.offSlotsUsed[nationIndex]++;
    }

    public void occupyDefensiveSlot() {
        if (storage.defSlotsUsed[nationIndex] >= WarSlotRules.defensiveSlotCap()) {
            throw new IllegalStateException("No free defensive slots for nation " + nationId());
        }
        storage.defSlotsUsed[nationIndex]++;
    }

    public void releaseOffensiveSlot() {
        if (storage.offSlotsUsed[nationIndex] <= 0) {
            throw new IllegalStateException("No offensive slots to release for nation " + nationId());
        }
        storage.offSlotsUsed[nationIndex]--;
    }

    public void releaseDefensiveSlot() {
        if (storage.defSlotsUsed[nationIndex] <= 0) {
            throw new IllegalStateException("No defensive slots to release for nation " + nationId());
        }
        storage.defSlotsUsed[nationIndex]--;
    }

    /**
     * Deep copy of this nation's mutable state.
     * The copy is a fully independent SimNation with identical state at the time of the call.
     * Providers and caps are shared by reference (they are immutable/constant during the sim).
     */
    public SimNation deepCopy() {
        return new SimNation(SimNationArrayStore.standalone(snapshot()), 0);
    }

    private void recalculateScoreAndNotify() {
        double previousScore = storage.scores[nationIndex];
        storage.recalculateScore(nationIndex);
        double currentScore = storage.scores[nationIndex];
        if (scoreListener != null && Double.compare(previousScore, currentScore) != 0) {
            scoreListener.onScoreChanged(nationId(), previousScore, currentScore);
        }
    }

    private static int requirePurchasableIndex(MilitaryUnit unit) {
        MilitaryUnit validatedUnit = Objects.requireNonNull(unit, "unit");
        int idx = SimUnits.purchasableIndex(validatedUnit);
        if (idx < 0) {
            throw new IllegalArgumentException("Sim purchase flow does not support unit: " + validatedUnit);
        }
        return idx;
    }

    private static double[] validateResources(double[] values, String name) {
        Objects.requireNonNull(values, name);
        if (values.length != ResourceType.values.length) {
            throw new IllegalArgumentException(name + " must be sized to ResourceType.values.length");
        }
        double[] copy = values.clone();
        for (int i = 0; i < copy.length; i++) {
            double amount = copy[i];
            if (Double.isNaN(amount) || Double.isInfinite(amount)) {
                throw new IllegalArgumentException(name + " has non-finite value at index " + i);
            }
            if (amount < 0d) {
                throw new IllegalArgumentException(name + " must be >= 0 for all resources");
            }
        }
        return copy;
    }

}
