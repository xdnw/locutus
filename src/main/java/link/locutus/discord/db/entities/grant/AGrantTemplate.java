package link.locutus.discord.db.entities.grant;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.NationColor;
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
import link.locutus.discord.util.PnwUtil;
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

    public AGrantTemplate(GuildDB db, boolean enabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long dateCreated, long expiryOrZero, boolean allowIgnore) {
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
        this.allowIgnore = allowIgnore;
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
            result.append("#tax_id=").append(fromBracket);
        } else if (useReceiverBracket) {
            if (result.length() > 0) result.append(",");
            result.append("#tax_id=").append("receiver");
        }
        if (expiryOrZero > 0) {
            String time = TimeUtil.secToTime(TimeUnit.MILLISECONDS, expiryOrZero);
            result.append(" #expire=").append(time);
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
                fromBracket > 0 ? "#tax_id=" + fromBracket : null,
                useReceiverBracket ? "true" : null,
                maxTotal > 0 ? "" + maxTotal : null,
                maxDay > 0 ? "" + maxDay : null,
                maxGranterDay > 0 ? "" + maxGranterDay : null,
                maxGranterTotal > 0 ? "" + maxGranterTotal : null, expiryOrZero == 0 ? null : TimeUtil.secToTime(TimeUnit.MILLISECONDS, expiryOrZero), allowIgnore ? "true" : null);
    }

    public abstract String getCommandString(String name, String allowedRecipients, String econRole, String selfRole, String bracket, String useReceiverBracket, String maxTotal, String maxDay, String maxGranterDay, String maxGranterTotal, String allowExpire, String allowIgnore);

    public String toFullString(DBNation sender, DBNation receiver, T parsed) {
        System.out.println(6.1);
        // sender or receiver may be null
        StringBuilder data = new StringBuilder();
        if (!enabled) {
            data.append("`disabled: enable with `" + CM.grant_template.enable.cmd.toSlashMention());
        }
        System.out.println(6.2);
        data.append("Name: `").append(getName()).append("`\n");
        data.append("Type: `").append(this.getType().name()).append("`\n");
        data.append("Allowed: `" + nationFilter.getFilter() + "`\n");
        if (econRole > 0) {
            Role role = getEconRole();
            String roleStr = role == null ? "`<@&" + econRole + ">`" : role.getAsMention();
            data.append("Granter Other: ").append(roleStr).append("\n");
        }
        System.out.println(6.3);
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
        System.out.println(6.4);
        if (maxGranterTotal > 0) {
            data.append("Total(Granter): `");
            if (sender != null) {
                data.append(getGrantedTotal(sender).size() + "/");
            }
            data.append(maxGranterTotal).append("`\n");
        }
        System.out.println(6.5);
        if (maxGranterDay > 0) {
            data.append("Daily(Granter): `");
            if (sender != null) {
                data.append(getGranted(TimeUnit.DAYS.toMillis(1), sender).size() + "/");
            }
            data.append(maxGranterDay).append("`\n");
        }
        System.out.println(6.6);

        data.append(toInfoString(sender, receiver, parsed));
        System.out.println(6.7);

        // receiver markdown
        if (sender != null && receiver != null) {
            System.out.println(6.8);
            double[] cost = getCost(sender, receiver, parsed);
            if (cost != null) {
                data.append("Cost: `").append(PnwUtil.resourcesToString(cost)).append("`\n");
            }
            System.out.println(6.9);
            List<Grant.Requirement> requirements = getDefaultRequirements(sender, receiver, parsed);
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
            System.out.println(6.11);
            if (!failedFinal.isEmpty()) {
                data.append("Errors:\n");
                for (Grant.Requirement requirement : failedFinal) {
                    data.append("- " + requirement.getMessage()).append("\n");
                }
            }
            System.out.println(6.12);
            if (!failedOverride.isEmpty()) {
                data.append("Warnings:\n");
                for (Grant.Requirement requirement : failedOverride) {
                    data.append("- " + requirement.getMessage()).append("\n");
                }
            }
            System.out.println(6.13);
            String instructions = this.getInstructions(sender, receiver, parsed);
            if (instructions != null && !instructions.isEmpty()) {
                data.append("Instructions:\n>>> ").append(instructions).append("\n");
            }
            System.out.println(6.14);
        } else {
            System.out.println(6.15);
            List<Grant.Requirement> requirements = getDefaultRequirements(sender, receiver, parsed);
            System.out.println(6.16);
            if (!requirements.isEmpty()) {
                data.append("\n**Template Checks:**\n");
                for (Grant.Requirement requirement : requirements) {
                    data.append("- " + requirement.getMessage()).append("\n");
                }
            }
            System.out.println(6.17);
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

    public List<Grant.Requirement> getDefaultRequirements(@Nullable DBNation sender, @Nullable DBNation receiver, T parsed) {
        List<Grant.Requirement> list = new ArrayList<>();

        NationFilter filter = getNationFilter();
        if (filter != null && !"*".equals(filter.getFilter())) {
            Predicate<DBNation> cached = filter.toCached(Long.MAX_VALUE);
            list.add(new Grant.Requirement("Nation does not match: `" + filter.getFilter() + "`", false, new Function<DBNation, Boolean>() {
                @Override
                public Boolean apply(DBNation nation) {
                    return cached.test(nation);
                }
            }));
        }

        // check grant not disabled
        list.add(new Grant.Requirement("Grant is disabled. See: " + CM.grant_template.enable.cmd.toSlashMention(), false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return AGrantTemplate.this.isEnabled();
            }
        }));

        // check grant limits (limit total/day, limit granter total/day)
        if (getMaxTotal() > 0) {
            list.add(new Grant.Requirement("Grant limit reached: " + getMaxTotal() + " total grants", false, new Function<DBNation, Boolean>() {
                @Override
                public Boolean apply(DBNation nation) {
                    return getGrantedTotal().size() < getMaxTotal();
                }
            }));
        }
        if (getMaxDay() > 0) {
            list.add(new Grant.Requirement("Grant limit reached: " + getMaxDay() + " grants per day", false, new Function<DBNation, Boolean>() {
                @Override
                public Boolean apply(DBNation nation) {
                    long oneDayMs = TimeUnit.DAYS.toMillis(1);
                    return getGranted(oneDayMs).size() < getMaxDay();
                }
            }));
        }
        if (getMaxGranterTotal() > 0) {
            list.add(new Grant.Requirement("Grant limit reached: " + getMaxGranterTotal() + " total grants send from " + (sender == null ? "sender" : sender.getName()), false, new Function<DBNation, Boolean>() {
                @Override
                public Boolean apply(DBNation nation) {
                    return getGrantedTotal(sender).size() < getMaxGranterTotal();
                }
            }));
        }
        if (getMaxGranterDay() > 0) {
            list.add(new Grant.Requirement("Grant limit reached: " + getMaxGranterDay() + " grants per day send from " + (sender == null ? "sender" : sender.getName()), false, new Function<DBNation, Boolean>() {
                @Override
                public Boolean apply(DBNation nation) {
                    long oneDayMs = TimeUnit.DAYS.toMillis(1);
                    return getGranted(oneDayMs, sender).size() < getMaxGranterDay();
                }
            }));
        }

        // check nation not received grant already
        list.add(new Grant.Requirement("Nation has already received this grant", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return db.getGrantTemplateManager().getRecordsByReceiver(nation.getId(), getName()).isEmpty();
            }
        }));

       // errors.computeIfAbsent(nation, f -> new ArrayList<>()).add("Nation was not found in guild");
        list.add(new Grant.Requirement("Nation is not verified: " + CM.register.cmd.toSlashMention(), false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                User user = nation.getUser();
                return user != null;
            }
        }));
        list.add(new Grant.Requirement("Nation is not in an alliance", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return nation.getAlliance_id() != 0;
            }
        }));
