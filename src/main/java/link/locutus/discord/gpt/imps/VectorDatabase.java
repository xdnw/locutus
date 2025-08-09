package link.locutus.discord.gpt.imps;


import ai.djl.MalformedModelException;
import ai.djl.repository.zoo.ModelNotFoundException;
import link.locutus.discord.gpt.imps.embedding.IEmbedding;
import link.locutus.discord.gpt.imps.embedding.LocalEmbedding;
import link.locutus.discord.util.StringMan;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.codecs.lucene101.Lucene101Codec;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


public class VectorDatabase {
    private final Directory directory;
    private final AtomicLong searcherDirty = new AtomicLong(0);
    private volatile boolean uncomitted = false;
    private final int ramMb;
    private final SearcherManager searcherManager;
    private final IndexWriter indexWriter;

    private volatile boolean closed = false;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "VectorDB-Maintenance");
        t.setDaemon(true);
        return t;
    });

    public VectorDatabase(Path indexPath, int ramMb) throws IOException {
        this.ramMb = ramMb;
        this.directory = FSDirectory.open(indexPath);
        this.indexWriter = new IndexWriter(directory, createConfig());
        this.searcherManager = new SearcherManager(indexWriter, null);

        // Schedule periodic commit and refresh every 30 seconds
        scheduler.scheduleAtFixedRate(() -> {
            if (!closed) {
                try {
                    indexWriter.commit();
                    refresh();
                } catch (IOException e) {
                    // Log error but don't stop the scheduler
                    System.err.println("Error during periodic maintenance: " + e.getMessage());
                }
            }
        }, 3, 2, TimeUnit.MINUTES);
    }

    public List<SearchResult> getAllDocuments() throws IOException {
        refresh();
        IndexSearcher searcher = searcherManager.acquire();
        try {
            // Use MatchAllDocsQuery to get all documents
            Query allDocsQuery = new MatchAllDocsQuery();
            TopDocs results = searcher.search(allDocsQuery, Integer.MAX_VALUE);
            return processResults(searcher, results);
        } finally {
            searcherManager.release(searcher);
        }
    }

    private IndexWriterConfig createConfig() {
        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        // Configure HNSW parameters
        config.setCodec(new Lucene101Codec(Lucene101Codec.Mode.BEST_SPEED));
        config.setRAMBufferSizeMB(ramMb); // Increase RAM buffer
        config.setMaxBufferedDocs(10000); // Batch more docs

        TieredMergePolicy mergePolicy = new TieredMergePolicy();
        mergePolicy.setMaxMergeAtOnce(30);
        mergePolicy.setSegmentsPerTier(30);
        config.setMergePolicy(mergePolicy);

        return config;
    }

    public void addDocument(String text, float[] vector) throws IOException {
        addDocument(text, vector, null);
    }

    public void addDocumentIfNotExists(String text, float[] vector, String metadata) throws IOException {
        if (!documentExists(text)) {
            addDocument(text, vector, metadata);
        }
    }

    public void addDocument(String text, float[] vector, String metadata) throws IOException {
        Document doc = createDocument(text, vector, metadata);
        indexWriter.addDocument(doc);
        searcherDirty.incrementAndGet();
    }

    public void addDocuments(List<DocumentEntry> entries) throws IOException {
        for (DocumentEntry entry : entries) {
            Document doc = createDocument(entry.text, entry.vector, entry.metadata);
            indexWriter.addDocument(doc);
        }
        searcherDirty.incrementAndGet();
    }

    public List<SearchResult> searchSimilar(float[] queryVector, int topK) throws IOException {
        refresh();
        IndexSearcher searcher = searcherManager.acquire();
        try {
            Query knnQuery = KnnFloatVectorField.newVectorQuery("vector", queryVector, topK);
            TopDocs results = searcher.search(knnQuery, topK);
            return processResults(searcher, results);
        } finally {
            searcherManager.release(searcher);
        }
    }

    private List<SearchResult> processResults(IndexSearcher searcher, TopDocs results) throws IOException {
        List<SearchResult> searchResults = new ArrayList<>();
        for (ScoreDoc scoreDoc : results.scoreDocs) {
            Document doc = searcher.storedFields().document(scoreDoc.doc);
            SearchResult result = new SearchResult(
                    doc.get("text"),
                    doc.get("metadata"),
                    scoreDoc.score,
                    doc.get("id")
            );
            searchResults.add(result);
        }
        return searchResults;
    }

    public List<SearchResult> searchByText(String query, int maxResults) throws IOException, ParseException {
        Query textQuery = new org.apache.lucene.queryparser.classic.QueryParser("text", new StandardAnalyzer())
                .parse(query);
        refresh();
        IndexSearcher searcher = searcherManager.acquire();
        try {
            TopDocs results = searcher.search(textQuery, maxResults);

            List<SearchResult> searchResults = new ArrayList<>();
            for (ScoreDoc scoreDoc : results.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                SearchResult result = new SearchResult(
                        doc.get("text"),
                        doc.get("metadata"),
                        scoreDoc.score,
                        doc.get("id")
                );
                searchResults.add(result);
            }
            return searchResults;
        } finally {
            searcherManager.release(searcher);
        }
    }

    public void deleteDocument(String text) throws IOException {
        long hash = StringMan.hash(text);
        indexWriter.deleteDocuments(LongPoint.newExactQuery("id", hash));
        searcherDirty.incrementAndGet();
    }

    public void close() throws IOException {
        closed = true;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        indexWriter.commit();
        indexWriter.close();
        searcherManager.close();
        directory.close();
    }

    private Document createDocument(String text, float[] vector, String metadata) {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField("vector", vector, VectorSimilarityFunction.COSINE));
        doc.add(new TextField("text", text, Field.Store.YES));
        long id = StringMan.hash(text);

        // Numeric indexable field (not stored)
        doc.add(new LongPoint("id", id));
        // Stored copy so you can read it back from search results
        doc.add(new StoredField("id", id)); // same name is fine; Lucene allows multiple fields with same name

        if (metadata != null) {
            doc.add(new StringField("metadata", metadata, Field.Store.YES));
        }
        return doc;
    }

    public boolean documentExists(String text) throws IOException {
        long id = StringMan.hash(text);
        Query query = LongPoint.newExactQuery("id", id);
        refresh();
        IndexSearcher searcher = searcherManager.acquire();
        try {
            return searcher.count(query) > 0;
        } finally {
            searcherManager.release(searcher);
        }
    }

    public void addOrUpdateDocument(String text, float[] vector, String metadata) throws IOException {
        long id = StringMan.hash(text);
        Document doc = createDocument(text, vector, metadata);
        indexWriter.deleteDocuments(LongPoint.newExactQuery("id", id));
        indexWriter.addDocument(doc);
        searcherDirty.incrementAndGet();
    }

    private void refreshSearcher() throws IOException {
        searcherManager.maybeRefreshBlocking();
    }

    public void refresh() throws IOException {
        long val = searcherDirty.get();
        if (val > 0) {
            uncomitted = true;
            refreshSearcher();
            searcherDirty.compareAndSet(val, 0);
        }
    }

    // Helper classes
    public static class DocumentEntry {
        public final String text;
        public final float[] vector;
        public final String metadata;

        public DocumentEntry(String text, float[] vector, String metadata) {
            this.text = text;
            this.vector = vector;
            this.metadata = metadata;
        }

        public DocumentEntry(String text, float[] vector) {
            this(text, vector, null);
        }
    }

    public static class SearchResult {
        public final String text;
        public final String metadata;
        public final float score;
        public final String id;

        public SearchResult(String text, String metadata, float score, String id) {
            this.text = text;
            this.metadata = metadata;
            this.score = score;
            this.id = id;
        }

        @Override
        public String toString() {
            return String.format("SearchResult{text='%s', score=%.3f, id='%s'}", text, score, id);
        }
    }

    public static void main(String[] args) throws IOException, ParseException, SQLException, ModelNotFoundException, MalformedModelException, ClassNotFoundException {
        long start = System.currentTimeMillis();
        IEmbedding embedding = new LocalEmbedding("sentence-transformers/all-MiniLM-L6-v2");
        System.out.println("Model created " + (-start + (start = System.currentTimeMillis())) + " ms");
        embedding.init();
        System.out.println("Embedding initialized " + (-start + (start = System.currentTimeMillis())) + " ms");

        Path indexPath = Path.of("mydb.idx");
        Files.createDirectories(indexPath);
        VectorDatabase db = new VectorDatabase(indexPath, 512); // 512 MB RAM buffer

        System.out.println("Vector DB initialized " + (-start + (start = System.currentTimeMillis())) + " ms");

        // 20 distinct sentences
        List<String> sentences = Arrays.asList(
                "The quick brown fox jumps over the lazy dog.",
                "Lucene is a powerful search library.",
                "Java is a versatile programming language.",
                "Artificial intelligence is transforming industries.",
                "The weather today is sunny and bright.",
                "Cats are curious creatures.",
                "Mountains are beautiful in the spring.",
                "Music brings people together.",
                "Reading books expands the mind.",
                "Technology evolves rapidly.",
                "Coffee is a popular morning beverage.",
                "Traveling opens new horizons.",
                "Education is the key to success.",
                "Healthy eating improves wellbeing.",
                "Sports teach teamwork and discipline.",
                "Art inspires creativity.",
                "History helps us understand the present.",
                "Friendship enriches life.",
                "Coding challenges are fun.",
                "Nature is full of wonders."
        );

        // Add sentences to the database
        for (String sentence : sentences) {
            db.addOrUpdateDocument(sentence, embedding.fetchEmbedding(sentence), null);
        }

        System.out.println("Sentences added " + (-start + (start = System.currentTimeMillis())) + " ms");

        // Vector search: use embedding of a test sentence
        float[] queryVec = embedding.fetchEmbedding("Technology evolves rapidly.");
        List<VectorDatabase.SearchResult> vectorResults = db.searchSimilar(queryVec, 3);

        System.out.println("Sentences searched " + (-start + (start = System.currentTimeMillis())) + " ms");

        System.out.println("Top 3 vector search results for 'Technology evolves rapidly.':");
        for (VectorDatabase.SearchResult result : vectorResults) {
            System.out.println(result);
        }

        System.out.println("------------------------------------");

        // Text search: search for "technology"
        List<VectorDatabase.SearchResult> textResults = db.searchByText("technology", 3);

        System.out.println("Sentences searched (2) " + (-start + (start = System.currentTimeMillis())) + " ms");

        System.out.println("\nTop 3 text search results for 'technology':");
        for (VectorDatabase.SearchResult result : textResults) {
            System.out.println(result);
        }

//        System.out.println("------------------------------------");
//        // get all records
//        List<VectorDatabase.SearchResult> allDocs = db.getAllDocuments();
//        System.out.println("All documents in the database:");
//        for (VectorDatabase.SearchResult result : allDocs) {
//            System.out.println(result);
//        }

        // check document existence
        System.out.println("------------------------------------");
        for (String sentence : sentences) {
            boolean exists = db.documentExists(sentence);
            System.out.println("Document exists for '" + sentence + "': " + exists);
        }

        db.close();

    }
}