package link.locutus.discord.gpt.imps;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Downloads the latest sqlite-vec loadable extension for the current platform
 * from GitHub Releases and returns the path to the extracted vec0 library.
 *
 * Usage in SqliteVecStore:
 *   Path dll = SqliteVecFetcher.ensureLatestForCurrentPlatform(Path.of("data", "sqlite-vec-cache"));
 *   loadExtension(dll);
 */
public final class SqliteVecFetcher {

    private static final String REPO_RELEASES_API = "https://api.github.com/repos/asg017/sqlite-vec/releases?per_page=10";
    private static final String USER_AGENT = "xdnw-SqliteVecFetcher/1.0";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private SqliteVecFetcher() {}

    public static Path ensureLatestForCurrentPlatform(Path cacheDir) throws IOException, InterruptedException {
        // Default to not checking unless asked
        return ensureLatestForCurrentPlatform(cacheDir, false);
    }

    public static Path ensureLatestForCurrentPlatform(Path cacheDir, boolean checkForUpdates)
            throws IOException, InterruptedException {
        Objects.requireNonNull(cacheDir, "cacheDir");
        Files.createDirectories(cacheDir);

        Platform p = detectPlatform();

        String libExt = switch (p.os) {
            case "windows" -> ".dll";
            case "linux" -> ".so";
            case "macos" -> ".dylib";
            default -> throw new IllegalStateException("Unsupported OS: " + p.os);
        };

        // If we already have a vec0 library and updates are not requested, return it without any network calls.
        Path existing = findExistingLib(cacheDir, "vec0" + libExt);
        if (existing != null && Files.exists(existing) && !checkForUpdates) {
            return existing;
        }

        // We need to fetch/ensure the latest for this platform
        String suffix = "-loadable-" + p.os + "-" + p.arch + ".tar.gz";
        String token = System.getenv("GITHUB_TOKEN");
        String json = httpGet(REPO_RELEASES_API, token);

        // Find the first asset URL in the latest releases list that matches our platform suffix.
        Pattern urlPat = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+?" + Pattern.quote(suffix) + ")\"");
        Matcher m = urlPat.matcher(json);
        if (!m.find()) {
            // Fallback to the /latest endpoint (non-prerelease) if not found above
            String latestJson = httpGet("https://api.github.com/repos/asg017/sqlite-vec/releases/latest", token);
            m = urlPat.matcher(latestJson);
            if (!m.find()) {
                throw new IOException("No release asset found for suffix: " + suffix);
            }
            json = latestJson;
        }
        String assetUrl = m.group(1);

        // Use the asset file name as the cache key
        String assetName = assetUrl.substring(assetUrl.lastIndexOf('/') + 1); // sqlite-vec-...tar.gz
        Path assetCacheDir = cacheDir.resolve(assetName.replace(".tar.gz", ""));
        Files.createDirectories(assetCacheDir);

        Path libPath = assetCacheDir.resolve("vec0" + libExt);
        if (Files.exists(libPath) && !checkForUpdates) {
            return libPath;
        }

        // If the asset tarball is already cached, extract from it instead of downloading.
        Path cachedTgz = cacheDir.resolve(assetName);
        if (Files.exists(cachedTgz)) {
            extractSingleVecLibFromTarGz(cachedTgz, assetCacheDir, libExt);
            if (Files.exists(libPath)) return libPath;
            // if extraction failed to produce the lib, fall through to (re)download below
        }

        // Download asset to a temp file, copy it into cache, then extract
        Path tmpTgz = Files.createTempFile("sqlite-vec-", ".tar.gz");
        tmpTgz.toFile().deleteOnExit();
        httpDownload(assetUrl, tmpTgz, token);

        // Save a cached copy (overwrite if present)
        Files.copy(tmpTgz, cachedTgz, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        // Extract only the vec0 library from the tar.gz
        extractSingleVecLibFromTarGz(cachedTgz, assetCacheDir, libExt);

        if (!Files.exists(libPath)) {
            throw new IOException("Extracted archive but could not find vec0" + libExt);
        }
        return libPath;
    }

    // Find an existing vec0 library under the cache directory (pick the newest if multiple).
    private static Path findExistingLib(Path cacheDir, String libFileName) throws IOException {
        if (!Files.isDirectory(cacheDir)) return null;
        try (java.util.stream.Stream<Path> s = Files.find(
                cacheDir,
                5,
                (p, attr) -> attr.isRegularFile() && p.getFileName().toString().equals(libFileName))) {
            return s.sorted((a, b) -> {
                try {
                    long mb = Files.getLastModifiedTime(b).toMillis();
                    long ma = Files.getLastModifiedTime(a).toMillis();
                    return Long.compare(mb, ma);
                } catch (IOException e) {
                    return 0;
                }
            }).findFirst().orElse(null);
        }
    }

    // --- HTTP helpers ---

    private static String httpGet(String url, String token) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .GET()
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT);
        if (token != null && !token.isBlank()) {
            b.header("Authorization", "Bearer " + token.trim());
        }
        HttpResponse<byte[]> res = client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        int code = res.statusCode();
        if (code >= 200 && code < 300) {
            return new String(res.body(), StandardCharsets.UTF_8);
        }
        throw new IOException("GET " + url + " failed: " + code + " " + preview(res.body()));
    }

    private static void httpDownload(String url, Path dest, String token) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .GET()
                .header("Accept", "application/octet-stream")
                .header("User-Agent", USER_AGENT);
        if (token != null && !token.isBlank()) {
            b.header("Authorization", "Bearer " + token.trim());
        }
        HttpResponse<InputStream> res = client.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
        int code = res.statusCode();
        if (code >= 200 && code < 300) {
            try (InputStream in = new BufferedInputStream(res.body());
                 OutputStream out = new BufferedOutputStream(Files.newOutputStream(dest))) {
                in.transferTo(out);
            }
            return;
        }
        throw new IOException("Download failed: " + code + " " + url);
    }

    private static String preview(byte[] body) {
        String s = new String(body, StandardCharsets.UTF_8);
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    // --- Platform detection ---

    private record Platform(String os, String arch) {}

    private static Platform detectPlatform() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String archRaw = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

        String os;
        if (osName.contains("win")) os = "windows";
        else if (osName.contains("mac") || osName.contains("darwin")) os = "macos";
        else if (osName.contains("nux") || osName.contains("nix")) os = "linux";
        else throw new IllegalStateException("Unsupported OS: " + osName);

        String arch;
        if (archRaw.contains("aarch64") || archRaw.contains("arm64")) arch = "aarch64";
        else if (archRaw.contains("x86_64") || archRaw.contains("amd64") || archRaw.equals("x64")) arch = "x86_64";
        else throw new IllegalStateException("Unsupported arch: " + archRaw);

        return new Platform(os, arch);
    }

    // --- Minimal tar.gz extraction (extract only vec0.<ext>) ---

    private static void extractSingleVecLibFromTarGz(Path tgz, Path outDir, String libExt) throws IOException {
        Files.createDirectories(outDir);
        try (InputStream fin = Files.newInputStream(tgz);
             GZIPInputStream gzin = new GZIPInputStream(new BufferedInputStream(fin))) {

            // TAR blocks of 512 bytes
            byte[] header = new byte[512];
            while (true) {
                int n = readFully(gzin, header, 0, 512);
                if (n == -1) break; // EOF
                if (n < 512) throw new IOException("Short TAR header");
                if (isAllZeros(header)) break; // End of archive

                String name = readString(header, 0, 100);
                long size = readOctal(header, 124, 12);
                int typeflag = header[156]; // '0' or 0 for regular file

                // Normalize name separators
                String norm = name.replace('\\', '/');
                boolean isFile = typeflag == '0' || typeflag == 0;

                Path target = null;
                if (isFile && looksLikeVecLib(norm, libExt)) {
                    target = outDir.resolve("vec0" + libExt);
                    // Ensure parent dir exists
                    Files.createDirectories(target.getParent());
                    try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
                        copyN(gzin, out, size);
                    }
                } else {
                    // Skip file content if not extracting this entry
                    skipN(gzin, size);
                }

                // Skip padding to 512-byte boundary
                long pad = (512 - (size % 512)) % 512;
                if (pad > 0) skipN(gzin, pad);

                if (target != null && Files.exists(target)) {
                    // Stop early once we have our library
                    return;
                }
            }
        }
    }

    private static boolean looksLikeVecLib(String path, String libExt) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith("/vec0" + libExt) || lower.equals("vec0" + libExt) || lower.matches(".*?/vec0[^/]*" + Pattern.quote(libExt));
    }

    private static int readFully(InputStream in, byte[] buf, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int n = in.read(buf, off + total, len - total);
            if (n == -1) return total == 0 ? -1 : total;
            total += n;
        }
        return total;
    }

    private static boolean isAllZeros(byte[] b) {
        for (byte v : b) if (v != 0) return false;
        return true;
    }

    private static String readString(byte[] buf, int off, int len) {
        int end = off + len;
        int i = off;
        while (i < end && buf[i] != 0) i++;
        return new String(buf, off, i - off, StandardCharsets.US_ASCII).trim();
    }
    private static long readOctal(byte[] buf, int off, int len) {
        long val = 0;
        int end = off + len;
        int i = off;
        while (i < end && (buf[i] == ' ' || buf[i] == 0)) i++;
        for (; i < end && buf[i] >= '0' && buf[i] <= '7'; i++) {
            val = (val << 3) + (buf[i] - '0');
        }
        return val;
    }

    private static void copyN(InputStream in, OutputStream out, long n) throws IOException {
        byte[] buf = new byte[64 * 1024];
        long remaining = n;
        while (remaining > 0) {
            int toRead = (int) Math.min(buf.length, remaining);
            int r = in.read(buf, 0, toRead);
            if (r == -1) throw new IOException("Unexpected EOF while copying");
            out.write(buf, 0, r);
            remaining -= r;
        }
    }

    private static void skipN(InputStream in, long n) throws IOException {
        long remaining = n;
        byte[] buf = new byte[64 * 1024];
        while (remaining > 0) {
            int toRead = (int) Math.min(buf.length, remaining);
            int r = in.read(buf, 0, toRead);
            if (r == -1) throw new IOException("Unexpected EOF while skipping");
            remaining -= r;
        }
    }
}
