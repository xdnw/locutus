package link.locutus.discord.web.jooby;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.javalin.http.Context;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.SimpleValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.command.StringMessageIO;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.gpt.mcp.MCPUtil;
import link.locutus.discord.gpt.mcp.SchemaBindings;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.mcp.MCPCommands;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.Locale;
import java.util.function.Supplier;

public class MCPHandler {
    private static final String CONTRACT_VERSION = "2026-02-24";
    public static final int DEFAULT_PAGE_LIMIT = 25;
    public static final int MAX_PAGE_LIMIT = 250;
    private static final int INLINE_RESULT_MAX_BYTES = 120_000;
    private static final long RESULT_REF_TTL_MILLIS = TimeUnit.MINUTES.toMillis(20);
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String EVENT_STREAM_CONTENT_TYPE = "text/event-stream";
    private static final String MCP_SESSION_HEADER = "Mcp-Session-Id";
    private static final Set<String> MCP_ENVELOPE_PARAM_KEYS = Set.of("_meta", "task");

    @FunctionalInterface
    public interface LocalStoreFactory extends BiFunction<LocalValueStore<?>, Context, LocalValueStore<?>> {
    }

    private final ValueStore<Object> store;
    private final ValueStore<Object> htmlOptionStore;
    private final ValueStore<Object> schemaStore;
    private final CommandGroup toolCommands;
    private final CommandGroup rpcCommands;
    private final ValidatorStore validators;
    private final PermissionHandler permisser;
    private final LocalStoreFactory localStoreFactory;
    private final Supplier<MCPUtil.SchemaDebugObserver> schemaDebugObserverFactory;

    private final Map<UUID, ResultRefRecord> resultRefs = new ConcurrentHashMap<>();

    private volatile Map<String, Object> toolsListSchema;
    private final Object toolCacheLock = new Object();

    public MCPHandler(
            ValueStore<Object> store,
            ValueStore<Object> htmlOptionStore,
            ValidatorStore validators,
            PermissionHandler permisser,
            LocalStoreFactory localStoreFactory,
            boolean registerDefaultToolCommands,
            Supplier<MCPUtil.SchemaDebugObserver> schemaDebugObserverFactory
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.htmlOptionStore = Objects.requireNonNull(htmlOptionStore, "htmlOptionStore");
        this.validators = Objects.requireNonNull(validators, "validators");
        this.permisser = Objects.requireNonNull(permisser, "permisser");
        this.localStoreFactory = Objects.requireNonNull(localStoreFactory, "localStoreFactory");
        this.schemaDebugObserverFactory = schemaDebugObserverFactory;

        this.schemaStore = new SimpleValueStore<>();
        new SchemaBindings().register(schemaStore);

        this.toolCommands = CommandGroup.createRoot(store, validators);
        if (registerDefaultToolCommands) {
            this.toolCommands.registerCommands(new MCPCommands(this));
        }

        this.rpcCommands = CommandGroup.createRoot(store, validators);
        RpcCommands rpc = new RpcCommands();
        this.rpcCommands.registerMethod(rpc, List.of(), "initialize", "initialize");
        this.rpcCommands.registerMethod(rpc, List.of("notifications"), "notifications_initialized", "initialized");
        this.rpcCommands.registerMethod(rpc, List.of("tools"), "tools_list", "list");
        this.rpcCommands.registerMethod(rpc, List.of("tools"), "tools_call", "call");
    }

    public void registerToolCommands(Object toolCommandGroup) {
        toolCommands.registerCommands(toolCommandGroup);
        synchronized (toolCacheLock) {
            toolsListSchema = null;
        }
    }

    public void handleHttpRpcPost(Context ctx) {
        if (!"POST".equalsIgnoreCase(ctx.req().getMethod())) {
            respondMethodNotAllowed(ctx, "Use POST for JSON-RPC requests");
            return;
        }

        RpcTransportResult transportResult = processHttpRpcRequest(ctx);
        ctx.contentType(JSON_CONTENT_TYPE);
        if (transportResult.noImmediateBodyResponse()) {
            ctx.status(202);
            return;
        }
        ctx.status(transportResult.statusCode()).result(WebUtil.GSON.toJson(transportResult.payload()));
    }

