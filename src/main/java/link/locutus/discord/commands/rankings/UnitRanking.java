package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import com.google.common.collect.BiMap;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class UnitRanking extends Command {
    public UnitRanking() {
        super("UnitRanking", "PlaneRanking", CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }
    @Override
    public String help() {
        return Settings.commandPrefix(true) + "PlaneRanking <alliances|coalition> <unit>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.size() > 2) {
            return usage(event);
        }
        MilitaryUnit unit = MilitaryUnit.AIRCRAFT;

        String group;
        Collection<DBNation> nations;
        if (args.isEmpty()) {
            group = "*";
            nations = (Locutus.imp().getNationDB().getNations().values());
        } else {
            group = args.get(0);
            if (group.equals("*")) {
                nations = (Locutus.imp().getNationDB().getNations().values());
            } else {
                Set<Integer> alliances = DiscordUtil.parseAlliances(DiscordUtil.getDefaultGuild(event), group);
                if (alliances == null || alliances.isEmpty()) {
                    return "Invalid aa/coalition: " + group;
                }
                nations = Locutus.imp().getNationDB().getNations(alliances);
            }
            if (args.size() == 2) {
                unit = MilitaryUnit.get(args.get(1));
                if (unit == null) return "Invalid unit: `" + args.get(1) + "`";
            }
        }
        nations.removeIf(f -> f.getPosition() <= 1 || f.getVm_turns() > 0);

        MilitaryUnit finalUnit = unit;
        new RankBuilder<>(nations)
        .removeIf(nation -> nation.hasUnsetMil())
        .group(DBNation::getAlliance_id)
        .sumValues(n -> n.getUnits(finalUnit))
        .sort()
        .nameKeys(f -> PnwUtil.getName(f, true)).build(event, "Total " + unit.getName() + " in " + group);

        return null;
    }
}
