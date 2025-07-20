package link.locutus.discord.db.entities;

import com.google.gson.JsonSyntaxException;
import com.politicsandwar.graphql.model.*;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.building.PowerBuilding;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.apiv3.enums.ApiKeyPermission;
import link.locutus.discord.apiv3.enums.GameTimers;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.commands.manager.v2.binding.bindings.ScopedPlaceholderCache;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.command.*;
import link.locutus.discord.commands.manager.v2.command.shrink.EmptyShrink;
import link.locutus.discord.commands.manager.v2.command.shrink.IShrink;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.*;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.DBNationGetter;
import link.locutus.discord.db.entities.nation.DBNationSetter;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.nation.*;
import link.locutus.discord.pnw.*;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.battle.BlitzGenerator;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.offshore.TransferResult;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.discord.util.scheduler.ThrowingFunction;
import link.locutus.discord.util.scheduler.ThrowingSupplier;
import link.locutus.discord.util.sheet.SheetUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.task.MailTask;
import link.locutus.discord.util.task.ia.IACheckup;
import link.locutus.discord.util.task.mail.MailApiResponse;
import link.locutus.discord.util.task.mail.MailApiSuccess;
import link.locutus.discord.util.task.multi.GetUid;
import link.locutus.discord.util.task.roles.AutoRoleInfo;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.util.update.NationUpdateProcessor;
import link.locutus.discord.web.jooby.handler.CommandResult;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import link.locutus.discord.apiv1.domains.subdomains.SNationContainer;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.building.ResourceBuilding;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import javax.annotation.Nullable;
import java.io.IOException;
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

