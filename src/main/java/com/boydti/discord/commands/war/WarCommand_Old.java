package com.boydti.discord.commands.war;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.config.Settings;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.apiv1.domains.Nation;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WarCommand_Old extends Command {
    public WarCommand_Old() {
        super("war");
    }

    @Override
    public String help() {
        return "!war [alliance|coalition] [sort]";
    }

    @Override
    public String desc() {
        return "Find a war target, with optional alliance and sorting (default: *, avg_infra). To see a list of coalitions, use `!coalitions`. Valid sort options are " + StringMan.getString(Locutus.imp().getNationDB().getColumns());
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return (super.checkPermission(server, user));
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        User user = event.getAuthor();
        DBNation me = DiscordUtil.getNation(event);
        if (me == null) {
            return "Invalid nation? Are you sure you are registered?";
        }

        Nation mePnwNation = me.getPnwNation();

        String sort = "avg_infra";
        String aa = null;
        Set<String> columns = Locutus.imp().getNationDB().getColumns();

        switch (args.size()) {
            default:
                return "Usage: `!war [alliance|coalition] [sort]";
            case 2:
                sort = args.get(1).toLowerCase();
                if (!columns.contains(sort)) {
                    return "Invalid sort: " + sort + ". Valid options are: " + StringMan.getString(columns);
                }
            case 1:
                if (columns.contains(args.get(0).toLowerCase())) {
                    sort = args.get(0).toLowerCase();
                } else {
                    aa = args.get(0);
                }
            case 0:
                List<DBNation> allNations = new ArrayList<>(Locutus.imp().getNationDB().getNationsSortedBy("money").values());
                Collection<DBNation> nations;
                if (aa == null) {
                    GuildDB db = Locutus.imp().getGuildDB(event);
                    Set<Integer> enemies = db.getCoalitions().get("enemies");
                    if (enemies == null) return "No enemies set. Please use `!setcoalition <alliance> enemies` or specify an enemy alliance/coalition as your second parameter";
                    nations = Locutus.imp().getNationDB().getNations(new HashSet<>(enemies));
                } else if (aa.equalsIgnoreCase("*")) {
                    nations = Locutus.imp().getNationDB().getNationsSortedBy(sort).values();
                } else {
                    Set<Integer> aaIds = new HashSet<>();
                    for (String aaName : aa.split(",")) {
                        aaName = aaName.trim();
                        Integer aaId = null;

                        if (aaName.startsWith("~")) {
                            aaName = aaName.substring(1);
                        } else {
                            aaId = PnwUtil.parseAllianceId(aaName);
                        }
                        if (aaId == null) {
                            Set<Integer> coa = Locutus.imp().getGuildDB(event).getCoalition(aaName);
                            if (coa.isEmpty()) {
                                return "Invalid AA or Coalition (case sensitive): " + aaName + ". @see also: `!coalitions`";
                            }
                            aaIds.addAll(coa);
                        }
                    }
                    nations = Locutus.imp().getNationDB().getNations(aaIds, sort);
                }

                int myAir = Integer.parseInt(mePnwNation.getAircraft());
                if (myAir < 100) {
                    return "You don't have many planes. Did you instead mean to find a `!raid` target?";
                }
                double minPlanes = myAir * 0.4;
                double maxPlanes = myAir * 0.8;
                double minScore = me.getScore() * 0.75;
                double maxScore = me.getScore() * 1.75;

                boolean isAdmin = event.getGuild().equals(Locutus.imp().getServer()) && Roles.ADMIN.hasOnRoot(user);

                int count = 0;

                StringBuilder response = new StringBuilder();
                for (DBNation nation : nations) {
                    if (nation.getScore() >= maxScore || nation.getScore() <= minScore) continue;
                    if (nation.getAircraft() == null || nation.getAircraft() >= maxPlanes || nation.getAircraft() < minPlanes)
                        continue;
                    if (nation.getActive_m() > 10000) continue;
                    if (nation.getVm_turns() != 0) continue;
                    if (nation.isBeige()) continue;
                    if (nation.getDef() >= 3) continue;

                    if (count++ == 5) break;

                    String moneyStr;
                    int monetaryIndex = allNations.indexOf(nation);
                    moneyStr = String.format("#%5s", monetaryIndex);

                    response.append('\n')
                            .append("<" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + nation.getNation_id() + ">")
                            .append(" | " + String.format("%16s", nation.getNation()))
                            .append(" | " + String.format("%16s", nation.getAlliance()))
                            .append("\n```")
                            .append(String.format("%5s", (int) nation.getScore())).append(" ns").append(" | ")
                            .append(String.format("%2s", nation.getCities())).append(" \uD83C\uDFD9").append(" | ")
                            .append(String.format("%5s", nation.getAvg_infra())).append(" \uD83C\uDFD7").append(" | ")
                            .append(String.format("%6s", nation.getSoldiers())).append(" \uD83D\uDC82").append(" | ")
                            .append(String.format("%5s", nation.getTanks())).append(" \u2699").append(" | ")
                            .append(String.format("%5s", nation.getAircraft())).append(" \u2708").append(" | ")
                            .append(String.format("%4s", nation.getShips())).append(" \u26F5").append(" | ")
                            .append(String.format("%1s", nation.getOff())).append(" \uD83D\uDDE1").append(" | ")
                            .append(String.format("%1s", nation.getDef())).append(" \uD83D\uDEE1").append(" | ")
                            .append(moneyStr).append(" | ")
                            .append(String.format("%2s", nation.getSpies())).append(" \uD83D\uDD0D").append(" | ")
                            .append("```")
                    ;
                }

                if (count == 0) {
                    return "No results. Please ping a target advisor";
                }

                return response.toString().trim();
        }
    }
}
