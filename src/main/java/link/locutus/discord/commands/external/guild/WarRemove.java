package link.locutus.discord.commands.external.guild;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.war.WarCategory;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class WarRemove extends Command {
    public WarRemove() {
        super(CommandCategory.MILCOM);
    }
    @Override
    public String help() {
        return super.help() + " <nation>";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(event);
        GuildDB db = Locutus.imp().getGuildDB(event);
        WarCategory warChannels = db.getWarChannel();
        if (warChannels == null) return "War channels are not enabled";

        WarCategory.WarRoom waRoom = warChannels.getWarRoom(event.getGuildChannel());
        if (waRoom == null) return "This command must be run in a war room";

        Set<DBNation> nation = DiscordUtil.parseNations(guild, args.get(0));
        return super.onCommand(event, guild, author, me, args, flags);
    }
}
