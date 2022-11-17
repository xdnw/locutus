/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.nations.tables;


import java.util.function.Function;

import org.example.jooq.nations.DefaultSchema;
import org.example.jooq.nations.tables.records.KicksRecord;
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
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Kicks extends TableImpl<KicksRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>KICKS</code>
     */
    public static final Kicks KICKS = new Kicks();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<KicksRecord> getRecordType() {
        return KicksRecord.class;
    }

    /**
     * The column <code>KICKS.nation</code>.
     */
    public final TableField<KicksRecord, Integer> NATION = createField(DSL.name("nation"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>KICKS.alliance</code>.
     */
    public final TableField<KicksRecord, Integer> ALLIANCE = createField(DSL.name("alliance"), SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>KICKS.date</code>.
     */
    public final TableField<KicksRecord, Long> DATE = createField(DSL.name("date"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>KICKS.type</code>.
     */
    public final TableField<KicksRecord, Integer> TYPE = createField(DSL.name("type"), SQLDataType.INTEGER.nullable(false), this, "");

    private Kicks(Name alias, Table<KicksRecord> aliased) {
        this(alias, aliased, null);
    }

    private Kicks(Name alias, Table<KicksRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>KICKS</code> table reference
     */
    public Kicks(String alias) {
        this(DSL.name(alias), KICKS);
    }

    /**
     * Create an aliased <code>KICKS</code> table reference
     */
    public Kicks(Name alias) {
        this(alias, KICKS);
    }

    /**
     * Create a <code>KICKS</code> table reference
     */
    public Kicks() {
        this(DSL.name("KICKS"), null);
    }

    public <O extends Record> Kicks(Table<O> child, ForeignKey<O, KicksRecord> key) {
        super(child, key, KICKS);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : DefaultSchema.DEFAULT_SCHEMA;
    }

    @Override
    public Kicks as(String alias) {
        return new Kicks(DSL.name(alias), this);
    }

    @Override
    public Kicks as(Name alias) {
        return new Kicks(alias, this);
    }

    @Override
    public Kicks as(Table<?> alias) {
        return new Kicks(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public Kicks rename(String name) {
        return new Kicks(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public Kicks rename(Name name) {
        return new Kicks(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public Kicks rename(Table<?> name) {
        return new Kicks(name.getQualifiedName(), null);
    }

    // -------------------------------------------------------------------------
    // Row4 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row4<Integer, Integer, Long, Integer> fieldsRow() {
        return (Row4) super.fieldsRow();
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Function)}.
     */
    public <U> SelectField<U> mapping(Function4<? super Integer, ? super Integer, ? super Long, ? super Integer, ? extends U> from) {
        return convertFrom(Records.mapping(from));
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Class,
     * Function)}.
     */
    public <U> SelectField<U> mapping(Class<U> toType, Function4<? super Integer, ? super Integer, ? super Long, ? super Integer, ? extends U> from) {
        return convertFrom(toType, Records.mapping(from));
    }
}