package link.locutus.discord.web.commands.binding;

import com.google.gson.reflect.TypeToken;
import com.knuddels.jtokkit.api.ModelType;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.apiv3.enums.NationLootType;
import link.locutus.discord.commands.manager.v2.binding.FunctionProviderParser;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.bindings.MathOperation;
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.GuildCoalition;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.NationDepositLimit;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.NationPlaceholder;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.Report;
import link.locutus.discord.db.ReportManager;
import link.locutus.discord.db.ReportType;
import link.locutus.discord.db.conflict.Conflict;
import link.locutus.discord.db.conflict.ConflictManager;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.conflict.ConflictCategory;
import link.locutus.discord.db.entities.grant.AGrantTemplate;
import link.locutus.discord.db.entities.grant.GrantTemplateManager;
import link.locutus.discord.db.entities.grant.TemplateTypes;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.db.entities.metric.OrbisMetric;
import link.locutus.discord.db.entities.newsletter.Newsletter;
import link.locutus.discord.db.entities.newsletter.NewsletterManager;
import link.locutus.discord.db.entities.sheet.CustomSheetManager;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.gpt.ProviderType;
import link.locutus.discord.gpt.imps.embedding.EmbeddingType;
import link.locutus.discord.gpt.pw.PWGPTHandler;
import link.locutus.discord.pnw.*;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.discord.util.sheet.GoogleDoc;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.sheet.templates.TransferSheet;
import link.locutus.discord.util.task.ia.AuditType;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.HtmlInput;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.awt.*;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WebPWBindings extends WebBindingHelper {




    /*
    --------------------------------------------------------------------
     */
//Missing: Key{type=java.util.Set<link.locutus.discord.apiv1.enums.NationColor>, annotationTypes=[interface link.locutus.discord.web.commands.HtmlInput]}
    @HtmlInput
    @Binding(types = {Set.class, NationColor.class}, multiple = true)
    public String NationColorSet(@Default ParameterData param) {
        return multipleSelect(param, Arrays.asList(NationColor.values()), s -> new KeyValue<>(s.name(), s.name()), true);
    }

    @HtmlInput
    @Binding(types = {Set.class, ResourceType.class}, multiple = true)
    public String ResourceTypeSet(@Default ParameterData param) {
        return multipleSelect(param, Arrays.asList(ResourceType.values()), s -> new KeyValue<>(s.name(), s.name()), true);
    }

    @HtmlInput
    @Binding(types = {Set.class, Research.class}, multiple = true)
    public String ResearchSet(@Default ParameterData param) {
        return multipleSelect(param, Arrays.asList(Research.values()), s -> new KeyValue<>(s.name(), s.name()), true);
    }

    @HtmlInput
    @Binding(types = {Set.class, SuccessType.class}, multiple = true)
    public String SuccessTypeSet(@Default ParameterData param) {
        return multipleSelect(param, Arrays.asList(SuccessType.values()), s -> new KeyValue<>(s.name(), s.name()), true);
    }
    @HtmlInput
    @Binding(types = {Set.class, TreatyType.class}, multiple = true)
    public String TreatyTypeSet(@Default ParameterData param) {
        return multipleSelect(param, Arrays.asList(TreatyType.values()), s -> new KeyValue<>(s.name(), s.name()), true);
    }
    @HtmlInput
    @Binding(types = {Set.class, GuildSetting.class, WildcardType.class}, multiple = true)
    public String GuildSettingSet(@Default ParameterData param) {
        return multipleSelect(param, Arrays.asList(GuildKey.values()), s -> new KeyValue<>(s.name(), s.name()), true);
    }
    @HtmlInput
    @Binding(types = {Set.class, AutoAuditType.class}, multiple = true)
    public String AutoAuditTypeSet(@Default ParameterData param) {
        return multipleSelect(param, Arrays.asList(AutoAuditType.values()), s -> new KeyValue<>(s.name(), s.name()), true);
    }


    @HtmlInput
    @Binding(types = CustomSheet.class)
    public String CustomSheet(@Me GuildDB db, CustomSheetManager manager, @Default ParameterData param) {
        Set<String> keys = manager.getCustomSheets().keySet();
        return multipleSelect(param, keys, s -> new KeyValue<>(s, s));
    }

    @HtmlInput
    @Binding(types = {Conflict.class})
    public String Conflict(ConflictManager manager, @Default ParameterData param) {
        Map<Integer, Conflict> conflicts = manager.getConflictMap();
        Set<Map.Entry<Integer, Conflict>> options = conflicts.entrySet();
        return multipleSelect(param, options, s -> new KeyValue<>(s.getValue().getName(), s.getKey() + ""));
    }

    @HtmlInput
    @Binding(types = DBLoan.class)
    public String DBLoan(@Me GuildDB db, LoanManager manager, @Default ParameterData param) {
        List<DBLoan> keys = manager.getLoansByGuildDB(db);
        return multipleSelect(param, keys, s -> new KeyValue<>(s.toString(), s.loanId + ""));
    }

    @HtmlInput
    @Binding(types = MessageTrigger.class)
    public String MessageTrigger(@Default ParameterData param) {
        MessageTrigger[] options = MessageTrigger.values();
        return multipleSelect(param, Arrays.asList(options), s -> new KeyValue<>(s.toString(), s.toString()));
    }

    @HtmlInput
    @Binding(types = ConflictCategory.class)
    public String ConflictCategory(@Default ParameterData param) {
        ConflictCategory[] options = ConflictCategory.values();
        return multipleSelect(param, Arrays.asList(options), s -> new KeyValue<>(s.toString(), s.toString()));
    }

    @HtmlInput
    @Binding(types = SelectionAlias.class)
    public String SelectionAlias(@Me GuildDB db, CustomSheetManager manager, @Default ParameterData param) {
        Set<String> keys = manager.getSelectionAliasNames();
        return multipleSelect(param, keys, s -> new KeyValue<>(s, s));
    }

    @HtmlInput
    @Binding(types = WarCostMode.class)
    public String WarCostMode(ParameterData param) {
        return multipleSelect(param, Arrays.asList(WarCostMode.values()), s -> new KeyValue<>(s.name(), s.name()));
    }

    @HtmlInput
    @Binding(types = WarCostStat.class)
    public String WarCostStat(ParameterData param) {
        return multipleSelect(param, Arrays.asList(WarCostStat.values()), s -> new KeyValue<>(s.name(), s.name()));
    }

    @HtmlInput
    @Binding(types = SheetTemplate.class)
    public String SheetTemplate(@Me GuildDB db, CustomSheetManager manager, @Default ParameterData param) {
        Set<String> options = manager.getSheetTemplates(new ArrayList<>()).keySet();
        return multipleSelect(param, options, s -> new KeyValue<>(s, s));
    }

    @HtmlInput
    @Binding(types = TemplateTypes.class)
    public String TemplateTypes(@Default ParameterData param) {
        TemplateTypes[] options = TemplateTypes.values();
        return multipleSelect(param, Arrays.asList(options), s -> new KeyValue<>(s.toString(), s.toString()));
    }

    @HtmlInput
    @PlaceholderType
    @Binding(types = {Class.class})
    public String clazz(ParameterData param) {
        Set<Class<?>> types = Locutus.cmd().getV2().getPlaceholders().getTypes();
        List<String> options = types.stream().map(PlaceholdersMap::getClassName).collect(Collectors.toList());
        return multipleSelect(param, options, f -> KeyValue.of(f, f));
    }

    @HtmlInput
    @Binding(types = DBLoan.class)
    public String loan(ParameterData param, LoanManager manager, @Me DBNation me, @Me User author, @Me GuildDB db) {
        List<DBLoan> options = manager.getLoansByGuildDB(db);
        return multipleSelect(param, options, f -> KeyValue.of(f.getLineString(true, false), f.loanId + ""));
    }

    @HtmlInput
    @Binding(types = AGrantTemplate.class)
    public String AGrantTemplate(ParameterData param, GrantTemplateManager manager, @Me DBNation me, @Me User author, @Me GuildDB db) {
        Set<AGrantTemplate> options = manager.getTemplates();
        return multipleSelect(param, options, f -> KeyValue.of(f.getType() + "/" + f.getName(), f.getName()));
    }

    @HtmlInput
    @Binding(types = EmbeddingSource.class)
    public String EmbeddingSource(ParameterData param, PWGPTHandler handler, @Me Guild guild) {
        return EmbeddingSources(param, handler, guild, false);
    }

    @HtmlInput
    @Binding(types = {Set.class, EmbeddingSource.class}, multiple = true)
    public String EmbeddingSources(ParameterData param, PWGPTHandler handler, @Me Guild guild) {
        return EmbeddingSources(param, handler, guild, true);
    }

    public String EmbeddingSources(ParameterData param, PWGPTHandler handler, @Me Guild guild, boolean multiple) {
        Set<EmbeddingSource> sources = handler.getSources(guild, true);
        return multipleSelect(param, sources, f -> KeyValue.of(f.getQualifiedName(), f.source_id + ""), multiple);
    }

    @HtmlInput
    @Binding(types = Building.class)
    public String Building(ParameterData param, @Me DBNation me, @Me User author, @Me GuildDB db) {
        return Buildings(param, me, author, db, false);
    }

    @HtmlInput
    @Binding(types = {Set.class, Building.class}, multiple = true)
    public String Buildings(ParameterData param, @Me DBNation me, @Me User author, @Me GuildDB db) {
        return Buildings(param, me, author, db, true);
    }

    public String Buildings(ParameterData param, @Me DBNation me, @Me User author, @Me GuildDB db, boolean multiple) {
        List<Building> options = Arrays.asList(Buildings.values());
        return multipleSelect(param, options, f -> KeyValue.of(f.name(), f.name()), multiple);
    }

    @HtmlInput
    @Binding(types = EnemyAlertChannelMode.class)
    public String EnemyAlertChannelMode(ParameterData param, @Me DBNation me, @Me User author, @Me GuildDB db) {
        return multipleSelect(param, Arrays.asList(EnemyAlertChannelMode.values()), f -> KeyValue.of(f.name(), f.name()));
    }

    @HtmlInput
    @ReportPerms
    @Binding(types = Report.class)
    public String reportsPerm(ParameterData param, ReportManager manager, @Me DBNation me, @Me User author, @Me GuildDB db) {
        return reportsImp(param, manager, me, author, db, true);
    }

    @HtmlInput
    @Binding(types = Report.class)
    public String reports(ParameterData param, ReportManager manager, @Me DBNation me, @Me User author, @Me GuildDB db) {
        return reportsImp(param, manager, me, author, db, false);
    }

    public String reportsImp(ParameterData param, ReportManager manager, @Me DBNation me, @Me User author, @Me GuildDB db, boolean checkPerms) {
        List<Report> options = manager.loadReports(null);
        if (checkPerms) {
            options.removeIf(f -> !f.hasPermission(me, author, db));
        }
        return multipleSelect(param, options, f -> KeyValue.of("#" + f.reportId + " " + f.getTitle(), f.reportId + ""));
    }

    @HtmlInput
    @Binding(types = Newsletter.class)
    public String newsletters(ParameterData param, NewsletterManager manager) {
        List<Newsletter> options = new ArrayList<>(manager.getNewsletters().values());
        return multipleSelect(param, options, f -> KeyValue.of(f.getName(), f.getId() + ""));
    }

    @HtmlInput
    @Binding(types = NationAttributeDouble.class)
    public String nationMetricDouble(ArgumentStack stack, ParameterData param) {
        NationPlaceholders placeholders = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
        List<NationAttributeDouble> options = placeholders.getMetricsDouble(stack.getStore());

        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.getName());
            String desc = obj.getDesc();
            subtext.add(desc);
        });
    }


    @HtmlInput
    @Binding(types=DBWar.class)
    public String war(ParameterData param) {
        String pattern = Pattern.quote(Settings.PNW_URL() + "/nation/war/timeline/war") + "=[0-9]+";
        return WebUtil.createInput(WebUtil.InputType.text, param, "pattern='" + pattern + "'");
    }

    /*
    --------------------------------------------------------------------
     */

    @HtmlInput
    @Binding(types= CityBuild.class)
    public String CityBuild(ParameterData param) {
        String hint = """
                {
                    "infra_needed": 1900,
                    "imp_total": 38,
                    "imp_coalpower": 0,
                    "imp_oilpower": 0,
                    "imp_windpower": 0,
                    "imp_nuclearpower": 1,
                    "imp_coalmine": 0,
                    "imp_oilwell": 10,
                    "imp_uramine": 0,
                    "imp_leadmine": 1,
                    "imp_ironmine": 0,
                    "imp_bauxitemine": 10,
                    "imp_farm": 0,
                    "imp_gasrefinery": 4,
                    "imp_aluminumrefinery": 5,
                    "imp_munitionsfactory": 0,
                    "imp_steelmill": 0,
                    "imp_policestation": 0,
                    "imp_hospital": 0,
                    "imp_recyclingcenter": 0,
                    "imp_subway": 0,
                    "imp_supermarket": 0,
                    "imp_bank": 0,
                    "imp_mall": 0,
                    "imp_stadium": 0,
                    "imp_barracks": 4,
                    "imp_factory": 0,
                    "imp_hangars": 0,
                    "imp_drydock": 3
                }""";
        String placeholder = "placeholder='" + hint + "'";
        return WebUtil.createInputWithClass("textarea", WebUtil.InputType.textarea, param, null, true, placeholder);
    }

    @HtmlInput
    @Binding(types=DBNation.class, examples = {"Borg", "<@664156861033086987>", "Danzek", "189573", "https://politicsandwar.com/nation/id=189573"})
    public String nation(ParameterData param) {
        Collection<DBNation> options = (Locutus.imp().getNationDB().getAllNations());
        options.removeIf(f -> f.getVm_turns() > 0 && (f.getPosition() <= 1 || f.getCities() < 7));
        options.removeIf(f -> f.active_m() > 10000 && f.getCities() < 3);
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.getNation());
            values.add(obj.getId());
            String sub;
            if (obj.getPosition() > 1) {
                sub = "c" + obj.getCities() + " score:" + MathMan.format(obj.getScore()) + "- " + obj.getAllianceName() + "- " + Rank.byId(obj.getPosition());
            } else {
                sub = "c" + obj.getCities() + " score:" + MathMan.format(obj.getScore());
            }
            subtext.add(sub);
        });
    }

    @HtmlInput
    @Binding(types= NationOrAlliance.class)
    public String nationOrAlliance(ParameterData param) {
        List<DBNation> nations = new ArrayList<>(Locutus.imp().getNationDB().getAllNations());
        nations.removeIf(f -> f.getVm_turns() > 0 && (f.getPosition() <= 1 || f.getCities() < 7));
        nations.removeIf(f -> f.active_m() > 10000 && f.getCities() < 3);

        Set<DBAlliance> alliances = Locutus.imp().getNationDB().getAlliances();

        List<Map.Entry<String, String>> options = new ArrayList<>(alliances.size() + nations.size());
        for (DBNation nation : nations) {
            options.add(new KeyValue<>(nation.getName(), nation.getNation_id() + ""));
        }
        for (DBAlliance alliance : alliances) {
            options.add(new KeyValue<>("AA:" + alliance.getName(), "AA:" + alliance.getId()));
        }
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.getKey());
            values.add(obj.getValue());
        });
    }

    @HtmlInput
    @Binding(types= NationOrAllianceOrGuild.class)
    public String nationOrAllianceOrGuild(@Me User user, @Me GuildDB db, ParameterData param) {
        return nationOrAllianceOrGuildOrTaxid(user, db, param, false);
    }

    @HtmlInput
    @Binding(types= GuildOrAlliance.class)
    public String guildOrAlliance(@Me User user, @Me GuildDB db, ParameterData param) {
        Set<DBAlliance> alliances = Locutus.imp().getNationDB().getAlliances();
        List<Guild> guilds = user.getMutualGuilds();
        List<GuildOrAlliance> options = new ArrayList<>(alliances.size() + guilds.size());
        for (Guild guild : guilds) {
            options.add(Locutus.imp().getGuildDB(guild));
        }
        options.addAll(alliances);
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            if (obj.isAlliance()) {
                names.add(obj.getName());
                values.add("aa:" + obj.getAlliance_id());
                subtext.add("alliance");
            } else if (obj.isGuild()) {
                names.add(DiscordWebBindings.formatGuildName(obj.asGuild().getGuild()));
                values.add("guild:" + obj.getIdLong());
                subtext.add("guild");
            }
        });
    }

    @HtmlInput
    @Binding(types= NationOrAllianceOrGuildOrTaxid.class)
    public String nationOrAllianceOrGuildOrTaxid(@Me User user, @Me GuildDB db, ParameterData param) {
        return nationOrAllianceOrGuildOrTaxid(user, db, param, true);
    }

    public String nationOrAllianceOrGuildOrTaxid(@Me User user, @Me GuildDB db, ParameterData param, boolean includeBrackets) {
        List<DBNation> nations = new ArrayList<>(Locutus.imp().getNationDB().getAllNations());
        nations.removeIf(f -> f.getVm_turns() > 0 && (f.getPosition() <= 1 || f.getCities() < 7));
        nations.removeIf(f -> f.active_m() > 10000 && f.getCities() < 3);

        Set<DBAlliance> alliances = Locutus.imp().getNationDB().getAlliances();

        List<Guild> guilds = user.getMutualGuilds();

        AllianceList aaList = includeBrackets && db == null ? null : db.getAllianceList();
        List<NationOrAllianceOrGuildOrTaxid> options = new ArrayList<>(alliances.size() + nations.size() + guilds.size());
        for (Guild guild : guilds) {
            options.add(Locutus.imp().getGuildDB(guild));
        }
        options.addAll(alliances);

        for (DBNation nation : nations) {
            options.add(nation);
        }
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            if (obj.isAlliance()) {
                names.add(obj.getName());
                values.add("aa:" + obj.getAlliance_id());
                subtext.add("alliance");
            } else if (obj.isNation()) {
                names.add(obj.getName());
                values.add(obj.getId());
                subtext.add("nation- " + obj.asNation().getAllianceName());
            } else if (obj.isGuild()) {
                names.add(DiscordWebBindings.formatGuildName(obj.asGuild().getGuild()));
                values.add("guild:" + obj.getIdLong());
                subtext.add("guild");
            } else if (obj.isTaxid()) {
                TaxBracket bracket = obj.asBracket();
                int aaId = bracket.getAlliance_id();
                names.add((aaId > 0 ? DBAlliance.getOrCreate(aaId).getName() + "- " : "") + bracket.getName() + ": " + bracket.moneyRate + "/" + bracket.rssRate);
                subtext.add("#" + bracket.taxId + " (" + bracket.getNations().size() + " nations)");
                values.add("tax_id=" + bracket.taxId);
            }
        });
    }


    @HtmlInput
    @Binding(types= TaxRate.class)
    public String taxRate(ParameterData param) {
        String pattern = "[0-9]{1,2}/[0-9]{1,2}";
        String placeholder = "100/100";
        return WebUtil.createInput(WebUtil.InputType.text, param, "pattern='" + pattern + "'", "placeholder='" + placeholder + "'");
    }

    @HtmlInput
    @Binding(types= CityRanges.class)
    public String CityRanges(ParameterData param) {
        String pattern = "(c[0-9]{1,2}-[0-9]{1,2},?)+";
        String placeholder = "c1-10";
        return WebUtil.createInput(WebUtil.InputType.text, param, "pattern='" + pattern + "'", "placeholder='" + placeholder + "'");
    }

    @HtmlInput
    @Binding(types= DBAlliance.class, examples = {"'Error 404'", "7413", "https://politicsandwar.com/alliance/id=7413"})
    public String alliance(ParameterData param) {
        return alliance(param, false);
    }

    public String alliance(ParameterData param, boolean multiple) {
        List<DBAlliance> options = new ArrayList<>(Locutus.imp().getNationDB().getAlliances());
        Collections.sort(options, (o1, o2) -> Double.compare(o2.getScore(), o1.getScore()));
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.getName());
            values.add("aa:" + obj.getId());
            subtext.add("members:" + obj.getNations(false, 0, true).size() + " score:" + MathMan.format(obj.getScore()));
        }, multiple);
    }

    @HtmlInput
    @Binding(types= NationList.class)
    public String nations(ArgumentStack stack, ParameterData param) {
        return nations(stack.getStore(), param);
    }

    public String nations(ValueStore valueStore, ParameterData param) {
        Collection<DBNation> options;
        Filter filter = param.getAnnotation(Filter.class);
        if (filter != null) {
            String filterStr = filter.value();

            MessageChannel channel = (MessageChannel) valueStore.getProvided(Key.of(MessageChannel.class, Me.class));
            User user = (User) valueStore.getProvided(Key.of(User.class, Me.class));
            DBNation me = (DBNation) valueStore.getProvided(Key.of(DBNation.class, Me.class));
            Guild guild = (Guild) valueStore.getProvided(Key.of(Guild.class, Me.class));
            GuildDB db = (GuildDB) valueStore.getProvided(Key.of(GuildDB.class, Me.class));

            filterStr = filterStr.replace("{alliance_id}", "AA:" + me.getAlliance_id());
            if (db != null) {
                Set<Integer> aaIds = db.getAllianceIds();
                if (!aaIds.isEmpty()) {
                    filterStr = filterStr.replace("{guild_alliance_id}", "AA:" + StringMan.join(aaIds, ",AA:"));
                }
            }
            filterStr = Locutus.cmd().getV2().getNationPlaceholders().format2(guild, me, user, filterStr, me, true);
            options = Locutus.cmd().getV2().getNationPlaceholders().parseSet(valueStore, filterStr);
        } else {
            options = new ArrayList<>(Locutus.imp().getNationDB().getAllNations());
            options.removeIf(f -> f.getVm_turns() > 0 && (f.getPosition() <= 1 || f.getCities() < 7));
            options.removeIf(f -> f.active_m() > 10000 && f.getCities() < 3);
        }

        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.getNation());
            values.add(obj.getId());
            String sub;
            if (obj.getPosition() > 1) {
                sub = "c" + obj.getCities() + " score:" + MathMan.format(obj.getScore()) + "- " + obj.getAllianceName() + "- " + Rank.byId(obj.getPosition());
            } else {
                sub = "c" + obj.getCities() + " score:" + MathMan.format(obj.getScore());
            }
            subtext.add(sub);
        }, true);
    }

    public WebPWBindings() {
        {
            Key<String> key = Key.of(TypeToken.getParameterized(Set.class, Member.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    Guild guild = (Guild) valueStore.getProvided(Key.of(Guild.class, Me.class));
                    ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
                    List<Member> options = new ArrayList<>(guild.getMembers());

                    return multipleSelect(param, options, t -> new KeyValue<>(t.getEffectiveName(), t.getAsMention()), true);
                }));
            });
        }
        {
            Key<String> key = Key.of(TypeToken.getParameterized(Set.class, DBNation.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
                    return nations(valueStore, param);
                }));
            });
        }
        {
            Key<String> key = Key.of(TypeToken.getParameterized(Set.class, NationOrAlliance.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
                    return nationOrAlliance(param);
                }));
            });
        }
        {
            Key<String> key = Key.of(TypeToken.getParameterized(Set.class, NationOrAllianceOrGuild.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
                    User user = (User) valueStore.getProvided(Key.of(User.class, Me.class));
                    return nationOrAllianceOrGuild(user, null, param);
                }));
            });
        }
        {
            Key<String> key = Key.of(TypeToken.getParameterized(Set.class, NationOrAllianceOrGuildOrTaxid.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
                    User user = (User) valueStore.getProvided(Key.of(User.class, Me.class));
                    GuildDB db = (GuildDB) valueStore.getProvided(Key.of(GuildDB.class, Me.class));
                    return nationOrAllianceOrGuildOrTaxid(user, db, param, true);
                }));
            });
        }
        {
            Key<String> key = Key.of(TypeToken.getParameterized(Set.class, WarStatus.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
                    List<WarStatus> options = Arrays.asList(WarStatus.values());
                    return multipleSelect(param, options, t -> new KeyValue<>(t.name(), t.name()), true);
                }));
            });
        }
        {
            Key<String> key = Key.of(TypeToken.getParameterized(Set.class, WarType.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    return multipleSelectEmum(WarType.class, valueStore);
                }));
            });
        }
        {
            Key<String> key = Key.of(TypeToken.getParameterized(Set.class, AttackType.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    return multipleSelectEmum(AttackType.class, valueStore);
                }));
            });
        }
        {
            Key<String> key = Key.of(TypeToken.getParameterized(Set.class, AuditType.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    return multipleSelectEmum(AuditType.class, valueStore);
                }));
            });
        }
        {
            Key<String> key = Key.of(TypeToken.getParameterized(Set.class, Continent.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    return multipleSelectEmum(Continent.class, valueStore);
                }));
            });
        }
        {
            Key<String> key = Key.of(TypeToken.getParameterized(Set.class, Role.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
                    Guild guild = (Guild) valueStore.getProvided(Key.of(Guild.class, Me.class));
                    List<Role> options = guild.getRoles();
                    return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
                        String name = "@" + obj.getName();
                        names.add(name);

                        String sub = "";
                        if (obj.getColorRaw() != Role.DEFAULT_COLOR_RAW) {
                            Color color = obj.getColor();
                            String hex = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
                            sub += hex + "- ";
                        }
                        int members = guild.getMembersWithRoles(obj).size();
                        sub += members + " members";
                        subtext.add(sub);
                        values.add(obj.getAsMention());
                    }, true);
                }));
            });
        }
        {
            Key<String> key = Key.of(TypeToken.getParameterized(Set.class, AllianceMetric.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
                    List<AllianceMetric> options = Arrays.asList(AllianceMetric.values());

                    return multipleSelect(param, options, t -> new KeyValue<>(t.name(), t.name()), true);
                }));
            });
        }
        {
            Key<String> key = Key.of(TypeToken.getParameterized(Set.class, Project.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
                    List<Project> options = Arrays.asList(Projects.values);

                    return multipleSelect(param, options, t -> new KeyValue<>(t.name(), t.name()), true);
                }));
            });
        }
        {
            Key<String> key = Key.of(TypeToken.getParameterized(Set.class, NationAttributeDouble.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);

                    NationPlaceholders placeholders = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
                    List<NationAttributeDouble> options = placeholders.getMetricsDouble(valueStore);

                    return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
                        names.add(obj.getName());
                        String desc = obj.getDesc();
                        subtext.add(desc);
                    }, true);
                }));
            });
        }
        {
            Key<String> key = Key.of(TypeToken.getParameterized(Set.class, DBAlliance.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);

                    return alliance(param, true);
                }));
            });
        }
        {
            Key<String> key = Key.of(TypeToken.getParameterized(Set.class, ResourceType.class).getType(), HtmlInput.class);
            addBinding(rootStore -> {
                rootStore.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) store -> {
                    ParameterData param = (ParameterData) store.getProvided(ParameterData.class);
                    Set<ResourceType> options = Set.of(ResourceType.values());
                    return multipleSelect(param, options, rss -> new KeyValue<>(rss.getName(), rss.getName()), true);
                }));
            });
        }
        {
            Key<String> key = Key.of(TypeToken.getParameterized(Set.class, Operation.class).getType(), HtmlInput.class);
            addBinding(rootStore -> {
                rootStore.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) store -> {
                    ParameterData param = (ParameterData) store.getProvided(ParameterData.class);
                    List<Operation> options = Arrays.asList(Operation.values());

                    return multipleSelect(param, options, op -> new KeyValue<>(op.name(), op.name()), true);
                }));
            });
        }
        {
            Type type = TypeToken.getParameterized(Map.class, ResourceType.class, Double.class).getType();
            {
                {
                    Key<String> key = Key.of(type, HtmlInput.class);
                    addBinding(rootStore -> {
                        rootStore.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) this::getBankInput));
                    });
                    Key<String> key2 = Key.of(type, NationDepositLimit.class, HtmlInput.class);
                    addBinding(rootStore -> {
                        rootStore.addParser(key2, new FunctionProviderParser<>(key2, (Function<ValueStore, String>) this::getBankInput));
                    });
                    Key<String> key3 = Key.of(type, AllianceDepositLimit.class, HtmlInput.class);
                    addBinding(rootStore -> {
                        rootStore.addParser(key3, new FunctionProviderParser<>(key3, (Function<ValueStore, String>) this::getBankInput));
                    });
                }
            }
        }

        {
            Type type = TypeToken.getParameterized(Map.class, MilitaryUnit.class, Long.class).getType();
            {
                {
                    Key<String> key = Key.of(type, HtmlInput.class);
                    ArrayList<MilitaryUnit> types = new ArrayList<>(Arrays.asList(MilitaryUnit.values()));
                    types.removeIf(f -> f.getName() == null);

                    addBinding(rootStore -> {
                        rootStore.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) store -> getMapInput(store, types)));
                    });
                }
            }
        }
    }

    private <T> String getMapInput(ValueStore store, List<T> keys) {
        ParameterData param = (ParameterData) store.getProvided(ParameterData.class);
        GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class));

        StringBuilder response = new StringBuilder();
        for (T key : keys) {
            String name = param.getName() + "." + key.toString();
            String prefix = key.toString();
            String attributes = "";
            String width = "110px";
            response.append("<div class=\"input-group input-group-sm\"><div class=\"input-group-prepend\" ><div style='width:" + width + "' class=\"input-group-text\">" + prefix + "</div></div>");
            response.append("<input min=\"0\" type='number' class=\"form-control form-control-sm\" value=\"0\" name=\"" + name + "\" " + attributes + " required></div>");
        }

        return WebUtil.wrapLabel(param, null, response.toString(), WebUtil.InlineMode.BEFORE);
    }

    private String getBankInput(ValueStore store) {
        ParameterData param = (ParameterData) store.getProvided(ParameterData.class);
        GuildDB db = (GuildDB) store.getProvided(Key.of(GuildDB.class, Me.class));

        double[] available = null;
        for (Annotation annotation : param.getAnnotations()) {
            if (annotation.annotationType() == AllianceDepositLimit.class) {
                available = PW.normalize(db.getOffshore().getDeposits(db));
            }
            if (annotation.annotationType() == NationDepositLimit.class) {
                DBNation me = (DBNation) store.getProvided(Key.of(DBNation.class, Me.class));
                try {
                    available = PW.normalize(me.getNetDeposits(store, db, true));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if (available != null) {
            available = PW.normalize(available);
        }


        StringBuilder response = new StringBuilder();
        for (ResourceType rss : ResourceType.values) {
            String name = param.getName() + "." + rss.name();
            String prefix = rss.name();
            String attributes = "";
            if (available != null) {
                double max = available[rss.ordinal()];
                prefix += ": " + MathMan.format(max);
                attributes = "max='" + max + "'";
            }
            String width = available != null ? "180px" : "110px";
            response.append("<div class=\"input-group input-group-sm\"><div class=\"input-group-prepend\" ><div style='width:" + width + "' class=\"input-group-text\">" + prefix + "</div></div>");
            response.append("<input min=\"0\" type='number' class=\"form-control form-control-sm\" value=\"0\" name=\"" + name + "\" " + attributes + " required></div>");
        }

        return WebUtil.wrapLabel(param, null, response.toString(), WebUtil.InlineMode.BEFORE);
    }

    @HtmlInput
    @Binding(types= Coalition.class)
    public String coalition(ParameterData param) {
        List<Coalition> options = Arrays.asList(Coalition.values());
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.name());
            subtext.add(obj.getDescription());
        });
    }

    @HtmlInput
    @Binding(types=String.class)
    @GuildCoalition
    public String coalition(@Me GuildDB db, ParameterData param) {
        List<String> options = new ArrayList<>(db.getCoalitionNames());
        for (Coalition value : Coalition.values()) {
            options.add(value.name().toLowerCase());
        }
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj);
            subtext.add(db.getCoalition(obj).size() + " entries");
        });
    }

    @HtmlInput
    @Binding(types=ResourceType.class)
    public String resource(ParameterData param) {
        return multipleSelect(param, ResourceType.valuesList, rss -> new KeyValue<>(rss.getName(), rss.getName()));
    }

    @HtmlInput
    @Binding(types=Research.class)
    public String Research(ParameterData param) {
        return multipleSelect(param, Arrays.asList(Research.values), rss -> new KeyValue<>(rss.getName(), rss.getName()));
    }

    @HtmlInput
    @Binding(types= DepositType.class)
    public String DepositType(ParameterData param) {
        return multipleSelect(param, Arrays.asList(DepositType.values()), type -> new KeyValue<>(type.name(), type.name()));
    }

    @HtmlInput
    @Binding(types= DepositTypeInfo.class)
    public String DepositTypeInfo(ParameterData param) {
        return DepositType(param);
    }

    @HtmlInput
    @Binding(types= WarStatus.class)
    public String WarStatus(ParameterData param) {
        return multipleSelect(param, Arrays.asList(WarStatus.values()), type -> new KeyValue<>(type.name(), type.name()));
    }

    @HtmlInput
    @Binding(types= WarType.class)
    public String WarType(ParameterData param) {
        return multipleSelect(param, Arrays.asList(WarType.values()), type -> new KeyValue<>(type.name(), type.name()));
    }

    @HtmlInput
    @Binding(types= EscrowMode.class)
    public String EscrowMode(ParameterData param) {
        return multipleSelect(param, Arrays.asList(EscrowMode.values()), type -> new KeyValue<>(type.name(), type.name()));
    }

    @HtmlInput
    @Binding(types= WarPolicy.class)
    public String WarPolicy(ParameterData param) {
        return multipleSelect(param, Arrays.asList(WarPolicy.values()), type -> new KeyValue<>(type.name(), type.name()));
    }

    @HtmlInput
    @Binding(types= BeigeReason.class)
    public String BeigeReason(ParameterData param) {
        return BeigeReasons(param, false);
    }

    @HtmlInput
    @Binding(types= {Set.class, BeigeReason.class}, multiple = true)
    public String BeigeReasons(ParameterData param) {
        return BeigeReasons(param, true);
    }
    public String BeigeReasons(ParameterData param, boolean multiple) {
        return multipleSelect(param, Arrays.asList(BeigeReason.values()), type -> new KeyValue<>(type.name() + " - " + type.getDescription(), type.name()), multiple);
    }

    @HtmlInput
    @Binding(types= OrbisMetric.class)
    public String OrbisMetric(ParameterData param) {
        return OrbisMetrics(param, false);
    }

    @HtmlInput
    @Binding(types= {Set.class, OrbisMetric.class}, multiple = true)
    public String OrbisMetrics(ParameterData param) {
        return OrbisMetrics(param, true);
    }
    public String OrbisMetrics(ParameterData param, boolean multiple) {
        return multipleSelect(param, Arrays.asList(OrbisMetric.values()), type -> new KeyValue<>(type.name(), type.name()), multiple);
    }

    @HtmlInput
    @Binding(types= GuildDB.AutoRoleOption.class)
    public String AutoRoleOption(ParameterData param) {
        return AutoRoleOptions(param, false);
    }

    @HtmlInput
    @Binding(types= {Set.class, GuildDB.AutoRoleOption.class}, multiple = true)
    public String AutoRoleOptions(ParameterData param) {
        return AutoRoleOptions(param, true);
    }
    public String AutoRoleOptions(ParameterData param, boolean multiple) {
        return multipleSelect(param, Arrays.asList(GuildDB.AutoRoleOption.values()), type -> new KeyValue<>(type.name() + " - " + type.getDescription(), type.name()), multiple);
    }

    @HtmlInput
    @Binding(types= Status.class)
    public String LoanStatus(ParameterData param) {
        return LoanStatuses(param, false);
    }

    @HtmlInput
    @Binding(types= {Set.class, Status.class}, multiple = true)
    public String LoanStatuses(ParameterData param) {
        return LoanStatuses(param, true);
    }
    public String LoanStatuses(ParameterData param, boolean multiple) {
        return multipleSelect(param, Arrays.asList(Status.values()), type -> new KeyValue<>(type.name(), type.name()), multiple);
    }

    @HtmlInput
    @Binding(types= AuditType.class)
    public String AuditType(ParameterData param) {
        return multipleSelect(param, Arrays.asList(AuditType.values()), type -> new KeyValue<>(type.name() + " | " + type.emoji + " | " + type.severity.name(), type.name()));
    }

    @HtmlInput
    @Binding(types= AttackType.class)
    public String AttackType(ParameterData param) {
        return multipleSelect(param, Arrays.asList(AttackType.values()), type -> new KeyValue<>(type.name(), type.name()));
    }

    @HtmlInput
    @Binding(types=Rank.class)
    public String rank(ParameterData param) {
        return multipleSelect(param, Arrays.asList(Rank.values()), rank -> new KeyValue<>(rank.name(), rank.name()));
    }

    @HtmlInput
    @Binding(types= NationLootType.class)
    public String lootType(ParameterData param) {
        return multipleSelect(param, Arrays.asList(NationLootType.values()), rank -> new KeyValue<>(rank.name(), rank.name()));
    }

    @HtmlInput
    @Binding(types=AlliancePermission.class)
    public String AlliancePermission(ParameterData param) {
        return AlliancePermissions(param, false);
    }

    @HtmlInput
    @Binding(types={Set.class, AlliancePermission.class}, multiple = true)
    public String AlliancePermissions(ParameterData param) {
        return AlliancePermissions(param, true);
    }

    public String AlliancePermissions(ParameterData param, boolean multiple) {
        return multipleSelect(param, Arrays.asList(AlliancePermission.values()), f -> new KeyValue<>(f.name(), f.name()), multiple);
    }

    @HtmlInput
    @Binding(types= ProviderType.class)
    public String ProviderType(ParameterData param) {
        return ProviderTypes(param, false);
    }

    @HtmlInput
    @Binding(types={Set.class, ProviderType.class}, multiple = true)
    public String ProviderTypes(ParameterData param) {
        return ProviderTypes(param, true);
    }

    public String ProviderTypes(ParameterData param, boolean multiple) {
        return multipleSelect(param, Arrays.asList(ProviderType.values()), f -> new KeyValue<>(f.name(), f.name()), multiple);
    }


    @HtmlInput
    @Binding(types=DBAlliancePosition.class)
    public String position(@Me GuildDB db, ParameterData param) {
        AllianceList alliances = db.getAllianceList();
        Set<DBAlliancePosition> positions = new HashSet<>(alliances.getPositions());
        positions.add(DBAlliancePosition.REMOVE);
        positions.add(DBAlliancePosition.APPLICANT);
        return multipleSelect(param, positions, rank -> new KeyValue<>(alliances.size() > 1 ? rank.getQualifiedName() : rank.getName(), rank.getInputName()));
    }

    @HtmlInput
    @Binding(types= Permission.class)
    public String permission(ParameterData param) {
        return multipleSelect(param, Arrays.asList(Permission.values()), f -> new KeyValue<>(f.name(), f.name()));
    }

    @HtmlInput
    @Binding(types= ModelType.class)
    public String ModelType(ParameterData param) {
        return multipleSelect(param, Arrays.asList(ModelType.values()), f -> new KeyValue<>(f.name(), f.name()));
    }


    @HtmlInput
    @Binding(types= ClearRolesEnum.class)
    public String ClearRolesEnum(ParameterData param) {
        return multipleSelect(param, Arrays.asList(ClearRolesEnum.values()), rank -> new KeyValue<>(rank.name(), rank.name()));
    }

    @HtmlInput
    @Binding(types= FlowType.class)
    public String FlowType(ParameterData param) {
        return multipleSelect(param, Arrays.asList(FlowType.values()), rank -> new KeyValue<>(rank.name(), rank.name()));
    }

    @HtmlInput
    @Binding(types = { GuildSetting.class, WildcardType.class })
    public String GuildSetting(@Me GuildDB db, @Me Guild guild, @Me User author, ParameterData param) {
        ArrayList<GuildSetting> options = new ArrayList<>(Arrays.asList(GuildKey.values()));
        options.removeIf(key -> {
            if (!key.allowed(db)) return true;
            if (!key.hasPermission(db, author, null)) return true;
            return false;
        });
        return multipleSelect(param, options, f -> new KeyValue<>(f.name(), f.name()));
    }

    @HtmlInput
    @Binding(types= OnlineStatus.class)
    public String onlineStatus(ParameterData param) {
        return multipleSelect(param, Arrays.asList(OnlineStatus.values()), arg -> new KeyValue<>(arg.name(), arg.name()));
    }

    @HtmlInput
    @Binding(types= Continent.class)
    public String Continent(ParameterData param) {
        return multipleSelect(param, Arrays.asList(Continent.values()), arg -> new KeyValue<>(arg.name(), arg.name()));
    }

    @HtmlInput
    @Binding(types= NationMeta.BeigeAlertMode.class)
    public String BeigeAlertMode(ParameterData param) {
        return multipleSelect(param, Arrays.asList(NationMeta.BeigeAlertMode.values()), arg -> new KeyValue<>(arg.name(), arg.name()));
    }

    @HtmlInput
    @Binding(types= NationMeta.BeigeAlertRequiredStatus.class)
    public String BeigeAlertRequiredStatus(ParameterData param) {
        return multipleSelect(param, Arrays.asList(NationMeta.BeigeAlertRequiredStatus.values()), arg -> new KeyValue<>(arg.name(), arg.name()));
    }

    @HtmlInput
    @Binding(types= MilitaryUnit.class)
    public String unit(ParameterData param) {
        return units(param, false);
    }

    @HtmlInput
    @Binding(types= {Set.class, MilitaryUnit.class}, multiple = true)
    public String units(ParameterData param) {
        return units(param, true);
    }
    public String units(ParameterData param, boolean multiple) {
        return multipleSelect(param, Arrays.asList(MilitaryUnit.values()), arg -> new KeyValue<>(arg.name(), arg.name()), multiple);
    }

    @HtmlInput
    @Binding(types= MathOperation.class)
    public String operation(ParameterData param) {
        return multipleSelect(param, Arrays.asList(MathOperation.values()), op -> new KeyValue<>(op.name(), op.name()));
    }

    @HtmlInput
    @Binding(types= GuildDB.AutoNickOption.class)
    public String AutoNickOption(ParameterData param) {
        return multipleSelect(param, Arrays.asList(GuildDB.AutoNickOption.values()), op -> new KeyValue<>(op.name(), op.name()));
    }

    @HtmlInput
    @Binding(types= Safety.class)
    public String spySafety(ParameterData param) {
        return multipleSelect(param, Arrays.asList(Safety.values()), arg -> new KeyValue<>(arg.name(), arg.name()));
    }

    @HtmlInput
    @Binding(types= TreatyType.class)
    public String TreatyType(ParameterData param) {
        return multipleSelect(param, Arrays.asList(TreatyType.values()), arg -> new KeyValue<>(arg.name(), arg.name()));
    }

    @HtmlInput
    @Binding(types= ReportType.class)
    public String ReportType(ParameterData param) {
        return multipleSelect(param, Arrays.asList(ReportType.values()), arg -> new KeyValue<>(arg.name(), arg.name()));
    }

    @HtmlInput
    @Binding(types= AllianceMetric.class)
    public String AllianceMetric(ParameterData param) {
        return multipleSelect(param, Arrays.asList(AllianceMetric.values()), arg -> new KeyValue<>(arg.name(), arg.name()));
    }

    @HtmlInput
    @Binding(types= Role.class)
    public String role(@Me Guild guild, ParameterData param) {
        List<Role> options = guild.getRoles();
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            String name = "@" + obj.getName();
            names.add(name);

            String sub = "";
            if (obj.getColorRaw() != Role.DEFAULT_COLOR_RAW) {
                Color color = obj.getColor();
                String hex = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
                sub += hex + "- ";
            }
            int members = guild.getMembersWithRoles(obj).size();
            sub += members + " members";
            subtext.add(sub);
            values.add(obj.getAsMention());
        });
    }

    @HtmlInput
    @Binding(types= Roles.class)
    public String role(@Me GuildDB db, @Me Guild guild, ParameterData param) {
        return roles(db, guild, param, false);
    }

    @HtmlInput
    @Binding(types= {Set.class, Roles.class}, multiple = true)
    public String roles(@Me GuildDB db, @Me Guild guild, ParameterData param) {
        return roles(db, guild, param, true);
    }

    public String roles(@Me GuildDB db, @Me Guild guild, ParameterData param, boolean multiple) {
        List<Roles> options = Arrays.asList(Roles.values);
        if (param.getAnnotation(RegisteredRole.class) != null) {
            options = new ArrayList<>(options);
            options.removeIf(f -> f.toRoles(db) == null);
        }
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            GuildSetting key = obj.getKey();
            if (key != null && db.getOrNull(key) == null) {
                return;
//                if (!sub.isEmpty()) sub += "  - ";
//                sub += " MISSING: `" + key + "`";
            }
            String name = obj.name();
            names.add(name);

            Map<Long, Role> roleMap = obj.toRoleMap(db);
            String sub = DiscordUtil.toRoleString(roleMap);
            subtext.add(sub);
        }, multiple);
    }

    @HtmlInput
    @Binding(types= Project.class)
    public String project(ParameterData param) {
        List<Project> options = Arrays.asList(Projects.values);
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.name());
            String sub;
            if (obj.getOutput() != null) {
                sub = obj.getOutput() + "- " + ResourceType.toString(obj.cost());
            } else {
                sub = ResourceType.toString(obj.cost());
            }
            subtext.add(sub);
        });
    }

    @HtmlInput
    @Binding(types= TaxBracket.class)
    public String bracket(@Me GuildDB db, ParameterData param) {
        return bracket(db, param, false);
    }

    public String bracket(@Me GuildDB db, ParameterData param, boolean multiple) {
        Map<Integer, TaxBracket> brackets = db.getAllianceList().getTaxBrackets(TimeUnit.MINUTES.toMillis(1));
        Collection<TaxBracket> options = brackets.values();
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            DBAlliance alliance = obj.getAlliance();
            names.add(alliance.getName() + "- " + obj.getName() + ": " + obj.moneyRate + "/" + obj.rssRate);
            subtext.add("#" + obj.taxId + " (" + obj.getNations().size() + " nations)");
            values.add("tax_id=" + obj.taxId);
        }, multiple);
    }

    @HtmlInput
    @Binding(types= NationPlaceholder.class)
    public String natPlaceholder(ParameterData param, ArgumentStack stack) {
        CommandManager2 v2 = Locutus.imp().getCommandManager().getV2();
        NationPlaceholders placeholders = v2.getNationPlaceholders();
        List<ParametricCallable> options = placeholders.getParametricCallables();
        options.removeIf(f -> {
            try {
                f.validatePermissions(stack.getStore(), stack.getPermissionHandler());
                return false;
            } catch (Throwable ignore) {
                return true;
            }
        });
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.getPrimaryCommandId());
            subtext.add(obj.simpleDesc());
        });
    }

    //Missing: Key{type=class link.locutus.discord.apiv1.enums.WarCostByDayMode, annotationTypes=[interface link.locutus.discord.web.commands.HtmlInput]}
    @HtmlInput
    @Binding(types= WarCostByDayMode.class)
    public String warCostByDayMode(ParameterData param) {
        return multipleSelect(param, Arrays.asList(WarCostByDayMode.values()), arg -> new KeyValue<>(arg.name(), arg.name()));
    }
    //Missing: Key{type=class link.locutus.discord.db.entities.MMRDouble, annotationTypes=[interface link.locutus.discord.web.commands.HtmlInput]}
    // pattern = 4 decimal numbers (or whole numbers) separated by /
    @HtmlInput
    @Binding(types= MMRDouble.class)
    public String mmrDouble(ParameterData param) {
        String pattern = "([0-9]+(\\.[0-9]{1,4})?)/([0-9]+(\\.[0-9]{1,4})?)/([0-9]+(\\.[0-9]{1,4})?)/([0-9]+(\\.[0-9]{1,4})?)";
        return WebUtil.createInput(WebUtil.InputType.text, param, "pattern='" + pattern + "'");
    }

    //Missing: Key{type=class link.locutus.discord.db.entities.MMRInt, annotationTypes=[interface link.locutus.discord.web.commands.HtmlInput]}
    // four whole numbers, no separator
    @HtmlInput
    @Binding(types= MMRInt.class)
    public String mmrInt(ParameterData param) {
        String pattern = "[0-9]{4}";
        return WebUtil.createInput(WebUtil.InputType.text, param, "pattern='" + pattern + "'");
    }

    //Missing: Key{type=class link.locutus.discord.db.guild.SheetKey, annotationTypes=[interface link.locutus.discord.web.commands.HtmlInput]}
    // enum
    @HtmlInput
    @Binding(types= SheetKey.class)
    public String sheetKey(ParameterData param) {
        return multipleSelect(param, Arrays.asList(SheetKey.values()), arg -> new KeyValue<>(arg.name(), arg.name()));
    }

    //Missing: Key{type=class link.locutus.discord.util.sheet.GoogleDoc, annotationTypes=[interface link.locutus.discord.web.commands.HtmlInput]}
    // https://docs.google.com/document/d/([a-zA-Z0-9-_]{30,})/.*
    // document:([a-zA-Z0-9-_]{30,})
    @HtmlInput
    @Binding(types= GoogleDoc.class)
    public String googleDoc(ParameterData param) {
        String pattern = "https://docs\\.google\\.com/document/d/([a-zA-Z0-9-_]{30,})/.*|document:([a-zA-Z0-9-_]{30,})";
        return WebUtil.createInput(WebUtil.InputType.text, param, "pattern='" + pattern + "'");
    }

    //Missing: Key{type=class link.locutus.discord.db.entities.MMRMatcher, annotationTypes=[interface link.locutus.discord.web.commands.HtmlInput]}
    // [0-9xX\.]{4}
    @HtmlInput
    @Binding(types= MMRMatcher.class)
    public String mmrMatcher(ParameterData param) {
        String pattern = "[0-9xX\\.]{4}";
        return WebUtil.createInput(WebUtil.InputType.text, param, "pattern='" + pattern + "'");
    }

    //Missing: Key{type=class link.locutus.discord.util.sheet.SpreadSheet, annotationTypes=[interface link.locutus.discord.web.commands.HtmlInput]}
    // https://docs.google.com/spreadsheets/d/([a-zA-Z0-9-_]{30,})/.*
    // sheet:([a-zA-Z0-9-_]{30,})(,[a-zA-Z0-9-_]+)?
    @HtmlInput
    @Binding(types= SpreadSheet.class)
    public String spreadSheet(ParameterData param) {
        String pattern = "https://docs\\.google\\.com/spreadsheets/d/([a-zA-Z0-9-_]{30,})/.*|sheet:([a-zA-Z0-9-_]{30,})(,[a-zA-Z0-9-_]+)?";
        return WebUtil.createInput(WebUtil.InputType.text, param, "pattern='" + pattern + "'");
    }

    //Missing: Key{type=class link.locutus.discord.util.sheet.templates.TransferSheet, annotationTypes=[interface link.locutus.discord.web.commands.HtmlInput]}
    // same as spreadsheet
    @HtmlInput
    @Binding(types= TransferSheet.class)
    public String transferSheet(ParameterData param) {
        return spreadSheet(param);
    }

    //Missing: Key{type=class link.locutus.discord.db.entities.DBCity, annotationTypes=[interface link.locutus.discord.web.commands.HtmlInput]}
    // https://politicsandwar.com/city/id=([0-9]+)
    @HtmlInput
    @Binding(types= DBCity.class)
    public String city(ParameterData param) {
        String pattern = "https://politicsandwar\\.com/city/id=([0-9]+)";
        return WebUtil.createInput(WebUtil.InputType.text, param, "pattern='" + pattern + "'");
    }

    //Missing: Key{type=class link.locutus.discord.db.entities.DBLoan, annotationTypes=[interface link.locutus.discord.commands.manager.v2.binding.annotation.GuildLoan, interface link.locutus.discord.web.commands.HtmlInput]}
    @HtmlInput
    @Binding(types= DBLoan.class)
    public String loan(ParameterData param, GuildDB db, LoanManager loanManager) {
        List<DBLoan> options = loanManager.getLoans();
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.getLineString(false, false));
            String sub = obj.getSenderQualifiedName() + " -> " + obj.getReceiverQualifiedName();
            subtext.add(sub);
            values.add(obj.loanId + "");
        });
    }

    @HtmlInput
    @Binding(types = {Set.class, DomesticPolicy.class}, multiple = true)
    public String domesticPolicy(ParameterData param) {
        return multipleSelect(param, Arrays.asList(DomesticPolicy.values()), arg -> new KeyValue<>(arg.name(), arg.name()), true);
    }
    @HtmlInput
    @Binding(types = {Set.class, GuildDB.class}, multiple = true)
    public String guildDB(ParameterData param, @Me User user) {
        List<Guild> options = user.getMutualGuilds();
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.getName());
            subtext.add(obj.getId());
            values.add(obj.getId());
        }, true);
    }
