package link.locutus.discord.commands.info;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.scheduler.ThrowingFunction;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Who extends Command {

    public Who() {
        super("pnw-who", "who", "pw-who", "pw-info", "how", "where", "when", "why", "whois", CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.who.cmd);
    }
    @Override
    public String help() {
        return Settings.commandPrefix(true) + "pw-who <nation|alliance|coalition>";
    }

    @Override
    public String desc() {
        return "Get detailed information about a nation.\nNation argument can be nation name, id, link, or discord tag\n" +
                "Use `-l` to list the nations instead of just providing a summary\n" +
                "Use `-a` to list the alliances\n" +
                "Use `-r` to list discord tag (raw)\n" +
                "Use `-m` to list discord tag (ping)\n" +
                "Use `-i` to list individual nation info\n" +
                "Use `-c` to list individual nation channels" +
                "Use `page:2` to list the second page of results\n" +
                "Use `date:30d` to specify the snapshot date\n" +
                "e.g. `" + Settings.commandPrefix(true) + "who @borg`";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        Long date = DiscordUtil.parseArgFunc(args, "date", (ThrowingFunction<String, Long>) PrimitiveBindings::timestamp);
        Integer page = DiscordUtil.parseArgInt(args, "page");
        String arg0 = StringMan.join(args, " ");
        Set<NationOrAlliance> nations = PWBindings.nationOrAlliance(null, guild, arg0, author, me);
        if (args.isEmpty()) {
            return "Usage: `" + Settings.commandPrefix(true) + "pnw-who <discord-user>`";
        }
        // String nationOrAlliances, String sortBy, String list, String listAlliances, String listRawUserIds, String listMentions, String listInfo, String listChannels, String page
        CM.who command = CM.who.cmd.nationOrAlliances(
                arg0).list(
                flags.contains('l') ? "True" : null).listAlliances(
                flags.contains('a') ? "True" : null).listRawUserIds(
                flags.contains('r') ? "True" : null).listMentions(
                flags.contains('m') ? "True" : null).listInfo(
                flags.contains('i') ? "True" : null).listChannels(
                flags.contains('c') ? "True" : null).page(
                page == null ? null : page.toString()
        );
        GuildDB db = guild == null ? null : Locutus.imp().getGuildDB(guild);
        // flags.contains('i') flags.contains('p') || flags.contains('r') || flags.contains('c')
        return UtilityCommands.who(command.toJson(),
                guild,
                channel,
                author,
                db,
                me,
                nations,
                null,
                flags.contains('l'),
                flags.contains('a'),
                flags.contains('r'),
                flags.contains('m'),
                flags.contains('i'),
                flags.contains('c'),
                date,
                page);

//

//
//        StringBuilder response = new StringBuilder();
//
//        boolean isAdmin = Roles.ADMIN.hasOnRoot(author);
//

//        Set<DBNation> nations = DiscordUtil.parseNations(guild, arg0, false, false, flags.contains('f'));
//
//
//        if (nations.isEmpty()) {
//            return "Not found: `" + Settings.commandPrefix(true) + "pnw-who <user>`";
//        }
//        String title;
//        if (nations.size() == 1) {
//            DBNation nation = nations.iterator().next();
//            title = nation.getNation();
//            boolean showMoney = false;
//            nation.toCard(channel, false);
//
//            List<String> commands = new ArrayList<>();
//            commands.add(Settings.commandPrefix(true) + "multi " + nation.getNation_id());
//            commands.add(Settings.commandPrefix(true) + "wars " + nation.getNation_id());
//            commands.add(Settings.commandPrefix(true) + "revenue " + nation.getNation_id());
//            commands.add(Settings.commandPrefix(true) + "unithistory " + nation.getNation_id() + " <unit>");
//        } else {
//            int allianceId = -1;
//            for (DBNation nation : nations) {
//                if (allianceId == -1 || allianceId == nation.getAlliance_id()) {
//                    allianceId = nation.getAlliance_id();
//                } else {
//                    allianceId = -2;
//                }
//            }
//            if (allianceId != -2) {
//                String name = PW.getName(allianceId, true);
//                String url = PW.getUrl(allianceId, true);
//                title = "AA: " + name;
//                arg0 = MarkupUtil.markdownUrl(name, url);
//            } else {
//                title = "`" + arg0 + "`";
//                arg0 = "coalition";
//            }
//            title = "(" + nations.size() + " nations) " + title;
//
//            DBNation total = DBNation.createFromList(arg0, nations, false);
//            DBNation average = DBNation.createFromList(arg0, nations, true);
//
//            response.append("Total for ").append(arg0).append(":").append('\n');
//
//            printAA(response, total, isAdmin);
//
//            response.append("Average for ").append(arg0).append(":").append('\n');
//
//            printAA(response, average, isAdmin);
//
//            if (allianceId > 0) {
//                for (DBAlliance other : Locutus.imp().getNationDB().getAlliances()) {
//                    DBAlliance parent = other.getCachedParentOfThisOffshore();
//                    if (parent != null && parent.getAlliance_id() == allianceId) {
//                        response.append("\n- Offshore: ").append(other.getMarkdownUrl());
//                    }
//                }
//            }
//
//            // min score
//            // max score
//            // Num members
//            // averages
//        }
//        if (!flags.contains('i') && page == null && nations.size() > 1) {
//            channel.create().embed(title, response.toString()).send();
//        }
//
//        if (flags.contains('l') || flags.contains('p') || flags.contains('r') || flags.contains('c')) {
//            if (perpage == null) perpage = 15;
//            if (page == null) page = 0;
//            List<String> nationList = new ArrayList<>();
//
//            if (flags.contains('a')) {
//                // alliances
//                Set<Integer> alliances = new HashSet<>();
//                for (DBNation nation : nations) {
//                    if (!alliances.contains(nation.getAlliance_id())) {
//                        alliances.add(nation.getAlliance_id());
//                        nationList.add(nation.getAllianceUrlMarkup());
//                    }
//                }
//            } else {
//                GuildDB db = Locutus.imp().getGuildDB(guild);
//                IACategory iaCat = flags.contains('c') ? db.getIACategory() : null;
//                for (DBNation nation : nations) {
//                    String nationStr = flags.contains('l') ? nation.getNationUrlMarkup() : "";
//                    if (flags.contains('p')) {
//                        PNWUser user = nation.getDBUser();
//                        if (user != null) {
//                            nationStr += (" <@" + user.getDiscordId() + ">");
//                        }
//                    }
//                    if (flags.contains('r')) {
//                        PNWUser user = nation.getDBUser();
//                        if (user != null) {
//                            nationStr += (" `<@" + user.getDiscordId() + ">`");
//                        }
//                    }
//                    if (iaCat != null) {
//                        IAChannel iaChan = iaCat.get(nation);
//                        if (iaChan != null) {
//                            if (flags.contains('r')) {
//                                nationStr += " `" + iaChan.getChannel().getAsMention() + "`";
//                            } else {
//                                nationStr += " " + iaChan.getChannel().getAsMention();
//                            }
//                        }
//                    }
//                    nationList.add(nationStr);
//                }
//            }
//            int pages = (nations.size() + perpage - 1) / perpage;
//            title += "(" + (page + 1) + "/" + pages + ")";
//            DiscordUtil.paginate(channel, title, cmd, page, perpage, nationList, null, false);
//        }
//        if (flags.contains('i')) {
//            if (perpage == null) perpage = 5;
//            ArrayList<DBNation> sorted = new ArrayList<>(nations);
//
//            if (args.size() >= 2) {
//                String sortBy = args.get(1);
//                sorted.sort((o1, o2) -> Double.compare(o2.getAttr(sortBy), o1.getAttr(sortBy)));
//            }
//
//            List<String> results = new ArrayList<>();
//
//            for (DBNation nation : sorted) {
//                String entry = "<" + Settings.PNW_URL() + "/nation/id=" + nation.getNation_id() + ">" +
//                        " | " + String.format("%16s", nation.getNation()) +
//                        " | " + String.format("%16s", nation.getAllianceName()) +
//                        "\n```" +
//                        String.format("%2s", nation.getCities()) + " \uD83C\uDFD9" + " | " +
//                        String.format("%5s", nation.getAvg_infra()) + " \uD83C\uDFD7" + " | " +
//                        String.format("%6s", nation.getSoldiers()) + " \uD83D\uDC82" + " | " +
//                        String.format("%5s", nation.getTanks()) + " \u2699" + " | " +
//                        String.format("%5s", nation.getAircraft()) + " \u2708" + " | " +
//                        String.format("%4s", nation.getShips()) + " \u26F5" + " | " +
//                        String.format("%1s", nation.getOff()) + " \uD83D\uDDE1" + " | " +
//                        String.format("%1s", nation.getDef()) + " \uD83D\uDEE1" + " | " +
//                        String.format("%2s", nation.getSpies()) + " \uD83D\uDD0D" + " | " +
//                        "```";
//
//                results.add(entry);
//            }
//
//            DiscordUtil.paginate(channel, "Nations", cmd, page, perpage, results, null, false);
//        }
//
//        return null;//response.toString();
    }

    private void printAA(StringBuilder response, DBNation nation, boolean spies) {
        response.append(String.format("%4s", TimeUtil.secToTime(TimeUnit.DAYS, nation.getAgeDays()))).append(" ");
        response.append(nation.toMarkdown(true, false, false, true, false, false));
        response.append(nation.toMarkdown(true, false, false, false, true, spies));
    }
}
