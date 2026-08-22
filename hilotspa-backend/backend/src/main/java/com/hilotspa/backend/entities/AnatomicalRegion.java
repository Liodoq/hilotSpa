package com.hilotspa.backend.entities;

/**
 * The eleven regions named in the findings table on the paper intake form
 * (Appendix A, practitioner's half).
 *
 * displayName holds the exact wording used on the physical form, the same
 * convention as ComplaintType. As free text, "Lumbar" and "lumbar" were
 * different regions: aggregation across records broke, and the region-to-service
 * lookup silently missed.
 */
public enum AnatomicalRegion {
    CERVICAL("Cervical"),
    SHOULDER("Shoulder"),
    ELBOW("Elbow"),
    WRIST("Wrist"),
    THORACIC("Thoracic"),
    MID_BACK("Mid Back"),
    LUMBAR("Lumbar"),
    SI_JOINT("S.I. Joint"),
    HIP_JOINT("Hip Joint"),
    KNEE("Knee"),
    ANKLE("Ankle");

    private final String displayName;

    AnatomicalRegion(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
