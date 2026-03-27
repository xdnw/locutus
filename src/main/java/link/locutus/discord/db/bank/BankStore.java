package link.locutus.discord.db.bank;

import com.politicsandwar.graphql.model.Bankrec;
import link.locutus.discord.apiv1.entities.BankRecord;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.TaxDeposit;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.TaxEstimate;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.TransactionNote;
import link.locutus.discord.event.Event;
import link.locutus.discord.pnw.NationOrAlliance;
import net.dv8tion.jda.api.entities.User;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public interface BankStore {
    void createTables();

    void addTaxEstimate(int taxId, int minCash, int maxCash, int minRss, int maxRss);

    Map<Integer, TaxEstimate> getTaxEstimates();

    Map<Integer, double[]> getAppliedTaxDeposits(
            Set<Integer> nationIds,
            Set<Integer> allianceIds,
            int[] taxBase,
            boolean useTaxBase
    );

    Map<Integer, double[]> getUnappliedTaxDeposits(
            Set<Integer> nationIds,
            Set<Integer> allianceIds,
            int[] taxBase
    );

    Transaction2 getLatestTransaction();

    void updateBankRecs(int nationId, boolean priority, Consumer<Event> eventConsumer);

    void updateBankRecsAuto(Set<Integer> nations, boolean priority, Consumer<Event> eventConsumer);

    void updateBankRecs(boolean priority, Consumer<Event> eventConsumer);

    void saveBankRecsV2(List<BankRecord> bankrecs, Consumer<Event> eventConsumer);

    void saveBankRecs(List<Bankrec> bankrecs, Consumer<Event> eventConsumer);

    void addTaxDeposit(TaxDeposit record);

    List<Transaction2> getAllTransactions(
            NationOrAlliance sender,
            NationOrAlliance receiver,
            NationOrAlliance banker,
            Long startDate,
            Long endDate
    );

    List<Transaction2> getAllTransactions(
            Set<NationOrAlliance> sender,
            Set<NationOrAlliance> receiver,
            Set<NationOrAlliance> banker,
            Long startDate,
            Long endDate
    );

    List<Transaction2> getTransactionsbyId(Collection<Integer> ids);

    List<Transaction2> getTransactionsByBySenderOrReceiver(
            Set<Long> senders,
            Set<Long> receivers,
            long minDateMs,
            long maxDateMs
    );

    List<Transaction2> getTransactionsByBySender(Set<Long> senders, long minDateMs);

    List<Transaction2> getTransactionsByByReceiver(Set<Long> receivers, long minDateMs, long endDate);

    List<Transaction2> getTransactions(long minDateMs, boolean desc);

    List<Transaction2> getToNationTransactions(long minDateMs);

    List<Transaction2> getNationTransfers(int nationId, long minDateMs);

    Map<Integer, List<Transaction2>> getNationTransfersByNation(long start, long end, Set<Integer> nationIds);

    void iterateNationTransfersByNation(
            long start,
            long end,
            Set<Integer> nationIds,
            BiConsumer<Integer, Transaction2> consumer,
            boolean ordered
    );

    List<Transaction2> getAllianceTransfers(int allianceId, long minDateMs);

    int getTransactionsByNationCount(int nation);

    List<Transaction2> getTransactionsByBanker(int nation);

    List<Transaction2> getTransactionsByNation(int nation);

    List<Transaction2> getTransactionsByNation(int nation, int limit);

    List<Transaction2> getTransactionsByNation(int nation, long start, long end);

    Transaction2 getLatestDeposit(int id, int type);

    Transaction2 getLatestWithdrawal(int id, int type);

    Transaction2 getLatestSelfWithdrawal(int nationId);

    List<Transaction2> getTransactionsWithStructuredNote(TransactionNote note, long cutoff);

    List<Transaction2> getTransactionsByAllianceSender(int allianceId);

    Set<Integer> getReceiverNationIdFromAllianceReceivers(Set<Integer> allianceIds);

    List<Transaction2> getTransactionsByAllianceReceiver(int allianceId);

    List<Transaction2> getTransactionsByAlliance(int allianceId);

    int[] addTransactions(List<Transaction2> transactions, boolean ignoreInto);

    int addTransaction(Transaction2 tx, boolean ignoreInto);

    List<TaxDeposit> getTaxesPaid(int nation, int alliance);

    void iterateTaxesPaid(
            Set<Integer> nationIds,
            Set<Integer> alliances,
            boolean includeNoInternal,
            boolean includeMaxInternal,
            long start,
            long end,
            Consumer<TaxDeposit> consumer
    );

    List<TaxDeposit> getTaxesByIds(Collection<Integer> ids);

    List<TaxDeposit> getTaxesByBrackets(Collection<Integer> bracketIds);

    List<TaxDeposit> getTaxesByNations(Collection<Integer> nationIds);

    List<TaxDeposit> getTaxesByBracket(int taxId);

    List<TaxDeposit> getTaxesByBracket(int taxId, long start, long end);

    List<TaxDeposit> getTaxesPaid(int nation);

    List<TaxDeposit> getTaxesByAA(int alliance);

    List<TaxDeposit> getTaxesByAA(Set<Integer> allianceIds);

    TaxDeposit getLatestTaxDeposit(int allianceId);

    List<TaxDeposit> getTaxesByTurn(int alliance);

    void deleteTaxDeposits(int allianceId, long date);

    void addTaxDeposits(Collection<TaxDeposit> records);

    void clearTaxDeposits(int allianceId);

    Map<Integer, TaxBracket> getTaxBracketsFromDeposits();

    Map<Integer, TaxBracket> getTaxBracketsAndEstimates();

    Map<Integer, TaxBracket> getTaxBracketsAndEstimates(
            boolean allowDeposits,
            boolean allowApi,
            boolean addUnknownBrackets
    );

    Map<Integer, TaxBracket> getTaxBrackets();

    Map<Integer, TaxBracket> getTaxBrackets(Map<Integer, Integer> alliancesByTaxId);

    void addTaxBracket(TaxBracket bracket);

    void purgeSubscriptions();

    void unsubscribeAll(long userId);

    void unsubscribe(User user, int allianceOrNation, BankDB.BankSubType type);

    void subscribe(
            User user,
            int allianceOrNation,
            BankDB.BankSubType type,
            long date,
            boolean isReceive,
            long amount
    );

    Set<BankDB.Subscription> getSubscriptions(
            int allianceOrNation,
            BankDB.BankSubType type,
            boolean isReceive,
            long amount
    );

    Set<BankDB.Subscription> getSubscriptions(long userId);

    List<Transaction2> getAllianceTransactions(
            Set<Integer> receiverAAs,
            boolean includeLegacy,
            Predicate<Transaction2> filter
    );
}