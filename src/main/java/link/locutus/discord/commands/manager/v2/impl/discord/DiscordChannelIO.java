package link.locutus.discord.commands.manager.v2.impl.discord;

import link.locutus.discord.commands.manager.v2.command.*;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.util.RateLimitedSource;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.web.WebUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;

public class DiscordChannelIO implements IMessageIO {
    private final MessageChannel channel;
    private Supplier<Message> userMessage;

    // Used for command flows that want to target a DM output channel without
    // blocking at the pivot where the output IO is selected.
    private static final class DeferredOutputIO implements IMessageIO {
        private final CompletableFuture<IMessageIO> delegateFuture;
        private long localId = 1;

        private DeferredOutputIO(CompletableFuture<? extends IMessageIO> delegateFuture) {
            this.delegateFuture = delegateFuture.thenApply(io -> io);
        }

        @Override
        public IMessageBuilder getMessage(RateLimitedSource source) {
            IMessageIO delegate = delegateFuture.getNow(null);
            return delegate != null ? delegate.getMessage(source) : null;
        }

        @Override
        public Guild getGuildOrNull() {
            IMessageIO delegate = delegateFuture.getNow(null);
            return delegate != null ? delegate.getGuildOrNull() : null;
        }

        @Override
        public IMessageBuilder create() {
            return new StringMessageBuilder(this, localId++, System.currentTimeMillis(), null);
        }

        @Override
        public void setMessageDeleted(RateLimitedSource source) {
            delegateFuture.whenComplete((io, error) -> {
                if (error != null) {
                    error.printStackTrace();
                    return;
                }
                io.setMessageDeleted(source);
            });
        }

        @Override
        public CompletableFuture<IMessageBuilder> send(IMessageBuilder builder, RateLimitedSource source) {
            return delegateFuture.thenCompose(io -> io.send(builder.writeTo(io.create()), source));
        }

        @Override
        public IMessageIO update(IMessageBuilder builder, long id, RateLimitedSource source) {
            delegateFuture.whenComplete((io, error) -> {
                if (error != null) {
                    error.printStackTrace();
                    return;
                }
                io.update(builder.writeTo(io.create()), id, source);
            });
            return this;
        }

        @Override
        public IMessageIO delete(long id, RateLimitedSource source) {
            delegateFuture.whenComplete((io, error) -> {
                if (error != null) {
                    error.printStackTrace();
                    return;
                }
                io.delete(id, source);
            });
            return this;
        }

        @Override
        public long getIdLong() {
            IMessageIO delegate = delegateFuture.getNow(null);
            return delegate != null ? delegate.getIdLong() : 0;
        }

        @Override
        public CompletableFuture<IModalBuilder> send(IModalBuilder builder, RateLimitedSource source) {
            return delegateFuture.thenCompose(io -> io.send(builder, source));
        }
    }

    public DiscordChannelIO(MessageChannel channel, Supplier<Message> userMessage) {
        this.channel = channel;
        this.userMessage = userMessage;
    }

    @Override
    public Guild getGuildOrNull() {
        if (channel instanceof GuildChannel gc) {
            return gc.getGuild();
        }
        return null;
    }

    public DiscordChannelIO(MessageChannel channel) {
        this(channel, null);
    }

    public DiscordChannelIO(MessageReceivedEvent event) {
        this(event.getChannel(), event::getMessage);
    }

    public static IMessageIO privateOutput(User user, RateLimitedSource source) {
        return new DeferredOutputIO(RateLimitUtil.queue(user.openPrivateChannel(), source)
                .thenApply(DiscordChannelIO::new));
    }

    public MessageChannel getChannel() {
        return channel;
    }

    public Message getUserMessage() {
        return userMessage != null ? userMessage.get() : null;
    }

    @Override
    public void setMessageDeleted(RateLimitedSource source) {
        userMessage = null;
    }

