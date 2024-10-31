package link.locutus.discord.web.commands.binding;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import io.javalin.http.Context;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.web.WebUtil;
import net.dv8tion.jda.api.entities.Guild;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
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
        System.out.println(":||remove Input " + input);
        return WebUtil.GSON.fromJson(input, Map.class);
    }

    private static final TypeToken<List<List<Object>>> LIST_TOKEN = new TypeToken<>() {};

    @Binding
    public List<Map.Entry<String, Map<String, Object>>> jsonList(String input) {
        List<List<Object>> result = WebUtil.GSON.fromJson(input, LIST_TOKEN.getType());
        List<Map.Entry<String, Map<String, Object>>> cast = new ObjectArrayList<>();
        for (List<Object> entry : result) {
            if (entry.size() != 2) {
                throw new IllegalArgumentException("Invalid entry: " + entry);
            }
            if (!(entry.get(0) instanceof String)) {
                throw new IllegalArgumentException("Invalid key: " + entry.get(0));
            }
            if (!(entry.get(1) instanceof Map)) {
                throw new IllegalArgumentException("Invalid value: " + entry.get(1));
            }
            cast.add(Map.entry((String) entry.get(0), (Map<String, Object>) entry.get(1)));
        }
        return cast;
    }
}
