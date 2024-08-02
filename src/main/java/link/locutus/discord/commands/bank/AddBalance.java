package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.AddBalanceBuilder;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.*;

public class AddBalance extends Command {

    public AddBalance() {
        super(CommandCategory.ECON, CommandCategory.GOV, "addbalance", "addb");
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.deposits.add.cmd);
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
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (me == null) return "Please use " + CM.register.cmd.toSlashMention() + "";

        if (args.isEmpty()) return usage(args.size(), 4, channel);
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
        if (note.equalsIgnoreCase("#ignore") && !flags.contains('f')) {
            throw new IllegalArgumentException("Using `#ignore` will not affect the user's balance, but will add an entry to their bank log. Please use `-f` to confirm");
        }
        GuildDB guildDb = Locutus.imp().getGuildDB(guild);
        if (guildDb == null) return "No guild.";

        OffshoreInstance offshore = guildDb.getOffshore();

        AddBalanceBuilder builder = guildDb.addBalanceBuilder();

        String arg = args.get(0);
        if (arg.matches(".*tax_id[=:].*")) {
            int taxId = PW.parseTaxId(arg);
            TaxBracket bracket = new TaxBracket(taxId, -1, "", 0, 0, 0L);
            builder.add(bracket, ResourceType.parseResources(args.get(1)), note);
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
                    Map<ResourceType, Double> transfer = ResourceType.parseResources(args.get(1));
                    builder.add(otherGuildDb, transfer, note);
                } else {
                    Integer alliance = PW.parseAllianceId(arg);
                    if (alliance == null) {
                        return "Invalid nation/alliance: `" + arg + "`";
                    }
                    Map<ResourceType, Double> transfer = ResourceType.parseResources(args.get(1));
                    builder.add(DBAlliance.getOrCreate(alliance), transfer, note);
                }
            } else {
                Map<ResourceType, Double> transfer = new HashMap<>();
                if ((args.size() == 2 || args.size() == 3) && args.get(1).equalsIgnoreCase("*")) {
                    Set<Long> tracked = null;
                    if (args.size() == 3) {
                        tracked = new LinkedHashSet<>();
                        Set<Integer> alliances = DiscordUtil.parseAllianceIds(guild, args.get(2));
                        if (alliances == null || alliances.isEmpty()) {
                            return "Invalid alliance: `" + args.get(2) + "`";
                        }
                        for (Integer alliance : alliances) tracked.add(alliance.longValue());
                        tracked = PW.expandCoalition(tracked);
                    }

                    double[] total = nation.getNetDeposits(guildDb, tracked, true, true, 0L, 0L, false);
                    transfer = ResourceType.subResourcesToA(new HashMap<>(), ResourceType.resourcesToMap(total));
                } else if (args.size() == 3) {
                    ResourceType resource = ResourceType.parse(args.get(1).toUpperCase());
                    Double amount = MathMan.parseDouble(args.get(2));
                    if (amount == null) return "Invalid amount: `" + args.get(2) + "`";
                    if (resource == null) return "Invalid resource: `" + args.get(1) + "`";
                    transfer.put(resource, amount);
                } else if (args.size() == 2) {
                    transfer = ResourceType.parseResources(args.get(1));
                } else {
                    return usage(args.size(), 2, channel);
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


        if (!flags.contains('f')) {
            String cmd = DiscordUtil.trimContent(fullCommandRaw) + " -f";
            builder.buildWithConfirmation(channel, cmd);
            return null;
        }

        boolean hasEcon = Roles.ECON.has(author, guild);
        return builder.buildAndSend(me, hasEcon);
    }
}
