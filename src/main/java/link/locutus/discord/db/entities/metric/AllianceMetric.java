package link.locutus.discord.db.entities.metric;

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.csv.file.CitiesFile;
import link.locutus.discord.apiv3.csv.file.NationsFile;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.apiv3.csv.header.CityHeaderReader;
import link.locutus.discord.apiv3.csv.header.NationHeaderReader;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.db.entities.AttackCost;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.handlers.AttackQuery;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.scheduler.TriConsumer;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static link.locutus.discord.commands.manager.v2.table.TableNumberFormat.*;

public enum AllianceMetric implements IAllianceMetric {
    SOLDIER(false, SI_UNIT, new UnitMetric(MilitaryUnit.SOLDIER, f -> f.soldiers)),
    SOLDIER_PCT(true, PERCENTAGE_ONE, new UnitPctMetric(MilitaryUnit.SOLDIER, f -> f.soldiers)),
    TANK(false, SI_UNIT, new UnitMetric(MilitaryUnit.TANK, f -> f.tanks)),
    TANK_PCT(true, PERCENTAGE_ONE, new UnitPctMetric(MilitaryUnit.TANK, f -> f.tanks)),
    AIRCRAFT(false, SI_UNIT, new UnitMetric(MilitaryUnit.AIRCRAFT, f -> f.aircraft)),
    AIRCRAFT_PCT(true, PERCENTAGE_ONE, new UnitPctMetric(MilitaryUnit.AIRCRAFT, f -> f.aircraft)),
    SHIP(false, SI_UNIT, new UnitMetric(MilitaryUnit.SHIP, f -> f.ships)),
    SHIP_PCT(true, PERCENTAGE_ONE, new UnitPctMetric(MilitaryUnit.SHIP, f -> f.ships)),
    INFRA(false, SI_UNIT, new CountCityMetric(DBCity::getInfra, f -> f.infrastructure, AllianceMetricMode.TOTAL)),
    INFRA_AVG(true, DECIMAL_ROUNDED, new CountCityMetric(DBCity::getInfra, f -> f.infrastructure, AllianceMetricMode.PER_CITY)),
    LAND(false, SI_UNIT, new CountCityMetric(DBCity::getLand, f -> f.land, AllianceMetricMode.TOTAL)),
    LAND_AVG(true, DECIMAL_ROUNDED, new CountCityMetric(DBCity::getLand, f -> f.land, AllianceMetricMode.PER_CITY)),
    SCORE(false, SI_UNIT, new CountNationMetric(DBNation::getScore, f -> f.score)),
    SCORE_AVG(true, DECIMAL_ROUNDED, new CountNationMetric(DBNation::getScore, f -> f.score, AllianceMetricMode.PER_NATION)),
    CITY(false, SI_UNIT, new CountNationMetric(DBNation::getCities, f -> f.cities)),
    CITY_AVG(true, SI_UNIT, new CountNationMetric(DBNation::getCities, f -> f.cities, AllianceMetricMode.PER_NATION)),
    MEMBERS(false, SI_UNIT, new CountNationMetric(f -> 1)),
    MEMBERS_ACTIVE_1W(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            return (double) alliance.getNations(true, 1440 * 7, true).size();
        }
    },
    VM(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            return (double) alliance.getNations().stream().filter(f -> f.getVm_turns() != 0 && f.getPosition() > Rank.APPLICANT.id).count();
        }

        private final Map<Integer, Integer> vmByAA = new Int2IntOpenHashMap();

        @Override
        public void setupReaders(DataDumpImporter importer) {
            importer.setNationReader(this, (day, r) -> {
                Rank position = r.header.alliance_position.get();
                if (position.id <= Rank.APPLICANT.id) return;
                int allianceId = r.header.alliance_id.get();
                if (allianceId == 0) return;
                Integer vmTurns = r.header.vm_turns.get();
                if (vmTurns == null || vmTurns == 0) return;
                vmByAA.merge(allianceId, 1, Integer::sum);
            });
        }

        @Override
        public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
            Map<Integer, Double> result = vmByAA.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> (double) f.getValue()));
            vmByAA.clear();
            return result;
        }

        @Override
        public List<AllianceMetricValue> getAllValues() {
            return null;
        }
    },
    INACTIVE_1W(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            return (double) alliance.getNations().stream().filter(f -> f.getVm_turns() == 0 && f.getPosition() > Rank.APPLICANT.id && f.active_m() > 1440 * 7).count();
        }
    },
    VM_PCT(true, PERCENTAGE_ONE) {
        @Override
        public Double apply(DBAlliance alliance) {
            Set<DBNation> nations = alliance.getNations();
            return nations.stream().filter(f -> f.getVm_turns() != 0 && f.getPosition() > Rank.APPLICANT.id).count() / (double) nations.size();
        }

        private final Map<Integer, Integer> vmByAA = new Int2IntOpenHashMap();

        @Override
        public void setupReaders(DataDumpImporter importer) {
            importer.setNationReader(this, (day, r) -> {
                Rank position = r.header.alliance_position.get();
                if (position.id <= Rank.APPLICANT.id) return;
                int allianceId = r.header.alliance_id.get();
                if (allianceId == 0) return;
                Integer vm_turns = r.header.vm_turns.get();
                if (vm_turns == null || vm_turns == 0) return;
                vmByAA.merge(allianceId, 1, Integer::sum);
            });
        }

        @Override
        public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
            Map<Integer, Double> result = vmByAA.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> (double) f.getValue()));
            vmByAA.clear();
            return result;
        }

        @Override
        public List<AllianceMetricValue> getAllValues() {
            return null;
        }
    },
    INACTIVE_PCT(true, PERCENTAGE_ONE) {
        @Override
        public Double apply(DBAlliance alliance) {
            Set<DBNation> nations = alliance.getNations();
            return nations.stream().filter(f -> f.active_m() > 1440 * 7 && f.getPosition() > Rank.APPLICANT.id).count() / (double) nations.size();
        }
    },
    WARCOST_DAILY(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            Set<DBNation> nations = alliance.getNations();
            nations.removeIf(f -> f.getPosition() <= Rank.APPLICANT.id || f.getVm_turns() > 0);
            Set<Integer> nationIds = nations.stream().map(DBNation::getNation_id).collect(Collectors.toSet());

            AttackCost cost = new AttackCost("", "", false, false, false, false, false);
            long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
            Locutus.imp().getWarDb().iterateAttacksEither(nationIds, cutoff, (war, attack) -> {
                cost.addCost(attack, war, (w, a) -> nationIds.contains(a.getAttacker_id()), (w, b) -> nationIds.contains(b.getDefender_id()));
            });
            return cost.convertedTotal(true);
        }
    },
    REVENUE(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            Set<DBNation> nations = alliance.getNations();
            nations.removeIf(f -> f.isGray() || f.isBeige() || f.getPosition() <= Rank.APPLICANT.id || f.getVm_turns() > 0);

            double[] totalRss = ResourceType.getBuffer();
            for (DBNation nation : nations) {
                double[] revenue = nation.getRevenue();
                ResourceType.add(totalRss, revenue);
            }

            aaRevenueCache = new KeyValue<>(alliance.getAlliance_id(), totalRss);

            return ResourceType.convertedTotal(totalRss);
        }


        private final Map<Integer, DBNation> nationMap = new Int2ObjectOpenHashMap<>();
        private final Map<Integer, List<DBCity>> cityMap = new Int2ObjectOpenHashMap<>();
        private Map<DBWar, Long> warEndDates;

        @Override
        public void setupReaders(DataDumpImporter importer) {
            long minDate = importer.getParser().getMinDate();
            Map<Integer, DBWar> wars = Locutus.imp().getWarDb().getWarsSince(minDate - TimeUnit.DAYS.toMillis(5));
            AttackQuery query = Locutus.imp().getWarDb().queryAttacks().withWars(wars).withTypes(AttackType.PEACE, AttackType.VICTORY);
            warEndDates = importer.getParser().getUtil().getWarEndDates(wars, query);

            importer.setNationReader(this, (day, r) -> {
                Rank position = r.header.alliance_position.get();
                if (position.id <= Rank.APPLICANT.id) return;
                int allianceId = r.header.alliance_id.get();
                if (allianceId == 0) return;
                Integer vm_turns = r.header.vm_turns.get();
                if (vm_turns == null || vm_turns > 0) return;
                NationColor color = r.header.color.get();
                if (color == NationColor.GRAY || color == NationColor.BEIGE) return;
                DBNation nation = r.getNation( false, true);
                if (nation == null) return;
                nationMap.put(nation.getNation_id(), nation);
            });

            importer.setCityReader(this, (day, r) -> {
                int nationId = r.header.nation_id.get();
                DBNation nation = nationMap.get(nationId);
                if (nation == null) return;
                DBCity city = r.getCity();
                List<DBCity> cities = cityMap.computeIfAbsent(nationId, f -> new ObjectArrayList<>());
                cities.add(city);
            });
        }

        @Override
        public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
            Map<Integer, double[]> result = new Int2ObjectOpenHashMap<>();
            long date = TimeUtil.getTimeFromDay(day);
            Map<Continent, Double> radsMap = Locutus.imp().getNationDB().getRadiationByTurn(day);
            Set<Integer> nationsAtWar = importer.getParser().getUtil().getNationsAtWar(date, warEndDates);

            for (Map.Entry<Integer, DBNation> entry : nationMap.entrySet()) {
                DBNation nation = entry.getValue();
                int allianceId = nation.getAlliance_id();
                List<DBCity> cities = cityMap.get(nation.getNation_id());
                if (cities == null) continue;
                List<JavaCity> javaCities = cities.stream().map(f -> f.toJavaCity(nation)).toList();
                double[] buffer = result.computeIfAbsent(allianceId, f -> ResourceType.getBuffer());
                double rads = radsMap.getOrDefault(nation.getContinent(), 0d);
                boolean atWar = nationsAtWar.contains(nation.getNation_id());
                PW.getRevenue(buffer, 12, date, nation, javaCities, true, false, true, false, false, rads, atWar, 0);
            }
            importer.setRevenue(result);
            cityMap.clear();
            nationMap.clear();
            warEndDates.clear();

            return result.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> ResourceType.convertedTotal(f.getValue())));
        }

        @Override
        public List<AllianceMetricValue> getAllValues() {
            return null;
        }
    },
    OFFENSIVE_WARS(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            int total = 0;
            for (DBNation nation : alliance.getMemberDBNations()) total += nation.getOff();
            return (double) total;
        }
    },
    OFFENSIVE_WARS_AVG(true, DECIMAL_ROUNDED) {
        @Override
        public Double apply(DBAlliance alliance) {
            int total = 0;
            Set<DBNation> nations = alliance.getMemberDBNations();
            for (DBNation nation : nations) total += nation.getOff();
            return (double) total / nations.size();
        }
    },
    DEFENSIVE_WARS(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            int total = 0;
            Set<DBNation> nations = alliance.getMemberDBNations();
            for (DBNation nation : nations) total += nation.getDef();
            return (double) total;
        }
    },
    DEFENSIVE_WARS_AVG(true, DECIMAL_ROUNDED) {
        @Override
        public Double apply(DBAlliance alliance) {
            int total = 0;
            Set<DBNation> nations = alliance.getMemberDBNations();
            for (DBNation nation : nations) total += nation.getDef();
            return (double) total / nations.size();
        }
    },

    REVENUE_MONEY(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.MONEY.ordinal()] : 0d;
        }

        @Override
        public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
            return importer.getRevenueCache(ResourceType.MONEY);
        }
    },
    REVENUE_FOOD(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.FOOD.ordinal()] : 0d;
        }

        @Override
        public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
            return importer.getRevenueCache(ResourceType.FOOD);
        }
    },
    REVENUE_COAL(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.COAL.ordinal()] : 0d;
        }

        @Override
        public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
            return importer.getRevenueCache(ResourceType.COAL);
        }
    },
    REVENUE_OIL(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.OIL.ordinal()] : 0d;
        }

        @Override
        public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
            return importer.getRevenueCache(ResourceType.OIL);
        }
    },
    REVENUE_URANIUM(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.URANIUM.ordinal()] : 0d;
        }

        @Override
        public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
            return importer.getRevenueCache(ResourceType.URANIUM);
        }
    },
    REVENUE_LEAD(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.LEAD.ordinal()] : 0d;
        }

        @Override
        public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
            return importer.getRevenueCache(ResourceType.LEAD);
        }
    },
    REVENUE_IRON(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.IRON.ordinal()] : 0d;
        }

        @Override
        public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
            return importer.getRevenueCache(ResourceType.IRON);
        }
    },
    REVENUE_BAUXITE(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.BAUXITE.ordinal()] : 0d;
        }

        @Override
        public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
            return importer.getRevenueCache(ResourceType.BAUXITE);
        }
    },
    REVENUE_GASOLINE(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.GASOLINE.ordinal()] : 0d;
        }

        @Override
        public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
            return importer.getRevenueCache(ResourceType.GASOLINE);
        }
    },
    REVENUE_MUNITIONS(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.MUNITIONS.ordinal()] : 0d;
        }

        @Override
        public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
            return importer.getRevenueCache(ResourceType.MUNITIONS);
        }
    },
    REVENUE_STEEL(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.STEEL.ordinal()] : 0d;
        }

        @Override
        public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
            return importer.getRevenueCache(ResourceType.STEEL);
        }
    },
    REVENUE_ALUMINUM(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.ALUMINUM.ordinal()] : 0d;
        }

        @Override
        public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
            return importer.getRevenueCache(ResourceType.ALUMINUM);
        }
    },
    BARRACKS_PCT(true, PERCENTAGE_ONE, new BuildingPctMetric(Buildings.BARRACKS, f -> f.barracks)),
    FACTORY_PCT(true, PERCENTAGE_ONE, new BuildingPctMetric(Buildings.FACTORY, f -> f.factories)),
    HANGAR_PCT(true, PERCENTAGE_ONE, new BuildingPctMetric(Buildings.HANGAR, f -> f.hangars)),
    DRYDOCK_PCT(true, PERCENTAGE_ONE, new BuildingPctMetric(Buildings.DRYDOCK, f -> f.drydocks)),

    INFRA_VALUE(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            double total = 0;
            for (DBNation nation : alliance.getMemberDBNations()) {
                for (DBCity city : nation._getCitiesV3().values()) {
                    total += nation.infraCost(0, city.getInfra());
                }
            }
            return total;
        }

        private final Map<Integer, Integer> allianceByNationId = new Int2IntOpenHashMap();
        private final Map<Integer, Double> infraValueByAA = new Int2DoubleOpenHashMap();

        @Override
        public void setupReaders(DataDumpImporter importer) {
            importer.setNationReader(this, new BiConsumer<Long, NationHeaderReader>() {
                @Override
                public void accept(Long day, NationHeaderReader r) {
                    Rank position = r.header.alliance_position.get();
                    if (position.id <= Rank.APPLICANT.id) return;
                    int allianceId = r.header.alliance_id.get();
                    if (allianceId == 0) return;
                    Integer vm_turns = r.header.vm_turns.get();
                    if (vm_turns == null || vm_turns > 0) return;
                    allianceByNationId.put(r.header.nation_id.get(), allianceId);
                }
            });

            importer.setCityReader(this, new BiConsumer<Long, CityHeaderReader>() {
                @Override
                public void accept(Long day, CityHeaderReader r) {
                    int nationId = r.header.nation_id.get();
                    Integer allianceId = allianceByNationId.get(nationId);
                    if (allianceId == null || allianceId == 0) return;
                    double infra = r.header.infrastructure.get();
                    double value = PW.City.Infra.calculateInfra(0, infra);
                    infraValueByAA.merge(allianceId, value, Double::sum);
                }
            });


        }

        @Override
        public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
            Map<Integer, Double> result = infraValueByAA.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            allianceByNationId.clear();
            infraValueByAA.clear();
            return result;
        }

        @Override
        public List<AllianceMetricValue> getAllValues() {
            return null;
        }
    },

    LAND_VALUE(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            double total = 0;
            for (DBNation nation : alliance.getMemberDBNations()) {
                for (DBCity city : nation._getCitiesV3().values()) {
                    total += nation.landCost(0, city.getLand());
                }
            }
            return total;
        }

        private final Map<Integer, Integer> allianceByNationId = new Int2IntOpenHashMap();
        private final Map<Integer, Double> landValueByAA = new Int2DoubleOpenHashMap();

        @Override
        public void setupReaders(DataDumpImporter importer) {
            importer.setNationReader(this, new BiConsumer<Long, NationHeaderReader>() {
                @Override
                public void accept(Long day, NationHeaderReader r) {
                    Rank position = r.header.alliance_position.get();
                    if (position.id <= Rank.APPLICANT.id) return;
                    int allianceId = r.header.alliance_id.get();
                    if (allianceId == 0) return;
                    Integer vm_turns = r.header.vm_turns.get();
                    if (vm_turns == null || vm_turns > 0) return;
                    allianceByNationId.put(r.header.nation_id.get(), allianceId);
                }
            });

            importer.setCityReader(this, new BiConsumer<Long, CityHeaderReader>() {
                @Override
                public void accept(Long day, CityHeaderReader r) {
                    int nationId = r.header.nation_id.get();
                    Integer allianceId = allianceByNationId.get(nationId);
                    if (allianceId == null || allianceId == 0) return;
                    double land = r.header.land.get();
                    double value = PW.City.Land.calculateLand(0, land);
                    landValueByAA.merge(allianceId, value, Double::sum);
                }
            });
        }

        @Override
        public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
            Map<Integer, Double> result = landValueByAA.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            allianceByNationId.clear();
            landValueByAA.clear();
            return result;
        }

        @Override
        public List<AllianceMetricValue> getAllValues() {
            return null;
        }
    },

    PROJECT_VALUE(false, SI_UNIT, new CountNationMetric(f -> f.getProjects().stream().mapToDouble(Project::getMarketValue).sum())),

    CITY_VALUE(false, SI_UNIT, new CountNationMetric(f -> PW.City.cityCost(f, 0, f.getCities()))),

    NUKE(false, SI_UNIT, new UnitMetric(MilitaryUnit.NUKE, f -> f.nukes)),
    NUKE_AVG(true, DECIMAL_ROUNDED, new ProjectileAvg(MilitaryUnit.NUKE, f -> f.nukes)),

    MISSILE(false, SI_UNIT, new UnitMetric(MilitaryUnit.MISSILE, f -> f.missiles)),
    MISSILE_AVG(true, DECIMAL_ROUNDED, new ProjectileAvg(MilitaryUnit.MISSILE, f -> f.missiles)),

    GROUND_PCT(true, PERCENTAGE_ONE) {
        @Override
        public Double apply(DBAlliance alliance) {
            DBNation total = alliance.getMembersTotal();
            double tankPct = (double) total.getTanks() / (total.getCities() * Buildings.FACTORY.cap(f -> false) * Buildings.FACTORY.getUnitCap());
            double soldierPct = (double) total.getSoldiers() / (total.getCities() * Buildings.BARRACKS.cap(f -> false) * Buildings.BARRACKS.getUnitCap());
            return (tankPct + soldierPct) / 2d;
        }

        private final Map<Integer, Integer> soldiersByAA = new Int2IntOpenHashMap();
        private final Map<Integer, Integer> tanksByAA = new Int2IntOpenHashMap();
        private final Map<Integer, Integer> citiesByAA = new Int2IntOpenHashMap();

        @Override
        public void setupReaders(DataDumpImporter importer) {
            importer.setNationReader(this, (day, r) -> {
                Rank position = r.header.alliance_position.get();
                if (position.id <= Rank.APPLICANT.id) return;
                int allianceId = r.header.alliance_id.get();
                if (allianceId == 0) return;
                Integer vm_turns = r.header.vm_turns.get();
                if (vm_turns == null || vm_turns > 0) return;
                soldiersByAA.merge(allianceId, r.header.soldiers.get(), Integer::sum);
                tanksByAA.merge(allianceId, r.header.tanks.get(), Integer::sum);
                citiesByAA.merge(allianceId, r.header.cities.get(), Integer::sum);
            });

        }

        @Override
        public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
            int tankCap = Buildings.FACTORY.cap(f -> false) * Buildings.FACTORY.getUnitCap();
            int soldierCap = Buildings.BARRACKS.cap(f -> false) * Buildings.BARRACKS.getUnitCap();
            Map<Integer, Double> result = soldiersByAA.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> {
                int tanks = tanksByAA.get(f.getKey());
                int soldiers = f.getValue();
                int cities = citiesByAA.get(f.getKey());
                return ((double) tanks / (cities * tankCap) + (double) soldiers / (cities * soldierCap)) / 2d;
            }));
            soldiersByAA.clear();
            tanksByAA.clear();
            citiesByAA.clear();
            return result;
        }

        @Override
        public List<AllianceMetricValue> getAllValues() {
            return null;
        }
    },

    PROJECTS(false, SI_UNIT, new CountNationMetric(DBNation::getNumProjects, AllianceMetricMode.TOTAL)),

    CITY_BUY_VALUE_10D(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            double total = 0;
            long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10);
            for (DBNation nation : alliance.getMemberDBNations()) {
                long ms = Math.max(cutoff, nation.allianceSeniorityMs());
                int currentCity = nation.getCities();
                int previousCities = nation.getCitiesSince(ms);
                if (currentCity > previousCities) {
                    total += PW.City.cityCost(nation, currentCity - previousCities, currentCity);
                }
            }
            return total;
        }

        private final Map<Integer, Integer> allianceByNationId = new Int2IntOpenHashMap();
        private final Map<Integer, Long> nationJoinDay = new Int2LongOpenHashMap();
        private final Map<Integer, Integer> citiesByNation = new Int2IntOpenHashMap();
        private final Map<Integer, Integer> citiesBuyByNation = new Int2IntOpenHashMap();
        private final Map<Integer, Boolean> manifestDestiny = new Int2ObjectOpenHashMap<>();
        private final Map<Integer, Boolean> urbanPlanningByNation = new Int2ObjectOpenHashMap<>();
        private final Map<Integer, Boolean> advancedUrbanPlanningByNation = new Int2ObjectOpenHashMap<>();
        private final Map<Integer, Boolean> metropolitanPlanningByNation = new Int2ObjectOpenHashMap<>();
        private final Map<Integer, Boolean> governmentSupportAgencyByNation = new Int2ObjectOpenHashMap<>();
        private final Map<Integer, Boolean> bdaByNation = new Int2ObjectOpenHashMap<>();

        @Override
        public void setupReaders(DataDumpImporter importer) {
            importer.setNationReader(this, new BiConsumer<Long, NationHeaderReader>() {
                @Override
                public void accept(Long day, NationHeaderReader r) {
                    Rank position = r.header.alliance_position.get();
                    if (position.id <= Rank.APPLICANT.id) return;
                    int allianceId = r.header.alliance_id.get();
                    if (allianceId == 0) return;
                    Integer vm_turns = r.header.vm_turns.get();
                    if (vm_turns == null || vm_turns > 0) return;
                    int nationId = r.header.nation_id.get();
                    Integer previousAlliance = allianceByNationId.get(nationId);
                    if (previousAlliance == null || (previousAlliance != allianceId)) {
                        nationJoinDay.put(nationId, day);
                    }
                    allianceByNationId.put(nationId, allianceId);
                    citiesByNation.put(nationId, r.header.cities.get());
                    manifestDestiny.put(nationId, r.header.domestic_policy.get() == DomesticPolicy.MANIFEST_DESTINY);
                    if (r.header.urban_planning_np.get() == Boolean.TRUE) {
                        urbanPlanningByNation.put(nationId, r.header.urban_planning_np.get());
                    }
                    if (r.header.advanced_urban_planning_np.get() == Boolean.TRUE) {
                        advancedUrbanPlanningByNation.put(nationId, r.header.advanced_urban_planning_np.get());
                    }
                    if (r.header.metropolitan_planning_np.get() == Boolean.TRUE) {
                        metropolitanPlanningByNation.put(nationId, r.header.metropolitan_planning_np.get());
                    }
                    if (r.header.government_support_agency_np.get() == Boolean.TRUE) {
                        governmentSupportAgencyByNation.put(nationId, r.header.government_support_agency_np.get());
                    }
                    if (r.header.bureau_of_domestic_affairs_np.get() == Boolean.TRUE) {
                        bdaByNation.put(nationId, r.header.bureau_of_domestic_affairs_np.get());
                    }
                }
            });
            importer.setCityReader(this, new BiConsumer<Long, CityHeaderReader>() {
                @Override
                public void accept(Long day, CityHeaderReader r) {
                    int nationId = r.header.nation_id.get();
                    Integer allianceId = allianceByNationId.get(nationId);
                    if (allianceId == null || allianceId == 0) return;
                    long dayJoinedAlliance = nationJoinDay.get(nationId);
                    long joinedAllianceMs = TimeUtil.getTimeFromDay(Math.max(dayJoinedAlliance, day - 10));
                    long createdMs = r.header.date_created.get();
                    if (createdMs < joinedAllianceMs) return;
                    citiesBuyByNation.merge(nationId, 1, Integer::sum);
                }
            });

        }

        @Override
        public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
            Map<Integer, Double> cities10D = new Int2DoubleOpenHashMap();
            for (Map.Entry<Integer, Integer> entry : citiesBuyByNation.entrySet()) {
                int nationId = entry.getKey();
                Integer totalCities = citiesByNation.get(nationId);
                if (totalCities == null) continue;
                int previousCities = totalCities - entry.getValue();
                boolean md = manifestDestiny.get(nationId);
                boolean up = urbanPlanningByNation.getOrDefault(nationId, false);
                boolean aup = advancedUrbanPlanningByNation.getOrDefault(nationId, false);
                boolean mp = metropolitanPlanningByNation.getOrDefault(nationId, false);
                boolean gsa = governmentSupportAgencyByNation.getOrDefault(nationId, false);
                boolean bda = bdaByNation.getOrDefault(nationId, false);
                double cost = PW.City.cityCost(previousCities, totalCities, md, up, aup, mp, gsa, bda);
                int allianceId = allianceByNationId.get(nationId);
                cities10D.merge(allianceId, cost, Double::sum);
            }
            citiesByNation.clear();
            citiesBuyByNation.clear();
            manifestDestiny.clear();
            urbanPlanningByNation.clear();
            advancedUrbanPlanningByNation.clear();
            metropolitanPlanningByNation.clear();
            governmentSupportAgencyByNation.clear();
            bdaByNation.clear();
            return cities10D;
        }

        @Override
        public List<AllianceMetricValue> getAllValues() {
            nationJoinDay.clear();
            allianceByNationId.clear();
            return null;
        }
    },

    CITY_BUY_10D(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            double total = 0;
            long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10);
            for (DBNation nation : alliance.getMemberDBNations()) {
                long ms = Math.max(cutoff, nation.allianceSeniorityMs());
                total += nation.getCitiesSince(ms);
            }
            return total;
        }

        private final Map<Integer, Integer> allianceByNationId = new Int2IntOpenHashMap();
        private final Map<Integer, Long> nationJoinDay = new Int2LongOpenHashMap();
        private final Map<Integer, Integer> cities10D = new Int2IntOpenHashMap();

        @Override
        public void setupReaders(DataDumpImporter importer) {
            importer.setNationReader(this, new BiConsumer<Long, NationHeaderReader>() {
                @Override
                public void accept(Long day, NationHeaderReader r) {
                    Rank position = r.header.alliance_position.get();
                    if (position.id <= Rank.APPLICANT.id) return;
                    int allianceId = r.header.alliance_id.get();
                    if (allianceId == 0) return;
                    Integer vm_turns = r.header.vm_turns.get();
                    if (vm_turns == null || vm_turns > 0) return;
                    int nationId = r.header.nation_id.get();
                    Integer previousAlliance = allianceByNationId.get(nationId);
                    if (previousAlliance == null || (previousAlliance != allianceId)) {
                        nationJoinDay.put(nationId, day);
                    }
                    allianceByNationId.put(nationId, allianceId);
                }
            });
            importer.setCityReader(this, new BiConsumer<Long, CityHeaderReader>() {
                @Override
                public void accept(Long day, CityHeaderReader r) {
                    int nationId = r.header.nation_id.get();
                    Integer allianceId = allianceByNationId.get(nationId);
                    if (allianceId == null || allianceId == 0) return;
                    long dayJoinedAlliance = nationJoinDay.get(nationId);
                    long joinedAllianceMs = TimeUtil.getTimeFromDay(Math.max(dayJoinedAlliance, day - 10));
                    long createdMs = r.header.date_created.get();
                    if (createdMs < joinedAllianceMs) return;
                    cities10D.merge(allianceId, 1, Integer::sum);
                }
            });

        }

        @Override
        public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
            Map<Integer, Double> result = cities10D.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> (double) f.getValue()));
            cities10D.clear();
            return result;
        }

        @Override
        public List<AllianceMetricValue> getAllValues() {
            nationJoinDay.clear();
            allianceByNationId.clear();
            return null;
        }
    },

    PROJECT_BUY_10D(false, SI_UNIT) {

        @Override
        public Double apply(DBAlliance alliance) {
            int total = 0;
            for (DBNation nation : alliance.getMemberDBNations()) {
                if (nation.getProjectTurns() > 0) {
                    total++;
                }
            }
            return (double) total;
        }

        private final Map<Integer, Integer> allianceByNationId = new Int2IntOpenHashMap();
        private final Map<Integer, Long> nationJoinDay = new Int2LongOpenHashMap();
        private Map<Integer, Map<Long, Long>> projectsByDate = new Int2ObjectOpenHashMap<>();
        private Set<Integer> nationsToday = new IntOpenHashSet();

        @Override
        public void setupReaders(DataDumpImporter importer) {
            importer.setNationReader(this, new BiConsumer<Long, NationHeaderReader>() {
                @Override
                public void accept(Long day, NationHeaderReader r) {
                    Rank position = r.header.alliance_position.get();
                    if (position.id <= Rank.APPLICANT.id) return;
                    int allianceId = r.header.alliance_id.get();
                    if (allianceId == 0) return;
                    Integer vm_turns = r.header.vm_turns.get();
                    if (vm_turns == null || vm_turns > 0) return;
                    int nationId = r.header.nation_id.get();
                    Integer previousAlliance = allianceByNationId.get(nationId);
                    if (previousAlliance == null || (previousAlliance != allianceId)) {
                        nationJoinDay.put(nationId, day);
                    }
                    allianceByNationId.put(nationId, allianceId);
                    int numProjects = r.header.projects.get();
                    if (numProjects == 0) return;
                    DBNation nation = r.getNation(false, true);
                    if (nation == null) {
                        projectsByDate.computeIfAbsent(nationId, f -> new Long2LongOpenHashMap()).put(day, 0L);
                    } else {
                        long projectBits = nation.getProjectBitMask();
                        projectsByDate.computeIfAbsent(nationId, f -> new Long2LongOpenHashMap()).put(day, projectBits);
                        nationsToday.add(nationId);
                    }
                }
            });
        }

        private long getProjects(int nationId, long day) {
            Map<Long, Long> projectMap = projectsByDate.get(nationId);
            if (projectMap == null) return 0;
            return projectMap.getOrDefault(day, 0L);
        }

        @Override
        public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
            Map<Integer, Double> numBoughtByAA = new Int2DoubleOpenHashMap();
            for (int nationId : nationsToday) {
                long dayJoinedAlliance = nationJoinDay.get(nationId);
                long dayCheck = Math.max(dayJoinedAlliance, day - 10);
                if (dayCheck == day) continue;

                long projectsToday = getProjects(nationId, day);
                long projects10Ago = getProjects(nationId, dayCheck);

                int numToday = Long.bitCount(projectsToday);
                int num10Ago = Long.bitCount(projects10Ago);
                int allianceId = allianceByNationId.get(nationId);
                numBoughtByAA.merge(allianceId, (double) (numToday - num10Ago), Double::sum);
            }
            projectsByDate.entrySet().removeIf(entry -> {
                Map<Long, Long> nationProjectByDay = entry.getValue();
                nationProjectByDay.remove(day - 10);
                return nationProjectByDay.isEmpty();
            });
            nationsToday.clear();
            return numBoughtByAA;
        }

        @Override
        public List<AllianceMetricValue> getAllValues() {
            nationJoinDay.clear();
            allianceByNationId.clear();
            projectsByDate.clear();
            nationsToday.clear();
            return null;
        }
    },

    PROJECT_VALUE_10D(false, SI_UNIT) {

        @Override
        public Double apply(DBAlliance alliance) {
            return null;
        }

        private final Map<Integer, Integer> allianceByNationId = new Int2IntOpenHashMap();
        private final Map<Integer, Long> nationJoinDay = new Int2LongOpenHashMap();
        private Map<Integer, Map<Long, Long>> projectsByDate = new Int2ObjectOpenHashMap<>();
        private Set<Integer> nationsToday = new IntOpenHashSet();

        @Override
        public void setupReaders(DataDumpImporter importer) {
            importer.setNationReader(this, new BiConsumer<Long, NationHeaderReader>() {
                @Override
                public void accept(Long day, NationHeaderReader r) {
                    Rank position = r.header.alliance_position.get();
                    if (position.id <= Rank.APPLICANT.id) return;
                    int allianceId = r.header.alliance_id.get();
                    if (allianceId == 0) return;
                    Integer vm_turns = r.header.vm_turns.get();
                    if (vm_turns == null || vm_turns > 0) return;
                    int nationId = r.header.nation_id.get();
                    Integer previousAlliance = allianceByNationId.get(nationId);
                    if (previousAlliance == null || (previousAlliance != allianceId)) {
                        nationJoinDay.put(nationId, day);
                    }
                    allianceByNationId.put(nationId, allianceId);
                    int numProjects = r.header.projects.get();
                    if (numProjects == 0) return;
                    DBNation nation = r.getNation(false, true);
                    if (nation == null) {
                        projectsByDate.computeIfAbsent(nationId, f -> new Long2LongOpenHashMap()).put(day, 0L);
                    } else {
                        long projectBits = nation.getProjectBitMask();
                        projectsByDate.computeIfAbsent(nationId, f -> new Long2LongOpenHashMap()).put(day, projectBits);
                        nationsToday.add(nationId);
                    }
                }
            });
        }

        private long getProjects(int nationId, long day) {
            Map<Long, Long> projectMap = projectsByDate.get(nationId);
            if (projectMap == null) return 0;
            return projectMap.getOrDefault(day, 0L);
        }

        @Override
        public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
            Map<Integer, Double> numBoughtByAA = new Int2DoubleOpenHashMap();
            for (int nationId : nationsToday) {
                long dayJoinedAlliance = nationJoinDay.get(nationId);
                long dayCheck = Math.max(dayJoinedAlliance, day - 10);
                if (dayCheck == day) continue;

                long projectsToday = getProjects(nationId, day);
                long projects10Ago = getProjects(nationId, dayCheck);

                double valueBought = 0;

                for (Project project : Projects.values) {
                    boolean hasNow = (projectsToday & (1L << project.ordinal())) != 0;
                    boolean had10Ago = (projects10Ago & (1L << project.ordinal())) != 0;
                    if (hasNow && !had10Ago) {
                        int allianceId = allianceByNationId.get(nationId);
                        valueBought += project.getMarketValue();
                    }
                }
                if (valueBought > 0) {
                    int allianceId = allianceByNationId.get(nationId);
                    numBoughtByAA.merge(allianceId, valueBought, Double::sum);
                }
            }
            projectsByDate.entrySet().removeIf(entry -> {
                Map<Long, Long> nationProjectByDay = entry.getValue();
                nationProjectByDay.remove(day - 10);
                return nationProjectByDay.isEmpty();
            });
            nationsToday.clear();
            return numBoughtByAA;
        }

        @Override
        public List<AllianceMetricValue> getAllValues() {
            nationJoinDay.clear();
            allianceByNationId.clear();
            projectsByDate.clear();
            nationsToday.clear();
            return null;
        }
    },

    RESEARCH_VALUE(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            double total = 0;
            for (DBNation nation : alliance.getMemberDBNations()) {
                total += ResourceType.convertedTotal(Research.cost(nation.getResearchLevels()));
            }
            return total;
        }
    },

    RESEARCH_VALUE_AVG(true, DECIMAL_ROUNDED) {
        @Override
        public Double apply(DBAlliance alliance) {
            double total = 0;
            Set<DBNation> nations = alliance.getMemberDBNations();
            for (DBNation nation : nations) {
                total += ResourceType.convertedTotal(Research.cost(nation.getResearchLevels()));
            }
            return total / nations.size();
        }
    },

    RESEARCH_COUNT(false, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            double total = 0;
            for (DBNation nation : alliance.getMemberDBNations()) {
                total += nation.getResearchLevels().values().stream().mapToDouble(f -> f).sum();
            }
            return total;
        }
    },

    RESEARCH_AVG(true, DECIMAL_ROUNDED) {
        @Override
        public Double apply(DBAlliance alliance) {
            double total = 0;
            Set<DBNation> nations = alliance.getMemberDBNations();
            for (DBNation nation : nations) {
                total += nation.getResearchLevels().values().stream().mapToDouble(f -> f).sum();
            }
            return total / nations.size();
        }
    },

    GROUND_EFFICIENCY_AVG(true, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            double total = 0;
            Set<DBNation> nations = alliance.getMemberDBNations();
            for (DBNation nation : nations) {
                total += nation.getResearch(Research.GROUND_EFFICIENCY);
            }
            return total / nations.size();
        }
    },
    AIR_EFFICIENCY_AVG(true, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            double total = 0;
            Set<DBNation> nations = alliance.getMemberDBNations();
            for (DBNation nation : nations) {
                total += nation.getResearch(Research.AIR_EFFICIENCY);
            }
            return total / nations.size();
        }
    },
    NAVAL_EFFICIENCY_AVG(true, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            double total = 0;
            Set<DBNation> nations = alliance.getMemberDBNations();
            for (DBNation nation : nations) {
                total += nation.getResearch(Research.NAVAL_EFFICIENCY);
            }
            return total / nations.size();
        }
    },
    GROUND_CAPACITY_AVG(true, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            double total = 0;
            Set<DBNation> nations = alliance.getMemberDBNations();
            for (DBNation nation : nations) {
                total += nation.getResearch(Research.GROUND_CAPACITY);
            }
            return total / nations.size();
        }
    },
    AIR_CAPACITY_AVG(true, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            double total = 0;
            Set<DBNation> nations = alliance.getMemberDBNations();
            for (DBNation nation : nations) {
                total += nation.getResearch(Research.AIR_CAPACITY);
            }
            return total / nations.size();
        }
    },
    NAVAL_CAPACITY_AVG(true, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            double total = 0;
            Set<DBNation> nations = alliance.getMemberDBNations();
            for (DBNation nation : nations) {
                total += nation.getResearch(Research.NAVAL_CAPACITY);
            }
            return total / nations.size();
        }
    },

    SPIES(false, SI_UNIT, new UnitMetric(MilitaryUnit.SPIES, f -> f.spies)),
    SPIES_AVG(true, DECIMAL_ROUNDED, new UnitAvgMetric(MilitaryUnit.SPIES, f -> f.spies)),
    TREASURE(true, SI_UNIT) {
        @Override
        public Double apply(DBAlliance alliance) {
            return (double) alliance.getNumTreasures();
        }
    },

    // war policy over time
    // projects over time
    // PROJECT_BUY_VALUE_DAY
    // INFRA_BUY_VALUE_DAY
    // LAND_BUY_VALUE_DAY
    // MILITARY_BUY_VALUE_TURN
    // TODO
    // - color bonus
    // - treasure bonus

    ;

