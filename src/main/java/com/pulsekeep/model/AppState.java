package com.pulsekeep.model;

/**
 * Represents every valid operational state for PulseKeep.
 *
 * Keeping state as an enum (rather than a boolean) makes it easy to add
 * additional states (e.g. PAUSED) in the future without changing call-sites.
 */
public enum AppState {

    RUNNING("RUNNING"),
    STOPPED("STOPPED");

    /** Human-readable label shown in the UI status indicator. */
    private final String displayName;

    AppState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
