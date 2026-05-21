package com.pulsekeep;

/**
 * Non-Application launcher class required for jpackage and fat-JAR execution.
 *
 * <p>When JavaFX detects that the main class extends {@link javafx.application.Application},
 * it enforces module-system checks that fail on a non-modular classpath (e.g. shaded JAR
 * or jpackage runtime). This wrapper sidesteps the issue by providing a plain {@code main}
 * that delegates to {@link App#main}.
 */
public class Launcher {
    public static void main(String[] args) {
        App.main(args);
    }
}
