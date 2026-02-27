package link.locutus.discord.web.jooby;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import io.javalin.Javalin;
import io.javalin.http.Context;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.SimpleValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.gpt.mcp.MCPUtil;
import link.locutus.discord.web.WebUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MCPTransportContractTest {
    private Javalin app;
    private HttpClient client;
    private int port;
    private MCPHandler handler;

    public static void main(String[] args) throws Exception {
        MCPTransportContractTest harness = new MCPTransportContractTest();
        harness.setUpWithTools(false);
        try {
            JsonObject arguments = new JsonObject();
            arguments.addProperty("type", "nation");
            arguments.addProperty("selection", "hello");
            arguments.addProperty("columns", "a");
            arguments.addProperty("mode", "PLAN");
            arguments.addProperty("cursor", 0);
            arguments.addProperty("limit", 10);

            JsonObject meta = new JsonObject();
            meta.addProperty("progressToken", "99a6c54f-da7a-424f-b3cc-f110795958b6");
            meta.addProperty("vscode.conversationId", "5154923b-70be-4215-b6e0-085352804953");
            meta.addProperty("vscode.requestId", "5c09a1ae-dc00-4776-bd55-aa594aee162a");

            JsonObject params = new JsonObject();
            params.addProperty("name", "data_query");
            params.add("arguments", arguments);
            params.add("_meta", meta);

            JsonObject payload = harness.rpcPayload("tools/call", "3", params);
            HttpResponse<String> response = harness.postJson("/mcp", payload.toString());

            System.out.println("Request payload: " + payload);
            System.out.println("HTTP status: " + response.statusCode());
            System.out.println("Response body: " + response.body());
            System.out.println("Check stderr for stacktrace logs from MCPHandler.tools_call failures.");
        } finally {
            harness.tearDown();
        }
    }

    @BeforeEach
    void setUp() {
        setUpWithTools(false);
    }

    private void setUpWithTools(boolean registerDefaultToolCommands) {
        ValueStore<Object> store = new SimpleValueStore<>();
        ValueStore<Object> htmlOptionStore = new SimpleValueStore<>();
        ValidatorStore validators = new ValidatorStore();
        PermissionHandler permisser = new PermissionHandler();
        new PrimitiveBindings().register(store);
        Type mapType = new TypeToken<Map<String, Object>>() { }.getType();
        store.addParser((Key<Map<String, Object>>) (Key<?>) Key.of(mapType), new MapArgumentParser(mapType));

        store.addDynamicProvider(Key.of(Context.class), valueStore ->
                valueStore.getProvided(Key.of(Context.class, Me.class), false)
        );

        handler = new MCPHandler(
                store,
                htmlOptionStore,
                validators,
                permisser,
                (locals, ctx) -> {
                    LocalValueStore<Object> resolved = locals == null
                            ? new LocalValueStore<>(store)
                            : (LocalValueStore<Object>) locals;
                    resolved.addProvider(Context.class, ctx);
                    resolved.addProvider(Key.of(Context.class, Me.class), ctx);
                    return resolved;
                },
                registerDefaultToolCommands,
                null
        );
        if (!registerDefaultToolCommands) {
            handler.registerToolCommands(new TestToolCommands());
        }

        app = Javalin.create();
        app.post("/mcp", handler::handleHttpRpcPost);
        app.get("/mcp", handler::handleHttpSessionGet);
        app.put("/mcp", handler::handleUnsupportedMethod);
        app.patch("/mcp", handler::handleUnsupportedMethod);
        app.delete("/mcp", handler::handleUnsupportedMethod);
        app.options("/mcp", handler::handleUnsupportedMethod);
        app.get("/mcp/sse", ctx -> ctx.status(410).result("Deprecated endpoint. Use /mcp with streamable HTTP transport."));
        app.post("/mcp/sse", ctx -> ctx.status(410).result("Deprecated endpoint. Use /mcp with JSON-RPC POST."));

        app.start(0);
        port = app.port();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    void postInitializeReturnsJsonRpcSuccessEnvelope() throws Exception {
        JsonObject payload = rpcPayload("initialize", "init-1", Map.of());

        HttpResponse<String> response = postJson("/mcp", payload.toString());
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();

        assertEquals(200, response.statusCode());
        assertEquals("2.0", body.get("jsonrpc").getAsString());
        assertEquals("init-1", body.get("id").getAsString());
        assertTrue(body.has("result"));

        JsonObject result = body.getAsJsonObject("result");
        assertEquals("2025-11-25", result.get("protocolVersion").getAsString());
        assertTrue(result.has("capabilities"));
        assertTrue(result.has("serverInfo"));
    }

    @Test
    void malformedPostBodyMapsToInvalidRequest() throws Exception {
        HttpResponse<String> response = postJson("/mcp", "{ bad json");
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();

        assertEquals(400, response.statusCode());
        assertEquals("2.0", body.get("jsonrpc").getAsString());
        assertTrue(body.has("error"));
        assertEquals(-32600, body.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    void getMcpProvidesSessionStreamBootstrap() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/mcp"))
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("text/event-stream"));
        assertTrue(response.headers().firstValue("Mcp-Session-Id").isPresent());
        assertTrue(response.body().contains("event: session"));
    }

    @Test
    void legacyAliasIsHardCutWithMigrationResponse() throws Exception {
        HttpResponse<String> response = postJson("/mcp/sse", "{}");
        assertEquals(410, response.statusCode());
        assertTrue(response.body().contains("Deprecated endpoint"));
    }

    @Test
    void contractEnvelopeKeysStableForInitializeToolsListAndToolsCall() throws Exception {
        assertStandardSuccessEnvelope(postJson("/mcp", rpcPayload("initialize", "init-2", Map.of()).toString()));
        assertStandardSuccessEnvelope(postJson("/mcp", rpcPayload("tools/list", "tools-1", Map.of()).toString()));
        String toolName = "test_command";

        JsonObject toolsCallParams = new JsonObject();
        toolsCallParams.addProperty("name", toolName);
        toolsCallParams.add("arguments", WebUtil.GSON.toJsonTree(Map.of("arg1", "hello")));
        HttpResponse<String> toolsCallResponse = postJson("/mcp", rpcPayload("tools/call", "call-1", toolsCallParams).toString());

        JsonObject body = assertStandardSuccessEnvelope(toolsCallResponse);
        JsonObject result = body.getAsJsonObject("result");
        assertTrue(result.has("ok"));
        assertTrue(result.has("meta"));
        assertTrue(result.has("data") || result.has("error"));
    }

    @Test
    void toolsCallAcceptsTopLevelMetaEnvelopeField() throws Exception {
        String toolName = "test_command";

        JsonObject params = toolsCallParams(toolName);
        params.add("_meta", WebUtil.GSON.toJsonTree(Map.of(
                "progressToken", "token-123",
                "vscode.requestId", "req-123"
        )));

        HttpResponse<String> response = postJson("/mcp", rpcPayload("tools/call", "call-meta", params).toString());
        JsonObject body = assertStandardSuccessEnvelope(response);
        assertTrue(body.getAsJsonObject("result").get("ok").getAsBoolean());
    }

    @Test
    void toolsCallAcceptsTopLevelTaskEnvelopeField() throws Exception {
        String toolName = "test_command";

        JsonObject params = toolsCallParams(toolName);
        params.add("task", WebUtil.GSON.toJsonTree(Map.of("id", "task-1", "label", "discover")));

        HttpResponse<String> response = postJson("/mcp", rpcPayload("tools/call", "call-task", params).toString());
        JsonObject body = assertStandardSuccessEnvelope(response);
        assertTrue(body.getAsJsonObject("result").get("ok").getAsBoolean());
    }

    @Test
    void toolsCallAcceptsNumericJsonArgumentsForIntegerParams() throws Exception {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("type", "nation");
        arguments.addProperty("selection", "hello");
        arguments.addProperty("columns", "a");
        arguments.addProperty("mode", "PLAN");
        arguments.addProperty("cursor", 0);
        arguments.addProperty("limit", 10);

        JsonObject params = new JsonObject();
        params.addProperty("name", "data_query");
        params.add("arguments", arguments);

        HttpResponse<String> response = postJson("/mcp", rpcPayload("tools/call", "call-numeric-int", params).toString());
        JsonObject body = assertStandardSuccessEnvelope(response);
        assertTrue(body.getAsJsonObject("result").get("ok").getAsBoolean());
    }

    @Test
    void toolsCallRejectsUnknownTopLevelParamOutsideEnvelope() throws Exception {
        String toolName = "test_command";

        JsonObject params = toolsCallParams(toolName);
        params.addProperty("unknown_field", "still-invalid");

        HttpResponse<String> response = postJson("/mcp", rpcPayload("tools/call", "call-unknown", params).toString());
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();

        assertEquals(400, response.statusCode());
        assertEquals("2.0", body.get("jsonrpc").getAsString());
        assertTrue(body.has("error"));
        assertEquals(-32600, body.getAsJsonObject("error").get("code").getAsInt());
        assertTrue(body.getAsJsonObject("error").get("message").getAsString().contains("unknown_field"));
    }

    @Test
    void resultRefRoundTripRemainsAvailableAfterTransportSplit() {
        String huge = "x".repeat(130_000);
        Object maybeRef = handler.maybeStoreLargeResult(Map.of("blob", huge), "application/json");

        assertTrue(maybeRef instanceof MCPHandler.ResultRefEnvelope);
        MCPHandler.ResultRefEnvelope envelope = (MCPHandler.ResultRefEnvelope) maybeRef;

        Object restored = handler.getResultRef(envelope.result_ref(), 0, 10);
        assertTrue(restored instanceof MCPHandler.ResultRefDataResponse);
        MCPHandler.ResultRefDataResponse response = (MCPHandler.ResultRefDataResponse) restored;

        assertEquals(envelope.result_ref(), response.result_ref());
        assertEquals("application/json", response.content_type());
        assertNotNull(response.data());
    }

    private JsonObject assertStandardSuccessEnvelope(HttpResponse<String> response) {
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(200, response.statusCode());
        assertEquals("2.0", body.get("jsonrpc").getAsString());
        assertTrue(body.has("id"));
        assertTrue(body.has("result"));
        return body;
    }

    private HttpResponse<String> postJson(String path, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private JsonObject rpcPayload(String method, String id, Object params) {
        JsonObject payload = new JsonObject();
        payload.addProperty("jsonrpc", "2.0");
        payload.addProperty("id", id);
        payload.addProperty("method", method);
        payload.add("params", WebUtil.GSON.toJsonTree(params));
        return payload;
    }

    private JsonObject toolsCallParams(String toolName) {
        JsonObject toolsCallParams = new JsonObject();
        toolsCallParams.addProperty("name", toolName);
        toolsCallParams.add("arguments", WebUtil.GSON.toJsonTree(Map.of("arg1", "hello")));
        return toolsCallParams;
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    public static class TestToolCommands {
        @Command(aliases = {"test_command"})
        public Object test_command(String arg1) {
            return Map.of("arg1", arg1);
        }

        @Command(aliases = {"data_query"})
        public Object data_query(String type,
                                 String selection,
                                 String columns,
                                 String mode,
                                 Integer cursor,
                                 Integer limit) {
            return Map.of(
                    "type", type,
                    "selection", selection,
                    "columns", columns,
                    "mode", mode,
                    "cursor", cursor,
                    "limit", limit
            );
        }
    }

    private static class MapArgumentParser implements Parser<Map<String, Object>> {
        private final Key<Map<String, Object>> key;

        private MapArgumentParser(Type mapType) {
            this.key = (Key<Map<String, Object>>) (Key<?>) Key.of(mapType);
        }

        @Override
        public Map<String, Object> apply(ArgumentStack arg) {
            throw new UnsupportedOperationException("ArgumentStack parsing is not used in this transport test");
        }

        @Override
        public Map<String, Object> apply(ValueStore store, Object t) {
            if (t == null) {
                return Collections.emptyMap();
            }
            if (t instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            throw new IllegalArgumentException("Expected object map for arguments");
        }

        @Override
        public boolean isConsumer(ValueStore store) {
            return true;
        }

        @Override
        public Key<?> getKey() {
            return key;
        }

        @Override
        public String getDescription() {
            return "JSON object map";
        }

        @Override
        public String[] getExamples() {
            return new String[]{"{\"key\":\"value\"}"};
        }

        @Override
        public Class<?>[] getWebType() {
            return new Class<?>[0];
        }

        @Override
        public Map<String, Object> toJson() {
            return Map.of("type", "object");
        }
    }
}
