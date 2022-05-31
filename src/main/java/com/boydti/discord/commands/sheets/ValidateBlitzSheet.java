package com.boydti.discord.commands.sheets;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.battle.BlitzGenerator;
import com.boydti.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ValidateBlitzSheet extends Command {
    public ValidateBlitzSheet() {
        super(CommandCategory.GOV, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }

    @Override
    public String help() {
        return super.help() + " <sheet> [max-wars=3] [nations-filer=*]";
    }

    @Override
    public String desc() {
        return "Check that all nations are in range of their blitz targets and that they have no more than the provided number of offensive wars";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        GuildDB db = Locutus.imp().getGuildDB(server);
        return (db.isValidAlliance() || db.isWhitelisted()) && Roles.MILCOM.has(user, server);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) return usage(event);

        Integer maxWars = 3;
        if (args.size() >= 2) maxWars = Integer.parseInt(args.get(1));

        Function<DBNation, Boolean> isValidTarget = f -> true;
        if (args.size() >= 3) {
            Set<DBNation> filter = DiscordUtil.parseNations(guild, args.get(2));
            isValidTarget = n -> filter.contains(n);
        }

        SpreadSheet sheet = SpreadSheet.create(args.get(0));
        StringBuilder response = new StringBuilder();
        Integer finalMaxWars = maxWars;
        BlitzGenerator.getTargets(sheet, 0, f -> finalMaxWars, 0.75, 1.75, true, isValidTarget, new BiConsumer<Map.Entry<DBNation, DBNation>, String>() {
            @Override
            public void accept(Map.Entry<DBNation, DBNation> dbNationDBNationEntry, String msg) {
                response.append(msg + "\n");
            }
        });

        if (response.length() <= 1) return "All checks passed";

        return response.toString();
    }
}
