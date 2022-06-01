package link.locutus.discord.commands.info;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class ChannelCount extends Command {

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {

        StringBuilder channelList = new StringBuilder();

        List<Category> categories = guild.getCategories();
        for (Category category : categories) {
            channelList.append(category.getName() + "\n");

            for (GuildChannel channel : category.getChannels()) {
                String prefix = "+ ";
                if (channel instanceof VoiceChannel) {
                    prefix = "\uD83D\uDD0A ";
                }
                channelList.append(prefix + channel.getName() + "\n");
            }

            channelList.append("\n");
        }

        if (Roles.ADMIN.has(author, guild)) {
            DiscordUtil.upload(event.getChannel(), guild.getChannels().size() + "/500 channels", channelList.toString());
            return null;
        } else {
            return "This discord has " + guild.getChannels().size() + "/500 channels";
        }
    }
}
