package link.locutus.discord.sim.input;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.combat.NationCombatProfile;
import link.locutus.discord.sim.combat.SpecialistCityProfile;

import java.util.Objects;

public record NationInit(
        int nationId,
        int teamId,
        WarPolicy policy,
        double[] resources,
        double nonInfraScoreBase,
        double[] cityInfra,
        int maxOffSlots,
        byte resetHourUtc,
        long projectBits,
        SpecialistCityProfile[] citySpecialistProfiles,
        NationCombatProfile combatProfile
) {
    public static final int DEFAULT_MAX_OFF_SLOTS = 5;
    public static final byte DEFAULT_RESET_HOUR_UTC = 0;

    public NationInit {
        if (nationId <= 0) {
            throw new IllegalArgumentException("nationId must be > 0");
        }
        if (teamId <= 0) {
            throw new IllegalArgumentException("teamId must be > 0");
        }
        policy = Objects.requireNonNull(policy, "policy");
        resources = validateResources(resources, "resources");
        if (nonInfraScoreBase < 0d) {
            throw new IllegalArgumentException("nonInfraScoreBase must be >= 0");
        }
        cityInfra = validateInfra(cityInfra);
        if (maxOffSlots <= 0) {
            throw new IllegalArgumentException("maxOffSlots must be > 0");
        }
        if (resetHourUtc < 0 || resetHourUtc > 23) {
            throw new IllegalArgumentException("resetHourUtc must be in [0,23]");
        }
        citySpecialistProfiles = validateCitySpecialistProfiles(citySpecialistProfiles, cityInfra);
        combatProfile = combatProfile == null
                ? NationCombatProfile.derived(policy, projectBits)
                : combatProfile;
    }

    public NationInit(
            int nationId,
            int teamId,
            WarPolicy policy,
            double[] resources,
            double nonInfraScoreBase,
            double[] cityInfra,
            int maxOffSlots,
            byte resetHourUtc
    ) {
        this(
                nationId,
                teamId,
                policy,
                resources,
                nonInfraScoreBase,
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
            double nonInfraScoreBase,
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
                    nonInfraScoreBase,
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
                0d,
                new double[0],
                DEFAULT_MAX_OFF_SLOTS,
                DEFAULT_RESET_HOUR_UTC
        );
    }

    public static NationInit moneyOnly(
            int nationId,
            WarPolicy policy,
            double money,
            double nonInfraScoreBase,
            double[] cityInfra,
            int maxOffSlots,
            byte resetHourUtc
    ) {
        return moneyOnly(nationId, nationId, policy, money, nonInfraScoreBase, cityInfra, maxOffSlots, resetHourUtc);
    }

    public static NationInit moneyOnly(
            int nationId,
            int teamId,
            WarPolicy policy,
            double money,
            double nonInfraScoreBase,
            double[] cityInfra,
            int maxOffSlots,
            byte resetHourUtc
    ) {
        return new NationInit(
                nationId,
                teamId,
                policy,
                moneyOnly(money),
                nonInfraScoreBase,
                cityInfra,
                maxOffSlots,
                resetHourUtc
        );
    }

    @Override
    public double[] resources() {
        return resources.clone();
    }

    @Override
    public double[] cityInfra() {
        return cityInfra.clone();
    }

    @Override
    public SpecialistCityProfile[] citySpecialistProfiles() {
        return citySpecialistProfiles.clone();
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
}
