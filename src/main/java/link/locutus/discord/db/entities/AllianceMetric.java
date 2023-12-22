package link.locutus.discord.db.entities;

import de.siegmar.fastcsv.reader.CsvRow;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv3.DataDumpParser;
import link.locutus.discord.apiv3.ParsedRow;
import link.locutus.discord.commands.rankings.table.TableNumberFormat;
import link.locutus.discord.commands.rankings.table.TimeNumericTable;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.scheduler.TriConsumer;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.text.ParseException;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static link.locutus.discord.commands.rankings.table.TableNumberFormat.*;

public enum AllianceMetric {
    SOLDIER(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getMembersTotal().getSoldiers();
        }

        private final Map<Integer, Integer> soldiersByAA = new Int2IntOpenHashMap();

        @Override
        public void setupReaders(DataDumpImporter importer) {
            importer.setNationReader(this, new TriConsumer<Long, DataDumpParser.NationHeader, ParsedRow>() {
                @Override
                public void consume(Long day, DataDumpParser.NationHeader header, ParsedRow row) {
                    int position = row.get(header.alliance_position, Integer::parseInt);
                    if (position <= Rank.APPLICANT.id) return;
                    int allianceId = row.get(header.alliance_id, Integer::parseInt);
                    if (allianceId == 0) return;
                    int soldiers = row.get(header.soldiers, Integer::parseInt);
                    soldiersByAA.merge(allianceId, soldiers, Integer::sum);
                }
            });
        }

