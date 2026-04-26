package link.locutus.discord.apiv1.enums;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.sim.combat.ProjectileDefenseMath;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.discord.sim.combat.WarRoleModifiers;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public enum AttackType {
    GROUND(3, 10, MilitaryUnit.SOLDIER, MilitaryUnit.TANK, MilitaryUnit.AIRCRAFT),
    VICTORY(0, 0),
    FORTIFY(3, 0),
    A_LOOT("Alliance Loot", 0, 0),
    AIRSTRIKE_INFRA("Airstrike Infrastructure", 4, 12, MilitaryUnit.AIRCRAFT),
    AIRSTRIKE_SOLDIER("Airstrike Soldiers", 4, 12, MilitaryUnit.AIRCRAFT, MilitaryUnit.SOLDIER),
    AIRSTRIKE_TANK("Airstrike Tanks", 4, 12, MilitaryUnit.AIRCRAFT, MilitaryUnit.TANK),
    AIRSTRIKE_MONEY("Airstrike Money", 4, 12, MilitaryUnit.AIRCRAFT, MilitaryUnit.MONEY),
    AIRSTRIKE_SHIP("Airstrike Ships", 4, 12, MilitaryUnit.AIRCRAFT, MilitaryUnit.SHIP),
    AIRSTRIKE_AIRCRAFT("Dogfight", 4, 12, MilitaryUnit.AIRCRAFT),

    NAVAL(4, 14, MilitaryUnit.SHIP),
    PEACE(0, 0),
    MISSILE(8, 18, MilitaryUnit.MISSILE),
    NUKE(12, 25, MilitaryUnit.NUKE),

    NAVAL_INFRA(4, 14, MilitaryUnit.SHIP),
    NAVAL_AIR(4, 14, MilitaryUnit.SHIP),
    NAVAL_GROUND(4, 14, MilitaryUnit.SHIP);

    public interface CasualtyCityView {
        double getInfra();
        Map.Entry<Integer, Integer> getMissileDamage(Predicate<Project> hasProject);
        Map.Entry<Integer, Integer> getNukeDamage(Predicate<Project> hasProject);
    }

    public interface CasualtyNationView {
        double infraAttackModifier(AttackType type);
        double infraDefendModifier(AttackType type);
        double looterModifier(boolean ground);
        double lootModifier();
        boolean isBlitzkrieg();
        boolean hasProject(Project project);
        int getUnits(MilitaryUnit unit);
        Collection<? extends CasualtyCityView> getCityViews();

        default int cityCount() {
            return getCityViews().size();
        }

        default double cityInfra(int cityIndex) {
            return cityViewAt(cityIndex).getInfra();
        }

        default Map.Entry<Integer, Integer> cityMissileDamage(int cityIndex) {
            return cityViewAt(cityIndex).getMissileDamage(this::hasProject);
        }

        default int cityMissileDamageMin(int cityIndex) {
            return cityMissileDamage(cityIndex).getKey();
        }

        default int cityMissileDamageMax(int cityIndex) {
            return cityMissileDamage(cityIndex).getValue();
        }

        default Map.Entry<Integer, Integer> cityNukeDamage(int cityIndex) {
            return cityViewAt(cityIndex).getNukeDamage(this::hasProject);
        }

        default int cityNukeDamageMin(int cityIndex) {
            return cityNukeDamage(cityIndex).getKey();
        }

        default int cityNukeDamageMax(int cityIndex) {
            return cityNukeDamage(cityIndex).getValue();
        }

        default int getSoldiers() {
            return getUnits(MilitaryUnit.SOLDIER);
        }

        default int getTanks() {
            return getUnits(MilitaryUnit.TANK);
        }

        default int getAircraft() {
            return getUnits(MilitaryUnit.AIRCRAFT);
        }

        default int getShips() {
            return getUnits(MilitaryUnit.SHIP);
        }

        default double maxCityInfra() {
            double max = 0;
            for (int cityIndex = 0; cityIndex < cityCount(); cityIndex++) {
                max = Math.max(max, cityInfra(cityIndex));
            }
            return max;
        }

        private CasualtyCityView cityViewAt(int cityIndex) {
            Collection<? extends CasualtyCityView> cities = getCityViews();
            if (cityIndex < 0 || cityIndex >= cities.size()) {
                throw new IndexOutOfBoundsException(cityIndex);
            }
            if (cities instanceof List<?> cityList) {
                return (CasualtyCityView) cityList.get(cityIndex);
            }
            int currentIndex = 0;
            for (CasualtyCityView city : cities) {
                if (currentIndex == cityIndex) {
                    return city;
                }
                currentIndex++;
            }
            throw new IndexOutOfBoundsException(cityIndex);
        }
    }

    public interface CasualtyRangeWriter {
        void clearCasualtyRanges();
        void putAttackerRange(MilitaryUnit unit, int min, int max);
        void putDefenderRange(MilitaryUnit unit, int min, int max);
    }

    private static final class MapCasualtyRangeWriter implements CasualtyRangeWriter {
        private final Map<MilitaryUnit, Map.Entry<Integer, Integer>> attackerCasualties;
        private final Map<MilitaryUnit, Map.Entry<Integer, Integer>> defenderCasualties;

        private MapCasualtyRangeWriter(
                Map<MilitaryUnit, Map.Entry<Integer, Integer>> attackerCasualties,
                Map<MilitaryUnit, Map.Entry<Integer, Integer>> defenderCasualties
        ) {
            this.attackerCasualties = attackerCasualties;
            this.defenderCasualties = defenderCasualties;
        }

        @Override
        public void clearCasualtyRanges() {
            attackerCasualties.clear();
            defenderCasualties.clear();
        }

        @Override
        public void putAttackerRange(MilitaryUnit unit, int min, int max) {
            putRange(attackerCasualties, unit, min, max);
        }

        @Override
        public void putDefenderRange(MilitaryUnit unit, int min, int max) {
            putRange(defenderCasualties, unit, min, max);
        }

        private static void putRange(
                Map<MilitaryUnit, Map.Entry<Integer, Integer>> target,
                MilitaryUnit unit,
                int min,
                int max
        ) {
            if (unit == null || (min == 0 && max == 0)) {
                return;
            }
            int normalizedMin = Math.max(0, min);
            int normalizedMax = Math.max(normalizedMin, max);
            target.put(unit, KeyValue.of(normalizedMin, normalizedMax));
        }
    }

    private enum Bound {
        MIN {
            @Override
            int apply(Map.Entry<Integer, Integer> range) {
                return range.getKey();
            }
        },
        MAX {
            @Override
            int apply(Map.Entry<Integer, Integer> range) {
                return range.getValue();
            }
        },
        AVG {
            @Override
            int apply(Map.Entry<Integer, Integer> range) {
                return (int) Math.round((range.getKey() + range.getValue()) / 2d);
            }
        };

        abstract int apply(Map.Entry<Integer, Integer> range);
    }

    private static final Map.Entry<Integer, Integer> ZERO_RANGE = KeyValue.of(0, 0);

        private static final ResourceType[] RESOURCE_VALUES = ResourceType.values;

    private static final class DBNationCityView implements CasualtyCityView {
        private final JavaCity city;

        private DBNationCityView(JavaCity city) {
            this.city = city;
        }

        @Override
        public double getInfra() {
            return city.getInfra();
        }

        @Override
        public Map.Entry<Integer, Integer> getMissileDamage(Predicate<Project> hasProject) {
            return city.getMissileDamage(hasProject);
        }

        @Override
        public Map.Entry<Integer, Integer> getNukeDamage(Predicate<Project> hasProject) {
            return city.getNukeDamage(hasProject);
        }
    }

    private static final class DBNationView implements CasualtyNationView {
        private final DBNation nation;

        private DBNationView(DBNation nation) {
            this.nation = nation;
        }

        @Override
        public double infraAttackModifier(AttackType type) {
            return nation.infraAttackModifier(type);
        }

        @Override
        public double infraDefendModifier(AttackType type) {
            return nation.infraDefendModifier(type);
        }

        @Override
        public double looterModifier(boolean ground) {
            return nation.looterModifier(ground);
        }

        @Override
        public double lootModifier() {
            return nation.lootModifier();
        }

        @Override
        public boolean isBlitzkrieg() {
            return nation.isBlitzkrieg();
        }

        @Override
        public boolean hasProject(Project project) {
            return nation.hasProject(project);
        }

        @Override
        public int getUnits(MilitaryUnit unit) {
            return nation.getUnits(unit);
        }

        @Override
        public Collection<? extends CasualtyCityView> getCityViews() {
            Collection<JavaCity> cityValues = nation.getCityMap(false).values();
            List<CasualtyCityView> views = new ArrayList<>(cityValues.size());
            for (JavaCity city : cityValues) {
                views.add(new DBNationCityView(city));
            }
            return views;
        }
    }

    @Override
    public String toString() {
        return name;
    }

    public static AttackType fromV3(com.politicsandwar.graphql.model.AttackType v3) {
        return switch (v3) {
            case AIRVINFRA -> AIRSTRIKE_INFRA;
            case AIRVSOLDIERS -> AIRSTRIKE_SOLDIER;
            case AIRVTANKS -> AIRSTRIKE_TANK;
            case AIRVMONEY -> AIRSTRIKE_MONEY;
            case AIRVSHIPS -> AIRSTRIKE_SHIP;
            case AIRVAIR -> AIRSTRIKE_AIRCRAFT;
            case GROUND -> GROUND;
            case MISSILE, MISSILEFAIL -> MISSILE;
            case NUKE, NUKEFAIL -> NUKE;
            case NAVAL, NAVALVSHIPS -> NAVAL;
            case NAVALVAIR -> NAVAL_AIR;
            case NAVALVGROUND -> NAVAL_GROUND;
            case NAVALVINFRA -> NAVAL_INFRA;
            case FORTIFY -> FORTIFY;
            case PEACE -> PEACE;
            case VICTORY -> VICTORY;
            case ALLIANCELOOT -> A_LOOT;
            default -> throw new IllegalStateException("No v3 attack type found: " + v3);
        };
    }

    public static Map.Entry<Map.Entry<Integer, Integer>, Map.Entry<Integer, Integer>> getAirstrikeCasualties(
            int attUnits,
            int defUnits,
            SuccessType victory,
            boolean airVsAir,
            double attModifier,
            double defModifier
    ) {
        double attStr = attUnits * 3;
        double defStr = defUnits * 3;
        int minAtt = 0;
        int minDef = 0;
        int maxAtt = 0;
        int maxDef = 0;

        double attCasualties = airVsAir ? 0.01 : 0.015385;
        double defCasualties = airVsAir ? 0.018337 : 0.009091;

        int failures = 3 - victory.ordinal();
        int successes = victory.ordinal();

        for (int i = 0; i < failures; i++) {
            maxAtt += defStr * attCasualties * attModifier;
            minAtt += Math.max(defStr * 0.4, attStr * 0.4 + 1) * attCasualties * attModifier;

            maxDef += Math.min(attStr, defStr - 1) * defCasualties * defModifier;
            minDef += attStr * 0.4 * defCasualties * defModifier;
        }

        for (int i = 0; i < successes; i++) {
            maxAtt += Math.min(defStr, attStr - 1) * attCasualties * attModifier;
            minAtt += defStr * 0.4 * attCasualties * attModifier;

            maxDef += attStr * defCasualties * defModifier;
            minDef += Math.max(attStr * 0.4, defStr * 0.4 + 1) * defCasualties * defModifier;
        }

        minAtt = Math.min(attUnits, minAtt);
        maxAtt = Math.min(attUnits, maxAtt);
        minDef = Math.min(defUnits, minDef);
        maxDef = Math.min(defUnits, maxDef);

        return KeyValue.of(
                KeyValue.of(minAtt, maxAtt),
                KeyValue.of(minDef, maxDef)
        );
    }

    public static Map.Entry<Double, Double> getAirInfraCasualties(
            int attAir,
            int defAir,
            boolean airVsInfra,
            SuccessType success,
            double infra
    ) {
        int victoryType = success.ordinal();
        if (victoryType == 0) {
            return KeyValue.of(0d, 0d);
        }

        double min = Math.max(
                Math.min((attAir - (defAir * 0.5)) * 0.35353535 * 0.85 * (victoryType / 3d), infra * 0.5 + 100),
                0
        );
        double max = Math.max(
                Math.min((attAir - (defAir * 0.5)) * 0.35353535 * 1.05 * (victoryType / 3d), infra * 0.5 + 100),
                0
        );

        if (!airVsInfra) {
            min /= 3;
            max /= 3;
        }

        return KeyValue.of(min, max);
    }

    private static double victoryFactor(SuccessType victory) {
        return switch (victory.ordinal()) {
            case 1 -> 0.4;
            case 2 -> 0.7;
            case 3 -> 1.0;
            default -> 0.0;
        };
    }

    private static Map.Entry<Integer, Integer> scaleRange(double min, double max, double factor) {
        return KeyValue.of(
                (int) Math.round(min * factor),
                (int) Math.round(max * factor)
        );
    }

    private static Map.Entry<Integer, Integer> scaleRange(Map.Entry<Integer, Integer> range, double factor) {
        return scaleRange(range.getKey(), range.getValue(), factor);
    }

    private static long packRange(int min, int max) {
        return ((long) min << 32) | (max & 0xffffffffL);
    }

    private static int rangeMin(long range) {
        return (int) (range >> 32);
    }

    private static int rangeMax(long range) {
        return (int) range;
    }

    private static long scaleRangePacked(double min, double max, double factor) {
        return packRange((int) Math.round(min * factor), (int) Math.round(max * factor));
    }

    private static long scaleRangePacked(long range, double factor) {
        return scaleRangePacked(rangeMin(range), rangeMax(range), factor);
    }

    private static long scaleRangePacked(Map.Entry<Integer, Integer> range, double factor) {
        return scaleRangePacked(range.getKey(), range.getValue(), factor);
    }

    private static void putIfNonZero(
            CasualtyRangeWriter writer,
            boolean attackerSide,
            MilitaryUnit unit,
            int min,
            int max
    ) {
        if (min == 0 && max == 0) {
            return;
        }
        if (attackerSide) {
            writer.putAttackerRange(unit, min, max);
        } else {
            writer.putDefenderRange(unit, min, max);
        }
    }

    private static void putIfNonZero(
            CasualtyRangeWriter writer,
            boolean attackerSide,
            MilitaryUnit unit,
            long range
    ) {
        putIfNonZero(writer, attackerSide, unit, rangeMin(range), rangeMax(range));
    }

    private static void putIfNonZero(
            CasualtyRangeWriter writer,
            boolean attackerSide,
            MilitaryUnit unit,
            Map.Entry<Integer, Integer> range
    ) {
        putIfNonZero(writer, attackerSide, unit, range.getKey(), range.getValue());
    }

    private static void putIfNonZero(
            Map<MilitaryUnit, Map.Entry<Integer, Integer>> map,
            MilitaryUnit unit,
            Map.Entry<Integer, Integer> range
    ) {
        if (range.getKey() != 0 || range.getValue() != 0) {
            map.put(unit, range);
        }
    }

    private static Map.Entry<Integer, Integer> maxCityDamage(CasualtyNationView defender, boolean nuke) {
        int min = 0;
        int max = 0;

        for (int cityIndex = 0; cityIndex < defender.cityCount(); cityIndex++) {
            Map.Entry<Integer, Integer> damage = nuke
                    ? defender.cityNukeDamage(cityIndex)
                    : defender.cityMissileDamage(cityIndex);
            min = Math.max(min, damage.getKey());
            max = Math.max(max, damage.getValue());
        }

        return KeyValue.of(min, max);
    }

    private static long maxCityDamagePacked(CasualtyNationView defender, boolean nuke) {
        int min = 0;
        int max = 0;

        for (int cityIndex = 0; cityIndex < defender.cityCount(); cityIndex++) {
            int damageMin = nuke
                    ? defender.cityNukeDamageMin(cityIndex)
                    : defender.cityMissileDamageMin(cityIndex);
            int damageMax = nuke
                    ? defender.cityNukeDamageMax(cityIndex)
                    : defender.cityMissileDamageMax(cityIndex);
            min = Math.max(min, damageMin);
            max = Math.max(max, damageMax);
        }

        return packRange(min, max);
    }

    private Map.Entry<Integer, Integer> getAirTargetCasualties(
            MilitaryUnit unit,
            double killRatio,
            int baseUnits,
            double baseFactor,
            int attAir,
            int defAir,
            Function<MilitaryUnit, Integer> defenderUnits,
            SuccessType victory,
            double defModifier
    ) {
        if (victory == SuccessType.UTTER_FAILURE) {
            return ZERO_RANGE;
        }

        double factor = victoryFactor(victory);
        int enemyUnits = defenderUnits.apply(unit);
        double randMin = (attAir - defAir * 0.5) * killRatio * 0.85 * defModifier;
        double randMax = (attAir - defAir * 0.5) * killRatio * 1.05 * defModifier;
        long upperBound = Math.round(Math.max(Math.min(enemyUnits, enemyUnits * baseFactor + baseUnits), 0));

        int min = (int) (Math.min(upperBound, randMin) * factor);
        int max = (int) (Math.min(upperBound, randMax) * factor);
        return KeyValue.of(min, max);
    }

    private long airTargetCasualtiesPacked(
            MilitaryUnit unit,
            double killRatio,
            int baseUnits,
            double baseFactor,
            int attAir,
            int defAir,
            CasualtyNationView defender,
            SuccessType victory,
            double defModifier
    ) {
        if (victory == SuccessType.UTTER_FAILURE) {
            return packRange(0, 0);
        }

        double factor = victoryFactor(victory);
        int enemyUnits = defender.getUnits(unit);
        double randMin = (attAir - defAir * 0.5) * killRatio * 0.85 * defModifier;
        double randMax = (attAir - defAir * 0.5) * killRatio * 1.05 * defModifier;
        long upperBound = Math.round(Math.max(Math.min(enemyUnits, enemyUnits * baseFactor + baseUnits), 0));

        int min = (int) (Math.min(upperBound, randMin) * factor);
        int max = (int) (Math.min(upperBound, randMax) * factor);
        return packRange(min, max);
    }

    private static long scaledAirInfraCasualtiesPacked(
            int attAir,
            int defAir,
            boolean airVsInfra,
            SuccessType success,
            double infra,
            double infraModifier
    ) {
        int victoryType = success.ordinal();
        if (victoryType == 0) {
            return packRange(0, 0);
        }

        double min = Math.max(
                Math.min((attAir - (defAir * 0.5)) * 0.35353535 * 0.85 * (victoryType / 3d), infra * 0.5 + 100),
                0
        );
        double max = Math.max(
                Math.min((attAir - (defAir * 0.5)) * 0.35353535 * 1.05 * (victoryType / 3d), infra * 0.5 + 100),
                0
        );

        if (!airVsInfra) {
            min /= 3;
            max /= 3;
        }

        return scaleRangePacked(min, max, infraModifier);
    }

    private static void writeAirstrikeCasualties(
            CasualtyRangeWriter casualties,
            int attUnits,
            int defUnits,
            SuccessType victory,
            boolean airVsAir,
            double attModifier,
            double defModifier
    ) {
        double attStr = attUnits * 3;
        double defStr = defUnits * 3;
        int minAtt = 0;
        int minDef = 0;
        int maxAtt = 0;
        int maxDef = 0;

        double attCasualties = airVsAir ? 0.01 : 0.015385;
        double defCasualties = airVsAir ? 0.018337 : 0.009091;

        int failures = 3 - victory.ordinal();
        int successes = victory.ordinal();

        for (int i = 0; i < failures; i++) {
            maxAtt += defStr * attCasualties * attModifier;
            minAtt += Math.max(defStr * 0.4, attStr * 0.4 + 1) * attCasualties * attModifier;

            maxDef += Math.min(attStr, defStr - 1) * defCasualties * defModifier;
            minDef += attStr * 0.4 * defCasualties * defModifier;
        }

        for (int i = 0; i < successes; i++) {
            maxAtt += Math.min(defStr, attStr - 1) * attCasualties * attModifier;
            minAtt += defStr * 0.4 * attCasualties * attModifier;

            maxDef += attStr * defCasualties * defModifier;
            minDef += Math.max(attStr * 0.4, defStr * 0.4 + 1) * defCasualties * defModifier;
        }

        minAtt = Math.min(attUnits, minAtt);
        maxAtt = Math.min(attUnits, maxAtt);
        minDef = Math.min(defUnits, minDef);
        maxDef = Math.min(defUnits, maxDef);

        putIfNonZero(casualties, true, MilitaryUnit.AIRCRAFT, minAtt, maxAtt);
        putIfNonZero(casualties, false, MilitaryUnit.AIRCRAFT, minDef, maxDef);
    }

    private void inputAirCasualties(
            CasualtyRangeWriter casualties,
            CasualtyNationView attacker,
            CasualtyNationView defender,
            SuccessType victory,
            double infraModifier,
            double attModifier,
            double defModifier
    ) {
        int attAir = attacker.getAircraft();
        int defAir = defender.getAircraft();
        boolean vsAir = this == AIRSTRIKE_AIRCRAFT;
        boolean vsInfra = this == AIRSTRIKE_INFRA;

        writeAirstrikeCasualties(casualties, attAir, defAir, victory, vsAir, attModifier, defModifier);

        if (victory == SuccessType.UTTER_FAILURE) {
            return;
        }

        putIfNonZero(
                casualties,
                false,
                MilitaryUnit.INFRASTRUCTURE,
                scaledAirInfraCasualtiesPacked(attAir, defAir, vsInfra, victory, defender.maxCityInfra(), infraModifier)
        );

        switch (this) {
            case AIRSTRIKE_SOLDIER -> putIfNonZero(
                    casualties,
                    false,
                    MilitaryUnit.SOLDIER,
                    airTargetCasualtiesPacked(
                            MilitaryUnit.SOLDIER, 35, 1000, 0.75,
                            attAir, defAir, defender, victory, defModifier
                    )
            );
            case AIRSTRIKE_TANK -> putIfNonZero(
                    casualties,
                    false,
                    MilitaryUnit.TANK,
                    airTargetCasualtiesPacked(
                            MilitaryUnit.TANK, 1.25, 10, 0.75,
                            attAir, defAir, defender, victory, defModifier
                    )
            );
            case AIRSTRIKE_MONEY -> {
                double factor = victoryFactor(victory);
                putIfNonZero(
                        casualties,
                        false,
                        MilitaryUnit.MONEY,
                        (int) (attAir * 0.85 * 3000 * factor),
                        (int) (attAir * 1.05 * 3000 * factor)
                );
            }
            case AIRSTRIKE_SHIP -> putIfNonZero(
                    casualties,
                    false,
                    MilitaryUnit.SHIP,
                    airTargetCasualtiesPacked(
                            MilitaryUnit.SHIP, 0.0285, 4, 0.5,
                            attAir, defAir, defender, victory, defModifier
                    )
            );
            case AIRSTRIKE_AIRCRAFT, AIRSTRIKE_INFRA -> {
            }
            default -> throw new IllegalArgumentException("Cannot get air casualties for " + this);
        }
    }

    private void inputNavalCasualties(
            CasualtyRangeWriter casualties,
            CasualtyNationView attacker,
            CasualtyNationView defender,
            SuccessType victory,
            double infraFactor,
            double attModifier,
            double defModifier
    ) {
        double navalFactor = 1.3;

        switch (this) {
            case NAVAL -> {
                navalFactor *= 1.3;
                infraFactor *= 0.7;
            }
            case NAVAL_INFRA -> navalFactor *= 0.7;
            case NAVAL_AIR, NAVAL_GROUND -> {
                navalFactor *= 0.7;
                infraFactor *= 0.7;
            }
            default -> throw new IllegalArgumentException("Cannot get naval casualties for " + this);
        }

        int attShips = attacker.getShips();
        int defShips = defender.getShips();

        if (victory != SuccessType.UTTER_FAILURE) {
            double maxCityInfra = defender.maxCityInfra();
            double infraMin = Math.max(Math.min((attShips - (defShips * 0.5)) * 2.625 * 0.85 * (victory.ordinal() / 3d), maxCityInfra * 0.5 + 25), 0);
            double infraMax = Math.max(Math.min((attShips - (defShips * 0.5)) * 2.625 * 1.05 * (victory.ordinal() / 3d), maxCityInfra * 0.5 + 25), 0);
            putIfNonZero(casualties, false, MilitaryUnit.INFRASTRUCTURE, scaleRangePacked(infraMin, infraMax, infraFactor));
        }

        int failures = 3 - victory.ordinal();
        int successes = victory.ordinal();
        int attStr = attShips * 4;
        int defStr = defShips * 4;
        int attLossMin = 0;
        int attLossMax = 0;
        int defLossMin = 0;
        int defLossMax = 0;

        if (failures > 0) {
            double attStrMin = attStr * 0.4;
            double attStrMax = Math.min(attStr, defStr - 1);
            double defStrMin = Math.max(attStr * 0.4 + 1, defStr * 0.4);
            double defStrMax = defStr;

            attLossMin += (int) Math.round((defStrMin * 0.01375 * navalFactor * attModifier) * failures);
            attLossMax += (int) Math.round((defStrMax * 0.01375 * navalFactor * attModifier) * failures);
            defLossMin += (int) Math.round((attStrMin * 0.01375 * navalFactor * defModifier) * failures);
            defLossMax += (int) Math.round((attStrMax * 0.01375 * navalFactor * defModifier) * failures);
        }

        if (successes > 0) {
            double attStrMin = Math.max(defStr * 0.4 + 1, attStr * 0.4);
            double attStrMax = attStr;
            double defStrMin = defStr * 0.4;
            double defStrMax = Math.min(defStr, attStr - 1);

            attLossMin += (int) Math.round((defStrMin * 0.01375 * navalFactor * attModifier) * successes);
            attLossMax += (int) Math.round((defStrMax * 0.01375 * navalFactor * attModifier) * successes);
            defLossMin += (int) Math.round((attStrMin * 0.01375 * navalFactor * defModifier) * successes);
            defLossMax += (int) Math.round((attStrMax * 0.01375 * navalFactor * defModifier) * successes);
        }

        attLossMin = Math.min(attLossMin, attShips);
        attLossMax = Math.min(attLossMax, attShips);
        defLossMin = Math.min(defLossMin, defShips);
        defLossMax = Math.min(defLossMax, defShips);

        putIfNonZero(casualties, true, MilitaryUnit.SHIP, attLossMin, attLossMax);
        putIfNonZero(casualties, false, MilitaryUnit.SHIP, defLossMin, defLossMax);
    }

    private void inputGroundCasualties(
            CasualtyRangeWriter casualties,
            CasualtyNationView attacker,
            CasualtyNationView defender,
            SuccessType victory,
            double lootFactor,
            double infraFactor,
            double attModifier,
            double defModifier,
            boolean defAirControl,
            boolean attAirControl,
            boolean equipAttackerSoldiers,
            boolean equipDefenderSoldiers,
            boolean attGroundControl
    ) {
        if (victory != SuccessType.UTTER_FAILURE) {
            double soldiersStoleMoney = attacker.getSoldiers() * 1.1;
            double tankStoleMoney = attacker.getTanks() * 25.15;
            double minStolen = (soldiersStoleMoney + tankStoleMoney) * victory.ordinal() * 0.8 * lootFactor;
            double maxStolen = (soldiersStoleMoney + tankStoleMoney) * victory.ordinal() * 1.1 * lootFactor;
            putIfNonZero(casualties, false, MilitaryUnit.MONEY, (int) Math.round(minStolen), (int) Math.round(maxStolen));

            double maxCityInfra = defender.maxCityInfra();
            double infraMin = Math.max(Math.min(((attacker.getSoldiers() - (defender.getSoldiers() * 0.5)) * 0.000606061 + (attacker.getTanks() - (defender.getTanks() * 0.5)) * 0.01) * 0.85 * (victory.ordinal() / 3d), maxCityInfra * 0.2 + 25), 0);
            double infraMax = Math.max(Math.min(((attacker.getSoldiers() - (defender.getSoldiers() * 0.5)) * 0.000606061 + (attacker.getTanks() - (defender.getTanks() * 0.5)) * 0.01) * 1.05 * (victory.ordinal() / 3d), maxCityInfra * 0.2 + 25), 0);
            putIfNonZero(casualties, false, MilitaryUnit.INFRASTRUCTURE, scaleRangePacked(infraMin, infraMax, infraFactor));
        }

        double attStrS = attacker.getSoldiers() * (equipAttackerSoldiers ? 1.75 : 1);
        double attStrT = attacker.getTanks() * 40 * (defAirControl ? 0.5 : 1);
        double attStr = attStrS + attStrT;

        double defStrS = defender.getSoldiers() * (equipDefenderSoldiers ? 1.75 : 1);
        double defStrT = defender.getTanks() * 40 * (attAirControl ? 0.5 : 1);
        double defStr = defStrS + defStrT;

        double attSoldierLossMin = 0;
        double attSoldierLossMax = 0;
        double defSoldierLossMin = 0;
        double defSoldierLossMax = 0;
        double attTankLossMin = 0;
        double attTankLossMax = 0;
        double defTankLossMin = 0;
        double defTankLossMax = 0;

        int failures = 3 - victory.ordinal();
        int successes = victory.ordinal();

        if (failures > 0) {
            double attStrMin = attStr * 0.4;
            double attStrMax = Math.min(attStr, defStr - 1);
            double defStrMin = Math.max(attStr * 0.4 + 1, defStr * 0.4);
            double defStrMax = defStr;

            double attStrMinS = attStrS * attStrMin / attStr;
            double attStrMaxS = attStrS * attStrMax / attStr;
            double defStrMinS = defStrS * defStrMin / defStr;
            double defStrMaxS = defStrS * defStrMax / defStr;
            double attStrMinT = attStrT * attStrMin / attStr;
            double attStrMaxT = attStrT * attStrMax / attStr;
            double defStrMinT = defStrT * defStrMin / defStr;
            double defStrMaxT = defStrT * defStrMax / defStr;

            attSoldierLossMin += ((defStrMinS * 0.0084) + (defStrMinT * 0.0092)) * failures;
            attSoldierLossMax += ((defStrMaxS * 0.0084) + (defStrMaxT * 0.0092)) * failures;
            defSoldierLossMin += ((attStrMinS * 0.0084) + (attStrMinT * 0.0092)) * failures;
            defSoldierLossMax += ((attStrMaxS * 0.0084) + (attStrMaxT * 0.0092)) * failures;
            attTankLossMin += ((defStrMinS * 0.00043225806) + (defStrMinT * 0.00070967741)) * failures;
            attTankLossMax += ((defStrMaxS * 0.00043225806) + (defStrMaxT * 0.00070967741)) * failures;
            defTankLossMin += ((attStrMinS * 0.0004060606) + (attStrMinT * 0.00066666666)) * failures;
            defTankLossMax += ((attStrMaxS * 0.0004060606) + (attStrMaxT * 0.00066666666)) * failures;
        }

        if (successes > 0) {
            if (attGroundControl && defender.getAircraft() > 0) {
                double factor = switch (successes) {
                    case 3 -> 0.005025;
                    case 2 -> 0.00335;
                    case 1 -> 0.001675;
                    default -> 0;
                };
                int killed = Math.min(defender.getAircraft(), (int) Math.round(attacker.getTanks() * factor));
                if (killed > 0) {
                    casualties.putDefenderRange(MilitaryUnit.AIRCRAFT, killed, killed);
                }
            }

            double attStrMin = Math.max(defStr * 0.4 + 1, attStr * 0.4);
            double attStrMax = attStr;
            double defStrMin = defStr * 0.4;
            double defStrMax = Math.min(defStr, attStr - 1);

            double attStrMinS = attStrS * attStrMin / attStr;
            double attStrMaxS = attStrS * attStrMax / attStr;
            double defStrMinS = defStrS * defStrMin / defStr;
            double defStrMaxS = defStrS * defStrMax / defStr;
            double attStrMinT = attStrT * attStrMin / attStr;
            double attStrMaxT = attStrT * attStrMax / attStr;
            double defStrMinT = defStrT * defStrMin / defStr;
            double defStrMaxT = defStrT * defStrMax / defStr;

            attSoldierLossMin += ((defStrMinS * 0.0084) + (defStrMinT * 0.0092)) * successes;
            attSoldierLossMax += ((defStrMaxS * 0.0084) + (defStrMaxT * 0.0092)) * successes;
            defSoldierLossMin += ((attStrMinS * 0.0084) + (attStrMinT * 0.0092)) * successes;
            defSoldierLossMax += ((attStrMaxS * 0.0084) + (attStrMaxT * 0.0092)) * successes;
            attTankLossMin += ((defStrMinS * 0.0004060606) + (defStrMinT * 0.00066666666)) * successes;
            attTankLossMax += ((defStrMaxS * 0.0004060606) + (defStrMaxT * 0.00066666666)) * successes;
            defTankLossMin += ((attStrMinS * 0.00043225806) + (attStrMinT * 0.00070967741)) * successes;
            defTankLossMax += ((attStrMaxS * 0.00043225806) + (attStrMaxT * 0.00070967741)) * successes;
        }

        defSoldierLossMin = Math.min(defender.getSoldiers(), defSoldierLossMin * defModifier);
        defSoldierLossMax = Math.min(defender.getSoldiers(), defSoldierLossMax * defModifier);
        defTankLossMin = Math.min(defender.getTanks(), defTankLossMin * defModifier);
        defTankLossMax = Math.min(defender.getTanks(), defTankLossMax * defModifier);
        attSoldierLossMin = Math.min(attacker.getSoldiers(), attSoldierLossMin * attModifier);
        attSoldierLossMax = Math.min(attacker.getSoldiers(), attSoldierLossMax * attModifier);
        attTankLossMin = Math.min(attacker.getTanks(), attTankLossMin * attModifier);
        attTankLossMax = Math.min(attacker.getTanks(), attTankLossMax * attModifier);

        putIfNonZero(casualties, false, MilitaryUnit.SOLDIER, (int) Math.round(defSoldierLossMin), (int) Math.round(defSoldierLossMax));
        putIfNonZero(casualties, false, MilitaryUnit.TANK, (int) Math.round(defTankLossMin), (int) Math.round(defTankLossMax));
        putIfNonZero(casualties, true, MilitaryUnit.SOLDIER, (int) Math.round(attSoldierLossMin), (int) Math.round(attSoldierLossMax));
        putIfNonZero(casualties, true, MilitaryUnit.TANK, (int) Math.round(attTankLossMin), (int) Math.round(attTankLossMax));
    }

    @Command(desc = "Get the minimum unit casualties for the attacker.")
    public int getAttackerMinCasualties(MilitaryUnit unit, DBNation attacker, DBNation defender, @Default("IMMENSE_TRIUMPH") SuccessType victory, @Default("raid") WarType warType, @Default("false") boolean defAirControl, @Default("false") boolean attAirControl, @Default("false") boolean defFortified, @Default("true") boolean equipAttackerSoldiers, @Default("true") boolean equipDefenderSoldiers, @Default("false") boolean attGroundControl) {
        return getCasualtyBound(unit, attacker, defender, victory, warType, true, defAirControl, attAirControl, defFortified, equipAttackerSoldiers, equipDefenderSoldiers, attGroundControl, true, Bound.MIN);
    }

    @Command(desc = "Get the maximum unit casualties for the attacker.")
    public int getAttackerMaxCasualties(MilitaryUnit unit, DBNation attacker, DBNation defender, @Default("IMMENSE_TRIUMPH") SuccessType victory, @Default("raid") WarType warType, @Default("false") boolean defAirControl, @Default("false") boolean attAirControl, @Default("false") boolean defFortified, @Default("true") boolean equipAttackerSoldiers, @Default("true") boolean equipDefenderSoldiers, @Default("false") boolean attGroundControl) {
        return getCasualtyBound(unit, attacker, defender, victory, warType, true, defAirControl, attAirControl, defFortified, equipAttackerSoldiers, equipDefenderSoldiers, attGroundControl, true, Bound.MAX);
    }

    @Command(desc = "Get the average unit casualties for the attacker.")
    public int getAttackerAvgCasualties(MilitaryUnit unit, DBNation attacker, DBNation defender, @Default("IMMENSE_TRIUMPH") SuccessType victory, @Default("raid") WarType warType, @Default("false") boolean defAirControl, @Default("false") boolean attAirControl, @Default("false") boolean defFortified, @Default("true") boolean equipAttackerSoldiers, @Default("true") boolean equipDefenderSoldiers, @Default("false") boolean attGroundControl) {
        return getCasualtyBound(unit, attacker, defender, victory, warType, true, defAirControl, attAirControl, defFortified, equipAttackerSoldiers, equipDefenderSoldiers, attGroundControl, true, Bound.AVG);
    }

    @Command(desc = "Get the minimum unit casualties for the defender.")
    public int getDefenderMinCasualties(MilitaryUnit unit, DBNation attacker, DBNation defender, @Default("IMMENSE_TRIUMPH") SuccessType victory, @Default("raid") WarType warType, @Default("false") boolean defAirControl, @Default("false") boolean attAirControl, @Default("false") boolean defFortified, @Default("true") boolean equipAttackerSoldiers, @Default("true") boolean equipDefenderSoldiers, @Default("false") boolean attGroundControl) {
        return getCasualtyBound(unit, attacker, defender, victory, warType, true, defAirControl, attAirControl, defFortified, equipAttackerSoldiers, equipDefenderSoldiers, attGroundControl, false, Bound.MIN);
    }

    @Command(desc = "Get the maximum unit casualties for the defender.")
    public int getDefenderMaxCasualties(MilitaryUnit unit, DBNation attacker, DBNation defender, @Default("IMMENSE_TRIUMPH") SuccessType victory, @Default("raid") WarType warType, @Default("false") boolean defAirControl, @Default("false") boolean attAirControl, @Default("false") boolean defFortified, @Default("true") boolean equipAttackerSoldiers, @Default("true") boolean equipDefenderSoldiers, @Default("false") boolean attGroundControl) {
        return getCasualtyBound(unit, attacker, defender, victory, warType, true, defAirControl, attAirControl, defFortified, equipAttackerSoldiers, equipDefenderSoldiers, attGroundControl, false, Bound.MAX);
    }

    @Command(desc = "Get the average unit casualties for the defender.")
    public int getDefenderAvgCasualties(MilitaryUnit unit, DBNation attacker, DBNation defender, @Default("IMMENSE_TRIUMPH") SuccessType victory, @Default("raid") WarType warType, @Default("false") boolean defAirControl, @Default("false") boolean attAirControl, @Default("false") boolean defFortified, @Default("true") boolean equipAttackerSoldiers, @Default("true") boolean equipDefenderSoldiers, @Default("false") boolean attGroundControl) {
        return getCasualtyBound(unit, attacker, defender, victory, warType, true, defAirControl, attAirControl, defFortified, equipAttackerSoldiers, equipDefenderSoldiers, attGroundControl, false, Bound.AVG);
    }

    private int getCasualtyBound(
            MilitaryUnit unit,
            DBNation attacker,
            DBNation defender,
            SuccessType victory,
            WarType warType,
            boolean attackerIsOriginalAttacker,
            boolean defAirControl,
            boolean attAirControl,
            boolean defFortified,
            boolean equipAttackerSoldiers,
            boolean equipDefenderSoldiers,
            boolean attGroundControl,
            boolean attackerSide,
            Bound bound
    ) {
        Map.Entry<Map<MilitaryUnit, Map.Entry<Integer, Integer>>, Map<MilitaryUnit, Map.Entry<Integer, Integer>>> casualties =
                getCasualties(attacker, defender, victory, warType, attackerIsOriginalAttacker, defAirControl, attAirControl, defFortified, equipAttackerSoldiers, equipDefenderSoldiers, attGroundControl);

        Map<MilitaryUnit, Map.Entry<Integer, Integer>> side = attackerSide ? casualties.getKey() : casualties.getValue();
        return bound.apply(side.getOrDefault(unit, ZERO_RANGE));
    }

    public Map.Entry<Map<MilitaryUnit, Map.Entry<Integer, Integer>>, Map<MilitaryUnit, Map.Entry<Integer, Integer>>> getCasualties(
            CasualtyNationView attacker,
            CasualtyNationView defender,
            SuccessType victory,
            WarType type,
            boolean defAirControl,
            boolean attAirControl,
            boolean defFortified,
            boolean equipAttackerSoldiers,
            boolean equipDefenderSoldiers,
            boolean attGroundControl
        ) {
        return getCasualties(
            attacker,
            defender,
            victory,
            type,
            true,
            defAirControl,
            attAirControl,
            defFortified,
            equipAttackerSoldiers,
            equipDefenderSoldiers,
            attGroundControl
        );
        }

        public Map.Entry<Map<MilitaryUnit, Map.Entry<Integer, Integer>>, Map<MilitaryUnit, Map.Entry<Integer, Integer>>> getCasualties(
            CasualtyNationView attacker,
            CasualtyNationView defender,
            SuccessType victory,
            WarType type,
            boolean attackerIsOriginalAttacker,
            boolean defAirControl,
            boolean attAirControl,
            boolean defFortified,
            boolean equipAttackerSoldiers,
            boolean equipDefenderSoldiers,
            boolean attGroundControl
    ) {
        Map<MilitaryUnit, Map.Entry<Integer, Integer>> attackerCasualties = new Object2ObjectArrayMap<>(6);
        Map<MilitaryUnit, Map.Entry<Integer, Integer>> defenderCasualties = new Object2ObjectArrayMap<>(6);
        writeCasualties(
                attacker,
                defender,
                victory,
                type,
                attackerIsOriginalAttacker,
                defAirControl,
                attAirControl,
                defFortified,
                equipAttackerSoldiers,
                equipDefenderSoldiers,
                attGroundControl,
                new MapCasualtyRangeWriter(attackerCasualties, defenderCasualties)
        );

        return KeyValue.of(attackerCasualties, defenderCasualties);
    }

    public void writeCasualties(
            CasualtyNationView attacker,
            CasualtyNationView defender,
            SuccessType victory,
            WarType type,
            boolean attackerIsOriginalAttacker,
            boolean defAirControl,
            boolean attAirControl,
            boolean defFortified,
            boolean equipAttackerSoldiers,
            boolean equipDefenderSoldiers,
            boolean attGroundControl,
            CasualtyRangeWriter casualties
    ) {
        casualties.clearCasualtyRanges();
        double infraFactor = (1 + (attacker.infraAttackModifier(this) - 1) + (defender.infraDefendModifier(this) - 1))
            * WarRoleModifiers.infraModifier(type, attackerIsOriginalAttacker);
        double lootFactor = (1 + (attacker.looterModifier(true) - 1) + (defender.lootModifier() - 1))
            * WarRoleModifiers.lootModifier(type, attackerIsOriginalAttacker);
        double defModifier = attacker.isBlitzkrieg() ? 1.1 : 1;
        double attModifier = defFortified ? 1.33 : 1;

        switch (this) {
            case NAVAL, NAVAL_INFRA, NAVAL_AIR, NAVAL_GROUND ->
                    inputNavalCasualties(casualties, attacker, defender, victory, infraFactor, attModifier, defModifier);

            case GROUND ->
                    inputGroundCasualties(casualties, attacker, defender, victory, lootFactor, infraFactor, attModifier, defModifier, defAirControl, attAirControl, equipAttackerSoldiers, equipDefenderSoldiers, attGroundControl);

            case NUKE -> {
                casualties.putAttackerRange(MilitaryUnit.NUKE, 1, 1);
                if (!ProjectileDefenseMath.isIntercepted(this, victory)) {
                    putIfNonZero(
                            casualties,
                            false,
                            MilitaryUnit.INFRASTRUCTURE,
                            scaleRangePacked(maxCityDamagePacked(defender, true), infraFactor)
                    );
                }
            }

            case MISSILE -> {
                casualties.putAttackerRange(MilitaryUnit.MISSILE, 1, 1);
                if (!ProjectileDefenseMath.isIntercepted(this, victory)) {
                    putIfNonZero(
                            casualties,
                            false,
                            MilitaryUnit.INFRASTRUCTURE,
                            scaleRangePacked(maxCityDamagePacked(defender, false), infraFactor)
                    );
                }
            }

            case AIRSTRIKE_INFRA, AIRSTRIKE_SOLDIER, AIRSTRIKE_TANK, AIRSTRIKE_MONEY, AIRSTRIKE_SHIP, AIRSTRIKE_AIRCRAFT ->
                    inputAirCasualties(casualties, attacker, defender, victory, infraFactor, attModifier, defModifier);

            default -> throw new IllegalArgumentException("Cannot get casualties for " + this);
        }
    }

    public Map.Entry<Map<MilitaryUnit, Map.Entry<Integer, Integer>>, Map<MilitaryUnit, Map.Entry<Integer, Integer>>> getCasualties(
            DBNation attacker,
            DBNation defender,
            SuccessType victory,
            WarType type,
            boolean defAirControl,
            boolean attAirControl,
            boolean defFortified,
            boolean equipAttackerSoldiers,
            boolean equipDefenderSoldiers,
            boolean attGroundControl
        ) {
        return getCasualties(
            attacker,
            defender,
            victory,
            type,
            true,
            defAirControl,
            attAirControl,
            defFortified,
            equipAttackerSoldiers,
            equipDefenderSoldiers,
            attGroundControl
        );
        }

        public Map.Entry<Map<MilitaryUnit, Map.Entry<Integer, Integer>>, Map<MilitaryUnit, Map.Entry<Integer, Integer>>> getCasualties(
            DBNation attacker,
            DBNation defender,
            SuccessType victory,
            WarType type,
            boolean attackerIsOriginalAttacker,
            boolean defAirControl,
            boolean attAirControl,
            boolean defFortified,
            boolean equipAttackerSoldiers,
            boolean equipDefenderSoldiers,
            boolean attGroundControl
    ) {
        return getCasualties(
                new DBNationView(attacker),
                new DBNationView(defender),
                victory,
                type,
                attackerIsOriginalAttacker,
                defAirControl,
                attAirControl,
                defFortified,
                equipAttackerSoldiers,
                equipDefenderSoldiers,
                attGroundControl
        );
    }

    private final MilitaryUnit[] units;
    private final String name;
    private final int mapUsed;
    private final int resistanceIT;

    AttackType(int mapUsed, int resistanceIT, MilitaryUnit... units) {
        this(null, mapUsed, resistanceIT, units);
    }

    AttackType(String name, int mapUsed, int resistanceIT, MilitaryUnit... units) {
        this.units = units;
        this.name = name == null ? name() : name;
        this.mapUsed = mapUsed;
        this.resistanceIT = resistanceIT;
    }

    @Command(desc = "Resistance points dealt for a successful attack")
    public int getResistanceIT() {
        return resistanceIT;
    }

    @Command(desc = "Resistance points dealt for an attack with the given success types")
    public int getResistance(SuccessType success) {
        if (this == MISSILE || this == NUKE) {
            return success == SuccessType.UTTER_FAILURE ? 0 : getResistanceIT();
        }
        return getResistanceIT() - (9 - success.ordinal() * 3);
    }

    @Command(desc = "The Military Action Points (MAP) used for this attack type")
    public int getMapUsed() {
        return mapUsed;
    }

    @Command(desc = "The name of this attack type")
    public String getName() {
        return name;
    }

    public static final AttackType[] VALUES = values();
    public static final AttackType[] values = VALUES;

    public static AttackType get(String input) {
        String normalized = input.toUpperCase(Locale.ROOT);
        if (normalized.endsWith("F")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return switch (normalized) {
            case "AIRSTRIKE1" -> AIRSTRIKE_INFRA;
            case "AIRSTRIKE2" -> AIRSTRIKE_SOLDIER;
            case "AIRSTRIKE3" -> AIRSTRIKE_TANK;
            case "AIRSTRIKE4" -> AIRSTRIKE_MONEY;
            case "AIRSTRIKE5" -> AIRSTRIKE_SHIP;
            case "AIRSTRIKE6" -> AIRSTRIKE_AIRCRAFT;
            case "NAVALINFRA" -> NAVAL_INFRA;
            case "NAVALGROUND" -> NAVAL_GROUND;
            case "NAVALAIR" -> NAVAL_AIR;
            case "NAVALSHIPS" -> NAVAL;
            default -> valueOf(normalized);
        };
    }

    @Command(desc = "Get the unit type at the given index")
    public MilitaryUnit getUnitType(int index) {
        return units.length > index ? units[index] : null;
    }

    public MilitaryUnit[] getUnits() {
        return units;
    }

    public Map<MilitaryUnit, Integer> getLosses(int a, int b, int c) {
        int[] amounts = {a, b, c};
        if (a == 0 && b == 0 && c == 0) {
            return Collections.emptyMap();
        }

        Map<MilitaryUnit, Integer> map = new Object2IntOpenHashMap<>(Math.min(units.length, amounts.length));
        for (int i = 0; i < units.length && i < amounts.length; i++) {
            if (amounts[i] != 0) {
                map.put(units[i], amounts[i]);
            }
        }
        return map;
    }

    @Command(desc = "If this attack type is a victory")
    public boolean isVictory() {
        return switch (this) {
            case VICTORY, A_LOOT -> true;
            default -> false;
        };
    }

    @Command(desc = "If this attack is one that can damage the enemy")
    public boolean canDamage() {
        return this != FORTIFY && this != PEACE;
    }

    public Map<ResourceType, Double> getConsumption(Function<MilitaryUnit, Integer> getUnits, boolean equipSoldiers) {
        double[] totals = new double[ResourceType.values.length];
        writeConsumption(getUnits, equipSoldiers, totals);
        return ResourceType.resourcesToMap(totals);
    }

    public void writeConsumption(Function<MilitaryUnit, Integer> getUnits, boolean equipSoldiers, double[] target) {
        Arrays.fill(target, 0d);
        if (target.length != ResourceType.values.length) {
            throw new IllegalArgumentException("target must be sized to ResourceType.values.length");
        }

        for (MilitaryUnit unit : units) {
            int amt = getUnits.apply(unit);
            if (amt <= 0) continue;
            if (unit == MilitaryUnit.SOLDIER && !equipSoldiers) continue;

            double[] consumePerUnit = unit.getConsumption();
            for (ResourceType type : RESOURCE_VALUES) {
                double consumeI = consumePerUnit[type.ordinal()];
                if (consumeI != 0) {
                    target[type.ordinal()] += consumeI * amt;
                }
            }
        }
    }

    public boolean isAir() {
        return switch (this) {
            case AIRSTRIKE_INFRA, AIRSTRIKE_SOLDIER, AIRSTRIKE_TANK,
                    AIRSTRIKE_MONEY, AIRSTRIKE_SHIP, AIRSTRIKE_AIRCRAFT -> true;
            default -> false;
        };
    }

    public boolean isNaval() {
        return switch (this) {
            case NAVAL, NAVAL_INFRA, NAVAL_AIR, NAVAL_GROUND -> true;
            default -> false;
        };
    }
}
