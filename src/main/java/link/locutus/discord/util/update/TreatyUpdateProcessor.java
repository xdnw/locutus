package link.locutus.discord.util.update;

import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.event.TreatyUpdateEvent;
import link.locutus.discord.pnw.Alliance;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.PnwUtil;
import com.google.common.eventbus.Subscribe;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.util.Set;
import java.util.function.BiConsumer;

public class TreatyUpdateProcessor {

    @Subscribe
    public void update(TreatyUpdateEvent event) {
        Treaty previous = event.getPrevious();

        Treaty current = event.getCurrent();
        String title;
        if (previous != null && current != null) {
            if (current.type.ordinal() == previous.type.ordinal()) {
                title = "Treaty Renewed | " + current.type;
                return;
            }
            else if (current.type.getStrength() < previous.type.getStrength()) {
                title = "Treaty downgraded | " + previous.type + "->" + current.type;
            } else {
                title = "Treaty upgraded | " + previous.type + "->" + current.type;
            }
        } else if (previous == null) {
            title = "Treaty signed | " + current.type;
        } else {
            title = "Treaty ended | " + previous.type;
            current = previous;
        }

        Treaty existing = previous == null ? current : previous;
        Alliance fromAA = new Alliance(existing.from);
        Alliance toAA = new Alliance(existing.to);

        StringBuilder body = new StringBuilder();
        body.append("From: " + PnwUtil.getMarkdownUrl(current.from, true)).append("\n");
        body.append("To: " + PnwUtil.getMarkdownUrl(current.to, true)).append("\n");

        AlertUtil.forEachChannel(f -> true, GuildDB.Key.TREATY_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
            @Override
            public void accept(MessageChannel channel, GuildDB guildDB) {
                StringBuilder finalBody = new StringBuilder(body);

//                Integer allianceId = guildDB.getOrNull(GuildDB.Key.ALLIANCE_ID);
//                if (allianceId != null)
                {
                    Set<Integer> tracked = guildDB.getAllies(true);
                    if (!tracked.isEmpty()) {
                        tracked.addAll(guildDB.getCoalition("enemies"));
                        if (!tracked.contains(existing.from) && !tracked.contains(existing.to)) {
                            if (fromAA.getRank() > 50 && toAA.getRank() > 50) {
                                return;
                            }
                        } else {
                            finalBody.append("\n\n**IN SPHERE**");
                        }
                    }
                }
                DiscordUtil.createEmbedCommand(channel, title, finalBody.toString());
            }
        });
    }
}
