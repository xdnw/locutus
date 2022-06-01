package link.locutus.discord.commands.rankings.builder;

import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.StringMan;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class RankBuilder<T> {
    private List<T> values;
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

    public <G> RankBuilder<G> adapt(Function<T, G> adapter) {
        List<G> transform = Lists.transform(values, adapter);
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
        Collections.sort(values, comparator);
        return this;
    }

    public RankBuilder<T> limit(int amount) {
        values = values.subList(0, Math.min(values.size(), amount));
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

    public Message build(MessageReceivedEvent event, String title) {
        return build(event.getAuthor(), event.getChannel(), DiscordUtil.trimContent(event.getMessage().getContentRaw()), title);
    }

    public Message build(MessageChannel channel, String cmd, String title) {
        return build(null, channel, cmd, title);
    }

    public Message build(User author, MessageChannel channel, String cmd, String title) {
        List<String> items = toItems(25);
        String emoji = "\uD83D\uDD04";
        String itemsStr = StringMan.join(items, "\n") + "\n";
        if (cmd != null) itemsStr += "\npress " + emoji + " to refresh";
        if (author != null) itemsStr += "\n" + author.getAsMention();

        String[] args = cmd == null ? new String[0] : new String[] {emoji, cmd};
        return DiscordUtil.createEmbedCommand(channel, title, itemsStr, args);
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
