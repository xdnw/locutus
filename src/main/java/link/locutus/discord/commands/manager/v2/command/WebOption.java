package link.locutus.discord.commands.manager.v2.command;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Autocomplete;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.scheduler.TriFunction;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Predicate;

public class WebOption {
    private Key key;
    private boolean allowCompletions;
    private JsonArray options;
    private TriFunction<GuildDB, User, DBNation, JsonArray> queryOptions;
    private boolean requiresGuild;
    private boolean requiresNation;
    private boolean requiresUser;

    private static Set<Class> ignoreClass = new HashSet<>(Arrays.asList(
            Set.class,
            Map.class,
            List.class,
            Predicate.class,
            TypedFunction.class,
            boolean.class,
            Boolean.class,
            int.class,
            Integer.class,
            double.class,
            Double.class,
            long.class,
            Long.class,
            Number.class,
            String.class
    ));

    public WebOption setRequiresGuild() {
        this.requiresGuild = true;
        return this;
    }

    public WebOption setRequiresNation() {
        this.requiresNation = true;
        return this;
    }

    public WebOption setRequiresUser() {
        this.requiresUser = true;
        return this;
    }

    public static List<Class> getComponentClasses(Type type) {
        List<Class> components = new ArrayList<>();
        if (type instanceof ParameterizedType) {
            ParameterizedType pType = (ParameterizedType) type;
            for (Type argType : pType.getActualTypeArguments()) {
                components.addAll(getComponentClasses(argType));
            }
        } else if (type instanceof Class) {
            Class clazz = (Class) type;
            if (ignoreClass.contains(clazz)) return components;
            components.add(clazz);
        }
        return components;
    }

    public WebOption(Class t) {
        this(Key.of(t));
    }

    public WebOption(Key key) {
        this.key = key;
    }

    public static WebOption fromEnum(Class<? extends Enum<?>> enumClass) {
        List<String> names = Arrays.stream(enumClass.getEnumConstants()).map(Enum::name).toList();
        return new WebOption(Key.of(enumClass)).setOptions(names);
    }

    public Key getKey() {
        return key;
    }

    public WebOption setOptions(String[] options) {
        this.options = new JsonArray();
        for (String o : options) this.options.add(o);
        return this;
    }

    public WebOption setOptions(Class<? extends Enum<?>> enumClass) {
        this.options = new JsonArray();
        for (Enum<?> e : enumClass.getEnumConstants()) this.options.add(e.name());
        return this;
    }

    public WebOption setOptions(JsonArray arr) {
        this.options = arr;
        return this;
    }

    public WebOption addOption(Map<String, String> data) {
        if (this.options == null) this.options = new JsonArray();
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            obj.addProperty(entry.getKey(), entry.getValue());
        }
        this.options.add(obj);
        return this;
    }

    public WebOption addOption(JsonObject obj) {
        if (this.options == null) this.options = new JsonArray();
        this.options.add(obj);
        return this;
    }

    public WebOption setOptions(List<String> options) {
        this.options = new JsonArray();
        for (String o : options) this.options.add(o);
        return this;
    }

    public WebOption setQuery(TriFunction<GuildDB, User, DBNation, JsonArray> queryOptions) {
        this.queryOptions = queryOptions;
        return this;
    }

    public WebOption setQueryMap(TriFunction<GuildDB, User, DBNation, List<Map<String, String>>> queryOptions) {
        this.queryOptions = (guild, user, nation) -> {
            JsonArray arr = new JsonArray();
            for (Map<String, String> map : queryOptions.apply(guild, user, nation)) {
                JsonObject obj = new JsonObject();
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    if (entry.getValue() != null) obj.addProperty(entry.getKey(), entry.getValue());
                }
                arr.add(obj);
            }
            return arr;
        };
        return this;
    }

    public WebOption allowCompletions() {
        this.allowCompletions = true;
        return this;
    }

    public WebOption checkAllowCompletions(Key key, ValueStore store) {
        Key completer = key.append(Autocomplete.class);
        if (store.get(completer) != null) allowCompletions();
        return this;
    }

    public JsonArray getOptions() {
        return options;
    }

    public String getName() {
        return key.toSimpleString();
    }

    public boolean isAllowQuery() {
        return queryOptions != null;
    }

    public boolean isAllowCompletions() {
        return allowCompletions;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (options != null) {
            json.add("options", options);
        }
        if (isAllowQuery()) {
            json.addProperty("query", true);
        }
        if (isAllowCompletions()) {
            json.addProperty("completions", true);
        }
        if (requiresGuild) {
            json.addProperty("guild", true);
        }
        if (requiresNation) {
            json.addProperty("nation", true);
        }
        if (requiresUser) {
            json.addProperty("user", true);
        }
        return json;
    }

    public WebOption setType(Class type) {
        this.key = Key.of(type);
        return this;
    }
}
