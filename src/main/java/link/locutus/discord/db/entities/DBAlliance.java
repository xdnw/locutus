package link.locutus.discord.db.entities;

import com.politicsandwar.graphql.model.ApiKeyDetails;
import com.politicsandwar.graphql.model.Bankrec;
import com.politicsandwar.graphql.model.Nation;
import com.politicsandwar.graphql.model.NationResponseProjection;
import com.politicsandwar.graphql.model.NationsQueryRequest;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv2.PoliticsAndWarV2;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.alliance.*;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.apiv1.domains.AllianceMembers;
import link.locutus.discord.apiv1.domains.subdomains.AllianceMembersContainer;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.task.deprecated.GetTaxesTask;
import link.locutus.discord.util.task.EditAllianceTask;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DBAlliance implements NationList, NationOrAlliance {
    private final int allianceId;
    private String name;
    private String acronym;
    private String flag;
    private String forum_link;
    private String discord_link;
    private String wiki_link;
    private long dateCreated;
    private NationColor color;
    private volatile long lastUpdated = 0;
    private OffshoreInstance bank;
    private LootEntry lootEntry;
    private boolean cachedLootEntry;
    private Int2ObjectOpenHashMap<byte[]> metaCache = null;

    public DBAlliance(com.politicsandwar.graphql.model.Alliance alliance) {
        this.allianceId = alliance.getId();
        this.acronym = "";
        this.flag = "";
        this.forum_link = "";
        this.discord_link = "";
        this.wiki_link = "";
        set(alliance, null);
    }

    public DBAlliance(DBAlliance other) {
        this(other.allianceId,
                other.name,
                other.acronym,
                other.flag,
                other.forum_link,
                other.discord_link,
                other.wiki_link,
                other.dateCreated,
                other.color,
                other.metaCache);
    }

    @Override
    public String getFilter() {
        return getTypePrefix() + ":" + allianceId;
    }

    @Override
    public boolean test(DBNation dbNation) {
        return dbNation.getAlliance_id() == allianceId;
    }

    public void setLoot(LootEntry lootEntry) {
        this.lootEntry = lootEntry;
        cachedLootEntry = true;
    }

    public LootEntry getLoot() {
        if (cachedLootEntry) {
            return lootEntry;
        }
        if (lootEntry == null) {
            lootEntry = Locutus.imp().getNationDB().getAllianceLoot(allianceId);
            cachedLootEntry = true;
        }
        return lootEntry;
    }

    public void setAAPage(String file) throws Exception{
        String input = FileUtil.readFile(file);

        input = input.replaceAll("\n", "");
        input = input.replaceAll("\r", "");
        input = input.replaceAll("\t", "");
        input = input.replaceAll("[ ][ ][ ]+", "");

        String finalInput = input;

        Auth auth = getAuth();
        System.out.println(auth.getNation() + " | " + auth.getAllianceId());
        String response = new EditAllianceTask(auth.getNation(), new Consumer<Map<String, String>>() {
            @Override
            public void accept(Map<String, String> stringStringMap) {
                stringStringMap.put("desc", finalInput);
            }
        }).call();
        System.out.println(response + " | response");
    }

    public static DBAlliance parse(String arg, boolean throwError) {
        Integer id = PnwUtil.parseAllianceId(arg);
        if (id == null) {
            if (throwError) throw new IllegalArgumentException("Invalid alliance id: `" + arg + "`");
            return null;
        }
        DBAlliance alliance = DBAlliance.get(id);
        if (alliance == null) {
            if (throwError) throw new IllegalArgumentException("No alliance found for id: `" + id + "`");
        }
        return alliance;
    }

    @Command
    public String getAcronym() {
        return acronym;
    }

    @Command
    public String getFlag() {
        return flag;
    }

    @Command
    public String getForum_link() {
        return forum_link;
    }

    @Command
    public String getDiscord_link() {
        return discord_link;
    }

    @Command
    public String getWiki_link() {
        return wiki_link;
    }

    @Command
    public long getDateCreated() {
        return dateCreated;
    }

    @Command
    public NationColor getColor() {
        return color;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public boolean set(com.politicsandwar.graphql.model.Alliance alliance, Consumer<Event> eventConsumer) {
        lastUpdated = System.currentTimeMillis();

        boolean dirty = false;

        DBAlliance copy = null;
        if (alliance.getDate() != null && this.dateCreated != alliance.getDate().toEpochMilli()) {
            dirty = true;
            // creation date should never change
            this.dateCreated = alliance.getDate().toEpochMilli();
        }
        if (alliance.getName() != null && !alliance.getName().equals(name)) {
            dirty = true;
            if (copy == null && eventConsumer != null) copy = new DBAlliance(this);
            this.name = alliance.getName();
            if (eventConsumer != null) eventConsumer.accept(new AllianceChangeNameEvent(copy, this));
        }
        if (alliance.getAcronym() != null && !alliance.getAcronym().equals(acronym)) {
            dirty = true;
            if (copy == null && eventConsumer != null) copy = new DBAlliance(this);
            this.acronym = alliance.getAcronym();
            if (eventConsumer != null) eventConsumer.accept(new AllianceChangeAcronymEvent(copy, this));
        }
        if (alliance.getColor() != null) {
            NationColor newColor = NationColor.valueOf(alliance.getColor().toUpperCase(Locale.ROOT));
            if (newColor != this.color) {
                dirty = true;
                if (copy == null && eventConsumer != null) copy = new DBAlliance(this);
                this.color = newColor;
                if (eventConsumer != null) eventConsumer.accept(new AllianceChangeColorEvent(copy, this));
            }
        }
        if (alliance.getFlag() != null && !alliance.getFlag().equals(flag)) {
            dirty = true;
            if (copy == null && eventConsumer != null) copy = new DBAlliance(this);
            this.flag = alliance.getFlag();
            if (eventConsumer != null) eventConsumer.accept(new AllianceChangeFlagEvent(copy, this));
        }
        if (alliance.getForum_link() != null && !alliance.getForum_link().equals(forum_link)) {
            dirty = true;
            if (copy == null && eventConsumer != null) copy = new DBAlliance(this);
            this.forum_link = alliance.getForum_link();
            if (eventConsumer != null) eventConsumer.accept(new AllianceChangeForumLinkEvent(copy, this));
        }
        if (alliance.getDiscord_link() != null && !alliance.getDiscord_link().equals(discord_link)) {
            dirty = true;
            if (copy == null && eventConsumer != null) copy = new DBAlliance(this);
            this.discord_link = alliance.getDiscord_link();
            if (eventConsumer != null) eventConsumer.accept(new AllianceChangeDiscordLinkEvent(copy, this));
        }
        if (alliance.getWiki_link() != null && !alliance.getWiki_link().equals(wiki_link)) {
            dirty = true;
            if (copy == null && eventConsumer != null) copy = new DBAlliance(this);
            this.wiki_link = alliance.getWiki_link();
            if (eventConsumer != null) eventConsumer.accept(new AllianceChangeWikiLinkEvent(copy, this));
        }
        return dirty;
    }

    public static DBAlliance get(int aaId) {
        return Locutus.imp().getNationDB().getAlliance(aaId);
    }

    public Auth getAuth(AlliancePermission... permissions) {
        Set<AlliancePermission> permsSet = new HashSet<>(Arrays.asList(permissions));
        GuildDB db = getGuildDB();

        int preferNation = -1;
        if (db != null) {
            List<String> apiKeys = db.getOrNull(GuildKey.API_KEY);
            if (apiKeys != null) {
                for (String apiKey : apiKeys) {
                    // get nation from key
                    Integer nationId = Locutus.imp().getDiscordDB().getNationFromApiKey(apiKey);
                    if (nationId == null) continue;
                    DBNation gov = DBNation.getById(nationId);
                    if (gov == null || gov.getVm_turns() > 0) continue;
                    if (!gov.hasAllPermission(permsSet)) continue;
                    Auth auth = gov.getAuth(false);
                    if (auth != null && auth.isValid()) return auth;
                }
            }
        }

        Set<DBNation> nations = getNations();
        for (DBNation gov : nations) {
            if (gov.getVm_turns() > 0 || gov.getPositionEnum().id <= Rank.APPLICANT.id) continue;
            if (!gov.hasAllPermission(permsSet)) continue;
            Auth auth = gov.getAuth(false);
            if (auth != null && auth.getAllianceId() == allianceId && auth.isValid()) {
                return auth;
            }
        }
        return null;
    }

    public static DBAlliance getOrCreate(int aaId) {
        if (aaId == 0) return new DBAlliance(0, "None", "", "", "", "", "", 0, NationColor.GRAY, null);
        return Locutus.imp().getNationDB().getOrCreateAlliance(aaId);
    }

    public DBAlliance(int allianceId, String name, String acronym, String flag, String forum_link, String discord_link, String wiki_link, long dateCreated, NationColor color, Int2ObjectOpenHashMap<byte[]> metaCache) {
        this.allianceId = allianceId;
        this.dateCreated = dateCreated;
        this.name = name;
        this.acronym = acronym;
        this.color = color;
        this.flag = flag;
        this.forum_link = forum_link;
        this.discord_link = discord_link;
        this.wiki_link = wiki_link;
        this.metaCache = metaCache;
    }

    @Command(desc = "Number of offensive and defensive wars since date")
    public int getNumWarsSince(long date) {
        return Locutus.imp().getWarDb().countWarsByAlliance(allianceId, date);
    }

    @Command
    public double exponentialCityStrength() {
        double total = 0;
        for (DBNation nation : getNations(true, 10000, true)) {
            total += Math.pow(nation.getCities(), 3) / 3d;
        }
        return total;
    }

    public static Set<DBAlliance> getTopX(int topX, boolean checkTreaty) {
        Set<DBAlliance> results = new LinkedHashSet<>();
        Map<Integer, Double> aas = new RankBuilder<>(Locutus.imp().getNationDB().getNations().values()).group(DBNation::getAlliance_id).sumValues(DBNation::getScore).sort().get();
        for (Map.Entry<Integer, Double> entry : aas.entrySet()) {
            if (entry.getKey() == 0) continue;
            if (topX-- <= 0) break;
            int allianceId = entry.getKey();
            results.add(DBAlliance.getOrCreate(allianceId));
            if (checkTreaty) {
                Map<Integer, Treaty> treaties = Locutus.imp().getNationDB().getTreaties(allianceId);
                for (Map.Entry<Integer, Treaty> aaTreatyEntry : treaties.entrySet()) {
                    switch (aaTreatyEntry.getValue().getType()) {
                        case MDP:
                        case MDOAP:
                        case PROTECTORATE:
                            results.add(DBAlliance.getOrCreate(aaTreatyEntry.getKey()));
                    }
                }
            }
        }
        return results;
    }

    public Set<Integer> listUsedTaxIds() {
        Set<Integer> taxIds = getNations().stream().map(DBNation::getTax_id).collect(Collectors.toSet());
        taxIds.remove(0);
        return taxIds;
    }

    private Map<Integer, TaxBracket> BRACKETS_CACHED;
    private long BRACKETS_TURN_UPDATED;

    public synchronized Map<Integer, TaxBracket> getTaxBrackets(boolean useCache) {
        if (useCache && BRACKETS_TURN_UPDATED == TimeUtil.getTurn()) {
            boolean isOutdated = false;
            for (int id : listUsedTaxIds()) {
                if (!BRACKETS_CACHED.containsKey(id)) {
                    isOutdated = true;
                    break;
                }
            }
            if (!isOutdated) return BRACKETS_CACHED;
        }
        PoliticsAndWarV3 api = getApi(AlliancePermission.TAX_BRACKETS);
        if (api == null) {
            Map<Integer, TaxBracket> result = new HashMap<>();
            for (DBNation nation : getNations()) {
                if (nation.getTax_id() == 0 || result.containsKey(nation.getTax_id())) continue;
                TaxBracket bracket = nation.getTaxBracket();
                if (bracket == null) continue;
                result.put(nation.getTax_id(), bracket);
            }
            return result;
        }

        Map<Integer, com.politicsandwar.graphql.model.TaxBracket> bracketsV3 = api.fetchTaxBrackets(allianceId);
        BRACKETS_CACHED = new ConcurrentHashMap<>();
        BRACKETS_TURN_UPDATED = TimeUtil.getTurn();
        for (Map.Entry<Integer, com.politicsandwar.graphql.model.TaxBracket> entry : bracketsV3.entrySet()) {
            TaxBracket bracket = new TaxBracket(entry.getValue());
            Locutus.imp().getBankDB().addTaxBracket(bracket);
            BRACKETS_CACHED.put(bracket.taxId, bracket);
        }
        // update nations not matching a valid tax bracket
        List<Integer> toUpdate = new ArrayList<>();
        for (DBNation nation : getNations()) {
            if (nation.getTax_id() != 0 && ! bracketsV3.containsKey(nation.getTax_id())) {
                toUpdate.add(nation.getId());
            }
        }
        if (!toUpdate.isEmpty()) {
            Locutus.imp().runEventsAsync(f -> Locutus.imp().getNationDB().updateNations(toUpdate, f));
        }


        return Collections.unmodifiableMap(BRACKETS_CACHED);
    }

    public String getMarkdownUrl() {
        return PnwUtil.getMarkdownUrl(allianceId, true);
    }

    @Override
    @Command
    public int getId() {
        return allianceId;
    }

    @Override
    public boolean isAlliance() {
        return true;
    }

    @Command
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }

    public Set<DBNation> getNations(boolean removeVM, int removeInactiveM, boolean removeApps) {
        Set<DBNation> nations = getNations();
        if (removeVM) nations.removeIf(f -> f.getVm_turns() != 0);
        if (removeInactiveM > 0) nations.removeIf(f -> f.getActive_m() > removeInactiveM);
        if (removeApps) nations.removeIf(f -> f.getPosition() <= 1);
        return nations;
    }

    public Set<DBNation> getNations() {
        return Locutus.imp().getNationDB().getNations(Collections.singleton(allianceId));
    }

    public DBNation getMembersTotal() {
        DBNation result = DBNation.createFromList(getName(), getNations(true, 0, true), false);
        result.setAlliance_id(allianceId);
        return result;
    }

    public DBNation getMembersAverage() {
        DBNation result = DBNation.createFromList(getName(), getNations(true, 0, true), true);
        result.setAlliance_id(allianceId);
        return result;
    }

    public boolean updateSpies(AllianceMembers members) {
//        Set<DBNation> toUpdate = new LinkedHashSet<>();
//        for (AllianceMembersContainer member : members.getNations()) {
//            Integer spies = Integer.parseInt(member.getSpies());
//            DBNation nation = Locutus.imp().getNationDB().getNation(member.getNationId());
//            if (nation != null && !spies.equals(nation.getSpies())) {
//                nation.setSpies(spies, true);
//                Locutus.imp().getNationDB().setSpies(nation.getNation_id(), spies);
//                toUpdate.add(nation);
//            }
//        }
//        Locutus.imp().getNationDB().saveNations(toUpdate);
        return true;
    }

    public Set<Integer> updateSpies(boolean updateManually) {
//        PoliticsAndWarV3 api = getApi(AlliancePermission.SEE_SPIES);
//        Set<Integer> updated = new HashSet<>();
//        if (api != null) {
//            List<Nation> nations = api.fetchNations(f -> {
//                f.setAlliance_id(List.of(allianceId));
//                f.setVmode(false);
//            }, f -> {
//                f.id();
//                f.spies();
//            });
//            Set<DBNation> toSave = new HashSet<>();
//            for (Nation nation : nations) {
//                Integer spies = nation.getSpies();
//                if (spies != null) {
//                    updated.add(nation.getId());
//                    DBNation locutusNation = DBNation.getById(nation.getId());
//                    if (locutusNation != null) {
//                        locutusNation.setSpies(spies, true);
//                        toSave.add(locutusNation);
//                    }
//                }
//            }
//            Locutus.imp().getNationDB().saveNations(toSave);
//            return updated;
//        }
//        if (!updateManually) return updated;
//        for (DBNation nation : getNations(true, 1440, true)) {
//            nation.updateSpies(PagePriority.ESPIONAGE_ODDS_BULK);
//            updated.add(nation.getId());
//        }
//        return updated;
        return getNations().stream().map(f -> f.getNation_id()).collect(Collectors.toSet());
    }

    public DBNation getTotal() {
        DBNation result = DBNation.createFromList(getName(), getNations(), false);
        result.setAlliance_id(allianceId);
        return result;
    }

    @Command
    public Set<DBAlliance> getTreatiedAllies() {
        return getTreatiedAllies(true);
    }
    public Set<DBAlliance> getTreatiedAllies(boolean checkOffshore) {
        Set<DBAlliance> allies = new HashSet<>();
        for (Map.Entry<Integer, Treaty> treatyEntry : getDefenseTreaties().entrySet()) {
            Treaty treaty = treatyEntry.getValue();
            int other = treaty.getFromId() == allianceId ? treaty.getToId() : treaty.getFromId();
            allies.add(DBAlliance.getOrCreate(other));
        }
        if (checkOffshore) {
            DBAlliance parent = getCachedParentOfThisOffshore();
            if (parent != null) {
                allies.add(parent);
                allies.addAll(parent.getTreatiedAllies(false));
            }
        }
        return allies;
    }

    public Map<Integer, Treaty> getDefenseTreaties() {
        HashMap<Integer, Treaty> defTreaties = new HashMap<>(getTreaties());
        defTreaties.entrySet().removeIf(f -> f.getValue().getType() == TreatyType.NAP || f.getValue().getType() == TreatyType.PIAT);
        return defTreaties;
    }

    public Map<Integer, Treaty> getTreaties() {
        return getTreaties(false);
    }
    public Map<Integer, Treaty> getTreaties(boolean update) {
        if (update) {
            PoliticsAndWarV3 api = getApi(AlliancePermission.MANAGE_TREATIES);
            if (api != null) {
                List<com.politicsandwar.graphql.model.Treaty> treaties = api.fetchTreaties(allianceId);
                Locutus.imp().getNationDB().updateTreaties(treaties, Event::post, f -> {
                    return f.getFromId() == allianceId || f.getToId() == allianceId;
                });
                Map<Integer, Treaty> result = new HashMap<>();
                for (com.politicsandwar.graphql.model.Treaty v3 : treaties) {
                    Treaty treaty = new Treaty(v3);
                    result.put(treaty.getFromId() == allianceId ? treaty.getToId() : treaty.getFromId(), treaty);
                }
                return result;
            }
        }
        return Locutus.imp().getNationDB().getTreaties(allianceId);
    }

    public Set<DBAlliance> getSphere() {
        return getSphereCached(new HashMap<>());
    }

    public Set<DBAlliance> getSphereCached(Map<Integer, DBAlliance> aaCache) {
        Set<DBAlliance> result = getTreaties(this, new HashMap<>(), aaCache);

        for (DBAlliance alliance : Locutus.imp().getNationDB().getAlliances()) {
            DBAlliance parent = alliance.getCachedParentOfThisOffshore();
            if (parent != null && (result.contains(parent) || parent == this)) {
                result.add(alliance);
            }
        }
        return result;
    }

    public List<DBAlliance> getSphereRankedCached(Map<Integer, DBAlliance> aaCache) {
        ArrayList<DBAlliance> list = new ArrayList<>(getSphereCached(aaCache));
        Collections.sort(list, (o1, o2) -> Double.compare(o2.getScore(), o1.getScore()));
        return list;
    }

    public List<DBAlliance> getSphereRanked() {
        return getSphereRankedCached( new HashMap<>());
    }

    double scoreCached = -1;

    @Override
    @Command
    public double getScore() {
        if (scoreCached == -1) {
            scoreCached = new SimpleNationList(getNations(true, 0, true)).getScore();
        }
        return scoreCached;
    }

    private Integer rank;

    @Command(desc = "Rank by score")
    public int getRank() {
        if (rank == null) {
            Map<Integer, List<DBNation>> byScore = Locutus.imp().getNationDB().getNationsByAlliance(false, false, true, true);
            rank = 0;
            for (Map.Entry<Integer, List<DBNation>> entry : byScore.entrySet()) {
                rank++;
                if (entry.getKey() == allianceId) return rank;
            }
            return rank = Integer.MAX_VALUE;
        }
        return rank;
    }

    @Command
    public int getAlliance_id() {
        return allianceId;
    }

    private static Set<DBAlliance> getTreaties(DBAlliance currentAA, Map<DBAlliance, Double> currentWeb, Map<Integer, DBAlliance> aaCache) {
        if (!currentWeb.containsKey(currentAA)) currentWeb.put(currentAA, currentAA.getScore());
        aaCache.put(currentAA.allianceId, currentAA);

        Map<Integer, Treaty> treaties = currentAA.getTreaties();

        DBAlliance protector = null;
        double protectorScore = 0;
        for (Map.Entry<Integer, Treaty> entry : treaties.entrySet()) {
            int otherId = entry.getKey();
            DBAlliance otherAA = aaCache.computeIfAbsent(otherId, f -> DBAlliance.getOrCreate(otherId));
            if (currentWeb.containsKey(otherAA)) continue;
            if (otherAA.getAlliance_id() == 4150 || currentAA.allianceId == 4150) continue;
//            if (otherAA.getAlliance_id() == 770 || currentAA.allianceId == 770) continue;
            switch (entry.getValue().getType()) {
                case NAP:
                    if (otherAA.getAlliance_id() != 3339 && currentAA.getAlliance_id() != 3339) continue;
                case MDP:
                    if (otherAA.getAlliance_id() == 3669 || currentAA.getAlliance_id() == 3669) continue;
                case MDOAP:
                    if (protector != null) continue;
                    getTreaties(otherAA, currentWeb, aaCache);
                    continue;
                case PROTECTORATE:
                    double score = otherAA.getScore();
                    double currentScore = 0;
                    for (double value : currentWeb.values()) currentScore = Math.max(currentScore, value);
                    if (score > currentScore && score > protectorScore) {
                        protectorScore = score;
                        protector = otherAA;
                    }
            }
        }
        if (protector != null) {
            return getTreaties(protector, currentWeb, aaCache);
        }

        return new HashSet<>(currentWeb.keySet());
    }

    public DBNation getAverage() {
        DBNation result = DBNation.createFromList(getName(), getNations(), true);
        result.setAlliance_id(allianceId);
        return result;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) {
            if (o instanceof Number) {
                return ((Number) o).intValue() == allianceId;
            }
            return false;
        }

        DBAlliance alliance = (DBAlliance) o;

        return allianceId == alliance.allianceId;
    }

    @Override
    public int hashCode() {
        return allianceId;
    }

    public String getUrl() {
        return "" + Settings.INSTANCE.PNW_URL() + "/alliance/id=" + getAlliance_id();
    }

    public boolean exists() {
        return Locutus.imp().getNationDB().getAlliance(allianceId) != null;
    }

