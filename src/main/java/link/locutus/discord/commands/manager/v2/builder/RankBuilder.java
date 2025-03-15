package link.locutus.discord.commands.manager.v2.builder;

import com.google.common.base.Function;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.shrink.EmbedShrink;
import link.locutus.discord.commands.manager.v2.command.shrink.IShrink;
import link.locutus.discord.commands.manager.v2.command.shrink.IdenticalShrink;
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
    private final Set<Integer> highlight;
    private List<T> values;
    private int limit = 25;

    public RankBuilder(Collection<T> values, Set<Integer> highlight) {
        this.values = values instanceof List l ? l : new ObjectArrayList<>(values);
        this.highlight = highlight;
    }
    public RankBuilder(Collection<T> values) {
        this(new ArrayList<>(values), Collections.emptySet());
    }

    public RankBuilder<T> removeIf(Predicate<T> removeIf) {
        values.removeIf(removeIf);
        return this;
    }

    public <G> RankBuilder<G> adapt(java.util.function.Function<T, G> adapter) {
        List<G> transform = values.stream().map(adapter).collect(Collectors.toList());
        return new RankBuilder<>(transform, highlight);
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

    public RankBuilder<T> limit(Integer amount) {
        if (amount != null) limit = amount;
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

    public void build(User author, IMessageIO channel, String command, String title, boolean upload) {
        List<IShrink> items = toItems(limit);
        String emoji = "Refresh";
        EmbedShrink embed = new EmbedShrink().title(title);
        for (int i = 0; i < items.size(); i++) {
            embed.append(items.get(i)).append("\n");
        }
        if (author != null) embed.append("\n" + author.getAsMention());
        IMessageBuilder msg = channel.create().embed(embed);
        if (command != null) msg = msg.commandButton(command.toString(), emoji);

        if (upload && values.size() > limit) {
            msg.file(title + ".txt", toString());
        }

        msg.send();
    }

    public void build(User author, MessageChannel channel, String cmd, String title, boolean upload) {
        List<IShrink> items = toItems(limit);
        String emoji = "Refresh";
        EmbedShrink embed = new EmbedShrink().title(title);
        embed.append("```\n");
        for (int i = 0; i < items.size(); i++) {
            embed.append(items.get(i)).append("\n");
        }
        embed.append("```");
        if (author != null) embed.append("\n" + author.getAsMention());

        DiscordChannelIO io = new DiscordChannelIO(channel);
        IMessageBuilder msg = io.create().embed(embed);
        if (cmd != null && !cmd.isBlank()) msg = msg.commandButton(cmd, emoji);

        if (upload && values.size() > 25) {
            msg.file(title + ".txt", toString());
        }

        msg.send();
    }

    public List<IShrink> toItems(int limit) {
        List<IShrink> sublist = new ObjectArrayList<>();
        for (int i = 0; i < Math.min(values.size(), limit); i++) {
            T elem = values.get(i);
            IShrink item;
            if (elem instanceof IShrink s && !s.isIdentical()) {
                item = s.clone();
            } else {
                item = (IShrink.of(elem.toString()));
            }
            if (highlight.contains(i)) {
                item = item.prepend("**").append("**");
            }
            item = item.prepend((i + 1) + ". ");
            sublist.add(item);
        }
        if (!highlight.isEmpty()) {
            boolean addedElipses = false;
            for (int i : highlight) {
                if (i < limit) continue;
                if (!addedElipses) {
                    sublist.add(IShrink.of("..."));
                    addedElipses = true;
                }
                T elem = values.get(i);
                IShrink item = elem instanceof IShrink s && !s.isIdentical() ? s.clone() : IShrink.of(elem.toString());
                item = item.prepend((i + 1) + ". ").prepend("**").append("**");
                sublist.add(item);
            }
        }
        return sublist;
    }

    @Override
    public String toString() {
        return StringMan.join(toItems(Integer.MAX_VALUE), "\n");
    }

    public List<T> get() {
        return values;
    }
}
