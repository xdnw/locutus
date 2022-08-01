package link.locutus.discord.web.commands.binding;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.apiv3.enums.NationLootType;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.FunctionProviderParser;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.AllianceDepositLimit;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Filter;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.GuildCoalition;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.NationDepositLimit;
import link.locutus.discord.commands.manager.v2.binding.annotation.RegisteredRole;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timediff;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.commands.manager.v2.binding.bindings.Operation;
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.NationPlaceholder;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.pnw.CityRanges;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.util.scheduler.QuadConsumer;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.HtmlInput;
import com.google.common.collect.BiMap;
import com.google.gson.JsonArray;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.ICategorizableChannel;
import net.dv8tion.jda.api.entities.IPositionableChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;

import java.awt.Color;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static link.locutus.discord.web.WebUtil.createInput;
import static link.locutus.discord.web.WebUtil.generateSearchableDropdown;
import static link.locutus.discord.web.WebUtil.wrapLabel;

public class WebPrimitiveBinding extends BindingHelper {

    @HtmlInput
    @Binding(types={int.class, Integer.class}, examples = {"3"})
    public static String Integer(ParameterData param) {
        return WebUtil.createInput(WebUtil.InputType.number, param, "step='1'");
    }

    @HtmlInput
    @Binding(types={double.class, Double.class}, examples = {"3.0"})
    public static String Double(ParameterData param) {
        return WebUtil.createInput(WebUtil.InputType.number, param);
    }

    @HtmlInput
    @Binding(types={long.class, Long.class}, examples = {"3.0"})
    public static String Long(ParameterData param) {
        return Integer(param);
    }

    @HtmlInput
    @Binding(examples = {"true", "false"}, types = {boolean.class, Boolean.class})
    public String Boolean(ParameterData param) {
        String def = param.getDefaultValueString();
        return WebUtil.createInput(WebUtil.InputType.checkbox, param, (def != null && def.equals("true") ? "checked " : ""));
    }

    @HtmlInput
    @Binding(examples = {"#420420"}, types={Color.class})
    public static String color(ParameterData param) {
        return WebUtil.createInput(WebUtil.InputType.color, param);
    }

    @HtmlInput
    @Binding(examples = {"8-4-4-4-12"}, types={UUID.class})
    public static String uuid(ParameterData param) {
        String pattern = "/^[0-9a-f]{8}-[0-9a-f]{4}-[0-5][0-9a-f]{3}-[089ab][0-9a-f]{3}-[0-9a-f]{12}$/i";
        return WebUtil.createInput(WebUtil.InputType.text, param, "pattern='" + pattern + "'");
    }

    @Timediff
    @HtmlInput
    @Binding(types={long.class, Long.class}, examples = {"5d", "10h3m25s"})
    public static String timediff(ParameterData param) {
        return WebUtil.createInputWithClass("input", WebUtil.InputType.date, param, "input-timediff", false);
    }

    @Timestamp
    @HtmlInput
    @Binding(types={long.class, Long.class}, examples = {"5d", "10h3m25s", "dd/MM/yyyy"})
    public static String timestamp(ParameterData param) {
        return WebUtil.createInput(WebUtil.InputType.date, param);
    }

    /*
    --------------------------------------------------------------------
     */

    private String formatGuildName(Guild guild) {
        return DiscordUtil.toDiscordChannelString(guild.getName());
    }

    @HtmlInput
    @Binding(types = {User.class})
    public String user(@Me Guild guild, ParameterData param) {
        Set<User> users = guild.getMembers().stream().map(f -> f.getUser()).collect(Collectors.toSet());
        return WebUtil.generateSearchableDropdown(param, users, (obj, names, values, subtext) -> {
            names.add(obj.getName());
            values.add(obj.getAsMention());
            DBNation nation = DiscordUtil.getNation(obj);
            if (nation != null) {
                subtext.add(nation.getNation() + " - " + nation.getAllianceName() + " - " + Rank.byId(nation.getPosition()));
            } else {
                subtext.add("");
            }
        });
    }

