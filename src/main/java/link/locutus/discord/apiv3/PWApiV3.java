//package link.locutus.discord.apiv3;
//
//import link.locutus.discord.Locutus;
//import link.locutus.discord.config.Settings;
//import link.locutus.discord.db.entities.DBCity;
//import link.locutus.discord.db.entities.Transaction2;
//import link.locutus.discord.event.bank.TransactionEvent;
//import link.locutus.discord.db.entities.DBNation;
//import link.locutus.discord.util.scheduler.ThrowingConsumer;
//import link.locutus.discord.util.FileUtil;
//import link.locutus.discord.util.StringMan;
//import link.locutus.discord.util.TimeUtil;
//import com.google.gson.JsonArray;
//import com.google.gson.JsonElement;
//import com.google.gson.JsonObject;
//import com.google.gson.JsonParseException;
//import com.google.gson.JsonParser;
//import link.locutus.discord.apiv1.core.ApiKeyPool;
//import link.locutus.discord.apiv1.enums.DomesticPolicy;
//import link.locutus.discord.apiv1.enums.NationColor;
//import link.locutus.discord.apiv1.enums.Rank;
//import link.locutus.discord.apiv1.enums.city.building.Building;
//import link.locutus.discord.apiv1.enums.city.building.Buildings;
//import link.locutus.discord.apiv1.enums.city.project.Project;
//import link.locutus.discord.apiv1.enums.city.project.Projects;
//
//import java.io.IOException;
//import java.net.HttpRetryException;
//import java.nio.charset.StandardCharsets;
//import java.text.ParseException;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//import java.util.concurrent.TimeUnit;
//import java.util.function.BiConsumer;
//import java.util.stream.Collectors;
//
//@Deprecated
//public class PWApiV3 {
//
//    private static final Map<String, Integer> improvementIds = new HashMap<>();
//    static {
//        for (Building building : Buildings.values()) {
//            String name = building.name().substring(3).toLowerCase();
//            int id = building.ordinal();
//            improvementIds.put(name, id);
//        }
//    }
//    public static int PER_PAGE = 500;
//    private final String urlBase;
//    private final ApiKeyPool pool;
//
//    public PWApiV3(ApiKeyPool pool) {
//        this.urlBase = "https://api" + (Settings.INSTANCE.TEST ? "-test" : "") + ".politicsandwar.com/graphql?api_key=%key%";
//        this.pool = pool;
//    }
//
//    public String getUrl() {
//        String key = pool.getNextApiKey();
//        return getUrl(key);
//    }
//
//    public String getUrl(String key) {
//        return urlBase.replace("%key%", key);
//    }
//
//    private String fetchRaw(String query) throws IOException {
//        String queryFull = "{\"query\":\"{" + query + "}\"}";
//        byte[] queryBytes = queryFull.getBytes(StandardCharsets.UTF_8);
//
//        int errored = 0;
//        while (true) {
//            String key = pool.getNextApiKey();
//            try {
//
//                String url = getUrl(key);
//                String result = FileUtil.readStringFromURL(url, queryBytes, true, null,
//                        c -> c.setRequestProperty("Content-Type", "application/json"));
//                return result;
//            } catch (HttpRetryException e) {
//                pool.removeKey(key);
//            } catch (IOException e1) {
//                e1.printStackTrace();
//                errored++;
//                if (errored > 3) {
//                    e1.printStackTrace();
//                    throw new IOException(e1.getMessage().replace(key, "XXXX"));
//                }
//                try {
//                    Thread.sleep(1000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            } catch (Throwable e) {
//                e.printStackTrace();
//                throw new RuntimeException(e.getMessage().replace(key, "XXXX"));
//            }
//        }
//    }
//
//    private JsonObject fetch(String query) throws IOException {
//        String raw = fetchRaw(query);
//        try {
//            JsonElement json = JsonParser.parseString(raw);
//            return json.getAsJsonObject();
//        } catch (JsonParseException e) {
//            e.printStackTrace();
//            throw e;
//        }
//    }
//
//    public void updateNations(ThrowingConsumer<ThrowingConsumer<String>> taskConsumer, boolean spies, boolean tx, boolean city, boolean project, boolean policy) throws IOException, ParseException {
//        Set<DBNation> toUpdate = new HashSet<>();
//        long turn = TimeUtil.getTurn();
//
//        Map<Integer, Map<Integer, DBCity>> allCities = new HashMap<>();
//        long txCutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14);
//        Set<Integer> existingTransactions = Locutus.imp().getBankDB().getTransactions(txCutoff, true).stream().map(f -> f.tx_id).collect(Collectors.toSet());
//
//        List<Transaction2> newTransactions = new ArrayList<>();
//
//        BiConsumer<DBNation, Boolean> spyConsumer = !spies ? null : new BiConsumer<DBNation, Boolean>() {
//            @Override
//            public void accept(DBNation nation, Boolean espionageFull) {
//                long currentTurn = nation.getEspionageFullTurn();
//                if (currentTurn > 0 && espionageFull) {
//                    nation.setEspionageFull(-currentTurn);
//                    toUpdate.add(nation);
//                } else if (currentTurn != turn && !espionageFull) {
//                    nation.setEspionageFull(turn);
//                    toUpdate.add(nation);
//                }
//            }
//        };
//
//        BiConsumer<Integer, List<Transaction2>> txConsumer = !tx ? null : new BiConsumer<Integer, List<Transaction2>>() {
//            @Override
//            public void accept(Integer integer, List<Transaction2> records) {
//                if (records.isEmpty()) return;
//                records.removeIf(f -> existingTransactions.contains(f.tx_id));
//                if (records.isEmpty()) return;
//                newTransactions.addAll(records);
//            }
//        };
//
//        BiConsumer<Integer, Map<Integer, DBCity>> cityConsumer = !city ? null : new BiConsumer<Integer, Map<Integer, DBCity>>() {
//            @Override
//            public void accept(Integer nationId, Map<Integer, DBCity> cities) {
//                if (!cities.isEmpty()) {
//                    allCities.put(nationId, cities);
//                }
//            }
//        };
//
//        BiConsumer<DBNation, Set<Project>> projectConsumer = !project ? null : new BiConsumer<DBNation, Set<Project>>() {
//            @Override
//            public void accept(DBNation nation, Set<Project> projects) {
//                if ((nation.getProjectBitMask() == 0 && !projects.isEmpty()) ||
//                        (nation.getProjectBitMask() != 0 && !nation.getProjects().equals(projects))) {
//                    // set projects
//                    nation.setProjectsRaw(0);
//                    for (Project project : projects) {
//                        nation.setProject(project);
//                    }
//                    toUpdate.add(nation);
//                }
//            }
//        };
//
//        BiConsumer<DBNation, DomesticPolicy> policyConsumer = !policy ? null : new BiConsumer<DBNation, DomesticPolicy>() {
//            @Override
//            public void accept(DBNation nation, DomesticPolicy domesticPolicy) {
//                DomesticPolicy currentPolicy = nation.getDomesticPolicy();
//                if (currentPolicy != domesticPolicy) {
//                    nation.setDomesticPolicy(domesticPolicy);
//                    toUpdate.add(nation);
//                }
//            }
//        };
//
//        ThrowingConsumer<String> task = new ThrowingConsumer<String>() {
//            @Override
//            public void acceptThrows(String filter) throws Exception {
//                fetchNations(filter, null, spyConsumer, txConsumer, cityConsumer, projectConsumer, policyConsumer);
//            }
//        };
//
//        taskConsumer.accept(task);
//
//        Locutus.imp().getNationDB().addCities(allCities);
//        // newTransactions
//        Locutus.imp().getBankDB().addTransactions(newTransactions);
//        // toUpdate
//        Locutus.imp().getNationDB().saveNations(toUpdate);
//
//        for (Transaction2 newTransaction : newTransactions) {
//            new TransactionEvent(newTransaction).post();
//        }
//
//    }
//
//    public void updateNations(int allianceId) throws IOException, ParseException {
//        updateNations(new ThrowingConsumer<ThrowingConsumer<String>>() {
//            @Override
//            public void acceptThrows(ThrowingConsumer<String> task) throws Exception {
//                task.accept("vmode:false,alliance_id:" + allianceId);
//            }
//        }, true, true, true, true, true);
//    }
//
//    public void updateNations(boolean spies, boolean tx, boolean city, boolean project, boolean policy) throws IOException, ParseException {
//        updateNations(new ThrowingConsumer<ThrowingConsumer<String>>() {
//            @Override
//            public void acceptThrows(ThrowingConsumer<String> task) throws Exception {
//                for (NationColor value : NationColor.values()) {
//                    String filter;
//                    if (value == NationColor.BEIGE || value == NationColor.GRAY) {
//                        for (int pos = Rank.MEMBER.id; pos <= Rank.LEADER.id; pos++) {
//                            filter = "vmode:false,alliance_position:" + pos + ",color:\\\"" + value.name().toLowerCase() + "\\\"";
//                            task.accept(filter);
//                        }
//                    } else {
//                        filter = "vmode:false,color:\\\"" + value.name().toLowerCase() + "\\\"";
//                        task.accept(filter);
//                    }
//                }
//            }
//        }, spies, tx, city, project, policy);
//    }
//
//    public void updateAllNations() throws IOException, ParseException {
//        updateNations(new ThrowingConsumer<ThrowingConsumer<String>>() {
//            @Override
//            public void acceptThrows(ThrowingConsumer<String> task) throws Exception {
//                task.accept("vmode:false");
//            }
//        }, true, true, true, true, true);
//    }
//
//    private void fetchNations(String filters, Integer perPage, BiConsumer<DBNation, Boolean> spyConsumer, BiConsumer<Integer, List<Transaction2>> txConsumer, BiConsumer<Integer, Map<Integer, DBCity>> citiesConsumer, BiConsumer<DBNation, Set<Project>> projectConsumer, BiConsumer<DBNation, DomesticPolicy> domesticPolicy) throws IOException, ParseException {
//        long fetched = System.currentTimeMillis();
//
//        if (perPage == null) perPage = PER_PAGE;
//
//        List<String> dataArgs = new ArrayList<>();
//        if (citiesConsumer != null) {
//            perPage /= 3;
//            dataArgs.add("cities {id,date,infrastructure,land,powered,oilpower,windpower,coalpower,nuclearpower,coalmine,oilwell,uramine,barracks,farm,policestation,hospital,recyclingcenter,subway,supermarket,bank,mall,stadium,leadmine,ironmine,bauxitemine,gasrefinery,aluminumrefinery,steelmill,munitionsfactory,factory,airforcebase,drydock}");
//        }
//        if (spyConsumer != null) {
//            dataArgs.add("espionage_available");
//        }
//        if (domesticPolicy != null) {
//            dataArgs.add("dompolicy");
//        }
//        if (txConsumer != null) {
//            perPage /= 2;
//            String bankRecFields = "{id,date,sid,stype,rid,rtype,pid,note,money,coal,oil,uranium,iron,bauxite,lead,gasoline,munitions,steel,aluminum,food}";
//            dataArgs.add("sent_bankrecs " + bankRecFields);
//            dataArgs.add("received_bankrecs " + bankRecFields);
//        }
//        if (projectConsumer != null) {
//            for (Project project : Projects.values) {
//                dataArgs.add(project.getApiName());
//            }
//        }
//
//        String dataField = "nations";
//        String query = "nations(" + filters + (filters.isEmpty() ? "" : ",") + "first:%perpage%,page:%page%){data{id," + StringMan.join(dataArgs, ",") + "}}";
//        JsonObject json = fetchPaginate(query, dataField, perPage);
//        JsonArray nations = json.get("data").getAsJsonObject().get(dataField).getAsJsonObject().get("data").getAsJsonArray();
//
//        for (JsonElement nationJsonElem : nations) {
//            JsonObject nationJson = nationJsonElem.getAsJsonObject();
//            int nationId = nationJson.get("id").getAsInt();
//
//            if (citiesConsumer != null) {
//                citiesConsumer.accept(nationId, readCities(nationJson.get("cities").getAsJsonArray(), fetched));
//            }
//
//            if (txConsumer != null) {
//                List<Transaction2> transfers = new ArrayList<>();
//                JsonArray sent = nationJson.get("sent_bankrecs").getAsJsonArray();
//                JsonArray received = nationJson.get("received_bankrecs").getAsJsonArray();
//                for (JsonElement recordJson : sent) {
//                    transfers.add(Transaction2.fromAPiv3(recordJson.getAsJsonObject()));
//                }
//                for (JsonElement recordJson : received) {
//                    transfers.add(Transaction2.fromAPiv3(recordJson.getAsJsonObject()));
//                }
//                txConsumer.accept(nationId, transfers);
//            }
//
//            if (spyConsumer != null || projectConsumer != null || domesticPolicy != null) {
//                DBNation nation = DBNation.byId(nationId);
//                if (nation != null) {
//                    if (spyConsumer != null) {
//                        spyConsumer.accept(nation, nationJson.get("espionage_available").getAsBoolean());
//                    }
//                    if (projectConsumer != null) {
//                        projectConsumer.accept(nation, readProjects(nationJson));
//                    }
//                    if (domesticPolicy != null) {
//                        domesticPolicy.accept(nation, DomesticPolicy.parse(nationJson.get("dompolicy").getAsString()));
//                    }
//                }
//            }
//        }
//    }
//
//    private Set<Project> readProjects(JsonObject nationJson) {
//        Set<Project> projects = new HashSet<>();
//        for (Project project : Projects.values) {
//            if (nationJson.get(project.getApiName()).getAsBoolean()) {
//                projects.add(project);
//            }
//        }
//        return projects;
//    }
//
//    public Map<Integer, DBCity> readCities(JsonArray citiesJson, long fetched) throws ParseException {
//        Map<Integer, DBCity> cities = new HashMap<>();
//        for (JsonElement cityElem : citiesJson) {
//            JsonObject city = cityElem.getAsJsonObject();
//
//            DBCity cityV3 = new DBCity();
//            cityV3.fetched = fetched;
//
//            for (Map.Entry<String, JsonElement> entry : city.entrySet()) {
//                String key = entry.getKey();
//                switch (key) {
//                    case "id":
//                        cityV3.id = entry.getValue().getAsInt();
//                        break;
//                    case "date":
//                        cityV3.created = TimeUtil.parseDate(TimeUtil.YYYY_MM_DD_FORMAT, entry.getValue().getAsString());
//                        break;
//                    case "infrastructure":
//                        cityV3.infra = entry.getValue().getAsDouble();
//                        break;
//                    case "land":
//                        cityV3.land = entry.getValue().getAsDouble();
//                        break;
//                    case "powered":
//                        cityV3.powered = entry.getValue().getAsBoolean();
//                        break;
//                    case "airforcebase":
//                        key = "hangars";
//                    default:
//                        int amt = entry.getValue().getAsInt();
//                        if (amt != 0) {
//                            Integer impId = improvementIds.get(key);
//                            if (impId == null) throw new IllegalArgumentException("Invalid improvement: " + key);
//                            cityV3.buildings[impId] = (byte) amt;
//                        }
//
//                }
//            }
//            cities.put(cityV3.id, cityV3);
//        }
//        return cities;
//    }
//
//    public JsonObject fetchPaginate(String query, String dataField, int perPage) throws IOException {
//        JsonObject root = null;
//        for (int i = 1; ; i++) {
//            String formatted = query.replace("%perpage%", "" + perPage).replace("%page%", i + "");
//            JsonObject obj = fetch(formatted);
//
//            JsonArray newData = obj.get("data").getAsJsonObject().get(dataField).getAsJsonObject().get("data").getAsJsonArray();
//            if (root == null) root = obj;
//            else {
//                JsonArray rootData = root.get("data").getAsJsonObject().get(dataField).getAsJsonObject().get("data").getAsJsonArray();
//                rootData.addAll(newData);
//            }
//            if (newData.size() < perPage) {
//                break;
//            }
//        }
//        return root;
//    }
//}
