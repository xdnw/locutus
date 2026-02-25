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
import java.util.function.Consumer;
import java.util.Locale;

public class MCPHandler {
    private static final String CONTRACT_VERSION = "2026-02-24";
    public static final int DEFAULT_PAGE_LIMIT = 25;
    public static final int MAX_PAGE_LIMIT = 250;
    private static final int INLINE_RESULT_MAX_BYTES = 120_000;
    private static final long RESULT_REF_TTL_MILLIS = TimeUnit.MINUTES.toMillis(20);

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

    private final Map<UUID, ResultRefRecord> resultRefs = new ConcurrentHashMap<>();

    private volatile Map<String, Object> toolsListSchema;
    private final Object toolCacheLock = new Object();

    public MCPHandler(
            ValueStore<Object> store,
            ValueStore<Object> htmlOptionStore,
            ValidatorStore validators,
            PermissionHandler permisser,
            LocalStoreFactory localStoreFactory
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.htmlOptionStore = Objects.requireNonNull(htmlOptionStore, "htmlOptionStore");
        this.validators = Objects.requireNonNull(validators, "validators");
        this.permisser = Objects.requireNonNull(permisser, "permisser");
        this.localStoreFactory = Objects.requireNonNull(localStoreFactory, "localStoreFactory");

        this.schemaStore = new SimpleValueStore<>();
        new SchemaBindings().register(schemaStore);

        this.toolCommands = CommandGroup.createRoot(store, validators);
        this.toolCommands.registerCommands(new MCPCommands(this));

        this.rpcCommands = CommandGroup.createRoot(store, validators);
        RpcCommands rpc = new RpcCommands();
        this.rpcCommands.registerMethod(rpc, List.of(), "initialize", "initialize");
        this.rpcCommands.registerMethod(rpc, List.of("notifications"), "notifications_initialized", "initialized");
        this.rpcCommands.registerMethod(rpc, List.of("tools"), "tools_list", "list");
        this.rpcCommands.registerMethod(rpc, List.of("tools"), "tools_call", "call");
    }

    public void handleMessage(Context ctx) {
        processRequest(ctx, response -> {
            ctx.contentType("application/json");
            ctx.status(200).result(WebUtil.GSON.toJson(response));
        });
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

        Object payload = record.payload;
        if (payload instanceof Map<?, ?> map && map.get("rows") instanceof List<?> rowsAny) {
            List<?> rows = rowsAny;
            int from = Math.min(cursor, rows.size());
            int to = Math.min(from + limit, rows.size());

            Map<String, Object> page = new LinkedHashMap<>();
            page.putAll((Map<String, Object>) map);
            page.put("rows", new ArrayList<>(rows.subList(from, to)));
            page.put("page_info", pageInfo(from, to, rows.size(), limit));
            page.put("result_ref", resultRef);
            page.put("expires_at", isoMillis(record.expiresAt));
            return page;
        }

        return new ResultRefDataResponse(resultRef, isoMillis(record.expiresAt), record.contentType, payload);
    }

    public static PageInfo pageInfo(int from, int to, int total, int limit) {
        return new PageInfo(from, limit, total, Math.max(0, to - from), to >= total ? null : to);
    }

    private void processRequest(Context ctx, Consumer<Map<String, Object>> responder) {
        Object requestId = null;
        try {
            RpcRequestDto requestRaw = parseRpcRequest(ctx.body());
            requestId = requestRaw.id;
            RpcRequest request = new RpcRequest(requestRaw.id, requestRaw.method, requestRaw.params);

            if (request.method() == null || request.method().isEmpty()) {
                responder.accept(RpcResponseFactory.error(request.id(), -32600, "Invalid Request: missing method"));
                return;
            }

            RpcMethodResult result = invokeRpcMethod(ctx, request);
            if (result == null) {
                responder.accept(RpcResponseFactory.error(request.id(), -32601, "Method not found"));
                return;
            }
            if (result.noSseResponse()) {
                ctx.status(202);
                return;
            }
            responder.accept(result.response());
        } catch (IllegalArgumentException e) {
            responder.accept(RpcResponseFactory.error(requestId, -32600, "Invalid Request: " + e.getMessage()));
        } catch (JsonSyntaxException e) {
            responder.accept(RpcResponseFactory.error(requestId, -32600, "Invalid Request: " + e.getMessage()));
        } catch (Throwable e) {
            Throwable root = rootCause(e);
            String message = root.getMessage() == null || root.getMessage().isEmpty()
                    ? root.getClass().getSimpleName()
                    : root.getMessage();
            responder.accept(RpcResponseFactory.error(requestId, -32603, "Internal error: " + message));
        }
    }

