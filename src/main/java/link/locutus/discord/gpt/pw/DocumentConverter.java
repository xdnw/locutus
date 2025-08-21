package link.locutus.discord.gpt.pw;

import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.gpt.GptHandler;
import link.locutus.discord.gpt.ISourceManager;
import link.locutus.discord.gpt.imps.ConvertingDocument;
import link.locutus.discord.gpt.imps.DocumentChunk;
import link.locutus.discord.gpt.imps.embedding.IEmbedding;
import link.locutus.discord.gpt.imps.text2text.IText2Text;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class DocumentConverter {
    private final LimitManager limitManager;
    private final GptHandler handler;
    private final IEmbedding embedding;
    private final IText2Text text2Text;

    public DocumentConverter(LimitManager limitManager, GptHandler handler) {
        this.limitManager = limitManager;
        this.handler = handler;
        this.embedding = handler.getEmbedding();
        this.text2Text = handler.getText2Text();
    }

    public GptHandler getHandler() {
        return handler;
    }

    public LimitManager getLimitManager() {
        return limitManager;
    }

    private String getDocumentPrompt(String documentName) {
        return """
                Please rewrite the page as a list of standalone dot points.
                Include every tiny detail that could be relevant, such as syntax.
                Keep adding points until you have included everything.
                Start all dot points with `- `
                Include any headings within each of its dot points rather than as a separate line. e.g.
                - Heading Name: First Dot point text
                - Heading Name: Second Dot point text
                - Heading Name.Subheading Name: Third Dot point text
                
                # Previous Dot Points:
                ```
                {context}
                ```
                
                # Page:
                ```
                {text}
                ```""".replace("{context}", "Document named `" + documentName + "`\n{context}");
    }

    public String getSummaryPrompt(String prompt, List<String> previousSummary , String text, int maxSummarySize, Function<String, Integer> sizeFunction, Function<String, List<String>> getClosestFacts) {
        int sizeRemaining = maxSummarySize;
        List<String> lines = new ArrayList<>();
        if (getClosestFacts != null) {
            List<String> closestEmbeddings = getClosestFacts.apply(text);
            if (!closestEmbeddings.isEmpty()) {
                int globalRemaining = maxSummarySize / 2;
                for (String line : closestEmbeddings) {
                    line = line.replace("```", "\"\"\"");
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
        String context = StringMan.join(lines, "\n").replace("```", "\"\"\"");
        return prompt.replace("{context}", context).replace("{text}", text.replace("```", "\"\"\""));
    }

    public List<String> chunkTexts(String markdown, GptLimitTracker generator) {
        int totalCap = text2Text.getSizeCap();
        int promptCap = totalCap / 3;
        return GPTUtil.getChunks(markdown, promptCap, text2Text::getSize);
    }

    public List<ConvertingDocument> getDocumentConversions(Guild guild) {
        List<ConvertingDocument> documents = new ArrayList<>();
        for (ConvertingDocument document : handler.getSourcemanager().getUnconvertedDocuments()) {
            EmbeddingSource source = handler.getSourcemanager().getEmbeddingSource(document.source_id);
            if (source != null) {
                documents.add(document);
            }
        }
        return documents;
    }

    public ConvertingDocument createDocumentAndChunks(User user, GuildDB db, String documentName, String markdown, String prompt) {
        EmbeddingSource source = handler.getSourcemanager().getOrCreateSource(documentName, db.getIdLong());
        long hash = embeddings.getHash(markdown);
        if (hash == source.source_hash) {
            throw new IllegalArgumentException("An identical document already exists: `" + documentName + "`. See: " + CM.chat.dataset.view.cmd.source(source.getQualifiedName()));
        }

        ConvertingDocument document = new ConvertingDocument();

        document.source_id = source.source_id;
        if (prompt == null) prompt = getDocumentPrompt(documentName);
        document.prompt = prompt;
        document.converted = false;
        document.use_global_context = true;
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

        System.out.println("Add chunks to document: " + chunks.size() + " chunks");

        getSourceManager().addConvertingDocument(List.of(document));
        getSourceManager().addChunks(chunks);

        this.submitDocument(db, document, true);

        return document;
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
        EmbeddingSource source = getSourceManager().getEmbeddingSource(document.source_id);
        if (source == null) {
            String msg = "Cannot find document source `" + document.source_id + "` in guild " + db.getGuild() + " (was it deleted?)";
            getSourceManager().setDocumentError(document, msg);
            if (throwError) {
                throw new IllegalArgumentException(msg);
            }
            return;
        }
        long guildId = source.guild_id;

        if (guildId == 0) {
            // TODO set error
            String msg = "Document `" + source.getQualifiedName() + "` (`#" + source.source_id + "`) has no assigned guild";
            getSourceManager().setDocumentError(document, msg);
            if (throwError) {
                throw new IllegalArgumentException(msg);
            }
            return;
        }

        Member member = db.getGuild().getMemberById(document.user);
        if (member == null) {
            String msg = "Document submitted by user `" + DiscordUtil.getUserName(document.user) + "` but cannot be found in " + db.getGuild();
            getSourceManager().setDocumentError(document, msg);
            if (throwError) {
                throw new IllegalArgumentException(msg);
            }
            return;
        }

        User user = member.getUser();
        GptLimitTracker provider = getLimitTracker(db, user, document.getProviderType());
        if (provider == null) {
            String msg = "Cannot find provider for document `" + source.getQualifiedName() + "` (`#" + source.source_id + "`) in guild " + db.getGuild();
            getSourceManager().setDocumentError(document, msg);
            if (throwError) {
                throw new IllegalArgumentException(msg);
            }
            return;
        }

        DBNation nation = DiscordUtil.getNation(user);
        if (nation == null) {
            String msg = "Cannot find nation for user `" + user.getName() + "` in guild " + db.getGuild();
            getSourceManager().setDocumentError(document, msg);
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
            List<DocumentChunk> chunks = getSourceManager().getChunks(document.source_id);
            List<String> outputSplit = chunks.stream().filter(f -> f.converted).flatMap(f -> f.getOutputList().stream()).toList();
            chunks.removeIf(f -> f.converted);
            if (chunks.isEmpty()) {
                setConverted(document);
                return;
            }

            Boolean status = conversionStatus.get(document.source_id);
            if (status == Boolean.FALSE) {
                getSourceManager().setDocumentErrorIfAbsent(document, "Document conversion manually cancelled");
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
                    provider.getSizeCap() * 2 / 4,
                    provider::getSize,
                    getClosestFacts
            );

            boolean isLastChunk = chunks.size() == 1;

            try {
                CompletableFuture<String> future = provider.submit(db, user, nation, null, text);

                future.thenAcceptAsync(s -> {
                    synchronized (lock) {
                        chunk.output = MarkupUtil.unescapeMarkdown(s);
                        chunk.converted = true;
                        getSourceManager().addChunks(List.of(chunk));

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
                        getSourceManager().setDocumentError(document, e.getMessage());
                        conversionStatus.remove(document.source_id);
                        return null;
                    }
                });

            } catch (Throwable e) {
                getSourceManager().setDocumentError(document, e.getMessage());
                conversionStatus.remove(document.source_id);
            }
        }
    }

    public void pauseConversion(ConvertingDocument document, String reason) {
        conversionStatus.put(document.source_id, false);
        document.error = "Paused: " + reason;
        getSourceManager().addConvertingDocument(List.of(document));
    }

    public String resumeConversion(GuildDB db, ConvertingDocument document) {
        // conversionStatus remove if false
        boolean removed = conversionStatus.remove(document.source_id, false);
        String existingError = document.error;
        document.error = null;
        getSourceManager().addConvertingDocument(List.of(document));
        submitDocument(db, document, true);
        return existingError;
    }

    private void setConverted(ConvertingDocument document) {
        document.converted = true;
        getSourceManager().addConvertingDocument(List.of(document));
        // get or create source
        EmbeddingSource source = getSourceManager().getEmbeddingSource(document.source_id);
        if (source != null) {
            // get chunks
            List<DocumentChunk> chunks = getSourceManager().getChunks(document.source_id);
            List<String> facts = new ArrayList<>();
            for (DocumentChunk chunk : chunks) {
                if (chunk.output == null) continue;
                facts.addAll(chunk.getOutputList());
            }
            handler.registerEmbeddings(source, facts, false, true);
            source.source_hash = document.hash;
            getSourceManager().updateSources(List.of(source));
        } else {
            System.out.println("Cannot find source for document `#" + document.source_id + "`");
        }

        getSourceManager().deleteDocumentAndChunks(document.source_id);
    }

    public void initDocumentConversion(GuildDB db) {
        List<ConvertingDocument> docs = getDocumentConversions(db.getGuild());
        for (ConvertingDocument doc : docs) {
            if (doc.converted || doc.error != null) continue;
            submitDocument(db, doc, false);
        }
    }
}
