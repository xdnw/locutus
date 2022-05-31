package com.boydti.discord.web.jooby.adapter;

import com.boydti.discord.util.discord.DiscordUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.JDA;
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
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.ThreadChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.requests.restaction.pagination.ReactionPaginationAction;
import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.HashBag;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.List;
import java.util.stream.Collectors;

public class JoobyMessage implements Message {
    private final Message parent;
    private final long id;
    private final JoobyMessageAction action;
    private User user;

    public JoobyMessage(JoobyMessageAction action, Message parent, long id) {
        this.parent = parent;
        this.id = id;
        this.action = action;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Nullable
    @Override
    public Message getReferencedMessage() {
        return parent.getReferencedMessage();
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
        if (user != null) return user;
        return getGuild().getJDA().getSelfUser();
    }

    @Override
    @Nullable
    public Member getMember() {
        return getGuild().getMember(getAuthor());
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
        return parent.isFromGuild();
    }

    @Override
    @Nonnull
    public ChannelType getChannelType() {
        return getChannel().getType();
    }

    @Override
    public boolean isWebhookMessage() {
        return parent.isWebhookMessage();
    }

    @Override
    @Nonnull
    public MessageChannel getChannel() {
        return action.getChannel();
    }

    @Nonnull
    @Override
    public TextChannel getTextChannel() {
        return (TextChannel) getChannel();
    }

    @Override
    @Nonnull
    public PrivateChannel getPrivateChannel() {
        return (PrivateChannel) action.getChannel();
    }

    @Override
    @Nonnull
    public GuildMessageChannel getGuildChannel() {
        return (GuildMessageChannel) action.getChannel();
    }

    @Nonnull
    @Override
    public NewsChannel getNewsChannel() {
        return null;
    }

    @Override
    @Nullable
    public Category getCategory() {
        return getTextChannel().getParentCategory();
    }

    @Override
    @Nonnull
    public Guild getGuild() {
        return getGuildChannel().getGuild();
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
        return action.getReactions().stream().map(f -> f.getEmote()).collect(Collectors.toList());
    }

    @Override
    @Nonnull
    public Bag<Emote> getEmotesBag() {
        return new HashBag<>(getEmotes());
    }

    @Override
    @Nonnull
    public List<MessageReaction> getReactions() {
        List<MessageReaction> result = new ArrayList<>();
        for (MessageReaction.ReactionEmote reaction : action.getReactions()) {
            result.add(new MessageReaction(getChannel(), reaction, id, false, 1));
        }
        return result;
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
        return action.content(newContent.toString());
    }

    @Nonnull
    @Override
    public MessageAction editMessageEmbeds(@Nonnull MessageEmbed... embeds) {
        return action.setEmbeds(embeds);
    }
    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageFormat(@Nonnull String format, @Nonnull Object... args) {
        return action.appendFormat(format, args);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessage(@Nonnull Message newContent) {
        return action.apply(newContent);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public AuditableRestAction<Void> delete() {
        return new AuditableRestActionDelegate<>(action.delete(Collections.singleton(id)));
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
        return (RestAction) action.addReaction(emote);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> addReaction(@Nonnull String unicode) {
        return (RestAction) action.addReaction(unicode);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> clearReactions() {
        return (RestAction) this.action.clearReactions();
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> clearReactions(@Nonnull String unicode) {
        return (RestAction) this.action.clearReactions(unicode);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> clearReactions(@Nonnull Emote emote) {
        return (RestAction) this.action.clearReactions(emote);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> removeReaction(@Nonnull Emote emote) {
        return (RestAction) this.action.clearReactions(emote);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> removeReaction(@Nonnull Emote emote, @Nonnull User user) {
        return (RestAction) this.action.clearReactions(emote);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> removeReaction(@Nonnull String unicode) {
        return (RestAction) this.action.clearReactions(unicode);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Void> removeReaction(@Nonnull String unicode, @Nonnull User user) {
        return (RestAction) this.action.clearReactions(unicode);
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
        return null;
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
    public long getFlagsRaw() {
        return 0;
    }

    @Override
    public boolean isEphemeral() {
        return true;
    }

    @Override
    @Nonnull
    public MessageType getType() {
        return parent.getType();
    }

    @Nullable
    @Override
    public Interaction getInteraction() {
        return null;
    }

    @Override
    public RestAction<ThreadChannel> createThreadChannel(String name) {
        return null;
    }

    @Override
    @Nonnull
    public String getId() {
        return getIdLong() + "";
    }

    @Override
    public long getIdLong() {
        return id;
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
    public MessageAction editMessageEmbeds(@Nonnull Collection<? extends MessageEmbed> embeds) {
        return parent.editMessageEmbeds(embeds);
    }


    @Nullable
    @Override
    public MessageReference getMessageReference() {
        return null;
    }

    @Nonnull
    @Override
    public MessageAction editMessageComponents(@Nonnull Collection<? extends LayoutComponent> components) {
        return null;
    }
}
