package link.locutus.discord.commands.info;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.apiv1.domains.Nation;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class TurnTimer extends Command {
    public TurnTimer() {
        super("TurnTimer", "Timer", "CityTimer", "ProjectTimer", CommandCategory.ECON);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String help() {
        return super.help() + " <nation>";
    }

    @Override
    public String desc() {
        return "Check how many turns are left in the city/project timer";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(event);
        DBNation nation = DiscordUtil.parseNation(args.get(0));
        if (nation == null) return "Invalid nation: `" + args.get(0) + "`";

        StringBuilder response = new StringBuilder();
        response.append("City: " + nation.getCityTurns() + " turns (" + nation.getCities() + " cities)\n");
        response.append("Project: " + nation.getProjectTurns() + "turns | " +
                "(" + nation.getProjects().size() + "/" + nation.projectSlots() + " slots)\n");
        response.append("Color: " + nation.getColorTurns() + "turns \n");
        response.append("Domestic Policy: " + nation.getDomesticPolicyTurns() + "turns \n");
        response.append("War Policy: " + nation.getWarPolicyTurns() + "turns \n");
        response.append("Beige Turns: " + nation.getBeigeTurns() + "turns \n");
        response.append("Vacation: " + nation.getVm_turns());

        return response.toString();
    }
}
