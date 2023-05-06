package link.locutus.discord.pnw;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface NationList extends NationFilter {
    @Override
    default String getFilter() {
        return null;
    }

    @Override
    default boolean test(DBNation dbNation){
        return getNations().contains(dbNation);
    }

    Collection<DBNation> getNations();

    default Set<Integer> getAllianceIds() {
        return getNations().stream().map(DBNation::getAlliance_id).collect(Collectors.toSet());
    }

    default DBNation getTotal() {
        return DBNation.createFromList(null, getNations(), false);
    }

    default DBNation getAverage() {
        return DBNation.createFromList(null, getNations(), true);
    }

    default Set<Integer> getNationIds() {
        return getNations().stream().map(DBNation::getNation_id).collect(Collectors.toSet());
    }

    default void updateCities(boolean events) {
        if (events) {
            Locutus.imp().runEventsAsync(f -> Locutus.imp().getNationDB().updateCitiesOfNations(getNationIds(), true, f));
        } else {
            Locutus.imp().getNationDB().updateCitiesOfNations(getNationIds(), true, null);
        }
    }


    default double[] getAverageMMR(boolean update) {
        double[] total = getTotalMMR(update);
        double num = total[4];
        return new double[] {total[0] / num, total[1] / num, total[2] / num, total[3] / num};
    }

    default  <T> Map<T, NationList> groupBy(Function<DBNation, T> groupBy) {
        Map<T, List<DBNation>> mapList = new HashMap<>();
        for (DBNation nation : getNations()) {
            T group = groupBy.apply(nation);
            mapList.computeIfAbsent(group, f -> new ArrayList<>()).add(nation);
        }
        Map<T, NationList> result = new HashMap<>();
        for (Map.Entry<T, List<DBNation>> entry : mapList.entrySet()) {
            result.put(entry.getKey(), new SimpleNationList(entry.getValue()));
        }
        return result;
    }

    default  Map<Integer, NationList> byTier() {
        return groupBy(DBNation::getCities);
    }

    default <T> Stream<T> stream(Function<? super DBNation, ? extends T> map) {
        return getNations().stream().map(map);
    }

    default Stream<DBNation> stream() {
        return getNations().stream();
    }

    /**
     * in the form [barracks, factories, hangars, drydocks, cities]
     * @param update
     * @return
     */
    default double[] getTotalMMR(boolean update) {
        double barracks = 0;
        double factories = 0;
        double hangars = 0;
        double drydocks = 0;

        int numCities = 0;

        for (DBNation nation : getNations()) {
            Map<Integer, JavaCity> cities = nation.getCityMap(update, false);
            numCities += cities.size();
            for (Map.Entry<Integer, JavaCity> cityEntry : cities.entrySet()) {
                barracks += cityEntry.getValue().get(Buildings.BARRACKS);
                factories += cityEntry.getValue().get(Buildings.FACTORY);
                hangars += cityEntry.getValue().get(Buildings.HANGAR);
                drydocks += cityEntry.getValue().get(Buildings.DRYDOCK);
            }
        }

        return new double[] {barracks, factories, hangars, drydocks, numCities};
    }

    default double[] getMilitaryBuyPct(boolean update) {
        DBNation total = getTotal();

        double oldSoldier = 0;
        double oldTanks = 0;
        double oldAir = 0;
        double oldSea = 0;

        long yesterday = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);

        Collection<DBNation> aaNations = getNations();
        for (DBNation nation : aaNations) {
            oldSoldier += nation.getUnits(MilitaryUnit.SOLDIER, yesterday);
            oldTanks += nation.getUnits(MilitaryUnit.TANK, yesterday);
            oldAir += nation.getUnits(MilitaryUnit.AIRCRAFT, yesterday);
            oldSea += nation.getUnits(MilitaryUnit.SHIP, yesterday);
        }

        double soldierBuyTotal = (total.getSoldiers() - oldSoldier) / oldSoldier;
        double tankBuyTotal = (total.getTanks() - oldTanks) / oldTanks;
        double airBuyTotal = (total.getAircraft() - oldAir) / oldAir;
        double navyBuyTotal = (total.getShips() - oldSea) / oldSea;

        return new double[] {soldierBuyTotal, tankBuyTotal, airBuyTotal, navyBuyTotal};
    }

    default double getScore() {
        Collection<DBNation> nations = new ArrayList<>(getNations());
        nations.removeIf(f -> f.getPosition() <= 0 || f.getVm_turns() != 0);
        double total = 0;
        for (DBNation nation : nations) {
            total += nation.getScore();
        }
        return total;
    }

    default Set<Integer> updateSpies(boolean updateManually) {
        Set<DBNation> toUpdate = new HashSet<>(getNations());
        toUpdate.removeIf(f -> f.getVm_turns() > 0 || f.getActive_m() > 7200);
        Set<Integer> alliances = new HashSet<>();
        for (DBNation nation : toUpdate) {
            if (nation.getPosition() > Rank.APPLICANT.id) {
                alliances.add(nation.getAlliance_id());
            }
        }
        Set<Integer> updated = new HashSet<>();
        boolean hasUpdated = false;
        for (Integer allianceId : alliances) {
            Set<Integer> result = DBAlliance.getOrCreate(allianceId).updateSpies(false);
            updated.addAll(result);
            toUpdate.removeIf(f -> result.contains(f.getId()));
        }
        if (updateManually) {
            for (DBNation nation : toUpdate) {
                nation.updateSpies();
                updated.add(nation.getId());
            }
        }
        return updated;
    }

    default boolean contains(DBNation f) {
        return getNations().contains(f);
    }
}
