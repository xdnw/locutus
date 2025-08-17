package link.locutus.discord.gpt.imps.token;

import ai.djl.sentencepiece.SpTokenizer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.Optional;

public class TokenizerDownloader {
    private static final Path CACHE_DIR =
            Paths.get(System.getProperty("user.home"), ".cache", "djl", "tokenizers");

    /**
     * Download tokenizer.model bytes from a Hugging Face repo (or any direct URL).
     * If HUGGINGFACE_TOKEN is non-empty and repo is private, it will be used in Authorization header.
     *
     * @param repoId repo id like "google/gemma-2-9b-it"
     * @param filename usually "tokenizer.model"
     * @return SpTokenizer constructed from the bytes (caller should close it when done)
     */
    public static SpTokenizer downloadAndLoadFromHf(String repoId, String filename) throws IOException {
        // cache path: ~/.cache/djl/tokenizers/google__gemma-2-9b-it_tokenizer.model
        String safeName = repoId.replace('/', '_').replace(':', '_');
        Path cache = CACHE_DIR.resolve(safeName + "__" + filename);

        // ensure cache dir exists
        Files.createDirectories(CACHE_DIR);

        byte[] modelBytes;
        if (Files.exists(cache)) {
            modelBytes = Files.readAllBytes(cache);
        } else {
            modelBytes = downloadFromHfRaw(repoId, filename);
            // write atomically
            Path tmp = cache.resolveSibling(cache.getFileName() + ".tmp");
            Files.write(tmp, modelBytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            Files.move(tmp, cache, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }

        // Construct SpTokenizer from bytes (DJL API)
        return new SpTokenizer(modelBytes);
    }

    private static byte[] downloadFromHfRaw(String repoId, String filename) throws IOException {
        // Hugging Face raw file URL pattern:
        // https://huggingface.co/{repoId}/resolve/main/{filename}
        String url = "https://huggingface.co/" + repoId + "/resolve/main/" + filename;

        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET()
                .header("User-Agent", "djl-tokenizer-downloader/1.0");

        // Optional: use HF token if provided in env var (for private models)
        Optional<String> hfToken = Optional.ofNullable(System.getenv("HUGGINGFACE_TOKEN"));
        hfToken.ifPresent(token -> builder.header("Authorization", "Bearer " + token));

        HttpRequest req = builder.build();
        try {
            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                throw new IOException("Failed to download " + url + " : HTTP " + resp.statusCode());
            }
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading tokenizer", e);
        }
    }

    // Example usage:
    public static void main(String[] args) throws Exception {
        // repo: google/gemma-2-9b-it  filename: tokenizer.model
        try (SpTokenizer tokenizer = downloadAndLoadFromHf("google/gemma-2-9b-it", "tokenizer.model")) {
            String text = "Hello world, this is a Gemma test.";
            System.out.println("Pieces: " + tokenizer.tokenize(text));
            System.out.println("Piece count: " + tokenizer.tokenize(text).size());
            // if you need IDs:
            System.out.println("IDs: " + java.util.Arrays.toString(tokenizer.getProcessor().encode(text)));
        }
    }
}
