package link.locutus.discord.util.update;

import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.event.treaty.*;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.PnwUtil;
import com.google.common.eventbus.Subscribe;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.util.Set;
import java.util.function.BiConsumer;

public class TreatyUpdateProcessor {

    @Subscribe
    public void onTreatyCreate(TreatyCreateEvent event) {
        update("Signed", event);
    }
    @Subscribe
    public void onTreatyCancel(TreatyCancelEvent event) {
        update("Cancelled", event);
    }
    @Subscribe
    public void onTreatyDowngrade(TreatyDowngradeEvent event) {
        update("Downgraded", event);
    }
    @Subscribe
    public void onTreatyExtend(TreatyExtendEvent event) {
        update("Extended", event);
    }
    @Subscribe
    public void onTreatyUpgraded(TreatyUpgradeEvent event) {
        update("Upgraded", event);
    }
    @Subscribe
    public void onTreatyExpire(TreatyExpireEvent event) {
        update("Expired", event);
    }

    private void update(String title, TreatyChangeEvent event) {
        Treaty previous = event.getPrevious();

        Treaty current = event.getCurrent();

        Treaty existing = previous == null ? current : previous;
        DBAlliance fromAA = DBAlliance.getOrCreate(existing.getFromId());
        DBAlliance toAA = DBAlliance.getOrCreate(existing.getToId());

        if (previous == null) {
            title += " " + current.getType();
        } else if (current == null) {
            title += " " + previous.getType();
        } else  if(current.getType() != previous.getType()) {
            title += " " + (previous.getType() + "->" + current.getType());
        } else {
            title += " " + current.getType();
        }

        StringBuilder body = new StringBuilder();
        body.append("From: " + PnwUtil.getMarkdownUrl(existing.getFromId(), true)).append("\n");
        body.append("To: " + PnwUtil.getMarkdownUrl(existing.getToId(), true)).append("\n");

        String finalTitle = title;
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
                        if (!tracked.contains(existing.getFromId()) && !tracked.contains(existing.getToId())) {
                            if (fromAA.getRank() > 50 && toAA.getRank() > 50) {
                                return;
                            }
                        } else {
                            finalBody.append("\n\n**IN SPHERE**");
                        }
                    }
                }
                DiscordUtil.createEmbedCommand(channel, finalTitle, finalBody.toString());
            }
        });
    }
}
