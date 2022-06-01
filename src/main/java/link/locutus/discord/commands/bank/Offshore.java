package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.task.balance.BankWithTask;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

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
        return "!offshore <alliance-url> [aa-warchest] [#note]";
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
            if (db.getOrNull(GuildDB.Key.MEMBER_CAN_OFFSHORE) == Boolean.TRUE) return true;

            Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
            return (aaId != null && nation.getPosition() >= Rank.OFFICER.id && nation.getAlliance_id() == aaId);
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
        Auth auth = db.getAuth();
        if (auth == null) return "No authentication enabled for this guild";
        OffshoreInstance offshore = db.getOffshore();
        int from = db.getOrThrow(GuildDB.Key.ALLIANCE_ID);
        Integer to = offshore == null || offshore.getAllianceId() == from ? null : offshore.getAllianceId();
        if (to == null && args.isEmpty()) {
            return usage();
        } else {
            Set<Integer> offshores = db.getCoalition("offshore");
            to = PnwUtil.parseAllianceId(args.get(0));
            if (to == null) return "Invalid alliance: " + args.get(0);
            if (!offshores.contains(to)) return "Please add the offshore using `!setcoalition " + to + " offshore";
        }

        Integer finalTo = to;
        double[] amountSent = ResourceType.getBuffer();

        new BankWithTask(auth, from, to, resources -> {
            Map<ResourceType, Double> sent = new HashMap<>(resources);

            Iterator<Map.Entry<ResourceType, Double>> iterator = resources.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<ResourceType, Double> entry = iterator.next();
                entry.setValue(warchest.getOrDefault(entry.getKey(), 0d));
            }
            sent = PnwUtil.subResourcesToA(sent, resources);
            for (int i = 0; i < amountSent.length; i++) amountSent[i] = sent.getOrDefault(ResourceType.values[i], 0d);

            Locutus.imp().getRootBank().sync();

            return note;
        }).call();

        return "Sent " + PnwUtil.resourcesToString(amountSent) + " to " + PnwUtil.getName(finalTo, true);
    }
}
