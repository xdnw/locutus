package link.locutus.discord.db;

import com.politicsandwar.graphql.model.*;
import com.ptsmods.mysqlw.query.QueryCondition;
import com.ptsmods.mysqlw.query.QueryOrder;
import com.ptsmods.mysqlw.query.builder.SelectBuilder;
import com.ptsmods.mysqlw.table.ColumnType;
import com.ptsmods.mysqlw.table.TablePreset;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.bank.TransactionEvent;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.User;

import java.io.File;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class BankDB extends DBMainV2 {

    public BankDB(String name, boolean init) throws SQLException, ClassNotFoundException {
        super(name, init);
        if (name.equalsIgnoreCase("bank") && new File("database/import_bank.db").exists()) {
            System.out.println("Importing external bank recs");
            importFromExternal("import_bank");
            System.out.println("Exporting external bank recs");
            byte[] maxIdData = ByteBuffer.allocate(4).putInt(87004798).array();
            Locutus.imp().getDiscordDB().setInfo(DiscordMeta.BANK_RECS_SEQUENTIAL, 0, maxIdData);
        }
    }

//    public void updateBankRecs(int nationId) {
//        PoliticsAndWarV3 v3 = Locutus.imp().getV3();
//        List<Bankrec> recs = v3.fetchBankRecsWithInfo(new Consumer<BankrecsQueryRequest>() {
//            @Override
//            public void accept(BankrecsQueryRequest r) {
//                r.setOr_id(List.of(nationId));
//                r.setOr_type(List.of(1)); //1 == nation
//            }
//        });
//
//
////        v3.fetchBankRecsWithInfo(new Consumer<BankrecsQueryRequest>() {
////            @Override
////            public void accept(BankrecsQueryRequest r) {
////                r.setMin_id();
////                r.setOr_type();
////            }
////        });
////
////        selectTransactions(s -> {
////            s.order("tx_id", QueryOrder.OrderDirection.DESC);
////            s.limit(1);
////        });
//
////        addTransaction()
////
////        updated = addTransactions(transactions);
//    }


    public List<Transaction2> selectTransactions(Consumer<SelectBuilder> query) {
        List<Transaction2> list = new ArrayList<>();
        SelectBuilder builder = getDb().selectBuilder("TRANSACTIONS_2")
                .select("*");
        if (query != null) query.accept(builder);
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                list.add(new Transaction2(rs));
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void importFromExternal(String fileName) throws SQLException, ClassNotFoundException {
        BankDB otherDb = new BankDB(fileName, false);
        List<Transaction2> transactions = selectTransactions(f -> {
        });
        int batchSize = 10000;
        for (int i = 0; i < transactions.size(); i+= batchSize) {
            List<Transaction2> subList = transactions.subList(i, Math.min(i + batchSize, transactions.size()));
            addTransactions(subList, true);
        }
    }

    @Override
    public void createTables() {
        {
            StringBuilder txTableCreate = new StringBuilder("CREATE TABLE IF NOT EXISTS `TRANSACTIONS_2` (`tx_id` INT NOT NULL PRIMARY KEY, tx_datetime BIGINT NOT NULL, sender_id BIGINT NOT NULL, sender_type INT NOT NULL, receiver_id BIGINT NOT NULL, receiver_type INT NOT NULL, banker_nation_id INT NOT NULL, note varchar");
            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) continue;
                txTableCreate.append(", " + type.name() + " INT NOT NULL");
            }
            txTableCreate.append(")");
            executeStmt(txTableCreate.toString());

            executeStmt("CREATE INDEX IF NOT EXISTS index_sender ON TRANSACTIONS_2 (sender_id, sender_type);");
            executeStmt("CREATE INDEX IF NOT EXISTS index_receiver ON TRANSACTIONS_2 (receiver_id, receiver_type);");
            executeStmt("CREATE INDEX IF NOT EXISTS index_sender_receiver_id ON TRANSACTIONS_2 (receiver_id, sender_id);");
            executeStmt("CREATE INDEX IF NOT EXISTS index_sender_receiver ON TRANSACTIONS_2 (sender_id, sender_type, receiver_id, receiver_type);");
            executeStmt("CREATE INDEX IF NOT EXISTS index_sender_id ON TRANSACTIONS_2 (sender_id);");
            executeStmt("CREATE INDEX IF NOT EXISTS index_sender_type ON TRANSACTIONS_2 (sender_type);");
            executeStmt("CREATE INDEX IF NOT EXISTS index_receiver_id ON TRANSACTIONS_2 (receiver_id);");
            executeStmt("CREATE INDEX IF NOT EXISTS index_receiver_type ON TRANSACTIONS_2 (receiver_type);");
            executeStmt("CREATE INDEX IF NOT EXISTS index_tx_datetime ON TRANSACTIONS_2 (tx_datetime);");
        }

        {
            StringBuilder transactions = new StringBuilder("CREATE TABLE IF NOT EXISTS `TRANSACTIONS_ALLIANCE_2` (`tx_id` INTEGER PRIMARY KEY AUTOINCREMENT, tx_datetime BIGINT NOT NULL, sender_id BIGINT NOT NULL, sender_type INT NOT NULL, receiver_id BIGINT NOT NULL, receiver_type INT NOT NULL, banker_nation_id INT NOT NULL, note varchar");
            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) continue;
                transactions.append(", " + type.name() + " INT NOT NULL");
            }
            transactions.append(")");

            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(transactions.toString());
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            executeStmt("CREATE INDEX IF NOT EXISTS index_sender_id ON TRANSACTIONS_ALLIANCE_2 (sender_id);");
            executeStmt("CREATE INDEX IF NOT EXISTS index_sender_type ON TRANSACTIONS_ALLIANCE_2 (sender_type);");
            executeStmt("CREATE INDEX IF NOT EXISTS index_receiver_id ON TRANSACTIONS_ALLIANCE_2 (receiver_id);");
            executeStmt("CREATE INDEX IF NOT EXISTS index_receiver_type ON TRANSACTIONS_ALLIANCE_2 (receiver_type);");
            executeStmt("CREATE INDEX IF NOT EXISTS index_tx_datetime ON TRANSACTIONS_ALLIANCE_2 (tx_datetime);");
        }
