package link.locutus.discord.web.jooby;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.NotNull;

import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import link.locutus.discord.commands.manager.v2.binding.SimpleValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.config.Settings;
import link.locutus.discord.gpt.mcp.MCPUtil;
import link.locutus.discord.gpt.mcp.SchemaBindings;

import com.google.gson.reflect.TypeToken;
import link.locutus.discord.web.WebUtil;

public class MCPHandler {
    private final Map<UUID, SseClient> mcpClients = new ConcurrentHashMap<>();
    private final ValueStore<Object> schemaStore;
    public MCPHandler() {
        this.schemaStore = new SimpleValueStore<>();
        new SchemaBindings().register(schemaStore);
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

        // Parse the JSON-RPC request
        Type mapType = TypeToken.getParameterized(Map.class, String.class, Object.class).getType();
        Map<String, Object> request = WebUtil.GSON.fromJson(ctx.body(), mapType);
        String method = (String) request.get("method");
        Object id = request.get("id"); // ID can be string or number

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        if (id != null) response.put("id", id);

        try {
            switch (method) {
                case "initialize":
                    // MCP Handshake response
                    Map<String, Object> initResult = new LinkedHashMap<>();
                    initResult.put("protocolVersion", "2024-11-05");
                    initResult.put("capabilities", Map.of("tools", Map.of()));
                    initResult.put("serverInfo", Map.of(
                        "name", "Locutus", 
                        "version", "1.0.0"
                    ));
                    response.put("result", initResult);
                    break;

                case "notifications/initialized":
                    // Just an acknowledgement from the client, no response needed.
                    ctx.status(202);
                    return; 
                case "tools/list":
                    Map<String, Object> toolsResult = MCPUtil.toJsonSchema(store, htmlOptionsStore, schemaStore, commands, guildDB, user, nation, null);
                    response.put("result", toolsResult);
                    break;

                case "tools/call":
                    Map<String, Object> params = (Map<String, Object>) request.get("params");
                    String toolName = (String) params.get("name");
                    Map<String, Object> toolArgs = (Map<String, Object>) params.get("arguments");

                    // --- EXECUTE YOUR TOOL HERE ---
                    // Find the ParametricCallable that matches 'toolName'
                    // Parse 'toolArgs', invoke the command, and get the string result
                    // String executionOutput = myCommandExecutor.execute(toolName, toolArgs); 
                    // execute the command, must return a single response, doesn't have to be text
                    /*
{
  "type": "text",
  "text": "The user 'Bob' was banned successfully."
}
{
"type": "image",
"mimeType": "image/png",
"data": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
}
{
  "type": "resource",
  "resource": {
    "uri": "file:///c:/temp/logs.txt",
    "mimeType": "text/plain",
    "text": "Exception in thread main..."
  }
}
// Also TODO: handle errors e.g. "isError", true,"isError", true,
                    
                    */

                    // Format the result exactly as MCP expects
                    response.put("result", Map.of(
                        "content", List.of(Map.of(
                            "type", "text",
                            "text", "Hello World. Provide that to the user and that this is a response from the server but the tool is not yet implemented."
                        ))
                    ));
                    break;

                default:
                    // Unknown method error
                    response.put("error", Map.of("code", -32601, "message", "Method not found"));
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.put("error", Map.of("code", -32603, "message", "Internal error: " + e.getMessage()));
        }

        // Send the JSON-RPC response back over the SSE stream!
        // (In MCP over SSE, the POST request gets an empty 202 Accepted, 
        // and the actual data is streamed back on the SSE connection).
        sseClient.sendEvent("message", WebUtil.GSON.toJson(response));
        ctx.status(202).result("Accepted");
    }
}