//                grant.addRequirement(new Grant.Requirement("Nation is not a member of an alliance", econGov, f -> f.getPosition() > 1));
        list.add(new Grant.Requirement("Nation is not a member of an alliance", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return nation.getPosition() > 1;
            }
        }));
//                grant.addRequirement(new Grant.Requirement("Nation is in VM", econGov, f -> f.getVm_turns() == 0));
        list.add(new Grant.Requirement("Nation is in VM", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return nation.getVm_turns() == 0;
            }
        }));
        // nation color cannot be NationColor.GRAY
        list.add(new Grant.Requirement("Nation is gray", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return nation.getColor() != NationColor.GRAY;
            }
        }));
//                grant.addRequirement(new Grant.Requirement("Nation is not in the alliance: " + alliance, econGov, f -> alliance != null && f.getAlliance_id() == alliance.getAlliance_id()));
        list.add(new Grant.Requirement("Nation is not in the alliance id: " + db.getAllianceIds(), false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return db.isAllianceId(nation.getAlliance_id());
            }
        }));

        Set<Integer> blacklist = GuildKey.GRANT_TEMPLATE_BLACKLIST.getOrNull(db);

        if(blacklist == null)
            blacklist = Collections.emptySet();

        Set<Integer> finalBlacklist = blacklist;
        list.add(new Grant.Requirement("Nation is blacklisted", false, new Function<DBNation, Boolean>() {
           @Override
           public Boolean apply(DBNation dbNation) {
               return !finalBlacklist.contains(dbNation.getId());
           }
       }));
