package com.boydti.discord.commands.rankings;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.entities.AttackTypeBreakdown;
import com.boydti.discord.db.entities.WarAttackParser;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.MathMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class AttackTypeBreakdownAB extends Command {
    public AttackTypeBreakdownAB() {
        super("AttackTypeBreakdown", "AttackTypes", CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }
    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String help() {
        return "`" + super.help() + " <alliance|coalition> <alliance|coalition> <days>` OR `" + super.help() + " <war-url>`";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty() || args.size() > 3 || (args.size() == 3 && args.get(0).equalsIgnoreCase(args.get(1)))) {
            return usage(event);
        }

        WarAttackParser parser = new WarAttackParser(Locutus.imp().getGuildDB(event), args, flags);

        AttackTypeBreakdown breakdown = new AttackTypeBreakdown(parser.getNameA(), parser.getNameB());
        breakdown.addAttacks(parser.getAttacks(), parser.getIsPrimary(), parser.getIsSecondary());

        breakdown.toEmbed(event.getGuildChannel());

        return null;
    }
}
