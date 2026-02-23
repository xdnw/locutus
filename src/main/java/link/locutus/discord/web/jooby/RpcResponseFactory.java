package link.locutus.discord.web.jooby;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RpcResponseFactory {
    private static final String JSON_RPC_VERSION = "2.0";

    private RpcResponseFactory() {
    }

    public static Map<String, Object> success(Object id, Object result) {
        Map<String, Object> response = baseResponse(id);
        response.put("result", result);
        return response;
    }

    public static Map<String, Object> error(Object id, int code, String message) {
        Map<String, Object> response = baseResponse(id);
        response.put("error", Map.of("code", code, "message", message));
        return response;
    }

    public static Map<String, Object> result(String text) {
        return Map.of(
                "content", List.of(Map.of(
                        "type", "text",
                        "text", text == null ? "" : text
                ))
        );
    }

    public static Map<String, Object> resultContent(Map<String, Object> payload) {
        if (payload == null) {
            return result("");
        }
        return payload;
    }

    public static Map<String, Object> resultError(String text) {
        return Map.of(
                "isError", true,
                "content", List.of(Map.of(
                        "type", "text",
                        "text", text == null ? "Unknown error" : text
                ))
        );
    }

    private static Map<String, Object> baseResponse(Object id) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", JSON_RPC_VERSION);
        if (id != null) {
            response.put("id", id);
        }
        return response;
    }
}