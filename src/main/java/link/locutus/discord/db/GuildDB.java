package link.locutus.discord.db;

import com.google.common.eventbus.AsyncEventBus;
import com.politicsandwar.graphql.model.ApiKeyDetails;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.enums.AccessType;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.apiv3.subscription.PnwPusherShardManager;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.war.WarCategory;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.commands.trade.subbank.BankAlerts;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.BeigeReason;
import link.locutus.discord.pnw.CityRanges;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.pnw.json.CityBuildRange;
import link.locutus.discord.util.AuditType;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.offshore.test.IACategory;
import link.locutus.discord.util.task.roles.AutoRoleTask;
import link.locutus.discord.util.task.roles.IAutoRoleTask;
import com.google.common.eventbus.EventBus;
import com.google.gson.JsonObject;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.TreatyType;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class GuildDB extends DBMain implements NationOrAllianceOrGuild {
    private final Guild guild;
    private volatile IAutoRoleTask autoRoleTask;
    private GuildHandler handler;
    private IACategory iaCat;

    public GuildDB(Guild guild) throws SQLException, ClassNotFoundException {
        super("guilds/" + guild.getId());
        this.roleToAccountToDiscord  = new ConcurrentHashMap<>();
        this.guild = guild;
        System.out.println(guild + " | AA:" + StringMan.getString(getAllianceIds()));
        importLegacyRoles();
    }

    private void importLegacyRoles() {
        try {
            if (tableExists("ROLES")) {
                // get records from ROLES
                try (PreparedStatement stmt = prepareQuery("SELECT * FROM ROLES")) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String roleName = rs.getString("role");
                            long alias = rs.getLong("alias");
                            long alliance = rs.getLong("alliance");

                            Roles role = Roles.getRoleByNameLegacy(roleName);
                            if (role == null) {
                                throw new IllegalArgumentException("Unknown legacy role: " + roleName);
                            }

                            addRole(role, alias, alliance);
                        }
                    }
                }
                // drop tables ROLES
                executeStmt("DROP TABLE ROLES");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setAutoRoleTask(IAutoRoleTask autoRoleTask) {
        this.autoRoleTask = autoRoleTask;
    }

    public synchronized IACategory getExistingIACategory() {
        return iaCat;
    }

    public synchronized IACategory getIACategory() {
        return getIACategory(false, true, false);
    }
    public synchronized IACategory getIACategory(boolean create, boolean allowDelegate, boolean throwError) {
        GuildDB delegate = allowDelegate ? getDelegateServer() : null;
        if (delegate != null && delegate.iaCat != null) {
            return delegate.iaCat;
        }
        if (this.iaCat == null) {
            boolean hasInterview = false;
            for (Category category : guild.getCategories()) {
                if (category.getName().toLowerCase().startsWith("interview")) {
                    hasInterview = true;
                }
            }

            if (hasInterview) {
                this.iaCat = new IACategory(this);
                this.iaCat.load();
            }
        }
        if (iaCat == null && delegate != null) {
            iaCat = delegate.getIACategory(false, false, throwError);
        }
        if (iaCat == null && create) {
            Category category = guild.createCategory("interview").complete();
            this.iaCat = new IACategory(this);
            this.iaCat.load();
        }
        if (iaCat == null && throwError) {
            throw new IllegalStateException("No `interview` category found");
        }
        return this.iaCat;
    }

    private EventBus eventBus = null;

    public void postEvent(Object event) {
        EventBus bus = getEventBus();
        if (bus != null) bus.post(event);
    }

    public EventBus getEventBus() {
        if (this.eventBus == null) {
            if (hasAlliance()) {
                synchronized (this) {
                    if (this.eventBus == null) {
                        this.eventBus = new AsyncEventBus(getGuild().toString(), Runnable::run);
                        eventBus.register(getHandler());
                    }
                }
            }
        }
        return eventBus;
    }

    public synchronized void setHandler(GuildHandler handler) {
        if (eventBus == null) {
            this.eventBus = new AsyncEventBus(getGuild().toString(), Runnable::run);
        } else if (this.handler != null) {
            eventBus.unregister(this.handler);
        }
        this.handler = handler;
        this.eventBus.register(this.handler);
    }

    public synchronized GuildHandler getHandler() {
        if (handler == null) {
            this.handler = new GuildHandler(guild, this);
        }
        return handler;
    }

    public Guild getGuild() {
        return guild;
    }

    public ApiKeyPool getMailKey() {
        Set<Integer> aaIds = getAllianceIds();
        Set<Integer> allowedNations = Settings.INSTANCE.TASKS.MAIL.getInstances().stream().map(f -> f.NATION_ID).collect(Collectors.toSet());
        for (int nationId : allowedNations) {
            DBNation nation = DBNation.byId(nationId);
            if (nation == null || !aaIds.contains(nation.getAlliance_id())) continue;
            ApiKeyPool.ApiKey key = nation.getApiKey(false);
            if (key != null) return ApiKeyPool.builder().addKey(key).build();
        }

        Map.Entry<Integer, String> mailKeysBackup = null;
        String[] apiKeys = getOrNull(Key.API_KEY);
        if (apiKeys != null) {
            for (String key : apiKeys) {
                Integer nationId = Locutus.imp().getDiscordDB().getNationFromApiKey(key);
                if (nationId != null) {
                    DBNation nation = DBNation.byId(nationId);
                    if (nation != null) {
                        if (aaIds.contains(nation.getAlliance_id())) {
                            return ApiKeyPool.builder().addKey(nationId, key).build();
                        } else if (mailKeysBackup == null) {
                            mailKeysBackup = (Map.entry(nationId, key));
                        }
                    }
                }
            }
        }
        if (mailKeysBackup != null) {
            return ApiKeyPool.builder().addKey(mailKeysBackup.getKey(), mailKeysBackup.getValue()).build();
        }

        return null;
    }

    public boolean hasCoalitionPermsOnRoot(Coalition coalition) {
        return hasCoalitionPermsOnRoot(coalition.name().toLowerCase());
    }

    public boolean hasCoalitionPermsOnRoot(String coalition) {
        return hasCoalitionPermsOnRoot(coalition, true);
    }
    public boolean hasCoalitionPermsOnRoot(String coalition, boolean allowDelegate) {
        Set<Integer> aaids = getAllianceIds();

        Guild rootServer = Locutus.imp().getServer();
        GuildDB rootDB = Locutus.imp().getGuildDB(rootServer);
        if (this == rootDB) return true;
        Set<Long> coalMembers = rootDB.getCoalitionRaw(coalition);
        if (coalMembers.contains(getIdLong())) {
            return true;
        }
        for (Integer id : aaids) {
            if (coalMembers.contains(id.longValue())) {
                return true;
            }
        }
        if (allowDelegate) {
            GuildDB delegate = getDelegateServer();
            if (delegate != null) {
                return delegate.hasCoalitionPermsOnRoot(coalition);
            }
        }
        return false;
    }

    public <T> T getOrThrow(Key key) {
        T value = getOrNull(key);
        if (value == null) {
            throw new UnsupportedOperationException("No " + key + " registered. Use " + CM.settings.cmd.create(key.name(), null, null, null));
        }
        return value;
    }

    public <T> T getOrNull(Key key, boolean allowDelegate) {
        Object parsed;
        synchronized (infoParsed) {
            parsed = infoParsed.getOrDefault(key, nullInstance);
        }
        if (parsed != nullInstance) return (T) parsed;

        boolean isDelegate = false;
        String value = getInfo(key, false);
        if (value == null) {
            isDelegate = true;
            if (allowDelegate) {
                value = getInfo(key, true);
            }
        }
        if (value == null) return null;
        try {
            parsed =  (T) key.parse(this, value);
            if (!isDelegate) {
                synchronized (infoParsed) {
                    infoParsed.put(key, parsed);
                }
            }
            return (T) parsed;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public <T> T getOrNull(Key key) {
        return getOrNull(key, true);
    }

    public Map<String,String> getKeys() {
        return Collections.unmodifiableMap(info);
    }

    public void setInternalTaxRate(DBNation nation, TaxRate taxRate) {
        setMeta(nation.getNation_id(), NationMeta.TAX_RATE, new byte[]{(byte) taxRate.money, (byte) taxRate.resources});
    }

    public String generateEscrowedCard(DBNation nation) throws IOException {
        double[] disburse = getEscrowed(nation, true, false, false);
        double[] topUp = getEscrowed(nation, false, true, false);
        double[] extra = getEscrowed(nation, false, false, true);
        double[] escrowed = getEscrowed(nation, false, false, true);

        double[] deposits = nation.getNetDeposits(this, false);

        User user = nation.getUser();

        StringBuilder body = new StringBuilder();
        if (user != null) body.append("User: " + user.getAsMention() + "\n");
        body.append("Receiver: " + nation.getNationUrlMarkup(true));
        if (nation.getPosition() <= Rank.APPLICANT.id) body.append(" (applicant)");
        body.append("\n");
        if (nation.getActive_m() > 1440) {
            body.append("Inactive: " + TimeUtil.minutesToTime(nation.getActive_m()) + "\n");
        }
        if (nation.isGray() || nation.isBeige()) {
            body.append("Color: " + nation.getColor() + "\n");
        }
        if (nation.getNumWars() > 0) {
            body.append("Off/Def: " + nation.getOff() + "/" + nation.getDef() + "\n");

            body.append("Relative Strength: " + MathMan.format(nation.getRelativeStrength() * 100) + "%\n");

            double relativeGround = 1;
            double relativeAir = 1;
            double relativeSea = 1;
            for (DBWar war : nation.getActiveWars()) {
                DBNation other = war.getNation(!war.isAttacker(nation));
                if (other.getActive_m() > 2880) continue;

                double otherGround = other.getGroundStrength(true, false);
                double otherAir = other.getAircraft();
                double otherShip = other.getShips();

                if (otherGround > 0) relativeGround = Math.min(relativeGround, nation.getGroundStrength(true, false) / otherGround);
                if (otherAir > 0) relativeAir = Math.min(relativeAir, nation.getAircraft() / otherAir);
                if (otherShip > 0)  relativeSea = Math.min(relativeSea, nation.getShips() / otherShip);
            }

            body.append("Ground: " + MathMan.format(100 * relativeGround) + "%" + " | Air: " + MathMan.format(100 * relativeAir) + "%" + " | Sea: " + MathMan.format(100 * relativeSea) + "%\n");
        }
        body.append("mmr[build]: " + nation.getMMRBuildingStr() + "\n");
        body.append("mmr[unit]: " + nation.getMMR() + "\n\n");

        body.append("**Deposits:**\n`" + PnwUtil.resourcesToString(deposits) + "` worth: ~$" + MathMan.format(PnwUtil.convertedTotal(deposits)) + "\n");
        body.append(StringMan.repeat("\u2501", 8) + "\n");
        int typesEscrowed = 0;
        if (disburse != null) {
            typesEscrowed++;
            ByteBuffer escrowedDisburseBuf = getNationMeta(nation.getNation_id(), NationMeta.ESCROWED_DISBURSE_DAYS);
            int days = escrowedDisburseBuf.get() & 0xFF;
            body.append("**Disburse " + days + "d:**\n`" + PnwUtil.resourcesToString(disburse) + "` worth: ~$" + MathMan.format(PnwUtil.convertedTotal(disburse)) + "\n");
        }
        if (topUp != null) {
            typesEscrowed++;
            body.append("**Top Up:**\n`" + PnwUtil.resourcesToString(topUp) + "` worth: ~$" + MathMan.format(PnwUtil.convertedTotal(topUp)) + "\n");
        }
        if (escrowed != null) {
            typesEscrowed++;
            body.append("**Additional:**\n`" + PnwUtil.resourcesToString(extra) + "` worth: ~$" + MathMan.format(PnwUtil.convertedTotal(extra)) + "\n");
        }
        if (typesEscrowed > 1 || true) {
            body.append(StringMan.repeat("\u2501", 8) + "\n");
            body.append("**Total**:\n");
            for (int i = 0; i < escrowed.length; i++) {
                if (escrowed[i] == 0) continue;
                body.append(ResourceType.values[i].name().toLowerCase() + "=");
                boolean underline = escrowed[i] < deposits[i];
                if (underline) body.append("__");
                body.append(MathMan.format(escrowed[i]));
                if (underline) body.append("__");
                body.append("\n");
            }
            double totalValue = PnwUtil.convertedTotal(topUp);
            body.append("Total Worth: $" + MathMan.format(totalValue));
            if (totalValue + 1> PnwUtil.convertedTotal(deposits)) {
                body.append(" (insufficient deposits)");
            }

            body.append("**Total:**\n`" + PnwUtil.resourcesToString(escrowed) + "` worth: ~$" + MathMan.format(PnwUtil.convertedTotal(escrowed)) + "\n");
        }
        body.append("\nPress `Send` to confirm transfer");
        return body.toString();
    }

    public double[] getEscrowed(DBNation nation) throws IOException {
        return getEscrowed(nation, true, true, true);
    }

    public double[] getEscrowed(DBNation nation, boolean disburse, boolean topUp, boolean extra) throws IOException {
        ByteBuffer escrowedBuf = getNationMeta(nation.getNation_id(), NationMeta.ESCROWED);
        ByteBuffer escrowedTopBuf = getNationMeta(nation.getNation_id(), NationMeta.ESCROWED_UP_TO);
        ByteBuffer escrowedDisburseBuf = getNationMeta(nation.getNation_id(), NationMeta.ESCROWED_DISBURSE_DAYS);

        if (escrowedBuf == null && escrowedTopBuf == null && escrowedDisburseBuf == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        double[] toSend = ResourceType.getBuffer();
        Map<ResourceType, Double> stockpile = null;
        if (escrowedDisburseBuf != null) {
            long expire = escrowedDisburseBuf.getLong();
            if (expire > now) {
                int days = escrowedDisburseBuf.get() & 0xFF;
                if (stockpile == null) stockpile = nation.getStockpile();
                Map<ResourceType, Double> resources = nation.getResourcesNeeded(stockpile, days, false);
                for (Map.Entry<ResourceType, Double> entry : resources.entrySet()) {
                    toSend[entry.getKey().ordinal()] += entry.getValue();
                }
            } else {
                deleteMeta(nation.getNation_id(), NationMeta.ESCROWED_DISBURSE_DAYS);
            }
        }
        if (escrowedTopBuf != null) {
            long expire = escrowedTopBuf.getLong();
            if (expire > now) {
                double[] resources = ResourceType.read(escrowedBuf, null);
                if (stockpile == null) stockpile = nation.getStockpile();
                for (ResourceType type : ResourceType.values) {
                    double amt = resources[type.ordinal()];
                    amt = Math.min(amt, stockpile.getOrDefault(type, 0d));
                    if (amt > 0) {
                        toSend[type.ordinal()] = amt;
                    }
                }
            } else {
                deleteMeta(nation.getNation_id(), NationMeta.ESCROWED_UP_TO);
            }
        }
        if (escrowedBuf != null) {
            long expire = escrowedBuf.getLong();
            if (expire > now) {
                ResourceType.read(escrowedBuf, toSend);
            } else {
                deleteMeta(nation.getNation_id(), NationMeta.ESCROWED);
            }
        }
        return toSend;
    }

    public void setMeta(long userId, NationMeta key, byte[] value) {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            delegate.setMeta(userId, key, value);
            return;
        }
        checkNotNull(key);
        checkNotNull(value);
        update("INSERT OR REPLACE INTO `NATION_META`(`id`, `key`, `meta`) VALUES(?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, userId);
            stmt.setInt(2, key.ordinal());
            stmt.setBytes(3, value);
        });
    }

    public Map<DBNation, byte[]> getNationMetaMap(NationMeta key) {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            return delegate.getNationMetaMap(key);
        }
        Map<DBNation, byte[]> result = new HashMap<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_META where key = ?")) {
            stmt.setInt(1, key.ordinal());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long nationId = rs.getInt("id");
                    if (nationId < Integer.MAX_VALUE) {
                        DBNation nation = Locutus.imp().getNationDB().getNation((int) nationId);
                        byte[] data = rs.getBytes("meta");

                        result.put(nation, data);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public ByteBuffer getNationMeta(int nationId, NationMeta key) {
        return getMeta((long) nationId, key);
    }

    public Map<Integer, TaxRate> getInternalTaxRates() {
        Map<Integer, TaxRate> taxRates = new HashMap<>();
        Map<Long, ByteBuffer> metaMap = getAllMeta(NationMeta.TAX_RATE);
        for (Map.Entry<Long, ByteBuffer> entry : metaMap.entrySet()) {
            ByteBuffer buf = entry.getValue();
            int moneyRate = buf.get();
            int  resourceRate = buf.get();
            TaxRate taxRate = new TaxRate(moneyRate, resourceRate);
            taxRates.put(entry.getKey().intValue(), taxRate);
        }
        return taxRates;
    }

    public Map<Long, ByteBuffer> getAllMeta(NationMeta key) {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            return delegate.getAllMeta(key);
        }
        Map<Long, ByteBuffer> results = new HashMap<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_META where AND key = ?")) {
            stmt.setInt(1, key.ordinal());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    ByteBuffer buf = ByteBuffer.wrap(rs.getBytes("meta"));
                    results.put(id, buf);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    public ByteBuffer getMeta(long userId, NationMeta key) {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            return delegate.getMeta(userId, key);
        }
        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_META where id = ? AND key = ?")) {
            stmt.setLong(1, userId);
            stmt.setInt(2, key.ordinal());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    return ByteBuffer.wrap(rs.getBytes("meta"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void deleteMeta(long userId, NationMeta key) {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            delegate.deleteMeta(userId, key);
            return;
        }
        update("DELETE FROM NATION_META where id = ? AND key = ?", new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setLong(1, userId);
                stmt.setInt(2, key.ordinal());
            }
        });
    }

    @Override
    public long getIdLong() {
        return guild.getIdLong();
    }

    @Override
    public boolean isAlliance() {
        return false;
    }

    @Override
    public int getAlliance_id() {
        throw new UnsupportedOperationException("Not an alliance");
    }

    public boolean hasAlliance() {
        return getOrNull(Key.ALLIANCE_ID) != null;
    }

    /**
     * @return the alliances registered, or null
     */
    public AllianceList getAllianceList() {
        Set<Integer> ids = getAllianceIds();
        if (ids.isEmpty()) return null;
        return new AllianceList(ids);
    }

    @Override
    public String getName() {
        return guild.getName() + "/" + guild.getIdLong();
    }

    public String getUrl() {
        List<Invite> invites = RateLimitUtil.complete(guild.retrieveInvites());
        for (Invite invite : invites) {
            if (invite.getMaxUses() == 0) {
                return invite.getUrl();
            }
        }
        return null;
    }

    public void addTransactions(List<Transaction2> transactions) {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            delegate.addTransactions(transactions);
            return;
        }
        if (transactions.isEmpty()) return;
        String query = transactions.get(0).createInsert("INTERNAL_TRANSACTIONS2", false, false);
        executeBatch(transactions, query, (ThrowingBiConsumer<Transaction2, PreparedStatement>) Transaction2::setNoID);
    }

    public void addTransaction(Transaction2 tx) {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            delegate.addTransaction(tx);
            return;
        }
        String sql = tx.createInsert("INTERNAL_TRANSACTIONS2", false, false);
        update(sql, (ThrowingConsumer<PreparedStatement>) tx::setNoID);

        GuildMessageChannel output = getOrNull(Key.ADDBALANCE_ALERT_CHANNEL);
        if (output != null) {
            try {
                RateLimitUtil.queueWhenFree(output.sendMessage(tx.toString()));
            } catch (Throwable ignore) {
                ignore.printStackTrace();
            }
        }
    }

    public List<Key> listInaccessibleChannelKeys() {
        List<Key> inaccessible = new ArrayList<>();
        for (Key key : Key.values()) {
            String valueStr = getInfo(key, false);
            if (valueStr == null) continue;
            Object value = key.parse(this, valueStr);
            if (value == null) {
                inaccessible.add(key);
                continue;
            }
            if (value instanceof GuildMessageChannel) {
                GuildMessageChannel channel = (GuildMessageChannel) value;
                if (!channel.canTalk()) {
                    inaccessible.add(key);
                }
            }
        }
        return inaccessible;
    }
    public void unsetInaccessibleChannels() {
        for (Key key : listInaccessibleChannelKeys()) {
            deleteInfo(key);
        }
    }
    public void deleteExpire_bugFix() {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            delegate.deleteExpire_bugFix();
            return;
        }
        String query = "DELETE FROM INTERNAL_TRANSACTIONS2 WHERE lower(note) like \"%timestamp:%\"";
        executeStmt(query);
    }

    public void updateNoteDate_bugFix() {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            delegate.updateNoteDate_bugFix();
            return;
        }
        long date = System.currentTimeMillis();
        String query = "UPDATE INTERNAL_TRANSACTIONS2 set tx_datetime = " + date + " where lower(note) like \"%timestamp:%\"";
        executeStmt(query);
    }

    public List<Transaction2> getTransactionsByNote(String note, boolean fuzzy) {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            return delegate.getTransactionsByNote(note, fuzzy);
        }
        List<Transaction2> list = new ArrayList<>();
        String query;
        if (fuzzy) {
            query = "select * FROM INTERNAL_TRANSACTIONS2 WHERE lower(note) like ?";
            note = ("%" + note + "%").toLowerCase();
        } else {
            query = "select * FROM INTERNAL_TRANSACTIONS2 WHERE note = ?";
        }
        try (PreparedStatement stmt = prepareQuery(query)) {
            stmt.setString(1, note);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Transaction2(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Transaction2> getTransactionsById(long senderOrReceiverId, int type) {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            return delegate.getTransactionsById(senderOrReceiverId, type);
        }
        List<Transaction2> list = new ArrayList<>();

        String query = "select * FROM INTERNAL_TRANSACTIONS2 WHERE ((sender_id = ? AND sender_TYPE = ?) OR (receiver_id = ? AND receiver_type = ?))";

        query(query, new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setLong(1, senderOrReceiverId);
                stmt.setInt(2, type);
                stmt.setLong(3, senderOrReceiverId);
                stmt.setInt(4, type);
            }
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                list.add(new Transaction2(rs));
            }
        });
        return list;
    }

    public List<Transaction2> getTransactions(long minDateMs, boolean desc) {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            return delegate.getTransactions(minDateMs, desc);
        }
        List<Transaction2> list = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM INTERNAL_TRANSACTIONS2 WHERE tx_datetime > ? ORDER BY tx_id " + (desc ? "DESC" : "ASC"))) {
            stmt.setLong(1, minDateMs);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Transaction2(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void createTables() {
        {

            StringBuilder query = new StringBuilder("CREATE TABLE IF NOT EXISTS `INTERNAL_TRANSACTIONS2` (" +
                    "`tx_id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "tx_datetime BIGINT NOT NULL, " +
                    "sender_id BIGINT NOT NULL, " +
                    "sender_type INT NOT NULL, " +
                    "receiver_id BIGINT NOT NULL, " +
                    "receiver_type INT NOT NULL, " +
                    "banker_nation_id INT NOT NULL, " +
                    "note varchar");

            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) continue;
                query.append(", " + type.name() + " BIGINT NOT NULL");
            }
            query.append(")");

            executeStmt(query.toString());
        }
        {
            String nations = "CREATE TABLE IF NOT EXISTS `TRANSACTIONS` (`id` INT NOT NULL PRIMARY KEY, `from` BIGINT NOT NULL, `to` BIGINT NOT NULL, `resources` BLOB NOT NULL, `note` VARCHAR)";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(nations);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };

        {
            String query = "CREATE TABLE IF NOT EXISTS `ANNOUNCEMENTS2` (`ann_id` INTEGER PRIMARY KEY AUTOINCREMENT, `sender` BIGINT NOT NULL, `active` BOOLEAN NOT NULL, `title` VARCHAR NOT NULL, `content` VARCHAR NOT NULL, `replacements` VARCHAR NOT NULL, `filter` VARCHAR NOT NULL, `date` BIGINT NOT NULL)";
            executeStmt(query);

            String query2 = "CREATE TABLE IF NOT EXISTS `ANNOUNCEMENTS_PLAYER2` (`receiver` INT NOT NULL, `ann_id` INT NOT NULL, `active` BOOLEAN NOT NULL, `diff` BLOB NOT NULL, PRIMARY KEY(receiver, ann_id), FOREIGN KEY(ann_id) REFERENCES ANNOUNCEMENTS2(ann_id))";
            executeStmt(query2);
        }

