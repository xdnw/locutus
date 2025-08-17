package link.locutus.discord.gpt.pw;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.imps.embedding.EmbeddingType;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ArgumentEmbeddingAdapter extends PWAdapter<Parser> {
    private Map<String, Parser> parsersByName = new ConcurrentHashMap<>();
    public ArgumentEmbeddingAdapter(EmbeddingSource source, Map<Key, Parser> parsers) {
        super(source, new HashSet<>(parsers.values()));
        for (Parser value : parsers.values()) {
            Key key = value.getKey();
            parsersByName.put(key.keyNameMarkdown().toLowerCase(Locale.ROOT), value);
            parsersByName.put(key.toSimpleString().toLowerCase(Locale.ROOT), value);
        }
    }

    public Parser getParser(String name) {
        return parsersByName.get(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public EmbeddingType getType() {
        return EmbeddingType.Argument;
    }

    @Override
    public boolean hasPermission(Parser obj, ValueStore store, CommandManager2 manager) {
        return true;
    }

    @Override
    public String getDescription(Parser parser) {
        return getType() + ": " + parser.getNameDescriptionAndExamples(true, false,false, false);
    }
}
