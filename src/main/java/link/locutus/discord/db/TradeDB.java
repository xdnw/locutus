package link.locutus.discord.db;

import com.ptsmods.mysqlw.table.ColumnType;
import com.ptsmods.mysqlw.table.TableIndex;
import com.ptsmods.mysqlw.table.TablePreset;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.Trade;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.trade.Offer;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.User;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class TradeDB extends DBMainV2 {
    public TradeDB() throws SQLException, ClassNotFoundException {
        super("trade");
    }


    @Override
    public void createTables() {
        TablePreset.create("TRADES")
                .putColumn("tradeId", ColumnType.INT.struct().setPrimary(true).setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("date", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("seller", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("buyer", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("resource", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("isBuy", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("quantity", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("ppu", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))

                .putColumn("type", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("date_accepted", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("parent_id", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))

                .addIndex(TableIndex.index("index_trade_date", "date", TableIndex.Type.INDEX))
                .addIndex(TableIndex.index("index_trade_type", "resource", TableIndex.Type.INDEX))
                .create(getDb());

        try (PreparedStatement stmt = getConnection().prepareStatement("ALTER TABLE TRADES " +
                "ADD COLUMN `type` INT NOT NULL DEFAULT 0, " +
                "ADD COLUMN `date_accepted` INT NOT NULL DEFAULT 0," +
                "ADD COLUMN `parent_id` INT NOT NULL DEFAULT 0")) {
            stmt.executeUpdate();
        } catch (SQLException ignore) {}

        String query = TablePreset.create("SUBSCRIPTIONS_2")
                .putColumn("user", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("resource", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("date", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("isBuy", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("above", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("ppu", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .putColumn("type", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                .buildQuery(getDb().getType());
        query = query.replace(");", ", PRIMARY KEY(user, resource, isBuy, above, type));");
        getDb().executeUpdate(query);

        {
            query = "CREATE TABLE IF NOT EXISTS `TRADEPRICE` (`resource` INT NOT NULL, `ppu` INT NOT NULL, `isBuy` INT NOT NULL, PRIMARY KEY(resource, ppu, isBuy))";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(query);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        ;
    }

//    public boolean updateTrades() {
//
//    }

    public void setTradePrice(ResourceType type, int ppu, boolean isBuy) {
        update("INSERT OR REPLACE INTO `TRADEPRICE`(`resource`, `ppu`, `isBuy`) VALUES(?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, type.ordinal());
            stmt.setInt(2, ppu);
            stmt.setBoolean(3, isBuy);
        });
    }

    public Map<ResourceType, Integer> getTradePrice(boolean isBuy) {
            long date = System.currentTimeMillis();
            Map<ResourceType, Integer> result = new EnumMap<>(ResourceType.class);
        try (PreparedStatement stmt = prepareQuery("select * FROM `TRADEPRICE` WHERE isBuy = ?")) {
            stmt.setBoolean(1, isBuy);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ResourceType type = ResourceType.values[rs.getInt("resource")];
                    int ppu = rs.getInt("ppu");
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

    public enum TradeAlertType {
        MIXUP,
        UNDERCUT,
        DISPARITY,
        ABSOLUTE,
        NO_LOW,
        NO_HIGH,
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

    public Set<Long> getSubscriptions(ResourceType type, TradeAlertType alert) {
            long date = System.currentTimeMillis();
            Set<Long> list = new LinkedHashSet<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM SUBSCRIPTIONS_2 WHERE resource = ? AND DATE > ? AND type = ?")) {
            stmt.setInt(1, type.ordinal());
            stmt.setLong(2, date);
            stmt.setInt(3, alert.ordinal());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long userId = rs.getLong("user");
                    list.add(userId);
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Set<Long> getSubscriptions(ResourceType type,  double disparity) {
            long date = System.currentTimeMillis();
            Set<Long> list = new LinkedHashSet<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM SUBSCRIPTIONS_2 WHERE resource = ? AND DATE > ? AND type = ?")) {
            stmt.setInt(1, type.ordinal());
            stmt.setLong(2, date);
            stmt.setInt(3, TradeAlertType.DISPARITY.ordinal());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long userId = rs.getLong("user");
                    list.add(userId);
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Set<Long> getSubscriptions(ResourceType type,  boolean isBuy, boolean above, int ppu, TradeAlertType alert) {
            long date = System.currentTimeMillis();
            String symbol = above ? "<" : ">";
            Set<Long> list = new LinkedHashSet<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM SUBSCRIPTIONS_2 WHERE resource = ? AND DATE > ? AND isBuy = ? AND above = ? AND ppu " + symbol + " ? AND type = ?")) {
            stmt.setInt(1, type.ordinal());
            stmt.setLong(2, date);
            stmt.setBoolean(3, isBuy);
            stmt.setBoolean(4, above);
            stmt.setInt(5, ppu);
            stmt.setInt(6, alert.ordinal());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long userId = rs.getLong("user");
                    list.add(userId);
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static class Subscription {
        public final ResourceType resource;
        public final long endDate;
        public final boolean isBuy;
        public final boolean above;
        public final int ppu;

        public Subscription(ResourceType resource, long endDate, boolean isBuy, boolean above, int ppu) {
            this.resource = resource;
            this.endDate = endDate;
            this.isBuy = isBuy;
            this.above = above;
            this.ppu = ppu;
        }
    }

    public Set<Subscription> getSubscriptions(long userId) {
            long date = System.currentTimeMillis();
            Set<Subscription> list = new LinkedHashSet<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM SUBSCRIPTIONS_2 WHERE user = ? AND date > ?")) {
            stmt.setLong(1, userId);
            stmt.setLong(2, date);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ResourceType resource = ResourceType.values[rs.getInt("resource")];
                    long endDate = rs.getLong("date");
                    boolean isBuy = rs.getBoolean("isBuy");
                    boolean above = rs.getBoolean("above");
                    int ppu = rs.getInt("ppu");

                    list.add(new Subscription(resource, endDate, isBuy, above, ppu));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void addTrades(Collection<Offer> offers) {
        try {
            synchronized (this) {
                String query = "INSERT OR REPLACE INTO `TRADES`(`tradeId`, `date`, `seller`, `buyer`, `resource`, `isBuy`, `quantity`, `ppu`) VALUES(?, ?, ?, ?, ?, ?, ?, ?)";
                executeBatch(offers, query, new ThrowingBiConsumer<Offer, PreparedStatement>() {
                    @Override
                    public void acceptThrows(Offer offer, PreparedStatement stmt) throws SQLException {
                        stmt.setInt(1, offer.getTradeId());
                        stmt.setLong(2, offer.getEpochms());
                        if (offer.getSeller() == null) stmt.setNull(3, Types.INTEGER);
                        else stmt.setInt(3, offer.getSeller());
                        if (offer.getBuyer() == null) stmt.setNull(4, Types.INTEGER);
                        else stmt.setInt(4, offer.getBuyer());
                        stmt.setInt(5, offer.getResource().ordinal());
                        stmt.setBoolean(6, offer.isBuy());
                        stmt.setInt(7, offer.getAmount());
                        stmt.setInt(8, offer.getPpu());
                    }
                });
            }
        } catch (Throwable e) {
            e.printStackTrace();
            for (Offer offer : offers) {
                addTrade(offer);
            }
        }
    }

    public void addTrade(Offer offer) {
        addTrade(offer.getTradeId(), offer.getEpochms(), offer.getSeller(), offer.getBuyer(), offer.getResource(), offer.isBuy(), offer.getAmount(), offer.getPpu());
    }

    private void addTrades(List<Trade> trades) {
        executeBatch(trades, "INSERT OR REPLACE INTO `TRADES`(`tradeId`, `date`, `seller`, `buyer`, `resource`, `isBuy`, `quantity`, `ppu`, `type`,  `date_accepted`, `parent_id`) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", new ThrowingBiConsumer<Trade, PreparedStatement>() {
            @Override
            public void acceptThrows(Trade trade, PreparedStatement stmt) throws Exception {
                stmt.setInt(1, trade.tradeId);
                stmt.setLong(2, trade.date);
                stmt.setInt(3, trade.seller);
                stmt.setInt(4, trade.buyer);
                stmt.setInt(5, trade.type.ordinal());
                stmt.setBoolean(6, trade.isBuy);
                stmt.setInt(7, trade.quantity);
                stmt.setInt(8, trade.ppu);
                stmt.setInt(9, trade.type.ordinal());
                stmt.setLong(10, trade.date_accepted);
                stmt.setInt(11, trade.parent_id);
            }
        });
    }

    public Offer getLatestTrade() {
        try (PreparedStatement stmt = prepareQuery("select * FROM TRADES ORDER BY tradeId DESC LIMIT 0, 1")) {

            int maxId = 6620000;

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    return create(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // `tradeId` INT NOT NULL PRIMARY KEY, `date` INT NOT NULL, seller INT NOT NULL, buyer INT NOT NULL, resource INT NOT NULL, isBuy INT NOT NULL, quantity INT NOT NULL, ppu INT NOT NULL

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

    public List<Offer> getOffers(long minDateMs) {
        ArrayList<Offer> list = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM TRADES WHERE DATE > ?")) {
            stmt.setLong(1, minDateMs);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(create(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Offer> getOffers(ResourceType type, long minDateMs) {
        ArrayList<Offer> list = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM TRADES WHERE DATE > ? AND resource = ?")) {
            stmt.setLong(1, minDateMs);
            stmt.setInt(2, type.ordinal());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(create(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Offer> getOffers(ResourceType type, long start, long end) {
        ArrayList<Offer> list = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM TRADES WHERE resource = ? AND DATE > ? AND DATE < ?")) {
            stmt.setInt(1, type.ordinal());
            stmt.setLong(2, start);
            stmt.setLong(3, end);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(create(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Offer getOffer(int tradeId) {
        try (PreparedStatement stmt = prepareQuery("select * FROM TRADES WHERE tradeId = ?")) {
            stmt.setLong(1, tradeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return create(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Offer create(ResultSet rs) throws SQLException {
        int tradeId = rs.getInt("tradeId");
        long date = rs.getLong("date");
        Integer seller = getInt(rs, "seller");
        Integer buyer = getInt(rs, "buyer");
        ResourceType type = ResourceType.values[rs.getInt("resource")];
        boolean isBuy = rs.getBoolean("isBuy");
        int quantity = rs.getInt("quantity");
        int ppu = rs.getInt("ppu");
        return new Offer(seller, buyer, type, isBuy, quantity, ppu, tradeId, date);
    }

    public List<Offer> getOffers(int nationId, long cutoffMs) {
            ArrayList<Offer> list = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM TRADES WHERE DATE > ? AND (seller = ? OR buyer = ?)")) {
            stmt.setLong(1, cutoffMs);
            stmt.setInt(2, nationId);
            stmt.setInt(3, nationId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(create(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
