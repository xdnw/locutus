package link.locutus.discord.web.commands;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.util.scheduler.QuadConsumer;
import link.locutus.discord.web.WebUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

public class NationListPages {
    @Command
    public Object filtersJson(ArgumentStack stack) {
        NationPlaceholders np = new NationPlaceholders(stack.getStore(), stack.getValidators(), stack.getPermissionHandler());
        List<CommandCallable> filters = np.getFilterCallables();
        JsonArray array = new JsonArray();
        for (CommandCallable filter : filters) {
            array.add(filter.getPrimaryCommandId());
        }
        JsonObject obj = new JsonObject();
        obj.add("names", array);
        return obj.toString();
    }

    @Command
    public Object filters(ArgumentStack stack) {
        NationPlaceholders np = new NationPlaceholders(stack.getStore(), stack.getValidators(), stack.getPermissionHandler());
        List<CommandCallable> filters = np.getFilterCallables();
        return WebUtil.generateSearchableDropdown("Filter", "Pick a filter", null, true, filters, new QuadConsumer<CommandCallable, JsonArray, JsonArray, JsonArray>() {
            @Override
            public void consume(CommandCallable cmd, JsonArray names, JsonArray values, JsonArray subtext) {
                names.add(cmd.getPrimaryCommandId());
            }
        });
    }

    @Command
    public Object filter(ArgumentStack stack, String filter, String parentId) {
        NationPlaceholders np = new NationPlaceholders(stack.getStore(), stack.getValidators(), stack.getPermissionHandler());
        return np.getHtml(stack.getStore(), filter, parentId);
    }


}
