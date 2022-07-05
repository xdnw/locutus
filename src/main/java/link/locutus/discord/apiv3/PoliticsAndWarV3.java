package link.locutus.discord.apiv3;

import kotlin.collections.ArrayDeque;
import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.StringMan;
import com.kobylynskyi.graphql.codegen.model.graphql.*;
import com.politicsandwar.graphql.model.*;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.enums.NationColor;
import graphql.GraphQLException;
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

        ResponseEntity<T> exchange = null;

        int badKey = 0;
        int backOff = 0;
        while (true) {
            String key = pool.getNextApiKey();
            String url = getUrl(key);
            try {
                exchange = restTemplate.exchange(URI.create(url),
                        HttpMethod.POST,
                        httpEntity(graphQLRequest),
                        resultBody);
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
                    throw e;
                }
                pool.removeKey(key);
            } catch (HttpClientErrorException e) {
                AlertUtil.error(e.getMessage(), e);
                e.printStackTrace();
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
        T result = exchange.getBody();
        return result;
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
        return fetchBounties(1000, filter, query, f -> ErrorResponse.EXIT, f -> true);
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
        return fetchBaseballGames(BOUNTIES_PER_PAGE, filter, query, f -> ErrorResponse.EXIT, f -> true);
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

    public List<WarAttack> fetchAttacksSince(Integer maxId) {
        return fetchAttacks(r -> {
            if (maxId != null) r.setMin_id(maxId + 1);
            QueryWarattacksOrderByOrderByClause order = QueryWarattacksOrderByOrderByClause.builder()
                    .setColumn(QueryWarattacksOrderByColumn.ID)
                    .setOrder(SortOrder.ASC)
                    .build();
            r.setOrderBy(List.of(order));
        });
    }

    public List<WarAttack> fetchAttacks(Consumer<WarattacksQueryRequest> filter) {
        return fetchAttacks(ATTACKS_PER_PAGE, filter, new Consumer<WarAttackResponseProjection>() {
            @Override
            public void accept(WarAttackResponseProjection p) {
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
            }
        }, f -> ErrorResponse.EXIT, f -> true);
    }

    public List<WarAttack> fetchAttacks(Consumer<WarattacksQueryRequest> filter, Consumer<WarAttackResponseProjection> query) {
        return fetchAttacks(ATTACKS_PER_PAGE, filter, query, f -> ErrorResponse.EXIT, f -> true);
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
        return fetchCities(CITIES_PER_PAGE, filter, query, f -> ErrorResponse.EXIT, f -> true);
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
        return fetchBankRecs(BANKRECS_PER_PAGE, filter, query, f -> ErrorResponse.EXIT, f -> true);
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
        }, f -> PoliticsAndWarV3.ErrorResponse.EXIT, nationResults);
    }

    public List<Nation> fetchNations(Consumer<NationsQueryRequest> filter, Consumer<NationResponseProjection> query) {
        return fetchNations(NATIONS_PER_PAGE, filter, query, f -> ErrorResponse.EXIT, f -> true);
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
        }, f -> ErrorResponse.EXIT, f -> true);
    }

    public List<Alliance> fetchAlliances(Consumer<AlliancesQueryRequest> filter, Consumer<AllianceResponseProjection> query) {
        return fetchAlliances(ALLIANCES_PER_PAGE, filter, query, f -> ErrorResponse.EXIT, f -> true);
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
        return fetchTreaties(TREATIES_PER_PAGE, filter, query, f -> ErrorResponse.EXIT, f -> true);
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
    public void updateNations(boolean spies, boolean tx, boolean city, boolean project, boolean policy) {
        for (NationColor value : NationColor.values()) {

        }
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

    public static void main(String[] args) throws ParseException, LoginException, InterruptedException, SQLException, ClassNotFoundException, IOException {
        System.out.println("Hello World");
        System.exit(1);

        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        Settings.INSTANCE.ENABLED_COMPONENTS.disableListeners();
        Settings.INSTANCE.ENABLED_COMPONENTS.disableTasks();
        Settings.INSTANCE.ENABLED_COMPONENTS.DISCORD_BOT = false;
        Locutus.create().start();

        System.out.println("Nation " + Settings.INSTANCE.NATION_ID);
        System.out.println("ADMIN_USER_ID " + Settings.INSTANCE.ADMIN_USER_ID);
        System.out.println("APPLICATION_ID " + Settings.INSTANCE.APPLICATION_ID);

        ApiKeyPool pool = new ApiKeyPool(Settings.INSTANCE.API_KEY_PRIMARY);

        System.out.println("Update games");


        PoliticsAndWarV3 main = new PoliticsAndWarV3(pool);
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
//            return ErrorResponse.EXIT;
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

    private static HttpEntity<String> httpEntity(GraphQLRequest request) {
        return new HttpEntity<>(request.toHttpJsonBody(), getHttpHeaders());
    }

    private static HttpEntity<String> httpEntity(GraphQLRequests request) {
        return new HttpEntity<>(request.toHttpJsonBody(), getHttpHeaders());
    }

    private static HttpHeaders getHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
