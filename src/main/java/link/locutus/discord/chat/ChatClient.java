package link.locutus.discord.chat;

import com.google.gson.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildKey;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ChatClient {
    public static byte[] parseToken(String token) {
        if (token.isEmpty()) {
            throw new IllegalArgumentException("Chat token cannot be empty.");
        }
        if (token.length() != 64) {
            throw new IllegalArgumentException("Chat token must be 64 characters long.");
        }
        if (!Settings.INSTANCE.CHAT_TOKEN.matches("^[0-9a-fA-F]{64}$")) {
            throw new IllegalArgumentException("Chat token must be a valid hexadecimal string.");
        }
        try {
            return Hex.decodeHex(token.toCharArray());
        } catch (DecoderException e) {
            throw new IllegalArgumentException("Chat token must be a valid hexadecimal string.", e);
        }
    }

    public static Map.Entry<String, Boolean> validateToken(int natioId, String token) {
        if (token == null || token.isEmpty()) {
            String script = "(()=>{let r=/token\\s*:\\s*'([0-9a-f]{64})'/i,t;for(let s of document.scripts){let m=(s.textContent||\"\").match(r);if(m){t=m[1];break}}if(!t)return alert(\"Token not found\");let c=e=>{try{let a=document.createElement(\"textarea\");a.value=e;a.style.position=\"fixed\";a.style.left=\"-9999px\";document.body.appendChild(a);a.select();document.execCommand(\"copy\");a.remove();return 1}catch(e){return 0}},n=navigator.clipboard,m=[\"Token copied:\",\"Use Ctrl+C to copy token:\"];(async()=>prompt((n&&n.writeText?await n.writeText(t).then(()=>0).catch(()=>c(t)?0:1):c(t)?0:1)?m[1]:m[0],t))()})();";
            String md = "# How to Obtain and Set Your Chat Token\n\n" +
                    "1. **Open Developer Tools (PC)**\n" +
                    "\\- On Windows: Press `F12`  \n" +
                    "\\- On Mac: Press `Cmd + Option + I`\n\n" +
                    "2. **Copy the Provided Script**\n\n" +
                    "```javascript\n" +
                    script +
                    "\n```\n\n" +
                    "3. **Paste and Run the Script**  \n" +
                    "\\- Go to the `Console` tab in Developer Tools.  \n" +
                    "\\- Paste the script and press `Enter`.\n\n" +
                    "4. **Copy the Token**  \n" +
                    "\\- The script will copy the token to your clipboard or show a prompt.  \n" +
                    "\\- If prompted, use `Ctrl+C` (Windows) or `Cmd+C` (Mac) to copy.\n\n" +
                    "5. **Set Your Token in the Command**  \n" +
                    "\\- Run the chat command again, providing your copied token as the argument.\n\n";
            return Map.entry(md, false);
        } else {
            parseToken(token);
            // Throws error on fail
            if (!ChatClient.testConnection(token, natioId)) {
                throw new IllegalArgumentException("Invalid token or connection failed");
            }

            return Map.entry("Token is valid", true);
        }
    }

    public static boolean testConnection(String token, int nationId) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        AtomicReference<String> result = new AtomicReference<>("Timeout: No response within 15 seconds.");
        ChatClient client = new ChatClient(ChatClient.WS_DEFAULT_URL, token, nationId, Channels.GLOBAL) {
            @Override
            public void onAuthenticated(JsonObject data, User user) {
                result.set("Authenticated: " + user.toString());
                if (user.accountId == nationId) {
                    success.set(true);
                } else {
                    result.set("Error: Account ID mismatch. Expected " + nationId + ", got " + user.accountId);
                }
                latch.countDown();
            }

            @Override
            public void onError(WebSocket socket, Throwable err) {
                latch.countDown();
            }
        };
        try {
            client.start();
            latch.await(10, TimeUnit.SECONDS);
            if (!success.get()) {
                throw new IllegalArgumentException("Connection failed: " + result.get());
            } else {
                return true;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            client.quit();
        }
    }

    private final Object replyLock = new Object();

    public static final Channels WS_DEFAULT_CHANNEL = Channels.GLOBAL;
    public static final String WS_DEFAULT_URL = "wss://test.politicsandwar.com/ws/";
    // Configuration (can be overridden by CLI args/env vars)
    private String wsUrl;
    private String token;
    private int accountId;
    private Channels currentChannel;

    // Intervals (match JS)
    private static final long HEARTBEAT_MS = 30_000L;
    private static final long AUTH_REFRESH_MS = 300_000L;

    // Runtime
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private volatile WebSocket webSocket;
    private final Gson gson = new GsonBuilder().create();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> authRefreshTask;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final Object lock = new Object();

    // Reconnect
    private int reconnectAttempts = 0;
    private final int maxReconnectAttempts = 10;

    public ChatClient(String wsUrl, String token, int accountId, Channels currentChannel) {
        if (token == null) {
            throw new IllegalArgumentException("Token cannot be null");
        }
        if (accountId <= 0) {
            throw new IllegalArgumentException("Account ID must be a positive number");
        }
        if (currentChannel == null) {
            throw new IllegalArgumentException("Current channel cannot be null or empty");
        }
        if (wsUrl == null || wsUrl.isBlank()) {
            throw new IllegalArgumentException("WebSocket URL cannot be null or empty");
        }
        if (!wsUrl.startsWith("wss://") && !wsUrl.startsWith("ws://")) {
            throw new IllegalArgumentException("WebSocket URL must start with 'wss://' or 'ws://'");
        }
        this.wsUrl = wsUrl;
        this.token = token;
        this.accountId = accountId;
        this.currentChannel = currentChannel;
    }

    public String getToken() {
        return token;
    }

    public int getAccountId() {
        return accountId;
    }

    public static ChatClient loadFromArgs(String[] args) {
        String wsUrl = WS_DEFAULT_URL;
        String token = null;
        int accountId = 0;
        Channels currentChannel = WS_DEFAULT_CHANNEL;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--ws":
                case "--wsUrl":
                    if (i + 1 < args.length) wsUrl = args[++i];
                    break;
                case "--token":
                    if (i + 1 < args.length) token = args[++i];
                    break;
                case "--userId":
                case "--accountId":
                    if (i + 1 < args.length) accountId = Integer.parseInt(args[++i]);
                    break;
                case "--channel":
                    if (i + 1 < args.length) currentChannel = link.locutus.discord.chat.Channels.parse(args[++i]);
                    break;
            }
        }
        return new ChatClient(wsUrl, token, accountId, currentChannel);
    }

    public static ChatClient loadFromEnv() {
        String token = null;
        int accountId = 0;
        Channels currentChannel = WS_DEFAULT_CHANNEL;
        String wsUrl = WS_DEFAULT_URL;
        if (token == null) {
            String t = System.getenv("TOKEN");
            if (t != null && !t.isBlank()) token = t;
        }
        if (accountId == 0) {
            String id = System.getenv("USER_ID");
            if (id == null) id = System.getenv("ACCOUNT_ID");
            if (id != null && !id.isBlank()) accountId = Integer.parseInt(id);
        }
        String ch = System.getenv("CHANNEL");
        if (ch != null && !ch.isBlank()) currentChannel = Channels.parse(ch);
        String ws = System.getenv("WS_URL");
        if (ws != null && !ws.isBlank()) wsUrl = ws;
        return new ChatClient(wsUrl, token, accountId, currentChannel);
    }

    public void start() {
        System.out.println("Connecting to " + wsUrl + " as account " + accountId + " channel " + currentChannel);
        connect();
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    public void onError(WebSocket socket, Throwable err) {

    }

    private void connect() {
        if (!running.get()) return;
        if (connecting.getAndSet(true)) return;

        try {
            httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(wsUrl), new Listener())
                    .whenComplete((ws, err) -> {
                        connecting.set(false);
                        if (err != null) {
                            synchronized (replyLock) {
                                replyLock.notifyAll();
                            }
                            onError(ws, err);
                            scheduleReconnect();
                        } else {
                            System.out.println("WebSocket connected: " + (ws != null) + " | " + System.nanoTime());
                            synchronized (lock) {
                                this.webSocket = ws;
                            }
                            synchronized (replyLock) {
                                replyLock.notifyAll();
                            }
                            this.reconnectAttempts = 0;
                        }
                    });
        } catch (Exception e) {
            connecting.set(false);
            onError(webSocket, e);
            scheduleReconnect();
        }
    }

    public boolean isValidAllianceChat(int allianceId, boolean checkToken, boolean checkGuild) {
        if (currentChannel != Channels.ALLIANCE_ALL) return false;
        DBNation nation = DBNation.getById(accountId);
        if (nation == null || nation.getAlliance_id() != allianceId || nation.getPositionEnum().id <= Rank.APPLICANT.id) {
            return false;
        }
        if (checkToken) {
            String token = Locutus.imp().getDiscordDB().getChatToken(accountId);
            if (!token.equalsIgnoreCase(this.token)) {
                return false;
            }
        }
        if (checkGuild) {
            GuildDB db = Locutus.imp().getGuildDBByAA(allianceId);
            if (db == null) return false;
            Set<Integer> validAccounts = GuildKey.GAME_CHAT_ACCOUNT.get(db);
            if (validAccounts == null || !validAccounts.contains(accountId)) return false;
            ApiKeyPool key = db.getMailKey();
            if (key == null) return false;
        }
        return true;
    }

    private void scheduleReconnect() {
        if (!running.get()) return;
        if (reconnectAttempts >= maxReconnectAttempts) {
            System.err.println("Max reconnect attempts reached. Exiting.");
            running.set(false);
            synchronized (lock) { lock.notifyAll(); }
            return;
        }
        int attempt = ++reconnectAttempts;
        long delay = Math.min(30_000L, (long) (2000 * Math.pow(2, attempt - 1)));
        System.out.println("Reconnecting in " + delay + " ms (attempt " + attempt + "/" + maxReconnectAttempts + ")");
        scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
    }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatTask = scheduler.scheduleAtFixedRate(() -> send(json("action", "heartbeat")), HEARTBEAT_MS, HEARTBEAT_MS, TimeUnit.MILLISECONDS);
    }

    private void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
            heartbeatTask = null;
        }
    }

    private void startAuthRefresh() {
        stopAuthRefresh();
        authRefreshTask = scheduler.scheduleAtFixedRate(() -> {
            JsonObject o = new JsonObject();
            o.addProperty("action", "refresh_auth");
            o.addProperty("token", token);
            o.addProperty("account_id", accountId);
            send(o);
        }, AUTH_REFRESH_MS, AUTH_REFRESH_MS, TimeUnit.MILLISECONDS);
    }

    private void stopAuthRefresh() {
        if (authRefreshTask != null) {
            authRefreshTask.cancel(true);
            authRefreshTask = null;
        }
    }

    public void quit() {
        running.set(false);
        synchronized (lock) { lock.notifyAll(); }
        shutdown();
    }

    private void shutdown() {
        running.set(false);
        stopHeartbeat();
        stopAuthRefresh();
        closeWebSocket();
        scheduler.shutdownNow();
    }

    public void join(Channels ch) {
        if (ch == null) {
            throw new IllegalArgumentException("Channel cannot be null");
        }
        currentChannel = ch;
        JsonObject o = new JsonObject();
        o.addProperty("action", "join_channel");
        o.addProperty("channel", currentChannel.getName());
        send(o);
        System.out.println("Joining channel: " + currentChannel);
    }

    public void clear() {
        JsonObject clearObj = new JsonObject();
        clearObj.addProperty("action", "moderate");
        clearObj.addProperty("command", "clear_channel");
        clearObj.addProperty("channel", currentChannel.toString());
        send(clearObj);
        System.out.println("Clearing channel: " + currentChannel);
    }

    public void broadcast(String message) {
        if (message.isEmpty()) {
            System.out.println("Usage: /broadcast <message>");
            return;
        }
        JsonObject broadcastObj = new JsonObject();
        broadcastObj.addProperty("action", "admin_moderate");
        broadcastObj.addProperty("command", "broadcast_message");
        broadcastObj.addProperty("message", message);
        send(broadcastObj);
        System.out.println("Broadcasted message: " + message);
    }

    public void setTag(String tag) {
        if (tag.isEmpty()) {
            System.out.println("Usage: /tag <tag>");
            return;
        }
        JsonObject tagObj = new JsonObject();
        tagObj.addProperty("action", "set_alliance_tag");
        tagObj.addProperty("tag", tag);
        send(tagObj);
        System.out.println("Alliance tag set: " + tag);
    }

    public void sendMessage(Channels channel, String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("action", "message");
        obj.addProperty("channel", channel.getName());
        obj.addProperty("message", message);
        send(obj);
    }

    private void send(JsonObject obj) {
        WebSocket ws;
        synchronized (lock) {
            ws = this.webSocket;
        }
        if (ws == null) {
            System.err.println("Not connected: " + System.nanoTime());
            new Exception().printStackTrace();
            return;
        }
        String s = gson.toJson(obj);
        ws.sendText(s, true);
    }

    private JsonObject json(String k, String v) {
        JsonObject o = new JsonObject();
        o.addProperty(k, v);
        return o;
    }

    private void closeWebSocket() {
        try {
            if (webSocket != null) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Bye");
            }
        } catch (Exception ignored) {}
    }

    public static class ChatMessage {
        public final long id;
        public final String type;
        public final String channel;
        public final User user;
        public final String message;
        public final long timestamp;

        public ChatMessage(JsonObject obj) {
            this.id = obj.has("id") ? obj.get("id").getAsLong() : 0;
            this.type = obj.has("type") ? obj.get("type").getAsString() : null;
            this.channel = obj.has("channel") ? obj.get("channel").getAsString() : null;
            this.user = obj.has("user") && obj.get("user").isJsonObject() ? new User(obj.getAsJsonObject("user")) : null;
            this.message = obj.has("message") ? obj.get("message").getAsString() : null;
            this.timestamp = obj.has("timestamp") ? obj.get("timestamp").getAsLong() : 0;
        }
    }

    public static class User {
        public final long id;
        public final int accountId;
        public final String nationName;
        public final String leaderName;
        public final Rank position;
        public final Long allianceId;
        public final String allianceAcronym;
        public final String allianceColor;
        public final String allianceChatTag;

        public User(JsonObject obj) {
            this.id = obj.has("id") ? obj.get("id").getAsLong() : 0;
            this.accountId = obj.has("account_id") ? obj.get("account_id").getAsInt() : 0;
            this.nationName = obj.has("nation_name") ? obj.get("nation_name").getAsString() : null;
            this.leaderName = obj.has("leader_name") ? obj.get("leader_name").getAsString() : null;
            if (obj.has("position") && !obj.get("position").getAsString().isEmpty()) {
                position = Rank.valueOf(obj.get("position").getAsString().toUpperCase(Locale.ROOT));
            } else {
                position = Rank.REMOVE;
            }
            this.allianceId = obj.has("alliance_id") ? obj.get("alliance_id").getAsLong() : null;
            this.allianceAcronym = obj.has("alliance_acronym") ? obj.get("alliance_acronym").getAsString() : null;
            this.allianceColor = obj.has("alliance_color") ? obj.get("alliance_color").getAsString() : null;
            this.allianceChatTag = obj.has("alliance_chat_tag") ? obj.get("alliance_chat_tag").getAsString() : null;
        }

        public static List<User> getList(JsonArray userArr) {
            List<User> users = new ObjectArrayList<>();
            for (JsonElement elem : userArr) {
                User user = new User(elem.getAsJsonObject());
                users.add(user);
            }
            return users;
        }

        @Override
        public String toString() {
            return "User{" +
                    "id=" + id +
                    ", accountId=" + accountId +
                    ", nationName='" + nationName + '\'' +
                    ", leaderName='" + leaderName + '\'' +
                    ", position=" + position +
                    ", allianceId=" + allianceId +
                    ", allianceAcronym='" + allianceAcronym + '\'' +
                    ", allianceColor='" + allianceColor + '\'' +
                    ", allianceChatTag='" + allianceChatTag + '\'' +
                    '}';
        }
    }

    private class Listener implements WebSocket.Listener {
        private final StringBuilder partial = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            WebSocket.Listener.super.onOpen(webSocket);
            authenticated.set(false);
            ChatClient.this.webSocket = webSocket;
            System.out.println("WebSocket opened.");
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String msg = partial.toString();
                partial.setLength(0);
                handleIncoming(msg);
            }
            webSocket.request(1);
            return null;
        }

        private void handleIncoming(String raw) {
            try {
                JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
                String type = null;
                if (obj.has("status")) type = obj.get("status").getAsString();
                else if (obj.has("type")) type = obj.get("type").getAsString();

                if (type == null) {
                    System.out.println("Unknown message: " + raw);
                    return;
                }

                switch (type) {
                    case "welcome":
                        System.out.println("Welcome received, authenticating...");
                        authenticate();
                        break;
                    case "pong":
                        System.out.println("Pong received, authenticating...");
                        authenticate();
                        break;
                    case "authenticated":
                        handleAuthenticated(obj);
                        break;
                    case "joined":
                        handleJoin(obj);
                        break;
                    case "message":
                        onChatMessage(new ChatMessage(obj));
                        break;
                    case "system": {
                        String m = obj.has("message") ? obj.get("message").getAsString() : "";
                        onSystem(obj, m);
                        break;
                        }
                    case "message_deleted":
                        System.out.println("[SYSTEM] A message was deleted: " + obj);
                        break;
                    case "message_edited":
                        System.out.println("[SYSTEM] A message was edited: " + obj);
                        break;
                    case "message_pinned":
                        System.out.println("[SYSTEM] Pinned: " + obj);
                        break;
                    case "message_unpinned":
                        System.out.println("[SYSTEM] Unpinned: " + obj);
                        break;
                    case "user_presence":
                        System.out.println("[PRESENCE-user] " + obj);
                        break;
                    case "online_users":
                        System.out.println("[PRESENCE-online] " + obj);
                        break;
                    case "heartbeat":
                    case "auth_refreshed":
                        // No-op
                        break;
                    case "admin_action":
                    case "moderation_action":
                        System.out.println("[SYSTEM] " + obj);
                        break;
                    case "broadcast_message": {
                        String m = obj.has("message") ? obj.get("message").getAsString() : obj.toString();
                        System.out.println("[BROADCAST] " + m);
                        break;
                    }
                    case "chat_logs":
                        System.out.println("[LOGS] " + obj);
                        break;
                    case "error": {
                        String m = obj.has("message") ? obj.get("message").getAsString() : obj.toString();
                        onErrorMsg(obj, m);
                        break;
                    }
                    default:
                        System.out.println("Unhandled message: " + obj);
                }
            } catch (Exception e) {
                onError(webSocket, e);
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            System.out.println("WebSocket closed: " + statusCode + " " + reason);
            authenticated.set(false);
            stopHeartbeat();
            stopAuthRefresh();
            if (running.get()) scheduleReconnect();
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            ChatClient.this.onError(webSocket, error);
            authenticated.set(false);
            stopHeartbeat();
            stopAuthRefresh();
            if (running.get()) scheduleReconnect();
        }
    }

    // Auth

    private void authenticate() {
        JsonObject o = new JsonObject();
        o.addProperty("action", "auth");
        o.addProperty("token", token);
        o.addProperty("account_id", accountId);
        send(o);
        System.out.println("Sent auth request.");
    }

    ///  Receiving events


    public void onChatMessage(ChatMessage msg) {

    }

    public void onSystem(JsonObject data, String message) {
    }

    public void onErrorMsg(JsonObject data, String message) {
    }

    private void handleAuthenticated(JsonObject data) {
        if (data.has("online_users") && data.get("online_users").isJsonArray()) {
            JsonArray userArr = data.getAsJsonArray("online_users");
            handleOnlineUsers(User.getList(userArr));
        }
        User user = null;
        if (data.has("user")) {
            user = new User(data.getAsJsonObject("user"));
        }
        onAuthenticated(data, user);
    }

    public void onAuthenticated(JsonObject data, User user) {
        authenticated.set(true);
        System.out.println("Authenticated.");
        startHeartbeat();
        startAuthRefresh();

        // Join desired channel
        JsonObject o = new JsonObject();
        o.addProperty("action", "join_channel");
        o.addProperty("channel", currentChannel.getName());
        send(o);
        System.out.println("Requested join: " + currentChannel);
    }

    public void handleOnlineUsers(List<User> users) {
        System.out.println("Online users: " + users);
    }

    public void onChannelJoin(Channels channel) {
        currentChannel = channel;
    }

    private void handleJoin(JsonObject data) {
        Channels ch = data.has("channel") ? link.locutus.discord.chat.Channels.parse(data.get("channel").getAsString()) : currentChannel;
        if (ch != currentChannel) {
            onChannelJoin(ch);
        }
        List<ChatMessage> history = new ObjectArrayList<>();
        List<ChatMessage> systemMsgs = new ObjectArrayList<>();

        if (data.has("history") && data.get("history").isJsonArray()) {
            JsonArray historyArr = data.getAsJsonArray("history");
            for (JsonElement el : historyArr) {
                if (!el.isJsonObject()) continue;
                JsonObject msg = el.getAsJsonObject();
                if (msg.has("type") && "message".equalsIgnoreCase(msg.get("type").getAsString())) {
                    history.add(new ChatMessage(msg));
                } else if (msg.has("type") && "system".equalsIgnoreCase(msg.get("type").getAsString())) {
                    systemMsgs.add(new ChatMessage(msg));
                }
            }
        }
        onJoined(data, ch, history, systemMsgs);
    }

    public void onJoined(JsonObject data, Channels channel, List<ChatMessage> history, List<ChatMessage> systemMsgs) {

    }
}