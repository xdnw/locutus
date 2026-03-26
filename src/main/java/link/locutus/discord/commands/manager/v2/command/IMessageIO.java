package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.commands.manager.v2.command.shrink.EmbedShrink;
import link.locutus.discord.commands.manager.v2.command.shrink.IShrink;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.RateLimitedSource;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.json.JSONObject;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IMessageIO {
    IMessageBuilder getMessage(RateLimitedSource source);

    Guild getGuildOrNull();

    @CheckReturnValue
    IMessageBuilder create();

    @CheckReturnValue
    default IModalBuilder modal() {
        return new AModalBuilder(this, null, null);
    }

    default CompletableFuture<IMessageBuilder> send(String message, RateLimitedSource source) {
        return send(create().append(message), source);
    }

    default boolean isInteraction() {
        return false;
    }

    void setMessageDeleted(RateLimitedSource source);

    CompletableFuture<IMessageBuilder> send(IMessageBuilder builder, RateLimitedSource source);

    IMessageIO update(IMessageBuilder builder, long id, RateLimitedSource source);

    IMessageIO delete(long id, RateLimitedSource source);

    @CheckReturnValue
    default IMessageBuilder paginate(String title, JSONObject command, Integer page, int perPage, List<IShrink> results, String footer, boolean inline, RateLimitedSource source) {
        IMessageBuilder message = getMessage(source);
        TextChannel t = null;
        if (message == null || message.getAuthor() == null || message.getAuthor().getIdLong() != Settings.INSTANCE.APPLICATION_ID) {
            message = create();
        } else {
            inline = true;
        }
        return message.paginate(title, command, page, perPage, results, footer, inline);
    }

    default boolean appendToEmbed(String s, RateLimitedSource source) {
        IMessageBuilder message = getMessage(source);
        if (message == null || message.getAuthor().getIdLong() != Settings.INSTANCE.APPLICATION_ID) return false;
        List<EmbedShrink> embeds = message.getEmbeds();
        if (embeds.size() != 1) return false;
        EmbedShrink embed = embeds.get(0);

        EmbedShrink builder = new EmbedShrink(embed);
        builder.setDescription(embed.getDescription().get() + "\n\n" + s);

        message.clearEmbeds().embed(builder).send(source);

        return true;
    }

    long getIdLong();

    CompletableFuture<IModalBuilder> send(IModalBuilder modal, RateLimitedSource source);
}
