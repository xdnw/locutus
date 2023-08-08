package link.locutus.discord.commands.manager.v2.impl.pw.binding.autocomplete;

import com.google.gson.reflect.TypeToken;
import com.knuddels.jtokkit.api.ModelType;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.FunctionConsumerParser;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.ICommand;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.GuildCoalition;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.NationDepositLimit;
import link.locutus.discord.commands.manager.v2.impl.pw.NationPlaceholder;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttribute;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.gpt.pwembed.PWGPTHandler;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.BeigeReason;
import link.locutus.discord.pnw.CityRanges;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.pnw.NationOrAllianceOrGuildOrTaxid;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.AutoAuditType;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.commands.manager.v2.binding.annotation.Autocomplete;
import link.locutus.discord.commands.manager.v2.binding.annotation.AllianceDepositLimit;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.util.task.ia.IACheckup;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.lang.reflect.Type;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PWCompleter extends BindingHelper {
    @Autocomplete
    @Binding(types={CommandCallable.class})
    public List<String> command(String input) {
        List<ParametricCallable> options = new ArrayList<>(Locutus.imp().getCommandManager().getV2().getCommands().getParametricCallables(f -> true));
        List<String> optionsStr = options.stream().map(f -> f.getFullPath()).toList();
        return StringMan.getClosest(input, optionsStr, f -> f, OptionData.MAX_CHOICES, true);
    }

    @Autocomplete
    @Binding(types={ICommand.class})
    public List<String> commandEndpoint(String input) {
        return command(input);
    }
    
    @Autocomplete
    @Binding(types={Coalition.class})
    public List<String> Coalition(String input) {
        return StringMan.completeEnum(input, Coalition.class);
    }

    @Autocomplete
    @Binding(types={DepositType.DepositTypeInfo.class})
    public List<String> DepositTypeInfo(String input) {
        return StringMan.completeEnum(input, DepositType.class);
    }

    @Autocomplete
    @Binding(types={AlliancePermission.class})
    public List<String> AlliancePermission(String input) {
        return StringMan.completeEnum(input, AlliancePermission.class);
    }

    @Autocomplete
    @Binding(types={DBAlliancePosition.class})
    public List<Map.Entry<String, String>> DBAlliancePosition(@Me GuildDB db, String input) {
        AllianceList alliances = db.getAllianceList();
        if (alliances == null || alliances.isEmpty()) return null;
        List<DBAlliancePosition> options = new ArrayList<>(alliances.getPositions());
        options.add(DBAlliancePosition.REMOVE);
        options.add(DBAlliancePosition.APPLICANT);

        options = StringMan.getClosest(input, options, DBAlliancePosition::getName, OptionData.MAX_CHOICES, true);
        return options.stream().map(new Function<DBAlliancePosition, Map.Entry<String, String>>() {
            @Override
            public Map.Entry<String, String> apply(DBAlliancePosition f) {
                return Map.entry(f.getQualifiedName(), f.getInputName());
            }
        }).collect(Collectors.toList());
    }


    @Autocomplete
    @GuildCoalition
    @Binding(types={String.class})
    public List<String> GuildCoalition(@Me GuildDB db, String input) {
        List<String> options = new ArrayList<>();
        for (Coalition coalition : Coalition.values()) {
            options.add(coalition.name());
        }
        for (String coalition : db.getCoalitions().keySet()) {
            if (Coalition.getOrNull(coalition) != null) continue;
            options.add(coalition);
        }
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true);
    }

    @Autocomplete
    @Binding(types={DBNation.class})
    public List<String> DBNation(String input) {
        if (input.isEmpty()) return null;

        List<DBNation> options = new ArrayList<>(Locutus.imp().getNationDB().getNations().values());
        options = StringMan.getClosest(input, options, DBNation::getName, OptionData.MAX_CHOICES, true, true);

        return options.stream().map(DBNation::getNation).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={DBAlliance.class})
    public List<Map.Entry<String, String>> DBAlliance(String input) {
        if (input.isEmpty()) return null;

        List<DBAlliance> options = new ArrayList<>(Locutus.imp().getNationDB().getAlliances());
        options = StringMan.getClosest(input, options, DBAlliance::getName, OptionData.MAX_CHOICES, true, true);

        return options.stream().map(f -> Map.entry(f.getName(), f.getTypePrefix() + ":" + f.getId())).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={NationOrAlliance.class})
    public List<Map.Entry<String, String>> NationOrAlliance(String input) {
        if (input.isEmpty()) return null;

        List<NationOrAlliance> options = new ArrayList<>(Locutus.imp().getNationDB().getNations().values());
        options.addAll(Locutus.imp().getNationDB().getAlliances());

        options = StringMan.getClosest(input, options, NationOrAlliance::getName, OptionData.MAX_CHOICES, true, true);

        return options.stream().map(f -> Map.entry(f.getName(), f.getTypePrefix() + ":" + f.getId())).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={NationOrAllianceOrGuild.class})
    public List<Map.Entry<String, String>> NationOrAllianceOrGuild(String input, @Me User user) {
        if (input.isEmpty()) return null;

        List<NationOrAllianceOrGuild> options = new ArrayList<>(Locutus.imp().getNationDB().getNations().values());
        options.addAll(Locutus.imp().getNationDB().getAlliances());
        if (user != null) {
            for (Guild guild : user.getMutualGuilds()) {
                GuildDB db = Locutus.imp().getGuildDB(guild);
                if (db != null) {
                    options.add(db);
                }
            }
        }
        options = StringMan.getClosest(input, options, NationOrAllianceOrGuild::getName, OptionData.MAX_CHOICES, true, true);
        return options.stream().map(f -> Map.entry((f.isGuild() ? "guild:" : "") + f.getName(), f.getTypePrefix() + ":" + f.getIdLong())).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={NationOrAllianceOrGuildOrTaxid.class})
    public List<Map.Entry<String, String>> NationOrAllianceOrGuildOrTaxid(String input, @Me GuildDB db, @Me User user) {
        if (input.isEmpty()) return null;
        AllianceList aaList = db.getAllianceList();

        List<NationOrAllianceOrGuildOrTaxid> options = new ArrayList<>(Locutus.imp().getNationDB().getNations().values());
        options.addAll(Locutus.imp().getNationDB().getAlliances());
        if (user != null) {
            for (Guild guild : user.getMutualGuilds()) {
                GuildDB mutual = Locutus.imp().getGuildDB(guild);
                if (mutual != null) {
                    options.add(mutual);
                }
            }
        }
        if (aaList != null) {
            for (Map.Entry<Integer, TaxBracket> entry : aaList.getTaxBrackets(true).entrySet()) {
                TaxBracket bracket = entry.getValue();
                if (bracket.getName().isEmpty()) {
                    bracket.setName("tax_id:" + bracket.taxId);
                }
                options.add(bracket);
            }
        }

        options = StringMan.getClosest(input, options, NationOrAllianceOrGuildOrTaxid::getName, OptionData.MAX_CHOICES, true, true);
        return options.stream().map(f -> Map.entry((f.isGuild() ? "guild:" : "") + f.getName(), f.getTypePrefix() + ":" + f.getIdLong())).collect(Collectors.toList());
    }

    private final static List<String> MMR_MATCHER_CHAR_5 = new ArrayList<>();
    private final static List<String> MMR_MATCHER_CHAR_3 = new ArrayList<>();
    static {
        for (int i = 0; i <= 5; i++) {
            MMR_MATCHER_CHAR_5.add(String.valueOf(i));
        }
        for (int i = 0; i <= 3; i++) {
            MMR_MATCHER_CHAR_3.add(String.valueOf(i));
        }
        MMR_MATCHER_CHAR_5.add("X");
        MMR_MATCHER_CHAR_3.add("X");
    }

    @Autocomplete
    @Binding(types={MMRMatcher.class, MMRDouble.class})
    public List<String> MMRMatcher(String input) {
        List<String> append = null;
        switch (input.length()) {
            case 0:
                return MMR_MATCHER_CHAR_5;
            case 1:
            case 2:
                append = MMR_MATCHER_CHAR_5;
                break;
            case 3:
                append = MMR_MATCHER_CHAR_3;
                break;
        }
        if (append != null) {
            return append.stream().map(f -> input + f).collect(Collectors.toList());
        }
        return null;
    }

    @Autocomplete
    @Binding(types={CityRanges.class})
    public List<String> CityRanges(String input) {
        if (input.isEmpty() || input.endsWith(",")) return List.of(input + "c");
        char lastChar = input.charAt(input.length() - 1);

        boolean allowDigit = false;
        boolean allowDash = false;
        boolean allowComma = false;

        String[] split = input.split("-");
        // last
        String token = split[split.length - 1];

        if (Character.isDigit(lastChar)) {
            // if only 1 preceeding digit, allow digit
            if (token.length() == 1 || !Character.isDigit(token.charAt(token.length() - 2))) {
                allowDigit = true;
            }
            // if token does not contain '-' allow dash
            if (!token.contains("-")) {
                allowDash = true;
            }
            // if token does contain '-' allow comma
            if (token.contains("-")) {
                allowComma = true;
            }
        } else if (lastChar == '-') {
            allowDigit = true;
        } else if (lastChar == ',') {
            return List.of("c");
        } else {
            allowDigit = true;
        }
        List<String> options = new LinkedList<>();
        if (allowDigit) {
            for (int i = 0; i < 10; i++) {
                options.add(input + i);
            }
        }
        if (allowDash) {
            options.add(input + "-");
        }
        if (allowComma) {
            options.add(input + ",");
        }
        return options;
    }


    @Autocomplete
    @Binding(types={AllianceMetric.class})
    public List<String> AllianceMetric(String input) {
        return StringMan.completeEnum(input, AllianceMetric.class);
    }

    @Autocomplete
    @Binding(types={AutoAuditType.class})
    public List<String> AutoAuditType(String input) {
        return StringMan.completeEnum(input, AutoAuditType.class);
    }

    @Autocomplete
    @Binding(types={Project.class})
    public List<String> Project(String input) {
        List<Project> options = Arrays.asList(Projects.values);;
        options = StringMan.getClosest(input, options, Project::name, OptionData.MAX_CHOICES, true);
        return options.stream().map(Project::name).collect(Collectors.toList());
    }


    @Autocomplete
    @Binding(types={NationPlaceholder.class})
    public List<String> NationPlaceholder(String input) {
        NationPlaceholders placeholders = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
        List<String> options = new ArrayList<>(placeholders.getKeys());
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true);
    }

    @Autocomplete
    @Binding(types={GuildSetting.class})
    public List<String> setting(String input) {
        List<String> options = Arrays.asList(GuildKey.values()).stream().map(f -> f.name()).collect(Collectors.toList());
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true);
    }

    @Autocomplete
    @Binding(types={NationAttributeDouble.class})
    public List<String> NationPlaceholder(ArgumentStack stack, String input) {
        NationPlaceholders placeholders = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
        List<String> options = placeholders.getMetricsDouble(stack.getStore())
                .stream().map(NationAttribute::getName).collect(Collectors.toList());
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true);
    }

    @Autocomplete
    @Binding(types={TaxBracket.class})
    public List<String> TaxBracket(@Me GuildDB db, String input) {
        Map<Integer, TaxBracket> brackets = db.getAllianceList().getTaxBrackets(true);
        if (brackets.isEmpty()) return null;

        List<String> options = brackets.values().stream().map(f -> f.taxId + "").collect(Collectors.toList());
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true);
    }

    @Autocomplete
    @Binding(types={UnsortedCommands.ClearRolesEnum.class})
    public List<String> ClearRolesEnum(String input) {
        return StringMan.completeEnum(input, UnsortedCommands.ClearRolesEnum.class);
    }

    {
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, DBAlliance.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    List<DBAlliance> options = new ArrayList<>(Locutus.imp().getNationDB().getAlliances());
                    String inputStr = input.toString();
                    return StringMan.autocompleteComma(inputStr, options, f -> DBAlliance.parse(f, false), DBAlliance::getName, f -> f.getId() + "", OptionData.MAX_CHOICES);
                }));
            });
        }

        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, Roles.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(Roles.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }

        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, BeigeReason.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(BeigeReason.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, IACheckup.AuditType.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(IACheckup.AuditType.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, SpyCount.Operation.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(SpyCount.Operation.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, IACheckup.AuditType.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(IACheckup.AuditType.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, Continent.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(Continent.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, WarStatus.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(WarStatus.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, WarType.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(WarType.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, AttackType.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(AttackType.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, MilitaryUnit.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(MilitaryUnit.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(List.class, ResourceType.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    List<ResourceType> options = new ArrayList<>(ResourceType.valuesList);
                    return StringMan.autocompleteComma(input.toString(), options, ResourceType::valueOf, ResourceType::getName, ResourceType::getName, OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Map.class, MilitaryUnit.class, Long.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    List<String> options = Arrays.asList(MilitaryUnit.values).stream().map(Enum::name).collect(Collectors.toList());
                    return StringMan.completeMap(options, null, input.toString());
                }));
            });
        }
        {
            Type type = TypeToken.getParameterized(Map.class, ResourceType.class, Double.class).getType();
            Consumer<ValueStore<?>> binding = store -> {
                Key key = Key.of(type, Autocomplete.class);
                FunctionConsumerParser parser = new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    List<String> options = ResourceType.valuesList.stream().map(Enum::name).collect(Collectors.toList());
                    return StringMan.completeMap(options, null, input.toString());
                });
                store.addParser(key, parser);
                store.addParser(Key.of(type, Autocomplete.class, AllianceDepositLimit.class), parser);
                store.addParser(Key.of(type, Autocomplete.class, NationDepositLimit.class), parser);
            };
            addBinding(binding);
        }
    }
}