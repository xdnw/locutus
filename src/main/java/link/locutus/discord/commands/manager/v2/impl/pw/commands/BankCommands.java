package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.enums.AccessType;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.NationDepositLimit;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasOffshore;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.GuildHandler;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.event.Event;
import link.locutus.discord.pnw.*;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.Grant;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.sheet.templates.TransferSheet;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BankCommands {

    public static final Map<UUID, Grant> AUTHORIZED_TRANSFERS = new HashMap<>();
    public static Map<UUID, Map<NationOrAlliance, Map<ResourceType, Double>>> APPROVED_BULK_TRANSFER = new ConcurrentHashMap<>();

    @Command(desc = """
            Queue a transfer offshore (with authorization)
            `aa-warchest` is how much to leave in the AA bank - in the form `{money=1,food=2}`
            `#note` is what note to use for the transfer (defaults to deposit)""")
    @RolePermission(value = {Roles.MEMBER, Roles.ECON, Roles.ECON_STAFF}, alliance = true)
    @HasOffshore
    @IsAlliance
    public static String offshore(@Me User user, @Me GuildDB db, @Default DBAlliance to, @Default("{}") Map<ResourceType, Double> warchest, @Default NationOrAllianceOrGuild account) throws IOException {
        if (account != null && account.isNation()) {
            throw new IllegalArgumentException("You can't offshore into a nation. You can only offshore into an alliance or guild. Value provided: `Nation:" + account.getName() + "`");
        }
        boolean memberCanOffshore = db.getOrNull(GuildDB.Key.MEMBER_CAN_OFFSHORE) == Boolean.TRUE;
        Roles checkRole = memberCanOffshore && account != null ? Roles.MEMBER : Roles.ECON_STAFF;

        AllianceList allianceList = checkRole.getAllianceList(user, db);
        if (allianceList == null || allianceList.isEmpty()) {
            StringBuilder msg = new StringBuilder("Missing Role: " + checkRole.toDiscordRoleNameElseInstructions(db.getGuild()));
            if (memberCanOffshore && account != null) {
                msg.append(". You do not have permission to specify an alternative account.");
            }
            if (!memberCanOffshore) {
                msg.append(". See also: ").append(CM.settings.cmd.toSlashMention()).append(" with key ").append(GuildDB.Key.MEMBER_CAN_OFFSHORE);
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
            if (!offshores.contains(to.getAlliance_id()))
                return "Please add the offshore using " + CM.coalition.add.cmd.create(to.getQualifiedName(), Coalition.OFFSHORE.name()) + "";
        }
        Set<DBAlliance> alliances = allianceList.getAlliances();
        if (alliances.size() == 1 && alliances.iterator().next().equals(to)) {
            throw new IllegalArgumentException("You cannot offshore to yourself");
        }

        List<String> results = new ArrayList<>();
        for (DBAlliance from : alliances) {
            if (from.getAlliance_id() == to.getAlliance_id()) continue;
            Map<ResourceType, Double> resources = from.getStockpile();
            for (Map.Entry<ResourceType, Double> entry : resources.entrySet()) {
                double newAmount = Math.max(0, entry.getValue() - warchest.getOrDefault(entry.getKey(), 0d));
                entry.setValue(newAmount);
            }
            OffshoreInstance bank = from.getBank();
            String note;
            if (account != null) {
                note = account.isAlliance() ? "#alliance=" + account.getId() : "#guild=" + account.getIdLong();
            } else {
                note = "#alliance=" + from.getAlliance_id();
            }
            note += " #tx_id=" + UUID.randomUUID();
            Map.Entry<OffshoreInstance.TransferStatus, String> response = bank.transferUnsafe(null, to, resources, note);
            results.add(from.getName() + " -> " + to.getName() + ": " + response.getKey() + " (" + response.getValue() + ")");
        }

        return "Offshored:\n - " + String.join("\n - ", results);
    }

    @Command(desc = "Disburse funds", aliases = {"disburse", "disperse"})
    @RolePermission(value = {Roles.ECON_WITHDRAW_SELF, Roles.ECON}, alliance = true, any = true)
    @IsAlliance
    public static String disburse(@Me User author, @Me GuildDB db, @Me IMessageIO io, @Me DBNation me,
                                  NationList nationList,
                                  @Range(min = 0, max = 7) double daysDefault,
                                  @Default("#tax") DepositType.DepositTypeInfo depositType,
                                  @Switch("d") boolean noDailyCash,
                                  @Switch("c") boolean noCash,

                                  @Switch("n") DBNation depositsAccount,
                                  @Switch("a") DBAlliance useAllianceBank,
                                  @Switch("o") DBAlliance useOffshoreAccount,
                                  @Switch("t") TaxBracket taxAccount,
                                  @Switch("ta") boolean existingTaxAccount,
                                  @Switch("e") @Timediff Long expire,
                                  @Switch("m") boolean convertToMoney,
                                  @Switch("b") boolean bypassChecks,
                                  @Switch("f") boolean force) throws GeneralSecurityException, IOException, ExecutionException, InterruptedException {
        Set<DBNation> nations = new HashSet<>(nationList.getNations());

        AllianceList allianceList = db.getAllianceList();
        for (DBNation nation : nations) {
            if (!allianceList.contains(nation.getAlliance())) {
                throw new IllegalArgumentException("This guild is registered to: " + StringMan.getString(allianceList.getIds()) +
                        " but you are trying to disburse to a nation in " + nation.getAlliance_id() +
                        "\nConsider using a different list of nations or registering the alliance to this guild (" +
                        CM.settings.cmd.toSlashMention() + " with key `" + GuildDB.Key.ALLIANCE_ID.name() + "`)");
            }
        }

        List<String> output = new ArrayList<>();
        List<String> errors = new ArrayList<>();

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
                else if (!force) {
                    if (nation.active_m() > 2880) {
                        status = OffshoreInstance.TransferStatus.INACTIVE;
                        debug += " (2+ days)";
                    }
                    if (nation.isGray()) status = OffshoreInstance.TransferStatus.GRAY;
                    if (nation.isBeige() && nation.getCities() <= 4) status = OffshoreInstance.TransferStatus.BEIGE;
                    if (status != OffshoreInstance.TransferStatus.SUCCESS)
                        debug += " (use the `force` parameter to override)";
                }
                if (status != OffshoreInstance.TransferStatus.SUCCESS) {
                    iter.remove();
                    errors.add(nation.getName() + "\t" + status.name() + "\t" + status.getMessage() + debug);
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
            if (!errors.isEmpty()) {
                msg.file("errors.csv", StringMan.join(errors, "\n"));
            }
            msg.send();
            return null;
        }

        Map<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> funds = allianceList.calculateDisburse(nations, daysDefault, true, false, true, noDailyCash, noCash, force);
        Map<DBNation, Map<ResourceType, Double>> fundsToSendNations = new LinkedHashMap<>();
        for (Map.Entry<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> entry : funds.entrySet()) {
            DBNation nation = entry.getKey();
            Map.Entry<OffshoreInstance.TransferStatus, double[]> value = entry.getValue();
            OffshoreInstance.TransferStatus status = value.getKey();
            double[] amount = value.getValue();
            if (status == OffshoreInstance.TransferStatus.SUCCESS) {
                fundsToSendNations.put(nation, PnwUtil.resourcesToMap(amount));
            } else {
                errors.add(nation.getName() + "\t" + status.name() + "\t" + status.getMessage());
            }
        }

        if (!errors.isEmpty() && !force) {
            msg.file("errors.csv", StringMan.join(errors, "\n"));
            msg.send();
        }

        if (fundsToSendNations.size() == 1) {
            Map.Entry<DBNation, Map<ResourceType, Double>> entry = fundsToSendNations.entrySet().iterator().next();
            DBNation nation = entry.getKey();
            Map<ResourceType, Double> transfer = entry.getValue();

            JSONObject command = CM.transfer.resources.cmd.create(
                    nation.getUrl(),
                    PnwUtil.resourcesToString(transfer),
                    depositType.toString(),
                    depositsAccount != null ? depositsAccount.getUrl() : null,
                    useAllianceBank != null ? useAllianceBank.getUrl() : null,
                    useOffshoreAccount != null ? useOffshoreAccount.getUrl() : null,
                    taxAccount != null ? taxAccount.getQualifiedName() : null,
                    existingTaxAccount + "",
                    Boolean.FALSE.toString(),
                    expire == null ? null : "timestamp:" + expire,
                    null,
                    String.valueOf(convertToMoney),
                    String.valueOf(bypassChecks),
                    null
            ).toJson();

            return transfer(io, command, author, me, db, nation, transfer, depositType, depositsAccount, useAllianceBank, useOffshoreAccount, taxAccount, existingTaxAccount, false, expire, null, convertToMoney, bypassChecks, force);
        } else {
            UUID key = UUID.randomUUID();
            TransferSheet sheet = new TransferSheet(db).write(fundsToSendNations, new LinkedHashMap<>()).build();
            APPROVED_BULK_TRANSFER.put(key, sheet.getTransfers());

            JSONObject command = CM.transfer.bulk.cmd.create(
                    sheet.getSheet().getURL(),
                    depositType.toString(),
                    depositsAccount != null ? depositsAccount.getUrl() : null,
                    useAllianceBank != null ? useAllianceBank.getUrl() : null,
                    useOffshoreAccount != null ? useOffshoreAccount.getUrl() : null,
                    taxAccount != null ? taxAccount.getQualifiedName() : null,
                    existingTaxAccount + "",
                    Boolean.FALSE.toString(),
                    expire == null ? null : ("timestamp:" + expire),
                    String.valueOf(force),
                    null,
                    key.toString()
            ).toJson();

            return transferBulk(io, command, author, me, db, sheet, depositType, depositsAccount, useAllianceBank, useOffshoreAccount, taxAccount, existingTaxAccount, expire, convertToMoney, bypassChecks, force, key);
        }
    }

    @Command(desc = "Transfer from the alliance bank (alliance deposits)")
    @RolePermission(value = {Roles.ECON, Roles.ECON_WITHDRAW_SELF}, any = true)
    public static String transfer(@Me IMessageIO channel, @Me JSONObject command,
                                  @Me User author, @Me DBNation me, @Me GuildDB guildDb, NationOrAlliance receiver, @AllianceDepositLimit Map<ResourceType, Double> transfer, DepositType.DepositTypeInfo depositType,

                                  @Switch("n") DBNation nationAccount,
                                  @Switch("a") DBAlliance senderAlliance,
                                  @Switch("o") DBAlliance allianceAccount,
                                  @Switch("t") TaxBracket taxAccount,
                                  @Switch("ta") boolean existingTaxAccount,

                                  @Switch("m") boolean onlyMissingFunds,
                                  @Switch("e") @Timediff Long expire,
                                  @Switch("g") UUID token,
                                  @Switch("c") boolean convertCash,

                                  @Switch("b") boolean bypassChecks,
                                  @Switch("f") boolean force) throws IOException {
        if (existingTaxAccount) {
            if (taxAccount != null)
                throw new IllegalArgumentException("You can't specify both `tax_id` and `existingTaxAccount`");
            if (!receiver.isNation())
                throw new IllegalArgumentException("You can only specify `existingTaxAccount` for a nation");
            taxAccount = receiver.asNation().getTaxBracket();
        }
        if (receiver.isAlliance() && onlyMissingFunds) {
            return "Option `-o` only applicable for nations";
        }

        if (receiver.isAlliance() && !receiver.asAlliance().exists()) {
            throw new IllegalArgumentException("Alliance: " + receiver.getUrl() + " has no receivable nations");
        }
        if (!receiver.isNation() && depositType.type != DepositType.IGNORE) {
            return "Please use `" + DepositType.IGNORE + "` as the depositType when transferring to alliances";
        }
        List<String> forceErrors = new ArrayList<>();
        if (receiver.isNation()) {
            DBNation nation = receiver.asNation();
            if (nation.getVm_turns() > 0) forceErrors.add("Receiver is in Vacation Mode");
            if (nation.isGray()) forceErrors.add("Receiver is Gray");
            if (nation.getNumWars() > 0 && receiver.asNation().isBlockaded()) forceErrors.add("Receiver is blockaded");
            if (nation.getActive_m() > 10000)
                forceErrors.add(("!! **WARN**: Receiver is " + TimeUtil.secToTime(TimeUnit.MINUTES, nation.active_m())) + " inactive");
        } else if (receiver.isAlliance()) {
            DBAlliance alliance = receiver.asAlliance();
            if (alliance.getNations(f -> f.getPositionEnum().id > Rank.HEIR.id && f.getVm_turns() == 0 && f.active_m() < 10000).size() == 0) {
                forceErrors.add("Alliance has no active leaders/heirs (are they in vacation mode?)");
            }
        }
        if (!forceErrors.isEmpty() && !bypassChecks) {
            String title = forceErrors.size() + " **ERRORS**!";
            String body = StringMan.join(forceErrors, "\n");
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
                    if (requirement.canOverride()) {
                    } else {
                        return "Failed requirement: " + requirement.getMessage();
                    }
                }
            }

            isGrant = true;
        }

        // transfer limit
        long userId = author.getIdLong();
        if (PnwUtil.convertedTotal(transfer) > 1000000000L
                && userId != Settings.INSTANCE.ADMIN_USER_ID
                && !Settings.INSTANCE.LEGACY_SETTINGS.WHITELISTED_BANK_USERS.contains(userId)
                && !isGrant
        ) {
            return "Transfer too large. Please specify a smaller amount";
        }

        Map<Long, AccessType> allowedIds = guildDb.getAllowedBankAccountsOrThrow(author, receiver, channel.getIdLong());

        // Filter allowed ids by access type

        if (onlyMissingFunds) {
            int aaId = receiver.getAlliance_id();
            if (aaId == 0) {
                return "Receiver is not in an alliance (cannot determine missing funds)";
            }
            AccessType accessType = allowedIds.get((long) aaId);
            if (accessType == null) {
                return "You do not have access to the alliance stockpile information for " + DBAlliance.getOrCreate(aaId).getQualifiedName();
            }
            if (me.getId() != receiver.getId()) {
                if (accessType != AccessType.ECON) {
                    return "You can only access stockpile information for yourself";
                }
            }
            Map<ResourceType, Double> existing = receiver.getStockpile();
            for (Map.Entry<ResourceType, Double> entry : transfer.entrySet()) {
                double toSend = Math.max(0, entry.getValue() - existing.getOrDefault(entry.getKey(), 0d));
                entry.setValue(toSend);
            }
        }

        Map.Entry<OffshoreInstance.TransferStatus, String> result;
        try {
            result = offshore.transferFromNationAccountWithRoleChecks(
                    author,
                    nationAccount,
                    allianceAccount,
                    taxAccount,
                    guildDb,
                    channel.getIdLong(),
                    receiver,
                    PnwUtil.resourcesToArray(transfer),
                    depositType,
                    expire,
                    null,
                    convertCash,
                    !force,
                    bypassChecks
            );
        } catch (IllegalArgumentException | IOException e) {
            result = new AbstractMap.SimpleEntry<>(OffshoreInstance.TransferStatus.OTHER, e.getMessage());
        }
        if (result.getKey() == OffshoreInstance.TransferStatus.CONFIRMATION) {
            String worth = "$" + MathMan.format(PnwUtil.convertedTotal(transfer));
            String title = "Send (worth: " + worth + ") to " + receiver.getTypePrefix() + ":" + receiver.getName();
            if (receiver.isNation()) {
                title += " | " + receiver.asNation().getAlliance();
            }
            channel.create().confirmation(title, result.getValue(), command, "force", "Send").cancelButton().send();
            return null;
        }

        return "**Transfer: " + result.getKey() + ":**\n" + result.getValue();
    }

    @Command(desc = "Send multiple transfers to nations/alliances according to a sheet",
            help = "The transfer sheet columns must be `nations` (which has the nations or alliance name/id/url), " +
                    "and then there must be a column named for each resource type you wish to transfer")
    @RolePermission(value = {Roles.ECON_WITHDRAW_SELF, Roles.ECON}, alliance = true, any = true)
    public static String transferBulk(@Me IMessageIO io, @Me JSONObject command, @Me User user, @Me DBNation me, @Me GuildDB db, TransferSheet sheet, DepositType.DepositTypeInfo depositType,
                                      @Switch("n") DBNation depositsAccount,
                                      @Switch("a") DBAlliance useAllianceBank,
                                      @Switch("o") DBAlliance useOffshoreAccount,
                                      @Switch("t") TaxBracket taxAccount,
                                      @Switch("ta") boolean existingTaxAccount,
                                      @Switch("e") @Timediff Long expire,
                                      @Switch("m") boolean convertToMoney,
                                      @Switch("b") boolean bypassChecks,
                                      @Switch("f") boolean force,
                                      @Switch("k") UUID key) throws IOException {
        return transferBulkWithErrors(io, command, user, me, db, sheet, depositType, depositsAccount, useAllianceBank, useOffshoreAccount, taxAccount, existingTaxAccount, expire, convertToMoney, bypassChecks, force, key, new HashMap<>());
    }

    public static String transferBulkWithErrors(@Me IMessageIO io, @Me JSONObject command, @Me User user, @Me DBNation me, @Me GuildDB db, TransferSheet sheet, DepositType.DepositTypeInfo depositType,
                                                @Switch("n") DBNation depositsAccount,
                                                @Switch("a") DBAlliance useAllianceBank,
                                                @Switch("o") DBAlliance useOffshoreAccount,
                                                @Switch("t") TaxBracket taxAccount,
                                                @Switch("ta") boolean existingTaxAccount,
                                                @Switch("e") @Timediff Long expire,
                                                @Switch("m") boolean convertToMoney,
                                                @Switch("b") boolean bypassChecks,
                                                @Switch("f") boolean force,
                                                @Switch("k") UUID key,
                                                Map<NationOrAlliance, String> errors) {
        if (existingTaxAccount) {
            if (taxAccount != null)
                throw new IllegalArgumentException("You can't specify both `tax_id` and `existingTaxAccount`");
        }
        double totalVal = 0;

        int nations = 0;
        int alliances = 0;

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
            IMessageBuilder msg = io.create();
            desc.append("Total: `").append(PnwUtil.resourcesToString(totalRss)).append("`\n");
            desc.append("Note: `").append(depositType).append("`\n\n");
            sheet.getSheet().attach(msg, desc, true, desc.length());

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
                    System.out.println(nationOrAlliance + " | " + amtA + " | " + amtB);
                    return "The confirmed amount does not match. Please try again";
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

        Map<ResourceType, Double> totalSent = new HashMap<>();

        StringBuilder output = new StringBuilder();
        for (Map.Entry<NationOrAlliance, Map<ResourceType, Double>> entry : transfers.entrySet()) {
            NationOrAlliance receiver = entry.getKey();
            double[] amount = PnwUtil.resourcesToArray(entry.getValue());

            Map.Entry<OffshoreInstance.TransferStatus, String> result = null;
            TaxBracket taxAccountFinal = taxAccount;
            if (existingTaxAccount) {
                if (!receiver.isNation()) {
                    result = new AbstractMap.SimpleEntry<>(OffshoreInstance.TransferStatus.INVALID_DESTINATION, "Cannot use `existingTaxAccount` for transfers to alliances");
                } else {
                    taxAccountFinal = receiver.asNation().getTaxBracket();
                }
            }

            if (result == null) {
                try {
                    result = offshore.transferFromNationAccountWithRoleChecks(
                            user,
                            depositsAccount,
                            useOffshoreAccount,
                            taxAccountFinal,
                            db,
                            io.getIdLong(),
                            receiver,
                            amount,
                            depositType,
                            expire,
                            null,
                            convertToMoney,
                            false,
                            bypassChecks
                    );
                } catch (IllegalArgumentException | IOException e) {
                    result = new AbstractMap.SimpleEntry<>(OffshoreInstance.TransferStatus.OTHER, e.getMessage());
                }
            }

            output.append(receiver.getUrl()).append("\t").append(receiver.isAlliance()).append("\t").append(StringMan.getString(amount)).append("\t").append(result.getKey()).append("\t").append("\"").append(result.getValue()).append("\"");
            output.append("\n");
            if (result.getKey() == OffshoreInstance.TransferStatus.SUCCESS || result.getKey() == OffshoreInstance.TransferStatus.ALLIANCE_BANK) {
                totalSent = PnwUtil.add(totalSent, PnwUtil.resourcesToMap(amount));

                io.send(PnwUtil.resourcesToString(amount) + " -> " + receiver.getUrl() + " | **" + result.getKey() + "**: " + result.getValue());
            }
        }

        for (Map.Entry<NationOrAlliance, String> entry : errors.entrySet()) {
            NationOrAlliance receiver = entry.getKey();
            output.append(receiver.getUrl()).append("\t").append(receiver.isAlliance()).append("\t").append("\t").append(OffshoreInstance.TransferStatus.OTHER).append("\t").append("\"").append(entry.getValue()).append("\"");
            output.append("\n");
        }


        io.create().file("transfer-results.csv", output.toString()).append("Done!\nTotal sent: `" + PnwUtil.resourcesToString(totalSent) + "` worth: ~$" + MathMan.format(PnwUtil.convertedTotal(totalSent))).send();
        return null;
    }

    @Command(desc = "Generate csv of war cost by nation between alliances (for reimbursement)\n" +
            "Filters out wars where nations did not perform actions")
    @RolePermission(Roles.ADMIN)
    public String warReimburseByNationCsv(Set<DBAlliance> allies, Set<DBAlliance> enemies, @Timestamp long cutoff, boolean removeWarsWithNoDefenderActions) {
        Set<Integer> allyIds = allies.stream().map(DBAlliance::getAlliance_id).collect(Collectors.toSet());
        Set<Integer> enemyIds = enemies.stream().map(DBAlliance::getAlliance_id).collect(Collectors.toSet());

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
            PnwUtil.add(total, cost);
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
            if (nation == null || !allies.contains(nation.getAlliance_id()) || nation.getPosition() <= 1 || nation.getVm_turns() > 0 || nation.getActive_m() > 7200 || nation.getCities() < 10)
                continue;
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
        if (me.getNation_id() != receiver.getNation_id() && !Roles.ECON_STAFF.has(author, db.getGuild())) {
            return "You do not have permisssion to send to other nations";
        }
        if (db.getOrNull(GuildDB.Key.RESOURCE_REQUEST_CHANNEL) == null) {
            return "No resource request channel set. See " + CM.settings.cmd.create(GuildDB.Key.RESOURCE_REQUEST_CHANNEL.name(), null, null, null) + "";
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

    @Command(desc = "Queue funds to be disbursed when your blockade lifts", aliases = {"queueDisburse", "qdisburse"})
    @RolePermission(Roles.MEMBER)
    @IsAlliance
    @HasOffshore
    public String escrowDisburse(@Me GuildDB db, @Me User author, @Me DBNation me, DBNation receiver, @Range(min = 1, max = 10) int days, @Timediff long expireAfter) throws IOException {
        if (days <= 0) return "Days must be positive";
        if (me.getNation_id() != receiver.getNation_id() && !Roles.ECON_STAFF.has(author, db.getGuild())) {
            return "You do not have permisssion to disburse to other nations";
        }
        if (db.getOrNull(GuildDB.Key.RESOURCE_REQUEST_CHANNEL) == null) {
            return "No resource request channel set. See " + CM.settings.cmd.create(GuildDB.Key.RESOURCE_REQUEST_CHANNEL.name(), null, null, null) + "";
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
    @RolePermission(value = {Roles.ECON_STAFF, Roles.ECON})
    @IsAlliance
    public String revenueSheet(@Me IMessageIO io, @Me GuildDB db, Set<DBNation> nations, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException, ExecutionException, InterruptedException {
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
            row.set(1, nation.getTax_id() + "");
            row.set(2, nation.getCities() + "");
            row.set(3, MathMan.format(nation.getAvg_infra()));
            row.set(4, MathMan.format(nation.getAvgLand()));
            row.set(5, MathMan.format(nation.getAvgBuildings()));
            row.set(6, MathMan.format(disease));
            row.set(7, MathMan.format(crime));
            row.set(8, MathMan.format(pollution));
            row.set(9, MathMan.format(population));
            row.set(10, "=\"" + nation.getMMR() + "\"");
            row.set(11, "=\"" + nation.getMMRBuildingStr() + "\"");
            row.set(12, MathMan.format(revenueConverted));

            row.set(13, MathMan.format(100 * revenueRaw / revenueConverted));
            row.set(14, MathMan.format(100 * revenueManu / revenueConverted));
            row.set(15, MathMan.format(100 * revenueCommerce / revenueConverted));

            JavaCity city1 = cities.entrySet().iterator().next().getValue();

            double profit = city1.profitConvertedCached(nation.getContinent(), nation.getRads(), nation::hasProject, nation.getCities(), nation.getGrossModifier());
            JavaCity origin = new JavaCity(city1);
            origin.zeroNonMilitary();
            JavaCity optimal = origin.optimalBuild(nation, 0);
            double profitOptimal;
            if (optimal != null) {
                profitOptimal = optimal.profitConvertedCached(nation.getContinent(), nation.getRads(), nation::hasProject, nation.getCities(), nation.getGrossModifier());
            } else {
                profitOptimal = 0;
            }

            double optimalGain = profit >= profitOptimal ? 1 : profit / profitOptimal;

            row.set(16, MathMan.format(100 * optimalGain));


            int i = 17;
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

        sheet.attach(io.create()).send();
        return null;
    }

    @Command(desc = "Get a sheet of members and their saved up warchest (can include deposits and potential revenue)")
    @RolePermission(value = {Roles.ECON_STAFF, Roles.ECON, Roles.MILCOM, Roles.MILCOM_NO_PINGS})
    @IsAlliance
    public String warchestSheet(@Me GuildDB db, @Me IMessageIO io, Set<DBNation> nations, @Switch("c") Map<ResourceType, Double> perCityWarchest, @Arg("Excess resources in AA bank that could be used to supplementWarchest") @Switch("b") Map<ResourceType, Double> allianceBankWarchest, @Switch("g") boolean includeGrants, @Switch("n") boolean doNotNormalizeDeposits, @Switch("d") boolean ignoreDeposits, @Switch("e") boolean ignoreStockpileInExcess, @Switch("r") Integer includeRevenueDays, @Switch("f") boolean forceUpdate) throws IOException, GeneralSecurityException {
        AllianceList alliance = db.getAllianceList();
        Map<DBNation, Map<ResourceType, Double>> stockpiles = alliance.getMemberStockpile();

        List<String> errors = new ArrayList<>();

        Map<DBNation, double[]> warchestByNation = new HashMap<>();
        Map<DBNation, double[]> warchestLackingByNation = new HashMap<>();
        Map<DBNation, double[]> warchestExcessByNation = new HashMap<>();
        Map<DBNation, double[]> revenueByNation = new HashMap<>();
        Function<DBNation, Map<ResourceType, Double>> wcReqFunc = f -> perCityWarchest != null ? perCityWarchest : db.getPerCityWarchest(f);

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
                if (amt == 0) continue;
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
        response.append("Total Warchest: `").append(PnwUtil.resourcesToString(totalWarchest)).append("` worth: ~$").append(MathMan.format(PnwUtil.convertedTotal(totalWarchest))).append("\n");
        response.append("Net Warchest Req (warchest - requirements): `").append(PnwUtil.resourcesToString(totalNet)).append("` worth: ~$").append(MathMan.format(PnwUtil.convertedTotal(totalNet)));

        sheet.attach(io.create(), response, false, 0).append(response.toString()).send();
        return null;
    }

    @Command(desc = "Withdraw from the alliance bank (your deposits)")
    @RolePermission(value = {Roles.ECON_WITHDRAW_SELF, Roles.ECON}, any = true)
    public String withdraw(@Me IMessageIO channel, @Me JSONObject command,
                           @Me User author, @Me DBNation me, @Me GuildDB guildDb, @NationDepositLimit Map<ResourceType, Double> transfer, @Default("#deposit") DepositType.DepositTypeInfo depositType,

                           @Switch("n") DBNation depositsAccount,
                           @Switch("a") DBAlliance useAllianceBank,
                           @Switch("o") DBAlliance useOffshoreAccount,
                           @Switch("t") TaxBracket taxAccount,
                           @Switch("ta") boolean existingTaxAccount,

                           @Switch("m") boolean onlyMissingFunds,
                           @Switch("e") @Timediff Long expire,
                           @Switch("g") UUID token,
                           @Switch("c") boolean convertCash,
                           @Switch("b") boolean bypassChecks,
                           @Switch("f") boolean force
    ) throws IOException {
        return transfer(channel, command, author, me, guildDb, me, transfer, depositType,
                depositsAccount == null ? me : depositsAccount,
                useAllianceBank,
                useOffshoreAccount,
                taxAccount,
                existingTaxAccount,
                onlyMissingFunds,
                expire,
                token,
                convertCash,
                bypassChecks, force);
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

        double[] deposits = depoByType.get(DepositType.DEPOSIT);
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
                if (tx.note == null || !tx.note.contains("#expire") || (tx.receiver_id != nation.getNation_id() && tx.sender_id != nation.getNation_id()))
                    continue;
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

    @Command(desc = "Sheet of projects each nation has")
    @RolePermission(value = {Roles.ECON, Roles.INTERNAL_AFFAIRS}, any = true)
    public String ProjectSheet(@Me IMessageIO io, @Me GuildDB db, Set<DBNation> nations, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
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

        sheet.attach(io.create()).send();
        return null;
    }

    @Command(aliases = {"depositSheet", "depositsSheet"}, desc =
            """
                    Get a list of nations and their deposits.
                    Add `-b` to use 0/0 as the tax base
                    Add `-o` to not include any manual deposit offsets
                    Add `-d` to not include deposits
                    Add `-t` to not include taxes
                    Add `-l` to not include loans
                    Add `-g` to not include grants`
                    Add `-p` to include past depositors
                    Add `-f` to force an update"""

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
                               @Switch("p") boolean includePastDepositors,
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
            Set<Integer> aaIds = db.getAllianceIds();
            if (!aaIds.isEmpty()) {
                nations = new LinkedHashSet<>(Locutus.imp().getNationDB().getNations(aaIds));
                nations.removeIf(n -> n.getPosition() <= 1);

                if (includePastDepositors) {
                    Set<Integer> ids = Locutus.imp().getBankDB().getReceiverNationIdFromAllianceReceivers(aaIds);
                    for (int id : ids) {
                        DBNation nation = Locutus.imp().getNationDB().getNation(id);
                        if (nation != null) nations.add(nation);
                    }
                }
            } else {
                if (includePastDepositors) {
                    throw new IllegalArgumentException("usePastDepositors is only implemented for alliances (ping borg)");
                }
                Role role = Roles.MEMBER.toRole(guild);
                if (role == null)
                    throw new IllegalArgumentException("No " + CM.settings.cmd.create(GuildDB.Key.ALLIANCE_ID.name(), null, null, null).toSlashCommand() + " set, or " +
                            "" + CM.role.setAlias.cmd.create(Roles.MEMBER.name(), "", null, null) + " set");
                nations = new LinkedHashSet<>();
                for (Member member : guild.getMembersWithRoles(role)) {
                    DBNation nation = DiscordUtil.getNation(member.getUser());
                    nations.add(nation);
                }
                if (nations.isEmpty()) return "No members found";

            }
        } else if (includePastDepositors) {
            throw new IllegalArgumentException("usePastDepositors cannot be set when nations are provided");
        }
        Set<Long> tracked = null;
        if (offshores != null) {
            tracked = new LinkedHashSet<>();
            for (DBAlliance aa : offshores) tracked.add((long) aa.getAlliance_id());
            tracked = PnwUtil.expandCoalition(tracked);
        }

        double[] aaTotalPositive = ResourceType.getBuffer();
        double[] aaTotalNet = ResourceType.getBuffer();

        boolean updateBulk = Settings.INSTANCE.TASKS.BANK_RECORDS_INTERVAL_SECONDS > 0;
        if (updateBulk) {
            Locutus.imp().getBankDB().updateBankRecs(Event::post);
        }

        long last = System.currentTimeMillis();
        for (DBNation nation : nations) {
            if (System.currentTimeMillis() - last > 5000) {
                IMessageBuilder tmp = msgFuture.getNow(null);
                if (tmp != null) msgFuture = tmp.clear().append("calculating for: " + nation.getNation()).send();
                last = System.currentTimeMillis();
            }
            Map<DepositType, double[]> deposits = nation.getDeposits(db, tracked, useTaxBase, useOffset, (updateBulk && !force) ? -1 : 0L, 0L);
            double[] buffer = ResourceType.getBuffer();

            header.set(0, MarkupUtil.sheetUrl(nation.getNation(), nation.getNationUrl()));
            header.set(1, nation.getCities());
            header.set(2, nation.getAgeDays());
            header.set(3, String.format("%.2f", PnwUtil.convertedTotal(deposits.getOrDefault(DepositType.DEPOSIT, buffer))));
            header.set(4, String.format("%.2f", PnwUtil.convertedTotal(deposits.getOrDefault(DepositType.TAX, buffer))));
            header.set(5, String.format("%.2f", PnwUtil.convertedTotal(deposits.getOrDefault(DepositType.LOAN, buffer))));
            header.set(6, String.format("%.2f", PnwUtil.convertedTotal(deposits.getOrDefault(DepositType.GRANT, buffer))));
            double[] total = ResourceType.getBuffer();
            for (Map.Entry<DepositType, double[]> entry : deposits.entrySet()) {
                switch (entry.getKey()) {
                    case GRANT -> {
                        if (noGrants) continue;
                    }
                    case LOAN -> {
                        if (noLoans) continue;
                    }
                    case TAX -> {
                        if (noTaxes) continue;
                    }
                    case DEPOSIT -> {
                        if (noDeposits) continue;
                    }
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

        StringBuilder footer = new StringBuilder();
        footer.append(PnwUtil.resourcesToFancyString(aaTotalPositive, "Nation Deposits (" + nations.size() + " nations)"));

        String type = "";
        OffshoreInstance offshore = db.getOffshore();
        double[] aaDeposits;
        if (offshore != null && offshore.getGuildDB() != db) {
            type = "offshored";
            aaDeposits = offshore.getDeposits(db);
        } else if (db.isValidAlliance()) {
            type = "bank stockpile";
            aaDeposits = PnwUtil.resourcesToArray(db.getAllianceList().getStockpile());
        } else aaDeposits = null;
        if (aaDeposits != null) {
            if (PnwUtil.convertedTotal(aaDeposits) > 0) {
                for (int i = 0; i < aaDeposits.length; i++) {
                    aaTotalNet[i] = aaDeposits[i] - aaTotalNet[i];
                    aaTotalPositive[i] = aaDeposits[i] - aaTotalPositive[i];

                }
            }
            footer.append("\n**Total ").append(type).append(" - nation deposits (negatives normalized)**:  Worth: $").append(MathMan.format(PnwUtil.convertedTotal(aaTotalPositive))).append("\n`").append(PnwUtil.resourcesToString(aaTotalPositive)).append("`");
            footer.append("\n**Total ").append(type).append(" - nation deposits**:  Worth: $").append(MathMan.format(PnwUtil.convertedTotal(aaTotalNet))).append("\n`").append(PnwUtil.resourcesToString(aaTotalNet)).append("`");
        }

        sheet.attach(channel.create()).embed("Nation Deposits (With Alliance)", footer.toString())
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
            response.append("Set withdraw limit of: ").append(nation.getNationUrl()).append(" to $").append(MathMan.format(limit)).append("\n");
        }
        response.append("Done!");
        return response.toString();
    }

    @Command(desc = "Set nation's internal taxrate\n" +
            "See also: `{prefix}SetTaxRate` and `{prefix}KeyStore TAX_BASE`")
    @RolePermission(value = Roles.ECON)
    public String setInternalTaxRate(@Me GuildDB db, Set<DBNation> nations, TaxRate taxRate) {
        if (taxRate.money < -1 || taxRate.money > 100 || taxRate.resources < -1 || taxRate.resources > 100)
            throw new IllegalArgumentException("Invalid taxrate: " + taxRate);

        AllianceList aa = db.getAllianceList();
        if (aa == null || aa.isEmpty())
            throw new IllegalArgumentException("This guild is not registered to an alliance");

        StringBuilder response = new StringBuilder();

        for (DBNation nation : nations) {
            if (!aa.contains(nation.getAlliance_id()))
                throw new IllegalArgumentException("Nation: " + nation.getNationUrl() + " is not in alliances: " + StringMan.getString(aa.getIds()));
            if (nation.getPosition() <= 1)
                throw new IllegalArgumentException("Nation: " + nation.getNationUrl() + " is not a member");
            db.setMeta(nation.getNation_id(), NationMeta.TAX_RATE, new byte[]{(byte) taxRate.money, (byte) taxRate.resources});
            response.append("Set ").append(nation.getNationUrl()).append(" internal taxrate to ").append(taxRate).append("\n");
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
    public String convertNegativeDeposits(@Me IMessageIO channel, @Me GuildDB db, @Me User user, @Me DBNation me, Set<DBNation> nations, @Default("manu,raws,food") List<ResourceType> negativeResources, @Default("money") ResourceType convertTo, @Switch("g") boolean includeGrants, @Switch("t") DepositType.DepositTypeInfo depositType, @Switch("f") Double conversionFactor, @Switch("s") SpreadSheet sheet, @Default() @Switch("n") String note) throws IOException, GeneralSecurityException {
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
                depo = depoByCategory.get(depositType.type);
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
                    amtAdd[convertTo.ordinal()] -= newAmt;
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
                note = depositType.toString();
            } else {
                note = "#deposit";
            }
        }

        CM.deposits.addSheet cmd = CM.deposits.addSheet.cmd.create(txSheet.getSheet().getURL(), note, null, null);

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

        if (onlyOffshoreTransfers && nationOrAllianceOrGuild.isNation())
            return "Only Alliance/Guilds can have an offshore account";

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
            Set<Integer> aaIds = db.getAllianceIds();
            OffshoreInstance offshore = db.getOffshore();
            if (aaIds.contains(alliance.getAlliance_id()) && offshore != null) {
                transactions.addAll(offshore.getTransactionsAA(aaIds, true));
            } else if (!aaIds.isEmpty() && db.getOffshore() != null && aaIds.contains(db.getOffshore().getAllianceId()) && offshore != null) {
                transactions.addAll(offshore.getTransactionsAA(alliance.getAlliance_id(), true));
            } else {
                List<Transaction2> txToAdd = (db.getTransactionsById(alliance.getAlliance_id(), 2));
                boolean hasAdmin = Roles.ECON.hasOnRoot(user); // TODO or has admin in alliance server ?
                if (!aaIds.contains(alliance.getAlliance_id()) && !hasAdmin) {
                    txToAdd.removeIf(f -> f.receiver_type != 1 && f.sender_type != 1);
                }
                transactions.addAll(txToAdd);
            }

            if (onlyOffshoreTransfers) {
                if (offshore == null) return "This alliance does not have an offshore account";
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

    @Command
    @RolePermission(value = Roles.ADMIN)
    public String unlockTransfers(@Me GuildDB db, @Me User user, NationOrAllianceOrGuild alliance) {
        if (alliance.isNation()) {
            return "You can only unlock transfers for an alliance or guild";
        }
        OffshoreInstance offshore = db.getOffshore();
        if (offshore == null) return "No offshore is set";
        if (offshore.getGuildDB() != db) return "Please run in the offshore server";
        Set<Long> coalition = offshore.getGuildDB().getCoalitionRaw(Coalition.FROZEN_FUNDS);
        if (alliance.isAlliance()) {
            if (coalition.contains((long) alliance.getAlliance_id()))
                return "Please use `!removecoalition FROZEN_FUNDS " + alliance.getAlliance_id() + "`";
            GuildDB otherDb = alliance.asAlliance().getGuildDB();
            if (otherDb != null && coalition.contains(otherDb.getIdLong()))
                return "Please use `!removecoalition FROZEN_FUNDS " + otherDb.getIdLong() + "`";
        } else {
            if (coalition.contains(alliance.getIdLong()))
                return "Please use `!removecoalition FROZEN_FUNDS " + alliance.getIdLong() + "`";
            for (int aaId : alliance.asGuild().getAllianceIds(true)) {
                if (coalition.contains((long) aaId)) return "Please use `!removecoalition FROZEN_FUNDS " + aaId + "`";
            }

        }

        if (alliance.isGuild()) {
            offshore.disabledGuilds.remove(alliance.asGuild().getIdLong());
        } else if (alliance.isAlliance()) {
            GuildDB guild = alliance.asAlliance().getGuildDB();
            if (guild == null) return "No guild found for AA:" + alliance;
            offshore.disabledGuilds.remove(guild.getIdLong());
        } else {
            return alliance + " must be a guild or alliance";
        }
        return "Done!";
    }

    @Command
    @IsAlliance
    @RolePermission(Roles.ECON_STAFF)
    public String setNationInternalTaxRates(@Me IMessageIO channel, @Me GuildDB db, @Default() Set<DBNation> nations, @Switch("p") boolean ping) throws Exception {
        if (nations == null) {
            nations = db.getAllianceList().getNations();
        }
        List<String> messages = new ArrayList<>();
        Map<DBNation, Map.Entry<TaxRate, String>> result = db.getHandler().setNationInternalTaxRate(nations, messages::add);
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
    @RolePermission(Roles.ECON_STAFF)
    public String listRequiredTaxRates(@Me IMessageIO io, @Me GuildDB db, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
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

        AllianceList alliances = db.getAllianceList();
        Map<Integer, TaxBracket> brackets = alliances.getTaxBrackets(false);

        Set<DBNation> nations = alliances.getNations();

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
        sheet.attach(io.create()).send();
        return null;
    }


    @Command
    @IsAlliance
    @RolePermission(Roles.ECON_STAFF)
    public String setNationTaxBrackets(@Me IMessageIO channel, @Me GuildDB db, @Default() Set<DBNation> nations, @Switch("p") boolean ping) throws Exception {
        if (nations == null) {
            nations = db.getAllianceList().getNations();
        }
        List<String> messages = new ArrayList<>();
        Map<DBNation, Map.Entry<TaxBracket, String>> result = db.getHandler().setNationTaxBrackets(nations, messages::add);
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
        if (offshore == null)
            return "No offshore is set in this guild: <https://docs.google.com/document/d/1QkN1FDh8Z8ENMcS5XX8zaCwS9QRBeBJdCmHN5TKu_l8/>";

        GuildDB receiverDB = Locutus.imp().getGuildDBByAA(receiver.getAlliance_id());
        if (receiverDB == null) return "Receiver is not in a guild with locutus";

        User receiverUser = receiver.getUser();
        if (receiverUser == null) return "Receiver is not verified";
        Member member = receiverDB.getGuild().getMember(receiverUser);
        if (receiver.getActive_m() > 1440) return "Receive is offline for >24 hours";
        if (!force && receiver.getNumWars() > 0 && (member == null || member.getOnlineStatus() != OnlineStatus.ONLINE))
            return "Receiver is not online on discord. (add `-f` to ignore this check)";

        Auth auth = receiver.getAuth(null);
        if (auth == null)
            return "Receiver is not authenticated with Locutus: " + CM.credentials.login.cmd.toSlashMention() + "";

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
    @IsAlliance
    public String taxDeposits(@Me IMessageIO io, @Me GuildDB db, Set<DBNation> nations, @Arg("Set to 0/0 to include all taxes") @Default() TaxRate baseTaxRate, @Default() @Timestamp Long startDate, @Default() @Timestamp Long endDate, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        Set<Integer> allianceIds = db.getAllianceIds();

        if (startDate == null) startDate = 0L;
        if (endDate == null) endDate = Long.MAX_VALUE;

        List<BankDB.TaxDeposit> taxes = new ArrayList<>();
        for (int allianceId : allianceIds) {
            taxes.addAll(Locutus.imp().getBankDB().getTaxesByAA(allianceId));
        }
        Map<Integer, double[]> totalByNation = new HashMap<>();

        int[] baseArr = baseTaxRate == null ? null : baseTaxRate.toArray();
        int[] aaBase = db.getOrNull(GuildDB.Key.TAX_BASE);

        for (BankDB.TaxDeposit tax : taxes) {
            if (tax.date < startDate || tax.date > endDate) continue;
            DBNation nation = DBNation.byId(tax.nationId);
            if (!nations.contains(nation)) continue;

            int[] internalRate = new int[]{tax.internalMoneyRate, tax.internalResourceRate};
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

        txSheet.getSheet().attach(io.create()).send();
        return null;
    }

    @Command(desc = "Get a sheet of a nation tax deposits over a period")
    @RolePermission(value = Roles.ECON)
    @IsAlliance
    public String taxRecords(@Me IMessageIO io, @Me GuildDB db, DBNation nation, @Default() @Timestamp Long startDate, @Default() @Timestamp Long endDate, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        Set<Integer> aaIds = db.getAllianceIds();

        if (startDate == null) startDate = 0L;
        if (endDate == null) endDate = Long.MAX_VALUE;

        List<BankDB.TaxDeposit> taxes = new ArrayList<>();
        for (int aaId : aaIds) {
            taxes.addAll(Locutus.imp().getBankDB().getTaxesPaid(nation.getNation_id(), aaId));
        }

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
        sheet.attach(io.create()).send();
        return null;
    }

    @Command(desc = "Send from your nation's deposits to another account (internal transfer)")
    @RolePermission(value = Roles.ECON)
    public String send(@Me OffshoreInstance offshore, @Me IMessageIO channel, @Me JSONObject command, @Me GuildDB senderDB, @Me User user, @Me DBAlliance alliance, @Me Rank rank, @Me DBNation me,
                       @AllianceDepositLimit Map<ResourceType, Double> amount,
                       NationOrAllianceOrGuild receiver,

                       @Default Guild receiverGuild,
                       @Default DBAlliance receiverAlliance,

                       @Default DBAlliance senderAlliance,

                       @Switch("f") boolean confirm) throws IOException {
        return "WIP";
    }

    @Command(desc = "Send from your alliance offshore account to another account (internal transfer)")
    @RolePermission(value = Roles.ECON)
    public String sendAA(@Me OffshoreInstance offshore, @Me IMessageIO channel, @Me JSONObject command, @Me GuildDB senderDB, @Me User user, @Me DBAlliance alliance, @Me Rank rank, @Me DBNation me,
                         @AllianceDepositLimit Map<ResourceType, Double> amount,
                         NationOrAllianceOrGuild receiver,
                         @Default DBAlliance senderAlliance,
                         @Default DBNation senderNation,
                         @Default Guild receiverGuild,
                         @Default DBAlliance receiverAlliance,
                         @Switch("f") boolean confirm) throws IOException {
        if (true) return "WIP";
        if (OffshoreInstance.DISABLE_TRANSFERS) throw new IllegalArgumentException("Error: Maintenance");
        if (receiverGuild != null && !receiver.isNation())
            throw new IllegalArgumentException("Cannot specify receiver guild when receiver is not a nation");
        if (receiverAlliance != null && !receiver.isNation())
            throw new IllegalArgumentException("Cannot specify receiver alliance when receiver is not a nation");

        GuildDB receiverDB = receiverGuild == null ? receiver.isGuild() ? receiver.asGuild() : null : Locutus.imp().getGuildDB(receiverGuild);
        DBNation receiverNation = receiver.isNation() ? receiver.asNation() : null;
        if (receiver.isAlliance()) receiverAlliance = receiver.asAlliance();
        if (receiver.isGuild()) receiverGuild = receiver.asGuild().getGuild();

        if (!confirm) {
            String title = "Send to " + receiver.getQualifiedName();
            String url = receiver.getUrl();
            if (url == null) url = receiver.getIdLong() + "";
            StringBuilder body = new StringBuilder();
            if (senderNation == null) {
                body.append("**Sending from the guild offshore**");
            } else {
                body.append("**Sending from ").append(senderNation.getNation()).append("'s deposits and the guild offshore**");
            }
            body.append("\nAmount: `").append(PnwUtil.resourcesToString(amount)).append("`");
            body.append("\nWorth: ~$").append(MathMan.format(PnwUtil.convertedTotal(amount)));
            body.append("\nSender DB: ").append(senderDB.getGuild());
            body.append("\nSender Offshore: ").append(PnwUtil.getMarkdownUrl(offshore.getAllianceId(), true));
            body.append("\nReceiver: ").append(MarkupUtil.markdownUrl(receiver.getQualifiedName(), url));
            if (receiverAlliance != null)
                body.append("\nReceiver AA: ").append(MarkupUtil.markdownUrl(receiverAlliance.getName(), receiverAlliance.getUrl()));
            if (receiverGuild != null) body.append("\nReceiver Guild: ").append(receiverGuild.toString());
            body.append("\n\nPress `Confirm` to confirm");

            channel.create().confirmation(title, body.toString(), command, "confirm").send();
            return null;
        }

        double[] amountArr = PnwUtil.resourcesToArray(amount);
        if (senderDB.sendInternal(user, me, senderDB, senderAlliance, senderNation, receiverDB, receiverAlliance, receiverNation, amountArr)) {
            return "Sent `" + PnwUtil.resourcesToString(amount) + "` to " + receiver.getQualifiedName() + ". See: " + CM.deposits.check.cmd.toSlashMention();
        } else {
            return "Failed to transfer funds.";
        }
    }

    @Command(desc = "Calculate a nations deposits/loans/taxes")
    @RolePermission(Roles.MEMBER)
    public String deposits(@Me Guild guild, @Me GuildDB db, @Me IMessageIO channel, @Me DBNation me, @Me User author, @Me GuildHandler handler, NationOrAllianceOrGuildOrTaxid nationOrAllianceOrGuild,
                           @Switch("o") Set<DBAlliance> offshores,
                           @Switch("c") Long timeCutoff,
                           @Switch("b") boolean includeBaseTaxes,
                           @Switch("o") boolean ignoreInternalOffsets,
                           @Switch("t") Boolean showTaxesSeparately,
                           @Switch("d") boolean replyInDMs,
                           @Switch("e") boolean includeExpired,
                           @Switch("e") boolean includeIgnored
    ) throws IOException {
        if (timeCutoff == null) timeCutoff = 0L;
        Set<Long> offshoreIds = offshores == null ? null : offshores.stream().map(NationOrAllianceOrGuild::getIdLong).collect(Collectors.toSet());
        if (offshoreIds != null) offshoreIds = PnwUtil.expandCoalition(offshoreIds);

        boolean hasAdmin = Roles.ECON.has(author, guild);
        AllianceList allowed = Roles.ECON.getAllianceList(author, db);

        StringBuilder response = new StringBuilder();
        response.append("**").append(nationOrAllianceOrGuild.getQualifiedName()).append("**:\n");
        List<String> footers = new ArrayList<>();

        Map<DepositType, double[]> accountDeposits = new HashMap<>();

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
                    accountDeposits.put(DepositType.DEPOSIT, PnwUtil.resourcesToArray(stock));
                } else {
                    return "No offshore is set. In this server, use " + CM.coalition.add.cmd.create("AA:" + alliance.getAlliance_id(), Coalition.OFFSHORE.name()) + " and from the offshore server use " + CM.coalition.add.cmd.create("AA:" + alliance.getAlliance_id(), Coalition.OFFSHORING.name()) + "";
                }
            } else if (otherDb != db && offshore.getGuildDB() != db) {
                return "You do not have permisssion to check another alliance's deposits";
            } else {
                // txList
                double[] deposits = PnwUtil.resourcesToArray(offshore.getDeposits(alliance.getAlliance_id(), true));
                accountDeposits.put(DepositType.DEPOSIT, deposits);
            }
        } else if (nationOrAllianceOrGuild.isGuild()) {
            GuildDB otherDb = nationOrAllianceOrGuild.asGuild();
            OffshoreInstance offshore = otherDb.getOffshore();
            if (offshore == null)
                return "No offshore is set. In this server, use " + CM.coalition.add.cmd.create(nationOrAllianceOrGuild.getIdLong() + "", Coalition.OFFSHORE.name()) + " and from the offshore server use " + CM.coalition.add.cmd.create(nationOrAllianceOrGuild.getIdLong() + "", Coalition.OFFSHORING.name()) + "";

            if (!Roles.ECON.has(author, offshore.getGuildDB().getGuild()) && !Roles.ECON.has(author, otherDb.getGuild())) {
                return "You do not have permission to check another guild's deposits";
            }
            // txList
            double[] deposits = offshore.getDeposits(otherDb);
            accountDeposits.put(DepositType.DEPOSIT, deposits);

        } else if (nationOrAllianceOrGuild.isNation()) {
            DBNation nation = nationOrAllianceOrGuild.asNation();
            if (nation != me && !Roles.INTERNAL_AFFAIRS.has(author, guild) && !Roles.INTERNAL_AFFAIRS_STAFF.has(author, guild) && !Roles.ECON.has(author, guild))
                return "You do not have permission to check other nation's deposits";
            // txList
            accountDeposits = nation.getDeposits(db, offshoreIds, !includeBaseTaxes, !ignoreInternalOffsets, 0L, timeCutoff, includeIgnored, includeExpired, f -> true);
        } else if (nationOrAllianceOrGuild.isTaxid()) {
            TaxBracket bracket = nationOrAllianceOrGuild.asBracket();
            Map<DepositType, double[]> deposits = db.getTaxBracketDeposits(bracket.taxId, timeCutoff, includeExpired, includeIgnored);
            accountDeposits.putAll(deposits);
        }

        double[] total = new double[ResourceType.values.length];
        double[] totalNoGrants = new double[ResourceType.values.length];
        double[] taxAndDeposits = new double[ResourceType.values.length];
        Map<DepositType, double[]> categorized = new HashMap<>();

        for (Map.Entry<DepositType, double[]> entry : accountDeposits.entrySet()) {
            DepositType type = entry.getKey();
            double[] existing = categorized.computeIfAbsent(type, f -> new double[ResourceType.values.length]);
            double[] current = entry.getValue();

            for (int i = 0; i < existing.length; i++) {
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

        if (showTaxesSeparately == Boolean.TRUE || (showTaxesSeparately == null && db.getOrNull(GuildDB.Key.DISPLAY_ITEMIZED_DEPOSITS) == Boolean.TRUE)) {
            if (categorized.containsKey(DepositType.DEPOSIT)) {
                response.append("#DEPOSIT: (worth $").append(MathMan.format(PnwUtil.convertedTotal(categorized.get(DepositType.DEPOSIT)))).append(")");
                response.append("\n```").append(PnwUtil.resourcesToString(categorized.get(DepositType.DEPOSIT))).append("``` ");
            }
            if (categorized.containsKey(DepositType.TAX)) {
                response.append("#TAX (worth $").append(MathMan.format(PnwUtil.convertedTotal(categorized.get(DepositType.TAX)))).append(")");
                response.append("\n```").append(PnwUtil.resourcesToString(categorized.get(DepositType.TAX))).append("``` ");
            }
            if (categorized.containsKey(DepositType.LOAN)) {
                response.append("#LOAN/#GRANT (worth $").append(MathMan.format(PnwUtil.convertedTotal(categorized.get(DepositType.LOAN)))).append(")");
                response.append("\n```").append(PnwUtil.resourcesToString(categorized.get(DepositType.LOAN))).append("``` ");
            }
            if (categorized.containsKey(DepositType.GRANT)) {
                response.append("#EXPIRE (worth $").append(MathMan.format(PnwUtil.convertedTotal(categorized.get(DepositType.GRANT)))).append(")");
                response.append("\n```").append(PnwUtil.resourcesToString(categorized.get(DepositType.GRANT))).append("``` ");
            }
            if (categorized.size() > 1) {
                response.append("Total: (worth: $").append(MathMan.format(PnwUtil.convertedTotal(total))).append(")");
                response.append("\n```").append(PnwUtil.resourcesToString(total)).append("``` ");
            }
        } else {
            String totalTitle = "Total (`#expire`|`#loan`|`#tax`|`#deposit`: worth $";
            String noGrantTitle = "Excluding `#expire` (worth: $";
            String safekeepTitle = "Safekeep (`#tax`|`#deposit`: worth $";
            boolean hasPriorCategory = false;
            if (categorized.containsKey(DepositType.GRANT)) {
                response.append(totalTitle).append(MathMan.format(PnwUtil.convertedTotal(total))).append(")");
                response.append("\n```").append(PnwUtil.resourcesToString(total)).append("``` ");
                footers.add("Unlike loans, debt from grants will expire if you stay (see the transaction for the timeframe)");
                hasPriorCategory = true;
            }
            if (categorized.containsKey(DepositType.LOAN)) {
                response.append(hasPriorCategory ? noGrantTitle : totalTitle).append(MathMan.format(PnwUtil.convertedTotal(totalNoGrants))).append(")");
                response.append("\n```").append(PnwUtil.resourcesToString(totalNoGrants)).append("``` ");
                hasPriorCategory = true;
            }

            response.append(hasPriorCategory ? safekeepTitle : totalTitle).append(MathMan.format(PnwUtil.convertedTotal(taxAndDeposits))).append(")");
            response.append("\n```").append(PnwUtil.resourcesToString(taxAndDeposits)).append("``` ");
        }
        if (me != null && nationOrAllianceOrGuild == me) {
            footers.add("Funds default to #deposit if no other note is used");
            if (Boolean.TRUE.equals(db.getOrNull(GuildDB.Key.RESOURCE_CONVERSION))) {
                footers.add("You can sell resources to the alliance by depositing with the note #cash");
            }
            if (PnwUtil.convertedTotal(total) > 0 && Boolean.TRUE.equals(db.getOrNull(GuildDB.Key.MEMBER_CAN_WITHDRAW))) {
                Role role = Roles.ECON_WITHDRAW_SELF.toRole(db.getGuild());
                if (Objects.requireNonNull(db.getGuild().getMember(author)).getRoles().contains(role)) {
                    footers.add("To withdraw, use: `" + CM.transfer.self.cmd.toSlashMention() + "` ");
                }
            }
        }

        for (int i = 0; i < footers.size(); i++) {
            String footer = footers.get(i);
            response.append("\n`note").append(i == 0 ? "" : i).append(": ").append(footer).append("`");
        }

        IMessageIO output = replyInDMs ? new DiscordChannelIO(author.openPrivateChannel().complete(), null) : channel;
        CompletableFuture<IMessageBuilder> msgFuture = output.send(response.toString());

        if (me != null && nationOrAllianceOrGuild.isNation() && nationOrAllianceOrGuild.asNation().getPosition() > 1 && db.isWhitelisted() && db.getOrNull(GuildDB.Key.API_KEY) != null && db.getAllianceIds(true).contains(nationOrAllianceOrGuild.asNation().getAlliance_id())) {
            DBNation finalNation = nationOrAllianceOrGuild.asNation();
            Locutus.imp().getExecutor().submit(() -> {
                List<String> tips2 = new ArrayList<>();
                StringBuilder append = new StringBuilder();
                Map<ResourceType, Double> stockpile = finalNation.getStockpile();
                if (stockpile != null && !stockpile.isEmpty() && stockpile.getOrDefault(ResourceType.CREDITS, 0d) != -1) {
                    Map<ResourceType, Double> excess = finalNation.checkExcessResources(db, stockpile);
                    if (!excess.isEmpty()) {
                        tips2.add("Excess can be deposited: " + PnwUtil.resourcesToString(excess));
                        if (Boolean.TRUE.equals(db.getOrNull(GuildDB.Key.DEPOSIT_INTEREST))) {
                            List<Transaction2> transactions = finalNation.getTransactions(-1);
                            long last = 0;
                            for (Transaction2 transaction : transactions)
                                last = Math.max(transaction.tx_datetime, last);
                            if (System.currentTimeMillis() - last > TimeUnit.DAYS.toMillis(5)) {
                                tips2.add("Deposit frequently to be eligible for interest on your deposits");
                            }
                        }
                    }
                }
                Map<ResourceType, Double> needed = finalNation.getResourcesNeeded(stockpile, 3, true);
                if (!needed.isEmpty()) {
                    tips2.add("Missing resources for the next 3 days: " + PnwUtil.resourcesToString(needed));
                }

                if (me.getNation_id() == finalNation.getNation_id() && Boolean.TRUE.equals(db.getOrNull(GuildDB.Key.MEMBER_CAN_OFFSHORE)) && db.isValidAlliance()) {
                    AllianceList alliance = db.getAllianceList();
                    if (alliance != null && !alliance.isEmpty() && alliance.contains(me.getAlliance_id())) {
                        try {
                            Map<ResourceType, Double> aaStockpile = me.getAlliance().getStockpile();
                            if (aaStockpile != null && PnwUtil.convertedTotal(aaStockpile) > 5000000) {
                                tips2.add("You MUST offshore funds after depositing `" + CM.offshore.send.cmd.toSlashMention() + "` ");
                            }
                        } catch (Throwable ignore) {
                        }
                    }
                }

                if (!tips2.isEmpty()) {
                    for (String tip : tips2) append.append("\n`tip: ").append(tip).append("`");

                    try {
                        msgFuture.get().append(append.toString()).send();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
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
        result.append("Principle Amount: $").append(MathMan.format(amount)).append("\n");
        result.append("Loan Interest Rate: ").append(MathMan.format(pct)).append("%\n");
        result.append("Total Interest: $").append(MathMan.format(totalInterest)).append("\n");
        result.append("Weekly Payments: $").append(MathMan.format(weeklyPayments)).append("\n");

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
        int day = calendar.get(Calendar.DAY_OF_WEEK);

        int dayOffset = 0;
        switch (day) {
            case Calendar.MONDAY, Calendar.TUESDAY -> dayOffset = Calendar.FRIDAY - day;
            case Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY ->
                    dayOffset += 7 + Calendar.FRIDAY - day;
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime due = now.plusDays(dayOffset);
        DateTimeFormatter pattern = DateTimeFormatter.ofPattern("EEEE dd MMMM", Locale.ENGLISH);

        result.append("Today: ").append(pattern.format(now)).append("\n");
        String repeating = pattern.format(due) + " and every Friday thereafter for a total of " + weeks + " weeks.";
        result.append("First Payment Due: ").append(repeating).append("```");

        return result.toString();
    }

    @Command(desc = "List all nations in the alliance and their current stockpile\n" +
            "Add `-n` to normalize it per city")
    @RolePermission(any = true, value = {Roles.ECON_STAFF, Roles.ECON})
    @IsAlliance
    public String stockpileSheet(@Me GuildDB db, @Default Set<DBNation> nationFilter, @Switch("n") boolean normalize, @Switch("e") boolean onlyShowExcess, @Switch("f") boolean forceUpdate, @Me IMessageIO channel) throws IOException, GeneralSecurityException {
        AllianceList alliance = db.getAllianceList();

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

        SpreadSheet sheet = SpreadSheet.create(db, GuildDB.Key.STOCKPILE_SHEET);
        sheet.setHeader(header);

        double[] aaTotal = ResourceType.getBuffer();

        if (forceUpdate) {
            alliance.updateCities();
        }

        for (Map.Entry<DBNation, Map<ResourceType, Double>> entry : stockpile.entrySet()) {
            List<Object> row = new ArrayList<>();

            DBNation nation = entry.getKey();
            if (nation == null || (nationFilter != null && !nationFilter.contains(nation))) continue;
            row.add(MarkupUtil.sheetUrl(nation.getNation(), nation.getNationUrl()));
            row.add(nation.getUserDiscriminator());
            row.add(nation.getCities());
            row.add(nation.getAvg_infra());
            row.add(nation.getOff() + "|" + nation.getDef());
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
        sheet.attach(channel.create().embed("Nation Stockpiles", totalStr)).send();
        return null;
    }

    @Command(desc = """
            Generate a sheet of member tax brackets.
            Add `-a` to include applicants
            Add `-f` to force an update of deposits
            `note: internal tax rate is the TAX_BASE and determines what % of their taxes is excluded from deposits`""")
    @RolePermission(any = true, value = {Roles.ECON, Roles.ECON_STAFF})
    public String taxBracketSheet(@Me IMessageIO io, @Me GuildDB db, @Switch("f") boolean force, @Switch("a") boolean includeApplicants) throws Exception {
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
            brackets = db.getAllianceList().getTaxBrackets(false);
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

        StringBuilder response = new StringBuilder();
        if (failedFetch)
            response.append("\nnote: Please set an api key with ").append(CM.credentials.addApiKey.cmd.toSlashMention()).append(" to view updated tax brackets");

        sheet.attach(io.create(), response.toString()).send();
        return null;
    }

    @Command
    @RolePermission(value = Roles.ADMIN)
    public String addOffshore(@Me IMessageIO io, @Me User user, @Me GuildDB root, @Me DBNation nation, DBAlliance offshoreAlliance, @Switch("f") boolean force) {
        if (nation.getAgeDays() < 100) return "Please contact <@664156861033086987> | borg#5729";
        if (root.isDelegateServer())
            return "Cannot enable offshoring for delegate server (run this command in the root server)";

        IMessageBuilder confirmButton = io.create().confirmation(CM.offshore.add.cmd.create(offshoreAlliance.getId() + "", null));
        GuildDB offshoreDB = offshoreAlliance.getGuildDB();

        if (offshoreDB != null) {
            if (!offshoreDB.hasAlliance()) {
                return "Please set the key " + CM.settings.cmd.create(GuildDB.Key.ALLIANCE_ID.name(), null, null, null).toSlashCommand() + " in " + offshoreDB.getGuild();
            }
            PoliticsAndWarV3 api = offshoreAlliance.getApi(AlliancePermission.WITHDRAW_BANK, AlliancePermission.VIEW_BANK);
            if (api == null) {
                return "Please set the key " + CM.settings.cmd.create(GuildDB.Key.API_KEY.name(), null, null, null).toSlashCommand() + " in " + offshoreDB.getGuild();
            }
        }

        if (root.isOffshore() && (offshoreDB == null || (offshoreDB == root))) {
            if (nation.getAlliance_id() != offshoreAlliance.getAlliance_id()) {
                throw new IllegalArgumentException("You must be in the provided alliance: " + offshoreAlliance.getId() + " to set the new ALLIANCE_ID for this offshore");
            }

            Set<Long> announceChannels = new HashSet<>();
            Set<Long> serverIds = new HashSet<>();
            if (nation.getNation_id() == Settings.INSTANCE.NATION_ID) {
                announceChannels.add(Settings.INSTANCE.DISCORD.CHANNEL.ADMIN_ALERTS);
            }

            Set<Long> alliancesOrGuilds = root.getCoalitionRaw(Coalition.OFFSHORING);
            outer:
            for (Long alliancesOrGuild : alliancesOrGuilds) {
                GuildDB other;
                if (alliancesOrGuild < Integer.MAX_VALUE) {
                    other = Locutus.imp().getGuildDBByAA(alliancesOrGuild.intValue());
                    if (other == null) {
                        continue;
                    }
                } else {
                    other = Locutus.imp().getGuildDB(alliancesOrGuild);
                }

                if (other == null) {
                    continue;
                }
                Set<Long> offshoreIds = other.getCoalitionRaw(Coalition.OFFSHORE);
                for (int id : root.getAllianceIds()) {
                    if (offshoreIds.contains((long) id)) {
                        serverIds.add(alliancesOrGuild);
                        continue outer;
                    }
                }
                if (offshoreIds.contains(root.getIdLong())) {
                    serverIds.add(alliancesOrGuild);
                }
            }


            Set<Integer> aaIds = root.getAllianceIds();
            Set<Integer> toUnregister = new HashSet<>();

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

                String body = "The alliances to this guild will be unregistered: `" + StringMan.getString(toUnregister) + "`\n" +
                        "The new alliance: `" + offshoreAlliance.getId() + " will be set ` (See: " + CM.settings.cmd.create(GuildDB.Key.ALLIANCE_ID.name(), null, null, null) + ")\n" +
                        "All other guilds using the prior alliance `" + StringMan.getString(toUnregister) + "` will be changed to use the new offshore";

                confirmButton.embed(title, body).send();
                return null;
            }

            Set<Integer> newIds = new HashSet<>(aaIds);
            newIds.removeAll(toUnregister);
            newIds.add(offshoreAlliance.getAlliance_id());
            root.setInfo(GuildDB.Key.ALLIANCE_ID, StringMan.join(newIds, ","));
            root.addCoalition(offshoreAlliance.getAlliance_id(), Coalition.OFFSHORE);
            root.addCoalition(offshoreAlliance.getAlliance_id(), Coalition.OFFSHORING);

            for (Long serverId : serverIds) {
                GuildDB db = Locutus.imp().getGuildDB(serverId);
                if (db == null) continue;
                db.addCoalition(offshoreAlliance.getAlliance_id(), Coalition.OFFSHORE);

                // Find the most stuited channel to post the announcement in
                MessageChannel channel = db.getResourceChannel(0);
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
            String body = PnwUtil.getMarkdownUrl(offshoreAlliance.getAlliance_id(), true);
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
                    } catch (InsufficientPermissionException ignore) {
                    }
                }
            }

            return "Done";
        }

        if (offshoreDB == null) {
            return "No guild found for alliance: " + offshoreAlliance.getAlliance_id() + ". To register a guild to an alliance: " + CM.settings.cmd.create(GuildDB.Key.ALLIANCE_ID.name(), offshoreAlliance.getAlliance_id() + "", null, null);
        }

        OffshoreInstance currentOffshore = root.getOffshore();
        if (currentOffshore != null) {
            if (currentOffshore.getAllianceId() == offshoreAlliance.getAlliance_id()) {
                return "This guild is already the offshore for this server";
            }
            Set<Integer> aaIds = root.getAllianceIds();

            if (!force) {
                String idStr = aaIds.isEmpty() ? root.getIdLong() + "" : StringMan.join(aaIds, ",");
                String title = "Replace current offshore";
                String body = "Changing offshores will close the account with your previous offshore provider\n" +
                        "Your current offshore is set to: " + currentOffshore.getAllianceId() + "\n" +
                        "To check your funds with the current offshore, use " +
                        CM.deposits.check.cmd.create(idStr, null, null, null, null, null, null, null, null) +
                        "\nIt is recommended to withdraw all funds from the current offshore before changing, as Locutus may not be able to access the account after closing it`";

                confirmButton.embed(title, body).send();
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
                String body = "Withdraw commands will use this alliance bank\n" +
                        "To have another alliance/corporation use this bank as an offshore:\n" +
                        " - You must be admin or econ on both discord servers\n" +
                        " - On the other guild, use: " + CM.offshore.add.cmd.create(offshoreAlliance.getAlliance_id() + "", null) + "\n\n" +
                        "If this is an offshore, and you create a new alliance, you may use this command to set the new alliance (all servers offshoring here will be updated)";

                confirmButton.embed(title, body).send();
                return null;
            }
            root.addCoalition(offshoreAlliance.getAlliance_id(), Coalition.OFFSHORE);
            root.addCoalition(offshoreAlliance.getAlliance_id(), Coalition.OFFSHORING);
            return "Done! Set " + offshoreAlliance.getName() + "/" + offshoreAlliance.getId() + " as the designated bank for this server";
        }

        if (!offshoreDB.isOffshore()) {
            return "No offshore found for alliance: " + offshoreAlliance.getAlliance_id() + ". Are you sure that's a valid offshore setup with locutus?";
        }
        Boolean enabled = offshoreDB.getOrNull(GuildDB.Key.PUBLIC_OFFSHORING);
        if (enabled != Boolean.TRUE && !Roles.ECON.has(user, offshoreDB.getGuild())) {
            Role role = Roles.ECON.toRole(offshoreDB);
            String roleName = role == null ? "ECON" : role.getName();
            return "You do not have " + roleName + " on " + offshoreDB.getGuild() + ". Alternatively " + CM.settings.cmd.create(GuildDB.Key.PUBLIC_OFFSHORING.name(), null, null, null) + " is not enabled on that guild.";
        }

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
                for (Map.Entry<NationOrAllianceOrGuild, double[]> entry : depoByAccount.entrySet()) {
                    NationOrAllianceOrGuild account = entry.getKey();
                    double[] depo = entry.getValue();
                    if (!ResourceType.isEmpty(depo)) {
                        long tx_datetime = System.currentTimeMillis();
                        long receiver_id = 0;
                        int receiver_type = 0;
                        int banker = nation.getNation_id();

                        String note = "#deposit";
                        for (int i = 0; i < depo.length; i++) depo[i] = -depo[i];
                        if (!ResourceType.isEmpty(depo)) {
                            if (account.isGuild()) {
                                offshoreDB.addTransfer(tx_datetime, account.asGuild().getIdLong(), account.getReceiverType(), receiver_id, receiver_type, banker, note, depo);
                            } else {
                                offshoreDB.addTransfer(tx_datetime, account.asAlliance(), receiver_id, receiver_type, banker, note, depo);
                            }
                        }


                        MessageChannel output = offshoreDB.getResourceChannel(0);
                        if (output != null) {
                            String msg = "Added " + PnwUtil.resourcesToString(depo) + " to " + account.getTypePrefix() + ":" + account.getName() + "/" + account.getIdLong();
                            RateLimitUtil.queue(output.sendMessage(msg));
                            response.append("Reset deposit for ").append(root.getGuild()).append("\n");
                        }
                    }

                    response.append("Registered ").append(offshoreAlliance.getQualifiedName()).append(" as an offshore. See: https://docs.google.com/document/d/1QkN1FDh8Z8ENMcS5XX8zaCwS9QRBeBJdCmHN5TKu_l8/edit");
                    if (aaIds.isEmpty()) {
                        response.append("\n(Your guild id, and the id of your account with the offshore is `").append(root.getIdLong()).append("`)");
                    }
                    if (root.getOrNull(GuildDB.Key.WAR_ALERT_FOR_OFFSHORES) == null) {
                        if (offshoreDB.getOrNull(GuildDB.Key.PUBLIC_OFFSHORING) == Boolean.TRUE) {
                            root.setInfo(GuildDB.Key.WAR_ALERT_FOR_OFFSHORES, "false");
                            response.append("\nNote: Offshore War alerts are disabled. Enable using: ").append(CM.settings.cmd.create(GuildDB.Key.WAR_ALERT_FOR_OFFSHORES.name(), "true", null, null));
                        } else {
                            response.append("\nNote: Disable offshore war alerts using: ").append(CM.settings.cmd.create(GuildDB.Key.WAR_ALERT_FOR_OFFSHORES.name(), "false", null, null));
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

        return response.toString();
    }
}