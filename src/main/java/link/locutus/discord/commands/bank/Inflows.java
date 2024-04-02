package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;

public class Inflows extends Command {
    public Inflows() {
        super("transfers", "inflows", "outflows", CommandCategory.ECON);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "transfers <nation|alliance|coalition> <days>";
    }

    @Override
    public String desc() {
        return "Analyze the inflows of a nation or alliance over a period of time.";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 2) {
            return usage(args.size(), 2, channel);
        }
        if (!MathMan.isInteger(args.get(1))) {
            return "Invalid number of days: `" + args.get(1) + "`";
        }

        Integer days = MathMan.parseInt(args.get(1));
        if (days == null) {
            return "Invalid number of days: `" + args.get(1) + "`";
        }
        long cutoffMs = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L;

        String arg0 = args.get(0);
        Integer nationId = DiscordUtil.parseNationId(arg0);

        List<Transaction2> allTransfers = new ArrayList<>();

        Set<Integer> self;
        String selfName;
        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNations();
        Function<Integer, String> nationNameFunc = i -> {
            DBNation nation = nations.get(i);
            return nation == null ? Integer.toString(i) : nation.getNation();
        };

        Function<Integer, String> aaNameFunc = i -> Locutus.imp().getNationDB().getAllianceName(i);

        if (nationId == null || arg0.contains("/alliance/") || arg0.charAt(0) == '~') {
            if (arg0.charAt(0) == '~') {
                arg0 = args.get(0).substring(1);
                self = Locutus.imp().getGuildDB(guild).getCoalition(arg0);
                selfName = arg0;
            } else {
                Integer selfId = PW.parseAllianceId(arg0);
                self = selfId != null ? Collections.singleton(selfId) : null;
                selfName = selfId == null ? "" : Locutus.imp().getNationDB().getAllianceName(selfId);
            }
            if (self == null || self.isEmpty()) {
                return "Not found: `" + Settings.commandPrefix(true) + "pnw-who <user>`";
            }

            for (Integer allianceId : self) {
                allTransfers.addAll(Locutus.imp().getBankDB().getAllianceTransfers(allianceId, cutoffMs));
            }
        } else {
            self = Collections.singleton(nationId);

            DBNation nation = Locutus.imp().getNationDB().getNation(nationId);
            selfName = nation == null ? Integer.toString(nationId) : nation.getNation();

            allTransfers.addAll(Locutus.imp().getBankDB().getNationTransfers(nationId, cutoffMs));

            List<DBTrade> trades = Locutus.imp().getTradeManager().getTradeDb().getTrades(nationId, cutoffMs);
            for (DBTrade offer : trades) {
                int per = offer.getPpu();
                ResourceType type = offer.getResource();
                if (per > 1 && (per < 10000 || (type != ResourceType.FOOD && per < 100000))) {
                    continue;
                }
                Transaction2 transfer = new Transaction2(offer);
                allTransfers.add(transfer);
            }
        }

        Map<Integer, List<Transaction2>> aaInflow = new HashMap<>();
        Map<Integer, List<Transaction2>> nationInflow = new HashMap<>();

        Map<Integer, List<Transaction2>> aaOutflow = new HashMap<>();
        Map<Integer, List<Transaction2>> nationOutflow = new HashMap<>();

        for (Transaction2 transfer : allTransfers) {
            if (transfer.note != null && transfer.note.contains("'s nation and captured.")) continue;
            int sender = (int) transfer.getSender();
            int receiver = (int) transfer.getReceiver();

            Map<Integer, List<Transaction2>> map;
            int other;
            if (!self.contains(receiver)) {
                other = receiver;
                map = transfer.receiver_type == 2 ? aaOutflow : nationOutflow;
            } else if (!self.contains(sender)) {
                other = sender;
                map = transfer.sender_type == 2 ? aaInflow : nationInflow;
            } else {
                // Internal transfer
                continue;
            }

            List<Transaction2> list = map.computeIfAbsent(other, k -> new ArrayList<>());
            list.add(transfer);
        }

        StringBuilder msg = new StringBuilder();
        if ((!aaInflow.isEmpty() || !nationInflow.isEmpty()) && !flags.contains('i')) {
            msg.append("Net inflows:\n");
            msg.append(send(selfName, aaNameFunc, aaInflow, true)).append("\n");
            msg.append(send(selfName, nationNameFunc, nationInflow, true)).append("\n");
        }
        if ((!aaOutflow.isEmpty() || !nationOutflow.isEmpty()) && !flags.contains('o')) {
            msg.append("Net outflows:\n");
            msg.append(send(selfName, aaNameFunc, aaOutflow, false)).append("\n");
            msg.append(send(selfName, nationNameFunc, nationOutflow, false)).append("\n");
        }

        if (aaInflow.isEmpty() && nationInflow.isEmpty() && aaOutflow.isEmpty() && nationOutflow.isEmpty()) {
            return "No results.";
        } else {
            return msg.toString();
        }
    }

    private String send(String selfName, Function<Integer, String> nameFunc, Map<Integer, List<Transaction2>> transferMap, boolean inflow) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<Integer, List<Transaction2>> entry : transferMap.entrySet()) {
            int id = entry.getKey();

            String name = nameFunc.apply(id);

            List<Transaction2> transfers = entry.getValue();
            String title = inflow ? name + " > " + selfName : selfName + " > " + name;

            StringBuilder message = new StringBuilder();

            Map<ResourceType, Double> totals = new HashMap<>();
            for (Transaction2 transfer : transfers) {
                double[] rss = transfer.resources.clone();
                totals = ResourceType.add(totals, ResourceType.resourcesToMap(rss));
            }

            message.append(ResourceType.resourcesToString(totals));

            result.append(title).append(": ").append(message).append("\n");
        }
        return result.toString();
    }
}