package link.locutus.discord.db.entities.grant;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.Grant;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

import javax.annotation.Nullable;
import java.util.*;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class AGrantTemplate<T> {

    private final GuildDB db;
    private final long expiryOrZero;
    private final long decayOrZero;
    private final boolean allowIgnore;
    private boolean enabled;
    private String name;
    private NationFilter nationFilter;
    private long econRole;
    private long selfRole;
    private int fromBracket;
    private boolean useReceiverBracket;
    private int maxTotal;
    private int maxDay;
    private int maxGranterTotal;
    private int maxGranterDay;
    private long dateCreated;
    private long repeatable_time;

    public AGrantTemplate(GuildDB db, boolean enabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long dateCreated, long expiryOrZero, long decayOrZero, boolean allowIgnore, long repeatable_time) {
        this.db = db;
        this.enabled = enabled;
        this.name = name;
        this.nationFilter = nationFilter;
        this.econRole = econRole;
        this.selfRole = selfRole;
        this.fromBracket = fromBracket;
        this.useReceiverBracket = useReceiverBracket;
        this.maxTotal = maxTotal;
        this.maxDay = maxDay;
        this.maxGranterDay = maxGranterDay;
        this.maxGranterTotal = maxGranterTotal;
        this.dateCreated = dateCreated;
        this.expiryOrZero = expiryOrZero;
        this.decayOrZero = decayOrZero;
        this.allowIgnore = allowIgnore;
        this.repeatable_time = repeatable_time;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String toListString() {
        StringBuilder result = new StringBuilder(getName() + " | " + getType());
        String filterString = nationFilter.getFilter();
        if (filterString.contains("#")) {
            result.append(" | ");
            result.append("" + filterString + "");
        }

        if (fromBracket > 0) {
            if (filterString.contains("#")) result.append(",");
            result.append("tax_id=").append(fromBracket);
        } else if (useReceiverBracket) {
            if (result.length() > 0) result.append(",");
            result.append("tax_id=").append("receiver");
        }
        if (expiryOrZero > 0) {
            String time = TimeUtil.secToTime(TimeUnit.MILLISECONDS, expiryOrZero);
            result.append(" #expire=").append(time);
        }
        if (decayOrZero > 0) {
            String time = TimeUtil.secToTime(TimeUnit.MILLISECONDS, decayOrZero);
            result.append(" #decay=").append(time);
        }
        if (allowIgnore) {
            result.append(" #ignore");
        }
        return result.toString();
    }

    public List<GrantTemplateManager.GrantSendRecord> getGrantedTotal() {
        return getGranted(Long.MAX_VALUE);
    }

    public List<GrantTemplateManager.GrantSendRecord> getGranted(long time) {
        return getGranted(time, null);
    }

    public List<GrantTemplateManager.GrantSendRecord> getGranted(long time, DBNation sender) {
        long cutoff = System.currentTimeMillis() - time;
        List<GrantTemplateManager.GrantSendRecord> grants;
        if (sender == null) {
            grants = db.getGrantTemplateManager().getRecordsByGrant(getName());
        } else {
            grants = db.getGrantTemplateManager().getRecordsBySender(sender.getId(), getName());
        }
        if (time > 0) {
            grants = grants.stream().filter(f -> f.date >= cutoff).collect(Collectors.toList());
        }
        return grants;
    }

    public List<GrantTemplateManager.GrantSendRecord> getGrantedTotal(DBNation sender) {
        return getGranted(Long.MAX_VALUE, sender);
    }

    protected abstract String toInfoString(DBNation sender, DBNation receiver, T parsed);

    public String getCommandString() {
        return getCommandString(name,
                nationFilter.getFilter(),
                getEconRole() == null ? null : getEconRole().getAsMention(),
                getSelfRole() == null ? null : getSelfRole().getAsMention(),
                fromBracket > 0 ? "tax_id=" + fromBracket : null,
                useReceiverBracket ? "true" : null,
                maxTotal > 0 ? "" + maxTotal : null,
                maxDay > 0 ? "" + maxDay : null,
                maxGranterDay > 0 ? "" + maxGranterDay : null,
                maxGranterTotal > 0 ? "" + maxGranterTotal : null,
                expiryOrZero == 0 ? null : TimeUtil.secToTime(TimeUnit.MILLISECONDS, expiryOrZero),
                decayOrZero == 0 ? null : TimeUtil.secToTime(TimeUnit.MILLISECONDS, decayOrZero),
                allowIgnore ? "true" : null,
                repeatable_time <= 0 ? null : TimeUtil.secToTime(TimeUnit.MILLISECONDS, repeatable_time));
    }

    public abstract String getCommandString(String name, String allowedRecipients, String econRole, String selfRole, String bracket, String useReceiverBracket, String maxTotal, String maxDay, String maxGranterDay, String maxGranterTotal, String allowExpire, String allowDecay, String allowIgnore, String repeatable);

    public String toFullString(DBNation sender, DBNation receiver, T parsed) {
        // sender or receiver may be null
        StringBuilder data = new StringBuilder();
        if (!enabled) {
            data.append("Disabled: enable with " + CM.grant_template.enable.cmd.toSlashMention() + "\n");
        }
        data.append("Name: `").append(getName()).append("`\n");
        data.append("Type: `").append(this.getType().name()).append("`\n");
        data.append("Allowed: `" + nationFilter.getFilter() + "`\n");
        if (econRole > 0) {
            Role role = getEconRole();
            String roleStr = role == null ? "`<@&" + econRole + ">`" : role.getAsMention();
            data.append("Granter Other: ").append(roleStr).append("\n");
        }
        if (selfRole > 0) {
            Role role = getSelfRole();
            String roleStr = role == null ? "`<@&" + selfRole + ">`" : role.getAsMention();
            data.append("Grant Self: ").append(roleStr).append("\n");
        }
        if (fromBracket > 0) {
            data.append("Tax account: `#").append(fromBracket).append("`\n");
        }
        if (useReceiverBracket) {
            data.append("Tax account: `receiver`\n");
        }
        if (maxTotal > 0) {
            data.append("Total: `").append(getGrantedTotal().size() + "/" + maxTotal).append("`\n");
        }
        if (maxDay > 0) {
            data.append("Daily: `").append(getGranted(TimeUnit.DAYS.toMillis(1)).size() + "/" + maxDay).append("`\n");
        }
        if (expiryOrZero > 0) {
            data.append("Allow Expiry: `").append(TimeUtil.secToTime(TimeUnit.MILLISECONDS, expiryOrZero)).append("`\n");
        }
        if (allowIgnore) {
            data.append("Allow #ignore: `true`\n");
        }
        if (maxGranterTotal > 0) {
            data.append("Total(Granter): `");
            if (sender != null) {
                data.append(getGrantedTotal(sender).size() + "/");
            }
            data.append(maxGranterTotal).append("`\n");
        }
        if (maxGranterDay > 0) {
            data.append("Daily(Granter): `");
            if (sender != null) {
                data.append(getGranted(TimeUnit.DAYS.toMillis(1), sender).size() + "/");
            }
            data.append(maxGranterDay).append("`\n");
        }
        data.append(toInfoString(sender, receiver, parsed));
        // receiver markdown
        if (sender != null && receiver != null) {
            double[] cost = getCost(db, sender, receiver, parsed);
            if (cost != null) {
                data.append("Cost: `").append(ResourceType.toString(cost)).append("`\n");
            }
            List<Grant.Requirement> requirements = getDefaultRequirements(db, sender, receiver, parsed, false);
            Set<Grant.Requirement> failedFinal = new HashSet<>();
            Set<Grant.Requirement> failedOverride = new HashSet<>();
            for (Grant.Requirement requirement : requirements) {
                if (!requirement.apply(receiver)) {
                    boolean canOverride = requirement.canOverride();
                    if (canOverride) {
                        failedOverride.add(requirement);
                    } else {
                        failedFinal.add(requirement);
                    }
                }
            }
            if (!failedFinal.isEmpty()) {
                data.append("Errors:\n");
                for (Grant.Requirement requirement : failedFinal) {
                    data.append("- " + requirement.getMessage()).append("\n");
                }
            }
            if (!failedOverride.isEmpty()) {
                data.append("Warnings:\n");
                for (Grant.Requirement requirement : failedOverride) {
                    data.append("- " + requirement.getMessage()).append("\n");
                }
            }
            String instructions = this.getInstructions(sender, receiver, parsed);
            if (instructions != null && !instructions.isEmpty()) {
                data.append("Instructions:\n>>> ").append(instructions).append("\n");
            }
        } else {
            List<Grant.Requirement> requirements = getDefaultRequirements(db, sender, receiver, parsed, false);
            if (!requirements.isEmpty()) {
                data.append("\n**Template Checks:**\n");
                for (Grant.Requirement requirement : requirements) {
                    data.append("- " + requirement.getMessage()).append("\n");
                }
            }
        }

        return data.toString();
    }

    public boolean hasRole(Member author) {
        List<Role> roles = author.getRoles();
        for (Role role : roles) {
            if (role.getIdLong() == econRole) {
                return true;
            }
        }
        return false;
    }

    public boolean hasSelfRole(Member author) {
        List<Role> roles = author.getRoles();
        for (Role role : roles) {
            if (role.getIdLong() == selfRole) {
                return true;
            }
        }
        return false;
    }

    public GuildDB getDb() {
        return db;
    }

    public String getName() {
        return name;
    }

    public NationFilter getNationFilter() {
        return nationFilter;
    }

    public long getEconRoleId() {
        return econRole;
    }

    public long getSelfRoleId() {
        return selfRole;
    }

    public int getFromBracket() {
        return fromBracket;
    }

    public boolean isUseReceiverBracket() {
        return useReceiverBracket;
    }

    public int getMaxTotal() {
        return maxTotal;
    }

    public int getMaxDay() {
        return maxDay;
    }

    public int getMaxGranterDay() {
        return maxGranterDay;
    }

    public int getMaxGranterTotal() {
        return maxGranterTotal;
    }

    public abstract TemplateTypes getType();

    public List<Grant.Requirement> getDefaultRequirements(GuildDB db, @Nullable DBNation sender, @Nullable DBNation receiver, T parsed, boolean confirmed) {
        return getBaseRequirements(db, sender, receiver, this, confirmed);
    }

    public static List<Grant.Requirement> getBaseRequirements(GuildDB db, @Nullable DBNation sender, @Nullable DBNation receiver, @Nullable AGrantTemplate template, boolean confirmed) {
        List<Grant.Requirement> list = new ArrayList<>();

        NationFilter filter = template == null ? null : template.getNationFilter();
        if (template == null || (filter != null && !"*".equals(filter.getFilter()))) {
            Predicate<DBNation> cached = template == null ? null : filter.toCached(Long.MAX_VALUE);
            list.add(new Grant.Requirement("Nation must match: `" + (template == null ? "`{filter}`" : filter.getFilter()) + "`", false, new Function<DBNation, Boolean>() {
                @Override
                public Boolean apply(DBNation nation) {
                    return cached == null || cached.test(nation);
                }
            }));
        }

        // check grant not disabled
        list.add(new Grant.Requirement("Grant template must NOT be disabled: " + CM.grant_template.enable.cmd.toSlashMention(), false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return template == null || template.isEnabled();
            }
        }));

        // check grant limits (limit total/day, limit granter total/day)
        int maxTotal = template == null ? Integer.MAX_VALUE : template.getMaxTotal();
        int maxDay = template == null ? Integer.MAX_VALUE : template.getMaxDay();
        int maxGranterTotal = template == null ? Integer.MAX_VALUE : template.getMaxGranterTotal();
        int maxGranterDay = template == null ? Integer.MAX_VALUE : template.getMaxGranterDay();

        if (template == null || maxTotal > 0) {
            list.add(new Grant.Requirement("Must NOT exceed grant limit of: " + (template == null ? "`{max_total}`" : maxTotal) + " total grants for this template", false, new Function<DBNation, Boolean>() {
                @Override
                public Boolean apply(DBNation nation) {
                    return template == null || template.getGrantedTotal().size() < maxTotal;
                }
            }));
        }
        if (template == null || maxDay > 0) {
            list.add(new Grant.Requirement("Must NOT exceed grant limit of: " + (template == null ? "`{max_day}`" : maxDay) + " grants per day for this template", false, new Function<DBNation, Boolean>() {
                @Override
                public Boolean apply(DBNation nation) {
                    if (template == null) return true;
                    long oneDayMs = TimeUnit.DAYS.toMillis(1);
                    return template.getGranted(oneDayMs).size() < maxDay;
                }
            }));
        }
        if (template == null || maxGranterTotal > 0) {
            list.add(new Grant.Requirement("Must NOT exceed grant limit of: " + (template == null ? "`{max_granter_total}`" : maxGranterTotal) + " total grants sent from " + (sender == null ? "sender" : sender.getName() + " for this template"), false, new Function<DBNation, Boolean>() {
                @Override
                public Boolean apply(DBNation nation) {
                    if (template == null) return true;
                    return template.getGrantedTotal(sender).size() < maxGranterTotal;
                }
            }));
        }
        if (template == null || maxGranterDay > 0) {
            list.add(new Grant.Requirement("Must NOT exceed grant limit of: " + (template == null ? "`{max_granter_day}`" : maxGranterDay) + " grants per day sent from " + (sender == null ? "sender" : sender.getName() + " for this template"), false, new Function<DBNation, Boolean>() {
                @Override
                public Boolean apply(DBNation nation) {
                    if (template == null) return true;
                    long oneDayMs = TimeUnit.DAYS.toMillis(1);
                    return template.getGranted(oneDayMs, sender).size() < maxGranterDay;
                }
            }));
        }
        list.add(new Grant.Requirement("Nation must NOT receive a grant template twice (when `repeatable: false`)", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                if (template == null) return true;
                List<GrantTemplateManager.GrantSendRecord> records = db.getGrantTemplateManager().getRecordsByReceiver(nation.getId(), template.getName());
                if (template.repeatable_time <= 0) {
                    return records.isEmpty();
                }
                long cutoff = System.currentTimeMillis() - template.repeatable_time;
                records.removeIf(f -> f.date <= cutoff);
                return records.isEmpty();
            }
        }));

       // errors.computeIfAbsent(nation, f -> new ArrayList<>()).add("Nation was not found in guild");
        list.add(new Grant.Requirement("Nation must be verified: " + CM.register.cmd.toSlashMention(), false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                User user = nation.getUser();
                return user != null;
            }
        }));
        list.add(new Grant.Requirement("Nation must be in an alliance", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return nation.getAlliance_id() != 0;
            }
        }));
