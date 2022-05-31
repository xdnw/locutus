package com.boydti.discord.commands.manager.v2.impl.pw.binding.autocomplete;

import com.boydti.discord.Locutus;
import com.boydti.discord.apiv1.enums.MilitaryUnit;
import com.boydti.discord.apiv1.enums.ResourceType;
import com.boydti.discord.apiv1.enums.city.project.Project;
import com.boydti.discord.apiv1.enums.city.project.Projects;
import com.boydti.discord.commands.manager.v2.binding.BindingHelper;
import com.boydti.discord.commands.manager.v2.binding.FunctionConsumerParser;
import com.boydti.discord.commands.manager.v2.binding.Key;
import com.boydti.discord.commands.manager.v2.binding.ValueStore;
import com.boydti.discord.commands.manager.v2.binding.annotation.*;
import com.boydti.discord.commands.manager.v2.command.ArgumentStack;
import com.boydti.discord.commands.manager.v2.impl.discord.binding.annotation.GuildCoalition;
import com.boydti.discord.commands.manager.v2.impl.discord.binding.annotation.NationDepositLimit;
import com.boydti.discord.commands.manager.v2.impl.pw.NationPlaceholder;
import com.boydti.discord.commands.manager.v2.impl.pw.binding.NationMetric;
import com.boydti.discord.commands.manager.v2.impl.pw.binding.NationMetricDouble;
import com.boydti.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands;
import com.boydti.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.db.entities.AllianceMetric;
import com.boydti.discord.db.entities.Coalition;
import com.boydti.discord.db.entities.WarStatus;
import com.boydti.discord.pnw.Alliance;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.pnw.NationOrAlliance;
import com.boydti.discord.pnw.NationOrAllianceOrGuild;
import com.boydti.discord.util.SpyCount;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.commands.manager.v2.binding.annotation.Autocomplete;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.lang.reflect.Type;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PWCompleter extends BindingHelper {

    @Autocomplete
    @Binding(types={Coalition.class})
    public List<String> Coalition(String input) {
        return StringMan.completeEnum(input, Coalition.class);
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
    @Binding(types={NationOrAlliance.class})
    public List<Map.Entry<String, String>> NationOrAlliance(String input) {
        if (input.isEmpty()) return null;

        List<NationOrAlliance> options = new ArrayList<>(Locutus.imp().getNationDB().getNations().values());
        options.addAll(Locutus.imp().getNationDB().getAlliances().keySet().stream().map(Alliance::new).collect(Collectors.toList()));

        options = StringMan.getClosest(input, options, new Function<NationOrAlliance, String>() {
            @Override
            public String apply(NationOrAlliance f) {
                return f.getName();
            }
        }, OptionData.MAX_CHOICES, true, true);

        return options.stream().map(new Function<NationOrAlliance, Map.Entry<String, String>>() {
            @Override
            public Map.Entry<String, String> apply(NationOrAlliance f) {

                return null;
            }
        }).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={AllianceMetric.class})
    public List<String> AllianceMetric(String input) {
        return StringMan.completeEnum(input, AllianceMetric.class);
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
    @Binding(types={NationMetricDouble.class})
    public List<String> NationPlaceholder(ArgumentStack stack, String input) {
        NationPlaceholders placeholders = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
        List<String> options = placeholders.getMetricsDouble(stack.getStore())
                .stream().map(NationMetric::getName).collect(Collectors.toList());
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true);
    }

    @Autocomplete
    @Binding(types={UnsortedCommands.ClearRolesEnum.class})
    public List<String> ClearRolesEnum(String input) {
        return StringMan.completeEnum(input, UnsortedCommands.ClearRolesEnum.class);
    }

    public final List<ResourceType> RESOURCE_LIST_KEY = null;
    public final Set<Alliance> ALLIANCES_KEY = null;
    public final Set<WarStatus> WARSTATUSES_KEY = null;
    public final Set<SpyCount.Operation> SPYCOUNT_OPERATIONS_KEY = null;
    public final Map<ResourceType, Double> RESOURCE_MAP_KEY = null;
    public final Map<MilitaryUnit, Long> UNIT_MAP_KEY = null;

    public final Set<NationOrAllianceOrGuild> NATIONS_OR_ALLIANCE_OR_GUILD_KEY = null;
    public final Set<NationOrAlliance> NATIONS_OR_ALLIANCE_KEY = null;
    public final Set<DBNation> NATIONS_KEY = null;
    public final Set<AllianceMetric> ALLIANCE_METRIC_KEY = null;
    public final Set<NationMetricDouble> NATION_METRIC_KEY = null;

    {
        try {
            {
                Type type = getClass().getDeclaredField("SPYCOUNT_OPERATIONS_KEY").getGenericType();
                Key key = Key.of(type, Autocomplete.class);
                addBinding(store -> {
                    store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                        return StringMan.autocompleteCommaEnum(SpyCount.Operation.class, input.toString(), OptionData.MAX_CHOICES);
                    }));
                });
            }
            {
                Type type = getClass().getDeclaredField("SPYCOUNT_OPERATIONS_KEY").getGenericType();
                Key key = Key.of(type, Autocomplete.class);
                addBinding(store -> {
                    store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                        return StringMan.autocompleteCommaEnum(SpyCount.Operation.class, input.toString(), OptionData.MAX_CHOICES);
                    }));
                });
            }
            {
                Type type = getClass().getDeclaredField("WARSTATUSES_KEY").getGenericType();
                Key key = Key.of(type, Autocomplete.class);
                addBinding(store -> {
                    store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                        return StringMan.autocompleteCommaEnum(WarStatus.class, input.toString(), OptionData.MAX_CHOICES);
                    }));
                });
            }
            {
                Type type = getClass().getDeclaredField("RESOURCE_LIST_KEY").getGenericType();
                Key key = Key.of(type, Autocomplete.class);
                addBinding(store -> {
                    store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                        List<ResourceType> options = new ArrayList<>(ResourceType.valuesList);
                        return StringMan.autocompleteComma(input.toString(), options, ResourceType::valueOf, ResourceType::getName, ResourceType::getName, OptionData.MAX_CHOICES);
                    }));
                });
            }
            {
                Type type = getClass().getDeclaredField("UNIT_MAP_KEY").getGenericType();
                Key key = Key.of(type, Autocomplete.class);
                addBinding(store -> {
                    store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                        List<String> options = Arrays.asList(MilitaryUnit.values).stream().map(Enum::name).collect(Collectors.toList());
                        return StringMan.completeMap(options, null, input.toString());
                    }));
                });
            }
            {
                Type type = getClass().getDeclaredField("RESOURCE_MAP_KEY").getGenericType();
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
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
