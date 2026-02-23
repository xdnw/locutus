package link.locutus.discord.web.jooby;

import com.google.gson.reflect.TypeToken;
import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import link.locutus.discord.commands.manager.v2.binding.SimpleValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.command.StringMessageIO;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.config.Settings;
import link.locutus.discord.gpt.mcp.MCPUtil;
import link.locutus.discord.gpt.mcp.SchemaBindings;
import link.locutus.discord.web.WebUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

public class MCPHandler {
    private static final String JSON_RPC_VERSION = "2.0";
    private static final Type MAP_TYPE = TypeToken.getParameterized(Map.class, String.class, Object.class).getType();

    @FunctionalInterface
    public interface LocalStoreFactory extends BiFunction<LocalValueStore<?>, Context, LocalValueStore<?>> {
    }

    private final Map<UUID, SseClient> mcpClients = new ConcurrentHashMap<>();

    private final ValueStore<Object> store;
    private final ValueStore<Object> htmlOptionStore;
    private final ValueStore<Object> schemaStore;
    private final Collection<ParametricCallable<?>> commands;
    private final ValidatorStore validators;
    private final PermissionHandler permisser;
    private final LocalStoreFactory localStoreFactory;

    private final Map<String, RpcMethodHandler> methodHandlers = new HashMap<>();

    private volatile Map<String, Object> toolsListSchema;
    private volatile Map<String, ParametricCallable<?>> toolByName;
    private final Object toolCacheLock = new Object();

    public MCPHandler(
            ValueStore<Object> store,
            ValueStore<Object> htmlOptionStore,
            Collection<ParametricCallable<?>> commands,
            ValidatorStore validators,
            PermissionHandler permisser,
            LocalStoreFactory localStoreFactory
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.htmlOptionStore = Objects.requireNonNull(htmlOptionStore, "htmlOptionStore");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.validators = Objects.requireNonNull(validators, "validators");
        this.permisser = Objects.requireNonNull(permisser, "permisser");
        this.localStoreFactory = Objects.requireNonNull(localStoreFactory, "localStoreFactory");

        this.schemaStore = new SimpleValueStore<>();
        new SchemaBindings().register(schemaStore);

        registerMethodHandlers();
    }

    public void sse(SseClient client) {
        UUID sessionId = UUID.randomUUID();
        mcpClients.put(sessionId, client);

        client.onClose(() -> mcpClients.remove(sessionId));

        // MCP Standard: Tell the client where to POST messages
        // Adjust the domain/port to match your server's public URL
        String postEndpoint = Settings.INSTANCE.WEB.BACKEND_DOMAIN + ":" + Settings.INSTANCE.WEB.PORT + "/mcp/message?sessionId=" + sessionId;

        client.sendEvent("endpoint", postEndpoint);
        client.keepAlive();
    }

    public void handleMessage(Context ctx) {
        String sessionId = ctx.queryParam("sessionId");
        if (sessionId == null) {
            ctx.status(400).result("Missing sessionId");
            return;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(sessionId);
        } catch (IllegalArgumentException e) {
            ctx.status(400).result("Invalid sessionId: " + sessionId);
            return;
        }
        SseClient sseClient = mcpClients.get(uuid);
        if (sseClient == null) {
            ctx.status(400).result("Missing SSE session: " + sessionId);
            return;
        }

        try {
            Map<String, Object> requestRaw = parseRpcRequest(ctx.body());
            Object id = requestRaw.get("id");
            RpcRequest request = new RpcRequest(id, asString(requestRaw.get("method")), asMap(requestRaw.get("params")));

            if (request.method() == null || request.method().isEmpty()) {
                sendRpcResponse(ctx, sseClient, error(id, -32600, "Invalid Request: missing method"));
                return;
            }

            RpcMethodHandler handler = methodHandlers.get(request.method());
            if (handler == null) {
                sendRpcResponse(ctx, sseClient, error(id, -32601, "Method not found"));
                return;
            }

            RpcMethodResult result = handler.handle(ctx, request);
            if (result.noSseResponse()) {
                ctx.status(202);
                return;
            }
            sendRpcResponse(ctx, sseClient, result.response());
        } catch (IllegalArgumentException e) {
            sendRpcResponse(ctx, sseClient, error(null, -32600, "Invalid Request: " + e.getMessage()));
        } catch (Throwable e) {
            e.printStackTrace();
            sendRpcResponse(ctx, sseClient, error(null, -32603, "Internal error: " + e.getMessage()));
        }
    }

    private void sendRpcResponse(Context ctx, SseClient sseClient, Map<String, Object> response) {
        sseClient.sendEvent("message", WebUtil.GSON.toJson(response));
        ctx.status(202).result("Accepted");
    }

    private void registerMethodHandlers() {
        methodHandlers.put("initialize", this::handleInitialize);
        methodHandlers.put("notifications/initialized", this::handleInitializedNotification);
        methodHandlers.put("tools/list", this::handleToolsList);
        methodHandlers.put("tools/call", this::handleToolsCall);
    }

    private RpcMethodResult handleInitialize(Context ctx, RpcRequest request) {
        Map<String, Object> initResult = new LinkedHashMap<>();
        initResult.put("protocolVersion", "2024-11-05");
        initResult.put("capabilities", Map.of("tools", Map.of()));
        initResult.put("serverInfo", Map.of(
                "name", "Locutus",
                "version", "1.0.0"
        ));
        return RpcMethodResult.respond(ok(request.id(), initResult));
    }

