package link.locutus.discord.commands.manager.result;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public abstract class CmdResult {
    private final JsonObject obj;

    public CmdResult(boolean success) {
        this.obj = new JsonObject();
        set("success", success);
    }

    public CmdResult message(String message) {
        this.obj.addProperty("message", message);
        return this;
    }

    public CmdResult set(String key, Number value) {
        this.obj.addProperty(key, value);
        return this;
    }

    public CmdResult set(String key, Boolean value) {
        this.obj.addProperty(key, value);
        return this;
    }

    public CmdResult set(String key, Character value) {
        this.obj.addProperty(key, value);
        return this;
    }

    public CmdResult set(String key, String value) {
        this.obj.addProperty(key, value);
        return this;
    }

    public CmdResult set(String key, JsonElement value) {
        this.obj.add(key, value);
        return this;
    }

    public JsonObject json() {
        return this.obj;
    }

    public String getMessage() {
        JsonElement msg = obj.get("message");
        return msg == null ? null : msg.getAsString();
    }

    public abstract void build(MessageReceivedEvent event);
}
