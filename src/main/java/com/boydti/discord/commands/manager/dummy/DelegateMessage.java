package com.boydti.discord.commands.manager.dummy;

import com.boydti.discord.Locutus;
import com.boydti.discord.config.Settings;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.web.jooby.adapter.JoobyChannel;
import com.boydti.discord.web.jooby.handler.IMessageOutput;
import gnu.trove.set.hash.TLongHashSet;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageActivity;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.MessageReference;
import net.dv8tion.jda.api.entities.MessageSticker;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.NewsChannel;
import net.dv8tion.jda.api.entities.PrivateChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.ThreadChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.requests.restaction.pagination.ReactionPaginationAction;
import net.dv8tion.jda.api.utils.AttachmentOption;
import net.dv8tion.jda.internal.entities.ReceivedMessage;
import org.apache.commons.collections4.Bag;
import org.jetbrains.annotations.NotNull;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.List;

public class DelegateMessage implements Message {
    @Override
    @Nullable
    public MessageReference getMessageReference() {
        return parent.getMessageReference();
    }

    @Override
    @Nonnull
    public GuildMessageChannel getGuildChannel() {
        return (GuildMessageChannel) parent.getChannel();
    }

    @Override
    @Nonnull
    public NewsChannel getNewsChannel() {
        return (NewsChannel) parent.getChannel();
    }

    @Override
    @Nonnull
    public List<Button> getButtons() {
        return parent.getButtons();
    }

    @Override
    @Nullable
    public Button getButtonById(@Nonnull String id) {
        return parent.getButtonById(id);
    }

