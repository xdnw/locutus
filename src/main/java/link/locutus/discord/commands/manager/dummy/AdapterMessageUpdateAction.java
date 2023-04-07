package link.locutus.discord.commands.manager.dummy;

import link.locutus.discord.commands.manager.v2.impl.discord.HookMessageChannel;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageUpdateAction;
import net.dv8tion.jda.api.utils.AttachmentOption;
import net.dv8tion.jda.api.utils.Result;
import net.dv8tion.jda.api.utils.concurrent.DelayedCompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

public class AdapterMessageUpdateAction implements MessageAction {
    private final WebhookMessageUpdateAction<Message> parent;
    private final HookMessageChannel hookMC;

    public AdapterMessageUpdateAction(HookMessageChannel hookMC, WebhookMessageUpdateAction<Message> parent) {
        this.parent = parent;
        this.hookMC = hookMC;
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction addFile(@NotNull InputStream data, @NotNull String name, @NotNull AttachmentOption @NotNull ... options) {
        parent.addFile(data, name, options);
        return this;
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction addFile(byte @NotNull [] data, @NotNull String name, @NotNull AttachmentOption @NotNull ... options) {
        parent.addFile(data, name, options);
        return this;
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction addFile(@NotNull File file, @NotNull String name, @NotNull AttachmentOption @NotNull ... options) {
        parent.addFile(file, name, options);
        return this;
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction addFile(@NotNull File file, @NotNull AttachmentOption @NotNull ... options) {
        parent.addFile(file, options);
        return this;
    }

    @Override
    @Nonnull
    public JDA getJDA() {
        return parent.getJDA();
    }

    @Override
    @Nonnull
    public MessageAction setCheck(@Nullable BooleanSupplier checks) {
        parent.setCheck(checks);
        return this;
    }

    @Override
    @javax.annotation.Nullable
    public BooleanSupplier getCheck() {
        return parent.getCheck();
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Message> addCheck(@NotNull BooleanSupplier checks) {
        return parent.addCheck(checks);
    }

    @Override
    @Nonnull
    public MessageAction timeout(long timeout, @NotNull TimeUnit unit) {
        parent.timeout(timeout, unit);
        return this;
    }

    @Override
    @Nonnull
    public MessageAction deadline(long timestamp) {
        parent.deadline(timestamp);
        return this;
    }

    @Override
    public void queue() {
        parent.queue();
    }

    @Override
    public void queue(@Nullable Consumer<? super Message> success) {
        parent.queue(success);
    }

    @Override
    public void queue(@Nullable Consumer<? super Message> success, @Nullable Consumer<? super Throwable> failure) {
        parent.queue(success, failure);
    }

    @Override
    public Message complete() {
        return parent.complete();
    }

    @Override
    public Message complete(boolean shouldQueue) throws RateLimitedException {
        return parent.complete(shouldQueue);
    }

    @Override
    @Nonnull
    public CompletableFuture<Message> submit() {
        return parent.submit();
    }

    @Override
    @Nonnull
    public CompletableFuture<Message> submit(boolean shouldQueue) {
        return parent.submit(shouldQueue);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Result<Message>> mapToResult() {
        return parent.mapToResult();
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public <O> RestAction<O> map(@NotNull Function<? super Message, ? extends O> map) {
        return parent.map(map);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Message> onErrorMap(@NotNull Function<? super Throwable, ? extends Message> map) {
        return parent.onErrorMap(map);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Message> onErrorMap(@Nullable Predicate<? super Throwable> condition, @NotNull Function<? super Throwable, ? extends Message> map) {
        return parent.onErrorMap(condition, map);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Message> onErrorFlatMap(@NotNull Function<? super Throwable, ? extends RestAction<? extends Message>> map) {
        return parent.onErrorFlatMap(map);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Message> onErrorFlatMap(@Nullable Predicate<? super Throwable> condition, @NotNull Function<? super Throwable, ? extends RestAction<? extends Message>> map) {
        return parent.onErrorFlatMap(condition, map);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public <O> RestAction<O> flatMap(@NotNull Function<? super Message, ? extends RestAction<O>> flatMap) {
        return parent.flatMap(flatMap);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public <O> RestAction<O> flatMap(@Nullable Predicate<? super Message> condition, @NotNull Function<? super Message, ? extends RestAction<O>> flatMap) {
        return parent.flatMap(condition, flatMap);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public <U, O> RestAction<O> and(@NotNull RestAction<U> other, @NotNull BiFunction<? super Message, ? super U, ? extends O> accumulator) {
        return parent.and(other, accumulator);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public <U> RestAction<Void> and(@NotNull RestAction<U> other) {
        return parent.and(other);
    }

    @SafeVarargs
    @Override
    @CheckReturnValue
    @Nonnull
    public final RestAction<List<Message>> zip(@NotNull RestAction<? extends Message> first, @NotNull RestAction<? extends Message> @NotNull ... other) {
        return parent.zip(first, other);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Message> delay(@NotNull Duration duration) {
        return parent.delay(duration);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Message> delay(@NotNull Duration duration, @Nullable ScheduledExecutorService scheduler) {
        return parent.delay(duration, scheduler);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Message> delay(long delay, @NotNull TimeUnit unit) {
        return parent.delay(delay, unit);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public RestAction<Message> delay(long delay, @NotNull TimeUnit unit, @Nullable ScheduledExecutorService scheduler) {
        return parent.delay(delay, unit, scheduler);
    }

    @Override
    @Nonnull
    public DelayedCompletableFuture<Message> submitAfter(long delay, @NotNull TimeUnit unit) {
        return parent.submitAfter(delay, unit);
    }

    @Override
    @Nonnull
    public DelayedCompletableFuture<Message> submitAfter(long delay, @NotNull TimeUnit unit, @Nullable ScheduledExecutorService executor) {
        return parent.submitAfter(delay, unit, executor);
    }

    @Override
    public Message completeAfter(long delay, @NotNull TimeUnit unit) {
        return parent.completeAfter(delay, unit);
    }

    @Override
    @Nonnull
    public ScheduledFuture<?> queueAfter(long delay, @NotNull TimeUnit unit) {
        return parent.queueAfter(delay, unit);
    }

    @Override
    @Nonnull
    public ScheduledFuture<?> queueAfter(long delay, @NotNull TimeUnit unit, @Nullable Consumer<? super Message> success) {
        return parent.queueAfter(delay, unit, success);
    }

    @Override
    @Nonnull
    public ScheduledFuture<?> queueAfter(long delay, @NotNull TimeUnit unit, @Nullable Consumer<? super Message> success, @Nullable Consumer<? super Throwable> failure) {
        return parent.queueAfter(delay, unit, success, failure);
    }

    @Override
    @Nonnull
    public ScheduledFuture<?> queueAfter(long delay, @NotNull TimeUnit unit, @Nullable ScheduledExecutorService executor) {
        return parent.queueAfter(delay, unit, executor);
    }

    @Override
    @Nonnull
    public ScheduledFuture<?> queueAfter(long delay, @NotNull TimeUnit unit, @Nullable Consumer<? super Message> success, @Nullable ScheduledExecutorService executor) {
        return parent.queueAfter(delay, unit, success, executor);
    }

    @Override
    @Nonnull
    public ScheduledFuture<?> queueAfter(long delay, @NotNull TimeUnit unit, @Nullable Consumer<? super Message> success, @Nullable Consumer<? super Throwable> failure, @Nullable ScheduledExecutorService executor) {
        return parent.queueAfter(delay, unit, success, failure, executor);
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction mentionRepliedUser(boolean mention) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction allowedMentions(@Nullable Collection<Message.MentionType> allowedMentions) {
        return this;
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction mention(@NotNull IMentionable @NotNull ... mentions) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction mention(@NotNull Collection<? extends IMentionable> mentions) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction mentionUsers(@NotNull String @NotNull ... userIds) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction mentionUsers(long @NotNull ... userIds) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction mentionRoles(@NotNull String @NotNull ... roleIds) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction mentionRoles(long @NotNull ... roleIds) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @NotNull
    @Override
    public MessageAction clearFiles() {
        parent.retainFilesById(Collections.emptyList());
        return this;
    }

    @NotNull
    @Override
    public MessageAction clearFiles(@NotNull BiConsumer<String, InputStream> finalizer) {
        clearFiles();
        return this;
    }

    @NotNull
    @Override
    public MessageAction clearFiles(@NotNull Consumer<InputStream> finalizer) {
        clearFiles();
        return this;
    }

    @NotNull
    @Override
    public MessageAction retainFilesById(@NotNull Collection<String> ids) {
        parent.retainFilesById(ids);
        return this;
    }

    @NotNull
    @Override
    public MessageAction setActionRows(@NotNull ActionRow @NotNull ... rows) {
        parent.setActionRows(rows);
        return this;
    }

    @NotNull
    @Override
    public MessageAction override(boolean bool) {
        return this;
    }

    @NotNull
    @Override
    public MessageAction failOnInvalidReply(boolean fail) {
        return this;
    }

    @NotNull
    @Override
    public MessageAction tts(boolean isTTS) {
        return this;
    }

    @NotNull
    @Override
    public MessageAction reset() {
        return this;
    }

    @NotNull
    @Override
    public MessageAction nonce(@Nullable String nonce) {
        return this;
    }

    @NotNull
    @Override
    public MessageAction content(@Nullable String content) {
        parent.setContent(content);
        return this;
    }

    @NotNull
    @Override
    public MessageAction setEmbeds(@NotNull Collection<? extends MessageEmbed> embeds) {
        parent.setEmbeds(embeds);
        return this;
    }

    @NotNull
    @Override
    public MessageAction append(@Nullable CharSequence csq, int start, int end) {
        assert csq != null;
        parent.setContent(csq.subSequence(start, end) + "");
        return this;
    }

    @NotNull
    @Override
    public MessageAction append(char c) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @NotNull
    @Override
    public MessageAction apply(@Nullable Message message) {
        assert message != null;
        parent.applyMessage(message);
        return this;
    }

    @NotNull
    @Override
    public MessageAction referenceById(long messageId) {
        return this;
    }

    @NotNull
    @Override
    public MessageChannel getChannel() {
        return this.hookMC;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean isEdit() {
        return false;
    }
}
