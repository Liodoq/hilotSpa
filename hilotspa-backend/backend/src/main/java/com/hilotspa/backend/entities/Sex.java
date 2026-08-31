package com.hilotspa.backend.entities;

/**
 * Used for a therapist's sex, and for the client's preference about it.
 *
 * An enum rather than the free-text String on Demographics, for the reason
 * behind B27: "Female", "female" and "F" are three different values the moment
 * anyone types them, and this one is compared against a preference on every
 * availability search.
 *
 * There is no NO_PREFERENCE constant. A client with no preference has no
 * preference, and null says that better than a third value nobody can hold.
 */
public enum Sex {

    FEMALE("Female"),
    MALE("Male");

    private final String displayName;

    Sex(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
