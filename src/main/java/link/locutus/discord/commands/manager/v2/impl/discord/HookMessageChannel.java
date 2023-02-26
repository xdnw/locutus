package link.locutus.discord.commands.manager.v2.impl.discord;

import link.locutus.discord.commands.manager.dummy.AdapterAuditableRestAction;
import link.locutus.discord.commands.manager.dummy.AdapterMessageAction;
import link.locutus.discord.commands.manager.dummy.AdapterMessageUpdateAction;
import link.locutus.discord.commands.manager.dummy.DelegateMessageChannel;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.ICategorizableChannel;
import net.dv8tion.jda.api.entities.IPermissionContainer;
import net.dv8tion.jda.api.entities.IPermissionHolder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.managers.channel.ChannelManager;
import net.dv8tion.jda.api.managers.channel.attribute.ICategorizableChannelManager;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;
import net.dv8tion.jda.api.utils.AttachmentOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

public class HookMessageChannel extends DelegateMessageChannel implements ICategorizableChannel, GuildChannel {
    private final InteractionHook hook;
    private final MessageChannel parent;

    public HookMessageChannel(MessageChannel parent, InteractionHook hook) {
        super(parent);
        this.parent = parent;
        this.hook = hook;
    }

    public MessageChannel getParent() {
        return parent;
    }

    @NotNull
    @Override
    public Guild getGuild() {
        return hook.getInteraction().getGuild();
    }

    @Nullable
    @Override
    public PermissionOverride getPermissionOverride(@NotNull IPermissionHolder permissionHolder) {
        if (parent instanceof IPermissionContainer) {
            return ((IPermissionContainer) parent).getPermissionOverride(permissionHolder);
        }
        throw new UnsupportedOperationException("Not a permission container");
    }

    @NotNull
    @Override
    public List<PermissionOverride> getPermissionOverrides() {
        if (parent instanceof IPermissionContainer) {
            return ((IPermissionContainer) parent).getPermissionOverrides();
        }
        throw new UnsupportedOperationException("Not a permission container");
    }

    @NotNull
    @Override
    public PermissionOverrideAction putPermissionOverride(@NotNull IPermissionHolder permissionHolder) {
        if (parent instanceof IPermissionContainer) {
            return ((IPermissionContainer) parent).putPermissionOverride(permissionHolder);
        }
        throw new UnsupportedOperationException("Not a permission container");
    }

    @Override
    public long getParentCategoryIdLong() {
        if (parent instanceof ICategorizableChannel) {
            return ((ICategorizableChannel) parent).getParentCategoryIdLong();
        }
        return 0;
    }

    @Override
    public boolean isSynced() {
        if (parent instanceof ICategorizableChannel) {
            return ((ICategorizableChannel) parent).isSynced();
        }
        return true;
    }

    @NotNull
    @Override
    public ICategorizableChannelManager<?, ?> getManager() {
        if (parent instanceof GuildChannel) {
            ChannelManager<?, ?> manager = ((GuildMessageChannel) parent).getManager();
            if (manager instanceof ICategorizableChannelManager) {
                return (ICategorizableChannelManager<?, ?>) manager;
            }
            CategorizableChannelManagerDelegate delegate = new CategorizableChannelManagerDelegate(manager);
            return delegate;
        }
        throw new UnsupportedOperationException("Not a guild channel");
    }

    @Override
    public IPermissionContainer getPermissionContainer() {
        if (parent instanceof GuildChannel) return ((GuildChannel) parent).getPermissionContainer();
        return null;
    }

    @Override
    public int compareTo(@NotNull GuildChannel o) {
        if (parent instanceof GuildChannel) {
            return ((GuildChannel) parent).compareTo(o);
        }
        return Long.compareUnsigned(getIdLong(), o.getIdLong());
    }

    @Override
    @Nonnull
    public JDA getJDA() {
        return hook.getJDA();
    }

