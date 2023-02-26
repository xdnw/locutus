package link.locutus.discord.commands.info;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Reroll extends Command {
    public Reroll() {
        super(CommandCategory.GAME_INFO_AND_TOOLS);
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
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.isEmpty()) {
            return usage(event);
        }

        String arg0 = args.get(0);
        Integer id = DiscordUtil.parseNationId(arg0);
        if (id == null) {
            return "Invalid nation`" + arg0 + "`";
        }
        DBNation me = Locutus.imp().getNationDB().getNation(id);
        if (me == null) {
            return "Invalid nation`" + arg0 + "`" + ". (Out of " + Settings.commandPrefix(true) + "sync ?)";
        }

        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNations();
        for (Map.Entry<Integer, DBNation> entry : nations.entrySet()) {
            int otherId = entry.getKey();
            DBNation otherNation = entry.getValue();

            if (otherId > id && otherNation.getAgeDays() > me.getAgeDays() && Math.abs(otherNation.getDate() - me.getDate()) > TimeUnit.DAYS.toMillis(3)) {
                return me.getNation() + "/" + me.getNation_id() + " is a reroll.";
            }
        }

        Set<String> multiNations = new HashSet<>();
        Set<Integer> deletedMulti = new HashSet<>();

        return me.getNation() + "/" + me.getNation_id() + " is not a reroll.";
    }
}
