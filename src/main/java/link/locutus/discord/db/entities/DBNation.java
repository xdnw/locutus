package link.locutus.discord.db.entities;

import com.google.gson.JsonSyntaxException;
import com.politicsandwar.graphql.model.Bankrec;
import com.politicsandwar.graphql.model.Nation;
import com.politicsandwar.graphql.model.Trade;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.building.PowerBuilding;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.apiv3.enums.GameTimers;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.StringMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.ReportManager;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.nation.NationRegisterEvent;
import link.locutus.discord.event.nation.*;
import link.locutus.discord.pnw.CityRanges;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.NationScoreMap;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.battle.BlitzGenerator;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.offshore.TransferResult;
import link.locutus.discord.util.sheet.SheetUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.task.MailTask;
import link.locutus.discord.util.task.multi.GetUid;
import link.locutus.discord.util.task.roles.AutoRoleInfo;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.web.jooby.handler.CommandResult;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

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
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DBNation implements NationOrAlliance {
    private int nation_id;
    private String nation;
    private String leader;
    private int alliance_id;
    private long last_active;
    private double score;
    private int cities; // TODO remove
    private DomesticPolicy domestic_policy;
    private WarPolicy war_policy;
    private int soldiers;
    private int tanks;
    private int aircraft;
    private int ships;
    private int missiles;
    private int nukes;
    private int spies;
    private long entered_vm;
    private long leaving_vm;
    private NationColor color;
    private long date;
    private Rank rank;
    private int alliancePosition;
    private Continent continent;
    private long projects;
    private long cityTimer;
    private long projectTimer;
    private long beigeTimer;
    private long warPolicyTimer;
    private long domesticPolicyTimer;
    private long colorTimer;
    private long espionageFull;
    private int dc_turn = -1;
    private int wars_won;
    private int wars_lost;
    private int tax_id;
//    private double gdp;
    private double gni;
    private transient  DBNationCache cache;

    public static DBNation getByUser(User user) {
        return DiscordUtil.getNation(user);
    }

    public void processTurnChange(long lastTurn, long turn, Consumer<Event> eventConsumer) {
        if (leaving_vm == turn) {
            if (eventConsumer != null) new NationLeaveVacationEvent(this, this).post();
        }
        if (beigeTimer == turn) {
            if (eventConsumer != null) new NationLeaveBeigeEvent(this, this).post();
        }
        if (colorTimer == turn) {
            if (eventConsumer != null) new NationColorTimerEndEvent(this, this).post();
        }
        if (warPolicyTimer == turn) {
            if (eventConsumer != null) new NationWarPolicyTimerEndEvent(this, this).post();
        }
        if (domesticPolicyTimer == turn) {
            if (eventConsumer != null) new NationDomesticPolicyTimerEndEvent(this, this).post();
        }
        if (cityTimer == turn) {
            if (eventConsumer != null) new NationCityTimerEndEvent(this, this).post();
        }
        if (projectTimer == turn) {
            if (eventConsumer != null) new NationProjectTimerEndEvent(this, this).post();
        }
    }

    public DBNation(int nation_id, String nation, String leader, int alliance_id, long last_active, double score,
                        int cities, DomesticPolicy domestic_policy, WarPolicy war_policy, int soldiers,
                        int tanks, int aircraft, int ships, int missiles, int nukes, int spies,
                        long entered_vm, long leaving_vm, NationColor color, long date,
                        Rank rank, int alliancePosition, Continent continent,
                        long projects, long cityTimer, long projectTimer,
                        long beigeTimer, long warPolicyTimer, long domesticPolicyTimer,
                        long colorTimer,
                        long espionageFull, int dc_turn, int wars_won, int wars_lost,
                        int tax_id,
                    double gni,
                    double gdp) {
        this.nation_id = nation_id;
        this.nation = nation;
        this.leader = leader;
        this.alliance_id = alliance_id;
        this.last_active = last_active;
        this.score = score;
        this.cities = cities;
        this.domestic_policy = domestic_policy;
        this.war_policy = war_policy;
        this.soldiers = soldiers;
        this.tanks = tanks;
        this.aircraft = aircraft;
        this.ships = ships;
        this.missiles = missiles;
        this.nukes = nukes;
        this.spies = spies;
        this.entered_vm = entered_vm;
        this.leaving_vm = leaving_vm;
        this.color = color;
        this.date = date;
        this.rank = rank;
        this.alliancePosition = alliancePosition;
        this.continent = continent;
        this.projects = projects;
        this.cityTimer = cityTimer;
        this.projectTimer = projectTimer;
        this.beigeTimer = beigeTimer;
        this.warPolicyTimer = warPolicyTimer;
        this.domesticPolicyTimer = domesticPolicyTimer;
        this.colorTimer = colorTimer;
        this.espionageFull = espionageFull;
        this.dc_turn = dc_turn;
        this.wars_won = wars_won;
        this.wars_lost = wars_lost;
        this.tax_id = tax_id;
        this.gni = gni;
//        this.gdp = gdp;
    }

    public static DBNation createFromList(String coalition, Collection<DBNation> nations, boolean average) {
        int size = nations.size();
        int numProjects = 0;
        Map<Integer, DBCity> cityCopy = new HashMap<>();
        for (DBNation nation : nations) {
            numProjects += nation.getNumProjects();
            cityCopy.putAll(nation._getCitiesV3());
        }

        int finalNumProjects = numProjects;
        return new DBNation(coalition, nations, average) {
            @Override
            public Map<Integer, DBCity> _getCitiesV3() {
                return cityCopy;
            }

            @Override
            public int getNations() {
                return size;
            }

            @Override
            public int getNumProjects() {
                return finalNumProjects;
            }
        };
    }

    public static DBNation getById(int nationId) {
        return Locutus.imp().getNationDB().getNation(nationId);
    }

    public DBNation() {
        projects = -1;
        beigeTimer = 14 * 12;
        war_policy = WarPolicy.TURTLE;
        domestic_policy = DomesticPolicy.MANIFEST_DESTINY;
        color = NationColor.BEIGE;
        cities = 1;
        date = System.currentTimeMillis();
        spies = -1;
        rank = Rank.BAN;
        continent = Continent.ANTARCTICA;
        leaving_vm = Long.MAX_VALUE;
    }

    public DBNation(DBNation other) {
        this.nation_id = other.nation_id;
        this.nation = other.nation;
        this.leader = other.leader;
        this.alliance_id = other.alliance_id;
        this.last_active = other.last_active;
        this.score = other.score;
        this.cities = other.cities;
        this.domestic_policy = other.domestic_policy;
        this.war_policy = other.war_policy;
        this.soldiers = other.soldiers;
        this.tanks = other.tanks;
        this.aircraft = other.aircraft;
        this.ships = other.ships;
        this.missiles = other.missiles;
        this.nukes = other.nukes;
        this.spies = other.spies;
        this.entered_vm = other.entered_vm;
        this.leaving_vm = other.leaving_vm;
        this.color = other.color;
        this.date = other.date;
        this.rank = other.rank;
        this.alliancePosition = other.alliancePosition;
        this.continent = other.continent;
        this.projects = other.projects;
        this.cityTimer = other.cityTimer;
        this.projectTimer = other.projectTimer;
        this.beigeTimer = other.beigeTimer;
        this.warPolicyTimer = other.warPolicyTimer;
        this.domesticPolicyTimer = other.domesticPolicyTimer;
        this.colorTimer = other.colorTimer;
        this.espionageFull = other.espionageFull;
        this.dc_turn = other.dc_turn;
        this.wars_won = other.wars_won;
        this.wars_lost = other.wars_lost;
        this.tax_id = other.tax_id;
        this.gni = other.gni;
//        this.gdp = other.gdp;
    }

    @Command(desc = "If the nation is taxable")
    public boolean isTaxable() {
        return !isGray() && !isBeige() && getPositionEnum().id > Rank.APPLICANT.id && getVm_turns() == 0;
    }

//    public double getInfraCost(double from, double to) {
//        double cost = PnwUtil.calculateInfra(from, to);
//    }

    @Command(desc = "Daily revenue value of nation")
    public double getRevenueConverted() {
        return PnwUtil.convertedTotal(getRevenue());
    }

    @Command(desc = "Estimated daily Gross National Income (GNI)")
    public double estimateGNI() {
        double[] revenue = getRevenue();
        double total = 0;
        for (ResourceType type : ResourceType.values()) {
            double amt = revenue[type.ordinal()];
            if (amt != 0) {
                total += amt * Locutus.imp().getTradeManager().getGamePrice(type);
            }
        }
        return total;
    }

    public long getLastFetchedUnitsMs() {
        return cache != null ? cache.lastCheckUnitMS : 0;
    }

    public void setLastFetchedUnitsMs(long timestamp) {
        if (cache != null) cache.lastCheckUnitMS = timestamp;
    }

    public String register(User user, GuildDB db, boolean isNewRegistration) {
        if (nation_id == Settings.INSTANCE.NATION_ID) {
            if (Settings.INSTANCE.ADMIN_USER_ID != user.getIdLong()) {
                if (Settings.INSTANCE.ADMIN_USER_ID > 0) {
                    throw new IllegalArgumentException("Invalid admin user id in `config.yaml`. Tried to register `" + user.getIdLong() + "` but config has is `" + Settings.INSTANCE.ADMIN_USER_ID + "`");
                }
                Settings.INSTANCE.ADMIN_USER_ID = user.getIdLong();
                Settings.INSTANCE.save(Settings.INSTANCE.getDefaultFile());
            }
        }
        new NationRegisterEvent(nation_id, db, user, isNewRegistration).post();

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

        output.append("Registration successful. See:\n");
        output.append("- " + MarkupUtil.markdownUrl("Wiki Pages", "<https://github.com/xdnw/locutus/wiki>") + "\n");
        output.append("- " + MarkupUtil.markdownUrl("Initial Setup", "<https://github.com/xdnw/locutus/wiki/initial_setup>") + "\n");
        output.append("- " + MarkupUtil.markdownUrl("Commands", "<https://github.com/xdnw/locutus/wiki/commands>") + "\n\n");
        output.append("Join the Support Server \n");
        output.append("""
                - Help using or configuring the bot
                - Public banking/offshoring
                - Requesting a feature
                - General inquiries/feedback
                <https://discord.gg/cUuskPDrB7>""")
                .append("\n\nRunning auto role task...");
        if (db != null) {
            Role role = Roles.REGISTERED.toRole(db);
            if (role != null) {
                try {
                    Member member = db.getGuild().getMember(user);
                    if (member == null) {
                        member = db.getGuild().retrieveMember(user).complete();
                    }
                    if (member != null) {
                        RateLimitUtil.complete(db.getGuild().addRoleToMember(user, role));
                        output.append("You have been assigned the role: " + role.getName());
                        AutoRoleInfo task = db.getAutoRoleTask().autoRole(member, this);
                        task.execute();
                        output.append("\n" + task.getChangesAndErrorMessage());
                    } else {
                        output.append("Member " + DiscordUtil.getFullUsername(user) + " not found in guild: " + db.getGuild());
                    }
                } catch (InsufficientPermissionException e) {
                    output.append(e.getMessage() + "\n");
                }
            } else {
                if (Roles.ADMIN.has(user, db.getGuild())) {
                    output.append("No REGISTERED role mapping found.");
                    output.append("\nCreate a role mapping with " + CM.role.setAlias.cmd.toSlashMention() + "");
                }
            }
        }
        return output.toString();
    }

    @Command(desc = "The absolute turn of leaving beige")
    public long getBeigeAbsoluteTurn() {
        return beigeTimer;
    }

    @Command(desc = "The absolute turn the war policy change timer expires")
    public long getWarPolicyAbsoluteTurn() {
        return warPolicyTimer;
    }

    @Command(desc = "The absolute turn the domestic policy change timer expires")
    public long getDomesticPolicyAbsoluteTurn() {
        return domesticPolicyTimer;
    }

    @Command(desc = "The absolute turn the color change timer expires")
    public long getColorAbsoluteTurn() {
        return colorTimer;
    }

    @Command(desc = "The number of turns until the color timer expires")
    public long getColorTurns() {
        return Math.max(0, colorTimer - TimeUtil.getTurn());
    }

    @Command(desc = "The number of turns until the domestic policy timer expires")
            public long getDomesticPolicyTurns() {
        return Math.max(0, domesticPolicyTimer - TimeUtil.getTurn());
    }

    @Command(desc = "The number of turns until the war policy timer expires")
    public long getWarPolicyTurns() {
        return Math.max(0, warPolicyTimer - TimeUtil.getTurn());
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

    @Command(desc = "If the nation has a project")
    public boolean hasProject(Project project) {
        return hasProject(project, false);
    }

    public boolean hasProject(Project project, boolean update) {
        return (this.projects & (1L << project.ordinal())) != 0;
    }

    @Command(desc = "If the nation has the treasure")
    public boolean hasTreasure() {
        return !Locutus.imp().getNationDB().getTreasure(nation_id).isEmpty();
    }

    public Set<DBTreasure> getTreasures() {
        return Locutus.imp().getNationDB().getTreasure(nation_id);
    }

    @Command(desc = "How many days the treasure is in said nation")
    public long treasureDays() {
        long max = 0;
        for (DBTreasure treasure : getTreasures()) {
            max = Math.max(max, treasure.getDaysRemaining());
        }
        return max;
    }

    public void setProject(Project project) {
        projects |= 1L << (project.ordinal());
    }

    @Command(desc = "Number of built projects")
    // Mock
    public int getNumProjects() {
        int count = 0;
        for (Project project : Projects.values) {
            if (hasProject(project)) count++;
        }
        return count;
    }

    @Command(desc = "Number of free project slots")
    public int getFreeProjectSlots() {
        return projectSlots() - getProjects().size();
    }

    @Command(desc = "Number of nations")
    // Mock
    public int getNations() {
        return 1;
    }

    /**
     * Entry( value, has data )
     * @return
     */
    public Map.Entry<Double, Boolean> getIntelOpValue() {
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14);
        return getIntelOpValue(cutoff);
    }

    public Map.Entry<Double, Boolean> getIntelOpValue(long cutoff) {
        if (active_m() < 4320) return null;
        if (getVm_turns() > 12) return null;
        if (getActive_m() > 385920) return null;
//        if (!isGray()) return null;
        if (getDef() == 3) return null;
        long currentDate = System.currentTimeMillis();

        LootEntry loot = Locutus.imp().getNationDB().getLoot(getNation_id());
        if (loot != null && loot.getDate() > cutoff) return null;

        long lastLootDate = 0;
        if (loot != null) lastLootDate = Math.max(lastLootDate, loot.getDate());
        if (currentDate - active_m() * 60L * 1000L < lastLootDate) return null;

        long checkBankCutoff = currentDate - TimeUnit.DAYS.toMillis(60);
        if (cities > 10 && lastLootDate < checkBankCutoff) {
            List<Transaction2> transactions = getTransactions(Long.MAX_VALUE, true);
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
        double cityCost = PnwUtil.nextCityCost(cities, true, hasProject(Projects.URBAN_PLANNING), hasProject(Projects.ADVANCED_URBAN_PLANNING), hasProject(Projects.METROPOLITAN_PLANNING), hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY));
        double maxStockpile = cityCost * 2;
        double daysToMax = maxStockpile / (getInfra() * 300);
        if (lastLootDate == 0) {
            lastLootDate = currentDate - TimeUnit.DAYS.toMillis((int) daysToMax);
        }

        long diffMin = TimeUnit.MILLISECONDS.toMinutes(currentDate - lastLootDate);

        if (getActive_m() < 12000) {
            diffMin /= 8;
            DBWar lastWar = Locutus.imp().getWarDb().getLastDefensiveWar(nation_id);
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

        if (loot == null && cities < 12) {
            long finalLastLootDate = lastLootDate;
            Map<Integer, DBWar> wars = Locutus.imp().getWarDb().getWarsForNationOrAlliance(f -> f == nation_id, null, f -> f.date > finalLastLootDate);
            if (!wars.isEmpty()) {
                WarParser cost = WarParser.of(wars.values(), f -> f.attacker_id == nation_id);
                double total = cost.toWarCost().convertedTotal(true);
                value -= total;
            }
        }

        // value for weak military
        double soldierPct = (double) getSoldiers() / (Buildings.BARRACKS.max() * Buildings.BARRACKS.cap(this::hasProject) * getCities());
        double tankPct = (double) getTanks() / (Buildings.FACTORY.max() * Buildings.FACTORY.cap(this::hasProject) * getCities());
        value = value + value * (2 - soldierPct - tankPct);

        return new AbstractMap.SimpleEntry<>(value, loot != null);
    }

    public Long getSnapshot() {
        return null;
    }

    @Command(desc = "Days since joining the alliance")
    @RolePermission(Roles.MEMBER)
    public int allianceSeniority() {
        if (alliance_id == 0) return 0;
        long timestamp = Locutus.imp().getNationDB().getAllianceMemberSeniorityTimestamp(this, getSnapshot());
        long now = System.currentTimeMillis();
        if (timestamp > now) return 0;

        return (int) TimeUnit.MILLISECONDS.toDays(now - timestamp);
    }

    @Command(desc = "Milliseconds since joining the alliance")
    @RolePermission(Roles.MEMBER)
    public long allianceSeniorityMs() {
        if (alliance_id == 0) return 0;
        long timestamp = Locutus.imp().getNationDB().getAllianceMemberSeniorityTimestamp(this, getSnapshot());
        long now = System.currentTimeMillis();
        if (timestamp > now) return 0;
        return now - timestamp;
    }

    @Command(desc="Military strength (1 plane = 1)")
    public double getStrength() {
        return BlitzGenerator.getAirStrength(this, true);
    }

    @Command(desc="Military strength (1 plane = 1)")
    public double getStrength(MMRDouble mmr) {
        return BlitzGenerator.getAirStrength(this, mmr);
    }

    @Command(desc = "Estimated combined strength of the enemies its fighting")
    public double getEnemyStrength() {
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
            List<AbstractCursor> attacks = war.getAttacks();

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
            List<AbstractCursor> attacks = war.getAttacks();

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

    @Command(desc = "Set of projects this nation has")
    @RolePermission(Roles.MEMBER)
    public Set<Project> getProjects() {
        if (this.projects == -1) return Collections.EMPTY_SET;

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
        if (bracket.getAlliance_id() != alliance_id) throw new UnsupportedOperationException("Not in alliance");

        Map<String, String> post = new HashMap<>();
        post.put("bracket_id", "" + bracket.taxId);
        post.put("change_member_bracket", "Update Nation's Bracket");
        post.put("nation_id", getNation_id() + "");
        String url = String.format("" + Settings.INSTANCE.PNW_URL() + "/alliance/id=%s&display=taxes", alliance_id);

        return PnwUtil.withLogin(() -> {
            String token = auth.getToken(PagePriority.BRACKET_SET_UNUSED, url);
            post.put("token", token);

            StringBuilder response = new StringBuilder();

            String result = auth.readStringFromURL(PagePriority.TOKEN, url, post);
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
        return getAuth(true);
    }

    public double getStrongestOffEnemyOfScore(Predicate<Double> filter) {
        List<DBWar> wars = getActiveOffensiveWars();
        double strongest = -1;
        for (DBWar war : wars) {
            DBNation other = war.getNation(!war.isAttacker(this));
            if (other == null || other.active_m() > 2440 || other.getVm_turns() > 0) continue;
            if (filter.test(other.getScore())) {
                strongest = Math.max(strongest, other.getStrength());
            }
        }
        return strongest;
    }

    @Command(desc = "Effective strength of the strongest nation this nation is fighting")
    public double getStrongestEnemy() {
        double val = getStrongestEnemyOfScore((score) -> true);
        return val == -1 ? 0 : val;
    }

    @Command(desc = "Relative strength of the strongest nation this nation is fighting (1 = equal)")
    public double getStrongestEnemyRelative() {
        double enemyStr = getStrongestEnemy();
        double myStrength = getStrength();
        return myStrength == 0 ? 0 : enemyStr / myStrength;
    }

    public double getStrongestEnemyOfScore(Predicate<Double> filter) {
        List<DBWar> wars = getActiveWars();
        double strongest = -1;
        for (DBWar war : wars) {
            DBNation other = war.getNation(!war.isAttacker(this));
            if (other == null || other.active_m() > 2440 || other.getVm_turns() > 0) continue;
            if (filter.test(other.getScore())) {
                strongest = Math.max(strongest, other.getStrength());
            }
        }
        return strongest;
    }

    public boolean isFightingOffEnemyOfScore(Predicate<Double> filter) {
        return getStrongestOffEnemyOfScore(filter) != -1;
    }

    public boolean isFightingEnemyOfScore(Predicate<Double> filter) {
        return getStrongestEnemyOfScore(filter) != -1;
    }

    public boolean isFightingEnemyOfCities(Predicate<Double> filter) {
        for (DBWar war : getWars()) {
            DBNation other = war.getNation(!war.isAttacker(this));
            if (other != null && filter.test((double) other.getCities())) {
                return true;
            }
        }
        return false;
    }

    public boolean isDefendingEnemyOfCities(Predicate<Double> filter) {
        for (DBWar war : getWars()) {
            if (war.defender_id != nation_id) continue;
            DBNation other = war.getNation(!war.isAttacker(this));
            if (other != null && filter.test((double) other.getCities())) {
                return true;
            }
        }
        return false;
    }

    public boolean isAttackingEnemyOfCities(Predicate<Double> filter) {
        for (DBWar war : getWars()) {
            if (war.attacker_id != nation_id) continue;
            DBNation other = war.getNation(!war.isAttacker(this));
            if (other != null && filter.test((double) other.getCities())) {
                return true;
            }
        }
        return false;
    }

    public Auth getAuth(boolean throwError) {
        if (this.auth != null && !this.auth.isValid()) this.auth = null;
        if (this.auth != null) return auth;
        synchronized (this) {
            if (auth == null) {
                if (this.nation_id == Settings.INSTANCE.NATION_ID) {
                    if (!Settings.INSTANCE.USERNAME.isEmpty() && !Settings.INSTANCE.PASSWORD.isEmpty()) {
                        return auth = new Auth(nation_id, Settings.INSTANCE.USERNAME, Settings.INSTANCE.PASSWORD);
                    }
                }
                Map.Entry<String, String> pass = Locutus.imp().getDiscordDB().getUserPass2(nation_id);
                if (pass == null) {
                    PNWUser dbUser = getDBUser();
                    if (dbUser != null) {
                        pass = Locutus.imp().getDiscordDB().getUserPass2(dbUser.getDiscordId());
                        if (pass != null) {
                            Locutus.imp().getDiscordDB().addUserPass2(nation_id, pass.getKey(), pass.getValue());
                            Locutus.imp().getDiscordDB().logout(dbUser.getDiscordId());
                        }
                    }
                }
                if (pass != null) {
                    auth = new Auth(nation_id, pass.getKey(), pass.getValue());
                }
            }
        }
        if (auth == null && throwError) {
            throw new IllegalArgumentException("Please authenticate using " + CM.credentials.login.cmd.toSlashMention() + "");
        }
        return auth;
    }

    public DBNation(String coalition, Collection<DBNation> nations, boolean average) {
        this.nation_id = -1;
        this.nation = coalition;
        this.leader = null;
        this.alliance_id = 0;

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
            this.projects |= other.projects;
            this.wars_won += other.wars_won;
            this.wars_lost += other.wars_lost;
            this.last_active += cast(other.last_active).longValue();
            this.score += cast(other.score).intValue();
            this.cities += cast(other.cities).intValue();
            this.soldiers += cast(other.soldiers).intValue();
            this.tanks += cast(other.tanks).intValue();
            this.aircraft += cast(other.aircraft).intValue();
            this.ships += cast(other.ships).intValue();
            this.missiles += cast(other.missiles).intValue();
            this.nukes += cast(other.nukes).intValue();
            if (other.getVm_turns() > 0) {
                setLeaving_vm(TimeUtil.getTurn() + other.getVm_turns());
            }
            this.spies += cast(other.spies).intValue();
            this.wars_won += other.wars_won;
            this.wars_lost += other.wars_lost;
            if (other.date != 0) {
                numDate++;
                this.date += cast(other.date).longValue();
            }
        }
        if (average) {
            this.last_active /= nations.size();
            this.score /= nations.size();
            this.cities /= nations.size();
            this.soldiers /= nations.size();
            this.tanks /= nations.size();
            this.aircraft /= nations.size();
            this.ships /= nations.size();
            this.missiles /= nations.size();
            this.nukes /= nations.size();
//            this.money /= nations.size();
            this.spies /= nations.size();
            this.date /= numDate;
            this.wars_won /= nations.size();
            this.wars_lost /= nations.size();

        } else {
            long diffAvg = this.last_active / nations.size();
            last_active = System.currentTimeMillis() - ((System.currentTimeMillis() - diffAvg) * nations.size());
        }
    }
    @Command(desc = "The radiation level of the nation")
    public double getRads() {
        double radIndex;
        if (getSnapshot() != null) {
            long turn = TimeUtil.getTurn(getSnapshot());
            Map<Continent, Double> rads = Locutus.imp().getNationDB().getRadiationByTurn(turn);
            radIndex = rads.get(continent) + rads.values().stream().mapToDouble(f -> f).sum() / 5d;
        } else {
            TradeManager manager = Locutus.imp().getTradeManager();
            radIndex = manager.getGlobalRadiation() + manager.getGlobalRadiation(getContinent());
        }
        return (1 + (radIndex / (-1000)));
    }

    @Command(desc = "Raw positional value (0 = remove, 1 = app, 2 = member, 3 = officer 4 = heir, 5 = leader)")
    public int getPosition() {
        return rank.id;
    }

    @Command(desc = "Alliance position enum id\n" +
            "0 = None or Removed\n" +
            "1 = Applicant\n" +
            "2 = Member\n" +
            "3 = Officer\n" +
            "4 = Heir\n" +
            "5 = Leader")
    public Rank getPositionEnum() {
        return rank;
    }

    @Command(desc = "Alliance unique id of the position level\n" +
            "As shown in the position edit page")
    public int getPositionLevel() {
        DBAlliancePosition position = getAlliancePosition();
        if (position == null) {
            return 0;
        }
        return position.getPosition_level();
    }

    @Command(desc = "Globally unique id of the position in the alliance")
    public int getAlliancePositionId() {
        return alliancePosition;
    }

//    public boolean updateNationWithInfo(DBNation copyOriginal, NationMilitaryContainer nation, Consumer<Event> eventConsumer) {
//
//    }

    public boolean updateNationInfo(SNationContainer nation, Consumer<Event> eventConsumer) {
        boolean dirty = false;
        DBNation copyOriginal = null;
        if (nation.getNationid() != null && this.getNation_id() != nation.getNationid()) {
            if (copyOriginal == null && eventConsumer != null) copyOriginal = new DBNation(this);
            this.setNation_id(nation.getNationid());
            dirty = true;
        }

        if (nation.getNation() != null && (this.getNation() == null || !this.nation.equals(nation.getNation()))) {
            dirty = true;
            if (copyOriginal == null && eventConsumer != null) copyOriginal = new DBNation(this);
            this.nation = nation.getNation();
            if (eventConsumer != null) eventConsumer.accept(new NationChangeNameEvent(copyOriginal, this));
        }
        if (nation.getLeader() != null && (this.getLeader() == null || !this.leader.equals(nation.getLeader()))) {
            dirty = true;
            if (copyOriginal == null && eventConsumer != null) copyOriginal = new DBNation(this);
            this.leader = nation.getLeader();
            if (eventConsumer != null) eventConsumer.accept(new NationChangeLeaderEvent(copyOriginal, this));
        }
        if (nation.getContinent() != null) {
            Continent continent = Continent.valueOf(nation.getContinent().toUpperCase(Locale.ROOT).replace(" ", "_"));
            if (continent != this.continent) {
                dirty = true;
                if (copyOriginal == null && eventConsumer != null) copyOriginal = new DBNation(this);
                this.continent = continent;
                if (eventConsumer != null) eventConsumer.accept(new NationChangeContinentEvent(copyOriginal, this));
            }
        }
        if (nation.getWarPolicy() != null) {
            WarPolicy warPolicy = WarPolicy.parse(nation.getWarPolicy());
            if (warPolicy != this.war_policy) {
                dirty = true;
                if (copyOriginal == null && eventConsumer != null) copyOriginal = new DBNation(this);
                setWarPolicy(warPolicy);
                if (eventConsumer != null) eventConsumer.accept(new NationChangeWarPolicyEvent(copyOriginal, this));
            }
        }
        if (nation.getColor() != null) {
            NationColor color = NationColor.valueOf(nation.getColor().toUpperCase(Locale.ROOT));
            if (color != this.color) {
                dirty = true;
                if (copyOriginal == null && eventConsumer != null) copyOriginal = new DBNation(this);
                setColor(color);
                if (eventConsumer != null) {
                    if (copyOriginal.color == NationColor.BEIGE) eventConsumer.accept(new NationLeaveBeigeEvent(copyOriginal, this));
                    eventConsumer.accept(new NationChangeColorEvent(copyOriginal, this));
                }
            }
        }
        if (nation.getAllianceid() != null && this.alliance_id != nation.getAllianceid()) {
            dirty = true;
            if (copyOriginal == null && eventConsumer != null) copyOriginal = new DBNation(this);
            this.setAlliance_id(nation.getAllianceid());
            if (eventConsumer != null) eventConsumer.accept(new NationChangeAllianceEvent(copyOriginal, this));
        }
        if (nation.getAllianceposition() != null && (this.rank == null || this.rank.id != nation.getAllianceposition())) {
            dirty = true;
            if (copyOriginal == null && eventConsumer != null) copyOriginal = new DBNation(this);
            this.rank = Rank.byId(nation.getAllianceposition());
            if (eventConsumer != null) eventConsumer.accept(new NationChangeRankEvent(copyOriginal, this));
        }
        if (nation.getCities() != null && this.cities != nation.getCities()) {
            dirty = true;
            if (copyOriginal == null && eventConsumer != null) copyOriginal = new DBNation(this);
            this.cities = nation.getCities();
//            if (eventConsumer != null) eventConsumer.accept(new NationChangeCitiesEvent(copyOriginal, this));
        }
        if (nation.getScore() != null && nation.getScore() != this.score) {
            this.score = nation.getScore();
            dirty = true;
        }
        if (nation.getVacmode() != null && nation.getVacmode() != this.getVm_turns()) {
            dirty = true;
            if (copyOriginal == null && eventConsumer != null) copyOriginal = new DBNation(this);
            this.setLeaving_vm(TimeUtil.getTurn() + nation.getVacmode());
            if (eventConsumer != null) eventConsumer.accept(new NationChangeVacationEvent(copyOriginal, this));
        }
        if (nation.getMinutessinceactive() != null && nation.getMinutessinceactive() < this.active_m() - 3) {
            dirty = true;
            if (copyOriginal == null && eventConsumer != null) copyOriginal = new DBNation(this);
            this.last_active = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(nation.getMinutessinceactive());
            if (eventConsumer != null) eventConsumer.accept(new NationChangeActiveEvent(copyOriginal, this));
        }
        return dirty;
    }

    public boolean updateNationInfo(DBNation copyOriginal, com.politicsandwar.graphql.model.Nation nation, Consumer<Event> eventConsumer) {
        boolean dirty = false;
        if (nation.getWars_won() != null && this.wars_won != nation.getWars_won()) {
            wars_won = nation.getWars_won();
            dirty = true;
        }
        if (nation.getWars_lost() != null && this.wars_lost != nation.getWars_lost()) {
            wars_lost = nation.getWars_lost();
            dirty = true;
        }
        if (nation.getId() != null && this.getNation_id() != nation.getId()) {
            this.setNation_id(nation.getId());
            dirty = true;
        }
        if (nation.getDiscord_id() != null && !nation.getDiscord_id().isEmpty()) {
            Long newDiscordId = Long.parseLong(nation.getDiscord_id());
            Long thisUserId = getUserId();
            if (!newDiscordId.equals(thisUserId)) {
                User user = Locutus.imp().getDiscordApi().getUserById(newDiscordId);
                String name = user == null ? newDiscordId + "" : DiscordUtil.getFullUsername(user);
                Locutus.imp().getDiscordDB().addUser(new PNWUser(nation_id, newDiscordId, name));
                if (eventConsumer != null) {
                    eventConsumer.accept(new NationRegisterEvent(nation_id, null, user, thisUserId == null));
                }
            }
        }
        if (nation.getNation_name() != null && (this.getNation() == null || !this.getNation().equals(nation.getNation_name()))) {
            this.setNation(nation.getNation_name());
            if (eventConsumer != null) eventConsumer.accept(new NationChangeNameEvent(copyOriginal, this));
            dirty = true;
        }
        if (nation.getLeader_name() != null && (this.getLeader() == null || !this.getLeader().equals(nation.getLeader_name()))) {
            this.setLeader(nation.getLeader_name());
            if (eventConsumer != null) eventConsumer.accept(new NationChangeLeaderEvent(copyOriginal, this));
            dirty = true;
        }

        if (nation.getAlliance_id() != null && this.getAlliance_id() != (nation.getAlliance_id())) {
            this.setAlliance_id(nation.getAlliance_id());
            if (eventConsumer != null) eventConsumer.accept(new NationChangeAllianceEvent(copyOriginal, this));
            dirty = true;
        }

        if (nation.getLast_active() != null && this.lastActiveMs() != (nation.getLast_active().toEpochMilli())) {
            // No reason they should become less active, but guard for anyway
            long newActive = nation.getLast_active().toEpochMilli();
            long currentActive = this.last_active;
            this.setLastActive(nation.getLast_active().toEpochMilli());
            if (currentActive < newActive) {
                if (eventConsumer != null) eventConsumer.accept(new NationChangeActiveEvent(copyOriginal, this));
            }
            dirty = true;
        }
        if (nation.getScore() != null && this.getScore() != (nation.getScore())) {
            this.setScore(nation.getScore());
//            events.accept(new NationChangeScoreEvent(copyOriginal, this)); // Not useful
            dirty = true;
        }
        if (nation.getNum_cities() != null && this.getCities() != (nation.getNum_cities())) {
            this.setCities(nation.getNum_cities());
//            events.accept(new NationChangeCitiesEvent(copyOriginal, this)); // Not useful, call the event when DBCity is created instead
            dirty = true;
        }
        if (nation.getDomestic_policy() != null) {
            DomesticPolicy newPolicy = DomesticPolicy.parse(nation.getDomestic_policy().name());
            if (newPolicy != this.getDomesticPolicy()) {
                this.setDomesticPolicy(newPolicy);
                if (eventConsumer != null) eventConsumer.accept(new NationChangeDomesticPolicyEvent(copyOriginal, this));
                dirty = true;
            }
        }
        if (nation.getWar_policy() != null) {
            WarPolicy newPolicy = WarPolicy.parse(nation.getWar_policy().name());
            if (newPolicy != this.getWarPolicy()) {
                this.setWarPolicy(newPolicy);
                if (eventConsumer != null) eventConsumer.accept(new NationChangeWarPolicyEvent(copyOriginal, this));
                dirty = true;
            }
        }

        if (nation.getProject_bits() != null) {
            this.setProjectsRaw(nation.getProject_bits());
            if (copyOriginal != null && nation.getProject_bits() != copyOriginal.getProjectBitMask()) {
                if (copyOriginal.getProjectBitMask() != -1) {
                    Set<Project> originalProjects = copyOriginal.getProjects();
                    Set<Project> currentProjects = this.getProjects();
                    for (Project project : currentProjects) {
                        if (!originalProjects.contains(project)) {
                            if (eventConsumer != null) eventConsumer.accept(new NationCreateProjectEvent(copyOriginal, this, project));
                            this.setProjectTimer(TimeUtil.getTurn() + GameTimers.PROJECT.getTurns());
                        }
                    }
                    for (Project project : originalProjects) {
                        if (!currentProjects.contains(project)) {
                            if (eventConsumer != null) eventConsumer.accept(new NationDeleteProjectEvent(copyOriginal, this, project));
                        }
                    }
                }
                dirty = true;
            }
        }

        if (nation.getSoldiers() != null) {
            this.setSoldiers(nation.getSoldiers()); // For tracking last update time, even if no change
            if (copyOriginal != null && copyOriginal.getSoldiers() != (nation.getSoldiers())) {
                if (eventConsumer != null) eventConsumer.accept(new NationChangeUnitEvent(copyOriginal, this, MilitaryUnit.SOLDIER));
                dirty = true;
            }
        }
        if (nation.getTanks() != null) {
            this.setTanks(nation.getTanks()); // For tracking last update time, even if no change
            if (copyOriginal != null && copyOriginal.getTanks() != (nation.getTanks())) {
                if (eventConsumer != null) eventConsumer.accept(new NationChangeUnitEvent(copyOriginal, this, MilitaryUnit.TANK));
                dirty = true;
            }
        }
        if (nation.getAircraft() != null) {
            this.setAircraft(nation.getAircraft()); // For tracking last update time, even if no change
            if (copyOriginal != null && copyOriginal.getAircraft() != (nation.getAircraft())) {
                if (eventConsumer != null) eventConsumer.accept(new NationChangeUnitEvent(copyOriginal, this, MilitaryUnit.AIRCRAFT));
                dirty = true;
            }
        }
        if (nation.getShips() != null) {
            this.setShips(nation.getShips()); // For tracking last update time, even if no change
            if (copyOriginal != null && copyOriginal.getShips() != (nation.getShips())) {
                if (eventConsumer != null) eventConsumer.accept(new NationChangeUnitEvent(copyOriginal, this, MilitaryUnit.SHIP));
                dirty = true;
            }
        }
        if (nation.getMissiles() != null) {
            this.setMissiles(nation.getMissiles()); // For tracking last update time, even if no change
            if (copyOriginal != null && copyOriginal.getMissiles() != (nation.getMissiles())) {
                if (eventConsumer != null) eventConsumer.accept(new NationChangeUnitEvent(copyOriginal, this, MilitaryUnit.MISSILE));
                dirty = true;
            }
        }
        if (nation.getNukes() != null) {
            this.setNukes(nation.getNukes()); // For tracking last update time, even if no change
            if (copyOriginal != null && copyOriginal.getNukes() != (nation.getNukes())) {
                if (eventConsumer != null) eventConsumer.accept(new NationChangeUnitEvent(copyOriginal, this, MilitaryUnit.NUKE));
                dirty = true;
            }
        }
        if (nation.getSpies() != null) {
            this.setSpies(nation.getSpies(), false);
            if (copyOriginal != null && copyOriginal.getSpies() != (nation.getSpies())) {
                if (eventConsumer != null) eventConsumer.accept(new NationChangeUnitEvent(copyOriginal, this, MilitaryUnit.SPIES));
                dirty = true;
            }
        }
        if (nation.getSpy_attacks() != null) {
            setSpyAttacks(nation.getSpy_attacks());
        }
        if (nation.getVacation_mode_turns() != null && this.getVm_turns() != nation.getVacation_mode_turns()) {
            long turnEnd = TimeUtil.getTurn() + nation.getVacation_mode_turns();
            this.setLeaving_vm(turnEnd);
            if (eventConsumer != null) eventConsumer.accept(new NationChangeVacationEvent(copyOriginal, this));
            dirty = true;
        }
        if (nation.getColor() != null) {
            NationColor color = NationColor.valueOf(nation.getColor().toUpperCase(Locale.ROOT));
            if (color != this.getColor()) {
                this.setColor(color);
                if (eventConsumer != null) {
                    if (copyOriginal.color == NationColor.BEIGE) {
                        eventConsumer.accept(new NationLeaveBeigeEvent(copyOriginal, this));
                    }
                    eventConsumer.accept(new NationChangeColorEvent(copyOriginal, this));
                }
                dirty = true;
            }
        }
        if (nation.getDate() != null && nation.getDate().toEpochMilli() != this.getDate()) {
            this.setDate(nation.getDate().toEpochMilli());
            dirty = true;
        }
        if (nation.getAlliance_position() != null && Rank.from(nation.getAlliance_position()) != this.getPositionEnum()) {
            this.setPosition(Rank.from(nation.getAlliance_position()));
            if (eventConsumer != null) eventConsumer.accept(new NationChangeRankEvent(copyOriginal, this));
            dirty = true;
        }
        if (nation.getAlliance_position_id() != null && nation.getAlliance_position_id() != this.getAlliancePositionId()) {
            this.setAlliancePositionId(nation.getAlliance_position_id());
            if (eventConsumer != null) eventConsumer.accept(new NationChangePositionEvent(copyOriginal, this));
            dirty = true;
        }
        if (nation.getContinent() != null) {
            Continent continent = Continent.parseV3(nation.getContinent().toUpperCase(Locale.ROOT));
            if (continent != this.getContinent()) {
                this.setContinent(continent);
                if (eventConsumer != null) eventConsumer.accept(new NationChangeContinentEvent(copyOriginal, this));
                dirty = true;
            }
        }
        if (nation.getTurns_since_last_city() != null && nation.getTurns_since_last_city() < GameTimers.CITY.getTurns() && this.getCityTurns() != nation.getTurns_since_last_city()) {
            long turnEnd = TimeUtil.getTurn() - nation.getTurns_since_last_city() + GameTimers.CITY.getTurns();
            this.setCityTimer(turnEnd);
//            events.accept(new NationChangeEvent(copyOriginal, this));
            dirty = true;
        }
        if (nation.getTurns_since_last_project() != null && nation.getTurns_since_last_project() < GameTimers.PROJECT.getTurns() && this.getProjectTurns() != nation.getTurns_since_last_project()) {
            long turnEnd = TimeUtil.getTurn() - nation.getTurns_since_last_project() + GameTimers.PROJECT.getTurns();
            this.setProjectTimer(turnEnd);
//            events.accept(new NationChangeEvent(copyOriginal, this));
            dirty = true;
        }
        if (nation.getBeige_turns() != null && nation.getBeige_turns() > 0 && nation.getBeige_turns() != this.getBeigeTurns()) {
            long turnEnd = TimeUtil.getTurn() + nation.getBeige_turns();
            this.setBeigeTimer(turnEnd);
            dirty = true;
        }
        if (nation.getEspionage_available() != null) {
            if (nation.getEspionage_available() != (this.isEspionageAvailable())) {
                if (nation.getEspionage_available()) {
                    this.setEspionageFull(false);
                } else {
                    this.setEspionageFull(true);
                }
                if (eventConsumer != null && this.getVm_turns() > 0) eventConsumer.accept(new NationChangeSpyFullEvent(copyOriginal, this));
            } else {
                // Set to same so that last update can be tracked
                this.setEspionageFull(this.isEspionageFull());
            }
        }
        // DC can be changed by unit changes
        if (copyOriginal != null && copyOriginal.getDc_turn() != this.getDc_turn()) {
            if (eventConsumer != null) eventConsumer.accept(new NationChangeDCEvent(copyOriginal, this));
        }
        if (nation.getWars_won() != null && nation.getWars_won() != this.getWars_won()) {
            this.setWars_won(nation.getWars_won());
            dirty = true;
        }
        if (nation.getWars_lost() != null && nation.getWars_lost() != this.getWars_lost()) {
            this.setWars_lost(nation.getWars_lost());
            dirty = true;
        }
//        if (nation.getSpy_kills() != null && nation.getSpy_kills() != this.getSpy_kills()) {
//            this.setSpy_kills(nation.getSpy_kills());
//            if (eventConsumer != null) eventConsumer.accept(new NationChangeSpyKillsEvent(copyOriginal, this));
//            dirty = true;
//        }
//        if (nation.getSpy_casualties() != null) {
//            this.setSpy_casualties(nation.getSpy_casualties());
//            if (copyOriginal != null && nation.getSpy_casualties() != copyOriginal.getSpy_casualties()) {
//                if (eventConsumer != null) eventConsumer.accept(new NationChangeSpyCasualtiesEvent(copyOriginal, this));
//                dirty = true;
//            }
//        }
        if (copyOriginal != null && copyOriginal.getSpies() != getSpies()) {
            if (eventConsumer != null) eventConsumer.accept(new NationChangeUnitEvent(copyOriginal, this, MilitaryUnit.SPIES));
            dirty = true;
        }
        if (nation.getTax_id() != null && this.getTax_id() != nation.getTax_id()) {
            this.setTax_id(nation.getTax_id());
            if (eventConsumer != null) eventConsumer.accept(new NationChangeTaxBracketEvent(copyOriginal, this));
            dirty = true;
        }
        if (nation.getGross_national_income() != null && Math.round((this.gni - nation.getGross_national_income()) * 100) != 0) {
            this.setGNI(nation.getGross_national_income());
            if (eventConsumer != null) eventConsumer.accept(new NationChangeGNIEvent(copyOriginal, this));
            dirty = true;
        }
//        if (nation.getGross_domestic_product() != null && Math.round((this.gdp - nation.getGross_domestic_product()) * 100) != 0) {
//            this.setGDP(nation.getGross_domestic_product());
////            if (eventConsumer != null) eventConsumer.accept(new NationChangeGDPEvent(copyOriginal, this));
//            dirty = true;
//        }
        return dirty;
    }

    private void setSpyAttacks(int spyAttacks) {
        ByteBuffer lastSpyOpDayBuf = getMeta(NationMeta.SPY_OPS_DAY);
        long currentDay = TimeUtil.getDay();
        long lastOpDay = lastSpyOpDayBuf == null ? 0L : lastSpyOpDayBuf.getLong();
        if (lastOpDay != currentDay) {
            setMeta(NationMeta.SPY_OPS_DAY, currentDay);
        }
        int lastAmt;
        if (currentDay == lastOpDay) {
            ByteBuffer dailyOpAmt = getMeta(NationMeta.SPY_OPS_AMOUNT_DAY);
            lastAmt = dailyOpAmt == null ? 0 : dailyOpAmt.getInt();
        } else {
            lastAmt = 0;
        }
        int newOps = spyAttacks - lastAmt;
        if (newOps > 0) {
            setMeta(NationMeta.SPY_OPS_AMOUNT_DAY, spyAttacks);
            ByteBuffer totalOpAmtBuf = getMeta(NationMeta.SPY_OPS_AMOUNT_TOTAL);
            int total = totalOpAmtBuf == null ? 0 : totalOpAmtBuf.getInt();
            setMeta(NationMeta.SPY_OPS_AMOUNT_TOTAL, total + newOps);
        }
    }

    public DBAlliancePosition getAlliancePosition() {
        if (alliance_id == 0 || rank.id <= Rank.APPLICANT.id) return null;
        DBAlliancePosition pos = Locutus.imp().getNationDB().getPosition(alliancePosition, alliance_id, false);
        if (pos == null) {
            long permission_bits = 0;
            switch (this.rank) {
                case LEADER:
                case HEIR:
                    permission_bits = Long.MAX_VALUE;
                    break;
                case OFFICER:
                    permission_bits |= (1 << AlliancePermission.VIEW_BANK.ordinal());
                    permission_bits |= (1 << AlliancePermission.CHANGE_PERMISSIONS.ordinal());
                    permission_bits |= (1 << AlliancePermission.POST_ANNOUNCEMENTS.ordinal());
                    permission_bits |= (1 << AlliancePermission.MANAGE_ANNOUNCEMENTS.ordinal());
                    permission_bits |= (1 << AlliancePermission.ACCEPT_APPLICANTS.ordinal());
                    permission_bits |= (1 << AlliancePermission.REMOVE_MEMBERS.ordinal());
                    permission_bits |= (1 << AlliancePermission.EDIT_ALLIANCE_INFO.ordinal());
                    permission_bits |= (1 << AlliancePermission.MANAGE_TREATIES.ordinal());
                    break;
                case MEMBER:
                    break;
            }
            pos = new DBAlliancePosition(alliancePosition, alliance_id, rank.key, -1, -1, this.rank, permission_bits);
        }
        return pos;
    }

    public void setPosition(Rank rank) {
        this.rank = rank;
    }

    public void setAlliancePositionId(int position) {
        this.alliancePosition = position;
    }

    @Command(desc = "Continent")
    public Continent getContinent() {
        return continent;
    }

    public void setContinent(Continent continent) {
        this.continent = continent;
    }

    @Command(desc = "Turn epoch when project timer expires")
    public Long getProjectAbsoluteTurn() {
        return projectTimer;
    }

    public DBNation(SNationContainer wrapper) {
        updateNationInfo(wrapper, null);

        if (this.isBeige() && beigeTimer == 0) {
            this.beigeTimer = TimeUtil.getTurn() + 14 * 12;
        }
        this.spies = -1;
    }

    private Number cast(Number t) {
        return t == null ? (Number) 0 : t;
    }

    @Command(desc = "Effective ground strength with munitions and enemy air control")
    public double getGroundStrength(boolean munitions, boolean enemyAc) {
        return soldiers * (munitions ? 1.75 : 1) + (tanks * 40) * (enemyAc ? 0.66 : 1);
    }

    @Command(desc = "Effective ground strength with munitions, enemy air control, and daily rebuy")
    public double getGroundStrength(boolean munitions, boolean enemyAc, double includeRebuy) {
        int soldiers = this.soldiers;
        int tanks = this.tanks;
        if (includeRebuy > 0) {
            int barracks = Buildings.BARRACKS.cap(this::hasProject) * cities;
            int soldierMax = Buildings.BARRACKS.max() * barracks;
            int soldPerDay = barracks * Buildings.BARRACKS.perDay();

            soldiers = Math.min(soldierMax, (int) (soldiers + soldPerDay * includeRebuy));

            int factories = Buildings.FACTORY.cap(this::hasProject) * cities;
            int tankMax = Buildings.FACTORY.max() * barracks;
            int tankPerDay = barracks * Buildings.FACTORY.perDay();

            tanks = Math.min(tankMax, (int) (tanks + tankPerDay * includeRebuy));
        }
        return soldiers * (munitions ? 1.75 : 1) + (tanks * 40) * (enemyAc ? 0.66 : 1);
    }

    @Command(desc = "Get number of buildings")
    public double getAvgBuilding(Building building) {
        // TODO
        long total = 0;
        Map<Integer, DBCity> cities = _getCitiesV3();
        for (Map.Entry<Integer, DBCity> entry : cities.entrySet()) {
            DBCity city = entry.getValue();
            total += city.get(building);
        }
        return total / (double) cities.size();
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

    public boolean setMetaRaw(int id, byte[] value) {
        Int2ObjectArrayMap<byte[]> metaCache = getCache(true).metaCache;
        boolean changed = false;
        if (metaCache == null) {
            cache.metaCache = metaCache = new Int2ObjectArrayMap<>();
            changed = true;
        } else {
            byte[] existing = metaCache.get(id);
            changed = existing == null || !Arrays.equals(existing, value);
        }
        if (changed) {
            metaCache.put(id, value);
            return true;
        }
        return false;
    }

    public void setMeta(NationMeta key, byte... value) {
        if (setMetaRaw(key.ordinal(), value)) {
            Locutus.imp().getNationDB().setMeta(nation_id, key, value);
        }
    }

    public ByteBuffer getMeta(NationMeta key) {
        if (cache == null) {
            return null;
        }
        if (cache.metaCache == null) {
            return null;
        }
        byte[] result = cache.metaCache.get(key.ordinal());
        return result == null ? null : ByteBuffer.wrap(result);
    }

    public void deleteMeta(NationMeta key) {
        if (cache != null) {
            if (cache.metaCache != null) {
                if (cache.metaCache.remove(key.ordinal()) != null) {
                    Locutus.imp().getNationDB().deleteMeta(nation_id, key);
                }
            }
        }
    }

    @Command(desc = "Most recent spy count")
    public int getSpies() {
        return Math.max(spies, 0);
    }

    public void setSpies(int spies, boolean events) {
        getCache().processUnitChange(this, MilitaryUnit.SPIES, this.spies, spies);
        if (events && this.spies != spies) {
            DBNation copyOriginal = new DBNation(this);
            this.spies = spies;

            Locutus.imp().getNationDB().saveNation(this);

            new NationChangeUnitEvent(copyOriginal, this, MilitaryUnit.SPIES).post();
        }
        this.spies = spies;
    }

    public double[] getNetDeposits(GuildDB db, boolean priority) throws IOException {
        return getNetDeposits(db, 0L, priority);
    }

    public double[] getNetDeposits(GuildDB db, boolean includeGrants, boolean priority) throws IOException {
        return getNetDeposits(db, null, true, true, includeGrants, 0L, 0L, priority);
    }

    public double[] getNetDeposits(GuildDB db, boolean includeGrants, long updateThreshold, boolean priority) throws IOException {
        return getNetDeposits(db, null, true, true, includeGrants, updateThreshold, 0L, priority);
    }

    public double[] getNetDeposits(GuildDB db, long updateThreshold, boolean priority) throws IOException {
        return getNetDeposits(db, null, true, true, updateThreshold, 0L, priority);
    }

    public double[] getNetDeposits(GuildDB db, Set<Long> tracked, boolean useTaxBase, boolean offset, long updateThreshold, long cutOff, boolean priority) throws IOException {
        return getNetDeposits(db, tracked, useTaxBase, offset, true, updateThreshold, cutOff, priority);
    }

    public double[] getNetDeposits(GuildDB db, Set<Long> tracked, boolean useTaxBase, boolean offset, boolean includeGrants, long updateThreshold, long cutOff, boolean priority) throws IOException {
        long start = System.currentTimeMillis();
        Map<DepositType, double[]> result = getDeposits(db, tracked, useTaxBase, offset, updateThreshold, cutOff, priority);
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
        return PnwUtil.convertedTotal(getNetDeposits(db, updateThreshold, false));
    }

    public List<Transaction2> getTransactions(boolean priority) {
        return getTransactions(0, priority);
    }

    public List<Transaction2> updateTransactions(boolean priority) {
        BankDB bankDb = Locutus.imp().getBankDB();
        if (Settings.USE_V2) {
            Locutus.imp().runEventsAsync(events -> bankDb.updateBankRecs(nation_id, priority, events));
        } else if (Settings.INSTANCE.TASKS.BANK_RECORDS_INTERVAL_SECONDS > 0) {
            System.out.println("Update bank recs 0");
            Locutus.imp().runEventsAsync(f -> bankDb.updateBankRecs(priority, f));
        } else {
            System.out.println("Update bank recs 1");
            Locutus.imp().runEventsAsync(events -> bankDb.updateBankRecs(nation_id, priority, events));
            System.out.println("Update bank recs 2");
        }
        return Locutus.imp().getBankDB().getTransactionsByNation(nation_id);
    }

    @Command(desc = "Days since the last three consecutive daily logins")
    public long daysSince3ConsecutiveLogins() {
        return daysSinceConsecutiveLogins(1200, 3);
    }

    @Command(desc = "Days since the last four consecutive daily logins")
    public long daysSince4ConsecutiveLogins() {
        return daysSinceConsecutiveLogins(1200, 4);
    }

    @Command(desc = "Days since the last five consecutive daily logins")
    public long daysSince5ConsecutiveLogins() {
        return daysSinceConsecutiveLogins(1200, 5);
    }

    @Command(desc = "Days since the last six consecutive daily logins")
    public long daysSince6ConsecutiveLogins() {
        return daysSinceConsecutiveLogins(1200, 6);
    }

    @Command(desc = "Days since the last seven consecutive daily logins")
    public long daysSince7ConsecutiveLogins() {
        return daysSinceConsecutiveLogins(1200, 7);
    }

    @Command(desc = "Days since last specified consecutive daily logins")
    public long daysSinceConsecutiveLogins(long checkPastXDays, int sequentialDays) {
        long currentDay = TimeUtil.getDay();
        long turns = checkPastXDays * 12 + 11;
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
            if (tally >= sequentialDays) return currentDay - day;
            last = day;
        }
        return Long.MAX_VALUE;
    }

    @Command(desc = "Days since last bank deposit")
    public double daysSinceLastBankDeposit() {
        return (System.currentTimeMillis() - lastBankDeposit()) / (double) TimeUnit.DAYS.toMillis(1);
    }

    @Command(desc = "Unix timestamp when last deposited in a bank")
    public long lastBankDeposit() {
        if (getPositionEnum().id <= Rank.APPLICANT.id) return 0;
        List<Transaction2> transactions = getTransactions(Long.MAX_VALUE, false);
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
    public List<Transaction2> getTransactions(long updateThreshold, boolean priority) {
        boolean update = updateThreshold == 0;
        if (!update && updateThreshold > 0) {
            Transaction2 tx = Locutus.imp().getBankDB().getLatestTransaction();
            if (updateThreshold < Long.MAX_VALUE) {
                if (tx == null || tx.tx_datetime < last_active) update = true;
                else if (System.currentTimeMillis() - tx.tx_datetime > updateThreshold) update = true;
            }
        }
        if (update) {
            System.out.println("Update transactions");
            return updateTransactions(priority);
        }
        return Locutus.imp().getBankDB().getTransactionsByNation(nation_id);
    }

    public Map<Long, Long> getLoginNotifyMap() {
        ByteBuffer existing = getMeta(NationMeta.LOGIN_NOTIFY);
        Map<Long, Long> existingMap = new LinkedHashMap<>();
        if (existing != null) {
            while (existing.hasRemaining()) {
                existingMap.put(existing.getLong(), existing.getLong());
            }
        } else {
            return null;
        }
        existingMap.entrySet().removeIf(e -> e.getValue() < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(5));
        return existingMap;
    }

    public void setLoginNotifyMap(Map<Long, Long> map) {
        ByteBuffer buffer = ByteBuffer.allocate(map.size() * 16);
        map.forEach((k, v) -> buffer.putLong(k).putLong(v));
        setMeta(NationMeta.LOGIN_NOTIFY, buffer.array());
    }

    @Command(desc = "Total offensive espionage spy slots")
    public int getOffSpySlots() {
        if (hasProject(Projects.INTELLIGENCE_AGENCY)) {
            return 2;
        }
        return 1;
    }

    public long getDateCheckedUnits() {
        if (cache == null) return 0;
        return cache.lastCheckUnitMS;
    }

    public Long getTimeUpdatedSpies() {
        if (cache != null) {
            return cache.lastCheckUnitMS;
        }
        return null;
    }

    public Integer updateSpies(PagePriority priority) {
        return spies;
    }

    public Integer updateSpies(PagePriority priority, boolean update, boolean force) {
        return spies;
    }

    public Integer updateSpies(PagePriority priority, boolean force) {
        return spies;
    }

    public Integer updateSpies(PagePriority priority, int turns) {
        return spies;
    }

    public static class LoginFactor {
        private final Function<DBNation, Double> function;
        public final String name;
        private final Map<DBNation, Double> functionCache;

        public LoginFactor(String name, Function<DBNation, Double> function) {
            this.name = name;
            this.function = function;
            this.functionCache = new HashMap<>();
        }

        public double get(DBNation nation) {
            return functionCache.computeIfAbsent(nation, function);
        }

        public boolean matches(double candidate, double target) {
            return candidate == target;
        }

        public String toString(double value) {
            return MathMan.format(value);
        }
    }

    public static Map<LoginFactor, Double> getLoginFactorPercents(DBNation nation) {
        long start = System.currentTimeMillis();
        List<DBNation.LoginFactor> factors = DBNation.getLoginFactors(nation);
        System.out.println("login pcts 1: " + (( - start) + (start = System.currentTimeMillis())) + "ms");

        long turnNow = TimeUtil.getTurn();
        int maxTurn = 30 * 12;
        int candidateTurnInactive = (int) (turnNow - TimeUtil.getTurn(nation.lastActiveMs()));

        System.out.println("login pcts 2: " + (( - start) + (start = System.currentTimeMillis())) + "ms");

        Set<DBNation> nations1dInactive = Locutus.imp().getNationDB().getNationsMatching(f -> f.active_m() >= 1440 && f.getVm_turns() == 0 && f.active_m() <= TimeUnit.DAYS.toMinutes(30));
        NationScoreMap<DBNation> inactiveByTurn = new NationScoreMap<DBNation>(nations1dInactive, f -> {
            return (double) (turnNow - TimeUtil.getTurn(f.lastActiveMs()));
        }, 1, 1);

        System.out.println("login pcts 3: " + (( - start) + (start = System.currentTimeMillis())) + "ms");

        Map<LoginFactor, Double> result = new LinkedHashMap<>();
        for (DBNation.LoginFactor factor : factors) {
            long start2 = System.currentTimeMillis();
            Predicate<DBNation> matches = f -> factor.matches(factor.get(nation), factor.get(f));
            BiFunction<Integer, Integer, Integer> sumFactor = inactiveByTurn.getSummedFunction(matches);

            int numCandidateActivity = sumFactor.apply(Math.min(maxTurn - 23, candidateTurnInactive), Math.min(maxTurn, candidateTurnInactive + 24));
            int numInactive = Math.max(1, sumFactor.apply(14 * 12, 30 * 12) / (30 - 14));

            long diff = System.currentTimeMillis() - start2;
            if (diff > 5) {
                System.out.println("Diff " + factor.name + ": " + diff + "ms");
            }

            double loginPct = Math.min(0.95, Math.max(0.05, numCandidateActivity > numInactive ? (1d - ((double) (numInactive) / (double) numCandidateActivity)) : 0)) * 100;
            result.put(factor, loginPct);
        }
        return result;
    }

    public static List<LoginFactor> getLoginFactors(DBNation nationOptional) {
        List<LoginFactor> factors = new ArrayList<>();
        factors.add(new LoginFactor("age", new Function<DBNation, Double>() {
            @Override
            public Double apply(DBNation f) {
                return (double) f.lastActiveMs() - f.getDate();
            }
        }) {
            @Override
            public boolean matches(double candidate, double target) {
                return target >= candidate * 0.75 && target <= candidate * 1.5;
            }

            @Override
            public String toString(double value) {
                return TimeUtil.secToTime(TimeUnit.MILLISECONDS, (long) value);
            }
        });

        factors.add(new LoginFactor("cities", new Function<DBNation, Double>() {
            @Override
            public Double apply(DBNation f) {
                return (double) f.getCities();
            }
        }) {
            @Override
            public boolean matches(double candidate, double target) {
                return target >= candidate * 0.75 && target <= candidate * 1.5;
            }
        });

        if (nationOptional == null || nationOptional.getCities() <= 10) {
            factors.add(new LoginFactor("projects", new Function<DBNation, Double>() {
                @Override
                public Double apply(DBNation f) {
                    return f.getProjectBitMask() > 0 ? 1d : 0d;
                }
            }) {
                @Override
                public boolean matches(double candidate, double target) {
                    return candidate == target;
                }

                @Override
                public String toString(double value) {
                    return value > 0 ? "yes" : "no";
                }
            });

            factors.add(new LoginFactor("turtle", new Function<DBNation, Double>() {
                @Override
                public Double apply(DBNation f) {
                    return f.getWarPolicy() == WarPolicy.TURTLE ? 1d : 0d;
                }
            }) {
                @Override
                public boolean matches(double candidate, double target) {
                    return candidate == target;
                }

                @Override
                public String toString(double value) {
                    return value > 0 ? "yes" : "no";
                }
            });
        }

        factors.add(new LoginFactor("position", new Function<DBNation, Double>() {
            @Override
            public Double apply(DBNation f) {
                return (double) (Math.min(f.getPositionEnum().id, Rank.MEMBER.id));
            }
        }) {
            @Override
            public boolean matches(double candidate, double target) {
                return candidate == target;
            }

            @Override
            public String toString(double value) {
                return Rank.byId((int) value).name();
            }
        });

        factors.add(new LoginFactor("alliancerank", new Function<DBNation, Double>() {
            private static Map<Integer, Integer> RANK_CACHE = new Int2IntOpenHashMap();
            @Override
            public Double apply(DBNation f) {
                if (f.getAlliance_id() == 0) return Double.MAX_VALUE;

                Integer cachedRank = RANK_CACHE.get(f.getAlliance_id());
                if (cachedRank == null) {
                    DBAlliance alliance = f.getAlliance(false);
                    if (alliance != null) {
                        RANK_CACHE.put(f.getAlliance_id(), alliance.getRank());
                        return (double) alliance.getRank();
                    }
                } else {
                    return cachedRank.doubleValue();
                }
                return Double.MAX_VALUE;
            }
        }) {
            @Override
            public boolean matches(double candidate, double target) {
                return Math.max(candidate, 30) >= Math.max(30, target);
            }

            @Override
            public String toString(double value) {
                return value == Double.MAX_VALUE ? "none" : "#" + (int) value;
            }
        });

        factors.add(new LoginFactor("recentwar", new Function<DBNation, Double>() {
            @Override
            public Double apply(DBNation f) {
                DBWar lastWar = Locutus.imp().getWarDb().getLastDefensiveWar(f.nation_id);
                if (lastWar != null) {
                    long warDiff = f.lastActiveMs() - TimeUnit.DAYS.toMillis(10);
                    if (lastWar.date > warDiff) {
                        if (lastWar.status == WarStatus.PEACE || lastWar.status == WarStatus.DEFENDER_VICTORY) {
                            return -1d;
                        } else {
                            return 1d;
                        }
                    }
                }
                return 0d;
            }
        }) {
            @Override
            public boolean matches(double candidate, double target) {
                return candidate == target;
            }

            @Override
            public String toString(double value) {
                return value > 0 ? "lost" : value < 0 ? "won" : "no";
            }
        });

        if (nationOptional == null || nationOptional.isBeige() || nationOptional.isGray()) {
            factors.add(new LoginFactor("grayorbeige", new Function<DBNation, Double>() {
                @Override
                public Double apply(DBNation f) {
                    return f.isGray() || f.isBeige() ? 1d : 0d;
                }
            }) {
                @Override
                public boolean matches(double candidate, double target) {
                    return (candidate == 0) || (target > 0);
                }

                @Override
                public String toString(double value) {
                    return value > 0 ? "yes" : "no";
                }
            });
        }

        factors.add(new LoginFactor("verified", new Function<DBNation, Double>() {
            @Override
            public Double apply(DBNation f) {
                return f.isVerified() ? 1d : 0d;
            }
        }) {
            @Override
            public boolean matches(double candidate, double target) {
                return candidate == target;
            }

            @Override
            public String toString(double value) {
                return value > 0 ? "yes" : "no";
            }
        });

        factors.add(new LoginFactor("lastoffensive", new Function<DBNation, Double>() {
            @Override
            public Double apply(DBNation f) {
                double days = f.daysSinceLastOffensive() - ((System.currentTimeMillis() - f.lastActiveMs()) / (double) TimeUnit.DAYS.toMillis(1));
                return days;
            }
        }) {
            @Override
            public boolean matches(double candidate, double target) {
                return (candidate > 10000 && target > 10000) || candidate <= target + 7;
            }

            @Override
            public String toString(double value) {
                return MathMan.format(value) +"d";
            }
        });

        factors.add(new LoginFactor("lastbank", new Function<DBNation, Double>() {
            private static Map<Integer, Double> BANK_DAYS_CACHE  = new Int2DoubleOpenHashMap();
            private static Map<Integer, Long> BANK_CACHE_DATE = new Int2LongOpenHashMap();
            long originalBankCache = 0;
            @Override
            public Double apply(DBNation f) {
                Double daysSinceLastDepo = BANK_DAYS_CACHE.get(f.getNation_id());
                if (daysSinceLastDepo == null || f.lastActiveMs() > BANK_CACHE_DATE.getOrDefault(f.getNation_id(), 0L)) {
                    daysSinceLastDepo = f.daysSinceLastBankDeposit();
                    BANK_DAYS_CACHE.put(f.getNation_id(), daysSinceLastDepo);
                    BANK_CACHE_DATE.put(f.getNation_id(), System.currentTimeMillis());
                }
                double days = daysSinceLastDepo - ((System.currentTimeMillis() - f.lastActiveMs()) / (double) TimeUnit.DAYS.toMillis(1));
                return days;
            }
        }) {
            @Override
            public boolean matches(double candidate, double target) {
                return (candidate > 10000 && target > 10000) || candidate <= target + 7;
            }

            @Override
            public String toString(double value) {
                return MathMan.format(value) +"d";
            }
        });

        factors.add(new LoginFactor("aircraft", new Function<DBNation, Double>() {
            @Override
            public Double apply(DBNation f) {
                return (double) f.getAircraft();
            }
        }) {
            @Override
            public boolean matches(double candidate, double target) {
                return candidate >= target;
            }
        });

        factors.add(new LoginFactor("consecutive", new Function<DBNation, Double>() {
            private static Map<Integer, Long> CONSECUTIVE_DAYS_CACHE  = new Int2LongOpenHashMap();
            private static Map<Integer, Long> CONSECUTIVE_CACHE_DATE = new Int2LongOpenHashMap();
            @Override
            public Double apply(DBNation f) {
                Long daysSinceLastConsecutive = CONSECUTIVE_DAYS_CACHE.get(f.getNation_id());
                if (daysSinceLastConsecutive == null || f.lastActiveMs() > CONSECUTIVE_CACHE_DATE.getOrDefault(f.getNation_id(), 0L)) {
                    daysSinceLastConsecutive = f.daysSince7ConsecutiveLogins();
                    CONSECUTIVE_DAYS_CACHE.put(f.getNation_id(), daysSinceLastConsecutive);
                    CONSECUTIVE_CACHE_DATE.put(f.getNation_id(), System.currentTimeMillis());
                }
                double days = daysSinceLastConsecutive - ((System.currentTimeMillis() - f.lastActiveMs()) / (double) TimeUnit.DAYS.toMillis(1));
                return days;
            }
        }) {
            @Override
            public boolean matches(double candidate, double target) {
                return (candidate > 10000 && target > 10000) || candidate <= target + 7;
            }

            @Override
            public String toString(double value) {
                return value > Integer.MAX_VALUE ? "infrequent" : MathMan.format(value) +"d";
            }
        });
        return factors;
    }

    @Deprecated
    public Map.Entry<String, String> generateRecruitmentMessage(boolean force) throws InterruptedException, ExecutionException, IOException {
        StringBuilder body = new StringBuilder();
        Map<Integer, JavaCity> cities = getCityMap(true);
        body.append("Hey hey! I'm Danzek. If you would like any help, feel free to ask me here or on discord! :D<br>" +
                "Here are some beginner tips for you<hr><br>");
        if (cities.size() == 1) {
            body.append("<a href=\"https://politicsandwar.com/nation/objectives/\" class=\"btn btn-warning\"><i class=\"fas fa-book\" aria-hidden=\"true\"></i> Open Objectives</a>");
            body.append("<p>The tutorial runs you through some of the basics, and gives you some cash right away. Let me know if you need any assistance with them:</p>");
            body.append("<hr><br>");
        }
        if (getOff() < getMaxOff()) {
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

        double[] daily = PnwUtil.getRevenue(null, 12, this, cityMap.values(), true, true, true, false, false);
        double[] turn = PnwUtil.getRevenue(null,  1, this, citiesNoRaws.values(), true, true, true, false, false);
        double[] turn2 = PnwUtil.getRevenue(null,  1, this, citiesNoRaws.values(), true, true, true, true, false);
        turn[ResourceType.MONEY.ordinal()] = Math.min(turn[ResourceType.MONEY.ordinal()], turn2[ResourceType.MONEY.ordinal()]);
        turn[ResourceType.FOOD.ordinal()] = Math.min(turn[ResourceType.FOOD.ordinal()], turn2[ResourceType.FOOD.ordinal()]);

//        turn[0] = Math.min(daily[0], turn[0]);

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

    public Map<ResourceType, Double> getStockpile() {
        ApiKeyPool pool;
        ApiKeyPool.ApiKey myKey = getApiKey(false);

        DBAlliance alliance = getAlliance();
        if (myKey != null) {
            pool  = ApiKeyPool.create(myKey);
        } else if (getPositionEnum().id <= Rank.APPLICANT.id || alliance == null) {
            throw new IllegalArgumentException("Nation " + nation + " is not member in an alliance");
        } else {
            pool = alliance.getApiKeys(AlliancePermission.SEE_SPIES);
            if (pool == null) {
                throw new IllegalArgumentException("No api key found. Please use" + CM.credentials.addApiKey.cmd.toSlashMention() + "");
            }
        }

        double[] stockpile = new PoliticsAndWarV3(pool).getStockPile(f -> f.setId(List.of(nation_id))).get(nation_id);
        return stockpile == null ? null : PnwUtil.resourcesToMap(stockpile);
    }

    public ApiKeyPool.ApiKey getApiKey(boolean dummy) {
        return Locutus.imp().getDiscordDB().getApiKey(nation_id);
    }

    @Command(desc = "Check if the nation has all permissions")
    public boolean hasAllPermission(Set<AlliancePermission> permissions) {
        if (rank.id >= Rank.HEIR.id) return true;
        if (permissions == null || permissions.isEmpty()) return true;
        DBAlliancePosition position = getAlliancePosition();
        if (position == null) return false;
        for (AlliancePermission perm : permissions) {
            if (!position.hasPermission(perm)) return false;
        }
        return true;
    }

    @Command(desc = "Check if the nation has a permissions")
    public boolean hasPermission(AlliancePermission permission) {
        return hasAllPermission(Set.of(permission));
    }

    @Command(desc = "Check if the nation has any permissions")
    public boolean hasAnyPermission(Set<AlliancePermission> permissions) {
        if (rank.id >= Rank.HEIR.id) return true;
        if (permissions == null || permissions.isEmpty()) return true;
        DBAlliancePosition position = getAlliancePosition();
        if (position == null) return false;
        for (AlliancePermission perm : permissions) {
            if (position.hasPermission(perm)) return true;
        }
        return false;
    }

    public String commend(boolean isCommend) throws IOException {
        ApiKeyPool.ApiKey key = getApiKey(true);
        String url = "https://politicsandwar.com/api/denouncements.php";
        Map<String, String> post = new HashMap<>();

        if (isCommend) {
            post.put("action", "commendment");
        } else {
            post.put("action", "denouncement");
        }
        post.put("account_id", Settings.INSTANCE.NATION_ID + "");
        post.put("target_id", getNation_id() + "");
        post.put("api_key", key.getKey());
        Locutus.imp().getRootAuth().readStringFromURL(PagePriority.COMMEND, url, post);

        String actionStr = isCommend ? "commended" : "denounced";
        return "Borg has publicly " + actionStr + " the nation of " + getNation() + " led by " + getLeader() + ".";
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
    public Map<DepositType, double[]> getDeposits(GuildDB db, Set<Long> tracked, boolean useTaxBase, boolean offset, long updateThreshold, long cutOff, boolean priority) {
        return getDeposits(db, tracked, useTaxBase, offset, updateThreshold, cutOff, false, false, f -> true, priority);
    }
    public Map<DepositType, double[]> getDeposits(GuildDB db, Set<Long> tracked, boolean useTaxBase, boolean offset, long updateThreshold, long cutOff, boolean forceIncludeExpired, boolean forceIncludeIgnored, Predicate<Transaction2> filter, boolean priority) {
        long start = System.currentTimeMillis();
        List<Map.Entry<Integer, Transaction2>> transactions = getTransactions(db, tracked, useTaxBase, offset, updateThreshold, cutOff, priority);
        Map<DepositType, double[]> sum = PnwUtil.sumNationTransactions(db, tracked, transactions, forceIncludeExpired, forceIncludeIgnored, filter);
        return sum;
    }

    public List<Map.Entry<Integer, Transaction2>> getTransactions(GuildDB db, Set<Long> tracked, boolean useTaxBase, boolean offset, long updateThreshold, long cutOff, boolean priority) {
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
            TaxRate defTaxBaseTmp = db.getOrNull(GuildKey.TAX_BASE);
            if (defTaxBaseTmp != null) defTaxBase = new int[]{defTaxBaseTmp.money, defTaxBaseTmp.resources};
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

        List<Transaction2> records = getTransactions(updateThreshold, priority);
        transactions.addAll(records);

        List<Map.Entry<Integer, Transaction2>> result = new ArrayList<>();

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

    @Command(desc = "Nation ID")
    public int getNation_id() {
        return nation_id;
    }

    public void setNation_id(int nation_id) {
        this.nation_id = nation_id;
    }

    @Command(desc = "Nation Name")
    public String getNation() {
        return nation;
    }

    public void setNation(String nation) {
        this.nation = nation;
    }

    @Command(desc = "Leader name")
    public String getLeader() {
        return leader;
    }

    public void setLeader(String leader) {
        this.leader = leader;
    }

    @Command(desc = "Alliance ID")
    public int getAlliance_id() {
        return alliance_id;
    }

    public void setAlliance_id(int alliance_id) {
        this.alliance_id = alliance_id;
    }

    @Command(desc = "Alliance Name")
    public String getAllianceName() {
        if (alliance_id == 0) return "AA:0";
        return Locutus.imp().getNationDB().getAllianceName(alliance_id);
    }

    public DBAlliance getAlliance() {
        return getAlliance(true);
    }
    public DBAlliance getAlliance(boolean createIfNotExist) {
        if (alliance_id == 0) return null;
        if (createIfNotExist) {
            return Locutus.imp().getNationDB().getOrCreateAlliance(alliance_id);
        } else {
            return Locutus.imp().getNationDB().getAlliance(alliance_id);
        }
    }

    @Command(desc = "Minutes since last active in-game")
    public int getActive_m() {
        return active_m();
    }

    public void setActive_m(int active_m) {
        this.last_active = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(active_m);
    }

    @Command(desc = "Nation Score (ns)")
    public double getScore() {
        return score;
    }

    public double estimateScore(MMRDouble mmr, Double infra, Integer projects, Integer cities) {
        if (projects == null) projects = getNumProjects();
        if (infra == null) infra = getInfra();
        if (cities == null) cities = this.cities;

        double base = 10;
        base += projects * Projects.getScore();
        base += (cities - 1) * 100;
        base += infra / 40d;
        for (MilitaryUnit unit : MilitaryUnit.values) {
            int amt;
            if (mmr != null && unit.getBuilding() != null) {
                amt = (int) (mmr.getPercent(unit) * unit.getBuilding().max() * unit.getBuilding().cap(f -> false) * cities);
            } else {
                amt = getUnits(unit);
            }
            if (amt > 0) {
                base += unit.getScore(amt);
            }
        }
        return base;
    }

    public void setScore(double score) {
        this.score = score;
    }

    @Command(desc = "Total infra in all cities")
    public double getInfra() {
        double total = 0;
        for (DBCity city : _getCitiesV3().values()) {
            total += city.infra;
        }
        return total;
    }


    @Command(desc = "Total population in all cities")
    public int getPopulation() {
        int total = 0;
        for (JavaCity city : getCityMap(false).values()) {
            total += city.getPopulation(this::hasProject);
        }
        return total;
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
    @Command(desc = "Number of built cities")
    public int getCities() {
        return cities;
    }

    @Command(desc = "Number of cities built since date")
    public int getCitiesSince(@Timestamp long time) {
        return (int) _getCitiesV3().values().stream().filter(city -> city.created > time).count();
    }

    @Command(desc = "Number of cities at a date")
    public int getCitiesAt(@Timestamp long time) {
        return cities - getCitiesSince(time);
    }
    @Command(desc = "Cost of cities built since a date")
    public double getCityCostSince(@Timestamp long time, boolean allowProjects) {
        int numBuilt = getCitiesSince(time);
        int from = cities - numBuilt;
        return PnwUtil.cityCost(allowProjects ? this : null, from, cities);
    }

    @Command(desc = "Cost of cities built since a date divided by the current city count")
    public double getCityCostPerCitySince(@Timestamp long time, boolean allowProjects) {
        int numBuilt = getCitiesSince(time);
        int from = cities - numBuilt;
        return numBuilt > 0 ? PnwUtil.cityCost(allowProjects ? this : null, from, cities) / numBuilt : 0;
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
        if (cities > this.cities && this.cities != 0) {
            this.cityTimer = TimeUtil.getTurn() + GameTimers.CITY.getTurns();
        }
        this.cities = cities;
    }

    @Command(desc="average infrastructure in cities")
    public double getAvg_infra() {
        double total = 0;
        Collection<DBCity> cities = _getCitiesV3().values();
        if (cities.isEmpty()) return 0;

        for (DBCity city : cities) {
            total += city.infra;
        }
        return total / cities.size();
    }

    @Command(desc = "War policy")
    public WarPolicy getWarPolicy() {
        return war_policy;
    }

    @Command(desc = "Domestic policy")
    public DomesticPolicy getDomesticPolicy() {
        if (domestic_policy == null) return DomesticPolicy.MANIFEST_DESTINY;
        return domestic_policy;
    }

    public void setWarPolicy(WarPolicy policy) {
        if (policy != this.war_policy && this.war_policy != null) {
            warPolicyTimer = TimeUtil.getTurn() + GameTimers.WAR_POLICY.getTurns();
        }
        this.war_policy = policy;
    }

    public void setDomesticPolicy(DomesticPolicy policy) {
        if (policy != this.domestic_policy && this.domestic_policy != null) {
            domesticPolicyTimer = TimeUtil.getTurn() + GameTimers.DOMESTIC_POLICY.getTurns();
        }
        this.domestic_policy = policy;
    }

    @Command(desc = "Number of soldiers")
    public int getSoldiers() {
        return soldiers;
    }

    public void setSoldiers(int soldiers) {
        getCache().processUnitChange(this, MilitaryUnit.SOLDIER, this.soldiers, soldiers);
        this.soldiers = soldiers;
    }

    @Command(desc = "Number of tanks")
    public int getTanks() {
        return tanks;
    }

    public void setTanks(int tanks) {
        getCache().processUnitChange(this, MilitaryUnit.TANK, this.tanks, tanks);
        this.tanks = tanks;
    }

    @Command(desc = "Number of aircraft")
    public int getAircraft() {
        return aircraft;
    }

    @Command(desc = "Maximum spies a nation can have")
    public int getSpyCap() {
        return hasProject(Projects.INTELLIGENCE_AGENCY) ? 60 : 50;
    }

    @Command(desc = "Decimal ratio of aircraft a nation has out of their maximum (between 0 and 1)")
    public double getAircraftPct() {
        return getAircraft() / (double) (Math.max(1, Buildings.HANGAR.max() * Buildings.HANGAR.cap(this::hasProject) * getCities()));
    }

    @Command(desc = "Decimal ratio of tanks a nation has out of their maximum (between 0 and 1)")
    public double getTankPct() {
        return getTanks() / (double) (Math.max(1, Buildings.FACTORY.max() * Buildings.FACTORY.cap(this::hasProject) * getCities()));
    }

    @Command(desc = "Decimal ratio of soldiers a nation has out of their maximum (between 0 and 1)")
    public double getSoldierPct() {
        return getSoldiers() / (double) (Math.max(1, Buildings.BARRACKS.max() * Buildings.BARRACKS.cap(this::hasProject) * getCities()));
    }

    @Command(desc = "Decimal ratio of ships a nation has out of their maximum (between 0 and 1)")
    public double getShipPct() {
        return getShips() / (double) (Math.max(1, Buildings.DRYDOCK.max() * Buildings.DRYDOCK.cap(this::hasProject) * getCities()));
    }

    public void setAircraft(int aircraft) {
        getCache().processUnitChange(this, MilitaryUnit.AIRCRAFT, this.aircraft, aircraft);
        this.aircraft = aircraft;
    }

    @Command(desc = "Number of navy ships")
    public int getShips() {
        return ships;
    }

    public void setShips(int ships) {
        getCache().processUnitChange(this, MilitaryUnit.SHIP, this.ships, ships);
        this.ships = ships;
    }

    @Command(desc = "Number of missiles")
    public int getMissiles() {
        return missiles;
    }

    public void setMissiles(int missiles) {
        getCache().processUnitChange(this, MilitaryUnit.MISSILE, this.missiles, missiles);
        this.missiles = missiles;
    }

    @Command(desc = "Number of nuclear weapons (nukes)")
    public int getNukes() {
        return nukes;
    }

    public void setNukes(int nukes) {
        getCache().processUnitChange(this, MilitaryUnit.NUKE, this.nukes, nukes);
        this.nukes = nukes;
    }

    @Command(desc = "Number of turns since entering Vacation Mode (VM)")
    public int getVacationTurnsElapsed() {
        long turn = TimeUtil.getTurn();
        if (entered_vm > 0 && entered_vm < turn) {
            return (int) (turn - entered_vm);
        }
        return 0;
    }

    @Command(desc = "Number of turns in Vacation Mode (VM)")
    public int getVm_turns() {
        if (leaving_vm == 0) return 0;
        long currentTurn = TimeUtil.getTurn();
        if (leaving_vm <= currentTurn) return 0;
        return (int) (leaving_vm - currentTurn);
    }

    @Command(desc = "Absolute turn number when entering Vacation Mode (VM)")
    public long getEntered_vm() {
        return entered_vm;
    }

    @Command(desc = "Absolute turn number when leaving Vacation Mode (VM)")
    public long getLeaving_vm() {
        return leaving_vm;
    }

    public void setLeaving_vm(long leaving_vm) {
        long currentTurn = TimeUtil.getTurn();
        if (this.leaving_vm <= currentTurn) {
            this.entered_vm = currentTurn;
        }
        this.leaving_vm = leaving_vm;
    }

    @Command(desc = "If nation color is beige")
    public boolean isBeige() {
        return getColor() == NationColor.BEIGE;
    }

    @Command(desc = "If nation color is gray")
    public boolean isGray() {
        return getColor() == NationColor.GRAY;
    }

    @Command(desc = "Nation color bloc")
    public NationColor getColor() {
        return color;
    }

    @Command(desc = "If nation color matches the alliance color")
    public boolean isAllianceColor() {
        DBAlliance alliance = getAlliance();
        if (alliance == null) return false;
        return alliance.getColor() == getColor();
    }

    public void setColor(NationColor color) {
//        if (color != NationColor.GRAY && color != NationColor.BEIGE)
        if (color != this.color) {
            this.colorTimer = TimeUtil.getTurn() + GameTimers.COLOR.getTurns();
            if (color != NationColor.BEIGE) {
                beigeTimer = 0L;
            }
        }
        this.color = color;
    }

    @Command(desc = "Number of active offensive wars")
    public int getOff() {
        return (int) getActiveWars().stream().filter(f -> f.attacker_id == nation_id).count();
    }

    @Command(desc = "All time offensive wars involved in")
    @RolePermission(Roles.MEMBER)
    public int getAllTimeOffensiveWars() {
        return (int) getWars().stream().filter(f -> f.attacker_id == nation_id).count();
    }

    @Command(desc = "All time defensive wars involved in")
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

    @Command(desc = "All time wars involved in")
    @RolePermission(Roles.MEMBER)
    public int getAllTimeWars() {
        return getWars().size();
    }

    @Command(desc = "All time wars against active nations")
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
        return getActiveWars().size();
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

    @Command(desc = "Number of active defensive wars")
    public int getDef() {
        return (int) getActiveWars().stream().filter(f -> f.defender_id == nation_id).count();
    }

    @Command(desc = "Unix timestamp of date created")
    public long getDate() {
        return date;
    }

    public void setDate(long date) {
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

    public void setUnits(MilitaryUnit unit, int amt) {
        switch (unit) {
            case SOLDIER:
                setSoldiers(amt);
                break;
            case TANK:
                setTanks(amt);
                break;
            case AIRCRAFT:
                setAircraft(amt);
                break;
            case SHIP:
                setShips(amt);
                break;
            case MONEY:
            case INFRASTRUCTURE:
                // TODO
                return;
            case MISSILE:
                setMissiles(amt);
                break;
            case NUKE:
                setNukes(amt);
                break;
            case SPIES:
                setSpies(amt, false);
                break;
            default:
                throw new UnsupportedOperationException("Unit type not implemented");
        }
    }

    @Command(desc = "Get number of a specific military unit")
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
            case INFRASTRUCTURE:
                return (int) maxCityInfra();
            case MONEY:
                // TODO
                return 0;
            case MISSILE:
                return getMissiles();
            case NUKE:
                return getNukes();
            case SPIES:
                return getSpies();
        }
        return 0;
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

    @Command(desc = "Set of nation ids fighting this nation")
    public Set<Integer> getEnemies() {
        return getActiveWars().stream()
                .map(dbWar -> dbWar.attacker_id == getNation_id() ? dbWar.defender_id : dbWar.attacker_id)
                .collect(Collectors.toSet());
    }

    @Command(desc = "If a specified nation is within this nations espionage range")
    public boolean isInSpyRange(DBNation other) {
        return SpyCount.isInScoreRange(getScore(), other.getScore());
    }

    @Command(desc = "If they have undefined military values")
    public boolean hasUnsetMil() {
        return soldiers == -1;
    }

    public int active_m() {
        return (int) TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - last_active);
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

    public LootEntry getBeigeLoot() {
        return Locutus.imp().getNationDB().getLoot(getNation_id());
    }

    public String toMarkdown() {
        return toMarkdown(false);
    }

    public String toMarkdown(boolean war) {
        return toMarkdown(war, true, true, true, true);
    }
    public String toMarkdown(boolean war, boolean showOff, boolean showSpies, boolean showInfra, boolean spies) {
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
        int vm = getVm_turns();

        response.append(" | " + String.format("%16s", getNation()))
                .append(" | " + String.format("%16s", getAllianceName()))
                .append(alliance_id != 0 && getPositionEnum() == Rank.APPLICANT ? " applicant" : "")
                .append(color == NationColor.BEIGE ? " beige:" + beigeStr : "")
                .append(vm > 0 ? " vm=" + TimeUtil.secToTime(TimeUnit.HOURS, vm * 2) : "")
                .append("\n```")
                .append(String.format("%5s", (int) getScore())).append(" ns").append(" | ")
                .append(String.format("%10s", TimeUtil.secToTime(TimeUnit.MINUTES, getActive_m()))).append(" \uD83D\uDD52").append(" | ")
                .append(String.format("%2s", getCities())).append(" \uD83C\uDFD9").append(" | ");
                if (showInfra) response.append(String.format("%5s", (int) getAvg_infra())).append(" \uD83C\uDFD7").append(" | ");
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
        return toMarkdown(true, false, title, general, military, true);
    }

    @Command(desc = "Days since creation")
    public int getAgeDays() {
        if (getDate() == 0) return 0;
        return (int) TimeUnit.MILLISECONDS.toDays((getSnapshot() != null ? getSnapshot() : System.currentTimeMillis()) - date);
//        return (int) (TimeUnit.SECONDS.toDays(ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond()) - getDate() / 65536);
    }

    // Mock
    public Map<Integer, DBCity> _getCitiesV3() {
        return Locutus.imp().getNationDB().getCitiesV3(nation_id);
    }

    public Map<Integer, JavaCity> getCityMap(boolean force) {
        return getCityMap(false, false, force);
    }

    public Map<Integer, JavaCity> getCityMap(boolean updateIfOutdated, boolean force) {
        return getCityMap(updateIfOutdated, true, force);
    }

    public Map<Integer, JavaCity> getCityMap(boolean updateIfOutdated, boolean updateNewCities, boolean force) {
        Map<Integer, DBCity> cityObj = _getCitiesV3();
        if (cityObj == null) cityObj = Collections.emptyMap();

        if (nation_id > 0) {
            if (updateNewCities && cityObj.size() != cities) force = true;
            if (updateIfOutdated && estimateScore() != this.score) force = true;
            if (force) {
                System.out.println("Fetch cities for " + getNation() + " | " + getNation_id());
                Locutus.imp().getNationDB().updateCitiesOfNations(Collections.singleton(nation_id), true,true, Event::post);
                cityObj = _getCitiesV3();
            }
        }
        Map<Integer, JavaCity> converted = new LinkedHashMap<>();
        for (Map.Entry<Integer, DBCity> entry : cityObj.entrySet()) {
            converted.put(entry.getKey(), entry.getValue().toJavaCity(this));
        }
        return converted;
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

    @Command(desc = "Decimal pct of times they login during UTC daychange")
    public double login_daychange() {
        Activity activity = getActivity(12 * 14);
        double[] arr = activity.getByDayTurn();
        return (arr[0] + arr[arr.length - 1]) / 2d;
    }

    @Command(desc = "The alliance tax rate necessary to break even when distributing raw resources")
    @RolePermission(value = Roles.ECON)
    public double equilibriumTaxRate() {
        return equilibriumTaxRate(false, false);
    }

    public double equilibriumTaxRate(boolean updateNewCities, boolean force) {
        double[] revenue = getRevenue(12, true, false, true, updateNewCities, false, false, force);
        double consumeCost = 0;
        double taxable = 0;
        for (ResourceType type : ResourceType.values) {
            double value = revenue[type.ordinal()];
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

    public double[] getRevenue() {
        return getRevenue(12);
    }
    public double[] getRevenue(int turns) {
        return getRevenue(turns, true, true, true, true, false, false, false);
    }

    public double[] getRevenue(int turns, boolean cities, boolean militaryUpkeep, boolean tradeBonus, boolean bonus, boolean noFood, boolean noPower, boolean force) {
        Map<Integer, JavaCity> cityMap = cities ? getCityMap(force, false) : new HashMap<>();
        double[] revenue = PnwUtil.getRevenue(null, turns, this, cityMap.values(), militaryUpkeep, tradeBonus, bonus, noFood, noPower);
        return revenue;
    }

    public String fetchUsername() throws IOException {
        List<Nation> discord = Locutus.imp().getV3().fetchNations(true, f -> f.setId(List.of(nation_id)), r -> r.discord());
        if (discord.isEmpty()) return null;
        return discord.get(0).getDiscord();
    }

    @Command(desc = "Total stockpile value based on last war loss or espionage")
    @WhitelistPermission
    public double getBeigeLootTotal() {
        LootEntry loot = getBeigeLoot();
        return loot == null ? 0 : PnwUtil.convertedTotal(loot.getTotal_rss());
    }

    @Command(desc = "Estimated loot value including aliance bank loot when defeated in a raid war based on last war loss or espionage")
    public double lootTotal() {
        double[] knownResources = new double[ResourceType.values.length];
        double[] buffer = new double[knownResources.length];
        LootEntry loot = getBeigeLoot();
        double convertedTotal = loot == null ? 0 : loot.convertedTotal() * 0.14 * lootModifier();

        if (getPosition() > 1 && alliance_id != 0) {
            Map<ResourceType, Double> aaLoot = Locutus.imp().getWarDb().getAllianceBankEstimate(getAlliance_id(), getScore());
            convertedTotal += PnwUtil.convertedTotal(aaLoot);
        }
        return convertedTotal;
    }

    public int getTurnsInactive(LootEntry loot) {
        long turnInactive = TimeUtil.getTurn(lastActiveMs());
        if (loot != null) {
            long lootTurn = TimeUtil.getTurn(loot.getDate());
            if (lootTurn > turnInactive) turnInactive = lootTurn;
        }
        long turnEntered = getEntered_vm();
        long turnEnded = getLeaving_vm();

        long turn = TimeUtil.getTurn();
        if (getVm_turns() > 0) {
            if (turnEntered > turnInactive) {
                turnInactive = turn - (turnEntered - turnInactive);
            } else {
                turnInactive = turn;
            }
        } else if (turnEnded > turnInactive) {
            turnInactive = turnEnded;
        }
        return Math.min(12 * 90, (int) (turn - turnInactive));
    }

    private int getTurnsPowered(double[] rss) {
        // 1 get power plant resource usage
        double[] profitBuffer = ResourceType.getBuffer();
        for (JavaCity city : getCityMap(false).values()) {
            for (Building building : Buildings.values()) {
                if (!(building instanceof PowerBuilding power)) {
                    city.set(building, 0);
                }
            }
            city.profit(continent, 0, 0, this::hasProject, profitBuffer, 1, 1, 1);
        }
        int turns = Integer.MAX_VALUE;
        for (ResourceType type : ResourceType.values) {
            if (type.isRaw() && type != ResourceType.FOOD) {
                int newTurns = (int) Math.floor(rss[type.ordinal()] / profitBuffer[type.ordinal()]);
                turns = Math.min(turns, newTurns);
            }
        }
        return turns;
    }

    @Command
    public double[] getLootRevenueTotal() {
        LootEntry loot = getBeigeLoot();
        int turnsInactive = getTurnsInactive(loot);
        double lootFactor = 0.14 * lootModifier();

        double[] lootRevenue = loot == null ? ResourceType.getBuffer() : PnwUtil.multiply(loot.getTotal_rss().clone(), lootFactor);
        if (getPositionEnum().id > Rank.APPLICANT.id) {
            DBAlliance alliance = getAlliance(false);
            if (alliance != null) {
                LootEntry aaLoot = alliance.getLoot();
                double[] lootScaled =  aaLoot.getAllianceLootValue(getScore());
                lootRevenue = PnwUtil.add(lootRevenue, lootScaled);
            }
        }

        if (turnsInactive > 0) {
            int turnsOfRevenue = turnsInactive + 24;
            // food
            // power
            int turnsFed = 60;
            int turnsPowered = isPowered() ? Integer.MAX_VALUE : 60;
            if (loot != null) {
                turnsPowered = getTurnsPowered(loot.getTotal_rss());
                double food = loot.getTotal_rss()[ResourceType.FOOD.ordinal()];
                if (food <= 0) {
                    turnsFed = 0;
                } else {
                    double[] revenue = getRevenue(1, true, true, false, true, false, false, false);
                    if (revenue[ResourceType.FOOD.ordinal()] < 0) {
                        turnsFed = Math.max(0, (int) (food / Math.abs(revenue[ResourceType.FOOD.ordinal()])));
                    } else {
                        turnsFed = Integer.MAX_VALUE;
                    }
                }
            }

            double[] revenue = ResourceType.getBuffer();
            int turnsUnpowered = turnsOfRevenue - turnsPowered;
            if (turnsUnpowered > 0) {
                int turnsFedUnpowered = Math.max(0, Math.min(turnsFed - turnsPowered, turnsUnpowered));
                int turnsUnfedUnpowered = turnsPowered - turnsFedUnpowered;
                if (turnsFedUnpowered > 0) {
                    revenue = getRevenue(turnsFedUnpowered, true, true, false, true, false, true, false);
                }
                if (turnsUnfedUnpowered > 0) {
                    revenue = getRevenue(turnsUnfedUnpowered, true, true, false, true, true, true, false);
                }
                revenue = PnwUtil.capManuFromRaws(revenue, ResourceType.getBuffer());
            }
            if (turnsPowered > 0) {
                int turnsFedPowered = Math.min(turnsFed, turnsPowered);
                int turnsUnfedPowered = turnsPowered - turnsFedPowered;
                if (turnsFedPowered > 0) {
                    revenue = PnwUtil.add(revenue, getRevenue(turnsFedPowered, true, true, false, true, false, false, false));
                }
                if (turnsUnfedPowered > 0) {
                    revenue = PnwUtil.add(revenue, getRevenue(turnsUnfedPowered, true, true, false, true, true, false, false));
                }
            }
            if (loot != null) {
                revenue = PnwUtil.capManuFromRaws(revenue, loot.getTotal_rss());
            }
            for (int i = 0; i < lootRevenue.length; i++) {
                lootRevenue[i] += revenue[i] * lootFactor;
            }
        }
        return lootRevenue;
    }

    public double estimateRssLootValue(double[] knownResources, LootEntry lootHistory, double[] buffer, boolean fetchStats) {
        if (lootHistory != null) {
            double[] loot = lootHistory.getTotal_rss();
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
//            List<DBTrade> trades = Locutus.imp().getTradeManager().getTradeDb().getOffers(nation_id, cutoffMs);
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
//            for (DBTrade offer : trades) {
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
//            List<AbstractCursor> attacks = Locutus.imp().getWarDb().getAttacks(nation_id, cutoffMs);
//            for (AbstractCursor attack : attacks) {
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

    @Command(desc = "If this nation has a previous ban attached to their nation or discord id")
    public boolean hasPriorBan() {
        List<DBBan> bans = getBans();
        return bans != null && !bans.isEmpty();
    }

    public List<DBBan> getBans() {
        PNWUser user = getDBUser();
        List<DBBan> bans;
        if (user != null) {
            bans = Locutus.imp().getNationDB().getBansForUser(user.getDiscordId(), nation_id);
        } else {
            bans = Locutus.imp().getNationDB().getBansForNation(nation_id);
        }
        return bans;
    }

    @Command(desc = "If this nation has a ban attached to their nation or discord id that has not expired")
    public boolean isBanEvading() {
        List<DBBan> bans = getBans();
        for (DBBan ban : bans) {
            if (ban.getTimeRemaining() > 0) {
                return true;
            }
        }
        return false;
    }

    public PNWUser getDBUser() {
        return Locutus.imp().getDiscordDB().getUserFromNationId(nation_id);
    }

    @Command(desc = "If registered with Locutus")
    public boolean isVerified() {
        return getDBUser() != null;
    }

    @Command(desc = "If in the discord guild for their alliance")
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

    @Command(desc = "If in the milcom discord guild for their alliance")
    public boolean isInMilcomGuild() {
        GuildDB db = Locutus.imp().getGuildDBByAA(alliance_id);
        if (db != null) {
            Guild warGuild = db.getOrNull(GuildKey.WAR_SERVER);
            if (warGuild == null) warGuild = db.getGuild();
            User user = getUser();
            if (user != null) {
                return warGuild.getMember(user) != null;
            }
        }
        return false;
    }

    @Command(desc = "The registered discord username and user discriminator")
    public String getUserDiscriminator() {
        User user = getUser();
        if (user == null) return null;
        return DiscordUtil.getFullUsername(user);
    }

    @Command(desc = "The registered discord user id")
    public Long getUserId() {
        PNWUser dbUser = getDBUser();
        if (dbUser == null) return null;
        return dbUser.getDiscordId();
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

    @Command(desc = "If blockaded by navy ships in-game")
    public boolean isBlockaded() {
        return !getBlockadedBy().isEmpty();
    }

    @Command(desc = "If blockading a nation in-game with navy ships")
    public boolean isBlockader() {
        return !getBlockading().isEmpty();
    }

    @Command(desc = "List of nation ids whuch are blockaded by this nation's navy ships in-game")
    public Set<Integer> getBlockading() {
        return Locutus.imp().getWarDb().getNationsBlockadedBy(nation_id);
    }

    @Command(desc = "List of nation ids which are blockading this nation with their navy ships in-game")
    public Set<Integer> getBlockadedBy() {
        return Locutus.imp().getWarDb().getNationsBlockading(nation_id);
    }

    public void toCard(IMessageIO channel, boolean refresh) {
        String title = nation;
        String counterEmoji = "Counter";
        String counterCmd = Settings.commandPrefix(true) + "counter " + getNationUrl();
//        String simEmoji = "Simulate";
//        String simCommand = Settings.commandPrefix(true) + "simulate " + getNationUrl();
        String refreshEmoji = "Refresh";
        String refreshCmd = Settings.commandPrefix(true) + "who " + getNationUrl();

        String response = toEmbedString();
        response += "To report in-game fraud: " + CM.report.add.cmd.toSlashMention();
        IMessageBuilder msg = channel.create().embed(title, response)
                .commandButton(CommandBehavior.UNPRESS, CM.war.counter.nation.cmd.create(getId() + "", null, null, null, null, null, null, null), "Counter");
        if (refresh) {
            msg = msg.commandButton(CM.who.cmd.create(getId() + "", null, null, null, null, null, null, null, null), "Refresh");
        }
        msg.send();
    }
    public String toEmbedString() {
        StringBuilder response = new StringBuilder();
        PNWUser user = Locutus.imp().getDiscordDB().getUserFromNationId(getNation_id());
        if (user != null) {
            response.append(user.getDiscordName() + " / <@" + user.getDiscordId() + "> | ");
        }
        response.append(toMarkdown(true, false, true, true, false, false));
        response.append(toMarkdown(true, false, false, false, true, true));



        response.append(" ```")
                .append(String.format("%6s", getVm_turns())).append(" \uD83C\uDFD6\ufe0f").append(" | ");

        if (color == NationColor.BEIGE) {
            int turns = getBeigeTurns();
            long diff = TimeUnit.MILLISECONDS.toMinutes(TimeUtil.getTimeFromTurn(TimeUtil.getTurn() + turns) - System.currentTimeMillis());
            String beigeStr = TimeUtil.secToTime(TimeUnit.MINUTES, diff);
            response.append(" beige:" + beigeStr);
        } else {
            response.append(String.format("%6s", getColor()));
        }
        response.append(" | ")
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

    @Command(desc = "Renamed to `cityurl`")
    @Deprecated
    public String city(int index) {
        return cityUrl(index);
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

    @Command(desc = "Game url for nation")
    public String getNationUrl() {
        return "" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + getNation_id();
    }

    @Command(desc = "Game url for alliance")
    public String getAllianceUrl() {
        return "" + Settings.INSTANCE.PNW_URL() + "/alliance/id=" + getAlliance_id();
    }

    public String getMarkdownUrl() {
        return getNationUrlMarkup(true);
    }

    public String getNationUrlMarkup(boolean embed) {
        String nationUrl = getNationUrl();
        nationUrl = MarkupUtil.markdownUrl(nation, "<" + nationUrl + ">");
        return nationUrl;
    }

    public String getAllianceUrlMarkup(boolean embed) {
        String allianceUrl = getAllianceUrl();
        allianceUrl = MarkupUtil.markdownUrl(getAllianceName(), "<" + allianceUrl + ">");
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

    @Command
    public int getNumReports() {
        ReportManager reportManager = Locutus.imp().getNationDB().getReportManager();
        return reportManager.loadReports(getId(), getUserId(), null, null).size();
    }

    public int getRemainingUnitBuy(MilitaryUnit unit, long timeSince) {
        if (unit == MilitaryUnit.INFRASTRUCTURE || unit == MilitaryUnit.MONEY) return -1;

        int previousAmt = getUnits(unit, timeSince);
        int currentAmt = getUnits(unit);
        int lostInAttacks = 0;

        if (unit != MilitaryUnit.SPIES) {
            List<AbstractCursor> attacks = Locutus.imp().getWarDb().getAttacks(getNation_id(), timeSince);

            outer:
            for (AbstractCursor attack : attacks) {
                MilitaryUnit[] units = attack.getAttack_type().getUnits();
                for (MilitaryUnit other : units) {
                    if (other == unit) {
                        Map<MilitaryUnit, Integer> losses = attack.getUnitLosses(attack.getAttacker_id() == nation_id);
                        lostInAttacks += losses.get(unit);
                        continue outer;
                    }
                }
            }
        }

        int numPurchased = currentAmt - previousAmt + lostInAttacks;
        int maxPerDay = unit.getMaxPerDay(cities, this::hasProject);
        return Math.max(0, maxPerDay - numPurchased);
    }

    public PoliticsAndWarV3 getApi(boolean throwError) {
        ApiKeyPool.ApiKey apiKey = this.getApiKey(true);
        if (apiKey == null) {
            if (throwError) throw new IllegalStateException("No api key found for `" + nation + "` Please set one: " + CM.credentials.addApiKey.cmd.toSlashMention());
            return null;
        }
        return new PoliticsAndWarV3(ApiKeyPool.create(apiKey));
    }

    private Map.Entry<Boolean, String> createAndOffshoreDeposit(GuildDB currentDB, DBNation senderNation, Supplier<Set<Auth.TradeResult>> tradeSupplier) {
        PoliticsAndWarV3 receiverApi = getApi(true);

        synchronized (OffshoreInstance.BANK_LOCK) {
            if (!TimeUtil.checkTurnChange()) {
                throw new IllegalArgumentException("Turn change");
            }
            if (getPosition() <= Rank.APPLICANT.id) {
                throw new IllegalArgumentException("Receiver is not member");
            }
            if (!this.hasPermission(AlliancePermission.WITHDRAW_BANK)) {
                return Map.entry(false, "The nation specifies has no `" + AlliancePermission.WITHDRAW_BANK + "` permission");
            }
            if (!this.hasPermission(AlliancePermission.VIEW_BANK)) {
                return Map.entry(false, "The nation specifies has no `" + AlliancePermission.VIEW_BANK + "` permission");
            }

            if (senderNation == null) throw new IllegalArgumentException("Sender is null");
            if (senderNation.isBlockaded()) throw new IllegalArgumentException("Sender is blockaded");
            if (isBlockaded()) throw new IllegalArgumentException("Receiver is blockaded");

            OffshoreInstance offshore = currentDB.getOffshore();

            GuildDB authDb = Locutus.imp().getGuildDBByAA(getAlliance_id());
            if (authDb == null) throw new IllegalArgumentException("Receiver is not in a server with locutus: " + getAlliance_id());
            OffshoreInstance receiverOffshore = authDb.getOffshore();
            if (receiverOffshore == null) {
                throw new IllegalArgumentException("Receiver does not have a registered offshore");
            }
            if (receiverOffshore != offshore) {
                throw new IllegalArgumentException("Receiver offshore does not match this guilds offshore");
            }
            Set<Integer> aaIds = currentDB.getAllianceIds();
            long senderId;
            int senderType;
            if (aaIds.isEmpty()) {
                senderId = currentDB.getIdLong();
                senderType = currentDB.getReceiverType();
            } else if (aaIds.size() == 1) {
                senderId = aaIds.iterator().next();
                senderType = getAlliance().getReceiverType();
            } else if (aaIds.contains(senderNation.getAlliance_id())){
                senderId = senderNation.getAlliance_id();
                senderType = senderNation.getAlliance().getReceiverType();
            } else {
                throw new IllegalArgumentException("Sender " + senderNation.getQualifiedId() + " is not in alliances: " + StringMan.getString(aaIds));
            }

            StringBuilder response = new StringBuilder("Checking trades...");

            Set<Auth.TradeResult> trades = tradeSupplier.get();

            double[] toDeposit = ResourceType.getBuffer();
            for (Auth.TradeResult trade : trades) {
                response.append("\n" + trade.toString());
                if (trade.getResult() == Auth.TradeResultType.SUCCESS) {
                    int sign = trade.getBuyer().getNation_id() == getNation_id() ? 1 : -1;
                    toDeposit[trade.getResource().ordinal()] += trade.getAmount() * sign;
                    toDeposit[ResourceType.MONEY.ordinal()] += ((long) trade.getPpu()) * trade.getAmount() * sign * -1;
                }
            }

            if (ResourceType.isZero(toDeposit)) {
                response.append("\n- No trades to deposit " + PnwUtil.resourcesToString(toDeposit));
                return Map.entry(false, response.toString());
            }
            int receiverId;
            try {
                Bankrec deposit = receiverApi.depositIntoBank(toDeposit, "#ignore");
                double[] amt = ResourceType.fromApiV3(deposit, ResourceType.getBuffer());
                response.append("\nDeposited: `" + PnwUtil.resourcesToString(amt) + "`");
                if (!ResourceType.equals(toDeposit, amt)) {
                    response.append("\n- Error Depositing: " + PnwUtil.resourcesToString(toDeposit) + " != " + PnwUtil.resourcesToString(amt));
                    return Map.entry(false, response.toString());
                }
                receiverId = deposit.getReceiver_id();
            } catch (Throwable e) {
                e.printStackTrace();
                response.append("\n- Error Depositing: " + e.getMessage());
                return Map.entry(false, response.toString());
            }

            DBAlliance receiverAA = DBAlliance.getOrCreate(receiverId);
            OffshoreInstance bank = receiverAA.getBank();
            if (bank != offshore) {
                for (int i = 0; i < toDeposit.length; i++) {
                    if (toDeposit[i] < 0) toDeposit[i] = 0;
                }
                TransferResult transferResult = bank.transfer(offshore.getAlliance(), PnwUtil.resourcesToMap(toDeposit), "#ignore");
                response.append("Offshore " + transferResult.toLineString());
                if (transferResult.getStatus() != OffshoreInstance.TransferStatus.SUCCESS) {
                    response.append("\n- Depositing failed");
                    return Map.entry(false, response.toString());
                }
            }

            // add balance to guilddb
            long tx_datetime = System.currentTimeMillis();
            String note = "#deposit";

            response.append("\nAdding deposits:");

            offshore.getGuildDB().addTransfer(tx_datetime, senderId, senderType, offshore.getAlliance(), getNation_id(), note, toDeposit);
            response.append("\n- Added " + PnwUtil.resourcesToString(toDeposit) + " to " + currentDB.getGuild());
            // add balance to expectedNation
            currentDB.addTransfer(tx_datetime, senderNation, senderId, senderType, getNation_id(), note, toDeposit);
            response.append("\n- Added " + PnwUtil.resourcesToString(toDeposit) + " to " + senderNation.getNationUrl());

            MessageChannel logChannel = offshore.getGuildDB().getResourceChannel(0);
            if (logChannel != null) {
                RateLimitUtil.queue(logChannel.sendMessage(response));
            }

            return new AbstractMap.SimpleEntry<>(true, response.toString());
        }
    }

    public Map.Entry<Boolean, String> tradeAndOffshoreDeposit(GuildDB currentDB, DBNation senderNation, double[] amounts) {
        Map<ResourceType, Integer> amountMap = new LinkedHashMap<>();
        for (ResourceType type : ResourceType.values) {
            double amt = amounts[type.ordinal()];
            amt = amt < 0 ? Math.ceil(amt) : Math.floor(amt);
            if (amt == 0) continue;
            if (amt < 0) {
                throw new IllegalArgumentException("Negative amount for " + type);
            }
            if (type == ResourceType.CREDITS) {
                throw new IllegalArgumentException("Cannot deposit credits (amount: " + amt + ")");
            }
            amountMap.put(type, (int) amt);
        }
        if (amountMap.isEmpty()) {
            throw new IllegalArgumentException("No resources to deposit");
        }
        PoliticsAndWarV3 senderApi = senderNation.getApi(true);
        PoliticsAndWarV3 receiverApi = getApi(true);
        Auth auth = getAuth();
        if (auth == null) {
            throw new IllegalArgumentException("Banker nation has no auth (" + getNationUrlMarkup(true) + "). See: " + CM.credentials.login.cmd.toSlashMention());
        }

        Supplier<Set<Auth.TradeResult>> tradeSupplier = new Supplier<>() {
            @Override
            public Set<Auth.TradeResult> get() {
                for (Map.Entry<ResourceType, Integer> entry : amountMap.entrySet()) {
                    String trade = auth.createDepositTrade(senderNation, entry.getKey(), entry.getValue());
                }
                return senderNation.acceptTrades(getNation_id(), false);

            }
        };
        return createAndOffshoreDeposit(currentDB, senderNation, tradeSupplier);
    }

    public Map.Entry<Boolean, String> acceptAndOffshoreTrades(GuildDB currentDB, DBNation senderNation) {
        int expectedNationId = senderNation.getNation_id();
        PoliticsAndWarV3 api = getApi(true);

        Supplier<Set<Auth.TradeResult>> tradeSupplier = new Supplier<>() {
            @Override
            public Set<Auth.TradeResult> get() {
                return acceptTrades(senderNation.getNation_id(), false);
            }
        };
        return createAndOffshoreDeposit(currentDB, senderNation, tradeSupplier);
    }

    public Set<Auth.TradeResult> acceptTrades(int expectedNationId, boolean reverse) {
        if (expectedNationId == nation_id) throw new IllegalArgumentException("Buyer and seller cannot be the same");
        if (!TimeUtil.checkTurnChange()) return Collections.singleton(new Auth.TradeResult("cannot accept during turn change", Auth.TradeResultType.BLOCKADED));
        if (isBlockaded()) return Collections.singleton(new Auth.TradeResult("receiver is blockaded", Auth.TradeResultType.BLOCKADED));

        PoliticsAndWarV3 api = this.getApi(true);
        Set<Auth.TradeResult> responses = new LinkedHashSet<>();

        Map<Trade, Map.Entry<String, Auth.TradeResultType>> errors = new LinkedHashMap<>();

        String foodBuyOrSell = reverse ? "sell" : "buy";
        String rssBuyOrSell = reverse ? "buy" : "sell";

        List<Trade> tradesV3 = new ArrayList<>(api.fetchPrivateTrades(nation_id));
        if (tradesV3.isEmpty()) {
            return Collections.singleton(new Auth.TradeResult("no trades to accept", Auth.TradeResultType.NO_TRADES));
        }
        tradesV3.removeIf(f -> f.getSender_id() == null || f.getSender_id() != expectedNationId);
        tradesV3.removeIf((Predicate<Trade>) f -> {
            ResourceType resource = ResourceType.parse(f.getOffer_resource());
            if (f.getBuy_or_sell().equalsIgnoreCase(foodBuyOrSell)) {
                if (resource != ResourceType.FOOD) {
                    errors.put(f, Map.entry(foodBuyOrSell + " offers can only be food trades", Auth.TradeResultType.NOT_A_FOOD_TRADE));
                    return true;
                }
                if (f.getPrice() < 100000) {
                    errors.put(f, Map.entry(foodBuyOrSell + " offers must be at least $100,000 to deposit", Auth.TradeResultType.INCORRECT_PPU));
                    return true;
                }
                if (f.getSender_id() == null) {
                    errors.put(f, Map.entry("Sender id is null", Auth.TradeResultType.NOT_A_BUY_OFFER));
                    return true;
                }
                if (f.getSender_id() != expectedNationId) {
                    errors.put(f, Map.entry("Sender id is not expected nation id (" + f.getSender_id() + " != " + expectedNationId + ")", Auth.TradeResultType.NOT_A_BUY_OFFER));
                    return true;
                }
                return false;
            } else if (f.getBuy_or_sell().equalsIgnoreCase(rssBuyOrSell)) {
                if (resource == ResourceType.CREDITS) {
                    errors.put(f, Map.entry("Cannot " + rssBuyOrSell + " credits", Auth.TradeResultType.CANNOT_DEPOSIT_CREDITS));
                    return true;
                }
                if (f.getPrice() != 0) {
                    errors.put(f, Map.entry(rssBuyOrSell + " offers must be $0 to deposit", Auth.TradeResultType.INCORRECT_PPU));
                    return true;
                }
                if (f.getSender_id() == null) {
                    errors.put(f, Map.entry("Sender id is null", Auth.TradeResultType.NOT_A_SELL_OFFER));
                    return true;
                }
                if (f.getSender_id() != expectedNationId) {
                    errors.put(f, Map.entry("Sender id is not expected nation id (" + f.getSender_id() + " != " + expectedNationId + ")", Auth.TradeResultType.NOT_A_SELL_OFFER));
                    return true;
                }
                return false;
            } else {
                errors.put(f, Map.entry("Unknown buy or sell type: " + f.getBuy_or_sell(), Auth.TradeResultType.UNKNOWN_ERROR));
                return true;
            }
        });

        Function<Trade, String> tradeToString = trade ->
                trade.getBuy_or_sell() + " `" +
                        trade.getOffer_resource() + "=" +
                        trade.getOffer_amount() + "` @ `$" +
                        trade.getPrice() + "`";
        Callable<String> getErrors = () -> {
            String errorMsg = errors.entrySet().stream().map(e -> tradeToString.apply(e.getKey()) + ": " + e.getValue()).collect(Collectors.joining("\n- "));
            if (!errorMsg.isEmpty()) errorMsg = "\n- " + errorMsg;
            return errorMsg;
        };

        if (!tradesV3.isEmpty()) {
            for (Trade trade : tradesV3) {
                try {
                    Trade completed = api.acceptPersonalTrade(trade.getId(), trade.getOffer_amount());
                    DBNation seller = DBNation.getById(completed.getSender_id());
                    DBNation buyer = DBNation.getById(completed.getReceiver_id());
                    if (completed.getBuy_or_sell().equalsIgnoreCase("buy")) {
                        DBNation tmp = buyer;
                        buyer = seller;
                        seller = tmp;
                    }
                    Auth.TradeResult response = new Auth.TradeResult(seller, buyer);
                    response.setAmount(completed.getOffer_amount());
                    response.setResource(ResourceType.parse(completed.getOffer_resource()));
                    response.setPPU(completed.getPrice());
                    responses.add(response);
                    response.setResult(Auth.TradeResultType.SUCCESS);
                    response.setMessage("Accepted " + tradeToString.apply(trade));

                } catch (Throwable e) {
                    errors.put(trade, Map.entry(e.getMessage(), Auth.TradeResultType.UNKNOWN_ERROR));
                }
            }
        }

        for (Map.Entry<Trade, Map.Entry<String, Auth.TradeResultType>> entry : errors.entrySet()) {
            Trade trade = entry.getKey();
            Map.Entry<String, Auth.TradeResultType> value = entry.getValue();
            Auth.TradeResultType type = value.getValue();
            String error = value.getKey();
            String msg = tradeToString.apply(trade) + ": " + error;
            Auth.TradeResult response = new Auth.TradeResult(msg, type);
            responses.add(response);
        }

        return responses;
    }

    public int getUnitCap(MilitaryUnit unit, boolean checkBuildingsAndPop) {
        int result = checkBuildingsAndPop ? unit.getCap(this, false) : unit.getMaxMMRCap(cities, this::hasProject);
        return result;
    }

    public String toFullMarkdown() {
        StringBuilder body = new StringBuilder();
        //Nation | Leader name | timestamp(DATE_CREATED) `tax_id=1`
        body.append(getNationUrlMarkup(true)).append(" | ");
        body.append(leader).append(" | ");
        // DiscordUtil.timestamp
        if (tax_id != 0) {
            body.append(" `#tax_id=").append(tax_id).append("` | ");
        }

        body.append(DiscordUtil.timestamp(date, null)).append("\n");
        //Alliance | PositionOrEnum(id=0,enum=0) | timestamp(Seniority)
        if (alliance_id == 0) {
            body.append("`AA:0`");
        } else {
            body.append(getAllianceUrlMarkup(true));
            DBAlliancePosition position = this.getAlliancePosition();
            String posStr;
            if (position != null) {
                posStr = position.getName() + " (id=" + position.getId() + ",enum=" + this.getPosition() + ")";
            } else {
                posStr = getPositionEnum().name();
            }
            long dateJoined = System.currentTimeMillis() - allianceSeniorityMs();
            body.append(" | `").append(posStr).append("` | ").append(DiscordUtil.timestamp(dateJoined, null));
        }
        body.append("\n");
        User user = getUser();
        {
            String prefix = "";
            if (user != null) {
                long created = user.getTimeCreated().toEpochSecond() * 1000L;
                body.append(user.getAsMention() + " | " + MarkupUtil.markdownUrl(DiscordUtil.getFullUsername(user), DiscordUtil.userUrl(user.getIdLong(), false)) + " | " + DiscordUtil.timestamp(created, null));
                prefix = " | ";
            }
            List<DBBan> bans = getBans();
            if (!bans.isEmpty()) {
                body.append(prefix).append(bans.size() + " bans");
                prefix = " | ";
            }
            int reports = getNumReports();
            if (reports > 0) {
                body.append(prefix).append(reports + " reports");
            }
            body.append("\n");
        }
        body.append("\n");
        {
            Collection<DBCity> cities = _getCitiesV3().values();
            double infra = 0;
            double buildingInfra = 0;
            int unpowered = 0;
            for (DBCity value : cities) {
                buildingInfra += value.getNumBuildings() * 50;
                infra += value.infra;
                if (!value.powered) unpowered++;
            }
            infra /= cities.size();
            buildingInfra /= cities.size();
            body.append("Infra: `").append(MathMan.format(infra)).append("/").append(MathMan.format(buildingInfra)).append("` | ");
            body.append("Cities: `").append(cities.size());
            if (unpowered == 0) {
                body.append("`");
            } else if (unpowered == cities.size()) {
                body.append("` (unpowered)");
            } else {
                body.append("` (").append(unpowered).append(" unpowered)");
            }
            body.append(" | ").append("Off: `").append(getOff()).append("/").append(getMaxOff()).append("` | ");
            body.append("Def: `").append(getDef()).append("/").append(3).append("`\n");
        }
        //VM: Timestamp(Started) - Timestamp(ends) (5 turns)
        if (getVm_turns() > 0) {
            body.append("VM: ").append(DiscordUtil.timestamp(TimeUtil.getTimeFromTurn(entered_vm), null)).append(" - ").append(DiscordUtil.timestamp(TimeUtil.getTimeFromTurn(leaving_vm), null)).append(" (").append(getVm_turns()).append(" turns)").append("\n");
        }
        //Domestic/War policy | beige turns | score
        body.append("`").append(this.domestic_policy.name()).append("` | `").append(this.war_policy.name()).append("` | `").append(MathMan.format(score) + "ns").append("` | `").append(getContinent().name()).append("`\n");
        //MMR[Building]: 1/2/3 | MMR[Unit]: 5/6/7
        body.append("\n");
        //
        //Units: Now/Buyable/Cap
        body.append("**Units:** Now/Remaining Buy/Cap (assumes 5553)\n```json\n");
        //Soldier: 0/0/0
        long dcTurn = this.getTurnsFromDC();
        long dcTimestamp = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - dcTurn);
        for (MilitaryUnit unit : new MilitaryUnit[]{MilitaryUnit.SOLDIER, MilitaryUnit.TANK, MilitaryUnit.AIRCRAFT, MilitaryUnit.SHIP, MilitaryUnit.SPIES, MilitaryUnit.MISSILE, MilitaryUnit.NUKE}) {
            int cap = getUnitCap(unit, false);
            if (cap == Integer.MAX_VALUE) cap = -1;
            // 6 chars
            String unitsStr = String.format("%6s", getUnits(unit));
            // getRemainingUnitBuy(unit, dcTimestamp)
            String remainingStr = String.format("%6s", getRemainingUnitBuy(unit, dcTimestamp));
            String capStr = String.format("%6s", cap);
            body.append(String.format("%2s", unit.getEmoji())).append(" ").append(unitsStr).append("|").append(remainingStr).append("|").append(capStr).append("").append("\n");
        }
        body.append("\n```\n");
        body.append("MMR[Build]=`").append(getMMRBuildingStr()).append("` | MMR[Unit]=`").append(getMMR()).append("`\n\n");
        //
        //Attack Range: War= | Spy=
        {
            double offWarMin = PnwUtil.getAttackRange(true, true, true, score);
            double offWarMax = PnwUtil.getAttackRange(true, true, false, score);
            double offSpyMin = PnwUtil.getAttackRange(true, false, true, score);
            double offSpyMax = PnwUtil.getAttackRange(true, false, false, score);
            // use MathMan.format to format doubles
            body.append("**Attack Range**: War=`").append(MathMan.format(offWarMin)).append("-").append(MathMan.format(offWarMax)).append("` | Spy=`").append(MathMan.format(offSpyMin)).append("-").append(MathMan.format(offSpyMax)).append("`\n");
        }
        //Defense Range: War= | Spy=
        {
            double defWarMin = PnwUtil.getAttackRange(false, true, true, score);
            double defWarMax = PnwUtil.getAttackRange(false, true, false, score);
            double defSpyMin = PnwUtil.getAttackRange(false, false, true, score);
            double defSpyMax = PnwUtil.getAttackRange(false, false, false, score);
            // use MathMan.format to format doubles
            body.append("**Defense Range**: War=`").append(MathMan.format(defWarMin)).append("-").append(MathMan.format(defWarMax)).append("` | Spy=`").append(MathMan.format(defSpyMin)).append("-").append(MathMan.format(defSpyMax)).append("`\n");
        }
        body.append("\n");
        //
        Map<String, Integer> timerStr = new LinkedHashMap<>();
        //(optional) Timers: city=1, project=1, color=1, war=, domestic=1
        long cityTurns = getCityTurns();
        if (cityTurns > 0) timerStr.put("city", (int) cityTurns);
        long projectTurns = getProjectTurns();
        if (projectTurns > 0) timerStr.put("project", (int) projectTurns);
        long colorTurns = getColorTurns();
        if (colorTurns > 0) timerStr.put("color", (int) colorTurns);
        long warTurns = getWarPolicyTurns();
        if (warTurns > 0) timerStr.put("war", (int) warTurns);
        long domesticTurns = getDomesticPolicyTurns();
        if (domesticTurns > 0) timerStr.put("domestic", (int) domesticTurns);
        if (!timerStr.isEmpty()) {
            body.append("**Timers:** `" + timerStr.toString() + "`\n");
        }
        //(optional) Active wars
        //
        //Revenue: {}
        // - Worth: $10
        double[] revenue = getRevenue();
        body.append("**Revenue:**");
        body.append(" worth: `$").append(MathMan.format(PnwUtil.convertedTotal(revenue))).append("`");
        body.append("\n```json\n").append(PnwUtil.resourcesToString(revenue)).append("\n``` ");
        //
        body.append("\n");
        //Projects: 5/10 | [Projects] (bold VDS and ID)
        List<Project> projects = new ArrayList<>(getProjects());
        projects.sort(Comparator.comparing(Project::name));
        Function<String, String> toAcronym = s -> Arrays.stream(s.split("_")).map(w -> w.substring(0, 1)).collect(Collectors.joining()).toUpperCase(Locale.ROOT);

        body.append("**Projects:** ").append(getNumProjects()).append("/").append(projectSlots()).append("\n- ")
                .append(projects.stream().map(f -> {
                    String name = toAcronym.apply(f.name());
                    return (f == Projects.IRON_DOME || f == Projects.VITAL_DEFENSE_SYSTEM ? "**" + name + "**" : name);
                }).collect(Collectors.joining(", "))).append("\n");

        Set<Integer> blockaded = this.getBlockadedBy();
        if (!blockaded.isEmpty()) {
            // DBNation.byId(id)
            // if not null, append nation.getMarkdownUrl(true) else, append id. comma separated
            body.append("Blockaded By: ").append(blockaded.stream().map(id -> {
                DBNation nation = DBNation.getById(id);
                return nation != null ? nation.getMarkdownUrl() : String.valueOf(id);
            }).collect(Collectors.joining(","))).append("\n");
        }
        // use MathMan.format to turn into Map<WarType, String> from Map<WarType, Long>
        Map<WarType, String> bounties = getBountySums().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> MathMan.format(e.getValue())));
        //(optional) Bounties: {}
        if (!bounties.isEmpty()) {
            body.append("Bounties: `").append(bounties.toString()).append("\n");
        }
        return body.toString();
    }

    public Set<DBBounty> getBounties() {
        return Locutus.imp().getWarDb().getBounties(getId());
    }

    @Command(desc = "Get a map of each war type to the total value of bounties for that type that this nation has")
    public Map<WarType, Long> getBountySums() {
        return getBounties().stream().collect(Collectors.groupingBy(DBBounty::getType, Collectors.summingLong(DBBounty::getAmount)));
    }

    public String toMarkdown(boolean embed, boolean war, boolean title, boolean general, boolean military, boolean spies) {
        StringBuilder response = new StringBuilder();
        if (title) {
            String nationUrl;
            if (war) {
                String url = Settings.INSTANCE.PNW_URL() + "/nation/war/declare/id=" + getNation_id();
                nationUrl = embed ? MarkupUtil.markdownUrl(getName(), url) : "<" + url + ">";
            } else {
                nationUrl = getNationUrlMarkup(embed);
            }
            String allianceUrl = getAllianceUrlMarkup(embed);
            response
                    .append(nationUrl)
                    .append(" | ")
                    .append(allianceUrl);

            if (embed && getPositionEnum() == Rank.APPLICANT && alliance_id != 0) response.append(" (applicant)");

            if (getVm_turns() > 0) {
                response.append(" | VM");
            }

            response.append('\n');
        }
        if (general || military) {
            response.append("```");
            if (general) {
                int active = getActive_m();
                active = active - active % (60);
                String time = active <= 1 ? "Online" : TimeUtil.secToTime(TimeUnit.MINUTES, active);
                response
                        .append(String.format("%5s", (int) getScore())).append(" ns").append(" | ")
                        .append(String.format("%6s", time)).append(" | ")
                        .append(String.format("%2s", getCities())).append(" \uD83C\uDFD9").append(" | ")
                        .append(String.format("%5s", (int) getAvg_infra())).append(" \uD83C\uDFD7").append(" | ");
            }
            if (military) {
                response
                        .append(String.format("%6s", getSoldiers())).append(" \uD83D\uDC82").append(" | ")
                        .append(String.format("%5s", getTanks())).append(" \u2699").append(" | ")
                        .append(String.format("%5s", getAircraft())).append(" \u2708").append(" | ")
                        .append(String.format("%4s", getShips())).append(" \u26F5").append(" | ")
                        .append(String.format("%2s", getSpies())).append(" \uD83D\uDD0E").append(" | ");
            }
            if (general) {
                response
                        .append(String.format("%8s", getWarPolicy())).append(" | ")
                        .append(String.format("%1s", getOff())).append("\uD83D\uDDE1").append(" | ")
                        .append(String.format("%1s", getDef())).append("\uD83D\uDEE1").append(" | ");
            }
            String str = response.toString();
            if (str.endsWith(" | ")) response = new StringBuilder(str.substring(0, str.length() - 3));
            response.append("```");
        }
        return response.toString();
    }

    @Command(desc = "The infra level of their highest city")
    public double maxCityInfra() {
        double maxInfra = 0;
        Collection<DBCity> cities = _getCitiesV3().values();
        for (DBCity city : cities) {
            maxInfra = Math.max(city.infra, maxInfra);
        }
        return maxInfra;
    }

    public JsonObject sendMail(ApiKeyPool pool, String subject, String message, boolean priority) throws IOException {
        if (pool.size() == 1 && pool.getNextApiKey().getKey().equalsIgnoreCase(Settings.INSTANCE.API_KEY_PRIMARY)) {
            Auth auth = Locutus.imp().getRootAuth();
            if (auth != null) {
                String result = new MailTask(auth, priority, this, subject, message, null).call();
                if (result.contains("Message sent")) {
                    return JsonParser.parseString("{\"success\":true,\"to\":\"" + nation_id + "\",\"cc\":null,\"subject\":\"" + subject + "\"}").getAsJsonObject();
                }
            }
        }

        long exponentialBackoff = 1000;
        while (true) {
            ApiKeyPool.ApiKey pair = pool.getNextApiKey();
            Map<String, String> post = new HashMap<>();
            post.put("to", getNation_id() + "");
            post.put("subject", subject);
            post.put("message", message);
            String url = "" + Settings.INSTANCE.PNW_URL() + "/api/send-message/?key=" + pair.getKey();
            String result = FileUtil.get(FileUtil.readStringFromURL(priority ? PagePriority.MAIL_SEND_SINGLE : PagePriority.MAIL_SEND_BULK, url, post, null));
            System.out.println("Result " + result);
            if (result.contains("Invalid API key")) {
                pair.deleteApiKey();
                pool.removeKey(pair);
            } else {
                String successStr = "success\":";
                int successIndex = result.indexOf(successStr);
                if (successIndex != -1) {
                    char tf = result.charAt(successIndex + successStr.length());
                    if (tf == 't') {
                        return JsonParser.parseString(result).getAsJsonObject();
                    }
                }
            }
            try {
                return JsonParser.parseString(result).getAsJsonObject();
            } catch (JsonSyntaxException e) {
                System.out.println("Error sending mail to " + getNation_id() + " with key " + pair.getKey());
                System.out.println(result);
            }
            System.out.println("Mail response " + result);
            break;
        }
        return null;
    }

    public int estimateBeigeTime() {
        if (!isBeige()) {
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
        List<AbstractCursor> attacks = Locutus.imp().getWarDb().getAttacks(nation_id, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days + 1));
        for (AbstractCursor attack : attacks) {
            if (attack.getAttack_type() != AttackType.VICTORY || attack.getVictor() == nation_id) continue;

            ZonedDateTime warTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(attack.getDate()), ZoneOffset.UTC);
            long warTurns = TimeUtil.getTurn(warTime);

            if (warTurns < currentTurn - days * 12) continue;
            int turnsAgo = (int) (currentTurn - warTurns);
            beigeList.add(new AbstractMap.SimpleEntry<>(attack.getDate(), 24));
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
        if (this.beigeTimer < turnLeaveBeige) {
            this.beigeTimer = turnLeaveBeige;
            Locutus.imp().getNationDB().saveNation(this);
        }
        return turnsBeige;
    }

    @Command(desc = "Game turns left on the beige color bloc")
    public int getBeigeTurns() {
        if (!isBeige()) return 0;
        long turn = TimeUtil.getTurn();
        if (turn >= beigeTimer) {
            return 24;
        } else {
            return (int) (beigeTimer - turn);
        }
    }

    @Command(desc = "Returns self nation ID if the nation is a reroll, otherwise 0")
    public int isReroll() {
        return isReroll(false);
    }

    public int isReroll(boolean fetchUid) {
        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNations();
        for (Map.Entry<Integer, DBNation> entry : nations.entrySet()) {
            int otherId = entry.getKey();
            DBNation otherNation = entry.getValue();
            if (otherNation.getDate() == 0) continue;

            if (otherId > nation_id && Math.abs(otherNation.getDate()  - getDate()) > TimeUnit.DAYS.toMillis(14)) {
                return nation_id;
            }
        }

        if (Settings.INSTANCE.TASKS.AUTO_FETCH_UID && fetchUid) {
            try {
                BigInteger uid = fetchUid(true);
                for (Map.Entry<Integer, Long> entry : Locutus.imp().getDiscordDB().getUuids(uid)) {
                    int nationId = entry.getKey();
                    if (nationId == this.nation_id) continue;
                    BigInteger latest = Locutus.imp().getDiscordDB().getLatestUuid(nationId);
                    if (latest != null && latest.equals(uid)) {
                        return nationId;
                    }
                    break;
                }
            } catch (Exception e) {
                AlertUtil.error("Failed to fetch uid for " + nation_id, e);
            }
        }

        return 0;
    }

    public BigInteger fetchUid(boolean priority) throws IOException {
        return new GetUid(this, priority).call();
    }

    public NationMeta.BeigeAlertMode getBeigeAlertMode(NationMeta.BeigeAlertMode def) {
        ByteBuffer value = getMeta(NationMeta.BEIGE_ALERT_MODE);
        if (value == null) {
            return def;
        }
        return NationMeta.BeigeAlertMode.values()[value.get()];
    }

    public double getBeigeAlertRequiredLoot() {
        double requiredLoot = 15000000;
        ByteBuffer requiredLootBuf = getMeta(NationMeta.BEIGE_ALERT_REQUIRED_LOOT);
        if (requiredLootBuf != null) {
            requiredLoot = requiredLootBuf.getDouble();
        }
        return requiredLoot;
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

    @Command(desc = "If this nation is not daily active and lost their most recent war")
    public boolean lostInactiveWar() {
        if (getActive_m() < 2880) return false;
        DBWar lastWar = Locutus.imp().getWarDb().getLastOffensiveWar(nation_id);
        if (lastWar != null && lastWar.defender_id == nation_id && lastWar.status == WarStatus.ATTACKER_VICTORY) {
            long lastActiveCutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(Math.max(active_m() + 1220, 7200));
            if (lastWar.date > lastActiveCutoff) return true;
        }
        return false;
    }

    @Command(desc = "Turns left on the city timer")
    public long getCityTurns() {
        return (cityTimer - TimeUtil.getTurn());
    }

    @Command(desc = "Absolute turn the city time ends before being able to buy a city")
    public long getCityTimerAbsoluteTurn() {
        return cityTimer;
    }

    @Command(desc = "Turns left on the project timer before being able to buy a project")
    public long getProjectTurns() {
        return (projectTimer - TimeUtil.getTurn());
    }

    public void setMMR(double barracks, double factories, double hangars, double drydocks) {
        soldiers = (int) (barracks * cities * Buildings.BARRACKS.max());
        tanks = (int) (factories * cities * Buildings.FACTORY.max());
        aircraft = (int) (hangars * cities * Buildings.HANGAR.max());
        ships = (int) (drydocks * cities * Buildings.DRYDOCK.max());
    }

    @Command(desc = "Number of buildings total")
    public int getBuildings() {
        int total = 0;
        Collection<DBCity> cities = _getCitiesV3().values();
        for (DBCity city : cities) {
            total += city.getNumBuildings();
        }
        return total;
    }

    @Command(desc = "Number of buildings per city")
    public double getAvgBuildings() {
        int total = 0;
        Collection<DBCity> cities = _getCitiesV3().values();
        for (DBCity city : cities) {
            total += city.getNumBuildings();
        }
        return total / (double) cities.size();
    }

    @Command(desc = "Net nation deposits with their alliance guild divided by their city count")
    @RolePermission(Roles.ECON)
    public double getAllianceDepositValuePerCity() throws IOException {
        return getAllianceDepositValue() / cities;
    }

    @Command(desc = "Net nation deposits with their alliance guild")
    @RolePermission(Roles.ECON)
    public double getAllianceDepositValue() throws IOException {
        GuildDB db = Locutus.imp().getGuildDBByAA(alliance_id);
        if (db == null) return 0;
        boolean includeGrants = db.getOrNull(GuildKey.MEMBER_CAN_WITHDRAW_IGNORES_GRANTS) == Boolean.FALSE;
        double[] depo = getNetDeposits(db, includeGrants, -1, false);
        return PnwUtil.convertedTotal(depo);
    }

    @Command(desc = "If on the correct MMR for their alliance (if one is set)")
    @RolePermission(Roles.MEMBER)
    public boolean correctAllianceMMR() {
        if (getPosition() <= 1 || getVm_turns() > 0) return true;

        GuildDB db = Locutus.imp().getGuildDBByAA(alliance_id);
        if (db == null) return true;

        return db.hasRequiredMMR(this);
    }

    @Command(desc = "Array of the average military buildings (building mmr) in all cities as a decimal\n" +
            "[barracks, factories, hangars, drydocks]\n" +
            "e.g. [5.0,5.0,5.0,3.0]")
    public double[] getMMRBuildingArr() {
        double barracks = 0; // for rounding
        double factories = 0;
        double hangars = 0;
        double drydocks = 0;
        Map<Integer, JavaCity> cityMap = getCityMap(false);
        for (Map.Entry<Integer, JavaCity> entry : cityMap.entrySet()) {
            barracks += entry.getValue().get(Buildings.BARRACKS);
            factories += entry.getValue().get(Buildings.FACTORY);
            hangars += entry.getValue().get(Buildings.HANGAR);
            drydocks += entry.getValue().get(Buildings.DRYDOCK);
        }
        barracks /= cityMap.size();
        factories /= cityMap.size();
        hangars /= cityMap.size();
        drydocks /= cityMap.size();
        return new double[]{barracks, factories, hangars, drydocks};
    }
    @Command(desc = "The average military buildings (building mmr) in all cities as a whole number\n" +
            "barracks factories hangars drydocks\n" +
            "Maximum is: 5553")
    public String getMMRBuildingStr() {
        double[] arr = getMMRBuildingArr();
        return Math.round(arr[0]) + "" + Math.round(arr[1]) + "" + Math.round(arr[2]) + "" + Math.round(arr[3]);
    }

    /**
     * MMR (units)
     * @return
     */
    @Command(desc = "Average military unit building capacity (unit mmr) in all cities as a whole number\n" +
            "soldiers tanks aircraft ships\n" +
            "Maximum is: 5553")
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
    public Activity getActivity() {
        return new Activity(getNation_id());
    }

    public Activity getActivity(long turns) {
        return new Activity(getNation_id(), turns);
    }

    public JsonObject sendMail(Auth auth, boolean priority, String subject, String body) throws IOException {
        String result = new MailTask(auth, priority, this, subject, body, null).call();
        if (result.contains("Message sent")) {
            return JsonParser.parseString("{\"success\":true,\"to\":\"" + nation_id + "\",\"cc\":null,\"subject\":\"" + subject + "\"}").getAsJsonObject();
        }
        // return new json object
        JsonObject obj = new JsonObject();
        // add error key with result
        obj.addProperty("error", result);
        return obj;
    }

    public Map.Entry<Integer, Integer> getCommends() throws IOException {
        Document dom = Jsoup.parse(FileUtil.readStringFromURL(PagePriority.COMMEND, getNationUrl()));
        int commend = Integer.parseInt(dom.select("#commendment_count").text());
        int denounce = Integer.parseInt(dom.select("#denouncement_count").text());
        return new AbstractMap.SimpleEntry<>(commend, denounce);
    }

    public void setProjectsRaw(long projBitmask) {
        getCache().lastCheckProjectsMS = System.currentTimeMillis();
        this.projects = projBitmask;
    }

    @Command(desc = "The projects built as a bitmask")
    public long getProjectBitMask() {
        return projects;
    }

    public double estimateScore() {
        return estimateScore(getInfra());
    }


    public double infraCost(double from, double to) {
        return PnwUtil.calculateInfra(from, to,
                hasProject(Projects.ADVANCED_ENGINEERING_CORPS),
                hasProject(Projects.CENTER_FOR_CIVIL_ENGINEERING),
                getDomesticPolicy() == DomesticPolicy.URBANIZATION,
                hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY));
    }

    public double landCost(double from, double to) {
        double factor = 1;
        if (hasProject(Projects.ADVANCED_ENGINEERING_CORPS)) factor -= 0.05;
        if (hasProject(Projects.ARABLE_LAND_AGENCY)) factor -= 0.05;
        if (getDomesticPolicy() == DomesticPolicy.RAPID_EXPANSION) {
            factor -= 0.05;
            if (hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY)) factor -= 0.025;
        }
        return PnwUtil.calculateLand(from, to) * (to > from ? factor : 1);
    }
    public double printScore() {
        double base = 10;
        System.out.println("base " + base);
        base += getNumProjects() * Projects.getScore();
        System.out.println("projects " + base);
        base += (cities - 1) * 100;
        System.out.println("cities " + base);
        base += getInfra() / 40d;
        System.out.println("infra " + base);
        for (MilitaryUnit unit : MilitaryUnit.values) {
            int amt = getUnits(unit);
            if (amt > 0) {
                base += unit.getScore(amt);
                System.out.println("- unt " + amt + " | " + unit + " | " + unit.getScore(amt));
            } else if (unit == MilitaryUnit.NUKE) {
                System.out.println("Unit " + amt + " | " + getNukes());
            }
        }
        System.out.println("unit " + base);
        return base;
    }

    public double estimateScore(double infra) {
        return estimateScore(null, infra, null, null);
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
            Map<ResourceType, Double> cityProfit = PnwUtil.resourcesToMap(city.profit(continent, getRads(), -1L, this::hasProject, null, cities, 1, 12));
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
        return Locutus.imp().getWarDb().getActiveWars(nation_id);
    }

    public List<DBWar> getActiveOffensiveWars() {
        List<DBWar> myWars = getActiveWars();
        if (myWars.isEmpty()) return Collections.emptyList();
        List<DBWar> result = new ArrayList<>(myWars);
        result.removeIf(f -> f.attacker_id != nation_id);
        return result;
    }

    public List<DBWar> getActiveDefensiveWars() {
        List<DBWar> myWars = getActiveWars();
        if (myWars.isEmpty()) return Collections.emptyList();
        List<DBWar> result = new ArrayList<>(myWars);
        result.removeIf(f -> f.defender_id != nation_id);
        return result;
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
        return getAlliancePosition(history);
    }

    public Map.Entry<Integer, Rank> getAlliancePosition(Map<Integer, Map.Entry<Long, Rank>> history) {
        int latestAA = alliance_id;
        Rank latestRank = getPositionEnum();
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
        return new AbstractMap.SimpleEntry<>(alliance_id, getPositionEnum());
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

    @Command(desc = "If there is remaining purchase for a unit today")
    public boolean hasUnitBuyToday(MilitaryUnit unit) {
        long turnDayStart = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - TimeUtil.getDayTurn());
        return Locutus.imp().getNationDB().hasBought(this, unit, turnDayStart);
    }

    @Command(desc = "purchased soldiers today")
    public boolean hasBoughtSoldiersToday() {
        return hasUnitBuyToday(MilitaryUnit.SOLDIER);
    }

    @Command(desc = "purchased tanks today")
    public boolean hasBoughtTanksToday() {
        return hasUnitBuyToday(MilitaryUnit.TANK);
    }

    @Command(desc = "purchased aircraft today")
    public boolean hasBoughtAircraftToday() {
        return hasUnitBuyToday(MilitaryUnit.AIRCRAFT);
    }

    @Command(desc = "purchased ships today")
    public boolean hasBoughtShipsToday() {
        return hasUnitBuyToday(MilitaryUnit.SHIP);
    }

    @Command(desc = "purchased nuclear weapons today")
    public boolean hasBoughtNukeToday() {
        return hasUnitBuyToday(MilitaryUnit.NUKE);
    }

    @Command(desc = "purchased missiles today")
    public boolean hasBoughtMissileToday() {
        return hasUnitBuyToday(MilitaryUnit.MISSILE);
    }

    @Command(desc = "purchased spies today")
    public boolean hasBoughtSpiesToday() {
        return hasUnitBuyToday(MilitaryUnit.SPIES);
    }

    @Command(desc = "Days since last soldier purchase")
    public double daysSinceLastSoldierBuy() {
        Long result = getLastUnitBuy(MilitaryUnit.SOLDIER);
        return result == null ? Long.MAX_VALUE : (((double) (System.currentTimeMillis() - result)) / TimeUnit.DAYS.toMillis(1));
    }

    @Command(desc = "Days since last tank purchase")
    public double daysSinceLastTankBuy() {
        Long result = getLastUnitBuy(MilitaryUnit.TANK);
        return result == null ? Long.MAX_VALUE : (((double) (System.currentTimeMillis() - result)) / TimeUnit.DAYS.toMillis(1));
    }

    @Command(desc = "Days since last aircraft purchase")
    public double daysSinceLastAircraftBuy() {
        Long result = getLastUnitBuy(MilitaryUnit.AIRCRAFT);
        return result == null ? Long.MAX_VALUE : (((double) (System.currentTimeMillis() - result)) / TimeUnit.DAYS.toMillis(1));
    }

    @Command(desc = "Days since last ship purchase")
    public double daysSinceLastShipBuy() {
        Long result = getLastUnitBuy(MilitaryUnit.SHIP);
        return result == null ? Long.MAX_VALUE : (((double) (System.currentTimeMillis() - result)) / TimeUnit.DAYS.toMillis(1));
    }

    @Command(desc = "Days since last spy purchase")
    public double daysSinceLastSpyBuy() {
        Long result = getLastUnitBuy(MilitaryUnit.SPIES);
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
            List<AbstractCursor> attacks = Locutus.imp().getWarDb().getAttacks(getNation_id(), cutoff);

            outer:
            for (AbstractCursor attack : attacks) {
                MilitaryUnit[] units = attack.getAttack_type().getUnits();
                for (MilitaryUnit other : units) {
                    if (other == unit) {
                        Map<MilitaryUnit, Integer> losses = attack.getUnitLosses(attack.getAttacker_id() == nation_id);
                        long turn = TimeUtil.getTurn(attack.getDate());
                        unitsLost.put(turn, losses.getOrDefault(unit, 0) + unitsLost.getOrDefault(turn, 0));
                        continue outer;
                    }
                }
            }

            outer:
            for (AbstractCursor attack : attacks) {
                AbstractMap.SimpleEntry<Long, Integer> toAdd = new AbstractMap.SimpleEntry<>(attack.getDate(), getUnits(unit));
                int i = 0;
                for (; i < history.size(); i++) {
                    Map.Entry<Long, Integer> entry = history.get(i);
                    long diff = Math.abs(entry.getKey() - attack.getDate());
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

    public long getRerollDate() {
        List<DBNation> nations = new ArrayList<>(Locutus.imp().getNationDB().getNations().values());
        int previousNationId = -1;
        for (DBNation nation : nations) {
            if (nation.getNation_id() < nation_id) {
                previousNationId = Math.max(previousNationId, nation.getNation_id());
            }
        }
        int finalPreviousNationId = previousNationId;
        nations.removeIf(f -> f.getNation_id() < finalPreviousNationId);
        // sort nations by nation_id
        nations.sort(Comparator.comparingInt(DBNation::getNation_id));

        long minDate = Long.MAX_VALUE;
        for (int i = 1; i < nations.size() - 1; i++) {
            DBNation nation = nations.get(i);
            if (nation.nation_id <= nation_id) continue;
            if (nation.date < date) {
                DBNation previous = nations.get(i - 1);
                if (previous.date < nation.date) {
                    // valid
                    minDate = Math.min(nation.date, minDate);
                }
            }
        }
        return minDate;
    }

    public Map<Integer, Long> findDayChange() {
        MilitaryUnit[] units = new MilitaryUnit[]{MilitaryUnit.SOLDIER, MilitaryUnit.TANK, MilitaryUnit.AIRCRAFT, MilitaryUnit.SHIP, MilitaryUnit.MISSILE, MilitaryUnit.NUKE};
        int[] caps = new int[units.length];
        caps[0] = Buildings.BARRACKS.perDay() * Buildings.BARRACKS.cap(this::hasProject) * getCities();
        caps[1] = Buildings.FACTORY.perDay() * Buildings.FACTORY.cap(this::hasProject) * getCities();
        caps[2] = Buildings.HANGAR.perDay() * Buildings.HANGAR.cap(this::hasProject) * getCities();
        caps[3] = Buildings.DRYDOCK.perDay() * Buildings.DRYDOCK.cap(this::hasProject) * getCities();
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
            RateLimitUtil.queue(RateLimitUtil.complete(user.openPrivateChannel()).sendMessage(msg));
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

    @Command(desc = "Total project slots (used + unused)")
    public int projectSlots() {
        int warBonus = this.wars_won + this.wars_lost >= 100 ? 1 : 0;
        int projectBonus = hasProject(Projects.RESEARCH_AND_DEVELOPMENT_CENTER) ? 2 : 0;
        return ((int) getInfra() / 5000) + 1 + warBonus + projectBonus;
    }

//    public void setSpy_kills(int spy_kills) {
//        this.spy_kills = spy_kills;
//    }
//
//    public void setSpy_casualties(int spy_casualties) {
//        long now = System.currentTimeMillis();
////        if (spy_casualties > this.spy_casualties) {
////            int diff = spy_casualties - this.spy_casualties;
////            if (getCache().lastCheckSpyCasualtiesMs > getCache().lastUpdateSpiesMs) {
////                this.spies = Math.max(0, this.spies - diff);
////            }
////        }
////        getCache().lastCheckSpyCasualtiesMs = now;
//        this.spy_casualties = spy_casualties;
//    }
//
//    @Command
//    public int getSpy_kills() {
//        return spy_kills;
//    }
//
//    @Command
//    public int getSpy_casualties() {
//        return spy_casualties;
//    }

    @Command(desc = "Total wars won")
    public int getWars_won() {
        return wars_won;
    }

    @Command(desc = "Total wars lost")
    public int getWars_lost() {
        return wars_lost;
    }

    public void setWars_won(int wars_won) {
        this.wars_won = wars_won;
    }

    public void setWars_lost(int wars_lost) {
        this.wars_lost = wars_lost;
    }

    public AttackCost getWarCost() {
        AttackCost cost = new AttackCost();
        List<AbstractCursor> attacks = Locutus.imp().getWarDb().getAttacks(nation_id, 0);
        cost.addCost(attacks, a -> a.getAttacker_id() == nation_id, b -> b.getDefender_id() == nation_id);
        return cost;
    }

    @Command(desc = "Total money looted")
    @RolePermission(Roles.MILCOM)
    public double getMoneyLooted() {
        double total = 0;
        List<AbstractCursor> attacks = Locutus.imp().getWarDb().getAttacks(nation_id, 0);
        for (AbstractCursor attack : attacks) {
            if (attack.getAttacker_id() == nation_id) {
                double[] loot = attack.getLoot();
                if (loot != null) total += PnwUtil.convertedTotal(loot);
            }
        }
        return total;
    }

    public void setCityTimer(Long timer) {
        this.cityTimer = timer;
    }

    public void setProjectTimer(Long timer) {
        this.projectTimer = timer;
    }

    @Command(desc = "Absolute turn when full espionage slots will reset")
    public long getEspionageFullTurn() {
        return espionageFull;
    }

    @Command(desc = "If espionage slots are full")
    public boolean isEspionageFull() {
        return this.getVm_turns() > 0 || this.espionageFull > TimeUtil.getTurn();
    }

    @Command(desc = "If there are remaining espionage slots")
    public boolean isEspionageAvailable() {
        return !isEspionageFull();
    }

    @Command(desc = "The turn of the day (0-11) when their day change (DC) unit rebuy is available")
    public int getDc_turn() {
        return dc_turn;
    }

    public void setDc_turn(int dc_turn) {
        this.dc_turn = dc_turn;
    }

    @Command(desc = "Turns remaining until their day change (DC)")
    public int getTurnsTillDC() {
        int currentTurn = (int) TimeUtil.getDayTurn();
        if (currentTurn >= dc_turn) return (dc_turn + 12) - currentTurn;
        return dc_turn - currentTurn;
    }

    @Command
    public int getTurnsFromDC() {
        int currentTurnMod = (int) TimeUtil.getDayTurn() % 12;
        if (currentTurnMod >= dc_turn) {
            return currentTurnMod - dc_turn;
        } else {
            return (currentTurnMod + 12) - dc_turn;
        }
    }

    @Command(desc = "Are any cities powered")
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

    @Command(desc = "Days since a nation posted a spy report")
    public int getDaysSinceLastSpyReport() {
        ByteBuffer lastSpyOpDayBuf = getMeta(NationMeta.SPY_OPS_DAY);
        if (lastSpyOpDayBuf != null) {
            long currentDay = TimeUtil.getDay();
            return (int) (currentDay - lastSpyOpDayBuf.getLong());
        }
        return Integer.MAX_VALUE;
    }

    @Command(desc = "How many spy reports they have posted today")
    public int getSpyReportsToday() {
        if (getDaysSinceLastSpyReport() == 0) {
            ByteBuffer dailyOpAmt = getMeta(NationMeta.SPY_OPS_AMOUNT_DAY);
            if (dailyOpAmt != null) {
                return dailyOpAmt.getInt();
            }
        }
        return 0;
    }

    @Command(desc = "Days since their last offensive war")
    public double daysSinceLastOffensive() {
        if (getOff() > 0) return 0;
        DBWar last = Locutus.imp().getWarDb().getLastOffensiveWar(nation_id);
        if (last != null) {
            long diff = System.currentTimeMillis() - last.date;
            return ((double) diff) / TimeUnit.DAYS.toMillis(1);
        }
        return Integer.MAX_VALUE;
    }

    @Command(desc = "Days since last defensive war")
    public double daysSinceLastDefensiveWarLoss() {
        long maxDate = 0;
        for (DBWar war : Locutus.imp().getWarDb().getWarsByNation(nation_id)) {
            if (war.defender_id == nation_id && war.status == WarStatus.ATTACKER_VICTORY) {
                maxDate = Math.max(maxDate, war.date);
            }
        }
        return maxDate == 0 ? Integer.MAX_VALUE : ((double) (System.currentTimeMillis() - maxDate)) / TimeUnit.DAYS.toMillis(1);
    }

    @Command(desc = "Days since last war")
    public double daysSinceLastWar() {
        if (getNumWars() > 0) return 0;
        DBWar last = Locutus.imp().getWarDb().getLastWar(nation_id);
        if (last != null) {
            long diff = System.currentTimeMillis() - last.date;
            return ((double) diff) / TimeUnit.DAYS.toMillis(1);
        }
        return Integer.MAX_VALUE;
    }

    public boolean updateEspionageFull() {
        if (getVm_turns() > 0) return true;

        long lastUpdated = espionageFull == 0 ? 0 : Math.abs(espionageFull);
        long dc = TimeUtil.getTurn() - TimeUtil.getDayTurn();

        Auth auth = Locutus.imp().getRootAuth();
        Callable<Long> task = new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                String baseUrl = "" + Settings.INSTANCE.PNW_URL() + "/nation/espionage/eid=";
                String url = baseUrl + getNation_id();
                String html = auth.readStringFromURL(PagePriority.ESPIONAGE_FULL_UNUSED, url, Collections.emptyMap());
                if (html.contains("This target has already had 3 espionage operations executed upon them today.")) {
                    return TimeUtil.getTurn() + getTurnsTillDC();
                }
                return 0L;
            }
        };
        try {
            espionageFull = PnwUtil.withLogin(task, auth);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this.espionageFull > TimeUtil.getTurn();
    }

    public void setBeigeTimer(long time) {
        this.beigeTimer = time;
    }

    @Override
    @Command(desc = "The nation id")
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

    @Command(desc = "Average land per city")
    public double getAvgLand() {
        double total = 0;
        Collection<DBCity> cities = _getCitiesV3().values();
        for (DBCity city : cities) {
            total += city.land;
        }
        return total / cities.size();
    }

    @Command(desc = "Total land in their cities")
    public double getTotalLand() {
        double total = 0;
        Collection<DBCity> cities = _getCitiesV3().values();
        for (DBCity city : cities) {
            total += city.land;
        }
        return total;
    }

    @Command(desc = "Free offensive war slots")
    public int getFreeOffensiveSlots() {
        return getMaxOff() - getOff();
    }

    @Command(desc = "Maximum offensive war slots")
    public int getMaxOff() {
        int slots = 5;
        if (hasProject(Projects.PIRATE_ECONOMY)) {
            slots++;
        }
        if (hasProject(Projects.ADVANCED_PIRATE_ECONOMY)) {
            slots++;
        }
        return slots;
    }

    public void setEspionageFull(boolean value) {
        DBNationCache cache = getCache();
        long currentTurn = TimeUtil.getTurn();
        boolean isTurnChange = cache.lastCheckEspionageFull == currentTurn - 1;
        cache.lastCheckEspionageFull = currentTurn;

        if (!value) {
            if (this.espionageFull > 0 && isTurnChange) {
                int turn = (int) TimeUtil.getDayTurn();
                if (turn != this.dc_turn) {
                    this.dc_turn = turn;
                }
            }
            espionageFull = 0;
        } else if (espionageFull == 0) {
            espionageFull = TimeUtil.getTurn() + getTurnsTillDC();
        }
    }

    @Command(desc = "ID of their in-game tax rate bracket")
    public int getTax_id() {
        return tax_id;
    }

    public void setTax_id(int tax_id) {
        this.tax_id = tax_id;
    }

//    public void setGDP(double gdp) {
//        this.gdp = gdp;
//    }

    public void setGNI(double gni) {
        this.gni = gni;
    }

    public GuildDB getGuildDB() {
        if (alliance_id == 0) return null;
        return Locutus.imp().getGuildDBByAA(alliance_id);
    }

    public Map.Entry<CommandResult, String> runCommandInternally(Guild guild, User user, String command) {
        if (user == null) return new AbstractMap.SimpleEntry<>(CommandResult.ERROR, "No user for: " + getNation());

        StringMessageIO output = new StringMessageIO(user);
        CommandResult type;
        String result;
        try {
            Locutus.imp().getCommandManager().run(guild, output, user, command, false, true);
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

    public void setLastActive(long epoch) {
        this.last_active = epoch;
    }

    public void update(boolean bulk) {
        Locutus.imp().runEventsAsync(events ->
        Locutus.imp().getNationDB().updateNations(List.of(nation_id), bulk, events));
    }

    public void updateCities(boolean bulk) {
        Locutus.imp().runEventsAsync(events ->
                Locutus.imp().getNationDB().updateCitiesOfNations(Set.of(nation_id), true, bulk, events));
    }

    public double[] projectCost(Project project) {
        return project.cost(getDomesticPolicy() == DomesticPolicy.TECHNOLOGICAL_ADVANCEMENT);
    }

    public double getGrossModifier() {
        return getGrossModifier(false);
    }
    public double getGrossModifier(boolean noFood) {
        double grossModifier = 1;
        if (getDomesticPolicy() == DomesticPolicy.OPEN_MARKETS) {
            grossModifier += 0.01;
            if (hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY)) {
                grossModifier += 0.005;
            }
        }
        if (noFood) grossModifier -= 0.33;
        return grossModifier;
    }

    public double getMilitaryUpkeepFactor() {
        double factor = 1;
        if (getDomesticPolicy() == DomesticPolicy.IMPERIALISM) {
            factor -= 0.05;
            if (hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY)) {
                factor -= 0.025;
            }
        }
        return factor;
    }

    /**
     *
     * @return
     */
    @Command(desc = "The modifier for loot given when they are defeated in war")
    public double lootModifier() {
        double value = 1;
        if (getWarPolicy() == WarPolicy.TURTLE) {
            value += 0.2;
        } else if (getWarPolicy() == WarPolicy.MONEYBAGS) {
            value -= 0.4;
        } else if (getWarPolicy() == WarPolicy.GUARDIAN) {
            value += 0.2;
        }
        return value;
    }

    @Command(desc = "The modifier for loot given when they defeat an enemy in war")
    public double looterModifier(boolean isGround) {
        double modifier = 1;
        if (getWarPolicy() == WarPolicy.PIRATE) {
            modifier += 0.4;
        } else if (getWarPolicy() == WarPolicy.ATTRITION) {
            modifier -= 0.2;
        }
        if (hasProject(Projects.ADVANCED_PIRATE_ECONOMY)) {
            if (isGround) {
                modifier += 0.05;
            }
            modifier += 0.1;
        }
        if (hasProject(Projects.PIRATE_ECONOMY)) {
            if (isGround) {
                modifier += 0.05;
            }
        }
        return modifier;
    }

    /*
        // blitzkrieg = For the first 12 turns (24 hours) after switching, your nation does 10% more infrastructure damage and casualties in Ground Battles, Airstrikes, and Naval Battles.
     */

    @Command(desc = "Whether their war policy is blitzkrieg and within the first 12 turns of switching to it")
    public boolean isBlitzkrieg() {
        return getWarPolicy() == WarPolicy.BLITZKRIEG && getWarPolicyTurns() > GameTimers.WAR_POLICY.getTurns() - 12;
    }

    @Command(desc = "The modifier for infra damage")
    public double infraAttackModifier(AttackType type) {
        boolean isGroundAirOrNaval = switch (type) {
            case GROUND, AIRSTRIKE_INFRA, AIRSTRIKE_SOLDIER, AIRSTRIKE_TANK, AIRSTRIKE_MONEY, AIRSTRIKE_SHIP, AIRSTRIKE_AIRCRAFT, NAVAL -> true;
            default -> false;
        };
        return switch (getWarPolicy()) {
            case ATTRITION -> isGroundAirOrNaval ? 1.1 : 1;
            case BLITZKRIEG -> {
                if (isGroundAirOrNaval && getWarPolicyTurns() > GameTimers.WAR_POLICY.getTurns() - 12) {
                    yield 1.1;
                } else {
                    yield 1;
                }
            }
            default -> 1;
        };
    }

    @Command(desc = "The modifier for infra damage")
    public double infraDefendModifier(AttackType type) {
        boolean isGroundAirOrNaval = switch (type) {
            case GROUND, AIRSTRIKE_INFRA, AIRSTRIKE_SOLDIER, AIRSTRIKE_TANK, AIRSTRIKE_MONEY, AIRSTRIKE_SHIP, AIRSTRIKE_AIRCRAFT, NAVAL -> true;
            default -> false;
        };
        return switch (getWarPolicy()) {
            case TURTLE -> isGroundAirOrNaval ? 0.9 : 1;
            case MONEYBAGS -> 1.05;
            case COVERT, ARCANE -> isGroundAirOrNaval ? 1.05 : 1;
            default -> 1;
        };
    }

    public boolean isInWarRange(DBNation target) {
        return target.getScore() > getScore() * 0.75 && target.getScore() < getScore() * 1.25;
    }

    public TaxBracket getTaxBracket() {
        return new TaxBracket(tax_id, alliance_id, "", -1, -1, 0);
    }

    @Command(desc = "Their gross income (GNI)")
    public double getGNI() {
        return gni;
    }

    @Command(desc = "Has any bounties placed on them to defeat them in war or detonate a nuke")
    public boolean hasBounty() {
        return !Locutus.imp().getWarDb().getBounties(nation_id).isEmpty();
    }

    @Command(desc = "Has any bounties placed on them to defeat them in war")
    public boolean hasWarBounty() {
        return !Locutus.imp().getWarDb().getBounties(nation_id).isEmpty();
    }

    @Command(desc = "Has any bounties placed on them to detonate a nuke")
    public boolean hasNukeBounty() {
        return Locutus.imp().getWarDb().getBounties(nation_id).stream().anyMatch(f -> f.getType() == WarType.NUCLEAR);
    }

    @Command(desc = "Maximum total bounty placed on them of any type")
    public double maxBountyValue() {
        Set<DBBounty> bounties = Locutus.imp().getWarDb().getBounties(nation_id);
        if (bounties.isEmpty()) return 0;
        if (bounties.size() == 1) return bounties.iterator().next().getAmount();
        Map<WarType, Double> sumByType = bounties.stream().collect(Collectors.groupingBy(DBBounty::getType, Collectors.summingDouble(DBBounty::getAmount)));
        return sumByType.values().stream().max(Double::compareTo).orElse(0D);
    }

    @Command(desc = "Maximum total bounty placed on them of any war type")
    public double maxWarBountyValue() {
        Set<DBBounty> bounties = Locutus.imp().getWarDb().getBounties(nation_id);
        bounties.removeIf(f -> f.getType() == WarType.NUCLEAR);
        if (bounties.isEmpty()) return 0;
        if (bounties.size() == 1) return bounties.iterator().next().getAmount();
        Map<WarType, Double> sumByType = bounties.stream().collect(Collectors.groupingBy(DBBounty::getType, Collectors.summingDouble(DBBounty::getAmount)));
        return sumByType.values().stream().max(Double::compareTo).orElse(0D);
    }

    @Command(desc = "Sum of all nuke bounties placed on them")
    public double nukeBountyValue() {
        return Locutus.imp().getWarDb().getBounties(nation_id).stream().filter(f -> f.getType() == WarType.NUCLEAR).mapToLong(DBBounty::getAmount).sum();
    }

    @Command(desc = "Sum of all raid bounties placed on them")
    public double raidBountyValue() {
        return Locutus.imp().getWarDb().getBounties(nation_id).stream().filter(f -> f.getType() == WarType.RAID).mapToLong(DBBounty::getAmount).sum();
    }

    @Command(desc = "Sum of all ordinary bounties placed on them")
    public double ordinaryBountyValue() {
        return Locutus.imp().getWarDb().getBounties(nation_id).stream().filter(f -> f.getType() == WarType.ORD).mapToLong(DBBounty::getAmount).sum();
    }

    @Command(desc = "Sum of all attrition bounties placed on them")
    public double attritionBountyValue() {
        return Locutus.imp().getWarDb().getBounties(nation_id).stream().filter(f -> f.getType() == WarType.ATT).mapToLong(DBBounty::getAmount).sum();
    }
}