//    public static synchronized void updateLegacy() {
//        long currentTurn = TimeUtil.getTurn();
//        long[] min = new long[1];
//        try {
//            Locutus.imp().getWarDb().iterateAttacks(0, new Consumer<AbstractCursor>() {
//                @Override
//                public void accept(AbstractCursor AbstractCursor) {
//                    min[0] = AbstractCursor.getDate();
//                    throw new RuntimeException("break");
//                }
//            });
//        } catch (RuntimeException ignore) {};
//        long startTurn = TimeUtil.getTurn(min[0]);
//        AllianceMetric metric = AllianceMetric.WARCOST_DAILY;
//        Set<DBAlliance> alliances = Locutus.imp().getNationDB().getAlliances(true, true, true, 80);
//        Set<Integer> allianceIds = alliances.stream().map(DBAlliance::getAlliance_id).collect(Collectors.toSet());
//        for (long turn = startTurn; turn < currentTurn; turn++) {
//            System.out.println("Updating " + ((turn - startTurn)) + "/" + (currentTurn - startTurn) + " " + ((double) (turn - startTurn) / (currentTurn - startTurn) * 100) + "%");
//            long start = TimeUtil.getTimeFromTurn(turn + 1) - TimeUnit.DAYS.toMillis(1);
//            long end = TimeUtil.getTimeFromTurn(turn + 1);
//            List<AbstractCursor> allAttacks = Locutus.imp().getWarDb().getAttacks(start, end, f -> true);
//            Map<Integer, Map<AbstractCursor, Boolean>> attacksByAA = new HashMap<>();
//            for (AbstractCursor attack : allAttacks) {
//                DBWar war = attack.getWar();
//                if (war == null) continue;
//                if (allianceIds.contains(war.attacker_aa)) {
//                    attacksByAA.computeIfAbsent(war.attacker_aa, f -> new HashMap<>()).put(attack, war.attacker_id == attack.getAttacker_id());
//                }
//                if (allianceIds.contains(war.defender_aa)) {
//                    attacksByAA.computeIfAbsent(war.defender_aa, f -> new HashMap<>()).put(attack, war.defender_id == attack.getAttacker_id());
//                }
//            }
//            for (DBAlliance alliance : alliances) {
//                Map<AbstractCursor, Boolean> attacks = attacksByAA.get(alliance.getAlliance_id());
//                if (attacks == null || attacks.isEmpty()) continue;
//
//                AttackCost cost = new AttackCost();
//
//                cost.addCost(attacks.keySet(), a -> attacks.get(a), b -> !attacks.get(b));
//                double total = cost.convertedTotal(true);
//
//                Locutus.imp().getNationDB().addMetric(alliance, metric, turn, total);
//            }
//        }
//    }

    public static void saveAll(List<AllianceMetricValue> values, boolean replace) {
        if (values.isEmpty()) return;
        int chunkSize = 10000;
        for (int i = 0; i < values.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, values.size());
            List<AllianceMetricValue> subList = values.subList(i, end);
            String keyWord = replace ? "REPLACE" : "IGNORE";
            Locutus.imp().getNationDB().executeBatch(subList, "INSERT OR " + keyWord + " INTO `ALLIANCE_METRICS`(`alliance_id`, `metric`, `turn`, `value`) VALUES(?, ?, ?, ?)", (ThrowingBiConsumer<AllianceMetricValue, PreparedStatement>) (value, stmt) -> {
                stmt.setInt(1, value.alliance);
                stmt.setInt(2, value.metric.ordinal());
                stmt.setLong(3, value.turn);
                stmt.setDouble(4, value.value);
            });
        }
    }

    private static Map.Entry<Integer, double[]> aaRevenueCache;

    private final IAllianceMetric delegate;
    private final boolean average;
    private final TableNumberFormat format;

    AllianceMetric(boolean averageInAggregate, TableNumberFormat format) {
        this(averageInAggregate, format, null);
    }

    AllianceMetric(boolean averageInAggregate, TableNumberFormat format, IAllianceMetric delegate) {
        this.average = averageInAggregate;
        this.format = format;
        this.delegate = delegate;
    }

    public TableNumberFormat getFormat() {
        return format;
    }

    public static TableNumberFormat getFormat(Collection<AllianceMetric> metrics) {
        Set<TableNumberFormat> formats = metrics.stream().map(AllianceMetric::getFormat).collect(Collectors.toSet());
        if (formats.size() == 1) {
            return formats.iterator().next();
        }
        if (formats.contains(SI_UNIT)) {
            return SI_UNIT;
        }
        return TableNumberFormat.DECIMAL_ROUNDED;
    }

    public boolean shouldAverage() {
        return average;
    }

    public static AllianceMetric[] values = AllianceMetric.values();

    public static synchronized void update(int topX) {
        long turn = TimeUtil.getTurn();
        Set<DBAlliance> alliances = Locutus.imp().getNationDB().getAlliances(true, true, true, topX);
        List<AllianceMetricValue> toAdd = new ArrayList<>();
        for (DBAlliance alliance : alliances) {
            for (AllianceMetric metric : values) {
                try {
                    Double value = metric.apply(alliance);
                    if (value != null) {
                        toAdd.add(new AllianceMetricValue(alliance.getAlliance_id(), metric, turn, value));
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
        saveAll(toAdd, false);
    }

    public Double apply(DBAlliance alliance) {
        if (delegate != null) return delegate.apply(alliance);
        throw new IllegalArgumentException("No implementation for " + name());
    }

    @Override
    public void setupReaders(IAllianceMetric metric, DataDumpImporter importer) {
        if (delegate != null) delegate.setupReaders(metric, importer);
        else setupReaders(importer);
    }

    public void setupReaders(DataDumpImporter importer) {
        if (delegate != null) delegate.setupReaders(this, importer);
    }
    public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
        if (delegate != null) return delegate.getDayValue(importer, day);
        return null;
    }

    public List<AllianceMetricValue> getAllValues() {
        if (delegate != null) delegate.getAllValues();
        return null;
    }

    public static synchronized void saveDataDump(DataDumpParser parser, Predicate<Long> acceptDay, boolean overwrite, boolean saveAllTurns) throws IOException, ParseException {
        saveDataDump(parser, Arrays.asList(AllianceMetric.values), acceptDay, overwrite, saveAllTurns);
    }

    public static synchronized Map.Entry<Integer, Integer> saveDataDump(DataDumpParser parser, List<IAllianceMetric> metrics, Predicate<Long> acceptDay, boolean overwrite, boolean saveAllTurns) throws IOException, ParseException {
        int[] count = {0, 0};
        if (acceptDay == null) acceptDay = f -> true;
        List<AllianceMetricValue> values = new ArrayList<>();
        Runnable save = () -> {
            saveAll(values, overwrite);
            values.clear();
        };
        runDataDump(parser, metrics, acceptDay, (metric, day, value) -> {
            for (Map.Entry<Integer, Double> entry : value.entrySet()) {
                if (saveAllTurns) {
                    for (int i = 0; i < 12; i++) {
                        values.add(new AllianceMetricValue(entry.getKey(), (AllianceMetric) metric, day * 12 + i, entry.getValue()));
                    }
                } else {
                    values.add(new AllianceMetricValue(entry.getKey(), (AllianceMetric) metric, day * 12, entry.getValue()));
                }

            }
            if (values.size() > 10000) {
                count[0] += values.size();
                save.run();
            }
            count[1]++;
        });
        count[0] += values.size();
        save.run();
        return KeyValue.of(count[0], count[1]);
    }

    public static synchronized void runDataDump(DataDumpParser parser, List<IAllianceMetric> metrics, Predicate<Long> acceptDay, TriConsumer<IAllianceMetric, Long, Map<Integer, Double>> metricDayData) throws IOException, ParseException {
        DataDumpImporter importer = new DataDumpImporter(parser);
        for (IAllianceMetric metric : metrics) {
            metric.setupReaders(metric, importer);
        }

        BiConsumer<Long, NationHeaderReader> nationRows = importer.getNationReader();
        BiConsumer<Long, CityHeaderReader> cityRows = importer.getCityReader();

        parser.iterateFiles(new TriConsumer<Long, NationsFile, CitiesFile>() {
            @Override
            public void accept(Long day, NationsFile nf, CitiesFile cf) {
                if (!acceptDay.test(day)) return;

            }
        });
        parser.iterateAll(acceptDay, null, null, nationRows, cityRows, new Consumer<Long>() {
            @Override
            public void accept(Long day) {
                for (IAllianceMetric metric : metrics) {
                    Map<Integer, Double> value = metric.getDayValue(importer, day);
                    if (value != null) {
                        metricDayData.accept(metric, day, value);
                    }
                }
            }
        });

//        for (IAllianceMetric metric : metrics) {
//            List<AllianceMetricValue> allValues = metric.getAllValues();
//        }
    }

    public static double[][] toValueByTurnByMetric(Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap, Collection<AllianceMetric> metrics, long cutoffTurn, long maxTurn, Set<DBAlliance> coalition) {
        AllianceMetric[] metricsArr = metrics.toArray(new AllianceMetric[0]);
        double[][] totalValuesByTurnByMetric = new double[metricsArr.length][(int) (maxTurn - cutoffTurn + 1)];
        for (double[] doubles : totalValuesByTurnByMetric) Arrays.fill(doubles, Double.MAX_VALUE);

        boolean[] shouldAverageArr = new boolean[metricsArr.length];
        double[][] totalMembersByTurnByMetric = new double[metricsArr.length][];
        for (int i = 0; i < shouldAverageArr.length; i++) {
            if (metricsArr[i].shouldAverage() && coalition.size() > 1) {
                shouldAverageArr[i] = true;
                totalMembersByTurnByMetric[i] = new double[(int) (maxTurn - cutoffTurn + 1)];
            }
        }

        for (int k = 0; k < metricsArr.length; k++) {
            AllianceMetric metric = metricsArr[k];
            boolean shouldAverage = shouldAverageArr[k];

            for (DBAlliance alliance : coalition) {
                Map<AllianceMetric, Map<Long, Double>> metricData = metricMap.get(alliance);
                if (metricData == null) continue;
                Map<Long, Double> membersByTurn = null;
                if (shouldAverage) {
                    membersByTurn = metricData.get(AllianceMetric.MEMBERS);
                    if (membersByTurn == null) {
                        continue;
                    }
                }
                Map<Long, Double> metricByTurn = metricData.get(metric);
                if (metricByTurn == null) {
                    continue;
                }

                for (Map.Entry<Long, Double> turnMetric : metricByTurn.entrySet()) {
                    long turn = turnMetric.getKey();
                    Double value = turnMetric.getValue();
                    if (value == null) {
                        continue;
                    }

                    int turnRelative = (int) (turn - cutoffTurn);

                    Double members = null;
                    if (shouldAverage) {
                        members = membersByTurn.get(turn);
                        if (members == null) {
                            continue;
                        }
                        value *= members;
                    }

                    if (members != null) {
                        double[] totalMembersByTurn = totalMembersByTurnByMetric[k];
                        totalMembersByTurn[turnRelative] += members;
                    }
                    double[] totalValuesByTurn = totalValuesByTurnByMetric[k];
                    if (totalValuesByTurn[turnRelative] == Double.MAX_VALUE) {
                        totalValuesByTurn[turnRelative] = value;
                    } else {
                        totalValuesByTurn[turnRelative] += value;
                    }
                }
            }
        }
        for (int i = 0; i < totalMembersByTurnByMetric.length; i++) {
            boolean shouldAverage = shouldAverageArr[i];
            if (shouldAverage) {
                double[] totalValuesByTurn = totalValuesByTurnByMetric[i];
                double[] totalMembersByTurn = totalMembersByTurnByMetric[i];
                for (int j = 0; j < totalMembersByTurn.length; j++) {
                    if (totalValuesByTurn[j] != Double.MAX_VALUE) {
                        totalValuesByTurn[j] /= totalMembersByTurn[j];
                    }
                }
            }
        }

        return totalValuesByTurnByMetric;
    }

    public static double[][] toValueByTurnByCoalition(Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap, AllianceMetric metric, long cutoffTurn, long maxTurn, Set<DBAlliance>... coalitions) {

        double[][] totalValuesByTurnByCoal = new double[coalitions.length][(int) (maxTurn - cutoffTurn + 1)];
        for (double[] doubles : totalValuesByTurnByCoal) Arrays.fill(doubles, Double.MAX_VALUE);

        boolean shouldAverage = metric.shouldAverage() && metricMap.size() > 1;

        double[][] totalMembersByTurnByCoal = shouldAverage ? new double[coalitions.length][(int) (maxTurn - cutoffTurn + 1)] : null;

        for (Map.Entry<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> entry1 : metricMap.entrySet()) {
            DBAlliance alliance = entry1.getKey();
            Map<Long, Double> membersByTurn = null;
            if (shouldAverage) {
                membersByTurn = entry1.getValue().get(AllianceMetric.MEMBERS);
                if (membersByTurn == null) {
                    continue;
                }
            }
            Map<Long, Double> metricByTurn = entry1.getValue().get(metric);
            if (metricByTurn == null) {
                continue;
            }

            for (Map.Entry<Long, Double> turnMetric : metricByTurn.entrySet()) {
                long turn = turnMetric.getKey();
                Double value = turnMetric.getValue();
                if (value == null) {
                    continue;
                }

                int turnRelative = (int) (turn - cutoffTurn);

                Double members = null;
                if (shouldAverage) {
                    members = membersByTurn.get(turn);
                    if (members == null) {
                        continue;
                    }
                    value *= members;
                }
                for (int i = 0; i < coalitions.length; i++) {
                    Set<DBAlliance> coalition = coalitions[i];
                    if (coalition.contains(alliance)) {
                        if (members != null) {
                            double[] totalMembersByTurn = totalMembersByTurnByCoal[i];
                            totalMembersByTurn[turnRelative] += members;
                        }
                        double[] totalValuesByTurn = totalValuesByTurnByCoal[i];
                        if (totalValuesByTurn[turnRelative] == Double.MAX_VALUE) {
                            totalValuesByTurn[turnRelative] = value;
                        } else {
                            totalValuesByTurn[turnRelative] += value;
                        }
                    }
                }
            }
        }
        if (shouldAverage) {
            for (int i = 0; i < totalMembersByTurnByCoal.length; i++) {
                double[] totalValuesByTurn = totalValuesByTurnByCoal[i];
                double[] totalMembersByTurn = totalMembersByTurnByCoal[i];
                for (int j = 0; j < totalMembersByTurn.length; j++) {
                    if (totalValuesByTurn[j] != Double.MAX_VALUE) {
                        totalValuesByTurn[j] /= totalMembersByTurn[j];
                    }
                }
            }
        }

        return totalValuesByTurnByCoal;
    }

    public static Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> getMetrics(Collection<AllianceMetric> metrics, long minTurn, Set<DBAlliance>... coalitions) {
        return getMetrics(metrics, minTurn, TimeUtil.getTurn(), coalitions);
    }

    public static Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> getMetrics(Collection<AllianceMetric> metrics, long minTurn, long maxTurn, Set<DBAlliance>... coalitions) {
        if (minTurn < maxTurn - Short.MAX_VALUE) throw new IllegalArgumentException("Time range too large");
        if (maxTurn > TimeUtil.getTurn()) throw new IllegalArgumentException("End turn must be a current or previous time");
        Set<DBAlliance> allAlliances = new ObjectLinkedOpenHashSet<>();
        for (Set<DBAlliance> coalition : coalitions) allAlliances.addAll(coalition);
        // to linked hash set
        Set<Integer> aaIds = allAlliances.stream().map(DBAlliance::getAlliance_id).collect(Collectors.toCollection(ObjectLinkedOpenHashSet::new));
        Set<AllianceMetric> finalMetrics = new ObjectLinkedOpenHashSet<>(metrics);
        if (aaIds.size() > 1) {
            for (AllianceMetric metric : metrics) {
                if (metric.shouldAverage()) {
                    finalMetrics.add(AllianceMetric.MEMBERS);
                    break;
                }
            }
        }
        return Locutus.imp().getNationDB().getAllianceMetrics(aaIds, finalMetrics, minTurn, maxTurn);
    }
}
