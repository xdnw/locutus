package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.NationFilterString;

import java.lang.reflect.InvocationTargetException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class GrantTemplateManager {
    /**
     * TODO
     *  Default requirements
     *  - Add the default grant requirements to AGrantTemplate
     *      (e.g. Are they a member in the alliance, are they active, not in VM etc.)
     *  - Add the default grant requirements to each grant type
     *      (e.g. Project grant requires the nation to not already have the project, have a free slot, be on the correct policy)
     *
     *  Tracking table
     *  - Tracks which grants a user has sent (sender id, receiver id, grant type, amount, date)
     *  - Avoids needing to parse the notes
     *
     *  Ensure grant sending is atomic
     *
     *  Grant send command
     *  Priority support for:
     *   - Projects
     *   - Cities
     *   - Land
     *   - Infra
     *  Low priority
     *   - Builds
     *   - Raws
     *   - Warchest
     *   - Support for partial grants
     *
     *  Nice to have:
     *   - Bank record note parsing to check if they already received a grant (e.g. already got an infra grant to X level)
     *   This will then support tracking transfers sent via `!grant` (due to the note)
     *
     *  Grant update command (low priority) - can just delete remake a grant in the meantime
     *      - Make the create commands modify an existing grant (and add a title/message to the confirmation dialog)
     *      - Needs checking to ensure the grant type is the same
     *
     *  Grant requirements
     *   - Add command to add/remove custom requirements
     *
     */
    private final GuildDB db;
    public Map<String, AGrantTemplate> templates = new ConcurrentHashMap<>();

    public GrantTemplateManager(GuildDB db) {
        this.db = db;
    }

    public Set<AGrantTemplate> getTemplates() {
        return Set.copyOf(templates.values());
    }

    public Set<AGrantTemplate> getTemplates(TemplateTypes type) {
        return Set.copyOf(templates.values().stream().filter(t -> type == null || t.getType() == type).toList());
    }

    public void createTables() {
        String projects = "CREATE TABLE IF NOT EXISTS `GRANT_TEMPLATE_PROJECT` " +
                "(`enabled` INTEGER NOT NULL, " +
                "`name` VARCHAR NOT NULL PRIMARY KEY, " +
                "`project` BIGINT NOT NULL, " +
                "`nation_filter` VARCHAR NOT NULL, " +
                "`econ_role` BIGINT NOT NULL, " +
                "`self_role` BIGINT NOT NULL, " +
                "`from_bracket` BIGINT NOT NULL, " +
                "`use_receiver_bracket` BOOLEAN NOT NULL, " +
                "`max_total` INT NOT NULL, " +
                "`max_day` INT NOT NULL, " +
                "`max_granter_day` INT NOT NULL)";

        String cities = "CREATE TABLE IF NOT EXISTS `GRANT_TEMPLATE_CITY` " +
                "(`enabled` INTEGER NOT NULL, " +
                "`min_city` INT NOT NULL, " +
                "`max_city` INT NOT NULL, " +
                "`name` VARCHAR NOT NULL PRIMARY KEY, " +
                "`nation_filter` VARCHAR NOT NULL, " +
                "`econ_role` BIGINT NOT NULL, " +
                "`self_role` BIGINT NOT NULL, " +
                "`from_bracket` BIGINT NOT NULL, " +
                "`use_receiver_bracket` BOOLEAN NOT NULL, " +
                "`max_total` INT NOT NULL, " +
                "`max_day` INT NOT NULL, " +
                "`max_granter_day` INT NOT NULL)";

        String warchest = "CREATE TABLE IF NOT EXISTS `GRANT_TEMPLATE_WARCHEST` " +
                "(`enabled` INTEGER NOT NULL, " +
                "`allowance_per_city` BLOB NOT NULL, " +
                "`name` VARCHAR NOT NULL PRIMARY KEY, " +
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
        String infra = "CREATE TABLE IF NOT EXISTS `GRANT_TEMPLATE_INFRA` " +
                "(`enabled` INTEGER NOT NULL, " +
                "`level` BIGINT NOT NULL, " +
                "`only_new_cities` BOOLEAN NOT NULL, " +
                "`track_days` BOOLEAN NOT NULL, " +
                "`require_n_offensives` BIGINT NOT NULL, " +
                "`allow_rebuild` BOOLEAN NOT NULL, " +
                "`name` VARCHAR NOT NULL PRIMARY KEY, " +
                "`nation_filter` VARCHAR NOT NULL, " +
                "`econ_role` BIGINT NOT NULL, " +
                "`self_role` BIGINT NOT NULL, " +
                "`from_bracket` BIGINT NOT NULL, " +
                "`use_receiver_bracket` BOOLEAN NOT NULL, " +
                "`max_total` INT NOT NULL, " +
                "`max_day` INT NOT NULL, " +
                "`max_granter_day` INT NOT NULL)";
        String land = "CREATE TABLE IF NOT EXISTS `GRANT_TEMPLATE_LAND` " +
                "(`enabled` INTEGER NOT NULL, " +
                "`level` BIGINT NOT NULL, " +
                "`only_new_cities` BOOLEAN NOT NULL, " +
                "`name` VARCHAR NOT NULL PRIMARY KEY, " +
                "`nation_filter` VARCHAR NOT NULL, " +
                "`econ_role` BIGINT NOT NULL, " +
                "`self_role` BIGINT NOT NULL, " +
                "`from_bracket` BIGINT NOT NULL, " +
                "`use_receiver_bracket` BOOLEAN NOT NULL, " +
                "`max_total` INT NOT NULL, " +
                "`max_day` INT NOT NULL, " +
                "`max_granter_day` INT NOT NULL)";
        String build = "CREATE TABLE IF NOT EXISTS `GRANT_TEMPLATE_BUILD` " +
                "(`enabled` INTEGER NOT NULL, " +
                "`build` BLOB NOT NULL, " +
                "`use_optimal` BOOLEAN NOT NULL, " +
                "`mmr` BIGINT NOT NULL, " +
                "`track_days` BIGINT NOT NULL, " +
                "`allow_switch_after_offensive` BOOLEAN NOT NULL, " +
                "`name` VARCHAR NOT NULL PRIMARY KEY, " +
                "`nation_filter` VARCHAR NOT NULL, " +
                "`econ_role` BIGINT NOT NULL, " +
                "`self_role` BIGINT NOT NULL, " +
                "`from_bracket` BIGINT NOT NULL, " +
                "`use_receiver_bracket` BOOLEAN NOT NULL, " +
                "`max_total` INT NOT NULL, " +
                "`max_day` INT NOT NULL, " +
                "`max_granter_day` INT NOT NULL)";
        String raws = "CREATE TABLE IF NOT EXISTS `GRANT_TEMPLATE_RAWS` " +
                "(`enabled` INTEGER NOT NULL, " +
                "`days` BIGINT NOT NULL, " +
                "`overdraw_percent_cents` BIGINT NOT NULL, " +
                "`name` VARCHAR NOT NULL PRIMARY KEY, " +
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

    public void loadTemplates() throws SQLException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Map<String, AGrantTemplate> templates = new HashMap<>();

        for (TemplateTypes type : TemplateTypes.values()) {
            String query = "SELECT * FROM `" + type.getTable() + "`";

            try (PreparedStatement stmt = db.prepareQuery(query)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        AGrantTemplate template = type.create(db, rs);
                        templates.put(template.getName(), template);
                    }
                }
            }
        }
    }

    public void saveTemplate(AGrantTemplate template) {
        String query = template.createQuery();
        try (PreparedStatement stmt = db.prepareQuery(query)) {
            template.setValues(stmt);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Set<AGrantTemplate> getTemplateMatching(Predicate<AGrantTemplate> predicate) {
        return templates.values().stream().filter(predicate).collect(Collectors.toSet());
    }
}
