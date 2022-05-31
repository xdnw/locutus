package com.boydti.discord.web.jooby.handler;

import com.boydti.discord.web.jooby.adapter.JoobyMessageAction;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.utils.data.DataObject;

public interface IMessageOutput {
    default void sendEvent(JoobyMessageAction action) {
        sendEvent(action.toJson().toString());
    }
    default void sendEvent(JsonObject obj) {
        sendEvent(obj.toString());
    }
    void sendEvent(String toString);
}
