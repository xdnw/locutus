package link.locutus.discord.commands.manager.v2.impl.discord;

import link.locutus.discord.Logg;
import link.locutus.discord.commands.manager.v2.command.AModalBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.IModalBuilder;
import link.locutus.discord.util.RateLimitUtil;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class DiscordHookIO implements IMessageIO {
    private final InteractionHook hook;
    private final Map<Long, IMessageBuilder> messageCache = new HashMap<>();
    private final IModalCallback modalCallback;
    private boolean originalDeleted;
    private boolean isInteraction = false;

    public DiscordHookIO(InteractionHook hook, IModalCallback modalCallback) {
        this.hook = hook;
        this.modalCallback = modalCallback;
    }

    public DiscordHookIO setInteraction(boolean isInteraction) {
        this.isInteraction = isInteraction;
        return this;
    }

    @Override
    public boolean isInteraction() {
        return true;
    }

    @Override
    public Guild getGuildOrNull() {
        if (hook != null) {
            Interaction interaction = hook.getInteraction();
            if (interaction != null && interaction.isFromGuild()) return interaction.getGuild();
        }
        if (modalCallback != null && modalCallback.isFromGuild()) {
            return modalCallback.getGuild();
        }
        return null;
    }

    public InteractionHook getHook() {
        return hook;
    }

    @Override
    public void setMessageDeleted() {
        boolean alreadyDeleted = this.originalDeleted;
        this.originalDeleted = true;
        if (!alreadyDeleted) {
            try {
                RateLimitUtil.complete(hook.deleteOriginal());
            } catch (ErrorResponseException | IllegalArgumentException ignore) {
                ignore.printStackTrace();
            }
        }
    }

    public IModalCallback getModalCallback() {
        return modalCallback;
    }

    @Override
    @Deprecated
    public IMessageBuilder getMessage() {
        if (originalDeleted) return null;
        try {
            return new DiscordMessageBuilder(this, RateLimitUtil.complete(hook.retrieveOriginal()));
        } catch (ErrorResponseException ignore) {
            return null;
        }
    }

    @Override
    public IMessageBuilder create() {
        return new DiscordMessageBuilder(this, (Message) null);
    }

    @Override
    public CompletableFuture<IMessageBuilder> send(IMessageBuilder builder) {
        if (builder instanceof DiscordMessageBuilder discMsg) {
            if (builder.getId() > 0) {
                CompletableFuture<Message> future = RateLimitUtil.queue(hook.editMessageById(builder.getId(), discMsg.buildEdit(true)));
                return future.thenApply(msg -> new DiscordMessageBuilder(this, msg));
            }
            if (discMsg.isEmpty()) return CompletableFuture.completedFuture(builder);

            List<MessageCreateData> messages = discMsg.build(true);
            List<CompletableFuture<Message>> futures = new ArrayList<>();
            for (MessageCreateData message : messages) {
                CompletableFuture<Message> future = RateLimitUtil.queue(hook.sendMessage(message));
                futures.add(future);
            }
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<Message> responseMsgs = new ArrayList<>();
                for (CompletableFuture<Message> future : futures) {
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
            Logg.error("Unsupported message builder: " + builder.getClass().getName());
            throw new IllegalArgumentException("Only DiscordMessageBuilder is supported.");
        }
    }

    @Override
    public IMessageIO update(IMessageBuilder builder, long id) {
        if (builder instanceof DiscordMessageBuilder discMsg) {
            MessageEditData message = discMsg.buildEdit(true);
            RateLimitUtil.queue(hook.editMessageById(id, message));
            return this;
        } else {
            throw new IllegalArgumentException("Only DiscordMessageBuilder is supported.");
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

    @Override
    public CompletableFuture<IModalBuilder> send(IModalBuilder builder) {
        if (modalCallback == null) {
            return DiscordChannelIO.send(this, builder);
        }
        AModalBuilder casted = (AModalBuilder) builder;
        List<Label> inputs = casted.getInputs();
        if (casted.getId() == null) {
            casted.setId(UUID.randomUUID());
        }
        UUID id = casted.getId();
        String idPair = id + " " + casted.getTitle();
        Modal modal = Modal.create(idPair, casted.getTitle())
                .addComponents(inputs)
                .build();

//        modalCallback.replyModal(modal).complete();
//        return null;
        RateLimitUtil.complete(modalCallback.replyModal(modal));
        return CompletableFuture.completedFuture(casted);
    }
}