//        {
//            String transactions = "CREATE TABLE IF NOT EXISTS `TRANSACTIONS_BANK` (`date` INT NOT NULL, note VARCHAR, bank INT NOT NULL, receiver INT NOT NULL, banker INT NOT NULL, resource INT NOT NULL, amount REAL NOT NULL, PRIMARY KEY(date, bank, receiver, banker, resource, amount))";
//            try (Statement stmt = getConnection().createStatement()) {
//                stmt.addBatch(transactions);
//                stmt.executeBatch();
//                stmt.clearBatch();
//            } catch (SQLException e) {
//                e.printStackTrace();
//            }
//        };
        {
            String query = "CREATE TABLE IF NOT EXISTS `SUBSCRIPTIONS` (`user` BIGINT NOT NULL, `allianceOrNation` INT NOT NULL, `isNation` INT NOT NULL, `date` BIGINT NOT NULL, `isReceive` INT NOT NULL, `amount` BIGINT NOT NULL, PRIMARY KEY(user, allianceOrNation, isNation))";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(query);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        {
            String query = "CREATE TABLE IF NOT EXISTS `TAX_BRACKETS` (`id` INT NOT NULL, `money` INT NOT NULL, `resources` INT NOT NULL, `date` BIGINT NOT NULL, PRIMARY KEY(id))";
            executeStmt(query);
            try (PreparedStatement stmt = getConnection().prepareStatement("ALTER TABLE TAX_BRACKETS ADD COLUMN `date` BIGINT NOT NULL DEFAULT 0")){
                stmt.executeUpdate();
            } catch (SQLException ignore) {}
        }
        {
            String query = "CREATE TABLE IF NOT EXISTS `TAX_DEPOSITS_DATE` (`tax_id` INT NOT NULL DEFAULT (-1), `alliance` INT NOT NULL, `date` BIGINT NOT NULL, `id` INT NOT NULL, `nation` INT NOT NULL, `moneyrate` INT NOT NULL, `resoucerate` INT NOT NULL, `resources` BLOB NOT NULL, `internal_taxrate` INT NOT NULL DEFAULT (-1), PRIMARY KEY(alliance, date, nation))";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(query);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            executeStmt("CREATE INDEX IF NOT EXISTS index_tax_deposits_nation ON TAX_DEPOSITS_DATE (nation);");
        }
        {
            String query = TablePreset.create("loot_diff_by_tax_id")
                    .putColumn("nation_id", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("tax_id", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("date", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("resources", ColumnType.BINARY.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .buildQuery(getDb().getType());
            query = query.replace(");", ", PRIMARY KEY(nation_id, tax_id));");
            getDb().executeUpdate(query);

            TablePreset.create("tax_estimate")
                    .putColumn("tax_id", ColumnType.INT.struct().setPrimary(true).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("min_cash", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("max_cash", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("min_rss", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("max_rss", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .create(getDb())
            ;
        }
    }

    // add tax estimate
    public void addTaxEstimate(int taxId, int minCash, int maxCash, int minRss, int maxRss) {
        update("INSERT INTO tax_estimate (tax_id, min_cash, max_cash, min_rss, max_rss) VALUES (?, ?, ?, ?, ?)", taxId, minCash, maxCash, minRss, maxRss);
    }

    public Map<Integer, TaxEstimate> getTaxEstimates() {
        Map<Integer, TaxEstimate> result = new Int2ObjectOpenHashMap<>();
        query("SELECT * FROM tax_estimate", f -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                TaxEstimate estimate = new TaxEstimate();
                estimate.tax_id = (rs.getInt("tax_id"));
                estimate.min_cash = (rs.getInt("min_cash"));
                estimate.max_cash = (rs.getInt("max_cash"));
                estimate.min_rss = (rs.getInt("min_rss"));
                estimate.max_rss = (rs.getInt("max_rss"));
                result.put(estimate.tax_id, estimate);
            }
        });
        return result;
    }

    public Transaction2 getLatestTransaction() {
        List<Transaction2> latestList = selectTransactions(query -> query.order("id", QueryOrder.OrderDirection.DESC).limit(1));
        return latestList.isEmpty() ? null : latestList.get(0);
    }

    public void updateBankRecs(int nationId, Consumer<Event> eventConsumer) {
        PoliticsAndWarV3 v3 = Locutus.imp().getV3();

        List<Transaction2> latestTx = getTransactionsByNation(nationId, 1);
        int minId = latestTx.size() == 1 ? latestTx.get(0).tx_id : 0;
        List<Bankrec> bankRecs = v3.fetchBankRecsWithInfo(new Consumer<BankrecsQueryRequest>() {
            @Override
            public void accept(BankrecsQueryRequest request) {
                request.setOr_type(List.of(1));
                request.setOr_id(List.of(nationId));
                if (minId > 0) request.setMin_id(minId + 1);
            }
        });

        saveBankRecs(bankRecs, eventConsumer);
    }

    public void updateBankRecs(Consumer<Event> eventConsumer) {
        ByteBuffer info = Locutus.imp().getDiscordDB().getInfo(DiscordMeta.BANK_RECS_SEQUENTIAL, 0);
        int latestId = info == null ? -1 : info.getInt();

        PoliticsAndWarV3 v3 = Locutus.imp().getV3();

        List<Bankrec> records = new ArrayList<>();
        Runnable saveTransactions = () -> {
            if (records.isEmpty()) return;
            List<Bankrec> copy = new ArrayList<>(records);
            records.clear();
            int maxId = copy.stream().mapToInt(Bankrec::getId).max().getAsInt();
            saveBankRecs(copy, eventConsumer);

            byte[] maxIdData = ByteBuffer.allocate(4).putInt(maxId).array();
            Locutus.imp().getDiscordDB().setInfo(DiscordMeta.BANK_RECS_SEQUENTIAL, 0, maxIdData);
        };
        v3.fetchBankRecs(new Consumer<BankrecsQueryRequest>() {
            @Override
            public void accept(BankrecsQueryRequest f) {
                f.setOr_type(List.of(1));
                if (latestId > 0) f.setMin_id(latestId + 1);
                f.setOrderBy(List.of(new QueryBankrecsOrderByOrderByClause(QueryBankrecsOrderByColumn.ID, SortOrder.ASC, null)));
            }
        }, v3.createBankRecProjection(), new Predicate<Bankrec>() {
            @Override
            public boolean test(Bankrec bankrec) {
                records.add(bankrec);
                if (records.size() > 1000) {
                    System.out.println("MIN " + records.get(0).getId());
                    saveTransactions.run();
                }
                return false;
            }
        });
        saveTransactions.run();
    }

    public void saveBankRecs(List<Bankrec> bankrecs, Consumer<Event> eventConsumer) {
        List<Transaction2> transfers = new ArrayList<>();
        for (Bankrec bankrec : bankrecs) {
            if (bankrec.getSender_type() != 1 && bankrec.getReceiver_type() != 1) {
                throw new UnsupportedOperationException("Alliance -> alliance transfers are not currently supported");
            }
            Transaction2 tx = Transaction2.fromApiV3(bankrec);
            transfers.add(tx);
        }
        Collections.sort(transfers, Comparator.comparingLong(Transaction2::getDate));
        int[] modified = addTransactions(transfers, true);
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
        if (eventConsumer != null) {
            for (int i = 0; i < modified.length; i++) {
                if (modified[i] > 0) {
                    Transaction2 tx = transfers.get(i);
                    if (tx.tx_datetime > cutoff) {
                        eventConsumer.accept(new TransactionEvent(tx));
                    }
                }
            }
        }
    }

//    public void addLoan(DBLoan loan) {
//        addTask(TaskType.LOAN, pair, new UniqueStatement(pair) {
//            @Override
//            public PreparedStatement get() throws SQLException {
//                return getConnection().prepareStatement("INSERT OR IGNORE INTO `TAX_DEPOSITS_DATE` (`alliance`, `date`, `id`, `nation`, `moneyrate`, `resoucerate`, `resources`) VALUES(?, ?, ?, ?, ?, ?, ?)");
//            }
//
//            @Override
//            public void set(PreparedStatement stmt) throws SQLException {
//                stmt.setInt(1, allianceId);
//                stmt.setLong(2, date);
//                stmt.setInt(3, taxIndex);
//                stmt.setInt(4, nation);
//                stmt.setInt(5, moneyRate);
//                stmt.setInt(6, rssRate);
//                stmt.setBytes(7, depositBytes);
//            }
//        });
//    }

    public void addTaxDeposit(TaxDeposit record) {
        addTaxDeposit(record.allianceId, record.date, record.index, record.nationId, (int) record.moneyRate, (int) record.resourceRate, record.internalMoneyRate, record.internalResourceRate, record.resources);
    }

    public List<Transaction2> getAllTransactions(NationOrAlliance sender, NationOrAlliance receiver, NationOrAlliance banker, long timeframe) {
        boolean checkNation = (sender != null && sender.isNation()) || (receiver != null && receiver.isNation()) || (sender == null && receiver == null);
        boolean checkAlliance = !checkNation || (sender == null && receiver == null);
        if (sender == null && receiver == null && banker == null) return Collections.emptyList();

        String query = "SELECT * FROM %table% WHERE tx_datetime > ?";
        if (sender != null) {
            query += " AND sender_id = " + sender.getIdLong();
            query += " AND sender_type = " + sender.getReceiverType();
        }
        if (receiver != null) {
            query += " AND receiver_id = " + receiver.getIdLong();
            query += " AND receiver_type = " + receiver.getReceiverType();
        }
        if (banker != null) {
            query += "banker_nation_id = " + banker;
        }

        List<Transaction2> results = new ArrayList<>();
        if (checkNation) {
            // tx_datetime INT NOT NULL, sender_id INT NOT NULL, sender_type INT NOT NULL, receiver_id INT NOT NULL, receiver_type INT NOT NULL, banker_nation_id INT NOT NULL, note varchar
            String queryNation = query.replaceFirst("%table%", "TRANSACTIONS_2");

            query(queryNation,
                    (ThrowingConsumer<PreparedStatement>) elem -> elem.setLong(1, timeframe),
                    (ThrowingConsumer<ResultSet>) elem -> results.add(new Transaction2(elem))
            );
        }
        if (checkAlliance) {
            String queryAA = query.replaceFirst("%table%", "TRANSACTIONS_ALLIANCE_2");
            query(queryAA,
                    (ThrowingConsumer<PreparedStatement>) elem -> elem.setLong(1, timeframe),
                    (ThrowingConsumer<ResultSet>) elem -> results.add(new Transaction2(elem))
            );
        }
        return results;
    }

    public static class TaxDeposit {
        public int allianceId;
        public long date;
        public int index;
        public int nationId;
        public int moneyRate;
        public int resourceRate;
        public double[] resources;
        public int internalMoneyRate;
        public int internalResourceRate;
        public int tax_id;

        public TaxDeposit(int allianceId, long date, int index, int tax_id, int nationId, int moneyRate, int resourceRate, int internalMoneyRate, int internalResourceRate, double[] resources) {
            this.allianceId = allianceId;
            this.date = date;
            this.index = index;
            this.nationId = nationId;
            this.moneyRate = moneyRate;
            this.resourceRate = resourceRate;
            this.resources = resources;
            this.internalMoneyRate = internalMoneyRate;
            this.internalResourceRate = internalResourceRate;
            this.tax_id = tax_id;
        }

        public static TaxDeposit of(ResultSet rs) throws SQLException {
            int money = rs.getInt("moneyrate");
            int rss = rs.getInt("resoucerate");
            int id = rs.getInt("id");
            long date = rs.getLong("date");
            // round date for legacy reasons
            if (date > 1656153134000L && date < 1657449182000L) {
                date = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(date));
            }

            long[] cents = ArrayUtil.toLongArray(rs.getBytes("resources"));
            double[] deposit = new double[cents.length];
            for (int i = 0; i < cents.length; i++) deposit[i] = cents[i] / 100d;

            int alliance = rs.getInt("alliance");
            int nation = rs.getInt("nation");

            short internalTaxRatePair = rs.getShort("internal_taxrate");

            byte internalMoneyRate = MathMan.unpairShortX(internalTaxRatePair);
            byte internalResourceRate = MathMan.unpairShortY(internalTaxRatePair);

            int tax_id = rs.getInt("tax_id");

            return new TaxDeposit(alliance, date, id, tax_id, nation, money, rss, internalMoneyRate, internalResourceRate, deposit);
        }

        /**
         * Money added to deposits
         * @param taxBase
         * @return
         */
        public double getPctMoney(int[] taxBase) {
            return getPct(moneyRate, taxBase[0]);
        }

        /**
         * Rss added to deposits
         * @param taxBase
         * @return
         */
        public double getPctResource(int[] taxBase) {
            return getPct(resourceRate, taxBase[1]);
        }

        private double getPct(double rate, int taxBase){
            return (rate > taxBase ?
                    Math.max(0, (rate - taxBase) / rate)
                    : 0);
        }

        /**
         * Remainder after subtracting tax base
         * @param taxBase
         */
        public void multiplyBase(int[] taxBase) {
            double pctMoney = getPctMoney(taxBase);
            double pctRss = getPctResource(taxBase);
            resources[0] *= pctMoney;
            for (int i = 1; i < resources.length; i++) {
                resources[i] *= pctRss;
            }
        }

        public void multiplyBaseInverse(int[] taxBase) {
            double pctMoney = 1 - getPctMoney(taxBase);
            double pctRss = 1 - getPctResource(taxBase);
            resources[0] *= pctMoney;
            for (int i = 1; i < resources.length; i++) {
                resources[i] *= pctRss;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TaxDeposit that = (TaxDeposit) o;

            if (allianceId != that.allianceId) return false;
            if (date != that.date) return false;
            if (nationId != that.nationId) return false;
            if (Double.compare(that.moneyRate, moneyRate) != 0) return false;
            if (Double.compare(that.resourceRate, resourceRate) != 0) return false;
            return Arrays.equals(resources, that.resources);
        }

        @Override
        public int hashCode() {
            int result;
            long temp;
            result = allianceId;
            result = 31 * result + (int) (date ^ (date >>> 32));
            result = 31 * result + nationId;
            temp = Double.doubleToLongBits(moneyRate);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(resourceRate);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            result = 31 * result + Arrays.hashCode(resources);
            return result;
        }

        @Override
        public String toString() {
            return "TaxDeposit{" +
                    "allianceId=" + allianceId +
                    ", date=" + date +
                    ", nationId=" + nationId +
                    ", moneyRate=" + moneyRate +
                    ", resourceRate=" + resourceRate +
                    ", resources=" + Arrays.toString(resources) +
                    '}';
        }

        public long getTurn() {
            return TimeUtil.getTurn(date);
        }
    }

    public List<Transaction2> getTransactionsByBySenderOrReceiver(Set<Long> senders, Set<Long> receivers, long minDateMs) {
        List<Transaction2> list = new ArrayList<>();
        query("select * FROM TRANSACTIONS_2 WHERE tx_datetime > ? AND sender_id in " + StringMan.getString(senders) + " AND receiver_id in " + StringMan.getString(receivers) + " ORDER BY tx_id DESC",
                (ThrowingConsumer<PreparedStatement>) stmt -> stmt.setLong(1, minDateMs),
                (ThrowingConsumer<ResultSet>) rs -> {
                    while (rs.next()) {
                        list.add(new Transaction2(rs));
                    }
                });
        return list;
    }

    public List<Transaction2> getTransactionsByBySender(Set<Long> senders, long minDateMs) {
        List<Transaction2> list = new ArrayList<>();
        query("select * FROM TRANSACTIONS_2 WHERE tx_datetime > ? AND sender_id in " + StringMan.getString(senders) + " ORDER BY tx_id DESC",
                (ThrowingConsumer<PreparedStatement>) stmt -> stmt.setLong(1, minDateMs),
                (ThrowingConsumer<ResultSet>) rs -> {
                    while (rs.next()) {
                        list.add(new Transaction2(rs));
                    }
                });
        return list;
    }

    public List<Transaction2> getTransactionsByByReceiver(Set<Long> receivers, long minDateMs) {
        List<Transaction2> list = new ArrayList<>();
        query("select * FROM TRANSACTIONS_2 WHERE tx_datetime > ? AND receiver_id in " + StringMan.getString(receivers) + " ORDER BY tx_id DESC",
                (ThrowingConsumer<PreparedStatement>) stmt -> stmt.setLong(1, minDateMs),
                (ThrowingConsumer<ResultSet>) rs -> {
                    while (rs.next()) {
                        list.add(new Transaction2(rs));
                    }
                });
        return list;
    }

    public List<Transaction2> getTransactions(long minDateMs, boolean desc) {
        List<Transaction2> list = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM TRANSACTIONS_2 WHERE tx_datetime > ? ORDER BY tx_id " + (desc ? "DESC" : "ASC"))) {
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

    public List<Transaction2> getToNationTransactions(long minDateMs) {
        ArrayList<Transaction2> list = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM TRANSACTIONS_2 WHERE tx_datetime > ? AND sender_type = 2 ORDER BY tx_id DESC")) {
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
    public List<Transaction2> getNationTransfers(int nationId, long minDateMs) {
            ArrayList<Transaction2> list = new ArrayList<>();
            try (PreparedStatement stmt = prepareQuery("select * FROM TRANSACTIONS_2 WHERE tx_datetime > ? AND ((receiver_id = ? AND receiver_type = 1) OR (sender_id = ? AND sender_type = 1)) ORDER BY tx_id DESC")) {
            stmt.setLong(1, minDateMs);
            stmt.setInt(2, nationId);
            stmt.setInt(3, nationId);
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

    public Map<Integer, List<Transaction2>> getNationTransfersByNation(long minDateMs, Set<Integer> nationIds) {
        Map<Integer, List<Transaction2>> result = new HashMap<>();

        String idStr = StringMan.getString(nationIds);

        try (PreparedStatement stmt = prepareQuery("select * FROM TRANSACTIONS_2 WHERE tx_datetime > ? AND ((receiver_id in " + idStr + " AND receiver_type = 1) OR (sender_id in " + idStr + " AND sender_type = 1))  ORDER BY tx_id DESC")) {
            stmt.setLong(1, minDateMs);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Transaction2 transfer = new Transaction2(rs);
                    int nationId = (int) (transfer.sender_type == 1 ? transfer.sender_id : transfer.receiver_id);
                    List<Transaction2> list = result.get(nationId);
                    if (list == null) {
                        result.put(nationId, list = new ArrayList<>());
                    }
                    list.add(transfer);
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
//
    public List<Transaction2> getAllianceTransfers(int allianceId, long minDateMs) {
            ArrayList<Transaction2> list = new ArrayList<>();
            try (PreparedStatement stmt = prepareQuery("select * FROM TRANSACTIONS_2 WHERE tx_datetime > ? AND ((receiver_id = ? AND receiver_type == 2) OR (sender_id = ? AND sender_type == 2)) ORDER BY tx_id DESC")) {
            stmt.setLong(1, minDateMs);
            stmt.setInt(2, allianceId);
            stmt.setInt(3, allianceId);
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

    public int getTransactionsByNationCount(int nation) {
        List<Transaction2> list = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select COUNT(*) as `num_rows` FROM TRANSACTIONS_2 WHERE (sender_id = ? AND sender_type = 1) OR (receiver_id = ? AND receiver_type = 1)")) {
            stmt.setInt(1, nation);
            stmt.setInt(2, nation);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.getInt("num_rows");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public List<Transaction2> getTransactionsByBanker(int nation) {
        List<Transaction2> list = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM TRANSACTIONS_2 WHERE banker_nation_id = ?")) {
            stmt.setInt(1, nation);
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

    private SoftReference<Map.Entry<Integer, List<Transaction2>>> txNationCache = null;
    private void invalidateTXCache() {
        txNationCache = null;
    }

    public List<Transaction2> getTransactionsByNation(int nation) {
        return getTransactionsByNation(nation, -1);
    }

    public List<Transaction2> getTransactionsByNation(int nation, int limit) {
        Reference<Map.Entry<Integer, List<Transaction2>>> tmp = txNationCache;
        Map.Entry<Integer, List<Transaction2>> cached = tmp == null ? null : tmp.get();
        if (cached != null && cached.getKey() == nation) {
            return cached.getValue();
        }
        List<Transaction2> list = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM TRANSACTIONS_2 WHERE (sender_id = ? AND sender_type = 1) OR (receiver_id = ? AND receiver_type = 1)" + (limit > 0 ? " ORDER BY tx_id DESC LIMIT " + limit : ""))) {
            stmt.setInt(1, nation);
            stmt.setInt(2, nation);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Transaction2(rs));
                }
            }
            txNationCache = new SoftReference<>(new AbstractMap.SimpleEntry<>(nation, new ArrayList<>(list)));
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to fetch transactions " + e.getMessage());
        }
    }

    public List<Transaction2> getTransactionsByNote(String note) {
        List<Transaction2> list = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM TRANSACTIONS_2 WHERE lower(note) like ?")) {
            stmt.setString(1, ("%" + note + "%").toLowerCase());
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

    public List<Transaction2> getTransactionsByAllianceSender(int alliance_id) {
        List<Transaction2> list = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM TRANSACTIONS_2 WHERE (sender_id = ? AND sender_type = 2)")) {
            stmt.setInt(1, alliance_id);
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

    public List<Transaction2> getTransactionsByAllianceReceiver(int alliance_id) {
        List<Transaction2> list = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM TRANSACTIONS_2 WHERE (receiver_id = ? AND receiver_type = 2)")) {
            stmt.setInt(1, alliance_id);
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

    public List<Transaction2> getTransactionsByAlliance(int alliance_id) {
        List<Transaction2> list = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM TRANSACTIONS_2 WHERE (sender_id = ? AND sender_type = 2) OR (receiver_id = ? AND receiver_type = 2)")) {
            stmt.setInt(1, alliance_id);
            stmt.setInt(2, alliance_id);
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

    private int[] addTransactions(List<Transaction2> transactions, boolean ignoreInto) {
        if (transactions.isEmpty()) return new int[0];
        invalidateTXCache();
        String query = transactions.get(0).createInsert("TRANSACTIONS_2", true, ignoreInto);
        return executeBatch(transactions, query, (ThrowingBiConsumer<Transaction2, PreparedStatement>) Transaction2::set);
    }

    public int addTransaction(Transaction2 tx, boolean ignoreInto) {
        invalidateTXCache();
        String sql = tx.createInsert("TRANSACTIONS_2", true, ignoreInto);
        return update(sql, (ThrowingConsumer<PreparedStatement>) tx::set);
    }

    public List<TaxDeposit> getTaxesPaid(int nation, int alliance) {
        List<TaxDeposit> list = new ArrayList<>();
        query("select * FROM TAX_DEPOSITS_DATE WHERE alliance = ? AND nation = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, alliance);
            stmt.setInt(2, nation);
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                list.add(TaxDeposit.of(rs));
            }
        });
        return list;
    }

    public List<TaxDeposit> getTaxesByBracket(int tax_id) {
        List<TaxDeposit> list = new ArrayList<>();
        query("select * FROM TAX_DEPOSITS_DATE WHERE tax_id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, tax_id);
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                list.add(TaxDeposit.of(rs));
            }
        });
        return list;
    }

    public List<TaxDeposit> getTaxesByBracket(int tax_id, long afterDate) {
        List<TaxDeposit> list = new ArrayList<>();
        query("select * FROM TAX_DEPOSITS_DATE WHERE tax_id = ? AND date > ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, tax_id);
            stmt.setLong(2, afterDate);
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                list.add(TaxDeposit.of(rs));
            }
        });
        return list;
    }

    public List<TaxDeposit> getTaxesPaid(int nation) {
        List<TaxDeposit> list = new ArrayList<>();

        query("select * FROM TAX_DEPOSITS_DATE WHERE nation = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nation);
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                list.add(TaxDeposit.of(rs));
            }
        });
        return list;
    }

    public List<TaxDeposit> getTaxesByAA(int alliance) {
        List<TaxDeposit> list = new ArrayList<>();

        query("select * FROM TAX_DEPOSITS_DATE WHERE alliance = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, alliance);
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                list.add(TaxDeposit.of(rs));
            }
        });
        return list;
    }

    public List<TaxDeposit> getTaxDeposits(Consumer<SelectBuilder> query) {
        List<TaxDeposit> list = new ArrayList<>();
        SelectBuilder builder = getDb().selectBuilder("TAX_DEPOSITS_DATE")
                .select("*");
        if (query != null) query.accept(builder);
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                list.add(TaxDeposit.of(rs));
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public TaxDeposit getTaxDeposits(int allianceId) {
        List<TaxDeposit> result = getTaxDeposits(s -> {
            s.where(QueryCondition.equals("alliance", allianceId));
            s.order("date", QueryOrder.OrderDirection.ASC);
        });
        return result.size() == 1 ? result.get(0) : null;
    }

    public TaxDeposit getLatestTaxDeposit(int allianceId) {
        List<TaxDeposit> result = getTaxDeposits(new Consumer<SelectBuilder>() {
            @Override
            public void accept(SelectBuilder s) {
                s.where(QueryCondition.equals("alliance", allianceId));
                s.order("date", QueryOrder.OrderDirection.DESC);
                s.limit(1);
            }
        });
        return result.size() == 1 ? result.get(0) : null;
    }

    public List<TaxDeposit> getTaxesByTurn(int alliance) {
        List<TaxDeposit> list = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM TAX_DEPOSITS_DATE WHERE alliance = ? order by DATE ASC")) {
            stmt.setInt(1, alliance);
            try (ResultSet rs = stmt.executeQuery()) {
                TaxDeposit turnTotal = null;

                double moneyRateDouble = 0;
                double rssRateDouble = 0;
                double intMoneyRateDouble = 0;
                double intRssRateDouble = 0;

                int i = 0;
                while (rs.next()) {
                    i++;
                    TaxDeposit nextDeposit = TaxDeposit.of(rs);

                    if (turnTotal == null) {
                        i = 1;
                        turnTotal = nextDeposit;
                        moneyRateDouble = turnTotal.moneyRate;
                        rssRateDouble = turnTotal.resourceRate;
                        intMoneyRateDouble = turnTotal.internalMoneyRate;
                        intRssRateDouble = turnTotal.internalResourceRate;
                    }
                    else if (Math.abs(turnTotal.date - nextDeposit.date) > 5 * 60 * 1000) {
                        i = 1;
                        turnTotal.moneyRate = (int) moneyRateDouble;
                        turnTotal.resourceRate = (int) rssRateDouble;
                        turnTotal.internalMoneyRate = (int) intMoneyRateDouble;
                        turnTotal.internalResourceRate = (int) intRssRateDouble;
                        list.add(turnTotal);
                        turnTotal = nextDeposit;
                    } else {
                        moneyRateDouble = ((moneyRateDouble * (i - 1d) + nextDeposit.moneyRate) / i);
                        rssRateDouble = ((rssRateDouble * (i - 1d) + nextDeposit.resourceRate) / i);
                        intMoneyRateDouble = ((intMoneyRateDouble * (i - 1d) + nextDeposit.internalMoneyRate) / i);
                        intRssRateDouble = ((intRssRateDouble * (i - 1d) + nextDeposit.internalResourceRate) / i);

                        ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, turnTotal.resources, nextDeposit.resources, false);
                    }
                }
                if (turnTotal != null) list.add(turnTotal);
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void deleteTaxDeposits(int allianceId, long date) {
        update("DELETE FROM `TAX_DEPOSITS_DATE` where alliance = ? AND date >= ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, allianceId);
            stmt.setLong(2, date);
        });

    }

    public void addTaxDeposits(Collection<TaxDeposit> records) {
        String query = "INSERT OR IGNORE INTO `TAX_DEPOSITS_DATE` (`alliance`, `date`, `id`, `nation`, `moneyrate`, `resoucerate`, `resources`, `internal_taxrate`, `tax_id`) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)";
        executeBatch(records, query, new ThrowingBiConsumer<TaxDeposit, PreparedStatement>() {
            @Override
            public void acceptThrows(TaxDeposit record, PreparedStatement stmt) throws SQLException {
                stmt.setInt(1, record.allianceId);

                long dateRounded = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(record.date));

                stmt.setLong(2, dateRounded);
                stmt.setInt(3, record.index);
                stmt.setInt(4, record.nationId);
                stmt.setInt(5, (int) record.moneyRate);
                stmt.setInt(6, (int) record.resourceRate);

                double[] deposit = record.resources;
                long[] depositCents = new long[deposit.length];
                for (int i = 0; i < deposit.length; i++) depositCents[i] = (long) (deposit[i] * 100);
                byte[] depositBytes = ArrayUtil.toByteArray(depositCents);

                stmt.setBytes(7, depositBytes);

                short internalPair = MathMan.pairByte(record.internalMoneyRate, record.internalResourceRate);
                stmt.setShort(8, internalPair);
                stmt.setInt(9, record.tax_id);
            }
        });
    }

    public void addTaxDeposit(int allianceId, long date, int taxIndex, int nation, int moneyRate, int rssRate, int internalMoneyRate, int internalResourceRate, double[] deposit) {
        long[] depositCents = new long[deposit.length];
        for (int i = 0; i < deposit.length; i++) depositCents[i] = (long) (deposit[i] * 100);
        byte[] depositBytes = ArrayUtil.toByteArray(depositCents);

        update("INSERT OR IGNORE INTO `TAX_DEPOSITS_DATE` (`alliance`, `date`, `id`, `nation`, `moneyrate`, `resoucerate`, `resources`, `internal_taxrate`) VALUES(?, ?, ?, ?, ?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, allianceId);
            stmt.setLong(2, date);
            stmt.setInt(3, taxIndex);
            stmt.setInt(4, nation);
            stmt.setInt(5, moneyRate);
            stmt.setInt(6, rssRate);
            stmt.setBytes(7, depositBytes);
            stmt.setShort(8, MathMan.pairByte(internalMoneyRate, internalResourceRate));
        });
    }

    public void clearTaxDeposits(int allianceId) {
        update("DELETE FROM `TAX_DEPOSITS_DATE` WHERE `alliance` = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, allianceId);
        });
    }

    public Map<Integer, TaxBracket> getTaxBracketsFromDeposits() {
        Map<Integer, TaxBracket> rates = new HashMap<>();
        query("SELECT tax_id, MAX(date), `money`, `resources`, `alliance` FROM `TAX_DEPOSITS_DATE` GROUP BY `tax_id`, `money`, `resources`", f -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                int id = rs.getInt(1);
                long date = rs.getLong(2);
                int money = rs.getInt(3);
                int rss = rs.getInt(4);
                int alliance = rs.getInt(5);

                TaxBracket existing = rates.get(id);
                if (existing == null || existing.dateFetched < date) {
                    existing = new TaxBracket(id, alliance, "", money, rss, date);
                    rates.put(id, existing);
                }
            }
        });
        return rates;
    }

    public Map<Integer, TaxBracket> getTaxBracketsAndEstimates() {
        return getTaxBracketsAndEstimates(true, true, true);
    }
    public Map<Integer, TaxBracket> getTaxBracketsAndEstimates(boolean allowDeposits, boolean allowApi, boolean addUnknownBrackets) {
        Map<Integer, TaxBracket> rates = new HashMap<>();

        Map<Integer, Integer> taxIdByAlliances = new HashMap<>();
        // add date to tax record
        List<Map.Entry<Integer, TaxBracket>> bracketEntries = new ArrayList<>();

        if (allowDeposits) bracketEntries.addAll(getTaxBracketsFromDeposits().entrySet());
        if (allowApi) bracketEntries.addAll(getTaxBrackets(taxIdByAlliances).entrySet());
//        if (allowSuggestions) bracketEntries.addAll(getTaxBracketSuggestions(taxIdByAlliances).entrySet());

        for (Map.Entry<Integer, TaxBracket> entry : bracketEntries) {
            TaxBracket bracket = entry.getValue();
            TaxBracket existing = rates.get(bracket.taxId);
            if (existing == null || existing.dateFetched < bracket.dateFetched) {
                rates.put(bracket.taxId, bracket);
            }
        }

        if (addUnknownBrackets) {
            if (taxIdByAlliances.isEmpty()) {
                taxIdByAlliances.putAll(Locutus.imp().getNationDB().getAllianceIdByTaxId());
            }
            for (Map.Entry<Integer, Integer> entry : taxIdByAlliances.entrySet()) {
                int taxId = entry.getKey();
                int allianceId = entry.getValue();
                if (!rates.containsKey(taxId)) {
                    rates.put(taxId, new TaxBracket(taxId, allianceId, "", -1, -1, 0));
                }
            }

        }

        return rates;
    }

    public Map<Integer, TaxBracket> getTaxBrackets() {
        return getTaxBrackets(new HashMap<>());
    }

    public Map<Integer, TaxBracket> getTaxBrackets(Map<Integer, Integer> alliancesByTaxId) {
        if (alliancesByTaxId.isEmpty()) alliancesByTaxId.putAll(Locutus.imp().getNationDB().getAllianceIdByTaxId());

        Map<Integer, TaxBracket> result = new HashMap<>();

        try (PreparedStatement stmt = prepareQuery("select * FROM TAX_BRACKETS")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    int money = rs.getInt("money");
                    int rss = rs.getInt("resources");
                    long date = rs.getLong("date");
                    int allianceId = alliancesByTaxId.getOrDefault(id, 0);
                    result.put(id, new TaxBracket(id, allianceId, "", money, rss, date));
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void addTaxBracket(TaxBracket bracket) {
        update("INSERT OR REPLACE INTO `TAX_BRACKETS`(`id`, `money`, `resources`, `date`) VALUES(?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, bracket.taxId);
            stmt.setInt(2, bracket.moneyRate);
            stmt.setInt(3, bracket.rssRate);
            stmt.setLong(4, bracket.dateFetched);
        });
    }

    public synchronized void purgeSubscriptions() {
        long now = System.currentTimeMillis();
        update("DELETE FROM `SUBSCRIPTIONS` WHERE date < ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, now);
        });
    }

    public void unsubscribeAll(User user) {
        update("DELETE FROM `SUBSCRIPTIONS` WHERE user = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, user.getIdLong());
        });
    }

    public void unsubscribe(User user, int allianceOrNation, BankSubType type) {
        update("DELETE FROM `SUBSCRIPTIONS` WHERE user = ? AND allianceOrNation = ? AND isNation & ? > 0", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, user.getIdLong());
            stmt.setInt(2, allianceOrNation);
            stmt.setInt(3, type.mask);
        });
    }

    public void subscribe(User user, int allianceOrNation, BankSubType type, long date, boolean isReceive, long amount) {
        long pair = user.getIdLong();
        update("INSERT OR REPLACE INTO `SUBSCRIPTIONS`(`user`, `allianceOrNation`, `isNation`, `date`, `isReceive`, `amount`) VALUES(?, ?, ?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, user.getIdLong());
            stmt.setInt(2, allianceOrNation);
            stmt.setInt(3, type.mask);
            stmt.setLong(4, date);
            stmt.setBoolean(5, isReceive);
            stmt.setLong(6, amount);
        });
    }

    public enum BankSubType {
        ALL(0),
        ALLIANCE(1),
        NATION(2),
        GUILD(3);

        public static final BankSubType[] values = values();
        public static final BankSubType of(boolean isAA) {
            return isAA ? ALLIANCE : NATION;
        }

        public final int mask;

        BankSubType(int i) {
            this.mask = i;
        }

        public static BankSubType get(int isNation) {
            for (BankSubType type : values) {
                if (type.mask == isNation) return type;
            }
            return null;
        }
    }

    public Set<Subscription> getSubscriptions(int allianceOrNation, BankSubType type, boolean isReceive, long amount) {
        long date = System.currentTimeMillis();
        Set<Subscription> list = new LinkedHashSet<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM SUBSCRIPTIONS WHERE allianceOrNation = ? AND isNation & ? > 0 AND isReceive = ? AND amount <= ? AND DATE > ?")) {
            stmt.setInt(1, allianceOrNation);
            stmt.setInt(2, type.mask);
            stmt.setBoolean(3, isReceive);
            stmt.setLong(4, amount);
            stmt.setLong(5, date);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(createSub(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static class Subscription {
        public final long user;
        public final int allianceOrNation;
        public final BankSubType type;
        public final long endDate;
        public final boolean isReceive;
        public final long amount;

        public Subscription(long user, int allianceOrNation, BankSubType type, long endDate, boolean isReceive, long amount) {
            this.user = user;
            this.allianceOrNation = allianceOrNation;
            this.type = type;
            this.endDate = endDate;
            this.isReceive = isReceive;
            this.amount = amount;
        }
    }

    public Subscription createSub(ResultSet rs) throws SQLException {
        // `user`, `allianceOrNation`, `isNation`, `date`, `isReceive`, `amount`
        return new Subscription(rs.getLong("user"),
                rs.getInt("allianceOrNation"),
                BankSubType.get(rs.getInt("isNation")),
                rs.getLong("date"),
                rs.getBoolean("isReceive"),
                rs.getLong("amount")
                );
    }

    public Set<Subscription> getSubscriptions(long userId) {
        long date = System.currentTimeMillis();
        Set<Subscription> list = new LinkedHashSet<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM SUBSCRIPTIONS WHERE user = ? AND date > ?")) {
            stmt.setLong(1, userId);
            stmt.setLong(2, date);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(createSub(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void addAllianceTransactionsLegacy2(List<Transaction2> transactions) {
        if (transactions.isEmpty()) return;
        long now = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(60);
        for (Transaction2 transaction : transactions) {
            if (transaction.tx_datetime > now) throw new IllegalArgumentException("Transaction date is > now: " + transaction.tx_datetime);
        }

        String query = transactions.get(0).createInsert("TRANSACTIONS_ALLIANCE_2", false, false);
        executeBatch(transactions, query, (ThrowingBiConsumer<Transaction2, PreparedStatement>) Transaction2::setNoID);
    }

    public void importTransactionsLegacy() {
        BankDB bankDb = Locutus.imp().getBankDB();

        Set<Long> offshores = Locutus.imp().getRootBank().getGuildDB().getCoalitionRaw(Coalition.OFFSHORE);
        List<Transfer> transfersLegacy = bankDb.getBankTransactionsLegacy();
        transfersLegacy.removeIf(f -> f.getReceiver() > 10000);
        transfersLegacy.removeIf(f -> !offshores.contains((long) f.getSender()) && !offshores.contains((long) f.getReceiver()) || f.getAmount() == 0);
        Map<Long, List<Transfer>> byAA = new HashMap<>();
        for (Transfer transfer : transfersLegacy) {
            long pair = transfer.getSender() ^ ((long) transfer.getReceiver() << 17L) ^ ((long) transfer.getBanker() << 34L);
            byAA.computeIfAbsent(pair, f -> new ArrayList<>()).add(transfer);
        }

        List<Transaction2> transaction2s = new ArrayList<>();

        int notOffshore = 0;
        Map<Integer, double[]> duplicateByAA = new HashMap<>();

        for (Map.Entry<Long, List<Transfer>> entry : byAA.entrySet()) {
            List<Transfer> transferList = entry.getValue();
            Collections.sort(transferList, new Comparator<Transfer>() {
                @Override
                public int compare(Transfer o1, Transfer o2) {
                    return Long.compare(o1.getDate(), o2.getDate());
                }
            });
            Transaction2 transaction = null;
            Transfer last = null;

            List<Integer> toRemove = new ArrayList<>();
            for (int i = 0; i < transferList.size(); i++) {
                for (int j = i + 1; j < transferList.size(); j++) {
                    Transfer tx1 = transferList.get(i);
                    Transfer tx2 = transferList.get(j);
                    if (tx2.getDate() > tx1.getDate() + 60000) break;
                    if (!tx1.isReceiverAA() || !tx2.isReceiverAA() || !tx1.isSenderAA() || !tx2.isSenderAA()) continue;
                    if (tx1.getReceiver() > 8762 || tx1.getSender() > 8762) continue;
                    if (!offshores.contains((long) tx1.getSender()) && !offshores.contains((long) tx1.getReceiver())) {
                        notOffshore++;
                        continue;
                    }
                    if (tx1.getSender() == tx2.getSender() &&
                            tx1.getReceiver() == tx2.getReceiver() &&
                            tx1.getBanker() == tx2.getBanker() &&
                            tx1.getDate() / 60000 == tx2.getDate() / 60000 &&
                            tx1.getRss() == tx2.getRss() &&
                            tx1.getAmount() == tx2.getAmount() &&
                            Objects.equals(tx1.getNote(), tx2.getNote())
                    ) {
                        toRemove.add(j);
                        if (tx1.isSenderAA()) {
                            duplicateByAA.computeIfAbsent(tx1.getSender(), f -> ResourceType.getBuffer())[tx1.getRss().ordinal()] += (tx1.getAmount() * 1);
                        }
                        if (tx1.isReceiverAA()) {
                            duplicateByAA.computeIfAbsent(tx1.getReceiver(), f -> ResourceType.getBuffer())[tx1.getRss().ordinal()] += (tx1.getAmount() * -1);
                        }
                    }
                }
            }
            if (!toRemove.isEmpty()) {
                toRemove = new ArrayList<>(new HashSet<>(toRemove));
                Collections.sort(toRemove);
                for (int i = toRemove.size() - 1; i >= 0; i--) {
                    transferList.remove((int) toRemove.get(i));
                }
            }

            for (Transfer transfer : transferList) {
                // if equal to previous and rss id > previous rss id
                if (last == null) {
                    last = transfer;
                    transaction = new Transaction2(last);
                    continue;
                }
                if (transfer.isReceiverAA() != last.isReceiverAA() ||
                        transfer.isSenderAA() != last.isSenderAA() ||
                        (transfer.getDate() / 60000) != (last.getDate() / 60000) ||
                        transfer.getSender() != last.getSender() ||
                        transfer.getReceiver() != last.getReceiver() ||
                        transfer.getBanker() != last.getBanker() ||
//                        transfer.getRss().ordinal() <= last.getRss().ordinal() ||
                        transaction.resources[transfer.getRss().ordinal()] != 0 ||
                        !Objects.equals(transfer.getNote(), last.getNote())
                ) {
                    transaction2s.add(transaction);
                    last = transfer;
                    transaction = new Transaction2(last);
                    continue;
                } else {
                    transaction.resources[transfer.getRss().ordinal()] += transfer.getAmount();
                }
            }
            if (transaction != null) {
                transaction2s.add(transaction);
            }
        }

        double[] total = ResourceType.getBuffer();
        for (Long offshore : offshores) {
            total = ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, total, duplicateByAA.getOrDefault(offshore.intValue(), ResourceType.getBuffer()));
        }
        for (Map.Entry<Integer, double[]> entry : duplicateByAA.entrySet()) {
            if (offshores.contains((long) entry.getKey())) continue;
        }

        Collections.sort(transaction2s, new Comparator<Transaction2>() {
            @Override
            public int compare(Transaction2 o1, Transaction2 o2) {
                return Long.compare(o1.getDate(), o2.getDate());
            }
        });

        addAllianceTransactionsLegacy2(transaction2s);
    }

    public void removeAllianceTransactions(int allianceId, long timestamp) {
        update("DELETE FROM `TRANSACTIONS_ALLIANCE_2` WHERE (sender_id = ? AND sender_type = ?) or (receiver_id = ? AND receiver_type = ?) and tx_datetime >= ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, allianceId);
            stmt.setInt(2, 2);
            stmt.setInt(3, allianceId);
            stmt.setInt(4, 2);
            stmt.setLong(5, timestamp);
        });
    }

    public void removeAllianceTransactions(int aaId1, int aaId2, long timestamp) {
        if (aaId1 == aaId2) {
            removeAllianceTransactions(aaId1, timestamp);
            return;
        }
        update("DELETE FROM `TRANSACTIONS_ALLIANCE_2` WHERE ((sender_type = 2 and receiver_type = 2) and ((sender_id = ? and receiver_id = ?) or (sender_id = ? and receiver_id = ?))) and tx_datetime >= ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, aaId1);
            stmt.setInt(2, aaId2);
            stmt.setInt(3, aaId2);
            stmt.setInt(4, aaId1);
            stmt.setLong(5, timestamp);
        });
    }

    public void removeAllianceTransactions(int allianceId) {
        update("DELETE FROM `TRANSACTIONS_ALLIANCE_2` WHERE (sender_id = ? AND sender_type = ?) or (receiver_id = ? AND receiver_type = ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, allianceId);
            stmt.setInt(2, 2);
            stmt.setInt(3, allianceId);
            stmt.setInt(4, 2);
        });
    }

    public List<Transaction2> getBankTransactionsWithNote(String note, long cutoff) {
        note = "%" + note.toLowerCase() + "%";
        String query = "select * FROM TRANSACTIONS_ALLIANCE_2 WHERE tx_datetime >= ? AND lower(note) like ?";

        List<Transaction2> list = new ArrayList<>();

        String finalNote = note;
        query(query, new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setLong(1, cutoff);
                stmt.setString(2, finalNote);
            }
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                list.add(new Transaction2(rs));
            }
        });
        return list;
    }

    public List<Transaction2> getBankTransactions(long senderOrReceiverId, int type) {
        if (type == 1) return getTransactionsByNation((int) senderOrReceiverId);
        if (type != 2 && type != 3) throw new IllegalArgumentException("Invalid type: " + type);

        List<Transaction2> list = new ArrayList<>();

        String query = "select * FROM TRANSACTIONS_ALLIANCE_2 WHERE ((sender_id = ? AND sender_TYPE = ?) OR (receiver_id = ? AND receiver_type = ?) OR (lower(note) like ?))";

        query(query, new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setLong(1, senderOrReceiverId);
                stmt.setInt(2, type);
                stmt.setLong(3, senderOrReceiverId);
                stmt.setInt(4, type);

                if (type == 3) stmt.setString(5, "%#guild=" + senderOrReceiverId + "%");
                else if (type == 2) stmt.setString(5, "%#alliance=" + senderOrReceiverId + "%");

            }
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                list.add(new Transaction2(rs));
            }
        });
        return list;
    }

    private List<Transfer> getBankTransactionsLegacy() {
        ArrayList<Transfer> list = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM TRANSACTIONS_BANK")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(Transfer.of(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
