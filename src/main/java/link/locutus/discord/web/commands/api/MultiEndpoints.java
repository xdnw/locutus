package link.locutus.discord.web.commands.api;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.AllowDeleted;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.task.multi.MultiResult;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.WebGraph;

public class MultiEndpoints {
    @Command
    @ReturnType(MultiResult.class)
    public MultiResult multi_buster(@AllowDeleted DBNation nation) {
        MultiResult result = Locutus.imp().getDiscordDB().getMultiResult(nation.getId());
        Auth auth = Locutus.imp().getRootAuth();
        result.updateIfOutdated(auth, Long.MAX_VALUE, true);
        return result.loadNames();
    }
}
