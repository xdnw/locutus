package com.boydti.discord.commands.bank;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.config.Settings;
import com.boydti.discord.db.entities.Transaction2;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.trade.Offer;
import com.google.common.collect.BiMap;
import com.boydti.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class Inflows extends Command {
    public Inflows() {
        super("transfers", "inflows", "outflows", CommandCategory.ECON);
    }

    private final String URL_BASE = "" + Settings.INSTANCE.PNW_URL() + "/%s/id=%s";
    private final String EMOJI_FOLLOW = "\u27a1\ufe0f";
    private final String EMOJI_QUESTION = "\u2753";

    @Override
    public String help() {
        return "!transfers <nation|alliance|coalition> <days>";
    }

    @Override
    public String desc() {
        return "Analyze the inflows of a nation or alliance over a period of time";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 2) {
            return usage(event);
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

        BiMap<Integer, String> allianceNames = Locutus.imp().getNationDB().getAlliances();
        Function<Integer, String> aaNameFunc = i -> allianceNames.getOrDefault(i, Integer.toString(i));

        if (nationId == null || arg0.contains("/allaince/") || arg0.charAt(0) == '~') {
            if (arg0.charAt(0) == '~') {
                arg0 = args.get(0).substring(1);
                self = Locutus.imp().getGuildDB(event).getCoalition(arg0);
                selfName = arg0;
            } else {
                Integer selfId = PnwUtil.parseAllianceId(arg0);
                self = selfId != null ? Collections.singleton(selfId) : null;
                selfName = selfId == null ? "" : Locutus.imp().getNationDB().getAllianceName(selfId);
            }
            if (self == null || self.isEmpty()) {
                return "Not found: `!pnw-who <user>`";
            }

            for (Integer allianceId : self) {
                allTransfers.addAll(Locutus.imp().getBankDB().getAllianceTransfers(allianceId, cutoffMs));
            }
        } else {
            self = Collections.singleton(nationId);

            DBNation nation = Locutus.imp().getNationDB().getNation(nationId);
            selfName = nation == null ? Integer.toString(nationId) : nation.getNation();

            allTransfers.addAll(Locutus.imp().getBankDB().getNationTransfers(nationId, cutoffMs));

            List<Offer> trades = Locutus.imp().getTradeManager().getTradeDb().getOffers(nationId, cutoffMs);
            for (Offer offer : trades) {
                int per = offer.getPpu();
                ResourceType type = offer.getResource();
                if (per > 1 && (per < 10000 || (type != ResourceType.FOOD && per < 100000))) {
                    continue;
                }
                long amount = offer.getAmount();
                if (per <= 1) {
                    amount = offer.getTotal();
                    type = ResourceType.MONEY;
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
            if (transfer.note != null && transfer.note.contains("'s nation and captured")) continue;
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

            List<Transaction2> list = map.get(other);
            if (list == null) {
                list = new ArrayList<>();
                map.put(other, list);
            }
            list.add(transfer);
        }

        MessageChannel channel = event.getChannel();
        if ((!aaInflow.isEmpty() || !nationInflow.isEmpty()) && !flags.contains('i')) {
            channel.sendMessage("Net inflows: ").complete();
            send(channel, selfName, aaNameFunc, "alliance", aaInflow, days, true);
            send(channel, selfName, nationNameFunc, "nation", nationInflow, days, true);
        }
        if ((!aaOutflow.isEmpty() || !nationOutflow.isEmpty()) && !flags.contains('o')) {
            channel.sendMessage("Net outflows: ").complete();
            send(channel, selfName, aaNameFunc, "alliance", aaOutflow, days, false);
            send(channel, selfName, nationNameFunc, "nation", nationOutflow, days, false);
        }

        if (aaInflow.isEmpty() && nationInflow.isEmpty() && aaOutflow.isEmpty() && nationOutflow.isEmpty()) {
            return "No results.";
        } else {
            return "Done!";
        }
    }

    private void send(MessageChannel channel, String selfName, Function<Integer, String> nameFunc, String typeOther, Map<Integer, List<Transaction2>> transferMap, int days, boolean inflow) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<Integer, List<Transaction2>> entry : transferMap.entrySet()) {
            int id = entry.getKey();

            String url = String.format(URL_BASE, typeOther, id);
            String name = nameFunc.apply(id);

            List<Transaction2> transfers = entry.getValue();
            String title = inflow ? name + " > " + selfName : selfName + " > " + name;
            String followCmd = "!inflows " + url + " " + days;

            StringBuilder message = new StringBuilder();

            Map<ResourceType, Double> totals = new HashMap<>();
            for (Transaction2 transfer : transfers) {
                double[] rss = transfer.resources.clone();
                totals = PnwUtil.add(totals, PnwUtil.resourcesToMap(rss));
            }

            message.append(PnwUtil.resourcesToString(totals));

            String infoCmd = "!pw-who " + url;
//            Message msg = PnwUtil.createEmbedCommand(channel, title, message.toString(), EMOJI_FOLLOW, followCmd, EMOJI_QUESTION, infoCmd);
            result.append(title + ": " + message).append("\n");
        }
        DiscordUtil.sendMessage(channel, result.toString());
    }
}