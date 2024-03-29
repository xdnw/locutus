/*
 * This file is generated by jOOQ.
 */
package org.example.jooq.locutus;


import java.util.Arrays;
import java.util.List;

import org.example.jooq.locutus.tables.ApiKeys2;
import org.example.jooq.locutus.tables.Credentials2;
import org.example.jooq.locutus.tables.DiscordMeta;
import org.example.jooq.locutus.tables.Users;
import org.example.jooq.locutus.tables.Uuids;
import org.example.jooq.locutus.tables.Verified;
import org.jooq.Catalog;
import org.jooq.Table;
import org.jooq.impl.SchemaImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class DefaultSchema extends SchemaImpl {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>DEFAULT_SCHEMA</code>
     */
    public static final DefaultSchema DEFAULT_SCHEMA = new DefaultSchema();

    /**
     * The table <code>API_KEYS2</code>.
     */
    public final ApiKeys2 API_KEYS2 = ApiKeys2.API_KEYS2;

    /**
     * The table <code>CREDENTIALS2</code>.
     */
    public final Credentials2 CREDENTIALS2 = Credentials2.CREDENTIALS2;

    /**
     * The table <code>DISCORD_META</code>.
     */
    public final DiscordMeta DISCORD_META = DiscordMeta.DISCORD_META;

    /**
     * The table <code>USERS</code>.
     */
    public final Users USERS = Users.USERS;

    /**
     * The table <code>UUIDS</code>.
     */
    public final Uuids UUIDS = Uuids.UUIDS;

    /**
     * The table <code>VERIFIED</code>.
     */
    public final Verified VERIFIED = Verified.VERIFIED;

    /**
     * No further instances allowed
     */
    private DefaultSchema() {
        super("", null);
    }


    @Override
    public Catalog getCatalog() {
        return DefaultCatalog.DEFAULT_CATALOG;
    }

    @Override
    public final List<Table<?>> getTables() {
        return Arrays.asList(
            ApiKeys2.API_KEYS2,
            Credentials2.CREDENTIALS2,
            DiscordMeta.DISCORD_META,
            Users.USERS,
            Uuids.UUIDS,
            Verified.VERIFIED
        );
    }
}
