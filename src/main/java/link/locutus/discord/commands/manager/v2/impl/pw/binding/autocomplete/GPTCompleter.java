package link.locutus.discord.commands.manager.v2.impl.pw.binding.autocomplete;

import com.google.gson.reflect.TypeToken;
import com.knuddels.jtokkit.api.ModelType;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.FunctionConsumerParser;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Autocomplete;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.pwembed.GPTProvider;
import link.locutus.discord.gpt.pwembed.PWGPTHandler;
import link.locutus.discord.gpt.pwembed.ProviderType;
import link.locutus.discord.util.StringMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.List;
import java.util.Set;
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

    @Autocomplete
    @Binding(types={GPTProvider.class})
    public List<String> provider(PWGPTHandler handler, @Me GuildDB db, String input) {
        Set<GPTProvider> providers = handler.getProviders(db);
        List<String> providerList = providers.stream().map(f -> f.getId()).collect(Collectors.toList());
        return StringMan.getClosest(input, providerList, f -> f, OptionData.MAX_CHOICES, true);
    }

    //
    {
        Key key = Key.of(TypeToken.getParameterized(Set.class, ProviderType.class).getType(), Autocomplete.class);
        addBinding(store -> {
            store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                return StringMan.autocompleteCommaEnum(ProviderType.class, input.toString(), OptionData.MAX_CHOICES);
            }));
        });
    }
}
