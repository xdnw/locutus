package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.combat.NationCombatProfile;
import link.locutus.discord.sim.combat.SpecialistCityProfile;
import link.locutus.discord.sim.input.NationInit;

import java.util.Arrays;
import java.util.Objects;

final class SimNationArrayStore {
    static final int RESOURCE_COUNT = ResourceType.values.length;
    static final int PURCHASABLE_COUNT = SimUnits.PURCHASABLE_UNITS.length;
    private static final int DEFAULT_CAPACITY = 4;

    private boolean sharedStaticArrays;

    int size;
    int cityInfraSize;
    int[] nationIds;
    int[] teamIds;
    int[] maxOffSlots;
    long[] projectBits;
    NationCombatProfile[] combatProfiles;
    int[] cityInfraOffsets;
    int[] cityCounts;
    byte[] resetHoursUtc;
    WarPolicy[] policies;
    int[] policyCooldownTurnsRemaining;
    int[] beigeTurns;
    int[] offSlotsUsed;
    int[] defSlotsUsed;
    int[] dayPhaseTurns;
    double[] nonInfraScoreBase;
    double[] scores;
    double[] resourcesFlat;
    int[] unitsFlat;
    int[] unitBuysTodayFlat;
    int[] pendingBuysNextTurnFlat;
    int[] dailyBuyCapsFlat;
    int[] unitCapsFlat;
    double[] cityInfraFlat;
    SpecialistCityProfile[] citySpecialistProfilesFlat;

    private SimNationArrayStore(int nationCapacity, int cityCapacity) {
        int safeNationCapacity = Math.max(1, nationCapacity);
        int safeCityCapacity = Math.max(0, cityCapacity);
        nationIds = new int[safeNationCapacity];
        teamIds = new int[safeNationCapacity];
        maxOffSlots = new int[safeNationCapacity];
        projectBits = new long[safeNationCapacity];
        combatProfiles = new NationCombatProfile[safeNationCapacity];
        cityInfraOffsets = new int[safeNationCapacity];
        cityCounts = new int[safeNationCapacity];
        resetHoursUtc = new byte[safeNationCapacity];
        policies = new WarPolicy[safeNationCapacity];
        policyCooldownTurnsRemaining = new int[safeNationCapacity];
        beigeTurns = new int[safeNationCapacity];
        offSlotsUsed = new int[safeNationCapacity];
        defSlotsUsed = new int[safeNationCapacity];
        dayPhaseTurns = new int[safeNationCapacity];
        nonInfraScoreBase = new double[safeNationCapacity];
        scores = new double[safeNationCapacity];
        resourcesFlat = new double[safeNationCapacity * RESOURCE_COUNT];
        unitsFlat = new int[safeNationCapacity * PURCHASABLE_COUNT];
        unitBuysTodayFlat = new int[safeNationCapacity * PURCHASABLE_COUNT];
        pendingBuysNextTurnFlat = new int[safeNationCapacity * PURCHASABLE_COUNT];
        dailyBuyCapsFlat = new int[safeNationCapacity * PURCHASABLE_COUNT];
        unitCapsFlat = new int[safeNationCapacity * PURCHASABLE_COUNT];
        cityInfraFlat = new double[safeCityCapacity];
        citySpecialistProfilesFlat = new SpecialistCityProfile[safeCityCapacity];
    }

    static SimNationArrayStore empty() {
        return new SimNationArrayStore(DEFAULT_CAPACITY, 0);
    }

    static SimNationArrayStore standalone(SimNationSnapshot snapshot) {
        SimNationArrayStore store = new SimNationArrayStore(1, snapshot.init().cityInfra().length);
        store.add(snapshot);
        return store;
    }

