package link.locutus.discord.util.update;

import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DiscordMeta;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.apiv1.enums.Rank;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PrivateChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

public class LeavingBeigeAlert {
    private final long alertMSBefore;

    public LeavingBeigeAlert() {
        this(TimeUnit.MINUTES.toMillis(8));
    }

    public LeavingBeigeAlert(long alertMSBefore) {
        this.alertMSBefore = alertMSBefore;
    }

    public void run() {
        ByteBuffer lastTurnBuf = Locutus.imp().getDiscordDB().getInfo(DiscordMeta.BEIGE_ALERT_TASK_TURN, 0);
        long lastTurn = lastTurnBuf == null ? 0 : lastTurnBuf.getLong();
        run(lastTurn, true);
    }

    public void run(long lastTurn, boolean update) {
        long newTurn = TimeUtil.getTurn(System.currentTimeMillis() + alertMSBefore);
        if (newTurn != lastTurn) {
            if (update) {
                Locutus.imp().getDiscordDB().setInfo(DiscordMeta.BEIGE_ALERT_TASK_TURN, 0, ArrayUtil.longToBytes(newTurn));
            }

            alertNations(newTurn, update);
        }
    }

    private void alertNations(long nextTurn) {
        alertNations(nextTurn, true);
    }

    private void alertNations(long nextTurn, boolean update) {
        long nextTurnMs = TimeUtil.getTimeFromTurn(nextTurn);

        Set<DBNation> leavingBeige = new HashSet<>();
        Map<DBNation, Double> lootEstimateByNation = new HashMap<>();

        Map<DBNation, Map<DBNation, Boolean>> nationTargets = new HashMap<>(); // boolean = is subscribed
        Map<DBNation, Double> scoreLeewayMap = new HashMap<>();
        Function<DBNation, Double> scoreLeewayFunc = f -> scoreLeewayMap.computeIfAbsent(f, n -> {
            ByteBuffer buf = n.getMeta(NationMeta.BEIGE_ALERT_SCORE_LEEWAY);
            return buf == null ? 0 : buf.getDouble();
        });

        Collection<DBNation> nations = Locutus.imp().getNationDB().getNations().values();
        for (DBNation target : nations) {
            if (target.getVm_turns() > 1) continue;
            if (target.isBeige() && target.getBeigeTurns() <= 1 || (target.getVm_turns() == 1 && !target.isBeige())) {
                leavingBeige.add(target);

                Set<DBNation> attackers = Locutus.imp().getNationDB().getBeigeRemindersByTarget(target);
                if (attackers != null) {
                    for (DBNation attacker : attackers) {
                        if (attacker.getVm_turns() > 0 || attacker.getOff() >= attacker.getMaxOff()) continue;
                        if (update) {
                            Locutus.imp().getNationDB().deleteBeigeReminder(attacker.getNation_id(), target.getNation_id());
                        }

                        if (attacker.getActive_m() > 7200 || attacker.getVm_turns() > 0 || attacker.getPosition() <= Rank.APPLICANT.id) continue;

                        GuildDB db = Locutus.imp().getGuildDBByAA(attacker.getAlliance_id());
                        if (db == null || !db.isWhitelisted() || !db.hasCoalitionPermsOnRoot(Coalition.RAIDPERMS)) continue;

                        double score = attacker.getScore();
                        double leeway = scoreLeewayFunc.apply(attacker);
                        if (target.getScore() < score * 0.75 - leeway || target.getScore() > score * 1.75) continue;

                        nationTargets.computeIfAbsent(attacker, f -> new HashMap<>()).put(target, true);
                    }
                }
            }
        }

        for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
            if (db.isWhitelisted() && db.hasCoalitionPermsOnRoot(Coalition.RAIDPERMS)) {
                Guild guild = db.getGuild();
//                GuildMessageChannel optOutChannel = db.getOrNull(GuildDB.Key.BEIGE_ALERT_CHANNEL, false);
//                if (optOutChannel == null) continue;
                Role beigeAlert = Roles.BEIGE_ALERT.toRole(guild);
                Role beigeAlertOptOut = Roles.BEIGE_ALERT_OPT_OUT.toRole(guild);

                if (beigeAlert == null || beigeAlertOptOut == null) continue;

                Function<DBNation, Boolean> canRaid = db.getCanRaid();

                List<Member> members = guild.getMembersWithRoles(beigeAlert);
                members.removeAll(guild.getMembersWithRoles(beigeAlertOptOut));

                if (members.isEmpty()) continue;

                Set<Integer> ids = db.getAllianceIds();

                for (Member member : members) {
                    DBNation attacker = DiscordUtil.getNation(member.getUser());
                    if (attacker == null || !ids.contains(attacker.getAlliance_id()) || attacker.getActive_m() > 7200 || attacker.getVm_turns() > 0) continue;
                    if (attacker.getOff() >= attacker.getMaxOff()) continue;

                    NationMeta.BeigeAlertRequiredStatus requiredStatus = attacker.getBeigeRequiredStatus(NationMeta.BeigeAlertRequiredStatus.ONLINE);

                    if ((attacker.getActive_m() <= 15 || requiredStatus.getApplies().test(member) || attacker.getNation_id() == Settings.INSTANCE.NATION_ID)) {
                        NationMeta.BeigeAlertMode mode = attacker.getBeigeAlertMode(NationMeta.BeigeAlertMode.NONES);
                        if (mode == NationMeta.BeigeAlertMode.NO_ALERTS) continue;
                        if (mode == null) mode = NationMeta.BeigeAlertMode.NONES;

                        Predicate<DBNation> baseFilter = new Predicate<>() {
                            @Override
                            public boolean test(DBNation f) {
                                if (f.getVm_turns() > 0) return false;
                                double score = attacker.getScore();
                                double leeway = scoreLeewayFunc.apply(attacker);
                                if (f.getScore() < score * 0.75 - leeway || f.getScore() > score * 1.75) return false;
                                return canRaid.apply(f);
                            }
                        };

                        double requiredLoot = 15000000;

                        ByteBuffer requiredLootBuf = attacker.getMeta(NationMeta.BEIGE_ALERT_REQUIRED_LOOT);
                        if (requiredLootBuf != null) {
                            requiredLoot = requiredLootBuf.getDouble();
                        }

                        for (DBNation target : leavingBeige) {
                            if (!baseFilter.test(target)) continue;
                            if (!mode.getIsAllowed().test(target)) continue;


                            double loot = lootEstimateByNation.computeIfAbsent(target, DBNation::lootTotal);
                            if (requiredLoot > 0) {
                                if (loot < requiredLoot) continue;
                            }

                            nationTargets.computeIfAbsent(attacker, f -> new HashMap<>()).putIfAbsent(target, false);
                        }
                    }
                }
            }
        }