    @HtmlInput
    @Binding(types = {Member.class})
    public String member(@Me User user, @Me Guild guild, ParameterData param) {
        List<Member> options = guild.getMembers();
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.getEffectiveName());
            values.add(obj.getAsMention());
            DBNation nation = DiscordUtil.getNation(obj.getUser());
            if (nation != null) {
                subtext.add(nation.getNation() + " - " + nation.getAllianceName() + " - " + Rank.byId(nation.getPosition()));
            } else {
                subtext.add("");
            }
        });
    }

    @HtmlInput
    @Binding(types=Category.class)
    public String category(@Me Guild guild, ParameterData param) {
        List<Category> options = guild.getCategories();
        Filter filter = param.getAnnotation(Filter.class);
        options = new ArrayList<>(options);
        options.removeIf(f -> !f.getName().matches(filter.value()));
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.getName());
            values.add(obj.getIdLong());
        });
    }

    @HtmlInput
    @Binding(types=Guild.class)
    public String guild(@Me User user, ParameterData param) {
        List<Guild> options = user.getMutualGuilds();
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(formatGuildName(obj) + "/" + obj.getIdLong());
            values.add(obj.getIdLong());

            String sub = "<img class='guild-icon-inline' src='" + obj.getIconUrl() + "'>";
            Integer aaId = Locutus.imp().getGuildDB(obj).getOrNull(GuildDB.Key.ALLIANCE_ID);
            if (aaId != null) sub += aaId;
            subtext.add(sub);
        });
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
    @Binding(types=TextChannel.class)
    public String textChannel(@Me Guild guild, @Me User user, ParameterData param) {
        List<MessageChannel> options = getGuildChannels(guild, user);
        options.removeIf(f -> !(f instanceof TextChannel));
        return channel(guild, user, param, options);
    }

    @HtmlInput
    @Binding(types=ICategorizableChannel.class)
    public String categorizableChannel(@Me Guild guild, @Me User user, ParameterData param) {
        List<MessageChannel> options = getGuildChannels(guild, user);
        options.removeIf(f -> !(f instanceof ICategorizableChannel));
        return channel(guild, user, param, options);
    }

    @HtmlInput
    @Binding(types=MessageChannel.class)
    public String channel(@Me Guild guild, @Me User user, ParameterData param) {
        return channel(guild, user, param, getGuildChannels(guild, user));
    }

    public List<MessageChannel> getGuildChannels(Guild guild, User user) {
        Member member = guild.getMember(user);
        if (member == null) throw new IllegalArgumentException("You are not a member");
        List<MessageChannel> options = new ArrayList<>();
        for (GuildChannel channel : guild.getChannels()) {
            if (!(channel instanceof MessageChannel)) continue;
            MessageChannel mc = (MessageChannel) channel;
            if (member.hasAccess(channel)) {
                options.add(mc);
            }
        }
        return options;
    }

    public String channel(@Me Guild guild, @Me User user, ParameterData param, List<MessageChannel> options) {
        if (options.isEmpty()) throw new IllegalArgumentException("You cannot view any channels");
        Collections.sort(options, (o1, o2) -> {
            GuildMessageChannel tc1 = (GuildMessageChannel) o1;
            GuildMessageChannel tc2 = (GuildMessageChannel) o2;
            Category cat1 = (tc1 instanceof ICategorizableChannel) ? ((ICategorizableChannel) tc1).getParentCategory() : null;
            Category cat2 = (tc2 instanceof ICategorizableChannel) ? ((ICategorizableChannel) tc2).getParentCategory() : null;

            if (cat1 != cat2) {
                if (cat1 == null) return 1;
                if (cat2 == null) return -1;
                return Integer.compare(cat1.getPositionRaw(), cat2.getPositionRaw());
            }
            int pos1 = (tc1 instanceof IPositionableChannel) ? ((IPositionableChannel) tc1).getPositionRaw() : -1;
            int pos2 = (tc2 instanceof IPositionableChannel) ? ((IPositionableChannel) tc2).getPositionRaw() : -1;
            if (pos1 != pos2) {
                return Integer.compare(pos1, pos2);
            }
            return Long.compare(tc1.getIdLong(), tc2.getIdLong());
        });
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add("#" + obj.getName());
            GuildMessageChannel tc = (GuildMessageChannel) obj;
            values.add(tc.getAsMention());
            Category cat = (tc instanceof ICategorizableChannel) ? ((ICategorizableChannel) tc).getParentCategory() : null;
            if (cat != null) {
                subtext.add(cat.getName());
            } else {
                subtext.add("");
            }
        });
    }

    @HtmlInput
    @Binding(types=Message.class)
    public String message(ParameterData param) {
        String pattern = "https\\:\\/\\/discord\\.com\\/channels\\/[0-9]+\\/[0-9]+\\/[0-9]+";
        return WebUtil.createInput(WebUtil.InputType.text, param, "pattern='" + pattern + "'");
    }

    /*
    --------------------------------------------------------------------
     */

    @HtmlInput
    @Binding(types= CityBuild.class)
    public String CityBuild(ParameterData param) {
        String hint = "{\n" +
                "    \"infra_needed\": 1900,\n" +
                "    \"imp_total\": 38,\n" +
                "    \"imp_coalpower\": 0,\n" +
                "    \"imp_oilpower\": 0,\n" +
                "    \"imp_windpower\": 0,\n" +
                "    \"imp_nuclearpower\": 1,\n" +
                "    \"imp_coalmine\": 0,\n" +
                "    \"imp_oilwell\": 10,\n" +
                "    \"imp_uramine\": 0,\n" +
                "    \"imp_leadmine\": 1,\n" +
                "    \"imp_ironmine\": 0,\n" +
                "    \"imp_bauxitemine\": 10,\n" +
                "    \"imp_farm\": 0,\n" +
                "    \"imp_gasrefinery\": 4,\n" +
                "    \"imp_aluminumrefinery\": 5,\n" +
                "    \"imp_munitionsfactory\": 0,\n" +
                "    \"imp_steelmill\": 0,\n" +
                "    \"imp_policestation\": 0,\n" +
                "    \"imp_hospital\": 0,\n" +
                "    \"imp_recyclingcenter\": 0,\n" +
                "    \"imp_subway\": 0,\n" +
                "    \"imp_supermarket\": 0,\n" +
                "    \"imp_bank\": 0,\n" +
                "    \"imp_mall\": 0,\n" +
                "    \"imp_stadium\": 0,\n" +
                "    \"imp_barracks\": 4,\n" +
                "    \"imp_factory\": 0,\n" +
                "    \"imp_hangars\": 0,\n" +
                "    \"imp_drydock\": 3\n" +
                "}";
        String placeholder = "placeholder='" + hint + "'";
        return WebUtil.createInput("textarea", WebUtil.InputType.textarea, param, placeholder);
    }

    @HtmlInput
    @Binding(types=DBNation.class, examples = {"Borg", "<@664156861033086987>", "Danzek", "189573", "https://politicsandwar.com/nation/id=189573"})
    public String nation(ParameterData param) {
        Collection<DBNation> options = (Locutus.imp().getNationDB().getNations().values());
        options.removeIf(f -> f.getVm_turns() > 0 && (f.getPosition() <= 1 || f.getCities() < 7));
        options.removeIf(f -> f.getActive_m() > 10000 && f.getCities() < 3);
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.getNation());
            values.add(obj.getId());
            String sub;
            if (obj.getPosition() > 1) {
                sub = "c" + obj.getCities() + " score:" + MathMan.format(obj.getScore()) + " - " + obj.getAllianceName() + " - " + Rank.byId(obj.getPosition());
            } else {
                sub = "c" + obj.getCities() + " score:" + MathMan.format(obj.getScore());
            }
            subtext.add(sub);
        });
    }

    @HtmlInput
    @Binding(types= NationOrAlliance.class)
    public String nationOrAlliance(ParameterData param) {
        List<DBNation> nations = new ArrayList<>(Locutus.imp().getNationDB().getNations().values());
        nations.removeIf(f -> f.getVm_turns() > 0 && (f.getPosition() <= 1 || f.getCities() < 7));
        nations.removeIf(f -> f.getActive_m() > 10000 && f.getCities() < 3);

        Set<DBAlliance> alliances = Locutus.imp().getNationDB().getAlliances();

        List<Map.Entry<String, String>> options = new ArrayList<>(alliances.size() + nations.size());
        for (DBNation nation : nations) {
            options.add(new AbstractMap.SimpleEntry<>(nation.getName(), nation.getNation_id() + ""));
        }
        for (DBAlliance alliance : alliances) {
            options.add(new AbstractMap.SimpleEntry<>("AA:" + alliance.getName(), "AA:" + alliance.getId()));
        }
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.getKey());
            values.add(obj.getValue());
        });
    }

    @HtmlInput
    @Binding(types= NationOrAllianceOrGuild.class)
    public String nationOrAllianceOrGuild(@Me User user, ParameterData param) {
        List<DBNation> nations = new ArrayList<>(Locutus.imp().getNationDB().getNations().values());
        nations.removeIf(f -> f.getVm_turns() > 0 && (f.getPosition() <= 1 || f.getCities() < 7));
        nations.removeIf(f -> f.getActive_m() > 10000 && f.getCities() < 3);

        Set<DBAlliance> alliances = Locutus.imp().getNationDB().getAlliances();

        List<Guild> guilds = user.getMutualGuilds();

        List<NationOrAllianceOrGuild> options = new ArrayList<>(alliances.size() + nations.size() + guilds.size());
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
                subtext.add("nation - " + obj.asNation().getAllianceName());
            } else if (obj.isGuild()) {
                names.add(formatGuildName(obj.asGuild().getGuild()));
                values.add("guild:" + obj.getIdLong());
                subtext.add("guild");
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
                filterStr = filterStr.replace("{guild_alliance_id}", "AA:" + db.getAlliance_id());
            }
            filterStr = DiscordUtil.format(guild, channel, user, me, filterStr);
            options = DiscordUtil.parseNations(guild, filterStr);
        } else {
            options = new ArrayList<>(Locutus.imp().getNationDB().getNations().values());
            options.removeIf(f -> f.getVm_turns() > 0 && (f.getPosition() <= 1 || f.getCities() < 7));
            options.removeIf(f -> f.getActive_m() > 10000 && f.getCities() < 3);
        }

        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.getNation());
            values.add(obj.getId());
            String sub;
            if (obj.getPosition() > 1) {
                sub = "c" + obj.getCities() + " score:" + MathMan.format(obj.getScore()) + " - " + obj.getAllianceName() + " - " + Rank.byId(obj.getPosition());
            } else {
                sub = "c" + obj.getCities() + " score:" + MathMan.format(obj.getScore());
            }
            subtext.add(sub);
        }, true);

    }

    public <T> String multipleSelect(ParameterData param, Collection<T> objects, Function<T, Map.Entry<String, String>> toNameValue) {
        return multipleSelect(param, objects, toNameValue, false);
    }

    public <T> String multipleSelect(ParameterData param, Collection<T> objects, Function<T, Map.Entry<String, String>> toNameValue, boolean multiple) {
        if (true) {
            return WebUtil.generateSearchableDropdown(param, objects, new QuadConsumer<T, JsonArray, JsonArray, JsonArray>() {
                @Override
                public void consume(T obj, JsonArray names, JsonArray values, JsonArray subtext) {
                    Map.Entry<String, String> pair = toNameValue.apply(obj);
                    names.add(pair.getKey());
                    values.add(pair.getValue());
                }
            }, multiple);
        }

        UUID uuid = UUID.randomUUID();

        String def = param.getDefaultValueString();
        String valueStr = def != null ? " value=\"" + def + "\"" : "";
        StringBuilder response = new StringBuilder("<select id=\"" + uuid + "\" class=\"form-control form-control-sm\" name=\"" + param.getName() + "\" " + valueStr + " " + (param.isOptional() ? "" : "required") + " " + (multiple ? " multiple" : "") + ">");

        for (T object : objects) {
            Map.Entry<String, String> pair = toNameValue.apply(object);
            response.append("<option value=\"" + pair.getValue() + "\">" + pair.getKey() + "</option>");
        }

        response.append("</select>");

        return WebUtil.wrapLabel(param, uuid, response.toString(), WebUtil.InlineMode.NONE);
    }

    public final Set<SpyCount.Operation> SPYCOUNT_OPERATIONS_KEY = null;
    public final Set<AllianceMetric> ALLIANCE_METRIC_KEY = null;

    public final Set<Project> PROJECTS_KEY = null;
    public final Set<NationAttributeDouble> NATION_METRIC_KEY = null;
    public final Set<DBNation> NATIONS_KEY = null;
    public final Set<NationOrAlliance> NATIONS_OR_ALLIANCE_KEY = null;
    public final Set<NationOrAllianceOrGuild> NATIONS_OR_ALLIANCE_OR_GUILD_KEY = null;
    public final Set<Role> ROLES_KEY = null;
    public final Set<Member> MEMBERS_KEY = null;
    public final Set<DBAlliance> ALLIANCES_KEY = null;
    public final List<ResourceType> RESOURCE_LIST_KEY = null;
    public final Set<WarStatus> WARSTATUSES_KEY = null;
    public final Map<ResourceType, Double> RESOURCE_MAP_KEY = null;
    public final Map<MilitaryUnit, Long> UNIT_MAP_KEY = null;



    public WebPrimitiveBinding() {

        try {
            {
                Type type = getClass().getDeclaredField("MEMBERS_KEY").getGenericType();
                Key key = Key.of(type, HtmlInput.class);
                addBinding(store -> {
                    store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                        Guild guild = (Guild) valueStore.getProvided(Key.of(Guild.class, Me.class));
                        ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
                        List<Member> options = new ArrayList<>(guild.getMembers());

                        return multipleSelect(param, options, t -> new AbstractMap.SimpleEntry<>(t.getEffectiveName(), t.getAsMention()), true);
                    }));
                });
            }
            {
                Type type = getClass().getDeclaredField("NATIONS_KEY").getGenericType();
                Key key = Key.of(type, HtmlInput.class);
                addBinding(store -> {
                    store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                        ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
                        return nations(valueStore, param);
                    }));
                });
            }
            {
                Type type = getClass().getDeclaredField("NATIONS_OR_ALLIANCE_KEY").getGenericType();
                Key key = Key.of(type, HtmlInput.class);
                addBinding(store -> {
                    store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                        ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
                        return nationOrAlliance(param);
                    }));
                });
            }
            {
                Type type = getClass().getDeclaredField("NATIONS_OR_ALLIANCE_OR_GUILD_KEY").getGenericType();
                Key key = Key.of(type, HtmlInput.class);
                addBinding(store -> {
                    store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                        ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
                        User user = (User) valueStore.getProvided(Key.of(User.class, Me.class));
                        return nationOrAllianceOrGuild(user, param);
                    }));
                });
            }
            {
                Type type = getClass().getDeclaredField("WARSTATUSES_KEY").getGenericType();
                Key key = Key.of(type, HtmlInput.class);
                addBinding(store -> {
                    store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                        ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
                        List<WarStatus> options = Arrays.asList(WarStatus.values());

                        return multipleSelect(param, options, t -> new AbstractMap.SimpleEntry<>(t.name(), t.name()), true);
                    }));
                });
            }
            {
                Type type = getClass().getDeclaredField("ROLES_KEY").getGenericType();
                Key key = Key.of(type, HtmlInput.class);
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
                                sub += hex + " - ";
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
                Type type = getClass().getDeclaredField("ALLIANCE_METRIC_KEY").getGenericType();
                Key key = Key.of(type, HtmlInput.class);
                addBinding(store -> {
                    store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                        ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
                        List<AllianceMetric> options = Arrays.asList(AllianceMetric.values());

                        return multipleSelect(param, options, t -> new AbstractMap.SimpleEntry<>(t.name(), t.name()), true);
                    }));
                });
            }
            {
                Type type = getClass().getDeclaredField("PROJECTS_KEY").getGenericType();
                Key key = Key.of(type, HtmlInput.class);
                addBinding(store -> {
                    store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                        ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
                        List<Project> options = Arrays.asList(Projects.values);

                        return multipleSelect(param, options, t -> new AbstractMap.SimpleEntry<>(t.name(), t.name()), true);
                    }));
                });
            }
            {
                Type type = getClass().getDeclaredField("NATION_METRIC_KEY").getGenericType();
                Key key = Key.of(type, HtmlInput.class);
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
                Type type = getClass().getDeclaredField("ALLIANCES_KEY").getGenericType();
                Key key = Key.of(type, HtmlInput.class);
                addBinding(store -> {
                    store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                        ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);

                        return alliance(param, true);
                    }));
                });
            }
            {
                Type type = getClass().getDeclaredField("RESOURCE_LIST_KEY").getGenericType();
                Key key = Key.of(type, HtmlInput.class);
                addBinding(rootStore -> {
                    rootStore.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) store -> {
                        ParameterData param = (ParameterData) store.getProvided(ParameterData.class);
                        List<ResourceType> options = Arrays.asList(ResourceType.values());

                        return multipleSelect(param, options, rss -> new AbstractMap.SimpleEntry<>(rss.getName(), rss.getName()), true);
                    }));
                });
            }
            {
                Type type = getClass().getDeclaredField("SPYCOUNT_OPERATIONS_KEY").getGenericType();
                Key key = Key.of(type, HtmlInput.class);
                addBinding(rootStore -> {
                    rootStore.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) store -> {
                        ParameterData param = (ParameterData) store.getProvided(ParameterData.class);
                        List<SpyCount.Operation> options = Arrays.asList(SpyCount.Operation.values());

                        return multipleSelect(param, options, op -> new AbstractMap.SimpleEntry<>(op.name(), op.name()), true);
                    }));
                });
            }
            {
                Type type = getClass().getDeclaredField("RESOURCE_MAP_KEY").getGenericType();
                {
                    {
                        Key key = Key.of(type, HtmlInput.class);
                        addBinding(rootStore -> {
                            rootStore.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) store -> getBankInput(store)));
                        });
                        Key key2 = Key.of(type, NationDepositLimit.class, HtmlInput.class);
                        addBinding(rootStore -> {
                            rootStore.addParser(key2, new FunctionProviderParser<>(key2, (Function<ValueStore, String>) store -> getBankInput(store)));
                        });
                        Key key3 = Key.of(type, AllianceDepositLimit.class, HtmlInput.class);
                        addBinding(rootStore -> {
                            rootStore.addParser(key3, new FunctionProviderParser<>(key3, (Function<ValueStore, String>) store -> getBankInput(store)));
                        });
                    }
                }
            }

            {
                Type type = getClass().getDeclaredField("UNIT_MAP_KEY").getGenericType();
                {
                    {
                        Key key = Key.of(type, HtmlInput.class);
                        ArrayList<MilitaryUnit> types = new ArrayList<>(Arrays.asList(MilitaryUnit.values()));
                        types.removeIf(f -> f.getName() == null);

                        addBinding(rootStore -> {
                            rootStore.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) store -> getMapInput(store, types)));
                        });
                    }
                }
            }
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
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
                available = PnwUtil.normalize(db.getOffshore().getDeposits(db));
            }
            if (annotation.annotationType() == NationDepositLimit.class) {
                DBNation me = (DBNation) store.getProvided(Key.of(DBNation.class, Me.class));
                try {
                    available = PnwUtil.normalize(me.getNetDeposits(db));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if (available != null) {
            available = PnwUtil.normalize(available);
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
        Map<String, Set<Integer>> options = db.getCoalitions();
        for (Coalition value : Coalition.values()) {
            options.putIfAbsent(value.name().toLowerCase(), Collections.emptySet());
        }
        return WebUtil.generateSearchableDropdown(param, options.entrySet(), (obj, names, values, subtext) -> {
            names.add(obj.getKey());
            subtext.add(obj.getValue().size() + " entries");
        });
    }

    @HtmlInput
    @Binding(types=ResourceType.class)
    public String resource(ParameterData param) {
        return multipleSelect(param, ResourceType.valuesList, rss -> new AbstractMap.SimpleEntry<>(rss.getName(), rss.getName()));
    }

    @HtmlInput
    @Binding(types= DepositType.class)
    public String DepositType(ParameterData param) {
        return multipleSelect(param, Arrays.asList(DepositType.values()), type -> new AbstractMap.SimpleEntry<>(type.name(), type.name()));
    }

    @HtmlInput
    @Binding(types= WarStatus.class)
    public String WarStatus(ParameterData param) {
        return multipleSelect(param, Arrays.asList(WarStatus.values()), type -> new AbstractMap.SimpleEntry<>(type.name(), type.name()));
    }

    @HtmlInput
    @Binding(types= WarType.class)
    public String WarType(ParameterData param) {
        return multipleSelect(param, Arrays.asList(WarType.values()), type -> new AbstractMap.SimpleEntry<>(type.name(), type.name()));
    }

    @HtmlInput
    @Binding(types=Rank.class)
    public String rank(ParameterData param) {
        return multipleSelect(param, Arrays.asList(Rank.values()), rank -> new AbstractMap.SimpleEntry<>(rank.name(), rank.name()));
    }

    @HtmlInput
    @Binding(types= NationLootType.class)
    public String lootType(ParameterData param) {
        return multipleSelect(param, Arrays.asList(NationLootType.values()), rank -> new AbstractMap.SimpleEntry<>(rank.name(), rank.name()));
    }

    @HtmlInput
    @Binding(types=AlliancePermission.class)
    public String AlliancePermission(ParameterData param) {
        return multipleSelect(param, Arrays.asList(AlliancePermission.values()), f -> new AbstractMap.SimpleEntry<>(f.name(), f.name()));
    }

    @HtmlInput
    @Binding(types=DBAlliancePosition.class)
    public String position(@Me GuildDB db, ParameterData param) {
        DBAlliance alliance = DBAlliance.get(db.getAlliance_id());
        Set<DBAlliancePosition> positions = new HashSet<>(alliance.getPositions());
        positions.add(DBAlliancePosition.REMOVE);
        positions.add(DBAlliancePosition.APPLICANT);
        return multipleSelect(param, positions, rank -> new AbstractMap.SimpleEntry<>(rank.getName(), rank.getInputName()));
    }

    @HtmlInput
    @Binding(types= Permission.class)
    public String permission(ParameterData param) {
        return multipleSelect(param, Arrays.asList(Permission.values()), f -> new AbstractMap.SimpleEntry<>(f.name(), f.name()));
    }

    @HtmlInput
    @Binding(types= UnsortedCommands.ClearRolesEnum.class)
    public String ClearRolesEnum(ParameterData param) {
        return multipleSelect(param, Arrays.asList(UnsortedCommands.ClearRolesEnum.values()), rank -> new AbstractMap.SimpleEntry<>(rank.name(), rank.name()));
    }

    @HtmlInput
    @Binding(types= GuildDB.Key.class)
    public String GuildDBKey(@Me GuildDB db, @Me Guild guild, @Me User author, ParameterData param) {
        ArrayList<GuildDB.Key> options = new ArrayList<>(Arrays.asList(GuildDB.Key.values()));
        options.removeIf(key -> {
            if (key.requires != null && db.getOrNull(key.requires) == null) return true;
            if (!key.allowed(db)) return true;
            if (!key.hasPermission(db, author, null)) return true;
            return false;
        });
        return multipleSelect(param, options, f -> new AbstractMap.SimpleEntry<>(f.name(), f.name()));
    }

    @HtmlInput
    @Binding(types= OnlineStatus.class)
    public String onlineStatus(ParameterData param) {
        return multipleSelect(param, Arrays.asList(OnlineStatus.values()), arg -> new AbstractMap.SimpleEntry<>(arg.name(), arg.name()));
    }

    @HtmlInput
    @Binding(types= Continent.class)
    public String Continent(ParameterData param) {
        return multipleSelect(param, Arrays.asList(Continent.values()), arg -> new AbstractMap.SimpleEntry<>(arg.name(), arg.name()));
    }

    @HtmlInput
    @Binding(types= NationMeta.BeigeAlertMode.class)
    public String BeigeAlertMode(ParameterData param) {
        return multipleSelect(param, Arrays.asList(NationMeta.BeigeAlertMode.values()), arg -> new AbstractMap.SimpleEntry<>(arg.name(), arg.name()));
    }

    @HtmlInput
    @Binding(types= NationMeta.BeigeAlertRequiredStatus.class)
    public String BeigeAlertRequiredStatus(ParameterData param) {
        return multipleSelect(param, Arrays.asList(NationMeta.BeigeAlertRequiredStatus.values()), arg -> new AbstractMap.SimpleEntry<>(arg.name(), arg.name()));
    }

    @HtmlInput
    @Binding(types= MilitaryUnit.class)
    public String unit(ParameterData param) {
        return multipleSelect(param, Arrays.asList(MilitaryUnit.values()), arg -> new AbstractMap.SimpleEntry<>(arg.name(), arg.name()));
    }

    @HtmlInput
    @Binding(types= Operation.class)
    public String operation(ParameterData param) {
        return multipleSelect(param, Arrays.asList(Operation.values()), op -> new AbstractMap.SimpleEntry<>(op.name(), op.name()));
    }

    @HtmlInput
    @Binding(types= SpyCount.Safety.class)
    public String spySafety(ParameterData param) {
        return multipleSelect(param, Arrays.asList(SpyCount.Safety.values()), arg -> new AbstractMap.SimpleEntry<>(arg.name(), arg.name()));
    }

    @HtmlInput
    @Binding(types= TreatyType.class)
    public String TreatyType(ParameterData param) {
        return multipleSelect(param, Arrays.asList(TreatyType.values()), arg -> new AbstractMap.SimpleEntry<>(arg.name(), arg.name()));
    }

    @HtmlInput
    @Binding(types= AllianceMetric.class)
    public String AllianceMetric(ParameterData param) {
        return multipleSelect(param, Arrays.asList(AllianceMetric.values()), arg -> new AbstractMap.SimpleEntry<>(arg.name(), arg.name()));
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
                sub += hex + " - ";
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
        List<Roles> options = Arrays.asList(Roles.values);
        if (param.getAnnotation(RegisteredRole.class) != null) {
            options = new ArrayList<>(options);
            options.removeIf(f -> f.toRole(guild) == null);
        }
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            GuildDB.Key key = obj.getKey();
            if (key != null && db.getOrNull(key) == null) {
                return;
//                if (!sub.isEmpty()) sub += "  - ";
//                sub += " MISSING: `" + key + "`";
            }
            String name = obj.name();
            names.add(name);

            Role discordRole = obj.toRole(guild);
            String sub = "";
            if (discordRole != null) {
                sub += "@" + discordRole.getName();
            }
            subtext.add(sub);
        });
    }

    @HtmlInput
    @Binding(types= Project.class)
    public String project(ParameterData param) {
        List<Project> options = Arrays.asList(Projects.values);
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.name());
            String sub;
            if (obj.getOutput() != null) {
                sub = obj.getOutput() + " - " + PnwUtil.resourcesToString(obj.cost());
            } else {
                sub = PnwUtil.resourcesToString(obj.cost());
            }
            subtext.add(sub);
        });
    }

    @HtmlInput
    @Binding(types= TaxBracket.class)
    public String bracket(@Me GuildDB db, ParameterData param) {
        Map<Integer, TaxBracket> brackets = db.getAlliance().getTaxBrackets(true);
        Collection<TaxBracket> options = brackets.values();
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.name + ": " + obj.moneyRate + "/" + obj.rssRate);
            subtext.add("#" + obj.taxId + " (" + obj.nations + " nations)");
            values.add("tax_id=" + obj.taxId);
        });
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
            subtext.add(obj.getSimpleDesc());
        });
    }
}