    public void handleHttpSessionGet(Context ctx) {
        if (!"GET".equalsIgnoreCase(ctx.req().getMethod())) {
            respondMethodNotAllowed(ctx, "Use GET for MCP session stream establishment");
            return;
        }

        String accept = ctx.header("Accept");
        if (accept != null && !accept.contains(EVENT_STREAM_CONTENT_TYPE) && !accept.contains("*/*")) {
            ctx.status(406)
                    .contentType(JSON_CONTENT_TYPE)
                    .result(WebUtil.GSON.toJson(Map.of(
                            "error", "Not Acceptable",
                            "message", "GET /mcp requires Accept: text/event-stream"
                    )));
            return;
        }

        String sessionId = normalizeSessionId(ctx.header(MCP_SESSION_HEADER));
        ctx.status(200);
        ctx.contentType(EVENT_STREAM_CONTENT_TYPE);
        ctx.header("Cache-Control", "no-cache");
        ctx.header("Connection", "keep-alive");
        ctx.header("X-Accel-Buffering", "no");
        ctx.header(MCP_SESSION_HEADER, sessionId);

        try {
            // Streamable HTTP session bootstrap event.
            ctx.res().getOutputStream().write((
                    "event: session\n" +
                            "data: " + WebUtil.GSON.toJson(Map.of(
                            "session_id", sessionId,
                            "transport", "streamable-http",
                            "endpoint", "/mcp"
                    )) + "\n\n"
            ).getBytes(StandardCharsets.UTF_8));
            ctx.res().getOutputStream().flush();
        } catch (IOException e) {
            throw new RuntimeException("Unable to open MCP session stream", e);
        }
    }

    public void handleUnsupportedMethod(Context ctx) {
        respondMethodNotAllowed(ctx, "Supported methods for /mcp are GET and POST");
    }

    public Set<ParametricCallable<?>> getToolCallables() {
        return toolCommands.getParametricCallables(callable -> true);
    }

    public ParametricCallable<?> resolveTool(String requestedName) {
        String normalized = MCPUtil.canonicalToolName(requestedName);
        List<String> path = MCPUtil.getToolPathTokens(requestedName);
        if (path.isEmpty()) {
            return null;
        }

        CommandCallable callable;
        try {
            callable = toolCommands.getCallable(new ArrayList<>(path));
        } catch (IllegalArgumentException e) {
            return null;
        }

        if (!(callable instanceof ParametricCallable<?> parametric)) {
            return null;
        }
        return MCPUtil.getToolName(parametric).equals(normalized) ? parametric : null;
    }

    private ParametricCallable<?> resolveRpcMethod(String methodName) {
        if (methodName == null || methodName.isBlank()) {
            return null;
        }

        List<String> path = Arrays.stream(methodName.toLowerCase(Locale.ROOT).trim().split("/"))
                .filter(part -> part != null && !part.isBlank())
                .toList();
        if (path.isEmpty()) {
            return null;
        }

        CommandCallable callable;
        try {
            callable = rpcCommands.getCallable(new ArrayList<>(path));
        } catch (IllegalArgumentException e) {
            return null;
        }

        return callable instanceof ParametricCallable<?> parametric ? parametric : null;
    }

    public LocalValueStore<?> createLocals(Context ctx) {
        return localStoreFactory.apply(null, ctx);
    }

    public ParsedCommand parseCommand(ParametricCallable<?> callable, Map<String, Object> arguments, Context ctx) {
        LocalValueStore<?> locals = createLocals(ctx);
        callable.validatePermissions(locals, permisser);
        Object[] parsed = callable.parseArgumentMap2(arguments, locals, validators, permisser, true);

        Map<String, Object> normalized = new LinkedHashMap<>();
        List<ParameterData> params = callable.getUserParameters();
        for (int i = 0; i < params.size(); i++) {
            Object value = i < parsed.length ? parsed[i] : null;
            normalized.put(params.get(i).getName(), StringMan.toSerializable(value));
        }
        return new ParsedCommand(locals, parsed, normalized);
    }

    public Map<String, Object> executeParsed(ParametricCallable<?> callable, ParsedCommand parsed) {
        Guild guild = (Guild) parsed.locals().getProvided(Key.of(Guild.class, Me.class), false);
        User user = (User) parsed.locals().getProvided(Key.of(User.class, Me.class), false);

        StringMessageIO io = new StringMessageIO(user, guild);
        parsed.locals().addProvider(Key.of(IMessageIO.class, Me.class), io);
        Object result = callable.call(null, parsed.locals(), parsed.arguments());
        return io.toMcpToolResult(result);
    }

    private <T> T parseAndInvoke(Context ctx,
                                 ParametricCallable<?> callable,
                                 Map<String, Object> arguments,
                                 ParsedCommandInvoker<T> invoker) {
        ParsedCommand parsed = parseCommand(callable, arguments == null ? Collections.emptyMap() : arguments, ctx);
        return invoker.invoke(callable, parsed);
    }

