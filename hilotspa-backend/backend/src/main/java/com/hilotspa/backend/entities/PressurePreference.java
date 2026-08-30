package com.hilotspa.backend.entities;

/**
 * How firm the client wants the treatment - paper-deltas H9.
 *
 * Not a safety flag and not a clinical finding: it is a preference, and it was
 * sharing a free-text field with the safety checklist, which is how "Preferred
 * pressure: Firm" ended up being read by the assistant as something that had
 * been "noted on record" alongside a heart condition. Separate concerns,
 * separate columns.
 */
public enum PressurePreference {

    LIGHT("Light"),
    MEDIUM("Medium"),
    FIRM("Firm");

    private final String displayName;

    PressurePreference(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
