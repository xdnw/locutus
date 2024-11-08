package link.locutus.discord.commands.manager.v2.command;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Autocomplete;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.scheduler.TriFunction;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.binding.value_types.WebOptions;
import net.dv8tion.jda.api.entities.User;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Predicate;

public class WebOption {
    private Key key;
    private boolean allowCompletions;
    private List<String> options;
    private TriFunction<GuildDB, User, DBNation, WebOptions> queryOptions;
    private List<String> compositeTypes;
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

    public WebOption setCompositeTypes(String... types) {
        this.compositeTypes = Arrays.asList(types);
        return this;
    }

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
        this.options = new ObjectArrayList<>();
        for (String o : options) this.options.add(o);
        return this;
    }

    public WebOption setOptions(Class<? extends Enum<?>> enumClass) {
        this.options = new ObjectArrayList<>();
        for (Enum<?> e : enumClass.getEnumConstants()) this.options.add(e.name());
        return this;
    }


    public WebOption setOptions(List<String> options) {
        this.options = options;
        return this;
    }

    public WebOption setQueryMap(TriFunction<GuildDB, User, DBNation, WebOptions> queryOptions) {
        this.queryOptions = (guild, user, nation) -> {
            return queryOptions.apply(guild, user, nation);
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

    public List<String> getOptions() {
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

    public WebOptions getQueryOptions(GuildDB guild, User user, DBNation nation) {
        if (queryOptions == null) return null;
        return queryOptions.apply(guild, user, nation);
    }

    public boolean isRequiresGuild() {
        return requiresGuild;
    }

    public boolean isRequiresNation() {
        return requiresNation;
    }

    public boolean isRequiresUser() {
        return requiresUser;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (options != null) {
            json.add("options", WebUtil.GSON.toJsonTree(options));
        }
        if (compositeTypes != null) {
            json.add("composite", WebUtil.GSON.toJsonTree(compositeTypes));
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