//        {
//            String query = "CREATE TABLE IF NOT EXISTS `ORDERED_COUNTERS` (`ann_id` INTEGER PRIMARY KEY AUTOINCREMENT, `sender` INT NOT NULL, `active` BOOLEAN NOT NULL, `title` VARCHAR NOT NULL, `content` VARCHAR NOT NULL, `replacements` VARCHAR NOT NULL, `filter` VARCHAR NOT NULL, `date` INT NOT NULL)";
//            executeStmt(query);
//        }

        {
            String query = "CREATE TABLE IF NOT EXISTS `INTERVIEW_MESSAGES2` (`message_id` BIGINT NOT NULL PRIMARY KEY, `channel_id` BIGINT NOT NULL, `sender` BIGINT NOT NULL, `date_created` BIGINT NOT NULL, `date_updated` BIGINT NOT NULL, `message` VARCHAR NOT NULL)";
            executeStmt(query);
            purgeOldInterviews(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14));
        }

        {
            String nations = "CREATE TABLE IF NOT EXISTS `NATION_META` (`id` BIGINT NOT NULL, `key` BIGINT NOT NULL, `meta` BLOB NOT NULL, PRIMARY KEY(id, key))";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(nations);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };

        {
            String create = "CREATE TABLE IF NOT EXISTS `ROLES2` (`role` BIGINT NOT NULL, `alias` BIGINT NOT NULL, `alliance` BIGINT NOT NULL)";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(create);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };
        {
            String create = "CREATE TABLE IF NOT EXISTS `COALITIONS` (`alliance_id` BIGINT NOT NULL, `coalition` VARCHAR NOT NULL, PRIMARY KEY(alliance_id, coalition))";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(create);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };
        {
            String create = "CREATE TABLE IF NOT EXISTS `BUILDS` (`category` VARCHAR NOT NULL, `min` INT NOT NULL, `max` INT NOT NULL, `build` VARCHAR NOT NULL, PRIMARY KEY(category, min))";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(create);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };
        {
            String create = "CREATE TABLE IF NOT EXISTS `PERMISSIONS` (`permission` VARCHAR NOT NULL PRIMARY KEY, `value` INT NOT NULL)";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(create);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };
        {
            String create = "CREATE TABLE IF NOT EXISTS `INFO` (`key` VARCHAR NOT NULL PRIMARY KEY, `value` VARCHAR NOT NULL)";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(create);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };
        // deprecated
//        {
//            String create = "CREATE TABLE IF NOT EXISTS `BANK_DEPOSIT` (`nationId` INT NOT NULL, `resource` INT NOT NULL, `amount` INT NOT NULL, `note` VARCHAR NOT NULL, PRIMARY KEY(nationId, resource, note))";
//            try (Statement stmt = getConnection().createStatement()) {
//                stmt.addBatch(create);
//                stmt.executeBatch();
//                stmt.clearBatch();
//            } catch (SQLException e) {
//                e.printStackTrace();
//            }
//        };
        {
            String create = "CREATE TABLE IF NOT EXISTS `LOANS` (`loan_id` INTEGER PRIMARY KEY AUTOINCREMENT, `server` BIGINT NOT NULL, `message`, `receiver` INT NOT NULL, `resources` BLOB NOT NULL, `due` BIGINT NOT NULL, `repaid` BIGINT NOT NULL)";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(create);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };

//        {
//            String create = "CREATE TABLE IF NOT EXISTS `BEIGE_TARGET_ALERTS` (`user` INT NOT NULL, `target`)";
//            try (Statement stmt = getConnection().createStatement()) {
//                stmt.addBatch(create);
//                stmt.executeBatch();
//                stmt.clearBatch();
//            } catch (SQLException e) {
//                e.printStackTrace();
//            }
//        };

//        {
//            List<String> stmts = new ArrayList<>();
//            stmts.add("CREATE TEMPORARY TABLE BANK_DEPOSIT_backup(`nationId` INT NOT NULL, `resource` INT NOT NULL, `amount` INT NOT NULL, `note` VARCHAR, PRIMARY KEY(nationId, resource, note));");
//            stmts.add("INSERT INTO BANK_DEPOSIT_backup(nationId, resource, amount) SELECT nationId, resource, amount FROM BANK_DEPOSIT;");
//            stmts.add("DROP TABLE BANK_DEPOSIT;");
//            stmts.add("CREATE TABLE BANK_DEPOSIT(`nationId` INT NOT NULL, `resource` INT NOT NULL, `amount` INT NOT NULL, `note` VARCHAR NOT NULL, PRIMARY KEY(nationId, resource, note));");
//            stmts.add("INSERT INTO BANK_DEPOSIT SELECT * FROM BANK_DEPOSIT_backup;");
//            stmts.add("DROP TABLE BANK_DEPOSIT_backup;");
//            for (String create : stmts) {
//                try (PreparedStatement stmt = getConnection().prepareStatement(create)) {
//                    stmt.executeUpdate();
//                    getConnection().commit();
//                } catch (SQLException e) {
//                    e.printStackTrace();
//                }
//            }
//        });
//        {
//            String create = "UPDATE BANK_DEPOSIT SET note = '' WHERE note IS NULL";
//            try (Statement stmt = getConnection().createStatement()) {
//                stmt.addBatch(create);
//                stmt.executeBatch();
//                stmt.clearBatch();
//            } catch (SQLException e) {
//                e.printStackTrace();
//            }
//        });
    }

