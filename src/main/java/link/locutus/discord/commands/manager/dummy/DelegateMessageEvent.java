package link.locutus.discord.commands.manager.dummy;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import javax.annotation.Nonnull;

public class DelegateMessageEvent extends MessageReceivedEvent {
    private final Guild guild;

    public DelegateMessageEvent(Guild guild, long responseId, Message message) {
        super(guild.getJDA(), responseId, message);
        this.guild = guild;
    }

    @Override
    public boolean isFromGuild() {
        return guild != null;
    }

    @Nonnull
    @Override
    public Guild getGuild() {
        return guild;
    }
}