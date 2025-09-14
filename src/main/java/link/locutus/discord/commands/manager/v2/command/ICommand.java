package link.locutus.discord.commands.manager.v2.command;


import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Arg;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import org.json.JSONObject;

import java.util.*;

public interface ICommand<T> extends CommandCallable {
    Class<T> getType();

    Collection<ParameterData> getParameters();

    List<ParameterData> getUserParameters();

    default Map<String, ParameterData> getUserParameterMap() {
        List<ParameterData> params = getUserParameters();
        Map<String, ParameterData> result = new LinkedHashMap<>(params.size());
        for (ParameterData param : params) {
            result.put(param.getName(), param);
        }
        return result;
    }

    default String toCommandArgs(Map<String, String> arguments) {
        Map<String, String> full = new LinkedHashMap<>();
        full.put("", getFullPath());
        full.putAll(arguments);
        return new JSONObject(full).toString();
    }

    String toBasicMarkdown(ValueStore store, PermissionHandler permisser, String prefix, boolean spoiler, boolean links, boolean useFullLinks);

    default Map<String, String> getArgumentAliases(boolean includeUnderscore) {
        Map<String, String> result = new Object2ObjectOpenHashMap<>();
        for (ParameterData param : getUserParameters()) {
            Arg arg = param.getAnnotation(Arg.class);
            if (arg != null && arg.aliases() != null) {
                for (String alias : arg.aliases()) {
                    result.put(alias, param.getName());
                }
            }
            if (param.getName().contains("_")) {
                result.put(param.getName().replace("_", "").toLowerCase(Locale.ROOT), param.getName());
            }
        }
        return result;
    }

    Map<String, Object> toJsonSchema(Map<Key<?>, Map<String, Object>> primitiveCache);
}
