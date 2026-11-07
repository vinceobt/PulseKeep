package com.pulsekeep.ui;

import com.pulsekeep.model.AppState;
import com.pulsekeep.service.AutoShutdownService;
import com.pulsekeep.service.CaffeinateService;
import com.pulsekeep.service.SchedulerService;
import com.pulsekeep.util.Logger;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.time.Duration;
import java.util.Objects;

/**
 * Primary JavaFX window for PulseKeep.
 *
 * <p>The window has three cards: the keep-awake controls, the auto-shutdown
 * timer (independent of whether PulseKeep is currently running), and the
 * activity log.
 */
public class MainView {

    // ── Layout constants ─────────────────────────────────────────────────────

    private static final int WINDOW_WIDTH  = 540;
    private static final int WINDOW_HEIGHT = 780;
    private static final int PADDING       = 24;
    private static final int ROOT_SPACING  = 16;

    // ── Service dependencies ─────────────────────────────────────────────────

    private final CaffeinateService   caffeinateService;
    private final SchedulerService    schedulerService;
    private final AutoShutdownService autoShutdownService;

    private TrayManager trayManager;

    // ── Mutable UI nodes ─────────────────────────────────────────────────────

    private Label     statusPill;
    private Label     intervalLabel;
    private TextArea  logArea;
    private Button    startButton;
    private Button    stopButton;

    private TextField hoursField;
    private TextField minutesField;
    private Button    timerButton;
    private Label     countdownLabel;
    private Label     timerHint;
    private HBox      presetRow;
    private Button    offChip;
    private HBox      inputRow;
    private boolean   timerActive = false;

    private AppState currentState = AppState.STOPPED;

    // ── Constructor ───────────────────────────────────────────────────────────

    public MainView(CaffeinateService caffeinateService, SchedulerService schedulerService,
                    AutoShutdownService autoShutdownService) {
        this.caffeinateService   = caffeinateService;
        this.schedulerService    = schedulerService;
        this.autoShutdownService = autoShutdownService;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void setTrayManager(TrayManager trayManager) {
        this.trayManager = trayManager;
    }

    public void show(Stage stage) {
        stage.setTitle("PulseKeep");
        stage.setWidth(WINDOW_WIDTH);
        stage.setHeight(WINDOW_HEIGHT);
        stage.setResizable(false);

        VBox root = buildRoot();
        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);

        String cssUri = Objects.requireNonNull(
                getClass().getResource("/styles.css"),
                "styles.css not found on classpath"
        ).toExternalForm();
        scene.getStylesheets().add(cssUri);

        stage.setScene(scene);
        stage.show();

        Logger.setUiConsumer(line ->
                Platform.runLater(() -> {
                    logArea.appendText(line + "\n");
                    logArea.setScrollTop(Double.MAX_VALUE);
                })
        );

        schedulerService.setIntervalUpdateCallback(interval ->
                Platform.runLater(() -> intervalLabel.setText("Next nudge in " + interval))
        );
    }

    /** Starts caffeinate and the nudge scheduler. Safe to call from any thread. */
    public void handleStart() {
        caffeinateService.start();
        schedulerService.start();
        updateState(AppState.RUNNING);
    }

    /** Stops the nudge scheduler and kills caffeinate. Safe to call from any thread. */
    public void handleStop() {
        schedulerService.stop();
        caffeinateService.stop();
        updateState(AppState.STOPPED);
    }

    public AppState getCurrentState() {
        return currentState;
    }

    // ── Auto-shutdown timer (independent of start/stop) ───────────────────────

    private void toggleTimer() {
        if (timerActive) {
            cancelTimer();
        } else {
            startTimer();
        }
    }

