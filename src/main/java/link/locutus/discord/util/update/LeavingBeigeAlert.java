package link.locutus.discord.util.update;

import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DiscordMeta;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.ArrayUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

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

    public static boolean testBeigeAlert(GuildDB db, boolean throwError) {
        if (db == null) {
            if (throwError) throw new IllegalArgumentException("Your are not in a guild");
            return false;
        }
        if (!db.isWhitelisted()) {
            if (throwError) throw new IllegalArgumentException("Guild is not whitelisted");
            return false;
        } if (!db.hasCoalitionPermsOnRoot(Coalition.RAIDPERMS)) {
            if (throwError) throw new IllegalArgumentException("Guild does not have coalition.raidperms");
            return false;
        }

        // db.isWhitelisted() && db.hasCoalitionPermsOnRoot(Coalition.RAIDPERMS)

        Role beigeAlert = Roles.BEIGE_ALERT.toRole2(db.getGuild());
        if (beigeAlert == null) {
            if (throwError) throw new IllegalArgumentException("No BEIGE_ALERT role set");
            return false;
        }
//        Role beigeAlertOptOut = Roles.BEIGE_ALERT_OPT_OUT.toRole(db.getGuild());
        return true;
    }

    public static boolean testBeigeAlertAuto(ValueStore store, DBNation attacker, DBNation target, double requiredLoot, NationMeta.BeigeAlertMode mode, Function<DBNation, Boolean> canRaid, Function<DBNation, Double> scoreLeewayFunc, Map<DBNation, Double> lootEstimateByNation, boolean throwError) {
        if (target.getVm_turns() > 0) {
            if (throwError) throw new IllegalArgumentException("Target is in vacation mode");
            return false;
        }
        double score = attacker.getScore();
        double leeway = scoreLeewayFunc.apply(attacker);
        if (target.getScore() < score * 0.75 - leeway || target.getScore() > score * PW.WAR_RANGE_MAX_MODIFIER) {
            if (throwError) throw new IllegalArgumentException("Target is not within 75% to 175% of attacker's score (leeway: " + leeway + ")\nSee: " + CM.alerts.beige.setBeigeAlertScoreLeeway.cmd.toSlashMention());
            return false;
        }
        if (!canRaid.apply(target)) {
            if (throwError) throw new IllegalArgumentException("Target is in the Do Not Raid list. See: " + CM.coalition.list.cmd.toSlashMention());
            return false;
        }

        if (!mode.getIsAllowed().test(target)) {
            if (throwError) throw new IllegalArgumentException("Your beige mode is set to: " + mode.name() + "\nSee: " + CM.alerts.beige.beigeAlertMode.cmd.toSlashMention());
            return false;
        }

        double loot = lootEstimateByNation.computeIfAbsent(target, f -> f.lootTotal(store));
        if (requiredLoot > 0) {
            if (loot < requiredLoot) {
                if (throwError) throw new IllegalArgumentException("Target is below the loot threshold ($" + MathMan.format(loot) + " < $" + MathMan.format(requiredLoot) + "). See: " + CM.alerts.beige.beigeAlertRequiredLoot.cmd.toSlashMention());
                return false;
            }
        }

        return true;
    }

    public static boolean testBeigeAlertAuto(GuildDB db, Member member, Role beigeAlert, Role beigeAlertOptOut, Set<Integer> allianceIds, boolean throwError, boolean ignoreMaxOff) {
        DBNation attacker = DiscordUtil.getNation(member.getUser());

        if (beigeAlert == null) {
            if (throwError) throw new IllegalArgumentException("No BEIGE_ALERT role set. See: " + CM.role.setAlias.cmd.toSlashMention());
            return false;
        }
        if (!member.getUnsortedRoles().contains(beigeAlert)) {
            if (throwError) throw new IllegalArgumentException("You do not have the beige alert role: " + beigeAlert.getName());
            return false;
        }
        if (beigeAlertOptOut != null && member.getUnsortedRoles().contains(beigeAlertOptOut)) {
            if (throwError) throw new IllegalArgumentException("You have the beige alert opt out role: " + beigeAlertOptOut.getName());
            return false;
        }

        if (attacker == null) {
            if (throwError) throw new IllegalArgumentException("You are not registered. See " + CM.register.cmd.toSlashMention());
            return false;
        }
        if (allianceIds != null && !allianceIds.contains(attacker.getAlliance_id())) {
            if (throwError) throw new IllegalArgumentException("You are not in any of the alliances: ID:" + StringMan.getString(allianceIds));
            return false;
        }
        if (attacker.active_m() > 7200) {
            if (throwError) throw new IllegalArgumentException("You are more than 1 week inactive");
            return false;
        }
        if (attacker.getVm_turns() > 0) {
            if (throwError) throw new IllegalArgumentException("You are in vacation mode");
            return false;
        }

        NationMeta.BeigeAlertRequiredStatus requiredStatus = attacker.getBeigeRequiredStatus(NationMeta.BeigeAlertRequiredStatus.ONLINE);

        if ((attacker.active_m() <= 15 || requiredStatus.getApplies().test(member) || attacker.getNation_id() == Locutus.loader().getNationId())) {
            NationMeta.BeigeAlertMode mode = attacker.getBeigeAlertMode(NationMeta.BeigeAlertMode.NO_ALERTS);
            if (mode == NationMeta.BeigeAlertMode.NO_ALERTS) {
                if (throwError) throw new IllegalArgumentException("You have disabled beige alerts. See " + CM.alerts.beige.beigeAlertMode.cmd.toSlashMention());
                return false;
            }

            if (attacker.getOff() >= attacker.getMaxOff() && !ignoreMaxOff) {
                if (throwError) throw new IllegalArgumentException("You are at max offense wars");
                return false;
            }
        } else {
            if (throwError) throw new IllegalArgumentException("You are not " + requiredStatus.name().toLowerCase() + " (see " + CM.alerts.beige.beigeAlertRequiredStatus.cmd.toSlashMention());
            return false;
        }
        return true;
    }

    private static boolean testBeigeAlert(DBNation target, boolean throwError, boolean checkBeige, boolean checkVM) {
        if (target.getVm_turns() > 1 && checkVM) {
            if (throwError) throw new IllegalArgumentException("Target nation is VM");
            return false;
        }
        if (!target.isBeige() && target.getVm_turns() == 0) {
            if (throwError) throw new IllegalArgumentException("Target is not beige or VM");
            return false;
        }
        if (target.getBeigeTurns() > 1 && checkBeige) {
            if (throwError) throw new IllegalArgumentException("Target has more than 1 turn of beige");
            return false;
        }
        if (target.getVm_turns() == 1 && target.isBeige()) {
            if (throwError) throw new IllegalArgumentException("Target is VM and beige");
            return false;
        }
        return true;
    }

    public static boolean testBeigeAlert(GuildDB db, DBNation target, DBNation attacker, Function<DBNation, Double> scoreLeewayCache, boolean throwError, boolean checkBeige, boolean checkVM, boolean checkMaxOff) {
        if (!testBeigeAlert(db, throwError)) return false;
        if (!testBeigeAlert(target, throwError, checkBeige, checkVM)) return false;
        if (attacker != null) {
            if (attacker.getVm_turns() > 0) {
                if (throwError) throw new IllegalArgumentException("Your are VM");
                return false;
            }
            if (attacker.getOff() >= attacker.getMaxOff() && checkMaxOff) {
                if (throwError) throw new IllegalArgumentException("Your are at max offensives");
                return false;
            }

            if (attacker.active_m() > 7200) {
                if (throwError) throw new IllegalArgumentException("Your are inactive (1w)");
                return false;
            }
            if (attacker.getPosition() <= Rank.APPLICANT.id) {
                if (throwError) throw new IllegalArgumentException("Your are not a member of an alliance");
                return false;
            }

            double score = attacker.getScore();
            double leeway;
            if (scoreLeewayCache != null) {
                leeway = scoreLeewayCache.apply(attacker);
            } else {
                ByteBuffer leewayBits = attacker.getMeta(NationMeta.BEIGE_ALERT_SCORE_LEEWAY);
                leeway = leewayBits != null ? leewayBits.getDouble() : 0;
            }
            if (target.getScore() < score * 0.75 - leeway || target.getScore() > score * PW.WAR_RANGE_MAX_MODIFIER) {
                if (throwError) throw new IllegalArgumentException("You are not in range of target " + MathMan.format(target.getScore()) + ". See: " + CM.alerts.beige.setBeigeAlertScoreLeeway.cmd.toSlashMention());
            };
        }
        return true;
    }

    private void alertNations(long nextTurn, boolean update) {
        long nextTurnMs = TimeUtil.getTimeFromTurn(nextTurn);

        Set<DBNation> leavingBeige = new ObjectOpenHashSet<>();
        Map<DBNation, Double> lootEstimateByNation = new Object2DoubleOpenHashMap<>();

        Map<DBNation, Map<DBNation, Boolean>> nationTargets = new HashMap<>(); // boolean = is subscribed
        Map<DBNation, Double> scoreLeewayMap = new HashMap<>();
        Function<DBNation, Double> scoreLeewayFunc = f -> scoreLeewayMap.computeIfAbsent(f, n -> {
            ByteBuffer buf = n.getMeta(NationMeta.BEIGE_ALERT_SCORE_LEEWAY);
            return buf == null ? 0 : buf.getDouble();
        });

        Set<DBNation> targetNations = new ObjectOpenHashSet<>();
        Collection<DBNation> nations = Locutus.imp().getNationDB().getAllNations();
        for (DBNation target : nations) {
            if (!testBeigeAlert(target, false, true, true)) continue;

            leavingBeige.add(target);

            Set<DBNation> attackers = Locutus.imp().getNationDB().getBeigeRemindersByTarget(target);
            if (attackers != null) {
                for (DBNation attacker : attackers) {
                    if (attacker.getVm_turns() > 1) continue;
                    if (update) {
                        Locutus.imp().getNationDB().deleteBeigeReminder(attacker.getNation_id(), target.getNation_id());
                    }
                    GuildDB db = Locutus.imp().getGuildDBByAA(attacker.getAlliance_id());
                    if (!testBeigeAlert(db, target, attacker, scoreLeewayFunc, false, true, true, true)) continue;

                    nationTargets.computeIfAbsent(attacker, f -> new HashMap<>()).put(target, true);
                    targetNations.add(target);
                }
            }
        }

        ValueStore<DBNation> cacheStore = PlaceholderCache.createCache(leavingBeige, DBNation.class);

        for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
            if (db.isWhitelisted() && db.hasCoalitionPermsOnRoot(Coalition.RAIDPERMS)) {
                Guild guild = db.getGuild();
//                GuildMessageChannel optOutChannel = db.getOrNull(GuildKey.BEIGE_ALERT_CHANNEL, false);
//                if (optOutChannel == null) continue;
                Role beigeAlert = Roles.BEIGE_ALERT.toRole2(guild);
                Role beigeAlertOptOut = Roles.BEIGE_ALERT_OPT_OUT.toRole2(guild);

                if (beigeAlert == null) continue;

                Function<DBNation, Boolean> canRaid = db.getCanRaid();

                List<Member> members = guild.getMembersWithRoles(beigeAlert);
                if (beigeAlertOptOut != null) {
                    members.removeAll(guild.getMembersWithRoles(beigeAlertOptOut));
                }

                if (members.isEmpty()) continue;

                Set<Integer> ids = db.getAllianceIds(false);

                for (Member member : members) {
                    DBNation attacker = DiscordUtil.getNation(member.getUser());

                    if (!testBeigeAlertAuto(db, member, beigeAlert, beigeAlertOptOut, ids, false, false)) {
                        continue;
                    }

                    NationMeta.BeigeAlertRequiredStatus requiredStatus = attacker.getBeigeRequiredStatus(NationMeta.BeigeAlertRequiredStatus.ONLINE);

                    NationMeta.BeigeAlertMode mode = attacker.getBeigeAlertMode(NationMeta.BeigeAlertMode.NO_ALERTS);
                    if (mode == NationMeta.BeigeAlertMode.NO_ALERTS) continue;

                    double requiredLoot = 15000000;
                    ByteBuffer requiredLootBuf = attacker.getMeta(NationMeta.BEIGE_ALERT_REQUIRED_LOOT);
                    if (requiredLootBuf != null) {
                        requiredLoot = requiredLootBuf.getDouble();
                    }

                    for (DBNation target : leavingBeige) {
                        if (testBeigeAlertAuto(cacheStore, attacker, target, requiredLoot, mode, canRaid, scoreLeewayFunc, lootEstimateByNation, false)) {
                            nationTargets.computeIfAbsent(attacker, f -> new HashMap<>()).putIfAbsent(target, false);
                        }
                    }
                }
            }
        }

        long diff = nextTurnMs - System.currentTimeMillis();

        String footer = "**note1**: To find specific beige targets, go to your alliance server on discord and use e.g. " +
                CM.war.find.raid.cmd.targets("*").numResults("25").beigeTurns("84") + " or set a reminder using " + CM.alerts.beige.beigeAlert.cmd.toSlashMention() + "\n" +
                "**note2:** To set how you receive alerts for *any* target leaving beige automatically, update your settings:\n" +
                "- " + CM.alerts.beige.beigeAlertMode.cmd.toSlashMention() + "\n" +
                "- " + CM.alerts.beige.beigeAlertRequiredStatus.cmd.toSlashMention() + "\n" +
                "- " + CM.alerts.beige.beigeAlertRequiredLoot.cmd.toSlashMention() + " (only get auto alerts for nations over X loot)\n\n" +
                "**These nations are leaving beige or VM next turn**" +
                " (Next turn in " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, diff) + ")\n\n" +
                "`note: Default day change is 5 minutes, turn change is 30s`\n" +
                "To disable automatic alerts, go to your alliance server and use " + CM.alerts.beige.beigeAlertMode.cmd.mode(NationMeta.BeigeAlertMode.NO_ALERTS.name()).toSlashCommand();

        for (Map.Entry<DBNation, Map<DBNation, Boolean>> entry : nationTargets.entrySet()) {
            DBNation attacker = entry.getKey();
            User user = attacker.getUser();
            if (user == null) continue;

            Locutus.imp().getExecutor().submit(new Runnable() {
                @Override
                public void run() {
                    PrivateChannel channel = RateLimitUtil.complete(user.openPrivateChannel());
                    DiscordChannelIO io = new DiscordChannelIO(channel);
                    IMessageBuilder msg = io.create();

                    Map<DBNation, Boolean> myTargets = entry.getValue();
                    for (Map.Entry<DBNation, Boolean> targetEntry : myTargets.entrySet()) {
                        DBNation target = targetEntry.getKey();

                        double loot = lootEstimateByNation.computeIfAbsent(target, f -> f.lootTotal(cacheStore));
                        String title = "Target: " + target.getNation() + ": Worth ~$" + MathMan.format(loot);
                        String body = target.toMarkdown(true, true, true, true, true, false);

                        boolean isSubscription = targetEntry.getValue();
                        if (isSubscription) {
                            body += "\n**subscribed alert**";
                        } else {
                            NationMeta.BeigeAlertMode mode = attacker.getBeigeAlertMode(NationMeta.BeigeAlertMode.NONES);
                            if (mode == NationMeta.BeigeAlertMode.NO_ALERTS) return;

                            body += "\n**auto alert**";
                        }

                        msg.embed(title, body);
                    }
                    msg.append(footer);
                    msg.send();
                }
            });

        }
    }
}
