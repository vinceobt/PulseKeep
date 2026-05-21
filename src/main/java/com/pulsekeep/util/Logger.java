package com.pulsekeep.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Lightweight timestamped logger for PulseKeep.
 *
 * <p>All messages are written to {@code stdout} using the format:
 * <pre>[HH:mm:ss] message</pre>
 *
 * <p>Optionally, a UI consumer can be registered via {@link #setUiConsumer(Consumer)}
 * so that log messages are also appended to the JavaFX log area without
 * redirecting {@code System.out} (which would break other tooling).
 */
public final class Logger {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Optional callback invoked on every log call.
     * Typically set once by {@code MainView} to push messages into the UI.
     * Volatile so that changes are visible across threads immediately.
     */
    private static volatile Consumer<String> uiConsumer;

    // Utility class – prevent instantiation
    private Logger() {}

    /**
     * Registers a callback that receives every formatted log line.
     * Must be called from the JavaFX Application thread; callbacks are
     * invoked on whatever thread calls {@link #log(String)}, so the
     * consumer itself is responsible for dispatching to the UI thread.
     *
     * @param consumer a {@link Consumer} that accepts formatted log strings
     */
    public static void setUiConsumer(Consumer<String> consumer) {
        uiConsumer = consumer;
    }

    /**
     * Logs {@code message} with a wall-clock timestamp prefix.
     *
     * <p>Thread-safe: may be called from any thread.
     *
     * @param message the message to log (must not be {@code null})
     */
    public static void log(String message) {
        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        String formatted  = "[" + timestamp + "] " + message;

        // Always write to stdout so logs are visible in the terminal / IDE console
        System.out.println(formatted);

        // Optionally push to the UI log area
        Consumer<String> consumer = uiConsumer;
        if (consumer != null) {
            consumer.accept(formatted);
        }
    }
}
