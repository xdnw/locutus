package link.locutus.discord.apiv3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.util.*;
import link.locutus.discord.util.StringMan;
import com.kobylynskyi.graphql.codegen.model.graphql.*;
import com.politicsandwar.graphql.model.*;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import graphql.GraphQLException;
import link.locutus.discord.util.io.PagePriority;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class PoliticsAndWarV3 {
    static {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        GraphQLRequestSerializer.OBJECT_MAPPER.setDateFormat(sdf);
    }
    public static int NATIONS_PER_PAGE = 500;
    public static int CITIES_PER_PAGE = 500;
    public static int TREATIES_PER_PAGE = 1000;
    public static int BANKRECS_PER_PAGE = 1000;
    public static int ALLIANCES_PER_PAGE = 500;
    public static int ATTACKS_PER_PAGE = 1000;
    public static int WARS_PER_PAGE = 1000;
    public static int TRADES_PER_PAGE = 1000;
    public static int BOUNTIES_PER_PAGE = 1000;
    public static int BASEBALL_PER_PAGE = 1000;
    public static int EMBARGO_PER_PAGE = 1000;

    public static int BANS_PER_PAGE = 500;

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

    public <T> T readTemplate(PagePriority priority, GraphQLRequest graphQLRequest, Class<T> resultBody) {
        return readTemplate(priority.ordinal(), graphQLRequest, resultBody);
    }

    public <T> T readTemplate(int priority, GraphQLRequest graphQLRequest, Class<T> resultBody) {
        if (rateLimitGlobal.intervalMs != 0) {
            synchronized (rateLimitGlobal) {
                long now = System.currentTimeMillis();
                rateLimitGlobal.reset(now);

                if (rateLimitGlobal.remaining <= 0) {
                    long sleepMs = rateLimitGlobal.resetMs - now;
                    if (sleepMs > 0) {
                        try {
                            System.out.println("Hit rate limit ( " + rateLimitGlobal.limit + " | " + sleepMs + " )");
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

        ResponseEntity<String> exchange;
        T result;

        int badKey = 0;
        int backOff = 1;
        while (true) {
            ApiKeyPool.ApiKey pair = pool.getNextApiKey();
            String url = getUrl(pair.getKey());
            try {
                restTemplate.acceptHeaderRequestCallback(String.class);
//
                HttpEntity<String> entity = httpEntity(graphQLRequest, pair.getKey(), pair.getBotKey());

                exchange =
//                        restTemplate.exchange(URI.create(url),
                        FileUtil.submit(priority, () -> restTemplate.exchange(URI.create(url),
                        HttpMethod.POST,
                        entity,
                        String.class));

                String body = exchange.getBody();
                JsonNode json = jacksonObjectMapper.readTree(body);

                if (json.has("errors")) {
                    System.out.println("Body " + exchange.getBody());
                    System.out.println("\n\n------\n");
                    System.out.println(graphQLRequest.toQueryString() + " | " + graphQLRequest.getRequest());
                    System.out.println("\n\n------\n");
                    JsonNode errors = json.get("errors");
                    List<String> errorMessages = new ArrayList<>();
                    for (JsonNode error : errors) {
                        if (error.has("message")) {
                            errorMessages.add(error.get("message").toString());
                        }
                    }
                    String message = errorMessages.isEmpty() ? errors.toString() : StringMan.join(errorMessages, "\n");
                    message = message.replaceAll("(?i)[\\[\\]\"\\n^:\\s,\\.](?=.*[A-Za-z])(?=.*\\d)[0-9A-F]{14,}(?=[\\[\\]\"\\n$:\\s,\\.]|$)", "XXX");
                    rethrow(new IllegalArgumentException(message.replace(pair.getKey(), "XXX")), pair, true);
                }

                result = jacksonObjectMapper.readValue(body, resultBody);
                break;
            } catch (HttpClientErrorException.TooManyRequests e) {
                try {
                    long timeout = (60000L);
                    System.out.println(e.getMessage());
                    System.out.println("Hit rate limit 2 " + timeout + "ms");
                    Thread.sleep(timeout);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                backOff++;
            } catch (HttpClientErrorException.Unauthorized e) {
                System.out.println("Unauthorized ");
                try {
                    if (badKey++ >= 4 || pool.size() <= 1) {
                        e.printStackTrace();
                        AlertUtil.error(e.getMessage(), e);
                        rethrow(e, pair, false);
                        throw e;
                    }
                } finally {
                    pair.deleteApiKey();
                }
                pool.removeKey(pair);
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
                System.out.println("Error " + graphQLRequest.toHttpJsonBody() + "\n- " + e.getMessage());
                rethrow(e, pair,false);
                throw e;
            }
        }
        HttpHeaders header = exchange.getHeaders();
        synchronized (rateLimitGlobal) {
            if (header.containsKey("X-RateLimit-Reset-After")) {
                rateLimitGlobal.resetAfterMs = Long.parseLong(Objects.requireNonNull(header.get("X-RateLimit-Reset-After")).get(0)) * 1000L;
            }
            if (header.containsKey("X-RateLimit-Limit")) {
                rateLimitGlobal.limit = Integer.parseInt(Objects.requireNonNull(header.get("X-RateLimit-Limit")).get(0));
            }
            if (header.containsKey("X-RateLimit-Remaining")) {
                rateLimitGlobal.remaining = Integer.parseInt(Objects.requireNonNull(header.get("X-RateLimit-Remaining")).get(0));
            }
            if (header.containsKey("X-RateLimit-Reset")) {
                rateLimitGlobal.resetMs = Long.parseLong(Objects.requireNonNull(header.get("X-RateLimit-Reset")).get(0)) * 1000L;
            }
            if (header.containsKey("X-RateLimit-Interval")) {
                rateLimitGlobal.intervalMs = Integer.parseInt(Objects.requireNonNull(header.get("X-RateLimit-Interval")).get(0)) * 1000;
            }
        }

        return result;
    }

    private <T extends Throwable> void rethrow(T e, ApiKeyPool.ApiKey pair, boolean throwRuntime) {
        String msg = e.getMessage();
        msg = msg.replaceAll("(?i)[\\[\\]\"\\n^:\\s,\\.](?=.*[A-Za-z])(?=.*\\d)[0-9A-F]{14,}(?=[\\[\\]\"\\n$:\\s,\\.]|$)", "XXX");
        if (e.getMessage() != null &&
                (StringUtils.containsIgnoreCase(e.getMessage(), pair.getKey()) ||
                (pair.getBotKey() != null && StringUtils.containsIgnoreCase(e.getMessage(), pair.getBotKey())))) {
            msg = StringUtils.replaceIgnoreCase(e.getMessage(), pair.getKey(), "XXX");
            if (pair.getBotKey() != null) msg = StringUtils.replaceIgnoreCase(msg, pair.getBotKey(), "XXX");
            throwRuntime = true;
        }
        if (msg == null) msg = "";
        if (pair.getKey() != null) {
            Integer nation = Locutus.imp().getDiscordDB().getNationFromApiKey(pair.getKey(), false);
            if (nation != null) {
                msg = msg + " (using key from: " + nation + ")";
            }
        }
        if (throwRuntime) throw new RuntimeException(msg);
        if (e instanceof HttpClientErrorException.Unauthorized unauthorized) {
            throw HttpClientErrorException.create(msg, unauthorized.getStatusCode(), unauthorized.getStatusText(), unauthorized.getResponseHeaders(), unauthorized.getResponseBodyAsByteArray(), /* charset utf-8 */ StandardCharsets.UTF_8);
        }
    }

    public List<BannedNation> getBansSince(long date) {
        return getBansSince(date, null);
    }

    public List<BannedNation> getBansSince(long date, Consumer<Banned_nationsQueryRequest> filter) {
        List<BannedNation> allResults = new ArrayList<>();
        AtomicBoolean stopPaginating = new AtomicBoolean(false);
        int perPage = BANS_PER_PAGE;
        handlePagination(PagePriority.API_BANS, page -> {
            Banned_nationsQueryRequest request = new Banned_nationsQueryRequest();
            request.setOrderBy(List.of(
                    QueryBannedNationsOrderByOrderByClause.builder()
                            .setOrder(SortOrder.DESC)
                            .setColumn(QueryBannedNationsOrderByColumn.DATE)
                            .build()
            ));
            if (filter != null) filter.accept(request);
            request.setFirst(perPage);
            request.setPage(page);

            BannedNationResponseProjection respProj = new BannedNationResponseProjection();
            respProj.nation_id();
            respProj.date();
            respProj.days_left();
            respProj.reason();

            BannedNationPaginatorResponseProjection pagRespProj = new BannedNationPaginatorResponseProjection()
                    .paginatorInfo(new PaginatorInfoResponseProjection()
                    .hasMorePages())
                    .data(respProj);

            return new GraphQLRequest(request, pagRespProj);
        }, f -> ErrorResponse.THROW, Banned_nationsQueryResponse.class,
        response -> {
            BannedNationPaginator paginator = response.banned_nations();
            PaginatorInfo pageInfo = paginator != null ? paginator.getPaginatorInfo() : null;
            return pageInfo != null && pageInfo.getHasMorePages() && !stopPaginating.get();
        }, result -> {
            BannedNationPaginator paginator = result.banned_nations();
            if (paginator != null) {
                List<BannedNation> txs = paginator.getData();
                for (BannedNation tx : txs) {
                    if (tx.getDate().toEpochMilli() <= date) {
                        stopPaginating.set(true);
                        continue;
                    }
                    allResults.add(tx);
                }
            }
        });
        return allResults;
    }

    public <T extends GraphQLResult<?>> void handlePagination(PagePriority priority, Function<Integer, GraphQLRequest> requestFactory, Function<GraphQLError, ErrorResponse> errorBehavior, Class<T> resultBody, Predicate<T> hasMorePages, Consumer<T> onEachResult) {
        pageLoop:
        for (int page = 1; ; page++) {
            GraphQLRequest graphQLRequest = requestFactory.apply(page);

            for (int i = 0; i < 5; i++) {
                T result = readTemplate(priority.ordinal() - (page == 1 ? 0 : 1), graphQLRequest, resultBody);

                boolean iterateNext = result != null && hasMorePages.test(result);

                if (result == null) {
                    break pageLoop;
                }
                if (result.hasErrors()) {
                    System.out.println("Has error ");
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
                                long timeout = Math.min(60000, (long) (1000 + Math.pow(i * 1000, 2)));
                                System.out.println("Hit rate limit 3 " + timeout + "ms");
                                Thread.sleep(timeout);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            continue;
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

        handlePagination(PagePriority.API_BOUNTIES, page -> {
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

    public List<Treasure> fetchTreasures() {
        TreasuresQueryResponse result = request(PagePriority.API_TREASURES, new TreasuresQueryRequest(), new TreasureResponseProjection()
                        .name()
                    .color()
                    .continent()
                    .bonus()
                    .spawn_date()
                    .nation_id(),
                TreasuresQueryResponse.class);
        if (result.treasures() == null) throw new GraphQLException("Error fetching colors");
        return result.treasures();
    }

    public List<BBGame> fetchBaseballGames(Consumer<Baseball_gamesQueryRequest> filter, Consumer<BBGameResponseProjection> query) {
        return fetchBaseballGames(BASEBALL_PER_PAGE, filter, query, f -> ErrorResponse.THROW, f -> true);
    }

    public List<BBGame> fetchBaseballGames(int perPage, Consumer<Baseball_gamesQueryRequest> filter, Consumer<BBGameResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<BBGame> recResults) {
        List<BBGame> allResults = new ArrayList<>();

        handlePagination(PagePriority.API_BASEBALL, page -> {
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
            p.success();
//            p.attcas1();
//            p.attcas2();
//            p.defcas1();
//            p.defcas2();
            // victory
            p.infra_destroyed_percentage();

            CityInfraDamageResponseProjection cityDamageProj = new CityInfraDamageResponseProjection();
            cityDamageProj.infrastructure();
            cityDamageProj.id();

            p.cities_infra_before(cityDamageProj);


            // unit attack
            p.infra_destroyed();
            p.city_infra_before();
            p.city_id();

            // units for each attack
            p.att_soldiers_lost();
            p.def_soldiers_lost();
            p.att_tanks_lost();
            p.def_tanks_lost();
            p.att_aircraft_lost();
            p.def_aircraft_lost();
            p.att_ships_lost();
            p.def_ships_lost();

            // ground money
            p.money_stolen();

            // a and victory loot
            // loot percent??
            p.loot_info(); // deprecated
            p.money_looted();
            p.coal_looted();
            p.oil_looted();
            p.uranium_looted();
            p.iron_looted();
            p.bauxite_looted();
            p.lead_looted();
            p.gasoline_looted();
            p.munitions_looted();
            p.steel_looted();
            p.aluminum_looted();
            p.food_looted();

            // airstrike money
            p.money_destroyed();

            // improvements
            p.improvements_destroyed();

            p.money_stolen();
//            p.infra_destroyed_value(); // can be calculated

            // slavage
            p.military_salvage_aluminum();
            p.military_salvage_steel();

            p.att_gas_used();
            p.att_mun_used();
            p.def_gas_used();
            p.def_mun_used();
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

        handlePagination(PagePriority.API_ATTACKS, page -> {
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
        return fetchWars(WARS_PER_PAGE, filter, p -> {
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
        }, f -> ErrorResponse.THROW, f -> true);
    }

    public List<War> fetchWars(Consumer<WarsQueryRequest> filter, Consumer<WarResponseProjection> query) {
        return fetchWars(WARS_PER_PAGE, filter, query, f -> ErrorResponse.THROW, f -> true);
    }

    public List<War> fetchWars(int perPage, Consumer<WarsQueryRequest> filter, Consumer<WarResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<War> recResults) {
        List<War> allResults = new ArrayList<>();

        handlePagination(PagePriority.API_WARS, page -> {
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
        return fetchCities(filter, proj -> {
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
        });
    }

    public List<City> fetchCities(Consumer<CitiesQueryRequest> filter, Consumer<CityResponseProjection> query) {
        return fetchCities(CITIES_PER_PAGE, filter, query, f -> ErrorResponse.THROW, f -> true);
    }

    public List<City> fetchCities(int perPage, Consumer<CitiesQueryRequest> filter, Consumer<CityResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<City> recResults) {
        List<City> allResults = new ArrayList<>();

        handlePagination(PagePriority.API_CITIES, page -> {
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

    public Consumer<BankrecResponseProjection> createBankRecProjection() {
        return proj -> {
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
        };
    }

    public List<Bankrec> fetchBankRecsWithInfo(Consumer<BankrecsQueryRequest> filter) {
        return fetchBankRecs(filter, createBankRecProjection());
    }

    public List<Bankrec> fetchBankRecs(Consumer<BankrecsQueryRequest> filter, Consumer<BankrecResponseProjection> query) {
        return fetchBankRecs(BANKRECS_PER_PAGE, filter, query, f -> ErrorResponse.THROW, f -> true);
    }

    public List<Bankrec> fetchBankRecs(Consumer<BankrecsQueryRequest> filter, Consumer<BankrecResponseProjection> query, Predicate<Bankrec> recResults) {
        return fetchBankRecs(BANKRECS_PER_PAGE, filter, query, f -> ErrorResponse.THROW, recResults);
    }

    public List<Bankrec> fetchAllianceBankRecs(int allianceId, Consumer<AllianceBankrecsParametrizedInput> filter) {
        List<Alliance> alliance = fetchAlliances(f -> f.setId(List.of(allianceId)), new Consumer<AllianceResponseProjection>() {
            @Override
            public void accept(AllianceResponseProjection proj) {
                BankrecResponseProjection bankProj = new BankrecResponseProjection();
                createBankRecProjection().accept(bankProj);
                AllianceBankrecsParametrizedInput input = new AllianceBankrecsParametrizedInput();
                filter.accept(input);
                proj.id();
                proj.bankrecs(input, bankProj);
            }
        });
        if (alliance == null || alliance.size() == 0) {
            System.out.println("No results");
            return null;
        }
        return alliance.get(0).getBankrecs();
    }

    public List<Bankrec> fetchBankRecs(int perPage, Consumer<BankrecsQueryRequest> filter, Consumer<BankrecResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<Bankrec> recResults) {
        List<Bankrec> allResults = new ArrayList<>();

        handlePagination(PagePriority.API_BANK_RECS, page -> {
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
                projection.spies();

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

                projection.tax_id();
                projection.gross_national_income();
//                projection.gross_domestic_product();

                projection.wars_won();
                projection.wars_lost();

                projection.spies();
            }
        }, f -> PoliticsAndWarV3.ErrorResponse.THROW, nationResults);
    }

    public List<Nation> fetchNations(Consumer<NationsQueryRequest> filter, Consumer<NationResponseProjection> query) {
        return fetchNations(NATIONS_PER_PAGE, filter, query, f -> ErrorResponse.THROW, f -> true);
    }
    public List<Nation> fetchNations(int perPage, Consumer<NationsQueryRequest> filter, Consumer<NationResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<Nation> nationResults) {
        List<Nation> allResults = new ArrayList<>();

        handlePagination(PagePriority.API_NATIONS, page -> {
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

        handlePagination(PagePriority.API_ALLIANCES, page -> {
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

    public List<Treaty> fetchTreaties(int allianceId) {
        List<Alliance> alliances = fetchAlliances(f -> f.setId(List.of(allianceId)), new Consumer<AllianceResponseProjection>() {
            @Override
            public void accept(AllianceResponseProjection proj) {
                proj.id();
                proj.treaties(treatyResponseProjection());
            }
        });
        List<Treaty> result = new ArrayList<>();
        for (Alliance alliance : alliances) {
            if (alliance.getTreaties() != null) {
                result.addAll(alliance.getTreaties());
            }
        }
        return result;
    }

    public List<Treaty> fetchTreaties(Consumer<TreatiesQueryRequest> filter) {
        return fetchTreaties(filter, r -> {
            r.id();
            r.date();
            r.treaty_type();
            r.turns_left();
            r.alliance1_id();
            r.alliance2_id();
            r.approved();
        });
    }

    public List<Treaty> fetchTreaties(Consumer<TreatiesQueryRequest> filter, Consumer<TreatyResponseProjection> query) {
        return fetchTreaties(TREATIES_PER_PAGE, filter, query, f -> ErrorResponse.THROW, f -> true);
    }

    public List<Treaty> fetchTreaties(int perPage, Consumer<TreatiesQueryRequest> filter, Consumer<TreatyResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<Treaty> addEachResult) {
        List<Treaty> allResults = new ArrayList<>();

        handlePagination(PagePriority.API_TREATIES, page -> {
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

    public <T> T request(PagePriority priority, GraphQLOperationRequest request, GraphQLResponseProjection response, Class<T> resultBody) {
        return readTemplate(priority, new GraphQLRequest(request, response), resultBody);
    }

    public ApiKeyDetails getApiKeyStats() {
        MeQueryResponse result = request(PagePriority.API_KEY_STATS, new MeQueryRequest(), new ApiKeyDetailsResponseProjection()
                        .key()
                        .max_requests()
                        .requests()
                        .nation(new NationResponseProjection().id()),
                MeQueryResponse.class);
        if (result.me() == null) throw new GraphQLException("Error fetching api key");
        return result.me();
    }
    public Tradeprice getTradePrice() {
        List<Tradeprice> allResults = new ArrayList<>();

        handlePagination(PagePriority.API_TRADE_PRICE, page -> {
                    TradepricesQueryRequest request = new TradepricesQueryRequest();
                    request.setFirst(1);
                    request.setPage(page);

                    TradepriceResponseProjection proj = new TradepriceResponseProjection()
//                        .id()
                            .coal()
                            .oil()
                            .uranium()
                            .iron()
                            .bauxite()
                            .lead()
                            .gasoline()
                            .munitions()
                            .steel()
                            .aluminum()
                            .food()
                            .credits();

                    TradepricePaginatorResponseProjection natPagRespProj = new TradepricePaginatorResponseProjection()
                            .paginatorInfo(new PaginatorInfoResponseProjection()
                                    .hasMorePages())
                            .data(proj);

                    return new GraphQLRequest(request, natPagRespProj);
                }, f -> ErrorResponse.THROW, TradepricesQueryResponse.class,
                response -> {
//                    TradepricePaginator paginator = response.tradeprices();
//                    PaginatorInfo pageInfo = paginator != null ? paginator.getPaginatorInfo() : null;
                    return false;
                }, result -> {
                    TradepricePaginator paginator = result.tradeprices();
                    if (paginator != null) {
                        List<Tradeprice> results = paginator.getData();
                        for (Tradeprice price : results) {
                            allResults.add(price);
                        }
                    }
                });
        return allResults.get(0);
    }

    public GameInfo getGameInfo() {
        Game_infoQueryResponse result = request(PagePriority.API_GAME_TIME, new Game_infoQueryRequest(), new GameInfoResponseProjection()
                        .game_date()
                        .radiation(new RadiationResponseProjection().all$()),
                Game_infoQueryResponse.class);
        if (result.game_info() == null) throw new GraphQLException("Error fetching game info " + result.toString());
        return result.game_info();
    }

    public double[] getAllianceStockpile(int allianceId) {
        List<Alliance> alliances = fetchAlliances(f -> f.setId(List.of(allianceId)), new Consumer<AllianceResponseProjection>() {
            @Override
            public void accept(AllianceResponseProjection projection) {
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
        });
        if (alliances.size() == 0) {
            return null;
        }
        Alliance rec = alliances.get(0);
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
        return resources;
    }

    public Map<Integer, double[]> getStockPile(Consumer<NationsQueryRequest> query) {
        Map<Integer, double[]> result = new HashMap<>();
        for (Nation rec : fetchNations(query::accept, new Consumer<NationResponseProjection>() {
            @Override
            public void accept(NationResponseProjection projection) {
                projection.id();
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
            if (rec.getMoney() != null) {
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
        }
        return result;
    }

    public Bankrec transferFromBank(double[] amount, NationOrAlliance destination, String note) {
        return transferFromBank(amount, destination.getReceiverType(), destination.getId(), note);
    }

    public Bankrec transferFromBank(double[] amount, int receiverType, int receiverId, String note) {
        BankWithdrawMutationRequest mutation = new BankWithdrawMutationRequest();
        mutation.setMoney(amount[ResourceType.MONEY.ordinal()]);
        mutation.setCoal(amount[ResourceType.COAL.ordinal()]);
        mutation.setOil(amount[ResourceType.OIL.ordinal()]);
        mutation.setUranium(amount[ResourceType.URANIUM.ordinal()]);
        mutation.setIron(amount[ResourceType.IRON.ordinal()]);
        mutation.setBauxite(amount[ResourceType.BAUXITE.ordinal()]);
        mutation.setLead(amount[ResourceType.LEAD.ordinal()]);
        mutation.setGasoline(amount[ResourceType.GASOLINE.ordinal()]);
        mutation.setMunitions(amount[ResourceType.MUNITIONS.ordinal()]);
        mutation.setSteel(amount[ResourceType.STEEL.ordinal()]);
        mutation.setAluminum(amount[ResourceType.ALUMINUM.ordinal()]);
        mutation.setFood(amount[ResourceType.FOOD.ordinal()]);

        if (note != null) mutation.setNote(note);
        mutation.setReceiver_type(receiverType);
        mutation.setReceiver(receiverId);

        BankrecResponseProjection projection = new BankrecResponseProjection();
        projection.id();
        projection.date();

        return request(PagePriority.API_BANK_SEND, mutation, projection, Bankrec.class);
    }


    public Bankrec depositIntoBank(double[] amount, String note) {
        BankDepositMutationRequest mutation = new BankDepositMutationRequest();
        mutation.setMoney(amount[ResourceType.MONEY.ordinal()]);
        mutation.setCoal(amount[ResourceType.COAL.ordinal()]);
        mutation.setOil(amount[ResourceType.OIL.ordinal()]);
        mutation.setUranium(amount[ResourceType.URANIUM.ordinal()]);
        mutation.setIron(amount[ResourceType.IRON.ordinal()]);
        mutation.setBauxite(amount[ResourceType.BAUXITE.ordinal()]);
        mutation.setLead(amount[ResourceType.LEAD.ordinal()]);
        mutation.setGasoline(amount[ResourceType.GASOLINE.ordinal()]);
        mutation.setMunitions(amount[ResourceType.MUNITIONS.ordinal()]);
        mutation.setSteel(amount[ResourceType.STEEL.ordinal()]);
        mutation.setAluminum(amount[ResourceType.ALUMINUM.ordinal()]);
        mutation.setFood(amount[ResourceType.FOOD.ordinal()]);

        if (note != null) mutation.setNote(note);

        BankrecResponseProjection projection = new BankrecResponseProjection();
        projection.id();
        projection.date();

        return request(PagePriority.API_BANK_DEPOSIT, mutation, projection, Bankrec.class);
    }

    /**
     * Try sending an empty transaction
     * @return
     */
    public boolean testBotKey() {
        BankDepositMutationRequest mutation = new BankDepositMutationRequest();
        mutation.setNote("test 123");

        BankrecResponseProjection projection = new BankrecResponseProjection();
        projection.id();
        projection.date();

        try {
            request(PagePriority.API_BOT_KEY, mutation, projection, Bankrec.class);
            return false;
        } catch (RuntimeException e) {
            if (e.getMessage().contains("The bot key you provided is not valid.")) {
                throw new IllegalArgumentException(e.getMessage() + "\n- Please fill out <https://forms.gle/KbszjAfPVVz3DX9A7> and DM <@258298021266063360> to receive a working bot key");
            }
            if (e.getMessage().contains("The API key you provided does not allow whitelisted access.")) {
                throw new IllegalArgumentException(e.getMessage() + "\n- Please go to <https://politicsandwar.com/account/> and at the bottom enable `Whitelisted Access`");
            }
            if (!e.getMessage().contains("You can't deposit no resources.") && !e.getMessage().contains("You can't deposit resources while blockaded.")) {
                throw e;
            }
        }
        return true;
    }

    private TreatyResponseProjection treatyResponseProjection() {
        return new TreatyResponseProjection()
        .id()
        .alliance1_id()
        .alliance2_id()
        .treaty_type()
        .treaty_url()
        .approved()
        .date()
        .turns_left();
    }

    public Treaty approveTreaty(int id) {
        ApproveTreatyMutationRequest mutation = new ApproveTreatyMutationRequest();
        mutation.setId(id);
        return request(PagePriority.API_TREATY_APPROVE, mutation, treatyResponseProjection(), Treaty.class);
    }

    public Treaty cancelTreaty(int id) {
        CancelTreatyMutationRequest mutation = new CancelTreatyMutationRequest();
        mutation.setId(id);
        return request(PagePriority.API_TREATY_CANCEL, mutation, treatyResponseProjection(), Treaty.class);
    }

    public Treaty proposeTreaty(int alliance_id, int length, TreatyType type, String url) {
        ProposeTreatyMutationRequest mutation = new ProposeTreatyMutationRequest();
        mutation.setAlliance_id(alliance_id);
        mutation.setLength(length);
        mutation.setType(type.getId());
        mutation.setUrl(url);
        return request(PagePriority.API_TREATY_SEND, mutation, treatyResponseProjection(), Treaty.class);
    }

    private TaxBracketResponseProjection createTaxBracketProjection() {
        TaxBracketResponseProjection projection = new TaxBracketResponseProjection()
                .id()
                .alliance_id()
                .date()
                .date_modified()
                .last_modifier_id()
                .tax_rate()
                .resource_tax_rate()
                .bracket_name();
        return projection;
    }

    public TaxBracket assignTaxBracket(int taxId, int nationId) {
        AssignTaxBracketMutationRequest mutation = new AssignTaxBracketMutationRequest();
        mutation.setId(taxId);
        mutation.setTarget_id(nationId);
        return request(PagePriority.API_TAX_ASSIGN, mutation, createTaxBracketProjection(), TaxBracket.class);
    }

    public TaxBracket createTaxBracket(String name, Integer moneyRate, Integer rssRate) {
        CreateTaxBracketMutationRequest mutation = new CreateTaxBracketMutationRequest();
        mutation.setName(name);
        mutation.setMoney_tax_rate(moneyRate);
        mutation.setResource_tax_rate(rssRate);

        return request(PagePriority.API_TAX_CREATE, mutation, createTaxBracketProjection(), TaxBracket.class);
    }

    public void deleteTaxBracket(int id) {
        DeleteTaxBracketMutationRequest request = new DeleteTaxBracketMutationRequest();
        request.setId(id);
        request(PagePriority.API_TAX_DELETE, request, createTaxBracketProjection(), TaxBracket.class);
    }

    public TaxBracket editTaxBracket(int id, String name, Integer moneyRate, Integer rssRate) {
        EditTaxBracketMutationRequest mutation = new EditTaxBracketMutationRequest();
        mutation.setId(id);
        if (name != null) mutation.setName(name);
        if (moneyRate != null) mutation.setMoney_tax_rate(moneyRate);
        if (rssRate != null) mutation.setResource_tax_rate(rssRate);

        return request(PagePriority.API_TAX_EDIT, mutation, createTaxBracketProjection(), TaxBracket.class);
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

    public void iterateIdChunks(List<Integer> ids, int maxSize, Consumer<List<Integer>> subListConsumer) {
        ids = new ArrayList<>(ids);
        Collections.sort(ids);
        if (ids.size() <= maxSize) {
            subListConsumer.accept(ids);
        } else {
            for (int i = 0; i < ids.size(); i += maxSize) {
                List<Integer> subList = ids.subList(i, Math.min(i + maxSize, ids.size()));
                subListConsumer.accept(subList);
            }
        }
    }
    public List<Trade> fetchTradesWithInfo(Consumer<TradesQueryRequest> filter, Predicate<Trade> tradeResults) {
        return fetchTrades(TRADES_PER_PAGE, filter, new Consumer<TradeResponseProjection>() {
            @Override
            public void accept(TradeResponseProjection projection) {
                projection.id();
                projection.type();
                projection.date();
                projection.sender_id();
                projection.receiver_id();
                projection.offer_resource();
                projection.offer_amount();
                projection.buy_or_sell();
                projection.price();

                projection.date_accepted();
                projection.original_trade_id();
            }
        }, f -> PoliticsAndWarV3.ErrorResponse.THROW, tradeResults);
    }

    public List<Embargo> fetchEmbargoWithInfo(Consumer<EmbargoesQueryRequest> filter, Consumer<EmbargoResponseProjection> query, Predicate<Embargo> embargoResults) {
        List<Embargo> allResults = new ArrayList<>();

        handlePagination(PagePriority.API_EMBARGO_GET, page -> {
                    EmbargoesQueryRequest request = new EmbargoesQueryRequest();
                    if (filter != null) filter.accept(request);
                    request.setFirst(EMBARGO_PER_PAGE);
                    request.setPage(page);

                    EmbargoResponseProjection respProj = new EmbargoResponseProjection();
                    query.accept(respProj);

                    EmbargoPaginatorResponseProjection pagRespProj = new EmbargoPaginatorResponseProjection()
                            .paginatorInfo(new PaginatorInfoResponseProjection().hasMorePages())
                            .data(respProj);
                    return new GraphQLRequest(request, pagRespProj);
                }, f -> ErrorResponse.THROW, EmbargoesQueryResponse.class,
                response -> {
                    EmbargoPaginator paginator = response.embargoes();
                    PaginatorInfo pageInfo = paginator != null ? paginator.getPaginatorInfo() : null;
                    return pageInfo != null && pageInfo.getHasMorePages();
                }, result -> {
                    EmbargoPaginator paginator = result.embargoes();
                    if (paginator != null) {
                        List<Embargo> nations = paginator.getData();
                        for (Embargo elem : nations) {
                            if (embargoResults.test(elem)) allResults.add(elem);
                        }
                    }
                });

        return allResults;
    }

    public List<Trade> fetchTrades(int perPage, Consumer<TradesQueryRequest> filter, Consumer<TradeResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<Trade> tradeResults) {
        List<Trade> allResults = new ArrayList<>();

        handlePagination(PagePriority.API_TRADE_GET, page -> {
            TradesQueryRequest request = new TradesQueryRequest();
            if (filter != null) filter.accept(request);
            request.setFirst(perPage);
            request.setPage(page);

            TradeResponseProjection respProj = new TradeResponseProjection();
            query.accept(respProj);

            TradePaginatorResponseProjection pagRespProj = new TradePaginatorResponseProjection()
                    .paginatorInfo(new PaginatorInfoResponseProjection().hasMorePages())
                    .data(respProj);
            return new GraphQLRequest(request, pagRespProj);
        }, errorBehavior, TradesQueryResponse.class,
        response -> {
            TradePaginator paginator = response.trades();
            PaginatorInfo pageInfo = paginator != null ? paginator.getPaginatorInfo() : null;
            return pageInfo != null && pageInfo.getHasMorePages();
        }, result -> {
            TradePaginator paginator = result.trades();
            if (paginator != null) {
                List<Trade> nations = paginator.getData();
                for (Trade trade : nations) {
                    if (tradeResults.test(trade)) allResults.add(trade);
                }
            }
        });

        return allResults;
    }

    public List<Color> getColors() {
        ColorsQueryResponse result = request(PagePriority.API_COLOR_GET, new ColorsQueryRequest(), new ColorResponseProjection()
                        .color()
                        .bloc_name()
                        .turn_bonus(),
                ColorsQueryResponse.class);
        if (result.colors() == null) throw new GraphQLException("Error fetching colors");
        return result.colors();
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

        if (api != null && bot != null && !bot.isEmpty()) {
            headers.set("X-Api-Key", api);
        } else {
            headers.set("X-Api-Key", Settings.INSTANCE.API_KEY_PRIMARY);
        }
        if (bot != null && !bot.isEmpty()) {
            headers.set("X-Bot-Key", bot);
        } else {
            headers.set("X-Bot-Key", Settings.INSTANCE.ACCESS_KEY);
        }
        return headers;
    }
}
