package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.MMRInt;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.grant.AGrantTemplate;
import link.locutus.discord.db.entities.grant.BuildTemplate;
import link.locutus.discord.db.entities.grant.CityTemplate;
import link.locutus.discord.db.entities.grant.GrantTemplateManager;
import link.locutus.discord.db.entities.grant.InfraTemplate;
import link.locutus.discord.db.entities.grant.LandTemplate;
import link.locutus.discord.db.entities.grant.ProjectTemplate;
import link.locutus.discord.db.entities.grant.RawsTemplate;
import link.locutus.discord.db.entities.grant.TemplateTypes;
import link.locutus.discord.db.entities.grant.WarchestTemplate;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.offshore.Grant;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

public class GrantCommands {

    @Command(desc = "List all grant templates for the specified category")
    @RolePermission(Roles.MEMBER)
    public void templateList(@Me GuildDB db, @Me Guild guild, @Me User author, @Me Member member, @Me DBNation me, @Me IMessageIO io, @Default TemplateTypes category, @Switch("d") boolean listDisabled) {
        GrantTemplateManager manager = db.getGrantTemplateManager();
        Set<AGrantTemplate> templates = category == null ? manager.getTemplates() : manager.getTemplates(category);
        if (!listDisabled) {
            templates.removeIf(f -> !f.isEnabled());
        }

        if (templates.isEmpty()) {
            String msg = "No templates found for category: " + category + "\n" +
                    "Create one with TODO CM ref here";
            if (!listDisabled) {
                msg += "\nUse `-d listDisabled` to list disabled templates";
            }
            io.send(msg);
            return;
        }

        boolean hasAdmin = Roles.ADMIN.has(author, guild);

        List<AGrantTemplate> grantOthers = new ArrayList<>();
        List<AGrantTemplate> grantSelf = new ArrayList<>();
        List<AGrantTemplate> noAccess = new ArrayList<>();

        for (AGrantTemplate template : templates) {
            if (template.isEnabled() && (hasAdmin || template.hasRole(member))) {
                grantOthers.add(template);
            } else if (template.isEnabled() && template.hasSelfRole(member)) {
                grantSelf.add(template);
            } else {
                noAccess.add(template);
            }
        }

        StringBuilder result = new StringBuilder();
        if (!grantOthers.isEmpty()) {
            result.append("### Grant Others:\n");
            for (AGrantTemplate template : grantOthers) {
                result.append("- ").append(template.getName()).append(" - ").append(template.toListString()).append("\n");
            }
        }
        if (!grantSelf.isEmpty()) {
            result.append("### Grant Self:\n");
            for (AGrantTemplate template : grantSelf) {
                result.append("- ").append(template.getName()).append(" - ").append(template.toListString()).append("\n");
            }
        }
        if (!noAccess.isEmpty()) {
            result.append("### No Access:\n");
            for (AGrantTemplate template : noAccess) {
                result.append("- ").append(template.getName()).append(" - ").append(template.toListString()).append("\n");
            }
        }

        io.send(result.toString());
    }

    @Command(desc = "Full information about a grant template")
    @RolePermission(Roles.MEMBER)
    public void templateInfo(@Me GuildDB db, @Me Guild guild, @Me User author, @Me Member member, @Me DBNation me, @Me IMessageIO io, AGrantTemplate template, @Default DBNation receiver) {
        io.create().embed(template.getName(), template.toFullString(me, receiver)).send();
    }

    // grant_template Delete
    @Command(desc = "Delete a grant template")
    @RolePermission(Roles.ECON)
    public String templateDelete(@Me GuildDB db, @Me DBNation me, @Me IMessageIO io, @Me JSONObject command, AGrantTemplate template, @Switch("f") boolean force) {
        if (!force) {
            String body = template.toFullString(me, null);
            io.create().confirmation("Delete template: " + template.getName(), body.toString(), command).send();
            return null;
        }
        db.getGrantTemplateManager().deleteTemplate(template);
        return "The template: `" + template.getName() + "` has been deleted.";
    }

    // grant_template disable
    @Command(desc = "Set an active grant template as disabled")
    @RolePermission(Roles.ECON)
    public String templateDisable(@Me GuildDB db, @Me DBNation me, @Me IMessageIO io, @Me JSONObject command, AGrantTemplate template, @Switch("f") boolean force) {
        if (!template.isEnabled()) {
            return "The template: `" + template.getName() + "` is already disabled.";
        }
        template.setEnabled(false);
        db.getGrantTemplateManager().saveTemplate(template);
        return "The template: `" + template.getName() + "` has been disabled.";
    }

    @Command(desc = "Set a disabled grant template as enabled")
    @RolePermission(Roles.ECON)
    public String templateEnabled(@Me GuildDB db, @Me DBNation me, @Me IMessageIO io, AGrantTemplate template) {
        if (template.isEnabled()) {
            return "The template: `" + template.getName() + "` is already enabled.";
        }
        template.setEnabled(true);
        db.getGrantTemplateManager().saveTemplate(template);
        return "The template: `" + template.getName() + "` has been enabled.";
    }

