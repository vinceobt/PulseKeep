package com.pulsekeep.service;

import com.pulsekeep.util.Logger;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Background scheduler that triggers mouse nudges at randomized intervals.
 *
 * <h3>Interval strategy</h3>
 * After each nudge a new random delay is drawn from
 * [{@value #MIN_INTERVAL_MINUTES}, {@value #MAX_INTERVAL_MINUTES}] minutes
 * so the pattern cannot be predicted by monitoring software.
 *
 * <h3>Threading</h3>
 * Backed by a single daemon thread so the JVM can exit cleanly without an
 * explicit {@link #shutdown()} call, though {@link #shutdown()} should still
 * be invoked during graceful shutdown to flush resources immediately.
 *
 * <p>Distinguish between two lifecycle operations:
 * <ul>
 *   <li>{@link #stop()} – cancel the pending nudge, keep the executor alive
 *       (the scheduler can be restarted later).</li>
 *   <li>{@link #shutdown()} – terminate the executor permanently; call only
 *       on application exit.</li>
 * </ul>
 */
public class SchedulerService {

    // ── Configuration constants ───────────────────────────────────────────────

    /** Minimum delay between nudges (inclusive), in minutes. */
    public static final int MIN_INTERVAL_MINUTES = 2;

    /** Maximum delay between nudges (inclusive), in minutes. */
    public static final int MAX_INTERVAL_MINUTES = 5;

    // ── Dependencies ─────────────────────────────────────────────────────────

    /**
     * May be {@code null} if the Robot could not be initialised (e.g. missing
     * Accessibility permissions). The scheduler will still run but skip nudges.
     */
    private final MouseMovementService mouseMovementService;

    private final Random random = new Random();

    // ── Executor ─────────────────────────────────────────────────────────────

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread t = new Thread(runnable, "PulseKeep-Scheduler");
                // Daemon thread: the JVM will not wait for it on exit
                t.setDaemon(true);
                return t;
            });

    // ── Runtime state ─────────────────────────────────────────────────────────

    /** Handle to the currently pending scheduled task; {@code null} when stopped. */
    private ScheduledFuture<?> pendingTask;

    /**
     * Optional UI callback – receives a human-readable interval string
     * (e.g. {@code "3 min"}) every time a new nudge is scheduled.
     * Called from the scheduler thread; implementations must dispatch to
     * the UI thread themselves (e.g. via {@code Platform.runLater}).
     */
    private Consumer<String> intervalUpdateCallback;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * @param mouseMovementService may be {@code null}; nudges are skipped
     *                             when {@code null} but the scheduler still runs.
     */
    public SchedulerService(MouseMovementService mouseMovementService) {
        this.mouseMovementService = mouseMovementService;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Registers a callback that is notified with the next interval label
     * each time a nudge is scheduled.
     *
     * @param callback consumer that receives strings like {@code "4 min"}
     */
    public void setIntervalUpdateCallback(Consumer<String> callback) {
        this.intervalUpdateCallback = callback;
    }

    /**
     * Starts the scheduler. The first nudge will fire after a random initial
     * interval; subsequent nudges each use a freshly drawn random interval.
     *
     * <p>Calling {@code start()} while already running has no effect.
     */
    public synchronized void start() {
        if (pendingTask != null && !pendingTask.isDone()) {
            return; // already running
        }
        scheduleNextNudge();
        Logger.log("Scheduler started");
    }

    /**
     * Cancels the currently pending nudge without shutting down the executor.
     * Calling {@code start()} after {@code stop()} restarts the cycle.
     */
    public synchronized void stop() {
        if (pendingTask != null) {
            pendingTask.cancel(false);
            pendingTask = null;
        }
        Logger.log("Scheduler stopped");
    }

    /**
     * Permanently shuts down the executor. Must be called only during
     * application exit. After this, {@code start()} will have no effect.
     */
    public void shutdown() {
        executor.shutdownNow();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Schedules the next nudge after a freshly randomised interval, then
     * reschedules itself recursively after the nudge executes.
     */
    private void scheduleNextNudge() {
        int intervalMinutes = MIN_INTERVAL_MINUTES
                + random.nextInt(MAX_INTERVAL_MINUTES - MIN_INTERVAL_MINUTES + 1);

        // Notify the UI of the upcoming interval
        Consumer<String> callback = intervalUpdateCallback;
        if (callback != null) {
            callback.accept(intervalMinutes + " min");
        }

        pendingTask = executor.schedule(() -> {
            performNudge();
            // Recursively schedule the next cycle with a new random interval
            synchronized (SchedulerService.this) {
                if (!executor.isShutdown()) {
                    scheduleNextNudge();
                }
            }
        }, intervalMinutes, TimeUnit.MINUTES);
    }

    /**
     * Executes the mouse nudge, or logs a skip if the Robot is unavailable.
     */
    private void performNudge() {
        if (mouseMovementService != null) {
            mouseMovementService.nudge();
        } else {
            Logger.log("Mouse nudge skipped – Accessibility permissions may be required");
        }
    }
}
