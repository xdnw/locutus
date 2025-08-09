package link.locutus.discord.chat;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import org.apache.commons.codec.DecoderException;

import java.util.Locale;
import java.util.Map;

public class ChatManager {

    private final Map<Integer, ChatClient> clientByAlliance;

    private boolean globalChatInitialized = false;
    private ChatClient globalChatClient;

    public ChatManager() throws DecoderException {
        clientByAlliance = new Int2ObjectOpenHashMap<>();
        startGlobalChatClient();
    }

    private synchronized void startGlobalChatClient() {
        if (globalChatClient != null) {
            return;
        }
        if (globalChatInitialized) {
            return;
        }
        globalChatInitialized = true;
        try {
            String token = Settings.INSTANCE.CHAT_TOKEN;
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

        }
    }

    private synchronized void checkAndUpdateAllianceChats() {
        // if nation is not in the alliance, or is deleted, quit the chat and remove from map
        // if quit is called, find a new nation with the same alliance and start the chat

        // if no nation is found, disable the chat setting for the guild
    }

    private synchronized void disableAllianceChat(int allianceId) {
        // get the chat instance, stop it and remove from map

    }

    private synchronized void enableAllianceChat(int allianceId) {
        // get the first nation in alliance with chat token set
    }
}