    int add(SimNationSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        NationInit init = snapshot.init();
        int cityCount = init.cityInfra().length;
        ensureStaticCapacity(size + 1);
        ensureDynamicCapacity(size + 1, cityCount);

        int nationIndex = size;
        size++;

        nationIds[nationIndex] = init.nationId();
        teamIds[nationIndex] = init.teamId();
        maxOffSlots[nationIndex] = init.maxOffSlots();
        projectBits[nationIndex] = init.projectBits();
        combatProfiles[nationIndex] = init.combatProfile();
        resetHoursUtc[nationIndex] = init.resetHourUtc();
        policies[nationIndex] = init.policy();
        policyCooldownTurnsRemaining[nationIndex] = snapshot.policyCooldownTurnsRemaining();
        beigeTurns[nationIndex] = snapshot.beigeTurns();
        offSlotsUsed[nationIndex] = snapshot.offSlotsUsed();
        defSlotsUsed[nationIndex] = snapshot.defSlotsUsed();
        dayPhaseTurns[nationIndex] = snapshot.dayPhaseTurn();
        nonInfraScoreBase[nationIndex] = init.nonInfraScoreBase();

        cityInfraOffsets[nationIndex] = cityInfraSize;
        cityCounts[nationIndex] = cityCount;

        double[] resources = init.resources();
        System.arraycopy(resources, 0, resourcesFlat, resourceBase(nationIndex), RESOURCE_COUNT);

        int unitBase = unitBase(nationIndex);
        System.arraycopy(snapshot.units(), 0, unitsFlat, unitBase, PURCHASABLE_COUNT);
        System.arraycopy(snapshot.unitBuysToday(), 0, unitBuysTodayFlat, unitBase, PURCHASABLE_COUNT);
        System.arraycopy(snapshot.pendingBuysNextTurn(), 0, pendingBuysNextTurnFlat, unitBase, PURCHASABLE_COUNT);
        System.arraycopy(snapshot.dailyBuyCaps(), 0, dailyBuyCapsFlat, unitBase, PURCHASABLE_COUNT);
        System.arraycopy(snapshot.unitCaps(), 0, unitCapsFlat, unitBase, PURCHASABLE_COUNT);

        double[] cityInfra = init.cityInfra();
        System.arraycopy(cityInfra, 0, cityInfraFlat, cityInfraSize, cityCount);
        SpecialistCityProfile[] cityProfiles = init.citySpecialistProfiles();
        System.arraycopy(cityProfiles, 0, citySpecialistProfilesFlat, cityInfraSize, cityCount);
        cityInfraSize += cityCount;

        recalculateScore(nationIndex);
        return nationIndex;
    }

    int size() {
        return size;
    }

    SimNationArrayStore deepCopy() {
        SimNationArrayStore copy = new SimNationArrayStore(size, cityInfraSize);
        sharedStaticArrays = true;
        copy.sharedStaticArrays = true;
        copy.size = size;
        copy.cityInfraSize = cityInfraSize;
        copy.nationIds = nationIds;
        copy.teamIds = teamIds;
        copy.maxOffSlots = maxOffSlots;
        copy.projectBits = projectBits;
        copy.combatProfiles = Arrays.copyOf(combatProfiles, size);
        copy.cityInfraOffsets = cityInfraOffsets;
        copy.cityCounts = cityCounts;
        copy.resetHoursUtc = resetHoursUtc;
        copy.policies = Arrays.copyOf(policies, size);
        copy.policyCooldownTurnsRemaining = Arrays.copyOf(policyCooldownTurnsRemaining, size);
        copy.beigeTurns = Arrays.copyOf(beigeTurns, size);
        copy.offSlotsUsed = Arrays.copyOf(offSlotsUsed, size);
        copy.defSlotsUsed = Arrays.copyOf(defSlotsUsed, size);
        copy.dayPhaseTurns = Arrays.copyOf(dayPhaseTurns, size);
        copy.nonInfraScoreBase = Arrays.copyOf(nonInfraScoreBase, size);
        copy.scores = Arrays.copyOf(scores, size);
        copy.resourcesFlat = Arrays.copyOf(resourcesFlat, size * RESOURCE_COUNT);
        copy.unitsFlat = Arrays.copyOf(unitsFlat, size * PURCHASABLE_COUNT);
        copy.unitBuysTodayFlat = Arrays.copyOf(unitBuysTodayFlat, size * PURCHASABLE_COUNT);
        copy.pendingBuysNextTurnFlat = Arrays.copyOf(pendingBuysNextTurnFlat, size * PURCHASABLE_COUNT);
        copy.dailyBuyCapsFlat = Arrays.copyOf(dailyBuyCapsFlat, size * PURCHASABLE_COUNT);
        copy.unitCapsFlat = Arrays.copyOf(unitCapsFlat, size * PURCHASABLE_COUNT);
        copy.cityInfraFlat = Arrays.copyOf(cityInfraFlat, cityInfraSize);
        copy.citySpecialistProfilesFlat = Arrays.copyOf(citySpecialistProfilesFlat, cityInfraSize);
        return copy;
    }

