package link.locutus.discord.commands.info.optimal;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.ICity;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.*;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.db.entities.CityNode;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.search.BFSUtil;

import java.util.*;
import java.util.function.*;

public class CityBranch implements BiConsumer<CityNode, PriorityQueue<CityNode>>, Consumer<CityNode> {
    private final Building[] buildings;
    private boolean usedOrigin = false;
    private Building originBuilding = null;

    private final CityNode.CachedCity origin;

    public CityNode toOptimal(ToDoubleFunction<CityNode> valueFunction, Predicate<CityNode> goal, long timeout) {
        if (goal == null) {
            goal = f -> f.getFreeSlots() <= 0;
        }

        // todo test TObjectPriorityQueue trove
        // todo test eclipse collection

        ObjectHeapPriorityQueue<CityNode> queue = new ObjectHeapPriorityQueue<CityNode>(500000,
                (o1, o2) -> Double.compare(valueFunction.applyAsDouble(o2), valueFunction.applyAsDouble(o1)));

        CityNode init = origin.create();
        origin.setMaxIndex(buildings.length);

        Function<Double, Function<CityNode, Double>> valueCompletionFunction;

        valueCompletionFunction = factor -> (Function<CityNode, Double>) entry -> {
            double parentValue = valueFunction.applyAsDouble(entry);
            int imps = entry.getNumBuildings();
            if (factor <= 1) {
                parentValue = (parentValue * imps) * factor + (parentValue * (1 - factor));
            } else {
                double factor2 = factor - 1;
                parentValue = imps * factor2 + (parentValue * imps) * (1 - factor2);
            }
            return parentValue;
        };

        if (init.getRequiredInfra() > init.getInfra()) {
            throw new IllegalArgumentException("The city infrastructure (" + MathMan.format(init.getInfra()) + ") is too low for the required buildings (required infra: " + MathMan.format(init.getRequiredInfra()) + ")");
        }

        CityNode optimized = new BFSUtil<>(goal, valueFunction, valueCompletionFunction, this, this, init, queue, timeout).search();
        return optimized;
    }

    public CityBranch(CityNode.CachedCity cached) {
        this.origin = cached;
        Map<ResourceBuilding, Double> rssBuildings = new HashMap<>();
        for (Building building : Buildings.values()) {
            if (!building.canBuild(origin.getContinent())) continue;
            if (!(building instanceof ResourceBuilding rssBuild)) continue;
            if (origin.getRads() <= 0 && building == Buildings.FARM) continue;
            int cap = Math.min(10, rssBuild.cap(origin.hasProject()));
            double profit = rssBuild.profitConverted(origin.getContinent(), origin.getRads(), origin.hasProject(), origin.getLand(), cap) / cap;
            if (profit <= 0) continue;
            rssBuildings.put(rssBuild, profit);
        }
        List<Building> rssSorted = new ArrayList<>(rssBuildings.entrySet().stream().sorted(Map.Entry.comparingByValue()).map(Map.Entry::getKey).toList().reversed());
        if (origin.selfSufficient()) {
            rssSorted.removeIf(building -> {
                if (building.getResourceProduced().isManufactured()) {
                    for (ResourceType req : building.getResourceTypesConsumed()) {
                        if (!origin.getContinent().hasResource(req)) {
                            return true;
                        }
                    }
                }
                return false;
            });
            resortList(rssSorted);
        }

        List<Building> allBuildings = new ArrayList<>(rssSorted);
        for (Building building : Buildings.values()) {
            if (building instanceof CommerceBuilding) allBuildings.add(building);
        }
        for (Building building : Buildings.values()) {
            if (building instanceof ServiceBuilding) allBuildings.add(building);
        }
        this.buildings = allBuildings.toArray(new Building[0]);
    }

    private static void resortList(List<Building> rssSorted) {
        Set<ResourceType> seen = new HashSet<>();
        for (int i = 0; i < rssSorted.size(); i++) {
            ResourceBuilding building = (ResourceBuilding) rssSorted.get(i);
            seen.add(building.getResourceProduced());
            if (!building.getResourceProduced().isManufactured()) {
                continue;
            }
            for (ResourceType r : building.getResourceTypesConsumed()) {
                if (!seen.contains(r)) {
                    for (int j = i + 1; j < rssSorted.size(); j++) {
                        if (rssSorted.get(j).getResourceProduced() == r) {
                            rssSorted.remove(i);
                            rssSorted.add(j, building);
                            break;
                        }
                    }
                    i--;
                    break;
                }
            }
        }
    }

