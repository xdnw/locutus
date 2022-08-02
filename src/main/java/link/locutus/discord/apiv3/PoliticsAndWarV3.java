package link.locutus.discord.apiv3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.subscription.PnwPusherEvent;
import link.locutus.discord.apiv3.subscription.PnwPusherHandler;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.*;
import link.locutus.discord.util.StringMan;
import com.kobylynskyi.graphql.codegen.model.graphql.*;
import com.politicsandwar.graphql.model.*;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import graphql.GraphQLException;
import link.locutus.discord.util.trade.TradeDB;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import java.util.stream.Collectors;

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
    private final ObjectMapper jacksonObjectMapper;
    private final ApiKeyPool pool;

    public PoliticsAndWarV3(String url, ApiKeyPool pool) {
        this.endpoint = url;
        this.restTemplate = new RestTemplate();
        this.pool = pool;

        this.jacksonObjectMapper = Jackson2ObjectMapperBuilder.json().simpleDateFormat("yyyy-MM-dd").build();
    }

    public PoliticsAndWarV3(ApiKeyPool pool) {
        this("https://api" + (Settings.INSTANCE.TEST ? "-test" : "") + ".politicsandwar.com/graphql", pool);
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

        System.out.println(graphQLRequest.toQueryString() + " | " + graphQLRequest.getRequest());

        ResponseEntity<String> exchange = null;
        T result = null;

        int badKey = 0;
        int backOff = 0;
        while (true) {
            ApiKeyPool.ApiKey pair = pool.getNextApiKey();
            String url = getUrl(pair.getKey());
            try {
                restTemplate.acceptHeaderRequestCallback(String.class);
//
                HttpEntity<String> entity = httpEntity(graphQLRequest, pair.getKey(), pair.getBotKey());

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
                    throw new IllegalArgumentException(message.replace(pair.getKey(), "XXX"));
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
                System.out.println("Unauthorized ");

                pair.deleteApiKey();

                if (badKey++ >= 4 || pool.size() <= 1) {
                    e.printStackTrace();
                    AlertUtil.error(e.getMessage(), e);
                    rethrow(e, pair,false);
                    throw e;
                }
                pool.removeKey(pair);
                continue;
            } catch (HttpClientErrorException e) {
                e.printStackTrace();
                AlertUtil.error(e.getMessage(), e);
                rethrow(e, pair,false);
                throw e;
            } catch (JsonProcessingException e) {
                AlertUtil.error(e.getMessage(), e);
                rethrow(e, pair,true);
            } catch (Throwable e) {
                boolean remove = false;
                if (e.getMessage().contains("The bot key you provided is not valid.")) {
                    pair.deleteBotKey();
                    remove = true;
                }
                if (e.getMessage().contains("The API key you provided does not allow whitelisted access.")) {
                    remove = true;
                }
                if (badKey++ < 4 && pool.size() > 1) {
                    if (remove) pool.removeKey(pair);
                    continue;
                }
                rethrow(e, pair,false);
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

    private <T extends Throwable> void rethrow(T e, ApiKeyPool.ApiKey pair, boolean throwRuntime) {
        if (e.getMessage() != null &&
                (StringUtils.containsIgnoreCase(e.getMessage(), pair.getKey()) ||
                (pair.getBotKey() != null && StringUtils.containsIgnoreCase(e.getMessage(), pair.getBotKey())))) {
            String msg = StringUtils.replaceIgnoreCase(e.getMessage(), pair.getKey(), "XXX");
            if (pair.getBotKey() != null) msg = StringUtils.replaceIgnoreCase(msg, pair.getBotKey(), "XXX");
            throw new RuntimeException(msg);
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

//                    proj.nuke_date();

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

    public GameInfo getGameInfo() {
        Game_infoQueryResponse result = request(new Game_infoQueryRequest(), new GameInfoResponseProjection()
                        .game_date()
                        .radiation(new RadiationResponseProjection().all$()),
                Game_infoQueryResponse.class);
        if (result.game_info() == null) throw new GraphQLException("Error fetching game info " + result.toString());
        return result.game_info();
    }

    public static void testPusher() throws IOException, InterruptedException, ParseException {
        String key = Settings.INSTANCE.API_KEY_PRIMARY;

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

    public Map<Integer, double[]> getStockPile(Consumer<NationsQueryRequest> query) {
        Map<Integer, double[]> result = new HashMap<>();
        for (Nation rec : fetchNations(query::accept, new Consumer<NationResponseProjection>() {
            @Override
            public void accept(NationResponseProjection projection) {
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
            }
        })) {
            double[] resources = ResourceType.getBuffer();
            resources[ResourceType.MONEY.ordinal()] = rec.getMoney();
            resources[ResourceType.COAL.ordinal()] = rec.getCoal();
            resources[ResourceType.OIL.ordinal()] = rec.getOil();
            resources[ResourceType.URANIUM.ordinal()] = rec.getUranium();
            resources[ResourceType.IRON.ordinal()] = rec.getIron();
            resources[ResourceType.BAUXITE.ordinal()] = rec.getBauxite();
            resources[ResourceType.LEAD.ordinal()] = rec.getLead();
            resources[ResourceType.GASOLINE.ordinal()] = rec.getGasoline();
            resources[ResourceType.MUNITIONS.ordinal()] = rec.getMunitions();
            resources[ResourceType.STEEL.ordinal()] = rec.getSteel();
            resources[ResourceType.ALUMINUM.ordinal()] = rec.getAluminum();
            resources[ResourceType.FOOD.ordinal()] = rec.getFood();
            result.put(rec.getId(), resources);
        }
        return result;
    }

    public void testBotKey() {
        BankDepositMutationRequest mutation = new BankDepositMutationRequest();
        mutation.setNote("test 123");
//        mutation.setMoney(0.01);

        BankrecResponseProjection projection = new BankrecResponseProjection();
        projection.id();
        projection.date();

        Bankrec result = request(mutation, projection, Bankrec.class);
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

    public List<Nation> fetchNationActive(List<Integer> ids) {
        return fetchNations(f -> {
            f.setId(ids);
            f.setVmode(false);
        }, new Consumer<NationResponseProjection>() {
            @Override
            public void accept(NationResponseProjection r) {
                r.id();
                r.last_active();
            }
        });
    }

    private void pollForSpyops() {
        long outputChannel = 672225858223734804L;

        Set<Integer> whitelisted = new HashSet<>(Arrays.asList(
                7452,8173,8624,9000,4124
        ));

        Map<DBNation, Double> inRangeNations = new HashMap<>();
        for (DBNation nation : Locutus.imp().getNationDB().getNationsMatching(f -> f.getVm_turns() == 0 && f.active_m() < 2880 && f.getAlliancePosition() != null && f.isInSpyRange(DBNation.byId(Settings.INSTANCE.NATION_ID)))) {
            double value = 10000 - nation.active_m();
            if (!whitelisted.contains(nation.getAlliance_id())) {
                value /= 2;
                if (nation.daysSinceLastOffensive() > 200) {
                    value /= 2;
                }
                if (!nation.hasProject(Projects.INTELLIGENCE_AGENCY)) {
                    value /= 4;
                }
            }
            if (nation.getUserId() == null) {
                value /= 1.2;
            }
            if (nation.isBlockaded()) {
                value /= 1.2;
            }
            if (nation.isEspionageFull()) {
                value /= 1.5;
            }
            inRangeNations.put(nation, value);
        }
        inRangeNations.put(DBNation.byId(129454), Integer.MAX_VALUE * 1d);
        inRangeNations.put(DBNation.byId(270887), Integer.MAX_VALUE * 1d);

//        List<Integer> pollActivityOf = alliance.getNations(true, 10000, true).stream().map(DBNation::getNation_id).collect(Collectors.toList());

        List<Integer> pollSpiesOf = List.of(189573);

        Map<Integer, Nation> existingMap = new HashMap<>();
        while (true) {
            System.out.println("Polling for spy ops");

            long start = System.currentTimeMillis();
            List<Nation> defenders = fetchNations(f -> f.setId(pollSpiesOf), new Consumer<NationResponseProjection>() {
                @Override
                public void accept(NationResponseProjection r) {
                    r.id();
                    r.spy_casualties();
                    r.espionage_available();
                }
            });

            for (Nation nation : defenders) {
                Nation existing = existingMap.get(nation.getId());
                if (existing != null) {
                    if (!Objects.equals(existing.getSpy_casualties(), nation.getSpy_casualties()) || existing.getEspionage_available() != nation.getEspionage_available()) {
                        {
                            List<Map.Entry<DBNation, Double>> pollActivityOf = new ArrayList<>(inRangeNations.entrySet());
                            Collections.sort(pollActivityOf, (o1, o2) -> Double.compare(o2.getValue(), o1.getValue()));
                            if (pollActivityOf.size() > 500) pollActivityOf = pollActivityOf.subList(0, 500);

                            List<Integer> pollActivityOfIds = pollActivityOf.stream().map(f -> f.getKey().getNation_id()).collect(Collectors.toList());

                            List<Nation> results = new ArrayList<>(fetchNationActive(pollActivityOfIds));
                            Collections.sort(results, (o1, o2) -> Long.compare(o2.getLast_active().toEpochMilli(), o1.getLast_active().toEpochMilli()));
//                        results.removeIf(f -> DBNation.byId(f.getId()) == null);
//                        results.removeIf(f -> f.getLast_active().toEpochMilli() < start - 1000);

                            DBNation defender = DBNation.byId(existing.getId());

                            String title = "Defensive Spyop(?): " + defender.getNation() + " | " + defender.getAllianceName();
                            StringBuilder response = new StringBuilder();
                            response.append("Defender: " + defender.getNationUrlMarkup(true) + " | " + defender.getAllianceUrlMarkup(true) + "\n");

                            if (results.isEmpty()) {
                                response.append("Attacker: No results found");
                            } else {
                                response.append("Attackers:\n");
                                long now = System.currentTimeMillis();
                                for (Nation attActive : results) {
                                    long lastActive = attActive.getLast_active().toEpochMilli();
                                    DBNation attacker = DBNation.byId(attActive.getId());

                                    long diff = now - lastActive;

                                    response.append(" - " + attacker.getNationUrlMarkup(true) + " | " + attacker.getAllianceName() + " | " + MathMan.format(diff) + "ms");
                                    if (attacker.hasProject(Projects.INTELLIGENCE_AGENCY)) {
                                        response.append(" | IA=true");
                                    }
                                    if (attacker.hasProject(Projects.SPY_SATELLITE)) {
                                        response.append(" | SAT=true");
                                    }
                                    response.append("\n");
                                }
                            }

                            System.out.println("\n== " + title + "\n" + response + "\n");

                            GuildMessageChannel channel = Locutus.imp().getDiscordApi().getGuildChannelById(outputChannel);
                            if (channel != null) {
                                channel.sendMessageEmbeds(new EmbedBuilder().setTitle(title).setDescription(response.toString()).build()).queue();
                            }
                        }
                        {
                            List<Map.Entry<DBNation, Double>> pollActivityOf = new ArrayList<>(inRangeNations.entrySet());
                            Collections.sort(pollActivityOf, (o1, o2) -> Double.compare(o2.getValue(), o1.getValue()));
                            if (pollActivityOf.size() > 500) pollActivityOf = pollActivityOf.subList(0, 500);

                            List<Integer> pollActivityOfIds = pollActivityOf.stream().map(f -> f.getKey().getNation_id()).collect(Collectors.toList());

                            List<Nation> results = new ArrayList<>(fetchNationActive(pollActivityOfIds));
                            Collections.sort(results, (o1, o2) -> Long.compare(o2.getLast_active().toEpochMilli(), o1.getLast_active().toEpochMilli()));
                        results.removeIf(f -> DBNation.byId(f.getId()) == null);
                        results.removeIf(f -> f.getLast_active().toEpochMilli() < start - 10000);

                            DBNation defender = DBNation.byId(existing.getId());

                            String title = "Defensive Spyop(?): " + defender.getNation() + " | " + defender.getAllianceName();
                            StringBuilder response = new StringBuilder();
                            response.append("Defender: " + defender.getNationUrlMarkup(true) + " | " + defender.getAllianceUrlMarkup(true) + "\n");

                            if (results.isEmpty()) {
                                response.append("Attacker: No results found");
                            } else {
                                response.append("Attackers:\n");
                                long now = System.currentTimeMillis();
                                for (Nation attActive : results) {
                                    long lastActive = attActive.getLast_active().toEpochMilli();
                                    DBNation attacker = DBNation.byId(attActive.getId());

                                    long diff = now - lastActive;

                                    response.append(" - " + attacker.getNationUrlMarkup(true) + " | " + attacker.getAllianceName() + " | " + MathMan.format(diff) + "ms");
                                    if (attacker.hasProject(Projects.INTELLIGENCE_AGENCY)) {
                                        response.append(" | IA=true");
                                    }
                                    if (attacker.hasProject(Projects.SPY_SATELLITE)) {
                                        response.append(" | SAT=true");
                                    }
                                    response.append("\n");
                                }
                            }

                            System.out.println("\n== " + title + "\n" + response + "\n");

                            GuildMessageChannel channel = Locutus.imp().getDiscordApi().getGuildChannelById(outputChannel);
                            if (channel != null) {
                                channel.sendMessageEmbeds(new EmbedBuilder().setTitle(title).setDescription(response.toString()).build()).queue();
                            }
                        }
                    }
                }
                existingMap.put(nation.getId(), nation);
            }

            long diff = System.currentTimeMillis() - start;
            if (diff < 1400) {
                try {
                    Thread.sleep(1410 - diff);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public static void main(String[] args) throws ParseException, LoginException, InterruptedException, SQLException, ClassNotFoundException, IOException {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        Settings.INSTANCE.ENABLED_COMPONENTS.disableListeners();
        Settings.INSTANCE.ENABLED_COMPONENTS.disableTasks();
        Settings.INSTANCE.ENABLED_COMPONENTS.DISCORD_BOT = false;


        Locutus.create().start();

        ApiKeyPool pool =  ApiKeyPool.builder().addKey(Settings.INSTANCE.NATION_ID, Settings.INSTANCE.API_KEY_PRIMARY, Settings.INSTANCE.ACCESS_KEY).build();
        PoliticsAndWarV3 main = new PoliticsAndWarV3(pool);

        if (true) {
            Collection<Bounty> bounties = main.fetchBounties(null, f -> f.all$(-1));
            System.exit(0);
        }


        final int meId = 189573;
        final int cityId = 375361;
        DBNation meNation = DBNation.byId(meId);
        {



            Locutus.imp().getNationDB().updateNationsV2(false, null);
            while (Locutus.imp().getNationDB().getDirtyNations() > 10) {
                System.out.println("Dirty " + Locutus.imp().getNationDB().getDirtyNations());
                Locutus.imp().getNationDB().updateMostActiveNations(500, null);
            }

            List<Integer> ids = Locutus.imp().getNationDB().getMostActiveNationIds(500);

            long start = System.currentTimeMillis();
            List<Nation> mostActive = main.fetchNationActive(ids);
            long diff = System.currentTimeMillis() - start;
            System.out.println("Diff " + MathMan.format(diff) + "ms");

            Collections.sort(mostActive, (o1, o2) -> Long.compare(o2.getLast_active().toEpochMilli(), o1.getLast_active().toEpochMilli()));

            long now = System.currentTimeMillis();

            for (Nation nation : mostActive) {
                long lastActive = nation.getLast_active().toEpochMilli();
                long diff2 = now - lastActive;

                DBNation dbnation = DBNation.byId(nation.getId());
                System.out.println(dbnation.getNation() + " | " + dbnation.getAllianceName() + " | " + dbnation.getCities() + " | " + dbnation.getAlliancePosition() + " | " + MathMan.format(diff2) + "s");

            }

            System.exit(0);
        }

        {
            Locutus.imp().getWarDb().updateAttacks(null);
            Locutus.imp().getWarDb().loadNukeDates();
            System.exit(0);
        }

        {
            NationDB natDb = Locutus.imp().getNationDB();

            TradeDB tradeDb = Locutus.imp().getTradeManager();


//
//            natDb.markCityDirty(id, cityId, Long.MAX_VALUE);
//            natDb.updateDirtyCities(null);
//
//            {
//                JavaCity city = nation.getCityMap(false).get(cityId);
//                System.out.println("Nuke " + city.getNukeDate());
//                System.out.println("Pollution " + city.getPollution(nation::hasProject));
//                System.out.println("Age " + city.getAge());
//            }

//            double[] revenue = nation.getRevenue();
//            System.out.println(PnwUtil.resourcesToFancyString(revenue));


//            natDb.updateOutdatedAlliances(true, null);
//            natDb.updateOutdatedAlliances(true, null);
//            natDb.updateIncorrectNations(null);
//            natDb.updateIncorrectNations(null);
//            natDb.updateIncorrectNations(null);
//            System.out.println("Update v2");
//            natDb.updateNationsV2(false, null);

//            PnwPusherHandler pusher = new PnwPusherHandler(Settings.INSTANCE.API_KEY_PRIMARY);
//            pusher.connect().subscribeBuilder(Nation.class, PnwPusherEvent.UPDATE).build(new Consumer<List<Nation>>() {
//                @Override
//                public void accept(List<Nation> nations) {
//                    for (Nation nation : nations) {
//                        Locutus.imp().getNationDB().markNationDirty(nation.getId());
//                    }
//                }
//            });

//            while (true) {
//                natDb.updateMostActiveNations(500, null);
//                System.out.println("Update msot active 2");
//                long now = System.currentTimeMillis();
//                long cutoff = now - TimeUnit.MINUTES.toMillis(1);
//                for (DBNation nation : natDb.getNationsMatching(f -> f.lastActiveMs() > cutoff)) {
//                    System.out.println(nation.getNation() + " | " + nation.getAlliance() + " | " + (TimeUnit.MILLISECONDS.toSeconds(now - nation.lastActiveMs())));
//                }
//
//                main.fetchNationsWithInfo(f -> f.setId(List.of(Settings.INSTANCE.NATION_ID)), new Predicate<Nation>() {
//                    @Override
//                    public boolean test(Nation nation) {
//                        System.out.println("Nation " + nation);
//                        return false;
//                    }
//                });
//
//                break;
//            }
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
//            for (Nation nation : nations) {
//                long activeMs = nation.getLast_active().toEpochMilli();
//                if (now - activeMs < TimeUnit.MINUTES.toMillis(15)) {
//                    idsToFetch.add(nation.getId());
//                }
//            }
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
//            for (Nation nation : nations) {
//                System.out.println("Nation " + nation);
//            }
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

//        {
//            Set<Integer> alliances = new HashSet<>();
//            for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
//
//            }
//
//            List<Nation> nations = main.fetchNations(new Consumer<NationsQueryRequest>() {
//                @Override
//                public void accept(NationsQueryRequest request) {
//                    request.setAlliance_id(Arrays.asList(3427,9829,8777,4397,1742,2510,5039,9618,9620,8535,8280,5049,7484,7580,9793,9465,5722));
//                    request.setAlliance_position(Arrays.asList(2, 3, 4, 5));
//                    request.setMin_cities(10);
//                    request.setVmode(false);
//
//                    request.setId(Collections.singletonList(189573));
//                }
//            }, new Consumer<NationResponseProjection>() {
//                @Override
//                public void accept(NationResponseProjection projection) {
//                    projection.id();
//
//                    projection.gross_domestic_product();
//                    CityResponseProjection cityProj = new CityResponseProjection();
//                    cityProj.id();
//                    cityProj.date();
//                    cityProj.infrastructure();
//                    cityProj.land();
//                    cityProj.powered();
//
//                    cityProj.oil_power();
//                    cityProj.wind_power();
//                    cityProj.coal_power();
//                    cityProj.nuclear_power();
//                    cityProj.coal_mine();
//                    cityProj.lead_mine();
//                    cityProj.iron_mine();
//                    cityProj.bauxite_mine();
//                    cityProj.oil_well();
//                    cityProj.uranium_mine();
//                    cityProj.farm();
//                    cityProj.police_station();
//                    cityProj.hospital();
//                    cityProj.recycling_center();
//                    cityProj.subway();
//                    cityProj.supermarket();
//                    cityProj.bank();
//                    cityProj.shopping_mall();
//                    cityProj.stadium();
//                    cityProj.oil_refinery();
//                    cityProj.aluminum_refinery();
//                    cityProj.steel_mill();
//                    cityProj.munitions_factory();
//                    cityProj.barracks();
//                    cityProj.factory();
//                    cityProj.hangar();
//                    cityProj.drydock();
////                    cityProj.nuke_date();
//                    projection.cities(cityProj);
//                }
//            });
//
//            for (Nation nation : nations) {
//                List<City> cities = nation.getCities();
//                for (City city : cities) {
//                    System.out.println("City " + city);
//                }
//            }
//        }

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

    private static HttpEntity<String> httpEntity(GraphQLRequest request, String api, String bot) {
        return new HttpEntity<>(request.toHttpJsonBody(), getHttpHeaders(api, bot));
    }

    private static HttpEntity<String> httpEntity(GraphQLRequests request, String api, String bot) {
        return new HttpEntity<>(request.toHttpJsonBody(), getHttpHeaders(api, bot));
    }

    private static HttpHeaders getHttpHeaders(String api, String bot) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bot != null && !bot.isEmpty()) {
            headers.set("X-Bot-Key", bot);
            headers.set("X-Api-Key", api);
        }
        return headers;
    }
}
