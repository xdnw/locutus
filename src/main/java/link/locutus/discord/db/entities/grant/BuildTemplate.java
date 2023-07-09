package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.Grant;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class BuildTemplate extends AGrantTemplate<CityBuild> {
    private final byte[] build;
    private final boolean onlyNewCities;
    private final int mmr;
    private final long allow_switch_after_days;
    private final boolean allow_switch_after_offensive;
    private final boolean allow_switch_after_infra;
    private final boolean allow_switch_after_land_or_project;

    public BuildTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        this(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, rs.getBytes("build"), rs.getBoolean("only_new_cities"), rs.getInt("mmr"), rs.getLong("track_days"), rs.getBoolean("allow_switch_after_offensive"));
    }

    // create new constructor  with typed parameters instead of resultset
    public BuildTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, byte[] build, boolean onlyNewCities, int mmr, long track_days, boolean allow_switch_after_offensive) {
        super(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal);
        this.build = build;
        this.onlyNewCities = onlyNewCities;
        this.mmr = mmr;
        this.track_days = track_days;
        this.allow_switch_after_offensive = allow_switch_after_offensive;
    }

    @Override
    public String toListString() {
        StringBuilder result = new StringBuilder(super.toListString());
        if (mmr > 0) {
            // format int to 4 digits with 0 padding (before the number)
            String mmrString = String.format("%04d", mmr);
            result.append(" | MMR=").append(mmrString);
        }
        if (onlyNewCities) {
            result.append(" | new_cities=true");
        }
        if (allow_switch_after_offensive) {
            result.append(" | damaged=allow");
        }
        return result.toString();
    }

    @Override
    public TemplateTypes getType() {
        return TemplateTypes.BUILD;
    }

    @Override
    public List<String> getQueryFields() {
        List<String> list = getQueryFieldsBase();
        list.add("build");
        list.add("use_optimal");
        list.add("mmr");
        list.add("track_days");
        list.add("allow_switch_after_offensive");
        return list;
    }

    @Override
    public void setValues(PreparedStatement stmt) throws SQLException {
        stmt.setBytes(12, build);
        stmt.setBoolean(13, onlyNewCities);
        stmt.setLong(14, mmr);
        stmt.setLong(15, track_days);
        stmt.setBoolean(16, allow_switch_after_offensive);
    }

    @Override
    public CityBuild parse(DBNation receiver, String value) {
        if (value == null) {
            // get infra in last city

            // generate
        } else {
            return super.parse(receiver, value);
        }
    }

    //make build template command open to members
    //will check if member has bought a city recently
    //will also check if member has used a build grant for their new city to prevent abuse
    //should probly consider dm'ing the user to use the city build grant command once the city grant command is ran
    @Override
    public List<Grant.Requirement> getDefaultRequirements(DBNation sender, DBNation receiver, CityBuild build) {
        List<Grant.Requirement> list = super.getDefaultRequirements(sender, receiver, build);

        if (build == null) {
            // if build is null generate new optimal build
        } else {
            //TODO validate build is valid
            //TODO validate build matches current infra level ??
        }

        if (onlyNewCities) {
            list.add(new Grant.Requirement("Nation hasn't bought a city in the past 6 days", true, new Function<DBNation, Boolean>() {
                @Override
                public Boolean apply(DBNation receiver) {

                    return receiver.getCitiesSince(TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - 72)) > 0;
                }
            }));

            list.add(new Grant.Requirement("Nation has already received a new city build grant", true, new Function<DBNation, Boolean>() {
                @Override
                public Boolean apply(DBNation receiver) {

                    List<GrantTemplateManager.GrantSendRecord> records = getDb().getGrantTemplateManager().getRecordsByReceiver(receiver.getId());

                    for(GrantTemplateManager.GrantSendRecord record : records) {

                        return receiver.getCitiesSince(record.date) == 0;
                    }

                    return true;
                }
            }));
        }

        // require no build grants in past track_days

        // else require war in past track_days

        // else require infra in past track_days (in all cities)

        // for single:
        // require city built in the past day
        // require no build grant since the city

        return list;
    }

    private Set<Integer> getCitiesToGrantTo(DBNation receiver) {
        Map<Integer, DBCity> cities = receiver._getCitiesV3();
        Map<Integer, Long> createDate = new HashMap<>();

        long buildDate = 0;
        long infraDate = 0;
        long projectOrLandDate = 0;
        for (Map.Entry<Integer, DBCity> entry : cities.entrySet()) {
            DBCity city = entry.getValue();
            createDate.put(entry.getKey(), city.created);
        }

        long lastAttackDate = 0;
        if (allow_switch_after_offensive) {
            // get attacks where attacker
            List<DBWar> wars = receiver.getWars();
            wars.removeIf(f -> f.attacker_id != receiver.getId());
            // sort wars date desc
            Collections.sort(wars, (o1, o2) -> Long.compare(o2.date, o1.date));
            outer:
            for (DBWar war : wars) {
                List<DBAttack> attacks = war.getAttacks();
                // reverse attacks
                Collections.reverse(attacks);
                for (DBAttack attack : attacks) {
                    if (attack.getAttacker_nation_id() != receiver.getId()) {
                        lastAttackDate = attack.getDate();
                        break outer;
                    }
                }
            }
        }

        // get grants
        for (GrantTemplateManager.GrantSendRecord record : getDb().getGrantTemplateManager().getRecordsByReceiver(receiver.getId())) {
            // buildDate = Math.max(buildDate, record.date);
            switch (record.grant_type) {
                case BUILD:
                    buildDate = Math.max(buildDate, record.date);
                    break;
                case INFRA:
                    infraDate = Math.max(infraDate, record.date);
                    break;
                case PROJECT:
                case LAND:
                    projectOrLandDate = Math.max(projectOrLandDate, record.date);
                    break;
            }
        }
        Set<Integer> citiesToGrant = new HashSet<>();
        long cutoff = onlyNewCities ? TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - 119) : 0;
        for (Map.Entry<Integer, Long> entry : createDate.entrySet()) {
            long date = entry.getValue();
            if (date < cutoff) continue;
            boolean allowGrant = true;
            if (buildDate > date) {
                allowGrant = false;
                if (infraDate > buildDate && allow_switch_after_infra) {
                    allowGrant = true;
                }
                if (projectOrLandDate > buildDate && allow_switch_after_land_or_project) {
                    allowGrant = true;
                }
                if (lastAttackDate > date && allow_switch_after_offensive) {
                    allowGrant = true;
                }
            }
            if (allowGrant) {
                citiesToGrant.add(entry.getKey());
            }
        }
        return citiesToGrant;
    }

    @Override
    public double[] getCost(DBNation sender, DBNation receiver, CityBuild build) {
        int cities = receiver.getCities();
        // todo get cost of build
        JavaCity to = new JavaCity(build);
        JavaCity from = new JavaCity(build);
        Arrays.fill(from.getBuildings(), (byte) 0);
        double[] cost = to.calculateCost(from);
        if (onlyNewCities) {
            // get cities in past 10 days
            // multiply by cities since last city
            receiver.getCitiesSince(TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - 119));
        } else {
            // multiply by cities
            cost = PnwUtil.multiply(cost, cities);
        }
        return cost;
    }

    @Override
    public DepositType.DepositTypeInfo getDepositType(DBNation receiver, CityBuild build) {
        int cities = 0;
        if (onlyNewCities) {
            cities = 1;
        } else {
            cities = receiver.getCities();
        }
        return DepositType.BUILD.withAmount(cities);
    }

    @Override
    public String getInstructions(DBNation sender, DBNation receiver, CityBuild parsed) {
        StringBuilder instructions = new StringBuilder();
        if (onlyNewCities) {

        } else {
            instructions.append("Go to <https://politicsandwar.com/city/improvements/bulk-import/> and import the build:\n");
        }
        instructions.append("```json\n");
        instructions.append(parsed.toString());
        instructions.append("\n```");
        return instructions.toString();
    }

    @Override
    public Class<CityBuild> getParsedType() {
        return CityBuild.class;
    }
}
