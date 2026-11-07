package com.pulsekeep.service;

import com.pulsekeep.util.Logger;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Counts down a user-chosen duration and invokes a callback when it elapses.
 *
 * <p>Runs on its own daemon thread so it never blocks JVM exit. All public
 * methods are thread-safe.
 */
public class AutoShutdownService {

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "PulseKeep-AutoShutdown");
                t.setDaemon(true);
                return t;
            });

    private ScheduledFuture<?> expiryTask;
    private ScheduledFuture<?> tickTask;
    private long deadlineNanos;

    /**
     * Arms the timer. Does nothing if {@code duration} is null, zero or negative.
     * Any previously armed timer is cancelled first.
     *
     * @param duration time until {@code onExpire} runs
     * @param onExpire invoked once on the timer thread when time is up
     * @param onTick   invoked every second with the remaining time as {@code H:MM:SS}
     */
    public synchronized void start(Duration duration, Runnable onExpire, Consumer<String> onTick) {
        stop();
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return;
        }

        long totalNanos = duration.toNanos();
        deadlineNanos = System.nanoTime() + totalNanos;

        expiryTask = executor.schedule(() -> {
            Logger.log("Auto-shutdown timer expired");
            onExpire.run();
        }, totalNanos, TimeUnit.NANOSECONDS);

        tickTask = executor.scheduleAtFixedRate(() -> {
            long remainingSecs = Math.max(0,
                    TimeUnit.NANOSECONDS.toSeconds(deadlineNanos - System.nanoTime() + 999_999_999L));
            onTick.accept(format(remainingSecs));
        }, 0, 1, TimeUnit.SECONDS);

        Logger.log("Auto-shutdown armed for " + format(duration.getSeconds()));
    }

    /** Cancels a pending timer, if any. */
    public synchronized void stop() {
        if (expiryTask != null) {
            expiryTask.cancel(false);
            expiryTask = null;
        }
        if (tickTask != null) {
            tickTask.cancel(false);
            tickTask = null;
        }
    }

    /** Permanently releases the timer thread. */
    public synchronized void shutdown() {
        stop();
        executor.shutdownNow();
    }

    private static String format(long totalSeconds) {
        return String.format("%d:%02d:%02d",
                totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60);
    }
}
