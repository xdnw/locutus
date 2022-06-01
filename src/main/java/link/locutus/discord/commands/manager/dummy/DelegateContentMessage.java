package link.locutus.discord.commands.manager.dummy;

import net.dv8tion.jda.api.entities.Message;

import javax.annotation.Nonnull;

public class DelegateContentMessage extends DelegateMessage {
    private final String content;

    public DelegateContentMessage(Message parent, String content) {
        super(parent);
        this.content = content;
    }

    @Nonnull
    @Override
    public String getContentRaw() {
        return content;
    }
}
