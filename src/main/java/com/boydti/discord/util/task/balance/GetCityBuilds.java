package com.boydti.discord.util.task.balance;

import com.boydti.discord.Locutus;
import com.boydti.discord.config.Settings;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.pnw.json.CityBuild;
import com.boydti.discord.util.FileUtil;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.task.GetCitiesTask;
import com.boydti.discord.apiv1.enums.city.JavaCity;
import com.boydti.discord.apiv1.enums.city.building.Building;
import com.boydti.discord.apiv1.enums.city.building.Buildings;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class GetCityBuilds extends GetCitiesTask {
    public GetCityBuilds(int allianceId) {
        super(allianceId);
    }

    public GetCityBuilds(Set<Integer> alliances) {
        super(alliances);
    }

    public GetCityBuilds() {
        super();
    }

    public GetCityBuilds(DBNation... nations) {
        super(nations);
    }

    public Map<DBNation, Map<Integer, JavaCity>> adapt() throws IOException, ExecutionException, InterruptedException {
        return adapt(nation -> {});
    }

    public Map<DBNation, Map<Integer, JavaCity>> adapt(Consumer<DBNation> update) throws IOException, ExecutionException, InterruptedException {
        Map<DBNation, Map<Integer, JavaCity>> newMap = new LinkedHashMap<>();

        Queue<Future<?>> tasks = new ConcurrentLinkedQueue<>();

        for (DBNation nation : nations) {
            Callable<Boolean> task = () -> {
                update.accept(nation);
                String url = "" + Settings.INSTANCE.PNW_URL() + "/?id=62&n=" + URLEncoder.encode(nation.getLeader(), StandardCharsets.UTF_8);
                String html = FileUtil.readStringFromURL(url);
                Document dom = Jsoup.parse(html);

                Element table = dom.getElementsByClass("nationtable").get(0);
                Elements rows = table.getElementsByTag("tr");

                Elements header = rows.get(0).getElementsByTag("th");
                Map<Integer, JavaCity> cityBuildMap = new LinkedHashMap<>();
                JavaCity[] cityBuilds = new JavaCity[header.size() - 2];
                for (int i = 2; i < header.size(); i++) {
                    String cityUrl = header.get(i).getElementsByTag("a").get(0).attr("href");
                    int cityId = Integer.parseInt(cityUrl.split("=")[1]);
                    JavaCity build = new JavaCity();
                    cityBuildMap.put(cityId, build);
                    cityBuilds[i - 2] = build;
                }

                boolean failedFetch = false;

                for (int i = 1; i < rows.size(); i++) {
                    Element row = rows.get(i);
                    Elements columns = row.getElementsByTag("td");
                    String imp = columns.get(0).text();

                    if (columns.size() != header.size()) {
                        failedFetch = true;
                    }

                    for (int j = 2; j < header.size(); j++) {
                        JavaCity build = cityBuilds[j - 2];

                        String value = columns.get(j).text().replaceAll(" days", "").replaceAll("%", "");
                        Double valueDouble = null;
                        try {
                            valueDouble = MathMan.parseDouble(value);
                        } catch (NumberFormatException ignore) {
                        }
                        Building building = null;
                        switch (imp) {
                            case "Uranium Mines":
                                build.set(building = Buildings.URANIUM_MINE, valueDouble.intValue());
                                break;
                            case "Iron Mines":
                                build.set(building = Buildings.IRON_MINE, valueDouble.intValue());
                                break;
                            case "Coal Mines":
                                build.set(building = Buildings.COAL_MINE, valueDouble.intValue());
                                break;
                            case "City Age":
                                build.setAge(valueDouble.intValue());
                                break;
                            case "Infrastructure":
                                build.setInfra(valueDouble.intValue());
                                break;
                            case "Land":
                                build.setLand(valueDouble);
                                break;
                            case "Population":
                                build.setPopulation(nation::hasProject, valueDouble.intValue());
                                break;
                            case "Disease":
                                build.setDisease(nation::hasProject, valueDouble);
                                break;
                            case "Crime":
                                build.setCrime(nation::hasProject, valueDouble);
                                break;
                            case "Pollution":
                                build.setPollution(nation::hasProject, valueDouble.intValue());
                                break;
                            case "Commerce":
                                build.setCommerce(nation::hasProject, valueDouble.intValue());
                                break;
                            case "Powered":
                                build.setPowered(nation::hasProject, value.equalsIgnoreCase("Yes"));
                                break;
                            case "Coal Power":
                                build.set(building = Buildings.COAL_POWER, valueDouble.intValue());
                                break;
                            case "Oil Power":
                                build.set(building = Buildings.OIL_POWER, valueDouble.intValue());
                                break;
                            case "Nuclear Power":
                                build.set(building = Buildings.NUCLEAR_POWER, valueDouble.intValue());
                                break;
                            case "Wind Power":
                                build.set(building = Buildings.WIND_POWER, valueDouble.intValue());
                                break;
                            case "Oil Wells":
                                build.set(building = Buildings.OIL_WELL, valueDouble.intValue());
                                break;
                            case "Bauxite Mines":
                                build.set(building = Buildings.BAUXITE_MINE, valueDouble.intValue());
                                break;
                            case "Lead Mines":
                                build.set(building = Buildings.LEAD_MINE, valueDouble.intValue());
                                break;
                            case "Farms":
                                build.set(building = Buildings.FARM, valueDouble.intValue());
                                break;
                            case "Oil Refineries":
                                build.set(building = Buildings.GAS_REFINERY, valueDouble.intValue());
                                break;
                            case "Steel Mills":
                                build.set(building = Buildings.STEEL_MILL, valueDouble.intValue());
                                break;
                            case "Aluminum Refineries":
                                build.set(building = Buildings.ALUMINUM_REFINERY, valueDouble.intValue());
                                break;
                            case "Munitions Factories":
                                build.set(building = Buildings.MUNITIONS_FACTORY, valueDouble.intValue());
                                break;
                            case "Police Stations":
                                build.set(building = Buildings.POLICE_STATION, valueDouble.intValue());
                                break;
                            case "Hospitals":
                                build.set(building = Buildings.HOSPITAL, valueDouble.intValue());
                                break;
                            case "Recycling Centers":
                                build.set(building = Buildings.RECYCLING_CENTER, valueDouble.intValue());
                                break;
                            case "Subways":
                                build.set(building = Buildings.SUBWAY, valueDouble.intValue());
                                break;
                            case "Supermarkets":
                                build.set(building = Buildings.SUPERMARKET, valueDouble.intValue());
                                break;
                            case "Banks":
                                build.set(building = Buildings.BANK, valueDouble.intValue());
                                break;
                            case "Shopping Malls":
                                build.set(building = Buildings.MALL, valueDouble.intValue());
                                break;
                            case "Stadiums":
                                build.set(building = Buildings.STADIUM, valueDouble.intValue());
                                break;
                            case "Barracks":
                                build.set(building = Buildings.BARRACKS, valueDouble.intValue());
                                break;
                            case "Factories":
                                build.set(building = Buildings.FACTORY, valueDouble.intValue());
                                break;
                            case "Hangars":
                                build.set(building = Buildings.HANGAR, valueDouble.intValue());
                                break;
                            case "Drydocks":
                                build.set(building = Buildings.DRYDOCK, valueDouble.intValue());
                                break;
                        }
//                        if (building != null && invalid && build.get(building) == 0) {
//                            int avg = (int) (Double.parseDouble(columns.get(1).text()) / (header.size() - 2));
//                            if (avg > 0) {
//                                build.set(building, avg);
//                            }
//                        }
                    }

                    if (failedFetch) {
                        String urlBase = "https://politicsandwar.com/api/city_export.php?city_id=";
                        for (Map.Entry<Integer, JavaCity> cityEntry : cityBuildMap.entrySet()) {
                            int id = cityEntry.getKey();

                            JavaCity city = cityEntry.getValue();
                            String json = FileUtil.readStringFromURL(urlBase + id);
                            json = json.substring(json.indexOf('{'), json.indexOf('}') + 1);
                            if (json.contains("{")) {
                                CityBuild build = CityBuild.of(json);
                                JavaCity tmp = new JavaCity(build);
                                for (Building building : Buildings.values()) {
                                    city.set(building, tmp.get(building));
                                }
                            }
                        }

                    }
                }
                newMap.put(nation, cityBuildMap);
                return true;
            };
            tasks.add(Locutus.imp().getExecutor().submit(task));
        }

        Future<?> task = tasks.poll();
        while (task != null) {
            task.get();
            task = tasks.poll();
        }

        return newMap;
    }
}
