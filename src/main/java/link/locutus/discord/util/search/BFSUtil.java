package link.locutus.discord.util.search;

import link.locutus.discord.Logg;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;
import link.locutus.discord.util.MathMan;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

public class BFSUtil<T> {

    private final Predicate<T> goal;
    private final ToDoubleFunction<T> valueFunction;
    private final Function<Double, Function<T, Double>> valueCompletionFunction;
    private final ToDoubleFunction<T> upperBoundFunction;
    private final BiConsumer<T, Consumer<T>> branch;
    private final Consumer<T> garbageLastMax;
    private final T origin;
    private final long timeout;

    private static final long DEFAULT_MAX_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(1);
    private static final String PROFILE_PROPERTY = "locutus.bfs.profile";
    private static final String PROFILE_ENV = "LOCUTUS_BFS_PROFILE";
    private static final String PRUNING_PROPERTY = "locutus.bfs.boundPruning";
    private static final String PRUNING_ENV = "LOCUTUS_BFS_BOUND_PRUNING";
    private static final String STRICT_TIMEOUT_PROPERTY = "locutus.bfs.strictTimeout";
    private static final String STRICT_TIMEOUT_ENV = "LOCUTUS_BFS_STRICT_TIMEOUT";
    private static final String MAX_TIMEOUT_PROPERTY = "locutus.bfs.maxTimeoutMs";
    private static final String MAX_TIMEOUT_ENV = "LOCUTUS_BFS_MAX_TIMEOUT_MS";
    private static final String REPRIORITIZE_MODE_PROPERTY = "locutus.bfs.reprioritizeMode";
    private static final String REPRIORITIZE_MODE_ENV = "LOCUTUS_BFS_REPRIORITIZE_MODE";
    private static final String SCHEDULER_MODE_PROPERTY = "locutus.bfs.schedulerMode";
    private static final String SCHEDULER_MODE_ENV = "LOCUTUS_BFS_SCHEDULER_MODE";
    private static final String SCHEDULE_BASE_STEP_PROPERTY = "locutus.bfs.schedule.baseStep";
    private static final String SCHEDULE_BASE_STEP_ENV = "LOCUTUS_BFS_SCHEDULE_BASE_STEP";
    private static final String SCHEDULE_SLOPE_WINDOW_PROPERTY = "locutus.bfs.schedule.slopeWindow";
    private static final String SCHEDULE_SLOPE_WINDOW_ENV = "LOCUTUS_BFS_SCHEDULE_SLOPE_WINDOW";
    private static final String SCHEDULE_MIN_IMPROVEMENT_EPS_PROPERTY = "locutus.bfs.schedule.minImprovementEpsilon";
    private static final String SCHEDULE_MIN_IMPROVEMENT_EPS_ENV = "LOCUTUS_BFS_SCHEDULE_MIN_IMPROVEMENT_EPSILON";
    private static final String SCHEDULE_GRACE_DELAY_PROPERTY = "locutus.bfs.schedule.graceDelayMs";
    private static final String SCHEDULE_GRACE_DELAY_ENV = "LOCUTUS_BFS_SCHEDULE_GRACE_DELAY_MS";
    private static final String SCHEDULE_STAGNATION_WINDOW_PROPERTY = "locutus.bfs.schedule.stagnationWindow";
    private static final String SCHEDULE_STAGNATION_WINDOW_ENV = "LOCUTUS_BFS_SCHEDULE_STAGNATION_WINDOW";
    private static final String SCHEDULE_PRESSURE_THRESHOLD_PROPERTY = "locutus.bfs.schedule.pressureThreshold";
    private static final String SCHEDULE_PRESSURE_THRESHOLD_ENV = "LOCUTUS_BFS_SCHEDULE_PRESSURE_THRESHOLD";
    private static final String SCHEDULE_MAX_STEP_PROPERTY = "locutus.bfs.schedule.maxStep";
    private static final String SCHEDULE_MAX_STEP_ENV = "LOCUTUS_BFS_SCHEDULE_MAX_STEP";
    private static final String FRONTIER_STALE_REFRESH_BUDGET_PROPERTY = "locutus.bfs.frontier.staleRefreshBudget";
    private static final String FRONTIER_STALE_REFRESH_BUDGET_ENV = "LOCUTUS_BFS_FRONTIER_STALE_REFRESH_BUDGET";
    private static final String FRONTIER_HEAD_STABILIZATION_LIMIT_PROPERTY = "locutus.bfs.frontier.headStabilizationLimit";
    private static final String FRONTIER_HEAD_STABILIZATION_LIMIT_ENV = "LOCUTUS_BFS_FRONTIER_HEAD_STABILIZATION_LIMIT";
    private static final String FRONTIER_FALLBACK_EAGER_REBUILD_PROPERTY = "locutus.bfs.frontier.fallbackEagerRebuild";
    private static final String FRONTIER_FALLBACK_EAGER_REBUILD_ENV = "LOCUTUS_BFS_FRONTIER_FALLBACK_EAGER_REBUILD";
    private static final String CITY_NODE_CLASS = "link.locutus.discord.db.entities.CityNode";

    private static final ThreadLocal<SearchMetrics> LAST_METRICS = new ThreadLocal<>();

    public enum SearchStage {
        EXACT,
        AGGRESSIVE
    }

