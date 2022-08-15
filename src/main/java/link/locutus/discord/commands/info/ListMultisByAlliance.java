package link.locutus.discord.commands.info;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.rankings.builder.GroupedRankBuilder;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import com.google.api.client.util.Sets;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class ListMultisByAlliance extends Command {
    public ListMultisByAlliance() {
        super(CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.LOCUTUS_ADMIN);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        NationDB nationDB = Locutus.imp().getNationDB();

        String[] lines = FileUtil.readFile("/debug/nonreferrals.txt").split("\\r?\\n");
        Map<Integer, DBNation> referredNations = new HashMap<>(Locutus.imp().getNationDB().getNations());
        Map<String, DBNation> nationsByName = new HashMap<>();
        for (Map.Entry<Integer, DBNation> entry : referredNations.entrySet()) {
            nationsByName.putIfAbsent(entry.getValue().getNation(), entry.getValue());
        }
        int oldest = 0;
        for (String line : lines) {
            String[] columns = line.split("\t");
            DBNation other = nationsByName.get(columns[1]);
            if (other != null) {
                oldest = other.getNation_id();
                referredNations.remove(other.getNation_id());
            }
        }
        int finalOldest = oldest;
        referredNations.entrySet().removeIf(e -> e.getValue().getNation_id() > finalOldest);

        Map<BigInteger, Set<Integer>> multis = Locutus.imp().getDiscordDB().getUuidMap();
        Map<Integer, Set<Integer>> multisByNation = new HashMap<>();
        Set<Integer> verified = Locutus.imp().getDiscordDB().getVerified();

        for (Map.Entry<BigInteger, Set<Integer>> entry : multis.entrySet()) {
            for (Integer nationId : entry.getValue()) {
                if (verified.contains(nationId)) continue;

                Set<Integer> others = new HashSet<>(entry.getValue());
                others.remove(nationId);
                others.removeAll(verified);
                multisByNation.computeIfAbsent(nationId, t -> Sets.newHashSet()).addAll(others);
            }
        }

        Map<Integer, List<DBWar>> allWars = Locutus.imp().getWarDb().getWars(multisByNation.keySet(), Collections.emptySet());
        List<DBTrade> allOffers = Locutus.imp().getTradeManager().getTradeDb().getTrades(0);
        Map<Integer, List<DBTrade>> offersByNation = new RankBuilder<>(allOffers).group((BiConsumer<DBTrade, GroupedRankBuilder<Integer, DBTrade>>) (offer, builder) -> {
            builder.put(offer.getBuyer(), offer);
            builder.put(offer.getSeller(), offer);
        }).get();

        Map<Integer, List<Transaction2>> allTransfers = Locutus.imp().getBankDB().getNationTransfersByNation(0, multisByNation.keySet());

        Map<Integer, Collection<DBWar>> wars = new HashMap<>();
        Map<Integer, Collection<DBTrade>> trades = new HashMap<>();

        StringBuilder response = new StringBuilder();
        int i = 0;

        for (Map.Entry<Integer, Set<Integer>> entry : multisByNation.entrySet()) {
            Integer nationId = entry.getKey();
            DBNation nation = nationDB.getNation(nationId);
            if (nation == null || entry.getValue().isEmpty()) continue;
            if (verified.contains(nationId)) continue;

            boolean sharedWars = false;

            Set<Integer> defenders = getDefenders(nationId, allWars);
            for (int otherNationId : entry.getValue()) {
                Set<Integer> otherDefenders = getDefenders(otherNationId, allWars);
                if (otherDefenders.removeAll(defenders)) {
                    sharedWars = true;
                }
            }

            boolean sharedTrades = false;

            List<DBTrade> myTrades = offersByNation.getOrDefault(nationId, Collections.emptyList());
            for (DBTrade offer : myTrades) {
                sharedTrades |= (offer.getSeller() == (nationId) || entry.getValue().contains(offer.getSeller())) && (offer.getBuyer() == (nationId) || entry.getValue().contains(offer.getBuyer()));
            }

            boolean sentBank = false;
            List<Transaction2> transfers = allTransfers.getOrDefault(nationId, Collections.emptyList());
            for (Transaction2 transfer : transfers) {
                if (transfer.banker_nation == nationId) {
                    if (entry.getValue().contains(transfer.getReceiver())) {
                        sentBank = true;
                    }
                } else {
                    if (entry.getValue().contains(transfer.banker_nation)) {
                        sentBank = true;
                    }
                }
            }

            String nationUrl = url(nationId);

            String allianceName = nationDB.getAllianceName(nation.getAlliance_id());
            String allianceUrl = "=HYPERLINK(\"" + Settings.INSTANCE.PNW_URL() + "/alliance/id=%s\",\"%s\")";
            allianceUrl = String.format(allianceUrl, nation.getAlliance_id(), allianceName);

            String multiStr = StringMan.join(entry.getValue().stream().map(this::url).collect(Collectors.toList()), "\t");

            response.append(
                    nationUrl + "\t" +
                    allianceUrl + "\t" +
                    referredNations.containsKey(nationId) + "\t" +
                    sharedWars + "\t" +
                    sharedTrades + "\t" +
                    sentBank + "\t" +
                    (sentBank || sharedTrades || sharedWars) + "\t" +
                    nation.getAgeDays() + "\t" +
                    nation.getPosition() + "\t" +
                    multiStr + "\n"
            );
        }

        DiscordUtil.createEmbedCommand(event.getChannel(), "Potential multis:", response.toString());

        return null;
    }

    private Set<Integer> getDefenders(int nationId, Map<Integer, List<DBWar>> allWars) {
        List<DBWar> myWars = allWars.getOrDefault(nationId, Collections.emptyList());
        Set<Integer> defenders = new HashSet<>();
        for (DBWar war : myWars) {
            if (war.defender_id != nationId) {
                defenders.add(war.defender_id);
            }
        }
        return defenders;
    }

    private String url(int nationId) {
        String name = PnwUtil.getName(nationId, false);
        String nationUrl = "=HYPERLINK(\"" + Settings.INSTANCE.PNW_URL() + "/nation/id=%s\",\"%s\")";
        return String.format(nationUrl, nationId, name);
    }
}