//    public boolean addBalance(GuildDB db, Map<ResourceType, Double> transfer, String note) {
//        synchronized (db) {
//            Map<ResourceType, Map<String, Double>> offset = db.getDepositOffset(-allianceId);
//
//            boolean result = false;
//            for (Map.Entry<ResourceType, Double> entry : transfer.entrySet()) {
//                ResourceType rss = entry.getKey();
//                Double amt = entry.getValue();
//                if (amt == 0) continue;
//
//                double currentAmt = offset.getOrDefault(rss, new HashMap<>()).getOrDefault(note, 0d);
//                double newAmount = amt + currentAmt;
//
//                db.setDepositOffset(-allianceId, rss, newAmount, note);
//
//                result = true;
//            }
//
//            return result;
//        }
//    }

    public GuildDB getGuildDB() {
        return Locutus.imp().getGuildDBByAA(allianceId);
    }

    public List<Map.Entry<Long, Map.Entry<Integer, Rank>>> getRankChanges() {
        return Locutus.imp().getNationDB().getRankChanges(allianceId);
    }

    public Map<Integer, Map.Entry<Long, Rank>> getRemoves() {
        return Locutus.imp().getNationDB().getRemovesByAlliance(allianceId);
    }

    public List<DBWar> getActiveWars() {
        return Locutus.imp().getWarDb().getActiveWars(Collections.singleton(allianceId), WarStatus.ACTIVE);
    }

    public void deleteMeta(AllianceMeta key) {
        if (metaCache != null && metaCache.remove(key.ordinal()) != null) {
            Locutus.imp().getNationDB().deleteMeta(-allianceId, key.ordinal());
        }
    }

    public boolean setMetaRaw(int id, byte[] value) {
        if (metaCache == null) {
            synchronized (this) {
                if (metaCache == null) {
                    metaCache = new Int2ObjectOpenHashMap<>();
                }
            }
        }
        byte[] existing = metaCache.isEmpty() ? null : metaCache.get(id);
        if (existing == null || !Arrays.equals(existing, value)) {
            metaCache.put(id, value);
            return true;
        }
        return false;
    }

    public void setMeta(AllianceMeta key, byte... value) {
        if (setMetaRaw(key.ordinal(), value)) {
            Locutus.imp().getNationDB().setMeta(-allianceId, key.ordinal(), value);
        }
    }

    public ByteBuffer getMeta(AllianceMeta key) {
        if (metaCache == null) {
            return null;
        }
        byte[] result = metaCache.get(key.ordinal());
        return result == null ? null : ByteBuffer.wrap(result);
    }

    public void setMeta(AllianceMeta key, byte value) {
        setMeta(key, new byte[] {value});
    }

    public void setMeta(AllianceMeta key, int value) {
        setMeta(key, ByteBuffer.allocate(4).putInt(value).array());
    }

    public void setMeta(AllianceMeta key, long value) {
        setMeta(key, ByteBuffer.allocate(8).putLong(value).array());
    }

    public void setMeta(AllianceMeta key, double value) {
        setMeta(key, ByteBuffer.allocate(8).putDouble(value).array());
    }

    public void setMeta(AllianceMeta key, String value) {
        setMeta(key, value.getBytes(StandardCharsets.ISO_8859_1));
    }

    public ApiKeyPool getApiKeys(AlliancePermission... permissions) {
        GuildDB db = getGuildDB();
        if (db != null) {
            List<String> apiKeys = db.getOrNull(GuildKey.API_KEY);

            if (apiKeys != null && !apiKeys.isEmpty()) {
                List<String> newKeys = new ArrayList<>(apiKeys);
                for (String key : apiKeys) {
                    try {
                        Integer nationId = Locutus.imp().getDiscordDB().getNationFromApiKey(key);
                        if (nationId == null) {
                            ApiKeyDetails stats = new PoliticsAndWarV3(ApiKeyPool.builder().addKeyUnsafe(key).build()).getApiKeyStats();
                            Locutus.imp().getDiscordDB().addApiKey(stats.getNation().getId(), key);
                        }
//                    deleteInfo(Key.API_KEY);
                    } catch (HttpClientErrorException.Unauthorized e) {
                        newKeys.remove(key);
                        if (newKeys.isEmpty()) {
                            db.deleteInfo(GuildKey.API_KEY);
                        } else {
                            GuildKey.API_KEY.set(db, newKeys);
                        }
                    } catch (Throwable e) {
                        throw e;
                    }
                }
            }
        }

        ApiKeyPool.SimpleBuilder builder = new ApiKeyPool.SimpleBuilder();

        Set<DBNation> nations = getNations();
        for (DBNation gov : nations) {
            if (gov.getVm_turns() > 0 || gov.getPositionEnum().id <= Rank.APPLICANT.id || gov.getAlliance_id() != allianceId) continue;
            if (gov.getPositionEnum() != Rank.LEADER && gov.getPositionEnum() != Rank.HEIR) {
                DBAlliancePosition position = gov.getAlliancePosition();
                if (permissions != null && permissions.length > 0 && (position == null || (!position.hasAllPermission(permissions)))) {
                    continue;
                }
            }
            try {
                ApiKeyPool.ApiKey key = gov.getApiKey(false);
                if (key == null) continue;
                builder.addKey(key);
            } catch (IllegalArgumentException ignore) {
                ignore.printStackTrace();
            }
        }
        if (!builder.isEmpty()) {
            return builder.build();
        }
        return null;
    }

    public Map<DBNation, Map<ResourceType, Double>> getMemberStockpile() throws IOException {
        return getMemberStockpile(f -> true);
    }

    public Map<DBNation, Map<ResourceType, Double>> getMemberStockpile(Predicate<DBNation> fetchNations) throws IOException {
        PoliticsAndWarV3 api = getApiOrThrow(AlliancePermission.SEE_SPIES);
        List<Integer> ids = getNations().stream()
                .filter(f -> f.getVm_turns() == 0 && f.getPositionEnum().id > Rank.APPLICANT.id && fetchNations.test(f))
                .map(f -> f.getNation_id()).collect(Collectors.toList());
        ids.sort(Comparator.comparingInt(a -> a));
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        Map<Integer, double[]> stockPile = api.getStockPile(f -> f.setId(ids));
        Map<DBNation, Map<ResourceType, Double>> result = new HashMap<>();
        for (Map.Entry<Integer, double[]> entry : stockPile.entrySet()) {
            DBNation nation = DBNation.getById(entry.getKey());
            if (nation == null) continue;
            result.put(nation, PnwUtil.resourcesToMap(entry.getValue()));
        }
        return result;
    }

    public PoliticsAndWarV3 getApiOrThrow(boolean preferKeyStore, AlliancePermission... permissions) {
        if (preferKeyStore) {
            GuildDB db = getGuildDB();
            if (db != null) {
                ApiKeyPool key = db.getApiKey(allianceId, permissions);
                if (key != null) return new PoliticsAndWarV3(key);
            }
        }
        return getApiOrThrow(permissions);
    }

    public PoliticsAndWarV3 getApiOrThrow(AlliancePermission... permissions) {
        PoliticsAndWarV3 api = getApi( permissions);
        if (api == null) {
            String msg = "No api key found for " + getQualifiedName() + ". Please use" + CM.credentials.addApiKey.cmd.toSlashMention() + "\n" +
                    "Api key can be found on <https://politicsandwar.com/account/>";
            if (permissions.length > 0) msg += " and ensure your in-game position grants: " + StringMan.getString(permissions);
            throw new IllegalArgumentException(msg);
        }
        return api;
    }

    public PoliticsAndWarV2 getApiV2(AlliancePermission... permissions) {
        ApiKeyPool pool = getApiKeys(permissions);
        if (pool == null) return null;
        return new PoliticsAndWarV2(pool, Settings.INSTANCE.TEST, true);
    }
    public PoliticsAndWarV3 getApi(AlliancePermission... permissions) {
        ApiKeyPool pool = getApiKeys(permissions);
        if (pool == null) return null;
        return new PoliticsAndWarV3(pool);
    }

    public Map<ResourceType, Double> getStockpile() {
        PoliticsAndWarV3 api = getApiOrThrow(AlliancePermission.VIEW_BANK);
        double[] stockpile = api.getAllianceStockpile(allianceId);
        return stockpile == null ? null : PnwUtil.resourcesToMap(stockpile);
    }

    public void updateCities() throws IOException, ParseException {
        updateCities(f -> true);
    }

    public void updateCities(Predicate<DBNation> fetchNation) throws IOException, ParseException {
        Set<Integer> nationIds = getNations(false, 0, true).stream().filter(fetchNation).map(DBNation::getId).collect(Collectors.toSet());
        if (nationIds.isEmpty()) return;
        Locutus.imp().getNationDB().updateCitiesOfNations(nationIds, true, true, Event::post);
    }

    public DBAlliance getCachedParentOfThisOffshore() {
        if (!isRightSizeForOffshore(getNations())) {
            deleteMeta(AllianceMeta.OFFSHORE_PARENT);
            return null;
        }
        ByteBuffer parentIdBuffer = getMeta(AllianceMeta.OFFSHORE_PARENT);
        if (parentIdBuffer != null) {
            DBAlliance parent = DBAlliance.get(parentIdBuffer.getInt());
            if (parent != null && parent.getNations().size() > 3) {
                return parent;
            }
        }
        return null;
    }

    public boolean isRightSizeForOffshore(Set<DBNation> members) {
        if (members.size() > 3) {
            return false;
        }
        if (members.size() == 0) {
            return false;
        }
        int maxCities = 0;
        int maxAge = 0;
        int activeMembers = 0;
        int numVM = 0;

        for (DBNation member : members) {
            if (member.getVm_turns() > 0) numVM++;
            if (member.getVm_turns() == 0 && member.getActive_m() > 10000) {
                return false;
            }
            if (member.getVm_turns() == 0) {
                activeMembers++;
                maxCities = Math.max(maxCities, member.getCities());
                maxAge = Math.max(maxAge, member.getAgeDays());
            }
        }
        if (numVM >= 3) {
            return false;
        }
        if (activeMembers == 0) {
            return false;
        }
        return true;
    }

    /**
     * If this alliance is an offshore
     * @return parent alliance if this is an offshore
     */
    public DBAlliance findParentOfThisOffshore() {
        Set<DBNation> members = getNations();
        if (!isRightSizeForOffshore(members)) {
            deleteMeta(AllianceMeta.OFFSHORE_PARENT);
            return null;
        }
        return findParentOfThisOffshore(members, false);
    }

    public DBAlliance findParentOfThisOffshore(Set<DBNation> membersOrNull, boolean checkSize) {
        Set<DBNation> members = membersOrNull == null ? getNations() : membersOrNull;
        if (checkSize && !isRightSizeForOffshore(members)) {
            deleteMeta(AllianceMeta.OFFSHORE_PARENT);
            return null;
        }

        for (DBWar war : Locutus.imp().getWarDb().getWarsByAlliance(getAlliance_id())) {

            List<AbstractCursor> attacks = Locutus.imp().getWarDb().getAttacksByWarId(war);
            attacks.removeIf(f -> f.getAttack_type() != AttackType.A_LOOT);
            if (attacks.size() != 1) continue;

            AbstractCursor attack = attacks.get(0);
            int attAA = war.isAttacker(attack.getAttacker_id()) ? war.attacker_aa : war.defender_aa;
            if (attAA == getAlliance_id()) continue;
            boolean lowMil = false;
            for (DBNation member : members) {
                if (member.getVm_turns() != 0) continue;
                if (member.getActive_m() > 7200) {
                    return null;
                }
                if (member.isGray()) {
                    deleteMeta(AllianceMeta.OFFSHORE_PARENT);
                    return null;
                }
                if (member.getCities() > 1 && member.getAircraftPct() < 0.8) {
                    lowMil = true;
                }
            }
            if (lowMil) {
                for (DBNation member : members) {
                    if (member.daysSinceLastOffensive() < 30) lowMil = false;
                }
                if (lowMil) {
                    deleteMeta(AllianceMeta.OFFSHORE_PARENT);
                    return null;
                }
            }
        }

        double thisScore = this.getScore();

        DBAlliance maxAlly = null;
        for (Map.Entry<Integer, Treaty> treaty : getDefenseTreaties().entrySet()) {
            DBAlliance other = DBAlliance.get(treaty.getKey());
            if (other == null) continue;
            if (maxAlly == null || other.getScore() > maxAlly.getScore()) maxAlly = other;
        }
        if (maxAlly != null && maxAlly.getNations().size() > 3) {
            setMeta(AllianceMeta.OFFSHORE_PARENT, maxAlly.getAlliance_id());
            return maxAlly;
        }

        for (DBNation member : members) {
            Map.Entry<Integer, Rank> lastAAInfo = member.getPreviousAlliance();
            if (lastAAInfo == null) continue;
            int aaId = lastAAInfo.getKey();

            if (lastAAInfo.getValue().id >= Rank.OFFICER.id) {
                DBAlliance lastAA = DBAlliance.get(aaId);
                if (lastAA != null && lastAA.getNations().size() > 3) {
                    if (lastAA == maxAlly) continue;
                    double lastAAScore = lastAA.getScore();
                    if (lastAAScore > thisScore * 2 && (maxAlly == null || lastAAScore > maxAlly.getScore())) {
                        maxAlly = lastAA;
                        continue;
                    }
                }
                if (lastAA == null) {
                    lastAA = DBAlliance.getOrCreate(aaId);
                } else if (lastAA.getNations().size() >= 3) {
                    continue;
                }
                ByteBuffer offshore = lastAA.getMeta(AllianceMeta.OFFSHORE_PARENT);
                if (offshore != null) {
                    DBAlliance parent = DBAlliance.get(offshore.getInt());
                    if (parent == maxAlly) continue;
                    if (parent != null && parent.getNations().size() > 3 && (maxAlly == null || parent.getScore() > maxAlly.getScore())) {
                        maxAlly = parent;
                    }
                }
            }
        }
        if (maxAlly != null) {
            setMeta(AllianceMeta.OFFSHORE_PARENT, maxAlly.getAlliance_id());
            return maxAlly;
        }

        ByteBuffer parentIdBuffer = getMeta(AllianceMeta.OFFSHORE_PARENT);
        if (parentIdBuffer != null) {
            DBAlliance parent = DBAlliance.get(parentIdBuffer.getInt());
            if (parent != null && parent.getNations().size() > 3) {
                return parent;
            }
        }

        deleteMeta(AllianceMeta.OFFSHORE_PARENT);
        return null;
    }

    public Set<DBAlliancePosition> getPositions() {
        return new HashSet<>(Locutus.imp().getNationDB().getPositions(allianceId));
    }

    public List<BankDB.TaxDeposit> updateTaxes() {
        return updateTaxes(null);
    }
    public List<BankDB.TaxDeposit> updateTaxes(Long afterDate) {
        long oldestApiFetchDate = getDateCreated() - TimeUnit.HOURS.toMillis(2);

        GuildDB db = Locutus.imp().getGuildDBByAA(allianceId);

        PoliticsAndWarV3 api = getApi( AlliancePermission.TAX_BRACKETS);
        if (api == null) {
            return null;
        }

        BankDB bankDb = Locutus.imp().getBankDB();
        if (afterDate == null) {
            BankDB.TaxDeposit latestTaxRecord = bankDb.getLatestTaxDeposit(getAlliance_id());

            afterDate = oldestApiFetchDate;
            if (latestTaxRecord != null) afterDate = latestTaxRecord.date;

        }

        List<Bankrec> bankRecs = api.fetchTaxRecsWithInfo(getAlliance_id(), afterDate);

        if (bankRecs == null) return null;
        if (bankRecs.isEmpty()) return new ArrayList<>();

        Map<Integer, com.politicsandwar.graphql.model.TaxBracket> taxRates = api.fetchTaxBrackets(getAlliance_id());

        List<BankDB.TaxDeposit> taxes = new ArrayList<>();
        Map<Integer, TaxRate> internalTaxRateCache = new HashMap<>();
        for (Bankrec bankrec : bankRecs) {
            int nationId = bankrec.getSender_id();
            TaxRate internal = internalTaxRateCache.get(nationId);
            if (internal == null) {
                if (db != null) {
                    internal = db.getHandler().getInternalTaxrate(nationId);
                } else {
                    internal = new TaxRate(-1, -1);
                }
                internalTaxRateCache.put(nationId, internal);
            }

            double[] deposit = ResourceType.fromApiV3(bankrec, null);
            if (ResourceType.isZero(deposit)) continue;

            int moneyTax = 0;
            int resourceTax = 0;
            com.politicsandwar.graphql.model.TaxBracket taxRate = taxRates.get(bankrec.getTax_id());
            if (taxRate != null) {
                moneyTax = taxRate.getTax_rate();
                resourceTax = taxRate.getResource_tax_rate();
            }

            BankDB.TaxDeposit taxRecord = new BankDB.TaxDeposit(bankrec.getReceiver_id(), bankrec.getDate().toEpochMilli(), bankrec.getId(), bankrec.getTax_id(), nationId, moneyTax, resourceTax, internal.money, internal.resources, deposit);
            taxes.add(taxRecord);

        }
        Locutus.imp().getBankDB().addTaxDeposits(taxes);
        return taxes;
    }

    public Map<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> getResourcesNeeded(Collection<DBNation> nations, Map<DBNation, Map<ResourceType, Double>> existing, double daysDefault, boolean useExisting, boolean force) throws IOException {
        if (useExisting) {
            if (existing == null) {
                existing = getMemberStockpile(f -> nations.contains(f));
            }
        } else {
            existing = new HashMap<>();
            for (DBNation nation : nations) {
                existing.put(nation, new HashMap<>());
            }
        }
        Map<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> result = new HashMap<>();
        for (DBNation nation : nations) {
            Map<ResourceType, Double> stockpile = existing.get(nation);
            if (stockpile == null) {
                result.put(nation, Map.entry(OffshoreInstance.TransferStatus.ALLIANCE_ACCESS, ResourceType.getBuffer()));
                continue;
            }
            Map<ResourceType, Double> needed = nation.getResourcesNeeded(stockpile, daysDefault, force);
            if (!needed.isEmpty()) {
                result.put(nation, Map.entry(OffshoreInstance.TransferStatus.SUCCESS, PnwUtil.resourcesToArray(needed)));
            } else {
                result.put(nation, Map.entry(OffshoreInstance.TransferStatus.NOTHING_WITHDRAWN, ResourceType.getBuffer()));
            }
        }

        return result;
    }

    public OffshoreInstance getBank() {
        if (bank == null) {
            synchronized (this) {
                if (bank == null) {
                    bank = new OffshoreInstance(allianceId);
                }
            }
        }
        return bank;
    }

    public int updateTaxesLegacy(Long latestDate) {
        int count = 0;

        List<BankDB.TaxDeposit> existing = Locutus.imp().getBankDB().getTaxesByTurn(allianceId);
        int latestId = 1;
        if (latestDate == null) {
            latestDate = 0L;
        }

        Auth auth = getAuth(AlliancePermission.TAX_BRACKETS);
        if (auth == null) throw new IllegalArgumentException("Not auth found");


        long now = System.currentTimeMillis();
        if (!existing.isEmpty()) {

            long date = existing.get(existing.size() - 1).date;
            if (date < now) {
                latestDate = Math.max(latestDate, date);
            }
            latestId = existing.get(existing.size() - 1).index;
        }

        List<BankDB.TaxDeposit> taxes = new GetTaxesTask(auth, latestDate).call();

        synchronized (Locutus.imp().getBankDB()) {
            long oldestFetched = Long.MAX_VALUE;
            for (BankDB.TaxDeposit tax : taxes) {
                tax.index = ++latestId;
                oldestFetched = Math.min(oldestFetched, tax.date);
            }
            if (oldestFetched < latestDate - TimeUnit.DAYS.toMillis(7)) {
                throw new IllegalArgumentException("Invalid fetch date: " + oldestFetched);
            }

            if (!taxes.isEmpty()) {
                Locutus.imp().getBankDB().deleteTaxDeposits(auth.getAllianceId(), oldestFetched);
                Locutus.imp().getBankDB().addTaxDeposits(taxes);
            }
        }
        return count;
    }

    public Map<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> calculateDisburse(Collection<DBNation> nations, Map<DBNation, Map<ResourceType, Double>> cachedStockpilesorNull, double daysDefault, boolean useExisting, boolean ignoreInactives, boolean allowBeige, boolean noDailyCash, boolean noCash, boolean force) throws IOException, ExecutionException, InterruptedException {
        Map<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> nationResourcesNeed;
        nationResourcesNeed = getResourcesNeeded(nations, cachedStockpilesorNull, daysDefault, useExisting, force);

        Map<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> toSend = new HashMap<>();

        for (Map.Entry<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> entry : nationResourcesNeed.entrySet()) {
            DBNation nation = entry.getKey();
            Map.Entry<OffshoreInstance.TransferStatus, double[]> value = entry.getValue();
            double[] resources = value.getValue();

            if (noDailyCash) {
                resources[ResourceType.MONEY.ordinal()] = Math.max(0, resources[ResourceType.MONEY.ordinal()] - daysDefault * 500000);
            }
            if (noCash) resources[ResourceType.MONEY.ordinal()] = 0;

            if (nation.getPositionEnum() == Rank.APPLICANT) {
                toSend.put(nation, Map.entry(OffshoreInstance.TransferStatus.APPLICANT, ResourceType.getBuffer()));
                continue;
            }
            if (nation.getAlliance_id() != allianceId) {
                toSend.put(nation, Map.entry(OffshoreInstance.TransferStatus.NOT_MEMBER, ResourceType.getBuffer()));
                continue;
            }
            if (nation.getVm_turns() > 0) {
                toSend.put(nation, Map.entry(OffshoreInstance.TransferStatus.VACATION_MODE, ResourceType.getBuffer()));
            }
            if (nation.isGray() && !ignoreInactives && !force) {
                toSend.put(nation, Map.entry(OffshoreInstance.TransferStatus.GRAY, ResourceType.getBuffer()));
                continue;
            }
            if (nation.active_m() > TimeUnit.DAYS.toMinutes(4) && !ignoreInactives && !force) {
                toSend.put(nation, Map.entry(OffshoreInstance.TransferStatus.INACTIVE, ResourceType.getBuffer()));
            }
            if (nation.isBeige() && !allowBeige && !force) {
                toSend.put(nation, Map.entry(OffshoreInstance.TransferStatus.BEIGE, ResourceType.getBuffer()));
            }
            if (value.getKey() != OffshoreInstance.TransferStatus.SUCCESS) {
                toSend.put(nation, value);
                continue;
            }
            if (resources[ResourceType.CREDITS.ordinal()] != 0) {
                toSend.put(nation, Map.entry(OffshoreInstance.TransferStatus.ALLIANCE_ACCESS, ResourceType.getBuffer()));
                continue;
            }
            if (ResourceType.isZero(resources)) {
                toSend.put(nation, Map.entry(OffshoreInstance.TransferStatus.NOTHING_WITHDRAWN, ResourceType.getBuffer()));
                continue;
            }

            toSend.put(nation, entry.getValue());
        }

        return toSend;
    }

    public Set<DBNation> getNations(Predicate<DBNation> filter) {
        Set<DBNation> nations = new HashSet<>();
        for (DBNation nation : getNations()) {
            if (filter.test(nation)) nations.add(nation);
        }
        return nations;
    }

    public boolean setTaxBracket(TaxBracket required, DBNation nation) {
        PoliticsAndWarV3 api = getApiOrThrow(true, AlliancePermission.TAX_BRACKETS);
        com.politicsandwar.graphql.model.TaxBracket result = api.assignTaxBracket(required.taxId, nation.getNation_id());
        return result != null;
    }

    public Treaty sendTreaty(int allianceId, TreatyType type, String message, int days) {
        PoliticsAndWarV3 api = getApiOrThrow(true, AlliancePermission.MANAGE_TREATIES);
        com.politicsandwar.graphql.model.Treaty result = api.proposeTreaty(allianceId, days, type, message);
        return new Treaty(result);
    }

    public Treaty approveTreaty(int id) {
        PoliticsAndWarV3 api = getApiOrThrow(true, AlliancePermission.MANAGE_TREATIES);
        com.politicsandwar.graphql.model.Treaty result = api.approveTreaty(id);
        return new Treaty(result);
    }


    public Treaty cancelTreaty(int id) {
        PoliticsAndWarV3 api = getApiOrThrow(true, AlliancePermission.MANAGE_TREATIES);
        com.politicsandwar.graphql.model.Treaty result = api.cancelTreaty(id);
        return new Treaty(result);
    }

    public double getCities() {
        return getMemberDBNations().stream().mapToDouble(DBNation::getCities).sum();
    }

    public Map<DBNation, Integer> updateOffSpyOps() {
        Map<DBNation, Integer> ops = new HashMap<>();
        // get api
        PoliticsAndWarV3 api = getApiOrThrow(true, AlliancePermission.SEE_SPIES);
        for (Nation nation : api.fetchNations(true, new Consumer<NationsQueryRequest>() {
            @Override
            public void accept(NationsQueryRequest request) {
                request.setAlliance_id(List.of(allianceId));
                request.setVmode(false);
            }
        }, new Consumer<NationResponseProjection>() {
            @Override
            public void accept(NationResponseProjection proj) {
                proj.id();
                proj.spies();
                proj.spy_attacks();
                proj.espionage_available();
            }
        })) {
            DBNation dbNation = DBNation.getById(nation.getId());
            if (dbNation == null) continue;
            DBNation copy = new DBNation(dbNation);

            dbNation.setSpies(nation.getSpies(), false);
            if (nation.getEspionage_available() != (dbNation.isEspionageAvailable())) {
                dbNation.setEspionageFull(!nation.getEspionage_available());
            }
            if (nation.getSpy_attacks() != null) {
                nation.setSpy_attacks(nation.getSpy_attacks());
                ops.put(dbNation, nation.getSpy_attacks());
            }
        }
        return ops;
    }
}
