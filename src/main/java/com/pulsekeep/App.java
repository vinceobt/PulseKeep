package com.pulsekeep;

import com.pulsekeep.service.CaffeinateService;
import com.pulsekeep.service.MouseMovementService;
import com.pulsekeep.service.SchedulerService;
import com.pulsekeep.ui.MainView;
import com.pulsekeep.ui.TrayManager;
import com.pulsekeep.util.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.awt.*;

/**
 * PulseKeep – application entry point.
 *
 * <p>Bootstraps all services, wires them together, shows the primary window,
 * and registers both a JavaFX lifecycle callback and a JVM shutdown hook for
 * graceful cleanup.
 *
 * <h3>Startup sequence</h3>
 * <ol>
 *   <li>{@link #main} forces AWT toolkit initialisation <em>before</em>
 *       JavaFX launches. On macOS this prevents a race condition between the
 *       two UI toolkits.</li>
 *   <li>{@link #init} constructs all services (off the JavaFX thread).</li>
 *   <li>{@link #start} builds the UI, wires callbacks, and attaches the
 *       system tray if the platform supports it.</li>
 * </ol>
 *
 * <h3>Shutdown sequence</h3>
 * {@link #stop} is guaranteed to run when {@link Platform#exit()} is called.
 * The JVM shutdown hook handles SIGTERM / abnormal terminations.
 */
public class App extends Application {

    // ── Service fields (populated in init()) ─────────────────────────────────

    private CaffeinateService  caffeinateService;
    private SchedulerService   schedulerService;

    // ── UI fields (populated in start()) ─────────────────────────────────────

    private MainView    mainView;
    private TrayManager trayManager;

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) {
        /*
         * Force AWT initialisation before JavaFX takes over the main thread.
         * Required on macOS for java.awt.SystemTray and java.awt.Robot to
         * coexist with JavaFX without a NullPointerException in the toolkit.
         */
        Toolkit.getDefaultToolkit();

        launch(args);
    }

    // ── JavaFX Application lifecycle ──────────────────────────────────────────

    /**
     * Called by the JavaFX launcher on a background thread before {@link #start}.
     * Construct services here so the UI thread is not blocked during initialisation.
     */
    @Override
    public void init() {
        caffeinateService = new CaffeinateService();

        MouseMovementService mouseMovementService = null;
        try {
            mouseMovementService = new MouseMovementService();
        } catch (AWTException e) {
            // Robot is unavailable (e.g. missing Accessibility permissions).
            // PulseKeep will still run – mouse nudges will be skipped.
            Logger.log("Mouse movement unavailable: " + e.getMessage());
            Logger.log("Tip: grant Accessibility access in System Settings → Privacy & Security");
        }

        schedulerService = new SchedulerService(mouseMovementService);
    }

    /**
     * Called by the JavaFX launcher on the JavaFX Application Thread.
     * Builds the UI and attaches the system tray integration.
     */
    @Override
    public void start(Stage primaryStage) {
        /*
         * Keep the application alive even when all windows are hidden
         * (needed for the system-tray / menu-bar workflow).
         */
        Platform.setImplicitExit(false);

        // Build and show the main window
        mainView = new MainView(caffeinateService, schedulerService);
        mainView.show(primaryStage);

        // System tray integration (graceful degradation when unsupported)
        if (SystemTray.isSupported()) {
            trayManager = new TrayManager(primaryStage, mainView);
            trayManager.initialize();
            mainView.setTrayManager(trayManager);

            // Override the window-close button: hide to tray instead of exiting
            primaryStage.setOnCloseRequest(event -> {
                event.consume();
                trayManager.minimizeToTray();
            });
        }

        // JVM shutdown hook – safety net for SIGTERM / abnormal exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            schedulerService.shutdown();
            caffeinateService.stop();
            Logger.log("PulseKeep shutdown complete");
        }, "PulseKeep-ShutdownHook"));

        Logger.log("PulseKeep started");
    }

    /**
     * Called by JavaFX when {@link Platform#exit()} is invoked (normal exit).
     * Cleans up all resources before the JVM terminates.
     */
    @Override
    public void stop() {
        schedulerService.shutdown();
        caffeinateService.stop();

        if (trayManager != null) {
            trayManager.removeTrayIcon();
        }

        Logger.log("PulseKeep stopped");
    }
}
