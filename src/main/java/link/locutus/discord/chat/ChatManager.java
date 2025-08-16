package link.locutus.discord.chat;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.commands.manager.v2.command.StringMessageIO;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.task.mail.MailApiResponse;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.apache.commons.codec.DecoderException;

import java.net.http.WebSocket;
import java.util.*;

public class ChatManager {

    private final Map<Integer, ChatClient> clientByAlliance;
    private final Map<Long, Set<Integer>> alliancesByGuildCache;

    private boolean globalChatInitialized = false;
    private ChatClient globalChatClient;

    public ChatManager() throws DecoderException {
        clientByAlliance = new Int2ObjectOpenHashMap<>();
        alliancesByGuildCache = new Long2ObjectOpenHashMap<>();
        startGlobalChatClient();
    }

    private synchronized void startGlobalChatClient() {
        if (globalChatClient != null) {
            return;
        }
        if (globalChatInitialized) {
            return;
        }
        String token = Settings.INSTANCE.CHAT_TOKEN;
        if (token.isEmpty()) return;

        globalChatInitialized = true;
        try {
            byte[] tokenBytes = ChatClient.parseToken(token);
            globalChatClient = new ChatClient(ChatClient.WS_DEFAULT_URL, token.toLowerCase(Locale.ROOT), Settings.INSTANCE.NATION_ID, Channels.GLOBAL);
            globalChatClient.start();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void setGlobalToken(String token) throws DecoderException {
        if (token.equalsIgnoreCase(Settings.INSTANCE.CHAT_TOKEN)) return;

        ChatClient.parseToken(token);
        if (globalChatClient != null) {
            globalChatClient.quit();
            globalChatInitialized = false;
            globalChatClient = null;
        }
        Settings.INSTANCE.CHAT_TOKEN = token;
        Settings.INSTANCE.save(Settings.INSTANCE.getDefaultFile());

        startGlobalChatClient();
    }

    private synchronized void startAllianceChats() {
        for (Map.Entry<Long, GuildDB> entry : Locutus.imp().getGuildDatabases().entrySet()) {
            boolean started = startAllianceChatOrDisable(entry.getValue(), false, false);
        }
    }

    private synchronized boolean startAllianceChatOrDisable(GuildDB db, boolean checkToken, boolean checkGuild) {
        if (!startAllianceChat(db)) {
            for (int aid : db.getAllianceIds()) {
                ChatClient client = clientByAlliance.remove(aid);
                if (client == null) continue;
                client.quit();
            }
            for (int aid : alliancesByGuildCache.getOrDefault(db.getIdLong(), Collections.emptySet())) {
                if (db.isAllianceId(aid)) continue;
                ChatClient chatClient = clientByAlliance.get(aid);
                if (chatClient == null) continue;
                if (!chatClient.isValidAllianceChat(aid, checkToken, checkGuild)) {
                    clientByAlliance.remove(aid);
                    chatClient.quit();
                }
            }
            return false;
        }
        return true;
    }

    private synchronized void checkAndUpdateAllianceChats(boolean checkToken, boolean checkGuild) {
        Iterator<Map.Entry<Integer, ChatClient>> iter = clientByAlliance.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Integer, ChatClient> entry = iter.next();
            int allianceId = entry.getKey();
            ChatClient client = entry.getValue();
            if (!client.isValidAllianceChat(allianceId, checkToken, checkGuild)) {
                iter.remove();
                client.quit();
                // find a new nation with the same alliance
                GuildDB db = Locutus.imp().getGuildDBByAA(allianceId);
                if (db != null) {
                    startAllianceChatOrDisable(db, false, false);
                }
            }
        }
    }

    private synchronized void enableAllianceChat(int allianceId) {
        GuildDB db = Locutus.imp().getGuildDBByAA(allianceId);
        if (db == null) return;
        if (!db.hasAlliance()) return;
        startAllianceChat(db);
    }

    private synchronized boolean startAllianceChat(GuildDB db) {
        Set<Integer> accounts = GuildKey.GAME_CHAT_ACCOUNT.get(db);
        if (accounts == null || accounts.isEmpty()) return false;
        ApiKeyPool mailKey = db.getMailKey();
        if (mailKey == null) return false;

        Map<Integer, String> unsetAccounts = null;
        Map<Integer, Map.Entry<Integer, String>> tokens = null;

        for (int nationId : accounts) {
            DBNation nation = DBNation.getById(nationId);
            if (nation == null) {
                if (unsetAccounts == null) unsetAccounts = new Int2ObjectOpenHashMap<>();
                unsetAccounts.put(nationId, "Nation not found");
                continue;
            } else if (nation.getPositionEnum().id <= Rank.APPLICANT.id) {
                if (unsetAccounts == null) unsetAccounts = new Int2ObjectOpenHashMap<>();
                unsetAccounts.put(nationId, "Nation is not in alliance or is applicant");
                continue;
            } else if (!db.isAllianceId(nation.getAlliance_id())) {
                if (unsetAccounts == null) unsetAccounts = new Int2ObjectOpenHashMap<>();
                unsetAccounts.put(nationId, "Nation is not in the alliance/s: " + db.getAllianceIds());
                continue;
            }
            String token = Locutus.imp().getDiscordDB().getChatToken(nationId);
            if (token == null) {
                if (unsetAccounts == null) unsetAccounts = new Int2ObjectOpenHashMap<>();
                unsetAccounts.put(nationId, "Nation does not have a chat token set. Set one with TODO CM REF");
                continue;
            }
            if (tokens == null) tokens = new Int2ObjectOpenHashMap<>();
            tokens.put(nationId, Map.entry(nation.getAlliance_id(), token));
        }

        if (unsetAccounts != null) {
            accounts.removeAll(unsetAccounts.keySet());

            if (accounts.isEmpty()) {
                GuildKey.GAME_CHAT_ACCOUNT.delete(db, null);
            } else {
                GuildKey.GAME_CHAT_ACCOUNT.set(db, null, accounts);
            }

            MessageChannel alertChannel = GuildKey.GAME_CHAT_CHANNEL.getOrNull(db);
            if (alertChannel == null) {
                alertChannel = db.getNotifcationChannel();
            }

            if (alertChannel != null) {
                // send message describing the above changes
                StringBuilder sb = new StringBuilder("Removed invalid accounts from setting: `" + GuildKey.GAME_CHAT_ACCOUNT.name() + "`:\n");
                for (Map.Entry<Integer, String> entry2 : unsetAccounts.entrySet()) {
                    sb.append("- Nation ID ").append(entry2.getKey())
                            .append(": ").append(entry2.getValue()).append("\n");
                }
                RateLimitUtil.queue(alertChannel.sendMessage(sb.toString()));
            }
        }

        if (accounts.isEmpty()) return false;
        for (Map.Entry<Integer, Map.Entry<Integer, String>> entry : tokens.entrySet()) {
            int nationId = entry.getKey();
            int allianceId = entry.getValue().getKey();
            String token = entry.getValue().getValue();

            ChatClient existingClient = clientByAlliance.get(allianceId);
            if (existingClient != null) {
                existingClient.quit();
                clientByAlliance.remove(allianceId);
            }
            DBAlliance alliance = DBAlliance.getOrCreate(allianceId);
            ChatClient newClient = new ChatClient(ChatClient.WS_DEFAULT_URL, token, nationId, Channels.ALLIANCE_ALL) {
                @Override
                public void onError(WebSocket socket, Throwable err) {
                    // idk, print it for now, or post it to the channel i need to figure out what errors get thrown
                    err.printStackTrace();
                    MessageChannel alertChannel = GuildKey.GAME_CHAT_CHANNEL.getOrNull(db);
                    if (alertChannel != null && alertChannel.canTalk()) {
                        String msgFormatted = "[" + alliance.getMarkdownUrl() + "] " + err.getMessage();
                        RateLimitUtil.queue(alertChannel.sendMessage(msgFormatted));
                    }
                }

                @Override
                public void onChatMessage(ChatMessage msg) {
                    handleChatMessage(this, db, allianceId, msg);
                }

                @Override
                public void onJoined(JsonObject data, Channels channel, List<ChatMessage> history, List<ChatMessage> systemMsgs) {
                    handleHistory(this, db, allianceId, history);
                }
            };
            try {
                newClient.start();
            } catch (Throwable e) {
                newClient.quit();
                throw e;
            }
            clientByAlliance.put(allianceId, newClient);
            alliancesByGuildCache.computeIfAbsent(db.getIdLong(), k -> new IntOpenHashSet()).add(allianceId);
        }
        return true;
    }

    public void handleChatMessage(ChatClient client, GuildDB db, int allianceId, ChatClient.ChatMessage msg) {
        // Handle the chat message here
        // This method should be implemented to process the chat messages received from the clients
        // For example, you can log the message, send it to a specific channel, etc.
        int nationId = msg.user.accountId;
        if (nationId == client.getAccountId()) {
            System.out.println("Received message from the same account, ignoring: " + msg.message + " | " + msg.id);
            return; // Ignore messages from the same account
        }
        String mention = "@" + msg.user.leaderName;

        // Guild guild, IMessageIO io, User author, String command, boolean async, boolean returnNotFound
        DBNation nation = DBNation.getOrCreate(nationId);
        User user = nation.getUser();
        Guild guild = db.getGuild();
        ApiKeyPool mailKey = db.getMailKey();
        if (mailKey == null) {
            synchronized (this) {
                if (clientByAlliance.get(allianceId) == client) {
                    clientByAlliance.remove(allianceId);
                } else {
                    clientByAlliance.entrySet().removeIf(entry -> entry.getValue() == client);
                }
                client.quit();
            }
            return;
        }

        StringMessageIO io = new StringMessageIO(user, guild);
        String command = msg.message;

        Locutus.cmd().getExecutor().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    client.sendMessage(Channels.ALLIANCE_ALL, mention + " Please wait...");
                    Locutus.cmd().getV2().run(db.getGuild(), io, user, command, false, false);
                    String html = io.toSimpleHtml(true, false);
                    if (!html.isEmpty()) {
                        String title = "Alliance Chat Message";
                        MailApiResponse mail = nation.sendMail(mailKey, title, html, true);
                        String url = Settings.PNW_URL() + "/inbox/";
                        client.sendMessage(Channels.ALLIANCE_ALL, mention + " " + url);
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void handleHistory(ChatClient client, GuildDB db, int allianceId, List<ChatClient.ChatMessage> history) {
        // Handle the chat history here
        // This method should be implemented to process the chat history received from the clients
        // For example, you can log the history, send it to a specific channel, etc.

        // Do nothing for now
    }
}
