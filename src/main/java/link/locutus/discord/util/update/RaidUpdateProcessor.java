package link.locutus.discord.util.update;

import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.MathMan;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

public class RaidUpdateProcessor implements Runnable {
    private Logger logger = Logger.getLogger(RaidUpdateProcessor.class.getSimpleName());
    private static Set<Integer> checkSlotBackQueue = new LinkedHashSet<>();
    private static Set<Integer> checkSlotQueue = new LinkedHashSet<>();
    private static Queue<Integer> checkSlotBuffer = new ConcurrentLinkedQueue<>();
    private final PassiveExpiringMap<Long, Boolean> pingFlag;

    public RaidUpdateProcessor() {
        this.pingFlag = new PassiveExpiringMap<Long, Boolean>(60, TimeUnit.MINUTES);
        long start = System.currentTimeMillis();
        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNations();

//        for (DBNation nation : nations.values()) {
//            if (nation.getAlliance_id() != 0) continue;
//            if (nation.getDef() != 3) continue;
//            if (nation.getActive_m() < 2440) continue;
//            if (nation.getVm_turns() != 0) continue;
////            if (nation.isBeige()) continue;
//            if (!isInScore(nation)) continue;
//            double loot = nation.lootTotal();
//            if (loot < 100000000) continue;
//
//            synchronized (checkSlotBackQueue) {
//                checkSlotBackQueue.add(nation.getNation_id());
//            }
//        }

        long diff = System.currentTimeMillis() - start;
        logger.info(checkSlotBackQueue.size() + " high value targets");
        logger.info("Loaded raid processor: " + (diff) + "ms");
    }

    public void checkSlot(int nationId) {
        checkSlotBuffer.add(nationId);
    }

