package link.locutus.discord.gpt.pw;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.gpt.GptHandler;
import link.locutus.discord.gpt.IEmbeddingDatabase;
import link.locutus.discord.gpt.IModerator;
import link.locutus.discord.gpt.imps.ConvertingDocument;
import link.locutus.discord.gpt.imps.DocumentChunk;
import link.locutus.discord.gpt.imps.IText2Text;
import link.locutus.discord.gpt.imps.ProviderType;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DocumentConverter {
    private final IEmbeddingDatabase embeddings;
    private final ProviderManager providerManager;
    private final IModerator moderator;

    public DocumentConverter(IEmbeddingDatabase embeddings, ProviderManager providerManager, IModerator moderator) {
        this.embeddings = embeddings;
        this.providerManager = providerManager;
        this.moderator = moderator;
    }

    public IEmbeddingDatabase getEmbeddings() {
        return embeddings;
    }

    private String getDocumentPrompt() {
        return """
        # Goal
        You will be provided both `context` and `text` from a user guide for the game Politics And War.\s
        Take the information in `text` and organize it into dot points of standalone factual knowledge (start each line with `- `).\s
        Use the `context` solely to understand `text` but do not create facts solely from it.
        Do not make anything up.\s
        Preserve syntax, formulas and precision.
                        
        # Context:
        {context}
                        
        # Text:
        {text}
                        
        # Fact summary:""";
    }

    public String getSummaryPrompt(String prompt, List<String> previousSummary , String text, int maxSummarySize, Function<String, Integer> sizeFunction, Function<String, List<String>> getClosestFacts) {
        int sizeRemaining = maxSummarySize;
        List<String> lines = new ArrayList<>();
        if (getClosestFacts != null) {
            List<String> closestEmbeddings = getClosestFacts.apply(text);
            if (!closestEmbeddings.isEmpty()) {
                int globalRemaining = maxSummarySize / 2;
                for (String line : closestEmbeddings) {
                    int size = sizeFunction.apply(line);
                    if (size > globalRemaining) break;
                    globalRemaining -= size;
                    sizeRemaining -= size;
                    lines.add(line);
                }
            }
        }

        for (int i = previousSummary.size() - 1; i >= 0; i--) {
            String line = previousSummary.get(i);
            int size = sizeFunction.apply(line);
            if (size > sizeRemaining) break;
            sizeRemaining -= size;
            lines.add(line);
        }
        // reverse lines
        Collections.reverse(lines);
        String context = StringMan.join(lines, "\n");
        return prompt.replace("{context}", context).replace("{text}", text);
    }

    public List<String> chunkTexts(String markdown, GPTProvider generator) {
        int totalCap = generator.getSizeCap();
        int promptCap = totalCap / 3;
        return GPTUtil.getChunks(markdown, promptCap, generator::getSize);
    }

    public List<ConvertingDocument> getDocumentConversions(Guild guild) {
        List<ConvertingDocument> documents = new ArrayList<>();
        for (ConvertingDocument document : getEmbeddings().getUnconvertedDocuments()) {
            EmbeddingSource source = getEmbeddings().getEmbeddingSource(guild.getIdLong(), document.source_id);
            if (source != null) {
                documents.add(document);
            }
        }
        return documents;
    }

    public ConvertingDocument createDocumentAndChunks(User user, GuildDB db, String documentName, String markdown, String prompt, ProviderType type) {
        GPTProvider generator = getGenerator(db, user, type);

        ConvertingDocument document = new ConvertingDocument();

        EmbeddingSource source = getEmbeddings().getOrCreateSource(documentName, db.getIdLong());

        document.source_id = source.source_id;
        if (prompt == null) prompt = getDocumentPrompt();
        document.prompt = prompt;
        document.converted = false;
        document.use_global_context = true;
        document.provider_type = type.ordinal();
        document.user = user.getIdLong();
        document.error = null;
        document.date = System.currentTimeMillis();

        List<String> chunkTexts = chunkTexts(markdown, generator);

        List<DocumentChunk> chunks = new ArrayList<>();
        for (int i = 0; i < chunkTexts.size(); i++) {
            String text = chunkTexts.get(i);
            GPTUtil.checkThrowModeration(moderator.moderate(text), text);
            DocumentChunk chunk = new DocumentChunk();
            chunk.source_id = source.source_id;
            chunk.chunk_index = i;
            chunk.converted = false;
            chunk.text = text;
            chunks.add(chunk);
        }

        getEmbeddings().addConvertingDocument(List.of(document));

        this.submitDocument(db, document, true);

        return document;
    }

    private GPTProvider getGenerator(GuildDB db, User user, ProviderType type) {
        return providerManager.getProvider(db, user, Collections.singleton(type));
    }

    private ConcurrentHashMap<Integer, Object> conversionLocks = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, Boolean> conversionStatus = new ConcurrentHashMap<>();

    private void submitDocument(GuildDB db, ConvertingDocument document, boolean throwError) {
        if (document.converted) {
            if (throwError){
                throw new IllegalArgumentException("Document `#" + document.source_id + "` is already converted");
            }
            return;
        }
        if (document.error != null) {
            if (throwError) throw new IllegalArgumentException(document.error);
            return;
        }
        EmbeddingSource source = getEmbeddings().getEmbeddingSource(db.getIdLong(), document.source_id);
        if (source == null) {
            String msg = "Cannot find document source `" + document.source_id + "` in guild " + db.getGuild() + " (was it deleted?)";
            document.error = msg;
            getEmbeddings().addConvertingDocument(List.of(document));
            if (throwError) {
                throw new IllegalArgumentException(msg);
            }
            return;
        }
        long guildId = source.guild_id;

        if (guildId == 0) {
            // TODO set error
            String msg = "Document `" + source.getQualifiedName() + "` (`#" + source.source_id + "`) has no assigned guild";
            document.error = msg;
            getEmbeddings().addConvertingDocument(List.of(document));
            if (throwError) {
                throw new IllegalArgumentException(msg);
            }
            return;
        }

        Member member = db.getGuild().getMemberById(document.user);
        if (member == null) {
            String msg = "Document submitted by user `" + DiscordUtil.getUserName(document.user) + "` but cannot be found in " + db.getGuild();
            document.error = msg;
            if (throwError) {
                throw new IllegalArgumentException(msg);
            }
            return;
        }

        GPTProvider provider = getGenerator(db, member.getUser(), document.getProviderType());
        if (provider == null) {
            String msg = "Cannot find provider for document `" + source.getQualifiedName() + "` (`#" + source.source_id + "`) in guild " + db.getGuild();
            document.error = msg;
            getEmbeddings().addConvertingDocument(List.of(document));
            if (throwError) {
                throw new IllegalArgumentException(msg);
            }
            return;
        }

        Object lock = conversionLocks.computeIfAbsent(document.source_id, k -> new Object());

        synchronized (lock) {
            if (document.converted) {
                if (throwError) {
                    throw new IllegalArgumentException("Document `#" + document.source_id + "` is already converted");
                }
                return;
            }
            List<DocumentChunk> chunks = getEmbeddings().getChunks(document.source_id);
            List<String> outputSplit = chunks.stream().filter(f -> f.converted).flatMap(f -> Arrays.stream(f.output.split("\n[ ]+-"))).toList();
            chunks.removeIf(f -> f.converted);
            if (chunks.isEmpty()) {
                document.converted = true;
                getEmbeddings().addConvertingDocument(List.of(document));
                return;
            }

            Boolean status = conversionStatus.get(document.source_id);
            if (status == Boolean.FALSE) {
                if (document.error == null) {
                    document.error = "Document conversion manually cancelled";
                }
                getEmbeddings().addConvertingDocument(List.of(document));
                if (throwError) {
                    throw new IllegalArgumentException(document.error);
                }
                return;
            }
            if (status == Boolean.TRUE) {
                if (throwError) {
                    throw new IllegalArgumentException("Document `#" + document.source_id + "` is already being converted");
                }
                return;
            }
            // put status true
            conversionStatus.put(document.source_id, true);

            DocumentChunk chunk = chunks.get(0);

            Function<String, List<String>> getClosestFacts = new Function<String, List<String>>() {
                @Override
                public List<String> apply(String s) {
                    return new ArrayList<>();
                }
            };

            // public String getSummaryPrompt(String prompt, List<String> previousSummary , String text, int maxSummarySize, Function<String, Integer> sizeFunction, Function<String, List<String>> getClosestFacts) {
            String text = getSummaryPrompt(document.prompt,
                    outputSplit,
                    chunk.text,
                    provider.getSizeCap() * 2 / 3,
                    provider::getSize,
                    getClosestFacts
            );

            // schedule task
            {
                // move this to the task
                // TODO add guards for already running

                conversionStatus.put(document.source_id, true);

                // try finally, removing conversion status
            }
        }


        // submit chunks
        // if all chunks are submitted, mark document as submitted
    }

    public void initDocumentConversion(GuildDB db) {
        List<ConvertingDocument> docs = getDocumentConversions(db.getGuild());

        // If doc is unfinished

        // add these documents to the queue

        // while free, submit
    }
}