    SimNationSnapshot snapshot(int nationIndex) {
        return new SimNationSnapshot(
                new NationInit(
                        nationIds[nationIndex],
                        teamIds[nationIndex],
                        policies[nationIndex],
                        copyResources(nationIndex),
                        nonInfraScoreBase[nationIndex],
                        copyCityInfra(nationIndex),
                        maxOffSlots[nationIndex],
                        resetHoursUtc[nationIndex],
                        projectBits[nationIndex],
                        copyCitySpecialistProfiles(nationIndex),
                        combatProfiles[nationIndex]
                ),
                policyCooldownTurnsRemaining[nationIndex],
                beigeTurns[nationIndex],
                offSlotsUsed[nationIndex],
                defSlotsUsed[nationIndex],
                dayPhaseTurns[nationIndex],
                copyUnitSlice(unitsFlat, nationIndex),
                copyUnitSlice(unitBuysTodayFlat, nationIndex),
                copyUnitSlice(pendingBuysNextTurnFlat, nationIndex),
                copyUnitSlice(dailyBuyCapsFlat, nationIndex),
                copyUnitSlice(unitCapsFlat, nationIndex)
        );
    }

    double[] copyResources(int nationIndex) {
        int base = resourceBase(nationIndex);
        return Arrays.copyOfRange(resourcesFlat, base, base + RESOURCE_COUNT);
    }

    double[] copyCityInfra(int nationIndex) {
        int offset = cityInfraOffsets[nationIndex];
        return Arrays.copyOfRange(cityInfraFlat, offset, offset + cityCounts[nationIndex]);
    }

    SpecialistCityProfile[] copyCitySpecialistProfiles(int nationIndex) {
        int offset = cityInfraOffsets[nationIndex];
        return Arrays.copyOfRange(citySpecialistProfilesFlat, offset, offset + cityCounts[nationIndex]);
    }

    void recalculateScore(int nationIndex) {
        double total = nonInfraScoreBase[nationIndex];
        int cityOffset = cityInfraOffsets[nationIndex];
        int cityCount = cityCounts[nationIndex];
        for (int i = 0; i < cityCount; i++) {
            total += cityInfraFlat[cityOffset + i] / 40d;
        }
        int unitBase = unitBase(nationIndex);
        for (MilitaryUnit unit : SimUnits.PURCHASABLE_UNITS) {
            int idx = SimUnits.purchasableIndex(unit);
            int amount = unitsFlat[unitBase + idx] + pendingBuysNextTurnFlat[unitBase + idx];
            if (amount > 0) {
                total += unit.getScore(amount);
            }
        }
        scores[nationIndex] = total;
    }

    int resourceBase(int nationIndex) {
        return nationIndex * RESOURCE_COUNT;
    }

    int unitBase(int nationIndex) {
        return nationIndex * PURCHASABLE_COUNT;
    }

    private void ensureStaticCapacity(int minimumNationCapacity) {
        if (sharedStaticArrays || minimumNationCapacity > nationIds.length) {
            int nextCapacity = minimumNationCapacity > nationIds.length
                    ? Math.max(minimumNationCapacity, nationIds.length * 2)
                    : nationIds.length;
            nationIds = Arrays.copyOf(nationIds, nextCapacity);
            teamIds = Arrays.copyOf(teamIds, nextCapacity);
            maxOffSlots = Arrays.copyOf(maxOffSlots, nextCapacity);
            projectBits = Arrays.copyOf(projectBits, nextCapacity);
            combatProfiles = Arrays.copyOf(combatProfiles, nextCapacity);
            cityInfraOffsets = Arrays.copyOf(cityInfraOffsets, nextCapacity);
            cityCounts = Arrays.copyOf(cityCounts, nextCapacity);
            resetHoursUtc = Arrays.copyOf(resetHoursUtc, nextCapacity);
            sharedStaticArrays = false;
        }
    }

