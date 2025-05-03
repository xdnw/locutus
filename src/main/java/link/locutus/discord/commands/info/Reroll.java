package link.locutus.discord.commands.info;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Reroll extends Command {
    public Reroll() {
        super(CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.nation.reroll.cmd);
    }
    @Override
    public String help() {
        return Settings.commandPrefix(true) + "reroll <nation>";
    }

    @Override
    public String desc() {
        return "Checks if a nation is a reroll";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) {
            return usage(args.size(), 1, channel);
        }

        String arg0 = args.get(0);
        me = DiscordUtil.parseNation(arg0, true);
        if (me == null) {
            return "Invalid nation`" + arg0 + "`" + ". (Out of " + Settings.commandPrefix(true) + "sync ?)";
        }

        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNationsById();
        for (Map.Entry<Integer, DBNation> entry : nations.entrySet()) {
            int otherId = entry.getKey();
            DBNation otherNation = entry.getValue();

            if (otherId > me.getId() && otherNation.getAgeDays() > me.getAgeDays() && Math.abs(otherNation.getDate() - me.getDate()) > TimeUnit.DAYS.toMillis(3)) {
                return me.getNation() + "/" + me.getNation_id() + " is a reroll.";
            }
        }
        return me.getNation() + "/" + me.getNation_id() + " is not a reroll.";
    }
}
