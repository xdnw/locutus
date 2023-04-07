package link.locutus.discord.commands.manager.v2.impl.discord;

import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.ArrayUtil;
import net.dv8tion.jda.api.entities.Channel;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.InteractionHook;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DiscordHookIO implements IMessageIO {
    private final InteractionHook hook;
    private final Map<Long, IMessageBuilder> messageCache = new HashMap<>();

    public DiscordHookIO(InteractionHook hook) {
        this.hook = hook;
    }

    @Override
    @Deprecated
    public IMessageBuilder getMessage() {
        return new DiscordMessageBuilder(this, hook.retrieveOriginal().complete());
    }

    @Override
    public IMessageBuilder create() {
        return new DiscordMessageBuilder(this, null);
    }

    @Override
    public CompletableFuture<IMessageBuilder> send(IMessageBuilder builder) {
        if (builder instanceof DiscordMessageBuilder discMsg) {
            if (builder.getId() > 0) {
                CompletableFuture<Message> future = RateLimitUtil.queue(hook.editMessageById(builder.getId(), discMsg.build(true)));
                return future.thenApply(msg -> new DiscordMessageBuilder(this, msg));
            }
            if (discMsg.content.length() > 2000) {
                DiscordUtil.sendMessage(hook, discMsg.content.toString());
                discMsg.content.setLength(0);
            }
            CompletableFuture<IMessageBuilder> msgFuture = null;
            if (!discMsg.content.isEmpty() || !discMsg.buttons.isEmpty() || !discMsg.embeds.isEmpty()) {
                Message message = discMsg.build(true);

                if (message.getContentRaw().length() > 20000) {
                    Message result = null;
                    if (!discMsg.buttons.isEmpty() || !discMsg.embeds.isEmpty()) {
                        message = discMsg.build(false);
                        result = hook.sendMessage(message).complete();
                    }
                    CompletableFuture<Message> future = DiscordUtil.sendMessage(hook, discMsg.content.toString());
                    if (result != null) {
                        msgFuture = future.thenApply(f -> new DiscordMessageBuilder(this, f));
                    }
                } else {
                    CompletableFuture<Message> future = hook.sendMessage(message).submit();
                    msgFuture = future.thenApply(f -> new DiscordMessageBuilder(this, f));
                }
            }
            if (!discMsg.files.isEmpty() || !discMsg.images.isEmpty()) {
                Map<String, byte[]> allFiles = new HashMap<>(discMsg.files);
                allFiles.putAll(discMsg.images);
                Message result = null;
                for (Map.Entry<String, byte[]> entry : allFiles.entrySet()) {
                    result = hook.sendFile(entry.getValue(), entry.getKey()).complete();
                }
                if (result != null && msgFuture == null) msgFuture = CompletableFuture.completedFuture(new DiscordMessageBuilder(this, result));
            }
            return msgFuture;
        } else {
            throw new IllegalArgumentException("Only DiscordMessageBuilder is supported");
        }
    }

    @Override
    public IMessageIO update(IMessageBuilder builder, long id) {
        if (builder instanceof DiscordMessageBuilder discMsg) {
            Message message = discMsg.build(true);
            RateLimitUtil.queue(hook.editMessageById(id, message));
            return this;
        } else {
            throw new IllegalArgumentException("Only DiscordMessageBuilder is supported");
        }
    }

    @Override
    public IMessageIO delete(long id) {
        RateLimitUtil.queue(hook.deleteMessageById(id));
        return this;
    }

    @Override
    public long getIdLong() {
        Interaction interaction = hook.getInteraction();
        Channel channel = interaction.getChannel();
        if (channel != null) return channel.getIdLong();
        return 0;
    }
}
