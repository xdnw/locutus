package link.locutus.discord.db.entities;

import com.politicsandwar.graphql.model.ApiKeyDetails;
import com.politicsandwar.graphql.model.Bankrec;
import com.politicsandwar.graphql.model.Nation;
import com.politicsandwar.graphql.model.NationResponseProjection;
import com.politicsandwar.graphql.model.NationsQueryRequest;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.*;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv2.PoliticsAndWarV2;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.commands.manager.v2.binding.bindings.ScopedPlaceholderCache;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.commands.manager.v2.builder.RankBuilder;
import link.locutus.discord.commands.manager.v2.table.imp.CoalitionMetricsGraph;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.TaxDeposit;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.db.entities.metric.GrowthAsset;
import link.locutus.discord.db.entities.metric.MembershipChangeReason;
import link.locutus.discord.db.entities.metric.GrowthSummary;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.alliance.*;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.GuildOrAlliance;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.apiv1.domains.AllianceMembers;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.discord.util.scheduler.ThrowingFunction;
import link.locutus.discord.util.task.deprecated.GetTaxesTask;
import link.locutus.discord.util.task.EditAllianceTask;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.binding.value_types.WebGraph;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static link.locutus.discord.util.MathMan.orElse;

public class DBAlliance implements NationList, NationOrAlliance, GuildOrAlliance {
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

    @Command(desc = "Number of treasures in the alliance")
    public int getNumTreasures() {
        if (allianceId == 0) return 0;
        return Locutus.imp().getNationDB().countTreasures(allianceId);
    }

    @Command(desc = "Treasure bonus (decimal percent between 0-1)")
    public double getTreasureBonus() {
        int num = getNumTreasures();
        return num == 0 ? 0 : (Math.sqrt(num * 4) * 0.01);
    }

    @Command(desc = "Get value of an alliance metric at a date")
    public double getMetricAt(ValueStore store, AllianceMetric metric, @Default @Timestamp Long date) {
        if (date == null) return metric.apply(this);
        long turn = TimeUtil.getTurn(date);
        if (turn == TimeUtil.getTurn()) {
            return metric.apply(this);
        }
        NationDB db = Locutus.imp().getNationDB();
        String method = "metric" + (turn - TimeUtil.getTurn(TimeUtil.getOrigin())) + metric;
        ScopedPlaceholderCache<DBAlliance> scoped = PlaceholderCache.getScoped(store, DBAlliance.class, method);
        Double value = scoped.getMap(this,
        (ThrowingFunction<List<DBAlliance>, Map<DBAlliance, Double>>)
        f -> {
            Set<Integer> aaIds = new IntOpenHashSet(f.size());
            for (DBAlliance alliance : f) aaIds.add(alliance.allianceId);
            Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metrics = db.getAllianceMetrics(aaIds, metric, turn);
            Map<DBAlliance, Double> result = new Object2ObjectOpenHashMap<>();
            for (Map.Entry<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> entry : metrics.entrySet()) {
                DBAlliance aa = entry.getKey();
                for (Map.Entry<AllianceMetric, Map<Long, Double>> entry2 : entry.getValue().entrySet()) {
                    entry2.getValue().forEach((k, v) -> {
                        result.put(aa, v);
                    });
                }
            }
            return result;
        });
        return value == null ? 0 : value;
    }

    public Map<AllianceMetric, Map<Long, Double>> getMetricsAt(ValueStore store, Set<AllianceMetric> metrics, long turnStart, long turnEnd) {
        String method = "metrics" + metrics;
        ScopedPlaceholderCache<DBAlliance> scoped = PlaceholderCache.getScoped(store, DBAlliance.class, method);
        Map<AllianceMetric, Map<Long, Double>> result = scoped.getMap(this,
        (ThrowingFunction<List<DBAlliance>, Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>>>)
        f -> {
            Set<Integer> aaIds = new IntOpenHashSet(f.size());
            for (DBAlliance alliance : f) aaIds.add(alliance.allianceId);
            return Locutus.imp().getNationDB().getAllianceMetrics(aaIds, metrics, turnStart, turnEnd);
        });
        return result == null ? Collections.emptyMap() : result;
    }

    @Override
    public boolean isValid() {
        return get(allianceId) != null;
    }

