package link.locutus.discord.db.entities.announce;

import com.google.api.services.drive.model.File;
import link.locutus.discord.Locutus;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.sheet.DriveFile;
import link.locutus.discord.util.sheet.GoogleDoc;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.DefaultGuildChannelUnion;
import net.dv8tion.jda.api.requests.restaction.InviteAction;

import java.io.IOException;
import java.security.GeneralSecurityException;

public enum AnnounceType {
    MESSAGE {
        @Override
        public String create(GuildDB db, Announcement original, int index) {
            int requiredResults = 1;
            int requiredDiff = 0;
            int requiredDepth = 0;
            return StringMan.getReplacementIndex(original.body, original.getReplacements(), requiredResults, requiredDiff, requiredDepth, index);
        }
    },
    INVITE {
        @Override
        public String create(GuildDB db, Announcement original, int index) {
            if (original.replacements.isEmpty()) throw new IllegalArgumentException("No guild specified");
            String[] split = original.replacements.split(",");
            long serverId = Long.parseLong(split[0]);
            int expire = Integer.parseInt(split[1]);
            int maxUses = Integer.parseInt(split[2]);
            GuildDB otherDb = Locutus.imp().getGuildDB(serverId);
            if (otherDb == null) throw new IllegalArgumentException("No guild found with id `" + serverId + "`");
            DefaultGuildChannelUnion defaultChannel = otherDb.getGuild().getDefaultChannel();
            InviteAction create = defaultChannel.createInvite().setUnique(true);
            if (expire != 0) {
                create = create.setMaxAge(expire);
            }
            if (maxUses != 0) {
                create = create.setMaxUses(maxUses);
            }
            Invite invite = RateLimitUtil.complete(create);
            return invite.getUrl();
        }

        @Override
        public boolean isValid(GuildDB db, Announcement original, Announcement.PlayerAnnouncement current) {
            if (original.replacements.isEmpty()) throw new IllegalArgumentException("No guild specified");
            String[] split = original.replacements.split(",");
            long serverId = Long.parseLong(split[0]);
            GuildDB otherDb = Locutus.imp().getGuildDB(serverId);
            if (otherDb == null) throw new IllegalArgumentException("No guild found with id `" + serverId + "`");
            String content = current.getContent();
            String[] inviteSplit = content.split("\n");
            String inviteUrl = inviteSplit[inviteSplit.length - 1];
            String code = inviteUrl.substring(inviteUrl.lastIndexOf("/") + 1);

            for (Invite invite : RateLimitUtil.complete(otherDb.getGuild().retrieveInvites())) {
                if (invite.getCode().equals(code)) {
                    return true;
                }
            }
            return false;
        }
    },
    DOCUMENT {
        @Override
        public String create(GuildDB db, Announcement original, int index) {
            try {
                int requiredResults = 1;
                int requiredDiff = 1;
                int requiredDepth = 1;
                String text = StringMan.getReplacementIndex(original.body, original.getReplacements(), requiredResults, requiredDiff, requiredDepth, index);

                File file = DriveFile.createFile(original.title, text);
                String url = "https://docs.google.com/document/d/" + file.getId() + "/edit";
                new DriveFile(file.getId()).shareWithAnyone(DriveFile.DriveRole.READER);
                return url + "\n" + text;
            } catch (GeneralSecurityException | IOException e) {
                throw new RuntimeException(e);
            }
        }
    },

    ;

    public static final AnnounceType[] values = values();

    public boolean isValid(GuildDB db, Announcement original, Announcement.PlayerAnnouncement current) {
        return true;
    }
    public abstract String create(GuildDB db, Announcement original, int index);
}