//    public void unsubscribeAllBeige(User user) {
//        update("DELETE FROM `BEIGE_TARGET_ALERTS` WHERE user = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
//            stmt.setLong(1, user.getIdLong());
//        });
//    }
//
//    public void unsubscribeBeige(User user, int nationId) {
//        update("DELETE FROM `BEIGE_TARGET_ALERTS` WHERE user = ? AND target = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
//            stmt.setLong(1, user.getIdLong());
//            stmt.setInt(2, nationId);
//        });
//    }
//
//    public void subscribeBeige(User user, int nationId) {
//        update("INSERT OR IGNORE INTO `BEIGE_TARGET_ALERTS`(`user`, `target`) VALUES(?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
//            stmt.setLong(1, user.getIdLong());
//            stmt.setInt(2, nationId);
//        });
//    }
//
//    public Set<DBNation> getBeigeSubscriptions(User user) {
//            Set<DBNation> set = new LinkedHashSet<>();
//        try (PreparedStatement stmt = prepareQuery("select * FROM BEIGE_TARGET_ALERTS WHERE user = ?")) {
//            stmt.setLong(1, user.getIdLong());
//            try (ResultSet rs = stmt.executeQuery()) {
//                while (rs.next()) {
//                    int target = rs.getInt("target");
//                    DBNation nation = Locutus.imp().getNationDB().getNation(target);
//                    if (nation != null) {
//                        set.add(nation);
//                    } else {
//                        rs.deleteRow();
//                    }
//                }
//            }
//            return set;
//        } catch (SQLException e) {
//            e.printStackTrace();
//            return null;
//        }
//    }

    public void purgeOldInterviews(long cutoff) {
        update("DELETE FROM INTERVIEW_MESSAGES2 WHERE date_updated < ?", (ThrowingConsumer<PreparedStatement>) stmt -> stmt.setLong(1, cutoff));
    }

    public void addInterviewMessage(Message message, boolean createMessageOnFail) {
        User author = message.getAuthor();
        GuildMessageChannel channel = message.getGuildChannel();
        long channelId = channel.getIdLong();
        long date = message.getTimeCreated().toInstant().toEpochMilli();
        long now = System.currentTimeMillis();

        if (author.isBot() || author.isSystem() || message.isWebhookMessage()) {
            if (createMessageOnFail) {
                addInterviewMessage(channelId, author.getIdLong(), message.getIdLong(), date, now, DiscordUtil.trimContent(message.getContentRaw()));
            } else {
                updateInterviewMessageDate(channelId);
            }
        } else {
            addInterviewMessage(channelId, author.getIdLong(), message.getIdLong(), date, now, DiscordUtil.trimContent(message.getContentRaw()));
        }

    }

    public void addInterviewMessage(long channelId, long sender, long message_id, long dateCreated, long dateUpdated, String message) {
        ByteBuffer optOut = DiscordMeta.OPT_OUT.get(sender);
        if (optOut != null && optOut.get() != 0) return;

        // "CREATE TABLE IF NOT EXISTS `INTERVIEW_MESSAGES2` (`channel_id` INTEGER NOT NULL PRIMARY KEY, `sender` INT NOT NULL, `date` INT NOT NULL, `message` VARCHAR NOT NULL)";
        String query = "INSERT OR IGNORE INTO INTERVIEW_MESSAGES2(`channel_id`, `sender`, `message_id`, `date_created`, `date_updated`, `message`) VALUES(?, ?, ?, ?, ?, ?)";
        update(query , (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, channelId);
            stmt.setLong(2, sender);
            stmt.setLong(3, message_id);
            stmt.setLong(4, dateCreated);
            stmt.setLong(5, dateUpdated);
            stmt.setString(6, message);
        });
    }

    public void updateInterviewMessageDate(long channelId) {
        String query = "UPDATE INTERVIEW_MESSAGES2 SET `date_updated` = ? WHERE `channel_id` = ? AND date_updated = (SELECT MAX(date_updated) FROM INTERVIEW_MESSAGES2 WHERE `channel_id` = ?)";
        update(query, (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setLong(2, channelId);
            stmt.setLong(3, channelId);
        });
    }

    public Map<Long, InterviewMessage> getLatestInterviewMessages() {
        IACategory iaCat = getIACategory();
        if (iaCat == null) return null;
        iaCat.load();

        Map<Long, InterviewMessage> result = new LinkedHashMap<>();
        query("select * FROM INTERVIEW_MESSAGES2 ORDER BY date_created desc",
                f -> {},
                (ThrowingConsumer<ResultSet>) rs -> {
                    while (rs.next()) {
                        InterviewMessage msg = new InterviewMessage(rs);
                        InterviewMessage existing = result.get(msg.channelId);
                        if (existing == null || existing.date_created < msg.date_created) {
                            result.put(msg.channelId, msg);
                        }
                    }
                });
        return result;
    }

    public void deleteInterviewMessages(long userId) {
        update("DELETE FROM INTERVIEW_MESSAGES2 WHERE sender = ?", (ThrowingConsumer<PreparedStatement>) stmt -> stmt.setLong(1, userId));
    }

    public Map<Long, List<InterviewMessage>> getInterviewMessages() {
        IACategory iaCat = getIACategory();
        if (iaCat == null) return null;
        iaCat.load();

        Map<Long, List<InterviewMessage>> result = new LinkedHashMap<>();
        query("select * FROM INTERVIEW_MESSAGES2 ORDER BY date_created desc",
        f -> {},
        (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                InterviewMessage msg = new InterviewMessage(rs);
                result.computeIfAbsent(msg.channelId, f -> new ArrayList<>()).add(msg);
            }
        });
        return result;
    }

    public int addAnnouncement(User sender, String title, String content, String replacements, String filter) {
        String query = "INSERT INTO `ANNOUNCEMENTS2`(`sender`, `active`, `title`, `content`, `replacements`, `filter`, `date`) VALUES(?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = getConnection().prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, sender.getIdLong());
            stmt.setBoolean(2, true);
            stmt.setString(3, title);
            stmt.setString(4, content);
            stmt.setString(5, replacements);
            stmt.setString(6, filter);
            stmt.setLong(7, System.currentTimeMillis());
            stmt.executeUpdate();

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    return id;
                }
            }
            throw new IllegalArgumentException("Error creating announcement");
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void addPlayerAnnouncement(DBNation receiver, int annId, byte[] diff) {
        String query = "INSERT INTO ANNOUNCEMENTS_PLAYER2(`receiver`, `ann_id`, `active`, `diff`) VALUES(?, ?, ?, ?)";
        update(query , (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, receiver.getNation_id());
            stmt.setInt(2, annId);
            stmt.setBoolean(3, true);
            stmt.setBytes(4, diff);
        });
    }

    public List<Announcement> getAnnouncements() {
        List<Announcement> result = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM ANNOUNCEMENTS2 ORDER BY date desc")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new Announcement(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public Announcement getAnnouncement(int ann_id) {
        List<Announcement> result = new ArrayList<>();
        query("select * FROM ANNOUNCEMENTS2 WHERE ann_id = ? ORDER BY date desc",
                (ThrowingConsumer<PreparedStatement>) stmt -> stmt.setInt(1, ann_id),
                (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                result.add(new Announcement(rs));
            }
        });
        return result.isEmpty() ? null : result.get(0);
    }

    public Map<Integer, Announcement> getAnnouncementsByIds(Set<Integer> ids) {
        Map<Integer, Announcement> result = new LinkedHashMap<>();
        query("select * FROM ANNOUNCEMENTS2 WHERE ann_id in " + StringMan.getString(ids) + " ORDER BY date desc",
        f -> {},
        (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                Announcement ann = new Announcement(rs);
                result.put(ann.id, ann);
            }
        });
        return result;
    }

    public void setAnnouncementActive(int ann_id, boolean value) {
        update("UPDATE ANNOUNCEMENTS2 SET active = ? WHERE ann_id = ?", new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setBoolean(1, value);
                stmt.setInt(2, ann_id);
            }
        });
    }

    public void setAnnouncementActive(int ann_id, int nation_id, boolean active) {
        update("UPDATE ANNOUNCEMENTS_PLAYER2 SET active = ? WHERE ann_id = ? AND receiver = ?", new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setBoolean(1, active);
                stmt.setInt(2, ann_id);
                stmt.setInt(3, nation_id);
            }
        });
    }

    public Map<Announcement, List<Announcement.PlayerAnnouncement>> getAllPlayerAnnouncements(boolean allowArchived) {
        List<Announcement> announcements = getAnnouncements();
        Map<Integer, Announcement> announcementsById = new LinkedHashMap<>();
        for (Announcement announcement : announcements) {
            if (!announcement.active && !allowArchived) continue;
            announcementsById.put(announcement.id, announcement);
        }

        Map<Announcement, List<Announcement.PlayerAnnouncement>> result = new LinkedHashMap<>();
        query("select * FROM ANNOUNCEMENTS_PLAYER2 ORDER BY ann_id desc",
                f -> {},
                (ThrowingConsumer<ResultSet>) rs -> {
                    while (rs.next()) {
                        int annId = rs.getInt("ann_id");
                        Announcement announcement = announcementsById.get(annId);
                        if (announcement == null) continue;
                        Announcement.PlayerAnnouncement plrAnn = new Announcement.PlayerAnnouncement(this, announcement, rs);
                        if (!allowArchived && !plrAnn.active) continue;
                        result.computeIfAbsent(announcement, f -> new ArrayList<>()).add(plrAnn);
                    }
                });
        return result;
    }

    public List<Announcement.PlayerAnnouncement> getPlayerAnnouncementsByAnnId(int ann_id) {
        Announcement announcement = getAnnouncement(ann_id);
        if (announcement == null) return null;
        List<Announcement.PlayerAnnouncement> result = new ArrayList<>();
        query("select * FROM ANNOUNCEMENTS_PLAYER2 WHERE ann_id = ? ORDER BY ann_id desc",
                (ThrowingConsumer<PreparedStatement>) stmt -> stmt.setInt(1, ann_id),
                (ThrowingConsumer<ResultSet>) rs -> {
                    while (rs.next()) {
                        result.add(new Announcement.PlayerAnnouncement(this, announcement, rs));
                    }
                });
        return result;
    }

    public List<Announcement.PlayerAnnouncement> getPlayerAnnouncementsByNation(int nationId, boolean requireActive) {
        List<Announcement.PlayerAnnouncement> result = new ArrayList<>();
        query("select * FROM ANNOUNCEMENTS_PLAYER2 WHERE receiver = ?" + (requireActive ? " AND `active` = true" : "") + " ORDER BY ann_id desc",
        (ThrowingConsumer<PreparedStatement>) stmt -> stmt.setInt(1, nationId),
        (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                result.add(new Announcement.PlayerAnnouncement(this, rs));
            }
        });
        Set<Integer> annIds = result.stream().map(f -> f.ann_id).collect(Collectors.toSet());
        Map<Integer, Announcement> announcements = getAnnouncementsByIds(annIds);
        Iterator<Announcement.PlayerAnnouncement> iter = result.iterator();
        while (iter.hasNext()) {
            Announcement.PlayerAnnouncement plrAnn = iter.next();
            Announcement announcement = announcements.get(plrAnn.ann_id);
            if (announcement != null && (announcement.active || !requireActive)) {
                plrAnn.setParent(announcement);
            } else if (requireActive) {
                iter.remove();
            }
        }
        return result;
    }

    public List<DBLoan> getLoansByNation(int nationId) {
            List<DBLoan> loans = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM LOANS where receiver = ? ORDER BY loan_id desc")) {
            stmt.setInt(1, nationId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    loans.add(new DBLoan(rs));
                }
            }
            return loans;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public DBLoan getLoanById(int loanId) {
            List<DBLoan> loans = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM LOANS where loan_id = ?")) {
            stmt.setInt(1, loanId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    return new DBLoan(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Map<DepositType, double[]> getTaxBracketDeposits(int taxId, long cutOff, boolean includeExpired, boolean includeIgnored) {
        List<BankDB.TaxDeposit> records;
        if (cutOff == 0) {
            records = Locutus.imp().getBankDB().getTaxesByBracket(taxId);
        } else {
            records = Locutus.imp().getBankDB().getTaxesByBracket(taxId, cutOff);
        }
        Set<Integer> allowedAAIds = getAllianceIds(true);

        double[] deposits = ResourceType.getBuffer();
        double[] taxes = ResourceType.getBuffer();

        int[] aaBase = getOrNull(GuildDB.Key.TAX_BASE);
        if (aaBase == null) aaBase = new int[]{100, 100};
        int[] baseBuffer = new int[2];

        for (BankDB.TaxDeposit record : records) {
            if (!allowedAAIds.contains(record.allianceId)) {
                throw new IllegalArgumentException("Cannot view taxes for another alliance: " + record.allianceId + ". Guild is registered to: " + StringMan.getString(allowedAAIds));
            }
            baseBuffer[0] = record.internalMoneyRate >= 0 ? record.internalMoneyRate : aaBase[0];
            baseBuffer[1] = record.internalResourceRate >= 0 ? record.internalResourceRate : aaBase[1];
            double[] totalCopy = record.resources.clone();
            record.multiplyBase(baseBuffer);

            for (int i = 0; i < record.resources.length; i++) {
                deposits[i] += record.resources[i];
                taxes[i] += totalCopy[i] - record.resources[i];
            }
        }

        Map<DepositType, double[]> result = new LinkedHashMap<>();
        result.put(DepositType.TAX, taxes);
        result.put(DepositType.DEPOSIT, deposits);

        List<Map.Entry<Integer, Transaction2>> offset = getDepositOffsetTransactionsTaxId(taxId);
        if (!offset.isEmpty()) {
            Set<Long> allowedIdsLong = allowedAAIds.stream().map(f -> (long) f).collect(Collectors.toSet());
            Map<DepositType, double[]> sum = PnwUtil.sumNationTransactions(this, allowedIdsLong, offset, includeExpired, includeIgnored, f -> true);
            for (Map.Entry<DepositType, double[]> entry : sum.entrySet()) {
                ResourceType.add(result.computeIfAbsent(entry.getKey(), f -> ResourceType.getBuffer()), entry.getValue());
            }
        }
        return result;
    }
//
//    public List<Transaction2> getTaxBracketTransfers(int tax_id, TaxRate taxBase, boolean useOffset) {
//        List<Transaction2> transactions = new ArrayList<>();
//        List<BankDB.TaxDeposit> records = Locutus.imp().getBankDB().getTaxesByBracket(tax_id);
//
//        for (BankDB.TaxDeposit deposit : records) {
//            int internalMoneyRate = taxBase != null ? 100 - deposit.internalMoneyRate : 100;
//            int internalResourceRate = taxBase != null ? 100 - deposit.internalResourceRate : 100;
//            if (internalMoneyRate < 0 || internalMoneyRate > 100) internalMoneyRate = 100 - taxBase.money;
//            if (internalResourceRate < 0 || internalResourceRate > 100) internalResourceRate = 100 - taxBase.resources;
//
//            double pctMoney = (deposit.moneyRate > internalMoneyRate ?
//                    Math.max(0, (deposit.moneyRate - internalMoneyRate) / (double) deposit.moneyRate)
//                    : 0);
//            double pctRss = (deposit.resourceRate > internalResourceRate ?
//                    Math.max(0, (deposit.resourceRate - internalResourceRate) / (double) deposit.resourceRate)
//                    : 0);
//
//            deposit.resources[0] *= pctMoney;
//            for (int i = 1; i < deposit.resources.length; i++) {
//                deposit.resources[i] *= pctRss;
//            }
//            Transaction2 transaction = new Transaction2(deposit);
//            transactions.add(transaction);
//        }
//        if (useOffset) {
//            List<Transaction2> offset = getDepositOffsetTransactionsTaxId(tax_id);
//            transactions.addAll(offset);
//        }
//        return transactions;
//    }

//    public double[] getTaxBracketDeposits(int tax_id, TaxRate taxBase, boolean useOffset) {
//        List<Transaction2> transfers = getTaxBracketTransfers(tax_id, taxBase, useOffset);
//
//        double[] total = ResourceType.getBuffer();
//
//        for (BankDB.TaxDeposit deposit : transfers) {
//            int sign;
//
//
//            if (deposit.date < cutoff) continue;
//            int internalMoneyRate = taxBase != null ? 100 - deposit.internalMoneyRate : 100;
//            int internalResourceRate = taxBase != null ? 100 - deposit.internalResourceRate : 100;
//            if (internalMoneyRate < 0 || internalMoneyRate > 100) internalMoneyRate = 100 - taxBase.money;
//            if (internalResourceRate < 0 || internalResourceRate > 100) internalResourceRate = 100 - taxBase.resources;
//
//            double pctMoney = (deposit.moneyRate > internalMoneyRate ?
//                    Math.max(0, (deposit.moneyRate - internalMoneyRate) / (double) deposit.moneyRate)
//                    : 0);
//            double pctRss = (deposit.resourceRate > internalResourceRate ?
//                    Math.max(0, (deposit.resourceRate - internalResourceRate) / (double) deposit.resourceRate)
//                    : 0);
//
//            deposit.resources[0] *= pctMoney;
//            for (int i = 1; i < deposit.resources.length; i++) {
//                deposit.resources[i] *= pctRss;
//            }
//
//            total = ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, total, deposit.resources);
//        }
//
//        if (useOffset) {
//
//        }
//
//        return total;
//
//    }

    public DBLoan getLoanByMessageId(long loanId) {
            List<DBLoan> loans = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM LOANS where message = ?")) {
            stmt.setLong(1, loanId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    return new DBLoan(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<DBLoan> getExpiredLoans() {
            List<DBLoan> loans = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM LOANS where due < ? AND repaid = 0")) {
            stmt.setLong(1, System.currentTimeMillis());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    loans.add(new DBLoan(rs));
                }
            }
            return loans;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void addLoan(DBLoan loan) {
        update("INSERT OR REPLACE INTO `LOANS`(`server`, `message`, `receiver`, `resources`, `due`, `repaid`) VALUES(?, ?, ?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, loan.loanerGuildOrAA);
            stmt.setLong(2, loan.loanerNation);
            stmt.setLong(3, loan.nationId);
            stmt.setBytes(4, ArrayUtil.toByteArray(ArrayUtil.dollarToCents(loan.resources)));
            stmt.setLong(5, loan.dueDate);
            stmt.setInt(6, loan.status.ordinal());
        });
    }

    public void updateLoan(DBLoan loan) {
        if (loan.loanId == -1) throw new IllegalArgumentException("Loan has no id");
        update("INSERT OR REPLACE INTO `LOANS`(`loan_id`, `server`, `message`, `receiver`, `resources`, `due`, `repaid`) VALUES(?, ?, ?, ?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, loan.loanId);
            stmt.setLong(2, loan.loanerGuildOrAA);
            stmt.setLong(3, loan.loanerNation);
            stmt.setLong(4, loan.nationId);
            stmt.setBytes(5, ArrayUtil.toByteArray(ArrayUtil.dollarToCents(loan.resources)));
            stmt.setLong(6, loan.dueDate);
            stmt.setInt(7, loan.status.ordinal());
        });
    }

    public IAutoRoleTask getAutoRoleTask() {
        if (this.autoRoleTask == null) {
            synchronized (this) {
                if (this.autoRoleTask == null) {
                     this.autoRoleTask = new AutoRoleTask(getGuild(), this);
                }
            }
        }
        return autoRoleTask;
    }

    public Map<ResourceType, Double> getPerCityWarchest(DBNation nation) {
        return getPerCityWarchest();
}

    public Map<ResourceType, Double> getPerCityWarchest() {
        Map<ResourceType, Double> warchest = getOrNull(Key.WARCHEST_PER_CITY);
        if (warchest == null) warchest = new HashMap<>();
        warchest.putIfAbsent(ResourceType.MONEY, 1000000d);
        warchest.putIfAbsent(ResourceType.GASOLINE, 360d);
        warchest.putIfAbsent(ResourceType.MUNITIONS, 720d);
        warchest.putIfAbsent(ResourceType.ALUMINUM, 400d);
        warchest.putIfAbsent(ResourceType.STEEL, 500d);
        warchest.putIfAbsent(ResourceType.FOOD, 600d);
        warchest.putIfAbsent(ResourceType.URANIUM, 15d);
        return warchest;
    }

    public Set<Long> getResourceChannelAccounts(Long messageChannelIdOrNull) {
        if (messageChannelIdOrNull == null) return null;
        Map<Long, MessageChannel> channels = getOrNull(Key.RESOURCE_REQUEST_CHANNEL);
        if (channels != null) {
            if (messageChannelIdOrNull == 0) {
                MessageChannel channel = channels.get(0L);
                if (channel != null) messageChannelIdOrNull = channel.getIdLong();
            }
            Set<Integer> guildAAIds = null;
            Set<Long> allowedIds = new HashSet<>();
            for (Map.Entry<Long, MessageChannel> entry : channels.entrySet()) {
                if (entry.getValue().getIdLong() == messageChannelIdOrNull) {
                    if (guildAAIds == null) guildAAIds = getAllianceIds();
                    if (entry.getKey() == 0L) {
                        if (guildAAIds.isEmpty()) {
                            return Collections.singleton(getIdLong());
                        }
                        for (Integer aaId : guildAAIds) allowedIds.add(aaId.longValue());
                        return allowedIds;
                    } else if (guildAAIds.contains(entry.getKey().intValue())) {
                        allowedIds.add(entry.getKey().longValue());
                    }
                }
            }
            return allowedIds;
        }
        return null;
    }

    public long getMemberWithdrawAccount(User banker, Long messageChannelIdOrNull, Set<Long> channelWithdrawAccounts, boolean throwError) {
        if (!Roles.MEMBER.has(banker, guild)) {
            if (throwError) throw new IllegalArgumentException("You must have the member role to withdraw resources: " + Roles.MEMBER.toDiscordRoleNameElseInstructions(guild));
            return 0;
        }

        if (!Roles.ECON_WITHDRAW_SELF.has(banker, guild)) {
            if (throwError) throw new IllegalArgumentException("You must have the withdraw role to withdraw resources: " + Roles.ECON_WITHDRAW_SELF.toDiscordRoleNameElseInstructions(guild));
            return 0;
        }

        Set<Integer> aaIds = getAllianceIds();
        DBNation bankerNation = DiscordUtil.getNation(banker);
        if (bankerNation != null) {
            if (getOrNull(Key.MEMBER_CAN_WITHDRAW) == Boolean.TRUE) {
                if (!aaIds.isEmpty() && !getCoalition(Coalition.ENEMIES).isEmpty() && getOrNull(Key.MEMBER_CAN_WITHDRAW_WARTIME) != Boolean.TRUE) {
                    if (throwError) {
                        throw new IllegalArgumentException("You cannot withdraw during wartime. `" + Key.MEMBER_CAN_WITHDRAW_WARTIME + "` is false (see " + CM.settings.cmd.create(GuildDB.Key.MEMBER_CAN_WITHDRAW.name(), "true", null, null) + ") and `enemies` is set (see: " + CM.coalition.add.cmd.toSlashMention() + " | " + CM.coalition.remove.cmd.toSlashMention() + " | " + CM.coalition.list.cmd.toSlashMention() + ")");
                    }
                } else if (aaIds.isEmpty()) {
                    if (channelWithdrawAccounts.isEmpty() || !channelWithdrawAccounts.contains(getIdLong())) {
                        MessageChannel defaultChannel = getResourceChannel(0);
                        if (defaultChannel == null) {
                            String channelMention = messageChannelIdOrNull == null ? null : "<#" + messageChannelIdOrNull + ">";
                            throw new IllegalArgumentException("Please set a default resource channel with " + CM.settings.cmd.create(GuildDB.Key.RESOURCE_REQUEST_CHANNEL.name(), channelMention, null, null));
                        } else {
                            throw new IllegalArgumentException("Please use the resource channel: " + defaultChannel.getAsMention());
                        }
                    }
                    return getIdLong();
                } else {
                    int aaId = bankerNation.getAlliance_id();
                    if (!channelWithdrawAccounts.contains((long) aaId)) {
                        if (throwError) {
                            throw new IllegalArgumentException("You cannot withdraw as you are not in the alliance: " + StringMan.getString(channelWithdrawAccounts) + " (your alliance id is: " + aaId + ")");
                        }
                    } else if (bankerNation.getPositionEnum().id <= Rank.APPLICANT.id) {
                        if (throwError) {
                            throw new IllegalArgumentException("You cannot withdraw as you are not a member in game (only applicant)");
                        }
                    } else {
                        return aaId;
                    }
                }

            } else {
                if (throwError) {
                    throw new IllegalArgumentException("MEMBER_CAN_WITHDRAW is disabled. See " + CM.settings.cmd.toSlashMention());
                }
            }
        } else if (bankerNation == null) {
            throw new IllegalArgumentException("You are not registered. See: " + CM.register.cmd.toSlashMention());
        } else if (throwError) {
            String msg = "You need the econ role to withdraw to other nations";
            MessageChannel channel = getResourceChannel(aaIds.isEmpty() ? 0 : bankerNation.getAlliance_id());
            if (channel != null && (messageChannelIdOrNull == null || messageChannelIdOrNull != channel.getIdLong())) msg += ". The channel for alliance withdrawals is: " + channel.getAsMention();
            throw new IllegalArgumentException(msg);
        }
        return 0L;
    }

    public Map<Long, AccessType> getAllowedBankAccountsOrThrow(User banker, NationOrAlliance receiver, Long messageChannelIdOrNull) {
        Map<Long, AccessType> accessTypeMap = new LinkedHashMap<>();
        Set<Integer> aaIds = getAllianceIds();
        Set<Long> channelAccountIds = getResourceChannelAccounts(messageChannelIdOrNull);
        boolean isResourceChannel = channelAccountIds != null;
        if (channelAccountIds == null) {
            channelAccountIds = new HashSet<>();
            if (!aaIds.isEmpty()) {
                for (Integer aaId : aaIds) channelAccountIds.add(aaId.longValue());
            } else {
                channelAccountIds.add(getIdLong());
            }
        }

        if (!aaIds.isEmpty()) {
            channelAccountIds.removeIf(f -> !aaIds.contains(f.intValue()));
        } else {
            channelAccountIds.removeIf(f -> f != getIdLong());
        }
        if (channelAccountIds.isEmpty()) {
            throw new IllegalArgumentException("The resource channel: <#" + messageChannelIdOrNull + ">" +
                    " is configured for the following alliances: " + StringMan.getString(getResourceChannelAccounts(messageChannelIdOrNull)) +
                    " but the server is only registered to the following alliances: " + StringMan.getString(aaIds) +
                    "\nSee: " + CM.settings.cmd.toSlashMention() + " with keys: " + Key.ALLIANCE_ID + " and " + Key.RESOURCE_REQUEST_CHANNEL);
        }


        if (Roles.ECON.has(banker, guild)) {
            for (long accountId : channelAccountIds) {
                accessTypeMap.put(accountId, AccessType.ECON);
            }
        } else if (!aaIds.isEmpty()) {
            for (Long aaId : channelAccountIds) {
                if (Roles.ECON.has(banker, guild, aaId.intValue())) {
                    accessTypeMap.put(aaId, AccessType.ECON);
                }
            }
            long withdrawAccount = getMemberWithdrawAccount(banker, messageChannelIdOrNull, channelAccountIds, accessTypeMap.isEmpty());
            if (withdrawAccount > 0) {
                accessTypeMap.putIfAbsent(withdrawAccount, AccessType.SELF);
            }
        }
        if (accessTypeMap.isEmpty()) {
            if (isResourceChannel) {
                throw new IllegalArgumentException("You are not authorized to withdraw from this resource channel. (Do you have the econ role for the alliances?: " + StringMan.getString(channelAccountIds) + ")");
            } else {
                throw new IllegalArgumentException("You are not authorized to withdraw resources. Do you have the econ role for the alliance?");
            }
        }
        return accessTypeMap;
    }

    public void addBalanceMulti(Map<NationOrAllianceOrGuild, double[]> amounts, long dateTime, int banker, String offshoreNote) {
        for (Map.Entry<NationOrAllianceOrGuild, double[]> entry : amounts.entrySet()) {
            NationOrAllianceOrGuild account = entry.getKey();
            double[] amount = entry.getValue();
            if (account.isGuild()) {
                addTransfer(dateTime, 0, 0, account.getIdLong(), account.getReceiverType(), banker, offshoreNote, amount);
            } else {
                addTransfer(dateTime, 0, 0, (NationOrAlliance) account, banker, offshoreNote, amount);
            }
        }
    }

    public Map<NationOrAllianceOrGuild, double[]> addBalanceMulti(Map<NationOrAllianceOrGuild, double[]> depositsByAA, long dateTime, double[] amount, double sign, int banker, String offshoreNote) {
        if (sign != -1 && sign != 1) throw new IllegalArgumentException("Sign must be -1 or 1");
        for (int i = 0; i < amount.length; i++) {
            if (amount[i] < 0) throw new IllegalArgumentException("Amount must be positive: " + PnwUtil.resourcesToString(amount));
        }

        double[] amountLeft = amount.clone();

        Map<NationOrAllianceOrGuild, double[]> ammountEach = new LinkedHashMap<>();
        for (Map.Entry<NationOrAllianceOrGuild, double[]> entry : depositsByAA.entrySet()) {
            NationOrAllianceOrGuild account = entry.getKey();
            double[] subDeposits = entry.getValue();

            double[] toSubtract = null;
            for (int i = 0; i < amountLeft.length; i++) {
                double subDepositsI = subDeposits[i];
                double amountI = amountLeft[i];
                if (subDepositsI > 0 && amountI > 0) {
                    double subtract = Math.min(subDepositsI, amountI);
                    if (subtract < 0.01) continue;
                    if (toSubtract == null) toSubtract = ResourceType.getBuffer();
                    toSubtract[i] = subtract;
                    amountLeft[i] -= subtract;
                }
            }
            if (toSubtract != null) {
                ammountEach.put(account, toSubtract);

                double[] amountSigned = toSubtract.clone();
                if (sign != 1) {
                    for (int i = 0; i < amountSigned.length; i++) {
                        amountSigned[i] *= sign;
                    }
                }

                addTransfer(dateTime, 0, 0, account.getIdLong(), account.getReceiverType(), banker, offshoreNote, amountSigned);
            }
        }
        return ammountEach;
    }

    public void addBalanceTaxId(long tx_datetime, int taxId, int banker, String note, double[] amount) {
        addTransfer(tx_datetime, taxId, 4, 0, 0, banker, note, amount);
    }

    public void addBalanceTaxId(long tx_datetime, int taxId, int nation, int banker, String note, double[] amount) {
        addTransfer(tx_datetime, taxId, 4, nation, 1, banker, note, amount);
    }

    public void subBalance(long tx_datetime, NationOrAlliance account, int banker, String note, double[] amount) {
        double[] copy = ResourceType.getBuffer();
        for (int i = 0; i < copy.length; i++) copy[i] = -amount[i];
        addBalance(tx_datetime, account, banker, note, copy);
    }

    public void addBalance(long tx_datetime, NationOrAlliance account, int banker, String note, double[] amount) {
        addTransfer(tx_datetime, account, 0, 0, banker, note, amount);
    }

    public void subtractBalance(long tx_datetime, NationOrAlliance account, int banker, String note, double[] amount) {
        addTransfer(tx_datetime, 0, 0, account, banker, note, amount);
    }

    public void addBalance(long tx_datetime, long accountId, int type, int banker, String note, double[] amount) {
        addTransfer(tx_datetime, accountId, type, 0, 0, banker, note, amount);
    }

    public void subtractBalance(long tx_datetime, long accountId, int type, int banker, String note, double[] amount) {
        addTransfer(tx_datetime, 0, 0, accountId, type, banker, note, amount);
    }

    public void addTransfer(long tx_datetime, NationOrAlliance sender, long receiver_id, int receiver_type, int banker, String note, double[] amount) {
        Map.Entry<Long, Integer> idType = sender.getTransferIdAndType();
        addTransfer(tx_datetime, idType.getKey(), idType.getValue(), receiver_id, receiver_type, banker, note, amount);
    }

    public void addTransfer(long tx_datetime, NationOrAlliance sender, NationOrAlliance receiver2, int banker, String note, double[] amount) {
        Map.Entry<Long, Integer> idType = sender.getTransferIdAndType();
        addTransfer(tx_datetime, idType.getKey(), idType.getValue(), receiver2, banker, note, amount);
    }

    public void addTransfer(long tx_datetime, long sender_id, int sender_type, NationOrAlliance receiver, int banker, String note, double[] amount) {
        Map.Entry<Long, Integer> idType = receiver.getTransferIdAndType();
        addTransfer(tx_datetime, sender_id, sender_type, idType.getKey(), idType.getValue(), banker, note, amount);
    }

    public void addTransfer(long tx_datetime, long sender_id, int sender_type, long receiver_id, int receiver_type, int banker, String note, double[] amount) {
        Transaction2 tx = new Transaction2(0, tx_datetime, sender_id, sender_type, receiver_id, receiver_type, banker, note, amount);
        addTransaction(tx);
    }

    private void checkDeposits(double[] deposits, double[] amount, String senderType, String senderName) {
        double[] normalized = PnwUtil.normalize(deposits);

        if (PnwUtil.convertedTotal(deposits) <= 0) throw new IllegalArgumentException("Sender " + senderType + " (" + senderName + ") does not have any deposits");

        for (int i = 0; i < deposits.length; i++) {
            if (Math.round(amount[i] * 100) < Math.round(normalized[i] * 100)) {
                String msg = "Sender " + senderType + " (" + senderName + ") can only send " + MathMan.format(normalized[i]) + "x" + ResourceType.values[i] + "(not " + MathMan.format(amount[i]) + ")";
                if (Math.round(amount[i] * 100) < Math.round(deposits[i] * 100)) {
                    throw new IllegalArgumentException(msg);
                } else {
                    throw new IllegalArgumentException(msg + "\nNote: Transfer limit is reduced by negative resources in deposits");
                }
            }
        }
    }

    public boolean sendInternal(@Me User banker, @Me DBNation bankerNation, GuildDB senderDB, DBAlliance senderAlliance, DBNation senderNation, GuildDB receiverDB, DBAlliance receiverAlliance, DBNation receiverNation, double[] amount) throws IOException {
        synchronized (OffshoreInstance.BANK_LOCK) {
            for (int i = 0; i < amount.length; i++) {
                if (!Double.isFinite(amount[i]) || amount[i] < 0) {
                    throw new IllegalArgumentException("You cannot send negative amounts for " + ResourceType.values[i]);
                }
            }
            if (senderDB == null && senderNation == null && senderAlliance == null)
                throw new IllegalArgumentException("Sender cannot be null");
            if (receiverDB == null && receiverNation == null && receiverAlliance == null)
                throw new IllegalArgumentException("Receiver cannot be null");

            if (senderDB == null) {
                throw new IllegalArgumentException("Sender DB cannot be null");
            }
            if (senderAlliance == null) {
                Set<Integer> aaIds = senderDB.getAllianceIds();
                if (!aaIds.isEmpty()) {
                    if (aaIds.size() == 1) {
                        senderAlliance = DBAlliance.getOrCreate(aaIds.iterator().next());
                    } else if (aaIds.contains(senderNation.getAlliance_id())) {
                        senderAlliance = DBAlliance.getOrCreate(senderNation.getAlliance_id());
                    } else {
                        throw new IllegalArgumentException("Sender DB " + senderDB + " has multiple alliances: " + StringMan.getString(aaIds) + " and must be specified");
                    }
                }
            }

            if (receiverAlliance == null && receiverDB == null) {
                receiverAlliance = receiverNation.getAlliance(false);
            }
            if (receiverDB == null) {
                if (receiverAlliance == null)
                    throw new IllegalArgumentException("No Alliance not found for nation: " + receiverNation.getNation());
                receiverDB = receiverAlliance.getGuildDB();
                if (receiverDB == null)
                    throw new IllegalArgumentException("No GuildDB found for: " + receiverAlliance + " (Are you sure Locutus is setup for this AA?)");
            }

            if (receiverAlliance == null) {
                Set<Integer> aaIds = receiverDB.getAllianceIds();
                if (!aaIds.isEmpty()) {
                    if (aaIds.size() == 1) {
                        receiverAlliance = DBAlliance.getOrCreate(aaIds.iterator().next());
                    } else if (aaIds.contains(receiverNation.getAlliance_id())) {
                        receiverAlliance = DBAlliance.getOrCreate(receiverNation.getAlliance_id());
                    } else {
                        throw new IllegalArgumentException("Receiver DB " + receiverDB + " has multiple alliances: " + StringMan.getString(aaIds) + " and must be specified");
                    }
                }
            }

            if (senderAlliance != null) {
                if (!receiverDB.isAllianceId(senderAlliance.getId())) {
                    throw new IllegalArgumentException("Sender alliance: " + senderAlliance.getAlliance_id() + " is not registered to receiver DB: " + receiverDB);
                }
                if (senderNation != null && senderAlliance.getAlliance_id() != senderNation.getAlliance_id()) {
                    throw new IllegalArgumentException("Sender alliance: " + senderAlliance.getAlliance_id() + " does not match nation: " + senderNation.getNation());
                }
                Set<Integer> aaIds = senderDB.getAllianceIds();
                if (!aaIds.isEmpty() && !aaIds.contains(senderAlliance.getAlliance_id())) {
                    throw new IllegalArgumentException("Sender alliance: " + senderAlliance.getAlliance_id() + " does not match guild AA: " + StringMan.getString(aaIds));
                }
            }

            if (receiverAlliance != null) {
                if (!receiverDB.isAllianceId(receiverAlliance.getId())) {
                    throw new IllegalArgumentException("Receiver alliance: " + receiverAlliance.getAlliance_id() + " is not registered to receiver DB: " + receiverDB);
                }
                if (receiverNation != null && receiverAlliance.getAlliance_id() != receiverNation.getAlliance_id()) {
                    throw new IllegalArgumentException("Receiver alliance: " + receiverAlliance.getAlliance_id() + " does not match nation: " + receiverNation.getNation());
                }
                Set<Integer> aaIds = receiverDB.getAllianceIds();
                if (!aaIds.isEmpty() && !aaIds.contains(receiverAlliance.getAlliance_id())) {
                    throw new IllegalArgumentException("Receiver alliance: " + receiverAlliance.getAlliance_id() + " does not match guild AA: " + StringMan.getString(aaIds));
                }
            }
            if (senderNation != null && senderNation.getPositionEnum().id < Rank.MEMBER.id) {
                throw new IllegalArgumentException("Sender Nation " + senderNation.getNation() + " is not member in-game");
            }
            if (receiverNation != null && receiverNation.getPositionEnum().id < Rank.MEMBER.id) {
                throw new IllegalArgumentException("Receiver Nation " + receiverNation.getNation() + " is not member in-game");
            }

            int roleAA = senderAlliance != null ? senderAlliance.getId() : 0;
            boolean hasEcon = Roles.ECON.has(banker, senderDB.getGuild(), roleAA);
            boolean canWithdrawSelf = hasEcon || Roles.ECON_WITHDRAW_SELF.has(banker, senderDB.getGuild(), roleAA);
            if (!canWithdrawSelf) {
                Map<Long, Role> roles = Roles.ECON_WITHDRAW_SELF.toRoleMap(senderDB);
                Role role = roles.getOrDefault((long) roleAA, roles.get(0L));
                if (role != null) {
                    throw new IllegalArgumentException("Missing " + role.getName() + " to withdraw from the sender guild: " + senderDB.getGuild());
                } else {
                    throw new IllegalArgumentException("No permission to withdraw from: " + senderDB.getGuild() + " see: " + CM.role.setAlias.cmd.toSlashMention() + " with roles: " + Roles.ECON_WITHDRAW_SELF + "," + Roles.ECON);
                }
            }
            if (!hasEcon && !Roles.MEMBER.has(banker, senderDB.getGuild())) {
                throw new IllegalArgumentException("Banker " + banker.getName() + " does not have the member role in " + senderDB + ". See: " + CM.role.setAlias.cmd.toSlashMention());
            }
            if (senderNation == null && !hasEcon) {
                Map<Long, Role> roles = Roles.ECON.toRoleMap(senderDB);
                Role role = roles.getOrDefault((long) roleAA, roles.get(0L));
                if (role == null) {
                    throw new IllegalArgumentException("You cannot send from the alliance account (Did you instead mean to send from your deposits?). See: " + CM.role.setAlias.cmd.toSlashMention() + " with role " + Roles.ECON);
                } else {
                    throw new IllegalArgumentException("You cannot send from the alliance account (Did you instead mean to send from your deposits?). Missing role " + role.getName());
                }
            }
            if (!hasEcon && senderNation.getNation_id() != bankerNation.getNation_id()) {
                throw new IllegalArgumentException("Lacking role: " + Roles.ECON + " (see " + CM.role.setAlias.cmd.toSlashMention() + "). You do not have permission to send from other nations");
            }
            if (!hasEcon && senderDB.getOrNull(GuildDB.Key.MEMBER_CAN_WITHDRAW) != Boolean.TRUE) {
                throw new IllegalArgumentException("Lacking role: " + Roles.ECON + " (see " + CM.role.setAlias.cmd.toSlashMention() + "). Member withdrawals are not enabled, see: " + CM.settings.cmd.create(GuildDB.Key.MEMBER_CAN_WITHDRAW.name(), null, null, null));
            }

            // if sender nation does not have member role
            // if receiver nation does not have member role

            // ensure receiver is not gray / inactive / vm

            // if sender nation is not null, ensure alliance matches and nation position > 1

            if (senderNation != null) {
                if (senderNation.getVm_turns() > 0)
                    throw new IllegalArgumentException("Sender nation (" + senderNation.getNation() + ") is in VM");
                if (senderNation.getActive_m() > 10000)
                    throw new IllegalArgumentException("Sender nation (" + senderNation.getNation() + ") is inactive in-game");
            }
            if (receiverNation != null) {
                if (receiverNation.getVm_turns() > 0)
                    throw new IllegalArgumentException("Receiver nation (" + receiverNation.getNation() + ") is in VM");
                if (receiverNation.getActive_m() > 10000)
                    throw new IllegalArgumentException("Receiver nation (" + receiverNation.getNation() + ") is inactive in-game");
            }

            if (senderNation != null) {
                double[] deposits = senderNation.getNetDeposits(senderDB);
                checkDeposits(deposits, amount, "nation", senderNation.getName());
            }

            OffshoreInstance senderOffshore = senderDB.getOffshore();
            OffshoreInstance receiverOffshore = receiverDB.getOffshore();

            if (senderOffshore == null) {
                throw new IllegalArgumentException("Sender Guild: " + senderDB.getGuild() + " has no offshore. See: " + CM.offshore.add.cmd.toSlashMention());
            }
            if (receiverOffshore == null) {
                throw new IllegalArgumentException("Receiver Guild: " + receiverDB.getGuild() + " has no offshore. See: " + CM.offshore.add.cmd.toSlashMention());
            }

            if (receiverOffshore != senderOffshore) {
                // TODO support this
                throw new IllegalArgumentException("Internal transfers to other offshores is not currently supported. Please use " + CM.transfer.resources.cmd.toSlashMention() + " or conduct an ingame trade.");
            }

            if (receiverOffshore.isDisabled(senderDB.getIdLong())) {
                throw new IllegalArgumentException("An error occured. Please contact an administrator (code: " + senderDB.getIdLong() + ")");
            }
            if (receiverOffshore.isDisabled(receiverDB.getIdLong())) {
                throw new IllegalArgumentException("An error occured. Please contact an administrator (code: " + receiverDB.getIdLong() + ")");
            }

            MessageChannel receiverChannel = receiverDB.getResourceChannel(receiverAlliance != null ? receiverAlliance.getId() : 0);

//        if (senderChannel == null) throw new IllegalArgumentException("Please have an admin use. " + CM.settings.cmd.create(GuildDB.Key.RESOURCE_REQUEST_CHANNEL.name(), "#someChannel") + " in " + senderDB);
            if (receiverChannel == null)
                throw new IllegalArgumentException("Please have an admin set: " + CM.settings.cmd.create(GuildDB.Key.RESOURCE_REQUEST_CHANNEL.name(), null, null, null) + " in receiving " + receiverDB.getGuild());


            String accountName;
            double[] guildDepo;
            if (senderAlliance != null) {
                guildDepo = PnwUtil.resourcesToArray(senderOffshore.getDeposits(senderAlliance.getAlliance_id()));
                accountName = "AA:" + senderAlliance.getId();
                // ensure alliance deposits
            } else {
                guildDepo = PnwUtil.resourcesToArray(senderOffshore.getDeposits(senderDB.getIdLong(), true));
                accountName = senderDB.getIdLong() + "";
                // ensure guild deposits
            }
            try {
                checkDeposits(guildDepo, amount, senderAlliance != null ? "Alliance" : "Guild", accountName);
            } catch (IllegalArgumentException e) {
                CM.deposits.check cmd = CM.deposits.check.cmd.create(accountName, null, null, null, null, null, null, null, null);
                throw new IllegalArgumentException(e.getMessage() + "\n" + "See: " + cmd);
            }

            String note = "#deposit";

            long tx_datetime = System.currentTimeMillis();
            if (senderNation != null) {
                senderDB.subtractBalance(tx_datetime, senderNation, bankerNation.getNation_id(), note, amount);
            }
            long senderAccountId = senderAlliance != null ? senderAlliance.getIdLong() : senderDB.getIdLong();
            long receiverAccountId = receiverAlliance != null ? receiverAlliance.getIdLong() : receiverDB.getIdLong();

            if (senderAccountId != receiverAccountId) {
                if (senderAlliance != null) {
                    senderOffshore.getGuildDB().subtractBalance(tx_datetime, senderAlliance, bankerNation.getNation_id(), note, amount);
                } else {
                    senderOffshore.getGuildDB().subtractBalance(tx_datetime, senderDB.getIdLong(), senderDB.getReceiverType(), bankerNation.getNation_id(), note, amount);
                }
                if (receiverAlliance != null) {
                    senderOffshore.getGuildDB().addBalance(tx_datetime, receiverAlliance, bankerNation.getNation_id(), note, amount);
                } else {
                    senderOffshore.getGuildDB().addBalance(tx_datetime, receiverDB.getIdLong(), receiverDB.getReceiverType(), bankerNation.getNation_id(), note, amount);
                }
            }
            if (receiverNation != null) {
                receiverDB.addBalance(tx_datetime, receiverNation, bankerNation.getNation_id(), note, amount);
            }

            if (receiverChannel.canTalk()) {
                StringBuilder message = new StringBuilder("Internal Transfer ");
                if (senderDB != receiverDB) message.append(senderDB.getGuild() + " ");
                if (senderAlliance != null && !Objects.equals(senderAlliance, receiverAlliance))
                    message.append(" AA:" + senderAlliance.getName());
                if (senderNation != null) message.append(" " + senderNation.getName());
                message.append(" -> ");
                if (senderDB != receiverDB) message.append(receiverDB.getGuild() + " ");
                if (receiverAlliance != null && !Objects.equals(senderAlliance, receiverAlliance))
                    message.append(" AA:" + receiverAlliance.getName());
                if (receiverNation != null) message.append(" " + receiverNation.getName());
                message.append(": " + PnwUtil.resourcesToString(amount) + ", note: `" + note + "`");
                RateLimitUtil.queueMessage(receiverChannel, message.toString(), true);
            }
            return true;
        }
    }

//    public void setDepositOffset(long nationId, ResourceType resource, double amount, String note) {
//        long amtLong = (long) Math.round(amount * 100);
//        long id = MathMan.pairInt((int) nationId, resource.ordinal());
//        update("INSERT OR REPLACE INTO `BANK_DEPOSIT`(`nationId`, `resource`, `amount`, `note`) VALUES(?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
//            stmt.setLong(1, nationId);
//            stmt.setInt(2, resource.ordinal());
//            stmt.setLong(3, amtLong);
//            stmt.setString(4, note);
//        });
//    }

//    private Map<ResourceType, Map<String, Double>> getDepositOffset(int nationId, String requiredNote) {
//        Map<ResourceType, Map<String, Double>> result = new EnumMap<ResourceType, Map<String, Double>>(ResourceType.class);
//        try (PreparedStatement stmt = prepareQuery("select * FROM BANK_DEPOSIT WHERE `nationId` = ? and note like ?")) {
//            stmt.setInt(1, nationId);
//            stmt.setString(2, requiredNote);
//            try (ResultSet rs = stmt.executeQuery()) {
//                while (rs.next()) {
//                    ResourceType type = ResourceType.values[rs.getInt("resource")];
//                    double amount = rs.getLong("amount") / 100d;
//                    String note = rs.getString("note");
//                    Map<String, Double> map = result.computeIfAbsent(type, f -> new HashMap<>());
//                    map.put(note, map.getOrDefault(note, 0d) + amount);
//                }
//            }
//            return result;
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//        return null;
//    }

    private Map<String, double[]> remapDepositOffset(Map<ResourceType, Map<String, Double>> offset) {
        Map<String, double[]> result = new HashMap<>();
        for (Map.Entry<ResourceType, Map<String, Double>> entry : offset.entrySet()) {
            ResourceType type = entry.getKey();
            Map<String, Double> noteAmtMap = entry.getValue();
            for (Map.Entry<String, Double> stringDoubleEntry : noteAmtMap.entrySet()) {
                String note = stringDoubleEntry.getKey();
                double amt = stringDoubleEntry.getValue();

                double[] rss = result.computeIfAbsent(note, f -> new double[ResourceType.values.length]);
                rss[type.ordinal()] += amt;
            }
        }
        return result;
    }

    private List<Transaction2> getDepositOffsetTransactionsLegacy(long sender_id, int sender_type, Map<ResourceType, Map<String, Double>> offset) {
        List<Transaction2> result = new ArrayList<>();
        Map<String, double[]> remapped = remapDepositOffset(offset);
        for (Map.Entry<String, double[]> entry : remapped.entrySet()) {
            Transaction2 transaction = new Transaction2(-1, Long.MAX_VALUE, sender_id, sender_type, 0, 0, 0, entry.getKey(), entry.getValue());
            result.add(transaction);
        }
        return result;
    }

    public List<Map.Entry<Integer, Transaction2>> getDepositOffsetTransactionsTaxId(int tax_id) {
        List<Transaction2> records = getDepositOffsetTransactions(tax_id, 4);
        List<Map.Entry<Integer, Transaction2>> result = new ArrayList<>(records.size());
        for (Transaction2 record : records) {
            if (record.sender_id != tax_id && record.receiver_id != tax_id) continue;
            int sign = record.sender_id == tax_id ? 1 : -1;
            result.add(new AbstractMap.SimpleEntry<>(sign, record));
        }
        return result;
    }

    public List<Transaction2> getDepositOffsetTransactions(long id) {
        long sender_id = Math.abs(id);

        // nation_id
        // alliance_id
        // guild_id
        int sender_type;
        if (sender_id > Integer.MAX_VALUE) {
            sender_type = id > 0 ? 3 : 4;
        } else {
            sender_type = id >= 0 ? 1 : 2;
        }
        return getDepositOffsetTransactions(sender_id, sender_type);
    }

    public List<Transaction2> getDepositOffsetTransactions(long sender_id, int sender_type) {
        Map<ResourceType, Map<String, Double>> legacyOffset = sender_type <= 3 ? getDepositOffset(sender_type == 2 ? -sender_id : sender_id) : new HashMap<>();
        List<Transaction2> legacyTransfers = getDepositOffsetTransactionsLegacy(sender_id, sender_type, legacyOffset);

        List<Transaction2> transfers = getTransactionsById(sender_id, sender_type);
        transfers.addAll(legacyTransfers);

        for (Transaction2 transfer : transfers) {
            transfer.tx_id = -1;
        }

        return transfers;
    }

    private Map<ResourceType, Map<String, Double>> getDepositOffset(long nationId) {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            return delegate.getDepositOffset(nationId);
        }
        try {
            if (!tableExists("BANK_DEPOSIT")) return new HashMap<>();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        Map<ResourceType, Map<String, Double>> result = new EnumMap<ResourceType, Map<String, Double>>(ResourceType.class);
        try (PreparedStatement stmt = prepareQuery("select * FROM BANK_DEPOSIT WHERE `nationId` = ?")) {
            stmt.setLong(1, nationId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ResourceType type = ResourceType.values[rs.getInt("resource")];
                    double amount = rs.getLong("amount") / 100d;
                    String note = rs.getString("note");
                    Map<String, Double> map = result.computeIfAbsent(type, f -> new HashMap<>());
                    map.put(note, map.getOrDefault(note, 0d) + amount);
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private WarCategory warChannel;
    private boolean warChannelInit = false;

    public boolean isAllyOfRoot() {
        return isAllyOfRoot(true);
    }

    public boolean isAllyOfRoot(boolean checkWhitelist) {
        return isAllyOfRoot(type -> {
            if (type == null) return false;
            switch (type) {
                case MDP:
                case MDOAP:
                case ODP:
                case ODOAP:
                case PROTECTORATE:
                    return true;
            }
            return false;
        }, checkWhitelist);
    }

    public boolean isAllyOfRoot(Function<TreatyType, Boolean> checkType) {
        return isAllyOfRoot(checkType, true);
    }

    public boolean isAllyOfRoot(Function<TreatyType, Boolean> checkType, boolean allowWhitelist) {
        if (allowWhitelist && isWhitelisted()) return true;
        return false;
    }

    public void disableWarChannel() {
        this.warChannelInit = true;
        this.warChannel = null;
    }

    public WarCategory getWarChannel() {
        return getWarChannel(false);
    }

    private Throwable warCatError = null;

    public WarCategory getWarChannel(boolean throwException) {
        return getWarChannel(throwException, false);
    }
    public WarCategory getWarChannel(boolean throwException, boolean isWarServer) {
        Boolean enabled = getOrNull(Key.ENABLE_WAR_ROOMS, false);
        if (enabled == Boolean.FALSE || enabled == null) {
            if (throwException) throw new IllegalArgumentException("War rooms are not enabled " + CM.settings.cmd.create(GuildDB.Key.ENABLE_WAR_ROOMS.name(), "true", null, null) + "");
            return null;
        }
        if (!isWhitelisted() && !isValidAlliance()) {
            if (throwException) {
                throw new IllegalArgumentException("Ensure there are members in this alliance, " + CM.who.cmd.toSlashMention() + " and that " + CM.settings.cmd.create(GuildDB.Key.ALLIANCE_ID.name(), "<id>", null, null) + " is set");
            }
            return null;
        }
        try {
            Guild warServer = getOrNull(Key.WAR_SERVER, false);
            if (warServer != null && warServer.getIdLong() != guild.getIdLong()) {
                GuildDB db = Locutus.imp().getGuildDB(warServer);
                // circular reference
                if (db == null) {
                    if (throwException) throw new IllegalArgumentException("There is a null war server set (or delegated to) " + CM.settings.cmd.create(GuildDB.Key.WAR_SERVER.name(), "null", null, null) + "");
                    return null;
                }
                if (db.getOrNull(Key.WAR_SERVER, false) != null) {
                    if (throwException) throw new IllegalArgumentException("There is a null war server set " + CM.settings.cmd.create(GuildDB.Key.WAR_SERVER.name(), "null", null, null) + "");
                    return null;
                }
                return db.getWarChannel(throwException, true);
            }

            if (hasAlliance() || isWarServer) {
                if (warChannel == null && !warChannelInit) {
                    warChannelInit = true;
                    boolean allowed = Boolean.TRUE.equals(enabled) || isWhitelisted() || isAllyOfRoot() || getPermission(WarCategory.class) > 0;
                    if (allowed) {
                        guild.getMembers();
                        warChannel = new WarCategory(guild, "warcat");
//                        warChannel.sync();
                    } else if (warChannel == null) {
                        if (throwException)  {
                            if (warChannelInit) {
                                String message = "Locutus previous failed to create war channels: ";
                                if (warCatError != null) {
                                    message += warCatError.getMessage() + "\n```" + StringMan.stacktraceToString(warCatError) + "```";
                                }
                                message += "\nTry setting " + CM.settings.cmd.create(GuildDB.Key.ENABLE_WAR_ROOMS.name(), "true", null, null).toSlashMention() + " and attempting this command again once the issue has been resolved.";
                                throw new IllegalArgumentException(message);
                            }
                            throw new IllegalArgumentException("This guild does not have permission to use war channels");
                        }
                    }
                }
//            } else if (isWhitelisted()) {
//                warChannel = new DebugWarChannel(guild, "warcat", "");
            } else if (warChannel == null) {
                if (throwException) throw new IllegalArgumentException("Please set " + CM.settings.cmd.create(GuildDB.Key.ALLIANCE_ID.name(), "<id>", null, null) + " in " + guild);
            }
            return warChannel;
        } catch (Throwable e) {
            warCatError = e;
            if (throwException) throw new IllegalArgumentException("There was an error creating war channels: " + e.getMessage() + "\n```" + StringMan.stacktraceToString(e) + "```\n" +
                    "\nTry setting " + CM.settings.cmd.create(GuildDB.Key.ENABLE_WAR_ROOMS.name(), "true", null, null).toSlashMention() + " and attempting this command again once the issue has been resolved.");
            return null;
        }
    }

    public boolean isWhitelisted() {
        if (getIdLong() == Settings.INSTANCE.ROOT_SERVER) return true;
        if (hasCoalitionPermsOnRoot(Coalition.WHITELISTED)) return true;
        if (hasCoalitionPermsOnRoot(Coalition.WHITELISTED_AUTO)) return true;

        // other stuff?
        return false;
    }
    
    public boolean violatesDNR(DBNation defender) {
        Function<DBNation, Boolean> canRaid = getCanRaid();
        return !canRaid.apply(defender);
    }

    public JsonObject sendRecruitMessage(DBNation to) throws IOException {
        return getHandler().sendRecruitMessage(to);
    }

    public GuildDB getDelegateServer() {
        return getDelegateServer(false);
    }
    public GuildDB getDelegateServer(boolean getParent) {
        GuildDB delegate = getOrNull(Key.DELEGATE_SERVER, false);
        if (delegate != null && delegate.getIdLong() != getIdLong()) {
            if (getParent) {
                GuildDB delegateDelegate = delegate.getOrNull(Key.DELEGATE_SERVER, false);
            }
            return delegate;
        }
        return null;
    }

    public boolean isDelegateServer() {
        return getDelegateServer() != null;
    }

    public boolean isValidAlliance() {
        Set<Integer> aaIds = getOrNull(Key.ALLIANCE_ID);
        if (aaIds.isEmpty()) return false;
        for (int aaId : aaIds) {
            if (DBAlliance.get(aaId) != null) return true;
        }
        return false;
    }

    public boolean hasRequiredMMR(DBNation nation) {
        if (getOrNull(Key.REQUIRED_MMR) == null) return true;
        return getRequiredMMR(nation).values().stream().anyMatch(f -> f);
    }

    public Map<String, Boolean> getRequiredMMR(DBNation nation) {
        Map<NationFilterString, MMRMatcher> requiredMmrMap = getOrNull(GuildDB.Key.REQUIRED_MMR);
        if (requiredMmrMap == null) return null;
        Map<String, Boolean> allowedMMr = new LinkedHashMap<>();
        String myMMR = null;
        for (Map.Entry<NationFilterString, MMRMatcher> entry : requiredMmrMap.entrySet()) {
            NationFilterString nationMatcher = entry.getKey();
            if (nationMatcher.test(nation)) {
                if (myMMR == null) {
                    myMMR = nation.getMMRBuildingStr();
                }
                MMRMatcher required = entry.getValue();
                allowedMMr.put(required.getRequired(), required.test(myMMR));
            }
        }
        return allowedMMr;
    }

    public boolean isOffshore() {
        if (isDelegateServer()) return false;

        Set<Long> offshoring = getCoalitionRaw(Coalition.OFFSHORING);
        if (offshoring.isEmpty()) return false;
        Set<Long> offshore = getCoalitionRaw(Coalition.OFFSHORE);
        if (offshore.isEmpty()) return false;

        if (getOrNull(Key.API_KEY) == null || !isValidAlliance()) {
            return false;
        }

        Set<Long> myIds = new HashSet<>();
        myIds.add(getGuild().getIdLong());
        for (Integer id : getAllianceIds()) myIds.add(id.longValue());

        for (Long id : offshoring) {
            GuildDB other;
            if (id > Integer.MAX_VALUE) {
                other = Locutus.imp().getGuildDB(id);
            } else {
                other = Locutus.imp().getGuildDBByAA(id.intValue());
            }
            if (other != null) {
                Set<Long> otherOffshores = other.getCoalitionRaw(Coalition.OFFSHORE);
                for (Long offshoreId : otherOffshores) {
                    if (myIds.contains(offshoreId)) return true;
                }
            }
        }
        return false;
    }

    public boolean isOwnerActive() {
        if (guild == null) return false;
        Member owner = guild.getOwner();
        if (owner == null) return false;
        User user = owner.getUser();
        if (user == null) return false;
        DBNation nation = DiscordUtil.getNation(user);
        return nation != null && nation.getActive_m() < 10000;
    }

    /**
     * Has spy sheets enabled
     * @return true/false
     */
    public boolean enabledSpySheet() {
        Object spysheet = getOrNull(Key.SPYOP_SHEET);
        return (getOrNull(Key.API_KEY) != null && hasAlliance()) || spysheet != null;
    }

    public Set<String> findCoalitions(int aaId) {
        Set<String> coalitions = new LinkedHashSet<>();
        for (Map.Entry<String, Set<Integer>> entry : getCoalitions().entrySet()) {
            if (entry.getValue().contains(aaId)) {
                coalitions.add(entry.getKey());
            }
        }
        return coalitions;
    }

    public AddBalanceBuilder addBalanceBuilder() {
        return new AddBalanceBuilder(this);
    }

//    public synchronized void addBalance(GuildDB guildDb, Map<ResourceType, Double> transfer) {
//        addBalance(guildDb.getOrNull(Key.ALLIANCE_ID), guildDb, transfer);
//    }
//
//    public synchronized void addBalance(Integer aaId, GuildDB guildDb, Map<ResourceType, Double> transfer) {
//        String note = "#deposit";
//
//        long id = aaId == null ? guildDb.getGuild().getIdLong() : -aaId;
//        Map<ResourceType, Map<String, Double>> offset = getDepositOffset(id);
//
//        boolean result = false;
//        for (Map.Entry<ResourceType, Double> entry : transfer.entrySet()) {
//            ResourceType rss = entry.getKey();
//            Double amt = entry.getValue();
//            if (amt == 0) continue;
//
//            double currentAmt = offset.getOrDefault(rss, new HashMap<>()).getOrDefault(note, 0d);
//            double newAmount = amt + currentAmt;
//
//            setDepositOffset(id, rss, newAmount, note);
//
//            result = true;
//        }
//    }

    public enum Key {
        ALLIANCE_ID() {
            @Override
            public String validate(GuildDB db, String value) {
                if (db.getInfo(Key.DELEGATE_SERVER, false) != null) throw new IllegalArgumentException("Cannot set alliance id of delegate server (please unset DELEGATE_SERVER first)");

                Set<Integer> aaIds = PnwUtil.parseAlliances(db, value);
                if (aaIds.isEmpty()) {
                    throw new IllegalArgumentException("Invalid alliance: " + value);
                }
                if (aaIds.size() > 1) {
                    throw new IllegalArgumentException("Multiple alliances not supported (yet): " + value);
                }

                for (int aaId : aaIds) {
                    if (aaId == 0) {
                        throw new IllegalArgumentException("Invalid alliance: " + value);
                    }
                    DBAlliance alliance = DBAlliance.getOrCreate(aaId);
                    GuildDB otherDb = alliance.getGuildDB();
                    Member owner = db.getGuild().getOwner();
                    DBNation ownerNation = owner != null ? DiscordUtil.getNation(owner.getUser()) : null;
                    if (ownerNation == null || ownerNation.getAlliance_id() != aaId || ownerNation.getPosition() < Rank.LEADER.id) {
                        Set<String> inviteCodes = new HashSet<>();
                        boolean isValid = false;
                        try {
                            try {
                                List<Invite> invites = RateLimitUtil.complete(db.guild.retrieveInvites());
                                for (Invite invite : invites) {
                                    String inviteCode = invite.getCode();
                                    inviteCodes.add(inviteCode);
                                }
                            } catch (InsufficientPermissionException ignore) {}

                            if (!inviteCodes.isEmpty() && alliance.getDiscord_link() != null && !alliance.getDiscord_link().isEmpty()) {
                                for (String code : inviteCodes) {
                                    if (alliance.getDiscord_link().contains(code)) {
                                        isValid = true;
                                        break;
                                    }
                                }
                            }

                            if (!isValid) {
                                String url = "" + Settings.INSTANCE.PNW_URL() + "/alliance/id=" + aaId;
                                String content = FileUtil.readStringFromURL(url);
                                String idStr = db.guild.getId();

                                if (!content.contains(idStr)) {
                                    for (String inviteCode : inviteCodes) {
                                        if (content.contains(inviteCode)) {
                                            isValid = true;
                                            break;
                                        }
                                    }
                                }
                            }

                            if (!isValid) {
                                String msg = "1. Go to: <" + Settings.INSTANCE.PNW_URL() + "/alliance/edit/id=" + aaId + ">\n" +
                                        "2. Scroll down to where it says Alliance Description:\n" +
                                        "3. Put your guild id `" + db.getIdLong() + "` somewhere in the text\n" +
                                        "4. Click save\n" +
                                        "5. Run the command " + CM.settings.cmd.create(GuildDB.Key.ALLIANCE_ID.name(), "" + value + "", null, null) + " again\n" +
                                        "(note: you can remove the id after setup)";
                                throw new IllegalArgumentException(msg);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    if (otherDb != null && otherDb != db) {
                        otherDb.deleteInfo(Key.ALLIANCE_ID);

                        String msg = "Only 1 root server per Alliance is permitted. The ALLIANCE_ID in the other guild: " + otherDb.getGuild() + " has been removed.\n" +
                                "To have multiple servers, set the ALLIANCE_ID on your primary server, and then set " + CM.settings.cmd.create(GuildDB.Key.DELEGATE_SERVER.name(), "<guild-id>", null, null) + " on your other servers\n" +
                                "The `<guild-id>` for this server is `" + db.getIdLong() + "` and the id for the other server is `" + otherDb.getIdLong() + "`.\n\n" +
                                "Run this command again to confirm and set the ALLIANCE_ID";
                        throw new IllegalArgumentException(msg);
                    }
                }
                return StringMan.join(aaIds, ",");
            }
            @Override
            public Object parse(GuildDB db, String input) {
                String[] split = input.split(",");
                Set<Integer> parsed = new LinkedHashSet<>(split.length);
                for (String arg : split) {
                    Integer id = PnwUtil.parseAllianceId(arg);
                    if (id == null) {
                        throw new IllegalArgumentException("Invalid alliance: " + arg);
                    }
                    parsed.add(id);
                }
                return parsed;
            }

            @Override
            public String help() {
                return "Your alliance id. `null` if no alliance";
            }

            @Override
            public String toString(Object value) {
                return StringMan.join((Set<Integer>) value, ",");
            }
        },

        API_KEY() {
            @Override
            public String[] parse(GuildDB db, String input) {
                return input.split(",");
            }

            @Override
            public String validate(GuildDB db, String value) {
                Set<Integer> aaIds = db.getAllianceIds();
                if (aaIds.isEmpty()) {
                    throw new IllegalArgumentException("Please first use " + CM.settings.cmd.create(GuildDB.Key.ALLIANCE_ID.name(), "<alliance>", null, null) + "");
                }

                String[] keys = value.split(",");
                for (String key : keys) {
                    try {
                        Integer nationId = Locutus.imp().getDiscordDB().getNationFromApiKey(key);
                        if (nationId == null) {
                            throw new IllegalArgumentException("Invalid API key");
                        }
                        DBNation nation = DBNation.byId(nationId);
                        if (nation == null) {
                            throw new IllegalArgumentException("Nation not found for id: " + nationId + "(out of sync?)");
                        }
                        if (!aaIds.contains(nation.getAlliance_id())) {
                            nation.update(true);
                            if (!aaIds.contains(nation.getAlliance_id())) {
                                throw new IllegalArgumentException("Nation " + nation.getName() + " is not in your alliance: " + StringMan.getString(aaIds));
                            }
                        }
                    } catch (Throwable e) {
                        try {
                            ApiKeyDetails details = new PoliticsAndWarV3(ApiKeyPool.builder().addKeyUnsafe(key).build()).getApiKeyStats();
                            Integer nationId = details.getNation().getId();
                            if (nationId != null) {
                                DBNation nation = DBNation.byId(nationId);
                                if (nation != null && !aaIds.contains(nation.getAlliance_id())) {
                                    continue;
                                }
                                throw new IllegalArgumentException("API key is not from a nation in the alliance (nation: " + nation + "): " + e.getMessage());
                            }
                            System.out.println("Record " + details);
                        } catch (Throwable ignore) {
                            ignore.printStackTrace();
                        }
                        throw new IllegalArgumentException("Key was rejected: " + e.getMessage());
                    }
                }


                return value;
            }

            @Override
            public String toString(Object value) {
                return "<redacted>";
            }

            @Override
            public String help() {
                return "APi key found on: <https://politicsandwar.com/account/>.";
            }
        },

        ESPIONAGE_ALERT_CHANNEL(false, API_KEY, CommandCategory.MILCOM) {
            @Override
            public String validate(GuildDB db, String value) {
                db.getOrThrow(Key.ALLIANCE_ID);
                Set<Integer> aaIds = db.getAllianceIds(true);
                if (aaIds.isEmpty()) {
                    throw new IllegalArgumentException("Guild not registered to an alliance. See: " + CM.settings.cmd.toSlashMention() + " with key `" + ALLIANCE_ID.name() + "`");
                }
                String msg = "Invalid api key set. See " + CM.settings.cmd.toSlashMention() + " with key `" + API_KEY.name() + "`";
                for (String key : ((String[]) db.getOrThrow(Key.API_KEY))) {
                    Integer nationId = Locutus.imp().getDiscordDB().getNationFromApiKey(key);
                    if (nationId == null) throw new IllegalArgumentException(msg);
                    DBNation nation = DBNation.byId(nationId);
                    if (nation.getAlliancePosition() == null) throw new IllegalArgumentException(msg + " (no position found for nation: " + nationId + ")");
                    if (!nation.getAlliancePosition().hasPermission(AlliancePermission.SEE_SPIES)) throw new IllegalArgumentException(msg + " (nation: " + nationId + " does not have permission " + AlliancePermission.SEE_SPIES + ")");
                    if (!aaIds.contains(nation.getAlliance_id())) throw new IllegalArgumentException(msg + " (nation: " + nationId + " is not in your alliance: " + StringMan.getString(aaIds) + ")");

                    PoliticsAndWarV3 api = new PoliticsAndWarV3(ApiKeyPool.create(nationId, key));
                    try {
                        api.getApiKeyStats();
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException(e.getMessage() + " (for nation: " + nationId + ")");
                    }
                }
                PnwPusherShardManager pusher = Locutus.imp().getPusher();
                if (pusher == null) {
                    throw new IllegalArgumentException("Pusher is not enabled. Please contact the bot owner.");
                }
                String channelName = Key.validateChannel(db, value);
                for (int aaId : aaIds) {
                    pusher.setupSpySubscriptions(db, DBAlliance.getOrCreate(aaId));
                }

                return channelName;
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }

            @Override
            public String help() {
                return "The channel to get alerts when a member is spied";
            }
        },

        EMBASSY_CATEGORY(true, ALLIANCE_ID, CommandCategory.FOREIGN_AFFAIRS) {
            @Override
            public String validate(GuildDB db, String value) {
                Guild guild = db.getGuild();
                Category category = DiscordUtil.getCategory(guild, value);
                if (category == null) {
                    throw new IllegalArgumentException("Invalid category: " + value);
                }
                if (!category.getGuild().equals(guild)) {
                    throw new IllegalArgumentException("Invalid guild for: " + value);
                }
                return category.getId();
            }
            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getCategory(db.getGuild(), input);
            }

            @Override
            public String help() {
                return "The name or id of the CATEGORY you would like embassy channels created in (for " + CM.embassy.cmd.toSlashMention() + ")";
            }
        },

        ASSIGNABLE_ROLES(true, null, CommandCategory.INTERNAL_AFFAIRS) {
            @Override
            public Object parse(GuildDB db, String input) {
                Guild guild = db.getGuild();
                Map<Role, Set<Role>> result = new LinkedHashMap<>();
                for (String line : input.split("\n")) {
                    String[] split = line.split("[:=]");
                    String key = split[0].trim();
                    Role roleKey = DiscordUtil.getRole(guild, key);
                    if (roleKey != null) {
                        for (String roleId : split[1].split(",")) {
                            roleId = roleId.trim();
                            Role roleValue = DiscordUtil.getRole(guild, roleId);

                            if (roleValue != null) {
                                result.computeIfAbsent(roleKey, f -> new HashSet<>()).add(roleValue);
                            }
                        }
                    }
                }

                return result;
            }

            @Override
            public String toString(Object parsed) {
                Map<Role, Set<Role>> map = (Map<Role, Set<Role>>) parsed;
                List<String> lines = new ArrayList<>();
                for (Map.Entry<Role, Set<Role>> entry : map.entrySet()) {
                    String key = entry.getKey().getAsMention();
                    List<String> valueStrings = entry.getValue().stream().map(f -> f.getAsMention()).collect(Collectors.toList());
                    String value = StringMan.join(valueStrings, ",");

                    lines.add(key +":" + value);
                }
                return StringMan.join(lines, "\n");
            }

            @Override
            public String help() {
                return "Map roles that can be assigned (or removed). See `" +  CM.self.create.cmd.toSlashMention() + "` " + CM.role.removeAssignableRole.cmd.toSlashMention() + " " + CM.role.add.cmd.toSlashMention() + " " + CM.role.remove.cmd.toSlashMention() + "";
            }
        },

        DEFENSE_WAR_CHANNEL(true, ALLIANCE_ID, CommandCategory.MILCOM) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }
            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }

            @Override
            public String help() {
                return "The #channel to receive alerts for defensive wars";
            }
        },

        OFFENSIVE_WAR_CHANNEL(true, ALLIANCE_ID, CommandCategory.MILCOM) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }
            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The #channel to receive alerts for offensive wars";
            }
        },

        WAR_PEACE_ALERTS(true, ALLIANCE_ID, CommandCategory.MILCOM) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }
            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The #channel to receive alerts for changes to any war peace offers";
            }
        },

        UNBLOCKADE_REQUESTS(true, null, CommandCategory.MILCOM) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }
            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The #channel to receive alerts for unblockade requests";
            }
        },

        BLOCKADED_ALERTS(true, DEFENSE_WAR_CHANNEL, CommandCategory.MILCOM) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }
            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The #channel to receive alerts for blockades";
            }
        },

        UNBLOCKADED_ALERTS(true, BLOCKADED_ALERTS, CommandCategory.MILCOM) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }
            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The #channel to receive alerts for unblockades";
            }
        },

        SHOW_ALLY_OFFENSIVE_WARS(true, OFFENSIVE_WAR_CHANNEL, CommandCategory.MILCOM) {
            @Override
            public boolean allowed(GuildDB db) {
                return db.getOrNull(ALLIANCE_ID) != null && !db.getCoalition("allies").isEmpty();
            }

            @Override
            public String validate(GuildDB db, String value) {
                return Boolean.valueOf(value) + "";
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return Boolean.parseBoolean(input);
            }
            @Override
            public String help() {
                return "Whether to show offensive war alerts for allies (true/false)";
            }
        },

        SHOW_ALLY_DEFENSIVE_WARS(true, DEFENSE_WAR_CHANNEL, CommandCategory.MILCOM) {
            @Override
            public boolean allowed(GuildDB db) {
                return db.getOrNull(ALLIANCE_ID) != null && !db.getCoalition("allies").isEmpty();
            }

            @Override
            public String validate(GuildDB db, String value) {
                return Boolean.valueOf(value) + "";
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return Boolean.parseBoolean(input);
            }
            @Override
            public String help() {
                return "Whether to show offensive war alerts for allies (true/false)";
            }
        },

        HIDE_APPLICANT_WARS(true, OFFENSIVE_WAR_CHANNEL, CommandCategory.MILCOM) {
            @Override
            public boolean allowed(GuildDB db) {
                return db.getOrNull(ALLIANCE_ID) != null;
            }

            @Override
            public String validate(GuildDB db, String value) {
                return Boolean.valueOf(value) + "";
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return Boolean.parseBoolean(input);
            }
            @Override
            public String help() {
                return "Whether to hide war alerts for applicants";
            }
        },

        MEMBER_CAN_SET_BRACKET(false, ALLIANCE_ID, CommandCategory.ECON) {
            @Override
            public String validate(GuildDB db, String value) {
                return Boolean.valueOf(value) + "";
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return Boolean.parseBoolean(input);
            }
            @Override
            public String help() {
                return "Whether members can use " + CM.nation.set.taxbracket.cmd.toSlashMention() + "";
            }
        },

        MEMBER_CAN_OFFSHORE(false, ALLIANCE_ID, CommandCategory.ECON) {
            @Override
            public boolean allowed(GuildDB db) {
                return db.getOrNull(ALLIANCE_ID) != null && !db.getCoalition("offshore").isEmpty();
            }

            @Override
            public String validate(GuildDB db, String value) {
                return Boolean.valueOf(value) + "";
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return Boolean.parseBoolean(input);
            }
            @Override
            public String help() {
                return "Whether members can use " + CM.offshore.send.cmd.toSlashMention() + " (true/false)";
            }
        },

        MEMBER_CAN_WITHDRAW(false, null, CommandCategory.ECON) {
            @Override
            public boolean allowed(GuildDB db) {
                return db.getOffshore() != null;
            }

            @Override
            public String validate(GuildDB db, String value) {
                return Boolean.valueOf(value) + "";
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return Boolean.parseBoolean(input);
            }
            @Override
            public String help() {
                return "Whether members can use " + CM.transfer.resources.cmd.toSlashMention() + " or " + Settings.commandPrefix(true) + "grant` to access their own funds (true/false)";
            }
        },

        MEMBER_CAN_WITHDRAW_WARTIME(false, MEMBER_CAN_WITHDRAW, CommandCategory.ECON) {
            @Override
            public boolean allowed(GuildDB db) {
                return db.getOffshore() != null;
            }

            @Override
            public String validate(GuildDB db, String value) {
                return Boolean.valueOf(value) + "";
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return Boolean.parseBoolean(input);
            }
            @Override
            public String help() {
                return "Whether members can withdraw during wartime (true/false)";
            }
        },

        MEMBER_CAN_WITHDRAW_IGNORES_GRANTS(false, MEMBER_CAN_WITHDRAW, CommandCategory.ECON) {
            @Override
            public boolean allowed(GuildDB db) {
                return db.getOffshore() != null;
            }

            @Override
            public String validate(GuildDB db, String value) {
                return Boolean.valueOf(value) + "";
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return Boolean.parseBoolean(input);
            }
            @Override
            public String help() {
                return "Whether members's withdraw limit ignores their grants (true/false)";
            }
        },

        DISPLAY_ITEMIZED_DEPOSITS(false, null, CommandCategory.ECON) {
            @Override
            public boolean allowed(GuildDB db) {
                return Roles.MEMBER.toRole(db) != null;
            }

            @Override
            public String validate(GuildDB db, String value) {
                return Boolean.valueOf(value) + "";
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return Boolean.parseBoolean(input);
            }
            @Override
            public String help() {
                return "Whether members's deposits are displayed by default with a breakdown of each category (true/false)";
            }
        },

