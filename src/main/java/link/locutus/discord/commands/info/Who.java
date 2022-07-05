package link.locutus.discord.commands.info;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.war.SpyCommand;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.test.IACategory;
import link.locutus.discord.util.offshore.test.IAChannel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Who extends Command {
    private final SpyCommand spyCmd;

    public Who(SpyCommand spyCmd) {
        super("pnw-who", "who", "pw-who", "pw-info", "how", "where", "when", "why", "whois", CommandCategory.GAME_INFO_AND_TOOLS);
        this.spyCmd = spyCmd;
    }

    @Override
    public String help() {
        return Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "pw-who <nation|alliance|coalition>";
    }

    @Override
    public String desc() {
        return "Get detailed information about a nation. Nation argument can be nation name, id, link, or discord tag\n" +
                "Use `-l` to list the nations instead of just providing a summary\n" +
                "Use `-r` to list discord tag (raw)\n" +
                "Use `-p` to list discord tag (ping)\n" +
                "Use `-i` to list individual nation info\n" +
                "Use `-c` to list individual nation channels" +
                "e.g. `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "who @borg`";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        Integer page = DiscordUtil.parseArgInt(args, "page");
        Integer perpage = DiscordUtil.parseArgInt(args, "perpage");
        String cmd = DiscordUtil.trimContent(event.getMessage().getContentRaw());

        if (args.isEmpty()) {
            return "Usage: `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "pnw-who <discord-user>`";
        }

        StringBuilder response = new StringBuilder();

        boolean isAdmin = Roles.ADMIN.hasOnRoot(event.getAuthor());

        String arg0 = args.get(0);
        Set<DBNation> nations = DiscordUtil.parseNations(guild, arg0, false, false, flags.contains('f'));


        if (nations.isEmpty()) {
            return "Not found: `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "pnw-who <user>`";
        }
        String title;
        if (nations.size() == 1) {
            DBNation nation = nations.iterator().next();
            title = nation.getNation();
            boolean showMoney = false;
            Message msg = nation.toCard(event.getChannel(), false, showMoney);

            List<String> commands = new ArrayList<>();
            commands.add(Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "multi " + nation.getNation_id());
            commands.add(Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "wars " + nation.getNation_id());
            commands.add(Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "revenue " + nation.getNation_id());
            commands.add(Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "unithistory " + nation.getNation_id() + " <unit>");

        } else {
            int allianceId = -1;
            for (DBNation nation : nations) {
                if (allianceId == -1 || allianceId == nation.getAlliance_id()) {
                    allianceId = nation.getAlliance_id();
                } else {
                    allianceId = -2;
                }
            }
            if (allianceId != -2) {
                String name = PnwUtil.getName(allianceId, true);
                String url = PnwUtil.getUrl(allianceId, true);
                title = "AA: " + name;
                arg0 = MarkupUtil.markdownUrl(name, url);
            } else {
                title = "`" + arg0 + "`";
                arg0 = "coalition";
            }
            title = "(" + nations.size() + " nations) " + title;

            DBNation total = new DBNation(arg0, nations, false);
            DBNation average = new DBNation(arg0, nations, true);

            response.append("Total for " + arg0 + ":").append('\n');

            printAA(response, total, isAdmin);

            response.append("Average for " + arg0 + ":").append('\n');

            printAA(response, average, isAdmin);

            // min score
            // max score
            // Num members
            // averages
        }
        if (!flags.contains('i') && page == null && nations.size() > 1) {
            DiscordUtil.createEmbedCommand(event.getChannel(), title, response.toString());
        }

        if (flags.contains('l') || flags.contains('p') || flags.contains('r') || flags.contains('c')) {
            if (perpage == null) perpage = 15;
            if (page == null) page = 0;
            List<String> nationList = new ArrayList<>();

            if (flags.contains('a')) {
                // alliances
                Set<Integer> alliances = new HashSet<>();
                for (DBNation nation : nations) {
                    if (!alliances.contains(nation.getAlliance_id())) {
                        alliances.add(nation.getAlliance_id());
                        nationList.add(nation.getAllianceUrlMarkup(true));
                    }
                }
            } else {
                GuildDB db = Locutus.imp().getGuildDB(guild);
                IACategory iaCat = flags.contains('c') ? db.getIACategory() : null;
                for (DBNation nation : nations) {
                    String nationStr = flags.contains('l') ? nation.getNationUrlMarkup(true) : "";
                    if (flags.contains('p')) {
                        PNWUser user = nation.getDBUser();
                        if (user != null && user.getDiscordId() != null) {
                            nationStr += (" <@" + user.getDiscordId() + ">");
                        }
                    }
                    if (flags.contains('r')) {
                        PNWUser user = nation.getDBUser();
                        if (user != null && user.getDiscordId() != null) {
                            nationStr += (" `<@" + user.getDiscordId() + ">`");
                        }
                    }
                    if (iaCat != null) {
                        IAChannel channel = iaCat.get(nation);
                        if (channel != null) {
                            if (flags.contains('r')) {
                                nationStr += " `" + channel.getChannel().getAsMention() + "`";
                            } else {
                                nationStr += " " + channel.getChannel().getAsMention();
                            }
                        }
                    }
                    nationList.add(nationStr);
                }
            }
            int pages = (nations.size() + perpage - 1) / perpage;
            title += "(" + (page + 1) + "/" + pages + ")";
            DiscordUtil.paginate(event.getChannel(), title, cmd, page, perpage, nationList);
        }
        if (flags.contains('i')) {
            if (perpage == null) perpage = 5;
            ArrayList<DBNation> sorted = new ArrayList<>(nations);

            if (args.size() >= 2) {
                String sortBy = args.get(1);
                Collections.sort(sorted, (o1, o2) -> Double.compare(o2.getAttr(sortBy), o1.getAttr(sortBy)));
            }

            List<String> results = new ArrayList<>();

            for (DBNation nation : sorted) {
                StringBuilder entry = new StringBuilder();
                entry.append("<" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + nation.getNation_id() + ">")
                        .append(" | " + String.format("%16s", nation.getNation()))
                        .append(" | " + String.format("%16s", nation.getAllianceName()))
                        .append("\n```")
//                            .append(String.format("%5s", (int) nation.getScore())).append(" ns").append(" | ")
                        .append(String.format("%2s", nation.getCities())).append(" \uD83C\uDFD9").append(" | ")
                        .append(String.format("%5s", nation.getAvg_infra())).append(" \uD83C\uDFD7").append(" | ")
                        .append(String.format("%6s", nation.getSoldiers())).append(" \uD83D\uDC82").append(" | ")
                        .append(String.format("%5s", nation.getTanks())).append(" \u2699").append(" | ")
                        .append(String.format("%5s", nation.getAircraft())).append(" \u2708").append(" | ")
                        .append(String.format("%4s", nation.getShips())).append(" \u26F5").append(" | ")
                            .append(String.format("%1s", nation.getOff())).append(" \uD83D\uDDE1").append(" | ")
                        .append(String.format("%1s", nation.getDef())).append(" \uD83D\uDEE1").append(" | ")
                        .append(String.format("%2s", nation.getSpies())).append(" \uD83D\uDD0D").append(" | ");
//                Activity activity = nation.getActivity(14 * 12);
//                double loginChance = activity.loginChance((int) Math.max(1, (12 - (currentTurn % 12))), true);
//                int loginPct = (int) (loginChance * 100);
//                response.append("login=" + loginPct + "%").append(" | ");
                entry.append("```");

                results.add(entry.toString());
            }

            DiscordUtil.paginate(event.getGuildChannel(), "Nations", cmd, page, perpage, results);
        }

        return null;//response.toString();
    }

    private void printAA(StringBuilder response, DBNation nation, boolean spies) {
        response.append(String.format("%4s", TimeUtil.secToTime(TimeUnit.DAYS, nation.getAgeDays()))).append(" ");
        response.append(nation.toMarkdown(false, false, true, false, false));
        response.append(nation.toMarkdown(false, false, false, true, spies));
    }
}
