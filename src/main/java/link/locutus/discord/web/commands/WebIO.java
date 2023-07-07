package link.locutus.discord.web.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.IModalBuilder;
import link.locutus.discord.web.jooby.handler.IMessageOutput;
import net.dv8tion.jda.api.utils.data.DataObject;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class WebIO implements IMessageIO {
    private final IMessageOutput sse;

    public WebIO(IMessageOutput sse) {
        this.sse = sse;
    }

    @Override
    public IMessageBuilder getMessage() {
        return null;
    }

    @Override
    public IMessageBuilder create() {
        return new WebMessage(this);
    }

    @Override
    public CompletableFuture<IMessageBuilder> send(IMessageBuilder builder) {
        DataObject obj = ((WebMessage) builder).build();
        sse.sendEvent(obj.toString());
        return CompletableFuture.completedFuture(builder);
    }

    @Override
    public IMessageIO update(IMessageBuilder builder, long id) {
        (( WebMessage)builder).id = id;
        send(builder);
        return this;
    }

    @Override
    public IMessageIO delete(long id) {
        JsonObject obj = new JsonObject();
        obj.addProperty("action", "deleteByIds");
        JsonArray idArr = new JsonArray();
        idArr.add(id + "");
        obj.add("value", idArr);

        sse.sendEvent(obj);

        return this;
    }

    @Override
    public long getIdLong() {
        return 0;
    }

    @Override
    public CompletableFuture<IModalBuilder> send(IModalBuilder modal) {
        throw new UnsupportedOperationException("Modals not implemented for web");
    }
}