    private void startTimer() {
        long hours   = parse(hoursField);
        long minutes = parse(minutesField);
        Duration d = Duration.ofHours(hours).plusMinutes(minutes);
        if (d.isZero()) {
            timerHint.setText("Timer is off – pick a preset or enter a time");
            return;
        }

        autoShutdownService.start(
                d,
                this::onAutoShutdownExpired,
                time -> Platform.runLater(() -> {
                    if (timerActive) countdownLabel.setText(time);
                })
        );
        setTimerActive(true);

        // Setting a timer while stopped implies the user wants PulseKeep running
        if (currentState != AppState.RUNNING) {
            Logger.log("Timer set while stopped – starting PulseKeep");
            handleStart();
        }
        countdownLabel.setText(String.format("%d:%02d:%02d", d.toHours(), d.toMinutesPart(), d.toSecondsPart()));
    }

    private void cancelTimer() {
        autoShutdownService.stop();
        setTimerActive(false);
        Logger.log("Auto-shutdown timer cancelled");
    }

    private void setTimerActive(boolean active) {
        timerActive = active;
        timerButton.setText(active ? "Cancel timer" : "Set timer");
        timerButton.getStyleClass().removeAll("primary-button", "danger-button");
        timerButton.getStyleClass().add(active ? "danger-button" : "primary-button");
        // The Off chip stays enabled so the timer can be switched off while running
        presetRow.getChildren().forEach(n -> n.setDisable(active && n != offChip));
        hoursField.setDisable(active);
        minutesField.setDisable(active);
        countdownLabel.setVisible(active);
        countdownLabel.setManaged(active);
        timerHint.setText(active ? "PulseKeep will quit when the timer ends" : "Quit PulseKeep automatically after:");
        if (!active) countdownLabel.setText("");
    }

    /** Called on the timer thread when the countdown reaches zero: stop and quit. */
    private void onAutoShutdownExpired() {
        Platform.runLater(() -> {
            Logger.log("Auto-shutdown: quitting PulseKeep");
            quitApplication();
        });
    }

    /**
     * Stops all activity and terminates the JVM.
     *
     * <p>Deliberately avoids {@code Platform.exit()} / {@code App.stop()}: removing the
     * AWT tray icon from the JavaFX thread can deadlock on macOS and leave the window
     * frozen. {@code System.exit} runs the JVM shutdown hook (scheduler + caffeinate
     * cleanup) and the OS removes the tray icon when the process ends.
     */
    public void quitApplication() {
        handleStop();
        Logger.log("PulseKeep stopped");
        System.exit(0);
    }

