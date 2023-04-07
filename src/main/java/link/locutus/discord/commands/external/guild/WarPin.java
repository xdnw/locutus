package link.locutus.discord.commands.external.guild;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.war.WarCategory;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import rocker.guild.ia.message;

import java.util.List;
import java.util.Set;

public class WarPin extends Command {
    public WarPin() {
        super(CommandCategory.MILCOM, CommandCategory.MEMBER);
    }

    @Override
    public String desc() {
        return "Update the pin in the war room.";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        GuildDB db = Locutus.imp().getGuildDB(event);
        WarCategory warChannels = db.getWarChannel();
        if (warChannels == null) return "War channels are not enabled.";

        WarCategory.WarRoom warRoom = warChannels.getWarRoom(event.getGuildChannel());
        if (warRoom == null) return "This command must be run in a war room.";

        IMessageBuilder message = warRoom.updatePin(true);
        if (message == null) return "No war pin found.";
        TextChannel channel = warRoom.channel;
        String url = DiscordUtil.getChannelUrl(channel) + "/" + message.getId();
        return "Updated: " + url;
    }
}
