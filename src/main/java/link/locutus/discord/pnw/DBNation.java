package link.locutus.discord.pnw;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv2.PoliticsAndWarV2;
import link.locutus.discord.apiv1.entities.BankRecord;
import link.locutus.discord.commands.manager.dummy.DelegateMessage;
import link.locutus.discord.commands.manager.dummy.DelegateMessageEvent;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Activity;
import link.locutus.discord.db.entities.CityInfraLand;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.AttackCost;
import link.locutus.discord.db.entities.WarParser;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.event.NationRegisterEvent;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.battle.BlitzGenerator;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.sheet.SheetUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.task.GetMemberResources;
import link.locutus.discord.util.task.MailTask;
import link.locutus.discord.util.task.balance.GetCityBuilds;
import link.locutus.discord.util.task.multi.GetUid;
import link.locutus.discord.util.trade.Offer;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.util.update.BankUpdateProcessor;
import link.locutus.discord.util.update.NationUpdateProcessor;
import link.locutus.discord.web.jooby.handler.CommandResult;
import link.locutus.discord.web.jooby.handler.DummyMessageOutput;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import link.locutus.discord.apiv1.domains.Nation;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.domains.subdomains.SNationContainer;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.building.ResourceBuilding;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DBNation implements NationOrAlliance {
    // TODO add and track money here
    private String alliance;
    private int nation_id;
    private String nation;
    private String leader;
    private int alliance_id;
    private long last_active;
    private double score;
    private Integer infra;
    private int cities;
    private Integer avg_infra;
    private DomesticPolicy domestic_policy;
    private WarPolicy war_policy;
    private Integer soldiers;
    private Integer tanks;
    private Integer aircraft;
    private Integer ships;
    private Integer missiles;
    private Integer nukes;
    private int vm_turns;
    private NationColor color;
    private int off;
    private int def;
    private volatile Integer spies;
    private Long date;
    private int position;
    private int alliancePosition;
    private Continent continent;
    private long projects;
    private Long cityTimer;
    private Long projectTimer;
    private Long beigeTimer;
    private long espionageFull;
    private DBNationCache cache;

    public static DBNation byId(int nationId) {
        return Locutus.imp().getNationDB().getNation(nationId);
    }

//    public NationResponseProjection createQueryV3() {
//        NationResponseProjection projection = new NationResponseProjection();
//        projection
//    }
//
//    public DBNation fromResponse(com.politicsandwar.graphql.model.Nation nation) {
//        this.nation_id = Integer.parseInt(nation.getId());
//        this.nation = nation.getNation_name();
//        this.leader = nation.getLeader_name();
//        this.alliance_id = Integer.parseInt(nation.getAlliance_id());
//        this.alliance = nation.getAlliance().getName();
//        this.last_active = Instant.parse(nation.getLast_active()).toEpochMilli();
//        this.score = nation.getScore();
////        this.infra = nation.getinfra; // todo cities
//        this.cities = nation.getNum_cities();
////        this.avg_infra = avg_infra; // todo cities
//        this.war_policy = nation.getWar_policy();
//        this.domestic_policy = domPolicy;
//        this.soldiers = soldiers;
//        this.tanks = tanks;
//        this.aircraft = aircraft;
//        this.ships = ships;
//        this.missiles = missiles;
//        this.nukes = nukes;
//        this.vm_turns = vm_turns;
//        this.color = NationColor.valueOf(color.toUpperCase());
//        this.off = off;
//        this.def = def;
//        this.spies = spies;
//        this.date = date;
//        this.alliancePosition = alliancePosition;
//        this.position = position;
//        this.continent = continent;
//        this.projects = projects;
//        this.cityTimer = cityTimer;
//        this.projectTimer = projectTimer;
//        this.beigeTimer = beigeTimer;
//        this.espionageFull = espionageFull;
//
//        return this;
//    }

    public DBNation() {}

    public static final long val = 2L << 29L;

    public DBNation(int nation_id, String nation, String leader, int alliance_id, String alliance, long last_active, double score, Integer infra, int cities, Integer avg_infra, WarPolicy warPolicy, DomesticPolicy domPolicy, Integer soldiers, Integer tanks, Integer aircraft, Integer ships, Integer missiles, Integer nukes, int vm_turns, String color, int off, int def, Integer spies, Long date, int alliancePosition, int position, Continent continent, long projects, Long cityTimer, Long projectTimer, Long beigeTimer, long espionageFull) {
        this.nation_id = nation_id;
        this.nation = nation;
        this.leader = leader;
        this.alliance_id = alliance_id;
        this.alliance = alliance;

        if (last_active > (2 << 29)) {
            this.last_active = last_active;
        } else {
            this.last_active = System.currentTimeMillis() - last_active;
        }
        this.score = score;
        this.infra = infra;
        this.cities = cities;
        this.avg_infra = avg_infra;
        this.war_policy = warPolicy;
        this.domestic_policy = domPolicy;
        this.soldiers = soldiers;
        this.tanks = tanks;
        this.aircraft = aircraft;
        this.ships = ships;
        this.missiles = missiles;
        this.nukes = nukes;
        this.vm_turns = vm_turns;
        this.color = NationColor.valueOf(color.toUpperCase());
        this.off = off;
        this.def = def;
        this.spies = spies;
        this.date = date;
        this.alliancePosition = alliancePosition;
        this.position = position;
        this.continent = continent;
        this.projects = projects;
        this.cityTimer = cityTimer;
        this.projectTimer = projectTimer;
        this.beigeTimer = beigeTimer;
        this.espionageFull = espionageFull;
    }

    public DBNation(DBNation other) {
        this.nation_id = other.nation_id;
        this.nation = other.nation;
        this.leader = other.leader;
        this.alliance_id = other.alliance_id;
        this.alliance = other.alliance;
        this.last_active = other.last_active;
        this.score = other.score;
        this.infra = other.infra;
        this.cities = other.cities;
        this.avg_infra = other.avg_infra;
        this.war_policy = other.war_policy;
        this.soldiers = other.soldiers;
        this.tanks = other.tanks;
        this.aircraft = other.aircraft;
        this.ships = other.ships;
        this.missiles = other.missiles;
        this.nukes = other.nukes;
        this.vm_turns = other.vm_turns;
        this.color = other.color;
        this.off = other.off;
        this.def = other.def;
        this.spies = other.spies;
        this.date = other.date;
        this.alliancePosition = other.alliancePosition;
        this.position = other.position;
        this.continent = other.continent;
        this.projects = other.projects;
        this.cityTimer = other.cityTimer;
        this.projectTimer = other.projectTimer;
        this.beigeTimer = other.beigeTimer;
        this.espionageFull = other.espionageFull;
    }

    public String register(User user, Guild guild, boolean isNewRegistration) {
        if (nation_id == Settings.INSTANCE.NATION_ID) {
            if (Settings.INSTANCE.ADMIN_USER_ID != user.getIdLong()) {
                Settings.INSTANCE.ADMIN_USER_ID = user.getIdLong();
                Settings.INSTANCE.save(Settings.INSTANCE.getDefaultFile());
            }
        }
        new NationRegisterEvent(nation_id, guild, user, isNewRegistration).post();

        StringBuilder output = new StringBuilder();
//        try {
//            String endpoint = "" + Settings.INSTANCE.PNW_URL() + "/api/discord/validateDiscord.php?access_key=%s&nation_id=%s";
//            endpoint = String.format(endpoint, Settings.INSTANCE.DISCORD.ACCESS_KEY, getNation_id());
//
//            String json = FileUtil.readStringFromURL(endpoint);
//            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
//            boolean success = obj.get("success").getAsBoolean();
//
//            if (success) {
//                output.append(obj.get("message").getAsString()).append("\n");
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//            output.append("Error validating your discord: " + e.getMessage() + "\n");
//        }

        output.append("Registration successful. Use `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "?` for a list of commands.\n");
        if (guild != null) {
            Role role = Roles.REGISTERED.toRole(guild);
            if (role != null) {
                try {
                    guild.addRoleToMember(user.getIdLong(), role).complete();

                    output.append("You have been assigned the role: " + role.getName());

                    GuildDB guildDb = Locutus.imp().getGuildDB(guild);
                    Member member = guild.getMember(user);
                    if (member != null && guildDb != null) {
                        guildDb.getAutoRoleTask().autoRole(member, s -> {
                            output.append("\n").append(s);
                        });
                    }
                } catch (InsufficientPermissionException e) {
                    output.append(e.getMessage() + "\n");
                }
            } else {
                if (Roles.ADMIN.has(user, guild)) {
                    output.append("No role mapping found.");
                    output.append("\nCreate a role mapping with `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "aliasrole`");
                }
            }
        }
        return output.toString();
    }

    @Command
    public Long getBeigeTimer() {
        return beigeTimer;
    }

