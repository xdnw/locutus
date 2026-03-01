package link.locutus.discord.commands.info.optimal;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.ICity;
import link.locutus.discord.apiv1.enums.city.INationCity;
import link.locutus.discord.apiv1.enums.city.building.*;
import link.locutus.discord.db.entities.CityNode;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.search.BFSUtil;

import java.util.*;
import java.util.function.*;

public class CityBranch implements BiConsumer<CityNode, Consumer<CityNode>>, Consumer<CityNode> {
    private final Building[] buildings;
    private boolean usedOrigin = false;
    private Building originBuilding = null;

    private final CityNode.CachedCity origin;
    private final double[] optimisticBoundScratch;

    public CityNode toOptimal(ToDoubleFunction<INationCity> valueFunction, Predicate<INationCity> goal, long timeout) {
        if (goal == null) {
            goal = f -> f.getFreeSlots() <= 0;
        }

        CityNode init = origin.create();
        origin.setMaxIndex(buildings.length);

        Function<Double, Function<INationCity, Double>> valueCompletionFunction;

        valueCompletionFunction = factor -> (Function<INationCity, Double>) entry -> {
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

        boolean enableRevenueBound = isRevenueObjective((ToDoubleFunction<INationCity>) valueFunction, init);
        ToDoubleFunction<CityNode> upperBoundFunction = enableRevenueBound
            ? node -> node.optimisticRevenueUpperBound(buildings, node.getIndex(), optimisticBoundScratch)
                : node -> Double.POSITIVE_INFINITY;

        CityNode optimized = new BFSUtil<CityNode>((Predicate) goal,
                (ToDoubleFunction) valueFunction,
                (Function) valueCompletionFunction,
                upperBoundFunction,
                this,
                this,
                init,
                timeout).search();
        return optimized;
    }

    private boolean isRevenueObjective(ToDoubleFunction<INationCity> valueFunction, CityNode init) {
        double initExpected = init.getRevenueConverted();
        double initValue = valueFunction.applyAsDouble(init);
        if (Math.abs(initExpected - initValue) > 1e-9) {
            return false;
        }
        CityNode probe = null;
        for (Building building : buildings) {
            int current = init.getBuilding(building);
            if (current >= building.cap(origin.hasProject())) {
                continue;
            }
            probe = init.clone();
            probe.add(building);
            probe.setIndex(init.getIndex());
            break;
        }
        if (probe == null) {
            return true;
        }
        double probeExpected = probe.getRevenueConverted();
        double probeValue = valueFunction.applyAsDouble(probe);
        return Math.abs(probeExpected - probeValue) <= 1e-9;
    }

    public CityBranch(CityNode.CachedCity cached) {
        this.origin = cached;
        this.optimisticBoundScratch = new double[Math.max(4, cached.getMaxSlots())];
        Map<ResourceBuilding, Double> rssBuildings = new Object2DoubleOpenHashMap<>();
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

    public int getBranchingWidth() {
        return buildings.length;
    }

    public CityNode createInitialNode() {
        origin.setMaxIndex(buildings.length);
        return origin.create();
    }

    private static void resortList(List<Building> rssSorted) {
        Set<ResourceType> seen = new ObjectOpenHashSet<>();
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
    public void accept(CityNode origin, Consumer<CityNode> cities) {
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
                        cities.accept(create(origin, currBuildingIndex + 1));
                        return;
                    }
                }
            }
            CityNode next = create(origin, building, currBuildingIndex);
            cities.accept(next);
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
                cities.accept(create(origin, building, currBuildingIndex));
            }
        } else if (building instanceof ServiceBuilding) {
            if (building == Buildings.HOSPITAL) {
                double disease = origin.calcDisease(this.origin.hasProject());
                if (disease > 0) {
                    cities.accept(create(origin, building, currBuildingIndex));
                }
            } else if (building == Buildings.RECYCLING_CENTER) {
                int pollution = origin.getPollution();
                if (pollution > 0) {
                    cities.accept(create(origin, building, currBuildingIndex));
                }
            } else if (building == Buildings.POLICE_STATION) {
                double crime = origin.calcCrime(this.origin.hasProject());
                if (crime > 0) {
                    cities.accept(create(origin, building, currBuildingIndex));
                }
            }
        }
        if (currBuildingIndex < this.buildings.length - 1) {
            cities.accept(create(origin, currBuildingIndex + 1));
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
