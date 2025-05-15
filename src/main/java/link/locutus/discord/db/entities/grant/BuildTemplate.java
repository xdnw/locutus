package link.locutus.discord.db.entities.grant;

import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.MMRInt;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.Grant;

import javax.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class BuildTemplate extends AGrantTemplate<Map<Integer, CityBuild>> {
    private final byte[] build;
    private final boolean onlyNewCities;
    private final MMRInt mmr;
    private final long allow_switch_after_days;
    private final boolean allow_switch_after_offensive;
    private final boolean allow_switch_after_infra;
    private final boolean allow_switch_after_land_or_project;
    private final boolean allow_all;
    private final boolean includeInfra;
    private final int includeLand;

    public BuildTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        this(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, rs.getLong("date_created"), rs.getBytes("build"), rs.getBoolean("only_new_cities"),
                rs.getInt("mmr"),
                rs.getLong("allow_switch_after_days"),
                rs.getBoolean("allow_switch_after_offensive"),
                rs.getBoolean("allow_switch_after_infra"),
                rs.getBoolean("allow_switch_after_land_or_project"),
                rs.getBoolean("allow_all"),
                rs.getLong("expire"),
                rs.getLong("decay"),
                rs.getBoolean("allow_ignore"),
                rs.getLong("repeatable"),
                rs.getBoolean("include_infra"),
                rs.getInt("include_land")
        );
    }

    // create new constructor  with typed parameters instead of resultset
    public BuildTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long dateCreated, byte[] build, boolean onlyNewCities, int mmr,
                         long allow_switch_after_days,
                         boolean allow_switch_after_offensive,
                         boolean allow_switch_after_infra,
                         boolean allow_switch_after_land_or_project,
                         boolean allow_all, long expiryOrZero, long decayOrZero, boolean allowIgnore,
                         long repeatable_time,
                            boolean includeInfra, int includeLand
    ) {
        super(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, dateCreated, expiryOrZero, decayOrZero, allowIgnore, repeatable_time);
        this.build = build == null || build.length == 0 ? null : build;
        this.onlyNewCities = onlyNewCities;
        this.mmr = mmr <= 0 ? null : MMRInt.fromString(String.format("%04d", mmr));
        this.allow_switch_after_days = allow_switch_after_days;
        this.allow_switch_after_offensive = allow_switch_after_offensive;
        this.allow_switch_after_infra = allow_switch_after_infra;
        this.allow_switch_after_land_or_project = allow_switch_after_land_or_project;
        this.allow_all = allow_all;
        this.includeInfra = includeInfra;
        this.includeLand = includeLand;
    }

    @Override
    public String toInfoString(DBNation sender, DBNation receiver,  Map<Integer, CityBuild> parsed) {

        StringBuilder message = new StringBuilder();

        if(build != null)
            message.append("build: ```json\n" + new JavaCity().fromBytes(build).toJson(false) + "\n```\n");

        message.append("Only New Cities: `" + onlyNewCities + "`\n");
        if (mmr != null) {
            message.append("MMR: `" + String.format("%04d", mmr) + "`\n");
        }
        if (allow_switch_after_days > 0) message.append("Allow Switch After Days: `" + allow_switch_after_days + "`\n");
        if (allow_switch_after_offensive) message.append("Allow Switch After Offensive: `" + allow_switch_after_offensive + "`\n");
        if (allow_switch_after_infra) message.append("Allow Switch After Infra: `" + allow_switch_after_infra + "`\n");
        message.append("Allow All: `" + allow_all + "`\n");
        if (includeInfra) {
            message.append("Include Infra: `true`\n");
        }
        if (includeLand > 0) {
            message.append("Include Land: `" + includeLand + "`\n");
        }

        return message.toString();
    }

    @Override
    public String getCommandString(String name, String allowedRecipients, String econRole, String selfRole, String bracket, String useReceiverBracket, String maxTotal, String maxDay, String maxGranterDay, String maxGranterTotal, String allowExpire, String allowDecay, String allowIgnore, String repeatable) {
        return CM.grant_template.create.build.cmd.name(name).allowedRecipients(allowedRecipients).build(
                build != null ? JavaCity.fromBytes(build).toJson(false) : null).mmr(
                mmr == null ? null : mmr.toString()).only_new_cities(
                onlyNewCities ? "true" : null).allow_after_days(
                allow_switch_after_days > 0 ? allow_switch_after_days + "" : null).allow_after_offensive(
                allow_switch_after_offensive ? "true" : null).allow_after_infra(
                allow_switch_after_infra ? "true" : null).allow_all(
                allow_all ? "true" : null).allow_after_land_or_project(
                allow_switch_after_land_or_project ? "true" : null).econRole(
                econRole).selfRole(
                selfRole).bracket(
                bracket).useReceiverBracket(
                useReceiverBracket).maxTotal(
                maxTotal).maxDay(
                maxDay).maxGranterDay(
                maxGranterDay).maxGranterTotal(
                maxGranterTotal).expireTime(
                allowExpire).decayTime(
                allowDecay).allowIgnore(
                allowIgnore).repeatable_time(
                repeatable)
                .include_infra(includeInfra ? "true" : null)
                .include_land(includeLand > 0 ? includeLand + "" : null).toString();
    }

    @Override
    public String toListString() {
        StringBuilder result = new StringBuilder(super.toListString());
        if (mmr != null) {
            // format int to 4 digits with 0 padding (before the number)
            String mmrString = mmr.toString();
            result.append(" | MMR=").append(mmrString);
        }
        if (onlyNewCities) {
            result.append(" | new_cities=true");
        }
        if (allow_switch_after_offensive) {
            result.append(" | damaged=allow");
        }
        if (includeInfra) {
            result.append(" | infra");
        }
        if (includeLand > 0) {
            result.append(" | land=" + includeLand);
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
        list.add("only_new_cities");
        list.add("mmr");
        list.add("allow_switch_after_days");
        list.add("allow_switch_after_offensive");
        list.add("allow_switch_after_infra");
        list.add("allow_switch_after_land_or_project");
        list.add("allow_all");
        list.add("include_infra");
        list.add("include_land");
        return list;
    }

    @Override
    public void setValues(PreparedStatement stmt) throws SQLException {
        stmt.setBytes(17, build == null ? new byte[0] : build);
        stmt.setBoolean(18, onlyNewCities);
        stmt.setLong(19, mmr == null ? -1 : mmr.toNumber());
        stmt.setLong(20, allow_switch_after_days);
        stmt.setBoolean(21, allow_switch_after_offensive);
        stmt.setBoolean(22, allow_switch_after_infra);
        stmt.setBoolean(23, allow_switch_after_land_or_project);
        stmt.setBoolean(24, allow_all);
        stmt.setBoolean(25, includeInfra);
        stmt.setInt(26, includeLand);
    }

    @Override
    public Map<Integer, CityBuild> parse(DBNation receiver, String value) {
        CityBuild build;
        if (value == null) {
            JavaCity city;
            boolean isEmpty = this.build == null;
            if (!isEmpty) {
                for (byte b : this.build) {
                    if (b != 0) {
                        isEmpty = false;
                        break;
                    }
                }
            }
            if (!isEmpty) {
                city = JavaCity.fromBytes(this.build);
                if (mmr != null) {
                    city.setMMR(mmr);
                }
                build = city.toCityBuild();
            } else {
                // get infra in last city
                Map<Integer, DBCity> cities = receiver._getCitiesV3();
                // get city with largest id key
                int lastCity = Collections.max(cities.keySet());
                city = cities.get(lastCity).toJavaCity(receiver);
                // mmr
                if (mmr != null) {
                    city.setMMR(mmr);
                }
                city.zeroNonMilitary();
                city.optimalBuild(receiver, 5000, false, null);
                // generate
                build = city.toCityBuild();
            }
        } else {
            if (this.build != null) {
                throw new IllegalArgumentException("This template has a build set, you cannot specify a value");
            }
            build = parse(getDb(), receiver, value, CityBuild.class);
            List<String> errors = new ArrayList<>();
            if (this.mmr != null) {
                if (build.getImpBarracks() != mmr.getBarracks()) {
                    errors.add("Barracks must be " + mmr.getBarracks() + ", not " + build.getImpBarracks());
                }
                if (build.getImpFactory() != mmr.getFactory()) {
                    errors.add("Factories must be " + mmr.getFactory() + ", not " + build.getImpFactory());
                }
                if (build.getImpHangars() != mmr.getHangar()) {
                    errors.add("Hangars must be " + mmr.getHangar() + ", not " + build.getImpHangars());
                }
                if (build.getImpDrydock() != mmr.getDrydock()) {
                    errors.add("Drydocks must be " + mmr.getDrydock() + ", not " + build.getImpDrydock());
                }
            }
            if (includeLand > 0 && build.getLand() != null && build.getLand() > includeLand) {
                errors.add("Land must be less than or equal to " + includeLand + ", not " + build.getLand());
            }
        }
        build.setInfraNeeded(new JavaCity(build).getRequiredInfra());

        if (includeLand > 0) {
            build.setLand((double) includeLand);
        }

        Set<Integer> grantTo = getCitiesToGrantTo(receiver);

        if (grantTo.isEmpty()) {
            String message = "No eligable cities to grant to. Ensure you have not already received a build grant";
            if (onlyNewCities) {
                message += " and that you have built a city in the past 10 days";
            }
            throw new IllegalArgumentException(message);
        }

        // get max infra
        double maxInfra = 0;
        for (Map.Entry<Integer, DBCity> entry : receiver._getCitiesV3().entrySet()) {
            maxInfra = Math.max(maxInfra, entry.getValue().getInfra());
        }
        // ensure build matches infra level
        {
            JavaCity jc = new JavaCity(build);
            if (jc.getRequiredInfra() > maxInfra) {
                throw new IllegalArgumentException("Build requires more infra than the receiver has: " + jc.getRequiredInfra() + " > " + maxInfra);
            }
            // ensure no buildings are negative
            for (Building building : Buildings.values()) {
                int amt = jc.getBuilding(building);
                if (amt < 0) {
                    throw new IllegalArgumentException("Build has negative " + building.name() + " buildings");
                }
                if (amt > building.getCap(f -> true)) {
                    throw new IllegalArgumentException("Build has more than " + building.getCap(f -> true) + " " + building.name() + " buildings");
                }
            }
            // no more than 2 power plants
            if (jc.getBuilding(Buildings.NUCLEAR_POWER) > 2) {
                throw new IllegalArgumentException("Build has more than 2 nuclear power plants");
            }
            if (jc.getBuilding(Buildings.WIND_POWER) > 2) {
                throw new IllegalArgumentException("Build has more than 2 wind power plants");
            }
            if (jc.getBuilding(Buildings.COAL_MINE) > 8) {
                throw new IllegalArgumentException("Build has more than 8 coal mines");
            }
            if (jc.getBuilding(Buildings.OIL_POWER) > 8) {
                throw new IllegalArgumentException("Build has more than 8 oil power");
            }
            // 5,5,5,3 max military buildings
            if (jc.getBuilding(Buildings.BARRACKS) > Buildings.BARRACKS.cap(f -> true)) {
                throw new IllegalArgumentException("Build has more than " + Buildings.BARRACKS.cap(f -> true) + " barracks");
            }
            if (jc.getBuilding(Buildings.FACTORY) > Buildings.FACTORY.cap(f -> true)) {
                throw new IllegalArgumentException("Build has more than " + Buildings.FACTORY.cap(f -> true) + " factories");
            }
            if (jc.getBuilding(Buildings.HANGAR) > Buildings.HANGAR.cap(f -> true)) {
                throw new IllegalArgumentException("Build has more than " + Buildings.HANGAR.cap(f -> true) + " hangars");
            }
            if (jc.getBuilding(Buildings.DRYDOCK) > Buildings.DRYDOCK.cap(f -> true)) {
                throw new IllegalArgumentException("Build has more than " + Buildings.DRYDOCK.cap(f -> true) + " drydocks");
            }
        }
        // return map of city and build
        Map<Integer, CityBuild> map = new HashMap<>();
        for (Integer city : grantTo) {
            map.put(city, build);
        }
        return map;
    }

    @Override
    public List<Grant.Requirement> getDefaultRequirements(GuildDB db, @Nullable DBNation sender, @Nullable DBNation receiver, Map<Integer, CityBuild> build, boolean confirm) {
            List<Grant.Requirement> list = super.getDefaultRequirements(db, sender, receiver, build, confirm);
            list.addAll(getRequirements(db, sender, receiver, this, build));
            return list;
    }

    public static List<Grant.Requirement> getRequirements(GuildDB db, DBNation sender, DBNation receiver, BuildTemplate template, Map<Integer, CityBuild> parsed) {
        List<Grant.Requirement> list = new ArrayList<>();
        // cap at 4k infra worth of buildings
        list.add(new Grant.Requirement("Build must NOT have more than 80 buildings (4k infra)", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                for (CityBuild value : parsed.values()) {
                    if (new JavaCity(value).getNumBuildings() > 80) {
                        return false;
                    }
                }
                return true;
            }
        }));
        if (template == null || template.includeLand > 0) {
            list.add(new Grant.Requirement("Build must NOT have more than 5000 land", false, new Function<DBNation, Boolean>() {
                @Override
                public Boolean apply(DBNation receiver) {
                    for (CityBuild value : parsed.values()) {
                        if (value.getLand() != null && value.getLand() > 5000) {
                            return false;
                        }
                    }
                    return true;
                }
            }));
        }

        if (template == null || template.onlyNewCities) {
            list.add(new Grant.Requirement("Must have purchased a city in the past 10 days (when `onlyNewCities: True`)", true, new Function<DBNation, Boolean>() {
                @Override
                public Boolean apply(DBNation receiver) {
                    return template == null || receiver.getCitiesSince(TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - 119)) > 0;
                }
            }));
        }

        list.add(new Grant.Requirement("Nation must NOT have received a new city build grant (when `repeatable: False`)", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                if (template == null || db == null) return true;
//                if (template.allow_switch_after_offensive || template.allow_switch_after_infra || template.allow_switch_after_land_or_project) return true;
                List<GrantTemplateManager.GrantSendRecord> records = db.getGrantTemplateManager().getRecordsByReceiver(receiver.getId());
                long repeatable = template.getRepeatable();
                if (repeatable > 0) {
                    long cutoff = System.currentTimeMillis() - repeatable;
                    records.removeIf(f -> f.date < cutoff);
                }
                long lastLand = 0;
                long lastProject = 0;
                long lastInfra = 0;
                // get transfers
                List<Map.Entry<Integer, Transaction2>> transactions = receiver.getTransactions(db, null, false, false, false, -1, 0, true);
                for (Map.Entry<Integer, Transaction2> entry : transactions) {
                    Transaction2 tx = entry.getValue();
                    if (tx.receiver_id != receiver.getId()) continue;
                    String note = tx.note;
                    if (note == null || note.isEmpty()) continue;
                    if (note.contains("#project")) lastProject = Math.max(lastProject, tx.tx_datetime);
                    if (note.contains("#land")) lastLand = Math.max(lastLand, tx.tx_datetime);
                    if (note.contains("#infra")) lastInfra = Math.max(lastInfra, tx.tx_datetime);
                }
                long lastLandOrProject = Math.max(lastLand, lastProject);

                for(GrantTemplateManager.GrantSendRecord record : records) {
                    if (template.allow_switch_after_days > 0 && record.date < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(template.allow_switch_after_days)) {
                        continue;
                    }
                    if (receiver.getCitiesSince(record.date) == 0 && record.grant_type == TemplateTypes.BUILD) {
                        return false;
                    }
                    if (template.allow_switch_after_infra && record.grant_type == TemplateTypes.INFRA && record.date > lastInfra) {
                        return false;
                    }
                    if (template.allow_switch_after_land_or_project && (record.grant_type == TemplateTypes.LAND || record.grant_type == TemplateTypes.PROJECT) && record.date > lastLandOrProject) {
                        return false;
                    }
                }

                return true;
            }
        }));

        list.add(new Grant.Requirement("Cannot build in " + (receiver == null ? "{continent}" : receiver.getContinent()), false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                for (CityBuild value : parsed.values()) {
                    JavaCity city = new JavaCity(value);
                    for (Building building : Buildings.values()) {
                        int amt = city.getBuilding(building);
                        if (amt > 0 && !building.canBuild(receiver.getContinent())) {
                            return false;
                        }
                    }
                }
                return true;
            }
        }));

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
            createDate.put(entry.getKey(), city.getCreated());
        }

        long lastAttackDate = 0;
        if (allow_switch_after_offensive) {
            // get attacks where attacker
            lastAttackDate = getLatestAttackDate(receiver);
        }

        // get grants
        for (GrantTemplateManager.GrantSendRecord record : getDb().getGrantTemplateManager().getRecordsByReceiver(receiver.getId())) {
            // buildDate = Math.max(buildDate, record.date);
            switch (record.grant_type) {
                case BUILD -> buildDate = Math.max(buildDate, record.date);
                case INFRA -> infraDate = Math.max(infraDate, record.date);
                case PROJECT, LAND -> projectOrLandDate = Math.max(projectOrLandDate, record.date);
            }
        }
        Set<Integer> citiesToGrant = new IntOpenHashSet();
        long cutoff = onlyNewCities ? TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - 119) : 0;
        for (Map.Entry<Integer, Long> entry : createDate.entrySet()) {
            long date = entry.getValue();
            if (date < cutoff) continue;
            boolean allowGrant = true;
            if (buildDate > date) {
                allowGrant = infraDate > buildDate && allow_switch_after_infra;
                if (projectOrLandDate > buildDate && allow_switch_after_land_or_project) {
                    allowGrant = true;
                }
                if (lastAttackDate > date && allow_switch_after_offensive) {
                    allowGrant = true;
                }
                if (allow_all) {
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
    public double[] getCost(GuildDB db, DBNation sender, DBNation receiver, Map<Integer, CityBuild> builds) {
        double[] cost = ResourceType.getBuffer();
        Map<Integer, JavaCity> existing = receiver.getCityMap(true);
        for (Map.Entry<Integer, JavaCity> entry : existing.entrySet()) {
            JavaCity from = entry.getValue();
            CityBuild build = builds.get(entry.getKey());
            if (build == null) continue;
            JavaCity to = new JavaCity(build);
            to.calculateCost(from, cost, includeInfra, includeLand > 0);
        }
        return cost;
    }

    @Override
    public DepositType.DepositTypeInfo getDepositType(DBNation receiver, Map<Integer, CityBuild> build) {
        int cities = 0;
        if (onlyNewCities) {
            cities = build.size();
        } else {
            cities = receiver.getCities();
        }
        return DepositType.BUILD.withAmount(cities);
    }

    @Override
    public String getInstructions(DBNation sender, DBNation receiver, Map<Integer, CityBuild> parsed) {
        StringBuilder instructions = new StringBuilder();
        if (parsed.size() == 1) {
            int id = parsed.keySet().iterator().next();
            instructions.append("Go to <" + Settings.PNW_URL() + "/city/improvements/import/id=" + id + "> and import the build:\n");
        } else if (parsed.size() == receiver.getCities()) {
            instructions.append("Go to <" + Settings.PNW_URL() + "/city/improvements/bulk-import/> and import the build:\n");
        } else {
            Set<Integer> ids = parsed.keySet();
            instructions.append("Go to <" + Settings.PNW_URL() + "/city/improvements/import/id=> with the following city ids:\n");
            for (int id : ids) {
                instructions.append("- ").append(id).append("\n");
            }
            instructions.append("and import the build:\n");
        }
        instructions.append("```json\n");
        if (!parsed.isEmpty()) {
            CityBuild cityBuild = parsed.values().iterator().next();
            instructions.append(cityBuild.toString());
        }
        instructions.append("\n```");
        return instructions.toString();
    }

    @Override
    public Class<Map<Integer, CityBuild>> getParsedType() {
        return (Class<Map<Integer, CityBuild>>) TypeToken.getParameterized(Map.class, Integer.class, CityBuild.class).getRawType();
    }
}
