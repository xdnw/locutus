package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.AttackTypeBreakdown;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.WarAttackParser;
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
    public List<CommandRef> getSlashReference() {
        return List.of(CM.stats_war.attack_breakdown.versus.cmd);
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
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty() || args.size() > 3) {
            return usage(args.size(), 2, 3, channel);
        }
        if (args.size() == 3 && args.get(0).equalsIgnoreCase(args.get(1))) {
            return usage("Coalition 1 and 2 are the same", channel);
        }

        WarAttackParser parser = new WarAttackParser(Locutus.imp().getGuildDB(guild), author, me, args, flags);

        AttackTypeBreakdown breakdown = new AttackTypeBreakdown(parser.getNameA(), parser.getNameB());
        parser.iterateAttacks((war, attack) -> {
            breakdown.addAttack(war, attack, parser.getIsPrimary(), parser.getIsSecondary());
        });

        channel.create().writeTable("Attack Breakdown", breakdown.toTableList(), true, null).send();

        return null;
    }
}