    private RpcMethodResult invokeRpcMethod(Context ctx, RpcRequest request) {
        ParametricCallable<?> parametric = resolveRpcMethod(request.method());
        if (parametric == null) {
            return null;
        }

        Map<String, Object> params = request.params() == null
                ? Collections.emptyMap()
                : WebUtil.GSON.fromJson(request.params(), Map.class);
        Map<String, Object> rawParams = params == null ? Collections.emptyMap() : params;
        Object result = parseAndInvoke(ctx, parametric, rawParams,
                (callable, parsed) -> callable.call(null, parsed.locals(), parsed.arguments()));

        if (result instanceof RpcNoResponse) {
            return RpcMethodResult.noResponse();
        }
        return RpcMethodResult.respond(RpcResponseFactory.success(request.id(), result));
    }

    private void ensureToolCache() {
        if (toolsListSchema != null) {
            return;
        }
        synchronized (toolCacheLock) {
            Set<ParametricCallable<?>> commandList = getToolCallables();
            if (toolsListSchema == null) {
                toolsListSchema = MCPUtil.toJsonSchema(store, htmlOptionStore, schemaStore, commandList, null, null, null);
            }
        }
    }

    public Map<String, Object> getToolsListSchema() {
        ensureToolCache();
        return toolsListSchema;
    }

    private ToolCallResponse envelopeSuccess(Object data, String traceId, long startedAt) {
        return new ToolCallResponse(true, null, meta(traceId, startedAt), data);
    }

    private ToolCallResponse envelopeError(String code, String message, String traceId, long startedAt) {
        return new ToolCallResponse(false, new ErrorBody(code, message), meta(traceId, startedAt), null);
    }

    private Meta meta(String traceId, long startedAt) {
        return new Meta(CONTRACT_VERSION, traceId, Math.max(0, System.currentTimeMillis() - startedAt));
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

    private record RpcMethodResult(Map<String, Object> response, boolean noSseResponse) {
        static RpcMethodResult respond(Map<String, Object> response) {
            return new RpcMethodResult(response, false);
        }

        static RpcMethodResult noResponse() {
            return new RpcMethodResult(null, true);
        }
    }

    public record PageInfo(int cursor, int limit, int total, int returned, Integer next_cursor) {
    }

    public record ResultRefEnvelope(UUID result_ref, String expires_at, String content_type, int estimated_size, PageInfo page_info) {
    }

    public record ResultRefDataResponse(UUID result_ref, String expires_at, String content_type, Object data) {
    }

    private record ToolCallResponse(boolean ok, ErrorBody error, Meta meta, Object data) {
    }

    private record ErrorBody(String code, String message) {
    }

    private record Meta(String contract_version, String trace_id, long timing_ms) {
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
        public Object initialize() {
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
            try {
                ParametricCallable<?> tool = resolveTool(name);
                if (tool == null) {
                    throw new IllegalArgumentException("Unknown tool: " + name);
                }
                Map<String, Object> toolArgs = arguments == null ? Collections.emptyMap() : arguments;
                Map<String, Object> output = parseAndInvoke(context, tool, toolArgs, MCPHandler.this::executeParsed);
                Object payload = maybeStoreLargeResult(output, "application/json");
                return envelopeSuccess(payload, traceId, started);
            } catch (IllegalArgumentException e) {
                return envelopeError("invalid_arguments", e.getMessage(), traceId, started);
            } catch (Throwable e) {
                Throwable root = rootCause(e);
                String message = root.getMessage() == null || root.getMessage().isEmpty()
                        ? root.getClass().getSimpleName()
                        : root.getMessage();
                return envelopeError("internal_error", message, traceId, started);
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
