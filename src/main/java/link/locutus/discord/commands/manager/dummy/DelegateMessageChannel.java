package link.locutus.discord.commands.manager.dummy;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.requests.restaction.pagination.MessagePaginationAction;
import net.dv8tion.jda.api.requests.restaction.pagination.ReactionPaginationAction;
import net.dv8tion.jda.api.utils.AttachmentOption;
import org.jetbrains.annotations.NotNull;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Formatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DelegateMessageChannel implements MessageChannel {
    private final MessageChannel parent;

    public DelegateMessageChannel(MessageChannel parent) {
        this.parent = parent;
    }

    @Override
    @Nonnull
    public String getLatestMessageId() {
        return parent.getLatestMessageId();
    }

    @Override
    public long getLatestMessageIdLong() {
        return parent.getLatestMessageIdLong();
    }

    @Override
    public boolean hasLatestMessage() {
        return parent.hasLatestMessage();
    }

    @Override
    public boolean canTalk() {
        return parent.canTalk();
    }

    @Override
    @Nonnull
    public List<CompletableFuture<Void>> purgeMessagesById(@NotNull List<String> messageIds) {
        return parent.purgeMessagesById(messageIds);
    }

    @Override
    @Nonnull
    public List<CompletableFuture<Void>> purgeMessagesById(@NotNull String... messageIds) {
        return parent.purgeMessagesById(messageIds);
    }

    @Override
    @Nonnull
    public List<CompletableFuture<Void>> purgeMessages(@NotNull Message... messages) {
        return parent.purgeMessages(messages);
    }

    @Override
    @Nonnull
    public List<CompletableFuture<Void>> purgeMessages(@NotNull List<? extends Message> messages) {
        return parent.purgeMessages(messages);
    }

    @Override
    @Nonnull
    public List<CompletableFuture<Void>> purgeMessagesById(@NotNull long... messageIds) {
        return parent.purgeMessagesById(messageIds);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction sendMessage(@NotNull CharSequence text) {
        return parent.sendMessage(text);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction sendMessageFormat(@NotNull String format, @NotNull Object... args) {
        return parent.sendMessageFormat(format, args);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction sendMessageEmbeds(@NotNull MessageEmbed embed, @NotNull MessageEmbed... other) {
        return parent.sendMessageEmbeds(embed, other);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction sendMessageEmbeds(@NotNull Collection<? extends MessageEmbed> embeds) {
        return parent.sendMessageEmbeds(embeds);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction sendMessage(@NotNull Message msg) {
        return parent.sendMessage(msg);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction sendFile(@NotNull File file, @NotNull AttachmentOption... options) {
        return parent.sendFile(file, options);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction sendFile(@NotNull File file, @NotNull String fileName, @NotNull AttachmentOption... options) {
        return parent.sendFile(file, fileName, options);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction sendFile(@NotNull InputStream data, @NotNull String fileName, @NotNull AttachmentOption... options) {
        return parent.sendFile(data, fileName, options);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction sendFile(@NotNull byte[] data, @NotNull String fileName, @NotNull AttachmentOption... options) {
        return parent.sendFile(data, fileName, options);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Message> retrieveMessageById(@NotNull String messageId) {
        return parent.retrieveMessageById(messageId);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Message> retrieveMessageById(long messageId) {
        return parent.retrieveMessageById(messageId);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public AuditableRestAction<Void> deleteMessageById(@NotNull String messageId) {
        return parent.deleteMessageById(messageId);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public AuditableRestAction<Void> deleteMessageById(long messageId) {
        return parent.deleteMessageById(messageId);
    }

    @Override
    public MessageHistory getHistory() {
        return parent.getHistory();
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessagePaginationAction getIterableHistory() {
        return parent.getIterableHistory();
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageHistory.MessageRetrieveAction getHistoryAround(@NotNull String messageId, int limit) {
        return parent.getHistoryAround(messageId, limit);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageHistory.MessageRetrieveAction getHistoryAround(long messageId, int limit) {
        return parent.getHistoryAround(messageId, limit);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageHistory.MessageRetrieveAction getHistoryAround(@NotNull Message message, int limit) {
        return parent.getHistoryAround(message, limit);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageHistory.MessageRetrieveAction getHistoryAfter(@NotNull String messageId, int limit) {
        return parent.getHistoryAfter(messageId, limit);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageHistory.MessageRetrieveAction getHistoryAfter(long messageId, int limit) {
        return parent.getHistoryAfter(messageId, limit);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageHistory.MessageRetrieveAction getHistoryAfter(@NotNull Message message, int limit) {
        return parent.getHistoryAfter(message, limit);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageHistory.MessageRetrieveAction getHistoryBefore(@NotNull String messageId, int limit) {
        return parent.getHistoryBefore(messageId, limit);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageHistory.MessageRetrieveAction getHistoryBefore(long messageId, int limit) {
        return parent.getHistoryBefore(messageId, limit);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageHistory.MessageRetrieveAction getHistoryBefore(@NotNull Message message, int limit) {
        return parent.getHistoryBefore(message, limit);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageHistory.MessageRetrieveAction getHistoryFromBeginning(int limit) {
        return parent.getHistoryFromBeginning(limit);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> sendTyping() {
        return parent.sendTyping();
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> addReactionById(@NotNull String messageId, @NotNull String unicode) {
        return parent.addReactionById(messageId, unicode);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> addReactionById(long messageId, @NotNull String unicode) {
        return parent.addReactionById(messageId, unicode);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> addReactionById(@NotNull String messageId, @NotNull Emote emote) {
        return parent.addReactionById(messageId, emote);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> addReactionById(long messageId, @NotNull Emote emote) {
        return parent.addReactionById(messageId, emote);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> removeReactionById(@NotNull String messageId, @NotNull String unicode) {
        return parent.removeReactionById(messageId, unicode);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> removeReactionById(long messageId, @NotNull String unicode) {
        return parent.removeReactionById(messageId, unicode);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> removeReactionById(@NotNull String messageId, @NotNull Emote emote) {
        return parent.removeReactionById(messageId, emote);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> removeReactionById(long messageId, @NotNull Emote emote) {
        return parent.removeReactionById(messageId, emote);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public ReactionPaginationAction retrieveReactionUsersById(@NotNull String messageId, @NotNull String unicode) {
        return parent.retrieveReactionUsersById(messageId, unicode);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public ReactionPaginationAction retrieveReactionUsersById(long messageId, @NotNull String unicode) {
        return parent.retrieveReactionUsersById(messageId, unicode);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public ReactionPaginationAction retrieveReactionUsersById(@NotNull String messageId, @NotNull Emote emote) {
        return parent.retrieveReactionUsersById(messageId, emote);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public ReactionPaginationAction retrieveReactionUsersById(long messageId, @NotNull Emote emote) {
        return parent.retrieveReactionUsersById(messageId, emote);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> pinMessageById(@NotNull String messageId) {
        return parent.pinMessageById(messageId);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> pinMessageById(long messageId) {
        return parent.pinMessageById(messageId);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> unpinMessageById(@NotNull String messageId) {
        return parent.unpinMessageById(messageId);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> unpinMessageById(long messageId) {
        return parent.unpinMessageById(messageId);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<List<Message>> retrievePinnedMessages() {
        return parent.retrievePinnedMessages();
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageById(@NotNull String messageId, @NotNull CharSequence newContent) {
        return parent.editMessageById(messageId, newContent);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageById(long messageId, @NotNull CharSequence newContent) {
        return parent.editMessageById(messageId, newContent);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageById(@NotNull String messageId, @NotNull Message newContent) {
        return parent.editMessageById(messageId, newContent);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageById(long messageId, @NotNull Message newContent) {
        return parent.editMessageById(messageId, newContent);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageFormatById(@NotNull String messageId, @NotNull String format, @NotNull Object... args) {
        return parent.editMessageFormatById(messageId, format, args);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageFormatById(long messageId, @NotNull String format, @NotNull Object... args) {
        return parent.editMessageFormatById(messageId, format, args);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageEmbedsById(@NotNull String messageId, @NotNull MessageEmbed... newEmbeds) {
        return parent.editMessageEmbedsById(messageId, newEmbeds);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageEmbedsById(long messageId, @NotNull MessageEmbed... newEmbeds) {
        return parent.editMessageEmbedsById(messageId, newEmbeds);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageEmbedsById(@NotNull String messageId, @NotNull Collection<? extends MessageEmbed> newEmbeds) {
        return parent.editMessageEmbedsById(messageId, newEmbeds);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageEmbedsById(long messageId, @NotNull Collection<? extends MessageEmbed> newEmbeds) {
        return parent.editMessageEmbedsById(messageId, newEmbeds);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageComponentsById(@NotNull String messageId, @NotNull Collection<? extends LayoutComponent> components) {
        return parent.editMessageComponentsById(messageId, components);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageComponentsById(long messageId, @NotNull Collection<? extends LayoutComponent> components) {
        return parent.editMessageComponentsById(messageId, components);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageComponentsById(@NotNull String messageId, @NotNull LayoutComponent... components) {
        return parent.editMessageComponentsById(messageId, components);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageComponentsById(long messageId, @NotNull LayoutComponent... components) {
        return parent.editMessageComponentsById(messageId, components);
    }

    @Override
    @Nonnull
    public String getName() {
        return parent.getName();
    }

    @Override
    @Nonnull
    public ChannelType getType() {
        return parent.getType();
    }

    @Override
    @Nonnull
    public JDA getJDA() {
        return parent.getJDA();
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public AuditableRestAction<Void> delete() {
        return new AdapterAuditableRestAction<>(parent.delete());
    }

    @Override
    public String getAsMention() {
        return parent.getAsMention();
    }

    @Override
    public void formatTo(Formatter formatter, int flags, int width, int precision) {
        parent.formatTo(formatter, flags, width, precision);
    }

    @Override
    @Nonnull
    public String getId() {
        return parent.getId();
    }

    @Override
    public long getIdLong() {
        return parent.getIdLong();
    }

    @Override
    @Nonnull
    public OffsetDateTime getTimeCreated() {
        return parent.getTimeCreated();
    }
}
