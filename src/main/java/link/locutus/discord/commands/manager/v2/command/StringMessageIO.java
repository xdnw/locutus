package link.locutus.discord.commands.manager.v2.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class StringMessageIO implements IMessageIO {

    public enum Mode {
        LEGACY_TEXT,
        STRUCTURED
    }

    private final Map<Long, StringMessageBuilder> messages = new LinkedHashMap<>();
    private final User user;
    private final Guild guild;
    private final Mode mode;
    private long id = 1;

    public StringMessageIO(User user, Guild guild) {
        this(user, guild, Mode.LEGACY_TEXT);
    }

    public StringMessageIO(User user, Guild guild, Mode mode) {
        this.user = user;
        this.guild = guild;
        this.mode = mode == null ? Mode.LEGACY_TEXT : mode;
    }

    public List<IMessageBuilder> writeTo(IMessageIO io) {
        List<IMessageBuilder> result = messages.values().stream().filter(f -> !f.isEmpty()).map(f -> f.writeTo(io.create())).toList();
        return result;
    }

    @Override
    public String toString() {
        return toLegacyText();
    }

    public String toLegacyText() {
        StringBuilder sb = new StringBuilder();
        for (StringMessageBuilder message : messages.values()) {
            sb.append(message.toString()).append("\n");
        }
        return sb.toString().trim();
    }

    public List<Map<String, Object>> toMcpContentItems() {
        return McpMessageContentAdapter.fromMessages(messages.values());
    }

    public Map<String, Object> toMcpToolResult(Object result) {
        return McpMessageContentAdapter.toolResult(messages.values(), result, mode.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public Guild getGuildOrNull() {
        return guild;
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
    public void setMessageDeleted() {

    }

    @Override
    public CompletableFuture<IMessageBuilder> send(IMessageBuilder builder) {
        messages.put(builder.getId(), ((StringMessageBuilder) builder));
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

    @Override
    public CompletableFuture<IModalBuilder> send(IModalBuilder modal) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public List<StringMessageBuilder> getMessages() {
        return List.copyOf(messages.values());
    }
}