package link.locutus.discord.commands.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.task.roles.IAutoRoleTask;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.util.List;
import java.util.Set;

public class AutoRole extends Command {
    public AutoRole() {
        super("autorole", CommandCategory.USER_SETTINGS, CommandCategory.GUILD_MANAGEMENT, CommandCategory.INTERNAL_AFFAIRS);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.role.autoassign.cmd);
    }

    @Override
    public String help() {
        return super.help() + " <user|*>";
    }

    @Override
    public String desc() {
        return "Auto-Role discord users with registered role and alliance role.";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) {
            return usage(args.size(), 1, channel);
        }
        boolean force = flags.contains('f');

        GuildDB db = Locutus.imp().getGuildDB(guild);
        if (db == null) return "No registered guild.";
        IAutoRoleTask task = db.getAutoRoleTask();
        task.syncDB();

        StringBuilder response = new StringBuilder();

        if (args.get(0).equalsIgnoreCase("*")) {
            if (!Roles.INTERNAL_AFFAIRS.has(author, guild)) return "No permission";
            JSONObject command = CM.role.autoassign.cmd.force(force + "").toJson();
            return UtilityCommands.autoroleall(author, db, channel, command, force);
        } else {
            DBNation nation = DiscordUtil.parseNation(args.get(0));
            if (nation == null) return "That nation isn't registered: `" + args.get(0) + "` see:" + CM.register.cmd.toSlashMention() + "";
            User user = nation.getUser();
            if (user == null) return "User is not registered.";
            Member member = db.getGuild().getMember(user);
            if (member == null) return "Member not found in guild: " + DiscordUtil.getFullUsername(user);
            JSONObject command = CM.role.autorole.cmd.member(user.getAsMention()).force(force + "").toJson();
            return UtilityCommands.autorole(db, channel, command, member, force);
        }
    }
}