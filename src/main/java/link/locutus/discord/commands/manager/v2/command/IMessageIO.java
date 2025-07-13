package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.commands.manager.v2.command.shrink.EmbedShrink;
import link.locutus.discord.commands.manager.v2.command.shrink.IShrink;
import link.locutus.discord.config.Settings;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.json.JSONObject;

import javax.annotation.CheckReturnValue;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IMessageIO {
    IMessageBuilder getMessage();

    Guild getGuildOrNull();

    @CheckReturnValue
    IMessageBuilder create();

    @CheckReturnValue
    default IModalBuilder modal() {
        return new AModalBuilder(this, null, null);
    }

    default CompletableFuture<IMessageBuilder> send(String message) {
        return send(create().append(message));
    }

    default boolean isInteraction() {
        return false;
    }

    void setMessageDeleted();

    CompletableFuture<IMessageBuilder> send(IMessageBuilder builder);

    IMessageIO update(IMessageBuilder builder, long id);

    IMessageIO delete(long id);

    @CheckReturnValue
    default IMessageBuilder paginate(String title, JSONObject command, Integer page, int perPage, List<IShrink> results, String footer, boolean inline) {
        IMessageBuilder message = getMessage();
        TextChannel t = null;
        if (message == null || message.getAuthor() == null || message.getAuthor().getIdLong() != Settings.INSTANCE.APPLICATION_ID) {
            message = create();
        } else {
            inline = true;
        }
        return message.paginate(title, command, page, perPage, results, footer, inline);
    }

    default boolean appendToEmbed(String s) {
        IMessageBuilder message = getMessage();
        if (message == null || message.getAuthor().getIdLong() != Settings.INSTANCE.APPLICATION_ID) return false;
        List<EmbedShrink> embeds = message.getEmbeds();
        if (embeds.size() != 1) return false;
        EmbedShrink embed = embeds.get(0);

        EmbedShrink builder = new EmbedShrink(embed);
        builder.setDescription(embed.getDescription().get() + "\n\n" + s);

        message.clearEmbeds().embed(builder).send();

        return true;
    }


    default IMessageBuilder updateOptionally(CompletableFuture<IMessageBuilder> msgFuture, String message) {
        if (msgFuture == null) return null;
        IMessageBuilder msg = msgFuture.getNow(null);
        if (msg != null && msg.getId() > 0) {
            msg.clear().append(message).sendIfFree();
        }
        return msg;
    }

    long getIdLong();

    default CompletableFuture<IMessageBuilder> sendMessage(String s) {
        return send(s);
    }

    CompletableFuture<IModalBuilder> send(IModalBuilder modal);
}
