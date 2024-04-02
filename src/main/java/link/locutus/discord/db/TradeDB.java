package link.locutus.discord.db;

import com.ptsmods.mysqlw.query.QueryCondition;
import com.ptsmods.mysqlw.query.QueryConditions;
import com.ptsmods.mysqlw.query.builder.SelectBuilder;
import com.ptsmods.mysqlw.table.ColumnType;
import com.ptsmods.mysqlw.table.TableIndex;
import com.ptsmods.mysqlw.table.TablePreset;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.db.entities.DBAlliancePosition;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.db.entities.TradeSubscription;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.User;
import org.jooq.Condition;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
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
                .addIndex(TableIndex.index("index_trade_seller", "seller", TableIndex.Type.INDEX))
                .addIndex(TableIndex.index("index_trade_buyer", "buyer", TableIndex.Type.INDEX))
                .create(getDb());

        // executeStmt("CREATE INDEX IF NOT EXISTS index_mil_unit ON NATION_MIL_HISTORY (unit);");
        // add index for seller and buyer if not exist
        executeStmt("CREATE INDEX IF NOT EXISTS index_trade_seller ON TRADES (seller);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_trade_buyer ON TRADES (buyer);");

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

    public static class BulkTradeOffer {
        public int id = -1;
        public int resourceId;
        public int nation;
        public long quantity;
        public boolean isBuy;
        public int minPPU;
        public int maxPPU;
        public boolean negotiable;
        public long expire;
        public long exchangeForBits;
        public double[] exchangePPU;

        public void setExchangeFor(Collection<ResourceType> exchangeFor, double[] exchangePPU) {
            this.exchangeForBits = 0L;
            if (exchangeFor != null) {
                for (ResourceType type : exchangeFor) {
                    this.exchangeForBits |= 1L << type.ordinal();
                }
            }
            if (exchangePPU != null) {
                for (int i = 0; i < exchangePPU.length; i++) {
                    if (exchangePPU[i] >= 0.01) {
                        this.exchangeForBits |= 1L << i;
                    }
                }
            }
        }
        public BulkTradeOffer(int resourceId, int nation, long quantity, boolean isBuy, int minPPU, int maxPPU, boolean negotiable, long expire, List<ResourceType> exchangeFor, double[] exchangePPU) {
            this.resourceId = resourceId;
            this.nation = nation;
            this.quantity = quantity;
            this.isBuy = isBuy;
            this.minPPU = minPPU;
            this.maxPPU = maxPPU;
            this.negotiable = negotiable;
            this.expire = expire;
            this.setExchangeFor(exchangeFor, exchangePPU);
            this.exchangePPU = exchangePPU;
        }

        public Set<ResourceType> getSelling() {
            Set<ResourceType> resources = new HashSet<>();
            if (isBuy) {
                resources.addAll(getExchangeFor());
                resources.add(ResourceType.MONEY);
            } else {
                resources.add(getResource());
            }
            return resources;
        }

        public Set<ResourceType> getBuying() {
            Set<ResourceType> resources = new HashSet<>();
            if (!isBuy) {
                resources.addAll(getExchangeFor());
                resources.add(ResourceType.MONEY);
            } else {
                resources.add(getResource());
            }
            return resources;
        }

        public Map.Entry<Double, Double> getPriceRange(ResourceType tradeFor) {
            Set<ResourceType> exchangeFor = getExchangeFor();
            if (tradeFor != null && tradeFor != ResourceType.MONEY) {
                if (!exchangeFor.contains(tradeFor)) {
                    return null;
                }
                double ppu = exchangePPU[tradeFor.ordinal()];
                double rssMin;
                double rssMax;
                if (ppu > 0) {
                    rssMin = ppu;
                    rssMax = ppu;
                } else {
                    rssMin = Locutus.imp().getTradeManager().getLowAvg(tradeFor);
                    rssMax = Locutus.imp().getTradeManager().getHighAvg(tradeFor);
                    if (rssMin > rssMax) rssMin = rssMax;
                    if (rssMax < rssMin) rssMax = rssMin;
                }

                Map.Entry<Double, Double> cashRange = getPriceRange(null);
                double min = 1 * rssMin / cashRange.getValue();
                double max = 1 * rssMax / cashRange.getKey();
                return new AbstractMap.SimpleEntry<>(min, max);
            }

            double cashMin = minPPU > 0 ? minPPU : Locutus.imp().getTradeManager().getLowAvg(getResource());
            double cashMax = maxPPU > 0 ? maxPPU : Locutus.imp().getTradeManager().getHighAvg(getResource());
            if (cashMin > cashMax) cashMin = cashMax;
            if (cashMax < cashMin) cashMax = cashMin;
            return new AbstractMap.SimpleEntry<>(cashMin, cashMax);
        }

        public BulkTradeOffer(BulkTradeOffer copy) {
            this.id = copy.id;
            this.resourceId = copy.resourceId;
            this.nation = copy.nation;
            this.quantity = copy.quantity;
            this.isBuy = copy.isBuy;
            this.minPPU = copy.minPPU;
            this.maxPPU = copy.maxPPU;
            this.negotiable = copy.negotiable;
            this.expire = copy.expire;
            this.exchangeForBits = copy.exchangeForBits;
            this.exchangePPU = copy.exchangePPU;
        }

        public BulkTradeOffer(ResultSet rs) throws SQLException {
            id = rs.getInt(1);
            resourceId = rs.getInt(2);
            nation = rs.getInt(3);
            quantity = rs.getLong(4);
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

        public boolean canExchangeFor(ResourceType type) {
            return (exchangeForBits & (1L << type.ordinal())) != 0;
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
                if (exchangePPU == null) {
                    stmt.setNull(i++, Types.BINARY);
                } else {
                    stmt.setObject(i++, ArrayUtil.toByteArray(exchangePPU));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        public boolean isExpired() {
            return expire < System.currentTimeMillis();
        }

        /**
         * Return the price of 1 traderBuying in traderSelling
         * @param traderBuying
         * @param traderSelling
         * @return
         */
        public Map.Entry<Double, Double> getPriceRange(ResourceType traderBuying, ResourceType traderSelling) {
            if (traderBuying == null) traderBuying = ResourceType.MONEY;
            if (traderSelling == null) traderSelling = ResourceType.MONEY;

            String error = null;
            if (isBuy) {
                if (traderSelling != getResource()) {
                    error = "Cannot sell " + traderSelling + "!=" + getResource();
                }
                if (!getExchangeFor().contains(traderBuying) && traderBuying != ResourceType.MONEY) {
                    error = "Cannot sell " + traderBuying + "!=" + StringMan.getString(getExchangeFor()) + " or " + ResourceType.MONEY;
                }
            } else {
                if (traderBuying != getResource()) {
                    error = "Cannot sell " + traderBuying + "!=" + getResource();
                }
                if (!getExchangeFor().contains(traderSelling) && traderSelling != ResourceType.MONEY) {
                    error = "Cannot sell " + traderSelling + "!=" + StringMan.getString(getExchangeFor()) + " or " + ResourceType.MONEY;
                }
            }
            if (isBuy) {
                Map.Entry<Double, Double> range = getPriceRange(traderBuying);
                return Map.entry(1 / range.getValue(), 1 / range.getKey());
            } else {
                Map.Entry<Double, Double> range = getPriceRange(traderSelling);
                return range;
            }
        }

        public DBNation getNation() {
            return DBNation.getById(nation);
        }

        public String toSimpleString() {
            String result = "#" + id + ": `" +
                    PW.getName(nation, false) + "` " +
                    (isBuy ? "Buying" : "Selling") + " " +
                    MathMan.format(quantity) + "x " +
                    getResource() + " for " +
                    getPpuRangeStr(false);
            if (negotiable) result += " (negotiable)";
            return result;
        }

        public String toSimpleString(ResourceType traderBuying, ResourceType traderSelling, boolean boldMin, boolean boldMax) {
            if (traderBuying == null) traderBuying = ResourceType.MONEY;
            if (traderSelling == null) traderSelling = ResourceType.MONEY;
            if (traderBuying == traderSelling) {
                throw new IllegalArgumentException("Buying and selling cannot be the same resource (" + traderSelling + ")");
            }
            Map.Entry<Double, Double> range = getPriceRange(traderBuying, traderSelling);

            StringBuilder response = new StringBuilder();
            boolean isExpired = isExpired();
            if (isExpired) response.append("~~");

            response.append("#" + id + ": ");

            String minStr = range.getKey() < 1 ? "1/" + MathMan.format(1 / range.getKey()) : MathMan.format(range.getKey());
            String maxStr = range.getValue() < 1 ? "1/" + MathMan.format(1 / range.getValue()) : MathMan.format(range.getValue());

            if (traderSelling == null || traderSelling == ResourceType.MONEY) {
                response.append("$");
            }
            response.append(String.format("%8s", minStr)).append("-").append(String.format("%8s", maxStr));
            if (traderSelling != null && traderSelling != ResourceType.MONEY) {
                response.append(" " + String.format("%5s", traderSelling));
            }
            response.append(" | Amt: " + String.format("%9s", MathMan.format(quantity) + getResource()));
            // expire

            ResourceType offerTradeFor = getResource() == traderBuying ? traderSelling : traderBuying;
            Set<ResourceType> exchangeForSet = getExchangeFor();

            // C/O/U/L/I/B/G/M/S/A/F/$
            if (exchangeForSet.contains(offerTradeFor) && exchangeForSet.size() > 1) {
                response.append(" | Also For: ");
                for (ResourceType type : exchangeForSet) {
                    String symbol;
                    if (type == ResourceType.MONEY) {
                        symbol = "$";
                    } else {
                        symbol = type.toString().substring(0, 1);
                    }
                    response.append(symbol);
                }
            }
            if (!isExpired) {
                response.append(" | Ends: " + DiscordUtil.timestamp(expire, null));
            }
            DBNation dbNation = getNation();
            response.append(" | By: " + PW.getName(nation, false));
            Long userId = dbNation == null ? null : dbNation.getUserId();
            if (userId != null) response.append(" <@" + userId + ">");
            if (negotiable) response.append(" | **NEGOTIABLE**");
            if (isExpired) response.append("~~");
            return response.toString();
        }

        public String getTitle() {
            return "#" + id + ": " + PW.getName(nation, false) + " " + (isBuy ? "Buying" : "Selling") + " " + MathMan.format(quantity) + "x " + getResource();
        }

        public String toPrettyString() {
            if (isExpired()) {
                return "**EXPIRED** (No longer valid)\n";
            }
            StringBuilder result = new StringBuilder();
            if (negotiable) {
                result.append("**NEGOTIABLE**\n");
            }
//            nation as Seller: NATION_NAME
            DBNation natObj = DBNation.getById(nation);
            result.append((isBuy ? "Buyer" : "Seller") + ": ");
            if (natObj != null) {
                result.append(natObj.getNationUrlMarkup(true) + " | " + natObj.getAllianceUrlMarkup(true));
                Long userId = natObj.getUserId();
                if (userId != null) {
                    result.append(" | <@" + userId + ">");
                }
                DBAlliancePosition pos = natObj.getAlliancePosition();
                boolean hasBankPerms = natObj.getPositionEnum().id >= Rank.HEIR.id || (pos != null && pos.hasPermission(AlliancePermission.WITHDRAW_BANK));
                if (!hasBankPerms) {
                    result.append("(No Bank access)");
                }
            } else {
                result.append(nation + " (Deleted?)");
            }
            result.append("\n");
//            resourceId as Resource: RESOURCE_NAME
            result.append("Resource: " + getResource() + "\n");
//            quantity as Amount: QUANTITY
            result.append("Amount: " + MathMan.format(quantity) + "\n");
//            minPPU as Min PPU: MIN_PPU (if not null)
            String ppuRange = getPpuRangeStr(false);
            result.append("PPU: " + ppuRange + "\n");
//            expire as Expires: EXPIRE_DATE (using TimeUtil discord format)
            result.append("Expires: " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, expire - System.currentTimeMillis()) + "\n");


//            exchangeForBits as Exchange For: Set<EXCHANGE_FOR> (if not 0)
//            exchangePPU as Exchange PPU: Set<EXCHANGE_PPU> (if not null)

            return result.toString();
        }

        public String getPpuRangeStr(boolean compact) {
            String ppuRange;
            if (minPPU > 0 && maxPPU > 0) {
                ppuRange = MathMan.format(minPPU) + "-" + MathMan.format(maxPPU);
            } else if (minPPU > 0) {
                ppuRange = ">" + MathMan.format(minPPU);
            } else if (maxPPU > 0) {
                ppuRange = "<" + MathMan.format(maxPPU);
            } else {
                String low = MathMan.format(Locutus.imp().getTradeManager().getLowAvg(getResource()));
                String high = MathMan.format(Locutus.imp().getTradeManager().getHighAvg(getResource()));
                if (compact) {
                    ppuRange = "~" + low + "-" + high + "";
                } else {
                    ppuRange = "Market Average (currently " + low + "-" + high + ")";
                }
            }
            return ppuRange;
        }
    }



    public synchronized void updateMarketOffers(List<BulkTradeOffer> offers) {
        for (BulkTradeOffer offer : offers) {
            if (offer.id <= -1) {
                throw new IllegalArgumentException("Offer id must be positive");
            }
        }
        executeBatch(offers, "UPDATE `MARKET_OFFERS` SET `resource` = ?, `nation` = ?, `quantity` = ?, `isBuy` = ?, `minPPU` = ?, `maxPPU` = ?, `negotiable` = ?, `expire` = ?, `exchangeFor` = ?, `exchangePPU` = ? WHERE `id` = ?", new ThrowingBiConsumer<BulkTradeOffer, PreparedStatement>() {
            @Override
            public void acceptThrows(BulkTradeOffer offer, PreparedStatement stmt) throws Exception {
                offer.set(stmt, true);
                stmt.setInt(11, offer.id);
            }
        });
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

    public synchronized void addMarketOffers(BulkTradeOffer offer) {
        try (PreparedStatement stmt = getConnection().prepareStatement("INSERT OR REPLACE INTO `MARKET_OFFERS`(`resource`, `nation`, `quantity`, `isBuy`, `minPPU`, `maxPPU`, `negotiable`, `expire`, `exchangeFor`, `exchangePPU`) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            offer.set(stmt, false);
            // get generated key
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    offer.id = rs.getInt(1);
                } else {
                    throw new SQLException("Creating offer failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
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
        return getTrades(startDate, Long.MAX_VALUE);
    }

    public List<DBTrade> getTrades(long startDate, long endDate) {
        return getTrades(f -> {
            QueryConditions condition = QueryCondition.notEquals("seller", 0).and(QueryCondition.notEquals("buyer", 0));
            if (startDate != 0) condition = QueryCondition.greater("date", startDate).and(condition);
            if (endDate != Long.MAX_VALUE) condition = QueryCondition.less("date", endDate).and(condition);
            f.where(condition);
        });
    }

    public List<DBTrade> getTrades(ResourceType type, long startDate, long endDate) {
        return getTrades(f ->
                f.where(QueryCondition.equals("resource", type.ordinal())
                                .and(QueryCondition.greater("date", startDate))
                                .and(QueryCondition.less("date", endDate))
                                .and(QueryCondition.notEquals("seller", 0))
                                .and(QueryCondition.notEquals("buyer", 0))
                        )
        );
    }

    public List<DBTrade> getTrades(Set<Integer> nationIds, long startDate) {
        return getTrades(nationIds, startDate, Long.MAX_VALUE);
    }

    public List<DBTrade> getTrades(Set<Integer> nationIds, long startDate, long endDate) {
        if (nationIds.isEmpty()) return Collections.emptyList();
        if (nationIds.size() == 1) {
            int nationId = nationIds.iterator().next();
            return getTrades(nationId, startDate);
        }
        List<Integer> sorted = new ArrayList<>(nationIds);
        sorted.sort(Comparator.naturalOrder());
        Object[] nationIdsArr = sorted.toArray();
        List<DBTrade> result = getTrades(f -> {
            QueryConditions condition = QueryCondition.in("seller", nationIdsArr).or(QueryCondition.in("buyer", nationIdsArr));
            if (startDate > 0) condition = QueryCondition.greater("date", startDate).and(condition);
            if (endDate != Long.MAX_VALUE) condition = QueryCondition.less("date", endDate).and(condition);
            f.where(condition);
        });
        return result;
    }

    public List<DBTrade> getTrades(int nationId, long startDate) {
        List<DBTrade> result = getTrades(f -> f.where(QueryCondition.greater("date", startDate)
                        .and(QueryCondition.equals("seller", nationId).or(QueryCondition.equals("buyer", nationId)))));
        result.removeIf(f -> f.getSeller() == 0 || f.getBuyer() == 0);
        return result;
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

    public List<DBTrade> getTradesById(Collection<Integer> ids) {
        if (ids.isEmpty()) return Collections.emptyList();
        if (ids.size() == 1) {
            return Collections.singletonList(getTradeById(ids.iterator().next()));
        }
        List<Integer> idsList = new ArrayList<>(ids);
        idsList.sort(Comparator.naturalOrder());
        return getTrades(f -> f.where(QueryCondition.in("tradeId", idsList.toArray())));
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