    @Override
    public void run() {
        try {
            synchronized (checkSlotQueue) {
                while (!checkSlotBuffer.isEmpty()) {
                    checkSlotQueue.add(checkSlotBuffer.poll());
                }
                while (!checkSlotQueue.isEmpty()) {
                    Iterator<Integer> iter = checkSlotQueue.iterator();
                    if (iter.hasNext()) {
                        Integer nationId = iter.next();
                        iter.remove();
                        DBNation nation = Locutus.imp().getNationDB().getNation(nationId);
                        if (nation != null) {
                            try {
                                raidAlertChecked(nation);
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                synchronized (checkSlotBackQueue) {
                    if (!checkSlotBackQueue.isEmpty()) {
                        Iterator<Integer> iter = checkSlotBackQueue.iterator();
                        Integer nationId = iter.next();
                        iter.remove();
                        DBNation nation = Locutus.imp().getNationDB().getNation(nationId);
                        if (nation != null && !nation.isBeige()) {
                            try {
                                if (nation.getDef() <= 3) {
                                    if (pingFlag.containsKey((long) nation.getNation_id())) {
                                        return;
                                    }
                                    pingFlag.put((long) nation.getNation_id(), true);
                                }
                                raidAlertChecked(nation);
                            } catch (IOException e) {
                                e.printStackTrace();
                            } finally {
                                checkSlotBackQueue.add(nationId);
                            }
                        }
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private boolean isInScore(DBNation defender) {
        DBNation rootNation = Locutus.imp().getNationDB().getNation(Settings.INSTANCE.NATION_ID);
        if (rootNation != null && rootNation.getOff() < rootNation.getMaxOff() && !rootNation.isBeige() && (defender.getAlliance_id() == 0 || defender.active_m() > 10000)) {
            double minScore = defender.getScore() / 1.75;
            double maxScore = defender.getScore() / 0.75;
            if (rootNation.getScore() >= minScore && rootNation.getScore() <= maxScore) {
                return true;
            }
        }
        return false;
    }

    private boolean raidAlertChecked(DBNation defender) throws IOException {
        if (defender.getActive_m() > 260 * 60 * 24) return false;
        double loot = defender.lootTotal();
        if (loot < 5000000 || defender.isBeige()) {
            return false;
        }

        if (defender.getDef() == 3) {
            String url = defender.getNationUrl() + "&display=war";
            String html = FileUtil.readStringFromURL(url);
            int matches = StringUtils.countMatches(html, " Active War");

            if (matches == 3 || !html.contains("View Wars")) {
                if (loot > 30000000 && defender.getAlliance_id() == 0) {
                    synchronized (checkSlotBackQueue) {
                        checkSlotBackQueue.add(defender.getNation_id());
                    }
                }
                return false;
            }
        }
        return raidAlert(defender, loot);
    }

    private boolean raidAlert(DBNation defender, double loot) throws IOException {
        if (loot < 10000000 || defender.isBeige() || defender.getVm_turns() != 0) return false;

        String msg = defender.toMarkdown(true, true, true, true, false);
        String title = "Target: " + defender.getNation() + ": You can loot: ~$" + MathMan.format(loot);

        String url = "https://politicsandwar.com/nation/war/declare/id=" + defender.getNation_id();
        if (defender.getCities() >= 10) {
            msg += "\n" + url;
        }

        {
            if (Settings.INSTANCE.LEGACY_SETTINGS.OPEN_DESKTOP_FOR_RAIDS && isInScore(defender)) {
                AlertUtil.openDesktop(url);
            }
        }

        List<DBNation> allNations = new ArrayList<>(Locutus.imp().getNationDB().getNations().values());
        Map<Integer, Double> allianceScores = new HashMap<>();
        for (DBNation nation : allNations) {
            allianceScores.put(nation.getAlliance_id(), nation.getScore() + allianceScores.getOrDefault(nation.getAlliance_id(), 0d));
        }

        String finalMsg = msg;
        AlertUtil.forEachChannel(f -> f.hasCoalitionPermsOnRoot(Coalition.RAIDPERMS), GuildDB.Key.BEIGE_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
            @Override
            public void accept(MessageChannel channel, GuildDB guildDB) {
                if (!guildDB.isWhitelisted() || !guildDB.hasCoalitionPermsOnRoot(Coalition.RAIDPERMS)) return;

                if (guildDB.violatesDNR(defender) || (defender.getPosition() > 1 && defender.getActive_m() < 10000)) return;

                Guild guild = guildDB.getGuild();

                Role bountyRole = Roles.BEIGE_ALERT.toRole(guild);
                if (bountyRole == null) return;

                List<Member> members = guild.getMembersWithRoles(bountyRole);
                StringBuilder mentions = new StringBuilder();

                double minScore = defender.getScore() / 1.75;
                double maxScore = defender.getScore() / 0.75;

                Role optOut = Roles.BEIGE_ALERT_OPT_OUT.toRole(guild);
                int membersInRange = 0;

                for (Member member : members) {
                    PNWUser pnwUser = Locutus.imp().getDiscordDB().getUserFromDiscordId(member.getIdLong());
                    if (pnwUser == null) continue;

                    OnlineStatus status = member.getOnlineStatus();
                    if (status == OnlineStatus.OFFLINE || status == OnlineStatus.INVISIBLE) continue;

                    DBNation nation = Locutus.imp().getNationDB().getNation(pnwUser.getNationId());
                    if (nation == null || nation.getOff() >= 5 ||
                            (nation.getAvg_infra() > 1250 && defender.getAlliance_id() != 0 && (defender.getPosition() > 1 || nation.getCities() > 7))
                            || nation.getActive_m() > 2880
//                                        || nation.getSoldiers() < defender.getSoldiers()
                    ) continue;

                    if (nation.getScore() >= minScore && nation.getScore() <= maxScore) {
                        if (Locutus.imp().getWarDb().getActiveWarByNation(nation.getNation_id(), defender.getNation_id()) == null) {
                            if (optOut != null && member.getRoles().contains(optOut)) continue;
                            membersInRange++;

                            long pair = MathMan.pairInt(nation.getNation_id(), defender.getNation_id());
                            if (!pingFlag.containsKey(pair)) {
                                mentions.append(member.getAsMention() + " ");
                            }
                            pingFlag.put(pair, true);
                        }
                    }
                }
                if (membersInRange > 0) {
                    DiscordUtil.createEmbedCommand(channel, title, finalMsg);
                }

                if (mentions.length() != 0) {
                    RateLimitUtil.queue(channel.sendMessage("^ " + mentions + "(see pins to opt (out)"));
                }
            }
        });
        return true;
    }

}
