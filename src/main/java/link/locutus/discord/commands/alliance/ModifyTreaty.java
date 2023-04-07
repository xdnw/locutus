package link.locutus.discord.commands.alliance;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.FACommands;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
<<<<<<< HEAD
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.PendingTreaty;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.AllianceList;
=======
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.PendingTreaty;
>>>>>>> pr/15
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.offshore.Auth;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class ModifyTreaty extends Command {
    private final boolean value;

    public ModifyTreaty(String name, boolean value) {
        super(name, CommandCategory.GOV, CommandCategory.FOREIGN_AFFAIRS);
        this.value = value;
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.FOREIGN_AFFAIRS.has(user, server) && Locutus.imp().getGuildDB(server).hasAlliance();
    }

    @Override
    public String help() {
        return super.help() + " <treaty-id>";
    }

    @Override
    public String desc() {
        return "Use `" + Settings.commandPrefix(true) + "treaties` to list the current treaties.";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(event);
        GuildDB db = Locutus.imp().getGuildDB(guild);
<<<<<<< HEAD
        AllianceList aaList = Roles.FOREIGN_AFFAIRS.getAllianceList(author, db);
        if (aaList.isEmpty()) return "Missing roles: " + Roles.FOREIGN_AFFAIRS.toDiscordRoleNameElseInstructions(guild);
        Set<DBAlliance> senders = PWBindings.alliances(guild, args.get(0));
        if (value) {
            return FACommands.approveTreaty(aaList, senders);
        } else {
            return FACommands.cancelTreaty(aaList, senders);
        }
=======
        Auth auth = db.getAuth(AlliancePermission.MANAGE_TREATIES);
        if (auth == null) return "No authentication enabled for this guild.";

        List<PendingTreaty> treaties = auth.getTreaties();
        if (!value) treaties.removeIf(treaty -> treaty.status != PendingTreaty.TreatyStatus.ACTIVE);
        if (value) treaties.removeIf(treaty -> treaty.status != PendingTreaty.TreatyStatus.PENDING);
        treaties.removeIf(treaty -> treaty.getFromId() != treatyOrAAId && treaty.getToId() != treatyOrAAId && treaty.getId() != treatyOrAAId);
        if (treaties.isEmpty()) return "There are no active treaties.";

        boolean admin = Roles.ADMIN.has(author, db.getGuild()) || (me.getAlliance_id() == db.getAlliance_id() && me.getPosition() >= Rank.HEIR.id);

        for (PendingTreaty treaty : treaties) {
            if (!admin && treaty.getType().getStrength() >= TreatyType.PROTECTORATE.getStrength()) {
                return "You need to be an admin to cancel a defensive treaty.";
            }
            return auth.modifyTreaty(treaty.getId(), value);
        }
        return "No treaty found.";
>>>>>>> pr/15
    }
}
