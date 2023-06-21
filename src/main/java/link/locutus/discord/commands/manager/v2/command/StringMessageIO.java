package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.util.StringMan;
import net.dv8tion.jda.api.entities.User;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class StringMessageIO implements IMessageIO {

    private final Map<Long, String> messages = new LinkedHashMap<>();
    private final User user;
    private long id = 1;

    public StringMessageIO(User user) {
        this.user = user;
    }

    public String getOutput() {
        return StringMan.join(messages.values(), "\n\n");
    }

    @Override
    public IMessageBuilder getMessage() {
        return null;
    }

    @Override
    public IMessageBuilder create() {
        return new StringMessageBuilder(this, id++, System.currentTimeMillis(), user);
    }

    @Override
    public CompletableFuture<IMessageBuilder> send(IMessageBuilder builder) {
        messages.put(builder.getId(), ((StringMessageBuilder) builder).build());
        return CompletableFuture.completedFuture(builder);
    }

    @Override
    public IMessageIO update(IMessageBuilder builder, long id) {
        return null;
    }

    @Override
    public IMessageIO delete(long id) {
        messages.remove(id);
        return this;
    }

    @Override
    public long getIdLong() {
        return 0;
    }
}