    @Override
    @Nonnull
    public List<Button> getButtonsByLabel(@Nonnull String label, boolean ignoreCase) {
        return parent.getButtonsByLabel(label, ignoreCase);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageComponents(@Nonnull Collection<? extends LayoutComponent> components) {
        return parent.editMessageComponents(components);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageComponents(@Nonnull LayoutComponent... components) {
        return parent.editMessageComponents(components);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction reply(@Nonnull CharSequence content) {
        return parent.reply(content);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction replyEmbeds(@Nonnull MessageEmbed embed, @Nonnull MessageEmbed... other) {
        return parent.replyEmbeds(embed, other);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction replyEmbeds(@Nonnull Collection<? extends MessageEmbed> embeds) {
        return parent.replyEmbeds(embeds);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction reply(@Nonnull Message content) {
        return parent.reply(content);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction replyFormat(@Nonnull String format, @Nonnull Object... args) {
        return parent.replyFormat(format, args);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction reply(@Nonnull File file, @Nonnull AttachmentOption... options) {
        return parent.reply(file, options);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction reply(@Nonnull File data, @Nonnull String name, @Nonnull AttachmentOption... options) {
        return parent.reply(data, name, options);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction reply(@Nonnull InputStream data, @Nonnull String name, @Nonnull AttachmentOption... options) {
        return parent.reply(data, name, options);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction reply(@Nonnull byte[] data, @Nonnull String name, @Nonnull AttachmentOption... options) {
        return parent.reply(data, name, options);
    }

    @Override
    public long getFlagsRaw() {
        return parent.getFlagsRaw();
    }

    @Override
    public boolean isEphemeral() {
        return parent.isEphemeral();
    }

    @Override
    @Nullable
    public Interaction getInteraction() {
        return parent.getInteraction();
    }

    @Override
    @CheckReturnValue
    public RestAction<ThreadChannel> createThreadChannel(String name) {
        return parent.createThreadChannel(name);
    }

    public static Message create(String cmd, Guild guild, User user, MessageChannel channel) {
        String nonce = null;
        Member member = guild == null || user == null ? null : guild.getMember(user);

        ReceivedMessage msg = new ReceivedMessage(
                0,
                channel,
                MessageType.DEFAULT,
                null,
                false,
                false,
                new TLongHashSet(),
                new TLongHashSet(),
                false,
                false,
                cmd,
                nonce,
                user,
                member,
                null,
                OffsetDateTime.now(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                0, null
        );
        return msg;
    }

    public static DelegateMessage createWithDummyChannel(String message, Guild guild, User user, IMessageOutput output, File fileDir) {
        JoobyChannel channel = new JoobyChannel(guild, output, fileDir);
        Message embedMessage = new MessageBuilder().setContent(message).build();
        return create(embedMessage, guild, user, channel);
    }

    public static DelegateMessage create(Message message, String cmd, MessageChannel channel) {
        return new DelegateMessage(message) {
            @Nonnull
            @Override
            public String getContentRaw() {
                return cmd;
            }

            @Nonnull
            @Override
            public MessageChannel getChannel() {
                return channel;
            }
        };
    }

    public static DelegateMessage create(Message parent, String cmd, Guild guild, User user) {
        return new DelegateMessage(parent) {
            @Nonnull
            @Override
            public String getContentRaw() {
                return cmd;
            }

            @Nonnull
            @Override
            public User getAuthor() {
                return user;
            }

            @Nullable
            @Override
            public Member getMember() {
                return guild != null ? guild.getMember(user) : null;
            }
        };
    }

    public static DelegateMessage create(Message parent, Guild guild, User user, MessageChannel channel) {
        return new DelegateMessage(parent) {
            @NotNull
            @Override
            public MessageChannel getChannel() {
                return channel;
            }

            @Nonnull
            @Override
            public User getAuthor() {
                return user;
            }

            @Nullable
            @Override
            public Member getMember() {
                return guild != null ? guild.getMember(user) : null;
            }
        };
    }

    private final Message parent;

    public DelegateMessage(Message parent) {
        this.parent = parent;
    }

    @Nullable
    @Override
    public Message getReferencedMessage() {
        return null;
    }

    @Override
    @Nonnull
    public List<User> getMentionedUsers() {
        return parent.getMentionedUsers();
    }

    @Override
    @Nonnull
    public Bag<User> getMentionedUsersBag() {
        return parent.getMentionedUsersBag();
    }

    @Override
    @Nonnull
    public List<TextChannel> getMentionedChannels() {
        return parent.getMentionedChannels();
    }

    @Override
    @Nonnull
    public Bag<TextChannel> getMentionedChannelsBag() {
        return parent.getMentionedChannelsBag();
    }

    @Override
    @Nonnull
    public List<Role> getMentionedRoles() {
        return parent.getMentionedRoles();
    }

    @Override
    @Nonnull
    public Bag<Role> getMentionedRolesBag() {
        return parent.getMentionedRolesBag();
    }

    @Override
    @Nonnull
    public List<Member> getMentionedMembers(@Nonnull Guild guild) {
        return parent.getMentionedMembers(guild);
    }

    @Override
    @Nonnull
    public List<Member> getMentionedMembers() {
        return parent.getMentionedMembers();
    }

    @Override
    @Nonnull
    public List<IMentionable> getMentions(@Nonnull MentionType... types) {
        return parent.getMentions(types);
    }

    @Override
    public boolean isMentioned(@Nonnull IMentionable mentionable, @Nonnull MentionType... types) {
        return parent.isMentioned(mentionable, types);
    }

    @Override
    public boolean mentionsEveryone() {
        return parent.mentionsEveryone();
    }

    @Override
    public boolean isEdited() {
        return parent.isEdited();
    }

    @Override
    @Nullable
    public OffsetDateTime getTimeEdited() {
        return parent.getTimeEdited();
    }

    @Override
    @Nonnull
    public User getAuthor() {
        return parent.getAuthor();
    }

    @Override
    @Nullable
    public Member getMember() {
        return parent.getMember();
    }

    @Override
    @Nonnull
    public String getJumpUrl() {
        return parent.getJumpUrl();
    }

    @Override
    @Nonnull
    public String getContentDisplay() {
        return parent.getContentDisplay();
    }

    @Override
    @Nonnull
    public String getContentRaw() {
        return DiscordUtil.trimContent(parent.getContentRaw());
    }

    @Override
    @Nonnull
    public String getContentStripped() {
        return parent.getContentStripped();
    }

    @Override
    @Nonnull
    public List<String> getInvites() {
        return parent.getInvites();
    }

    @Override
    @Nullable
    public String getNonce() {
        return parent.getNonce();
    }

    @Override
    public boolean isFromType(@Nonnull ChannelType type) {
        return parent.isFromType(type);
    }

    @Override
    public boolean isFromGuild() {
        return parent != null && parent.isFromGuild();
    }

    @Override
    @Nonnull
    public ChannelType getChannelType() {
        return parent != null ? parent.getChannelType() : ChannelType.UNKNOWN;
    }

    @Override
    public boolean isWebhookMessage() {
        return parent.isWebhookMessage();
    }

    @Override
    @Nonnull
    public MessageChannel getChannel() {
        return parent != null ? parent.getChannel() : Locutus.imp().getDiscordApi().getGuildChannelById(Settings.INSTANCE.DISCORD.CHANNEL.ADMIN_ALERTS);
    }

    @Override
    @Nonnull
    public PrivateChannel getPrivateChannel() {
        return (PrivateChannel) parent.getChannel();
    }

    @Override
    @Nonnull
    public TextChannel getTextChannel() {
        return (TextChannel) parent.getChannel();
    }

    @Override
    @Nullable
    public Category getCategory() {
        return parent.getCategory();
    }

    @Override
    @Nonnull
    public Guild getGuild() {
        return parent.getGuild();
    }

    @Override
    @Nonnull
    public List<Attachment> getAttachments() {
        return parent.getAttachments();
    }

    @Override
    @Nonnull
    public List<MessageEmbed> getEmbeds() {
        return parent.getEmbeds();
    }

    @Nonnull
    @Override
    public List<ActionRow> getActionRows() {
        return parent.getActionRows();
    }

    @Override
    @Nonnull
    public List<Emote> getEmotes() {
        return parent.getEmotes();
    }

    @Override
    @Nonnull
    public Bag<Emote> getEmotesBag() {
        return parent.getEmotesBag();
    }

    @Override
    @Nonnull
    public List<MessageReaction> getReactions() {
        return parent.getReactions();
    }

    @Nonnull
    @Override
    public List<MessageSticker> getStickers() {
        return parent.getStickers();
    }

    @Override
    public boolean isTTS() {
        return parent.isTTS();
    }

    @Override
    @Nullable
    public MessageActivity getActivity() {
        return parent.getActivity();
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessage(@Nonnull CharSequence newContent) {
        return parent.editMessage(newContent);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageFormat(@Nonnull String format, @Nonnull Object... args) {
        return parent.editMessageFormat(format, args);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessage(@Nonnull Message newContent) {
        return parent.editMessage(newContent);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public AuditableRestAction<Void> delete() {
        return parent.delete();
    }

    @Override
    @Nonnull
    public JDA getJDA() {
        return parent.getJDA();
    }

    @Override
    public boolean isPinned() {
        return parent.isPinned();
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> pin() {
        return parent.pin();
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> unpin() {
        return parent.unpin();
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> addReaction(@Nonnull Emote emote) {
        return parent.addReaction(emote);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> addReaction(@Nonnull String unicode) {
        return parent.addReaction(unicode);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> clearReactions() {
        return parent.clearReactions();
    }

    @Nonnull
    @Override
    public RestAction<Void> clearReactions(@Nonnull String unicode) {
        return parent.clearReactions(unicode);
    }

    @Nonnull
    @Override
    public RestAction<Void> clearReactions(@Nonnull Emote emote) {
        return parent.clearReactions(emote);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> removeReaction(@Nonnull Emote emote) {
        return parent.removeReaction(emote);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> removeReaction(@Nonnull Emote emote, @Nonnull User user) {
        return parent.removeReaction(emote, user);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> removeReaction(@Nonnull String unicode) {
        return parent.removeReaction(unicode);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> removeReaction(@Nonnull String unicode, @Nonnull User user) {
        return parent.removeReaction(unicode, user);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public ReactionPaginationAction retrieveReactionUsers(@Nonnull Emote emote) {
        return parent.retrieveReactionUsers(emote);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public ReactionPaginationAction retrieveReactionUsers(@Nonnull String unicode) {
        return parent.retrieveReactionUsers(unicode);
    }

    @Override
    @CheckReturnValue
    @Nullable
    public MessageReaction.ReactionEmote getReactionByUnicode(@Nonnull String unicode) {
        return parent.getReactionByUnicode(unicode);
    }

    @Override
    @CheckReturnValue
    @Nullable
    public MessageReaction.ReactionEmote getReactionById(@Nonnull String id) {
        return parent.getReactionById(id);
    }

    @Override
    @CheckReturnValue
    @Nullable
    public MessageReaction.ReactionEmote getReactionById(long id) {
        return parent.getReactionById(id);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public AuditableRestAction<Void> suppressEmbeds(boolean suppressed) {
        return parent.suppressEmbeds(suppressed);
    }

    @Nonnull
    @Override
    public RestAction<Message> crosspost() {
        return parent.crosspost();
    }

    @Override
    public boolean isSuppressedEmbeds() {
        return parent.isSuppressedEmbeds();
    }

    @Override
    @Nonnull
    public EnumSet<MessageFlag> getFlags() {
        return parent.getFlags();
    }

    @Override
    @Nonnull
    public MessageType getType() {
        return parent.getType();
    }

    @Override
    @Nonnull
    public String getId() {
        return parent.getId();
    }

    @Override
    public long getIdLong() {
        try {
            if (parent != null) return parent.getIdLong();
        } catch (UnsupportedOperationException e) {}
        return 0;
    }

    @Override
    @Nonnull
    public OffsetDateTime getTimeCreated() {
        return parent.getTimeCreated();
    }

    @Override
    public void formatTo(Formatter formatter, int flags, int width, int precision) {
        parent.formatTo(formatter, flags, width, precision);
    }

    @Nonnull
    @Override
    public MessageAction editMessageEmbeds(@Nonnull MessageEmbed... embeds) {
        return parent.editMessageEmbeds(embeds);
    }

    @Nonnull
    @Override
    public MessageAction editMessageEmbeds(@Nonnull Collection<? extends MessageEmbed> embeds) {
        return parent.editMessageEmbeds(embeds);
    }
}