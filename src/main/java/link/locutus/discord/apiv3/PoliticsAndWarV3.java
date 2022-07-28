package link.locutus.discord.apiv3;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonObject;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.PoliticsAndWarBuilder;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.domains.subdomains.SNationContainer;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv2.PoliticsAndWarV2;
import link.locutus.discord.apiv3.subscription.PnwPusherEvent;
import link.locutus.discord.apiv3.subscription.PnwPusherHandler;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.StringMan;
import com.kobylynskyi.graphql.codegen.model.graphql.*;
import com.politicsandwar.graphql.model.*;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import graphql.GraphQLException;
import org.springframework.http.*;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class PoliticsAndWarV3 {
    public static int NATIONS_PER_PAGE = 500;
    public static int CITIES_PER_PAGE = 500;
    public static int TREATIES_PER_PAGE = 1000;
    public static int BANKRECS_PER_PAGE = 1000;
    public static int ALLIANCES_PER_PAGE = 100;
    public static int ATTACKS_PER_PAGE = 1000;
    public static int WARS_PER_PAGE = 1000;
    public static int TRADES_PER_PAGE = 1000;
    public static int BOUNTIES_PER_PAGE = 1000;
    public static int BASEBALL_PER_PAGE = 1000;

    private final String endpoint;
    private final RestTemplate restTemplate;
    private final ApiKeyPool pool;

    private final ObjectMapper jacksonObjectMapper = Jackson2ObjectMapperBuilder.json().build();

    public PoliticsAndWarV3(String url, ApiKeyPool pool) {
        this.endpoint = url;
        this.restTemplate = new RestTemplate();
        this.pool = pool;
    }

    public PoliticsAndWarV3(ApiKeyPool pool) {
        this("https://api" + (Settings.INSTANCE.TEST ? "-test" : "") + ".politicsandwar.com/graphql", pool);
    }

    public PoliticsAndWarV3(String key) {
        this(new ApiKeyPool(key));
    }

    public String getUrl(String key) {
        return endpoint + "?api_key=" + key;
    }

    public enum ErrorResponse {
        CONTINUE,
        RETRY,
        EXIT,
        THROW
    }

    private static class RateLimit {
        public int limit;
        public int intervalMs;
        public long resetAfterMs;
        public int remaining;
        public long resetMs;

        public void reset(long now) {
            if (now > resetMs) {
                remaining = limit;

                long remainder = (now - resetMs) % intervalMs;

                resetAfterMs = intervalMs - remainder;
                resetMs = now + resetAfterMs;
            }
        }
    }

    private static final RateLimit rateLimitGlobal = new RateLimit();

    public <T> T readTemplate(GraphQLRequest graphQLRequest, Class<T> resultBody) {
        if (rateLimitGlobal.intervalMs != 0) {
            synchronized (rateLimitGlobal) {
                long now = System.currentTimeMillis();
                rateLimitGlobal.reset(now);

                if (rateLimitGlobal.remaining <= 0) {
                    long sleepMs = rateLimitGlobal.resetMs - now;
                    if (sleepMs > 0) {
                        try {
                            Thread.sleep(sleepMs + 1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                rateLimitGlobal.reset(System.currentTimeMillis());
                rateLimitGlobal.remaining--;
            }
        }

        ResponseEntity<String> exchange = null;
        T result = null;

        int badKey = 0;
        int backOff = 0;
        while (true) {
            String key = pool.getNextApiKey();
            String url = getUrl(key);
            try {
                System.out.println(graphQLRequest.toHttpJsonBody());

                restTemplate.acceptHeaderRequestCallback(String.class);
//
                HttpEntity<String> entity = httpEntity(graphQLRequest, key);

                exchange = restTemplate.exchange(URI.create(url),
                        HttpMethod.POST,
                        entity,
                        String.class);

                String body = exchange.getBody();
                JsonNode json = (ObjectNode) jacksonObjectMapper.readTree(body);

                if (json.has("errors")) {
                    JsonNode errors = (JsonNode) json.get("errors");
                    List<String> errorMessages = new ArrayList<>();
                    for (JsonNode error : errors) {
                        if (error.has("message")) {
                            errorMessages.add(error.get("message").toString());
                        }
                    }
                    String message = errorMessages.isEmpty() ? errors.toString() : StringMan.join(errorMessages, "\n");
                    throw new IllegalArgumentException(message.replace(key, "XXX"));
                }

                result = jacksonObjectMapper.readValue(body, resultBody);
                break;
            } catch (HttpClientErrorException.TooManyRequests e) {
                try {
                    Thread.sleep((long) (60000 * Math.pow(2, backOff)));
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                backOff++;
            } catch (HttpClientErrorException.Unauthorized e) {
                if (badKey++ > 4) {
                    e.printStackTrace();
                    AlertUtil.error(e.getMessage(), e);
                    rethrow(e, key, false);
                    throw e;
                }
                pool.removeKey(key);
            } catch (HttpClientErrorException e) {
                e.printStackTrace();
                AlertUtil.error(e.getMessage(), e);
                rethrow(e, key, false);
                throw e;
            } catch (JsonProcessingException e) {
                AlertUtil.error(e.getMessage(), e);
                rethrow(e, key, true);
            } catch (Throwable e) {
                rethrow(e, key, false);
                throw e;
            }
        }
        HttpHeaders header = exchange.getHeaders();
        synchronized (rateLimitGlobal) {
            if (header.containsKey("X-RateLimit-Reset-After")) {
                rateLimitGlobal.resetAfterMs = Long.parseLong(header.get("X-RateLimit-Reset-After").get(0)) * 1000L;
            }
            if (header.containsKey("X-RateLimit-Limit")) {
                rateLimitGlobal.limit = Integer.parseInt(header.get("X-RateLimit-Limit").get(0));
            }
            if (header.containsKey("X-RateLimit-Remaining")) {
                rateLimitGlobal.remaining = Integer.parseInt(header.get("X-RateLimit-Remaining").get(0));
            }
            if (header.containsKey("X-RateLimit-Reset")) {
                rateLimitGlobal.resetMs = Long.parseLong(header.get("X-RateLimit-Reset").get(0)) * 1000L;
            }
            if (header.containsKey("X-RateLimit-Interval")) {
                rateLimitGlobal.intervalMs = Integer.parseInt(header.get("X-RateLimit-Interval").get(0)) * 1000;
            }
        }
        return result;
    }

    private <T extends Throwable> void rethrow(T e, String key, boolean throwRuntime) {
        if (e.getMessage() != null && e.getMessage().contains(key)) {
            throw new RuntimeException(e.getMessage().replace(key, "XXX"));
        }
        if (throwRuntime) throw new RuntimeException(e.getMessage());
    }

    public <T extends GraphQLResult<?>> void handlePagination(Function<Integer, GraphQLRequest> requestFactory, Function<GraphQLError, ErrorResponse> errorBehavior, Class<T> resultBody, Predicate<T> hasMorePages, Consumer<T> onEachResult) {
        pageLoop:
        for (int page = 1; ; page++) {
            GraphQLRequest graphQLRequest = requestFactory.apply(page);

            errorLoop:
            for (int i = 0; i < 5; i++) {
                T result = readTemplate(graphQLRequest, resultBody);

                boolean iterateNext = result != null && hasMorePages.test(result);

                if (result == null) {
                    break pageLoop;
                }
                if (result.hasErrors()) {
                    System.out.println("Has error");
                    int maxBehavior = 0;
                    List<GraphQLError> errors = result.getErrors();
                    for (GraphQLError error : errors) {
                        maxBehavior = Math.max(errorBehavior.apply(error).ordinal(), maxBehavior);
                    }
                    switch (ErrorResponse.values()[maxBehavior]) {
                        case CONTINUE:
                            if (!iterateNext) {
                                break pageLoop;
                            }
                            continue pageLoop;
                        case RETRY:
                            try {
                                Thread.sleep((long) (1000 + Math.pow(i * 1000, 2)));
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            continue errorLoop;
                        case EXIT:
                            break pageLoop;
                        case THROW:
                            RuntimeException e = new RuntimeException(StringMan.join(errors, "\n"));
                            e.printStackTrace();
                            throw e;
                    }
                }

                onEachResult.accept(result);
                if (!iterateNext) {
                    break pageLoop;
                }
                break;
            }
        }
    }

    public List<Bounty> fetchBounties(Consumer<BountiesQueryRequest> filter, Consumer<BountyResponseProjection> query) {
        return fetchBounties(1000, filter, query, f -> ErrorResponse.THROW, f -> true);
    }

    public List<Bounty> fetchBounties(int perPage, Consumer<BountiesQueryRequest> filter, Consumer<BountyResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<Bounty> recResults) {
        List<Bounty> allResults = new ArrayList<>();

        handlePagination(page -> {
                    BountiesQueryRequest request = new BountiesQueryRequest();
                    if (filter != null) filter.accept(request);
                    request.setFirst(perPage);
                    request.setPage(page);

                    BountyResponseProjection respProj = new BountyResponseProjection();
                    query.accept(respProj);

                    BountyPaginatorResponseProjection pagRespProj = new BountyPaginatorResponseProjection()
                            .paginatorInfo(new PaginatorInfoResponseProjection()
                                    .hasMorePages())
                            .data(respProj);

                    return new GraphQLRequest(request, pagRespProj);
                }, errorBehavior, BountiesQueryResponse.class,
                response -> {
                    BountyPaginator paginator = response.bounties();
                    PaginatorInfo pageInfo = paginator != null ? paginator.getPaginatorInfo() : null;
                    return pageInfo != null && pageInfo.getHasMorePages();
                }, result -> {
                    BountyPaginator paginator = result.bounties();
                    if (paginator != null) {
                        List<Bounty> txs = paginator.getData();
                        for (Bounty tx : txs) {
                            if (recResults.test(tx)) allResults.add(tx);
                        }
                    }
                });

        return allResults;
    }

    public List<BBGame> fetchBaseballGames(Consumer<Baseball_gamesQueryRequest> filter, Consumer<BBGameResponseProjection> query) {
        return fetchBaseballGames(BOUNTIES_PER_PAGE, filter, query, f -> ErrorResponse.THROW, f -> true);
    }

    public List<BBGame> fetchBaseballGames(int perPage, Consumer<Baseball_gamesQueryRequest> filter, Consumer<BBGameResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<BBGame> recResults) {
        List<BBGame> allResults = new ArrayList<>();

        handlePagination(page -> {
                    Baseball_gamesQueryRequest request = new Baseball_gamesQueryRequest();
                    if (filter != null) filter.accept(request);
                    request.setFirst(perPage);
                    request.setPage(page);

                    BBGameResponseProjection respProj = new BBGameResponseProjection();
                    query.accept(respProj);

                    BBGamePaginatorResponseProjection pagRespProj = new BBGamePaginatorResponseProjection()
                            .paginatorInfo(new PaginatorInfoResponseProjection()
                                    .hasMorePages())
                            .data(respProj);

                    return new GraphQLRequest(request, pagRespProj);
                }, errorBehavior, Baseball_gamesQueryResponse.class,
                response -> {
                    BBGamePaginator paginator = response.baseball_games();
                    PaginatorInfo pageInfo = paginator != null ? paginator.getPaginatorInfo() : null;
                    return pageInfo != null && pageInfo.getHasMorePages();
                }, result -> {
                    BBGamePaginator paginator = result.baseball_games();
                    if (paginator != null) {
                        List<BBGame> txs = paginator.getData();
                        for (BBGame tx : txs) {
                            if (recResults.test(tx)) allResults.add(tx);
                        }
                    }
                });

        return allResults;
    }

    public List<WarAttack> fetchAttacksSince(Integer maxId, Predicate<WarAttack> attackPredicate) {
        return fetchAttacks(ATTACKS_PER_PAGE, r -> {
            if (maxId != null) r.setMin_id(maxId + 1);
            QueryWarattacksOrderByOrderByClause order = QueryWarattacksOrderByOrderByClause.builder()
                    .setColumn(QueryWarattacksOrderByColumn.ID)
                    .setOrder(SortOrder.ASC)
                    .build();
            r.setOrderBy(List.of(order));
        }, warAttackInfo(), f -> ErrorResponse.EXIT, attackPredicate);
    }

    public Consumer<WarAttackResponseProjection> warAttackInfo() {
        return p -> {
            p.id();
            p.date();
            p.war_id();
            p.att_id();
            p.def_id();
            p.type();
            p.victor();
            p.success();
            p.attcas1();
            p.attcas2();
            p.defcas1();
            p.defcas2();
            p.aircraft_killed_by_tanks();
            p.infra_destroyed();
            p.improvements_lost();
            p.money_stolen();
            p.loot_info();
            p.city_infra_before();
            p.infra_destroyed_value();
            p.att_gas_used();
            p.att_mun_used();
            p.def_gas_used();
            p.def_mun_used();

            p.city_id();
        };
    }

    public List<WarAttack> fetchAttacks(Consumer<WarattacksQueryRequest> filter, ErrorResponse errorResponse) {
        return fetchAttacks(ATTACKS_PER_PAGE, filter, warAttackInfo(), f -> errorResponse, f -> true);
    }

    public List<WarAttack> fetchAttacks(Consumer<WarattacksQueryRequest> filter, Consumer<WarAttackResponseProjection> query) {
        return fetchAttacks(ATTACKS_PER_PAGE, filter, query, f -> ErrorResponse.THROW, f -> true);
    }

    public List<WarAttack> fetchAttacks(int perPage, Consumer<WarattacksQueryRequest> filter, Consumer<WarAttackResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<WarAttack> recResults) {
        List<WarAttack> allResults = new ArrayList<>();

        handlePagination(page -> {
                    WarattacksQueryRequest request = new WarattacksQueryRequest();
                    if (filter != null) filter.accept(request);
                    request.setFirst(perPage);
                    request.setPage(page);

                    WarAttackResponseProjection respProj = new WarAttackResponseProjection();
                    query.accept(respProj);

                    WarAttackPaginatorResponseProjection pagRespProj = new WarAttackPaginatorResponseProjection()
                            .paginatorInfo(new PaginatorInfoResponseProjection()
                                    .hasMorePages())
                            .data(respProj);

                    return new GraphQLRequest(request, pagRespProj);
                }, errorBehavior, WarattacksQueryResponse.class,
                response -> {
                    WarAttackPaginator paginator = response.warattacks();
                    PaginatorInfo pageInfo = paginator != null ? paginator.getPaginatorInfo() : null;
                    return pageInfo != null && pageInfo.getHasMorePages();
                }, result -> {
                    WarAttackPaginator paginator = result.warattacks();
                    if (paginator != null) {
                        List<WarAttack> txs = paginator.getData();
                        for (WarAttack tx : txs) {
                            if (recResults.test(tx)) allResults.add(tx);
                        }
                    }
                });

        return allResults;
    }

    public List<War> fetchWarsWithInfo(Consumer<WarsQueryRequest> filter) {
        return fetchWars(WARS_PER_PAGE, filter, new Consumer<WarResponseProjection>() {
            @Override
            public void accept(WarResponseProjection p) {
                p.id();
                p.att_id();
                p.def_id();
                p.att_alliance_id();
                p.def_alliance_id();
                p.war_type();
                p.att_peace();
                p.def_peace();
                p.winner_id();
                p.date();
            }
        }, f -> ErrorResponse.THROW, f -> true);
    }

    public List<War> fetchWars(Consumer<WarsQueryRequest> filter, Consumer<WarResponseProjection> query) {
        return fetchWars(WARS_PER_PAGE, filter, query, f -> ErrorResponse.THROW, f -> true);
    }

    public List<War> fetchWars(int perPage, Consumer<WarsQueryRequest> filter, Consumer<WarResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<War> recResults) {
        List<War> allResults = new ArrayList<>();

        handlePagination(page -> {
                    WarsQueryRequest request = new WarsQueryRequest();
                    if (filter != null) filter.accept(request);
                    request.setFirst(perPage);
                    request.setPage(page);

                    WarResponseProjection respProj = new WarResponseProjection();
                    query.accept(respProj);

                    WarPaginatorResponseProjection pagRespProj = new WarPaginatorResponseProjection()
                            .paginatorInfo(new PaginatorInfoResponseProjection()
                                    .hasMorePages())
                            .data(respProj);

                    return new GraphQLRequest(request, pagRespProj);
                }, errorBehavior, WarsQueryResponse.class,
                response -> {
                    System.out.println("Fetch page");
                    WarPaginator paginator = response.wars();
                    PaginatorInfo pageInfo = paginator != null ? paginator.getPaginatorInfo() : null;
                    return pageInfo != null && pageInfo.getHasMorePages();
                }, result -> {
                    WarPaginator paginator = result.wars();
                    if (paginator != null) {
                        List<War> txs = paginator.getData();
                        for (War tx : txs) {
                            if (recResults.test(tx)) allResults.add(tx);
                        }
                    }
                });

        return allResults;
    }

    public List<City> fetchCitiesWithInfo(Consumer<CitiesQueryRequest> filter, boolean cityInfo) {
        return fetchCities(filter, new Consumer<CityResponseProjection>() {
            @Override
            public void accept(CityResponseProjection proj) {
                proj.nation_id();
                proj.id();
                proj.infrastructure();

                if (cityInfo) {
                    proj.date();
                    proj.land();
                    proj.powered();

                    proj.oil_power();
                    proj.wind_power();
                    proj.coal_power();
                    proj.nuclear_power();
                    proj.coal_mine();
                    proj.lead_mine();
                    proj.iron_mine();
                    proj.bauxite_mine();
                    proj.oil_well();
                    proj.uranium_mine();
                    proj.farm();
                    proj.police_station();
                    proj.hospital();
                    proj.recycling_center();
                    proj.subway();
                    proj.supermarket();
                    proj.bank();
                    proj.shopping_mall();
                    proj.stadium();
                    proj.oil_refinery();
                    proj.aluminum_refinery();
                    proj.steel_mill();
                    proj.munitions_factory();
                    proj.barracks();
                    proj.factory();
                    proj.hangar();
                    proj.drydock();
                }
            }
        });
    }

    public List<City> fetchCities(Consumer<CitiesQueryRequest> filter, Consumer<CityResponseProjection> query) {
        return fetchCities(CITIES_PER_PAGE, filter, query, f -> ErrorResponse.THROW, f -> true);
    }

    public List<City> fetchCities(int perPage, Consumer<CitiesQueryRequest> filter, Consumer<CityResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<City> recResults) {
        List<City> allResults = new ArrayList<>();

        handlePagination(page -> {
                    CitiesQueryRequest request = new CitiesQueryRequest();
                    if (filter != null) filter.accept(request);
                    request.setFirst(perPage);
                    request.setPage(page);

                    CityResponseProjection natRespProj = new CityResponseProjection();
                    query.accept(natRespProj);

                    CityPaginatorResponseProjection natPagRespProj = new CityPaginatorResponseProjection()
                            .paginatorInfo(new PaginatorInfoResponseProjection()
                                    .hasMorePages())
                            .data(natRespProj);

                    return new GraphQLRequest(request, natPagRespProj);
                }, errorBehavior, CitiesQueryResponse.class,
                response -> {
                    CityPaginator paginator = response.cities();
                    PaginatorInfo pageInfo = paginator != null ? paginator.getPaginatorInfo() : null;
                    return pageInfo != null && pageInfo.getHasMorePages();
                }, result -> {
                    CityPaginator paginator = result.cities();
                    if (paginator != null) {
                        List<City> cities = paginator.getData();
                        for (City city : cities) {
                            if (recResults.test(city)) allResults.add(city);
                        }
                    }
                });

        return allResults;
    }

    public List<Bankrec> fetchBankRecsWithInfo(Consumer<BankrecsQueryRequest> filter) {
        return fetchBankRecs(filter, new Consumer<BankrecResponseProjection>() {
            @Override
            public void accept(BankrecResponseProjection proj) {
                proj.id();
                proj.date();
                proj.sender_id();
                proj.sender_type();
                proj.receiver_id();
                proj.receiver_type();
                proj.banker_id();
                proj.note();
                proj.money();
                proj.coal();
                proj.oil();
                proj.uranium();
                proj.iron();
                proj.bauxite();
                proj.lead();
                proj.gasoline();
                proj.munitions();
                proj.steel();
                proj.aluminum();
                proj.food();
            }
        });
    }

    public List<Bankrec> fetchBankRecs(Consumer<BankrecsQueryRequest> filter, Consumer<BankrecResponseProjection> query) {
        return fetchBankRecs(BANKRECS_PER_PAGE, filter, query, f -> ErrorResponse.THROW, f -> true);
    }

    public List<Bankrec> fetchBankRecs(int perPage, Consumer<BankrecsQueryRequest> filter, Consumer<BankrecResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<Bankrec> recResults) {
        List<Bankrec> allResults = new ArrayList<>();

        handlePagination(page -> {
                    BankrecsQueryRequest request = new BankrecsQueryRequest();
                    if (filter != null) filter.accept(request);
                    request.setFirst(perPage);
                    request.setPage(page);

                    BankrecResponseProjection natRespProj = new BankrecResponseProjection();
                    query.accept(natRespProj);

                    BankrecPaginatorResponseProjection natPagRespProj = new BankrecPaginatorResponseProjection()
                            .paginatorInfo(new PaginatorInfoResponseProjection()
                                    .hasMorePages())
                            .data(natRespProj);

                    return new GraphQLRequest(request, natPagRespProj);
                }, errorBehavior, BankrecsQueryResponse.class,
                response -> {
                    BankrecPaginator paginator = response.bankrecs();
                    PaginatorInfo pageInfo = paginator != null ? paginator.getPaginatorInfo() : null;
                    return pageInfo != null && pageInfo.getHasMorePages();
                }, result -> {
                    BankrecPaginator paginator = result.bankrecs();
                    if (paginator != null) {
                        List<Bankrec> txs = paginator.getData();
                        for (Bankrec tx : txs) {
                            if (recResults.test(tx)) allResults.add(tx);
                        }
                    }
                });

        return allResults;
    }

    public List<Bankrec> fetchTaxRecsWithInfo(int allianceId, Long afterDate) {
        List<Alliance> alliances = fetchAlliances(f -> f.setId(List.of(allianceId)), new Consumer<AllianceResponseProjection>() {
            @Override
            public void accept(AllianceResponseProjection projection) {
                AllianceTaxrecsParametrizedInput filter = new AllianceTaxrecsParametrizedInput();
                if (afterDate != null) filter.after(new Date(afterDate));

                BankrecResponseProjection taxProj = new BankrecResponseProjection();
                taxProj.id();
                taxProj.tax_id();
                taxProj.date();
                taxProj.sender_id();
                taxProj.sender_type();
                taxProj.receiver_id();
                taxProj.receiver_type();
                taxProj.banker_id();
                taxProj.note();
                taxProj.money();
                taxProj.coal();
                taxProj.oil();
                taxProj.uranium();
                taxProj.iron();
                taxProj.bauxite();
                taxProj.lead();
                taxProj.gasoline();
                taxProj.munitions();
                taxProj.steel();
                taxProj.aluminum();
                taxProj.food();

                projection.taxrecs(filter, taxProj);
            }
        });
        if (alliances != null && alliances.size() == 1) return alliances.get(0).getTaxrecs();
        return null;
    }

//
//    public List<Bankrec> fetchBankRecs2(int perPage, Consumer<AlliancesQueryRequest> filter, Consumer<BankrecResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<Bankrec> recResults) {
//        List<Bankrec> allResults = new ArrayList<>();
//
//        handlePagination(page -> {
//                    AlliancesQueryRequest request = new AlliancesQueryRequest();
//                    request.setId(Collections.singletonList(9821));
//                    request.setFirst(perPage);
//                    request.setPage(page);
//
//                    AllianceResponseProjection natRespProj = new AllianceResponseProjection();
//                    BankrecsQueryRequest request = new BankrecsQueryRequest();
//
//
//
//                    request.setFirst(perPage);
//                    request.setPage(page);
//
//                    BankrecResponseProjection natRespProj = new BankrecResponseProjection();
//                    query.accept(natRespProj);
//
//                    BankrecPaginatorResponseProjection natPagRespProj = new BankrecPaginatorResponseProjection()
//                            .paginatorInfo(new PaginatorInfoResponseProjection()
//                                    .hasMorePages())
//                            .data(natRespProj);
//
//                    return new GraphQLRequest(request, natPagRespProj);
//                }, errorBehavior, BankrecsQueryResponse.class,
//                response -> {
//                    BankrecPaginator paginator = response.bankrecs();
//                    PaginatorInfo pageInfo = paginator != null ? paginator.getPaginatorInfo() : null;
//                    return pageInfo != null && pageInfo.getHasMorePages();
//                }, result -> {
//                    BankrecPaginator paginator = result.bankrecs();
//                    if (paginator != null) {
//                        List<Bankrec> txs = paginator.getData();
//                        for (Bankrec tx : txs) {
//                            if (recResults.test(tx)) allResults.add(tx);
//                        }
//                    }
//                });
//
//        return allResults;
//    }

    public List<Nation> fetchNationsWithInfo(Consumer<NationsQueryRequest> filter, Predicate<Nation> nationResults) {
        return fetchNations(NATIONS_PER_PAGE, filter, new Consumer<NationResponseProjection>() {
            @Override
            public void accept(NationResponseProjection projection) {
                projection.id();

                projection.alliance_id();

                projection.nation_name(); // m
                projection.leader_name(); // m

                projection.last_active();
                projection.score();
                projection.num_cities(); // TODO remove
                projection.domestic_policy();
                projection.war_policy();
                projection.soldiers();
                projection.tanks();
                projection.aircraft();
                projection.ships();
                projection.missiles();
                projection.nukes();

                projection.vacation_mode_turns(); // m

                projection.color();
                projection.date(); // m

                projection.alliance_position();
                projection.alliance_position_id();

                projection.continent();

                projection.project_bits();

                projection.turns_since_last_project(); //
                projection.turns_since_last_city(); //
                projection.beige_turns();

                projection.espionage_available();
            }
        }, f -> PoliticsAndWarV3.ErrorResponse.THROW, nationResults);
    }

    public List<Nation> fetchNations(Consumer<NationsQueryRequest> filter, Consumer<NationResponseProjection> query) {
        return fetchNations(NATIONS_PER_PAGE, filter, query, f -> ErrorResponse.THROW, f -> true);
    }
    public List<Nation> fetchNations(int perPage, Consumer<NationsQueryRequest> filter, Consumer<NationResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<Nation> nationResults) {
        List<Nation> allResults = new ArrayList<>();

        handlePagination(page -> {
            NationsQueryRequest request = new NationsQueryRequest();
            if (filter != null) filter.accept(request);
            request.setFirst(perPage);
            request.setPage(page);

            NationResponseProjection natRespProj = new NationResponseProjection();
            query.accept(natRespProj);

            NationPaginatorResponseProjection natPagRespProj = new NationPaginatorResponseProjection()
                    .paginatorInfo(new PaginatorInfoResponseProjection()
                            .hasMorePages())
                    .data(natRespProj);

            return new GraphQLRequest(request, natPagRespProj);
        }, errorBehavior, NationsQueryResponse.class,
        response -> {
            NationPaginator paginator = response.nations();
            PaginatorInfo pageInfo = paginator != null ? paginator.getPaginatorInfo() : null;
            return pageInfo != null && pageInfo.getHasMorePages();
        }, result -> {
            NationPaginator paginator = result.nations();
            if (paginator != null) {
                List<Nation> nations = paginator.getData();
                for (Nation nation : nations) {
                    if (nationResults.test(nation)) allResults.add(nation);
                }
            }
        });

        return allResults;
    }

    public List<Alliance> fetchAlliances(Consumer<AlliancesQueryRequest> filter, boolean positions, boolean allianceInfo) {
        return fetchAlliances(ALLIANCES_PER_PAGE, filter, new Consumer<AllianceResponseProjection>() {
            @Override
            public void accept(AllianceResponseProjection projection) {
                projection.id();

                if (positions) {
                    AlliancePositionResponseProjection positionProj = new AlliancePositionResponseProjection();
                    positionProj.id();
                    positionProj.name();
                    positionProj.date();
                    positionProj.position_level();
                    positionProj.leader();
                    positionProj.heir();
                    positionProj.officer();
                    positionProj.member();
                    positionProj.permissions();

                    projection.alliance_positions(positionProj);
                }

                if (allianceInfo) {
                    projection.name();
                    projection.acronym();
                    projection.flag();
                    projection.forum_link();
                    projection.discord_link();
                    projection.wiki_link();
                    projection.date();
                    projection.color();
                }
            }
        }, f -> ErrorResponse.THROW, f -> true);
    }

    public List<Alliance> fetchAlliances(Consumer<AlliancesQueryRequest> filter, Consumer<AllianceResponseProjection> query) {
        return fetchAlliances(ALLIANCES_PER_PAGE, filter, query, f -> ErrorResponse.THROW, f -> true);
    }

    public List<Alliance> fetchAlliances(int perPage, Consumer<AlliancesQueryRequest> filter, Consumer<AllianceResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<Alliance> addEachResult) {
        List<Alliance> allResults = new ArrayList<>();

        handlePagination(page -> {
                    AlliancesQueryRequest request = new AlliancesQueryRequest();
                    if (filter != null) filter.accept(request);
                    request.setFirst(perPage);
                    request.setPage(page);

                    AllianceResponseProjection natRespProj = new AllianceResponseProjection();
                    query.accept(natRespProj);

                    AlliancePaginatorResponseProjection natPagRespProj = new AlliancePaginatorResponseProjection()
                            .paginatorInfo(new PaginatorInfoResponseProjection()
                                    .hasMorePages())
                            .data(natRespProj);

                    return new GraphQLRequest(request, natPagRespProj);
                }, errorBehavior, AlliancesQueryResponse.class,
                response -> {
                    AlliancePaginator paginator = response.alliances();
                    PaginatorInfo pageInfo = paginator != null ? paginator.getPaginatorInfo() : null;
                    return pageInfo != null && pageInfo.getHasMorePages();
                }, result -> {
                    AlliancePaginator paginator = result.alliances();
                    if (paginator != null) {
                        List<Alliance> alliances = paginator.getData();
                        for (Alliance alliance : alliances) {
                            if (addEachResult.test(alliance)) allResults.add(alliance);
                        }
                    }
                });

        return allResults;
    }

    public List<Treaty> fetchTreaties(Consumer<TreatiesQueryRequest> filter) {
        return fetchTreaties(filter, r -> {
            r.id();
            r.date();
            r.treaty_type();
            r.turns_left();
            r.alliance1_id();
            r.alliance2_id();
        });
    }

    public List<Treaty> fetchTreaties(Consumer<TreatiesQueryRequest> filter, Consumer<TreatyResponseProjection> query) {
        return fetchTreaties(TREATIES_PER_PAGE, filter, query, f -> ErrorResponse.THROW, f -> true);
    }

    public List<Treaty> fetchTreaties(int perPage, Consumer<TreatiesQueryRequest> filter, Consumer<TreatyResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<Treaty> addEachResult) {
        List<Treaty> allResults = new ArrayList<>();

        handlePagination(page -> {
                    TreatiesQueryRequest request = new TreatiesQueryRequest();
                    if (filter != null) filter.accept(request);
                    request.setFirst(perPage);
                    request.setPage(page);

                    TreatyResponseProjection natRespProj = new TreatyResponseProjection();
                    query.accept(natRespProj);

                    TreatyPaginatorResponseProjection natPagRespProj = new TreatyPaginatorResponseProjection()
                            .paginatorInfo(new PaginatorInfoResponseProjection()
                                    .hasMorePages())
                            .data(natRespProj);

                    return new GraphQLRequest(request, natPagRespProj);
                }, errorBehavior, TreatiesQueryResponse.class,
                response -> {
                    TreatyPaginator paginator = response.treaties();
                    PaginatorInfo pageInfo = paginator != null ? paginator.getPaginatorInfo() : null;
                    return pageInfo != null && pageInfo.getHasMorePages();
                }, result -> {
                    TreatyPaginator paginator = result.treaties();
                    if (paginator != null) {
                        List<Treaty> treaties = paginator.getData();
                        for (Treaty treaty : treaties) {
                            if (addEachResult.test(treaty)) allResults.add(treaty);
                        }
                    }
                });

        return allResults;
    }

    public <T> T request(GraphQLOperationRequest request, GraphQLResponseProjection response, Class<T> resultBody) {
        return readTemplate(new GraphQLRequest(request, response), resultBody);
    }

    public ApiKeyDetails getApiKeyStats() {
        MeQueryResponse result = request(new MeQueryRequest(), new ApiKeyDetailsResponseProjection()
                .key()
                .max_requests()
                .requests()
                .nation(new NationResponseProjection().id()),
                MeQueryResponse.class);
        if (result.me() == null) throw new GraphQLException("Error fetching api key " + result.toString());
        return result.me();
    }

    public static void testPusher() throws IOException, InterruptedException, ParseException {
        String key = Settings.INSTANCE.API_KEY_PRIMARY;
        String data = "[{\"id\":17362,\"alliance_id\":877,\"alliance_position\":2,\"alliance_position_id\":660,\"nation_name\":\"The Holy Britannian Empire\",\"leader_name\":\"Lelouch Vi Britannia\",\"continent\":\"na\",\"war_policy\":\"Fortress\",\"domestic_policy\":\"Urbanization\",\"color\":\"black\",\"num_cities\":23,\"score\":4590.5,\"update_tz\":null,\"population\":7369519,\"flag\":\"https://politicsandwar.com/img/imgur-old/917568acf2b88122f2e687d5bcfe41c8db855e95744.png\",\"vacation_mode_turns\":0,\"beige_turns\":0,\"espionage_available\":true,\"date\":\"2015-04-29 20:13:11\",\"soldiers\":321000,\"tanks\":28750,\"aircraft\":1725,\"ships\":0,\"missiles\":4,\"nukes\":3,\"spies\":null,\"discord\":\"\",\"turns_since_last_city\":104,\"turns_since_last_project\":186,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":12,\"iron_works\":1,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":0,\"mass_irrigation\":1,\"international_trade_center\":1,\"missile_launch_pad\":1,\"nuclear_research_facility\":1,\"iron_dome\":1,\"vital_defense_system\":0,\"central_intelligence_agency\":1,\"center_for_civil_engineering\":0,\"propaganda_bureau\":1,\"uranium_enrichment_program\":0,\"urban_planning\":1,\"advanced_urban_planning\":0,\"space_program\":1,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":1,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":1,\"resource_production_center\":0,\"wars_won\":41,\"wars_lost\":87,\"tax_id\":78,\"alliance_seniority\":562,\"gross_national_income\":10060706,\"gross_domestic_product\":6620064174,\"soldier_casualties\":11277621,\"soldier_kills\":15269908,\"tank_casualties\":388822,\"tank_kills\":492431,\"aircraft_casualties\":60580,\"aircraft_kills\":60964,\"ship_casualties\":3312,\"ship_kills\":4839,\"missile_casualties\":51,\"missile_kills\":27,\"nuke_casualties\":94,\"nuke_kills\":47,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":265661830.7,\"project_bits\":276911601},{\"id\":115525,\"alliance_id\":831,\"alliance_position\":2,\"alliance_position_id\":37,\"nation_name\":\"Cavendish\",\"leader_name\":\"ThommyZero\",\"continent\":\"au\",\"war_policy\":\"Covert\",\"domestic_policy\":\"Open Markets\",\"color\":\"green\",\"num_cities\":30,\"score\":4608.69,\"update_tz\":null,\"population\":9897534,\"flag\":\"https://politicsandwar.com/uploads/7230685ef2bfe02eb309b0c434568fc71a26e98589.jpg\",\"vacation_mode_turns\":0,\"beige_turns\":51,\"espionage_available\":true,\"date\":\"2018-04-25 01:49:28\",\"soldiers\":121596,\"tanks\":2143,\"aircraft\":0,\"ships\":232,\"missiles\":6,\"nukes\":0,\"spies\":null,\"discord\":\"ThommyZero?#8857\",\"turns_since_last_city\":410,\"turns_since_last_project\":2042,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":14,\"iron_works\":1,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":1,\"mass_irrigation\":1,\"international_trade_center\":1,\"missile_launch_pad\":1,\"nuclear_research_facility\":0,\"iron_dome\":1,\"vital_defense_system\":0,\"central_intelligence_agency\":1,\"center_for_civil_engineering\":1,\"propaganda_bureau\":1,\"uranium_enrichment_program\":0,\"urban_planning\":1,\"advanced_urban_planning\":1,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":1,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":1,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":1,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":0,\"wars_won\":76,\"wars_lost\":71,\"tax_id\":15758,\"alliance_seniority\":442,\"gross_national_income\":18602661,\"gross_domestic_product\":9522394599,\"soldier_casualties\":5368603,\"soldier_kills\":4017599,\"tank_casualties\":189554,\"tank_kills\":158966,\"aircraft_casualties\":18587,\"aircraft_kills\":13741,\"ship_casualties\":1654,\"ship_kills\":929,\"missile_casualties\":57,\"missile_kills\":5,\"nuke_casualties\":0,\"nuke_kills\":11,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":134261144.71,\"project_bits\":76602745},{\"id\":117574,\"alliance_id\":4829,\"alliance_position\":2,\"alliance_position_id\":260,\"nation_name\":\"Umbara\",\"leader_name\":\"Mohammad Khatta\",\"continent\":\"as\",\"war_policy\":\"Covert\",\"domestic_policy\":\"Open Markets\",\"color\":\"gray\",\"num_cities\":21,\"score\":3226,\"update_tz\":null,\"population\":5931419,\"flag\":\"https://politicsandwar.com/uploads/fefe524d9c1d18987fb8664d3bf6ab4ef63edc3a395.png\",\"vacation_mode_turns\":0,\"beige_turns\":0,\"espionage_available\":false,\"date\":\"2018-05-04 06:25:51\",\"soldiers\":74858,\"tanks\":0,\"aircraft\":580,\"ships\":273,\"missiles\":3,\"nukes\":0,\"spies\":null,\"discord\":\"Vietnationmn (Umbara)#6690\",\"turns_since_last_city\":696,\"turns_since_last_project\":1603,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":11,\"iron_works\":0,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":1,\"mass_irrigation\":1,\"international_trade_center\":0,\"missile_launch_pad\":1,\"nuclear_research_facility\":0,\"iron_dome\":1,\"vital_defense_system\":0,\"central_intelligence_agency\":1,\"center_for_civil_engineering\":1,\"propaganda_bureau\":1,\"uranium_enrichment_program\":0,\"urban_planning\":1,\"advanced_urban_planning\":1,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":1,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":1,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":0,\"wars_won\":177,\"wars_lost\":53,\"tax_id\":15898,\"alliance_seniority\":481,\"gross_national_income\":7659593,\"gross_domestic_product\":4744332017,\"soldier_casualties\":9788869,\"soldier_kills\":8812768,\"tank_casualties\":218581,\"tank_kills\":247879,\"aircraft_casualties\":29894,\"aircraft_kills\":28084,\"ship_casualties\":1347,\"ship_kills\":1863,\"missile_casualties\":24,\"missile_kills\":26,\"nuke_casualties\":0,\"nuke_kills\":24,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":289954297.04,\"project_bits\":75554136},{\"id\":122831,\"alliance_id\":2358,\"alliance_position\":2,\"alliance_position_id\":61,\"nation_name\":\"Greater Neo Drakonus\",\"leader_name\":\"Taranis V\",\"continent\":\"eu\",\"war_policy\":\"Arcane\",\"domestic_policy\":\"Urbanization\",\"color\":\"blue\",\"num_cities\":30,\"score\":5278.88,\"update_tz\":null,\"population\":7993519,\"flag\":\"https://politicsandwar.com/uploads/fe5d9dcd340e8495f0a20a36beeca6b8b909eb79860.png\",\"vacation_mode_turns\":0,\"beige_turns\":0,\"espionage_available\":true,\"date\":\"2018-06-01 16:47:10\",\"soldiers\":318000,\"tanks\":17250,\"aircraft\":2144,\"ships\":150,\"missiles\":4,\"nukes\":2,\"spies\":null,\"discord\":\"Taranis V#0708\",\"turns_since_last_city\":1973,\"turns_since_last_project\":1569,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":15,\"iron_works\":1,\"bauxite_works\":0,\"arms_stockpile\":1,\"emergency_gasoline_reserve\":1,\"mass_irrigation\":1,\"international_trade_center\":1,\"missile_launch_pad\":1,\"nuclear_research_facility\":1,\"iron_dome\":1,\"vital_defense_system\":1,\"central_intelligence_agency\":1,\"center_for_civil_engineering\":1,\"propaganda_bureau\":1,\"uranium_enrichment_program\":0,\"urban_planning\":0,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":1,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":1,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":1,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":0,\"wars_won\":91,\"wars_lost\":25,\"tax_id\":15961,\"alliance_seniority\":695,\"gross_national_income\":11644717,\"gross_domestic_product\":8107663831,\"soldier_casualties\":6452913,\"soldier_kills\":5649368,\"tank_casualties\":294047,\"tank_kills\":155967,\"aircraft_casualties\":25544,\"aircraft_kills\":14834,\"ship_casualties\":2038,\"ship_kills\":1233,\"missile_casualties\":47,\"missile_kills\":11,\"nuke_casualties\":13,\"nuke_kills\":52,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":151526740.9,\"project_bits\":76554237},{\"id\":244590,\"alliance_id\":913,\"alliance_position\":2,\"alliance_position_id\":498,\"nation_name\":\"Lithuania SSR\",\"leader_name\":\"Aleksandras\",\"continent\":\"eu\",\"war_policy\":\"Pirate\",\"domestic_policy\":\"Imperialism\",\"color\":\"black\",\"num_cities\":19,\"score\":2892.47,\"update_tz\":null,\"population\":2220447,\"flag\":\"https://politicsandwar.com/uploads/c75b6b344a62727759b7094c5c0b411276b09fbc867.jpg\",\"vacation_mode_turns\":0,\"beige_turns\":0,\"espionage_available\":true,\"date\":\"2020-09-27 14:36:02\",\"soldiers\":281415,\"tanks\":14250,\"aircraft\":1425,\"ships\":136,\"missiles\":0,\"nukes\":0,\"spies\":null,\"discord\":\"Potato Guy#9022\",\"turns_since_last_city\":208,\"turns_since_last_project\":684,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":5,\"iron_works\":0,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":0,\"mass_irrigation\":0,\"international_trade_center\":0,\"missile_launch_pad\":1,\"nuclear_research_facility\":1,\"iron_dome\":0,\"vital_defense_system\":0,\"central_intelligence_agency\":0,\"center_for_civil_engineering\":0,\"propaganda_bureau\":1,\"uranium_enrichment_program\":0,\"urban_planning\":1,\"advanced_urban_planning\":1,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":0,\"wars_won\":246,\"wars_lost\":129,\"tax_id\":79,\"alliance_seniority\":429,\"gross_national_income\":-2677757,\"gross_domestic_product\":400159734,\"soldier_casualties\":12640713,\"soldier_kills\":5982667,\"tank_casualties\":130504,\"tank_kills\":228099,\"aircraft_casualties\":8209,\"aircraft_kills\":9286,\"ship_casualties\":808,\"ship_kills\":702,\"missile_casualties\":114,\"missile_kills\":27,\"nuke_casualties\":13,\"nuke_kills\":9,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":1715943584.44,\"project_bits\":53440},{\"id\":272314,\"alliance_id\":0,\"alliance_position\":0,\"alliance_position_id\":0,\"nation_name\":\"Ventizio\",\"leader_name\":\"Agosto\",\"continent\":\"eu\",\"war_policy\":\"Pirate\",\"domestic_policy\":\"Urbanization\",\"color\":\"beige\",\"num_cities\":10,\"score\":839.87,\"update_tz\":null,\"population\":392077,\"flag\":\"https://politicsandwar.com/uploads/ceb839de6820c500be9b5dc788a20535e5a68bfex234.png\",\"vacation_mode_turns\":0,\"beige_turns\":24,\"espionage_available\":true,\"date\":\"2021-02-21 15:59:44\",\"soldiers\":0,\"tanks\":0,\"aircraft\":0,\"ships\":0,\"missiles\":0,\"nukes\":0,\"spies\":null,\"discord\":\"DomRom117#1997\",\"turns_since_last_city\":1845,\"turns_since_last_project\":4044,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":4,\"iron_works\":0,\"bauxite_works\":0,\"arms_stockpile\":1,\"emergency_gasoline_reserve\":1,\"mass_irrigation\":1,\"international_trade_center\":0,\"missile_launch_pad\":1,\"nuclear_research_facility\":0,\"iron_dome\":0,\"vital_defense_system\":0,\"central_intelligence_agency\":0,\"center_for_civil_engineering\":0,\"propaganda_bureau\":0,\"uranium_enrichment_program\":0,\"urban_planning\":0,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":0,\"wars_won\":22,\"wars_lost\":34,\"tax_id\":0,\"alliance_seniority\":36,\"gross_national_income\":27648,\"gross_domestic_product\":227602976,\"soldier_casualties\":842045,\"soldier_kills\":667600,\"tank_casualties\":34082,\"tank_kills\":21977,\"aircraft_casualties\":1763,\"aircraft_kills\":1163,\"ship_casualties\":274,\"ship_kills\":192,\"missile_casualties\":21,\"missile_kills\":11,\"nuke_casualties\":0,\"nuke_kills\":0,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":30658156.35,\"project_bits\":92},{\"id\":300307,\"alliance_id\":0,\"alliance_position\":0,\"alliance_position_id\":0,\"nation_name\":\"Grimsby\",\"leader_name\":\"Harold\",\"continent\":\"af\",\"war_policy\":\"Moneybags\",\"domestic_policy\":\"Open Markets\",\"color\":\"gray\",\"num_cities\":5,\"score\":334.11,\"update_tz\":null,\"population\":130513,\"flag\":\"https://politicsandwar.com/uploads/02101df58df0646d3468cdf89ae6a74705bdd46ax800.jpg\",\"vacation_mode_turns\":0,\"beige_turns\":0,\"espionage_available\":true,\"date\":\"2021-04-17 13:22:14\",\"soldiers\":0,\"tanks\":0,\"aircraft\":0,\"ships\":0,\"missiles\":0,\"nukes\":0,\"spies\":null,\"discord\":\"FroggySCRG#8388\",\"turns_since_last_city\":4990,\"turns_since_last_project\":4990,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":0,\"iron_works\":0,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":0,\"mass_irrigation\":0,\"international_trade_center\":0,\"missile_launch_pad\":0,\"nuclear_research_facility\":0,\"iron_dome\":0,\"vital_defense_system\":0,\"central_intelligence_agency\":0,\"center_for_civil_engineering\":0,\"propaganda_bureau\":0,\"uranium_enrichment_program\":0,\"urban_planning\":0,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":0,\"wars_won\":0,\"wars_lost\":16,\"tax_id\":0,\"alliance_seniority\":0,\"gross_national_income\":141740,\"gross_domestic_product\":95397800,\"soldier_casualties\":7000,\"soldier_kills\":2131,\"tank_casualties\":26,\"tank_kills\":34,\"aircraft_casualties\":0,\"aircraft_kills\":0,\"ship_casualties\":0,\"ship_kills\":0,\"missile_casualties\":0,\"missile_kills\":0,\"nuke_casualties\":0,\"nuke_kills\":0,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":0,\"project_bits\":0},{\"id\":320545,\"alliance_id\":0,\"alliance_position\":0,\"alliance_position_id\":0,\"nation_name\":\"Bikini-Bottom\",\"leader_name\":\"SpongeBobSquarePants\",\"continent\":\"na\",\"war_policy\":\"Pirate\",\"domestic_policy\":\"Urbanization\",\"color\":\"gray\",\"num_cities\":10,\"score\":919.21,\"update_tz\":null,\"population\":850206,\"flag\":\"https://politicsandwar.com/img/flags/piratesheep.png\",\"vacation_mode_turns\":0,\"beige_turns\":0,\"espionage_available\":true,\"date\":\"2022-03-15 01:00:54\",\"soldiers\":0,\"tanks\":0,\"aircraft\":0,\"ships\":0,\"missiles\":0,\"nukes\":0,\"spies\":null,\"discord\":\"Blackbeard#6973\",\"turns_since_last_city\":994,\"turns_since_last_project\":859,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":2,\"iron_works\":0,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":0,\"mass_irrigation\":1,\"international_trade_center\":0,\"missile_launch_pad\":0,\"nuclear_research_facility\":0,\"iron_dome\":0,\"vital_defense_system\":0,\"central_intelligence_agency\":0,\"center_for_civil_engineering\":0,\"propaganda_bureau\":0,\"uranium_enrichment_program\":0,\"urban_planning\":0,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":1,\"wars_won\":48,\"wars_lost\":16,\"tax_id\":0,\"alliance_seniority\":0,\"gross_national_income\":1930671,\"gross_domestic_product\":1118663839,\"soldier_casualties\":343309,\"soldier_kills\":646078,\"tank_casualties\":9925,\"tank_kills\":28920,\"aircraft_casualties\":946,\"aircraft_kills\":1625,\"ship_casualties\":11,\"ship_kills\":26,\"missile_casualties\":0,\"missile_kills\":1,\"nuke_casualties\":0,\"nuke_kills\":1,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":126999800.4,\"project_bits\":536870928},{\"id\":340541,\"alliance_id\":0,\"alliance_position\":0,\"alliance_position_id\":0,\"nation_name\":\"Scotlandz\",\"leader_name\":\"Robert of Scotlandz\",\"continent\":\"eu\",\"war_policy\":\"Covert\",\"domestic_policy\":\"Urbanization\",\"color\":\"gray\",\"num_cities\":4,\"score\":318.9,\"update_tz\":null,\"population\":361517,\"flag\":\"https://politicsandwar.com/img/imgur-old/7a6fc15b6be2d18a1d5528857d160584fa4172b3325.png\",\"vacation_mode_turns\":207,\"beige_turns\":0,\"espionage_available\":true,\"date\":\"2021-07-08 15:14:28\",\"soldiers\":0,\"tanks\":0,\"aircraft\":0,\"ships\":0,\"missiles\":0,\"nukes\":0,\"spies\":null,\"discord\":\"\",\"turns_since_last_city\":4318,\"turns_since_last_project\":4465,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":0,\"iron_works\":0,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":0,\"mass_irrigation\":0,\"international_trade_center\":0,\"missile_launch_pad\":0,\"nuclear_research_facility\":0,\"iron_dome\":0,\"vital_defense_system\":0,\"central_intelligence_agency\":0,\"center_for_civil_engineering\":0,\"propaganda_bureau\":0,\"uranium_enrichment_program\":0,\"urban_planning\":0,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":0,\"wars_won\":0,\"wars_lost\":7,\"tax_id\":0,\"alliance_seniority\":0,\"gross_national_income\":443285,\"gross_domestic_product\":318477434,\"soldier_casualties\":10000,\"soldier_kills\":2129,\"tank_casualties\":0,\"tank_kills\":52,\"aircraft_casualties\":0,\"aircraft_kills\":0,\"ship_casualties\":0,\"ship_kills\":0,\"missile_casualties\":0,\"missile_kills\":0,\"nuke_casualties\":0,\"nuke_kills\":0,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":0,\"project_bits\":0},{\"id\":349031,\"alliance_id\":0,\"alliance_position\":0,\"alliance_position_id\":0,\"nation_name\":\"Kalikskaya\",\"leader_name\":\"Kalik\",\"continent\":\"as\",\"war_policy\":\"Blitzkrieg\",\"domestic_policy\":\"Urbanization\",\"color\":\"gray\",\"num_cities\":13,\"score\":1077.84,\"update_tz\":null,\"population\":38038,\"flag\":\"https://politicsandwar.com/img/imgur-old/173d0e2fd98a1c7dadf08f244780fd7e5e29ed30406.jpg\",\"vacation_mode_turns\":0,\"beige_turns\":0,\"espionage_available\":true,\"date\":\"2021-07-30 20:57:50\",\"soldiers\":0,\"tanks\":0,\"aircraft\":0,\"ships\":0,\"missiles\":20,\"nukes\":0,\"spies\":null,\"discord\":\"Kalik#1066\",\"turns_since_last_city\":1791,\"turns_since_last_project\":2589,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":3,\"iron_works\":1,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":1,\"mass_irrigation\":0,\"international_trade_center\":0,\"missile_launch_pad\":1,\"nuclear_research_facility\":0,\"iron_dome\":0,\"vital_defense_system\":0,\"central_intelligence_agency\":0,\"center_for_civil_engineering\":0,\"propaganda_bureau\":0,\"uranium_enrichment_program\":0,\"urban_planning\":0,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":0,\"wars_won\":3,\"wars_lost\":62,\"tax_id\":0,\"alliance_seniority\":0,\"gross_national_income\":1374181,\"gross_domestic_product\":1100279975,\"soldier_casualties\":89540,\"soldier_kills\":110722,\"tank_casualties\":25750,\"tank_kills\":5489,\"aircraft_casualties\":234,\"aircraft_kills\":83,\"ship_casualties\":17,\"ship_kills\":1,\"missile_casualties\":2,\"missile_kills\":0,\"nuke_casualties\":0,\"nuke_kills\":0,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":3989705.63,\"project_bits\":73},{\"id\":362802,\"alliance_id\":9427,\"alliance_position\":2,\"alliance_position_id\":299,\"nation_name\":\"Florinsa\",\"leader_name\":\"Apocalypsa\",\"continent\":\"as\",\"war_policy\":\"Pirate\",\"domestic_policy\":\"Imperialism\",\"color\":\"lime\",\"num_cities\":6,\"score\":1308.43,\"update_tz\":null,\"population\":1653406,\"flag\":\"https://politicsandwar.com/uploads/d7f9b4bdc69d9177481df5b60d7ed659fe6ce026x509.jpeg\",\"vacation_mode_turns\":0,\"beige_turns\":24,\"espionage_available\":true,\"date\":\"2021-09-18 05:55:32\",\"soldiers\":89836,\"tanks\":7500,\"aircraft\":450,\"ships\":90,\"missiles\":3,\"nukes\":0,\"spies\":null,\"discord\":\"Leland#8701\",\"turns_since_last_city\":7,\"turns_since_last_project\":309,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":8,\"iron_works\":0,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":0,\"mass_irrigation\":0,\"international_trade_center\":0,\"missile_launch_pad\":1,\"nuclear_research_facility\":0,\"iron_dome\":0,\"vital_defense_system\":0,\"central_intelligence_agency\":1,\"center_for_civil_engineering\":1,\"propaganda_bureau\":1,\"uranium_enrichment_program\":0,\"urban_planning\":1,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":1,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":1,\"resource_production_center\":1,\"wars_won\":684,\"wars_lost\":3,\"tax_id\":15212,\"alliance_seniority\":180,\"gross_national_income\":3571687,\"gross_domestic_product\":2253251145,\"soldier_casualties\":1940465,\"soldier_kills\":5154724,\"tank_casualties\":101634,\"tank_kills\":160319,\"aircraft_casualties\":2420,\"aircraft_kills\":13826,\"ship_casualties\":438,\"ship_kills\":1478,\"missile_casualties\":4,\"missile_kills\":12,\"nuke_casualties\":0,\"nuke_kills\":1,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":1491859875.21,\"project_bits\":805854272},{\"id\":373549,\"alliance_id\":0,\"alliance_position\":0,\"alliance_position_id\":0,\"nation_name\":\"Omicron Rho\",\"leader_name\":\"Bam\",\"continent\":\"eu\",\"war_policy\":\"Pirate\",\"domestic_policy\":\"Open Markets\",\"color\":\"gray\",\"num_cities\":8,\"score\":612.35,\"update_tz\":null,\"population\":169067,\"flag\":\"https://politicsandwar.com/uploads/96f5acb8531e73e19649281a29d2ed9c2b1c25ac641x600638.png\",\"vacation_mode_turns\":0,\"beige_turns\":0,\"espionage_available\":true,\"date\":\"2021-10-27 22:39:39\",\"soldiers\":0,\"tanks\":0,\"aircraft\":0,\"ships\":0,\"missiles\":5,\"nukes\":0,\"spies\":null,\"discord\":\"\",\"turns_since_last_city\":2830,\"turns_since_last_project\":2804,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":1,\"iron_works\":0,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":0,\"mass_irrigation\":0,\"international_trade_center\":0,\"missile_launch_pad\":1,\"nuclear_research_facility\":0,\"iron_dome\":0,\"vital_defense_system\":0,\"central_intelligence_agency\":0,\"center_for_civil_engineering\":0,\"propaganda_bureau\":0,\"uranium_enrichment_program\":0,\"urban_planning\":0,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":0,\"wars_won\":9,\"wars_lost\":27,\"tax_id\":0,\"alliance_seniority\":0,\"gross_national_income\":47184,\"gross_domestic_product\":152252251,\"soldier_casualties\":194809,\"soldier_kills\":167101,\"tank_casualties\":9408,\"tank_kills\":5427,\"aircraft_casualties\":219,\"aircraft_kills\":145,\"ship_casualties\":30,\"ship_kills\":6,\"missile_casualties\":4,\"missile_kills\":0,\"nuke_casualties\":0,\"nuke_kills\":0,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":26718172.76,\"project_bits\":64},{\"id\":388438,\"alliance_id\":0,\"alliance_position\":0,\"alliance_position_id\":0,\"nation_name\":\"Sputnikia\",\"leader_name\":\"Holy Sputnik\",\"continent\":\"as\",\"war_policy\":\"Pirate\",\"domestic_policy\":\"Technological Advancement\",\"color\":\"gray\",\"num_cities\":3,\"score\":252,\"update_tz\":null,\"population\":316339,\"flag\":\"https://politicsandwar.com/img/flags/indonesia.jpg\",\"vacation_mode_turns\":255,\"beige_turns\":0,\"espionage_available\":true,\"date\":\"2021-12-15 11:14:22\",\"soldiers\":0,\"tanks\":0,\"aircraft\":0,\"ships\":6,\"missiles\":0,\"nukes\":0,\"spies\":null,\"discord\":\"Holy Sputnik#2997\",\"turns_since_last_city\":2396,\"turns_since_last_project\":2341,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":1,\"iron_works\":0,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":0,\"mass_irrigation\":0,\"international_trade_center\":0,\"missile_launch_pad\":0,\"nuclear_research_facility\":0,\"iron_dome\":0,\"vital_defense_system\":0,\"central_intelligence_agency\":1,\"center_for_civil_engineering\":0,\"propaganda_bureau\":0,\"uranium_enrichment_program\":0,\"urban_planning\":0,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":0,\"wars_won\":18,\"wars_lost\":4,\"tax_id\":0,\"alliance_seniority\":0,\"gross_national_income\":635692,\"gross_domestic_product\":344327080,\"soldier_casualties\":72108,\"soldier_kills\":105941,\"tank_casualties\":2969,\"tank_kills\":2291,\"aircraft_casualties\":91,\"aircraft_kills\":58,\"ship_casualties\":0,\"ship_kills\":1,\"missile_casualties\":0,\"missile_kills\":0,\"nuke_casualties\":0,\"nuke_kills\":0,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":63881994.82,\"project_bits\":1024},{\"id\":400458,\"alliance_id\":0,\"alliance_position\":0,\"alliance_position_id\":0,\"nation_name\":\"Russian P\",\"leader_name\":\"Vladimir jade\",\"continent\":\"eu\",\"war_policy\":\"Covert\",\"domestic_policy\":\"Imperialism\",\"color\":\"gray\",\"num_cities\":4,\"score\":289.65,\"update_tz\":null,\"population\":268160,\"flag\":\"https://politicsandwar.com/uploads/9e83bc7bf50852a9a0df1887d9af1f2cbc8218bc1000x664469.jpeg\",\"vacation_mode_turns\":339,\"beige_turns\":0,\"espionage_available\":true,\"date\":\"2022-01-30 01:14:30\",\"soldiers\":0,\"tanks\":0,\"aircraft\":0,\"ships\":0,\"missiles\":0,\"nukes\":0,\"spies\":null,\"discord\":\"\",\"turns_since_last_city\":1483,\"turns_since_last_project\":2001,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":0,\"iron_works\":0,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":0,\"mass_irrigation\":0,\"international_trade_center\":0,\"missile_launch_pad\":0,\"nuclear_research_facility\":0,\"iron_dome\":0,\"vital_defense_system\":0,\"central_intelligence_agency\":0,\"center_for_civil_engineering\":0,\"propaganda_bureau\":0,\"uranium_enrichment_program\":0,\"urban_planning\":0,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":0,\"wars_won\":0,\"wars_lost\":7,\"tax_id\":0,\"alliance_seniority\":0,\"gross_national_income\":197682,\"gross_domestic_product\":106477580,\"soldier_casualties\":14595,\"soldier_kills\":2835,\"tank_casualties\":156,\"tank_kills\":126,\"aircraft_casualties\":5,\"aircraft_kills\":0,\"ship_casualties\":0,\"ship_kills\":0,\"missile_casualties\":0,\"missile_kills\":0,\"nuke_casualties\":0,\"nuke_kills\":0,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":0,\"project_bits\":0},{\"id\":414798,\"alliance_id\":9192,\"alliance_position\":2,\"alliance_position_id\":73,\"nation_name\":\"Republica do sul do oeste\",\"leader_name\":\"Carlos Bolsona\",\"continent\":\"sa\",\"war_policy\":\"Turtle\",\"domestic_policy\":\"Manifest Destiny\",\"color\":\"gray\",\"num_cities\":7,\"score\":769.64,\"update_tz\":null,\"population\":969694,\"flag\":\"https://politicsandwar.com/uploads/bfa73b5aa1e6fc7d0c30726503fc69883f17f8c0x188.png\",\"vacation_mode_turns\":520,\"beige_turns\":0,\"espionage_available\":true,\"date\":\"2022-03-08 23:25:40\",\"soldiers\":31567,\"tanks\":0,\"aircraft\":147,\"ships\":21,\"missiles\":0,\"nukes\":0,\"spies\":null,\"discord\":\"Vitorius#0943\",\"turns_since_last_city\":249,\"turns_since_last_project\":1546,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":0,\"iron_works\":0,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":0,\"mass_irrigation\":0,\"international_trade_center\":0,\"missile_launch_pad\":0,\"nuclear_research_facility\":0,\"iron_dome\":0,\"vital_defense_system\":0,\"central_intelligence_agency\":0,\"center_for_civil_engineering\":0,\"propaganda_bureau\":0,\"uranium_enrichment_program\":0,\"urban_planning\":0,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":0,\"wars_won\":0,\"wars_lost\":3,\"tax_id\":14480,\"alliance_seniority\":118,\"gross_national_income\":588479,\"gross_domestic_product\":469936515,\"soldier_casualties\":129327,\"soldier_kills\":47697,\"tank_casualties\":5150,\"tank_kills\":2358,\"aircraft_casualties\":191,\"aircraft_kills\":0,\"ship_casualties\":6,\"ship_kills\":3,\"missile_casualties\":0,\"missile_kills\":0,\"nuke_casualties\":0,\"nuke_kills\":0,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":0,\"project_bits\":0},{\"id\":420980,\"alliance_id\":9573,\"alliance_position\":2,\"alliance_position_id\":21,\"nation_name\":\"Sultanate Of Jahania\",\"leader_name\":\"Jahanzeb\",\"continent\":\"as\",\"war_policy\":\"Attrition\",\"domestic_policy\":\"Technological Advancement\",\"color\":\"black\",\"num_cities\":14,\"score\":2876.5,\"update_tz\":null,\"population\":3518607,\"flag\":\"https://politicsandwar.com/uploads/18b79c288ac68df741551a52808d32bd66186ff41000x665420.jpg\",\"vacation_mode_turns\":0,\"beige_turns\":73,\"espionage_available\":true,\"date\":\"2022-03-22 06:38:14\",\"soldiers\":210000,\"tanks\":17500,\"aircraft\":1050,\"ships\":210,\"missiles\":5,\"nukes\":0,\"spies\":null,\"discord\":\"cartoonboy50#9278\",\"turns_since_last_city\":0,\"turns_since_last_project\":73,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":6,\"iron_works\":0,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":0,\"mass_irrigation\":0,\"international_trade_center\":1,\"missile_launch_pad\":1,\"nuclear_research_facility\":0,\"iron_dome\":0,\"vital_defense_system\":0,\"central_intelligence_agency\":1,\"center_for_civil_engineering\":0,\"propaganda_bureau\":0,\"uranium_enrichment_program\":0,\"urban_planning\":1,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":1,\"research_and_development_center\":0,\"resource_production_center\":1,\"wars_won\":47,\"wars_lost\":0,\"tax_id\":15605,\"alliance_seniority\":7,\"gross_national_income\":1572490,\"gross_domestic_product\":2171007681,\"soldier_casualties\":29552,\"soldier_kills\":60460,\"tank_casualties\":566,\"tank_kills\":443,\"aircraft_casualties\":32,\"aircraft_kills\":0,\"ship_casualties\":22,\"ship_kills\":15,\"missile_casualties\":2,\"missile_kills\":2,\"nuke_casualties\":0,\"nuke_kills\":1,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":18828972.16,\"project_bits\":671106144},{\"id\":424047,\"alliance_id\":4937,\"alliance_position\":2,\"alliance_position_id\":473,\"nation_name\":\"MAGA\",\"leader_name\":\"Don Trump\",\"continent\":\"as\",\"war_policy\":\"Pirate\",\"domestic_policy\":\"Urbanization\",\"color\":\"maroon\",\"num_cities\":12,\"score\":2007.41,\"update_tz\":null,\"population\":2154461,\"flag\":\"https://politicsandwar.com/uploads/7eea5d2fa22390eb677427c0cdb39935ca81786f668.jpg\",\"vacation_mode_turns\":0,\"beige_turns\":36,\"espionage_available\":true,\"date\":\"2022-03-28 11:59:59\",\"soldiers\":179779,\"tanks\":11860,\"aircraft\":900,\"ships\":0,\"missiles\":0,\"nukes\":0,\"spies\":null,\"discord\":\"towyl#7381\",\"turns_since_last_city\":48,\"turns_since_last_project\":192,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":5,\"iron_works\":0,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":0,\"mass_irrigation\":0,\"international_trade_center\":0,\"missile_launch_pad\":0,\"nuclear_research_facility\":0,\"iron_dome\":1,\"vital_defense_system\":0,\"central_intelligence_agency\":1,\"center_for_civil_engineering\":1,\"propaganda_bureau\":1,\"uranium_enrichment_program\":0,\"urban_planning\":0,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":1,\"wars_won\":187,\"wars_lost\":4,\"tax_id\":3370,\"alliance_seniority\":93,\"gross_national_income\":1739182,\"gross_domestic_product\":1909410851,\"soldier_casualties\":1126649,\"soldier_kills\":3456037,\"tank_casualties\":58850,\"tank_kills\":131109,\"aircraft_casualties\":2234,\"aircraft_kills\":8644,\"ship_casualties\":233,\"ship_kills\":357,\"missile_casualties\":0,\"missile_kills\":5,\"nuke_casualties\":0,\"nuke_kills\":0,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":571589820.26,\"project_bits\":536878336},{\"id\":429732,\"alliance_id\":8804,\"alliance_position\":2,\"alliance_position_id\":133,\"nation_name\":\"UNASR\",\"leader_name\":\"Joseph Adams\",\"continent\":\"na\",\"war_policy\":\"Pirate\",\"domestic_policy\":\"Urbanization\",\"color\":\"purple\",\"num_cities\":5,\"score\":695.64,\"update_tz\":null,\"population\":1131203,\"flag\":\"https://politicsandwar.com/img/flags/betsy_ross.jpg\",\"vacation_mode_turns\":0,\"beige_turns\":0,\"espionage_available\":true,\"date\":\"2022-04-13 07:07:31\",\"soldiers\":74102,\"tanks\":0,\"aircraft\":370,\"ships\":0,\"missiles\":0,\"nukes\":0,\"spies\":null,\"discord\":\"well hello there#3109\",\"turns_since_last_city\":521,\"turns_since_last_project\":436,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":1,\"iron_works\":0,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":0,\"mass_irrigation\":0,\"international_trade_center\":0,\"missile_launch_pad\":0,\"nuclear_research_facility\":0,\"iron_dome\":0,\"vital_defense_system\":0,\"central_intelligence_agency\":0,\"center_for_civil_engineering\":0,\"propaganda_bureau\":0,\"uranium_enrichment_program\":0,\"urban_planning\":0,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":1,\"wars_won\":58,\"wars_lost\":1,\"tax_id\":13360,\"alliance_seniority\":44,\"gross_national_income\":3665036,\"gross_domestic_product\":1781894535,\"soldier_casualties\":166759,\"soldier_kills\":316822,\"tank_casualties\":7391,\"tank_kills\":12616,\"aircraft_casualties\":447,\"aircraft_kills\":766,\"ship_casualties\":137,\"ship_kills\":199,\"missile_casualties\":0,\"missile_kills\":3,\"nuke_casualties\":0,\"nuke_kills\":0,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":140693541.78,\"project_bits\":536870912},{\"id\":435615,\"alliance_id\":7450,\"alliance_position\":2,\"alliance_position_id\":49,\"nation_name\":\"Alex pinnix land\",\"leader_name\":\"alex pinnix\",\"continent\":\"na\",\"war_policy\":\"Fortress\",\"domestic_policy\":\"Urbanization\",\"color\":\"brown\",\"num_cities\":10,\"score\":1608.5,\"update_tz\":null,\"population\":1847916,\"flag\":\"https://politicsandwar.com/img/imgur-old/d33bdef049ea1efcc72a76ba005395ead45624ae697.png\",\"vacation_mode_turns\":0,\"beige_turns\":60,\"espionage_available\":true,\"date\":\"2022-04-25 15:21:20\",\"soldiers\":0,\"tanks\":5760,\"aircraft\":615,\"ships\":50,\"missiles\":2,\"nukes\":0,\"spies\":null,\"discord\":\"Like my cat?#6872\",\"turns_since_last_city\":35,\"turns_since_last_project\":33,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":4,\"iron_works\":1,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":0,\"mass_irrigation\":0,\"international_trade_center\":0,\"missile_launch_pad\":1,\"nuclear_research_facility\":0,\"iron_dome\":0,\"vital_defense_system\":0,\"central_intelligence_agency\":0,\"center_for_civil_engineering\":0,\"propaganda_bureau\":1,\"uranium_enrichment_program\":0,\"urban_planning\":0,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":1,\"wars_won\":92,\"wars_lost\":0,\"tax_id\":9282,\"alliance_seniority\":0,\"gross_national_income\":6251965,\"gross_domestic_product\":3799107351,\"soldier_casualties\":51370,\"soldier_kills\":113734,\"tank_casualties\":2385,\"tank_kills\":7083,\"aircraft_casualties\":183,\"aircraft_kills\":631,\"ship_casualties\":25,\"ship_kills\":75,\"missile_casualties\":0,\"missile_kills\":3,\"nuke_casualties\":0,\"nuke_kills\":0,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":45183510.38,\"project_bits\":536875073},{\"id\":436962,\"alliance_id\":0,\"alliance_position\":0,\"alliance_position_id\":0,\"nation_name\":\"FreeWill\",\"leader_name\":\"Raf\",\"continent\":\"na\",\"war_policy\":\"Turtle\",\"domestic_policy\":\"Manifest Destiny\",\"color\":\"gray\",\"num_cities\":3,\"score\":311,\"update_tz\":null,\"population\":389890,\"flag\":\"https://politicsandwar.com/uploads/420e3cecbbb6456a2951872d85481b39303ed6fd647x600871.png\",\"vacation_mode_turns\":0,\"beige_turns\":0,\"espionage_available\":true,\"date\":\"2022-04-28 09:23:06\",\"soldiers\":15000,\"tanks\":200,\"aircraft\":0,\"ships\":0,\"missiles\":0,\"nukes\":0,\"spies\":null,\"discord\":\"\",\"turns_since_last_city\":341,\"turns_since_last_project\":820,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":1,\"iron_works\":0,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":0,\"mass_irrigation\":0,\"international_trade_center\":0,\"missile_launch_pad\":0,\"nuclear_research_facility\":0,\"iron_dome\":0,\"vital_defense_system\":0,\"central_intelligence_agency\":0,\"center_for_civil_engineering\":0,\"propaganda_bureau\":0,\"uranium_enrichment_program\":0,\"urban_planning\":0,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":1,\"wars_won\":0,\"wars_lost\":2,\"tax_id\":0,\"alliance_seniority\":0,\"gross_national_income\":1074425,\"gross_domestic_product\":613260031,\"soldier_casualties\":24111,\"soldier_kills\":4146,\"tank_casualties\":248,\"tank_kills\":139,\"aircraft_casualties\":0,\"aircraft_kills\":0,\"ship_casualties\":0,\"ship_kills\":0,\"missile_casualties\":0,\"missile_kills\":0,\"nuke_casualties\":0,\"nuke_kills\":0,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":0,\"project_bits\":536870912},{\"id\":440828,\"alliance_id\":9455,\"alliance_position\":2,\"alliance_position_id\":655,\"nation_name\":\"ayudia\",\"leader_name\":\"Edward break\",\"continent\":\"na\",\"war_policy\":\"Pirate\",\"domestic_policy\":\"Manifest Destiny\",\"color\":\"aqua\",\"num_cities\":8,\"score\":783.36,\"update_tz\":null,\"population\":987934,\"flag\":\"https://politicsandwar.com/img/flags/australia.jpg\",\"vacation_mode_turns\":0,\"beige_turns\":0,\"espionage_available\":true,\"date\":\"2022-05-08 14:06:57\",\"soldiers\":117788,\"tanks\":0,\"aircraft\":0,\"ships\":0,\"missiles\":0,\"nukes\":0,\"spies\":null,\"discord\":\"Edward break#3068\",\"turns_since_last_city\":81,\"turns_since_last_project\":818,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":0,\"iron_works\":0,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":0,\"mass_irrigation\":0,\"international_trade_center\":0,\"missile_launch_pad\":0,\"nuclear_research_facility\":0,\"iron_dome\":0,\"vital_defense_system\":0,\"central_intelligence_agency\":0,\"center_for_civil_engineering\":0,\"propaganda_bureau\":0,\"uranium_enrichment_program\":0,\"urban_planning\":0,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":0,\"wars_won\":44,\"wars_lost\":1,\"tax_id\":15906,\"alliance_seniority\":33,\"gross_national_income\":1442638,\"gross_domestic_product\":759595328,\"soldier_casualties\":49445,\"soldier_kills\":35201,\"tank_casualties\":350,\"tank_kills\":374,\"aircraft_casualties\":0,\"aircraft_kills\":0,\"ship_casualties\":0,\"ship_kills\":0,\"missile_casualties\":0,\"missile_kills\":0,\"nuke_casualties\":0,\"nuke_kills\":0,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":118774142.47,\"project_bits\":0},{\"id\":441723,\"alliance_id\":9455,\"alliance_position\":2,\"alliance_position_id\":3820,\"nation_name\":\"Choiceville\",\"leader_name\":\"Lord Diamond\",\"continent\":\"af\",\"war_policy\":\"Pirate\",\"domestic_policy\":\"Urbanization\",\"color\":\"beige\",\"num_cities\":11,\"score\":1348.53,\"update_tz\":null,\"population\":1990150,\"flag\":\"https://upload.wikimedia.org/wikipedia/commons/thumb/b/b8/Flag_of_Liberia.svg/1140px-Flag_of_Liberia.svg.png\",\"vacation_mode_turns\":0,\"beige_turns\":23,\"espionage_available\":true,\"date\":\"2022-05-11 08:24:51\",\"soldiers\":79085,\"tanks\":2617,\"aircraft\":75,\"ships\":0,\"missiles\":0,\"nukes\":0,\"spies\":null,\"discord\":\"Diamondhney#0128\",\"turns_since_last_city\":158,\"turns_since_last_project\":166,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":3,\"iron_works\":0,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":0,\"mass_irrigation\":0,\"international_trade_center\":0,\"missile_launch_pad\":0,\"nuclear_research_facility\":0,\"iron_dome\":0,\"vital_defense_system\":0,\"central_intelligence_agency\":0,\"center_for_civil_engineering\":1,\"propaganda_bureau\":1,\"uranium_enrichment_program\":0,\"urban_planning\":0,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":1,\"wars_won\":23,\"wars_lost\":3,\"tax_id\":15447,\"alliance_seniority\":30,\"gross_national_income\":2144024,\"gross_domestic_product\":1348938995,\"soldier_casualties\":209584,\"soldier_kills\":234430,\"tank_casualties\":9658,\"tank_kills\":4600,\"aircraft_casualties\":710,\"aircraft_kills\":792,\"ship_casualties\":16,\"ship_kills\":11,\"missile_casualties\":0,\"missile_kills\":0,\"nuke_casualties\":0,\"nuke_kills\":0,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":43589053.44,\"project_bits\":536877056},{\"id\":442320,\"alliance_id\":0,\"alliance_position\":0,\"alliance_position_id\":0,\"nation_name\":\"Zoro\",\"leader_name\":\"Brand Fyre\",\"continent\":\"as\",\"war_policy\":\"Pirate\",\"domestic_policy\":\"Open Markets\",\"color\":\"beige\",\"num_cities\":5,\"score\":549.46,\"update_tz\":null,\"population\":566547,\"flag\":\"https://politicsandwar.com/uploads/2272b43f9b0d12ccbf9c2fe8b2ef8218fed86949x880.jpeg\",\"vacation_mode_turns\":0,\"beige_turns\":41,\"espionage_available\":true,\"date\":\"2022-05-23 23:58:55\",\"soldiers\":49449,\"tanks\":0,\"aircraft\":63,\"ships\":31,\"missiles\":0,\"nukes\":0,\"spies\":null,\"discord\":\"\",\"turns_since_last_city\":355,\"turns_since_last_project\":179,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":2,\"iron_works\":0,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":1,\"mass_irrigation\":0,\"international_trade_center\":0,\"missile_launch_pad\":0,\"nuclear_research_facility\":0,\"iron_dome\":0,\"vital_defense_system\":0,\"central_intelligence_agency\":0,\"center_for_civil_engineering\":0,\"propaganda_bureau\":0,\"uranium_enrichment_program\":0,\"urban_planning\":0,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":1,\"wars_won\":35,\"wars_lost\":2,\"tax_id\":0,\"alliance_seniority\":0,\"gross_national_income\":1344921,\"gross_domestic_product\":770264311,\"soldier_casualties\":359866,\"soldier_kills\":813831,\"tank_casualties\":19347,\"tank_kills\":27162,\"aircraft_casualties\":642,\"aircraft_kills\":1369,\"ship_casualties\":60,\"ship_kills\":168,\"missile_casualties\":0,\"missile_kills\":1,\"nuke_casualties\":0,\"nuke_kills\":0,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":73728676.77,\"project_bits\":536870920},{\"id\":447997,\"alliance_id\":0,\"alliance_position\":0,\"alliance_position_id\":0,\"nation_name\":\"New Denmark\",\"leader_name\":\"Dane\",\"continent\":\"au\",\"war_policy\":\"Pirate\",\"domestic_policy\":\"Imperialism\",\"color\":\"aqua\",\"num_cities\":4,\"score\":378.06,\"update_tz\":null,\"population\":322285,\"flag\":\"https://politicsandwar.com/uploads/28789404137937f7a4d78cf70a6e9d4d54a6130cx334.png\",\"vacation_mode_turns\":0,\"beige_turns\":144,\"espionage_available\":true,\"date\":\"2022-05-30 03:07:49\",\"soldiers\":23487,\"tanks\":972,\"aircraft\":0,\"ships\":27,\"missiles\":0,\"nukes\":0,\"spies\":null,\"discord\":\"\",\"turns_since_last_city\":344,\"turns_since_last_project\":560,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":0,\"iron_works\":0,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":0,\"mass_irrigation\":0,\"international_trade_center\":0,\"missile_launch_pad\":0,\"nuclear_research_facility\":0,\"iron_dome\":0,\"vital_defense_system\":0,\"central_intelligence_agency\":0,\"center_for_civil_engineering\":0,\"propaganda_bureau\":0,\"uranium_enrichment_program\":0,\"urban_planning\":0,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":0,\"wars_won\":5,\"wars_lost\":0,\"tax_id\":0,\"alliance_seniority\":0,\"gross_national_income\":147603,\"gross_domestic_product\":229264156,\"soldier_casualties\":14358,\"soldier_kills\":6204,\"tank_casualties\":310,\"tank_kills\":0,\"aircraft_casualties\":33,\"aircraft_kills\":0,\"ship_casualties\":0,\"ship_kills\":0,\"missile_casualties\":0,\"missile_kills\":0,\"nuke_casualties\":0,\"nuke_kills\":0,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":7585614.86,\"project_bits\":0},{\"id\":454540,\"alliance_id\":4937,\"alliance_position\":2,\"alliance_position_id\":1790,\"nation_name\":\"Sigmaville\",\"leader_name\":\"Sigma Man\",\"continent\":\"eu\",\"war_policy\":\"Pirate\",\"domestic_policy\":\"Manifest Destiny\",\"color\":\"maroon\",\"num_cities\":5,\"score\":764.87,\"update_tz\":null,\"population\":853226,\"flag\":\"https://politicsandwar.com/uploads/809777efef4c5cbb8ce8220cf0cfc14b51c15eb8534x60099.jpg\",\"vacation_mode_turns\":0,\"beige_turns\":84,\"espionage_available\":true,\"date\":\"2022-06-21 19:08:55\",\"soldiers\":73298,\"tanks\":2422,\"aircraft\":375,\"ships\":25,\"missiles\":0,\"nukes\":0,\"spies\":null,\"discord\":\"\",\"turns_since_last_city\":17,\"turns_since_last_project\":26,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":2,\"iron_works\":0,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":0,\"mass_irrigation\":0,\"international_trade_center\":0,\"missile_launch_pad\":0,\"nuclear_research_facility\":0,\"iron_dome\":0,\"vital_defense_system\":0,\"central_intelligence_agency\":0,\"center_for_civil_engineering\":1,\"propaganda_bureau\":0,\"uranium_enrichment_program\":0,\"urban_planning\":0,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":1,\"wars_won\":13,\"wars_lost\":0,\"tax_id\":3370,\"alliance_seniority\":13,\"gross_national_income\":2211434,\"gross_domestic_product\":1329010275,\"soldier_casualties\":16200,\"soldier_kills\":40641,\"tank_casualties\":682,\"tank_kills\":1661,\"aircraft_casualties\":17,\"aircraft_kills\":89,\"ship_casualties\":0,\"ship_kills\":4,\"missile_casualties\":0,\"missile_kills\":0,\"nuke_casualties\":0,\"nuke_kills\":0,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":42381221.97,\"project_bits\":536872960},{\"id\":458157,\"alliance_id\":0,\"alliance_position\":0,\"alliance_position_id\":0,\"nation_name\":\"Arcadis\",\"leader_name\":\"Rani Emmett\",\"continent\":\"na\",\"war_policy\":\"Tactician\",\"domestic_policy\":\"Technological Advancement\",\"color\":\"beige\",\"num_cities\":1,\"score\":15.45,\"update_tz\":null,\"population\":20189,\"flag\":\"https://politicsandwar.com/img/flags/unitedstates.jpg\",\"vacation_mode_turns\":0,\"beige_turns\":168,\"espionage_available\":true,\"date\":\"2022-07-05 17:02:33\",\"soldiers\":500,\"tanks\":0,\"aircraft\":0,\"ships\":0,\"missiles\":0,\"nukes\":0,\"spies\":null,\"discord\":\"\",\"turns_since_last_city\":121,\"turns_since_last_project\":121,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":0,\"iron_works\":0,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":0,\"mass_irrigation\":0,\"international_trade_center\":0,\"missile_launch_pad\":0,\"nuclear_research_facility\":0,\"iron_dome\":0,\"vital_defense_system\":0,\"central_intelligence_agency\":0,\"center_for_civil_engineering\":0,\"propaganda_bureau\":0,\"uranium_enrichment_program\":0,\"urban_planning\":0,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":0,\"wars_won\":0,\"wars_lost\":0,\"tax_id\":0,\"alliance_seniority\":0,\"gross_national_income\":608364,\"gross_domestic_product\":222985850,\"soldier_casualties\":0,\"soldier_kills\":0,\"tank_casualties\":0,\"tank_kills\":0,\"aircraft_casualties\":0,\"aircraft_kills\":0,\"ship_casualties\":0,\"ship_kills\":0,\"missile_casualties\":0,\"missile_kills\":0,\"nuke_casualties\":0,\"nuke_kills\":0,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":0,\"project_bits\":0},{\"id\":458158,\"alliance_id\":0,\"alliance_position\":0,\"alliance_position_id\":0,\"nation_name\":\"The United Fedration\",\"leader_name\":\"Eternal General\",\"continent\":\"as\",\"war_policy\":\"Turtle\",\"domestic_policy\":\"Manifest Destiny\",\"color\":\"beige\",\"num_cities\":1,\"score\":10.25,\"update_tz\":null,\"population\":1000,\"flag\":\"https://politicsandwar.com/uploads/a9ebc7cbc4900dfce4230ba4a57a232574cae8de925.jpg\",\"vacation_mode_turns\":0,\"beige_turns\":168,\"espionage_available\":true,\"date\":\"2022-07-05 17:16:51\",\"soldiers\":0,\"tanks\":0,\"aircraft\":0,\"ships\":0,\"missiles\":0,\"nukes\":0,\"spies\":null,\"discord\":\"\",\"turns_since_last_city\":121,\"turns_since_last_project\":121,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":0,\"iron_works\":0,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":0,\"mass_irrigation\":0,\"international_trade_center\":0,\"missile_launch_pad\":0,\"nuclear_research_facility\":0,\"iron_dome\":0,\"vital_defense_system\":0,\"central_intelligence_agency\":0,\"center_for_civil_engineering\":0,\"propaganda_bureau\":0,\"uranium_enrichment_program\":0,\"urban_planning\":0,\"advanced_urban_planning\":0,\"space_program\":0,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":0,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":0,\"resource_production_center\":0,\"wars_won\":0,\"wars_lost\":0,\"tax_id\":0,\"alliance_seniority\":0,\"gross_national_income\":0,\"gross_domestic_product\":1,\"soldier_casualties\":0,\"soldier_kills\":0,\"tank_casualties\":0,\"tank_kills\":0,\"aircraft_casualties\":0,\"aircraft_kills\":0,\"ship_casualties\":0,\"ship_kills\":0,\"missile_casualties\":0,\"missile_kills\":0,\"nuke_casualties\":0,\"nuke_kills\":0,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":0,\"project_bits\":0}]";

        String data1 = "{\"id\":17362,\"alliance_id\":877,\"alliance_position\":2,\"alliance_position_id\":660,\"nation_name\":\"The Holy Britannian Empire\",\"leader_name\":\"Lelouch Vi Britannia\",\"continent\":\"na\",\"war_policy\":\"Fortress\",\"domestic_policy\":\"Urbanization\",\"color\":\"black\",\"num_cities\":23,\"score\":4590.5,\"update_tz\":null,\"population\":7369519,\"flag\":\"https://politicsandwar.com/img/imgur-old/917568acf2b88122f2e687d5bcfe41c8db855e95744.png\",\"vacation_mode_turns\":0,\"beige_turns\":0,\"espionage_available\":true,\"date\":\"2015-04-29 20:13:11\",\"soldiers\":321000,\"tanks\":28750,\"aircraft\":1725,\"ships\":0,\"missiles\":4,\"nukes\":3,\"spies\":null,\"discord\":\"\",\"turns_since_last_city\":104,\"turns_since_last_project\":186,\"money\":null,\"coal\":null,\"oil\":null,\"uranium\":null,\"iron\":null,\"bauxite\":null,\"lead\":null,\"gasoline\":null,\"munitions\":null,\"steel\":null,\"aluminum\":null,\"food\":null,\"projects\":12,\"iron_works\":1,\"bauxite_works\":0,\"arms_stockpile\":0,\"emergency_gasoline_reserve\":0,\"mass_irrigation\":1,\"international_trade_center\":1,\"missile_launch_pad\":1,\"nuclear_research_facility\":1,\"iron_dome\":1,\"vital_defense_system\":0,\"central_intelligence_agency\":1,\"center_for_civil_engineering\":0,\"propaganda_bureau\":1,\"uranium_enrichment_program\":0,\"urban_planning\":1,\"advanced_urban_planning\":0,\"space_program\":1,\"spy_satellite\":0,\"moon_landing\":0,\"pirate_economy\":0,\"recycling_initiative\":0,\"telecommunications_satellite\":0,\"green_technologies\":0,\"arable_land_agency\":1,\"clinical_research_center\":0,\"specialized_police_training_program\":0,\"advanced_engineering_corps\":0,\"government_support_agency\":0,\"research_and_development_center\":1,\"resource_production_center\":0,\"wars_won\":41,\"wars_lost\":87,\"tax_id\":78,\"alliance_seniority\":562,\"gross_national_income\":10060706,\"gross_domestic_product\":6620064174,\"soldier_casualties\":11277621,\"soldier_kills\":15269908,\"tank_casualties\":388822,\"tank_kills\":492431,\"aircraft_casualties\":60580,\"aircraft_kills\":60964,\"ship_casualties\":3312,\"ship_kills\":4839,\"missile_casualties\":51,\"missile_kills\":27,\"nuke_casualties\":94,\"nuke_kills\":47,\"spy_casualties\":null,\"spy_kills\":null,\"money_looted\":265661830.7,\"project_bits\":276911601}";

        PnwPusherHandler handler = new PnwPusherHandler(key)
        .connect()
        .subscribeBuilder(City.class, PnwPusherEvent.UPDATE)
        .setBulk(true)
        .build(cities -> {
            System.out.println("City update");
            for (City city : cities) {
                System.out.println("City " + city);
            }
        });

        Thread.sleep(100000);

        handler.disconnect();

        System.exit(1);
    }

    private void mutationTest() {
        BankDepositMutationRequest mutation = new BankDepositMutationRequest();
        mutation.setNote("test 123");
        mutation.setMoney(0.01);

        BankrecResponseProjection projection = new BankrecResponseProjection();
        projection.id();
        projection.date();

        Bankrec result = request(mutation, projection, Bankrec.class);

        System.out.println("Result " + result);
    }

    public Map<Integer, TaxBracket> fetchTaxBrackets(int allianceId) {
        Map<Integer, TaxBracket> taxBracketMap = new HashMap<>();
        List<Alliance> alliances = fetchAlliances(f -> f.setId(List.of(allianceId)), new Consumer<AllianceResponseProjection>() {
            @Override
            public void accept(AllianceResponseProjection proj) {
                proj.id();
                TaxBracketResponseProjection taxProj = new TaxBracketResponseProjection();
                taxProj.bracket_name();
                taxProj.id();
                taxProj.alliance_id();
                taxProj.resource_tax_rate();
                taxProj.tax_rate();
                proj.tax_brackets(taxProj);
            }
        });
        for (Alliance alliance : alliances) {
            if (alliance.getTax_brackets() != null) {
                for (TaxBracket bracket : alliance.getTax_brackets()) {
                    taxBracketMap.put(bracket.getId(), bracket);
                }
            }
        }
        return taxBracketMap;
    }

    public static void main(String[] args) throws ParseException, LoginException, InterruptedException, SQLException, ClassNotFoundException, IOException {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        Settings.INSTANCE.ENABLED_COMPONENTS.disableListeners();
        Settings.INSTANCE.ENABLED_COMPONENTS.disableTasks();
        Settings.INSTANCE.ENABLED_COMPONENTS.DISCORD_BOT = false;

        ApiKeyPool pool = new ApiKeyPool(Settings.INSTANCE.API_KEY_PRIMARY);
        PoliticsAndWarV3 main = new PoliticsAndWarV3(pool);

        {
            Locutus.create();
            Locutus.imp().getDiscordDB();
            System.out.println("Result ");

//            WarDB warDB = new WarDB();
//            Collection<DBAttack> attacks = warDB.getAttacks();
//            System.out.println("Num attcaks " + attacks.size());

//            int bytes = 0;
//            int numCities = 0;
//            Set<Integer> builds = new HashSet<>();
//            // test city memory
//            NationDB db = new NationDB();
//            for (Map<Integer, DBCity> cityEntry : db.getCities().values()) {
//                for (DBCity city : cityEntry.values()) {
//                    bytes += city.buildings.length;
//                    builds.add(Arrays.hashCode(city.buildings));
//                    numCities++;
//                }
//            }
//            System.out.println(builds.size() + "  unique builds in " + numCities + " cities " + bytes);

            System.exit(0);
        }

        {
            testPusher();
            System.exit(0);
        }

//        {
//            String query = "mutation{bankWithdraw(receiver_type:1,receiver: 189573,money: 0.01){id, date}}";
//            String queryFull = "{\"query\":\"" + query + "\"}";
//            Map<String, String> header = new HashMap<>();
//            header.put("X-Bot-Key", Settings.INSTANCE.API_KEY_PRIMARY);
//            byte[] queryBytes = queryFull.getBytes(StandardCharsets.UTF_8);
//
//
//            String url = main.getUrl(Settings.INSTANCE.API_KEY_PRIMARY);
//            String result = FileUtil.readStringFromURL(url, queryBytes, true, null,
//                    c -> {
//                            c.setRequestProperty("Content-Type", "application/json");
//                            c.setRequestProperty("X-Bot-Key", Settings.INSTANCE.API_KEY_PRIMARY);
//                    }
//            );
//
//            System.out.println("Result " + result);
//
//            System.exit(0);
//        }

        {
            BankDepositMutationRequest mutation = new BankDepositMutationRequest();
            mutation.setNote("test 123");
            mutation.setMoney(0.01);

            BankrecResponseProjection projection = new BankrecResponseProjection();
            projection.id();
            projection.date();

            Bankrec result = main.request(mutation, projection, Bankrec.class);

            System.out.println("Result " + result);

            System.exit(0);
        }

        {
            System.out.println("Starting");
            long start = System.currentTimeMillis();

            List<String> colors = new ArrayList<>();
            for (NationColor color : NationColor.values) {
                if (color == NationColor.GRAY || color == NationColor.BEIGE) continue;
                colors.add(color.name().toLowerCase(Locale.ROOT));
            }
            List<Nation> nations = main.fetchNations(new Consumer<NationsQueryRequest>() {
                @Override
                public void accept(NationsQueryRequest r) {
                    r.setColor(colors);
                    r.setVmode(false);
                }
            }, new Consumer<NationResponseProjection>() {
                @Override
                public void accept(NationResponseProjection r) {
                    r.id();
                    r.last_active();
                    r.alliance_id();
                    r.score();
                }
            });
            List<Integer> idsToFetch = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (Nation nation : nations) {
                long activeMs = nation.getLast_active().toEpochMilli();
                if (now - activeMs < TimeUnit.MINUTES.toMillis(15)) {
                    idsToFetch.add(nation.getId());
                }
            }
            nations = main.fetchNationsWithInfo(new Consumer<NationsQueryRequest>() {
                @Override
                public void accept(NationsQueryRequest r) {
                    r.setId(idsToFetch);
                }
            }, f -> true);

            long diff = System.currentTimeMillis() - start;
            System.out.println("Fetched " + nations.size() + " | " + diff);
            System.exit(0);
        }

        {

            int nationId = Settings.INSTANCE.NATION_ID;
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < 500; i++) ids.add(i);

            List<Nation> nations = main.fetchNations(new Consumer<NationsQueryRequest>() {
                @Override
                public void accept(NationsQueryRequest nationsQueryRequest) {
                    nationsQueryRequest.setId(ids);
                }
            }, new Consumer<NationResponseProjection>() {
                @Override
                public void accept(NationResponseProjection r) {
                    r.id();
                    r.spy_attacks();
                    r.spy_casualties();
                    r.spy_kills();
                    r.espionage_available();
                }
            });
            for (Nation nation : nations) {
                System.out.println("Nation " + nation);
            }
            System.out.println("Nations " + nations.size());


//            List<Bankrec> recs = main.fetchBankRecsWithInfo(new Consumer<BankrecsQueryRequest>() {
//                @Override
//                public void accept(BankrecsQueryRequest r) {
//                    r.setRtype(List.of(2));
//                    r.setStype(List.of(2));
//                    r.setOr_id(List.of(allianceId));
//                }
//            });

        }
        {
//            int nationId = Settings.INSTANCE.NATION_ID;
//            List<Bankrec> recs = main.fetchBankRecsWithInfo(new Consumer<BankrecsQueryRequest>() {
//                @Override
//                public void accept(BankrecsQueryRequest r) {
//                    r.setOr_id(List.of(nationId));
//                    r.setOr_type(List.of(1)); //1 == nation
//                }
//            });
//
//            BankDB db = new BankDB();
//            List<Transaction2> txs = recs.stream().map(Transaction2::fromApiV3).collect(Collectors.toList());
//            int[] result = db.addTransactions(txs);
//            for (int i = 0; i < result.length; i++) {
//                System.out.println(i + " -> " + result[i] + " | " + txs.get(i).note);
//            }

        }

//        testPusher();

//        {
//            long start = System.currentTimeMillis();
//            List<SWarContainer> wars = api.getWarsByAmount(5000).getWars();
//            long diff = System.currentTimeMillis() - start;
//            System.out.println("Diff1 " + diff);
//        }
//        {
//            long start = System.currentTimeMillis();
//            List<SWarContainer> wars = api.getWarsByAmount(1).getWars();
//            long diff = System.currentTimeMillis() - start;
//            System.out.println("Diff2 " + diff);
//        }
//        {
//            long start = System.currentTimeMillis();
//            List<SWarContainer> wars = api.getWarsByAmount(1).getWars();
//            long diff = System.currentTimeMillis() - start;
//            System.out.println("Diff3 " + diff);
//        }
//        {
//            long start = System.currentTimeMillis();
//            main.fetchWars(new Consumer<WarsQueryRequest>() {
//                @Override
//                public void accept(WarsQueryRequest r) {
//                    r.setActive(true);
//                    r.setDays_ago(0);
//                }
//            });
//            long diff = System.currentTimeMillis() - start;
//            System.out.println("Diff4 " + diff);
//        }


        System.exit(1);

        Locutus.create().start();

        System.out.println("Nation " + Settings.INSTANCE.NATION_ID);
        System.out.println("ADMIN_USER_ID " + Settings.INSTANCE.ADMIN_USER_ID);
        System.out.println("APPLICATION_ID " + Settings.INSTANCE.APPLICATION_ID);

        System.out.println("Update games");


        {
            for (Bounty bounty : main.fetchBounties(null, f -> f.all$(-1))) {
                System.out.println(bounty);
            }

        }

        {
            Set<Integer> alliances = new HashSet<>();
//            for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
//
//            }
//
            List<Nation> nations = main.fetchNations(new Consumer<NationsQueryRequest>() {
                @Override
                public void accept(NationsQueryRequest request) {
//                    request.setAlliance_id(Arrays.asList(3427,9829,8777,4397,1742,2510,5039,9618,9620,8535,8280,5049,7484,7580,9793,9465,5722));
//                    request.setAlliance_position(Arrays.asList(2, 3, 4, 5));
//                    request.setMin_cities(10);
//                    request.setVmode(false);

                    request.setId(Collections.singletonList(189573));
                }
            }, new Consumer<NationResponseProjection>() {
                @Override
                public void accept(NationResponseProjection projection) {
                    projection.id();

                    projection.gross_domestic_product();
                    CityResponseProjection cityProj = new CityResponseProjection();
                    cityProj.id();
                    cityProj.date();
                    cityProj.infrastructure();
                    cityProj.land();
                    cityProj.powered();

                    cityProj.oil_power();
                    cityProj.wind_power();
                    cityProj.coal_power();
                    cityProj.nuclear_power();
                    cityProj.coal_mine();
                    cityProj.lead_mine();
                    cityProj.iron_mine();
                    cityProj.bauxite_mine();
                    cityProj.oil_well();
                    cityProj.uranium_mine();
                    cityProj.farm();
                    cityProj.police_station();
                    cityProj.hospital();
                    cityProj.recycling_center();
                    cityProj.subway();
                    cityProj.supermarket();
                    cityProj.bank();
                    cityProj.shopping_mall();
                    cityProj.stadium();
                    cityProj.oil_refinery();
                    cityProj.aluminum_refinery();
                    cityProj.steel_mill();
                    cityProj.munitions_factory();
                    cityProj.barracks();
                    cityProj.factory();
                    cityProj.hangar();
                    cityProj.drydock();
//                    cityProj.nuke_date();
                    projection.cities(cityProj);
                }
            });

            for (Nation nation : nations) {
                List<City> cities = nation.getCities();
                for (City city : cities) {
                    System.out.println("City " + city);
                }
            }
        }

        System.exit(1);
        List<Alliance> alliances = main.fetchAlliances(new Consumer<AlliancesQueryRequest>() {
            @Override
            public void accept(AlliancesQueryRequest request) {
                request.setId(Collections.singletonList(9821));
            }
        }, new Consumer<AllianceResponseProjection>() {
            @Override
            public void accept(AllianceResponseProjection response) {
                BankrecResponseProjection projection = new BankrecResponseProjection();
                projection.id();
                projection.date();
                projection.sender_id();
                projection.sender_type();
                projection.receiver_id();
                projection.receiver_type();
                projection.banker_id();
                projection.note();
                projection.money();
                projection.coal();
                projection.oil();
                projection.uranium();
                projection.iron();
                projection.bauxite();
                projection.lead();
                projection.gasoline();
                projection.munitions();
                projection.steel();
                projection.aluminum();
                projection.food();
                projection.tax_id();

                AllianceBankrecsParametrizedInput input = new AllianceBankrecsParametrizedInput();
                input.rtype(Collections.singletonList(2));
                input.stype(Collections.singletonList(2));
                response.bankrecs(input, projection);
//                response.bankrecs(projection);

            }
        });


        System.out.println("Alliances " + alliances.size());

        for (Alliance aa : alliances) {
            Map<String, Object> fields = aa.getAdditionalFields();
            System.out.println("Fields: ");
            System.out.println(StringMan.getString(fields));

            List<Bankrec> recs = aa.getBankrecs();
            System.out.println("Recss " + recs.size());
            for (Bankrec rec : recs) {
                System.out.println(" - " + rec);
            }
        }

        System.exit(1);

        List<Bankrec> txs = main.fetchBankRecs(new Consumer<BankrecsQueryRequest>() {
            @Override
            public void accept(BankrecsQueryRequest request) {
                request.setSid(Collections.singletonList(9821));
            }
        }, new Consumer<BankrecResponseProjection>() {
            @Override
            public void accept(BankrecResponseProjection response) {
                response.money();
                response.coal();
                response.oil();
                response.uranium();
                response.iron();
                response.bauxite();
                response.lead();
                response.gasoline();
                response.munitions();
                response.steel();
                response.aluminum();
                response.food();

                response.note();

                response.tax_id();
                response.id();
                response.date();

                response.sid();
                response.stype();
                response.rid();
                response.rtype();

            }
        });

        System.out.println("Transactions " + txs.size());

        for (Bankrec tx : txs) {
            System.out.println(tx);
        }

//
//        for (Alliance alliance : alliances) {
//            for (AlliancePosition position : alliance.getAlliance_positions()) {
//                System.out.println(position.getId() + " | " + position.getPermissions());
//            }
//
//        }


//        main.fetchNations(500, new Consumer<NationsQueryRequest>() {
//            @Override
//            public void accept(NationsQueryRequest request) {
//                request.setAlliance_id(Collections.singletonList(8173));
//                request.setAlliance_position(Arrays.asList(2, 3, 4, 5));
//                request.setVmode(false);
//            }
//        }, new Consumer<NationResponseProjection>() {
//            @Override
//            public void accept(NationResponseProjection response) {
//
//            }
//        }, e -> {
//            System.out.println("Error " + e.getErrorType() + " | " + e.getMessage());
//            return ErrorResponse.THROW;
//        }, nation -> false);
//
//        NationsQueryRequest request = new NationsQueryRequest();
//        request.setAlliance_id(Collections.singletonList(8173));
//        request.setId(Collections.singletonList(239259));
//        request.setFirst(500);
//        request.setPage(2);
//
//        NationResponseProjection proj1 = new NationResponseProjection()
//                .nation_name()
//                .alliance_position()
//                .alliance_id()
//                .id()
//                .color()
//                ;
//
//        PaginatorInfoResponseProjection proj3 = new PaginatorInfoResponseProjection()
//                .count()
//                .currentPage()
//                .firstItem()
//                .hasMorePages()
//                .lastPage()
//                .perPage()
//                .total()
//                ;
//
//        NationPaginatorResponseProjection proj2 = new NationPaginatorResponseProjection()
//                .paginatorInfo(proj3)
//                .data(proj1);
//        GraphQLRequest graphQLRequest = new GraphQLRequest(request, proj2);
//
//        if (result.hasErrors()) {
//            System.out.println("Errors");
//            for (GraphQLError error : result.getErrors()) {
//                System.out.println("Error " + error.getErrorType() + "\n | " + error.getMessage() + "\n | " + error.getExtensions() + "\n | " + error.getLocations() + "\n | " + error.getPath());
//            }
//
//        }
//
//        System.out.println("Result " + result.toString());
//        NationPaginator paginator = result.nations();
//        List<Nation> data = paginator.getData();
//        System.out.println(data.size());
//        System.out.println(paginator.getPaginatorInfo() + " | " + result.getData() + " | " + result.toString());
//        System.out.println(data.get(0));
//
//        System.out.println(AttackType.class);
//        System.out.println("Done!");
    }

    private static HttpEntity<String> httpEntity(GraphQLRequest request, String key) {
        return new HttpEntity<>(request.toHttpJsonBody(), getHttpHeaders(key));
    }

    private static HttpEntity<String> httpEntity(GraphQLRequests request, String key) {
        return new HttpEntity<>(request.toHttpJsonBody(), getHttpHeaders(key));
    }

    private static HttpHeaders getHttpHeaders(String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            headers.set("X-Bot-Key", key);
        }
        return headers;
    }
}
