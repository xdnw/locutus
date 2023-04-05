package link.locutus.discord.commands.bank;

import com.politicsandwar.graphql.model.Bankrec;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.task.balance.BankWithTask;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Offshore extends Command {
    public Offshore() {
        super("offshore", CommandCategory.ECON);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "offshore <alliance-url> [aa-warchest] [#note]";
    }

    @Override
    public String desc() {
        return "Queue a transfer offshore (with authorization)\n" +
                "`aa-warchest` is how much to leave in the AA bank - in the form `{money=1,food=2}`\n" +
                "`#note` is what note to use for the transfer (defaults to deposit)";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        if (Roles.ECON.has(user, server) || Roles.INTERNAL_AFFAIRS.has(user, server) || Roles.MILCOM.has(user, server)) return true;
        DBNation nation = DiscordUtil.getNation(user);
        GuildDB db = Locutus.imp().getGuildDB(server);
        if (db != null && nation != null) {
            if (db.getOrNull(GuildDB.Key.MEMBER_CAN_OFFSHORE) == Boolean.TRUE && Roles.MEMBER.has(user, server)) return true;

            Set<Integer> aaIds = db.getAllianceIds();
            return (!aaIds.isEmpty() && nation.getPosition() >= Rank.OFFICER.id && aaIds.contains(nation.getAlliance_id()));
        }
        return false;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty() || args.size() > 3) return usage();

        Map<ResourceType, Double> warchest;
        String note;
        if (args.size() >= 2) warchest = PnwUtil.parseResources(args.get(1));
        else warchest = Collections.emptyMap();
        if (args.size() >= 3) note = args.get(2);
        else note = "#tx_id=" + UUID.randomUUID();

        GuildDB db = Locutus.imp().getGuildDB(guild);

        DBAlliance to = null;
        if (args.size() > 0) to = DBAlliance.getOrCreate(PnwUtil.parseAllianceId(args.get(0)));
        return BankCommands.offshore(author, db, to, warchest, note);
    }
}
