package com.pulsekeep.service;

import com.pulsekeep.util.Logger;

import java.awt.*;
import java.util.Random;

/**
 * Performs tiny, imperceptible mouse nudges to simulate user activity.
 *
 * <h3>Strategy</h3>
 * <ol>
 *   <li>Snapshot the current cursor position.</li>
 *   <li>Move the cursor by a random delta of {@code -1}, {@code 0}, or {@code +1}
 *       pixels in both the X and Y axes (at least one axis will be non-zero).</li>
 *   <li>Immediately return the cursor to its original position.</li>
 * </ol>
 *
 * <p>The round-trip happens in the same millisecond window, making the movement
 * invisible to the user while still registering as pointer activity in apps
 * such as Slack, Teams, and Zoom.
 *
 * <p>Requires Accessibility permissions on macOS (System Settings → Privacy &
 * Security → Accessibility). If permissions are absent the constructor will
 * throw an {@link AWTException} which the caller should handle gracefully.
 */
public class MouseMovementService {

    // ── Constants ─────────────────────────────────────────────────────────────

    /** Possible per-axis pixel deltas: move left/up, stay, or move right/down. */
    private static final int[] MOVEMENT_DELTAS = {-1, 0, 1};

    // ── Dependencies ─────────────────────────────────────────────────────────

    /** AWT Robot used to programmatically move the cursor. */
    private final Robot robot;

    /** Source of randomness for selecting pixel deltas. */
    private final Random random;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Creates a new {@code MouseMovementService}.
     *
     * @throws AWTException if a Robot cannot be created, typically because
     *                      the environment is headless or Accessibility
     *                      permissions have not been granted on macOS.
     */
    public MouseMovementService() throws AWTException {
        this.robot  = new Robot();
        this.random = new Random();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Performs a single atomic nudge-and-return cursor operation.
     *
     * <p>Safe to call from any thread; {@link Robot#mouseMove} dispatches
     * the native event synchronously.
     */
    public void nudge() {
        // 1. Capture current position
        Point origin = MouseInfo.getPointerInfo().getLocation();

        // 2. Pick random deltas; guarantee at least one axis moves
        int dx = randomDelta();
        int dy = randomDelta();
        if (dx == 0 && dy == 0) {
            dx = 1;  // ensure we always produce a real event
        }

        // 3. Move to nudged position then immediately snap back
        robot.mouseMove(origin.x + dx, origin.y + dy);
        robot.mouseMove(origin.x,       origin.y);

        Logger.log("Mouse nudged");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Returns a random element from {@link #MOVEMENT_DELTAS}. */
    private int randomDelta() {
        return MOVEMENT_DELTAS[random.nextInt(MOVEMENT_DELTAS.length)];
    }
}
