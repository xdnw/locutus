package link.locutus.discord.web.commands.options;

import com.google.gson.JsonArray;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.HtmlOptions;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.command.WebOption;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.util.scheduler.TriFunction;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
            List<Map<String, String>> data = new ArrayList<>();
            for (Guild g : Locutus.imp().getDiscordApi().getGuilds()) {
                data.add(Map.of(
                        "icon", g.getIconUrl(),
                        "key", g.getId(),
                        "text", g.getName(),
                        "subtext", g.getDescription()
                ));
            }
            return data;
        });
    }
//Category
    @Binding(types = Category.class)
    public WebOption getCategory() {
        return new WebOption(Category.class).setRequiresGuild().setQueryMap((db, user, nation) -> {
            List<Map<String, String>> data = new ArrayList<>();
            for (Category c : db.getGuild().getCategories()) {
                data.add(Map.of(
                        "key", c.getId(),
                        "text", c.getName()
                ));
            }
            return data;
        });
    }
//Role
    @Binding(types = Role.class)
    public WebOption getRole() {
        return new WebOption(Role.class).setRequiresGuild().setQueryMap((db, user, nation) -> {
            List<Map<String, String>> data = new ArrayList<>();
            for (Role r : db.getGuild().getRoles()) {
                data.add(Map.of(
                        "key", r.getId(),
                        "text", r.getName(),
                        "color", String.format("#%06X", (0xFFFFFF & r.getColorRaw()))
                ));
            }
            return data;
        });
    }
//TextChannel
    @Binding(types = TextChannel.class)
    public WebOption getTextChannel() {
        return new WebOption(TextChannel.class).setRequiresGuild().setQueryMap((db, user, nation) -> {
            List<Map<String, String>> data = new ArrayList<>();
            Member member = null;
            if (user != null) {
                member = db.getGuild().getMember(user);
            }
            if (member == null) member = db.getGuild().getSelfMember();
            for (TextChannel c : db.getGuild().getTextChannels()) {
                if (!c.canTalk(member)) continue;
                Category category = c.getParentCategory();
                if (category == null) {
                    data.add(Map.of(
                            "key", c.getId(),
                            "text", c.getName()
                    ));
                } else {
                    data.add(Map.of(
                            "key", c.getId(),
                            "text", c.getName(),
                            "subtext", category.getName()
                    ));

                }
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
            List<Map<String, String>> data = new ArrayList<>();
            for (Member m : db.getGuild().getMembers()) {
                data.add(Map.of(
                        "key", m.getId(),
                        "text", m.getEffectiveName()
                ));
            }
            return data;
        });
    }
//CommandCallable
    @Binding(types = CommandCallable.class)
    public WebOption getCommandCallable() {
        List<ParametricCallable> options = new ArrayList<>(Locutus.imp().getCommandManager().getV2().getCommands().getParametricCallables(f -> true));
        return new WebOption(CommandCallable.class).setOptions((List<String>) options.stream().map(f -> f.getFullPath()));
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
            List<Map<String, String>> data = new ArrayList<>();
            for (User u : Locutus.imp().getDiscordApi().getUsers()) {
                data.add(Map.of(
                        "key", u.getId(),
                        "text", u.getName()
                ));
            }
            return data;
        });
    }
//TaxBracket
    @Binding(types = TaxBracket.class)
    public WebOption getTaxBracket() {
        return new WebOption(TaxBracket.class).setRequiresGuild().setQueryMap((db, user, nation) -> {
            if (!db.isValidAlliance()) throw new IllegalArgumentException("No alliance is registered. See " + CM.settings_default.registerAlliance.cmd.toSlashMention());
            Map<Integer, TaxBracket> brackets = db.getAllianceList().getTaxBrackets(true);
            List<Map<String, String>> data = new ArrayList<>();
            for (Map.Entry<Integer, TaxBracket> entry : brackets.entrySet()) {
                TaxBracket bracket = entry.getValue();
                data.add(Map.of(
                        "key", String.valueOf(entry.getKey()),
                        "text", bracket.getName(),
                        "subtext", bracket.getSubText()
                ));
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
//Report - locutus - report manager -> get reports


//NationOrAllianceOrGuild -> same as guild()
//Conflict - locutus -> conflict manager -> conflicts





//ParametricCallable
//ICommand
//NationAttribute
//NationPlaceholder
//AGrantTemplate
//GuildOrAlliance
//Newsletter
//DBAlliancePosition
//GuildSetting
//EmbeddingSource
//GPTProvider
//SpreadSheet
//GoogleDoc
//SheetTemplate
//CustomSheet
//TransferSheet
//SelectionAlias
//Class
//DBBounty
//DBTrade
//UserWrapper
//Transaction2
//TaxDeposit

    //unused
//UUID
//Color
//Message
//DBNation
//DBAlliance
//Treaty
//CityBuild
//DBBan
//DBTreasure
//IAttack
//DBWar
//NationOrAlliance

//defer

//NationList -> Set<DBNation>
//NationFilter -> defer Set<DBNation>
//DepositTypeInfo -> DepositType
//NationAttributeDouble -> defer to Set<DBNation>
//NationOrAllianceOrGuildOrTaxid -> guild + taxbracket
//GuildDB - return same as guild()
//DBCity -> fetch via api, or accept int
//TaxRate
//CityRanges
//MMRInt
//MMRDouble
//MMRMatcher

//
// compound
//Map<ResourceType, Double>[AllianceDepositLimit]
//Map<ResourceType, Double>[NationDepositLimit]
//Newsletter[ReportPerms]
//String[GuildCoalition]
//ParametricCallable[NationAttributeCallable]
//Class[PlaceholderType]
//Set<String>[WikiCategory]
}
