package link.locutus.discord.commands.manager.v2.command;


import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import org.json.JSONObject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface ICommand extends CommandCallable {
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

    String toBasicMarkdown(ValueStore store, PermissionHandler permisser, String prefix, boolean spoiler, boolean links);
}