    public static final class SearchMetrics {
        public boolean profileEnabled;
        public long elapsedMs;
        public long expandedNodes;
        public long enqueuedNodes;
        public long prunedByBound;
        public long valueFunctionCalls;
        public long valueFunctionTimeNs;
        public long upperBoundCalls;
        public long upperBoundTimeNs;
        public long goalChecks;
        public long goalCheckTimeNs;
        public long pruneChecks;
        public long pruneCheckTimeNs;
        public long branchCalls;
        public long branchTimeNs;
        public long enqueueTimeNs;
        public long reprioritizeTimeNs;
        public long timeoutChecks;
        public long timeoutAdjustments;
        public long timeoutStartShiftMs;
        public long queueReprioritizations;
        public int stageTransitions;
        public int maxQueueSize;
        public double nodesPerSecond;
        public double bestScore = Double.NEGATIVE_INFINITY;
        public SearchStage lastStage = SearchStage.EXACT;
        public double checkpoint25Score = Double.NEGATIVE_INFINITY;
        public double checkpoint50Score = Double.NEGATIVE_INFINITY;
        public double checkpoint75Score = Double.NEGATIVE_INFINITY;
        public double checkpoint100Score = Double.NEGATIVE_INFINITY;
        public String reprioritizeMode = "lazy";
        public String schedulerMode = "adaptive";

        public SearchMetrics copy() {
            SearchMetrics copy = new SearchMetrics();
            copy.profileEnabled = profileEnabled;
            copy.elapsedMs = elapsedMs;
            copy.expandedNodes = expandedNodes;
            copy.enqueuedNodes = enqueuedNodes;
            copy.prunedByBound = prunedByBound;
            copy.valueFunctionCalls = valueFunctionCalls;
            copy.valueFunctionTimeNs = valueFunctionTimeNs;
            copy.upperBoundCalls = upperBoundCalls;
            copy.upperBoundTimeNs = upperBoundTimeNs;
            copy.goalChecks = goalChecks;
            copy.goalCheckTimeNs = goalCheckTimeNs;
            copy.pruneChecks = pruneChecks;
            copy.pruneCheckTimeNs = pruneCheckTimeNs;
            copy.branchCalls = branchCalls;
            copy.branchTimeNs = branchTimeNs;
            copy.enqueueTimeNs = enqueueTimeNs;
            copy.reprioritizeTimeNs = reprioritizeTimeNs;
            copy.timeoutChecks = timeoutChecks;
            copy.timeoutAdjustments = timeoutAdjustments;
            copy.timeoutStartShiftMs = timeoutStartShiftMs;
            copy.queueReprioritizations = queueReprioritizations;
            copy.stageTransitions = stageTransitions;
            copy.maxQueueSize = maxQueueSize;
            copy.nodesPerSecond = nodesPerSecond;
            copy.bestScore = bestScore;
            copy.lastStage = lastStage;
            copy.checkpoint25Score = checkpoint25Score;
            copy.checkpoint50Score = checkpoint50Score;
            copy.checkpoint75Score = checkpoint75Score;
            copy.checkpoint100Score = checkpoint100Score;
            copy.reprioritizeMode = reprioritizeMode;
            copy.schedulerMode = schedulerMode;
            return copy;
        }
    }

    private enum ReprioritizeMode {
        LAZY,
        EAGER;

        static ReprioritizeMode resolve() {
            String configured = readStringPropertyOrEnv(REPRIORITIZE_MODE_PROPERTY, REPRIORITIZE_MODE_ENV);
            if (configured == null) {
                return LAZY;
            }
            return "eager".equalsIgnoreCase(configured.trim()) ? EAGER : LAZY;
        }
    }

    private enum SchedulerMode {
        ADAPTIVE,
        LEGACY;

        static SchedulerMode resolve() {
            String configured = readStringPropertyOrEnv(SCHEDULER_MODE_PROPERTY, SCHEDULER_MODE_ENV);
            if (configured == null) {
                return ADAPTIVE;
            }
            return "legacy".equalsIgnoreCase(configured.trim()) ? LEGACY : ADAPTIVE;
        }
    }

    private static final class ScoredNode<T> {
        private final T state;
        private final double exactScore;
        private double priorityScore;
        private final double upperBound;
        private final int depth;
        private final long sequence;
        private long epoch;

        private ScoredNode(T state, double exactScore, double priorityScore, double upperBound, int depth, long sequence, long epoch) {
            this.state = state;
            this.exactScore = exactScore;
            this.priorityScore = priorityScore;
            this.upperBound = upperBound;
            this.depth = depth;
            this.sequence = sequence;
            this.epoch = epoch;
        }
    }

    private static final class FrontierEntry<T> {
        private T state;
        private double exactScore;
        private double upperBound;
        private int depth;

        private void set(T state, double exactScore, double upperBound, int depth) {
            this.state = state;
            this.exactScore = exactScore;
            this.upperBound = upperBound;
            this.depth = depth;
        }
    }

    private interface Frontier<T> {
        void push(T state, double exactScore, double upperBound, int depth);
        boolean popBest(FrontierEntry<T> out);
        boolean isEmpty();
        int size();
        void reprioritize(Function<T, Double> reprioritizer);
    }

    private record FrontierMaintenancePolicy(int staleRefreshBudget, int headStabilizationLimit, boolean allowFallbackEagerRebuild) {
        static FrontierMaintenancePolicy resolve() {
            int staleRefreshBudget = (int) parsePositiveLongOrDefault(
                    readStringPropertyOrEnv(FRONTIER_STALE_REFRESH_BUDGET_PROPERTY, FRONTIER_STALE_REFRESH_BUDGET_ENV),
                    32L);
            int headStabilizationLimit = (int) parsePositiveLongOrDefault(
                    readStringPropertyOrEnv(FRONTIER_HEAD_STABILIZATION_LIMIT_PROPERTY, FRONTIER_HEAD_STABILIZATION_LIMIT_ENV),
                    256L);
            String fallbackRaw = readStringPropertyOrEnv(FRONTIER_FALLBACK_EAGER_REBUILD_PROPERTY, FRONTIER_FALLBACK_EAGER_REBUILD_ENV);
            boolean fallback = fallbackRaw == null || Boolean.parseBoolean(fallbackRaw);
            return new FrontierMaintenancePolicy(Math.max(1, staleRefreshBudget), Math.max(1, headStabilizationLimit), fallback);
        }
    }

