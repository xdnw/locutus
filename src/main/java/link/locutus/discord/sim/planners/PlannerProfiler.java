package link.locutus.discord.sim.planners;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

final class PlannerProfiler {
    enum Scope {
        BLITZ_ASSIGN,
        OPENING_EVALUATE,
        DEFENDER_COVERAGE_RESCUE,
        PRIMITIVE_ASSIGNMENT_SOLVE,
        LONG_HORIZON_SOLVE,
        EXACT_ASSIGNMENT_SCORE,
        EXACT_ASSIGNMENT_DELTA,
        CONFLICT_BUNDLE_EXTRACT,
        REPLAY_CAPTURE,
        REPLAY_NATION_STATE_EXPORT,
        PROJECTION_ADVANCE,
        PROJECTION_STATE_SNAPSHOTS_FOR,
        CONFLICT_SNAPSHOTS_FOR,
        PROJECTION_EXPORT,
        LOCAL_NATION_TO_SNAPSHOT,
        CITY_INFRA_OVERLAY_EXPORT,
        DBNATION_TO_BUILDER,
        SCHEDULED_ASSIGN
    }

    private static final ThreadLocal<Session> ACTIVE = new ThreadLocal<>();
    private static final ScopeToken NOOP_TOKEN = new ScopeToken(null, null, 0L);

    private PlannerProfiler() {
    }

    static <T> T withSession(Session session, Supplier<T> supplier) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(supplier, "supplier");
        Session previous = ACTIVE.get();
        ACTIVE.set(session);
        try {
            return supplier.get();
        } finally {
            restore(previous);
        }
    }

    static void withSession(Session session, Runnable runnable) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(runnable, "runnable");
        Session previous = ACTIVE.get();
        ACTIVE.set(session);
        try {
            runnable.run();
        } finally {
            restore(previous);
        }
    }

    static ScopeToken enter(Scope scope) {
        Session session = ACTIVE.get();
        if (session == null || scope == null) {
            return NOOP_TOKEN;
        }
        return new ScopeToken(session, scope, System.nanoTime());
    }

    static void addCounter(Scope scope, String counterName, long delta) {
        Session session = ACTIVE.get();
        if (session == null || scope == null || counterName == null || delta == 0L) {
            return;
        }
        session.addCounter(scope, counterName, delta);
    }

    private static void restore(Session previous) {
        if (previous == null) {
            ACTIVE.remove();
        } else {
            ACTIVE.set(previous);
        }
    }

    static final class ScopeToken implements AutoCloseable {
        private final Session session;
        private final Scope scope;
        private final long startNanos;

        private ScopeToken(Session session, Scope scope, long startNanos) {
            this.session = session;
            this.scope = scope;
            this.startNanos = startNanos;
        }

        @Override
        public void close() {
            if (session == null || scope == null) {
                return;
            }
            session.record(scope, System.nanoTime() - startNanos);
        }
    }

    static final class Session {
        private final EnumMap<Scope, MutableScopeStats> stats = new EnumMap<>(Scope.class);

        void record(Scope scope, long elapsedNanos) {
            MutableScopeStats scopeStats = stats.computeIfAbsent(scope, ignored -> new MutableScopeStats());
            scopeStats.calls++;
            scopeStats.totalNanos += elapsedNanos;
            scopeStats.maxNanos = Math.max(scopeStats.maxNanos, elapsedNanos);
        }

        void addCounter(Scope scope, String counterName, long delta) {
            MutableScopeStats scopeStats = stats.computeIfAbsent(scope, ignored -> new MutableScopeStats());
            scopeStats.counters.merge(counterName, delta, Long::sum);
        }

        ProfileSnapshot snapshot() {
            EnumMap<Scope, ScopeStats> snapshot = new EnumMap<>(Scope.class);
            for (Map.Entry<Scope, MutableScopeStats> entry : stats.entrySet()) {
                snapshot.put(entry.getKey(), entry.getValue().snapshot());
            }
            return new ProfileSnapshot(Collections.unmodifiableMap(snapshot));
        }
    }

    record ProfileSnapshot(Map<Scope, ScopeStats> scopes) {
        ProfileSnapshot {
            scopes = Objects.requireNonNull(scopes, "scopes");
        }

        ScopeStats stats(Scope scope) {
            return scopes.getOrDefault(scope, ScopeStats.EMPTY);
        }
    }

    record ScopeStats(long calls, long totalNanos, long maxNanos, Map<String, Long> counters) {
        private static final ScopeStats EMPTY = new ScopeStats(0L, 0L, 0L, Map.of());

        ScopeStats {
            counters = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(counters, "counters")));
        }

        double totalMillis() {
            return totalNanos / 1_000_000.0d;
        }

        double maxMillis() {
            return maxNanos / 1_000_000.0d;
        }
    }

    private static final class MutableScopeStats {
        private long calls;
        private long totalNanos;
        private long maxNanos;
        private final LinkedHashMap<String, Long> counters = new LinkedHashMap<>();

        ScopeStats snapshot() {
            return new ScopeStats(calls, totalNanos, maxNanos, counters);
        }
    }
}