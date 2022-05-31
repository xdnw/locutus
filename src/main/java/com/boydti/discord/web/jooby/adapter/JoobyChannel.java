package com.boydti.discord.web.jooby.adapter;

import com.boydti.discord.web.jooby.handler.IMessageOutput;
import com.boydti.discord.web.jooby.handler.SseClient2;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.javalin.http.sse.SseClient;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.IPermissionContainer;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.managers.channel.ChannelManager;
import net.dv8tion.jda.api.managers.channel.concrete.TextChannelManager;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import net.dv8tion.jda.api.requests.restaction.InviteAction;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.requests.restaction.WebhookAction;
import net.dv8tion.jda.api.requests.restaction.pagination.ThreadChannelPaginationAction;
import net.dv8tion.jda.api.utils.AttachmentOption;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class JoobyChannel implements TextChannel {
    private static long MAX_FILE_LENGTH = 20_000_000;
    private final Guild guild;
    private final IMessageOutput sse;
    private final File fileDir;

    public JoobyChannel(Guild guild, IMessageOutput sse, File fileDir) {
        this.guild = guild;
        this.sse = sse;
        this.fileDir = fileDir;
    }

    @Nonnull
    @Override
    public MessageAction sendFile(@Nonnull File file, @Nonnull AttachmentOption... options) {
        return sendFile(file, file.getName(), options);
    }

    @Nonnull
    @Override
    public MessageAction sendFile(@Nonnull File file, @Nonnull String fileName, @Nonnull AttachmentOption... options) {
        if (file.length() > MAX_FILE_LENGTH) throw new IllegalArgumentException("File > " + MAX_FILE_LENGTH);
        try {
            return sendFile(Files.readAllBytes(file.toPath()), fileName, options);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nonnull
    @Override
    public MessageAction sendFile(@Nonnull byte[] data, @Nonnull String fileName, @Nonnull AttachmentOption... options) {
        if (data.length > MAX_FILE_LENGTH) throw new IllegalArgumentException("File > " + MAX_FILE_LENGTH);
        return new JoobyMessageAction(getJDA(), this, fileDir, sse).addFile(data, fileName, options);
    }

    @Nonnull
    @Override
    public MessageAction sendFile(@Nonnull InputStream data, @Nonnull String fileName, @Nonnull AttachmentOption... options) {
        try {
            byte[] bytes = ByteStreams.toByteArray(data);
            return sendFile(bytes, fileName, options);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Nonnull
    @Override
    public RestAction<Void> deleteMessages(@Nonnull Collection<Message> messages) {
        return deleteMessagesByIds(messages.stream().map(f -> f.getId()).collect(Collectors.toSet()));
    }

    @Nonnull
    @Override
    public RestAction<Void> deleteMessagesByIds(@Nonnull Collection<String> messageIds) {
        return deleteByIdStr(messageIds);
    }

    private RestAction<Void> deleteByIdStr(Collection<String> ids) {
        return deleteById(ids.stream().map(Long::parseLong).collect(Collectors.toSet()));
    }

    private RestAction<Void> deleteById(Collection<Long> ids) {
        JsonObject obj = new JsonObject();
        obj.addProperty("action", "deleteByIds");
        JsonArray idArr = new JsonArray();
        for (Long id : ids) {
            idArr.add(id + "");
        }
        obj.add("value", idArr);
        return new JoobyRestAction(getJDA(), () -> sse.sendEvent(obj));
    }

    @Nonnull
    @Override
    public RestAction<Void> clearReactionsById(@Nonnull String messageId) {
        return deleteByIdStr(Collections.singleton(messageId));
    }

    @Nonnull
    @Override
    public RestAction<Void> clearReactionsById(@Nonnull String messageId, @Nonnull String unicode) {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public RestAction<Void> clearReactionsById(@Nonnull String messageId, @Nonnull Emote emote) {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public RestAction<Void> removeReactionById(@Nonnull String messageId, @Nonnull String unicode, @Nonnull User user) {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public MessageAction sendMessage(@Nonnull Message msg) {
        return new JoobyMessageAction(getJDA(), this, fileDir, sse).apply(msg);
    }

    @Nonnull
    @Override
    public MessageAction sendMessage(@Nonnull CharSequence text) {
        return new JoobyMessageAction(getJDA(), this, fileDir, sse).content(text.toString());
    }

    @Nonnull
    @Override
    public MessageAction sendMessageEmbeds(@Nonnull MessageEmbed embed, @Nonnull MessageEmbed... other) {
        return new JoobyMessageAction(getJDA(), this, fileDir, sse).setEmbeds(embed);
    }

    @Nonnull
    @Override
    public MessageAction sendMessageFormat(@Nonnull String format, @Nonnull Object... args) {
        return new JoobyMessageAction(getJDA(), this, fileDir, sse).appendFormat(format, args);
    }

    @Nonnull
    @Override
    public MessageAction editMessageById(long messageId, @Nonnull Message newContent) {
        return new JoobyMessageAction(getJDA(), this, fileDir, sse).setId(messageId).apply(newContent);
    }

    @Nonnull
    @Override
    public MessageAction editMessageById(@Nonnull String messageId, @Nonnull Message newContent) {
        return editMessageById(Long.parseLong(messageId), newContent);
    }

    @Nonnull
    @Override
    public MessageAction sendMessageEmbeds(@Nonnull Collection<? extends MessageEmbed> embeds) {
        return null;
    }

    @Nonnull
    @Override
    public MessageAction editMessageEmbedsById(long messageId, @Nonnull MessageEmbed... newEmbeds) {
        return new JoobyMessageAction(getJDA(), this, fileDir, sse).setId(messageId).setEmbeds(newEmbeds);
    }

    @Nonnull
    @Override
    public MessageAction editMessageById(long messageId, @Nonnull CharSequence newContent) {
        return new JoobyMessageAction(getJDA(), this, fileDir, sse).setId(messageId).content(newContent.toString());
    }

    @Nonnull
    @Override
    public MessageAction editMessageEmbedsById(@Nonnull String messageId, @Nonnull MessageEmbed... newEmbeds) {
        return editMessageEmbedsById(Long.parseLong(messageId), newEmbeds);
    }

    @Nonnull
    @Override
    public MessageAction editMessageById(@Nonnull String messageId, @Nonnull CharSequence newContent) {
        return editMessageById(Long.parseLong(messageId), newContent);
    }

    @Nonnull
    @Override
    public MessageAction editMessageFormatById(long messageId, @Nonnull String format, @Nonnull Object... args) {
        return new JoobyMessageAction(getJDA(), this, fileDir, sse).setId(messageId).appendFormat(format, args);
    }

    @Nonnull
    @Override
    public MessageAction editMessageFormatById(@Nonnull String messageId, @Nonnull String format, @Nonnull Object... args) {
        return editMessageFormatById(Long.parseLong(messageId), format, args);
    }


    @Nullable
    @Override
    public String getTopic() {
        return "";
    }

    @Override
    public boolean isNSFW() {
        return false;
    }

    @Override
    public int getSlowmode() {
        return 0;
    }

    @Nonnull
    @Override
    public ChannelType getType() {
        return ChannelType.TEXT;
    }

    @Override
    public long getLatestMessageIdLong() {
        return 0;
    }

    @Override
    public boolean hasLatestMessage() {
        return false;
    }

    @Nonnull
    @Override
    public String getName() {
        return "__VIRTUAL__";
    }

    @Nonnull
    @Override
    public Guild getGuild() {
        return guild;
    }

    @Nullable
    @Override
    public Category getParentCategory() {
        return null;
    }

    @Nonnull
    @Override
    public List<Member> getMembers() {
        return guild.getMembers();
    }

    @Override
    public int getPosition() {
        return 0;
    }

    @Override
    public int getPositionRaw() {
        return 0;
    }

    @Nonnull
    @Override
    public JDA getJDA() {
        return guild.getJDA();
    }

    @Nullable
    @Override
    public PermissionOverride getPermissionOverride(@Nonnull IPermissionHolder permissionHolder) {
        return null;
    }

    @Nonnull
    @Override
    public List<PermissionOverride> getPermissionOverrides() {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public List<PermissionOverride> getMemberPermissionOverrides() {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public List<PermissionOverride> getRolePermissionOverrides() {
        return Collections.emptyList();
    }

    @Override
    public boolean isSynced() {
        return false;
    }

    @Nonnull
    @Override
    public ChannelAction<TextChannel> createCopy(@Nonnull Guild guild) {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public ChannelAction<TextChannel> createCopy() {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public TextChannelManager getManager() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getParentCategoryIdLong() {
        Category parent = getParentCategory();
        return parent != null ? parent.getIdLong() : 0;
    }

    @Nonnull
    @Override
    public AuditableRestAction<Void> delete() {
        throw new UnsupportedOperationException();
    }

    @Override
    public IPermissionContainer getPermissionContainer() {
        return null;
    }

    @Nonnull
    @Override
    public PermissionOverrideAction createPermissionOverride(@Nonnull IPermissionHolder permissionHolder) {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public PermissionOverrideAction putPermissionOverride(@Nonnull IPermissionHolder permissionHolder) {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public InviteAction createInvite() {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public RestAction<List<Invite>> retrieveInvites() {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public RestAction<List<Webhook>> retrieveWebhooks() {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public WebhookAction createWebhook(@Nonnull String name) {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public AuditableRestAction<Void> deleteWebhookById(@Nonnull String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canTalk() {
        return false;
    }

    @Override
    public boolean canTalk(@Nonnull Member member) {
        return false;
    }

    @Override
    public int compareTo(@NotNull GuildChannel o) {
        return 0;
    }

    @Nonnull
    @Override
    public String getAsMention() {
        return "<@-1>";
    }

    @Override
    public long getIdLong() {
        return -1;
    }


    @Nonnull
    @Override
    public AuditableRestAction<Void> deleteMessageById(long messageId) {
        return new AuditableRestActionDelegate<>(deleteById(Collections.singleton(messageId)));
    }

    @Nonnull
    @Override
    public ThreadChannelAction createThreadChannel(String name, boolean isPrivate) {
        return null;
    }

    @Nonnull
    @Override
    public ThreadChannelAction createThreadChannel(String name, long messageId) {
        return null;
    }

    @Nonnull
    @Override
    public ThreadChannelPaginationAction retrieveArchivedPublicThreadChannels() {
        return null;
    }

    @Nonnull
    @Override
    public ThreadChannelPaginationAction retrieveArchivedPrivateThreadChannels() {
        return null;
    }

    @Nonnull
    @Override
    public ThreadChannelPaginationAction retrieveArchivedPrivateJoinedThreadChannels() {
        return null;
    }
}