//    Missing: Key{type=java.util.Set<link.locutus.discord.db.conflict.Conflict>, annotationTypes=[interface link.locutus.discord.web.commands.HtmlInput]}
    @HtmlInput
    @Binding(types = {Set.class, Conflict.class}, multiple = true)
    public String conflict(ParameterData param, ConflictManager manager) {
        List<Conflict> options = new ArrayList<>(manager.getConflictMap().values());
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.getName());
            subtext.add(obj.getId() + " | " + obj.getSide(true).getName() + " vs " + obj.getSide(false).getName());
            values.add(obj.getId());
        }, true);
    }
//    Missing: Key{type=java.util.Set<link.locutus.discord.db.entities.DBTreasure>, annotationTypes=[interface link.locutus.discord.web.commands.HtmlInput]}
    @HtmlInput
    @Binding(types = {Set.class, DBTreasure.class}, multiple = true)
    public String treasure(ParameterData param) {
        Collection<DBTreasure> options = Locutus.imp().getNationDB().getTreasuresByName().values();
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.getName());
            List<String> sub = new ArrayList<>();
            if (obj.getColor() != null) {
                sub.add(obj.getColor().name());
            }
            DBNation nation = obj.getNation();
            if (nation != null) {
                sub.add(nation.getName());
            }
            if (obj.getContinent() != null) {
                sub.add(obj.getContinent().name());
            }
            sub.add(obj.getDaysRemaining() + "d");
            subtext.add(StringMan.join(sub, " | "));
            values.add(obj.getId());
        }, true);
    }
//    Missing: Key{type=java.util.Set<link.locutus.discord.db.entities.TaxBracket>, annotationTypes=[interface link.locutus.discord.web.commands.HtmlInput]}
    @HtmlInput
    @Binding(types = {Set.class, TaxBracket.class}, multiple = true)
    public String taxBracket(ParameterData param, @Me GuildDB db) {
        return bracket(db, param, true);
    }
//    Missing: Key{type=java.util.Set<link.locutus.discord.gpt.imps.embedding.EmbeddingType>, annotationTypes=[interface link.locutus.discord.web.commands.HtmlInput]}
    @HtmlInput
    @Binding(types = {Set.class, EmbeddingType.class}, multiple = true)
    public String embeddingType(ParameterData param) {
        return multipleSelect(param, Arrays.asList(EmbeddingType.values()), arg -> new KeyValue<>(arg.name(), arg.name()), true);
    }

}
