package com.hilotspa.backend.entities;

/**
 * The safety checklist from the pre-assessment - paper-deltas H9.
 *
 * These were packed into the free-text `remarks` column as
 * "Flagged: Pregnant, Taking blood thinners". Three things were wrong with that.
 * They could not be queried, so nobody could ever ask how many clients present
 * on blood thinners. They could not be joined to ServiceProtocol, so the
 * practitioner's rules can only ever key on a complaint and never on a
 * condition. And the assistant was told nothing but `noted_on_record: true`,
 * because a parsed claim pulled out of free text is a guess, and guessing about
 * pregnancy is not a thing this system should do.
 *
 * displayName is the exact wording the client saw when they ticked the box, on
 * the same principle as ComplaintType (B27): the record must read back the way
 * the question was asked.
 */
public enum SafetyFlag {

    PREGNANT("Pregnant"),
    HIGH_BLOOD_PRESSURE("High blood pressure"),
    HEART_CONDITION("Heart condition"),
    DIABETES("Diabetes"),
    VARICOSE_VEINS("Varicose veins"),
    RECENT_FRACTURE_OR_SURGERY("Fracture or surgery in the last 6 weeks"),
    OPEN_WOUND_OR_SKIN_INFECTION("Open wound or skin infection"),
    CANCER_OR_UNDER_TREATMENT("Cancer, or under treatment"),
    BLOOD_THINNERS("Taking blood thinners"),
    OSTEOPOROSIS("Osteoporosis");

    private final String displayName;

    SafetyFlag(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
