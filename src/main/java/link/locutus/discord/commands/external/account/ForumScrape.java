package link.locutus.discord.commands.external.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.io.PagePriority;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.List;
import java.util.Set;

public class ForumScrape extends Command {
    public ForumScrape() {
        super("forumscrape", CommandCategory.LOCUTUS_ADMIN);
    }
    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.admin.sync.forum_profiles.cmd);
    }

    @Override
    public String help() {
        return null;
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        String urlRaw = "https://forum.politicsandwar.com/index.php?/profile/%s-ignore/";

        DiscordDB db = Locutus.imp().getDiscordDB();

        for (int i = 0; i < 15000; i++) {
            String url = String.format(urlRaw, i);

            try {
                String html = FileUtil.readStringFromURL(PagePriority.FORUM_PAGE, url);
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
