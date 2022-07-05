package link.locutus.discord.apiv2;

import link.locutus.discord.apiv1.IPoliticsAndWar;
import link.locutus.discord.apiv1.entities.ApiRecord;
import link.locutus.discord.apiv1.entities.BankRecord;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.util.scheduler.ThrowingFunction;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.FileUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.core.CacheClient;
import link.locutus.discord.apiv1.core.QueryExecutor;
import link.locutus.discord.apiv1.domains.AllCities;
import link.locutus.discord.apiv1.domains.Alliance;
import link.locutus.discord.apiv1.domains.AllianceMembers;
import link.locutus.discord.apiv1.domains.Alliances;
import link.locutus.discord.apiv1.domains.Applicants;
import link.locutus.discord.apiv1.domains.Bank;
import link.locutus.discord.apiv1.domains.City;
import link.locutus.discord.apiv1.domains.Entity;
import link.locutus.discord.apiv1.domains.Members;
import link.locutus.discord.apiv1.domains.Nation;
import link.locutus.discord.apiv1.domains.NationMilitary;
import link.locutus.discord.apiv1.domains.Nations;
import link.locutus.discord.apiv1.domains.TradeHistory;
import link.locutus.discord.apiv1.domains.TradePrice;
import link.locutus.discord.apiv1.domains.War;
import link.locutus.discord.apiv1.domains.WarAttacks;
import link.locutus.discord.apiv1.domains.Wars;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.queries.AllCitiesQuery;
import link.locutus.discord.apiv1.queries.AllianceMembersQuery;
import link.locutus.discord.apiv1.queries.AllianceQuery;
import link.locutus.discord.apiv1.queries.AlliancesQuery;
import link.locutus.discord.apiv1.queries.ApiQuery;
import link.locutus.discord.apiv1.queries.ApplicantsQuery;
import link.locutus.discord.apiv1.queries.BankQuery;
import link.locutus.discord.apiv1.queries.CityQuery;
import link.locutus.discord.apiv1.queries.MembersQuery;
import link.locutus.discord.apiv1.queries.NationMilitaryQuery;
import link.locutus.discord.apiv1.queries.NationQuery;
import link.locutus.discord.apiv1.queries.NationsQuery;
import link.locutus.discord.apiv1.queries.TradehistoryQuery;
import link.locutus.discord.apiv1.queries.TradepriceQuery;
import link.locutus.discord.apiv1.queries.WarAttacksQuery;
import link.locutus.discord.apiv1.queries.WarQuery;
import link.locutus.discord.apiv1.queries.WarsQuery;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PoliticsAndWarV2 implements IPoliticsAndWar {
    private final String baseUrl;
    private final Gson gson;
    private final JsonParser parser;
    private final ApiKeyPool pool;
    private final QueryExecutor legacyV1;
    private final PoliticsAndWarV3 v3;

    public PoliticsAndWarV2(String key, boolean test, boolean cache) {
        this(new ApiKeyPool(Collections.singleton(key)), test, cache);
    }

    public PoliticsAndWarV2(ApiKeyPool pool, boolean test, boolean cache) {
        this.pool = pool;
        this.baseUrl = "https://" + (test ? "test." : "") + "politicsandwar.com/api/v2/";
        this.gson = new Gson();
        this.parser = new JsonParser();
        this.legacyV1 = new QueryExecutor(cache, test, 50, 60000);
        this.v3 = new PoliticsAndWarV3(pool);
    }

    public PoliticsAndWarV3 getV3() {
        return v3;
    }

    public Gson getGson() {
        return gson;
    }

    public <T> T get(QueryURLV2 url, Type typeOf) {
        return get(url, typeOf, null, null);
    }

    public <T> T get(QueryURLV2 url, Type typeOf, String arg) {
        return get(url, typeOf, arg, null);
    }

    public <T> T get(QueryURLV2 url, Type typeOf, String arg, String query) {
        String json = read(url, arg, query, true);
        return gson.fromJson(json, typeOf);
    }

    public JsonElement getJson(QueryURLV2 url) {
        return getJson(url, null);
    }

    public JsonElement getJson(QueryURLV2 url, String arg) {
        return getJson(url, arg, null);
    }

    public JsonElement getJson(QueryURLV2 url, String arg, String query) {
        return getJson(url, arg, query, true);
    }

    public JsonElement getJson(QueryURLV2 url, String arg, String query, boolean removeHeader) {
        String json = read(url, arg, query, removeHeader);
        return parser.parse(json);
    }

    public String read(QueryURLV2 url, String arg, String query, boolean removeHeader) {
        return runWithKey(apiKey -> {
            try {
                String urlStr = baseUrl + url.getUrl(apiKey, arg, query);
                String json = FileUtil.readStringFromURL(urlStr);

                if (removeHeader) {
                    String successStr = "success\":";
                    int successIndex = json.indexOf(successStr);
                    if (successIndex == -1) {
                        throw new IOException("Invalid response: " + json + " for " + url.getUrl("XXXX", arg, query));
                    }
                    char tf = json.charAt(successIndex + successStr.length());
                    if (tf != 't') {
                        throw new IOException("Failed: " + json + " for " + url.getUrl("XXXX", arg, query));
                    }

                    String startStr = "\"data\":";
                    int start = json.indexOf(startStr, successIndex + successStr.length());
                    json = json.substring(start + startStr.length(), json.length() - 1);
                }
                return json;
            } catch (Throwable e) {
                e.printStackTrace();
                String message = e.getMessage();
                message = message.toLowerCase().replace(apiKey.toLowerCase(), "<key>");
                throw new IOException(message);
            }
        });
    }

    public ApiRecord getApiRecord() {
        String json = read(QueryURLV2.BANK_RECORDS, Settings.INSTANCE.NATION_ID + "", null, false);
        Type type = new TypeToken<ApiRecord>() {}.getType();
        return getGson().fromJson(json, type);
    }

    public List<BankRecord> getBankRecords(int nationId) {
        try {
            Type type = new TypeToken<List<BankRecord>>() {
            }.getType();
            return get(QueryURLV2.BANK_RECORDS, type, nationId + "", null);
        } catch (Throwable e) {
            if (e.getMessage().toLowerCase().contains("error_msg\":\"no results to display.")) {
                return new ArrayList<>();
            }
            throw e;
        }
    }

    private <T> T runWithKey(ThrowingFunction<String, T> task) {
        incrementUsage(new Exception().getStackTrace());
        while (true) {
            String apiKey = pool.getNextApiKey();
            try {
                return task.applyThrows(apiKey);
            } catch (Throwable e) {
                if (e.getMessage().toLowerCase(Locale.ROOT).contains("the api key sent for this request is invalid")) {
                    pool.removeKey(apiKey);
                    AlertUtil.error("Invalid key", e);
                    continue;
                }
                if (e.getMessage().toLowerCase(Locale.ROOT).contains("exceeded max request limit of")) {
                    pool.removeKey(apiKey);
                    AlertUtil.error("Exceeded max request limit", e);
                    continue;
                }
                String msg = e.getMessage();
                msg = msg.replace(apiKey, "XXXX");
                String finalMsg = msg;
                throw new RuntimeException(e) {
                    @Override
                    public String getMessage() {
                        return finalMsg;
                    }
                };
            }
        }
    }

    Map<String, Set<List<StackTraceElement>>> methodToStacktrace = new ConcurrentHashMap<>();

    Map<String, AtomicInteger> methodToCount = new ConcurrentHashMap<>();

    private void incrementUsage(StackTraceElement[] stackTrace) {
        int clazzIndex = 0;
        for (int i = 0; i < stackTrace.length; i++) {
            StackTraceElement elem = stackTrace[i];
            if (elem.getClassName().contains("PoliticsAndWarV2")) {
                clazzIndex = i;
            }
        }
        StackTraceElement parent = stackTrace[clazzIndex + 1];
        String key = parent.getClassName() + "." + parent.getMethodName();
        methodToCount.computeIfAbsent(key, f -> new AtomicInteger()).incrementAndGet();

        Set<List<StackTraceElement>> methodTraces = methodToStacktrace.computeIfAbsent(key, f -> new LinkedHashSet<>());
        if (methodTraces.size() < 10) {
            List<StackTraceElement> trace = Arrays.asList(stackTrace);
            methodTraces.add(trace);
        }
    }

    public Map<String, AtomicInteger> getMethodUsageStats() {
        return methodToCount;
    }

    public Map<String, Set<List<StackTraceElement>>> getMethodToStacktrace() {
        return methodToStacktrace;
    }

    @Override
    public Nation getNation(int nationId) throws IOException {
        return runWithKey(key -> (Nation) execute(new NationQuery(nationId, key).build()));
    }

    @Override
    public Nations getNations() throws IOException {
        return runWithKey(key -> (Nations) execute(new NationsQuery(null, null, null, null, key).build()));
    }

    @Override
    public Nations getNations(boolean vm) throws IOException {
        return runWithKey(key -> (Nations) execute(new NationsQuery(vm, null, null, null, key).build()));
    }

    @Override
    public Nations getNationsByAlliance(boolean vm, int allianceId) throws IOException {
        return runWithKey(key -> (Nations) execute(new NationsQuery(vm, null, null, allianceId, key).build()));
    }

    @Override
    public Nations getNationsByScore(boolean vm, int maxScore, int minScore) throws IOException {
        return runWithKey(key -> (Nations) execute(new NationsQuery(vm, maxScore, minScore, null, key).build()));
    }

    @Override
    public Nations getNations(boolean vm, int allianceId, int maxScore, int minScore) throws IOException {
        return runWithKey(key -> (Nations) execute(new NationsQuery(vm, maxScore, minScore, allianceId, key).build()));
    }

    @Override
    public Alliance getAlliance(int allianceId) throws IOException {
        return runWithKey(key -> (Alliance) execute(new AllianceQuery(allianceId, key).build()));
    }

    @Override
    public AllianceMembers getAllianceMembers(int allianceId) throws IOException {
        AllianceMembers result = runWithKey(key -> (AllianceMembers) execute(new AllianceMembersQuery(allianceId, key).build()));
        DBAlliance.getOrCreate(allianceId).updateSpies(result);
        return result;
    }

    @Override
    public Alliances getAlliances() throws IOException {
        return runWithKey(key -> (Alliances) execute(new AlliancesQuery(key).build()));
    }

    @Override
    public NationMilitary getAllMilitaries() throws IOException {
        return runWithKey(key -> (NationMilitary) execute(new NationMilitaryQuery(key).build()));
    }

    @Override
    public AllCities getAllCities() throws IOException {
        return runWithKey(key -> (AllCities) execute(new AllCitiesQuery(key).build()));
    }

    @Override
    public Applicants getApplicants(int allianceId) throws IOException {
        return runWithKey(key -> (Applicants) execute(new ApplicantsQuery(allianceId, key).build()));
    }

    @Override
    public Bank getBank(int allianceId) throws IOException {
        return runWithKey(key -> (Bank) execute(new BankQuery(allianceId, key).build()));
    }

    @Override
    public Members getMembers(int allianceId) throws IOException {
        return runWithKey(key -> (Members) execute(new MembersQuery(allianceId, key).build()));
    }

    @Override
    public City getCity(int cityId) throws IOException {
        return runWithKey(key -> (City) execute(new CityQuery(cityId, key).build()));
    }

    @Override
    public War getWar(int warId) throws IOException {
        return runWithKey(key -> (War) execute(new WarQuery(warId, key).build()));
    }

    @Override
    public Wars getWars() throws IOException {
        return runWithKey(key -> (Wars) execute(new WarsQuery(-1, null, key).build()));
    }

    @Override
    public Wars getWarsByAmount(int amount) throws IOException {
        return runWithKey(key -> (Wars) execute(new WarsQuery(amount, null, key).build()));
    }

    @Override
    public Wars getWarsByAlliance(Integer... alliance_ids) throws IOException {
        return runWithKey(key -> (Wars) execute(new WarsQuery(-1, alliance_ids, key).build()));
    }

    @Override
    public Wars getWars(int amount, Integer... alliance_ids) throws IOException {
        return runWithKey(key -> (Wars) execute(new WarsQuery(amount, alliance_ids, key).build()));
    }

    @Override
    public TradePrice getTradeprice(ResourceType resource) throws IOException {
        return runWithKey(key -> (TradePrice) execute(new TradepriceQuery(resource, key).build()));
    }

    @Override
    public TradeHistory getAllTradehistory() throws IOException {
        return runWithKey(key -> (TradeHistory) execute(new TradehistoryQuery(null, null, key).build()));
    }

    @Override
    public TradeHistory getTradehistoryByType(ResourceType... resources) throws IOException {
        return runWithKey(key -> (TradeHistory) execute(new TradehistoryQuery(null, resources, key).build()));
    }

    @Override
    public TradeHistory getTradehistoryByAmount(Integer amount) throws IOException {
        return runWithKey(key -> (TradeHistory) execute(new TradehistoryQuery(amount, null, key).build()));
    }

    @Override
    public TradeHistory getTradehistory(Integer amount, ResourceType... resources) throws IOException {
        return runWithKey(key -> (TradeHistory) execute(new TradehistoryQuery(amount, resources, key).build()));
    }

    @Override
    public WarAttacks getWarAttacks() throws IOException {
        return runWithKey(key -> (WarAttacks) execute(new WarAttacksQuery(null, null, null, key).build()));
    }

    @Override
    public WarAttacks getWarAttacksByWarId(int warId) throws IOException {
        return runWithKey(key -> (WarAttacks) execute(new WarAttacksQuery(warId, null, null, key).build()));
    }

    @Override
    public WarAttacks getWarAttacksByMinWarAttackId(int minWarAttackId) throws IOException {
        return runWithKey(key -> (WarAttacks) execute(new WarAttacksQuery(null, minWarAttackId, null, key).build()));
    }

    public WarAttacks getWarAttacksByMinMaxWarAttackId(int min, int max) throws IOException {
        return runWithKey(key -> (WarAttacks) execute(new WarAttacksQuery(null, min, max, key).build()));
    }

    @Override
    public WarAttacks getWarAttacksByMaxWarAttackId(int maxWarAttackId) throws IOException {
        return runWithKey(key -> (WarAttacks) execute(new WarAttacksQuery(null, null, maxWarAttackId, key).build()));
    }

    @Override
    public WarAttacks getWarAttacks(int warId, int minWarAttackId, int maxWarAttackId) throws IOException {
        return runWithKey(key -> (WarAttacks) execute(new WarAttacksQuery(warId, minWarAttackId, maxWarAttackId, key).build()));
    }

    public Map<String, Integer> getApiKeyUsageStats() {
        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<String, AtomicInteger> entry : pool.getStats().entrySet()) {
            result.put(entry.getKey(), entry.getValue().intValue());
        }
        for (String key : pool.getKeys()) {
            result.putIfAbsent(key, 0);
        }
        return result;
    }

    public CacheClient getCacheClient() {
        return legacyV1.getCacheClient();
    }

    public void clearCache() {
        legacyV1.clearCacheClient();
    }

    private Entity execute(ApiQuery apiQuery) throws IOException {
        return legacyV1.execute(apiQuery);
    }
}
