package com.pulsekeep.ui;

import com.pulsekeep.model.AppState;
import com.pulsekeep.service.CaffeinateService;
import com.pulsekeep.service.SchedulerService;
import com.pulsekeep.util.Logger;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.Objects;

/**
 * Primary JavaFX window for PulseKeep.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Render a minimal dark-themed window with start/stop controls.</li>
 *   <li>Display the current {@link AppState} with a colour-coded indicator.</li>
 *   <li>Show the upcoming nudge interval in a status line.</li>
 *   <li>Display a scrolling real-time activity log fed by {@link Logger}.</li>
 *   <li>Delegate start/stop actions to the service layer.</li>
 * </ul>
 *
 * <p>This class is constructed and used exclusively on the JavaFX Application
 * Thread. Public methods that modify UI nodes call {@link Platform#runLater}
 * internally when invoked from other threads (e.g. the tray AWT thread).
 */
public class MainView {

    // ── Layout constants ─────────────────────────────────────────────────────

    private static final int WINDOW_WIDTH   = 500;
    private static final int WINDOW_HEIGHT  = 580;
    private static final int PADDING        = 24;
    private static final int ROOT_SPACING   = 16;
    private static final int BUTTON_SPACING = 12;

    // ── Service dependencies ─────────────────────────────────────────────────

    private final CaffeinateService caffeinateService;
    private final SchedulerService  schedulerService;

    // ── Optional tray integration (set after construction) ───────────────────

    private TrayManager trayManager;

    // ── Mutable UI nodes ─────────────────────────────────────────────────────

    private Label    statusLabel;
    private Label    intervalLabel;
    private TextArea logArea;
    private Button   startButton;
    private Button   stopButton;

    // ── Application state ─────────────────────────────────────────────────────

    private AppState currentState = AppState.STOPPED;

    // ── Constructor ───────────────────────────────────────────────────────────

    public MainView(CaffeinateService caffeinateService, SchedulerService schedulerService) {
        this.caffeinateService = caffeinateService;
        this.schedulerService  = schedulerService;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Injects the {@link TrayManager} after construction to break the
     * circular dependency (MainView ↔ TrayManager). Safe to call before
     * or after {@link #show}.
     */
    public void setTrayManager(TrayManager trayManager) {
        this.trayManager = trayManager;
    }

    /**
     * Builds the scene graph, applies the stylesheet, and shows the window.
     * Must be called on the JavaFX Application Thread.
     *
     * @param stage the primary {@link Stage} provided by JavaFX
     */
    public void show(Stage stage) {
        stage.setTitle("PulseKeep");
        stage.setWidth(WINDOW_WIDTH);
        stage.setHeight(WINDOW_HEIGHT);
        stage.setResizable(false);

        VBox root  = buildRoot();
        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);

        // Load external dark-theme stylesheet from the classpath root
        String cssUri = Objects.requireNonNull(
                getClass().getResource("/styles.css"),
                "styles.css not found on classpath"
        ).toExternalForm();
        scene.getStylesheets().add(cssUri);

        stage.setScene(scene);
        stage.show();

        // Register Logger UI callback: append each line to the log TextArea
        Logger.setUiConsumer(line ->
                Platform.runLater(() -> {
                    logArea.appendText(line + "\n");
                    logArea.setScrollTop(Double.MAX_VALUE);
                })
        );

        // Receive interval updates from the scheduler and show them in the UI
        schedulerService.setIntervalUpdateCallback(interval ->
                Platform.runLater(() -> intervalLabel.setText("Next nudge in: " + interval))
        );
    }

    /**
     * Starts caffeinate and the nudge scheduler.
     * Safe to call from any thread (e.g. the AWT system-tray thread).
     */
    public void handleStart() {
        caffeinateService.start();
        schedulerService.start();
        updateState(AppState.RUNNING);
    }

    /**
     * Stops the nudge scheduler and kills caffeinate.
     * Safe to call from any thread.
     */
    public void handleStop() {
        schedulerService.stop();
        caffeinateService.stop();
        updateState(AppState.STOPPED);
    }

    /**
     * Returns the current {@link AppState}. Thread-safe read.
     */
    public AppState getCurrentState() {
        return currentState;
    }

    // ── Scene graph construction ──────────────────────────────────────────────

    private VBox buildRoot() {
        VBox root = new VBox(ROOT_SPACING);
        root.setPadding(new Insets(PADDING));
        root.getStyleClass().add("root-container");

        Label titleLabel = new Label("PulseKeep");
        titleLabel.getStyleClass().add("title-label");

        Label subtitleLabel = new Label("Keep your Mac awake & presence active");
        subtitleLabel.getStyleClass().add("subtitle-label");

        HBox statusBox = buildStatusBox();

        intervalLabel = new Label("Next nudge in: --");
        intervalLabel.getStyleClass().add("interval-label");

        HBox buttonBox = buildButtonBox();

        VBox logSection = buildLogSection();
        VBox.setVgrow(logSection, Priority.ALWAYS);

        root.getChildren().addAll(
                titleLabel,
                subtitleLabel,
                styledSeparator(),
                statusBox,
                intervalLabel,
                buttonBox,
                styledSeparator(),
                logSection
        );

        return root;
    }

    private HBox buildStatusBox() {
        Label titleLabel = new Label("Status:");
        titleLabel.getStyleClass().add("status-title");

        statusLabel = new Label("● STOPPED");
        statusLabel.getStyleClass().addAll("status-label", "status-stopped");

        HBox box = new HBox(BUTTON_SPACING);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().addAll(titleLabel, statusLabel);
        return box;
    }

    private HBox buildButtonBox() {
        startButton = new Button("▶  Start");
        stopButton  = new Button("■  Stop");

        startButton.getStyleClass().addAll("action-button", "start-button");
        stopButton.getStyleClass().addAll("action-button", "stop-button");

        // App starts in STOPPED state – stop button is disabled initially
        stopButton.setDisable(true);

        startButton.setOnAction(e -> handleStart());
        stopButton.setOnAction(e -> handleStop());

        HBox box = new HBox(BUTTON_SPACING);
        box.getChildren().addAll(startButton, stopButton);
        return box;
    }

    private VBox buildLogSection() {
        Label logTitle = new Label("ACTIVITY LOG");
        logTitle.getStyleClass().add("log-title");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.getStyleClass().add("log-area");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        VBox section = new VBox(8, logTitle, logArea);
        VBox.setVgrow(section, Priority.ALWAYS);
        return section;
    }

    // ── State updates ─────────────────────────────────────────────────────────

    /**
     * Transitions the UI to reflect {@code newState}.
     * May be called from any thread; all UI mutations are dispatched via
     * {@link Platform#runLater}.
     *
     * @param newState the target state
     */
    public void updateState(AppState newState) {
        this.currentState = newState;

        Platform.runLater(() -> {
            boolean running = (newState == AppState.RUNNING);

            // Update status indicator text and colour
            statusLabel.setText("● " + newState.getDisplayName());
            statusLabel.getStyleClass().removeAll("status-running", "status-stopped");
            statusLabel.getStyleClass().add(running ? "status-running" : "status-stopped");

            // Toggle button enable state
            startButton.setDisable(running);
            stopButton.setDisable(!running);

            // Clear interval label when stopped
            if (!running) {
                intervalLabel.setText("Next nudge in: --");
            }

            // Keep tray menu item label in sync
            if (trayManager != null) {
                trayManager.updateTrayState(newState);
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Creates a styled horizontal rule. */
    private static Separator styledSeparator() {
        Separator sep = new Separator();
        sep.getStyleClass().add("custom-separator");
        return sep;
    }
}
