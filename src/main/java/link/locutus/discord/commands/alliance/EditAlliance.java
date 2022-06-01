package link.locutus.discord.commands.alliance;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.task.EditAllianceTask;
import link.locutus.discord.apiv1.enums.Rank;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class EditAlliance extends Command {

    public EditAlliance() {
        super("editalliance", CommandCategory.GOV, CommandCategory.INTERNAL_AFFAIRS);
    }

    @Override
    public String help() {
        return "!editalliance [attr] [value]";
    }

    @Override
    public String desc() {
        return "Edit the alliance";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        GuildDB db = Locutus.imp().getGuildDB(server);
        if (!db.hasAuth()) return false;
        if (Roles.ADMIN.has(user, server)) return true;
        DBNation nation = DiscordUtil.getNation(user);
        return nation != null && Roles.INTERNAL_AFFAIRS.has(user, server) && Rank.OFFICER.id <= nation.getPosition();
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        StringBuilder response = new StringBuilder();

        GuildDB db = Locutus.imp().getGuildDB(guild);
        Rank rank = args.size() > 0 && args.get(0).contains("bank") ? Rank.HEIR : Rank.OFFICER;
        Auth auth = db.getAuth(rank.id);
        if (auth == null) return "No authorization set";
        int allianceId = db.getOrThrow(GuildDB.Key.ALLIANCE_ID);

        EditAllianceTask task = new EditAllianceTask(auth.getNation(), new Consumer<Map<String, String>>() {
            @Override
            public void accept(Map<String, String> post) {
                if (args.size() == 0) {
                    response.append("Usage: `" + help() + "` - Currently set: " + StringMan.getString(post));
                    return;
                }
                String content = DiscordUtil.trimContent(event.getMessage().getContentRaw());
                String attr = args.get(0).split("\\r?\\n")[0];
                String finalValue = content.substring(content.indexOf(attr) + attr.length() + 1);

                if (post.containsKey(attr) || attr.equals("acceptmem")) {
                    String value = finalValue;
                    if (!value.isEmpty()) {
                        while (value.charAt(0) == '`' && value.charAt(value.length() - 1) == '`') {
                            value = value.substring(1, value.length() - 1);
                        }
                    }
                    post.put(attr, value);
                    response.append("Attribute has been set.");
                } else {
                    response.append("Invalid key: " + attr + ". Options: " + StringMan.getString(post));
                }
            }
        });
        task.call();
        return response.toString();
    }
}