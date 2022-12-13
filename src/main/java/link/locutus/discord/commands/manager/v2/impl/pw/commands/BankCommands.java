package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.bank.Disperse;
import link.locutus.discord.commands.manager.v2.binding.annotation.AllianceDepositLimit;
import link.locutus.discord.commands.manager.v2.binding.annotation.Arg;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.NationDepositLimit;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timediff;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasOffshore;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.util.offshore.Grant;
import link.locutus.discord.db.GuildHandler;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.JsonUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.sheet.templates.TransferSheet;
import link.locutus.discord.util.task.DepositRawTask;
import link.locutus.discord.util.task.balance.BankWithTask;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import org.json.JSONObject;
import rocker.guild.ia.message;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static link.locutus.discord.util.PnwUtil.convertedTotal;
import static link.locutus.discord.util.PnwUtil.resourcesToString;

public class BankCommands {

//    @Command(desc = "Find the ROI for various changes you can make to your nation, with a specified timeframe\n" +
//            "(typically how long you expect the changes, or peacetime to last)\n" +
//            "e.g. `{prefix}ROI @Borg 30`\n" +
//            "Add `-r` to run it recursively for various infra levels")
//    @RolePermission(Roles.MEMBER)
//    public String roi()

    @Command(desc = "Queue a transfer offshore (with authorization)\n" +
            "`aa-warchest` is how much to leave in the AA bank - in the form `{money=1,food=2}`\n" +
            "`#note` is what note to use for the transfer (defaults to deposit)")
    @RolePermission(value = {Roles.MEMBER, Roles.ECON, Roles.ECON_LOW_GOV})
    @HasOffshore
    @IsAlliance
    public String offshore(@Me Member member, @Me GuildDB db, @Default DBAlliance to, @Default("{}") Map<ResourceType, Double> warchest, @Default("") String note) {
        if (!Roles.ECON_LOW_GOV.has(member)) {
            if ((!Roles.MEMBER.has(member) || db.getOrNull(GuildDB.Key.MEMBER_CAN_OFFSHORE) != Boolean.TRUE)) {
                throw new IllegalArgumentException("You need ECON to offshore or to enable " + CM.settings.cmd.create(GuildDB.Key.MEMBER_CAN_OFFSHORE.name(), "true") + "");
            }
            if (note != null && !note.isEmpty()) {
                throw new IllegalArgumentException("You need ECON to use a custom note");
            }
        }
        Auth auth = db.getAuth(AlliancePermission.WITHDRAW_BANK);
        if (auth == null) return "No authentication enabled for this guild";
        OffshoreInstance offshore = db.getOffshore();
        int from = db.getOrThrow(GuildDB.Key.ALLIANCE_ID);

        if (to == null) {
            to = offshore.getAlliance();
            if (to == null || to.getAlliance_id() == db.getAlliance_id()) throw new IllegalArgumentException("Please provide an offshore to send to");
        }
        int toId = to.getAlliance_id();

        Set<Integer> offshores = db.getCoalition("offshore");
        if (!offshores.contains(toId)) return "Please add the offshore using " + CM.coalition.add.cmd.create("AA:" + toId, Coalition.OFFSHORE.name()) + "";

        double[] amountSent = ResourceType.getBuffer();

        new BankWithTask(auth, from, toId, resources -> {
            Map<ResourceType, Double> sent = new HashMap<>(resources);

            Iterator<Map.Entry<ResourceType, Double>> iterator = resources.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<ResourceType, Double> entry = iterator.next();
                entry.setValue(warchest.getOrDefault(entry.getKey(), 0d));
            }
            sent = PnwUtil.subResourcesToA(sent, resources);
            for (int i = 0; i < amountSent.length; i++) amountSent[i] = sent.getOrDefault(ResourceType.values[i], 0d);

            Locutus.imp().getRootBank().sync();

            return note.isEmpty() ? note : null;
        }).call();

