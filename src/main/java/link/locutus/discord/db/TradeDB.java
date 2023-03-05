package link.locutus.discord.db;

import com.ptsmods.mysqlw.query.QueryCondition;
import com.ptsmods.mysqlw.query.builder.SelectBuilder;
import com.ptsmods.mysqlw.table.ColumnType;
import com.ptsmods.mysqlw.table.TableIndex;
import com.ptsmods.mysqlw.table.TablePreset;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.db.entities.TradeSubscription;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.util.scheduler.ThrowingFunction;
import net.dv8tion.jda.api.entities.User;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TradeDB extends DBMainV2 {
    public TradeDB() throws SQLException, ClassNotFoundException {
        super("trade");
    }


    @Override
    public void createTables() {
        TablePreset.create("TRADES")
                .putColumn("tradeId", ColumnType.INT.struct().setPrimary(true).setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("date", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("seller", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("buyer", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("resource", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("isBuy", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("quantity", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("ppu", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))

                .addIndex(TableIndex.index("index_trade_date", "date", TableIndex.Type.INDEX))
                .addIndex(TableIndex.index("index_trade_type", "resource", TableIndex.Type.INDEX))
                .create(getDb());

        deleteIncompleteTrades();

        TablePreset.create("COLOR_BLOC")
                .putColumn("id", ColumnType.INT.struct().setPrimary(true).setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("name", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(32)))
                .putColumn("bonus", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .create(getDb());

        try {
            try (PreparedStatement close = prepareQuery("ALTER TABLE TRADES ADD COLUMN `type` INT NOT NULL DEFAULT 0" )) {close.execute();}
            try (PreparedStatement close = prepareQuery("ALTER TABLE TRADES ADD COLUMN `date_accepted` BIGINT NOT NULL DEFAULT 0" )) {close.execute();}
            try (PreparedStatement close = prepareQuery("ALTER TABLE TRADES ADD COLUMN `parent_id` INT NOT NULL DEFAULT 0" )) {close.execute();}
        } catch (SQLException ignore) {}

        String query = TablePreset.create("SUBSCRIPTIONS_2")
                .putColumn("user", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("resource", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("date", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("isBuy", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("above", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("ppu", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("type", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .buildQuery(getDb().getType());
        query = query.replace(");", ", PRIMARY KEY(user, resource, isBuy, above, type));");
        getDb().executeUpdate(query);

        {
            query = "CREATE TABLE IF NOT EXISTS `TRADEPRICE_2` (`resource` INT NOT NULL, `ppu` INT NOT NULL, `isBuy` INT NOT NULL, PRIMARY KEY(resource, isBuy))";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(query);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        {
            // int resource not null, int nation not null, int resource not null, int quantity not null, boolean isBuy notNull, int minPPU, int maxPPU, boolean negotiable, long expire, long exchangeFor, byte[] exchangePPU
            String stmt = "CREATE TABLE IF NOT EXISTS `MARKET_OFFERS` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "`resource` INT NOT NULL, " +
                    "`nation` INT NOT NULL, " +
                    "`quantity` INT NOT NULL, " +
                    "`isBuy` BOOLEAN NOT NULL," +
                    " `minPPU` INT NOT NULL, " +
                    "`maxPPU` INT NOT NULL, " +
                    "`negotiable` BOOLEAN NOT NULL, " +
                    "`expire` BIGINT NOT NULL," +
                    " `exchangeFor` BIGINT NOT NULL, " +
                    "`exchangePPU` BLOB)";
            executeStmt(stmt);
        }

        purgeExpiredMarketOffers();
        purgeSubscriptions();
        loadColorBlocs();
    }

    public class BulkTradeOffer {
        public int id;
        public int resourceId;
        public int nation;
        public int quantity;
        public boolean isBuy;
        public int minPPU;
        public int maxPPU;
        public boolean negotiable;
        public long expire;
        public long exchangeForBits;
        public double[] exchangePPU;

        public BulkTradeOffer(int id, int resourceId, int nation, int quantity, boolean isBuy, int minPPU, int maxPPU, boolean negotiable, long expire, long exchangeForBits, double[] exchangePPU) {
            this.id = id;
            this.resourceId = resourceId;
            this.nation = nation;
            this.quantity = quantity;
            this.isBuy = isBuy;
            this.minPPU = minPPU;
            this.maxPPU = maxPPU;
            this.negotiable = negotiable;
            this.expire = expire;
            this.exchangeForBits = exchangeForBits;
            this.exchangePPU = exchangePPU;
        }

        public BulkTradeOffer(ResultSet rs) throws SQLException {
            id = rs.getInt(1);
            resourceId = rs.getInt(2);
            nation = rs.getInt(3);
            quantity = rs.getInt(4);
            isBuy = rs.getBoolean(5);
            minPPU = rs.getInt(6);
            maxPPU = rs.getInt(7);
            negotiable = rs.getBoolean(8);
            expire = rs.getLong(9);
            exchangeForBits = rs.getLong(10);
            // bytes might be null
            byte[] exchangePPUBytes = rs.getBytes(11);
            if (exchangePPUBytes != null) {
                exchangePPU = ArrayUtil.toDoubleArray(exchangePPUBytes);
            }
        }

        public ResourceType getResource() {
            return ResourceType.values[resourceId];
        }

        public Set<ResourceType> getExchangeFor() {
            Set<ResourceType> set = new HashSet<>();
            for (ResourceType type : ResourceType.values) {
                if ((exchangeForBits & (1L << type.ordinal())) != 0) {
                    set.add(type);
                }
            }
            return set;
        }

        public double getExchangePpu(ResourceType type) {
            return exchangePPU != null ? exchangePPU[type.ordinal()] : -1;
        }

        public void set(PreparedStatement stmt, boolean setId) {
            try {
                int i = 1;
                if (setId) {
                    stmt.setObject(i++, id);
                }
                stmt.setObject(i++, resourceId);
                stmt.setObject(i++, nation);
                stmt.setObject(i++, quantity);
                stmt.setObject(i++, isBuy);
                stmt.setObject(i++, minPPU);
                stmt.setObject(i++, maxPPU);
                stmt.setObject(i++, negotiable);
                stmt.setObject(i++, expire);
                stmt.setObject(i++, exchangeForBits);
                stmt.setObject(i++, ArrayUtil.toByteArray(exchangePPU));
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void updateMarketOffers(List<BulkTradeOffer> offers) {
        executeBatch(offers, "UPDATE `MARKET_OFFERS` SET `resource` = ?, `nation` = ?, `quantity` = ?, `isBuy` = ?, `minPPU` = ?, `maxPPU` = ?, `negotiable` = ?, `expire` = ?, `exchangeFor` = ?, `exchangePPU` = ? WHERE `id` = ?", new ThrowingBiConsumer<BulkTradeOffer, PreparedStatement>() {
            @Override
            public void acceptThrows(BulkTradeOffer offer, PreparedStatement stmt) throws Exception {
                offer.set(stmt, true);
            }
        });
    }

    public synchronized void instertMarketOffer(BulkTradeOffer offer) {
        try (PreparedStatement stmt = getConnection().prepareStatement("INSERT INTO `MARKET_OFFERS`(`resource`, `nation`, `quantity`, `isBuy`, `minPPU`, `maxPPU`, `negotiable`, `expire`, `exchangeFor`, `exchangePPU`) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            offer.set(stmt, false);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    offer.id = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<BulkTradeOffer> getMarketOffers() {
        List<BulkTradeOffer> result = new ArrayList<>();
        com.ptsmods.mysqlw.query.builder.SelectBuilder builder = getDb().selectBuilder("MARKET_OFFERS")
                .select("*");
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                result.add(new BulkTradeOffer(rs));
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void addMarketOffers(List<BulkTradeOffer> offers) {
        executeBatch(offers, "INSERT INTO `MARKET_OFFERS`(`resource`, `nation`, `quantity`, `isBuy`, `minPPU`, `maxPPU`, `negotiable`, `expire`, `exchangeFor`, `exchangePPU`) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", new ThrowingBiConsumer<BulkTradeOffer, PreparedStatement>() {
            @Override
            public void acceptThrows(BulkTradeOffer offer, PreparedStatement stmt) throws Exception {
                offer.set(stmt, false);
            }
        });
    }

    public synchronized void deleteBulkMarketOffers(Set<Integer> ids) {
        if (ids.isEmpty()) return;
        ArrayList<Integer> idsList = new ArrayList<>(ids);
        Collections.sort(idsList);
        executeStmt("DELETE FROM `MARKET_OFFERS` WHERE `id` in " + StringMan.getString(idsList));
    }

    public void purgeExpiredMarketOffers() {
        executeStmt("DELETE FROM `MARKET_OFFERS` WHERE `expire` < " + System.currentTimeMillis());
    }

    public void saveColorBlocs() {
        executeBatch(Arrays.asList(NationColor.values), "INSERT OR REPLACE INTO `COLOR_BLOC`(`id`, `name`, `bonus`) VALUES(?, ?, ?)", new ThrowingBiConsumer<NationColor, PreparedStatement>() {
            @Override
            public void acceptThrows(NationColor color, PreparedStatement stmt) throws Exception {
                stmt.setObject(1, color.ordinal());
                stmt.setObject(2, color.getVotedName());
                stmt.setObject(3, color.getTurnBonus());
            }
        });
    }

    public void loadColorBlocs() {
        query("SELECT * FROM `COLOR_BLOC`", f -> {}, new Consumer<ResultSet>() {
            @Override
            public void accept(ResultSet resultSet) {
                try {
                    while (resultSet.next()) {
                        int id = resultSet.getInt("id");
                        String name = resultSet.getString("name");
                        int bonus = resultSet.getInt("bonus");
                        NationColor color = NationColor.values[id];
                        color.setVotedName(name);
                        color.setTurnBonus(bonus);
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void setTradePrice(ResourceType type, int ppu, boolean isBuy) {
        long pair = (isBuy ? 1 : 0) ^ ((type.ordinal() ^ (ppu << 4)) << 1);
        update("INSERT OR REPLACE INTO `TRADEPRICE_2`(`resource`, `ppu`, `isBuy`) VALUES(?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, type.ordinal());
            stmt.setInt(2, ppu);
            stmt.setBoolean(3, isBuy);
        });
    }

    public Map<ResourceType, Integer> getTradePrice(boolean isBuy) {
            long date = System.currentTimeMillis();
            Map<ResourceType, Integer> result = new EnumMap<>(ResourceType.class);
        try (PreparedStatement stmt = prepareQuery("select * FROM `TRADEPRICE_2` WHERE isBuy = ?")) {
            stmt.setBoolean(1, isBuy);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int ppu = rs.getInt("ppu");
                    ResourceType type = ResourceType.values[rs.getInt("resource")];
                    result.put(type, ppu);
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public synchronized void purgeSubscriptions() {
        long now = System.currentTimeMillis();
        update("DELETE FROM `SUBSCRIPTIONS_2` WHERE date < ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, now);
        });
    }

    public void unsubscribeAll(User user) {
        update("DELETE FROM `SUBSCRIPTIONS_2` WHERE user = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, user.getIdLong());
        });
    }

    public void unsubscribe(User user, ResourceType resource) {
        update("DELETE FROM `SUBSCRIPTIONS_2` WHERE user = ? AND resource = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, user.getIdLong());
            stmt.setInt(2, resource.ordinal());
        });
    }

    public List<DBTrade> getActiveTrades() {
        return getTrades(builder -> builder.where(QueryCondition.equals("date_accepted", -1L)));
    }

    public DBTrade getTradeById(int id) {
        return getTrades(builder -> builder.where(QueryCondition.equals("id", id))).stream().findFirst().orElse(null);
    }

    public enum TradeAlertType {
        MISTRADE,
        UNDERCUT,
        DISPARITY,
        ABSOLUTE,
        NO_OFFER,
    }

    public void subscribe(User user, ResourceType resource, long date, boolean isBuy, boolean above, int ppu, TradeAlertType type) {
        update("INSERT OR REPLACE INTO `SUBSCRIPTIONS_2`(`user`, `resource`, `date`, `isBuy`, `above`, `ppu`, type) VALUES(?, ?, ?, ?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, user.getIdLong());
            stmt.setInt(2, resource.ordinal());
            stmt.setLong(3, date);
            stmt.setBoolean(4, isBuy);
            stmt.setBoolean(5, above);
            stmt.setInt(6, ppu);
            stmt.setInt(7, type.ordinal());
        });
    }

    public List<TradeSubscription> getSubscriptions(Consumer<SelectBuilder> query) {
        List<TradeSubscription> list = new ArrayList<>();
        SelectBuilder builder = getDb().selectBuilder("SUBSCRIPTIONS_2")
                .select("*");
        if (query != null) query.accept(builder);
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                list.add(new TradeSubscription(rs));
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    public List<TradeSubscription> getSubscriptions(long userId) {
        return getSubscriptions(f ->
                f.where(QueryCondition.equals("user", userId))
                        .where(QueryCondition.greater("date", System.currentTimeMillis()))
        );
    }

    public void deleteIncompleteTrades() {
        long date = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(15);
        executeStmt("DELETE FROM TRADES WHERE seller = 0 OR buyer = 0 AND date < " + date);
    }

    public void deleteTradesById(Collection<Integer> ids) {
        if (ids.isEmpty()) {
            return;
        }
        executeStmt("DELETE FROM TRADES WHERE tradeId in " + StringMan.getString(ids));
    }
    public void saveTrades(Collection<DBTrade> trades) {
        if (trades.isEmpty()) return;
        if (trades.size() > 10) System.out.println("remove:|| save trades " + trades.size());
        executeBatch(trades, "INSERT OR REPLACE INTO `TRADES`(`tradeId`, `date`, `seller`, `buyer`, `resource`, `isBuy`, `quantity`, `ppu`, `type`,  `date_accepted`, `parent_id`) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", new ThrowingBiConsumer<DBTrade, PreparedStatement>() {
            @Override
            public void acceptThrows(DBTrade trade, PreparedStatement stmt) throws Exception {
                stmt.setInt(1, trade.getTradeId());
                stmt.setLong(2, trade.getDate());
                stmt.setInt(3, trade.getSeller());
                stmt.setInt(4, trade.getBuyer());
                stmt.setInt(5, trade.getResource().ordinal());
                stmt.setBoolean(6, trade.isBuy());
                stmt.setInt(7, trade.getQuantity());
                stmt.setInt(8, trade.getPpu());
                stmt.setInt(9, trade.getType().ordinal());
                stmt.setLong(10, trade.getDate_accepted());
                stmt.setInt(11, trade.getParent_id());
            }
        });
    }

    public List<DBTrade> getTrades(long startDate) {
        return getTrades(f ->
                f.where(QueryCondition.greater("date", startDate))
        );
    }

    public List<DBTrade> getTrades(ResourceType type, long startDate, long endDate) {
        return getTrades(f ->
                f.where(QueryCondition.equals("resource", type.ordinal()))
                        .where(QueryCondition.greater("date", startDate))
                        .where(QueryCondition.less("date", endDate))
        );
    }

    public List<DBTrade> getTrades(int nationId, long startDate) {
        return getTrades(f -> f.where(QueryCondition.greater("date", startDate))
                .where(QueryCondition.equals("seller", nationId).or(QueryCondition.equals("buyer", nationId)))
        );
    }
    public List<DBTrade> getTrades(Consumer<com.ptsmods.mysqlw.query.builder.SelectBuilder> query) {
        List<DBTrade> result = new ArrayList<>();
        com.ptsmods.mysqlw.query.builder.SelectBuilder builder = getDb().selectBuilder("TRADES")
                .select("*");
        if (query != null) query.accept(builder);
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                result.add(new DBTrade(rs));
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
    public Map<Long, Double> getAverage(long minDate, ResourceType type, int minQuantity, int min, int max) {
        String query = "select\n" +
                "trades.date,sum(ppu * quantity),sum(quantity)\n" +
                "from TRADES WHERE trades.date > ? AND resource = ? AND ppu >= ? and ppu <= ? and quantity > ?\n" +
                "group by date(datetime(trades.date/1000,'unixepoch'),'start of day')\n" +
                "order by trades.date DESC";

        Map<Long, Double> averages = new HashMap<>();

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setLong(1, minDate);
            stmt.setInt(2, type.ordinal());
            stmt.setInt(3, min);
            stmt.setInt(4, max);
            stmt.setInt(5, minQuantity);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long date = rs.getLong(1);
                    long day = TimeUtil.getDay(Instant.ofEpochMilli(date).atZone(ZoneOffset.UTC));
                    long total = rs.getLong(2);
                    long quantity = rs.getLong(3);
                    double avg = (double) total / quantity;

                    averages.put(day, avg);
                }
            }
            return averages;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
