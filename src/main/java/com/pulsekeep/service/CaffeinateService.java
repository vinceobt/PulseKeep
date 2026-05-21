package com.pulsekeep.service;

import com.pulsekeep.util.Logger;

import java.io.IOException;

/**
 * Manages the macOS {@code caffeinate} process to prevent system sleep.
 *
 * <h3>Flags used: {@code caffeinate -dimsu}</h3>
 * <ul>
 *   <li>{@code -d} – Prevent display sleep</li>
 *   <li>{@code -i} – Prevent idle sleep</li>
 *   <li>{@code -m} – Prevent disk idle sleep</li>
 *   <li>{@code -s} – Prevent system sleep (AC power only)</li>
 *   <li>{@code -u} – Declare user activity, delaying idle sleep</li>
 * </ul>
 *
 * <p>The process is started with {@link ProcessBuilder} and its reference is
 * stored so it can be cleanly destroyed on shutdown.
 *
 * <p>All public methods are safe to call from any thread.
 */
public class CaffeinateService {

    // ── Constants ────────────────────────────────────────────────────────────

    private static final String CAFFEINATE_BINARY = "caffeinate";
    private static final String CAFFEINATE_FLAGS  = "-dimsu";

    // ── State ─────────────────────────────────────────────────────────────────

    /** Reference to the running caffeinate OS process; {@code null} when stopped. */
    private Process caffeinateProcess;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Starts the {@code caffeinate} process.
     * If a process is already alive this method is a no-op.
     */
    public synchronized void start() {
        if (isRunning()) {
            Logger.log("caffeinate already running – skipping start");
            return;
        }

        try {
            ProcessBuilder builder = new ProcessBuilder(CAFFEINATE_BINARY, CAFFEINATE_FLAGS);

            /*
             * Inherit the parent process's stdin/stdout/stderr so that
             * caffeinate can communicate with the OS correctly, and its
             * output does not block on an unread pipe.
             */
            builder.inheritIO();

            caffeinateProcess = builder.start();
            Logger.log("caffeinate active");

        } catch (IOException e) {
            Logger.log("Failed to start caffeinate: " + e.getMessage());
        }
    }

    /**
     * Destroys the {@code caffeinate} process if it is currently running.
     * Calling this when no process is running is a no-op.
     */
    public synchronized void stop() {
        if (caffeinateProcess != null && caffeinateProcess.isAlive()) {
            caffeinateProcess.destroy();
            caffeinateProcess = null;
            Logger.log("caffeinate stopped");
        }
    }

    /**
     * Returns {@code true} if the managed caffeinate process is currently alive.
     */
    public synchronized boolean isRunning() {
        return caffeinateProcess != null && caffeinateProcess.isAlive();
    }
}