//    public boolean addBalance(GuildDB db, ResourceType type, double amt, String note) {
//        return addBalance(db, Collections.singletonMap(type, amt), note);
//    }
//
//    public boolean addBalance(GuildDB db, Map<ResourceType, Double> transfer, String note) {
//        synchronized (Settings.INSTANCE.BANK_LOCK) {
//            Map<ResourceType, Map<String, Double>> offset = db.getDepositOffset(getNation_id());
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
//                db.setDepositOffset(getNation_id(), rss, newAmount, note);
//                result = true;
//            }
//
//            return result;
//        }
//    }

    @Command
    public boolean hasProject(Project project) {
        if (this.projects == 0) {
            this.projects = 1;
            if (nation_id == 0) return false;
            updateProjects();
        }
        return (this.projects & (1L << project.ordinal() + 1L)) != 0;
    }

    public void setProject(Project project) {
        projects |= 1L << (project.ordinal() + 1L);
    }

    @Command
    public int getNumProjects() {
        return getProjects().size();
    }

    @Command
    public int getFreeProjectSlots() {
        return projectSlots() - getProjects().size();
    }

    public void updateProjects() {
        try {
            Set<Project> previousProjects = new LinkedHashSet<>();
            if (this.projects != 0) previousProjects.addAll(getProjects());
            new GetUid(this).call();
            for (Project project : getProjects()) {
                if (previousProjects.contains(project)) continue;
                this.addExpense(project.cost());
            }
            if (previousProjects.size() < getProjects().size() && cities >= 10) {
                setProjectTimer(TimeUtil.getTurn() + 120);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Command
    public int getNations() {
        return 1;
    }

    /**
     * Entry( value, has data )
     * @return
     */
    public Map.Entry<Double, Boolean> getIntelOpValue() {
        if (active_m() < 4320) return null;
        if (vm_turns > 12) return null;
        if (getActive_m() > 385920) return null;
//        if (!isGray()) return null;
        if (def == 3) return null;
        long currentDate = System.currentTimeMillis();
        long cutoff = currentDate - TimeUnit.DAYS.toMillis(14);

        Map.Entry<Long, double[]> loot = Locutus.imp().getNationDB().getLoot(getNation_id());
        if (loot != null && loot.getKey() > cutoff) return null;
        Map.Entry<Long, double[]> lootHistory = Locutus.imp().getWarDb().getNationLoot(getNation_id(), true).get(getNation_id());
        if (lootHistory != null && lootHistory.getKey() > cutoff) return null;

        long lastLootDate = 0;
        if (loot != null) lastLootDate = Math.max(lastLootDate, loot.getKey());
        if (lootHistory != null) lastLootDate = Math.max(lastLootDate, lootHistory.getKey());
        if (currentDate - active_m() * 60L * 1000L < lastLootDate) return null;

        long checkBankCutoff = currentDate - TimeUnit.DAYS.toMillis(60);
        if (cities > 10 && lastLootDate < checkBankCutoff) {
            List<Transaction2> transactions = getTransactions(Long.MAX_VALUE);
            long recent = 0;
            for (Transaction2 transaction : transactions) {
                if (transaction.receiver_id != getNation_id()) {
                    recent = Math.max(recent, transaction.tx_datetime);
                }
            }
            if (recent > 0) {
                lastLootDate = Math.max(lastLootDate, recent);
            }
        }
        double cityCost = PnwUtil.nextCityCost(cities, true, hasProject(Projects.URBAN_PLANNING), hasProject(Projects.ADVANCED_URBAN_PLANNING));
        double maxStockpile = cityCost * 2;
        double daysToMax = maxStockpile / (avg_infra * cities * 300);
        if (lastLootDate == 0) {
            lastLootDate = currentDate - TimeUnit.DAYS.toMillis((int) daysToMax);
        }

        long diffMin = TimeUnit.MILLISECONDS.toMinutes(currentDate - lastLootDate);

        if (getActive_m() < 12000) {
            diffMin /= 8;
            DBWar lastWar = Locutus.imp().getWarDb().getLastWar(0, nation_id);
            if (lastWar != null) {
                long warDiff = currentDate - TimeUnit.DAYS.toMillis(240);
                if (lastWar.date > warDiff) {
                    double ratio = TimeUnit.MILLISECONDS.toDays(currentDate - lastWar.date) / 240d;
                    if (lastWar.status == WarStatus.PEACE || lastWar.status == WarStatus.DEFENDER_VICTORY) {
                        diffMin *= ratio;
                    }
                }
            }
        }

        double value = getAvg_infra() * (diffMin + getActive_m()) * getCities();

        if (loot == null && lootHistory == null && cities < 12) {
            List<DBWar> wars = Locutus.imp().getWarDb().getWarsByNation(nation_id, nation_id, lastLootDate);
            if (!wars.isEmpty()) {
                WarParser cost = WarParser.of(wars, f -> f.attacker_id == nation_id);
                double total = cost.toWarCost().convertedTotal(true);
                value -= total;
            }
        }

        // value for weak military
        double soldierPct = (double) getSoldiers() / (Buildings.BARRACKS.max() * Buildings.BARRACKS.cap() * getCities());
        double tankPct = (double) getTanks() / (Buildings.FACTORY.max() * Buildings.FACTORY.cap() * getCities());
        value = value + value * (2 - soldierPct - tankPct);

        return new AbstractMap.SimpleEntry<>(value, loot != null || lootHistory != null);
    }

    @Command
    @RolePermission(Roles.MEMBER)
    public int allianceSeniority() {
        if (alliance_id == 0) return 0;
        long timestamp = Locutus.imp().getNationDB().getAllianceMemberSeniorityTimestamp(this);
        long now = System.currentTimeMillis();
        if (timestamp > now) return 0;

        return (int) TimeUnit.MILLISECONDS.toDays(now - timestamp);
    }

    @Command(desc="Military strength (1 plane = 1)")
    public double getStrength() {
        return BlitzGenerator.getAirStrength(this, true);
    }

    @Command(desc = "Estimated combined strength of the enemies its fighting")
    public double getEnemyStrength() {
        if (getOff() == 0 && getDef() == 0) return 0;
        List<DBWar> wars = getActiveWars();
        if (wars.isEmpty()) {
            return 0;
        }
        double totalStr = 0;
        int numWars = 0;
        for (DBWar war : wars) {
            DBNation other = war.getNation(!war.isAttacker(this));
            if (other == null || other.hasUnsetMil()) continue;
            numWars++;
            totalStr += Math.pow(BlitzGenerator.getAirStrength(other, true), 3);
        }
        totalStr = Math.pow(totalStr / numWars, 1 / 3d);

        return totalStr;
    }

    @Command(desc="Minimum resistance of self in current active wars")
    public int minWarResistance() {
        if (getNumWars() == 0) return 100;
        int min = 100;
        for (DBWar war : getActiveWars()) {
            List<DBAttack> attacks = war.getAttacks();

            Map.Entry<Integer, Integer> warRes = war.getResistance(attacks);
            int myRes = war.isAttacker(this) ? warRes.getKey() : warRes.getValue();
            if (myRes < min) min = myRes;
        }
        return min;
    }

    @Command(desc="Minimum resistance of self in current active wars, assuming the enemy translates their MAP into ground/naval with guaranteed IT")
    public int minWarResistancePlusMap() {
        if (getNumWars() == 0) return 100;
        int min = 100;
        for (DBWar war : getActiveWars()) {
            List<DBAttack> attacks = war.getAttacks();

            boolean isAttacker = war.isAttacker(this);

            Map.Entry<Integer, Integer> warRes = war.getResistance(attacks);
            Map.Entry<Integer, Integer> warMap = war.getMap(attacks);
            int myRes = isAttacker ? warRes.getKey() : warRes.getValue();
            int enemyMap = !isAttacker ? warMap.getKey() : warMap.getValue();

            switch (enemyMap) {
                case 12:
                    myRes -= 42;
                    break;
                case 11:
                case 10:
                case 9:
                    myRes -= 30;
                    break;
                case 8:
                    myRes -= 28;
                    break;
                case 7:
                    myRes -= 24;
                    break;
                case 6:
                    myRes -= 20;
                    break;
                case 5:
                case 4:
                    myRes -= 14;
                    break;
                case 3:
                    myRes -= 10;
                    break;
            }
            if (myRes < min) min = myRes;
        }
        return min;
    }

    @Command(desc = "Relative strength compared to enemies its fighting (1 = equal)")
    public double getRelativeStrength() {
        return getRelativeStrength(true);
    }

    public double getRelativeStrength(boolean inactiveIsLoss) {
        if (getActive_m() > 2440 && inactiveIsLoss) return 0;

        double myStr = getStrength();
        double enemyStr = getEnemyStrength();
        double otherMin = BlitzGenerator.getBaseStrength(cities);
        enemyStr = Math.max(otherMin, enemyStr);

        return myStr / enemyStr;
    }

    public void addExpense(Map<ResourceType, Double> cost) {
        // TODO add expense
    }

    @Command(desc = "Set of projects this nation has")
    @RolePermission(Roles.MEMBER)
    public Set<Project> getProjects() {
        Set<Project> set = null;
        for (Project value : Projects.values) {
            if (hasProject(value)) {
                if (set == null) set = new LinkedHashSet<>();
                set.add(value);
            }
        }
        return set == null ? Collections.EMPTY_SET : set;
    }

    public Auth auth = null;

    public String setTaxBracket(TaxBracket bracket, Auth auth) {
        if (bracket.allianceId != alliance_id) throw new UnsupportedOperationException("Not in alliance");

        Map<String, String> post = new HashMap<>();
        post.put("bracket_id", "" + bracket.taxId);
        post.put("change_member_bracket", "Update Nation's Bracket");
        post.put("nation_id", getNation_id() + "");
        String url = String.format("" + Settings.INSTANCE.PNW_URL() + "/alliance/id=%s&display=taxes", alliance_id);

        return PnwUtil.withLogin(() -> {
            String token = auth.getToken(url);
            post.put("token", token);

            StringBuilder response = new StringBuilder();

            String result = auth.readStringFromURL(url, post);
            Document dom = Jsoup.parse(result);
            int alerts = 0;
            for (Element element : dom.getElementsByClass("alert")) {
                String text = element.text();
                if (text.startsWith("Player Advertisement by ")) {
                    continue;
                }
                alerts++;
                response.append('\n').append(element.text());
            }
            if (alerts == 0) {
                response.append('\n').append("Set ." + getNation() + " to " + bracket.taxId);
            }
            if (response.toString().contains("You moved")) {
                GuildDB db = Locutus.imp().getGuildDBByAA(alliance_id);
                User user = getUser();
                if (db != null && user != null) {
                    Member member = db.getGuild().getMember(user);
                    if (member != null) {
                        db.getAutoRoleTask().updateTaxRole(member, bracket);
                    }
                }
            }
            return response.toString();
        }, auth);
    }

    public Auth getAuth() {
        return getAuth(Roles.ADMIN);
    }

    public Auth getAuth(Roles role) {
        if (this.auth != null && !this.auth.isValid()) this.auth = null;
        if (this.auth != null) return auth;
        if (auth == null) {
            synchronized (this) {
                if (auth == null) {
                    if (this.nation_id == Settings.INSTANCE.NATION_ID) {
                        if (!Settings.INSTANCE.USERNAME.isEmpty() && !Settings.INSTANCE.PASSWORD.isEmpty()) {
                            return auth = new Auth(nation_id, Settings.INSTANCE.USERNAME, Settings.INSTANCE.PASSWORD);
                        }
                    }

                    PNWUser dbUser = getDBUser();
                    if (dbUser != null && dbUser.getDiscordId() != null) {
                        Map.Entry<String, String> pass = Locutus.imp().getDiscordDB().getUserPass(dbUser.getDiscordId());
                        if (pass == null) {
                            User user = dbUser.getUser();
                            if (user != null) {
                                if (role != null && role.hasOnRoot(user)) {
                                    return Locutus.imp().getRootAuth();
                                }
                            }
                        }
                        if (pass != null) {
                            auth = new Auth(nation_id, pass.getKey(), pass.getValue());
                        }
                    }

                    if (role != null) {
                        User user = getUser();
                        if (user != null && role.hasOnRoot(user)) {
                            return Locutus.imp().getRootAuth();
                        }
                    }
                }
            }
        }
        if (auth == null) {
            throw new IllegalArgumentException("Please authenticate using `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "login`");
        }
        return auth;
    }

    public DBNation(String coalition, Collection<DBNation> nations, boolean average) {
        this.nation_id = -1;
        this.nation = coalition;
        this.leader = null;
        this.alliance_id = 0;
        this.alliance = null;

        this.infra = 0;
        this.avg_infra = 0;
        this.soldiers = 0;
        this.tanks = 0;
        this.aircraft = 0;
        this.ships = 0;
        this.missiles = 0;
        this.nukes = 0;
        this.spies = 0;
        this.date = 0L;

        int numDate = 0;

        for (DBNation other : nations) {
            this.last_active += cast(other.last_active).longValue();
            this.score += cast(other.score).intValue();
            this.infra += cast(other.infra).intValue();
            this.cities += cast(other.cities).intValue();
            this.avg_infra += cast(other.avg_infra).intValue();
            this.soldiers += cast(other.soldiers).intValue();
            this.tanks += cast(other.tanks).intValue();
            this.aircraft += cast(other.aircraft).intValue();
            this.ships += cast(other.ships).intValue();
            this.missiles += cast(other.missiles).intValue();
            this.nukes += cast(other.nukes).intValue();
            this.vm_turns += cast(other.vm_turns).intValue();
            this.off += cast(other.off).intValue();
            this.def += cast(other.def).intValue();
            this.spies += cast(other.spies).intValue();
            if (other.date != null && other.date != 0) {
                numDate++;
                this.date += cast(other.date).longValue();
            }
        }
        if (average) {
            this.last_active /= nations.size();
            this.score /= nations.size();
            this.infra /= nations.size();
            this.cities /= nations.size();
            this.avg_infra /= nations.size();
            this.soldiers /= nations.size();
            this.tanks /= nations.size();
            this.aircraft /= nations.size();
            this.ships /= nations.size();
            this.missiles /= nations.size();
            this.nukes /= nations.size();
            this.vm_turns /= nations.size();
            this.off /= nations.size();
            this.def /= nations.size();
//            this.money /= nations.size();
            this.spies /= nations.size();
            if (numDate != 0) this.date /= numDate;
        } else {
            long diffAvg = this.last_active / nations.size();
            last_active = System.currentTimeMillis() - ((System.currentTimeMillis() - diffAvg) * nations.size());
        }
    }

    @Command
    public double getRads() {
        TradeManager manager = Locutus.imp().getTradeManager();
        double radIndex = manager.getGlobalRadiation() + manager.getGlobalRadiation(getContinent());
        return (1 + (radIndex / (-1000)));
    }

    @Command(desc = "Raw positional value (0 = remove, 1 = app, 2 = member, 3 = officer 4 = heir, 5 = leader)")
    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int getAlliancePosition() {
        return alliancePosition;
    }

    public void setAlliancePosition(int position) {
        this.alliancePosition = position;
    }

    public Continent getContinent() {
        return continent;
    }

    public void setContinent(Continent continent) {
        this.continent = continent;
    }

    @Command(desc = "Unix timestamp when project timer expires")
    public Long getProjectTimerEpoch() {
        return projectTimer;
    }

    @Command(desc = "Unix timestamp when city timer expires")
    public Long getCityTimerEpoch() {
        return cityTimer;
    }

    public DBNation(SNationContainer wrapper) {
        this.nation = wrapper.getNation();
        this.nation_id = wrapper.getNationid();

        this.leader = wrapper.getLeader();

        this.alliance = wrapper.getAlliance();
        this.alliance_id = wrapper.getAllianceid();

        this.last_active = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(wrapper.getMinutessinceactive());

        this.score = wrapper.getScore();
        this.cities = wrapper.getCities();
        this.war_policy = WarPolicy.parse(wrapper.getWarPolicy());
        Integer vm = wrapper.getVacmode();
        vm_turns = vm == null ? 0 : vm;
        this.color = NationColor.valueOf(wrapper.getColor().toUpperCase());
        this.off = wrapper.getOffensivewars();
        this.def = wrapper.getDefensivewars();

        this.infra = wrapper.getInfrastructure().intValue();
        if (wrapper.getCities() == 0) {
            System.out.println("!!! invalid nation " + this.getNation_id());
        } else {
            this.avg_infra = this.infra / wrapper.getCities();
        }

        this.position = wrapper.getAllianceposition();
        this.continent = Continent.valueOf(wrapper.getContinent().toUpperCase().replace(" ", "_"));
    }

    private Number cast(Number t) {
        return t == null ? (Number) 0 : t;
    }

    @Command
    public double getGroundStrength(boolean munitions, boolean enemyAc) {
        return soldiers * (munitions ? 1.75 : 1) + (tanks * 40) * (enemyAc ? 0.66 : 1);
    }

    public double getGroundStrength(boolean munitions, boolean enemyAc, double includeRebuy) {
        int soldiers = this.soldiers;
        int tanks = this.tanks;
        if (includeRebuy > 0) {
            int barracks = Buildings.BARRACKS.cap() * cities;
            int soldierMax = Buildings.BARRACKS.max() * barracks;
            int soldPerDay = barracks * Buildings.BARRACKS.perDay();

            soldiers = Math.min(soldierMax, (int) (soldiers + soldPerDay * includeRebuy));

            int factories = Buildings.FACTORY.cap() * cities;
            int tankMax = Buildings.FACTORY.max() * barracks;
            int tankPerDay = barracks * Buildings.FACTORY.perDay();

            tanks = Math.min(tankMax, (int) (tanks + tankPerDay * includeRebuy));
        }
        return soldiers * (munitions ? 1.75 : 1) + (tanks * 40) * (enemyAc ? 0.66 : 1);
    }

    public Integer updateSpies() {
        return updateSpies(false);
    }

    public Integer updateSpies(boolean update, boolean force) {
        if (!update) {
            if (spies != null) {
                return spies;
            }
        }
        return updateSpies(force);
    }

    public Integer updateSpies(boolean force) {
        ByteBuffer lastTurn = getMeta(NationMeta.UPDATE_SPIES);
        long currentTurn = TimeUtil.getTurn();

        if (lastTurn == null ||  lastTurn.getLong() != currentTurn || force) {
            try {
                Integer oldSpies = spies;
                spies = SpyCount.guessSpyCount(this);
                if (oldSpies == null || !oldSpies.equals(spies)) {
                    Locutus.imp().getNationDB().addNation(this);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (spies == null) return 0;
        return spies;
    }

    public void setMeta(NationMeta key, byte value) {
        setMeta(key, new byte[] {value});
    }

    public void setMeta(NationMeta key, int value) {
        setMeta(key, ByteBuffer.allocate(4).putInt(value).array());
    }

    public void setMeta(NationMeta key, long value) {
        setMeta(key, ByteBuffer.allocate(8).putLong(value).array());
    }

    public void setMeta(NationMeta key, double value) {
        setMeta(key, ByteBuffer.allocate(8).putDouble(value).array());
    }

    public void setMeta(NationMeta key, String value) {
        setMeta(key, value.getBytes(StandardCharsets.ISO_8859_1));
    }

    public void setMeta(NationMeta key, byte... value) {
        Locutus.imp().getNationDB().setMeta(nation_id, key, value);
//        Locutus.imp().getDiscordDB().setInfo(key + "." + getNation_id(), new String(value));
//        Locutus.imp().getDiscordDB().flush();
    }

    public ByteBuffer getMeta(NationMeta key) {
        byte[] result = Locutus.imp().getNationDB().getMeta(nation_id, key);
        return result == null ? null : ByteBuffer.wrap(result);
    }

    public String setRank(Auth auth, Rank rank) {
        String url = String.format("" + Settings.INSTANCE.PNW_URL() + "/alliance/id=%s#permissions", auth.getAllianceId());

        Map<String, String> post = new HashMap<>();
        post.put("nationperm", getLeader());
        post.put("level", String.valueOf(rank.id));
        post.put("permsubmit", "Go");

        return PnwUtil.withLogin(() -> {
            StringBuilder response = new StringBuilder();

            String result = auth.readStringFromURL(url, post);
            Document dom = Jsoup.parse(result);
            int alerts = 0;
            for (Element element : dom.getElementsByClass("alert")) {
                String text = element.text();
                if (text.startsWith("Player Advertisement by ")) {
                    continue;
                }
                alerts++;
                response.append('\n').append(element.text());
            }
            if (alerts == 0) {
                response.append('\n').append("Set player rank ingame. Remember to also set the rank on discord.");
            }

            return response.toString().trim();
        }, auth);
    }

    public void deleteMeta(NationMeta key) {
        Locutus.imp().getNationDB().deleteMeta(nation_id, key);
    }

    @Command(desc = "Last fetched spy count")
    @RolePermission(value = Roles.MILCOM)
    public Integer getSpies() {
        return spies;
    }

    public void setSpies(Integer spies) {
        this.spies = spies;
    }

    public double[] getNetDeposits(GuildDB db) throws IOException {
        return getNetDeposits(db, 0L);
    }

    public double[] getNetDeposits(GuildDB db, boolean includeGrants) throws IOException {
        return getNetDeposits(db, null, true, true, includeGrants, 0L, 0L);
    }

    public double[] getNetDeposits(GuildDB db, boolean includeGrants, long updateThreshold) throws IOException {
        return getNetDeposits(db, null, true, true, includeGrants, updateThreshold, 0L);
    }

    public double[] getNetDeposits(GuildDB db, long updateThreshold) throws IOException {
        return getNetDeposits(db, null, true, true, updateThreshold, 0L);
    }

    public double[] getNetDeposits(GuildDB db, Set<Long> tracked, boolean useTaxBase, boolean offset, long updateThreshold, long cutOff) throws IOException {
        return getNetDeposits(db, tracked, useTaxBase, offset, true, updateThreshold, cutOff);
    }

    public double[] getNetDeposits(GuildDB db, Set<Long> tracked, boolean useTaxBase, boolean offset, boolean includeGrants, long updateThreshold, long cutOff) throws IOException {
        long start = System.currentTimeMillis();
        Map<DepositType, double[]> result = getDeposits(db, tracked, useTaxBase, offset, updateThreshold, cutOff);
        double[] total = new double[ResourceType.values.length];
        for (Map.Entry<DepositType, double[]> entry : result.entrySet()) {
            if (includeGrants || entry.getKey() != DepositType.GRANT) {
                double[] value = entry.getValue();
                for (int i = 0; i < value.length; i++) total[i] += value[i];
            }
        }
        return total;
    }

    public double getNetDepositsConverted(GuildDB db) throws IOException {
        return getNetDepositsConverted(db, 0);
    }

    public double getNetDepositsConverted(GuildDB db, long updateThreshold) throws IOException {
        return PnwUtil.convertedTotal(getNetDeposits(db, updateThreshold));
    }

    public List<Transaction2> getTransactions() {
        return getTransactions(0);
    }

    public List<Transaction2> updateTransactions() {
        PoliticsAndWarV2 apiV2 = Locutus.imp().getBankApi();
        List<BankRecord> records = apiV2.getBankRecords(nation_id);
        List<Transaction2> records2 = new ArrayList<>();
        for (BankRecord record : records) {
            records2.add(new Transaction2(record));
        }
        setMeta(NationMeta.LAST_BANK_UPDATE, System.currentTimeMillis());

        if (records.isEmpty()) {
            return Collections.emptyList();
        }

        int existing = Locutus.imp().getBankDB().getTransactionsByNationCount(nation_id);

        if (existing != records.size()) {
            Locutus.imp().getBankDB().addTransactions(records2);
            for (int i = existing; i < records2.size(); i++) {
                BankUpdateProcessor.process(records2.get(i));
            }
        } else { // Legacy fix
            List<Transaction2> toFix = new ArrayList<>();
            for (Transaction2 record2 : records2) {
                int rssInt = (int) (record2.resources[0] * 100);
                if (rssInt == 2147483647) {
                    toFix.add(record2);
                }
            }
            Locutus.imp().getBankDB().addTransactions(toFix);
        }
        return records2;
    }

    @Command
    public long daysSince3ConsecutiveLogins() {
        return daysSinceConsecutiveLogins(1200, 3);
    }

    @Command
    public long daysSince4ConsecutiveLogins() {
        return daysSinceConsecutiveLogins(1200, 4);
    }

    @Command
    public long daysSince5ConsecutiveLogins() {
        return daysSinceConsecutiveLogins(1200, 5);
    }

    @Command
    public long daysSince6ConsecutiveLogins() {
        return daysSinceConsecutiveLogins(1200, 6);
    }

    @Command
    public long daysSince7ConsecutiveLogins() {
        return daysSinceConsecutiveLogins(1200, 7);
    }

    @Command
    public long daysSinceConsecutiveLogins(long days, int required) {
        long currentDay = TimeUtil.getDay();
        long turns = days * 12 + 11;
        List<Long> logins = new ArrayList<>(Locutus.imp().getNationDB().getActivityByDay(nation_id, TimeUtil.getTurn() - turns));
        Collections.reverse(logins);
        int tally = 0;
        long last = 0;
        for (long day : logins) {
            if (day == last - 1) {
                tally++;
            } else {
                tally = 0;
            }
            if (tally >= required) return currentDay - day;
            last = day;
        }
        return Long.MAX_VALUE;
    }

    @Command
    public double daysSinceLastBankDeposit() {
        return (System.currentTimeMillis() - lastBankDeposit()) / (double) TimeUnit.DAYS.toMillis(1);
    }

    @Command(desc = "Unix timestamp when last depossited in a bank (cached)")
    public long lastBankDeposit() {
        if (position <= 1) return 0;
        List<Transaction2> transactions = getTransactions(Long.MAX_VALUE);
        long max = 0;
        for (Transaction2 transaction : transactions) {
            if (transaction.receiver_id == alliance_id && transaction.isReceiverAA()) max = Math.max(max, transaction.tx_datetime);
        }
        return max;
    }

    /**
     * get transactions
     * @param updateThreshold = -1 is no update, 0 = always update
     * @return
     */
    public List<Transaction2> getTransactions(long updateThreshold) {
        ByteBuffer lastUpdateMeta = getMeta(NationMeta.LAST_BANK_UPDATE);
        if (lastUpdateMeta == null) {
            return updateTransactions();
        }
        if (updateThreshold >= 0) {
            long lastUpdate = lastUpdateMeta == null ? 0 : lastUpdateMeta.getLong();
            long active_ms = TimeUnit.MINUTES.toMillis(5 + getActive_m());
            if (active_ms > updateThreshold || updateThreshold == 0) {
                long lastActive = System.currentTimeMillis() - active_ms;
                if (lastActive > lastUpdate || updateThreshold == 0) {
                    Map<Integer, Map.Entry<Long, Rank>> history = getAllianceHistory();
                    // check alliance history
//                    history
                    return updateTransactions();
                }
            }
        }
        return Locutus.imp().getBankDB().getTransactionsByNation(nation_id);
    }

    @Deprecated
    public Map.Entry<String, String> generateRecruitmentMessage(boolean force) throws InterruptedException, ExecutionException, IOException {
        StringBuilder body = new StringBuilder();
        Map<Integer, JavaCity> cities = new GetCityBuilds(this).adapt().get(this);
        body.append("Hey hey! I'm Danzek. If you would like any help, feel free to ask me here or on discord! :D<br>" +
                "Here are some beginner tips for you<hr><br>");
        if (cities.size() == 1) {
            body.append("<a href=\"https://politicsandwar.com/nation/objectives/\" class=\"btn btn-warning\"><i class=\"fas fa-book\" aria-hidden=\"true\"></i> Open Objectives</a>");
            body.append("<p>The tutorial runs you through some of the basics, and gives you some cash right away. Let me know if you need any assistance with them:</p>");
            body.append("<hr><br>");
        }
        if (getOff() != 5) {
//                String raidUrl = "https://politicsandwar.com/index.php?id=15&keyword=" + current.getScore() + "&cat=war_range&ob=date&od=DESC&maximum=50&minimum=0&search=Go&beige=true&vmode=false&aligned=true&openslots=true";
//                body.append("The quickest way to make money for new nations is to raid inactive nations (rather than waiting on your cities to generate revenue)\n");
//                body.append(" - You can use the nation search to find enemies: " + raidUrl + " (enter your nation's score, currently " + current.getScore() + " as the war range search term)\n");

            body.append("<p>The quickest way to make money is to raid:</p>");
            body.append("<ul>" +
                    "<li>Go to the <a href=\"https://politicsandwar.com/nation/war/\">War Page</a> and click `Find Nations in War Range`</li>" +
                    "<li>Attack up to 5 inactive nations (purple diamond next to them)</li>" +
                    "<li>Inactive people don't fight back</li>" +
                    "<li>take a portion of their resources when defeated</li>" +
                    "</ul>");
            body.append("<a href=\"https://politicsandwar.com/index.php?id=15&keyword=" + score + "&cat=war_range&ob=cities&od=ASC&maximum=50&minimum=0&search=Go&beige=true&vmode=false&aligned=true&openslots=true\" class=\"btn btn-warning\"><i class=\"fas fa-chess-rook\" aria-hidden=\"true\"></i> Find War Targets</a>");
            body.append("<a href=\"https://docs.google.com/document/d/1iuKHpSSQ9qYGFaKeE4Rv1lixyZcr6eO1ZTLyGJJLdd4/\" class=\"btn btn-warning\"><i class=\"fas fa-book\" aria-hidden=\"true\"></i> In Depth Raiding Guide</a>");
            body.append("<hr><br>");
        }

//            if (current.isBeige() || current.getColor().equalsIgnoreCase("grey")) {
//                body.append(" - You can go to https://politicsandwar.com/nation/edit/ and change your trade color to e.g. Green")
//            }
        boolean hasCityTip = false;
        int i = 0;
        for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
            String title = "City " + (++i);
            String url = "https://politicsandwar.com/city/id=" + entry.getKey();
            JavaCity city = entry.getValue();

            List<String> cityCompliance = new ArrayList<>();

            if (!city.getPowered(this::hasProject)) {
                cityCompliance.add("Is not powered. Make sure you have enough power plants and fuel. You can buy fuel from the trade page.");
            }
            if (city.get(Buildings.FARM) != 0) {
                cityCompliance.add("Has farms, which are not very profitable. It is better buying food from the trade page");
            }
            if (city.get(Buildings.BARRACKS) != 5) {
                cityCompliance.add("Doesn't have 5 barracks. Soldiers are a good cheap unit that are useful for raiding and protecting your nation.");
            }
            if (city.get(Buildings.NUCLEAR_POWER) == 0) {
                cityCompliance.add("Nuclear power can be expensive, but is a worthwhile investment. It provides power for more levels of infrastructure and does not pollute. (Pollution reduces your population)");
            }
            if (city.getFreeSlots() > 0) {
                cityCompliance.add("Once you have power and barracks, you can fill the remaining slots with e.g. mines, which you can sell on the market");
            }

            if (!cityCompliance.isEmpty()) {
                //
                // <a href="https://discord.gg/rpmvjWr" class="btn btn-default"><img style='width:16px;height:16px' src="https://discord.com/assets/07dca80a102d4149e9736d4b162cff6f.ico">Applicants/Members</a>
                String button = "<a href=\"" + url + "\" class=\"btn btn-warning\"><i class=\"fas fa-building\" aria-hidden=\"true\"></i> " + title + "</a>";
                body.append(button);
                String list = "<ul><li>" + StringMan.join(cityCompliance, "</li><li>") + "</li></ul>";
                body.append(list);
                hasCityTip = true;
            }
        }
        if (hasCityTip) {
            body.append("<hr><br>");
        }
        {
            body.append("<p><b>Making friends</b><br>" +
                    "The game is more fun if you aren't alone. It's good to have friends or a community of experienced players that can help you in the game, and protect each other from unsolicitied attacks.<br>" +
                    "So... heyo! Hit me up on discord :P"

            );
        }

        String bodyTmp = body.toString();
        body = new StringBuilder();
        body.append("<div class=\"col-xs-12 col-sm-12\" style=\"font-family: Merriweather, Helvetica;background-color:#EEE;background-image:url(https://www.toptal.com/designers/subtlepatterns/patterns/stripes-light.png);background-repeat:repeat;color:#111;padding:3px;border:6px solid #CCC\">");
        body.append(bodyTmp);
        body.append("</div>");

        body.append("<br><div class=\"btn-group btn-group-justified col-xs-12 col-sm-12\">" +
                "<a href=\"https://discord.gg/7xQPVVAsWP\" class=\"btn btn-default\"><i class=\"fab fa-discord\"></i> Join me on Discord</a>" +
                "</div>");

        String subject = getNation() + ": tips for getting started";
        String content = body.toString().replaceAll("\n", "");
        return new AbstractMap.SimpleEntry<>(subject, content);
    }

    public Map<ResourceType, Double> getResourcesNeeded(Map<ResourceType, Double> stockpile, double days, boolean force) {
        Map<Integer, JavaCity> cityMap = getCityMap(force);
        Map<Integer, JavaCity> citiesNoRaws = new HashMap<>();

        for (Map.Entry<Integer, JavaCity> cityEntry : cityMap.entrySet()) {
            JavaCity city = new JavaCity(cityEntry.getValue());
            city.set(Buildings.FARM, 0);
            for (Building building : Buildings.values()) {
                if (building instanceof ResourceBuilding) {
                    ResourceBuilding rssBuilding = (ResourceBuilding) building;
                    ResourceType rss = rssBuilding.resource();
                    if (rss.isRaw()) {
                        city.set(building, 0);
                    }
                }
            }
            citiesNoRaws.put(cityEntry.getKey(), city);
        }

        double[] daily = PnwUtil.getRevenue(null, this, cityMap, true, true, true);
        double[] turn = PnwUtil.getRevenue(null, this, citiesNoRaws, true, true, true);
        turn[0] = Math.min(daily[0], turn[0]);
        for (int i = 0; i < turn.length; i++) {
            turn[i] /= 12d;
        }

        Map<ResourceType, Double> profit = PnwUtil.resourcesToMap(daily);
        Map<ResourceType, Double> profitDays = PnwUtil.multiply(profit, (double) days);
        Map<ResourceType, Double> toSendNation = new HashMap<>();
        Map<ResourceType, Double> minResources = PnwUtil.resourcesToMap(turn);
        for (ResourceType type : ResourceType.values) {
            double current = stockpile.getOrDefault(type, 0d);
            double required = Math.min(minResources.getOrDefault(type, 0d), profitDays.getOrDefault(type, 0d));
            double toSend = current + required;
            if (toSend < 0) toSendNation.put(type, -toSend);
        }
        return toSendNation;
    }

    public Map<ResourceType, Double> getStockpile() throws IOException {
        if (position <= 1) return new HashMap<>();
        Integer alliance = getAlliance_id();
        Map<ResourceType, Double> totals = new GetMemberResources(alliance).call().get(getNation_id());
        return totals;
    }

    public String commend(boolean isCommend) throws IOException {
        String url = "https://politicsandwar.com/api/denouncements.php";
        Map<String, String> post = new HashMap<>();

        if (isCommend) {
            post.put("action", "commendment");
        } else {
            post.put("action", "denouncement");
        }
        post.put("account_id", Settings.INSTANCE.NATION_ID + "");
        post.put("target_id", getNation_id() + "");
        String key = Locutus.imp().getRootPnwApi().getApiKeyUsageStats().entrySet().iterator().next().getKey();
        post.put("api_key", key);
        Locutus.imp().getRootAuth().readStringFromURL(url, post);

        String actionStr = isCommend ? "commended" : "denounced";
        return "Borg has publicly " + actionStr +" the nation of " + getNation() + " led by " + getLeader()+ ".";
    }

    /**
     * process needs to do the following
     *  - check that the receiver / sender is a tracked bank
     *  - process tax base rates
     * @param db
     * @param tracked null if using alliance defaults
     * @param updateThreshold use 0l for force update
     * @return
     */
    public Map<DepositType, double[]> getDeposits(GuildDB db, Set<Long> tracked, boolean useTaxBase, boolean offset, long updateThreshold, long cutOff) {
        long start = System.currentTimeMillis();
        List<Map.Entry<Integer, Transaction2>> transactions = getTransactions(db, tracked, useTaxBase, offset, updateThreshold, cutOff);
        Map<DepositType, double[]> sum = PnwUtil.sumNationTransactions(db, tracked, transactions);
        return sum;
    }

    public List<Map.Entry<Integer, Transaction2>> getTransactions(GuildDB db, Set<Long> tracked, boolean useTaxBase, boolean offset, long updateThreshold, long cutOff) {
        long start = System.currentTimeMillis();
        if (tracked == null) {
            tracked = db.getTrackedBanks();
//        } else {
//            tracked = PnwUtil.expandCoalition(tracked);
        }

        List<Transaction2> transactions = new ArrayList<>();
        if (offset) {
            List<Transaction2> offsets = db.getDepositOffsetTransactions(getNation_id());
            transactions.addAll(offsets);
        }

        List<BankDB.TaxDeposit> taxes = Locutus.imp().getBankDB().getTaxesPaid(getNation_id());

        Set<Long> finalTracked = tracked;
        taxes.removeIf(f -> !finalTracked.contains((long) f.allianceId));
        int[] defTaxBase = new int[]{100, 100};
        if (useTaxBase) {
            int[] defTaxBaseTmp = db.getOrNull(GuildDB.Key.TAX_BASE);
            if (defTaxBaseTmp != null) defTaxBase = defTaxBaseTmp;
//            defTaxBase[0] = myTaxRate.money >= 0 ? 100 : myTaxRate.money;
//            defTaxBase[1] = myTaxRate.resources >= 0 ? 100 : myTaxRate.resources;
        } else {
            defTaxBase = new int[]{0, 0};
        }

        for (BankDB.TaxDeposit deposit : taxes) {
            if (deposit.date < cutOff) continue;
            int internalMoneyRate = useTaxBase ? deposit.internalMoneyRate : 0;
            int internalResourceRate = useTaxBase ? deposit.internalResourceRate : 0;
            if (internalMoneyRate < 0 || internalMoneyRate > 100) internalMoneyRate = defTaxBase[0];
            if (internalResourceRate < 0 || internalResourceRate > 100) internalResourceRate = defTaxBase[1];

            double pctMoney = (deposit.moneyRate > internalMoneyRate ?
                    Math.max(0, (deposit.moneyRate - internalMoneyRate) / (double) deposit.moneyRate)
                    : 0);
            double pctRss = (deposit.resourceRate > internalResourceRate ?
                    Math.max(0, (deposit.resourceRate - internalResourceRate) / (double) deposit.resourceRate)
                    : 0);

            deposit.resources[0] *= pctMoney;
            for (int i = 1; i < deposit.resources.length; i++) {
                deposit.resources[i] *= pctRss;
            }
            Transaction2 transaction = new Transaction2(deposit);
            transactions.add(transaction);
        }

        List<Transaction2> records = getTransactions(updateThreshold);
        transactions.addAll(records);

        List<Map.Entry<Integer, Transaction2>> result = new LinkedList<>();

        outer:
        for (Transaction2 record : transactions) {
            if (record.tx_datetime < cutOff) continue;

            Long otherId = null;

            if (((record.isSenderGuild() || record.isSenderAA()) && tracked.contains(record.sender_id))
                    || (record.sender_type == 0 && record.sender_id == 0 && record.tx_id == -1)) {
                otherId = record.sender_id;
            } else if (((record.isReceiverGuild() || record.isReceiverAA()) && tracked.contains(record.receiver_id))
                    || (record.receiver_type == 0 && record.receiver_id == 0 && record.tx_id == -1)) {
                otherId = record.receiver_id;
            }

            if (otherId == null) continue;

            int sign;
            if (record.sender_id == nation_id && record.sender_type == 1) {
                sign = 1;
            } else {
                sign = -1;
            }

            result.add(new AbstractMap.SimpleEntry<>(sign, record));
        }

        return result;
    }

    @Command
    public int getNation_id() {
        return nation_id;
    }

    public void setNation_id(int nation_id) {
        this.nation_id = nation_id;
    }

    @Command
    public String getNation() {
        return nation;
    }

    public void setNation(String nation) {
        this.nation = nation;
    }

    @Command
    public String getLeader() {
        return leader;
    }

    public void setLeader(String leader) {
        this.leader = leader;
    }

    @Command
    public int getAlliance_id() {
        return alliance_id;
    }

    public void setAlliance_id(int alliance_id) {
        this.alliance_id = alliance_id;
    }

    @Command
    public String getAlliance() {
        return alliance;
    }

    public void setAlliance(String alliance) {
        this.alliance = alliance;
    }

    @Command(desc = "Minutes since last active ingame")
    public int getActive_m() {
        return active_m();
    }

    public void setActive_m(int active_m) {
        this.last_active = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(active_m);
    }

    @Command
    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    @Command(desc = "Total infra in all cities")
    public Integer getInfra() {
        return infra;
    }

    /*
    A map of resource projects, and whether they are producing it
     */
    public Map<Project, Boolean> resourcesProducedProjects() {
        Map<Project, Boolean> manufacturing = new LinkedHashMap();
        for (Map.Entry<Integer, JavaCity> entry : getCityMap(false).entrySet()) {
            JavaCity city = entry.getValue();
            if (city.get(Buildings.GAS_REFINERY) > 0)
                manufacturing.put(Projects.EMERGENCY_GASOLINE_RESERVE, hasProject(Projects.EMERGENCY_GASOLINE_RESERVE));
            if (city.get(Buildings.STEEL_MILL) > 0)
                manufacturing.put(Projects.IRON_WORKS, hasProject(Projects.IRON_WORKS));
            if (city.get(Buildings.ALUMINUM_REFINERY) > 0)
                manufacturing.put(Projects.BAUXITEWORKS, hasProject(Projects.BAUXITEWORKS));
            if (city.get(Buildings.MUNITIONS_FACTORY) > 0)
                manufacturing.put(Projects.ARMS_STOCKPILE, hasProject(Projects.ARMS_STOCKPILE));
            if (city.get(Buildings.FARM) > 0)
                manufacturing.put(Projects.MASS_IRRIGATION, hasProject(Projects.MASS_IRRIGATION));
        }
        return manufacturing;
    }

    public void setInfra(Integer infra) {
        this.infra = infra;
    }

    @Command
    public int getCities() {
        return cities;
    }

    /**
     *
     * @return
     */
    @Command(desc="Get the min city count of the first matching city range\n" +
            "c1-10, c11-15")
    public int getCityGroup(CityRanges ranges) {
        for (Map.Entry<Integer, Integer> range : ranges.getRanges()) {
            if (cities >= range.getKey() && cities <= range.getValue()) return range.getKey();
        }
        return -1;
    }

    public void setCities(int cities) {
        this.cities = cities;
    }

    @Command(desc="average infrastructure in cities")
    public Integer getAvg_infra() {
        return avg_infra;
    }

    public void setAvg_infra(Integer avg_infra) {
        this.avg_infra = avg_infra;
    }

    @Command(desc = "War policy")
    public WarPolicy getWarPolicy() {
        return war_policy;
    }

    @Command(desc = "War policy")
    public DomesticPolicy getDomesticPolicy() {
        if (domestic_policy == null) return DomesticPolicy.MANIFEST_DESTINY;
        return domestic_policy;
    }

    public void setWarPolicy(WarPolicy policy) {
        this.war_policy = policy;
    }

    public void setDomesticPolicy(DomesticPolicy policy) {
        this.domestic_policy = policy;
    }

    @Command
    public Integer getSoldiers() {
        return soldiers;
    }

    public void setSoldiers(Integer soldiers) {
        this.soldiers = soldiers;
    }

    @Command
    public Integer getTanks() {
        return tanks;
    }

    public void setTanks(Integer tanks) {
        this.tanks = tanks;
    }

    @Command
    public Integer getAircraft() {
        return aircraft;
    }

    @Command
    public int getSpyCap() {
        return hasProject(Projects.INTELLIGENCE_AGENCY) ? 60 : 50;
    }

    @Command
    public double getAircraftPct() {
        if (hasUnsetMil()) return 0;
        return getAircraft() / (double) (Math.max(1, Buildings.HANGAR.max() * Buildings.HANGAR.cap() * getCities()));
    }

    @Command
    public double getTankPct() {
        if (hasUnsetMil()) return 0;
        return getTanks() / (double) (Math.max(1, Buildings.FACTORY.max() * Buildings.FACTORY.cap() * getCities()));
    }

    @Command
    public double getSoldierPct() {
        if (hasUnsetMil()) return 0;
        return getSoldiers() / (double) (Math.max(1, Buildings.BARRACKS.max() * Buildings.BARRACKS.cap() *getCities()));
    }

    @Command
    public double getShipPct() {
        if (hasUnsetMil()) return 0;
        return getShips() / (double) (Math.max(1, Buildings.DRYDOCK.max() * Buildings.DRYDOCK.cap() * getCities()));
    }

    public void setAircraft(Integer aircraft) {
        this.aircraft = aircraft;
    }

    @Command
    public Integer getShips() {
        return ships;
    }

    public void setShips(Integer ships) {
        this.ships = ships;
    }

    @Command
    public Integer getMissiles() {
        return missiles;
    }

    public void setMissiles(Integer missiles) {
        this.missiles = missiles;
    }

    @Command
    public Integer getNukes() {
        return nukes;
    }

    public void setNukes(Integer nukes) {
        this.nukes = nukes;
    }

    @Command(desc = "Number of turns in Vacation Mode")
    public int getVm_turns() {
        return vm_turns;
    }

    public void setVm_turns(int vm_turns) {
        this.vm_turns = vm_turns;
    }

    @Command
    public boolean isBeige() {
        return getColor() == NationColor.BEIGE;
    }

    @Command
    public boolean isGray() {
        return getColor() == NationColor.GRAY;
    }

    @Command
    public NationColor getColor() {
        return color;
    }

    public void setColor(NationColor color) {
        this.color = color;
    }

    @Command(desc = "Number of active offensive wars")
    public int getOff() {
        return off;
    }

    @Command
    @RolePermission(Roles.MEMBER)
    public int getAllTimeOffensiveWars() {
        return (int) getWars().stream().filter(f -> f.attacker_id == nation_id).count();
    }

    @Command
    @RolePermission(Roles.MEMBER)
    public int getAllTimeDefensiveWars() {
        return (int) getWars().stream().filter(f -> f.defender_id == nation_id).count();
    }

    @Command
    @RolePermission(Roles.MEMBER)
    public Map.Entry<Integer, Integer> getAllTimeOffDefWars() {
        List<DBWar> wars = getWars();
        int off = (int) wars.stream().filter(f -> f.attacker_id == nation_id).count();
        int def = (int) wars.stream().filter(f -> f.defender_id == nation_id).count();
        return new AbstractMap.SimpleEntry<>(off, def);
    }

    @Command
    @RolePermission(Roles.MEMBER)
    public int getAllTimeWars() {
        return getWars().size();
    }

    @Command(desc = "Number of wars against active nations")
    public int getNumWarsAgainstActives() {
        if (getNumWars() == 0) return 0;
        int total = 0;
        for (DBWar war : getActiveWars()) {
            DBNation other = war.getNation(!war.isAttacker(this));
            if (other != null && other.getActive_m() < 4880) total++;
        }
        return total;
    }

    @Command(desc = "Number of active offensive and defensive wars")
    public int getNumWars() {
        return getOff() + getDef();
    }

    @Command(desc = "Number of offensive and defensive wars since date")
    public int getNumWarsSince(long date) {
        return Locutus.imp().getWarDb().countWarsByNation(nation_id, date);
    }

    @Command(desc = "Number of offensive wars since date")
    public int getNumOffWarsSince(long date) {
        return Locutus.imp().getWarDb().countOffWarsByNation(nation_id, date);
    }

    @Command(desc = "Number of defensive wars since date")
    public int getNumDefWarsSince(long date) {
        return Locutus.imp().getWarDb().countDefWarsByNation(nation_id, date);
    }

    public void setOff(int off) {
        this.off = off;
    }

    @Command(desc = "Number of active defensive wars")
    public int getDef() {
        return def;
    }

    public void setDef(int def) {
        this.def = def;
    }

    @Command(desc = "Unix timestamp of date created")
    public Long getDate() {
        return date;
    }

    public void setDate(Long date) {
        this.date = date;
    }

    public List<String> headers() {
        ArrayList<String> buffer = new ArrayList<>();
        buffer.add("nation");
        buffer.add("leader");
        buffer.add("allianceUrl");
        buffer.add("active_m");
        buffer.add("score");
        buffer.add("infra");
        buffer.add("cities");
        buffer.add("avg_infra");
        buffer.add("policy");
        buffer.add("soldiers");
        buffer.add("tanks");
        buffer.add("aircraft");
        buffer.add("ships");
        buffer.add("missiles");
        buffer.add("nukes");
        buffer.add("vm_turns");
        buffer.add("color");
        buffer.add("off");
        buffer.add("def");
        buffer.add("money");
        buffer.add("spies");
//        buffer.add("date");
        return buffer;
    }

    @Command
    public int getUnits(MilitaryUnit unit) {
        switch (unit) {
            case SOLDIER:
                return getSoldiers();
            case TANK:
                return getTanks();
            case AIRCRAFT:
                return getAircraft();
            case SHIP:
                return getShips();
            case MONEY:
                // TODO
                return 0;
            case MISSILE:
                return getMissiles();
            case NUKE:
                return getNukes();
            case SPIES:
                return spies == null ? 0 : spies;
        }
        return 0;
    }

    public DBNation fillBlanks(DBNation other) {
        if (infra == null) this.infra = other.infra;
        if (avg_infra == null) this.avg_infra = other.avg_infra;
        if (soldiers == null) this.soldiers = other.soldiers;
        if (tanks == null) this.tanks = other.tanks;
        if (aircraft == null) this.aircraft = other.aircraft;
        if (ships == null) this.ships = other.ships;
        if (missiles == null) this.missiles = other.missiles;
        if (nukes == null) this.nukes = other.nukes;
        if (spies == null) this.spies = other.spies;
        if (date == null) this.date = other.date;
        if (this.projects == 0) this.projects = other.projects;
        if (this.cityTimer == null) this.cityTimer = other.cityTimer;
        if (this.projectTimer == null) this.projectTimer = other.projectTimer;
        if (this.beigeTimer == null) this.beigeTimer = other.beigeTimer;
        this.espionageFull = Math.max(this.espionageFull, other.espionageFull);
        return this;
    }

    public DBNation set(DBNation other) {
        this.nation_id = other.nation_id;
        this.nation = other.nation;
        this.leader = other.leader;
        this.alliance_id = other.alliance_id;
        this.alliance = other.alliance;
        this.last_active = other.last_active;
        this.score = other.score;
        this.infra = other.infra;
        this.cities = other.cities;
        this.avg_infra = other.avg_infra;
        this.war_policy = other.war_policy;
        this.domestic_policy = other.domestic_policy;
        this.soldiers = other.soldiers;
        this.tanks = other.tanks;
        this.aircraft = other.aircraft;
        this.ships = other.ships;
        this.missiles = other.missiles;
        this.nukes = other.nukes;
        this.vm_turns = other.vm_turns;
        this.color = other.color;
        this.off = other.off;
        this.def = other.def;
        this.spies = other.spies;
        this.date = other.date;
        this.alliancePosition = other.alliancePosition;
        this.position = other.position;
        this.continent = other.continent;
        this.projects = other.projects;
        this.cityTimer = other.cityTimer;
        this.projectTimer = other.projectTimer;
        this.beigeTimer = other.beigeTimer;
        this.espionageFull = other.espionageFull;
        return this;
    }

    private String toString(Number value) {
        if (value == null) return "";
        return value + "";
    }

    @Override
    public String toString() {
        return nation;
//        return "{" +
//                "nation_id=" + nation_id +
//                ", nation='" + nation + '\'' +
//                ", leader='" + leader + '\'' +
//                ", alliance_id=" + alliance_id +
//                ", alliance=" + alliance +
//                ", active_m=" + active_m() +
//                ", score=" + score +
//                ", infra=" + infra +
//                ", cities=" + cities +
//                ", avg_infra=" + avg_infra +
//                ", war_policy='" + war_policy + '\'' +
//                ", soldiers=" + soldiers +
//                ", tanks=" + tanks +
//                ", aircraft=" + aircraft +
//                ", ships=" + ships +
//                ", missiles=" + missiles +
//                ", nukes=" + nukes +
//                ", vm_turns=" + vm_turns +
//                ", color='" + color + '\'' +
//                ", off=" + off +
//                ", def=" + def +
////                ", money=" + money +
//                ", spies=" + spies +
//                '}';
    }

    public Set<Integer> getEnemies() {
        return Locutus.imp().getWarDb().getWarsByNation(getNation_id()).stream()
                .map(dbWar -> dbWar.attacker_id == getNation_id() ? dbWar.defender_id : dbWar.attacker_id)
                .collect(Collectors.toSet());
    }

    @Command
    public boolean isInSpyRange(DBNation other) {
        double min = getScore() * 0.4;
        double max = getScore() * 2.5;
        if (other.getScore() < min || other.getScore() > max) {
//            if (other.getRank() < getRank() || other.getRank() >= getRank() + 10)
            {
                return false;
            }
        }
        return true;
    }

    @Command(desc = "If they have undefined military values")
    public boolean hasUnsetMil() {
        return infra == null || avg_infra == null || soldiers == null || tanks == null || aircraft == null || ships == null || missiles == null || nukes == null;
    }

    public int active_m() {
        return (int) TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - last_active);
    }

    @Command
    public boolean hasNoMilitary() {
        if (soldiers != null && soldiers > 0) return false;
        if (tanks != null && tanks > 0) return false;
        if (aircraft != null && aircraft > 0) return false;
        if (ships != null && ships > 0) return false;
        if (missiles != null && missiles > 0) return false;
        return nukes == null || nukes <= 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof Integer) {
            return nation_id == (Integer) o;
        }
        if (o == null || getClass() != o.getClass()) return false;

        DBNation nation = (DBNation) o;

        return nation_id == nation.nation_id;
    }

    @Override
    public int hashCode() {
        return nation_id;
    }

    public Map.Entry<Long, double[]> getBeigeLoot() {
//        Map.Entry<Long, double[]> spyLoot = Locutus.imp().getNationDB().getLoot(nation_id);
        Map.Entry<Long, double[]> loot = Locutus.imp().getWarDb().getNationLoot(nation_id, true).get(nation_id);
//        if (spyLoot != null && (loot == null || loot.getKey() < spyLoot.getKey())) {
//            loot = spyLoot;
//        }
        return loot;
    }

    public String toMarkdown() {
        return toMarkdown(false);
    }

    public String toMarkdown(boolean war) {
        return toMarkdown(war, true, true, true);
    }
    public String toMarkdown(boolean war, boolean showOff, boolean showSpies, boolean showInfra) {
        StringBuilder response = new StringBuilder();
        if (war) {
            response.append("<" + Settings.INSTANCE.PNW_URL() + "/nation/war/declare/id=" + getNation_id() + ">");
        } else {
            response.append("<" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + getNation_id() + ">");
        }
        String beigeStr = null;
        if (color == NationColor.BEIGE) {
            int turns = getBeigeTurns();
            long diff = TimeUnit.MILLISECONDS.toMinutes(TimeUtil.getTimeFromTurn(TimeUtil.getTurn() + turns) - System.currentTimeMillis());
            beigeStr = TimeUtil.secToTime(TimeUnit.MINUTES, diff);
        }

        response.append(" | " + String.format("%16s", getNation()))
                .append(" | " + String.format("%16s", getAlliance()))
                .append(alliance_id != 0 && position == 1 ? " applicant" : "")
                .append(color == NationColor.BEIGE ? " beige:" + beigeStr : "")
                .append(vm_turns > 0 ? " vm=" + TimeUtil.secToTime(TimeUnit.HOURS, vm_turns * 2) : "")
                .append("\n```")
                .append(String.format("%5s", (int) getScore())).append(" ns").append(" | ")
                .append(String.format("%10s", TimeUtil.secToTime(TimeUnit.MINUTES, getActive_m()))).append(" \uD83D\uDD52").append(" | ")
                .append(String.format("%2s", getCities())).append(" \uD83C\uDFD9").append(" | ");
                if (showInfra) response.append(String.format("%5s", getAvg_infra())).append(" \uD83C\uDFD7").append(" | ");
                response.append(String.format("%6s", getSoldiers())).append(" \uD83D\uDC82").append(" | ")
                .append(String.format("%5s", getTanks())).append(" \u2699").append(" | ")
                .append(String.format("%5s", getAircraft())).append(" \u2708").append(" | ")
                .append(String.format("%4s", getShips())).append(" \u26F5");
                if (showOff) response.append(" | ").append(String.format("%1s", getOff())).append(" \uD83D\uDDE1");
                response.append(" | ").append(String.format("%1s", getDef())).append(" \uD83D\uDEE1");
                if (showSpies) response.append(" | ").append(String.format("%2s", getSpies())).append(" \uD83D\uDD0D");
                response.append("```");
        return response.toString();
    }

    public String toMarkdown(boolean title, boolean general, boolean military) {
        return toMarkdown(false, title, general, military, true);
    }

    @Command(desc = "Days since creation")
    public int getAgeDays() {
        if (getDate() == null || getDate() == 0) return 0;
        return (int) TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - date);
//        return (int) (TimeUnit.SECONDS.toDays(ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond()) - getDate() / 65536);
    }

    public Map<Integer, JavaCity> getCityMap(boolean force) {
        return getCityMap(false, force);
    }

    public Map<Integer, JavaCity> getCityMap(boolean updateIfOutdated, boolean force) {
        return getCityMap(updateIfOutdated, true, force);
    }

    public Map<Integer, JavaCity> getCityMap(boolean updateIfOutdated, boolean updateIfNewCity, boolean force) {
        return Locutus.imp().getNationDB().getCities(this, updateIfOutdated, updateIfNewCity, force);
    }

    @Command(desc = "Decimal pct of days they login")
    public double avg_daily_login() {
        int turns;
        if (getNation_id() > 380000) {
            turns = Math.min(12 * 14, Math.max(1, (getAgeDays() - 1) * 12));
        } else {
            turns = 12 * 14;
        }
        Activity activity = getActivity(turns);
        double[] arr = activity.getByDay();
        double total = 0;
        for (double v : arr) total += v;
        return total / arr.length;
    }

    @Command(desc = "Decimal pct of days they login in the past week")
    public double avg_daily_login_week() {
        int turns;
        if (getNation_id() > 380000) {
            turns = Math.min(12 * 7, Math.max(1, (getAgeDays() - 1) * 12));
        } else {
            turns = 12 * 7;
        }
        Activity activity = getActivity(turns);
        double[] arr = activity.getByDay();
        double total = 0;
        for (double v : arr) total += v;
        return total / arr.length;
    }

    @Command(desc = "Decimal pct of turns they login")
    public double avg_daily_login_turns() {
        Activity activity = getActivity(12 * 14);
        double[] arr = activity.getByDayTurn();
        double total = 0;
        for (double v : arr) total += v;
        return total / arr.length;
    }

    @Command(desc = "Decimal pct of turns they login")
    public double login_daychange() {
        Activity activity = getActivity(12 * 14);
        double[] arr = activity.getByDayTurn();
        return (arr[0] + arr[arr.length - 1]) / 2d;
    }

    @Command
    @RolePermission(value = Roles.ECON)
    public double equilibriumTaxRate() {
        return equilibriumTaxRate(false, false);
    }

    public double equilibriumTaxRate(boolean updateNewCities, boolean force) {
        double[] buffer = ResourceType.getBuffer();
        buffer = getRevenue(buffer, true, false, true, updateNewCities, force);
        double consumeCost = 0;
        double taxable = 0;
        for (ResourceType type : ResourceType.values) {
            double value = buffer[type.ordinal()];
            if (value < 0) {
                consumeCost += PnwUtil.convertedTotal(type, -value);
            } else {
                taxable += -PnwUtil.convertedTotal(type, -value);
            }
        }
        if (taxable > consumeCost) {
            return 100 * consumeCost / taxable;
        }
        return Double.NaN;
    }

    @Command
    public double[] getRevenue() {
        return getRevenue(null, true, true, true, false);
    }

    public double[] getRevenue(double[] profitBuffer, boolean militaryUpkeep, boolean tradeBonus, boolean bonus, boolean force) {
        return getRevenue(profitBuffer, militaryUpkeep, tradeBonus, bonus, false, force);
    }

    public double[] getRevenue(double[] profitBuffer, boolean militaryUpkeep, boolean tradeBonus, boolean bonus, boolean update, boolean force) {
        if (profitBuffer == null) profitBuffer = new double[ResourceType.values.length];
        Map<Integer, JavaCity> cityMap = getCityMap(force, false);
        double[] revenue = PnwUtil.getRevenue(profitBuffer, this, cityMap, militaryUpkeep, tradeBonus, bonus);
        return revenue;
    }

    public String fetchUsername() throws IOException {
        String url = getNationUrl();
        String html = FileUtil.readStringFromURL(url);
        Document dom = Jsoup.parse(html);
        Elements elem = dom.select("td:contains(Discord Username)");
        if (elem.isEmpty()) return null;
        String username = elem.first().nextElementSibling().text();
        return username;
    }

    @Command
    @WhitelistPermission
    public double getBeigeLootTotal() {
        Map.Entry<Long, double[]> loot = getBeigeLoot();
        return loot == null ? 0 : PnwUtil.convertedTotal(loot.getValue());
    }

    public double lootTotal() {
        double[] knownResources = new double[ResourceType.values.length];
        double[] buffer = new double[knownResources.length];
        Map.Entry<Long, double[]> loot = getBeigeLoot();
        double convertedTotal = estimateRssLootValue(knownResources, loot, buffer, true) * 0.14;

        if (getPosition() > 1) {
            long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14);
            Map<ResourceType, Double> aaLoot = Locutus.imp().getWarDb().getAllianceBankEstimate(cutoff, false, getAlliance_id(), getScore());
            convertedTotal += PnwUtil.convertedTotal(aaLoot);
        }
        return convertedTotal;
    }

    public double estimateRssLootValue(double[] knownResources, Map.Entry<Long,double[]> lootHistory, double[] buffer, boolean fetchStats) {
        if (lootHistory != null) {
            double[] loot = lootHistory.getValue();
            for (int i = 0; i < loot.length; i++) {
                knownResources[i] = loot[i];
            }
        }
        return PnwUtil.convertedTotal(knownResources);
//        if (getActive_m() > TimeUnit.DAYS.toMinutes(90)) {
//            return 0;
//        }
//        if (getActive_m() <= 10000) {
//            if (!fetchStats) {
//                return 0;
//            }
//            int days = 7;
//            long cutoffMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
//            List<Transaction2> transfers = Locutus.imp().getBankDB().getNationTransfers(nation_id, cutoffMs);
//            List<Offer> trades = Locutus.imp().getTradeManager().getTradeDb().getOffers(nation_id, cutoffMs);
//
//            Map<ResourceType, Double> offset = new HashMap<>();
//            Map<ResourceType, Double> bank = new HashMap<>();
//            Map<ResourceType, Double> trade = new HashMap<>();
//
//            long now = System.currentTimeMillis();
//            for (Transaction2 transfer : transfers) {
//                if (transfer.getDate() > now) continue;
//                int sign = transfer.getReceiver() == nation_id ? 1 : -1;
//                bank = PnwUtil.add(bank, PnwUtil.resourcesToMap(transfer.resources));
//            }
//
//            for (Offer offer : trades) {
//                Integer buyer = offer.getBuyer();
//                Integer seller = offer.getSeller();
//                int sign = (seller.equals(nation_id) ^ offer.isBuy()) ? -1 : 1;
//
//                long moneyOut = offer.getTotal();
//                trade.put(ResourceType.MONEY, trade.getOrDefault(ResourceType.MONEY, 0d) + (-1) * sign * moneyOut);
//                trade.put(offer.getResource(), trade.getOrDefault(offer.getResource(), 0d) + sign * offer.getAmount());
//            }
//
//            String dateStr = (ZonedDateTime.now(ZoneOffset.UTC).minusDays(days - 1).format(TimeUtil.YYYY_MM_DD));
//
//            // add up munition usage
//            // add up soldier losses
//
//            Map<ResourceType, Double> consumption = new HashMap<>();
//            Map<MilitaryUnit, Integer> unitLosses = new HashMap<>();
//            List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacks(nation_id, cutoffMs);
//            for (DBAttack attack : attacks) {
//                boolean attacker = attack.attacker_nation_id == nation_id;
//
//                Map<ResourceType, Double> attConsume = attack.getLosses(attacker, false, false, true, true);
//                consumption = PnwUtil.add(consumption, attConsume);
//
//                unitLosses = PnwUtil.add(unitLosses, attack.getUnitLosses(attacker));
//
//                Map<ResourceType, Double> loot = attack.getLoot();
//                if (loot != null && !loot.isEmpty() && (nation_id == (attack.getLooter()) || nation_id == (attack.getLooted()))) {
//                    int sign = (attack.getLooter() == nation_id) ? -1 : 1;
//                    for (Map.Entry<ResourceType, Double> entry : loot.entrySet()) {
//                        consumption.put(entry.getKey(), consumption.getOrDefault(entry.getKey(), 0d) + entry.getValue() * sign);
//                    }
//                    if (sign == 1) { // lost
//                        double factor = 1d / attack.getLootPercent();
//                        for (Map.Entry<ResourceType, Double> entry : loot.entrySet()) {
//                            offset.put(entry.getKey(), entry.getValue() * factor);
//                        }
//                    }
//                }
//            }
//
//            Map<MilitaryUnit, Integer> bought = new HashMap<>();
//
//            Map<ResourceType, Double> totals = new HashMap<>();
//            totals = PnwUtil.add(totals, bank);
//            totals = PnwUtil.add(totals, trade);
//            totals = PnwUtil.subResourcesToA(totals, consumption);
//            totals = PnwUtil.add(totals, offset);
//            for (Map.Entry<MilitaryUnit, Integer> entry : bought.entrySet()) {
//                MilitaryUnit unit = entry.getKey();
//                for (ResourceType resource : unit.getResources()) {
//                    Integer num = entry.getValue();
//                    if (num > 0) {
//                        totals.put(resource, totals.getOrDefault(resource, 0d) - unit.getRssAmt(resource) * num);
//                    }
//                }
//            }
//            for (Map.Entry<ResourceType, Double> entry : totals.entrySet()) {
//                knownResources[entry.getKey().ordinal()] = Math.max(0, entry.getValue());
//            }
//
//            return PnwUtil.convertedTotal(totals);
//        }
//        Map<Integer, JavaCity> cityMap = getCityMap(false, false);
//
//        double daysInactive = Math.min(90, TimeUnit.MINUTES.toDays(getActive_m()));
//        Arrays.fill(buffer, 0);
//        double rads = getRads();
//        for (Map.Entry<Integer, JavaCity> entry : cityMap.entrySet()) {
//            buffer = entry.getValue().profit(rads, p -> false, buffer, cities);
//        }
////        if (buffer[0] > 500000) {
////            daysInactive = Math.max(daysInactive, money / buffer[0]);
////        }
//
//        if (lootHistory != null) {
//            long diffMs = System.currentTimeMillis() - lootHistory.getKey();
//            long newDaysInactive = TimeUnit.MILLISECONDS.toDays(diffMs);
//
//            if (newDaysInactive <= daysInactive + 1) {
//                daysInactive = newDaysInactive;
//                double[] lootValue = lootHistory.getValue();
//                for (int i = 0; i < lootValue.length; i++) {
//                    knownResources[i] += lootValue[i];
//                }
//            }
//        }
//
//        for (int i = 0; i < buffer.length; i++) {
//            ResourceType type = ResourceType.values[i];
//            if (!type.isManufactured()) {
//                continue;
//            }
//            double value = buffer[i];
//            if (value == 0) continue;
//            for (ResourceType input : type.getInputs()) {
//                double inputConsumed = buffer[input.ordinal()];
//                if (inputConsumed >= 0) continue;
//                double inputStored = knownResources[input.ordinal()];
//                value = Math.min((inputStored / (inputConsumed * daysInactive)) * 3, value);
//            }
//            buffer[i] = value;
//        }
//        for (int i = 2; i < buffer.length; i++) {
//            knownResources[i] = Math.max(0, knownResources[i] + (buffer[i] * daysInactive));
//        }
//        knownResources[0] = 0;
//        knownResources[1] = 0;
//
//        return PnwUtil.convertedTotal(knownResources);
    }

    public PNWUser getDBUser() {
        return Locutus.imp().getDiscordDB().getUserFromNationId(nation_id);
    }

    @Command
    public boolean isVerified() {
        return getUser() != null;
    }

    @Command
    public boolean isInAllianceGuild() {
        GuildDB db = Locutus.imp().getGuildDBByAA(alliance_id);
        if (db != null) {
            User user = getUser();
            if (user != null) {
                return db.getGuild().getMember(user) != null;
            }
        }
        return false;
    }

    @Command
    public boolean isInMilcomGuild() {
        GuildDB db = Locutus.imp().getGuildDBByAA(alliance_id);
        if (db != null) {
            Guild warGuild = db.getOrNull(GuildDB.Key.WAR_SERVER);
            if (warGuild == null) warGuild = db.getGuild();
            User user = getUser();
            if (user != null) {
                return warGuild.getMember(user) != null;
            }
        }
        return false;
    }

    @Command
    public String getUserDiscriminator() {
        User user = getUser();
        if (user == null) return null;
        return user.getName() + "#" + user.getDiscriminator();
    }

    @Command
    public Long getUserId() {
        User user = getUser();
        if (user == null) return null;
        return user.getIdLong();
    }

    public User getUser() {
        PNWUser dbUser = getDBUser();
        return dbUser != null ? dbUser.getUser() : null;
    }

    public Map.Entry<Long, String> getUnblockadeRequest() {
        ByteBuffer request = getMeta(NationMeta.UNBLOCKADE_REASON);
        if (request == null) return null;
        long now = System.currentTimeMillis();
        long cutoff = request.getLong();
        byte[] noteBytes = new byte[request.remaining()];
        request.get(noteBytes);
        String note = new String(noteBytes);
        return new AbstractMap.SimpleEntry<>(cutoff, note);
    }

    @Command
    public boolean isBlockaded() {
        return !getBlockadedBy().isEmpty();
    }

    @Command
    public boolean isBlockader() {
        return !getBlockading().isEmpty();
    }

    @Command
    public Set<Integer> getBlockading() {
        Set<Integer> empty = Collections.emptySet();
        if (getNumWars() == 0 || getActive_m() > 7200 || ships == null || ships == 0) return empty;
        return Locutus.imp().getWarDb().getBlockaderByNation(false).getOrDefault(nation_id, empty);
    }

    @Command
    public Set<Integer> getBlockadedBy() {
        Set<Integer> empty = Collections.emptySet();
        if (getNumWars() == 0) return empty;
        return Locutus.imp().getWarDb().getBlockadedByNation(false).getOrDefault(nation_id, empty);
    }

    public Message toCard(MessageChannel channel, boolean spies, boolean money) {
        return toCard(channel, spies, money, false);
    }

    public Message toCard(MessageChannel channel, boolean spies, boolean money, boolean refresh) {
        String title = nation;
        String counterEmoji = "\uD83C\uDD98";
        String counterCmd = Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "counter " + getNationUrl();
        String simEmoji = "\uD83E\uDD16";
        String simCommand = Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "simulate " + getNationUrl();
        String refreshEmoji = "\uD83D\uDD04";
        String refreshCmd = Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "who " + getNationUrl();

        String response = toEmbedString(spies);

        if (refresh) {
            return DiscordUtil.createEmbedCommand(channel, title, response, counterEmoji, counterCmd, simEmoji, simCommand, refreshEmoji, refreshCmd);
        } else {
            return DiscordUtil.createEmbedCommand(channel, title, response, counterEmoji, counterCmd, simEmoji, simCommand);
        }
    }
    public String toEmbedString(boolean spies) {
        StringBuilder response = new StringBuilder();
        PNWUser user = Locutus.imp().getDiscordDB().getUserFromNationId(getNation_id());
        if (user != null) {
            response.append(user.getDiscordName() + " / <@" + user.getDiscordId() + "> | ");
        }
        response.append(toMarkdown(true, true, true, false, false));
        response.append(toMarkdown(true, false, false, true, spies));

        response.append(" ```")
                .append(String.format("%6s", getVm_turns())).append(" \uD83C\uDFD6\ufe0f").append(" | ")
                .append(String.format("%6s", getColor())).append(" | ")
                .append(String.format("%4s", getAgeDays())).append("day").append(" | ")
                .append(String.format("%6s", getContinent()))
                .append("```");


//        if (nation_id == Settings.INSTANCE.NATION_ID) {
//            String imageUrl = "https://cdn.discordapp.com/attachments/694201462837739563/710292110494138418/borg.jpg";
//            GuildMessageChannel channel = Locutus.imp().getDiscordApi().getGuildChannelById(channelId);
//            DiscordUtil.createEmbedCommand(channel, new Consumer<EmbedBuilder>() {
//                @Override
//                public void accept(EmbedBuilder embed) {
//                    embed.setThumbnail(imageUrl);
//                    embed.setTitle(title);
//                    embed.setDescription(response.toString());
//                }
//            }, counterEmoji, counterCmd, simEmoji, simCommand);
//        } else
        return response.toString();
    }

    @Command(desc = "Sheet lookup")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS})
    public Object cellLookup(SpreadSheet sheet, String columnSearch, String columnOutput, String search) {
        List<List<Object>> values = sheet.loadValues();
        int searchIndex = SheetUtil.getIndex(columnSearch) - 1;
        int outputIndex = SheetUtil.getIndex(columnOutput) - 1;

        for (int i = 0; i < values.size(); i++) {
            List<Object> row = values.get(i);
            if (row.size() > searchIndex && row.size() > outputIndex) {
                Object cellSearch = row.get(searchIndex);
                if (cellSearch == null) continue;
                cellSearch = cellSearch.toString();
                if (search.equals(cellSearch)) {
                    return row.get(outputIndex);
                }
            }
        }
        return null;
    }

    @Command(desc = "Get the city url by index")
    public String cityUrl(int index) {
        Set<Map.Entry<Integer, JavaCity>> cities = getCityMap(true, false).entrySet();
        int i = 0;
        for (Map.Entry<Integer, JavaCity> entry : cities) {
            if (++i == index) {
                String url = "" + Settings.INSTANCE.PNW_URL() + "/city/id=" + entry.getKey();
                return url;
            }
        }
        return null;
    }

    @Command
    public String getNationUrl() {
        return "" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + getNation_id();
    }

    @Command
    public String getAllianceUrl() {
        return "" + Settings.INSTANCE.PNW_URL() + "/alliance/id=" + getAlliance_id();
    }

    public String getNationUrlMarkup(boolean embed) {
        String nationUrl = getNationUrl();
        if (!embed) {
            nationUrl = "<" + nationUrl + ">" + " | " + String.format("%16s", getNation());
        } else {
            nationUrl = MarkupUtil.markdownUrl(nation, nationUrl);
        }
        return nationUrl;
    }

    public String getAllianceUrlMarkup(boolean embed) {
        String allianceUrl = getAllianceUrl();
        if (!embed) {
            allianceUrl = String.format("%16s", getAlliance()); // "<" + allianceUrl + ">" + " | " +
        } else {
            allianceUrl = MarkupUtil.markdownUrl(alliance, allianceUrl);
        }
        return allianceUrl;
    }

    public String toCityMilMarkedown() {
        StringBuilder body = new StringBuilder();
        body
                .append("```")
                .append(String.format("%2s", getCities())).append("\uD83C\uDFD9").append("|")
                .append(String.format("%6s", getSoldiers())).append("\uD83D\uDC82").append("|")
                .append(String.format("%5s", getTanks())).append("\u2699").append("|")
                .append(String.format("%4s", getAircraft())).append("\u2708").append("|")
                .append(String.format("%3s", getShips())).append("\u26F5")
                .append("```");
        return body.toString();
    }

    public String toMarkdown(boolean embed, boolean title, boolean general, boolean military, boolean spies) {
        StringBuilder response = new StringBuilder();
        if (title) {
            String nationUrl = getNationUrlMarkup(embed);
            String allianceUrl = getAllianceUrlMarkup(embed);
            response
                    .append(nationUrl)
                    .append(" | ")
                    .append(allianceUrl);

            if (embed && position == 1 && alliance_id != 0) response.append(" (applicant)");

            response.append('\n');
        }
        if (general || military || spies) {
            response.append("```");
            if (general) {
                int active = getActive_m();
                active = active - active % (60);
                String time = active <= 1 ? "Online" : TimeUtil.secToTime(TimeUnit.MINUTES, active);
                response
                        .append(String.format("%5s", (int) getScore())).append(" ns").append(" | ")
                        .append(String.format("%6s", time)).append(" | ")
                        .append(String.format("%2s", getCities())).append(" \uD83C\uDFD9").append(" | ")
                        .append(String.format("%5s", getAvg_infra())).append(" \uD83C\uDFD7").append(" | ");
            }
            if (military) {
                response
                        .append(String.format("%6s", getSoldiers())).append(" \uD83D\uDC82").append(" | ")
                        .append(String.format("%5s", getTanks())).append(" \u2699").append(" | ")
                        .append(String.format("%5s", getAircraft())).append(" \u2708").append(" | ")
                        .append(String.format("%4s", getShips())).append(" \u26F5").append(" | ");
            }
            if (general) {
                response
                        .append(String.format("%8s", getWarPolicy())).append(" | ")
                        .append(String.format("%1s", getOff())).append(" \uD83D\uDDE1").append(" | ")
                        .append(String.format("%1s", getDef())).append(" \uD83D\uDEE1").append(" | ");
            }
            String str = response.toString();
            if (str.endsWith(" | ")) response = new StringBuilder(str.substring(0, str.length() - 3));
            response.append("```");
        }
        return response.toString();
    }

    @Command(desc = "The infra level of their highest city")
    public double maxCityInfra() {
        double infra = avg_infra;
        Map<Integer, CityInfraLand> cityInfra = Locutus.imp().getNationDB().getCityInfraLand(nation_id);
        if (cityInfra.size() == cities) {
            double total = 0;
            for (Map.Entry<Integer, CityInfraLand> entry : cityInfra.entrySet()) {
                double amt = entry.getValue().infra;
                total += amt;
                infra = Math.max(infra, amt);
            }
            return infra;
        }
        infra = avg_infra;
        for (Map.Entry<Integer, JavaCity> entry : getCityMap(false, false).entrySet()) {
            infra = Math.max(infra, entry.getValue().getInfra());
        }
        return infra;
    }

    @Command
    public int getBeigeTurns() {
        return getBeigeTurns(false);
    }

    public JsonObject sendMail(String[] keys, String subject, String message) throws IOException {
        JsonObject json = null;
        for (String key : keys) {
            json = sendMail(key, subject, message);
            String jsonStr = json.toString();
            String successStr = "success\":";
            int successIndex = jsonStr.indexOf(successStr);
            if (successIndex != -1) {
                char tf = jsonStr.charAt(successIndex + successStr.length());
                if (tf == 't') {
                    return json;
                }
            }
        }
        return json;
    }

    public JsonObject sendMail(String key, String subject, String message) throws IOException {
        if (key.equalsIgnoreCase(Locutus.imp().getPrimaryKey())) {
            Auth auth = Locutus.imp().getRootAuth();
            if (auth != null) {
                String result = new MailTask(auth, this, subject, message, null).call();
                if (result.contains("Message sent")) {
                    return JsonParser.parseString("{\"success\":true,\"to\":\"" + nation_id + "\",\"cc\":null,\"subject\":\"" + subject + "\"}").getAsJsonObject();
                }
            }
        }

        Map<String, String> post = new HashMap<>();
        post.put("to", getNation_id() + "");
        post.put("subject", subject);
        post.put("message", message);
        String url = "" + Settings.INSTANCE.PNW_URL() + "/api/send-message/?key=" + key;
        String result = FileUtil.readStringFromURL(url, post, null);
        return JsonParser.parseString(result).getAsJsonObject();
    }

    public int getBeigeTurns(boolean force) {
        if (!force) {
            if (!isBeige()) return 0;
            if (beigeTimer != null && beigeTimer != 0) {
                long diff = Math.max(0, beigeTimer - TimeUtil.getTurn());
                if (diff > 0) return (int) diff;
            }
        }

        if (!isBeige() && !force) {
            if (this.beigeTimer == null) {
                this.beigeTimer = 0L;
                Locutus.imp().getNationDB().addNation(this);
            }
            return 0;
        }
        ZonedDateTime utc = ZonedDateTime.now(ZoneOffset.UTC);
        long currentTurn = TimeUtil.getTurn(utc);

        int days = 15;

//        int[] beige = new int[days * 12 + 1];
        List<Map.Entry<Long, Integer>> beigeList = new ArrayList<>();

        List<DBWar> wars = Locutus.imp().getWarDb().getWarsByNation(nation_id);
        for (DBWar war : wars) {
            ZonedDateTime warTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(war.date), ZoneOffset.UTC);
            long warTurns = TimeUtil.getTurn(warTime);
            if (warTurns < currentTurn - days * 12) continue;

            if (war.attacker_id == nation_id) {
                int turnsAgo = (int) (currentTurn - warTurns);
                beigeList.add(new AbstractMap.SimpleEntry<>(war.date, - (days * 120)));
//                beige[turnsAgo] -= days * 120;
            }
        }
        List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacks(nation_id, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days + 1));
        for (DBAttack attack : attacks) {
            if (attack.attack_type != AttackType.VICTORY || attack.victor == nation_id) continue;

            ZonedDateTime warTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(attack.epoch), ZoneOffset.UTC);
            long warTurns = TimeUtil.getTurn(warTime);

            if (warTurns < currentTurn - days * 12) continue;
            int turnsAgo = (int) (currentTurn - warTurns);
            beigeList.add(new AbstractMap.SimpleEntry<>(attack.epoch, 24));
