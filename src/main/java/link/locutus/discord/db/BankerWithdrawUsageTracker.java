package link.locutus.discord.db;

import java.util.concurrent.TimeUnit;

public final class BankerWithdrawUsageTracker {
    public static final long DEFAULT_INTERVAL_MS = TimeUnit.DAYS.toMillis(1);

    private BankerWithdrawUsageTracker() {
    }

    public static long normalizeInterval(Long intervalMs) {
        return intervalMs == null || intervalMs <= 0 ? DEFAULT_INTERVAL_MS : intervalMs;
    }

    public static long currentResetAt(long now, long intervalMs) {
        long normalizedInterval = normalizeInterval(intervalMs);
        return ((now / normalizedInterval) + 1) * normalizedInterval;
    }

    public record StoredUsage(double usedValue, long resetAt, long dateUpdated) {
    }

    public record UsageSnapshot(double usedValue, long resetAt, long intervalMs, long dateUpdated) {
    }

    public static final class Reservation {
        private final double amount;
        private final long reservedResetAt;
        private boolean active = true;

        private Reservation(double amount, long reservedResetAt) {
            this.amount = amount;
            this.reservedResetAt = reservedResetAt;
        }
    }

    public static final class State {
        private double usedValue;
        private long resetAt;
        private long dateUpdated;

        public State(StoredUsage storedUsage) {
            if (storedUsage != null) {
                this.usedValue = Math.max(0D, storedUsage.usedValue());
                this.resetAt = storedUsage.resetAt();
                this.dateUpdated = storedUsage.dateUpdated();
            }
        }

        public synchronized UsageSnapshot snapshot(long now, long intervalMs) {
            long normalizedInterval = normalizeInterval(intervalMs);
            refresh(now, normalizedInterval);
            return new UsageSnapshot(usedValue, resetAt, normalizedInterval, dateUpdated);
        }

        public synchronized Reservation tryReserve(double amount, double limit, long now, long intervalMs) {
            long normalizedInterval = normalizeInterval(intervalMs);
            refresh(now, normalizedInterval);
            if (usedValue + amount > limit) {
                return null;
            }
            usedValue += amount;
            dateUpdated = nextDateUpdated(now);
            return new Reservation(amount, resetAt);
        }

        public synchronized UsageSnapshot rollback(Reservation reservation, long now, long intervalMs) {
            long normalizedInterval = normalizeInterval(intervalMs);
            refresh(now, normalizedInterval);
            if (reservation != null && reservation.active) {
                reservation.active = false;
                if (reservation.reservedResetAt == resetAt) {
                    double newValue = Math.max(0D, usedValue - reservation.amount);
                    if (Double.compare(newValue, usedValue) != 0) {
                        usedValue = newValue;
                        dateUpdated = nextDateUpdated(now);
                    }
                }
            }
            return new UsageSnapshot(usedValue, resetAt, normalizedInterval, dateUpdated);
        }

        private void refresh(long now, long intervalMs) {
            long currentResetAt = currentResetAt(now, intervalMs);
            if (resetAt != currentResetAt) {
                usedValue = 0D;
                resetAt = currentResetAt;
            }
        }

        private long nextDateUpdated(long now) {
            return Math.max(now, dateUpdated + 1);
        }
    }
}
