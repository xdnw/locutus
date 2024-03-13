package link.locutus.discord.util.scheduler;

public interface TriConsumer<A, B, C> {
    void accept(A a, B b, C c);
}
