package link.locutus.discord.web.commands;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Ephemeral;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.binding.DBAuthRecord;
import link.locutus.discord.web.jooby.WebRoot;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WebCommands {

    @Command
    @Ephemeral
    public String web(@Me DBNation nation, @Me User user) {
        UUID uuid = WebUtil.generateSecureUUID();
        DBAuthRecord token = WebRoot.db().updateToken(uuid, nation.getId(), user.getIdLong());
        List<String> urls = new ArrayList<>();
        urls.add("**Frontend**: <" + Settings.INSTANCE.WEB.FRONTEND_DOMAIN + "/#login?token=" + uuid + "&nation=" + nation.getNation_id() + ">");
        urls.add("**Backend**: <" + WebRoot.REDIRECT + "/page/login?token=" + token.getUUID() + ">");
        return String.join("\n", urls);
    }
}
