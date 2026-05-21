package com.pulsekeep.ui;

import com.pulsekeep.model.AppState;
import com.pulsekeep.util.Logger;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Manages the macOS system-tray (menu-bar) icon for PulseKeep.
 *
 * <p>Features:
 * <ul>
 *   <li>Adds a small coloured dot to the system tray.</li>
 *   <li>Right-click context menu with Open / Toggle Start-Stop / Quit actions.</li>
 *   <li>Double-click on the tray icon restores the main window.</li>
 *   <li>Hiding the window (via close button) minimises to tray rather than
 *       exiting, when the close-request handler is set in {@code App}.</li>
 * </ul>
 *
 * <h3>Threading</h3>
 * AWT tray events fire on the AWT Event Dispatch Thread. Any code that
 * touches JavaFX nodes must be wrapped in {@link Platform#runLater}.
 * Conversely, {@link #minimizeToTray()} is called from the JavaFX thread
 * and delegates to AWT via normal field access (which is safe after
 * {@link #initialize()} completes).
 */
public class TrayManager {

    // ── Dependencies ─────────────────────────────────────────────────────────

    private final Stage    stage;
    private final MainView mainView;

    // ── AWT tray components ───────────────────────────────────────────────────

    private SystemTray systemTray;
    private TrayIcon   trayIcon;

    /**
     * Menu item whose label changes between "Start PulseKeep" and
     * "Stop PulseKeep" as the application state changes.
     */
    private MenuItem toggleItem;

    // ── Constructor ───────────────────────────────────────────────────────────

    public TrayManager(Stage stage, MainView mainView) {
        this.stage    = stage;
        this.mainView = mainView;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Creates the tray icon and attaches it to the system tray.
     * If the platform does not support a system tray this method logs a
     * warning and returns without side-effects.
     *
     * <p>Must be called from the JavaFX Application Thread (or at least after
     * the AWT toolkit has been initialised in {@code App.main()}).
     */
    public void initialize() {
        if (!SystemTray.isSupported()) {
            Logger.log("System tray not supported on this platform");
            return;
        }

        systemTray = SystemTray.getSystemTray();
        PopupMenu menu = buildPopupMenu();

        trayIcon = new TrayIcon(createTrayIconImage(), "PulseKeep", menu);
        trayIcon.setImageAutoSize(true);

        // Double-click restores the main window
        trayIcon.addActionListener(e -> Platform.runLater(this::showWindow));

        try {
            systemTray.add(trayIcon);
            Logger.log("System tray icon initialised");
        } catch (AWTException e) {
            Logger.log("Failed to add tray icon: " + e.getMessage());
        }
    }

    /**
     * Hides the main window and collapses PulseKeep to the tray.
     * Must be called on the JavaFX Application Thread.
     */
    public void minimizeToTray() {
        stage.hide();
        if (trayIcon != null) {
            trayIcon.displayMessage(
                    "PulseKeep",
                    "Running in the background. Double-click the icon to restore.",
                    TrayIcon.MessageType.INFO
            );
        }
        Logger.log("Minimised to tray");
    }

    /**
     * Restores and focuses the main window.
     * Must be called on the JavaFX Application Thread.
     */
    public void showWindow() {
        stage.show();
        stage.toFront();
        stage.requestFocus();
    }

    /**
     * Synchronises the toggle menu item label with {@code state}.
     * Safe to call from any thread.
     *
     * @param state the new application state
     */
    public void updateTrayState(AppState state) {
        if (toggleItem != null) {
            // MenuItem.setLabel is thread-safe in AWT
            toggleItem.setLabel(
                    state == AppState.RUNNING ? "Stop PulseKeep" : "Start PulseKeep"
            );
        }
    }

    /**
     * Removes the tray icon from the system tray.
     * Should be called during application shutdown.
     */
    public void removeTrayIcon() {
        if (systemTray != null && trayIcon != null) {
            systemTray.remove(trayIcon);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds the right-click popup menu attached to the tray icon.
     */
    private PopupMenu buildPopupMenu() {
        PopupMenu menu = new PopupMenu();

        // "Open" – restores the window
        MenuItem openItem = new MenuItem("Open PulseKeep");
        openItem.addActionListener(e -> Platform.runLater(this::showWindow));

        // "Start / Stop" – toggles the running state
        toggleItem = new MenuItem("Start PulseKeep");
        toggleItem.addActionListener(e -> Platform.runLater(() -> {
            if (mainView.getCurrentState() == AppState.RUNNING) {
                mainView.handleStop();
            } else {
                mainView.handleStart();
            }
        }));

        // "Quit" – stops services and exits cleanly
        MenuItem quitItem = new MenuItem("Quit");
        quitItem.addActionListener(e -> Platform.runLater(() -> {
            mainView.handleStop();
            Platform.exit();
        }));

        menu.add(openItem);
        menu.add(toggleItem);
        menu.addSeparator();
        menu.add(quitItem);

        return menu;
    }

    /**
     * Generates a 16×16 programmatic tray icon using AWT {@link Graphics2D}.
     *
     * <p>A filled green circle with a white outline keeps the icon minimal and
     * readable at menu-bar resolution without requiring an external image file.
     */
    private Image createTrayIconImage() {
        final int SIZE = 16;
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Filled green circle – indicates PulseKeep is present
        g.setColor(new Color(0, 200, 100));
        g.fillOval(1, 1, SIZE - 2, SIZE - 2);

        // Thin white border for contrast on both light and dark menu bars
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.0f));
        g.drawOval(1, 1, SIZE - 2, SIZE - 2);

        g.dispose();
        return image;
    }
}