//                grant.addRequirement(new Grant.Requirement("Nation is not a member of an alliance", econGov, f -> f.getPosition() > 1));
        list.add(new Grant.Requirement("Nation must be a member of an alliance", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return nation.getPosition() > 1;
            }
        }));
//                grant.addRequirement(new Grant.Requirement("Nation is in VM", econGov, f -> f.getVm_turns() == 0));
        list.add(new Grant.Requirement("Nation must NOT be in VM", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return nation.getVm_turns() == 0;
            }
        }));
        // nation color cannot be NationColor.GRAY
        list.add(new Grant.Requirement("Nation must NOT be gray", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return nation.getColor() != NationColor.GRAY;
            }
        }));
//                grant.addRequirement(new Grant.Requirement("Nation is not in the alliance: " + alliance, econGov, f -> alliance != null && f.getAlliance_id() == alliance.getAlliance_id()));
        list.add(new Grant.Requirement("Nation must be in the alliance id: " + (db == null ? "`{allianceids}`" : db.getAllianceIds()), false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return db.isAllianceId(nation.getAlliance_id());
            }
        }));

        Set<Integer> blacklist = db == null ? null : GuildKey.GRANT_TEMPLATE_BLACKLIST.getOrNull(db);

        if(blacklist == null)
            blacklist = Collections.emptySet();

        Set<Integer> finalBlacklist = blacklist;
        list.add(new Grant.Requirement("Nation must NOT be added to setting: `GRANT_TEMPLATE_BLACKLIST`", false, new Function<DBNation, Boolean>() {
           @Override
           public Boolean apply(DBNation dbNation) {
               return !finalBlacklist.contains(dbNation.getId());
           }
       }));
