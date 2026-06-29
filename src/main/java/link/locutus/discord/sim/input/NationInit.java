package link.locutus.discord.sim.input;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.WarSlotRules;
import link.locutus.discord.sim.combat.NationCombatProfile;
import link.locutus.discord.sim.combat.SpecialistCityProfile;
import link.locutus.discord.util.PW;

import java.util.Objects;

public final class NationInit {
    public static final int DEFAULT_MAX_OFF_SLOTS = WarSlotRules.baseOffensiveSlotCap();
    public static final byte DEFAULT_RESET_HOUR_UTC = 0;

    private final int nationId;
    private final int teamId;
    private final WarPolicy policy;
    private final double[] resources;
    private final double staticScoreComponent;
    private final double[] cityInfra;
    private final int maxOffSlots;
    private final byte resetHourUtc;
    private final long projectBits;
    private final SpecialistCityProfile[] citySpecialistProfiles;
    private final NationCombatProfile combatProfile;

    public NationInit(
            int nationId,
            int teamId,
            WarPolicy policy,
            double[] resources,
            double[] cityInfra,
            int maxOffSlots,
            byte resetHourUtc,
            long projectBits,
            SpecialistCityProfile[] citySpecialistProfiles,
            NationCombatProfile combatProfile
    ) {
        if (nationId <= 0) {
            throw new IllegalArgumentException("nationId must be > 0");
        }
        if (teamId <= 0) {
            throw new IllegalArgumentException("teamId must be > 0");
        }
        this.nationId = nationId;
        this.teamId = teamId;
        this.policy = Objects.requireNonNull(policy, "policy");
        this.resources = validateResources(resources, "resources");
        this.cityInfra = validateInfra(cityInfra);
        if (maxOffSlots <= 0) {
            throw new IllegalArgumentException("maxOffSlots must be > 0");
        }
        this.maxOffSlots = maxOffSlots;
        if (resetHourUtc < 0 || resetHourUtc > 23) {
            throw new IllegalArgumentException("resetHourUtc must be in [0,23]");
        }
        this.resetHourUtc = resetHourUtc;
        this.projectBits = projectBits;
        this.citySpecialistProfiles = validateCitySpecialistProfiles(citySpecialistProfiles, this.cityInfra);
        this.combatProfile = combatProfile == null
                ? NationCombatProfile.derived(this.policy, projectBits)
                : combatProfile;
        this.staticScoreComponent = derivedStaticScoreComponent(this.cityInfra.length, projectBits, this.combatProfile.researchBits());
    }

    public NationInit(
            int nationId,
            int teamId,
            WarPolicy policy,
            double[] resources,
            double[] cityInfra,
            int maxOffSlots,
            byte resetHourUtc
    ) {
        this(
                nationId,
                teamId,
                policy,
                resources,
                cityInfra,
                maxOffSlots,
                resetHourUtc,
                0L,
                SpecialistCityProfile.defaults(cityInfra == null ? 0 : cityInfra.length),
                null
        );
    }

    public NationInit(
            int nationId,
            int teamId,
            WarPolicy policy,
            double[] resources,
            double[] cityInfra,
            int maxOffSlots,
            byte resetHourUtc,
            long projectBits,
            SpecialistCityProfile[] citySpecialistProfiles
    ) {
        this(
                nationId,
                teamId,
                policy,
                resources,
                cityInfra,
                maxOffSlots,
                resetHourUtc,
                projectBits,
                citySpecialistProfiles,
                null
        );
    }

    public static NationInit basic(int nationId, WarPolicy policy) {
        return new NationInit(
                nationId,
                nationId,
                policy,
                moneyOnly(0d),
                new double[0],
                DEFAULT_MAX_OFF_SLOTS,
                DEFAULT_RESET_HOUR_UTC
        );
    }

    public static NationInit moneyOnly(
            int nationId,
            WarPolicy policy,
            double money,
            double[] cityInfra,
            int maxOffSlots,
            byte resetHourUtc
    ) {
        return moneyOnly(nationId, nationId, policy, money, cityInfra, maxOffSlots, resetHourUtc);
    }

    public static NationInit moneyOnly(
            int nationId,
            int teamId,
            WarPolicy policy,
            double money,
            double[] cityInfra,
            int maxOffSlots,
            byte resetHourUtc
    ) {
        return new NationInit(
                nationId,
                teamId,
                policy,
                moneyOnly(money),
                cityInfra,
                maxOffSlots,
                resetHourUtc
        );
    }

    public int nationId() {
        return nationId;
    }

    public int teamId() {
        return teamId;
    }

    public WarPolicy policy() {
        return policy;
    }

    public double[] resources() {
        return resources.clone();
    }

    public double staticScoreComponent() {
        return staticScoreComponent;
    }

    public double[] cityInfra() {
        return cityInfra.clone();
    }

    public int maxOffSlots() {
        return maxOffSlots;
    }

    public byte resetHourUtc() {
        return resetHourUtc;
    }

    public long projectBits() {
        return projectBits;
    }

    public SpecialistCityProfile[] citySpecialistProfiles() {
        return citySpecialistProfiles.clone();
    }

    public NationCombatProfile combatProfile() {
        return combatProfile;
    }

    private static double[] moneyOnly(double money) {
        if (money < 0d) {
            throw new IllegalArgumentException("money must be >= 0");
        }
        double[] buffer = ResourceType.getBuffer();
        buffer[ResourceType.MONEY.ordinal()] = money;
        return buffer;
    }

    private static double[] validateInfra(double[] values) {
        Objects.requireNonNull(values, "cityInfra");
        double[] copy = values.clone();
        for (int i = 0; i < copy.length; i++) {
            double infra = copy[i];
            if (Double.isNaN(infra) || Double.isInfinite(infra)) {
                throw new IllegalArgumentException("cityInfra has non-finite value at index " + i);
            }
            if (infra < 0d) {
                copy[i] = 0d;
            }
        }
        return copy;
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

    private static SpecialistCityProfile[] validateCitySpecialistProfiles(
            SpecialistCityProfile[] values,
            double[] cityInfra
    ) {
        int cityCount = cityInfra.length;
        if (values == null || values.length == 0) {
            return SpecialistCityProfile.defaults(cityCount);
        }
        if (values.length != cityCount) {
            throw new IllegalArgumentException("citySpecialistProfiles must match cityInfra length");
        }
        SpecialistCityProfile[] copy = values.clone();
        for (int i = 0; i < copy.length; i++) {
            copy[i] = Objects.requireNonNull(copy[i], "citySpecialistProfiles[" + i + "]");
        }
        return copy;
    }

    private static double derivedStaticScoreComponent(int cities, long projectBits, int researchBits) {
        if (cities <= 0) {
            return 0d;
        }
        return PW.computeStaticScoreComponent(cities, Long.bitCount(projectBits), researchBits);
    }
}
