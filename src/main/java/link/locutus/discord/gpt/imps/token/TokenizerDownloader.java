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

    public enum SourceType {
        HUGGINGFACE, GITHUB, LOCAL
    }
    /**
     * Download tokenizer.model bytes from a Hugging Face repo (or any direct URL).
     * If HUGGINGFACE_TOKEN is non-empty and repo is private, it will be used in Authorization header.
     *
     * @param repoId repo id like "google/gemma-2-9b-it"
     * @param filename usually "tokenizer.model"
     * @return SpTokenizer constructed from the bytes (caller should close it when done)
     */
    /**
     * Download tokenizer.model bytes from Hugging Face or GitHub and load into SpTokenizer.
     * Supports caching and optional HF token for private repositories.
     *
     * @param sourceType SourceType.HUGGINGFACE or SourceType.GITHUB
     * @param repoId For HUGGINGFACE: "org/model-name"
     *               For GITHUB: "user/repo/branch/path/to/file"
     * @param filename Usually "tokenizer.model" (used for cache naming)
     * @return SpTokenizer constructed from the bytes (caller should close it when done)
     */
    public static SpTokenizer downloadAndLoad(SourceType sourceType, String repoId, String filename) throws IOException {
        // cache path: ~/.cache/djl/tokenizers/{source}__{repoId}__{filename}
        String safeName = repoId.replace('/', '_').replace(':', '_');
        Path cache = CACHE_DIR.resolve(sourceType.name().toLowerCase() + "__" + safeName + "__" + filename);

        // ensure cache dir exists
        Files.createDirectories(CACHE_DIR);

        byte[] modelBytes;
        if (Files.exists(cache)) {
            modelBytes = Files.readAllBytes(cache);
        } else {
            switch (sourceType) {
                case HUGGINGFACE -> modelBytes = downloadFromHfRaw(repoId, filename);
                case GITHUB -> {
                    // Expecting repoId in format: "user/repo/branch/path/to/file"
                    String[] parts = repoId.split("/", 4);
                    if (parts.length < 4) {
                        throw new IllegalArgumentException("For GitHub, repoId must be: user/repo/branch/path/to/file");
                    }
                    modelBytes = downloadFromGithubRaw(parts[0], parts[1], parts[2], parts[3], filename);
                }
                case LOCAL -> {
                    try (var is = TokenizerDownloader.class.getClassLoader().getResourceAsStream(filename)) {
                        if (is == null) {
                            throw new IOException("Resource not found in JAR: " + filename);
                        }
                        modelBytes = is.readAllBytes();
                    }
                }
                default -> throw new IllegalArgumentException("Unsupported source type: " + sourceType);
            }

            // write atomically
            Path tmp = cache.resolveSibling(cache.getFileName() + ".tmp");
            Files.write(tmp, modelBytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            Files.move(tmp, cache, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }

        return new SpTokenizer(modelBytes);
    }

    private static byte[] downloadFromGithubRaw(String user, String repo, String branch, String path, String filename) throws IOException {
        // Ensure the path ends with a slash if not empty
        String fullPath = path.endsWith("/") ? path + filename : path + "/" + filename;
        String url = "https://raw.githubusercontent.com/" + user + "/" + repo + "/" + branch + "/" + fullPath;
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("User-Agent", "djl-tokenizer-downloader/1.0")
                .build();
        try {
            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                throw new IOException("Failed to download " + url + " : HTTP " + resp.statusCode());
            }
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading from GitHub", e);
        }
    }

    private static byte[] downloadFromHfRaw(String repoId, String filename) throws IOException {
        String url = "https://huggingface.co/" + repoId + "/resolve/main/" + filename;

        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).GET()
                .header("User-Agent", "djl-tokenizer-downloader/1.0");

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
//
//    // Example usage:
//    public static void main(String[] args) throws Exception {
//        // repo: google/gemma-2-9b-it  filename: tokenizer.model
//        try (SpTokenizer tokenizer = downloadAndLoadFromHf("google/gemma-2-9b-it", "tokenizer.model")) {
//            String text = "Hello world, this is a Gemma test.";
//            System.out.println("Pieces: " + tokenizer.tokenize(text));
//            System.out.println("Piece count: " + tokenizer.tokenize(text).size());
//            // if you need IDs:
//            System.out.println("IDs: " + java.util.Arrays.toString(tokenizer.getProcessor().encode(text)));
//        }
//    }
}
