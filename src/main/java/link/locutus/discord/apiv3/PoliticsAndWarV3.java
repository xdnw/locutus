package link.locutus.discord.apiv3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.base.CaseFormat;
import com.google.gson.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.config.Settings;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.util.*;
import com.kobylynskyi.graphql.codegen.model.graphql.*;
import com.politicsandwar.graphql.model.*;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import graphql.GraphQLException;
import link.locutus.discord.util.io.PagePriority;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
    public static int TREASURE_TRADES_PER_PAGE = 500;
    public static int ATTACKS_PER_PAGE = 1000;
    public static int WARS_PER_PAGE = 1000;
    public static int TRADES_PER_PAGE = 1000;
    public static int BOUNTIES_PER_PAGE = 1000;
    public static int BASEBALL_PER_PAGE = 1000;
    public static int EMBARGO_PER_PAGE = 1000;
    public static int BANS_PER_PAGE = 500;

    private final String endpoint;
    private final URI url;
    private final String snapshotUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper jacksonObjectMapper;
    private final ApiKeyPool pool;

    public PoliticsAndWarV3(String url, ApiKeyPool pool) {
        this.endpoint = url + "/graphql";
        this.snapshotUrl = url + "/subscriptions/v1/snapshot/";
        try {
            this.url = new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        this.restTemplate = new RestTemplate();
        this.pool = pool;

        this.jacksonObjectMapper = Jackson2ObjectMapperBuilder.json().simpleDateFormat("yyyy-MM-dd").build();
        jacksonObjectMapper.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS,true);
        jacksonObjectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        SimpleModule module = new SimpleModule();
        // Fix for snapshots returning Object instead of Array of CityInfraDamage
        module.addDeserializer(CityInfraDamage.class, (JsonDeserializer<CityInfraDamage>) (Object) new CityInfraDamageDeserializer());
        jacksonObjectMapper.registerModule(module);
    }

    public PoliticsAndWarV3(ApiKeyPool pool) {
        this("https://api" + (Settings.INSTANCE.TEST ? "-test" : "") + ".politicsandwar.com", pool);
    }

    public ApiKeyPool getPool() {
        return pool;
    }

    public String getUrl(String key) {
        return endpoint + "?api_key=" + key;
    }

    public String getSnapshotUrl(Class<?> type, String key) {
        String endpointName;
        endpointName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, type.getSimpleName());
        if (endpointName.equalsIgnoreCase("war_attack")) endpointName = "warattack";
        return snapshotUrl + endpointName + "?api_key=" + key;
    }

    public void throwInvalid(AlliancePermission alliancePermission, String message) {
        String validKeysStr;
        List<ApiKeyPool.ApiKey> keys = pool.getKeys();
        if (keys.isEmpty()) {
            validKeysStr = "No valid keys";
        } else {
            validKeysStr = keys.stream().map(key -> PW.getMarkdownUrl(key.getNationId(), false)).collect(Collectors.joining(","));
        }
        message = "Error accessing `" + alliancePermission.name() + "`" + (message == null || message.isEmpty() ? "" : " " + message) + ". Using keys: " + validKeysStr + "\n" +
                "API key scopes can be set on the account page: <" + Settings.PNW_URL() + "/account/>";
        throw new IllegalArgumentException(message);
    }

    public enum ErrorResponse {
        CONTINUE,
        RETRY,
        EXIT,
        THROW
    }

    private static class RateLimit {
        private final int defaultResetMs;
        private final int defaultLimit;

        // Amount of requests allowed per interval
        public volatile int limit;
        // The interval in milliseconds (typically 60_000)
        public volatile int intervalMs;
        // The number of ms until the ratelimit resets (unused)
//        public volatile long resetAfterMs_unused;
        // The remaining number of requests this interval
        public volatile int remaining;
        // the unix epoch time in milliseconds when the ratelimit resets
        public volatile long resetMs;

        public RateLimit(int resetMs, int limit) {
            this.defaultResetMs = resetMs;
            this.defaultLimit = limit;

            this.limit = limit;
            this.intervalMs = resetMs;
            this.remaining = limit;
            this.resetMs = System.currentTimeMillis() + resetMs;
        }

        public void reset(long now) {
            if (now > resetMs) {
                remaining = limit;
                long remainder = (now - resetMs) % intervalMs;
                resetMs = now + intervalMs - remainder;
            }
        }

        private void handleRateLimit(HttpHeaders header) {
            if (header == null) return;
            synchronized (this) {
                if (header.containsKey("X-RateLimit-Limit")) {
                    limit = Integer.parseInt(Objects.requireNonNull(header.get("X-RateLimit-Limit")).get(0));
                }
                if (header.containsKey("X-RateLimit-Remaining")) {
                    remaining = Integer.parseInt(Objects.requireNonNull(header.get("X-RateLimit-Remaining")).get(0));
                }
                if (header.containsKey("X-RateLimit-Reset")) {
                    resetMs = Long.parseLong(Objects.requireNonNull(header.get("X-RateLimit-Reset")).get(0)) * 1000L;
                } else if (header.containsKey("X-RateLimit-Reset-After")) {
                    long ms = Long.parseLong(Objects.requireNonNull(header.get("X-RateLimit-Reset-After")).get(0)) * 1000L;
                    resetMs = System.currentTimeMillis() + ms;
                }
                if (header.containsKey("X-RateLimit-Interval")) {
                    intervalMs = Integer.parseInt(Objects.requireNonNull(header.get("X-RateLimit-Interval")).get(0)) * 1000;
                }
            }
        }

        private void setRateLimit(long timeMs) {
            synchronized (this) {
                if (intervalMs == 0) {
                    intervalMs = defaultResetMs;
                }
                if (limit == 0) {
                    limit = defaultLimit;
                }
                long now = System.currentTimeMillis();
                remaining = 0;
                resetMs = now + timeMs;
                limit = 0;
            }
        }

        private void handleRateLimit() {
            if (intervalMs != 0) {
                synchronized (this) {
                    long now = System.currentTimeMillis();
                    reset(now);
                    if (remaining <= 0) {
                        long sleepMs = resetMs - now;
                        if (sleepMs > 0) {
                            try {
                                sleepMs = Math.min(sleepMs, defaultResetMs);
                                Logg.text("Pausing API requests to avoid being rate limited:\n" +
                                        "- Limit: " + limit + "\n" +
                                        "- Retry After: " + sleepMs + "ms");
                                Thread.sleep(sleepMs + 1000);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        now = System.currentTimeMillis();
                    }
                    reset(now);
                    remaining--;
                }
            }
        }

    }

    private static final RateLimit rateLimitGlobal = new RateLimit(60_000, 60);
    private static final RateLimit rateLimitSnapshot = new RateLimit(60_000 * 5, 10);
    private static RequestTracker requestTracker = new RequestTracker();

    public static RequestTracker getRequestTracker() {
        return requestTracker;
    }

    public <T extends Serializable> List<T> readSnapshot(PagePriority priority, Class<T> type) {
        String errorMsg;
        int backoff = 1;
        while (backoff++ < 8) {
            rateLimitSnapshot.handleRateLimit();

            errorMsg = null;
            ApiKeyPool.ApiKey pair = pool.getNextApiKey();
            String url = getSnapshotUrl(type, pair.getKey());
            String body = null;
            try {
                body = FileUtil.readStringFromURL(priority, url);
                // parse json
                if (body.contains("\"error\":")) {
                    JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
                    String errorRaw = obj.get("error").getAsString();
                    if (errorRaw.equalsIgnoreCase("rate-limited")) {
                        long sleepMs = 30_500;

                        JsonObject duration = obj.getAsJsonObject("duration");
                        if (duration != null) {
                            if (duration.has("nanos")) {
                                long nanos = duration.get("nanos").getAsLong();
                                sleepMs = Math.min(60_000, (nanos + 999_999) / 1_000_000);
                            } else if (duration.has("seconds")) {
                                long seconds = duration.get("seconds").getAsLong();
                                sleepMs = Math.min(60_000, seconds * 1000);
                            } else {
                                Logg.error("Unknown duration for API response: " + duration);
                            }
                        } else {
                            Logg.error("Unknown duration for API response (2): " + obj);
                        }

                        rateLimitSnapshot.setRateLimit(sleepMs);
                        Thread.sleep(sleepMs);
                        continue;
                    } else {
                        errorMsg = errorRaw;
                    }
                }
                if (errorMsg == null) {
                    return jacksonObjectMapper.readerForListOf(type).readValue(body);
                }
            } catch (IOException e) {
                errorMsg = e.getMessage();
                errorMsg = errorMsg == null ? "" : StringMan.stripApiKey(errorMsg);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (body != null && !errorMsg.contains("rate-limited") && !errorMsg.contains("database error") && !errorMsg.contains("couldn't find api key")) {
                Logg.text("Unknown error with APIv3 Snapshot response: " + errorMsg + "\n\n---START BODY\n\n" + body + "\n\n---END BODY---\n\n");
            }
            rethrow(new IllegalArgumentException(StringUtils.replaceIgnoreCase(errorMsg, pair.getKey(), "XXX")), pair, true);
        }
        throw new IllegalArgumentException("Failed to read snapshot");
    }

    private String getQueryStub(GraphQLRequest graphQLRequest) {
        String queryStr = graphQLRequest.toQueryString().split("\\{")[0];

//        String queryFull = graphQLRequest.toQueryString();
        // find index of first number
        int index = 0;
        while (index < queryStr.length() && !Character.isDigit(queryStr.charAt(index))) {
            index++;
        }
        String queryUrl = queryStr.substring(0, index);
        return queryUrl;
    }

    public <T> T readTemplate(PagePriority priority, boolean pagination, GraphQLRequest graphQLRequest, Class<T> resultBody) {
        int priorityId = priority.ordinal() + (pagination ? 1 : 0);
        ResponseEntity<String> exchange;
        String queryUrlStub = getQueryStub(graphQLRequest);

        int badKey = 0;
        int backOff = 1;
        while (backOff++ < 8) {
            rateLimitGlobal.handleRateLimit();

            ApiKeyPool.ApiKey pair = pool.getNextApiKey();
            String url = getUrl(pair.getKey());
            {
                requestTracker.addRequest(queryUrlStub, this.url);
            }
            restTemplate.acceptHeaderRequestCallback(String.class);
            HttpEntity<String> entity = httpEntity(graphQLRequest, pair.getKey(), pair.getBotKey());
            URI uri = URI.create(url);

            try {
                exchange =
                        FileUtil.submit(priorityId, priority.getAllowedBufferingMs(), priority.getAllowableDelayMs(), () -> restTemplate.exchange(uri,
                        HttpMethod.POST,
                        entity,
                        String.class), uri);
                rateLimitGlobal.handleRateLimit(exchange.getHeaders());

                String body = exchange.getBody();
                JsonNode json = jacksonObjectMapper.readTree(body);

                if (json.has("errors")) {
                    StringBuilder printToConsole = new StringBuilder("[GraphQL][" + priority + "] Error with " + graphQLRequest.getRequest());
                    printToConsole.append("\n\n---START BODY (2)---\n");
                    printToConsole.append(body);
                    printToConsole.append("\n\n---END BODY---\n");
                    Logg.text(printToConsole.toString());
                    JsonNode errors = json.get("errors");
                    List<String> errorMessages = new ObjectArrayList<>();
                    for (JsonNode error : errors) {
                        if (error.has("message")) {
                            errorMessages.add(error.get("message").toString());
                        }
                    }
                    String message = errorMessages.isEmpty() ? errors.toString() : StringMan.join(errorMessages, "\n");
                    message = StringMan.stripApiKey(message);
                    rethrow(new IllegalArgumentException(StringUtils.replaceIgnoreCase(message, pair.getKey(), "XXX")), pair, true);
                }

                FileUtil.setRateLimited(uri, false);
                return jacksonObjectMapper.readValue(body, resultBody);
            } catch (HttpClientErrorException.TooManyRequests e) {
                FileUtil.setRateLimited(uri, true);
                rateLimitGlobal.handleRateLimit(e.getResponseHeaders());
                try {
                    HttpHeaders headers = e.getResponseHeaders();
                    long timeout = (60000L);
                    String retryAfter = null;
                    if (headers != null) {
                        retryAfter = headers.getFirst("Retry-After");
                        timeout = retryAfter != null ? Math.min(60, Long.parseLong(retryAfter)) * 1000L : timeout;
                    }
                    Logg.text("Rate Limited On (3):\n" +
                            "- Request: " + graphQLRequest.getRequest() + "\n" +
                            "- Retry After: " + retryAfter);
                    Thread.sleep(timeout);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            } catch (HttpClientErrorException.Unauthorized e) {
                rateLimitGlobal.handleRateLimit(e.getResponseHeaders());
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
                FileUtil.setRateLimited(uri, true);
                rateLimitGlobal.handleRateLimit(e.getResponseHeaders());
                e.printStackTrace();
                AlertUtil.error(e.getMessage(), e);
                rethrow(e, pair, false);
                throw e;
            } catch (HttpServerErrorException.InternalServerError e) {
                FileUtil.setRateLimited(uri, true);
                rateLimitGlobal.handleRateLimit(e.getResponseHeaders());
                e.printStackTrace();
                String msg = "Error 500 thrown by " + endpoint + ". Is the game's API down?";
                throw HttpClientErrorException.create(msg, e.getStatusCode(), e.getStatusText(), e.getResponseHeaders(), e.getResponseBodyAsByteArray(), /* charset utf-8 */ StandardCharsets.UTF_8);
            } catch (JsonProcessingException e) {
                AlertUtil.error(e.getMessage(), e);
                rethrow(e, pair,true);
            } catch (Throwable e) {
                if (e instanceof RestClientResponseException rest) {
                    rateLimitGlobal.handleRateLimit(rest.getResponseHeaders());
                }
                boolean remove = false;
                if (e.getMessage().contains("The bot key you provided is not valid.")) {
                    pair.deleteApiKey();
                    remove = true;
                }
                if (e.getMessage().contains("The API key you provided does not allow whitelisted access.")) {
                    remove = true;
                }
                if (e.getMessage().contains("This API key cannot be used for this API endpoint, it will only work for API v3.")) {
                    pair.unsetMailKey();
                    remove = true;
                }
                if (remove && badKey++ < 4 && pool.size() > 1) {
                    if (remove) pool.removeKey(pair);
                    continue;
                }
                Logg.text("Error " + graphQLRequest.toHttpJsonBody() + "\n\n---START BODY (4)---\n\n" + e.getMessage() + "\n\n---END BODY---\n\n");
                rethrow(e, pair,false);
                throw e;
            }
        }
        throw new IllegalArgumentException("Failed to read template");
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
        List<BannedNation> allResults = new ObjectArrayList<>();
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

    public static class PageError extends RuntimeException {
        public PageError() {
            super("");
        }
        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    public <T extends GraphQLResult<?>> void handlePagination(PagePriority priority, Function<Integer, GraphQLRequest> requestFactory, Function<GraphQLError, ErrorResponse> errorBehavior, Class<T> resultBody, Predicate<T> hasMorePages, Consumer<T> onEachResult) {
        pageLoop:
        for (int page = 1; ; page++) {
            GraphQLRequest graphQLRequest = requestFactory.apply(page);

            for (int i = 0; i < 5; i++) {
                T result = readTemplate(priority, page > 1, graphQLRequest, resultBody);

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
                                long timeout = Math.min(60000, (long) (1000 + Math.pow(i * 1000, 2)));
                                Logg.text("Handle Rate Limit (backoff):\n" +
                                        "- Request: " + graphQLRequest.getRequest() + "\n" +
                                        "- Retry After: " + timeout + "ms" +
                                        "\n\n---\n\n" + errors + "\n\n---\n\n");
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

                try {
                    onEachResult.accept(result);
                } catch (PageError e) {
                    break;
                }
                if (!iterateNext) {
                    break pageLoop;
                }
                break;
            }
        }
    }

    public List<Bounty> fetchBountiesWithInfo() {
        return fetchBounties(f -> {}, proj -> {
            proj.id();
            proj.date();
            proj.amount();
            proj.nation_id();
            proj.type();
        });
    }

    public List<Bounty> fetchBounties(Consumer<BountiesQueryRequest> filter, Consumer<BountyResponseProjection> query) {
        return fetchBounties(1000, filter, query, f -> ErrorResponse.THROW, f -> true);
    }

    public List<Bounty> fetchBounties(int perPage, Consumer<BountiesQueryRequest> filter, Consumer<BountyResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<Bounty> recResults) {
        List<Bounty> allResults = new ObjectArrayList<>();

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
        TreasuresQueryResponse result = request(PagePriority.API_TREASURES, false, new TreasuresQueryRequest(), new TreasureResponseProjection()
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

    public List<NationResourceStat> getNationResourceStats(long date) {
        Nation_resource_statsQueryRequest request = new Nation_resource_statsQueryRequest();
        request.setAfter(new Date(date));
        request.setOrderBy(List.of(
                QueryNationResourceStatsOrderByOrderByClause.builder()
                        .setOrder(SortOrder.DESC)
                        .setColumn(QueryNationResourceStatsOrderByColumn.DATE)
                        .build()
        ));

        NationResourceStatResponseProjection respProj = new NationResourceStatResponseProjection();
        respProj.date();
        respProj.money();
        respProj.food();
        respProj.steel();
        respProj.aluminum();
        respProj.gasoline();
        respProj.munitions();
        respProj.uranium();
        respProj.coal();
        respProj.oil();
        respProj.iron();
        respProj.bauxite();
        respProj.lead();

        GraphQLRequest graphQLRequest = new GraphQLRequest(request, respProj);
        Nation_resource_statsQueryResponse result = readTemplate(PagePriority.API_ORBIS_METRICS, false, graphQLRequest, Nation_resource_statsQueryResponse.class);
        return result.nation_resource_stats();
    }

    public List<ResourceStat> getResourceStats(long date) {
        int perPage = 1000;
        List<ResourceStat> allResults = new ObjectArrayList<>();

        handlePagination(PagePriority.API_ORBIS_METRICS, page -> {
                    Resource_statsQueryRequest request = new Resource_statsQueryRequest();
                    request.setFirst(perPage);
                    request.setPage(page);
                    request.setAfter(new Date(date));

                    ResourceStatResponseProjection respProj = new ResourceStatResponseProjection();
                    respProj.date();
                    respProj.money();
                    respProj.food();
                    respProj.steel();
                    respProj.aluminum();
                    respProj.gasoline();
                    respProj.munitions();
                    respProj.uranium();
                    respProj.coal();
                    respProj.oil();
                    respProj.iron();
                    respProj.bauxite();
                    respProj.lead();

                    ResourceStatPaginatorResponseProjection pagRespProj = new ResourceStatPaginatorResponseProjection()
                            .paginatorInfo(new PaginatorInfoResponseProjection()
                                    .hasMorePages())
                            .data(respProj);

                    return new GraphQLRequest(request, pagRespProj);
                }, f -> ErrorResponse.EXIT, Resource_statsQueryResponse.class,
                response -> {
                    ResourceStatPaginator paginator = response.resource_stats();
                    PaginatorInfo pageInfo = paginator != null ? paginator.getPaginatorInfo() : null;
                    return pageInfo != null && pageInfo.getHasMorePages();
                }, result -> {
                    ResourceStatPaginator paginator = result.resource_stats();
                    if (paginator != null) {
                        List<ResourceStat> txs = paginator.getData();
                        if (!txs.isEmpty()) {
                            allResults.addAll(txs);
                            if (txs.get(txs.size() - 1).getDate().toEpochMilli() < date) {
                                throw new PageError();
                            }
                        }
                    }
                });

        return allResults;
    }

    public List<ActivityStat> getActivityStats(long date) {
        int perPage = 1000;
        List<ActivityStat> allResults = new ObjectArrayList<>();
        handlePagination(PagePriority.API_ORBIS_METRICS, page -> {
                    Activity_statsQueryRequest request = new Activity_statsQueryRequest();
                    request.setFirst(perPage);
                    request.setPage(page);
                    request.setAfter(new Date(date));

                    ActivityStatResponseProjection respProj = new ActivityStatResponseProjection();
                    respProj.date();
                    respProj.active_1_day();
                    respProj.active_2_days();
                    respProj.active_3_days();
                    respProj.active_1_week();
                    respProj.active_1_month();
                    respProj.nations_created();
                    respProj.total_nations();

                    ActivityStatPaginatorResponseProjection pagRespProj = new ActivityStatPaginatorResponseProjection()
                            .paginatorInfo(new PaginatorInfoResponseProjection()
                                    .hasMorePages())
                            .data(respProj);

                    return new GraphQLRequest(request, pagRespProj);
                }, f -> ErrorResponse.EXIT, Activity_statsQueryResponse.class,
                response -> {
                    ActivityStatPaginator paginator = response.activity_stats();
                    PaginatorInfo pageInfo = paginator != null ? paginator.getPaginatorInfo() : null;
                    return pageInfo != null && pageInfo.getHasMorePages();
                }, result -> {
                    ActivityStatPaginator paginator = result.activity_stats();
                    if (paginator != null) {
                        List<ActivityStat> txs = paginator.getData();
                        if (!txs.isEmpty()) {
                            allResults.addAll(txs);
                            if (txs.getLast().getDate().toEpochMilli() < date) {
                                throw new PageError();
                            }
                        }
                    }
                });

        return allResults;
    }

    public List<BBGame> fetchBaseballGames(int perPage, Consumer<Baseball_gamesQueryRequest> filter, Consumer<BBGameResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<BBGame> recResults) {
        List<BBGame> allResults = new ObjectArrayList<>();

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
        List<WarAttack> allResults = new ObjectArrayList<>();

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
        List<War> allResults = new ObjectArrayList<>();

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

    public List<City> fetchCitiesWithInfo(boolean priority, Consumer<CitiesQueryRequest> filter, boolean cityInfo) {
        return fetchCities(priority, filter, proj -> {
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

    public List<City> fetchCities(boolean priority, Consumer<CitiesQueryRequest> filter, Consumer<CityResponseProjection> query) {
        return fetchCities(priority, CITIES_PER_PAGE, filter, query, f -> ErrorResponse.THROW, f -> true);
    }

    public List<City> fetchCities(boolean priority, int perPage, Consumer<CitiesQueryRequest> filter, Consumer<CityResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<City> recResults) {
        List<City> allResults = new ObjectArrayList<>();

        handlePagination(priority ? PagePriority.API_CITIES_MANUAL : PagePriority.API_CITIES_AUTO, page -> {
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

    public List<Bankrec> fetchBankRecsWithInfo(boolean priority, Consumer<BankrecsQueryRequest> filter) {
        return fetchBankRecs(priority, filter, createBankRecProjection());
    }

    public List<Bankrec> fetchBankRecs(boolean priority, Consumer<BankrecsQueryRequest> filter, Consumer<BankrecResponseProjection> query) {
        return fetchBankRecs(priority, BANKRECS_PER_PAGE, filter, query, f -> ErrorResponse.THROW, f -> true);
    }

    public List<Bankrec> fetchBankRecs(boolean priority, Consumer<BankrecsQueryRequest> filter, Consumer<BankrecResponseProjection> query, Predicate<Bankrec> recResults) {
        return fetchBankRecs(priority, BANKRECS_PER_PAGE, filter, query, f -> ErrorResponse.THROW, recResults);
    }

    public List<Bankrec> fetchAllianceBankRecs(int allianceId, Consumer<AllianceBankrecsParametrizedInput> filter) {
        List<Alliance> alliance = fetchAlliances(PagePriority.API_BANK_RECS_MANUAL, ALLIANCES_PER_PAGE, f -> f.setId(List.of(allianceId)), new Consumer<AllianceResponseProjection>() {
            @Override
            public void accept(AllianceResponseProjection proj) {
                BankrecResponseProjection bankProj = new BankrecResponseProjection();
                createBankRecProjection().accept(bankProj);
                AllianceBankrecsParametrizedInput input = new AllianceBankrecsParametrizedInput();
                filter.accept(input);
                proj.id();
                proj.bankrecs(input, bankProj);
            }
        }, f -> ErrorResponse.THROW, f -> true);
        if (alliance == null || alliance.size() != 1) {
            return null;
        }
        return alliance.get(0).getBankrecs();
    }

    public List<Bankrec> fetchBankRecs(boolean priority, int perPage, Consumer<BankrecsQueryRequest> filter, Consumer<BankrecResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<Bankrec> recResults) {
        List<Bankrec> allResults = new ObjectArrayList<>();

        handlePagination(priority ? PagePriority.API_BANK_RECS_MANUAL : PagePriority.API_BANK_RECS_AUTO, page -> {
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
        List<Alliance> alliances = fetchAlliances(false, f -> f.setId(List.of(allianceId)), new Consumer<AllianceResponseProjection>() {
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
//        List<Bankrec> allResults = new ObjectArrayList<>();
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

    public List<Nation> fetchNationsWithInfo(boolean priority, Consumer<NationsQueryRequest> filter, Predicate<Nation> nationResults) {
        MilitaryResearchResponseProjection milResearch = new MilitaryResearchResponseProjection();
        milResearch.ground_capacity();
        milResearch.ground_cost();
        milResearch.air_capacity();
        milResearch.air_cost();
        milResearch.naval_capacity();
        milResearch.naval_cost();

        return fetchNations(priority, NATIONS_PER_PAGE, filter, new Consumer<NationResponseProjection>() {
            @Override
            public void accept(NationResponseProjection projection) {
                projection.id();

                projection.alliance_id();

                projection.nation_name(); // m
                projection.leader_name(); // m
                projection.spies();

                projection.discord();
                projection.discord_id();

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

                projection.cities_discount();

                projection.military_research(milResearch);

            }
        }, f -> PoliticsAndWarV3.ErrorResponse.THROW, nationResults);
    }

    public List<Nation> fetchNations(boolean priority, Consumer<NationsQueryRequest> filter, Consumer<NationResponseProjection> query) {
        return fetchNations(priority, NATIONS_PER_PAGE, filter, query, f -> ErrorResponse.THROW, f -> true);
    }
    public List<Nation> fetchNations(boolean priority, int perPage, Consumer<NationsQueryRequest> filter, Consumer<NationResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<Nation> nationResults) {
        List<Nation> allResults = new ObjectArrayList<>();

        handlePagination(priority ? PagePriority.API_NATIONS_MANUAL : PagePriority.API_NATIONS_AUTO, page -> {
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

    public List<TreasureTrade> fetchTreasureTrades(List<Integer> tradeIds) {
        return fetchTreasureTrades(f -> f.setId(tradeIds));
    }

    public List<TreasureTrade> fetchTreasureTrades(int minId) {
        return fetchTreasureTrades(f -> f.setMin_id(minId));
    }

    public List<TreasureTrade> fetchTreasureTrades(Consumer<Treasure_tradesQueryRequest> consumer) {
        List<TreasureTrade> allResults = new ObjectArrayList<>();

        handlePagination(PagePriority.API_TREASURE_TRADES, page -> {
            Treasure_tradesQueryRequest request = new Treasure_tradesQueryRequest();
            request.setFirst(TREASURE_TRADES_PER_PAGE);
            request.setPage(page);
            if (consumer != null) consumer.accept(request);

            TreasureTradeResponseProjection respProj = new TreasureTradeResponseProjection();
            respProj.id();
            respProj.offer_date();
            respProj.accept_date();
            respProj.sender_id();
            respProj.receiver_id();
            respProj.buying();
            respProj.selling();
            respProj.money();
            respProj.rejected();
            respProj.seller_cancelled();

            TreasureTradePaginatorResponseProjection pagRespProj = new TreasureTradePaginatorResponseProjection()
                    .paginatorInfo(new PaginatorInfoResponseProjection()
                            .hasMorePages())
                    .data(respProj);

            return new GraphQLRequest(request, pagRespProj);
        }, f -> PoliticsAndWarV3.ErrorResponse.THROW, Treasure_tradesQueryResponse.class,
                response -> {
                    TreasureTradePaginator paginator = response.treasure_trades();
                    PaginatorInfo pageInfo = paginator != null ? paginator.getPaginatorInfo() : null;
                    return pageInfo != null && pageInfo.getHasMorePages();
                }, result -> {
                    TreasureTradePaginator paginator = result.treasure_trades();
                    if (paginator != null) {
                        allResults.addAll(paginator.getData());
                    }
                });

        return allResults;
    }

    public List<Alliance> fetchAlliances(boolean priority, Consumer<AlliancesQueryRequest> filter, boolean positions, boolean allianceInfo) {
        return fetchAlliances(priority, ALLIANCES_PER_PAGE, filter, new Consumer<AllianceResponseProjection>() {
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

    public List<Alliance> fetchAlliances(boolean priority, Consumer<AlliancesQueryRequest> filter, Consumer<AllianceResponseProjection> query) {
        return fetchAlliances(priority, ALLIANCES_PER_PAGE, filter, query, f -> ErrorResponse.THROW, f -> true);
    }

    public List<Alliance> fetchAlliances(boolean priority, int perPage, Consumer<AlliancesQueryRequest> filter, Consumer<AllianceResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<Alliance> addEachResult) {
        return fetchAlliances(priority ? PagePriority.API_ALLIANCES_MANUAL : PagePriority.API_ALLIANCES_AUTO, perPage, filter, query, errorBehavior, addEachResult);
    }

    public List<Alliance> fetchAlliances(PagePriority priority, int perPage, Consumer<AlliancesQueryRequest> filter, Consumer<AllianceResponseProjection> query, Function<GraphQLError, ErrorResponse> errorBehavior, Predicate<Alliance> addEachResult) {
        List<Alliance> allResults = new ObjectArrayList<>();

        handlePagination(priority, page -> {
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
        List<Alliance> alliances = fetchAlliances(true, f -> f.setId(List.of(allianceId)), new Consumer<AllianceResponseProjection>() {
            @Override
            public void accept(AllianceResponseProjection proj) {
                proj.id();
                proj.treaties(treatyResponseProjection());
            }
        });
        List<Treaty> result = new ObjectArrayList<>();
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
        List<Treaty> allResults = new ObjectArrayList<>();

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

    public <T> T request(PagePriority priority, boolean pagination, GraphQLOperationRequest request, GraphQLResponseProjection response, Class<T> resultBody) {
        return readTemplate(priority, pagination, new GraphQLRequest(request, response), resultBody);
    }

    public ApiKeyDetails getApiKeyStats() {
        MeQueryResponse result = request(PagePriority.API_KEY_STATS, false, new MeQueryRequest(), new ApiKeyDetailsResponseProjection()
                        .key()
                        .max_requests()
                        .requests()
                        .permission_bits()
                        .nation(new NationResponseProjection().id()),
                MeQueryResponse.class);
        if (result.me() == null) throw new GraphQLException("Error fetching api key");
        return result.me();
    }

    public Tradeprice getTradePrice() {
        List<Tradeprice> allResults = new ObjectArrayList<>();

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
        Game_infoQueryResponse result = request(PagePriority.API_GAME_TIME, false, new Game_infoQueryRequest(), new GameInfoResponseProjection()
                        .game_date()
                        .city_average()
                        .radiation(new RadiationResponseProjection().all$()),
                Game_infoQueryResponse.class);
        if (result.game_info() == null) throw new GraphQLException("Error fetching game info " + result.toString());
        return result.game_info();
    }

    public double[] getAllianceStockpile(int allianceId) {
        List<Alliance> alliances = fetchAlliances(true, f -> f.setId(List.of(allianceId)), new Consumer<AllianceResponseProjection>() {
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
        if (rec.getMoney() == null) {
            return null;
        }
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
        Map<Integer, double[]> result = new Int2ObjectOpenHashMap<>();
        for (Nation rec : fetchNations(true, query::accept, new Consumer<NationResponseProjection>() {
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
        createBankRecProjection().accept(projection);

        return request(PagePriority.API_BANK_SEND, false, mutation, projection, BankWithdrawMutationResponse.class).bankWithdraw();
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
        createBankRecProjection().accept(projection);
        projection.id();
        projection.date();
        projection.receiver_id();
        projection.receiver_type();


        return request(PagePriority.API_BANK_DEPOSIT, false, mutation, projection, BankDepositMutationResponse.class).bankDeposit();
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
            BankDepositMutationResponse response = request(PagePriority.API_BOT_KEY, false, mutation, projection, BankDepositMutationResponse.class);
            return false;
        } catch (RuntimeException e) {
            if (e.getMessage().contains("The bot key you provided is not valid.")) {
                throw new IllegalArgumentException(e.getMessage() + "\n- Please fill out <https://forms.gle/KbszjAfPVVz3DX9A7> and DM <@258298021266063360> to receive a working bot key");
            }
            if (e.getMessage().contains("The API key you provided does not allow whitelisted access.")) {
                throw new IllegalArgumentException(e.getMessage() + "\n- Please go to <" + Settings.PNW_URL() + "/account/> and at the bottom enable `Whitelisted Access`");
            }
            if (!e.getMessage().contains("You can't deposit no resources.") && !e.getMessage().contains("You can't deposit resources while blockaded.")) {
                throw e;
            }
        }
        return true;
    }

    private TradeResponseProjection tradeResponseProjection() {
        return new TradeResponseProjection()
                .accepted()
                .date()
                .id()
                .buy_or_sell()
                .date_accepted()
                .offer_amount()
                .price()
                .receiver_id()
                .sender_id()
                .offer_resource()
                .type();
    }

    public Trade acceptPersonalTrade(int id, int amount) {
        AcceptPersonalTradeMutationRequest mutation = new AcceptPersonalTradeMutationRequest();
        mutation.setId(id);
        mutation.setOffer_amount(amount);
        return request(PagePriority.BANK_TRADE, false, mutation, tradeResponseProjection(), AcceptPersonalTradeMutationResponse.class).acceptPersonalTrade();
    }

    public AlliancePosition assignAlliancePosition(int nation_id, int position_id) {
        AssignAlliancePositionMutationRequest mutation = new AssignAlliancePositionMutationRequest();
        mutation.setId(nation_id);
        mutation.setPosition_id(position_id);
        AlliancePositionResponseProjection projection = new AlliancePositionResponseProjection().id();
        return request(PagePriority.RANK_SET, false, mutation, projection, AssignAlliancePositionMutationResponse.class).assignAlliancePosition();
    }

    public AlliancePosition assignAlliancePosition(int nation_id, Rank rank) {
        DefaultAlliancePosition v3 = rank.toV3();
        AssignAlliancePositionMutationRequest mutation = new AssignAlliancePositionMutationRequest();
        mutation.setId(nation_id);
        mutation.setDefault_position(v3);
        AlliancePositionResponseProjection projection = new AlliancePositionResponseProjection().id();
        return request(PagePriority.RANK_SET, false, mutation, projection, AssignAlliancePositionMutationResponse.class).assignAlliancePosition();
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
        return request(PagePriority.API_TREATY_APPROVE, false, mutation, treatyResponseProjection(), ApproveTreatyMutationResponse.class).approveTreaty();
    }

    public Treaty cancelTreaty(int id) {
        CancelTreatyMutationRequest mutation = new CancelTreatyMutationRequest();
        mutation.setId(id);
        return request(PagePriority.API_TREATY_CANCEL, false, mutation, treatyResponseProjection(), CancelTreatyMutationResponse.class).cancelTreaty();
    }

    public Treaty proposeTreaty(int alliance_id, int length, TreatyType type, String url) {
        ProposeTreatyMutationRequest mutation = new ProposeTreatyMutationRequest();
        mutation.setAlliance_id(alliance_id);
        mutation.setLength(length);
        mutation.setType(type.getId());
        mutation.setUrl(url);
        return request(PagePriority.API_TREATY_SEND, false, mutation, treatyResponseProjection(), ProposeTreatyMutationResponse.class).proposeTreaty();
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
        return request(PagePriority.API_TAX_ASSIGN, false, mutation, createTaxBracketProjection(), AssignTaxBracketMutationResponse.class).assignTaxBracket();
    }

    public TaxBracket createTaxBracket(String name, Integer moneyRate, Integer rssRate) {
        CreateTaxBracketMutationRequest mutation = new CreateTaxBracketMutationRequest();
        mutation.setName(name);
        mutation.setMoney_tax_rate(moneyRate);
        mutation.setResource_tax_rate(rssRate);

        return request(PagePriority.API_TAX_CREATE, false, mutation, createTaxBracketProjection(), CreateTaxBracketMutationResponse.class).createTaxBracket();
    }

    public void deleteTaxBracket(int id) {
        DeleteTaxBracketMutationRequest request = new DeleteTaxBracketMutationRequest();
        request.setId(id);
        request(PagePriority.API_TAX_DELETE, false, request, createTaxBracketProjection(), DeleteTaxBracketMutationResponse.class).deleteTaxBracket();
    }

    public TaxBracket editTaxBracket(int id, String name, Integer moneyRate, Integer rssRate) {
        EditTaxBracketMutationRequest mutation = new EditTaxBracketMutationRequest();
        mutation.setId(id);
        if (name != null) mutation.setName(name);
        if (moneyRate != null) mutation.setMoney_tax_rate(moneyRate);
        if (rssRate != null) mutation.setResource_tax_rate(rssRate);

        return request(PagePriority.API_TAX_EDIT, false, mutation, createTaxBracketProjection(), EditTaxBracketMutationResponse.class).editTaxBracket();
    }

    public Map<Integer, TaxBracket> fetchTaxBrackets(int allianceId, boolean priority) {
        Map<Integer, TaxBracket> taxBracketMap = new Int2ObjectOpenHashMap<>();
        List<Alliance> alliances = fetchAlliances(priority, f -> f.setId(List.of(allianceId)), new Consumer<AllianceResponseProjection>() {
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

    public List<Trade> fetchPrivateTrades(int nationId) {
        List<Nation> result = fetchNations(true, new Consumer<NationsQueryRequest>() {
            @Override
            public void accept(NationsQueryRequest r) {
                r.setId(List.of(nationId));
            }
        }, new Consumer<NationResponseProjection>() {
            @Override
            public void accept(NationResponseProjection r) {
                TradeResponseProjection projection = new TradeResponseProjection();
                tradeRespose(projection);
                NationTradesParametrizedInput params = new NationTradesParametrizedInput();
                params.accepted(false);
                params.type(TradeType.PERSONAL);
                r.trades(params, projection);
            }
        });
        if (result.size() != 1) return Collections.emptyList();
        return result.get(0).getTrades();
    }

    private void tradeRespose(TradeResponseProjection projection) {
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

    public List<Trade> fetchTradesWithInfo(Consumer<TradesQueryRequest> filter, Predicate<Trade> tradeResults) {
        return fetchTrades(TRADES_PER_PAGE, filter, this::tradeRespose, f -> PoliticsAndWarV3.ErrorResponse.THROW, tradeResults);
    }

    public List<Embargo> fetchEmbargoWithInfo(Consumer<EmbargoesQueryRequest> filter, Consumer<EmbargoResponseProjection> query, Predicate<Embargo> embargoResults) {
        List<Embargo> allResults = new ObjectArrayList<>();

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
        List<Trade> allResults = new ObjectArrayList<>();

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
        ColorsQueryResponse result = request(PagePriority.API_COLOR_GET, false, new ColorsQueryRequest(), new ColorResponseProjection()
                        .color()
                        .bloc_name()
                        .turn_bonus(),
                ColorsQueryResponse.class);
        if (result.colors() == null) throw new GraphQLException("Error fetching colors");
        return result.colors();
    }

    public List<Nation> fetchNationActive(boolean priority, List<Integer> ids) {
        return fetchNations(priority, f -> {
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
            headers.set("X-Api-Key", Locutus.loader().getApiKey());
        }
        if (bot != null && !bot.isEmpty()) {
            headers.set("X-Bot-Key", bot);
        } else {
            headers.set("X-Bot-Key", Settings.INSTANCE.ACCESS_KEY);
        }
        return headers;
    }
}
