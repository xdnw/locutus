package com.boydti.discord.apiv3;

import com.boydti.discord.Locutus;
import com.boydti.discord.apiv1.entities.ApiRecord;
import com.boydti.discord.config.Settings;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.StringMan;
import com.kobylynskyi.graphql.codegen.model.graphql.*;
import com.politicsandwar.graphql.model.*;
import com.boydti.discord.apiv1.core.ApiKeyPool;
import com.boydti.discord.apiv1.enums.NationColor;
import graphql.GraphQLException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.security.auth.login.LoginException;
import java.net.URI;
import java.sql.SQLException;
import java.text.ParseException;
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

    public List<Bankrec> fetchBankRecs(Consumer<BankrecsQueryRequest> filter, Consumer<BankrecResponseProjection> query) {
        return fetchBankRecs(1000, filter, query, f -> ErrorResponse.EXIT, f -> true);
    }


    public List<Bankrec> fetchBankRecs(int perPage, Consumer<BankrecsQueryRequest> filter, Consumer<BankrecResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<Bankrec> recResults) {
        List<Bankrec> allResults = new LinkedList<>();

        handlePagination(page -> {
                    BankrecsQueryRequest request = new BankrecsQueryRequest();
                    filter.accept(request);
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
            filter.accept(request);
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
                    filter.accept(request);
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

    public static void main(String[] args) throws ParseException, LoginException, InterruptedException, SQLException, ClassNotFoundException {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        Settings.INSTANCE.ENABLED_COMPONENTS.disableListeners();
        Settings.INSTANCE.ENABLED_COMPONENTS.disableTasks();
//        Settings.INSTANCE.ENABLED_COMPONENTS.DISCORD_BOT = false;
        Locutus.create().start();

        System.out.println("Nation " + Settings.INSTANCE.NATION_ID);
        System.out.println("ADMIN_USER_ID " + Settings.INSTANCE.ADMIN_USER_ID);
        System.out.println("APPLICATION_ID " + Settings.INSTANCE.APPLICATION_ID);

        String endpoint = "https://api" + (Settings.INSTANCE.TEST ? "-test" : "") + ".politicsandwar.com/graphql";
        ApiKeyPool pool = new ApiKeyPool(Settings.INSTANCE.API_KEY_PRIMARY);

        PoliticsAndWarV3 main = new PoliticsAndWarV3(endpoint, pool);
        {
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
