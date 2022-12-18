package link.locutus.discord.commands.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GuildInfo extends Command {
    public GuildInfo() {
        super(CommandCategory.INTERNAL_AFFAIRS, CommandCategory.GUILD_MANAGEMENT);
    }
    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.INTERNAL_AFFAIRS_STAFF.has(user, server);
    }

    @Override
    public String usage() {
        return super.usage() + " <guild-id>";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) args = List.of(guild.getId());
        long id = Long.parseLong(args.get(0));

        guild = Locutus.imp().getDiscordApi().getGuildById(id);
        if (guild == null) return "Guild not found";

        String title = guild.getName() + "/" + guild.getIdLong() + "\n" +
                "Owner: " + guild.getOwner() + "\n" +
                "Admins: " + StringMan.getString(guild.getMembers());
        return title;
    }
}
