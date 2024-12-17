package link.locutus.discord.web.jooby.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import link.locutus.discord.web.jooby.WebRoot;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface IMessageOutput {
    void sendEvent(byte[] data);

    default void sendEvent(Map<String, Object> obj) {
        ObjectMapper serializer = WebRoot.getInstance().getPageHandler().getSerializer();
        try {
            byte[] data = serializer.writeValueAsBytes(obj);
            sendEvent(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    void sendEvent(String toString);
}
