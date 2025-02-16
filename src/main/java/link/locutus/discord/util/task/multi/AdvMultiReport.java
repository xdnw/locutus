package link.locutus.discord.util.task.multi;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.entities.Activity;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.offshore.Auth;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class AdvMultiReport {
    public final int nationId;
    public final String nation;
    public final int allianceId;
    public final String alliance;
    public final int age;
    public final int cities;
    public final String discord;
    public final boolean discord_linked;
    public final boolean irl_verified;
    public final int customization;
    public final boolean banned;
    public final long lastActive;
    public final double percentOnline;

    public final long dateFetched;
    public final List<AdvMultiRow> rows;

    public AdvMultiReport(DBNation nation, SnapshotMultiData snapshot, Map<Integer, BigInteger> nationUids, boolean updateIfAbsent, long updateIfOutdated) {
        this.rows = new ObjectArrayList<>();
        this.nationId = nation.getId();
        this.lastActive = nation.lastActiveMs();
        this.nation = nation.getName();
        this.allianceId = nation.getAlliance_id();
        this.alliance = nation.getAllianceName();
        this.age = nation.getAgeDays();
        this.cities = nation.getCities();
        this.discord = nation.getUserDiscriminator();
        this.discord_linked = nation.isVerified();
        this.irl_verified = nation.hasProvidedIdentity(null);
        this.customization = snapshot.getCustomCount(nationId);
        this.banned = nation.hasPriorBan();

        BigInteger uid = nationUids.get(nationId);

        DiscordDB db = Locutus.imp().getDiscordDB();
        MultiResult data = db.getMultiResult(nationId);
        if (data == null) {
            throw new IllegalArgumentException("No multi result found for nation " + nationId);
        }
        if ((data.dateFetched == 0 && updateIfAbsent) || updateIfOutdated != Long.MAX_VALUE) {
            Auth auth = Locutus.imp().getRootAuth();
            data.updateIfOutdated(auth, updateIfOutdated, true);
        }
        this.dateFetched = data.dateFetched;
        Activity a1 = nation.getActivity();
        this.percentOnline = a1.getAverageByWeekTurn();

        Map<Integer, NetworkRow> networks = data.getNetwork();

        for (Map.Entry<Integer, NetworkRow> entry : networks.entrySet()) {
            int otherNationId = entry.getKey();
            BigInteger otherUid = nationUids.get(otherNationId);
            NetworkRow network = entry.getValue();

            DBNation otherNation = DBNation.getOrCreate(otherNationId);
            boolean banned = otherNation.hasPriorBan();
            MultiResult otherMultiResult = db.getMultiResult(otherNationId);
            Double reciprocal = null;
            Double reciprocal_nations = null;
            long lastLogin = otherNation.isValid() ? otherNation.lastActiveMs() : network.lastActiveMs;
            if (otherMultiResult.dateFetched > 0 && !otherMultiResult.getNetwork().isEmpty()) {
                reciprocal = getPercentReciprocal(nationId, otherNationId, data, otherMultiResult);
                reciprocal_nations = getPercentReciprocalNations(nationId, otherNationId, data, otherMultiResult);
                System.out.println("Reciprocal: " + nationId + " " + reciprocal + " | " + otherMultiResult.getNetwork().size());
            } else {
                System.out.println("Not reciprocal: " + nationId + " " + otherNationId);
            }
            long lastLoginDiff = Math.abs(lastActive - lastLogin);

            int aaId = otherNation.isValid() ? otherNation.getAlliance_id() : network.allianceId;
            String aaName = null;
            if (aaId > 0) {
                DBAlliance aa = DBAlliance.get(aaId);
                if (aa != null) {
                    aaName = aa.getName();
                }
            }

            String discordStr = otherNation.getUserDiscriminator();
            int customCount = snapshot.getCustomCount(otherNationId);
            boolean sameUid = Objects.equals(uid, otherUid);
            Double sameActivityPct = null;
            Double percent_online = null;
            if (otherNation.isValid() && nation.isValid()) {
                Activity a2 = otherNation.getActivity();
                sameActivityPct = compareActivity(a1, a2);
                percent_online = a2.getAverageByWeekTurn();
            }

            rows.add(new AdvMultiRow(
                    otherNationId,
                    otherNation.getName(),
                    aaId,
                    aaName,
                    otherNation.getAgeDays(),
                    otherNation.getCities(),
                    network.numberOfSharedIPs,
                    reciprocal,
                    reciprocal_nations,
                    sameUid,
                    banned,
                    lastLogin == 0 ? null : lastLoginDiff,
                    sameActivityPct,
                    percent_online,
                    discordStr,
                    otherNation.isVerified(),
                    otherNation.hasProvidedIdentity(null),
                    customCount
            ));
        }
    }

    private double compareActivity(Activity a1, Activity a2) {
        double[] t1 = a1.getByWeekTurn();
        double[] t2 = a2.getByWeekTurn();
        double diff = 0;
        for (int i = 0; i < t1.length; i++) {
            diff += Math.abs(t1[i] - t2[i]);
        }
        return 1 - diff / t1.length;
//        return ArrayUtil.cosineSimilarity(t1, t2);
    }

    public double getPercentReciprocal(int nat1, int nat2, MultiResult a, MultiResult b) {
        int totalIpsA = 0;
        int totalIpsB = 0;
        int matchIpA = 0;
        int matchIpB = 0;

        for (Map.Entry<Integer, NetworkRow> entry : a.getNetwork().entrySet()) {
            int otherNationId = entry.getKey();
            NetworkRow row = entry.getValue();
            totalIpsA += row.numberOfSharedIPs;
            if (nat2 == otherNationId || b.getNetwork().containsKey(otherNationId)) {
                matchIpA += row.numberOfSharedIPs;
            }
        }
        for (Map.Entry<Integer, NetworkRow> entry : b.getNetwork().entrySet()) {
            int otherNationId = entry.getKey();
            NetworkRow row = entry.getValue();
            totalIpsB += row.numberOfSharedIPs;
            if (nat1 == otherNationId || a.getNetwork().containsKey(otherNationId)) {
                matchIpB += row.numberOfSharedIPs;
            }
        }
        if (matchIpA == 0 || matchIpB == 0) {
            return 0;
        }
        double pctA = totalIpsA == 0 ? 0 : (double) matchIpA / totalIpsA;
        double pctB = totalIpsB == 0 ? 0 : (double) matchIpB / totalIpsB;
        return Math.min(pctA, pctB);
    }

    public double getPercentReciprocalNations(int nat1, int nat2, MultiResult a, MultiResult b) {
        int totalNationsA = a.getNetwork().size();
        int totalNationsB = b.getNetwork().size();
        int matchNationsA = 0;
        int matchNationsB = 0;

        for (Map.Entry<Integer, NetworkRow> entry : a.getNetwork().entrySet()) {
            int otherNationId = entry.getKey();
            if (nat2 == otherNationId || b.getNetwork().containsKey(otherNationId)) {
                matchNationsA++;
            }
        }
        for (Map.Entry<Integer, NetworkRow> entry : b.getNetwork().entrySet()) {
            int otherNationId = entry.getKey();
            if (nat1 == otherNationId || a.getNetwork().containsKey(otherNationId)) {
                matchNationsB++;
            }
        }
        if (matchNationsA == 0 || matchNationsB == 0) {
            return 0;
        }
        double pctA = totalNationsA == 0 ? 0 : (double) matchNationsA / totalNationsA;
        double pctB = totalNationsB == 0 ? 0 : (double) matchNationsB / totalNationsB;
        return Math.min(pctA, pctB);
    }

}