    private static final class ObjectFrontier<T> implements Frontier<T> {
        private static <T> int compare(ScoredNode<T> left, ScoredNode<T> right) {
            int byPriority = Double.compare(right.priorityScore, left.priorityScore);
            if (byPriority != 0) {
                return byPriority;
            }
            int byDepth = Integer.compare(left.depth, right.depth);
            if (byDepth != 0) {
                return byDepth;
            }
            return Long.compare(left.sequence, right.sequence);
        }

        private final ObjectHeapPriorityQueue<ScoredNode<T>> queue =
                new ObjectHeapPriorityQueue<>(500000, ObjectFrontier::compare);
        private final ReprioritizeMode reprioritizeMode;
        private final FrontierMaintenancePolicy maintenancePolicy;
        private Function<T, Double> activeReprioritizer;
        private long activeEpoch;
        private long nextSequence;

        private ObjectFrontier(ReprioritizeMode reprioritizeMode, FrontierMaintenancePolicy maintenancePolicy) {
            this.reprioritizeMode = reprioritizeMode;
            this.maintenancePolicy = maintenancePolicy;
        }

        private double activePriorityFor(T state, double exactScore) {
            return activeReprioritizer == null ? exactScore : activeReprioritizer.apply(state);
        }

        @Override
        public void push(T state, double exactScore, double upperBound, int depth) {
            double priorityScore = activePriorityFor(state, exactScore);
            queue.enqueue(new ScoredNode<>(state, exactScore, priorityScore, upperBound, depth, nextSequence++, activeEpoch));
        }

        @Override
        public boolean popBest(FrontierEntry<T> out) {
            if (queue.isEmpty()) {
                return false;
            }

            if (reprioritizeMode == ReprioritizeMode.LAZY && activeReprioritizer != null) {
                refreshHeadStale(maintenancePolicy.staleRefreshBudget());
                if (!isHeadFresh() && !stabilizeHead(maintenancePolicy.headStabilizationLimit())
                        && maintenancePolicy.allowFallbackEagerRebuild()) {
                    refreshAllStaleEager();
                }
            }

            ScoredNode<T> next = queue.dequeue();
            out.set(next.state, next.exactScore, next.upperBound, next.depth);
            return true;
        }

        @Override
        public boolean isEmpty() {
            return queue.isEmpty();
        }

        @Override
        public int size() {
            return queue.size();
        }

        @Override
        public void reprioritize(Function<T, Double> reprioritizer) {
            activeReprioritizer = reprioritizer;
            activeEpoch++;
            if (reprioritizeMode == ReprioritizeMode.LAZY || queue.isEmpty()) {
                return;
            }
            refreshAllStaleEager();
        }

        private boolean isHeadFresh() {
            return queue.isEmpty() || queue.first().epoch == activeEpoch;
        }

        private void refreshHeadStale(int budget) {
            int refreshed = 0;
            while (!queue.isEmpty() && refreshed < budget) {
                ScoredNode<T> top = queue.first();
                if (top.epoch == activeEpoch) {
                    return;
                }
                top.priorityScore = activeReprioritizer.apply(top.state);
                top.epoch = activeEpoch;
                queue.changed();
                refreshed++;
            }
        }

        private boolean stabilizeHead(int maxRefreshes) {
            int refreshed = 0;
            while (!queue.isEmpty() && refreshed < maxRefreshes) {
                ScoredNode<T> top = queue.first();
                if (top.epoch == activeEpoch) {
                    return true;
                }
                top.priorityScore = activeReprioritizer.apply(top.state);
                top.epoch = activeEpoch;
                queue.changed();
                refreshed++;
            }
            return isHeadFresh();
        }

        private void refreshAllStaleEager() {
            int size = queue.size();
            @SuppressWarnings("unchecked")
            ScoredNode<T>[] entries = new ScoredNode[size];
            for (int i = 0; i < size; i++) {
                entries[i] = queue.dequeue();
            }
            for (ScoredNode<T> entry : entries) {
                if (entry.epoch != activeEpoch) {
                    entry.priorityScore = activeReprioritizer.apply(entry.state);
                    entry.epoch = activeEpoch;
                }
                queue.enqueue(entry);
            }
        }
    }

    private static final class PrimitiveFrontier<T> implements Frontier<T> {
        private int[] heapSlots = new int[1024];
        private Object[] stateBySlot = new Object[1024];
        private double[] exactBySlot = new double[1024];
        private double[] priorityBySlot = new double[1024];
        private double[] upperBySlot = new double[1024];
        private int[] depthBySlot = new int[1024];
        private long[] sequenceBySlot = new long[1024];
        private long[] epochBySlot = new long[1024];
        private int[] freeSlots = new int[1024];

        private int heapSize;
        private int nextSlot;
        private int freeSize;
        private long nextSequence;
        private long activeEpoch;
        private final ReprioritizeMode reprioritizeMode;
        private final FrontierMaintenancePolicy maintenancePolicy;
        private Function<T, Double> activeReprioritizer;

        private PrimitiveFrontier(ReprioritizeMode reprioritizeMode, FrontierMaintenancePolicy maintenancePolicy) {
            this.reprioritizeMode = reprioritizeMode;
            this.maintenancePolicy = maintenancePolicy;
        }

