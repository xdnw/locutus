package link.locutus.discord.web.jooby;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.util.PW;
import org.json.JSONObject;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class BankRequestHandler {
    private final ConcurrentLinkedQueue<JSONObject> requests = new ConcurrentLinkedQueue<JSONObject>();
    Map<UUID, Consumer<String>> callbacks = new ConcurrentHashMap<>();

    public Future<String> addRequest(UUID uuid, NationOrAlliance recipient, double[] amt, String note) {
        // token
        String txStr = ResourceType.resourcesToJson(recipient.getName(), recipient.isNation(), ResourceType.resourcesToMap(amt), note);
        JSONObject json = new JSONObject();
        json.put("transfer", new JSONObject(txStr));
        json.put("token", uuid.toString());
        json.put("timestamp", System.currentTimeMillis());

        requests.add(json);
        CompletableFuture<String> future = new CompletableFuture<>();
        callbacks.put(uuid, future::complete);
        return future;
    }

    public void callBack(UUID uuid, String message) {
        Consumer<String> callback = callbacks.get(uuid);
        if (callback != null) callback.accept(message);
    }

    public JSONObject pollRequest() {
        return requests.poll();
    }
}
