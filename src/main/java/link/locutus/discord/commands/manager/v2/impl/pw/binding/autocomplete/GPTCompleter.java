package link.locutus.discord.commands.manager.v2.impl.pw.binding.autocomplete;

import com.google.gson.reflect.TypeToken;
import com.knuddels.jtokkit.api.ModelType;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.FunctionConsumerParser;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Autocomplete;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.ProviderType;
import link.locutus.discord.gpt.pw.PWGPTHandler;
import link.locutus.discord.util.StringMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class GPTCompleter extends BindingHelper {
    @Autocomplete
    @Binding(types={ModelType.class})
    public List<String> ModelType(String input) {
        return StringMan.completeEnum(input, ModelType.class);
    }

    @Autocomplete
    @Binding(types={EmbeddingSource.class})
    public List<String> embeddingSource(PWGPTHandler handler, @Me Guild guild, String input) {
        Set<EmbeddingSource> sources = handler.getSources(guild, true);
        List<String> sourceList = sources.stream().map(f -> f.source_name).collect(Collectors.toList());
        return StringMan.getClosest(input, sourceList, f -> f, OptionData.MAX_CHOICES, true);
    }

    public GPTCompleter() {
        {
            Key<Object> key = Key.of(TypeToken.getParameterized(Set.class, ProviderType.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(ProviderType.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key<Object> key = Key.of(TypeToken.getParameterized(Set.class, EmbeddingSource.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    PWGPTHandler handler = Locutus.imp().getCommandManager().getV2().getGptHandler();
                    Guild guild = (Guild) valueStore.getProvided(Key.of(Guild.class, Me.class));
                    List<EmbeddingSource> options = new ArrayList<>(handler.getSources(guild, true));
                    Map<String, EmbeddingSource> optionsMap = options.stream().collect(Collectors.toMap(f -> f.source_name.toLowerCase(Locale.ROOT), f -> f));
                    String inputStr = input.toString();
                    return StringMan.autocompleteComma(inputStr, options, f -> optionsMap.get(f.toLowerCase(Locale.ROOT)), f -> f.source_name, f -> f.source_name, OptionData.MAX_CHOICES);
                }));
            });
        }
    }
}
