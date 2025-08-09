package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.chat.ChatClient;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Ephemeral;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.NationMeta;
import org.apache.commons.codec.DecoderException;

import java.util.Map;

public class ChatCommands {

    @Ephemeral
    @Command
    public String token(@Me IMessageIO io, @Me DBNation nation, @Default String token) throws DecoderException {
        Map.Entry<String, Boolean> validInfo = ChatClient.validateToken(nation.getId(), token);
        if (!validInfo.getValue()) {
            return validInfo.getKey();
        }

        byte[] arr = ChatClient.parseToken(token);
        nation.setMeta(NationMeta.CHAT_TOKEN, arr);
        return validInfo.getKey() + ". Set your chat token successfully. You can now use the chat commands.";
    }
}
