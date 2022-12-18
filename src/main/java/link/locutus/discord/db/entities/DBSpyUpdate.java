package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.enums.WarPolicy;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DBSpyUpdate {
    public int nation_id;
    public long timestamp;
    public long projects;
    public WarPolicy policy;
    public int spies;
    
    public DBSpyUpdate(ResultSet rs) throws SQLException {
        this.nation_id = rs.getInt("nation");
        this.timestamp = rs.getLong("timestamp");
        this.projects = rs.getLong("projects");
        long policyId = rs.getLong("change");
        this.policy = policyId < WarPolicy.values.length ? WarPolicy.values[(int) policyId] : null;
        this.spies = rs.getInt("spies");
    }
}
