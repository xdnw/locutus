package link.locutus.discord.commands.manager.v2.command;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import link.locutus.discord.commands.rankings.table.TableNumberFormat;
import link.locutus.discord.commands.rankings.table.TimeFormat;
import link.locutus.discord.commands.rankings.table.TimeNumericTable;
import link.locutus.discord.util.MarkupUtil;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class StringMessageBuilder extends AMessageBuilder {

    public StringMessageBuilder(StringMessageIO parent, long id, long timeCreated, User author) {
        super(parent, id, timeCreated, author);
    }

    public StringMessageBuilder() {
        super(null, 0, System.currentTimeMillis(), null);
    }

    public static StringMessageBuilder fromText(String body, boolean onlyParseJson) {
        StringMessageBuilder msg = new StringMessageBuilder();
        JsonObject bodyJson = null;
        try {
            bodyJson = new Gson().fromJson(body, JsonObject.class);
            boolean contains = false;
            for (String required : new String[]{"content", "embeds", "tables", "attachments"}) {
                if (bodyJson.has(required)) {
                    contains = true;
                    break;
                }
            }
            if (contains) {
                msg.appendJson(bodyJson);
                return msg;
            }
        } catch (Exception ignore) {}
        if (onlyParseJson) return null;
        msg.append(body);
        return msg;
    }

    public static List<StringMessageBuilder> list(User author, String... message) {
        List<StringMessageBuilder> result = new ArrayList<>();
        for (String s : message) {
            result.add(of(author, s));
        }
        return result;
    }

    public static StringMessageBuilder of(User author, String message) {
        StringMessageBuilder builder = new StringMessageBuilder(null, 0, System.currentTimeMillis(), author);
        builder.append(message);
        return builder;
    }

    public static String toHtml(List<StringMessageBuilder> messages, boolean includeFiles) {
        StringBuilder response = new StringBuilder();
        if (messages != null) {
            for (StringMessageBuilder message : messages) {
                response.append(message.toSimpleHtml(includeFiles));
            }
        }
        return response.toString();
    }

    @Override
    public void addJson(Map<String, Object> root, boolean includeFiles, boolean includeButtons) {
        if (!content.isEmpty()) {
            String existing = root.computeIfAbsent("content", k -> "").toString();
            root.put("content", existing + content.toString());
        }
        if (!embeds.isEmpty()) {
            JSONArray embedArray = new JSONArray();
            for (Map.Entry<String, String> entry : embeds.entrySet()) {
                JSONObject embed = new JSONObject();
                embed.put("title", entry.getKey());
                embed.put("description", entry.getValue());
                embedArray.put(embed);
            }
            root.put("embeds", embedArray);
        }
    }

    @Override
    public void sendWhenFree() {
        this.toString();
    }
}
