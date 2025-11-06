package link.locutus.discord.db.conflict;

import java.util.function.Function;

public final class HeaderSpec<T> {
    private final String name;
    private final HeaderGroup group;
    private final Function<Conflict, T> extractor;

    public HeaderSpec(String name, HeaderGroup group, Function<Conflict, T> extractor) {
        this.name = name;
        this.group = group;
        this.extractor = extractor;
    }

    public String name() { return name; }
    public HeaderGroup group() { return group; }
    public Function<Conflict, T> extractor() { return extractor; }
}