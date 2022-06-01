package link.locutus.discord.commands.info;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.war.SpyCommand;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Collections;
import java.util.List;

public class MeCommand extends Command {
    private final Who who;

    public MeCommand(DiscordDB db, SpyCommand cmd) {
        super("me", CommandCategory.GAME_INFO_AND_TOOLS);
        this.who = new Who(cmd);
    }

    @Override
    public String help() {
        return "!me";
    }

    @Override
    public String desc() {
        return "Get pnw info about yourself";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        DBNation me = DiscordUtil.getNation(event);
        if (me == null) {
            return "Please use !validate";
        }
        return who.onCommand(event, Collections.singletonList(me.getNation_id() + ""));
    }
}