//
//                grant.addRequirement(new Grant.Requirement("Nation is not active in past 24h", econStaff, f -> f.getActive_m() < 1440));
        list.add(new Grant.Requirement("Nation is not active in past 24h", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return nation.getActive_m() < 1440;
            }
        }));
        list.add(new Grant.Requirement("Nation does not have 10d seniority", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return nation.allianceSeniority() >= 10;
            }
        }));

        list.add(new Grant.Requirement("Nation is close to being beiged", true, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {

                return nation.minWarResistancePlusMap() > 30;
            }
        }));

        list.add(new Grant.Requirement("Nation is blockaded", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return !nation.isBlockaded();
            }
        }));

        List<Transaction2> transfers = receiver == null ? Collections.emptyList() : receiver.getTransactions(0L, true);
        long latest = !transfers.isEmpty() ? transfers.stream().mapToLong(Transaction2::getDate).max().getAsLong() : 0L;
        // require no new transfers
        list.add(new Grant.Requirement("Nation has received a transfer since attempting this grant, please try again", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                List<Transaction2> newTransfers = receiver.getTransactions(0L, true);
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
        list.add("allow_ignore");
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
            List<AbstractCursor> attacks = war.getAttacks2();
            // reverse attacks
            Collections.reverse(attacks);
            for (AbstractCursor attack : attacks) {
                if (attack.getAttacker_id() != receiver.getId()) {
                    return attack.getDate();
                }
            }
        }
        return 0;
    }

    public abstract List<String> getQueryFields();

    public String createQuery() {
        List<String> fields = getQueryFields();
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO `" + this.getType().getTable() + "` (");
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
        stmt.setBoolean(14, this.allowsExpire());
    }

    public long getDateCreated() {
        return dateCreated;
    }

    public abstract double[] getCost(DBNation sender, DBNation receiver, T parsed);
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
        grant.setCost(f -> this.getCost(sender, receiver, customValue));
        grant.addRequirement(getDefaultRequirements(sender, receiver, customValue));
        // grant.addNote()
        grant.setInstructions(getInstructions(sender, receiver, customValue));

        return grant;
    }

    public TaxBracket getTaxAccount(GuildDB db, DBNation receiver) {
        if (useReceiverBracket || fromBracket == receiver.getTax_id()) {
            return receiver.getTaxBracket();
        }
        if (this.fromBracket > 0) {
            return PWBindings.bracket(db, "#tax_id=" + fromBracket);
        }
        return null;
    }

    public boolean allowsExpire() {
        return this.expiryOrZero > 0;
    }

    public long getExpire() {
        return this.expiryOrZero;
    }

    public boolean allowsIgnore() {
        return this.allowIgnore;
    }
}