//            beige[turnsAgo] += 24;
        }

        beigeList.sort(new Comparator<Map.Entry<Long, Integer>>() {
            @Override
            public int compare(Map.Entry<Long, Integer> o1, Map.Entry<Long, Integer> o2) {
                return Long.compare(o1.getKey(), o2.getKey());
            }
        });

        int turnsBeige = 0;
        long lastTurn = 0;
        for (Map.Entry<Long, Integer> entry : beigeList) {
            long timestamp = entry.getKey();
            long turn = TimeUtil.getTurn(timestamp);
            int amt = entry.getValue();

            if (lastTurn != turn) {
                if (turnsBeige > 0) {
                    turnsBeige -= (turn - lastTurn);
                    if (turnsBeige < 0) turnsBeige = 0;
                }
                lastTurn = turn;
            }
            turnsBeige += amt;
            if (turnsBeige < 0) turnsBeige = 0;
        }
        long turn = TimeUtil.getTurn();
        if (lastTurn != turn) {
            turnsBeige -= (turn - lastTurn);
            if (turnsBeige < 0) turnsBeige = 0;
        }

//        for (int i = beige.length - 1; i >= 0; i--) {
//            turnsBeige = Math.min(216, Math.max(0, turnsBeige - 1));
//            turnsBeige = Math.min(216, Math.max(0, turnsBeige + beige[i]));
//        }

        long turnLeaveBeige = turnsBeige <= 0 ? 0 : TimeUtil.getTurn() + turnsBeige;
        if (this.beigeTimer == null || this.beigeTimer < turnLeaveBeige) {
            this.beigeTimer = turnLeaveBeige;
            Locutus.imp().getNationDB().addNation(this);
        }
        return turnsBeige;
    }

    public boolean isReroll() {
        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNations();
        for (Map.Entry<Integer, DBNation> entry : nations.entrySet()) {
            int otherId = entry.getKey();
            DBNation otherNation = entry.getValue();
            if (otherNation.getDate() == null || getDate() == null) continue;

            if (otherId > nation_id && Math.abs(otherNation.getDate()  - getDate()) > TimeUnit.DAYS.toMillis(1)) {
                return true;
            }
        }
        return false;
    }

    public NationMeta.BeigeAlertMode getBeigeAlertMode(NationMeta.BeigeAlertMode def) {
        ByteBuffer value = getMeta(NationMeta.BEIGE_ALERT_MODE);
        if (value == null) {
            return def;
        }
        return NationMeta.BeigeAlertMode.values()[value.get()];
    }

    public NationMeta.BeigeAlertRequiredStatus getBeigeRequiredStatus(NationMeta.BeigeAlertRequiredStatus def) {
        ByteBuffer value = getMeta(NationMeta.BEIGE_ALERT_REQUIRED_STATUS);
        if (value == null) {
            return def;
        }
        return NationMeta.BeigeAlertRequiredStatus.values()[value.get()];
    }



    public Set<Integer> getMultis() {
        Set<BigInteger> uuids = Locutus.imp().getDiscordDB().getUuids(getNation_id()).keySet();
        Set<Integer> multiNations = new HashSet<>();;
        for (BigInteger uuid : uuids) {
            Set<Integer> multis = Locutus.imp().getDiscordDB().getMultis(uuid);
            for (int nationId : multis) {
                if (nationId == getNation_id()) continue;
                multiNations.add(nationId);
            }
        }
        return multiNations;
    }

    public Nation getPnwNation() throws IOException {
        return getPnwNation(Locutus.imp().getPnwApi());
    }

    public Nation getPnwNation(PoliticsAndWarV2 api) throws IOException {
        if (api == null) Locutus.imp().getPnwApi();
        long start = System.currentTimeMillis();
        Nation pnwNation = api.getNation(nation_id);

        DBNation previous = new DBNation(this);
        NationUpdateProcessor.process(previous, this, null, start, NationUpdateProcessor.UpdateType.INITIAL);
        update(pnwNation);
        Locutus.imp().getNationDB().addNation(this);

        return pnwNation;
    }

    public boolean lostInactiveWar() {
        if (getActive_m() < 2880) return false;
        DBWar lastWar = Locutus.imp().getWarDb().getLastWar(nation_id, nation_id);
        if (lastWar != null && lastWar.defender_id == nation_id && lastWar.status == WarStatus.ATTACKER_VICTORY) {
            long lastActiveCutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(Math.max(active_m() + 1220, 7200));
            if (lastWar.date > lastActiveCutoff) return true;
        }
        return false;
    }

    public DBNation update(Nation pnwNation) {
        alliance_id = Integer.parseInt(pnwNation.getAllianceid());
        alliance = pnwNation.getAlliance();
        nation = pnwNation.getName();
        leader = pnwNation.getLeadername();
        last_active = System.currentTimeMillis() - TimeUnit.MILLISECONDS.toMillis(pnwNation.getMinutessinceactive());
        score = MathMan.parseDouble(pnwNation.getScore());
        infra = (int) pnwNation.getTotalinfrastructure();
        cities = pnwNation.getCities();
        avg_infra = (int) (pnwNation.getTotalinfrastructure() / cities);
        war_policy = WarPolicy.parse(pnwNation.getWarPolicy());
        domestic_policy = DomesticPolicy.parse(pnwNation.getDomesticPolicy());
        soldiers = MathMan.parseInt(pnwNation.getSoldiers());
        tanks = MathMan.parseInt(pnwNation.getTanks());
        aircraft = MathMan.parseInt(pnwNation.getAircraft());
        ships = MathMan.parseInt(pnwNation.getShips());
        missiles = MathMan.parseInt(pnwNation.getMissiles());
        nukes = MathMan.parseInt(pnwNation.getNukes());
        vm_turns = MathMan.parseInt(pnwNation.getVmode());
        color = NationColor.valueOf(pnwNation.getColor().toUpperCase());
        off = pnwNation.getOffensivewars();
        def = pnwNation.getDefensivewars();
        position = MathMan.parseInt(pnwNation.getAllianceposition());
//        rank = MathMan.parseInt(pnwNation.getNationrank());
//        if (this.date == null || this.date == 0) {
//            // long dateIndex = (days * 65536) + dayIndex;
//            //
//            //                boolean wasNull = (nation.getDate() == null);
//            //                nation.setDate(dateIndex);
//
//        }
        this.date = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(pnwNation.getDaysold());

        this.cityTimer = TimeUtil.getTurn() + 120 - pnwNation.getTurns_since_last_city();
        this.projectTimer = TimeUtil.getTurn() + 120 - pnwNation.getTurns_since_last_project();
        if (pnwNation.getBeigeTurnsLeft() > 0) {
            this.beigeTimer = TimeUtil.getTurn() + pnwNation.getBeigeTurnsLeft();
        } else {
            this.beigeTimer = 0L;
        }
//
//        for (Project project : Projects.values) {
//            if (project.get(pnwNation) != 0) {
//                projects |= (1 << (project.ordinal() + 1));
//            }
//        }

        Locutus.imp().getNationDB().addNation(this);
        return this;
    }

    @Command
    public long cityTimerTurns() {
        if (cityTimer == null) {
            cityTimer = 0L;
        }
        return (cityTimer - TimeUtil.getTurn());
    }

    @Command
    public long projectTimerTurns() {
        if (projectTimer == null) {
            projectTimer = 0L;
        }
        return (projectTimer - TimeUtil.getTurn());
    }

    public void setMMR(int barracks, int factories, int hangars, int drydocks) {
        soldiers = barracks * cities * Buildings.BARRACKS.max();
        tanks = factories * cities * Buildings.FACTORY.max();
        aircraft = hangars * cities * Buildings.HANGAR.max();
        ships = drydocks * cities * Buildings.DRYDOCK.max();
    }

    @Command(desc = "Number of buildings total")
    @RolePermission(Roles.MEMBER)
    public int getBuildings() {
        Map<Integer, JavaCity> cityMap = getCityMap(false);
        int buildings = 0;
        for (Map.Entry<Integer, JavaCity> entry : cityMap.entrySet()) {
            buildings += entry.getValue().getImpTotal();
        }
        return buildings;
    }

    @Command(desc = "Number of buildings per city")
    @RolePermission(Roles.MEMBER)
    public double getAvgBuildings() {
        return getBuildings() / cities;
    }

    @Command
    public double getAllianceDepositValuePerCity() throws IOException {
        return getAllianceDepositValue() / cities;
    }

    @Command
    public double getAllianceDepositValue() throws IOException {
        GuildDB db = Locutus.imp().getGuildDBByAA(alliance_id);
        if (db == null) return 0;
        boolean includeGrants = db.getOrNull(GuildDB.Key.MEMBER_CAN_WITHDRAW_IGNORES_GRANTS) == Boolean.FALSE;
        double[] depo = getNetDeposits(db, includeGrants, -1);
        return PnwUtil.convertedTotal(depo);
    }

    @Command
    public boolean correctAllianceMMR() {
        if (position <= 1 || vm_turns > 0) return true;

        GuildDB db = Locutus.imp().getGuildDBByAA(alliance_id);
        if (db == null) return true;

        return db.hasRequiredMMR(this);
    }

    @Command
    public String getMMRBuildingStr() {
        double barracks = 0; // for rounding
        double factories = 0;
        double hangars = 0;
        double drydocks = 0;
        for (Map.Entry<Integer, JavaCity> entry : getCityMap(false).entrySet()) {
            barracks += entry.getValue().get(Buildings.BARRACKS);
            factories += entry.getValue().get(Buildings.FACTORY);
            hangars += entry.getValue().get(Buildings.HANGAR);
            drydocks += entry.getValue().get(Buildings.DRYDOCK);
        }
        barracks /= cities;
        factories /= cities;
        hangars /= cities;
        drydocks /= cities;
        return Math.round(barracks) + "" + Math.round(factories) + "" + Math.round(hangars) + "" + Math.round(drydocks);
    }

    /**
     * MMR (units)
     * @return
     */
    @Command
    public String getMMR() {
        int soldiers = (int) Math.round(getSoldiers() / ((double) cities * Buildings.BARRACKS.max()));
        int tanks = (int) Math.round(getTanks() / ((double) cities * Buildings.FACTORY.max()));
        int aircraft = (int) Math.round(getAircraft() / ((double) cities * Buildings.HANGAR.max()));
        int ships = (int) Math.round(getShips() / ((double) cities * Buildings.DRYDOCK.max()));
        return soldiers + "" + tanks + "" + aircraft + "" + ships;
    }

    @Command(desc = "Total monetary value of military units")
    public double militaryValue() {
        return militaryValue(true);
    }

    public long militaryValue(boolean ships) {
        long total = 0;
        total += soldiers * MilitaryUnit.SOLDIER.getConvertedCost();
        total += tanks * MilitaryUnit.TANK.getConvertedCost();
        total += aircraft * MilitaryUnit.AIRCRAFT.getConvertedCost();
        if (ships) {
            total += this.ships * MilitaryUnit.SHIP.getConvertedCost();
        }
        return total;
    }

    public double getAttr(String attribute) {
        try {
            Field field = DBNation.class.getDeclaredField(attribute);
            field.setAccessible(true);
            return ((Number) field.get(this)).doubleValue();
        } catch (IllegalAccessException | NoSuchFieldException e) {
            try {
                Method method = DBNation.class.getDeclaredMethod(attribute);
                if (method.getReturnType() == double.class) {
                    return (double) method.invoke(this);
                }
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
                ex.printStackTrace();
            }
            return 0;
        }
    }

    public Activity getActivity(long turns) {
        return new Activity(getNation_id(), turns);
    }

    public JsonObject sendMail(Auth auth, String subject, String body) throws IOException {
        String key = auth.getApiKey();
        return sendMail(key, subject, body);
    }

    public Map.Entry<Integer, Integer> getCommends() throws IOException {
        Document dom = Jsoup.parse(FileUtil.readStringFromURL(getNationUrl()));
        int commend = Integer.parseInt(dom.select("#commendment_count").text());
        int denounce = Integer.parseInt(dom.select("#denouncement_count").text());
        return new AbstractMap.SimpleEntry<>(commend, denounce);
    }

    public void setProjectsRaw(long projBitmask) {
        this.projects = projBitmask;
    }

    @Command
    public long getProjectBitMask() {
        return projects;
    }

    public double estimateScore(boolean project) {
        double base = 10;
        base += (cities - 1) * 75;
        base += infra / 40d;
        for (MilitaryUnit unit : MilitaryUnit.values) {
            base += unit.getScore(getUnits(unit));
        }
        if (project) base += getProjects().size() * 20;
        return base;
    }

    public Map<ResourceType, Double> checkExcessResources(GuildDB db) throws IOException {
        return checkExcessResources(db, getStockpile());
    }

    public Map<ResourceType, Double> checkExcessResources(GuildDB db, Map<ResourceType, Double> stockpile) {
        return checkExcessResources(db, stockpile, true);
    }

    public Map<ResourceType, Double> checkExcessResources(GuildDB db, Map<ResourceType, Double> stockpile, boolean update) {
        double factor = 3;
        Map<ResourceType, Double> required;
        if (getCities() >= 10 && getAircraft() > 0) {
            required = PnwUtil.multiply(db.getPerCityWarchest(), (double) getCities());
        } else {
            required = PnwUtil.multiply(db.getPerCityWarchest(), (double) getCities() * 0.33);
            required.remove(ResourceType.ALUMINUM);
            required.remove(ResourceType.URANIUM);
            required.remove(ResourceType.FOOD);
            if (getAircraft() <= 0) {
                required.remove(ResourceType.GASOLINE);
                required.remove(ResourceType.STEEL);
                required.remove(ResourceType.MONEY);
            }
        }

        Map<Integer, JavaCity> cityMap = getCityMap(update, false);
        for (Map.Entry<Integer, JavaCity> cityEntry : cityMap.entrySet()) {
            JavaCity city = cityEntry.getValue();
            Map<ResourceType, Double> cityProfit = PnwUtil.resourcesToMap(city.profit(0, f -> hasProject(f), new double[ResourceType.values.length], cities));
            for (Map.Entry<ResourceType, Double> entry : cityProfit.entrySet()) {
                if (entry.getValue() < 0) {
                    required.put(entry.getKey(), required.getOrDefault(entry.getKey(), 0d) - entry.getValue() * 7);
                }
            }
        }

        stockpile = new HashMap<>(stockpile);
        for (Map.Entry<ResourceType, Double> entry : stockpile.entrySet()) {
            double excess = entry.getValue() - required.getOrDefault(entry.getKey(), 0d) * factor;
            if (excess > 0 && entry.getKey() != ResourceType.CREDITS) entry.setValue(excess);
            else entry.setValue(0d);
        }

        stockpile.entrySet().removeIf(e -> e.getValue() <= 0);

        double excessTotal = PnwUtil.convertedTotal(stockpile);
        if (excessTotal > 1000000L * getCities()) {
            return stockpile;
        }

        return new HashMap<>();
    }

    public List<DBWar> getActiveWarsByStatus(WarStatus... statuses) {
        return Locutus.imp().getWarDb().getWarsByNation(nation_id, statuses);
    }

    @Command(desc = "If fighting a war against another active nation")
    public boolean isFightingActive() {
        if (getDef() > 0) return true;
        if (getOff() > 0) {
            for (DBWar activeWar : this.getActiveWars()) {
                DBNation other = activeWar.getNation(!activeWar.isAttacker(this));
                if (other != null && other.getActive_m() < 1440 && other.getVm_turns() == 0) return true;
            }
        }
        return false;
    }

    @Command(desc = "If online ingame or discord")
    public boolean isOnline() {
        if (active_m() < 60) return true;
        User user = getUser();
        if (user != null) {
            for (Guild guild : user.getMutualGuilds()) {
                Member member = guild.getMember(user);
                if (member != null) {

                    switch (member.getOnlineStatus()) {
                        case ONLINE:
                        case DO_NOT_DISTURB:
                            return true;
                    }
                }
            }
        }
        return false;
    }

    public List<DBWar> getActiveWars() {
        if (getOff() == 0 && getDef() == 0) return Collections.emptyList();
        return Locutus.imp().getWarDb().getWarsByNation(nation_id, WarStatus.ACTIVE, WarStatus.ATTACKER_OFFERED_PEACE, WarStatus.DEFENDER_OFFERED_PEACE);
    }

    public List<DBWar> getActiveOffensiveWars() {
        if (getOff() == 0) return Collections.emptyList();
        List<DBWar> wars = Locutus.imp().getWarDb().getWarsByNation(nation_id, WarStatus.ACTIVE, WarStatus.ATTACKER_OFFERED_PEACE, WarStatus.DEFENDER_OFFERED_PEACE);
        wars.removeIf(f -> !f.isAttacker(DBNation.this));
        return wars;
    }

    public List<DBWar> getWars() {
        return Locutus.imp().getWarDb().getWarsByNation(nation_id);
    }

    public Map<Integer, Map.Entry<Long, Rank>> getAllianceHistory() {
        return Locutus.imp().getNationDB().getRemovesByNation(getNation_id());
    }

    public Map.Entry<Integer, Rank> getPreviousAlliance() {
        Long lastTime = null;
        Rank lastRank = null;
        Integer lastAAId = null;
        for (Map.Entry<Integer, Map.Entry<Long, Rank>> entry : getAllianceHistory().entrySet()) {
            Map.Entry<Long, Rank> timeRank = entry.getValue();
            Rank rank = timeRank.getValue();
            if (rank.id <= Rank.APPLICANT.id) continue;
            int aaId = entry.getKey();
            if (aaId == 0 || aaId == alliance_id) continue;
            if (lastTime == null || timeRank.getKey() >= lastTime) {
                lastTime = timeRank.getKey();
                lastRank = rank;
                lastAAId = aaId;
            }
        }
        if (lastTime == null) return null;
        return new AbstractMap.SimpleEntry<>(lastAAId, lastRank);
    }

    public Map.Entry<Integer, Rank> getAlliancePosition(long date) {
        Map<Integer, Map.Entry<Long, Rank>> history = getAllianceHistory();

        int latestAA = alliance_id;
        Rank latestRank = Rank.byId(position);
        long latestDate = System.currentTimeMillis();
        for (Map.Entry<Integer, Map.Entry<Long, Rank>> entry : history.entrySet()) {
            int aaId = entry.getKey();
            Map.Entry<Long, Rank> dateRank = entry.getValue();
            long historyDate = dateRank.getKey();
            if (historyDate < date) break;
            latestAA = aaId;
            latestDate = historyDate;
            latestRank = dateRank.getValue();
        }
        if (latestRank != null) {
            return new AbstractMap.SimpleEntry<>(latestAA, latestRank);
        }
        return new AbstractMap.SimpleEntry<>(alliance_id, Rank.byId(position));
    }

    public String getWarInfoEmbed() {
        return getWarInfoEmbed(false);
    }

    public String getWarInfoEmbed(DBWar war, boolean loot) {
        return war.getWarInfoEmbed(war.isAttacker(this), loot);
    }

    public String getWarInfoEmbed(boolean loot) {
        StringBuilder body = new StringBuilder();
        List<DBWar> wars = this.getActiveWars();

        for (DBWar war : wars) {
            body.append(getWarInfoEmbed(war, loot));
        }
        body.append(this.getNationUrlMarkup(true));
        body.append("\n").append(this.toCityMilMarkedown());
        return body.toString().replaceAll(" \\| ","|");
    }