//        MEMBER_CAN_GRANT_SELF(false, MEMBER_CAN_WITHDRAW, CommandCategory.ECON) {
//            @Override
//            public boolean allowed(GuildDB db) {
//                return db.getOffshore() != null;
//            }
//
//            @Override
//            public String validate(GuildDB db, String value) {
//                return Boolean.valueOf(value) + "";
//            }
//
//            @Override
//            public Object parse(GuildDB db, String input) {
//                return Boolean.parseBoolean(input);
//            }
//            @Override
//            public String help() {
//                return "Whether members can send themselves grants (Not recommended)";
//            }
//        },

        LOST_WAR_CHANNEL(true, ALLIANCE_ID, CommandCategory.MILCOM) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }
            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }

            @Override
            public String help() {
                return "The #channel to post wars when our side loses a war";
            }
        },

        WON_WAR_CHANNEL(true, ALLIANCE_ID, CommandCategory.MILCOM) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }
            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }

            @Override
            public String help() {
                return "The #channel to post wars when our side wins a war (only includes actives)";
            }
        },

//        UNCOUNTERED_TASKS(true, ALLIANCE_ID, null) {
//            @Override
//            public String validate(GuildDB db, String value) {
//                return Key.validateChannel(db, value);
//            }
//            @Override
//            public Object parse(GuildDB db, String input) {
//                return DiscordUtil.getChannel(db.getGuild(), input);
//            }
//
//            @Override
//            public String help() {
//                return "The channel for uncountered war tasks";
//            }
//        },
//
//        COUNTERING_TASKS(true, ALLIANCE_ID, null) {
//            @Override
//            public String validate(GuildDB db, String value) {
//                return Key.validateChannel(db, value);
//            }
//            @Override
//            public Object parse(GuildDB db, String input) {
//                return DiscordUtil.getChannel(db.getGuild(), input);
//            }
//
//            @Override
//            public String help() {
//                return "The channel for countering war tasks";
//            }
//        },

        DEPOSIT_ALERT_CHANNEL(true, ALLIANCE_ID, CommandCategory.ECON) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }
            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The #channel to receive alerts when a nation makes a deposit (this will no longer reliably alert)";
            }
        },

        WITHDRAW_ALERT_CHANNEL(false, ALLIANCE_ID, CommandCategory.ECON) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }
            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The #channel to receive alerts when a nation requests a transfer";
            }
        },

        ADDBALANCE_ALERT_CHANNEL(false, ALLIANCE_ID, CommandCategory.ECON) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }
            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The #channel to receive alerts when balance is added";
            }
        },

        BANK_ALERT_CHANNEL(false, null, CommandCategory.ECON) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }

            @Override
            public boolean allowed(GuildDB db) {
                return db.getPermission(BankAlerts.class) > 0;
            }

            @Override
            public String help() {
                return "The #channel to receive alerts e.g. for custom `" + Settings.commandPrefix(true) + "BankAlerts`";
            }
        },

        REROLL_ALERT_CHANNEL(false, null, CommandCategory.ECON) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }

            @Override
            public String help() {
                return "The #channel to receive alerts for nation rerolls";
            }
        },

        DELETION_ALERT_CHANNEL(false, null, CommandCategory.GAME_INFO_AND_TOOLS) {
            @Override
            public boolean allowed(GuildDB db) {
                return db.getPermission(BankAlerts.class) > 0;
            }

            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The channel to receive alerts when a nation deletes (in all of orbis)";
            }
        },

        FA_CONTACT_SHEET(false, ALLIANCE_ID, CommandCategory.ECON),
        TRANSFER_SHEET(false, ALLIANCE_ID, CommandCategory.ECON),
        BANK_TRANSACTION_SHEET(false, ALLIANCE_ID, CommandCategory.ECON),
        NOTE_SHEET(false, ALLIANCE_ID, CommandCategory.INTERNAL_AFFAIRS),
        IA_SHEET(false, ALLIANCE_ID, CommandCategory.INTERNAL_AFFAIRS),
        WAR_BUILDUP_SHEET(false, ALLIANCE_ID, CommandCategory.MILCOM),
        GRANT_SHEET(false, ALLIANCE_ID, CommandCategory.ECON),
        COALITION_SHEET(false, ALLIANCE_ID, CommandCategory.MILCOM),
        NATION_SHEET(false, ALLIANCE_ID, CommandCategory.MILCOM),
        DESERTER_SHEET(false, ALLIANCE_ID, CommandCategory.MILCOM),
        MAIL_RESPONSES_SHEET(false, ALLIANCE_ID, CommandCategory.INTERNAL_AFFAIRS),
        ALLIANCES_SHEET(false, ALLIANCE_ID, CommandCategory.MILCOM),
        ROI_SHEET(false, ALLIANCE_ID, CommandCategory.ECON),
        TAX_SHEET(false, ALLIANCE_ID, CommandCategory.ECON),
        TAX_RECORD_SHEET(false, ALLIANCE_ID, CommandCategory.ECON),
        TAX_GRAPH_SHEET(false, ALLIANCE_ID, CommandCategory.ECON),
        WAR_SHEET(false, ALLIANCE_ID, CommandCategory.MILCOM),
        ACTIVE_COMBATANT_SHEET(false, ALLIANCE_ID, CommandCategory.MILCOM),
        COUNTER_SHEET(false, ALLIANCE_ID, CommandCategory.MILCOM),
        MMR_SHEET(false, ALLIANCE_ID, CommandCategory.MILCOM),
        SPYOP_SHEET(false, ALLIANCE_ID, CommandCategory.MILCOM),
        DEPOSITS_SHEET(false, ALLIANCE_ID, CommandCategory.ECON),
        TAX_BRACKET_SHEET(false, ALLIANCE_ID, CommandCategory.ECON),
        STOCKPILE_SHEET(false, ALLIANCE_ID, CommandCategory.ECON),
        WAR_COST_SHEET(false, ALLIANCE_ID, CommandCategory.MILCOM),
        CURRENT_LOOT_SHEET(false, ALLIANCE_ID, CommandCategory.MILCOM),
        WAR_COST_BY_ALLIANCE_SHEET(false, ALLIANCE_ID, CommandCategory.MILCOM),
        WAR_COST_BY_RESOURCE_SHEET(false, ALLIANCE_ID, CommandCategory.ECON),
        ACTIVITY_SHEET(false, ALLIANCE_ID, CommandCategory.MILCOM),
        CITY_GRAPH_SHEET(false, ALLIANCE_ID, CommandCategory.MILCOM),
        WAR_COST_BY_CITY_SHEET(false, ALLIANCE_ID, CommandCategory.MILCOM),
        MILITARY_GRAPH_SHEET(false, ALLIANCE_ID, CommandCategory.MILCOM),
        REVENUE_SHEET(false, ALLIANCE_ID, CommandCategory.ECON),
        REVENUE_BY_ALLIANCE(false, ALLIANCE_ID, CommandCategory.ECON),
        PROJECT_SHEET(false, ALLIANCE_ID, CommandCategory.ECON),
        INTERVIEW_SHEET(false, ALLIANCE_ID, CommandCategory.INTERNAL_AFFAIRS),
        NATION_META_SHEET(false, ALLIANCE_ID, CommandCategory.INTERNAL_AFFAIRS),
        TRADE_PROFIT_SHEET(false, ALLIANCE_ID, CommandCategory.ECON),
        TRADE_VOLUME_SHEET(false, ALLIANCE_ID, CommandCategory.ECON),
        MMR_BY_SCORE_SHEET(false, ALLIANCE_ID, CommandCategory.MILCOM),
        WARCHEST_SHEET(false, ALLIANCE_ID, CommandCategory.ECON),

        AUTONICK() {
            @Override
            public String validate(GuildDB db, String value) {
                return StringMan.parseUpper(AutoNickOption.class, value.toUpperCase()).name();
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return StringMan.parseUpper(AutoNickOption.class, input);
            }
            @Override
            public String help() {
                return "Options: " + StringMan.getString(AutoNickOption.values()) + "\n" +
                        "See also: " + CM.role.clearNicks.cmd.toSlashMention() + "";
            }
        },

        AUTOROLE() {
            @Override
            public String validate(GuildDB db, String value) {
                return StringMan.parseUpper(AutoRoleOption.class, value.toUpperCase()).name();
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return AutoRoleOption.valueOf(input.toUpperCase());
            }
            @Override
            public String help() {
                return "Options: " + StringMan.getString(AutoRoleOption.values()) + "\n" +
                        "See also:\n" +
                        " - " + CM.coalition.create.cmd.create(null, Coalition.MASKEDALLIANCES.name()) + "\n" +
                        " - " + CM.role.clearAllianceRoles.cmd.toSlashMention() + "\n" +
                        " - " + CM.settings.cmd.create(GuildDB.Key.AUTOROLE_ALLIANCE_RANK.name(), null, null, null).toSlashCommand() + "\n" +
                        " - " + CM.settings.cmd.create(GuildDB.Key.AUTOROLE_TOP_X.name(), null, null, null).toSlashCommand();
            }
        },

        AUTOROLE_ALLIANCE_RANK(true, AUTOROLE, CommandCategory.INTERNAL_AFFAIRS) {
            @Override
            public String validate(GuildDB db, String value) {
                return StringMan.parseUpper(Rank.class, value.toUpperCase()).name();
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return StringMan.parseUpper(Rank.class, input.toUpperCase());
            }
            @Override
            public String help() {
                return "The ingame rank required to get an alliance role. (default: member) Options: " + StringMan.getString(Rank.values());
            }
        },

        AUTOROLE_TOP_X(true, AUTOROLE, CommandCategory.INTERNAL_AFFAIRS) {
            @Override
            public String validate(GuildDB db, String value) {
                return Integer.parseInt(value) + "";
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return Integer.parseInt(input);
            }
            @Override
            public String help() {
                return "The number of top alliances to provide roles for, defaults to `0`";
            }
        },

        DO_NOT_RAID_TOP_X(true, ALLIANCE_ID, CommandCategory.FOREIGN_AFFAIRS) {
            @Override
            public String validate(GuildDB db, String value) {
                return Integer.parseInt(value) + "";
            }

            @Override
            public boolean allowed(GuildDB db) {
                return true;
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return Integer.parseInt(input);
            }
            @Override
            public String help() {
                return "The number of top alliances to include in the DNR defaults to `0`";
            }
        },

        AUTOROLE_ALLY_GOV() {
            @Override
            public String validate(GuildDB db, String value) {
                return Boolean.valueOf(value) + "";
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return Boolean.parseBoolean(input);
            }
            @Override
            public String help() {
                return "Whether to give gov/member roles to allies (this is intended for coalition servers), `true` or `false`";
            }
        },

        AUTOROLE_ALLY_ROLES(false, AUTOROLE_ALLY_GOV, CommandCategory.GUILD_MANAGEMENT) {
            @Override
            public String validate(GuildDB db, String value) {
                return toString(parse(db, value));
            }

            @Override
            public Object parse(GuildDB db, String input) {
                Set<Roles> roles = new HashSet<>();
                for (String arg : input.split(",")) {
                    roles.add(Roles.parse(arg));
                }
                return roles;
            }

            @Override
            public String toString(Object value) {
                return StringMan.join(((Set<Roles>) value).stream().map(f -> f.name()).collect(Collectors.toList()), ",");
            }

            @Override
            public String help() {
                return "List of roles to autorole from ally servers\n" +
                        "(this is intended for coalition servers to give gov roles to allies)";
            }
        },

        MENTION_MILCOM_FILTER(false, DEFENSE_WAR_CHANNEL, CommandCategory.MILCOM) {
            @Override
            public String validate(GuildDB db, String value) {
                Set<DBNation> nations = DiscordUtil.parseNations(db.getGuild(), value);
                return value;
            }

            @Override
            public Set<DBNation> parse(GuildDB db, String input) {
                return DiscordUtil.parseNations(db.getGuild(), input);
            }
            @Override
            public String help() {
                return "A nation filter to apply to limit what wars milcom gets pinged for. ";
            }
        },

        WAR_ALERT_FOR_OFFSHORES(true, DEFENSE_WAR_CHANNEL, CommandCategory.MILCOM) {
            @Override
            public <T> boolean hasPermission(GuildDB db, User author, T value) {
                return super.hasPermission(db, author, value) && !db.getCoalition(Coalition.OFFSHORE).isEmpty();
            }

            @Override
            public String validate(GuildDB db, String value) {
                return Boolean.valueOf(value) + "";
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return Boolean.parseBoolean(input);
            }
            @Override
            public String help() {
                return "Whether to do war alerts for offshore alliances";
            }
        },

        ENABLE_WAR_ROOMS(true, ALLIANCE_ID, CommandCategory.MILCOM) {
            @Override
            public String validate(GuildDB db, String value) {
                return Boolean.valueOf(value) + "";
            }

            @Override
            public Object parse(GuildDB db, String input) {
                db.warChannelInit = false;
                return Boolean.parseBoolean(input);
            }
            @Override
            public String help() {
                return "If war rooms should be enabled (i.e. auto generate a channel for wars against active nations)\n" +
                        "Note: Defensive war channels must be enabled to have auto war room creation";
            }
        },

        WAR_SERVER(false, ENABLE_WAR_ROOMS, CommandCategory.MILCOM) {
            @Override
            public String validate(GuildDB db, String value) {
                value = Key.validateGuild(value);
                Guild guild = Locutus.imp().getDiscordApi().getGuildById(Long.parseLong(value));
                GuildDB otherDb = Locutus.imp().getGuildDB(guild);
                if (guild.getIdLong() == db.getGuild().getIdLong()) throw new IllegalArgumentException("Use " + CM.settings.cmd.create(GuildDB.Key.WAR_SERVER.name(), "null", null, null) + " to unset the war server");
                if (otherDb.getOrNull(Key.WAR_SERVER) != null) throw new IllegalArgumentException("Circular reference. The server you have set already defers its war room");
                return value;
            }

            @Override
            public boolean hasPermission(GuildDB db, User author, Object value) {
                if (!super.hasPermission(db, author, value)) return false;
                if (value == null) return true;
                Guild guild = (Guild) value;
                if (!Roles.ADMIN.has(author, guild)) throw new IllegalArgumentException("You do not have ADMIN on " + guild);
                return true;
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return Locutus.imp().getDiscordApi().getGuildById(Long.parseLong(input));
            }

            @Override
            public String toString(Object value) {
                return ((Guild) value).getName();
            }
            @Override
            public String help() {
                return "The guild to defer war rooms to";
            }
        },

        DELEGATE_SERVER(false, null, CommandCategory.ADMIN) {
            @Override
            public String validate(GuildDB db, String value) {
                Map<Integer, Long> ids = (Map<Integer, Long>) parse(db, value);
                if (db.getOrNull(Key.ALLIANCE_ID) != null) {
                    throw new IllegalArgumentException("You cannot delegate a server with an alliance id set");
                }
                // loop over ids
                for (Map.Entry<Integer, Long> entry : ids.entrySet()) {
                    Guild guild = Locutus.imp().getDiscordApi().getGuildById(entry.getValue());
                    if (guild == null) throw new IllegalArgumentException("Invalid guild: `" + entry.getValue() + "` (are you sure locutus is in that server?)");
                    GuildDB otherDb = Locutus.imp().getGuildDB(guild);
                    if (guild.getIdLong() == db.getIdLong()) throw new IllegalArgumentException("You cannot set the delegate as this guild");
                    if (otherDb.getInfo(Key.DELEGATE_SERVER, false) != null) throw new IllegalArgumentException("Circular reference. The server you have set already delegates its DELEGATE_SERVER");
                }
                if (db.getOrNull(Key.ALLIANCE_ID) != null) throw new IllegalArgumentException("Cannot delegate alliance guilds (please unset ALLIANCE_ID first)");
                return value;
            }

            @Override
            public boolean hasPermission(GuildDB db, User author, Object value) {
                if (!super.hasPermission(db, author, value)) return false;
                if (value == null) return true;
                Map.Entry<Integer, Long> entry = (Map.Entry<Integer, Long>) value;
                GuildDB otherDB = Locutus.imp().getGuildDB(entry.getValue());
                if (otherDB == null) {
                    throw new IllegalArgumentException("Invalid guild: `" + entry.getValue() + "` (are you sure locutus is in that server?)");
                }
                if (!Roles.ADMIN.has(author, otherDB.getGuild())) throw new IllegalArgumentException("You do not have ADMIN on " + otherDB.getGuild());
                return true;
            }

            @Override
            public Object parse(GuildDB db, String input) {
                String[] split2 = input.trim().split("[:|=]", 2);
                if (split2.length == 2) {
                    return Map.entry(Integer.parseInt(split2[0]), Long.parseLong(split2[1]));
                } else {
                    return Map.entry(0, Long.parseLong(input));
                }
            }

            @Override
            public String toString(Object value) {
                Map.Entry<Integer, Long> pair = (Map.Entry<Integer, Long>) value;
                if (pair.getKey() == 0) return pair.getValue() + "";
                return pair.getKey() + ":" + pair.getValue();
            }
            @Override
            public String help() {
                return "The guild to delegate unset settings to";
            }
        },

        FA_SERVER(false, null, CommandCategory.FOREIGN_AFFAIRS) {
            @Override
            public String validate(GuildDB db, String value) {
                value = Key.validateGuild(value);
                Guild guild = Locutus.imp().getDiscordApi().getGuildById(Long.parseLong(value));
                GuildDB otherDb = Locutus.imp().getGuildDB(guild);
                if (guild.getIdLong() == db.getGuild().getIdLong()) throw new IllegalArgumentException("Use " + CM.settings.cmd.create(GuildDB.Key.FA_SERVER.name(), "null", null, null) + " to unset the FA_SERVER");
                if (otherDb.getInfo(Key.FA_SERVER, false) != null) throw new IllegalArgumentException("Circular reference. The server you have set already defers its FA_SERVER");
                return value;
            }

            @Override
            public boolean hasPermission(GuildDB db, User author, Object value) {
                if (!super.hasPermission(db, author, value)) return false;
                if (value == null) return true;
                GuildDB otherDB = (GuildDB) value;
                if (!Roles.ADMIN.has(author, otherDB.getGuild())) throw new IllegalArgumentException("You do not have ADMIN on " + otherDB.getGuild());
                return true;
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return Locutus.imp().getGuildDB(Long.parseLong(input));
            }

            @Override
            public String toString(Object value) {
                return ((Guild) value).getName();
            }
            @Override
            public String help() {
                return "The guild to defer coalitions to";
            }
        },

        RESOURCE_CONVERSION(true, ALLIANCE_ID, CommandCategory.ECON) {
            @Override
            public String validate(GuildDB db, String value) {
                return Boolean.valueOf(value) + "";
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return Boolean.parseBoolean(input);
            }
            @Override
            public String help() {
                return "If the alliance can convert resources to cash.\n" +
                        "This is done virtually in " + CM.deposits.check.cmd.toSlashMention() + "" +
                        "Resources are converted using market average\n" +
                        "Use `#cash` as the note when depositing or transferring funds";
            }
        },

