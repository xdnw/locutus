package link.locutus.discord.commands.war;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class GroundSim extends Command {
    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        // att soldiers
        // att tanks
        Integer attSoldiers = MathMan.parseInt(args.get(0));
        Integer attTanks = MathMan.parseInt(args.get(1));
        boolean attMunitions = Boolean.parseBoolean(args.get(2));
        Integer defSoldiers = MathMan.parseInt(args.get(3));
        Integer defTanks = MathMan.parseInt(args.get(4));
        boolean defMunitions = Boolean.parseBoolean(args.get(5));

        double attStrength = attSoldiers * (attMunitions ? 1.75 : 1) + attTanks * 40;
        double defStrength = defSoldiers * (defMunitions ? 1.75 : 1) + defTanks * 40;

        return super.onCommand(event, guild, author, me, args, flags);
    }
}
