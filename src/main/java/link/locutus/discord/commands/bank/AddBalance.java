package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.pnw.Alliance;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AddBalance extends Command {

    public AddBalance() {
        super(CommandCategory.ECON, CommandCategory.GOV, "addbalance", "addb");
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ECON.has(user, server);
    }

    @Override
    public String help() {
        return super.help() + " <nation> <resource> <amount> <#note>";
    }

    @Override
    public String desc() {
        return "Modify a nation, alliance or guild's deposits";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        return onCommand(event.getMessage(), event.getChannel(), guild, author, me, args, flags);
    }

    public String onCommand(Message message, MessageChannel channel, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (me == null) return "Please use `!verify`";

        if (args.isEmpty()) return usage(channel);
        String note = null;
        for (Iterator<String> iter = args.iterator(); iter.hasNext(); ) {
            String arg = iter.next();
            if (arg.startsWith("#")) {
                note = arg.toLowerCase();
                iter.remove();
                break;
            }
        }
        if (note == null) {
            return "Please use a note e.g. #deposit";
        }
        GuildDB guildDb = Locutus.imp().getGuildDB(guild);
        if (guildDb == null) return "No guild";

        OffshoreInstance offshore = guildDb.getOffshore();

        Map<DBNation, Map<ResourceType, Double>> fundsToSendNations = new LinkedHashMap<>();
        Map<Alliance, Map<ResourceType, Double>> fundsToSendAAs = new LinkedHashMap<>();
        Map<GuildDB, Map<ResourceType, Double>> fundsToSendGuilds = new LinkedHashMap<>();

        String arg = args.get(0);
        if (arg.contains("https://docs.google.com/spreadsheets/d/") || arg.startsWith("sheet:")) {
            boolean negative = false;
            if (arg.charAt(0) == '-') {
                negative = true;
                arg = arg.substring(1);
            }
            SpreadSheet sheet = SpreadSheet.create(arg);
            Map<String, Boolean> response = sheet.parseTransfers(fundsToSendNations, fundsToSendAAs);
            List<String> invalid = new ArrayList<>();
            for (Map.Entry<String, Boolean> entry : response.entrySet()) {
                if (!entry.getValue()) {
                    invalid.add(entry.getKey());
                }
            }
            if (negative) {
                for (Map.Entry<DBNation, Map<ResourceType, Double>> entry : fundsToSendNations.entrySet()) {
                    for (Map.Entry<ResourceType, Double> rssEntry : entry.getValue().entrySet()) {
                        rssEntry.setValue(-rssEntry.getValue());
                    }
                }
                for (Map.Entry<Alliance, Map<ResourceType, Double>> entry : fundsToSendAAs.entrySet()) {
                    for (Map.Entry<ResourceType, Double> rssEntry : entry.getValue().entrySet()) {
                        rssEntry.setValue(-rssEntry.getValue());
                    }
                }
            }
            if (!invalid.isEmpty() && !flags.contains('f')) return "Invalid nations/alliance:\n - " + StringMan.join(invalid, "\n - ");
        } else {
            boolean isGuild = arg.toLowerCase().startsWith("guild:");
            DBNation nation = DiscordUtil.parseNation(arg);
            if (nation == null) {
                if (isGuild || (MathMan.isInteger(arg) && Long.parseLong(arg) > Integer.MAX_VALUE)) {
                    if (isGuild) arg = arg.toLowerCase().replace("guild:", "");

                    GuildDB otherGuildDb = Locutus.imp().getGuildDB(Long.parseLong(arg));
                    if (otherGuildDb == null) return "Invalid guild id: " + otherGuildDb;
                    Map<ResourceType, Double> transfer = PnwUtil.parseResources(args.get(1));
                    fundsToSendGuilds.put(otherGuildDb, transfer);
                } else {
                    Integer alliance = PnwUtil.parseAllianceId(arg);
                    if (alliance == null) {
                        return "Invalid nation/alliance: `" + arg + "`";
                    }
                    Map<ResourceType, Double> transfer = PnwUtil.parseResources(args.get(1));
                    fundsToSendAAs.put(new Alliance(alliance), transfer);
                }
            } else {
                Map<ResourceType, Double> transfer = new HashMap<>();
                if ((args.size() == 2 || args.size() == 3) && args.get(1).equalsIgnoreCase("*")) {
                    Integer allianceId = guildDb.getOrNull(GuildDB.Key.ALLIANCE_ID);
                    if (allianceId == null) allianceId = nation.getAlliance_id();

                    Set<Long> tracked = null;
                    if (args.size() == 3) {
                        tracked = new LinkedHashSet<>();
                        Set<Integer> alliances = DiscordUtil.parseAlliances(guild, args.get(2));
                        if (alliances == null || alliances.isEmpty()) {
                            return "Invalid alliance: `" + args.get(2) + "`";
                        }
                        for (Integer alliance : alliances) tracked.add(alliance.longValue());
                        tracked = PnwUtil.expandCoalition(tracked);
                    }

                    double[] total = nation.getNetDeposits(guildDb, tracked, true, true, 0L, 0L);
                    transfer = PnwUtil.subResourcesToA(new HashMap<>(), PnwUtil.resourcesToMap(total));
                } else if (args.size() == 3) {
                    ResourceType resource = ResourceType.parse(args.get(1).toUpperCase());
                    Double amount = MathMan.parseDouble(args.get(2));
                    if (amount == null) return "Invalid amount: `" + args.get(2) + "`";
                    if (resource == null) return "Invalid resource: `" + args.get(1) + "`";
                    transfer.put(resource, amount);
                } else if (args.size() == 2) {
                    transfer = PnwUtil.parseResources(args.get(1));
                } else {
                    return usage(channel);
                }
                transfer.entrySet().removeIf(entry -> entry.getValue() == 0);
                if (transfer.isEmpty()) return "No amount specified";

                fundsToSendNations.put(nation, transfer);
            }
        }

        if ((!fundsToSendAAs.isEmpty() || !fundsToSendGuilds.isEmpty())) {
            if (offshore == null) {
                return "Please run the addbalance command for alliances/guilds on the applicable offshore server";
            }
            boolean isOffshore = guildDb.isOffshore();
            if (!isOffshore) return "Please run the addbalance command for alliances/guilds on the applicable offshore server";
        }

        Map<ResourceType, Double> total = new HashMap<>();
        for (Map.Entry<Alliance, Map<ResourceType, Double>> entry : fundsToSendAAs.entrySet()) {
            total = PnwUtil.add(total, entry.getValue());
        }
        for (Map.Entry<GuildDB, Map<ResourceType, Double>> entry : fundsToSendGuilds.entrySet()) {
            total = PnwUtil.add(total, entry.getValue());
        }
        for (Map.Entry<DBNation, Map<ResourceType, Double>> entry : fundsToSendNations.entrySet()) {
            total = PnwUtil.add(total, entry.getValue());
        }

        if (!flags.contains('f')) {
            StringBuilder title = new StringBuilder("Addbalance to");
            if (!fundsToSendNations.isEmpty()) title.append(" ").append(fundsToSendNations.size()).append("nations");
            if (!fundsToSendAAs.isEmpty()) title.append(" ").append(fundsToSendAAs.size()).append("alliances");
            if (!fundsToSendGuilds.isEmpty()) title.append(" ").append(fundsToSendGuilds.size()).append("guilds");

            String emoji = "\u2705";

            StringBuilder body = new StringBuilder();
            body.append("Note: `").append(note).append("`");

            if (!fundsToSendNations.isEmpty()) {
                if (fundsToSendNations.size() == 1) {
                    DBNation nation = fundsToSendNations.keySet().iterator().next();
                    body.append("\n" + nation.getNationUrlMarkup(true) + " | ").append(nation.getAllianceUrlMarkup(true));
                } else {
                    int gray = 0;
                    int vm = 0;
                    int inactive = 0;
                    int applicants = 0;
                    Set<Integer> aaIds = new HashSet<>();
                    for (DBNation nation : fundsToSendNations.keySet()) {
                        aaIds.add(nation.getAlliance_id());
                        if (nation.isGray()) gray++;
                        if (nation.getVm_turns() > 0) vm++;
                        if (nation.getActive_m() > 10000) inactive++;
                        if (nation.getPosition() <= 1) applicants++;
                    }
                    if (aaIds.size() > 1) {
                        body.append("\n" + fundsToSendNations.size() + " nations in " + aaIds.size() + " alliances:");
                    } else {
                        String aaName = PnwUtil.getMarkdownUrl(aaIds.iterator().next(), true);
                        body.append("\n" + fundsToSendNations.size() + " nations in " + aaName + ":");
                    }
                    if (gray > 0) body.append("\n - gray: " + gray);
                    if (vm > 0) body.append("\n - vm: " + vm);
                    if (inactive > 0) body.append("\n - inactive: " + inactive);
                    if (applicants > 0) body.append("\n - applicants: " + applicants);
                }
            }
            if (fundsToSendGuilds.size() == 1) {
                body.append("\nGuild: " + fundsToSendGuilds.keySet().iterator().next().getGuild());
            }
            if (fundsToSendAAs.size() == 1) {
                body.append("\nAlliance: " + fundsToSendAAs.keySet().iterator().next().getMarkdownUrl());
            }

            body.append("\nNet Total: ").append(PnwUtil.resourcesToFancyString(total));

            String cmd = DiscordUtil.trimContent(message.getContentRaw()) + " -f";
            body.append("\n\nPress " + emoji + " to confirm");

            DiscordUtil.createEmbedCommand(channel, title.toString(), body.toString(), emoji, cmd);
            return null;
        }

        List<String> response = new ArrayList<>();
        long tx_datetime = System.currentTimeMillis();
        long receiver_id = 0;
        int receiver_type = 0;
        int banker = me.getNation_id();

        Map<ResourceType, Double> totalAdded = new HashMap<>();


        if (!fundsToSendAAs.isEmpty()) {
            if (Roles.ECON.has(author, guildDb.getGuild())) {
                for (Map.Entry<Alliance, Map<ResourceType, Double>> entry : fundsToSendAAs.entrySet()) {
                    Alliance sender = entry.getKey();
                    double[] amount = PnwUtil.resourcesToArray(entry.getValue());
                    guildDb.addTransfer(tx_datetime, sender, receiver_id, receiver_type, banker, note, amount);
                    totalAdded = PnwUtil.add(totalAdded, entry.getValue());
                    response.add("Added " + PnwUtil.resourcesToString(entry.getValue()) + " to " + sender);
                }
            } else {
                response.add("You do not have permision add balance to alliances\n");
            }
        }

        if (!fundsToSendGuilds.isEmpty()) {
            if (Roles.ECON.has(author, guildDb.getGuild())) {
                for (Map.Entry<GuildDB, Map<ResourceType, Double>> entry : fundsToSendGuilds.entrySet()) {
                    double[] amount = PnwUtil.resourcesToArray(entry.getValue());
                    GuildDB sender = entry.getKey();
                    guildDb.addTransfer(tx_datetime, sender, receiver_id, receiver_type, banker, note, amount);
                    totalAdded = PnwUtil.add(totalAdded, entry.getValue());
                    response.add("Added " + PnwUtil.resourcesToString(entry.getValue()) + " to " + sender.getGuild());
                }
            } else {
                response.add("You do not have permision add balance to guilds\n");
            }
        }

        for (Map.Entry<DBNation, Map<ResourceType, Double>> entry : fundsToSendNations.entrySet()) {
            double[] amount = PnwUtil.resourcesToArray(entry.getValue());
            DBNation sender = entry.getKey();
            guildDb.addTransfer(tx_datetime, sender, receiver_id, receiver_type, banker, note, amount);
            totalAdded = PnwUtil.add(totalAdded, entry.getValue());
            response.add("Added " + PnwUtil.resourcesToString(entry.getValue()) + " to " + sender.getNationUrl());
        }

        return "Done:\n - " +
                StringMan.join(response, "\n - ") +
                "\nTotal added: `" + PnwUtil.resourcesToString(totalAdded) + "` worth: ~$" + MathMan.format(PnwUtil.convertedTotal(totalAdded));
    }
}