public abstract class DBNation implements NationOrAlliance {
    public static DBNation getByUser(User user) {
        return DiscordUtil.getNation(user);
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
        return new SimpleDBNation(new DBNationData(coalition, nations, average)) {
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
        return Locutus.imp().getNationDB().getNationById(nationId);
    }

    public static DBNation getOrCreate(int nationId) {
        DBNation existing = getById(nationId);
        if (existing == null) {
            existing = new SimpleDBNation(new DBNationData());
            existing.edit().setNation_id(nationId);
        }
        return existing;
    }

    ////

    public abstract DBNationGetter data();
    public abstract DBNationSetter edit();

    public abstract DBNation copy();

    public void processTurnChange(long lastTurn, long turn, Consumer<Event> eventConsumer) {
        if (data()._leavingVm() == turn) {
            if (eventConsumer != null) eventConsumer.accept(new NationLeaveVacationEvent(this, this));
        }
        if (data()._beigeTimer() == turn) {
            if (eventConsumer != null) eventConsumer.accept(new NationLeaveBeigeEvent(this, this));
        }
        if (data()._colorTimer() == turn) {
            if (eventConsumer != null) eventConsumer.accept(new NationColorTimerEndEvent(this, this));
        }
        if (data()._warPolicyTimer() == turn) {
            if (eventConsumer != null) eventConsumer.accept(new NationWarPolicyTimerEndEvent(this, this));
        }
        if (data()._domesticPolicyTimer() == turn) {
            if (eventConsumer != null) eventConsumer.accept(new NationDomesticPolicyTimerEndEvent(this, this));
        }
        if (data()._cityTimer() == turn) {
            if (eventConsumer != null) eventConsumer.accept(new NationCityTimerEndEvent(this, this));
        }
        if (data()._projectTimer() == turn) {
            if (eventConsumer != null) eventConsumer.accept(new NationProjectTimerEndEvent(this, this));
        }
    }

    @Command(desc = "If the nation is taxable")
    public boolean isTaxable() {
        return !isGray() && !isBeige() && getPositionEnum().id > Rank.APPLICANT.id && getVm_turns() == 0;
    }

//    public double getInfraCost(double from, double to) {
//        double cost = PW.calculateInfra(from, to);
//    }

    @Command(desc = "Daily revenue value of nation")
    public double getRevenueConverted() {
        return ResourceType.convertedTotal(getRevenue());
    }

    @Command(desc = "Daily revenue value of nation per city")
    public double getRevenuePerCityConverted() {
        return getRevenueConverted() / data()._cities();
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
        DBNationCache cache = data()._cache();
        return cache != null ? cache.lastCheckUnitMS : 0;
    }

    public void setLastFetchedUnitsMs(long timestamp) {
        DBNationCache cache = data()._cache();
        if (cache != null) cache.lastCheckUnitMS = timestamp;
    }

    public String register(User user, GuildDB db, boolean isNewRegistration) {
        if (data()._nationId() == Settings.INSTANCE.NATION_ID) {
            if (Settings.INSTANCE.ADMIN_USER_ID != user.getIdLong()) {
                if (Settings.INSTANCE.ADMIN_USER_ID > 0) {
                    throw new IllegalArgumentException("Invalid admin user id in `config.yaml`. Tried to register `" + user.getIdLong() + "` but config has `" + Settings.INSTANCE.ADMIN_USER_ID + "`");
                }
                Settings.INSTANCE.ADMIN_USER_ID = user.getIdLong();
                Settings.INSTANCE.save(Settings.INSTANCE.getDefaultFile());
            }
        }
        new NationRegisterEvent(data()._nationId(), db, user, isNewRegistration).post();

        StringBuilder output = new StringBuilder();
//        try {
//            String endpoint = "" + Settings.PNW_URL() + "/api/discord/validateDiscord.php?access_key=%s&nation_id=%s";
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
            try {
                Role role = Roles.REGISTERED.toRole2(db);
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
                        output.append("\nCreate a role mapping with " + CM.role.setAlias.cmd.toSlashMention());
                    }
                }
            } catch (HierarchyException e) {
                output.append("\nCannot add role (Make sure the Bot's role is high enough and has add role perms)\n- " + e.getMessage());
            }
        }
        return output.toString();
    }

    @Command(desc = "The absolute turn of leaving beige")
    public long getBeigeAbsoluteTurn() {
        return data()._beigeTimer();
    }

    @Command(desc = "The absolute turn the war policy change timer expires")
    public long getWarPolicyAbsoluteTurn() {
        return data()._warPolicyTimer();
    }

    @Command(desc = "The absolute turn the domestic policy change timer expires")
    public long getDomesticPolicyAbsoluteTurn() {
        return data()._domesticPolicyTimer();
    }

    @Command(desc = "The absolute turn the color change timer expires")
    public long getColorAbsoluteTurn() {
        return data()._colorTimer();
    }

    @Command(desc = "The number of turns until the color timer expires")
    public long getColorTurns() {
        return Math.max(0, getColorAbsoluteTurn() - TimeUtil.getTurn(getSnapshot()));
    }

    @Command(desc = "The number of turns until the domestic policy timer expires")
            public long getDomesticPolicyTurns() {
        return Math.max(0, getDomesticPolicyAbsoluteTurn() - TimeUtil.getTurn(getSnapshot()));
    }

    @Command(desc = "The number of turns until the war policy timer expires")
    public long getWarPolicyTurns() {
        return Math.max(0, getWarPolicyAbsoluteTurn() - TimeUtil.getTurn(getSnapshot()));
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
        return (this.data()._projects() & (1L << project.ordinal())) != 0;
    }

    @Command(desc = "Can build project (meets requirements, free slot, doesn't have project)")
    public boolean canBuildProject(Project project) {
        return !hasProject(project) && project.canBuild(this) && getFreeProjectSlots() > 0;
    }

    private Map.Entry<Object, String> getAuditRaw(ValueStore store, @Me GuildDB db, IACheckup.AuditType audit) throws IOException, ExecutionException, InterruptedException {
        ScopedPlaceholderCache<DBNation> scoped = PlaceholderCache.getScoped(store, DBNation.class, "getAudit");
        List<DBNation> nations = scoped.getList(this);
        AllianceList aaList = db.getAllianceList().subList(nations);
        IACheckup checkup = scoped.getGlobal((ThrowingSupplier<IACheckup>)
                () -> new IACheckup(db, aaList, true));
        if (!aaList.isInAlliance(this)) {
            throw new IllegalArgumentException("Nation " + data()._nationId() + " not in alliance: " + checkup.getAlliance().getIds());
        }
        Map<IACheckup.AuditType, Map.Entry<Object, String>> result =
                checkup.checkup(store, this, new IACheckup.AuditType[]{audit}, true, true);
        return result.get(audit);
    }

    @RolePermission(Roles.INTERNAL_AFFAIRS_STAFF)
    @Command(desc = "If the nation passes an audit")
    public boolean passesAudit(ValueStore store, @Me GuildDB db, IACheckup.AuditType audit) throws IOException, ExecutionException, InterruptedException {
        Map.Entry<Object, String> result = getAuditRaw(store, db, audit);
        return result == null || result.getKey() == null;
    }

    @RolePermission(Roles.INTERNAL_AFFAIRS_STAFF)
    @Command(desc = "Get the Audit result raw value")
    public String getAuditResult(ValueStore store, @Me GuildDB db, IACheckup.AuditType audit) throws IOException, ExecutionException, InterruptedException {
        Map.Entry<Object, String> result = getAuditRaw(store, db, audit);
        return result == null ? null : result.getKey() + "";
    }

    @RolePermission(Roles.INTERNAL_AFFAIRS_STAFF)
    @Command(desc = "Get the Audit result message")
    public String getAuditResultString(ValueStore store, @Me GuildDB db, IACheckup.AuditType audit) throws IOException, ExecutionException, InterruptedException {
        Map.Entry<Object, String> result = getAuditRaw(store, db, audit);
        return result == null ? null : result.getValue();
    }

    @Override
    public boolean isValid() {
        return getById(data()._nationId()) != null;
    }

    @Command(desc = "If the nation has all of the specified projects")
    public boolean hasProjects(@NoFormat Set<Project> projects, @Default("false") boolean any) {
        if (any) {
            for (Project p : projects) {
                if (hasProject(p)) {
                    return true;
                }
            }
            return false;
        }
        for (Project p : projects) {
            if (!hasProject(p)) {
                return false;
            }
        }
        return true;
    }

    @Command(desc = "If the nation has the treasure")
    public boolean hasTreasure() {
        return !Locutus.imp().getNationDB().getTreasure(data()._nationId()).isEmpty();
    }

    public Set<DBTreasure> getTreasures() {
        return Locutus.imp().getNationDB().getTreasure(data()._nationId());
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
        edit().setProjects(data()._projects() | 1L << project.ordinal());
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
        if (active_m() > 385920) return null;
//        if (!isGray()) return null;
        if (getDef() == 3) return null;
        long currentDate = System.currentTimeMillis();

        LootEntry loot = Locutus.imp().getNationDB().getLoot(getNation_id());
        if (loot != null && loot.getDate() > cutoff) return null;

        long lastLootDate = 0;
        if (loot != null) lastLootDate = Math.max(lastLootDate, loot.getDate());
        if (currentDate - active_m() * 60L * 1000L < lastLootDate) return null;

        long checkBankCutoff = currentDate - TimeUnit.DAYS.toMillis(60);
        if (data()._cities() > 10 && lastLootDate < checkBankCutoff) {
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
        double cityCost = PW.City.nextCityCost(data()._cities(), true, hasProject(Projects.URBAN_PLANNING),
                hasProject(Projects.ADVANCED_URBAN_PLANNING),
                hasProject(Projects.METROPOLITAN_PLANNING),
                hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY),
                hasProject(Projects.BUREAU_OF_DOMESTIC_AFFAIRS));
        double maxStockpile = cityCost * 2;
        double daysToMax = maxStockpile / (getInfra() * 300);
        if (lastLootDate == 0) {
            lastLootDate = currentDate - TimeUnit.DAYS.toMillis((int) daysToMax);
        }

        long diffMin = TimeUnit.MILLISECONDS.toMinutes(currentDate - lastLootDate);

        if (active_m() < 12000) {
            diffMin /= 8;
            DBWar lastWar = Locutus.imp().getWarDb().getLastDefensiveWar(data()._nationId());
            if (lastWar != null) {
                long warDiff = currentDate - TimeUnit.DAYS.toMillis(240);
                if (lastWar.getDate() > warDiff) {
                    double ratio = TimeUnit.MILLISECONDS.toDays(currentDate - lastWar.getDate()) / 240d;
                    if (lastWar.getStatus() == WarStatus.PEACE || lastWar.getStatus() == WarStatus.DEFENDER_VICTORY) {
                        diffMin *= ratio;
                    }
                }
            }
        }

        double value = getAvg_infra() * (diffMin + active_m()) * getCities();

        if (loot == null && data()._cities() < 12) {
            long finalLastLootDate = lastLootDate;
            Map<Integer, DBWar> wars = Locutus.imp().getWarDb().getWarsForNationOrAlliance(f -> f == data()._nationId(), null, f -> f.getDate() > finalLastLootDate);
            if (!wars.isEmpty()) {
                WarParser cost = WarParser.of(wars.values(), f -> f.getAttacker_id() == data()._nationId());
                double total = cost.toWarCost(false, false, false, false, false).convertedTotal(true);
                value -= total;
            }
        }

        // value for weak military
        double soldierPct = (double) getSoldiers() / (Buildings.BARRACKS.getUnitCap() * Buildings.BARRACKS.cap(this::hasProject) * getCities());
        double tankPct = (double) getTanks() / (Buildings.FACTORY.getUnitCap() * Buildings.FACTORY.cap(this::hasProject) * getCities());
        value = value + value * (2 - soldierPct - tankPct);

        return new KeyValue<>(value, loot != null);
    }

    public @Nullable Long getSnapshot() {
        return null;
    }

    @Command(desc = "Days since joining the alliance")
    public double allianceSeniority() {
        long result = allianceSeniorityMs();
        if (result == 0 || result == Long.MAX_VALUE) return result;
        return result / (double) TimeUnit.DAYS.toMillis(1);
    }

    @Command(desc = "Days since joining the alliance")
        public double allianceSeniorityApplicant() {
        long result = allianceSeniorityApplicantMs();
        if (result == 0 || result == Long.MAX_VALUE) return result;
        return result / (double) TimeUnit.DAYS.toMillis(1);
    }

    @Command
    public long allianceSeniorityNoneMs() {
        if (data()._allianceId() != 0) return 0;
        long timestamp = Locutus.imp().getNationDB().getAllianceMemberSeniorityTimestamp(this, getSnapshot());
        long now = getSnapshot() == null ? System.currentTimeMillis() : getSnapshot();
        if (timestamp > now) return 0;
        return now - timestamp;
    }

    @Command(desc = "Milliseconds since joining the alliance")
    public long allianceSeniorityApplicantMs() {
        if (data()._allianceId() == 0) return 0;
        long timestamp = Locutus.imp().getNationDB().getAllianceApplicantSeniorityTimestamp(this, getSnapshot());
        long now = getSnapshot() == null ? System.currentTimeMillis() : getSnapshot();
        if (timestamp > now) return 0;
        return (now - timestamp);
    }

    @Command(desc = "Milliseconds since joining the alliance")
    public long allianceSeniorityMs() {
        if (data()._allianceId() == 0) return 0;
        long timestamp = Locutus.imp().getNationDB().getAllianceMemberSeniorityTimestamp(this, getSnapshot());
        long now = getSnapshot() == null ? System.currentTimeMillis() : getSnapshot();
        if (timestamp > now) return 0;
        return now - timestamp;
    }

    @Command(desc="Military strength (1 plane = 1)")
    public double getStrength() {
        return BlitzGenerator.getAirStrength(this, true);
    }

    @Command(desc="Military strength (1 plane = 1)")
    public double getStrengthMMR(MMRDouble mmr) {
        return BlitzGenerator.getAirStrength(this, mmr);
    }

    @Command(desc = "Estimated combined strength of the enemies its fighting")
    public double getEnemyStrength() {
        Set<DBWar> wars = getActiveWars();
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
    public int minWarResistance() { // TODO FIXME Placeholders.PlaceholderCache<DBNation> cache
        if (getNumWars() == 0) return 100;
        int[] min = {100};
        Locutus.imp().getWarDb().iterateAttackList(getActiveWars(), AttackType::canDamage, null, (war, attacks) -> {
            boolean isAttacker = war.isAttacker(this);

            Map.Entry<Integer, Integer> warRes = war.getResistance(attacks);
            int myRes = isAttacker ? warRes.getKey() : warRes.getValue();
            if (myRes < min[0]) min[0] = myRes;
        });
        return min[0];
    }

    @Command(desc="Minimum resistance of self in current active wars, assuming the enemy translates their MAP into ground/naval with guaranteed IT")
    public int minWarResistancePlusMap() {
        if (getNumWars() == 0) return 100;
        int[] min = {100};
        Locutus.imp().getWarDb().iterateAttackList(getActiveWars(), AttackType::canDamage, null, (war, attacks) -> {
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
            if (myRes < min[0]) min[0] = myRes;
        });
        return min[0];
    }

    @Command(desc = "Relative strength compared to enemies its fighting (1 = equal)")
    public double getRelativeStrength() {
        return getRelativeStrength(true);
    }

    public double exponentialCityStrength(@Default Double power) {
        if (power == null) power = 3d;
        return Math.pow(getCities(), power);
    }

    public double getRelativeStrength(boolean inactiveIsLoss) {
        if (active_m() > 2440 && inactiveIsLoss) return 0;

        double myStr = getStrength();
        double enemyStr = getEnemyStrength();
        double otherMin = BlitzGenerator.getBaseStrength(data()._cities());
        enemyStr = Math.max(otherMin, enemyStr);

        return myStr / enemyStr;
    }

    @Command(desc = "Set of projects this nation has")
    public Set<Project> getProjects() {
        if (this.data()._projects() == -1) return Collections.EMPTY_SET;

        Set<Project> set = null;
        for (Project value : Projects.values) {
            if (hasProject(value)) {
                if (set == null) set = new ObjectLinkedOpenHashSet<>();
                set.add(value);
            }
        }
        return set == null ? Collections.EMPTY_SET : set;
    }

    public Auth auth = null;

    public String setTaxBracket(TaxBracket bracket, Auth auth) {
        if (bracket.getAlliance_id() != data()._allianceId()) throw new UnsupportedOperationException("Not in alliance");

        Map<String, String> post = new HashMap<>();
        post.put("bracket_id", "" + bracket.taxId);
        post.put("change_member_bracket", "Update Nation's Bracket");
        post.put("nation_id", getNation_id() + "");
        String url = String.format(Settings.PNW_URL() + "/alliance/id=%s&display=taxes", data()._allianceId());

        return PW.withLogin(() -> {
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
                GuildDB db = Locutus.imp().getGuildDBByAA(data()._allianceId());
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

    @Command(desc = "Effective strength of the strongest nation this nation is attacking (offensive war)")
    public double getStrongestOffEnemyOfScore(double minScore, double maxScore) {
        Set<DBWar> wars = getActiveOffensiveWars();
        double strongest = -1;
        for (DBWar war : wars) {
            DBNation other = war.getNation(!war.isAttacker(this));
            if (other == null || other.active_m() > 2440 || other.getVm_turns() > 0) continue;
//            if (filter.test(other.getScore())) {
            if (other.getScore() >= minScore && other.getScore() <= maxScore) {
                strongest = Math.max(strongest, other.getStrength());
            }
        }
        return strongest;
    }

    @Command(desc = "Effective strength of the strongest nation this nation is fighting")
    public double getStrongestEnemy() {
        double val = getStrongestEnemyOfScore(0, Double.MAX_VALUE);
        return val == -1 ? 0 : val;
    }

    @Command(desc = "Relative strength of the strongest nation this nation is fighting (1 = equal)")
    public double getStrongestEnemyRelative() {
        double enemyStr = getStrongestEnemy();
        double myStrength = getStrength();
        return myStrength == 0 ? 0 : enemyStr / myStrength;
    }

    @Command(desc = "Get the effective military strength of the strongegst nation within the provided score range")
    public double getStrongestEnemyOfScore(double minScore, double maxScore) {
        Set<DBWar> wars = getActiveWars();
        double strongest = -1;
        for (DBWar war : wars) {
            DBNation other = war.getNation(!war.isAttacker(this));
            if (other == null || other.active_m() > 2440 || other.getVm_turns() > 0) continue;
            if (other.getScore() >= minScore && other.getScore() <= maxScore) {
                strongest = Math.max(strongest, other.getStrength());
            }
        }
        return strongest;
    }

    @Command(desc = "If this nation has an offensive war against an enemy in the provided score range")
    public boolean isAttackingEnemyOfScore(double minScore, double maxScore) {
        return getStrongestOffEnemyOfScore(minScore, maxScore) != -1;
    }

    @Command(desc = "If this nation has a war with an enemy in the provided score range")
    public boolean isFightingEnemyOfScore(double minScore, double maxScore) {
        return getStrongestEnemyOfScore(minScore, maxScore) != -1;
    }

    @Command(desc = "If this nation has an war with an enemy in the provided city range")
    public boolean isFightingEnemyOfCities(double minCities, double maxCities) {
        for (DBWar war : getWars()) {
            DBNation other = war.getNation(!war.isAttacker(this));
            if (other != null && other.getCities() >= minCities && other.getCities() <= maxCities) {
                return true;
            }
        }
        return false;
    }

    @Command(desc = "If this nation has a defensive war from an enemy in the provided city range")
    public boolean isDefendingEnemyOfCities(double minCities, double maxCities) {
        for (DBWar war : getWars()) {
            if (war.getDefender_id() != data()._nationId()) continue;
            DBNation other = war.getNation(!war.isAttacker(this));
            if (other != null && other.getCities() >= minCities && other.getCities() <= maxCities) {
                return true;
            }
        }
        return false;
    }

    @Command(desc = "If this nation has an offensive war against an enemy in the provided city range")
    public boolean isAttackingEnemyOfCities(double minCities, double maxCities) {
        for (DBWar war : getWars()) {
            if (war.getAttacker_id() != data()._nationId()) continue;
            DBNation other = war.getNation(!war.isAttacker(this));
            if (other != null && other.getCities() >= minCities && other.getCities() <= maxCities) {
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
                if (this.data()._nationId() == Locutus.loader().getNationId()) {
                    if (!Settings.INSTANCE.USERNAME.isEmpty() && !Settings.INSTANCE.PASSWORD.isEmpty()) {
                        return auth = new Auth(data()._nationId(), Settings.INSTANCE.USERNAME, Settings.INSTANCE.PASSWORD);
                    }
                }
                Map.Entry<String, String> pass = Locutus.imp().getDiscordDB().getUserPass2(data()._nationId());
                if (pass == null) {
                    PNWUser dbUser = getDBUser();
                    if (dbUser != null) {
                        pass = Locutus.imp().getDiscordDB().getUserPass2(dbUser.getDiscordId());
                        if (pass != null) {
                            Locutus.imp().getDiscordDB().addUserPass2(data()._nationId(), pass.getKey(), pass.getValue());
                            Locutus.imp().getDiscordDB().logout(dbUser.getDiscordId());
                        }
                    }
                }
                if (pass != null) {
                    auth = new Auth(data()._nationId(), pass.getKey(), pass.getValue());
                }
            }
        }
        if (auth == null && throwError) {
            throw new IllegalArgumentException("Please authenticate using " + CM.credentials.login.cmd.toSlashMention());
        }
        return auth;
    }


    @Command(desc = "The radiation level of the nation")
    public double getRads() {
        double radIndex;
        if (getSnapshot() != null) {
            long turn = TimeUtil.getTurn(getSnapshot());
            Map<Continent, Double> rads = Locutus.imp().getNationDB().getRadiationByTurn(turn);
            if (rads == null || rads.isEmpty()) return 0;
            radIndex = rads.get(data()._continent()) + rads.values().stream().mapToDouble(f -> f).sum() / 5d;
        } else {
            TradeManager manager = Locutus.imp().getTradeManager();
            radIndex = manager.getGlobalRadiation() + manager.getGlobalRadiation(getContinent());
        }
        return (1 + (Math.min(1000, radIndex) / (-1000)));
    }

    @Command(desc = "Raw positional value (0 = remove, 1 = app, 2 = member, 3 = officer 4 = heir, 5 = leader)")
    public int getPosition() {
        return data()._rank().id;
    }

    @Command(desc = """
            Alliance position enum id
            0 = None or Removed
            1 = Applicant
            2 = Member
            3 = Officer
            4 = Heir
            5 = Leader""")
    public Rank getPositionEnum() {
        return data()._rank();
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
        return data()._alliancePosition();
    }

//    public boolean updateNationWithInfo(DBNation copyOriginal, NationMilitaryContainer nation, Consumer<Event> eventConsumer) {
//
//    }

    public boolean updateNationInfo(SNationContainer nation, Consumer<Event> eventConsumer) {
        boolean dirty = false;
        DBNation copyOriginal = null;
        if (nation.getNationid() != null && this.getNation_id() != nation.getNationid()) {
            if (copyOriginal == null && eventConsumer != null) copyOriginal = copy();
            this.setNation_id(nation.getNationid());
            dirty = true;
        }
        if (nation.getNation() != null && (this.getNation() == null || !this.data()._nation().equals(nation.getNation()))) {
            dirty = true;
            if (copyOriginal == null && eventConsumer != null) copyOriginal = copy();
            this.edit().setNation(nation.getNation());
            if (eventConsumer != null) eventConsumer.accept(new NationChangeNameEvent(copyOriginal, this));
        }
        if (nation.getLeader() != null && (this.getLeader() == null || !this.data()._leader().equals(nation.getLeader()))) {
            dirty = true;
            if (copyOriginal == null && eventConsumer != null) copyOriginal = copy();
            this.edit().setLeader(nation.getLeader());
            if (eventConsumer != null) eventConsumer.accept(new NationChangeLeaderEvent(copyOriginal, this));
        }
        if (nation.getContinent() != null) {
            Continent continent = Continent.valueOf(nation.getContinent().toUpperCase(Locale.ROOT).replace(" ", "_"));
            if (continent != this.data()._continent()) {
                dirty = true;
                if (copyOriginal == null && eventConsumer != null) copyOriginal = copy();
                this.edit().setContinent(continent);
                this.markCitiesDirty();
                if (eventConsumer != null) eventConsumer.accept(new NationChangeContinentEvent(copyOriginal, this));
            }
        }
        if (nation.getWarPolicy() != null) {
            WarPolicy warPolicy = WarPolicy.parse(nation.getWarPolicy());
            if (warPolicy != this.data()._warPolicy()) {
                dirty = true;
                if (copyOriginal == null && eventConsumer != null) copyOriginal = copy();
                setWarPolicy(warPolicy);
                if (eventConsumer != null) eventConsumer.accept(new NationChangeWarPolicyEvent(copyOriginal, this));
            }
        }
        if (nation.getColor() != null) {
            NationColor color = NationColor.valueOf(nation.getColor().toUpperCase(Locale.ROOT));
            if (color != this.data()._color()) {
                dirty = true;
                if (copyOriginal == null && eventConsumer != null) copyOriginal = copy();
                setColor(color);
                if (eventConsumer != null) {
                    if (copyOriginal.data()._color() == NationColor.BEIGE) eventConsumer.accept(new NationLeaveBeigeEvent(copyOriginal, this));
                    eventConsumer.accept(new NationChangeColorEvent(copyOriginal, this));
                }
            }
        }
        if (nation.getAllianceid() != null && this.data()._allianceId() != nation.getAllianceid()) {
            dirty = true;
            if (copyOriginal == null && eventConsumer != null) copyOriginal = copy();
            this.setAlliance_id(nation.getAllianceid());
            if (eventConsumer != null) eventConsumer.accept(new NationChangeAllianceEvent(copyOriginal, this));
        }
        if (nation.getAllianceposition() != null && (this.data()._rank() == null || this.data()._rank().id != nation.getAllianceposition())) {
            dirty = true;
            if (copyOriginal == null && eventConsumer != null) copyOriginal = copy();
            this.edit().setRank(Rank.byId(nation.getAllianceposition()));
            if (eventConsumer != null) eventConsumer.accept(new NationChangeRankEvent(copyOriginal, this));
        }
        if (nation.getCities() != null && this.data()._cities() != nation.getCities()) {
            dirty = true;
            if (copyOriginal == null && eventConsumer != null) copyOriginal = copy();
            this.setCities(nation.getCities());
            if (eventConsumer != null) eventConsumer.accept(new NationChangeCitiesEvent(copyOriginal, this));
        }
        if (nation.getScore() != null && nation.getScore() != this.data()._score()) {
            this.edit().setScore(nation.getScore());
            dirty = true;
        }
        if (nation.getVacmode() != null && nation.getVacmode() != this.getVm_turns()) {
            dirty = true;
            if (copyOriginal == null && eventConsumer != null) copyOriginal = copy();
            this.setLeaving_vm(TimeUtil.getTurn() + nation.getVacmode());
            if (eventConsumer != null) eventConsumer.accept(new NationChangeVacationEvent(copyOriginal, this));
        }
        if (nation.getMinutessinceactive() != null && nation.getMinutessinceactive() < this.active_m() - 3) {
            dirty = true;
            if (copyOriginal == null && eventConsumer != null) copyOriginal = copy();
            this.edit().setLast_active(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(nation.getMinutessinceactive()));
            if (eventConsumer != null) eventConsumer.accept(new NationChangeActiveEvent(copyOriginal, this));
        }
        if (this.isBeige() && data()._beigeTimer() == 0) {
            this.edit().setBeigeTimer(TimeUtil.getTurn() + 14 * 12);
        }
        return dirty;
    }

    private void markCitiesDirty() {
        long now = System.currentTimeMillis();
        NationDB db = Locutus.loader().getCachedNationDB();
        if (db != null) {
            for (int cityId : db.getCitiesV3(data()._nationId()).keySet())
                db.markCityDirty(getNation_id(), cityId, now);
        }
    }

    public boolean updateNationInfo(DBNation copyOriginal, com.politicsandwar.graphql.model.Nation nation, Consumer<Event> eventConsumer) {
        boolean dirty = false;
        Double discount = nation.getCities_discount();
        if (discount != null && Math.round(this.getCityRefund() * 100) != Math.round(discount * 100)) {
            this.edit().setCostReduction(discount);
            dirty = true;
        }

        MilitaryResearch apiResearch = nation.getMilitary_research();
        if (apiResearch != null) {
            int researchBits = Research.toBits(apiResearch);
            if (this.getResearchBits() != researchBits) {
                this.edit().setResearchBits(researchBits);
                dirty = true;
            }
        }

        if (nation.getWars_won() != null && this.data()._warsWon() != nation.getWars_won()) {
            this.edit().setWars_won(nation.getWars_won());
            dirty = true;
        }
        if (nation.getWars_lost() != null && this.data()._warsLost() != nation.getWars_lost()) {
            this.edit().setWars_lost(nation.getWars_lost());
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
                String name;
                if (user != null) {
                    name = DiscordUtil.getFullUsername(user);
                } else {
                    name = getDiscordString();
                    if (name == null || name.isEmpty()) {
                        name = newDiscordId + "";
                    }
                }
                Locutus.imp().getDiscordDB().addUser(new PNWUser(data()._nationId(), newDiscordId, name));
                if (eventConsumer != null) {
                    eventConsumer.accept(new NationRegisterEvent(data()._nationId(), null, user, thisUserId == null));
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
        if (nation.getDiscord() != null && (this.data()._discordStr() == null || !this.data()._discordStr().equals(nation.getDiscord()))) {
            this.edit().setDiscordStr(nation.getDiscord());
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
            long currentActive = this.data()._lastActiveMs();
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
            if (eventConsumer != null) eventConsumer.accept(new NationChangeCitiesEvent(copyOriginal, this)); // Not useful, call the event when DBCity is created instead
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
                            if (currentProjects.size() >= 5) {
                                this.setProjectTimer(TimeUtil.getTurn() + GameTimers.PROJECT.getTurns());
                            }
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
            this.setSpies(nation.getSpies(), eventConsumer);
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
                    if (copyOriginal.data()._color() == NationColor.BEIGE) {
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
                this.markCitiesDirty();
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
                this.setEspionageFull(!nation.getEspionage_available());
                if (eventConsumer != null && this.getVm_turns() > 0) eventConsumer.accept(new NationChangeSpyFullEvent(copyOriginal, this));
            } else {
                this.setEspionageFull(!nation.getEspionage_available());
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
        if (nation.getGross_national_income() != null && (Math.round(this.data()._gni()) != Math.round(nation.getGross_national_income()))) {
            this.setGNI(nation.getGross_national_income());
            if (eventConsumer != null) eventConsumer.accept(new NationChangeGNIEvent(copyOriginal, this));
            dirty = true;
            this.markCitiesDirty();
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
        if (data()._allianceId() == 0 || data()._rank().id <= Rank.APPLICANT.id) return null;
        DBAlliancePosition pos = Locutus.imp().getNationDB().getPosition(data()._alliancePosition(), data()._allianceId(), false);
        if (pos == null) {
            long permission_bits = 0;
            switch (this.data()._rank()) {
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
            pos = new DBAlliancePosition(data()._alliancePosition(), data()._allianceId(), data()._rank().key, -1, -1, this.data()._rank(), permission_bits);
        }
        return pos;
    }

    public void setPosition(Rank rank) {
        this.edit().setRank(rank);
    }

    public void setAlliancePositionId(int position) {
        this.edit().setAlliancePosition(position);
    }

    @Command(desc = "Continent")
    public Continent getContinent() {
        return data()._continent();
    }

    public void setContinent(Continent continent) {
        this.edit().setContinent(continent);
    }

    @Command(desc = "Turn epoch when project timer expires")
    public Long getProjectAbsoluteTurn() {
        return data()._projectTimer();
    }

    public double getGroundStrength(boolean munitions, boolean enemyAc) {
        return data()._soldiers() * (munitions ? 1.7_5 : 1) + (data()._tanks() * 40) * (enemyAc ? 0.66 : 1);
    }

    @Command(desc = "Effective ground strength with munitions, enemy air control, and daily rebuy")
    public double getGroundStrength(boolean munitions, boolean enemyAc, @Default Double includeRebuy) {
        int soldiers = this.data()._soldiers();
        int tanks = this.data()._tanks();
        if (includeRebuy != null && includeRebuy > 0) {
            int barracks = Buildings.BARRACKS.cap(this::hasProject) * data()._cities();
            int soldierMax = Buildings.BARRACKS.getUnitCap() * barracks;
            int soldPerDay = barracks * Buildings.BARRACKS.getUnitDailyBuy();

            soldiers = Math.min(soldierMax, (int) (soldiers + soldPerDay * includeRebuy));

            int factories = Buildings.FACTORY.cap(this::hasProject) * data()._cities();
            int tankMax = Buildings.FACTORY.getUnitCap() * barracks;
            int tankPerDay = barracks * Buildings.FACTORY.getUnitDailyBuy();

            tanks = Math.min(tankMax, (int) (tanks + tankPerDay * includeRebuy));
        }
        return soldiers * (munitions ? 1.7_5 : 1) + (tanks * 40) * (enemyAc ? 0.66 : 1);
    }

    @Command(desc = "Get number of buildings")
    public double getAvgBuilding(Building building) {
        // TODO
        long total = 0;
        Map<Integer, DBCity> cities = _getCitiesV3();
        for (Map.Entry<Integer, DBCity> entry : cities.entrySet()) {
            DBCity city = entry.getValue();
            total += city.getBuilding(building);
        }
        return total / (double) cities.size();
    }

    @Command(desc = "Get number of barracks\n" +
            "Shorthand for getAvgBuilding(barracks)")
    public double getAvgBarracks() {
        return getAvgBuilding(Buildings.BARRACKS);
    }
    @Command(desc = "Get number of factories\n" +
            "Shorthand for getAvgBuilding(factory)")
    public double getAvgFactories() {
        return getAvgBuilding(Buildings.FACTORY);
    }
    @Command(desc = "Get number of hangars\n" +
            "Shorthand for getAvgBuilding(hangar)")
    public double getAvgHangars() {
        return getAvgBuilding(Buildings.HANGAR);
    }
    @Command(desc = "Get number of drydocks\n" +
            "Shorthand for getAvgBuilding(drydock)")
    public double getAvgDrydocks() {
        return getAvgBuilding(Buildings.DRYDOCK);
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
        DBNationCache cache = getCache(true);
        Int2ObjectArrayMap<byte[]> metaCache = cache.metaCache;
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
            Locutus.imp().getNationDB().setMeta(data()._nationId(), key, value);
        }
    }

    public ByteBuffer getMeta(NationMeta key) {
        DBNationCache cache = data()._cache();
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
        DBNationCache cache = data()._cache();
        if (cache != null) {
            if (cache.metaCache != null) {
                if (cache.metaCache.remove(key.ordinal()) != null) {
                    Locutus.imp().getNationDB().deleteMeta(data()._nationId(), key);
                }
            }
        }
    }

    @Command(desc = "Most recent spy count")
    public int getSpies() {
        return Math.max(data()._spies(), 0);
    }

    public void setSpies(int spies, Consumer<Event> eventConsumer) {
        getCache().processUnitChange(this, MilitaryUnit.SPIES, this.data()._spies(), spies);
        if (eventConsumer != null && this.data()._spies() != spies) {
            DBNation copyOriginal = copy();
            this.edit().setSpies(spies);
            Locutus.imp().getNationDB().saveNation(this);
            eventConsumer.accept(new NationChangeUnitEvent(copyOriginal, this, MilitaryUnit.SPIES));
        }
        this.edit().setSpies(spies);
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
        boolean includeGrants = db.getOrNull(GuildKey.MEMBER_CAN_WITHDRAW_IGNORES_GRANTS) == Boolean.FALSE;
        return getNetDeposits(db, tracked, useTaxBase, offset, includeGrants, updateThreshold, cutOff, priority);
    }

    public double[] getNetDeposits(GuildDB db, Set<Long> tracked, boolean useTaxBase, boolean offset, boolean includeGrants, long updateThreshold, long cutOff, boolean priority) throws IOException {
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

    @Command
    @RolePermission(Roles.ECON_STAFF)
    public double getNetDepositsConverted(@Me GuildDB db) throws IOException {
        return getNetDepositsConverted(db, -1);
    }

    public double getNetDepositsConverted(GuildDB db, long updateThreshold) throws IOException {
        return ResourceType.convertedTotal(getNetDeposits(db, updateThreshold, false));
    }

    @Command(desc = "Net nation deposits with their alliance guild divided by their city count")
    @RolePermission(Roles.ECON)
    public double getDepositValuePerCity(@Me GuildDB db) throws IOException {
        return getNetDepositsConverted(db) / data()._cities();
    }

    public List<Transaction2> getTransactions(boolean priority) {
        return getTransactions(0, priority);
    }

    public List<Transaction2> updateTransactions(boolean priority) {
        BankDB bankDb = Locutus.imp().getBankDB();
        if (Settings.USE_V2) {
            Locutus.imp().runEventsAsync(events -> bankDb.updateBankRecs(data()._nationId(), priority, events));
        } else if (Settings.INSTANCE.TASKS.BANK_RECORDS_INTERVAL_SECONDS > 0) {
            Locutus.imp().runEventsAsync(f -> bankDb.updateBankRecs(priority, f));
        } else {
            Locutus.imp().runEventsAsync(events -> bankDb.updateBankRecs(data()._nationId(), priority, events));
        }
        return Locutus.imp().getBankDB().getTransactionsByNation(data()._nationId());
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
        List<Long> logins = new ArrayList<>(Locutus.imp().getNationDB().getActivityByDay(data()._nationId(), TimeUtil.getTurn(getSnapshot()) - turns));
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

    @Command(desc = "Number of times since this nation's creation that they have been inactive for a specified number of days")
    public int inactivity_streak(int daysInactive, long checkPastXDays) {
        long turns = checkPastXDays * 12 + 11;
        List<Long> logins = new ArrayList<>(Locutus.imp().getNationDB().getActivityByDay(data()._nationId(), TimeUtil.getTurn(getSnapshot()) - turns));
        Collections.reverse(logins);

        int inactivityCount = 0;
        long last = 0;

        for (long day : logins) {
            long diff = last - day;
            if (diff > daysInactive) {
                inactivityCount++;
            }
            last = day;
        }

        return inactivityCount;
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
            if (transaction.receiver_id == data()._allianceId() && transaction.isReceiverAA()) max = Math.max(max, transaction.tx_datetime);
        }
        return max;
    }

    @Command(desc = "Days since they last withdrew from their own deposits")
    public double daysSinceLastSelfWithdrawal() {
        return (System.currentTimeMillis() - lastSelfWithdrawal()) / (double) TimeUnit.DAYS.toMillis(1);
    }

    @Command(desc = "Unix timestamp when they last withdrew from their own deposits")
    public long lastSelfWithdrawal() {
        if (getPositionEnum().id <= Rank.APPLICANT.id) return 0;
        List<Transaction2> transactions = getTransactions(Long.MAX_VALUE, false);
        long max = 0;
        for (Transaction2 transaction : transactions) {
            if (transaction.isSelfWithdrawal(this)) {
                max = Math.max(max, transaction.tx_datetime);
            }
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
                if (tx == null || tx.tx_datetime < lastActiveMs()) update = true;
                else if (System.currentTimeMillis() - tx.tx_datetime > updateThreshold) update = true;
            }
        }
        if (update) {
            return updateTransactions(priority);
        }
        return Locutus.imp().getBankDB().getTransactionsByNation(data()._nationId());
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
        DBNationCache cache = data()._cache();
        if (cache == null) return 0;
        return cache.lastCheckUnitMS;
    }

    public Long getTimeUpdatedSpies() {
        DBNationCache cache = data()._cache();
        if (cache != null) {
            return cache.lastCheckUnitMS;
        }
        return null;
    }

    public Integer updateSpies(PagePriority priority) {
        return data()._spies();
    }

    public Integer updateSpies(PagePriority priority, boolean update, boolean force) {
        return data()._spies();
    }

    public Integer updateSpies(PagePriority priority, boolean force) {
        return data()._spies();
    }

    public Integer updateSpies(PagePriority priority, int turns) {
        return data()._spies();
    }

    public static LoginFactorResult getLoginFactorPercents(DBNation nation) {
        List<LoginFactor> factors = DBNation.getLoginFactors(nation);

        long turnNow = TimeUtil.getTurn();
        int maxTurn = 30 * 12;
        int candidateTurnInactive = (int) (turnNow - TimeUtil.getTurn(nation.lastActiveMs()));

        Set<DBNation> nations1dInactive = Locutus.imp().getNationDB().getNationsMatching(f -> f.active_m() >= 1440 && f.getVm_turns() == 0 && f.active_m() <= TimeUnit.DAYS.toMinutes(30));
        NationScoreMap<DBNation> inactiveByTurn = new NationScoreMap<DBNation>(nations1dInactive, f -> {
            return (double) (turnNow - TimeUtil.getTurn(f.lastActiveMs()));
        }, 1, 1);

        LoginFactorResult result = new LoginFactorResult();
        for (LoginFactor factor : factors) {
            Predicate<DBNation> matches = f -> factor.matches(factor.get(nation), factor.get(f));
            BiFunction<Integer, Integer, Integer> sumFactor = inactiveByTurn.getSummedFunction(matches);

            int numCandidateActivity = sumFactor.apply(Math.min(maxTurn - 23, candidateTurnInactive), Math.min(maxTurn, candidateTurnInactive + 24));
            int numInactive = Math.max(1, sumFactor.apply(14 * 12, 30 * 12) / (30 - 14));

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
                DBWar lastWar = Locutus.imp().getWarDb().getLastDefensiveWar(f.data()._nationId());
                if (lastWar != null) {
                    long warDiff = f.lastActiveMs() - TimeUnit.DAYS.toMillis(10);
                    if (lastWar.getDate() > warDiff) {
                        if (lastWar.getStatus() == WarStatus.PEACE || lastWar.getStatus() == WarStatus.DEFENDER_VICTORY) {
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
            body.append("<a href=\"" + Settings.PNW_URL() + "/nation/objectives/\" class=\"btn btn-warning\"><i class=\"fas fa-book\" aria-hidden=\"true\"></i> Open Objectives</a>");
            body.append("<p>The tutorial runs you through some of the basics, and gives you some cash right away. Let me know if you need any assistance with them:</p>");
            body.append("<hr><br>");
        }
        if (getOff() < getMaxOff()) {
//                String raidUrl = "https://politicsandwar.com/index.php?id=15&keyword=" + current.getScore() + "&cat=war_range&ob=date&od=DESC&maximum=50&minimum=0&search=Go&beige=true&vmode=false&aligned=true&openslots=true";
//                body.append("The quickest way to make money for new nations is to raid inactive nations (rather than waiting on your cities to generate revenue)\n");
//                body.append(" - You can use the nation search to find enemies: " + raidUrl + " (enter your nation's score, currently " + current.getScore() + " as the war range search term)\n");

            body.append("<p>The quickest way to make money is to raid:</p>");
            body.append("<ul>" +
                    "<li>Go to the <a href=\"" + Settings.PNW_URL() + "/nation/war/\">War Page</a> and click `Find Nations in War Range`</li>" +
                    "<li>Attack up to 5 inactive nations (purple diamond next to them)</li>" +
                    "<li>Inactive people don't fight back</li>" +
                    "<li>take a portion of their resources when defeated</li>" +
                    "</ul>");
            body.append("<a href=\"" + Settings.PNW_URL() + "/index.php?id=15&keyword=" + data()._score() + "&cat=war_range&ob=cities&od=ASC&maximum=50&minimum=0&search=Go&beige=true&vmode=false&aligned=true&openslots=true\" class=\"btn btn-warning\"><i class=\"fas fa-chess-rook\" aria-hidden=\"true\"></i> Find War Targets</a>");
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
            String url = Settings.PNW_URL() + "/city/id=" + entry.getKey();
            JavaCity city = entry.getValue();

            List<String> cityCompliance = new ArrayList<>();

            if (!city.getPowered()) {
                cityCompliance.add("Is not powered. Make sure you have enough power plants and fuel. You can buy fuel from the trade page.");
            }
            if (city.getBuilding(Buildings.FARM) != 0) {
                cityCompliance.add("Has farms, which are not very profitable. It is better buying food from the trade page");
            }
            if (city.getBuilding(Buildings.BARRACKS) != 5) {
                cityCompliance.add("Doesn't have 5 barracks. Soldiers are a good cheap unit that are useful for raiding and protecting your nation.");
            }
            if (city.getBuilding(Buildings.NUCLEAR_POWER) == 0) {
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
        return new KeyValue<>(subject, content);
    }

    public Map<ResourceType, Double> getResourcesNeeded(Map<ResourceType, Double> stockpile, double days, boolean force) {
        Map<Integer, JavaCity> cityMap = getCityMap(force);
        Map<Integer, JavaCity> citiesNoRaws = new HashMap<>();

        for (Map.Entry<Integer, JavaCity> cityEntry : cityMap.entrySet()) {
            JavaCity city = new JavaCity(cityEntry.getValue());
            city.setBuilding(Buildings.FARM, 0);
            for (Building building : Buildings.values()) {
                if (building instanceof ResourceBuilding) {
                    ResourceBuilding rssBuilding = (ResourceBuilding) building;
                    ResourceType rss = rssBuilding.getResourceProduced();
                    if (rss.isRaw()) {
                        city.setBuilding(building, 0);
                    }
                }
            }
            citiesNoRaws.put(cityEntry.getKey(), city);
        }

        double[] daily = PW.getRevenue(null, 12, this, cityMap.values(), true, true, true, false, false, 0d);
        double[] turn = PW.getRevenue(null,  1, this, citiesNoRaws.values(), true, true, true, false, false, 0d);
        double[] turn2 = PW.getRevenue(null,  1, this, citiesNoRaws.values(), true, true, true, true, false, 0d);
        turn[ResourceType.MONEY.ordinal()] = Math.min(turn[ResourceType.MONEY.ordinal()], turn2[ResourceType.MONEY.ordinal()]);
        turn[ResourceType.FOOD.ordinal()] = Math.min(turn[ResourceType.FOOD.ordinal()], turn2[ResourceType.FOOD.ordinal()]);

//        turn[0] = Math.min(daily[0], turn[0]);

        Map<ResourceType, Double> profit = ResourceType.resourcesToMap(daily);
        Map<ResourceType, Double> profitDays = PW.multiply(profit, (double) days);
        Map<ResourceType, Double> toSendNation = new HashMap<>();
        Map<ResourceType, Double> minResources = ResourceType.resourcesToMap(turn);
        for (ResourceType type : ResourceType.values) {
            double current = stockpile.getOrDefault(type, 0d);
            double required = Math.min(minResources.getOrDefault(type, 0d), profitDays.getOrDefault(type, 0d));
            double toSend = current + required;
            if (toSend < 0) toSendNation.put(type, -toSend);
        }
        return toSendNation;
    }

    @Command(desc = "Get the in-game resources a member nation has")
    @RolePermission(value = {Roles.ECON, Roles.INTERNAL_AFFAIRS, Roles.ECON_STAFF, Roles.MILCOM}, any = true)
    public Map<ResourceType, Double> getStockpile(ValueStore store, @Me GuildDB db) {
        if (!db.isAllianceId(data()._allianceId())) {
            throw new IllegalArgumentException("Nation " + data()._nation() + " is not member of " + db.getGuild() + " alliance: " + db.getAllianceIds());
        }
        if (getPositionEnum().id <= Rank.APPLICANT.id) {
            throw new IllegalArgumentException("Nation " + data()._nation() + " is not a member");
        }
        ScopedPlaceholderCache<DBNation> scoped = PlaceholderCache.getScoped(store, DBNation.class, "getStockpile");
        return scoped.getMap(this,
                (ThrowingFunction<List<DBNation>, Map<DBNation, Map<ResourceType, Double>>>)
                        f -> db.getAllianceList().subList(f).getMemberStockpile(),
                DBNation::getStockpile);
    }

    public Map<ResourceType, Double> getStockpile() {
        ApiKeyPool pool;
        ApiKeyPool.ApiKey myKey = getApiKey(false);

        DBAlliance alliance = getAlliance();
        if (myKey != null) {
            pool  = ApiKeyPool.create(myKey);
        } else if (getPositionEnum().id <= Rank.APPLICANT.id || alliance == null) {
            throw new IllegalArgumentException("Nation " + data()._nation() + " is not member in an alliance");
        } else {
            pool = alliance.getApiKeys(AlliancePermission.SEE_SPIES);
            if (pool == null) {
                throw new IllegalArgumentException("No api key found. Please use" + CM.credentials.addApiKey.cmd.toSlashMention());
            }
        }

        double[] stockpile = new PoliticsAndWarV3(pool).getStockPile(f -> f.setId(List.of(data()._nationId()))).get(data()._nationId());
        return stockpile == null ? null : ResourceType.resourcesToMap(stockpile);
    }

    @Command(desc = "Get nation deposits")
    @RolePermission(Roles.ECON)
    public Map<ResourceType, Double> getDeposits(ValueStore store, @Me GuildDB db, @Default @Timestamp Long start, @Default @Timestamp Long end, @NoFormat @Default Predicate<Transaction2> filter,
                                                 @Arg("""
                                                         use 0/0 as the tax base
                                                         i.e. All taxes included in deposits
                                                         The default internal taxrate is 100/100 (all taxes excluded)""") @Switch("b") boolean ignoreBaseTaxrate,
                                                 @Arg("Do NOT include any manual deposit offesets") @Switch("o") boolean ignoreOffsets,
                                                 @Switch("e") boolean includeExpired,
                                                 @Switch("i") boolean includeIgnored,
                                                 @Switch("d") Set<DepositType> excludeTypes
    ) {
        if (start == null) start = 0L;
        if (end == null) end = Long.MAX_VALUE;
        ScopedPlaceholderCache<DBNation> scoped = PlaceholderCache.getScoped(store, DBNation.class, "getDeposits");
        Set<Long> tracked = scoped.getGlobal(db::getTrackedBanks);
        List<Map.Entry<Integer, Transaction2>> transactions = getTransactions(db, tracked, !ignoreOffsets, !ignoreOffsets, -1L, start, false);
        if (filter != null) {
            transactions.removeIf(f -> !filter.test(f.getValue()));
        }
        Map<DepositType, double[]> sum = PW.sumNationTransactions(this, db, null, transactions, includeExpired, includeIgnored, filter);
        double[] total = ResourceType.getBuffer();
        for (Map.Entry<DepositType, double[]> entry : sum.entrySet()) {
            if (excludeTypes != null && excludeTypes.contains(entry.getKey())) continue;
            total = ResourceType.add(total, entry.getValue());
        }
        return ResourceType.resourcesToMap(total);
    }

    public ApiKeyPool.ApiKey getApiKey(boolean dummy) {
        return Locutus.imp().getDiscordDB().getApiKey(data()._nationId());
    }

    @Command(desc = "Check if the nation has all permissions")
    public boolean hasAllPermission(Set<AlliancePermission> permissions) {
        if (data()._rank().id >= Rank.HEIR.id) return true;
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
        if (data()._rank().id >= Rank.HEIR.id) return true;
        if (permissions == null || permissions.isEmpty()) return true;
        DBAlliancePosition position = getAlliancePosition();
        if (position == null) return false;
        for (AlliancePermission perm : permissions) {
            if (position.hasPermission(perm)) return true;
        }
        return false;
    }

    @Deprecated // Not working any more since you cant do it with an api key, only login
    public String commend(boolean isCommend) throws IOException {
        ApiKeyPool.ApiKey key = getApiKey(true);
        String url = Settings.PNW_URL() + "/api/denouncements.php";
        Map<String, String> post = new HashMap<>();

        if (isCommend) {
            post.put("action", "commendment");
        } else {
            post.put("action", "denouncement");
        }
        post.put("account_id", Locutus.loader().getNationId() + "");
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
        List<Map.Entry<Integer, Transaction2>> transactions = getTransactions(db, tracked, useTaxBase, offset, updateThreshold, cutOff, priority);
        Map<DepositType, double[]> sum = PW.sumNationTransactions(this, db, tracked, transactions, forceIncludeExpired, forceIncludeIgnored, filter);
        return sum;
    }

    public List<Map.Entry<Integer, Transaction2>> getTransactions(GuildDB db, Set<Long> tracked, boolean useTaxBase, boolean offset, long updateThreshold, long cutOff, boolean priority) {
        return getTransactions(db, tracked, true, useTaxBase, offset, updateThreshold, cutOff, priority);
    }

    public List<Map.Entry<Integer, Transaction2>> getTransactions(GuildDB db, Set<Long> tracked, boolean includeTaxes, boolean useTaxBase, boolean offset, long updateThreshold, long cutOff, boolean priority) {
        if (tracked == null) {
            tracked = db.getTrackedBanks();
        }

        List<Transaction2> transactions = new ArrayList<>();
        if (offset) {
            List<Transaction2> offsets = db.getDepositOffsetTransactions(getNation_id());
            transactions.addAll(offsets);
        }


        int[] defTaxBase = new int[]{100, 100};
        if (useTaxBase) {
            TaxRate defTaxBaseTmp = db.getOrNull(GuildKey.TAX_BASE);
            if (defTaxBaseTmp != null) defTaxBase = new int[]{defTaxBaseTmp.money, defTaxBaseTmp.resources};
        } else {
            defTaxBase = new int[]{0, 0};
        }
        boolean includeNoInternal = defTaxBase[0] == 100 && defTaxBase[1] == 100;
        boolean includeMaxInternal = false;

        Set<Integer> finalTracked = tracked.stream().filter(f -> f <= Integer.MAX_VALUE).map(Long::intValue).collect(Collectors.toSet());
        List<TaxDeposit> taxes = includeTaxes ? Locutus.imp().getBankDB().getTaxesPaid(getNation_id(), finalTracked, includeNoInternal, includeMaxInternal, cutOff) : new ArrayList<>();

        for (TaxDeposit deposit : taxes) {
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
            if (record.sender_id == data()._nationId() && record.sender_type == 1) {
                sign = 1;
            } else {
                sign = -1;
            }

            result.add(new KeyValue<>(sign, record));
        }

        return result;
    }

    @Command(desc = "Nation ID")
    public int getNation_id() {
        return data()._nationId();
    }

    public void setNation_id(int nation_id) {
        this.edit().setNation_id(nation_id);
    }

    @Command(desc = "Nation Name")
    public String getNation() {
        return data()._nation();
    }

    public void setNation(String nation) {
        this.edit().setNation(nation);
    }

    @Command(desc = "Leader name")
    public String getLeader() {
        return data()._leader();
    }

    public void setLeader(String leader) {
        this.edit().setLeader(leader);
    }

    @Command(desc = "Alliance ID")
    public int getAlliance_id() {
        return data()._allianceId();
    }

    public void setAlliance_id(int alliance_id) {
        this.edit().setAlliance_id(alliance_id);
    }

    @Command(desc = "Alliance Name")
    public String getAllianceName() {
        if (data()._allianceId() == 0) return "AA:0";
        return Locutus.imp().getNationDB().getAllianceName(data()._allianceId());
    }

    @Command(desc = "The alliance class")
    public DBAlliance getAlliance() {
        return getAlliance(true);
    }

    public DBAlliance getAlliance(boolean createIfNotExist) {
        if (data()._allianceId() == 0) return null;
        if (createIfNotExist) {
            return Locutus.imp().getNationDB().getOrCreateAlliance(data()._allianceId());
        } else {
            return Locutus.imp().getNationDB().getAlliance(data()._allianceId());
        }
    }

    @Command(desc = "Minutes since last active in-game")
    public int getActive_m(@Default @Timestamp Long time) {
        if (time == null) time = getSnapshot();
        long now = System.currentTimeMillis();
        if (time != null) {
            if (time < now - TimeUnit.MINUTES.toMillis(15)) {
                now = time;
            } else {
                time = null;
            }
        }
        return (int) TimeUnit.MILLISECONDS.toMinutes(now - (time == null ? lastActiveMs() : lastActiveMs(time)));
    }

    public void setActive_m(int active_m) {
        this.edit().setLast_active(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(active_m));
    }

    @Command(desc = "Nation Score (ns)")
    public double getScore() {
        return data()._score();
    }

    public double estimateScore(MMRDouble mmr, Double infra, Integer projects, Integer cities) {
        return PW.estimateScore(Locutus.imp().getNationDB(), this, mmr, infra, projects, cities);
    }

    public void setScore(double score) {
        this.edit().setScore(score);
    }

    @Command(desc = "Total infra in all cities")
    public double getInfra() {
        double total = 0;
        for (DBCity city : _getCitiesV3().values()) {
            total += city.getInfra();
        }
        return total;
    }


    @Command(desc = "Total population in all cities")
    public int getPopulation() {
        int total = 0;
        for (JavaCity city : getCityMap(false).values()) {
            total += city.calcPopulation(this::hasProject);
        }
        return total;
    }

    /*
    A map of resource projects, and whether they are producing it
     */
    public Map<Project, Boolean> resourcesProducedProjects() {
        Map<Project, Boolean> manufacturing = new LinkedHashMap<>();
        for (Map.Entry<Integer, JavaCity> entry : getCityMap(false).entrySet()) {
            JavaCity city = entry.getValue();
            if (city.getBuilding(Buildings.GAS_REFINERY) > 0)
                manufacturing.put(Projects.EMERGENCY_GASOLINE_RESERVE, hasProject(Projects.EMERGENCY_GASOLINE_RESERVE));
            if (city.getBuilding(Buildings.STEEL_MILL) > 0)
                manufacturing.put(Projects.IRON_WORKS, hasProject(Projects.IRON_WORKS));
            if (city.getBuilding(Buildings.ALUMINUM_REFINERY) > 0)
                manufacturing.put(Projects.BAUXITEWORKS, hasProject(Projects.BAUXITEWORKS));
            if (city.getBuilding(Buildings.MUNITIONS_FACTORY) > 0)
                manufacturing.put(Projects.ARMS_STOCKPILE, hasProject(Projects.ARMS_STOCKPILE));
            if (city.getBuilding(Buildings.FARM) > 0)
                manufacturing.put(Projects.MASS_IRRIGATION, hasProject(Projects.MASS_IRRIGATION));
        }
        return manufacturing;
    }
    @Command(desc = "Number of built cities")
    public int getCities() {
        return data()._cities();
    }

    @Command(desc = "Number of cities built since date")
    public int getCitiesSince(@Timestamp long time) {
        return (int) _getCitiesV3().values().stream().filter(city -> city.getCreated() > time).count();
    }

    @Command(desc = "Number of cities at a date")
    public int getCitiesAt(@Timestamp long time) {
        return data()._cities() - getCitiesSince(time);
    }
    @Command(desc = "Cost of cities built since a date")
    public double getCityCostSince(@Timestamp long time, boolean allowProjects) {
        int numBuilt = getCitiesSince(time);
        int from = data()._cities() - numBuilt;
        return PW.City.cityCost(allowProjects ? this : null, from, data()._cities());
    }

    @Command(desc = "Cost of cities built since a date divided by the current city count")
    public double getCityCostPerCitySince(@Timestamp long time, boolean allowProjects) {
        int numBuilt = getCitiesSince(time);
        int from = data()._cities() - numBuilt;
        return numBuilt > 0 ? PW.City.cityCost(allowProjects ? this : null, from, data()._cities()) / numBuilt : 0;
    }

    /**
     *
     * @return
     */
    @Command(desc="Get the min city count of the first matching city range\n" +
            "c1-10, c11-15")
    public int getCityGroup(CityRanges ranges) {
        for (Map.Entry<Integer, Integer> range : ranges.getRanges()) {
            if (data()._cities() >= range.getKey() && data()._cities() <= range.getValue()) return range.getKey();
        }
        return -1;
    }

    public void setCities(int cities) {
        if (((cities > 20 && cities > this.data()._cities()) || (cities <= 20 && cities < this.data()._cities())) && this.data()._cities() != 0) {
            this.edit().setCityTimer(TimeUtil.getTurn() + GameTimers.CITY.getTurns());
        }
        double costReduction = data()._costReduction();
        if (costReduction > 0) {
            int currCity = this.data()._cities();
            if (cities > currCity) {
                double cost = PW.City.cityCost(this, currCity, cities);
                this.edit().setCostReduction(Math.max(0, costReduction - cost));
            }
        }
        this.edit().setCities(cities);
    }

    @Command(desc="average infrastructure in cities")
    public double getAvg_infra() {
        double total = 0;
        Collection<DBCity> cities = _getCitiesV3().values();
        if (cities.isEmpty()) return 0;

        for (DBCity city : cities) {
            total += city.getInfra();
        }
        return total / cities.size();
    }

    @Command(desc = "War policy")
    public WarPolicy getWarPolicy() {
        return data()._warPolicy();
    }

    @Command(desc = "Domestic policy")
    public DomesticPolicy getDomesticPolicy() {
        if (data()._domesticPolicy() == null) return DomesticPolicy.MANIFEST_DESTINY;
        return data()._domesticPolicy();
    }

    public void setWarPolicy(WarPolicy policy) {
        if (policy != this.data()._warPolicy() && this.data()._warPolicy() != null) {
            this.edit().setWarPolicyTimer(TimeUtil.getTurn() + GameTimers.WAR_POLICY.getTurns());
        }
        this.edit().setWar_policy(policy);
    }

    public void setDomesticPolicy(DomesticPolicy policy) {
        if (policy != this.data()._domesticPolicy() && this.data()._domesticPolicy() != null) {
            this.edit().setDomesticPolicyTimer(TimeUtil.getTurn() + GameTimers.DOMESTIC_POLICY.getTurns());
        }
        this.edit().setDomestic_policy(policy);
    }

    @Command(desc = "Number of soldiers")
    public int getSoldiers() {
        return data()._soldiers();
    }

    public void setSoldiers(int soldiers) {
        getCache().processUnitChange(this, MilitaryUnit.SOLDIER, this.data()._soldiers(), soldiers);
        this.edit().setSoldiers(soldiers);
    }

    @Command(desc = "Number of tanks")
    public int getTanks() {
        return data()._tanks();
    }

    public void setTanks(int tanks) {
        getCache().processUnitChange(this, MilitaryUnit.TANK, this.data()._tanks(), tanks);
        this.edit().setTanks(tanks);
    }

    @Command(desc = "Number of aircraft")
    public int getAircraft() {
        return data()._aircraft();
    }

    @Command(desc = "Maximum spies a nation can have")
    public int getSpyCap() {
        return hasProject(Projects.INTELLIGENCE_AGENCY) ? 60 : 50;
    }

    @Command(desc = "Number of spies this nation can buy before reaching cap")
    public int getSpyCapLeft() {
        return getSpyCap() - getSpies();
    }

    @Command(desc = "Decimal ratio of aircraft a nation has out of their maximum (between 0 and 1)")
    public double getAircraftPct() {
        return getAircraft() / (double) (Math.max(1, Buildings.HANGAR.getUnitCap() * Buildings.HANGAR.cap(this::hasProject) * getCities()));
    }

    @Command(desc = "Decimal ratio of tanks a nation has out of their maximum (between 0 and 1)")
    public double getTankPct() {
        return getTanks() / (double) (Math.max(1, Buildings.FACTORY.getUnitCap() * Buildings.FACTORY.cap(this::hasProject) * getCities()));
    }

    @Command(desc = "Decimal ratio of soldiers a nation has out of their maximum (between 0 and 1)")
    public double getSoldierPct() {
        return getSoldiers() / (double) (Math.max(1, Buildings.BARRACKS.getUnitCap() * Buildings.BARRACKS.cap(this::hasProject) * getCities()));
    }

    @Command(desc = "Decimal ratio of ships a nation has out of their maximum (between 0 and 1)")
    public double getShipPct() {
        return getShips() / (double) (Math.max(1, Buildings.DRYDOCK.getUnitCap() * Buildings.DRYDOCK.cap(this::hasProject) * getCities()));
    }

    public void setAircraft(int aircraft) {
        getCache().processUnitChange(this, MilitaryUnit.AIRCRAFT, this.data()._aircraft(), aircraft);
        this.edit().setAircraft(aircraft);
    }

    @Command(desc = "Number of navy ships")
    public int getShips() {
        return data()._ships();
    }

    public void setShips(int ships) {
        getCache().processUnitChange(this, MilitaryUnit.SHIP, this.data()._ships(), ships);
        this.edit().setShips(ships);
    }

    @Command(desc = "Number of missiles")
    public int getMissiles() {
        return data()._missiles();
    }

    public void setMissiles(int missiles) {
        getCache().processUnitChange(this, MilitaryUnit.MISSILE, this.data()._missiles(), missiles);
        this.edit().setMissiles(missiles);
    }

    @Command(desc = "Number of nuclear weapons (nukes)")
    public int getNukes() {
        return data()._nukes();
    }

    public void setNukes(int nukes) {
        getCache().processUnitChange(this, MilitaryUnit.NUKE, this.data()._nukes(), nukes);
        this.edit().setNukes(nukes);
    }

    @Command(desc = "Number of turns since entering Vacation Mode (VM)")
    public int getVacationTurnsElapsed() {
        long turn = TimeUtil.getTurn();
        if (data()._enteredVm() > 0 && data()._enteredVm() < turn) {
            return (int) (turn - data()._enteredVm());
        }
        return 0;
    }

    @Command(desc = "Number of turns in Vacation Mode (VM)")
    public int getVm_turns() {
        if (data()._leavingVm() == 0) return 0;
        long currentTurn = TimeUtil.getTurn(getSnapshot());
        if (data()._leavingVm() <= currentTurn) return 0;
        return (int) (data()._leavingVm() - currentTurn);
    }

    @Command(desc = "Absolute turn number when entering Vacation Mode (VM)")
    public long getEntered_vm() {
        return data()._enteredVm();
    }

    @Command(desc = "Absolute turn number when leaving Vacation Mode (VM)")
    public long getLeaving_vm() {
        return data()._leavingVm();
    }

    public void setLeaving_vm(long leaving_vm) {
        long currentTurn = TimeUtil.getTurn(getSnapshot());
        if (this.data()._leavingVm() <= currentTurn) {
            this.edit().setEntered_vm(currentTurn);
        }
        this.edit().setLeaving_vm(leaving_vm);
    }

    public void setLeaving_vmRaw(long leavingVm) {
        this.edit().setLeaving_vm(leavingVm);
    }

    public void setEntered_vm(long entered_vm) {
        this.edit().setEntered_vm(entered_vm);
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
        return data()._color();
    }

    @Command(desc = "If nation color matches the alliance color")
    public boolean isAllianceColor() {
        DBAlliance alliance = getAlliance();
        if (alliance == null) return false;
        return alliance.getColor() == getColor();
    }

    public void setColor(NationColor color) {
//        if (color != NationColor.GRAY && color != NationColor.BEIGE)
        if (color != this.data()._color()) {
            this.edit().setColorTimer(TimeUtil.getTurn() + GameTimers.COLOR.getTurns());
            if (color != NationColor.BEIGE) {
                this.edit().setBeigeTimer(0L);
            }
        }
        this.edit().setColor(color);
    }

    @Command(desc = "Number of active offensive wars")
    public int getOff() {
        return (int) getActiveWars().stream().filter(f -> f.getAttacker_id() == data()._nationId()).count();
    }

    @Command(desc = "All time offensive wars involved in")
    public int getAllTimeOffensiveWars() {
        return (int) getWars().stream().filter(f -> f.getAttacker_id() == data()._nationId()).count();
    }

    @Command(desc = "All time defensive wars involved in")
    public int getAllTimeDefensiveWars() {
        return (int) getWars().stream().filter(f -> f.getDefender_id() == data()._nationId()).count();
    }

    @Command
    public int[] getAllTimeOffDefWars() {
        Set<DBWar> wars = getWars();
        int off = (int) wars.stream().filter(f -> f.getAttacker_id() == data()._nationId()).count();
        int def = (int) wars.stream().filter(f -> f.getDefender_id() == data()._nationId()).count();
        return new int[]{off, def};
    }

    @Command(desc = "All time wars involved in")
    public int getAllTimeWars() {
        return getWars().size();
    }

    @Command(desc = "All time wars against active nations")
    public int getNumWarsAgainstActives() {
        if (getNumWars() == 0) return 0;
        int total = 0;
        for (DBWar war : getActiveWars()) {
            DBNation other = war.getNation(!war.isAttacker(this));
            if (other != null && other.active_m() < 4880) total++;
        }
        return total;
    }

    @Command(desc = "Number of active offensive and defensive wars")
    public int getNumWars() {
        return getActiveWars().size();
    }

    @Command(desc = "Number of offensive and defensive wars since date")
    public int getNumWarsSince(long date) {
        return Locutus.imp().getWarDb().countWarsByNation(data()._nationId(), date, getSnapshot());
    }

    @Command(desc = "Number of offensive wars since date")
    public int getNumOffWarsSince(long date) {
        return Locutus.imp().getWarDb().countOffWarsByNation(data()._nationId(), date, getSnapshot() == null ? Long.MAX_VALUE : getSnapshot());
    }

    @Command(desc = "Number of defensive wars since date")
    public int getNumDefWarsSince(long date) {
        return Locutus.imp().getWarDb().countDefWarsByNation(data()._nationId(), date, getSnapshot() == null ? Long.MAX_VALUE : getSnapshot());
    }

    @Command(desc = "Number of active defensive wars")
    public int getDef() {
        return (int) getActiveWars().stream().filter(f -> f.getDefender_id() == data()._nationId()).count();
    }

    @Command(desc = "Unix timestamp of date created")
    public long getDate() {
        return data()._date();
    }

    public void setDate(long date) {
        this.edit().setDate(date);
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
                setSpies(amt, null);
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
        return data()._nation();
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
                .map(dbWar -> dbWar.getAttacker_id() == getNation_id() ? dbWar.getDefender_id() : dbWar.getAttacker_id())
                .collect(Collectors.toSet());
    }

    @Command(desc = "If a specified nation is within this nations espionage range")
    public boolean isInSpyRange(DBNation other) {
        return SpyCount.isInScoreRange(getScore(), other.getScore());
    }

    @Command(desc = "If they have undefined military values")
    public boolean hasUnsetMil() {
        return data()._soldiers() == -1;
    }

    public int active_m() {
        long now = getSnapshot() == null ? System.currentTimeMillis() : getSnapshot();
        return (int) TimeUnit.MILLISECONDS.toMinutes(now - lastActiveMs());
    }

    @Command(desc = "Number of turns this nation has been inactive for")
    public long getInactiveTurns() {
        return TimeUtil.getTurn() - TimeUtil.getTurn(lastActiveMs());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof Integer i) {
            return i.equals(data()._nationId());
        }
        if (o instanceof ArrayUtil.IntKey key) {
            return key.key == data()._nationId();
        }
        if (o == null || getClass() != o.getClass()) return false;

        DBNation nation = (DBNation) o;

        return data()._nationId() == nation.data()._nationId();
    }

    @Override
    public int hashCode() {
        return data()._nationId();
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
            response.append("<" + Settings.PNW_URL() + "/nation/war/declare/id=" + getNation_id() + ">");
        } else {
            response.append("<" + Settings.PNW_URL() + "/nation/id=" + getNation_id() + ">");
        }
        String beigeStr = null;
        if (data()._color() == NationColor.BEIGE) {
            int turns = getBeigeTurns();
            long diff = TimeUnit.MILLISECONDS.toMinutes(TimeUtil.getTimeFromTurn(TimeUtil.getTurn() + turns) - System.currentTimeMillis());
            beigeStr = TimeUtil.secToTime(TimeUnit.MINUTES, diff);
        }
        int vm = getVm_turns();

        response.append(" | " + String.format("%16s", getNation()))
                .append(" | " + String.format("%16s", getAllianceName()))
                .append(data()._allianceId() != 0 && getPositionEnum() == Rank.APPLICANT ? " applicant" : "")
                .append(data()._color() == NationColor.BEIGE ? " beige:" + beigeStr : "")
                .append(vm > 0 ? " vm=" + TimeUtil.secToTime(TimeUnit.HOURS, vm * 2) : "")
                .append("\n```")
                .append(String.format("%5s", (int) getScore())).append(" ns").append(" | ")
                .append(String.format("%10s", TimeUtil.secToTime(TimeUnit.MINUTES, active_m()))).append(" \uD83D\uDD52").append(" | ")
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
        return (int) TimeUnit.MILLISECONDS.toDays((getSnapshot() != null ? getSnapshot() : System.currentTimeMillis()) - data()._date());
    }

    @Command(desc = "Days since last city")
    public int getDaysSinceLastCity() {
        int min = getAgeDays();
        for (Map.Entry<Integer, JavaCity> entry : getCityMap(false).entrySet()) {
            min = Math.min(min, entry.getValue().getAgeDays());
        }
        return min;
    }

    // Mock
    public Map<Integer, DBCity> _getCitiesV3() {
        return Locutus.imp().getNationDB().getCitiesV3(data()._nationId());
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
        if (data()._nationId() > 0 && getSnapshot() == null) {
            if (updateNewCities && cityObj.size() != data()._cities()) force = true;
            if (updateIfOutdated && estimateScore() != this.data()._score()) force = true;
            if (force) {
                Locutus.imp().runEventsAsync(events -> Locutus.imp().getNationDB().updateCitiesOfNations(Collections.singleton(data()._nationId()), true,true, events));
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
        double[] revenue = getRevenue(12, true, false, true, updateNewCities, false, false, 0d, force);
        double consumeCost = 0;
        double taxable = 0;
        for (ResourceType type : ResourceType.values) {
            double value = revenue[type.ordinal()];
            if (value < 0) {
                consumeCost += ResourceType.convertedTotal(type, -value);
            } else {
                taxable += -ResourceType.convertedTotal(type, -value);
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
        return getRevenue(turns, true, true, true, true, false, false, getTreasureBonusPct(), false);
    }

    @Command(desc = "Treasure bonus decimal percent")
    public double getTreasureBonusPct() {
        if (data()._allianceId() == 0 || getPositionEnum().id < Rank.APPLICANT.id || getVm_turns() > 0) return 0;
        DBAlliance aa = getAlliance();
        int treasures = aa.getNumTreasures();
        Set<DBTreasure> natTreasures = getTreasures();
        return ((treasures == 0 ? 0 : Math.sqrt(treasures * 4)) + natTreasures.stream().mapToDouble(DBTreasure::getBonus).sum()) * 0.01;
    }

    public double[] getRevenue(int turns, boolean cities, boolean militaryUpkeep, boolean tradeBonus, boolean bonus, boolean noFood, boolean noPower, double treasureBonus, boolean force) {
        return getRevenue(turns, cities, militaryUpkeep, tradeBonus, bonus, noFood, noPower, treasureBonus, null, null, force);
    }

    public double[] getRevenue(int turns, boolean cities, boolean militaryUpkeep, boolean tradeBonus, boolean bonus, boolean noFood, boolean noPower, double treasureBonus, Double forceRads, Boolean forceAtWar, boolean force) {
        Map<Integer, JavaCity> cityMap = cities ? getCityMap(force, force, false) : new HashMap<>();
        double rads = forceRads != null ? forceRads : getRads();
        boolean atWar = forceAtWar != null ? forceAtWar : getNumWars() > 0;
        double[] revenue = PW.getRevenue(null, turns, -1L, this, cityMap.values(), militaryUpkeep, tradeBonus, bonus, noFood, noPower, rads, atWar, treasureBonus);
        return revenue;
    }

    public String fetchUsername() throws IOException {
        List<Nation> discord = Locutus.imp().getApiPool().fetchNations(true, f -> f.setId(List.of(data()._nationId())), NationResponseProjection::discord);
        if (discord.isEmpty()) return null;
        return discord.get(0).getDiscord();
    }

    @Command(desc = "Get the number of wars with nations matching a filter")
    public int getActiveWarsWith(@NoFormat NationFilter filter) {
        int count = 0;
        for (DBWar war : getActiveWars()) {
            DBNation other = war.getNation(!war.isAttacker(this));
            if (other != null && filter.test(other)) count++;
        }
        return count;
    }

    @Command(desc = "Total stockpile value based on last war loss or espionage")
    public double getBeigeLootTotal() {
        LootEntry loot = getBeigeLoot();
        return loot == null ? 0 : ResourceType.convertedTotal(loot.getTotal_rss());
    }

    @Command(desc = "Estimated loot value including aliance bank loot when defeated in a raid war based on last war loss or espionage")
    public double lootTotal() {
        double[] knownResources = new double[ResourceType.values.length];
        double[] buffer = new double[knownResources.length];
        LootEntry loot = getBeigeLoot();
        double convertedTotal = loot == null ? 0 : loot.convertedTotal() * 0.14 * lootModifier();

        if (getPosition() > 1 && data()._allianceId() != 0) {
            Map<ResourceType, Double> aaLoot = Locutus.imp().getWarDb().getAllianceBankEstimate(getAlliance_id(), getScore());
            convertedTotal += ResourceType.convertedTotal(aaLoot);
        }
        return convertedTotal;
    }

    @Command(desc = "Cost of the next city, optionally accounting for cost reduction policies")
    public double getNextCityCost(@Default Boolean costReduction) {
        if (costReduction == Boolean.TRUE) {
            return PW.City.cityCost(this, this.getCities(), this.getCities() + 1);
        } else {
            return PW.City.cityCost(null, this.getCities(), this.getCities() + 1);
        }
    }

    public int getTurnsInactiveForLoot(LootEntry loot) {
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
                    city.setBuilding(building, 0);
                }
            }
            city.profit(data()._continent(), 0, 0, this::hasProject, profitBuffer, 1, 1, 1);
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
    public Map<ResourceType, Double> getLootRevenueTotal() {
        LootEntry loot = getBeigeLoot();
        int turnsInactive = getTurnsInactiveForLoot(loot);
        double lootFactor = 0.14 * lootModifier();

        double[] lootRevenue = loot == null ? ResourceType.getBuffer() : PW.multiply(loot.getTotal_rss().clone(), lootFactor);
        if (getPositionEnum().id > Rank.APPLICANT.id) {
            DBAlliance alliance = getAlliance(false);
            if (alliance != null) {
                LootEntry aaLoot = alliance.getLoot();
                if (aaLoot != null) {
                    double[] lootScaled = aaLoot.getAllianceLootValue(getScore());
                    lootRevenue = ResourceType.add(lootRevenue, lootScaled);
                }
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
                    double[] revenue = getRevenue(1, true, true, false, true, false, false, 0d, false);
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
                    revenue = getRevenue(turnsFedUnpowered, true, true, false, true, false, true, 0d, false);
                }
                if (turnsUnfedUnpowered > 0) {
                    revenue = getRevenue(turnsUnfedUnpowered, true, true, false, true, true, true, 0d, false);
                }
                revenue = PW.capManuFromRaws(revenue, ResourceType.getBuffer());
            }
            if (turnsPowered > 0) {
                int turnsFedPowered = Math.min(turnsFed, turnsPowered);
                int turnsUnfedPowered = turnsPowered - turnsFedPowered;
                if (turnsFedPowered > 0) {
                    revenue = ResourceType.add(revenue, getRevenue(turnsFedPowered, true, true, false, true, false, false, 0d, false));
                }
                if (turnsUnfedPowered > 0) {
                    revenue = ResourceType.add(revenue, getRevenue(turnsUnfedPowered, true, true, false, true, true, false, 0d, false));
                }
            }
            if (loot != null) {
                revenue = PW.capManuFromRaws(revenue, loot.getTotal_rss());
            }
            for (int i = 0; i < lootRevenue.length; i++) {
                lootRevenue[i] += revenue[i] * lootFactor;
            }
        }
        return ResourceType.resourcesToMap(lootRevenue);
    }

    @Command
    public double getLootRevenueConverted() {
        return ResourceType.convertedTotal(getLootRevenueTotal());
    }

    public double estimateRssLootValue(double[] knownResources, LootEntry lootHistory, double[] buffer, boolean fetchStats) {
        if (lootHistory != null) {
            double[] loot = lootHistory.getTotal_rss();
            for (int i = 0; i < loot.length; i++) {
                knownResources[i] = loot[i];
            }
        }
        return ResourceType.convertedTotal(knownResources);
//        if (active_m() > TimeUnit.DAYS.toMinutes(90)) {
//            return 0;
//        }
//        if (active_m() <= 10000) {
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
//            long now = getSnapshot() == null ? System.currentTimeMillis() : getSnapshot();
//            for (Transaction2 transfer : transfers) {
//                if (transfer.getDate() > now) continue;
//                int sign = transfer.getReceiver() == nation_id ? 1 : -1;
//                bank = PW.add(bank, PW.resourcesToMap(transfer.resources));
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
//                consumption = PW.add(consumption, attConsume);
//
//                unitLosses = PW.add(unitLosses, attack.getUnitLosses(attacker));
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
//            totals = PW.add(totals, bank);
//            totals = PW.add(totals, trade);
//            totals = PW.subResourcesToA(totals, consumption);
//            totals = PW.add(totals, offset);
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
//            return PW.convertedTotal(totals);
//        }
//        Map<Integer, JavaCity> cityMap = getCityMap(false, false);
//
//        double daysInactive = Math.min(90, TimeUnit.MINUTES.toDays(active_m()));
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
//        return PW.convertedTotal(knownResources);
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
            bans = Locutus.imp().getNationDB().getBansForUser(user.getDiscordId(), data()._nationId());
        } else {
            bans = Locutus.imp().getNationDB().getBansForNation(data()._nationId());
        }
        if (getSnapshot() != null) {
            bans.removeIf(ban -> ban.date < getSnapshot());
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
        return Locutus.imp().getDiscordDB().getUserFromNationId(data()._nationId());
    }

    @Command(desc = "If registered with this Bot")
    public boolean isVerified() {
        return getDBUser() != null;
    }

    @Command(desc = "If the user has provided IRL identity verification to the game")
    public boolean hasProvidedIdentity(ValueStore store) {
        ScopedPlaceholderCache<DBNation> scoped = PlaceholderCache.getScoped(store, DBNation.class, "hasProvidedIdentity");
        Boolean verified = scoped.getMap(this,
                (ThrowingFunction<List<DBNation>, Map<DBNation, Boolean>>) f -> {
                    Set<Integer> nationIds = new IntOpenHashSet(f.size());
                    for (DBNation nation : f) nationIds.add(nation.getNation_id());
                    Set<Integer> verifiedSet = Locutus.imp().getDiscordDB().getVerified(nationIds);
                    Map<DBNation, Boolean> result = new Object2BooleanOpenHashMap<>(f.size());
                    for (DBNation nation : f) {
                        result.put(nation, verifiedSet.contains(nation.getNation_id()));
                    }
                    return result;
                });
        return verified != null && verified;
    }

    @Command(desc = "If in the discord guild for their alliance")
    public boolean isInAllianceGuild() {
        GuildDB db = Locutus.imp().getGuildDBByAA(data()._allianceId());
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
        GuildDB db = Locutus.imp().getGuildDBByAA(data()._allianceId());
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

    @Command(desc = "The flag url set in-game (snapshots only)")
    public String getFlagUrl() {
        throw new IllegalArgumentException("flagUrl is only supported for snapshots");
    }

    @Command(desc = "The unverified discord string set in-game (snapshots only)")
    public String getDiscordString() {
        return data()._discordStr();
    }

    @Command(desc = "The registered discord user id")
    public Long getUserId() {
        PNWUser dbUser = getDBUser();
        if (dbUser == null) return null;
        return dbUser.getDiscordId();
    }

    @Command(desc = "The registered discord user mention (or null)")
    public String getUserMention() {
        PNWUser dbUser = getDBUser();
        if (dbUser == null) return null;
        return "<@" + dbUser.getDiscordId() + ">";
    }

    @Command(desc = "Age of discord account in milliseconds")
    public long getUserAgeMs() {
        User user = getUser();
        return user == null ? 0 : (System.currentTimeMillis() - user.getTimeCreated().toEpochSecond() * 1000L);
    }

    @Command(desc = "Age of discord account in days")
    public double getUserAgeDays() {
        return getUserAgeMs() / (double) TimeUnit.DAYS.toMillis(1);
    }

    public User getUser() {
        PNWUser dbUser = getDBUser();
        return dbUser != null ? dbUser.getUser() : null;
    }

    @Command(desc = "Get the discord user object")
    public UserWrapper getDiscordUser(@Me Guild guild) {
        User user = getUser();
        if (user == null || guild == null) return null;
        Member member = guild.getMember(user);
        return member == null ? null : new UserWrapper(member);
    }

    @Command(desc = "Get the average value of a city attribute for this nation's cities")
    public double getCityAvg(@NoFormat TypedFunction<DBCity, Double> attribute) {
        Map<Integer, DBCity> cities = _getCitiesV3();
        double total = 0;
        for (Map.Entry<Integer, DBCity> entry : cities.entrySet()) {
            Double value = attribute.apply(entry.getValue());
            if (value != null) total += value;
        }
        return total / cities.size();
    }

    @Command(desc = "Get the summed total of a city attribute for this nation's cities")
    public double getCityTotal(@NoFormat TypedFunction<DBCity, Double> attribute) {
        Map<Integer, DBCity> cities = _getCitiesV3();
        double total = 0;
        for (Map.Entry<Integer, DBCity> entry : cities.entrySet()) {
            Double value = attribute.apply(entry.getValue());
            if (value != null) total += value;
        }
        return total;
    }

    @Command(desc = "Get the minimum value of a city attribute for this nation's cities")
    public double getCityMin(@NoFormat TypedFunction<DBCity, Double> attribute) {
        Map<Integer, DBCity> cities = _getCitiesV3();
        double min = Double.MAX_VALUE;
        for (Map.Entry<Integer, DBCity> entry : cities.entrySet()) {
            Double value = attribute.apply(entry.getValue());
            if (value != null) min = Math.min(min, value);
        }
        return min;
    }

    @Command(desc = "Get the maximum value of a city attribute for this nation's cities")
    public double getCityMax(@NoFormat TypedFunction<DBCity, Double> attribute) {
        Map<Integer, DBCity> cities = _getCitiesV3();
        double max = 0;
        for (Map.Entry<Integer, DBCity> entry : cities.entrySet()) {
            Double value = attribute.apply(entry.getValue());
            if (value != null) max = Math.max(max, value);
        }
        return max;
    }

    public Map.Entry<Long, String> getUnblockadeRequest() {
        ByteBuffer request = getMeta(NationMeta.UNBLOCKADE_REASON);
        if (request == null) return null;
        long cutoff = request.getLong();
        byte[] noteBytes = new byte[request.remaining()];
        request.get(noteBytes);
        String note = new String(noteBytes);
        return new KeyValue<>(cutoff, note);
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
        return Locutus.imp().getWarDb().getNationsBlockadedBy(data()._nationId());
    }

    @Command(desc = "List of nation ids which are blockading this nation with their navy ships in-game")
    public Set<Integer> getBlockadedBy() {
        return Locutus.imp().getWarDb().getNationsBlockading(data()._nationId());
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

        if (data()._color() == NationColor.BEIGE) {
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
    public String cellLookup(SpreadSheet sheet, String tabName, String columnSearch, String columnOutput, String search) {
        List<List<Object>> values = sheet.loadValues(tabName, false);
        if (values == null) return null;
        int searchIndex = SheetUtil.getIndex(columnSearch) - 1;
        int outputIndex = SheetUtil.getIndex(columnOutput) - 1;

        for (List<Object> row : values) {
            if (row.size() > searchIndex && row.size() > outputIndex) {
                Object cellSearch = row.get(searchIndex);
                if (cellSearch == null) continue;
                cellSearch = cellSearch.toString();
                if (search.equals(cellSearch)) {
                    Object value = row.get(outputIndex);
                    return value == null ? null : value.toString();
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

    @Command(desc = "Get the city url by index (1-indexed)")
    public String cityUrl(int index) {
        Set<Map.Entry<Integer, JavaCity>> cities = getCityMap(true, true, false).entrySet();
        int i = 0;
        for (Map.Entry<Integer, JavaCity> entry : cities) {
            if (++i == index) {
                String url = Settings.PNW_URL() + "/city/id=" + entry.getKey();
                return url;
            }
        }
        return null;
    }

    @Command(desc = "Game url for nation")
    public String getUrl() {
        return Settings.PNW_URL() + "/nation/id=" + getNation_id();
    }

    @Command(desc = "Game url for alliance")
    public String getAllianceUrl() {
        return Settings.PNW_URL() + "/alliance/id=" + getAlliance_id();
    }

    @Command(desc = "Markdown url for the nation")
    public String getMarkdownUrl() {
        return getNationUrlMarkup();
    }

    @Command(desc = "Markdown url to the bot's web page for the nation (instead of ingame page)")
    public String getWebUrl() {
        return MarkupUtil.markdownUrl(getName(), "<" + Settings.INSTANCE.WEB.FRONTEND_DOMAIN + "/nation/" + getId() + ">");
    }

    @Override
    public IShrink toShrink(int priority) {
        return IShrink.of(priority, getName() == null ? "nation:" + getId() : getName(), getMarkdownUrl(), getMarkdownUrl() + " | " + getAllianceUrlMarkup());
    }

    @Command(desc = "Google sheet named url")
    public String getSheetUrl() {
        return MarkupUtil.sheetUrl(getName(), getUrl());
    }

    public String getNationUrlMarkup() {
        String nationUrl = getUrl();
        nationUrl = MarkupUtil.markdownUrl(data()._nation(), "<" + nationUrl + ">");
        return nationUrl;
    }

    @Command
    public String getAllianceUrlMarkup() {
        String allianceUrl = getAllianceUrl();
        allianceUrl = MarkupUtil.markdownUrl(getAllianceName(), "<" + allianceUrl + ">");
        return allianceUrl;
    }

    public String toCityMilMarkdown() {
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
        List<ReportManager.Report> reports = reportManager.loadReports(getId(), getUserId(), null, null);
        if (getSnapshot() != null) {
            reports.removeIf(report -> report.date < getSnapshot());
        }
        return reports.size();
    }

    @Command
    public int getRemainingUnitBuy(MilitaryUnit unit, @Default Long timeSince) {
        if (timeSince == null) {
            long dcTurn = this.getTurnsFromDC();
            timeSince = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - dcTurn);
        }
        if (unit == MilitaryUnit.INFRASTRUCTURE || unit == MilitaryUnit.MONEY) return -1;

        int previousAmt = getUnitsAt(unit, timeSince);
        int currentAmt = getUnits(unit);
        int[] lostInAttacks = {0};

        if (unit != MilitaryUnit.SPIES) {
            Locutus.imp().getWarDb().iterateAttacks(getNation_id(), timeSince, (war, attack) -> {
                boolean isAttacker = attack.getAttacker_id() == data()._nationId();
                int val;
                if (isAttacker) {
                    val = attack.getAttUnitLosses(unit);
                } else {
                    val = attack.getDefUnitLosses(unit);
                }
                lostInAttacks[0] += val;
            });
        }

        int numPurchased = Math.max(0, currentAmt - previousAmt + lostInAttacks[0]);
        int maxPerDay = unit.getMaxPerDay(data()._cities(), this::hasProject);
        return Math.max(0, maxPerDay - numPurchased);
    }

    public PoliticsAndWarV3 getApi(boolean throwError) {
        ApiKeyPool.ApiKey apiKey = this.getApiKey(true);
        if (apiKey == null) {
            if (throwError) throw new IllegalStateException("No api key found for `" + data()._nation() + "` Please set one: " + CM.credentials.addApiKey.cmd.toSlashMention());
            return null;
        }
        return new PoliticsAndWarV3(ApiKeyPool.create(apiKey));
    }

    private Map.Entry<double[], String> createAndOffshoreDeposit(GuildDB currentDB, DBNation senderNation, Supplier<List<Auth.TradeResult>> tradeSupplier) {
        PoliticsAndWarV3 receiverApi = getApi(true);

        synchronized (OffshoreInstance.BANK_LOCK) {
            if (!TimeUtil.checkTurnChange()) {
                throw new IllegalArgumentException("Turn change");
            }
            if (getPosition() <= Rank.APPLICANT.id) {
                throw new IllegalArgumentException("Receiver is not member");
            }
            if (!this.hasPermission(AlliancePermission.WITHDRAW_BANK)) {
                return KeyValue.of(ResourceType.getBuffer(), "The nation specifies has no `" + AlliancePermission.WITHDRAW_BANK + "` permission");
            }
            if (!this.hasPermission(AlliancePermission.VIEW_BANK)) {
                return KeyValue.of(ResourceType.getBuffer(), "The nation specifies has no `" + AlliancePermission.VIEW_BANK + "` permission");
            }

            if (senderNation == null) throw new IllegalArgumentException("Sender is null");
//            if (isBlockaded()) throw new IllegalArgumentException("Receiver is blockaded");
//            if (senderNation.isBlockaded()) throw new IllegalArgumentException("Sender is blockaded");

            OffshoreInstance offshore = currentDB.getOffshore();

            GuildDB authDb = Locutus.imp().getGuildDBByAA(getAlliance_id());
            if (authDb == null) throw new IllegalArgumentException("Receiver is not in a server with this Bot: " + getAlliance_id());
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

            List<Auth.TradeResult> trades = tradeSupplier.get();
            if (trades.isEmpty()) {
                ApiKeyDetails stats = receiverApi.getApiKeyStats();
                int bits = stats.getPermission_bits();
                List<String> errors = new ArrayList<>();
                if (!ApiKeyPermission.NATION_VIEW_TRADES.has(bits)) {
                    errors.add("Missing `" + ApiKeyPermission.NATION_VIEW_TRADES + "`");
                }
                if (!ApiKeyPermission.NATION_ACCEPT_TRADE.has(bits)) {
                    errors.add("Missing `" + ApiKeyPermission.NATION_ACCEPT_TRADE + "`");
                }
                if (!errors.isEmpty()) {
                    // for the key starting with <letter> and ending with `<letter>`
                    String key = stats.getKey();
                    String redacted = key.substring(0, 1) + "..." + key.substring(key.length() - 1);
                    throw new IllegalArgumentException("Error: " + String.join(", ", errors) + " for key: `" + redacted + "` at <" + Settings.PNW_URL() + "/account/#7>");
                }
            }

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
                response.append("\n- No trades to deposit " + ResourceType.toString(toDeposit));
                return KeyValue.of(ResourceType.getBuffer(), response.toString());
            }
            double[] depositPositive = ResourceType.max(toDeposit.clone(), ResourceType.getBuffer());
            int receiverId;
            try {
                Bankrec deposit = receiverApi.depositIntoBank(depositPositive, "#ignore");
                double[] amt = ResourceType.fromApiV3(deposit, ResourceType.getBuffer());
                response.append("\nDeposited: `" + ResourceType.toString(amt) + "`");
                if (!ResourceType.equals(depositPositive, amt)) {
                    response.append("\n- Error Depositing: " + ResourceType.toString(depositPositive) + " != " + ResourceType.toString(amt));
                    return KeyValue.of(ResourceType.getBuffer(), response.toString());
                }
                receiverId = deposit.getReceiver_id();
            } catch (Throwable e) {
                e.printStackTrace();
                response.append("\n- Error Depositing: " + StringMan.stripApiKey(e.getMessage()));
                return KeyValue.of(ResourceType.getBuffer(), response.toString());
            }

            DBAlliance receiverAA = DBAlliance.getOrCreate(receiverId);
            OffshoreInstance bank = receiverAA.getBank();
            if (bank != offshore) {
                for (int i = 0; i < toDeposit.length; i++) {
                    if (toDeposit[i] < 0) toDeposit[i] = 0;
                }
                TransferResult transferResult = bank.transfer(offshore.getAlliance(), ResourceType.resourcesToMap(depositPositive), "#ignore", null);
                response.append("Offshore " + transferResult.toLineString());
                if (transferResult.getStatus() != OffshoreInstance.TransferStatus.SUCCESS) {
                    response.append("\n- Depositing failed");
                    return KeyValue.of(ResourceType.getBuffer(), response.toString());
                }
            }

            // add balance to guilddb
            long tx_datetime = System.currentTimeMillis();
            String note = "#deposit";

            response.append("\nAdding deposits:");

            offshore.getGuildDB().addTransfer(tx_datetime, senderId, senderType, offshore.getAlliance(), getNation_id(), note, toDeposit);
            response.append("\n- Added " + ResourceType.toString(toDeposit) + " to " + currentDB.getGuild());
            // add balance to expectedNation
            currentDB.addTransfer(tx_datetime, senderNation, senderId, senderType, getNation_id(), note, toDeposit);
            response.append("\n- Added " + ResourceType.toString(toDeposit) + " to " + senderNation.getUrl());

            MessageChannel logChannel = offshore.getGuildDB().getResourceChannel(0);
            if (logChannel != null) {
                RateLimitUtil.queue(logChannel.sendMessage(response));
            }

            return new KeyValue<>(toDeposit, response.toString());
        }
    }

    public Map.Entry<double[], String> tradeAndOffshoreDeposit(GuildDB currentDB, DBNation senderNation, double[] amounts) {
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
            throw new IllegalArgumentException("Banker nation has no auth (" + getNationUrlMarkup() + "). See: " + CM.credentials.login.cmd.toSlashMention());
        }

        Map<ResourceType, Double> amountMapDbl = amountMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> (double) e.getValue()));

        Supplier<List<Auth.TradeResult>> tradeSupplier = new Supplier<>() {
            @Override
            public List<Auth.TradeResult> get() {
                List<String> responses = new ArrayList<>();
                List<Map.Entry<ResourceType, Integer>> amounts = new ArrayList<>(amountMap.entrySet());
                for (int i = 0; i < amounts.size(); i++) {
                    Map.Entry<ResourceType, Integer> entry = amounts.get(i);
                    if (i > 0) {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    String trade = auth.createDepositTrade(senderNation, entry.getKey(), entry.getValue());
                    responses.add(trade);
                }
                List<Auth.TradeResult> result = senderNation.acceptTrades(getNation_id(), amountMapDbl, true);
                if (responses.size() > 0) {
                    if (result.size() > 0) {
                        Auth.TradeResult first = result.get(0);
                        first.setMessage(StringMan.join(responses, "\n") + "\n" + first.getMessage());
                    } else {
                        Logg.text("[Accept Trades] nation:" + getNation_id() + " | No trades to accept: " + responses);
                    }
                }
                return result;
            }
        };
        return createAndOffshoreDeposit(currentDB, senderNation, tradeSupplier);
    }

    public Map.Entry<double[], String> acceptAndOffshoreTrades(GuildDB currentDB, DBNation senderNation) {
        int expectedNationId = senderNation.getNation_id();
        PoliticsAndWarV3 api = getApi(true);

        Supplier<List<Auth.TradeResult>> tradeSupplier = new Supplier<>() {
            @Override
            public List<Auth.TradeResult> get() {
                return acceptTrades(senderNation.getNation_id(), null,false);
            }
        };
        return createAndOffshoreDeposit(currentDB, senderNation, tradeSupplier);
    }

    public List<Auth.TradeResult> acceptTrades(int expectedNationId, Map<ResourceType, Double> amount, boolean reverse) {
        if (expectedNationId == data()._nationId()) throw new IllegalArgumentException("Buyer and seller cannot be the same");
        if (!TimeUtil.checkTurnChange()) return List.of(new Auth.TradeResult("cannot accept during turn change", Auth.TradeResultType.BLOCKADED));
//        if (isBlockaded()) return List.of(new Auth.TradeResult("receiver is blockaded", Auth.TradeResultType.BLOCKADED));

        PoliticsAndWarV3 api = this.getApi(true);
        List<Auth.TradeResult> responses = new ArrayList<>();

        Map<Trade, Map.Entry<String, Auth.TradeResultType>> errors = new LinkedHashMap<>();

        String foodBuyOrSell = reverse ? "sell" : "buy";
        String rssBuyOrSell = reverse ? "buy" : "sell";

        List<Trade> tradesV3 = new ArrayList<>(api.fetchPrivateTrades(data()._nationId()));
        if (tradesV3.isEmpty()) {
            return List.of(new Auth.TradeResult("no trades to accept", Auth.TradeResultType.NO_TRADES));
        }

        double[] amountArr = amount == null ? null : ResourceType.resourcesToArray(amount);

        tradesV3.removeIf(f -> f.getSender_id() == null || f.getSender_id() != expectedNationId);
        tradesV3.removeIf((Predicate<Trade>) f -> {
            ResourceType resource = ResourceType.parse(f.getOffer_resource());
            if (f.getBuy_or_sell().equalsIgnoreCase(foodBuyOrSell)) {
                if (resource != ResourceType.FOOD) {
                    errors.put(f, KeyValue.of(foodBuyOrSell + " offers can only be food trades", Auth.TradeResultType.NOT_A_FOOD_TRADE));
                    return true;
                }
                if (f.getPrice() < 100000) {
                    errors.put(f, KeyValue.of(foodBuyOrSell + " offers must be at least $100,000 to deposit", Auth.TradeResultType.INCORRECT_PPU));
                    return true;
                }
                if (f.getSender_id() == null) {
                    errors.put(f, KeyValue.of("Sender id is null", Auth.TradeResultType.NOT_A_BUY_OFFER));
                    return true;
                }
                if (f.getSender_id() != expectedNationId) {
                    errors.put(f, KeyValue.of("Sender id is not expected nation id (" + f.getSender_id() + " != " + expectedNationId + ")", Auth.TradeResultType.NOT_A_BUY_OFFER));
                    return true;
                }
                double cost = f.getOffer_amount() * f.getPrice();
                if (amountArr != null && amountArr[0] + 100000 <= cost) {
                    errors.put(f, KeyValue.of("Found trade for $" + MathMan.format(cost) + " but user only specified amount of $" + MathMan.format(amountArr[0]), Auth.TradeResultType.INSUFFICIENT_RESOURCES));
                    return true;
                }
                return false;
            } else if (f.getBuy_or_sell().equalsIgnoreCase(rssBuyOrSell)) {
                if (resource == ResourceType.CREDITS) {
                    errors.put(f, KeyValue.of("Cannot " + rssBuyOrSell + " credits", Auth.TradeResultType.CANNOT_DEPOSIT_CREDITS));
                    return true;
                }
                if (f.getPrice() != 0) {
                    errors.put(f, KeyValue.of(rssBuyOrSell + " offers must be $0 to deposit", Auth.TradeResultType.INCORRECT_PPU));
                    return true;
                }
                if (f.getSender_id() == null) {
                    errors.put(f, KeyValue.of("Sender id is null", Auth.TradeResultType.NOT_A_SELL_OFFER));
                    return true;
                }
                if (f.getSender_id() != expectedNationId) {
                    errors.put(f, KeyValue.of("Sender id is not expected nation id (" + f.getSender_id() + " != " + expectedNationId + ")", Auth.TradeResultType.NOT_A_SELL_OFFER));
                    return true;
                }
                if (amountArr != null && amountArr[resource.ordinal()] < f.getOffer_amount()) {
                    errors.put(f, KeyValue.of("Found trade for " + MathMan.format(f.getOffer_amount()) + " " + resource + " but user only specified amount of " + MathMan.format(amountArr[resource.ordinal()]), Auth.TradeResultType.INSUFFICIENT_RESOURCES));
                    return true;
                }
                return false;
            } else {
                errors.put(f, KeyValue.of("Unknown buy or sell type: " + f.getBuy_or_sell(), Auth.TradeResultType.UNKNOWN_ERROR));
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
                    errors.put(trade, KeyValue.of(StringMan.stripApiKey(e.getMessage()), Auth.TradeResultType.UNKNOWN_ERROR));
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

    @Command
    public int getUnitCap(MilitaryUnit unit, @Switch("c") boolean checkBuildingsAndPop) {
        int result = checkBuildingsAndPop ? unit.getCap(this, false) : unit.getMaxMMRCap(data()._cities(), getResearchBits(), this::hasProject);
        return result;
    }

    public String toFullMarkdown() {
        StringBuilder body = new StringBuilder();
        //Nation | Leader name | timestamp(DATE_CREATED) `tax_id=1`
        body.append(getNationUrlMarkup()).append(" | ");
        body.append(data()._leader()).append(" | ");
        // DiscordUtil.timestamp
        if (data()._taxId() != 0) {
            body.append(" `tax_id=").append(data()._taxId()).append("` | ");
        }

        body.append(DiscordUtil.timestamp(data()._date(), null)).append("\n");
        //Alliance | PositionOrEnum(id=0,enum=0) | timestamp(Seniority)
        if (data()._allianceId() == 0) {
            body.append("`AA:0`");
        } else {
            body.append(getAllianceUrlMarkup());
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
        int len = body.length();
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
            if (len != body.length()) {
                body.append("\n\n");
            }
        }
        {
            Collection<DBCity> cities = _getCitiesV3().values();
            double infra = 0;
            double buildingInfra = 0;
            int unpowered = 0;
            for (DBCity value : cities) {
                buildingInfra += value.getNumBuildings() * 50;
                infra += value.getInfra();
                if (!value.isPowered()) unpowered++;
            }
            infra /= cities.size();
            body.append("I:`" + MathMan.format(infra)).append("` ");
            double maxCityInfra = maxCityInfra();
            if (maxCityInfra > infra) {
                body.append("(max:`").append(MathMan.format(maxCityInfra) + "`)");
            }
            body.append(" \uD83C\uDFD7\uFE0F | ");
            body.append("`c").append(cities.size());
            if (unpowered == 0) {
                body.append("`");
            } else if (unpowered == cities.size()) {
                body.append("` (\uD83E\uDEAB)");
            } else {
                body.append("` (").append(unpowered).append(" \uD83E\uDEAB)");
            }
            body.append(" | O:").append("`").append(getOff()).append("/").append(getMaxOff()).append("` \uD83D\uDDE1\uFE0F | ");
            body.append("D:`").append(getDef()).append("/").append(3).append("` \uD83D\uDEE1\uFE0F").append(" | `").append(MathMan.format(data()._score()) + "ns`\n");
        }
        //Domestic/War policy | beige turns | score
        String colorStr = getColor().name();
        if (data()._color() == NationColor.BEIGE && getSnapshot() == null) colorStr += "=" + getBeigeTurns();
        body.append("`").append(this.data()._domesticPolicy().name()).append("` | `").append(this.data()._warPolicy().name()).append("` | `")
                .append(getContinent().name()).append("` | `" + colorStr).append("` | ").append(DiscordUtil.timestamp(lastActiveMs(), null)).append(" \u23F0\n");
        //MMR[Building]: 1/2/3 | MMR[Unit]: 5/6/7
        body.append("\n");
        //VM: Timestamp(Started) - Timestamp(ends) (5 turns)
        if (getVm_turns() > 0) {
            body.append("**VM: **").append(DiscordUtil.timestamp(TimeUtil.getTimeFromTurn(data()._enteredVm()), null)).append(" - ").append(DiscordUtil.timestamp(TimeUtil.getTimeFromTurn(data()._leavingVm()), null)).append(" (").append(getVm_turns()).append(" turns left)").append("\n");
        }
        //
        //Units: Now/Buyable/Cap
        body.append("**Units:** Now/Remaining Buy/Cap (assumes 5553)\n```json\n");
        //Soldier: 0/0/0
        long dcTurn = this.getTurnsFromDC();
        long dcTimestamp = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - dcTurn);
        for (MilitaryUnit unit : new MilitaryUnit[]{MilitaryUnit.SOLDIER, MilitaryUnit.TANK, MilitaryUnit.AIRCRAFT, MilitaryUnit.SHIP, MilitaryUnit.SPIES, MilitaryUnit.MISSILE, MilitaryUnit.NUKE}) {
            if (unit == MilitaryUnit.MISSILE && !hasProject(Projects.MISSILE_LAUNCH_PAD)) continue;
            if (unit == MilitaryUnit.NUKE && !hasProject(Projects.NUCLEAR_RESEARCH_FACILITY)) continue;
            int cap = getUnitCap(unit, false);
            if (cap == Integer.MAX_VALUE) cap = -1;
            // 6 chars
            String unitsStr = String.format("%6s", getUnits(unit));
            String remainingStr = String.format("%6s", getRemainingUnitBuy(unit, dcTimestamp));
            String capStr = String.format("%6s", cap);
            body.append(String.format("%2s", unit.getEmoji())).append(" ").append(unitsStr).append("|").append(remainingStr).append("|").append(capStr).append("").append("\n");
        }
        body.append("``` ");
        body.append("MMR[Build]=`").append(getMMRBuildingStr()).append("` | MMR[Unit]=`").append(getMMR()).append("`\n\n");
        //
        //Attack Range: War= | Spy=
        {
            double offWarMin = PW.getAttackRange(true, true, true, data()._score());
            double offWarMax = PW.getAttackRange(true, true, false, data()._score());
            double offSpyMin = PW.getAttackRange(true, false, true, data()._score());
            double offSpyMax = PW.getAttackRange(true, false, false, data()._score());
            // use MathMan.format to format doubles
            body.append("**Attack Range**: War=`").append(MathMan.format(offWarMin)).append("-").append(MathMan.format(offWarMax)).append("` | Spy=`").append(MathMan.format(offSpyMin)).append("-").append(MathMan.format(offSpyMax)).append("`\n");
        }
        //Defense Range: War= | Spy=
        {
            double defWarMin = PW.getAttackRange(false, true, true, data()._score());
            double defWarMax = PW.getAttackRange(false, true, false, data()._score());
            double defSpyMin = PW.getAttackRange(false, false, true, data()._score());
            double defSpyMax = PW.getAttackRange(false, false, false, data()._score());
            // use MathMan.format to format doubles
            body.append("**Defense Range**: War=`").append(MathMan.format(defWarMin)).append("-").append(MathMan.format(defWarMax)).append("` | Spy=`").append(MathMan.format(defSpyMin)).append("-").append(MathMan.format(defSpyMax)).append("`\n");
        }
        body.append("\n");
        //
        if (getSnapshot() == null) {
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
        }
        //(optional) Active wars
        //
        //Revenue: {}
        // - Worth: $10
        double[] revenue = getRevenue();
        body.append("**Revenue:**");
        body.append(" worth: `$").append(MathMan.format(ResourceType.convertedTotal(revenue))).append("`");
        body.append("\n```json\n").append(ResourceType.toString(revenue)).append("\n``` ");
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
        if (getSnapshot() == null) {
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
                body.append("Bounties: `").append(bounties.toString()).append("`\n");
            }
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
                String url = Settings.PNW_URL() + "/nation/war/declare/id=" + getNation_id();
                nationUrl = embed ? MarkupUtil.markdownUrl(getName(), url) : "<" + url + ">";
            } else {
                nationUrl = getNationUrlMarkup();
            }
            response.append(nationUrl);
            if (getAlliance_id() != 0) {
                String allianceUrl = getAllianceUrlMarkup();
                response.append(" | ").append(allianceUrl);
                response.append("`#" + getAllianceRank(null) + "`");
            }

            if (embed && getPositionEnum() == Rank.APPLICANT && data()._allianceId() != 0) response.append(" (applicant)");

            if (getVm_turns() > 0) {
                response.append(" | VM");
            }

            response.append('\n');
        }
        if (general || military) {
            response.append("```");
            if (general) {
                int active = active_m();
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

    @Command(desc = "The highest infra level in their cities")
    public double maxCityInfra() {
        double maxInfra = 0;
        Collection<DBCity> cities = _getCitiesV3().values();
        for (DBCity city : cities) {
            maxInfra = Math.max(city.getInfra(), maxInfra);
        }
        return maxInfra;
    }

    @Command(desc = "Sum of city attribute for specific cities this nation has")
    public double getTotal(@NoFormat TypedFunction<DBCity, Double> attribute, @NoFormat @Default Predicate<DBCity> filter) {
        Collection<DBCity> cities = this._getCitiesV3().values();
        return cities.stream().filter(f -> filter == null || filter.test(f)).mapToDouble(attribute::apply).sum();
    }

    @Command(desc = "Average of city attribute for specific cities in nation")
    public double getAverage(@NoFormat TypedFunction<DBCity, Double> attribute, @NoFormat @Default Predicate<DBCity> filter) {
        Collection<DBCity> cities = this._getCitiesV3().values();
        return cities.stream().filter(f -> filter == null || filter.test(f)).mapToDouble(attribute::apply).average().orElse(0);
    }

    @Command(desc = "Returns the average value of the given attribute per another attribute (such as infra)")
    public double getAveragePer(@NoFormat TypedFunction<DBCity, Double> attribute, @NoFormat TypedFunction<DBCity, Double> per, @Default Predicate<DBCity> filter) {
        Collection<DBCity> cities = this._getCitiesV3().values();
        double total = 0;
        double perTotal = 0;
        for (DBCity city : cities) {
            if (filter != null && !filter.test(city)) continue;
            total += attribute.apply(city);
            perTotal += per.apply(city);
        }
        return total / perTotal;
    }

    @Command(desc = "Count of cities in nation matching a filter")
    public int countCities(@NoFormat @Default Predicate<DBCity> filter) {
        if (filter == null) return getCities();
        return (int) _getCitiesV3().values().stream().filter(filter).count();
    }

    @Command(desc = "The highest land level in their cities")
    public double maxCityLand() {
        double maxLand = 0;
        Collection<DBCity> cities = _getCitiesV3().values();
        for (DBCity city : cities) {
            maxLand = Math.max(city.getLand(), maxLand);
        }
        return maxLand;
    }

    public MailApiResponse sendMail(ApiKeyPool pool, String subject, String message, boolean priority) {
        try {
            if (pool.size() == 1 && pool.getNextApiKey().getKey().equalsIgnoreCase(Locutus.loader().getApiKey())) {
                Auth auth = Locutus.imp().getRootAuth();
                if (auth != null) {
                    String result = new MailTask(auth, priority, this, subject, message, null).call();
                    if (result.contains("Message sent")) {
//                    return JsonParser.parseString("{\"success\":true,\"to\":\"" + data()._nationId() + "\",\"cc\":null,\"subject\":\"" + subject + "\"}").getAsJsonObject();
                        return new MailApiResponse(MailApiSuccess.SUCCESS, null);
                    }
                }
            }
            ApiKeyPool.ApiKey pair = pool.getNextApiKey();
            Map<String, String> post = new HashMap<>();
            post.put("to", getNation_id() + "");
            post.put("subject", subject);
            post.put("message", message);
            String url = Settings.PNW_URL() + "/api/send-message/?key=" + pair.getKey();
            String result = FileUtil.get(FileUtil.readStringFromURL(priority ? PagePriority.MAIL_SEND_SINGLE : PagePriority.MAIL_SEND_BULK, url, post, null));
            if (result.contains("Invalid API key")) {
                pair.deleteApiKey();
                pool.removeKey(pair);
                return new MailApiResponse(MailApiSuccess.INVALID_KEY, null);
            } else {
                String successStr = "success\":";
                int successIndex = result.indexOf(successStr);
                if (successIndex != -1) {
                    char tf = result.charAt(successIndex + successStr.length());
                    if (tf == 't') {
                        return new MailApiResponse(MailApiSuccess.SUCCESS, null);
                    }
                }
                Logg.text("Invalid response\n\n---START BODY (5)---\n" + result + "\n---END BODY---");
            }
            try {
                JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
                String generalMessage = obj.get("general_message").getAsString();
                if (generalMessage.equalsIgnoreCase("This API key cannot be used for this API endpoint, it will only work for API v3.")) {
                    return new MailApiResponse(MailApiSuccess.NON_MAIL_KEY, null);
                }
                return new MailApiResponse(MailApiSuccess.ERROR_MESSAGE, StringMan.stripApiKey(generalMessage));
            } catch (JsonSyntaxException e) {
                Logg.text("Error sending mail to " + getNation_id() + " with key " + pair.getKey() + "\n\n---START BODY---\n" + result + "\n---END BODY---");
            }
            return new MailApiResponse(MailApiSuccess.UNKNOWN_ERROR, StringMan.stripApiKey(result));
        } catch (IOException e) {
            e.printStackTrace();
            return new MailApiResponse(MailApiSuccess.UNKNOWN_ERROR, StringMan.stripApiKey(e.getMessage()));
        }
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

        Set<DBWar> wars = Locutus.imp().getWarDb().getWarsByNation(data()._nationId());
        for (DBWar war : wars) {
            ZonedDateTime warTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(war.getDate()), ZoneOffset.UTC);
            long warTurns = TimeUtil.getTurn(warTime);
            if (warTurns < currentTurn - days * 12) continue;

            if (war.getAttacker_id() == data()._nationId()) {
                int turnsAgo = (int) (currentTurn - warTurns);
                beigeList.add(new KeyValue<>(war.getDate(), - (days * 120)));
//                beige[turnsAgo] -= days * 120;
            }
        }
        Locutus.imp().getWarDb().iterateAttacks(data()._nationId(), System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days + 1), (war, attack) -> {
            if (attack.getAttack_type() != AttackType.VICTORY || attack.getVictor() == data()._nationId()) return;

            ZonedDateTime warTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(attack.getDate()), ZoneOffset.UTC);
            long warTurns = TimeUtil.getTurn(warTime);

            if (warTurns < currentTurn - days * 12) return;
            int turnsAgo = (int) (currentTurn - warTurns);
            beigeList.add(new KeyValue<>(attack.getDate(), 24));
//            beige[turnsAgo] += 24;
        });

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
        if (this.data()._beigeTimer() < turnLeaveBeige) {
            this.edit().setBeigeTimer(turnLeaveBeige);
            Locutus.imp().getNationDB().saveNation(this);
        }
        return turnsBeige;
    }

    @Command(desc = "Game turns left on the beige color bloc")
    public int getBeigeTurns() {
        if (!isBeige()) return 0;
        long turn = TimeUtil.getTurn(getSnapshot());
        if (turn >= data()._beigeTimer()) {
            return 24;
        } else {
            return (int) (data()._beigeTimer() - turn);
        }
    }

    @Command(desc = "Returns self nation ID if the nation is a reroll, otherwise 0")
    public int isReroll() {
        return isReroll(false);
    }

    public int isReroll(boolean fetchUid) {
        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNationsById();
        for (Map.Entry<Integer, DBNation> entry : nations.entrySet()) {
            int otherId = entry.getKey();
            DBNation otherNation = entry.getValue();
            if (otherNation.getDate() == 0) continue;

            if (otherId > data()._nationId() && Math.abs(otherNation.getDate()  - getDate()) > TimeUnit.DAYS.toMillis(14)) {
                return data()._nationId();
            }
        }

        if (Settings.INSTANCE.TASKS.AUTO_FETCH_UID && fetchUid) {
            try {
                BigInteger uid = fetchUid(true);
                for (Map.Entry<Integer, Long> entry : Locutus.imp().getDiscordDB().getUuids(uid)) {
                    int nationId = entry.getKey();
                    if (nationId == this.data()._nationId()) continue;
                    BigInteger latest = Locutus.imp().getDiscordDB().getLatestUuid(nationId);
                    if (latest != null && latest.equals(uid)) {
                        return nationId;
                    }
                    break;
                }
            } catch (Exception e) {
                AlertUtil.error("Failed to fetch uid for " + data()._nationId(), e);
            }
        }

        return 0;
    }

    public BigInteger fetchUid(boolean priority) throws IOException {
        return new GetUid(this, priority).call();
    }

    @Command(desc = "The unique network id of this nation")
    public BigInteger getLatestUid(@Default boolean do_not_fetch) throws IOException {
        BigInteger latest = Locutus.imp().getDiscordDB().getLatestUuid(getId());
        if (latest == null && isValid() && !do_not_fetch) {
            latest = fetchUid(false);
        }
        return latest;
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
        Set<Integer> multiNations = new IntOpenHashSet();
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
        if (active_m() < 2880) return false;
        DBWar lastWar = Locutus.imp().getWarDb().getLastOffensiveWar(data()._nationId(), getSnapshot());
        if (lastWar != null && lastWar.getDefender_id() == data()._nationId() && lastWar.getStatus() == WarStatus.ATTACKER_VICTORY) {
            long now = getSnapshot() == null ? System.currentTimeMillis() : getSnapshot();
            long lastActiveCutoff = now - TimeUnit.MINUTES.toMillis(Math.max(active_m() + 1220, 7200));
            if (lastWar.getDate() > lastActiveCutoff) return true;
        }
        return false;
    }

    @Command(desc = "Turns left on the city timer")
    public long getCityTurns() {
        return (getCityTimerAbsoluteTurn() - TimeUtil.getTurn(getSnapshot()));
    }

    @Command(desc = "Absolute turn the city time ends before being able to buy a city")
    public long getCityTimerAbsoluteTurn() {
        return data()._cityTimer();
    }

    @Command(desc = "Turns left on the project timer before being able to buy a project")
    public long getProjectTurns() {
        return (getProjectAbsoluteTurn() - TimeUtil.getTurn(getSnapshot()));
    }

    public void setMMR(double barracks, double factories, double hangars, double drydocks) {
        this.edit().setSoldiers((int) (barracks * data()._cities() * Buildings.BARRACKS.getUnitCap()));
        this.edit().setTanks((int) (factories * data()._cities() * Buildings.FACTORY.getUnitCap()));
        this.edit().setAircraft((int) (hangars * data()._cities() * Buildings.HANGAR.getUnitCap()));
        this.edit().setShips((int) (drydocks * data()._cities() * Buildings.DRYDOCK.getUnitCap()));
    }

    @Command(desc = "Alliance rank by score")
    public int getAllianceRank(@NoFormat @Default NationFilter filter) {
        if (data()._allianceId() == 0) return Integer.MAX_VALUE;
        return getAlliance().getRank(filter);
    }

    @Command(desc= "Get total free building slots in this nations cities")
    public int getFreeBuildings() {
        int total = 0;
        for (DBCity city : _getCitiesV3().values()) {
            total += Math.max(0, ((int) city.getInfra() - city.getNumBuildings() * 50) / 50);
        }
        return total;
    }

    @Command(desc = "Number of buildings total")
    public int getBuildings(@NoFormat @Default Set<Building> buildings) {
        int total = 0;
        Collection<DBCity> cities = _getCitiesV3().values();
        if (buildings != null) {
            for (DBCity city : cities) {
                for (Building building : buildings) {
                    total += city.getBuilding(building);
                }
            }
        } else {
            for (DBCity city : cities) {
                total += city.getNumBuildings();
            }
        }
        return total;
    }

    public double getAvgBuildings() {
        return getAvgBuildings(null);
    }

    @Command(desc = "Number of buildings per city")
    public double getAvgBuildings(@NoFormat @Default Set<Building> buildings) {
        int total = 0;
        Collection<DBCity> cities = _getCitiesV3().values();
        if (buildings != null) {
            for (DBCity city : cities) {
                for (Building building : buildings) {
                    total += city.getBuilding(building);
                }
            }
        } else {
            for (DBCity city : cities) {
                total += city.getNumBuildings();
            }
        }
        return total / (double) cities.size();
    }

    @Command(desc = "If on the correct MMR for their alliance (if one is set)")
    @RolePermission(Roles.MEMBER)
    public boolean correctAllianceMMR(@Me GuildDB db) {
        if (getPosition() <= 1 || getVm_turns() > 0) return true;
        return db.hasRequiredMMR(this);
    }

    @Command(desc = """
            The average military buildings (building mmr) in all cities as a decimal
            barracks/factories/hangars/drydocks]
            e.g. 4.9/4.8/4.3/2.5""")
    public String getMMRBuildingDecimal() {
        double[] arr = getMMRBuildingArr();
        return Arrays.stream(arr).mapToObj(MathMan::format).collect(Collectors.joining("/"));
    }

    public double[] getMMRBuildingArr() {
        double barracks = 0; // for rounding
        double factories = 0;
        double hangars = 0;
        double drydocks = 0;
        Map<Integer, JavaCity> cityMap = getCityMap(false);
        for (Map.Entry<Integer, JavaCity> entry : cityMap.entrySet()) {
            barracks += entry.getValue().getBuilding(Buildings.BARRACKS);
            factories += entry.getValue().getBuilding(Buildings.FACTORY);
            hangars += entry.getValue().getBuilding(Buildings.HANGAR);
            drydocks += entry.getValue().getBuilding(Buildings.DRYDOCK);
        }
        barracks /= cityMap.size();
        factories /= cityMap.size();
        hangars /= cityMap.size();
        drydocks /= cityMap.size();
        return new double[]{barracks, factories, hangars, drydocks};
    }
    @Command(desc = """
            The average military buildings (building mmr) in all cities as a whole number
            barracks factories hangars drydocks
            Maximum is: 5553""")
    public String getMMRBuildingStr() {
        double[] arr = getMMRBuildingArr();
        return Math.round(arr[0]) + "" + Math.round(arr[1]) + Math.round(arr[2]) + Math.round(arr[3]);
    }

    /**
     * MMR (units)
     * @return
     */
    @Command(desc = """
            Average military building capacity used (unit mmr) in all cities as a whole number
            soldiers tanks aircraft ships
            Maximum is: 5553""")
    public String getMMR() {
        double[] arr = getMMRUnitArr();
        return Math.round(arr[0]) + "" + Math.round(arr[1]) + Math.round(arr[2]) + Math.round(arr[3]);
    }

    // decimal version of the above
    @Command(desc = """
            Average military building capacity used (unit mmr) in all cities as a decimal
            soldiers/tanks/aircraft/ships
            e.g. 4.9/4.8/4.3/2.5""")
    public String getMMRUnitDecimal() {
        double[] arr = getMMRUnitArr();
        return Arrays.stream(arr).mapToObj(MathMan::format).collect(Collectors.joining("/"));
    }

    public double[] getMMRUnitArr() {
        double soldiers = getSoldiers() / ((double) data()._cities() * Buildings.BARRACKS.getUnitCap());
        double tanks = getTanks() / ((double) data()._cities() * Buildings.FACTORY.getUnitCap());
        double aircraft = getAircraft() / ((double) data()._cities() * Buildings.HANGAR.getUnitCap());
        double ships = getShips() / ((double) data()._cities() * Buildings.DRYDOCK.getUnitCap());
        return new double[]{soldiers, tanks, aircraft, ships};
    }

    @Command(desc = "Total monetary value of military units")
    public double militaryValue() {
        return militaryValue(true);
    }

    public long militaryValue(boolean ships) {
        long total = 0;
        total += data()._soldiers() * MilitaryUnit.SOLDIER.getConvertedCost(this::getResearch);
        total += data()._tanks() * MilitaryUnit.TANK.getConvertedCost(this::getResearch);
        total += data()._aircraft() * MilitaryUnit.AIRCRAFT.getConvertedCost(this::getResearch);
        if (ships) {
            total += this.data()._ships() * MilitaryUnit.SHIP.getConvertedCost(this::getResearch);
        }
        return total;
    }
    public Activity getActivity() {
        return new Activity(getNation_id());
    }

    public Activity getActivity(long turns) {
        long now = TimeUtil.getTurn();
        return new Activity(getNation_id(), now - turns, Long.MAX_VALUE);
    }

    public Activity getActivity(long turnStart, long turnEnd) {
        return new Activity(getNation_id(), turnStart, turnEnd);
    }

    public JsonObject sendMail(Auth auth, boolean priority, String subject, String body) throws IOException {
        String result = new MailTask(auth, priority, this, subject, body, null).call();
        if (result.contains("Message sent")) {
            return JsonParser.parseString("{\"success\":true,\"to\":\"" + data()._nationId() + "\",\"cc\":null,\"subject\":\"" + subject + "\"}").getAsJsonObject();
        }
        // return new json object
        JsonObject obj = new JsonObject();
        // add error key with result
        obj.addProperty("error", result);
        return obj;
    }

    public Map.Entry<Integer, Integer> getCommends() throws IOException {
        Document dom = Jsoup.parse(FileUtil.readStringFromURL(PagePriority.COMMEND, getUrl()));
        int commend = Integer.parseInt(dom.select("#commendment_count").text());
        int denounce = Integer.parseInt(dom.select("#denouncement_count").text());
        return new KeyValue<>(commend, denounce);
    }

    public void setProjectsRaw(long projBitmask) {
        getCache().lastCheckProjectsMS = System.currentTimeMillis();
        this.edit().setProjects(projBitmask);
    }

    @Command(desc = "The projects built as a bitmask")
    public long getProjectBitMask() {
        return data()._projects();
    }

    public double estimateScore() {
        return estimateScore(getInfra());
    }

    @Command(desc = "Get resource quantity for this nation")
    public long getTradeQuantity(ValueStore store, long dateStart, @Default Long dateEnd, @NoFormat @Default Set<ResourceType> types, @NoFormat @Default Predicate<DBTrade> filter, @Switch("n") boolean net) {
        String funcStr = "getTradeQuantity(" + dateStart + "," + dateEnd + "," + StringMan.getString(types) + "," + filter + ")";
        ScopedPlaceholderCache<DBNation> scoped = PlaceholderCache.getScoped(store, DBNation.class, funcStr);

        if (dateEnd == null) dateEnd = getSnapshot() == null ? Long.MAX_VALUE : getSnapshot();
        List<DBNation> nations = scoped.getList(this);
        Set<Integer> nationIds = nations.stream().map(DBNation::getNation_id).collect(Collectors.toSet());
        Long finalDateEnd = dateEnd;
        Long quantity = scoped.getMap(this, new Function<List<DBNation>, Map<DBNation, Long>>() {
            @Override
            public Map<DBNation, Long> apply(List<DBNation> nations) {
                List<DBTrade> trades = nations.size() > 1000 ? Locutus.imp().getTradeManager().getTradeDb().getTrades(dateStart, finalDateEnd) : Locutus.imp().getTradeManager().getTradeDb().getTrades(nationIds, dateStart, finalDateEnd);
                if (nationIds.size() > 1000) {
                    trades.removeIf(t -> !nationIds.contains(t.getBuyer()) || !nationIds.contains(t.getSeller()));
                }
                if (filter != null) trades.removeIf(filter.negate());
                if (types != null) {
                    trades.removeIf(t -> !types.contains(t.getResource()));
                }
                Map<DBNation, Long> quantity = new LinkedHashMap<>();
                for (DBTrade trade : trades) {
                    DBNation buyer = trade.getBuyerNation();
                    DBNation seller = trade.getSellerNation();
                    if (buyer != null && nationIds.contains(buyer.getNation_id())) {
                        quantity.put(buyer, quantity.getOrDefault(buyer, 0L) + trade.getQuantity());
                    }
                    if (seller != null && nationIds.contains(seller.getNation_id())) {
                        int change = net ? trade.getQuantity() * -1 : trade.getQuantity();
                        quantity.put(seller, quantity.getOrDefault(seller, 0L) + change);
                    }
                }
                return quantity;
            }
        });
        return quantity == null ? 0 : quantity;
    }

    @Command(desc = "Get resource quantity for this nation")
    public long getTradeAvgPpu(ValueStore store, long dateStart, @Default Long dateEnd, @NoFormat @Default Set<ResourceType> types, @NoFormat @Default Predicate<DBTrade> filter) {
        String funcStr = "getTradeAvgPpu(" + dateStart + "," + dateEnd + "," + StringMan.getString(types) + "," + filter + ")";
        ScopedPlaceholderCache<DBNation> scoped = PlaceholderCache.getScoped(store, DBNation.class, funcStr);

        if (dateEnd == null) dateEnd = getSnapshot() == null ? Long.MAX_VALUE : getSnapshot();
        List<DBNation> nations = scoped.getList(this);
        Set<Integer> nationIds = nations.stream().map(DBNation::getNation_id).collect(Collectors.toSet());
        Long finalDateEnd = dateEnd;
        Long avgPPU = scoped.getMap(this, new Function<List<DBNation>, Map<DBNation, Long>>() {
            @Override
            public Map<DBNation, Long> apply(List<DBNation> nations) {
                List<DBTrade> trades = nations.size() > 1000 ? Locutus.imp().getTradeManager().getTradeDb().getTrades(dateStart, finalDateEnd) : Locutus.imp().getTradeManager().getTradeDb().getTrades(nationIds, dateStart, finalDateEnd);
                if (nationIds.size() > 1000) {
                    trades.removeIf(t -> !nationIds.contains(t.getBuyer()) || !nationIds.contains(t.getSeller()));
                }
                if (filter != null) trades.removeIf(filter.negate());
                if (types != null) {
                    trades.removeIf(t -> !types.contains(t.getResource()));
                }
                Map<DBNation, Long> quantity = new LinkedHashMap<>();
                Map<DBNation, Long> price = new LinkedHashMap<>();
                for (DBTrade trade : trades) {
                    DBNation buyer = trade.getBuyerNation();
                    DBNation seller = trade.getSellerNation();
                    if (buyer != null && nationIds.contains(buyer.getNation_id())) {
                        quantity.put(buyer, quantity.getOrDefault(buyer, 0L) + trade.getQuantity());
                        price.put(buyer, price.getOrDefault(buyer, 0L) + trade.getPpu() * (long) trade.getQuantity());
                    }
                    if (seller != null && nationIds.contains(seller.getNation_id())) {
                        quantity.put(seller, quantity.getOrDefault(seller, 0L) + trade.getQuantity());
                        price.put(seller, price.getOrDefault(seller, 0L) + trade.getPpu() * (long) trade.getQuantity());
                    }
                }
                return price.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() / quantity.get(e.getKey())));
            }
        });
        return avgPPU == null ? 0 : avgPPU;
    }

    @Command(desc = "Get resource quantity for this nation")
    public double getTradeValue(ValueStore store, long dateStart, @Default Long dateEnd, @NoFormat @Default Set<ResourceType> types, @NoFormat @Default Predicate<DBTrade> filter) {
        String funcStr = "getTradeQuantity(" + dateStart + "," + dateEnd + "," + StringMan.getString(types) + "," + filter + ")";
        ScopedPlaceholderCache<DBNation> scoped = PlaceholderCache.getScoped(store, DBNation.class, funcStr);

        if (dateEnd == null) dateEnd = getSnapshot() == null ? Long.MAX_VALUE : getSnapshot();
        List<DBNation> nations = scoped.getList(this);
        Set<Integer> nationIds = nations.stream().map(DBNation::getNation_id).collect(Collectors.toSet());
        Long finalDateEnd = dateEnd;
        Double value = scoped.getMap(this, new Function<List<DBNation>, Map<DBNation, Double>>() {
            @Override
            public Map<DBNation, Double> apply(List<DBNation> nations) {
                List<DBTrade> trades = nations.size() > 1000 ? Locutus.imp().getTradeManager().getTradeDb().getTrades(dateStart, finalDateEnd) : Locutus.imp().getTradeManager().getTradeDb().getTrades(nationIds, dateStart, finalDateEnd);
                if (nationIds.size() > 1000) {
                    trades.removeIf(t -> !nationIds.contains(t.getBuyer()) || !nationIds.contains(t.getSeller()));
                }
                if (filter != null) trades.removeIf(filter.negate());
                if (types != null) {
                    trades.removeIf(t -> !types.contains(t.getResource()));
                }
                Map<DBNation, Double> value = new LinkedHashMap<>();
                for (DBTrade trade : trades) {
                    DBNation buyer = trade.getBuyerNation();
                    DBNation seller = trade.getSellerNation();
                    if (buyer != null && nationIds.contains(buyer.getNation_id())) {
                        value.put(buyer, value.getOrDefault(buyer, 0d) + ResourceType.convertedTotal(trade.getResource(), trade.getQuantity()));
                    }
                    if (seller != null && nationIds.contains(seller.getNation_id())) {
                        value.put(seller, value.getOrDefault(seller, 0d) + ResourceType.convertedTotal(trade.getResource(), trade.getQuantity()));
                    }
                }
                return value;
            }
        });
        return value == null ? 0 : value;
    }

    public double infraCost(double from, double to) {
        return PW.City.Infra.calculateInfra(from, to,
                hasProject(Projects.ADVANCED_ENGINEERING_CORPS),
                hasProject(Projects.CENTER_FOR_CIVIL_ENGINEERING),
                getDomesticPolicy() == DomesticPolicy.URBANIZATION,
                hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY),
                hasProject(Projects.BUREAU_OF_DOMESTIC_AFFAIRS));
    }

    public double landCost(double from, double to) {
        double factor = 1;
        if (hasProject(Projects.ADVANCED_ENGINEERING_CORPS)) factor -= 0.05;
        if (hasProject(Projects.ARABLE_LAND_AGENCY)) factor -= 0.05;
        if (getDomesticPolicy() == DomesticPolicy.RAPID_EXPANSION) {
            factor -= 0.05;
            if (hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY)) factor -= 0.025;
            if (hasProject(Projects.BUREAU_OF_DOMESTIC_AFFAIRS)) factor -= 0.0125;
        }
        return PW.City.Land.calculateLand(from, to) * (to > from ? factor : 1);
    }

    public double estimateScore(double infra) {
        return estimateScore(null, infra, null, null);
    }

    public Map<ResourceType, Double> checkExcessResources(GuildDB db, Map<ResourceType, Double> stockpile) {
        return checkExcessResources(db, stockpile, true);
    }

    public Map<ResourceType, Double> checkExcessResources(GuildDB db, Map<ResourceType, Double> stockpile, boolean update) {
        double factor = 3;
        Map<ResourceType, Double> required;
        if (getCities() >= 10 && getAircraft() > 0) {
            required = PW.multiply(db.getPerCityWarchest(), (double) getCities());
        } else {
            required = PW.multiply(db.getPerCityWarchest(), (double) getCities() * 0.33);
            required.remove(ResourceType.ALUMINUM);
            required.remove(ResourceType.URANIUM);
            required.remove(ResourceType.FOOD);
            if (getAircraft() <= 0) {
                required.remove(ResourceType.GASOLINE);
                required.remove(ResourceType.STEEL);
                required.remove(ResourceType.MONEY);
            }
        }

        Map<Integer, JavaCity> cityMap = getCityMap(update, update, false);
        for (Map.Entry<Integer, JavaCity> cityEntry : cityMap.entrySet()) {
            JavaCity city = cityEntry.getValue();
            Map<ResourceType, Double> cityProfit = ResourceType.resourcesToMap(city.profit(data()._continent(), getRads(), -1L, this::hasProject, null, data()._cities(), 1, 12));
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

        double excessTotal = ResourceType.convertedTotal(stockpile);
        if (excessTotal > 1000000L * getCities()) {
            return stockpile;
        }

        return new HashMap<>();
    }

    @Command(desc = "If fighting a war against another active nation")
    public boolean isFightingActive() {
        if (getDef() > 0) return true;
        if (getOff() > 0) {
            for (DBWar activeWar : this.getActiveWars()) {
                DBNation other = activeWar.getNation(!activeWar.isAttacker(this));
                if (other != null && other.active_m() < 1440 && other.getVm_turns() == 0) return true;
            }
        }
        return false;
    }

    @Command(desc = "Discord online status")
    public OnlineStatus getOnlineStatus() {
        User user = getUser();
        if (user != null) {
            for (Guild guild : user.getMutualGuilds()) {
                Member member = guild.getMember(user);
                if (member != null) {
                    return member.getOnlineStatus();
                }
            }
        }
        return OnlineStatus.OFFLINE;
    }

    @Command(desc = "If online ingame within 60m or currently online discord\n" +
            "Discord activity requires being registered with the bot")
    public boolean isOnline() {
        if (active_m() < 60) return true;
        OnlineStatus status = getOnlineStatus();
        return status == OnlineStatus.ONLINE || status == OnlineStatus.DO_NOT_DISTURB;
    }

    public Set<DBWar> getActiveWars() {
        if (getSnapshot() != null) {
            long end = getSnapshot();
            long start = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(end) - 59);
            Set<DBWar> wars = Locutus.imp().getWarDb().getWarsByNationMatching(data()._nationId(), f -> f.getDate() > start && f.getDate() < end);
            if (wars.isEmpty()) {
                return wars;
            }
            Set<DBWar>[] finalCopy = new Set[1];
            Locutus.imp().getWarDb().iterateAttackList(wars, f -> f == AttackType.VICTORY || f == AttackType.PEACE, f -> {
                if (f.getDate() <= end) {
                    Set<DBWar> value = finalCopy[0];
                    if (value == null) {
                        value = new ObjectOpenHashSet<>(wars);
                        finalCopy[0] = value;
                    }
                    value.remove(new DBWar.DBWarKey(f.getWar_id()));
                }
                return false;
            }, (war, attacks) -> {});
            Set<DBWar> value = finalCopy[0];
            return value == null ? wars : value;
        }
        return Locutus.imp().getWarDb().getActiveWars(data()._nationId());
    }

    public Set<DBWar> getActiveOffensiveWars() {
        Set<DBWar> myWars = getActiveWars();
        if (myWars.isEmpty()) return Collections.emptySet();
        Set<DBWar> result = new ObjectOpenHashSet<>(myWars);
        result.removeIf(f -> f.getAttacker_id() != data()._nationId());
        return result;
    }

    public Set<DBWar> getActiveDefensiveWars() {
        Set<DBWar> myWars = getActiveWars();
        if (myWars.isEmpty()) return Collections.emptySet();
        Set<DBWar> result = new ObjectOpenHashSet<>(myWars);
        result.removeIf(f -> f.getDefender_id() != data()._nationId());
        return result;
    }

    public Set<DBWar> getWars() {
        if (getSnapshot() != null) {
            return Locutus.imp().getWarDb().getWarsByNationMatching(data()._nationId(), f -> f.getDate() < getSnapshot());
        }
        return Locutus.imp().getWarDb().getWarsByNation(data()._nationId());
    }

    public List<AllianceChange> getAllianceHistory(Long date) {
        return Locutus.imp().getNationDB().getRemovesByNation(getNation_id(), date);
    }

    public AllianceChange getPreviousAlliance(boolean ignoreApplicant, Long date) {
        return Locutus.imp().getNationDB().getPreviousAlliance(data()._nationId(), data()._allianceId());
    }

    public IShrink getWarInfoEmbed(DBWar war) {
        return war.getWarInfoEmbed(war.isAttacker(this), true, null);
    }

    public IShrink getWarInfoEmbed() {
        IShrink body = EmptyShrink.EMPTY;
        Set<DBWar> wars = this.getActiveWars();

        double[] total = {0d};
        for (DBWar war : wars) {
            body = body.append(war.getWarInfoEmbed(war.isAttacker(this), true, f -> total[0] += f));
        }
        if (!wars.isEmpty()) body.append(IShrink.of("", "-# total loot: `~$" + MathMan.format(total[0]) + "`\n"));
        body = body.append(this.getNationUrlMarkup());
        if (getAlliance_id() != 0) {
            body = body.append(IShrink.of("", " | " + getAllianceUrlMarkup()));
        }
        body = body.append("\n").append(this.toCityMilMarkdown());
        return body;
    }

    @Command(desc = "Get units at a specific date")
    @RolePermission(Roles.MILCOM)
    public int getUnitsAt(MilitaryUnit unit, @Timestamp long date) {
        long now = System.currentTimeMillis();
        if (date > now - TimeUnit.MILLISECONDS.toMillis(15)) {
            return getUnits(unit);
        }
        return Locutus.imp().getNationDB().getMilitary(this, unit, date);
    }

    @Command(desc = "If a unit was bought today")
    public boolean hasUnitBuyToday(MilitaryUnit unit) {
        long turnDayStart;
        long turn;
        if (getSnapshot() != null) {
            turn = TimeUtil.getTurn(getSnapshot());
            turnDayStart = TimeUtil.getTimeFromTurn(turn - turn % 12);
        } else {
            turn = TimeUtil.getTurn();
        }
        turnDayStart = TimeUtil.getTimeFromTurn(turn - turn % 12);
        return Locutus.imp().getNationDB().hasBought(this, unit, turnDayStart, getSnapshot());
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
    public double daysSinceLastSoldierBuy(ValueStore store) {
        Long result = getLastUnitBuy(store, MilitaryUnit.SOLDIER);
        long now = getSnapshot() == null ? System.currentTimeMillis() : getSnapshot();
        return result == null ? Long.MAX_VALUE : (((double) (now - result)) / TimeUnit.DAYS.toMillis(1));
    }

    @Command(desc = "Days since last tank purchase")
    public double daysSinceLastTankBuy(ValueStore store) {
        Long result = getLastUnitBuy(store, MilitaryUnit.TANK);
        long now = getSnapshot() == null ? System.currentTimeMillis() : getSnapshot();
        return result == null ? Long.MAX_VALUE : (((double) (now - result)) / TimeUnit.DAYS.toMillis(1));
    }

    @Command(desc = "Days since last aircraft purchase")
    public double daysSinceLastAircraftBuy(ValueStore store) {
        Long result = getLastUnitBuy(store, MilitaryUnit.AIRCRAFT);
        long now = getSnapshot() == null ? System.currentTimeMillis() : getSnapshot();
        return result == null ? Long.MAX_VALUE : (((double) (now - result)) / TimeUnit.DAYS.toMillis(1));
    }

    @Command(desc = "Days since last ship purchase")
    public double daysSinceLastShipBuy(ValueStore store) {
        Long result = getLastUnitBuy(store, MilitaryUnit.SHIP);
        long now = getSnapshot() == null ? System.currentTimeMillis() : getSnapshot();
        return result == null ? Long.MAX_VALUE : (((double) (now - result)) / TimeUnit.DAYS.toMillis(1));
    }

    @Command(desc = "Days since last spy purchase")
    public double daysSinceLastSpyBuy(ValueStore store) {
        Long result = getLastUnitBuy(store, MilitaryUnit.SPIES);
        long now = getSnapshot() == null ? System.currentTimeMillis() : getSnapshot();
        return result == null ? Long.MAX_VALUE : (((double) (now - result)) / TimeUnit.DAYS.toMillis(1));
    }

    @Command(desc = "Days since last ship purchase")
    public double daysSinceLastNukeBuy(ValueStore store) {
        Long result = getLastUnitBuy(store, MilitaryUnit.NUKE);
        long now = getSnapshot() == null ? System.currentTimeMillis() : getSnapshot();
        return result == null ? Long.MAX_VALUE : (((double) (now - result)) / TimeUnit.DAYS.toMillis(1));
    }

    @Command(desc = "Days since last missile purchase")
    public double daysSinceLastMissileBuy(ValueStore store) {
        Long result = getLastUnitBuy(store, MilitaryUnit.MISSILE);
        long now = getSnapshot() == null ? System.currentTimeMillis() : getSnapshot();
        return result == null ? Long.MAX_VALUE : (((double) (now - result)) / TimeUnit.DAYS.toMillis(1));
    }

    public List<Map.Entry<Long, Integer>> getUnitHistory(MilitaryUnit unit) {
        return Locutus.imp().getNationDB().getMilitaryHistory(this, unit, getSnapshot());
    }

    @Command(desc = "Get unix timestamp of when a unit was purchased last")
    @RolePermission(Roles.MILCOM)
    public Long getLastUnitBuy(ValueStore store, MilitaryUnit unit) {
        ScopedPlaceholderCache<DBNation> scoped = PlaceholderCache.getScoped(store, DBNation.class, "getLastUnitBuy");
        Map<MilitaryUnit, Long> byUnit = scoped.getMap(this,
        (ThrowingFunction<List<DBNation>, Map<DBNation, Map<MilitaryUnit, Long>>>) f -> {
            Set<Integer> nationIds = new IntOpenHashSet(f.size());
            for (DBNation nation : f) nationIds.add(nation.getNation_id());
            Map<Integer, Map<MilitaryUnit, Long>> resultById = Locutus.imp().getNationDB().getLastMilitaryBuyByNationId(nationIds);
            Map<DBNation, Map<MilitaryUnit, Long>> result = new LinkedHashMap<>();
            for (DBNation nation : f) {
                result.put(nation, resultById.get(nation.getNation_id()));
            }
            return result;
        });
        return byUnit == null ? null : byUnit.get(unit);
    }

    public Map<Long, Integer> getUnitPurchaseHistory(MilitaryUnit unit, long cutoff) {
        HashMap<Long, Integer> unitsLost = new HashMap<>();

        List<Map.Entry<Long, Integer>> history = getUnitHistory(unit);

        if (unit == MilitaryUnit.NUKE || unit == MilitaryUnit.MISSILE) {
            Locutus.imp().getWarDb().iterateAttacks(getNation_id(), cutoff, (war, attack) -> {
                boolean isAttacker = attack.getAttacker_id() == data()._nationId();
                { // losses
                    int amt;
                    if (isAttacker) {
                        amt = attack.getAttUnitLosses(unit);
                    } else {
                        amt = attack.getDefUnitLosses(unit);
                    }
                    long turn = TimeUtil.getTurn(attack.getDate());
                    if (amt > 0) {
                        unitsLost.merge(turn, amt, Integer::sum);
                    }
                }
                {
                    Map.Entry<Long, Integer> toAdd = new KeyValue<>(attack.getDate(), getUnits(unit));
                    int i = 0;
                    for (; i < history.size(); i++) {
                        Map.Entry<Long, Integer> entry = history.get(i);
                        long diff = Math.abs(entry.getKey() - attack.getDate());
                        if (diff < 5 * 60 * 1000) return;

                        toAdd.setValue(entry.getValue());
                        if (entry.getKey() < toAdd.getKey()) {
                            history.add(i, toAdd);
                            return;
                        }
                    }
                    history.add(i, toAdd);
                }
            });
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
            previous = new KeyValue<>(entry);
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

    @Command(desc = "Get the number of active wars with a list of nations")
    public int getFighting(@NoFormat Set<DBNation> nations) {
        if (nations == null) return getNumWars();
        int count = 0;
        for (DBWar war : getActiveWars()) {
            DBNation other = war.getNation(!war.isAttacker(this));
            if (nations.contains(other)) {
                count++;
            }
        }
        return count;
    }

    @Command(desc = "Cost of buying up to a certain infra level")
    public double getBuyInfraCost(double toInfra, @Switch("u") boolean forceUrbanization, @Switch("aec") boolean forceAEC, @Switch("cfce") boolean forceCFCE, @Switch("gsa") boolean forceGSA, @Switch("bda") boolean forceBDA) {
        double total = 0;
        for (Map.Entry<Integer, JavaCity> entry : getCityMap(false).entrySet()) {
            double cityInfra = entry.getValue().getInfra();
            if (cityInfra < toInfra) {
                total += PW.City.Infra.calculateInfra(cityInfra, toInfra,
                        hasProject(Projects.ADVANCED_ENGINEERING_CORPS) || forceAEC,
                        hasProject(Projects.CENTER_FOR_CIVIL_ENGINEERING) || forceCFCE,
                        getDomesticPolicy() == DomesticPolicy.URBANIZATION || forceUrbanization,
                        hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY) || forceGSA,
                        hasProject(Projects.BUREAU_OF_DOMESTIC_AFFAIRS) || forceBDA);
            }
        }
        return total;
    }

    @Command(desc = "Cost of buying up to a certain land level")
    public double getBuyLandCost(double toLand, @Switch("ra") boolean forceRAPolicy, @Switch("aec") boolean forceAEC, @Switch("ala") boolean forceALA, @Switch("gsa") boolean forceGSA, @Switch("bda") boolean forceBDA) {
        boolean ra = getDomesticPolicy() == DomesticPolicy.RAPID_EXPANSION || forceRAPolicy;
        boolean aec = hasProject(Projects.ADVANCED_ENGINEERING_CORPS) || forceAEC;
        boolean ala = hasProject(Projects.ARABLE_LAND_AGENCY) || forceALA;
        boolean gsa = hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY) || forceGSA;
        boolean bda = hasProject(Projects.BUREAU_OF_DOMESTIC_AFFAIRS) || forceBDA;
        double total = 0;
        for (Map.Entry<Integer, JavaCity> entry : getCityMap(false).entrySet()) {
            double cityLand = entry.getValue().getLand();
            if (cityLand < toLand) {
                total += PW.City.Land.calculateLand(cityLand, toLand, ra, aec, ala, gsa, bda);
            }
        }
        return total;
    }

    @Command(desc = "Get the number of active offensive wars with a list of nations")
    public int getAttacking(@NoFormat Set<DBNation> nations) {
        if (nations == null) return getNumWars();
        int count = 0;
        for (DBWar war : getActiveOffensiveWars()) {
            DBNation other = war.getNation(false);
            if (nations.contains(other)) {
                count++;
            }
        }
        return count;
    }

    @Command(desc = "Get the number of active defensive wars with a list of nations")
        public int getDefending(@NoFormat Set<DBNation> nations) {
        if (nations == null) return getNumWars();
        int count = 0;
        for (DBWar war : getActiveDefensiveWars()) {
            DBNation other = war.getNation(true);
            if (nations.contains(other)) {
                count++;
            }
        }
        return count;
    }

    @Command(desc = "Can perform a spy attack against a nation of score")
    public boolean canSpyOnScore(double score) {
        double min = PW.getAttackRange(true, false, true, this.data()._score());
        double max = PW.getAttackRange(true, false, false, this.data()._score());
        return score >= min && score <= max;
    }

    @Command(desc = "Can be spied by a nation of score")
    public boolean canBeSpiedByScore(double score) {
        double min = PW.getAttackRange(false, false, true, this.data()._score());
        double max = PW.getAttackRange(false, false, false, this.data()._score());
        return score >= min && score <= max;
    }

    @Command(desc = "Can declare war on a nation of score")
        public boolean canDeclareOnScore(double score) {
        double min = PW.getAttackRange(true, true, true, this.data()._score());
        double max = PW.getAttackRange(true, true, false, this.data()._score());
        return score >= min && score <= max;
    }

    @Command(desc = "Can be declared on by a nation of score")
    public boolean canBeDeclaredOnByScore(double score) {
        double min = PW.getAttackRange(false, true, true, this.data()._score());
        double max = PW.getAttackRange(false, true, false, this.data()._score());
        return score >= min && score <= max;
    }

    @Command(desc = "If this nation is in a nation list")
    public boolean isIn(@NoFormat Set<DBNation> nations) {
        return nations.contains(this);
    }

    @Command(desc = "If this nation is in the enemies coalition")
    public boolean isEnemy(@Me GuildDB db) {
        if (data()._allianceId() == 0) return false;
        return db.getCoalition(Coalition.ENEMIES).contains(data()._allianceId());
    }

    public long getRerollDate() {
        List<DBNation> nations = new ArrayList<>(Locutus.imp().getNationDB().getAllNations());
        int previousNationId = -1;
        for (DBNation nation : nations) {
            if (nation.getNation_id() < data()._nationId()) {
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
            if (nation.data()._nationId() <= data()._nationId()) continue;
            if (nation.data()._date() < data()._date()) {
                DBNation previous = nations.get(i - 1);
                if (previous.data()._date() < nation.data()._date()) {
                    // valid
                    minDate = Math.min(nation.data()._date(), minDate);
                }
            }
        }
        if (Math.abs(data()._date() - minDate) > TimeUnit.DAYS.toMillis(10)) {
            return Long.MAX_VALUE;
        }
        return minDate;
    }

    @Command(desc = "Get the update timezone (in hours from UTC)\n" +
            "-1 = No data available")
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public double getUpdateTZ(@Me GuildDB db, ValueStore store) {
        if (getPositionEnum().id < Rank.APPLICANT.id || !db.isAllianceId(data()._allianceId())) {
            return -1;
        }
        ScopedPlaceholderCache<DBNation> scoped = PlaceholderCache.getScoped(store, DBNation.class, "getUpdateTZ");
        List<DBNation> nations = scoped.getList(this);
        Map<Integer, Double> update = scoped.getGlobal(() -> db.getAllianceList().subList(nations).fetchUpdateTz(new HashSet<>(nations)));
        return update.getOrDefault(data()._nationId(), -1d);
    }

    @Command(desc = "Get free offensive spy ops available\n" +
            "-1 = No data available")
    @RolePermission(Roles.MILCOM)
    public int getFreeOffSpyOps(@Me GuildDB db, ValueStore store) {
        if (getPositionEnum().id < Rank.APPLICANT.id || !db.isAllianceId(data()._allianceId())) {
            return -1;
        }
        ScopedPlaceholderCache<DBNation> scoped = PlaceholderCache.getScoped(store, DBNation.class, "getFreeOffSpyOps");
        List<DBNation> nations = scoped.getList(this);
        Map<DBNation, Integer> update = scoped.getGlobal(() -> db.getAllianceList().subList(nations).updateOffSpyOps());
        return update.getOrDefault(this, -1);
    }

    public Map<Integer, Long> findDayChange() {
        MilitaryUnit[] units = new MilitaryUnit[]{MilitaryUnit.SOLDIER, MilitaryUnit.TANK, MilitaryUnit.AIRCRAFT, MilitaryUnit.SHIP, MilitaryUnit.MISSILE, MilitaryUnit.NUKE};
        int[] caps = new int[units.length];
        caps[0] = Buildings.BARRACKS.getUnitDailyBuy() * Buildings.BARRACKS.cap(this::hasProject) * getCities();
        caps[1] = Buildings.FACTORY.getUnitDailyBuy() * Buildings.FACTORY.cap(this::hasProject) * getCities();
        caps[2] = Buildings.HANGAR.getUnitDailyBuy() * Buildings.HANGAR.cap(this::hasProject) * getCities();
        caps[3] = Buildings.DRYDOCK.getUnitDailyBuy() * Buildings.DRYDOCK.cap(this::hasProject) * getCities();
        caps[4] = MilitaryUnit.MISSILE.getMaxPerDay(getCities(), this::hasProject);
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
            Set<Integer> bestOffset = new IntOpenHashSet();
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
        return sendDM(msg, null);
    }

    public boolean sendDM(String msg, Consumer<String> errors) {
        User user = getUser();
        if (user == null) return false;

        try {
            RateLimitUtil.queue(RateLimitUtil.complete(user.openPrivateChannel()).sendMessage(msg));
        } catch (Throwable e) {
            if (errors != null) {
                errors.accept(e.getMessage());
            }
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private DBNationCache getCache() {
        return getCache(true);
    }

    private DBNationCache getCache(boolean create) {
        DBNationCache cache = data()._cache();
        if (cache == null && create) {
            edit().setCache(cache = new DBNationCache());
        }
        return cache;
    }

    @Command(desc = "Total project slots (used + unused)")
    public int projectSlots() {
        int warBonus = this.data()._warsWon() + this.data()._warsLost() >= 100 ? 1 : 0;
        int projectBonus = (hasProject(Projects.RESEARCH_AND_DEVELOPMENT_CENTER) ? 2 : 0) +
                (hasProject(Projects.MILITARY_RESEARCH_CENTER) ? 2 : 0);
        return ((int) getInfra() / 5000) + 1 + warBonus + projectBonus;
    }

//    public void setSpy_kills(int spy_kills) {
//        this.spy_kills = spy_kills;
//    }
//
//    public void setSpy_casualties(int spy_casualties) {
//        long now = getSnapshot() == null ? System.currentTimeMillis() : getSnapshot();
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
        return data()._warsWon();
    }

    @Command(desc = "Total wars lost")
    public int getWars_lost() {
        return data()._warsLost();
    }

    public void setWars_won(int wars_won) {
        this.edit().setWars_won(wars_won);
    }

    public void setWars_lost(int wars_lost) {
        this.edit().setWars_lost(wars_lost);
    }

    @Command(desc = "Number of wars matching a filter")
    public int countWars(@NoFormat Predicate<DBWar> warFilter) {
        return (int) getWars().stream().filter(warFilter).count();
    }

    public AttackCost getWarCost(boolean buildings, boolean ids, boolean victories, boolean wars, boolean attacks) {
        AttackCost cost = new AttackCost(getName(), "*", buildings, ids, victories, wars, attacks);
        Locutus.imp().getWarDb().iterateAttacks(data()._nationId(), 0L, (war, attack) -> {
            cost.addCost(attack, war, (w, a) -> a.getAttacker_id() == data()._nationId(), (w, b) -> b.getDefender_id() == data()._nationId());
        });
        return cost;
    }

    @Command(desc = "Total money looted")
    @RolePermission(Roles.MILCOM)
    public double getMoneyLooted() {
        double[] total = {0};
        Locutus.imp().getWarDb().iterateAttacks(data()._nationId(), 0, getSnapshot() == null ? Long.MAX_VALUE : getSnapshot(), (war, attack) -> {
            if (attack.getAttacker_id() == data()._nationId()) {
                double[] loot = attack.getLoot();
                if (loot != null) total[0] += ResourceType.convertedTotal(loot);
            }
        });
        return total[0];
    }

    public void setCityTimer(Long timer) {
        this.edit().setCityTimer(timer);
    }

    public void setProjectTimer(Long timer) {
        this.edit().setProjectTimer(timer);
    }

    @Command(desc = "Absolute turn when full espionage slots will reset")
    public long getEspionageFullTurn() {
        return data()._espionageFull();
    }

    @Command(desc = "If espionage slots are full")
    public boolean isEspionageFull() {
        return this.getVm_turns() > 0 || this.getEspionageFullTurn() > TimeUtil.getTurn(getSnapshot());
    }

    @Command(desc = "If there are remaining espionage slots")
    public boolean isEspionageAvailable() {
        return !isEspionageFull();
    }

    @Command(desc = "The turn of the day (0-11) when their day change (DC) unit rebuy is available")
    public int getDc_turn() {
        return data()._dcTurn();
    }

    public void setDc_turn(int dc_turn) {
        this.edit().setDc_turn(dc_turn);
    }

    @Command(desc = "Turns remaining until their day change (DC)")
    public int getTurnsTillDC() {
        int currentTurn = (int) TimeUtil.getDayTurn();
        if (currentTurn >= data()._dcTurn()) return (data()._dcTurn() + 12) - currentTurn;
        return data()._dcTurn() - currentTurn;
    }

    @Command
    public int getTurnsFromDC() {
        int currentTurnMod = (int) TimeUtil.getDayTurn() % 12;
        if (currentTurnMod >= data()._dcTurn()) {
            return currentTurnMod - data()._dcTurn();
        } else {
            return (currentTurnMod + 12) - data()._dcTurn();
        }
    }

    @Command(desc = "Are any cities powered")
    public boolean isPowered() {
        for (Map.Entry<Integer, JavaCity> entry : getCityMap(false, false,false).entrySet()) {
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
        DBWar last = Locutus.imp().getWarDb().getLastOffensiveWar(data()._nationId(), getSnapshot());
        if (last != null) {
            long now = getSnapshot() == null ? System.currentTimeMillis() : getSnapshot();
            long diff = now - last.getDate();
            return ((double) diff) / TimeUnit.DAYS.toMillis(1);
        }
        return Integer.MAX_VALUE;
    }

    @Command(desc = "Days since last defensive war")
    public double daysSinceLastDefensiveWarLoss() {
        long maxDate = 0;
        for (DBWar war : getWars()) {
            if (war.getDefender_id() == data()._nationId() && war.getStatus() == WarStatus.ATTACKER_VICTORY) {
                maxDate = Math.max(maxDate, war.getDate());
            }
        }
        long now = getSnapshot() == null ? System.currentTimeMillis() : getSnapshot();
        return maxDate == 0 ? Integer.MAX_VALUE : ((double) (now - maxDate)) / TimeUnit.DAYS.toMillis(1);
    }

    @Command(desc = "Days since last war")
    public double daysSinceLastWar() {
        if (getNumWars() > 0) return 0;
        DBWar last = Locutus.imp().getWarDb().getLastWar(data()._nationId(), getSnapshot());
        if (last != null) {
            long now = getSnapshot() == null ? System.currentTimeMillis() : getSnapshot();
            long diff = now - last.getDate();
            return ((double) diff) / TimeUnit.DAYS.toMillis(1);
        }
        return Integer.MAX_VALUE;
    }

    public boolean updateEspionageFull() {
        if (getVm_turns() > 0) return true;

        long lastUpdated = data()._espionageFull() == 0 ? 0 : Math.abs(data()._espionageFull());
        long dc = TimeUtil.getTurn() - TimeUtil.getDayTurn();

        Auth auth = Locutus.imp().getRootAuth();
        Callable<Long> task = new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                String baseUrl = Settings.PNW_URL() + "/nation/espionage/eid=";
                String url = baseUrl + getNation_id();
                String html = auth.readStringFromURL(PagePriority.ESPIONAGE_FULL_UNUSED, url, Collections.emptyMap());
                if (html.contains("This target has already had 3 espionage operations executed upon them today.")) {
                    return TimeUtil.getTurn() + getTurnsTillDC();
                }
                return 0L;
            }
        };
        try {
            this.edit().setEspionageFull(PW.withLogin(task, auth));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this.data()._espionageFull() > TimeUtil.getTurn();
    }

    public void setBeigeTimer(long time) {
        this.edit().setBeigeTimer(time);
    }

    @Override
    @Command(desc = "The nation id")
    public int getId() {
        return data()._nationId();
    }

    @Override
    public boolean isAlliance() {
        return false;
    }

    @Override
    @Command(desc = "The nation name")
    public String getName() {
        return data()._nation();
    }

    public String getDeclareUrl() {
        return Settings.PNW_URL() + "/nation/war/declare/id=" + getNation_id();
    }

    @Command(desc = "Average land per city")
    public double getAvgLand() {
        double total = 0;
        Collection<DBCity> cities = _getCitiesV3().values();
        for (DBCity city : cities) {
            total += city.getLand();
        }
        return total / cities.size();
    }

    @Command(desc = "Total land in their cities")
    public double getTotalLand() {
        double total = 0;
        Collection<DBCity> cities = _getCitiesV3().values();
        for (DBCity city : cities) {
            total += city.getLand();
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
            if (this.data()._espionageFull() > 0 && isTurnChange) {
                int turn = (int) TimeUtil.getDayTurn();
                if (turn != this.data()._dcTurn()) {
                    this.edit().setDc_turn(turn);
                }
            }
            this.edit().setEspionageFull(0);
        } else {
            long turn = TimeUtil.getTurn();
            if (data()._espionageFull() == 0 || data()._espionageFull() <= turn) {
                this.edit().setEspionageFull(turn + getTurnsTillDC());
            }
        }
    }

    @Command(desc = "ID of their in-game tax rate bracket")
    public int getTax_id() {
        return data()._taxId();
    }

    public void setTax_id(int tax_id) {
        this.edit().setTax_id(tax_id);
    }

    public void setGNI(double gni) {
        this.edit().setGni(gni);
    }

    public GuildDB getGuildDB() {
        if (data()._allianceId() == 0) return null;
        return Locutus.imp().getGuildDBByAA(data()._allianceId());
    }

    public Map.Entry<CommandResult, List<StringMessageBuilder>> runCommandInternally(Guild guild, User user, String command) {
        if (user == null) return new KeyValue<>(CommandResult.ERROR, StringMessageBuilder.list(null, "No user for: " + getMarkdownUrl()));

        StringMessageIO output = new StringMessageIO(user, guild);
        CommandResult type;
        String result;
        try {
            Locutus.imp().getCommandManager().run(guild, output, user, command, false, true);
            type = CommandResult.SUCCESS;
            return new KeyValue<>(type, output.getMessages());
        } catch (Throwable e) {
            result = StringMan.stripApiKey(e.getMessage());
            type = CommandResult.ERROR;
            return new KeyValue<>(type, StringMessageBuilder.list(user, result));
        }
    }

    public long lastActiveMs(long timestamp) {
        long currentTurn = TimeUtil.getTurn(timestamp);
        long lastTurn = Locutus.imp().getNationDB().getLastActiveTurn(getId(), currentTurn);
        long diffTurn = currentTurn - lastTurn;
        if (getColor() != NationColor.GRAY && getColor() != NationColor.BEIGE) {
            diffTurn = Math.min(diffTurn, 12 * 5);
        }
        return TimeUtil.getTimeFromTurn(currentTurn - diffTurn);
    }

    @Command(desc = "The time in epoch milliseconds when this nation was last active")
    public long lastActiveMs() {
        return data()._lastActiveMs();
    }

    public void setLastActive(long epoch) {
        this.edit().setLast_active(epoch);
    }

    public void update(boolean bulk) {
        Locutus.imp().runEventsAsync(events ->
        Locutus.imp().getNationDB().updateNations(List.of(data()._nationId()), bulk, events));
    }

    public void updateCities(boolean bulk) {
        Locutus.imp().runEventsAsync(events ->
                Locutus.imp().getNationDB().updateCitiesOfNations(Set.of(data()._nationId()), true, bulk, events));
    }

    public double[] projectCost(Project project) {
        return project.cost(getDomesticPolicy() == DomesticPolicy.TECHNOLOGICAL_ADVANCEMENT, hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY), hasProject(Projects.BUREAU_OF_DOMESTIC_AFFAIRS));
    }

    public double getGrossModifier() {
        return getGrossModifier(false);
    }
    public double getGrossModifier(boolean noFood) {
        return getGrossModifier(noFood, getDomesticPolicy() == DomesticPolicy.OPEN_MARKETS, hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY), hasProject(Projects.BUREAU_OF_DOMESTIC_AFFAIRS));
    }

    public static double getGrossModifier(boolean noFood, boolean openMarkets, boolean gsa, boolean bda) {
        double grossModifier = 1;
        if (openMarkets) {
            grossModifier += 0.01;
            if (gsa) {
                grossModifier += 0.005;
            }
            if (bda) {
                grossModifier += 0.0025;
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
            if (hasProject(Projects.BUREAU_OF_DOMESTIC_AFFAIRS)) {
                factor -= 0.0125;
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

    @Command(desc = "Value of the projects this nation has\n" +
            "Cost reduction policies are not included")
    public double projectValue() {
        double value = 0;
        for (Project project : Projects.values) {
            if (hasProject(project)) {
                value += project.getMarketValue();
            }
        }
        return value;
    }

    @Command(desc = "Value of the buildings this nation has\n" +
            "Cost reduction policies are not included")
    public double buildingValue() {
        double value = 0;
        for (DBCity city : _getCitiesV3().values()) {
            value += city.getBuildingMarketCost();
        }
        return value;
    }

    @Command(desc = "Value of the military units this nation has\n" +
            "Cost reduction policies are not included")
    public double unitValue() {
        double total = 0;
        for (MilitaryUnit unit : MilitaryUnit.values) {
            int amt = getUnits(unit);
            if (amt > 0) total += unit.getBaseMonetaryValue(amt);
        }
        return total;
    }

    @Command(desc = "Value of the land this nation has\n" +
            "Cost reduction policies are not included")
    public double landValue() {
        double value = 0;
        for (DBCity city : _getCitiesV3().values()) {
            value += PW.City.Land.calculateLand(0, city.getLand());
        }
        return value;
    }

    @Command(desc = "Value of the infrastructure this nation has\n" +
            "Cost reduction policies are not included")
    public double infraValue() {
        double value = 0;
        for (DBCity city : _getCitiesV3().values()) {
            value += PW.City.Infra.calculateInfra(0, city.getInfra());
        }
        return value;
    }

    // city value
    @Command(desc = "Value of the cities this nation has\n" +
            "Cost reduction policies are not included")
    public double cityValue() {
        return PW.City.cityCost(0, data()._cities(), false, false, false, false, false, false);
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
            case GROUND, AIRSTRIKE_INFRA, AIRSTRIKE_SOLDIER, AIRSTRIKE_TANK, AIRSTRIKE_MONEY, AIRSTRIKE_SHIP, AIRSTRIKE_AIRCRAFT, NAVAL, NAVAL_GROUND, NAVAL_AIR, NAVAL_INFRA -> true;
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
            case GROUND, AIRSTRIKE_INFRA, AIRSTRIKE_SOLDIER, AIRSTRIKE_TANK, AIRSTRIKE_MONEY, AIRSTRIKE_SHIP, AIRSTRIKE_AIRCRAFT, NAVAL, NAVAL_GROUND, NAVAL_AIR, NAVAL_INFRA -> true;
            default -> false;
        };
        return switch (getWarPolicy()) {
            case TURTLE -> isGroundAirOrNaval ? 0.9 : 1;
            case MONEYBAGS -> 1.05;
            case COVERT, ARCANE -> isGroundAirOrNaval ? 1.05 : 1;
            default -> 1;
        };
    }

    @Command(desc = "If this nation is in war declare range of the current attacking nation")
    public boolean isInWarRange(@Default @Me DBNation target) {
        return target.getScore() > getScore() * 0.75 && target.getScore() < getScore() * 1.25;
    }

    public TaxBracket getTaxBracket() {
        return new TaxBracket(data()._taxId(), data()._allianceId(), "", -1, -1, 0);
    }

    @Command(desc = "Their gross income (GNI)")
    public double getGNI() {
        return data()._gni();
    }

    @Command(desc = "Has any bounties placed on them to defeat them in war or detonate a nuke")
    public boolean hasBounty() {
        return !getBounties().isEmpty();
    }

    @Command(desc = "Has any bounties placed on them to defeat them in war")
    public boolean hasWarBounty() {
        return !getBounties().isEmpty();
    }

    @Command(desc = "Has any bounties placed on them to detonate a nuke")
    public boolean hasNukeBounty() {
        return getBounties().stream().anyMatch(f -> f.getType() == WarType.NUCLEAR);
    }

    @Command(desc = "Sum of all bounty values placed on them")
    public double totalBountyValue() {
        return getBounties().stream().mapToLong(DBBounty::getAmount).sum();
    }

    @Command(desc = "Maximum total bounty placed on them of any type")
    public double maxBountyValue() {
        Set<DBBounty> bounties = getBounties();
        if (bounties.isEmpty()) return 0;
        if (bounties.size() == 1) return bounties.iterator().next().getAmount();
        Map<WarType, Double> sumByType = bounties.stream().collect(Collectors.groupingBy(DBBounty::getType, Collectors.summingDouble(DBBounty::getAmount)));
        return sumByType.values().stream().max(Double::compareTo).orElse(0D);
    }

    @Command(desc = "Maximum total bounty placed on them of any war type")
    public double maxWarBountyValue() {
        Set<DBBounty> bounties = Locutus.imp().getWarDb().getBounties(data()._nationId());
        bounties.removeIf(f -> f.getType() == WarType.NUCLEAR);
        if (bounties.isEmpty()) return 0;
        if (bounties.size() == 1) return bounties.iterator().next().getAmount();
        Map<WarType, Double> sumByType = bounties.stream().collect(Collectors.groupingBy(DBBounty::getType, Collectors.summingDouble(DBBounty::getAmount)));
        return sumByType.values().stream().max(Double::compareTo).orElse(0D);
    }

    @Command(desc = "Sum of all nuke bounties placed on them")
    public double nukeBountyValue() {
        return Locutus.imp().getWarDb().getBounties(data()._nationId()).stream().filter(f -> f.getType() == WarType.NUCLEAR).mapToLong(DBBounty::getAmount).sum();
    }

    @Command(desc = "Sum of all raid bounties placed on them")
    public double raidBountyValue() {
        return Locutus.imp().getWarDb().getBounties(data()._nationId()).stream().filter(f -> f.getType() == WarType.RAID).mapToLong(DBBounty::getAmount).sum();
    }

    @Command(desc = "Sum of all ordinary bounties placed on them")
    public double ordinaryBountyValue() {
        return Locutus.imp().getWarDb().getBounties(data()._nationId()).stream().filter(f -> f.getType() == WarType.ORD).mapToLong(DBBounty::getAmount).sum();
    }

    @Command(desc = "Sum of all attrition bounties placed on them")
    public double attritionBountyValue() {
        return Locutus.imp().getWarDb().getBounties(data()._nationId()).stream().filter(f -> f.getType() == WarType.ATT).mapToLong(DBBounty::getAmount).sum();
    }

    public Member getMember(GuildDB db) {
        if (db != null) {
            User user = getUser();
            if (user != null) {
                return db.getGuild().getMember(user);
            }
        }
        return null;
    }

    @Command(desc = "Daily revenue of a nation")
    public Map<ResourceType, Double> revenue(@Default Integer turns,
                                             @Switch("c") boolean no_cities,
                                             @Switch("m") boolean no_military,
                                            @Switch("t") boolean no_trade_bonus,
                                            @Switch("b") boolean no_new_bonus,
                                            @Switch("f") boolean no_food,
                                            @Switch("p") boolean no_power,
                                            @Switch("r") Double treasure_bonus) {
        if (turns == null) turns = 12;
        if (treasure_bonus == null) treasure_bonus = getTreasureBonusPct();
        double[] rss = getRevenue(turns, !no_cities, !no_military, !no_trade_bonus, !no_new_bonus, no_food, no_power, treasure_bonus, false);
        return ResourceType.resourcesToMap(rss);
    }

    @Command(desc = "City refund from city planning projects")
    public double getCityRefund() {
        return data()._costReduction();
    }

    @Command(desc = "The level this nation has for a specified research")
    public int getResearch(Research research) {
        return research.getLevel(getResearchBits());
    }

    // research bits
    @Command(desc = "The raw data of all the research levels this nation has")
    public int getResearchBits() {
        return data()._researchBits();
    }

    public void updateResearch() throws IOException {
        String url = this.getUrl();
        Document doc = Jsoup.connect(url).get();
        NationUpdateProcessor.NationUpdate update = NationUpdateProcessor.updateNation(this, doc);
        Map<Research, Integer> research = update.research;;
        int bits = Research.toBits(research);
        this.edit().setResearchBits(bits);
    }

    @Command(desc = "The research levels this nation has")
    public Map<Research, Integer> getResearchLevels() {
        Map<Research, Integer> levels = new EnumMap<>(Research.class);
        for (Research research : Research.values) {
            int lvl = getResearch(research);
            if (lvl > 0) {
                levels.put(research, lvl);
            }
        }
        return levels;
    }

    @Command
    public double getResearchCostFactor() {
        return Research.costFactor(hasProject(Projects.MILITARY_DOCTRINE));
    }

    @Command(desc = "The number of research this nation has")
    public int getNumResearch() {
        return getResearchLevels().values().stream().mapToInt(Integer::intValue).sum();
    }

    @Command(desc = "Resource cost of all research this nation has")
    public Map<ResourceType, Double> getResearchCost() {
        return Research.cost(Collections.emptyMap(), getResearchLevels(), getResearchCostFactor());
    }

    @Command(desc = "Market value of all the research this nation has")
    public double  getResearchValue() {
        return ResourceType.convertedTotal(getResearchCost());
    }
    @Command(desc = """
            The total value of all the assets this nation has
            Includes projects, infra, land, cities, buildings, units, and research
            Does not factor in cost reduction policies or projects""")
    public double costConverted() {
        int total = 0;
        total += projectValue();
        total += infraValue();
        total += landValue();
        total += cityValue();
        total += buildingValue();
        total += unitValue();
        total += getResearchValue();
        return total;
    }

    public Predicate<Project> hasProjectPredicate() {
        return Projects.optimize(this::hasProject);
    }
}
