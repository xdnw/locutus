package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.NationFilterString;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;

public enum TemplateTypes {
    CITY(DepositType.CITY, CityTemplate.class) {
        @Override
        public CommandRef getCommandMention() {
            return CM.grant_template.create.city.cmd;
        }
    },
    PROJECT(DepositType.PROJECT, ProjectTemplate.class) {
        @Override
        public CommandRef getCommandMention() {
            return CM.grant_template.create.project.cmd;
        }
    },
    INFRA(DepositType.INFRA, InfraTemplate.class) {
        @Override
        public CommandRef getCommandMention() {
            return CM.grant_template.create.infra.cmd;
        }
    },
    LAND(DepositType.LAND, LandTemplate.class) {
        @Override
        public CommandRef getCommandMention() {
            return CM.grant_template.create.land.cmd;
        }
    },
    BUILD(DepositType.BUILD, BuildTemplate.class) {
        @Override
        public CommandRef getCommandMention() {
            return CM.grant_template.create.build.cmd;
        }
    },
    WARCHEST(DepositType.WARCHEST, WarchestTemplate.class) {
        @Override
        public CommandRef getCommandMention() {
            return CM.grant_template.create.warchest.cmd;
        }
    },
    RAWS(DepositType.RAWS, RawsTemplate.class) {
        @Override
        public CommandRef getCommandMention() {
            return CM.grant_template.create.raws.cmd;
        }
    },

    ;

    public static TemplateTypes[] values = values();
    private final DepositType depositType;
    private final Constructor<? extends AGrantTemplate> constructor;

    TemplateTypes(DepositType depositType, Class<? extends AGrantTemplate> implementation) {
        this.depositType = depositType;
        try {
            // (GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs
            this.constructor = implementation.getConstructor(
                    GuildDB.class,
                    boolean.class,
                    String.class,
                    NationFilter.class,
                    long.class,
                    long.class,
                    int.class,
                    boolean.class,
                    int.class,
                    int.class,
                    int.class,
                    int.class,
                    ResultSet.class
            );
            this.constructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public DepositType getDepositType() {
        return depositType;
    }

    public String getTable() {
        return "GRANT_TEMPLATE_" + name();
    }

    public AGrantTemplate create(GuildDB db, ResultSet rs) throws SQLException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return (AGrantTemplate) this.constructor.newInstance(
                db,
                rs.getBoolean("enabled"),
                rs.getString("name"),
                new NationFilterString(rs.getString("nation_filter"), db.getGuild()),
                rs.getLong("econ_role"),
                rs.getLong("self_role"),
                rs.getInt("from_bracket"),
                rs.getBoolean("use_receiver_bracket"),
                rs.getInt("max_total"),
                rs.getInt("max_day"),
                rs.getInt("max_granter_day"),
                rs.getInt("max_granter_total"),
                rs
        );
    }


    public abstract CommandRef getCommandMention();
}
