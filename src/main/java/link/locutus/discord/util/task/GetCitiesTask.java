package link.locutus.discord.util.task;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.apiv1.domains.subdomains.SCityContainer;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class GetCitiesTask implements Callable<Map<DBNation, List<SCityContainer>>> {
    protected final Set<DBNation> nations;
    protected final Map<Integer, DBNation> nationsMap;
    protected Predicate<SCityContainer> filter;

    public GetCitiesTask(int allianceId) {
        this(Collections.singleton(allianceId));
    }

    public GetCitiesTask(Set<Integer> alliances) {
        this.nations = Locutus.imp().getNationDB().getNations(alliances);
        this.nationsMap = nations.stream().collect(Collectors.toMap(DBNation::getNation_id, nation -> nation));
        this.filter = city -> {
            DBNation nation = nationsMap.get(Integer.parseInt(city.getNationId()));
            return nation != null && (alliances.isEmpty() || alliances.contains(nation.getAlliance_id()));
        };
    }

    public GetCitiesTask() {
        this(Collections.emptySet());
        this.filter = (c -> true);
    }

    public GetCitiesTask(DBNation... nationsArr) {
        this.nations = new HashSet<>(Arrays.asList(nationsArr));
        this.nationsMap = nations.stream().collect(Collectors.toMap(DBNation::getNation_id, nation -> nation));
        this.filter = new Predicate<SCityContainer>() {
            @Override
            public boolean test(SCityContainer city) {
                return nationsMap.containsKey(Integer.parseInt(city.getNationId()));
            }
        };
    }

    @Override
    public Map<DBNation, List<SCityContainer>> call() throws IOException {
        List<SCityContainer> allCities = Locutus.imp().getPnwApi().getAllCities().getAllCities();
        List<SCityContainer> cityContainers = allCities.stream().filter(Objects::nonNull).filter(filter).collect(Collectors.toList());

        Map<DBNation, List<SCityContainer>> nationCities = new HashMap<>();

        for (SCityContainer city : cityContainers) {
            if (city == null) continue;
            Integer nationId = Integer.parseInt(city.getNationId());
            DBNation nation = nationsMap.get(nationId);

            if (nation == null) continue;

            List<SCityContainer> list = nationCities.computeIfAbsent(nation, v -> new ArrayList<>());
            list.add(city);
        }

        return nationCities;
    }
}
