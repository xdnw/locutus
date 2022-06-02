package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.config.Settings;

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

    public static String formatDescription(Command command) {
        String desc = command.desc();
        if (desc.contains("{")) {
            desc = desc.replace("{legacy_prefix}", Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "");
            desc = desc.replace("{prefix}", Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX + "");
        }
        return desc;
    }
}
