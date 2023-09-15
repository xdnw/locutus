package link.locutus.discord.gpt.pw;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.gpt.GptHandler;
import link.locutus.discord.gpt.IEmbeddingDatabase;
import link.locutus.discord.gpt.IModerator;
import link.locutus.discord.gpt.imps.ConvertingDocument;
import link.locutus.discord.gpt.imps.DocumentChunk;
import link.locutus.discord.gpt.imps.EmbeddingType;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DocumentConverter {
    private final IEmbeddingDatabase embeddings;
    private final ProviderManager providerManager;
    private final IModerator moderator;
    private final GptHandler handler;

    public DocumentConverter(IEmbeddingDatabase embeddings, ProviderManager providerManager, IModerator moderator, GptHandler handler) {
        this.embeddings = embeddings;
        this.providerManager = providerManager;
        this.moderator = moderator;
        this.handler = handler;
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
            EmbeddingSource source = getEmbeddings().getEmbeddingSource(document.source_id);
            if (source != null) {
                documents.add(document);
            }
        }
        return documents;
    }

    public ConvertingDocument createDocumentAndChunks(User user, GuildDB db, String documentName, String markdown, String prompt, ProviderType type) {
        GPTProvider generator = getGenerator(db, user, type);

        EmbeddingSource source = getEmbeddings().getOrCreateSource(documentName, db.getIdLong());
        long hash = embeddings.getHash(markdown);
        if (hash == source.source_hash) {
            throw new IllegalArgumentException("An identical document already exists: `" + documentName + "`. See: " + CM.chat.dataset.view.cmd.create(source.getQualifiedName(), null, null));
        }

        ConvertingDocument document = new ConvertingDocument();

        document.source_id = source.source_id;
        if (prompt == null) prompt = getDocumentPrompt();
        document.prompt = prompt;
        document.converted = false;
        document.use_global_context = true;
        document.provider_type = type.ordinal();
        document.user = user.getIdLong();
        document.error = null;
        document.date = System.currentTimeMillis();
        document.hash = hash;

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
        getEmbeddings().addChunks(chunks);

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
        EmbeddingSource source = getEmbeddings().getEmbeddingSource(document.source_id);
        if (source == null) {
            String msg = "Cannot find document source `" + document.source_id + "` in guild " + db.getGuild() + " (was it deleted?)";
            getEmbeddings().setDocumentError(document, msg);
            if (throwError) {
                throw new IllegalArgumentException(msg);
            }
            return;
        }
        long guildId = source.guild_id;

        if (guildId == 0) {
            // TODO set error
            String msg = "Document `" + source.getQualifiedName() + "` (`#" + source.source_id + "`) has no assigned guild";
            getEmbeddings().setDocumentError(document, msg);
            if (throwError) {
                throw new IllegalArgumentException(msg);
            }
            return;
        }

        Member member = db.getGuild().getMemberById(document.user);
        if (member == null) {
            String msg = "Document submitted by user `" + DiscordUtil.getUserName(document.user) + "` but cannot be found in " + db.getGuild();
            getEmbeddings().setDocumentError(document, msg);
            if (throwError) {
                throw new IllegalArgumentException(msg);
            }
            return;
        }

        User user = member.getUser();
        GPTProvider provider = getGenerator(db, user, document.getProviderType());
        if (provider == null) {
            String msg = "Cannot find provider for document `" + source.getQualifiedName() + "` (`#" + source.source_id + "`) in guild " + db.getGuild();
            getEmbeddings().setDocumentError(document, msg);
            if (throwError) {
                throw new IllegalArgumentException(msg);
            }
            return;
        }

        DBNation nation = DiscordUtil.getNation(user);
        if (nation == null) {
            String msg = "Cannot find nation for user `" + user.getName() + "` in guild " + db.getGuild();
            getEmbeddings().setDocumentError(document, msg);
            if (throwError) {
                throw new IllegalArgumentException(msg);
            }
            return;
        }

        if (document.error != null) {
            if (throwError) throw new IllegalArgumentException(document.error);
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
            List<String> outputSplit = chunks.stream().filter(f -> f.converted).flatMap(f -> f.getOutputList().stream()).toList();
            chunks.removeIf(f -> f.converted);
            if (chunks.isEmpty()) {
                setConverted(document);
                return;
            }

            Boolean status = conversionStatus.get(document.source_id);
            if (status == Boolean.FALSE) {
                getEmbeddings().setDocumentErrorIfAbsent(document, "Document conversion manually cancelled");
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

            String text = getSummaryPrompt(document.prompt,
                    outputSplit,
                    chunk.text,
                    provider.getSizeCap() * 2 / 3,
                    provider::getSize,
                    getClosestFacts
            );

            boolean isLastChunk = chunks.size() == 1;

            try {
                CompletableFuture<String> future = provider.submit(db, user, nation, null, text);

                future.thenAcceptAsync(s -> {
                    synchronized (lock) {
                        chunk.output = s;
                        chunk.converted = true;
                        conversionStatus.remove(document.source_id);
                        if (isLastChunk) {
                            setConverted(document);
                        } else {
                            submitDocument(db, document, false);
                        }
                    }
                }).exceptionally(e -> {
                    e.printStackTrace();
                    synchronized (lock) {
                        getEmbeddings().setDocumentError(document, e.getMessage());
                        conversionStatus.remove(document.source_id);
                        return null;
                    }
                });

            } catch (Throwable e) {
                getEmbeddings().setDocumentError(document, e.getMessage());
                conversionStatus.remove(document.source_id);
            }
        }
    }

    public void pauseConversion(ConvertingDocument document, String reason) {
        conversionStatus.put(document.source_id, false);
        document.error = "Paused: " + reason;
        getEmbeddings().addConvertingDocument(List.of(document));
    }

    public String resumeConversion(GuildDB db, ConvertingDocument document) {
        // conversionStatus remove if false
        boolean removed = conversionStatus.remove(document.source_id, false);
        String existingError = document.error;
        document.error = null;
        getEmbeddings().addConvertingDocument(List.of(document));
        submitDocument(db, document, true);
        return existingError;
    }

    private void setConverted(ConvertingDocument document) {
        document.converted = true;
        getEmbeddings().addConvertingDocument(List.of(document));
        // get or create source
        EmbeddingSource source = getEmbeddings().getEmbeddingSource(document.source_id);
        if (source != null) {
            // get chunks
            List<DocumentChunk> chunks = getEmbeddings().getChunks(document.source_id);
            List<String> facts = new ArrayList<>();
            for (DocumentChunk chunk : chunks) {
                if (chunk.output == null) continue;
                facts.addAll(chunk.getOutputList());
            }
            handler.registerEmbeddings(source, facts, null, false, true);
            source.source_hash = document.hash;
            getEmbeddings().updateSources(List.of(source));
        } else {
            System.out.println("Cannot find source for document `#" + document.source_id + "`");
        }

        getEmbeddings().deleteDocumentAndChunks(document.source_id);
    }

    public void initDocumentConversion(GuildDB db) {
        List<ConvertingDocument> docs = getDocumentConversions(db.getGuild());
        for (ConvertingDocument doc : docs) {
            if (doc.converted || doc.error != null) continue;
            submitDocument(db, doc, false);
        }
    }
}
