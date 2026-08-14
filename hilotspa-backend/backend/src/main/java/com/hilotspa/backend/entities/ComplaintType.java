package com.hilotspa.backend.entities;

/**
 * The complaint list from the Hilotin Spa intake form (Appendix A of the paper).
 *
 * displayName holds the exact wording used on the physical form. The Angular UI
 * reads these values so the digital pre-assessment matches the paper form verbatim,
 * while the database stores the stable constant name.
 */
public enum ComplaintType {
    NECK_PAIN("Neck Pain"),
    SHOULDER_PAIN("Shoulder Pain"),
    UPPER_BACK_PAIN("Upper Back Pain"),
    LOWER_BACK_PAIN("Lower Back Pain"),
    ELBOW_PAIN("Elbow Pain"),
    WRIST_PAIN("Wrist Pain"),
    HIP_JOINT_PAIN("Hip Joint Pain"),
    KNEE_PAIN("Knee Pain"),
    ANKLE_PAIN("Ankle Pain"),
    STIFF_NECK("Stiff Neck"),
    FROZEN_SHOULDER("Frozen Shoulder"),
    SCIATICA("Sciatica"),
    SCOLIOSIS("Scoliosis"),
    OSTEOARTHRITIS("Osteoarthritis"),
    SPONDYLOSIS("Spondylosis"),
    DISC_BULGE("Disc Bulge"),
    SLIP_DISC("Slip Disc"),
    DDD("DDD"),
    DISC_DESICCATION("Disc Desiccation"),
    STENOSIS("Stenosis"),
    PLANTAR_FASCIITIS("Plantar Fasciitis"),
    RADICULOPATHY("Radiculopathy"),
    CTS("CTS"),
    TMJ_DISORDER("TMJ Disorder"),
    OTHER("Others");

    private final String displayName;

    ComplaintType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
