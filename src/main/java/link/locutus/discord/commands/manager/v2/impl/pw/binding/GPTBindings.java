package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import com.openai.models.ChatModel;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.WikiCategory;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.imps.embedding.EmbeddingType;
import link.locutus.discord.gpt.pw.GptLimitTracker;
import link.locutus.discord.gpt.pw.PWGPTHandler;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import net.dv8tion.jda.api.entities.Guild;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GPTBindings extends BindingHelper {
    @Binding(value = "A GPT model name")
    public ChatModel ChatModel(String input) {
        return ChatModel.of(input);
    }

    @Binding(value = "The name of an data set")
    public EmbeddingSource EmbeddingSource(PWGPTHandler handler, @Me Guild guild, String input) {
        Long id = MathMan.isInteger(input) ? Long.parseLong(input) : null;
        Set<EmbeddingSource> sources = handler.getSources(guild, true);
        for (EmbeddingSource source : sources) {
            if (source.source_name.equalsIgnoreCase(input) || (id != null && source.source_id == id)) {
                return source;
            }
        }
        String names = sources.stream().map(f -> f.source_name).collect(Collectors.joining(", "));
        throw new IllegalArgumentException("No source found with name " + input + "\nOptions: " + names);
    }

    @Binding(value = "A comma separated list of data sets")
    public Set<EmbeddingSource> EmbeddingSources(PWGPTHandler handler, @Me Guild guild, String input) {
        Set<String> sourcesStr = StringMan.split(input, ',').stream().map(String::toLowerCase).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> sourceIds = sourcesStr.stream().filter(MathMan::isInteger).map(Long::parseLong).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<EmbeddingSource> sources = new ObjectLinkedOpenHashSet<>();
        for (EmbeddingSource source : sources) {
            if (sourceIds.contains((long) source.source_id) || sourcesStr.contains(source.source_name.toLowerCase())) {
                sources.add(source);
            } else {
                throw new IllegalArgumentException("No source found with name " + source.source_name + "\nOptions: " + sourcesStr);
            }
        }
        return sources;
    }

    @Binding
    public PWGPTHandler PWGPTHandler() {
        return Locutus.imp().getCommandManager().getV2().getGptHandler();
    }

    @Binding(value = "A comma separated list of embedding types")
    public Set<EmbeddingType> EmbeddingType(String input) {
        return emumSet(EmbeddingType.class, input);
    }

    @WikiCategory
    @Binding(value = "A comma separated list of wiki categories")
    public Set<String> includeWikiCategories(String input) {
        throw new IllegalArgumentException("Not implemented (wiki category)");
    }

    @Binding
    @Me
    public GptLimitTracker provider(PWGPTHandler handler, @Me GuildDB db) {
        return handler.getLimitManager().getLimitTracker(db);
    }

    @Binding
    public Map<String, String> map(String input) {
        Map<String, String> map = PW.parseMap(input);
        return map;
    }

    @WikiCategory
    public String wikiCategory(String input) {
        throw new IllegalArgumentException("Not implemented (wiki category)");
    }

    @WikiCategory
    public Set<String> wikiCategories(String input) {
        //  - PWBinding / No completer but add note in the binding to add a completer
        // ^ for the above wiki category as well, need to add the completer, ty
        // linked hash set
        throw new IllegalArgumentException("Not implemented (wiki category list)");
    }
}
