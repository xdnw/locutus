package link.locutus.discord.apiv2;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.IPoliticsAndWar;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.core.CacheClient;
import link.locutus.discord.apiv1.core.QueryExecutor;
import link.locutus.discord.apiv1.domains.*;
import link.locutus.discord.apiv1.entities.ApiRecord;
import link.locutus.discord.apiv1.entities.BankRecord;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.queries.*;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.scheduler.ThrowingFunction;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PoliticsAndWarV2 implements IPoliticsAndWar {
    private final String baseUrl;
    private final Gson gson;
    private final JsonParser parser;
    private final ApiKeyPool pool;
    private final QueryExecutor legacyV1;
    Map<String, Set<List<StackTraceElement>>> methodToStacktrace = new ConcurrentHashMap<>();
    Map<String, AtomicInteger> methodToCount = new ConcurrentHashMap<>();

    @Deprecated
    public PoliticsAndWarV2(String key, boolean test, boolean cache) {
        this(ApiKeyPool.builder().addKeyUnsafe(key).build(), test, cache);
    }

    public PoliticsAndWarV2(ApiKeyPool pool, boolean test, boolean cache) {
        this.pool = pool;
        this.baseUrl = "https://" + (test ? "test." : "") + "politicsandwar.com/api/v2/";
        this.gson = new Gson();
        this.parser = new JsonParser();
        this.legacyV1 = new QueryExecutor(cache, test, 50, 60000);
    }

    public Gson getGson() {
        return gson;
    }

    public <T> T get(PagePriority priority, QueryURLV2 url, Type typeOf) {
        return get(priority, url, typeOf, null, null);
    }

    public <T> T get(PagePriority priority, QueryURLV2 url, Type typeOf, String arg) {
        return get(priority, url, typeOf, arg, null);
    }

    public <T> T get(PagePriority priority, QueryURLV2 url, Type typeOf, String arg, String query) {
        String json = read(priority, url, arg, query, true);
        return gson.fromJson(json, typeOf);
    }

    public JsonElement getJson(PagePriority priority, QueryURLV2 url) {
        return getJson(priority, url, null);
    }

    public JsonElement getJson(PagePriority priority, QueryURLV2 url, String arg) {
        return getJson(priority, url, arg, null);
    }

    public JsonElement getJson(PagePriority priority, QueryURLV2 url, String arg, String query) {
        return getJson(priority, url, arg, query, true);
    }

    public JsonElement getJson(PagePriority priority, QueryURLV2 url, String arg, String query, boolean removeHeader) {
        String json = read(priority, url, arg, query, removeHeader);
        return parser.parse(json);
    }

    public String read(PagePriority priority, QueryURLV2 url, String arg, String query, boolean removeHeader) {
        return runWithKey(apiKey -> {
            try {
                String urlStr = baseUrl + url.getUrl(apiKey, arg, query);
                String json = FileUtil.readStringFromURL(priority, urlStr);

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
                message = StringMan.stripApiKey(message.toLowerCase().replace(apiKey.toLowerCase(), "<key>"));
                throw new IOException(message);
            }
        });
    }

    public ApiRecord getApiRecord() {
        String json = read(PagePriority.API_KEY_STATS, QueryURLV2.BANK_RECORDS, Locutus.loader().getNationId() + "", null, false);
        Type type = new TypeToken<ApiRecord>() {
        }.getType();
        return getGson().fromJson(json, type);
    }

    public List<BankRecord> getBankRecords(int nationId, boolean priority) {
        try {
            Type type = new TypeToken<List<BankRecord>>() {
            }.getType();
            return get(priority ? PagePriority.API_BANK_RECS_MANUAL : PagePriority.API_BANK_RECS_AUTO, QueryURLV2.BANK_RECORDS, type, nationId + "", null);
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
            ApiKeyPool.ApiKey keyPair = pool.getNextApiKey();
            try {
                return task.applyThrows(keyPair.getKey());
            } catch (Throwable e) {
                if (e.getMessage().toLowerCase(Locale.ROOT).contains("the api key sent for this request is invalid")) {
                    keyPair.deleteApiKey();
                    pool.removeKey(keyPair);
                    AlertUtil.error("Invalid key", e);
                    continue;
                }
                if (e.getMessage().toLowerCase(Locale.ROOT).contains("exceeded max request limit of")) {
                    pool.removeKey(keyPair);
                    AlertUtil.error("Exceeded max request limit", e);
                    continue;
                }
                String msg = e.getMessage();
                msg = StringUtils.replaceIgnoreCase(msg, keyPair.getKey(), "XXXX");
                if (keyPair.getBotKey() != null) {
                    msg = StringUtils.replaceIgnoreCase(msg, keyPair.getBotKey(), "YYYY");
                }
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
    public Nations getNationsByAlliance(boolean vm, int allianceId) {
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
    public AllianceMembers getAllianceMembers(int allianceId) {
        AllianceMembers result = runWithKey(key -> (AllianceMembers) execute(new AllianceMembersQuery(allianceId, key).build()));
        DBAlliance.getOrCreate(allianceId).updateSpies(result);
        return result;
    }

    @Override
    public Alliances getAlliances() throws IOException {
        return runWithKey(key -> (Alliances) execute(new AlliancesQuery(key).build()));
    }

    @Override
    public NationMilitary getAllMilitaries() {
        return runWithKey(key -> (NationMilitary) execute(new NationMilitaryQuery(key).build()));
    }

    @Override
    public AllCities getAllCities() throws IOException {
        return runWithKey(key -> (AllCities) execute(new AllCitiesQuery(key).build()));
    }

    @Override
    public Applicants getApplicants(int allianceId) {
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
    public City getCity(int cityId) {
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
    public Wars getWarsByAmount(int amount) {
        return runWithKey(key -> (Wars) execute(new WarsQuery(amount, null, key).build()));
    }

    @Override
    public Wars getWarsByAlliance(Integer... alliance_ids) {
        return runWithKey(key -> (Wars) execute(new WarsQuery(-1, alliance_ids, key).build()));
    }

    @Override
    public Wars getWars(int amount, Integer... alliance_ids) throws IOException {
        return runWithKey(key -> (Wars) execute(new WarsQuery(amount, alliance_ids, key).build()));
    }

    @Override
    public TradePrice getTradeprice(ResourceType resource) {
        return runWithKey(key -> (TradePrice) execute(new TradepriceQuery(resource, key).build()));
    }

    @Override
    public TradeHistory getAllTradehistory() {
        return runWithKey(key -> (TradeHistory) execute(new TradehistoryQuery(null, null, key).build()));
    }

    @Override
    public TradeHistory getTradehistoryByType(ResourceType... resources) {
        return runWithKey(key -> (TradeHistory) execute(new TradehistoryQuery(null, resources, key).build()));
    }

    @Override
    public TradeHistory getTradehistoryByAmount(Integer amount) {
        return runWithKey(key -> (TradeHistory) execute(new TradehistoryQuery(amount, null, key).build()));
    }

    @Override
    public TradeHistory getTradehistory(Integer amount, ResourceType... resources) {
        return runWithKey(key -> (TradeHistory) execute(new TradehistoryQuery(amount, resources, key).build()));
    }

    @Override
    public WarAttacks getWarAttacks() {
        return runWithKey(key -> (WarAttacks) execute(new WarAttacksQuery(null, null, null, key).build()));
    }

    @Override
    public WarAttacks getWarAttacksByWarId(int warId) {
        return runWithKey(key -> (WarAttacks) execute(new WarAttacksQuery(warId, null, null, key).build()));
    }

    @Override
    public WarAttacks getWarAttacksByMinWarAttackId(int minWarAttackId) throws IOException {
        return runWithKey(key -> (WarAttacks) execute(new WarAttacksQuery(null, minWarAttackId, null, key).build()));
    }

    public WarAttacks getWarAttacksByMinMaxWarAttackId(int min, int max) {
        return runWithKey(key -> (WarAttacks) execute(new WarAttacksQuery(null, min, max, key).build()));
    }

    @Override
    public WarAttacks getWarAttacksByMaxWarAttackId(int maxWarAttackId) {
        return runWithKey(key -> (WarAttacks) execute(new WarAttacksQuery(null, null, maxWarAttackId, key).build()));
    }

    @Override
    public WarAttacks getWarAttacks(int warId, int minWarAttackId, int maxWarAttackId) {
        return runWithKey(key -> (WarAttacks) execute(new WarAttacksQuery(warId, minWarAttackId, maxWarAttackId, key).build()));
    }

    public Map<String, Integer> getApiKeyUsageStats() {
        Map<String, Integer> result = new HashMap<>();
        for (ApiKeyPool.ApiKey key : pool.getKeys()) {
            result.put(key.getKey().toLowerCase(Locale.ROOT), key.getUsage());
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