//    public int getCities(long date) {
//
//    }
//
//    public Set<Project> getProjects(long date) {
//
//    }

    @Command(desc = "Get units at a specific date")
    @RolePermission(Roles.MILCOM)
    public int getUnits(MilitaryUnit unit, long date) {
        return Locutus.imp().getNationDB().getMilitary(this, unit, date);
    }

    @Command
    public boolean hasUnitBuyToday(MilitaryUnit unit) {
        long turnDayStart = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - TimeUtil.getDayTurn());
        return Locutus.imp().getNationDB().hasBought(this, unit, turnDayStart);
    }

    @Command
    public boolean hasBoughtSoldiersToday() {
        return hasUnitBuyToday(MilitaryUnit.SOLDIER);
    }

    @Command
    public boolean hasBoughtTanksToday() {
        return hasUnitBuyToday(MilitaryUnit.TANK);
    }

    @Command
    public boolean hasBoughtAircraftToday() {
        return hasUnitBuyToday(MilitaryUnit.AIRCRAFT);
    }

    @Command
    public boolean hasBoughtShipsToday() {
        return hasUnitBuyToday(MilitaryUnit.SHIP);
    }

    @Command
    public boolean hasBoughtNukeToday() {
        return hasUnitBuyToday(MilitaryUnit.NUKE);
    }

    @Command
    public boolean hasBoughtMissileToday() {
        return hasUnitBuyToday(MilitaryUnit.MISSILE);
    }

    @Command
    public double daysSinceLastSoldierBuy() {
        Long result = getLastUnitBuy(MilitaryUnit.SOLDIER);
        return result == null ? Long.MAX_VALUE : (((double) (System.currentTimeMillis() - result)) / TimeUnit.DAYS.toMillis(1));
    }

    @Command
    public double daysSinceLastTankBuy() {
        Long result = getLastUnitBuy(MilitaryUnit.TANK);
        return result == null ? Long.MAX_VALUE : (((double) (System.currentTimeMillis() - result)) / TimeUnit.DAYS.toMillis(1));
    }

    @Command
    public double daysSinceLastAircraftBuy() {
        Long result = getLastUnitBuy(MilitaryUnit.AIRCRAFT);
        return result == null ? Long.MAX_VALUE : (((double) (System.currentTimeMillis() - result)) / TimeUnit.DAYS.toMillis(1));
    }

    @Command
    public double daysSinceLastShipBuy() {
        Long result = getLastUnitBuy(MilitaryUnit.SHIP);
        return result == null ? Long.MAX_VALUE : (((double) (System.currentTimeMillis() - result)) / TimeUnit.DAYS.toMillis(1));
    }

    public List<Map.Entry<Long, Integer>> getUnitHistory(MilitaryUnit unit) {
        return Locutus.imp().getNationDB().getMilitaryHistory(this, unit);
    }

    @Command(desc = "Get unix timestamp of when a unit was purchased last")
    @RolePermission(Roles.MILCOM)
    public Long getLastUnitBuy(MilitaryUnit unit) {
        List<Map.Entry<Long, Integer>> list = getUnitHistory(unit);
        if (list == null) return null;
        long lastTime = -1;
        int lastAmt = -1;
        for (Map.Entry<Long, Integer> entry : list) {
            int amt = entry.getValue();
            if (amt < lastAmt) {
                return lastTime;
            }
            lastAmt = amt;
            lastTime = entry.getKey();
        }
        if (lastTime != -1) return lastTime;
        return 0L;
    }

    public Map<Long, Integer> getUnitPurchaseHistory(MilitaryUnit unit, long cutoff) {
        HashMap<Long, Integer> unitsLost = new HashMap<>();

        List<Map.Entry<Long, Integer>> history = getUnitHistory(unit);

        if (unit == MilitaryUnit.NUKE || unit == MilitaryUnit.MISSILE) {
            List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacks(getNation_id(), cutoff);

            outer:
            for (DBAttack attack : attacks) {
                MilitaryUnit[] units = attack.attack_type.getUnits();
                for (MilitaryUnit other : units) {
                    if (other == unit) {
                        Map<MilitaryUnit, Integer> losses = attack.getUnitLosses(attack.attacker_nation_id == nation_id);
                        long turn = TimeUtil.getTurn(attack.epoch);
                        unitsLost.put(turn, losses.getOrDefault(unit, 0) + unitsLost.getOrDefault(turn, 0));
                        continue outer;
                    }
                }
            }

            outer:
            for (DBAttack attack : attacks) {
                AbstractMap.SimpleEntry<Long, Integer> toAdd = new AbstractMap.SimpleEntry<>(attack.epoch, getUnits(unit));
                int i = 0;
                for (; i < history.size(); i++) {
                    Map.Entry<Long, Integer> entry = history.get(i);
                    long diff = Math.abs(entry.getKey() - attack.epoch);
                    if (diff < 5 * 60 * 1000) continue outer;

                    toAdd.setValue(entry.getValue());
                    if (entry.getKey() < toAdd.getKey()) {
                        history.add(i, toAdd);
                        continue outer;
                    }
                }
                history.add(i, toAdd);
            }
        }

        HashMap<Long, Integer> netUnits = new HashMap<>();
        HashMap<Long, Integer> purchases = new HashMap<>();
        Map.Entry<Long, Integer> previous = null;
        for (Map.Entry<Long, Integer> entry : history) {
            if (previous != null) {
                long timestamp = previous.getKey();

                int from = entry.getValue();
                int to = previous.getValue();

                long turn = TimeUtil.getTurn(timestamp);
                int amt = (to - from);
                netUnits.put(turn, netUnits.getOrDefault(turn, 0) + amt);

                if (amt > 0) {
                    purchases.put(turn, purchases.getOrDefault(turn, 0) + amt);
                }
            }
            previous = new AbstractMap.SimpleEntry<>(entry);
        }
        if (previous != null) {
            long timestamp = previous.getKey();
            int to = previous.getValue();
            int from = to;
            if ((unit == MilitaryUnit.MISSILE || unit == MilitaryUnit.NUKE) && to > 0) {
                from = to - 1;
            }
            long turn = TimeUtil.getTurn(timestamp);
            int amt = (to - from);
            netUnits.put(turn, netUnits.getOrDefault(turn, 0) + amt);

            if (amt > 0) {
                purchases.put(turn, purchases.getOrDefault(turn, 0) + amt);
            }
        }
        for (Map.Entry<Long, Integer> entry : unitsLost.entrySet()) {
            if (netUnits.containsKey(entry.getKey())) {
                int purchaseNet = netUnits.get(entry.getKey()) + entry.getValue();
                netUnits.put(entry.getKey(), purchaseNet);
            }
        }

        for (Map.Entry<Long, Integer> entry : netUnits.entrySet()) {
            int purchaseMin = Math.max(0, purchases.getOrDefault(entry.getKey(), 0));
            entry.setValue(Math.max(purchaseMin, entry.getValue()));
        }

        return netUnits;
    }

    public Map<Integer, Long> findDayChange() {
        MilitaryUnit[] units = new MilitaryUnit[]{MilitaryUnit.SOLDIER, MilitaryUnit.TANK, MilitaryUnit.AIRCRAFT, MilitaryUnit.SHIP, MilitaryUnit.MISSILE, MilitaryUnit.NUKE};
        int[] caps = new int[units.length];
        caps[0] = Buildings.BARRACKS.perDay() * Buildings.BARRACKS.cap() * getCities();
        caps[1] = Buildings.FACTORY.perDay() * Buildings.FACTORY.cap() * getCities();
        caps[2] = Buildings.HANGAR.perDay() * Buildings.HANGAR.cap() * getCities();
        caps[3] = Buildings.DRYDOCK.perDay() * Buildings.DRYDOCK.cap() * getCities();
        caps[4] = hasProject(Projects.SPACE_PROGRAM) ? 2 : 1;
        caps[5] = 1;

        Map<Integer, Long> result = new HashMap<>();

        long currentTurn = TimeUtil.getTurn();

        for (int unitI = 0; unitI < units.length; unitI++) {
            MilitaryUnit unit = units[unitI];
            int cap = caps[unitI];
            Map<Long, Integer> history = getUnitPurchaseHistory(unit, 0);
            history.entrySet().removeIf(f -> f.getValue() > cap);

            for (long turn = currentTurn; turn >= currentTurn - 365 * 12; turn--) {
                Integer turn1 = history.getOrDefault(turn, 0);
                int total = history.getOrDefault(turn, 0) + history.getOrDefault(turn - 1, 0);
                if (total > cap) {
                    long dc = turn1 > cap ? turn - 1 : turn;
                    long diff = currentTurn - dc;
                    boolean invalid = turn1 > cap;

                    int dayTurn = (int) (dc % 12);
                    result.put(dayTurn, diff);
                }
            }
        }

        int[] bestByTurn = new int[12];
        Arrays.fill(bestByTurn, Integer.MAX_VALUE);

        for (int unitI = 0; unitI < units.length; unitI++) {
            MilitaryUnit unit = units[unitI];
            int cap = caps[unitI];

            Map<Long, Integer> history = getUnitPurchaseHistory(unit, 0);
            history.entrySet().removeIf(f -> f.getValue() > cap);

            long[] summedPurchases = new long[365 * 12];
            long total = 0;
            for (int j = 0; j < summedPurchases.length; j++) {
                long turn = currentTurn - j;
                total += history.getOrDefault(turn, 0);
                summedPurchases[j] = total;
            }
            Set<Integer> bestOffset = new HashSet<>();
            int bestOffsetVal = 0;

            for (int offset = 0; offset < 12; offset++) {
                int i = 0;
                for (; i < 365; i++) {
                    int start = (i * 12) + offset - 12;
                    int end = start + 12;

                    long sum = summedPurchases[Math.min(summedPurchases.length - 1, Math.max(0, end))] - summedPurchases[Math.max(0, start)];
                    if (sum > cap) {
                        int turn = (int) ((currentTurn - offset) % 12);
                        break;
                    }
                }
                if (i >= bestOffsetVal) {
                    if (i != bestOffsetVal) {
                        bestOffset.clear();
                    }
                    bestOffsetVal = i;
                    int turn = (int) ((currentTurn - offset) % 12);
                    bestOffset.add(turn);
                }
            }

            if (!bestOffset.isEmpty()) {
                for (int i = 0; i < 12; i++) {
                    if (!bestOffset.contains(i)) {
//                            if (bestOffsetVal > bestByTurn[i])
                        bestByTurn[i] = Math.max(0, Math.min(bestByTurn[i], bestOffsetVal - 1));
                        continue;
                    }
                    bestByTurn[i] = Math.min(bestByTurn[i], bestOffsetVal);
                }
            }
        }

        int max = 0;
        for (int val : bestByTurn) {
            if (val != Integer.MAX_VALUE) max = Math.max(max, val);
        }
        for (int dayTurn = 0; dayTurn < bestByTurn.length; dayTurn++) {
            if (bestByTurn[dayTurn] == max) {

                Long existing = result.get(dayTurn);
                if (existing == null || existing > max) result.putIfAbsent(dayTurn, (long) max);
            }
        }

        return result;

    }

    public boolean sendDM(String msg) {
        User user = getUser();
        if (user == null) return false;

        try {
            Message result = RateLimitUtil.complete(user.openPrivateChannel().complete().sendMessage(msg));
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private DBNationCache getCache() {
        return getCache(true);
    }

    private DBNationCache getCache(boolean create) {
        if (cache == null && create) {
            cache = new DBNationCache();
        }
        return cache;
    }

    @Command
    @RolePermission(Roles.MILCOM)
    public int projectSlots() {
        DBNationCache cached = getCache();
        if (cached.lastProjectInfra == infra) {
            return cache.projectSlots;
        }
        synchronized (cached) {
            cached.lastProjectInfra = infra;
            int warsWon = Locutus.imp().getWarDb().getWarsWonOrLost(nation_id);
            int warBonus = warsWon >= 100 ? 1 : 0;
            return cached.projectSlots = (infra / 5000) + 1 + warBonus;
        }
    }

    public AttackCost getWarCost() {
        AttackCost cost = new AttackCost();
        List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacks(nation_id, 0);
        cost.addCost(attacks, a -> a.attacker_nation_id == nation_id, b -> b.defender_nation_id == nation_id);
        return cost;
    }

    @Command
    @RolePermission(Roles.MILCOM)
    public double getMoneyLooted() {
        double total = 0;
        List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacks(nation_id, 0);
        for (DBAttack attack : attacks) {
            if (attack.attacker_nation_id == nation_id) {
                Map<ResourceType, Double> loot = attack.getLoot();
                if (!loot.isEmpty()) total += PnwUtil.convertedTotal(loot);
            }
        }
        return total;
    }

    public void setCityTimer(Long timer) {
        this.cityTimer = timer;
        Locutus.imp().getNationDB().addNation(this);
    }

    public void setProjectTimer(Long timer) {
        this.projectTimer = timer;
        Locutus.imp().getNationDB().addNation(this);
    }

    @Command
    public long getEspionageFullTurn() {
        return espionageFull;
    }

    @Command
    public boolean isEspionageFull() {
        long dc = TimeUtil.getTurn() - TimeUtil.getDayTurn();
        return this.getVm_turns() > 0 || this.espionageFull >= dc;
    }

    @Command
    public boolean isPowered() {
        for (Map.Entry<Integer, JavaCity> entry : getCityMap(false, false).entrySet()) {
            JavaCity city = entry.getValue();
            if (city.getPoweredInfra() >= city.getInfra()) {
                JavaCity.Metrics metrics = city.getCachedMetrics();
                if (metrics == null || metrics.powered != Boolean.FALSE) return true;
            }
            return false;
        }
        return false;
    }

    @Command
    public int daysSinceLastOffensive() {
        DBWar last = Locutus.imp().getWarDb().getLastWar(nation_id);
        if (last != null) {
            return (int) TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - last.date);
        }
        return Integer.MAX_VALUE;
    }

    public boolean isEspionageFull(boolean forceUpdateTurn, boolean forceUpdateDay, boolean login) {
        if (getVm_turns() > 0) return true;

        long lastUpdated = espionageFull == 0 ? 0 : Math.abs(espionageFull);
        long dc = TimeUtil.getTurn() - TimeUtil.getDayTurn();

        if ((forceUpdateTurn && lastUpdated != TimeUtil.getTurn()) || (forceUpdateDay && lastUpdated < dc)) {
            Auth auth = Locutus.imp().getRootAuth();
            Callable<Long> task = new Callable<Long>() {
                @Override
                public Long call() throws Exception {
                    String baseUrl = "" + Settings.INSTANCE.PNW_URL() + "/nation/espionage/eid=";
                    String url = baseUrl + getNation_id();
                    String html = auth.readStringFromURL(url, Collections.emptyMap());
                    if (html.contains("This target has already had 3 espionage operations executed upon them today.")) {
                        return TimeUtil.getTurn();
                    }
                    return -TimeUtil.getTurn();
                }
            };
            try {
                espionageFull = login ? PnwUtil.withLogin(task, auth) : task.call();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (espionageFull >= dc) {
            String baseUrl = "" + Settings.INSTANCE.PNW_URL() + "/nation/espionage/eid=";
            String url = baseUrl + getNation_id();
        }
        return this.espionageFull >= dc;
    }

    public void setBeigeTimer(long l) {
        this.beigeTimer = 0L;
    }

    @Override
    @Command
    public int getId() {
        return nation_id;
    }

    @Override
    public boolean isAlliance() {
        return false;
    }

    @Override
    public String getName() {
        return nation;
    }

    public String getDeclareUrl() {
        return "https://politicsandwar.com/nation/war/declare/id=" + getNation_id();
    }

    @Command
    @RolePermission(Roles.MEMBER)
    public double getAvgLand() {
        return getTotalLand() / cities;
    }

    @Command
    @RolePermission(Roles.MEMBER)
    public double getTotalLand() {
        return getTotalLand(false);
    }
    public double getTotalLand(boolean updateIfOutdated) {
        double total = 0;
        if (!updateIfOutdated) {
            Map<Integer, CityInfraLand> infraLand = Locutus.imp().getNationDB().getCityInfraLand(nation_id);
            for (Map.Entry<Integer, CityInfraLand> entry : infraLand.entrySet()) {
                total += entry.getValue().land;
            }
        } else {
            Map<Integer, JavaCity> cityMap = getCityMap(updateIfOutdated, false);
            for (Map.Entry<Integer, JavaCity> entry : cityMap.entrySet()) {
                total += entry.getValue().getLand();
            }
        }
        return total;
    }

    public int getMaxOff() {
        return hasProject(Projects.PIRATE_ECONOMY) ? 6 : 5;
    }

    public void setEspionageFull(long turn) {
        espionageFull = turn;
    }

    public GuildDB getGuildDB() {
        return Locutus.imp().getGuildDBByAA(alliance_id);
    }

    public Map.Entry<CommandResult, String> runCommandInternally(Guild guild, User user, String command) {
        if (user == null) return new AbstractMap.SimpleEntry<>(CommandResult.ERROR, "No user for: " + getNation());

        DummyMessageOutput output = new DummyMessageOutput();
        DelegateMessage message = DelegateMessage.createWithDummyChannel(command, guild, user, output, null);

        MessageReceivedEvent finalEvent = new DelegateMessageEvent(guild, -1L, message);
        CommandResult type;
        String result;
        try {
            Locutus.imp().getCommandManager().run(finalEvent, false, true);
            type = CommandResult.SUCCESS;
            result = output.getOutput();
        } catch (Throwable e) {
            result = e.getMessage();
            type = CommandResult.ERROR;
        }
        return new AbstractMap.SimpleEntry<>(type, result);
    }

    public long lastActiveMs() {
        return last_active;
    }
}