    private static long parse(TextField f) {
        String t = f.getText();
        if (t == null || t.isBlank()) return 0;
        try {
            return Math.min(Long.parseLong(t.trim()), 999);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ── Scene graph construction ──────────────────────────────────────────────

    private VBox buildRoot() {
        VBox root = new VBox(ROOT_SPACING);
        root.setPadding(new Insets(PADDING));
        root.getStyleClass().add("root-container");

        VBox logCard = buildLogCard();
        VBox.setVgrow(logCard, Priority.ALWAYS);

        root.getChildren().addAll(buildHeader(), buildControlCard(), buildTimerCard(), logCard);
        return root;
    }

    private HBox buildHeader() {
        Label title = new Label("PulseKeep");
        title.getStyleClass().add("title-label");
        Label subtitle = new Label("Keep your Mac awake & presence active");
        subtitle.getStyleClass().add("subtitle-label");
        VBox texts = new VBox(2, title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusPill = new Label("STOPPED");
        statusPill.getStyleClass().addAll("pill", "pill-stopped");

        HBox header = new HBox(texts, spacer, statusPill);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private VBox buildControlCard() {
        Label heading = cardTitle("KEEP AWAKE");

        intervalLabel = new Label("Next nudge in --");
        intervalLabel.getStyleClass().add("muted-label");

        startButton = new Button("Start");
        stopButton  = new Button("Stop");
        startButton.getStyleClass().addAll("action-button", "start-button");
        stopButton.getStyleClass().addAll("action-button", "stop-button");
        stopButton.setDisable(true);
        startButton.setOnAction(e -> handleStart());
        stopButton.setOnAction(e -> handleStop());
        startButton.setMaxWidth(Double.MAX_VALUE);
        stopButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(startButton, Priority.ALWAYS);
        HBox.setHgrow(stopButton, Priority.ALWAYS);

        HBox buttons = new HBox(12, startButton, stopButton);
        return card(heading, intervalLabel, buttons);
    }

    private VBox buildTimerCard() {
        Label heading = cardTitle("AUTO-SHUTDOWN TIMER");

        timerHint = new Label("Quit PulseKeep automatically after:");
        timerHint.getStyleClass().add("muted-label");

        countdownLabel = new Label("");
        countdownLabel.getStyleClass().add("countdown-label");
        countdownLabel.setVisible(false);
        countdownLabel.setManaged(false);

        // Preset chips fill the custom fields
        presetRow = new HBox(8);
        offChip = new Button("Off");
        offChip.getStyleClass().add("chip");
        offChip.setOnAction(e -> {
            if (timerActive) cancelTimer();
            hoursField.setText("0");
            minutesField.setText("0");
            timerHint.setText("Auto-shutdown is off");
        });
        presetRow.getChildren().addAll(
                offChip,
                chip("15 min", 0, 15), chip("30 min", 0, 30), chip("1 h", 1, 0),
                chip("2 h", 2, 0), chip("4 h", 4, 0), chip("8 h", 8, 0));

        hoursField   = numberField("0");
        minutesField = numberField("30");
        Label h = new Label("h");
        Label m = new Label("min");
        h.getStyleClass().add("muted-label");
        m.getStyleClass().add("muted-label");

        timerButton = new Button("Set timer");
        timerButton.getStyleClass().addAll("action-button", "primary-button");
        timerButton.setOnAction(e -> toggleTimer());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        inputRow = new HBox(8, hoursField, h, minutesField, m, spacer, timerButton);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        return card(heading, timerHint, countdownLabel, presetRow, inputRow);
    }

    private VBox buildLogCard() {
        Label heading = cardTitle("ACTIVITY LOG");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.getStyleClass().add("log-area");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        VBox card = card(heading, logArea);
        return card;
    }

    // ── Small builders ────────────────────────────────────────────────────────

    private static VBox card(javafx.scene.Node... children) {
        VBox box = new VBox(12);
        box.getStyleClass().add("card");
        box.getChildren().addAll(children);
        return box;
    }

    private static Label cardTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("card-title");
        return l;
    }

    private Button chip(String text, int hours, int minutes) {
        Button b = new Button(text);
        b.getStyleClass().add("chip");
        b.setOnAction(e -> {
            hoursField.setText(String.valueOf(hours));
            minutesField.setText(String.valueOf(minutes));
        });
        return b;
    }

    private static TextField numberField(String initial) {
        TextField f = new TextField(initial);
        f.getStyleClass().add("number-field");
        f.setPrefColumnCount(3);
        f.setAlignment(Pos.CENTER);
        f.setTextFormatter(new TextFormatter<String>(c ->
                c.getControlNewText().matches("\\d{0,3}") ? c : null));
        return f;
    }

    // ── State updates ─────────────────────────────────────────────────────────

    /** Transitions the UI to reflect {@code newState}. May be called from any thread. */
    public void updateState(AppState newState) {
        this.currentState = newState;

        Platform.runLater(() -> {
            boolean running = (newState == AppState.RUNNING);

            statusPill.setText(newState.getDisplayName().toUpperCase());
            statusPill.getStyleClass().removeAll("pill-running", "pill-stopped");
            statusPill.getStyleClass().add(running ? "pill-running" : "pill-stopped");

            startButton.setDisable(running);
            stopButton.setDisable(!running);

            if (!running) {
                intervalLabel.setText("Next nudge in --");
            }

            if (trayManager != null) {
                trayManager.updateTrayState(newState);
            }
        });
    }
}
