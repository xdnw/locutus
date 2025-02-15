package link.locutus.discord.util.task.multi;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
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

    public final long dateFetched;

    private final long lastActive;

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

        Map<Integer, NetworkRow> networks = data.getNetwork();

        for (Map.Entry<Integer, NetworkRow> entry : networks.entrySet()) {
            int otherNationId = entry.getKey();
            BigInteger otherUid = nationUids.get(otherNationId);
            NetworkRow network = entry.getValue();

            DBNation otherNation = DBNation.getOrCreate(otherNationId);
            boolean banned = otherNation.hasPriorBan();
            MultiResult otherMultiResult = db.getMultiResult(otherNationId);
            double reciprocal = -1;
            long lastLogin = otherNation.isValid() ? otherNation.lastActiveMs() : network.lastActiveMs;
            if (otherMultiResult != null) {
                reciprocal = getPercentReciprocal(nationId, otherNationId, data, otherMultiResult);
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
            Double sameActivityPct = 0d; // TODO

            rows.add(new AdvMultiRow(
                    otherNationId,
                    otherNation.getName(),
                    aaId,
                    aaName,
                    otherNation.getAgeDays(),
                    otherNation.getCities(),
                    network.numberOfSharedIPs,
                    reciprocal,
                    sameUid,
                    banned,
                    lastLoginDiff,
                    sameActivityPct,
                    discordStr,
                    otherNation.isVerified(),
                    otherNation.hasProvidedIdentity(null),
                    customCount
            ));
        }
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

}
