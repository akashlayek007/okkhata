package com.example.simplekhata.models;

public class SyncStatus {
    public enum State {
        SYNCED,
        SYNCING,
        OFFLINE,
        FAILED,
        LOCAL_ONLY
    }

    private final State state;
    private final String errorMessage;
    private final long lastSyncedAtMillis;

    public SyncStatus(State state, String errorMessage, long lastSyncedAtMillis) {
        this.state = state;
        this.errorMessage = errorMessage;
        this.lastSyncedAtMillis = lastSyncedAtMillis;
    }

    public State getState() {
        return state;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getLastSyncedAtMillis() {
        return lastSyncedAtMillis;
    }
}
