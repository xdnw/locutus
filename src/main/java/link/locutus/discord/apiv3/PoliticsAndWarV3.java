package link.locutus.discord.apiv3;

import com.ptsmods.mysqlw.query.QueryCondition;
import com.ptsmods.mysqlw.query.builder.SelectBuilder;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.WarAttacks;
import link.locutus.discord.apiv2.PoliticsAndWarV2;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationMetricDouble;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.BaseballDB;
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
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class PoliticsAndWarV3 {
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

    public <T> T readTemplate(GraphQLRequest graphQLRequest, Class<T> resultBody) {
        ResponseEntity<T> exchange = null;

        int j = 0;
        while (true) {
            String key = pool.getNextApiKey();
            String url = getUrl(key);
            try {
                exchange = restTemplate.exchange(URI.create(url),
                        HttpMethod.POST,
                        httpEntity(graphQLRequest),
                        resultBody);
                break;
            } catch (HttpClientErrorException.Unauthorized e) {
                if (j++ > 4) {
                    e.printStackTrace();
                    throw e;
                }
                pool.removeKey(key);
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
        List<Bounty> allResults = new LinkedList<>();

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
        return fetchBaseballGames(1000, filter, query, f -> ErrorResponse.EXIT, f -> true);
    }

    public List<BBGame> fetchBaseballGames(int perPage, Consumer<Baseball_gamesQueryRequest> filter, Consumer<BBGameResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<BBGame> recResults) {
        List<BBGame> allResults = new LinkedList<>();

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

    public List<WarAttack> fetchAttacks(Consumer<WarattacksQueryRequest> filter, Consumer<WarAttackResponseProjection> query) {
        return fetchAttacks(1000, filter, query, f -> ErrorResponse.EXIT, f -> true);
    }

    public List<WarAttack> fetchAttacks(int perPage, Consumer<WarattacksQueryRequest> filter, Consumer<WarAttackResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<WarAttack> recResults) {
        List<WarAttack> allResults = new LinkedList<>();

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

    public List<Bankrec> fetchBankRecs(Consumer<BankrecsQueryRequest> filter, Consumer<BankrecResponseProjection> query) {
        return fetchBankRecs(1000, filter, query, f -> ErrorResponse.EXIT, f -> true);
    }

    public List<Bankrec> fetchBankRecs(int perPage, Consumer<BankrecsQueryRequest> filter, Consumer<BankrecResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<Bankrec> recResults) {
        List<Bankrec> allResults = new LinkedList<>();

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
//        List<Bankrec> allResults = new LinkedList<>();
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

    public List<Nation> fetchNations(Consumer<NationsQueryRequest> filter, Consumer<NationResponseProjection> query) {
        return fetchNations(500, filter, query, f -> ErrorResponse.EXIT, f -> true);
    }


    public List<Nation> fetchNations(int perPage, Consumer<NationsQueryRequest> filter, Consumer<NationResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<Nation> nationResults) {
        List<Nation> allResults = new LinkedList<>();

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

    public List<Alliance> fetchAlliances(Consumer<AlliancesQueryRequest> filter, Consumer<AllianceResponseProjection> query) {
        return fetchAlliances(100, filter, query, f -> ErrorResponse.EXIT, f -> true);
    }

    public List<Alliance> fetchAlliances(int perPage, Consumer<AlliancesQueryRequest> filter, Consumer<AllianceResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<Alliance> addEachResult) {
        List<Alliance> allResults = new LinkedList<>();

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

        BaseballDB bbDb = Locutus.imp().getBaseballDB();
        int max = bbDb.getMaxGameId();
        int min = bbDb.getMinGameId();
//        bbDb.updateGames(false, true,null, null);
//        BaseballDB bbDb = Locutus.imp().getBaseballDB();//.updateGames(false);

        List<BBGame> games = bbDb.getBaseballGames(f -> f.where(QueryCondition.greater("wager", 0)));

        Map<Integer, Integer> mostWageredGames = new HashMap<>();
        Map<Integer, Integer> mostWageredWinnings = new HashMap<>();

        for (BBGame game : games) {
            mostWageredGames.merge(game.getHome_nation_id(), 1, Integer::sum);
            mostWageredGames.merge(game.getAway_nation_id(), 1, Integer::sum);
            if (game.getHome_score() > game.getAway_score()) {
                mostWageredWinnings.merge(game.getHome_nation_id(), game.getWager().intValue(), Integer::sum);
            } else if (game.getAway_score() > game.getHome_score()) {
                mostWageredWinnings.merge(game.getAway_nation_id(), game.getWager().intValue(), Integer::sum);
            }
        }

        System.exit(1);

        PoliticsAndWarV3 main = new PoliticsAndWarV3(pool);
        {
            for (Bounty bounty : main.fetchBounties(null, f -> f.all$(-1))) {
                System.out.println(bounty);
            }

        }

        System.exit(1);
        {
            Set<Integer> alliances = new HashSet<>();
//            for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
//
//            }
//
            main.fetchNations(new Consumer<NationsQueryRequest>() {
                @Override
                public void accept(NationsQueryRequest request) {
                    request.setAlliance_id(Arrays.asList(3427,9829,8777,4397,1742,2510,5039,9618,9620,8535,8280,5049,7484,7580,9793,9465,5722));
                    request.setAlliance_position(Arrays.asList(2, 3, 4, 5));
                    request.setMin_cities(10);
                    request.setVmode(false);
                }
            }, new Consumer<NationResponseProjection>() {
                @Override
                public void accept(NationResponseProjection projection) {
                    projection.wars_won();
                    projection.wars_lost();

                    projection.gross_domestic_product();
                    CityResponseProjection cityProj = new CityResponseProjection();
                }
            });
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
                projection.recipient_id();
                projection.recipient_type();
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
