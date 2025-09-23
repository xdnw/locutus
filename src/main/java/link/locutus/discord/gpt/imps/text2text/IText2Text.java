package link.locutus.discord.gpt.imps.text2text;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.gpt.ISummarizer;
import link.locutus.discord.gpt.ITokenizer;
import link.locutus.discord.gpt.imps.SimpleSummarizer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

public interface IText2Text  extends ITokenizer {
    public String getId();

    String generate(String text, IntConsumer usage);

    default String generate(String text, BiConsumer<String, Integer> usage) {
        return generate(text, (t) -> usage.accept(getId(), t));
    }

    default ISummarizer getSummarizer(@Nullable String promptOrNull, double promptToOutputRatio) {
        return new SimpleSummarizer(this, promptOrNull, promptToOutputRatio);
    }

    default <T> List<T> rerank(List<T> items, String criterion, IntConsumer tokensUsed, Function<T, String> getKey, Function<T, String> getValue) {
        int n = items.size();
        Object2ObjectLinkedOpenHashMap<String, String> map = new Object2ObjectLinkedOpenHashMap<>(Math.max(2, n));
        Object2IntOpenHashMap<String> firstIndex = new Object2IntOpenHashMap<>(Math.max(2, n));
        firstIndex.defaultReturnValue(-1);

        for (int i = 0; i < n; i++) {
            T item = items.get(i);
            String key = getKey.apply(item);
            String val = getValue.apply(item);
            if (map.containsKey(key)) {
                // update value but keep original insertion order / index
                map.put(key, val);
            } else {
                map.put(key, val);
                firstIndex.put(key, i);
            }
        }

        List<String> rankedKeys = rerank((Map<String, String>) map, criterion, tokensUsed);

        ObjectArrayList<T> result = new ObjectArrayList<>(rankedKeys.size());
        for (String k : rankedKeys) {
            int idx = firstIndex.getInt(k);
            if (idx >= 0) result.add(items.get(idx));
        }
        return result;
    }

    List<String> rerank(Map<String, String> items, String criterion, IntConsumer tokensUsed);
}
