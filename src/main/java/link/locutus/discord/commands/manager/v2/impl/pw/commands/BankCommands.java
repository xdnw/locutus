package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.NationDepositLimit;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasOffshore;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.TaxDeposit;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.GuildOrAlliance;
import link.locutus.discord.pnw.NationOrAllianceOrGuildOrTaxid;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.util.offshore.Grant;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.offshore.TransferResult;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.discord.util.scheduler.TriConsumer;
import link.locutus.discord.util.scheduler.TriFunction;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.sheet.templates.DepositSheetTask;
import link.locutus.discord.util.sheet.templates.NationBalanceRow;
import link.locutus.discord.util.sheet.templates.TransferSheet;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.util.task.mail.MailApiResponse;
import link.locutus.discord.web.WebUtil;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

import static link.locutus.discord.apiv1.enums.ResourceType.*;
import static link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands.handleAddbalanceAllianceScope;
import static link.locutus.discord.util.offshore.OffshoreInstance.DISABLED_MESSAGE;

public class BankCommands {

/*
> `/deposit resources <amount> <raws-days> <warchest-per-city> <warchest-total> <unit-resources> <note> `
>
> `amount` = How many resources to deposit. Optional. Throws error if any of the other arguments (besides note) is set.
>
> `raws-days` = Number of days of raws to keep on nation (optional) `double`
>
> `keep-warchest-factor` = Number of default warchests to keep per city (per city). Default warchest is is set via the settings command (optional) `double`
> `keep-per-city` = amount of resources to keep (per city) (optional) `Map<ResourceType,Double>`
> `keep-total` = amount of resources to keep (total) (optional) `Map<ResourceType,Double>`
> (only one of warchest-per-city or warchest-total can be set, not both)
>
> `unit-resources` = what units to keep funds to buy e.g. for nukes/missiles `Map<MilitaryUnit,Integer>`
>
> `note` = the note to use for deposits `String`
>
> Which will return a bank url (with the resource values set in the url query, e.g. `d_food=1234&d_coal=5678` (off the top of my head, i cant remember if that's the right parameter to use)`
 */
//    @Command(desc = "Find the ROI for various changes you can make to your nation, with a specified timeframe\n" +
//            "(typically how long you expect the changes, or peacetime to last)\n" +
//            "e.g. `{prefix}ROI @Borg 30`\n" +
//            "Add `-r` to run it recursively for various infra levels")
//    @RolePermission(Roles.MEMBER)
//    public String roi()

    @Command(desc = "Instruct nations to deposit resources into the alliance bank\n" +
     "If multiple calculation options are set the largest values will be used",
            groups = {
                "Mode 1: Using a Sheet",
                "Mode 2: Specify an Amount",
                "Mode 3: Calculate an Amount",
                "Message Settings",
                "Deposit Via Api",
            }
    )
    @RolePermission(Roles.MEMBER)
    @IsAlliance
    public String depositResources(@Me User author, @Me DBNation me, @Me GuildDB db, @Me JSONObject command, @Me IMessageIO io,
                                   Set<DBNation> nations,

                                   @Arg(value = "A spreadsheet of nations and amounts to deposit\n" +
                                           "Columns must be named after the resource names", group = 0)
                                   @Switch("s") TransferSheet sheetAmounts,

                                   @Arg(value = "Exact amount of resources to deposit (capped at resources on nation)\n" +
                                           "Cannot be used with other deposit modes are set", group = 1)
                                   @Switch("a") Map<ResourceType, Double> amount,

                                   @Arg(value = "Number of days of city raw resource consumption to keep\n" +
                                           "Recommended value: 5", group = 2)
                                       @Range(min = 0)
                                       @Switch("r") Double rawsDays,

                                   @Arg(value = """
                                           Alternatively specify a number of days of raws for each individual resource type
                                           Overrides any value set for `rawsDays`
                                           Ommitted resources will use 0 if `rawsDays` is not provided""", group = 2)
                                       @Range(min = 0)
                                       @Switch("rb") Map<ResourceType, Double> raws_days_by_resource,

                                   @Arg(value = "Do not keep money above the daily login bonus\n" +
                                           "Requires `rawsDays` to be set", group = 2) @Switch("d") boolean rawsNoDailyCash,
                                   @Arg(value = "Do not keep any money\n" +
                                           "Requires `rawsDays` to be set ", group = 2) @Switch("c") boolean rawsNoCash,

                                   @Arg(value = """
                                           Number of default warchests to keep per city
                                           Recommended value: 1
                                           Default warchest is is set via the settings command""", group = 2)
                                   @Switch("wcf") Double keepWarchestFactor,
                                   @Arg(value = "Amount of resources to keep per city", group = 2)
                                   @Switch("pc") Map<ResourceType, Double> keepPerCity,
                                   @Arg(value = "Amount of resources to keep in total", group = 2)
                                   @Switch("kt") Map<ResourceType, Double> keepTotal,
                                   @Arg(value = "Keep resources for purchasing specific units", group = 2)
                                   @Switch("ur") Map<MilitaryUnit, Long> unitResources,

                                   @Arg(value = "Do not keep any money for units", group = 2)
                                   @Switch("uc") boolean units_no_cash,

                                   @Arg(value = "Note to add to the deposit\n" +
                                           "Defaults to deposits", group = 3)
                                   @Switch("n") DepositType.DepositTypeInfo note,

                                   @Arg(value = "The message to append to the mail or dm message\n" +
                                           "You must specify either `mailResults` or `dm` if this is set", group = 4)
                                   @Switch("cm") String customMessage,

                                   @Arg(value = "Send deposit urls to nations via in-game mail", group = 4)
                                   @Switch("m") boolean mailResults,
                                   @Arg(value = "Send deposit urls to nations in discord direct messages (dm)", group = 4)
                                   @Switch("dm") boolean dm,

                                   @Arg(value = "Use the API to do a deposit instead of sending a message", group = 5)
                                   @Switch("u") boolean useApi,

                                   @Switch("f") boolean force) throws IOException, ExecutionException, InterruptedException, GeneralSecurityException {
        if (customMessage != null && !dm && !mailResults) {
            throw new IllegalArgumentException("Cannot specify `customMessage` without specifying `dm` or `mailResults`");
        }
        if (amount != null && (rawsDays != null || raws_days_by_resource != null || keepWarchestFactor != null || keepPerCity != null || keepTotal != null || unitResources != null)) {
            throw new IllegalArgumentException("Cannot specify `amount` to deposit with other deposit modes.");
        }
        if (rawsNoDailyCash && (rawsDays == null && raws_days_by_resource == null)) {
            throw new IllegalArgumentException("Cannot specify `rawsNoDailyCash` (`-d`) without specifying `rawsDays`");
        }
        if (rawsNoCash && (rawsDays == null && raws_days_by_resource == null)) {
            throw new IllegalArgumentException("Cannot specify `rawsNoCash` (`-c`) without specifying `rawsDays`");
        }
        if (sheetAmounts != null && (rawsDays != null || raws_days_by_resource == null || keepWarchestFactor != null || keepPerCity != null || keepTotal != null || unitResources != null)) {
            throw new IllegalArgumentException("Cannot specify `sheetAmounts` to deposit with other deposit modes.");
        }
        if (nations.isEmpty()) return "No nations found";

        boolean isOther = nations.size() > 1 || me.getNation_id() != nations.iterator().next().getNation_id();

        if (mailResults && !Roles.MAIL.has(author, db.getGuild()) && isOther) {
            throw new IllegalArgumentException("No permission for `mailResults`. " + Roles.MAIL.toDiscordRoleNameElseInstructions(db.getGuild()));
        }
        if (dm && !Roles.MAIL.hasOnRoot(author) && isOther) {
            throw new IllegalArgumentException("No permission for `dm`. " + Roles.MAIL.toDiscordRoleNameElseInstructions(Locutus.imp().getServer()));
        }
        if (useApi && isOther && !Roles.ECON.has(author, db.getGuild())) {
            throw new IllegalArgumentException("No permission for `useApi`. " + Roles.ECON.toDiscordRoleNameElseInstructions(db.getGuild()));
        }
        Set<Long> allowed = Roles.ECON_STAFF.getAllowedAccounts(author, db);
        Map<DBNation, OffshoreInstance.TransferStatus> statuses = new LinkedHashMap<>();
        Map<DBNation, double[]> toKeepMap = new LinkedHashMap<>();
        Map<DBNation, double[]> toDepositMap = new LinkedHashMap<>();

        if (sheetAmounts != null) {
            Map<DBNation, Map<ResourceType, Double>> nationTransfers = sheetAmounts.getNationTransfers();
            nations = new HashSet<>(nations);
            nations.removeIf(f -> !nationTransfers.containsKey(f));
            for (Map.Entry<DBNation, Map<ResourceType, Double>> entry : nationTransfers.entrySet()) {
                toDepositMap.put(entry.getKey(), ResourceType.resourcesToArray(entry.getValue()));
            }
        }

        for (DBNation nation : nations) {
            if (!db.isAllianceId(nation.getAlliance_id())) {
                statuses.put(nation, OffshoreInstance.TransferStatus.NOT_MEMBER);
                continue;
            }
            if (nation.getPositionEnum().id <= Rank.APPLICANT.id) {
                statuses.put(nation, OffshoreInstance.TransferStatus.APPLICANT);
                continue;
            }
            if (nation.getNation_id() != me.getNation_id() && !allowed.contains((long) nation.getAlliance_id())) {
                statuses.put(nation, OffshoreInstance.TransferStatus.AUTHORIZATION);
                continue;
            }
            if (nation.getVm_turns() > 0) {
                statuses.put(nation, OffshoreInstance.TransferStatus.VACATION_MODE);
            }
            if (nation.isBlockaded()) {
                statuses.put(nation, OffshoreInstance.TransferStatus.BLOCKADE);
            }
        }

        Set<DBNation> remainingNations = nations.stream().filter(f -> !statuses.containsKey(f)).collect(Collectors.toSet());

        AllianceList allianceList = new SimpleNationList(remainingNations).toAllianceList();

        if (rawsDays != null || raws_days_by_resource != null) {
            if (rawsDays != null && rawsDays < 1/12d) {
                throw new IllegalArgumentException("rawsDays must be > 1 turns (1/12 days), not: `" + rawsDays + "`");
            }
            if (raws_days_by_resource != null) {
                for (Map.Entry<ResourceType, Double> entry : raws_days_by_resource.entrySet()) {
                    if (entry.getValue() < 1/12d) {
                        throw new IllegalArgumentException("Each value of `raws_days_by_resource` must be > 1 turns (1/12 days), not: `" + entry.getValue() + "` for `" + entry.getKey() + "`");
                    }
                }
            }
            double[] daysByResource = ResourceType.getBuffer();
            if (rawsDays != null) {
                for (ResourceType type : ResourceType.values) {
                    daysByResource[type.ordinal()] = rawsDays;
                }
            }
            if (raws_days_by_resource != null) {
                for (Map.Entry<ResourceType, Double> entry : raws_days_by_resource.entrySet()) {
                    daysByResource[entry.getKey().ordinal()] = entry.getValue();
                }
            }

            allianceList.updateCities();
            Map<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> funds1d = allianceList.calculateDisburse(nations, null, 1, false, false, true, rawsNoDailyCash, rawsNoCash, true, false);
            Map<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> funds2d = allianceList.calculateDisburse(nations, null, 2, false, false, true, rawsNoDailyCash, rawsNoCash, true, false);

            for (Map.Entry<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> entry : funds1d.entrySet()) {
                DBNation nation = entry.getKey();
                OffshoreInstance.TransferStatus status = entry.getValue().getKey();
                double[] rss1d = entry.getValue().getValue();
                double[] rss2d = funds2d.get(nation).getValue();
                // multiply by amt
                double[] rss = ResourceType.getBuffer();
                for (int i = 0; i < rss1d.length; i++) {
                    double days = daysByResource[i];
                    if (days == 0) continue;
                    if (Math.round(rss1d[i] * 100) == Math.round(rss2d[i] * 100)) {
                        rss[i] = rss1d[i];
                    } else {
                        rss[i] = rss1d[i] * days;
                    }
                }

                if (rawsNoDailyCash) {
                    rss[ResourceType.MONEY.ordinal()] = Math.max(50000 * nation.getCities(), rss[ResourceType.MONEY.ordinal()]);
                }
                if (rawsNoCash) {
                    rss[ResourceType.MONEY.ordinal()] = Math.min(50000 * nation.getCities(), rss[ResourceType.MONEY.ordinal()]);
                }
                if (rss != null) {
                    toKeepMap.put(nation, rss);
                } else {
                    statuses.put(nation, status);
                }
            }
        }

        CompletableFuture<IMessageBuilder> msgFuture = (io.sendMessage("Please wait..."));
        long start = System.currentTimeMillis();

        IMessageBuilder updateMsg = null;
        Map<DBNation, Map<ResourceType, Double>> stockpiles = allianceList.getMemberStockpile(remainingNations::contains);
        int i = 0;
        for (DBNation nation : remainingNations) {
            i++;
            if (System.currentTimeMillis() - start > 10000) {
                updateMsg = io.updateOptionally(msgFuture, "Updating " + nation.getNation() + "(" + i + "/" + remainingNations.size() + ")");
                start = System.currentTimeMillis();
            }
            Map<ResourceType, Double> stockpile = stockpiles.get(nation);
            if (stockpile == null) {
                statuses.put(nation, OffshoreInstance.TransferStatus.ALLIANCE_ACCESS);
                continue;
            }
            double[] toKeep = toKeepMap.getOrDefault(nation, ResourceType.getBuffer());

            double[] toDeposit = toDepositMap.getOrDefault(nation, ResourceType.getBuffer());
            if (amount != null) {
                toDeposit = ResourceType.resourcesToArray(amount);
            }
            if (keepWarchestFactor != null) {
                double[] rss = ResourceType.resourcesToArray(db.getPerCityWarchest(nation));
                PW.multiply(rss, nation.getCities());
                toKeep = ResourceType.max(toKeep, rss);
            }
            if (keepPerCity != null) {
                double[] rss = ResourceType.resourcesToArray(keepPerCity);
                PW.multiply(rss, nation.getCities());
                toKeep = ResourceType.max(toKeep, rss);
            }
            if (keepTotal != null) {
                double[] rss = ResourceType.resourcesToArray(keepTotal);
                toKeep = ResourceType.max(toKeep, rss);
            }
            if (unitResources != null) {
                double[] rss = ResourceType.getBuffer();
                for (Map.Entry<MilitaryUnit, Long> entry : unitResources.entrySet()) {
                    entry.getKey().addCost(rss, entry.getValue().intValue(), nation::getResearch);
                }
                if (units_no_cash) {
                    rss[ResourceType.MONEY.ordinal()] = 0;
                }
                toKeep = ResourceType.max(toKeep, rss);
            }
            if (!ResourceType.isZero(toKeep)) {
                // to deposit = stockpile - toKeep, max 0
                toDeposit = ResourceType.builder().add(stockpile).subtract(toKeep).build();
            }

            toDeposit = ResourceType.max(toDeposit, ResourceType.getBuffer());
            toDeposit = ResourceType.min(toDeposit, ResourceType.resourcesToArray(stockpile));

            if (ResourceType.isZero(toDeposit)) {
                statuses.put(nation, OffshoreInstance.TransferStatus.NOTHING_WITHDRAWN);
                continue;
            }
            toDepositMap.put(nation, toDeposit);
        }
        List<String> header = new ArrayList<>(Arrays.asList(
                "nation",
                "nation_url",
                "status",
                "deposit_raw",
                "remaining_raw",
                "deposit_url",
                "DM",
                "Mail",
                "API"
        ));

        List<List<String>> errorRows = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        for (Map.Entry<DBNation, OffshoreInstance.TransferStatus> entry : statuses.entrySet()) {
            DBNation nation = entry.getKey();

            Map<ResourceType, Double> stockpile = stockpiles.get(nation);
            String stockpileStr = stockpile == null ? null : ResourceType.toString(stockpile);
            List<String> row = Arrays.asList(
                    nation.getName(),
                    nation.getUrl(),
                    entry.getValue().name(),
                    "{}",
                    stockpileStr,
                    "null",
                    "false",
                    "false",
                    "false"
            );
            errorRows.add(row);
        }

        Set<DBNation> finalNations = nations;
        TriConsumer<IMessageBuilder, List<List<String>>, String> attachErrors = new TriConsumer<IMessageBuilder, List<List<String>>, String>() {
            @Override
            public void accept(IMessageBuilder msg, List<List<String>> errors, String title) {
                if (errors.size() > 0) {
                    errors = new ArrayList<>(errors);
                    errors.add(0, header);
                    if (finalNations.size() > 1) {
                        String content = errors.stream().map(f -> StringMan.join(f, "\t")).collect(Collectors.joining("\n"));
                        msg.file(title + ".txt", content);
                    } else {
                        String content = errors.stream().map(f -> StringMan.join(f, "\t")).collect(Collectors.joining("\n"));
                        msg.append("### " + title + "\n");
                        msg.append("```\n" + content + "\n```");
                    }
                }
            }
        };

        if (toDepositMap.isEmpty()) {
            IMessageBuilder msg = io.create().append("Nothing to deposit");
            attachErrors.accept(msg, errorRows, "errors");
            msg.send();
            return null;
        }

        TriFunction<double[], Integer, String, String> toBankUrl = new TriFunction<>() {
            @Override
            public String apply(double[] resources, Integer allianceId, String note) {
                StringBuilder url = new StringBuilder(Settings.PNW_URL() + "/alliance/id=" + allianceId + "&display=bank" + (note != null ? "&d_note=" + note : ""));
                for (ResourceType type : ResourceType.values) {
                    double amt = resources[type.ordinal()];
                    if (amt > 0) {
                        String amtStr = MathMan.format(amt);
                        url.append("&d_" + type.name().toLowerCase() + "=" + amtStr);
                    }
                }
                return url.toString();
            }
        };

        ApiKeyPool key = db.getMailKey();
        long channelId = io.getIdLong();

        if (!force) {
            double[] total = ResourceType.getBuffer();
            double[] remainingTotal = ResourceType.getBuffer();
            for (Map.Entry<DBNation, double[]> entry : toDepositMap.entrySet()) {
                total = ResourceType.add(total, entry.getValue());
                Map<ResourceType, Double> stockpile = stockpiles.get(entry.getKey());
                if (stockpile != null) {
                    double[] remaining = ResourceType.max(ResourceType.builder().add(stockpile).subtract(entry.getValue()).build(), ResourceType.getBuffer());
                    remainingTotal = ResourceType.add(remainingTotal, remaining);
                }
            }

            String title = "Deposit worth ~$" + MathMan.format(ResourceType.convertedTotal(total)) + " for ";
            if (toDepositMap.size() == 1) {
                title += toDepositMap.keySet().iterator().next().getName();
            } else {
                title += toDepositMap.size() + " nations";
                int numAlliances = new SimpleNationList(toDepositMap.keySet()).getAllianceIds().size();
                if (numAlliances > 1) {
                    title += " in " + numAlliances + " alliances";
                }
            }
            IMessageBuilder msg = io.create();
            StringBuilder body = new StringBuilder();
            if (useApi) {
                body.append("`useApi`: True (Will attempt to deposit via api)\n");
            }
            if (mailResults) {
                body.append("`mailResults`: True (Will send results via in-game mail)\n");
            }
            if (dm) {
                body.append("`dmResults`: True (Will send results via discord dm)\n");
            }

            if (toDepositMap.size() > 1) {
                SpreadSheet sheet;
                if (sheetAmounts == null) {
                    // create sheet
                    sheet = SpreadSheet.create(db, SheetKey.DEPOSIT_SHEET);
                    sheet.addRow(header);
                    for (List<String> errorRow : errorRows) {
                        sheet.addRow(errorRow);
                    }
                    for (Map.Entry<DBNation, double[]> entry : toDepositMap.entrySet()) {
                        DBNation nation = entry.getKey();
                        double[] rss = entry.getValue();
                        // stockpileStr
                        Map<ResourceType, Double> stockpile = stockpiles.get(nation);
                        String stockpileStr = stockpile == null ? null : ResourceType.toString(stockpile);

                        List<String> row = Arrays.asList(
                                nation.getName(),
                                nation.getUrl(),
                                "SEND",
                                ResourceType.toString(rss),
                                stockpileStr,
                                toBankUrl.apply(rss, nation.getAlliance_id(), note == null ? null : note.toString()),
                                (dm && nation.getUser() != null) + "",
                                mailResults + "",
                                (useApi && nation.getApiKey(false) != null) + ""
                        );
                        sheet.addRow(row);
                    }
                    sheet.updateClearCurrentTab();
                    sheet.updateWrite();
                } else {
                    sheet = sheetAmounts.getSheet();
                }
                sheet.attach(msg, "deposit", body, false, 0);
            }

            // total / worth
            body.append("\n**Total:** `" + ResourceType.toString(total) + "` worth ~$" + MathMan.format(ResourceType.convertedTotal(total)) + "\n");
            // remaining / worth
            body.append("\n**Remaining:** `" + ResourceType.toString(remainingTotal) + "` worth ~$" + MathMan.format(ResourceType.convertedTotal(remainingTotal)) + "\n");
            // errors
            if (!errorRows.isEmpty()) {
                attachErrors.accept(msg, errorRows, "errors");
            }
            msg.confirmation(title, body.toString(), command).send();
            return null;
        }

        StringBuilder result = new StringBuilder();
        for (Map.Entry<DBNation, double[]> entry : toDepositMap.entrySet()) {
            DBNation nation = entry.getKey();

            String sentDmError = null;
            String sentMailError = null;
            String sentApiError = null;
            boolean sentDm = false;
            boolean sentMail = false;
            boolean sentApi = false;

            double[] resources = entry.getValue();
            String url = toBankUrl.apply(resources, nation.getAlliance_id(), note == null ? null : note.toString());
            result.append("__**# " + nation.getNation() + "**__:\n");
            //  +"\t" + nation.getNationUrl()).append(" Can deposit: `").append(url).append(">");
            result.append("- **nation**: <" + nation.getUrl() + ">\n");
            result.append("- **deposit amount**: `" + ResourceType.toString(resources) + "`\n");
            result.append("- **deposit url**: <" + url + ">\n");

            StringBuilder body = new StringBuilder("Excess resources can be deposited in the alliance bank.\n");
            body.append("Deposit url: " + url + "\n");

            if (customMessage != null) {
                body.append(customMessage);
            }
            if (me.getNation_id() != nation.getNation_id()) {
                body.append("\n- Sent by: " + me.getNation() + "/" + author.getName());
            }

            if (mailResults) {
                String subject = "Deposit Resources/" + channelId;
                try {
                    MailApiResponse mailResult = nation.sendMail(key, subject, body.toString(), true);
                    result.append("\n- **mail**: ").append("`" + mailResult.status() + " " + mailResult.error() + "`");
                    sentMail = true;
                } catch (Throwable e) {
                    sentMailError = e.getMessage();
                    sentMail = false;
                    result.append("\n- **mail**: Failed to send mail (`" + e.getMessage() + "`)");
                }
            }

            if (dm) {
                User user = nation.getUser();
                if (user == null) {
                    sentDmError = "NO DISCORD";
                    sentDm = false;
                    result.append("\n- **dm**: No discord user set. See " + CM.register.cmd.toSlashMention());
                } else {
                    try {
                        PrivateChannel channel = RateLimitUtil.complete(user.openPrivateChannel());
                        RateLimitUtil.queue(channel.sendMessage(body.toString()));
                        sentDm = true;
                        result.append("\n- **dm**: Sent dm");
                    } catch (Throwable e) {
                        sentDmError = e.getMessage();
                        sentDm = false;
                        result.append("\n- **dm**: Failed to send dm (`" + e.getMessage() + "`)");
                    }
                }
            }

            if (useApi) {
                try {
                    ApiKeyPool.ApiKey api = nation.getApiKey(false);
                    if (api == null) {
                        sentApiError = "NO API";
                        sentApi = false;
                        result.append("\n- **api**: No `API_KEY` set. See " + CM.credentials.addApiKey.cmd.toSlashMention());
                    } else {
                        String noteStr = note != null ? note.toString() : "#deposit";
                        new PoliticsAndWarV3(ApiKeyPool.create(api)).depositIntoBank(resources, noteStr);
                        sentApi = true;
                        result.append("\n- **api**: Deposited `" + ResourceType.toString(resources) + "` | `" + noteStr + "` into bank");
                    }
                } catch (Throwable e) {
                    sentApi = false;
                    sentApiError = e.getMessage();
                    result.append("\n- **api**: No valid api key set (`" + e.getMessage() + "`). See " + CM.credentials.addApiKey.cmd.toSlashMention());
                }
            }
            result.append("\n");

            String status = "";
            if (sentDmError != null) status += "DM: " + sentDmError + " ";
            if (sentMailError != null) status += "Mail: " + sentMailError + " ";
            if (sentApiError != null) status += "Api: " + sentApiError + " ";
            if (status.isEmpty()) status = "SUCCESS";
            Map<ResourceType, Double> stockpile = stockpiles.get(nation);
            String stockpileStr = stockpile == null ? null : ResourceType.toString(stockpile);

            List<String> row = Arrays.asList(
                    nation.getName(),
                    nation.getUrl(),
                    status,
                    ResourceType.toString(resources),
                    stockpileStr,
                    url,
                    sentDm + "",
                    sentMail + "",
                    sentApi + ""
            );
            if (sentDmError != null || sentMailError != null) {
                errorRows.add(row);
            } else {
                rows.add(row);
            }
            result.append("\n");
        }
        IMessageBuilder msg = io.create();
        if (nations.size() == 1) {
            attachErrors.accept(msg, errorRows, "errors");
            if (customMessage != null && !customMessage.isEmpty()) {
                result.append("> " + StringMan.join(customMessage.split("\n"), "\n> ")).append("\n");
            }

            return "**Excess resources can be deposited in the alliance bank.**\n" +
                    "Use the pre-filled link below to deposit the recommended amount:\n" + result.toString();
        } else {
            attachErrors.accept(msg, errorRows, "errors");
            attachErrors.accept(msg, rows, "results");
            msg.append(result.toString());
            msg.send();
        }

        if (updateMsg != null && updateMsg.getId() > 0) {
            io.delete(updateMsg.getId());
        }

        return null;
    }

