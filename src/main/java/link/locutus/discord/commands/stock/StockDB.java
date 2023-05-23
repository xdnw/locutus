package link.locutus.discord.commands.stock;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.DBMain;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationOrExchange;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class StockDB extends DBMain {
    public static final long ROOT_GUILD = 679408591274377283L;
    public static final String INVITE = "https://discord.gg/TAF5zkh6WJ";

    public StockDB() throws SQLException, ClassNotFoundException {
        super("stock");
    }

    @Override
    public synchronized void createTables() {
        // companies
        executeStmt("CREATE TABLE IF NOT EXISTS `exchanges` (`id` INTEGER PRIMARY KEY, `symbol` TEXT NOT NULL UNIQUE, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `shares` INT NOT NULL, `owner` BIGINT NOT NULL, `guild` BIGINT NOT NULL, `charter` TEXT, `website` TEXT, `category` INT NOT NULL, `required_rank` INT NOT NULL)");
        // officers
        executeStmt("CREATE TABLE IF NOT EXISTS `ranks` (`exchange` INT NOT NULL, `nation` INT NOT NULL, `rank` INT NOT NULL, PRIMARY KEY(exchange, nation))");
        // shareholders
        executeStmt("CREATE TABLE IF NOT EXISTS `shareholders` (`shareholder` INT NOT NULL, `company` INT NOT NULL, `shares` INT NOT NULL, PRIMARY KEY(shareholder, company))");
        // Trades are from nation <-> nation
        executeStmt("CREATE TABLE IF NOT EXISTS `TRADES` (`id` INTEGER PRIMARY KEY, `company` INT NOT NULL, `buyer` INT NOT NULL, `seller` INT NOT NULL, `is_buying` INT NOT NULL, `resource` INT NOT NULL, `amount` BIGINT NOT NULL, `price` BIGINT NOT NULL, `date_offered` BIGINT NOT NULL, `date_bought` BIGINT NOT NULL)");
//         Transactions from corp <-> nation or corp <-> corp
//        executeStmt("CREATE TABLE IF NOT EXISTS `TRANSACTIONS` (`tx_id` INT NOT NULL PRIMARY KEY, tx_datetime INT NOT NULL, sender_id INT NOT NULL, sender_type INT NOT NULL, receiver_id INT NOT NULL, receiver_type INT NOT NULL, banker_nation_id INT NOT NULL, type INT NOT NULL, amount INT NOT NULL)");

        if (getExchanges().isEmpty()) {
            for (ResourceType type : ResourceType.values) {
                Exchange company = new Exchange(ExchangeCategory.CURRENCY, type.name().toLowerCase(), type.name() + " resource exchange, 1 share = 1 resource", Integer.MAX_VALUE, ROOT_GUILD);
                company.requiredRank = Rank.UNINVITE;
                company.id = type.ordinal();
                addExchangeWithId(company);
            }
        }
        Map<Integer, Exchange> exchanges = getExchangesById();
        // add exchange for each alliance
        for (DBAlliance alliance : Locutus.imp().getNationDB().getAlliances()) {
            int id = alliance.getAlliance_id();
            Exchange exchange = exchanges.get(id);
            if (exchange == null) {
                exchange = new Exchange(ExchangeCategory.ALLIANCE, id + "", "", 0, ROOT_GUILD);
                exchange.name = alliance.getName();
                exchange.requiredRank = Rank.OFFICER;
                addExchangeWithId(exchange);
            }
        }
        // delete alliance exchanges that dont belong to an alliance
        for (Map.Entry<Integer, Exchange> entry : exchanges.entrySet()) {
            Exchange exchange = entry.getValue();
            if (exchange.isAlliance()) {
                String name = Locutus.imp().getNationDB().getAllianceName(exchange.id);
                if (name == null) {
                    deleteExchange(exchange);
                }
            }
        }
    }



    public synchronized Map.Entry<Long, Long> getCurrentPrice(int exchange) {
        List<StockTrade> trades = getOpenTradesByCorp(exchange);
        long maxPrice = 0;
        long minPrice = Long.MAX_VALUE;
        for (StockTrade trade : trades) {
            if (trade.buyer == 0) {
                maxPrice = Math.max(trade.price, maxPrice);
            } else if (trade.seller == 0) {
                minPrice = Math.min(trade.price, minPrice);
            }
        }
        return new AbstractMap.SimpleEntry<>(minPrice == Long.MAX_VALUE ? null : minPrice, maxPrice == 0 ? null : maxPrice);
    }

    public synchronized double getCombinedAveragePrice(Exchange exchange, long cutoff) {
        List<StockTrade> trades = getTradesBoughtByCorp(exchange.id, cutoff);

        long amt = 0;
        long value = 0;
        for (StockTrade trade : trades) {
            amt += trade.amount;
            value += trade.price;
        }
        double valueDouble = (amt == 0 ? 0 : (value / (double) amt)) / 100d;
        return valueDouble;
    }

    public synchronized Map.Entry<Double, Double> getAveragePrice(Exchange exchange, long cutoff) {
        List<StockTrade> trades = getTradesBoughtByCorp(exchange.id, cutoff);

        long amtBuy = 0;
        long buyValue = 0;
        long amtSell = 0;
        long sellValue = 0;
        for (StockTrade trade : trades) {
            if (trade.is_buying) {
                amtBuy += trade.amount;
                buyValue += trade.price;
            } else {
                amtSell += trade.amount;
                sellValue += trade.price;
            }
        }
        Double buy = amtBuy == 0 ? null : (buyValue / (double) amtBuy) / 100d;
        Double sell = amtSell == 0 ? null : (sellValue / (double) amtSell) / 100d;
        return new AbstractMap.SimpleEntry<>(buy, sell);
    }

    public synchronized  void addExchangeWithId(Exchange exchange) {
        try (PreparedStatement stmt = getConnection().prepareStatement("INSERT OR REPLACE INTO `exchanges`(`id`, `symbol`, `name`, `description`, `shares`, `owner`, `guild`, `charter`, `website`, `category`, `required_rank`) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, exchange.id);
            stmt.setString(2, exchange.symbol.toLowerCase());
            stmt.setString(3, exchange.name);
            stmt.setString(4, exchange.description);
            stmt.setInt(5, exchange.shares);
            stmt.setInt(6, exchange.owner);
            stmt.setLong(7, exchange.getGuildId());
            stmt.setString(8, exchange.charter);
            stmt.setString(9, exchange.website);
            stmt.setInt(10, exchange.category.ordinal());
            stmt.setInt(11, exchange.requiredRank.id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  void deleteExchange(Exchange company) {
        try (PreparedStatement stmt = getConnection().prepareStatement("DELETE FROM `exchanges` where `id` = ?")) {
            stmt.setInt(1, company.id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  void addExchange(Exchange exchange) {
        try (PreparedStatement stmt = getConnection().prepareStatement("INSERT INTO `exchanges`(`symbol`, `name`, `description`, `shares`, `owner`, `guild`, `charter`, `website`, `category`, `required_rank`) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, exchange.symbol.toLowerCase());
            stmt.setString(2, exchange.name);
            stmt.setString(3, exchange.description);
            stmt.setInt(4, exchange.shares);
            stmt.setInt(5, exchange.owner);
            stmt.setLong(6, exchange.getGuildId());
            stmt.setString(7, exchange.charter);
            stmt.setString(8, exchange.website);
            stmt.setInt(9, exchange.category.ordinal());
            stmt.setInt(10, exchange.requiredRank.id);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Creating corp failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    exchange.id = generatedKeys.getInt(1);
                }
                else {
                    throw new SQLException("Creating corp failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized List<Exchange> getExchangesByOwner(int nation_id) {
        List<Exchange> result = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM exchanges WHERE owner = ?")) {
            stmt.setInt(1, nation_id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Exchange corp = new Exchange(rs);
                    result.add(corp);
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized Exchange getExchange(int company) {
        try (PreparedStatement stmt = prepareQuery("select * FROM exchanges WHERE id = ?")) {
            stmt.setInt(1, company);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Exchange corp = new Exchange(rs);
                    return corp;
                }
            }
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized Exchange getExchange(String name) {
        try (PreparedStatement stmt = prepareQuery("select * FROM exchanges WHERE symbol = ?")) {
            stmt.setString(1, name.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Exchange corp = new Exchange(rs);
                    return corp;
                }
            }
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  Map<Integer, Exchange> getExchangesById() {
        try (PreparedStatement stmt = prepareQuery("select * FROM exchanges")) {
            Map<Integer, Exchange> map = new LinkedHashMap<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Exchange corp = new Exchange(rs);
                    map.put(corp.id, corp);
                }
            }
            return map;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  Map<String, Exchange> getExchanges() {
        try (PreparedStatement stmt = prepareQuery("select * FROM exchanges")) {
            Map<String, Exchange> map = new LinkedHashMap<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Exchange corp = new Exchange(rs);
                    map.put(corp.symbol, corp);
                }
            }
            return map;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  boolean removeZeroedTrades() {
        try (PreparedStatement stmt = getConnection().prepareStatement("DELETE FROM `TRADES` WHERE amount <= 0")) {
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  boolean buyTrade(StockTrade trade, long amt, int nation) {
        return buySellTrade(trade, amt, nation, trade.seller);
    }

    public synchronized  boolean sellTrade(StockTrade trade, long amt, int nation) {
        return buySellTrade(trade, amt, trade.buyer, nation);
    }

    public synchronized  boolean buySellTrade(StockTrade trade, long amt, int buyer, int seller) {
        if (amt <= 0) throw new IllegalArgumentException("Amount is negative");
        if (trade.seller != 0 && trade.buyer != 0) throw new IllegalArgumentException("Trade already finalized");
        if (trade.buyer != buyer && trade.seller != seller) throw new IllegalArgumentException("buyer/seller does not match (1)");
        if (buyer == 0 || seller == 0) throw new IllegalArgumentException("buyer/seller not provided (2)");

        Map<Exchange, Long> buyerShares = getSharesByNation(buyer);
        Map<Exchange, Long> sellerShares = getSharesByNation(seller);

        if (sellerShares.getOrDefault(trade.company, 0L) < amt) {
            if (trade.seller != 0) deleteTrade(trade.tradeId);
            throw new IllegalArgumentException("Seller does not have enough shares");
        }
        long totalPrice = amt * trade.price;
        if (buyerShares.getOrDefault(ResourceType.MONEY.ordinal(), 0L) < totalPrice) {
            if (trade.buyer != 0) deleteTrade(trade.tradeId);
            throw new IllegalArgumentException("Buyer does not have enough money");
        }

        try (PreparedStatement stmt = getConnection().prepareStatement("UPDATE `TRADES` SET amount = amount- ? where amount >= ? and id = ? AND (buyer = 0 or seller = 0)")) {
            stmt.setLong(1, amt);
            stmt.setLong(2, amt);
            stmt.setInt(3, trade.tradeId);

            StockTrade newTrade = new StockTrade(trade);
            newTrade.amount = amt;
            newTrade.buyer = buyer;
            newTrade.seller = seller;
            newTrade.date_bought = System.currentTimeMillis();

            boolean result = stmt.executeUpdate() == 1;
            if (result) {
                if (transferShare(trade.company, trade.seller, trade.buyer, amt)) {
                    if (!transferShare(ResourceType.MONEY.ordinal(), trade.buyer, trade.seller, totalPrice)) {
                        // TODO Halt ALL trading
                        throw new IllegalArgumentException("Failed to transfer money for trade: $" + MathMan.format(totalPrice / 100d) + " | buyer:" + trade.buyer + " seller:" + trade.seller + " | shares:" + amt + " | corp:" + trade.company);
                    }
                } else {
                    throw new IllegalArgumentException("Failed to transfer shares for trade");
                }

                trade.amount -= amt;
                addTrade(newTrade);
                if (trade.amount <= 0) {
                    removeZeroedTrades();
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  boolean updateTrade(StockTrade trade) {
        try (PreparedStatement stmt = getConnection().prepareStatement("UPDATE `TRADES` SET buyer = ?, seller = ?, is_buying = ?, resource = ?, amount = ?, price = ?, date_bought = ? WHERE id = ?")) {
            stmt.setInt(1, trade.buyer);
            stmt.setInt(2, trade.seller);
            stmt.setBoolean(3, trade.is_buying);
            stmt.setInt(4, trade.resource);
            stmt.setLong(5, trade.amount);
            stmt.setLong(6, trade.price);
            if (trade.buyer != 0 && trade.seller != 0 && trade.date_bought == 0) {
                trade.date_bought = System.currentTimeMillis();
            }
            stmt.setLong(7, trade.date_bought);
            stmt.setLong(8, trade.tradeId);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  boolean deleteTrade(int tradeId) {
        try (PreparedStatement stmt = getConnection().prepareStatement("DELETE FROM `TRADES` WHERE id = ?")) {
            stmt.setInt(1, tradeId);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  void addTrade(StockTrade trade) {
        if (trade.seller == 0 && trade.buyer == 0) throw new IllegalArgumentException("No seller or buyer");
        if (trade.seller != 0 && trade.buyer != 0) throw new IllegalArgumentException("Trade offer must have buyer or seller");
        if (trade.is_buying && trade.buyer == 0) throw new IllegalArgumentException("No buyer");
        if (!trade.is_buying && trade.seller == 0) throw new IllegalArgumentException("No seller");
        try (PreparedStatement stmt = getConnection().prepareStatement("INSERT INTO `TRADES`(`company`, `buyer`, `seller`, `is_buying`, `resource`, `amount`, `price`, `date_offered`, `date_bought`) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, trade.company);
            stmt.setInt(2, trade.buyer);
            stmt.setInt(3, trade.seller);
            stmt.setBoolean(4, trade.is_buying);
            stmt.setInt(5, trade.resource);
            stmt.setLong(6, trade.amount);
            stmt.setLong(7, trade.price);
            stmt.setLong(8, trade.date_offered = System.currentTimeMillis());
            stmt.setLong(9, trade.date_bought = 0);
            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Creating trade failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    trade.tradeId = generatedKeys.getInt(1);
                }
                else {
                    throw new SQLException("Creating trade failed, no ID obtained.");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  List<StockTrade> getTrades() {
        try (PreparedStatement stmt = prepareQuery("select * FROM TRADES")) {
            List<StockTrade> list = new ArrayList<StockTrade>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    StockTrade trade = new StockTrade(rs);
                    list.add(trade);
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  List<StockTrade> getOpenTrades() {
        try (PreparedStatement stmt = prepareQuery("select * FROM TRADES where seller = 0 or buyer = 0")) {
            List<StockTrade> list = new ArrayList<StockTrade>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    StockTrade trade = new StockTrade(rs);
                    list.add(trade);
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  List<StockTrade> getTradesByCorp(int exchange) {
        try (PreparedStatement stmt = prepareQuery("select * FROM TRADES WHERE company = ?")) {
            stmt.setInt(1, exchange);
            List<StockTrade> list = new ArrayList<StockTrade>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    StockTrade trade = new StockTrade(rs);
                    list.add(trade);
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  List<StockTrade> getTradesBoughtByCorp(int exchange, long cutoff) {
        try (PreparedStatement stmt = prepareQuery("select * FROM TRADES WHERE company = ? and date_bought > ?")) {
            stmt.setInt(1, exchange);
            stmt.setLong(2, cutoff);
            List<StockTrade> list = new ArrayList<StockTrade>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    StockTrade trade = new StockTrade(rs);
                    list.add(trade);
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  Map.Entry<List<StockTrade>, List<StockTrade>> getBuySellOffersByCorp(int company) {
        List<StockTrade> trades = getOpenTradesByCorp(company);
        List<StockTrade> buy = new ArrayList<>();
        List<StockTrade> sell = new ArrayList<>();
        for (StockTrade trade : trades) {
            if (trade.seller == 0) {
                buy.add(trade);
            } else if (trade.buyer == 0) {
                sell.add(trade);
            }
        }
        return new AbstractMap.SimpleEntry<>(buy, sell);
    }

    public synchronized  List<StockTrade> getOpenBuyTradesByCorp(int company) {
        try (PreparedStatement stmt = prepareQuery("select * FROM TRADES WHERE company = ? AND (seller = 0 or buyer = 0)")) {
            List<StockTrade> list = new ArrayList<StockTrade>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    StockTrade trade = new StockTrade(rs);
                    list.add(trade);
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  List<StockTrade> getOpenTradesByCorp(int company) {
        try (PreparedStatement stmt = prepareQuery("select * FROM TRADES WHERE company = ? AND (seller = 0 or buyer = 0)")) {
            stmt.setInt(1, company);
            List<StockTrade> list = new ArrayList<StockTrade>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    StockTrade trade = new StockTrade(rs);
                    list.add(trade);
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  List<StockTrade> getClosedTradesByNation(int buyerOrSeller, long cutoff) {
        try (PreparedStatement stmt = prepareQuery("select * FROM TRADES WHERE buyer != 0 AND seller != 0 AND date_bought > ? AND (seller = ? OR buyer = ?)")) {
            stmt.setLong(1, cutoff);
            stmt.setInt(2, buyerOrSeller);
            stmt.setInt(3, buyerOrSeller);
            List<StockTrade> list = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new StockTrade(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  List<StockTrade> getTradesBySeller(int seller) {
        try (PreparedStatement stmt = prepareQuery("select * FROM TRADES WHERE seller = ?")) {
            List<StockTrade> list = new ArrayList<StockTrade>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    StockTrade trade = new StockTrade(rs);
                    list.add(trade);
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  Map<Integer, StockTrade> getOpenTrades(int buyerOrSeller) {
        try (PreparedStatement stmt = prepareQuery("select * FROM TRADES WHERE (buyer = ? AND seller = 0) OR (seller = ? AND buyer = 0)")) {
            stmt.setInt(1, buyerOrSeller);
            stmt.setInt(2, buyerOrSeller);
            Map<Integer, StockTrade> list = new HashMap<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    StockTrade trade = new StockTrade(rs);
                    list.put(trade.tradeId, trade);
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  List<StockTrade> getTradesByBuyer(int buyer) {
        try (PreparedStatement stmt = prepareQuery("select * FROM TRADES WHERE buyer = ?")) {
            List<StockTrade> list = new ArrayList<StockTrade>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    StockTrade trade = new StockTrade(rs);
                    list.add(trade);
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  void createShareholder(int nationId, int company) {
        try (PreparedStatement stmt = getConnection().prepareStatement("INSERT OR IGNORE INTO SHAREHOLDERS (`shareholder`, `company`, `shares`) VALUES(?, ?, ?)")) {
            stmt.setInt(1, nationId);
            stmt.setInt(2, company);
            stmt.setLong(3, 0);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  boolean setShares(int nationId, int company, long amt) {
        createShareholder(nationId, company);
        long existing = getSharesByNation(nationId, company);
        try (PreparedStatement stmt = getConnection().prepareStatement("UPDATE SHAREHOLDERS SET `shares` = ? WHERE shareholder = ? AND company = ? and shares = ?")) {
            stmt.setLong(1, amt);
            stmt.setInt(2, nationId);
            stmt.setInt(3, company);
            stmt.setLong(4, existing);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  boolean addShares(int nationId, int company, long amt) {
        createShareholder(nationId, company);
        long existing = getSharesByNation(nationId, company);
        try (PreparedStatement stmt = getConnection().prepareStatement("UPDATE SHAREHOLDERS SET `shares` = `shares` + ? WHERE shareholder = ? AND company = ? and shares = ?")) {
            stmt.setLong(1, amt);
            stmt.setInt(2, nationId);
            stmt.setInt(3, company);
            stmt.setLong(4, existing);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  boolean transferShare(int company, int from, int to, long amt) {
        if (amt <= 0) throw new IllegalArgumentException("Amount is negative");
        createShareholder(from, company);
        createShareholder(to, company);
        long fromShares = getSharesByNation(from, company);
        long toShares = getSharesByNation(to, company);
        if (fromShares < amt) throw new IllegalStateException(from + " only has " + fromShares + " shares");

        String query = "UPDATE SHAREHOLDERS SET shares = ? WHERE shares = ? AND shareholder = ? AND company = ?;" +
                "UPDATE SHAREHOLDERS SET shares = ? WHERE shares = ? AND shareholder = ? AND company = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setLong(1, fromShares - amt);
            stmt.setLong(2, fromShares);
            stmt.setLong(3, from);
            stmt.setLong(4, company);

            stmt.addBatch();
            stmt.setLong(1, toShares + amt);
            stmt.setLong(2, toShares);
            stmt.setLong(3, to);
            stmt.setLong(4, company);
            stmt.addBatch();

            int[] changed = stmt.executeBatch();
            return changed[0] == 1 && changed[1] == 1;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  void updateCompanyTotalShares(int company) {
        try (PreparedStatement stmt = getConnection().prepareStatement("UPDATE exchanges set shares = (SELECT SUM(shares) FROM `shareholders` WHERE company = ?)")) {
            stmt.setInt(1, company);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized long getSharesByNation(int nationId, int company) {
        try (PreparedStatement stmt = prepareQuery("select shares FROM SHAREHOLDERS WHERE shareholder = ? and company = ?")) {
            stmt.setInt(1, nationId);
            stmt.setInt(2, company);
            Map<Integer, Integer> map = new HashMap<Integer, Integer>();
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getLong("shares");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return 0;
    }

    public synchronized  Map<Exchange, Long> getSharesByExchange(Exchange exchange) {
        return getSharesByNation(-exchange.id);
    }

    /**
     *
     * @param nationId
     * @return Map of corp -> num shares
     */
    public synchronized  Map<Exchange, Long> getSharesByNation(int nationId) {
        Map<Integer, Exchange> corps = getExchangesById();
        try (PreparedStatement stmt = prepareQuery("select * FROM SHAREHOLDERS WHERE shareholder = ?")) {
            stmt.setInt(1, nationId);
            Map<Exchange, Long> map = new HashMap<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int companyId = rs.getInt("company");
                    Exchange company = corps.get(companyId);
                    if (company != null) {
                        map.put(company, rs.getLong("shares"));
                    }
                }
            }
            return map;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     *
     * @param company
     * @return Map of nation -> num shares
     */
    public synchronized  Map<Integer, Long> getShareholdersByCorp(int company) {
        try (PreparedStatement stmt = prepareQuery("select * FROM SHAREHOLDERS WHERE company = ?")) {
            stmt.setInt(1, company);
            Map<Integer, Long> map = new HashMap<Integer, Long>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getInt("shareholder"), rs.getLong("shares"));
                }
            }
            return map;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized double convertedTotal(Map<Integer, Double> netOutflows) {
        double total = 0;
        for (Map.Entry<Integer, Double> entry : netOutflows.entrySet()) {
            Exchange exchange = getExchange(entry.getKey());
            if (exchange == null) continue;
            double amtDouble = entry.getValue();
            if (exchange.isResource()) {
                total += PnwUtil.convertedTotal(exchange.getResource(), amtDouble);
            } else {
                Map.Entry<Double, Double> avgBuySell = getAveragePrice(exchange, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7));
                Double value = avgBuySell.getKey();
                if (value == null) value = avgBuySell.getValue();
                if (value == null) value = 0d;
                total += value * amtDouble;
            }
        }

        return total;
    }

    public synchronized String getName(int company) {
        Exchange exchange = getExchange(company);
        return exchange == null ? company + "" : exchange.symbol;
    }

    public synchronized Map<Integer, Rank> getOfficers(int exchange) {
        try (PreparedStatement stmt = prepareQuery("select * FROM ranks WHERE exchange = ?")) {
            stmt.setInt(1, exchange);
            Map<Integer, Rank> map = new HashMap<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int nation = rs.getInt("nation");
                    Rank rank = Rank.byId(rs.getInt("rank"));
                    map.put(nation, rank);
                }
            }
            return map;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized void addOfficer(int exchange, int officer, Rank rank) {
        if (rank.id <= Rank.REMOVE.id) {
            removeOfficer(exchange, officer);
            return;
        }
        try (PreparedStatement stmt = getConnection().prepareStatement("INSERT OR REPLACE INTO `ranks`(`exchange`, `nation`, `rank`) VALUES(?, ?, ?)")) {
            stmt.setInt(1, exchange);
            stmt.setInt(2, officer);
            stmt.setInt(3, rank.id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized Map<DBNation, List<Map.Entry<Exchange, Rank>>> getRanks() {
        Map<Integer, Exchange> exchanges = getExchangesById();
        Map<DBNation, List<Map.Entry<Exchange, Rank>>> result = new HashMap<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM ranks")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int nationId = rs.getInt("nation");
                    Rank rank = Rank.byId(rs.getInt("rank"));
                    int exchangeId = rs.getInt("exchange");
                    Exchange exchange = exchanges.get(exchangeId);
                    DBNation nation = DBNation.byId(nationId);
                    if (exchange != null && nation != null) {
                        result.computeIfAbsent(nation, f -> new ArrayList<>())
                        .add(new AbstractMap.SimpleEntry<>(exchange, rank));
                    }
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized List<Map.Entry<Exchange, Rank>> getRanks(int nation_id) {
        List<Map.Entry<Exchange, Rank>> result = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM ranks WHERE nation = ?")) {
            stmt.setInt(1, nation_id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int nation = rs.getInt("nation");
                    Rank rank = Rank.byId(rs.getInt("rank"));
                    int exchangeId = rs.getInt("exchange");
                    Exchange exchange = getExchange(exchangeId);
                    if (exchange != null) {
                        result.add(new AbstractMap.SimpleEntry<>(exchange, rank));
                    }
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public synchronized  void removeOfficer(int exchange, int officer) {
        try (PreparedStatement stmt = getConnection().prepareStatement("DELETE FROM `ranks` where `exchange` = ? AND `nation` = ?")) {
            stmt.setInt(1, exchange);
            stmt.setInt(2, officer);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public enum ExchangeTransferResult {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        NO_PERMISSION,
        PARTIAL_TRANSFER,
        INVALID_TRANSFER
    }

    public class ExchangeTransfer {
        public NationOrExchange receiver;
        Map<Exchange, Long> amounts = new LinkedHashMap<>();
    }

    public synchronized  Map.Entry<NationOrExchange, Map.Entry<ExchangeTransferResult, String>> transferBulk(DBNation banker, NationOrExchange sender, List<ExchangeTransfer> transfers) {
        Map<Exchange, Long> total = new HashMap<>();

        for (ExchangeTransfer transfer : transfers) {
            NationOrExchange receiver = transfer.receiver;

            for (Map.Entry<Exchange, Long> entry : transfer.amounts.entrySet()) {
                Exchange type = entry.getKey();
                Long amount = entry.getValue();
                if (amount < 0) {
                    throw new IllegalArgumentException("Cannot transfer " + MathMan.format(amount) + " " + type + " to " + receiver.getName());
                }

                if (receiver.isNation()) {
                    DBNation nation = receiver.getNation();
                    if (!type.canView(nation)) throw new IllegalArgumentException(nation.getNation() + " does not have access to " + type.name);
                }
            }


        }

        // 1 check all receivers can receive X
        // get the total
        // check sender has the total
        // check amounts aren't negative
        return null;

    }

    public synchronized List<Map.Entry<Date, Long>> getESVHistory(int exchange) {
        return null;
    }

    public synchronized void setESV(int id, long value) {

    }
}