package com.boydti.discord.commands.rankings;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.commands.rankings.builder.RankBuilder;
import com.boydti.discord.config.Settings;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.pnw.PNWUser;
import com.google.common.base.Function;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AAMembers extends Command {
    public AAMembers() {
        super(CommandCategory.GAME_INFO_AND_TOOLS);
    }
    @Override
    public String help() {
        return "!" + getAliases().get(0) + " <page>";
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
        List<DBNation> nations = Locutus.imp().getNationDB().getNations(Collections.singleton(Settings.INSTANCE.getAlliance(event)));

        int page = Integer.parseInt(args.get(0));
        int perPage = 5;

        new RankBuilder<>(nations).adapt(new Function<DBNation, String>() {
            @Override
            public String apply(DBNation n) {
                PNWUser user = Locutus.imp().getDiscordDB().getUserFromNationId(n.getNation_id());
                String result = "**" + n.getNation() + "**:" +
                        "";

                String active;
                if (n.getActive_m() < TimeUnit.DAYS.toMinutes(1)) {
                    active = "daily";
                } else if (n.getActive_m() < TimeUnit.DAYS.toMinutes(7)) {
                    active = "weekly";
                } else {
                    active = "inactive";
                }
                String url = n.getNationUrl();
                String general = n.toMarkdown(false, true, false);
                String infra = n.toMarkdown(false, false, true);

                StringBuilder response = new StringBuilder();
                response.append(n.getNation()).append(" | ").append(n.getAlliance()).append(" | ").append(active);
                if (user != null) {
                    response.append('\n').append(user.getDiscordName()).append(" | ").append("`<@!").append(user.getDiscordId()).append(">`");
                }
                response.append('\n').append(url);
                response.append('\n').append(general);
                response.append(infra);

                return response.toString();
            }
        }).page(page - 1, perPage).build(event, getClass().getSimpleName());
        return null;
    }
}
