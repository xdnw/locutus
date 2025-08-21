package link.locutus.discord.gpt.imps;


import link.locutus.discord.config.Settings;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.scheduler.KeyValue;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.codecs.lucene101.Lucene101Codec;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;


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

    public Iterable<SearchResult> getAllDocuments() {
        try {
            refresh();
            IndexSearcher searcher = searcherManager.acquire();
            try {
                Query allDocsQuery = new MatchAllDocsQuery();
                TopDocs results = searcher.search(allDocsQuery, Integer.MAX_VALUE);
                return iterateResults(searcher, results);
            } finally {
                searcherManager.release(searcher);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve all documents", e);
        }
    }

    public Iterable<SearchResult> getDocumentsBySource(int sourceId) {
        try {
            refresh();
            IndexSearcher searcher = searcherManager.acquire();
            try {
                Query srcQuery = IntPoint.newExactQuery("src", sourceId);
                TopDocs results = searcher.search(srcQuery, Integer.MAX_VALUE);
                return iterateResults(searcher, results);
            } finally {
                searcherManager.release(searcher);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve all documents", e);
        }
    }

    public void deleteMissing(int sourceId, Set<Long> idSet) {
        try {
            Query srcQuery = IntPoint.newExactQuery("src", sourceId);

            if (idSet == null || idSet.isEmpty()) {
                // No keep-list -> delete all docs for this source
                indexWriter.deleteDocuments(srcQuery);
            } else {
                // Delete docs for this source whose id is NOT in the provided set
                long[] ids = idSet.stream().mapToLong(Long::longValue).toArray();
                Query idsInSet = LongPoint.newSetQuery("id", ids);

                BooleanQuery deleteQuery = new BooleanQuery.Builder()
                        .add(srcQuery, BooleanClause.Occur.MUST)
                        .add(idsInSet, BooleanClause.Occur.MUST_NOT)
                        .build();

                indexWriter.deleteDocuments(deleteQuery);
            }
            searcherDirty.incrementAndGet();
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete missing documents", e);
        }
    }

    public Iterable<SearchResult> getDocumentsBySource(Set<Integer> sourceIds) {
        if (sourceIds.isEmpty()) return Collections.emptyList();
        if (sourceIds.size() == 1) {
            return getDocumentsBySource(sourceIds.iterator().next());
        }
        try {
            refresh();
            IndexSearcher searcher = searcherManager.acquire();
            try {
                Query srcQuery = IntPoint.newSetQuery("src", sourceIds);
                TopDocs results = searcher.search(srcQuery, Integer.MAX_VALUE);
                return iterateResults(searcher, results);
            } finally {
                searcherManager.release(searcher);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve all documents", e);
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

    public void addDocument(String text, Long hash, float[] vector) {
        addDocument(text, hash, vector, null);
    }

    public void addDocumentIfNotExists(String text, Long hash, float[] vector, @Nullable Integer sourceId) {
        if (!documentExists(text)) {
            addDocument(text, hash, vector, sourceId);
        }
    }

    public void addDocumentIfNotExists(String text, Long hash, Supplier<float[]> vectorSupplier, @Nullable Integer sourceId) {
        if (!documentExists(text)) {
            addDocument(text, hash, vectorSupplier.get(), sourceId);
        }
    }

    public void addDocument(String text, @Nullable Long hash, float[] vector, @Nullable Integer sourceId) {
        Document doc = createDocument(text, hash, vector, sourceId);
        try {
        indexWriter.addDocument(doc);
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve all documents", e);
        }
        searcherDirty.incrementAndGet();
    }

    public void addDocuments(List<DocumentEntry> entries) {
        try {
        for (DocumentEntry entry : entries) {
            Document doc = createDocument(entry.text, entry.hash, entry.vector, entry.sourceId);
            indexWriter.addDocument(doc);
        }
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve all documents", e);
        }
        searcherDirty.incrementAndGet();
    }

    public Iterable<SearchResult> searchSimilar(float[] queryVector, int topK, Set<Integer> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return searchSimilar(queryVector, topK);
        }
        try {
            refresh();
            IndexSearcher searcher = searcherManager.acquire();
            try {
                Query knnQuery = KnnFloatVectorField.newVectorQuery("vector", queryVector, topK);
                Query srcFilter = IntPoint.newSetQuery("src", sourceIds);

                BooleanQuery combined = new BooleanQuery.Builder()
                        .add(knnQuery, BooleanClause.Occur.MUST)
                        .add(srcFilter, BooleanClause.Occur.FILTER)
                        .build();

                TopDocs results = searcher.search(combined, topK);
                return iterateResults(searcher, results);
            } finally {
                searcherManager.release(searcher);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to search similar with source filter", e);
        }
    }

    public Iterable<SearchResult> searchSimilar(float[] queryVector, int topK) {
        try {
            refresh();
            IndexSearcher searcher = searcherManager.acquire();
            try {
                Query knnQuery = KnnFloatVectorField.newVectorQuery("vector", queryVector, topK);
                TopDocs results = searcher.search(knnQuery, topK);
                return iterateResults(searcher, results);
            } finally {
                searcherManager.release(searcher);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve all documents", e);
        }
    }

    /**
     * KNN with post-filtering via a custom predicate. Over-fetches and trims to topK.
     * Note: true pre-filtering must be done with indexed fields (Lucene Query).
     */
    public List<SearchResult> searchSimilar(
            float[] queryVector,
            int topK,
            @Nullable Set<Integer> sourceIds,
            Predicate<SearchResult> predicate
    ) {
        // Reasonable defaults: start with 4x and cap growth to avoid runaway fetches
        return searchSimilar(queryVector, topK, sourceIds, predicate, Math.max(topK, topK * 4), 50_000);
    }

    /**
     * Overload with explicit fetch controls.
     *
     * @param initialFetch starting fetch size (>= topK)
     * @param maxFetch upper bound for fetch size
     */
    public List<SearchResult> searchSimilar(
            float[] queryVector,
            int topK,
            @Nullable Set<Integer> sourceIds,
            Predicate<SearchResult> predicate,
            int initialFetch,
            int maxFetch
    ) {
        Objects.requireNonNull(predicate, "predicate");

        int fetch = Math.max(topK, initialFetch);
        List<SearchResult> out = new ArrayList<>(topK);

        try {
            refresh();
            IndexSearcher searcher = searcherManager.acquire();
            try {
                while (true) {
                    Query knnQuery = KnnFloatVectorField.newVectorQuery("vector", queryVector, fetch);
                    if (sourceIds != null) {
                        Query srcFilter = IntPoint.newSetQuery("src", sourceIds);
                        knnQuery = new BooleanQuery.Builder()
                                .add(knnQuery, BooleanClause.Occur.MUST)
                                .add(srcFilter, BooleanClause.Occur.FILTER)
                                .build();
                    }

                    TopDocs results = searcher.search(knnQuery, fetch);

                    out.clear();
                    for (ScoreDoc sd : results.scoreDocs) {
                        Document doc = searcher.storedFields().document(sd.doc);
                        SearchResult sr = new SearchResult(
                                doc.get("text"),
                                doc.getField("src"),
                                sd.score,
                                doc.getField("id")
                        );
                        if (predicate.test(sr)) {
                            out.add(sr);
                            if (out.size() >= topK) break; // enough matches in score order
                        }
                    }

                    if (out.size() >= topK) break; // satisfied
                    if (results.scoreDocs.length < fetch) break; // no more candidates available
                    if (fetch >= maxFetch) break; // hit safety cap

                    // grow fetch size and try again
                    fetch = Math.min(maxFetch, Math.max(fetch + topK, fetch * 2));
                }

                // Trim to topK (already in descending score order)
                if (out.size() > topK) {
                    out = out.subList(0, topK);
                }
                return out;
            } finally {
                searcherManager.release(searcher);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to search with predicate filter", e);
        }
    }

    public Iterable<SearchResult> iterateResults(IndexSearcher searcher, TopDocs results) {
        return () -> new Iterator<SearchResult>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < results.scoreDocs.length;
            }

            @Override
            public SearchResult next() {
                try {
                    ScoreDoc scoreDoc = results.scoreDocs[index++];
                    Document doc = searcher.storedFields().document(scoreDoc.doc);
                    return new SearchResult(
                            doc.get("text"),
                            doc.getField("src"),
                            scoreDoc.score,
                            doc.getField("id")
                    );
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
    }

    public Iterable<SearchResult> searchByText(String query, int maxResults) throws ParseException {
        Query textQuery = new org.apache.lucene.queryparser.classic.QueryParser("text", new StandardAnalyzer())
                .parse(query);
        try {
            refresh();
            IndexSearcher searcher = searcherManager.acquire();
            try {
                TopDocs results = searcher.search(textQuery, maxResults);
                return iterateResults(searcher, results);
            } finally {
                searcherManager.release(searcher);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve all documents", e);
        }
    }

    public void deleteDocument(String text) {
        long hash = StringMan.hash(text);
        try {
        indexWriter.deleteDocuments(LongPoint.newExactQuery("id", hash));
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve all documents", e);
        }
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

    private Document createDocument(String text, @Nullable Long hash, float[] vector, Integer sourceId) {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField("vector", vector, VectorSimilarityFunction.COSINE));
        doc.add(new TextField("text", text, Field.Store.YES));
        if (hash == null) hash = StringMan.hash(text);

        // Numeric indexable field (not stored)
        doc.add(new LongPoint("id", hash));
        // Stored copy so you can read it back from search results
        doc.add(new StoredField("id", hash)); // same name is fine; Lucene allows multiple fields with same name

        if (sourceId != null) {
            doc.add(new IntPoint("src", sourceId));     // for queries
            doc.add(new StoredField("src", sourceId));   // for retrieval
        }
        return doc;
    }

    // Optional overload for a set of source IDs
    public int countBySource(Set<Integer> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) return 0;
        try {
            refresh();
            IndexSearcher searcher = searcherManager.acquire();
            try {
                Query srcQuery = (sourceIds.size() == 1)
                        ? IntPoint.newExactQuery("src", sourceIds.iterator().next())
                        : IntPoint.newSetQuery("src", sourceIds);
                return searcher.count(srcQuery);
            } finally {
                searcherManager.release(searcher);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to count documents by source set", e);
        }
    }

    public Optional<Map.Entry<String, float[]>> getTextAndVectorById(long id, boolean fetchText, boolean fetchVector) {
        try {
            refresh();
            IndexSearcher searcher = searcherManager.acquire();
            try {
                Query byId = LongPoint.newExactQuery("id", id);
                TopDocs hits = searcher.search(byId, 1);
                if (hits.scoreDocs.length == 0) {
                    return Optional.empty();
                }

                int globalDocId;
                String text = null;
                if (fetchText) {
                    ScoreDoc sd = hits.scoreDocs[0];
                    Document doc = searcher.storedFields().document(sd.doc);
                    text = doc.get("text");
                    globalDocId = sd.doc;
                } else {
                    globalDocId = hits.scoreDocs[0].doc;
                }

                float[] vector = fetchVector ? readFloatVector(searcher.getIndexReader(), globalDocId, "vector") : null;
                return Optional.of(new KeyValue<>(text, vector));
            } finally {
                searcherManager.release(searcher);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve all documents", e);
        }
    }

    /**
     * Reads a float vector from the index for the given global document ID.
     * This method handles both dense and sparse vectors efficiently.
     *
     * @param reader       The IndexReader to read from
     * @param globalDocId  The global document ID to read the vector for
     * @param field        The field name containing the float vector
     * @return The float vector, or null if not found
     * @throws IOException If an I/O error occurs
     */
    @SuppressWarnings("resource")
    private static float[] readFloatVector(IndexReader reader, int globalDocId, String field) throws IOException {
        List<LeafReaderContext> leaves = reader.leaves();
        int leafIndex = ReaderUtil.subIndex(globalDocId, leaves);
        LeafReaderContext leaf = leaves.get(leafIndex);
        int docIdInLeaf = globalDocId - leaf.docBase;

        FloatVectorValues values = leaf.reader().getFloatVectorValues(field);
        if (values == null) {
            return null; // no vectors for this field in this segment
        }

        // Fast path for dense segments: ord == docIdInLeaf
        int count = values.size();
        if (docIdInLeaf >= 0 && docIdInLeaf < count && values.ordToDoc(docIdInLeaf) == docIdInLeaf) {
            return values.vectorValue(docIdInLeaf);
        }

        // General path: binary search on ord -> doc mapping (ordinals are in doc order)
        int lo = 0, hi = count - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int doc = values.ordToDoc(mid);
            if (doc == docIdInLeaf) {
                return values.vectorValue(mid);
            } else if (doc < docIdInLeaf) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return null; // doc has no vector
    }

    public boolean documentExists(String text) {
        long id = StringMan.hash(text);
        Query query = LongPoint.newExactQuery("id", id);
        try {
            refresh();
            IndexSearcher searcher = searcherManager.acquire();
            try {
                return searcher.count(query) > 0;
            } finally {
                searcherManager.release(searcher);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve all documents", e);
        }
    }

    public void addOrUpdateDocument(String text, @Nullable Long hash, float[] vector, Integer sourceId) {
        long id = StringMan.hash(text);
        Document doc = createDocument(text, hash, vector, sourceId);
        try {
        indexWriter.deleteDocuments(LongPoint.newExactQuery("id", id));
        indexWriter.addDocument(doc);
        searcherDirty.incrementAndGet();
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve all documents", e);
        }
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
        public final Long hash;
        public final float[] vector;
        public final Integer sourceId;

        public DocumentEntry(String text, @Nullable Long hash, float[] vector, @Nullable Integer sourceId) {
            this.text = text;
            this.hash = hash != null ? hash : StringMan.hash(text);
            this.vector = vector;
            this.sourceId = sourceId;
        }

        public DocumentEntry(String text, @Nullable Long hash, float[] vector) {
            this(text, hash, vector, null);
        }
    }

    public static class SearchResult {
        public final String text;
        public final Integer sourceId;
        public final float score;
        public final long id;

        public SearchResult(String text, @Nullable IndexableField srcField, float score, IndexableField idField) {
            this.text = text;
            this.score = score;
            this.id = idField.numericValue().longValue();
            this.sourceId = srcField != null ? srcField.numericValue().intValue() : null;
        }

        @Override
        public String toString() {
            return java.lang.String.format("SearchResult{text='%s', score=%.3f, id='%s'}", text, score, id);
        }
    }

    public static VectorDatabase createInConfigFolder(String vectorName) throws IOException {
        Path indexPath = Path.of("config", "vectors", vectorName);
        Files.createDirectories(indexPath);
        return new VectorDatabase(indexPath, Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.EMBEDDING.MEMORY_ALLOCATED_MB);
    }
}