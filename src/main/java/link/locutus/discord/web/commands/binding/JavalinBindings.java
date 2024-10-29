package link.locutus.discord.web.commands.binding;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import io.javalin.http.Context;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.web.WebUtil;
import net.dv8tion.jda.api.entities.Guild;

import java.util.Map;

public class JavalinBindings extends BindingHelper {
    @Binding
    public Context context() {
        throw new IllegalStateException("No context set in command locals");
    }

    @Binding
    public WebStore webStore() {
        throw new IllegalStateException("No WebStore set in command locals");
    }

    @Binding
    public DBAuthRecord auth(Context context) {
        throw new IllegalStateException("No DBAuthRecord set in command locals");
    }


    @Binding
    public JsonObject json(String input) {
        return WebUtil.GSON.fromJson(input, JsonObject.class);
    }

    @Binding
    public Map<String, Object> jsonMap(String input) {
        System.out.println("Input " + input);
        return WebUtil.GSON.fromJson(input, Map.class);
    }
}
