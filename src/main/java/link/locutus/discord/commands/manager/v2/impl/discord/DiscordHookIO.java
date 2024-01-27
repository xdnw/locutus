package link.locutus.discord.commands.manager.v2.impl.discord;

import link.locutus.discord.commands.manager.v2.command.AModalBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.IModalBuilder;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DiscordHookIO implements IMessageIO {
    private final InteractionHook hook;
    private final Map<Long, IMessageBuilder> messageCache = new HashMap<>();
    private final IModalCallback modalCallback;
    private boolean originalDeleted;
    private IReplyCallback event;

    public DiscordHookIO(InteractionHook hook, IModalCallback modalCallback) {
        this.hook = hook;
        this.modalCallback = modalCallback;
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
        if (event != null && event.isFromGuild()) {
            return event.getGuild();
        }
        return null;
    }

    public InteractionHook getHook() {
        return hook;
    }

    @Override
    public void setMessageDeleted() {
        this.originalDeleted = true;
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
        return new DiscordMessageBuilder(this, null);
    }

    @Override
    public CompletableFuture<IMessageBuilder> send(IMessageBuilder builder) {
        if (event != null) {
            RateLimitUtil.queue(event.deferReply());
            event = null;
        }
        if (builder instanceof DiscordMessageBuilder discMsg) {
            if (builder.getId() > 0) {
                CompletableFuture<Message> future = RateLimitUtil.queue(hook.editMessageById(builder.getId(), discMsg.buildEdit(true)));
                return future.thenApply(msg -> new DiscordMessageBuilder(this, msg));
            }
            if (discMsg.content.length() > 2000) {
                DiscordUtil.sendMessage(hook, discMsg.content.toString());
                discMsg.content.setLength(0);
            }
            CompletableFuture<IMessageBuilder> msgFuture = null;
            boolean sendFiles = true;
            if (!discMsg.content.isEmpty() || !discMsg.buttons.isEmpty() || !discMsg.embeds.isEmpty()) {
                MessageCreateData message = discMsg.build(true);
                if (message.getContent().length() > 20000) {
                    Message result = null;
                    if (!discMsg.buttons.isEmpty() || !discMsg.embeds.isEmpty()) {
                        message = discMsg.build(false);
                        result = RateLimitUtil.complete(hook.sendMessage(message));
                    }
                    CompletableFuture<Message> future = DiscordUtil.sendMessage(hook, discMsg.content.toString());
                    if (result != null) {
                        assert future != null;
                        msgFuture = future.thenApply(f -> new DiscordMessageBuilder(this, f));
                    }
                } else {
                    sendFiles = false;
                    CompletableFuture<Message> future = RateLimitUtil.queue(hook.sendMessage(message));
                    msgFuture = future.thenApply(f -> new DiscordMessageBuilder(this, f));
                }
            }
            if (sendFiles && (!discMsg.files.isEmpty() || !discMsg.images.isEmpty())) {
                Map<String, byte[]> allFiles = new HashMap<>(discMsg.files);
                allFiles.putAll(discMsg.images);
                Message result = null;
                for (Map.Entry<String, byte[]> entry : allFiles.entrySet()) {
                    result = RateLimitUtil.complete(hook.sendFiles(FileUpload.fromData(entry.getValue(), entry.getKey())));
                }
                if (result != null && msgFuture == null)
                    msgFuture = CompletableFuture.completedFuture(new DiscordMessageBuilder(this, result));
            }
            return msgFuture;
        } else {
            System.out.println("Unsupported message builder: " + builder.getClass().getName());
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
        List<TextInput> inputs = casted.getInputs();
        if (casted.getId() == null) {
            casted.setId(UUID.randomUUID());
        }
        UUID id = casted.getId();
        String idPair = id + " " + casted.getTitle();
        Modal modal = Modal.create(idPair, casted.getTitle())
                .addActionRows(ActionRow.partitionOf(inputs))
                .build();

        System.out.println("Sending modal " + idPair);

//        modalCallback.replyModal(modal).complete();
//        return null;
        return RateLimitUtil.queue(modalCallback.replyModal(modal)).thenApply(f -> casted);
    }

    public void setIsModal(IReplyCallback event) {
        this.event = event;
    }
}
