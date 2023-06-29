package link.locutus.discord.gpt.copilot;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class HttpClientWrapper {
    private final HttpClient client;
    private final Map<String, String> headers = new LinkedHashMap<>();

    public HttpClientWrapper(HttpClient client) {
        this.client = client;
    }

    public HttpClient getClient() {
        return client;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void AddOrReplaceHeader(String header, String value) {
        headers.put(header, value);
    }

    public CompletableFuture<HttpResponse<String>> PostAsync(String url, String post) throws URISyntaxException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(new URI(url))
                .POST(HttpRequest.BodyPublishers.ofString(post));

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder = builder.header(entry.getKey(), entry.getValue());
        }
        HttpRequest request = builder.build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    public CompletableFuture<HttpResponse<String>> GetAsync(String url) throws URISyntaxException, IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(new URI(url))
                .GET();

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder = builder.header(entry.getKey(), entry.getValue());
        }
        HttpRequest request = builder.build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString());

    }
}