    @Command(desc = "View a nation's taxability, in-game tax rate, and internal tax-rate", viewable = true)
    @IsAlliance
    public String taxInfo(@Me IMessageIO io, @Me GuildDB db, @Me DBNation me, @Me User user, DBNation nation) {
        if (nation == null) nation = me;
        if (nation.getId() != me.getId()) {
            if (user == null) throw new IllegalArgumentException("You must be registered to check other nation's tax info: " + CM.register.cmd.toSlashMention());
            if (db == null) throw new IllegalArgumentException("Checking other nation's tax info must be done within a discord server");
            if (!Roles.ECON_STAFF.has(user, db.getGuild())) {
                throw new IllegalArgumentException("No permission to check other nation's tax info. Missing: " + Roles.ECON_STAFF.toDiscordRoleNameElseInstructions(db.getGuild()));
            }
        }
        List<String> responses = new ArrayList<>();

        if (nation.getPositionEnum().id <= Rank.APPLICANT.id) {
            responses.add("Applicants are not taxed. ");
        } else {
            DBAlliance alliance = nation.getAlliance();
            Map<Integer, TaxBracket> brackets = alliance.getTaxBrackets(TimeUnit.MINUTES.toMillis(5));
            TaxBracket bracket = brackets.get(nation.getTax_id());
            if (bracket == null) {
                bracket = nation.getTaxBracket();
            }
            String taxRateStr = bracket.moneyRate >= 0 ? String.format("%d/%d", bracket.moneyRate, bracket.moneyRate) : "Unknown";
            responses.add(String.format("**Bracket** %s: %s (tax_id:%d)", bracket.getName(), taxRateStr, nation.getTax_id()));
            if (nation.isGray()) {
                responses.add("`note: GRAY nations do not pay taxes. ");
            } else if (nation.isBeige()) {
                responses.add("`note: BEIGE nations do not pay taxes`");
            } else if (nation.allianceSeniority() < 3) {
                responses.add("`note: Nations with <2 days alliance seniority do not pay taxes`");
            }

            if (db != null) {
                TaxRate internal = db.getHandler().getInternalTaxrate(nation.getNation_id());

                if (internal.money == -1 && internal.resources == -1) {
                    responses.add("`note: your taxes do NOT go into your deposits`");
                } else {
                    responses.add(String.format("**Internal tax rate:** %d/%d", internal.money, internal.resources));
                    boolean payAboveMsg = false;
                    if (internal.money >= bracket.moneyRate) {
                        responses.add("`note: no $ from taxes goes into your deposits as internal $ rate is higher than your in-game tax $ rate`");
                    } else {
                        payAboveMsg = true;
                    }
                    if (internal.resources >= bracket.rssRate) {
                        responses.add("`note: no resources from taxes goes into your deposits as internal resource rate is higher than your in-game tax resources rate`");
                    } else {
                        payAboveMsg = true;
                    }
                    if (payAboveMsg) {
                        responses.add("`note: A portion of $/resources above the internal tax rate goes to the alliance`");
                    }
                }
                if (Roles.ECON.has(user, db.getGuild())) {
                    TaxRate taxBase = db.getOrNull(GuildKey.TAX_BASE);
                    if (taxBase == null || ((internal.money == -1 && internal.resources == -1))) {
                        responses.add("`note: set an internal taxrate with " + CM.nation.set.taxinternal.cmd.toSlashMention() + " or globally with " + CM.settings.info.cmd.toSlashMention() + " and key: " + GuildKey.TAX_BASE.name() + "`");
                    }
                    responses.add("\nTo view alliance wide bracket tax totals, use: " +
                        CM.deposits.check.cmd.nationOrAllianceOrGuild("tax_id=" + bracket.taxId).showCategories("true"));
                }
            }
        }

        CM.deposits.check checkCmd = CM.deposits.check.cmd.nationOrAllianceOrGuild(nation.getId() + "").showCategories("true");
        responses.add("\nTo view a breakdown of your deposits, use: " + checkCmd);

        String title = "Tax info for " + nation.getName();
        StringBuilder body = new StringBuilder();
        body.append("**Nation:** ").append(nation.getNationUrlMarkup()).append("\n");
        if (db != null && !db.isAllianceId(nation.getAlliance_id())) {
            body.append("`note: nation is not in alliances: " + StringMan.getString(db.getAllianceIds()) + "`\n");
        }
        body.append(StringMan.join(responses, "\n"));

        io.create().embed(title, body.toString()).send();
        return null;
    }


    @Command(desc = "Send the funds in the alliance bank to an alliance added to the `offshore` coalition in the bot\n" +
            "Optionally specify warchest and offshoring account", groups = {
            "Account Settings",
            "Resource Amounts",
    })
    @RolePermission(value = {Roles.MEMBER, Roles.ECON, Roles.ECON_STAFF}, alliance = true, any=true)
    @HasOffshore
    @IsAlliance
    public static String offshore(@Me User user, @Me GuildDB db, @Me IMessageIO io,
                                  @Arg(value = "Specify an alternative Offshore alliance to send funds in-game to\n" +
                                          "Defaults to the currently set offshore coalition", group = 0) @Default DBAlliance to,
                                  @Arg(value = "Specify an alternative account to offshore with\n" +
                                          "Defaults to the sender alliance", group = 0) @Default NationOrAllianceOrGuild account,
                                  @Arg(value = "The amount of resources to keep in the bank\n" +
                                          "Defaults to keep nothing", group = 1) @Default("{}") Map<ResourceType, Double> keepAmount,
                                  @Arg(value = """
                                          Specify specific resource amounts to offshore
                                          Defaults to all resources
                                          The send amount is auto capped by the resources available and `keepAmount`""", group = 1)
                                  @Default Map<ResourceType, Double> sendAmount) throws IOException {
        if (account != null && account.isNation()) {
            throw new IllegalArgumentException("You can't offshore into a nation. You can only offshore into an alliance or guild. Value provided: `Nation:" + account.getName() + "`");
        }
        if (account == null) {
            NationOrAllianceOrGuild defAccount = GuildKey.DEFAULT_OFFSHORE_ACCOUNT.getOrNull(db);
            if (defAccount != null) {
                account = defAccount;
            }
        }
        boolean memberCanOffshore = db.getOrNull(GuildKey.MEMBER_CAN_OFFSHORE) == Boolean.TRUE;
        Roles checkRole = memberCanOffshore && account == null ? Roles.MEMBER : Roles.ECON;

        AllianceList allianceList = checkRole.getAllianceList(user, db);
        if (allianceList == null || allianceList.isEmpty()) {
            StringBuilder msg = new StringBuilder("Missing Role: " + checkRole.toDiscordRoleNameElseInstructions(db.getGuild()));
            if (memberCanOffshore && account != null) {
                msg.append(". You do not have permission to specify an alternative account.");
            }
            if (!memberCanOffshore) {
                msg.append(". See also: " + CM.settings.info.cmd.toSlashMention() + " with key " + GuildKey.MEMBER_CAN_OFFSHORE.name());
            }
            throw new IllegalArgumentException(msg.toString());
        }
        Set<Integer> offshores = db.getCoalition(Coalition.OFFSHORE);
        if (to == null) {
            OffshoreInstance offshore = db.getOffshore();
            if (offshore == null) {
                throw new IllegalArgumentException("No offshore found. See: " + CM.offshore.add.cmd.toSlashMention() + "  or " + CM.coalition.add.cmd.toSlashCommand() + " (for a non automated offshore)");
            }
            to = offshore.getAlliance();
        } else {
            if (!offshores.contains(to.getAlliance_id())) return "Please add the offshore using " + CM.coalition.add.cmd.alliances(to.getQualifiedId()).coalitionName(Coalition.OFFSHORE.name());
        }
        Set<DBAlliance> alliances = allianceList.getAlliances();
        if (alliances.size() == 1 && alliances.iterator().next().equals(to)) {
            throw new IllegalArgumentException("You cannot offshore to yourself");
        }

        List<TransferResult> results = new ArrayList<>();
        for (DBAlliance from : alliances) {
            if (from.getAlliance_id() == to.getAlliance_id()) continue;
            Map<ResourceType, Double> resources = sendAmount != null ? sendAmount : from.getStockpile(true);
            Map<ResourceType, Double> stockpile = sendAmount != null ? from.getStockpile(true) : new Object2DoubleOpenHashMap<>(resources);
            for (Map.Entry<ResourceType, Double> sendAmt : resources.entrySet()) {
                ResourceType type = sendAmt.getKey();
                double amt = sendAmt.getValue();
                double stockpileAmt = stockpile.getOrDefault(type, 0d);
                double maxSend = Math.max(0, stockpileAmt - keepAmount.getOrDefault(type, 0d));
                if (amt > maxSend) {
                    sendAmt.setValue(maxSend);
                }
            }
            OffshoreInstance bank = from.getBank();
            String note;
            if (account != null) {
                note = account.isAlliance() ? "#alliance=" + account.getId() : "#guild=" + account.getIdLong();
            } else {
                note = "#alliance=" + from.getAlliance_id();
            }
            note += " #tx_id=" + UUID.randomUUID().toString();
            TransferResult response = bank.transferUnsafe2(null, to, resources, note, null);
            results.add(response);
        }
        Map.Entry<String, String> embed = TransferResult.toEmbed(results);
        io.create().embed(embed.getKey(), embed.getValue()).send();
        return null;
    }

