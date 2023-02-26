package link.locutus.discord.commands.manager.dummy;

import net.dv8tion.jda.api.entities.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class DelegateChannelMessage extends DelegateMessage {
    private final GuildMessageChannel channel;

    public DelegateChannelMessage(Message parent, GuildMessageChannel channel) {
        super(parent);
        this.channel = channel;
    }

    @Nonnull
    @Override
    public ChannelType getChannelType() {
        return channel.getType();
    }

    @Nonnull
    @Override
    public MessageChannel getChannel() {
        return channel;
    }

    @Nonnull
    @Override
    public PrivateChannel getPrivateChannel() {
        return null;
    }

    @Nonnull
    @Override
    public GuildMessageChannel getGuildChannel() {
        return channel;
    }

    @Nullable
    @Override
    public Category getCategory() {
        return channel instanceof ICategorizableChannel ? ((ICategorizableChannel) channel).getParentCategory() : null;
    }
}
