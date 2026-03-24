package com.gymsystem.checkin;

/** Lifecycle states for a check-in record. */
public enum CheckinStatus {
    /** Waiting for provider callback (Gympass/TotalPass). */
    STARTED,
    /** Confirmed by provider or completed via direct check-in. */
    COMPLETED,
    /** Provider rejected or an unexpected error occurred. */
    FAILED
}
