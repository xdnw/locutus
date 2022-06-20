package link.locutus.discord.commands.info;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.TimeUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class NAPDown extends Command {
    @Deprecated // For a previous war. Not used anymore
    public NAPDown() {
        super("NAPDown", "NAP");
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return false;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        long turnEnd = 227742;
        String napLink = "https://politicsandwar.fandom.com/wiki/Brawlywood";
        long turn = TimeUtil.getTurn();

        long diff = (turnEnd - turn) * TimeUnit.HOURS.toMillis(2);

        if (diff <= 0) {
            String[] images = new String[] {
                    "3556290",
                    "13099758",
                    "5876175",
                    "15996537",
                    "13578642",
                    "11331727",
                    "13776910",
                    "3381639",
                    "8476393",
                    "3581186",
                    "13354647",
                    "5652813",
                    "7645437",
                    "9507023",
                    "8832122",
                    "4735568",
                    "7391113",
                    "5196956",
                    "11955188",
                    "5483839",
                    "12321108",
                    "17686107",
                    "12262416",
                    "13093956",
                    "4909014",
                    "17318955",
                    "4655431",
                    "14853646",
                    "14464332",
                    "14583973",
                    "18127160",
                    "4897716",
                    "15353915",
                    "8503723"
            };
            String baseUrl = "https://tenor.com/view/";
            String id = images[ThreadLocalRandom.current().nextInt(images.length)];
            return baseUrl + id;
        }

        String title = "GW Countdown: " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, diff) + " | " + (turnEnd - turn) + " turns";
        StringBuilder response = new StringBuilder();

        String url = "https://www.epochconverter.com/countdown?q=" + TimeUtil.getTimeFromTurn(turnEnd);
        response.append(url);
        if (napLink != null && !napLink.isEmpty()) {
            response.append("\n" + napLink);
        }

        String emoji = "\uD83D\uDD04";
        response.append("\n\npress " + emoji + " to refresh");

        Message msg = DiscordUtil.createEmbedCommand(event.getChannel(), title, response.toString(), emoji, DiscordUtil.trimContent(event.getMessage().getContentRaw()));
        return null;
    }
}