    @Command(desc = "Generate csv of war cost by nation between alliances (for reimbursement)\n" +
            "Filters out wars where nations did not perform actions", viewable = true)
    public String warReimburseByNationCsv(@Arg("The alliances with nations you want to reimburse") Set<DBAlliance> allies,
                                          @Arg("The enemies during the conflict") Set<DBAlliance> enemies,
                                          @Arg("Starting time of the conflict") @Timestamp long cutoff, @Arg("If wars with no actions by the defender should NOT be reimbursed") boolean removeWarsWithNoDefenderActions) {
        Set<Integer> allyIds = allies.stream().map(f -> f.getAlliance_id()).collect(Collectors.toSet());
        Set<Integer> enemyIds = enemies.stream().map(f -> f.getAlliance_id()).collect(Collectors.toSet());

        Map<Integer, Integer> offensivesByNation = new Int2IntOpenHashMap();
        Map<Integer, Integer> defensivesByNation = new Int2IntOpenHashMap();

        Set<DBNation> nations = Locutus.imp().getNationDB().getNationsByAlliance(allyIds);
        nations.removeIf(f -> f.getVm_turns() > 0 || f.active_m() > 10000 || f.getPosition() <= 1);
        List<DBWar> wars = new ArrayList<>(Locutus.imp().getWarDb().getWarsForNationOrAlliance(null,
                f -> (allyIds.contains(f) || enemyIds.contains(f)),
                f -> (allyIds.contains(f.getAttacker_aa()) || allyIds.contains(f.getDefender_aa())) && (enemyIds.contains(f.getAttacker_aa()) || enemyIds.contains(f.getDefender_aa())) && f.getDate() > cutoff).values());

        Map<Integer, List<AbstractCursor>> attacksByWar = new Int2ObjectOpenHashMap<>();
        Locutus.imp().getWarDb().iterateAttacksByWars(wars, (war, attack) -> {
            attacksByWar.computeIfAbsent(attack.getWar_id(), f -> new ArrayList<>()).add(attack);
        });
        if (removeWarsWithNoDefenderActions) {
            wars.removeIf(f -> {
                List<AbstractCursor> attacks = attacksByWar.get(f.warId);
                if (attacks == null) return true;
                boolean att1 = attacks.stream().anyMatch(g -> g.getAttacker_id() == f.getAttacker_id());
                boolean att2 = attacks.stream().anyMatch(g -> g.getAttacker_id() == f.getDefender_id());
                return !att1 || !att2;
            });
        }

        wars.removeIf(f -> {
            List<AbstractCursor> attacks = attacksByWar.get(f.warId);
            AttackCost cost = f.toCost(attacks, false, false, false, false, false);
            boolean primary = allyIds.contains(f.getAttacker_aa());
            return cost.convertedTotal(primary) <= 0;
        });

        for (DBWar war : wars) {
            offensivesByNation.put(war.getAttacker_id(), offensivesByNation.getOrDefault(war.getAttacker_id(), 0) + 1);
            defensivesByNation.put(war.getDefender_id(), defensivesByNation.getOrDefault(war.getDefender_id(), 0) + 1);
        }


        Map<Integer, double[]> warcostByNation = new Int2ObjectOpenHashMap<>();

        for (DBWar war : wars) {
            List<AbstractCursor> attacks = attacksByWar.get(war.warId);
            AttackCost ac = war.toCost(attacks, false, false, false, false, false);
            boolean primary = allyIds.contains(war.getAttacker_aa());
            double[] units = ResourceType.resourcesToArray(ac.getUnitCost(primary));
            double[] consume = ResourceType.resourcesToArray(ac.getConsumption(primary));

            double[] cost = ResourceType.add(units, consume);

            double[] warCostTotal = ResourceType.resourcesToArray(ac.getTotal(primary));
            for (ResourceType type : ResourceType.values) {
                cost[type.ordinal()] = Math.max(0, Math.min(warCostTotal[type.ordinal()], cost[type.ordinal()]));
            }

            int nationId = primary ? war.getAttacker_id() : war.getDefender_id();
            double[] total = warcostByNation.computeIfAbsent(nationId, f -> ResourceType.getBuffer());
            total = ResourceType.add(total, cost);
        }


        List<String> header = new ArrayList<>(Arrays.asList("nation", "off", "def"));
        for (ResourceType type : ResourceType.values()) {
            if (type != ResourceType.CREDITS) header.add(type.name());
        }

        List<String> lines = new ArrayList<>();
        lines.add(StringMan.join(header, ","));
        for (Map.Entry<Integer, double[]> entry : warcostByNation.entrySet()) {
            int id = entry.getKey();
            DBNation nation = DBNation.getById(id);
            if (nation == null || !allyIds.contains(nation.getAlliance_id()) || nation.getPosition() <= 1 || nation.getVm_turns() > 0 || nation.active_m() > 7200 || nation.getCities() < 10) continue;
            header.clear();
            header.add(PW.getName(id, false));
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

    @Command(desc = """
            Set the escrow account balances for nation to the values in a spreadshet
            The sheet must have a `nation` column, and then a column for each resource type
            Escrow funds can be withdrawn at a later date by the receiver, such as when a blockade ends
            Use the deposits sheet command to get a spreadsheet of the current escrow balances""")
    @RolePermission(Roles.ECON)
    @IsAlliance
    @HasOffshore
    public String setEscrowSheet(@Me GuildDB db,
                                 @Me IMessageIO io,
                                 @Me JSONObject command,
                                    TransferSheet sheet,
                                 @Default @Timediff Long expireAfter,
                                 @Switch("f") boolean force) throws IOException {

        Map<DBAlliance, Map<ResourceType, Double>> aaTransfers = sheet.getAllianceTransfers();
        if (aaTransfers.isEmpty()) {
            // cannot escrow for alliance (print the alliance names)
            List<String> aaNames = aaTransfers.keySet().stream().map(DBAlliance::getName).collect(Collectors.toList());
            throw new IllegalArgumentException("Alliances cannot have escrow balances: " + StringMan.join(aaNames, ", ") + " please remove them from the sheet and try again");
        }

        Map<DBNation, Map<ResourceType, Double>> transfers = sheet.getNationTransfers();
        Map<DBNation, Set<ResourceType>> negativesByNation = new LinkedHashMap<>();
        for (Map.Entry<DBNation, Map<ResourceType, Double>> entry : transfers.entrySet()) {
            // ensure no negative values (map of nation: resource1,resource2 etc)
            Set<ResourceType> negatives = entry.getValue().entrySet().stream().filter(f -> f.getValue() < 0).map(Map.Entry::getKey).collect(Collectors.toSet());
            if (!negatives.isEmpty()) {
                negativesByNation.put(entry.getKey(), negatives);
            }
        }
        if (!negativesByNation.isEmpty()) {
            StringBuilder response = new StringBuilder();
            response.append("Nations cannot have negative escrow balances:\n");
            for (Map.Entry<DBNation, Set<ResourceType>> entry : negativesByNation.entrySet()) {
                response.append("- " + entry.getKey().getName()).append(": ").append(StringMan.join(entry.getValue(), ", ")).append("\n");
            }
            throw new IllegalArgumentException(response.toString());
        }

        Map<DBNation, double[]> transfersArr = new LinkedHashMap<>();
        for (Map.Entry<DBNation, Map<ResourceType, Double>> entry : transfers.entrySet()) {
            transfersArr.put(entry.getKey(), ResourceType.resourcesToArray(entry.getValue()));
        }

        return confirmAddOrSetEscrow(
                false,
                io,
                db,
                command,
                new HashMap<>(),
                "sheet:" + sheet.getSheet().getSpreadsheetId(),
                transfersArr,
                expireAfter,
                force
        );
    }

    @Command(desc = """
            Add funds to the escrow account for a set of nations
            Escrow funds can be withdrawn at a later date by the receiver, such as when a blockade ends
            To transfer funds from a nation's deposits into their escrow, see the transfer command""")
    @RolePermission(Roles.ECON)
    @IsAlliance
    @HasOffshore
    public String addEscrow(@Me GuildDB db, @Me User author, @Me DBNation me,
                            @Me IMessageIO io,
                            @Me JSONObject command,
                            NationList nations,
                            @Switch("b") @Arg("The base amount of resources to escrow\n" +
                                    "If per city is set, the highest value of each resource is chosen") Map<ResourceType, Double> amountBase,
                            @Switch("p") @Arg("""
                                    Amount of resources to escrow for each city the receiver has
                                    If base is set, the highest value of each resource is chosen
                                    This uses the city count now, not when the funds are withdrawn later""") Map<ResourceType, Double> amountPerCity,
                            @Switch("e") @Arg("Additional resources to escrow\n" +
                                    "If a base or per city are set, this adds to what is calculated for that") Map<ResourceType, Double> amountExtra,
                            @Arg("Don't add escrow resources that the nation has in their stockpile") @Switch("s") boolean subtractStockpile,
                            @Arg("When the nation has these units, don't add the resources equivalent to their cost\n" +
                                    "Useful to only give resources to those missing units")
                            @Switch("m") Set<MilitaryUnit> subtractNationsUnits,

                            @Arg("Do not add escrow resources that the nation has in their deposits")
                            @Switch("d") boolean subtractDeposits,


                            @Arg("Delete all receiver escrow after a time period\nRecommended: 5d") @Default @Timediff Long expireAfter,
                            @Switch("f") boolean force) throws IOException {
        return addOrSetEscrow(true, db, io, command, nations, amountBase, amountPerCity, amountExtra, subtractStockpile, subtractNationsUnits, subtractDeposits, expireAfter, force);
    }

    @Command(desc = """
            Set the escrow account balances for a set of nations
            Escrow funds can be withdrawn at a later date by the receiver, such as when a blockade ends
            To transfer funds from a nation's deposits into their escrow, see the transfer command""")
    @RolePermission(Roles.ECON)
    @IsAlliance
    @HasOffshore
    public String setEscrow(@Me GuildDB db, @Me User author, @Me DBNation me,
                            @Me IMessageIO io,
                            @Me JSONObject command,
                            NationList nations,
                            @Switch("b") @Arg("The base amount of resources to escrow\n" +
                                    "If per city is set, the highest value of each resource is chosen") Map<ResourceType, Double> amountBase,
                            @Switch("p") @Arg("""
                                    Amount of resources to escrow for each city the receiver has
                                    If base is set, the highest value of each resource is chosen
                                    This uses the city count now, not when the funds are withdrawn later""") Map<ResourceType, Double> amountPerCity,
                            @Switch("e") @Arg("Additional resources to escrow\n" +
                                    "If a base or per city are set, this adds to what is calculated for that") Map<ResourceType, Double> amountExtra,
                            @Arg("Don't add escrow resources that the nation has in their stockpile") @Switch("s") boolean subtractStockpile,
                            @Arg("When the nation has these units, don't add the resources equivalent to their cost\n" +
                                    "Useful to only give resources to those missing units")
                            @Switch("m") Set<MilitaryUnit> subtractNationsUnits,
                            @Arg("Do not add escrow resources that the nation has in their deposits")
                            @Switch("d") boolean subtractDeposits,
                            @Arg("Delete all receiver escrow after a time period\nRecommended: 5d") @Default @Timediff Long expireAfter,
                            @Switch("f") boolean force) throws IOException {
        return addOrSetEscrow(false, db, io, command, nations, amountBase, amountPerCity, amountExtra, subtractStockpile, subtractNationsUnits, subtractDeposits, expireAfter, force);
    }

    public String confirmAddOrSetEscrow(boolean isAdd, IMessageIO io, GuildDB db, JSONObject command, Map<DBNation, OffshoreInstance.TransferStatus> errors, String nationsName, Map<DBNation, double[]> amountToSetOrAdd, Long expireAfter, boolean force) throws IOException {
        long expireEpoch = expireAfter == null ? 0 : System.currentTimeMillis() + expireAfter;

        if (!force || amountToSetOrAdd.isEmpty()) {
            String title;
            if (isAdd) {
                title = "Add to Escrow to " + nationsName;
            } else {
                title = "Set escrow for " + nationsName;
            }
            List<String> warnings = new ArrayList<>();
            int blockaded = 0;
            int unblockaded = 0;
            for (DBNation nation : amountToSetOrAdd.keySet()) {
                if (nation.isBlockaded()) {
                    blockaded++;
                } else {
                    unblockaded++;
                }
                if (nation.getVm_turns() > 0) {
                    warnings.add(nation.getName() + " is in VM mode");
                    continue;
                }
                if (nation.active_m() > 2880) {
                    warnings.add(nation.getName() + " is inactive for " + TimeUtil.secToTime(TimeUnit.MINUTES, nation.active_m()));
                    continue;
                }
                if (!db.isAllianceId(nation.getAlliance_id())) {
                    warnings.add(nation.getName() + " is not a member");
                    continue;
                }
                if (nation.getPositionEnum() == Rank.APPLICANT) {
                    warnings.add(nation.getName() + " is an applicant");
                    continue;
                }
                if (nation.isGray()) {
                    warnings.add(nation.getName() + " is gray");
                    continue;
                }
            }

            StringBuilder body = new StringBuilder();
            if (amountToSetOrAdd.size() == 1) {
                DBNation nation = amountToSetOrAdd.keySet().iterator().next();
                body.append(nation.getNationUrlMarkup() + " | " + nation.getAllianceUrlMarkup() + "\n");
                if (nation.isBlockaded()) {
                    body.append("`BLOCKADED`\n");
                } else {
                    body.append("`NOT BLOCKADED`\n");
                }
            } else {
                SimpleNationList nations = new SimpleNationList(amountToSetOrAdd.keySet());
                body.append("To: `" + nationsName + "` (" + nations.getNations().size() + " nations in " + nations.getAllianceIds().size() + " alliances)\n");
                if (blockaded > 0) {
                    body.append(" | " + blockaded + " blockaded");
                }
                if (unblockaded > 0) {
                    body.append(" | " + unblockaded + " unblockaded");
                }
                body.append("\n");
            }
            String verb = isAdd ? "adding to Escrow" : "setting Escrow to ";
            double[] total = ResourceType.getBuffer();
            for (double[] amount : amountToSetOrAdd.values()) {
                ResourceType.add(total, amount);
            }
            body.append("\nTotal " + verb + ":\n`" + ResourceType.toString(total) + "` worth: ~$" + MathMan.format(ResourceType.convertedTotal(total)) + "\n");

            if (expireAfter != null) {
                body.append("\nSetting the expiry for all escrowed to " + DiscordUtil.timestamp(expireEpoch, null)).append("\n");
            } else {
                body.append("\nSetting all receiver escrow to not expire. Use `expireAfter` to set an expiry\n");
            }

            String bodyStr = body.toString();

            if (!warnings.isEmpty()) {
                body.append("\n**Warnings**:\n");
                for (String warning : warnings) {
                    body.append("- " + warning).append("\n");
                }
            }

            if (!errors.isEmpty()) {
                body.append("\n**Errors**:\n");
                for (Map.Entry<DBNation, OffshoreInstance.TransferStatus> entry : errors.entrySet()) {
                    body.append("- " + entry.getKey().getName() + ": " + entry.getValue()).append("\n");
                }
            }

            IMessageBuilder msg = io.create();

            if (body.length() > bodyStr.length() && body.length() > 4000) {
                bodyStr += "\n\nWarnings: " + warnings.size() + "\nErrors: " + errors.size() + "\n";
                bodyStr += "(see attached file)";
                msg.file("errors.txt", StringMan.join(errors.entrySet(), "\n"));
                msg.file("warnings.txt", StringMan.join(warnings, "\n"));
            } else {
                bodyStr = body.toString();
            }

            if (amountToSetOrAdd.isEmpty()) {
                msg.append(body.toString()).send();
                return null;
            }

            msg.confirmation(title, body.toString(), command).send();
            return null;
        }

        List<String> response = new ArrayList<>();
        // add all errors
        for (Map.Entry<DBNation, OffshoreInstance.TransferStatus> entry : errors.entrySet()) {
            response.add(entry.getKey().getName() + ": " + entry.getValue());
        }
        for (Map.Entry<DBNation, double[]> entry : amountToSetOrAdd.entrySet()) {
            DBNation nation = entry.getKey();
            double[] amount = entry.getValue();
            Object lock = OffshoreInstance.NATION_LOCKS.computeIfAbsent(entry.getKey().getId(), k -> new Object());
            synchronized (lock) {
                Map.Entry<double[], Long> currentPair = db.getEscrowed(nation);
                double[] current = currentPair == null ? ResourceType.getBuffer() : currentPair.getKey();
                long expireEpochNation = expireEpoch;
                if (expireEpochNation == 0) {
                    expireEpochNation = currentPair == null ? 0 : currentPair.getValue();
                }
                double[] newAmount;
                if (isAdd) {
                    newAmount = ResourceType.add(current.clone(), amount);
                } else {
                    newAmount = amount;
                }
                if (ResourceType.equals(current, newAmount)) {
                    response.add("No changes for " + nation.getName());
                    continue;
                }
                db.setEscrowed(nation, newAmount, expireEpochNation);
                response.add(nation.getName() + ": `" + ResourceType.toString(amount) + "` worth: ~$" + MathMan.format(ResourceType.convertedTotal(amount)) + " added to escrow. New escrowed: `" + ResourceType.toString(newAmount) + "`");
            }
        }

        return StringMan.join(response, "\n") + "\n\nSee also: " + CM.deposits.reset.cmd.toSlashMention();
    }

    public String addOrSetEscrow(boolean isAdd,
                            @Me GuildDB db,
                            @Me IMessageIO io,
                            @Me JSONObject command,
                            NationList nations,
                            @Switch("b") @Arg("The base amount of resources to escrow\n" +
                                    "If per city is set, the highest value of each resource is chosen") Map<ResourceType, Double> amountBase,
                            @Switch("p") @Arg("""
                                    Amount of resources to escrow for each city the receiver has
                                    If base is set, the highest value of each resource is chosen
                                    This uses the city count now, not when the funds are withdrawn later""") Map<ResourceType, Double> amountPerCity,
                            @Switch("e") @Arg("Additional resources to escrow\n" +
                                    "If a base or per city are set, this adds to what is calculated for that") Map<ResourceType, Double> amountExtra,
                            @Arg("Don't add escrow resources that the nation has in their stockpile") @Switch("s") boolean subtractStockpile,
                            @Arg("When the nation has these units, don't add the resources equivalent to their cost\n" +
                                    "Useful to only give resources to those missing units")
                            @Switch("m") Set<MilitaryUnit> subtractNationsUnits,

                            @Arg("Do not add escrow resources that the nation has in their deposits")
                            @Switch("d") boolean subtractDeposits,


                            @Arg("Delete all receiver escrow after a time period\nRecommended: 5d") @Default @Timediff Long expireAfter,
                            @Switch("f") boolean force) throws IOException {
        if (nations.getNations().size() > 300) {
            return "Too many nations: " + nations.getNations().size() + " (max: 300)";
        }
        if (amountBase == null && amountPerCity == null && amountExtra == null) {
            return "No amount specified. Please specify at least one of: `amountBase`, `amountPerCity`, `amountExtra`";
        }
        if (db.getOrNull(GuildKey.RESOURCE_REQUEST_CHANNEL) == null) {
            return "No resource request channel set. See " + GuildKey.RESOURCE_REQUEST_CHANNEL.getCommandMention();
        }

        Map<DBNation, OffshoreInstance.TransferStatus> errors = new LinkedHashMap<>();
        Map<DBNation, Map<ResourceType, Double>> memberStockpile = null;

        if (subtractStockpile) {
            for (DBNation nation : nations.getNations()) {
                if (!db.isAllianceId(nation.getAlliance_id())) {
                    return "Nation: " + nation.getName() + "(alliance id:" + nation.getAlliance_id() + ") is not a member of this guild's alliances: " + db.getAllianceIds();
                }
            }
            memberStockpile = db.getAllianceList().subList(nations.getNations()).getMemberStockpile();
        }

        if (!isAdd) {
            // ensure all are positive amounts
            if (amountBase != null) {
                for (Map.Entry<ResourceType, Double> entry : amountBase.entrySet()) {
                    if (entry.getValue() < 0) {
                        throw new IllegalArgumentException("`amountBase` is invalid. Cannot set negative escrow amounts: " + entry.getKey() + " = " + MathMan.format(entry.getValue()));
                    }
                }
            }
            if (amountPerCity != null) {
                for (Map.Entry<ResourceType, Double> entry : amountPerCity.entrySet()) {
                    if (entry.getValue() < 0) {
                        throw new IllegalArgumentException("`amountPerCity` is invalid. Cannot set negative escrow amounts: " + entry.getKey() + " = " + MathMan.format(entry.getValue()));
                    }
                }
            }
            if (amountExtra != null) {
                for (Map.Entry<ResourceType, Double> entry : amountExtra.entrySet()) {
                    if (entry.getValue() < 0) {
                        throw new IllegalArgumentException("`amountExtra` is invalid. Cannot set negative escrow amounts: " + entry.getKey() + " = " + MathMan.format(entry.getValue()));
                    }
                }
            }
        }

        Map<DBNation, double[]> amountToSetOrAdd = new LinkedHashMap<>();

        for (DBNation nation : nations.getNations()) {
            double[] amount = ResourceType.getBuffer();
            if (amountBase != null) {
                amount = ResourceType.resourcesToArray(amountBase);
            }
            if (amountPerCity != null) {
                int cities = nation.getCities();
                double[] perCity = ResourceType.resourcesToArray(amountPerCity);
                for (int i = 0; i < perCity.length; i++) {
                    amount[i] = Math.max(perCity[i] * cities, amount[i]);
                }
            }
            if (amountExtra != null) {
                double[] extra = ResourceType.resourcesToArray(amountExtra);
                ResourceType.add(amount, extra);
            }

            if (subtractStockpile) {
                Map<ResourceType, Double> stockpile = memberStockpile.get(nation);
                if (stockpile == null) {
                    errors.put(nation, OffshoreInstance.TransferStatus.ALLIANCE_ACCESS);
                    continue;
                }
                double[] stockpileArr = ResourceType.resourcesToArray(stockpile);
                for (int i = 0; i < stockpileArr.length; i++) {
                    amount[i] = Math.max(Math.min(0, amount[i]), amount[i] - stockpileArr[i]);
                }
            }

            if (subtractNationsUnits != null && !subtractNationsUnits.isEmpty()) {
                for (MilitaryUnit unit : subtractNationsUnits) {
                    int numUnits = nation.getUnits(unit);
                    double[] cost = ResourceType.resourcesToArray(unit.getCost(numUnits, nation::getResearch));
                    for (int i = 0; i < cost.length; i++) {
                        amount[i] = Math.max(Math.min(0, amount[i]), amount[i] - cost[i]);
                    }
                }
            }

            if (subtractDeposits) {
                double[] deposits = nation.getNetDeposits(db, -1L, true);
                for (int i = 0; i < deposits.length; i++) {
                    double amt = deposits[i];
                    if (amt > 0) {
                        amount[i] = Math.max(Math.min(0, amount[i]), amount[i] - deposits[i]);
                    }
                }
            }
            ResourceType.max(amount, ResourceType.getBuffer());

            amountToSetOrAdd.put(nation, amount);
        }

        return confirmAddOrSetEscrow(
                isAdd,
                io,
                db,
                command,
                errors,
                nations.getFilter(),
                amountToSetOrAdd,
                expireAfter,
                force
        );
    }

    @Command(desc = "Disburse raw resources needed to operate cities", aliases = {"disburse", "disperse"}, groups = {
        "Amount Options",
        "Optional: Bank Note",
        "Optional: Nation Account",
        "Optional: Specify Offshore/Bank",
        "Optional: Tax Bracket Account (pick either or none)"
    })
    @RolePermission(value = {Roles.ECON_WITHDRAW_SELF, Roles.ECON}, alliance = true, any = true)
    @IsAlliance
    public static String disburse(@Me User author, @Me GuildDB db, @Me IMessageIO io, @Me DBNation me,

                                  @Arg("The nations to send to")
                                  NationList nationList,
                                  @Arg(value = "Days of operation to send", group = 0, aliases = "daysdefault") @Range(min=0, max=20) double days,
                                  @Arg(value = "Do not send money below the daily login bonus", group = 0) @Switch("dc") boolean no_daily_cash,
                                  @Arg(value = "Do not send ANY money", group = 0) @Switch("c") boolean no_cash,

                                  @Arg(value = "Transfer note\nUse `#IGNORE` to not deduct from deposits", group = 1, aliases = "deposittype") @Default("#tax") DepositType.DepositTypeInfo bank_note,
                                  @Arg(value = "Have the transfer ignored from nation holdings after a timeframe", group = 1) @Switch("e") @Timediff Long expire,
                                  @Arg(value = "Have the transfer decrease linearly from balances over a timeframe", group = 1) @Switch("d") @Timediff Long decay,
                                  @Arg(value = "Have the transfer valued as cash in nation holdings", group = 1, aliases = "converttomoney") @Switch("m") boolean deduct_as_cash,

                           @Arg(value = "The guild's nation account to deduct from\n" +
                                   "Defaults to None if bulk disburse, else the receivers account", group = 2, aliases = "depositsaccount") @Switch("n") DBNation nation_account,
                                  @Arg(value = "How to handle the transfer if the receiver is blockaded\n" +
                                          "Defaults to never escrow", group = 2) @Switch("em") EscrowMode escrow_mode,

                           @Arg(value = "The in-game alliance bank to send from\nDefaults to the offshore set", group = 3, aliases = "usealliancebank") @Switch("a") DBAlliance ingame_bank,
                           @Arg(value = """
                                   The account with the offshore to use
                                   The alliance must be registered to this guild
                                   Defaults to all the alliances of this guild""", group = 3, aliases = "useoffshoreaccount") @Switch("o") DBAlliance offshore_account,

                           @Arg(value = "The tax account to deduct from", group = 4) @Switch("t") TaxBracket tax_account,
                           @Arg(value = "Deduct from the receiver's tax bracket account", group = 4, aliases = "existingtaxaccount") @Switch("ta") boolean use_receiver_tax_account,


                           @Arg("Skip checking receiver activity, blockade, VM etc.")
                                      @Switch("b") boolean bypass_checks,
                                  @Switch("p") boolean ping_when_sent,
                           @Switch("pr") Roles ping_role,
                           @Switch("f") boolean force) throws GeneralSecurityException, IOException, ExecutionException, InterruptedException {
        Set<DBNation> nations = new HashSet<>(nationList.getNations());
        if (ping_when_sent && nations.size() > 1) {
            throw new IllegalArgumentException("Cannot set `ping_when_sent` for multiple nations");
        }

        AllianceList allianceList = db.getAllianceList();
        if (allianceList == null) {
            throw new IllegalArgumentException("This guild is not registered to an alliance. See: " + GuildKey.ALLIANCE_ID.getCommandMention());
        }
        for (DBNation nation : nations) {
            if (!allianceList.contains(nation.getAlliance())) {
                throw new IllegalArgumentException("This guild is registered to: " + StringMan.getString(allianceList.getIds()) +
                        " but you are trying to disburse to a nation in " + nation.getAlliance_id() +
                        "\nConsider using a different list of nations or registering the alliance to this guild (" +
                        CM.settings.info.cmd.toSlashMention() + " with key `" +  GuildKey.ALLIANCE_ID.name() + "`)");
            }
        }

        List<String> output = new ArrayList<>();
        List<TransferResult> allStatuses = new ArrayList<>();

        if (nations.size() != 1) {
            int originalSize = nations.size();
            Iterator<DBNation> iter = nations.iterator();
            while (iter.hasNext()) {
                DBNation nation = iter.next();
                OffshoreInstance.TransferStatus status = OffshoreInstance.TransferStatus.SUCCESS;
                String debug = "";
                if (nation.getPosition() <= 1) status = OffshoreInstance.TransferStatus.APPLICANT;
                else if (nation.getVm_turns() > 0) status = OffshoreInstance.TransferStatus.VACATION_MODE;
                else if (!db.isAllianceId(nation.getAlliance_id())) status = OffshoreInstance.TransferStatus.NOT_MEMBER;
                else if (!bypass_checks) {
                    if (nation.active_m() > 2880) {
                        status = OffshoreInstance.TransferStatus.INACTIVE;
                        debug += " (2+ days)";
                    }
                    if (nation.isGray()) status = OffshoreInstance.TransferStatus.GRAY;
                    if (nation.isBeige() && nation.getCities() <= 4) status = OffshoreInstance.TransferStatus.BEIGE;
                    if (!status.isSuccess()) debug += " (use the `bypasschecks` parameter to override)";
                }
                if (!status.isSuccess()) {
                    iter.remove();
                    allStatuses.add(new TransferResult(status, nation, new Object2DoubleOpenHashMap<>(), bank_note.toString()).addMessage(status.getMessage() + debug));
                }
            }
            int removed = originalSize - nations.size();
            if (removed > 0) {
                output.add("Removed " + removed + " invalid nations (see errors.csv)");
            }
        }

        IMessageBuilder msg = io.create();

        if (nations.isEmpty()) {
            msg.append("No nations found (1)\n" + StringMan.join(output, "\n"));
            if (!allStatuses.isEmpty()) {
                msg.file("errors.csv", TransferResult.toFileString(allStatuses));
                msg.append("\nSummary: `" + TransferResult.count(allStatuses) + "`");
            }
            msg.send();
            return null;
        }

        Map<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> funds = allianceList.calculateDisburse(nations, null, days, true, bypass_checks, true, no_daily_cash, no_cash, bypass_checks, force);
        Map<DBNation, Map<ResourceType, Double>> fundsToSendNations = new LinkedHashMap<>();
        for (Map.Entry<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> entry : funds.entrySet()) {
            DBNation nation = entry.getKey();
            Map.Entry<OffshoreInstance.TransferStatus, double[]> value = entry.getValue();
            OffshoreInstance.TransferStatus status = value.getKey();
            double[] amount = value.getValue();
            if (status == OffshoreInstance.TransferStatus.SUCCESS) {
                fundsToSendNations.put(nation, ResourceType.resourcesToMap(amount));
            } else {
                allStatuses.add(new TransferResult(status, nation, ResourceType.resourcesToMap(amount), bank_note.toString()).addMessage(status.getMessage()));
            }
        }

        if (fundsToSendNations.size() <= 1 || !force) {
            if (!allStatuses.isEmpty()) {
                msg.file("errors.csv", TransferResult.toFileString(allStatuses));
                msg.append("Summary: `" + TransferResult.count(allStatuses) + "`");
            }
            if (fundsToSendNations.isEmpty()) {
                msg.append("\nError. No funds to send.");
                msg.send();
                return null;
            } else {
                msg.send();
            }
        }
        if (fundsToSendNations.size() == 1) {
            Map.Entry<DBNation, Map<ResourceType, Double>> entry = fundsToSendNations.entrySet().iterator().next();
            DBNation nation = entry.getKey();
            Map<ResourceType, Double> transfer = entry.getValue();

            JSONObject command = CM.transfer.resources.cmd.receiver(
                    nation.getUrl()).transfer(
                    ResourceType.toString(transfer)).depositType(
                    bank_note.toString()).nationAccount(
                    nation_account != null ? nation_account.getUrl() : null).senderAlliance(
                    ingame_bank != null ? ingame_bank.getUrl() : null).allianceAccount(
                    offshore_account != null ? offshore_account.getUrl() : null).taxAccount(
                    tax_account != null ? tax_account.getQualifiedId() : null).existingTaxAccount(
                    use_receiver_tax_account + "").onlyMissingFunds(
                    Boolean.FALSE.toString()).expire(
                    expire == null ? null : TimeUtil.secToTime(TimeUnit.MILLISECONDS, expire)).decay(
                    decay == null ? null : TimeUtil.secToTime(TimeUnit.MILLISECONDS, decay)).convertCash(
                    String.valueOf(deduct_as_cash)).escrow_mode(
                    escrow_mode == null ? null : escrow_mode.name()).bypassChecks(
                    String.valueOf(bypass_checks)).force(
                    String.valueOf(force)).ping_when_sent(
                    ping_when_sent ? "true" : null).toJson();

            if (ping_role != null) {
                Role role = ping_role.toRole2(db);
                if (role != null) {
                    io.send(role.getAsMention());
                }
            }
            return transfer(io, command, author, me, db, nation, transfer, bank_note, nation_account, ingame_bank, offshore_account, tax_account, use_receiver_tax_account, false, expire, decay, null, deduct_as_cash, escrow_mode, bypass_checks, ping_when_sent, force);
        } else {
            UUID key = UUID.randomUUID();
            TransferSheet sheet = new TransferSheet(db).write(fundsToSendNations, new LinkedHashMap<>()).build();
            APPROVED_BULK_TRANSFER.put(key, sheet.getTransfers());

            JSONObject command = CM.transfer.bulk.cmd.sheet(
                    sheet.getSheet().getURL()).depositType(
                    bank_note.toString()).depositsAccount(
                    nation_account != null ? nation_account.getUrl() : null).useAllianceBank(
                    ingame_bank != null ? ingame_bank.getUrl() : null).useOffshoreAccount(
                    offshore_account != null ? offshore_account.getUrl() : null).taxAccount(
                    tax_account != null ? tax_account.getQualifiedId() : null).existingTaxAccount(
                    use_receiver_tax_account + "").expire(
                    expire == null ? null : TimeUtil.secToTime(TimeUnit.MILLISECONDS, expire)).decay(
                    decay == null ? null : TimeUtil.secToTime(TimeUnit.MILLISECONDS, decay)).convertToMoney(
                    Boolean.FALSE.toString()).escrow_mode(
                    escrow_mode == null ? null : escrow_mode.name()).bypassChecks(
                    String.valueOf(bypass_checks)).force(
                    String.valueOf(force)).key(
                    key.toString()
            ).toJson();
            Map errors = force ? new HashMap<>() : TransferResult.toMap(allStatuses);
            return transferBulkWithErrors(io, command, author, me, db, sheet, bank_note, nation_account, ingame_bank, offshore_account, tax_account, use_receiver_tax_account, expire, decay, deduct_as_cash, escrow_mode, bypass_checks, force, key, errors);
        }
    }

    @Command(desc = "Get a sheet of nations and their revenue (compared to optimal city builds)", viewable = true)
    @RolePermission(value = Roles.MEMBER, onlyInGuildAlliance = true)
    public String revenueSheet(@Me IMessageIO io, @Me @Default GuildDB db, NationList nations, @Switch("s") SpreadSheet sheet, @Switch("i") boolean include_untaxable,
                               @Switch("t") @Timestamp Long snapshotTime) throws GeneralSecurityException, IOException, ExecutionException, InterruptedException {
        Set<DBNation> nationSet = PW.getNationsSnapshot(nations.getNations(), nations.getFilter(), snapshotTime, db == null ? null : db.getGuild());
        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKey.REVENUE_SHEET);
        }

        int sizeOriginal = nationSet.size();
        int numRemovedNotAA = 0;
        if (db != null) {
            Set<Integer> ids = db.getAllianceIds(false);
            nationSet.removeIf(f -> f.getPosition() <= Rank.APPLICANT.id || (!ids.isEmpty() && !ids.contains(f.getAlliance_id())));
            numRemovedNotAA = sizeOriginal - (sizeOriginal = nationSet.size());
        }
        nationSet.removeIf(f -> f.getVm_turns() > 0);
        int numRemovedVM = sizeOriginal - (sizeOriginal = nationSet.size());
        if (!include_untaxable) nationSet.removeIf(f -> !f.isTaxable());
        int numRemovedUntaxable = sizeOriginal - (sizeOriginal = nationSet.size());
        List<String> footer = new ArrayList<>();
        if (numRemovedNotAA > 0) footer.add(numRemovedNotAA + " nations were removed for not being members of the guild's alliances");
        if (numRemovedVM > 0) footer.add(numRemovedVM + " nations were removed for being in vacation mode");
        if (numRemovedUntaxable > 0) footer.add(numRemovedUntaxable + " nations were removed for being untaxable");

        if (nationSet.isEmpty()) {
            return "No nations to process." + StringMan.join(footer, "\n");
        }

        if (nations.getNations().size() > 100 && db.isValidAlliance()) {
            throw new IllegalArgumentException("Too many nations: " + nations.getNations().size() + " (max: 100 outside of an alliance guild)");
        }

        List<String> header = new ArrayList<>(Arrays.asList(
                "nation",
                "tax_id",
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

        CompletableFuture<IMessageBuilder> msgFuture = (io.sendMessage("Please wait... "));
        long[] start = {System.currentTimeMillis()};

        Function<DBNation, List<String>> addRowTask = nation -> {
            if (start[0] + 10000 < System.currentTimeMillis()) {
                start[0] = System.currentTimeMillis();
                io.updateOptionally(msgFuture, "Updating build for " + nation.getMarkdownUrl());
            }

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

            double revenueConverted = ResourceType.convertedTotal(revenue);
            double revenueRaw = 0;
            double revenueManu = 0;
            for (ResourceType type : ResourceType.values) {
                if (type.isManufactured()) revenueManu += ResourceType.convertedTotal(type, revenue[type.ordinal()]);
                if (type.isRaw()) revenueRaw += ResourceType.convertedTotal(type, revenue[type.ordinal()]);
            }
            double revenueCommerce = revenue[0];

            List<String> row = new ArrayList<>(header);
            row.set(0, MarkupUtil.sheetUrl(nation.getNation(), PW.getUrl(nation.getNation_id(), false)));
            row.set(1, nation.getTax_id() + "");
            row.set(2, nation.getCities() + "");
            row.set(3, MathMan.format(nation.getAvg_infra()));
            row.set(4, MathMan.format(nation.getAvgLand()));
            row.set(5, MathMan.format(nation.getAvgBuildings()));
            row.set(6, MathMan.format(disease));
            row.set(7, MathMan.format(crime));
            row.set(8, MathMan.format(pollution));
            row.set(9, MathMan.format(population));
            row.set(10, "=\"" + nation.getMMR()+ "\"");
            row.set(11, "=\"" + nation.getMMRBuildingStr()+ "\"");
            row.set(12, MathMan.format(revenueConverted));

            row.set(13, MathMan.format(100 * revenueRaw / revenueConverted));
            row.set(14, MathMan.format(100 * revenueManu / revenueConverted));
            row.set(15, MathMan.format(100 * revenueCommerce / revenueConverted));

            JavaCity city1 = cities.entrySet().iterator().next().getValue();

            double profit = city1.profitConvertedCached(nation.getContinent(), nation.getRads(), nation::hasProject, nation.getCities(), nation.getGrossModifier());
            JavaCity origin = new JavaCity(city1);
            origin.zeroNonMilitary().setOptimalPower(nation.getContinent());
            try {
                JavaCity optimal = origin.optimalBuild(nation, 0, false, null);
                double profitOptimal = 0;
                if (optimal != null) {
                    profitOptimal = optimal.profitConvertedCached(nation.getContinent(), nation.getRads(), nation::hasProject, nation.getCities(), nation.getGrossModifier());
                }
                double optimalGain = profit >= profitOptimal ? 1 : profit / profitOptimal;

                row.set(16, MathMan.format(100 * optimalGain));
            } catch (IllegalArgumentException e) {
                row.set(16, e.getMessage());
            }

            int i = 17;
            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) continue;
                row.set(i++, MathMan.format(revenue[type.ordinal()]));
            }

            return row;
        };

        List<Future<List<String>>> addRowFutures = new ArrayList<>();

        for (DBNation nation : nationSet) {
            Future<List<String>> future = Locutus.imp().getExecutor().submit(() -> addRowTask.apply(nation));
            addRowFutures.add(future);
        }

        for (Future<List<String>> future : addRowFutures) {
            sheet.addRow(future.get());
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        sheet.attach(io.create(), "revenue").append(StringMan.join(footer, "\n")).send();
        return null;
    }

    @Command(desc = "Get a sheet of members and their saved up warchest (can include deposits and potential revenue)", viewable = true)
    @RolePermission(value = {Roles.ECON_STAFF, Roles.ECON, Roles.MILCOM, Roles.MILCOM_NO_PINGS})
    @IsAlliance
    public String warchestSheet(@Me GuildDB db, @Me IMessageIO io, Set<DBNation> nations,
                                @Arg("The required warchest per city. Else uses the guild default") @Switch("c") Map<ResourceType, Double> perCityWarchest,
                                @Arg("Count current grants against warchest totals") @Switch("g") boolean includeGrants,
                                @Arg("If negative deposits are NOT normalized (to ignore negatives)") @Switch("n") boolean doNotNormalizeDeposits,
                                @Arg("If deposits are NOT included in warchest totals") @Switch("d") boolean ignoreDeposits,
                                @Arg("Do not count resources above the required amount toward total warchest value") @Switch("e") boolean ignoreStockpileInExcess,
                                @Arg("Include days of potential revenue toward warchest resources")@Switch("r") Integer includeRevenueDays,
                                @Switch("f") boolean forceUpdate) throws IOException, GeneralSecurityException {
        AllianceList alliance = db.getAllianceList();
        if (alliance == null) {
            throw new IllegalArgumentException("This guild is not registered to an alliance. See: " + GuildKey.ALLIANCE_ID.getCommandMention());
        }
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
            double[] stockpileArr2 = ResourceType.resourcesToArray(myStockpile);
            double[] total = stockpileArr2.clone();
            double[] depo = ResourceType.getBuffer();
            if (!ignoreDeposits) {
                depo = nation.getNetDeposits(db, includeGrants, forceUpdate ? 0L : -1L, false);
                if (!doNotNormalizeDeposits) {
                    depo = PW.normalize(depo);
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

        SpreadSheet sheet = SpreadSheet.create(db, SheetKey.WARCHEST_SHEET);
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

            double[] localPerCityWarchest = ResourceType.resourcesToArray(wcReqFunc.apply(nation));
            double requiredValue = ResourceType.convertedTotal(localPerCityWarchest) * nation.getCities();
            double wcPct = (requiredValue - ResourceType.convertedTotal(lacking)) / requiredValue;
            double wcPctConverted = (requiredValue - ResourceType.convertedTotal(lacking) + ResourceType.convertedTotal(excess)) / requiredValue;

            double revenueIndividualValue = 0;
            double revenueAggregateValue = 0;
            double revenueValue = ResourceType.convertedTotal(revenue);
            for (int i = 0; i < revenue.length; i++) {
                double amt = revenue[i];
                if (amt == 0) continue;;
                if (lacking[i] > 0) {
                    revenueIndividualValue += ResourceType.convertedTotal(ResourceType.values[i], amt);
                }
                if (totalNet[i] < 0) {
                    revenueAggregateValue += ResourceType.convertedTotal(ResourceType.values[i], amt);
                }
            }

            header.set(0, MarkupUtil.sheetUrl(nation.getNation(), PW.getUrl(nation.getNation_id(), false)));
            header.set(1, nation.getCities() + "");
            header.set(2, nation.getMMRBuildingStr());
            header.set(3, nation.getMMR());

            header.set(4, MathMan.format(100 * wcPct));
            header.set(5, MathMan.format(100 * wcPctConverted));

            header.set(6, MathMan.format(100 * (revenueIndividualValue / revenueValue)));
            header.set(7, MathMan.format(100 * (revenueAggregateValue / revenueValue)));
            header.set(8, ResourceType.toString(lacking));
            header.set(9, ResourceType.toString(excess));
            header.set(10, ResourceType.toString(warchest));
            header.set(11, MathMan.format(ResourceType.convertedTotal(lacking)));
            header.set(12, MathMan.format(ResourceType.convertedTotal(excess)));

            sheet.addRow(header);
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        StringBuilder response = new StringBuilder();
        response.append("Total Warchest: `" + ResourceType.toString(totalWarchest) + "` worth: ~$" + MathMan.format(ResourceType.convertedTotal(totalWarchest)) + "\n");
        response.append("Net Warchest Req (warchest- requirements): `" + ResourceType.toString(totalNet) + "` worth: ~$" + MathMan.format(ResourceType.convertedTotal(totalNet)));

        sheet.attach(io.create(), "warchest", response, false, 0).append(response.toString()).send();
        return null;
    }

    public static final Map<UUID, Grant> AUTHORIZED_TRANSFERS = new HashMap<>();

    @Command(desc = "Withdraw from the alliance bank (nation balance)", groups = {
            "Amount Options",
            "Optional: Bank Note",
            "Specify Offshore/Bank",
            "Optional: Nation Account",
            "Optional: Tax Bracket Account (pick either or none)",
    })
    @RolePermission(value = {Roles.ECON_WITHDRAW_SELF, Roles.ECON}, any=true)
    public String withdraw(@Me IMessageIO channel, @Me JSONObject command,
                           @Me User author, @Me DBNation me, @Me GuildDB guildDb,

                           @Arg(value = "Amount to send", group = 0, aliases = "transfer")
                           @NationDepositLimit Map<ResourceType, Double> amount,
                           @Arg(value = "Only send funds the receiver is lacking from the amount", aliases = "onlymissingfunds") @Switch("m") boolean only_send_missing,

                           @Arg(value = "Transfer note", group = 1, aliases = "deposittype")
                           @Default("#deposit") DepositType.DepositTypeInfo bank_note,
                           @Arg(value = "Have the transfer ignored from nation holdings after a timeframe", group = 1) @Switch("e") @Timediff Long expire,
                           @Arg(value = "Have the transfer decrease linearly from balances over a timeframe", group = 1) @Switch("d") @Timediff Long decay,
                           @Arg(value = "Transfer valued at cash equivalent in nation balance", group = 1, aliases = "convertcash") @Switch("c") boolean deduct_as_cash,

                           @Arg(value = "The in-game alliance bank to send from\n" +
                                   "Defaults to the offshore set", group = 2, aliases = "usealliancebank") @Switch("a") DBAlliance ingame_bank,
                           @Arg(value = """
                                   The account with the offshore to use
                                   The alliance must be registered to this guild
                                   Defaults to all the alliances of this guild""", group = 2, aliases = "useoffshoreaccount") @Switch("o") DBAlliance offshore_account,

                           @Arg(value = "The guild's nation account to use\n" +
                                   "Defaults to your nation", group = 3, aliases = "depositsaccount") @Switch("n") DBNation nation_account,
                           @Arg(value = "How to handle the transfer if the receiver is blockaded\n" +
                                   "Defaults to never escrow", group = 3) @Switch("em") EscrowMode escrow_mode,

                           @Arg(value = "The guild's tax account to deduct from\n" +
                                   "Defaults to None", group = 4) @Switch("t") TaxBracket tax_account,
                           @Arg(value = "OR deduct from the receiver's tax bracket account\n" +
                                   "Defaults to false", group = 4, aliases = "existingtaxaccount") @Switch("ta") boolean use_receiver_tax_account,

                           @Arg("Skip checking receiver activity, blockade, VM etc.")
                           @Switch("b") boolean bypass_checks,

                           @Switch("f") boolean force
    ) throws IOException {
        return transfer(channel, command, author, me, guildDb, me, amount, bank_note,
                nation_account == null ? me : nation_account,
                ingame_bank,
                offshore_account,
                tax_account,
                use_receiver_tax_account,
                only_send_missing,
                expire,
                decay,
                null,
                deduct_as_cash,
                escrow_mode,
                bypass_checks,
                false,
                force);
    }

    @Command(desc = "Bulk shift resources in a nations holdings to another note category")
    @RolePermission(Roles.ECON)
    public String shiftDeposits(@Me GuildDB db, @Me IMessageIO io, @Me User author, @Me DBNation me, DBNation nation, @Arg("The note to change FROM") DepositType from, @Arg("The new note to use") DepositType to, @Arg("Make the funds expire") @Default @Timestamp Long expireTime, @Arg("Make the funds decay") @Default @Timestamp Long decayTime) throws IOException {
        if (from == to) throw new IllegalArgumentException("From and to must be a different category.");
        if (expireTime != null && expireTime != 0 && to != DepositType.GRANT) {
            throw new IllegalArgumentException("The grant expiry time is only needed if converted to the grant category");
        }
        if (decayTime != null && decayTime != 0 && to != DepositType.GRANT) {
            throw new IllegalArgumentException("The grant decay time is only needed if converted to the grant category");
        }
        Long allowedAllianceId = Roles.ECON.hasAlliance(author, db.getGuild());
        if (allowedAllianceId == null) {
            throw new IllegalArgumentException("Missing " + Roles.ECON.toDiscordRoleNameElseInstructions(db.getGuild()));
        }
        if (allowedAllianceId != 0L && allowedAllianceId != nation.getAlliance_id()) {
            throw new IllegalArgumentException("You can only shift deposits for nations in your alliance (" + PW.getMarkdownUrl(allowedAllianceId.intValue(), true));
        }

        String note = "#" + to.name().toLowerCase(Locale.ROOT);

        if (to == DepositType.GRANT) {
            if ((expireTime == null || expireTime == 0) && (decayTime == null || decayTime == 0)) {
                throw new IllegalArgumentException("You must specify a grant expiry timediff if converting to the grant category. e.g. `60d`");
            } else {
                if (expireTime != null && expireTime != 0) {
                    note += " #expire=timestamp:" + (System.currentTimeMillis() + expireTime);
                }
                if (decayTime != null && decayTime != 0) {
                    note += " #decay=timestamp:" + (System.currentTimeMillis() + decayTime);
                }
            }
        }
        Map<DepositType, double[]> depoByType = nation.getDeposits(db, null, true, true, 0, 0, true);

        double[] toAdd = depoByType.get(from);
        if (toAdd == null || ResourceType.isZero(toAdd)) {
            return "Nothing to shift for " + nation.getNation();
        }
        long now = System.currentTimeMillis();
        if (from == DepositType.GRANT) {
            SimpleNationList nationList = new SimpleNationList(Collections.singleton(nation));
            resetDeposits(db, me, io, author, db.getGuild(), null, nationList, false, true, true, true, false, true);
        } else {
            String noteFrom = "#" + from.name().toLowerCase(Locale.ROOT);
            db.subBalance(now, nation, me.getNation_id(), noteFrom, toAdd);
        }
        db.addBalance(now, nation, me.getNation_id(), note, toAdd);
        return "Shifted " + ResourceType.toString(toAdd) + " from " + from + " to " + to + " for " + nation.getNation();
    }

    @Command(desc = "Resets a nations deposits to net zero (of the specific note categories)", groups = {
            "Reset Options"
    })
    @RolePermission(Roles.ECON)
    public String resetDeposits(@Me GuildDB db, @Me DBNation me, @Me IMessageIO io, @Me User author, @Me Guild guild,
                                @Me JSONObject command,
                                NationList nations,
                                @Arg(value = "Do NOT reset grants", group = 0) @Switch("g") boolean ignoreGrants,
                                @Arg(value = "Do NOT reset loans", group = 0) @Switch("l") boolean ignoreLoans,
                                @Arg(value = "Do NOT reset taxes", group = 0) @Switch("t") boolean ignoreTaxes,
                                @Arg(value = "Do NOT reset deposits", group = 0) @Switch("d") boolean ignoreBankDeposits,
                                @Arg(value = "Do NOT reset escrow", group = 0) @Switch("e") boolean ignoreEscrow,
                                @Switch("f") boolean force) throws IOException {
        if (nations.getNations().size() > 300) {
            throw new IllegalArgumentException("Due to performance issues, you can only reset up to 300 nations at a time");
        }

        long now = System.currentTimeMillis();
        StringBuilder response = new StringBuilder("Resetting deposits for `" + nations.getFilter() + "`\n");

        double[] totalDeposits = ResourceType.getBuffer();
        double[] totalTax = ResourceType.getBuffer();
        double[] totalLoan = ResourceType.getBuffer();
        double[] totalExpire = ResourceType.getBuffer();
        double[] totalEscrow = ResourceType.getBuffer();

        if (force) {
            String errorMsg = handleAddbalanceAllianceScope(author, guild, (Set) nations.getNations());
            if (errorMsg != null) return errorMsg;
        }

        CompletableFuture<IMessageBuilder> msgFuture = io.send("Please wait...");
        long start = System.currentTimeMillis();

        for (DBNation nation : nations.getNations()) {
            if (start + 5000 < System.currentTimeMillis()) {
                start = System.currentTimeMillis();
                io.updateOptionally(msgFuture, "Resetting deposits for " + nation.getMarkdownUrl());
            }
            Map<DepositType, double[]> depoByType = nation.getDeposits(db, null, true, true, force ? 0L : -1L, 0, true);

            double[] deposits = depoByType.get(DepositType.DEPOSIT);
            if (deposits != null && !ignoreBankDeposits && !ResourceType.isZero(deposits)) {
                ResourceType.round(deposits);
                response.append("Subtracting `" + nation.getQualifiedId() + " " + ResourceType.toString(deposits) + " #deposit`\n");
                ResourceType.subtract(totalDeposits, deposits);
                if (force) db.subBalance(now, nation, me.getNation_id(), "#deposit", deposits);
            }

            double[] tax = depoByType.get(DepositType.TAX);
            if (tax != null && !ignoreTaxes && !ResourceType.isZero(tax)) {
                ResourceType.round(tax);
                response.append("Subtracting `" + nation.getQualifiedId() + " " + ResourceType.toString(tax) + " #tax`\n");
                ResourceType.subtract(totalTax, tax);
                if (force) db.subBalance(now, nation, me.getNation_id(), "#tax", tax);
            }

            double[] loan = depoByType.get(DepositType.LOAN);
            if (loan != null && !ignoreLoans && !ResourceType.isZero(loan)) {
                ResourceType.round(loan);
                response.append("Subtracting `" + nation.getQualifiedId() + " " + ResourceType.toString(loan) + " #loan`\n");
                ResourceType.subtract(totalLoan, loan);
                if (force) db.subBalance(now, nation, me.getNation_id(), "#loan", loan);
            }

            double[] grant = depoByType.get(DepositType.GRANT);
            if (grant != null && !ignoreGrants && !ResourceType.isZero(grant)) {
                List<Map.Entry<Integer, Transaction2>> transactions = nation.getTransactions(db, null, true, true, -1, 0, true);
                for (Map.Entry<Integer, Transaction2> entry : transactions) {
                    Transaction2 tx = entry.getValue();
                    if (tx.note == null || (tx.receiver_id != nation.getNation_id() && tx.sender_id != nation.getNation_id()) || (!tx.note.contains("#expire") && !tx.note.contains("#decay")))
                        continue;
                    if (tx.sender_id == tx.receiver_id) continue;
                    Map<DepositType, Object> noteMap = tx.getNoteMap();
                    Object expire3 = noteMap.get(DepositType.EXPIRE);
                    Object decay3 = noteMap.get(DepositType.DECAY);
                    long expireEpoch = Long.MAX_VALUE;
                    long decayEpoch = Long.MAX_VALUE;
                    if (expire3 instanceof Number n) {
                        expireEpoch = n.longValue();
                    }
                    if (decay3 instanceof Number n) {
                        decayEpoch = n.longValue();
                    }
                    expireEpoch = Math.min(expireEpoch, decayEpoch);
                    if (expireEpoch > now) {
                        String noteCopy = tx.note.toLowerCase(Locale.ROOT)
                                .replaceAll("#expire=[a-zA-Z0-9:]+", "")
                                .replaceAll("#decay=[a-zA-Z0-9:]+", "");
                        if (expire3 instanceof Number) {
                            noteCopy += " #expire=" + "timestamp:" + expireEpoch;
                        }
                        if (decay3 instanceof Number) {
                            noteCopy += " #decay=" + "timestamp:" + decayEpoch;
                        }
                        noteCopy = noteCopy.trim();

                        tx.tx_datetime = System.currentTimeMillis();
                        int sign = entry.getKey();
                        if (sign == 1) {
                            response.append("Subtracting `" + nation.getQualifiedId() + " " + ResourceType.toString(tx.resources) + " " + noteCopy + "`\n");
                            ResourceType.subtract(totalExpire, tx.resources);
                            if (force) db.subBalance(now, nation, me.getNation_id(), noteCopy, tx.resources);
                        } else if (sign == -1) {
                            response.append("Adding `" + nation.getQualifiedId() + " " + ResourceType.toString(tx.resources) + " " + noteCopy + "`\n");
                            ResourceType.add(totalExpire, tx.resources);
                            if (force) db.addBalance(now, nation, me.getNation_id(), noteCopy, tx.resources);
                        } else {
                            System.out.println("Invalid sign for deposits reset " + sign);
                        }
                    }
                }
            }

            if (!ignoreEscrow) {
                try {
                    Map.Entry<double[], Long> escrowedPair = db.getEscrowed(nation);
                    if (escrowedPair != null && !ResourceType.isZero(escrowedPair.getKey())) {
                        response.append("Subtracting escrow: `" + nation.getQualifiedId() + " " + ResourceType.toString(escrowedPair.getKey()) + "`\n");
                        ResourceType.subtract(totalEscrow, escrowedPair.getKey());
                        if (force) db.setEscrowed(nation, null, 0);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    response.append("Failed to reset escrow balance: " + e.getMessage() + "\n");
                }
            }
        }

        if (!force) {
            String name = nations.getFilter();
            String title = "Reset deposits for " + (name.length() > 100 ? nations.getNations().size() + " nations" : name);
            StringBuilder body = new StringBuilder();
            if (!ResourceType.isZero(totalDeposits)) {
                body.append("Net Adding `" + name + " " + ResourceType.toString(totalDeposits) + " #deposit`\n");
            }
            if (!ResourceType.isZero(totalTax)) {
                body.append("Net Adding `" + name + " " + ResourceType.toString(totalTax) + " #tax`\n");
            }
            if (!ResourceType.isZero(totalLoan)) {
                body.append("Net Adding `" + name + " " + ResourceType.toString(totalLoan) + " #loan`\n");
            }
            if (!ResourceType.isZero(totalExpire)) {
                body.append("Net Adding `" + name + " " + ResourceType.toString(totalExpire) + " #expire`\n");
            }
            if (!ResourceType.isZero(totalEscrow)) {
                body.append("Deleting Escrow: `" + name + " " + ResourceType.toString(totalEscrow) + "`\n");
            }

            double[] total = ResourceType.getBuffer();
            total = ResourceType.add(total, totalDeposits);
            total = ResourceType.add(total, totalTax);
            total = ResourceType.add(total, totalLoan);
            total = ResourceType.add(total, totalExpire);
            total = ResourceType.subtract(total, totalEscrow);
            body.append("Total Net: `" + name + " " + ResourceType.toString(total) + "`\n");
            body.append("\n\nSee attached file for transaction details\n");

            io.create().confirmation(title, body.toString(), command)
                    .file("transaction.txt", response.toString()).send();
            return null;
        }

        return response.toString();
    }

    @Command(desc = "Transfer from the alliance bank (alliance deposits)")
    @RolePermission(value = {Roles.ECON, Roles.ECON_WITHDRAW_SELF}, any = true)
    public static String transfer(@Me IMessageIO channel, @Me JSONObject command,
                                  @Me User author, @Me DBNation me, @Me GuildDB guildDb, NationOrAlliance receiver, @AllianceDepositLimit Map<ResourceType, Double> transfer, DepositType.DepositTypeInfo depositType,
                                  @Arg("The nation account to deduct from") @Switch("n") DBNation nationAccount,
                                  @Arg("The alliance bank to send from\nDefaults to the offshore") @Switch("a") DBAlliance senderAlliance,
                                  @Arg("The alliance account to deduct from\nAlliance must be registered to this guild\nDefaults to all the alliances of this guild") @Switch("o") DBAlliance allianceAccount,
                                  @Arg("The tax account to deduct from") @Switch("t") TaxBracket taxAccount,
                                  @Arg("Deduct from the receiver's tax bracket account") @Switch("ta") boolean existingTaxAccount,
                                  @Arg("Only send funds the receiver is lacking from the amount") @Switch("m") boolean onlyMissingFunds,
                                  @Arg("Have the transfer ignored from nation holdings after a timeframe") @Switch("e") @Timediff Long expire,
                                  @Arg("Have the transfer decrease linearly to zero for balances over a timeframe") @Switch("d") @Timediff Long decay,
                                  @Switch("g") UUID token,
                                  @Arg("Transfer valued at cash equivalent in nation holdings") @Switch("c") boolean convertCash,
                                  @Arg("The mode for escrowing funds (e.g. if the receiver is blockaded)\nDefaults to never") @Switch("em") EscrowMode escrow_mode,
                                  @Switch("b") boolean bypassChecks,
                                  @Switch("p") boolean ping_when_sent,
                                  @Switch("f") boolean force) throws IOException {
        if (existingTaxAccount) {
            if (taxAccount != null) throw new IllegalArgumentException("You can't specify both `tax_id` and `existingTaxAccount`");
            if (!receiver.isNation()) throw new IllegalArgumentException("You can only specify `existingTaxAccount` for a nation");
            taxAccount = receiver.asNation().getTaxBracket();
        }
        if (receiver.isAlliance() && onlyMissingFunds) {
            return "Option `-o` only applicable for nations";
        }

        if (receiver.isAlliance() && !receiver.asAlliance().exists()) {
            throw new IllegalArgumentException("Alliance: " + receiver.getUrl() + " has no receivable nations");
        }
        List<String> forceErrors = new ArrayList<>();
        if (receiver.isNation()) {
            DBNation nation = receiver.asNation();
            if (nation.getVm_turns() > 0) forceErrors.add("Receiver is in Vacation Mode");
            if (nation.isGray()) forceErrors.add("Receiver is Gray");
            if (nation.getNumWars() > 0 && receiver.asNation().isBlockaded() && (escrow_mode == null || escrow_mode == EscrowMode.NEVER)) forceErrors.add("Receiver is blockaded");
            if (nation.active_m() > 10000) forceErrors.add(("!! **WARN**: Receiver is " + TimeUtil.secToTime(TimeUnit.MINUTES, nation.active_m())) + " inactive");
        } else if (receiver.isAlliance()) {
            DBAlliance alliance = receiver.asAlliance();
            if (alliance.getNations(f -> f.getPositionEnum().id > Rank.HEIR.id && f.getVm_turns() == 0 && f.active_m() < 10000).size() == 0) {
                forceErrors.add("Alliance has no active leaders/heirs (are they in vacation mode?)");
            }
        }
        if (!forceErrors.isEmpty() && !bypassChecks) {
            String title = forceErrors.size() + " **ERRORS**. Please confirm transfer";
            String body = StringMan.join(forceErrors, "\n") + "\n\n" +
                    "Press `Confirm` to attempt to send anyway";
            channel.create().confirmation(title, body, command, "bypassChecks").send();
            return null;
        }

        OffshoreInstance offshore;
        if (senderAlliance != null) {
            if (!guildDb.isAllianceId(senderAlliance.getAlliance_id())) {
                return "This guild is not registered to the alliance `" + senderAlliance.getName() + "`";
            }
            offshore = senderAlliance.getBank();
        } else {
            offshore = guildDb.getOffshore();
        }
        if (offshore == null) {
            return "No offshore is setup. See " + CM.offshore.add.cmd.toSlashMention();
        }
        if (nationAccount == null && Roles.ECON.getAllianceList(author, guildDb).isEmpty()) {
            nationAccount = me;
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

        // transfer limit
        long userId = author.getIdLong();
        if (ResourceType.convertedTotal(transfer) > 5000000000L
                && userId != Locutus.loader().getAdminUserId()
                && !Settings.INSTANCE.LEGACY_SETTINGS.WHITELISTED_BANK_USERS.contains(userId)
                && !isGrant && offshore.getAllianceId() == Settings.INSTANCE.ALLIANCE_ID()
        ) {
            return "Transfer too large. Please specify a smaller amount";
        }

        boolean hasAdmin = Roles.ECON.has(author, guildDb.getGuild());
        Map<Long, AccessType> allowedIds = guildDb.getAllowedBankAccountsOrThrow(me, author, receiver, channel.getIdLong(), hasAdmin);

        if (onlyMissingFunds) {
            int aaId = receiver.getAlliance_id();
            if (aaId == 0) {
                return "Receiver is not in an alliance (cannot determine missing funds)";
            }
            AccessType accessType = allowedIds.get((long) aaId);
            if (accessType == null) {
                return "You do not have access to the alliance stockpile information for " + DBAlliance.getOrCreate(aaId).getQualifiedId();
            }
            if (me.getId() != receiver.getId()) {
                if (accessType != AccessType.ECON) {
                    return "You can only access stockpile information for yourself";
                }
            }
            if (!guildDb.isAllianceId(receiver.getAlliance_id())) {
                return "This guild is not registered to the alliance `" + receiver.getAlliance_id() + "`. See: " + CM.settings_default.registerAlliance.cmd.toSlashMention();
            }
            Map<ResourceType, Double> existing = receiver.getStockpile();
            for (Map.Entry<ResourceType, Double> entry : transfer.entrySet()) {
                double toSend = Math.max(0, entry.getValue() - existing.getOrDefault(entry.getKey(), 0d));
                entry.setValue(toSend);
            }
        }

        TransferResult result;
        try {
            result = offshore.transferFromNationAccountWithRoleChecks(
                    me,
                    author,
                    nationAccount,
                    allianceAccount,
                    taxAccount,
                    guildDb,
                    channel.getIdLong(),
                    receiver,
                    ResourceType.resourcesToArray(transfer),
                    depositType,
                    expire,
                    decay,
                    null,
                    convertCash,
                    escrow_mode,
                    !force,
                    bypassChecks
            );
        } catch (IllegalArgumentException | IOException e) {
//            result = new KeyValue<>(OffshoreInstance.TransferStatus.OTHER, e.getMessage());
            result = new TransferResult(OffshoreInstance.TransferStatus.OTHER, receiver, transfer, depositType.toString()).addMessage(e.getMessage());
        }
        if (result.getStatus() == OffshoreInstance.TransferStatus.CONFIRMATION) {
            String worth = "$" + MathMan.format(ResourceType.convertedTotal(transfer));
            String title = "Send (worth: " + worth + ") to " + receiver.getTypePrefix() + ":" + receiver.getName();
            if (receiver.isNation()) {
                title += " | " + receiver.asNation().getAlliance();
            }
            channel.create().confirmation(title, result.getMessageJoined(false), command, "force", "Send").cancelButton().send();
            return null;
        }

        IMessageBuilder msg = channel.create().embed(result.toTitleString(), result.toEmbedString());

        if (ping_when_sent && receiver.isNation()) {
            User user = receiver.asNation().getUser();
            if (user != null) {
                MessageChannel notify = GuildKey.GRANT_REQUEST_CHANNEL.getOrNull(guildDb);
                if (notify == null) notify = guildDb.getResourceChannel(receiver.getAlliance_id());
                if (notify == null) notify = guildDb.getResourceChannel(0);
                if (notify != null) {
                    DiscordUtil.sendMessage(notify, user.getAsMention() + " " + result.getMessageJoined(false));
                } else {
                    msg.append(user.getAsMention());
                }
            }
        }

        msg.send();
        return null;
    }

    @Command(desc = "Sheet of projects each nation has", viewable = true)
    @RolePermission(value = Roles.MEMBER, onlyInGuildAlliance = true)
    public String ProjectSheet(@Me IMessageIO io, @Me @Default GuildDB db, NationList nations, @Switch("s") SpreadSheet sheet, @Switch("t") @Timestamp Long snapshotTime) throws GeneralSecurityException, IOException {
        Set<DBNation> nationSet = PW.getNationsSnapshot(nations.getNations(), nations.getFilter(), snapshotTime, db == null ? null : db.getGuild());
        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKey.PROJECT_SHEET);
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

        for (DBNation nation : nationSet) {

            header.set(0, MarkupUtil.sheetUrl(nation.getNation(), PW.getUrl(nation.getNation_id(), false)));
            header.set(1, MarkupUtil.sheetUrl(nation.getAllianceName(), PW.getUrl(nation.getAlliance_id(), true)));
            header.set(2, nation.getCities());
            header.set(3, nation.getAvg_infra());
            header.set(4, nation.getScore());

            for (int i = 0; i < Projects.values.length; i++) {
                Project project = Projects.values[i];
                if (project.isDisabled()) continue;
                header.set(5 + i, nation.hasProject(project) + "");
            }

            sheet.addRow(header);
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        sheet.attach(io.create(), "projects").send();
        return null;
    }

    @Command(desc = "Create a google sheet of escrowed resources amounts for a set of nations", groups = {
            "Optional 1: Specific Nations",
            "Optional 2: Include past members"
    })
    @RolePermission(Roles.ECON)
    public String escrowSheetCmd(@Me IMessageIO io, @Me GuildDB db, @Me Guild guild,
                                 @Arg(value = "Specify the nations to include in the sheet\n" +
                                         "Defaults to current members", group = 0)
                                 @Default Set<DBNation> nations,
                                 @Arg(value = "Include all nations that have deposited in the past", group = 1)
                                 @Switch("p") Set<Integer> includePastDepositors,

                                 @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (nations == null) {
            Set<Integer> aaIds = db.getAllianceIds();
            if (!aaIds.isEmpty()) {
                nations = new ObjectLinkedOpenHashSet<>(Locutus.imp().getNationDB().getNationsByAlliance(aaIds));
                if (includePastDepositors == null || includePastDepositors.isEmpty()) nations.removeIf(n -> n.getPosition() <= 1);

                if (includePastDepositors != null && !includePastDepositors.isEmpty()) {
                    Set<Integer> ids = Locutus.imp().getBankDB().getReceiverNationIdFromAllianceReceivers(includePastDepositors);
                    for (int id : ids) {
                        DBNation nation = Locutus.imp().getNationDB().getNationById(id);
                        if (nation != null) nations.add(nation);
                    }
                }
            } else {
                if (includePastDepositors != null && !includePastDepositors.isEmpty()) {
                    throw new IllegalArgumentException("`usePastDepositors` is only for alliances");
                }
                if (Roles.MEMBER.toRoles(db).isEmpty()) throw new IllegalArgumentException("No " + GuildKey.ALLIANCE_ID.getCommandMention() + " set, or " + CM.role.setAlias.cmd.locutusRole(Roles.MEMBER.name()).discordRole("") + " set");
                nations = new ObjectLinkedOpenHashSet<>();
                for (Member member : Roles.MEMBER.getAll(db)) {
                    DBNation nation = DiscordUtil.getNation(member.getUser());
                    if (nation != null) {
                        nations.add(nation);
                    }
                }
                if (nations.isEmpty()) return "No members found";

            }
        } else if (includePastDepositors != null && !includePastDepositors.isEmpty()) {
            throw new IllegalArgumentException("`usePastDepositors` cannot be set when nations are provided");
        }
        Map.Entry<SpreadSheet, double[]> sheetPair = escrowSheet(db, nations, sheet);
        sheet = sheetPair.getKey();
        double[] totalEscrowed = sheetPair.getValue();

        sheet.updateClearCurrentTab();
        sheet.updateWrite();
        // appent resource string and worth
        sheet.attach(io.create(), "escrow").append("Total Escrowed: `" + ResourceType.toString(totalEscrowed) + "` | worth: ~$" + MathMan.format(ResourceType.convertedTotal(totalEscrowed))).send();
        return null;
    }

    private static Map.Entry<SpreadSheet, double[]> escrowSheet(GuildDB db, Collection<DBNation> nations, SpreadSheet sheetOrNull) throws GeneralSecurityException, IOException {
        double[] totalEscrowed = ResourceType.getBuffer();
        List<Object> escrowHeader = new ArrayList<>(Arrays.asList(
                "nation",
                "cities",
                "age",
                "expires",
                "value"

        ));
        for (ResourceType type : ResourceType.values()) {
            if (type == ResourceType.CREDITS) continue;
            escrowHeader.add(type.name());
        }
        SpreadSheet escrowSheet = sheetOrNull != null ? sheetOrNull : SpreadSheet.create(db, SheetKey.ESCROW_SHEET);
        escrowSheet.setHeader(escrowHeader);

        for (DBNation nation : nations) {
            Map.Entry<double[], Long> escrowedPair = db.getEscrowed(nation);
            if (escrowedPair == null || ResourceType.isZero(escrowedPair.getKey())) continue;
            ResourceType.add(totalEscrowed, escrowedPair.getKey());

            escrowHeader.set(0, MarkupUtil.sheetUrl(nation.getNation(), PW.getUrl(nation.getNation_id(), false)));
            escrowHeader.set(1, nation.getCities());
            escrowHeader.set(2, nation.getAgeDays());

            long expireEpoch = escrowedPair.getValue();
            String expires = expireEpoch == 0 ? "never" : TimeUtil.YYYY_MM_DD_HH_MM_SS.format(expireEpoch);
            escrowHeader.set(3, expires);

            double value = ResourceType.convertedTotal(escrowedPair.getKey());
            escrowHeader.set(4, MathMan.format(value));
            int i = 0;
            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) continue;
                escrowHeader.set(5 + (i++), MathMan.format(escrowedPair.getKey()[type.ordinal()]));
            }

            escrowSheet.addRow(escrowHeader);
        }

        return KeyValue.of(escrowSheet, totalEscrowed);
    }

    @Command(aliases = {"depositSheet", "depositsSheet"}, desc =
            """
                    Get a sheet with member nations and their deposits
                    Each nation's safekeep should match the total balance given by deposits command\
                    Add `-b` to\s
                    Add `-o` to not include any manual deposit offsets
                    Add `-d` to not include deposits
                    Add `-t` to not include taxes
                    Add `-l` to not include loans
                    Add `-g` to not include grants
                    Add `-p` to include past depositors
                    Add `-f` to force an update""",
            viewable = true)
    @RolePermission(Roles.ECON)
    public static String depositSheet(@Me IMessageIO channel, @Me Guild guild, @Me GuildDB db,
                               @Default Set<DBNation> nations,
                               @Arg("The alliances to track transfers from") @Default Set<DBAlliance> offshores,
                               @Arg("use 0/0 as the tax base") @Switch("b") boolean ignoreTaxBase,
                               @Arg("Do NOT include any manual deposit offsets") @Switch("o") boolean ignoreOffsets,
                                      @Arg("Include ALL #expire and #decay transfers") @Switch("ex") boolean includeExpired,
                                      @Arg("Include #ignore transfers") @Switch("i") boolean includeIgnored,
                               @Arg("Do NOT include taxes") @Switch("t") boolean noTaxes,
                               @Arg("Do NOT include loans") @Switch("l") boolean noLoans,
                               @Arg("Do NOT include grants") @Switch("g") boolean noGrants,
                               @Arg("Do NOT include deposits") @Switch("d") boolean noDeposits,
                               @Arg("Include past depositors") @Switch("p") Set<Integer> includePastDepositors,
                               @Arg("Do NOT include escrow sheet") @Switch("e") boolean noEscrowSheet,
                               @Arg("""
                                       Only show the flow for this note
                                       i.e. To only see funds marked as #TRADE
                                       This is for transfer flow breakdown internal, withdrawal, and deposit""")
                                  @Switch("n") DepositType useFlowNote,
                               @Switch("f") boolean force

    ) throws GeneralSecurityException, IOException {
        CompletableFuture<IMessageBuilder> msgFuture = channel.send("Please wait...");

        SpreadSheet sheet = SpreadSheet.create(db, SheetKey.DEPOSIT_SHEET);

        List<Object> header = new ArrayList<>(Arrays.asList(
                "nation",
                "cities",
                "age",
                "deposit",
                "tax",
                "loan",
                "grant",
                "total",
                "last_deposit_day",
                "last_self_withdraw_day",
                "flow_internal",
                "flow_withdrawal",
                "flow_deposit"
        ));

        for (ResourceType type : ResourceType.values()) {
            if (type == ResourceType.CREDITS) continue;
            header.add(type.name());
        }

        sheet.setHeader(header);

        boolean useTaxBase = !ignoreTaxBase;
        boolean useOffset = !ignoreOffsets;

        if (nations == null) {
            Set<Integer> aaIds = db.getAllianceIds();
            if (!aaIds.isEmpty()) {
                nations = new ObjectLinkedOpenHashSet<>(Locutus.imp().getNationDB().getNationsByAlliance(aaIds));
                if (includePastDepositors == null || includePastDepositors.isEmpty()) nations.removeIf(n -> n.getPosition() <= 1);

                if (includePastDepositors != null && !includePastDepositors.isEmpty()) {
                    Set<Integer> ids = Locutus.imp().getBankDB().getReceiverNationIdFromAllianceReceivers(includePastDepositors);
                    for (int id : ids) {
                        DBNation nation = Locutus.imp().getNationDB().getNationById(id);
                        if (nation != null) nations.add(nation);
                    }
                }
            } else {
                if (includePastDepositors != null && !includePastDepositors.isEmpty()) {
                    throw new IllegalArgumentException("usePastDepositors is only implemented for alliances (ping borg)");
                }
                if (Roles.MEMBER.toRoles(db).isEmpty()) throw new IllegalArgumentException("No " + GuildKey.ALLIANCE_ID.getCommandMention() + " set, or " + CM.role.setAlias.cmd.locutusRole(Roles.MEMBER.name()).discordRole("") + " set");
                nations = new ObjectLinkedOpenHashSet<>();
                for (Member member : Roles.MEMBER.getAll(db)) {
                    DBNation nation = DiscordUtil.getNation(member.getUser());
                    if (nation != null) {
                        nations.add(nation);
                    }
                }
                if (nations.isEmpty()) return "No members found";

            }
        } else if (includePastDepositors != null && !includePastDepositors.isEmpty()) {
            throw new IllegalArgumentException("usePastDepositors cannot be set when nations are provided");
        }
        Set<Long> tracked = null;
        if (offshores != null) {
            tracked = new LongOpenHashSet();
            for (DBAlliance aa : offshores) tracked.add((long) aa.getAlliance_id());
            tracked = PW.expandCoalition(tracked);
        }

        double[] aaTotalPositive = ResourceType.getBuffer();
        double[] aaTotalNet = ResourceType.getBuffer();

        boolean updateBulk = Settings.INSTANCE.TASKS.BANK_RECORDS_INTERVAL_SECONDS > 0;
        if (updateBulk) {
            Locutus.imp().runEventsAsync(events -> Locutus.imp().getBankDB().updateBankRecs(false, events));
        }
        IMessageBuilder updateMsg = null;
        final AtomicLong last = new AtomicLong(System.currentTimeMillis());

        ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        AtomicInteger processedCount = new AtomicInteger(0);
        int totalNationsCount = nations.size();
        AtomicLong lastUpdateTimestamp = new AtomicLong(System.currentTimeMillis()); // For progress updates

        // Use thread-safe accumulators for totals
        DoubleAccumulator[] aaTotalPositiveAccumulator = new DoubleAccumulator[ResourceType.values().length];
        DoubleAccumulator[] aaTotalNetAccumulator = new DoubleAccumulator[ResourceType.values().length];
        DoubleBinaryOperator sum = Double::sum; // Or (x, y) -> x + y
        double identity = 0.0;

        for (int i = 0; i < ResourceType.values().length; i++) {
            aaTotalPositiveAccumulator[i] = new DoubleAccumulator(sum, identity);
            aaTotalNetAccumulator[i] = new DoubleAccumulator(sum, identity);
        }


        try {
            Set<Long> finalTracked = tracked;
            List<Callable<NationBalanceRow>> tasks = nations.stream()
                    .map(nation -> new DepositSheetTask(
                            nation, db, finalTracked, useTaxBase, useOffset, updateBulk, force,
                            useFlowNote, includeExpired, includeIgnored, header.size(),
                            noGrants, noLoans, noTaxes, noDeposits,
                            processedCount, totalNationsCount, channel, msgFuture, lastUpdateTimestamp))
                    .collect(Collectors.toList());

            // Invoke all tasks and wait for completion
            List<NationBalanceRow> results = null;
            try {
                results = pool.invokeAll(tasks).stream()
                        .map(task -> {
                            try {
                                return task.get();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .toList();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            // Process results (add rows to sheet and accumulate totals)
            for (NationBalanceRow result : results) {
                // Add row to sheet (synchronized)
                synchronized (sheet) {
                    sheet.addRow(result.row());
                }

                // Accumulate totals (thread-safe)
                if (ResourceType.convertedTotal(result.normalized()) > 0) {
                    for(int i = 0; i < result.normalized().length; i++) {
                        aaTotalPositiveAccumulator[i].accumulate(result.normalized()[i]); // Use accumulate
                    }
                }
                for(int i = 0; i < result.total().length; i++) {
                    aaTotalNetAccumulator[i].accumulate(result.total()[i]); // Use accumulate
                }
            }

            // Finalize totals after all tasks are complete
            for (int i = 0; i < ResourceType.values().length; i++) {
                aaTotalPositive[i] = aaTotalPositiveAccumulator[i].get();
                aaTotalNet[i] = aaTotalNetAccumulator[i].get();
            }


        } finally {
            pool.shutdown(); // Always shut down the pool
        }
        if (updateMsg != null && updateMsg.getId() > 0) channel.delete(updateMsg.getId());

        StringBuilder footer = new StringBuilder();

        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        IMessageBuilder msg = channel.create();
        sheet.attach(msg, "deposits");

        if (!noEscrowSheet) {
            Map.Entry<SpreadSheet, double[]> pair = escrowSheet(db, nations, null);
            if (!ResourceType.isZero(pair.getValue())) {
                SpreadSheet escrowSheet = pair.getKey();
                // attach sheet
                escrowSheet.updateClearCurrentTab();
                escrowSheet.updateWrite();
                escrowSheet.attach(msg, "escrow");

                double[] escrowTotal = pair.getValue();
                aaTotalPositive = ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, aaTotalPositive, escrowTotal);
                aaTotalNet = ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, aaTotalNet, escrowTotal);
            } else {
                noEscrowSheet = true;
            }
        }

        footer.append(ResourceType.resourcesToFancyString(aaTotalPositive, "Nation Deposits (" + nations.size() + " nations)"));

        footer.append("\n\nTo adjust member balances: " + CM.deposits.add.cmd.toSlashMention());
        footer.append("\nTo reset member balances: " + CM.deposits.reset.cmd.toSlashMention());

        String type = "";
        OffshoreInstance offshore = db.getOffshore();
        double[] aaDeposits;
        if (offshore != null && offshore.getGuildDB() != db) {
            type = "offshored";
            try {
                aaDeposits = offshore.getDeposits(db);
            } catch (RuntimeException e) {
                aaDeposits = null;
                footer.append("Failed to check offshore balance (ensure you api key can view stockpile): " + e.getMessage());
            }
        } else if (db.isValidAlliance()){
            type = "bank stockpile";
            aaDeposits = ResourceType.resourcesToArray(db.getAllianceList().getStockpile());
        } else aaDeposits = null;
        if (aaDeposits != null) {
            if (ResourceType.convertedTotal(aaDeposits) > 0) {
                for (int i = 0; i < aaDeposits.length; i++) {
                    aaTotalNet[i] = aaDeposits[i] - aaTotalNet[i];
                    aaTotalPositive[i] = aaDeposits[i] - aaTotalPositive[i];
                }
                String natDepTypes = noEscrowSheet ? "balances" : "balances (with escrow)";
                footer.append("\n**Total " + type + "- nation " + natDepTypes + " (without negative balances)**:  Worth: $" + MathMan.format(ResourceType.convertedTotal(aaTotalPositive)) + "\n`" + ResourceType.toString(aaTotalPositive) + "`");
                footer.append("\n**Total " + type + "- nation " + natDepTypes + "**:  Worth: $" + MathMan.format(ResourceType.convertedTotal(aaTotalNet)) + "\n`" + ResourceType.toString(aaTotalNet) + "`");
            } else {
                footer.append("\n**No funds are currently " + type + "**");
            }
        }

        msg.embed("Nation Deposits (With Alliance)", footer.toString())
                .send();
        return null;
    }

    @Command(desc = "Set the withdrawal limit (per interval) of a banker", aliases = {"setTransferLimit", "setWithdrawLimit", "setWithdrawalLimit", "setBankLimit"})
    @RolePermission(Roles.ADMIN)
    public String setTransferLimit(@Me GuildDB db, Set<DBNation> nations, double limit) {
        db.getOrThrow(GuildKey.BANKER_WITHDRAW_LIMIT); // requires to be set

        StringBuilder response = new StringBuilder();
        for (DBNation nation : nations) {
            db.getHandler().setWithdrawLimit(nation.getNation_id(), limit);
            response.append("Set withdraw limit of: " + nation.getUrl() + " to $" + MathMan.format(limit) + "\n");
        }
        response.append("Done!");
        return response.toString();
    }

    @Command(desc = "Set nation's internal taxrate\n" +
            "See also: `{prefix}nation set taxbracket` and `{prefix}settings key:TAX_BASE`")
    @RolePermission(value = Roles.ECON)
    @IsAlliance
    public String setInternalTaxRate(@Me GuildDB db, Set<DBNation> nations, TaxRate taxRate) {
        if (taxRate.money < -1 || taxRate.money > 100 || taxRate.resources < -1 || taxRate.resources > 100) throw new IllegalArgumentException("Invalid taxrate: " + taxRate);

        AllianceList aa = db.getAllianceList();
        if (aa == null || aa.isEmpty()) throw new IllegalArgumentException("This guild is not registered to an alliance");

        StringBuilder response = new StringBuilder();

        for (DBNation nation : nations) {
            if (!aa.contains(nation.getAlliance_id())) throw new IllegalArgumentException("Nation: " + nation.getUrl() + " is not in alliances: " + StringMan.getString(aa.getIds()));
            if (nation.getPosition() <= 1) throw new IllegalArgumentException("Nation: " + nation.getUrl() + " is not a member");
            db.setMeta(nation.getNation_id(), NationMeta.TAX_RATE, new byte[]{(byte) taxRate.money, (byte) taxRate.resources});
            response.append("Set " + nation.getUrl() + " internal taxrate to " + taxRate + "\n");
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

    @Command(desc = "Get a sheet of in-game transfers for nations", viewable = true)
    @RolePermission(value = Roles.MEMBER, onlyInGuildAlliance = true)
    public String getIngameNationTransfers(@Me IMessageIO channel, @Me @Default User author, @Me @Default GuildDB db, @AllowDeleted Set<NationOrAlliance> senders, @AllowDeleted Set<NationOrAlliance> receivers,
                                           @Arg("Only transfers after timeframe") @Default("%epoch%") @Timestamp long start_time,
                                           @Switch("e") @Timestamp Long end_time,
                                           @Switch("s") SpreadSheet sheet) throws IOException, GeneralSecurityException {
        if (end_time == null) end_time = Long.MAX_VALUE;
        if (sheet == null) sheet = SpreadSheet.create(db, SheetKey.BANK_TRANSACTION_SHEET);
        Set<Integer> hasAdmin = new IntOpenHashSet();
        boolean globalAdmin = Roles.ECON_STAFF.hasOnRoot(author);
        if (author != null && db != null) {
            hasAdmin.addAll(Roles.ECON_STAFF.getAllianceList(author, db).getIds());
        }

        Set<Long> senderIds = senders.stream().map(NationOrAllianceOrGuild::getIdLong).collect(Collectors.toSet());
        Set<Long> receiverIds = receivers.stream().map(NationOrAllianceOrGuild::getIdLong).collect(Collectors.toSet());
        List<Transaction2> transactions = Locutus.imp().getBankDB().getTransactionsByBySenderOrReceiver(senderIds, receiverIds, start_time, end_time);
        transactions.removeIf(tx -> {
            NationOrAllianceOrGuild sender = tx.getSenderObj();
            NationOrAllianceOrGuild receiver = tx.getReceiverObj();
            if (sender.isAlliance() && receiver.isAlliance() && !globalAdmin && !hasAdmin.contains(sender.getId()) && !hasAdmin.contains(receiver.getId())) {
                return true;
            }
            return !senders.contains(sender) || !receivers.contains(receiver);
        });
        sheet.addTransactionsList(channel, transactions, true);
        return null;
    }

    @Command(desc = "Get a sheet of ingame transfers for nations, filtered by the sender", viewable = true)
    @RolePermission(value = Roles.MEMBER, onlyInGuildAlliance = true)
    public String IngameNationTransfersBySender(@Me IMessageIO channel, @Me @Default GuildDB db, @Me @Default User author,
    Set<NationOrAlliance> senders, @Default("%epoch%") @Timestamp long timeframe, @Switch("s") SpreadSheet sheet) throws IOException, GeneralSecurityException {
        if (sheet == null) sheet = SpreadSheet.create(db, SheetKey.BANK_TRANSACTION_SHEET);
        Set<Long> senderIds = senders.stream().map(NationOrAllianceOrGuild::getIdLong).collect(Collectors.toSet());
        Set<Integer> hasAdmin = new IntOpenHashSet();
        boolean globalAdmin = Roles.ECON_STAFF.hasOnRoot(author);
        if (author != null && db != null) {
            hasAdmin.addAll(Roles.ECON_STAFF.getAllianceList(author, db).getIds());
        }

        List<Transaction2> transactions = Locutus.imp().getBankDB().getTransactionsByBySender(senderIds, timeframe);
        transactions.removeIf(tx -> {
            NationOrAllianceOrGuild sender = tx.getSenderObj();
            if (!senders.contains(sender)) return true;
            NationOrAllianceOrGuild receiver = tx.getReceiverObj();
            if (sender.isAlliance() && receiver.isAlliance() && !globalAdmin && !hasAdmin.contains(sender.getId()) && !hasAdmin.contains(receiver.getId())) {
                return true;
            }
            return false;
        });
        sheet.addTransactionsList(channel, transactions, true);
        return null;
    }

    @Command(desc = "Get a sheet of ingame transfers for nations, filtered by the receiver", groups = {
            "Optional: Specify timeframe"
    }, viewable = true)
    @RolePermission(value = Roles.MEMBER, onlyInGuildAlliance = true)
    public String IngameNationTransfersByReceiver(@Me IMessageIO channel, @Me @Default GuildDB db, @Me @Default User author,
                                                  Set<NationOrAlliance> receivers, @Arg(value = "Only list transfers after this time", group = 0)
                                                      @Timestamp @Default Long startTime,
                                                  @Arg(value = "Only list transfers before this time", group = 0)
                                                      @Timestamp @Default Long endTime, @Switch("s") SpreadSheet sheet) throws IOException, GeneralSecurityException {
        if (sheet == null) sheet = SpreadSheet.create(db, SheetKey.BANK_TRANSACTION_SHEET);
        Set<Integer> hasAdmin = new IntOpenHashSet();
        boolean globalAdmin = Roles.ECON_STAFF.hasOnRoot(author);
        if (author != null && db != null) {
            hasAdmin.addAll(Roles.ECON_STAFF.getAllianceList(author, db).getIds());
        }

        Set<Long> receiverIds = receivers.stream().map(NationOrAllianceOrGuild::getIdLong).collect(Collectors.toSet());
        if (startTime == null) startTime = 0L;
        if (endTime == null) endTime = Long.MAX_VALUE;
        List<Transaction2> transactions = Locutus.imp().getBankDB().getTransactionsByByReceiver(receiverIds, startTime, endTime);
        transactions.removeIf(tx -> {
            NationOrAllianceOrGuild receiver = tx.getReceiverObj();
            if (!receivers.contains(receiver)) return true;
            NationOrAllianceOrGuild sender = tx.getSenderObj();
            if (sender.isAlliance() && receiver.isAlliance() && !globalAdmin && !hasAdmin.contains(sender.getId()) && !hasAdmin.contains(receiver.getId())) {
                return true;
            }
            return false;
        });
        sheet.addTransactionsList(channel, transactions, true);
        return null;
    }

    @Command(desc = "Adjust nation's holdings by converting negative resource values of a specific note to a different resource or money",
    groups = {
            "Optional: Resources to convert",
            "Optional (max 1): Specify the deposit notes to convert",
            "Conversion Options",
    },
    groupDescs = {
            "If no resources are specified, all negative resources are converted to money",
            "Defaults to all types, except grants"
    })
    @RolePermission(value = {Roles.ECON, Roles.RESOURCE_CONVERSION}, any = true)
    public String convertDeposits(@Me IMessageIO channel, @Me GuildDB db, @Me User user, @Me DBNation me, @Me JSONObject command,
                                          Set<DBNation> nations,
                                          RssConvertMode mode,

                                          @Arg(value = "The resources to convert", group = 0)
                                          @Default("manu,raws,food") Set<ResourceType> from_resources,

                                          @Arg(value = "What resource to convert to\n" +
                                                  "Conversion uses weekly market average prices", group = 0)
                                          @Default("money") ResourceType to_resource,


                                          @Arg(value = "If grants are also converted", group = 1)
                                          @Switch("g") boolean includeGrants,
                                          @Arg(value = "Convert transfers of this note category", group = 1)
                                          @Switch("t") DepositType.DepositTypeInfo depositType,

                                          @Arg(value = "What factor to multiple the converted resources by\n" +
                                                  "e.g. Use a value below 1.0 to incur a fee", group = 2) @Switch("f")
                                              Double conversionFactor,
                                          @Arg(value = "The transfer note to use for the adjustment", group = 2)
                                              @Default() @Switch("n") String note,
                                          @Switch("s") SpreadSheet sheet,
                                            @Switch("force") boolean force
                                              ) throws IOException, GeneralSecurityException {
        if (nations.size() > 500) return "Too many nations > 500";
        boolean isMe = (nations.size() == 1 && me.getId() == nations.iterator().next().getId());
        if ((nations.size() > 1 && !Roles.ECON.has(user, db.getGuild())) || !isMe) {
            for (DBNation nation : nations) {
                if (!Roles.ECON.has(user, db.getGuild(), nation.getAlliance_id())) {
                    throw new IllegalArgumentException("Missing (alliance: " + nation.getAllianceUrlMarkup() + "): " + Roles.ECON.toDiscordRoleNameElseInstructions(db.getGuild()));
                }
            }
        }
        if (isMe && !Roles.ECON.has(user, db.getGuild()) && !Roles.ECON.has(user, db.getGuild(), me.getAlliance_id())) {
            String msg = """
            The following arguments are not allowed for personal conversion:
            - convertTo
            - includeGrants
            - depositType
            - conversionFactor
            - note
            """;
            boolean disallowed = false;
            if (to_resource != null) {
                msg = msg.replace("convertTo", "**convertTo**");
                disallowed = true;
            }
            if (includeGrants) {
                msg = msg.replace("includeGrants", "**includeGrants**");
                disallowed = true;
            }
            if (depositType != null) {
                msg = msg.replace("depositType", "**depositType**");
                disallowed = true;
            }
            if (conversionFactor != null) {
                msg = msg.replace("conversionFactor", "**conversionFactor**");
                disallowed = true;
            }
            if (note != null) {
                msg = msg.replace("note", "**note**");
                disallowed = true;
            }
            if (disallowed) {
                throw new IllegalArgumentException("You cannot use the following arguments for personal conversion: " + msg);
            }
        }

        Function<ResourceType, Double> conversionRate;
        if (conversionFactor != null || !isMe) {
            conversionRate = _ -> 1d;
        } else if (isMe) {
            conversionRate = db.getConversionRate(me);
        } else {
            conversionRate = null;
        }

        Map<NationOrAlliance, double[]> toAddMap = new LinkedHashMap<>();
        double[] total = ResourceType.getBuffer();

        if (note == null) {
            if (depositType != null) {
                note = depositType.toString();
            } else {
                note = "#deposit";
            }
        }

        Map<ResourceType, Double> rates = new Object2DoubleLinkedOpenHashMap<>();

        String title = "Addbalance";
        StringBuilder body = new StringBuilder();

        Object lock = isMe && force ? OffshoreInstance.BANK_LOCK : new Object();
        synchronized (lock) {
            for (DBNation nation : nations) {
                Function<ResourceType, Double> rateFinal = conversionRate == null ? db.getConversionRate(nation) : conversionRate;

                double[] depo;
                if (depositType != null) {
                    Map<DepositType, double[]> depoByCategory = nation.getDeposits(db, null, true, true, -1, 0L, false);
                    depo = depoByCategory.get(depositType.type);
                    if (depo == null) continue;
                } else {
                    depo = nation.getNetDeposits(db, null, true, true, includeGrants, -1, 0L, false);
                }
                double[] amtAddArr = ResourceType.getBuffer();
                boolean add = false;
                for (ResourceType type : ResourceType.values) {
                    if (!from_resources.contains(type) || type == to_resource) continue;
                    double currAmt = depo[type.ordinal()];
                    double addAmt = 0;
                    if (mode == RssConvertMode.NEGATIVE) {
                        if (currAmt < -0.01) {
                            add = true;
                            addAmt = -currAmt;

                        }
                    } else {
                        if (currAmt > 0.01) {
                            add = true;
                            addAmt = -currAmt;
                        }
                    }
                    if (addAmt != 0) {
                        double from_rate = type == MONEY ? 1 : conversionRate.apply(type);
                        double to_rate = to_resource == MONEY ? 1 : rateFinal.apply(to_resource);

                        if (from_rate == 0 || to_rate == 0) {
                            continue;
                        }

                        rates.put(type, from_rate);
                        rates.put(to_resource, to_rate);

                        double perUnitFrom = ResourceType.convertedTotal(type, 1);
                        double perUnitTo = ResourceType.convertedTotal(to_resource, 1);

                        double newAmt;
                        if (addAmt > 0) {
                            newAmt = (addAmt * perUnitFrom) / (perUnitTo * from_rate * to_rate);
                        } else {
                            newAmt = (addAmt * from_rate * to_rate * perUnitFrom) / (perUnitTo);
                        }

                        amtAddArr[type.ordinal()] = addAmt;
                        amtAddArr[to_resource.ordinal()] -= newAmt;
                    }
                }
                if (add) {
                    total = ResourceType.add(total, amtAddArr);
                    toAddMap.put(nation, amtAddArr);
                }
            }

            if (toAddMap.isEmpty()) return "No deposits need to be adjusted (" + nations.size() + " nations checked)";

            body.append("Total: \n`" + ResourceType.toString(total) + "`\nWorth: ~$" + MathMan.format(ResourceType.convertedTotal(total)));
            if (isMe) {
                body.append("\n").append("**Resource Rates**:\n")
                        .append("-# Resource amounts are multiplied by these values before converting based on weekly average price\n")
                        .append("`" + ResourceType.toString(rates) + "`\n");
            }

            body.append("**Total Conversion Amount:**\n")
                    .append("`" + ResourceType.toString(total) + "`\n");

            if (isMe) {
                if (force) {
                    AddBalanceBuilder builder = db.addBalanceBuilder();
                    builder.add(me, total, note);
                    return builder.buildAndSend(me, true);
                }
            }
        }

        if (isMe) {
            channel.create().confirmation(title, body.toString(), command).send();
            return null;
        }


        if (sheet == null) sheet = SpreadSheet.create(db, SheetKey.TRANSFER_SHEET);

        TransferSheet txSheet = new TransferSheet(sheet).write(toAddMap).build();

        CM.deposits.addSheet cmd = CM.deposits.addSheet.cmd.sheet(txSheet.getSheet().getURL()).note(note).force(force ? "true" : null);

        channel.create().embed(title, body.toString())
                .commandButton(cmd, "confirm")
                .send();
        return null;
    }

    @Command(desc = "Get a sheet of internal transfers for nations", groups = {
            "Optional: Specify timeframe"
    }, viewable = true)
    @RolePermission(value = Roles.ECON)
    public String getNationsInternalTransfers(@Me IMessageIO channel, @Me GuildDB db,
                                              @AllowDeleted Set<DBNation> nations,
                                              @Arg(value = "Only list transfers after this time", group = 0)
                                              @Timestamp @Default Long startTime,
                                              @Arg(value = "Only list transfers before this time", group = 0)
                                              @Timestamp @Default Long endTime,
                                              @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (startTime == null) startTime = 0L;
        if (endTime == null) endTime = Long.MAX_VALUE;
        if (sheet == null) sheet = SpreadSheet.create(db, SheetKey.BANK_TRANSACTION_SHEET);
        if (nations.size() > 1000) return "Too many nations >1000";

        List<Transaction2> transactions = new ArrayList<>();
        for (DBNation nation : nations) {
            List<Transaction2> offsets = db.getDepositOffsetTransactions(nation.getNation_id());
            transactions.addAll(offsets);
        }
        Long finalStartTime = startTime;
        Long finalEndTime = endTime;
        transactions.removeIf(f -> f.tx_datetime < finalStartTime || f.tx_datetime > finalEndTime);

        sheet.addTransactionsList(channel, transactions, true);
        return null;
    }

    @Command(desc = "Get a sheet of transfers", viewable = true)
    @RolePermission(value = Roles.MEMBER, onlyInGuildAlliance = true)
    public String getIngameTransactions(@Me IMessageIO channel, @Me @Default GuildDB db, @Me @Default User author,
                                        @AllowDeleted @Default NationOrAlliance sender,
                                        @AllowDeleted @Default NationOrAlliance receiver,
                                        @AllowDeleted @Default NationOrAlliance banker,
                                        @Default("%epoch%") @Timestamp long timeframe, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (sheet == null) sheet = SpreadSheet.create(db, SheetKey.BANK_TRANSACTION_SHEET);
        List<Transaction2> transactions = Locutus.imp().getBankDB().getAllTransactions(sender, receiver, banker, timeframe, null);
        Set<Integer> hasAdmin = new IntOpenHashSet();
        boolean globalAdmin = Roles.ECON_STAFF.hasOnRoot(author);
        if (author != null && db != null) {
            hasAdmin.addAll(Roles.ECON_STAFF.getAllianceList(author, db).getIds());
        }
        transactions.removeIf(f -> {
            NationOrAllianceOrGuild txSender = f.getSenderObj();
            NationOrAllianceOrGuild txReceiver = f.getReceiverObj();
            if (txSender.isAlliance() && txReceiver.isAlliance() && !globalAdmin && !hasAdmin.contains(txSender.getId()) && !hasAdmin.contains(txReceiver.getId())) {
                return true;
            }
            return false;
        });

        if (transactions.size() > 10000) return "Timeframe is too large, please use a shorter period";

        sheet.addTransactionsList(channel, transactions, true);
        return null;
    }

    public static List<Transaction2> getRecords(GuildDB db, User user, boolean includeTaxes, boolean useTaxBase, boolean useOffset, long timeframe, NationOrAllianceOrGuildOrTaxid nationOrAllianceOrGuild, boolean onlyOffshoreTransfers) {
        List<Transaction2> transactions = new ArrayList<>();
        if (nationOrAllianceOrGuild.isNation()) {
            DBNation nation = nationOrAllianceOrGuild.asNation();
            List<Map.Entry<Integer, Transaction2>> natTrans = nation.getTransactions(db, null, includeTaxes, useTaxBase, useOffset, 0, timeframe, false);
            for (Map.Entry<Integer, Transaction2> entry : natTrans) {
                transactions.add(entry.getValue());
            }
        } else if (nationOrAllianceOrGuild.isAlliance()) {
            DBAlliance alliance = nationOrAllianceOrGuild.asAlliance();
            // if this alliance - get the transactions in the offshore
            Set<Integer> aaIds = db.getAllianceIds();
            OffshoreInstance offshore = db.getOffshore();
            if (aaIds.contains(alliance.getAlliance_id()) && offshore != null) {
                transactions.addAll(offshore.getTransactionsAA(aaIds, true));
            } else if (!aaIds.isEmpty() && db.getOffshore() != null && aaIds.contains(db.getOffshore().getAllianceId()) && offshore != null) {
                transactions.addAll(offshore.getTransactionsAA(alliance.getAlliance_id(), true));
            } else {
                List<Transaction2> txToAdd = (db.getTransactionsById(alliance.getAlliance_id(), 2));
                Set<Integer> hasAdmin = new IntOpenHashSet();
                boolean globalAdmin = Roles.ECON_STAFF.hasOnRoot(user);
                if (user != null && db != null) {
                    hasAdmin.addAll(Roles.ECON_STAFF.getAllianceList(user, db).getIds());
                }
                if (!aaIds.contains(alliance.getAlliance_id()) && !hasAdmin.contains(alliance.getAlliance_id())) {
                    txToAdd.removeIf(f -> {
                        NationOrAllianceOrGuild sender = f.getSenderObj();
                        NationOrAllianceOrGuild receiver = f.getReceiverObj();
                        if (sender.isAlliance() && receiver.isAlliance() && !globalAdmin && !hasAdmin.contains(sender.getId()) && !hasAdmin.contains(receiver.getId())) {
                            return true;
                        }
                        return false;
                    });
                }
                transactions.addAll(txToAdd);
            }

            if (onlyOffshoreTransfers) {
                if (offshore == null) throw new IllegalArgumentException("This alliance does not have an offshore account");
                Set<Integer> offshoreAAIds = db.getOffshore().getOffshoreAAIds();
                transactions.removeIf(f -> f.sender_type == 1 || f.receiver_type == 1);
                transactions.removeIf(f -> f.tx_id != -1 && f.sender_id != 0 && f.receiver_id != 0 && !offshoreAAIds.contains((int) f.sender_id) && !offshoreAAIds.contains((int) f.receiver_id));
            }

        } else if (nationOrAllianceOrGuild.isGuild()) {
            GuildDB otherDB = nationOrAllianceOrGuild.asGuild();

            // if this guild - get the transactions in the offshore
            Map.Entry<GuildDB, Integer> offshoreEntry = otherDB.getOffshoreDB();
            GuildDB offshoreDb = offshoreEntry.getKey();
            if (otherDB.getIdLong() == db.getIdLong() || (offshoreDb == db && onlyOffshoreTransfers)) {
                OffshoreInstance offshore = db.getOffshore();
                transactions.addAll(offshore.getTransactionsGuild(otherDB.getIdLong(), true));
            } else {
                transactions.addAll(db.getTransactionsById(otherDB.getGuild().getIdLong(), 3));
            }
        } else if (nationOrAllianceOrGuild.isTaxid()) {
            throw new IllegalArgumentException("Not implemented");
        }
        return transactions;
    }

    @Command(desc = "Get a sheet of a nation or alliances transactions (excluding taxes)", groups = {
        "Optional: Specify timeframe",
        "Display Options",
    }, viewable = true)
    @RolePermission(value = Roles.MEMBER)
    public String transactions(@Me IMessageIO channel, @Me GuildDB db, @Me @Default User user, @Me DBNation me,
                               @AllowDeleted NationOrAllianceOrGuildOrTaxid nationOrAllianceOrGuild,
                               @Arg(value = "Only show transactions after this time", group = 0)
                               @Default("%epoch%") @Timestamp long timeframe,
                               @Arg(value = "Do NOT include the tax record resources below the internal tax rate\n" +
                                       "Default: False", group = 1)
                               @Default("false") boolean useTaxBase,
                               @Arg(value = "Include balance offset records (i.e. from commands)\n" +
                                       "Default: True", group = 1)
                               @Default("true") boolean useOffset,
                               @Switch("s") SpreadSheet sheet,
                               @Switch("o") boolean onlyOffshoreTransfers) throws GeneralSecurityException, IOException {
        if ((!nationOrAllianceOrGuild.isNation() || nationOrAllianceOrGuild.getId() != me.getId()) && !Roles.ECON_STAFF.has(user, db.getGuild())) {
            throw new IllegalArgumentException("You can only view your own transactions. Missing: " + Roles.ECON_STAFF.toDiscordRoleNameElseInstructions(db.getGuild()));
        }
        if (sheet == null) sheet = SpreadSheet.create(db, SheetKey.BANK_TRANSACTION_SHEET);
        if (onlyOffshoreTransfers && nationOrAllianceOrGuild.isNation()) return "Only Alliance/Guilds can have an offshore account";

        List<Transaction2> transactions = getRecords(db, user, true, useTaxBase, useOffset, timeframe, nationOrAllianceOrGuild, onlyOffshoreTransfers);
        sheet.addTransactionsList(channel, transactions, true);
        return null;
    }

    public static Map<UUID, Map<NationOrAlliance, Map<ResourceType, Double>>> APPROVED_BULK_TRANSFER = new ConcurrentHashMap<>();



    @Command(desc = """
            Send multiple transfers to nations/alliances according to a sheet
            The transfer sheet columns must be `nations` (which has the nations or alliance name/id/url)
            and then there must be a column named for each resource type you wish to transfer
            OR use a column called `resources` which has a resource list (e.g. a json object of the resources)""")
    @RolePermission(value = {Roles.ECON_WITHDRAW_SELF, Roles.ECON}, alliance = true, any = true)
    public static String transferBulk(@Me IMessageIO io, @Me JSONObject command, @Me User user, @Me DBNation me, @Me GuildDB db, TransferSheet sheet, DepositType.DepositTypeInfo depositType,

                                      @Arg("The nation account to deduct from") @Switch("n") DBNation depositsAccount,
                                      @Arg("The alliance bank to send from\nDefaults to the offshore") @Switch("a") DBAlliance useAllianceBank,
                                      @Arg("The alliance account to deduct from\nAlliance must be registered to this guild\nDefaults to all the alliances of this guild") @Switch("o") DBAlliance useOffshoreAccount,
                                      @Arg("The tax account to deduct from") @Switch("t") TaxBracket taxAccount,
                                      @Arg("Deduct from the receiver's tax bracket account") @Switch("ta") boolean existingTaxAccount,
                                      @Arg("Have the transfer ignored from nation holdings after a timeframe") @Switch("e") @Timediff Long expire,
                                      @Arg("Have the transfer decrease linearly over a timeframe") @Switch("d") @Timediff Long decay,
                                      @Switch("m") boolean convertToMoney,
                                      @Arg("The mode for escrowing funds (e.g. if the receiver is blockaded)\nDefaults to never") @Switch("em") EscrowMode escrow_mode,
                                      @Switch("b") boolean bypassChecks,
                                      @Switch("f") boolean force,
                                      @Switch("k") UUID key) throws IOException {
        return transferBulkWithErrors(io, command, user, me, db, sheet, depositType, depositsAccount, useAllianceBank, useOffshoreAccount, taxAccount, existingTaxAccount, expire, decay, convertToMoney, escrow_mode, bypassChecks, force, key, new HashMap<>());
    }


    public static String transferBulkWithErrors(@Me IMessageIO io, @Me JSONObject command, @Me User user, @Me DBNation me, @Me GuildDB db, TransferSheet sheet, DepositType.DepositTypeInfo depositType,
                                        @Arg("The nation account to deduct from") @Switch("n") DBNation depositsAccount,
                                        @Arg("The alliance bank to send from\nDefaults to the offshore") @Switch("a") DBAlliance useAllianceBank,
                                        @Arg("The alliance account to deduct from\nAlliance must be registered to this guild\nDefaults to all the alliances of this guild") @Switch("o") DBAlliance useOffshoreAccount,
                                        @Arg("The tax account to deduct from") @Switch("t") TaxBracket taxAccount,
                                        @Arg("Deduct from the receiver's tax bracket account") @Switch("ta") boolean existingTaxAccount,
                                                @Arg("Have the transfer ignored from nation holdings after a timeframe") @Switch("e") @Timediff Long expire,
                                                @Arg("Have the transfer decrease linearly to zero for balances over a timeframe") @Switch("d") @Timediff Long decay,
                                      @Switch("m") boolean convertToMoney,
                                                @Arg("The mode for escrowing funds (e.g. if the receiver is blockaded)\nDefaults to never") @Switch("em") EscrowMode escrow_mode,
                                      @Switch("b") boolean bypassChecks,
                                      @Switch("f") boolean force,
                                      @Switch("k") UUID key,
                                                Map<NationOrAlliance, TransferResult> errors) throws IOException {
        if (existingTaxAccount) {
            if (taxAccount != null) throw new IllegalArgumentException("You can't specify both `tax_id` and `existingTaxAccount`");
        }
        double totalVal = 0;

        int nations = 0;
        int alliances = 0;

        Set<Integer> memberAlliances = new IntOpenHashSet();
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
                    totalVal += ResourceType.convertedTotal(type, amt);
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
            IMessageBuilder msg = io.create();
            desc.append("Total: `" + ResourceType.toString(totalRss) + "`\n");
            desc.append("Note: `" + depositType + "`\n\n");
            if (escrow_mode != null && escrow_mode != EscrowMode.NEVER) {
                desc.append("Escrow Mode: `" + escrow_mode + "`\n");
            }
            sheet.getSheet().attach(msg, "transfers", desc, true, desc.length());

            if (!errors.isEmpty()) {
                desc.append("**Warnings**: `" + TransferResult.count(errors.values()) + "`\n");
                msg = msg.file("errors.csv", TransferResult.toFileString(errors.values()));
            }

            key = UUID.randomUUID();
            APPROVED_BULK_TRANSFER.put(key, transfers);
            String commandStr = command.put("force", "true").put("key", key).toString();
            msg.embed(title, desc.toString())
                    .commandButton(commandStr, "Confirm")
                    .send();
            return null;
        }
        if (key != null) {
            Map<NationOrAlliance, Map<ResourceType, Double>> approvedAmounts = APPROVED_BULK_TRANSFER.get(key);
            if (approvedAmounts == null) return "No amount has been approved for transfer. Please try again";
            Set<NationOrAlliance> keys = new HashSet<>();
            keys.addAll(approvedAmounts.keySet());
            keys.addAll(transfers.keySet());
            for (NationOrAlliance nationOrAlliance : keys) {
                Map<ResourceType, Double> amtA = approvedAmounts.getOrDefault(nationOrAlliance, Collections.emptyMap());
                Map<ResourceType, Double> amtB = transfers.getOrDefault(nationOrAlliance, Collections.emptyMap());
                if (!ResourceType.equals(amtA, amtB)) {
                    return "The confirmed amount does not match (For " + nationOrAlliance.getMarkdownUrl() + " `" + ResourceType.toString(amtA) + "` != `" + ResourceType.toString(amtB) + "`)\n" +
                            "Please try again, and avoid confirming multiple transfers at once";
                }
            }
        }

        OffshoreInstance offshore;
        if (useAllianceBank != null) {
            if (!db.isAllianceId(useAllianceBank.getAlliance_id())) {
                return "This guild is not registered to the alliance `" + useAllianceBank.getName() + "`";
            }
            offshore = useAllianceBank.getBank();
        } else {
            offshore = db.getOffshore();
        }
        if (offshore == null) {
            return "No offshore is setup. See " + CM.offshore.add.cmd.toSlashMention();
        }
        if (depositsAccount == null && Roles.ECON.getAllianceList(user, db).isEmpty()) {
            depositsAccount = me;
        }

        Map<ResourceType, Double> totalSent = new Object2DoubleOpenHashMap<>();

        Map<NationOrAlliance, String> notes = sheet.getNotes();
        StringBuilder output = new StringBuilder();
        for (Map.Entry<NationOrAlliance, Map<ResourceType, Double>> entry : transfers.entrySet()) {
            NationOrAlliance receiver = entry.getKey();
            double[] amount = ResourceType.resourcesToArray(entry.getValue());

            TransferResult result = null;
            TaxBracket taxAccountFinal = taxAccount;
            if (existingTaxAccount) {
                if (!receiver.isNation()) {
                    result = new TransferResult(OffshoreInstance.TransferStatus.INVALID_DESTINATION, receiver, amount, depositType.toString()).addMessage("Cannot use `existingTaxAccount` for transfers to alliances");
                } else {
                    taxAccountFinal = receiver.asNation().getTaxBracket();
                }
            }

            DepositType.DepositTypeInfo depositTypeFinal = depositType.clone();
            String note = notes.get(receiver);
            if (note != null) {
                Map<DepositType, Object> parsed = Transaction2.parseTransferHashNotes(note, System.currentTimeMillis());
                depositTypeFinal.applyClassifiers(parsed);
            }

            if (result == null) {
                try {
                    result = offshore.transferFromNationAccountWithRoleChecks(
                            me,
                            user,
                            depositsAccount,
                            useOffshoreAccount,
                            taxAccountFinal,
                            db,
                            io.getIdLong(),
                            receiver,
                            amount,
                            depositTypeFinal,
                            expire,
                            decay,
                            null,
                            convertToMoney,
                            escrow_mode,
                            false,
                            bypassChecks
                    );
                } catch (IllegalArgumentException | IOException e) {
                    result = new TransferResult(OffshoreInstance.TransferStatus.OTHER, receiver, amount, depositType.toString()).addMessage(e.getMessage());
                }
            }

            output.append(receiver.getUrl() + "\t" + receiver.isAlliance() + "\t" + StringMan.getString(amount) + "\t" + result.getStatus() + "\t" + "\"" + result.getMessageJoined(false).replace("\n", " ") + "\"");
            output.append("\n");
            if (result.getStatus().isSuccess()) {
                totalSent = ResourceType.add(totalSent, ResourceType.resourcesToMap(amount));
                io.create().embed(result.toTitleString(), result.toEmbedString()).send();
            } else {
                errors.put(receiver, result);
            }
        }
        IMessageBuilder msg = io.create();
        if (!errors.isEmpty()) {
            for (Map.Entry<NationOrAlliance, TransferResult> entry : errors.entrySet()) {
                NationOrAlliance receiver = entry.getKey();
                TransferResult result = entry.getValue();
                output.append(receiver.getUrl() + "\t" + receiver.isAlliance() + result.getStatus() + "\t" + "\"" + result.getMessageJoined(true) + "\"");
                output.append("\n");
            }
            msg.append("Summary: `" + TransferResult.count(new ArrayList<>(errors.values())) + "`\n");
        }

        msg.file("transfer-results.csv", output.toString())
                .append("Done!\nTotal sent: `" + ResourceType.toString(totalSent) + "` worth: ~$" + MathMan.format(convertedTotal(totalSent)));

        msg.send();
        return null;
    }

    @Command(desc = """
            Unlock transfers for an alliance or guild using this guild as an offshore
            Accounts are automatically locked if there is an error accessing the api, a game captcha, or if an admin of the account is banned in-game
            Only locks from game bans persist across restarts""")
    @RolePermission(value = Roles.ADMIN)
    public String unlockTransfers(@Me GuildDB db, @Default NationOrAllianceOrGuild nationOrAllianceOrGuild, @Switch("a") boolean unlockAll) {
        OffshoreInstance offshore = db.getOffshore();
        if (offshore == null) return "No offshore is set";
        if (unlockAll) {
            OffshoreInstance.FROZEN_ESCROW.clear();
            offshore.disabledNations.clear();
            offshore.disabledGuilds.clear();
            return "Done!";
        } else if (nationOrAllianceOrGuild == null) {
            return "Please specify `nationOrAllianceOrGuild`";
        }
        NationOrAllianceOrGuild alliance = nationOrAllianceOrGuild;
        if (alliance.isNation()) {
            return "You can only unlock transfers for an alliance or guild";
        }
        if (!alliance.isNation() && offshore.getGuildDB() != db) {
            if (!OffshoreInstance.FROZEN_ESCROW.containsKey(alliance.getId())) {
                return "The nation: " + alliance.getUrl() + " is not frozen";
            }
            OffshoreInstance.FROZEN_ESCROW.remove(alliance.getId());
            return "Removed the frozen escrow for " + alliance.getUrl();
        }
        Set<Long> coalition = offshore.getGuildDB().getCoalitionRaw(Coalition.FROZEN_FUNDS);
        if (alliance.isAlliance()) {
            if (coalition.contains((long) alliance.getAlliance_id())) return "Please use `!removecoalition FROZEN_FUNDS " +  alliance.getAlliance_id() + "`";
            GuildDB otherDb = alliance.asAlliance().getGuildDB();
            if (otherDb != null && coalition.contains(otherDb.getIdLong())) return "Please use `!removecoalition FROZEN_FUNDS " + otherDb.getIdLong() + "`";
        } else if (alliance.isGuild()) {
            if (coalition.contains((long) alliance.getIdLong())) return "Please use `!removecoalition FROZEN_FUNDS " +  alliance.getIdLong() + "`";
            for (int aaId : alliance.asGuild().getAllianceIds(true)) {
                if (coalition.contains((long) aaId))return "Please use `!removecoalition FROZEN_FUNDS " + aaId + "`";
            }
        } else if (alliance.isNation()){
            Long removed = offshore.disabledNations.remove(alliance.getId());
            if (removed == null) {
                return "No transfers are locked for " + alliance.getQualifiedId() + ". Valid options: " + offshore.disabledNations.keySet().stream().map(f -> PW.getMarkdownUrl(f, false));
            }
            return "Enabled transfers for " + alliance.getQualifiedId();
        }
        if (alliance.isGuild() || alliance.isAlliance()) {
            GuildDB aaDb = alliance.isGuild() ? alliance.asGuild() : alliance.asAlliance().getGuildDB();
            if (aaDb == null) {
                return "No guild found for " + alliance.getMarkdownUrl();
            }
            if (offshore.disabledGuilds.remove(aaDb.getIdLong()) == null) {
                String msg = "No transfers are locked for " + alliance.getQualifiedId() + ".";
                if (!offshore.disabledGuilds.isEmpty()) {
                    msg += " Valid options:\n- " + offshore.disabledGuilds.keySet().stream().map(f -> {
                        Guild guild = Locutus.imp().getDiscordApi().getGuildById(f);
                        return guild == null ? f + "" : guild.toString();
                    }).collect(Collectors.joining("\n- "));
                }
                return msg;
            }
        } else {
            return alliance + " must be a guild or alliance";
        }
        return "Done!";
    }

    @Command(desc = "Bulk set nation internal taxrates as configured in the guild setting: `REQUIRED_INTERNAL_TAXRATE`")
    @IsAlliance
    @RolePermission(Roles.ECON_STAFF)
    public String setNationInternalTaxRates(@Me GuildDB db, @Arg("The nations to set internal taxrates for\nIf not specified, all nations in the alliance will be used")
    @Default() Set<DBNation> nations, @Arg("Ping users if their rates are modified") @Switch("p") boolean ping) throws Exception {
        if (nations == null) {
            nations = db.getAllianceList().getNations();;
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

            String message = "- " + nation.getNation() + ": moved internal rates to `" + bracketInfo.getKey() + "` for reason: `" + bracketInfo.getValue() + "`";
            if (ping) {
                User user = nation.getUser();
                if (user != null) message += " | " + user.getAsMention();
            }
            messages.add(message);
        }

        messages.add("\n**About Internal Tax Rates**\n" +
                "- Internal tax rates are NOT ingame tax rates\n" +
                "- Internal rates determine what money%/resource% of ingame city taxes goes to the alliance\n" +
                "- The remainder is added to a nation's " + CM.deposits.check.cmd.toSlashMention());

        return StringMan.join(messages, "\n");
    }

    @Command(desc = "List the assigned taxrate if REQUIRED_TAX_BRACKET or REQUIRED_INTERNAL_TAXRATE are set\n" +
            "Note: this command does set nations brackets. See: `{prefix}tax setNationBracketAuto` and `{prefix}nation set taxinternalAuto` ", viewable = true)
    @IsAlliance
    @RolePermission(Roles.ECON_STAFF)
    public String listRequiredTaxRates(@Me IMessageIO io, @Me GuildDB db, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (sheet == null) sheet = SpreadSheet.create(db, SheetKey.TAX_BRACKET_SHEET);
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

        AllianceList alliances = db.getAllianceList();
        Map<Integer, TaxBracket> brackets = alliances.getTaxBrackets(TimeUnit.MINUTES.toMillis(5));

        Set<DBNation> nations = alliances.getNations();

        Map<NationFilter, Integer> requiredBrackets = db.getOrNull(GuildKey.REQUIRED_TAX_BRACKET);
        Map<NationFilter, TaxRate> requiredInternalRates = db.getOrNull(GuildKey.REQUIRED_INTERNAL_TAXRATE);

        if (requiredBrackets == null) requiredBrackets = Collections.emptyMap();
        if (requiredInternalRates == null) requiredInternalRates = Collections.emptyMap();
        requiredBrackets.keySet().forEach(NationFilter::recalculate);
        requiredInternalRates.keySet().forEach(NationFilter::recalculate);

        sheet.setHeader(header);

        for (DBNation nation : nations) {
            header.set(0, MarkupUtil.sheetUrl(nation.getNation(), PW.getUrl(nation.getNation_id(), false)));
            header.set(1, nation.getCities() + "");

            Map.Entry<NationFilter, Integer> requiredBracket = requiredBrackets.entrySet().stream().filter(f -> f.getKey().test(nation)).findFirst().orElse(null);
            Map.Entry<NationFilter, TaxRate> requiredInternal = requiredInternalRates.entrySet().stream().filter(f -> f.getKey().test(nation)).findFirst().orElse(null);

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

        sheet.updateClearCurrentTab();
        sheet.updateWrite();
        sheet.attach(io.create(), "tax_rates").send();
        return null;
    }


    @Command(desc = "Bulk set nation tax brackets as configured in the guild setting: `REQUIRED_TAX_BRACKET`")
    @IsAlliance
    @RolePermission(Roles.ECON_STAFF)
    public String setNationTaxBrackets(@Me GuildDB db, @Arg("The nations to set tax brackets for\nIf not specified, all nations in the alliance will be used")
                                       @Default() Set<DBNation> nations, @Arg("Ping users if their brackets are modified")
                                       @Switch("p") boolean ping) throws Exception {
        if (nations == null) {
            nations = db.getAllianceList().getNations();
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

            String message = "- " + nation.getNation() + ": moved city bracket to `" + bracketInfo.getKey() + "` for reason: `" + bracketInfo.getValue() + "`";
            if (ping) {
                User user = nation.getUser();
                if (user != null) message += " | " + user.getAsMention();
            }
            messages.add(message);
        }

        messages.add("\n**About Ingame City Tax Brackets**\n" +
                "- A tax bracket decides what % of city income (money/resources) goes to the alliance bank\n" +
                "- Funds from raiding, trading or daily login are never taxed\n" +
                "- Taxes bypass blockades\n" +
                "- Your internal tax rate will then determine what portion of city taxes go to your " + CM.deposits.check.cmd.toSlashMention());

        return StringMan.join(messages, "\n");
    }

    @Command(aliases = {"acceptTrades", "acceptTrade"}, desc = """
            Deposit your pending trades into your nation's holdings for this guild
            The receiver must be authenticated with the bot and have bank access in an alliance
            Only resources sold for $0 or food bought for cash are accepted""")
    @RolePermission(value = Roles.MEMBER)
    public String acceptTrades(@Me JSONObject command, @Me IMessageIO io, @Me User user, @Me GuildDB db, @Me DBNation me, DBNation receiver, @Default Map<ResourceType, Double> amount, @Switch("a") boolean useLogin, @Switch("f") boolean force) throws Exception {
        OffshoreInstance offshore = db.getOffshore();
        if (offshore == null) return "No offshore is set in this guild: <https://github.com/xdnw/locutus/wiki/banking>";

        GuildDB receiverDB = Locutus.imp().getGuildDBByAA(receiver.getAlliance_id());
        if (receiverDB == null) return "Receiver is not in a guild with locutus";
        if (!force && me.isBlockaded()) throw new IllegalArgumentException("Sender is blockaded");
        if (!force && receiver.isBlockaded()) throw new IllegalArgumentException("Receiver is blockaded");

        User receiverUser = receiver.getUser();
        if (receiverUser == null) return "Receiver is not verified";
        Member member = receiverDB.getGuild().getMember(receiverUser);
        if (receiver.active_m() > 1440) return "Receive is offline for >24 hours";
        if (!force && receiver.getNumWars() > 0 && (member == null || member.getOnlineStatus() != OnlineStatus.ONLINE)) {
            String title = "Receiver is not online on discord";
            StringBuilder body = new StringBuilder();
            body.append("**Receiver:** " + receiver.getNationUrlMarkup() + " | " + receiver.getAllianceUrlMarkup()).append("\n");
            int activeM = receiver.active_m();
            body.append("**Last active** (in-game): " + (activeM == 0 ? "Now" : TimeUtil.secToTime(TimeUnit.MINUTES, activeM))).append("\n");
            body.append("**Discord Status:** " + (member == null ? "No Discord" : member.getOnlineStatus())).append("\n");
            body.append("\n> In case there is a game captcha it is recommended to have the receiver online on discord to solve it so your funds can be safely deposited");
            io.create().confirmation(title, body.toString(), command).cancelButton().send();
            return null;
        }

        Map.Entry<double[], String> result;
        if (amount != null) {
            Double money = amount.get(ResourceType.MONEY);
            if (money != null && money < 100000) {
                throw new IllegalArgumentException("Minimum amount is $100,000");
            }
            result = receiver.tradeAndOffshoreDeposit(db, me, ResourceType.resourcesToArray(amount));
        } else if (!useLogin) {
            result = receiver.acceptAndOffshoreTrades(db, me);
        } else {
            Auth auth = receiver.getAuth(true);
            if (auth == null) return "Receiver is not authenticated with Locutus: " + CM.credentials.login.cmd.toSlashMention() + "\n" +
                    "Alternatively, set `useApi: True`";
            result = auth.acceptAndOffshoreTrades(db, me.getNation_id());
        }
        if (ResourceType.isZero(result.getKey())) {
            return "__**ERROR: No funds have been added to your account**__\n" +
                    result.getValue();
        } else {
            double[] amt = result.getKey();
            if (amt[0] > 100000 && amt[ResourceType.FOOD.ordinal()] < -1) {
                long ppu = Math.round(amt[0] / Math.abs(amt[ResourceType.FOOD.ordinal()]));
                long created = user.getTimeCreated().toEpochSecond() * 1000L;
                if (ppu == 100_000 && !db.hasAlliance() && me.getAgeDays() < 30) {
                    Locutus.imp().getRootDb().addCoalition(db.getIdLong(), Coalition.FROZEN_FUNDS);
                }
            }
            return result.getValue();
        }
    }

    @Command(desc = "Get a sheet of a nation tax deposits over a period\n" +
            "If a tax base is set for the nation or alliance then only the portion within member holdings are included by default", viewable = true)
    @RolePermission(value = Roles.ECON)
    @IsAlliance
    public String taxDeposits(@Me IMessageIO io, @Me GuildDB db, Set<DBNation> nations, @Arg("Set to 0/0 to include all taxes") @Default() TaxRate baseTaxRate, @Default() @Timestamp Long startDate, @Default() @Timestamp Long endDate, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        Set<Integer> allianceIds = db.getAllianceIds();

        if (startDate == null) startDate = 0L;
        if (endDate == null) endDate = Long.MAX_VALUE;

        List<TaxDeposit> taxes = new ArrayList<>();
        for (int allianceId : allianceIds) {
            taxes.addAll(Locutus.imp().getBankDB().getTaxesByAA(allianceId));
        }
        Map<Integer, double[]> totalByNation = new Int2ObjectOpenHashMap<>();

        int[] baseArr = baseTaxRate == null ? null : baseTaxRate.toArray();
        TaxRate aaBase = db.getOrNull(GuildKey.TAX_BASE);

        for (TaxDeposit tax : taxes) {
            if (tax.date < startDate || tax.date > endDate) continue;
            DBNation nation = DBNation.getById(tax.nationId);
            if (!nations.contains(nation)) continue;

            int[] internalRate = new int[] {tax.internalMoneyRate, tax.internalResourceRate};
            if (baseArr != null && baseArr[0] >= 0) internalRate[0] = baseArr[0];
            if (baseArr != null && baseArr[1] >= 0) internalRate[1] = baseArr[1];
            if (internalRate[0] < 0) internalRate[0] = aaBase != null && aaBase.money >= 0 ? aaBase.money : 0;
            if (internalRate[1] < 0) internalRate[1] = aaBase != null && aaBase.resources >= 0 ? aaBase.resources : 0;

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
            transfers.put(DBNation.getById(entry.getKey()), entry.getValue());
        }
        txSheet.write(transfers).build();

        txSheet.getSheet().attach(io.create(), "tax_deposits").send();
        return null;
    }

    @Command(desc = "Get a sheet of a nation's in-game tax records and full resource amounts over a period", viewable = true)
    @RolePermission(value = Roles.ECON)
    @IsAlliance
    public String taxRecords(@Me IMessageIO io, @Me GuildDB db, DBNation nation, @Default() @Timestamp Long startDate, @Default() @Timestamp Long endDate, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        Set<Integer> aaIds = db.getAllianceIds();

        if (startDate == null) startDate = 0L;
        if (endDate == null) endDate = Long.MAX_VALUE;

        List<TaxDeposit> taxes = new ArrayList<>();
        for (int aaId : aaIds) {
            taxes.addAll(Locutus.imp().getBankDB().getTaxesPaid(nation.getNation_id(), aaId));
        }

        if (sheet == null) sheet = SpreadSheet.create(db, SheetKey.TAX_RECORD_SHEET);

        List<Object> header = new ArrayList<>(Arrays.asList("nation", "date", "taxrate", "internal_taxrate"));
        for (ResourceType value : ResourceType.values) {
            if (value != ResourceType.CREDITS) header.add(value.name());
        }
        sheet.setHeader(header);

        for (TaxDeposit tax : taxes) {
            if (tax.date < startDate || tax.date > endDate) continue;
            header.set(0, MarkupUtil.sheetUrl(nation.getNation(), nation.getUrl()));
            header.set(1, TimeUtil.YYYY_MM_DD_HH_MM_SS.format(new Date(tax.date)));
            header.set(2, tax.moneyRate + "/" + tax.resourceRate);
            header.set(3, tax.internalMoneyRate + "/" + tax.internalResourceRate);
            int i = 0;
            for (ResourceType type : ResourceType.values) {
                if (type != ResourceType.CREDITS) header.set(4 + i++, tax.resources[type.ordinal()]);
            }
            sheet.addRow(header);
        }
        sheet.updateClearCurrentTab();
        sheet.updateWrite();
        sheet.attach(io.create(), "tax_records").send();
        return null;
    }

    @Command(desc = "Send from your nation's deposits) to another account (internal transfer", groups = {
            "Amount to send",
            "Required: Send To",
            "Optional: Send From"
    },
            groupDescs = {
                    "",
                    "Pick a receiver account, nation, or both",
                    "If there are multiple alliances registered to this guild"
            })
    @RolePermission(value = Roles.ECON)
    public String send(@Me IMessageIO channel, @Me JSONObject command, @Me GuildDB senderDB, @Me User user, @Me DBAlliance alliance, @Me Rank rank, @Me DBNation me,
                       @Arg(value = "The amount to send", group = 0)
                       @AllianceDepositLimit Map<ResourceType, Double> amount,

                       @Arg(value = "The offshore alliance or guild account to send to\n" +
                       "Defaults to this guild", group = 1)
                       @Default GuildOrAlliance receiver_account,
                       @Arg(value = "The alliance or guild nation account to send to\n" +
                       "Defaults to None", group = 1)
                       @Default DBNation receiver_nation,

                       @Arg(value = "The offshore alliance account to send from\n" +
                       "Defaults to your alliance (if valid)", group = 2)
                       @Default DBAlliance sender_alliance,

                       @Switch("f") boolean force) throws IOException {
        if (OffshoreInstance.DISABLE_TRANSFERS && user.getIdLong() != Locutus.loader().getAdminUserId()) throw new IllegalArgumentException(DISABLED_MESSAGE);
        return sendAA(channel, command, senderDB, user, me, amount, receiver_account, receiver_nation, sender_alliance, me, force);
    }

    @Command(desc = "Send from your alliance offshore account to another account (internal transfer)", groups = {
            "Amount to send",
            "Required: Send To",
            "Optional: Send From"
    },
    groupDescs = {
            "",
            "Pick a receiver account, nation, or both",
            "If using a different account to send from"
    })
    @RolePermission(value = Roles.ECON)
    public String sendAA(@Me IMessageIO channel, @Me JSONObject command, @Me GuildDB senderDB, @Me User user, @Me DBNation me,
                         @Arg(value = "The amount to send", group = 0)
                         @AllianceDepositLimit Map<ResourceType, Double> amount,
                         @Arg(value = "The offshore alliance or guild account to send to\n" +
                                 "Defaults to this guild", group = 1)
                         @Default GuildOrAlliance receiver_account,
                         @Arg(value = "The alliance or guild nation account to send to\n" +
                                 "Defaults to None", group = 1)
                         @Default DBNation receiver_nation,

                         @Arg(value = "The offshore alliance account to send from\n" +
                                 "Defaults to your alliance (if valid)", group = 2)
                         @Default DBAlliance sender_alliance,
                         @Arg(value = "The nation to send from\n" +
                                 "Defaults to your nation", group = 2)
                         @Default DBNation sender_nation,
                         @Switch("f") boolean force) throws IOException {
        if (user.getIdLong() != Locutus.loader().getAdminUserId()) return "WIP";
        if (OffshoreInstance.DISABLE_TRANSFERS && user.getIdLong() != Locutus.loader().getAdminUserId()) throw new IllegalArgumentException(DISABLED_MESSAGE);
        if (sender_alliance != null && !senderDB.isAllianceId(sender_alliance.getId())) {
            throw new IllegalArgumentException("Sender alliance is not in this guild");
        }
        if (receiver_account == null && receiver_nation == null) {
            throw new IllegalArgumentException("Please specify a `receiver_account` or `receiver_nation` or both");
        }
        boolean hasEcon = Roles.ECON.has(user, senderDB.getGuild());
        if (!hasEcon) {
            if (sender_alliance != null && sender_alliance.getId() != me.getAlliance_id()) {
                throw new IllegalArgumentException("You do not have permission to send from another alliance (only: " + me.getAllianceUrlMarkup() + ") " + Roles.ECON.toDiscordRoleNameElseInstructions(senderDB.getGuild()));
            }
            if (sender_nation == null) {
                throw new IllegalArgumentException("You do not have permission to omit `sender_nation` " + Roles.ECON.toDiscordRoleNameElseInstructions(senderDB.getGuild()));
            } else if (sender_nation.getId() != me.getId()) {
                throw new IllegalArgumentException("You do not have permission to send from another nation (only: " + me.getNationUrlMarkup() + ") " + Roles.ECON.toDiscordRoleNameElseInstructions(senderDB.getGuild()));
            }
        }

        double[] amountArr = ResourceType.resourcesToArray(amount);
        GuildDB receiverDB = receiver_account.isGuild() ? receiver_account.asGuild() : null;
        DBAlliance receiverAlliance = receiver_account.isAlliance() ? receiver_account.asAlliance() : null;
        List<TransferResult> results = senderDB.sendInternal(user, me, senderDB, sender_alliance, sender_nation, receiverDB, receiverAlliance, receiver_nation, amountArr, force);
        if (results.size() == 1) {
            TransferResult result = results.get(0);
            if (result.getStatus() == OffshoreInstance.TransferStatus.CONFIRMATION) {
                String worth = "$" + MathMan.format(ResourceType.convertedTotal(amount));
                String receiverName;
                if (receiver_account != null && receiver_nation != null) {
                    receiverName = receiver_account.getQualifiedName() + " | " + receiver_nation.getNation();
                } else if (receiver_account != null) {
                    receiverName = receiver_account.getQualifiedName();
                } else {
                    receiverName = receiver_nation.getNation();
                }
                String title = "Send (worth: " + worth + ") to " + receiverName;
                channel.create().confirmation(title, result.getMessageJoined(false), command, "force", "Send").cancelButton().send();
                return null;
            }
        }
        Map.Entry<String, String> embed = TransferResult.toEmbed(results);
        channel.create().embed(embed.getKey(), embed.getValue()).send();
        return null;
    }

    @Command(desc="Displays the account balance for a nation, alliance or guild\n" +
            "Balance info includes deposits, loans, grants, taxes and escrow", viewable = true)
    @RolePermission(Roles.MEMBER)
    @UserCommand
    public static String deposits(@Me Guild guild, @Me GuildDB db, @Me IMessageIO channel, @Me DBNation me, @Me User author,
                           @AllowDeleted
                           @StarIsGuild
                           @Arg("Account to check holdings for") NationOrAllianceOrGuildOrTaxid nationOrAllianceOrGuild,
                           @Arg("The alliances to check transfers from\nOtherwise the guild configured ones will be used")
                           @Switch("a") Set<DBAlliance> offshores,
                           @Arg("Only include transfers after this time")
                           @Switch("c") @Timestamp Long timeCutoff,
                            @Arg("Include all taxes in account balance")
                           @Switch("b") boolean includeBaseTaxes,
                            @Arg("Do NOT include manual offsets in account balance")
                           @Switch("o") boolean ignoreInternalOffsets,
                            @Arg("Show separate sections for taxes and deposits")
                           @Switch("t") Boolean showCategories,
                           @Switch("d") boolean replyInDMs,
                           @Arg("Include expired transfers")
                           @Switch("e") boolean includeExpired,
                           @Arg("Include transfers marked as ignore")
                           @Switch("i") boolean includeIgnored,
                           @Switch("z") boolean allowCheckDeleted,
                           @Arg("Hide the escrow balance ") @Switch("h") boolean hideEscrowed,
                           @Switch("s") boolean show_expiring_records
    ) throws IOException {
        if (show_expiring_records && !nationOrAllianceOrGuild.isNation()) {
            throw new IllegalArgumentException("Only nations can show expiring records");
        }
        boolean condensedFormat = GuildKey.DISPLAY_CONDENSED_DEPOSITS.getOrNull(db) == Boolean.TRUE;
        if (!nationOrAllianceOrGuild.isNation() && !nationOrAllianceOrGuild.isTaxid()) {
            showCategories = false;
        }
            if (showCategories == null) {
            showCategories = (db.getOrNull(GuildKey.DISPLAY_ITEMIZED_DEPOSITS) == Boolean.TRUE);
        }
        if (timeCutoff == null) timeCutoff = 0L;
        Set<Long> offshoreIds = offshores == null ? null : offshores.stream().map(f -> f.getIdLong()).collect(Collectors.toSet());
        if (offshoreIds != null) offshoreIds = PW.expandCoalition(offshoreIds);

//        boolean hasAdmin = Roles.ECON.has(author, guild);
//        AllianceList allowed = Roles.ECON.getAllianceList(author, db);

        Map<DepositType, double[]> accountDeposits = new HashMap<>();
        double[] escrowed = null;
        long escrowExpire = 0;

        List<String> footers = new ArrayList<>();
        IMessageIO output = replyInDMs ? new DiscordChannelIO(RateLimitUtil.complete(author.openPrivateChannel()), null) : channel;
        IMessageBuilder msg = output.create();

        if (nationOrAllianceOrGuild.isAlliance()) {
            DBAlliance alliance = nationOrAllianceOrGuild.asAlliance();

            GuildDB otherDb2 = alliance.getGuildDB();
            if (otherDb2 == null && !allowCheckDeleted) throw new IllegalArgumentException("No guild found for " + alliance);

            OffshoreInstance offshore = otherDb2 == null && allowCheckDeleted ? db.getOffshore() : otherDb2.getOffshore();

            if (offshore == null) {
                if (otherDb2 == db) {
                    if (!Roles.ECON.has(author, otherDb2.getGuild())) {
                        return "You do not have permisssion to check another alliance's deposits (1)";
                    }
                    Map<ResourceType, Double> stock = alliance.getStockpile(true);
                    accountDeposits.put(DepositType.DEPOSIT, ResourceType.resourcesToArray(stock));
                } else {
                    return "No offshore is set. In this server, use " + CM.coalition.add.cmd.alliances("AA:" + alliance.getAlliance_id()).coalitionName(Coalition.OFFSHORE.name()) + " and from the offshore server use " + CM.coalition.add.cmd.alliances("AA:" + alliance.getAlliance_id()).coalitionName(Coalition.OFFSHORING.name());
                }
            } else if (otherDb2 != db && offshore.getGuildDB() != db) {
                return "You do not have permisssion to check another alliance's deposits (2)";
            } else {
                if ((otherDb2 == null || !Roles.ECON.has(author, otherDb2.getGuild())) && (!Roles.ECON.has(author, offshore.getGuildDB().getGuild()))) {
                    return "You do not have permisssion to check another alliance's deposits (3)";
                }
                double[] deposits = ResourceType.resourcesToArray(offshore.getDeposits(alliance.getAlliance_id(), true));
                accountDeposits.put(DepositType.DEPOSIT, deposits);
            }
        } else if (nationOrAllianceOrGuild.isGuild()) {
            GuildDB otherDb = nationOrAllianceOrGuild.asGuild();
            OffshoreInstance offshore = otherDb.getOffshore();
            if (offshore == null) return "No offshore is set. In this server, use " + CM.coalition.add.cmd.alliances(nationOrAllianceOrGuild.getIdLong() + "").coalitionName(Coalition.OFFSHORE.name()) + " and from the offshore server use " + CM.coalition.add.cmd.alliances(nationOrAllianceOrGuild.getIdLong() + "").coalitionName(Coalition.OFFSHORING.name());

            if (!Roles.ECON.has(author, offshore.getGuildDB().getGuild()) && !Roles.ECON.has(author, otherDb.getGuild())) {
                return "You do not have permission to check another guild's deposits";
            }
            double[] deposits = offshore.getDeposits(otherDb);
            accountDeposits.put(DepositType.DEPOSIT, deposits);

        } else if (nationOrAllianceOrGuild.isNation()) {
            DBNation nation = nationOrAllianceOrGuild.asNation();
            if (nation != me && !Roles.INTERNAL_AFFAIRS.has(author, guild) && !Roles.INTERNAL_AFFAIRS_STAFF.has(author, guild) && !Roles.ECON.has(author, guild) && !Roles.ECON_STAFF.has(author, guild)) return "You do not have permission to check other nation's deposits";
            List<Map.Entry<Integer, Transaction2>> transactions = nation.getTransactions(db, offshoreIds, !includeBaseTaxes, !ignoreInternalOffsets, 0L, timeCutoff, true);
            accountDeposits = PW.sumNationTransactions(nation, db, offshoreIds, transactions, includeExpired, includeIgnored, f -> true);

            if (show_expiring_records) {
                long now = System.currentTimeMillis();
                double[] perDay = ResourceType.getBuffer();

                List<List<Object>> rows = new ObjectArrayList<>();
                List<Object> header = new ObjectArrayList<>(Arrays.asList(
                        "sign",
                        "end_date",
                        "created",
                        "initial_value",
                        "decay_value/day",
                        "decay/day",
                        "initial",
                        "remaining"
                ));
                rows.add(header);

                for (Map.Entry<Integer, Transaction2> entry : transactions) {
                    Transaction2 record = entry.getValue();
                    if (record.note == null || record.note.isEmpty()) continue;
                    boolean isOffshoreSender = (record.sender_type == 2 || record.sender_type == 3) && record.receiver_type == 1;
                    if (!isOffshoreSender && !record.isInternal()) continue;
                    int sign = entry.getKey();

                    Map<DepositType, Object> noteMap = record.getNoteMap();
                    Object expireVal = noteMap.get(DepositType.EXPIRE);
                    Object decayVal = noteMap.get(DepositType.DECAY);
                    Long dateVal;
                    if (decayVal instanceof Number n) {
                        dateVal = n.longValue();
                    } else if (expireVal instanceof Number n) {
                        dateVal = n.longValue();
                    } else {
                        continue;
                    }
                    if (dateVal <= now) continue;

                    List<Object> row = new ObjectArrayList<>();
                    row.add(sign);
                    row.add(TimeUtil.YYYY_MM_DD_HH_MM_SS.format(dateVal));
                    row.add(TimeUtil.YYYY_MM_DD_HH_MM_SS.format(record.tx_datetime));
                    row.add(ResourceType.convertedTotal(record.resources));

                    if (decayVal != null) {
                        double[] principal = record.resources.clone();
                        double decayFactor = 1 - (now - record.tx_datetime) / (double) (dateVal - record.tx_datetime);
                        double[] remaining = PW.multiply(principal.clone(), decayFactor);
                        double decayFactorPerDay = (double) TimeUnit.DAYS.toMillis(1) / (dateVal - record.tx_datetime);
                        double[] decayPerDay = ResourceType.getBuffer();
                        for (int i = 0; i < principal.length; i++) {
                            decayPerDay[i] = Math.min(remaining[i], principal[i] * decayFactorPerDay);
                        }
                        double decayValuePerDay = ResourceType.convertedTotal(decayPerDay);

                        row.add(MathMan.format(decayValuePerDay));
                        row.add(ResourceType.toString(decayPerDay));
                        row.add(ResourceType.toString(principal));
                        row.add(ResourceType.toString(remaining));
                    } else {
                        row.add("");
                        row.add("");
                        row.add(ResourceType.toString(record.resources));
                        row.add(ResourceType.toString(record.resources));
                    }
                    rows.add(row);
                }
                if (rows.size() > 1) {
                    String title = "expire_records.csv";
                    String csv = StringMan.join(rows, "\n", f -> StringMan.join(f, "\t"));
                    msg.file(title, csv);
                } else {
                    footers.add("No remaining expiring records found");
                }
            }

            if (!hideEscrowed) {
                Map.Entry<double[], Long> escoredPair = db.getEscrowed(nation);
                if (escoredPair != null) {
                    if (escrowed == null) {
                        escrowed = ResourceType.getBuffer();
                    }
                    ResourceType.add(escrowed, escoredPair.getKey());
                    escrowExpire = escoredPair.getValue();
                }
            }
        } else if (nationOrAllianceOrGuild.isTaxid()) {
            TaxBracket bracket = nationOrAllianceOrGuild.asBracket();
            Map<DepositType, double[]> deposits = db.getTaxBracketDeposits(bracket.taxId, timeCutoff, includeExpired, includeIgnored);
            accountDeposits.putAll(deposits);

            if (showCategories) {
                footers.add("`#TAX` is for the portion of tax income that does NOT go into member holdings");
                footers.add("`#DEPOSIT` is for the portion of tax income in member holdings");
            } else {
                footers.add("Set `showCategories` for breakdown of tax within member holdings");
            }
        }

        String title = "Deposits for: " + nationOrAllianceOrGuild.getQualifiedName();
        Map.Entry<double[], String> balanceBody = PW.createDepositEmbed(db, nationOrAllianceOrGuild, accountDeposits, showCategories, escrowed, escrowExpire, condensedFormat);
        double[] balance = balanceBody.getKey();
        String body = balanceBody.getValue();
        Map<String, Map.Entry<CommandRef, Boolean>> buttons = new LinkedHashMap<>();

        if (me != null && nationOrAllianceOrGuild == me) {
            if (!condensedFormat) {
                footers.add("Funds default to `#deposit` if no other note is used");
            }
            if (Boolean.TRUE.equals(db.getOrNull(GuildKey.RESOURCE_CONVERSION)) && !condensedFormat) {
                footers.add("You can sell resources to the alliance by depositing with the note `#cash`");
            }
//            if (PW.convertedTotal(balance) > 0 && Boolean.TRUE.equals(db.getOrNull(GuildKey.MEMBER_CAN_WITHDRAW))) {
//                if (Roles.ECON_WITHDRAW_SELF.has(author, db.getGuild())) {
//                    footers.add("To withdraw, use: `" + CM.transfer.self.cmd.toSlashMention() + "` ");
//                }
//            }
        }
        boolean econStaff = Roles.ECON_STAFF.has(author, guild);
        boolean econ = Roles.ECON.has(author, guild);

        if (nationOrAllianceOrGuild.isNation()) {
            boolean canWithdraw = econ || (ResourceType.convertedTotal(balance) > 0 && Boolean.TRUE.equals(db.getOrNull(GuildKey.MEMBER_CAN_WITHDRAW)) && Roles.ECON_WITHDRAW_SELF.has(author, guild));
            if (canWithdraw) {
                // add button, add note
                if (me != null && me.getId() == nationOrAllianceOrGuild.getId()) {
                    buttons.put("withdraw",
                            KeyValue.of(
                                    CM.transfer.self.cmd.amount("").bank_note(DepositType.DEPOSIT.name()),
                                    true));
                }
                buttons.put("withdraw elsewhere",
                        KeyValue.of(
                                CM.transfer.resources.cmd.receiver("").transfer("").depositType(DepositType.DEPOSIT.name())
                                        .nationAccount(nationOrAllianceOrGuild.getQualifiedId()),
                                true));
                footers.add("To withdraw: " + CM.transfer.self.cmd.toSlashMention() + " or " + CM.transfer.resources.cmd.toSlashMention() + " ");
            }
            if (escrowed != null && !ResourceType.isZero(escrowed)) {
                if (Roles.ECON_WITHDRAW_SELF.has(author, guild)) {
                    // add button, add note
                    buttons.put("withdraw escrow", KeyValue.of(CM.escrow.withdraw.cmd.receiver(nationOrAllianceOrGuild.getQualifiedId()).amount(""), true));
                } else if (!condensedFormat) {
                    footers.add("You do not have permission to withdraw escrowed funds");
                }
            }
            if (econ) {
                footers.add("To view records: " + CM.bank.records.cmd.nationOrAllianceOrGuild(nationOrAllianceOrGuild.getQualifiedId()));
            }
        } else if (nationOrAllianceOrGuild.isAlliance()) {
            if (econ) {
                buttons.put("withdraw",
                        KeyValue.of(
                                CM.transfer.resources.cmd.receiver("").transfer("").depositType(DepositType.IGNORE.name())
                                        .allianceAccount(nationOrAllianceOrGuild.getQualifiedId()),
                                true));
                footers.add("To withdraw: " + CM.transfer.resources.cmd.toSlashMention() + " with `#ignore` as note");
                footers.add("To view records: " + CM.bank.records.cmd.nationOrAllianceOrGuild(nationOrAllianceOrGuild.getQualifiedId()).onlyOffshoreTransfers("true"));
            }
        } else if (nationOrAllianceOrGuild.isTaxid()) {
            buttons.put("withdraw",
                    KeyValue.of(
                            CM.transfer.resources.cmd.receiver("").transfer("").depositType("").taxAccount(nationOrAllianceOrGuild.getQualifiedName()),
                            true));
            footers.add("To withdraw: " + CM.transfer.resources.cmd.toSlashMention() + " with `taxaccount: " + nationOrAllianceOrGuild.getQualifiedName() + "`");

            if (econ) {
                footers.add("To add balance: " + CM.deposits.add.cmd.toSlashMention() + " with `accounts: " + nationOrAllianceOrGuild.getQualifiedName() + "`");
                footers.add("To view records: " + CM.bank.records.cmd.nationOrAllianceOrGuild(nationOrAllianceOrGuild.getQualifiedId()));
            }
        } else if (nationOrAllianceOrGuild.isGuild()) {
            // trade deposit
            if (econ) {
                buttons.put("withdraw",
                        KeyValue.of(
                                CM.transfer.resources.cmd.receiver("").transfer("").depositType(DepositType.IGNORE.name()),
                                true));
                footers.add("To withdraw: " + CM.transfer.resources.cmd.toSlashMention() + " with `#ignore` as note");
                Map.Entry<GuildDB, Integer> offshore = db.getOffshoreDB();
                if (offshore != null) {
                    Set<Integer> aaIds = db.getAllianceIds();
                    String note = aaIds.isEmpty() ? "#guild=" + guild.getIdLong() : "#alliance=" + aaIds.iterator().next();
                    footers.add("Send to " + PW.getMarkdownUrl(offshore.getValue(), true) + " with note `" + note + "` to offshore\n" +
                            "Or " + MarkupUtil.markdownUrl("send a trade", "https://github.com/xdnw/locutus/wiki/banking#for-my-corporation"));
                }
                footers.add("To view records: " + CM.bank.records.cmd.nationOrAllianceOrGuild(nationOrAllianceOrGuild.getIdLong() + "").onlyOffshoreTransfers("true"));
            }
        }

        if (!showCategories && (nationOrAllianceOrGuild.isNation() || nationOrAllianceOrGuild.isTaxid())) {
            // add footer and button for showing separately
            String itemziedSetting = !econ ? "" : "or " + GuildKey.DISPLAY_ITEMIZED_DEPOSITS.getCommandMention() + " ";
            if (!condensedFormat) {
                footers.add("Use `showCategories: True` " + itemziedSetting + "for a breakdown");
            }
            buttons.put("breakdown",
                    KeyValue.of(
                            CM.deposits.check.cmd.nationOrAllianceOrGuild(
                                    nationOrAllianceOrGuild.getQualifiedId())
                                    .offshores(
                                            offshores == null || offshores.isEmpty() ? null : StringMan.join(offshores.stream().map(DBAlliance::getId).toList(), ",")
                                    ).timeCutoff(
                                    timeCutoff != null && timeCutoff > 0 ? "timestamp:" + timeCutoff : null
                                    ).includeBaseTaxes(
                                    includeBaseTaxes ? "true" : null
                                    ).ignoreInternalOffsets(
                                    ignoreInternalOffsets ? "true" : null
                                            ).showCategories(
                                    "true"
                                            ).replyInDMs(
                                    replyInDMs ? "true" : null
                                            ).includeExpired(
                                    includeExpired ? "true" : null
                                            ).includeIgnored(
                                    includeIgnored ? "true" : null
                                    ).hideEscrowed(
                                            hideEscrowed ? "true" : null
                            ), false));
        }

        boolean canOffshore = db.isValidAlliance() && (Boolean.TRUE.equals(GuildKey.MEMBER_CAN_OFFSHORE.getOrNull(db)) || Roles.ECON_STAFF.has(author, guild));
        if (canOffshore && (nationOrAllianceOrGuild.isNation() || nationOrAllianceOrGuild.isAlliance())) {
            Map.Entry<GuildDB, Integer> offshore = db.getOffshoreDB();
            if (offshore != null) {
                buttons.put("offshore",
                        KeyValue.of(
                                CM.offshore.send.cmd,
                                false));
                if (!condensedFormat) {
                    if (GuildKey.API_KEY.getOrNull(db) != null) {
                        footers.add("To offshore: " + CM.offshore.send.cmd.toSlashMention());
                    } else {
                        footers.add("To offshore, send to " + PW.getMarkdownUrl(offshore.getValue(), true));
                    }
                }
            }
        }

        StringBuilder response = new StringBuilder(body);

        if (!footers.isEmpty()) {
            if (condensedFormat) {
                response.append("\n-# **Tips:**\n");
            } else {
                response.append("\n-# **Tips:**\n");
            }
            for (String footer : footers) {
                response.append("-# - " + footer + "\n");
            }
        }

        msg.embed(title, response.toString());
        for (Map.Entry<String, Map.Entry<CommandRef, Boolean>> entry : buttons.entrySet()) {
            String label = entry.getKey();
            CommandRef cmd = entry.getValue().getKey();
            boolean isModal = entry.getValue().getValue();
            if (isModal) {
                msg = msg.modal(CommandBehavior.EPHEMERAL, cmd, label);
            } else {
                msg = msg.commandButton(CommandBehavior.EPHEMERAL, cmd, label);
            }
        }

        CompletableFuture<IMessageBuilder> msgFuture = msg.send();

        if (me != null && nationOrAllianceOrGuild.isNation() && nationOrAllianceOrGuild.asNation().getPosition() > 1 && db.isWhitelisted() && db.getOrNull(GuildKey.API_KEY) != null && db.getAllianceIds(true).contains(nationOrAllianceOrGuild.asNation().getAlliance_id())) {
            DBNation finalNation = nationOrAllianceOrGuild.asNation();
            Locutus.imp().getExecutor().submit(new Runnable() {
                @Override
                public void run() {
                    int initialLength = response.length();
                    Map<ResourceType, Double> stockpile = finalNation.getStockpile();
                    if (stockpile != null && !stockpile.isEmpty() && stockpile.getOrDefault(ResourceType.CREDITS, 0d) != -1) {
                        Map<ResourceType, Double> excess = finalNation.checkExcessResources(db, stockpile);
                        if (!excess.isEmpty()) {
                            response.append("-# - Excess can be deposited: ```" + ResourceType.toString(excess) + "```\n");
                        }
                    }
                    Map<ResourceType, Double> needed = finalNation.getResourcesNeeded(stockpile, 3, true);
                    if (!needed.isEmpty()) {
                        response.append("-# - Missing resources for the next 3 days: ```" + ResourceType.toString(needed) + "```\n");
                    }

                    if (me != null && me.getNation_id() == finalNation.getNation_id() && Boolean.TRUE.equals(db.getOrNull(GuildKey.MEMBER_CAN_OFFSHORE)) && db.isValidAlliance()) {
                        AllianceList alliance = db.getAllianceList();
                        if (alliance != null && !alliance.isEmpty() && alliance.contains(me.getAlliance_id())) {
                            try {
                                Map<ResourceType, Double> aaStockpile = me.getAlliance().getStockpile();
                                if (aaStockpile != null && ResourceType.convertedTotal(aaStockpile) > 5000000) {
                                    response.append("-# - The alliance bank has funds to offshore\n");
                                }
                            } catch (Throwable ignore) {}
                        }
                    }

                    if (response.length() != initialLength) {
                        try {
                            IMessageBuilder msg = msgFuture.get();
                            if (msg != null && msg.getId() > 0) {
                                msg.clearEmbeds().embed(title, response.toString()).send();
                            }
                        } catch (InterruptedException | ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            });
        }

        return null;
    }

    @Command(desc = "Calculate weekly interest payments for a loan", viewable = true)
    public String weeklyInterest(@Arg("Principle amount") double amount, @Arg("Percent weekly interest") double pct, @Arg("Number of weeks to loan for") int weeks) {
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

    @Command(desc = "List all nations in the alliance and their in-game resource stockpile", groups = {
            "Optional: Specify Nations",
            "Display Options",
    }, viewable = true)
    @RolePermission(any = true, value = {Roles.ECON_STAFF, Roles.ECON, Roles.INTERNAL_AFFAIRS, Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM})
    @IsAlliance
    public String stockpileSheet(@Me GuildDB db, @Me IMessageIO channel,
                                 @Arg(value = "Only include stockpiles from these nations", group = 0) @Default NationList nationFilter,

                                 @Arg(value = "Divide stockpiles by city count", group = 1) @Switch("n") boolean normalize,
                                 @Arg(value = "Only show the resources well above warchest and city operation requirements", group = 1) @Switch("e") boolean onlyShowExcess,
                                 @Switch("f") boolean forceUpdate) throws IOException, GeneralSecurityException {
        if (nationFilter != null && !db.getAllianceIds().containsAll(nationFilter.getAllianceIds())) {
            return "You can only view stockpiles for nations in your alliance: (" + db.getAllianceIds() + ")";
        }
        AllianceList alliance = db.getAllianceList();
        if (nationFilter != null) alliance = alliance.subList(nationFilter.getAllianceIds());

        Map<DBNation, Map<ResourceType, Double>> stockpile = alliance.getMemberStockpile();

        List<String> header = new ArrayList<>();
        header.add("nation");
        header.add("discord");
        header.add("cities");
        header.add("avg_infra");
        header.add("off|def");
        header.add("mmr");

        header.add("marketValue");
        for (ResourceType value : ResourceType.values) {
            header.add(value.name().toLowerCase());
        }

        SpreadSheet sheet = SpreadSheet.create(db, SheetKey.STOCKPILE_SHEET);
        sheet.setHeader(header);

        double[] aaTotal = ResourceType.getBuffer();

        if (forceUpdate) {
            alliance.updateCities();
        }

        for (Map.Entry<DBNation, Map<ResourceType, Double>> entry : stockpile.entrySet()) {
            List<Object> row = new ArrayList<>();

            DBNation nation = entry.getKey();
            if (nation == null || (nationFilter != null && !nationFilter.getNations().contains(nation))) continue;
            row.add(MarkupUtil.sheetUrl(nation.getNation(), nation.getUrl()));
            row.add(nation.getUserDiscriminator());
            row.add(nation.getCities());
            row.add(nation.getAvg_infra());
            row.add(nation.getOff() +"|" + nation.getDef());
            row.add(nation.getMMR());

            Map<ResourceType, Double> rss = entry.getValue();
            if (onlyShowExcess) {
                rss = nation.checkExcessResources(db, rss, false);
            }

            row.add(ResourceType.convertedTotal(rss));

            for (ResourceType type : ResourceType.values) {
                double amt = rss.getOrDefault(type, 0d);
                if (normalize) amt /= nation.getCities();
                row.add(amt);

                if (amt > 0) aaTotal[type.ordinal()] += amt;
            }

            sheet.addRow(row);
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        String totalStr = ResourceType.resourcesToFancyString(aaTotal);
        totalStr += "\n`note:total ignores nations with alliance info disabled`";
        sheet.attach(channel.create().embed("Nation Stockpiles", totalStr), "stockpiles").send();
        return null;
    }

    @Command(desc = "Generate a sheet of member tax brackets and internal tax rates\n" +
            "`note: internal tax rate is the TAX_BASE and determines what % of their taxes is excluded from deposits`", viewable = true)
    @RolePermission(any = true, value = {Roles.ECON, Roles.ECON_STAFF})
    public String taxBracketSheet(@Me IMessageIO io, @Me GuildDB db, @Switch("f") boolean force, @Switch("a") boolean includeApplicants) throws Exception {
        SpreadSheet sheet = SpreadSheet.create(db, SheetKey.TAX_BRACKET_SHEET);
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
            AllianceList alliances = db.getAllianceList();
            if (alliances == null) {
                return "Please register an alliance: " + CM.settings_default.registerAlliance.cmd.toSlashMention();
            }
            brackets = alliances.getTaxBrackets(TimeUnit.MINUTES.toMillis(5));
            failedFetch = false;
        } catch (IllegalArgumentException e) {
            brackets = new LinkedHashMap<>();
            Set<Integer> allianceIds = db.getAllianceIds(true);
            Map<Integer, TaxBracket> allAllianceBrackets = Locutus.imp().getBankDB().getTaxBracketsAndEstimates();
            for (Map.Entry<Integer, TaxBracket> entry : allAllianceBrackets.entrySet()) {
                TaxBracket bracket = entry.getValue();
                if (allianceIds.contains(bracket.getAlliance_id(false))) {
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

            header.set(0, MarkupUtil.sheetUrl(nation.getNation(), nation.getUrl()));
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

        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        StringBuilder response = new StringBuilder();
        if (failedFetch) response.append("\nnote: Please set an api key with " + CM.credentials.addApiKey.cmd.toSlashMention() + " to view updated tax brackets");

        sheet.attach(io.create(), "tax_brackets", response.toString()).send();
        return null;
    }

    @Command(desc = "Set the bot managed offshore for this guild\n" +
            "The alliance must use a guild with locutus settings `ALLIANCE_ID` and `API_KEY` set, and the coalitions `offshore` and `offshoring` set to include the offshore alliance")
    @RolePermission(value = Roles.ADMIN)
    public static String addOffshore(@Me IMessageIO io, @Me User user, @Me GuildDB root, @Me DBNation nation, @Me JSONObject command, DBAlliance offshoreAlliance, @Switch("n") boolean newAccount, @Switch("i") boolean importAccount, @Switch("f") boolean force) throws IOException {
        if (importAccount && newAccount) {
            throw new IllegalArgumentException("Cannot specify both `newAccount` and `importAccount` (pick one)");
        }
        if (root.isDelegateServer()) return "Cannot enable offshoring for delegate server (run this command in the root server)";
        IMessageBuilder confirmButton = io.create().confirmation(command);
        GuildDB offshoreDB = offshoreAlliance.getGuildDB();

        if (offshoreDB != null) {
            if (!offshoreDB.hasAlliance()) {
                return "Please set the key " + GuildKey.ALLIANCE_ID.getCommandMention() + " in " + offshoreDB.getGuild();
            }
            PoliticsAndWarV3 api = offshoreAlliance.getApi(AlliancePermission.WITHDRAW_BANK, AlliancePermission.VIEW_BANK);
            if (api == null) {
                return "Please set the key " + GuildKey.API_KEY.getCommandMention() + " in " + offshoreDB.getGuild();
            }
        }

        if (root.isOffshore(true) && (offshoreDB == null || (offshoreDB == root))) {
            if (nation.getAlliance_id() != offshoreAlliance.getAlliance_id()) {
                GuildKey.ALLIANCE_ID.validate(root, user, Set.of(offshoreAlliance.getAlliance_id()));
//                throw new IllegalArgumentException("You must be in the provided alliance: " + offshoreAlliance.getId() + " to set the new ALLIANCE_ID for this offshore");
            }

            Set<Long> announceChannels = new LongOpenHashSet();
            Set<Long> serverIds = new LongOpenHashSet();
            if (nation.getNation_id() == Locutus.loader().getNationId()) {
                announceChannels.add(Settings.INSTANCE.DISCORD.CHANNEL.ADMIN_ALERTS);
            }

            outer:
            for (GuildDB other : Locutus.imp().getGuildDatabases().values()) {
                if (other.isDelegateServer()) continue outer;

                Set<Long> offshoreIds = other.getCoalitionRaw(Coalition.OFFSHORE);
                if (offshoreIds.isEmpty()) continue;

                for (int id : root.getAllianceIds()) {
                    if (offshoreIds.contains((long) id)) {
                        serverIds.add(other.getIdLong());
                        continue outer;
                    }
                }
                if (offshoreIds.contains(root.getIdLong())) {
                    serverIds.add(other.getIdLong());
                    continue outer;
                }
            }

            Set<Integer> aaIds = root.getAllianceIds();
            Set<Integer> toUnregister = new IntOpenHashSet();

            // check which ids are are set in offshore and offshoring coalition
            Set<Integer> offshoringIds = root.getCoalition(Coalition.OFFSHORING);
            Set<Integer> offshoreIds = root.getCoalition(Coalition.OFFSHORE);
            for (int aaId : aaIds) {
                if (offshoreIds.contains(aaId) && offshoringIds.contains(aaId)) {
                    toUnregister.add(aaId);
                }
            }

            if (!force) {
                String title = "Change offshore to: " + offshoreAlliance.getName() + "/" + offshoreAlliance.getId();
                StringBuilder body = new StringBuilder();
                body.append("The alliances to this guild will be unregistered: `" + StringMan.getString(toUnregister) + "`\n");

                body.append("The new alliance: `" + offshoreAlliance.getId() + " will be set ` (See: " + GuildKey.ALLIANCE_ID.getCommandMention() + ")\n");
                body.append("All other guilds using the prior alliance `" + StringMan.getString(toUnregister) + "` will be changed to use the new offshore");

                confirmButton.embed(title, body.toString()).send();
                return null;
            }

            Set<Integer> newIds = new IntOpenHashSet(aaIds);
            newIds.removeAll(toUnregister);
            newIds.add(offshoreAlliance.getAlliance_id());
            GuildKey.ALLIANCE_ID.set(root, user, newIds);
            root.addCoalition(offshoreAlliance.getAlliance_id(), Coalition.OFFSHORE);
            root.addCoalition(offshoreAlliance.getAlliance_id(), Coalition.OFFSHORING);

            for (Long serverId : serverIds) {
                GuildDB db = Locutus.imp().getGuildDB(serverId);
                if (db == null) continue;
                db.addCoalition(offshoreAlliance.getAlliance_id(), Coalition.OFFSHORE);

                // Find the most stuited channel to post the announcement in
                MessageChannel channel = db.getResourceChannel(0);
                if (channel == null) channel = db.getOrNull(GuildKey.ADDBALANCE_ALERT_CHANNEL);
                if (channel == null) channel = db.getOrNull(GuildKey.DEPOSIT_ALERT_CHANNEL);
                if (channel == null) channel = db.getOrNull(GuildKey.WITHDRAW_ALERT_CHANNEL);
                if (channel == null) {
                    List<NewsChannel> newsChannels = db.getGuild().getNewsChannels();
                    if (!newsChannels.isEmpty()) channel = newsChannels.get(0);
                }
                if (channel == null) channel = (MessageChannel) db.getGuild().getDefaultChannel();
                if (channel != null) {
                    announceChannels.add(channel.getIdLong());
                }
            }

            String title = "New Offshore";
            String body = PW.getMarkdownUrl(offshoreAlliance.getAlliance_id(), true);
            for (Long announceChannel : announceChannels) {
                GuildMessageChannel channel = Locutus.imp().getDiscordApi().getGuildChannelById(announceChannel);
                if (channel != null) {
                    try {
                        DiscordUtil.createEmbedCommand(channel, title, body);
                        Role adminRole = Roles.ECON.toRole2(channel.getGuild());
                        if (adminRole == null) {
                            adminRole = Roles.ADMIN.toRole2(channel.getGuild());
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

        if (offshoreDB == null) {
            return "No guild found for alliance: " + offshoreAlliance.getAlliance_id() + ". To register a guild to an alliance: " + CM.settings_default.registerAlliance.cmd.alliances(offshoreAlliance.getAlliance_id() + "");
        }

        OffshoreInstance currentOffshore = root.getOffshore();
        if (currentOffshore != null) {
            if (currentOffshore.getAllianceId() == offshoreAlliance.getAlliance_id()) {
                return "That guild is already the offshore for this server";
            }
            Set<Integer> aaIds = root.getAllianceIds();

            if (!force) {
                String idStr = aaIds.isEmpty() ? root.getIdLong() + "" : StringMan.join(aaIds, ",");
                String title = "Replace current offshore";
                StringBuilder body = new StringBuilder();
                body.append("Changing offshores will close the account with your previous offshore provider\n");
                body.append("Your current offshore is set to: " + currentOffshore.getAllianceId() + "\n");
                body.append("To check your funds with the current offshore, use " +
                        CM.deposits.check.cmd.nationOrAllianceOrGuild(idStr));
                body.append("\nIt is recommended to withdraw all funds from the current offshore before changing, as Locutus may not be able to access the account after closing it`");

                confirmButton.embed(title, body.toString()).send();
                return null;
            }
            for (int aaId : aaIds) {
                offshoreDB.removeCoalition(aaId, Coalition.OFFSHORING);
            }
            offshoreDB.removeCoalition(root.getIdLong(), Coalition.OFFSHORING);

            root.removeCoalition(currentOffshore.getAllianceId(), Coalition.OFFSHORE);
            root.removeCoalition(currentOffshore.getGuildDB().getIdLong(), Coalition.OFFSHORE);
        }

        if (offshoreDB == root) {
            if (!force) {
                String title = "Designate " + offshoreAlliance.getName() + "/" + offshoreAlliance.getId() + " as the bank";
                StringBuilder body = new StringBuilder();
                body.append("Withdraw commands will use this alliance bank\n");
                body.append("To have another alliance/corporation use this bank as an offshore:\n");
                body.append("- You must be admin or econ on both discord servers\n");
                body.append("- On the other guild, use: " + CM.offshore.add.cmd.offshoreAlliance(offshoreAlliance.getAlliance_id() + "") + "\n\n");
                body.append("If this is an offshore, and you create a new alliance, you may use this command to set the new alliance (all servers offshoring here will be updated)");

                confirmButton.embed(title, body.toString()).send();
                return null;
            }
            root.addCoalition(offshoreAlliance.getAlliance_id(), Coalition.OFFSHORE);
            root.addCoalition(offshoreAlliance.getAlliance_id(), Coalition.OFFSHORING);
            return "Done! Set " + offshoreAlliance.getName() + "/" + offshoreAlliance.getId() + " as the designated bank for this server";
        }

        if (!offshoreDB.isOffshore()) {
            return "No offshore found for alliance: " + offshoreAlliance.getAlliance_id() + ". Are you sure that's a valid offshore setup with locutus?";
        }
        Boolean enabled = offshoreDB.getOrNull(GuildKey.PUBLIC_OFFSHORING);
        if (enabled != Boolean.TRUE && !Roles.ECON.has(user, offshoreDB.getGuild())) {
            Role role = Roles.ECON.toRole2(offshoreDB);
            String roleName = role == null ? "ECON" : role.getName();
            return "You do not have " + roleName + " on " + offshoreDB.getGuild() + ". Alternatively " + GuildKey.PUBLIC_OFFSHORING.getCommandMention() + " is not enabled on that guild.";
        }

        boolean hasAdmin = Roles.ECON.has(user, offshoreDB.getGuild());

        StringBuilder response = new StringBuilder();
        // check public offshoring is enabled
        synchronized (OffshoreInstance.BANK_LOCK) {
            Set<Integer> aaIds = root.getAllianceIds();

            try {
                root.addCoalition(offshoreAlliance.getAlliance_id(), Coalition.OFFSHORE);
                if (aaIds.isEmpty()) {
                    long id = root.getIdLong();
                    offshoreDB.addCoalition(id, Coalition.OFFSHORING);
                } else {
                    for (int aaId : aaIds) {
                        offshoreDB.addCoalition(aaId, Coalition.OFFSHORING);
                    }
                }


                OffshoreInstance offshoreInstance = offshoreDB.getOffshore();
                Map<NationOrAllianceOrGuild, double[]> depoByAccount = offshoreInstance.getDepositsByAA(root, f -> true, true);

                boolean hasDepoToReset = depoByAccount.values().stream().anyMatch(depo -> !ResourceType.isZero(depo));
                if (hasDepoToReset && !hasAdmin && importAccount) {
                    throw new IllegalArgumentException("Missing " + Roles.ADMIN.toDiscordRoleNameElseInstructions(offshoreDB.getGuild()));
                }

                if (hasDepoToReset && !newAccount && !importAccount) {
                    String title = "Reset Balance for " + root.getName();
                    StringBuilder body = new StringBuilder("**__!! WARNING !!__**\n");
                    if (hasAdmin) {
                        body.append("Press `New` to make a new account with the offshore\n");
                        body.append("Press `Import` to import your previous account holdings\n");
                    } else {
                        body.append("> This will open a new account and reset your balance with the offshore.\nTo import your previous account holdings, please contact an administrator of the offshore");
                        body.append("\n\n**Offshore Owner:** <@" + offshoreDB.getGuild().getOwnerId() + ">\n");
                    }
                    body.append("**Offshore:** " + offshoreDB.getGuild().toString() + " | " + offshoreAlliance.getMarkdownUrl() + "\n");
                    for (Map.Entry<NationOrAllianceOrGuild, double[]> entry : depoByAccount.entrySet()) {
                        NationOrAllianceOrGuild account = entry.getKey();
                        double[] depo = entry.getValue();
                        body.append("**Resetting Accounts:**\n");
                        if (!ResourceType.isZero(depo)) {
                            body.append("- " + account.getQualifiedId() + " - worth: `~$" + MathMan.format(ResourceType.convertedTotal(depo)) + "`\n");
                            body.append(" - Balance: `" + ResourceType.toString(depo) + "`\n");
                        }
                    }

                    JSONObject newAccountCmd = WebUtil.json(command).put("force", "true");

                    IMessageBuilder msg = io.create().embed(title, body.toString());
                    msg = msg.commandButton(newAccountCmd.put("newAccount", true), "New");
                    if (hasAdmin) {
                        JSONObject importAccountCmd = WebUtil.json(command).put("force", "true");
                        msg = msg.commandButton(importAccountCmd.put("importAccount", true), "Import");
                    }
                    msg.send();
                    return null;
                }

                for (Map.Entry<NationOrAllianceOrGuild, double[]> entry : depoByAccount.entrySet()) {
                    NationOrAllianceOrGuild account = entry.getKey();
                    if (!importAccount) {
                        double[] depo = entry.getValue();
                        if (!ResourceType.isZero(depo)) {
                            long tx_datetime = System.currentTimeMillis();
                            long receiver_id = 0;
                            int receiver_type = 0;
                            int banker = nation.getNation_id();

                            String note = "#deposit";
                            double[] amount = depo;
                            for (int i = 0; i < amount.length; i++) amount[i] = -amount[i];
                            if (!ResourceType.isZero(amount)) {
                                if (account.isGuild()) {
                                    offshoreDB.addTransfer(tx_datetime, account.asGuild().getIdLong(), account.getReceiverType(), receiver_id, receiver_type, banker, note, amount);
                                } else {
                                    offshoreDB.addTransfer(tx_datetime, account.asAlliance(), receiver_id, receiver_type, banker, note, amount);
                                }
                            }


                            MessageChannel output = offshoreDB.getResourceChannel(0);
                            if (output != null) {
                                String msg = "Added " + ResourceType.toString(amount) + " to " + account.getTypePrefix() + ":" + account.getName() + "/" + account.getIdLong();
                                RateLimitUtil.queue(output.sendMessage(msg));
                                response.append("Reset deposit for " + root.getGuild() + "\n");
                            }
                        }
                    }

                    response.append("Registered " + offshoreAlliance.getQualifiedId() + " as an offshore. See: https://github.com/xdnw/locutus/wiki/banking");
                    if (aaIds.isEmpty()) {
                        response.append("\n(Your guild id, and the id of your account with the offshore is `" + root.getIdLong() + "`)");
                    }
                    if (root.getOrNull(GuildKey.WAR_ALERT_FOR_OFFSHORES) == null) {
                        if (offshoreDB.getOrNull(GuildKey.PUBLIC_OFFSHORING) == Boolean.TRUE) {
                            GuildKey.WAR_ALERT_FOR_OFFSHORES.set(root, user, false);
                            response.append("\nNote: Offshore War alerts are disabled. Enable using: " + GuildKey.WAR_ALERT_FOR_OFFSHORES.getCommandObj(root, true));
                        } else {
                            response.append("\nNote: Disable offshore war alerts using: " + GuildKey.WAR_ALERT_FOR_OFFSHORES.getCommandObj(root, false));
                        }
                    }
                }



            } catch (Throwable e) {
                root.removeCoalition(offshoreAlliance.getAlliance_id(), Coalition.OFFSHORE);
                for (int aaId : aaIds) {
                    offshoreDB.removeCoalition(aaId, Coalition.OFFSHORING);
                }
                throw e;
            }
        }

        response.append("\nDone!");
        return response.toString();
    }
}