//        DISABLE_CHANNEL_MOVING(false, null, CommandCategory.INTERNAL_AFFAIRS) {
//            @Override
//            public String validate(GuildDB db, String value) {
//                return Boolean.valueOf(value) + "";
//            }
//
//            @Override
//            public Object parse(GuildDB db, String input) {
//                return Boolean.parseBoolean(input);
//            }
//            @Override
//            public String help() {
//                return "If channel moving should be prevented";
//            }
//
//            @Override
//            public boolean hasPermission(GuildDB db, User author) {
//                return Roles.hasAny(author, db.getGuild(), Roles.ADMIN, Roles.INTERNAL_AFFAIRS, Roles.ECON, Roles.MILCOM, Roles.FOREIGN_AFFAIRS);
//            }
//        },

        DEPOSIT_INTEREST(true, ALLIANCE_ID, CommandCategory.ECON) {
            @Override
            public String validate(GuildDB db, String value) {
                return Boolean.valueOf(value) + "";
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return Boolean.parseBoolean(input);
            }

            @Override
            public boolean allowed(GuildDB db) {
                return db.isWhitelisted();
            }

            @Override
            public String help() {
                return "";
            }
        },

//        WARCHEST_INCLUDES_DEPOSITS(true, ALLIANCE_ID, CommandCategory.ECON) {
//            @Override
//            public String validate(GuildDB db, String value) {
//                return Boolean.valueOf(value) + "";
//            }
//
//            @Override
//            public Object parse(GuildDB db, String input) {
//                return Boolean.parseBoolean(input);
//            }
//            @Override
//            public String help() {
//                return "Whether to include warchest in deposits, `true` or `false`";
//            }
//        },

        TRADE_ALERT_CHANNEL(true, null, CommandCategory.ECON) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public boolean allowed(GuildDB db) {
                return db.isWhitelisted();
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The #channel to receive alerts for trades";
            }
        },

        RECRUIT_MESSAGE_SUBJECT(true, API_KEY, CommandCategory.INTERNAL_AFFAIRS) {
            @Override
            public String validate(GuildDB db, String value) {
                if (value.length() >= 50) throw new IllegalArgumentException("Your subject line cannot be longer than 50 characters.");
                return value;
            }

            @Override
            public boolean allowed(GuildDB db) {
                if (db.getIdLong() == 940788925209923584L) return true;
                AllianceList aaList = db.getAllianceList();
                return aaList != null && !aaList.isEmpty();
            }

            @Override
            public String help() {
                return "The recruit message subject";
            }
        },

        RECRUIT_MESSAGE_CONTENT(true, RECRUIT_MESSAGE_SUBJECT, CommandCategory.INTERNAL_AFFAIRS) {
            @Override
            public boolean allowed(GuildDB db) {
                if (db.getIdLong() == 940788925209923584L) return true;
                AllianceList aaList = db.getAllianceList();
                return aaList != null && !aaList.isEmpty();
            }

            @Override
            public String help() {
                return "The recruit message body";
            }
        },

        RECRUIT_MESSAGE_OUTPUT(true, RECRUIT_MESSAGE_CONTENT, CommandCategory.INTERNAL_AFFAIRS) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }

            @Override
            public String help() {
                return "The #channel to receive recruitment message output";
            }

            @Override
            public boolean allowed(GuildDB db) {
                AllianceList aaList = db.getAllianceList();
                return aaList != null && !aaList.isEmpty();
            }
        },

        RECRUIT_MESSAGE_DELAY(true, RECRUIT_MESSAGE_OUTPUT, CommandCategory.INTERNAL_AFFAIRS) {
            @Override
            public String validate(GuildDB db, String value) {
                if (TimeUtil.timeToSec(value) <= 60) throw new IllegalArgumentException("please use a time format e.g. 3m");
                return value;
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return TimeUtil.timeToSec(input);
            }

            @Override
            public String toString(Object value) {
                return value.toString();
            }

            @Override
            public String help() {
                return "The amount of time to delay recruitment messages by";
            }

            @Override
            public boolean allowed(GuildDB db) {
                return db.isWhitelisted();
            }
        },

        BEIGE_ALERT_CHANNEL(true, ALLIANCE_ID, CommandCategory.MILCOM) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public boolean allowed(GuildDB db) {
                return db.isWhitelisted() && db.hasCoalitionPermsOnRoot(Coalition.RAIDPERMS);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }

            @Override
            public String help() {
                return "The #channel to receive alerts when a raid target leaves beige.\n" +
                        "" + CM.role.setAlias.cmd.create(Roles.BEIGE_ALERT.name(), null, null) + " must also be set and have members in range";
            }
        },

        ENEMY_BEIGED_ALERT(true, ALLIANCE_ID, CommandCategory.MILCOM) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public boolean allowed(GuildDB db) {
                return true;
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The #channel to receive alerts when an enemy gets beiged";
            }
        },

        ENEMY_BEIGED_ALERT_VIOLATIONS(true, ALLIANCE_ID, CommandCategory.MILCOM) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public boolean allowed(GuildDB db) {
                return true;
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The #channel to receive alerts when an enemy gets beiged (without reason)";
            }
        },

