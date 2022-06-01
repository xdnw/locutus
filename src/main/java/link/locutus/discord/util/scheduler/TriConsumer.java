package link.locutus.discord.util.scheduler;

public interface TriConsumer<A, B, C> {
    void consume(A a, B b, C c);
}
