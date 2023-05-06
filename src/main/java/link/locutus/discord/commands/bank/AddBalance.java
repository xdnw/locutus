package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.AddBalanceBuilder;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.*;

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
        return "Modify a nation, alliance or guild's deposits.";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        return onCommand(event.getMessage(), event.getChannel(), guild, author, me, args, flags);
    }

    public String onCommand(Message message, MessageChannel channel, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (me == null) return "Please use " + CM.register.cmd.toSlashMention() + "";

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
        if (guildDb == null) return "No guild.";

        OffshoreInstance offshore = guildDb.getOffshore();

        AddBalanceBuilder builder = guildDb.addBalanceBuilder();

        String arg = args.get(0);
        if (arg.matches(".*tax_id[=:].*")) {
            int taxId = PnwUtil.parseTaxId(arg);
            TaxBracket bracket = new TaxBracket(taxId, -1, "", 0, 0, 0L);
            builder.add(bracket, PnwUtil.parseResources(args.get(1)), note);
        } else if (arg.contains("https://docs.google.com/spreadsheets/") || arg.startsWith("sheet:")) {
            boolean negative = false;
            if (arg.charAt(0) == '-') {
                negative = true;
                arg = arg.substring(1);
            }
            SpreadSheet sheet = SpreadSheet.create(arg);
            List<String> invalid = new ArrayList<>();
            builder.addSheet(sheet, negative, invalid::add, true, note);
        } else {
            boolean isGuild = arg.toLowerCase().startsWith("guild:");
            DBNation nation = DiscordUtil.parseNation(arg, true);
            if (nation == null) {
                if (isGuild || (MathMan.isInteger(arg) && Long.parseLong(arg) > Integer.MAX_VALUE)) {
                    if (isGuild) arg = arg.toLowerCase().replace("guild:", "");

                    GuildDB otherGuildDb = Locutus.imp().getGuildDB(Long.parseLong(arg));
                    if (otherGuildDb == null) return "Invalid guild id: `" + arg + "`";
                    Map<ResourceType, Double> transfer = PnwUtil.parseResources(args.get(1));
                    builder.add(otherGuildDb, transfer, note);
                } else {
                    Integer alliance = PnwUtil.parseAllianceId(arg);
                    if (alliance == null) {
                        return "Invalid nation/alliance: `" + arg + "`";
                    }
                    Map<ResourceType, Double> transfer = PnwUtil.parseResources(args.get(1));
                    builder.add(DBAlliance.getOrCreate(alliance), transfer, note);
                }
            } else {
                Map<ResourceType, Double> transfer = new HashMap<>();
                if ((args.size() == 2 || args.size() == 3) && args.get(1).equalsIgnoreCase("*")) {
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
                if (transfer.isEmpty()) return "No amount specified.";

                builder.add(nation, transfer, note);
            }
        }

        if ((!builder.getFundsToSendAAs().isEmpty() || !builder.getFundsToSendGuilds().isEmpty())) {
            if (offshore == null) {
                return "Please run the addbalance command for alliances/guilds on the applicable offshore server.";
            }
            boolean isOffshore = guildDb.isOffshore();
            if (!isOffshore)
                return "Please run the addbalance command for alliances/guilds on the applicable offshore server.";
        }


        DiscordChannelIO io = new DiscordChannelIO(channel, () -> message);
        if (!flags.contains('f')) {
            String cmd = DiscordUtil.trimContent(message.getContentRaw()) + " -f";
            builder.buildWithConfirmation(io, cmd);
            return null;
        }

        boolean hasEcon = Roles.ECON.has(author, guild);
        return builder.buildAndSend(me, hasEcon);
    }
}
