package link.locutus.discord.commands.fun;

import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class Tag {
    private Long it;
    private Long previous;

    public String tag(MessageReceivedEvent event) {
        if (!event.isFromGuild()) {
            return null;
        }

        if (it != null) {
            User user = Locutus.imp().getDiscordApi().getUserById(it);
            List<Guild> mutual = user.getMutualGuilds();
            if (mutual.isEmpty()) {
                it = null;
                previous = null;
            } else {
                Member member = mutual.get(0).getMemberById(it);
                if (member == null || (member.getOnlineStatus() != OnlineStatus.ONLINE && member.getOnlineStatus() != OnlineStatus.DO_NOT_DISTURB)) {
                    it = null;
                    previous = null;
                } else {
                    return user.getName() + " is it!";
                }
            }
        }
        Member member = event.getMember();
        if (it == null) {
            assert member != null;
            if ((member.getOnlineStatus() != OnlineStatus.ONLINE && member.getOnlineStatus() != OnlineStatus.DO_NOT_DISTURB)) {
                return "You can only play tag if you are online.";
            }
            previous = it;
            it = event.getAuthor().getIdLong();
            return "Tag, you're it!";
        }
        return null;
    }

    public Long getIt() {
        return it;
    }

    public void checkTag(MessageReceivedEvent event) {
        if (event.isFromGuild() && it != null && it.equals(event.getAuthor().getIdLong()) && !event.getAuthor().isBot()) {
            Mentions mentions = event.getMessage().getMentions();
            for (Member mention : mentions.getMembers()) {
                if ((mention.getOnlineStatus() == OnlineStatus.ONLINE || mention.getOnlineStatus() == OnlineStatus.DO_NOT_DISTURB)) {
                    String msg = "%s tagged %s. %s is now it. Run for your lives!";
                    msg = String.format(msg, event.getAuthor().getName(), mention.getEffectiveName(), mention.getEffectiveName());
                    if (it == Settings.INSTANCE.APPLICATION_ID) {
                        it = null;
                        previous = null;
                    } else {
                        Long tmp = previous;
                        if (DiscordUtil.trimContent(event.getMessage().getContentRaw()).toLowerCase().contains("no backsies")) {
                            previous = it;
                        } else {
                            previous = null;
                        }
                        if (tmp != null && mention.getIdLong() == tmp) {
                            msg = "You cannot tag " + mention.getEffectiveName() + ", as the no backsies rule is in play.";
                        } else {
                            it = mention.getIdLong();
                        }
                    }
                    RateLimitUtil.queue(event.getChannel().sendMessage(msg));
                    return;
                }
            }
        }
    }
}
