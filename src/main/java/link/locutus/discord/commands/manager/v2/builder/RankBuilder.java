package link.locutus.discord.commands.manager.v2.builder;

import com.google.common.base.Function;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class RankBuilder<T> {
    private List<T> values;
    private int limit = 25;

    public RankBuilder(List<T> values) {
        this.values = values;
    }

    public RankBuilder(Collection<T> values) {
        this(new ArrayList<>(values));
    }

    public RankBuilder<T> removeIf(Predicate<T> removeIf) {
        values.removeIf(removeIf);
        return this;
    }

    public <G> RankBuilder<G> adapt(java.util.function.Function<T, G> adapter) {
        List<G> transform = values.stream().map(adapter).collect(Collectors.toList());
        return new RankBuilder<>(transform);
    }

    public <K> GroupedRankBuilder<K, T> group(Function<T, K> groupBy) {
        return group((t, builder) -> builder.put(groupBy.apply(t), t));
    }

    public <K, V> GroupedRankBuilder<K, V> group(BiConsumer<T, GroupedRankBuilder<K, V>> consumer) {
        GroupedRankBuilder<K, V> result = new GroupedRankBuilder<K, V>();
        for (T value : values) consumer.accept(value, result);
        return result;
    }

    public <K, V, I extends Number> NumericMappedRankBuilder<K, V, I> map(BiConsumer<T, NumericMappedRankBuilder<K, V, I>> consumer) {
        NumericMappedRankBuilder<K, V, I> result = new NumericMappedRankBuilder<>();
        for (T value : values) consumer.accept(value, result);
        return result;
    }


    public <K, I extends Number> SummedMapRankBuilder<K, I> sum(BiConsumer<T, SummedMapRankBuilder<K, I>> consumer) {
        SummedMapRankBuilder<K, I> result = new SummedMapRankBuilder<>();
        for (T value : values) consumer.accept(value, result);
        return result;
    }

    public RankBuilder<T> sort() {
        Collections.sort((List) values);
        return this;
    }

    public RankBuilder<T> sort(Comparator<T> comparator) {
        values.sort(comparator);
        return this;
    }

    public RankBuilder<T> limit(int amount) {
        limit = amount;
        return this;
    }

    public RankBuilder<T> page(int page, int amt) {
        int start = page * amt;
        int end = start + amt;
        if (start >= values.size()) {
            values.clear();

        } else {
            values = values.subList(start, Math.min(values.size(), end));
        }
        return this;
    }

    public void build(User author, IMessageIO channel, String command, String title, boolean upload) {
        List<String> items = toItems(limit);
        String emoji = "Refresh";
        StringBuilder itemsStr = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            itemsStr.append(items.get(i)).append("\n");
        }
        System.out.println("ITEMS " + itemsStr);
        if (author != null) itemsStr.append("\n" + author.getAsMention());
        IMessageBuilder msg = channel.create().embed(title, itemsStr.toString());
        if (command != null) msg = msg.commandButton(command.toString(), emoji);

        if (upload && values.size() > limit) {
            msg.file(title + ".txt", toString());
        }

        msg.send();
    }

    public void build(User author, IMessageIO channel, String fullCommandRaw, String title) {
        build(author, channel, DiscordUtil.trimContent(fullCommandRaw), title, false);
    }

    public void build(MessageChannel channel, String cmd, String title) {
        build(null, channel, cmd, title);
    }

    public void build(IMessageIO io, JSONObject command, String title, boolean upload) {
        build(null, io, command, title, upload);
    }

    public void build(IMessageIO io, JSONObject command, String title) {
        build(null, io, command, title, false);
    }

    public void build(User author, IMessageIO io, JSONObject command, String title, boolean upload) {
        build(author, io, command == null ? null : command.toString(), title, upload);
    }

    public void build(User author, MessageChannel channel, String cmd, String title) {
        build(author, channel, cmd, title, false);
    }

    public void build(User author, MessageChannel channel, String cmd, String title, boolean upload) {
        List<String> items = toItems(limit);
        String emoji = "Refresh";
        StringBuilder itemsStr = new StringBuilder("```\n");
        for (int i = 0; i < items.size(); i++) {
            itemsStr.append(items.get(i)).append("\n");
        }
        itemsStr.append("```");
        if (author != null) itemsStr.append("\n" + author.getAsMention());

        DiscordChannelIO io = new DiscordChannelIO(channel);
        IMessageBuilder msg = io.create().embed(title, itemsStr.toString());
        if (cmd != null && !cmd.isBlank()) msg = msg.commandButton(cmd, emoji);

        if (upload && values.size() > 25) {
            msg.file(title + ".txt", toString());
        }

        msg.send();
    }

    public List<String> toItems(int limit) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, values.size()); i++) {
            items.add((i + 1) + ". " + values.get(i).toString());
        }
        return items;
    }

    @Override
    public String toString() {
        return StringMan.join(toItems(Integer.MAX_VALUE), "\n");
    }

    public List<T> get() {
        return values;
    }
}
