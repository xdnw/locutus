package link.locutus.discord.db.conflict;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import link.locutus.discord.util.TimeUtil;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class IndexHeaders {
    private IndexHeaders() {}

    private static <T> HeaderSpec<T> spec(String name, HeaderGroup group, Function<Conflict, T> f) {
        return new HeaderSpec<>(name, group, f);
    }

    public static final Map<HeaderGroup, List<HeaderSpec<?>>> HEADERS_BY_GROUP = Map.of(
            HeaderGroup.INDEX_META, List.of(
                    spec("id", HeaderGroup.INDEX_META, Conflict::getId),
                    spec("name", HeaderGroup.INDEX_META, Conflict::getName),
                    spec("c1_name", HeaderGroup.INDEX_META, f -> f.getCoalitionName(true)),
                    spec("c2_name", HeaderGroup.INDEX_META, f -> f.getCoalitionName(false)),
                    spec("start", HeaderGroup.INDEX_META, f -> TimeUtil.getTimeFromTurn(f.getStartTurn())),
                    spec("end", HeaderGroup.INDEX_META, f -> f.getEndTurn() == Long.MAX_VALUE ? -1 : TimeUtil.getTimeFromTurn(f.getEndTurn())),
                    spec("c1", HeaderGroup.INDEX_META, f -> new IntArrayList(f.getCoalition1())),
                    spec("c2", HeaderGroup.INDEX_META, f -> new IntArrayList(f.getCoalition2())),
                    spec("wiki", HeaderGroup.INDEX_META, Conflict::getWiki),
                    spec("status", HeaderGroup.INDEX_META, Conflict::getStatusDesc),
                    spec("cb", HeaderGroup.INDEX_META, Conflict::getCasusBelli),
                    spec("posts", HeaderGroup.INDEX_META, Conflict::getAnnouncementsList),
                    spec("source", HeaderGroup.INDEX_META, Conflict::getGuildId),
                    spec("category", HeaderGroup.INDEX_META, f -> f.getCategory().name())
            ),
            HeaderGroup.INDEX_STATS, List.of(
                    spec("wars", HeaderGroup.INDEX_STATS, Conflict::getTotalWars),
                    spec("active_wars", HeaderGroup.INDEX_STATS, Conflict::getActiveWars),
                    spec("c1_dealt", HeaderGroup.INDEX_STATS, f -> (long) f.getDamageConverted(true)),
                    spec("c2_dealt", HeaderGroup.INDEX_STATS, f -> (long) f.getDamageConverted(false))
            )
    );

    public static List<HeaderSpec<?>> headers(HeaderGroup group) {
        return HEADERS_BY_GROUP.getOrDefault(group, List.of());
    }

    public static List<String> names(List<HeaderSpec<?>> specs) {
        return specs.stream().map(HeaderSpec::name).toList();
    }

    public static List<String> names(HeaderGroup group) {
        return headers(group).stream().map(HeaderSpec::name).toList();
    }
}