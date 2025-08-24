package link.locutus.discord.web.commands.options;

import com.google.common.base.Predicates;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.ICommand;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.command.WebOption;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.ReportManager;
import link.locutus.discord.db.conflict.Conflict;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.grant.AGrantTemplate;
import link.locutus.discord.db.entities.grant.GrantTemplateManager;
import link.locutus.discord.db.entities.menu.AppMenu;
import link.locutus.discord.db.entities.menu.MenuState;
import link.locutus.discord.db.entities.newsletter.Newsletter;
import link.locutus.discord.db.entities.newsletter.NewsletterManager;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.gpt.pw.PWGPTHandler;
import link.locutus.discord.pnw.*;
import link.locutus.discord.util.PW;
import link.locutus.discord.web.commands.binding.value_types.WebOptions;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class WebOptionBindings extends BindingHelper {
//Parser
    @Binding(types = Parser.class)
    public WebOption getParser() {
        List<String> options = new ArrayList<>();
        ValueStore<Object> store = Locutus.cmd().getV2().getStore();
        for (Parser parser : store.getParsers().values()) {
            if (!parser.isConsumer(store)) continue;
            options.add(parser.getKey().toSimpleString());
        }

        return new WebOption(Parser.class).setOptions(options);
    }
//Font
    @Binding(types = Font.class)
    public WebOption getFont() {
        String[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        return new WebOption(Font.class).setOptions(fonts);
    }
//Guild
    @Binding(types = Guild.class)
    public WebOption getGuild() {
        return new WebOption(Guild.class).setQueryMap((guild, user, nation) -> {
            WebOptions data = new WebOptions(false).withIcon().withText().withSubtext();
            for (Guild g : Locutus.imp().getDiscordApi().getCachedGuilds()) {
                data.addWithIcon(g.getId(), g.getName(), g.getDescription(), g.getIconUrl());
            }
            return data;
        });
    }

    @Binding(types = {AppMenu.class})
    public WebOption appMenu(String input) {
        return new WebOption(AppMenu.class).setRequiresGuild().setQueryMap((guild, user, nation) -> {
            WebOptions data = new WebOptions(false).withText().withSubtext();
            for (AppMenu menu : guild.getMenuManager().getAppMenus().values()) {
                data.addSubtext(menu.title, menu.description.split("\n")[0]);
            }
            return data;
        });
    }

    @Binding(types = {MenuState.class})
    public WebOption MenuState(String input) {
        return new WebOption(MenuState.class).setOptions(Arrays.stream(MenuState.values()).map(MenuState::name).toList());
    }

//Category
    @Binding(types = Category.class)
    public WebOption getCategory() {
        return new WebOption(Category.class).setRequiresGuild().setQueryMap((db, user, nation) -> {
            WebOptions data = new WebOptions(false).withText();
            for (Category c : db.getGuild().getCategories()) {
                data.add(c.getId(), c.getName());
            }
            return data;
        });
    }
//Role
    @Binding(types = Role.class)
    public WebOption getRole() {
        return new WebOption(Role.class).setRequiresGuild().setQueryMap((db, user, nation) -> {
            WebOptions data = new WebOptions(false).withText().withColor();
            for (Role r : db.getGuild().getRoles()) {
                data.addWithColor(r.getId(), r.getName(), String.format("#%06X", (0xFFFFFF & r.getColorRaw())));
            }
            return data;
        });
    }
//TextChannel
    @Binding(types = TextChannel.class)
    public WebOption getTextChannel() {
        return new WebOption(TextChannel.class).setRequiresGuild().setQueryMap((db, user, nation) -> {
            WebOptions data = new WebOptions(false).withText().withSubtext();
            Member member = null;
            if (user != null) {
                member = db.getGuild().getMember(user);
            }
            if (member == null) member = db.getGuild().getSelfMember();
            for (TextChannel c : db.getGuild().getTextChannels()) {
                if (!c.canTalk(member)) continue;
                Category category = c.getParentCategory();
                data.add(c.getId(), c.getName(), category == null ? null : category.getName());
            }
            return data;
        });
    }
//ICategorizableChannel
    @Binding(types = ICategorizableChannel.class)
    public WebOption getICategorizableChannel() {
        return getTextChannel();
    }
//Member
    @Binding(types = Member.class)
    public WebOption getMember() {
        return new WebOption(Member.class).setRequiresGuild().setQueryMap((db, user, nation) -> {
            WebOptions data = new WebOptions(false).withText();
            for (Member m : db.getGuild().getMembers()) {
                data.add(m.getId(), m.getEffectiveName());
            }
            return data;
        });
    }
//CommandCallable
    @Binding(types = ICommand.class)
    public WebOption getCommandCallable() {
        List<ParametricCallable> options = new ArrayList<>(Locutus.imp().getCommandManager().getV2().getCommands().getParametricCallables(Predicates.alwaysTrue()));
        return new WebOption(ICommand.class).setOptions(options.stream().map(CommandCallable::getFullPath).toList());
    }

//MessageChannel - just return textChannel()
    @Binding(types = MessageChannel.class)
    public WebOption getMessageChannel() {
        return getTextChannel();
    }
//User
    @Binding(types = User.class)
    public WebOption getUser() {
        return new WebOption(User.class).setQueryMap((guild, user, nation) -> {
            WebOptions data = new WebOptions(false).withText();
            for (User u : Locutus.imp().getDiscordApi().getUsers()) {
                data.add(u.getId(), u.getName());
            }
            return data;
        });
    }
//TaxBracket
    @Binding(types = TaxBracket.class)
    public WebOption getTaxBracket() {
        return new WebOption(TaxBracket.class).setRequiresGuild().setQueryMap((db, user, nation) -> {
            if (!db.isValidAlliance()) throw new IllegalArgumentException("No alliance is registered. See " + CM.settings_default.registerAlliance.cmd.toSlashMention());
            Map<Integer, TaxBracket> brackets = db.getAllianceList().getTaxBrackets(TimeUnit.MINUTES.toMillis(120));
            WebOptions data = new WebOptions(true).withText().withSubtext();
            for (Map.Entry<Integer, TaxBracket> entry : brackets.entrySet()) {
                TaxBracket bracket = entry.getValue();
                data.add(entry.getKey(), bracket.getName(), bracket.getSubText());
            }
            return data;
        });
    }
//Project - return list Projects.values -> name()
    @Binding(types = Project.class)
    public WebOption getProject() {
        List<String> options = Arrays.stream(Projects.values).map(Project::name).toList();
        return new WebOption(Project.class).setOptions(options);
    }
//Building - return Buildings.values -> name()
    @Binding(types = Building.class)
    public WebOption getBuilding() {
        List<String> options = Arrays.stream(Buildings.values()).map(Building::name).toList();
        return new WebOption(Building.class).setOptions(options);
    }

//DBLoan - locutus -> loan manager -> getloans
    @Binding(types = DBLoan.class)
    public WebOption getDBLoan() {
        return new WebOption(DBLoan.class).setQueryMap((db, user, nation) -> {
            LoanManager loanUtil = Locutus.imp().getNationDB().getLoanManager();
            WebOptions data = new WebOptions(true).withText();
            for (DBLoan loan : loanUtil.getLoans()) {
                data.add(loan.loanId, loan.getLineString(true, true));
            }
            return data;
        });
    }
//Report - locutus - report manager -> get reports
    @Binding(types = ReportManager.Report.class)
    public WebOption getReport() {
        return new WebOption(ReportManager.Report.class).setQueryMap((db, user, nation) -> {
            WebOptions data = new WebOptions(true).withText();
            ReportManager reportManager = Locutus.imp().getNationDB().getReportManager();
            for (ReportManager.Report report : reportManager.loadReports()) {
                data.add(report.reportId, report.getTitle());
            }
            return data;
        });
    }
//Conflict - locutus -> conflict manager -> conflicts
    @Binding(types = Conflict.class)
    public WebOption getConflict() {
        return new WebOption(Conflict.class).setQueryMap((db, user, nation) -> {
            WebOptions data = new WebOptions(true).withText();
            for (Conflict conflict : Locutus.imp().getWarDb().getConflicts().getConflictMap().values()) {
                data.add(conflict.getId(), conflict.getName());
            }
            return data;
        });
    }
//GuildSetting
    @Binding(types = GuildSetting.class)
    public WebOption getGuildSetting() {
        return new WebOption(GuildSetting.class).setRequiresGuild()
                .setOptions((Arrays.stream(GuildKey.values()).map(GuildSetting::name).toList()));
    }
//EmbeddingSource
    @Binding(types = EmbeddingSource.class)
    public WebOption getEmbeddingSource() {
        return new WebOption(EmbeddingSource.class).setRequiresGuild().setQueryMap((db, user, nation) -> {
            PWGPTHandler gpt = Locutus.cmd().getV2().getPwgptHandler();
            if (gpt == null) {
                return new WebOptions(true);
            }
            WebOptions data = new WebOptions(true).withText();
            Set<EmbeddingSource> sources = gpt.getSources(db.getGuild(), true);
            for (EmbeddingSource source : sources) {
                data.add(source.source_id, source.source_name);
            }
            return data;
        });
    }
//DBAlliancePosition
    @Binding(types = DBAlliancePosition.class)
    public WebOption getDBAlliancePosition() {
        return new WebOption(DBAlliancePosition.class).setRequiresGuild().setQueryMap((db, user, nation) -> {
            AllianceList aaList = db.getAllianceList();
            WebOptions data = new WebOptions(true).withText();
            for (DBAlliancePosition position : aaList.getPositions()) {
                data.add(position.getId(), aaList.size() > 1 ? position.getQualifiedName() : position.getName());
            }
            return data;
        });
    }
//SheetTemplate
    @Binding(types = SheetTemplate.class)
    public WebOption getSheetTemplate() {
        return new WebOption(SheetTemplate.class).setRequiresGuild().setQueryMap((db, user, nation) -> {
            List<String> errors = new ArrayList<>();
            Map<String, SheetTemplate> templates = db.getSheetManager().getSheetTemplates(errors);
            WebOptions data = new WebOptions(false).withText().withSubtext();
            for (Map.Entry<String, SheetTemplate> entry : templates.entrySet()) {
                data.add(entry.getKey(),
                        PlaceholdersMap.getClassName(entry.getValue().getType()) + ":" + entry.getKey(),
                        entry.getValue().getColumns().toString());
            }
            return data;
        });
    }
//CustomSheet
    @Binding(types = CustomSheet.class)
    public WebOption getCustomSheet() {
        return new WebOption(CustomSheet.class).setRequiresGuild().setQueryMap((db, user, nation) -> {
            WebOptions data = new WebOptions(false);
            Map<String, String> sheets = db.getSheetManager().getCustomSheets();
            for (Map.Entry<String, String> entry : sheets.entrySet()) {
                data.add(entry.getKey());
            }
            return data;
        });
    }
//SelectionAlias
    @Binding(types = SelectionAlias.class)
    public WebOption getSelectionAlias() {
        return new WebOption(SelectionAlias.class).setRequiresGuild().setQueryMap((db, user, nation) -> {
            Map<Class, Map<String, SelectionAlias>> aliases = db.getSheetManager().getSelectionAliases();
            Set<SelectionAlias> allAliases = new ObjectLinkedOpenHashSet<>();
            for (Map<String, SelectionAlias> map : aliases.values()) {
                allAliases.addAll(map.values());
            }

            WebOptions data = new WebOptions(false).withText().withSubtext();
            for (SelectionAlias alias : allAliases) {
                data.add(alias.getName(), PlaceholdersMap.getClassName(alias.getType()) + ":" + alias.getName(), alias.getSelection());
            }
            return data;
        });
    }
//DBBounty
    @Binding(types = DBBounty.class)
    public WebOption getDBBounty() {
        return new WebOption(DBBounty.class).setQueryMap((guild, user, nation) -> {
            WebOptions data = new WebOptions(true).withText();
            for (DBBounty bounty : Locutus.imp().getWarDb().getBounties()) {
                data.add(bounty.getId(), bounty.toLineString());
            }
            return data;
        });
    }
    //NationOrAllianceOrGuild -> set components to nation, alliance and guild
    @Binding(types = NationOrAllianceOrGuild.class)
    public WebOption getNationOrAllianceOrGuild() {
        return new WebOption(DBNation.class).setCompositeTypes("DBNation", "DBAlliance", "GuildDB");
    }
    @Binding(types = GuildOrAlliance.class)
    public WebOption getGuildOrAlliance() {
        return new WebOption(GuildOrAlliance.class).setCompositeTypes("GuildDB", "DBAlliance");
    }
    //NationOrAlliance
    @Binding(types = NationOrAlliance.class)
    public WebOption getNationOrAlliance() {
        return new WebOption(DBNation.class).setCompositeTypes("DBNation", "DBAlliance");
    }
// NationOrAllianceOrGuildOrTaxid
    @Binding(types = NationOrAllianceOrGuildOrTaxid.class)
    public WebOption getNationOrAllianceOrGuildOrTaxid() {
        return new WebOption(DBNation.class).setCompositeTypes("DBNation", "DBAlliance", "GuildDB", "TaxBracket");
    }
    //Treaty
    @Binding(types = Treaty.class)
    public WebOption getTreaty() {
        return new WebOption(Treaty.class).setQueryMap((db, user, nation) -> {
            WebOptions data = new WebOptions(true).withText().withSubtext();
            for (Treaty treaty : Locutus.imp().getNationDB().getTreaties()) {
                data.add(treaty.getId(), treaty.toLineString(), treaty.getTurnsRemaining() + "");
            }
            return data;
        });
    }
//DBBan
    @Binding(types = DBBan.class)
    public WebOption getDBBan() {
        return new WebOption(DBBan.class).setQueryMap((db, user, nation) -> {
            WebOptions data = new WebOptions(true).withText().withSubtext();
            Map<Integer, DBBan> bans = Locutus.imp().getNationDB().getBansByNation();
            for (Map.Entry<Integer, DBBan> entry : bans.entrySet()) {
                DBBan ban = entry.getValue();
                data.add(entry.getKey(), PW.getName(ban.nation_id, false), ban.reason);
            }
            return data;
        });
    }
//DBTreasure
    @Binding(types = DBTreasure.class)
    public WebOption getDBTreasure() {
        List<String> names = Locutus.imp().getNationDB().getTreasuresByName().keySet().stream().toList();
        return new WebOption(DBTreasure.class).setOptions(names);
    }
//AGrantTemplate
    @Binding(types = AGrantTemplate.class)
    public WebOption getAGrantTemplate() {
        return new WebOption(AGrantTemplate.class).setRequiresGuild().setQueryMap((db, user, nation) -> {
            WebOptions data = new WebOptions(false).withText();
            GrantTemplateManager manager = db.getGrantTemplateManager();
            for (AGrantTemplate template : manager.getTemplates()) {
                data.add(template.getName(), template.getType().name() + ":" + template.getName());
            }
            return data;
        });
    }
//Newsletter
    @Binding(types = Newsletter.class)
    public WebOption getNewsletter() {
        return new WebOption(Newsletter.class).setRequiresGuild().setQueryMap((db, user, nation) -> {
            WebOptions data = new WebOptions(true).withText();
            NewsletterManager manager = db.getNewsletterManager();
            if (manager != null) {
                for (Map.Entry<Integer, Newsletter> entry : manager.getNewsletters().entrySet()) {
                    Newsletter newsletter = entry.getValue();
                    data.add(entry.getKey(), newsletter.getName());
                }
            }
            return data;
        });
    }
//DBNation
    @Binding(types = DBNation.class)
    public WebOption getDBNation() {
        return new WebOption(DBNation.class).setQueryMap((db, user, nation) -> {
            WebOptions data = new WebOptions(true).withText();
            for (DBNation n : Locutus.imp().getNationDB().getAllNations()) {
                data.add(n.getId(), n.getName());
            }
            return data;
        });
    }
//DBAlliance - prefix with AA:<id>
    @Binding(types = DBAlliance.class)
    public WebOption getDBAlliance() {
        return new WebOption(DBAlliance.class).setQueryMap((db, user, nation) -> {
            WebOptions data = new WebOptions(false).withText();
            for (DBAlliance aa : Locutus.imp().getNationDB().getAlliances()) {
                data.add("AA:" + aa.getId(), aa.getName());
            }
            return data;
        });
    }
//GuildDB
    @Binding(types = GuildDB.class)
    public WebOption getGuildDB() {
        return new WebOption(GuildDB.class).setRequiresUser().setQueryMap((db, user, nation) -> {
            WebOptions data = new WebOptions(false).withText();
            for (Guild guild : user.getMutualGuilds()) {
                data.add(guild.getId(), guild.getName());
            }
            return data;
        });
    }
//    AllianceDepositLimit
//    NationDepositLimit
// WikiCategory
//unused
//Class
// IAttack
//DBWar
//DBTrade
//UserWrapper
//Transaction2
//TaxDeposit

//DBCity -> fetch via api, or accept int

// compound
//Map<ResourceType, Double>[AllianceDepositLimit]
//Map<ResourceType, Double>[NationDepositLimit]
//Newsletter[ReportPerms]
//String[GuildCoalition]
//Set<String>[WikiCategory]
}
