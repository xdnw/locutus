package link.locutus.discord.commands.info;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Set;

public class ChannelCount extends Command {

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.channel.count.cmd);
    }
    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {

        StringBuilder channelList = new StringBuilder();

        List<Category> categories = guild.getCategories();
        for (Category category : categories) {
            channelList.append(category.getName()).append("\n");

            for (GuildChannel catChan : category.getChannels()) {
                String prefix = "+ ";
                if (catChan instanceof VoiceChannel) {
                    prefix = "\uD83D\uDD0A ";
                }
                channelList.append(prefix).append(catChan.getName()).append("\n");
            }

            channelList.append("\n");
        }

        if (Roles.ADMIN.has(author, guild)) {
            channel.create().file(guild.getChannels().size() + "_of_500_channels.txt", channelList.toString()).send();
            return null;
        } else {
            return "This discord has " + guild.getChannels().size() + "/500 channels";
        }
    }
}
