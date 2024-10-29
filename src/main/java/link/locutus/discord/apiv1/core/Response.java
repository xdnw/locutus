package link.locutus.discord.apiv1.core;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import link.locutus.discord.apiv1.PoliticsAndWarAPIException;
import link.locutus.discord.apiv1.domains.Alliances;
import link.locutus.discord.apiv1.domains.Entity;
import link.locutus.discord.apiv1.domains.Nations;
import link.locutus.discord.apiv1.domains.Wars;
import link.locutus.discord.web.WebUtil;

import java.lang.reflect.Type;

public class Response<T extends Entity> {

    private final String jsonStr;
    private final T t;
    private final String url;

    public Response(String jsonStr, T t, String url) {
        this.jsonStr = jsonStr;
        this.t = t;
        this.url = Utility.obfuscateApiKey(url);
    }

    public String getJson() {
        return jsonStr;
    }

    public T getEntity() throws JsonSyntaxException {
        JsonElement jsonElement = new JsonParser().parse(jsonStr);
        JsonObject jsonObject = jsonElement.getAsJsonObject();

        if (jsonObject.has("error") || jsonObject.has("error_message") ||
                jsonObject.has("general_message") || jsonObject.has("message")) {
            if (jsonObject.has("general_message"))
                throw new PoliticsAndWarAPIException("Unsuccessful API request. Error Received: " + jsonObject.get("general_message").getAsString(), url);
            else if (jsonObject.has("error"))
                throw new PoliticsAndWarAPIException("Unsuccessful API request. Error Received: " + jsonObject.get("error").getAsString(), url);
            else if (jsonObject.has("error_message"))
                throw new PoliticsAndWarAPIException("Unsuccessful API request. Error Received: " + jsonObject.get("error_message").getAsString(), url);
            else if (jsonObject.has("message"))
                throw new PoliticsAndWarAPIException("Unsuccessful API request. Error Received: " + jsonObject.get("message").getAsString(), url);
        }
        Type type = TypeToken.get(t.getClass()).getType();
        return WebUtil.GSON.fromJson(jsonStr, type);
    }
}