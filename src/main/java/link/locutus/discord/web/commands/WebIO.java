package link.locutus.discord.web.commands;

import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.IModalBuilder;
import link.locutus.discord.util.RateLimitedSource;
import link.locutus.discord.web.jooby.handler.IMessageOutput;
import net.dv8tion.jda.api.entities.Guild;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class WebIO implements IMessageIO {
    private final IMessageOutput sse;
    private final Guild guild;

    public WebIO(IMessageOutput sse, Guild guild) {
        this.sse = sse;
        this.guild = guild;
    }

    @Override
    public Guild getGuildOrNull() {
        return guild;
    }

    @Override
    public IMessageBuilder getMessage(RateLimitedSource source) {
        return null;
    }

    @Override
    public IMessageBuilder create() {
        return new WebMessage(this);
    }

    @Override
    public void setMessageDeleted(RateLimitedSource source) {

    }

    @Override
    public CompletableFuture<IMessageBuilder> send(IMessageBuilder builder, RateLimitedSource source) {
        Map<String, Object> obj = ((WebMessage) builder).build();
        sse.sendEvent(obj);
        return CompletableFuture.completedFuture(builder);
    }

    @Override
    public IMessageIO update(IMessageBuilder builder, long id, RateLimitedSource source) {
        (( WebMessage)builder).id = id;
        send(builder, source);
        return this;
    }

    @Override
    public IMessageIO delete(long id, RateLimitedSource source) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", "deleteByIds");
        data.put("value", List.of(id + ""));
        sse.sendEvent(data);
        return this;
    }

    @Override
    public long getIdLong() {
        return 0;
    }

    @Override
    public CompletableFuture<IModalBuilder> send(IModalBuilder modal, RateLimitedSource source) {
        throw new UnsupportedOperationException("Modals not implemented for web");
    }
}