//
//                grant.addRequirement(new Grant.Requirement("Nation is not active in past 24h", econStaff, f -> f.active_m() < 1440));
        list.add(new Grant.Requirement("Nation must be active in past 24h", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return nation.active_m() < 1440;
            }
        }));
        list.add(new Grant.Requirement("Nation must have 10d seniority", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return nation.allianceSeniority() >= 10;
            }
        }));

        list.add(new Grant.Requirement("Nation must NOT be close to being beiged (<30 resistance after attacks)", true, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return nation.minWarResistancePlusMap() > 30;
            }
        }));

        list.add(new Grant.Requirement("Nation must NOT be blockaded", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return !nation.isBlockaded();
            }
        }));

        List<Transaction2> transfers = receiver == null ? Collections.emptyList() : receiver.getTransactions(template == null || !confirmed ? -1 : 0, true);
        long latest = !transfers.isEmpty() ? transfers.stream().mapToLong(Transaction2::getDate).max().getAsLong() : 0L;
        // require no new transfers
        list.add(new Grant.Requirement("Nation must NOT receive a transfer whilst this grant is being sent. Please try again", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                List<Transaction2> newTransfers = receiver.getTransactions(template == null || !confirmed ? -1 : 0, true);
                long newLatest = newTransfers.size() > 0 ? newTransfers.stream().mapToLong(Transaction2::getDate).max().getAsLong() : 0L;
                return latest == newLatest;
            }
        }));

        return list;
    }

    protected List<String> getQueryFieldsBase() {
        List<String> list = new ArrayList<>();
        list.add("enabled");
        list.add("name");
        list.add("nation_filter");
        list.add("econ_role");
        list.add("self_role");
        list.add("from_bracket");
        list.add("use_receiver_bracket");
        list.add("max_total");
        list.add("max_day");
        list.add("max_granter_day");
        list.add("max_granter_total");
        list.add("date_created");
        list.add("expire");
        list.add("decay");
        list.add("allow_ignore");
        list.add("repeatable");
        return list;
    }

    public long getLatestAttackDate(DBNation receiver) {
        return getLatestAttackDate(receiver, 0);
    }

    public long getLatestAttackDate(DBNation receiver, int requireNOffensives) {
        List<DBWar> wars = new ObjectArrayList<>(receiver.getWars());
        wars.removeIf(f -> f.getAttacker_id() != receiver.getId());
        // sort wars date desc
        Collections.sort(wars, (o1, o2) -> Long.compare(o2.getDate(), o1.getDate()));
        int nOffensives = 0;
        outer:
        for (int offensiveI = requireNOffensives; offensiveI < wars.size(); offensiveI++) {
            DBWar war = wars.get(offensiveI);
            long latest = 0;
            List<AbstractCursor> attacks = new ArrayList<>(war.getAttacks2());
            for (AbstractCursor attack : attacks) {
                if (attack.getAttacker_id() != receiver.getId()) {
                    latest = Math.max(latest, attack.getDate());
                }
            }
            if (latest != 0) return latest;
        }
        return 0;
    }

    public abstract List<String> getQueryFields();

    public String createQuery(boolean replace) {
        List<String> fields = getQueryFields();
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT " + (replace ? "OR REPLACE " : "") + " INTO `" + this.getType().getTable() + "` (");
        for (int i = 0; i < fields.size(); i++) {
            sb.append("`" + fields.get(i) + "`");
            if (i < fields.size() - 1) sb.append(", ");
        }
        sb.append(") VALUES (");
        for (int i = 0; i < fields.size(); i++) {
            sb.append("?");
            if (i < fields.size() - 1) sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }

    protected void setValuesBase(PreparedStatement stmt) throws SQLException {
        stmt.setBoolean(1, this.isEnabled());
        stmt.setString(2, this.getName());
        stmt.setString(3, this.getNationFilter().getFilter());
        stmt.setLong(4, this.getEconRoleId());
        stmt.setLong(5, this.getSelfRoleId());
        stmt.setLong(6, this.getFromBracket());
        stmt.setBoolean(7, this.isUseReceiverBracket());
        stmt.setInt(8, this.getMaxTotal());
        stmt.setInt(9, this.getMaxDay());
        stmt.setInt(10, this.getMaxGranterDay());
        stmt.setInt(11, this.getMaxGranterTotal());
        stmt.setLong(12, this.getDateCreated());
        stmt.setLong(13, this.getExpire());
        stmt.setLong(14, this.getDecay());
        stmt.setBoolean(15, this.allowsIgnore());
        stmt.setLong(16, this.getRepeatable());
    }

    public long getRepeatable() {
        return repeatable_time;
    }

    public long getDateCreated() {
        return dateCreated;
    }

    public abstract double[] getCost(GuildDB db, DBNation sender, DBNation receiver, T parsed);
    public abstract DepositType.DepositTypeInfo getDepositType(DBNation receiver, T parsed);
    public abstract String getInstructions(DBNation sender, DBNation receiver, T parsed);

    public abstract void setValues(PreparedStatement stmt) throws SQLException;

    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public Role getEconRole() {
        return db.getGuild().getRoleById(econRole);
    }

    public Role getSelfRole() {
        return db.getGuild().getRoleById(selfRole);
    }

    public abstract Class<T> getParsedType();

    public T parse(DBNation receiver, String value) {
        return (T) parse(db, receiver, value, getParsedType());
    }

    public static <K> K parse(GuildDB db, DBNation receiver, String value, Class<K> parsedType) {
        if (value == null) return null;
        CommandManager2 cmdManager = Locutus.imp().getCommandManager().getV2();
        LocalValueStore store = new LocalValueStore<>(cmdManager.getStore());
        store.addProvider(Key.of(DBNation.class, Me.class), receiver);
        store.addProvider(Key.of(GuildDB.class, Me.class), db);
        return (K) store.get(Key.of(parsedType)).apply(store, value);
    }

    public Grant createGrant(DBNation sender, DBNation receiver, T customValue) {
        Grant grant = new Grant(receiver, getDepositType(receiver, customValue));
        grant.setCost(f -> this.getCost(db, sender, receiver, customValue));
        grant.addRequirement(getDefaultRequirements(db, sender, receiver, customValue, false));
        // grant.addNote()
        grant.setInstructions(getInstructions(sender, receiver, customValue));

        return grant;
    }

    public TaxBracket getTaxAccount(GuildDB db, DBNation receiver) {
        if (useReceiverBracket || fromBracket == receiver.getTax_id()) {
            return receiver.getTaxBracket();
        }
        if (this.fromBracket > 0) {
            return PWBindings.bracket(db, "tax_id=" + fromBracket);
        }
        return null;
    }

    public boolean allowsExpire() {
        return this.expiryOrZero > 0;
    }

    public boolean allowsDecay() {
        return this.decayOrZero > 0;
    }

    public long getExpire() {
        return this.expiryOrZero;
    }

    public long getDecay() {
        return this.decayOrZero;
    }

    public boolean allowsIgnore() {
        return this.allowIgnore;
    }
}
