package link.locutus.discord.commands.info.optimal;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.*;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class CityBranch implements BiConsumer<Map.Entry<JavaCity, Integer>, PriorityQueue<Map.Entry<JavaCity, Integer>>>, Consumer<Map.Entry<JavaCity, Integer>> {
    private final Continent continent;
    private final Predicate<Project> hasProject;
    private final ObjectArrayFIFOQueue<Map.Entry<JavaCity, Integer>> pool;
    private final Building[] buildings;
    private boolean usedOrigin = false;
    private Building originBuilding = null;
    public CityBranch(JavaCity origin, Continent continent, boolean selfSufficient, double rads, Predicate<Project> hasProject) {
        this.continent = continent;
        this.hasProject = hasProject;
        Map<ResourceBuilding, Double> rssBuildings = new HashMap<>();
        for (Building building : Buildings.values()) {
            if (!building.canBuild(continent)) continue;
            if (!(building instanceof ResourceBuilding rssBuild)) continue;
            if (rads <= 0 && building == Buildings.FARM) continue;
            int cap = rssBuild.cap(hasProject);
            double profit = rssBuild.profitConverted(continent, rads, hasProject, origin, cap) / cap;
            rssBuildings.put(rssBuild, profit);
        }
        List<Building> rssSorted;
        if (selfSufficient) {
            List<Building> rawSorted = new ArrayList<>(rssBuildings.entrySet().stream().filter(e -> e.getKey().getResourceProduced().isRaw()).sorted(Map.Entry.comparingByValue()).map(Map.Entry::getKey).toList().reversed());
            List<Building> manuSorted = new ArrayList<>(rssBuildings.entrySet().stream().filter(e -> !e.getKey().getResourceProduced().isRaw()).sorted(Map.Entry.comparingByValue()).map(Map.Entry::getKey).toList().reversed());
            rssSorted = new ArrayList<>(rawSorted);
            rssSorted.addAll(manuSorted);
        } else {
            rssSorted = new ArrayList<>(rssBuildings.entrySet().stream().sorted(Map.Entry.comparingByValue()).map(Map.Entry::getKey).toList().reversed());
        }
        for (Building building : rssSorted) {
            System.out.println(building.name() + " " + rssBuildings.get(building) + " profit");
        }

        List<Building> allBuildings = new ArrayList<>(rssSorted);
        for (Building building : Buildings.values()) {
            if (building instanceof CommerceBuilding) allBuildings.add(building);
        }
        for (Building building : Buildings.values()) {
            if (building instanceof ServiceBuilding) allBuildings.add(building);
        }
        this.buildings = allBuildings.toArray(new Building[0]);
        this.pool = new ObjectArrayFIFOQueue<>(500000);
    }

    private Map.Entry<JavaCity, Integer> create(Map.Entry<JavaCity, Integer> originPair, Building building, int index) {
        JavaCity origin = originPair.getKey();
        if (!usedOrigin) {
            usedOrigin = true;
            originBuilding = building;

            origin.set(building);
            origin.clearMetrics();
            originPair.setValue(index);

            return originPair;
        }
        if (this.pool.isEmpty()) {
            JavaCity newCity = new JavaCity(origin);
            newCity.remove(originBuilding);
            newCity.set(building);
            return new AbstractMap.SimpleEntry<>(newCity, index);
        }
        Map.Entry<JavaCity, Integer> elem = this.pool.dequeue();
        JavaCity newCity = (elem.getKey());
        int maxIndex = Math.max(index, elem.getValue());

        newCity.set(origin, maxIndex);
        newCity.remove(originBuilding);

        newCity.set(building);
        newCity.clearMetrics();
        elem.setValue(index);

        return elem;
    }

    @Override
    public void accept(Map.Entry<JavaCity, Integer> originPair, PriorityQueue<Map.Entry<JavaCity, Integer>> cities) {
        usedOrigin = false;

        JavaCity origin = originPair.getKey();
        int freeSlots = origin.getFreeSlots();
        if (freeSlots <= 0) return;

        int minBuilding = originPair.getValue();
        int maxBuilding = this.buildings.length;
        Building minBuildingType = buildings[minBuilding];
        boolean buildMore = true;
        {
            int amt = origin.getBuildingOrdinal(minBuildingType.ordinal());
            if (amt != 0) {
                if (minBuildingType instanceof ResourceBuilding rssBuild) {
                    if (amt < minBuildingType.cap(hasProject)) {
                        buildMore = false;
//                        if (rssBuild.getResourceProduced().isRaw()) {
//                            buildMoreRaws = false;
//                        } else {
//                            buildMoreManu = false;
//                        }
                    }
                } else if (minBuildingType instanceof CommerceBuilding) {
                    minBuildingType.cap(hasProject);
                }
            }
        }

        int maxCommerce;
        if (hasProject.test(Projects.INTERNATIONAL_TRADE_CENTER)) {
            if (hasProject.test(Projects.TELECOMMUNICATIONS_SATELLITE)) {
                maxCommerce = 125;
            } else {
                maxCommerce = 115;
            }
        } else {
            maxCommerce = 100;
        }
        for (int i = minBuilding; i < maxBuilding; i++) {
            Building building = buildings[i];
            int ordinal = building.ordinal();
            int amt = origin.getBuildingOrdinal(ordinal);
            if (amt >= building.cap(hasProject)) continue;

            if (building instanceof ResourceBuilding rssBuild) {
                ResourceType rss = rssBuild.getResourceProduced();
                if (buildMore || i == minBuilding) {
                    cities.enqueue(create(originPair, building, i));
                }
//                if (rss.isRaw()) {
//
//                } else if (buildMoreManu || i == minBuilding) {
//                    cities.enqueue(create(originPair, building, i));
//                }
            } else if (building instanceof CommerceBuilding) {
                if (origin.calcCommerce(hasProject) >= maxCommerce) continue;
                {
                    cities.enqueue(create(originPair, building, i));
                }
            } else if (building instanceof ServiceBuilding) {
                if (building == Buildings.HOSPITAL) {
                    Double disease = origin.calcDisease(hasProject);
                    if (disease > 0) {
                        cities.enqueue(create(originPair, building, i));
                    }
                } else if (building == Buildings.RECYCLING_CENTER) {
                    Integer pollution = origin.calcPollution(hasProject);
                    if (pollution > 0) {
                        cities.enqueue(create(originPair, building, i));
                    }
                } else if (building == Buildings.POLICE_STATION) {
                    Double crime = origin.calcCrime(hasProject);
                    if (crime > 0) {
                        cities.enqueue(create(originPair, building, i));
                    }
                }
            }
        }

        if (!usedOrigin) {
            pool.enqueue(originPair);
        }
    }

    @Override
    public void accept(Map.Entry<JavaCity, Integer> originPair) {
        pool.enqueue(originPair);
    }
}
