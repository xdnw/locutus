package link.locutus.discord.commands.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class Embassy extends Command {
    public Embassy() {
        super(CommandCategory.FOREIGN_AFFAIRS, CommandCategory.USER_COMMANDS);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.embassy.cmd);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (me == null) {
            return "Please use " + CM.register.cmd.toSlashMention();
        }
        GuildDB db = Locutus.imp().getGuildDB(guild);
        Category category = db.getOrThrow(GuildKey.EMBASSY_CATEGORY);
        if (category == null) {
            return "Embassies are disabled. To set it up, use " + GuildKey.EMBASSY_CATEGORY.getCommandMention();
        }
        DBNation nation = me;
        if (args.size() == 1 && args.get(0).equalsIgnoreCase("*")) {
            if (!Roles.ADMIN.has(author, guild)) return "No permission.";
            Map<Integer, Role> aaRoles = DiscordUtil.getAARoles(guild.getRoles());
            for (TextChannel catChan : category.getTextChannels()) {
                String[] split = catChan.getName().split("-");
                if (split.length <= 1) continue;
                String allianceIdStr = split[split.length - 1];
                if (MathMan.isInteger(allianceIdStr)) {
                    int allianceId = Integer.parseInt(split[split.length - 1]);
                    Role role = aaRoles.get(allianceId);
                    if (role != null) {
                        FACommands.updateEmbassyPerms(catChan, role, author, true);
                    }
                }
            }
            return "Done!";
        } else if (args.size() == 1) {
            nation = PWBindings.nation(author, guild, args.get(0));
            if (!me.equals(nation) && !Roles.FOREIGN_AFFAIRS.has(author, guild)) return "You do not have FOREIGN_AFFAIRS";
        }

        DBAlliance aa = me.getAlliance();
        if (aa == null || aa.getAlliance_id() == 0) {
            return "You are not in an alliance.";
        }
        String aaName = aa.getName();

        Role role = DiscordUtil.getAARoles(guild.getRoles()).get(aa.getAlliance_id());
        if (role == null) {
            return "No role found (try using " + CM.role.autoassign.cmd.toSlashMention() + " ?)";
        }

        for (TextChannel catChan : category.getTextChannels()) {
            String[] split = catChan.getName().split("-");
            if (MathMan.isInteger(split[split.length - 1]) && Integer.parseInt(split[split.length - 1]) == aa.getAlliance_id()) {
                FACommands.updateEmbassyPerms(catChan, role, author, true);
                return "Embassy: <#" + catChan.getId() + ">";
            }
        }
        if (me.getPosition() <= 1) {
            return "You must be a member to create an embassy.";
        }

        String embassyName = aaName + "-" + aa;

        TextChannel embassyChan = RateLimitUtil.complete(category.createTextChannel(embassyName).setParent(category).setTopic("Embassy: " + aa.getMarkdownUrl()));

        StringBuilder body = new StringBuilder();
        // Alliance name/link
        body.append("## Embassy for: " + aa.getMarkdownUrl() + " `#" + aa.getRank() + "`\n");
        // score
        body.append("-# color:`" + aa.getColor().name() + "` | " +
                "Score:" + aa.getScore() + " | " +
                "Members:" + aa.countMembers() + " | " +
                "Taxable:" + aa.getNations(true, 0, true).size());
        if (!aa.getDiscord_link().isEmpty()) {
            body.append("- **Discord**: <" + aa.getDiscord_link() + ">\n");
        }
        if (!aa.getFlag().isEmpty()) {
            body.append("- **Flag**: " + aa.getFlag() + "\n");
        }
        Map<Integer, Treaty> treaties = aa.getTreaties();
        if (!treaties.isEmpty()) {
            body.append("***Treaties**:\n");
            for (Map.Entry<Integer, Treaty> entry : treaties.entrySet()) {
                Treaty treaty = entry.getValue();
                DBAlliance other = DBAlliance.get(entry.getKey());
                if (other == null) continue;
                body.append("- " + treaty.getType().getName() + ": " + other.getMarkdownUrl() + "\n");
            }
        } else {
            body.append("> No treaties found\n");
        }

        FACommands.updateEmbassyPerms(embassyChan, role, author, true);

        RateLimitUtil.queue(embassyChan.sendMessage(body.toString()));

        return "Embassy: <#" + embassyChan.getId() + ">";
    }
}
