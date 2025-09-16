package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.offshore.Grant;

import javax.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ResearchTemplate extends AGrantTemplate<Void>{
    private final Map<Research, Integer> research;
    private final boolean from_zero;

    public ResearchTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        this(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal,
                rs.getLong("date_created"),
                rs.getInt("research"),
                rs.getBoolean("from_zero"),
                rs.getLong("expire"),
                rs.getLong("decay"),
                rs.getBoolean("allow_ignore"));
    }

    // create new constructor  with typed parameters instead of resultset
    public ResearchTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole,
                            long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay,
                            int maxGranterDay, int maxGranterTotal, long dateCreated,
                            int researchBits, boolean fromZero, long expiryOrZero, long decayOrZero, boolean allowIgnore) {
        super(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, dateCreated, expiryOrZero, decayOrZero, allowIgnore, -1);
        this.research = Research.fromBits(researchBits);
        this.from_zero = fromZero;
    }

    @Override
    public String toInfoString(DBNation sender, DBNation receiver,  Void parsed) {
        StringBuilder message = new StringBuilder();
        message.append("Research: `" + StringMan.getString(research) + "`");
        if (from_zero) {
            message.append(" (from zero)");
        }
        return message.toString();
    }

    @Override
    public String getCommandString(String name, String allowedRecipients, String econRole, String selfRole, String bracket, String useReceiverBracket, String maxTotal, String maxDay, String maxGranterDay, String maxGranterTotal, String allowExpire, String allowDecay, String allowIgnore, String repeatable) {
        return CM.grant_template.create.research.cmd.name(name).allowedRecipients(allowedRecipients)
                .research(StringMan.getString(research)).econRole(
                econRole).selfRole(
                selfRole).bracket(
                bracket).useReceiverBracket(
                useReceiverBracket).maxTotal(
                maxTotal).maxDay(
                maxDay).from_zero(from_zero ? "true" : null).maxGranterDay(
                maxGranterDay).maxGranterTotal(
                maxGranterTotal).expireTime(
                allowExpire).decayTime(
                allowDecay).allowIgnore(
                allowIgnore).toString();
    }


    @Override
    public String toListString() {
        return super.toListString() + " | " + StringMan.getString(research) + (from_zero ? " (from zero)" : "");
    }

    @Override
    public TemplateTypes getType() {
        return TemplateTypes.RESEARCH;
    }

    public Map<Research, Integer> getResearch() {
        return research;
    }

    public boolean isFromZero() {
        return from_zero;
    }

    @Override
    public List<Grant.Requirement> getDefaultRequirements(GuildDB db, @Nullable DBNation sender, @Nullable DBNation receiver, Void parsed, boolean confirm) {
        List<Grant.Requirement> list = super.getDefaultRequirements(db, sender, receiver, parsed, confirm);
        list.addAll(getRequirements(db, sender, receiver, this, parsed));
        return list;
    }

    public static List<Grant.Requirement> getRequirements(GuildDB db, DBNation sender, DBNation receiver, ResearchTemplate template, Void parsed) {
        return getRequirementsResearch(db, sender, receiver, template, template != null ? template.research : null);
    }

    public static List<Grant.Requirement> getRequirementsResearch(GuildDB db, DBNation sender, DBNation receiver, ResearchTemplate template, Map<Research, Integer> research) {
        List<Grant.Requirement> list = new ArrayList<>();

        // has a timer
        list.add(new Grant.Requirement("Must NOT have a project timer", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return nation.getProjectTurns() <= 0;
            }
        }));
        // received project already
        list.add(new Grant.Requirement("Must not have received a transfer for " + (research == null ? "`{research}`" : research) + " already", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                outer:
                for (Transaction2 transaction : nation.getTransactions(-1, true, 0L, Long.MAX_VALUE)) {
                    if (transaction.note == null) continue;
                    Map<DepositType, Object> noteMap = transaction.getNoteMap();
                    Object researchVal = noteMap.get(DepositType.RESEARCH);
                    if (researchVal instanceof  Number n) {
                        int researchBits = n.intValue();
                        Map<Research, Integer> researchMap = Research.fromBits(researchBits);
                        for (Map.Entry<Research, Integer> entry : research.entrySet()) {
                            if (researchMap.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                                continue outer;
                            }
                        }
                    }
                }
                return true;
            }
        }));

        //nation does not have ALA
        list.add(new Grant.Requirement("Requires the project: `" + Projects.MILITARY_RESEARCH_CENTER + "`", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                return receiver.hasProject(Projects.MILITARY_RESEARCH_CENTER);
            }
        }));

        //nation does not have AEC
        list.add(new Grant.Requirement("Requires the project: `" + Projects.MILITARY_DOCTRINE + "`", true, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                return receiver.hasProject(Projects.MILITARY_DOCTRINE);
            }
        }));

        // domestic policy is technological advancement
        list.add(new Grant.Requirement("Domestic policy must be `" + DomesticPolicy.TECHNOLOGICAL_ADVANCEMENT.name() + "`", true, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return nation.getDomesticPolicy() == DomesticPolicy.TECHNOLOGICAL_ADVANCEMENT;
            }
        }));

        return list;
    }

    @Override
    public List<String> getQueryFields() {
        List<String> list = getQueryFieldsBase();
        list.add("research");
        list.add("from_zero");
        return list;
    }

    @Override
    public void setValues(PreparedStatement stmt) throws SQLException {
        stmt.setInt(17, Research.toBits(research));
        stmt.setBoolean(18, from_zero);
    }

    @Override
    public double[] getCost(GuildDB db, DBNation sender, DBNation receiver, Void parsed) {
        Map<Research, Integer> start = new HashMap<>();
        for (Map.Entry<Research, Integer> entry : receiver.getResearchLevels().entrySet()) {
            int level = from_zero ? Math.min(entry.getValue(), research.get(entry.getKey())) : entry.getValue();
            if (level > 0) {
                start.put(entry.getKey(), level);
            }
        }
        String findNote = "#research=";
        for (Transaction2 transaction : receiver.getTransactions(-1, true, 0L, Long.MAX_VALUE)) {
            if (transaction.note == null) continue;
            String noteLower = transaction.note.toLowerCase();
            if (noteLower.contains(findNote)) {
                Object researchObj = transaction.getNoteMap().get(DepositType.RESEARCH);
                if (researchObj instanceof Number n) {
                    int researchBits = n.intValue();
                    Map<Research, Integer> priorGrants = Research.fromBits(researchBits);
                    for (Map.Entry<Research, Integer> entry : priorGrants.entrySet()) {
                        int level = Math.min(entry.getValue(), research.get(entry.getKey()));
                        if (level > 0) {
                            start.put(entry.getKey(), Math.max(start.getOrDefault(entry.getKey(), 0), level));
                        }
                    }
                }
            }
        }
        return ResourceType.resourcesToArray(Research.cost(start, research, receiver.getResearchCostFactor()));
    }

    @Override
    public DepositTypeInfo getDepositType(DBNation receiver, Void parsed) {
        return DepositType.RESEARCH.withAmount(Research.toBits(research));
    }

    @Override
    public String getInstructions(DBNation sender, DBNation receiver, Void parsed) {
        return "Go to: " + Settings.PNW_URL() + "/nation/military/research/\nAnd buy UP TO the research: " + research;
    }

    @Override
    public Void parse(DBNation receiver, String value) {
        if (value != null && !value.isEmpty()) {
            throw new UnsupportedOperationException("Research grants do not support any additional arguments");
        }
        return null;
    }

    @Override
    public Class<Void> getParsedType() {
        return Void.class;
    }
}
