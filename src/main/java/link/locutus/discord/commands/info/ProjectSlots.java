package link.locutus.discord.commands.info;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.apiv1.enums.city.project.Project;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class ProjectSlots extends Command {
    public ProjectSlots() {
        super("ProjectSlots", "ProjectSlot", CommandCategory.ECON);
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
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(event);
        DBNation nation = DiscordUtil.parseNation(args.get(0));
        if (nation == null) return "Invalid nation: `" + args.get(0) + "`";


        Set<Project> projects = nation.getProjects();
        double value = 0;
        for (Project project : projects) {
            value += PnwUtil.convertedTotal(project.cost());
        }

        return nation.getNation() + " has " + projects.size() + "/" + nation.projectSlots() + " worth $" + MathMan.format(value);
    }
}