    public Object maybeStoreLargeResult(Object payload, String contentType) {
        String json = WebUtil.GSON.toJson(payload);
        if (json.length() <= INLINE_RESULT_MAX_BYTES) {
            return payload;
        }

        UUID resultRef = UUID.randomUUID();
        int estimatedSize = json.length();
        long expiresAt = System.currentTimeMillis() + RESULT_REF_TTL_MILLIS;
        resultRefs.put(resultRef, new ResultRefRecord(payload, contentType, estimatedSize, expiresAt));
        return new ResultRefEnvelope(resultRef, isoMillis(expiresAt), contentType, estimatedSize, new PageInfo(0, 0, 0, 0, null));
    }

    public Object getResultRef(UUID resultRef, int cursor, int limit) {
        ResultRefRecord record = resultRefs.get(resultRef);
        if (record == null || record.expiresAt < System.currentTimeMillis()) {
            resultRefs.remove(resultRef);
            throw new IllegalArgumentException("Unknown or expired result_ref: " + resultRef);
        }

        Map<String, Object> pagedShape = toPagedShape(record.payload);
        if (pagedShape != null && pagedShape.get("rows") instanceof List<?> rowsAny) {
            List<?> rows = rowsAny;
            int from = Math.min(cursor, rows.size());
            int to = Math.min(from + limit, rows.size());

            Map<String, Object> page = new LinkedHashMap<>();
            page.putAll(pagedShape);
            page.put("rows", new ArrayList<>(rows.subList(from, to)));
            page.put("page_info", pageInfo(from, to, rows.size(), limit));
            page.put("result_ref", resultRef);
            page.put("expires_at", isoMillis(record.expiresAt));
            return page;
        }

        return new ResultRefDataResponse(resultRef, isoMillis(record.expiresAt), record.contentType, record.payload);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toPagedShape(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        // Keep paging logic map-based at the boundary, but allow typed DTO payloads internally.
        Map<String, Object> converted = WebUtil.GSON.fromJson(WebUtil.GSON.toJson(payload), Map.class);
        return converted != null && converted.containsKey("rows") ? converted : null;
    }

    public static PageInfo pageInfo(int from, int to, int total, int limit) {
        return new PageInfo(from, limit, total, Math.max(0, to - from), to >= total ? null : to);
    }

    private RpcTransportResult processHttpRpcRequest(Context ctx) {
        Object requestId = null;
        try {
            RpcRequestDto requestRaw = parseRpcRequest(ctx.body());
            requestId = requestRaw.id;
            RpcRequest request = new RpcRequest(requestRaw.id, requestRaw.method, requestRaw.params);

            if (request.method() == null || request.method().isEmpty()) {
                return RpcTransportResult.withBody(400, RpcResponseFactory.error(request.id(), -32600, "Invalid Request: missing method"));
            }

            RpcMethodResult result = dispatchRpcRequest(ctx, request);
            if (result == null) {
                return RpcTransportResult.withBody(200, RpcResponseFactory.error(request.id(), -32601, "Method not found"));
            }
            if (result.noImmediateBodyResponse()) {
                return RpcTransportResult.noImmediateBodyResponse(202);
            }
            return RpcTransportResult.withBody(200, result.response());
        } catch (RpcProtocolException e) {
            return RpcTransportResult.withBody(400, RpcResponseFactory.error(requestId, e.code(), e.getMessage()));
        } catch (IllegalArgumentException e) {
            return RpcTransportResult.withBody(400, RpcResponseFactory.error(requestId, -32600, "Invalid Request: " + e.getMessage()));
        } catch (JsonSyntaxException e) {
            return RpcTransportResult.withBody(400, RpcResponseFactory.error(requestId, -32600, "Invalid Request: " + e.getMessage()));
        } catch (Throwable e) {
            Throwable root = rootCause(e);
            String message = root.getMessage() == null || root.getMessage().isEmpty()
                    ? root.getClass().getSimpleName()
                    : root.getMessage();
            return RpcTransportResult.withBody(500, RpcResponseFactory.error(requestId, -32603, "Internal error: " + message));
        }
    }

    private RpcMethodResult dispatchRpcRequest(Context ctx, RpcRequest request) {
        ParametricCallable<?> parametric = resolveRpcMethod(request.method());
        if (parametric == null) {
            return null;
        }

        Map<String, Object> params = request.params() == null
                ? Collections.emptyMap()
                : WebUtil.GSON.fromJson(request.params(), Map.class);
        Map<String, Object> rawParams = params == null ? Collections.emptyMap() : params;
        Map<String, Object> normalizedParams = stripMcpEnvelopeParams(rawParams);
        Object result;
        try {
            result = parseAndInvoke(ctx, parametric, normalizedParams,
                    (callable, parsed) -> callable.call(null, parsed.locals(), parsed.arguments()));
        } catch (Throwable throwable) {
            Throwable root = rootCause(throwable);
            if (root instanceof RpcProtocolException rpcProtocolException) {
                throw rpcProtocolException;
            }
            throw throwable;
        }

        if (result instanceof RpcNoResponse) {
            return RpcMethodResult.noResponse();
        }
        return RpcMethodResult.respond(RpcResponseFactory.success(request.id(), result));
    }

    private Map<String, Object> stripMcpEnvelopeParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return Collections.emptyMap();
        }

