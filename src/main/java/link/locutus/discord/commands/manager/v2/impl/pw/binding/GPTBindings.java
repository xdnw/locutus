package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import com.knuddels.jtokkit.api.ModelType;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.pwembed.GPTProvider;
import link.locutus.discord.gpt.pwembed.PWGPTHandler;
import link.locutus.discord.gpt.pwembed.ProviderType;
import net.dv8tion.jda.api.entities.Guild;

import java.util.Set;
import java.util.stream.Collectors;

import static link.locutus.discord.commands.manager.v2.binding.BindingHelper.emum;

public class GPTBindings extends BindingHelper {
    @Binding(value = "A GPT model name")
    public ModelType ModelType(String input) {
        return emum(ModelType.class, input);
    }

    @Binding(value = "The name of an data set")
    public EmbeddingSource EmbeddingSource(PWGPTHandler handler, @Me Guild guild, String input) {
        Set<EmbeddingSource> sources = handler.getSources(guild, true);
        for (EmbeddingSource source : sources) {
            if (source.source_name.equalsIgnoreCase(input)) {
                return source;
            }
        }
        String names = sources.stream().map(f -> f.source_name).collect(Collectors.joining(", "));
        throw new IllegalArgumentException("No source found with name " + input + "\nOptions: " + names);
    }

    @Binding
    public PWGPTHandler PWGPTHandler() {
        return Locutus.imp().getCommandManager().getV2().getPwgptHandler();
    }

    @Binding(value = "A comma separated list of provider types")
    public Set<ProviderType> providerTypes(String input) {
        return emumSet(ProviderType.class, input);
    }

    @Binding
    public GPTProvider provider(PWGPTHandler handler, @Me GuildDB db, String input) {
        for (GPTProvider provider : handler.getProviders(db)) {
            if (provider.getId().equalsIgnoreCase(input)) {
                return provider;
            }
        }
        String names = handler.getProviders(db).stream().map(f -> f.getId()).collect(Collectors.joining(", "));
        throw new IllegalArgumentException("No provider found with name " + input + "\nOptions: " + names);
    }
}
