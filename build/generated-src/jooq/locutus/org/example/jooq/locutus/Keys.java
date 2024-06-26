/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.locutus;


import org.example.jooq.locutus.tables.ApiKeys2;
import org.example.jooq.locutus.tables.Credentials2;
import org.example.jooq.locutus.tables.DiscordMeta;
import org.example.jooq.locutus.tables.Users;
import org.example.jooq.locutus.tables.Uuids;
import org.example.jooq.locutus.tables.Verified;
import org.example.jooq.locutus.tables.records.ApiKeys2Record;
import org.example.jooq.locutus.tables.records.Credentials2Record;
import org.example.jooq.locutus.tables.records.DiscordMetaRecord;
import org.example.jooq.locutus.tables.records.UsersRecord;
import org.example.jooq.locutus.tables.records.UuidsRecord;
import org.example.jooq.locutus.tables.records.VerifiedRecord;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.Internal;


/**
 * A class modelling foreign key relationships and constraints of tables in the
 * default schema.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Keys {

    // -------------------------------------------------------------------------
    // UNIQUE and PRIMARY KEY definitions
    // -------------------------------------------------------------------------

    public static final UniqueKey<ApiKeys2Record> API_KEYS2__PK_API_KEYS2 = Internal.createUniqueKey(ApiKeys2.API_KEYS2, DSL.name("pk_API_KEYS2"), new TableField[] { ApiKeys2.API_KEYS2.NATION_ID }, true);
    public static final UniqueKey<Credentials2Record> CREDENTIALS2__PK_CREDENTIALS2 = Internal.createUniqueKey(Credentials2.CREDENTIALS2, DSL.name("pk_CREDENTIALS2"), new TableField[] { Credentials2.CREDENTIALS2.DISCORDID }, true);
    public static final UniqueKey<DiscordMetaRecord> DISCORD_META__PK_DISCORD_META = Internal.createUniqueKey(DiscordMeta.DISCORD_META, DSL.name("pk_DISCORD_META"), new TableField[] { DiscordMeta.DISCORD_META.KEY, DiscordMeta.DISCORD_META.ID }, true);
    public static final UniqueKey<UsersRecord> USERS__PK_USERS = Internal.createUniqueKey(Users.USERS, DSL.name("pk_USERS"), new TableField[] { Users.USERS.DISCORD_ID }, true);
    public static final UniqueKey<UuidsRecord> UUIDS__PK_UUIDS = Internal.createUniqueKey(Uuids.UUIDS, DSL.name("pk_UUIDS"), new TableField[] { Uuids.UUIDS.NATION_ID, Uuids.UUIDS.UUID, Uuids.UUIDS.DATE }, true);
    public static final UniqueKey<VerifiedRecord> VERIFIED__PK_VERIFIED = Internal.createUniqueKey(Verified.VERIFIED, DSL.name("pk_VERIFIED"), new TableField[] { Verified.VERIFIED.NATION_ID }, true);
}
