package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.db.GuildDB;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GrantTemplateManager {
    private final GuildDB db;
    public Map<String, Set<AGrantTemplate>> templates = new ConcurrentHashMap<>();

    public GrantTemplateManager(GuildDB db) {
        this.db = db;
    }

    public void createTables() {
        // project_grants
        //long Project
        //varchat Name
        //varchar NationFilter
        //long EconRole
        //long SelfRole
        //int FromBracket
        //boolean UseReceiverBracket
        //int MaxTotal
        //int MaxDay
        //int MaxGranterDay
        String projects = "CREATE TABLE IF NOT EXISTS `GRANT_TEMPLATE_PROJECT` " +
                "(`grant_id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "`name` VARCHAR NOT NULL, " +
                "`project` BIGINT NOT NULL, " +
                "`nation_filter` VARCHAR NOT NULL, " +
                "`econ_role` BIGINT NOT NULL, " +
                "`self_role` BIGINT NOT NULL, " +
                "`from_bracket` BIGINT NOT NULL, " +
                "`use_receiver_bracket` BOOLEAN NOT NULL, " +
                "`max_total` INT NOT NULL, " +
                "`max_day` INT NOT NULL, " +
                "`max_granter_day` INT NOT NULL)";

        // city_grants
        //int min_city
        //int max_city
        //varchar Name
        //varchar NationFilter
        //long EconRole
        //long SelfRole
        //int FromBracket
        //boolean UseReceiverBracket
        //int MaxTotal
        //int MaxDay
        //int MaxGranterDay
        String cities = "CREATE TABLE IF NOT EXISTS `GRANT_TEMPLATE_CITY` " +
                "(`grant_id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "`min_city` INT NOT NULL, " +
                "`max_city` INT NOT NULL, " +
                "`name` VARCHAR NOT NULL, " +
                "`nation_filter` VARCHAR NOT NULL, " +
                "`econ_role` BIGINT NOT NULL, " +
                "`self_role` BIGINT NOT NULL, " +
                "`from_bracket` BIGINT NOT NULL, " +
                "`use_receiver_bracket` BOOLEAN NOT NULL, " +
                "`max_total` INT NOT NULL, " +
                "`max_day` INT NOT NULL, " +
                "`max_granter_day` INT NOT NULL)";

        //
        //city_warchest
        //byte[] allowance_per_city
        //long track_days
        //boolean sutract_expenditure
        //long overdraw_percent_cents
        //varchar Name
        //varchar NationFilter
        //long EconRole
        //long SelfRole
        //int FromBracket
        //boolean UseReceiverBracket
        //int MaxTotal
        //int MaxDay
        //int MaxGranterDay
        String warchest = "CREATE TABLE IF NOT EXISTS `GRANT_TEMPLATE_WARCHEST` " +
                "(`grant_id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "`allowance_per_city` BLOB NOT NULL, " +
                "`name` VARCHAR NOT NULL, " +
                "`nation_filter` VARCHAR NOT NULL, " +
                "`track_days` BIGINT NOT NULL, " +
                "`subtract_expenditure` BOOLEAN NOT NULL, " +
                "`overdraw_percent_cents` BIGINT NOT NULL, " +
                "`econ_role` BIGINT NOT NULL, " +
                "`self_role` BIGINT NOT NULL, " +
                "`from_bracket` BIGINT NOT NULL, " +
                "`use_receiver_bracket` BOOLEAN NOT NULL, " +
                "`max_total` INT NOT NULL, " +
                "`max_day` INT NOT NULL, " +
                "`max_granter_day` INT NOT NULL)";
        //
        //Grant template infra
        //long level
        //boolean onlyNewCities
        //boolean track_days
        //long require_n_offensives
        //boolean allow_rebuild
        //varchar Name
        //varchar NationFilter
        //long EconRole
        //long SelfRole
        //int FromBracket
        //boolean UseReceiverBracket
        //int MaxTotal
        //int MaxDay
        //int MaxGranterDay
        String infra = "CREATE TABLE IF NOT EXISTS `GRANT_TEMPLATE_INFRA` " +
                "(`grant_id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "`level` BIGINT NOT NULL, " +
                "`only_new_cities` BOOLEAN NOT NULL, " +
                "`track_days` BOOLEAN NOT NULL, " +
                "`require_n_offensives` BIGINT NOT NULL, " +
                "`allow_rebuild` BOOLEAN NOT NULL, " +
                "`name` VARCHAR NOT NULL, " +
                "`nation_filter` VARCHAR NOT NULL, " +
                "`econ_role` BIGINT NOT NULL, " +
                "`self_role` BIGINT NOT NULL, " +
                "`from_bracket` BIGINT NOT NULL, " +
                "`use_receiver_bracket` BOOLEAN NOT NULL, " +
                "`max_total` INT NOT NULL, " +
                "`max_day` INT NOT NULL, " +
                "`max_granter_day` INT NOT NULL)";
        //
        //Grant template land
        //long level
        //boolean onlyNewCities
        //varchar Name
        //varchar NationFilter
        //long EconRole
        //long SelfRole
        //int FromBracket
        //boolean UseReceiverBracket
        //int MaxTotal
        //int MaxDay
        //int MaxGranterDay
        String land = "CREATE TABLE IF NOT EXISTS `GRANT_TEMPLATE_LAND` " +
                "(`grant_id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "`level` BIGINT NOT NULL, " +
                "`only_new_cities` BOOLEAN NOT NULL, " +
                "`name` VARCHAR NOT NULL, " +
                "`nation_filter` VARCHAR NOT NULL, " +
                "`econ_role` BIGINT NOT NULL, " +
                "`self_role` BIGINT NOT NULL, " +
                "`from_bracket` BIGINT NOT NULL, " +
                "`use_receiver_bracket` BOOLEAN NOT NULL, " +
                "`max_total` INT NOT NULL, " +
                "`max_day` INT NOT NULL, " +
                "`max_granter_day` INT NOT NULL)";
        //
        //Grant template build
        //byte[] build
        //boolean useOptimal
        //long mmr
        //long track_days
        //boolean allow_switch_after_offensive
        //varchar Name
        //varchar NationFilter
        //long EconRole
        //long SelfRole
        //int FromBracket
        //boolean UseReceiverBracket
        //int MaxTotal
        //int MaxDay
        //int MaxGranterDay
        String build = "CREATE TABLE IF NOT EXISTS `GRANT_TEMPLATE_BUILD` " +
                "(`grant_id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "`build` BLOB NOT NULL, " +
                "`use_optimal` BOOLEAN NOT NULL, " +
                "`mmr` BIGINT NOT NULL, " +
                "`track_days` BIGINT NOT NULL, " +
                "`allow_switch_after_offensive` BOOLEAN NOT NULL, " +
                "`name` VARCHAR NOT NULL, " +
                "`nation_filter` VARCHAR NOT NULL, " +
                "`econ_role` BIGINT NOT NULL, " +
                "`self_role` BIGINT NOT NULL, " +
                "`from_bracket` BIGINT NOT NULL, " +
                "`use_receiver_bracket` BOOLEAN NOT NULL, " +
                "`max_total` INT NOT NULL, " +
                "`max_day` INT NOT NULL, " +
                "`max_granter_day` INT NOT NULL)";
        //
        //Grant template raws
        //long days
        //long overdraw_percent_cents
        //varchar Name
        //varchar NationFilter
        //long EconRole
        //long SelfRole
        //int FromBracket
        //boolean UseReceiverBracket
        //int MaxTotal
        //int MaxDay
        //int MaxGranterDay

        String raws = "CREATE TABLE IF NOT EXISTS `GRANT_TEMPLATE_RAWS` " +
                "(`grant_id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "`days` BIGINT NOT NULL, " +
                "`overdraw_percent_cents` BIGINT NOT NULL, " +
                "`name` VARCHAR NOT NULL, " +
                "`nation_filter` VARCHAR NOT NULL, " +
                "`econ_role` BIGINT NOT NULL, " +
                "`self_role` BIGINT NOT NULL, " +
                "`from_bracket` BIGINT NOT NULL, " +
                "`use_receiver_bracket` BOOLEAN NOT NULL, " +
                "`max_total` INT NOT NULL, " +
                "`max_day` INT NOT NULL, " +
                "`max_granter_day` INT NOT NULL)";
        db.executeStmt(projects);
        db.executeStmt(cities);
        db.executeStmt(warchest);
        db.executeStmt(land);
        db.executeStmt(infra);
        db.executeStmt(build);
        db.executeStmt(raws);
    }

    public void loadTemplates() {
        // projects
        {
            // project_grants
            //long Project
            //varchat Name
            //varchar NationFilter
            //long EconRole
            //long SelfRole
            //int FromBracket
            //boolean UseReceiverBracket
            //int MaxTotal
            //int MaxDay
            //int MaxGranterDay
            String query = "SELECT * FROM `GRANT_TEMPLATE_PROJECT`";
        }

        // cities
        {
            // city_grants
            //int min_city
            //int max_city
            //varchar Name
            //varchar NationFilter
            //long EconRole
            //long SelfRole
            //int FromBracket
            //boolean UseReceiverBracket
            //int MaxTotal
            //int MaxDay
            //int MaxGranterDay
        }

        // warchest
        {

        }

        // land
        {

        }

        // infra
        {

        }

        // build
        {

        }

        // raws
        {

        }
    }

    public void saveTemplate(AGrantTemplate template) {
        // build
        // city
        // infra
        // land
        // project
        // raws
        // warchest
        switch (template.getType()) {

        }

    }
}
