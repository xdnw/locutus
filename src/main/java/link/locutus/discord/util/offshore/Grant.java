package link.locutus.discord.util.offshore;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.EscrowMode;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.grant.AGrantTemplate;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.sheet.templates.TransferSheet;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Member;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Grant {
    public static final Map<Long, Map<UUID, Grant>> APPROVED_GRANTS_BY_GUILD = new ConcurrentHashMap<>();
    public static void addGrant(long db, UUID uuid, Grant grant) {
        APPROVED_GRANTS_BY_GUILD.computeIfAbsent(db, f -> new ConcurrentHashMap<>()).put(uuid, grant);
    }
    public static Grant getApprovedGrant(long guildId, UUID uuid) {
        return APPROVED_GRANTS_BY_GUILD.getOrDefault(guildId, Collections.emptyMap()).get(uuid);
    }

    public static Grant deleteApprovedGrant(long guildId, UUID uuid) {
        return APPROVED_GRANTS_BY_GUILD.getOrDefault(guildId, Collections.emptyMap()).remove(uuid);
    }

    private final long timestamp = System.currentTimeMillis();

    private final DBNation nation;

    private final Set<String> notes;

    private DepositType.DepositTypeInfo type;
    private String title;

    private Function<DBNation, double[]> cost;

    private final Set<Requirement> requirements;
    private final Set<Integer> cities;

    private String instructions;
    private String amount;
    private boolean allCities;

    public Grant setCities(Collection<Integer> cities) {
        cities.addAll(cities);
        return this;
    }

    public Grant addCity(int cityId) {
        cities.add(cityId);
        return this;
    }

    public long getDateCreated() {
        return timestamp;
    }

    public boolean isAllCities() {
        return allCities;
    }

    public Set<Integer> getCities() {
        return cities;
    }

    public List<Integer> getCitiesSorted() {
        List<Integer> sorted = new ArrayList<>(cities);
        Collections.sort(sorted);
        return sorted;
    }

    public Grant setAllCities() {
        this.allCities = true;
        for (Map.Entry<Integer, JavaCity> entry : nation.getCityMap(true).entrySet()) {
            cities.add(entry.getKey());
        }
        return this;
    }

    public static Set<Integer> getCityIdsBeforeDate(DBNation nation, long date) {
        Set<Integer> cities = new HashSet<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, JavaCity> entry : nation.getCityMap(false, false).entrySet()) {
            JavaCity city = entry.getValue();

            if (now - TimeUnit.DAYS.toMillis(city.getAgeDays()) < date) {
                cities.add(entry.getKey());
            }
        }
        return cities;
    }

    public static double getCityInfraGranted(DBNation nation, int cityId, Collection<Transaction2> transactions) {
        Map<Integer, Map<Long, Double>> infraGrants = Grant.getInfraGrantsByCityByDate(nation, transactions);
        Map<Integer, JavaCity> cityMap = nation.getCityMap(false, false);
        JavaCity city = cityMap.get(cityId);
        if (city == null) return Double.MAX_VALUE;
        Map<Long, Double> maxGrantedDate = infraGrants.getOrDefault(cityId, Collections.singletonMap(0L, 10d));
        double maxGranted = Collections.max(maxGrantedDate.values());
        maxGranted = Math.max(maxGranted, city.getRequiredInfra());
        maxGranted = Math.max(maxGranted, city.getInfra());
        return maxGranted;
    }

    public static Map<Integer, Double> getLandGrantedByCity(DBNation nation, Collection<Transaction2> transactions) {
        Map<Integer, Double> result = new HashMap<>();
        for (Transaction2 transaction : transactions) {
            if (transaction.note == null) continue;
            if (!transaction.note.toLowerCase().contains("#land")) continue;
            String landAmt = PW.parseTransferHashNotes(transaction.note).get("#land");
            if (landAmt != null) {
                try {
                    double amt = Double.parseDouble(landAmt);

                    Set<Integer> cities = getCities(nation, transaction.note, transaction.tx_datetime);
                    if (cities == null) {
                        cities = getCityIdsBeforeDate(nation, transaction.tx_datetime);
                    }
                    for (Integer cityId : cities) {
                        double max = Math.max(result.getOrDefault(cityId, 0d), amt);
                        result.put(cityId, max);
                    }
                } catch (NumberFormatException ignore) {}
            }
        }
        return result;
    }

    public static Map<Integer, Map<Long, Double>> getInfraGrantsByCityByDate(DBNation nation, Collection<Transaction2> transactions) {
        Map<Integer, Map<Long, Double>> result = new HashMap<>();

        for (Transaction2 transaction : transactions) {
            if (transaction.note == null) continue;
            if (!transaction.note.toLowerCase().contains("#infra")) continue;
            String infraAmt = PW.parseTransferHashNotes(transaction.note).get("#infra");
            if (infraAmt != null) {
                try {
                    double amt = Double.parseDouble(infraAmt);

                    Set<Integer> cities = getCities(nation, transaction.note, transaction.tx_datetime);
                    if (cities == null) {
                        cities = getCityIdsBeforeDate(nation, transaction.tx_datetime);
                    }
                    for (Integer cityId : cities) {
                        result.computeIfAbsent(cityId, f -> new HashMap<>()).put(transaction.tx_datetime, amt);
                    }
                } catch (NumberFormatException ignore) {}
            }
        }
        return result;
    }

    public static boolean hasGrantedCity(DBNation nation, Collection<Transaction2> transactions, int city) {
        Set<Long> costs = new HashSet<>();
        for (boolean md : new boolean[]{true, false}) {
            for (boolean cp : new boolean[]{true, false}) {
                if (cp && !nation.hasProject(Projects.URBAN_PLANNING)) continue;
                for (boolean aup : new boolean[]{true, false}) {
                    if (aup && !nation.hasProject(Projects.ADVANCED_URBAN_PLANNING)) continue;
                    for (boolean mp : new boolean[]{true, false}) {
                        for (boolean gsa : new boolean[]{true, false}) {
                            if (gsa && !nation.hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY)) continue;
                            for (boolean bda : new boolean[]{true, false}) {
                                if (bda && !nation.hasProject(Projects.BUREAU_OF_DOMESTIC_AFFAIRS)) continue;
                                double cost = PW.City.nextCityCost(city - 1, md, cp, aup, mp, gsa, bda);
                                costs.add(Math.round(cost));
                            }
                        }
                    }
                }
            }
        }

        for (Transaction2 transaction : transactions) {
            if (transaction.note == null) continue;
            if (!transaction.note.toLowerCase().contains("#city")) continue;
            Set<Integer> cities = Grant.getCities(nation, transaction.note, transaction.tx_datetime);
            Double amount = Grant.getAmount(transaction.note);

            if (cities != null && cities.size() == 1) {
                int num = cities.iterator().next();
                if (amount == null || amount <= 0) amount = 1d;
                if (num + amount >= city) return false;
            } else {
                if (costs.contains(Math.round(transaction.resources[0]))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean hasReceivedGrantWithNote(Collection<Transaction2> transactions, String note, long date) {
        note = note.toLowerCase();
        for (Transaction2 transaction : transactions) {
            if (transaction.tx_datetime < date) continue;
            if (transaction.note.toLowerCase().contains(note)) return true;
        }
        return false;
    }

    public static Set<Project> getProjectsGranted(DBNation nation, Collection<Transaction2> transactions) {
        Set<Project> result = new HashSet<>();
        for (Transaction2 transaction : transactions) {
            if (transaction.note == null || !transaction.note.contains("#project")) continue;
            String projectName = Grant.getAmountStr(transaction.note);
            if (projectName != null) {
                Project project = Projects.get(projectName.toUpperCase());
                if (project != null) {
                    result.add(project);
                    continue;
                }
            }

            Set<Project> possible = new HashSet<>();
            for (Project project : Projects.values) {
                if (result.contains(project)) continue;

                double[] cost = ResourceType.resourcesToArray(project.cost());
                double[] cost2 = ResourceType.resourcesToArray(PW.multiply(project.cost(), 0.95d));
                if (Arrays.equals(transaction.resources, cost) || Arrays.equals(transaction.resources, cost2)) {
                    if (nation.hasProject(project)) {
                        result.add(project);
                        continue;
                    }
                    possible.add(project);
                }
            }
            if (possible.size() >= 1) {
                result.add(possible.iterator().next());
            }
        }
        return result;
    }

    public DBNation getNation() {
        return DBNation.getById(nation.getNation_id());
    }

    public static class GrantRequirementBuilder {

    }

    public static class Requirement implements Function<DBNation, Boolean> {
        private final Function<DBNation, Boolean> function;
        private final String message;
        private boolean canOverride;

        public Requirement(String message, boolean canOverride, Function<DBNation, Boolean> requirement) {
            this.message = message;
            this.function = requirement;
            this.canOverride = canOverride;
        }

//        public abstract Map<String, String> serialize();
//
//        protected Map<String, String> serializeHelper(String command, String... argNameValuePairs) {
//            // todo
//        }

        public boolean canOverride() {
            return canOverride;
        }

        public Function<DBNation, Boolean> getFunction() {
            return function;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public Boolean apply(DBNation nation) {
            return function.apply(nation);
        }

        public Set<Requirement> toSet(Collection<Requirement> requirements) {
            return toSet(requirements.toArray(new Requirement[0]));
        }

        public Set<Requirement> toSet(Requirement... requirements) {
            HashSet<Requirement> set = new HashSet<>(Collections.singleton(this));
            if (requirements.length != 0) set.addAll(Arrays.asList(requirements));
            return set;
        }
    }

    public Grant(DBNation nation, DepositType.DepositTypeInfo type) {
        this.nation = nation;
        this.cost = f -> ResourceType.getBuffer();
        this.requirements = new LinkedHashSet<>();
        this.type = type;
        this.notes = new HashSet<>();
        this.cities = new LinkedHashSet<>();
    }

    /**
     * Get the N city ids granted after a date
     * @param nation
     * @param date
     * @param amt
     * @return
     */
    public static List<Integer> getNCityIdAfter(DBNation nation, long date, int amt) {
        List<DBCity> cities = new ArrayList<>(nation._getCitiesV3().values());
        cities.removeIf(f -> f.created < date);
        cities.sort(Comparator.comparingLong(o -> o.created));
        return cities.subList(0, Math.min(cities.size(), amt)).stream().map(f -> f.id).toList();
    }

    /**
     * Get the city uds granted from a note
     * @param nation
     * @param note
     * @param date
     * @return
     */
    public static Set<Integer> getCities(DBNation nation, String note, long date) {
        Map<String, String> parsed = PW.parseTransferHashNotes(note);
        String citiesStr = parsed.get("cities");
        if (citiesStr != null) {
            Set<Integer> result = new LinkedHashSet<>();
            for (String s : citiesStr.split(",")) {
                if (MathMan.isInteger(s)) {
                    result.add(Integer.parseInt(s));
                } else if (s.equalsIgnoreCase("*")) {
                    result.addAll(getCityIdsBeforeDate(nation, date));
                }
            }
            return result;
        }
        return null;
    }

    public static Double getAmount(String note) {
        Map<String, String> parsed = PW.parseTransferHashNotes(note);
        String amountStr = parsed.get("amount");
        return amountStr == null ? null : MathMan.parseDouble(amountStr);
    }

    public static String getAmountStr(String note) {
        return PW.parseTransferHashNotes(note).get("amount");
    }

    public Grant setAmount(double amount) {
        this.amount = "" + amount;
        return this;
    }

    public String getAmount() {
        return amount;
    }

    public Grant setAmount(String amount) {
        this.amount = amount;
        return this;
    }

    public String title() {
        return "Grant " + nation.getNation() + " " + type + (title != null ? " " + title : "") + " worth ~$" + MathMan.format(ResourceType.convertedTotal(cost()));
    }

    public Grant setTitle(String title) {
        this.title = title;
        return this;
    }

    public Grant addExpiry(long timediff) {
        if (timediff <= 0) addNote("#ignore");
        else addNote("#expire=" + TimeUtil.secToTime(TimeUnit.MILLISECONDS, timediff));
        return this;
    }
    public Grant addExpiry(int days) {
        if (days <= 0) addNote("#ignore");
        else addNote("#expire=" + days + "d");
        return this;
    }

    public Grant addNote(String note) {
        notes.add(note);
        return this;
    }

    public String getTitle() {
        return title;
    }

    public DepositType.DepositTypeInfo getType() {
        return type;
    }

    public String getNote() {
        Set<String> finalNotes = new HashSet<>();
        finalNotes.addAll(notes);
        finalNotes.add(type.toString() + (amount == null || amount.equalsIgnoreCase("0") ? "" : "=" + amount));
        if (!cities.isEmpty()) {
            if (cities.size() == nation.getCities() || allCities) {
                finalNotes.add("#cities=*");
            } else {
                finalNotes.add("#cities=" + StringMan.join(cities, ","));
            }
        }
        return StringMan.join(finalNotes, " ");
    }

    public Grant setNote(String note) {
        this.notes.clear();
        this.notes.add(note);
        return this;
    }

    public Grant setType(DepositType.DepositTypeInfo type) {
        this.type = type;
        return this;
    }

    public Set<Requirement> getRequirements() {
        return requirements;
    }

    public Grant setCost(Function<DBNation, double[]> cost) {
        this.cost = cost;
        return this;
    }

    public Grant setInstructions(String instructions) {
        this.instructions = instructions;
        return this;
    }

    public String getInstructions() {
        if (this.instructions == null) return type.type.getDescription();
        return instructions;
    }

    public boolean hasInstructions() {
        return instructions != null && !instructions.isEmpty();
    }

    public Grant addRequirement(Collection<Requirement> requirements) {
        return addRequirement(requirements.toArray(new Requirement[0]));
    }

    public Grant addRequirement(Requirement... requirements) {
        for (Requirement requirement : requirements) {
            this.requirements.add(requirement);
        }
        return this;
    }

    public boolean hasPermission() {
        for (Requirement requirement : requirements) {
            Boolean result = requirement.getFunction().apply(nation);
            if (!result) throw new IllegalArgumentException(requirement.getMessage());
        }
        return true;
    }

    public boolean canGrant(Member member, DBNation granter) {
        return false;
    }

    public double[] cost() {
        return cost.apply(nation);
    }

    // TransferResult
    // Cost
    // Instructions
    // DepositType

    public static String generateCommandLogic(
            IMessageIO io, GuildDB db, DBNation me, User author,
            Set<DBNation> receivers,
            boolean onlySendMissingFunds,
            DBNation depositsAccount,
            DBAlliance useAllianceBank,
            DBAlliance useOffshoreAccount,
            TaxBracket taxAccount,
            boolean existingTaxAccount,
            Long expire,
            Long decay,
            boolean ignore,
            boolean convertToMoney,
            EscrowMode escrow_mode,
            boolean bypass_checks,
            boolean force,
            BiFunction<DBNation, Grant, TransferResult> getCostInfo,
            DepositType baseNote,
            Function<DBNation, List<Grant.Requirement>> getRequirements
    ) throws GeneralSecurityException, IOException {
        List<TransferResult> errors = new ArrayList<>();
        SpreadSheet sheet = receivers.size() > 1 ? SpreadSheet.create(db, SheetKey.GRANT_SHEET) : null;
        if (sheet != null) {
            List<String> header = new ArrayList<>(Arrays.asList(
                    "nation",
                    "cities",
                    "avg_infra",
                    "response",
                    "cost_converted",
                    "cost_raw",
                    "note"
            ));
            sheet.updateClearCurrentTab();
            sheet.setHeader(header);
        }

        ResourceType.ResourcesBuilder total = ResourceType.builder();
        Map<DBNation, Grant> grantByReceiver = new LinkedHashMap<>();
        for (DBNation receiver : receivers) {
            if (!db.isAllianceId(receiver.getAlliance_id())) {
                errors.add(new TransferResult(OffshoreInstance.TransferStatus.NOT_MEMBER, receiver, new HashMap<>(), baseNote.withValue().toString()).addMessage( "Nation is not in an alliance registered to this guild"));
                if (!force) {
                    continue;
                }
            }
            Role noGrants = Roles.TEMP.toRole(db);
            if (noGrants != null) {
                net.dv8tion.jda.api.entities.Member member = receiver.getMember(db);
                if (member != null && Roles.TEMP.has(member)) {
                    errors.add(new TransferResult(OffshoreInstance.TransferStatus.GRANT_REQUIREMENT, receiver, new HashMap<>(), baseNote.withValue().toString()).addMessage("Nation has the @" + noGrants.getName() + " role"));
                    if (!force) {
                        continue;
                    }
                }
            }

            ArrayList<Object> row = new ArrayList<>();
            if (sheet != null) {
                row.add(MarkupUtil.sheetUrl(receiver.getNation(), receiver.getUrl()));
                row.add(receiver.getCities());
                row.add(receiver.getAvg_infra());
            }

            try {
                if (!force) {
                    List<Grant.Requirement> requirements = new ArrayList<>();
                    requirements.addAll(AGrantTemplate.getBaseRequirements(db, me, receiver, null, false));
                    List<Requirement> addReq = getRequirements.apply(receiver);
                    if (addReq != null) requirements.addAll(addReq);
                    for (Requirement requirement : requirements) {
                        if (!requirement.apply(receiver)) {
                            errors.add(new TransferResult(OffshoreInstance.TransferStatus.GRANT_REQUIREMENT, receiver, new HashMap<>(), baseNote.withValue().toString()).addMessage(requirement.getMessage()));
                        }
                    }
                }

                Grant grant = new Grant(receiver, DepositType.GRANT.withValue());

                TransferResult error = getCostInfo.apply(receiver, grant);
                if (error != null) {
                    errors.add(error);
                    continue;
                }
                DepositType.DepositTypeInfo note = grant.getType();
                DepositType type = note.getType();
                double[] resources = grant.cost();
                if (ResourceType.isZero(resources)) {
                    errors.add(new TransferResult(OffshoreInstance.TransferStatus.NOTHING_WITHDRAWN, receiver, new HashMap<>(), baseNote.withValue().toString()).addMessage(OffshoreInstance.TransferStatus.NOTHING_WITHDRAWN.getMessage()));
                    continue;
                }

                if (!grant.hasInstructions()) {
                    grant.setInstructions(type.getDescription() + "\nFor `" + ResourceType.resourcesToString(resources) + "`");
                }
                grantByReceiver.put(receiver, grant);

                if (sheet != null) {
                    double[] costApplyMissing = resources.clone();
                    if (onlySendMissingFunds) {
                        if (!db.isAllianceId(receiver.getAlliance_id())) {
                            throw new IllegalArgumentException("Nation " + receiver.getMarkdownUrl() + " is not in an alliance registered to this guild (currently: " + db.getAllianceIds() + ")");
                        }
                        Map<ResourceType, Double> stockpile = receiver.getStockpile();
                        for (Map.Entry<ResourceType, Double> entry : stockpile.entrySet()) {
                            if (entry.getValue() > 0) {
                                double newAmt = Math.max(0, costApplyMissing[entry.getKey().ordinal()] - entry.getValue());
                                if (newAmt <= 0) {
                                    costApplyMissing[entry.getKey().ordinal()] = 0;
                                } else {
                                    costApplyMissing[entry.getKey().ordinal()] = newAmt;
                                }
                            }
                        }
                    }
                    if (ResourceType.isZero(costApplyMissing)) {
                        errors.add(new TransferResult(OffshoreInstance.TransferStatus.NOTHING_WITHDRAWN, receiver, new HashMap<>(), baseNote.withValue().toString()).addMessage(OffshoreInstance.TransferStatus.NOTHING_WITHDRAWN.getMessage()));
                        continue;
                    }
                    row.add(grant.getInstructions());
                    row.add(ResourceType.convertedTotal(costApplyMissing));
                    row.add(ResourceType.resourcesToString(costApplyMissing));
                    row.add(note.toString());
                }

                total.add(resources);
            } catch (IllegalArgumentException e) {
                errors.add(new TransferResult(OffshoreInstance.TransferStatus.OTHER, receiver, new HashMap<>(), baseNote.withValue().toString()).addMessage(e.getMessage()));
                if (sheet != null) {
                    row.add(e.getMessage());
                    row.add(0);
                    row.add("{}");
                }
            }
            if (sheet != null) {
                sheet.addRow(row);
            }
        }
        if (grantByReceiver.isEmpty()) {
            io.create().embed("No Grants Created", "Summary: `" + TransferResult.count(errors) + "`\n\n" +
                            "See attached `errors.csv`")
                    .file("errors.csv", TransferResult.toFileString(errors))
                    .send();
            return null;
        }

        String typeStr = (ignore ? DepositType.IGNORE : DepositType.GRANT).name();
        if (sheet == null) {
            DBNation receiver = receivers.iterator().next();
            Grant grant = grantByReceiver.get(receiver);
            double[] resources = grant.cost();

            JSONObject command = CM.transfer.resources.cmd
                    .receiver(receiver.getUrl())
                    .depositType(typeStr)
                    .transfer(ResourceType.resourcesToString(resources))
                    .nationAccount(depositsAccount != null ? depositsAccount.getQualifiedId() : null)
                    .senderAlliance(useAllianceBank != null ? useAllianceBank.getQualifiedId() : null)
                    .allianceAccount(useOffshoreAccount != null ? useOffshoreAccount.getQualifiedId() : null)
                    .taxAccount(taxAccount != null ? taxAccount.getQualifiedId() : null)
                    .existingTaxAccount(existingTaxAccount ? "true" : null)
                    .expire(expire != null ? TimeUtil.secToTime(TimeUnit.MILLISECONDS, expire) : null)
                    .decay(decay != null ? TimeUtil.secToTime(TimeUnit.MILLISECONDS, decay) : null)
                    .onlyMissingFunds(onlySendMissingFunds ? "true" : null)
                    .convertCash(convertToMoney ? "true" : null)
                    .escrow_mode(escrow_mode != null ? escrow_mode.name() : null)
                    .bypassChecks(bypass_checks ? "true" : null)
                    .force(force ? "true" : null)
                    .toJson();
            StringBuilder msg = new StringBuilder();
            msg.append(ResourceType.resourcesToString(resources)).append("\n")
                    .append("Current values for: " + receiver.getNation()).append('\n')
                    .append("Cities: " + receiver.getCities()).append('\n')
                    .append("Infra: " + receiver.getAvg_infra()).append('\n')
            ;
            StringBuilder body = new StringBuilder();
            body.append("**INSTRUCTIONS:** " + grant.getInstructions());
            if (!errors.isEmpty()) {
                body.append("\n\n**Warnings:**\n");
                body.append(TransferResult.toEmbed(errors).getValue());
            }

            io.create().embed("Instruct: " + grant.title(), body.toString()).send();
            io.create().confirmation("Confirm: " + grant.title(), msg.toString(), command).cancelButton().send();
            return null;
        } else {
            sheet.updateWrite();

            TransferSheet transferSheet = new TransferSheet(sheet);
            transferSheet.read();

            JSONObject command = CM.transfer.bulk.cmd
                    .sheet(sheet.getURL())
                    .depositType(typeStr)
                    .depositsAccount(depositsAccount != null ? depositsAccount.getQualifiedId() : null)
                    .useAllianceBank(useAllianceBank != null ? useAllianceBank.getQualifiedId() : null)
                    .useOffshoreAccount(useOffshoreAccount != null ? useOffshoreAccount.getQualifiedId() : null)
                    .taxAccount(taxAccount != null ? taxAccount.getQualifiedId() : null)
                    .existingTaxAccount(existingTaxAccount ? "true" : null)
                    .expire(expire != null ? TimeUtil.secToTime(TimeUnit.MILLISECONDS, expire) : null)
                    .decay(decay != null ? TimeUtil.secToTime(TimeUnit.MILLISECONDS, decay) : null)
                    .convertToMoney(convertToMoney ? "true" : null)
                    .escrow_mode(escrow_mode != null ? escrow_mode.name() : null)
                    .bypassChecks(bypass_checks ? "true" : null)
                    .force(force ? "true" : null)
                    .toJson();

            return BankCommands.transferBulkWithErrors(io, command, author, me, db,
                    transferSheet,
                    (ignore ? DepositType.IGNORE : DepositType.GRANT).withValue(),
                    depositsAccount,
                    useAllianceBank,
                    useOffshoreAccount,
                    taxAccount,
                    existingTaxAccount,
                    expire,
                    decay,
                    convertToMoney,
                    escrow_mode,
                    bypass_checks,
                    force,
                    null,
                    (Map) TransferResult.toMap(errors)
            );
        }
    }
}