//        NO_BEIGE_RANGE(false, ENEMY_BEIGED_ALERT_VIOLATIONS, CommandCategory.MILCOM) {
//            @Override
//            public String validate(GuildDB db, String value) {
//                CityRanges range = CityRanges.parse(value);
//                return range.toString();
//            }
//
//            @Override
//            public boolean allowed(GuildDB db) {
//                return db.isWhitelisted();
//            }
//
//            @Override
//            public Object parse(GuildDB db, String input) {
//                return CityRanges.parse(input);
//            }
//
//            @Override
//            public String toString(Object value) {
//                return ((CityRanges) value).toString();
//            }
//
//            @Override
//            public String help() {
//                return "The list of city ranges to avoid beiging in (in the form `c5-10,c20+`)";
//            }
//        },

        REQUIRED_INTERNAL_TAXRATE(false, ALLIANCE_ID, CommandCategory.ECON) {
            @Override
            public Object parse(GuildDB db, String input) {
                Map<NationFilterString, TaxRate> filterToTaxRate = new LinkedHashMap<>();
                for (String line : input.split("\n")) {
                    String[] split = line.split("[:]");
                    if (split.length != 2) continue;

                    String filterStr = split[0].trim();

                    boolean containsNation = false;
                    for (String arg : filterStr.split(",")) {
                        if (!arg.startsWith("#")) containsNation = true;
                    }
                    if (!containsNation) filterStr += ",*";
                    NationFilterString filter = new NationFilterString(filterStr, db.getGuild());
                    TaxRate rate = new TaxRate(split[1]);
                    filterToTaxRate.put(filter, rate);
                }
                if (filterToTaxRate.isEmpty()) throw new IllegalArgumentException("No valid nation filters provided");

                return filterToTaxRate;
            }

            @Override
            public String toString(Object value) {
                Map<NationFilterString, TaxRate> filterToTaxRate = (Map<NationFilterString, TaxRate>) value;
                StringBuilder result = new StringBuilder();
                for (Map.Entry<NationFilterString, TaxRate> entry : filterToTaxRate.entrySet()) {
                    result.append(entry.getKey().getFilter() + ":" + entry.getValue() + "\n");
                }
                return result.toString().trim();
            }

            @Override
            public String help() {
                StringBuilder response = new StringBuilder("This setting maps nation filters to internal tax rate for bulk automation.\n" +
                        "To list nations current rates: " + CM.tax.listBracketAuto.cmd.toSlashMention() + "\n" +
                        "To bulk move nations: " + CM.nation.set.taxinternal.cmd.toSlashMention() + "\n" +
                        "Tax rate is in the form: `money/rss`\n" +
                        "In the form:\n" +
                        "```" +
                        "#cities<10:100/100\n" +
                        "#cities>=10:25/25" +
                        "```\n" +
                        "All nation filters are supported (e.g. roles)\n" +
                        "Priority is first to last (so put defaults at the bottom)");

                return response.toString();
            }
        },

        REQUIRED_TAX_BRACKET(false, API_KEY, CommandCategory.ECON) {
            @Override
            public String validate(GuildDB db, String value) {
                Map<NationFilterString, Integer> parsed = (Map<NationFilterString, Integer>) parse(db, value);

                AllianceList alliance = db.getAllianceList();
                if (alliance == null) throw new IllegalArgumentException("No valid `!KeyStore ALLIANCE_ID` set");

                Map<Integer, TaxBracket> brackets = alliance.getTaxBrackets(false);
                if (brackets.isEmpty()) throw new IllegalArgumentException("Could not fetch tax brackets. Is `!KeyStore API_KEY` correct?");

                for (Map.Entry<NationFilterString, Integer> entry : parsed.entrySet()) {
                    if (!brackets.containsKey(entry.getValue())) {
                        throw new IllegalArgumentException("No tax bracket founds for id: " + entry.getValue());
                    }
                }

                return value;
            }

            @Override
            public Object parse(GuildDB db, String input) {
                Map<NationFilterString, Integer> filterToTaxId = new LinkedHashMap<>();
                for (String line : input.split("\n")) {
                    String[] split = line.split("[:]");
                    if (split.length != 2) continue;

                    String filterStr = split[0].trim();

                    boolean containsNation = false;
                    for (String arg : filterStr.split(",")) {
                        if (!arg.startsWith("#")) containsNation = true;
                    }
                    if (!containsNation) filterStr += ",*";
                    NationFilterString filter = new NationFilterString(filterStr, db.getGuild());
                    int taxId = Integer.parseInt(split[1]);
                    filterToTaxId.put(filter, taxId);
                }

                return filterToTaxId;
            }

            @Override
            public String toString(Object value) {
                Map<NationFilterString, Integer> filterToTaxId = (Map<NationFilterString, Integer>) value;
                StringBuilder result = new StringBuilder();
                for (Map.Entry<NationFilterString, Integer> entry : filterToTaxId.entrySet()) {
                    result.append(entry.getKey().getFilter() + ":" + entry.getValue() + "\n");
                }
                return result.toString().trim();
            }

            @Override
            public String help() {
                StringBuilder response = new StringBuilder("This setting maps nation filters to ingame tax id for bulk automation.\n" +
                        "To list nations current rates: " + CM.tax.listBracketAuto.cmd.toSlashMention() + "\n" +
                        "To bulk move nations: " + CM.nation.set.taxbracketAuto.cmd.toSlashMention() + "\n" +
                        "In the form:\n" +
                        "```" +
                        "#cities<10:1234\n" +
                        "#cities>=10:5678" +
                        "```\n" +
                        "All nation filters are supported\n" +
                        "Priority is first to last (so put defaults at the bottom)");

                return response.toString();
            }
        },

        REQUIRED_MMR(false, ALLIANCE_ID, CommandCategory.MILCOM) {
            @Override
            public Object parse(GuildDB db, String input) {
                Map<NationFilterString, MMRMatcher> filterToMMR = new LinkedHashMap<>();
                for (String line : input.split("\n")) {
                    String[] split = line.split("[:]");
                    if (split.length != 2) continue;

                    String filterStr = split[0].trim();

                    boolean containsNation = false;
                    for (String arg : filterStr.split(",")) {
                        if (!arg.startsWith("#")) containsNation = true;
                    }
                    if (!containsNation) filterStr += ",*";
                    DiscordUtil.parseNations(db.getGuild(), filterStr); // validate
                    NationFilterString filter = new NationFilterString(filterStr, db.getGuild());
                    MMRMatcher mmr = new MMRMatcher(split[1]);
                    filterToMMR.put(filter, mmr);
                }

                return filterToMMR;
            }

            @Override
            public String toString(Object value) {
                Map<NationFilterString, MMRMatcher> filterToMMR = (Map<NationFilterString, MMRMatcher>) value;
                StringBuilder result = new StringBuilder();
                for (Map.Entry<NationFilterString, MMRMatcher> entry : filterToMMR.entrySet()) {
                    result.append(entry.getKey().getFilter() + ":" + entry.getValue().getRequired() + "\n");
                }
                return result.toString().trim();
            }

            @Override
            public String help() {
                StringBuilder response = new StringBuilder("A list of filters to required MMR.\n" +
                        "In the form:\n" +
                        "```" +
                        "#cities<10:505X\n" +
                        "#cities>=10:0250" +
                        "```\n" +
                        "All nation filters are supported");

                return response.toString();
            }
        },

        ALLOWED_BEIGE_REASONS(false, ENEMY_BEIGED_ALERT_VIOLATIONS, CommandCategory.MILCOM) {
            @Override
            public boolean allowed(GuildDB db) {
                return true;
            }

            @Override
            public Object parse(GuildDB db, String input) {
                input = input.replace("=", ":");

                Map<CityRanges, Set<BeigeReason>> result = new HashMap<>();
                String[] split = input.trim().split("\\r?\\n");
                for (String s : split) {
                    String[] pair = s.split(":");
                    if (pair.length != 2) throw new IllegalArgumentException("Invalid `CITY_RANGE:BEIGE_REASON` pair: `" + s + "`");
                    CityRanges range = CityRanges.parse(pair[0]);
                    List<BeigeReason> list = StringMan.parseEnumList(BeigeReason.class, pair[1]);
                    result.put(range, new HashSet<>(list));
                }
                return result;
            }

            @Override
            public String toString(Object value) {
                Map<CityRanges, Set<BeigeReason>> obj = (Map<CityRanges, Set<BeigeReason>>) value;
                StringBuilder result = new StringBuilder();
                for (Map.Entry<CityRanges, Set<BeigeReason>> entry : obj.entrySet()) {
                    result.append(entry.getKey().toString() + ":" + StringMan.join(entry.getValue(), ",")).append("\n");
                }
                return result.toString().trim();
            }

            @Override
            public String help() {
                StringBuilder response = new StringBuilder("A list of city ranges to beige reasons that are permitted.\n" +
                        "In the form:\n" +
                        "```" +
                        "c1-9:*\n" +
                        "c10+:INACTIVE,VACATION_MODE,APPLICANT" +
                        "```").append(" Options:\n");
                for (BeigeReason value : BeigeReason.values()) {
                    response.append(" - " + value.name() + ": " + value.getDescription()).append("\n");
                }
                return response.toString();
            }
        },

        ENEMY_ALERT_CHANNEL(true, ALLIANCE_ID, CommandCategory.MILCOM) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The #channel to receive alerts when an enemy nation leaves beige";
            }
        },

        BOUNTY_ALERT_CHANNEL(true, null, CommandCategory.MILCOM) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The channel to receive alerts when a bounty is placed";
            }
        },


        TREASURE_ALERT_CHANNEL(true, null, CommandCategory.MILCOM) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }

            @Override
            public <T> boolean hasPermission(GuildDB db, User author, T value) {
                return db.isWhitelisted() && db.hasCoalitionPermsOnRoot(Coalition.RAIDPERMS);
            }

            @Override
            public String help() {
                return "The channel to receive alerts when a bounty is placed";
            }
        },

        MEMBER_REBUY_INFRA_ALERT(false, ALLIANCE_ID, CommandCategory.INTERNAL_AFFAIRS) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The channel to receive alerts when a member buys infra";
            }
        },

        MEMBER_LEAVE_ALERT_CHANNEL(true, ALLIANCE_ID, CommandCategory.INTERNAL_AFFAIRS) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The channel to receive alerts when a member leaves";
            }
        },

        LOW_TIER_BUY_CITY_ALERTS(false, ALLIANCE_ID, CommandCategory.INTERNAL_AFFAIRS) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The channel to receive alerts when a <c10 member buys a city";
            }
        },

        INTERVIEW_INFO_SPAM(false, ALLIANCE_ID, CommandCategory.INTERNAL_AFFAIRS) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public boolean allowed(GuildDB db) {
                return true;
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The channel to receive info spam about expired interview channels";
            }
        },

        INTERVIEW_PENDING_ALERTS(true, ALLIANCE_ID, CommandCategory.INTERNAL_AFFAIRS) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public boolean allowed(GuildDB db) {
                return true;
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The channel to receive alerts when a member requests an interview";
            }
        },

        ARCHIVE_CATEGORY(true, INTERVIEW_PENDING_ALERTS, CommandCategory.INTERNAL_AFFAIRS) {
            @Override
            public String validate(GuildDB db, String value) {
                Guild guild = db.getGuild();
                Category category = DiscordUtil.getCategory(guild, value);
                if (category == null) {
                    throw new IllegalArgumentException("Invalid category: " + value);
                }
                if (!category.getGuild().equals(guild)) {
                    throw new IllegalArgumentException("Invalid guild for: " + value);
                }
                return category.getId();
            }
            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getCategory(db.getGuild(), input);
            }

            @Override
            public String help() {
                return "The name or id of the CATEGORY you would like " + CM.channel.close.current.cmd.toSlashMention() + " to move channels to";
            }
        },

