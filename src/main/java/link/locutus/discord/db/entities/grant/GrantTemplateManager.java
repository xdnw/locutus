package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.NationFilterString;
import link.locutus.discord.util.math.ArrayUtil;
import org.jooq.meta.derby.sys.Sys;

import java.lang.reflect.InvocationTargetException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class GrantTemplateManager {
    /**
     * TODO
     *
     * TODO:
     *  handling for "`track_days` BOOLEAN NOT NULL, " +
     *                 "`require_n_offensives` BIGINT NOT NULL, " +
     *                 "`allow_rebuild` BOOLEAN NOT NULL, " +
*                 int infra grant
 *  Note parsing for raws grant
     *  Warchest grant is TODO
     *  Info command shows example of recreating the grant with same settings
     *  Full info command for each
     *  Add default requirements for raws etc.
     *
     *  Default requirements
     *  - Add the default grant requirements to AGrantTemplate
     *      (e.g. Are they a member in the alliance, are they active, not in VM etc.)
     *  - Add the default grant requirements to each grant type
     *      (e.g. Project grant requires the nation to not already have the project, have a free slot, be on the correct policy)
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
     *  AGrantTemplate binding in PWBindings
     *
     *  Nice to have:
     *   - Bank record note parsing to check if they already received a grant (e.g. already got an infra grant to X level)
     *   This will then support tracking transfers sent via `!grant` (due to the note)
     *
     *  Completions in PWCompleter for grant templates
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
        createTables();
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
                "`date_created` BIGINT NOT NULL, " +
                "`name` VARCHAR NOT NULL PRIMARY KEY, " +
                "`project` BIGINT NOT NULL, " +
                "`nation_filter` VARCHAR NOT NULL, " +
                "`econ_role` BIGINT NOT NULL, " +
                "`self_role` BIGINT NOT NULL, " +
                "`from_bracket` BIGINT NOT NULL, " +
                "`use_receiver_bracket` BOOLEAN NOT NULL, " +
                "`max_total` INT NOT NULL, " +
                "`max_day` INT NOT NULL, " +
                "`max_granter_day` INT NOT NULL, " +
                "`max_granter_total` INT NOT NULL, " +
                "`expire` BIGINT NOT NULL, " +
                "`allow_ignore` BOOLEAN NOT NULL)";

        String cities = "CREATE TABLE IF NOT EXISTS `GRANT_TEMPLATE_CITY` " +
                "(`enabled` INTEGER NOT NULL, " +
                "`min_city` INT NOT NULL, " +
                "`max_city` INT NOT NULL, " +
                "`date_created` BIGINT NOT NULL, " +
                "`name` VARCHAR NOT NULL PRIMARY KEY, " +
                "`nation_filter` VARCHAR NOT NULL, " +
                "`econ_role` BIGINT NOT NULL, " +
                "`self_role` BIGINT NOT NULL, " +
                "`from_bracket` BIGINT NOT NULL, " +
                "`use_receiver_bracket` BOOLEAN NOT NULL, " +
                "`max_total` INT NOT NULL, " +
                "`max_day` INT NOT NULL, " +
                "`max_granter_day` INT NOT NULL, " +
                "`max_granter_total` INT NOT NULL, " +
                "`expire` BIGINT NOT NULL, " +
                "`allow_ignore` BOOLEAN NOT NULL)";

        String warchest = "CREATE TABLE IF NOT EXISTS `GRANT_TEMPLATE_WARCHEST` " +
                "(`enabled` INTEGER NOT NULL, " +
                "`allowance_per_city` BLOB NOT NULL, " +
                "`date_created` BIGINT NOT NULL, " +
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
                "`max_granter_day` INT NOT NULL, " +
                "`max_granter_total` INT NOT NULL, " +
                "`expire` BIGINT NOT NULL, " +
                "`allow_ignore` BOOLEAN NOT NULL)";
        String infra = "CREATE TABLE IF NOT EXISTS `GRANT_TEMPLATE_INFRA` " +
                "(`enabled` INTEGER NOT NULL, " +
                "`level` BIGINT NOT NULL, " +
                "`only_new_cities` BOOLEAN NOT NULL, " +
                "`require_n_offensives` BIGINT NOT NULL, " +
                "`allow_rebuild` BOOLEAN NOT NULL, " +
                "`date_created` BIGINT NOT NULL, " +
                "`name` VARCHAR NOT NULL PRIMARY KEY, " +
                "`nation_filter` VARCHAR NOT NULL, " +
                "`econ_role` BIGINT NOT NULL, " +
                "`self_role` BIGINT NOT NULL, " +
                "`from_bracket` BIGINT NOT NULL, " +
                "`use_receiver_bracket` BOOLEAN NOT NULL, " +
                "`max_total` INT NOT NULL, " +
                "`max_day` INT NOT NULL, " +
                "`max_granter_day` INT NOT NULL, " +
                "`max_granter_total` INT NOT NULL, " +
                "`expire` BIGINT NOT NULL, " +
                "`allow_ignore` BOOLEAN NOT NULL)";
        String land = "CREATE TABLE IF NOT EXISTS `GRANT_TEMPLATE_LAND` " +
                "(`enabled` INTEGER NOT NULL, " +
                "`level` BIGINT NOT NULL, " +
                "`only_new_cities` BOOLEAN NOT NULL, " +
                "`date_created` BIGINT NOT NULL, " +
                "`name` VARCHAR NOT NULL PRIMARY KEY, " +
                "`nation_filter` VARCHAR NOT NULL, " +
                "`econ_role` BIGINT NOT NULL, " +
                "`self_role` BIGINT NOT NULL, " +
                "`from_bracket` BIGINT NOT NULL, " +
                "`use_receiver_bracket` BOOLEAN NOT NULL, " +
                "`max_total` INT NOT NULL, " +
                "`max_day` INT NOT NULL, " +
                "`max_granter_day` INT NOT NULL, " +
                "`max_granter_total` INT NOT NULL, " +
                "`expire` BIGINT NOT NULL, " +
                "`allow_ignore` BOOLEAN NOT NULL)";
        String build = "CREATE TABLE IF NOT EXISTS `GRANT_TEMPLATE_BUILD` " +
                "(`enabled` INTEGER NOT NULL, " +
                "`build` BLOB NOT NULL, " +
                "`only_new_cities` BOOLEAN NOT NULL, " +
                "`mmr` BIGINT NOT NULL, " +
                "`allow_switch_after_days` BIGINT NOT NULL, " +
                "`allow_switch_after_offensive` BOOLEAN NOT NULL, " +
                "`allow_switch_after_infra` BOOLEAN NOT NULL, " +
                "`allow_switch_after_land_or_project` BOOLEAN NOT NULL, " +
                "`allow_all` BOOLEAN NOT NULL, " +
                "`date_created` BIGINT NOT NULL, " +
                "`name` VARCHAR NOT NULL PRIMARY KEY, " +
                "`nation_filter` VARCHAR NOT NULL, " +
                "`econ_role` BIGINT NOT NULL, " +
                "`self_role` BIGINT NOT NULL, " +
                "`from_bracket` BIGINT NOT NULL, " +
                "`use_receiver_bracket` BOOLEAN NOT NULL, " +
                "`max_total` INT NOT NULL, " +
                "`max_day` INT NOT NULL, " +
                "`max_granter_day` INT NOT NULL, " +
                "`max_granter_total` INT NOT NULL, " +
                "`expire` BIGINT NOT NULL, " +
                "`allow_ignore` BOOLEAN NOT NULL)";
        String raws = "CREATE TABLE IF NOT EXISTS `GRANT_TEMPLATE_RAWS` " +
                "(`enabled` INTEGER NOT NULL, " +
                "`days` BIGINT NOT NULL, " +
                "`overdraw_percent_cents` BIGINT NOT NULL, " +
                "`date_created` BIGINT NOT NULL, " +
                "`name` VARCHAR NOT NULL PRIMARY KEY, " +
                "`nation_filter` VARCHAR NOT NULL, " +
                "`econ_role` BIGINT NOT NULL, " +
                "`self_role` BIGINT NOT NULL, " +
                "`from_bracket` BIGINT NOT NULL, " +
                "`use_receiver_bracket` BOOLEAN NOT NULL, " +
                "`max_total` INT NOT NULL, " +
                "`max_day` INT NOT NULL, " +
                "`max_granter_day` INT NOT NULL, " +
                "`max_granter_total` INT NOT NULL, " +
                "`expire` BIGINT NOT NULL, " +
                "`allow_ignore` BOOLEAN NOT NULL)";

        // grants_sent (long sender id, long receiver id, String grant, int grant type, byte[] amount, long date)
        String grants_sent = "CREATE TABLE IF NOT EXISTS `GRANTS_SENT` " +
                "(`sender_id` BIGINT NOT NULL, " +
                "`receiver_id` BIGINT NOT NULL, " +
                "`grant` VARCHAR NOT NULL," +
                "`grant_type` INT NOT NULL, " +
                "`amount` BLOB NOT NULL, " +
                "`date` BIGINT NOT NULL, " +
                "`expire` BIGINT NOT NULL, " +
                "`allow_ignore` BOOLEAN NOT NULL)";

//        for (TemplateTypes value : TemplateTypes.values()) {
//            String tableName = value.getTable();
//            // drop table
//            db.executeStmt("DROP TABLE IF EXISTS `" + tableName + "`");
//        }

        db.executeStmt(projects);
        db.executeStmt(cities);
        db.executeStmt(warchest);
        db.executeStmt(land);
        db.executeStmt(infra);
        db.executeStmt(build);
        db.executeStmt(raws);
        db.executeStmt(grants_sent);

        for (TemplateTypes value : TemplateTypes.values()) {
            String tableName = value.getTable();
            db.executeStmt("ALTER TABLE `" + tableName + "` ADD COLUMN `repeatable` BOOLEANT NOT NULL DEFAULT 0");
        }
    }

    public void deleteTemplate(AGrantTemplate template) {
        // remove from map
        templates.remove(template.getName());

        String table = template.getType().getTable();
        String name = template.getName();
        synchronized (db) {
            db.logDeletion(table, System.currentTimeMillis(), "name", name);
            String sql = "DELETE FROM `" + table + "` WHERE `name` = ?";
            try (PreparedStatement stmt = db.prepareQuery(sql)) {
                stmt.setString(1, name);
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static class GrantSendRecord {
        public final String grant;
        public final int sender_id;
        public final int receiver_id;
        public final TemplateTypes grant_type;
        public final double[] amount;
        public final long date;

        // constructor
        public GrantSendRecord(String grant, int sender_id, int receiver_id, TemplateTypes grant_type, double[] amount, long date) {
            this.grant = grant;
            this.sender_id = sender_id;
            this.receiver_id = receiver_id;
            this.grant_type = grant_type;
            this.amount = amount;
            this.date = date;
        }

        public GrantSendRecord(ResultSet rs) throws SQLException {
//            rs.getString("grant"),
//                    rs.getInt("sender_id"),
//                    rs.getInt("receiver_id"),
//                    TemplateTypes.values()[rs.getInt("grant_type")],
//                    (double[]) rs.getObject("amount"),
//                    rs.getLong("date")
                    this(
                    rs.getString("grant"),
                    rs.getInt("sender_id"),
                    rs.getInt("receiver_id"),
                    TemplateTypes.values[rs.getInt("grant_type")],
                    ArrayUtil.toDoubleArray(rs.getBytes("amount")),
                    rs.getLong("date"));
        }
    }

    public List<GrantSendRecord> getRecordsByGrant(String grant) {
        List<GrantSendRecord> records = new ArrayList<>();
        String query = "SELECT * FROM `GRANTS_SENT` WHERE `grant` = ?";
        try (PreparedStatement stmt = db.prepareQuery(query)) {
            stmt.setString(1, grant);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    records.add(new GrantSendRecord(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return records;
    }

    public List<GrantSendRecord> getRecordsBySender(int senderId) {
        List<GrantSendRecord> records = new ArrayList<>();
        String query = "SELECT * FROM `GRANTS_SENT` WHERE `sender_id` = ?";
        try (PreparedStatement stmt = db.prepareQuery(query)) {
            stmt.setInt(1, senderId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    records.add(new GrantSendRecord(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return records;
    }

    public List<GrantSendRecord> getRecordsBySender(int senderId, String grant) {
        List<GrantSendRecord> records = new ArrayList<>();
        String query = "SELECT * FROM `GRANTS_SENT` WHERE `sender_id` = ? AND `grant` = ?";
        try (PreparedStatement stmt = db.prepareQuery(query)) {
            stmt.setInt(1, senderId);
            stmt.setString(2, grant);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    records.add(new GrantSendRecord(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return records;
    }

    public List<GrantSendRecord> getRecordsByReceiver(int receiverId, String name) {
        List<GrantSendRecord> records = new ArrayList<>();
        String query = "SELECT * FROM `GRANTS_SENT` WHERE `receiver_id` = ? AND `grant` = ?";
        try (PreparedStatement stmt = db.prepareQuery(query)) {
            stmt.setInt(1, receiverId);
            stmt.setString(2, name);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    records.add(new GrantSendRecord(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return records;
    }

    public List<GrantSendRecord> getRecordsByReceiver(int receiverId) {
        List<GrantSendRecord> records = new ArrayList<>();
        String query = "SELECT * FROM `GRANTS_SENT` WHERE `receiver_id` = ?";
        try (PreparedStatement stmt = db.prepareQuery(query)) {
            stmt.setInt(1, receiverId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    records.add(new GrantSendRecord(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return records;
    }

    public void saveGrantRecord(GrantSendRecord record) {
        String query = "INSERT INTO `GRANTS_SENT` (`grant`, `sender_id`, `receiver_id`, `grant_type`, `amount`, `date`) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = db.prepareQuery(query)) {
            stmt.setString(1, record.grant);
            stmt.setInt(2, record.sender_id);
            stmt.setInt(3, record.receiver_id);
            stmt.setInt(4, record.grant_type.ordinal());
            stmt.setObject(5, record.amount);
            stmt.setLong(6, record.date);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void loadTemplates() throws SQLException, InvocationTargetException, InstantiationException, IllegalAccessException {
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
        templates.put(template.getName(), template);
        String query = template.createQuery();
        try (PreparedStatement stmt = db.prepareQuery(query)) {
            template.setValuesBase(stmt);
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
