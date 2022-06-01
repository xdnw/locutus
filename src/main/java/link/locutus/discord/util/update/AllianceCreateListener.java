package link.locutus.discord.util.update;

import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.event.AllianceCreateEvent;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import com.google.common.eventbus.Subscribe;
import link.locutus.discord.apiv1.enums.Rank;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class AllianceCreateListener {
    @Subscribe
    public void onNewAlliance(AllianceCreateEvent event) {
        int aaId = event.getAllianceId();

        List<DBNation> members = event.getMembers();
        String title = "Created: " + event.getName();

        StringBuilder body = new StringBuilder();

        for (DBNation member : members) {
            if (member.getPosition() != Rank.LEADER.id) continue;
            Map.Entry<Integer, Rank> lastAA = member.getPreviousAlliance();

            body.append("Leader: " + MarkupUtil.markdownUrl(member.getNation(), member.getNationUrl()) + "\n");

            if (lastAA != null) {
                String previousAAName = event.getPreviousAlliances().getOrDefault(lastAA.getKey(), PnwUtil.getName(lastAA.getKey(), true));
                body.append(" - " + member.getNation() + " previously " + lastAA.getValue() + " in " + previousAAName + "\n");
            }

            Map<Integer, Integer> wars = new HashMap<>();
            for (DBWar activeWar : member.getActiveWars()) {
                int otherAA = activeWar.attacker_id == member.getNation_id() ? activeWar.defender_aa : activeWar.attacker_aa;
                if (otherAA == 0) continue;
                wars.put(otherAA, wars.getOrDefault(otherAA, 0) + 1);
            }

            if (!wars.isEmpty()) body.append("Wars:\n");
            for (Map.Entry<Integer, Integer> entry : wars.entrySet()) {
                body.append(" - " + entry.getValue() + " wars vs " + PnwUtil.getMarkdownUrl(entry.getKey(), true) + "\n");
            }


        }
        body.append(PnwUtil.getUrl(aaId, true));

        AlertUtil.forEachChannel(f -> true, GuildDB.Key.TREATY_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
            @Override
            public void accept(MessageChannel channel, GuildDB guildDB) {
                DiscordUtil.createEmbedCommand(channel, title, body.toString());
            }
        });
    }
}
