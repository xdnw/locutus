package link.locutus.discord.commands.manager.v2.impl.discord;

import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class DiscordChannelIO implements IMessageIO {
    private final MessageChannel channel;
    private final Supplier<Message> userMessage;

    public DiscordChannelIO(MessageChannel channel, Supplier<Message> userMessage) {
        this.channel = channel;
        this.userMessage = userMessage;
    }

    public DiscordChannelIO(MessageChannel channel) {
        this(channel, null);
    }

    public DiscordChannelIO(MessageReceivedEvent event) {
        this(event.getChannel(), event::getMessage);
    }

    @Override
    @Deprecated
    public IMessageBuilder getMessage() {
        if (userMessage == null) {
            return null;
        }
        Message msg = userMessage.get();
        if (msg == null) return null;
        return new DiscordMessageBuilder(this, msg);
    }

    @Override
    public IMessageBuilder create() {
        return new DiscordMessageBuilder(this, null);
    }

    @Deprecated
    public IMessageBuilder getMessage(long id) {
        Message message = RateLimitUtil.complete(channel.retrieveMessageById(id));
        return new DiscordMessageBuilder(this, message);
    }

    @Override
    public CompletableFuture<IMessageBuilder> send(IMessageBuilder builder) {
        if (builder instanceof DiscordMessageBuilder discMsg) {
            if (builder.getId() > 0) {
                System.out.println("Send ");
                CompletableFuture<Message> future = RateLimitUtil.queue(channel.editMessageById(builder.getId(), discMsg.build(true)));
                return future.thenApply(msg -> new DiscordMessageBuilder(this, msg));
            }
            if (discMsg.embeds.size() > 10) {
                for (MessageEmbed embed : discMsg.embeds) {
                    discMsg.content.append("**").append(embed.getTitle()).append("**\n");
                    discMsg.content.append(embed.getDescription()).append("\n");
                }
                discMsg.embeds.clear();
            }
            if (discMsg.content.length() > 2000) {
                DiscordUtil.sendMessage(channel, discMsg.content.toString());
                discMsg.content.setLength(0);
            }
            CompletableFuture<IMessageBuilder> msgFuture = null;
            if (!discMsg.content.isEmpty() || !discMsg.buttons.isEmpty() || !discMsg.embeds.isEmpty()) {
                Message message = discMsg.build(true);

                if (message.getContentRaw().length() > 20000) {
                    Message result = null;
                    if (!discMsg.buttons.isEmpty() || !discMsg.embeds.isEmpty()) {
                        message = discMsg.build(false);
                        result = RateLimitUtil.complete(channel.sendMessage(message));
                    }
                    CompletableFuture<Message> future = DiscordUtil.sendMessage(channel, discMsg.content.toString());
                    if (result != null) {
                        assert future != null;
                        msgFuture = future.thenApply(f -> new DiscordMessageBuilder(this, f));
                    }
                } else {
                    CompletableFuture<Message> future =RateLimitUtil.queue(channel.sendMessage(message));
                    msgFuture = future.thenApply(f -> new DiscordMessageBuilder(this, f));
                }


            }
            if (!discMsg.files.isEmpty() || !discMsg.images.isEmpty()) {
                Map<String, byte[]> allFiles = new HashMap<>(discMsg.files);
                allFiles.putAll(discMsg.images);
                Message result = null;
                for (Map.Entry<String, byte[]> entry : allFiles.entrySet()) {
                    result = RateLimitUtil.complete(channel.sendFile(entry.getValue(), entry.getKey()));
                }
                if (result != null && msgFuture == null)
                    msgFuture = CompletableFuture.completedFuture(new DiscordMessageBuilder(this, result));
            }
            return msgFuture;
        } else {
            throw new IllegalArgumentException("Only DiscordMessageBuilder is supported.");
        }
    }

    @Override
    public IMessageIO update(IMessageBuilder builder, long id) {
        if (builder instanceof DiscordMessageBuilder discMsg) {
            Message message = discMsg.build(true);
            RateLimitUtil.queue(channel.editMessageById(id, message));
            return this;
        } else {
            throw new IllegalArgumentException("Only DiscordMessageBuilder is supported.");
        }
    }

    @Override
    public IMessageIO delete(long id) {
        RateLimitUtil.queue(channel.deleteMessageById(id));
        return this;
    }

    @Override
    public long getIdLong() {
        return channel.getIdLong();
    }
}
