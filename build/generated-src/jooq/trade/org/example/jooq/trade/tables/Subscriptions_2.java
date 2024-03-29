/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.trade.tables;


import java.util.function.Function;

import org.example.jooq.trade.DefaultSchema;
import org.example.jooq.trade.Keys;
import org.example.jooq.trade.tables.records.Subscriptions_2Record;
import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Function7;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Records;
import org.jooq.Row7;
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
public class Subscriptions_2 extends TableImpl<Subscriptions_2Record> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>SUBSCRIPTIONS_2</code>
     */
    public static final Subscriptions_2 SUBSCRIPTIONS_2 = new Subscriptions_2();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<Subscriptions_2Record> getRecordType() {
        return Subscriptions_2Record.class;
    }

    /**
     * The column <code>SUBSCRIPTIONS_2.user</code>.
     */
    public final TableField<Subscriptions_2Record, Long> USER = createField(DSL.name("user"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>SUBSCRIPTIONS_2.resource</code>.
     */
    public final TableField<Subscriptions_2Record, Integer> RESOURCE = createField(DSL.name("resource"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>SUBSCRIPTIONS_2.date</code>.
     */
    public final TableField<Subscriptions_2Record, Long> DATE = createField(DSL.name("date"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>SUBSCRIPTIONS_2.isBuy</code>.
     */
    public final TableField<Subscriptions_2Record, Integer> ISBUY = createField(DSL.name("isBuy"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>SUBSCRIPTIONS_2.above</code>.
     */
    public final TableField<Subscriptions_2Record, Integer> ABOVE = createField(DSL.name("above"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>SUBSCRIPTIONS_2.ppu</code>.
     */
    public final TableField<Subscriptions_2Record, Integer> PPU = createField(DSL.name("ppu"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>SUBSCRIPTIONS_2.type</code>.
     */
    public final TableField<Subscriptions_2Record, Integer> TYPE = createField(DSL.name("type"), SQLDataType.INTEGER.nullable(false), this, "");

    private Subscriptions_2(Name alias, Table<Subscriptions_2Record> aliased) {
        this(alias, aliased, null);
    }

    private Subscriptions_2(Name alias, Table<Subscriptions_2Record> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>SUBSCRIPTIONS_2</code> table reference
     */
    public Subscriptions_2(String alias) {
        this(DSL.name(alias), SUBSCRIPTIONS_2);
    }

    /**
     * Create an aliased <code>SUBSCRIPTIONS_2</code> table reference
     */
    public Subscriptions_2(Name alias) {
        this(alias, SUBSCRIPTIONS_2);
    }

    /**
     * Create a <code>SUBSCRIPTIONS_2</code> table reference
     */
    public Subscriptions_2() {
        this(DSL.name("SUBSCRIPTIONS_2"), null);
    }

    public <O extends Record> Subscriptions_2(Table<O> child, ForeignKey<O, Subscriptions_2Record> key) {
        super(child, key, SUBSCRIPTIONS_2);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : DefaultSchema.DEFAULT_SCHEMA;
    }

    @Override
    public UniqueKey<Subscriptions_2Record> getPrimaryKey() {
        return Keys.SUBSCRIPTIONS_2__PK_SUBSCRIPTIONS_2;
    }

    @Override
    public Subscriptions_2 as(String alias) {
        return new Subscriptions_2(DSL.name(alias), this);
    }

    @Override
    public Subscriptions_2 as(Name alias) {
        return new Subscriptions_2(alias, this);
    }

    @Override
    public Subscriptions_2 as(Table<?> alias) {
        return new Subscriptions_2(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public Subscriptions_2 rename(String name) {
        return new Subscriptions_2(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public Subscriptions_2 rename(Name name) {
        return new Subscriptions_2(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public Subscriptions_2 rename(Table<?> name) {
        return new Subscriptions_2(name.getQualifiedName(), null);
    }

    // -------------------------------------------------------------------------
    // Row7 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row7<Long, Integer, Long, Integer, Integer, Integer, Integer> fieldsRow() {
        return (Row7) super.fieldsRow();
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Function)}.
     */
    public <U> SelectField<U> mapping(Function7<? super Long, ? super Integer, ? super Long, ? super Integer, ? super Integer, ? super Integer, ? super Integer, ? extends U> from) {
        return convertFrom(Records.mapping(from));
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Class,
     * Function)}.
     */
    public <U> SelectField<U> mapping(Class<U> toType, Function7<? super Long, ? super Integer, ? super Long, ? super Integer, ? super Integer, ? super Integer, ? super Integer, ? extends U> from) {
        return convertFrom(toType, Records.mapping(from));
    }
}
