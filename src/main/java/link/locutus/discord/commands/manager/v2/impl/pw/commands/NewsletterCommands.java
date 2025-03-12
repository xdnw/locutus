package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timediff;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsGuild;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.newsletter.Newsletter;
import link.locutus.discord.db.entities.newsletter.NewsletterManager;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.GoogleDoc;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.requests.restaction.pagination.MessagePaginationAction;
import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class NewsletterCommands {

    public static void main(String[] args) {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        Settings.WEB.CHAT_EXPORTER exportSettings = Settings.INSTANCE.WEB.CHAT_EXPORTER;
        Set<Long> channels = new LinkedHashSet<>(exportSettings.NEWS_CHANNELS);

        // C:\DCE-CLI\DiscordChatExporter.Cli.exe export -t TOKEN -c CHANNEL -f Json --after "2019-09-17 23:34" --utc -o "TODO"
        // if I want media
        // --media --reuse-media --media-dir "TODO"
        String exeFile = exportSettings.LOCATION;
        List<String> command = Arrays.asList("export");

        // Fetch last export date
        // for each channel
        // fetch the name of the channel (else, unknown)
        // Export the channel
        // aggregate it with the existing one
        // set the new export date
    }

    @RolePermission(value = {Roles.INTERNAL_AFFAIRS})
    @Command(desc = "Create a new newsletter with a name\n" +
            "After creating, you need to add channels to the newsletter, and subscribe users")
    @IsGuild(value = {672217848311054346L, 672217848311054346L})
    public String create(NewsletterManager manager, String name) {
        Newsletter existing = manager.getNewsletter(name);
        if (existing != null) {
            throw new IllegalArgumentException("Newsletter with name `" + name + "` already exists");
        }
        if (!name.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Newsletter name must be alphanumerical");
        }

        Newsletter newsletter = new Newsletter(0, name, System.currentTimeMillis(), 0, 0, 0, 0);
        manager.addNewsletter(newsletter);

        return "Newsletter `" + name + "` created with id: `#" + newsletter.getId() + "`. See:\n" +
                "- Add channel " + CM.newsletter.channel.add.cmd.toSlashMention() + "\n" +
                "- View " + CM.newsletter.info.cmd.toSlashMention() + "\n" +
                "- Send " + CM.newsletter.send.cmd.toSlashMention() + "\n" +
                "- Subscribe " + CM.newsletter.subscribe.cmd.toSlashMention() + "\n" +
                "- Delete " + CM.newsletter.delete.cmd.toSlashMention();
    }

    @RolePermission(value = {Roles.INTERNAL_AFFAIRS})
    @Command(desc = "Add a channel to a newsletter")
    @IsGuild(value = {672217848311054346L, 672217848311054346L})
    public String channelAdd(@Me GuildDB db, @Me Member author, NewsletterManager manager, Newsletter newsletter, TextChannel channel) {
        if (!channel.canTalk(author)) {
            throw new IllegalArgumentException("You cannot talk in channel " + channel.getAsMention() + "");
        }
        if (channel.getGuild().getIdLong() != db.getIdLong()) {
            throw new IllegalArgumentException("Channel " + channel.getAsMention() + " is not in " + db.getGuild());
        }
        if (newsletter.getChannelIds().contains(channel.getIdLong())) {
            throw new IllegalArgumentException("Channel " + channel.getAsMention() + " is already in newsletter `" + newsletter.getName() + "`");
        }
        newsletter.addChannelId(channel.getIdLong());
        manager.addChannel(newsletter.getId(), channel.getIdLong());
        return "Channel " + channel.getAsMention() + " added to newsletter `" + newsletter.getName() + "`\n" +
                "See " + CM.newsletter.info.cmd.toSlashMention();
    }

    @RolePermission(value = {Roles.INTERNAL_AFFAIRS})
    @Command(desc = "Remove a channel from a newsletter")
    @IsGuild(value = {672217848311054346L, 672217848311054346L})
    public String channelRemove(NewsletterManager manager, Newsletter newsletter, TextChannel channel) {
        if (!newsletter.getChannelIds().contains(channel.getIdLong())) {
            throw new IllegalArgumentException("Channel " + channel.getAsMention() + " is not in newsletter `" + newsletter.getName() + "`");
        }
        newsletter.removeChannelId(channel.getIdLong());
        manager.removeChannel(newsletter.getId(), channel.getIdLong());
        return "Channel " + channel.getAsMention() + " removed from newsletter `" + newsletter.getName() + "`\n" +
                "See " + CM.newsletter.info.cmd.toSlashMention();
    }

    @RolePermission(value = {Roles.INTERNAL_AFFAIRS})
    @Command(desc = "Delete a newsletter")
    @IsGuild(value = {672217848311054346L, 672217848311054346L})
    public String delete(@Me IMessageIO io, @Me JSONObject command, NewsletterManager manager, Newsletter newsletter, @Switch("f") boolean force) {
        if (!force) {
            String title = "Delete newsletter `" + newsletter.getName() + "`";
            String body = "**DELETE**:\n" + newsletter.toString();
            io.create().confirmation(title, body, command).send();
            return null;
        }
        manager.delete(newsletter.getId());
        return "Newsletter `" + newsletter.getName() + "` deleted";
    }

    @Command(desc = "View information about a newsletter", viewable = true)
    @IsGuild(value = {672217848311054346L, 672217848311054346L})
    public String info(@Me @Default User user, NewsletterManager manager, @Me IMessageIO io, @Me Guild guild, Newsletter newsletter, @Switch("u") boolean listNations) {
        if (listNations && !Roles.INTERNAL_AFFAIRS.has(user, guild)) {
            throw new IllegalArgumentException("You do not have permission to list nations");
        }
        String title = "Newsletter " + newsletter.getName();
        String body = newsletter.toString();
        IMessageBuilder msg = io.create().embed(title, body.toString());

        if (listNations) {
            Set<Integer> subscribed = manager.getSubscribedNations(newsletter.getId());
            if (subscribed.isEmpty()) {
                msg.append("No nations subscribed");
            } else {
                // just the ids as numbers, no formatting or mentions
                String nationsTxt = subscribed.stream().map(String::valueOf).collect(Collectors.joining("\n"));
                msg = msg.file("nations.txt", nationsTxt);
            }
        }
        msg.send();
        return null;
    }

    @RolePermission(value = {Roles.INTERNAL_AFFAIRS})
    @Command(desc = "Setup a reminder to send a newsletter at an interval")
    @IsGuild(value = {672217848311054346L, 672217848311054346L})
    public String autosend(NewsletterManager manager, @Me IMessageIO channel, Newsletter newsletter, @Timediff long interval, @Default Role pingRole) {
        if (interval == 0) {
            if (newsletter.getSendInterval() != 0) {
                throw new IllegalArgumentException("Autosend is already disabled");
            }
            newsletter.setSendInterval(0);
            newsletter.setPingRole(0);
            manager.updateNewsletter(List.of(newsletter));
            return "Autosend disabled";
        }

        if (interval < 0) {
            throw new IllegalArgumentException("Interval must be positive, not: `" + interval + "`");
        }

        if (pingRole == null) {
            throw new IllegalArgumentException("Please specify a role to ping with `pingRole: <role>`");
        }

        newsletter.setSendInterval(interval);
        newsletter.setPingRole(pingRole.getIdLong());
        manager.updateNewsletter(List.of(newsletter));

        return "Autosend enabled. You will be notified every `" + TimeUtil.secToTime(TimeUnit.MILLISECONDS, interval) + "` since the last send in this channel\n" +
                "- The role: `" + pingRole.getName() + "` will be mentioned\n" +
                "- Autosend task will check every turn\n" +
                "- Disable with " + CM.newsletter.auto.cmd.newsletter(newsletter.getName()).interval("0");
    }

    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.MAIL})
    @Command(desc = "Compile and send a newsletter to the subscribed nations\n" +
            "If no time period is specified, the newsletter will be compiled from the messages since the last compilation\n" +
            "If there is no previous compilation, the newsletter creation date will be used")
    @IsGuild(value = {672217848311054346L, 672217848311054346L})
    public String send(NewsletterManager manager, @Me DBNation me, @Me User author, @Me JSONObject command, @Me GuildDB db, @Me Guild guild, @Me IMessageIO io,
                       Newsletter newsletter, @Default @Timediff Long sendSince , @Switch("d") GoogleDoc document, @Switch("e") Long endDate) throws IOException, GeneralSecurityException {
        if (sendSince == null) sendSince = newsletter.getLastSent();
        if (sendSince == 0) sendSince = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
        long now = endDate == null ? System.currentTimeMillis() : endDate;

        String fromStr = TimeUtil.DD_MM_YYYY_HH.format(new Date(sendSince));
        String toStr = TimeUtil.DD_MM_YYYY_HH.format(new Date(now));

        Set<Integer> subscribed = manager.getSubscribedNations(newsletter.getId());
        if (document != null) {
            Set<DBNation> nations = subscribed.stream().map(f -> DBNation.getById(f)).filter(Objects::nonNull).collect(Collectors.toSet());
            if (nations.isEmpty()) {
                throw new IllegalArgumentException("No nations subscribed to newsletter: `" + newsletter.getName() + "`\n" +
                        "Subscribe with " + CM.newsletter.subscribe.cmd.newsletter(newsletter.getName()));
            }

            String title = "newsletter:" + newsletter.getName() + " " + fromStr + " to " + toStr;
            String body = document.readHtml();

            newsletter.setLastSent(now);

            JSONObject confirm = CM.mail.send.cmd.nations(StringMan.join(nations, ",")).subject(title).message(body).force("true").toJson();
            return IACommands.mail(me, confirm, db, io,  author, nations, title, body, true, true, null);
        }

        List<GuildChannel> channels = newsletter.getChannelIds().stream().map(guild::getGuildChannelById).filter(Objects::nonNull).toList();
        if (channels.isEmpty()) {
            throw new IllegalArgumentException("No channels added to newsletter: `" + newsletter.getName() + "`. Add one using " + CM.newsletter.channel.add.cmd.toSlashMention());
        }

        String mention = "<@[!]+" + Settings.INSTANCE.APPLICATION_ID + ">";

        String template = """
                <div class="card">
                    <div class="card-body">
                    <div class="media">
                        <div class="media-body">
                        <p class="card-text">{content}</p>
                        <p class="small text-muted">
                            <img src="{avatar}" class="mr-3" alt="Avatar" style="width:50px;"> | \s
                            <span>{username}</span> | \s
                            <span>{date}</span>
                        </p>
                        </div>
                    </div>
                    </div>
                </div>""";

        Map<TextChannel, List<String>> messages = new LinkedHashMap<>();
        for (Long channelId : newsletter.getChannelIds()) {
            TextChannel channel = guild.getTextChannelById(channelId);
            MessagePaginationAction history = channel.getIterableHistory();
            for (Message message : history) {
                long date = message.getTimeCreated().toEpochSecond() * 1000L;
                if (date < sendSince) {
                    break;
                }
                if (date > now) continue;

                String content = MarkupUtil.markdownToHTML(message.getContentRaw().replace(mention, ""));

                User msgAuth = message.getAuthor();
                String username = DiscordUtil.getUserName(msgAuth.getIdLong());
                String avatarUrl = msgAuth.getEffectiveAvatarUrl();

                content = template.replace("{avatar}", avatarUrl)
                        .replace("{content}", content)
                        .replace("{username}", username)
                        .replace("{date}", TimeUtil.DD_MM_YYYY_HH.format(new Date(date))).replace("\n[ ]+", "");
                messages.computeIfAbsent(channel, f -> new ArrayList<>()).add(content);
            }
        }

        if (messages.isEmpty()) {
            throw new IllegalArgumentException("No new messages found for newsletter: `" + newsletter.getName() + "` since " + DiscordUtil.timestamp(sendSince, null) + "\n" +
                    "See: " + CM.newsletter.info.cmd.toSlashMention() + " to view channels\n" +
                    "Note: Only messages mentioning this bot are included");
        }


        String title = fromStr + " to " + toStr;
        StringBuilder body = new StringBuilder();

        for (Map.Entry<TextChannel, List<String>> entry : messages.entrySet()) {
            TextChannel channel = entry.getKey();
            List<String> posts = entry.getValue();

            body.append("<h2>").append(channel.getName()).append("</h2>\n");
            for (String post : posts) {
                body.append(post).append("<br><hr><br>\n");
            }
        }

        document = GoogleDoc.create(db, SheetKey.NEWSLETTER, title);
        document.clear();
        document.append(body.toString());
        document.write();

        command.put("endDate", endDate);
        command.put("document", document.getUrl());

        StringBuilder confirmBody = new StringBuilder();

        // url
        confirmBody.append("**Document:** <" + document.getUrl() + ">\n");
        // time range (from/to)
        confirmBody.append("**Time:** ").append(DiscordUtil.timestamp(sendSince, null)).append(" to ").append(DiscordUtil.timestamp(now, null)).append("\n");
        // channels (dot points, channel mention
        confirmBody.append("**Channels:**\n");
        for (Map.Entry<TextChannel, List<String>> entry : messages.entrySet()) {
            TextChannel channel = entry.getKey();
            confirmBody.append("- ").append(channel.getAsMention()).append(": `" + entry.getValue().size()).append("`\n");
        }
        // num ppl subscribed
        confirmBody.append("**Subscribed:** `").append(subscribed.size()).append("`\n");

        io.create().embed("Send Newsletter: " + newsletter.getName(), confirmBody.toString())
                .commandButton(CommandBehavior.DELETE_MESSAGE, command, "Confirm")
                .send();
        return null;
    }

    @Command(desc = "List all newsletters", viewable = true)
    @IsGuild(value = {672217848311054346L, 672217848311054346L})
    public String list(NewsletterManager manager, @Me @Default DBNation nation) {
        Map<Integer, Newsletter> newsletters = manager.getNewsletters();
        Set<Newsletter> subs = nation == null ? Collections.emptySet() : manager.getSubscriptions(nation.getId());

        StringBuilder body = new StringBuilder("Newsletters:\n");
        for (Map.Entry<Integer, Newsletter> entry : newsletters.entrySet()) {
            Newsletter newsletter = entry.getValue();
            body.append("`#").append(entry.getKey()).append("` - ").append(newsletter.getName());
            if (subs.contains(newsletter)) {
                body.append(" - Subscribed");
            }
            body.append("\n");
        }
        body.append("\n");
        if (!subs.isEmpty()) {
            body.append("Unsubscribe with " + CM.newsletter.unsubscribe.cmd.toSlashMention() + "\n");
        }
        if (subs.size() != newsletters.size()) {
            body.append("Subscribe with " + CM.newsletter.subscribe.cmd.toSlashMention() + "\n");
        }

        return body.toString();
    }

    @Command(desc = "Subscribe yourself or a set of nations to a newsletter")
    @IsGuild(value = {672217848311054346L, 672217848311054346L})
    public String subscribe(NewsletterManager manager, @Me Guild guild, @Me User user, @Me DBNation nation, Newsletter newsletter, @Default Set<DBNation> nations) {
        if (nations == null) {
            nations = new HashSet<>(List.of(nation));
        }
        if (nations.size() != 1 || nations.iterator().next().getId() != nation.getId() && !Roles.INTERNAL_AFFAIRS.has(user, guild)) {
            throw new IllegalArgumentException("Cannot subscribe other nations. Missing role: " + Roles.INTERNAL_AFFAIRS.toDiscordRoleNameElseInstructions(guild));
        }
        for (DBNation other : nations) {
            manager.subscribe(other.getId(), newsletter.getId());
        }

        return "Successfully subscribed " + (nations.size() == 1 ? nations.iterator().next().getMarkdownUrl() : nations.size() + " nations") + " to " + newsletter.getName()
                + "\n- Unsubscribe with " + CM.newsletter.unsubscribe.cmd.newsletter(newsletter.getName());
    }

    @Command(desc = "Unsubscribe yourself or a set of nations from a newsletter")
    @IsGuild(value = {672217848311054346L, 672217848311054346L})
    public String unsubscribe(NewsletterManager manager, @Me Guild guild, @Me User user, @Me DBNation me, @Default Newsletter newsletter, @Default Set<DBNation> nations) {
        if (nations == null) {
            nations = new HashSet<>(List.of(me));
        }
        if (nations.size() != 1 || nations.iterator().next().getId() != me.getId() && !Roles.INTERNAL_AFFAIRS.has(user, guild)) {
            throw new IllegalArgumentException("Cannot subscribe other nations. Missing role: " + Roles.INTERNAL_AFFAIRS.toDiscordRoleNameElseInstructions(guild));
        }
        if (newsletter == null) {
            if (nations.size() > 1) {
                throw new IllegalArgumentException("Please specify a `newsletter` to unsubscribe multiple nations");
            }
            DBNation nation = nations.iterator().next();
            Set<Newsletter> current = manager.getSubscriptions(nation.getId());
            if (current.isEmpty()) {
                throw new IllegalArgumentException("No subscriptions found.");
            }
            manager.unsubscribeAllNation(nation.getId());
            return "Unsubscribed " + nation.getMarkdownUrl() + " from newsletter: `" + current.stream().map(f -> f.getName()).collect(Collectors.joining(",")) + "`";
        } else {
            for (DBNation nation : nations) {
                manager.unsubscribe(nation.getId(), newsletter.getId());
            }
            String nationStr = nations.size() == 1 ? nations.iterator().next().getMarkdownUrl() : (nations.size() + " nations");
            return "Unsubscribed " + nationStr + " from newsletter: `" + newsletter.getName() + "`";
        }
    }
}