        private double activePriorityFor(T state, double exactScore) {
            return activeReprioritizer == null ? exactScore : activeReprioritizer.apply(state);
        }

        private boolean higherPrioritySlot(int leftSlot, int rightSlot) {
            int byPriority = Double.compare(priorityBySlot[leftSlot], priorityBySlot[rightSlot]);
            if (byPriority != 0) {
                return byPriority > 0;
            }
            int byDepth = Integer.compare(depthBySlot[rightSlot], depthBySlot[leftSlot]);
            if (byDepth != 0) {
                return byDepth > 0;
            }
            return sequenceBySlot[leftSlot] < sequenceBySlot[rightSlot];
        }

        @Override
        public void push(T state, double exactScore, double upperBound, int depth) {
            int slot;
            if (freeSize > 0) {
                slot = freeSlots[--freeSize];
            } else {
                slot = nextSlot++;
                ensureSlotCapacity(nextSlot);
            }
            ensureHeapCapacity(heapSize + 1);

            stateBySlot[slot] = state;
            exactBySlot[slot] = exactScore;
            priorityBySlot[slot] = activePriorityFor(state, exactScore);
            upperBySlot[slot] = upperBound;
            depthBySlot[slot] = depth;
            sequenceBySlot[slot] = nextSequence++;
            epochBySlot[slot] = activeEpoch;

            heapSlots[heapSize] = slot;
            siftUp(heapSize);
            heapSize++;
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean popBest(FrontierEntry<T> out) {
            if (heapSize == 0) {
                return false;
            }

            if (reprioritizeMode == ReprioritizeMode.LAZY && activeReprioritizer != null) {
                refreshHeadStale(maintenancePolicy.staleRefreshBudget());
                if (!isHeadFresh() && !stabilizeHead(maintenancePolicy.headStabilizationLimit())
                        && maintenancePolicy.allowFallbackEagerRebuild()) {
                    refreshAllStaleEager();
                }
            }

            int bestSlot = heapSlots[0];
            int lastSlot = heapSlots[--heapSize];
            if (heapSize > 0) {
                heapSlots[0] = lastSlot;
                siftDown(0);
            }

            T state = (T) stateBySlot[bestSlot];
            out.set(state,
                    exactBySlot[bestSlot],
                    upperBySlot[bestSlot],
                    depthBySlot[bestSlot]);

            stateBySlot[bestSlot] = null;
                ensureFreeCapacity(freeSize + 1);
                freeSlots[freeSize++] = bestSlot;
            return true;
        }

        @Override
        public boolean isEmpty() {
            return heapSize == 0;
        }

        @Override
        public int size() {
            return heapSize;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void reprioritize(Function<T, Double> reprioritizer) {
            activeReprioritizer = reprioritizer;
            activeEpoch++;
            if (reprioritizeMode == ReprioritizeMode.LAZY || heapSize == 0) {
                return;
            }
            refreshAllStaleEager();
        }

        @SuppressWarnings("unchecked")
        private void refreshHeadStale(int budget) {
            int refreshed = 0;
            while (heapSize > 0 && refreshed < budget) {
                int topSlot = heapSlots[0];
                if (epochBySlot[topSlot] == activeEpoch) {
                    return;
                }
                T topState = (T) stateBySlot[topSlot];
                priorityBySlot[topSlot] = activeReprioritizer.apply(topState);
                epochBySlot[topSlot] = activeEpoch;
                siftDown(0);
                refreshed++;
            }
        }

        private boolean isHeadFresh() {
            return heapSize == 0 || epochBySlot[heapSlots[0]] == activeEpoch;
        }

        @SuppressWarnings("unchecked")
        private boolean stabilizeHead(int maxRefreshes) {
            int refreshed = 0;
            while (heapSize > 0 && refreshed < maxRefreshes) {
                int topSlot = heapSlots[0];
                if (epochBySlot[topSlot] == activeEpoch) {
                    return true;
                }
                T topState = (T) stateBySlot[topSlot];
                priorityBySlot[topSlot] = activeReprioritizer.apply(topState);
                epochBySlot[topSlot] = activeEpoch;
                siftDown(0);
                refreshed++;
            }
            return isHeadFresh();
        }

        @SuppressWarnings("unchecked")
        private void refreshAllStaleEager() {
            for (int i = 0; i < heapSize; i++) {
                int slot = heapSlots[i];
                if (epochBySlot[slot] != activeEpoch) {
                    T state = (T) stateBySlot[slot];
                    priorityBySlot[slot] = activeReprioritizer.apply(state);
                    epochBySlot[slot] = activeEpoch;
                }
            }
            for (int i = (heapSize >>> 1) - 1; i >= 0; i--) {
                siftDown(i);
            }
        }

        private void ensureHeapCapacity(int required) {
            if (required <= heapSlots.length) {
                return;
            }
            int newSize = Math.max(required, heapSlots.length << 1);
            heapSlots = Arrays.copyOf(heapSlots, newSize);
        }

        private void ensureSlotCapacity(int requiredSlots) {
            if (requiredSlots <= stateBySlot.length) {
                return;
            }
            int newSize = Math.max(requiredSlots, stateBySlot.length << 1);
            stateBySlot = Arrays.copyOf(stateBySlot, newSize);
            exactBySlot = Arrays.copyOf(exactBySlot, newSize);
            priorityBySlot = Arrays.copyOf(priorityBySlot, newSize);
            upperBySlot = Arrays.copyOf(upperBySlot, newSize);
            depthBySlot = Arrays.copyOf(depthBySlot, newSize);
            sequenceBySlot = Arrays.copyOf(sequenceBySlot, newSize);
            epochBySlot = Arrays.copyOf(epochBySlot, newSize);
        }

        private void ensureFreeCapacity(int required) {
            if (required <= freeSlots.length) {
                return;
            }
            int newSize = Math.max(required, freeSlots.length << 1);
            freeSlots = Arrays.copyOf(freeSlots, newSize);
        }

        private void siftUp(int index) {
            int slot = heapSlots[index];
            while (index > 0) {
                int parent = (index - 1) >>> 1;
                int parentSlot = heapSlots[parent];
                if (higherPrioritySlot(parentSlot, slot)) {
                    break;
                }
                heapSlots[index] = parentSlot;
                index = parent;
            }
            heapSlots[index] = slot;
        }

        private void siftDown(int index) {
            int slot = heapSlots[index];
            int half = heapSize >>> 1;
            while (index < half) {
                int left = (index << 1) + 1;
                int right = left + 1;
                int bestChild = left;
                int bestChildSlot = heapSlots[left];
                if (right < heapSize) {
                    int rightSlot = heapSlots[right];
                    if (higherPrioritySlot(rightSlot, bestChildSlot)) {
                        bestChild = right;
                        bestChildSlot = rightSlot;
                    }
                }
                if (!higherPrioritySlot(bestChildSlot, slot)) {
                    break;
                }
                heapSlots[index] = bestChildSlot;
                index = bestChild;
            }
            heapSlots[index] = slot;
        }
    }

    public BFSUtil(Predicate<T> goal, ToDoubleFunction<T> valueFunction,
                   Function<Double, Function<T, Double>> valueCompletionFunction,
                   ToDoubleFunction<T> upperBoundFunction,
                   BiConsumer<T, Consumer<T>> branch, Consumer<T> garbageLastMax,
                   T origin, long timeout) {
        this.goal = goal;
        this.valueFunction = valueFunction;
        this.valueCompletionFunction = valueCompletionFunction;
        this.upperBoundFunction = upperBoundFunction;
        this.branch = branch;
        this.garbageLastMax = garbageLastMax;
        this.origin = origin;
        this.timeout = timeout;
    }

    public static SearchMetrics getLastMetrics() {
        SearchMetrics metrics = LAST_METRICS.get();
        return metrics == null ? null : metrics.copy();
    }

    private static double computeNodesPerSecond(long processed, long elapsedMs) {
        if (elapsedMs <= 0) {
            return processed;
        }
        return processed * 1000d / elapsedMs;
    }

    private Frontier<T> createFrontier() {
        ReprioritizeMode reprioritizeMode = ReprioritizeMode.resolve();
        FrontierMaintenancePolicy maintenancePolicy = FrontierMaintenancePolicy.resolve();
        if (origin != null && CITY_NODE_CLASS.equals(origin.getClass().getName())) {
            return new PrimitiveFrontier<>(reprioritizeMode, maintenancePolicy);
        }
        return new ObjectFrontier<>(reprioritizeMode, maintenancePolicy);
    }

    public T search() {
        final Frontier<T> frontier = createFrontier();

        T max = null;
        double maxValue = Double.NEGATIVE_INFINITY;

        SearchMetrics metrics = new SearchMetrics();
        boolean profileEnabled = isProfilingEnabled();
        boolean boundPruningEnabled = isBoundPruningEnabled();
        boolean strictTimeoutEnabled = isStrictTimeoutEnabled();
        long maxTimeoutMs = resolveMaxTimeoutMs();
        ReprioritizeMode reprioritizeMode = ReprioritizeMode.resolve();
        SchedulerMode schedulerMode = SchedulerMode.resolve();
        AdaptiveSchedulePolicy adaptivePolicy = AdaptiveSchedulePolicy.resolve();
        metrics.profileEnabled = profileEnabled;
        metrics.reprioritizeMode = reprioritizeMode.name().toLowerCase();
        metrics.schedulerMode = schedulerMode.name().toLowerCase();
        SearchStage stage = SearchStage.EXACT;

        long originalStart = System.currentTimeMillis();
        long start = originalStart;

        double completeFactor = 0;
        Function<T, Double> completionValue = null;
        final double[] recentBest = new double[Math.max(2, adaptivePolicy.slopeWindow())];
        int recentBestCount = 0;
        int recentBestIndex = 0;
        int stagnationWindows = 0;
        int lastQueueSize = 0;

        long delay = 5000;

        final ToDoubleFunction<T> timedValueFunction = node -> {
            if (!profileEnabled) {
                return valueFunction.applyAsDouble(node);
            }
            long t0 = System.nanoTime();
            double value = valueFunction.applyAsDouble(node);
            metrics.valueFunctionCalls++;
            metrics.valueFunctionTimeNs += System.nanoTime() - t0;
            return value;
        };

        final FrontierEntry<T> current = new FrontierEntry<>();
        double originExactScore = timedValueFunction.applyAsDouble(origin);
        long enqueueStart = profileEnabled ? System.nanoTime() : 0L;
        frontier.push(origin,
                originExactScore,
            boundPruningEnabled ? Double.NaN : Double.POSITIVE_INFINITY,
                0);
        metrics.enqueuedNodes++;
        metrics.maxQueueSize = Math.max(metrics.maxQueueSize, frontier.size());
        if (profileEnabled) {
            metrics.enqueueTimeNs += System.nanoTime() - enqueueStart;
        }

        int i = 0;
        while (!frontier.isEmpty()) {
            if (!frontier.popBest(current)) {
                break;
            }
            metrics.expandedNodes++;
            i++;

            if (strictTimeoutEnabled || (i & 0xFFFF) == 0) {
                if (profileEnabled) {
                    metrics.timeoutChecks++;
                }
                long diff = System.currentTimeMillis() - start;
                if (diff > timeout) {
                    if (strictTimeoutEnabled) {
                        break;
                    }
                    if (max != null && diff > timeout + 5000) {
                        break;
                    } else if (max == null && completeFactor + 0.05 < 2 && valueCompletionFunction != null) {
                        boolean isNew = completeFactor == 0;
                        int queueSize = frontier.size();
                        double improvementSlope = schedulerMode == SchedulerMode.LEGACY
                            ? 0d
                            : computeImprovementSlope(recentBest, recentBestCount, recentBestIndex);
                        boolean stagnatingNow = schedulerMode != SchedulerMode.LEGACY
                            && Math.abs(improvementSlope) < adaptivePolicy.minImprovementEpsilon();
                        stagnationWindows = stagnatingNow ? stagnationWindows + 1 : 0;
                        double nextFactor = schedulerMode == SchedulerMode.LEGACY
                                ? legacyNextFactor(completeFactor)
                            : adaptivePolicy.nextFactor(completeFactor, timeout, diff, queueSize, lastQueueSize, improvementSlope, stagnationWindows);
                        completeFactor = Math.min(2d, nextFactor);
                        completionValue = valueCompletionFunction.apply(completeFactor);
                        if (stage != SearchStage.AGGRESSIVE) {
                            stage = SearchStage.AGGRESSIVE;
                            metrics.stageTransitions++;
                        }
                        metrics.lastStage = stage;
                        if (isNew || completeFactor >= 0.1) {
                            long reprioritizeStart = profileEnabled ? System.nanoTime() : 0L;
                            frontier.reprioritize(completionValue);
                            metrics.queueReprioritizations++;
                            if (profileEnabled) {
                                metrics.reprioritizeTimeNs += System.nanoTime() - reprioritizeStart;
                            }
                        }

                        if (profileEnabled) {
                            metrics.timeoutAdjustments++;
                        }
                        long adjustmentDelay = schedulerMode == SchedulerMode.LEGACY ? delay : adaptivePolicy.graceDelayMs();
                        start = System.currentTimeMillis() - timeout + adjustmentDelay;
                        if (profileEnabled) {
                            metrics.timeoutStartShiftMs += adjustmentDelay;
                        }
                        lastQueueSize = queueSize;
                    } else if (System.currentTimeMillis() - originalStart > maxTimeoutMs) {
                        break;
                    }
                }
            }

            long pruneStart = profileEnabled ? System.nanoTime() : 0L;
            if (profileEnabled) {
                metrics.pruneChecks++;
            }
            if (boundPruningEnabled && maxValue != Double.NEGATIVE_INFINITY) {
                double threshold = aggressivePruneThreshold(maxValue, stage);
                if (current.exactScore > threshold) {
                    if (profileEnabled) {
                        metrics.pruneCheckTimeNs += System.nanoTime() - pruneStart;
                    }
                } else {
                double upperBound = current.upperBound;
                if (Double.isNaN(upperBound)) {
                    upperBound = upperBound(current.state, current.exactScore, metrics);
                    current.upperBound = upperBound;
                }
                    if (upperBound <= threshold) {
                    if (profileEnabled) {
                        metrics.pruneCheckTimeNs += System.nanoTime() - pruneStart;
                    }
                    metrics.prunedByBound++;
                    garbageLastMax.accept(current.state);
                    continue;
                }
                }
            }
            if (profileEnabled) {
                metrics.pruneCheckTimeNs += System.nanoTime() - pruneStart;
            }

            long goalStart = profileEnabled ? System.nanoTime() : 0L;
            boolean result = goal.test(current.state);
            if (profileEnabled) {
                metrics.goalChecks++;
                metrics.goalCheckTimeNs += System.nanoTime() - goalStart;
            }
            if (result) {
                double value = current.exactScore;
                if (value > maxValue) {
                    T lastMax = max;
                    max = current.state;
                    maxValue = value;
                    metrics.bestScore = maxValue;

                    if (lastMax != null && lastMax != current.state) {
                        garbageLastMax.accept(lastMax);
                    }
                } else {
                    garbageLastMax.accept(current.state);
                }
                continue;
            }

            long branchStart = profileEnabled ? System.nanoTime() : 0L;
            if (profileEnabled) {
                metrics.branchCalls++;
            }
            branch.accept(current.state, child -> {
                double exactScore = timedValueFunction.applyAsDouble(child);
                long childEnqueueStart = profileEnabled ? System.nanoTime() : 0L;
                frontier.push(child,
                        exactScore,
                        boundPruningEnabled ? Double.NaN : Double.POSITIVE_INFINITY,
                        current.depth + 1);
                metrics.enqueuedNodes++;
                metrics.maxQueueSize = Math.max(metrics.maxQueueSize, frontier.size());
                if (profileEnabled) {
                    metrics.enqueueTimeNs += System.nanoTime() - childEnqueueStart;
                }
            });
            if (profileEnabled) {
                metrics.branchTimeNs += System.nanoTime() - branchStart;
            }

            if (maxValue != Double.NEGATIVE_INFINITY) {
                recentBest[recentBestIndex] = maxValue;
                recentBestIndex = (recentBestIndex + 1) % recentBest.length;
                if (recentBestCount < recentBest.length) {
                    recentBestCount++;
                }
            }

            updateConvergenceCheckpoints(metrics, timeout, System.currentTimeMillis() - originalStart, maxValue);
        }

        long diff = System.currentTimeMillis() - originalStart;
        metrics.elapsedMs = diff;
        metrics.nodesPerSecond = computeNodesPerSecond(metrics.expandedNodes, diff);
        metrics.bestScore = maxValue;
        metrics.lastStage = stage;
        LAST_METRICS.set(metrics.copy());

        Logg.text("BFS/A* searched " + metrics.expandedNodes + " options in " + diff + "ms for a rate of " + MathMan.format(metrics.nodesPerSecond) + " per second"
                + " | enqueued=" + metrics.enqueuedNodes
                + " | prunedByBound=" + metrics.prunedByBound
            + " | boundPruning=" + boundPruningEnabled
                + " | reprioritized=" + metrics.queueReprioritizations
                + " | bestScore=" + MathMan.format(maxValue));
        Logg.text("BFS/A* profile"
            + " | profileEnabled=" + metrics.profileEnabled
            + " | valueCalls=" + metrics.valueFunctionCalls
            + " | valueMs=" + MathMan.format(metrics.valueFunctionTimeNs / 1_000_000d)
            + " | upperBoundCalls=" + metrics.upperBoundCalls
            + " | upperBoundMs=" + MathMan.format(metrics.upperBoundTimeNs / 1_000_000d)
            + " | goalMs=" + MathMan.format(metrics.goalCheckTimeNs / 1_000_000d)
            + " | pruneMs=" + MathMan.format(metrics.pruneCheckTimeNs / 1_000_000d)
            + " | branchMs=" + MathMan.format(metrics.branchTimeNs / 1_000_000d)
            + " | enqueueMs=" + MathMan.format(metrics.enqueueTimeNs / 1_000_000d)
            + " | reprioritizeMs=" + MathMan.format(metrics.reprioritizeTimeNs / 1_000_000d)
            + " | timeoutChecks=" + metrics.timeoutChecks
            + " | timeoutAdjustments=" + metrics.timeoutAdjustments
            + " | reprioritizeMode=" + metrics.reprioritizeMode
            + " | schedulerMode=" + metrics.schedulerMode);
        return max;
    }

    private static double legacyNextFactor(double currentFactor) {
        double next = currentFactor + 0.01;
        if (next >= 0.1) {
            next += 0.1;
        }
        if (next >= 0.5) {
            next += 0.2;
        }
        return next;
    }

    private static void updateConvergenceCheckpoints(SearchMetrics metrics, long timeoutMs, long elapsedMs, double bestScore) {
        if (timeoutMs <= 0 || bestScore == Double.NEGATIVE_INFINITY) {
            return;
        }
        if (metrics.checkpoint25Score == Double.NEGATIVE_INFINITY && elapsedMs * 4L >= timeoutMs) {
            metrics.checkpoint25Score = bestScore;
        }
        if (metrics.checkpoint50Score == Double.NEGATIVE_INFINITY && elapsedMs * 2L >= timeoutMs) {
            metrics.checkpoint50Score = bestScore;
        }
        if (metrics.checkpoint75Score == Double.NEGATIVE_INFINITY && elapsedMs * 4L >= timeoutMs * 3L) {
            metrics.checkpoint75Score = bestScore;
        }
        if (metrics.checkpoint100Score == Double.NEGATIVE_INFINITY && elapsedMs >= timeoutMs) {
            metrics.checkpoint100Score = bestScore;
        }
    }

    private static double computeImprovementSlope(double[] recentBest, int recentBestCount, int recentBestIndex) {
        if (recentBestCount < 2) {
            return 0d;
        }
        int newestIndex = (recentBestIndex - 1 + recentBest.length) % recentBest.length;
        int oldestIndex = recentBestCount < recentBest.length ? 0 : recentBestIndex;
        double oldest = recentBest[oldestIndex];
        double newest = recentBest[newestIndex];
        return (newest - oldest) / Math.max(1, recentBestCount - 1);
    }

    private record AdaptiveSchedulePolicy(double baseStep,
                                          int slopeWindow,
                                          double minImprovementEpsilon,
                                          long graceDelayMs,
                                          int stagnationWindow,
                                          double pressureThreshold,
                                          double maxStep) {
        static AdaptiveSchedulePolicy resolve() {
            double baseStep = parsePositiveDoubleOrDefault(readStringPropertyOrEnv(SCHEDULE_BASE_STEP_PROPERTY, SCHEDULE_BASE_STEP_ENV), 0.03d);
            int slopeWindow = (int) parsePositiveLongOrDefault(readStringPropertyOrEnv(SCHEDULE_SLOPE_WINDOW_PROPERTY, SCHEDULE_SLOPE_WINDOW_ENV), 8L);
            double minImprovementEpsilon = parsePositiveDoubleOrDefault(readStringPropertyOrEnv(SCHEDULE_MIN_IMPROVEMENT_EPS_PROPERTY, SCHEDULE_MIN_IMPROVEMENT_EPS_ENV), 1e-6d);
            long graceDelayMs = parsePositiveLongOrDefault(readStringPropertyOrEnv(SCHEDULE_GRACE_DELAY_PROPERTY, SCHEDULE_GRACE_DELAY_ENV), 2500L);
            int stagnationWindow = (int) parsePositiveLongOrDefault(readStringPropertyOrEnv(SCHEDULE_STAGNATION_WINDOW_PROPERTY, SCHEDULE_STAGNATION_WINDOW_ENV), 3L);
            double pressureThreshold = parsePositiveDoubleOrDefault(readStringPropertyOrEnv(SCHEDULE_PRESSURE_THRESHOLD_PROPERTY, SCHEDULE_PRESSURE_THRESHOLD_ENV), 1.08d);
            double maxStep = parsePositiveDoubleOrDefault(readStringPropertyOrEnv(SCHEDULE_MAX_STEP_PROPERTY, SCHEDULE_MAX_STEP_ENV), 0.18d);
            return new AdaptiveSchedulePolicy(baseStep, slopeWindow, minImprovementEpsilon, graceDelayMs, Math.max(1, stagnationWindow), pressureThreshold, maxStep);
        }

        double nextFactor(double currentFactor,
                          long timeoutMs,
                          long elapsedMs,
                          int queueSize,
                          int previousQueueSize,
                          double improvementSlope,
                          int stagnationWindows) {
            if (timeoutMs <= 0) {
                return currentFactor + baseStep;
            }
            double elapsedRatio = Math.min(2d, Math.max(0d, elapsedMs / (double) timeoutMs));
            double queueGrowthRatio = previousQueueSize <= 0 ? 1d : (queueSize / (double) Math.max(1, previousQueueSize));
            double absSlope = Math.abs(improvementSlope);
            boolean improvementResumed = absSlope >= minImprovementEpsilon;
            boolean sustainedStagnation = stagnationWindows >= stagnationWindow;
            boolean pressureHigh = queueGrowthRatio >= pressureThreshold;

            double step = baseStep * 0.35d;
            if (sustainedStagnation || pressureHigh || elapsedRatio >= 0.9d) {
                double elapsedNorm = Math.min(1d, Math.max(0d, (elapsedRatio - 0.5d) / 0.7d));
                double pressureNorm = Math.min(1d, Math.max(0d, (queueGrowthRatio - 1d) / Math.max(0.01d, pressureThreshold - 1d)));
                double stagnationNorm = Math.min(1d, stagnationWindows / (double) Math.max(1, stagnationWindow));
                double progress = (elapsedNorm * 0.45d) + (pressureNorm * 0.30d) + (stagnationNorm * 0.25d);
                step = baseStep * (0.8d + progress * 2.4d);
            }

            if (improvementResumed) {
                step *= 0.65d;
            }

            step = Math.max(baseStep * 0.15d, Math.min(step, maxStep));
            return currentFactor + step;
        }
    }

    private double upperBound(T node, double exactScore, SearchMetrics metrics) {
        return invokeBoundFunction(upperBoundFunction, node, exactScore, metrics);
    }

    private double invokeBoundFunction(ToDoubleFunction<T> function, T node, double exactScore, SearchMetrics metrics) {
        boolean profileEnabled = metrics.profileEnabled;
        long t0 = profileEnabled ? System.nanoTime() : 0L;
        if (profileEnabled) {
            metrics.upperBoundCalls++;
        }
        if (function == null) {
            if (profileEnabled) {
                metrics.upperBoundTimeNs += System.nanoTime() - t0;
            }
            return Double.POSITIVE_INFINITY;
        }
        double upper = function.applyAsDouble(node);
        if (Double.isNaN(upper)) {
            if (profileEnabled) {
                metrics.upperBoundTimeNs += System.nanoTime() - t0;
            }
            return Double.POSITIVE_INFINITY;
        }
        if (profileEnabled) {
            metrics.upperBoundTimeNs += System.nanoTime() - t0;
        }
        return Math.max(exactScore, upper);
    }

    private static boolean isProfilingEnabled() {
        String propertyValue = System.getProperty(PROFILE_PROPERTY);
        if (propertyValue != null) {
            return Boolean.parseBoolean(propertyValue);
        }
        String envValue = System.getenv(PROFILE_ENV);
        if (envValue != null) {
            return Boolean.parseBoolean(envValue);
        }
        return true;
    }

    private static boolean isBoundPruningEnabled() {
        String propertyValue = System.getProperty(PRUNING_PROPERTY);
        if (propertyValue != null) {
            return Boolean.parseBoolean(propertyValue);
        }
        String envValue = System.getenv(PRUNING_ENV);
        if (envValue != null) {
            return Boolean.parseBoolean(envValue);
        }
        return true;
    }

    private static boolean isStrictTimeoutEnabled() {
        String propertyValue = System.getProperty(STRICT_TIMEOUT_PROPERTY);
        if (propertyValue != null) {
            return Boolean.parseBoolean(propertyValue);
        }
        String envValue = System.getenv(STRICT_TIMEOUT_ENV);
        if (envValue != null) {
            return Boolean.parseBoolean(envValue);
        }
        return false;
    }

    private static long resolveMaxTimeoutMs() {
        String propertyValue = System.getProperty(MAX_TIMEOUT_PROPERTY);
        if (propertyValue != null) {
            return parsePositiveLongOrDefault(propertyValue, DEFAULT_MAX_TIMEOUT_MS);
        }
        String envValue = System.getenv(MAX_TIMEOUT_ENV);
        if (envValue != null) {
            return parsePositiveLongOrDefault(envValue, DEFAULT_MAX_TIMEOUT_MS);
        }
        return DEFAULT_MAX_TIMEOUT_MS;
    }

    private static long parsePositiveLongOrDefault(String raw, long fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            long parsed = Long.parseLong(raw.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double parsePositiveDoubleOrDefault(String raw, double fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(raw.trim());
            return parsed > 0d ? parsed : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String readStringPropertyOrEnv(String propertyKey, String envKey) {
        String propertyValue = System.getProperty(propertyKey);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return null;
    }

    private static double aggressivePruneThreshold(double bestScore, SearchStage stage) {
        if (bestScore == Double.NEGATIVE_INFINITY) {
            return Double.NEGATIVE_INFINITY;
        }
        if (stage == SearchStage.EXACT) {
            return bestScore;
        }
        return bestScore + Math.max(1e-9, Math.abs(bestScore) * 0.0005);
    }
}