    @CheckReturnValue
    @Nonnull
    @Override
    public MessageAction sendMessage(@NotNull CharSequence text) {
        return new AdapterMessageAction(this, hook.sendMessage(text + ""));
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction sendMessage(@NotNull Message message) {
        return new AdapterMessageAction(this, hook.sendMessage(message));
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction sendMessageFormat(@NotNull String format, @NotNull Object... args) {
        return new AdapterMessageAction(this, hook.sendMessageFormat(format, args));
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction sendMessageEmbeds(@NotNull Collection<? extends MessageEmbed> embeds) {
        return new AdapterMessageAction(this, hook.sendMessageEmbeds(embeds));
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction sendMessageEmbeds(@NotNull MessageEmbed embed, @NotNull MessageEmbed... embeds) {
        return new AdapterMessageAction(this, hook.sendMessageEmbeds(embed, embeds));
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction sendFile(@NotNull InputStream data, @NotNull String name, @NotNull AttachmentOption... options) {
        return new AdapterMessageAction(this, hook.sendFile(data, name, options));
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction sendFile(@NotNull File file, @NotNull AttachmentOption... options) {
        return new AdapterMessageAction(this, hook.sendFile(file, options));
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction sendFile(@NotNull File file, @NotNull String name, @NotNull AttachmentOption... options) {
        return new AdapterMessageAction(this, hook.sendFile(file, name, options));
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction sendFile(@NotNull byte[] data, @NotNull String name, @NotNull AttachmentOption... options) {
        return new AdapterMessageAction(this, hook.sendFile(data, name, options));
    }

    @CheckReturnValue
    @Nonnull
    @Override
    public MessageAction editMessageById(@NotNull String messageId, @NotNull CharSequence content) {
        return new AdapterMessageUpdateAction(this, hook.editMessageById(messageId, content + ""));
    }

    @CheckReturnValue
    @Nonnull
    @Override
    public MessageAction editMessageById(long messageId, @NotNull CharSequence content) {
        return new AdapterMessageUpdateAction(this, hook.editMessageById(messageId, content + ""));
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageById(@NotNull String messageId, @NotNull Message message) {
        return new AdapterMessageUpdateAction(this, hook.editMessageById(messageId, message));
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageById(long messageId, @NotNull Message message) {
        return new AdapterMessageUpdateAction(this, hook.editMessageById(messageId, message));
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageFormatById(@NotNull String messageId, @NotNull String format, @NotNull Object... args) {
        return new AdapterMessageUpdateAction(this, hook.editMessageFormatById(messageId, format, args));
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageFormatById(long messageId, @NotNull String format, @NotNull Object... args) {
        return new AdapterMessageUpdateAction(this, hook.editMessageFormatById(messageId, format, args));
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageEmbedsById(@NotNull String messageId, @NotNull Collection<? extends MessageEmbed> embeds) {
        return new AdapterMessageUpdateAction(this, hook.editMessageEmbedsById(messageId, embeds));
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageEmbedsById(long messageId, @NotNull Collection<? extends MessageEmbed> embeds) {
        return new AdapterMessageUpdateAction(this, hook.editMessageEmbedsById(messageId, embeds));
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageEmbedsById(@NotNull String messageId, @NotNull MessageEmbed... embeds) {
        return new AdapterMessageUpdateAction(this, hook.editMessageEmbedsById(messageId, embeds));
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageEmbedsById(long messageId, @NotNull MessageEmbed... embeds) {
        return new AdapterMessageUpdateAction(this, hook.editMessageEmbedsById(messageId, embeds));
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageComponentsById(@NotNull String messageId, @NotNull Collection<? extends LayoutComponent> components) {
        return new AdapterMessageUpdateAction(this, hook.editMessageComponentsById(messageId, components));
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageComponentsById(long messageId, @NotNull Collection<? extends LayoutComponent> components) {
        return new AdapterMessageUpdateAction(this, hook.editMessageComponentsById(messageId, components));
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageComponentsById(@NotNull String messageId, @NotNull LayoutComponent... components) {
        return new AdapterMessageUpdateAction(this, hook.editMessageComponentsById(messageId, components));
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public MessageAction editMessageComponentsById(long messageId, @NotNull LayoutComponent... components) {
        return new AdapterMessageUpdateAction(this, hook.editMessageComponentsById(messageId, components));
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public AuditableRestAction<Void> deleteMessageById(@NotNull String messageId) {
        return new AdapterAuditableRestAction<>(hook.deleteMessageById(messageId));
    }

    @Override
    @CheckReturnValue
    @Nonnull
    public AuditableRestAction<Void> deleteMessageById(long messageId) {
        return new AdapterAuditableRestAction<>(hook.deleteMessageById(messageId));
    }
}