//        INTERVIEW_CATEGORY(true, ALLIANCE_ID, CommandCategory.INTERNAL_AFFAIRS) {
//            @Override
//            public String validate(GuildDB db, String value) {
//                Guild guild = db.getGuild();
//                Category category = DiscordUtil.getCategory(guild, value);
//                if (category == null) {
//                    throw new IllegalArgumentException("Invalid category: " + value);
//                }
//                if (!category.getGuild().equals(guild)) {
//                    throw new IllegalArgumentException("Invalid guild for: " + value);
//                }
//                return category.getId();
//            }
//            @Override
//            public Object parse(GuildDB db, String input) {
//                return DiscordUtil.getCategory(db.getGuild(), input);
//            }
//
//            @Override
//            public String help() {
//                return "The name or id of the category you would like interviews created in";
//            }
//        },

//        BOUNTY_DEANONYMIZED_ALERT_CHANNEL(true, null, CommandCategory.MILCOM) {
//            @Override
//            public String validate(GuildDB db, String value) {
//                return Key.validateChannel(db, value);
//            }
//
//            @Override
//            public boolean allowed(GuildDB db) {
//                return false;
//            }
//
//            @Override
//            public Object parse(GuildDB db, String input) {
//                return DiscordUtil.getChannel(db.getGuild(), input);
//            }
//            @Override
//            public String help() {
//                return "The channel to receive alerts when a bounty is placed (deanonymized)";
//            }
//        },

        RESOURCE_REQUEST_CHANNEL(true, null, CommandCategory.ECON) {
            @Override
            public String validate(GuildDB db, String value) {
                Map<Long, MessageChannel> parsed = (Map<Long, MessageChannel>) parse(db, value);
                if (!parsed.containsKey(0L)) throw new IllegalArgumentException("You must specify a default channel (id 0)");
                return toString(parsed);

            }

            @Override
            public boolean allowed(GuildDB db) {
                return db.getOffshore() != null;
            }

            @Override
            public Object parse(GuildDB db, String input) {
                Map<Long, MessageChannel> parsed = new HashMap<>();
                for (String line : input.split("[\n;,]")) {
                    String[] split = line.split("[:=]", 2);
                    long id = split.length == 1 ? 0 : Long.parseLong(split[0]);
                    MessageChannel channel = DiscordUtil.getChannel(db.getGuild(), split[split.length - 1]);
                    if (channel != null) {
                        parsed.put((long) id, channel);
                    }
                }
                return parsed.isEmpty() ? null : parsed;
            }

            @Override
            public String toString(Object value) {
                Map<Long, MessageChannel> parsed = (Map<Long, MessageChannel>)value;
                List<String> mentions = new ArrayList<>();
                for (Map.Entry<Long, MessageChannel> entry : parsed.entrySet()) {
                    if (entry.getKey() == 0) mentions.add(entry.getValue().getAsMention());
                    else mentions.add(entry.getKey() + ":" + entry.getValue().getAsMention());
                }
                return StringMan.join(mentions, "\n");
            }
            @Override
            public String help() {
                return "The #channel for users to request resources in.\n" +
                        "For multiple alliances, use the form:```" +
                        "#defaultChannel\n" +
                        "alliance1:#channel\n" +
                        "```";
            }
        },

        GRANT_REQUEST_CHANNEL(true, MEMBER_CAN_WITHDRAW, CommandCategory.ECON) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public boolean allowed(GuildDB db) {
                return db.getOffshore() != null && Roles.ECON_GRANT_SELF.toRole(db.getGuild()) != null;
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The #channel for users to request grants in";
            }
        },

        TREATY_ALERTS(false, null, CommandCategory.FOREIGN_AFFAIRS) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }

            @Override
            public String help() {
                return "The #channel to receive alerts for treaty changes";
            }
        },

        ORBIS_LEADER_CHANGE_ALERT(false, null, CommandCategory.FOREIGN_AFFAIRS) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The #channel to receive alerts when a nation is promoted to leader in an alliance (top 80)";
            }
        },

        ORBIS_OFFICER_LEAVE_ALERTS(false, null, CommandCategory.FOREIGN_AFFAIRS) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The #channel to receive alerts when officers leave an alliance  (top 50)";
            }
        },

//        ORBIS_OFFICER_DELETE_ALERTS(false, null, CommandCategory.FOREIGN_AFFAIRS) {
//            @Override
//            public String validate(GuildDB db, String value) {
//                return Key.validateChannel(db, value);
//            }
//
//            @Override
//            public Object parse(GuildDB db, String input) {
//                return DiscordUtil.getChannel(db.getGuild(), input);
//            }
//
//            @Override
//            public String toString(Object value) {
//                return ((IMentionable) value).getAsMention();
//            }
//            @Override
//            public String help() {
//                return "The #channel to receive alerts when officers delete (top 50)";
//            }
//        },

        ORBIS_ALLIANCE_EXODUS_ALERTS(false, null, CommandCategory.FOREIGN_AFFAIRS) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The #channel to receive alerts when multiple 5+ members leave an alliance  (top 80)";
            }
        },

        ORBIS_OFFICER_MMR_CHANGE_ALERTS(false, null, CommandCategory.MILCOM) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }

            @Override
            public String help() {
                return "The #channel to receive alerts when gov members increase MMR (top 80)";
            }
        },

        ENEMY_MMR_CHANGE_ALERTS(false, null, CommandCategory.MILCOM) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }

            @Override
            public String help() {
                return "The #channel to receive alerts when a member in `enemies` coalitions changes MMR";
            }
        },

        ESCALATION_ALERTS(false, null, CommandCategory.MILCOM) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }

            @Override
            public String help() {
                return "The #channel to receive alerts for war escalation alerts in orbis";
            }
        },

        ACTIVITY_ALERTS(false, null, CommandCategory.MILCOM) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }

            @Override
            public String help() {
                return "The #channel to receive alerts for activity (e.g. pre blitz)";
            }
        },

        BANKER_WITHDRAW_LIMIT(true, null, CommandCategory.ECON) {
            public boolean allowed(GuildDB db) {
                return db.getOffshore() != null;
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return MathMan.parseDouble(input);
            }

            @Override
            public String toString(Object value) {
                return MathMan.format((Double) value);
            }

            @Override
            public String help() {
                return "The daily withdraw limit (from the offshore) of non admins";
            }
        },

        BANKER_WITHDRAW_LIMIT_INTERVAL(true, BANKER_WITHDRAW_LIMIT, CommandCategory.ECON) {
            public boolean allowed(GuildDB db) {
                return db.getOffshore() != null;
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return TimeUtil.timeToSec(input) * 1000L;
            }

            @Override
            public String toString(Object value) {
                return TimeUtil.secToTime(TimeUnit.MILLISECONDS, (Long) value);
            }

            @Override
            public String help() {
                return "The time period the withdraw limit applies to (defaults to 1 day)";
            }
        },

        TAX_BASE(true, ALLIANCE_ID, CommandCategory.ECON) {
            @Override
            public String validate(GuildDB db, String value) {
                if (parse(null, value) != null) {
                    return value;
                }
                return null;
            }

            @Override
            public int[] parse(GuildDB db, String value) {
                String[] split = value.split("/");
                if (split.length != 2) {
                    throw new IllegalArgumentException("Invalid tax rate: `" + value + "`. Must be in the form 25/25.");
                }
                int moneyTax = Integer.parseInt(split[0]);
                int rssTax = Integer.parseInt(split[1]);
                if (moneyTax < 0 || moneyTax > 100 || rssTax < 0 || rssTax > 100) {
                    throw new IllegalArgumentException("Tax rates must be between 0 and 100");
                }
                return new int[]{moneyTax, rssTax};
            }

            @Override
            public String toString(Object value) {
                int[] parsed = (int[]) value;
                return parsed[0] + "/" + parsed[1];
            }

            @Override
            public String help() {
                return "The internal tax amount ($/rss) in the format e.g. `25/25` to be excluded in deposits.\n" +
                        "Defaults to `100/100` (i.e. no taxes are included in depos).\n" +
                        "Setting is retroactive. See also: " + CM.nation.set.taxinternal.cmd.toSlashMention() + "";
            }
        },

        MEMBER_AUDIT_ALERTS(true, ALLIANCE_ID, CommandCategory.INTERNAL_AFFAIRS) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public boolean allowed(GuildDB db) {
                return true;
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "The #channel to ping members about audits";
            }
        },

        DISABLED_MEMBER_AUDITS(true, MEMBER_AUDIT_ALERTS, CommandCategory.INTERNAL_AFFAIRS) {
            @Override
            public String validate(GuildDB db, String value) {
                return toString(parse(db, value));
            }

            @Override
            public boolean allowed(GuildDB db) {
                return MEMBER_AUDIT_ALERTS.allowed(db);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return new HashSet<>(BindingHelper.emumList(AuditType.class, input));
            }

            @Override
            public String toString(Object value) {
                return StringMan.join((Collection) value, ",");
            }
            @Override
            public String help() {
                return "A comma separated list of audit types to ignore: " + StringMan.getString(AuditType.values());
            }
        },

//        BLOCKADE_ALERTS(true, null, CommandCategory.MILCOM) {
//            @Override
//            public String validate(GuildDB db, String value) {
//                return Key.validateChannel(db, value);
//            }
//
//            @Override
//            public Object parse(GuildDB db, String input) {
//                return DiscordUtil.getChannel(db.getGuild(), input);
//            }
//            @Override
//            public String help() {
//                return "The channel to post counter alerts in";
//            }
//        },
//
//        RAID_INFO_CHANNEL(false, ALLIANCE_ID, CommandCategory.MILCOM) {
//            @Override
//            public String validate(GuildDB db, String value) {
//                return Key.validateChannel(db, value);
//            }
//
//            @Override
//            public boolean allowed(GuildDB db) {
//                return db.isWhitelisted();
//            }
//
//            @Override
//            public Object parse(GuildDB db, String input) {
//                return DiscordUtil.getChannel(db.getGuild(), input);
//            }
//            @Override
//            public String help() {
//                return "A channel for members with general server info";
//            }
//        },

        GRANT_ALERT_CHANNEL(false, ALLIANCE_ID, CommandCategory.ECON) {
            @Override
            public String validate(GuildDB db, String value) {
                return Key.validateChannel(db, value);
            }

            @Override
            public boolean allowed(GuildDB db) {
                return db.isWhitelisted();
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return DiscordUtil.getChannel(db.getGuild(), input);
            }

            @Override
            public String toString(Object value) {
                return ((IMentionable) value).getAsMention();
            }
            @Override
            public String help() {
                return "A #channel to alert when a member's timer expires";
            }
        },

        WARCHEST_PER_CITY(true, ALLIANCE_ID, CommandCategory.INTERNAL_AFFAIRS) {
            @Override
            public String validate(GuildDB db, String value) {
                Map<ResourceType, Double> rss = PnwUtil.parseResources(value);
                if (rss == null) {
                    throw new IllegalArgumentException("Invalid resources: `" + value + "`");
                }
                double[] arr = PnwUtil.resourcesToArray(rss);

                byte[] bytes = ArrayUtil.toByteArray(arr);

                return new String(bytes, StandardCharsets.ISO_8859_1);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return PnwUtil.resourcesToMap(ArrayUtil.toDoubleArray(input.getBytes(StandardCharsets.ISO_8859_1)));
            }
            @Override
            public String help() {
                return "Amount of warchest to recommend per city in form `{steel=1234,aluminum=5678,gasoline=69,munitions=420}`";
            }
        },

        REWARD_REFERRAL(false, MEMBER_LEAVE_ALERT_CHANNEL, CommandCategory.ECON) {
            @Override
            public boolean allowed(GuildDB db) {
                return db.isWhitelisted();
            }

            @Override
            public String validate(GuildDB db, String value) {
                return validateResources(db, value);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return PnwUtil.resourcesToMap(ArrayUtil.toDoubleArray(input.getBytes(StandardCharsets.ISO_8859_1)));
            }
            @Override
            public String help() {
                return "The reward (resources) for referring a nation in the form `{food=1,money=3.2}";
            }
        },

        REWARD_MENTOR(false, Key.INTERVIEW_PENDING_ALERTS, CommandCategory.ECON) {
            @Override
            public boolean allowed(GuildDB db) {
                return db.isWhitelisted();
            }

            @Override
            public String validate(GuildDB db, String value) {
                return validateResources(db, value);
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return PnwUtil.resourcesToMap(ArrayUtil.toDoubleArray(input.getBytes(StandardCharsets.ISO_8859_1)));
            }
            @Override
            public String help() {
                return "The reward (resources) for mentoring a nation in the form `{food=1,money=3.2}";
            }
        },

        PUBLIC_OFFSHORING(false, Key.ALLIANCE_ID, CommandCategory.ECON) {
            @Override
            public boolean allowed(GuildDB db) {
                Map.Entry<GuildDB, Integer> offshoreDb = db.getOffshoreDB();
                return offshoreDb != null && offshoreDb.getKey().getIdLong() == db.getIdLong();
            }

            @Override
            public String validate(GuildDB db, String value) {
                return Boolean.valueOf(value) + "";
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return Boolean.parseBoolean(input);
            }
            @Override
            public String help() {
                return "Public offshores allow other alliances to see and register to use this alliance as an offshore without approval";
            }
        },

        CHANNEL_BLACKLIST(false, null, CommandCategory.GUILD_MANAGEMENT) {
            @Override
            public String validate(GuildDB db, String value) {
                Set<MessageChannel> channels = (Set<MessageChannel>) parse(db, value);
                return channels.stream().map(f -> f.getAsMention()).collect(Collectors.joining("\n"));
            }

            @Override
            public Object parse(GuildDB db, String input) {
                Set<MessageChannel> channels = new LinkedHashSet<>();
                for (String channelStr : input.split("[ ,\n]")) {
                    MessageChannel channel = DiscordUtil.getChannel(db.getGuild(), channelStr);
                    if (channel == null) throw new IllegalArgumentException("Invalid channel: `" + channelStr + "`");
                    channels.add(channel);
                }
                return channels;
            }

            @Override
            public String help() {
                return "List of channels to disable Locutus in";
            }
        },

        CHANNEL_WHITELIST(false, null, CommandCategory.GUILD_MANAGEMENT) {
            @Override
            public String validate(GuildDB db, String value) {
                Set<MessageChannel> channels = (Set<MessageChannel>) parse(db, value);
                return channels.stream().map(f -> f.getAsMention()).collect(Collectors.joining("\n"));
            }

            @Override
            public Object parse(GuildDB db, String input) {
                return CHANNEL_BLACKLIST.parse(db, input);
            }

            @Override
            public String help() {
                return "List of channels to whitelist Locutus in";
            }
        },

        MEMBER_REWARDS(false, ALLIANCE_ID, CommandCategory.ECON) {

            @Override
            public Object parse(GuildDB db, String input) {
                Map<NationFilterString, double[]> result = new LinkedHashMap<>();
                for (String line : input.trim().split("[\n|;]")) {
                    String[] split = line.split("[:=]", 2);
                    String filterStr = split[0];
                    boolean containsNation = false;
                    for (String arg : filterStr.split(",")) {
                        if (!arg.startsWith("#")) containsNation = true;
                    }
                    if (!containsNation) filterStr += ",*";
                    DiscordUtil.parseNations(db.getGuild(), filterStr); // validate
                    NationFilterString filter = new NationFilterString(filterStr, db.getGuild());

                    double[] resources = PnwUtil.resourcesToArray(PnwUtil.parseResources(split[1]));
                    result.put(filter, resources);
                }
                return result;
            }

            @Override
            public <T> boolean hasPermission(GuildDB db, User author, T value) {
                return super.hasPermission(db, author, value) && db.getOrNull(Key.RESOURCE_REQUEST_CHANNEL) != null;
            }

            @Override
            public String toString(Object value) {
                Map<NationFilterString, double[]> filterToResources = (Map<NationFilterString, double[]>) value;
                StringBuilder result = new StringBuilder();
                for (Map.Entry<NationFilterString, double[]> entry : filterToResources.entrySet()) {
                    result.append(entry.getKey().getFilter() + ":" + PnwUtil.resourcesToString(entry.getValue()) + "\n");
                }
                return result.toString().trim();
            }

            @Override
            public String help() {
                return "A map of rewards to give members e.g.\n" +
                        "```\n" +
                        "#wars_won>50:{food=5,munitions=1}\n" +
                        "#cities>5,#correctalliancemmr=1:{coal=1}\n" +
                        "" +
                        "```";
            }
        }