    private void ensureDynamicCapacity(int minimumNationCapacity, int additionalCities) {
        if (minimumNationCapacity > policies.length) {
            int nextCapacity = Math.max(minimumNationCapacity, policies.length * 2);
            policies = Arrays.copyOf(policies, nextCapacity);
            policyCooldownTurnsRemaining = Arrays.copyOf(policyCooldownTurnsRemaining, nextCapacity);
            beigeTurns = Arrays.copyOf(beigeTurns, nextCapacity);
            offSlotsUsed = Arrays.copyOf(offSlotsUsed, nextCapacity);
            defSlotsUsed = Arrays.copyOf(defSlotsUsed, nextCapacity);
            dayPhaseTurns = Arrays.copyOf(dayPhaseTurns, nextCapacity);
            nonInfraScoreBase = Arrays.copyOf(nonInfraScoreBase, nextCapacity);
            scores = Arrays.copyOf(scores, nextCapacity);
            resourcesFlat = Arrays.copyOf(resourcesFlat, nextCapacity * RESOURCE_COUNT);
            unitsFlat = Arrays.copyOf(unitsFlat, nextCapacity * PURCHASABLE_COUNT);
            unitBuysTodayFlat = Arrays.copyOf(unitBuysTodayFlat, nextCapacity * PURCHASABLE_COUNT);
            pendingBuysNextTurnFlat = Arrays.copyOf(pendingBuysNextTurnFlat, nextCapacity * PURCHASABLE_COUNT);
            dailyBuyCapsFlat = Arrays.copyOf(dailyBuyCapsFlat, nextCapacity * PURCHASABLE_COUNT);
            unitCapsFlat = Arrays.copyOf(unitCapsFlat, nextCapacity * PURCHASABLE_COUNT);
        }

        int minimumCityCapacity = cityInfraSize + Math.max(0, additionalCities);
        if (minimumCityCapacity > cityInfraFlat.length) {
            int nextCityCapacity = Math.max(minimumCityCapacity, Math.max(1, cityInfraFlat.length * 2));
            cityInfraFlat = Arrays.copyOf(cityInfraFlat, nextCityCapacity);
            citySpecialistProfilesFlat = Arrays.copyOf(citySpecialistProfilesFlat, nextCityCapacity);
        }
    }

    private int[] copyUnitSlice(int[] source, int nationIndex) {
        int base = unitBase(nationIndex);
        return Arrays.copyOfRange(source, base, base + PURCHASABLE_COUNT);
    }
}

record SimNationSnapshot(
        NationInit init,
        int policyCooldownTurnsRemaining,
        int beigeTurns,
        int offSlotsUsed,
        int defSlotsUsed,
        int dayPhaseTurn,
        int[] units,
        int[] unitBuysToday,
        int[] pendingBuysNextTurn,
        int[] dailyBuyCaps,
        int[] unitCaps
) {
    SimNationSnapshot {
        init = Objects.requireNonNull(init, "init");
        units = validateArray(units, "units");
        unitBuysToday = validateArray(unitBuysToday, "unitBuysToday");
        pendingBuysNextTurn = validateArray(pendingBuysNextTurn, "pendingBuysNextTurn");
        dailyBuyCaps = validateArray(dailyBuyCaps, "dailyBuyCaps");
        unitCaps = validateArray(unitCaps, "unitCaps");
        if (policyCooldownTurnsRemaining < 0) {
            throw new IllegalArgumentException("policyCooldownTurnsRemaining must be >= 0");
        }
        if (beigeTurns < 0) {
            throw new IllegalArgumentException("beigeTurns must be >= 0");
        }
        if (offSlotsUsed < 0) {
            throw new IllegalArgumentException("offSlotsUsed must be >= 0");
        }
        if (defSlotsUsed < 0) {
            throw new IllegalArgumentException("defSlotsUsed must be >= 0");
        }
        if (dayPhaseTurn < 0 || dayPhaseTurn > 11) {
            throw new IllegalArgumentException("dayPhaseTurn must be in [0,11]");
        }
    }

    static SimNationSnapshot initial(NationInit init) {
        int[] dailyBuyCaps = new int[SimNationArrayStore.PURCHASABLE_COUNT];
        int[] unitCaps = new int[SimNationArrayStore.PURCHASABLE_COUNT];
        Arrays.fill(dailyBuyCaps, Integer.MAX_VALUE);
        Arrays.fill(unitCaps, Integer.MAX_VALUE);
        return new SimNationSnapshot(
                init,
                0,
                0,
                0,
                0,
                0,
                new int[SimNationArrayStore.PURCHASABLE_COUNT],
                new int[SimNationArrayStore.PURCHASABLE_COUNT],
                new int[SimNationArrayStore.PURCHASABLE_COUNT],
                dailyBuyCaps,
                unitCaps
        );
    }

    private static int[] validateArray(int[] values, String name) {
        Objects.requireNonNull(values, name);
        if (values.length != SimNationArrayStore.PURCHASABLE_COUNT) {
            throw new IllegalArgumentException(name + " must have length " + SimNationArrayStore.PURCHASABLE_COUNT);
        }
        return values.clone();
    }
}