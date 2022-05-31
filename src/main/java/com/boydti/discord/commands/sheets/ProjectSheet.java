package com.boydti.discord.commands.sheets;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.MarkupUtil;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.sheet.SpreadSheet;
import com.boydti.discord.apiv1.enums.city.project.Project;
import com.boydti.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.boydti.discord.db.GuildDB.Key.PROJECT_SHEET;

public class ProjectSheet extends Command {
    public ProjectSheet() {
        super(CommandCategory.INTERNAL_AFFAIRS, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.GOV);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Locutus.imp().getGuildDB(server).isValidAlliance() && (Roles.INTERNAL_AFFAIRS.has(user, server) || Roles.ECON.has(user, server));
    }

    @Override
    public String help() {
        return super.help() + " <nations>";
    }

    @Override
    public String desc() {
        return help() + "\n" +
                "Use `-f` to force an update of projects";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(event);

        Set<DBNation> nations = DiscordUtil.parseNations(guild, args.get(0));

        GuildDB db = Locutus.imp().getGuildDB(event);

        SpreadSheet sheet = SpreadSheet.create(db, PROJECT_SHEET);

        List<Object> header = new ArrayList<>(Arrays.asList(
                "nation",
                "alliance",
                "\uD83C\uDFD9", // cities
                "\uD83C\uDFD7", // avg_infra
                "score"
        ));

        for (Project value : Projects.values) {
            header.add(value.name());
        }

        sheet.setHeader(header);

        for (DBNation nation : nations) {

            header.set(0, MarkupUtil.sheetUrl(nation.getNation(), PnwUtil.getUrl(nation.getNation_id(), false)));
            header.set(1, MarkupUtil.sheetUrl(nation.getAlliance(), PnwUtil.getUrl(nation.getAlliance_id(), true)));
            header.set(2, nation.getCities());
            header.set(3, nation.getAvg_infra());
            header.set(4, nation.getScore());

            for (Project value : Projects.values) {
                if (flags.contains('f')) {
                    nation.updateProjects();
                }
                header.set(5 + value.ordinal(), nation.hasProject(value) + "");
            }

            sheet.addRow(header);
        }

        sheet.clearAll();
        sheet.set(0, 0);

        return "<" + sheet.getURL() + ">";
    }
}