package link.locutus.discord.web.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasApi;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.binding.DBAuthRecord;
import link.locutus.discord.web.jooby.WebRoot;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class WebCommands {

    @Command(desc = "Get the login code for the web interface")
    @Ephemeral
    public String web(@Me DBNation nation, @Me User user, @Me @Default GuildDB db) {
        UUID uuid = WebUtil.generateSecureUUID();
        DBAuthRecord token = WebRoot.db().updateToken(uuid, nation.getId(), user.getIdLong());
        List<String> urls = new ArrayList<>();

        String frontEndUrl = Settings.INSTANCE.WEB.FRONTEND_DOMAIN + "/#login/" + uuid;
        if (db != null) {
            frontEndUrl += "?guild=" + db.getIdLong();
        }

        urls.add("**Frontend**: <" + frontEndUrl + ">");
//        urls.add("**Backend**: <" + WebRoot.REDIRECT + "/page/login?token=" + token.getUUID() + ">");
        urls.add("**Conflicts**: <" + Settings.INSTANCE.WEB.S3.SITE + ">");
        return String.join("\n", urls);
    }

    @Command(desc = "Mail bot web login codes to all guild members, or the nations specified")
    @Ephemeral
    @RolePermission(Roles.ADMIN)
    @IsAlliance
    @HasApi
    public String mailLogin(@Me GuildDB db, @Me IMessageIO io, @Me JSONObject command, @Default Set<DBNation> nations, @Switch("r") boolean reset_sessions, @Switch("f") boolean force) {
        if (WebRoot.getInstance() == null) return "WebRoot not initialized";

        if (nations != null) {
            for (DBNation nation : nations) {
                if (!db.isAllianceId(nation.getAlliance_id())) {
                    return "Nation `" + nation.getName() + "` is in " + nation.getAlliance().getQualifiedId() + " but this server is registered to: "
                            + StringMan.getString(db.getAllianceIds()) + "\nSee: " + CM.settings.info.cmd.toSlashMention() + " with key `" + GuildKey.ALLIANCE_ID.name() + "`";
                }
            }
        } else {
            nations = db.getAllianceList().getNations(true, 0, true);;
        }

        if (!force) {
            String title = "Mail Web Login to " + nations.size() + " nations";
            StringBuilder body = new StringBuilder();
            if (reset_sessions) {
                body.append("Users will be logged out and sent a new login code via in-game mail\n");
            } else {
                body.append("Users will be sent a new or existing login code via in-game mail\n");
            }
            body.append("Do you want to continue?");
            io.create().confirmation(title, body.toString(), command).send();
            return null;
        }
        ApiKeyPool mailKey = ApiKeyPool.create(Locutus.loader().getNationId(), Locutus.loader().getApiKey());
        Map<String, String> errors = new LinkedHashMap<>();
        int success = 0;
        int i = 0;

        CompletableFuture<IMessageBuilder> msgFuture = io.send("Please wait...");

        long start = System.currentTimeMillis();
        for (DBNation nation : nations) {
            i++;
            try {
                WebUtil.mailLogin(mailKey, nation, false, true);
                success++;
            } catch (IOException | IllegalArgumentException e) {
                errors.put(nation.getMarkdownUrl(), "Failed to send mail: " + e.getMessage());
            }
            if (System.currentTimeMillis() - start > 10000) {
                io.updateOptionally(msgFuture, "Updating " + nation.getNation() + "(" + i + "/" + nations.size() + ")");
                start = System.currentTimeMillis();
            }
        }
        IMessageBuilder msg = io.create().append("Mail sent to " + success + " nations");
        if (!errors.isEmpty()) {
            msg.append("\nErrors:\n");
            for (Map.Entry<String, String> entry : errors.entrySet()) {
                msg.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        msg.send();
        return null;
    }
}
