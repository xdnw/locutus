package link.locutus.discord.commands.info.optimal;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.*;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;

import java.util.AbstractMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class CityBranch implements BiConsumer<Map.Entry<JavaCity, Integer>, PriorityQueue<Map.Entry<JavaCity, Integer>>>, Consumer<Map.Entry<JavaCity, Integer>> {
    private final Continent continent;
    private final Predicate<Project> hasProject;
    private final ObjectArrayFIFOQueue<Map.Entry<JavaCity, Integer>> pool;
    private boolean usedOrigin = false;
    private Building originBuilding = null;
    public CityBranch(Continent continent, double maxDisease, double minPop, boolean selfSufficient, Predicate<Project> hasProject) {
        this.continent = continent;
        this.hasProject = hasProject;

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

        if (originPair.getValue() <= 4) {
            double unpowered = origin.getInfra() - origin.getPoweredInfra();
            if (unpowered > 500 || (unpowered > 250 && origin.getInfra() > 2000)) {
                cities.enqueue(create(originPair, Buildings.NUCLEAR_POWER, 4));
                return;
            }
            if (unpowered > 250) {
                if (Buildings.OIL_WELL.canBuild(continent)) {
                    cities.enqueue(create(originPair, Buildings.OIL_POWER, 4));
                } else {
                    cities.enqueue(create(originPair, Buildings.COAL_POWER, 4));
                }
                return;
            }
            if (unpowered > 0) {
                cities.enqueue(create(originPair, Buildings.WIND_POWER, 4));
                return;
            }
        }

        int freeSlots = origin.getFreeSlots();
        if (freeSlots <= 0) return;

        int minBuilding = originPair.getValue();
        int maxBuilding = Buildings.BUILDINGS.length - 4;
        boolean buildMoreRaws = true;
        boolean buildMoreManu = true;
        boolean buildMoreCommerce = true;
        {
            Building minBuildingType = Buildings.get(minBuilding);
            int amt = origin.get(minBuilding);
            if (amt != 0) {
                if (minBuildingType instanceof ResourceBuilding rssBuild) {
                    if (amt < minBuildingType.cap(hasProject)) {
                        if (rssBuild.getResourceProduced().isRaw()) {
                            buildMoreRaws = false;
                        } else {
                            buildMoreManu = false;
                        }
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
            Building building = Buildings.get(i);
            if (!building.canBuild(continent)) continue;
            int amt = origin.get(i);
            if (amt >= building.cap(hasProject)) continue;

            if (building instanceof ResourceBuilding rssBuild) {
                ResourceType rss = rssBuild.getResourceProduced();
                if (rss.isRaw()) {
                    if (buildMoreRaws || i == minBuilding) {
                        cities.enqueue(create(originPair, building, i));
                    }
                } else if (buildMoreManu || i == minBuilding) {
                    cities.enqueue(create(originPair, building, i));
                }
            } else if (building instanceof CommerceBuilding) {

                if (origin.getCommerce(hasProject) >= maxCommerce) continue;
                {
                    cities.enqueue(create(originPair, building, i));
                }
            } else if (building instanceof ServiceBuilding) {
                if (building == Buildings.HOSPITAL) {
                    Double disease = origin.getDisease(hasProject);
                    if (disease > 0) {
                        cities.enqueue(create(originPair, building, i));
                    }
                } else if (building == Buildings.RECYCLING_CENTER) {
                    Integer pollution = origin.getPollution(hasProject);
                    if (pollution > 0) {
                        cities.enqueue(create(originPair, building, i));
                    }
                } else if (building == Buildings.POLICE_STATION) {
                    Double crime = origin.getCrime(hasProject);
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
