package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.battle.BlitzGenerator;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ValidateSpyBlitzSheet extends Command {
    public ValidateSpyBlitzSheet() {
        super(CommandCategory.GOV, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }

    @Override
    public String help() {
        return super.help() + " <sheet> [day-change=false] [nations-filter=*]";
    }

    @Override
    public String desc() {
        return "Check that all nations are in range of their spy blitz targets and that they have no more than the provided number of offensive operations.\n" +
                "Add `true` for the day-change argument to double the offensive op limit\n" +
                "Add `-l` to use leader name instead of nation name";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Locutus.imp().getGuildDB(server).isValidAlliance() && Roles.MILCOM.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) return usage(args.size(), 1, channel);

        SpreadSheet sheet = SpreadSheet.create(args.get(0));
        StringBuilder response = new StringBuilder();

        Boolean dayChange = (args.size() >= 2) && Boolean.parseBoolean(args.get(1));

        Function<DBNation, Integer> maxWarsFunc = new Function<DBNation, Integer>() {
            @Override
            public Integer apply(DBNation nation) {
                int offSlots = 1;
                if (nation.hasProject(Projects.INTELLIGENCE_AGENCY)) offSlots++;
                if (dayChange) offSlots *= 2;
                return offSlots;
            }
        };

        Function<DBNation, Boolean> isValidTarget = f -> true;
        if (args.size() >= 3) {
            Set<DBNation> filter = DiscordUtil.parseNations(guild, author, me, args.get(2), false, false);
            isValidTarget = n -> filter.contains(n);
        }

        boolean useLeader = flags.contains('l');

        BlitzGenerator.getTargets(sheet, useLeader, 0, maxWarsFunc, 0.4, 2.5, false, false, true, isValidTarget, new BiConsumer<Map.Entry<DBNation, DBNation>, String>() {
            @Override
            public void accept(Map.Entry<DBNation, DBNation> dbNationDBNationEntry, String msg) {
                response.append(msg + "\n");
            }
        });

        if (response.length() <= 1) return "All checks passed";

        return response.toString();
    }
}