        return "Sent " + PnwUtil.resourcesToString(amountSent) + " to " + to.getName();
    }

    @Command(desc = "Generate csv of war cost by nation between alliances (for reimbursement)\n" +
            "Filters out wars where nations did not perform actions")
    @RolePermission(Roles.ADMIN)
    public String warReimburseByNationCsv(Set<DBAlliance> allies, Set<DBAlliance> enemies, @Timestamp long cutoff, boolean removeWarsWithNoDefenderActions) {
        Set<Integer> allyIds = allies.stream().map(f -> f.getAlliance_id()).collect(Collectors.toSet());
        Set<Integer> enemyIds = enemies.stream().map(f -> f.getAlliance_id()).collect(Collectors.toSet());

        Map<Integer, Integer> offensivesByNation = new HashMap<>();
        Map<Integer, Integer> defensivesByNation = new HashMap<>();

        Set<DBNation> nations = Locutus.imp().getNationDB().getNations(allyIds);
        nations.removeIf(f -> f.getVm_turns() > 0 || f.getActive_m() > 10000 || f.getPosition() <= 1);
        List<DBWar> wars = new ArrayList<>(Locutus.imp().getWarDb().getWarsForNationOrAlliance(null,
                f -> (allyIds.contains(f) || enemyIds.contains(f)),
                f -> (allyIds.contains(f.attacker_aa) || allyIds.contains(f.defender_aa)) && (enemyIds.contains(f.attacker_aa) || enemyIds.contains(f.defender_aa)) && f.date > cutoff).values());

        List<DBAttack> allattacks = Locutus.imp().getWarDb().getAttacksByWars(wars);
        Map<Integer, List<DBAttack>> attacksByWar = new HashMap<>();
        for (DBAttack attack : allattacks) {
            attacksByWar.computeIfAbsent(attack.war_id, f -> new ArrayList<>()).add(attack);
        }

        if (removeWarsWithNoDefenderActions) {
            wars.removeIf(f -> {
                List<DBAttack> attacks = attacksByWar.get(f.warId);
                if (attacks == null) return true;
                boolean att1 = attacks.stream().anyMatch(g -> g.attacker_nation_id == f.attacker_id);
                boolean att2 = attacks.stream().anyMatch(g -> g.attacker_nation_id == f.defender_id);
                return !att1 || !att2;
            });
        }

        wars.removeIf(f -> {
            List<DBAttack> attacks = attacksByWar.get(f.warId);
            AttackCost cost = f.toCost(attacks);
            boolean primary = allyIds.contains(f.attacker_aa);
            return cost.convertedTotal(primary) <= 0;
        });

        for (DBWar war : wars) {
            offensivesByNation.put(war.attacker_id, offensivesByNation.getOrDefault(war.attacker_id, 0) + 1);
            defensivesByNation.put(war.defender_id, defensivesByNation.getOrDefault(war.defender_id, 0) + 1);
        }


        Map<Integer, double[]> warcostByNation = new HashMap<>();

        for (DBWar war : wars) {
            List<DBAttack> attacks = attacksByWar.get(war.warId);
            AttackCost ac = war.toCost(attacks);
            boolean primary = allies.contains(war.attacker_aa);
            double[] units = PnwUtil.resourcesToArray(ac.getUnitCost(primary));
            double[] consume = PnwUtil.resourcesToArray(ac.getConsumption(primary));

            double[] cost = PnwUtil.add(units, consume);

            double[] warCostTotal = PnwUtil.resourcesToArray(ac.getTotal(primary));
            for (ResourceType type : ResourceType.values) {
                cost[type.ordinal()] = Math.max(0, Math.min(warCostTotal[type.ordinal()], cost[type.ordinal()]));
            }

            int nationId = primary ? war.attacker_id : war.defender_id;
            double[] total = warcostByNation.computeIfAbsent(nationId, f -> ResourceType.getBuffer());
            total = PnwUtil.add(total, cost);
        }


        List<String> header = new ArrayList<>(Arrays.asList("nation", "off", "def"));
        for (ResourceType type : ResourceType.values()) {
            if (type != ResourceType.CREDITS) header.add(type.name());
        }

        List<String> lines = new ArrayList<>();
        lines.add(StringMan.join(header, ","));
        for (Map.Entry<Integer, double[]> entry : warcostByNation.entrySet()) {
            int id = entry.getKey();
            DBNation nation = DBNation.byId(id);
            if (nation == null || !allies.contains(nation.getAlliance_id()) || nation.getPosition() <= 1 || nation.getVm_turns() > 0 || nation.getActive_m() > 7200 || nation.getCities() < 10) continue;
            header.clear();
            header.add(PnwUtil.getName(id, false));
            header.add(offensivesByNation.getOrDefault(id, 0) + "");
            header.add(defensivesByNation.getOrDefault(id, 0) + "");
            double[] cost = entry.getValue();
            for (ResourceType type : ResourceType.values()) {
                if (type != ResourceType.CREDITS) header.add(MathMan.format(cost[type.ordinal()]).replace(",", ""));
            }

            lines.add(StringMan.join(header, ","));
        }
        return StringMan.join(lines, "\n");
    }

    @Command(desc = "Queue funds to be sent when your blockade lifts")
    @RolePermission(Roles.MEMBER)
    @IsAlliance
    @HasOffshore
    public String escrow(@Me GuildDB db, @Me User author, @Me DBNation me, DBNation receiver, Map<ResourceType, Double> resources, @Timediff long expireAfter, @Switch("t") boolean topUp) throws IOException {
        if (me.getNation_id() != receiver.getNation_id() && !Roles.ECON_LOW_GOV.has(author, db.getGuild())) {
            return "You do not have permisssion to send to other nations";
        }
        if (db.getOrNull(GuildDB.Key.RESOURCE_REQUEST_CHANNEL) == null) {
            return "No resource request channel set. See " + CM.settings.cmd.create(GuildDB.Key.RESOURCE_REQUEST_CHANNEL.name(), null) + "";
        }
        if (!receiver.isBlockaded()) return "You are not currently blockaded";

        long expireEpoch = System.currentTimeMillis() + expireAfter;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(out);
        dout.writeLong(expireEpoch);

        for (ResourceType type : ResourceType.values) {
            double amt = resources.getOrDefault(type, 0d);
            if (amt <= 0) return "Amount cannot be negative";
            dout.writeDouble(amt);
        }

        NationMeta meta = topUp ? NationMeta.ESCROWED_UP_TO : NationMeta.ESCROWED;
        synchronized (OffshoreInstance.BANK_LOCK) {
            db.setMeta(receiver.getNation_id(), meta, out.toByteArray());
        }
        return "Queued " + (topUp ? "top up " : "") + "transfer for `" + PnwUtil.resourcesToString(resources) + "` worth: ~$" + MathMan.format(PnwUtil.convertedTotal(resources)) + " when you next leave blockade." +
                "\nExpires in " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, expireAfter);
    }

    @Command(desc = "Disburse funds", aliases = {"disburse", "disperse"})
    @RolePermission(Roles.MEMBER)
    @IsAlliance
    @HasOffshore
    public String disburse(@Me User author, @Me GuildDB db, @Me IMessageIO io, NationList nationList, @Range(min=0, max=7) double daysDefault, @Default("#tax") String note, @Switch("d") boolean noDailyCash, @Switch("c") boolean noCash, @Switch("f") boolean force, @Switch("i") boolean ignoreInactives) throws GeneralSecurityException, IOException, ExecutionException, InterruptedException {

        Collection<String> allowedLabels = Arrays.asList("#grant", "#deposit", "#trade", "#ignore", "#tax", "#warchest", "#account");
        if (!allowedLabels.contains(note.split("=")[0])) return "Please use one of the following labels: " + StringMan.getString(allowedLabels);


        Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
        if (aaId != null) note += "=" + aaId;
        else {
            note += "=" + db.getIdLong();
        }

        Map<DBNation, Map<ResourceType, Double>> fundsToSendNations = new LinkedHashMap<>();
        Map<DBAlliance, Map<ResourceType, Double>> fundsToSendAAs = new LinkedHashMap<>();

        Collection<DBNation> nations = nationList.getNations();
        if (nations.size() != 1 || !force) {
            nations.removeIf(n -> n.getPosition() <= 1);
            nations.removeIf(n -> n.getVm_turns() != 0);
            nations.removeIf(n -> n.getActive_m() > 2880);
            nations.removeIf(n -> n.isGray() && n.getOff() == 0);
            nations.removeIf(n -> n.isBeige() && n.getCities() <= 4);
        }

        Consumer<String> updateTask = io::send;
        Consumer<String> errors = io::send;

        fundsToSendNations = new DepositRawTask(nations, aaId != null ? aaId : 0, updateTask, daysDefault, true, ignoreInactives, errors).setForce(force).call();
        if (nations.isEmpty()) {
            return "No nations found (1)";
        }

        for (Map.Entry<DBNation, Map<ResourceType, Double>> entry : fundsToSendNations.entrySet()) {
            Map<ResourceType, Double> transfer = entry.getValue();
            double cash = transfer.getOrDefault(ResourceType.MONEY, 0d);
            if (noDailyCash) cash -= daysDefault * 500000;
            if (noCash) cash = 0;
            cash = Math.max(0, cash);
            transfer.put(ResourceType.MONEY, cash);
        }

        String title = "Disperse raws " + "(" + daysDefault + " days)";

        String result = Disperse.disperse(db, fundsToSendNations, fundsToSendAAs, note, io, title);
        if (fundsToSendNations.size() > 1 || fundsToSendAAs.size() > 0) {
            result += "\n" + author.getAsMention();
        }
        return result;
    }

    @Command(desc = "Queue funds to be disbursed when your blockade lifts", aliases = {"queueDisburse", "qdisburse"})
    @RolePermission(Roles.MEMBER)
    @IsAlliance
    @HasOffshore
    public String escrowDisburse(@Me GuildDB db, @Me User author, @Me DBNation me, DBNation receiver, @Range(min=1, max=10) int days, @Timediff long expireAfter) throws IOException {
        if (days <= 0) return "Days must be positive";
        if (me.getNation_id() != receiver.getNation_id() && !Roles.ECON_LOW_GOV.has(author, db.getGuild())) {
            return "You do not have permisssion to disburse to other nations";
        }
        if (db.getOrNull(GuildDB.Key.RESOURCE_REQUEST_CHANNEL) == null) {
            return "No resource request channel set. See " + CM.settings.cmd.create(GuildDB.Key.RESOURCE_REQUEST_CHANNEL.name(), null) + "";
        }
        if (!receiver.isBlockaded()) return "You are not currently blockaded";

        long expireEpoch = System.currentTimeMillis() + expireAfter;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(out);
        dout.writeLong(expireEpoch);
        dout.writeInt(days);

        synchronized (OffshoreInstance.BANK_LOCK) {
            db.setMeta(receiver.getNation_id(), NationMeta.ESCROWED_DISBURSE_DAYS, out.toByteArray());
        }

        return "Queued disburse (" + days + " days) when you next leave blockade." +
                "\nExpires in " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, expireAfter);
    }

    @Command(desc = "Get a sheet of members and their revenue (compared to optimal)")
    @RolePermission(value = {Roles.ECON_LOW_GOV, Roles.ECON})
    @IsAlliance
    public String revenueSheet(@Me GuildDB db, Set<DBNation> nations, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException, ExecutionException, InterruptedException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, GuildDB.Key.REVENUE_SHEET);
        }

        Set<Integer> ids = db.getAllianceIds(false);
        int sizeOriginal = nations.size();
        nations.removeIf(f -> f.getPosition() <= Rank.APPLICANT.id || !ids.contains(f.getAlliance_id()));
        nations.removeIf(f -> f.getActive_m() > 7200 || f.isGray() || f.isBeige() || f.getVm_turns() > 0);
        int numRemoved = sizeOriginal - nations.size();

        List<String> header = new ArrayList<>(Arrays.asList(
            "nation",
            "cities",
            "avg_infra",
            "avg_land",
            "avg_buildings",
            "avg_disease",
            "avg_crime",
            "avg_pollution",
            "avg_population",
            "mmr[unit]",
            "mmr[build]",
            "revenue[converted]",
            "raws %",
            "manu %",
            "commerce %",
            "optimal %"
        ));
        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS) continue;
            header.add(type.name());
        }
        sheet.setHeader(header);

        Function<DBNation, List<String>> addRowTask = nation -> {
            double[] revenue = nation.getRevenue();

            double disease = 0;
            double crime = 0;
            double pollution = 0;
            double population = 0;
            Map<Integer, JavaCity> cities = nation.getCityMap(false, false);
            for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
                JavaCity city = entry.getValue();
                disease += city.getMetrics(nation::hasProject).disease;
                crime += city.getMetrics(nation::hasProject).crime;
                pollution += city.getMetrics(nation::hasProject).pollution;
                population += city.getMetrics(nation::hasProject).population;
            }
            disease /= cities.size();
            crime /= cities.size();
            pollution /= cities.size();
            population /= cities.size();

            double revenueConverted = PnwUtil.convertedTotal(revenue);
            double revenueRaw = 0;
            double revenueManu = 0;
            for (ResourceType type : ResourceType.values) {
                if (type.isManufactured()) revenueManu += PnwUtil.convertedTotal(type, revenue[type.ordinal()]);
                if (type.isRaw()) revenueRaw += PnwUtil.convertedTotal(type, revenue[type.ordinal()]);
            }
            double revenueCommerce = revenue[0];

            List<String> row = new ArrayList<>(header);
            row.set(0, MarkupUtil.sheetUrl(nation.getNation(), PnwUtil.getUrl(nation.getNation_id(), false)));
            row.set(1, nation.getCities() + "");
            row.set(2, MathMan.format(nation.getAvg_infra()));
            row.set(3, MathMan.format(nation.getAvgLand()));
            row.set(4, MathMan.format(nation.getAvgBuildings()));
            row.set(5, MathMan.format(disease));
            row.set(6, MathMan.format(crime));
            row.set(7, MathMan.format(pollution));
            row.set(8, MathMan.format(population));
            row.set(9, nation.getMMR());
            row.set(9, "=\"" + nation.getMMR()+ "\"");
            row.set(10, "=\"" + nation.getMMRBuildingStr()+ "\"");
            row.set(11, MathMan.format(revenueConverted));

            row.set(12, MathMan.format(100 * revenueRaw / revenueConverted));
            row.set(13, MathMan.format(100 * revenueManu / revenueConverted));
            row.set(14, MathMan.format(100 * revenueCommerce / revenueConverted));

            JavaCity city1 = cities.entrySet().iterator().next().getValue();

            double profit = city1.profitConvertedCached(nation.getContinent(), nation.getRads(), nation::hasProject, nation.getCities(), nation.getGrossModifier());
            JavaCity origin = new JavaCity(city1);
            origin.zeroNonMilitary();
            JavaCity optimal = origin.optimalBuild(nation, 0);
            double profitOptimal = optimal.profitConvertedCached(nation.getContinent(), nation.getRads(), nation::hasProject, nation.getCities(), nation.getGrossModifier());

            double optimalGain = profit >= profitOptimal ? 1 : profit / profitOptimal;

            row.set(15, MathMan.format(100 * optimalGain));


            int i = 16;
            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) continue;
                row.set(i++, MathMan.format(revenue[type.ordinal()]));
            }

            return row;
        };

        List<Future<List<String>>> addRowFutures = new ArrayList<>();

        for (DBNation nation : nations) {
            Future<List<String>> future = Locutus.imp().getExecutor().submit(() -> addRowTask.apply(nation));
            addRowFutures.add(future);
        }

        for (Future<List<String>> future : addRowFutures) {
            sheet.addRow(future.get());
        }

        sheet.clearAll();
        sheet.set(0, 0);
        return "<" + sheet.getURL() + ">";
    }

    @Command(desc = "Get a sheet of members and their saved up warchest (can include deposits and potential revenue)")
    @RolePermission(value = {Roles.ECON_LOW_GOV, Roles.ECON, Roles.MILCOM, Roles.MILCOM_ADVISOR})
    @IsAlliance
    public String warchestSheet(@Me GuildDB db, Set<DBNation> nations, @Switch("c") Map<ResourceType, Double> perCityWarchest, @Arg("Excess resources in AA bank that could be used to supplementWarchest") @Switch("b") Map<ResourceType, Double> allianceBankWarchest, @Switch("g") boolean includeGrants, @Switch("n") boolean doNotNormalizeDeposits, @Switch("d") boolean ignoreDeposits, @Switch("e") boolean ignoreStockpileInExcess, @Switch("r") Integer includeRevenueDays, @Switch("f") boolean forceUpdate) throws IOException, GeneralSecurityException {
        int aaId = db.getAlliance_id();
        DBAlliance alliance = db.getAlliance();
        Map<DBNation, Map<ResourceType, Double>> stockpiles = alliance.getMemberStockpile();

        List<String> errors = new ArrayList<>();

        Map<DBNation, double[]> warchestByNation = new HashMap<>();
        Map<DBNation, double[]> warchestLackingByNation = new HashMap<>();
        Map<DBNation, double[]> warchestExcessByNation = new HashMap<>();
        Map<DBNation, double[]> revenueByNation = new HashMap<>();
        Function<DBNation, Map<ResourceType, Double>> wcReqFunc = f -> {
            return perCityWarchest != null ? perCityWarchest : db.getPerCityWarchest(f);
        };

        for (DBNation nation : nations) {
            Map<ResourceType, Double> myStockpile = stockpiles.get(nation);
            if (myStockpile == null) {
                errors.add("Could not fetch stockpile of: " + nation.getNation() + "/" + nation.getNation_id());
                continue;
            }
            double[] stockpileArr2 = PnwUtil.resourcesToArray(myStockpile);
            double[] total = stockpileArr2.clone();
            double[] depo = ResourceType.getBuffer();
            if (!ignoreDeposits) {
                depo = nation.getNetDeposits(db, includeGrants, forceUpdate ? 0L : -1L);
                if (!doNotNormalizeDeposits) {
                    depo = PnwUtil.normalize(depo);
                }
                for (int i = 0; i < depo.length; i++) {
                    if (depo[i] > 0) total[i] += depo[i];
                }
            }
            double[] revenue = nation.getRevenue();
            revenueByNation.put(nation, revenue);
            if (includeRevenueDays != null) {
                for (int i = 0; i < revenue.length; i++) {
                    total[i] += revenue[i] * includeRevenueDays;
                }
            }

            double[] warchest = ResourceType.getBuffer();
            double[] warchestLacking = ResourceType.getBuffer();
            double[] warchestExcess = ResourceType.getBuffer();

            Map<ResourceType, Double> localPerCityWarchest = wcReqFunc.apply(nation);

            boolean lacking = false;
            boolean excess = false;
            for (Map.Entry<ResourceType, Double> entry : localPerCityWarchest.entrySet()) {
                ResourceType type = entry.getKey();
                double required = entry.getValue() * nation.getCities();
                double current = Math.max(0, total[type.ordinal()]);
                double net = current - required;

                if (net < -1) {
                    lacking = true;
                    warchestLacking[type.ordinal()] += Math.abs(net);
                } else if (net > 1) {
                    if (ignoreStockpileInExcess) {
                        net -= (stockpileArr2[type.ordinal()] + Math.max(0, depo[type.ordinal()]));
                    }
                    if (net > 1) {
                        warchestExcess[type.ordinal()] += net;
                        excess = true;
                    }
                }

                warchest[type.ordinal()] = total[type.ordinal()];
            }

            warchestByNation.put(nation, warchest);
            if (lacking) {
                warchestLackingByNation.put(nation, warchestLacking);
            }
            if (excess) {
                warchestExcessByNation.put(nation, warchestExcess);
            }
        }

        double[] totalWarchest = ResourceType.getBuffer();
        double[] totalNet = ResourceType.getBuffer();

        SpreadSheet sheet = SpreadSheet.create(db, GuildDB.Key.WARCHEST_SHEET);
        List<String> header = new ArrayList<>(Arrays.asList(
            "nation",
            "cities",
            "mmr[build]",
            "mmr[unit]",
            "WC %",
            "WC % (converted)",
            "Revenue Contribution Individual %",
            "Revenue Contribution Aggregate %",
            "Missing WC",
            "Excess WC",
            "WC",
            "Missing WC val",
            "Excess WC val"
        ));

        sheet.setHeader(header);

        double[] empty = ResourceType.getBuffer();
        for (Map.Entry<DBNation, double[]> entry : warchestByNation.entrySet()) {
            DBNation nation = entry.getKey();
            double[] warchest = entry.getValue();
            double[] lacking = warchestLackingByNation.getOrDefault(nation, empty);
            double[] excess = warchestExcessByNation.getOrDefault(nation, empty);
            for (int i = 0; i < warchest.length; i++) {
                totalWarchest[i] += warchest[i];
                totalNet[i] += excess[i];
                totalNet[i] -= lacking[i];
            }
        }

        for (Map.Entry<DBNation, double[]> entry : warchestByNation.entrySet()) {
            DBNation nation = entry.getKey();

            double[] warchest = entry.getValue();
            double[] lacking = warchestLackingByNation.getOrDefault(nation, empty);
            double[] excess = warchestExcessByNation.getOrDefault(nation, empty);
            double[] revenue = revenueByNation.getOrDefault(nation, empty);

            double[] localPerCityWarchest = PnwUtil.resourcesToArray(wcReqFunc.apply(nation));
            double requiredValue = PnwUtil.convertedTotal(localPerCityWarchest) * nation.getCities();
            double wcPct = (requiredValue - PnwUtil.convertedTotal(lacking)) / requiredValue;
            double wcPctConverted = (requiredValue - PnwUtil.convertedTotal(lacking) + PnwUtil.convertedTotal(excess)) / requiredValue;

            double revenueIndividualValue = 0;
            double revenueAggregateValue = 0;
            double revenueValue = PnwUtil.convertedTotal(revenue);
            for (int i = 0; i < revenue.length; i++) {
                double amt = revenue[i];
                if (amt == 0) continue;;
                if (lacking[i] > 0) {
                    revenueIndividualValue += PnwUtil.convertedTotal(ResourceType.values[i], amt);
                }
                if (totalNet[i] < 0) {
                    revenueAggregateValue += PnwUtil.convertedTotal(ResourceType.values[i], amt);
                }
            }

            header.set(0, MarkupUtil.sheetUrl(nation.getNation(), PnwUtil.getUrl(nation.getNation_id(), false)));
            header.set(1, nation.getCities() + "");
            header.set(2, nation.getMMRBuildingStr());
            header.set(3, nation.getMMR());

            header.set(4, MathMan.format(100 * wcPct));
            header.set(5, MathMan.format(100 * wcPctConverted));

            header.set(6, MathMan.format(100 * (revenueIndividualValue / revenueValue)));
            header.set(7, MathMan.format(100 * (revenueAggregateValue / revenueValue)));
            header.set(8, PnwUtil.resourcesToString(lacking));
            header.set(9, PnwUtil.resourcesToString(excess));
            header.set(10, PnwUtil.resourcesToString(warchest));
            header.set(11, MathMan.format(PnwUtil.convertedTotal(lacking)));
            header.set(12, MathMan.format(PnwUtil.convertedTotal(excess)));

            sheet.addRow(header);
        }

        sheet.clearAll();
        sheet.set(0, 0);

        StringBuilder response = new StringBuilder();
        response.append("<" + sheet.getURL() + ">\n");
        response.append("Total Warchest: `" + PnwUtil.resourcesToString(totalWarchest) + "` worth: ~$" + MathMan.format(PnwUtil.convertedTotal(totalWarchest)) + "\n");
        response.append("Net Warchest Req (warchest - requirements): `" + PnwUtil.resourcesToString(totalNet) + "` worth: ~$" + MathMan.format(PnwUtil.convertedTotal(totalNet)));

        return response.toString();
    }

    public static final Map<UUID, Grant> AUTHORIZED_TRANSFERS = new HashMap<>();

    @Command(desc = "Withdraw from the alliance bank (your deposits)")
    @RolePermission(value = {Roles.ECON_WITHDRAW_SELF, Roles.ECON}, any=true)
    public String withdraw(@Me IMessageIO channel, @Me JSONObject command,
                           @Me User author, @Me DBNation me, @Me GuildDB guildDb, @NationDepositLimit Map<ResourceType, Double> transfer, @Default("#deposit") String primaryNote,
                           @Switch("f") boolean force, @Switch("o") boolean onlyMissingFunds,
                           @Switch("e") @Timediff Long expire,
                           @Switch("n") String secondaryNotes,
                           @Switch("g") UUID token,
                           @Switch("c") boolean convertCash) throws IOException {
        return transfer(channel, command, author, me, guildDb, me, transfer, primaryNote, force, onlyMissingFunds, expire, secondaryNotes, token, convertCash);
    }

    @Command(desc = "Bulk shift resources in a nations `{prefix}depo <nation> -t` to another category")
    @RolePermission(Roles.ECON)
    public String shiftDeposits(@Me GuildDB db, @Me DBNation me, DBNation nation, DepositType from, DepositType to, @Default @Timestamp Long timediff) {
        if (from == to) throw new IllegalArgumentException("From and to must be a different category.");
        if (timediff != null && to != DepositType.GRANT) {
            throw new IllegalArgumentException("The grant expiry timediff is only needed if converted to the grant category");
        }

        String note = "#" + to.name().toLowerCase(Locale.ROOT);

        if (to == DepositType.GRANT) {
            if (timediff == null) {
                throw new IllegalArgumentException("You must specify a grant expiry timediff if converting to the grant category. e.g. `60d`");
            } else {
                note += " #expire=timestamp:" + (System.currentTimeMillis() + timediff);
            }
        }
        Map<DepositType, double[]> depoByType = nation.getDeposits(db, null, true, true, 0, 0);

        double[] toAdd = depoByType.get(from);
        if (toAdd == null || ResourceType.isEmpty(toAdd)) {
            return "Nothing to shift for " + nation.getNation();
        }
        long now = System.currentTimeMillis();
        if (from == DepositType.GRANT) {
            resetDeposits(db, me, nation, false, true, true, true);
        } else {
            String noteFrom = "#" + from.name().toLowerCase(Locale.ROOT);
            db.subBalance(now, nation, me.getNation_id(), noteFrom, toAdd);
        }
        db.addBalance(now, nation, me.getNation_id(), note, toAdd);
        return "Shifted " + PnwUtil.resourcesToString(toAdd) + " from " + from + " to " + to + " for " + nation.getNation();
    }

    @Command(desc = "Resets a nations deposits")
    @RolePermission(Roles.ECON)
    public String resetDeposits(@Me GuildDB db, @Me DBNation me, DBNation nation, @Switch("g") boolean ignoreGrants, @Switch("l") boolean ignoreLoans, @Switch("t") boolean ignoreTaxes, @Switch("d") boolean ignoreBankDeposits) {
        Map<DepositType, double[]> depoByType = nation.getDeposits(db, null, true, true, 0, 0);

        long now = System.currentTimeMillis();

        double[] deposits = depoByType.get(DepositType.DEPOSITS);
        if (deposits != null && !ignoreBankDeposits) {
            db.subBalance(now, nation, me.getNation_id(), "#deposit", deposits);
        }

        double[] tax = depoByType.get(DepositType.TAX);
        if (tax != null && !ignoreTaxes) {
            db.subBalance(now, nation, me.getNation_id(), "#tax", tax);
        }

        double[] loan = depoByType.get(DepositType.LOAN);
        if (loan != null && !ignoreLoans) {
            db.subBalance(now, nation, me.getNation_id(), "#loan", loan);
        }
        if (depoByType.containsKey(DepositType.GRANT) && !ignoreGrants) {
            List<Map.Entry<Integer, Transaction2>> transactions = nation.getTransactions(db, null, true, true, -1, 0);
            for (Map.Entry<Integer, Transaction2> entry : transactions) {
                Transaction2 tx = entry.getValue();
                if (tx.note == null || !tx.note.contains("#expire") || (tx.receiver_id != nation.getNation_id() && tx.sender_id != nation.getNation_id())) continue;
                if (tx.sender_id == tx.receiver_id) continue;
                Map<String, String> notes = PnwUtil.parseTransferHashNotes(tx.note);
                String expire = notes.get("#expire");
                long expireEpoch = tx.tx_datetime + TimeUtil.timeToSec_BugFix1(expire, tx.tx_datetime) * 1000L;
                if (expireEpoch > now) {
                    String noteCopy = tx.note.replaceAll("#expire=[a-zA-Z0-9:]+", "");
                    noteCopy += " #expire=" + "timestamp:" + expireEpoch;
                    noteCopy = noteCopy.trim();

                    tx.tx_datetime = System.currentTimeMillis();
                    int sign = entry.getKey();
                    if (sign == 1) {
                        db.subBalance(now, nation, me.getNation_id(), noteCopy, tx.resources);
                    } else if (sign == -1) {
                        db.addBalance(now, nation, me.getNation_id(), noteCopy, tx.resources);
                    }
                }
            }
        }
        return "Reset deposits for " + nation.getNation();
    }

    @Command(desc = "Transfer from the alliance bank (alliance deposits)")
    @RolePermission(Roles.ECON)
    public String transfer(@Me IMessageIO channel, @Me JSONObject command,
                           @Me User author, @Me DBNation me, @Me GuildDB guildDb, NationOrAlliance receiver, @AllianceDepositLimit Map<ResourceType, Double> transfer, String primaryNote,
                           @Switch("f") boolean force, @Switch("o") boolean onlyMissingFunds,
                           @Switch("e") @Timediff Long expire,
                           @Switch("n") String secondaryNotes,
                           @Switch("g") UUID token,
                           @Switch("c") boolean convertCash) throws IOException {
        boolean isAdmin = Roles.ECON.hasOnRoot(author);
        OffshoreInstance offshore = guildDb.getOffshore();

        if (primaryNote.isEmpty()) throw new IllegalArgumentException("Note must not be empty");
        if (primaryNote.contains(" ")) throw new IllegalArgumentException("Primary not cannot contain spaces");

        List<String> otherNotes = new ArrayList<>();
        if (secondaryNotes != null) {
            otherNotes.addAll(StringMan.split(secondaryNotes, ' '));
        }

        if (expire != null) {
            if (expire < 1000) throw new IllegalArgumentException("Invalid amount of time (maybe add `d` otherwise it uses seconds): `" + expire + "`");
            otherNotes.add("#expire=" + TimeUtil.secToTime(TimeUnit.MILLISECONDS, expire));
        }

        Set<Grant.Requirement> failedRequirements = new HashSet<>();

        boolean isGrant = false;
        if (token != null) {
            Grant authorized = AUTHORIZED_TRANSFERS.get(token);
            if (authorized == null) return "Invalid token (try again)";
            if (!receiver.isNation()) return "Receiver is not nation";

            for (Grant.Requirement requirement : authorized.getRequirements()) {
                if (!requirement.apply(receiver.asNation())) {
                    failedRequirements.add(requirement);
                    if (requirement.canOverride()) continue;
                    else {
                        return "Failed requirement: " + requirement.getMessage();
                    }
                }
            }

            isGrant = true;
        }

        Collection<String> allowedLabels = Arrays.asList("#grant", "#deposit", "#trade", "#ignore", "#tax", "#warchest", "#account");
        if (!allowedLabels.contains(primaryNote.split("=")[0].toLowerCase())) {
            return "Please use one of the following labels: " + StringMan.getString(allowedLabels);
        }

        if (!isAdmin) {
            GuildDB offshoreGuild = Locutus.imp().getGuildDBByAA(offshore.getAllianceId());
            if (offshoreGuild != null) {
                isAdmin = Roles.ECON.has(author, offshoreGuild.getGuild());
            }
        }

        if (receiver.isAlliance() && !receiver.asAlliance().exists()) {
            throw new IllegalArgumentException("Alliance: " + receiver.getUrl() + " has no receivable nations");
        }

        if (receiver.isAlliance() && onlyMissingFunds) {
            return "Option `-o` only applicable for nations";
        }

        long userId = author.getIdLong();
        if (PnwUtil.convertedTotal(transfer) > 1000000000L
                && userId != Settings.INSTANCE.ADMIN_USER_ID
                && !Settings.INSTANCE.LEGACY_SETTINGS.WHITELISTED_BANK_USERS.contains(userId)
                && !isGrant
        ) {
            return "No permission (1)";
        }

        if (convertCash) {
            otherNotes.add("#cash=" + MathMan.format(PnwUtil.convertedTotal(transfer)));
        }

        String receiverStr = receiver.isAlliance() ? receiver.getName() : receiver.asNation().getNation();
        String note = primaryNote;
        DBNation banker = me;

        Integer aaId3 = guildDb.getOrNull(GuildDB.Key.ALLIANCE_ID);
        long senderId = aaId3 == null ? guildDb.getIdLong() : aaId3;
        note += "=" + senderId;
        if (!otherNotes.isEmpty()) note += " " + StringMan.join(otherNotes, " ");
        note = note.trim();

        if (note.contains("#cash") && !Roles.ECON.has(author, guildDb.getGuild())) {
            return "You must have `ECON` Role to send with `#cash`";
        }

        {
            if (receiver.isAlliance() && !note.contains("#ignore") && !force) {
                return "Please include `#ignore` in note when transferring to alliances";
            }
            if (receiver.isNation() && !note.contains("#deposit=") && !note.contains("#grant=") && !note.contains("#ignore")) {
                if (aaId3 == null) return "Please *include* `#ignore` or `#deposit` or `#grant` in note when transferring to nations";
                if (aaId3 != receiver.asNation().getAlliance_id()) {
                    return "Please include `#ignore` or `#deposit` or `#grant` in note when transferring to nations not in your alliance";
                }
            }
        }

        // transfer json if they dont have perms to do the transfer
        if (offshore == null) {
            // don't send it
            String json = PnwUtil.resourcesToJson(receiverStr, receiver.isNation(), transfer, note);
            String prettyJson = JsonUtil.toPrettyFormat("[" + json + "]");

            StringBuilder body = new StringBuilder();

            body.append("```").append(prettyJson).append("```").append("\n");
            body.append("Total: `" + StringMan.getString(transfer) + "`").append('\n');
            body.append("Worth: ~$" + MathMan.format(PnwUtil.convertedTotal(transfer)) + "`").append("\n\n");

            String title = (receiver.isNation() ? "NATION:" : "ALLIANCE:") + receiver.getName() + " " + note;

            // send message, with reactions to send to nation or alliance
            List<String> params = new ArrayList<>();

            channel.create()
                            .embed(title, body.toString())
                                    .append("See also:\n" +
                                            "> https://docs.google.com/document/d/1QkN1FDh8Z8ENMcS5XX8zaCwS9QRBeBJdCmHN5TKu_l8\n" +
                                            "To add an offshore:\n" +
                                            "1. (int this server) " + CM.coalition.add.cmd.create(null, Coalition.OFFSHORE.name()) + "\n" +
                                            "2. (in the offshore) " + CM.coalition.add.cmd.create(null, Coalition.OFFSHORING.name()) + "")
                                            .send();
            return null;
        }

        if (!force) {
            if (receiver.isNation() && receiver.asNation().getVm_turns() > 0) return "Receiver is in Vacation Mode (use " + CM.admin.sync.syncNations.cmd.create(receiver.getName()) + " to force an update, add `-f` to bypass)";
            if (receiver.isNation() && receiver.asNation().isGray()) return "Receiver is Gray (use " + CM.admin.sync.syncNations.cmd.create(receiver.getName()) + " to force an update, add `-f` to bypass)";
            if (receiver.isNation() && receiver.asNation().getNumWars() > 0 && receiver.asNation().isBlockaded()) return "Receiver is blockaded (use " + CM.admin.sync.syncNations.cmd.create(receiver.getName()) + " to force an update, add `-f` to bypass)";
            if (receiver.isNation() && receiver.asNation().getActive_m() > 10000) channel.send("!! **WARN**: Receiver is inactive  (use " + CM.admin.sync.syncNations.cmd.create(receiver.getName()) + " to force an update, add `-f` to bypass)");
        }

        // confirmation prompt
        if (!force) {
            String title;
            if (transfer.size() == 1) {
                Map.Entry<ResourceType, Double> entry = transfer.entrySet().iterator().next();
                title = MathMan.format(entry.getValue()) + " x " + entry.getKey();
                if (entry.getKey() == ResourceType.MONEY) title = "$" + title;
            } else {
                title = PnwUtil.resourcesToString(transfer);
            }
            title += " to " + (receiver.isAlliance() ? "AA " : "") + receiver.getName();
            if (receiver.isNation()) title += " | " + receiver.asNation().getAllianceName();
            String body = note + (note.isEmpty() ? "" : "\n") + "Press `Confirm` to confirm";

            channel.create().confirmation(title, body, command).send();
            return null;
        }

        GuildDB offshoreDb = offshore.getGuildDB();
        if (offshoreDb == null) return "Error: No guild DB set for offshore??";

        synchronized (OffshoreInstance.BANK_LOCK) {
            Integer aaId2 = guildDb.getOrNull(GuildDB.Key.ALLIANCE_ID);

            if (!isAdmin) {
                if (offshore.isDisabled(guildDb.getGuild().getIdLong())) {
                    MessageChannel logChannel = offshore.getGuildDB().getOrNull(GuildDB.Key.RESOURCE_REQUEST_CHANNEL);
                    if (logChannel != null) {
                        String msg = "Transfer error: " + guildDb.getGuild().toString() + " | " + aaId2 + " | <@" + Settings.INSTANCE.ADMIN_USER_ID + (">");
                        RateLimitUtil.queue(logChannel.sendMessage(msg));
                    }
                    return "An error occured. Please request an administrator transfer the funds";
                }

                if (!Roles.ECON.has(author, guildDb.getGuild())) {
                    if (aaId2 != null) {
                        if (banker.getAlliance_id() != aaId2 || banker.getPosition() <= 1)
                            return "You are not a member of " + aaId2;
                    } else if (!Roles.MEMBER.has(author, guildDb.getGuild())) {
                        Role memberRole = Roles.MEMBER.toRole(guildDb.getGuild());
                        if (memberRole == null) return "No member role enabled (see " + CM.role.setAlias.cmd.toSlashMention() + ")";
                        return "You do not have the member role: " + memberRole.getName();
                    }
                    if (guildDb.getOrNull(GuildDB.Key.MEMBER_CAN_WITHDRAW) != Boolean.TRUE)
                        return "`MEMBER_CAN_WITHDRAW` is false (see " + CM.settings.cmd.create(GuildDB.Key.MEMBER_CAN_WITHDRAW.name(), "true") + " )";
                    GuildMessageChannel rssChannel = guildDb.getOrNull(GuildDB.Key.RESOURCE_REQUEST_CHANNEL);
                    if (rssChannel == null)
                        return "Please have an admin use. " + CM.settings.cmd.create(GuildDB.Key.RESOURCE_REQUEST_CHANNEL.name(), "#someChannel") + "";
                    if (channel.getIdLong() != rssChannel.getIdLong())
                        return "Please use the transfer command in " + ((GuildMessageChannel) channel).getAsMention();

                    if (!Roles.ECON_WITHDRAW_SELF.has(author, guildDb.getGuild()))
                        return "You do not have the `ECON_WITHDRAW_SELF` role. See: " + CM.role.setAlias.cmd.toSlashMention() + "";
                    if (!receiver.isNation() || receiver.getId() != me.getId())
                        return "You only have permission to withdraw to yourself";

                    if (guildDb.getOrNull(GuildDB.Key.MEMBER_CAN_WITHDRAW_WARTIME) != Boolean.TRUE && aaId2 != null) {
                        if (!guildDb.getCoalition("enemies").isEmpty())
                            return "You cannot withdraw during wartime. `MEMBER_CAN_WITHDRAW_WARTIME` is false (see " + CM.settings.cmd.create(GuildDB.Key.MEMBER_CAN_WITHDRAW.name(), "true") + ") and `enemies` is set (see: " +
                                    "" + CM.coalition.add.cmd.toSlashMention() + " | " + CM.coalition.remove.cmd.toSlashMention() + " | " + CM.coalition.list.cmd.toSlashMention() + ")";
                        DBAlliance aaObj = DBAlliance.getOrCreate(aaId2);
                        ByteBuffer warringBuf = aaObj.getMeta(AllianceMeta.IS_WARRING);
                        if (warringBuf != null && warringBuf.get() == 1)
                            return "You cannot withdraw during wartime. `MEMBER_CAN_WITHDRAW_WARTIME` is false (see " + CM.settings.cmd.create(GuildDB.Key.MEMBER_CAN_WITHDRAW.name(), "true") + ")";
                    }

                    // check that we personally have the required deposits
                    Boolean ignoreGrants = guildDb.getOrNull(GuildDB.Key.MEMBER_CAN_WITHDRAW_IGNORES_GRANTS);
                    if (ignoreGrants == null) ignoreGrants = false;

                    double[] myDeposits = me.getNetDeposits(guildDb, !ignoreGrants);
                    myDeposits = PnwUtil.normalize(myDeposits);
                    double myDepoValue = PnwUtil.convertedTotal(myDeposits, false);
                    double txValue = PnwUtil.convertedTotal(transfer);

                    if (myDepoValue <= 0)
                        return "Your deposits value (market min of $" + MathMan.format(myDepoValue) + ") is insufficient (transfer value $" + MathMan.format(txValue) + ")";

                    boolean rssConversion = guildDb.getOrNull(GuildDB.Key.RESOURCE_CONVERSION) == Boolean.TRUE;
                    boolean hasExactResources = true;
                    for (Map.Entry<ResourceType, Double> entry : transfer.entrySet()) {
                        if (myDeposits[entry.getKey().ordinal()] + 0.01 < entry.getValue()) {
                            if (!rssConversion) {
                                return "You do not have `" + MathMan.format(entry.getValue()) + "x" + entry.getKey() + "`. (see " + CM.deposits.check.cmd.create(me.getNation(), null, null, null, null, null, null) + " ). RESOURCE_CONVERSION is disabled (see " + CM.settings.cmd.create(GuildDB.Key.MEMBER_CAN_WITHDRAW.name(), "true") + ")";
                            }
                            hasExactResources = false;
                        }
                    }
                    if (!hasExactResources && myDepoValue < txValue) {
                        return "Your deposits are worth $" + MathMan.format(myDepoValue) + "(market min) but you requested to withdraw $" + MathMan.format(txValue) + " worth of resources";
                    }

                    if (!PnwUtil.isNoteFromDeposits(note, senderId, System.currentTimeMillis())) {
                        return "Only `#deposit` is permitted as the note, you provided: `" + note + "`";
                    }
                }
            }
            double[] deposits = offshore.getDeposits(guildDb);

            MessageChannel logChannel = offshore.getGuildDB().getOrNull(GuildDB.Key.RESOURCE_REQUEST_CHANNEL);
            if (logChannel != null) {
                String msg = "Prior Deposits for: " + guildDb.getGuild().toString() + "/" + aaId2 + ": `" + PnwUtil.resourcesToString(deposits) + ("`");
                RateLimitUtil.queue(logChannel.sendMessage(msg));
            }

            if (!isAdmin && (aaId2 == null || offshore.getAllianceId() != aaId2)) {
                for (Map.Entry<ResourceType, Double> entry : transfer.entrySet()) {
                    ResourceType rss = entry.getKey();
                    Double amt = entry.getValue();
                    if (amt > 0 && deposits[rss.ordinal()] + 0.01 < amt) {
                        return "You do not have " + MathMan.format(amt) + " x " + rss.name();
                    }
                }
            }

            double[] amount = PnwUtil.resourcesToArray(transfer);
            Map.Entry<OffshoreInstance.TransferStatus, String> result = offshore.transferFromDeposits(me, guildDb, receiver, amount, note);

            if (result.getKey() == OffshoreInstance.TransferStatus.SUCCESS) {
                banker.setMeta(NationMeta.INTERVIEW_TRANSFER_SELF, (byte) 1);
            }

            return "`" + PnwUtil.resourcesToString(transfer) + "` -> " + receiver.getUrl() + "\n**" + result.getKey() + "**: " + result.getValue();
        }
    }

    @Command(desc = "Sheet of projects each nation has")
    @RolePermission(value = {Roles.ECON, Roles.INTERNAL_AFFAIRS}, any=true)
    public String ProjectSheet(@Me GuildDB db, Set<DBNation> nations, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, GuildDB.Key.PROJECT_SHEET);
        }
        List<Object> header = new ArrayList<>(Arrays.asList(
                "nation",
                "alliance",
                "\uD83C\uDFD9", // cities
                "\uD83C\uDFD7", // avg_infra
                "score"
        ));

        for (Project value : Projects.values) {
            header.add(value.name());
        }

        sheet.setHeader(header);

        for (DBNation nation : nations) {

            header.set(0, MarkupUtil.sheetUrl(nation.getNation(), PnwUtil.getUrl(nation.getNation_id(), false)));
            header.set(1, MarkupUtil.sheetUrl(nation.getAllianceName(), PnwUtil.getUrl(nation.getAlliance_id(), true)));
            header.set(2, nation.getCities());
            header.set(3, nation.getAvg_infra());
            header.set(4, nation.getScore());

            for (int i = 0; i < Projects.values.length; i++) {
                Project project = Projects.values[i];
                header.set(5 + i, nation.hasProject(project) + "");
            }

            sheet.addRow(header);
        }

        sheet.clearAll();
        sheet.set(0, 0);

        return "<" + sheet.getURL() + ">";
    }

    @Command(aliases = {"depositSheet", "depositsSheet"}, desc =
            "Get a list of nations and their deposits.\n" +
                    "Add `-b` to use 0/0 as the tax base\n" +
                    "Add `-o` to not include any manual deposit offsets\n" +
                    "Add `-d` to not include deposits\n" +
                    "Add `-t` to not include taxes\n" +
                    "Add `-l` to not include loans\n" +
                    "Add `-g` to not include grants`\n" +
                    "Add `-f` to force an update"
    )
    @RolePermission(Roles.ECON)
    public String depositSheet(@Me IMessageIO channel, @Me Guild guild, @Me GuildDB db,
                               @Default Set<DBNation> nations, @Default Set<DBAlliance> offshores,
                               @Switch("b") boolean ignoreTaxBase,
                               @Switch("o") boolean ignoreOffsets,
                               @Switch("t") boolean noTaxes,
                               @Switch("l") boolean noLoans,
                               @Switch("g") boolean noGrants,
                               @Switch("d") boolean noDeposits,
                               @Switch("f") boolean force

    ) throws GeneralSecurityException, IOException {
        CompletableFuture<IMessageBuilder> msgFuture = channel.send("Please wait...");

        SpreadSheet sheet = SpreadSheet.create(db, GuildDB.Key.DEPOSITS_SHEET);

        List<Object> header = new ArrayList<>(Arrays.asList(
                "nation",
                "cities",
                "age",
                "deposit",
                "tax",
                "loan",
                "grant",
                "total",
                "last_deposit_day"
        ));

        for (ResourceType type : ResourceType.values()) {
            if (type == ResourceType.CREDITS) continue;
            header.add(type.name());
        }

        sheet.setHeader(header);

        boolean useTaxBase = !ignoreTaxBase;
        boolean useOffset = !ignoreOffsets;

        if (nations == null) {
            Integer allianceId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
            if (allianceId != null) {
                nations = new LinkedHashSet<>(Locutus.imp().getNationDB().getNations(Collections.singleton(allianceId)));
                nations.removeIf(n -> n.getPosition() <= 1);
            } else {
                Role role = Roles.MEMBER.toRole(guild);
                if (role == null) throw new IllegalArgumentException("No " + CM.settings.cmd.create(GuildDB.Key.ALLIANCE_ID.name(), null).toSlashCommand() + " set, or " +
                        "" + CM.role.setAlias.cmd.create(Roles.MEMBER.name(), "") + " set");
                nations = new LinkedHashSet<>();
                for (Member member : guild.getMembersWithRoles(role)) {
                    DBNation nation = DiscordUtil.getNation(member.getUser());
                    nations.add(nation);
                }
                if (nations.isEmpty()) return "No members found";

            }
        }
        Set<Long> tracked = null;
        if (offshores != null) {
            tracked = new LinkedHashSet<>();
            for (DBAlliance aa : offshores) tracked.add((long) aa.getAlliance_id());
            tracked = PnwUtil.expandCoalition(tracked);
        }

        double[] aaTotalPositive = ResourceType.getBuffer();
        double[] aaTotalNet = ResourceType.getBuffer();

        long last = System.currentTimeMillis();
        for (DBNation nation : nations) {
            if (System.currentTimeMillis() - last > 5000) {
                IMessageBuilder tmp = msgFuture.getNow(null);
                if (tmp != null) msgFuture = tmp.clear().append("calculating for: " + nation.getNation()).send();
                last = System.currentTimeMillis();
            }
            Map<DepositType, double[]> deposits = nation.getDeposits(db, tracked, useTaxBase, useOffset, 0L, 0L);
            double[] buffer = ResourceType.getBuffer();

            header.set(0, MarkupUtil.sheetUrl(nation.getNation(), nation.getNationUrl()));
            header.set(1, nation.getCities());
            header.set(2, nation.getAgeDays());
            header.set(3, String.format("%.2f", PnwUtil.convertedTotal(deposits.getOrDefault(DepositType.DEPOSITS, buffer))));
            header.set(4, String.format("%.2f", PnwUtil.convertedTotal(deposits.getOrDefault(DepositType.TAX, buffer))));
            header.set(5, String.format("%.2f", PnwUtil.convertedTotal(deposits.getOrDefault(DepositType.LOAN, buffer))));
            header.set(6, String.format("%.2f", PnwUtil.convertedTotal(deposits.getOrDefault(DepositType.GRANT, buffer))));
            double[] total = ResourceType.getBuffer();
            for (Map.Entry<DepositType, double[]> entry : deposits.entrySet()) {
                switch (entry.getKey()) {
                    case GRANT:
                        if (noGrants) continue;
                        break;
                    case LOAN:
                        if (noLoans) continue;
                        break;
                    case TAX:
                        if (noTaxes) continue;
                        break;
                    case DEPOSITS:
                        if (noDeposits) continue;
                        break;
                }
                double[] value = entry.getValue();
                total = ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, total, value);
            }
            header.set(7, String.format("%.2f", PnwUtil.convertedTotal(total)));
            List<Transaction2> transactions = nation.getTransactions(Long.MAX_VALUE);
            long lastDeposit = 0;
            for (Transaction2 transaction : transactions) {
                if (transaction.sender_id == nation.getNation_id()) {
                    lastDeposit = Math.max(transaction.tx_datetime, lastDeposit);
                }
            }
            if (lastDeposit == 0) {
                header.set(8, "NEVER");
            } else {
                long days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastDeposit);
                header.set(8, days);
            }
            int i = 9;
            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) continue;
                header.set((i++), total[type.ordinal()]);
            }
            double[] normalized = PnwUtil.normalize(total);
            if (PnwUtil.convertedTotal(normalized) > 0) {
                aaTotalPositive = ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, aaTotalPositive, normalized);
            }
            aaTotalNet = ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, aaTotalNet, total);
            sheet.addRow(header);
        }

        sheet.clearAll();
        sheet.set(0, 0);

        StringBuilder response = new StringBuilder();
        response.append("<" + sheet.getURL() + ">");

        StringBuilder footer = new StringBuilder();
        footer.append(PnwUtil.resourcesToFancyString(aaTotalPositive));

        String type = "";
        OffshoreInstance offshore = db.getOffshore();
        double[] aaDeposits;
        if (offshore != null && offshore.getGuildDB() != db) {
            type = "offshored";
            aaDeposits = offshore.getDeposits(db);
        } else if (db.isValidAlliance() && db.getOrNull(GuildDB.Key.API_KEY) != null){
            type = "bank";
            aaDeposits = PnwUtil.resourcesToArray(db.getAlliance().getStockpile());
        } else aaDeposits = null;
        if (aaDeposits != null) {
            if (PnwUtil.convertedTotal(aaDeposits) > 0) {
                for (int i = 0; i < aaDeposits.length; i++) {
                    aaTotalNet[i] = aaDeposits[i] - aaTotalNet[i];
                    aaTotalPositive[i] = aaDeposits[i] - aaTotalPositive[i];

                }
            }
            footer.append("\n**Net " + type + " (normalized)**:  Worth: $" + MathMan.format(PnwUtil.convertedTotal(aaTotalPositive)) + "\n`" + PnwUtil.resourcesToString(aaTotalPositive) + "`");
            footer.append("\n**Net " + type + "**:  Worth: $" + MathMan.format(PnwUtil.convertedTotal(aaTotalNet)) + "\n`" + PnwUtil.resourcesToString(aaTotalNet) + "`");
        }

        channel.create().embed("AA Total", footer.toString())
                        .append(response.toString())
                                .send();
        return null;
    }

    @Command(desc = "Set the withdrawal limit (per interval) of a banker", aliases = {"setTransferLimit", "setWithdrawLimit", "setWithdrawalLimit", "setBankLimit"})
    @RolePermission(Roles.ADMIN)
    public String setTransferLimit(@Me GuildDB db, Set<DBNation> nations, double limit) {
        db.getOrThrow(GuildDB.Key.BANKER_WITHDRAW_LIMIT); // requires to be set

        StringBuilder response = new StringBuilder();
        for (DBNation nation : nations) {
            db.getHandler().setWithdrawLimit(nation.getNation_id(), limit);
            response.append("Set withdraw limit of: " + nation.getNationUrl() + " to $" + MathMan.format(limit) + "\n");
        }
        response.append("Done!");
        return response.toString();
    }

    @Command(desc = "Set nation's internal taxrate\n" +
        "See also: `{prefix}SetTaxRate` and `{prefix}KeyStore TAX_BASE`")
    @RolePermission(value = Roles.ECON)
    public String setInternalTaxRate(@Me GuildDB db, Set<DBNation> nations, TaxRate taxRate) {
        if (taxRate.money < -1 || taxRate.money > 100 || taxRate.resources < -1 || taxRate.resources > 100) throw new IllegalArgumentException("Invalid taxrate: " + taxRate);

        DBAlliance aa = db.getAlliance();
        if (aa == null) throw new IllegalArgumentException("This guild is not registered to an alliance");

        StringBuilder response = new StringBuilder();

        for (DBNation nation : nations) {
            if (nation.getAlliance_id() != aa.getAlliance_id()) throw new IllegalArgumentException("Nation: " + nation.getNationUrl() + " is not in " + aa.getUrl());
            if (nation.getPosition() <= 1) throw new IllegalArgumentException("Nation: " + nation.getNationUrl() + " is not a member");
            db.setMeta(nation.getNation_id(), NationMeta.TAX_RATE, new byte[]{(byte) taxRate.money, (byte) taxRate.resources});
            response.append("Set " + nation.getNationUrl() + " taxrate to " + taxRate + "\n");
        }

        response.append("Done!");
        return response.toString();
    }

    public void largest(Map<ResourceType, Double> total, Map<ResourceType, Double> local) {
        for (Map.Entry<ResourceType, Double> entry : local.entrySet()) {
            ResourceType type = entry.getKey();
            total.put(type, Math.max(entry.getValue(), total.getOrDefault(type, 0d)));
            if (total.get(type) <= 0) {
                total.remove(type);
            }
        }
    }

    @Command(desc = "Get a sheet of ingame transfers for nations")
    @RolePermission(value = Roles.ECON)
    public String getIngameNationTransfers(@Me IMessageIO channel, @Me GuildDB db, Set<NationOrAlliance> senders, Set<NationOrAlliance> receivers, @Default("%epoch%") @Timestamp long timeframe, @Switch("s") SpreadSheet sheet) throws IOException, GeneralSecurityException {
        if (sheet == null) sheet = SpreadSheet.create(db, GuildDB.Key.BANK_TRANSACTION_SHEET);
        Set<Long> senderIds = senders.stream().map(NationOrAllianceOrGuild::getIdLong).collect(Collectors.toSet());
        Set<Long> receiverIds = receivers.stream().map(NationOrAllianceOrGuild::getIdLong).collect(Collectors.toSet());
        List<Transaction2> transactions = Locutus.imp().getBankDB().getTransactionsByBySenderOrReceiver(senderIds, receiverIds, timeframe);
        transactions.removeIf(transaction2 -> {
            NationOrAllianceOrGuild sender = transaction2.getSenderObj();
            NationOrAllianceOrGuild receiver = transaction2.getReceiverObj();
            return !senders.contains(sender) || !receivers.contains(receiver);
        });
        sheet.addTransactionsList(channel, transactions, true);
        return null;
    }

    @Command(desc = "Get a sheet of ingame transfers for nations")
    @RolePermission(value = Roles.ECON)
    public String IngameNationTransfersBySender(@Me IMessageIO channel, @Me GuildDB db, Set<NationOrAlliance> senders, @Default("%epoch%") @Timestamp long timeframe, @Switch("s") SpreadSheet sheet) throws IOException, GeneralSecurityException {
        if (sheet == null) sheet = SpreadSheet.create(db, GuildDB.Key.BANK_TRANSACTION_SHEET);
        Set<Long> senderIds = senders.stream().map(NationOrAllianceOrGuild::getIdLong).collect(Collectors.toSet());
        List<Transaction2> transactions = Locutus.imp().getBankDB().getTransactionsByBySender(senderIds, timeframe);
        transactions.removeIf(tx -> !senders.contains(tx.getSenderObj()));
        sheet.addTransactionsList(channel, transactions, true);
        return null;
    }

    @Command(desc = "Get a sheet of ingame transfers for nations")
    @RolePermission(value = Roles.ECON)
    public String IngameNationTransfersByReceiver(@Me IMessageIO channel, @Me GuildDB db, Set<NationOrAlliance> receivers, @Default("%epoch%") @Timestamp long timeframe, @Switch("s") SpreadSheet sheet) throws IOException, GeneralSecurityException {
        if (sheet == null) sheet = SpreadSheet.create(db, GuildDB.Key.BANK_TRANSACTION_SHEET);
        Set<Long> receiverIds = receivers.stream().map(NationOrAllianceOrGuild::getIdLong).collect(Collectors.toSet());
        List<Transaction2> transactions = Locutus.imp().getBankDB().getTransactionsByByReceiver(receiverIds, timeframe);
        transactions.removeIf(tx -> !receivers.contains(tx.getReceiverObj()));
        sheet.addTransactionsList(channel, transactions, true);
        return null;
    }

    @Command(desc = "Convert negative deposits to another resource")
    @RolePermission(value = Roles.ECON)
    public String convertNegativeDeposits(@Me IMessageIO channel, @Me GuildDB db, @Me User user, @Me DBNation me, Set<DBNation> nations, @Default("manu,raws,food") List<ResourceType> negativeResources, @Default("money") ResourceType convertTo, @Switch("g") boolean includeGrants, @Switch("t") DepositType depositType, @Switch("f") Double conversionFactor, @Switch("s") SpreadSheet sheet, @Default() @Switch("n") String note) throws IOException, GeneralSecurityException {
        if (nations.size() > 500) return "Too many nations > 500";
        // get deposits of nations
        // get negatives

        double convertValue = PnwUtil.convertedTotal(convertTo, 1);
        if (conversionFactor != null) convertValue /= conversionFactor;

        Map<NationOrAlliance, double[]> toAddMap = new LinkedHashMap<>();
        double[] total = ResourceType.getBuffer();

        for (DBNation nation : nations) {
            double[] depo;
            if (depositType != null) {
                Map<DepositType, double[]> depoByCategory = nation.getDeposits(db, null, true, true, -1, 0L);
                depo = depoByCategory.get(depositType);
                if (depo == null) continue;
            } else {
                depo = nation.getNetDeposits(db, null, true, true, includeGrants, -1, 0L);
            }
            double[] amtAdd = ResourceType.getBuffer();
            boolean add = false;
            for (ResourceType type : ResourceType.values) {
                if (!negativeResources.contains(type) || type == convertTo) continue;
                double currAmt = depo[type.ordinal()];
                if (currAmt < -0.01) {
                    add = true;
                    double newAmt = PnwUtil.convertedTotal(type, -currAmt) / convertValue;
                    amtAdd[type.ordinal()] = -currAmt;
                    amtAdd[convertTo.ordinal()] += -newAmt;
                }
            }
            if (add) {
                total = PnwUtil.add(total, amtAdd);
                toAddMap.put(nation, amtAdd);
            }
        }

        if (toAddMap.isEmpty()) return "No deposits need to be adjusted (" + nations.size() + " nations checked)";
        if (sheet == null) sheet = SpreadSheet.create(db, GuildDB.Key.TRANSFER_SHEET);

        TransferSheet txSheet = new TransferSheet(sheet).write(toAddMap).build();

        if (note == null) {
            if (depositType != null) {
                note = "#" + depositType.name().toLowerCase(Locale.ROOT);
            } else {
                note = "#deposit";
            }
        }

        CM.deposits.addSheet cmd = CM.deposits.addSheet.cmd.create(txSheet.getURL(), note, null, null);
        String title = "Addbalance";
        String body = "Total: \n`" + PnwUtil.resourcesToString(total) + "`\nWorth: ~$" + MathMan.format(PnwUtil.convertedTotal(total));
        String emoji = "Confirm";


        channel.create().embed(title, body)
                        .commandButton(cmd, emoji)
                                .send();
        return null;
    }

    @Command(desc = "Get a sheet of internal transfers for nations")
    @RolePermission(value = Roles.ECON)
    public String getNationsInternalTransfers(@Me IMessageIO channel, @Me GuildDB db, Set<DBNation> nations, @Default("999d") @Timestamp long timeframe, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (sheet == null) sheet = SpreadSheet.create(db, GuildDB.Key.BANK_TRANSACTION_SHEET);
        if (nations.size() > 1000) return "Too many nations >1000";

        List<Transaction2> transactions = new ArrayList<>();
        for (DBNation nation : nations) {
            List<Transaction2> offsets = db.getDepositOffsetTransactions(nation.getNation_id());
            transactions.addAll(offsets);
        }
        transactions.removeIf(f -> f.tx_datetime < timeframe);

        sheet.addTransactionsList(channel, transactions, true);
        return null;
    }

    @Command(desc = "Get a sheet of transfers")
    @RolePermission(value = Roles.ECON, root = true)
    public String getIngameTransactions(@Me IMessageIO channel, @Me GuildDB db, @Default NationOrAlliance sender, @Default NationOrAlliance receiver, @Default NationOrAlliance banker, @Default("%epoch%") @Timestamp long timeframe, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (sheet == null) sheet = SpreadSheet.create(db, GuildDB.Key.BANK_TRANSACTION_SHEET);
        List<Transaction2> transactions = Locutus.imp().getBankDB().getAllTransactions(sender, receiver, banker, timeframe, null);
        if (transactions.size() > 10000) return "Timeframe is too large, please use a shorter period";

        sheet.addTransactionsList(channel, transactions, true);
        return null;
    }

    @Command(desc = "Get a sheet of a nation or alliances transactions (excluding taxes)")
    @RolePermission(value = Roles.ECON)
    public String transactions(@Me IMessageIO channel, @Me GuildDB db, @Me User user, NationOrAllianceOrGuild nationOrAllianceOrGuild, @Default("%epoch%") @Timestamp long timeframe, @Default("false") boolean useTaxBase, @Default("true") boolean useOffset, @Switch("s") SpreadSheet sheet,
                               @Switch("o") boolean onlyOffshoreTransfers) throws GeneralSecurityException, IOException {
        if (sheet == null) sheet = SpreadSheet.create(db, GuildDB.Key.BANK_TRANSACTION_SHEET);

        if (onlyOffshoreTransfers && nationOrAllianceOrGuild.isNation()) return "Only Alliance/Guilds can have an offshore account";

        List<Transaction2> transactions = new ArrayList<>();
        if (nationOrAllianceOrGuild.isNation()) {
            DBNation nation = nationOrAllianceOrGuild.asNation();
            List<Map.Entry<Integer, Transaction2>> natTrans = nation.getTransactions(db, null, useTaxBase, useOffset, 0, timeframe);
            for (Map.Entry<Integer, Transaction2> entry : natTrans) {
                transactions.add(entry.getValue());
            }
        } else if (nationOrAllianceOrGuild.isAlliance()) {
            DBAlliance alliance = nationOrAllianceOrGuild.asAlliance();

            // if this alliance - get the transactions in the offshore
            Integer thisAA = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
            if (thisAA != null && thisAA.equals(alliance.getAlliance_id())) {
                OffshoreInstance offshore = db.getOffshore();
                transactions.addAll(offshore.getTransactionsAA(thisAA, true));
            } else if (thisAA != null && db.getOffshore() != null && db.getOffshore().getAllianceId() == thisAA) {
                OffshoreInstance offshore = db.getOffshore();
                transactions.addAll(offshore.getTransactionsAA(alliance.getAlliance_id(), true));
            } else {
                transactions.addAll(db.getTransactionsById(alliance.getAlliance_id(), 2));
            }

            if (onlyOffshoreTransfers) {
                Set<Long> offshoreAAs = db.getOffshore().getOffshoreAAs();
                transactions.removeIf(f -> f.sender_type == 1 || f.receiver_type == 1);
                transactions.removeIf(f -> f.tx_id != -1 && f.sender_id != 0 && f.receiver_id != 0 && !offshoreAAs.contains(f.sender_id) && !offshoreAAs.contains(f.receiver_id));
            }

        } else if (nationOrAllianceOrGuild.isGuild()) {
            GuildDB otherDB = nationOrAllianceOrGuild.asGuild();

            // if this guild - get the transactions in the offshore
            if (otherDB.getIdLong() == db.getIdLong() || (otherDB.getOffshoreDB() == db && onlyOffshoreTransfers)) {
                OffshoreInstance offshore = db.getOffshore();
                transactions.addAll(offshore.getTransactionsGuild(otherDB.getIdLong(), true));
            } else {
                transactions.addAll(db.getTransactionsById(otherDB.getGuild().getIdLong(), 3));
            }
        }

        sheet.addTransactionsList(channel, transactions, true);
        return null;
    }

    private Map<UUID, Map<NationOrAlliance, Map<ResourceType, Double>>> approvedTransfer = new ConcurrentHashMap<>();

    @Command(desc = "Send multiple transfers to nations/alliances according to a sheet",
            help = "The transfer sheet columns must be `nations` (which has the nations or alliance name/id/url), " +
                    "and then there must be a column named for each resource type you wish to transfer")
    @RolePermission(value = Roles.ECON)
    public String transferBulk(@Me IMessageIO io, @Me JSONObject command, @Me GuildDB db, @Me User user, @Me DBNation me, TransferSheet sheet, String note, @Switch("f") boolean force, @Switch("k") UUID key) {
        double totalVal = 0;

        int nations = 0;
        int alliances = 0;

        Set<DBNation> inactiveOrVM = new HashSet<>();

        Set<Integer> memberAlliances = new HashSet<>();
        Map<NationOrAlliance, Map<ResourceType, Double>> transfers = sheet.getTransfers();
        Map<ResourceType, Double> totalRss = new LinkedHashMap<>();
        for (Map.Entry<NationOrAlliance, Map<ResourceType, Double>> entry : transfers.entrySet()) {
            NationOrAlliance natOrAA = entry.getKey();
            if (natOrAA.isAlliance()) alliances++;
            if (natOrAA.isNation()) {
                nations++;
                memberAlliances.add(natOrAA.asNation().getAlliance_id());
            }
            for (Map.Entry<ResourceType, Double> resourceEntry : entry.getValue().entrySet()) {
                if (resourceEntry.getValue() > 0) {
                    ResourceType type = resourceEntry.getKey();
                    Double amt = resourceEntry.getValue();
                    totalVal += PnwUtil.convertedTotal(type, amt);
                    totalRss.put(type, totalRss.getOrDefault(type, 0d) + amt);
                }
            }
        }

        if (!force) {

            String title = "Transfer ~$" + MathMan.format(totalVal) + " to ";
            if (nations > 0) {
                title += "(" + nations + " nations";
                if (memberAlliances.size() > 1) {
                    title += " in " + memberAlliances.size() + " AAs";
                }
                title += ")";
            }
            if (alliances > 0) {
                title += "(" + alliances + " AAs)";
            }

            StringBuilder desc = new StringBuilder();
            desc.append("Note: " + note);
            desc.append("\nTotal: `" + PnwUtil.resourcesToString(totalRss) + "`");

            for (Map.Entry<NationOrAlliance, Map<ResourceType, Double>> entry : transfers.entrySet()) {
                NationOrAlliance natOrAA = entry.getKey();
                if (!natOrAA.isNation()) continue;
                DBNation nation = natOrAA.asNation();
                if (nation.getVm_turns() > 0) {
                    desc.append("\nVM: " + nation.getNationUrlMarkup(true));
                } else if (nation.getActive_m() > 7200) {
                    desc.append("\nINACTIVE: " + nation.getNationUrlMarkup(true));
                }
            }

            key = UUID.randomUUID();
            approvedTransfer.put(key, transfers);
            String commandStr = command.put("force", "true").put("key", key).toString();
            io.create().embed(title, desc.toString())
                            .commandButton(commandStr, "Confirm")
                                    .send();
            return null;
        }
        if (key != null) {
            Map<NationOrAlliance, Map<ResourceType, Double>> approvedAmounts = approvedTransfer.get(key);
            if (approvedAmounts == null) return "No amount has been approved for transfer. Please try again";
            if (!approvedAmounts.equals(transfers)) {
                return "The confirmed amount does not match. Please try again";
            }
        }

        StringBuilder output = new StringBuilder();

        OffshoreInstance offshore = db.getOffshore();
        if (offshore == null) {
            return "No offshore is set";
        }
        Map<NationOrAlliance, Map.Entry<OffshoreInstance.TransferStatus, String>> results = new HashMap<>();
        Map<ResourceType, Double> totalSent = new HashMap<>();

        for (Map.Entry<NationOrAlliance, Map<ResourceType, Double>> entry : transfers.entrySet()) {
            NationOrAlliance natOrAA = entry.getKey();
            double[] amount = PnwUtil.resourcesToArray(entry.getValue());

            Map.Entry<OffshoreInstance.TransferStatus, String> result = null;
            try {
                result = offshore.transferFromDeposits(me, db, natOrAA, amount, note);
            } catch (IllegalArgumentException e) {
                result = new AbstractMap.SimpleEntry<>(OffshoreInstance.TransferStatus.OTHER, e.getMessage());
            }

            output.append(natOrAA.getUrl() + "\t" + natOrAA.isAlliance() + "\t" + StringMan.getString(amount) + "\t" + result.getKey() + "\t" + "\"" + result.getValue() + "\"");
            output.append("\n");
            io.send(PnwUtil.resourcesToString(amount) + " -> " + natOrAA.getUrl() + " | **" + result.getKey() + "**: " + result.getValue());
            results.put(natOrAA, result);
            if (result.getKey() == OffshoreInstance.TransferStatus.SUCCESS || result.getKey() == OffshoreInstance.TransferStatus.ALLIANCE_BANK) {
                totalSent = PnwUtil.add(totalSent, PnwUtil.resourcesToMap(amount));
            }
        }

        io.create().file("transfer-results.csv", output.toString()).append("Done!\nTotal sent: `" + PnwUtil.resourcesToString(totalSent) + "`").send();
        return null;
    }

    @Command
    @RolePermission(value = Roles.ADMIN)
    public String unlockTransfers(@Me GuildDB db, @Me User user, NationOrAllianceOrGuild alliance) {
        OffshoreInstance offshore = db.getOffshore();
        if (offshore == null) return "No offshore is set";
        if (offshore.getGuildDB() != db) return "Please run in the offshore server";
        Set<Long> coalition = offshore.getGuildDB().getCoalitionRaw(Coalition.FROZEN_FUNDS);
        if (alliance.isAlliance()) {
            if (coalition.contains((long) alliance.getAlliance_id())) return "Please use `!removecoalition FROZEN_FUNDS " +  alliance.getAlliance_id() + "`";
            GuildDB otherDb = alliance.asAlliance().getGuildDB();
            if (otherDb != null && coalition.contains(otherDb.getIdLong()))return "Please use `!removecoalition FROZEN_FUNDS " + otherDb.getIdLong() + "`";
        } else {
            if (coalition.contains((long) alliance.getIdLong())) return "Please use `!removecoalition FROZEN_FUNDS " +  alliance.getIdLong() + "`";
            Integer aaId = alliance.asGuild().getOrNull(GuildDB.Key.ALLIANCE_ID);
            if (aaId != null && coalition.contains((long) aaId))return "Please use `!removecoalition FROZEN_FUNDS " + aaId + "`";
        }

        if (alliance.isGuild()) {
            offshore.disabledGuilds.remove(alliance.asGuild().getGuild().getIdLong());
        } else if (alliance.isAlliance()) {
            GuildDB guild = alliance.asAlliance().getGuildDB();
            if (guild == null) return "No guild found for AA:" + alliance;
            offshore.disabledGuilds.remove(guild.getGuild().getIdLong());
        } else {
           return alliance + " must be a guild or alliance";
        }
        return "Done!";
    }

    @Command
    @IsAlliance
    @RolePermission(Roles.ECON_LOW_GOV)
    public String setNationInternalTaxRates(@Me IMessageIO channel, @Me GuildDB db, @Default() Set<DBNation> nations, @Switch("p") boolean ping) throws Exception {
        if (nations == null) {
            nations = new LinkedHashSet<>(DiscordUtil.getNationsByAA(db.getAlliance_id()));
        }
        List<String> messages = new ArrayList<>();
        Map<DBNation, Map.Entry<TaxRate, String>> result = db.getHandler().setNationInternalTaxRate(nations, s -> messages.add(s));
        if (result.isEmpty()) {
            return "Done! No changes made to internal tax rates";
        }

        messages.add("\nResult:");
        for (Map.Entry<DBNation, Map.Entry<TaxRate, String>> entry : result.entrySet()) {
            DBNation nation = entry.getKey();
            Map.Entry<TaxRate, String> bracketInfo = entry.getValue();

            String message = " - " + nation.getNation() + ": moved internal rates to `" + bracketInfo.getKey() + "` for reason: `" + bracketInfo.getValue() + "`";
            if (ping) {
                User user = nation.getUser();
                if (user != null) message += " | " + user.getAsMention();
            }
            messages.add(message);
        }

        messages.add("\n**About Internal Tax Rates**\n" +
                " - Internal tax rates are NOT ingame tax rates\n" +
                " - Internal rates determine what money%/resource% of ingame city taxes goes to the alliance\n" +
                " - The remainder is added to a nation's " + CM.deposits.check.cmd.toSlashMention() + "");

        return StringMan.join(messages, "\n");
    }

    @Command(desc = "List the assigned taxrate if REQUIRED_TAX_BRACKET or REQUIRED_INTERNAL_TAXRATE are set\n" +
            "Note: this command does set nations brackets. See: `{prefix}setNationTaxBrackets` and `{prefix}setNationInternalTaxRates` ")
    @IsAlliance
    @RolePermission(Roles.ECON_LOW_GOV)
    public String listRequiredTaxRates(@Me GuildDB db, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (sheet == null) sheet = SpreadSheet.create(db, GuildDB.Key.TAX_BRACKET_SHEET);
        List<Object> header = new ArrayList<>(Arrays.asList(
                "nation",
                "cities",
                "assigned_bracket_category",
                "assigned_bracket_name",
                "assigned_bracket_id",
                "assigned_bracket_rate",
                "assigned_internal_category",
                "assigned_internal_rate"
        ));

        Auth auth = db.getAuth(AlliancePermission.TAX_BRACKETS);
        Map<Integer, TaxBracket> brackets = auth.getTaxBrackets();

        LinkedHashSet<DBNation> nations = new LinkedHashSet<>(DiscordUtil.getNationsByAA(db.getAlliance_id()));

        Map<NationFilterString, Integer> requiredBrackets = db.getOrNull(GuildDB.Key.REQUIRED_TAX_BRACKET);
        Map<NationFilterString, TaxRate> requiredInternalRates = db.getOrNull(GuildDB.Key.REQUIRED_INTERNAL_TAXRATE);

        if (requiredBrackets == null) requiredBrackets = Collections.emptyMap();
        if (requiredInternalRates == null) requiredInternalRates = Collections.emptyMap();

        sheet.setHeader(header);

        for (DBNation nation : nations) {
            header.set(0, MarkupUtil.sheetUrl(nation.getNation(), PnwUtil.getUrl(nation.getNation_id(), false)));
            header.set(1, nation.getCities() + "");

            Map.Entry<NationFilterString, Integer> requiredBracket = requiredBrackets.entrySet().stream().filter(f -> f.getKey().test(nation)).findFirst().orElse(null);
            Map.Entry<NationFilterString, TaxRate> requiredInternal = requiredInternalRates.entrySet().stream().filter(f -> f.getKey().test(nation)).findFirst().orElse(null);

            header.set(2, requiredBracket != null ? requiredBracket.getKey().getFilter() : "");
            int taxId = requiredBracket != null ? requiredBracket.getValue() : -1;
            TaxBracket bracket = brackets.get(taxId);
            header.set(3, bracket != null ? bracket.getName() : "");
            header.set(4, bracket != null ? bracket.taxId : "");
            header.set(5, bracket != null ? "'" + bracket.getTaxRate().toString() + "'" : "");

            header.set(6, requiredInternal != null ? requiredInternal.getKey().getFilter() : "");
            header.set(7, requiredInternal != null ? requiredInternal.getValue().toString() : null);

            sheet.addRow(header);
        }

        sheet.clear("A:Z");
        sheet.set(0, 0);
        return "<" + sheet.getURL() + ">";
    }


    @Command
    @IsAlliance
    @RolePermission(Roles.ECON_LOW_GOV)
    public String setNationTaxBrackets(@Me IMessageIO channel, @Me GuildDB db, @Default() Set<DBNation> nations, @Switch("p") boolean ping) throws Exception {
        if (nations == null) {
            nations = new LinkedHashSet<>(DiscordUtil.getNationsByAA(db.getAlliance_id()));
        }
        List<String> messages = new ArrayList<>();
        Map<DBNation, Map.Entry<TaxBracket, String>> result = db.getHandler().setNationTaxBrackets(nations, s -> messages.add(s));
        if (result.isEmpty()) {
            return "Done! No changes made to ingame brackets";
        }

        messages.add("\nResult:");
        for (Map.Entry<DBNation, Map.Entry<TaxBracket, String>> entry : result.entrySet()) {
            DBNation nation = entry.getKey();
            Map.Entry<TaxBracket, String> bracketInfo = entry.getValue();

            String message = " - " + nation.getNation() + ": moved city bracket to `" + bracketInfo.getKey() + "` for reason: `" + bracketInfo.getValue() + "`";
            if (ping) {
                User user = nation.getUser();
                if (user != null) message += " | " + user.getAsMention();
            }
            messages.add(message);
        }

        messages.add("\n**About Ingame City Tax Brackets**\n" +
                " - A tax bracket decides what % of city income (money/resources) goes to the alliance bank\n" +
                " - Funds from raiding, trading or daily login are never taxed\n" +
                " - Taxes bypass blockades\n" +
                " - Your internal tax rate will then determine what portion of city taxes go to your " + CM.deposits.check.cmd.toSlashMention() + "");

        return StringMan.join(messages, "\n");
    }

    @Command(aliases = {"acceptTrades", "acceptTrade"})
    @RolePermission(value = Roles.MEMBER)
    public String acceptTrades(@Me GuildDB db, @Me DBNation me, DBNation receiver, @Switch("f") boolean force) throws Exception {
        OffshoreInstance offshore = db.getOffshore();
        if (offshore == null) return "No offshore is set in this guild: <https://docs.google.com/document/d/1QkN1FDh8Z8ENMcS5XX8zaCwS9QRBeBJdCmHN5TKu_l8/>";

        GuildDB receiverDB = Locutus.imp().getGuildDBByAA(receiver.getAlliance_id());
        if (receiverDB == null) return "Receiver is not in a guild with locutus";

        User receiverUser = receiver.getUser();
        if (receiverUser == null) return "Receiver is not verified";
        Member member = receiverDB.getGuild().getMember(receiverUser);
        if (receiver.getActive_m() > 1440) return "Receive is offline for >24 hours";
        if (!force && receiver.getNumWars() > 0 && (member == null || member.getOnlineStatus() != OnlineStatus.ONLINE)) return "Receiver is not online on discord. (add `-f` to ignore this check)";

        Auth auth = receiver.getAuth(null);
        if (auth == null) return "Receiver is not authenticated with Locutus: " + CM.credentials.login.cmd.toSlashMention() + "";

        Map.Entry<Boolean, String> result = auth.acceptAndOffshoreTrades(db, me.getNation_id());
        if (!result.getKey()) {
            return "__**ERROR: No funds have been added to your account**__\n" +
                    result.getValue();
        } else {
            return result.getValue();
        }
    }

    @Command(desc = "Get a sheet of a nation tax deposits over a period")
    @RolePermission(value = Roles.ECON)
    public String taxDeposits(@Me GuildDB db, Set<DBNation> nations, @Arg("Set to 0/0 to include all taxes") @Default() TaxRate baseTaxRate, @Default() @Timestamp Long startDate, @Default() @Timestamp Long endDate, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        int allianceId = db.getOrThrow(GuildDB.Key.ALLIANCE_ID);

        if (startDate == null) startDate = 0L;
        if (endDate == null) endDate = Long.MAX_VALUE;

        List<BankDB.TaxDeposit> taxes = Locutus.imp().getBankDB().getTaxesByAA(allianceId);
        Map<Integer, double[]> totalByNation = new HashMap<>();

        int[] baseArr = baseTaxRate == null ? null : baseTaxRate.toArray();
        int[] aaBase = db.getOrNull(GuildDB.Key.TAX_BASE);

        for (BankDB.TaxDeposit tax : taxes) {
            if (tax.date < startDate || tax.date > endDate) continue;
            DBNation nation = DBNation.byId(tax.nationId);
            if (!nations.contains(nation)) continue;

            int[] internalRate = new int[] {tax.internalMoneyRate, tax.internalResourceRate};
            if (baseArr != null && baseArr[0] >= 0) internalRate[0] = baseArr[0];
            if (baseArr != null && baseArr[1] >= 0) internalRate[1] = baseArr[1];
            if (internalRate[0] < 0) internalRate[0] = aaBase != null && aaBase[0] >= 0 ? aaBase[0] : 0;
            if (internalRate[1] < 0) internalRate[1] = aaBase != null && aaBase[1] >= 0 ? aaBase[1] : 0;

            tax.multiplyBase(internalRate);
            double[] taxDepo = totalByNation.computeIfAbsent(tax.nationId, f -> ResourceType.getBuffer());
            ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, taxDepo, tax.resources);
        }

        TransferSheet txSheet;
        if (sheet == null) {
            txSheet = new TransferSheet(db);
        } else {
            txSheet = new TransferSheet(sheet.getSpreadsheetId());
        }
        Map<NationOrAlliance, double[]> transfers = new HashMap<>();
        for (Map.Entry<Integer, double[]> entry : totalByNation.entrySet()) {
            transfers.put(DBNation.byId(entry.getKey()), entry.getValue());
        }
        txSheet.write(transfers).build();
        return txSheet.getURL();
    }

    @Command(desc = "Get a sheet of a nation tax deposits over a period")
    @RolePermission(value = Roles.ECON)
    public String taxRecords(@Me GuildDB db, DBNation nation, @Default() @Timestamp Long startDate, @Default() @Timestamp Long endDate, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        int allianceId = db.getOrThrow(GuildDB.Key.ALLIANCE_ID);

        if (startDate == null) startDate = 0L;
        if (endDate == null) endDate = Long.MAX_VALUE;

        List<BankDB.TaxDeposit> taxes = Locutus.imp().getBankDB().getTaxesPaid(nation.getNation_id(), allianceId);

        if (sheet == null) sheet = SpreadSheet.create(db, GuildDB.Key.TAX_RECORD_SHEET);

        List<Object> header = new ArrayList<>(Arrays.asList("nation", "date", "taxrate", "internal_taxrate"));
        for (ResourceType value : ResourceType.values) {
            if (value != ResourceType.CREDITS) header.add(value.name());
        }
        sheet.setHeader(header);

        for (BankDB.TaxDeposit tax : taxes) {
            if (tax.date < startDate || tax.date > endDate) continue;
            header.set(0, MarkupUtil.sheetUrl(nation.getNation(), nation.getNationUrl()));
            header.set(1, TimeUtil.YYYY_MM_DD_HH_MM_SS.format(new Date(tax.date)));
            header.set(2, tax.moneyRate + "/" + tax.resourceRate);
            header.set(3, tax.internalMoneyRate + "/" + tax.internalResourceRate);
            int i = 0;
            for (ResourceType type : ResourceType.values) {
                if (type != ResourceType.CREDITS) header.set(4 + i++, tax.resources[type.ordinal()]);
            }
            sheet.addRow(header);
        }
        sheet.clear("A:Z");
        sheet.set(0, 0);
        return "<" + sheet.getURL() + ">";
    }

    @Command(desc = "Send from your alliance offshore account to another account (internal transfer)")
    @RolePermission(value = Roles.ECON)
    public String sendAA(@Me OffshoreInstance offshore, @Me IMessageIO channel, @Me JSONObject command, @Me GuildDB senderDB, @Me User user, @Me DBAlliance alliance, @Me Rank rank, @Me DBNation me, NationOrAllianceOrGuild receiver, @AllianceDepositLimit Map<ResourceType, Double> amount, @Switch("f") boolean confirm) {
        if (!receiver.isAlliance() && !receiver.isGuild()) return "Alliance -> Nation is not supported. Please use " + CM.transfer.resources.cmd.toSlashMention() + " or " + CM.deposits.add.cmd.toSlashMention() + "";
        for (Map.Entry<ResourceType, Double> entry : amount.entrySet()) {
            if (entry.getValue() <= 0 || entry.getValue().isNaN()) return "You cannot send negative amounts for " + entry.getKey();
        }
        Map<ResourceType, Double> negative = PnwUtil.subResourcesToA(new HashMap<>(), amount);

        String emoji = "Confirm";
        String cmd = CM.transfer.internal.alliance.cmd.create(receiver.getQualifiedName(), PnwUtil.resourcesToString(amount), "true").toCommandArgs();
        String name;
        if (receiver.isAlliance()) name = "AA:";
        else if (receiver.isNation()) name = "Nation:";
        else if (receiver.isGuild()) name = "Guild:";
        else throw new IllegalArgumentException("Invalid receiver: " + receiver);
        name += receiver.getName();

        synchronized (OffshoreInstance.BANK_LOCK) {
            double[] deposits = offshore.getDeposits(senderDB);
            deposits = PnwUtil.normalize(deposits);

            for (Map.Entry<ResourceType, Double> entry : amount.entrySet()) {
                if (deposits[entry.getKey().ordinal()] < entry.getValue()) {
                    return "Your alliance does not have " + MathMan.format(entry.getValue()) + "x" + entry.getKey();
                }
            }

            if (receiver.isNation()) {
                return "To send to a nation, please use " + CM.transfer.resources.cmd.toSlashMention() + "";
            }

            GuildDB receiverDB = null;
            if (receiver.isGuild()) {
                receiverDB = receiver.asGuild();
            }
            if (receiver.isAlliance()) {
                DBAlliance aa = receiver.asAlliance();
                receiverDB = aa.getGuildDB();
                if (receiverDB == null) return "Receiver AA" + aa + " does not have Locutus. This command is for internal transfers. Please use " + CM.transfer.resources.cmd.toSlashMention() + " to move funds ingame";
            }
            if (receiverDB == null) {
                return "Invalid receiver type: " + receiver;
            }
            OffshoreInstance receiverOffshore = receiverDB.getOffshore();
            if (receiverOffshore == null) return "Receiver does not have an offshore. Please use " + CM.transfer.resources.cmd.toSlashMention() + "";

            if (!confirm) {
                String title = "Send to " + name;
                String url = receiver.getUrl();
                if (url == null) url = receiver.getIdLong() + "";
                StringBuilder body = new StringBuilder("**This will send from the alliance/guild offshore**");
                body.append("\nAmount: `" + PnwUtil.resourcesToString(amount) + "`");
                body.append("\nWorth: ~$" + MathMan.format(PnwUtil.convertedTotal(amount)));
                body.append("\nSender: " + senderDB.getGuild());
                body.append("\nSender Offshore: " + PnwUtil.getMarkdownUrl(offshore.getAllianceId(), true));
                body.append("\nReceiver: " + MarkupUtil.markdownUrl(name, url));
                body.append("\nReceiver Offshore: " + PnwUtil.getMarkdownUrl(receiverOffshore.getAllianceId(), true));
                body.append("\n\nPress `" + emoji + "` to confirm");

                channel.create().confirmation(title, body.toString(), command, "confirm").send();
                return null;
            }

            String note = "#deposit";
            long tx_datetime = System.currentTimeMillis();

            if (receiverOffshore != offshore) {
                return "Internal transfers to other offshores is not currently supported. Please use " + CM.transfer.resources.cmd.toSlashMention() + "";
            } else {
                GuildDB offshoreDB = offshore.getGuildDB();
                int banker = me.getNation_id();

                double[] amountArr = PnwUtil.resourcesToArray(amount);
                offshoreDB.addTransfer(tx_datetime, receiver, senderDB, banker, note, amountArr);
                return "Sent `" + PnwUtil.resourcesToString(amount) + "` from " + senderDB.getGuild() + " to " + receiverDB.getGuild();
            }
        }
    }

    @Command(desc = "Send from your nation deposits to another nation or alliance")
    @HasOffshore
    @RolePermission(any = true, value = {Roles.ECON_WITHDRAW_SELF, Roles.ECON})
    public String send(@Me JSONObject command, @Me IMessageIO channel, @Me GuildDB db, @Me User user, @Me DBAlliance alliance, @Me Rank rank, @Me DBNation me, @Me Map<ResourceType, Double> deposits, NationOrAllianceOrGuild receiver, @NationDepositLimit Map<ResourceType, Double> amount, @Switch("f") boolean confirm) {
//        if (!receiver.isNation()) return "Nation -> Alliance transfers are not implemented yet";

        GuildDB receiverDb = null;
        DBNation receiverNation = null;
        if (receiver.isNation()) {
            receiverNation = receiver.asNation();
            if (receiverNation.getPosition() <= 1) return "Receiver is not a member";
            if (receiverNation.getActive_m() > 10000) return "Receiver is inactive ingame";
            if (receiverNation.getVm_turns() > 0) return "Receiver is in VM";
            receiverDb = Locutus.imp().getGuildDBByAA(receiverNation.getAlliance_id());
        } else if (receiver.isAlliance()) {
            DBAlliance receiverAA = receiver.asAlliance();
            receiverDb = receiverAA.getGuildDB();
        } else if (receiver.isGuild()) {
            receiverDb = receiver.asGuild();
        }
        if (receiverDb == null) {
            return "Receiver does not have Locutus: " + receiver;
        }
        if (receiverNation != null && receiverNation.getNation_id() == me.getNation_id()) {
            return "You cannot send funds to yourself (this command is for doing an internal transfer to another member)";
        }

        boolean admin = Roles.ECON.hasOnRoot(user);

        synchronized (OffshoreInstance.BANK_LOCK) {
            GuildMessageChannel rssChannel = db.getOrNull(GuildDB.Key.RESOURCE_REQUEST_CHANNEL);
            if (rssChannel == null) return "Please have an admin use. " + CM.settings.cmd.create(GuildDB.Key.RESOURCE_REQUEST_CHANNEL.name(), "#someChannel") + " in the receiving server";
            if (channel.getIdLong() != rssChannel.getIdLong()) return "Please use the transfer command in " + rssChannel.getAsMention();

            if (rank.id < Rank.MEMBER.id) throw new IllegalArgumentException("You are not member");

            Integer guildAAId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
            if (guildAAId == null) return "No alliance set. (see: " + CM.settings.cmd.create(GuildDB.Key.ALLIANCE_ID.name(), null).toSlashCommand() + ")";

            if (db.getOrNull(GuildDB.Key.MEMBER_CAN_WITHDRAW) != Boolean.TRUE) return "Member transfers are not enabled. (see: " + CM.settings.cmd.create(GuildDB.Key.MEMBER_CAN_WITHDRAW.name(), "true") + ")";

            if (me.getVm_turns() > 0) return "You are in VM";
            if (me.getActive_m() > 10000) return "You are inactive ingame";
            if (me.getPosition() <= 1) return "You are not a member";
            if (me.getAlliance_id() != guildAAId) return "You are not in: " + guildAAId;

//            if (receiverNation.getAlliance_id() != me.getAlliance_id()) return "You are not in the same alliance as " + receiverNation.getAlliance_id();

            deposits = PnwUtil.normalize(deposits);
            if (!admin) {
                if (PnwUtil.convertedTotal(deposits) <= 0) return "You do not have any deposits";

                for (Map.Entry<ResourceType, Double> entry : amount.entrySet()) {
                    if (entry.getValue() <= 0 || entry.getValue().isNaN())
                        return "You cannot transfer " + entry.getValue() + "x" + entry.getKey();
                    if (deposits.getOrDefault(entry.getKey(), -1d) + 0.01 < entry.getValue())
                        return "You do not have " + MathMan.format(entry.getValue()) + "x" + entry.getKey() + " (see: " + CM.deposits.check.cmd.toSlashMention() + ")";
                }
            }

            String note = "#deposit";

            long tx_datetime = System.currentTimeMillis();
            double[] amountArr = PnwUtil.resourcesToArray(amount);

            OffshoreInstance offshore = db.getOffshore();
            OffshoreInstance receiverOffshore = receiverDb.getOffshore();
            if (receiverDb != db) {
                if (offshore == null || receiverOffshore == null) return "Please use " + CM.transfer.resources.cmd.toSlashMention() + " to send to an nation not on a bank network";
                if (receiverOffshore != offshore) return "Please use " + CM.transfer.resources.cmd.toSlashMention() + " to send to an nation not on the same bank network";
                double[] allianceDeposits = PnwUtil.normalize(offshore.getDeposits(db));
                for (int i = 0; i < amountArr.length; i++) {
                    if (amountArr[i] > allianceDeposits[i]) {
                        return "You cannot transfer " + MathMan.format(amountArr[i]) + "x" + ResourceType.values[i];
                    }
                }
            }

            if (!confirm) {
                String emoji = "Confirm";

                String title = "Send " + PnwUtil.resourcesToString(amount) + " to " + receiver.getTypePrefix() + ":" + receiver.getName();
                String url = receiver.getUrl();
                if (url == null) url = receiver.getIdLong() + "";
                StringBuilder body = new StringBuilder("**This will send from the alliance/guild offshore**");
                body.append("\nAmount: `" + PnwUtil.resourcesToString(amount) + "`");
                body.append("\nWorth: ~$" + MathMan.format(PnwUtil.convertedTotal(amount)));
                body.append("\nSender: " + me.getNationUrlMarkup(true) + " | " + me.getAllianceUrlMarkup(true));
                body.append("\nSender Offshore: " + PnwUtil.getMarkdownUrl(offshore.getAllianceId(), true));
                body.append("\nReceiver: " + MarkupUtil.markdownUrl(receiver.getName(), url));
                body.append("\nReceiver Offshore: " + PnwUtil.getMarkdownUrl(receiverOffshore.getAllianceId(), true));
                body.append("\n\nPress `" + emoji + "` to confirm");

                channel.create().confirmation(title, body.toString(), command, "confirm").send();
                return null;
            }

            db.subtractBalance(tx_datetime, me, me.getNation_id(), note, amountArr);
            if (receiverDb != db) {
                // subtract from db
                offshore.getGuildDB().subtractBalance(tx_datetime, db, me.getNation_id(), note, amountArr);
            }
            if (receiverNation != null) {
                receiverDb.addBalance(tx_datetime, receiverNation, me.getNation_id(), note, amountArr);
            }
            if (receiverDb != db) {
                offshore.getGuildDB().addBalance(tx_datetime, receiverDb, me.getNation_id(), note, amountArr);
            }

            return me.getNation() + " successfully sent `" + PnwUtil.resourcesToString(amount) + "` (worth: $" + PnwUtil.convertedTotal(amount) + ") to " + receiver.getUrl() + ">)\n" +
                    "See (" + CM.deposits.check.cmd.toSlashMention() + ")";
        }
    }

    @Command(desc="Calculate a nations deposits/loans/taxes")
    @RolePermission(Roles.MEMBER)
    public String deposits(@Me Guild guild, @Me GuildDB db, @Me IMessageIO channel, @Me DBNation me, @Me User author, @Me GuildHandler handler, NationOrAllianceOrGuild nationOrAllianceOrGuild,
                           @Switch("o") Set<DBAlliance> offshores,
                           @Switch("c") Long timeCutoff,
                           @Switch("b") boolean includeBaseTaxes,
                           @Switch("o") boolean ignoreInternalOffsets,
                           @Switch("t") Boolean showTaxesSeparately,
                           @Switch("d") boolean replyInDMs
                           ) throws IOException {
        if (timeCutoff == null) timeCutoff = 0L;
        Set<Long> offshoreIds = offshores == null ? null : offshores.stream().map(f -> f.getIdLong()).collect(Collectors.toSet());
        if (offshoreIds != null) offshoreIds = PnwUtil.expandCoalition(offshoreIds);


        StringBuilder response = new StringBuilder();
        response.append("**" + nationOrAllianceOrGuild.getName() + "**:\n");
        List<String> footers = new ArrayList<>();

        Map<DepositType, double[]> accountDeposits = new HashMap<>();
        List<Transaction2> txList = new ArrayList<>();

        if (nationOrAllianceOrGuild.isAlliance()) {
            DBAlliance alliance = nationOrAllianceOrGuild.asAlliance();
            GuildDB otherDb = alliance.getGuildDB();

            if (otherDb == null) throw new IllegalArgumentException("No guild found for " + alliance);

            OffshoreInstance offshore = otherDb.getOffshore();

            if (!Roles.ECON.has(author, otherDb.getGuild()) && (offshore == null || !Roles.ECON.has(author, offshore.getGuildDB().getGuild()))) {
                return "You do not have permisssion to check another alliance's deposits";
            }

            if (offshore == null) {
                if (otherDb == db) {
                    Map<ResourceType, Double> stock = alliance.getStockpile();
                    accountDeposits.put(DepositType.DEPOSITS, PnwUtil.resourcesToArray(stock));
                } else {
                    return "No offshore is set. In this server, use " + CM.coalition.add.cmd.create("AA:" + alliance.getAlliance_id(), Coalition.OFFSHORE.name()) + " and from the offshore server use " + CM.coalition.add.cmd.create("AA:" + alliance.getAlliance_id(), Coalition.OFFSHORING.name()) + "";
                }
            } else if (otherDb != db && offshore.getGuildDB() != db) {
                return "You do not have permisssion to check another alliance's deposits";
            } else {
                // txList
                double[] deposits = PnwUtil.resourcesToArray(offshore.getDeposits(alliance.getAlliance_id(), true));
                accountDeposits.put(DepositType.DEPOSITS, deposits);
            }
        } else if (nationOrAllianceOrGuild.isGuild()) {
            GuildDB otherDb = nationOrAllianceOrGuild.asGuild();
            OffshoreInstance offshore = otherDb.getOffshore();
            if (offshore == null) return "No offshore is set. In this server, use " + CM.coalition.add.cmd.create(nationOrAllianceOrGuild.getIdLong() + "", Coalition.OFFSHORE.name()) + " and from the offshore server use " + CM.coalition.add.cmd.create(nationOrAllianceOrGuild.getIdLong() + "", Coalition.OFFSHORING.name()) + "";

            if (!Roles.ECON.has(author, offshore.getGuildDB().getGuild()) && !Roles.ECON.has(author, otherDb.getGuild())) {
                return "You do not have permission to check another guild's deposits";
            }
            // txList
            double[] deposits = offshore.getDeposits(otherDb);
            accountDeposits.put(DepositType.DEPOSITS, deposits);

        } else if (nationOrAllianceOrGuild.isNation()) {
            DBNation nation = nationOrAllianceOrGuild.asNation();
            if (nation != me && !Roles.INTERNAL_AFFAIRS.has(author, guild) && !Roles.INTERNAL_AFFAIRS_STAFF.has(author, guild) && !Roles.ECON.has(author, guild)) return "You do not have permission to check other nation's deposits";
            // txList
            accountDeposits = nation.getDeposits(db, offshoreIds, !includeBaseTaxes, !ignoreInternalOffsets, 0L, timeCutoff);
        }

        double[] total = new double[ResourceType.values.length];
        double[] totalNoGrants = new double[ResourceType.values.length];
        double[] taxAndDeposits = new double[ResourceType.values.length];
        Map<DepositType, double[]> categorized = new HashMap<>();

        for (Map.Entry<DepositType, double[]> entry : accountDeposits.entrySet()) {
            DepositType type = entry.getKey();
            double[] existing = categorized.computeIfAbsent(type, f -> new double[ResourceType.values.length]);
            double[] current = entry.getValue();

            for (int i = 0 ; i < existing.length; i++) {
                existing[i] += current[i];
                total[i] += current[i];
                if (type != DepositType.GRANT) {
                    totalNoGrants[i] += current[i];
                    if (type != DepositType.LOAN) {
                        taxAndDeposits[i] += current[i];
                    }
                }
            }
        }

        footers.add("value is based on current market prices");

        if (showTaxesSeparately == Boolean.TRUE || (showTaxesSeparately == null &&  db.getOrNull(GuildDB.Key.DISPLAY_ITEMIZED_DEPOSITS) == Boolean.TRUE)) {
            if (categorized.containsKey(DepositType.DEPOSITS)) {
                response.append("#DEPOSIT: (worth $" + MathMan.format(PnwUtil.convertedTotal(categorized.get(DepositType.DEPOSITS))) + ")");
                response.append("\n```").append(PnwUtil.resourcesToString(categorized.get(DepositType.DEPOSITS))).append("``` ");
            }
            if (categorized.containsKey(DepositType.TAX)) {
                response.append("#TAX (worth $" + MathMan.format(PnwUtil.convertedTotal(categorized.get(DepositType.TAX))) + ")");
                response.append("\n```").append(PnwUtil.resourcesToString(categorized.get(DepositType.TAX))).append("``` ");
            }
            if (categorized.containsKey(DepositType.LOAN)) {
                response.append("#LOAN/#GRANT (worth $" + MathMan.format(PnwUtil.convertedTotal(categorized.get(DepositType.LOAN))) + ")");
                response.append("\n```").append(PnwUtil.resourcesToString(categorized.get(DepositType.LOAN))).append("``` ");
            }
            if (categorized.containsKey(DepositType.GRANT)) {
                response.append("#EXPIRE (worth $" + MathMan.format(PnwUtil.convertedTotal(categorized.get(DepositType.GRANT))) + ")");
                response.append("\n```").append(PnwUtil.resourcesToString(categorized.get(DepositType.GRANT))).append("``` ");
            }
            if (categorized.size() > 1) {
                response.append("Total: (worth: $" + MathMan.format(PnwUtil.convertedTotal(total)) + ")");
                response.append("\n```").append(PnwUtil.resourcesToString(total)).append("``` ");
            }
        } else {
            String totalTitle = "Total (`#expire`|`#loan`|`#tax`|`#deposit`: worth $";
            String noGrantTitle = "Excluding `#expire` (worth: $";
            String safekeepTitle = "Safekeep (`#tax`|`#deposit`: worth $";
            boolean hasPriorCategory = false;
            if (categorized.containsKey(DepositType.GRANT)) {
                response.append(totalTitle + MathMan.format(PnwUtil.convertedTotal(total)) + ")");
                response.append("\n```").append(PnwUtil.resourcesToString(total)).append("``` ");
                footers.add("Unlike loans, debt from grants will expire if you stay (see the transaction for the timeframe)");
                hasPriorCategory = true;
            }
            if (categorized.containsKey(DepositType.LOAN)) {
                response.append((hasPriorCategory ? noGrantTitle : totalTitle) + MathMan.format(PnwUtil.convertedTotal(totalNoGrants)) + ")");
                response.append("\n```").append(PnwUtil.resourcesToString(totalNoGrants)).append("``` ");
                hasPriorCategory = true;
            }

            response.append((hasPriorCategory ? safekeepTitle : totalTitle) + MathMan.format(PnwUtil.convertedTotal(taxAndDeposits)) + ")");
            response.append("\n```").append(PnwUtil.resourcesToString(taxAndDeposits)).append("``` ");
        }
        if (me != null && nationOrAllianceOrGuild == me) {
            footers.add("Funds default to #deposit if no other note is used");
            if (Boolean.TRUE.equals(db.getOrNull(GuildDB.Key.RESOURCE_CONVERSION))) {
                footers.add("You can sell resources to the alliance by depositing with the note #cash");
            }
            if (PnwUtil.convertedTotal(total) > 0 && Boolean.TRUE.equals(db.getOrNull(GuildDB.Key.MEMBER_CAN_WITHDRAW))) {
                Role role = Roles.ECON_WITHDRAW_SELF.toRole(db.getGuild());
                if (db.getGuild().getMember(author).getRoles().contains(role)) {
                    footers.add("To withdraw, use: `" + CM.transfer.self.cmd.toSlashMention() + "` ");
                }
            }
        }

        if (!footers.isEmpty()) {
            for (int i = 0; i < footers.size(); i++) {
                String footer = footers.get(i);
                response.append("\n`note" + (i == 0 ? "" : i)).append(": " + footer + "`");
            }
        }

        IMessageIO output = replyInDMs ? new DiscordChannelIO(author.openPrivateChannel().complete(), null) : channel;
        CompletableFuture<IMessageBuilder> msgFuture = output.send(response.toString());

        if (me != null && nationOrAllianceOrGuild.isNation() && nationOrAllianceOrGuild.asNation().getPosition() > 1 && db.isWhitelisted() && db.getOrNull(GuildDB.Key.API_KEY) != null && db.getAllianceIds(true).contains(nationOrAllianceOrGuild.asNation().getAlliance_id())) {
            DBNation finalNation = nationOrAllianceOrGuild.asNation();
            Locutus.imp().getExecutor().submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        List<String> tips2 = new ArrayList<>();

                        {
                            Map<ResourceType, Double> stockpile = finalNation.getStockpile();
                            if (stockpile != null && !stockpile.isEmpty() && stockpile.getOrDefault(ResourceType.CREDITS, 0d) != -1) {
                                Map<ResourceType, Double> excess = finalNation.checkExcessResources(db, stockpile);
                                if (!excess.isEmpty()) {
                                    tips2.add("Excess can be deposited: " + PnwUtil.resourcesToString(excess));
                                    if (Boolean.TRUE.equals(db.getOrNull(GuildDB.Key.DEPOSIT_INTEREST))) {
                                        List<Transaction2> transactions = finalNation.getTransactions(-1);
                                        long last = 0;
                                        for (Transaction2 transaction : transactions) last = Math.max(transaction.tx_datetime, last);
                                        if (System.currentTimeMillis() - last > TimeUnit.DAYS.toMillis(5)) {
                                            tips2.add("Deposit frequently to be eligable for interest on your deposits");
                                        }
                                    }
                                }
                                Map<ResourceType, Double> needed = finalNation.getResourcesNeeded(stockpile, 3, true);
                                if (!needed.isEmpty()) {
                                    tips2.add("Missing resources for the next 3 days: " + PnwUtil.resourcesToString(needed));
                                }
                            }
                        }

                        if (me != null && me.getNation_id() == finalNation.getNation_id() && Boolean.TRUE.equals(db.getOrNull(GuildDB.Key.MEMBER_CAN_OFFSHORE)) && db.isValidAlliance() && db.hasAuth()) {
                            DBAlliance alliance = db.getAlliance();
                            if (alliance != null && me.getAlliance_id() == alliance.getAlliance_id()) {
                                try {
                                    Map<ResourceType, Double> stockpile = alliance.getStockpile();
                                    if (PnwUtil.convertedTotal(stockpile) > 5000000) {
                                        tips2.add("You MUST offshore funds after depositing `" + CM.offshore.send.cmd.toSlashMention() + "` ");
                                    }
                                } catch (Throwable ignore) {}
                            }
                        }

                        if (!tips2.isEmpty()) {
                            for (String tip : tips2) response.append("\n`tip: " + tip + "`");

                            try {
                                msgFuture.get().append(response.toString()).send();
                            } catch (InterruptedException | ExecutionException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        return null;
    }

    @Command
    public String weeklyInterest(double amount, double pct, int weeks) {
        double totalInterest = weeks * (pct / 100d) * amount;

        double weeklyPayments = (totalInterest + amount) / weeks;

        StringBuilder result = new StringBuilder("```");
        result.append("Principle Amount: $" + MathMan.format(amount)).append("\n");
        result.append("Loan Interest Rate: " + MathMan.format(pct)).append("%\n");
        result.append("Total Interest: $" + MathMan.format(totalInterest)).append("\n");
        result.append("Weekly Payments: $" + MathMan.format(weeklyPayments)).append("\n");

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
        int day = calendar.get(Calendar.DAY_OF_WEEK);

        int dayOffset = 0;
        switch (day) {
            case Calendar.MONDAY:
            case Calendar.TUESDAY:
                dayOffset = Calendar.FRIDAY - day;
                break;
            case Calendar.WEDNESDAY:
            case Calendar.THURSDAY:
            case Calendar.FRIDAY:
            case Calendar.SATURDAY:
            case Calendar.SUNDAY:
                dayOffset += 7 + Calendar.FRIDAY - day;
                break;
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime due = now.plusDays(dayOffset);
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("EEEE dd MMMM", Locale.ENGLISH);

        result.append("Today: " + pattern.format(now)).append("\n");
        String repeating = pattern.format(due) + " and every Friday thereafter for a total of " + weeks + " weeks.";
        result.append("First Payment Due: " + repeating).append("```");

        return result.toString();
    }

    @Command(desc = "List all nations in the alliance and their current stockpile\n" +
            "Add `-n` to normalize it per city")
    @RolePermission(any = true, value = {Roles.ECON_LOW_GOV, Roles.ECON})
    @IsAlliance
    public String stockpileSheet(@Me GuildDB db, @Switch("n") boolean normalize, @Switch("e") boolean onlyShowExcess, @Switch("f") boolean forceUpdate, @Me IMessageIO channel) throws IOException, GeneralSecurityException {
        DBAlliance alliance = db.getAlliance();

        Map<DBNation, Map<ResourceType, Double>> stockpile = alliance.getMemberStockpile();

        List<String> header = new ArrayList<>();
        header.add("nation");
        header.add("cities");
        header.add("avg_infra");
        header.add("off|def");
        header.add("mmr");

        header.add("marketValue");
        for (ResourceType value : ResourceType.values) {
            header.add(value.name().toLowerCase());
        }

        SpreadSheet sheet = SpreadSheet.create(db, GuildDB.Key.STOCKPILE_SHEET);
        sheet.setHeader(header);

        double[] aaTotal = ResourceType.getBuffer();

        if (forceUpdate) {
            try {
                alliance.updateCities();
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        for (Map.Entry<DBNation, Map<ResourceType, Double>> entry : stockpile.entrySet()) {
            List<Object> row = new ArrayList<>();

            DBNation nation = entry.getKey();
            if (nation == null) continue;
            row.add(MarkupUtil.sheetUrl(nation.getNation(), nation.getNationUrl()));
            row.add(nation.getCities());
            row.add(nation.getAvg_infra());
            row.add(nation.getOff() +"|" + nation.getDef());
            row.add(nation.getMMR());

            Map<ResourceType, Double> rss = entry.getValue();
            if (onlyShowExcess) {
                rss = nation.checkExcessResources(db, rss, false);
            }

            row.add(PnwUtil.convertedTotal(rss));

            for (ResourceType type : ResourceType.values) {
                double amt = rss.getOrDefault(type, 0d);
                if (normalize) amt /= nation.getCities();
                row.add(amt);

                if (amt > 0) aaTotal[type.ordinal()] += amt;
            }

            sheet.addRow(row);
        }

        sheet.clearAll();
        sheet.set(0, 0);

        String totalStr = PnwUtil.resourcesToFancyString(aaTotal);
        totalStr += "\n`note:total ignores nations with alliance info disabled`";
        channel.create().embed("AA TOTAL", totalStr)
                        .append("<" + sheet.getURL() + ">")
                                .send();
        return null;
    }

    @Command(desc = "Generate a sheet of member tax brackets.\n" +
            "Add `-a` to include applicants\n" +
            "Add `-f` to force an update of deposits\n" +
            "`note: internal tax rate is the TAX_BASE and determines what % of their taxes is excluded from deposits`")
    @RolePermission(any = true, value = {Roles.ECON, Roles.ECON_LOW_GOV})
    public String taxBracketSheet(@Me GuildDB db, @Switch("f") boolean force, @Switch("a") boolean includeApplicants) throws Exception {
        SpreadSheet sheet = SpreadSheet.create(db, GuildDB.Key.TAX_BRACKET_SHEET);
        List<Object> header = new ArrayList<>(Arrays.asList(
                "nation",
                "position",
                "cities",
                "age",
                "deposits",
                "tax_id",
                "tax_rate",
                "internal"
        ));

        sheet.setHeader(header);

        boolean failedFetch = true;
        Map<Integer, TaxBracket> brackets;
        try {
            brackets = db.getAlliance().getTaxBrackets(false);
            failedFetch = false;
        } catch (IllegalArgumentException e) {
            brackets = new LinkedHashMap<>();
            Set<Integer> allianceIds = db.getAllianceIds(true);
            Map<Integer, TaxBracket> allAllianceBrackets = Locutus.imp().getBankDB().getTaxBracketsAndEstimates();
            for (Map.Entry<Integer, TaxBracket> entry : allAllianceBrackets.entrySet()) {
                TaxBracket bracket = entry.getValue();
                if (allianceIds.contains(bracket.getAllianceId(false))) {
                    brackets.put(entry.getKey(), bracket);
                }
            }
        }
        Map<DBNation, TaxBracket> nations = new HashMap<>();
        for (TaxBracket bracket : brackets.values()) {
            for (DBNation nation : bracket.getNations()) {
                nations.put(nation, bracket);
            }
        }

        db.getAutoRoleTask().updateTaxRoles(nations);

        long threshold = force ? 0 : Long.MAX_VALUE;

        for (Map.Entry<DBNation, TaxBracket> entry : nations.entrySet()) {
            TaxBracket bracket = entry.getValue();
            DBNation nation = entry.getKey();

            if (!includeApplicants && nation.getPosition() <= 1) continue;

            header.set(0, MarkupUtil.sheetUrl(nation.getNation(), nation.getNationUrl()));
            header.set(1, Rank.byId(nation.getPosition()).name());
            header.set(2, nation.getCities());
            header.set(3, nation.getAgeDays());
            header.set(4, String.format("%.2f", nation.getNetDepositsConverted(db, threshold)));
            header.set(5, bracket.taxId + "");
            header.set(6, bracket.moneyRate + "/" + bracket.rssRate);

            TaxRate internal = db.getHandler().getInternalTaxrate(nation.getNation_id());
            header.set(7, internal.toString());

            sheet.addRow(header);
        }

        sheet.clearAll();
        sheet.set(0, 0);

        StringBuilder response = new StringBuilder("<" + sheet.getURL() + ">");
        if (failedFetch) response.append("\nnote: Please set an api key with " + CM.credentials.addApiKey.cmd.toSlashMention() + " to view updated tax brackets");
        return response.toString();
    }

    @Command
    @RolePermission(value = Roles.ADMIN)
    public String addOffshore(@Me User user, @Me GuildDB root, @Me DBNation nation, DBAlliance alliance) throws IOException {
        if (root.isDelegateServer()) return "Cannot enable offshoring for delegate server (run this command in the root server)";

        if (root.isOffshore()) {
            if (nation.getAlliance_id() != alliance.getAlliance_id()) {
                throw new IllegalArgumentException("You must be in the provided alliance: " + alliance.getId() + " to set the new ALLIANCE_ID for this offshore\n" +
                        "Note: If this is not a locutus managed offshore, please use `coalition add alliances:<alliance> coalition:offshore`");
            }

            Set<Long> announceChannels = new HashSet<>();
            Set<Long> serverIds = new HashSet<>();
            if (nation.getNation_id() == Settings.INSTANCE.NATION_ID) {
                announceChannels.add(Settings.INSTANCE.DISCORD.CHANNEL.ADMIN_ALERTS);
            }

            root.setInfo(GuildDB.Key.ALLIANCE_ID, alliance.getAlliance_id() + "");

            Set<Long> alliancesOrGuilds = root.getCoalitionRaw(Coalition.OFFSHORING);
            for (Long alliancesOrGuild : alliancesOrGuilds) {
                if (alliancesOrGuild < Integer.MAX_VALUE) {
                    GuildDB other = Locutus.imp().getGuildDBByAA(alliancesOrGuild.intValue());
                    if (other != null) alliancesOrGuild = other.getGuild().getIdLong();
                }
                serverIds.add(alliancesOrGuild);
            }

            for (Long serverId : serverIds) {
                GuildDB db = Locutus.imp().getGuildDB(serverId);
                if (db == null) continue;
                db.addCoalition(alliance.getAlliance_id(), "offshore");

                // Find the most stuited channel to post the announcement in
                MessageChannel channel = db.getOrNull(GuildDB.Key.RESOURCE_REQUEST_CHANNEL);
                if (channel == null) channel = db.getOrNull(GuildDB.Key.ADDBALANCE_ALERT_CHANNEL);
                if (channel == null) channel = db.getOrNull(GuildDB.Key.BANK_ALERT_CHANNEL);
                if (channel == null) channel = db.getOrNull(GuildDB.Key.DEPOSIT_ALERT_CHANNEL);
                if (channel == null) channel = db.getOrNull(GuildDB.Key.WITHDRAW_ALERT_CHANNEL);
                if (channel == null) {
                    List<NewsChannel> newsChannels = db.getGuild().getNewsChannels();
                    if (!newsChannels.isEmpty()) channel = newsChannels.get(0);
                }
                if (channel == null) channel = db.getGuild().getDefaultChannel();
                if (channel != null) {
                    announceChannels.add(channel.getIdLong());
                }
            }

            String title = "New Offshore";
            String body = PnwUtil.getMarkdownUrl(alliance.getAlliance_id(), true);
            for (Long announceChannel : announceChannels) {
                GuildMessageChannel channel = Locutus.imp().getDiscordApi().getGuildChannelById(announceChannel);
                if (channel != null) {
                    try {
                        DiscordUtil.createEmbedCommand(channel, title, body);
                        Role adminRole = Roles.ECON.toRole(channel.getGuild());
                        if (adminRole == null) {
                            adminRole = Roles.ADMIN.toRole(channel.getGuild());
                        }
                        Member owner = channel.getGuild().getOwner();
                        if (adminRole != null) {
                            RateLimitUtil.queue((channel.sendMessage(adminRole.getAsMention())));
                        } else if (owner != null) {
                            RateLimitUtil.queue((channel.sendMessage(owner.getAsMention())));
                        }
                    } catch (InsufficientPermissionException ignore) {}
                }
            }

            return "Done";
        }

        GuildDB offshoreDB = alliance.getGuildDB();
        if (offshoreDB == null) {
            return "No guild found for alliance: " + alliance.getAlliance_id();
        }
        if (!offshoreDB.isOffshore()) {
            return "No offshore found for alliance: " + alliance.getAlliance_id() + ". Are you sure that's a valid offshore setup with locutus?";
        }
        OffshoreInstance currentOffshore = root.getOffshore();
        if (currentOffshore != null) {
            return "This guild already has an offshore. If you would like to set a different offshore, use " + CM.coalition.delete.cmd.create(Coalition.OFFSHORE.name()) + " to remove it.\n" +
                    "Note: Removing offshores will remove deposits tracked from it.";
        }
        Boolean enabled = offshoreDB.getOrNull(GuildDB.Key.PUBLIC_OFFSHORING);
        if (enabled != Boolean.TRUE && !Roles.ECON.has(user, offshoreDB.getGuild())) {
            return "You do not have ECON on " + offshoreDB.getGuild() + " AND " + CM.settings.cmd.create(GuildDB.Key.PUBLIC_OFFSHORING.name(), null) + " is not enabled on that guild.";
        }
        if (offshoreDB.getOrNull(GuildDB.Key.ALLIANCE_ID) == null) {
            return "Please set the key " + CM.settings.cmd.create(GuildDB.Key.ALLIANCE_ID.name(), null).toSlashCommand() + " in " + offshoreDB.getGuild();
        }
        if (offshoreDB.getOrNull(GuildDB.Key.API_KEY) == null) {
            return "Please set the key " + CM.settings.cmd.create("API_KEY", null).toSlashCommand() + " in " + offshoreDB.getGuild();
        }

        StringBuilder response = new StringBuilder();
        // check public offshoring is enabled
        synchronized (OffshoreInstance.BANK_LOCK) {
            Integer aaId = root.getOrNull(GuildDB.Key.ALLIANCE_ID);
            NationOrAllianceOrGuild sender;
            if (aaId != null) {
                sender = DBAlliance.getOrCreate(aaId);
            } else {
                sender = root;
            }

            try {
                root.addCoalition(alliance.getAlliance_id(), Coalition.OFFSHORE);
                offshoreDB.addCoalition(sender.getIdLong(), Coalition.OFFSHORING);

                OffshoreInstance offshoreInstance = offshoreDB.getOffshore();
                double[] depo = offshoreInstance.getDeposits(root);

                if (!ResourceType.isEmpty(depo)) {
                    long tx_datetime = System.currentTimeMillis();
                    long receiver_id = 0;
                    int receiver_type = 0;
                    int banker = nation.getNation_id();

                    String note = "#deposit";
                    double[] amount = depo;
                    for (int i = 0; i < amount.length; i++) amount[i] = -amount[i];
                    offshoreDB.addTransfer(tx_datetime, sender, receiver_id, receiver_type, banker, note, amount);

                    MessageChannel output = offshoreDB.getOrNull(GuildDB.Key.RESOURCE_REQUEST_CHANNEL);
                    if (output != null) {
                        String msg = "Added " + PnwUtil.resourcesToString(amount) + " to " + sender.getTypePrefix() + ":" + sender.getName() + "/" + sender.getIdLong();
                        RateLimitUtil.queue(output.sendMessage(msg));
                        response.append("Reset deposit for " + root.getGuild() + "\n");
                    }
                }
                response.append("Registered " + alliance + " as an offshore. See: https://docs.google.com/document/d/1QkN1FDh8Z8ENMcS5XX8zaCwS9QRBeBJdCmHN5TKu_l8/edit");
            } catch (Throwable e) {
                root.removeCoalition(alliance.getAlliance_id(), Coalition.OFFSHORE);
                offshoreDB.removeCoalition(sender.getIdLong(), Coalition.OFFSHORING);

                throw e;
            }
        }

        return response.toString();
    }
}