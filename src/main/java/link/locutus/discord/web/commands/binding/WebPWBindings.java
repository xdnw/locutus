package link.locutus.discord.web.commands.binding;

import com.google.gson.reflect.TypeToken;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.apiv3.enums.NationLootType;
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
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.commands.manager.v2.binding.bindings.Operation;
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.NationPlaceholder;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.ReportCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.CityRanges;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.pnw.NationOrAllianceOrGuildOrTaxid;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.task.ia.IACheckup;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.HtmlInput;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

import java.awt.Color;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

import static link.locutus.discord.web.WebUtil.createInput;
import static link.locutus.discord.web.WebUtil.generateSearchableDropdown;
import static link.locutus.discord.web.WebUtil.wrapLabel;

public class WebPWBindings extends WebBindingHelper {




    /*
    --------------------------------------------------------------------
     */



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
        String pattern = Pattern.quote(Settings.INSTANCE.PNW_URL() + "/nation/war/timeline/war") + "=[0-9]+";
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
    public String nationOrAllianceOrGuildOrTaxid(@Me User user, @Me GuildDB db, ParameterData param) {
        return nationOrAllianceOrGuildOrTaxid(user, db, param, true);
    }

    public String nationOrAllianceOrGuildOrTaxid(@Me User user, @Me GuildDB db, ParameterData param, boolean includeBrackets) {
        List<DBNation> nations = new ArrayList<>(Locutus.imp().getNationDB().getNations().values());
        nations.removeIf(f -> f.getVm_turns() > 0 && (f.getPosition() <= 1 || f.getCities() < 7));
        nations.removeIf(f -> f.getActive_m() > 10000 && f.getCities() < 3);

        Set<DBAlliance> alliances = Locutus.imp().getNationDB().getAlliances();

        List<Guild> guilds = user.getMutualGuilds();

        AllianceList aaList = includeBrackets && db == null ? null : db.getAllianceList();
        Map<Integer, TaxBracket> taxIds = aaList == null || aaList.isEmpty() ? null : aaList.getTaxBrackets(true);

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
                subtext.add("nation - " + obj.asNation().getAllianceName());
            } else if (obj.isGuild()) {
                names.add(DiscordWebBindings.formatGuildName(obj.asGuild().getGuild()));
                values.add("guild:" + obj.getIdLong());
                subtext.add("guild");
            } else if (obj.isTaxid()) {
                TaxBracket bracket = obj.asBracket();
                int aaId = bracket.getAlliance_id();
                names.add((aaId > 0 ? DBAlliance.getOrCreate(aaId).getName() + " - " : "") + bracket.getName() + ": " + bracket.moneyRate + "/" + bracket.rssRate);
                subtext.add("#" + bracket.taxId + " (" + bracket.getNations().size() + " nations)");
                values.add("tax_id=" + bracket.taxId);
            }
        });
    }


    @HtmlInput
    @Binding(types= NationOrAllianceOrGuild.class)
    public String nationOrAllianceOrGuild(@Me User user, ParameterData param) {
        return nationOrAllianceOrGuildOrTaxid(user, null, param, false);
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

    public WebPWBindings() {

        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, Member.class).getType(), HtmlInput.class);
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
            Key key = Key.of(TypeToken.getParameterized(Set.class, DBNation.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
                    return nations(valueStore, param);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, NationOrAlliance.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
                    return nationOrAlliance(param);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, NationOrAllianceOrGuild.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
                    User user = (User) valueStore.getProvided(Key.of(User.class, Me.class));
                    return nationOrAllianceOrGuild(user, param);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, NationOrAllianceOrGuildOrTaxid.class).getType(), HtmlInput.class);
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
            Key key = Key.of(TypeToken.getParameterized(Set.class, WarStatus.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
                    List<WarStatus> options = Arrays.asList(WarStatus.values());
                    return multipleSelect(param, options, t -> new AbstractMap.SimpleEntry<>(t.name(), t.name()), true);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, WarType.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    return multipleSelectEmum(WarType.class, valueStore);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, AttackType.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    return multipleSelectEmum(AttackType.class, valueStore);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, IACheckup.AuditType.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    return multipleSelectEmum(IACheckup.AuditType.class, valueStore);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, Continent.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    return multipleSelectEmum(Continent.class, valueStore);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, Role.class).getType(), HtmlInput.class);
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
            Key key = Key.of(TypeToken.getParameterized(Set.class, AllianceMetric.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
                    List<AllianceMetric> options = Arrays.asList(AllianceMetric.values());

                    return multipleSelect(param, options, t -> new AbstractMap.SimpleEntry<>(t.name(), t.name()), true);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, Project.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);
                    List<Project> options = Arrays.asList(Projects.values);

                    return multipleSelect(param, options, t -> new AbstractMap.SimpleEntry<>(t.name(), t.name()), true);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, NationAttributeDouble.class).getType(), HtmlInput.class);
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
            Key key = Key.of(TypeToken.getParameterized(Set.class, DBAlliance.class).getType(), HtmlInput.class);
            addBinding(store -> {
                store.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) valueStore -> {
                    ParameterData param = (ParameterData) valueStore.getProvided(ParameterData.class);

                    return alliance(param, true);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(List.class, ResourceType.class).getType(), HtmlInput.class);
            addBinding(rootStore -> {
                rootStore.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) store -> {
                    ParameterData param = (ParameterData) store.getProvided(ParameterData.class);
                    List<ResourceType> options = Arrays.asList(ResourceType.values());

                    return multipleSelect(param, options, rss -> new AbstractMap.SimpleEntry<>(rss.getName(), rss.getName()), true);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, SpyCount.Operation.class).getType(), HtmlInput.class);
            addBinding(rootStore -> {
                rootStore.addParser(key, new FunctionProviderParser<>(key, (Function<ValueStore, String>) store -> {
                    ParameterData param = (ParameterData) store.getProvided(ParameterData.class);
                    List<SpyCount.Operation> options = Arrays.asList(SpyCount.Operation.values());

                    return multipleSelect(param, options, op -> new AbstractMap.SimpleEntry<>(op.name(), op.name()), true);
                }));
            });
        }
        {
            Type type = TypeToken.getParameterized(Map.class, ResourceType.class, Double.class).getType();
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
            Type type = TypeToken.getParameterized(Map.class, MilitaryUnit.class, Long.class).getType();
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
    @Binding(types= DepositType.DepositTypeInfo.class)
    public String DepositTypeInfo(ParameterData param) {
        return DepositType(param);
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
    @Binding(types= AttackType.class)
    public String AttackType(ParameterData param) {
        return multipleSelect(param, Arrays.asList(AttackType.values()), type -> new AbstractMap.SimpleEntry<>(type.name(), type.name()));
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
        AllianceList alliances = db.getAllianceList();
        Set<DBAlliancePosition> positions = new HashSet<>(alliances.getPositions());
        positions.add(DBAlliancePosition.REMOVE);
        positions.add(DBAlliancePosition.APPLICANT);
        return multipleSelect(param, positions, rank -> new AbstractMap.SimpleEntry<>(alliances.size() > 1 ? rank.getQualifiedName() : rank.getName(), rank.getInputName()));
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
    @Binding(types= GuildSetting.class)
    public String GuildSetting(@Me GuildDB db, @Me Guild guild, @Me User author, ParameterData param) {
        ArrayList<GuildSetting> options = new ArrayList<>(Arrays.asList(GuildKey.values()));
        options.removeIf(key -> {
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
    @Binding(types= ReportCommands.ReportType.class)
    public String ReportType(ParameterData param) {
        return multipleSelect(param, Arrays.asList(ReportCommands.ReportType.values()), arg -> new AbstractMap.SimpleEntry<>(arg.name(), arg.name()));
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
            GuildSetting key = obj.getKey();
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
        Map<Integer, TaxBracket> brackets = db.getAllianceList().getTaxBrackets(true);
        Collection<TaxBracket> options = brackets.values();
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            DBAlliance alliance = obj.getAlliance();
            names.add(alliance.getName() + " - " + obj.getName() + ": " + obj.moneyRate + "/" + obj.rssRate);
            subtext.add("#" + obj.taxId + " (" + obj.getNations().size() + " nations)");
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
            subtext.add(obj.simpleDesc());
        });
    }
}
