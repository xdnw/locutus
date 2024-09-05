package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.builder.GroupedRankBuilder;
import link.locutus.discord.commands.manager.v2.builder.NumericMappedRankBuilder;
import link.locutus.discord.commands.manager.v2.builder.RankBuilder;
import link.locutus.discord.db.entities.DBNation;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class CityAgeRanking extends Command {

    @Override
    public String help() {
        return super.help() + " <min> <max> [members]";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) return usage(args.size(), 2, channel);
        int min = Integer.parseInt(args.get(0));
        int max = Integer.parseInt(args.get(1));

        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNations();
        nations.entrySet().removeIf(e -> {
            int cities = e.getValue().getCities();
            return cities < min || cities > max;
        });

        GroupedRankBuilder<Integer, DBNation> nationByAAGroup = new RankBuilder<>(nations.values()).group(DBNation::getAlliance_id);
        Map<Integer, List<DBNation>> nationsByAlliance = nationByAAGroup.get();

        NumericMappedRankBuilder<Integer, Integer, Number> avg = nationByAAGroup.map((integer, nation) -> null, (integer, nation) -> nation.getAgeDays());

        return null;

    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of();
    }
}