    private RpcMethodResult handleInitializedNotification(Context ctx, RpcRequest request) {
        // notifications/initialized is acknowledged by HTTP 202 only.
        return RpcMethodResult.noResponse();
    }

    private RpcMethodResult handleToolsList(Context ctx, RpcRequest request) {
        ensureToolCache();
        return RpcMethodResult.respond(ok(request.id(), toolsListSchema));
    }

    private RpcMethodResult handleToolsCall(Context ctx, RpcRequest request) {
        ensureToolCache();

        String toolName = asString(request.params().get("name"));
        if (toolName == null || toolName.isEmpty()) {
            return RpcMethodResult.respond(ok(request.id(), toolError("Missing tool name in params.name")));
        }

        Map<String, Object> toolArgs = asMap(request.params().get("arguments"));
        if (toolArgs == null) {
            toolArgs = Collections.emptyMap();
        }

        ParametricCallable<?> tool = toolByName.get(toolName);
        if (tool == null) {
            return RpcMethodResult.respond(ok(request.id(), toolError("Unknown tool: " + toolName)));
        }

        try {
            String output = executeTool(tool, toolArgs, ctx);
            return RpcMethodResult.respond(ok(request.id(), toolSuccess(output)));
        } catch (Throwable e) {
            Throwable root = rootCause(e);
            String message = root.getMessage() == null || root.getMessage().isEmpty()
                    ? root.getClass().getSimpleName()
                    : root.getMessage();
            return RpcMethodResult.respond(ok(request.id(), toolError(message)));
        }
    }

    private String executeTool(ParametricCallable<?> callable, Map<String, Object> arguments, Context ctx) {
        LocalValueStore<?> locals = localStoreFactory.apply(null, ctx);
        Guild guild = (Guild) locals.getProvided(Key.of(Guild.class, Me.class));
        User user = (User) locals.getProvided(Key.of(User.class, Me.class));

        StringMessageIO io = new StringMessageIO(user, guild);
        locals.addProvider(Key.of(IMessageIO.class, Me.class), io);

        callable.validatePermissions(locals, permisser);

        Object[] parsed = callable.parseArgumentMap2(arguments, locals, validators, permisser, true);
        Object result = callable.call(null, locals, parsed);
        String ioOutput = io.toString().trim();
        if (!ioOutput.isEmpty()) {
            return ioOutput;
        }
        if (result == null) {
            return "";
        }
        return result.toString();
    }

    private void ensureToolCache() {
        if (toolsListSchema != null && toolByName != null) {
            return;
        }
        synchronized (toolCacheLock) {
            if (toolsListSchema == null) {
                toolsListSchema = MCPUtil.toJsonSchema(store, htmlOptionStore, schemaStore, commands, null, null, null);
            }
            if (toolByName == null) {
                Map<String, ParametricCallable<?>> index = new LinkedHashMap<>();
                List<String> duplicates = new ArrayList<>();
                for (ParametricCallable<?> command : commands) {
                    String name = getMcpToolName(command);
                    if (index.containsKey(name)) {
                        duplicates.add(name);
                        continue;
                    }
                    index.put(name, command);
                }
                if (!duplicates.isEmpty()) {
                    throw new IllegalStateException("Duplicate MCP tool names found: " + String.join(", ", duplicates));
                }
                toolByName = index;
            }
        }
    }

    private Map<String, Object> parseRpcRequest(String body) {
        Map<String, Object> request = WebUtil.GSON.fromJson(body, MAP_TYPE);
        if (request == null) {
            throw new IllegalArgumentException("Request body is empty or invalid JSON");
        }
        return request;
    }

    private Map<String, Object> ok(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", JSON_RPC_VERSION);
        if (id != null) {
            response.put("id", id);
        }
        response.put("result", result);
        return response;
    }

    private Map<String, Object> error(Object id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", JSON_RPC_VERSION);
        if (id != null) {
            response.put("id", id);
        }
        response.put("error", Map.of("code", code, "message", message));
        return response;
    }

    private Map<String, Object> toolSuccess(String text) {
        return Map.of(
                "content", List.of(Map.of(
                        "type", "text",
                        "text", text == null ? "" : text
                ))
        );
    }

    private Map<String, Object> toolError(String text) {
        return Map.of(
                "isError", true,
                "content", List.of(Map.of(
                        "type", "text",
                        "text", text == null ? "Unknown error" : text
                ))
        );
    }

    private static String getMcpToolName(ParametricCallable<?> command) {
        // Mirrors ParametricCallable#toJsonSchema name generation exactly.
        String name = command.getFullPath("-").toLowerCase(Locale.ROOT);
        if (name.length() > 64) {
            name = name.substring(0, 64);
        }
        return name;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("Expected object but got: " + value.getClass().getSimpleName());
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        throw new IllegalArgumentException("Expected string but got: " + value.getClass().getSimpleName());
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private interface RpcMethodHandler {
        RpcMethodResult handle(Context ctx, RpcRequest request);
    }

    private record RpcRequest(Object id, String method, Map<String, Object> params) {
        private RpcRequest {
            if (params == null) {
                params = Collections.emptyMap();
            }
        }
    }

    private record RpcMethodResult(Map<String, Object> response, boolean noSseResponse) {
        static RpcMethodResult respond(Map<String, Object> response) {
            return new RpcMethodResult(response, false);
        }

        static RpcMethodResult noResponse() {
            return new RpcMethodResult(null, true);
        }
    }
}
