/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.nations.tables;


import java.util.function.Function;

import org.example.jooq.nations.DefaultSchema;
import org.example.jooq.nations.Keys;
import org.example.jooq.nations.tables.records.AllianceMetricsRecord;
import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Function4;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Records;
import org.jooq.Row4;
import org.jooq.Schema;
import org.jooq.SelectField;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class AllianceMetrics extends TableImpl<AllianceMetricsRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>ALLIANCE_METRICS</code>
     */
    public static final AllianceMetrics ALLIANCE_METRICS = new AllianceMetrics();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<AllianceMetricsRecord> getRecordType() {
        return AllianceMetricsRecord.class;
    }

    /**
     * The column <code>ALLIANCE_METRICS.alliance_id</code>.
     */
    public final TableField<AllianceMetricsRecord, Integer> ALLIANCE_ID = createField(DSL.name("alliance_id"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>ALLIANCE_METRICS.metric</code>.
     */
    public final TableField<AllianceMetricsRecord, Integer> METRIC = createField(DSL.name("metric"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>ALLIANCE_METRICS.turn</code>.
     */
    public final TableField<AllianceMetricsRecord, Long> TURN = createField(DSL.name("turn"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>ALLIANCE_METRICS.value</code>.
     */
    public final TableField<AllianceMetricsRecord, Double> VALUE = createField(DSL.name("value"), SQLDataType.DOUBLE.nullable(false), this, "");

    private AllianceMetrics(Name alias, Table<AllianceMetricsRecord> aliased) {
        this(alias, aliased, null);
    }

    private AllianceMetrics(Name alias, Table<AllianceMetricsRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>ALLIANCE_METRICS</code> table reference
     */
    public AllianceMetrics(String alias) {
        this(DSL.name(alias), ALLIANCE_METRICS);
    }

    /**
     * Create an aliased <code>ALLIANCE_METRICS</code> table reference
     */
    public AllianceMetrics(Name alias) {
        this(alias, ALLIANCE_METRICS);
    }

    /**
     * Create a <code>ALLIANCE_METRICS</code> table reference
     */
    public AllianceMetrics() {
        this(DSL.name("ALLIANCE_METRICS"), null);
    }

    public <O extends Record> AllianceMetrics(Table<O> child, ForeignKey<O, AllianceMetricsRecord> key) {
        super(child, key, ALLIANCE_METRICS);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : DefaultSchema.DEFAULT_SCHEMA;
    }

    @Override
    public UniqueKey<AllianceMetricsRecord> getPrimaryKey() {
        return Keys.ALLIANCE_METRICS__PK_ALLIANCE_METRICS;
    }

    @Override
    public AllianceMetrics as(String alias) {
        return new AllianceMetrics(DSL.name(alias), this);
    }

    @Override
    public AllianceMetrics as(Name alias) {
        return new AllianceMetrics(alias, this);
    }

    @Override
    public AllianceMetrics as(Table<?> alias) {
        return new AllianceMetrics(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public AllianceMetrics rename(String name) {
        return new AllianceMetrics(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public AllianceMetrics rename(Name name) {
        return new AllianceMetrics(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public AllianceMetrics rename(Table<?> name) {
        return new AllianceMetrics(name.getQualifiedName(), null);
    }

    // -------------------------------------------------------------------------
    // Row4 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row4<Integer, Integer, Long, Double> fieldsRow() {
        return (Row4) super.fieldsRow();
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Function)}.
     */
    public <U> SelectField<U> mapping(Function4<? super Integer, ? super Integer, ? super Long, ? super Double, ? extends U> from) {
        return convertFrom(Records.mapping(from));
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Class,
     * Function)}.
     */
    public <U> SelectField<U> mapping(Class<U> toType, Function4<? super Integer, ? super Integer, ? super Long, ? super Double, ? extends U> from) {
        return convertFrom(toType, Records.mapping(from));
    }
}
