package link.locutus.discord.web.jooby.handler;

import link.locutus.discord.web.jooby.adapter.JoobyMessageAction;
import com.google.gson.JsonObject;

public interface IMessageOutput {
    default void sendEvent(JoobyMessageAction action) {
        sendEvent(action.toJson().toString());
    }
    default void sendEvent(JsonObject obj) {
        sendEvent(obj.toString());
    }
    void sendEvent(String toString);
}
