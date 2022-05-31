package com.boydti.discord.commands.external.account;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.DiscordDB;
import com.boydti.discord.pnw.PNWUser;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.FileUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.List;

public class ForumScrape extends Command {
    public ForumScrape() {
        super("forumscrape", CommandCategory.LOCUTUS_ADMIN);
    }
    @Override
    public String help() {
        return null;
    }

    @Override
    public String desc() {
        return null;
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        String urlRaw = "https://forum.politicsandwar.com/index.php?/profile/%s-ignore/";

        DiscordDB db = Locutus.imp().getDiscordDB();

        for (int i = 0; i < 15000; i++) {
            String url = String.format(urlRaw, i);

            try {
                String html = FileUtil.readStringFromURL(url);
                Document dom = Jsoup.parse(html);
                int nationId = Integer.parseInt(dom.select("strong:matches(Nation ID)").first().parent().nextElementSibling().text());
                String discordId = dom.select("strong:matches(Discord Name)").first().parent().nextElementSibling().text();

                if (Locutus.imp().getDiscordDB().getUserFromNationId(nationId) != null) continue;

                if (nationId != 0) {
                    String[] split = discordId.split("#");
                    User user = null;
                    if (split.length == 2) {
                        user = Locutus.imp().getDiscordApi().getUserByTag(split[0], split[1]);
                    }
                    if (user == null && !discordId.contains("#")) {
                        List<User> users = Locutus.imp().getDiscordApi().getUsersByName(discordId, true);
                        if (users.size() == 1) {
                            user = users.get(0);
                        }
                    }
                    if (user != null) {
                        PNWUser pnwUser = new PNWUser(nationId, user.getIdLong(), discordId);
                        db.addUser(pnwUser);
                    }
                }
            } catch (Throwable ignore) {
            }
        }


        return null;
    }
}
