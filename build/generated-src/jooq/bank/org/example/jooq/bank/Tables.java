/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.bank;


import org.example.jooq.bank.tables.LootDiffByTaxId;
import org.example.jooq.bank.tables.SqliteSequence;
import org.example.jooq.bank.tables.Subscriptions;
import org.example.jooq.bank.tables.TaxBrackets;
import org.example.jooq.bank.tables.TaxDepositsDate;
import org.example.jooq.bank.tables.TaxEstimate;
import org.example.jooq.bank.tables.TransactionsAlliance_2;
import org.example.jooq.bank.tables.Transactions_2;


/**
 * Convenience access to all tables in the default schema.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Tables {

    /**
     * The table <code>loot_diff_by_tax_id</code>.
     */
    public static final LootDiffByTaxId LOOT_DIFF_BY_TAX_ID = LootDiffByTaxId.LOOT_DIFF_BY_TAX_ID;

    /**
     * The table <code>sqlite_sequence</code>.
     */
    public static final SqliteSequence SQLITE_SEQUENCE = SqliteSequence.SQLITE_SEQUENCE;

    /**
     * The table <code>SUBSCRIPTIONS</code>.
     */
    public static final Subscriptions SUBSCRIPTIONS = Subscriptions.SUBSCRIPTIONS;

    /**
     * The table <code>TAX_BRACKETS</code>.
     */
    public static final TaxBrackets TAX_BRACKETS = TaxBrackets.TAX_BRACKETS;

    /**
     * The table <code>TAX_DEPOSITS_DATE</code>.
     */
    public static final TaxDepositsDate TAX_DEPOSITS_DATE = TaxDepositsDate.TAX_DEPOSITS_DATE;

    /**
     * The table <code>tax_estimate</code>.
     */
    public static final TaxEstimate TAX_ESTIMATE = TaxEstimate.TAX_ESTIMATE;

    /**
     * The table <code>TRANSACTIONS_2</code>.
     */
    public static final Transactions_2 TRANSACTIONS_2 = Transactions_2.TRANSACTIONS_2;

    /**
     * The table <code>TRANSACTIONS_ALLIANCE_2</code>.
     */
    public static final TransactionsAlliance_2 TRANSACTIONS_ALLIANCE_2 = TransactionsAlliance_2.TRANSACTIONS_ALLIANCE_2;
}
