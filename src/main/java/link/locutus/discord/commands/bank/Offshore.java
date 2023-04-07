package link.locutus.discord.commands.bank;

import com.politicsandwar.graphql.model.Bankrec;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.task.balance.BankWithTask;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

<<<<<<< HEAD
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
=======
import java.util.*;
>>>>>>> pr/15

public class Offshore extends Command {
    public Offshore() {
        super("offshore", CommandCategory.ECON);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "offshore <alliance-url> [aa-warchest] [account]";
    }

    @Override
    public String desc() {
<<<<<<< HEAD
        return "Queue a transfer offshore (with authorization)\n" +
                "`aa-warchest` is how much to leave in the AA bank - in the form `{money=1,food=2}`\n" +
                "`account` is what account to offshore into (e.g. your alliance or guild). Defaults to sender alliance";
=======
        return """
                Queue a transfer offshore (with authorization)
                `aa-warchest` is how much to leave in the AA bank - in the form `{money=1,food=2}`
                `#note` is what note to use for the transfer (defaults to deposit)""";
>>>>>>> pr/15
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        if (Roles.ECON.has(user, server) || Roles.INTERNAL_AFFAIRS.has(user, server) || Roles.MILCOM.has(user, server))
            return true;
        DBNation nation = DiscordUtil.getNation(user);
        GuildDB db = Locutus.imp().getGuildDB(server);
        if (db != null && nation != null) {
            if (db.getOrNull(GuildDB.Key.MEMBER_CAN_OFFSHORE) == Boolean.TRUE && Roles.MEMBER.has(user, server))
                return true;

            Set<Integer> aaIds = db.getAllianceIds();
            return (!aaIds.isEmpty() && nation.getPosition() >= Rank.OFFICER.id && aaIds.contains(nation.getAlliance_id()));
        }
        return false;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty() || args.size() > 3) return usage();

        NationOrAllianceOrGuild account = null;
        Map<ResourceType, Double> warchest;
        if (args.size() >= 2) warchest = PnwUtil.parseResources(args.get(1));
        else warchest = Collections.emptyMap();
        if (args.size() >= 3) {
            account = PWBindings.nationOrAllianceOrGuild(args.get(2));
        }

        GuildDB db = Locutus.imp().getGuildDB(guild);
<<<<<<< HEAD

        DBAlliance to = null;
        if (args.size() > 0) to = DBAlliance.getOrCreate(PnwUtil.parseAllianceId(args.get(0)));
        return BankCommands.offshore(author, db, to, warchest, account);
=======
        OffshoreInstance offshore = db.getOffshore();
        int from = db.getOrThrow(GuildDB.Key.ALLIANCE_ID);
        Integer to = offshore == null || offshore.getAllianceId() == from ? null : offshore.getAllianceId();
        if (to == null && args.isEmpty()) {
            return usage();
        } else {
            Set<Integer> offshores = db.getCoalition("offshore");
            to = PnwUtil.parseAllianceId(args.get(0));
            if (to == null) return "Invalid alliance: " + args.get(0);
            if (!offshores.contains(to))
                return "Please add the offshore using `" + Settings.commandPrefix(true) + "setcoalition " + to + " offshore";
        }

        Integer finalTo = to;
        double[] amountSent = ResourceType.getBuffer();

        DBAlliance alliance = db.getAlliance();
        Auth auth = null;
        try {
            auth = db.getAuth(AlliancePermission.WITHDRAW_BANK);
        } catch (IllegalArgumentException ignore) {
        }
        PoliticsAndWarV3 api = alliance.getApi(false, AlliancePermission.WITHDRAW_BANK);
        if (api != null && auth == null) {
            Map<ResourceType, Double> resources = alliance.getStockpile();
            double[] amtToSend = ResourceType.getBuffer();
            for (Map.Entry<ResourceType, Double> entry : resources.entrySet()) {
                double amt = entry.getValue() - warchest.getOrDefault(entry.getKey(), 0d);
                if (amt > 0.01) amtToSend[entry.getKey().ordinal()] = amt;
            }
            if (ResourceType.isEmpty(amtToSend)) return "No funds need to be sent.";

            return "Offshored: " + api.transferFromBank(amtToSend, DBAlliance.get(to), note);
        } else {
            if (auth == null) {
                return "Please authenticate with locutus. Options:\n" +
                        "Option 1: Provide a bot key via `" + Settings.commandPrefix(false) + "credentials addApiKey`\n" +
                        "Option 2: Provide P&W username/password via " + CM.credentials.login.cmd.toSlashMention() + "";
            }
            new BankWithTask(auth, from, to, resources -> {
                Map<ResourceType, Double> sent = new HashMap<>(resources);

                for (Map.Entry<ResourceType, Double> entry : resources.entrySet()) {
                    entry.setValue(warchest.getOrDefault(entry.getKey(), 0d));
                }
                PnwUtil.subResourcesToA(sent, resources);
                for (int i = 0; i < amountSent.length; i++)
                    amountSent[i] = sent.getOrDefault(ResourceType.values[i], 0d);

                Locutus.imp().getRootBank().sync();

                return note;
            }).call();
        }

        return "Sent " + PnwUtil.resourcesToString(amountSent) + " to " + PnwUtil.getName(finalTo, true);
>>>>>>> pr/15
    }
}