//        REWARD_ECON(false, Key.GRANT_REQUEST_CHANNEL, CommandCategory.ECON) {
//            @Override
//            public boolean allowed(GuildDB db) {
//                return db.isWhitelisted();
//            }
//
//            @Override
//            public String validate(GuildDB db, String value) {
//                return validateResources(db, value);
//            }
//
//            @Override
//            public Object parse(GuildDB db, String input) {
//                return PnwUtil.resourcesToMap(ArrayUtil.toDoubleArray(input.getBytes()));
//            }
//            @Override
//            public String help() {
//                return "The reward (resources) for completing a nation grant `{food=1,money=3.2}";
//            }
//        },


        ;

        public final Key requires;
        public final boolean requiresSetup;
        public final CommandCategory category;

        Key() {
            this(true, null, null);
        }

        Key(boolean requiresSetup, Key dependsOn, CommandCategory perm) {
            this.requiresSetup = requiresSetup;
            this.requires = dependsOn;
            this.category = perm;
        }

        public <T> boolean hasPermission(GuildDB db, User author, T value) {
            return Roles.ADMIN.has(author, db.getGuild());
        }

        public boolean allowed(GuildDB db) {
            return true;
        }

        public String toString(Object value) {
            return value == null ? "NULL" : StringMan.getString(value);
        }

        public String validate(GuildDB db, String value) {
            parse(db, value);
            return value;
        };

        public Object parse(GuildDB db, String input) {
            return input;
        }

        public String help() {
            return "";
        }

        private static String validateGuild(String value) {
            Long id = null;
            if (value.toLowerCase().contains("/alliance/")) {
                Integer aaId = PnwUtil.parseAllianceId(value);
                if (aaId != null) {
                    GuildDB db = Locutus.imp().getGuildDBByAA(aaId);
                    if (db == null) throw new IllegalArgumentException("No Locutus on " + value);
                    id = db.getGuild().getIdLong();
                }
            }
            else if (!MathMan.isInteger(value)) {
                for (Guild guild : Locutus.imp().getDiscordApi().getGuilds()) {
                    if (guild.getName().equalsIgnoreCase(value)) {
                        id = guild.getIdLong();
                        break;
                    }
                }
            } else {
                Guild guild = Locutus.imp().getDiscordApi().getGuildById(Long.parseLong(value));
                if (guild == null) throw new IllegalArgumentException("Locutus is not in: " + value);
                id = guild.getIdLong();
            }
            if (id == null) throw new IllegalArgumentException("Invalid guild id: " + value);
            return id + "";
        }

        private static String validateChannel(GuildDB db, String value) {
            Guild guild = db.getGuild();
            MessageChannel channel = DiscordUtil.getChannel(guild, value);
            if (channel == null) {
                throw new IllegalArgumentException("Invalid channel: " + value);
            }
//            channel = Locutus.imp().getDiscordApi().getGuildChannelById(channel.getIdLong());
//            if (channel == null) {
//                throw new IllegalArgumentException("Invalid guild text channel: " + value);
//            }
            return channel.getId();
        }

        private static String validateResources(GuildDB db, String value) {
            Map<ResourceType, Double> rss = PnwUtil.parseResources(value);
            if (rss == null) {
                throw new IllegalArgumentException("Invalid resources: `" + value + "`");
            }
            double[] arr = PnwUtil.resourcesToArray(rss);
            byte[] bytes = ArrayUtil.toByteArray(arr);
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    public boolean isEnemyAlliance(int allianceId) {
        return getCoalitionRaw(Coalition.ENEMIES).contains((long) allianceId);
    }

    public Map.Entry<GuildDB, Integer> getOffshoreDB() {
        Set<Integer> aaIds = getAllianceIds();

        Set<Integer> offshores = getCoalition(Coalition.OFFSHORE);
        for (int offshoreId : offshores) {
            DBAlliance aa = DBAlliance.get(offshoreId);
            if (aa == null) continue;

            GuildDB otherDb = aa.getGuildDB();
            if (otherDb == null) continue;
            Set<Long> offshoring = otherDb.getCoalitionRaw(Coalition.OFFSHORING);
            if (!offshoring.contains((long) offshoreId)) {
                return null;
            }

            if (aaIds.isEmpty()) {
                if (offshoring.contains(getIdLong())) {
                    return new AbstractMap.SimpleEntry<>(otherDb, offshoreId);
                }
            } else {
                for (int aaId : aaIds) {
                    if (offshoring.contains((long) aaId)) {
                        return new AbstractMap.SimpleEntry<>(otherDb, offshoreId);
                    }
                }
            }
        }
        return null;
    }

    public OffshoreInstance getOffshore() {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            return delegate.getOffshore();
        }
        Map.Entry<GuildDB, Integer> otherDbAA = getOffshoreDB();

        if (otherDbAA == null) return null;
        GuildDB otherDb = otherDbAA.getKey();
        int aaId = otherDbAA.getValue();
        DBAlliance aa = DBAlliance.get(aaId);
        if (aa == null) return null;
        return aa.getBank();
    }

    public enum AutoNickOption {
        FALSE,
        LEADER,
        NATION,
        DISCORD
    }

    public enum AutoRoleOption {
        FALSE,
        ALL,
        ALLIES,
    }

    public Function<DBNation, Boolean> getCanRaid() {
        Integer topX = getOrNull(Key.DO_NOT_RAID_TOP_X);
        if (topX == null) topX = 0;
        return getCanRaid(topX, true);
    }

    public Function<DBNation, Boolean> getCanRaid(int topX, boolean checkTreaties) {
        Set<Integer> dnr = new HashSet<>(getCoalition(Coalition.ALLIES));
        dnr.addAll(getCoalition("dnr"));
        Set<Integer> dnr_active = new HashSet<>(getCoalition("dnr_active"));
        Set<Integer> dnr_member = new HashSet<>(getCoalition("dnr_member"));
        Set<Integer> can_raid = new HashSet<>(getCoalition(Coalition.CAN_RAID));
        Set<Integer> can_raid_inactive = new HashSet<>(getCoalition("can_raid_inactive"));
        Map<Integer, Long> dnr_timediff_member = new HashMap<>();
        Map<Integer, Long> dnr_timediff_app = new HashMap<>();

        if (checkTreaties) {
            for (int allianceId : getAllianceIds()) {
                Map<Integer, Treaty> treaties = Locutus.imp().getNationDB().getTreaties(allianceId);
                dnr.addAll(treaties.keySet());
            }
        }

        if (topX > 0) {
            Map<Integer, Double> aas = new RankBuilder<>(Locutus.imp().getNationDB().getNations().values()).group(DBNation::getAlliance_id).sumValues(DBNation::getScore).sort().get();
            for (Map.Entry<Integer, Double> entry : aas.entrySet()) {
                if (entry.getKey() == 0) continue;
                if (topX-- <= 0) break;
                int allianceId = entry.getKey();
                Map<Integer, Treaty> treaties = Locutus.imp().getNationDB().getTreaties(allianceId);
                for (Map.Entry<Integer, Treaty> aaTreatyEntry : treaties.entrySet()) {
                    switch (aaTreatyEntry.getValue().getType()) {
                        case MDP:
                        case MDOAP:
                        case PROTECTORATE:
                            dnr_member.add(aaTreatyEntry.getKey());
                    }
                }
                dnr_member.add(allianceId);
            }
        }

        for (Map.Entry<String, Set<Integer>> entry : getCoalitions().entrySet()) {
            String name = entry.getKey().toLowerCase();
            if (!name.startsWith("dnr_")) continue;

            String[] split = name.split("_");
            if (split.length < 2 || split[split.length - 1].isEmpty()) continue;
            String timeStr = split[split.length - 1];
            if (!Character.isDigit(timeStr.charAt(0))) continue;

            long time = TimeUtil.timeToSec(timeStr) * 1000L;
            for (Integer aaId : entry.getValue()) {
                if (split.length == 3 && split[1].equalsIgnoreCase("member")) {
                    dnr_timediff_member.put(aaId, time);
                } else if (true || split[1].equalsIgnoreCase("applicant")) {
                    dnr_timediff_app.put(aaId, time);
                }

            }
        }
        Set<Integer> enemies = getCoalition("enemies");
        enemies.addAll(getCoalition(Coalition.CAN_RAID));

        Function<DBNation, Boolean> canRaid = new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation enemy) {
                if (enemy.getAlliance_id() == 0) return true;
                if (can_raid.contains(enemy.getAlliance_id())) return true;
                if (can_raid_inactive.contains(enemy.getAlliance_id()) && enemy.getActive_m() > 10000) return true;
                if (enemies.contains(enemy.getAlliance_id())) return true;
                if (dnr.contains(enemy.getAlliance_id())) return false;
                if (enemy.getActive_m() < 10000 && dnr_active.contains(enemy.getAlliance_id())) return false;
                if ((enemy.getActive_m() < 10000 || enemy.getPosition() > 1) && dnr_member.contains(enemy.getAlliance_id())) return false;

                long requiredInactive = -1;

                {
                    Long timeDiff = dnr_timediff_app.get(enemy.getAlliance_id());
                    if (timeDiff != null) {
                        requiredInactive = enemy.getPosition() > 1 ? Long.MAX_VALUE : timeDiff;
                    }
                }
                if (enemy.getPosition() > 1) {
                    Long timeDiff = dnr_timediff_member.get(enemy.getAlliance_id());
                    if (timeDiff != null) {
                        requiredInactive = timeDiff;
                    }
                }

                long msInactive = enemy.getActive_m() * 60 * 1000L;

                return (msInactive > requiredInactive);
            }
        };

        return canRaid;
    }

    public Map<String, String> getCopyPastas(@Nullable Member memberOrNull) {
        Map<String, String> options = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : getInfoMap().entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("copypasta.")) continue;
            if (memberOrNull != null && !getMissingCopypastaPerms(key, memberOrNull).isEmpty()) continue;
            options.put(key.substring("copypasta.".length()), entry.getValue());
        }
        return options;
    }

    /**
     * Returns the missing copypasta permissions for the given member.
     * @param key
     * @param member
     * @return
     */
    public Set<String> getMissingCopypastaPerms(String key, Member member) {
        String[] split = key.split("\\.");
        Set<String> noRoles = new HashSet<>();
        if (split.length > 1) {
            for (int i = 0; i < split.length - 1; i++) {
                String roleName = split[i];
                if (roleName.equalsIgnoreCase("copypasta")) continue;

                Roles role = Roles.parse(roleName);
                Role discRole = DiscordUtil.getRole(member.getGuild(), roleName);
                if ((role != null && role.has(member)) || (discRole != null && member.getRoles().contains(discRole))) {
                    return Collections.emptySet();
                }
                if (role != null) noRoles.add(role.toString());
                if (discRole != null) noRoles.add(discRole.getName());
            }
        }
        return noRoles;
    }

    public Map<String, String> getInfoMap() {
        initInfo();
        return info;
    }

    @Deprecated
    public String getInfo(Key key, boolean allowDelegate) {
        if (key == Key.ALLIANCE_ID) {
            String result = getInfo(key.name(), false);
        }
        return getInfo(key.name(), allowDelegate);
    }

    public void setInfo(Key key, String value) {
        synchronized (infoParsed) {
            setInfo(key.name(), key.validate(this, value));
            infoParsed.remove(key);
        }
    }

    private Map<String, String> info;
    private final Map<Key, Object> infoParsed = new HashMap<>();
    private final Object nullInstance = new Object();

    public MessageChannel getResourceChannel(Integer allianceId) {
        Map<Long, MessageChannel> channels = getOrNull(Key.RESOURCE_REQUEST_CHANNEL);
        if (channels == null) return null;
        MessageChannel channel = channels.get(allianceId.longValue());
        if (channel == null) channel = channels.get(0L);
        return channel;
    }

    public String getInfo(String key, boolean allowDelegate) {
        if (info == null) {
            initInfo();
        }
        String value = info.get(key.toLowerCase());
        if (value == null && allowDelegate) {
            GuildDB delegate = getOrNull(Key.DELEGATE_SERVER, false);
            if (delegate != null && delegate.getIdLong() != getIdLong()) {
                value = delegate.getInfo(key, false);
            }
        }
        return value;
    }

    public void deleteInfo(Key key) {
        synchronized (infoParsed) {
            deleteInfo(key.name());
            infoParsed.remove(key);
        }
    }

    public void deleteInfo(String key) {
        info.remove(key.toLowerCase());
        update("DELETE FROM `INFO` where `key` = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, key.toLowerCase());
        });
    }

    public void setInfo(String key, String value) {
        checkNotNull(key);
        checkNotNull(value);
        initInfo();
        synchronized (this) {
            update("INSERT OR REPLACE INTO `INFO`(`key`, `value`) VALUES(?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
                stmt.setString(1, key.toLowerCase());
                stmt.setString(2, value);
            });
            info.put(key.toLowerCase(), value);
        }
    }

    private synchronized void initInfo() {
        if (info == null) {
            ConcurrentHashMap<String, String> tmp = new ConcurrentHashMap<>();
            try (PreparedStatement stmt = prepareQuery("select * FROM INFO")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String key = rs.getString("key");
                        String value = rs.getString("value");
                        tmp.put(key, value);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            this.info = tmp;
        }
    }

    private Map<Class, Integer> permissions;

    public int getPermission(Class perm) {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            return delegate.getPermission(perm);
        }
        if (isWhitelisted()) {
            return 1;
        }
        if (permissions == null) initPerms();
        return permissions.getOrDefault(perm, 0);
    }

    public Map<Class, Integer> getPermissions() {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            return delegate.getPermissions();
        }
        if (permissions == null) initPerms();
        return this.permissions;
    }

    private synchronized void initPerms() {
        if (permissions == null) {
            ConcurrentHashMap<Class, Integer> tmp = new ConcurrentHashMap<>();
            Map<String, Class> cmdMap = new HashMap<>();
            for (Command cmd : Locutus.imp().getCommandManager().getCommandMap().values()) {
                cmdMap.put(cmd.getClass().getSimpleName().toLowerCase(), cmd.getClass());
            }

            try (PreparedStatement stmt = prepareQuery("select * FROM PERMISSIONS")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String clazzName = rs.getString("permission");
                        Class<?> clazz = cmdMap.get(clazzName.toLowerCase());
                        if (clazz == null) {
                            System.out.println("!!Invalid perm: " + clazzName);
                            continue;
                        }
                        int value = rs.getInt("value");
                        tmp.put(clazz, value);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            this.permissions = tmp;
        }
    }

    public void setPermission(Class perm, int value) {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            delegate.setPermission(perm, value);
            return;
        }
        checkNotNull(perm);
        initPerms();
        synchronized (this) {
            update("INSERT OR REPLACE INTO `PERMISSIONS`(`permission`, `value`) VALUES(?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
                stmt.setString(1, perm.getSimpleName());
                stmt.setInt(2, value);
            });
            permissions.put(perm, value);
        }
    }

    public void addBuild(String category, int min, int max, String build) {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            delegate.addBuild(category, min, max, build);
            return;
        }

        update("INSERT OR REPLACE INTO `BUILDS`(`category`, `min`, `max`, `build`) VALUES(?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, category);
            stmt.setInt(2, min);
            stmt.setInt(3, max);
            stmt.setString(4, build);
        });
    }

    public void removeBuild(String category, int min) {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) {
            delegate.removeBuild(category, min);
            return;
        }

        update("DELETE FROM `BUILDS` WHERE `category` = ? AND `min` = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, category);
            stmt.setInt(2, min);
        });
    }

    public Map<String, List<CityBuildRange>> getBuilds() {
        GuildDB delegate = getDelegateServer();
        if (delegate != null) return delegate.getBuilds();

        Map<String, List<CityBuildRange>> ranges = new HashMap<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM BUILDS")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String category = rs.getString("category");
                    int min = rs.getInt("min");
                    int max = rs.getInt("max");
                    String buildJson = rs.getString("build");

                    CityBuildRange build = new CityBuildRange(min, max, buildJson);
                    List<CityBuildRange> list = ranges.computeIfAbsent(category, k -> new ArrayList<>());
                    list.add(build);
                }
            }
            return ranges;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void addCoalition(long allianceId, Coalition coalition) {
        addCoalition(allianceId, coalition.name().toLowerCase());
    }

    public void addCoalition(long allianceId, String coalition) {
        GuildDB faServer = getOrNull(Key.FA_SERVER);
        if (faServer != null && faServer.getIdLong() != getIdLong()) {
            faServer.addCoalition(allianceId, coalition);
            return;
        }
        synchronized (OffshoreInstance.BANK_LOCK) {
            loadCoalitions();
            if (coalitions == null) coalitions = new ConcurrentHashMap<>();
            coalitions.computeIfAbsent(coalition.toLowerCase(), f -> new LinkedHashSet<>()).add((long) allianceId);

            update("INSERT OR IGNORE INTO `COALITIONS`(`alliance_id`, `coalition`) VALUES(?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
                stmt.setLong(1, allianceId);
                stmt.setString(2, coalition.toLowerCase());
            });
        }
    }

    public void removeCoalition(long allianceId, Coalition coalition) {
        removeCoalition(allianceId, coalition.name().toLowerCase());
    }

    public void removeCoalition(long allianceId, String coalition) {
        GuildDB faServer = getOrNull(Key.FA_SERVER);
        if (faServer != null && faServer.getIdLong() != getIdLong()) {
            faServer.removeCoalition(allianceId, coalition);
            return;
        }
        synchronized (OffshoreInstance.BANK_LOCK) {
            Set<Long> set = coalitions.getOrDefault(coalition, Collections.emptySet());
            if (set != null) set.remove(allianceId);
            update("DELETE FROM `COALITIONS` WHERE `alliance_id` = ? AND LOWER(coalition) = LOWER(?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
                stmt.setLong(1, allianceId);
                stmt.setString(2, coalition.toLowerCase());
            });
        }
    }

    public Set<Integer> getAllies() {
        return getAllies(false);
    }

    public enum UnmaskedReason {
        NOT_REGISTERED,
        NOT_IN_ALLIANCE,
        APPLICANT,
        INACTIVE
    }

    public Map<Member, UnmaskedReason> getMaskedNonMembers() {
        if (!hasAlliance()) return Collections.emptyMap();

        List<Role> roles = new ArrayList<>();
        roles.add(Roles.MEMBER.toRole(this));
        roles.add(Roles.ECON_WITHDRAW_SELF.toRole(this));
        roles.removeIf(Objects::isNull);

        Map<Member, UnmaskedReason> result = new HashMap<>();

        Set<Integer> allowedAAs = new HashSet<>(getAllianceIds());
        allowedAAs.addAll(getCoalition(Coalition.OFFSHORE));
        for (Role role : roles) {
            List<Member> members = guild.getMembersWithRoles(role);
            for (Member member : members) {
                DBNation nation = DiscordUtil.getNation(member.getUser());
                if (nation == null) result.put(member, UnmaskedReason.NOT_REGISTERED);
                else if (!allowedAAs.contains(nation.getAlliance_id())) result.put(member, UnmaskedReason.NOT_IN_ALLIANCE);
                else if (nation.getPosition() <= 1) result.put(member, UnmaskedReason.APPLICANT);
                else if (nation.getActive_m() > 20000) result.put(member, UnmaskedReason.INACTIVE);
            }
        }
        return result;
    }

    public Set<Integer> getAllies(boolean fetchTreaties) {
        Set<Integer> allies = getCoalition("allies");
        if (getOrNull(GuildDB.Key.WAR_ALERT_FOR_OFFSHORES) != Boolean.FALSE) {
            allies.addAll(getCoalition("offshore"));
        }
        Set<Integer> aaIds = getAllianceIds();
        if (!aaIds.isEmpty()) {
            allies.addAll(aaIds);
            if (fetchTreaties) {
                for (int allianceId : aaIds) {
                    Map<Integer, Treaty> treaties = Locutus.imp().getNationDB().getTreaties(allianceId);
                    for (Map.Entry<Integer, Treaty> entry : treaties.entrySet()) {
                        switch (entry.getValue().getType()) {
                            case MDP:
                            case MDOAP:
                            case ODP:
                            case ODOAP:
                            case PROTECTORATE:
                                allies.add(entry.getKey());
                        }
                    }
                }
            }
        }
        return allies;
    }

    private Map<String, Set<Long>> coalitions = null;

    private Map<String, Set<Integer>> coalitionToAlliances(Map<String, Set<Long>> input) {
        Map<String, Set<Integer>> result = new HashMap<>();
        for (Map.Entry<String, Set<Long>> entry : input.entrySet()) {
            Set<Integer> aaIds = new LinkedHashSet<>();
            for (Long id : entry.getValue()) {
                if (id > Integer.MAX_VALUE) {
                    GuildDB db = Locutus.imp().getGuildDB(id);
                    if (db != null) {
                        aaIds.addAll(db.getAllianceIds());
                    }
                } else {
                    aaIds.add(id.intValue());
                }
            }
            result.put(entry.getKey(), aaIds);
        }
        return result;
    }

    public Map<String, Set<Long>> getCoalitionsRaw() {
        GuildDB faServer = getOrNull(Key.FA_SERVER);
        if (faServer != null && faServer.getIdLong() != getIdLong()) return faServer.getCoalitionsRaw();
        loadCoalitions();
        return Collections.unmodifiableMap(coalitions);
    }

    public void loadCoalitions() {
        if (coalitions == null) {
            synchronized (this) {
                if (coalitions == null) {
                    coalitions = new ConcurrentHashMap<>();
                    try (PreparedStatement stmt = prepareQuery("select * FROM COALITIONS")) {
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                long allianceId = rs.getLong("alliance_id");
                                String coalition = rs.getString("coalition").toLowerCase();
                                Set<Long> set = coalitions.computeIfAbsent(coalition, k -> new LinkedHashSet<>());
                                set.add(allianceId);
                            }
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    public Map<String, Set<Integer>> getCoalitions() {
        GuildDB faServer = getOrNull(Key.FA_SERVER);
        if (faServer != null) return faServer.getCoalitions();
        loadCoalitions();
        return coalitionToAlliances(coalitions);
    }

    public Set<Integer> getCoalition(Coalition coalition) {
        return getCoalition(coalition.name().toLowerCase());
    }

    public Set<Long> getCoalitionRaw(Coalition coalition) {
        return getCoalitionRaw(coalition.name().toLowerCase());
    }

    public Set<Long> getTrackedBanks() {
        Set<Long> tracked = new LinkedHashSet<>(getCoalitionRaw(Coalition.OFFSHORE));
        tracked.add(getGuild().getIdLong());
        tracked.addAll(getCoalitionRaw(Coalition.TRACK_DEPOSITS));
        for (Integer id : getAllianceIds()) tracked.add(id.longValue());
        tracked = PnwUtil.expandCoalition(tracked);
        return tracked;
    }
    public Set<Long> getCoalitionRaw(String coalition) {
        GuildDB faServer = getOrNull(Key.FA_SERVER);
        if (faServer != null && faServer.getIdLong() != getIdLong()) return faServer.getCoalitionRaw(coalition);
        synchronized (this) {
            loadCoalitions();
            Set<Long> raw = coalitions.getOrDefault(coalition, Collections.emptySet());
            return Collections.unmodifiableSet(raw);
        }
    }

    public Set<Integer> getCoalition(String coalition) {
        coalition = coalition.toLowerCase();
        Set<Integer> result = getCoalitions().get(coalition);
        if (result == null && Coalition.getOrNull(coalition) == null) {
            Coalition namedCoal = Coalition.getOrNull(coalition);
            if (namedCoal == null) {
                GuildDB locutusStats = Locutus.imp().getRootCoalitionServer();
                if (locutusStats != null) {
                    result = locutusStats.getCoalitions().get(coalition);
                }
            }
        }
        if (result == null) result = Collections.emptySet();
        return new LinkedHashSet<>(result);
    }

    public Set<Integer> getAllianceIds() {
        return getAllianceIds(true);
    }

    public boolean isAllianceId(int id) {
        Set<Integer> aaIds = getOrNull(Key.ALLIANCE_ID);
        return aaIds != null && aaIds.contains(id);
    }

    /**
     * @param onlyVerified - If only verified alliances are returned
     * @return the alliance ids associated with the guild
     */
    public Set<Integer> getAllianceIds(boolean onlyVerified) {
        Set<Integer> aaIds = getOrNull(Key.ALLIANCE_ID);
        if (onlyVerified) {
            if (aaIds == null) return Collections.emptySet();
            return aaIds;
        }
        Set<Integer> offshore = getCoalition(Coalition.OFFSHORE);
        if (offshore.isEmpty()) {
            if (aaIds == null) return Collections.emptySet();
            return aaIds;
        }
        Set<Integer> alliances = new LinkedHashSet<>();
        for (int offshoreId : offshore) {
            GuildDB otherGuild = Locutus.imp().getGuildDBByAA(offshoreId);
            if (otherGuild == null || otherGuild == this) {
                alliances.add(offshoreId);
            }
        }
        if (aaIds != null) {
            alliances.addAll(aaIds);
        }
        return alliances;
    }

//    public void removeCoalition(int allianceId) {
//        addTask(TaskType.COALITION, allianceId, new UniqueStatement(allianceId) {
//            @Override
//            public PreparedStatement get() throws SQLException {
//                return getConnection().prepareStatement("DELETE FROM `COALITIONS` WHERE `alliance_id` = ?");
//            }
//
//            @Override
//            public void set(PreparedStatement stmt) throws SQLException {
//                stmt.setInt(1, allianceId);
//            }
//        });
//    }

    public Set<BeigeReason> getAllowedBeigeReasons(DBNation defender) {
        Map<CityRanges, Set<BeigeReason>> allowedReasonsMap = getOrNull(GuildDB.Key.ALLOWED_BEIGE_REASONS);
        Set<BeigeReason> allowedReasons = null;
        if (allowedReasonsMap != null) {
            for (Map.Entry<CityRanges, Set<BeigeReason>> entry : allowedReasonsMap.entrySet()) {
                if (entry.getKey().contains(defender.getCities())) {
                    allowedReasons = entry.getValue();
                }
            }
        }
        if (allowedReasons == null) {
            allowedReasons = new HashSet<>(Arrays.asList(BeigeReason.values()));
            allowedReasons.remove(BeigeReason.NO_REASON);
            allowedReasons.remove(BeigeReason.OFFENSIVE_WAR);
            allowedReasons.remove(BeigeReason.NO_ENEMY_OFFENSIVE_WARS);
            allowedReasons.remove(BeigeReason.UNDER_C10_SLOG);
        }
        return allowedReasons;
    }

    public void removeCoalition(String coalition) {
        GuildDB faServer = getOrNull(Key.FA_SERVER);
        if (faServer != null && faServer.getIdLong() != getIdLong()) {
            faServer.removeCoalition(coalition);
            return;
        }
        synchronized (this) {
            if (coalitions != null) {
                coalitions.remove(coalition.toLowerCase());
            }
        }
        update("DELETE FROM `COALITIONS` WHERE LOWER(coalition) = LOWER(?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, coalition);
        });
    }

    private volatile boolean cachedRoleAliases = false;
    private final Map<Roles, Map<Long, Long>> roleToAccountToDiscord;

    public void addRole(Roles locutusRole, Role discordRole, long allianceId) {
        addRole(locutusRole, discordRole.getIdLong(), allianceId);
    }

    public void addRole(Roles locutusRole, long discordRole, long allianceId) {
        deleteRole(locutusRole, allianceId, false);
        roleToAccountToDiscord.computeIfAbsent(locutusRole, f -> new ConcurrentHashMap<>()).put(allianceId, discordRole);
        update("INSERT OR REPLACE INTO `ROLES2`(`role`, `alias`, `alliance`) VALUES(?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, locutusRole.getId());
            stmt.setLong(2, discordRole);
            stmt.setLong(3, allianceId);
        });
    }
    public void deleteRole(Roles role, long alliance) {
        deleteRole(role, alliance, true);
    }
    public void deleteRole(Roles role, long alliance, boolean updateCache) {
        if (updateCache) {
            Map<Long, Long> existing = roleToAccountToDiscord.get(role);
            if (existing != null) {
                existing.remove(alliance);
            }
        }
        update("DELETE FROM `ROLES2` WHERE `role` = ? AND `alliance` = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, role.getId());
            stmt.setLong(2, alliance);
        });
    }

    public void deleteRole(Roles role) {
        roleToAccountToDiscord.remove(role);
        update("DELETE FROM `ROLES2` WHERE `role` = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, role.getId());
        });
    }

    public  Map<Roles, Map<Long, Long>> getMappingRaw() {
        return Collections.unmodifiableMap(roleToAccountToDiscord);
    }

    public Map<Long, Role> getAccountMapping(Roles role) {
        loadRoles();
        Map<Long, Long> existing = roleToAccountToDiscord.get(role);
        if (existing == null || existing.isEmpty()) return Collections.emptyMap();

        Map<Long, Role> result = new HashMap<>();
        for (Map.Entry<Long, Long> entry : existing.entrySet()) {
            Role discordRole = guild.getRoleById(entry.getValue());
            if (discordRole != null) {
                result.put(entry.getKey(), discordRole);
            }
        }
        return result;
    }

    private void loadRoles() {
        if (roleToAccountToDiscord.isEmpty() && !cachedRoleAliases) {
            synchronized (roleToAccountToDiscord) {
                if (cachedRoleAliases) return;
                cachedRoleAliases = true;
                try (PreparedStatement stmt = prepareQuery("select * FROM ROLES2")) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            try {
                                Roles role = Roles.getRoleById(rs.getInt("role"));
                                long alias = rs.getLong("alias");
                                long alliance = rs.getLong("alliance");
                                roleToAccountToDiscord.computeIfAbsent(role, f -> new ConcurrentHashMap<>()).put(alliance, alias);
                            } catch (IllegalArgumentException ignore) {
                            }
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public Map<Long, Role> getRoleMap(Roles role) {
        loadRoles();
        Map<Long, Long> roleIds = roleToAccountToDiscord.get(role);
        if (roleIds == null) return Collections.emptyMap();
        Map<Long, Role> result = new HashMap<>();
        for (Map.Entry<Long, Long> entry : roleIds.entrySet()) {
            Role discordRole = guild.getRoleById(entry.getValue());
            if (discordRole != null) {
                result.put(entry.getKey(), discordRole);
            }
        }
        return result;
    }

    public Role getRole(Roles role, Long allianceOrNull) {
        loadRoles();
        Map<Long, Long> roleIds = roleToAccountToDiscord.get(role);
        if (roleIds == null) return null;
        Long mapping = null;
        if (allianceOrNull != null) {
            mapping = roleIds.get(allianceOrNull);
        }
        if (mapping == null) {
            mapping = roleIds.get(0L);
        }
        if (mapping != null) {
            return guild.getRoleById(mapping);
        }
        return null;
    }
}
