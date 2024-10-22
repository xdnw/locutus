package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public class MMRByScoreSheet extends Command {

    @Override
    public String help() {
        return super.help() + " <coalition-1> <coalition-2> ...";
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.stats_tier.mmrTierGraph.cmd.sheet(""), CM.stats_tier.strengthTierGraph.cmd.attachCsv("true"));
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {

        if (args.size() < 1) return usage(args.size(), 1, channel);
        int minutesInactive;
        if (false || args.size() == 3) {
            minutesInactive = (int) (TimeUtil.timeToSec(args.get(2)) / 60);
        } else {
            minutesInactive = 2880;
        }

        GuildDB guildDb = checkNotNull(Locutus.imp().getGuildDB(guild));
        SpreadSheet sheet = SpreadSheet.create(guildDb, SheetKey.CITY_GRAPH_SHEET);

        List<String> header = new ArrayList<>();
        header.add("soldiers");
        header.add("soldiers");
        header.add("tanks");
        header.add("aircraft");
        header.add("ships");
        header.add("average");
        header.addAll(args);

        sheet.setHeader(header);

        List<String>[] rows = new List[1000];



        for (int column = 0; column < args.size(); column++) {
            String arg = args.get(column);
            Set<Integer> aaIds = DiscordUtil.parseAllianceIds(null, arg);
            Set<DBNation> nations = Locutus.imp().getNationDB().getNationsByAlliance(aaIds);
            nations.removeIf(t -> t.getVm_turns() > 0 || t.active_m() > minutesInactive);


            for (int i = 0; i < 50; i++) {
                List<String> row = rows[i];
                int cities = i + 1;
                if (row == null) {
                    rows[i] = row = new ArrayList<>(header);
                    row.set(0, cities + "");
                }
            }
        }

        for (List<String> row : rows) {
            if (row != null) {
                sheet.addRow(row);
            }
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite();
        sheet.attach(channel.create(), "mmr_score").send();
        return null;

//        return super.onCommand(event, guild, author, me, args, flags);
    }
}
