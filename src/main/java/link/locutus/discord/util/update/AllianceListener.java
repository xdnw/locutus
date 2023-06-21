package link.locutus.discord.util.update;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.event.alliance.AllianceCreateEvent;
import link.locutus.discord.event.game.TurnChangeEvent;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import com.google.common.eventbus.Subscribe;
import link.locutus.discord.apiv1.enums.Rank;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class AllianceListener {
    @Subscribe
    public void onTurnChange(TurnChangeEvent event) {

        { // Update offshores
            for (DBAlliance alliance : Locutus.imp().getNationDB().getAlliances()) {
                alliance.findParentOfThisOffshore();
            }
        }

        { // update internal taxrates
            for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
                if (db.isDelegateServer()) continue;

                Set<DBNation> nations = db.getAllianceList().getNations(DBNation::isTaxable);
                if (nations.isEmpty()) continue;

                Map<NationFilter, TaxRate> internal = GuildKey.REQUIRED_INTERNAL_TAXRATE.getOrNull(db, false);
                Map<NationFilter, Integer> taxrate = GuildKey.REQUIRED_TAX_BRACKET.getOrNull(db, false);
                if ((internal == null || internal.isEmpty())
//                        && (taxrate == null || taxrate.isEmpty())
                ) {
                    continue;
                }

                MessageChannel output = db.getResourceChannel(0);
                if (output == null) output = GuildKey.ADDBALANCE_ALERT_CHANNEL.getOrNull(db, false);

                try {
                    List<String> messages = new ArrayList<>();
                    db.getHandler().setNationInternalTaxRate(nations, messages::add);
//                    db.getHandler().setNationTaxBrackets(nations, messages::add);
                    if (!messages.isEmpty() && output != null) {
                        StringBuilder footer = new StringBuilder();
                        if (internal != null) {
                            footer.append("Configure automatic internal taxrate: " + CM.settings.info.cmd.toSlashMention() + " with key " + GuildKey.REQUIRED_INTERNAL_TAXRATE.name() + "\n");
                        }
//                        if (taxrate != null) {
//                            footer.append("Configure automatic tax bracket: " + CM.settings.info.cmd.toSlashMention() + " with key " + GuildKey.REQUIRED_TAX_BRACKET.name() + "\n");
//                        }
                        DiscordUtil.sendMessage(output, StringMan.join(messages, "\n") + "\n" + footer);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        }

        { // Update tax records
            for (DBAlliance alliance : Locutus.imp().getNationDB().getAlliances()) {
                // Only update taxes if alliance has locutus taxable nations
                if (alliance.getNations(DBNation::isTaxable).isEmpty()) continue;
                try {
                    alliance.updateTaxes();
                } catch (Throwable ignore) {
                    ignore.printStackTrace();
                }
            }
        }
    }


    @Subscribe
    public void onNewAlliance(AllianceCreateEvent event) {
        DBAlliance alliance = event.getCurrent();
        int aaId = alliance.getAlliance_id();

        Set<DBNation> members = alliance.getNations();
        String title = "Created: " + alliance.getName();

        StringBuilder body = new StringBuilder();

        for (DBNation member : members) {
            if (member.getPosition() < Rank.HEIR.id) continue;
            Map.Entry<Integer, Rank> lastAA = member.getPreviousAlliance();

            body.append("Leader: " + MarkupUtil.markdownUrl(member.getNation(), member.getNationUrl()) + "\n");

            if (lastAA != null) {
                String previousAAName = Locutus.imp().getNationDB().getAllianceName(lastAA.getKey());
                body.append("- " + member.getNation() + " previously " + lastAA.getValue() + " in " + previousAAName + "\n");

                GuildDB db = Locutus.imp().getRootCoalitionServer();
                if (db != null) {
                    Set<String> coalitions = db.findCoalitions(lastAA.getKey());
                    if (!coalitions.isEmpty()) {
                        body.append("- in coalitions: `" + StringMan.join(coalitions, ",") + "`\n");
                    }
                }
            }

            Map<Integer, Integer> wars = new HashMap<>();
            for (DBWar activeWar : member.getActiveWars()) {
                int otherAA = activeWar.attacker_id == member.getNation_id() ? activeWar.defender_aa : activeWar.attacker_aa;
                if (otherAA == 0) continue;
                wars.put(otherAA, wars.getOrDefault(otherAA, 0) + 1);
            }

            if (!wars.isEmpty()) body.append("Wars:\n");
            for (Map.Entry<Integer, Integer> entry : wars.entrySet()) {
                body.append("- " + entry.getValue() + " wars vs " + PnwUtil.getMarkdownUrl(entry.getKey(), true) + "\n");
            }
        }

        DBAlliance parent = alliance.findParentOfThisOffshore();
        if (parent != null) {
            body.append("**Potential offshore for**: " + parent.getMarkdownUrl()).append("\n");
        }

        body.append(PnwUtil.getUrl(aaId, true));

        AlertUtil.forEachChannel(f -> true, GuildKey.TREATY_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
            @Override
            public void accept(MessageChannel channel, GuildDB guildDB) {
                DiscordUtil.createEmbedCommand(channel, title, body.toString());
            }
        });
    }
}