        boolean containsEnvelopeParams = false;
        for (String key : params.keySet()) {
            if (MCP_ENVELOPE_PARAM_KEYS.contains(key)) {
                containsEnvelopeParams = true;
                break;
            }
        }
        if (!containsEnvelopeParams) {
            return params;
        }

        Map<String, Object> filtered = new LinkedHashMap<>(params);
        MCP_ENVELOPE_PARAM_KEYS.forEach(filtered::remove);
        return filtered;
    }

    private void ensureToolCache() {
        if (toolsListSchema != null) {
            return;
        }
        synchronized (toolCacheLock) {
            Set<ParametricCallable<?>> commandList = getToolCallables();
            if (toolsListSchema == null) {
                MCPUtil.SchemaDebugObserver debugObserver = schemaDebugObserverFactory == null ? null : schemaDebugObserverFactory.get();
                toolsListSchema = MCPUtil.toJsonSchema(store, htmlOptionStore, schemaStore, commandList, null, null, null, debugObserver);
            }
        }
    }

    public Map<String, Object> getToolsListSchema() {
        ensureToolCache();
        return toolsListSchema;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callToolSuccessResult(Object payload, String traceId, long startedAt) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (payload instanceof Map<?, ?> payloadMap && payloadMap.containsKey("content")) {
            result.putAll((Map<String, Object>) payloadMap);
        } else if (payload instanceof ResultRefEnvelope envelope) {
            result.put("content", List.of(Map.of(
                    "type", "text",
                    "text", "Result exceeded inline transport size. Retrieve it with result_get using result_ref."
            )));
            result.put("structuredContent", Map.of(
                    "result_ref", envelope.result_ref(),
                    "expires_at", envelope.expires_at(),
                    "content_type", envelope.content_type(),
                    "estimated_size", envelope.estimated_size(),
                    "page_info", envelope.page_info()
            ));
        } else {
            result.put("content", List.of(Map.of(
                    "type", "text",
                    "text", WebUtil.GSON.toJson(payload)
            )));
            if (payload instanceof Map<?, ?> payloadMap) {
                result.put("structuredContent", payloadMap);
            }
        }

        result.put("_meta", meta(traceId, startedAt));
        return result;
    }

    private Map<String, Object> callToolErrorResult(String message, String traceId, long startedAt) {
        Map<String, Object> result = new LinkedHashMap<>(RpcResponseFactory.resultError(message));
        result.put("_meta", meta(traceId, startedAt));
        return result;
    }

    private Map<String, Object> meta(String traceId, long startedAt) {
        return Map.of(
                "contract_version", CONTRACT_VERSION,
                "trace_id", traceId,
                "timing_ms", Math.max(0, System.currentTimeMillis() - startedAt)
        );
    }

    private void logToolCallFailure(String traceId, String toolName, Map<String, Object> arguments, Throwable error) {
        String argsJson;
        try {
            argsJson = WebUtil.GSON.toJson(arguments == null ? Collections.emptyMap() : arguments);
        } catch (Throwable serializationError) {
            argsJson = "<failed to serialize arguments: " + serializationError.getMessage() + ">";
        }

        System.err.println("[MCP tools/call] trace_id=" + traceId
                + " tool=" + toolName
                + " arguments=" + argsJson
                + " error=" + error.getClass().getSimpleName()
                + ": " + error.getMessage());
        error.printStackTrace(System.err);
    }

    private String isoMillis(long millis) {
        return java.time.Instant.ofEpochMilli(millis).toString();
    }

    private RpcRequestDto parseRpcRequest(String body) {
        RpcRequestDto request = WebUtil.GSON.fromJson(body, RpcRequestDto.class);
        if (request == null) {
            throw new IllegalArgumentException("Request body is empty or invalid JSON");
        }
        return request;
    }

    private String normalizeSessionId(String sessionIdHeader) {
        if (sessionIdHeader == null || sessionIdHeader.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return sessionIdHeader.trim();
    }

    private void respondMethodNotAllowed(Context ctx, String message) {
        ctx.status(405)
                .contentType(JSON_CONTENT_TYPE)
                .header("Allow", "GET, POST")
                .result(WebUtil.GSON.toJson(Map.of(
                        "error", "Method Not Allowed",
                        "message", message
                )));
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    public record ParsedCommand(LocalValueStore<?> locals, Object[] arguments, Map<String, Object> normalizedArguments) {
    }

    private record ResultRefRecord(Object payload, String contentType, int estimatedSize, long expiresAt) {
    }

    private record RpcRequest(Object id, String method, JsonObject params) {
        private RpcRequest {
            if (params == null) {
                params = new JsonObject();
            }
        }
    }

    private static class RpcRequestDto {
        Object id;
        String method;
        JsonObject params;
    }

    private static class RpcProtocolException extends RuntimeException {
        private final int code;

        private RpcProtocolException(int code, String message) {
            super(message);
            this.code = code;
        }

        private RpcProtocolException(int code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        private int code() {
            return code;
        }
    }

    private record RpcMethodResult(Map<String, Object> response, boolean noImmediateBodyResponse) {
        static RpcMethodResult respond(Map<String, Object> response) {
            return new RpcMethodResult(response, false);
        }

        static RpcMethodResult noResponse() {
            return new RpcMethodResult(null, true);
        }
    }

    private record RpcTransportResult(int statusCode, Map<String, Object> payload, boolean noImmediateBodyResponse) {
        static RpcTransportResult withBody(int statusCode, Map<String, Object> payload) {
            return new RpcTransportResult(statusCode, payload, false);
        }

        static RpcTransportResult noImmediateBodyResponse(int statusCode) {
            return new RpcTransportResult(statusCode, null, true);
        }
    }

    public record PageInfo(int cursor, int limit, int total, int returned, Integer next_cursor) {
    }

    public record ResultRefEnvelope(UUID result_ref, String expires_at, String content_type, int estimated_size, PageInfo page_info) {
    }

    public record ResultRefDataResponse(UUID result_ref, String expires_at, String content_type, Object data) {
    }

    private enum RpcNoResponse {
        INSTANCE
    }

    @FunctionalInterface
    private interface ParsedCommandInvoker<T> {
        T invoke(ParametricCallable<?> callable, ParsedCommand parsed);
    }

    public class RpcCommands {
        @Command(aliases = {"initialize"})
        public Object initialize(@Default String protocolVersion,
                     @Default Map<String, Object> capabilities,
                     @Default Map<String, Object> clientInfo) {
            return new InitializeResponse(
                    "2025-11-25",
                    new Capabilities(new ToolCapabilities()),
                    new ServerInfo("Locutus", "1.0.0")
            );
        }

        @Command(aliases = {"initialized"})
        public Object notifications_initialized() {
            return RpcNoResponse.INSTANCE;
        }

        @Command(aliases = {"list"})
        public Object tools_list() {
            return getToolsListSchema();
        }

        @Command(aliases = {"call"})
        public Object tools_call(Context context, String name, @Default Map<String, Object> arguments) {
            String traceId = UUID.randomUUID().toString();
            long started = System.currentTimeMillis();
            Map<String, Object> toolArgs = arguments == null ? Collections.emptyMap() : arguments;
            try {
                ParametricCallable<?> tool = resolveTool(name);
                if (tool == null) {
                    throw new RpcProtocolException(-32602, "Unknown tool: " + name);
                }

                ParsedCommand parsed;
                try {
                    parsed = parseCommand(tool, toolArgs, context);
                } catch (IllegalArgumentException e) {
                    throw new RpcProtocolException(-32602, "Invalid params: " + e.getMessage(), e);
                }

                Map<String, Object> output = executeParsed(tool, parsed);
                Object payload = maybeStoreLargeResult(output, "application/json");
                return callToolSuccessResult(payload, traceId, started);
            } catch (RpcProtocolException e) {
                throw e;
            } catch (Throwable e) {
                logToolCallFailure(traceId, name, toolArgs, e);
                Throwable root = rootCause(e);
                String message = root.getMessage() == null || root.getMessage().isEmpty()
                        ? root.getClass().getSimpleName()
                        : root.getMessage();
                return callToolErrorResult(message, traceId, started);
            }
        }
    }

    private record InitializeResponse(String protocolVersion, Capabilities capabilities, ServerInfo serverInfo) {
    }

    private record Capabilities(ToolCapabilities tools) {
    }

    private record ToolCapabilities() {
    }

    private record ServerInfo(String name, String version) {
    }
}