    // grant_template create project
    // public ProjectTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, Project project) {
    @Command(desc = "Create a new project grant template")
    @RolePermission(Roles.ECON)
    public String templateCreateProject(@Me GuildDB db, @Me DBNation me, @Me IMessageIO io, @Me JSONObject command,
                                        String name,
                                        NationFilter allowedRecipients,
                                        Project project,
                                        @Switch("e") Role econRole,
                                        @Switch("s") Role selfRole,
                                        @Switch("b")TaxBracket bracket,
                                        @Switch("r") boolean useReceiverBracket,
                                        @Switch("mt") Integer maxTotal,
                                        @Switch("md") Integer maxDay,
                                        @Switch("mgd") Integer maxGranterDay,
                                        @Switch("mgt") Integer maxGranterTotal,
                                        @Switch("f") boolean force) {
        name = name.toUpperCase(Locale.ROOT).trim();
        // Ensure name is alphanumericalund
        if (!name.matches("[A-Z0-9_-]+")) {
            throw new IllegalArgumentException("The name must be alphanumericalunderscore, not `" + name + "`");
        }
        GrantTemplateManager manager = db.getGrantTemplateManager();
        // check a template does not exist by that name
        String finalName = name;
        if (manager.getTemplateMatching(f -> f.getName().equalsIgnoreCase(finalName)) != null) {
            throw new IllegalArgumentException("A template with that name already exists. See: TODO CM ref here");
        }
        if (econRole == null) econRole = Roles.ECON_STAFF.toRole(db);
        if (econRole == null) econRole = Roles.ECON.toRole(db);
        if (econRole == null) {
            throw new IllegalArgumentException("No `econRole` found. Please provide one, or set a default ECON_STAFF via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (selfRole == null) selfRole = Roles.ECON.toRole(db);
        if (selfRole == null) {
            throw new IllegalArgumentException("No `selfRole` found. Please provide one, or set a default ECON via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (bracket != null && useReceiverBracket) {
            throw new IllegalArgumentException("Cannot use both `bracket` and `useReceiverBracket`");
        }
        ProjectTemplate template = new ProjectTemplate(db, false, name, allowedRecipients, econRole.getIdLong(), selfRole.getIdLong(), bracket == null ? 0 : bracket.getId(), useReceiverBracket, maxTotal == null ? 0 : maxTotal, maxDay == null ? 0 : maxDay, maxGranterDay == null ? 0 : maxGranterDay, maxGranterTotal == null ? 0 : maxGranterTotal, project);

        // confirmation
        if (!force) {
            io.create().confirmation("Create template: " + template.getName(), template.toFullString(me, null), command).send();
            return null;
        }
        manager.saveTemplate(template);
        return "The template: `" + template.getName() + "` has been created. See: TODO CM ref here";
    }

    // grant_template create build
    // public BuildTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, byte[] build, boolean only_new_cities, long mmr, long track_days, boolean allow_switch_after_offensive) {
    @Command(desc = "Create a new build grant template")
    @RolePermission(Roles.ECON)
    public String templateCreateBuild(@Me GuildDB db, @Me DBNation me, @Me IMessageIO io, @Me JSONObject command,
                                      String name,
                                      NationFilter allowedRecipients,
                                      @Switch("c") CityBuild build,
                                      @Switch("m") MMRInt mmr,
                                      @Switch("o") boolean only_new_cities,
                                      @Switch("t") Integer trackDays,
                                      @Switch("a") boolean allowSwitchAfterOffensive,
                                      @Switch("e") Role econRole,
                                      @Switch("s") Role selfRole,
                                      @Switch("b")TaxBracket bracket,
                                      @Switch("r") boolean useReceiverBracket,
                                      @Switch("mt") Integer maxTotal,
                                      @Switch("md") Integer maxDay,
                                      @Switch("mgd") Integer maxGranterDay,
                                      @Switch("mgt") Integer maxGranterTotal,
                                      @Switch("f") boolean force) {
        name = name.toUpperCase(Locale.ROOT).trim();
        // Ensure name is alphanumericalund
        if (!name.matches("[A-Z0-9_-]+")) {
            throw new IllegalArgumentException("The name must be alphanumericalunderscore, not `" + name + "`");
        }
        GrantTemplateManager manager = db.getGrantTemplateManager();
        // check a template does not exist by that name
        String finalName = name;
        if (manager.getTemplateMatching(f -> f.getName().equalsIgnoreCase(finalName)) != null) {
            throw new IllegalArgumentException("A template with that name already exists. See: TODO CM ref here");
        }
        if (econRole == null) econRole = Roles.ECON_STAFF.toRole(db);
        if (econRole == null) econRole = Roles.ECON.toRole(db);
        if (econRole == null) {
            throw new IllegalArgumentException("No `econRole` found. Please provide one, or set a default ECON_STAFF via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (selfRole == null) selfRole = Roles.ECON.toRole(db);
        if (selfRole == null) {
            throw new IllegalArgumentException("No `selfRole` found. Please provide one, or set a default ECON via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (bracket != null && useReceiverBracket) {
            throw new IllegalArgumentException("Cannot use both `bracket` and `useReceiverBracket`");
        }
        byte[] buildBytes = build == null ? null : new JavaCity(build).toBytes();

        BuildTemplate template = new BuildTemplate(db, false, name, allowedRecipients, econRole.getIdLong(), selfRole.getIdLong(), bracket == null ? 0 : bracket.getId(), useReceiverBracket, maxTotal == null ? 0 : maxTotal, maxDay == null ? 0 : maxDay, maxGranterDay == null ? 0 : maxGranterDay, maxGranterTotal == null ? 0 : maxGranterTotal, buildBytes, only_new_cities, mmr.toNumber(), trackDays, allowSwitchAfterOffensive);

        // confirmation
        if (!force) {
            io.create().confirmation("Create template: " + template.getName(), template.toFullString(me, null), command).send();
            return null;
        }
        manager.saveTemplate(template);
        return "The template: `" + template.getName() + "` has been created. See: TODO CM ref here";
    }

    // grant_template create city
    // public CityTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, int min_city, int max_city) {
    @Command(desc = "Create a new city grant template")
    @RolePermission(Roles.ECON)
    public String templateCreateCity(@Me GuildDB db, @Me DBNation me, @Me IMessageIO io, @Me JSONObject command,
                                     String name,
                                     NationFilter allowedRecipients,
                                     @Switch("c") Integer minCity,
                                     @Switch("m") Integer maxCity,
                                     @Switch("e") Role econRole,
                                     @Switch("s") Role selfRole,
                                     @Switch("b")TaxBracket bracket,
                                     @Switch("r") boolean useReceiverBracket,
                                     @Switch("mt") Integer maxTotal,
                                     @Switch("md") Integer maxDay,
                                     @Switch("mgd") Integer maxGranterDay,
                                     @Switch("mgt") Integer maxGranterTotal,
                                     @Switch("f") boolean force) {
        name = name.toUpperCase(Locale.ROOT).trim();
        // Ensure name is alphanumericalund
        if (!name.matches("[A-Z0-9_-]+")) {
            throw new IllegalArgumentException("The name must be alphanumericalunderscore, not `" + name + "`");
        }
        GrantTemplateManager manager = db.getGrantTemplateManager();
        // check a template does not exist by that name
        String finalName = name;
        if (manager.getTemplateMatching(f -> f.getName().equalsIgnoreCase(finalName)) != null) {
            throw new IllegalArgumentException("A template with that name already exists. See: TODO CM ref here");
        }
        if (econRole == null) econRole = Roles.ECON_STAFF.toRole(db);
        if (econRole == null) econRole = Roles.ECON.toRole(db);
        if (econRole == null) {
            throw new IllegalArgumentException("No `econRole` found. Please provide one, or set a default ECON_STAFF via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (selfRole == null) selfRole = Roles.ECON.toRole(db);
        if (selfRole == null) {
            throw new IllegalArgumentException("No `selfRole` found. Please provide one, or set a default ECON via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (bracket != null && useReceiverBracket) {
            throw new IllegalArgumentException("Cannot use both `bracket` and `useReceiverBracket`");
        }

        CityTemplate template = new CityTemplate(db, false, name, allowedRecipients, econRole.getIdLong(), selfRole.getIdLong(), bracket == null ? 0 : bracket.getId(), useReceiverBracket, maxTotal == null ? 0 : maxTotal, maxDay == null ? 0 : maxDay, maxGranterDay == null ? 0 : maxGranterDay, maxGranterTotal == null ? 0 : maxGranterTotal, minCity == null ? 0 : minCity, maxCity == null ? 0 : maxCity);

        // confirmation
        if (!force) {
            io.create().confirmation("Create template: " + template.getName(), template.toFullString(me, null), command).send();
            return null;
        }
        manager.saveTemplate(template);
        return "The template: `" + template.getName() + "` has been created. See: TODO CM ref here";
    }

    // grant_template create infra
    // public InfraTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long level, boolean onlyNewCities, boolean track_days, long require_n_offensives, boolean allow_rebuild) {
    @Command(desc = "Create a new infra grant template")
    @RolePermission(Roles.ECON)
    public String templateCreateInfra(@Me GuildDB db, @Me DBNation me, @Me IMessageIO io, @Me JSONObject command,
                                      String name,
                                      NationFilter allowedRecipients,
                                      @Switch("l") Integer level,
                                      @Switch("n") boolean onlyNewCities,
                                      @Switch("t") boolean trackDays,
                                      @Switch("o") Integer requireNOffensives,
                                      @Switch("a") boolean allowRebuild,

                                      @Switch("e") Role econRole,
                                      @Switch("s") Role selfRole,
                                      @Switch("b")TaxBracket bracket,
                                      @Switch("r") boolean useReceiverBracket,
                                      @Switch("mt") Integer maxTotal,
                                      @Switch("md") Integer maxDay,
                                      @Switch("mgd") Integer maxGranterDay,
                                      @Switch("mgt") Integer maxGranterTotal,

                                      @Switch("f") boolean force) {
        name = name.toUpperCase(Locale.ROOT).trim();
        // Ensure name is alphanumericalund
        if (!name.matches("[A-Z0-9_-]+")) {
            throw new IllegalArgumentException("The name must be alphanumericalunderscore, not `" + name + "`");
        }
        GrantTemplateManager manager = db.getGrantTemplateManager();
        // check a template does not exist by that name
        String finalName = name;
        if (manager.getTemplateMatching(f -> f.getName().equalsIgnoreCase(finalName)) != null) {
            throw new IllegalArgumentException("A template with that name already exists. See: TODO CM ref here");
        }
        if (econRole == null) econRole = Roles.ECON_STAFF.toRole(db);
        if (econRole == null) econRole = Roles.ECON.toRole(db);
        if (econRole == null) {
            throw new IllegalArgumentException("No `econRole` found. Please provide one, or set a default ECON_STAFF via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (selfRole == null) selfRole = Roles.ECON.toRole(db);
        if (selfRole == null) {
            throw new IllegalArgumentException("No `selfRole` found. Please provide one, or set a default ECON via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (bracket != null && useReceiverBracket) {
            throw new IllegalArgumentException("Cannot use both `bracket` and `useReceiverBracket`");
        }

        InfraTemplate template = new InfraTemplate(db, false, name, allowedRecipients, econRole.getIdLong(), selfRole.getIdLong(), bracket == null ? 0 : bracket.getId(), useReceiverBracket, maxTotal == null ? 0 : maxTotal, maxDay == null ? 0 : maxDay, maxGranterDay == null ? 0 : maxGranterDay, maxGranterTotal == null ? 0 : maxGranterTotal, level == null ? 0 : level, onlyNewCities, trackDays, requireNOffensives == null ? 0 : requireNOffensives, allowRebuild);

        // confirmation
        if (!force) {
            io.create().confirmation("Create template: " + template.getName(), template.toFullString(me, null), command).send();
            return null;
        }
        manager.saveTemplate(template);
        return "The template: `" + template.getName() + "` has been created. See: TODO CM ref here";
    }

    // grant_template create land
    // public LandTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long level, boolean onlyNewCities) {
    @Command(desc = "Create a new land grant template")
    @RolePermission(Roles.ECON)
    public String templateCreateLand(@Me GuildDB db, @Me DBNation me, @Me IMessageIO io, @Me JSONObject command,
                                     String name,
                                     NationFilter allowedRecipients,
                                     @Switch("l") Integer level,
                                     @Switch("n") boolean onlyNewCities,

                                     @Switch("e") Role econRole,
                                     @Switch("s") Role selfRole,
                                     @Switch("b")TaxBracket bracket,
                                     @Switch("r") boolean useReceiverBracket,
                                     @Switch("mt") Integer maxTotal,
                                     @Switch("md") Integer maxDay,
                                     @Switch("mgd") Integer maxGranterDay,
                                     @Switch("mgt") Integer maxGranterTotal,

                                     @Switch("f") boolean force) {
        name = name.toUpperCase(Locale.ROOT).trim();
        // Ensure name is alphanumericalund
        if (!name.matches("[A-Z0-9_-]+")) {
            throw new IllegalArgumentException("The name must be alphanumericalunderscore, not `" + name + "`");
        }
        GrantTemplateManager manager = db.getGrantTemplateManager();
        // check a template does not exist by that name
        String finalName = name;
        if (manager.getTemplateMatching(f -> f.getName().equalsIgnoreCase(finalName)) != null) {
            throw new IllegalArgumentException("A template with that name already exists. See: TODO CM ref here");
        }
        if (econRole == null) econRole = Roles.ECON_STAFF.toRole(db);
        if (econRole == null) econRole = Roles.ECON.toRole(db);
        if (econRole == null) {
            throw new IllegalArgumentException("No `econRole` found. Please provide one, or set a default ECON_STAFF via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (selfRole == null) selfRole = Roles.ECON.toRole(db);
        if (selfRole == null) {
            throw new IllegalArgumentException("No `selfRole` found. Please provide one, or set a default ECON via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (bracket != null && useReceiverBracket) {
            throw new IllegalArgumentException("Cannot use both `bracket` and `useReceiverBracket`");
        }

        LandTemplate template = new LandTemplate(db, false, name, allowedRecipients, econRole.getIdLong(), selfRole.getIdLong(), bracket == null ? 0 : bracket.getId(), useReceiverBracket, maxTotal == null ? 0 : maxTotal, maxDay == null ? 0 : maxDay, maxGranterDay == null ? 0 : maxGranterDay, maxGranterTotal == null ? 0 : maxGranterTotal, level == null ? 0 : level, onlyNewCities);

        // confirmation
        if (!force) {
            io.create().confirmation("Create template: " + template.getName(), template.toFullString(me, null), command).send();
            return null;
        }
        manager.saveTemplate(template);
        return "The template: `" + template.getName() + "` has been created. See: TODO CM ref here";
    }

    // grant_template create raws
    // public RawsTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long days, long overdrawPercentCents) {
    @Command(desc = "Create a new raws grant template")
    @RolePermission(Roles.ECON)
    public String templateCreateRaws(@Me GuildDB db, @Me DBNation me, @Me IMessageIO io, @Me JSONObject command,
                                     String name,
                                     NationFilter allowedRecipients,
                                     @Switch("d") long days,
                                     @Switch("o") long overdrawPercentCents,

                                     @Switch("e") Role econRole,
                                     @Switch("s") Role selfRole,
                                     @Switch("b")TaxBracket bracket,
                                     @Switch("r") boolean useReceiverBracket,
                                     @Switch("mt") Integer maxTotal,
                                     @Switch("md") Integer maxDay,
                                     @Switch("mgd") Integer maxGranterDay,
                                     @Switch("mgt") Integer maxGranterTotal,

                                     @Switch("f") boolean force) {
        name = name.toUpperCase(Locale.ROOT).trim();
        // Ensure name is alphanumericalund
        if (!name.matches("[A-Z0-9_-]+")) {
            throw new IllegalArgumentException("The name must be alphanumericalunderscore, not `" + name + "`");
        }
        GrantTemplateManager manager = db.getGrantTemplateManager();
        // check a template does not exist by that name
        String finalName = name;
        if (manager.getTemplateMatching(f -> f.getName().equalsIgnoreCase(finalName)) != null) {
            throw new IllegalArgumentException("A template with that name already exists. See: TODO CM ref here");
        }
        if (econRole == null) econRole = Roles.ECON_STAFF.toRole(db);
        if (econRole == null) econRole = Roles.ECON.toRole(db);
        if (econRole == null) {
            throw new IllegalArgumentException("No `econRole` found. Please provide one, or set a default ECON_STAFF via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (selfRole == null) selfRole = Roles.ECON.toRole(db);
        if (selfRole == null) {
            throw new IllegalArgumentException("No `selfRole` found. Please provide one, or set a default ECON via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (bracket != null && useReceiverBracket) {
            throw new IllegalArgumentException("Cannot use both `bracket` and `useReceiverBracket`");
        }

        RawsTemplate template = new RawsTemplate(db, false, name, allowedRecipients, econRole.getIdLong(), selfRole.getIdLong(), bracket == null ? 0 : bracket.getId(), useReceiverBracket, maxTotal == null ? 0 : maxTotal, maxDay == null ? 0 : maxDay, maxGranterDay == null ? 0 : maxGranterDay, maxGranterTotal == null ? 0 : maxGranterTotal, days, overdrawPercentCents);

        // confirmation
        if (!force) {
            io.create().confirmation("Create template: " + template.getName(), template.toFullString(me, null), command).send();
            return null;
        }
        manager.saveTemplate(template);
        return "The template: `" + template.getName() + "` has been created. See: TODO CM ref here";
    }

    // grant_template create warchest
    // public WarchestTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, double[] allowancePerCity, long trackDays, boolean subtractExpenditure, long overdrawPercentCents) {
    @Command(desc = "Create a new warchest grant template")
    @RolePermission(Roles.ECON)
    public String templateCreateWarchest(@Me GuildDB db, @Me DBNation me, @Me IMessageIO io, @Me JSONObject command,
                                         String name,
                                         NationFilter allowedRecipients,
                                         @Switch("a") double[] allowancePerCity,
                                         @Switch("t") long trackDays,
                                         @Switch("s") boolean subtractExpenditure,
                                         @Switch("o") long overdrawPercentCents,

                                         @Switch("e") Role econRole,
                                         @Switch("s") Role selfRole,
                                         @Switch("b")TaxBracket bracket,
                                         @Switch("r") boolean useReceiverBracket,
                                         @Switch("mt") Integer maxTotal,
                                         @Switch("md") Integer maxDay,
                                         @Switch("mgd") Integer maxGranterDay,
                                         @Switch("mgt") Integer maxGranterTotal,

                                         @Switch("f") boolean force) {
        name = name.toUpperCase(Locale.ROOT).trim();
        // Ensure name is alphanumericalund
        if (!name.matches("[A-Z0-9_-]+")) {
            throw new IllegalArgumentException("The name must be alphanumericalunderscore, not `" + name + "`");
        }
        GrantTemplateManager manager = db.getGrantTemplateManager();
        // check a template does not exist by that name
        String finalName = name;
        if (manager.getTemplateMatching(f -> f.getName().equalsIgnoreCase(finalName)) != null) {
            throw new IllegalArgumentException("A template with that name already exists. See: TODO CM ref here");
        }
        if (econRole == null) econRole = Roles.ECON_STAFF.toRole(db);
        if (econRole == null) econRole = Roles.ECON.toRole(db);
        if (econRole == null) {
            throw new IllegalArgumentException("No `econRole` found. Please provide one, or set a default ECON_STAFF via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (selfRole == null) selfRole = Roles.ECON.toRole(db);
        if (selfRole == null) {
            throw new IllegalArgumentException("No `selfRole` found. Please provide one, or set a default ECON via " + CM.role.setAlias.cmd.toSlashMention());
        }
        if (bracket != null && useReceiverBracket) {
            throw new IllegalArgumentException("Cannot use both `bracket` and `useReceiverBracket`");
        }

        WarchestTemplate template = new WarchestTemplate(db, false, name, allowedRecipients, econRole.getIdLong(), selfRole.getIdLong(), bracket == null ? 0 : bracket.getId(), useReceiverBracket, maxTotal == null ? 0 : maxTotal, maxDay == null ? 0 : maxDay, maxGranterDay == null ? 0 : maxGranterDay, maxGranterTotal == null ? 0 : maxGranterTotal, allowancePerCity, trackDays, subtractExpenditure, overdrawPercentCents);

        // confirmation
        if (!force) {
            io.create().confirmation("Create template: " + template.getName(), template.toFullString(me, null), command).send();
            return null;
        }
        manager.saveTemplate(template);
        return "The template: `" + template.getName() + "` has been created. See: TODO CM ref here";
    }


    // grant_template send <template> <receiver> <partial> <expire>
    @Command
    @RolePermission(Roles.ECON)
    public String templateSend(@Me GuildDB db, @Me Member selfMember, @Me DBNation me, @Me IMessageIO io, @Me JSONObject command,
                               AGrantTemplate template,
                               DBNation receiver,
                               @Switch("p") String customValue,
                               @Switch("e") @Timediff Long expire,
                               @Switch("f") boolean force) {
        Role econRole = template.getEconRole();
        if (econRole == null) {
            throw new IllegalArgumentException("The template `" + template.getName() + "` does not have an `econRole` set. Please set one via " + template.getType().getCommandMention());
        }
        Role selfRole = template.getSelfRole();

        boolean hasEconRole = selfMember.getRoles().contains(econRole);
        if (!hasEconRole) {
            if (receiver.getNation_id() != me.getNation_id() || selfRole == null) {
                throw new IllegalArgumentException("You must have the role `" + econRole.getName() + "` to send grants to other nations");
            }
            // check has self role
            if (!selfMember.getRoles().contains(selfRole)) {
                throw new IllegalArgumentException("You must have the role `" + selfRole.getName() + "` to send grants to yourself");
            }
        }

        Grant grant = template.createGrant(receiver, customValue);
        // Get the note
        String note = grant.getNote();
        // Get the amount
        double[] cost = grant.cost();
        // Get the instructions
        String instructions = grant.getInstructions();

        List<Grant.Requirement> requirements = template.getDefaultRequirements(me, receiver);
        // TODO add these to default requirements
        // check grant not disabled
        // check grant limits (limit total/day, limit granter total/day)
        // check nation not received grant already

        // validate requirements

        // TODO figure out how to handle partial
        // example:
        //

        // confirmation
        if (!force) {
            io.create().confirmation("Send grant: " + grant.getName(), grant.toFullString(me, null), command).send();
            return null;
        }

        // todo send (Ensure grant sending is atomic)
        // Adds the template send record to the database
        // Transfers the resources
        // Prints the grant instructions
        // Pings the receiver

        // return message
    }

    // register all the require commands
    // grant_template require __key__ AGrantTemplate [args]

    // template.addRequirement(requirement)
    // requirements are nation filters

    // grant_template unrequire <filter>


    // ----------------- old stuff below, ignore it ----------- //

    /*
    // 1111111111111111111111111111111111111111111111111111111111111222222222222222222222222222222222222222
            // #grant=533640709337514025 #expire=1680066316000 #land=2000 #city=12345678 #banker=1234567 #cash

        CITY

        LAND

        INFRA

        RESOURCES

        PROJECT

        BUILD

        WARCHEST
            - cities
            - units
            - mmr
            - rebuy



     */
//    public String city(@Default DBCity) {
//
//    }

    /*
    Econ staff can send grants

    Grantable tax rates
        unit grants use warchest as note
        warchest grants are to fill up since last warchest grant + war costs
            // if no wars declared and performed attacks in since last warchest, then those wars do not count

        // econ staff can override safe checks
        // members on 70/70 can grant themselves if they are >70% taxes and MEMBERS_CAN_GRANT is set to true
            // Does not apply to warchest grants

        Restrictions:
        Projects:
     */
//
//    public String send(GuildDB db, User author, DBNation me, Map<DBNation, Grant> grants, Map<DBNation, List<String>> errors, DepositType type, boolean onlyMissingFunds, Long expire, boolean countAsCash) {
//        // no funds need to be sent
//        boolean econGov = Roles.ECON.has(author, db.getGuild());
//        boolean econStaff = Roles.ECON_STAFF.has(author, db.getGuild());
//
//        if (!econGov) {
//            if (!econStaff) {
//                if (expire != null && expire < TimeUnit.DAYS.toMillis(120)) throw new IllegalArgumentException("Minimum expire date is 120d");
//            }
//            if (expire != null && expire < TimeUnit.DAYS.toMillis(120)) throw new IllegalArgumentException("Minimum expire date is 60d");
//        }
//
//        if (countAsCash) {
//            if (db.getOrNull(GuildKey.RESOURCE_CONVERSION) == Boolean.TRUE || econStaff) {
//                grants.entrySet().forEach(f -> f.getValue().addNote("#cash"));
//            } else {
//                throw new IllegalArgumentException("RESOURCE_CONVERSION is disabled. Only a staff member can use `#cash`");
//            }
//        }
//        // add basic requirements
//        grants.entrySet().forEach(new Consumer<Map.Entry<DBNation, Grant>>() {
//            @Override
//            public void accept(Map.Entry<DBNation, Grant> entry) {
//                Grant grant = entry.getValue();
//                DBNation nation = entry.getKey();
//                User user = nation.getUser();
//                if (user == null) {
//                    errors.computeIfAbsent(nation, f -> new ArrayList<>()).add("Nation is not verified: " + CM.register.cmd.toSlashMention() + "");
//                    entry.setValue(null);
//                    return;
//                }
//                Member member = db.getGuild().getMember(user);
//                if (member == null) {
//                    entry.setValue(null);
//                    errors.computeIfAbsent(nation, f -> new ArrayList<>()).add("Nation was not found in guild");
//                }
//
//                DBAlliance alliance = db.getAlliance();
//                grant.addRequirement(new Grant.Requirement("This guild is not part of an alliance", econGov, f -> alliance != null));
//                grant.addRequirement(new Grant.Requirement("Nation is not a member of an alliance", econGov, f -> f.getPosition() > 1));
//                grant.addRequirement(new Grant.Requirement("Nation is in VM", econGov, f -> f.getVm_turns() == 0));
//                grant.addRequirement(new Grant.Requirement("Nation is not in the alliance: " + alliance, econGov, f -> alliance != null && f.getAlliance_id() == alliance.getAlliance_id()));
//
//                Role temp = Roles.TEMP.toRole(db.getGuild());
//                grant.addRequirement(new Grant.Requirement("Nation not eligible for grants (has role: " + temp.getName() + ")", econStaff, f -> !member.getRoles().contains(temp)));
//
//                grant.addRequirement(new Grant.Requirement("Nation is not active in past 24h", econStaff, f -> f.getActive_m() < 1440));
//                grant.addRequirement(new Grant.Requirement("Nation is not active in past 7d", econGov, f -> f.getActive_m() < 10000));
//
//                grant.addRequirement(new Grant.Requirement("Nation does not have 5 raids going", econStaff, f -> f.getCities() >= 10 || f.getOff() >= 5));
//
//                if (nation.getNumWars() > 0) {
//                    // require max barracks
//                    grant.addRequirement(new Grant.Requirement("Nation does not have 5 barracks in each city (raiding)", econStaff, f -> f.getMMRBuildingStr().charAt(0) == '5'));
//                }
//
//                if (nation.getCities() >= 10 && nation.getNumWars() == 0) {
//                    // require 5 hangars
//                    grant.addRequirement(new Grant.Requirement("Nation does not have 5 hangars in each city (peacetime)", econStaff, f -> f.getMMRBuildingStr().charAt(2) == '5'));
//                    if (type == DepositType.CITY || type == DepositType.INFRA || type == DepositType.LAND) {
//                        grant.addRequirement(new Grant.Requirement("Nation does not have 0 factories in each city (peacetime)", econStaff, f -> f.getMMRBuildingStr().charAt(1) == '0'));
//                        grant.addRequirement(new Grant.Requirement("Nation does not have max aircraft", econStaff, f -> f.getMMR().charAt(2) == '5'));
//                    }
//                }
//
//                if (type != DepositType.WARCHEST) grant.addRequirement(new Grant.Requirement("Nation is beige", econStaff, f -> !f.isBeige()));
//                grant.addRequirement(new Grant.Requirement("Nation is gray", econStaff, f -> !f.isGray()));
//                grant.addRequirement(new Grant.Requirement("Nation is blockaded", econStaff, f -> !f.isBlockaded()));
//
//                // TODO no disburse past 5 days during wartime
//                // TODO 2d seniority and 5 won wars for initial 1.7k infra grants
//                grant.addRequirement(new Grant.Requirement("Nation does not have 10d seniority", econStaff, f -> f.allianceSeniority() >= 10));
//
//                grant.addRequirement(new Grant.Requirement("Nation does not have 80% daily logins (past 1 weeks)", econStaff, f -> nation.avg_daily_login_week() > 0.8));
//                if (nation.getCities() < 10 && type != DepositType.WARCHEST) {
//                    // mmr = 5000
//                    grant.addRequirement(new Grant.Requirement("Nation is not mmr=5000 (5 barracks, 0 factories, 0 hangars, 0 drydocks in each city)\n" +
//                            "(peacetime raiding below city 10)", econStaff, f -> f.getMMRBuildingStr().startsWith("5000")));
//                }
//
//                switch (type) {
//                    case WARCHEST:
//                        grant.addRequirement(new Grant.Requirement("Nation does not have 10 cities", econStaff, f -> f.getCities() >= 10));
//                        grant.addRequirement(new Grant.Requirement("Nation is losing", econStaff, f -> f.getRelativeStrength() < 1));
//                        grant.addRequirement(new Grant.Requirement("Nation is on low military", econStaff, f -> f.getAircraftPct() < 0.7));
//
//                        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(120);
//                        double[] allowed = getAllowedWarchest(db, nation, cutoff, econStaff);
//
//                        grant.addRequirement(new Grant.Requirement("Amount sent over past 120 days exceeds WARCHEST_PER_CITY\n" +
//                                "Tried to send: " + PnwUtil.resourcesToString(grant.cost()) + "\n" +
//                                "Allowance: " + PnwUtil.resourcesToString(allowed), econGov, f -> {
//                            double[] cost = grant.cost();
//                            for (int i = 0; i < allowed.length; i++) {
//                                if (cost[i] > allowed[i] + 0.01) return false;
//                            }
//                            return true;
//                        }));
//
//                        grant.addRequirement(new Grant.Requirement("Nation does not have 10 cities", econStaff, f -> f.getCities() >= 10));
//                        grant.addRequirement(new Grant.Requirement("Nation is losing", econStaff, f -> f.getRelativeStrength() < 1));
//                        grant.addRequirement(new Grant.Requirement("Nation is on low military", econStaff, f -> f.getAircraftPct() < 0.7));
//                        grant.addRequirement(new Grant.Requirement("Already received warchest since last war", econGov, new Function<DBNation, Boolean>() {
//                            @Override
//                            public Boolean apply(DBNation nation) {
//                                long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3);
//                                long latestWarTime = 0;
//                                DBWar latestWar = null;
//                                for (DBWar war : nation.getWars()) {
//                                    if (war.date > latestWarTime) {
//                                        latestWarTime = war.date;
//                                        latestWar = war;
//                                    }
//                                }
//                                if (latestWar != null) {
//                                    for (DBAttack attack : latestWar.getAttacks()) {
//                                        latestWarTime = Math.max(latestWarTime, attack.epoch);
//                                    }
//                                    cutoff = Math.min(latestWarTime, cutoff);
//                                }
//                                List<Transaction2> transactions = nation.getTransactions(-1L);
//                                for (Transaction2 transaction : transactions) {
//                                    if (transaction.tx_datetime < cutoff) continue;
//                                    if (transaction.note != null && transaction.note.toLowerCase().contains("#warchest")) {
//                                        return false;
//                                    }
//                                }
//                                return true;
//                            }
//                        }));
//
//
//                        // has not received warchest in past 3 days
//                        // is assigned to a counter
//
//                        // fighting an enemy, or there are enemies
//
//                        boolean isCountering = false;
//                        Set<Integer> allies = db.getAllies(true);
//                        WarCategory warChannel = db.getWarChannel();
//                        for (Map.Entry<Integer, WarCategory.WarRoom> entry2 : warChannel.getWarRoomMap().entrySet()) {
//                            WarCategory.WarRoom room = entry2.getValue();
//                            if (room.isParticipant(nation, false)) {
//                                boolean isDefending = false;
//                                boolean isEnemyAttackingMember = false;
//                                for (DBWar war : room.target.getActiveWars()) {
//                                    if (allies.contains(war.defender_aa)) {
//                                        isEnemyAttackingMember = true;
//                                    }
//                                    if (war.defender_id == nation.getNation_id()) {
//                                        isDefending = true;
//                                    }
//                                }
//                                if (!isDefending && isEnemyAttackingMember) {
//                                    isCountering = true;
//                                }
//                            }
//                        }
//
//                        boolean isLosing = nation.getRelativeStrength() < 1;
//
//                        boolean hasEnemy = false;
//                        Set<Integer> enemies = db.getCoalition(Coalition.ENEMIES);
//
//                        boolean correctMMR = PnwUtil.matchesMMR(nation.getMMRBuildingStr(), "555X");
//
//                        // is assigned counter
//                        // OR
//                        // enemies AND mmr=555X
//
//                        // TODO
//                        // - funds for only a specific unit
//                        // - limit 24h
//                        // - dont allow more than 5 since last war
//                        // - dont allow more than 1 in 5 days if no units were bought in last X days
//
//                /*
//                Nation has max aircraft and 3% tanks
//                 */
//                        if (!enemies.isEmpty() && nation.getRelativeStrength() >= 1) {
//                            String mmr = nation.getMMRBuildingStr();
//                            // 80% planes + 50%
//                        }
//                        // has enemies, or ordered to counter
//
//                /*
//                    Has enemies and has not received warchest in the past 5 days
//                    Added to a war room as an attacker
//                    Has not received warchest in the past 5 days
//                 */
//
//                        break;
//                    case PROJECT:
//                        Project project = Projects.get(grant.getAmount());
//                        grant.addRequirement(new Grant.Requirement("Domestic policy must be set to TECHNOLOGICAL_ADVANCEMENT for project grants: <https://politicsandwar.com/nation/edit/>", econStaff, f -> f.getDomesticPolicy() == DomesticPolicy.TECHNOLOGICAL_ADVANCEMENT));
//                        grant.addRequirement(new Grant.Requirement("Nation still has a project timer", econGov, f -> f.getProjectTurns() <= 0));
//                        grant.addRequirement(new Grant.Requirement("Nation has no free project slot", econGov, f -> f.projectSlots() > f.getNumProjects()));
//
//                        Set<Project> allowedProjects = db.getHandler().getAllowedProjectGrantSet(nation);
//
//                        grant.addRequirement(new Grant.Requirement("Recommended projects are: " + StringMan.getString(allowedProjects), econStaff, f -> allowedProjects.contains(project)));
//                        grant.addRequirement(new Grant.Requirement("Already have project", false, f -> {
//                            return !f.getProjects().contains(project);
//                        }));
//                        if (project == Projects.URBAN_PLANNING || project == Projects.ADVANCED_URBAN_PLANNING || project == Projects.METROPOLITAN_PLANNING) {
//                            grant.addRequirement(new Grant.Requirement("Please contact econ gov to approve this grant (as its expensive)", econStaff, f -> {
//                                return !f.getProjects().contains(project);
//                            }));
//                        }
//                        for (Project required : project.requiredProjects()) {
//                            grant.addRequirement(new Grant.Requirement("Missing required project: " + required.name(), false, f -> f.hasProject(required)));
//                        }
//                        if (project.requiredCities() > 1) {
//                            grant.addRequirement(new Grant.Requirement("Project requires " + project.requiredCities() + " cities", false, f -> f.getCities() >= project.requiredCities()));
//                        }
//                        if (project.maxCities() > 0) {
//                            grant.addRequirement(new Grant.Requirement("Project cannot be built above " + project.requiredCities() + " cities", false, f -> f.getCities() <= project.maxCities()));
//                        }
//
//                        grant.addRequirement(new Grant.Requirement("Already received a grant for a project in past 10 days", econGov, new Function<DBNation, Boolean>() {
//                            @Override
//                            public Boolean apply(DBNation nation) {
//                                long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10);
//
//                                List<Transaction2> transactions = nation.getTransactions(-1L);
//
//                                if (Grant.hasReceivedGrantWithNote(transactions, "#project", cutoff)) return false;
//
//                                if (Grant.getProjectsGranted(nation, transactions).contains(project)) return false;
//                                return true;
//                            }
//                        }));
//
//                        break;
//                    case CITY: {
//                        int upTo = Integer.parseInt(grant.getAmount());
//                        if (upTo > 1) {
//                            grant.addRequirement(new Grant.Requirement("City timer prevents purchasing more cities after c10", econGov, f -> {
//                                int max = Math.max(10, f.getCities() + 1);
//                                return upTo <= max;
//                            }));
//                        }
//
//
//                        grant.addRequirement(new Grant.Requirement("Nation already has 20 cities", true, f -> f.getCities() < 20));
//                        grant.addRequirement(new Grant.Requirement("Nation does not have 10 cities", true, f -> f.getCities() >= 10));
//
//                        grant.addRequirement(new Grant.Requirement("Domestic policy must be set to MANIFEST_DESTINY for city grants: <https://politicsandwar.com/nation/edit/>", econStaff, f -> f.getDomesticPolicy() == DomesticPolicy.MANIFEST_DESTINY));
//
//                        grant.addRequirement(new Grant.Requirement("It is recommended to alternate city and project timers after c15", true, f -> f.getProjectTurns() <= 0));
//
//                        grant.addRequirement(new Grant.Requirement("Nation still has a city timer", econGov, f -> f.getCities() < 10 || f.getCityTurns() <= 0));
//
//                        int currentCities = nation.getCities();
//                        grant.addRequirement(new Grant.Requirement("Nation has built a city, please run the grant command again", false, f -> f.getCities() == currentCities));
//
//                        grant.addRequirement(new Grant.Requirement("Nation received city grant in past 10 days", econGov, new Function<DBNation, Boolean>() {
//                            @Override
//                            public Boolean apply(DBNation nation) {
//                                long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10);
//                                List<Transaction2> transactions = nation.getTransactions(-1L);
//                                return (!Grant.hasReceivedGrantWithNote(transactions, "#city", cutoff));
//                            }
//                        }));
//
//                        grant.addRequirement(new Grant.Requirement("Already received a grant for a city", econStaff, new Function<DBNation, Boolean>() {
//                            @Override
//                            public Boolean apply(DBNation nation) {
//                                List<Transaction2> transactions = nation.getTransactions(-1);
//                                return !Grant.hasGrantedCity(nation, transactions, currentCities + 1);
//                            }
//                        }));
//                        break;
//                    }
//                    case INFRA: {
//                        // TODO check has not received an infra grant for that city
//
//                        double amount = Double.parseDouble(grant.getAmount());
//                        Map<Integer, JavaCity> cities = nation.getCityMap(true, false);
//
//                        Set<Integer> grantCities = grant.getCities();
//                        Set<Integer> isForPoweredCities = new HashSet<>();
//                        for (Integer grantCity : grantCities) {
//                            JavaCity city = cities.get(grantCity);
//                            if (city.getPowered(nation::hasProject) && city.getRequiredInfra() > city.getInfra() && city.getRequiredInfra() > 1450) {
//                                isForPoweredCities.add(grantCity);
//                            }
//                        }
//
//                        grant.addRequirement(new Grant.Requirement("Grant is for powered city with damaged infra (ids: " + StringMan.getString(isForPoweredCities), true, f -> {
//                            return isForPoweredCities.isEmpty();
//                        }));
//                        Map<Integer, Double> infraGrants = Grant.getInfraGrantsByCity(nation, nation.getTransactions());
//                        grant.addRequirement(new Grant.Requirement("City already received infra grant (ids: " + StringMan.getString(infraGrants), econStaff, f -> {
//                            // if city current infra is less than infra grant
//                            return infraGrants.isEmpty();
//                        }));
//
//
//                        Grant.Requirement noWarRequirement = new Grant.Requirement("Higher infra grants require approval when fighting wars", econStaff, new Function<DBNation, Boolean>() {
//                            @Override
//                            public Boolean apply(DBNation nation) {
//                                if (nation.getDef() > 0) return false;
//                                if (nation.getNumWars() > 0 && nation.getNumWarsAgainstActives() > 0) return false;
//                                return true;
//                            }
//                        });
//
//                        Grant.Requirement seniority = new Grant.Requirement("Nation does not have 3 days alliance seniority", econStaff, f -> f.allianceSeniority() < 3);
//
//                        // if amount is uneven
//                        if (amount % 50 != 0) {
//                            grant.addRequirement(new Grant.Requirement("Amount must be a multiple of 50", false, f -> false));
//                        }
//
//                        if (amount >= 1500) {
//                            grant.addRequirement(seniority);
//                            grant.addRequirement(new Grant.Requirement("Infra grants are restricted during wartime. Please contact econ (or remove the `enemies` coalition)", econStaff, f -> db.getCoalitionRaw(Coalition.ENEMIES).isEmpty()));
//                            grant.addRequirement(noWarRequirement);
//                        }
//                        if (amount > 1700) {
//                            grant.addRequirement(new Grant.Requirement("Nation does not have 10 cities", econStaff, f -> f.getCities() >= 10));
//                        }
//                        if (amount >= 2000) {
//                            grant.addRequirement(seniority);
//                            grant.addRequirement(noWarRequirement);
//                            grant.addRequirement(new Grant.Requirement("Infra grants are restricted during wartime. Please contact econ", econStaff, f -> db.getCoalitionRaw(Coalition.ENEMIES).isEmpty()));
//                            grant.addRequirement(new Grant.Requirement("Domestic policy must be set to URBANIZATION for infra grants above 1700: <https://politicsandwar.com/nation/edit/>", econStaff, f -> f.getDomesticPolicy() == DomesticPolicy.URBANIZATION));
//                            grant.addRequirement(new Grant.Requirement("Infra grants above 1700 whilst raiding/warring require econ approval", econStaff, f -> {
//                                if (f.getDef() > 0) return false;
//                                if (f.getOff() > 0) {
//                                    for (DBWar war : f.getActiveWars()) {
//                                        DBNation other = war.getNation(war.isAttacker(f));
//                                        if (other.getActive_m() < 1440 || other.getPosition() >= Rank.APPLICANT.id) {
//                                            return false;
//                                        }
//                                    }
//                                }
//                                return true;
//                            }));
//                        }
//                        int max = 2000;
//                        if (nation.getCities() > 15) max = 2250;
//                        if (nation.getCities() > 20) max = 2500;
//                        if (amount > 2000) {
//                            // todo
//                            // 10-15 2k
//                            // 15-20 2.25k
//                            // 20-25 2.5k
//                            grant.addRequirement(new Grant.Requirement("Nation does not have 10 cities", econStaff, f -> f.getCities() >= 10));
//                        }
//
//                        break;
//                    }
//                    case LAND:
//
//
//                    case BUILD:
//                        // MMR of build matches required MMR
//                    case UNIT:
//                    case RESOURCES: {
//                        grant.addRequirement(new Grant.Requirement("Amount must be a multiple of 50", econGov, f -> false));
//                        GuildMessageChannel channel = db.getOrNull(GuildKey.RESOURCE_REQUEST_CHANNEL);
//                        if (channel != null) {
//                            throw new IllegalArgumentException("Please use " + CM.transfer.self.cmd.toSlashMention() + " or `" + Settings.commandPrefix(true) + "disburse` in " + channel.getAsMention() + " to request funds from your deposits");
//                        }
//                    }
//                    default:
//
//                        throw new UnsupportedOperationException("TODO: This type of grant is not supported currently");
//                        break;
//                }
//
//            }
//        });
//
//        // 60 day minimum expire for staff
//
//        // if no econ perms, only 1 nation, and has to be self
//        //
//        return "TODO";
//    }
//
//    public double[] getAllowedWarchest(@Me GuildDB db, DBNation nation, long cutoff, boolean addWarchestBase) {
//        double[] warchestMax = PnwUtil.resourcesToArray(db.getPerCityWarchest(nation));
//        List<DBWar> wars = nation.getWars();
//
//        Set<Long> banks = db.getTrackedBanks();
//        List<Map.Entry<Integer, Transaction2>> transactions = nation.getTransactions(db, banks, true, true, 0, 0);
//
//        double[] transfers = ResourceType.getBuffer();
//
//        for (Map.Entry<Integer, Transaction2> entry : transactions) {
//            Transaction2 tx = entry.getValue();
//            if (tx.note == null || tx.tx_datetime < cutoff) continue;
//            Map<String, String> notes = PnwUtil.parseTransferHashNotes(tx.note);
//            if (!notes.containsKey("#warchest")) continue;
//            int sign = entry.getKey();
//            if (sign > 0 && tx.tx_id > 0 && !notes.containsKey("#ignore")) continue;
//
//            transfers = PnwUtil.add(transfers, PnwUtil.multiply(tx.resources, sign));
//        }
//
//        double[] warExpenses = ResourceType.getBuffer();
//
//        long offensiveLeeway = TimeUnit.DAYS.toMillis(14);
//        for (int i = 0; i < wars.size(); i++) {
//            DBWar war = wars.get(i);
//            if (war.date < cutoff) continue;
//            if (war.attacker_id == nation.getNation_id()) {
//                if (!banks.contains(war.attacker_aa)) continue;
//                if (war.status == WarStatus.EXPIRED || war.status == WarStatus.DEFENDER_VICTORY) {
//                    List<DBAttack> attacks = war.getAttacks();
//                    attacks.removeIf(f -> f.attacker_nation_id != nation.getNation_id());
//                    if (attacks.isEmpty()) continue;
//                }
//                // was against an inactive, or a non enemy
//            } else {
//                if (!banks.contains(war.defender_aa)) continue;
//                if (war.status == WarStatus.ATTACKER_VICTORY || war.status == WarStatus.EXPIRED || war.status == WarStatus.PEACE) {
//                    List<DBAttack> attacks = war.getAttacks();
//                    attacks.removeIf(f -> f.attacker_nation_id != nation.getNation_id());
//                    if (attacks.isEmpty()) continue;
//
//                    boolean hasOffensive = false;
//                    for (int j = i - 1; j >= 0; j--) {
//                        DBWar other = wars.get(j);
//                        if (war.date - other.date > offensiveLeeway) break;
//                        if (other.attacker_id == nation.getNation_id()) {
//                            hasOffensive = true;
//                            break;
//                        }
//                    }
//                    if (!hasOffensive) {
//                        for (int j = i + 1; j < wars.size(); j++) {
//                            DBWar other = wars.get(j);
//                            if (other.date - war.date > offensiveLeeway) break;
//                            if (other.attacker_id == nation.getNation_id()) {
//                                hasOffensive = true;
//                                break;
//                            }
//                        }
//                        if (!hasOffensive) continue;
//                    }
//                }
//
//                double[] cost = PnwUtil.resourcesToArray(war.toCost().getTotal(war.isAttacker(nation), true, false, true, true));
//                for (int j = 0; j < cost.length; j++) {
//                    double amt = cost[j];
//                    if (amt > 0) warExpenses[j] += amt;
//                }
//                // add war cost
//
//            }
//        }
//
//        double[] total = ResourceType.getBuffer();
//        if (addWarchestBase) total = ResourceType.add(total, warchestMax);
//        total = ResourceType.add(total, transfers);
//        total = ResourceType.add(total, warExpenses);
//        for (int i = 0; i < total.length; i++) total[i] = Math.max(0, Math.min(warchestMax[i], total[i]));
//
//        return total;
//    }
//
//
//
//    // for all cities
//    // for new cities
//    @Command(desc = "Calculate and send funds for specified military units")
//    @RolePermission(Roles.MEMBER)
//    public String unit(@Me GuildDB db, @Me User author, @Me DBNation me, Set<DBNation> nations, MilitaryUnit unit, @Switch("q") Integer quantity,
//                       @Switch("p") @Range(min =0, max=100) Double percent,
//                       @Switch("a") double sendAttackConsumption,
//                       @Switch("n") Integer isForNewCities,
//
//                       @Switch("m") MMRDouble capAtMMR,
//                       @Switch("b") boolean capAtCurrentMMR,
//                       @Switch("c") boolean multiplyPerCity,
//
//                       @Switch("u") boolean onlySendMissingUnits,
//
//                       @Switch("f") boolean forceOverrideChecks,
//
//                       @Switch("o") boolean onlySendMissingFunds,
//                       @Switch("e") @Timediff Long expire,
//                       @Switch("cash") boolean countAsCash) {
//
//        if (unit.getBuilding() == null && (capAtMMR != null || multiplyPerCity || percent != null || isForNewCities != null)) {
//            throw new IllegalArgumentException("Unit " + unit + " is not valid with the arguments: capAtMMR,multiplyPerCity,percent,isForNewCities");
//        }
//        if ((quantity == null) == (percent == null)) throw new IllegalArgumentException("Please specify `percent` OR `quantity`, not both");
//        if (percent != null && multiplyPerCity) throw new IllegalArgumentException("multiplyPerCity is only valid for absolute values");
//
//        // Map<DBNation, double[]> fundsToSend, Map<DBNation, List<String>> notes, Map<DBNation, String> instructions, Map<DBNation, List<String>> errors
//        Map<DBNation, Grant> grants = new HashMap<>();
//        Map<DBNation, List<String>> errors = new HashMap<>();
//
//        if (nations.size() > 200) throw new IllegalArgumentException("Too many nations (Max 200)");
//        if (nations.stream().map(f -> f.getAlliance_id()).collect(Collectors.toSet()).size() > 4) throw new IllegalArgumentException("Too many alliances (max 4)");
//
//        for (DBNation nation : nations) {
//            int currentAmt = nation.getUnits(unit);
//            int numCities = isForNewCities != null ? isForNewCities : nation.getCities();
//
//            Double natQuantity = (double) quantity;
//            if (percent != null) {
//                natQuantity = numCities * unit.getBuilding().max() * unit.getBuilding().cap(nation::hasProject) * percent / 100d;
//            }
//            if (multiplyPerCity) {
//                natQuantity *= numCities;
//            }
//
//            Map<Integer, JavaCity> cities = nation.getCityMap(false);
//            Set<JavaCity> previousCities = cities.values().stream().filter(f -> f.getAge() >= 1).collect(Collectors.toSet());
//
//            int currentUnits = nation.getUnits(unit);
//            if (isForNewCities != null) {
//                if (previousCities.isEmpty()) {
//                    currentUnits = 0;
//                } else {
//                    currentUnits = Math.max(0, unit.getCap(() -> previousCities, nation::hasProject) - currentUnits);
//                }
//            }
//
//            int cap = Integer.MAX_VALUE;
//            if (capAtMMR != null) {
//                cap = (int) (capAtMMR.get(unit) * unit.getBuilding().max() * numCities);
//            }
//            if (capAtCurrentMMR) {
//                if (isForNewCities == null) {
//                    cap = Math.min(cap, unit.getCap(nation, false));
//                } else {
//                    MMRDouble mmr = new MMRDouble(nation.getMMRBuildingArr());
//                    cap = Math.min(cap, (int) (mmr.get(unit) * unit.getBuilding().max() * numCities));
//                }
//            }
//            natQuantity = Math.min(natQuantity, cap);
//
//            int unitsToSend = (int) (onlySendMissingUnits ? Math.max(natQuantity - nation.getUnits(unit), 0) : natQuantity);
//
//            double[] funds = unit.getCost(unitsToSend);
//            if (sendAttackConsumption > 0) {
//                funds = ResourceType.add(funds, PnwUtil.multiply(unit.getConsumption().clone(), sendAttackConsumption));
//            }
//
//            if (ResourceType.isEmpty(funds)) {
//                errors.computeIfAbsent(nation, f -> new ArrayList<>()).add("No funds need to be sent");
//                continue;
//            }
//
//            double[] finalFunds = funds;
//            Grant grant = new Grant(nation, DepositType.WARCHEST)
//                    .setInstructions(DepositType.UNIT.instructions + "\n" + unit + "=" + MathMan.format(natQuantity))
//                    .setTitle(unit.name())
//                    .setCost(f -> finalFunds)
//                    .addRequirement(new Grant.Requirement("Nation units have changed, try again", false, f -> f.getUnits(unit) == currentAmt));
//            ;
//
//            grants.put(nation, grant);
//        }
//
//        return send(db, author, me, grants, errors, DepositType.UNIT, onlySendMissingFunds, expire, countAsCash);
//    }
//
//    @Command(desc = "Send funds for mmr")
//    public String mmr(@Me GuildDB db, @Me User author, @Me DBNation me, Set<DBNation> nations,
//                      @Arg("MMR worth of units to send (only missing)") MMRDouble grantMMR,
//                      @Switch("r") @Arg("Additional rebuy worth of units `10/10/10/6` would be two full rebuys") MMRDouble rebuyMMR,
//                      @Switch("b") boolean switchBuildFunds,
//                      @Switch("a") @Arg("Funds for unit consumption 10/10/10/10 would be funds for 10 attacks of each uni (at max MMR)") MMRDouble sendConsumptionForAttacks,
//                      @Switch("p") boolean ignoreLowPopulation,
//                      @Switch("o") boolean onlySendMissingFunds,
//                      @Switch("e") @Timediff Long expire) {
//        Map<DBNation, double[]> amountsToSendMap = new HashMap<>();
//        for (DBNation nation : nations) {
//            double[] funds = ResourceType.getBuffer();
//
//            for (MilitaryUnit unit : MilitaryUnit.values) {
//                if (unit.getBuilding() == null) continue;
//                int cap;
//                if (ignoreLowPopulation) {
//                    cap = unit.getBuilding().cap(nation::hasProject) * unit.getBuilding().max() * nation.getCities();
//                } else {
//                    cap = unit.getCap(nation, false);
//                }
//                int amt = Math.max(0, (int) (cap * grantMMR.getPercent(unit)) - nation.getUnits(unit));
//                if (rebuyMMR != null) amt += rebuyMMR.getPercent(unit) * cap;
//                if (amt > 0) {
//                    funds = ResourceType.add(funds, unit.getCost(amt));
//                }
//            }
//
//            if (sendConsumptionForAttacks != null) {
//                for (MilitaryUnit unit : MilitaryUnit.values) {
//                    double numAttacks = sendConsumptionForAttacks.get(unit);
//                    if (numAttacks > 0) {
//                        funds = ResourceType.add(funds, PnwUtil.multiply(unit.getConsumption().clone(), numAttacks));
//                    }
//                }
//            }
//            if (switchBuildFunds) {
//                for (Map.Entry<Integer, DBCity> entry : nation._getCitiesV3().entrySet()) {
//                    DBCity city = entry.getValue();
//                    for (MilitaryUnit unit : MilitaryUnit.values) {
//                        double required = grantMMR.get(unit);
//                        if (required <= 0) continue;
//                        int current = city.get(unit.getBuilding());
//                        if (current < required) {
//                            int toBuy = (int) Math.ceil(required - current);
//                            unit.getBuilding().cost(funds, toBuy);
//                        }
//                    }
//                }
//            }
//            amountsToSendMap.put(nation, funds);
//        }
//        return send(db, author, me, amountsToSendMap, onlySendMissingFunds, expire, note, instructions);
//    }
//
//    @Command(desc = "Send funds for a city")
//    @RolePermission(Roles.MEMBER)
//    public String city(@Me GuildDB db, @Me User author, @Me DBNation me, Set<DBNation> nations,
//                       int buyUpToCity,
//                       @Switch("r") boolean citiesAreAdditional,
//                       @Switch("d") boolean forceManifestDestiny,
//                       @Switch("u") boolean forceUrbanPlanning,
//                       @Switch("a") boolean forceAdvancedUrbanPlanning,
//                       @Switch("m") boolean forceMetropolitanPlanning,
//
//                       @Switch("p") boolean includeProjectPurchases,
//                       @Switch("b") @Arg("Requires the force project argument as true") boolean onlyForceProjectIfCheaper,
//                       @Switch("i") Double includeInfraGrant,
//                       @Switch("l") Double includeLandGrant,
//                       @Switch("j") CityBuild includeCityBuildJsonCost,
//                       @Switch("mmr") MMRInt includeNewUnitCost,
//                       @Switch("w") boolean includeNewCityWarchest,
//                       @Switch("o") boolean onlySendMissingFunds,
//                       @Switch("e") @Timediff Long expire
//    ) {
//        if (onlyForceProjectIfCheaper && !forceAdvancedUrbanPlanning && !forceUrbanPlanning && !forceMetropolitanPlanning) {
//            throw new IllegalArgumentException("`onlyBuyProjectIfCheaper` is enabled, but no project purchases are: forceAdvancedUrbanPlanning,forceUrbanPlanning,forceMetropolitanPlanning");
//        }
//
//        Map<DBNation, List<String>> notesMap = new HashMap<>();
//        Map<DBNation, double[]> amountsToSendMap = new HashMap<>();
//
//        for (DBNation nation : nations) {
//            int cityTo = citiesAreAdditional ? nation.getCities() + buyUpToCity : buyUpToCity;
//            if (cityTo <= nation.getCities()) continue;
//            int numCities = cityTo - nation.getCities();
//
//            boolean manifest = nation.getDomesticPolicy() == DomesticPolicy.MANIFEST_DESTINY || forceManifestDestiny;
//            boolean up = nation.hasProject(Projects.URBAN_PLANNING) || (forceUrbanPlanning && !onlyForceProjectIfCheaper);
//            boolean aup = nation.hasProject(Projects.ADVANCED_URBAN_PLANNING) || (forceAdvancedUrbanPlanning && !onlyForceProjectIfCheaper);
//            boolean mp = nation.hasProject(Projects.METROPOLITAN_PLANNING) || (forceMetropolitanPlanning && !onlyForceProjectIfCheaper);
//
//            List<String> notes = new ArrayList<>();
//            double[] funds = ResourceType.getBuffer();
//
//            if (!up && forceUrbanPlanning && cityTo > Projects.URBAN_PLANNING.requiredCities()) {
//                double cost1 = PnwUtil.cityCost(nation.getCities(), cityTo, manifest, up, aup, mp);
//                double[] projectCost = Projects.URBAN_PLANNING.cost(true);
//                double cost2 = PnwUtil.cityCost(nation.getCities(), cityTo, manifest, true, aup, mp) + PnwUtil.convertedTotal(projectCost);
//                if (cost2 <= cost1) {
//                    up = true;
//                    if (includeProjectPurchases) funds = PnwUtil.add(funds, projectCost);
//                }
//            }
//            if (!aup && forceAdvancedUrbanPlanning && cityTo > Projects.ADVANCED_URBAN_PLANNING.requiredCities()) {
//                double cost1 = PnwUtil.cityCost(nation.getCities(), cityTo, manifest, up, aup, mp);
//                double[] projectCost = Projects.ADVANCED_URBAN_PLANNING.cost(true);
//                double cost2 = PnwUtil.cityCost(nation.getCities(), cityTo, manifest, up, true, mp) + PnwUtil.convertedTotal(projectCost);
//                if (cost2 <= cost1) {
//                    aup = true;
//                    if (includeProjectPurchases) funds = PnwUtil.add(funds, projectCost);
//                }
//            }
//            if (!mp && forceUrbanPlanning && cityTo > Projects.METROPOLITAN_PLANNING.requiredCities()) {
//                double cost1 = PnwUtil.cityCost(nation.getCities(), cityTo, manifest, up, aup, mp);
//                double[] projectCost = Projects.METROPOLITAN_PLANNING.cost(true);
//                double cost2 = PnwUtil.cityCost(nation.getCities(), cityTo, manifest, up, aup, true) + PnwUtil.convertedTotal(projectCost);
//                if (cost2 <= cost1) {
//                    mp = true;
//                    if (includeProjectPurchases) funds = PnwUtil.add(funds, projectCost);
//                }
//            }
//
//            funds[0] += PnwUtil.cityCost(nation.getCities(), cityTo, manifest, up, aup, mp);
//
//            if (includeCityBuildJsonCost != null) {
//                if (includeCityBuildJsonCost.getLand() != null) {
//                    includeLandGrant = includeCityBuildJsonCost.getLand();
//                }
//                if (includeCityBuildJsonCost.getInfraNeeded() != null) {
//                    includeInfraGrant = includeCityBuildJsonCost.getInfraNeeded().doubleValue();
//                }
//                JavaCity city = new JavaCity(includeCityBuildJsonCost);
//                for (Building building : Buildings.values()) {
//                    int amt = city.get(building);
//                    if (amt > 0) {
//                        building.cost(funds, amt * numCities);
//                    }
//                }
//            }
//            if (includeInfraGrant != null && includeInfraGrant > 10) {
//                funds[0] += PnwUtil.calculateInfra(10, includeInfraGrant) * numCities;
//            }
//            if (includeLandGrant != null && includeLandGrant > 250) {
//                funds[0] += PnwUtil.calculateLand(250, includeLandGrant) * numCities;
//            }
//
//            if (includeNewUnitCost != null) {
//                for (MilitaryUnit unit : MilitaryUnit.values()) {
//                    double numBuildings = includeNewUnitCost.get(unit);
//                    if (numBuildings > 0) {
//                        int units = (int) (unit.getBuilding().max() * numBuildings * numCities);
//                        funds = ResourceType.add(funds, unit.getCost(units));
//                    }
//                }
//            }
//            if (includeNewCityWarchest) {
//                funds = ResourceType.add(funds, PnwUtil.multiply(PnwUtil.resourcesToArray(db.getPerCityWarchest(nation)), numCities));
//            }
//            amountsToSendMap.put(nation, funds);
//        }
//
//        Grant.getInfraGrantsByCity()
//        return send(db, author, me, amountsToSendMap, onlySendMissingFunds, expire, note, instructions);
//    }
//
//    @Command(desc = "Send funds for a city")
//    @RolePermission(Roles.MEMBER)
//    public String infra(@Me GuildDB db, @Me User author, @Me DBNation me, Set<DBNation> nations,
//                       int buyUpToInfra,
//                       @Switch("d") DBCity forCity,
//                       @Switch("p") boolean buyFromPreviousGrantLevel,
//                       @Switch("b") boolean forNewCity,
//                       @Switch("a") boolean forceAdvancedEngineeringCorps,
//                       @Switch("c") boolean forceCenterForCivilEngineering,
//                       @Switch("u") boolean forceUrbanization,
//                       @Switch("g") boolean forceGovernmentSupportAgency,
//                       @Switch("o") boolean onlySendMissingFunds,
//                       @Switch("e") @Timestamp Long expire,
//                       @Switch("n") String note
//    ) {
//        // anyone can run the grant command
//        // displays current infra level -> amount
//
//        Map<DBNation, Grant> grants = new HashMap<>();
//        Map<DBNation, List<String>> errors = new HashMap<>();
//
//        for (DBNation nation : nations) {
//            double[] funds = ResourceType.getBuffer();
//
//            Map<Integer, Double> currentInfraLevels = new HashMap<>();
//
//            Grant grant = new Grant(nation, DepositType.INFRA);
//
//            if (forNewCity) {
//                // requires econ gov
//                if (!Roles.ECON.has(author, db.getGuild())) {
//                    grant.addRequirement(new Grant.Requirement("Requires ECON role to send funds for a new city. Use the `forCity` argument instead", false, f -> Roles.ECON.has(author, db.getGuild())));
//                }
//                currentInfraLevels.put(-1, 10d);
//            } else if (forCity != null) {
//                if (!nation._getCitiesV3().containsKey(forCity.getId())) {
//                    errors.computeIfAbsent(nation, f -> new ArrayList<>()).add("No city with id " + forCity.getId() + " found in nation " + nation.getName());
//                    continue;
//                }
//                currentInfraLevels.put(forCity.id, forCity.infra);
//                grant.setCities(Collections.singleton(forCity.id));
//            } else {
//                grant.setAllCities();
//                // set current infra levels from nation's cities
//            }
//            // process buyFromPreviousGrantLevel
//            if (buyFromPreviousGrantLevel) {
//                List<Transaction2> transactions = null;
//                for (Map.Entry<Integer, Double> entry : currentInfraLevels.entrySet()) {
//                    if (entry.getKey() != -1) {
//                        if (transactions == null) transactions = nation.getTransactions();
//                        double previous = Grant.getCityInfraGranted(nation, entry.getKey(), transactions);
//                        entry.setValue(Math.max(previous, entry.getValue()));
//                    }
//                }
//            }
//
//            // remove any that have that much infra akready
//
//            // if  newinfralevels is empty
//            if (currentInfraLevels.isEmpty()) {
//                errors.computeIfAbsent(nation, f -> new ArrayList<>()).add("No infra needs to be granted for " + nation.getNation() + " (already has sufficient)");
//                continue;
//            }
//
//            boolean aec = nation.hasProject(Projects.ADVANCED_ENGINEERING_CORPS) || forceAdvancedEngineeringCorps;
//            boolean cce = nation.hasProject(Projects.CENTER_FOR_CIVIL_ENGINEERING) || forceCenterForCivilEngineering;
//            boolean gsa = nation.hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY) || forceGovernmentSupportAgency;
//            boolean urb = nation.getDomesticPolicy() == DomesticPolicy.URBANIZATION || forceUrbanization;
//
//            grant.setCost(f -> {
//                double total = 0;
//                for (Map.Entry<Integer, Double> entry : currentInfraLevels.entrySet()) {
//                    if (entry.getValue() < buyUpToInfra) {
//                        total += PnwUtil.calculateInfra(entry.getValue(), buyUpToInfra, aec, cce, gsa, urb);
//                    }
//                }
//                return ResourceType.MONEY.toArray(total);
//            });
//
//            // set instructions
//            // use @ symbol to buy
//
//            grant.setAmount(buyUpToInfra);
//
//        }
//        return send(db, author, me, amountsToSendMap, onlySendMissingFunds, expire, note);
//    }
//
//
//
//     infra
//     build
//     land
//     project
//     resources
//     warchest
//
//    @Command
//    @RolePermission(Roles.MEMBER)
//    public String resources() {
//
//    }
//
//
//
//    @Command
//    @RolePermission(Roles.MEMBER)
//    public String project() {
//
//    }
//
//    @Command
//    @RolePermission(Roles.MEMBER)
//    public String infra() {
//
//    }
//
//    @Command
//    @RolePermission(Roles.MEMBER)
//    public String land(NationList nations, double landUpTo, @Default CityFilter cities, @Switch("m") boolean onlyMissingFunds, @Switch("e") int expireAfterDays, @Switch("f") boolean bypassChecks) {
//
//    }

    @WhitelistPermission
    @Command
    @RolePermission(value = {Roles.ECON_STAFF, Roles.ECON, Roles.ECON_GRANT_SELF})
    public String approveEscrowed(@Me IMessageIO channel, @Me GuildDB db, @Me DBNation me, @Me User author, DBNation receiver, Map<ResourceType, Double> deposits, Map<ResourceType, Double> escrowed) throws IOException {
        /*
        Member: Can only send funds in their deposits
         */

        boolean memberCanApprove = db.getOrNull(GuildKey.MEMBER_CAN_WITHDRAW) == Boolean.TRUE && (db.getCoalition(Coalition.ENEMIES).isEmpty() || db.getOrNull(GuildKey.MEMBER_CAN_WITHDRAW_WARTIME) == Boolean.TRUE);
        boolean checkDepoValue = !Roles.ECON_STAFF.has(author, db.getGuild());
        boolean checkDepoResource = db.getOrNull(GuildKey.RESOURCE_CONVERSION) != Boolean.TRUE;
        boolean allowExtra = Roles.ECON.has(author, db.getGuild());

        double[] expectedDeposits = PnwUtil.resourcesToArray(deposits);
        double[] actualDeposits;
        boolean depositsMatch = true;
        synchronized (OffshoreInstance.BANK_LOCK) {
            actualDeposits = receiver.getNetDeposits(db, false);

            for (int i = 0; i < actualDeposits.length; i++) {
                if (actualDeposits[i] < expectedDeposits[i]) {
                    depositsMatch = false;
                }
            }

            if (depositsMatch) {

            }
        }

        if (!depositsMatch) {
            String title = "[Error] Outdated. Please try again";

            String body = db.generateEscrowedCard(receiver);
            body += "\nCommand run by: " + author.getAsMention();
//            db.getEscrowed()
            CM.bank.escrow.approve cmd = CM.bank.escrow.approve.cmd.create(receiver.getNationUrl(), PnwUtil.resourcesToString(actualDeposits), PnwUtil.resourcesToString(escrowed));

            String emoji = "Send";
            channel.create().embed(title, body).commandButton(cmd, emoji).send();
            return null;
        }
        return null;

        // check deposits match provided deposits
    }

    @WhitelistPermission
    @Command
    @RolePermission(Roles.MEMBER)
    public synchronized String grants(@Me GuildDB db, DBNation nation) {
        String baseUrl = Settings.INSTANCE.WEB.REDIRECT + "/" + db.getIdLong() + "/";
        List<String> pages = Arrays.asList(
                baseUrl + "infragrants/" + nation.getNation_id(),
                baseUrl + "landgrants/" + nation.getNation_id(),
                baseUrl + "citygrants/" + nation.getNation_id(),
                baseUrl + "projectgrants/" + nation.getNation_id()
        );
        StringBuilder response = new StringBuilder();
        response.append(nation.toMarkdown() + "\n");
        response.append("color=" + nation.getColor() + "\n");
        response.append("mmr[unit]=" + nation.getMMR() + "\n");
        response.append("mmr[build]=" + nation.getMMRBuildingStr() + "\n");
        response.append("timer[city]=" + nation.getCityTurns() + " timer[project]=" + nation.getProjectTurns() + "\n");
        response.append("slots[project]=" + nation.getNumProjects() + "/" + nation.projectSlots() + "\n");
        response.append("activity[turn]=" + MathMan.format(nation.avg_daily_login_turns() * 100) + "%\n");
        response.append("activity[day]=" + MathMan.format(nation.avg_daily_login() * 100) + "%\n");
        return response + "<" + StringMan.join(pages, ">\n<") + ">";
    }
    //
    private Set<Integer> disabledNations = new HashSet<>();
//
//    @WhitelistPermission
//    @Command
//    @RolePermission(Roles.ECON_STAFF)
//    public synchronized String approveGrant(@Me DBNation banker, @Me User user, @Me IMessageIO io, @Me JSONObject command, @Me GuildDB db, UUID key, @Switch("f") boolean force) {
//        OffshoreInstance offshore = db.getOffshore();
//        if (offshore == null) {
//            return "No offshore bank";
//        }
//        try {
//            Grant grant = Grant.getApprovedGrant(db.getIdLong(), key);
//            if (grant == null) {
//                return "Invalid Token. Please try again";
//            }
//            DBNation receiver = grant.getNation();
//
//            receiver.updateTransactions();
//            receiver.getCityMap(true);
//
//            Set<Grant.Requirement> requirements = grant.getRequirements();
//            Set<Grant.Requirement> failed = new HashSet<>();
//            Set<Grant.Requirement> override = new HashSet<>();
//            for (Grant.Requirement requirement : requirements) {
//                Boolean result = requirement.apply(receiver);
//                if (!result) {
//                    if (requirement.canOverride()) {
//                        override.add(requirement);
//                    } else {
//                        failed.add(requirement);
//                    }
//                }
//            }
//
//            if (!failed.isEmpty()) {
//                StringBuilder result = new StringBuilder("Grant could not be approved.\n");
//                if (!failed.isEmpty()) {
//                    result.append("\nFailed checks:\n - " + StringMan.join(failed.stream().map(f -> f.getMessage()).collect(Collectors.toList()), "\n - ") + "\n");
//                }
//                if (!override.isEmpty()) {
//                    result.append("\nFailed checks that you have permission to bypass:\n - " + StringMan.join(override.stream().map(f -> f.getMessage()).collect(Collectors.toList()), "\n - ") + "\n");
//                }
//                return result.toString();
//            }
//
//            if (!force) {
//                String title = grant.title();
//                StringBuilder body = new StringBuilder();
//
//                body.append("Receiver: " + receiver.getNationUrlMarkup(true) + " | " + receiver.getAllianceUrlMarkup(true)).append("\n");
//                body.append("Note: " + grant.getNote()).append("\n");
//                body.append("Amt: " + grant.getAmount()).append("\n");
//                body.append("Cost: `" + PnwUtil.resourcesToString(grant.cost())).append("\n\n");
//
//                if (!override.isEmpty()) {
//                    body.append("**" + override.size() + " failed checks (you have admin override)**\n - ");
//                    body.append(StringMan.join(override.stream().map(f -> f.getMessage()).collect(Collectors.toList()), "\n - ") + "\n\n");
//                }
//
//                io.create().confirmation(title, body.toString(), command).send();
//                return null;
//            }
//            if (disabledNations.contains(receiver.getNation_id())) {
//                return "There was an error processing the grant. Please contact an administrator";
//            }
//
//            Grant.deleteApprovedGrant(db.getIdLong(), key);
//
//            disabledNations.add(receiver.getNation_id());
//
//            Map.Entry<OffshoreInstance.TransferStatus, String> result = offshore.transferFromAllianceDeposits(banker, db, f -> allowedAlliances.contains((long) f), receiver, grant.cost(), grant.getNote());
//            OffshoreInstance.TransferStatus status = result.getKey();
//
//            StringBuilder response = new StringBuilder();
//            if (status == OffshoreInstance.TransferStatus.SUCCESS || status == OffshoreInstance.TransferStatus.ALLIANCE_BANK) {
//                response.append("**Transaction:** ").append(result.getValue()).append("\n");
//                response.append("**Instructions:** ").append(grant.getInstructions());
//
//                Locutus.imp().getExecutor().submit(new Runnable() {
//                    @Override
//                    public void run() {
//                        receiver.updateTransactions();
//                        for (Grant.Requirement requirement : grant.getRequirements()) {
//                            if (!requirement.apply(receiver)) {
//                                disabledNations.remove(receiver.getNation_id());
//                                return;
//                            }
//                        }
//                        AlertUtil.error("Grant is still eligable",grant.getType() + " | " + grant.getNote() + " | " + grant.getAmount() + " | " + grant.getTitle());
//                    }
//                });
//
//            } else {
//                if (status != OffshoreInstance.TransferStatus.OTHER) {
//                    disabledNations.remove(receiver.getNation_id());
//                }
//                response.append(status + ": " + result.getValue());
//            }
//
//
//            return response.toString();
//        } catch (Throwable e) {
//            e.printStackTrace();
//            throw e;
//        }
//    }
}