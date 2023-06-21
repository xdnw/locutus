package link.locutus.discord.web.jooby.handler;

import com.google.gson.JsonObject;

public interface IMessageOutput {
    default void sendEvent(JsonObject obj) {
        sendEvent(obj.toString());
    }
    void sendEvent(String toString);
}
