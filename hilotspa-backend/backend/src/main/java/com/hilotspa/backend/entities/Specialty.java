package com.hilotspa.backend.entities;

/**
 * What a therapist is trained to perform, and what a treatment requires.
 *
 * An enum for the same reason Sex is one (B27): "Bone Setting", "bone setting"
 * and "bonesetting" become three different skills the moment anyone types them,
 * and this value is compared on every availability search.
 *
 * Stored as STRING, so adding a value later is a one-line change with no
 * migration and no risk of an ordinal quietly reassigning what a therapist can
 * do - the mistake B25 was.
 *
 * The clinical point: bone setting is not massage. A client who books a bone
 * setting and is handed a therapist who has never set a bone has been failed in
 * a way no amount of scheduling correctness makes up for. This enum is what
 * lets the system decline to make that booking rather than making it and hoping.
 */
public enum Specialty {

    MASSAGE("Massage"),
    BONE_SETTING("Bone setting"),
    HEAD_SPA("Head spa");

    private final String displayName;

    Specialty(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