        long diff = nextTurnMs - System.currentTimeMillis();

        String footer = "**note1**: To find specific beige targets, go to your alliance server on discord and use e.g. " +
                "`" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "raid * 25 -beige` and set a reminder using `" + Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX + "beigeReminder <nations> [required-loot]`\n" +
                "**note2:** To set how you receive alerts for *any* target leaving beige automatically, update your settings:\n" +
                " - `" + Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX + "beigeAlertMode <" + StringMan.join(NationMeta.BeigeAlertMode.values(), "|") + ">` e.g. `" + Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX + "beigeAlertMode " + NationMeta.BeigeAlertMode.NONES + "`\n" +
                " - `" + Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX + "beigeAlertRequiredStatus <" + StringMan.join(NationMeta.BeigeAlertRequiredStatus.values(), "|") + ">`\n" +
                " - `" + Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX + "beigeAlertRequiredLoot 10m` (only get auto alerts for nations with 10M+ in loot)\n\n" +
                "**These nations are leaving beige or VM next turn**" +
                " (Next turn in " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, diff) + ")\n\n" +
                "To disable automatic alerts, go to your alliance server and use `" + Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX + "beigeAlertMode NO_ALERTS`";

        for (Map.Entry<DBNation, Map<DBNation, Boolean>> entry : nationTargets.entrySet()) {
            DBNation attacker = entry.getKey();
            User user = attacker.getUser();
            if (user == null) continue;

            Locutus.imp().getExecutor().submit(new Runnable() {
                @Override
                public void run() {
                    PrivateChannel channel = RateLimitUtil.complete(user.openPrivateChannel());

                    Map<DBNation, Boolean> myTargets = entry.getValue();
                    for (Map.Entry<DBNation, Boolean> targetEntry : myTargets.entrySet()) {
                        DBNation target = targetEntry.getKey();

                        double loot = lootEstimateByNation.computeIfAbsent(target, f -> f.lootTotal());
                        String title = "Target: " + target.getNation() + ": Worth ~$" + MathMan.format(loot);
                        String msg = target.toMarkdown(true, true, true, true, false);

                        boolean isSubscription = targetEntry.getValue();
                        if (isSubscription) {
                            msg += "\n**subscribed alert**";
                        } else {
                            NationMeta.BeigeAlertMode mode = attacker.getBeigeAlertMode(NationMeta.BeigeAlertMode.NONES);
                            if (mode == NationMeta.BeigeAlertMode.NO_ALERTS) return;

                            msg += "\n**auto alert**";
                        }

                        DiscordUtil.createEmbedCommand(channel, title, msg);
                        RateLimitUtil.queueWhenFree(channel.sendMessage(footer));
                    }
                }
            });

        }
    }
}