    @Override
    public AllianceList toAllianceList() {
        return new AllianceList(allianceId);
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

    @Command(desc = "Loot value for a specific score")
    public double getLootValue(double score) {
        LootEntry loot = getLoot();
        return loot == null ? 0 : ResourceType.convertedTotal(loot.getAllianceLootValue(score));
    }

    @Command(desc = "Loot resources for a specific score")
    public Map<ResourceType, Double> getLoot(double score) {
        LootEntry loot = getLoot();
        return loot == null ? Collections.emptyMap() : ResourceType.resourcesToMap(loot.getAllianceLootValue(score));
    }

    @Command(desc = "Estimated stockpile based on last loot info")
    public Map<ResourceType, Double> getEstimatedStockpile() {
        LootEntry loot = getLoot();
        return loot == null ? Collections.emptyMap() : ResourceType.resourcesToMap(loot.getTotal_rss());
    }

    @Command(desc = "Estimated stockpile value based on last loot info")
    public double getEstimatedStockpileValue() {
        LootEntry loot = getLoot();
        return loot == null ? 0 : loot.convertedTotal();
    }

    @Command
    public double getCostConverted() {
        double total = 0;
        for (DBNation nation : getMemberDBNations()) {
            total += nation.costConverted();
        }
        return total;
    }

    public String setAAPage(String file) throws Exception{
        String input = FileUtil.readFile(file);

        input = input.replaceAll("\n", "");
        input = input.replaceAll("\r", "");
        input = input.replaceAll("\t", "");
        input = input.replaceAll("[ ][ ][ ]+", "");

        String finalInput = input;

        Auth auth = getAuth();
        return new EditAllianceTask(auth.getNation(), new Consumer<Map<String, String>>() {
            @Override
            public void accept(Map<String, String> stringStringMap) {
                stringStringMap.put("desc", finalInput);
            }
        }).call();
    }

    public static DBAlliance parse(String arg, boolean throwError) {
        Integer id = PW.parseAllianceId(arg);
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
    public double exponentialCityStrength(@Default Double power) {
        if (power == null) power = 3d;
        double total = 0;
        for (DBNation nation : getNations(true, 10000, true)) {
            total += Math.pow(nation.getCities(), power);
        }
        return total;
    }

    public static Set<DBAlliance> getTopX(int topX, boolean checkTreaty) {
        Set<DBAlliance> results = new ObjectLinkedOpenHashSet<>();
        Map<Integer, Double> aas = new RankBuilder<>(Locutus.imp().getNationDB().getAllNations()).group(DBNation::getAlliance_id).sumValues(DBNation::getScore).sort().get();
        for (Map.Entry<Integer, Double> entry : aas.entrySet()) {
            if (entry.getKey() == 0) continue;
            if (topX-- <= 0) break;
            int allianceId = entry.getKey();
            results.add(DBAlliance.getOrCreate(allianceId));
            if (checkTreaty) {
                Map<Integer, Treaty> treaties = Locutus.imp().getNationDB().getTreaties(allianceId);
                for (Map.Entry<Integer, Treaty> aaTreatyEntry : treaties.entrySet()) {
                    switch (aaTreatyEntry.getValue().getType()) {
                        case EXTENSION:
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
    private long BRACKETS_TIME_UPDATED;

    public synchronized Map<Integer, TaxBracket> getTaxBrackets(long cacheFor) {
        long now = System.currentTimeMillis();
        if (cacheFor > 0 && (now - BRACKETS_TIME_UPDATED < cacheFor) && BRACKETS_CACHED != null) {
            boolean isOutdated = false;
            for (int id : listUsedTaxIds()) {
                if (!BRACKETS_CACHED.containsKey(id)) {
                    isOutdated = true;
                    break;
                }
            }
            if (!isOutdated) return BRACKETS_CACHED;
        }
        if (cacheFor == Long.MAX_VALUE) {
            Map<Integer, TaxBracket> brackets = new LinkedHashMap<>();
            for (DBNation nation : getNations()) {
                int taxId = nation.getTax_id();
                if (taxId != 0) {
                    brackets.put(taxId, new TaxBracket(taxId, allianceId, "#" + taxId, -1, -1, 0L));
                }
            }
            return brackets;
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

        Map<Integer, com.politicsandwar.graphql.model.TaxBracket> bracketsV3 = api.fetchTaxBrackets(allianceId, true);
        BRACKETS_CACHED = new ConcurrentHashMap<>();
        BRACKETS_TIME_UPDATED = now;
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

    public String toMarkdown() {
        StringBuilder body = new StringBuilder();
        // `#id` | Alliance urlMakrup / acronym (linked)
        body.append("`AA:").append(allianceId).append("` | ").append(getMarkdownUrl());
        if (acronym != null && !acronym.isEmpty()) {
            body.append(" / `").append(acronym).append("`");
        }
        body.append(" | `#").append(getRank()).append("`").append("\n");

        String prefix = "";
        if (discord_link != null && !discord_link.isEmpty()) {
            body.append(MarkupUtil.markdownUrl("Discord", discord_link));
            prefix = " | ";
        }
        if (wiki_link != null && !wiki_link.isEmpty()) {
            body.append(prefix).append(MarkupUtil.markdownUrl("Wiki", wiki_link));
            prefix = " | ";
        }
        if (forum_link != null && !forum_link.isEmpty()) {
            body.append(prefix).append(MarkupUtil.markdownUrl("Forum", forum_link));
            prefix = " | ";
        }
        {
            DBAlliance parent = getCachedParentOfThisOffshore();
            if (parent != null) {
                body.append(prefix).append("Offshore of: " + parent.getMarkdownUrl());
                prefix = " | ";
            } else {
                for (DBAlliance other : Locutus.imp().getNationDB().getAlliances()) {
                    if (other == this) continue;
                    parent = other.getCachedParentOfThisOffshore();
                    if (parent != null && parent.getAlliance_id() == allianceId) {
                        body.append(prefix).append(MarkupUtil.markdownUrl("Offshore for: ", other.getMarkdownUrl()));
                        prefix = " | ";
                        break;
                    }
                }
            }
        }

        if (!prefix.isEmpty()) {
            body.append("\n");
        }
        body.append("```\n");
        // Number of members / applicants (active past day)
        Set<DBNation> nations = getNations();
        Set<DBNation> members = nations.stream().filter(n -> n.getPosition() > Rank.APPLICANT.id && n.getVm_turns() == 0).collect(Collectors.toSet());
        Set<DBNation> activeMembers = members.stream().filter(n -> n.active_m() < 7200).collect(Collectors.toSet());
        Set<DBNation> taxableMembers = members.stream().filter(n -> n.isTaxable()).collect(Collectors.toSet());
        Set<DBNation> applicants = nations.stream().filter(n -> n.getPosition() == Rank.APPLICANT.id && n.getVm_turns() == 0).collect(Collectors.toSet());
        Set<DBNation> activeApplicants = applicants.stream().filter(n -> n.active_m() < 7200).collect(Collectors.toSet());
        // 5 members (3 active/2 taxable) | 2 applicants (1 active)
        body.append(members.size()).append(" members (").append(activeMembers.size()).append(" active/").append(taxableMembers.size()).append(" taxable)");
        if (!applicants.isEmpty()) {
            body.append(" | ").append(applicants.size()).append(" applicants (").append(activeApplicants.size()).append(" active)");
        }
        body.append("\n");
        // Off, Def, Cities (total/average), Score, Color
        int off = nations.stream().mapToInt(DBNation::getOff).sum();
        int def = nations.stream().mapToInt(DBNation::getDef).sum();
        int cities = members.stream().mapToInt(DBNation::getCities).sum();
        double avgCities = cities / (double) members.size();
        double score = members.stream().mapToDouble(DBNation::getScore).sum();
        body.append(off).append("\uD83D\uDDE1 | ")
                .append(def).append("\uD83D\uDEE1 | ")
                .append(cities).append("\uD83C\uDFD9").append(" (avg:").append(MathMan.format(avgCities)).append(") | ")
                .append(MathMan.format(score)).append("ns | ")
                .append(color).append("\n```\n");

        // mmr
        double[] mmrBuild = this.getAverageMMR(false);
        double[] mmrUnit = this.getAverageMMRUnit();
        // Convert to e.g. MMR[Build]=1.5/2.5/1.1/3.0 | MMR[Unit]=1.5/2.5/1.1/3.0
        // append with each number on newline
        body.append("\n**MMR[Build]**: `")
                .append(MathMan.format(mmrBuild[0])).append("/")
                .append(MathMan.format(mmrBuild[1])).append("/")
                .append(MathMan.format(mmrBuild[2])).append("/")
                .append(MathMan.format(mmrBuild[3])).append("`")
            .append("\n**MMR[Unit]**: `")
                .append(MathMan.format(mmrUnit[0])).append("/")
                .append(MathMan.format(mmrUnit[1])).append("/")
                .append(MathMan.format(mmrUnit[2])).append("/")
                .append(MathMan.format(mmrUnit[3])).append("`\n");
        Map<DBAlliance, Integer> warsByAlliance = new HashMap<>();
        for (DBWar war : getActiveWars()) {
            DBNation attacker = war.getNation(true);
            DBNation defender = war.getNation(false);
            if (attacker == null || attacker.active_m() > 7200) continue;
            if (defender == null || defender.active_m() > 7200) continue;
            int otherAAId = war.getAttacker_aa() == allianceId ? war.getDefender_aa() : war.getAttacker_aa();
            if (otherAAId > 0) {
                DBAlliance otherAA = DBAlliance.getOrCreate(otherAAId);
                warsByAlliance.put(otherAA, warsByAlliance.getOrDefault(otherAA, 0) + 1);
            }
        }
        if (!warsByAlliance.isEmpty()) {
            List<Map.Entry<DBAlliance, Integer>> sorted = warsByAlliance.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .toList();
            body.append("\n**Alliance Wars:**\n");
            String cappedMsg = null;
            if (sorted.size() > 20) {
                cappedMsg = "- +" + (sorted.size() - 20) + " more";
                sorted = sorted.stream().limit(20).collect(Collectors.toList());
            }
            for (Map.Entry<DBAlliance, Integer> entry : sorted) {
                body.append("- ").append(PW.getMarkdownUrl(entry.getKey().getId(), true))
                        .append(": ").append(entry.getValue()).append(" wars\n");
            }
            if (cappedMsg != null) {
                body.append(cappedMsg).append("\n");
            }
        }
        Map<Integer, Treaty> treaties = this.getTreaties();
        if (treaties.isEmpty()) {
            body.append("`No treaties`\n");
        } else {
            body.append("\n**Treaties:**\n");
            String cappedMsg = null;
            if (treaties.size() > 20) {
                cappedMsg = "- +" + (treaties.size() - 20) + " more";
                treaties = treaties.entrySet().stream().limit(20).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            }
            for (Treaty treaty : treaties.values()) {
                int otherId = treaty.getToId() == allianceId ? treaty.getFromId() : treaty.getToId();
                body.append("- ").append(treaty.getType())
                        .append(": ").append(PW.getMarkdownUrl(otherId, true))
                        .append(" (").append(treaty.getExpiresDiscordString())
                        .append(")\n");
            }
            if (cappedMsg != null) {
                body.append(cappedMsg).append("\n");
            }
        }
        // Revenue
        Map<ResourceType, Double> revenue = getRevenue();
        if (revenue.isEmpty()) {
            body.append("`No taxable revenue`\n");
        } else {
            body.append("\n**Taxable Nation Revenue:**");
            body.append("`").append(ResourceType.toString(revenue)).append("`\n");
            body.append("- worth: `$" + MathMan.format(ResourceType.convertedTotal(revenue)) + "`\n");
        }
        // Last loot
        LootEntry lastLoot = this.getLoot();
        if (lastLoot == null) {
            body.append("`No loot history`\n");
        } else {
            body.append("\n**Last Resources:** (from ")
                    .append(lootEntry.getType().name()).append(" ")
                    .append(DiscordUtil.timestamp(lootEntry.getDate(), null)).append(")\n");
            body.append("`").append(ResourceType.toString(lootEntry.getTotal_rss())).append("`\n");
            body.append("- worth: `$").append(MathMan.format(lootEntry.convertedTotal())).append("`\n");
        }
        return body.toString();
    }

    @Command(desc = "Sum of nation attribute for specific nations in alliance")
    public double getTotal(@NoFormat NationAttributeDouble attribute, @NoFormat @Default NationFilter filter) {
        Set<DBNation> nations = filter == null ? getNations() : getNations(filter.toCached(Long.MAX_VALUE));
        return nations.stream().mapToDouble(attribute::apply).sum();
    }

    @Command(desc = "Average of nation attribute for specific nations in alliance")
    public double getAverage(@NoFormat NationAttributeDouble attribute, @NoFormat @Default NationFilter filter) {
        Set<DBNation> nations = filter == null ? getNations() : getNations(filter.toCached(Long.MAX_VALUE));
        return nations.stream().mapToDouble(attribute::apply).average().orElse(0);
    }

    @Command(desc = "Returns the average value of the given attribute per another attribute (such as cities)")
    public double getAveragePer(@NoFormat NationAttributeDouble attribute, @NoFormat NationAttributeDouble per, @Default NationFilter filter) {
        double total = 0;
        double perTotal = 0;
        for (DBNation nation : getNations(filter.toCached(Long.MAX_VALUE))) {
            total += attribute.apply(nation);
            perTotal += per.apply(nation);
        }
        return total / perTotal;
    }

    @Command(desc = "Number of members, not including VM")
    public int countMembers() {
        return getMemberDBNations().size();
    }

    @Command(desc = "Count of nations in alliance matching a filter")
    public int countNations(@NoFormat @Default NationFilter filter) {
        if (filter == null) return getNations().size();
        return getNations(filter.toCached(Long.MAX_VALUE)).size();
    }

    @Command(desc = "Is allied with another alliance")
    public boolean hasDefensiveTreaty(@NoFormat Set<DBAlliance> alliances) {
        for (DBAlliance alliance : alliances) {
            Treaty treaty = getDefenseTreaties().get(alliance.getId());
            if (treaty != null) return true;
        }
        return false;
    }

    @Command(desc = "Get the treaty type with another alliance")
    public TreatyType getTreatyType(DBAlliance alliance) {
        Treaty treaty = getTreaties().get(alliance.getId());
        return treaty == null ? null : treaty.getType();
    }

    @Command(desc = "Get the treaty level number with another alliance\n" +
            "0 = No Treaty" +
            "1 = PIAT" +
            "2 = NAP" +
            "3 = NPT" +
            "4 = ODP" +
            "5 = ODOAP" +
            "6 = PROTECTORATE" +
            "7 = MDP" +
            "8 = MDOAP"
    )
    public int getTreatyOrdinal(DBAlliance alliance) {
        Treaty treaty = getTreaties().get(alliance.getId());
        return treaty == null ? 0 : treaty.getType().getStrength();
    }

    @Command(desc = "Market value of alliance revenue of taxable member nations")
    public double getRevenueConverted() {
        return ResourceType.convertedTotal(getRevenue());
    }

    @Command(desc = "Revenue of taxable alliance members")
    public Map<ResourceType, Double> getRevenue() {
        return getRevenue(getNations(f -> f.isTaxable()));
    }

    private Map<ResourceType, Double> getRevenue(Set<DBNation> nations) {
        double[] total = ResourceType.getBuffer();
        for (DBNation nation : nations) {
            total = ResourceType.add(total, nation.getRevenue());
        }
        return ResourceType.resourcesToMap(total);
    }

    @Command(desc = "Get the markdown url of this alliance")
    public String getMarkdownUrl() {
        return PW.getMarkdownUrl(allianceId, true);
    }

    @Command(desc = "Markdown url to the bot's web page for the nation (instead of ingame page)")
    public String getWebUrl() {
        return MarkupUtil.markdownUrl(getName(), "<" + Settings.INSTANCE.WEB.FRONTEND_DOMAIN + "/alliance/" + getId() + ">");
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
    public boolean isGuild() {
        return false;
    }

    @Override
    public String toString() {
        return getName();
    }

    public Set<DBNation> getNations(boolean removeVM, int removeInactiveM, boolean removeApps) {
        Set<DBNation> nations = getNations();
        if (removeVM) nations.removeIf(f -> f.getVm_turns() != 0);
        if (removeInactiveM > 0) nations.removeIf(f -> f.active_m() > removeInactiveM);
        if (removeApps) nations.removeIf(f -> f.getPosition() <= 1);
        return nations;
    }

    public Set<DBNation> getNations() {
        return Locutus.imp().getNationDB().getNationsByAlliance(Collections.singleton(allianceId));
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
//        Set<DBNation> toUpdate = new ObjectLinkedOpenHashSet<>();
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
        return getTreatiedAllies(TreatyType::isDefensive, checkOffshore);
    }

    public Set<DBAlliance> getTreatiedAllies(Predicate<TreatyType> allowedType, boolean checkOffshore) {
        Set<DBAlliance> allies = new HashSet<>();
        for (Map.Entry<Integer, Treaty> treatyEntry : getTreaties(allowedType, false).entrySet()) {
            Treaty treaty = treatyEntry.getValue();
            int other = treaty.getFromId() == allianceId ? treaty.getToId() : treaty.getFromId();
            allies.add(DBAlliance.getOrCreate(other));
        }
        if (checkOffshore) {
            DBAlliance parent = getCachedParentOfThisOffshore();
            if (parent != null) {
                allies.add(parent);
                allies.addAll(parent.getTreatiedAllies(allowedType, false));
            }
        }
        return allies;
    }

    public Map<Integer, Treaty> getDefenseTreaties() {
        HashMap<Integer, Treaty> defTreaties = new HashMap<>(getTreaties());
        defTreaties.entrySet().removeIf(f -> f.getValue().getType() == TreatyType.NAP || f.getValue().getType() == TreatyType.PIAT || f.getValue().getType() == TreatyType.NPT);
        return defTreaties;
    }

    public Map<Integer, Treaty> getTreaties() {
        return getTreaties(false);
    }

    public Map<Integer, Treaty> getTreaties(boolean update) {
        return getTreaties(null, update);
    }

    public Map<Integer, Treaty> getTreaties(Predicate<TreatyType> allowedType, boolean update) {
        if (update) {
            PoliticsAndWarV3 api = getApi(AlliancePermission.MANAGE_TREATIES);
            if (api != null) {
                List<com.politicsandwar.graphql.model.Treaty> treaties = api.fetchTreaties(allianceId);
                Locutus.imp().runEventsAsync(events ->
                    Locutus.imp().getNationDB().
                        updateTreaties(treaties, events, f -> f.getFromId() == allianceId || f.getToId() == allianceId));
                Map<Integer, Treaty> result = new HashMap<>();
                for (com.politicsandwar.graphql.model.Treaty v3 : treaties) {
                    Treaty treaty = new Treaty(v3);
                    if (allowedType == null || allowedType.test(treaty.getType())) {
                        result.put(treaty.getFromId() == allianceId ? treaty.getToId() : treaty.getFromId(), treaty);
                    }
                }
                return result;
            }
        }
        Map<Integer, Treaty> result = Locutus.imp().getNationDB().getTreaties(allianceId);
        if (allowedType != null) {
            result = result.entrySet().stream().filter(f -> allowedType.test(f.getValue().getType())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        return result;
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
    public double getScore() {
        return getScore(null);
    }

    @Command
    public double getScore(@NoFormat @Default NationFilter filter) {
        if (filter != null) {
            return new SimpleNationList(getNations(filter.toCached(Long.MAX_VALUE))).getScore();
        }
        if (scoreCached == -1) {
            scoreCached = new SimpleNationList(getNations(true, 0, true)).getScore();
        }
        return scoreCached;
    }

    private Integer rank;

    public int getRank() {
        return getRank(null);
    }

    @Command(desc = "Rank by score")
    public int getRank(@NoFormat @Default NationFilter filter) {
        if (filter != null) {
            Map<Integer, List<DBNation>> byScore = Locutus.imp().getNationDB().getNationsByAlliance(filter.toCached(Long.MAX_VALUE), true);
            int rankTmp = 0;
            for (Map.Entry<Integer, List<DBNation>> entry : byScore.entrySet()) {
                rankTmp++;
                if (entry.getKey() == allianceId) return rankTmp;
            }
            return Integer.MAX_VALUE;
        }
        if (rank == null) {
            Map<Integer, List<DBNation>> byScore = Locutus.imp().getNationDB().getNationsByAlliance(false, false, true, true, true);
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
                case EXTENSION:
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

    @Command(desc = "Get the alliance's in-game link")
    public String getUrl() {
        return Settings.PNW_URL() + "/alliance/id=" + getAlliance_id();
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

    public List<AllianceChange> getRankChanges() {
        return Locutus.imp().getNationDB().getRemovesByAlliance(allianceId);
    }

    public List<AllianceChange> getRankChanges(long timeStart) {
        return Locutus.imp().getNationDB().getRemovesByAlliance(allianceId, timeStart);
    }

    public Set<DBWar> getActiveWars() {
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
        Set<String> apiKeysToUse = new ObjectLinkedOpenHashSet<>();
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
                        DBNation nation = DBNation.getById(nationId);
                        if (nation != null && nation.getAlliance_id() == allianceId && nation.hasAllPermission(new HashSet<>(Arrays.asList(permissions)))) {
                            apiKeysToUse.add(key);
                        }
//                    deleteInfo(Key.API_KEY);
                    } catch (HttpClientErrorException.Unauthorized e) {
                        newKeys.remove(key);
                        if (newKeys.isEmpty()) {
                            db.deleteInfo(GuildKey.API_KEY);
                        } else {
                            db.setInfoRaw(GuildKey.API_KEY, newKeys);
                        }
                    } catch (Throwable e) {
                        throw e;
                    }
                }
            }
        }

        ApiKeyPool.SimpleBuilder builder = new ApiKeyPool.SimpleBuilder();

        if (!apiKeysToUse.isEmpty()) {
            builder.addKeys(new ArrayList<>(apiKeysToUse));
        } else {
            Set<DBNation> nations = getNations();
            for (DBNation gov : nations) {
                if (gov.getVm_turns() > 0 || gov.getPositionEnum().id <= Rank.APPLICANT.id || gov.getAlliance_id() != allianceId)
                    continue;
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
            result.put(nation, ResourceType.resourcesToMap(entry.getValue()));
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
        PoliticsAndWarV3 api = getApi(permissions);
        if (api == null) {
            String msg = "No api key found for " + getMarkdownUrl() + ". Please use" + CM.settings_default.registerApiKey.cmd.toSlashMention() + "\n" +
                    "Api key can be found on <" + Settings.PNW_URL() + "/account/>";
            if (permissions.length > 0) msg += " and ensure your in-game position grants: " + StringMan.getString(permissions);
            throw new IllegalArgumentException(msg);
        }
        return api;
    }

    @Deprecated
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
        return getStockpile(false);
    }

    public Map<ResourceType, Double> getStockpile(boolean throwError) {
        PoliticsAndWarV3 api = getApiOrThrow(AlliancePermission.VIEW_BANK);
        double[] stockpile = api.getAllianceStockpile(allianceId);
        if (stockpile == null && throwError) {
            api.throwInvalid(AlliancePermission.VIEW_BANK, "for alliance " + getMarkdownUrl());
        }
        return stockpile == null ? null : ResourceType.resourcesToMap(stockpile);
    }

    public void updateCities() throws IOException, ParseException {
        updateCities(f -> true);
    }

    public void updateCities(Predicate<DBNation> fetchNation) throws IOException {
        Set<Integer> nationIds = getNations(false, 0, true).stream().filter(fetchNation).map(DBNation::getId).collect(Collectors.toSet());
        if (nationIds.isEmpty()) return;
        Locutus.imp().runEventsAsync(events -> Locutus.imp().getNationDB().updateCitiesOfNations(nationIds, true, true, events));
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
            if (member.getVm_turns() == 0 && member.active_m() > 10000) {
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
            int lostAA = war.getStatus() == WarStatus.ATTACKER_VICTORY ? war.getDefender_aa() : war.getStatus() == WarStatus.DEFENDER_VICTORY ? war.getAttacker_aa() : 0;
            boolean isLooted = lostAA != 0 && lostAA == getAlliance_id();
            if (!isLooted) continue;
            int otherAA = war.getStatus() == WarStatus.ATTACKER_VICTORY ? war.getAttacker_aa() : war.getStatus() == WarStatus.DEFENDER_VICTORY ? war.getDefender_aa() : 0;
            if (otherAA == getAlliance_id()) continue;
            boolean lowMil = false;
            for (DBNation member : members) {
                if (member.getVm_turns() != 0) continue;
                if (member.active_m() > 7200) {
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
            AllianceChange lastAAInfo = member.getPreviousAlliance(true, null);
            if (lastAAInfo == null) continue;
            int aaId = lastAAInfo.getFromId();

            if (lastAAInfo.getFromRank().id >= Rank.OFFICER.id) {
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

    public List<TaxDeposit> updateTaxes() {
        return updateTaxes(null);
    }
    public List<TaxDeposit> updateTaxes(Long afterDate) {
        long oldestApiFetchDate = getDateCreated() - TimeUnit.HOURS.toMillis(2);

        GuildDB db = Locutus.imp().getGuildDBByAA(allianceId);

        PoliticsAndWarV3 api = getApi( AlliancePermission.TAX_BRACKETS);
        if (api == null) {
            return null;
        }

        BankDB bankDb = Locutus.imp().getBankDB();
        if (afterDate == null) {
            TaxDeposit latestTaxRecord = bankDb.getLatestTaxDeposit(getAlliance_id());

            afterDate = oldestApiFetchDate;
            if (latestTaxRecord != null) afterDate = latestTaxRecord.date;

        }

        List<Bankrec> bankRecs = api.fetchTaxRecsWithInfo(getAlliance_id(), afterDate);

        if (bankRecs == null) return null;
        if (bankRecs.isEmpty()) return new ArrayList<>();

        Map<Integer, com.politicsandwar.graphql.model.TaxBracket> taxRates = api.fetchTaxBrackets(getAlliance_id(), false);

        List<TaxDeposit> taxes = new ArrayList<>();
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

            TaxDeposit taxRecord = new TaxDeposit(bankrec.getReceiver_id(), bankrec.getDate().toEpochMilli(), bankrec.getId(), bankrec.getTax_id(), nationId, moneyTax, resourceTax, internal.money, internal.resources, deposit);
            taxes.add(taxRecord);

        }
        Locutus.imp().getBankDB().addTaxDeposits(taxes);
        return taxes;
    }

    public Map<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> getResourcesNeeded(Collection<DBNation> nations, Map<DBNation, Map<ResourceType, Double>> existing, double daysDefault, boolean useExisting, boolean force) throws IOException {
        if (useExisting) {
            if (existing == null) {
                existing = getMemberStockpile(nations::contains);
            }
            if (force) {
                updateCities(nations::contains);
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
                result.put(nation, KeyValue.of(OffshoreInstance.TransferStatus.ALLIANCE_ACCESS, ResourceType.getBuffer()));
                continue;
            }
            Map<ResourceType, Double> needed = nation.getResourcesNeeded(stockpile, daysDefault, false);
            if (!needed.isEmpty()) {
                result.put(nation, KeyValue.of(OffshoreInstance.TransferStatus.SUCCESS, ResourceType.resourcesToArray(needed)));
            } else {
                result.put(nation, KeyValue.of(OffshoreInstance.TransferStatus.NOTHING_WITHDRAWN, ResourceType.getBuffer()));
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

        List<TaxDeposit> existing = Locutus.imp().getBankDB().getTaxesByTurn(allianceId);
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

        List<TaxDeposit> taxes = new GetTaxesTask(auth, latestDate).call();

        synchronized (Locutus.imp().getBankDB()) {
            long oldestFetched = Long.MAX_VALUE;
            for (TaxDeposit tax : taxes) {
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

    public Map<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> calculateDisburse(Collection<DBNation> nations, Map<DBNation, Map<ResourceType, Double>> cachedStockpilesorNull, double daysDefault, boolean useExisting, boolean ignoreInactives, boolean allowBeige, boolean noDailyCash, boolean noCash, boolean bypassChecks, boolean force) throws IOException {
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
                toSend.put(nation, KeyValue.of(OffshoreInstance.TransferStatus.APPLICANT, ResourceType.getBuffer()));
                continue;
            }
            if (nation.getAlliance_id() != allianceId) {
                toSend.put(nation, KeyValue.of(OffshoreInstance.TransferStatus.NOT_MEMBER, ResourceType.getBuffer()));
                continue;
            }
            if (nation.getVm_turns() > 0) {
                toSend.put(nation, KeyValue.of(OffshoreInstance.TransferStatus.VACATION_MODE, ResourceType.getBuffer()));
                continue;
            }
            if (nation.isGray() && !ignoreInactives && !bypassChecks) {
                toSend.put(nation, KeyValue.of(OffshoreInstance.TransferStatus.GRAY, ResourceType.getBuffer()));
                continue;
            }
            if (nation.active_m() > TimeUnit.DAYS.toMinutes(4) && !ignoreInactives && !bypassChecks) {
                toSend.put(nation, KeyValue.of(OffshoreInstance.TransferStatus.INACTIVE, ResourceType.getBuffer()));
                continue;
            }
            if (nation.isBeige() && !allowBeige && !bypassChecks) {
                toSend.put(nation, KeyValue.of(OffshoreInstance.TransferStatus.BEIGE, ResourceType.getBuffer()));
                continue;
            }
            if (value.getKey() != OffshoreInstance.TransferStatus.SUCCESS) {
                toSend.put(nation, value);
                continue;
            }
            if (resources[ResourceType.CREDITS.ordinal()] != 0) {
                toSend.put(nation, KeyValue.of(OffshoreInstance.TransferStatus.ALLIANCE_ACCESS, ResourceType.getBuffer()));
                continue;
            }
            if (ResourceType.isZero(resources)) {
                toSend.put(nation, KeyValue.of(OffshoreInstance.TransferStatus.NOTHING_WITHDRAWN, ResourceType.getBuffer()));
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

    @Command(desc = "Google sheet named url")
    public String getSheetUrl() {
        return MarkupUtil.sheetUrl(getName(), getUrl());
    }

    @Command
    public double getCities() {
        return getMemberDBNations().stream().mapToDouble(DBNation::getCities).sum();
    }

    @Command
    public Map<String, Object> getMilitarizationGraph(@Default @Timestamp Long start, @Default @Timestamp Long end) {
        Set<AllianceMetric> metrics = new ObjectLinkedOpenHashSet<>(List.of(AllianceMetric.SOLDIER_PCT, AllianceMetric.TANK_PCT, AllianceMetric.AIRCRAFT_PCT, AllianceMetric.SHIP_PCT));
        return getMetricsGraph(metrics, start, end);
    }

    @Command
    public Map<String, Object> getMetricsGraph(Set<AllianceMetric> metrics, @Default @Timestamp Long start, @Default @Timestamp Long end) {
        if (end == null) end = System.currentTimeMillis();
        if (start == null) start = end - TimeUnit.DAYS.toMillis(7);
        long startTurn = TimeUtil.getTurn(start);
        long endTurn = TimeUtil.getTurn(end);
        CoalitionMetricsGraph table = CoalitionMetricsGraph.create(metrics, startTurn, endTurn, this.getName(), Collections.singleton(this));
        WebGraph toSerialize = table.toHtmlJson();
        return WebUtil.convertToMap(toSerialize);
    }

    public Map<DBNation, Integer> updateOffSpyOps() {
        Map<DBNation, Integer> ops = new HashMap<>();
        // get api
        PoliticsAndWarV3 api = getApiOrThrow(true, AlliancePermission.SEE_SPIES);
        Locutus.imp().runEventsAsync(events -> {
            for (Nation nation : api.fetchNations(true, request -> {
                request.setAlliance_id(List.of(allianceId));
                request.setVmode(false);
            }, proj -> {
                proj.id();
                proj.spies();
                proj.spy_attacks();
                proj.espionage_available();
            })) {
                DBNation dbNation = DBNation.getById(nation.getId());
                if (dbNation == null) continue;
                dbNation.setSpies(nation.getSpies(), events);
                if (nation.getEspionage_available() != (dbNation.isEspionageAvailable())) {
                    dbNation.setEspionageFull(!nation.getEspionage_available());
                }
                if (nation.getSpy_attacks() != null) {
                    nation.setSpy_attacks(nation.getSpy_attacks());
                    ops.put(dbNation, nation.getSpy_attacks());
                }
            }
        });
        return ops;
    }

    public Map<DBNation, Map<MilitaryUnit, Integer>> updateMilitaryBuys() {
        Map<DBNation, Map<MilitaryUnit, Integer>> ops = new Object2ObjectOpenHashMap<>();
        PoliticsAndWarV3 api = getApiOrThrow(true, AlliancePermission.SEE_SPIES);
        Locutus.imp().runEventsAsync(events -> {
            for (Nation nation : api.fetchNations(true, request -> {
                request.setAlliance_id(List.of(allianceId));
                request.setVmode(false);
            }, proj -> {
                proj.id();
                proj.soldiers_today();
                proj.tanks_today();
                proj.aircraft_today();
                proj.ships_today();
                proj.missiles_today();
                proj.nukes_today();
                proj.spies_today();
            })) {
                DBNation dbNation = DBNation.getById(nation.getId());
                if (dbNation == null) continue;
                Map<MilitaryUnit, Integer> units = new Object2IntOpenHashMap<>();
                units.put(MilitaryUnit.SOLDIER, orElse(nation.getSoldiers_today(), 0));
                units.put(MilitaryUnit.TANK, orElse(nation.getTanks_today(), 0));
                units.put(MilitaryUnit.AIRCRAFT, orElse(nation.getAircraft_today(), 0));
                units.put(MilitaryUnit.SHIP, orElse(nation.getShips_today(), 0));
                units.put(MilitaryUnit.MISSILE, orElse(nation.getMissiles_today(), 0));
                units.put(MilitaryUnit.NUKE, orElse(nation.getNukes_today(), 0));
                units.put(MilitaryUnit.SPIES, orElse(nation.getSpies_today(), 0));
                ops.put(dbNation, units);
            }
        });
        return ops;
    }

    public Map<Integer, Double> fetchUpdateTz(Set<DBNation> nations) {
        List<Integer> nationsInAa = nations.stream().filter(f -> f.getAlliance_id() == allianceId && f.getPosition() > Rank.APPLICANT.id).map(DBNation::getId).sorted().toList();
        Map<Integer, Double> timezones = new HashMap<>();
        if (nationsInAa.isEmpty()) return timezones;
        // get api
        PoliticsAndWarV3 api = getApiOrThrow(true, AlliancePermission.SEE_RESET_TIMERS);
        for (Nation nation : api.fetchNations(true, new Consumer<NationsQueryRequest>() {
            @Override
            public void accept(NationsQueryRequest nationsQueryRequest) {
                nationsQueryRequest.setAlliance_id(List.of(getAlliance_id()));
                nationsQueryRequest.setId(nationsInAa);
            }
        }, new Consumer<NationResponseProjection>() {
            @Override
            public void accept(NationResponseProjection nationResponseProjection) {
                nationResponseProjection.id();
                nationResponseProjection.update_tz();
            }
        })) {
            int id = nation.getId();
            Double timezone = nation.getUpdate_tz();
            if (timezone != null) {
                timezones.put(id, timezone);
            }
        }
        return timezones;
    }

    public GrowthSummary.AllianceGrowthSummary getGrowthSummary(ValueStore store, @Timestamp long start, @Timestamp @Default Long end) {
        long dayStart = TimeUtil.getDay(start);
        long dayEnd = end == null ? TimeUtil.getDay() : TimeUtil.getDay(end);
        ScopedPlaceholderCache<DBAlliance> scoped = PlaceholderCache.getScoped(store, DBAlliance.class, "growth_" + dayStart + "_" + dayEnd);
        GrowthSummary.AllianceGrowthSummary r = scoped.getMap(this,
        (ThrowingFunction<List<DBAlliance>, Map<DBAlliance, GrowthSummary.AllianceGrowthSummary>>)
        f -> {
            Map<Integer, DBAlliance> alliancesById = new Int2ObjectOpenHashMap<>();
            Set<DBAlliance> alliances = new ObjectOpenHashSet<>();
            for (DBAlliance aa : f) {
                alliancesById.put(aa.getId(), aa);
                alliances.add(aa);
            }
            GrowthSummary summary = new GrowthSummary(alliances, dayStart, dayEnd).run();
            Map<Integer, GrowthSummary.AllianceGrowthSummary> summaries = summary.getSummaries();
            Map<DBAlliance, GrowthSummary.AllianceGrowthSummary> result = new Object2ObjectOpenHashMap<>();
            for (DBAlliance alliance : f) {
                GrowthSummary.AllianceGrowthSummary allianceSummary = summaries.get(alliance.getId());
                if (allianceSummary != null) {
                    result.put(alliance, allianceSummary);
                }
            }
            return result;
        });
        return r == null ? new GrowthSummary.AllianceGrowthSummary() : r;
    }

    public Map<ResourceType, Double> getAssetAcquired(ValueStore store, Predicate<GrowthAsset> summaries, Predicate<MembershipChangeReason> reasons, boolean effective, @Timestamp long start, @Timestamp @Default Long end) {
        GrowthSummary.AllianceGrowthSummary summary = getGrowthSummary(store, start, end);
        return ResourceType.resourcesToMap(summary.getSpending(summaries, reasons, effective));
    }

    public double getAssetAcquiredValue(ValueStore store, Predicate<GrowthAsset> summaries, Predicate<MembershipChangeReason> reasons, boolean effective, @Timestamp long start, @Timestamp @Default Long end) {
        GrowthSummary.AllianceGrowthSummary summary = getGrowthSummary(store, start, end);
        return ResourceType.convertedTotal(summary.getSpending(summaries, reasons, effective));
    }

    @Command(desc = "The net change in an asset count for members over a period")
    public int getNetAsset(ValueStore store, GrowthAsset asset, @Timestamp long start, @Timestamp @Default Long end) {
        if (end == null) end = System.currentTimeMillis();
        int startVal = (int) getMetricAt(store, asset.count, start);
        int endVal = (int) getMetricAt(store, asset.count, end);
        return endVal - startVal;
    }

    @Command(desc = "The net change in an asset value for members over a period")
    public double getNetAssetValue(ValueStore store, Set<GrowthAsset> asset, @Timestamp long start, @Timestamp @Default Long end) {
        if (end == null) end = System.currentTimeMillis();
        double startVal = 0;
        for (GrowthAsset a : asset) {
            startVal += getMetricAt(store, a.value, start);
        }
        double endVal = 0;
        for (GrowthAsset a : asset) {
            endVal += getMetricAt(store, a.value, end);
        }
        return endVal - startVal;
    }

    // city
    @Command(desc = "The resources this alliance has spent on member cities over a period")
    public Map<ResourceType, Double> getSpending(ValueStore store, Set<GrowthAsset> assets, @Timestamp long start, @Timestamp @Default Long end) {
        return getAssetAcquired(store, assets::contains, f -> f == MembershipChangeReason.UNCHANGED, false, start, end);
    }

    @Command(desc = "The resources this alliance has spent on remaining members' cities over a period")
    public Map<ResourceType, Double> getEffectiveSpending(ValueStore store, Set<GrowthAsset> assets, @Timestamp long start, @Timestamp @Default Long end) {
        return getAssetAcquired(store, assets::contains, f -> f == MembershipChangeReason.UNCHANGED, true, start, end);
    }

    @Command(desc = "The value the alliance has spent on member cities over a period")
    public double getSpendingValue(ValueStore store, Set<GrowthAsset> assets, @Timestamp long start, @Timestamp @Default Long end) {
        return getAssetAcquiredValue(store, assets::contains, f -> f == MembershipChangeReason.UNCHANGED, false, start, end);
    }

    @Command(desc = "The value the alliance has spent on remaining members' cities over a period")
    public double getEffectiveSpendingValue(ValueStore store, Set<GrowthAsset> assets, @Timestamp long start, @Timestamp @Default Long end) {
        return getAssetAcquiredValue(store, assets::contains, f -> f == MembershipChangeReason.UNCHANGED, true, start, end);
    }

    @Command(desc = "The number of members which have joined and remained in the alliance over the period (all sources)")
    public int getNetMembersAcquired(ValueStore store, @Timestamp long start, @Timestamp @Default Long end) {
        GrowthSummary.AllianceGrowthSummary summary = getGrowthSummary(store, start, end);
        int total = 0;
        for (Map.Entry<Integer, MembershipChangeReason> entry : summary.finalState.entrySet()) {
            if (entry.getValue() == null || !entry.getValue().afterwardsMember()) continue;
            MembershipChangeReason initial = summary.initialState.get(entry.getKey());
            if (initial == null || !initial.previouslyMember()) {
                total++;
            }
        }
        for (Map.Entry<Integer, MembershipChangeReason> entry : summary.initialState.entrySet()) {
            if (entry.getValue() == null || !entry.getValue().previouslyMember()) continue;
            MembershipChangeReason finalState = summary.finalState.get(entry.getKey());
            if (finalState == null || !finalState.afterwardsMember()) {
                total--;
            }
        }

        return total;
    }

    @Command(desc = "The number of the specified assets bought for members in this alliance over the period, regardless of leaving")
    public int getBoughtAssetCount(ValueStore store, Set<GrowthAsset> assets, @Timestamp long start, @Timestamp @Default Long end) {
        return getGrowthSummary(store, start, end)
                .getAssetCounts(f -> assets.contains(f), f -> f == MembershipChangeReason.UNCHANGED, false);
    }

    @Command(desc = "The number of the specified assets bought for remaining members in this alliance over the period")
    public int getEffectiveBoughtAssetCount(ValueStore store, Set<GrowthAsset> assets, @Timestamp long start, @Timestamp @Default Long end) {
        return getGrowthSummary(store, start, end)
                .getAssetCounts(f -> assets.contains(f), f -> f == MembershipChangeReason.UNCHANGED, true);
    }

    //

    @Command(desc = """
            The number of membership changes by reason
            Nations can have multiple membership changes over a duration
            - RECRUITED: 7d or less nation becomes member
            - JOINED: Nation >7d becomes member
            - LEFT: Nation is set to applicant, none, or leaves the alliance (does not include delete/vm)
            - DELETED: nation deletes
            - VM_LEFT: Nation goes into VM
            - VM_RETURNED: Nation leaves VM""")
    public int getMembershipChangesByReason(ValueStore store, Set<MembershipChangeReason> reasons, @Timestamp long start, @Timestamp @Default Long end) {
        return getGrowthSummary(store, start, end)
                .getReasonCounts(reasons::contains);
    }

    @Command(desc = """
            The number of membership changes by reason
            Nations can have multiple membership changes over a duration
            - RECRUITED: 7d or less nation becomes member
            - JOINED: Nation >7d becomes member
            - LEFT: Nation is set to applicant, none, or leaves the alliance (does not include delete/vm)
            - DELETED: nation deletes
            - VM_LEFT: Nation goes into VM
            - VM_RETURNED: Nation leaves VM""")
    public int getMembershipChangeAssetCount(ValueStore store, Set<MembershipChangeReason> reasons, Set<GrowthAsset> assets, @Timestamp long start, @Timestamp @Default Long end) {
        return getGrowthSummary(store, start, end)
                .getAssetCounts(assets::contains, reasons::contains, false);
    }

    @Command(desc = """
            The market value of the specified assets associated with the provided membership change reasons
            Nations can have multiple membership changes over a duration
            - RECRUITED: 7d or less nation becomes member
            - JOINED: Nation >7d becomes member
            - LEFT: Nation is set to applicant, none, or leaves the alliance (does not include delete/vm)
            - DELETED: nation deletes
            - VM_LEFT: Nation goes into VM
            - VM_RETURNED: Nation leaves VM""")
    public double getMembershipChangeAssetValue(ValueStore store, Set<MembershipChangeReason> reasons, Set<GrowthAsset> assets, @Timestamp long start, @Timestamp @Default Long end) {
        return getGrowthSummary(store, start, end)
                .getSpendingValue(assets::contains, reasons::contains, false);
    }

    @Command(desc = """
            The resource value of the specified assets associated with the provided membership change reasons
            Nations can have multiple membership changes over a duration
            - RECRUITED: 7d or less nation becomes member
            - JOINED: Nation >7d becomes member
            - LEFT: Nation is set to applicant, none, or leaves the alliance (does not include delete/vm)
            - DELETED: nation deletes
            - VM_LEFT: Nation goes into VM
            - VM_RETURNED: Nation leaves VM""")
    public Map<ResourceType, Double> getMembershipChangeAssetRss(ValueStore store, Set<MembershipChangeReason> reasons, Set<GrowthAsset> assets, @Timestamp long start, @Timestamp @Default Long end) {
        return ResourceType.resourcesToMap(getGrowthSummary(store, start, end)
                .getSpending(assets::contains, reasons::contains, false));
    }

    @Command(desc = "The cumulative revenue members have produced over the period, accounting for joins/leaves, radiation, city, building, policy, and project changes")
    public Map<ResourceType, Double> getCumulativeRevenue(ValueStore store, @Timestamp long start, @Timestamp @Default Long end) {
        long turnStart = TimeUtil.getTurn(start);
        long turnEnd = end == null ? TimeUtil.getTurn() : TimeUtil.getTurn(end);
        if (turnEnd - turnStart > 365 * 12) {
            throw new IllegalArgumentException("Cannot calculate cumulative revenue over more than 365 days");
        }
        Map<AllianceMetric, ResourceType> metrics = new EnumMap<>(AllianceMetric.class);

        metrics.put(AllianceMetric.REVENUE_MONEY, ResourceType.MONEY);
        metrics.put(AllianceMetric.REVENUE_FOOD, ResourceType.FOOD);
        metrics.put(AllianceMetric.REVENUE_COAL, ResourceType.COAL);
        metrics.put(AllianceMetric.REVENUE_OIL, ResourceType.OIL);
        metrics.put(AllianceMetric.REVENUE_URANIUM, ResourceType.URANIUM);
        metrics.put(AllianceMetric.REVENUE_LEAD, ResourceType.LEAD);
        metrics.put(AllianceMetric.REVENUE_IRON, ResourceType.IRON);
        metrics.put(AllianceMetric.REVENUE_BAUXITE, ResourceType.BAUXITE);
        metrics.put(AllianceMetric.REVENUE_GASOLINE, ResourceType.GASOLINE);
        metrics.put(AllianceMetric.REVENUE_MUNITIONS, ResourceType.MUNITIONS);
        metrics.put(AllianceMetric.REVENUE_STEEL, ResourceType.STEEL);
        metrics.put(AllianceMetric.REVENUE_ALUMINUM, ResourceType.ALUMINUM);

        Set<AllianceMetric> metricSet = new ObjectOpenHashSet<>(metrics.keySet());
        Map<AllianceMetric, Map<Long, Double>> values = getMetricsAt(store, metricSet, turnStart, turnEnd);
        double[] buffer = new double[ResourceType.values().length];
        for (Map.Entry<AllianceMetric, Map<Long, Double>> entry : values.entrySet()) {
            ResourceType type = metrics.get(entry.getKey());
            for (Map.Entry<Long, Double> entry2 : entry.getValue().entrySet()) {
                buffer[type.ordinal()] += entry2.getValue();
            }
        }
        double factor = 1/12d;
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] *= factor;
        }
        return ResourceType.resourcesToMap(buffer);
    }

    @Command(desc = "The cumulative market value (current prices) of revenue members have produced over the period, accounting for joins/leaves, radiation, city, building, policy, and project changes")
    public double getCumulativeRevenueValue(ValueStore store, @Timestamp long start, @Timestamp @Default Long end) {
        return ResourceType.convertedTotal(getCumulativeRevenue(store, start, end));
    }

    @Command(desc = "Days since the alliance was created (decimal)")
    public double getAgeDays() {
        long now = System.currentTimeMillis();
        return (now - getDateCreated()) / (double) TimeUnit.DAYS.toMillis(1);
    }
}
