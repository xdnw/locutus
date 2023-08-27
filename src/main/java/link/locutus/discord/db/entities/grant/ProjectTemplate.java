package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.Grant;

import javax.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class ProjectTemplate extends AGrantTemplate<Void>{
    private final Project project;
    public ProjectTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        this(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, rs.getLong("date_created"), Projects.values[rs.getInt("project")],
                rs.getLong("expire"),
                rs.getBoolean("allow_ignore"));
    }

    // create new constructor  with typed parameters instead of resultset
    public ProjectTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long dateCreated, Project project, long expiryOrZero, boolean allowIgnore) {
        super(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, dateCreated, expiryOrZero, allowIgnore);
        this.project = project;
    }

    @Override
    public String toInfoString(DBNation sender, DBNation receiver,  Void parsed) {
        StringBuilder message = new StringBuilder();
        message.append("Project: " + project);
        return message.toString();
    }

    @Override
    public String getCommandString(String name, String allowedRecipients, String econRole, String selfRole, String bracket, String useReceiverBracket, String maxTotal, String maxDay, String maxGranterDay, String maxGranterTotal, String allowExpire, String allowIgnore) {
        return CM.grant_template.create.project.cmd.create(name, allowedRecipients, project.name(), econRole, selfRole, bracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, allowExpire, allowIgnore, null).toString();

    }


    @Override
    public String toListString() {
        return super.toListString() + " | " + project.name();
    }

    @Override
    public TemplateTypes getType() {
        return TemplateTypes.PROJECT;
    }

    public Project getProject() {
        return project;
    }

    @Override
    public List<Grant.Requirement> getDefaultRequirements(@Nullable DBNation sender, @Nullable DBNation receiver, Void parsed) {
        List<Grant.Requirement> list = super.getDefaultRequirements(sender, receiver, parsed);

        // has a timer
        list.add(new Grant.Requirement("Has a project timer", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return nation.getProjectTurns() <= 0;
            }
        }));

        // received project already
        list.add(new Grant.Requirement("Received transfer for " + project + " already", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                String findNote = "#project=" + project.name().toLowerCase();
                String findNotr2 = "#project=" + project.ordinal();
                for (Transaction2 transaction : nation.getTransactions(true)) {
                    if (transaction.note == null) continue;
                    String noteLower = transaction.note.toLowerCase();
                    if (noteLower.contains(findNote) || noteLower.contains(findNotr2)) return false;
                }
                return true;
            }
        }));

        // already got project grant in past 10 days
        list.add(new Grant.Requirement("Has not received a project grant in the past 10 days", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                List<GrantTemplateManager.GrantSendRecord> received = getDb().getGrantTemplateManager().getRecordsByReceiver(nation.getId());
                long cutoff = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - 119);
                received.removeIf(f -> f.date <= cutoff || f.grant_type != TemplateTypes.PROJECT);
                return received.size() == 0;
            }
        }));

        // already have project
        list.add(new Grant.Requirement("Already has the project " + project.name(), false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return !nation.hasProject(project);
            }
        }));
        // required projects
        list.add(new Grant.Requirement("Requires the projects: " + StringMan.getString(project.requiredProjects()), false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                for (Project req : project.requiredProjects()) {
                    if (!nation.hasProject(req)) {
                        return false;
                    }
                }
                return true;
            }
        }));

        // max city
        if (project.maxCities() != 0) {
            list.add(new Grant.Requirement("Project requires at most " + project.maxCities() + " cities", false, new Function<DBNation, Boolean>() {
                @Override
                public Boolean apply(DBNation nation) {
                    return project.maxCities() <= 0 || nation.getCities() <= project.maxCities();
                }
            }));
        }

        // min city
        if (project.maxCities() != 0) {
            list.add(new Grant.Requirement("Project requires at least " + project.maxCities() + " cities", false, new Function<DBNation, Boolean>() {
                @Override
                public Boolean apply(DBNation nation) {
                    return project.requiredCities() <= 0 || nation.getCities() >= project.requiredCities();
                }
            }));
        }

        // domestic policy is technological advancement
        list.add(new Grant.Requirement("Domestic policy must be technological advancement", true, new Function<DBNation, Boolean>() {
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
        list.add("project");
        return list;
    }

    @Override
    public void setValues(PreparedStatement stmt) throws SQLException {
        stmt.setInt(15, project.ordinal());
    }

    @Override
    public double[] getCost(DBNation sender, DBNation receiver, Void parsed) {
        return receiver.projectCost(project);
    }

    @Override
    public DepositType.DepositTypeInfo getDepositType(DBNation receiver, Void parsed) {
        return DepositType.PROJECT.withAmount(project.ordinal());
    }

    @Override
    public String getInstructions(DBNation sender, DBNation receiver, Void parsed) {
        return "Go to: https://politicsandwar.com/nation/projects/\nAnd buy the project: " + project.name();
    }

    @Override
    public Void parse(DBNation receiver, String value) {
        if (value != null && !value.isEmpty()) {
            throw new UnsupportedOperationException("Project grants do not support any additional arguments");
        }
        return null;
    }

    @Override
    public Class<Void> getParsedType() {
        return Void.class;
    }
}
