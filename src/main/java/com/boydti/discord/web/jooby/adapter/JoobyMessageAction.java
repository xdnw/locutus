package com.boydti.discord.web.jooby.adapter;

import com.boydti.discord.util.MarkupUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.web.jooby.handler.IMessageOutput;
import com.boydti.discord.web.jooby.handler.SseClient2;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.utils.AttachmentOption;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.api.utils.data.SerializableArray;
import net.dv8tion.jda.internal.utils.IOUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class JoobyMessageAction implements MessageAction {
    private final IMessageOutput sse;
    private final JDA jda;
    private final MessageChannel channel;
    private final File fileRoot;
    private long id;

    private StringBuilder builder = new StringBuilder();
    private List<MessageEmbed> embeds = new ArrayList<>();
    private List<MessageReaction.ReactionEmote> reactions = new ArrayList<>();
    private List<Map.Entry<String, Future<InputStream>>> attachments = new ArrayList<>();

    public JoobyMessageAction(JDA jda, MessageChannel channel, File fileRoot, IMessageOutput sse) {
        this.jda = jda;
        this.channel = channel;
        this.sse = sse;
        this.id = UUID.randomUUID().getMostSignificantBits();
        this.fileRoot = fileRoot;
    }

    public String getContent() {
        return builder.toString();
    }

    public JoobyMessageAction load(Message message) {
        builder.append(DiscordUtil.trimContent(message.getContentRaw()));
        embeds.addAll(message.getEmbeds());
        for (MessageReaction reaction : message.getReactions()) {
            reactions.add(reaction.getReactionEmote());
        }
        return this;
    }

    public JoobyMessageAction setId(long id) {
        this.id = id;
        return this;
    }

    public JoobyRestAction delete(Set<Long> ids) {
        JsonObject obj = new JsonObject();
        obj.addProperty("action", "deleteByIds");
        JsonArray idArr = new JsonArray();
        for (Long id : ids) {
            idArr.add(id + "");
        }
        obj.add("value", idArr);
        return new JoobyRestAction(getJDA(), () -> sse.sendEvent(obj));
    }

    public List<MessageReaction.ReactionEmote> getReactions() {
        return reactions;
    }

    public List<Map.Entry<String, Future<InputStream>>> getAttachments() {
        return attachments;
    }

    public long getId() {
        return id;
    }

    @Nonnull
    @Override
    public JDA getJDA() {
        return jda;
    }

    @Nonnull
    @Override
    public MessageAction setCheck(@Nullable BooleanSupplier checks) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction timeout(long timeout, @Nonnull TimeUnit unit) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction deadline(long timestamp) {
        return this;
    }

    @Override
    public void queue(@Nullable Consumer<? super Message> success, @Nullable Consumer<? super Throwable> failure) {
        Message result = complete(false);
        if (success != null) success.accept(result);
    }

    private List<Map.Entry<String, File>> files = null;

    private List<Map.Entry<String, File>> generateFileList() {
        if (files == null || files.size() != attachments.size()) {
            files = new ArrayList<>();
            for (Map.Entry<String, Future<InputStream>> attachment : attachments) {
                try {
                    InputStream is = attachment.getValue().get();
                    byte[] bytes = IOUtil.readFully(is);
                    String filename = attachment.getKey();
                    String fileId = UUID.randomUUID().toString();
                    File file = new File(fileRoot, fileId);
                    Files.write(file.toPath(), bytes);
                    files.add(new AbstractMap.SimpleEntry<>(filename, file));
                } catch (InterruptedException | ExecutionException | IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return files;
    }

    public IMessageOutput getOutput() {
        return sse;
    }

    public List<MessageEmbed> getEmbeds() {
        return embeds;
    }

    public DataObject toJson() {
        DataObject obj = DataObject.empty();
        if (this.builder.length() != 0) {
            obj.put("content", MarkupUtil.transformURLIntoMarkup(builder.toString()));
        }
        if (!this.embeds.isEmpty()) {
            if (this.embeds.size() == 1) {
                obj.put("embed", embeds.get(0));
            } else {
                DataArray array = DataArray.fromCollection(embeds);
                obj.put("embeds", array);
            }
            // reactions
        }
        if (!reactions.isEmpty()) {
            List<String> reactionsStrList = new ArrayList<>();
            for (MessageReaction.ReactionEmote reaction : reactions) {
                reactionsStrList.add(reaction.getEmoji());
            }

            obj.put("reactions", reactionsStrList);
        }
        if (!attachments.isEmpty()) {
            Map<String, String> urlFileNames = getUrlFileNames();
            if (!urlFileNames.isEmpty()) {
                obj.put("files", urlFileNames);
            }

        }
        obj.put("id", id + "");
        return obj;
    }

    public Map<String, String> getUrlFileNames() {
        Map<String, String> urlFileNames = new LinkedHashMap<>();
        for (Map.Entry<String, File> entry : generateFileList()) {
            urlFileNames.put(entry.getValue().getName(), entry.getKey());
        }
        return urlFileNames;
    }

    @Override
    public Message complete(boolean shouldQueue) {
        sse.sendEvent(this);
        MessageBuilder mb = new MessageBuilder();
        mb.setContent(this.builder.toString());
        if (!this.embeds.isEmpty()) mb.setEmbeds(embeds);
        return new JoobyMessage(this,  mb.build(), id);
    }

    @Nonnull
    @Override
    public CompletableFuture<Message> submit(boolean shouldQueue) {
        return CompletableFuture.completedFuture(complete(false));
    }

    @Nonnull
    @Override
    public MessageChannel getChannel() {
        return channel;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean isEdit() {
        return false;
    }

    @Nonnull
    @Override
    public MessageAction apply(@Nullable Message message) {
        reset();
        this.builder.setLength(0);
        this.builder.append(DiscordUtil.trimContent(message.getContentRaw()));
        this.reactions.clear();
        for (MessageReaction reaction : message.getReactions()) {
            this.reactions.add(reaction.getReactionEmote());
        }
        this.attachments.clear();
        this.files = null;
        for (Message.Attachment attachment : message.getAttachments()) {
            CompletableFuture<InputStream> is = attachment.retrieveInputStream();
            this.attachments.add(new AbstractMap.SimpleEntry<>(attachment.getFileName(), is));
        }
        this.embeds.clear();
        this.embeds.addAll(message.getEmbeds());
        return this;
    }

    public MessageAction addReaction(@Nonnull Emote emote) {
        MessageReaction.ReactionEmote re = MessageReaction.ReactionEmote.fromCustom(emote);
        reactions.add(MessageReaction.ReactionEmote.fromCustom(emote));
        return this;
    }

    public MessageAction addReaction(@Nonnull String unicode) {
        MessageReaction.ReactionEmote emote = MessageReaction.ReactionEmote.fromUnicode(unicode, jda);
        reactions.add(emote);
        return this;
    }

    public MessageAction clearReactions() {
        reactions.clear();
        return this;
    }

    @Nonnull
    @Override
    public MessageAction referenceById(long messageId) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction mentionRepliedUser(boolean mention) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction failOnInvalidReply(boolean fail) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction tts(boolean isTTS) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction reset() {
        this.builder.setLength(0);
        this.reactions.clear();
        this.files = null;
        this.attachments.clear();
        this.embeds.clear();
        return this;
    }

    @Nonnull
    @Override
    public MessageAction nonce(@Nullable String nonce) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction content(@Nullable String content) {
        this.builder.setLength(0);
        this.builder.append(content);
        return this;
    }

    @Nonnull
    @Override
    public MessageAction setEmbeds(@Nonnull MessageEmbed... embeds) {
        this.embeds.clear();
        this.embeds.addAll(Arrays.asList(embeds));
        return this;
    }

    @Nonnull
    @Override
    public MessageAction setEmbeds(@Nonnull Collection<? extends MessageEmbed> embeds) {
        this.embeds.clear();
        this.embeds.addAll(embeds);
        return this;
    }

    public void setEmbeds(List<MessageEmbed> embeds) {
        this.embeds.clear();
        this.embeds.addAll(embeds);
    }

    @Nonnull
    @Override
    public MessageAction append(@Nullable CharSequence csq, int start, int end) {
        builder.append(csq.subSequence(start, end));
        return this;
    }

    @Nonnull
    @Override
    public MessageAction append(char c) {
        builder.append(c);
        return this;
    }

    @Nonnull
    @Override
    public MessageAction addFile(@Nonnull InputStream data, @Nonnull String name, @Nonnull AttachmentOption... options) {
        this.attachments.add(new AbstractMap.SimpleEntry<>(name, CompletableFuture.completedFuture(data)));
        return this;
    }

    @Nonnull
    @Override
    public MessageAction addFile(@Nonnull File file, @Nonnull String name, @Nonnull AttachmentOption... options) {
        this.attachments.add(new AbstractMap.SimpleEntry<>(name, CompletableFuture.supplyAsync(() -> {
            try {
                return new ByteArrayInputStream(Files.readAllBytes(file.toPath()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        })));
        return this;
    }

    @Nonnull
    @Override
    public MessageAction clearFiles() {
        attachments.clear();
        return this;
    }

    @Nonnull
    @Override
    public MessageAction clearFiles(@Nonnull BiConsumer<String, InputStream> finalizer) {
        return clearFiles();
    }

    @Nonnull
    @Override
    public MessageAction clearFiles(@Nonnull Consumer<InputStream> finalizer) {
        return clearFiles();
    }

    @Nonnull
    @Override
    public MessageAction retainFilesById(@Nonnull Collection<String> ids) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction setActionRows(@Nonnull ActionRow... rows) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction override(boolean bool) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction allowedMentions(@Nullable Collection<Message.MentionType> allowedMentions) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction mention(@Nonnull IMentionable... mentions) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction mentionUsers(@Nonnull String... userIds) {
        return this;
    }

    @Nonnull
    @Override
    public MessageAction mentionRoles(@Nonnull String... roleIds) {
        return this;
    }

    public MessageAction clearReactions(String unicode) {
        reactions.removeIf(f -> f.getEmoji().equalsIgnoreCase(unicode));
        return this;
    }

    public MessageAction clearReactions(Emote emote) {
        reactions.removeIf(f -> f.getEmoji().equalsIgnoreCase(emote.getName()));
        return this;
    }
}
