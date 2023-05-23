package link.locutus.discord.util.task.nation;

import com.politicsandwar.graphql.model.BBGame;
import com.ptsmods.mysqlw.query.QueryCondition;
import com.ptsmods.mysqlw.query.builder.SelectBuilder;
import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.task.multi.GetUid;
import com.google.api.client.util.Lists;
import link.locutus.discord.apiv1.enums.ResourceType;

import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MultiReport {
    private final int nationId;
    private final Map<Integer, Set<BigInteger>>  uuidByNation = new LinkedHashMap<>();
    private final Map<Integer, Map<BigInteger, Long>>  diffMap;
    private final Map<Integer, Set<Map.Entry<DBWar, DBWar>>> illegalWarsByMulti;
    private final Map<Integer, Set<DBTrade>> illegalTradesByMulti;

    private final Map<Integer, Long> challengeEarnings = new LinkedHashMap<>();
    private final Map<Integer, Integer> challengeGames = new LinkedHashMap<>();
    private final Map<Integer, Set<Transaction2>> illegalTransfers = new HashMap<>();
    private final Map<Integer, Set<Map.Entry<Transaction2, Transaction2>>> illegalIndirectTransfers = new HashMap<>();

    @Override
    public String toString() {
        return toString(false);
    }
    public String toString(boolean simple) {
        StringBuilder response = new StringBuilder();

        if (diffMap.isEmpty()) return "No multis founds. Networks: " + uuidByNation.getOrDefault(nationId, Collections.emptySet()).size();

        response.append("**Possible multis for: **" + PnwUtil.getMarkdownUrl(nationId, false)).append("\n");

        Set<BigInteger> myUUIDS = uuidByNation.get(nationId);

        response.append("verified: ").append(Locutus.imp().getDiscordDB().isVerified(nationId)).append("\n");
        response.append("networks total: ").append(myUUIDS.size()).append('\n');

        for (Map.Entry<Integer, Map<BigInteger, Long>> entry : diffMap.entrySet()) {
            int nationId = entry.getKey();
            DBNation nation = Locutus.imp().getNationDB().getNation(nationId);

            String name;
            if (nation == null) name = nationId + "";
            else name = nation.getNationUrlMarkup(true) + " | " + nation.getAllianceUrlMarkup(true);

            response.append("**Possible multis for: **" + PnwUtil.getMarkdownUrl(nationId, false)).append("\n");

            Set<BigInteger> multiUUIDS = uuidByNation.get(nationId);
            Set<BigInteger> shared = new HashSet<>(multiUUIDS);
            shared.retainAll(myUUIDS);

            if (simple) {
                Map<BigInteger, Long> diff = entry.getValue();
                int concurrent = 0;
                for (Map.Entry<BigInteger, Long> timeDiff : diff.entrySet()) {
                    if (timeDiff.getValue() <= TimeUnit.DAYS.toMillis(1)) concurrent++;
                }
                response.append("shared networks: " + shared.size() + "/" + multiUUIDS.size())
                        .append(" ( " + concurrent + " same day)")
                        .append("\n");
            } else {
                response.append("shared networks: " + shared.size() + "/" + multiUUIDS.size()).append("\n");
                Map<BigInteger, Long> diff = entry.getValue();
                for (Map.Entry<BigInteger, Long> timeDiff : diff.entrySet()) {
                    String timeStr;
                    long uuidTimeDiff = timeDiff.getValue();
                    if (uuidTimeDiff == 0) timeStr = "concurrent";
                    else if (uuidTimeDiff >= Integer.MAX_VALUE || uuidTimeDiff < 0) timeStr = "unknown";
                    else timeStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, uuidTimeDiff);
                    response.append("- ").append(timeDiff.getKey()).append(": " + timeStr + "\n");
                }
            }

            Set<Map.Entry<DBWar, DBWar>> wars = illegalWarsByMulti.get(nationId);
            if (wars != null && !wars.isEmpty()) {
                List<String> simpleList = new ArrayList<>();
                for (Map.Entry<DBWar, DBWar> warEntry : wars) {
                    String key = warEntry.getKey().warId + "";
                    String value = warEntry.getValue().warId + "";
                    if (warEntry.getKey().attacker_id == this.nationId || warEntry.getKey().defender_id == this.nationId) {
                        key = "**" + key + "**";
                    }
                    if (warEntry.getValue().attacker_id == this.nationId || warEntry.getValue().defender_id == this.nationId) {
                        value = "**" + value + "**";
                    }
                    simpleList.add("(" + key + "," + value + ")");
                }
                response.append("shared wars: " + (simple ? "" : "\n"));
                response.append(StringMan.getString(simpleList)).append("\n");
                if (!simple) response.append('\n');
            }

            Set<DBTrade> trades = illegalTradesByMulti.get(nationId);
            if (trades != null && !trades.isEmpty()) {
                List<Integer> tradeIds = new ArrayList<>();
                double worth = 0;
                for (DBTrade offer : trades) {
                    int per = offer.getPpu();
                    ResourceType type = offer.getResource();
                    if (per <= 1 || (per > 10000 || (type == ResourceType.FOOD && per > 1000)) || type == ResourceType.CREDITS) {
                        tradeIds.add(offer.getTradeId());
                        worth += Math.abs(PnwUtil.convertedTotal(offer.getResource(), offer.getQuantity()));
                    }
                }
                if (!tradeIds.isEmpty()) {
                    Collections.sort(tradeIds);
                    response.append("\nshared trades:\n").append(StringMan.getString(tradeIds))
                            .append(" worth: $" + MathMan.format(worth))
                            .append("\n");
                    if (!simple) response.append('\n');
                }
            }

            Set<Transaction2> transfers = illegalTransfers.get(nationId);

            if (transfers != null && !transfers.isEmpty()) {
                if (simple) {
                    double worth = 0;
                    for (Transaction2 transfer : transfers) {
                        worth += transfer.convertedTotal();
                    }
                    response.append("shared transfers: " + transfers.size() + " worth: $" + MathMan.format(worth)).append("\n");
                } else {
                    response.append("shared transfers:\n- ").append(StringMan.join(transfers.stream().map(t -> t.toSimpleString()).collect(Collectors.toList()), "\n- ")).append("\n\n");
                }
            }

            Set<Map.Entry<Transaction2, Transaction2>> proxyTransfers = illegalIndirectTransfers.get(nationId);
            if (proxyTransfers != null && !proxyTransfers.isEmpty()) {
                if (simple) {
                    double worth = 0;
                    for (Map.Entry<Transaction2, Transaction2> pair : proxyTransfers) {
                        worth += pair.getValue().convertedTotal();
                    }
                    response.append("3rd party transfers: " + proxyTransfers.size() + " worth: $" + MathMan.format(worth)).append("\n");
                } else {
                    List<String> msg = proxyTransfers.stream().map(e -> e.getKey().toSimpleString() + " -> " + e.getValue().toSimpleString()).collect(Collectors.toList());
                    response.append("\n3rd party transfers:\n- ").append(StringMan.join(msg, "\n- ")).append("\n\n");
                }
            }
            {
                Long earnings = challengeEarnings.get(nationId);
                Integer numGames = challengeGames.get(nationId);

                if (earnings != null || numGames != null) {
                    response.append("BB Wagers:");
                    if (simple) {
                        if (numGames != null) response.append(" " + MathMan.format(numGames));
                        if (earnings != null) response.append(" net $" + MathMan.format(earnings));
                    } else {
                        response.append("\n");
                        if (earnings != null) response.append("- Net: $" + MathMan.format(earnings)).append("\n");
                        if (numGames != null)
                            response.append("- # Wagered Games: " + MathMan.format(numGames)).append("\n");
                    }
                    response.append("\n");
                }
            }
        }

        return response.toString();
    }

    public MultiReport(int nationId) {
        this.nationId = nationId;
        DBNation nation = Locutus.imp().getNationDB().getNation(nationId);
        try {
            if (nation != null) {
                new GetUid(nation).call();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Map<BigInteger, List<Map.Entry<Long, Long>>> uuidsByTime = Locutus.imp().getDiscordDB().getUuids(nationId);

        uuidByNation.put(nationId, uuidsByTime.keySet());

        Set<Integer> multis = new HashSet<>();
        for (BigInteger uuid : uuidsByTime.keySet()) {
            Set<Integer> currMultis = Locutus.imp().getDiscordDB().getMultis(uuid);
            multis.addAll(currMultis);
        }
        multis.remove(nationId);

        diffMap = new HashMap<>();

        for (Integer multi : multis) {
            Map<BigInteger, List<Map.Entry<Long, Long>>> multiUuids = Locutus.imp().getDiscordDB().getUuids(multi);

            uuidByNation.put(multi, multiUuids.keySet());

            Map<BigInteger, Long> myDiffMap = new LinkedHashMap<>();
            for (Map.Entry<BigInteger, List<Map.Entry<Long, Long>>> entry : uuidsByTime.entrySet()) {
                BigInteger uuid = entry.getKey();
                List<Map.Entry<Long, Long>> multiTime = multiUuids.get(uuid);
                if (multiTime == null) continue;
                List<Map.Entry<Long, Long>> myTime = entry.getValue();

                long diff = Long.MAX_VALUE;
                outer:
                for (Map.Entry<Long, Long> a : multiTime) {
                    for (Map.Entry<Long, Long> b : myTime) {
                        if ((a.getKey() == 0 && a.getValue() != Long.MAX_VALUE) || (b.getKey() == 0 && b.getValue() != Long.MAX_VALUE)) continue;
                        if (a.getKey() <= b.getValue() && a.getValue() >= b.getKey()) {
                            diff = 0;
                            break outer;
                        } else if (a.getValue() < b.getKey()) {
                            diff = Math.min(diff, Math.abs(a.getValue() - b.getKey()));
                        } else if (a.getKey() > b.getValue()) {
                            diff = Math.min(diff, Math.abs(a.getKey() - b.getValue()));
                        } else {
                            diff = Math.min(diff, Math.abs(a.getValue() - b.getKey()));
                            diff = Math.min(diff, Math.abs(a.getKey() - b.getValue()));
                        }
                    }
                }
                myDiffMap.put(uuid, diff);
            }
            if (!myDiffMap.isEmpty()) {
                diffMap.put(multi, myDiffMap);
            }
        }

        this.illegalWarsByMulti = new HashMap<>();
        {
            Map<Integer, List<DBWar>> wars = getDefenders(nationId);
            Set<Integer> sharedWars = new HashSet<>();

            for (int otherNationId : multis) {
                Map<Integer, List<DBWar>> otherWars = getDefenders(otherNationId);
                for (Map.Entry<Integer, List<DBWar>> entry : otherWars.entrySet()) {
                    if (wars.containsKey(entry.getKey())) {
                        sharedWars.add(entry.getKey());
                    }
                    wars.computeIfAbsent(entry.getKey(), i -> Lists.newArrayList()).addAll(entry.getValue());
                }
            }

            for (int defender : sharedWars) {
                List<DBWar> checkWars = wars.get(defender);
                for (int i = 0; i < checkWars.size(); i++) {
                    for (int j = i + 1; j < checkWars.size(); j++) {
                        DBWar a = checkWars.get(i);
                        DBWar b = checkWars.get(j);

                        int multiId1 = a.attacker_id == defender ? a.defender_id : a.attacker_id;
                        int multiId2 = b.attacker_id == defender ? b.defender_id : b.attacker_id;
                        if (multiId1 == multiId2) continue;
                        if (a.attacker_id != multiId1 && b.attacker_id != multiId2) continue;

                        if (TimeUnit.MILLISECONDS.toDays(Math.abs(a.date - b.date)) > 6) {
                            continue;
                        }

                        illegalWarsByMulti.computeIfAbsent(multiId1, f -> new LinkedHashSet<>()).add(new AbstractMap.SimpleEntry<>(a, b));
                        illegalWarsByMulti.computeIfAbsent(multiId2, f -> new LinkedHashSet<>()).add(new AbstractMap.SimpleEntry<>(b, a));
                    }
                }
            }
        }
        illegalWarsByMulti.remove(nationId);


        this.illegalTradesByMulti = new LinkedHashMap<>();
        List<DBTrade> myTrades = Locutus.imp().getTradeManager().getTradeDb().getTrades(nationId, 0);
        for (DBTrade offer : myTrades) {
            if ((offer.getSeller() == (nationId) || multis.contains(offer.getSeller())) && (offer.getBuyer() == (nationId) || multis.contains(offer.getBuyer()))) {
                int multiId = (offer.getSeller() == (nationId)) ? offer.getBuyer() : offer.getSeller();
                illegalTradesByMulti.computeIfAbsent(multiId, f -> new LinkedHashSet<>()).add(offer);
            }
        }

        {
          boolean sentBank = false;
            List<Transaction2> transfers = trimTransfers(Locutus.imp().getBankDB().getNationTransfers(nationId, 0));
            for (Transaction2 transfer : transfers) {
                if (transfer.banker_nation == nationId) {
                    if (multis.contains(transfer.getReceiver())) {
                        sentBank = true;
                        illegalTransfers.computeIfAbsent((int) transfer.getReceiver(), f -> new HashSet<>()).add(transfer);
                    }
                } else {
                    if (multis.contains(transfer.banker_nation)) {
                        sentBank = true;
                        illegalTransfers.computeIfAbsent(transfer.banker_nation, f -> new HashSet<>()).add(transfer);
                    }
                }
            }

            if (sentBank) {
//                body.append("**Illegal Bank transfers**:\n - " + StringMan.join(illegalTransfers, "\n - ")).append("\n\n");
            }

            Map<Long, List<Transaction2>> sharedTransfers = toShared(transfers);
            Set<Long> sharedKeys = new HashSet<>();
            for (int otherId : multis) {
                Map<Long, List<Transaction2>> multiTransfers = toShared(trimTransfers(Locutus.imp().getBankDB().getNationTransfers(otherId, 0)));
                for (Map.Entry<Long, List<Transaction2>> entry : multiTransfers.entrySet()) {
                    if (sharedTransfers.containsKey(entry.getKey())) {
                        sharedKeys.add(entry.getKey());
                    }
                    sharedTransfers.computeIfAbsent(entry.getKey(), i -> Lists.newArrayList()).addAll(entry.getValue());
                }
            }

//            Set<String> illegalThirdPartyTransfers = new HashSet<>();

            for (long key : sharedKeys) {
                List<Transaction2> list = sharedTransfers.get(key);
                for (int i = 0; i < list.size(); i++) {
                    for (int j = i + 1; j < list.size(); j++) {
                        Transaction2 a = list.get(i);
                        Transaction2 b = list.get(j);

                        if (a.receiver_type == b.receiver_type) continue;

                        long diff = Math.abs(a.getDate() - b.getDate());
                        if (TimeUnit.MILLISECONDS.toDays(diff) > 2) continue;

                        int nationA = (int) (a.receiver_type == 2 ? a.getSender() : a.getReceiver());
                        int nationB = (int) (b.receiver_type == 2 ? b.getSender() : b.getReceiver());

                        if (nationA == nationB) continue;

                        AbstractMap.SimpleEntry<Transaction2, Transaction2> entry = new AbstractMap.SimpleEntry<>(a, b);
                        illegalIndirectTransfers.computeIfAbsent(nationA, f -> new LinkedHashSet<>()).add(entry);
                        illegalIndirectTransfers.computeIfAbsent(nationB, f -> new LinkedHashSet<>()).add(entry);
                    }
                }
            }
        }

        {
            calculateBBGames();
        }
    }

    private void calculateBBGames() {
        List<BBGame> games = getChallengeGames(nationId);
        for (BBGame game : games) {
            challengeGames.merge(game.getHome_nation_id(), 1, Integer::sum);
            challengeGames.merge(game.getAway_nation_id(), 1, Integer::sum);
            if (game.getHome_score() > game.getAway_score()) {
                challengeEarnings.merge(game.getHome_nation_id(), game.getWager().longValue(), Long::sum);
                challengeEarnings.merge(game.getAway_nation_id(), -game.getWager().longValue(), Long::sum);
            } else if (game.getAway_score() > game.getHome_score()) {
                challengeEarnings.merge(game.getAway_nation_id(), game.getWager().longValue(), Long::sum);
                challengeEarnings.merge(game.getHome_nation_id(), -game.getWager().longValue(), Long::sum);
            }
        }
    }

    private List<Transaction2> trimTransfers(List<Transaction2> transfers) {
        transfers.removeIf(t -> t.note != null && t.note.toLowerCase().contains("of the alliance bank inventory"));
        return transfers;
    }

    /**
     * Maps a list of  wars to the opponent's nation id
     */
    private Map<Integer, List<DBWar>> getDefenders(int nationId) {
        List<DBWar> myWars = Locutus.imp().getWarDb().getWarsByNation(nationId);
        Map<Integer, List<DBWar>> defenders = new HashMap<>();
        for (DBWar war : myWars) {
            int otherId = war.defender_id == nationId ? war.attacker_id : war.defender_id;
            defenders.computeIfAbsent(otherId, i -> Lists.newArrayList()).add(war);
        }
        return defenders;
    }

    public Map<Long, List<Transaction2>> toShared(List<Transaction2> transfers) {
        HashMap<Long, List<Transaction2>> map = new HashMap<>();
        for (Transaction2 transfer : transfers) {
            long key = Arrays.hashCode(transfer.resources);
            map.computeIfAbsent(key, i -> Lists.newArrayList()).add(transfer);
        }
        return map;
    }

    private Map<Integer, List<Map.Entry<Integer, Transaction2>>> getWarTransfers(int nationId) {
        Map<Integer, List<Map.Entry<Integer, Transaction2>>> warTransfers = new HashMap<>();

        List<Transaction2> transfers = Locutus.imp().getBankDB().getNationTransfers(nationId, 0);
        for (Transaction2 transfer : transfers) {
            if (transfer.note == null) continue;

            String[] split = transfer.note.split(" defeated ");
            if (split.length != 2) continue;
            String name1 = split[0];
            String name2 = split[1].split("'s nation and captured")[0];
            try {
                DBNation nation1 = Locutus.imp().getNationDB().getNationByLeader(name1);
                DBNation nation2 = Locutus.imp().getNationDB().getNationByLeader(name2);
                if (nation1 == null || nation2 == null) {
                    continue;
                }
                int otherId = nation1.getNation_id() == nationId ? nation2.getNation_id() : nation1.getNation_id();
                warTransfers.computeIfAbsent(otherId, i -> Lists.newArrayList()).add(new AbstractMap.SimpleEntry<>(nationId, transfer));
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return warTransfers;
    }

    public List<BBGame> getChallengeGames(int nationid) {
        return Locutus.imp().getBaseballDB().getBaseballGames(new Consumer<SelectBuilder>() {
            @Override
            public void accept(SelectBuilder f) {
                f.where(QueryCondition.greater("wager", 0).and(QueryCondition.equals("home_nation_id", nationid).or(QueryCondition.equals("away_nation_id", nationid))));
            }
        });
    }

    public static long getTimeDiff(List<Map.Entry<Long, BigInteger>> mine, List<Map.Entry<Long, BigInteger>> other) {
        long end = Long.MAX_VALUE;
        for (int i = 0; i < mine.size(); i++) {
            Map.Entry<Long, BigInteger> myEntry = mine.get(i);
            long start = myEntry.getKey();
            BigInteger myUUID = myEntry.getValue();

            long otherEnd = Long.MAX_VALUE;
            for (int j = 0; j < other.size(); j++) {
                Map.Entry<Long, BigInteger> otherEntry = other.get(j);
                long otherStart = otherEntry.getKey();
                BigInteger otherUUID = otherEntry.getValue();

            }
        }
        return 0l;


    }
}
