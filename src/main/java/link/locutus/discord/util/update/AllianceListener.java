package link.locutus.discord.util.update;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.AllianceMeta;
import link.locutus.discord.db.entities.AllianceMetric;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.event.alliance.AllianceCreateEvent;
import link.locutus.discord.event.game.TurnChangeEvent;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import com.google.common.eventbus.Subscribe;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.util.scheduler.CaughtTask;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class AllianceListener {

    public AllianceListener() {
        Locutus.imp().addTaskSeconds(new CaughtTask() {
            @Override
            public void runUnsafe() throws Exception {
                runMilitarizationAlerts();
            }
        }, 15);
    }
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
                AllianceList aaList = db.getAllianceList();
                if (aaList == null) continue;

                Set<DBNation> nations = aaList.getNations(DBNation::isTaxable);
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

    public static void runMilitarizationAlerts() {

        double thresholdFivedays = 0.02;
        double thresholdDaily = 0.05;
        double thresholdTurnly = 0.1;

        double thresholdMin = 0.2;

        Map<Integer, Double> milPreviousMap = new HashMap<>();
        Map<Integer, Double> milNowMap = new HashMap<>();
        Map<Integer, Long> milDateMap = new HashMap<>();

        Map<DBAlliance, Integer> alertAlliances = new LinkedHashMap<>();

        long now = System.currentTimeMillis();

        // get top 80 alliance
        Map<Integer, List<DBNation>> nationsByAA = Locutus.imp().getNationDB().getNationsByAlliance(false, true, true, true);
        int rank = 0;
        for (Map.Entry<Integer, List<DBNation>> entry : nationsByAA.entrySet()) {
            rank++;
            if (rank > 80) break;
            int allianceId = entry.getKey();
            List<DBNation> nations = entry.getValue();

            // get previous militarization
            DBAlliance alliance = DBAlliance.getOrCreate(allianceId);
            ByteBuffer milBuf = alliance.getMeta(AllianceMeta.GROUND_MILITARIZATION);
            ByteBuffer milDateBuf = alliance.getMeta(AllianceMeta.GROUND_MILITARIZATION_DATE);

            int count = 0;
            double groundPctTotal = 0;

            // get current militarization
            for (DBNation nation : nations) {
                // skip < c10
                if (nation.getCities() < 10) continue;
                groundPctTotal += (nation.getSoldierPct() + nation.getTankPct()) / 2d;
                count++;
            }

            double groundPctAvg = groundPctTotal / count;

            if (milBuf == null) {
                alliance.setMeta(AllianceMeta.GROUND_MILITARIZATION_DATE, 0L);
                alliance.setMeta(AllianceMeta.GROUND_MILITARIZATION, groundPctAvg);
                continue;
            }

            double previousMil = milBuf.getDouble();
            long previousMilDate = milDateBuf.remaining() == 4 ? milDateBuf.getInt() : milDateBuf.getLong();

            double milGain = groundPctAvg - previousMil;
            if (milGain < thresholdFivedays) continue;

            long timeSinceLastAlert = now - previousMilDate;

            milPreviousMap.put(allianceId, previousMil);
            milNowMap.put(allianceId, groundPctAvg);
            milDateMap.put(allianceId, previousMilDate);

            if (
                    milGain >= thresholdFivedays && timeSinceLastAlert > TimeUnit.DAYS.toMillis(5) ||
                    milGain >= thresholdDaily && timeSinceLastAlert > TimeUnit.DAYS.toMillis(1) ||
                    milGain >= thresholdTurnly && timeSinceLastAlert > TimeUnit.HOURS.toMillis(2)
            ) {
                if (groundPctAvg >= thresholdMin) {
                    alertAlliances.put(alliance, rank);
                }

                // set meta
                alliance.setMeta(AllianceMeta.GROUND_MILITARIZATION_DATE, now);
                alliance.setMeta(AllianceMeta.GROUND_MILITARIZATION, groundPctAvg);


            } else {
                if (groundPctAvg < previousMil) {
//                    alliance.setMeta(AllianceMeta.GROUND_MILITARIZATION_DATE, now);
                    alliance.setMeta(AllianceMeta.GROUND_MILITARIZATION, groundPctAvg);
                }
                continue;
            }


        }

        if (alertAlliances.isEmpty()) return;

        AlertUtil.forEachChannel(f -> true, GuildKey.AA_GROUND_UNIT_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
            @Override
            public void accept(MessageChannel channel, GuildDB db) {
                // get alert role
                Role role = Roles.GROUND_MILITARIZE_ROLE.toRole(db);

                String title = "Ground Militarization Alert";
                StringBuilder body = new StringBuilder();

                Integer topX = GuildKey.AA_GROUND_TOP_X.getOrNull(db);
                Set<Integer> groundCoalition = db.getCoalition(Coalition.GROUND_ALERTS);

                BiPredicate<DBAlliance, Integer> allowed = (alliance, rank) -> {
                    boolean isGroundCoalition = groundCoalition.contains(alliance.getAlliance_id());
                    boolean isTopX = topX != null && rank <= topX;
                    // return true if either is true or topX is null and groundCoalition is empty
                    return (isGroundCoalition || isTopX) || (topX == null && groundCoalition.isEmpty());
                };

                Set<Integer> allowedIds = new HashSet<>();
                for (Map.Entry<DBAlliance, Integer> entry : alertAlliances.entrySet()) {
                    DBAlliance alliance = entry.getKey();
                    int rank = entry.getValue();
                    if (!allowed.test(alliance, rank)) continue;
                    allowedIds.add(alliance.getAlliance_id());

                    String previousMilStr = MathMan.format(milPreviousMap.get(alliance.getAlliance_id()) * 100);
                    String nowMilStr = MathMan.format(milNowMap.get(alliance.getAlliance_id()) * 100);
                    long date = milDateMap.get(alliance.getAlliance_id());
                    String dateStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, now - date);

                    body.append("- #" + rank + " " + alliance.getMarkdownUrl() + ": `" + previousMilStr + "%` -> `" + nowMilStr + "%` (" + dateStr + ")\n");
                }
                if (allowedIds.isEmpty()) return;

                body.append("\n**Press `graph` for 7d ground graph.**");

                CM.alliance.stats.metricsByTurn graphCmd = CM.alliance.stats.metricsByTurn.cmd.create(AllianceMetric.GROUND_PCT.name(), StringMan.join(allowedIds, ","), "7d");
                IMessageBuilder msg = new DiscordChannelIO(channel).create()
                        .embed(title, body.toString())
                        .commandButton(CommandBehavior.UNDO_REACTION, graphCmd, "graph");

                if (role != null) {
                    msg.append(role.getAsMention());
                }

                msg.send();
            }
        });
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