    @Override
    @Deprecated
    public IMessageBuilder getMessage(RateLimitedSource source) {
        if (userMessage == null) {
            return null;
        }
        Message msg = userMessage.get();
        if (msg == null) return null;
        return new DiscordMessageBuilder(this, msg);
    }

    @Override
    public IMessageBuilder create() {
        return new DiscordMessageBuilder(this, (Message) null);
    }

    @Deprecated
    public IMessageBuilder getMessage(long id, RateLimitedSource source) {
        Message message = RateLimitUtil.complete(channel.retrieveMessageById(id), source);
        return new DiscordMessageBuilder(this, message);
    }

    @Override
    public CompletableFuture<IMessageBuilder> send(IMessageBuilder builder, RateLimitedSource source) {
        if (builder instanceof DiscordMessageBuilder discMsg) {
            if (builder.getId() > 0) {
                String key = "message-edit:" + channel.getIdLong() + ":" + builder.getId();
                CompletableFuture<Message> future = RateLimitUtil.queueLatest(key, source,
                        () -> RateLimitUtil.queue(channel.editMessageById(builder.getId(), discMsg.buildEdit(true)), source));
                return future.thenApply(msg -> new DiscordMessageBuilder(this, msg));
            }
            if (discMsg.isEmpty()) return CompletableFuture.completedFuture(builder);

            List<MessageCreateData> messages = discMsg.build(true);
            List<Future<Message>> futures = new ArrayList<>();
            for (MessageCreateData message : messages) {
                CompletableFuture<Message> future = RateLimitUtil.queue(channel.sendMessage(message), source);
                futures.add(future);
            }
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                List<Message> responseMsgs = new ArrayList<>();
                for (Future<Message> future : futures) {
                    try {
                        Message msg = future.get();
                        responseMsgs.add(msg);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return new DiscordMessageBuilder(this, responseMsgs);
            });
        } else {
            throw new IllegalArgumentException("Only DiscordMessageBuilder is supported.");
        }
    }

    @Override
    public IMessageIO update(IMessageBuilder builder, long id, RateLimitedSource source) {
        if (builder instanceof DiscordMessageBuilder discMsg) {
            MessageEditData message = discMsg.buildEdit(true);
            String key = "message-edit:" + channel.getIdLong() + ":" + id;
            RateLimitUtil.queueLatest(key, source,
                    () -> RateLimitUtil.queue(channel.editMessageById(id, message), source));
            return this;
        } else {
            throw new IllegalArgumentException("Only DiscordMessageBuilder is supported.");
        }
    }

    @Override
    public IMessageIO delete(long id, RateLimitedSource source) {
        RateLimitUtil.queue(channel.deleteMessageById(id), source);
        return this;
    }

    @Override
    public long getIdLong() {
        return channel.getIdLong();
    }

    @Override
    public CompletableFuture<IModalBuilder> send(IModalBuilder builder, RateLimitedSource source) {
        return send(this, builder, source);
    }

    public static CompletableFuture<IModalBuilder> send(IMessageIO io, IModalBuilder builder, RateLimitedSource source) {
        AModalBuilder record = (AModalBuilder) builder;
        UUID id = builder.getId();
        String defaultsStr = null;
        try {
            Map<String, String> defaults = IModalBuilder.DEFAULT_VALUES.get(id);
            if (defaults != null && !defaults.isEmpty()) {
                // map to json google
                defaultsStr = WebUtil.GSON.toJson(defaults);
                IModalBuilder.DEFAULT_VALUES.refresh(id);
            }
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
        String cmd = builder.getTitle();
        List<String> argList = new ArrayList<>();
        for (TextInput input : record.getInputs()) {
            String argName = input.getCustomId();
            argList.add(argName);
        }
        CM.modal.create cmRef = CM.modal.create.cmd.command(cmd).arguments(StringMan.join(argList, " ")).defaults(defaultsStr);
        io.create().embed("Form: `" + cmd + "`", cmRef.toSlashCommand(true))
                .commandButton(cmRef, "Open")
                .send(source);
        return CompletableFuture.completedFuture(builder);
    }
}