        @Override
        public Map<Integer, Double> getDayValue() {
            Map<Integer, Double> result = soldiersByAA.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> (double) f.getValue()));
            soldiersByAA.clear();
            return result;
        }

        @Override
        public List<Value> getAllValues() {
            soldiersByAA.clear();
            return null;
        }
    },
    SOLDIER_PCT(true, PERCENTAGE_ONE) {
        @Override
        public double apply(DBAlliance alliance) {
            DBNation total = alliance.getMembersTotal();
            return (double) total.getSoldiers() / (total.getCities() * Buildings.BARRACKS.cap(f -> false) * Buildings.BARRACKS.getUnitCap());
        }

        private final Map<Integer, Integer> soldiersByAA = new Int2IntOpenHashMap();
        private final Map<Integer, Integer> citiesByAA = new Int2IntOpenHashMap();

        @Override
        public void setupReaders(DataDumpImporter importer) {
            importer.setNationReader(this, new TriConsumer<Long, DataDumpParser.NationHeader, ParsedRow>() {
                @Override
                public void consume(Long day, DataDumpParser.NationHeader header, ParsedRow row) {
                    int position = row.get(header.alliance_position, Integer::parseInt);
                    if (position <= Rank.APPLICANT.id) return;
                    int allianceId = row.get(header.alliance_id, Integer::parseInt);
                    if (allianceId == 0) return;
                    int soldiers = row.get(header.soldiers, Integer::parseInt);
                    soldiersByAA.merge(allianceId, soldiers, Integer::sum);
                    int cities = row.get(header.cities, Integer::parseInt);
                    citiesByAA.merge(allianceId, cities, Integer::sum);
                }
            });
        }

        @Override
        public Map<Integer, Double> getDayValue() {
            int soldiersPerCity = Buildings.BARRACKS.cap(f -> false) * Buildings.BARRACKS.getUnitCap();
            Map<Integer, Double> result = soldiersByAA.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> (double) f.getValue() / (citiesByAA.get(f.getKey()) * soldiersPerCity)));
            soldiersByAA.clear();
            citiesByAA.clear();
            return result;
        }

        @Override
        public List<Value> getAllValues() {
            return null;
        }
    },
    TANK(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getMembersTotal().getTanks();
        }

        private final Map<Integer, Integer> tanksByAA = new Int2IntOpenHashMap();

        @Override
        public void setupReaders(DataDumpImporter importer) {
            importer.setNationReader(this, new TriConsumer<Long, DataDumpParser.NationHeader, ParsedRow>() {
                @Override
                public void consume(Long day, DataDumpParser.NationHeader header, ParsedRow row) {
                    int position = row.get(header.alliance_position, Integer::parseInt);
                    if (position <= Rank.APPLICANT.id) return;
                    int allianceId = row.get(header.alliance_id, Integer::parseInt);
                    if (allianceId == 0) return;
                    int tanks = row.get(header.tanks, Integer::parseInt);
                    tanksByAA.merge(allianceId, tanks, Integer::sum);
                }
            });
        }

        @Override
        public Map<Integer, Double> getDayValue() {
            Map<Integer, Double> result = tanksByAA.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> (double) f.getValue()));
            tanksByAA.clear();
            return result;
        }

        @Override
        public List<Value> getAllValues() {
            tanksByAA.clear();
            return null;
        }
    },
    TANK_PCT(true, PERCENTAGE_ONE) {
        @Override
        public double apply(DBAlliance alliance) {
            DBNation total = alliance.getMembersTotal();
            return (double) total.getTanks() / (total.getCities() * Buildings.FACTORY.cap(f -> false) * Buildings.FACTORY.getUnitCap());
        }

        private final Map<Integer, Integer> tanksByAA = new Int2IntOpenHashMap();
        private final Map<Integer, Integer> citiesByAA = new Int2IntOpenHashMap();

        @Override
        public void setupReaders(DataDumpImporter importer) {
            importer.setNationReader(this, new TriConsumer<Long, DataDumpParser.NationHeader, ParsedRow>() {
                @Override
                public void consume(Long day, DataDumpParser.NationHeader header, ParsedRow row) {
                    int position = row.get(header.alliance_position, Integer::parseInt);
                    if (position <= Rank.APPLICANT.id) return;
                    int allianceId = row.get(header.alliance_id, Integer::parseInt);
                    if (allianceId == 0) return;
                    int tanks = row.get(header.tanks, Integer::parseInt);
                    tanksByAA.merge(allianceId, tanks, Integer::sum);
                    int cities = row.get(header.cities, Integer::parseInt);
                    citiesByAA.merge(allianceId, cities, Integer::sum);
                }
            });
        }

        @Override
        public Map<Integer, Double> getDayValue() {
            int tanksPerCity = Buildings.FACTORY.cap(f -> false) * Buildings.FACTORY.getUnitCap();
            Map<Integer, Double> result = tanksByAA.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> (double) f.getValue() / (citiesByAA.get(f.getKey()) * tanksPerCity)));
            tanksByAA.clear();
            citiesByAA.clear();
            return result;
        }

        @Override
        public List<Value> getAllValues() {
            tanksByAA.clear();
            citiesByAA.clear();
            return null;
        }
    },
    AIRCRAFT(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getMembersTotal().getAircraft();
        }

        private final Map<Integer, Integer> aircraftByAA = new Int2IntOpenHashMap();

        @Override
        public void setupReaders(DataDumpImporter importer) {
            importer.setNationReader(this, new TriConsumer<Long, DataDumpParser.NationHeader, ParsedRow>() {
                @Override
                public void consume(Long day, DataDumpParser.NationHeader header, ParsedRow row) {
                    int position = row.get(header.alliance_position, Integer::parseInt);
                    if (position <= Rank.APPLICANT.id) return;
                    int allianceId = row.get(header.alliance_id, Integer::parseInt);
                    if (allianceId == 0) return;
                    int aircraft = row.get(header.aircraft, Integer::parseInt);
                    aircraftByAA.merge(allianceId, aircraft, Integer::sum);
                }
            });
        }

        @Override
        public Map<Integer, Double> getDayValue() {
            Map<Integer, Double> result = aircraftByAA.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> (double) f.getValue()));
            aircraftByAA.clear();
            return result;
        }

        @Override
        public List<Value> getAllValues() {
            aircraftByAA.clear();
            return null;
        }
    },
    AIRCRAFT_PCT(true, PERCENTAGE_ONE) {
        @Override
        public double apply(DBAlliance alliance) {
            DBNation total = alliance.getMembersTotal();
            return (double) total.getAircraft() / (total.getCities() * Buildings.HANGAR.cap(f -> false) * Buildings.HANGAR.getUnitCap());
        }

        private final Map<Integer, Integer> aircraftByAA = new Int2IntOpenHashMap();
        private final Map<Integer, Integer> citiesByAA = new Int2IntOpenHashMap();

        @Override
        public void setupReaders(DataDumpImporter importer) {
            importer.setNationReader(this, new TriConsumer<Long, DataDumpParser.NationHeader, ParsedRow>() {
                @Override
                public void consume(Long day, DataDumpParser.NationHeader header, ParsedRow row) {
                    int position = row.get(header.alliance_position, Integer::parseInt);
                    if (position <= Rank.APPLICANT.id) return;
                    int allianceId = row.get(header.alliance_id, Integer::parseInt);
                    if (allianceId == 0) return;
                    int aircraft = row.get(header.aircraft, Integer::parseInt);
                    aircraftByAA.merge(allianceId, aircraft, Integer::sum);
                    int cities = row.get(header.cities, Integer::parseInt);
                    citiesByAA.merge(allianceId, cities, Integer::sum);
                }
            });
        }

        @Override
        public Map<Integer, Double> getDayValue() {
            int aircraftPerCity = Buildings.HANGAR.cap(f -> false) * Buildings.HANGAR.getUnitCap();
            Map<Integer, Double> result = aircraftByAA.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> (double) f.getValue() / (citiesByAA.get(f.getKey()) * aircraftPerCity)));
            aircraftByAA.clear();
            citiesByAA.clear();
            return result;
        }

        @Override
        public List<Value> getAllValues() {
            aircraftByAA.clear();
            citiesByAA.clear();
            return null;
        }
    },
    SHIP(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getMembersTotal().getShips();
        }

        private final Map<Integer, Integer> shipsByAA = new Int2IntOpenHashMap();

        @Override
        public void setupReaders(DataDumpImporter importer) {
            importer.setNationReader(this, new TriConsumer<Long, DataDumpParser.NationHeader, ParsedRow>() {
                @Override
                public void consume(Long day, DataDumpParser.NationHeader header, ParsedRow row) {
                    int position = row.get(header.alliance_position, Integer::parseInt);
                    if (position <= Rank.APPLICANT.id) return;
                    int allianceId = row.get(header.alliance_id, Integer::parseInt);
                    if (allianceId == 0) return;
                    int ships = row.get(header.ships, Integer::parseInt);
                    shipsByAA.merge(allianceId, ships, Integer::sum);
                }
            });
        }

        @Override
        public Map<Integer, Double> getDayValue() {
            Map<Integer, Double> result = shipsByAA.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> (double) f.getValue()));
            shipsByAA.clear();
            return result;
        }

        @Override
        public List<Value> getAllValues() {
            shipsByAA.clear();
            return null;
        }
    },
    SHIP_PCT(true, PERCENTAGE_ONE) {
        @Override
        public double apply(DBAlliance alliance) {
            DBNation total = alliance.getMembersTotal();
            return (double) total.getShips() / (total.getCities() * Buildings.DRYDOCK.cap(f -> false) * Buildings.DRYDOCK.getUnitCap());
        }

        private final Map<Integer, Integer> shipsByAA = new Int2IntOpenHashMap();
        private final Map<Integer, Integer> citiesByAA = new Int2IntOpenHashMap();

        @Override
        public void setupReaders(DataDumpImporter importer) {
            importer.setNationReader(this, new TriConsumer<Long, DataDumpParser.NationHeader, ParsedRow>() {
                @Override
                public void consume(Long day, DataDumpParser.NationHeader header, ParsedRow row) {
                    int position = row.get(header.alliance_position, Integer::parseInt);
                    if (position <= Rank.APPLICANT.id) return;
                    int allianceId = row.get(header.alliance_id, Integer::parseInt);
                    if (allianceId == 0) return;
                    int ships = row.get(header.ships, Integer::parseInt);
                    shipsByAA.merge(allianceId, ships, Integer::sum);
                    int cities = row.get(header.cities, Integer::parseInt);
                    citiesByAA.merge(allianceId, cities, Integer::sum);
                }
            });
        }

        @Override
        public Map<Integer, Double> getDayValue() {
            int shipsPerCity = Buildings.DRYDOCK.cap(f -> false) * Buildings.DRYDOCK.getUnitCap();
            Map<Integer, Double> result = shipsByAA.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> (double) f.getValue() / (citiesByAA.get(f.getKey()) * shipsPerCity)));
            shipsByAA.clear();
            citiesByAA.clear();
            return result;
        }

        @Override
        public List<Value> getAllValues() {
            return null;
        }
    },
    INFRA(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getMembersTotal().getInfra();
        }

        private final Map<Integer, Integer> allianceByNationId = new Int2IntOpenHashMap();
        private final Map<Integer, Double> infraByAA = new Int2DoubleOpenHashMap();
        @Override
        public void setupReaders(DataDumpImporter importer) {
            importer.setNationReader(this, new TriConsumer<Long, DataDumpParser.NationHeader, ParsedRow>() {
                @Override
                public void consume(Long day, DataDumpParser.NationHeader header, ParsedRow row) {
                    int position = row.get(header.alliance_position, Integer::parseInt);
                    if (position <= Rank.APPLICANT.id) return;
                    int allianceId = row.get(header.alliance_id, Integer::parseInt);
                    if (allianceId == 0) return;
                    allianceByNationId.put(row.get(header.nation_id, Integer::parseInt), allianceId);
                }
            });

            importer.setCityReader(this, new TriConsumer<Long, DataDumpParser.CityHeader, ParsedRow>() {
                @Override
                public void consume(Long aLong, DataDumpParser.CityHeader header, ParsedRow parsedRow) {
                    int nationId = parsedRow.get(header.nation_id, Integer::parseInt);
                    Integer allianceId = allianceByNationId.get(nationId);
                    if (allianceId == null) return;
                    double infra = parsedRow.get(header.infrastructure, Double::parseDouble);
                    infraByAA.merge(allianceId, infra, Double::sum);
                }
            });

        }

        @Override
        public Map<Integer, Double> getDayValue() {
            Map<Integer, Double> result = infraByAA.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            infraByAA.clear();
            allianceByNationId.clear();
            return result;
        }

        @Override
        public List<Value> getAllValues() {
            return null;
        }
    },
    INFRA_AVG(true, DECIMAL_ROUNDED) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getMembersTotal().getAvg_infra();
        }
    },
    LAND(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            Set<DBNation> nations = alliance.getNations(true, 0, true);
            double totalLand = 0;
            for (DBNation nation : nations) {
                totalLand += nation.getTotalLand();
            }
            return totalLand;
        }
    },
    LAND_AVG(true, DECIMAL_ROUNDED) {
        @Override
        public double apply(DBAlliance alliance) {
            Set<DBNation> nations = alliance.getNations(true, 0, true);
            double totalLand = 0;
            int num = 0;
            for (DBNation nation : nations) {
                totalLand += nation.getTotalLand();
                num += nation.getCities();
            }
            return totalLand / num;
        }
    },
    SCORE(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getScore();
        }
    },
    SCORE_AVG(true, DECIMAL_ROUNDED) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getScore() / alliance.getNations(true, 0, true).size();
        }
    },
    CITY(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getMembersTotal().getCities();
        }
    },
    CITY_AVG(true, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            int total = 0;
            Set<DBNation> nations = alliance.getMemberDBNations();
            for (DBNation nation : nations) total += nation.getCities();
            return (double) total / nations.size();
        }
    },
    MEMBERS(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getNations(true, 0, true).size();
        }
    },
    MEMBERS_ACTIVE_1W(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getNations(true, 1440 * 7, true).size();
        }
    },
    VM(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getNations().stream().filter(f -> f.getVm_turns() != 0 && f.getPosition() > Rank.APPLICANT.id).count();
        }
    },
    INACTIVE_1W(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getNations().stream().filter(f -> f.getVm_turns() == 0 && f.getPosition() > Rank.APPLICANT.id && f.getActive_m() > 1440 * 7).count();
        }
    },
    VM_PCT(true, PERCENTAGE_ONE) {
        @Override
        public double apply(DBAlliance alliance) {
            Set<DBNation> nations = alliance.getNations();
            return nations.stream().filter(f -> f.getVm_turns() != 0 && f.getPosition() > Rank.APPLICANT.id).count() / (double) nations.size();
        }
    },
    INACTIVE_PCT(true, PERCENTAGE_ONE) {
        @Override
        public double apply(DBAlliance alliance) {
            Set<DBNation> nations = alliance.getNations();
            return nations.stream().filter(f -> f.getActive_m() > 1440 * 7 && f.getPosition() > Rank.APPLICANT.id).count() / (double) nations.size();
        }
    },
    WARCOST_DAILY(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            Set<DBNation> nations = alliance.getNations();
            nations.removeIf(f -> f.getPosition() <= Rank.APPLICANT.id || f.getVm_turns() > 0);
            Set<Integer> nationIds = nations.stream().map(DBNation::getNation_id).collect(Collectors.toSet());

            AttackCost cost = new AttackCost("", "", false, false, false, false, false);
            long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
            List<AbstractCursor> attacks = Locutus.imp().getWarDb().getAttacksEither(nationIds, cutoff);
            cost.addCost(attacks, a -> nationIds.contains(a.getAttacker_id()), b -> nationIds.contains(b.getDefender_id()));
            return cost.convertedTotal(true);
        }
    },
    REVENUE(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            Set<DBNation> nations = alliance.getNations();
            nations.removeIf(f -> f.isGray() || f.isBeige() || f.getPosition() <= Rank.APPLICANT.id || f.getVm_turns() > 0);

            double[] totalRss = ResourceType.getBuffer();
            for (DBNation nation : nations) {
                double[] revenue = nation.getRevenue();
                ResourceType.add(totalRss, revenue);
            }

            aaRevenueCache = new AbstractMap.SimpleEntry<>(alliance.getAlliance_id(), totalRss);

            return PnwUtil.convertedTotal(totalRss);
        }
    },
    OFFENSIVE_WARS(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            int total = 0;
            for (DBNation nation : alliance.getMemberDBNations()) total += nation.getOff();
            return total;
        }
    },
    OFFENSIVE_WARS_AVG(true, DECIMAL_ROUNDED) {
        @Override
        public double apply(DBAlliance alliance) {
            int total = 0;
            Set<DBNation> nations = alliance.getMemberDBNations();
            for (DBNation nation : nations) total += nation.getOff();
            return (double) total / nations.size();
        }
    },
    DEFENSIVE_WARS(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            int total = 0;
            Set<DBNation> nations = alliance.getMemberDBNations();
            for (DBNation nation : nations) total += nation.getDef();
            return total;
        }
    },
    DEFENSIVE_WARS_AVG(true, DECIMAL_ROUNDED) {
        @Override
        public double apply(DBAlliance alliance) {
            int total = 0;
            Set<DBNation> nations = alliance.getMemberDBNations();
            for (DBNation nation : nations) total += nation.getDef();
            return (double) total / nations.size();
        }
    },

    REVENUE_MONEY(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.MONEY.ordinal()] : 0d;
        }
    },
    REVENUE_FOOD(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.FOOD.ordinal()] : 0d;
        }
    },
    REVENUE_COAL(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.COAL.ordinal()] : 0d;
        }
    },
    REVENUE_OIL(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.OIL.ordinal()] : 0d;
        }
    },
    REVENUE_URANIUM(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.URANIUM.ordinal()] : 0d;
        }
    },
    REVENUE_LEAD(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.LEAD.ordinal()] : 0d;
        }
    },
    REVENUE_IRON(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.IRON.ordinal()] : 0d;
        }
    },
    REVENUE_BAUXITE(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.BAUXITE.ordinal()] : 0d;
        }
    },
    REVENUE_GASOLINE(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.GASOLINE.ordinal()] : 0d;
        }
    },
    REVENUE_MUNITIONS(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.MUNITIONS.ordinal()] : 0d;
        }
    },
    REVENUE_STEEL(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.STEEL.ordinal()] : 0d;
        }
    },
    REVENUE_ALUMINUM(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            Map.Entry<Integer, double[]> tmp = aaRevenueCache;
            return tmp != null && tmp.getKey() == alliance.getAlliance_id() ? tmp.getValue()[ResourceType.ALUMINUM.ordinal()] : 0d;
        }
    },
    BARRACKS_PCT(true, PERCENTAGE_ONE) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getMembersTotal().getMMRBuildingArr()[0] / Buildings.BARRACKS.cap(f -> false);
        }
    },
    FACTORY_PCT(true, PERCENTAGE_ONE) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getMembersTotal().getMMRBuildingArr()[1] / Buildings.FACTORY.cap(f -> false);
        }
    },
    HANGAR_PCT(true, PERCENTAGE_ONE) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getMembersTotal().getMMRBuildingArr()[2] / Buildings.HANGAR.cap(f -> false);
        }
    },
    DRYDOCK_PCT(true, PERCENTAGE_ONE) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getMembersTotal().getMMRBuildingArr()[3] / Buildings.DRYDOCK.cap(f -> false);
        }
    },

    INFRA_VALUE(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            double total = 0;
            for (DBNation nation : alliance.getMemberDBNations()) {
                for (DBCity city : nation._getCitiesV3().values()) {
                    total += nation.infraCost(0, city.getInfra());
                }
            }
            return total;
        }
    },

    LAND_VALUE(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            double total = 0;
            for (DBNation nation : alliance.getMemberDBNations()) {
                for (DBCity city : nation._getCitiesV3().values()) {
                    total += nation.landCost(0, city.getLand());
                }
            }
            return total;
        }
    },

    PROJECT_VALUE(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            double total = 0;
            for (DBNation nation : alliance.getMemberDBNations()) {
                for (Project project : nation.getProjects()) {
                    total += PnwUtil.convertedTotal(project.cost());
                }
            }
            return total;
        }
    },

    CITY_VALUE(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            double total = 0;
            for (DBNation nation : alliance.getMemberDBNations()) {
                total += PnwUtil.cityCost(nation, 0, nation.getCities());
            }
            return total;
        }
    },

    NUKE(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getMembersTotal().getNukes();
        }
    },
    NUKE_AVG(true, DECIMAL_ROUNDED) {
        @Override
        public double apply(DBAlliance alliance) {
            Set<DBNation> nations = alliance.getNations(true, 0, true);
            long total = 0;
            int num = 0;
            for (DBNation nation : nations) {
                total += nation.getNukes();
                num += nation.getCities();
            }
            return total / num;
        }
    },

    MISSILE(false, SI_UNIT) {
        @Override
        public double apply(DBAlliance alliance) {
            return alliance.getMembersTotal().getMissiles();
        }
    },
    MISSILE_AVG(true, DECIMAL_ROUNDED) {
        @Override
        public double apply(DBAlliance alliance) {
            Set<DBNation> nations = alliance.getNations(true, 0, true);
            long total = 0;
            int num = 0;
            for (DBNation nation : nations) {
                total += nation.getMissiles();
                num += nation.getCities();
            }
            return total / num;
        }
    },

    GROUND_PCT(true, PERCENTAGE_ONE) {
        @Override
        public double apply(DBAlliance alliance) {
            DBNation total = alliance.getMembersTotal();
            double tankPct = (double) total.getTanks() / (total.getCities() * Buildings.FACTORY.cap(f -> false) * Buildings.FACTORY.getUnitCap());
            double soldierPct = (double) total.getSoldiers() / (total.getCities() * Buildings.BARRACKS.cap(f -> false) * Buildings.BARRACKS.getUnitCap());
            return (tankPct + soldierPct) / 2d;
        }
    },

    // war policy over time
    // projects over time
    // CITY_BUY_VALUE_DAY
    // PROJECT_BUY_VALUE_DAY
    // INFRA_BUY_VALUE_DAY
    // LAND_BUY_VALUE_DAY
    // MILITARY_BUY_VALUE_TURN

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

    public static class Value {
        public final int alliance;
        public final AllianceMetric metric;
        public final long turn;
        public final double value;

        public Value(int alliance, AllianceMetric metric, long turn, double value) {
            this.alliance = alliance;
            this.metric = metric;
            this.turn = turn;
            this.value = value;
        }
    }

    public static void saveAll(List<Value> values) {
        if (values.isEmpty()) return;
        int chunkSize = 10000;
        for (int i = 0; i < values.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, values.size());
            List<Value> subList = values.subList(i, end);
            Locutus.imp().getNationDB().executeBatch(subList, "INSERT OR IGNORE INTO `ALLIANCE_METRICS`(`alliance_id`, `metric`, `turn`, `value`) VALUES(?, ?, ?, ?)", (ThrowingBiConsumer<Value, PreparedStatement>) (value, stmt) -> {
                stmt.setInt(1, value.alliance);
                stmt.setInt(2, value.metric.ordinal());
                stmt.setLong(3, value.turn);
                stmt.setDouble(4, value.value);
            });
        }
    }

    private static Map.Entry<Integer, double[]> aaRevenueCache;

    private final boolean average;
    private final TableNumberFormat format;

    AllianceMetric(boolean averageInAggregate, TableNumberFormat format) {
        this.average = averageInAggregate;
        this.format = format;
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
        System.out.println("Updating metrics for top " + topX + " alliances");
        long turn = TimeUtil.getTurn();
        Set<DBAlliance> alliances = Locutus.imp().getNationDB().getAlliances(true, true, true, topX);
        for (DBAlliance alliance : alliances) {
            for (AllianceMetric metric : values) {
                double value = metric.apply(alliance);
                Locutus.imp().getNationDB().addMetric(alliance, metric, turn, value);
            }
        }
    }

    public abstract double apply(DBAlliance alliance);

    public void setupReaders(DataDumpImporter importer) {
        // sets the reader headers
        return;
    }
    public Map<Integer, Double> getDayValue() {
        // returns day value or null
        return null;
    }

    public List<Value> getAllValues() {
        // returns all values
        // resets any caches
        return null;
    }

    public synchronized void runDataDump(DataDumpParser parser) throws IOException, ParseException {
        DataDumpImporter importer = new DataDumpImporter();
        for (AllianceMetric metric : AllianceMetric.values) {
            metric.setupReaders(importer);
        }

        TriConsumer<Long, DataDumpParser.NationHeader, CsvRow> nationRows = importer.getNationReader();
        TriConsumer<Long, DataDumpParser.CityHeader, CsvRow> cityRows = importer.getCityReader();

        parser.iterateAll(nationRows, cityRows, new Consumer<Long>() {
            @Override
            public void accept(Long day) {
                List<AllianceMetric.Value> values = new ObjectArrayList<>();
                for (AllianceMetric metric : AllianceMetric.values) {
                    Map<Integer, Double> value = metric.getDayValue();
                    if (value != null) {
                        for (Map.Entry<Integer, Double> entry : value.entrySet()) {
                            values.add(new AllianceMetric.Value(entry.getKey(), metric, day, entry.getValue()));
                        }
                    }
                }
                saveAll(values);
            }
        });

        List<Value> all = new ObjectArrayList<>();
        for (AllianceMetric metric : AllianceMetric.values) {
            all.addAll(metric.getAllValues());
        }
        saveAll(all);
    }
    private static class DataDumpImporter {
        Map<AllianceMetric, TriConsumer<Long, DataDumpParser.NationHeader, ParsedRow>> nationReaders = new LinkedHashMap<>();
        Map<AllianceMetric, TriConsumer<Long, DataDumpParser.CityHeader, ParsedRow>> cityReaders = new LinkedHashMap<>();

        public void setNationReader(AllianceMetric metric, TriConsumer<Long, DataDumpParser.NationHeader, ParsedRow> nationReader) {
            this.nationReaders.put(metric, nationReader);
        }

        public void setCityReader(AllianceMetric metric, TriConsumer<Long, DataDumpParser.CityHeader, ParsedRow> cityReader) {
            this.cityReaders.put(metric, cityReader);
        }

        public TriConsumer<Long, DataDumpParser.NationHeader, CsvRow> getNationReader() {
            if (nationReaders.isEmpty()) return null;
            ParsedRow parsedRow = new ParsedRow();
            return (turn, header, row) -> {
                parsedRow.setRow(row);
                for (Map.Entry<AllianceMetric, TriConsumer<Long, DataDumpParser.NationHeader, ParsedRow>> entry : nationReaders.entrySet()) {
                    entry.getValue().consume(turn, header, parsedRow);
                }
            };
        }

        public TriConsumer<Long, DataDumpParser.CityHeader, CsvRow> getCityReader() {
            if (cityReaders.isEmpty()) return null;
            ParsedRow parsedRow = new ParsedRow();
            return (turn, header, row) -> {
                parsedRow.setRow(row);
                for (Map.Entry<AllianceMetric, TriConsumer<Long, DataDumpParser.CityHeader, ParsedRow>> entry : cityReaders.entrySet()) {
                    entry.getValue().consume(turn, header, parsedRow);
                }
            };
        }
    }

    public static TimeNumericTable generateTable(AllianceMetric metric, long cutoffTurn, Collection<String> coalitionNames, Set<DBAlliance>... coalitions) {
        return generateTable(metric, cutoffTurn, TimeUtil.getTurn(), coalitionNames, coalitions);
    }

    public static TimeNumericTable generateTable(Collection<AllianceMetric> metrics, long startTurn, long endTurn, String coalitionName, Set<DBAlliance> coalition) {
        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap = AllianceMetric.getMetrics(metrics, startTurn, endTurn, coalition);
        return generateTable(metricMap, metrics, startTurn, endTurn, coalitionName, coalition);
    }

    public static TimeNumericTable generateTable(Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap, Collection<AllianceMetric> metrics, long startTurn, long endTurn, String coalitionName, Set<DBAlliance> coalition) {
        double[][] valuesByTurnByMetric = AllianceMetric.toValueByTurnByMetric(metricMap, metrics, startTurn, endTurn, coalition);

        AllianceMetric[] metricsArr = metrics.toArray(new AllianceMetric[0]);
        long minTurn = Long.MAX_VALUE;
        long maxTurn = 0;
        for (double[] valuesByTurn : valuesByTurnByMetric) {
            for (int i = 0; i < valuesByTurn.length; i++) {
                if (valuesByTurn[i] != Long.MAX_VALUE) {
                    minTurn = Math.min(i + startTurn, minTurn);
                    maxTurn = Math.max(i + startTurn, maxTurn);
                }
            }
        }

        double[] buffer =  new double[metrics.size()];

        String[] labels = new String[metrics.size()];
        {
            int i = 0;
            for (AllianceMetric metric : metrics) {
                labels[i++] = metric.name();
            }
        }
        String title = coalitionName + " metrics";
        TimeNumericTable<Void> table = new TimeNumericTable<>(title, "turn", "value", labels) {
            @Override
            public void add(long turn, Void ignore) {
                int turnRelative = (int) (turn - startTurn);
                for (int i = 0; i < metricsArr.length; i++) {
                    double value = valuesByTurnByMetric[i][turnRelative];
                    if (value != Double.MAX_VALUE && Double.isFinite(value)) {
                        buffer[i] = value;
                    }
                }
                add(turnRelative, buffer);
            }
        };

        for (long turn = minTurn; turn <= maxTurn; turn++) {
            table.add(turn, (Void) null);
        }

        return table;
    }

    public static TimeNumericTable generateTable(AllianceMetric metric, long startTurn, long endTurn, Collection<String> coalitionNames, Set<DBAlliance>... coalitions) {
        if (startTurn < endTurn - Short.MAX_VALUE) throw new IllegalArgumentException("Time range too large");
        if (endTurn > TimeUtil.getTurn()) throw new IllegalArgumentException("End turn must be a current or previous time");

        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap = AllianceMetric.getMetrics(Collections.singleton(metric), startTurn, endTurn, coalitions);
        double[][] valuesByTurnByCoalition = AllianceMetric.toValueByTurnByCoalition(metricMap, metric, startTurn, endTurn, coalitions);

        long minTurn = Long.MAX_VALUE;
        long maxTurn = 0;
        for (double[] valuesByTurn : valuesByTurnByCoalition) {
            for (int i = 0; i < valuesByTurn.length; i++) {
                if (valuesByTurn[i] != Long.MAX_VALUE) {
                    minTurn = Math.min(i + startTurn, minTurn);
                    maxTurn = Math.max(i + startTurn, maxTurn);
                }
            }
        }

        double[] buffer =  new double[coalitions.length];

        String[] labels;
        if (coalitionNames == null || coalitionNames.size() != coalitions.length) {
            labels = new String[coalitions.length];
            for (int i = 0; i < labels.length; i++) labels[i] = "coalition " + (i + 1);
        } else {
            labels = coalitionNames.toArray(new String[0]);
        }
        String title = metric + " by turn";
        TimeNumericTable<Void> table = new TimeNumericTable<>(title, "turn", metric.name(), labels) {

            @Override
            public void add(long turn, Void ignore) {
                int turnRelative = (int) (turn - startTurn);
                for (int i = 0; i < coalitions.length; i++) {
                    double value = valuesByTurnByCoalition[i][turnRelative];
                    if (value != Double.MAX_VALUE && Double.isFinite(value)) {
                        buffer[i] = value;
                    }
                }
                add(turnRelative, buffer);
            }
        };

        for (long turn = minTurn; turn <= maxTurn; turn++) {
            table.add(turn, (Void) null);
        }

        return table;
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
        Set<DBAlliance> allAlliances = new HashSet<>();
        for (Set<DBAlliance> coalition : coalitions) allAlliances.addAll(coalition);
        Set<Integer> aaIds = allAlliances.stream().map(f -> f.getAlliance_id()).collect(Collectors.toSet());
        Set<AllianceMetric> finalMetrics = new HashSet<>(metrics);
        if (aaIds.size() > 1) {
            for (AllianceMetric metric : metrics) {
                if (metric.shouldAverage()) {
                    finalMetrics.add(AllianceMetric.MEMBERS);
                    break;
                }
            }
        }

        return Locutus.imp().getNationDB().getMetrics(aaIds, finalMetrics, minTurn, maxTurn);
    }
}