    private CityNode create(CityNode origin, int index) {
        if (!usedOrigin) {
            usedOrigin = true;
            origin.setIndex(index);
            return origin;
        }
        CityNode newCity = origin.clone();
        newCity.remove(originBuilding);
        newCity.setIndex(index);
        return newCity;
    }

    private CityNode create(CityNode origin, Building building, int index) {
        if (!usedOrigin) {
            usedOrigin = true;
            originBuilding = building;
            origin.add(building);
            origin.setIndex(index);
            return origin;
        }
        CityNode copy = origin.clone();
        copy.remove(originBuilding);
        copy.add(building);
        copy.setIndex(index);
        return copy;
    }

    @Override
    public void accept(CityNode origin, PriorityQueue<CityNode> cities) {
        usedOrigin = false;
        int freeSlots = origin.getFreeSlots();
        if (freeSlots <= 0) {
            return;
        }

        int currBuildingIndex = origin.getIndex();

        Building building = buildings[currBuildingIndex];
        int amt = origin.getBuildingOrdinal(building.ordinal());
        while (amt >= building.cap(this.origin.hasProject())) {
            currBuildingIndex++;
            if (currBuildingIndex >= this.buildings.length) {
                return;
            }
            building = buildings[currBuildingIndex];
            amt = origin.getBuilding(building);
        }

        if (building instanceof ResourceBuilding rssBuild) {
            if (this.origin.selfSufficient() && rssBuild.getResourceProduced().isManufactured()) {
                double reqAmt = rssBuild.getBaseInput() / 3d;
                if (this.origin.hasProject().test(rssBuild.getResourceProduced().getProject())) {
                    reqAmt *= rssBuild.getResourceProduced().getBoostFactor();
                }
                for (ResourceType req : rssBuild.getResourceTypesConsumed()) {
                    if (origin.getBuilding(req.getBuilding()) < reqAmt) {
                        cities.enqueue(create(origin, currBuildingIndex + 1));
                        return;
                    }
                }
            }
            CityNode next = create(origin, building, currBuildingIndex);
            cities.enqueue(next);
//            CityNode nextCity = next.getKey();
//            boolean checkDisease = nextCity.calcDisease(hasProject) > 0 && nextCity.getBuilding(Buildings.HOSPITAL) < Buildings.HOSPITAL.cap(hasProject);
//            if (checkDisease) {
//                cities.enqueue(create(next, Buildings.HOSPITAL, currBuildingIndex));
//            }
//            boolean checkRecycling = nextCity.calcPollution(hasProject) > 0 && nextCity.getBuilding(Buildings.RECYCLING_CENTER) < Buildings.RECYCLING_CENTER.cap(hasProject);
//            if (checkRecycling) {
//                cities.enqueue(create(next, Buildings.RECYCLING_CENTER, currBuildingIndex));
//            }
        } else if (building instanceof CommerceBuilding) {
            int commerce = origin.getCommerce();
            if (commerce < this.origin.getMaxCommerce()) {
                cities.enqueue(create(origin, building, currBuildingIndex));
            }
        } else if (building instanceof ServiceBuilding) {
            if (building == Buildings.HOSPITAL) {
                double disease = origin.calcDisease(this.origin.hasProject());
                if (disease > 0) {
                    cities.enqueue(create(origin, building, currBuildingIndex));
                }
            } else if (building == Buildings.RECYCLING_CENTER) {
                int pollution = origin.getPollution();
                if (pollution > 0) {
                    cities.enqueue(create(origin, building, currBuildingIndex));
                }
            } else if (building == Buildings.POLICE_STATION) {
                double crime = origin.calcCrime(this.origin.hasProject());
                if (crime > 0) {
                    cities.enqueue(create(origin, building, currBuildingIndex));
                }
            }
        }
        if (currBuildingIndex < this.buildings.length - 1) {
            cities.enqueue(create(origin, currBuildingIndex + 1));
        }
//        if (!usedOrigin)
//        {
//            pool.enqueue(originPair);
//        }
    }

    @Override
    public void accept(CityNode originPair) {
//        pool.enqueue(originPair);
    }
}
